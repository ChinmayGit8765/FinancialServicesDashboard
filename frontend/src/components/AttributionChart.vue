<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import CardHeading from './CardHeading.vue'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { AttributionDto } from '@/api/analytics'

// T-04-08: props-driven — tests mount directly without store.
// No theme prop: THEME_KEY is provided globally in App.vue; VChart picks it up automatically.
const props = defineProps<{
  attribution: AttributionDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

// ECharts bar option — per-bar green/red coloring based on sign
const option = computed<EChartsOption>(() => {
  if (!props.attribution) return {}
  const dto = props.attribution
  const labels = ['Alpha', 'Mkt-RF', 'SMB', 'HML']
  const values = [
    dto.alphaAnnualized * 100,
    dto.contribMktAnnualized * 100,
    dto.contribSmbAnnualized * 100,
    dto.contribHmlAnnualized * 100,
  ]

  return {
    grid: { top: 32, right: 20, bottom: 32, left: 52 },
    tooltip: {
      formatter: (params: any) => `${params.name}: ${Number(params.value).toFixed(2)}%`,
    },
    xAxis: {
      type: 'category',
      data: labels,
      axisLabel: { fontSize: 12 },
    },
    yAxis: {
      type: 'value',
      axisLabel: { formatter: '{value}%', fontSize: 11 },
    },
    series: [
      {
        type: 'bar',
        data: values.map((v, i) => ({
          value: v,
          name: labels[i],
          itemStyle: { color: v >= 0 ? '#22c55e' : '#ef4444' },
        })),
        label: {
          show: true,
          position: 'top',
          formatter: (p: any) => `${Number(p.value).toFixed(2)}%`,
          fontSize: 11,
        },
      },
    ],
  }
})
</script>

<template>
  <figure
    class="chart-panel"
    :aria-busy="loading"
    aria-label="Factor attribution chart"
  >
    <CardHeading title="Factor Attribution" subtitle="Fama-French breakdown: how much return came from market, size & value exposure vs. stock-picking (alpha)." />

    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 280px" aria-hidden="true" />

    <!-- Error state — T-04-08: static copy only -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load attribution data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.attribution" class="chart-empty">
      No attribution data available.
    </div>

    <!-- Chart — no theme prop: THEME_KEY provided in App.vue propagates automatically -->
    <v-chart
      v-else
      class="chart"
      :option="option"
      :autoresize="true"
    />

    <figcaption class="sr-only">Fama-French factor attribution bar chart showing Alpha, Mkt-RF, SMB, and HML contributions.</figcaption>
  </figure>
</template>

<style scoped>
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

@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
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
