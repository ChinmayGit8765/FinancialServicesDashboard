---
phase: 06-demo-mode-ai-seam
fixed_at: 2026-06-09T16:30:00Z
review_path: .planning/phases/06-demo-mode-ai-seam/06-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 11
skipped: 0
status: all_fixed
---

# Phase 06: Code Review Fix Report — Demo Mode AI Seam

**Fixed at:** 2026-06-09
**Source review:** `.planning/phases/06-demo-mode-ai-seam/06-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (CR-01 through CR-05, WR-01 through WR-06)
- Fixed: 11
- Skipped: 0

**Build verification:**
- Backend: `mvnw verify` — 147 tests PASS, 2 skipped (pre-existing golden-value printers)
- Frontend: `npm run test` — 82 tests PASS; `npm run build` — SUCCESS
- `KeyLeakageIntegrationTest` GREEN

---

## Fixed Issues

### CR-01: DELETE /api/ai/key CSRF exemption scoped to POST only

**Files modified:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java`, `backend/src/main/java/com/quantlens/ai/api/AiKeyController.java`, `backend/src/test/java/com/quantlens/ai/AiKeyControllerTest.java`, `backend/src/test/java/com/quantlens/ai/AiKeyControllerCsrfTest.java`
**Commit:** `2555b68`, `11f92d5`
**Applied fix:** Replaced the method-agnostic `.ignoringRequestMatchers("/api/ai/key")` with `AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/key")`. DELETE /api/ai/key now requires `X-XSRF-TOKEN`. Added `AiKeyControllerCsrfTest` with MockMvc-based `deleteKey_withoutCsrfToken_returns403` and `clearKey_withCsrfToken_returns200_withDemoMode` tests. Note: Spring Security 6 uses deferred CSRF token loading which prevents XSRF-TOKEN cookies from materialising in `TestRestTemplate` responses; `MockMvc` with `csrf()` is the authoritative test approach.

---

### CR-02: Validation error body no longer echoes `apiKey` field name

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/AiKeyController.java`, `backend/src/test/java/com/quantlens/ai/AiKeyControllerTest.java`
**Commit:** `517a3fa`
**Applied fix:** Added `@ExceptionHandler(MethodArgumentNotValidException.class)` to `AiKeyController` returning `{"error":"Invalid request"}` with no field names or rejected values. Added `setKey_blankApiKey_returns400_withGenericBody` integration test asserting body does not contain `apiKey`.
**Status:** fixed: requires human verification (logic: confirm the new handler fires before Spring Boot's default handler and that all validation error paths return the generic body)

---

### CR-03: Global exception backstop added to AI controller layer

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/GlobalAiExceptionHandler.java` (new file)
**Commit:** `f2fd202`, updated in `b94ed98`
**Applied fix:** Added `@RestControllerAdvice(basePackages="com.quantlens.ai.api")` that: (1) catches `ConstraintViolationException` from `@Validated` path variables (CR-05) returning generic 400; (2) catches `IllegalArgumentException` globally (WR-01) returning generic 400; (3) catches `Exception` broadly returning generic 500 with full internal logging but no exception detail forwarded to client. Re-throws `ResponseStatusException` to preserve existing 404/401/502 semantics.

---

### CR-04: BYOKeyModal awaits setKey before closing; error shown on failure

**Files modified:** `frontend/src/components/ai/BYOKeyModal.vue`, `frontend/src/stores/ai.ts`, `frontend/src/__tests__/components/BYOKeyModal.test.ts`
**Commit:** `8f58559`
**Applied fix:** Reversed the order in `handleSubmit` — `await aiStore.setKey(...)` runs first; `emit('submitted')` and `emit('close')` only fire on success. On rejection, `submitError` is set and the modal stays open. `aiStore.setKey` now re-throws on failure (WR-05) so the catch block in the modal is genuinely reachable. Updated tests: mock `setKey` with `vi.spyOn`, add `flushPromises()`, add `stays open and shows error when setKey rejects` test.

---

