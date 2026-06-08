<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { ForecastDto } from '@/api/forecast'
import type { ModelType } from '@/api/forecast'
import { getFanColors } from '@/plugins/chart-colors'

// T-03-07: axis/tooltip formatters use typed numbers only — no v-html, no raw API strings
// T-03-08: error state shows static copy from UI-SPEC, never the raw error object
// T-05-06: error copy is static ("Failed to load forecast…") — no server detail leaked

const props = defineProps<{
  forecast: ForecastDto | null
  loading:  boolean
  error:    string | null
}>()

const emit = defineEmits<{ retry: []; 'update:model': [ModelType] }>()

// Model selector — drives re-fetch (AllocationChart.vue toggle pattern)
const selectedModel = ref<ModelType>('GBM')
watch(selectedModel, (model) => {
  emit('update:model', model)
})

const MODEL_LABELS: ModelType[] = ['GBM', 'JUMP_DIFFUSION', 'HESTON', 'BOOTSTRAP']

// NOTE: ECharts renders on <canvas> and CANNOT resolve CSS custom properties at paint time.
// getFanColors() re-reads the --color-fan-* CSS tokens on each computed evaluation (WR-05 fix)
// supporting runtime theme switching. Do NOT pass 'var(--color-fan-*)' strings
// (they render as transparent/black on canvas — Pitfall 4, 05-RESEARCH.md).
const option = computed<EChartsOption>(() => {
  if (!props.forecast?.p50?.length) return {}

  const FAN_COLORS = getFanColors()  // WR-05: re-read CSS tokens on each compute (supports theme switching)
  const { p5, p25, p50, p75, p95 } = props.forecast
  const steps = p50.map((_, i) => `Day ${i + 1}`)

  return {
    xAxis: {
      type: 'category',
      data: steps,
      axisLabel: {
        interval: Math.floor(steps.length / 6),
        showMaxLabel: true,
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: (v: number) =>
          new Intl.NumberFormat('en-US', {
            style: 'currency',
            currency: 'USD',
            notation: 'compact',
            maximumFractionDigits: 1,
          }).format(v),
      },
    },
    series: [
      // Series 0: base floor at p5 (invisible fill — establishes the stack floor)
      {
        type: 'line',
        data: p5,
        stack: 'fan',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: 'transparent' },
      },
      // Series 1: band p5 → p25 (outer, lighter)  — DIFFERENCE, not absolute
      {
        type: 'line',
        // CR-03 fix: clamp to >= 0 — ECharts stacks negative values downward (inverted bands)
        data: p25.map((v, i) => Math.max(0, v - p5[i])),
        stack: 'fan',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandOuter },
      },
      // Series 2: band p25 → p75 (IQR, more opaque)  — DIFFERENCE
      {
        type: 'line',
        // CR-03 fix: clamp to >= 0
        data: p75.map((v, i) => Math.max(0, v - p25[i])),
        stack: 'fan',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandInner },
      },
      // Series 3: band p75 → p95 (outer, lighter)  — DIFFERENCE
      {
        type: 'line',
        // CR-03 fix: clamp to >= 0
        data: p95.map((v, i) => Math.max(0, v - p75[i])),
        stack: 'fan',
        symbol: 'none',
        lineStyle: { opacity: 0 },
        areaStyle: { color: FAN_COLORS.bandOuter },
      },
      // Series 4: median line (NOT stacked — absolute p50 values; drawn on top of all bands)
      {
        type: 'line',
        name: 'Median (p50)',
        data: p50,
        symbol: 'none',
        lineStyle: { color: FAN_COLORS.median, width: 2 },
      },
    ],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        // T-03-07: only typed number + axis label — no raw API strings injected
        const label = Array.isArray(params) ? params[0]?.axisValue : params?.axisValue
        const medianParam = Array.isArray(params)
          ? params.find((p: any) => p.seriesName === 'Median (p50)')
          : null
        if (!medianParam) return label ?? ''
        const val = new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
        }).format(Number(medianParam.value))
        return `${label ?? ''}<br/>Median: ${val}`
      },
    },
  }
})
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="loading"
    aria-label="Monte Carlo fan chart"
  >
    <!-- Model selector toggle (AllocationChart.vue .chart-toggle pattern) -->
    <div
      v-if="!loading && !error && props.forecast?.p50?.length"
      class="chart-toggle"
      role="group"
      aria-label="Forecast model"
    >
      <button
        v-for="m in MODEL_LABELS"
        :key="m"
        :class="['toggle-btn', { active: selectedModel === m }]"
        :aria-pressed="selectedModel === m"
        @click="selectedModel = m"
      >
        {{ m }}
      </button>
    </div>

    <!-- Heston illustrative-parameters disclaimer (T-05-02 UX) -->
    <p
      v-if="selectedModel === 'HESTON' && !loading && !error && props.forecast?.p50?.length"
      class="heston-disclaimer"
    >
      Heston parameters are illustrative, not calibrated to live option prices.
    </p>

    <!-- Loading skeleton (PnlChart.vue pattern) -->
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />

    <!-- Error state — T-03-08: static copy only, never the raw error object -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load forecast. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.forecast?.p50?.length" class="chart-empty">
      No forecast data available.
    </div>

    <!-- Chart — NO :theme prop (THEME_KEY provided in App.vue propagates automatically) -->
    <v-chart
      v-else
      class="chart"
      :option="option"
      :autoresize="true"
    />

    <figcaption class="sr-only">
      Monte Carlo fan chart. Shows projected portfolio value percentile bands
      (p5, p25, p50 median, p75, p95) over the forecast horizon. Select a model
      with the toggle buttons above.
    </figcaption>
  </figure>
</template>

<style scoped>
/* All colors/spacing/radius via CSS custom properties — no hardcoded hex */

.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  min-height: 320px;
  display: block;
}

.chart {
  height: 320px;
  width: 100%;
}

/* Model selector toggle (AllocationChart.vue .chart-toggle / .toggle-btn pattern) */
.chart-toggle {
  display: flex;
  gap: var(--space-xs);
  margin-bottom: var(--space-sm);
  flex-wrap: wrap;
}

.toggle-btn {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  padding: 3px 10px;
  cursor: pointer;
  font-size: 12px;
  min-height: 26px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.toggle-btn:hover {
  background: var(--color-accent-subtle);
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.toggle-btn.active {
  background: var(--color-accent-subtle);
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.toggle-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 2px;
}

/* Heston illustrative-params disclaimer */
.heston-disclaimer {
  font-size: 11px;
  color: var(--color-text-muted);
  margin: 0 0 var(--space-xs);
  padding: 0 var(--space-xs);
  font-style: italic;
}

/* Shimmer skeleton */
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position: 200% center; }
}

.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  border-radius: var(--radius-md);
}

.chart-error {
  color: var(--color-down);
  border: 1px solid var(--color-down);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}

.retry-btn {
  background: transparent;
  border: 1px solid var(--color-accent);
  color: var(--color-accent);
  border-radius: var(--radius-sm);
  padding: 2px 10px;
  cursor: pointer;
  font-size: 13px;
  min-height: 28px;
}

.retry-btn:hover {
  background: var(--color-accent-subtle);
}

.retry-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.chart-empty {
  color: var(--color-text-muted);
  text-align: center;
  padding: var(--space-xl);
  min-height: 280px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
</style>
