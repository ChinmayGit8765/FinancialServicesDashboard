# Project Research Summary

**Project:** QuantLens -- AI Portfolio and Market Intelligence Dashboard
**Domain:** AI-augmented quantitative portfolio analytics (Spring Boot + Spring AI + Vue 3)
**Researched:** 2026-06-07
**Confidence:** HIGH

---

## Executive Summary

QuantLens is a flagship resume/portfolio piece combining a correctness-first quantitative finance backend with a fully-wired Spring AI layer behind a zero-setup seeded demo. Experts build this class of product by establishing a data foundation first (seeded OHLCV price series, demo users, pgvector schema), then building the quant engine before touching the AI layer, because the AI narrative layer has nothing credible to narrate until real computed metrics exist. Research across all four files converged independently on the same five-phase forced build order: data foundation -> quant engine -> demo-mode AI seam -> live AI features and RAG -> MCP and polish. Skipping ahead breaks demonstrability or correctness at every step.

The recommended stack is decisive: Java 21 LTS + Spring Boot 3.5.13 + Spring AI 1.1.6 (BOM-pinned, never milestone) + finmath-lib 6.1.7 + Hipparchus 4.0.3 + pgvector inside the existing Postgres 16 container + ECharts 5 via vue-echarts 7. Market data decision: Finnhub (io.finnhub:kotlin-client:2.0.22, 60 req/min free tier) -- replaces Alpha Vantage and Polygon mentions in earlier notes; Finnhub rate limit is workable for a multi-holding demo where Alpha Vantage 25-req/day hard cap is not. Vector store decision: pgvector in the existing Postgres container -- no separate Weaviate or Qdrant service. Quant library decision: finmath-lib 6.1.7 for Monte Carlo (GBM, Heston, Merton jump-diffusion, path bootstrap) and Hipparchus 4.0.3 for all statistics, linear algebra, and regression. ADF cointegration must be assembled from Hipparchus primitives (OLS residuals + lagged t-test loop + MacKinnon 1994 critical values) because Hipparchus has no pre-built ADF.

The four highest-risk pitfalls to front-load are: (1) Spring AI version pinning -- pin spring-ai-bom:1.1.6 from day one and never use milestone releases; (2) quant correctness with golden-value unit tests -- every simulation and risk metric validates against a textbook known-good value before being narrated by the AI layer; (3) pgvector initialize-schema -- this property defaults false and silently produces an empty vector store, set spring.ai.vectorstore.pgvector.initialize-schema=true explicitly; (4) LLM session key leakage via logs or response bodies -- add a Logback redaction filter and key-pattern integration test before any live-AI code ships. The DemoModeAdvisor (a Spring AI CallAdvisor) is the most cross-cutting architectural decision: build it in a dedicated phase before any real LLM features or every AI feature needs its own mock.

---

## Cross-Cutting Decisions Resolved

| Question | Decision | Rationale |
|----------|----------|-----------|
| Market data API | **Finnhub** (io.finnhub:kotlin-client:2.0.22) | 60 req/min free tier vs Alpha Vantage 25 req/day; official JVM client; interoperates with Java 21 |
| Monte Carlo library | **finmath-lib 6.1.7** | Only pure-Java library with GBM, Heston, Merton jump-diffusion as ready-to-instantiate MonteCarloProcess; written by Prof. Christian Fries (LMU Munich) |
| Stats/LA library | **Hipparchus 4.0.3** | Active fork of Apache Commons Math; OLS, covariance, correlation, VaR percentiles, matrix ops, Cholesky, distributions -- all in one managed artifact set |
| ADF cointegration | **Assembled from Hipparchus primitives** | No pre-built ADF; implement as OLS residuals via OLSMultipleLinearRegression + lagged t-test loop; verify critical values against MacKinnon 1994 |
| Vector store | **pgvector in the existing Postgres container** | pgvector/pgvector:pg16 has extension pre-installed; spring-ai-starter-vector-store-pgvector:1.1.6 is first-class; no extra Docker service needed |
| Demo embeddings without a key | **Pre-computed Flyway-seeded embeddings (recommended)** -- run real embeddings once offline against 3-5 SEC filings, serialize float arrays to SQL, commit as a Flyway migration. Alternative: Ollama nomic-embed-text at Docker seed time. Flyway preferred: deterministic, no extra container, no first-run network latency | Flyway seed guarantees reproducibility on slow machines or restricted networks; Ollama adds a model pull step |
| Heston vs bootstrap calibration | **Heston: fixed illustrative parameters** (kappa=2, theta=0.04, sigma_v=0.3, rho=-0.7, v0=0.04) with a UI disclaimer -- calibrating from equity return series alone is statistically unreliable and frequently violates the Feller condition (2*kappa*theta >= sigma_v^2). **Bootstrap: block resampling** of historical returns (block length ~sqrt(T) trading days) via Hipparchus -- model-free and defensible | Heston calibration from return series is the classic looks-rigorous-isnt trap; fixed documented params are more credible than a poorly-calibrated fit |

