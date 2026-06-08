<script setup lang="ts">
import type { PairResultDto } from '@/api/analytics'

// T-04-08: props-driven — tests mount directly without store.
const props = defineProps<{
  pairs: PairResultDto[] | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

function signalClass(signal: PairResultDto['signal']): string {
  if (signal === 'LONG_Y_SHORT_X') return 'signal-buy'
  if (signal === 'SHORT_Y_LONG_X') return 'signal-sell'
  return 'signal-neutral'
}

function zScoreClass(z: number): string {
  if (Math.abs(z) >= 2) return 'zscore-high'
  if (Math.abs(z) >= 1) return 'zscore-mid'
  return 'zscore-low'
}
</script>

<template>
  <div class="table-panel">
    <!-- Loading skeleton -->
    <div v-if="loading" class="skeleton" style="height: 200px" aria-hidden="true" />

    <!-- Error state — T-04-08: static copy only -->
    <div v-else-if="error" class="chart-error" role="alert">
      <span>Failed to load pairs data. Check your connection and try again.</span>
      <button class="retry-btn" @click="emit('retry')">Retry</button>
    </div>

    <!-- Empty state -->
    <div v-else-if="!props.pairs?.length" class="chart-empty">
      No cointegrated pairs found in current holdings.
    </div>

    <!-- Data table -->
    <table v-else class="pairs-table" aria-label="Cointegration pairs">
      <thead>
        <tr>
          <th scope="col">Pair</th>
          <th scope="col">p-value</th>
          <th scope="col">Hedge &beta;</th>
          <th scope="col">Z-score</th>
          <th scope="col">Signal</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="pair in props.pairs" :key="`${pair.tickerY}-${pair.tickerX}`">
          <td class="pair-cell">{{ pair.tickerY }} / {{ pair.tickerX }}</td>
          <td class="numeric">{{ pair.pValue.toFixed(3) }}</td>
          <td class="numeric">{{ pair.hedgeRatio.toFixed(3) }}</td>
          <td class="numeric" :class="zScoreClass(pair.spreadZScore)">
            {{ pair.spreadZScore.toFixed(2) }}
          </td>
          <td>
            <span :class="['signal-badge', signalClass(pair.signal)]">
              {{ pair.signal }}
            </span>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.table-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  overflow-x: auto;
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
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pairs-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 14px;
}

.pairs-table thead th {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  padding: var(--space-xs) var(--space-md);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

.pairs-table tbody tr:nth-child(odd)  { background: var(--color-bg-surface); }
.pairs-table tbody tr:nth-child(even) { background: var(--color-bg-elevated); }
.pairs-table tbody tr:hover           { background: var(--color-bg-overlay); }

.pairs-table tbody td {
  padding: var(--space-xs) var(--space-md);
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-subtle);
}

td.numeric {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.pair-cell {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-accent);
}

.zscore-high { color: var(--color-up); }
.zscore-mid  { color: var(--color-warn); }
.zscore-low  { color: var(--color-text-secondary); }

.signal-badge {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  white-space: nowrap;
}

.signal-buy {
  background: var(--color-up-subtle, rgba(34,197,94,0.15));
  color: var(--color-up);
  border: 1px solid var(--color-up);
}

.signal-sell {
  background: var(--color-down-subtle, rgba(239,68,68,0.15));
  color: var(--color-down);
  border: 1px solid var(--color-down);
}

.signal-neutral {
  background: var(--color-bg-overlay);
  color: var(--color-text-secondary);
  border: 1px solid var(--color-border);
}
</style>
