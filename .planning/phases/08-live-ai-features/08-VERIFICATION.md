---
phase: 08-live-ai-features
verified: 2026-06-10T00:00:00Z
status: human_needed
score: 12/12
overrides_applied: 0
human_verification:
  - test: "Live quote tool call via chat"
    expected: "Ask 'what's AAPL trading at?' with a real Anthropic key set. LLM invokes getStockQuote @Tool. Response includes a price and an as-of timestamp. No key or token visible in the UI or server logs."
    why_human: "Requires a running Docker stack plus real Anthropic + optional Finnhub keys. Tool invocation only happens on the live LLM path; cannot be verified by mocks or grep."
  - test: "StructuredOutputChart renders seeded DTO in demo mode and live DTO in live mode"
    expected: "With no keys: structured-output panel renders the seeded sector-exposure bar chart (GROWTH persona for alice). With a real key: the chart re-renders from the BeanOutputConverter-typed LLM response — same DTO shape, different data."
    why_human: "Visual rendering and chart presence cannot be verified programmatically; requires a browser and a running frontend."
  - test: "Multi-provider live switch (Anthropic -> OpenAI)"
    expected: "Switch the BYO-key popup from Anthropic to OpenAI (enter a real OpenAI key) and trigger a structured-output or chat request. Confirm a successful non-502 AI call from the same ChatClientStrategy.forSession() entry point."
    why_human: "Requires real OpenAI key and a running stack. Route correctness is unit-proven by MultiProviderRoutingTest; the live behavioral path needs actual keys."
---

# Phase 08: Live AI Features — Verification Report