### CR-05: Ticker @PathVariable validated with @Pattern; DB ticker used in prompt

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/AiController.java`, `backend/src/main/java/com/quantlens/ai/service/ExplainPositionService.java`, `backend/src/main/java/com/quantlens/ai/api/GlobalAiExceptionHandler.java`, `backend/src/test/java/com/quantlens/ai/AiControllerIntegrationTest.java`
**Commit:** `b94ed98`
**Applied fix:** Added `@Validated` to `AiController` and `@Pattern(regexp="^[A-Z]{1,10}$")` on the `ticker` `@PathVariable`. Malformed tickers (lowercase, digits, `>10` chars) are rejected with 400 before reaching the service. In `ExplainPositionService.explain()`, the prompt now uses `holding.getSecurity().getTicker()` (DB-sourced) instead of the raw path variable. Added `explain_malformedTicker_returns400` integration test covering lowercase, too-long, and digit-containing tickers.

---

### WR-01: IllegalArgumentException from ChatClientStrategy now caught globally

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/GlobalAiExceptionHandler.java`
**Commit:** `f2fd202`
**Applied fix:** Covered by `GlobalAiExceptionHandler.handleIllegalArgument()` which catches `IllegalArgumentException` for all AI controllers (not just `AiKeyController`). Returns generic 400 with no exception message forwarded.

---

### WR-02: parseCommentary fallback uses sentence-boundary dot detection

**Files modified:** `backend/src/main/java/com/quantlens/ai/service/CommentaryService.java`
**Commit:** `884b074`
**Applied fix:** Replaced `text.indexOf('.')` in the fallback path with a loop that finds the first dot followed by whitespace or end-of-string. Abbreviation dots like "Inc." or "Corp." (followed by a non-whitespace character) are now skipped. The fallback headline is correctly extracted at the first sentence boundary.

---

### WR-03: @Size(max=200) added to AiKeyRequest fields

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/AiKeyRequest.java`
**Commit:** `517a3fa`
**Applied fix:** Added `@Size(max = 200)` to both `provider` and `apiKey` fields in `AiKeyRequest`. Prevents resource exhaustion from arbitrarily large payloads stored in session memory. 200 chars is generous relative to real API key lengths (Anthropic ~40, OpenAI ~51 chars).

---

### WR-04: AiModeBadge aria-label uses Vue binding

**Files modified:** `frontend/src/components/ai/AiModeBadge.vue`
**Commit:** `60f96a0`
**Applied fix:** Changed `aria-label="\`AI mode: ${props.mode}\`"` to `:aria-label="\`AI mode: ${props.mode}\`"`. The static attribute was rendering the template literal syntax verbatim; screen readers now announce "AI mode: demo" or "AI mode: live".

---

### WR-05: aiStore.setKey re-throws on failure (dead catch fixed)

**Files modified:** `frontend/src/stores/ai.ts`, `frontend/src/components/ai/BYOKeyModal.vue`
**Commit:** `8f58559`
**Applied fix:** Covered by the CR-04 fix. `aiStore.setKey` now re-throws the error after setting `status.error`, making the `catch` block in `BYOKeyModal.handleSubmit` genuinely reachable.

---

### WR-06: EXPLAIN_POSITION seed content rewritten to be persona-neutral; seed bumped to ai-v2

**Files modified:** `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java`, `backend/src/main/java/com/quantlens/ai/seed/AiSeedContent.java`, `backend/src/test/java/com/quantlens/ai/AiSeedRunnerTest.java`
**Commit:** `956392c`
**Applied fix:** All 13 `EXPLAIN_POSITION` narratives rewritten to be persona-neutral — no specific share counts ("approximately N shares") and no portfolio style labels ("growth-oriented portfolio", "income-oriented portfolio", "balanced portfolio"). Content now describes the company, sector role, and investment characteristics generically. Seed version bumped from `ai-v1` to `ai-v2` with upsert strategy (update existing rows, insert new ones) so corrected content applies to existing databases. `AiSeedContent.setContent()` added for the update path. `AiSeedRunnerTest` updated with `seedLog_aiV2_isMarkedCompleted` and new `explainPosition_content_isPersonaNeutral` test.

---

## Skipped Issues

None — all 11 in-scope findings were fixed.

---

_Fixed: 2026-06-09_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
