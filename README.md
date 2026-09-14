<p align="center"><img src="docs/assets/hero.svg" alt="QuantLens — AI portfolio & market intelligence with real quant math, narrated by Spring AI" width="100%"></p>

<p align="center">
  <b>A portfolio dashboard that does the quant math for real — Monte Carlo SDEs, VaR, cointegration — and lets an LLM narrate it. <code>docker compose up</code>, no API key, and it is already full of data.</b>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Java-21_LTS-f89820?style=flat-square&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-3.5.13-6DB33F?style=flat-square&logo=springboot&logoColor=white" alt="Spring Boot 3.5.13">
  <img src="https://img.shields.io/badge/Spring_AI-1.1.6-6DB33F?style=flat-square&logo=spring&logoColor=white" alt="Spring AI 1.1.6">
  <img src="https://img.shields.io/badge/Vue-3.5-42b883?style=flat-square&logo=vuedotjs&logoColor=white" alt="Vue 3.5">
  <img src="https://img.shields.io/badge/Postgres_16-pgvector-2563eb?style=flat-square&logo=postgresql&logoColor=white" alt="PostgreSQL 16 + pgvector">
  <img src="https://img.shields.io/badge/MCP-server_embedded-000000?style=flat-square" alt="MCP server embedded">
  <img src="https://img.shields.io/github/stars/ChinmayGit8765/FinancialServicesDashboard?style=flat-square&color=2563eb" alt="Stars">
</p>

---

## ✨ What it does

- **Computes real portfolio analytics**, not mocked numbers: P&L and running cost basis, sector allocation, Sharpe, annualised volatility, max drawdown, beta vs SPX500, historical **and** parametric VaR (95%, 1-day), Pearson/Spearman correlation matrices, and Fama-French factor attribution by OLS.
- **Simulates potential futures** with four stochastic models over a 252-day horizon and 5,000 paths — GBM, Merton jump-diffusion, Heston stochastic vol, and historical block bootstrap — rendered as a percentile fan chart (p5/p25/p50/p75/p95).
- **Scans for statistically cointegrated pairs** using the Engle-Granger two-step with an ADF unit-root test (`p < 0.05`), reporting hedge ratio and spread Z-score — a pairs-trading candidate list, not a correlation shortcut.
- **Narrates all of it with Spring AI**: daily commentary, per-position explanations, structured output that drives charts directly, tool calling for live quotes, and RAG Q&A grounded in SEC 10-K excerpts with citations.
- **Runs with zero keys.** A `DemoModeAdvisor` serves authored seed content and a deterministic in-process embedding model powers RAG, so every AI panel works offline. Paste your own Anthropic or OpenAI key into the session-only popup and the same endpoints go live.
- **Re-exposes itself as an MCP server** over Streamable HTTP on the same port — three portfolio tools, HTTP-Basic gated and IDOR-safe, so Claude Code (or any MCP client) can query the portfolio directly.

## 🎬 See it

Everything below is captured in **DEMO mode** — no API key, no network calls, zero setup.

<p align="center"><img src="docs/screenshots/demo-tour.gif" alt="One-click demo login into QuantLens, then a scroll through the live dashboard: KPI strip, AI commentary, P&L and benchmark charts, correlation heatmap, Monte Carlo fan chart, holdings and transactions" width="100%"></p>
<p align="center"><sub>One click on a demo persona → the whole dashboard, populated from seeded data. No key, no signup, no data import. Recorded straight through, panels rendering as they load.</sub></p>

