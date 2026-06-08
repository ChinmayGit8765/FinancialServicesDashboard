// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already sets withCredentials: true and registers the CSRF request
// interceptor + 401 response interceptor globally on the shared axios singleton.
// This module declares DTO interfaces only — no fetch functions.
// Actual fetch calls live in the Pinia portfolio store (same pattern as portfolio.ts).

// DTO interfaces — VERIFIED against Java records in
// backend/src/main/java/com/quantlens/analytics/api/

export interface VarResultDto {
  method: string        // "HISTORICAL" | "PARAMETRIC" | "CVaR_HISTORICAL"
  confidence: number    // 0.95
  horizonDays: number   // 1
  amount: number        // positive monetary VaR (BigDecimal serialized as number)
  percentage: number    // fraction e.g. 0.018 = 1.8%
}

export interface RiskScorecardDto {
  sharpeRatio: number
  annualizedVolatility: number
  maxDrawdown: number           // negative; e.g. -0.18 = 18% drawdown from peak
  beta: number
  var: VarResultDto[]           // 2–3 entries: HISTORICAL, PARAMETRIC, optionally CVaR_HISTORICAL
}

export interface CorrelationMatrixDto {
  tickers: string[]
  matrix: number[][]            // tickers.length × tickers.length; diagonal = 1.0
}

export interface AttributionDto {
  alphaAnnualized: number       // intercept × 252
  betaMkt: number
  betaSmb: number
  betaHml: number
  rSquared: number
  contribMktAnnualized: number  // betaMkt × mean(MktRf) × 252
  contribSmbAnnualized: number
  contribHmlAnnualized: number
}

export interface PairResultDto {
  tickerY: string
  tickerX: string
  hedgeRatio: number
  adfStatistic: number
  pValue: number
  spreadZScore: number
  signal: 'LONG_Y_SHORT_X' | 'SHORT_Y_LONG_X' | 'NEUTRAL'
}
