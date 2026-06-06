# Architecture Research

**Domain:** AI-augmented quantitative portfolio & market-intelligence dashboard
**Researched:** 2026-06-07
**Confidence:** HIGH (Spring AI docs verified via official reference; patterns verified against official Spring docs and github issues)

---

## Standard Architecture

### System Overview

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          BROWSER CLIENT                                  │
│  ┌─────────────┐  ┌──────────────┐  ┌────────────┐  ┌───────────────┐  │
│  │ Dashboard   │  │  AI Panels   │  │  Q&A Chat  │  │  BYO-Key      │  │
│  │ Charts      │  │  Explain /   │  │  (RAG)     │  │  Popup        │  │
│  │ ECharts     │  │  Commentary  │  │            │  │  (session)    │  │
│  └──────┬──────┘  └──────┬───────┘  └─────┬──────┘  └───────┬───────┘  │
│         └────────────────┴────────────────┴─────────────────┘          │
│                          Vue 3 Composition API / Pinia                   │
│                          Axios  →  REST + SSE                            │
└──────────────────────────────┬──────────────────────────────────────────┘
                               │ HTTP/SSE
┌──────────────────────────────▼──────────────────────────────────────────┐
│                     SPRING BOOT MODULAR MONOLITH                         │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                   API / Web Layer (REST controllers)              │   │
│  │   /api/portfolio  /api/ai  /api/rag  /api/analytics  /api/auth   │   │
│  └────────┬──────────────┬──────────────┬──────────────┬────────────┘   │
│           │              │              │              │                 │
│  ┌────────▼──────┐ ┌─────▼──────┐ ┌────▼──────┐ ┌────▼──────────────┐  │
│  │  portfolio    │ │  ai        │ │ analytics │ │ marketdata        │  │
│  │  module       │ │  module    │ │ module    │ │ adapter module    │  │
│  │               │ │            │ │           │ │                   │  │
│  │ holdings      │ │ ChatClient │ │ quant     │ │ @Tool live quotes │  │
│  │ transactions  │ │ strategy   │ │ engine    │ │ Alpha Vantage /   │  │
│  │ P&L           │ │ (demo/live)│ │ MonteCarlo│ │ Polygon / Yahoo   │  │
│  │ allocation    │ │ RAG advisor│ │ Sharpe    │ │                   │  │
│  │               │ │ tools wiring│ │ VaR/beta │ │ demo: canned data │  │
│  └────────┬──────┘ └─────┬──────┘ └────┬──────┘ └────┬──────────────┘  │
│           │              │              │              │                 │
│  ┌────────▼──────────────▼──────────────▼──────────────▼────────────┐   │
│  │                   persistence module                              │   │
│  │   Spring Data JPA (Postgres)     PgVectorStore (pgvector ext)     │   │
│  └────────────────────────────────────────────────────────────────┬─┘   │
│                                                                    │     │
│  ┌─────────────────────────────────────────────────────────────┐   │     │
│  │               @McpTool product MCP server                   │   │     │
│  │   (embedded via spring-ai-starter-mcp-server)               │   │     │
│  │   exposes: get_portfolio_summary, get_risk_metrics,          │   │     │
│  │            get_position_analysis, query_filings             │   │     │
│  └─────────────────────────────────────────────────────────────┘   │     │
└────────────────────────────────────────────────────────────────────┼────┘
                                                                      │
┌────────────────────────────────────────────────────────────────────▼────┐
│                        DATA LAYER                                        │
│                                                                          │
│   ┌─────────────────────────┐   ┌────────────────────────────────────┐  │
│   │  PostgreSQL + pgvector  │   │  Seeded fixtures (Flyway/SQL)      │  │
│   │  - portfolio tables     │   │  - demo users + portfolios         │  │
│   │  - vector_store (RAG)   │   │  - seeded AI responses (JSON)      │  │
│   │  - filings metadata     │   │  - pre-chunked embeddings (demo)   │  │
│   └─────────────────────────┘   └────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────┘
```

### External integrations (live mode only)

```
Spring Boot (ai module)
    │
    ├──▶ Anthropic API  (claude-3-5-sonnet or claude-opus-4)
    ├──▶ OpenAI API     (gpt-4o / gpt-4o-mini)
    └──▶ Market-data API (Alpha Vantage / Polygon — injected as @Tool)
