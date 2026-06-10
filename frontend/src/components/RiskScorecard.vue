<script setup lang="ts">
import type { RiskScorecardDto } from '@/api/analytics'
import { formatPercent, formatSignedPercent, formatCurrency } from '@/utils/format'
import KpiCard from './KpiCard.vue'
import CardHeading from './CardHeading.vue'

// T-04-08: props-driven so tests can mount directly without store.
// Components in DashboardView bind portfolioStore.risk.{data,loading,error}.
const props = defineProps<{
  risk: RiskScorecardDto | null
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{ retry: [] }>()
</script>

<template>
  <section class="risk-scorecard" aria-label="Risk Scorecard">
    <CardHeading
      style="grid-column: 1 / -1"
      title="Risk Metrics"
      subtitle="Annualized risk for this portfolio — Sharpe, volatility, max drawdown, beta, and 1-day Value-at-Risk (VaR)."
    />

    <!-- Loading state: one outer skeleton shown when loading, plus KpiCard skeletons -->
    <template v-if="loading">
      <div class="skeleton kpi-skeleton" aria-hidden="true" />
      <div class="skeleton kpi-skeleton" aria-hidden="true" />
      <div class="skeleton kpi-skeleton" aria-hidden="true" />
      <div class="skeleton kpi-skeleton" aria-hidden="true" />
    </template>

    <!-- Error state — T-04-08: static copy only, never raw error object -->
    <template v-else-if="error">
      <div class="chart-error" role="alert">
        <span>Failed to load risk metrics. Check your connection and try again.</span>
        <button class="retry-btn" @click="emit('retry')">Retry</button>
      </div>
    </template>

    <!-- Populated state -->
    <template v-else>
      <KpiCard
        label="Sharpe Ratio"
        :primary="risk ? risk.sharpeRatio.toFixed(2) : '—'"
        :loading="false"
      />
      <KpiCard
        label="Ann. Volatility"
        :primary="risk ? formatPercent(risk.annualizedVolatility) : '—'"
        :loading="false"
      />
      <KpiCard
        label="Max Drawdown"
        :primary="risk ? formatSignedPercent(risk.maxDrawdown * 100) : '—'"
        :delta="risk ? risk.maxDrawdown * 100 : undefined"
        :loading="false"
      />
      <KpiCard
        label="Beta"
        :primary="risk ? risk.beta.toFixed(2) : '—'"
        :loading="false"
      />

      <!-- VaR side-by-side table -->
      <div v-if="risk && risk.var.length" class="var-table-wrap">
        <table class="var-table" aria-label="Value at Risk">
          <thead>
            <tr>
              <th scope="col">Method</th>
              <th scope="col">Confidence</th>
              <th scope="col">Horizon</th>
              <th scope="col">Amount</th>
              <th scope="col">%</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="v in risk.var" :key="`${v.method}-${v.confidence}`">
              <td>{{ v.method }}</td>
              <td>{{ (v.confidence * 100).toFixed(0) }}%</td>
              <td>{{ v.horizonDays }}d</td>
              <td class="numeric">{{ formatCurrency(v.amount) }}</td>
              <td class="numeric">{{ formatPercent(v.percentage) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
  </section>
</template>

<style scoped>
.risk-scorecard {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(160px, 1fr));
  gap: var(--space-md);
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

.kpi-skeleton {
  min-height: 88px;
  border: 1px solid var(--color-border);
}

.chart-error {
  grid-column: 1 / -1;
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

/* VaR table spans full grid width */
.var-table-wrap {
  grid-column: 1 / -1;
  overflow-x: auto;
}

.var-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}

.var-table thead th {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  padding: var(--space-xs) var(--space-sm);
  text-align: left;
  border-bottom: 1px solid var(--color-border);
}

.var-table tbody td {
  padding: var(--space-xs) var(--space-sm);
  color: var(--color-text-primary);
  border-bottom: 1px solid var(--color-border-subtle);
}

.var-table td.numeric {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  text-align: right;
}
</style>
