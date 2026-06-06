<!-- GSD:project-start source:PROJECT.md -->
## Project

**QuantLens — AI Portfolio & Market Intelligence Dashboard**

An AI-augmented portfolio & market intelligence dashboard that fuses a quantitative-finance backend with a modern Spring AI layer. A **Spring Boot** service computes genuine portfolio analytics — P&L, allocation, and risk metrics (Sharpe, VaR, beta, volatility, correlation matrices), **stochastic forecasts of potential futures** (Monte Carlo across multiple models), **causal return/risk attribution**, and **arbitrage detection**. A **Spring AI** layer on top adds natural-language Q&A over the portfolio, RAG over earnings reports / 10-Ks / filings, tool calling for live quotes, and structured output that drives the **Vue 3** charts directly. It is built as a flagship portfolio/resume piece signalling a quant-leaning AI engineer: Java/Spring depth + modern Spring AI + Vue + quantitative finance.

**Core Value:** A working, screenshot-ready dashboard that demonstrates **real quantitative-finance analytics narrated by a real Spring AI layer** — instantly convincing with zero setup (seeded data + seeded LLM responses), yet genuinely live the moment you paste your own LLM key.

### Constraints

- **Tech stack:** Spring Boot + Spring AI (multi-provider), Vue 3 Composition API, ECharts/Chart.js, Postgres (with pgvector likely for RAG), Docker / docker-compose — Java/Spring + Vue + quant is the intended signal.
- **Security:** never persist user-supplied LLM keys; the popup key is session-only.
- **Demo-first:** every feature must degrade gracefully to a seeded mode with no external keys or network dependencies, so the app always runs and always demos.
- **Quant credibility:** risk, stochastic-forecasting, causal, and arbitrage math must be correct and defensible — this is the part the author's fin-math background makes credible.
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Recommended Stack
### Core Technologies
| Technology | Version | Purpose | Why Recommended |
|------------|---------|---------|-----------------|
| Java | 21 LTS | Runtime | Spring Boot 3.5 baseline; Java 25 is too new for the quant library ecosystem (finmath-lib targets Java 11 compile target; Hipparchus 4.x targets Java 11; Strata 2.x requires Java 8+). Java 21 has virtual threads, records, sealed classes, and runs until Sept 2026 free; all finmath and Hipparchus JARs run on 21 without issues |
| Spring Boot | 3.5.13 | Application platform | Latest stable 3.5.x patch (March 2026); pairs with Spring AI 1.1.x. Spring Boot 4.0 / Spring AI 2.0 are milestone-only as of mid-2026 and introduce breaking changes (requires Spring Framework 7); avoid for a portfolio piece until GA |
| Spring AI | 1.1.6 | AI layer / LLM abstraction | Latest stable 1.1.x patch (May 2026); supports ChatClient, advisors, tool calling, structured output, vector stores, MCP server annotations (@McpTool). Multi-provider: Anthropic + OpenAI both first-class. Do NOT use 2.0.0-Mx milestones — API is still shifting |
| Vue 3 | 3.x (latest) | Frontend SPA | Composition API, `<script setup>`, excellent TypeScript support. Required by the project brief |
| Apache ECharts + vue-echarts | ECharts 5.x / vue-echarts 7.x | Data visualisation | Only option with native candlestick, heatmap, and custom fan-chart support out of the box. Renders via Canvas — handles 1M+ points at 60 FPS. Chart.js requires plugins for everything financial |
| PostgreSQL | 16.x | Relational persistence + vector store | pgvector extension ships in the official Docker image; single DB for both relational data and RAG embeddings — no separate Weaviate/Qdrant container needed |
| pgvector | 0.7.x (bundled with pgvector/pgvector Docker image) | Vector similarity search | Spring AI 1.1 has first-class `spring-ai-starter-vector-store-pgvector`; HNSW index, cosine distance, auto schema init. Eliminates a separate vector DB service |
| Docker / docker-compose | Latest stable | Container orchestration | From day one per project constraints |
### Java Quant / Math Library Stack
#### Primary Recommendation by Need
| Need | Library | Version | What It Provides | Confidence |
|------|---------|---------|-----------------|------------|
| Monte Carlo stochastic simulation (GBM, jump-diffusion, Heston, bootstrap) | **finmath-lib** | 6.1.7 | Native SDE Monte Carlo engine: GBM (Black-Scholes multi-asset), Merton jump-diffusion, Heston stochastic-vol — all as Monte Carlo processes. Bootstrap paths via path resampling. AAD (stochastic AD) included. Java 11 compile target, runs on 21 | HIGH |
| Statistics (OLS regression, covariance, correlation matrices, t-tests, ADF) | **Hipparchus** | 4.0.3 | `OLSMultipleLinearRegression`, `Covariance`, `PearsonsCorrelation`, `SpearmansCorrelation`, `TTest`, `KolmogorovSmirnovTest`, `NormalDistribution`, `MersenneTwister` RNG. The successor to Apache Commons Math maintained by the original authors | HIGH |
| Linear algebra (matrix ops, Cholesky for correlated random draws) | **Hipparchus** | 4.0.3 | `RealMatrix`, `LUDecomposition`, `CholeskyDecomposition`, `EigenDecomposition` — all in `hipparchus-core`. No need for a separate LA library | HIGH |
| VaR / CVaR computation | **Hipparchus** | 4.0.3 + **finmath-lib** | Use Hipparchus `DescriptiveStatistics.getPercentile()` for Historical VaR. For Parametric VaR: `NormalDistribution.inverseCumulativeProbability()`. For MC VaR: sort finmath Monte Carlo path terminal values and percentile-cut | HIGH |
| Factor attribution / Fama-French style regression | **Hipparchus** | 4.0.3 | `OLSMultipleLinearRegression` for portfolio returns ~ factor returns. Residuals, R², t-stats all available | HIGH |
| Cointegration / pairs trading (ADF unit-root test, Engle-Granger 2-step) | **Hipparchus** | 4.0.3 | Engle-Granger step 1: OLS residuals via `OLSMultipleLinearRegression`. Step 2: ADF implemented using `TTest` and lagged OLS — must hand-roll the ADF statistic loop (Hipparchus has no pre-built ADF), but all primitives are present. Verify the critical values table against MacKinnon 1994 | MEDIUM |
| Sharpe, beta, volatility, Pearson/Spearman correlation | **Hipparchus** | 4.0.3 | `DescriptiveStatistics`, `PearsonsCorrelation`, `SpearmansCorrelation`, standard arithmetic on double arrays | HIGH |
#### Library Rationale in Detail
- The only pure-Java library that ships GBM, Heston, and Merton jump-diffusion as ready-to-instantiate `MonteCarloProcess` implementations.
- Written by Professor Christian Fries (LMU Munich), used in academic and practitioner contexts.
- Provides `MonteCarloSimulationModel`, `AssetModelMonteCarloSimulationModel`, and process classes like `HestonModel`, `MertonModel`, `BlackScholesModel`.
- Stochastic AAD enables sensitivity calculations without separate finite-difference passes.
- Bootstrap: simulate by resampling historical return blocks; not natively in finmath but trivial to implement once you have the statistical primitives from Hipparchus.
- Latest Maven Central release: 6.1.7 (June 2026).
- A hard fork of Apache Commons Math started by the original ACM dev team. Actively maintained, latest release February 2025.
- Split into focused modules: `hipparchus-core`, `hipparchus-stat`, `hipparchus-geometry`, `hipparchus-optim`.
- Has everything the quant engine needs for statistics, distributions, regression, and linear algebra.
- Replaces both Commons Math 3.x AND fills the gap left by Commons Math 4.0 (still in 4.0-beta1 as of 2026, no stable release).
- **Apache Commons Math 3.6.1** — final release 2016, not maintained. Has the same API as Hipparchus (it's the ancestor) but misses years of fixes. Commons Math 4 is perpetually in beta.
- **Apache Commons Math 4.0-beta1** — unstable, API still changing, no GA release as of mid-2026.
- **Strata (OpenGamma 2.12.71)** — excellent for derivatives pricing (IRD, CDS, FX), curve calibration, and sensitivity. However, it is heavyweight (>20 transitive deps), targets fixed-income instrument pricing workflows, and has Java 8 baseline with limited Java 21 modules support. For portfolio-level equity analytics (Sharpe, VaR, beta, Monte Carlo equity paths) finmath + Hipparchus is the right fit. Strata would be right if the project priced bond portfolios or OTC derivatives.
- **ND4J / Deeplearning4j** — heavyweight ML framework (50+ MB), native backend setup, designed for neural network tensor ops. Overkill for statistics and Monte Carlo. Significant startup overhead in a Spring Boot context.
- **Smile 4.x** — excellent ML library (k-means, SVM, random forest) but Monte Carlo finance is not its domain; statistical primitives overlap with Hipparchus with no benefit. Adds complexity without value here.
- **EJML 0.44** — fast for pure linear algebra benchmarks but has no statistical or finance domain knowledge. Hipparchus covers the same LA needs with OLS, distributions, and decompositions in the same library.
- **Tablesaw** — dataframe/visualization tool (think Pandas-lite for Java). Useful for exploratory data work but not appropriate for production MC simulation or risk computation in a Spring Boot service.
### Spring AI Version Pinning
### Multi-Provider Configuration (Anthropic + OpenAI)
# Disable the single-bean auto-config
# Anthropic
# OpenAI
### Spring AI Feature Map
| Feature | Spring AI 1.1.6 API | Notes |
|---------|--------------------|----|
| Chat completion | `ChatClient.prompt().user().call().content()` | Fluent builder per call |
| Tool calling | `@Tool` annotation on a `@Component` method | Spring AI registers it; pass via `.tools(...)` on prompt |
| Advisors | `MessageChatMemoryAdvisor`, `QuestionAnswerAdvisor` | Chained via `.advisors(...)`. `PromptChatMemoryAdvisor` deprecated in 1.1.6 — use explicit conversation ID |
| Structured output | `.call().entity(MyRecord.class)` | `BeanOutputConverter` auto-generates JSON schema; native structured output for GPT-4o / Claude 3.5+ |
| Chat memory | `InMemoryChatMemory` or `JdbcChatMemory` | Pass `ChatMemory.CONVERSATION_ID` to advisor params |
| RAG | `QuestionAnswerAdvisor` + `PgVectorStore` | Similarity search over embedded docs at query time |
| MCP server tool | `@McpTool` + `@McpToolParam` on `@Component` | Auto-registered by `spring-ai-mcp-server-boot-starter`; `annotation-scanner.enabled=true` |
| Embedding | `spring-ai-starter-model-openai` includes `OpenAiEmbeddingModel` | For RAG ingestion pipeline |
| Prompt caching | Anthropic auto-cache via provider config | Reduces cost on long system prompts |
### Market Data API
| Provider | Free Rate Limit | Data Delay | Java Client | Verdict |
|----------|----------------|------------|-------------|---------|
| Finnhub | 60 calls/min | 20 min | Official Kotlin JVM client `io.finnhub:kotlin-client:2.0.22` (runs on Java 21) | **Recommended** |
| Alpha Vantage | 5 calls/min, 500/day | 15 min | None official | Too slow for demo; no official Java SDK |
| Twelve Data | 800 calls/day | 4 hr | None official | Data lag too high for live-quote UX |
### Supporting Libraries
| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `net.finmath:finmath-lib` | 6.1.7 | GBM/Heston/Merton MC simulation | Core quant engine |
| `org.hipparchus:hipparchus-stat` | 4.0.3 | OLS, covariance, correlation, percentiles, ADF primitives | Core statistics |
| `org.hipparchus:hipparchus-core` | 4.0.3 | Matrix ops, Cholesky, probability distributions, MersenneTwister | LA + RNG |
| `io.finnhub:kotlin-client` | 2.0.22 | Live market data | Used inside Spring AI @Tool |
| `org.springframework.ai:spring-ai-starter-vector-store-pgvector` | 1.1.6 | RAG vector store | Backed by pg + pgvector extension |
| `org.flywaydb:flyway-core` | 10.x | DB migrations | Managed schema evolution; pgvector extension init can be scripted |
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | 2.x | OpenAPI / Swagger UI | Dev convenience |
| `com.fasterxml.jackson.core:jackson-databind` | (managed by Boot) | JSON serialisation | Spring Boot manages version |
| `echarts` (npm) | 5.x | Chart engine | Via `vue-echarts` wrapper |
| `vue-echarts` (npm) | 7.x | Vue 3 ECharts component | Vue 3 Composition API, tree-shakeable |
| `pinia` (npm) | 2.x | Vue state management | Official Vuex successor |
| `axios` (npm) | 1.x | HTTP client in Vue | For Spring Boot API calls |
### Development Tools (MCP Servers for Claude Code)
| Tool | Config | Purpose |
|------|--------|---------|
| context7 MCP | `claude mcp add context7 -- npx -y @upstash/context7-mcp@latest` | Fetch current Spring AI / Hipparchus / finmath docs into Claude Code context |
| Postgres MCP | `claude mcp add --transport stdio project-db -- npx -y @henkey/postgres-mcp-server --connection-string postgresql://quantlens:quantlens@localhost:5432/quantlens` | Query the local dev DB from Claude Code; introspect schema, write migrations. **NOTE:** `@modelcontextprotocol/server-postgres` is DEPRECATED (archived July 2025, SQL-injection CVE) — DO NOT USE; this `@henkey/postgres-mcp-server` replacement needs human-verify before first install. |
## Alternatives Considered
| Recommended | Alternative | Why Not |
|-------------|-------------|---------|
| finmath-lib | Hand-rolled MC simulation | Correctness risk; Heston Euler-Maruyama discretisation is non-trivial; project decision explicitly rejects hand-rolled math |
| Hipparchus 4.0.3 | Apache Commons Math 3.6.1 | Unmaintained since 2016; Hipparchus is the same API, actively maintained by the same team |
| Hipparchus 4.0.3 | Commons Math 4.0-beta1 | Still in beta as of mid-2026; API unstable |
| Hipparchus (OLS) | Smile (OLS regression) | Smile adds a large transitive footprint for duplicated functionality; Hipparchus covers the need |
| pgvector | Weaviate / Qdrant as separate service | pgvector runs inside the existing Postgres container; no additional Docker service needed for an MVP |
| Finnhub Kotlin client | Alpha Vantage | Alpha Vantage free tier is 5 req/min — crippling for any live-quote UX |
| vue-echarts 7 / ECharts 5 | Chart.js | Chart.js has no native candlestick or heatmap; the financial chart types needed here are built into ECharts |
| Spring AI 1.1.6 | Spring AI 2.0.0-Mx milestones | 2.0 API is still changing (setter removal, etc.); Boot 4 dependency makes it a moving target |
| Java 21 LTS | Java 25 LTS | Spring Boot 3.5.x does not support Java 25 (only up to Java 24 officially); finmath-lib and Hipparchus target Java 11 compile target and have not explicitly tested 25 yet. Java 21 free support runs to Sept 2026 — sufficient for this project horizon. Revisit at next LTS cycle. |
## What NOT to Use
| Avoid | Why | Use Instead |
|-------|-----|-------------|
| Apache Commons Math 3.6.1 | Last release 2016; superseded by Hipparchus (same API, active team) | Hipparchus 4.0.3 |
| Apache Commons Math 4.0-beta1 | Still beta, API unstable as of mid-2026 | Hipparchus 4.0.3 |
| ND4J / Eclipse Deeplearning4j | 50+ MB native backend, neural-net overhead, no quant finance domain support | finmath-lib + Hipparchus |
| Smile | Excellent ML lib but Monte Carlo equity simulation is not its domain; adds weight for duplicate stats overlap | Hipparchus for stats; finmath for MC |
| EJML | Pure LA, no statistics, no distributions, no regression — you'd still need Hipparchus | Hipparchus (covers LA as a subset) |
| Strata (OpenGamma) | Designed for derivatives/rates pricing workflows; heavyweight; mismatch for equity portfolio metrics | finmath-lib + Hipparchus |
| Tablesaw | Dataframe/visualization exploratory tool; not production risk computation | finmath-lib + Hipparchus |
| Chart.js | No native candlestick, no heatmap, no fan-chart; financial chart types all require plugins | Apache ECharts 5 via vue-echarts |
| Spring AI 2.0.0-Mx | Milestone only, breaking API changes ongoing, requires Spring Boot 4 (also milestone) | Spring AI 1.1.6 |
| Spring Boot 4.0.x | RC/milestone as of mid-2026; Spring Boot 3.5.13 is the stable production choice | Spring Boot 3.5.13 |
| Java 25 | Spring Boot 3.5.x only supports up to Java 24; quant libs not yet validated on 25 | Java 21 LTS |
| Weaviate / Qdrant (standalone) | Separate Docker service for vector store when pgvector covers the same need inside existing Postgres | pgvector via Spring AI starter |
## Version Compatibility
| Component | Pairs With | Notes |
|-----------|-----------|-------|
| `spring-ai-bom:1.1.6` | `spring-boot:3.5.13` | Official pairing per Spring.io blog |
| `spring-ai-bom:1.1.6` | Java 17+ (21 recommended) | Spring Boot 3.5 minimum is Java 17; use 21 |
| `finmath-lib:6.1.7` | Java 11+ | Compiles to Java 11 bytecode; runs on 21 without issues |
| `hipparchus:4.0.3` | Java 11+ | Same baseline as finmath; runs on 21 without issues |
| `vue-echarts:7.x` | `echarts:5.x`, Vue 3 | vue-echarts 7 dropped Vue 2; Vue 3 only |
| `pgvector Docker image` | `postgresql:16` | Use `pgvector/pgvector:pg16` image in docker-compose |
| `io.finnhub:kotlin-client:2.0.22` | Kotlin stdlib bundled; runs on Java 21 | No Kotlin in your source required; just add the dependency |
| `spring-ai-starter-vector-store-pgvector` | Requires `JdbcTemplate` auto-config + `spring-boot-starter-jdbc` | Add `spring-boot-starter-data-jpa` or `jdbc` starter |
## Docker Compose Skeleton
## Sources
- [Spring AI 1.1 GA Released](https://spring.io/blog/2025/11/12/spring-ai-1-1-GA-released/) — Feature list, MCP annotations, prompt caching, Spring Boot 3.4/3.5 pairing (HIGH)
- [Spring AI 1.0.7/1.1.6/2.0.0-M6 release blog](https://spring.io/blog/2026/05/08/spring-ai-1-0-7-1-1-6-2-0-0-M6-available-now/) — Latest 1.1.x version confirmed (HIGH)
- [Spring AI Getting Started docs](https://docs.spring.io/spring-ai/reference/getting-started.html) — BOM artifact IDs, multi-provider setup (HIGH)
- [Spring AI Anthropic docs](https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html) — Starter artifact ID, model IDs, config properties (HIGH)
- [Spring AI ChatClient docs](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — Multi-provider qualifier pattern, advisor API (HIGH)
- [Spring AI MCP server annotations docs](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) — @McpTool usage (HIGH)
- [Spring AI PgVector docs](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html) — Starter coords, config properties (HIGH)
- [Spring Boot 3.5.13 blog](https://spring.io/blog/2026/03/26/spring-boot-3-5-13-available-now/) — Latest 3.5 patch confirmed (HIGH)
- [finmath-lib GitHub](https://github.com/finmath/finmath-lib) — GBM/Heston/Merton MC support confirmed (HIGH)
- [finmath-lib Maven Central](https://central.sonatype.com/artifact/net.finmath/finmath-lib) — Version 6.1.7 confirmed (HIGH)
- [Hipparchus GitHub releases](https://github.com/Hipparchus-Math/hipparchus/releases) — Version 4.0.3, February 2025 (HIGH)
- [Hipparchus stat docs](https://www.hipparchus.org/hipparchus-stat/index.html) — OLS, covariance, correlation confirmed (HIGH)
- [hipparchus-core Maven Central](https://central.sonatype.com/artifact/org.hipparchus/hipparchus-core) — 4.0.3 confirmed (HIGH)
- [strata-measure Maven Central](https://central.sonatype.com/artifact/com.opengamma.strata/strata-measure) — 2.12.71 confirmed; not recommended for this use case (MEDIUM)
- [Finnhub Kotlin client Maven](https://central.sonatype.com/artifact/io.finnhub/kotlin-client) — 2.0.22 (HIGH)
- [Java 25 vs 21 migration guide](https://www.javacodegeeks.com/2026/04/java-25-vs-21-lts-the-migration-decision-framework-teams-are-avoiding.html) — Spring Boot 3.5 Java 24 ceiling; Java 25 needs Boot 4 (MEDIUM)
- [vue-echarts npm](https://www.npmjs.com/package/vue-echarts) — Vue 3 only in v7 (HIGH)
- [Context7 MCP setup](https://github.com/pleaseai/context7) — `@upstash/context7-mcp` package name confirmed (HIGH)
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.

### Build Environment (Windows host — verified 2026-06-07)

- **JDK 21 (required):** Eclipse Temurin 21.0.11 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`. `JAVA_HOME` is set (User scope). The host's default `java` on PATH may still be JDK 11 in already-open shells — **always set `JAVA_HOME` before host Java builds.**
  - bash: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"`
  - PowerShell: `$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"`
- **Maven/Gradle:** NOT installed standalone. Use the project's Maven wrapper `./mvnw` (bash) / `.\mvnw.cmd` (PowerShell) generated by Spring Initializr — it downloads Maven on first run. Generate the Spring Boot project with the wrapper included.
- **Node:** v22.18.0, **npm:** 11.12.0 — available on PATH for the Vue frontend.
- **Docker:** 28.0.4 + **Compose** v2.34.0 — available. Per project constraints, the full stack runs via `docker compose up`; backend container uses an `eclipse-temurin:21` base.
- **Platform:** Windows 11 / PowerShell 5.1 + Git bash (both tool shells available). Prefer `BigDecimal` for money; pin Spring AI BOM to 1.1.6.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->



<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