---

## Key Findings

### Recommended Stack

Spring Boot 3.5.13 + Spring AI 1.1.6 is the only stable pairing -- Spring AI 2.0.0-Mx milestones introduce API breaks and require Spring Boot 4 (also milestone-only as of mid-2026). Java 21 LTS is the ceiling: finmath-lib and Hipparchus target Java 11 bytecode and run cleanly on 21; Spring Boot 3.5 tops out at Java 24 support; Java 25 requires Boot 4. Vue 3 with ECharts 5 via vue-echarts 7 is the only option with native candlestick, heatmap, and fan-chart support without plugins.

**Core technologies:**
- **Java 21 LTS**: runtime -- only LTS compatible with Spring Boot 3.5 and the quant library ecosystem
- **Spring Boot 3.5.13**: application platform -- latest stable 3.5 patch; Boot 4 is milestone-only
- **Spring AI 1.1.6** (BOM-pinned): AI layer -- ChatClient, advisors, tool calling, structured output, vector stores, @McpTool
- **finmath-lib 6.1.7**: Monte Carlo -- GBM, Heston, Merton jump-diffusion, bootstrap path resampling
- **Hipparchus 4.0.3** (hipparchus-stat + hipparchus-core): statistics and LA -- OLS, covariance, correlation, VaR percentiles, Cholesky, distributions, ADF primitives
- **Finnhub** (io.finnhub:kotlin-client:2.0.22): live market data -- 60 req/min free, official JVM client, used inside Spring AI @Tool
- **PostgreSQL 16 + pgvector 0.7** (pgvector/pgvector:pg16): relational persistence and RAG vector store -- single container
- **Vue 3 + ECharts 5 + vue-echarts 7**: frontend -- native candlestick, heatmap, fan-chart support
- **Pinia 2**: Vue state management -- official Vuex successor
- **Spring Modulith**: module boundary enforcement -- preferred over Maven multi-module; auto-generates module dependency diagram as a portfolio artifact

### Expected Features

**Must have -- table stakes:**
- Holdings view (position list, current value, weight, cost basis, unrealized P&L)
- Portfolio-level P&L time series and daily change line chart
- Allocation breakdown pie/treemap by sector and ticker
- Transaction history (paginated buy/sell log)
- Risk metrics scorecard: Sharpe ratio, annualized volatility, max drawdown, beta vs benchmark, VaR (95%)
- Correlation heatmap (pairwise daily return correlations across holdings)
- Benchmark comparison (portfolio vs seeded S&P 500 proxy on same chart)
- Demo user switcher (3 seeded personas: Alice/growth, Bob/income, Charlie/balanced)
- Dockerized full stack, single-command start with Flyway seed on first run

**Should have -- differentiators (resume signal):**
- Monte Carlo fan chart with 4-model selector (GBM, Merton jump-diffusion, Heston, block bootstrap) + documented rationale per model
- Factor attribution (Fama-French 3-factor OLS: alpha, beta_mkt, beta_smb, beta_hml) with bar chart
- Cointegration pairs scanner (Engle-Granger 2-step, spread Z-scores, half-life, Bonferroni-corrected p-values)
- Parametric + historical + MC VaR side by side with method/confidence/horizon labels
- NL portfolio Q&A with MessageChatMemoryAdvisor + demo-mode intercept
- RAG over seeded 10-K filings via QuestionAnswerAdvisor + pgvector
- Tool calling for live quotes via @Tool on Finnhub client (demo mode returns seeded price)
- Structured output driving charts (BeanOutputConverter<T>)
- Explain-this-position AI panel (ChatClient + RAG context injection)
- AI daily portfolio commentary (ChatClient + portfolio snapshot)
- @McpTool MCP server exposing portfolio and risk services (freshest 2026 resume signal)
- Multi-provider toggle (Claude + OpenAI) + BYO-key session popup

