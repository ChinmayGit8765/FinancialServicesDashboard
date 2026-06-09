# Plan 06-03 Summary — Explain + Commentary Services (through the seam) + No-Network Proof

**Status:** complete
**Commit:** `2d3594d`

## What was delivered
- **ExplainPositionService** + **CommentaryService** now route through `ChatClientStrategy.forSession(keyHolder)` → `ChatClient` (with `DemoModeAdvisor` first). In demo mode the advisor short-circuits and returns the seeded `ai_seed_content` (explain = raw narrative; commentary parsed into `CommentaryDto(headline, body, bulletPoints)` per the storage convention). Live mode injects the per-request session key. No `if(demoMode)` branch in the services — the single switch lives in `DemoModeAdvisor`.
- **IDOR-safe explain:** the requested ticker is validated against the principal's holdings BEFORE any ChatClient call; unheld ticker → `404 NOT_FOUND` (proven by `ExplainPositionServiceTest` with `verifyNoInteractions(strategy)`). Provider failures (live) wrap as `502 BAD_GATEWAY` and never echo the key.
- **`AiController`** `/api/ai/explain/{ticker}` + `/api/ai/commentary` (principal-scoped, readOnly).
- **Executable no-network proof (folded in from 06-02):** `AiDemoModeIntegrationTest` registers a test-only counting `CallAdvisor` at order `HIGHEST_PRECEDENCE+1` (just after `DemoModeAdvisor`). In demo mode the advisor short-circuits, so the counter stays `0` — asserted, proving zero provider/network calls. Also asserts 200 + seeded non-blank content.
- **Module boundary:** `ai` module now allows `marketdata::domain` (it reads `Security` via `Position.getSecurity()` for ticker/sector context) — Modulith verify green.
- **KeyLeakage gate kept green:** with a fake session key the live path legitimately `502`s; the test now accepts 200-or-502 and asserts the key never appears in any response body or log line regardless.

## Verification
- AI suite green: ExplainPositionServiceTest 4, AiControllerIntegrationTest 4, AiDemoModeIntegrationTest 3 (incl. no-network proof), KeyLeakageIntegrationTest 1, AiKeyControllerTest 4, AiSeedRunnerTest 8, DemoModeAdvisorTest 5, QuantLensModulithTest 1.
- **Full backend `mvnw test` → BUILD SUCCESS** (post-merge gate).

## Note (orchestrator)
The executor agent truncated three times mid-edit on this plan (Windows stdio cut-off at the ChatClient varargs). The service/strategy/advisor files it had written were complete and correct but uncommitted; the orchestrator finished the remaining work directly (the executable no-network proof, the `marketdata::domain` boundary fix, and the KeyLeakage 200→200/502 fix), verified the full backend suite green, and committed.

## Handoff to 06-04 (frontend)
Backend AI endpoints live: `POST/DELETE /api/ai/key`, `GET /api/ai/status` ({mode,provider} only — never the key), `GET /api/ai/explain/{ticker}`, `GET /api/ai/commentary`. Build the BYO-key popup, mode badge, explain drawer, commentary card + the seeded structured-output stub.
