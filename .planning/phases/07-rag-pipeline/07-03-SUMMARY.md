---
phase: 07-rag-pipeline
plan: 03
subsystem: frontend-ai
tags: [vue3, pinia, chat, rag, citations, vitest, dark-theme]
dependency_graph:
  requires: [07-01, 07-02]
  provides: [chat-panel-ui, ai-store-sendMessage, dashboard-qa-slot]
  affects: [ChatPanel, ai store, DashboardView, ChatPanel.spec]
tech_stack:
  added: []
  patterns:
    - "Props-driven ChatPanel (messages/loading/error) + emit send — no direct store access in component"
    - "ai store ref<ChatMessage[]> + sendMessage action (POST /api/ai/chat, push user turn → push assistant turn)"
    - "Citation chips: {{ ticker }} · {{ section }} with excerpt title tooltip — text interpolation only (T-07-XSS)"
    - "Skeleton shimmer CSS verbatim from CommentaryCard.vue (design-token only, no raw hex)"
    - "Test location: src/components/ai/__tests__/ChatPanel.spec.ts (vitest glob src/**/*.spec.ts)"
key_files:
  created:
    - frontend/src/components/ai/ChatPanel.vue
    - frontend/src/components/ai/__tests__/ChatPanel.spec.ts
  modified:
    - frontend/src/api/ai.ts
    - frontend/src/stores/ai.ts
    - frontend/src/views/DashboardView.vue
decisions:
  - "Citation/ChatMessage/ChatResponseDto interfaces placed in api/ai.ts (DTO-only file), not store"
  - "Test file at src/components/ai/__tests__/ChatPanel.spec.ts using .spec.ts extension (vitest include: src/**/*.{test,spec}.ts)"
  - "SlotPlaceholder import removed from DashboardView — was only used for the AI Q&A slot"
  - "chatMessages is ref<ChatMessage[]> (not asyncState) — chat is append-only, not a single replaceable resource"
metrics:
  duration_seconds: 420
  tasks_completed: 2
  files_changed: 5
  completed_date: 2026-06-09
---

# Phase 7 Plan 03: Frontend AI Chat Panel + Store + DashboardView Wiring Summary

ChatPanel.vue with citation chips wired into the dashboard AI Q&A slot via ai store sendMessage (POST /api/ai/chat), with 3-state component test GREEN and vue-tsc build GREEN.

## What Was Built

1. **`frontend/src/api/ai.ts` — Chat DTOs added**
   - `Citation { ticker, section, source, excerpt }`
   - `ChatMessage { role: 'user'|'assistant', content, citations? }`
   - `ChatResponseDto { answer, citations }`

2. **`frontend/src/stores/ai.ts` — Chat state + sendMessage**
   - `chatMessages = ref<ChatMessage[]>([])`, `chatLoading = ref(false)`, `chatError = ref<string|null>(null)`
   - `sendMessage(message, conversationId?)` — pushes user turn, POSTs to `/api/ai/chat`, pushes assistant turn with citations; 401 → 'Session expired'; finally chatLoading=false
   - `$reset()` extended to clear chat state
   - Security invariant preserved: no apiKey in store, no message persistence outside reactive state

3. **`frontend/src/components/ai/ChatPanel.vue`**
   - Props: `messages: ChatMessage[]`, `loading: boolean`, `error: string | null`
   - Emit: `send: [message: string]`
   - Empty state: "Ask a question about your portfolio or the 10-K filings…"
   - Message list with user/assistant styling; auto-scrolls to bottom on new message (nextTick + scrollTop)
   - Citation chips: `{{ ticker }} · {{ section }}` with `title="{{ excerpt }}"` tooltip
   - Loading shimmer (skeleton CSS verbatim from CommentaryCard.vue)
   - Error block `role="alert"` with `{{ error }}`
   - Input row: `.chat-input` (disabled while loading, Enter submits) + `.send-btn` (disabled while loading or empty)
   - CSS: design tokens only — `var(--color-*)`, `var(--radius-*)`, `var(--space-*)`, `var(--shadow-card)`
   - T-07-XSS: all text rendered via `{{ }}` — never `v-html`