**Defer to v2+:**
- Portfolio optimization / efficient frontier (requires convex solver; mention in README)
- Backtesting engine (look-ahead-bias infrastructure required; mention in README)
- Real OAuth SSO (document upgrade path in README, do not build for v1)
- 5-factor Fama-French (easy extension; not required for v1 signal)
- Streaming AI responses via SSE (visual improvement; low priority)

**Hard anti-features (do not build):**
- Real brokerage API integration, order execution, real-time WebSocket streaming, persisting LLM keys, fine-tuned LLM, mobile PWA

### Architecture Approach

A Spring Modulith monolith (not Maven multi-module) with five package-level modules enforced by ApplicationModules.verify(). The single most important architectural element is DemoModeAdvisor -- a Spring AI CallAdvisor placed first in every ChatClient advisor chain, checking LlmKeySessionHolder (@SessionScope). If no key: returns a seeded JSON fixture without any external call. All downstream advisors and controllers are unaware of demo state. The MCP server runs inside the same JVM on the same port (/mcp), not as a separate container. Docker Compose uses condition: service_healthy on the db service to prevent Boot startup before pgvector is ready.

**Major components:**
1. **portfolio module** -- holdings, transactions, P&L, allocation CRUD; Spring Data JPA; source of truth for all quant inputs
2. **analytics module** -- quant engine: Monte Carlo, risk metrics, factor attribution, arbitrage detection; uses finmath-lib + Hipparchus; no AI dependencies
3. **ai module** -- ChatClientStrategy, DemoModeAdvisor, QuestionAnswerAdvisor wiring, RAG ingestion pipeline, structured output DTOs, daily commentary; depends on portfolio and analytics
4. **marketdata module** -- Finnhub HTTP client, @Tool-annotated MarketDataTools, demo quote provider; pure adapter, no module dependencies
5. **mcp module** -- @McpTool beans wrapping portfolio and analytics services; Streamable HTTP transport on /mcp; thin protocol adapter layer
6. **auth module** -- Spring Security in-memory users, LlmKeySessionHolder @SessionScope bean, OAuth upgrade path documented
7. **Data layer** -- PostgreSQL 16 + pgvector; Flyway migrations for schema + seeds; spring.ai.vectorstore.pgvector.initialize-schema=true mandatory

### Critical Pitfalls

1. **Spring AI version pinning** -- Artifact IDs changed wholesale in M7; PromptChatMemoryAdvisor removed in 1.1.6; tools() silently broken post-M8. Fix: pin spring-ai-bom:1.1.6; use spring-ai-starter-* artifact IDs; pass conversation ID at call time via .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, id)); smoke-test full ChatClient chain on day one.
2. **pgvector initialize-schema not set** -- Defaults false; vector store table never created, operations silently fail. Fix: spring.ai.vectorstore.pgvector.initialize-schema=true in all configs with a mandatory comment; verify table and HNSW index after every cold start.
3. **Quant correctness without golden-value tests** -- Sharpe with 365 instead of 252, missing Ito correction in GBM exponent, VaR method conflation, look-ahead bias in calibration windows. Fix: golden-value unit tests for every metric before it is narrated by the AI layer.
4. **LLM session key leakage** -- SimpleLoggerAdvisor logs prompts at DEBUG; OpenAI errors can echo request metadata. Fix: Logback redaction filter matching sk-[A-Za-z0-9]+; integration test asserting key absent from all logs and response bodies.
5. **Docker startup ordering** -- depends_on: db without condition: service_healthy crashes Boot against a not-yet-ready Postgres. Fix: healthcheck with pg_isready + condition: service_healthy; use pgvector/pgvector:pg16 image.
6. **Heston Feller condition violation** -- Calibrating from equity returns alone produces 2*kappa*theta < sigma_v^2, causing the variance process to go negative. Fix: fixed illustrative parameters, enforce Feller check in code, label as illustrative in UI.
7. **Demo embeddings requiring live API key at startup** -- Calling the embedding API during docker compose up breaks zero-setup demo. Fix: pre-computed Flyway-seeded embeddings (recommended); startup loader checks for existing embeddings before inserting.