```

---

## Module Boundaries

### Recommendation: Spring Modulith (not Maven multi-module)

For a single-developer portfolio project, Spring Modulith is the right call over Maven multi-module Maven subprojects. Rationale:

- Spring Modulith enforces boundaries at the package level; no build configuration overhead.
- It can auto-generate a module dependency diagram (ArchUnit-backed), which is itself a portfolio artifact.
- The code structure reads as a clean domain-driven design without the scaffolding noise of five separate `pom.xml` files.
- A Maven multi-module setup's main value is parallel team compilation; that benefit is zero for one developer.
- Spring Modulith can be extracted to Maven modules later if needed — the reverse is harder.

Boundary enforcement: annotate the top-level `@SpringBootApplication` class with `@ApplicationModules` and add a test that calls `ApplicationModules.of(QuantLensApp.class).verify()`. That test becomes a living contract.

### Package Layout (Spring Modulith)

```
com.quantlens
├── QuantLensApplication.java        (entry point + @ApplicationModules)
│
├── portfolio/                       (module: portfolio domain)
│   ├── api/                         (controllers — public API surface)
│   ├── domain/                      (entities, value objects)
│   ├── service/                     (portfolio CRUD, P&L calc)
│   └── persistence/                 (repositories)
│
├── analytics/                       (module: quant engine)
│   ├── api/
│   ├── montecarlo/                  (GBM, Heston, jump-diffusion, bootstrap)
│   ├── risk/                        (Sharpe, VaR, beta, vol, correlation)
│   ├── attribution/                 (factor/causal attribution)
│   └── arbitrage/                   (pairs, stat-arb, parity scanners)
│
├── ai/                              (module: Spring AI layer)
│   ├── api/                         (AI REST endpoints — chat, commentary)
│   ├── chat/                        (ChatClientStrategy + DemoModeAdvisor)
│   ├── rag/                         (ingestion service, QuestionAnswerAdvisor)
│   ├── commentary/                  (daily portfolio commentary generation)
│   └── structured/                  (DTOs for structured output → charts)
│
├── marketdata/                      (module: external market data adapter)
│   ├── api/
│   ├── client/                      (HTTP client to market-data provider)
│   ├── tools/                       (@Tool-annotated methods for ChatClient)
│   └── demo/                        (canned quote responses for demo mode)
│
├── mcp/                             (module: product MCP server)
│   └── tools/                       (@McpTool-annotated beans)
│
├── auth/                            (module: security / user identity)
│   ├── config/                      (SecurityFilterChain, demo user loader)
│   └── session/                     (LlmKeySessionHolder — stores BYO key)
│
└── shared/                          (non-module: cross-cutting)
    ├── dto/                         (shared response envelopes)
    └── config/                      (Jackson, CORS, OpenAPI)
```

**Inter-module rules enforced by Modulith:**
- `ai` depends on `portfolio` (reads positions) and `analytics` (reads metrics) and `marketdata` (registers tools).
- `analytics` depends on `portfolio` (reads holdings/prices). No upward dependency.
- `marketdata` has no module dependencies — pure adapter.
- `mcp` depends on `portfolio` and `analytics` (exposes their data via MCP protocol).
- `auth` has no module dependencies other than `shared`.

---

## The Demo-Mode / Live-Mode Seam

This is the most architecturally interesting decision in the project. The goal: zero-config demo that looks real, real AI the moment a key is pasted.

### Where the toggle lives

The toggle is a **custom Spring AI `CallAdvisor`** named `DemoModeAdvisor`, placed first in every `ChatClient` advisor chain. It intercepts `adviseCall()` before the request reaches any provider-specific model. The advisor checks a session-scoped bean (`LlmKeySessionHolder`) for a key. If no key: return a seeded response directly without calling `callAdvisorChain.nextCall()`. If a key exists: pass through unchanged.

This design keeps the seam in one place. The controllers, the `ChatClient`, and the downstream advisors (RAG, memory) are all unaware of demo mode.

```
┌──────────────────────────────────────────────────────────┐
│  ChatClient (per-request call)                           │
│                                                          │
│  Advisor chain (in order):                               │
│  1. DemoModeAdvisor  ──── key present? ──NO──▶ seeded    │
│          │                                    response   │
│         YES                                              │
│          ▼                                               │
│  2. QuestionAnswerAdvisor (RAG, only if live)            │
│          ▼                                               │
│  3. MessageChatMemoryAdvisor (conversation history)      │
│          ▼                                               │
│  4. ChatModel (Anthropic or OpenAI, chosen at runtime)   │
└──────────────────────────────────────────────────────────┘
```

### Session key holder

```java
// auth/session/LlmKeySessionHolder.java
@Component
@SessionScope                        // Spring-managed HTTP session bean
public class LlmKeySessionHolder {
    private String provider;         // "anthropic" | "openai"
    private String apiKey;           // never persisted; lives in HTTP session only

