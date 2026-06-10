<script setup lang="ts">
/**
 * StructuredOutputChart — renders a horizontal bar chart from a StructuredChartDto (title + series[]).
 *
 * The chart binds to one ai-store slot regardless of mode (AI-06): in demo mode that slot holds a
 * seeded fixture; with a BYO LLM key it holds a live, BeanOutputConverter-typed record returned by
 * GET /api/ai/structured. The DTO shape is identical in both modes, so the component never changes
 * between demo and live — the same typed structured output drives the chart either way.
 *
 * Three states: shimmer skeleton (loading), static error (error), chart (populated).
 */
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { StructuredChartDto } from '../../api/ai'
import { CHART_COLORS } from '../../plugins/chart-colors'

const props = defineProps<{
  structured: StructuredChartDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  retry: []
}>()

// Horizontal bar chart option for label→value series
const option = computed<EChartsOption>(() => {
  if (!props.structured?.series?.length) return {}

  const labels = props.structured.series.map(s => s.label)
  const values = props.structured.series.map(s => s.value)

  return {
    grid: { top: 16, right: 40, bottom: 16, left: 100, containLabel: false },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        return `${p.name}: ${Number(p.value).toFixed(1)}%`
      },
    },
    xAxis: {
      type: 'value',
      axisLabel: {
        formatter: (val: number) => `${val.toFixed(0)}%`,
        fontSize: 11,
        color: CHART_COLORS.textSecondary,
      },
      splitLine: {
        lineStyle: { color: CHART_COLORS.border },
      },
    },
    yAxis: {
      type: 'category',
      data: labels,
      axisLabel: {
        fontSize: 11,
        color: CHART_COLORS.textSecondary,
      },
      axisTick: { show: false },
    },
    series: [
      {
        type: 'bar',
        data: values,
        itemStyle: { color: CHART_COLORS.accent, borderRadius: [0, 2, 2, 0] },
        label: {
          show: true,
          position: 'right',
          formatter: (p: any) => `${Number(p.value).toFixed(1)}%`,
          fontSize: 11,
          color: CHART_COLORS.textSecondary,
        },
      },
    ],
  }
})
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="props.loading"
    aria-label="AI Structured Output chart"
  >

    <!-- Demo badge — clarifies this is the structured-output seam panel -->
    <div v-if="!props.loading && !props.error" class="chart-badge">
      <span class="demo-label">Structured Output · Demo</span>
    </div>

    <!-- Loading: shimmer skeleton -->
    <div v-if="props.loading" class="skeleton" style="height: 240px" aria-hidden="true" />

    <!-- Error: static copy only -->
    <div v-else-if="props.error" class="chart-error" role="alert">
      <span>Failed to load structured output data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty: populated but empty series -->
    <div v-else-if="!props.structured?.series?.length" class="chart-empty">
      No structured output data available.
    </div>

    <!-- Chart -->
    <template v-else>
      <div class="chart-header">
        <h3 class="chart-title">{{ props.structured.title }}</h3>
        <p v-if="props.structured.subtitle" class="chart-subtitle">{{ props.structured.subtitle }}</p>
      </div>
      <v-chart
        class="chart"
        :option="option"
        :autoresize="true"
      />
    </template>

    <figcaption class="sr-only">
      AI structured output chart showing detected sector exposure. The same typed structured output
      drives this chart in both modes — a seeded record in demo mode, or a live
      BeanOutputConverter-typed record from the LLM when a key is set.
    </figcaption>

  </figure>
</template>

<style scoped>
.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  min-height: 240px;
  display: block;
  position: relative;
}

.chart-badge {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  z-index: 1;
}

.demo-label {
  font-size: 10px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: var(--color-text-muted);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
}

.chart-header {
  margin-bottom: var(--space-sm);
}

.chart-title {
  font-size: 15px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px 0;
}

.chart-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 0;
}

.chart {
  height: 200px;
  width: 100%;
}

@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.skeleton {
  display: block;
  border-radius: var(--radius-md);
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
}

.chart-error {
  color: var(--color-down);
  border: 1px solid var(--color-down);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  font-size: 14px;
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
  flex-shrink: 0;
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
  min-height: 200px;
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
