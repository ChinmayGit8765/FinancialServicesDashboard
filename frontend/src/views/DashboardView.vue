<script setup lang="ts">
import { onMounted, computed, ref } from 'vue'
import { usePortfolioStore } from '../stores/portfolio'
import { useAiStore } from '../stores/ai'
import { formatCurrency, formatSignedCurrency, formatSignedPercent, formatPercent } from '../utils/format'

import TopBar from '../components/TopBar.vue'
import KpiCard from '../components/KpiCard.vue'
import PnlChart from '../components/PnlChart.vue'
import BenchmarkChart from '../components/BenchmarkChart.vue'
import AllocationChart from '../components/AllocationChart.vue'
import HoldingsTable from '../components/HoldingsTable.vue'
import TransactionsTable from '../components/TransactionsTable.vue'
// SlotPlaceholder removed — AI Q&A slot replaced by ChatPanel (Phase 7)
import RiskScorecard from '../components/RiskScorecard.vue'
import CorrelationHeatmap from '../components/CorrelationHeatmap.vue'
import AttributionChart from '../components/AttributionChart.vue'
import PairsTable from '../components/PairsTable.vue'
import MonteCarloFanChart from '../components/MonteCarloFanChart.vue'

// Phase-6 AI components
import CommentaryCard from '../components/ai/CommentaryCard.vue'
import ExplainDrawer from '../components/ai/ExplainDrawer.vue'
import BYOKeyModal from '../components/ai/BYOKeyModal.vue'
import StructuredOutputChart from '../components/ai/StructuredOutputChart.vue'
// Phase-7 AI Q&A
import ChatPanel from '../components/ai/ChatPanel.vue'

const portfolioStore = usePortfolioStore()
const aiStore = useAiStore()

// AI panel local state
const explainOpen = ref(false)
const keyModalOpen = ref(false)

