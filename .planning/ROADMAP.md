# Roadmap: QuantLens — AI Portfolio & Market Intelligence Dashboard

## Overview

QuantLens is built in 10 phases that respect a hard dependency order enforced by the research: data foundation first, then the quant engine (the AI has nothing credible to narrate until real metrics exist), then the demo-mode AI seam (every AI panel must be screenshot-ready before any real LLM is wired), then live AI features and RAG, then the product MCP server, and finally documentation polish. The frontend scaffold appears early (Phase 3) so chart bindings can be validated against real portfolio data before the quant engine adds complexity. Every one of the 32 v1 requirements maps to exactly one phase.

## Phases

**Phase Numbering:**
- Integer phases (1-10): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

Decimal phases appear between their surrounding integers in numeric order.

- [x] **Phase 1: Data Foundation** - Docker stack, Postgres+pgvector, Flyway seeds, demo users, price and factor series, dev MCP tooling [IN PROGRESS — 1/4 plans complete] (completed 2026-06-07)
- [x] **Phase 2: Portfolio Domain** - Holdings, P&L, allocation, transactions, benchmark — full portfolio REST API [IN PROGRESS — 3/4 plans complete] (completed 2026-06-07)
- [ ] **Phase 3: Frontend Scaffold** - Vue 3 + Pinia + ECharts dashboard, portfolio chart bindings, auth flow, OAuth upgrade path docs
- [ ] **Phase 4: Quant Risk Engine** - Sharpe, VaR, beta, volatility, correlation heatmap, Fama-French attribution, cointegration pairs scanner
- [ ] **Phase 5: Stochastic Forecasting** - Monte Carlo fan charts across four models (GBM, Merton, Heston, bootstrap) plus rationale documentation
- [ ] **Phase 6: Demo-Mode AI Seam** - DemoModeAdvisor, LlmKeySessionHolder, ChatClientStrategy, seeded fixtures, BYO-key popup, explain-position and commentary in demo mode
- [ ] **Phase 7: RAG Pipeline** - pgvector ingestion, pre-seeded embeddings, QuestionAnswerAdvisor, NL Q&A chat with conversation memory
- [ ] **Phase 8: Live AI Features** - Finnhub @Tool integration, multi-provider ChatClient (Claude + OpenAI), structured output driving a chart
- [ ] **Phase 9: MCP Server** - @McpTool beans, Streamable HTTP transport, Spring Security on /mcp, Claude Code .mcp.json config, MCP docs
- [ ] **Phase 10: Polish & Documentation** - README screenshots, OpenAPI spec, Spring Modulith ArchUnit verification test

## Phase Details

### Phase 1: Data Foundation
**Goal**: The full stack launches from a single `docker compose up` with a healthy Postgres+pgvector database, Flyway-seeded demo users, ~15 securities with ~2 years of daily OHLCV price series, benchmark series, Fama-French factor series, and a locked embedding dimension — so every downstream phase has real data to work with from day one.
**Mode:** mvp
**Depends on**: Nothing (first phase)
**Requirements**: DATA-01, DATA-02, DATA-03, AUTH-01, AUTH-02, DEVX-01
**Success Criteria** (what must be TRUE):
  1. `docker compose up` from a clean checkout starts backend, frontend, and Postgres with no manual steps and no API keys required
  2. On first start Flyway runs all migrations and seeds three demo users (Alice/growth, Bob/income, Charlie/balanced) with pre-built portfolios; each user can log in with a session cookie that survives page refresh and scopes portfolio data to that user
  3. The `vector_store` table and HNSW index are present and queryable after cold start (pgvector initialize-schema=true verified)
  4. The database contains price series for ~15 securities (~2 years daily OHLCV), a seeded S&P 500 benchmark series, and Fama-French factor return series (Mkt-RF, SMB, HML)
  5. Dev-side MCP servers (context7 for Spring AI docs, Postgres MCP) are configured in `.mcp.json` and Claude Code can connect to them