**Phase Goal:** Tool calling for live quotes (Finnhub @Tool), multi-provider ChatClient routing (Claude/OpenAI via per-session mutate()), and at least one AI response as typed structured output driving a chart — demo-mode fallbacks proven before any live key path.
**Verified:** 2026-06-10T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | In demo (no FINNHUB_API_KEY) the quote @Tool returns the last seeded OHLCV close — no network call | VERIFIED | `FinnhubQuoteClient.getQuote()` line 102-103: `if (finnhubApiKey == null || finnhubApiKey.isBlank()) return buildSeededQuote(ticker)`. `buildSeededQuote()` calls `ohlcvRepo.findLatestCloseByTicker(ticker)` returning `source=SEEDED, marketState=DEMO`. `StockQuoteToolServiceTest#demoFallback_noKey_returnsSeededLastClose` confirms with `verifyNoInteractions(mockHttpClient)`. |
| 2 | In live mode the Finnhub token is sent via X-Finnhub-Token header (never in URL), 15-min TTL cache applies, and market state derives from timestamp | VERIFIED | `FinnhubQuoteClient.java` lines 116-122: URI is `BASE_URL + "?symbol=" + ticker` with no token param; `header("X-Finnhub-Token", finnhubApiKey)` is set on the request builder. `CachedQuote` stores `expiresAt` for race-safe expiry (CR-02). `deriveMarketState(long)` covers PRE_MARKET/REGULAR/AFTER_HOURS/CLOSED from NYSE hours. `T2b-CR01-HEADER` test asserts URI has no `token=` and header is present. |
| 3 | The @Tool is registered on the live ChatClient via MethodToolCallbackProvider (not defaultTools) | VERIFIED | `ChatClientStrategy.forSession()` lines 93-99: `MethodToolCallbackProvider.builder().toolObjects(stockQuoteToolService).build()` passed to `ChatClient.builder(model).defaultToolCallbacks(toolProvider)`. No `defaultTools(` call exists. SUMMARY records `getToolCallbacks()` returns `ToolCallback[]`; `defaultToolCallbacks(ToolCallbackProvider...)` varargs used. |
| 4 | ChatClientStrategy.forSession routes to Anthropic for provider=anthropic and OpenAI for provider=openai from the same entry point | VERIFIED | `ChatClientStrategy.buildModel()` lines 118-122: switch on `keyHolder.getProvider()`: `"anthropic"` -> `buildAnthropicModel(apiKey)`, `"openai"` -> `buildOpenAiModel(apiKey)`. `MultiProviderRoutingTest`: 3 tests covering `anthropicProvider_buildsAnthropicModel`, `openaiProvider_buildsOpenAiModel`, and `bothProviders_sameEntryPoint_returnsNonNull` — all GREEN, no Spring context, no network. |
| 5 | Finnhub key never appears in any log line — proven by ACTIVE sentinel test forcing the catch path | VERIFIED | `FinnhubQuoteClient` catch block line 155: `log.warn("Finnhub call failed for ticker {} — falling back to seeded", ticker)` — no `e.getMessage()` or `e.toString()`. `StockQuoteToolServiceTest#finnhubKeySentinelNeverLogged_onForcedFailure`: key IS set to `TEST-FH-SENTINEL`, `HttpClient.send()` throws IOException embedding the sentinel in message, ListAppender captures all log events, test asserts sentinel absent from every formatted message and every field of the returned record. |
| 6 | In demo mode GET /api/ai/structured returns seeded STRUCTURED_INSIGHT JSON deserialized via ObjectMapper.readValue (no provider/network call) | VERIFIED | `StructuredOutputService.getInsight()` lines 96-104: `if (!keyHolder.hasKey())` branch calls `objectMapper.readValue(seedContent, StructuredInsightRecord.class)`. `AiDemoModeIntegrationTest#demoMode_structured_returnsSeededContent_withZeroNetworkCalls`: 200, `"title"` present, `NoNetworkProofConfig.NEXT_CALL_COUNT.get() == 0` (executable no-network proof). |
| 7 | In live mode GET /api/ai/structured fills the record via BeanOutputConverter (.entity(StructuredInsightRecord.class)) | VERIFIED | `StructuredOutputService.getInsight()` lines 110-119: live path calls `strategy.forSession(keyHolder).prompt()...call().entity(StructuredInsightRecord.class)`. `StructuredOutputServiceTest#livePath_entityReturnsRecord_shapeValid`: mock chain proves `.entity(StructuredInsightRecord.class)` is the terminal call. |
| 8 | GET /api/ai/structured endpoint exists, is IDOR-safe, and returns a StructuredInsightRecord | VERIFIED | `AiController.java` lines 138-142: `@GetMapping("/structured") public ResponseEntity<StructuredInsightRecord> structured(Authentication authentication)` calls `resolvePortfolioId(authentication)` then `structuredOutputService.getInsight(portfolioId)`. Portfolio identity derived exclusively from principal (IDOR pattern). `AiControllerIntegrationTest#structured_demoMode_returnsRecordShape` and `#structured_unauthenticated_returns401` both GREEN. |
| 9 | The frontend StructuredOutputChart fetches /api/ai/structured via the same StructuredChartDto path in demo and live | VERIFIED | `frontend/src/stores/ai.ts` lines 149: `axios.get<StructuredChartDto>('/api/ai/structured')`. No reference to `/ai-structured-demo.json` in the fetch action. `StructuredOutputChart.vue` accepts `StructuredChartDto | null` prop and renders `props.structured.series` — same component, same shape regardless of source. `StructuredOutputChart.test.ts` has 8 tests GREEN (7 existing + `renders live DTO shape from /api/ai/structured`). |
| 10 | AiSeedRunner seeds valid STRUCTURED_INSIGHT JSON per persona (GROWTH/INCOME/BALANCED) at ai-v4 | VERIFIED | `AiSeedRunner.java` line 65: `AI_SEED_VERSION = "ai-v4"`. Lines 487-514: three `new AiSeedContent(STRUCTURED_INSIGHT, "GROWTH"|"INCOME"|"BALANCED", ...)` rows with valid JSON matching `StructuredInsightRecord` schema (`title`, `subtitle`, `series:[{label,value}]`). |
| 11 | Demo no-network proof (counting advisor == 0) covers both the tool path and the structured path | VERIFIED | `AiDemoModeIntegrationTest`: `CountingCallAdvisor` at `HIGHEST_PRECEDENCE + 1` increments only if `DemoModeAdvisor` does NOT short-circuit. Both `demoMode_explain/commentary_returnsSeededContent_withZeroNetworkCalls` and `demoMode_structured_returnsSeededContent_withZeroNetworkCalls` assert `NEXT_CALL_COUNT.get() == 0`. |
| 12 | KeyLeakage gate extended: GET /api/ai/structured never returns the LLM key; Finnhub key threat documented and ACTIVE proof referenced | VERIFIED | `KeyLeakageIntegrationTest` step 5b (lines 161-167): GET `/api/ai/structured` asserts status in `{200, 502}` and body `doesNotContain(testKey)`. Step 6 log assertion covers all log lines including those emitted during the structured call. Finnhub T-08-LEAK-FH documented inline (lines 43-46) with reference to `StockQuoteToolServiceTest#finnhubKeySentinelNeverLogged_onForcedFailure`. |