---

## Implications for Roadmap

Both Architecture and Features research independently converged on the same forced build order. Module dependency direction makes this non-negotiable: quant engine before AI layer, demo seam before live features, data foundation before everything.

### Phase 1: Data Foundation

**Rationale:** All quant metrics, AI context, and chart demos depend on seeded price series, portfolio rows, and a working database schema. The pgvector initialize-schema and Docker healthcheck pitfalls must be neutralized here before any feature work begins.

**Delivers:** Running Docker Compose with service_healthy healthcheck, Flyway schema and seed migrations (demo users, OHLCV for ~15 securities over ~2 years, transactions, sector metadata, seeded Fama-French factor return series, embedding dimension locked in TECH_DECISIONS.md), Spring Security in-memory auth with session cookies, holdings/transaction CRUD REST endpoints, Vue scaffold with Pinia stores and Axios layer, allocation and P&L charts rendering from seeded data.

**Addresses:** Holdings view, P&L time series, allocation breakdown, transaction history, benchmark comparison, demo user switcher, single-command Docker start.

**Avoids:** pgvector startup race (healthcheck + condition: service_healthy), BigDecimal vs double type policy at data model layer, initialize-schema explicit in Flyway migration.

**Research flag:** Standard patterns -- Spring Boot + Flyway + Spring Data JPA + Spring Security in-memory auth. No deep research needed.

---

### Phase 2: Quant Engine

**Rationale:** The hardest intellectual phase and the foundation for all AI narrative. Build and test in isolation before touching Spring AI so correctness is established. All quant correctness pitfalls addressed with golden-value tests here before the AI layer narrates anything.

**Delivers:** Risk metrics REST API (Sharpe with log returns + 252-day annualization, parametric + historical + MC VaR with method/confidence/horizon labels, beta, volatility, correlation matrix), Monte Carlo fan chart API (GBM first, then Merton jump-diffusion, then Heston with fixed illustrative params, then block bootstrap), factor attribution (Fama-French 3-factor OLS via Hipparchus OLSMultipleLinearRegression), cointegration pairs scanner (Engle-Granger 2-step, ADF from Hipparchus primitives, Bonferroni-corrected p-values, spread Z-scores, half-life of mean reversion), analytics REST DTOs.

**Uses:** finmath-lib 6.1.7, Hipparchus 4.0.3.

**Avoids:** Look-ahead bias (calibration windows end before simulation start date), Sharpe annualization error (252 trading days, consistent log returns), GBM Ito correction (unit test: E[S(T)] approx S(0)*exp(mu*T) over 100k paths), spurious cointegration (ADF on spread residuals not prices, Bonferroni correction), Monte Carlo on request thread (async + cached).

**Research flag:** Needs phase research -- ADF assembled from Hipparchus primitives (MacKinnon 1994 critical values); finmath-lib HestonModel discretization specifics (Euler-Maruyama vs Milstein, internal Feller guard); block bootstrap via Hipparchus DescriptiveStatistics.

---

### Phase 3: Demo-Mode AI Seam

**Rationale:** DemoModeAdvisor is cross-cutting infrastructure that all AI features depend on. Build as a standalone phase before wiring any real LLM so all AI panels are screenshot-ready in demo mode. Seeded AI responses authored against finalized seeded portfolio data to guarantee internal consistency.

