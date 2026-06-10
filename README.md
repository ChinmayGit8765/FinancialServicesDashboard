# QuantLens — AI Portfolio & Market Intelligence Dashboard

An AI-augmented portfolio and market intelligence dashboard fusing a quantitative-finance Spring Boot backend with a Vue 3 SPA. Features real portfolio analytics (P&L, risk metrics, Sharpe, VaR, Monte Carlo), Spring AI narration, and a one-click demo mode — no API keys needed.

---

## Quick Start

```bash
docker compose up
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- PostgreSQL: localhost:5432 (user/db: `quantlens`, password: `quantlens_dev`)

No API keys, no manual setup. On first start the backend seeds demo users, ~15 securities with ~2 years of synthetic daily OHLCV, benchmark data, and Fama-French factor series.

---

## Demo Personas

All personas share the password **`demo1234`**. Use the one-click login buttons on the login page, or log in with the form.

| Persona | Username | Style | Description |
|---------|----------|-------|-------------|
| Growth | `alice` | Growth | Tech-heavy, high-beta portfolio |
| Income | `bob` | Income | Dividend-focused, defensive holdings |
| Balanced | `charlie` | Balanced | Diversified across sectors |

Sessions persist across page refresh — the JSESSIONID cookie is scoped to the logged-in persona's portfolio.

---

## Cold-Start Verification

After `docker compose up`, verify the stack is healthy:

### 1. Container health

```bash
docker compose ps
```

Expected: `db` (healthy), `backend` (running), `frontend` (running).

### 2. Schema and seed counts

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT count(*) AS securities  FROM securities;
  SELECT count(*) AS ohlcv_bars  FROM ohlcv_bars;
  SELECT count(*) AS users       FROM app_users;
  SELECT count(*) AS factors     FROM factor_returns;
"
```

Expected: securities >= 15, ohlcv_bars ~7560, users = 3, factors ~504.

### 3. pgvector schema

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT column_name, udt_name
  FROM information_schema.columns
  WHERE table_name='vector_store' AND column_name='embedding';
"
```

Expected: `udt_name = vector`.

### 4. Login flow (curl)

```bash
# Login as alice
curl -s -c cookies.txt \
  -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=alice&password=demo1234"
# Expected: {"authenticated":true,"username":"alice"}