<table><tr>
<td width="50%"><img src="docs/screenshots/demo-dashboard.png" alt="QuantLens dashboard header with market value, unrealized P&L, daily change, Sharpe ratio and volatility KPI cards above the AI daily commentary card"><br><sub><b>Dashboard home.</b> KPI strip (market value, unrealized P&L, daily change, Sharpe, annualised vol) over the AI commentary hero — and the panel that swaps seeded AI for your own key.</sub></td>
<td width="50%"><img src="docs/screenshots/demo-ai-chat.png" alt="Ask the Portfolio chat panel answering a question about 10-K risk factors with four citation chips"><br><sub><b>RAG Q&A, no key.</b> "What risks did the 10-K filings flag for my energy holdings?" — answered from the seeded filings corpus with <code>AAPL · NVDA · JPM · XOM</code> Risk-Factors citations.</sub></td>
</tr><tr>
<td width="50%"><img src="docs/screenshots/portfolio-charts.png" alt="Portfolio value P&L over two years beside portfolio vs S&P 500 rebased to a common base"><br><sub><b>Performance.</b> Portfolio value across ~2 years of daily bars, and the same series rebased against the S&P 500 — growth, not dollar size.</sub></td>
<td width="50%"><img src="docs/screenshots/correlation-matrix.png" alt="Correlation heatmap in azure shading with a hover tooltip showing COP versus BAC at 0.673"><br><sub><b>Correlation.</b> Every holding pair, Pearson on real return series, azure = more correlated. Hover any cell for the exact coefficient.</sub></td>
</tr><tr>
<td width="50%"><img src="docs/screenshots/fan-chart.png" alt="Monte Carlo fan chart with GBM, jump-diffusion, Heston and bootstrap model selector"><br><sub><b>Potential futures.</b> 5,000 simulated paths, 252 trading days, four models behind one selector — a stress-test surface, not a price target.</sub></td>
<td width="50%"><img src="docs/screenshots/cointegration-pairs.png" alt="Cointegration pairs table showing the detected XOM and COP mean-reverting pair"><br><sub><b>Cointegration.</b> The unmodified scanner finds <b>XOM / COP</b> in the seeded energy names — <code>p = 0.000</code>, hedge β <code>0.754</code>, spread Z <code>−0.59</code>, signal <code>NEUTRAL</code>.</sub></td>
</tr><tr>
<td width="50%"><img src="docs/screenshots/holdings-transactions.png" alt="Holdings table with value, weight, cost basis and P&L above transaction history with a running cost basis"><br><sub><b>Positions.</b> Value / weight / avg cost / P&L per holding — click a row for an AI explanation — over buy-sell history with a running cost basis.</sub></td>
<td width="50%"><img src="docs/screenshots/demo-login.png" alt="QuantLens login screen with one-click Growth, Income and Balanced demo personas and the demo password hint"><br><sub><b>Zero-key entry.</b> Three seeded personas — Growth, Income, Balanced — one click each, password shown on the page.</sub></td>
</tr></table>

<sub>Every capture is the real app running in demo mode against the seeded Postgres + Spring Boot backend — no mockups, no edited numbers. The correlation, attribution and cointegration panels are portfolio-specific: a diversified persona is what surfaces the XOM/COP pair.</sub>

## 🧠 How it works

```mermaid
flowchart LR
  SPA["Vue 3 SPA<br/>Pinia · ECharts · Tailwind"] -->|"/api/** · JSESSIONID"| API["Spring Boot 3.5<br/>Spring Modulith modules"]
  API --> QUANT["Quant engine<br/>finmath-lib SDEs · Hipparchus<br/>VaR · OLS · Engle-Granger ADF"]
  API --> AI["Spring AI 1.1<br/>ChatClient + advisors<br/>tool calling · structured output"]
  AI -->|"no key"| DEMO["DemoModeAdvisor<br/>authored seed content"]
  AI -->|"session key"| LLM["Anthropic / OpenAI"]
  AI --> RAG["QuestionAnswerAdvisor<br/>deterministic hashing embeddings"]
  AI --> FINN["Finnhub quote tool"]
  QUANT --> PG[("PostgreSQL 16<br/>+ pgvector HNSW")]
  RAG --> PG
  API --> MCP["Embedded MCP server<br/>Streamable HTTP /mcp"]
  MCP --> CLIENT["Claude Code<br/>and other MCP clients"]
```

One Spring Boot process, four seams worth knowing about:

