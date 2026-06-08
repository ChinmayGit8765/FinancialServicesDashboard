---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: ready_to_plan
stopped_at: Phase 04 complete (4/4) — ready to discuss Phase 5
last_updated: 2026-06-08T11:05:10.657Z
last_activity: 2026-06-08
progress:
  total_phases: 10
  completed_phases: 4
  total_plans: 17
  completed_plans: 17
  percent: 40
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-07)

**Core value:** A working, screenshot-ready dashboard that demonstrates real quantitative-finance analytics narrated by a real Spring AI layer — convincing with zero setup, genuinely live when you add a key.
**Current focus:** Phase 5 — stochastic forecasting

## Current Position

Phase: 5
Plan: Not started
Status: Ready to plan
Last activity: 2026-06-08

Progress: [██████████] 100%

## Performance Metrics

**Velocity:**

- Total plans completed: 18
- Average duration: ~7 minutes
- Total execution time: 0.12 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01-data-foundation | 1 of 4 | ~7 min | ~7 min |
| 01 | 4 | - | - |
| 02 | 4 | - | - |
| 03 | 5 | - | - |
| 04 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: 01-01 (7 min)
- Trend: Baseline established

*Updated after each plan completion*
| Phase 01 P02 | 35 minutes | 2 tasks | 29 files |
| Phase 01 P03 | 16 minutes | 2 tasks | 9 files |
| Phase 01 P04 | 45 minutes | 2 tasks | 16 files |
| Phase 02 P01 | 15 minutes | 3 tasks | 12 files |
| Phase 02 P02 | 10 minutes | 2 tasks | 3 files |
| Phase 02 P03 | 20 minutes | 2 tasks | 4 files |
| Phase 02-portfolio-domain P04 | 15 | 2 tasks | 4 files |
| Phase 03-frontend-scaffold P01 | 5 | 3 tasks | 12 files |
| Phase 03-frontend-scaffold P02 | 3 | 2 tasks | 4 files |
| Phase 03-frontend-scaffold P03 | 8 | 3 tasks | 3 files |
| Phase 03-frontend-scaffold P04 | 5 | 3 tasks | 8 files |
| Phase 03 P05 | 4 minutes | 3 tasks | 4 files |
| Phase 04 P01 | 20 | 3 tasks | 24 files |
| Phase 04-quant-risk-engine P02 | 95 | 2 tasks | 7 files |
| Phase 04-quant-risk-engine P03 | 40 | 2 tasks | 5 files |
| Phase 04-quant-risk-engine P04 | 10 | 2 tasks | 7 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Stack locked: Java 21, Spring Boot 3.5.13, Spring AI 1.1.6 (BOM-pinned), finmath-lib 6.1.7, Hipparchus 4.0.3, pgvector/pg16, Vue 3 + ECharts 5, Finnhub for live quotes
- Market data: Finnhub (io.finnhub:kotlin-client:2.0.22) — 60 req/min free tier replaces Alpha Vantage
- Demo embeddings: pre-computed Flyway-seeded float arrays — no live embedding API at docker compose up
- Heston params: fixed illustrative (kappa=2, theta=0.04, sigma_v=0.3, rho=-0.7, v0=0.04), Feller condition enforced in code
- Embedding dimension: 1536 (OpenAI text-embedding-3-small) — locked at Flyway migration time, document in TECH_DECISIONS.md
- [01-01] initialize-schema: false — Flyway owns vector_store DDL; Spring AI will not auto-create the table (prevents DDL divergence)
- [01-01] Spring Modulith BOM 1.4.11 selected (plan locks 1.4.11, not RESEARCH.md's assumed 1.3.5)
- [01-01] QuantLensModulithTest is fast-path (<15s) — no Spring context or DB needed for module boundary check
- [01-03] CSRF ignoringRequestMatchers on /api/auth/login and /api/auth/logout — login entry point cannot self-supply CSRF token; SameSite=Lax is the CSRF defence at the login boundary
- [01-03] DaoAuthenticationProvider(UserDetailsService) is the non-deprecated Spring Security 6.5.x constructor; setPasswordEncoder called separately
- [01-03] security module allowedDependencies = portfolio::domain — cross-module read for UserDetailsService + AuthController
- [01-04] layertools (not tools extract --layers --launcher) in backend Dockerfile — tools extract outputs to named subdirectory breaking COPY --from=build paths; layertools outputs flat to /workspace
- [01-04] @henkey/postgres-mcp-server in .mcp.json — replaces deprecated @modelcontextprotocol/server-postgres (archived July 2025, SQL injection CVE)
- [01-04] Walking skeleton closed: docker compose up → db (healthy) → backend (healthy) → frontend (up); alice/demo1234 login → portfolioId:1 verified live from seeded Postgres
- [02-03] buildEquityCurve is private (not public static) — depends on OhlcvBarRepository injection; only pure-math steps (computeDailyChange, rebaseToIndex, computeTotalUnrealizedGainAbs/Pct) are public static for unit tests
- [02-03] rebaseToIndex is public static to allow unit-test verification that both series[0] == 100.0000 without Spring context
- [02-03] SPX500 guard throws IllegalStateException on empty/multiple — fail-fast rather than returning silent wrong benchmark data
- [Phase ?]: computeRunningCostBasisFromTuples is public static to allow cross-subpackage access from PortfolioServiceTest
- [Phase ?]: Two-pass getTransactions: chronological scan for cost map + paginated query for display
- [Phase ?]: [03-01] ignoreDeprecations:6.0 for TS6 baseUrl compat
- [Phase ?]: [03-01] MONTH_ABBR array in formatDate — locale-stable 3-char months on Windows
- [Phase ?]: [03-01] globalThis cast in setup.ts — portable cross-env ResizeObserver mock
- [03-02] asyncState<T>() casts return as AsyncState<T> — vue reactive() returns UnwrapRef<T>; cast is safe (runtime shape identical)
- [03-02] 401 interceptor in api/auth.ts excludes /auth/me and /auth/login; falls back to window.location.href (T-03-06/A2)
- [03-02] fetchTransactions uses size:10 for dashboard panel (backend default is 20)
- [03-04] TransactionsTable emits 'page-change' (not 'page') to avoid Vue prop/emit name collision
- [03-04] HoldingsTable sort: 3-state cycle asc→desc→default(null); fraction*100 before formatSignedPercent for unrealizedPnlPct
- [Phase ?]: [04-01] analytics Modulith module: separate com.quantlens.analytics, no portfolio::service dependency
- [Phase ?]: [04-01] resolvePortfolioId copied verbatim from PortfolioController — IDOR T-04-01 mitigation, no portfolioId from request
- [Phase ?]: [04-01] tsconfig.app.json exclude __tests__ — test files excluded from vue-tsc production build to allow RED scaffolds importing non-existent components
- [Phase ?]: Local analytics.service.DateValueDto satisfies Modulith portfolio::domain boundary
- [Phase ?]: GBM seed all-1.0 correlation is correct (shared market factor only) — GOLDEN_CORR_AAPL_MSFT=1.0
- [Phase ?]: Risk and Correlation tests promoted to integration tests requiring AbstractPostgresIntegrationTest
- [Phase ?]: [04-04] RiskScorecard is props-driven (not store-reading) so tests mount without store context
- [Phase ?]: [04-04] portfolioStore.test.ts refreshAll count updated 5->9 for Phase-4 analytics additions
- [Phase ?]: [04-04] formatPercent takes 0-1 fraction; annualizedVolatility passed as-is to formatPercent without *100

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 2 research spike needed: ADF critical values (MacKinnon 1994) for hand-rolled Engle-Granger; finmath-lib HestonModel discretization (Euler-Maruyama vs Milstein); block bootstrap via Hipparchus
- Phase 4 research spike needed: Flyway binary column type for pre-computed float[] embeddings; structure-aware SEC EDGAR chunking; model.mutate() per-session key injection at Spring AI 1.1.6 (GitHub issue #2731)
- Phase 9 research spike needed: Spring AI MCP Streamable HTTP transport path config; Spring Security filter chain integration with spring-ai-mcp-server-boot-starter 1.1.6

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 Quant | SIM-04: Live Heston calibration from real data | Deferred | Roadmap init |
| v2 Quant | ATTR-02: 5-factor Fama-French upgrade | Deferred | Roadmap init |
| v2 Quant | ARB-02: Additional arb detectors | Deferred | Roadmap init |
| v2 Quant | OPT-01: Efficient frontier / mean-variance optimization | Deferred | Roadmap init |
| v2 Quant | BACK-01: Backtesting engine | Deferred | Roadmap init |
| v2 AI | AI-09: Streaming AI responses (SSE) | Deferred | Roadmap init |
| v2 Platform | MCP-03: @McpResource for filing documents | Deferred | Roadmap init |
| v2 Platform | AUTH-04: Real OAuth implementation | Deferred | Roadmap init |

## Session Continuity

Last session: 2026-06-08T10:28:36.818Z
Stopped at: Completed 04-04-PLAN.md — Phase-4 frontend panels complete: RiskScorecard, CorrelationHeatmap, AttributionChart, PairsTable wired into DashboardView
Resume file: None
