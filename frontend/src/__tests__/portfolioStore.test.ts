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

  it('refreshAll calls axios.get exactly 5 times', async () => {
    const { usePortfolioStore } = await import('../stores/portfolio')
    const store = usePortfolioStore()

    // All 5 fetches resolve successfully
    mockedAxios.get = vi.fn()
      .mockResolvedValueOnce({ data: holdingsPayload() })
      .mockResolvedValueOnce({ data: pnlPayload() })
      .mockResolvedValueOnce({ data: [{ label: 'Tech', weight: 0.5, marketValue: 1000 }] })
      .mockResolvedValueOnce({ data: { content: [], totalPages: 1, totalElements: 0, number: 0, size: 10 } })
      .mockResolvedValueOnce({ data: { dates: [], portfolioSeries: [], benchmarkSeries: [] } })

    await store.refreshAll()

    expect(mockedAxios.get).toHaveBeenCalledTimes(5)
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
    let callCount = 0
    mockedAxios.get = vi.fn().mockImplementation(() => {
      callCount = callCount + 1
      const n = callCount
      // Batch A (calls 1–5): call 2 is pnl — make it slow
      if (n === 2) return slowPnlAPromise
      // Batch B (calls 6–10): all fast; call 7 is pnl for batch B
      if (n === 7) return Promise.resolve({ data: personaBPnl })
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