    public boolean hasKey() { return apiKey != null && !apiKey.isBlank(); }
    // getters/setters
}
```

The frontend BYO-key popup POSTs to `/api/auth/llm-key` with `{provider, apiKey}`. The controller puts them into `LlmKeySessionHolder`. The session lives in server memory (no DB). On browser close or logout the session — and the key — dies.

### ChatClient provider selection (multi-provider)

Spring AI's `ChatClient.Builder.mutate()` pattern is used to build a provider-specific client at request time without re-constructing everything:

```java
// ai/chat/ChatClientStrategy.java
@Service
public class ChatClientStrategy {

    private final OpenAiChatModel openAiModel;
    private final AnthropicChatModel anthropicModel;
    private final DemoModeAdvisor demoAdvisor;
    private final QuestionAnswerAdvisor ragAdvisor;
    private final MessageChatMemoryAdvisor memoryAdvisor;

    // Builds a ChatClient wired for this request's context.
    public ChatClient forSession(LlmKeySessionHolder keyHolder) {
        ChatModel model = resolveModel(keyHolder);
        return ChatClient.builder(model)
            .defaultAdvisors(demoAdvisor, ragAdvisor, memoryAdvisor)
            .build();
    }

    private ChatModel resolveModel(LlmKeySessionHolder keyHolder) {
        if (!keyHolder.hasKey()) return openAiModel;  // irrelevant; demo advisor fires first

        return switch (keyHolder.getProvider()) {
            case "anthropic" -> anthropicModel.mutate()
                  .defaultOptions(AnthropicChatOptions.builder()
                      .apiKey(keyHolder.getApiKey()).build())
                  .build();
            case "openai" -> openAiModel.mutate()
                  .openAiApi(openAiApi.mutate().apiKey(keyHolder.getApiKey()).build())
                  .build();
            default -> throw new IllegalArgumentException("Unknown provider");
        };
    }
}
```

**Important caveat (verified via Spring AI GitHub issue #2731 and #6150):** Spring AI 2.0.0-M7 tightened API key requirements at construction time. Using `model.mutate().apiKey(...)` is the supported path for per-request key injection without persisting. Avoid relying on `NoopApiKey` — it was removed in 2.0.0-M7. The `mutate()` path is stable and verified against current docs.

### Seeded responses storage and selection

Seeded responses are stored as **JSON files in `src/main/resources/seeds/`**, loaded once at startup into a `Map<SeedKey, String>` in `DemoModeAdvisor`. Each `SeedKey` is a record of `{queryCategory, subjectId}` (e.g., `{EXPLAIN_POSITION, "AAPL"}`).

The `DemoModeAdvisor` extracts a category and optional subject from `adviseContext` parameters (populated by controllers before calling ChatClient). It does a direct lookup — no fuzzy matching in demo mode. This avoids any dependency on an embedding model for demo operation.

```
src/main/resources/seeds/
├── explain_position_AAPL.json
├── explain_position_MSFT.json
├── daily_commentary_demo.json
├── rag_10k_AAPL_2023.json
├── portfolio_qa_sharpe.json
└── ...
```

Each file is a JSON object containing `{"content": "...authored AI-looking text..."}`.

For RAG demo mode specifically: the vector store is pre-populated at startup via a `SeedDataLoader` `@Component` that checks if embeddings already exist in the DB before inserting. The embeddings for demo are **pre-computed using a real embedding model once** and stored in the Flyway seed SQL as a binary/JSON column — this means zero embedding calls at demo runtime. Alternatively (simpler): seed the raw text chunks into the vector store using a local Ollama embedding model during the Docker startup sequence, so no external key is ever needed for the initial embedding.

---

## RAG Pipeline

### Ingestion (one-time, admin-triggered or startup)

```
PDF (10-K / filing)
    │
    ▼
PagePdfDocumentReader          (Spring AI ETL — Apache PdfBox)
    │  List<Document>
    ▼
KeywordMetadataEnricher        (optional — uses ChatModel, skip in demo)
    │  List<Document> with metadata: {ticker, year, section}
    ▼
TokenTextSplitter              (chunkSize=800, minChunkSizeChars=400)
    │  List<Document> (chunks)
    ▼
PgVectorStore.write()          (EmbeddingModel → float[] → pgvector)
    │
    ▼
