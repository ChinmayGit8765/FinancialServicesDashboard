/**
 * portfolioStore.test.ts — unit tests for usePortfolioStore
 *
 * Pattern: setActivePinia(createPinia()) + vi.mock('axios')
 * NOT @pinia/testing (Pitfall 8 — createTestingPinia bypasses the real store logic)
 */
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'

vi.mock('axios')

// Cast the mocked axios to a type that exposes vi.fn() helpers
const mockedAxios = vi.mocked(axios, true)

beforeEach(() => {
  setActivePinia(createPinia())
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

// ---- helpers ------------------------------------------------------------------

function pnlPayload() {
  return {
    totalMarketValue: 123456.78,
    totalCostBasis: 100000,
    totalUnrealizedGainAbs: 23456.78,
    totalUnrealizedGainPct: 0.234568,
    dailyChangeAbs: 1234.56,
    dailyChangePct: 0.010000,
    equityCurve: [{ date: '2022-09-12', value: 100.0 }],
  }
}

function holdingsPayload() {
  return [
    {
      ticker: 'AAPL', name: 'Apple Inc.', sector: 'Technology',
      quantity: 10, avgCostBasis: 150, currentPrice: 175,
      currentMarketValue: 1750, portfolioWeight: 0.25,
      unrealizedPnlAbs: 250, unrealizedPnlPct: 0.1667,
    },
  ]
}

// ---- tests --------------------------------------------------------------------

describe('usePortfolioStore', () => {
  it('fetchPnl success: sets pnl.data and clears loading/error', async () => {
    // Lazy-import inside test so vi.mock('axios') is already in effect
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    // Arrange
    mockedAxios.get = vi.fn().mockResolvedValueOnce({ data: pnlPayload() })

    // Pre-condition
    expect(store.pnl.loading).toBe(false)
    expect(store.pnl.data).toBeNull()

    // Act
    const fetchPromise = store.fetchPnl()

    // loading should be true while fetch is in-flight
    expect(store.pnl.loading).toBe(true)

    await fetchPromise

    // Assert
    expect(store.pnl.loading).toBe(false)
    expect(store.pnl.error).toBeNull()
    expect(store.pnl.data?.totalMarketValue).toBe(123456.78)
  })

  it('fetchPnl on 500: sets pnl.error to "Failed to load P&L" and clears loading', async () => {
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    mockedAxios.get = vi.fn().mockRejectedValueOnce({ response: { status: 500 } })

    await store.fetchPnl()

    expect(store.pnl.loading).toBe(false)
    expect(store.pnl.data).toBeNull()
    expect(store.pnl.error).toContain('Failed to load P&L')
  })

  it('fetchHoldings on 401: sets holdings.error to "Session expired"', async () => {
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    mockedAxios.get = vi.fn().mockRejectedValueOnce({ response: { status: 401 } })

    await store.fetchHoldings()

    expect(store.holdings.loading).toBe(false)
    expect(store.holdings.error).toBe('Session expired')
    expect(store.holdings.data).toBeNull()
  })

  it('refreshAll calls axios.get exactly 10 times (5 Phase-3 + 4 Phase-4 analytics + 1 Phase-5 forecast)', async () => {
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    // All 10 fetches resolve successfully
    mockedAxios.get = vi.fn()
      .mockResolvedValueOnce({ data: holdingsPayload() })
      .mockResolvedValueOnce({ data: pnlPayload() })
      .mockResolvedValueOnce({ data: [{ label: 'Tech', weight: 0.5, marketValue: 1000 }] })
      .mockResolvedValueOnce({ data: { content: [], totalPages: 1, totalElements: 0, number: 0, size: 10 } })
      .mockResolvedValueOnce({ data: { dates: [], portfolioSeries: [], benchmarkSeries: [] } })
      // Phase-4 analytics:
      .mockResolvedValueOnce({ data: { sharpeRatio: 0.5, annualizedVolatility: 0.2, maxDrawdown: -0.1, beta: 1.0, var: [] } })
      .mockResolvedValueOnce({ data: { tickers: [], matrix: [] } })
      .mockResolvedValueOnce({ data: { alphaAnnualized: 0.01, betaMkt: 0.9, betaSmb: 0.1, betaHml: 0.05, rSquared: 0.85, contribMktAnnualized: 0.08, contribSmbAnnualized: 0.01, contribHmlAnnualized: 0.005 } })
      .mockResolvedValueOnce({ data: [] })
      // Phase-5 forecast:
      .mockResolvedValueOnce({ data: { model: 'GBM', horizonDays: 252, p5: [], p25: [], p50: [], p75: [], p95: [] } })

    await store.refreshAll()

    expect(mockedAxios.get).toHaveBeenCalledTimes(10)
  })

  /**
   * CR-01 regression test: rapid persona switch race condition.
   *
   * Scenario: persona A (first refreshAll) is slow; persona B (second refreshAll)
   * resolves first and writes B-data to the store. Then A's slow fetches resolve.
   * After the fix, A's writes must be discarded — the store must hold B's data.
   *
   * Before the fix, each sub-fetch wrote unconditionally, so A's slow pnl resolve
   * would overwrite B's pnl data. This test catches that regression.
   */
  it('CR-01 race guard: stale first-batch pnl does not overwrite second-batch pnl data', async () => {
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    // Distinct pnl payloads so we can assert which batch "won"
    const personaAPnl = {
      totalMarketValue: 111111.11,
      totalCostBasis: 100000,
      totalUnrealizedGainAbs: 11111.11,
      totalUnrealizedGainPct: 0.111111,
      dailyChangeAbs: 111.11,
      dailyChangePct: 0.001111,
      equityCurve: [{ date: '2022-01-01', value: 100.0 }],
    }
    const personaBPnl = {
      totalMarketValue: 999999.99,
      totalCostBasis: 100000,
      totalUnrealizedGainAbs: 899999.99,
      totalUnrealizedGainPct: 8.99999,
      dailyChangeAbs: 999.99,
      dailyChangePct: 0.009999,
      equityCurve: [{ date: '2022-12-31', value: 200.0 }],
    }

    // Slow promise that we control: represents persona A's pnl fetch (call #2, index 1)
    let resolveSlowPnlA!: (v: any) => void
    const slowPnlAPromise = new Promise<any>(resolve => { resolveSlowPnlA = resolve })

    // Track how many axios.get calls have been made total across both batches
    // refreshAll now issues 10 calls (5 Phase-3 + 4 Phase-4 analytics + 1 Phase-5 forecast per batch).
    // Pnl is always the 2nd call within a batch.
    // Batch A: calls 1–10 (pnl = call 2); Batch B: calls 11–20 (pnl = call 12).
    let callCount = 0
    mockedAxios.get = vi.fn().mockImplementation(() => {
      callCount = callCount + 1
      const n = callCount
      // Batch A (calls 1–10): call 2 is pnl — make it slow
      if (n === 2) return slowPnlAPromise
      // Batch B (calls 11–20): all fast; call 12 is pnl for batch B
      if (n === 12) return Promise.resolve({ data: personaBPnl })
      // Everything else resolves immediately with a generic payload
      return Promise.resolve({ data: pnlPayload() })
    })

    // Launch batch A (persona A) — pnl call hangs
    const firstRefresh = store.refreshAll()

    // Immediately launch batch B (persona B) — this bumps refreshVersion
    const secondRefresh = store.refreshAll()

    // Batch B resolves fully first (all fast)
    await secondRefresh
    // At this point the store should hold B's pnl data
    expect(store.pnl.data?.totalMarketValue).toBe(999999.99)

    // Now unblock persona A's slow pnl fetch — after the fix this write is discarded
    resolveSlowPnlA({ data: personaAPnl })
    await firstRefresh

    // Store must still hold persona B's data — A's stale write was rejected by the guard
    expect(store.pnl.data?.totalMarketValue).toBe(999999.99)
    expect(store.pnl.data?.totalUnrealizedGainPct).toBe(8.99999)
  })
})
