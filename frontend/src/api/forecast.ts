// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already sets withCredentials: true and registers the CSRF request
// interceptor + 401 response interceptor globally on the shared axios singleton.
// This module declares DTO interfaces only — no fetch functions.
// Actual fetch calls live in the Pinia portfolio store (same pattern as analytics.ts).

// DTO interfaces — VERIFIED against Java records in
// backend/src/main/java/com/quantlens/analytics/api/

export type ModelType = 'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP'

export interface ForecastDto {
  model:       ModelType
  horizonDays: number
  p5:          number[]
  p25:         number[]
  p50:         number[]
  p75:         number[]
  p95:         number[]
}