PostgreSQL vector_store table
```

The ingestion runs as a `DocumentIngestionService` with a REST endpoint `POST /api/admin/ingest` for adding new filings. In demo mode, startup seeds the vector store from pre-computed data.

### Retrieval (per AI query)

The `QuestionAnswerAdvisor` (built-in Spring AI) is added to the ChatClient advisor chain. It:
1. Takes the user's question from the prompt
2. Calls `vectorStore.similaritySearch(SearchRequest.query(question).topK(5))`
3. Appends the retrieved chunks as context before the prompt reaches the model

In **demo mode**, the `DemoModeAdvisor` fires before the `QuestionAnswerAdvisor` and short-circuits the chain, so no embedding calls are made at retrieval time either.

### Embedding model choice

- **Live mode:** OpenAI `text-embedding-3-small` (1536 dimensions) or Anthropic embeddings — whichever provider the session key is for.
- **Demo mode:** embeddings are pre-seeded (no model calls needed at runtime).
- **Local dev without key:** configure a local Ollama embedding model (`nomic-embed-text`) via Spring AI's Ollama starter — zero cost, zero network.

---

## Tool Calling for Live Quotes

### @Tool approach (native Spring AI, within main app)

```java
// marketdata/tools/MarketDataTools.java
@Component
public class MarketDataTools {

    private final MarketDataClient client;
    private final DemoQuoteProvider demoProvider;
    private final LlmKeySessionHolder keyHolder;

    @Tool(description = "Get the current price, volume, and daily change for a stock ticker symbol")
    public StockQuote getLiveQuote(
        @ToolParam(description = "Stock ticker symbol, e.g. AAPL") String ticker) {

        if (!keyHolder.hasKey()) {
            return demoProvider.getQuote(ticker);   // canned quote
        }
        return client.fetchQuote(ticker);            // real HTTP call
    }
}
```

The `MarketDataTools` bean is registered with the ChatClient as a default tool in `ChatClientStrategy.forSession()`.

### @McpTool product MCP server (embedded module)

The MCP server is embedded in the same Spring Boot process via `spring-ai-starter-mcp-server`. It exposes higher-level portfolio capabilities for external MCP clients (e.g., a future Claude Desktop integration, or the dev Claude Code environment):

```java
// mcp/tools/PortfolioMcpTools.java
@Component
public class PortfolioMcpTools {

    @McpTool(name = "get_portfolio_summary",
             description = "Returns current portfolio allocation, total value, and top holdings")
    public PortfolioSummaryDto getPortfolioSummary(
        @McpToolParam(description = "Demo user ID or 'demo'") String userId) { ... }

    @McpTool(name = "get_risk_metrics",
             description = "Returns Sharpe ratio, VaR, beta, and volatility for a portfolio")
    public RiskMetricsDto getRiskMetrics(
        @McpToolParam(description = "Demo user ID") String userId) { ... }

