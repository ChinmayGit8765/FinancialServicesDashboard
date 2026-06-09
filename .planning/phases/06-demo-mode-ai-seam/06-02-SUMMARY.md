# Plan 06-02 Summary — Seeded Fixtures + CSRF

**Status:** complete
**Plan:** 06-02 (Demo-Mode AI Seam — seeded fixtures + CSRF for the key endpoint)

## What was delivered

### Task 1 — Idempotent AiSeedRunner with authored persona-accurate fixtures (committed `761a89e`)
- `AiSeedRunner` (`com.quantlens.ai.seed`, `@Component @Order(2) ApplicationRunner`, `@Transactional`) seeds `ai_seed_content` idempotently, guarded by `seed_log` id `"ai-v1"` (completion row written LAST so a mid-seed crash rolls back).
- **16 rows:** 13 `EXPLAIN_POSITION` (one per seeded ticker — AAPL, MSFT, NVDA, AMZN, TSLA, JPM, BAC, XOM, CVX, PG, KO, WMT, JNJ), subjectId = ticker; + 3 `DAILY_COMMENTARY` (subjectId = `GROWTH`/`INCOME`/`BALANCED`). Content authored to look LLM-generated and consistent with each persona's real holdings (no contradiction with the dashboard).
- `AiSeedRunnerTest` (extends `AbstractPostgresIntegrationTest`): asserts row counts, per-persona presence, ticker-substring content, and idempotency on re-run. **8 tests green.**

### Task 2 — CSRF for /api/ai/key + no-network proof
- **CSRF (done):** `SecurityConfig` already lists `"/api/ai/key"` in `.ignoringRequestMatchers("/api/auth/login", "/api/auth/logout", "/api/ai/key")` (committed in 06-01 when AiKeyController was created). `/api/ai/**` stays under `.anyRequest().authenticated()` — no permitAll; the key POST is session-bound + SameSite=Lax. `KeyLeakageIntegrationTest` and `AiKeyControllerTest` green (key never leaks; POST accepted).
- **Executable no-network proof — FOLDED INTO 06-03:** the proof (assert the advisor chain's `nextCall()` is invoked 0 times in demo mode) is only meaningful once `ExplainPositionService`/`CommentaryService` actually route through the `ChatClient` + `DemoModeAdvisor` (which is 06-03's work — the services are stubs at the end of 06-01/06-02). Wiring the test here would either be vacuous (services don't call the chat client yet) or require editing 06-03's service files (out of this plan's `files_modified` scope, which caused two truncated runs). **Decision (orchestrator): 06-03 wires the services through the advisor AND upgrades `AiDemoModeIntegrationTest` to the executable `nextCall()==0` proof in the same change** — keeping the proof meaningful and the file ownership clean.

## DAILY_COMMENTARY storage convention (for 06-03 CommentaryService parsing)
The single `content` string encodes: **Line 1** = headline (no prefix); **blank line**; **body paragraph** (1–2 sentences); then **bullet lines prefixed `- `** (2–4). `CommentaryService` (06-03) splits this into `CommentaryDto(headline, body, bulletPoints[])`.

## Verification
- `mvnw test -Dtest=AiSeedRunnerTest,KeyLeakageIntegrationTest,AiKeyControllerTest,DemoModeAdvisorTest,QuantLensModulithTest` → **19 tests, 0 failures, BUILD SUCCESS.**
- Demo mode boots key-less (sentinel) and the security gate stays green.

## Handoff to 06-03
1. Wire `ExplainPositionService` + `CommentaryService` to call the `ChatClient` (which runs `DemoModeAdvisor` first) so demo returns the seeded `ai_seed_content` (explain = raw narrative; commentary = parsed per the convention above).
2. Upgrade `AiDemoModeIntegrationTest` to the executable no-network proof (a counting `CallAdvisor` after `DemoModeAdvisor` → assert `nextCall()` count == 0 across demo explain + commentary; assert 200 + non-blank seeded content).

## Commits
- `761a89e` — feat(06-02): Task 1 idempotent AiSeedRunner with authored persona-accurate fixtures
- (CSRF for /api/ai/key was already committed in 06-01)
