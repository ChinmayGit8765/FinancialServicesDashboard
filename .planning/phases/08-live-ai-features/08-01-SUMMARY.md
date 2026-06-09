---
phase: 08-live-ai-features
plan: "01"
subsystem: ai/tools + marketdata/domain
tags: [spring-ai, tool-calling, finnhub, quote, cache, security]
dependency_graph:
  requires: [06-01, 07-01]
  provides: [AI-05-backend]
  affects: [ai/chat/ChatClientStrategy, marketdata/domain/OhlcvBarRepository]
tech_stack:
  added: []
  patterns:
    - "MethodToolCallbackProvider.builder().toolObjects(bean).build() passed to defaultToolCallbacks(ToolCallbackProvider...) — avoids defaultTools() CGLIB bug #5134"
    - "java.net.http.HttpClient (JDK built-in) + ConcurrentHashMap TTL cache — zero new Maven deps"
    - "ReflectionTestUtils.setField for cross-package test injection of package-private fields"
    - "ListAppender<ILoggingEvent> attached to Logger for ACTIVE key-leak sentinel assertion"
key_files:
  created:
    - backend/src/main/java/com/quantlens/ai/tools/StockQuoteResult.java
    - backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java
    - backend/src/main/java/com/quantlens/ai/tools/StockQuoteToolService.java
    - backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java
    - backend/src/test/java/com/quantlens/ai/MultiProviderRoutingTest.java
  modified:
    - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java
    - backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java
decisions:
  - "[08-01] MethodToolCallbackProvider.getToolCallbacks() returns ToolCallback[] (array, not List); defaultToolCallbacks accepts ToolCallbackProvider... varargs — pass provider directly (Open Q1 resolved)"
  - "[08-01] OhlcvBarRepository.findLatestCloseByTicker uses JPQL b.security.ticker = :ticker correlated-MAX subquery; returns Optional<OhlcvBar> (Open Q2 resolved)"
  - "[08-01] com.quantlens.ai.tools subpackage is fully within the ai Modulith module; marketdata::domain already in allowedDependencies since Phase 6 — no package-info change needed (Open Q3 resolved)"
  - "[08-01] FinnhubQuoteClient.httpClient is non-final to allow ReflectionTestUtils injection from cross-package test; ACTIVE sentinel test forces catch path with a live key set"
  - "[08-01] MultiProviderRoutingTest constructs real OpenAiChatModel with fake key (no network at build time); AnthropicChatModel builder likewise — no Spring context needed"
metrics:
  duration: "18 minutes"
  completed: "2026-06-09"
  tasks: 3
  files: 7
---

# Phase 08 Plan 01: Live Quote Tool-Calling Slice Summary

**One-liner:** Thin Java HttpClient Finnhub quote client with 15-min TTL cache, after-hours labeling, seeded-close fallback, and `@Tool` registered on ChatClient via `MethodToolCallbackProvider` — key-leak proven by ACTIVE sentinel test.

## Tasks Completed

| # | Task | Commit | Status |
|---|------|--------|--------|
| RED | StockQuoteToolServiceTest failing (production classes absent) | 9dd6c4f | Done |
| 1 | FinnhubQuoteClient + StockQuoteResult + findLatestCloseByTicker | b4e7c7a | Done |
| 2 | StockQuoteToolService @Tool + ChatClientStrategy tool registration | 29be4dc | Done |
| 3 | MultiProviderRoutingTest + QuantLensModulithTest | e52f862 | Done |

## Open Questions Resolved

### Open Question 1: MethodToolCallbackProvider.getToolCallbacks() return type + defaultToolCallbacks arity

**Resolution:** `MethodToolCallbackProvider.getToolCallbacks()` returns `ToolCallback[]` (array, not `List`). `ChatClient.Builder.defaultToolCallbacks` has three overloads: `ToolCallback...`, `List<ToolCallback>`, and `ToolCallbackProvider...`. The cleanest approach: pass the `MethodToolCallbackProvider` instance directly to `defaultToolCallbacks(ToolCallbackProvider...)` — no `.toArray()` call needed. Verified by `javap` on `spring-ai-model-1.1.6.jar` and `spring-ai-client-chat-1.1.6.jar`.

### Open Question 2: findLatestCloseByTicker query

