---
phase: 08-live-ai-features
plan: "03"
subsystem: ai/security-gate + ai/demo-mode
tags: [security, key-leak, demo-mode, no-network-proof, green-gate, spring-ai]
dependency_graph:
  requires: [08-01, 08-02]
  provides: [T-08-LEAK-ST-gate, T-08-DEMO-NET-structured-proof, phase-green-gate]
  affects:
    - backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java
    - backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java
    - backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java
tech_stack:
  added: []
  patterns:
    - "KeyLeakageIntegrationTest: step 5b GET /api/ai/structured assertIsIn(200,502) + doesNotContain(testKey)"
    - "Finnhub key-leak threat (T-08-LEAK-FH) documented inline — ACTIVE proof referenced in 08-01 StockQuoteToolServiceTest"
    - "AiDemoModeIntegrationTest: NoNetworkProofConfig.NEXT_CALL_COUNT==0 proves DemoModeAdvisor short-circuits before provider"
    - "@Autowired on primary constructor required when Spring 6 bean has multiple constructors — prevents NoSuchMethodException: <init>()"
key_files:
  created: []
  modified:
    - backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java
    - backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java
    - backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java
    - backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java
decisions:
  - "[08-03] @Autowired required on FinnhubQuoteClient primary constructor — Spring 6 requires
    explicit annotation when a bean class declares multiple constructors; without it Spring cannot
    determine which to use and falls back to no-arg (NoSuchMethodException), breaking context load"
  - "[08-03] T-08-LEAK-FH vacuous at endpoint boundary — test env FINNHUB_API_KEY blank so seeded
    fallback runs; endpoint-level assertion documented inline; ACTIVE proof stays in 08-01"
  - "[08-03] Phase green gate PARTIAL — backend 189/197 tests GREEN (8 pre-existing failures in
    AiSeedRunnerTest and StructuredOutputServiceTest from 08-02, not introduced by 08-03);
    frontend 86/86 GREEN + build GREEN"
metrics:
  duration: "~25 minutes"
  completed_date: "2026-06-09"
  tasks_completed: 2
  files_changed: 4
---

# Phase 08 Plan 03: Security + Offline Gate Summary

**One-liner:** Extend KeyLeakageIntegrationTest to cover GET /api/ai/structured (T-08-LEAK-ST), add AiDemoModeIntegrationTest structured no-network proof (CountingCallAdvisor==0, T-08-DEMO-NET), fix @Autowired on FinnhubQuoteClient primary constructor (Spring 6 multi-constructor DI), and run the phase green gate confirming frontend 86/86 green + build — 8 pre-existing backend failures from 08-02 reported.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | Extend KeyLeakageIntegrationTest + AiDemoModeIntegrationTest | `c469004` | KeyLeakageIntegrationTest.java, AiDemoModeIntegrationTest.java, FinnhubQuoteClient.java, ChatClientStrategy.java |
| 2 | Phase green gate — pure verification | (metadata commit) | None modified |
| 3 | Human-verify checkpoint | — | Deferred (live UAT requires real keys) |

## Verification Results

### Task 1 (targeted)
- `KeyLeakageIntegrationTest` GREEN (1 test — now includes step 5b for `/api/ai/structured`)
- `AiDemoModeIntegrationTest` GREEN (4 tests — including new `demoMode_structured_returnsSeededContent_withZeroNetworkCalls`)

### Task 2 — Phase Green Gate

**Backend (`.\mvnw.cmd verify`):**
- Tests run: 197 total, **189 GREEN**, 8 pre-existing failures (not introduced by 08-03)
- Status: BUILD FAILURE (pre-existing 08-02 regressions; see Deferred Issues below)

**Frontend (`npm run test -- --run`):**
- 86/86 tests PASSED across 16 test files

**Frontend (`npm run build`):**
- vue-tsc + vite: SUCCESS (chunk size warning is pre-existing, unrelated to this plan)

### Human-verify checkpoint (Task 3)
Deferred — requires real Anthropic/OpenAI keys and a running Docker stack. The automated proof (CountingCallAdvisor==0, KeyLeakage assertions) is complete. Visual/live UAT items remain.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] @Autowired missing on FinnhubQuoteClient primary constructor**
- **Found during:** Task 1 test execution — both AiDemoModeIntegrationTest and KeyLeakageIntegrationTest failed to load ApplicationContext
- **Issue:** `FinnhubQuoteClient` declares two constructors (public 2-arg + package-private 4-arg for test injection). Spring 6 / Spring Boot 3 requires `@Autowired` when a bean has multiple constructors; without it Spring falls back to no-arg constructor, which doesn't exist — `NoSuchMethodException: FinnhubQuoteClient.<init>()`
- **Fix:** Added `@Autowired` annotation to the public primary constructor `(ObjectMapper, OhlcvBarRepository)`
- **Files modified:** `FinnhubQuoteClient.java`
- **Commit:** `c469004`

**2. Cosmetic carry-forward: ChatClientStrategy duplicate Javadoc removed**
- Pre-existing from 08-02 SUMMARY note; removed in c469004

## Deferred Issues (Task 2 Green Gate — 08-02 ownership)

| Test | Failure | Owning Plan |
|------|---------|-------------|
| `AiSeedRunnerTest.idempotency_rowCounts_stableAfterContextStart` | Expects 20 ai_seed_content rows; finds 23 (08-02 added 3 STRUCTURED_INSIGHT rows for 3 personas without updating assertion) | 08-02 |
| `AiSeedRunnerTest.seedLog_aiV3_isMarkedCompleted` | Expects `ai-v3` seed_log row; 08-02 bumped to `ai-v4` without updating this test | 08-02 |
| `StructuredOutputServiceTest` (all 6 tests) | `UnnecessaryStubbing` — `@BeforeEach` stubs `stubPortfolio.getStyle()` / `.getName()` which are unnecessary in `unknownPortfolio_throws401` and other paths; Mockito strict mode reports these | 08-02 |

**No cross-plan repairs were performed.** These failures are documented here per Task 2's mandate; the orchestrator handles the fix.

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-08-LEAK-ST | Mitigated — KeyLeakageIntegrationTest step 5b asserts key absent from /api/ai/structured response and all captured log lines |
| T-08-LEAK-FH | Documented at endpoint boundary (vacuously safe — no FINNHUB_API_KEY in test env); ACTIVE proof in 08-01 StockQuoteToolServiceTest |
| T-08-DEMO-NET | Mitigated — AiDemoModeIntegrationTest.demoMode_structured_returnsSeededContent_withZeroNetworkCalls: NEXT_CALL_COUNT==0 proves no provider call |

## Known Stubs

None — no new production code added in this plan.

## Human-Verify Checkpoint (Task 3)

**Status:** Deferred for live UAT. The automated proof gate is complete. Outstanding manual items:

1. `docker compose up` from clean checkout — confirm structured-output chart renders seeded DTO with zero keys (demo mode)
2. Enter real Anthropic key → ask "what's AAPL trading at?" — confirm LLM invokes quote @Tool, price + timestamp returned, no key leak in UI or logs
3. Switch to OpenAI key → repeat structured-output request — confirm same DTO shape rendered from OpenAI provider

**Resume signal:** Type "approved" or describe any issue.

## Threat Surface Scan

No new network endpoints introduced. No new auth paths. No schema changes.

## Self-Check: PASSED

- KeyLeakageIntegrationTest.java: FOUND (contains `/api/ai/structured`)
- AiDemoModeIntegrationTest.java: FOUND (contains `demoMode_structured`)
- FinnhubQuoteClient.java: FOUND (contains `@Autowired`)
- Commit c469004: FOUND
