import { defineStore } from 'pinia'
import { reactive } from 'vue'
import axios from 'axios'
import type {
  HoldingDto,
  PortfolioPnlDto,
  AllocationSliceDto,
  TransactionDto,
  BenchmarkComparisonDto,
  PageResponse,
} from '../api/portfolio'
import type {
  RiskScorecardDto,
  CorrelationMatrixDto,
  AttributionDto,
  PairResultDto,
} from '../api/analytics'

// Per-resource async state shape.
// Components access resources whole — do NOT destructure (Pitfall 5, loses reactivity).
interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

function asyncState<T>(init: T | null = null): AsyncState<T> {
  // Cast required: reactive() unwraps nested refs which causes 'UnwrapRef<T>' vs 'T'
  // type mismatch for generic T. The cast is safe — the runtime shape is identical.
  return reactive({ data: init, loading: false, error: null }) as AsyncState<T>
}

// Module-scoped refresh counter — incremented on every refreshAll() call to
// detect stale persona-switch results (race guard).
let refreshVersion = 0

export const usePortfolioStore = defineStore('portfolio', () => {
  // --- per-resource reactive state -------------------------------------------

  const holdings    = asyncState<HoldingDto[]>(null)
  const pnl         = asyncState<PortfolioPnlDto>(null)
  const allocation  = asyncState<AllocationSliceDto[]>(null)
  const transactions = asyncState<PageResponse<TransactionDto>>(null)
  const benchmark   = asyncState<BenchmarkComparisonDto>(null)

  // --- Phase-4 analytics state ------------------------------------------------
  const risk        = asyncState<RiskScorecardDto>(null)
  const correlation = asyncState<CorrelationMatrixDto>(null)
  const attribution = asyncState<AttributionDto>(null)
  const pairs       = asyncState<PairResultDto[]>(null)

  // --- fetch actions ----------------------------------------------------------

  /**
   * Fetch all holdings for the authenticated portfolio.
   * On 401 → sets error "Session expired" (global interceptor also fires /login redirect).
   * On other error → sets error "Failed to load holdings".
   * Never rethrows — errors become .error strings (T-03-05).
   *
   * version: if provided, the write is abandoned when a newer refreshAll() has
   * started — prevents a slow stale batch from overwriting fresh persona data.
   */
  async function fetchHoldings(version?: number): Promise<void> {
    holdings.loading = true
    holdings.error = null
    try {
      const { data } = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
      if (version !== undefined && version !== refreshVersion) return
      holdings.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      holdings.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load holdings'
    } finally {
      holdings.loading = false
    }
  }

  /**
   * Fetch P&L summary + equity curve.
   *
   * version: see fetchHoldings for race-guard semantics.
   */
  async function fetchPnl(version?: number): Promise<void> {
    pnl.loading = true
    pnl.error = null
    try {
      const { data } = await axios.get<PortfolioPnlDto>('/api/portfolio/pnl')
      if (version !== undefined && version !== refreshVersion) return
      pnl.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      pnl.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load P&L'
    } finally {
      pnl.loading = false
    }
  }

  /**
   * Fetch allocation slices for the pie / treemap chart.
   *
   * version: see fetchHoldings for race-guard semantics.
   */
  async function fetchAllocation(version?: number): Promise<void> {
    allocation.loading = true
    allocation.error = null
    try {
      const { data } = await axios.get<AllocationSliceDto[]>('/api/portfolio/allocation')
      if (version !== undefined && version !== refreshVersion) return
      allocation.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      allocation.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load allocation'
    } finally {
      allocation.loading = false
    }
  }

  /**
   * Fetch a single page of transactions (0-indexed).
   *
   * version: see fetchHoldings for race-guard semantics.
   */
  async function fetchTransactions(page = 0, version?: number): Promise<void> {
    transactions.loading = true
    transactions.error = null
    try {
      const { data } = await axios.get<PageResponse<TransactionDto>>(
        '/api/portfolio/transactions',
        { params: { page, size: 10 } }
      )
      if (version !== undefined && version !== refreshVersion) return
      transactions.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      transactions.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load transactions'
    } finally {
      transactions.loading = false
    }
  }

  /**
   * Fetch benchmark comparison series.
   *
   * version: see fetchHoldings for race-guard semantics.
   */
  async function fetchBenchmark(version?: number): Promise<void> {
    benchmark.loading = true
    benchmark.error = null
    try {
      const { data } = await axios.get<BenchmarkComparisonDto>('/api/portfolio/benchmark')
      if (version !== undefined && version !== refreshVersion) return
      benchmark.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      benchmark.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load benchmark'
    } finally {
      benchmark.loading = false
    }
  }

  // --- Phase-4 analytics fetch actions ----------------------------------------

  /**
   * Fetch risk scorecard metrics (Sharpe, vol, VaR, drawdown, beta).
   * version: race-guard — see fetchHoldings for semantics.
   */
  async function fetchRisk(version?: number): Promise<void> {
    risk.loading = true
    risk.error = null
    try {
      const { data } = await axios.get<RiskScorecardDto>('/api/portfolio/risk')
      if (version !== undefined && version !== refreshVersion) return
      risk.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      risk.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load risk metrics'
    } finally {
      risk.loading = false
    }
  }

  /**
   * Fetch pairwise correlation matrix.
   * version: race-guard — see fetchHoldings for semantics.
   */
  async function fetchCorrelation(version?: number): Promise<void> {
    correlation.loading = true
    correlation.error = null
    try {
      const { data } = await axios.get<CorrelationMatrixDto>('/api/portfolio/correlation')
      if (version !== undefined && version !== refreshVersion) return
      correlation.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      correlation.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load correlation data'
    } finally {
      correlation.loading = false
    }
  }

  /**
   * Fetch Fama-French factor attribution.
   * version: race-guard — see fetchHoldings for semantics.
   */
  async function fetchAttribution(version?: number): Promise<void> {
    attribution.loading = true
    attribution.error = null
    try {
      const { data } = await axios.get<AttributionDto>('/api/portfolio/attribution')
      if (version !== undefined && version !== refreshVersion) return
      attribution.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      attribution.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load attribution'
    } finally {
      attribution.loading = false
    }
  }

  /**
   * Fetch cointegration pairs scanner results.
   * version: race-guard — see fetchHoldings for semantics.
   */
  async function fetchPairs(version?: number): Promise<void> {
    pairs.loading = true
    pairs.error = null
    try {
      const { data } = await axios.get<PairResultDto[]>('/api/portfolio/pairs')
      if (version !== undefined && version !== refreshVersion) return
      pairs.data = data
    } catch (e: any) {
      if (version !== undefined && version !== refreshVersion) return
      pairs.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load pairs data'
    } finally {
      pairs.loading = false
    }
  }

  // --- refreshAll ------------------------------------------------------------

  /**
   * Run all five fetches in parallel.
   *
   * Race-safe: increments a module-scoped refreshVersion counter before
   * launching sub-fetches. Each sub-fetch receives the captured version and
   * will discard its write if a newer refreshAll() has incremented the counter
   * in the meantime (e.g. rapid persona switch). This prevents a slow stale
   * batch from overwriting fresh data written by a faster, newer batch.
   *
   * Promise.allSettled ensures all fetches run even if some fail. Individual
   * errors appear in each resource's .error field; refreshAll itself never throws.
   */
  async function refreshAll(): Promise<void> {
    const myVersion = ++refreshVersion
    await Promise.allSettled([
      fetchHoldings(myVersion),
      fetchPnl(myVersion),
      fetchAllocation(myVersion),
      fetchTransactions(0, myVersion),
      fetchBenchmark(myVersion),
      fetchRisk(myVersion),
      fetchCorrelation(myVersion),
      fetchAttribution(myVersion),
      fetchPairs(myVersion),
    ])
  }

  // --- manual reset -----------------------------------------------------------

  /**
   * Clear all resource state — called on logout or persona switch start.
   * Pinia setup stores do not auto-$reset; implement manually.
   */
  function $reset(): void {
    holdings.data    = null; holdings.loading    = false; holdings.error    = null
    pnl.data         = null; pnl.loading         = false; pnl.error         = null
    allocation.data  = null; allocation.loading  = false; allocation.error  = null
    transactions.data = null; transactions.loading = false; transactions.error = null
    benchmark.data   = null; benchmark.loading   = false; benchmark.error   = null
    risk.data        = null; risk.loading        = false; risk.error        = null
    correlation.data = null; correlation.loading = false; correlation.error = null
    attribution.data = null; attribution.loading = false; attribution.error = null
    pairs.data       = null; pairs.loading       = false; pairs.error       = null
  }

  return {
    // state
    holdings,
    pnl,
    allocation,
    transactions,
    benchmark,
    risk,
    correlation,
    attribution,
    pairs,
    // actions
    fetchHoldings,
    fetchPnl,
    fetchAllocation,
    fetchTransactions,
    fetchBenchmark,
    fetchRisk,
    fetchCorrelation,
    fetchAttribution,
    fetchPairs,
    refreshAll,
    $reset,
  }
})