**Plans**: 4 plans (Walking Skeleton)
Plans:
- [x] 01-01-PLAN.md — Scaffold backend (Boot 3.5.13/Java 21 Modulith, pinned BOMs) + Wave 0 test scaffolds (Testcontainers pgvector) [COMPLETE 2026-06-07]
- [x] 01-02-PLAN.md — Flyway schema + pgvector(1536)/HNSW + idempotent correlated-GBM seeder (DATA-02, DATA-03)
- [x] 01-03-PLAN.md — Spring Security form login (JSON handlers, session, persona scoping) (AUTH-01, AUTH-02) [COMPLETE 2026-06-07]
- [x] 01-04-PLAN.md — Vue shell + Dockerfiles + docker-compose + .mcp.json; `docker compose up` end-to-end (DATA-01, DEVX-01)

### Phase 2: Portfolio Domain
**Goal**: Users can view their complete portfolio — holdings with P&L, allocation breakdown, transaction history, and benchmark comparison — all computed from seeded data through a clean REST API.
**Mode:** mvp
**Depends on**: Phase 1
**Requirements**: PORT-01, PORT-02, PORT-03, PORT-04, PORT-05
**Success Criteria** (what must be TRUE):
  1. User can view a holdings table showing each position's current value, weight, cost basis, and unrealized P&L
  2. User can view portfolio-level P&L as a time-series curve with total unrealized gain/loss and daily change
  3. User can view an allocation breakdown by sector/asset class as a pie or treemap
  4. User can view a paginated transaction history showing buy/sell date, quantity, price, and running cost basis
  5. User can view portfolio return vs seeded S&P 500 proxy on the same chart
**Plans**: 4 plans
Plans:
- [x] 02-01-PLAN.md — Wave 0: 6 DTO contracts + 3 N+1-safe repository extensions + RED test scaffolds + golden-value printer (PORT-01..05 foundation) [COMPLETE 2026-06-07]
- [x] 02-02-PLAN.md — PortfolioService + Controller: /holdings + /allocation, principal-scoped (PORT-01, PORT-03) [COMPLETE 2026-06-07]
- [x] 02-03-PLAN.md — Equity-curve P&L + benchmark rebasing: /pnl + /benchmark (PORT-02, PORT-05) [COMPLETE 2026-06-07]
- [x] 02-04-PLAN.md — Running cost basis + paginated /transactions + page-size cap; full suite green (PORT-04)

### Phase 3: Frontend Scaffold
**Goal**: A cohesive Vue 3 single-page dashboard presents all portfolio data via ECharts visualizations, the demo-user switcher, and the auth flow — with the OAuth upgrade path documented for future reference.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: AUTH-03, UI-01
**Success Criteria** (what must be TRUE):
  1. The dashboard renders all portfolio views (holdings, P&L, allocation, transactions, benchmark) in a single-page layout using ECharts, driven by Pinia stores that fetch from the REST API
  2. User can switch between the three demo personas (Alice, Bob, Charlie) and the dashboard re-scopes all data to the selected user's portfolio without a full page reload
  3. The README documents the four-step upgrade path to add real OAuth (Google/GitHub) without code changes to `LlmKeySessionHolder` or downstream AI features
**Plans**: 5 plans
Plans:
- [x] 03-01-PLAN.md — Wave 0: Vitest infra + dev deps, dark-theme tokens, format.ts util + tests, ECharts plugin/theme + App.vue THEME_KEY (UI-01)
- [x] 03-02-PLAN.md — Data layer: api/portfolio.ts (verified DTO types), portfolio Pinia store + 401 interceptor + store tests (UI-01)
- [x] 03-03-PLAN.md — Chart components: PnlChart + BenchmarkChart + AllocationChart (donut/treemap) (UI-01)
- [x] 03-04-PLAN.md — Table/card primitives: SignedValue, SlotPlaceholder, KpiCard, HoldingsTable, TransactionsTable + component tests (UI-01) [COMPLETE 2026-06-07]
- [ ] 03-05-PLAN.md — Assembly: TopBar + DashboardView grid + persona switch + LoginView polish + README OAuth path (UI-01, AUTH-03)
**UI hint**: yes