# Fetch session user
curl -s -b cookies.txt http://localhost:8080/api/auth/me
# Expected: {"username":"alice","persona":"Growth","portfolioId":1}
```

### 5. UI walkthrough

1. Open http://localhost:5173
2. Click **"Log in as Alice"** — lands on the dashboard placeholder showing Alice's persona and portfolio ID
3. Refresh the page — session persists (no re-login required)
4. Log out and repeat as Bob or Charlie — different `portfolioId` each time

---

## Stochastic Forecasting Models

The Monte Carlo fan chart supports four models: Geometric Brownian Motion (GBM), Merton Jump-Diffusion, Heston Stochastic Volatility, and Historical Block Bootstrap.

See [Model documentation](docs/MODELS.md) for rationale, key assumptions, parameters used, and known limitations of each model — written for a non-specialist audience.

---

## Tech Stack

QuantLens pairs a quantitative-finance backend with a modern Spring AI layer, fronted by a Vue 3 SPA. Versions below are the ones actually pinned in `backend/pom.xml` and `frontend/package.json`.

### Backend & platform

| Tech | Version | Why |
|------|---------|-----|
| Java | 21 LTS | Records, sealed types, virtual threads; the Spring Boot 3.5 baseline, and the compile target every quant JAR runs on cleanly |
| Spring Boot | 3.5.13 | Latest stable 3.5.x; pairs with the Spring AI 1.1.x line. Web, Data JPA, Security, Validation, Actuator starters |
| Spring Modulith | 1.4.11 | Declares module boundaries in `package-info.java` and verifies them on every build (see Architecture) |

### AI layer — Spring AI 1.1.6

Pinned via the `spring-ai-bom`, multi-provider (Anthropic + OpenAI starters both on the classpath):

- **ChatClient + advisors** — fluent per-request prompts; a custom `DemoModeAdvisor` short-circuits the advisor chain in seeded mode, `ChatMemorySessionListener` scopes conversation memory.
- **Tool calling** — `StockQuoteToolService` exposes a live-quote tool backed by a Finnhub client.
- **Structured output** — `entity(...)` maps model responses into records (`StructuredInsightRecord`) that drive the charts directly.
- **RAG** — `spring-ai-starter-vector-store-pgvector` + `spring-ai-advisors-vector-store` (`QuestionAnswerAdvisor`) for retrieval over filings/earnings, with a deterministic seed-mode embedding model so RAG works with no key.
- **MCP server** — `spring-ai-starter-mcp-server-webmvc` (Streamable-HTTP, same port 8080) re-exposes portfolio/analytics as MCP tools (`PortfolioMcpTools`).

### Quant / math

| Tech | Version | Role |
|------|---------|------|
| finmath-lib | 6.1.7 | Monte Carlo SDE engine — GBM, Merton jump-diffusion, Heston stochastic-vol paths for the stochastic forecasts |
| Hipparchus | 4.0.3 (core + stat) | Statistics & linear algebra — OLS (Fama-French attribution), Pearson/Spearman correlation matrices, VaR percentiles, Engle-Granger / ADF cointegration, seedable MersenneTwister RNG |

Math is library-backed rather than hand-rolled: correctness on the Heston discretisation and the ADF critical-value table is exactly where bugs hide.

### Data

- **PostgreSQL 16 + pgvector** — single store for relational data *and* RAG embeddings (HNSW / cosine), no separate vector DB.
- **Flyway** (`flyway-core` + `flyway-database-postgresql`) — owns all DDL, including the pgvector schema (`initialize-schema=false` on the vector store).

### Frontend

| Tech | Version | Role |
|------|---------|------|
| Vue 3 | 3.5.34 | SPA, Composition API / `<script setup>` |
| Vite | 8.0.12 | Dev server & build (`vue-tsc` type-checked) |
| TypeScript | 6.0.x | Strict typing across the app |
| Pinia | 3.0.4 | State management |
| ECharts / vue-echarts | 6.1.0 / 8.0.1 | Native candlestick, heatmap, and fan-chart rendering on Canvas |
| Tailwind CSS | 4.3.0 | Styling, via `@tailwindcss/vite` (v4 zero-config) |
| Vue Router | 4.6.4 | Routing |
| axios | 1.17.0 | HTTP client to the Spring Boot API |

### Tooling & infra

- **springdoc-openapi** 2.8.17 — OpenAPI 3 spec + Swagger UI (`OpenApiConfig`).
- **Testcontainers** (postgresql + junit-jupiter) — backend integration tests against a real Postgres.
- **Vitest** 4.1.8 + `@vue/test-utils` + jsdom — frontend unit/component tests.
- **Docker / docker-compose** — full stack (Postgres+pgvector, backend, frontend) via one `docker compose up`.

### Architecture

The backend is a **Spring Modulith** application; each top-level package is a module whose allowed cross-module dependencies are declared in `package-info.java`, forming a living contract:

- `ai` → `portfolio::domain`, `marketdata::domain`, `seed`
- `analytics` → `portfolio::domain`, `marketdata::domain`
- `mcp` → `portfolio` (service/api/domain) + `analytics` (service/api) — a thin protocol adapter that recomputes nothing
- `security` → `portfolio::domain`; `config` is standalone (no QuantLens dependencies)

`QuantLensModulithTest` enforces this graph on every build via `ApplicationModules.verify()` (no illegal references, no cycles) and renders the module dependency graph to a PlantUML component diagram. The **demo ↔ live AI seam** is a first-class boundary: with no key the `DemoModeAdvisor` serves authored seed content (and a deterministic seed embedding model powers RAG), so the dashboard always runs and demos; paste a session-scoped key (`LlmKeySessionHolder`, never persisted or logged) and the same endpoints go live against Anthropic or OpenAI.

The entire stack comes up with a single `docker compose up`:

```
docker compose up
      │
      ▼
db (pgvector/pgvector:pg16, named volume, healthcheck)
      │ service_healthy
      ▼
backend (eclipse-temurin:21, Spring Boot 3.5.13)
      │ Flyway migrations → ApplicationRunner seeder → Spring Security → /mcp + /v3/api-docs
      │ depends_on: backend healthy
      ▼
frontend (node:22 build → nginx:alpine)
      Vue 3 SPA (Tailwind), Pinia + Axios (withCredentials, XSRF-TOKEN interceptor)