1. **Module boundaries are enforced, not documented.** The backend is a Spring Modulith app: each top-level package declares its allowed dependencies in `package-info.java` (`ai` → `portfolio::domain`, `marketdata::domain`, `seed`; `analytics` → the two domains; `mcp` → `portfolio` + `analytics`; `security` → `portfolio::domain`; `config` standalone). `QuantLensModulithTest` runs `ApplicationModules.verify()` on every build — illegal reference or cycle, red build.
2. **The math is library-backed.** finmath-lib supplies the GBM / Merton / Heston Monte Carlo processes; Hipparchus supplies OLS, correlation, percentiles and the ADF primitives. Correctness on the Heston discretisation and the MacKinnon critical-value table is exactly where hand-rolled quant code goes wrong.
3. **The demo ↔ live AI seam is a first-class boundary.** With no key, `DemoModeAdvisor` short-circuits the advisor chain; with a key, `LlmKeySessionHolder` (HTTP-session scoped, never persisted, never logged) points the same `ChatClient` at Anthropic or OpenAI.
4. **The MCP server recomputes nothing.** It is a thin protocol adapter over `PortfolioService` / `RiskCalculator`, with the portfolio derived from the authenticated principal rather than a tool parameter.

### A RAG question, end to end

```mermaid
sequenceDiagram
  participant U as Browser
  participant C as AiController · ChatService
  participant A as QuestionAnswerAdvisor
  participant E as Hashing embedding model
  participant V as pgvector HNSW cosine
  participant M as ChatClient
  U->>C: POST /api/ai/chat "risks in the 10-K filings?"
  C->>A: prompt + conversation id
  A->>E: embed(question)
  E-->>A: 1536-dim L2-normalised vector
  A->>V: similarity search over seeded filing chunks
  V-->>A: top-k chunks + metadata
  A->>M: question + retrieved context
  M-->>C: grounded answer (DemoModeAdvisor, or live LLM with a session key)
  C-->>U: answer + 10-K citations
```

RAG works with no key because the embedding model does: `DeterministicHashingEmbeddingModel` is pure Java — MurmurHash3 bigram feature hashing projected into 1536 dimensions and L2-normalised, registered `@Primary` so Spring AI's pgvector auto-configuration picks it over the OpenAI model even with the OpenAI starter on the classpath. `RagSeedRunner` embeds the filings corpus at startup with that same model, so seed-time and query-time vectors live in the same space. Full rationale in [docs/RAG_DESIGN.md](docs/RAG_DESIGN.md).

<details>
<summary><b>Spring AI wiring · the exact starters and the container flow</b></summary>

Spring AI 1.1.6 is pinned via `spring-ai-bom`, with the **Anthropic and OpenAI starters both on the classpath** so provider choice is a session-time decision, not a build-time one:

- **ChatClient + advisors** — fluent per-request prompts. `DemoModeAdvisor` short-circuits the advisor chain in seeded mode; `ChatMemorySessionListener` scopes conversation memory to the HTTP session.
- **Tool calling** — `StockQuoteToolService` exposes a live-quote tool backed by `FinnhubQuoteClient`.
- **Structured output** — `.entity(...)` maps model responses into records (`StructuredInsightRecord`) that drive charts directly, with no hand-parsing step.
- **RAG** — `spring-ai-starter-vector-store-pgvector` + `spring-ai-advisors-vector-store` (`QuestionAnswerAdvisor`) over filings/earnings excerpts, with the deterministic seed-mode embedding model so retrieval works with no key.
- **MCP server** — `spring-ai-starter-mcp-server-webmvc` (Streamable HTTP, same port 8080) re-exposes portfolio and analytics as `@McpTool` methods in `PortfolioMcpTools`.

`docker compose up` brings the three services up in dependency order:

```
db        pgvector/pgvector:pg16 · named volume (NOT a bind mount — NTFS/WSL2 corrupts PG data)
          healthcheck: pg_isready, 20s start period for the pgvector init script
   │ service_healthy
backend   eclipse-temurin:21 · Spring Boot 3.5.13 · SPRING_PROFILES_ACTIVE=demo
          Flyway migrations → ApplicationRunner seeders → Spring Security → /mcp + /v3/api-docs
   │ service_healthy  (waits on /actuator/health — no 502s while seeding)
frontend  node:22 build → nginx:alpine, which proxies /api/* to backend:8080
          Vue 3 SPA: Pinia stores, Axios with withCredentials + an XSRF-TOKEN interceptor
```

