# Phase 1: Data Foundation - Context

**Gathered:** 2026-06-07
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per user directive "use all recommended"

<domain>
## Phase Boundary

Stand up the full-stack skeleton and the seeded data layer that every downstream phase depends on. By the end of this phase: `docker compose up` from a clean checkout brings up Postgres+pgvector (healthy), the Spring Boot backend, and the Vue frontend shell; Flyway has created the relational + vector schema; an idempotent seeder has populated demo users, ~15 securities with ~2 years of daily OHLCV, a benchmark proxy series, and Fama-French factor series; and a user can log in as one of three demo personas with a session that persists across refresh.

**In scope:** project scaffolding (backend + frontend + compose), DB schema + pgvector extension + locked embedding dimension, synthetic seed-data generation, demo-user auth + session, dev-side MCP server config for Claude Code.

**Out of scope (later phases):** portfolio computation/REST endpoints (Phase 2), real charts/dashboard (Phase 3), quant metrics (Phase 4), any AI/LLM wiring (Phase 6+).

Requirements covered: DATA-01, DATA-02, DATA-03, AUTH-01, AUTH-02, DEVX-01.
</domain>

<decisions>
## Implementation Decisions

### Project Structure & Build
- Monorepo layout: `backend/` (Spring Boot) and `frontend/` (Vue 3) at repo root, with a root `docker-compose.yml` and `README.md`.
- Backend: single Maven module using the Maven wrapper (`./mvnw`), Java 21, Spring Boot 3.5.13, Spring AI BOM pinned to 1.1.6 (deps added as phases need them). Base package `com.quantlens`.
- Internal structure uses Spring Modulith package boundaries (e.g. `com.quantlens.portfolio`, `com.quantlens.marketdata`, `com.quantlens.analytics`, `com.quantlens.ai`, `com.quantlens.security`, `com.quantlens.seed`) — Modulith over Maven multi-module per architecture research. Only the packages needed this phase are created now.
- Money/quantities use `BigDecimal` (NUMERIC columns); dates use `LocalDate`.

### Demo Auth & Users
- Spring Security with form login over seeded users. Three personas seeded with BCrypt-hashed passwords: `alice` (Growth), `bob` (Income), `charlie` (Balanced), all sharing a demo password (e.g. `demo1234`) shown on the login screen for convenience.
- Login page offers a one-click "log in as <persona>" switcher in addition to the form.
- Server-side `HttpSession` cookie; session persists across page refresh and scopes which portfolio is visible (AUTH-02).
- Spring Security config is structured so an OAuth2/OIDC login can be added later without reworking authorization (the OAuth upgrade path is documented in README during Phase 3 / Polish). No OAuth implemented now.

### Seed Data Strategy
- Price data is **synthetic but realistic and reproducible** — no external/network dependency (demo-first). Generate ~2 years (~504 trading days) of daily OHLCV for ~15 securities using correlated geometric Brownian motion: a shared market factor plus per-security idiosyncratic drift/vol and beta, with a fixed RNG seed for reproducible screenshots.
- Universe (~15 large-caps across sectors, sector-tagged): AAPL, MSFT, NVDA, AMZN, GOOGL (Tech); JPM, BAC (Financials); XOM, CVX (Energy); JNJ, PFE (Healthcare); PG, KO, WMT (Consumer); TSLA (Auto). Adjust if needed.
- Benchmark: synthetic S&P 500 proxy = the market-factor series, stored as a pseudo-security/benchmark series.
- Fama-French: seeded daily Mkt-RF, SMB, HML factor-return series (synthetic but plausible, consistent with the generated price returns) sufficient for OLS attribution in Phase 4.
- Each demo persona gets a distinct portfolio (holdings + transaction history) drawn from the universe, matching its style (growth/income/balanced).
- Seeding mechanism: Flyway migrations own the schema (tables, indexes, `CREATE EXTENSION IF NOT EXISTS vector`); the large generated series + portfolios are populated by an idempotent Spring `ApplicationRunner`/`CommandLineRunner` seeder that no-ops if data already exists (programmatic generation is cleaner than giant SQL).

### Database & Vector Store
- Postgres 16 via the `pgvector/pgvector:pg16` Docker image; healthcheck in compose so the backend waits for DB readiness (`condition: service_healthy`).
- Flyway creates the relational schema and the pgvector extension. The `vector_store` table embedding dimension is locked now at **1536** (OpenAI `text-embedding-3-small`), even though embeddings are populated later in Phase 7 — switching dimensions later requires a drop/re-embed.
- `spring.ai.vectorstore.pgvector.initialize-schema=true` set explicitly; HNSW index, cosine distance.
- DB name/user `quantlens`; dev credentials via compose env vars only (never committed as real secrets).

### Dev MCP Tooling (DEVX-01)
- Add a project `.mcp.json` (and/or document `claude mcp add` commands in README) configuring: context7 MCP (`npx -y @upstash/context7-mcp@latest`) for current Spring AI / Hipparchus / finmath docs, and a Postgres MCP pointed at the local dev DB for schema introspection.
- **Postgres MCP package:** Do NOT use `@modelcontextprotocol/server-postgres` — it was deprecated/archived (July 2025) with a SQL-injection CVE. Use `@henkey/postgres-mcp-server` (flag for human-verify before first install) or a user-scoped `claude mcp add` fallback that keeps credentials out of version control.

### Claude's Discretion
- Exact table/column names, migration file organization, the precise GBM parameters per security, frontend scaffold tool (Vite + Vue 3 + TS), and Dockerfile layering are at Claude's discretion within the above constraints.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Greenfield repository — only `.planning/`, `CLAUDE.md`, and `README` placeholder exist. No application code yet.

### Established Patterns
- None yet. This phase establishes the foundational patterns (Modulith packages, Flyway migrations, BigDecimal money, idempotent seeding) that later phases follow.

### Integration Points
- Root `docker-compose.yml` is the single entry point. Backend exposes a health endpoint; frontend dev server proxies API calls to the backend. pgvector schema is the integration point for Phase 7 RAG.

### Build Environment (verified — see CLAUDE.md Conventions)
- JDK 21 (Temurin) at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`; set `JAVA_HOME` before host `./mvnw` builds. No standalone mvn/gradle — use the wrapper. Node v22.18 / npm 11.12. Docker 28 + Compose v2.34.
</code_context>

<specifics>
## Specific Ideas

- Reproducibility is a hard requirement: the same seed must produce the same charts so README screenshots are stable.
- The app MUST run end-to-end from `docker compose up` with no API keys (full demo mode) — this is a Definition-of-Done gate.
- Keep Phase 1 backend dependencies minimal (web, security, jpa/jdbc, flyway, postgres, validation); add Spring AI / quant libs only when their phases arrive, but pin the Spring AI BOM now.
</specifics>

<deferred>
## Deferred Ideas

- Importing real historical market data (kept synthetic for v1 reproducibility/zero-dependency).
- Real OAuth (Google/GitHub) implementation — documented as upgrade path only.
- Expanding the securities universe or adding asset classes beyond equities.
</deferred>
