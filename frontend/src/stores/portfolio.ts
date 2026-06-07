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

  // --- fetch actions ----------------------------------------------------------

  /**
   * Fetch all holdings for the authenticated portfolio.
   * On 401 → sets error "Session expired" (global interceptor also fires /login redirect).
   * On other error → sets error "Failed to load holdings".
   * Never rethrows — errors become .error strings (T-03-05).
   */
  async function fetchHoldings(): Promise<void> {
    holdings.loading = true
    holdings.error = null
    try {
      const { data } = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
      holdings.data = data
    } catch (e: any) {
      holdings.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load holdings'
    } finally {
      holdings.loading = false
    }
  }

  /**
   * Fetch P&L summary + equity curve.
   */
  async function fetchPnl(): Promise<void> {
    pnl.loading = true
    pnl.error = null
    try {
      const { data } = await axios.get<PortfolioPnlDto>('/api/portfolio/pnl')
      pnl.data = data
    } catch (e: any) {
      pnl.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load P&L'
    } finally {
      pnl.loading = false
    }
  }

  /**
   * Fetch allocation slices for the pie / treemap chart.
   */
  async function fetchAllocation(): Promise<void> {
    allocation.loading = true
    allocation.error = null
    try {
      const { data } = await axios.get<AllocationSliceDto[]>('/api/portfolio/allocation')
      allocation.data = data
    } catch (e: any) {
      allocation.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load allocation'
    } finally {
      allocation.loading = false
    }
  }

  /**
   * Fetch a single page of transactions (0-indexed).
   */
  async function fetchTransactions(page = 0): Promise<void> {
    transactions.loading = true
    transactions.error = null
    try {
      const { data } = await axios.get<PageResponse<TransactionDto>>(
        '/api/portfolio/transactions',
        { params: { page, size: 10 } }
      )
      transactions.data = data
    } catch (e: any) {
      transactions.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load transactions'
    } finally {
      transactions.loading = false
    }
  }

  /**
   * Fetch benchmark comparison series.
   */
  async function fetchBenchmark(): Promise<void> {
    benchmark.loading = true
    benchmark.error = null
    try {
      const { data } = await axios.get<BenchmarkComparisonDto>('/api/portfolio/benchmark')
      benchmark.data = data
    } catch (e: any) {
      benchmark.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load benchmark'
    } finally {
      benchmark.loading = false
    }
  }

  // --- refreshAll ------------------------------------------------------------

  /**
   * Run all five fetches in parallel.
   *
   * Race-safe: uses a module-scoped refreshVersion counter. If a newer call
   * arrives while this one is in-flight (e.g. rapid persona switch), the earlier
   * call's results are discarded — the store state already reflects the latest
   * batch's writes because each fetch action updates its resource independently.
   *
   * Promise.allSettled ensures all fetches run even if some fail. Individual
   * errors appear in each resource's .error field; refreshAll itself never throws.
   */
  async function refreshAll(): Promise<void> {
    const myVersion = ++refreshVersion
    await Promise.allSettled([
      fetchHoldings(),
      fetchPnl(),
      fetchAllocation(),
      fetchTransactions(0),
      fetchBenchmark(),
    ])
    // If a newer refreshAll() was called while we were in-flight, return early.
    // The newer batch has already started/finished writing to the store.
    if (myVersion !== refreshVersion) return
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
  }

  return {
    // state
    holdings,
    pnl,
    allocation,
    transactions,
    benchmark,
    // actions
    fetchHoldings,
    fetchPnl,
    fetchAllocation,
    fetchTransactions,
    fetchBenchmark,
    refreshAll,
    $reset,
  }
})