No AI keys appear anywhere in `docker-compose.yml` — by design.

</details>

## 🚀 Quick start

```bash
docker compose up
```

- Frontend → <http://localhost:5173>
- Backend API → <http://localhost:8080>
- PostgreSQL → `localhost:5432` (user/db `quantlens`, password `quantlens_dev`)

No API keys, no manual setup. On first start the backend runs Flyway, then seeds three demo users, ~15 securities with ~2 years of synthetic daily OHLCV (~7,560 bars), benchmark data, Fama-French factor series (~504 rows), and the RAG filings corpus.

All three personas share the password **`demo1234`** — use the one-click buttons on the login page:

| Persona | Username | Portfolio |
|---------|----------|-----------|
| Growth | `alice` | Tech-heavy, high-beta |
| Income | `bob` | Dividend-focused, defensive |
| Balanced | `charlie` | Diversified across sectors |

Sessions survive a page refresh — the `JSESSIONID` cookie is scoped to that persona's portfolio.

<details>
<summary><b>Run it on the host instead (JDK 21 + Node 22)</b></summary>

```bash
# Backend — JAVA_HOME must point at a JDK 21 (Temurin 21 is what this was built on);
# a shell whose `java` is an older JDK will fail the build, so set it explicitly
export JAVA_HOME=/path/to/jdk-21
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Frontend — Vite dev server, proxies /api to localhost:8080
cd frontend && npm install && npm run dev   # → http://localhost:5173, hot reload
```

Tests:

```bash
cd backend  && ./mvnw test    # JUnit 5 + Testcontainers (real Postgres)
cd frontend && npm test       # Vitest + @vue/test-utils + jsdom
```

</details>

<details>
<summary><b>Cold-start verification — is the stack actually healthy?</b></summary>

**1. Containers**

```bash
docker compose ps
```

Expected: `db` (healthy), `backend` (running), `frontend` (running).

**2. Schema and seed counts**

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT count(*) AS securities  FROM securities;
  SELECT count(*) AS ohlcv_bars  FROM ohlcv_bars;
  SELECT count(*) AS users       FROM app_users;
  SELECT count(*) AS factors     FROM factor_returns;
"
```

Expected: securities >= 15, ohlcv_bars ~7560, users = 3, factors ~504.

**3. pgvector schema**

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT column_name, udt_name
  FROM information_schema.columns
  WHERE table_name='vector_store' AND column_name='embedding';
"
```

Expected: `udt_name = vector`.

**4. Login flow**

```bash
curl -s -c cookies.txt \
  -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=alice&password=demo1234"
# → {"authenticated":true,"username":"alice"}

curl -s -b cookies.txt http://localhost:8080/api/auth/me
# → {"username":"alice","persona":"Growth","portfolioId":1}
```

**5. UI walkthrough**

1. Open <http://localhost:5173>
2. Click **Growth / @alice** — lands on the dashboard for Alice's portfolio
3. Refresh — the session persists, no re-login
4. Log out and repeat as Bob or Charlie — a different `portfolioId` each time

</details>

<details>
<summary><b>HTTP API — every endpoint</b></summary>

Session-cookie authenticated; the portfolio is always derived from the principal, never from a request parameter.