**Score:** 12/12 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/ai/tools/StockQuoteResult.java` | Typed @Tool result record | VERIFIED | `public record StockQuoteResult(String ticker, BigDecimal price, String asOf, String marketState, String source)` — substantive, referenced by FinnhubQuoteClient and StockQuoteToolService |
| `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java` | Thin Java HttpClient Finnhub fetch + 15-min TTL cache + seeded fallback + after-hours-from-timestamp | VERIFIED | 229 lines; `ConcurrentHashMap<String,CachedQuote>` TTL cache; `deriveMarketState(long)`; `buildSeededQuote(String)`; `@Autowired` on primary constructor (Spring 6 multi-constructor fix); token in header not URL |
| `backend/src/main/java/com/quantlens/ai/tools/StockQuoteToolService.java` | @Tool getStockQuote(ticker) component | VERIFIED | `@Tool(description=...)` on `getStockQuote(@ToolParam(...) String ticker)`. No `hasKey()` branch in body. Ticker canonicalized to uppercase (IN-01). |
| `backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java` | findLatestCloseByTicker(String) JPQL query joining Security | VERIFIED | `@Query` JPQL joining `b.security.ticker = :ticker` with correlated `MAX(b2.barDate)` subquery, returns `Optional<OhlcvBar>` |
| `backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java` | Tool registration via MethodToolCallbackProvider + defaultToolCallbacks | VERIFIED | `MethodToolCallbackProvider.builder().toolObjects(stockQuoteToolService).build()` passed to `.defaultToolCallbacks(toolProvider)` in `forSession()`. No `defaultTools(` call. |
| `backend/src/main/java/com/quantlens/ai/api/StructuredInsightRecord.java` | Structured-output root record | VERIFIED | `public record StructuredInsightRecord(String title, String subtitle, @JsonSetter(nulls=Nulls.AS_EMPTY) List<InsightEntry> series)` — null series coerced to empty list (WR-03) |
| `backend/src/main/java/com/quantlens/ai/api/InsightEntry.java` | Nested labeled-value record | VERIFIED | `public record InsightEntry(String label, Double value)` with compact constructor coercing null/NaN/Infinity to 0.0 (WR-04) |
| `backend/src/main/java/com/quantlens/ai/service/StructuredOutputService.java` | Demo ObjectMapper.readValue parse + live .entity() path | VERIFIED | Explicit `hasKey()` branch: demo → `objectMapper.readValue(seedContent, StructuredInsightRecord.class)`; live → `strategy.forSession(keyHolder)...call().entity(StructuredInsightRecord.class)`. 502 catch never echoes `e.getMessage()`. |
| `backend/src/main/java/com/quantlens/ai/api/AiController.java` | GET /api/ai/structured endpoint | VERIFIED | `@GetMapping("/structured")` returns `ResponseEntity<StructuredInsightRecord>`, uses existing `resolvePortfolioId(authentication)` |
| `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java` | STRUCTURED_INSIGHT seeds per persona + ai-v4 | VERIFIED | `AI_SEED_VERSION = "ai-v4"`, 3 STRUCTURED_INSIGHT rows with valid StructuredInsightRecord-matching JSON |
| `frontend/src/stores/ai.ts` | fetchStructured -> /api/ai/structured | VERIFIED | `axios.get<StructuredChartDto>('/api/ai/structured')` in `fetchStructured` action; no reference to static JSON file |
| `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java` | demo/live/cache/zero-timestamp + ACTIVE sentinel key-leak assertions | VERIFIED | 15 tests (expanded from original 7 after REVIEW-FIX): demoFallback, live_returnsFinnhubPrice, live_tokenInHeader_notInUri, cacheTtl, cache_expiredEntry, zeroTimestamp, cr03, cr04, wr01 x2, finnhubKeySentinelNeverLogged, toolDelegatesToClient x2, in01 x2 — all GREEN per REVIEW-FIX.md |
| `backend/src/test/java/com/quantlens/ai/MultiProviderRoutingTest.java` | Mock Anthropic + OpenAI both route via forSession | VERIFIED | 3 tests; no Spring context; no network; anthropic and openai branches both return non-null ChatClient from `forSession()` |
| `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java` | Key-leak assertions for /api/ai/structured + Finnhub key threat | VERIFIED | Step 5b added for `/api/ai/structured`; Finnhub T-08-LEAK-FH threat documented inline with ACTIVE proof reference |
| `backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java` | No-network proof for the structured demo path | VERIFIED | `demoMode_structured_returnsSeededContent_withZeroNetworkCalls`: 200, non-blank title, `NEXT_CALL_COUNT == 0` |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `StockQuoteToolService.getStockQuote` | `FinnhubQuoteClient.getQuote` | Direct delegation — no demo/live branch in @Tool body | WIRED | `return finnhubClient.getQuote(ticker != null ? ticker.toUpperCase(Locale.ROOT) : ticker)` — single line, no conditionals |
| `FinnhubQuoteClient.buildSeededQuote` | `OhlcvBarRepository.findLatestCloseByTicker` | Seeded last-close lookup by ticker | WIRED | `ohlcvRepo.findLatestCloseByTicker(ticker)` in `buildSeededQuote()` |
| `ChatClientStrategy.forSession` | `StockQuoteToolService @Tool` | `MethodToolCallbackProvider.builder().toolObjects(stockQuoteToolService).build()` -> `defaultToolCallbacks(toolProvider)` | WIRED | Builder-level tool registration in `forSession()` confirmed in source |
| `AiController.structured` | `StructuredOutputService.getInsight` | `resolvePortfolioId(authentication)` -> `getInsight(portfolioId)` | WIRED | `structuredOutputService.getInsight(portfolioId)` in `structured()` handler |
| `StructuredOutputService.getInsight (demo)` | `AiSeedContentRepository STRUCTURED_INSIGHT seed` | `findByTypeAndSubjectId + objectMapper.readValue` | WIRED | `seedRepo.findByTypeAndSubjectId(STRUCTURED_INSIGHT, personaKey).map(AiSeedContent::getContent).orElse(FALLBACK_SEED)` then `objectMapper.readValue(...)` |
| `StructuredOutputService.getInsight (live)` | `BeanOutputConverter` | `strategy.forSession(...).call().entity(StructuredInsightRecord.class)` | WIRED | `.entity(StructuredInsightRecord.class)` on the call chain — BeanOutputConverter auto-generates schema |
| `frontend ai store fetchStructured` | `GET /api/ai/structured` | `axios.get<StructuredChartDto>('/api/ai/structured')` | WIRED | Confirmed in `ai.ts` line 149; no reference to static `/ai-structured-demo.json` in the fetch path |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|-------------------|--------|
| `StructuredOutputChart.vue` | `props.structured` (StructuredChartDto) | ai store `structured.data` ← `fetchStructured()` ← `GET /api/ai/structured` ← `StructuredOutputService.getInsight()` ← DB seed or BeanOutputConverter | Yes — demo reads `ai_seed_content` DB rows seeded at ai-v4; live receives LLM JSON | FLOWING |
| `FinnhubQuoteClient.getQuote` | `StockQuoteResult` | Demo: `OhlcvBarRepository.findLatestCloseByTicker` (seeded OHLCV rows). Live: Finnhub `/quote` endpoint via `HttpClient` | Yes — both paths return substantive data | FLOWING |

---

### Behavioral Spot-Checks

Step 7b skipped for live Finnhub path and live LLM path — these require a running server and real API keys. Demo path spot-checks are covered by the integration tests (AiDemoModeIntegrationTest GREEN).

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Demo no-network proof (counting advisor == 0) | `AiDemoModeIntegrationTest` (run as part of full suite) | NEXT_CALL_COUNT == 0 for explain, commentary, structured | PASS (test evidence) |
| Key never logged | `KeyLeakageIntegrationTest` | testKey absent from all responses and log lines | PASS (test evidence) |
| Live tool + structured LLM path | Requires docker + real keys | — | SKIP — routed to human verification |

---

### Probe Execution

Step 7c: No `probe-*.sh` files found in `scripts/` for this phase. Phase-specific verification runs via Maven (`.\mvnw.cmd verify`) and npm (`npm run test`). Final verified run per REVIEW-FIX.md: **207 backend tests, 0 failures, BUILD SUCCESS; 86 frontend tests, 0 failures, npm run build SUCCESS**.

Note: 08-03-SUMMARY.md reported 189/197 (8 failures) at the time of 08-03 execution. Those 8 failures (AiSeedRunnerTest row-count assertion, AiSeedRunnerTest seed-log version assertion, StructuredOutputServiceTest UnnecessaryStubbing) were subsequently fixed during the code review pass (08-REVIEW-FIX.md, committed 2026-06-09T23:57:00Z). The post-fix state is 207 tests, 0 failures.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| AI-05 | 08-01-PLAN.md | User can request a live quote in chat and see the LLM invoke a quote tool; demo returns seeded price; live calls Finnhub with 15-min TTL cache and after-hours labeling | SATISFIED | `StockQuoteToolService` @Tool + `FinnhubQuoteClient` (header auth, cache, market state) + demo seeded path + ACTIVE sentinel key-leak test + MultiProviderRoutingTest |
| AI-06 | 08-02-PLAN.md | At least one AI response as typed structured output (BeanOutputConverter) driving a Vue ECharts chart — same DTO path in demo and live | SATISFIED | `StructuredInsightRecord` + `StructuredOutputService` (readValue/entity) + `GET /api/ai/structured` + ai-v4 seeds + frontend URL swap to live endpoint + StructuredOutputChart renders same DTO shape |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `frontend/src/components/ai/StructuredOutputChart.vue` | 3-11 (script comment) | Stale comment: "DEMO STUB only" + "Phase 8 (AI-06) will swap the source" | Warning | Comment predates Phase 8 execution; the store WAS updated (fetchStructured now calls /api/ai/structured). No functional impact. |
| `frontend/src/components/ai/StructuredOutputChart.vue` | 124-126 (figcaption) | Stale text: "Phase 8 will replace this demo fixture with a live BeanOutputConverter-typed record" | Warning | Same stale comment, now describing past work. No functional impact — figcaption is screen-reader-only. |

No `TBD`, `FIXME`, or `XXX` markers found in phase-modified files. No stub return patterns in production code paths. No hardcoded empty arrays or objects in live data paths.

---

### Human Verification Required

The three items below require a running Docker stack and real API keys. All automated gates (unit tests, integration tests, key-leak gate, no-network proof, build) are GREEN. Manual confirmation is needed only for the live behavioral paths.

### 1. Live Quote Tool Call (AI-05)

**Test:** `docker compose up` from a clean checkout. Log in as alice (demo1234). Open the BYO-key popup and enter a real Anthropic key (+ optionally a Finnhub key). In the chat panel ask "what's AAPL trading at?"
**Expected:** The LLM invokes the `getStockQuote` @Tool server-side. The response includes a price (Finnhub live price if FINNHUB_API_KEY is set, otherwise the seeded last close) and an as-of timestamp. No API key or Finnhub token is visible anywhere in the UI or server logs.
**Why human:** Tool invocation happens on the live LLM path during an actual model call. Cannot be asserted by mocks. KeyLeakage and sentinel tests cover the key-non-disclosure property; the tool invocation itself requires a real call.

### 2. StructuredOutputChart Renders (AI-06 — Demo and Live)

**Test:** With no keys (demo mode): confirm the structured-output chart panel on the dashboard renders the seeded sector-exposure bar chart with a non-empty series for alice (GROWTH persona). With a real key: trigger `fetchStructured` and confirm the chart re-renders from the BeanOutputConverter-typed LLM response.
**Expected:** In demo mode: title "Sector Exposure", subtitle "AI-Detected Allocation (Demo)", bars for Technology/Automotive/Consumer Staples/Cash. In live mode: the chart renders with LLM-generated sector data in the same StructuredChartDto shape — no component change required.
**Why human:** Visual rendering and chart presence cannot be verified programmatically. The store URL swap and DTO shape are code-verified; the actual chart rendering is a browser-only assertion.

### 3. Multi-Provider Live Switch (AI-05/AI-06)

**Test:** With a real Anthropic key active, switch the BYO-key popup to OpenAI and enter a real OpenAI key. Trigger a structured-output request or ask a chat question.
**Expected:** A successful (non-502) AI response is returned from the OpenAI provider via the same `ChatClientStrategy.forSession()` entry point. The structured chart renders the same DTO shape.
**Why human:** Requires real OpenAI key. `MultiProviderRoutingTest` proves the routing logic builds a non-null ChatClient for both providers; the live behavioral outcome (actual OpenAI API call succeeding) requires real credentials.

---

### Gaps Summary

No gaps. All 12 must-have truths are VERIFIED. The 8 backend test failures noted in 08-03-SUMMARY.md (AiSeedRunnerTest row-count/version mismatch + StructuredOutputServiceTest UnnecessaryStubbing) were resolved in the subsequent code review fix pass (08-REVIEW-FIX.md), confirmed by the final gate result of 207 tests, 0 failures, BUILD SUCCESS.

The two stale comments in `StructuredOutputChart.vue` are cosmetic — they pre-date Phase 8 execution and describe what Phase 8 *would* do. The functionality they describe (store URL swap, live BeanOutputConverter path) is fully implemented. No functional blocker.

Status is `human_needed` because three live behavioral items require real API keys and a running Docker stack. All automated checks pass.

---

_Verified: 2026-06-10T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
