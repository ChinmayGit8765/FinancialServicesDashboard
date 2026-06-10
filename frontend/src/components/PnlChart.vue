<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { PortfolioPnlDto } from '@/api/portfolio'
import { CHART_COLORS } from '@/plugins/chart-colors'
import CardHeading from './CardHeading.vue'

// T-03-07: tooltip/axis formatters use typed numbers/ISO dates only — no v-html, no raw API strings injected into DOM
// T-03-08: error state shows static copy from UI-SPEC, never the raw error object

const props = defineProps<{
  pnl: PortfolioPnlDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

const option = computed<EChartsOption>(() => {
  if (!props.pnl?.equityCurve?.length) return {}

  const dates  = props.pnl.equityCurve.map(d => d.date)
  const values = props.pnl.equityCurve.map(d => Number(d.value))

  return {
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        // Pitfall 2 guard: append T00:00:00 to force local midnight — avoids UTC off-by-one
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
      {
        type: 'line',
        data: values,
        smooth: true,
        symbol: 'none',
        lineStyle: { color: CHART_COLORS.accent, width: 2 },
        areaStyle: {
          color: {
            type: 'linear',
            x: 0,
            y: 0,
            x2: 0,
            y2: 1,
            colorStops: [
              { offset: 0, color: 'rgba(79,159,224,0.22)' },
              { offset: 1, color: 'rgba(79,159,224,0)' },
            ],
          },
        },
      },
    ],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = Array.isArray(params) ? params[0] : params
        // Pitfall 2 guard: T00:00:00 suffix forces local midnight parsing
        const d = new Date(p.axisValue + 'T00:00:00')
        const label = d.toLocaleDateString('en-US', {
          day: '2-digit',
          month: 'short',
          year: 'numeric',
        })
        const val = new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
        }).format(Number(p.value))
        // T-03-07: only typed number + date string — no API strings injected
        return `${label}<br/>${val}`
      },
    },
  }
})
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="loading"
    aria-label="P&L equity curve chart"
  >
    <CardHeading title="Portfolio Value" subtitle="Total portfolio market value over ~2 years. Hover any point for that day’s value." />

    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />

    <!-- Error state — T-03-08: static copy only, never raw error object -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load P&amp;L data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.pnl?.equityCurve?.length" class="chart-empty">
      No P&amp;L data available.
    </div>

    <!-- Chart — no theme prop: THEME_KEY provided in App.vue propagates automatically -->
    <v-chart
      v-else
      class="chart"
      :option="option"
      :autoresize="true"
    />

    <!-- Accessibility: screen-reader text summary (UI-SPEC § Accessibility) -->
    <figcaption class="sr-only">
      Portfolio equity curve over time. Shows the portfolio value across the full seeded window.
    </figcaption>
  </figure>
</template>

<style scoped>
/* All colors/spacing/radius/shadow via CSS custom properties — no hardcoded hex (PATTERNS.md rule) */

.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  min-height: 320px;
  display: block; /* figure is block by default but be explicit */
}

.chart {
  height: 320px;
  width: 100%;
}

/* Shimmer skeleton animation */
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

/* Visually hidden but accessible to screen readers */
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
}
</style>