| Method | Path | Returns |
|--------|------|---------|
| `POST` | `/api/auth/login` | Form login (`username`, `password`) → JSON auth result |
| `GET` | `/api/auth/me` | Current session user, persona, portfolio id |
| `GET` | `/api/auth/personas` | The demo persona list that drives the one-click login |
| `GET` | `/api/portfolio/holdings` | Positions: qty, avg cost, price, market value, weight, unrealized P&L |
| `GET` | `/api/portfolio/allocation` | Sector allocation weights |
| `GET` | `/api/portfolio/pnl` | Portfolio value series + totals and daily change |
| `GET` | `/api/portfolio/transactions` | Paged buy/sell history with running cost basis |
| `GET` | `/api/portfolio/benchmark` | Portfolio vs SPX500, rebased |
| `GET` | `/api/portfolio/risk` | Sharpe, annualised vol, max drawdown, beta, historical + parametric VaR |
| `GET` | `/api/portfolio/correlation` | Pearson / Spearman correlation matrix |
| `GET` | `/api/portfolio/attribution` | Fama-French OLS attribution (alpha, Mkt-RF, SMB, HML) |
| `GET` | `/api/portfolio/pairs` | Cointegrated pairs (Engle-Granger + ADF), hedge ratio, spread Z |
| `GET` | `/api/portfolio/forecast` | Monte Carlo fan-chart percentiles for the selected model |
| `GET` | `/api/ai/commentary` | Daily AI commentary (seeded or live) |
| `GET` | `/api/ai/structured` | Structured insight record that drives a chart directly |
| `GET` | `/api/ai/explain/{ticker}` | Per-position AI explanation |
| `POST` | `/api/ai/chat` | RAG Q&A over the filings corpus → answer + citations |
| `GET` | `/api/ai/status` | Whether the session is in demo or live-key mode |
| `POST`/`DELETE` | `/api/ai/key` | Store / clear the session-only LLM key |

- **OpenAPI spec:** <http://localhost:8080/v3/api-docs> — the BYO-key intake endpoint is deliberately excluded so no key surface is published.
- **Swagger UI:** <http://localhost:8080/swagger-ui.html>

</details>

## 🔌 MCP server

QuantLens re-exposes its analytics as a **Model Context Protocol** server over Streamable HTTP at `/mcp`, embedded in the Spring Boot app — no sidecar process. It is HTTP-Basic gated and IDOR-safe: the target portfolio comes from the authenticated principal in the `SecurityContextHolder`, never from a tool parameter. Every tool body is wrapped so the client receives a static safe message while the stack trace stays in the server log.

| Tool | Params | Returns |
|------|--------|---------|
| `get_portfolio_summary` | *(none — principal-derived)* | Market value, cost basis, unrealized P&L (abs + %), daily change, sector weights |
| `get_risk_metrics` | *(none — principal-derived)* | Sharpe, annualised vol, max drawdown, beta vs SPX500, historical + parametric VaR (95%, 1-day) |
| `get_position_detail` | `ticker` | One holding: name, sector, qty, avg cost, price, market value, weight, unrealized P&L. Sanitised to `[A-Z0-9.]`, capped at 10 chars |

```bash
# the committed .mcp.json entry, verified by hand
curl -s http://localhost:8080/mcp \
  -u alice:demo1234 \
  -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize",
       "params":{"protocolVersion":"2024-11-05","capabilities":{},
                 "clientInfo":{"name":"curl","version":"1.0"}}}'
```

```json
{"jsonrpc":"2.0","id":1,"result":{"protocolVersion":"2024-11-05","capabilities":{"completions":{},"logging":{},"prompts":{"listChanged":true},"resources":{"subscribe":false,"listChanged":true},"tools":{"listChanged":true}},"serverInfo":{"name":"quantlens-mcp","version":"1.0.0"}}}
```

<details>
<summary><b>Connect it to Claude Code · the security details · dev MCP servers</b></summary>

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

1. `docker compose up` — the embedded MCP server comes up on `:8080`.
2. `claude mcp get quantlens` — confirms Claude Code picked up the committed entry.
3. `/mcp` in a session — lists the three tools.

Override the credential without editing the file: `export QUANTLENS_MCP_AUTH="Basic $(printf 'bob:secret' | base64)"`.

