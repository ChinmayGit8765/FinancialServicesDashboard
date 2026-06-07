import axios from 'axios'

// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already sets withCredentials: true and registers the CSRF request
// interceptor + 401 response interceptor globally on the shared axios singleton.
// This module simply imports axios and uses it (T-03-03).

// DTO interfaces — VERIFIED against Java records in
// backend/src/main/java/com/quantlens/portfolio/api/

export interface DateValueDto {
  date: string    // ISO date string e.g. "2022-09-12"
  value: number
}

export interface HoldingDto {
  ticker: string
  name: string
  sector: string
  quantity: number            // scale 4 — may be fractional
  avgCostBasis: number
  currentPrice: number
  currentMarketValue: number
  portfolioWeight: number     // 0–1, scale 6
  unrealizedPnlAbs: number
  unrealizedPnlPct: number    // scale 6
}

export interface PortfolioPnlDto {
  totalMarketValue: number
  totalCostBasis: number
  totalUnrealizedGainAbs: number
  totalUnrealizedGainPct: number  // scale 6
  dailyChangeAbs: number
  dailyChangePct: number          // scale 6
  equityCurve: DateValueDto[]     // 504 entries
}

export interface AllocationSliceDto {
  label: string
  weight: number       // 0–1, scale 6
  marketValue: number
}

export interface TransactionDto {
  txDate: string              // ISO date string e.g. "2022-09-12"
  txType: 'BUY' | 'SELL'
  ticker: string
  quantity: number
  price: number
  tradeValue: number
  runningCostBasis: number
  // NOTE: there is NO `id` field — verified against Java record (PLAN correction)
}

export interface BenchmarkComparisonDto {
  dates: string[]
  portfolioSeries: number[]    // rebased to 100.0000
  benchmarkSeries: number[]    // rebased to 100.0000
}

/**
 * Spring Page<T> wrapper returned by paginated endpoints.
 * `number` is the 0-indexed current page (matches Spring's Page.getNumber()).
 */
export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number    // 0-indexed current page
  size: number
}

/**
 * GET /api/portfolio/holdings — returns all holdings for the authenticated portfolio.
 */
export async function fetchHoldings(): Promise<HoldingDto[]> {
  const response = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
  return response.data
}

/**
 * GET /api/portfolio/pnl — returns P&L summary + equity curve for the portfolio.
 */
export async function fetchPnl(): Promise<PortfolioPnlDto> {
  const response = await axios.get<PortfolioPnlDto>('/api/portfolio/pnl')
  return response.data
}

/**
 * GET /api/portfolio/allocation — returns allocation slices for the pie/treemap chart.
 */
export async function fetchAllocation(): Promise<AllocationSliceDto[]> {
  const response = await axios.get<AllocationSliceDto[]>('/api/portfolio/allocation')
  return response.data
}

/**
 * GET /api/portfolio/transactions — returns a paginated list of transactions.
 * Default page size is 10 (backend default is 20; using 10 for the dashboard panel).
 * Sort: txDate DESC.
 */
export async function fetchTransactions(page = 0): Promise<PageResponse<TransactionDto>> {
  const response = await axios.get<PageResponse<TransactionDto>>(
    '/api/portfolio/transactions',
    { params: { page, size: 10 } }
  )
  return response.data
}

/**
 * GET /api/portfolio/benchmark — returns dual-series benchmark comparison data.
 */
export async function fetchBenchmark(): Promise<BenchmarkComparisonDto> {
  const response = await axios.get<BenchmarkComparisonDto>('/api/portfolio/benchmark')
  return response.data
}