    @McpTool(name = "explain_position",
             description = "Returns AI-generated explanation of a specific holding")
    public String explainPosition(
        @McpToolParam(description = "Ticker symbol") String ticker,
        @McpToolParam(description = "Demo user ID") String userId) { ... }
}
```

**MCP server transport:** `spring.ai.mcp.server.protocol=STREAMABLE` (HTTP streamable transport — works over regular HTTP without WebSocket). The MCP server listens on `/mcp` by default, alongside the REST API.

**Relationship to main app:** same JVM process, same Postgres connection. The `@McpTool` beans call into the same `PortfolioService` and `AnalyticsService` as the REST controllers. This is intentional — the MCP server is not a separate service. It is a protocol adapter layer on top of the existing domain services.

---

## Frontend-Backend Contract

### REST API design

All AI endpoints return **structured Java DTOs** mapped to chart-ready JSON. The Vue layer never parses AI prose to build a chart — all chart data arrives as a typed DTO.

| Endpoint | Response DTO | Chart target |
|----------|-------------|--------------|
| `GET /api/portfolio/allocation` | `AllocationDto` (labels[], values[]) | Pie/donut chart |
| `GET /api/portfolio/pnl` | `PnlSeriesDto` (dates[], values[]) | Line chart |
| `GET /api/analytics/risk` | `RiskMetricsDto` (sharpe, var, beta, vol) | Stat cards |
| `GET /api/analytics/correlation` | `CorrelationMatrixDto` (tickers[], matrix[][]) | Heatmap |
| `GET /api/analytics/forecast/{model}` | `ForecastFanDto` (dates[], paths[][], percentiles) | Fan chart |
| `POST /api/ai/explain` | `AiExplanationDto` (text, keyPoints[], sentiment) | AI panel |
| `GET /api/ai/commentary` | `CommentaryDto` (headline, body, bulletPoints[]) | Commentary card |
| `POST /api/ai/chat` | SSE stream of `ChatChunkDto` | Chat UI |
| `POST /api/rag/query` | `RagResponseDto` (answer, sources[]) | Q&A panel |

### Structured output from Spring AI

For non-streaming AI endpoints, use `ChatClient.call().entity(MyDto.class)`. Spring AI generates the JSON schema from the record/class definition and instructs the LLM to conform. The controller returns the DTO directly — Spring MVC serializes it to JSON, Vue deserializes it.

```java
// Example: daily commentary
@GetMapping("/api/ai/commentary")
public CommentaryDto getDailyCommentary(@SessionAttribute LlmKeySessionHolder key) {
    return chatClientStrategy.forSession(key)
        .prompt()
        .system("You are a portfolio analyst. Return structured JSON only.")
        .user(u -> u.text("Generate today's portfolio commentary for: {summary}")
                    .param("summary", portfolioService.getSummary()))
        .call()
        .entity(CommentaryDto.class);
}
```

For streaming chat (`/api/ai/chat`), use Server-Sent Events (SSE). Spring's `SseEmitter` or WebFlux `Flux<ServerSentEvent>` streams token chunks. Vue uses the native `EventSource` API or `@microsoft/fetch-event-source` for POST-based SSE.

### Vue API layer

```
src/
├── api/
│   ├── portfolio.ts     (typed Axios calls → AllocationDto, PnlSeriesDto, etc.)
│   ├── analytics.ts     (typed Axios calls → RiskMetricsDto, ForecastFanDto, etc.)
│   ├── ai.ts            (typed Axios + SSE → AiExplanationDto, chat stream)
│   └── auth.ts          (login, BYO key submission)
├── stores/
│   ├── portfolio.ts     (Pinia — holds allocation, pnl, positions)
│   ├── analytics.ts     (Pinia — holds risk metrics, forecasts)
│   └── ai.ts            (Pinia — holds commentary, chat history)
└── components/
    ├── charts/           (ECharts wrappers consuming store DTOs directly)
    └── ai/               (AI panels consuming store state)
```

TypeScript interfaces in `src/types/` mirror the Java DTOs exactly. OpenAPI code generation (`springdoc-openapi` + `openapi-generator-cli`) is the recommended path to keep them in sync, but manual mirroring is acceptable for a portfolio piece.

---

## Auth Seam

### Phase 1 (demo): In-memory users + session cookie

```java
// auth/config/SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/actuator/health").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form.loginPage("/login").permitAll())
            .sessionManagement(s -> s.sessionCreationPolicy(ALWAYS))
            .csrf(AbstractHttpConfigurer::disable)   // disable for REST API demo
            .build();
    }

    @Bean
    public UserDetailsService demoUsers() {
        // Seeded from application-demo.yml or Flyway data
        return new InMemoryUserDetailsManager(
            User.withUsername("alice").password("{noop}demo").roles("USER").build(),
            User.withUsername("bob").password("{noop}demo").roles("USER").build()
        );
    }
}
```

### OAuth2 upgrade path (documented, not built)

To drop in OAuth2 later, the `SecurityConfig` changes are:

1. Add `spring-boot-starter-oauth2-client` dependency.
2. Replace `InMemoryUserDetailsManager` with an `OAuth2UserService` that maps the OAuth principal to a local `AppUser` entity.
3. Replace `formLogin` with `oauth2Login`.
4. Add provider config in `application.yml` (`spring.security.oauth2.client.registration.google.*`).
5. The `LlmKeySessionHolder` session-scoped bean works unchanged — it is independent of the authentication mechanism.

The `AppUser` entity should be created from the start (even for demo), with a `externalId` column nullable for v1. When OAuth is wired, the `externalId` maps to the provider's sub claim.

---

## Containerization Topology

### docker-compose.yml (base — always runs)

```yaml
services:

  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: quantlens
      POSTGRES_USER: quantlens
      POSTGRES_PASSWORD: quantlens_dev
    volumes:
      - postgres_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quantlens"]
      interval: 5s
      timeout: 5s
      retries: 5

  api:
    build: ./backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/quantlens
      SPRING_PROFILES_ACTIVE: demo   # or dev / prod
      # AI keys are NOT in compose — injected via session only
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy

  web:
    build: ./frontend
    ports:
      - "5173:80"
    depends_on:
      - api
    environment:
      VITE_API_BASE_URL: http://localhost:8080

