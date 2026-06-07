<script setup lang="ts">
import { ref, computed } from 'vue'
import type { HoldingDto } from '@/api/portfolio'
import {
  formatCurrency,
  formatPercent,
  formatSignedCurrency,
  formatSignedPercent,
} from '@/utils/format'
import SignedValue from './SignedValue.vue'

const props = defineProps<{
  holdings: HoldingDto[] | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()

type SortKey = 'ticker' | 'marketValue' | 'pnl'
type SortDir = 'ascending' | 'descending'

const sortKey = ref<SortKey | null>(null)
const sortDir = ref<SortDir>('ascending')

function toggleSort(key: SortKey) {
  if (sortKey.value === key) {
    if (sortDir.value === 'ascending') {
      sortDir.value = 'descending'
    } else {
      // third click: reset
      sortKey.value = null
      sortDir.value = 'ascending'
    }
  } else {
    sortKey.value = key
    sortDir.value = 'ascending'
  }
}

function ariaSortAttr(key: SortKey): 'ascending' | 'descending' | 'none' {
  if (sortKey.value !== key) return 'none'
  return sortDir.value
}

const sortedHoldings = computed(() => {
  if (!props.holdings) return []
  if (!sortKey.value) return props.holdings

  const copy = [...props.holdings]
  copy.sort((a, b) => {
    let aVal: string | number
    let bVal: string | number

    if (sortKey.value === 'ticker') {
      aVal = a.ticker
      bVal = b.ticker
    } else if (sortKey.value === 'marketValue') {
      aVal = a.currentMarketValue
      bVal = b.currentMarketValue
    } else {
      // pnl
      aVal = a.unrealizedPnlAbs
      bVal = b.unrealizedPnlAbs
    }

    if (typeof aVal === 'string' && typeof bVal === 'string') {
      return sortDir.value === 'ascending'
        ? aVal.localeCompare(bVal)
        : bVal.localeCompare(aVal)
    }
    const diff = (aVal as number) - (bVal as number)
    return sortDir.value === 'ascending' ? diff : -diff
  })
  return copy
})

const isEmpty = computed(
  () => !props.loading && !props.error && (!props.holdings || props.holdings.length === 0)
)
</script>

<template>
  <div class="holdings-wrapper">
    <table class="holdings-table" aria-label="Holdings">
      <thead>
        <tr>
          <th
            scope="col"
            :aria-sort="ariaSortAttr('ticker')"
            class="col-ticker sortable"
            @click="toggleSort('ticker')"
          >
            Ticker
            <span v-if="sortKey === 'ticker'" class="sort-indicator" aria-hidden="true">
              {{ sortDir === 'ascending' ? '▲' : '▼' }}
            </span>
          </th>
          <th scope="col" class="col-name">Name</th>
          <th scope="col" class="col-sector">Sector</th>
          <th scope="col" class="col-qty">Qty</th>
          <th scope="col" class="col-avgcost">Avg Cost</th>
          <th scope="col" class="col-price">Price</th>
          <th
            scope="col"
            :aria-sort="ariaSortAttr('marketValue')"
            class="col-mktval sortable"
            @click="toggleSort('marketValue')"
          >
            Mkt Value
            <span v-if="sortKey === 'marketValue'" class="sort-indicator" aria-hidden="true">
              {{ sortDir === 'ascending' ? '▲' : '▼' }}
            </span>
          </th>
          <th scope="col" class="col-weight">Weight</th>
          <th
            scope="col"
            :aria-sort="ariaSortAttr('pnl')"
            class="col-pnl-abs sortable"
            @click="toggleSort('pnl')"
          >
            P&amp;L $
            <span v-if="sortKey === 'pnl'" class="sort-indicator" aria-hidden="true">
              {{ sortDir === 'ascending' ? '▲' : '▼' }}
            </span>
          </th>
          <th scope="col" class="col-pnl-pct">P&amp;L %</th>
        </tr>
      </thead>
      <tbody>
        <!-- Loading skeleton rows -->
        <template v-if="loading" aria-busy="true" aria-label="Loading holdings">
          <tr v-for="i in 5" :key="`skel-${i}`" class="skeleton-row" aria-hidden="true">
            <td v-for="j in 10" :key="`skel-${i}-${j}`">
              <span class="skeleton-pill" />
            </td>
          </tr>
        </template>

        <!-- Error state -->
        <tr v-else-if="error" class="error-row">
          <td colspan="10" class="error-cell">
            Failed to load holdings.
            <button class="retry-btn" @click="emit('retry')">Retry</button>
          </td>
        </tr>

        <!-- Empty state -->
        <tr v-else-if="isEmpty" class="empty-row">
          <td colspan="10" class="empty-cell">No holdings in this portfolio.</td>
        </tr>

        <!-- Data rows -->
        <template v-else>
          <tr v-for="h in sortedHoldings" :key="h.ticker" class="data-row">
            <td class="ticker">{{ h.ticker }}</td>
            <td class="name">{{ h.name }}</td>
            <td class="sector">{{ h.sector }}</td>
            <td class="numeric">{{ Math.round(h.quantity).toLocaleString('en-US') }}</td>
            <td class="numeric">{{ formatCurrency(h.avgCostBasis) }}</td>
            <td class="numeric">{{ formatCurrency(h.currentPrice) }}</td>
            <td class="numeric">{{ formatCurrency(h.currentMarketValue) }}</td>
            <td class="numeric">{{ formatPercent(h.portfolioWeight) }}</td>
            <td class="numeric">
              <SignedValue
                :value="h.unrealizedPnlAbs"
                :formatted="formatSignedCurrency(h.unrealizedPnlAbs)"
              />
            </td>
            <td class="numeric">
              <SignedValue
                :value="h.unrealizedPnlPct * 100"
                :formatted="formatSignedPercent(h.unrealizedPnlPct * 100)"
              />
            </td>
          </tr>
        </template>
      </tbody>
    </table>
  </div>
</template>

<style scoped>
.holdings-wrapper {
  overflow-x: auto;
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
}

.holdings-table {
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
  user-select: none;
}

thead th.sortable {
  cursor: pointer;
}

thead th.sortable:hover {
  color: var(--color-text-primary);
}

.sort-indicator {
  font-size: 10px;
  color: var(--color-accent);
  margin-left: 4px;
}

/* Striped rows */
tbody tr:nth-child(odd)  { background: var(--color-bg-surface); }
tbody tr:nth-child(even) { background: var(--color-bg-elevated); }
tbody tr:hover           { background: var(--color-bg-overlay); }

tbody td {
  padding: var(--space-xs) var(--space-md);
  font-size: 14px;
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-subtle);
}

td.numeric {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  text-align: right;
}

td.ticker {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-accent);
  width: 72px;
}

td.name {
  color: var(--color-text-secondary);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

td.sector {
  color: var(--color-text-secondary);
  width: 120px;
}

.col-qty     { width: 80px; text-align: right; }
.col-avgcost { width: 96px; text-align: right; }
.col-price   { width: 96px; text-align: right; }
.col-mktval  { width: 112px; text-align: right; }
.col-weight  { width: 72px; text-align: right; }
.col-pnl-abs { width: 112px; text-align: right; }
.col-pnl-pct { width: 80px; text-align: right; }

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

.retry-btn:hover {
  background: var(--color-down-subtle);
}

/* Skeleton pills */
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

.skeleton-row td:nth-child(odd) .skeleton-pill  { width: 80%; }
.skeleton-row td:nth-child(even) .skeleton-pill { width: 50%; }
</style>
