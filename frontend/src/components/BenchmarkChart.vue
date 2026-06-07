<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { BenchmarkComparisonDto } from '@/api/portfolio'

// T-03-07: tooltip formatters use typed numbers/ISO dates only — no v-html, no raw API strings
// T-03-08: error state shows static copy from UI-SPEC, never the raw error object

const props = defineProps<{
  benchmark: BenchmarkComparisonDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

const option = computed<EChartsOption>(() => {
  if (!props.benchmark?.dates?.length) return {}

  return {
    legend: {
      // Legend positioned top-right inside chart — theme styles applied via quantlens-dark
      top: 4,
      right: 8,
    },
    xAxis: {
      type: 'category',
      data: props.benchmark.dates,
      axisLabel: {
        // Pitfall 2 guard: T00:00:00 suffix forces local midnight parsing
        formatter: (val: string) => {
          const d = new Date(val + 'T00:00:00')
          return d.toLocaleDateString('en-US', { month: 'short', year: '2-digit' })
        },
        showMaxLabel: true,
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: (v: number) => v.toFixed(0),
      },
      // Faint horizontal rule at 100 (baseline) — using markLine on first series
    },
    series: [
      {
        name: 'Portfolio',
        type: 'line',
        data: props.benchmark.portfolioSeries.map(Number),
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#0ea5e9', width: 2 },
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { color: '#334155', type: 'dashed', width: 1 },
          data: [{ yAxis: 100 }],
          label: { show: false },
        },
      },
      {
        name: 'S&P 500',
        type: 'line',
        data: props.benchmark.benchmarkSeries.map(Number),
        smooth: true,
        symbol: 'none',
        lineStyle: { color: '#94a3b8', width: 1.5, type: 'dashed' },
      },
    ],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const items = Array.isArray(params) ? params : [params]
        // T-03-07: only typed date string and numeric values — no API strings injected
        const d = new Date(items[0].axisValue + 'T00:00:00')
        const label = d.toLocaleDateString('en-US', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
        })
        const lines = items.map((p: any) => {
          const val = Number(p.value).toFixed(2)
          return `${p.seriesName}: ${val}`
        })
        return `${label}<br/>${lines.join('<br/>')}`
      },
    },
  }
})
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="loading"
    aria-label="Benchmark comparison chart"
  >
    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />

    <!-- Error state — T-03-08: static copy only, never raw error object -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load benchmark.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.benchmark?.dates?.length" class="chart-empty">
      No benchmark data available.
    </div>

    <!-- Chart — no theme prop: THEME_KEY provided in App.vue propagates automatically -->
    <v-chart
      v-else
      class="chart"
      :option="option"
      :autoresize="true"
    />

    <!-- Accessibility: screen-reader text summary -->
    <figcaption class="sr-only">
      Benchmark comparison chart. Portfolio and S&amp;P 500 proxy both rebased to 100 at the start of the window.
    </figcaption>
  </figure>
</template>

<style scoped>
/* All colors/spacing/radius/shadow via CSS custom properties — no hardcoded hex */

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