```

---

## Screenshots

Everything below is captured in **DEMO mode** — no API key, no network calls, zero setup — and turns fully live the moment you paste your own LLM key.

### AI Daily Commentary

![QuantLens AI Daily Commentary hero card with seeded narrative and bullet insights](docs/screenshots/ai-commentary.png)

The hero card: a seeded LLM narrative plus bullet insights, rendered with no key — the same surface a live Spring AI provider drives once a key is supplied.

### Portfolio Performance

![Portfolio Value P&L over two years beside Portfolio vs S&P 500 rebased](docs/screenshots/portfolio-charts.png)

Portfolio Value (P&L over ~2 years) and Portfolio vs S&P 500 (rebased to a common base) side by side — your equity curve against the benchmark.

### Correlation & Risk

![Correlation heatmap in azure shading alongside portfolio risk metrics](docs/screenshots/correlation-matrix.png)

The correlation heatmap (azure = more correlated) next to the risk metrics panel — Sharpe, VaR, beta, and volatility computed from real return series.

### Holdings & Transactions

![Holdings table with value, weight, cost and P&L beside transaction history with running cost basis](docs/screenshots/holdings-transactions.png)

The Holdings table (value / weight / cost / P&L — click any row for an AI explanation) and Transaction history with a running cost basis.

### Potential Futures

![Monte Carlo fan chart with GBM, Jump-Diffusion, Heston and Bootstrap model selector](docs/screenshots/fan-chart.png)

"Potential Futures": a Monte Carlo fan chart with a model selector for GBM / Jump-Diffusion / Heston / Bootstrap — stochastic forecasts of where the portfolio could land.

> Capture: these are taken at `localhost:5173` after `docker compose up`.
> See the API & Architecture Docs section to reproduce.

---

## API & Architecture Docs

- **OpenAPI spec:** http://localhost:8080/v3/api-docs (raw JSON — all portfolio, analytics, and AI endpoints; the BYO-key intake endpoint is intentionally excluded so no key surface is published)
- **Swagger UI:** http://localhost:8080/swagger-ui.html (interactive API explorer)
- **Module diagram (Spring Modulith):** regenerate (JAVA_HOME=Temurin 21) → `backend/target/spring-modulith-docs/components.puml`. The same test's `applicationModulesShouldBeValid()` enforces the module-boundary graph as a living contract on every build.
  - Windows (PowerShell): `.\mvnw.cmd test -pl backend -Dtest=QuantLensModulithTest`
  - Linux/macOS: `./mvnw test -pl backend -Dtest=QuantLensModulithTest`
- **Stochastic model rationale:** [docs/MODELS.md](docs/MODELS.md) — why each Monte Carlo model, key assumptions, parameters, and limitations
- **RAG design:** [docs/RAG_DESIGN.md](docs/RAG_DESIGN.md) — the zero-key deterministic embedding + retrieval approach
- **Product MCP server + dev MCP servers:** see the MCP sections below
- **Auth upgrade path:** see [OAuth Upgrade Path](#oauth-upgrade-path) for the form-login → OAuth2/OIDC seam

---

## Dev MCP Servers (DEVX-01)

The repo includes `.mcp.json` configuring two Claude Code MCP servers for development:

### context7 — current framework docs in Claude context

```json
"context7": {
  "command": "npx",
  "args": ["-y", "@upstash/context7-mcp@latest"]
}
```

Provides current Spring AI, Hipparchus, finmath-lib, and Vue/Vite documentation directly in Claude Code's context. No configuration required.

### project-db — query the local dev database from Claude Code

```json
"project-db": {
  "command": "npx",
  "args": ["-y", "@henkey/postgres-mcp-server"],
  "env": {
    "POSTGRES_CONNECTION_STRING": "postgresql://quantlens:quantlens_dev@localhost:5432/quantlens"
  }
}
```

**Note:** `@henkey/postgres-mcp-server` is the recommended replacement for `@modelcontextprotocol/server-postgres`, which was **deprecated and archived in July 2025** due to a SQL-injection vulnerability. Do NOT use the deprecated package.

**Alternative (user-scoped, keeps credentials out of version control):**

```bash
# Run once per developer — stored in ~/.claude.json, not committed to the repo
claude mcp add --transport stdio project-db \
  --env POSTGRES_CONNECTION_STRING=postgresql://quantlens:quantlens_dev@localhost:5432/quantlens \
  -- npx -y @henkey/postgres-mcp-server
