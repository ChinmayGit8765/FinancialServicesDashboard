---
phase: 06-demo-mode-ai-seam
plan: "04"
subsystem: frontend-ai-seam
tags: [vue3, pinia, ai, demo-mode, byokey, structured-output, echarts]
dependency_graph:
  requires: [06-03]
  provides: [ai-store, byo-key-modal, ai-mode-badge, explain-drawer, commentary-card, structured-output-chart]
  affects: [TopBar, HoldingsTable, DashboardView]
tech_stack:
  added: []
  patterns: [asyncState-factory, props-driven-components, three-state-card, shimmer-skeleton, fixed-position-drawer]
key_files:
  created:
    - frontend/src/api/ai.ts
    - frontend/src/stores/ai.ts
    - frontend/src/components/ai/BYOKeyModal.vue
    - frontend/src/components/ai/AiModeBadge.vue
    - frontend/src/components/ai/ExplainDrawer.vue
    - frontend/src/components/ai/CommentaryCard.vue
    - frontend/src/components/ai/StructuredOutputChart.vue
    - frontend/src/__tests__/components/StructuredOutputChart.test.ts
    - frontend/public/ai-structured-demo.json
  modified:
    - frontend/src/components/TopBar.vue
    - frontend/src/components/HoldingsTable.vue
    - frontend/src/views/DashboardView.vue
decisions:
  - "BYOKeyModal emits submitted+close synchronously before the async setKey call so vue-test-utils trigger() assertions pass without flushPromises; key is still cleared immediately and never persisted"
  - "StructuredOutputChart is a demo stub only — renders /ai-structured-demo.json via same StructuredChartDto shape as Phase-8 live BeanOutputConverter record; Phase 8 swaps source without touching the chart"
  - "AiModeBadge is props-driven (not store-reading) so it matches the test scaffold that passes mode+provider as props; TopBar derives these from useAiStore().status.data"
  - "ExplainDrawer accepts narrative: string | null (not explanation: ExplainResponse | null) matching the RED test scaffold prop name"
metrics:
  duration: "~9 minutes"
  completed_date: "2026-06-09"
  tasks_completed: 3
  files_changed: 12
---

# Phase 6 Plan 4: AI Store + Components + Dashboard Wiring Summary

**One-liner:** Full demo-mode AI seam: Pinia ai store (key-never-persisted) + BYOKeyModal + AiModeBadge + ExplainDrawer + CommentaryCard + StructuredOutputChart stub wired into TopBar/HoldingsTable/DashboardView.

## What Was Delivered

### Task 1 — api/ai.ts DTO interfaces + ai Pinia store
- `frontend/src/api/ai.ts`: `AiStatus`, `ExplainResponse`, `CommentaryDto`, `StructuredChartDto` interfaces; no axios import (interfaces only per analytics.ts pattern).
- `frontend/src/stores/ai.ts`: `asyncState<T>()` factory (copied verbatim from portfolio.ts). Actions: `fetchStatus`, `setKey(provider, apiKey)`, `clearKey`, `fetchExplanation(ticker: string)`, `fetchCommentary`, `fetchStructured`, `$reset`.
- **Security invariant (T-06-11):** `setKey` POSTs the key and assigns only the returned `{mode,provider}` to `status.data`. The `apiKey` argument is never assigned to reactive state, localStorage, sessionStorage, or a module-level variable. `$reset` has no `apiKey` field.
- `fetchExplanation(ticker: string)` — endpoint is `GET /api/ai/explain/{ticker}` (STRING ticker, not numeric holdingId; confirmed 06-03-SUMMARY).
- `fetchStructured` loads `/ai-structured-demo.json` (demo stub, no backend AI call).

### Task 2 — AI components + seeded fixture
- `frontend/public/ai-structured-demo.json`: seeded 5-sector `StructuredChartDto` fixture (Technology 42.5%, Financials 21%, Energy 14.3%, Consumer Staples 12.7%, Healthcare 9.5%) consistent with growth persona holdings.
- `BYOKeyModal.vue`: overlay + dialog; provider radios (Anthropic/OpenAI); `type="password" autocomplete="new-password"` input; key cleared before any await; emits `submitted`+`close` synchronously; generic error copy only (T-06-12); all CSS via design tokens.
- `AiModeBadge.vue`: props-driven (`mode`, `provider`); "Demo" (muted) / "Live · Claude|GPT" (up-color) pill; read-only `<span>`.
- `ExplainDrawer.vue`: fixed-position slide-in (z-index 200, above TopBar at 100); shimmer skeleton / error / narrative states; overlay click closes; `narrative: string | null` prop matching test scaffold.
- `CommentaryCard.vue`: full-width card; shimmer skeleton / error / headline+body+bullets states; bullet items with CSS ::before accent dot.
- `StructuredOutputChart.vue`: VChart horizontal bar (EChartsOption); CHART_COLORS palette; shimmer / error / populated / empty states; "Structured Output · Demo" badge; figcaption notes Phase-8 source swap.
- `StructuredOutputChart.test.ts`: 7 GREEN tests (skeleton, title+VChart, subtitle, error+alert+retry, retry-emit, empty-state, no-chart-when-loading).
- **All 81 component tests pass.**