onMounted(() => {
  void portfolioStore.refreshAll()
  void aiStore.fetchStatus()
  void aiStore.fetchCommentary()
  void aiStore.fetchStructured()
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

// --- Risk KPI derived values (access risk resource whole, never destructure) ---

const riskKpiLoading = computed(() => portfolioStore.risk.loading)

// Sharpe ratio KPI for the strip — shows "—" when not yet loaded
const sharpePrimary = computed(() =>
  portfolioStore.risk.data ? portfolioStore.risk.data.sharpeRatio.toFixed(2) : '—'
)

// Annualized volatility KPI (formatPercent expects 0-1 fraction)
const volatilityPrimary = computed(() =>
  portfolioStore.risk.data
    ? formatPercent(portfolioStore.risk.data.annualizedVolatility)
    : '—'
)

// --- Handlers ---------------------------------------------------------------

function handleTransactionsPage(page: number): void {
  portfolioStore.fetchTransactions(page)
}

function retryHoldings(): void { portfolioStore.fetchHoldings() }
function retryPnl(): void { portfolioStore.fetchPnl() }
function retryAllocation(): void { portfolioStore.fetchAllocation() }
function retryBenchmark(): void { portfolioStore.fetchBenchmark() }
function retryRisk(): void { portfolioStore.fetchRisk() }
function retryCorrelation(): void { portfolioStore.fetchCorrelation() }
function retryAttribution(): void { portfolioStore.fetchAttribution() }
function retryPairs(): void { portfolioStore.fetchPairs() }
function retryForecast(): void { portfolioStore.fetchForecast() }
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
          <!-- Phase 4: real Sharpe and Volatility KPI cards -->
          <KpiCard
            label="Sharpe Ratio"
            :primary="sharpePrimary"
            :loading="riskKpiLoading"
          />
          <KpiCard
            label="Ann. Volatility"
            :primary="volatilityPrimary"
            :loading="riskKpiLoading"
          />
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
          <RiskScorecard
            :risk="portfolioStore.risk.data"
            :loading="portfolioStore.risk.loading"
            :error="portfolioStore.risk.error"
            @retry="retryRisk"
          />
        </div>

        <!-- Row 4: Correlation Heatmap (col 12) -->
        <div class="col-12">
          <CorrelationHeatmap
            :correlation="portfolioStore.correlation.data"
            :loading="portfolioStore.correlation.loading"
            :error="portfolioStore.correlation.error"
            @retry="retryCorrelation"
          />
        </div>

        <!-- Row 4b: Attribution (col 6) + Pairs (col 6) — Phase 4 new rows -->
        <div class="col-6">
          <AttributionChart
            :attribution="portfolioStore.attribution.data"
            :loading="portfolioStore.attribution.loading"
            :error="portfolioStore.attribution.error"
            @retry="retryAttribution"
          />
        </div>
        <div class="col-6">
          <PairsTable
            :pairs="portfolioStore.pairs.data"
            :loading="portfolioStore.pairs.loading"
            :error="portfolioStore.pairs.error"
            @retry="retryPairs"
          />
        </div>

        <!-- Row 5: Monte Carlo fan chart (col 12) — Phase 5 -->
        <div class="col-12">
          <MonteCarloFanChart
            :forecast="portfolioStore.forecast.data"
            :loading="portfolioStore.forecast.loading"
            :error="portfolioStore.forecast.error"
            @retry="retryForecast"
            @update:model="(m) => portfolioStore.fetchForecast(m)"
          />
        </div>

        <!-- Row 6: Holdings table (col 12) — row click emits explain(ticker) → ExplainDrawer -->
        <div class="col-12">
          <HoldingsTable
            :holdings="portfolioStore.holdings.data"
            :loading="portfolioStore.holdings.loading"
            :error="portfolioStore.holdings.error"
            @retry="retryHoldings"
            @explain="(ticker) => { explainOpen = true; void aiStore.fetchExplanation(ticker) }"
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

        <!-- Row 8: AI Daily Commentary (col 12) — Phase 6 -->
        <div class="col-12">
          <CommentaryCard
            :commentary="aiStore.commentary.data"
            :loading="aiStore.commentary.loading"
            :error="aiStore.commentary.error"
            @retry="aiStore.fetchCommentary()"
          />
        </div>

        <!-- Row 9: Structured Output Chart (col 8) + BYO Key slot (col 4) — Phase 6 -->
        <div class="col-8">
          <!-- Structured-output chart: seeded demo fixture (Phase 6 AI panel).
               Phase 8 (AI-06) will swap source to live BeanOutputConverter typed record. -->
          <StructuredOutputChart
            :structured="aiStore.structured.data"
            :loading="aiStore.structured.loading"
            :error="aiStore.structured.error"
            @retry="aiStore.fetchStructured()"
          />
          <!-- AI Q&A RAG panel — Phase 7 -->
          <ChatPanel
            :messages="aiStore.chatMessages"
            :loading="aiStore.chatLoading"
            :error="aiStore.chatError"
            @send="aiStore.sendMessage($event)"
            style="margin-top: 16px;"
          />
        </div>
        <div class="col-4">
          <!-- Connect Live AI trigger + BYO-key popup -->
          <div class="connect-ai-panel">
            <button
              class="connect-ai-btn"
              @click="keyModalOpen = true"
            >
              Connect Live AI
            </button>
            <p class="connect-ai-hint">
              Paste your own Anthropic or OpenAI key to switch from demo to live AI responses.
            </p>
          </div>
          <BYOKeyModal
            :open="keyModalOpen"
            @close="keyModalOpen = false"
            @submitted="aiStore.fetchStatus(); aiStore.fetchCommentary()"
          />
        </div>

        <!-- ExplainDrawer — renders outside grid flow (position: fixed) -->
        <ExplainDrawer
          :open="explainOpen"
          :narrative="aiStore.explanation.data?.narrative ?? null"
          :loading="aiStore.explanation.loading"
          :error="aiStore.explanation.error"
          @close="explainOpen = false"
        />

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

/* Connect Live AI panel (LLM Key slot) */
.connect-ai-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  align-items: flex-start;
}

.connect-ai-btn {
  min-height: 40px;
  padding: 8px 20px;
  background: var(--color-accent);
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-md);
  color: var(--color-bg-base);
  font-size: 14px;
  font-weight: 600;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s;
}

.connect-ai-btn:hover {
  background: var(--color-accent-light);
  border-color: var(--color-accent-light);
}

.connect-ai-btn:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}

.connect-ai-hint {
  font-size: 13px;
  color: var(--color-text-muted);
  margin: 0;
  line-height: 1.5;
}

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