**What a tool actually looks like**

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
        double sharpeRatio, double annualizedVolatility, double maxDrawdown, double beta,
        BigDecimal historicalVar95,   // positive loss amount, method == "HISTORICAL"
        BigDecimal parametricVar95    // positive loss amount, method == "PARAMETRIC"
) {}
```

**Security notes**

- Each tool delegates to `PortfolioService` / `RiskCalculator` and recomputes nothing.
- `resolvePortfolioId()` rejects `null`, unauthenticated and `AnonymousAuthenticationToken` principals (whose `isAuthenticated()` returns `true` by design), then maps username → portfolio. Both "user not found" and "no portfolio" return `401`, so an LLM cannot probe for other users' portfolios.
- Typed results are plain records (no Spring/JPA annotations) for reliable JSON-schema generation; a `null` VaR is never serialised to the client — it becomes a static error instead.
- `GET /mcp` without credentials returns `401`. The POST transport is CSRF-exempt, which is safe because the endpoint is Basic-authenticated rather than session-based.
- `/mcp` is a machine endpoint (JSON-RPC + SSE). Opening it in a browser yields `Invalid Accept header. Expected TEXT_EVENT_STREAM` — expected, not a bug.

**Dev MCP servers (`.mcp.json`)**

- **context7** (`npx -y @upstash/context7-mcp@latest`) — current Spring AI / Hipparchus / finmath / Vue docs in Claude Code's context. No configuration.
- **project-db** (`npx -y @henkey/postgres-mcp-server`) — query the local dev database. `@henkey/postgres-mcp-server` is the recommended replacement for `@modelcontextprotocol/server-postgres`, which was **deprecated and archived in July 2025** over a SQL-injection vulnerability. Do not use the deprecated package.

  Prefer to keep credentials out of version control? Register it user-scoped instead:

  ```bash
  claude mcp add --transport stdio project-db \
    --env POSTGRES_CONNECTION_STRING=postgresql://quantlens:quantlens_dev@localhost:5432/quantlens \
    -- npx -y @henkey/postgres-mcp-server
  ```

Verify either way with `claude mcp list` inside the project directory.

</details>

## 🗂️ Project layout

```
backend/src/main/java/com/quantlens/
  portfolio/    holdings, P&L, allocation, transactions   (api · service · domain)
  analytics/    RiskCalculator · CorrelationCalculator · FamaFrenchCalculator
                CointegrationScanner · ForecastService    (Monte Carlo fan chart)
  ai/           ChatClientStrategy · DemoModeAdvisor · RagAdvisorConfig
                DeterministicHashingEmbeddingModel · RagSeedRunner
                StockQuoteToolService · LlmKeySessionHolder
  mcp/tools/    PortfolioMcpTools — the three @McpTool adapters
  seed/         SeedRunner + GbmGenerator (synthetic OHLCV history)
  security/     form login, session scoping, portfolio isolation
  resources/db/migration/  V1..V4 — Flyway owns all DDL, pgvector included
