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

## Architecture

```
docker compose up
      │
      ▼
db (pgvector/pgvector:pg16, named volume, healthcheck)
      │ service_healthy
      ▼
backend (eclipse-temurin:21, Spring Boot 3.5.13)
      │ Flyway migrations → ApplicationRunner seeder → Spring Security
      │ depends_on: backend started
      ▼
frontend (node:22 build → nginx:alpine)
      Vite SPA, Pinia + Axios (withCredentials, XSRF-TOKEN interceptor)
```

**Stack:** Java 21 + Spring Boot 3.5.13 + Spring AI 1.1.6 (BOM-pinned, no starters yet) + Spring Modulith + Flyway + pgvector/Postgres 16 + Vue 3 (Composition API) + Vite + Pinia + Axios + ECharts (charts deferred to Phase 3)

---

## Screenshots

The dashboard ships **screenshot-ready in demo mode** (no keys, no setup) and turns genuinely live when you paste your own LLM key into the in-app popup. The images below are captured in **live mode** — the AI panels show real LLM output, not the seeded fixtures — via the BYO-key flow described in the Capture Guide.

> Capturing the live-mode images is a manual step (it needs your own Anthropic or OpenAI key). Drop the PNGs into `docs/screenshots/` using the filenames below and they render here.

| View | Image | What it shows |
|------|-------|---------------|
| Dashboard (all AI panels) | `docs/screenshots/dashboard.png` | P&L, allocation, risk scorecard, the "explain this position" panel, and AI daily commentary in one view |
| Stochastic fan chart | `docs/screenshots/fan-chart.png` | Monte Carlo "potential futures" with the GBM / Merton / Heston / Bootstrap model selector active |
| RAG Q&A | `docs/screenshots/rag-qa.png` | Natural-language question answered over the 10-K corpus with inline citations |
| Structured-output chart | `docs/screenshots/structured-output.png` | The LLM's typed `StructuredChartDto` rendered directly as a chart (same component in demo + live) |
| BYO-key popup | `docs/screenshots/byo-key-popup.png` | The API-key popup that flips the mode badge from DEMO to LIVE (key is session-only, never persisted) |

### Capture Guide

1. `docker compose up` and open the app at http://localhost:5173
2. Log in as **alice** / `demo1234`
3. Open the **BYO-key popup** (the "Use your own key" / mode badge control)
4. Paste a real **Anthropic** or **OpenAI** API key — it is held in your session only and is never persisted or logged
5. The mode badge flips **DEMO → LIVE**; the AI panels now call the real LLM
6. Screenshot each AI panel; cycle the fan-chart **model selector** (GBM → Merton → Heston → Bootstrap) for the fan-chart image
7. Save the PNGs into `docs/screenshots/` using the filenames in the table above

---

## API & Architecture Docs

- **OpenAPI spec:** http://localhost:8080/v3/api-docs (raw JSON — all portfolio, analytics, and AI endpoints; the BYO-key intake endpoint is intentionally excluded so no key surface is published)
- **Swagger UI:** http://localhost:8080/swagger-ui.html (interactive API explorer)
- **Module diagram (Spring Modulith):** regenerate with `.\mvnw.cmd test -pl backend -Dtest=QuantLensModulithTest` (JAVA_HOME=Temurin 21) → `backend/target/spring-modulith-docs/components.puml`. The same test's `applicationModulesShouldBeValid()` enforces the module-boundary graph as a living contract on every build.
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

## Product MCP Server (MCP-01 / MCP-02)

QuantLens also ships its **own** MCP server — the portfolio analytics are exposed as Model
Context Protocol tools so any MCP client (Claude Code, Claude Desktop, or your own agent) can
query the live, computed portfolio. This is the product surface, distinct from the *dev* MCP
servers above: it runs inside the Spring Boot app itself, over Streamable HTTP at `/mcp`.

> The tools are a thin protocol adapter over the same golden-tested `PortfolioService` and
> `RiskCalculator` used by the REST API and the Vue UI — **no math is recomputed**. Every tool
> resolves the portfolio from the authenticated principal (never from a tool parameter), so one
> credential only ever sees its own portfolio.

### Transport & auth

| Property | Value |
|----------|-------|
| Endpoint | `http://localhost:8080/mcp` (Streamable HTTP, embedded in the backend on port 8080) |
| Protocol | Spring AI MCP server (`spring-ai-starter-mcp-server-webmvc`, BOM 1.1.6), SYNC |
| Auth | **HTTP Basic** — `/mcp` requires authentication before any tool is reachable (unauthenticated POST → `401`) |
| Demo credential | `alice` / `demo1234` (the seeded login shown on the sign-in screen) |

The endpoint is exempt from CSRF (a machine client cannot replay the `XSRF-TOKEN` cookie) — this
is **not** a relaxation, because `/mcp` still requires HTTP Basic auth. Error responses are
sanitized: a tool failure returns a static safe message, never a Java stack trace or internal detail.

### Tool catalog

| Tool | Parameters | Returns |
|------|-----------|---------|
| `get_portfolio_summary` | _(none)_ | Total market value, cost basis, unrealized P&L (abs + %), daily change (abs + %), and sector allocation weights |
| `get_risk_metrics` | _(none)_ | Annualized Sharpe ratio, annualized volatility, max drawdown, beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day) |
| `get_position_detail` | `ticker` (e.g. `AAPL`) | A single holding: ticker, name, sector, quantity, avg cost basis, current price, market value, portfolio weight, unrealized P&L (abs + %) |

### Connect from Claude Code

The committed `.mcp.json` already includes a `quantlens` entry pointing at the running server:

```jsonc
"quantlens": {
  "type": "http",
  "url": "http://localhost:8080/mcp",
  "headers": { "Authorization": "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}" }
}
```

The base64 token `YWxpY2U6ZGVtbzEyMzQ=` decodes to `alice:demo1234` (the demo credential — safe to
commit). Override it with a different user via the `QUANTLENS_MCP_AUTH` env var
(`echo -n 'bob:demo1234' | base64`).

```bash
# 1. Start the stack so /mcp is live
docker compose up

# 2. From inside this project directory, Claude Code auto-loads .mcp.json — verify:
claude mcp get quantlens
# In a Claude Code session, /mcp lists the three tools; then ask, e.g.:
#   "Use get_risk_metrics to show my portfolio's Sharpe and 95% VaR."
```

Any MCP client can connect the same way: point it at `http://localhost:8080/mcp` with an
`Authorization: Basic <base64(user:pass)>` header and call `tools/list` then `tools/call`.

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