volumes:
  postgres_data:
```

### Spring profiles

| Profile | Purpose | Key differences |
|---------|---------|-----------------|
| `demo` | Zero-config public demo | In-memory users, seeded AI responses, pre-seeded vector store, no external key required |
| `dev` | Local developer environment | Real DB, real Spring AI calls when key provided, detailed logging, Actuator endpoints open |
| `prod` | Hypothetical deployed version | Externalized secrets, HTTPS, stricter security, OAuth ready |

The profile controls:
- Whether `SeedDataLoader` runs on startup (demo + dev first run)
- Whether `DemoModeAdvisor` defaults to `alwaysDemo = true` (demo profile) or `alwaysDemo = false` (dev/prod — demo fires only when session has no key)
- Logging verbosity
- Actuator exposure

### MCP server in compose

The MCP server is **not a separate container**. It runs inside the `api` container on the same port as the REST API (multiplexed via Spring's dispatcher servlet). External MCP clients connect to `http://localhost:8080/mcp`. This avoids unnecessary container complexity for a portfolio project while still demonstrating the MCP capability.

---

## Data Flow

### 1. Standard chart load (portfolio data)

```
Vue component mount
    → Pinia action: fetchAllocation()
    → Axios GET /api/portfolio/allocation
    → PortfolioController.getAllocation()
    → PortfolioService.computeAllocation(userId)
    → PortfolioRepository (JPA query)
    → AllocationDto back through chain
    → Pinia store updated
    → ECharts reactive binding re-renders pie chart
```

### 2. AI explanation panel — demo mode

```
User clicks "Explain AAPL position"
    → Vue component emits explainPosition("AAPL")
    → Pinia action: fetchExplanation("AAPL")
    → Axios POST /api/ai/explain {ticker: "AAPL"}
    → AiController.explainPosition()
    → chatClientStrategy.forSession(keyHolder)    // keyHolder.hasKey() = false
    → ChatClient.prompt().advisors(demoAdvisor, ragAdvisor, memAdvisor).call()
    → DemoModeAdvisor.adviseCall()
        → keyHolder.hasKey() = false
        → lookup seeds["EXPLAIN_POSITION"]["AAPL"]
        → return seeded ChatClientResponse  (no LLM call)
    → AiExplanationDto deserialized from seeded JSON
    → 200 OK → Pinia → AI panel renders
```

### 3. AI explanation panel — live mode

```
User submits BYO key popup
    → POST /api/auth/llm-key {provider: "openai", apiKey: "sk-..."}
    → LlmKeySessionHolder.setKey(provider, apiKey)  (HTTP session only)

User clicks "Explain AAPL position"
    → (same as above to AiController)
    → chatClientStrategy.forSession(keyHolder)    // keyHolder.hasKey() = true
    → ChatClient built with OpenAI model mutated with session key
    → DemoModeAdvisor.adviseCall()
        → keyHolder.hasKey() = true → pass through
    → QuestionAnswerAdvisor.adviseCall()
        → similaritySearch("explain AAPL position risk exposure")
        → 5 relevant filing chunks retrieved from pgvector
        → appended to prompt context
    → MessageChatMemoryAdvisor runs
    → OpenAI API called with enriched prompt
    → Structured output → AiExplanationDto
    → 200 OK → AI panel renders with real response
```

### 4. Forecast fan chart

```
Vue component: ForecastFanChart mounts with prop model="GBM"
    → Axios GET /api/analytics/forecast/GBM
    → AnalyticsController.getForecast("GBM")
    → QuantAnalyticsService.runMonteCarlo(model=GBM, portfolio, N=1000, T=252)
    → Returns ForecastFanDto {dates[], paths[][], percentileBands{p10, p50, p90}}
    → ECharts fan chart: median line + shaded percentile bands
```

This flow is **entirely in Java** — no AI call needed. The quant engine runs the Monte Carlo simulation directly. The AI layer can optionally narrate the result in a separate "commentary" panel.

### 5. RAG query

```
User types in Q&A chat: "What does Apple say about AI risk in their latest 10-K?"
    → SSE POST /api/rag/query {question: "..."}
    → RagController streams response
    → ChatClient.prompt().user(question).stream()
    → DemoModeAdvisor: pass (if key present) or seeded Q&A response
    → QuestionAnswerAdvisor: pgvector similarity search → top 5 chunks from AAPL 10-K
    → Prompt augmented with filing context
    → Model generates answer with citations
    → SSE stream of tokens → Vue EventSource renders in real time
```