frontend/src/
  views/        LoginView · DashboardView
  components/   PnlChart · BenchmarkChart · CorrelationHeatmap · PairsTable
                MonteCarloFanChart · RiskScorecard · HoldingsTable · ai/*
  stores/       Pinia: auth · portfolio · ai
docs/           MODELS.md · RAG_DESIGN.md · screenshots/ · assets/
scripts/        rag-chat.ps1 — ask the RAG corpus from a terminal
docker-compose.yml   db (pgvector/pg16) → backend → frontend, health-gated
```

## 🧰 Stack

| Layer | Choice | Why this one |
|-------|--------|--------------|
| Runtime | **Java 21 LTS** | Records, sealed types, virtual threads; the Spring Boot 3.5 baseline, and every quant JAR here runs on it cleanly |
| Platform | **Spring Boot 3.5.13** + **Spring Modulith 1.4.11** | Latest stable 3.5.x, pairs with the Spring AI 1.1.x line; Modulith turns the module graph into a build-time contract |
| AI | **Spring AI 1.1.6** (Anthropic + OpenAI starters) | ChatClient + advisors, `@McpTool` MCP server, `entity(...)` structured output, `QuestionAnswerAdvisor` RAG — one abstraction, two providers |
| Monte Carlo | **finmath-lib 6.1.7** | The only pure-Java library shipping GBM, Merton jump-diffusion and Heston as ready-to-instantiate Monte Carlo processes |
| Statistics | **Hipparchus 4.0.3** (core + stat) | OLS for Fama-French, Pearson/Spearman, VaR percentiles, ADF primitives, seedable MersenneTwister — the maintained successor to Commons Math |
| Store | **PostgreSQL 16 + pgvector** | One store for relational data *and* RAG embeddings (HNSW, cosine). No separate vector DB |
| Migrations | **Flyway** (core + postgresql) | Owns all DDL including the pgvector schema (`initialize-schema=false` on the vector store) |
| SPA | **Vue 3.5** · **Vite 8** · **TypeScript 6** · **Pinia 3** | Composition API with `<script setup>`, `vue-tsc`-checked build, strict typing |
| Charts | **ECharts 6** / **vue-echarts 8** | Native candlestick, heatmap and fan-chart rendering on Canvas — no plugin stack |
| Styling | **Tailwind CSS 4.3** | Via `@tailwindcss/vite`, zero-config v4 |
| Tests | **JUnit 5 + Testcontainers** · **Vitest 4.1.8** | 45 backend test classes against a real Postgres; 16 frontend test files with `@vue/test-utils` + jsdom |
| Docs | **springdoc-openapi 2.8.17** | OpenAPI 3 spec + Swagger UI from the live controllers |

## 🗺️ Status & roadmap

✅ **Working today** — seeded zero-key demo mode · form login with three personas and session-scoped portfolios · full analytics suite (P&L, allocation, risk scorecard, correlation, attribution, cointegration, Monte Carlo forecasts) · Spring AI commentary, per-position explanations, structured output, tool calling and RAG Q&A with citations · embedded MCP server with three tools · Modulith boundary verification · Testcontainers + Vitest suites · one-command Docker stack.

🚧 **Known edges** — the correlation, attribution and cointegration panels are portfolio-dependent: a concentrated single-sector persona can legitimately produce a near-degenerate correlation matrix and an empty pairs list. Live-quote tool calling needs a Finnhub key. Seeded OHLCV is synthetic (GBM-generated), not market data.

🔜 **Next** — OAuth2/OIDC login in place of form login. The seam is already there: add `spring-boot-starter-oauth2-client`, configure `spring.security.oauth2.client.*`, add `.oauth2Login(...)` with the existing JSON success handler, map the OAuth `sub` claim to `app_users.external_id`, drop the seeded BCrypt users. No change to the authorization rules — route protection, session scoping and portfolio isolation stay as they are. The AI key seam needs **no changes at all**: `LlmKeySessionHolder` depends on the HTTP session, not on the login mechanism.

<details>
<summary><b>Deeper reading & production notes</b></summary>

- [docs/MODELS.md](docs/MODELS.md) — each Monte Carlo model in non-specialist language: what it is, why it is included, key assumptions, the parameters actually used, and its known limitations. Includes how to read a fan chart and why these are potential futures, not predictions.
- [docs/RAG_DESIGN.md](docs/RAG_DESIGN.md) — the zero-key deterministic embedding strategy and the demo/live retrieval split.
- `./scripts/rag-chat.ps1` — logs in and lets you ask the filings corpus questions from a terminal (grounded answer + 10-K citations). Demo mode needs no key; pass `-ApiKey sk-...` for live retrieval.
- **Module diagram** — regenerate with JDK 21 on `JAVA_HOME`: `.\mvnw.cmd test -pl backend -Dtest=QuantLensModulithTest` (PowerShell) or `./mvnw test -pl backend -Dtest=QuantLensModulithTest`. Writes `backend/target/spring-modulith-docs/components.puml`; the same test enforces the boundary graph.

**Production notes**

- `POSTGRES_PASSWORD: quantlens_dev` in `docker-compose.yml` is a dev-only, non-secret placeholder. Inject real credentials via environment variables or Docker secrets — never commit production passwords.
- AI provider keys are **never** stored server-side. The BYO-key popup keeps the key in the user's HTTP session only.
- `SPRING_PROFILES_ACTIVE: demo` activates the seed runner and demo AI responses. Remove it in production.

</details>

---

<p align="center"><sub>Built by <a href="https://github.com/ChinmayGit8765">Chinmay</a> · part of the <a href="https://chinmaygit8765.github.io/exaryn-studio/">Exaryn</a> studio</sub></p>