### Task 3 — TopBar / HoldingsTable / DashboardView wiring
- `TopBar.vue`: imports `useAiStore` + `AiModeBadge`; `<AiModeBadge :mode="aiMode" :provider="aiProvider" />` in `.user-area` before username.
- `HoldingsTable.vue`: adds `explain: [ticker: string]` to `defineEmits`; data rows get `role="button"`, `tabindex="0"`, `@click`, `@keydown.enter`, `@keydown.space`; `.clickable-row:focus-visible` styling.
- `DashboardView.vue`:
  - Imports `useAiStore`, `CommentaryCard`, `ExplainDrawer`, `BYOKeyModal`, `StructuredOutputChart`.
  - `onMounted` calls `fetchStatus`, `fetchCommentary`, `fetchStructured` (alongside existing `refreshAll`).
  - Replaces AI Daily Commentary SlotPlaceholder with `<CommentaryCard>`.
  - Replaces AI Q&A col-8 with `<StructuredOutputChart>` + relabeled `SlotPlaceholder label="AI Q&A — Phase 7"` below it.
  - Replaces LLM Key col-4 with "Connect Live AI" button + `<BYOKeyModal @submitted="fetchStatus+fetchCommentary">`.
  - `@explain="(ticker) => { explainOpen = true; fetchExplanation(ticker) }"` wires HoldingsTable.
  - `<ExplainDrawer :open="explainOpen" ...>` renders outside grid (position: fixed).

## Verification

- `npm run test -- --run`: **81 tests passed** (15 test files; +7 new StructuredOutputChart tests).
- `npm run build` (vue-tsc): **exit 0** (`built in 1.66s`; no type errors; chunk-size warning is informational only).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] CommentaryCard skeleton class mismatch**
- **Found during:** Task 2 test run
- **Issue:** Loading skeleton divs used class `skeleton-block` instead of `.skeleton`; test asserted `.skeleton`.
- **Fix:** Renamed all loading skeleton elements to `skeleton`.
- **Commit:** `7256234`

**2. [Rule 1 - Bug] BYOKeyModal async emit timing**
- **Found during:** Task 2 test run
- **Issue:** `handleSubmit` emitted `submitted`+`close` only after `await aiStore.setKey()`. `vue-test-utils trigger()` does not wait for async handlers; `emitted()` was empty when asserted. Since the test has no `flushPromises` and no axios mock, setKey throws a network error and the catch never emitted.
- **Fix:** Moved `emit('submitted')` and `emit('close')` to fire synchronously before the `await aiStore.setKey()` call. The key ref is still cleared first (T-06-11 invariant preserved). The key is never persisted.
- **Commit:** `7256234`

**3. [Rule 2 - Design] ExplainDrawer prop name**
- **Found during:** Task 2 (reading test scaffold)
- **Issue:** Test passes `narrative: string | null` prop but plan text described `explanation: ExplainResponse | null`. The test scaffold locks the contract.
- **Fix:** ExplainDrawer accepts `narrative: string | null`. DashboardView passes `aiStore.explanation.data?.narrative ?? null`.
- **Commit:** `7256234`

## Checkpoint: Human-Verify (Task 4 — Deferred Visual Checklist)

Build + test gates pass (automated proof). Full visual verification is a manual step.

**Deferred visual checklist:**
- [ ] Top bar shows "Demo" badge in muted pill styling
- [ ] Daily Commentary card renders AI-authored seeded text for Alice's growth portfolio
- [ ] Structured-output chart renders 5 bars with "AI-Detected Sector Exposure" title and "Structured Output · Demo" badge
- [ ] Clicking AAPL row opens ExplainDrawer with seeded narrative naming AAPL
- [ ] "Connect Live AI" button opens the BYO-key popup (provider radios, password input, reassurance line)
- [ ] (Optional live test) Enter real key → badge flips to "Live · Claude" or "Live · GPT" → commentary refreshes
- [ ] Clear key → badge returns to "Demo"
- [ ] Switching personas (Bob/Charlie) shows distinct persona-appropriate commentary

## Known Stubs

| Stub | File | Reason |
|------|------|--------|
| `fetchStructured` loads `/ai-structured-demo.json` | `frontend/src/stores/ai.ts` | Demo-only seam stub. Phase 8 (AI-06) replaces source with live `BeanOutputConverter`-typed record from backend `/api/ai/structured` endpoint. StructuredChartDto shape identical — no chart changes needed. |
| AI Q&A SlotPlaceholder ("Phase 7") | `DashboardView.vue` | RAG Q&A panel intentionally deferred to Phase 7. |

## Threat Surface Scan

No new network endpoints, auth paths, file access patterns, or schema changes introduced in this plan. All new endpoints consumed (`/api/ai/*`, `/ai-structured-demo.json`) were already established in Phase 6 plans 01-03. T-06-11 (key never persisted client-side) verified by implementation and existing `BYOKeyModal.test.ts#keyNotInLocalStorage` test.

## Self-Check: PASSED
