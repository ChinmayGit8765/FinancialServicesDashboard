<script setup lang="ts">
import { onMounted, computed } from 'vue'
import { usePortfolioStore } from '../stores/portfolio'
import { formatCurrency, formatSignedCurrency, formatSignedPercent } from '../utils/format'

import TopBar from '../components/TopBar.vue'
import KpiCard from '../components/KpiCard.vue'
import PnlChart from '../components/PnlChart.vue'
import BenchmarkChart from '../components/BenchmarkChart.vue'
import AllocationChart from '../components/AllocationChart.vue'
import HoldingsTable from '../components/HoldingsTable.vue'
import TransactionsTable from '../components/TransactionsTable.vue'
import SlotPlaceholder from '../components/SlotPlaceholder.vue'

const portfolioStore = usePortfolioStore()

onMounted(() => {
  portfolioStore.refreshAll()
})

// --- KPI derived values (access pnl resource whole, never destructure) ------

const kpiLoading = computed(() => portfolioStore.pnl.loading)

const marketValuePrimary = computed(() =>
  portfolioStore.pnl.data ? formatCurrency(portfolioStore.pnl.data.totalMarketValue) : '—'
)

const unrealizedPrimary = computed(() =>
  portfolioStore.pnl.data
    ? formatSignedCurrency(portfolioStore.pnl.data.totalUnrealizedGainAbs)
    : '—'
)

const unrealizedSecondary = computed(() =>
  portfolioStore.pnl.data
    ? formatSignedPercent(portfolioStore.pnl.data.totalUnrealizedGainPct * 100)
    : undefined
)

const unrealizedDelta = computed(() =>
  portfolioStore.pnl.data ? portfolioStore.pnl.data.totalUnrealizedGainPct * 100 : undefined
)

const dailyChangePrimary = computed(() =>
  portfolioStore.pnl.data
    ? formatSignedCurrency(portfolioStore.pnl.data.dailyChangeAbs)
    : '—'
)

const dailyChangeSecondary = computed(() =>
  portfolioStore.pnl.data
    ? formatSignedPercent(portfolioStore.pnl.data.dailyChangePct * 100)
    : undefined
)

const dailyChangeDelta = computed(() =>
  portfolioStore.pnl.data ? portfolioStore.pnl.data.dailyChangePct * 100 : undefined
)

// --- Handlers ---------------------------------------------------------------

function handleTransactionsPage(page: number): void {
  portfolioStore.fetchTransactions(page)
}

function retryHoldings(): void { portfolioStore.fetchHoldings() }
function retryPnl(): void { portfolioStore.fetchPnl() }
function retryAllocation(): void { portfolioStore.fetchAllocation() }
function retryBenchmark(): void { portfolioStore.fetchBenchmark() }
</script>