**Resolution:** Added JPQL `@Query` to `OhlcvBarRepository` joining via `b.security.ticker = :ticker` with correlated `MAX(b2.barDate)` subquery — same pattern as the existing `findLatestBarBySecurityIds`. Returns `Optional<OhlcvBar>`. Resolves the ticker-based lookup without a separate `SecurityRepository` call.

### Open Question 3: Modulith boundary for tools subpackage

**Resolution:** `com.quantlens.ai.tools` is a sub-package of `com.quantlens.ai` — fully within the `ai` Modulith module. `marketdata::domain` was already listed in `package-info.java` `allowedDependencies` since Phase 6. No change to `package-info.java` needed. `QuantLensModulithTest` GREEN unchanged.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Package-private constructor inaccessible from cross-package test**
- **Found during:** Task 1 GREEN phase — compile error in `StockQuoteToolServiceTest`
- **Issue:** The plan specified a "package-private test constructor" on `FinnhubQuoteClient`, but the test lives in `com.quantlens.ai` (not `com.quantlens.ai.tools`) — package-private is invisible across packages
- **Fix:** Made `httpClient` field non-final; tests use public primary constructor + `ReflectionTestUtils.setField` to inject `httpClient` and `finnhubApiKey`. The ACTIVE sentinel test still forces the catch path (key IS set, `HttpClient.send` throws).
- **Files modified:** `FinnhubQuoteClient.java`, `StockQuoteToolServiceTest.java`
- **Commit:** b4e7c7a

**2. [Rule 1 - Bug] Duplicate Javadoc on forSession()**
- **Found during:** Task 2 — old + new javadoc blocks both present after edit
- **Fix:** Removed duplicate old javadoc block
- **Commit:** 29be4dc

## Test Results

| Test Class | Tests | Status |
|------------|-------|--------|
| `StockQuoteToolServiceTest` | 7 | GREEN |
| `MultiProviderRoutingTest` | 3 | GREEN |
| `QuantLensModulithTest` | 1 | GREEN |
| `ChatServiceLivePathTest` | 4 | GREEN (not broken by constructor change) |

**Integration test failures:** All pre-existing — `ApplicationContext failure threshold` errors from Testcontainers/Postgres integration tests that require Docker. Unrelated to this plan.

## Key Behavior Proven

1. **Demo (no key):** `FinnhubQuoteClient.getQuote()` calls `buildSeededQuote()` → `findLatestCloseByTicker()` → `source=SEEDED, marketState=DEMO` — no HTTP call
2. **Live (key set, valid response):** Returns `source=FINNHUB`, price rounded to 2dp, `marketState` derived from timestamp vs NYSE 09:30-16:00 ET
3. **TTL cache:** Second call within 15 min returns same object reference; only one `HttpClient.send()` invocation
4. **Zero-timestamp:** `c==0 && t==0` payload falls back to seeded
5. **T-08-LEAK-FH ACTIVE:** Sentinel key set + `HttpClient.send()` throws IOException embedding the sentinel in the message → catch block logs only ticker → result is seeded → NO log line or result field contains `TEST-FH-SENTINEL`
6. **Tool registration:** `MethodToolCallbackProvider` + `defaultToolCallbacks(ToolCallbackProvider...)` — NOT `defaultTools()` (CGLIB bug #5134)
7. **Multi-provider routing:** Both anthropic and openai route to non-null ChatClient from same `forSession()` entry point

## Threat Surface Scan

No new network endpoints introduced. `FinnhubQuoteClient` outbound HTTP is existing threat surface (T-08-DEMO-NET, T-08-DOS, T-08-LEAK-FH) — all mitigated per plan.

## Known Stubs

None — `buildSeededQuote` reads real `OhlcvBarRepository.findLatestCloseByTicker()` data; seeded bars are present in Flyway migrations.

## Self-Check: PASSED

Files exist:
- `backend/src/main/java/com/quantlens/ai/tools/StockQuoteResult.java` FOUND
- `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java` FOUND
- `backend/src/main/java/com/quantlens/ai/tools/StockQuoteToolService.java` FOUND
- `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java` FOUND
- `backend/src/test/java/com/quantlens/ai/MultiProviderRoutingTest.java` FOUND

Commits exist: 9dd6c4f, b4e7c7a, 29be4dc, e52f862