### Phase 4: Quant Risk Engine
**Goal**: Users can view a full risk scorecard, correlation heatmap, Fama-French factor attribution, and cointegration pairs scanner — each metric backed by golden-value unit tests proving correctness before the AI layer ever narrates them.
**Mode:** mvp
**Depends on**: Phase 2
**Requirements**: RISK-01, RISK-02, RISK-03, ATTR-01, ARB-01
**Success Criteria** (what must be TRUE):
  1. User can view a risk scorecard showing Sharpe ratio (log returns, 252-day annualization), annualized volatility, max drawdown, beta vs benchmark, and 95% VaR — all with golden-value unit tests passing
  2. User can view VaR computed by both parametric (Gaussian) and historical methods displayed side by side with method, confidence, and horizon labels
  3. User can view a pairwise return-correlation heatmap across holdings (color scale -1 to +1)
  4. User can view Fama-French 3-factor attribution showing alpha and factor betas (Mkt-RF, SMB, HML) as a contribution bar chart
  5. User can view a cointegration pairs scanner listing candidate pairs with Engle-Granger p-value, current spread Z-score, and mean-reversion signal
**Plans**: TBD

### Phase 5: Stochastic Forecasting
**Goal**: Users can view a Monte Carlo fan chart of projected portfolio value across four switchable models (GBM, Merton jump-diffusion, Heston, block bootstrap) with percentile bands, and read the documented rationale for each model's assumptions and limitations.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: SIM-01, SIM-02, SIM-03
**Success Criteria** (what must be TRUE):
  1. User can view a fan chart of projected portfolio value showing p5/p25/p50/p75/p95 percentile bands over a forward horizon
  2. User can switch the forecast between four models (GBM, Merton jump-diffusion, Heston with fixed illustrative parameters, historical block bootstrap) and the fan chart updates accordingly
  3. Documentation (README or dedicated page) explains each model's rationale, key assumptions, parameter choices, and known limitations — readable by a non-specialist hiring manager
**Plans**: TBD
**UI hint**: yes

### Phase 6: Demo-Mode AI Seam
**Goal**: Every AI panel (explain-this-position, daily commentary, structured-output chart) renders realistic seeded content with zero API key via a single DemoModeAdvisor — and a BYO-key popup is wired and ready to flip all panels to live mode the moment a key is entered.
**Mode:** mvp
**Depends on**: Phase 4
**Requirements**: AI-01, AI-02, AI-07, AI-08
**Success Criteria** (what must be TRUE):
  1. All AI panels (explain-position, daily commentary, structured-output chart) render authored, LLM-looking content with no API key configured — demo mode is the default state
  2. User can open a BYO-key popup, select Anthropic Claude or OpenAI, and enter a session-only key; the key is never logged, echoed to the client, or persisted beyond the HTTP session (verified by a key-leakage integration test)
  3. User can click any holding and see an AI-generated "explain this position" narrative panel (seeded in demo, live after key entry)
  4. User sees an AI-generated daily portfolio commentary on the dashboard home (seeded in demo, live after key entry)
**Plans**: TBD
**UI hint**: yes

### Phase 7: RAG Pipeline
**Goal**: Users can ask freeform natural-language questions about the portfolio (with conversation memory) and get answers from embedded SEC 10-K filings via a QuestionAnswerAdvisor wired to pgvector — all working in demo mode via pre-seeded embeddings with no key required.
**Mode:** mvp
**Depends on**: Phase 6
**Requirements**: AI-03, AI-04
**Success Criteria** (what must be TRUE):
  1. User can ask freeform natural-language questions about the portfolio in a chat interface and receive contextually accurate answers; the chat retains conversation memory across turns within a session
  2. User can ask questions answered from seeded SEC 10-K filings (e.g., "What does Apple say about AI risk in their 10-K?") and receive answers with source citations; this works in demo mode because embeddings are pre-seeded in pgvector at startup with no API key
  3. Entering a BYO key switches answer generation to a live LLM call while using the same vector retrieval path
