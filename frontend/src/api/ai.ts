// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already sets withCredentials: true and registers the CSRF request
// interceptor + 401 response interceptor globally on the shared axios singleton.
// This module declares DTO interfaces only — no fetch functions.
// Actual fetch calls live in the Pinia ai store (same pattern as portfolio.ts).

// DTO interfaces — VERIFIED against Java records in
// backend/src/main/java/com/quantlens/ai/api/

export interface AiStatus {
  mode: 'demo' | 'live'
  provider?: 'anthropic' | 'openai'
}

export interface ExplainResponse {
  narrative: string
}

export interface CommentaryDto {
  headline: string
  body: string
  bulletPoints: string[]
}

/**
 * Typed DTO for the structured-output chart — mirrors the Phase-8 live BeanOutputConverter
 * record shape so the chart component is source-agnostic (demo stub feeds this from
 * /ai-structured-demo.json; Phase 8 swaps source to live typed record with no chart changes).
 */
export interface StructuredChartDto {
  title: string
  subtitle?: string
  series: { label: string; value: number }[]
}
