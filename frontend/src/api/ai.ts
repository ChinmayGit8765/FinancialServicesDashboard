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
 * Typed DTO for the structured-output chart — mirrors the live BeanOutputConverter record shape
 * so the chart component is source-agnostic. The store fetches GET /api/ai/structured for both
 * modes: in demo mode the backend returns the seeded STRUCTURED_INSIGHT record (no key, no network);
 * with a key it returns the live LLM-generated typed record — same shape, no chart changes.
 */
export interface StructuredChartDto {
  title: string
  subtitle?: string
  series: { label: string; value: number }[]
}

// --- Phase 7: Chat / RAG DTOs -----------------------------------------------

export interface Citation {
  ticker: string
  section: string
  source: string
  excerpt: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
}

export interface ChatResponseDto {
  answer: string
  citations: Citation[]
}
