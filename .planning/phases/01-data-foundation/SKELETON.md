# Walking Skeleton — QuantLens

**Phase:** 1
**Generated:** 2026-06-07

## Capability Proven End-to-End

A user runs a single `docker compose up`, opens the Vue frontend, clicks "log in as Alice" (a seeded demo persona), and lands on a dashboard placeholder that shows Alice's persona and portfolio id — data read live from a seeded Postgres+pgvector database — with the session surviving a page refresh, all with zero API keys and zero manual setup.

This exercises every layer of the stack: container orchestration → Postgres (real read of seeded data) → Spring Security session auth (real write of a session) → Vue SPA (real interactive UI wired to the API).

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Backend framework | Spring Boot 3.5.13 / Java 21, Spring Modulith 1.4.11 (package-level modules), single Maven module via the wrapper | STACK.md-locked; Modulith over Maven multi-module gives DDD boundaries + an ArchUnit-verifiable contract with no build overhead (ARCHITECTURE Module Boundaries). Boot 3.5.13 pairs with Spring AI 1.1.6 |
| Base package / modules | `com.quantlens`; Phase-1 modules: `marketdata`, `portfolio`, `security`, `seed` (each `@ApplicationModule`). `analytics`, `ai`, `mcp` deferred to their phases | Only create packages a phase needs (CONTEXT). Inter-module rules enforced by `ApplicationModules.verify()` |
| Data layer | PostgreSQL 16 via `pgvector/pgvector:pg16`; Flyway owns relational + `vector_store` DDL; pgvector extension created by `docker/db/00-init.sql` (superuser) | Single DB for relational + RAG vectors (no separate vector service). Extension needs superuser, so it runs in the docker init script, not Flyway (RESEARCH Pitfall 1) |
| Money / dates | `BigDecimal` on `NUMERIC` columns; `LocalDate` for dates | Quant credibility; never `double` for money (CONTEXT, RESEARCH Pitfall 6) |
| Embedding dimension | Locked at **1536** (OpenAI text-embedding-3-small), `initialize-schema=true`, COSINE_DISTANCE, HNSW — set now even though embeddings arrive in Phase 7 | Changing dimension later requires drop + re-embed (CONTEXT, Definition of Done) |
| Seed strategy | Idempotent `@Order(1)` `ApplicationRunner` guarded by a `seed_log` completed flag; correlated GBM (Hipparchus `MersenneTwister(42)`, Ito-corrected) generates ~504-day OHLCV for ~15 securities + benchmark + Fama-French factors | Synthetic + reproducible = zero network dependency + stable screenshots (CONTEXT). Programmatic generation beats giant SQL |
| Auth | Spring Security form login over seeded BCrypt users; JSON success/failure handlers (not redirects); `HttpSession` (`SessionCreationPolicy.ALWAYS` + `changeSessionId`); `CookieCsrfTokenRepository.withHttpOnlyFalse()`; cookie-only session tracking | SPA-compatible auth; ASVS L1 V2/V3. Authorization rules kept independent of login mechanism so OAuth drops in later without rework (ARCHITECTURE Auth Seam) |
| One PasswordEncoder bean | Owned by `com.quantlens.seed.PasswordEncoderConfig`; seeder and `SecurityConfig` both autowire it | Prevents strength-mismatch between hashing at seed time and verification at login (RESEARCH Anti-Patterns) |
| Frontend | Vue 3 (Composition API, `<script setup>`, TS) + Vite + Pinia + Axios; vue-echarts/echarts added now, charts deferred to Phase 3 | UI-01 stack locked; shell only needs login + persona switcher + dashboard placeholder this phase |
| Deployment / run | `docker compose up` from repo root: db (healthcheck + named volume) → backend (`condition: service_healthy`) → frontend (nginx serving the Vite build, /api proxied to backend) | DATA-01; named volume (not bind mount) avoids Windows NTFS/WSL2 corruption (RESEARCH Pattern 7) |
| Dev tooling | `.mcp.json` at repo root: context7 (`@upstash/context7-mcp`) + a non-deprecated Postgres MCP (`@henkey/postgres-mcp-server`, or user-scoped `claude mcp add` fallback) | DEVX-01. `@modelcontextprotocol/server-postgres` is deprecated/archived (SQLi) and forbidden |
| Directory layout | Monorepo: `backend/` (Spring Boot), `frontend/` (Vue), `docker/db/` (init SQL), root `docker-compose.yml` + `.mcp.json` + `README.md` | CONTEXT-locked monorepo layout |

## Stack Touched in Phase 1

- [x] Project scaffold — Spring Initializr backend (Maven wrapper) + Vite Vue-TS frontend; Spring Modulith boundaries; build + test runner (JUnit 5 + Testcontainers)
- [x] Routing — backend `/api/auth/*` + `/actuator/health`; frontend vue-router `/login` and `/`
- [x] Database — real READ (login resolves a seeded user + portfolio; dashboard reads persona/portfolioId) AND real WRITE (Flyway migrations + idempotent seeder populate the DB; session persisted server-side)
- [x] UI — interactive one-click persona login wired to the REST API with session cookie + CSRF
- [x] Deployment — `docker compose up` brings up the full stack locally; README documents the cold-start verification signals

## Out of Scope (Deferred to Later Slices)

- Portfolio computation / REST endpoints — holdings, P&L, allocation, transactions, benchmark comparison (Phase 2)
- ECharts dashboards and the full Pinia store graph (Phase 3); only a placeholder dashboard ships now
- Quant metrics: Sharpe, VaR, beta, correlation, factor attribution, cointegration (Phase 4)
- Stochastic forecasting / Monte Carlo fan charts (Phase 5)
- Any AI/LLM wiring: DemoModeAdvisor, ChatClientStrategy, BYO-key popup, RAG, tool calling, structured output (Phases 6–8)
- Product MCP server (`@McpTool` beans on `/mcp`) (Phase 9) — distinct from the dev-side `.mcp.json` shipped now
- Real OAuth (Google/GitHub) — documented as an upgrade path only (Phase 3 / Polish)
- Real/imported market data and any expansion of the equities universe (kept synthetic for v1)
- Live embeddings at startup — embeddings are pre-seeded later; Phase 1 only locks the dimension and schema

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- Phase 2: User views their complete portfolio (holdings + P&L + allocation + transactions + benchmark) via a clean REST API computed from the seeded data
- Phase 3: Vue dashboard renders all portfolio views in ECharts, with a live persona switcher and the OAuth upgrade path documented
- Phase 4: Risk scorecard, correlation heatmap, Fama-French attribution, cointegration pairs scanner — each golden-value tested
- Phase 5: Monte Carlo fan chart across GBM / Merton / Heston / block bootstrap with model rationale docs
- Phase 6: Demo-mode AI seam (DemoModeAdvisor, LlmKeySessionHolder, BYO-key popup, explain-position + commentary)
- Phase 7: RAG pipeline over pre-seeded SEC 10-K embeddings with conversation memory
- Phase 8: Live AI — Finnhub @Tool, multi-provider ChatClient, structured output driving a chart
- Phase 9: Product MCP server on Streamable HTTP, secured by Spring Security
- Phase 10: README screenshots, OpenAPI spec, Modulith ArchUnit verification test
