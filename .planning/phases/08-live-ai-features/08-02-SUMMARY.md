---
phase: 08-live-ai-features
plan: "02"
subsystem: ai
tags: [structured-output, spring-ai, bean-output-converter, demo-mode, seeding]
dependency_graph:
  requires: [06-01, 06-02, 06-03, 08-01]
  provides: [GET /api/ai/structured, StructuredInsightRecord, StructuredOutputService, ai-v4 seeds]
  affects: [AiController, AiSeedRunner, frontend ai store, StructuredOutputChart]
tech_stack:
  added: []
  patterns:
    - "hasKey() branch before .entity() (Pitfall 2 guard — explicit demo parse, no BeanOutputConverter in demo)"
    - "objectMapper.readValue(seedContent, StructuredInsightRecord.class) — mirrors ChatService.parseDemoResponse"
    - ".entity(StructuredInsightRecord.class) — BeanOutputConverter auto-schema in live mode"
    - "ai-v4 seed_log upsert — same loop handles new rows without code change"
key_files:
  created:
    - backend/src/main/java/com/quantlens/ai/api/InsightEntry.java
    - backend/src/main/java/com/quantlens/ai/api/StructuredInsightRecord.java
    - backend/src/main/java/com/quantlens/ai/service/StructuredOutputService.java
    - backend/src/test/java/com/quantlens/ai/StructuredOutputServiceTest.java
  modified:
    - backend/src/main/java/com/quantlens/ai/api/AiController.java
    - backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java
    - backend/src/test/java/com/quantlens/ai/AiControllerIntegrationTest.java
    - frontend/src/stores/ai.ts
    - frontend/src/__tests__/components/StructuredOutputChart.test.ts
decisions:
  - "InsightEntry uses double (not BigDecimal) — BeanOutputConverter JSON schema maps cleanly to JSON number"
  - "StructuredOutputService branches explicitly on hasKey() before .entity() (Pitfall 2) — demo never reaches BeanOutputConverter"
  - "series wrapped under named field in StructuredInsightRecord — avoids OpenAI top-level-array restriction (Pitfall 5)"
  - "AI_SEED_VERSION bumped to ai-v4 — upsert loop handles new rows on existing DBs transparently"
  - "fetchStructured error string updated to 'Failed to load structured output' (consistent with other errors)"
  - "/ai-structured-demo.json left in /public — no longer fetched by the store; noted as unused fixture"
metrics:
  duration: "~12 minutes"
  completed_date: "2026-06-09"
  tasks_completed: 3
  files_changed: 9
---

# Phase 08 Plan 02: Structured Output (AI-06) Summary

JWT-typed structured sector-exposure insight via `StructuredInsightRecord` + `InsightEntry` records, `StructuredOutputService` (demo `readValue` / live `.entity()`), `GET /api/ai/structured`, ai-v4 `STRUCTURED_INSIGHT` seeds per persona, and frontend URL swap from static JSON to live endpoint.

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | StructuredInsightRecord + InsightEntry + StructuredOutputService | `39002c4` | InsightEntry.java, StructuredInsightRecord.java, StructuredOutputService.java, StructuredOutputServiceTest.java |
| 2 | GET /api/ai/structured + ai-v4 seeds + integration coverage | `a89374f` | AiController.java, AiSeedRunner.java, AiControllerIntegrationTest.java |
| 3 | Frontend URL swap + live-DTO chart test | `32cd43c` | ai.ts, StructuredOutputChart.test.ts |

## Verification Results

- `StructuredOutputServiceTest` GREEN (4 tests: demo seed→record, live .entity()→record, 401 unknown portfolio, 502 provider error)
- `AiControllerIntegrationTest` GREEN (structured_demoMode_returnsRecordShape + structured_unauthenticated_returns401)
- `StructuredOutputChart.test.ts` GREEN (8 tests: 7 existing + 1 new live-DTO test)
- Full backend suite `.\mvnw.cmd -q test` GREEN
- `npm run build` GREEN (vue-tsc + vite; chunk size warning is pre-existing)

## Deviations from Plan

None — plan executed exactly as written.

**Notes:**
- `backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java` has an unstaged duplicate-javadoc cleanup left over from 08-01 (cosmetic; does not affect compilation or tests). Will be carried forward to 08-03.
- `/public/ai-structured-demo.json` is left in place — the store no longer fetches it, but it is harmless as a static public file and serves as a reference fixture.

## Threat Model Coverage

| Threat ID | Status |
|-----------|--------|
| T-08-IDOR-ST | Mitigated — portfolio resolved via `resolvePortfolioId(authentication)` only |
| T-08-LEAK-ST | Mitigated — 502 catch never echoes `e.getMessage()`; verified in StructuredOutputServiceTest.providerError_wrappedAs502 |
| T-08-JSON | Mitigated — typed record fields (String/double/List); demo seed schema validated by integration test |
| T-08-DEMO-NET-ST | Mitigated — demo branch on `hasKey()==false` reads seed before any `.entity()`/forSession call; no-network proof in 08-03 |
| T-08-SC | Accepted — no new packages installed |

## Known Stubs

None — the endpoint is fully wired in both demo and live modes. The static `/ai-structured-demo.json` fixture is now unused by the store but remains in `/public`.

## Self-Check: PASSED

- InsightEntry.java: FOUND
- StructuredInsightRecord.java: FOUND
- StructuredOutputService.java: FOUND
- StructuredOutputServiceTest.java: FOUND
- AiController.java (GET /structured endpoint): FOUND
- AiSeedRunner.java (ai-v4, STRUCTURED_INSIGHT): FOUND
- AiControllerIntegrationTest.java (structured tests): FOUND
- frontend/src/stores/ai.ts (/api/ai/structured): FOUND
- StructuredOutputChart.test.ts (8 tests): FOUND
- Commits 39002c4, a89374f, 32cd43c: FOUND