**Delivers:** DemoModeAdvisor (Spring AI CallAdvisor, intercepts when LlmKeySessionHolder.hasKey() is false), LlmKeySessionHolder (@SessionScope), ChatClientStrategy.forSession() (per-request ChatClient builder), seeded fixture store (src/main/resources/seeds/*.json loaded at startup), all AI panels rendering seeded content (explain-position, daily commentary, structured output chart), BYO-key popup UI component (POSTs to /api/auth/llm-key), AiExplanationDto + CommentaryDto DTOs, Logback key-redaction filter, key-leakage integration test.

**Avoids:** Fat AI controller (all demo logic in DemoModeAdvisor), single ChatClient bean for all scenarios (per-request builder with model.mutate()), key leakage from day one (session-only + redaction filter), seeded/live drift (seeded responses authored against finalized portfolio data).

**Research flag:** Standard patterns -- Spring AI CallAdvisor interface and @SessionScope bean are well-documented.

---

### Phase 4: Live AI Features and RAG

**Rationale:** With the demo seam proven, this phase flips it to live. RAG depends on pgvector schema (Phase 1) and pre-chunked documents. Multi-provider selection depends on ChatClientStrategy.forSession() (Phase 3). Tool calling depends on Finnhub client integration.

**Delivers:** Finnhub @Tool integration (live quotes; demo mode returns seeded prices), multi-provider ChatClient (Anthropic + OpenAI, per-session key via model.mutate()), QuestionAnswerAdvisor wired into advisor chain, structure-aware RAG ingestion pipeline for SEC filings (tables as atomic chunks, section metadata per chunk: ticker/year/section/page), pre-computed Flyway-seeded embeddings for 3-5 SEC 10-K filings, NL Q&A with MessageChatMemoryAdvisor (conversation ID at call time not builder), structured output via ChatClient.call().entity(T.class), explain-this-position live mode, daily commentary live mode.

**Avoids:** Demo embeddings requiring live key at startup (Flyway-seeded pre-computed embeddings), Spring AI advisor API misuse (conversation ID at call time), naive 10-K chunking (structure-aware chunking: tables as atomic chunks, section metadata), RAG prompt injection (system prompt wraps retrieved chunks in untrusted-content delimiters with instruction not to follow embedded instructions).

**Research flag:** Needs phase research -- Flyway binary column storage for pre-computed float array embeddings; structure-aware SEC EDGAR XBRL/HTML filing chunking approach; model.mutate() per-session API key injection at Spring AI 1.1.6 (verify against GitHub issue #2731 pattern).

---

### Phase 5: MCP Server and Polish

**Rationale:** @McpTool wraps already-working service beans and is correct by construction. Building last means no rework. This phase also makes the repository read as senior-engineer work.

**Delivers:** PortfolioMcpTools (@McpTool beans: get_portfolio_summary, get_risk_metrics, get_position_detail, query_filings), Streamable HTTP transport configuration on /mcp, Spring Security filter chain on /mcp/** (auth before any tool is reachable), McpExceptionHandler stripping stack traces from MCP error responses, .mcp.json dev config for Claude Code, ApplicationModules.verify() ArchUnit test, stochastic model rationale writeup (README), OpenAPI spec via springdoc-openapi, README screenshots (all demo mode), OAuth upgrade path documentation.

**Avoids:** MCP stdio transport for product server (Streamable HTTP required for remote access), @McpTool with polymorphic return types (concrete records only for reliable JSON schema), unauthenticated /mcp endpoint, exception stack traces in MCP error responses.

**Research flag:** Needs phase research -- Spring AI MCP Streamable HTTP transport path config; Spring Security filter chain integration with spring-ai-mcp-server-boot-starter at 1.1.6.

---

### Phase Ordering Rationale

- Data before everything: populated database required by all quant and AI features; Phase 1 is non-negotiable as first.
- Quant before AI: the AI layer narrates computed results; narrating incorrect results is worse than narrating nothing; Phase 2 precedes Phases 3-5.
- Demo seam before live features: every AI panel must be screenshot-ready before introducing any external keys; Phase 3 precedes Phase 4.
- RAG in Phase 4 not earlier: requires pgvector schema (Phase 1), pre-computed embeddings authored at Phase 4 planning time, and DemoModeAdvisor (Phase 3) in place.
- MCP last: @McpTool is a thin protocol adapter on top of already-working services; building before services exist means constant rework.

### Research Flags

**Needs deeper research during phase planning:**
- **Phase 2 (Quant Engine):** ADF assembled from Hipparchus primitives (MacKinnon 1994 critical values table); finmath-lib HestonModel discretization (Euler-Maruyama vs Milstein, internal Feller condition handling); block bootstrap implementation.
- **Phase 4 (Live AI + RAG):** Flyway float array embedding column storage approach; structure-aware SEC EDGAR XBRL/HTML filing chunking; model.mutate() per-session key injection verified at Spring AI 1.1.6.
- **Phase 5 (MCP + Polish):** Spring AI MCP Streamable HTTP transport path config; Spring Security filter chain integration with spring-ai-mcp-server-boot-starter.

**Standard patterns -- skip research-phase:**
- **Phase 1 (Data Foundation):** Spring Boot + Flyway + Spring Data JPA + Spring Security in-memory auth are among the most documented Java patterns.
- **Phase 3 (Demo-Mode Seam):** Spring AI CallAdvisor interface and @SessionScope bean pattern are well-documented in official Spring AI reference.

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | All artifact versions verified via Maven Central and official Spring blogs; compatibility matrix explicitly documented in STACK.md |
| Features | HIGH | Derived from market comparison (Bloomberg PORT, Portfolio Visualizer, IBKR PA) plus Spring AI API surface mapping; prioritization explicit |
| Architecture | HIGH | Patterns verified against official Spring AI docs and GitHub issues (#2731, #6150, #3361); Spring Modulith recommendation verified |
| Pitfalls | HIGH | Sourced from official Spring AI upgrade notes, peer-reviewed finance literature (Lo 2002), OWASP, and Spring AI GitHub commits |

**Overall confidence: HIGH**

### Gaps to Address During Planning

- **ADF critical values**: Phase 2 must include a spike validating the hand-rolled ADF implementation against a known cointegrated pair using MacKinnon 1994 critical values table.
- **Flyway binary embedding storage**: Column type decision for pre-computed float arrays (vector(1536) directly vs float[] deserialized at startup). Validate at Phase 4 planning time.
- **finmath-lib Heston discretization**: Verify whether HestonModel uses Euler-Maruyama or Milstein for the variance process and whether the Feller condition guard is internal. Phase 2 spike.
- **Embedding dimension lock-in**: Decision: OpenAI text-embedding-3-small (1536 dimensions) for pre-computed seeded embeddings. This dimension is locked at Flyway migration time. Document in TECH_DECISIONS.md during Phase 1.
- **Finnhub weekend/after-hours behavior**: Validate that the Kotlin client returns last closing price with a correct timestamp rather than a misleading live label on stale Friday data; add a quote cache with 15-minute TTL in Phase 4.

---

## Sources

### Primary (HIGH confidence)
- Spring AI 1.1 GA + 1.1.6 release blogs -- feature list, BOM pairing, version confirmation
- Spring AI reference docs (ChatClient, Advisors, PgVector, Tool Calling, Structured Output, MCP annotations)
- Spring AI upgrade notes -- breaking change catalog, artifact ID renames, advisor API changes (https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
- Spring AI GitHub issues #2731, #6150, #3361 -- per-session key injection, auth breaking change, multi-provider qualifier pattern
- Spring AI M8 commit 5b7849de -- tools() silent breakage confirmed; migrate to toolSpecifications()
- finmath-lib GitHub + Maven Central 6.1.7 -- GBM/Heston/Merton MC support confirmed
- Hipparchus GitHub 4.0.3 + stat/core docs -- OLS/covariance/correlation API verified
- Finnhub Kotlin client Maven Central 2.0.22 -- official JVM client confirmed
- Lo, A.W. (2002) The Statistics of Sharpe Ratios, Financial Analysts Journal -- annualization and serial correlation correction
- OWASP LLM Prompt Injection Prevention Cheat Sheet -- RAG system prompt defense
- Heston calibration instability from equity return series -- arXiv 2407.15536

### Secondary (MEDIUM confidence)
- Java 25 vs 21 migration guide (JavaCodeGeeks) -- Spring Boot 3.5 Java 24 ceiling confirmed
- Snowflake engineering blog -- structure-aware chunking achieves 87.7% context recall vs ~62% for naive fixed-size chunking on SEC filings
- Hudson & Thames -- Engle-Granger cointegration procedure for pairs trading
- Docker + Spring Boot startup ordering practitioner blog, verified against Docker docs
- Finnhub vs Alpha Vantage rate limit comparison, verified against provider pricing pages

### Tertiary (LOW confidence)
- Individual practitioner blog posts on VaR method comparison -- consistent with textbook treatment; verify exact formulas against an authoritative source during Phase 2

---
*Research completed: 2026-06-07*
*Ready for roadmap: yes*