4. **`frontend/src/components/ai/__tests__/ChatPanel.spec.ts`**
   - Test 1: type in `.chat-input` + click `.send-btn` → `emitted('send')[0]` equals trimmed input
   - Test 2: messages with user + assistant + citation → text contains both, `.citation-chip` contains ticker
   - Test 3: `loading: true` → `.chat-input` and `.send-btn` both have `disabled` attribute
   - Location: `src/components/ai/__tests__/ChatPanel.spec.ts` — picked up by vitest glob `src/**/*.{test,spec}.ts`

5. **`frontend/src/views/DashboardView.vue` — ChatPanel wired**
   - `<ChatPanel :messages="aiStore.chatMessages" :loading="aiStore.chatLoading" :error="aiStore.chatError" @send="aiStore.sendMessage($event)" />` replaces `<SlotPlaceholder label="AI Q&A — Phase 7" />`
   - `<StructuredOutputChart>` retained directly above (coexist as per plan)
   - SlotPlaceholder import removed (no other usages)

## Test Results

| Test | Status |
|------|--------|
| ChatPanel: emits send event on button click | GREEN |
| ChatPanel: renders user message and assistant message with citations | GREEN |
| ChatPanel: disables input while loading | GREEN |
| Full suite (16 test files, 85 tests) | GREEN |
| `npm run build` (vue-tsc + vite) | GREEN |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Unused import `Citation` in ai.ts store**
- **Found during:** Task 1 build verification
- **Issue:** `Citation` was imported by name in stores/ai.ts but not directly used (types flow through `ChatMessage`/`ChatResponseDto`); vue-tsc flagged TS6196
- **Fix:** Removed `Citation` from the store import (it lives in api/ai.ts and flows via the other types)
- **Files modified:** `frontend/src/stores/ai.ts`

## Checkpoint: human-verify (deferred — visual checklist)

Build gate (automatable proof) passed: `npm run build` exit 0, `npm run test` 85/85 green.

Visual verification deferred per autonomous run instructions:

- [ ] `docker compose up` → backend healthy; `npm run dev` → frontend up
- [ ] Log in as Alice (alice / demo1234)
- [ ] Scroll to "AI Q&A" panel (col-8, below structured-output chart); empty-state prompt visible
- [ ] Ask "What does Apple say about AI risk in their 10-K?" — user message appears, AI answer appears with citation chip (e.g. "AAPL · Risk Factors"); hover chip shows excerpt tooltip
- [ ] Ask follow-up — prior turn retained (conversation continuity)
- [ ] AI mode badge still reads "demo"

## Known Stubs

None — ChatPanel is fully wired; sendMessage posts to live `/api/ai/chat` endpoint; demo mode returns authored RAG_QA answers with citations from the seeded corpus (07-02).

## Threat Surface Scan

No new trust boundaries beyond the plan's threat model. Mitigations confirmed:
- T-07-XSS: all answer/citation text rendered via `{{ }}` — no `v-html` anywhere in ChatPanel.vue
- T-07-LEAK: store never holds apiKey; sendMessage forwards only `{ message, conversationId }`; chatMessages in reactive state only
- T-07-SESSION: 401 → 'Session expired' string; global api/auth.ts 401 interceptor handles redirect; no chat retry loop

## Self-Check: PASSED

Files exist:
- [x] `frontend/src/components/ai/ChatPanel.vue`
- [x] `frontend/src/components/ai/__tests__/ChatPanel.spec.ts`
- [x] `frontend/src/api/ai.ts` (Citation/ChatMessage/ChatResponseDto added)
- [x] `frontend/src/stores/ai.ts` (chatMessages/chatLoading/chatError/sendMessage added)
- [x] `frontend/src/views/DashboardView.vue` (ChatPanel wired, SlotPlaceholder replaced)

Commits exist:
- [x] 05290d3 — feat(07-03): add chat DTOs + ai store chatMessages/chatLoading/chatError + sendMessage
- [x] 5985f60 — feat(07-03): ChatPanel.vue + ChatPanel.spec.ts + DashboardView Q&A wiring
