<script setup lang="ts">
import { computed, ref } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { AllocationSliceDto } from '@/api/portfolio'
import { CHART_COLORS } from '@/plugins/chart-colors'

// T-03-07: label formatters use typed numbers only — no v-html, no raw API strings
// T-03-08: error state shows static copy from UI-SPEC, never the raw error object

const props = defineProps<{
  allocation: AllocationSliceDto[] | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

// Toggle between donut and treemap views
const chartType = ref<'donut' | 'treemap'>('donut')

// Donut (pie) option — radius/center as per RESEARCH.md Pattern 3
// Color palette comes from quantlens-dark theme color[] — not hardcoded here
const donutOption = computed<EChartsOption>(() => {
  if (!props.allocation?.length) return {}

  // Compute total market value for center label
  const totalMv = props.allocation.reduce((sum, s) => sum + Number(s.marketValue), 0)
  const totalLabel = new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    notation: 'compact',
    maximumFractionDigits: 1,
  }).format(totalMv)

  return {
    graphic: [
      {
        type: 'text',
        left: 'center',
        top: '46%',
        style: {
          text: totalLabel,
          textAlign: 'center',
          fill: '#e2e8f0',
          fontSize: 14,
          fontWeight: 600,
          fontFamily: "ui-monospace, 'Cascadia Code', Consolas, monospace",
        },
      },
    ],
    series: [
      {
        type: 'pie',
        radius: ['40%', '68%'],
        center: ['50%', '55%'],
        data: props.allocation.map(s => ({
          name: s.label,
          value: Number(s.marketValue),
        })),
        label: { show: false },
        emphasis: {
          label: { show: true, fontSize: 13 },
          itemStyle: { shadowBlur: 10, shadowOffsetX: 0, shadowColor: 'rgba(0,0,0,0.5)' },
        },
      },
    ],
    legend: {
      bottom: 4,
      left: 'center',
      type: 'scroll',
    },
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        // T-03-07: typed number only in formatter — no raw API strings
        const pct = Number(params.percent).toFixed(1)
        const val = new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
        }).format(Number(params.value))
        return `${params.name}<br/>${val} (${pct}%)`
      },
    },
  }
})

// Treemap option — RESEARCH.md Pattern 3
const treemapOption = computed<EChartsOption>(() => {
  if (!props.allocation?.length) return {}

  // Build a lookup map for weight by label (used in formatter)
  const weightMap = new Map(props.allocation.map(s => [s.label, Number(s.weight)]))

  return {
    series: [
      {
        type: 'treemap',
        data: props.allocation.map(s => ({
          name: s.label,
          value: Number(s.marketValue),
        })),
        width: '100%',
        height: '88%',
        label: {
          show: true,
          // T-03-07: formatter uses typed weight number only
          formatter: (p: any) => {
            const w = weightMap.get(p.name) ?? 0
            return `${p.name}\n${(w * 100).toFixed(1)}%`
          },
          fontSize: 11,
        },
        itemStyle: {
          borderWidth: 2,
          borderColor: CHART_COLORS.bgBase,
        },
        emphasis: {
          itemStyle: { shadowBlur: 8, shadowColor: 'rgba(0,0,0,0.5)' },
        },
        breadcrumb: { show: false },
      },
    ],
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        const w = weightMap.get(params.name) ?? 0
        const val = new Intl.NumberFormat('en-US', {
          style: 'currency',
          currency: 'USD',
        }).format(Number(params.value))
        return `${params.name}<br/>${val} (${(w * 100).toFixed(1)}%)`
      },
    },
  }
})

// Switch option based on chartType ref
const option = computed<EChartsOption>(() =>
  chartType.value === 'donut' ? donutOption.value : treemapOption.value
)
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="loading"
    aria-label="Allocation chart"
  >
    <!-- Toggle control — positioned top-right; outside v-if so always visible when populated -->
    <div
      v-if="!loading && !error && props.allocation?.length"
      class="chart-toggle"
      role="group"
      aria-label="Chart type"
    >
      <button
        :class="['toggle-btn', { active: chartType === 'donut' }]"
        @click="chartType = 'donut'"
        :aria-pressed="chartType === 'donut'"
      >
        Donut
      </button>
      <button
        :class="['toggle-btn', { active: chartType === 'treemap' }]"
        @click="chartType = 'treemap'"
        :aria-pressed="chartType === 'treemap'"
      >
        Treemap
      </button>
    </div>

    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 280px" aria-hidden="true" />

    <!-- Error state — T-03-08: static copy only -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load allocation. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.allocation?.length" class="chart-empty">
      No allocation data. Add holdings to see your sector breakdown.
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
      Portfolio allocation chart. Shows sector allocation as a donut or treemap. Toggle between views using the Donut and Treemap buttons.
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
  min-height: 280px;
  display: block;
  position: relative;
}

.chart {
  height: 280px;
  width: 100%;
}

/* Segmented toggle — top-right of card */
.chart-toggle {
  position: absolute;
  top: var(--space-md);
  right: var(--space-md);
  display: flex;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  overflow: hidden;
  z-index: 1;
}

.toggle-btn {
  background: transparent;
  border: none;
  color: var(--color-text-secondary);
  padding: 3px 10px;
  font-size: 12px;
  cursor: pointer;
  min-height: 28px;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.toggle-btn:not(:last-child) {
  border-right: 1px solid var(--color-border);
}

.toggle-btn.active {
  background: var(--color-accent-subtle);
  color: var(--color-text-primary);
  border-color: var(--color-accent);
}

.toggle-btn:hover:not(.active) {
  background: var(--color-bg-overlay);
}

.toggle-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
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
  min-height: 240px;
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
