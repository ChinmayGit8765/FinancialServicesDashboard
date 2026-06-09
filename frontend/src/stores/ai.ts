import { defineStore } from 'pinia'
import { reactive, ref } from 'vue'
import axios from 'axios'
import type { AiStatus, ExplainResponse, CommentaryDto, StructuredChartDto, ChatMessage, ChatResponseDto } from '../api/ai'

// Per-resource async state shape.
// Components access resources whole — do NOT destructure (loses reactivity).
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

export const useAiStore = defineStore('ai', () => {
  // --- per-resource reactive state -------------------------------------------

  const status      = asyncState<AiStatus>()
  const explanation = asyncState<ExplainResponse>()
  const commentary  = asyncState<CommentaryDto>()
  const structured  = asyncState<StructuredChartDto>()

  // --- Phase 7: chat state ----------------------------------------------------
  const chatMessages = ref<ChatMessage[]>([])
  const chatLoading  = ref(false)
  const chatError    = ref<string | null>(null)

  // --- fetch actions ----------------------------------------------------------

  /**
   * Fetch the current AI mode (demo/live) + provider from GET /api/ai/status.
   */
  async function fetchStatus(): Promise<void> {
    status.loading = true
    status.error = null
    try {
      const { data } = await axios.get<AiStatus>('/api/ai/status')
      status.data = data
    } catch (e: any) {
      status.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load AI status'
    } finally {
      status.loading = false
    }
  }

  /**
   * Submit a BYO API key to POST /api/ai/key.
   *
   * SECURITY INVARIANT: the apiKey parameter is forwarded directly to the
   * endpoint and is NEVER assigned to any reactive state, localStorage,
   * sessionStorage, or module-level variable. Only the returned {mode,provider}
   * is written to status.data. $reset has no apiKey field.
   *
   * CR-04 / WR-05: setKey now re-throws on failure so callers (e.g. BYOKeyModal)
   * can catch the rejection and keep the modal open with an error message. The store
   * still sets status.error for store-level consumers, and the re-throw allows the
   * component try/catch to be genuinely reachable (WR-05: the catch block was
   * previously dead because setKey swallowed all errors).
   */
  async function setKey(provider: 'anthropic' | 'openai', apiKey: string): Promise<void> {
    status.loading = true
    status.error = null
    try {
      const { data } = await axios.post<AiStatus>('/api/ai/key', { provider, apiKey })
      // Assign ONLY the returned status — never the key
      status.data = data
    } catch (e: any) {
      status.error = 'Key rejected — check provider and key format'
      // Re-throw so callers can react (e.g. keep modal open with error message)
      throw e
    } finally {
      status.loading = false
      // apiKey is not stored anywhere — it goes out of scope here
    }
  }

  /**
   * Clear the BYO key via DELETE /api/ai/key. Resets mode to demo.
   */
  async function clearKey(): Promise<void> {
    status.loading = true
    status.error = null
    try {
      await axios.delete('/api/ai/key')
      status.data = { mode: 'demo' }
      explanation.data = null
      explanation.error = null
      commentary.data = null
      commentary.error = null
    } catch (e: any) {
      status.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to clear key'
    } finally {
      status.loading = false
    }
  }

  /**
   * Fetch AI explanation for a holding via GET /api/ai/explain/{ticker}.
   * NOTE: The endpoint path variable is the ticker STRING (e.g. "AAPL"),
   * NOT a numeric holdingId. Confirmed in 06-03-SUMMARY.
   */
  async function fetchExplanation(ticker: string): Promise<void> {
    explanation.loading = true
    explanation.error = null
    try {
      const { data } = await axios.get<ExplainResponse>(`/api/ai/explain/${ticker}`)
      explanation.data = data
    } catch (e: any) {
      explanation.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load explanation'
    } finally {
      explanation.loading = false
    }
  }

  /**
   * Fetch AI daily commentary via GET /api/ai/commentary.
   */
  async function fetchCommentary(): Promise<void> {
    commentary.loading = true
    commentary.error = null
    try {
      const { data } = await axios.get<CommentaryDto>('/api/ai/commentary')
      commentary.data = data
    } catch (e: any) {
      commentary.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load commentary'
    } finally {
      commentary.loading = false
    }
  }

  /**
   * Fetch seeded structured-output chart fixture from /ai-structured-demo.json.
   *
   * DEMO STUB ONLY — this fetches the static seeded JSON from /public.
   * There is NO backend AI call and NO live LLM here.
   * Purpose: demonstrates the structured-output seam by rendering a typed JSON
   * fixture. Phase 8 (AI-06) will swap this source for a live BeanOutputConverter-
   * typed record without any changes to the StructuredOutputChart component.
   */
  async function fetchStructured(): Promise<void> {
    structured.loading = true
    structured.error = null
    try {
      const { data } = await axios.get<StructuredChartDto>('/ai-structured-demo.json')
      structured.data = data
    } catch (e: any) {
      structured.error = 'Failed to load structured output data'
    } finally {
      structured.loading = false
    }
  }

  // --- Phase 7: sendMessage action --------------------------------------------

  /**
   * POST /api/ai/chat with { message, conversationId }.
   * Pushes user turn immediately, then appends assistant turn on success.
   * SECURITY: only { message, conversationId } forwarded — no key in payload.
   */
  async function sendMessage(message: string, conversationId?: string): Promise<void> {
    chatMessages.value.push({ role: 'user', content: message })
    chatLoading.value = true
    chatError.value = null
    try {
      const { data } = await axios.post<ChatResponseDto>(
        '/api/ai/chat',
        { message, conversationId }
      )
      chatMessages.value.push({
        role: 'assistant',
        content: data.answer,
        citations: data.citations ?? []
      })
    } catch (e: any) {
      chatError.value = e?.response?.status === 401
        ? 'Session expired'
        : 'Failed to get AI response'
    } finally {
      chatLoading.value = false
    }
  }

  // --- manual reset -----------------------------------------------------------

  /**
   * Clear all AI resource state. No apiKey field — the key is never in store state.
   * Called on logout or when the user switches persona.
   */
  function $reset(): void {
    status.data      = null; status.loading      = false; status.error      = null
    explanation.data = null; explanation.loading = false; explanation.error = null
    commentary.data  = null; commentary.loading  = false; commentary.error  = null
    structured.data  = null; structured.loading  = false; structured.error  = null
    chatMessages.value = []; chatLoading.value = false; chatError.value = null
  }

  return {
    // state
    status,
    explanation,
    commentary,
    structured,
    // Phase 7 chat state
    chatMessages,
    chatLoading,
    chatError,
    // actions
    fetchStatus,
    setKey,
    clearKey,
    fetchExplanation,
    fetchCommentary,
    fetchStructured,
    sendMessage,
    $reset,
  }
})