---

## Suggested Build Order

The build order is determined by dependency direction: lower-layer modules must exist before higher-layer modules that consume them.

```
Phase 1: Foundation
├── Docker compose (db + api skeleton + web skeleton)
├── Flyway schema migrations (portfolio tables)
├── Seeded demo users + demo portfolios (SQL fixtures)
└── Spring Security (in-memory auth, formLogin, session)

Phase 2: Portfolio domain
├── Portfolio entities + repositories
├── Portfolio service (holdings, transactions, P&L, allocation)
└── Portfolio REST API + AllocationDto, PnlSeriesDto

Phase 3: Vue frontend scaffold + chart bindings
├── Vue 3 project (Vite), Pinia stores, Axios API layer
├── Dashboard layout + ECharts allocation + P&L charts
└── BYO-key popup component (stores key in session, no AI yet)

Phase 4: Quant analytics engine
├── Risk metrics (Sharpe, VaR, beta, volatility, correlation)
├── Monte Carlo + stochastic models (GBM first, then Heston/jump-diffusion)
├── Factor attribution + arbitrage scanners
└── Analytics REST API + chart DTOs (RiskMetricsDto, ForecastFanDto, CorrelationMatrixDto)

Phase 5: Spring AI core — demo mode first
├── DemoModeAdvisor + LlmKeySessionHolder + ChatClientStrategy
├── Seeded responses (JSON files in resources/seeds/)
├── AiController (explain-position, commentary — demo mode working)
└── Structured output DTOs (AiExplanationDto, CommentaryDto)

Phase 6: RAG pipeline
├── DocumentIngestionService (PagePdfDocumentReader → TokenTextSplitter → PgVectorStore)
├── pgvector schema + pre-seeded demo embeddings (Flyway or startup loader)
├── QuestionAnswerAdvisor wired into ChatClient chain
└── Q&A chat endpoint (SSE streaming)

Phase 7: Live mode + market data tools
├── MarketDataTools (@Tool for live quotes)
├── Multi-provider ChatClient wiring (OpenAI + Anthropic via mutate())
├── LLM key session endpoint (POST /api/auth/llm-key)
└── Integration test: key → real LLM call path

Phase 8: MCP server
├── spring-ai-starter-mcp-server dependency
├── PortfolioMcpTools (@McpTool beans)
└── Dev MCP config for Claude Code (.mcp.json)

Phase 9: Polish + documentation
├── README screenshots (all demo mode)
├── Stochastic model rationale writeup
├── OAuth upgrade path documentation
└── OpenAPI spec + ArchUnit Modulith verification test
```

**Why this order:**
- Phases 1-3 yield a visually impressive screenshot-ready dashboard with no AI at all, establishing the quant-finance credential early.
- Phase 4 (quant engine) is the hardest intellectually; build it before AI so the AI layer has real data to narrate.
- Phase 5 establishes the demo-mode advisor first — this means AI panels work in screenshots before any real LLM is wired.
- Phase 6 (RAG) depends on pgvector being configured and on having at least one real filing PDF.
- Phase 7 (live mode) depends on the advisor chain being proven in demo mode.
- Phase 8 (MCP) is additive — it wraps already-working services.
- Phase 9 is the "portfolio polish" pass that makes the repository look like a senior engineer built it.

---

## Anti-Patterns to Avoid

### Anti-Pattern 1: Persisting API Keys

**What:** Storing user-supplied LLM keys in the database or anywhere server-side beyond the HTTP session.
**Why bad:** Security liability; also out of scope per PROJECT.md.
**Do this instead:** `@SessionScope` bean (`LlmKeySessionHolder`) that lives and dies with the HTTP session.

### Anti-Pattern 2: Fat AI Controller

**What:** Putting demo-mode logic, provider selection, advisor wiring, and structured-output parsing all in one REST controller.
**Why bad:** Impossible to test; blurs the boundary between HTTP concerns and AI orchestration.
**Do this instead:** Controllers call `ChatClientStrategy.forSession(key)` and `call().entity(Dto.class)`. Demo logic lives entirely in `DemoModeAdvisor`.

### Anti-Pattern 3: Hand-rolled RAG retrieval

**What:** Writing custom SQL to retrieve vectors, manually computing cosine similarity, building prompt templates by string concatenation.
**Why bad:** Spring AI's `QuestionAnswerAdvisor` handles retrieval + prompt augmentation correctly and is the canonical pattern recruiters reading this code expect to see.
**Do this instead:** `QuestionAnswerAdvisor.builder(vectorStore).build()` — one line added to the advisor chain.

