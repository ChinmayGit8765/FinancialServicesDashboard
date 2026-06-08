<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { CorrelationMatrixDto } from '@/api/analytics'

// T-04-08: props-driven — tests mount directly without store.
// No theme prop: THEME_KEY is provided globally in App.vue; VChart picks it up automatically.
const props = defineProps<{
  correlation: CorrelationMatrixDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

// ECharts heatmap option — data is [xIndex, yIndex, value]
// yAxis reversed so matrix[0][0] (top-left diagonal) maps to top-left cell
const option = computed<EChartsOption>(() => {
  if (!props.correlation?.tickers.length) return {}
  const dto = props.correlation
  const n = dto.tickers.length
  const data: [number, number, number][] = []
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      data.push([j, i, dto.matrix[i][j]])
    }
  }

  return {
    grid: { top: 20, right: 20, bottom: 80, left: 60 },
    tooltip: {
      formatter: (params: any) => {
        const [xi, yi, v] = params.data
        return `${dto.tickers[yi]} / ${dto.tickers[xi]}: ${v.toFixed(3)}`
      },
    },
    xAxis: {
      type: 'category',
      data: dto.tickers,
      axisLabel: { rotate: 45, fontSize: 11, color: 'inherit' },
    },
    yAxis: {
      type: 'category',
      data: [...dto.tickers].reverse(),
      axisLabel: { fontSize: 11, color: 'inherit' },
    },
    visualMap: {
      min: -1,
      max: 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      color: ['#ef4444', '#f8fafc', '#3b82f6'],  // red=+1, white=0, blue=-1
    },
    series: [
      {
        type: 'heatmap',
        data,
        label: {
          show: true,
          formatter: (p: any) => p.data[2].toFixed(2),
          fontSize: 10,
          color: '#1e293b',
        },
        emphasis: {
          itemStyle: { shadowBlur: 10 },
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
    aria-label="Correlation heatmap"
  >
    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 320px" aria-hidden="true" />

    <!-- Error state — T-04-08: static copy only -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load correlation data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.correlation?.tickers.length" class="chart-empty">
      No correlation data available.
    </div>

    <!-- Chart — no theme prop: THEME_KEY provided in App.vue propagates automatically -->
    <v-chart
      v-else
      class="chart"
      :option="option"
      :autoresize="true"
    />

    <figcaption class="sr-only">Pairwise return correlation heatmap.</figcaption>
  </figure>
</template>

<style scoped>
.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  min-height: 320px;
  display: block;
  position: relative;
}

.chart {
  height: 320px;
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