```

Use the user-scoped approach if you prefer not to commit credentials to version control (the `quantlens_dev` password in `.mcp.json` is a dev-only non-secret, but the fallback is available).

After configuring, verify with `claude mcp list` inside this project directory.

---

## Product MCP Server

QuantLens exposes its portfolio analytics as a **Model Context Protocol (MCP) server** — the freshest 2026 resume signal — over Streamable HTTP at `/mcp`, embedded directly in the Spring Boot app (no sidecar process). The endpoint is **HTTP-Basic gated** and **IDOR-safe**: the target portfolio is derived solely from the authenticated principal in the `SecurityContextHolder`, never from a tool parameter. Every tool body is wrapped in try/catch so the MCP client receives a **static safe message** while the full stack trace stays in the server log — no exception classes, messages, or stack traces ever cross the wire.

### Tool catalog

| Tool | Params | Returns |
|------|--------|---------|
| `get_portfolio_summary` | _(none — principal-derived)_ | Total market value, total cost basis, total unrealized P&L (absolute + %), daily change (absolute + %), and sector allocation weights. |
| `get_risk_metrics` | _(none — principal-derived)_ | Annualized Sharpe ratio, annualized volatility, max drawdown, beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day). |
| `get_position_detail` | `ticker` (string, required — e.g. `AAPL`, `BRK.B`) | A single holding: ticker, name, sector, quantity, avg cost basis, current price, market value, portfolio weight, and unrealized P&L (absolute + %). The ticker identifies a *security*, not a user — it is sanitized (uppercased, stripped to `[A-Z0-9.]`, capped at 10 chars) before the lookup. |

Each tool is a thin protocol adapter: it delegates all computation to `PortfolioService` / `RiskCalculator` and recomputes nothing.

```java
@McpTool(
        name = "get_risk_metrics",
        description = "Returns risk metrics for the authenticated user's portfolio: " +
                      "annualized Sharpe ratio, annualized volatility, max drawdown, " +
                      "beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day)."
)
public McpSchema.CallToolResult getRiskMetrics() {
    try {
        Long portfolioId = resolvePortfolioId();                 // principal-derived, never a param
        RiskScorecardDto scorecard = riskCalculator.computeRiskScorecard(portfolioId);

        // Extract VaR amounts by method name — no recompute
        BigDecimalRef histVar = new BigDecimalRef();
        BigDecimalRef paramVar = new BigDecimalRef();
        for (VarResultDto var : scorecard.var()) {
            if ("HISTORICAL".equals(var.method())) histVar.value = var.amount();
            else if ("PARAMETRIC".equals(var.method())) paramVar.value = var.amount();
        }
        // never serialize null VaR to the client/LLM — surface a static error instead
        if (histVar.value == null || paramVar.value == null) { /* … log + isError result … */ }

        RiskMetricsResult result = new RiskMetricsResult(
                scorecard.sharpeRatio(), scorecard.annualizedVolatility(),
                scorecard.maxDrawdown(), scorecard.beta(),
                histVar.value, paramVar.value);
        String json = objectMapper.writeValueAsString(result);
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(json)))
                .isError(false)
                .build();
    } catch (ResponseStatusException e) {
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Portfolio not found for authenticated user")))
                .isError(true)
                .build();
    } catch (Exception e) {
        log.error("get_risk_metrics failed (not forwarded to client)", e);   // full trace stays server-side
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Risk metrics unavailable")))
                .isError(true)
                .build();
    }
}