<template>
  <div class="dashboard-page">
    <TopBar />

    <main class="dashboard-main">
      <div class="dashboard-grid">

        <!-- KPI Strip: 3 real + 2 Phase-4 placeholders -->
        <div class="kpi-strip">
          <KpiCard
            label="Market Value"
            :primary="marketValuePrimary"
            :loading="kpiLoading"
          />
          <KpiCard
            label="Unrealized P&amp;L"
            :primary="unrealizedPrimary"
            :secondary="unrealizedSecondary"
            :delta="unrealizedDelta"
            :loading="kpiLoading"
          />
          <KpiCard
            label="Daily Change"
            :primary="dailyChangePrimary"
            :secondary="dailyChangeSecondary"
            :delta="dailyChangeDelta"
            :loading="kpiLoading"
          />
          <!-- Phase 4 placeholder KPI cards -->
          <div class="kpi-placeholder" title="Available in Phase 4">
            <KpiCard
              label="Risk Score — Phase 4"
              primary="—"
              :loading="false"
            />
          </div>
          <div class="kpi-placeholder" title="Available in Phase 4">
            <KpiCard
              label="Sharpe Ratio — Phase 4"
              primary="—"
              :loading="false"
            />
          </div>
        </div>

        <!-- Row 2: P&L Chart (col 7) + Benchmark Chart (col 5) -->
        <div class="col-7">
          <PnlChart
            :pnl="portfolioStore.pnl.data"
            :loading="portfolioStore.pnl.loading"
            :error="portfolioStore.pnl.error"
            @retry="retryPnl"
          />
        </div>
        <div class="col-5">
          <BenchmarkChart
            :benchmark="portfolioStore.benchmark.data"
            :loading="portfolioStore.benchmark.loading"
            :error="portfolioStore.benchmark.error"
            @retry="retryBenchmark"
          />
        </div>

        <!-- Row 3: Allocation (col 6) + Risk Scorecard slot (col 6) -->
        <div class="col-6">
          <AllocationChart
            :allocation="portfolioStore.allocation.data"
            :loading="portfolioStore.allocation.loading"
            :error="portfolioStore.allocation.error"
            @retry="retryAllocation"
          />
        </div>
        <div class="col-6">
          <SlotPlaceholder label="Risk Scorecard — Phase 4" minHeight="280px" />
        </div>

        <!-- Row 4: Correlation Heatmap slot (col 12) -->
        <div class="col-12">
          <SlotPlaceholder label="Correlation Heatmap — Phase 4" minHeight="240px" />
        </div>

        <!-- Row 5: Monte Carlo slot (col 12) -->
        <div class="col-12">
          <SlotPlaceholder label="Monte Carlo Forecast — Phase 5" minHeight="320px" />
        </div>

        <!-- Row 6: Holdings table (col 12) -->
        <div class="col-12">
          <HoldingsTable
            :holdings="portfolioStore.holdings.data"
            :loading="portfolioStore.holdings.loading"
            :error="portfolioStore.holdings.error"
            @retry="retryHoldings"
          />
        </div>

        <!-- Row 7: Transactions table (col 12) — @page-change wired to fetchTransactions -->
        <div class="col-12">
          <TransactionsTable
            :page="portfolioStore.transactions.data"
            :loading="portfolioStore.transactions.loading"
            :error="portfolioStore.transactions.error"
            @page-change="handleTransactionsPage"
          />
        </div>

        <!-- Row 8: AI Commentary slot (col 12) -->
        <div class="col-12">
          <SlotPlaceholder label="AI Daily Commentary — Phase 6" minHeight="120px" />
        </div>

        <!-- Row 9: AI Q&A (col 8) + BYO Key (col 4) -->
        <div class="col-8">
          <SlotPlaceholder label="AI Q&amp;A — Phase 6" minHeight="400px" />
        </div>
        <div class="col-4">
          <SlotPlaceholder label="LLM Key — Phase 6" minHeight="320px" />
        </div>

      </div>
    </main>
  </div>
</template>

<style scoped>
.dashboard-page {
  min-height: 100vh;
  background: var(--color-bg-base);
  color: var(--color-text-primary);
}

.dashboard-main {
  padding-top: 48px; /* top-bar height */
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: var(--space-xl);
  padding: var(--space-lg);
}

/* KPI strip: auto-fit row spanning full width */
.kpi-strip {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: var(--space-md);
}

/* Phase-4 KPI placeholders: 50% opacity per UI-SPEC */
.kpi-placeholder {
  opacity: 0.5;
}

/* Column span utilities */
.col-4  { grid-column: span 4; }
.col-5  { grid-column: span 5; }
.col-6  { grid-column: span 6; }
.col-7  { grid-column: span 7; }
.col-8  { grid-column: span 8; }
.col-12 { grid-column: 1 / -1; }

/* Responsive: ≤ 1279px — charts stack to full width */
@media (max-width: 1279px) {
  .col-4,
  .col-5,
  .col-6,
  .col-7,
  .col-8 {
    grid-column: 1 / -1;
  }

  .dashboard-grid {
    padding: var(--space-md);
  }
}
</style>