**Plans**: TBD
**UI hint**: yes

### Phase 8: Live AI Features
**Goal**: Tool calling for live quotes, multi-provider ChatClient routing (Claude or OpenAI via per-session mutate()), and at least one AI response delivered as typed structured output driving a chart are all working — with demo-mode fallbacks proven before any live key path is tested.
**Mode:** mvp
**Depends on**: Phase 7
**Requirements**: AI-05, AI-06
**Success Criteria** (what must be TRUE):
  1. User can request a live stock quote in chat and see the LLM invoke a Finnhub @Tool; in demo mode the tool returns the last seeded price; in live mode it calls the real Finnhub API (with a 15-minute TTL cache and correct after-hours labeling)
  2. At least one AI response is delivered as a typed Java record via structured output (BeanOutputConverter) that drives a Vue ECharts chart directly — the chart renders the same DTO path whether in demo or live mode
  3. Switching the BYO-key provider between Anthropic Claude and OpenAI produces a successful live AI call from the same ChatClientStrategy.forSession() entry point
**Plans**: TBD

### Phase 9: MCP Server
**Goal**: Portfolio analytics are exposed as a product MCP server via @McpTool beans on Streamable HTTP transport, secured by Spring Security, with a .mcp.json config that allows Claude Code to connect — and documentation shows any MCP client how to use it.
**Mode:** mvp
**Depends on**: Phase 8
**Requirements**: MCP-01, MCP-02
**Success Criteria** (what must be TRUE):
  1. An MCP client (e.g., Claude Code) can connect to `http://localhost:8080/mcp` using the committed `.mcp.json` config and call `get_portfolio_summary`, `get_risk_metrics`, and `get_position_detail` tools, receiving correctly-computed responses from the seeded data
  2. The /mcp endpoint requires authentication before any tool is reachable; MCP error responses never contain Java stack traces
  3. The README documents how any MCP client connects to the server and what tools are available
**Plans**: TBD

### Phase 10: Polish & Documentation
**Goal**: The repository reads as senior-engineer work: README with live-demo screenshots, OpenAPI spec, and a Spring Modulith ArchUnit test that verifies module boundaries as a living contract.
**Mode:** mvp
**Depends on**: Phase 9
**Requirements**: DOCS-01
**Success Criteria** (what must be TRUE):
  1. The README includes screenshots of the running dashboard in demo mode (all AI panels visible, fan chart model selector active, RAG Q&A populated) captured via live mode with a real LLM key
  2. An OpenAPI spec is generated via springdoc-openapi and accessible at `/v3/api-docs`; all portfolio, analytics, and AI endpoints are documented
  3. A Spring Modulith ApplicationModules.verify() ArchUnit test passes, confirming the module dependency graph (ai → portfolio + analytics, analytics → portfolio, mcp → portfolio + analytics, auth standalone) and generating a module diagram
**Plans**: TBD

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Data Foundation | 4/4 | Complete   | 2026-06-07 |
| 2. Portfolio Domain | 4/4 | Complete   | 2026-06-07 |
| 3. Frontend Scaffold | 3/5 | In Progress|  |
| 4. Quant Risk Engine | 0/TBD | Not started | - |
| 5. Stochastic Forecasting | 0/TBD | Not started | - |
| 6. Demo-Mode AI Seam | 0/TBD | Not started | - |
| 7. RAG Pipeline | 0/TBD | Not started | - |
| 8. Live AI Features | 0/TBD | Not started | - |
| 9. MCP Server | 0/TBD | Not started | - |
| 10. Polish & Documentation | 0/TBD | Not started | - |