// Typed result — a plain record (no Spring/JPA annotations) for reliable JSON-schema generation
public record RiskMetricsResult(
        double sharpeRatio,
        double annualizedVolatility,
        double maxDrawdown,
        double beta,
        BigDecimal historicalVar95,   // positive loss amount, method=="HISTORICAL"
        BigDecimal parametricVar95    // positive loss amount, method=="PARAMETRIC"
) {}
```

The principal resolution is defence-in-depth behind the `/mcp` auth gate: `resolvePortfolioId()` rejects `null`, unauthenticated, and `AnonymousAuthenticationToken` principals (whose `isAuthenticated()` returns `true` by design), then maps the username to a portfolio — both "user not found" and "no portfolio" return `401`, so the LLM cannot probe for other users' portfolios.

### Connect from Claude Code

The repo ships a committed `quantlens` server entry in `.mcp.json`:

```jsonc
{
  "mcpServers": {
    "quantlens": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        // base64("alice:demo1234") — overridable via the QUANTLENS_MCP_AUTH env var
        "Authorization": "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}"
      }
    }
  }
}
```

1. `docker compose up` — brings up the Spring Boot app with the embedded MCP server on `:8080`.
2. `claude mcp get quantlens` — confirms Claude Code picked up the committed entry.
3. In a Claude Code session, run `/mcp` — it lists the three QuantLens tools (`get_portfolio_summary`, `get_risk_metrics`, `get_position_detail`).

The demo credential is `alice:demo1234`, supplied as base64 (`YWxpY2U6ZGVtbzEyMzQ=`) in the `Authorization` header. Override it for any user/password without editing the file by exporting `QUANTLENS_MCP_AUTH` (e.g. `export QUANTLENS_MCP_AUTH="Basic $(printf 'bob:secret' | base64)"`).

### Try it by hand

Drive the MCP `initialize` handshake directly with `curl` — Basic auth plus the dual `Accept` header that Streamable HTTP requires:

```bash
curl -s http://localhost:8080/mcp \
  -u alice:demo1234 \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{
        "jsonrpc": "2.0",
        "id": 1,
        "method": "initialize",
        "params": {
          "protocolVersion": "2024-11-05",
          "capabilities": {},
          "clientInfo": { "name": "curl", "version": "1.0" }
        }
      }'
```

The real server response:

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"quantlens-mcp","version":"1.0.0"}}}
```

Notes:
- **`GET /mcp` without credentials returns `401`** — the HTTP-Basic auth gate in `SecurityConfig` (`.anyRequest().authenticated()` + `.httpBasic(...)`). The `/mcp` POST transport is also explicitly exempted from CSRF, which is safe because the endpoint stays Basic-authenticated rather than session-based.
- **`/mcp` is a machine endpoint** (JSON-RPC + SSE), not a browsable page. Opening it in a browser yields `Invalid Accept header. Expected TEXT_EVENT_STREAM` — that error is expected; the endpoint is meant for MCP clients, not eyeballs.

---

## OAuth Upgrade Path

The current auth uses Spring Security form login with seeded BCrypt users — intentionally simple for the demo. The `SecurityConfig` is structured so an OAuth2/OIDC login can be added later without reworking the authorization rules:

1. Add `spring-boot-starter-oauth2-client` to `pom.xml`
2. Configure `spring.security.oauth2.client.*` properties (Google, GitHub, or any OIDC provider)
3. Add `.oauth2Login(oauth2 -> oauth2.successHandler(...))` to the existing `SecurityFilterChain` — the JSON success handler pattern is the same
4. Provision real users by mapping the OAuth `sub` claim to the `external_id` column in `app_users`
5. Remove the seeded BCrypt users from production configuration

No architectural changes to the authorization rules (route protection, session scoping, portfolio isolation) are required.

**AI feature seam — no code changes needed (AUTH-03):** The Spring AI layer (Phases 6–8) stores the user-supplied LLM key in `LlmKeySessionHolder`, which reads the key from the HTTP session. Because `LlmKeySessionHolder` and all downstream Spring AI features depend on the _HTTP session_, not on the login mechanism, replacing form login with OAuth2 login leaves the AI key-session seam and portfolio-scoping seam entirely intact. No changes to `LlmKeySessionHolder` or any AI component are required when upgrading authentication.

---

## Production Notes

- The `POSTGRES_PASSWORD: quantlens_dev` in `docker-compose.yml` is a dev-only, non-secret placeholder. For production, inject real credentials via environment variables or Docker secrets — never commit production passwords.
- AI provider keys (Anthropic, OpenAI) are **never** stored server-side. The BYO-key popup (Phase 6) stores keys in the user's browser session only.
- The `SPRING_PROFILES_ACTIVE: demo` profile activates the seed runner and demo AI responses. Remove this env var in production.

---

## Development

```bash
# Backend (host — requires JDK 21)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Frontend (Vite dev server with /api proxy to localhost:8080)
cd frontend && npm run dev
# → http://localhost:5173 (hot-reload)
```

For the full containerized stack: `docker compose up`.