### Anti-Pattern 4: Demo embeddings via live API at startup

**What:** Calling OpenAI embeddings API during Docker startup to embed demo documents, so the app fails to start without a key.
**Why bad:** Breaks the "zero-config demo" constraint. Anyone `docker compose up` without a key gets a crash.
**Do this instead:** Pre-compute demo embeddings once using a local Ollama model (or keep them as a base64 Flyway seed); the startup loader checks if embeddings already exist before inserting.

### Anti-Pattern 5: Maven multi-module before domain is stable

**What:** Creating 5 Maven submodules before the domain boundaries are understood.
**Why bad:** Constant pom.xml overhead; refactoring a module boundary requires moving files across module roots.
**Do this instead:** Spring Modulith packages with `@ApplicationModule` enforcement. The module boundaries are visible in the package tree and verified by a unit test. Extract to Maven submodules only if a multi-team or multi-deployment scenario actually materializes.

### Anti-Pattern 6: Single ChatClient bean for all scenarios

**What:** One `@Bean ChatClient chatClient(...)` wired with both `anthropicChatModel` and `openAiChatModel` somehow, hoping to switch between them.
**Why bad:** Spring AI does not support runtime provider switching through a single client instance. The `ChatClient` is bound to one `ChatModel` at build time.
**Do this instead:** `ChatClientStrategy.forSession()` builds a new `ChatClient` per request using `.mutate()` on the appropriate model. This is idiomatic Spring AI for multi-provider setups (verified against Spring AI docs and GitHub issue #3361).

---

## Scaling Considerations

This is a portfolio piece. Scaling is not the primary concern. For completeness:

| Scale | Approach |
|-------|---------|
| 1-10 demo viewers | Single JVM, single Postgres, in-memory session. The current design. |
| 10-100 concurrent | JVM handles it. Postgres connection pool (HikariCP defaults). Add Spring Cache on analytics endpoints. |
| 100-1000 concurrent | Externalize sessions to Redis (`spring-session-data-redis`). Add a CDN in front of the Vue static build. The `LlmKeySessionHolder` is already `@SessionScope` so it works with Redis sessions without code changes. |
| 1000+ | The quant engine (Monte Carlo) is CPU-bound; move to a separate thread pool or async endpoint. Consider splitting the analytics module to a separate service. Not needed for portfolio. |

The design's main scaling consideration is that **LLM calls are slow (1-10s)**. Use SSE streaming for chat responses so the browser shows tokens as they arrive rather than waiting for the full response.

---

## Integration Points

| External Service | Integration Pattern | Notes |
|-----------------|--------------------|----|
| Anthropic API | Spring AI `AnthropicChatModel` + `mutate()` for per-session key | Session key only; no config file key needed in demo |
| OpenAI API | Spring AI `OpenAiChatModel` + `mutate()` | Same pattern; `text-embedding-3-small` for live embeddings |
| Market data API (Alpha Vantage / Polygon) | `@Tool`-annotated `MarketDataClient` HTTP calls | Canned responses in demo mode; real calls in live mode |
| PostgreSQL + pgvector | Spring Data JPA + `PgVectorStore` | Single DB instance; pgvector extension enabled at startup |
| Claude Code (dev MCP) | `.mcp.json` config pointing to `http://localhost:8080/mcp` | Development tooling; same MCP server the product exposes |

---

## Sources

- Spring AI ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html
- Spring AI Advisors API: https://docs.spring.io/spring-ai/reference/api/advisors.html
- Spring AI ETL Pipeline: https://docs.spring.io/spring-ai/reference/api/etl-pipeline.html
- Spring AI Tool Calling: https://docs.spring.io/spring-ai/reference/api/tools.html
- Spring AI MCP Annotations: https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-overview.html
- Spring AI PgVector: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
- Spring AI Structured Output: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html
- Spring AI dynamic API keys (GitHub issue): https://github.com/spring-projects/spring-ai/issues/2731
- Spring AI 2.0.0-M7 auth breaking change: https://github.com/spring-projects/spring-ai/issues/6150
- Spring Modulith vs multi-module: https://bootify.io/multi-module/spring-modulith-vs-multi-module.html
- Spring AI MCP Boot Starters blog: https://spring.io/blog/2025/09/16/spring-ai-mcp-intro-blog/
- Spring Security in-memory auth: https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/in-memory.html

---

*Architecture research for: QuantLens AI Portfolio Dashboard*
*Researched: 2026-06-07*
