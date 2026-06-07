<script setup lang="ts">
import type { PageResponse, TransactionDto } from '@/api/portfolio'
import { formatCurrency, formatDate } from '@/utils/format'

const props = defineProps<{
  page: PageResponse<TransactionDto> | null
  loading: boolean
  error: string | null
}>()

// IMPORTANT: emit is named 'page-change' (NOT 'page') to avoid collision with the 'page' prop.
// DashboardView wires this via @page-change.
const emit = defineEmits<{ 'page-change': [n: number] }>()

const isEmpty = () =>
  !props.loading && !props.error && (!props.page || props.page.content.length === 0)
</script>

<template>
  <div class="transactions-wrapper">
    <table class="transactions-table" aria-label="Transactions">
      <thead>
        <tr>
          <th scope="col" class="col-date">Date</th>
          <th scope="col" class="col-side">Side</th>
          <th scope="col" class="col-ticker">Ticker</th>
          <th scope="col" class="col-qty">Qty</th>
          <th scope="col" class="col-price">Price</th>
          <th scope="col" class="col-rcb">Running Cost Basis</th>
        </tr>
      </thead>
      <tbody>
        <!-- Loading skeleton rows -->
        <template v-if="loading" aria-busy="true" aria-label="Loading transactions">
          <tr v-for="i in 5" :key="`skel-${i}`" class="skeleton-row" aria-hidden="true">
            <td v-for="j in 6" :key="`skel-${i}-${j}`">
              <span class="skeleton-pill" />
            </td>
          </tr>
        </template>

        <!-- Error state -->
        <tr v-else-if="error" class="error-row">
          <td colspan="6" class="error-cell">
            Failed to load transactions.
            <button class="retry-btn" @click="$emit('page-change', 0)">Retry</button>
          </td>
        </tr>

        <!-- Empty state -->
        <tr v-else-if="isEmpty()" class="empty-row">
          <td colspan="6" class="empty-cell">No transactions recorded.</td>
        </tr>

        <!-- Data rows -->
        <template v-else-if="page">
          <tr v-for="(tx, idx) in page.content" :key="idx" class="data-row">
            <td class="col-date-cell">{{ formatDate(tx.txDate) }}</td>
            <td class="col-side-cell">
              <span :class="tx.txType === 'BUY' ? 'badge-buy' : 'badge-sell'">
                {{ tx.txType }}
              </span>
            </td>
            <td class="ticker">{{ tx.ticker }}</td>
            <td class="numeric">{{ Math.round(tx.quantity).toLocaleString('en-US') }}</td>
            <td class="numeric">{{ formatCurrency(tx.price) }}</td>
            <td class="numeric">{{ formatCurrency(tx.runningCostBasis) }}</td>
          </tr>
        </template>
      </tbody>
    </table>

    <!-- Pagination controls -->
    <div
      v-if="page && !loading && !error"
      class="pagination"
      role="navigation"
      aria-label="Transactions pagination"
    >
      <button
        class="page-btn"
        :disabled="page.number === 0"
        @click="emit('page-change', page.number - 1)"
      >
        Prev
      </button>
      <span class="page-info">Page {{ page.number + 1 }} of {{ page.totalPages }}</span>
      <button
        class="page-btn"
        :disabled="page.number >= page.totalPages - 1"
        @click="emit('page-change', page.number + 1)"
      >
        Next
      </button>
    </div>
  </div>
</template>

<style scoped>
.transactions-wrapper {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  overflow-x: auto;
}

.transactions-table {
  width: 100%;
  border-collapse: collapse;
}

thead th {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  padding: var(--space-xs) var(--space-md);
  text-align: left;
  position: sticky;
  top: 0;
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
  white-space: nowrap;
}

/* No striping — all rows use surface color */
tbody tr { background: var(--color-bg-surface); }
tbody tr:hover { background: var(--color-bg-overlay); }

tbody td {
  padding: var(--space-xs) var(--space-md);
  font-size: 14px;
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-subtle);
}

.col-date-cell { width: 96px; }
.col-side-cell { width: 64px; text-align: center; }

td.ticker {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-accent);
  width: 72px;
}

td.numeric {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

/* BUY / SELL badges */
.badge-buy {
  color: var(--color-up);
  background: var(--color-up-subtle);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  display: inline-block;
}

.badge-sell {
  color: var(--color-down);
  background: var(--color-down-subtle);
  border-radius: var(--radius-pill);
  padding: 2px 8px;
  font-size: 12px;
  font-weight: 600;
  display: inline-block;
}

.empty-cell {
  text-align: center;
  color: var(--color-text-muted);
  padding: var(--space-xl) !important;
}

.error-cell {
  text-align: center;
  color: var(--color-down);
  padding: var(--space-xl) !important;
}

.retry-btn {
  margin-left: var(--space-sm);
  background: transparent;
  border: 1px solid var(--color-down);
  color: var(--color-down);
  border-radius: var(--radius-sm);
  padding: 2px 8px;
  cursor: pointer;
  font-size: 13px;
}

/* Skeleton */
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}

.skeleton-pill {
  display: block;
  height: 14px;
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  width: 60%;
}

/* Pagination */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-md);
  padding: var(--space-sm) var(--space-md);
  border-top: 1px solid var(--color-border);
  font-size: 13px;
  color: var(--color-text-secondary);
}

.page-btn {
  background: transparent;
  border: 1px solid var(--color-border);
  color: var(--color-text-secondary);
  border-radius: var(--radius-sm);
  padding: 4px 12px;
  cursor: pointer;
  font-size: 13px;
  min-height: 28px;
  transition: border-color 0.15s, color 0.15s;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-info {
  color: var(--color-text-muted);
}
</style>
