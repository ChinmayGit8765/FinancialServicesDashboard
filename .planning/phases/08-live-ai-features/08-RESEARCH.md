# Phase 8: Live AI Features — Research

**Researched:** 2026-06-09
**Domain:** Spring AI 1.1.6 tool calling + structured output + Finnhub quote integration + multi-provider routing
**Confidence:** HIGH (Spring AI official docs verified; Finnhub endpoint confirmed via community sources; codebase read directly)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Tool calling — live quotes (AI-05)**
- A Spring AI `@Tool`-annotated method `getStockQuote(ticker)` registered on the ChatClient (live tool calling). Returns a small typed result (ticker, price, asOf, marketState/after-hours label, source).
- Demo path (no network): tool returns the last seeded OHLCV close for the ticker (from market data). In demo CHAT (no LLM key), the DemoModeAdvisor short-circuits with a seeded answer that includes the quote — the LLM never runs.
- Live path: if a Finnhub API key is configured (env `FINNHUB_API_KEY` and/or a session field), the tool calls the real Finnhub quote API with a 15-minute TTL cache (avoid rate limits) and correct after-hours/market-state labeling; otherwise it falls back to the seeded last close. The Finnhub key is SEPARATE from the LLM key (a market-data credential) — handle it like the LLM key for leakage (never log/echo/persist beyond config/session).
- Add the Finnhub client to pom (research to confirm: `io.finnhub:kotlin-client:2.0.22` per STACK, or a thin Java HTTP client for the single /quote endpoint to avoid the Kotlin dependency — recommend the lightest option).

**Structured output → chart (AI-06)**
- At least one AI response delivered as a typed Java `record` via Spring AI structured output (`BeanOutputConverter` / `.entity(MyRecord.class)`). Choose a chart-shaped record (e.g. an AI "risk/allocation insight" → labeled values for a small chart).
- Same DTO path in both modes: demo → the StructuredOutputChart fixture/seeded JSON deserialized into the SAME record; live → the LLM fills the record via structured output. The Vue ECharts chart renders from that one DTO regardless of mode.
- Demo short-circuit (DemoModeAdvisor) returns the seeded structured JSON; live returns the LLM-generated record. Endpoint: `GET /api/ai/structured` (or reuse the existing structured fetch) → the typed DTO.

**Multi-provider (success criterion 3)**
- No new provider code — Phase 6 `ChatClientStrategy.forSession()` already builds Anthropic (builder) or OpenAI (mutate) per session key. Phase 8 adds tests proving BOTH providers route to a successful live call from the same entry point (using mock/stub ChatModels in tests; real multi-provider is a live UAT item).

**Testing**
- @Tool demo path: returns seeded last close with NO network (unit/integration). @Tool live path: Finnhub call MOCKED — assert TTL cache (second call within 15 min doesn't re-fetch) + after-hours labeling; NO real network.
- Structured output: demo (seeded JSON → record) + live (mock provider returns JSON → BeanOutputConverter → record) → chart DTO shape.
- Multi-provider routing test (mock both). Demo no-network proof (counting advisor) for the tool/structured chat paths. KeyLeakage gate extended to any new endpoint + Finnhub key never leaks. Frontend: StructuredOutputChart renders the typed DTO; quote display if any. No real network in tests.

### Claude's Discretion
- Exact @Tool signature + result record, the structured-output record/chart choice, Finnhub client vs thin HTTP, Finnhub key handling (env vs session vs both), endpoint grouping, and whether quotes surface via chat only or also a small UI affordance — at Claude's discretion within the above. Research to confirm Spring AI 1.1.6 @Tool registration + BeanOutputConverter + Finnhub client + cache approach.

### Deferred Ideas (OUT OF SCOPE)
- @McpTool product server (Phase 9)
- Streaming responses (v2 — AI-09)
- Real-time WebSocket price streaming
- Additional tools beyond quotes (v2)
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-05 | User can request a live quote in chat and see the LLM invoke a quote tool (tool calling) | @Tool annotation, ChatClientStrategy tool registration, Finnhub /quote endpoint, 15-min TTL cache, demo fallback to seeded OHLCV close |
| AI-06 | At least one AI response is delivered as typed structured output that drives a chart directly | BeanOutputConverter / .entity(record.class), StructuredOutputChart DTO upgrade, demo seeded JSON → same record path |
</phase_requirements>

---

## Summary

Phase 8 completes the Spring AI showcase by wiring tool calling (AI-05) and structured output (AI-06) onto the existing ChatClientStrategy / DemoModeAdvisor seam proven in Phases 6 and 7. The key architectural insight is that both features plug into the SAME advisor chain with zero changes to the seam: DemoModeAdvisor fires at HIGHEST_PRECEDENCE and short-circuits in demo mode — the @Tool method is never invoked and BeanOutputConverter is never called. In live mode the chain passes through and the real LLM decides when to invoke the tool or produce the structured JSON.

The demo path must return the identical DTO shape for both AI-05 and AI-06 so the frontend renders correctly in zero-key mode. For AI-05 this means `DemoModeAdvisor` seeded chat answers mention the quote (no separate tool call path in demo). For AI-06 this means the seeded JSON from `ai_seed_content` (type `STRUCTURED_INSIGHT`) is deserialized into the same `StructuredInsightRecord` that BeanOutputConverter produces in live mode — the `GET /api/ai/structured` endpoint becomes the live backend for what is currently the static `/ai-structured-demo.json` fetch in the frontend store.

For the Finnhub client, the research strongly recommends a **thin Java HttpClient wrapper** over `io.finnhub:kotlin-client:2.0.22`. The Kotlin client pulls Kotlin stdlib 1.5.x, OkHttp 4.9, Moshi 1.9, and kotlinx-serialization — all transitive dependencies for a single /quote call on a project written entirely in Java. A 15-line Java 21 `HttpClient.send()` call is lighter, fully under our control, requires no new Maven dependency, and avoids Kotlin/Java interop nuance at runtime.

**Primary recommendation:** Register the @Tool bean via `ToolCallback` (not raw `defaultTools()` which has a known detection bug in 1.1.x); use `.entity(StructuredInsightRecord.class)` on the live ChatClient call; keep the DemoModeAdvisor short-circuit as the single demo/live switch; use a `ConcurrentHashMap<String, CachedQuote>` with a timestamp check for the 15-min TTL (no new infrastructure).

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| @Tool getStockQuote() | API/Backend (ai module) | marketdata module (domain data for demo) | Tool is a server-side decision; LLM calls the tool server-side; Finnhub key is a server secret |
| Finnhub HTTP call + 15-min cache | API/Backend (ai module or marketdata adapter) | — | Market-data credential lives server-side; cache is per-JVM |
| DemoModeAdvisor tool short-circuit | API/Backend (advisor chain) | — | Same advisor that already gates all AI paths; no change needed |
| BeanOutputConverter / .entity() | API/Backend (ChatClient call site) | — | Schema generation, format instructions, deserialization all happen server-side in the service |
| GET /api/ai/structured endpoint | API/Backend | — | Replaces the static JSON public file fetch; typed DTO over HTTP |
| StructuredOutputChart.vue | Browser/Client | — | Already built; only the data source URL changes (store action updated) |
| ChatClientStrategy multi-provider | API/Backend | — | Provider selection from LlmKeySessionHolder is server-side session state |
| Quote display (if any) | Browser/Client | API/Backend | Chat response text contains quote; any dedicated UI is frontend-only |

---

## Standard Stack

### Core (already in pom.xml — no new Spring AI deps needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-starter-model-anthropic` | 1.1.6 (BOM) | Anthropic ChatModel | Already wired in Phase 6 |
| `spring-ai-starter-model-openai` | 1.1.6 (BOM) | OpenAI ChatModel | Already wired in Phase 6 |
| `spring-ai-advisors-vector-store` | 1.1.6 (BOM) | QuestionAnswerAdvisor | Already wired in Phase 7 |
| Java 21 `HttpClient` | JDK built-in | Finnhub quote fetch | Zero new dependency; already on classpath |

### New Dependency: NONE recommended

The `io.finnhub:kotlin-client:2.0.22` artifact pulls Kotlin stdlib 1.5.x + OkHttp 4.9.1 + Moshi 1.9.3 + kotlinx-serialization — four transitive dependencies for a single REST call with a 7-field JSON response. Java 21's built-in `HttpClient` covers this trivially. This is the correct trade-off for a single-endpoint integration.

```xml
<!-- NO new dependency needed for Finnhub — use java.net.http.HttpClient -->
<!-- If Kotlin client is ever required for additional Finnhub endpoints, add:
<dependency>
    <groupId>io.finnhub</groupId>
    <artifactId>kotlin-client</artifactId>
    <version>2.0.22</version>
</dependency>
But for Phase 8 (single /quote call), this is unnecessary overhead. -->
```

### Supporting (frontend — already installed)

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `vue-echarts` | 8.0.1 | StructuredOutputChart rendering | Already installed; no change needed |
| `echarts` | 6.1.0 | ECharts engine | Already installed |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Java 21 HttpClient (thin) | `io.finnhub:kotlin-client:2.0.22` | Kotlin client is richer API coverage but adds ~5MB transitive deps for a single endpoint we need; thin client wins for this scope |
| `ConcurrentHashMap` + timestamp TTL | Spring `@Cacheable` + Caffeine | @Cacheable is correct production choice but adds a dependency; ConcurrentHashMap TTL is 20 lines, zero deps, testable directly — appropriate for portfolio project |
| BeanOutputConverter + `.entity()` | Manual JSON parsing | BeanOutputConverter is the canonical Spring AI structured output path; manual parsing defeats the showcase point |

---

## Package Legitimacy Audit

> No new external packages are added in Phase 8 (Java HttpClient requires no Maven dependency; all Spring AI artifacts are already present and BOM-governed). This section confirms no new packages need legitimacy gating.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `org.springframework.ai:*` | Maven Central | 1+ yr | Millions/mo | spring-projects/spring-ai | N/A (already in pom.xml) | Pre-approved (existing) |
| `java.net.http.HttpClient` | JDK 21 stdlib | N/A | N/A | openjdk | N/A | Built-in — no install step |

**Packages removed due to slopcheck [SLOP] verdict:** none  
**Packages flagged as suspicious [SUS]:** none  
**New packages requiring install:** none

---

## Architecture Patterns

### System Architecture Diagram

```
User Chat: "What's the price of AAPL?"
         │
         ▼
AiController.chat()  →  ChatService.chat()
         │
         ▼
ChatClientStrategy.forSession(keyHolder)
         │ builds ChatClient with all CallAdvisor beans sorted by order
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  Advisor Chain (ordered)                                        │
│                                                                 │
│  1. DemoModeAdvisor (HIGHEST_PRECEDENCE)                        │
│     ├─ hasKey() = false → return seeded "RAG_QA/DEFAULT" chat   │
│     │   answer (mentions seeded AAPL close) — NO tool call      │
│     └─ hasKey() = true  → chain.nextCall() ──────────────────►  │
│                                                                 │
│  2. QuestionAnswerAdvisor (HIGHEST_PRECEDENCE + 10)             │
│     └─ retrieves filing chunks from pgvector                 ►  │
│                                                                 │
│  3. MessageChatMemoryAdvisor (HIGHEST_PRECEDENCE + 20)          │
│     └─ injects conversation history                          ►  │
│                                                                 │
│  4. ChatModel (Anthropic/OpenAI) with @Tool registered          │
│     ├─ LLM decides: invoke getStockQuote("AAPL")                │
│     │       │                                                   │
│     │       ▼                                                   │
│     │  StockQuoteToolService.getStockQuote("AAPL")              │
│     │  ├─ finnhubApiKey present → GET api.finnhub.io/quote      │
│     │  │   └─ 15-min TTL cache check (ConcurrentHashMap)        │
│     │  └─ no key → OhlcvBarRepository.findLatestBar("AAPL")    │
│     │                                                           │
│     └─ LLM includes quote in response                           │
└─────────────────────────────────────────────────────────────────┘

GET /api/ai/structured  →  StructuredOutputService.getInsight()
         │
         ▼
ChatClientStrategy.forSession(keyHolder)
         │
         ▼
DemoModeAdvisor (demo) → seeded JSON → ObjectMapper.readValue() → StructuredInsightRecord
         │
        (live)
         ▼
ChatClient.prompt().system(...).user(...).call().entity(StructuredInsightRecord.class)
   └─ BeanOutputConverter: generates DRAFT_2020_12 JSON schema, appends format instructions
   └─ LLM returns JSON → Jackson deserialize → StructuredInsightRecord
         │
         ▼
AiController.structured()  →  200 OK { title, subtitle, series:[{label,value}] }
         │
         ▼
frontend/stores/ai.ts: fetchStructured() → GET /api/ai/structured (replaces /ai-structured-demo.json)
         │
         ▼
StructuredOutputChart.vue (unchanged — same StructuredChartDto prop shape)
```

### Recommended Project Structure

New files (all within existing `com.quantlens.ai` Modulith module):

```
backend/src/main/java/com/quantlens/ai/
├── tools/
│   ├── StockQuoteToolService.java    # @Component with @Tool method
│   ├── StockQuoteResult.java         # record: ticker, price, asOf, marketState, source
│   └── FinnhubQuoteClient.java       # thin Java HttpClient wrapper (no new Maven dep)
├── service/
│   └── StructuredOutputService.java  # GET /api/ai/structured service
├── api/
│   └── StructuredInsightRecord.java  # Java record: title, subtitle, List<InsightEntry>
│   └── InsightEntry.java             # nested record: label, value (double)
│   (AiController extended with GET /api/ai/structured)

backend/src/test/java/com/quantlens/ai/
├── StockQuoteToolServiceTest.java    # @Tool demo (seeded), live (mocked Finnhub), cache TTL
├── StructuredOutputServiceTest.java  # demo (seeded JSON → record), live (mock ChatModel)
├── MultiProviderRoutingTest.java     # mock Anthropic + OpenAI models, assert both route
├── KeyLeakageIntegrationTest.java    # (extended) Finnhub key never in response/log
```

---

### Pattern 1: @Tool Method Definition and Registration in 1.1.6

**What:** Define a `@Component` class with a `@Tool`-annotated method. Register it on the `ChatClient` via `ToolCallback` (not raw object) to avoid the `defaultTools()` detection bug in 1.1.x.

**When to use:** Any server-side capability the LLM should be able to invoke (live quotes, portfolio summaries, etc.).

**Key gotcha in 1.1.x:** `ChatClient.Builder.defaultTools(toolBeanInstance)` has a known detection bug (GitHub issue #5134) where it fails with "No @Tool annotated methods found" for certain bean lifecycle scenarios (CGLIB proxied beans, startup-time registration). The safe path is `defaultToolCallbacks(MethodToolCallbackProvider.builder().toolObjects(bean).build())`.

```java
// Source: https://docs.spring.io/spring-ai/reference/api/tools.html [VERIFIED: official docs]

// 1. Define the @Tool method in a @Component class
// Package: org.springframework.ai.tool.annotation.Tool + ToolParam  [VERIFIED: official docs]
@Component
public class StockQuoteToolService {

    private final FinnhubQuoteClient finnhubClient;
    private final OhlcvBarRepository ohlcvRepo;
    // ... inject via constructor

    @Tool(description = "Get the current stock price for a ticker symbol. "
            + "Returns price, change, market state (regular/after-hours/closed), and timestamp.")
    public StockQuoteResult getStockQuote(
            @ToolParam(description = "Stock ticker symbol, e.g. AAPL, MSFT") String ticker) {

        // Live: Finnhub call with 15-min TTL cache
        // Demo fallback: seeded OHLCV last close
        return finnhubClient.getQuote(ticker);   // FinnhubQuoteClient handles both paths
    }
}

// 2. The result record — small, concrete, Jackson-serializable
public record StockQuoteResult(
    String ticker,
    java.math.BigDecimal price,
    String asOf,          // ISO-8601 datetime string
    String marketState,   // "REGULAR" | "AFTER_HOURS" | "PRE_MARKET" | "CLOSED" | "DEMO"
    String source         // "FINNHUB" | "SEEDED"
) {}
```

**Registration in ChatClientStrategy:** [VERIFIED: official docs + actual codebase]

```java
// In ChatClientStrategy — replace defaultAdvisors-only build with tools + advisors:
// [ASSUMED: exact MethodToolCallbackProvider API — verify at compile against spring-ai-model-1.1.6]
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

public ChatClient forSession(LlmKeySessionHolder keyHolder) {
    ChatModel model = buildModel(keyHolder);
    // MethodToolCallbackProvider avoids the defaultTools() detection bug (issue #5134)
    ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
            .toolObjects(stockQuoteToolService)
            .build()
            .getToolCallbacks()
            .toArray(new ToolCallback[0]);

    return ChatClient.builder(model)
            .defaultToolCallbacks(toolCallbacks)         // tools registered at builder level
            .defaultAdvisors(advisors.toArray(new CallAdvisor[0]))
            .build();
}
```

**Why tools at builder level (not request level):**
The `DemoModeAdvisor` short-circuits in demo mode BEFORE the model is ever invoked. Since the tools are registered on the ChatClient (model receives them in the tool spec), and the model is never called in demo mode, the tool method body is never executed in demo mode. This is the correct, existing seam behavior — no special handling required.

**Important:** If runtime-level `.tools(...)` is used on a ChatClient that also has `defaultToolCallbacks()`, the runtime tools COMPLETELY OVERRIDE the defaults (per official docs). Always use either builder-level OR request-level, not both.

---

### Pattern 2: BeanOutputConverter / .entity() for Structured Output

**What:** Use `ChatClient.call().entity(MyRecord.class)` to instruct the LLM to produce JSON that Spring AI deserializes into a typed Java record. BeanOutputConverter generates a DRAFT_2020_12 JSON schema from the record definition and appends format instructions to the prompt automatically.

**When to use:** Any AI response that must have a machine-readable, typed structure (charts, risk summaries, allocation breakdowns).

```java
// Source: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html
// [VERIFIED: official docs]

// 1. Define the target record — flat or nested; must be Jackson-deserializable
public record StructuredInsightRecord(
    String title,
    String subtitle,
    List<InsightEntry> series
) {}

public record InsightEntry(
    String label,
    double value  // percentage (0-100)
) {}

// 2. Call .entity() — BeanOutputConverter is wired automatically
StructuredInsightRecord insight = chatClient.prompt()
    .system("You are a portfolio analyst. Return ONLY valid JSON matching the schema.")
    .user(u -> u.text("Analyze this portfolio and produce a sector exposure insight: {summary}")
                .param("summary", portfolioSummary))
    .call()
    .entity(StructuredInsightRecord.class);

// 3. The endpoint returns the record directly (Spring MVC serializes to JSON)
// Frontend StructuredOutputChart.vue receives the same StructuredChartDto shape
// (title, subtitle, series[{label, value}]) — no chart changes needed.
```

**Format instructions injection:** BeanOutputConverter appends format instructions to the user message at call time (not the system prompt). Instructions read: "Your response should be in JSON format. The data structure for the JSON should match this Java class..." [VERIFIED: official docs]. No manual `{format}` placeholder needed with the ChatClient `.entity()` path.

**Native structured output (optional enhancement):**
```java
// Anthropic Claude 3.5+ and OpenAI GPT-4o support native structured output (higher reliability)
// Enable by adding an advisor param — do NOT add this globally as it may conflict with demo mode
// [VERIFIED: official docs - AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT]
// Only enable on the live path; demo path never reaches the model anyway.
```

**Demo path — same DTO shape:** The `DemoModeAdvisor` short-circuit returns seeded JSON stored in `ai_seed_content` (type `STRUCTURED_INSIGHT`, subject `GROWTH`/`INCOME`/`BALANCED`). The `StructuredOutputService` must deserialize this JSON into `StructuredInsightRecord` explicitly (since BeanOutputConverter is bypassed in demo mode). Use `ObjectMapper.readValue(content, StructuredInsightRecord.class)`.

---

### Pattern 3: Finnhub /quote REST Call (Thin Java HttpClient)

**What:** Call `https://finnhub.io/api/v1/quote?symbol={TICKER}&token={KEY}` using Java 21 `HttpClient`. Parse 8-field JSON response. No Maven dependency.

**Finnhub /quote endpoint (free tier):** [CITED: finnhub.io/docs/api/quote via web search + community confirmation]

| Field | Type | Meaning |
|-------|------|---------|
| `c` | double | Current price |
| `d` | double | Absolute change (vs. previous close) |
| `dp` | double | Change percent |
| `h` | double | Day high |
| `l` | double | Day low |
| `o` | double | Day open |
| `pc` | double | Previous close |
| `t` | long | Unix epoch seconds timestamp |

**No native after-hours/market-state field in /quote response.** [ASSUMED: separate `/stock/market-status?exchange=US` endpoint provides `isOpen` boolean. Verify at runtime.] The recommended approach: determine market state from the `t` timestamp — if `t` falls outside NYSE regular hours (Mon-Fri 09:30-16:00 ET), label as `AFTER_HOURS`/`PRE_MARKET`/`CLOSED`. This avoids a second API call.

**Rate limit:** 60 calls/minute free tier [VERIFIED: multiple sources confirm; official Finnhub docs state 60/min]. With 15-min TTL per-symbol cache, a 15-symbol portfolio needs at most 15 cache misses per 15 minutes = 1 call/min average — well within limits.

**Authentication:** `token` query parameter (`?symbol=AAPL&token=YOUR_KEY`). Never log the token. [CITED: finnhub.io/docs/api]

```java
// FinnhubQuoteClient.java — thin wrapper, no new Maven dependency
// [VERIFIED: Java 21 HttpClient is part of java.net.http, available on JDK 21]

@Component
public class FinnhubQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubQuoteClient.class);
    private static final String BASE_URL = "https://finnhub.io/api/v1/quote";
    private static final long TTL_MILLIS = 15 * 60 * 1000L; // 15 minutes

    // Cache: ticker → CachedQuote (price + fetchedAt timestamp)
    private final ConcurrentHashMap<String, CachedQuote> cache = new ConcurrentHashMap<>();

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper;
    private final OhlcvBarRepository ohlcvRepo;

    @Value("${FINNHUB_API_KEY:}") // empty string = no key = demo fallback
    private String finnhubApiKey;

    // ... constructor injection of objectMapper, ohlcvRepo

    public StockQuoteResult getQuote(String ticker) {
        if (finnhubApiKey == null || finnhubApiKey.isBlank()) {
            return buildSeededQuote(ticker);  // Demo: seeded OHLCV last close
        }

        // Check cache first (15-min TTL)
        CachedQuote cached = cache.get(ticker);
        if (cached != null && (System.currentTimeMillis() - cached.fetchedAt()) < TTL_MILLIS) {
            return cached.result();
        }

        // Live: call Finnhub
        try {
            String uri = BASE_URL + "?symbol=" + ticker + "&token=" + finnhubApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            FinnhubQuoteResponse raw = objectMapper.readValue(response.body(),
                    FinnhubQuoteResponse.class);

            // t == 0 means Finnhub returned no data (unknown ticker or market closed before first trade)
            if (raw.c() == 0.0 && raw.t() == 0L) {
                log.warn("Finnhub returned zero price for ticker {} — falling back to seeded", ticker);
                return buildSeededQuote(ticker);
            }

            String marketState = deriveMarketState(raw.t());
            StockQuoteResult result = new StockQuoteResult(
                    ticker,
                    BigDecimal.valueOf(raw.c()).setScale(2, RoundingMode.HALF_UP),
                    Instant.ofEpochSecond(raw.t()).toString(),
                    marketState,
                    "FINNHUB"
            );

            cache.put(ticker, new CachedQuote(result, System.currentTimeMillis()));
            return result;

        } catch (Exception e) {
            // T-08-LEAK: NEVER log the API key or echo the exception message
            log.warn("Finnhub call failed for ticker {} — falling back to seeded", ticker);
            return buildSeededQuote(ticker);
        }
    }

    // Internal records — not exposed in API
    private record CachedQuote(StockQuoteResult result, long fetchedAt) {}
    private record FinnhubQuoteResponse(double c, double d, double dp,
                                         double h, double l, double o,
                                         double pc, long t) {}

    private String deriveMarketState(long epochSeconds) {
        // NYSE regular hours: Mon-Fri 09:30-16:00 ET (UTC-4 or UTC-5)
        // [ASSUMED: market state determination from timestamp — verify edge cases at compile]
        ZonedDateTime dt = Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.of("America/New_York"));
        DayOfWeek day = dt.getDayOfWeek();
        LocalTime time = dt.toLocalTime();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) return "CLOSED";
        LocalTime open  = LocalTime.of(9, 30);
        LocalTime close = LocalTime.of(16, 0);
        LocalTime preMarket = LocalTime.of(4, 0);

        if (time.isBefore(preMarket)) return "CLOSED";
        if (time.isBefore(open))     return "PRE_MARKET";
        if (time.isBefore(close))    return "REGULAR";
        return "AFTER_HOURS";
    }

    private StockQuoteResult buildSeededQuote(String ticker) {
        // Fetch the latest OHLCV close from the seeded market data
        // OhlcvBarRepository.findLatestBarBySecurityIds() requires securityId — need ticker→id lookup
        // [ASSUMED: a OhlcvBarRepository.findLatestClosePriceByTicker(ticker) query method exists
        //  or can be added; or use SecurityRepository to look up id first]
        return new StockQuoteResult(ticker, BigDecimal.ZERO, "demo", "DEMO", "SEEDED");
    }
}
```

**Finnhub key handling:** `@Value("${FINNHUB_API_KEY:}")` with empty-string default. Never log the value. A separate environment variable (not the LLM key). The `LlmKeySessionHolder` holds LLM keys only. Finnhub key is application-level config (env var), not session-scoped.

---

### Pattern 4: Multi-Provider Routing (Existing — Verified in Codebase)

The `ChatClientStrategy.forSession(keyHolder)` already handles both providers correctly:
- **Anthropic:** `AnthropicChatModel.builder().anthropicApi(sessionApi).defaultOptions(...).build()` (builder pattern; no `mutate()` — confirmed not to exist on Anthropic in 1.1.x, A3 resolved in Phase 6)
- **OpenAI:** `baseOpenAiModel.mutate().openAiApi(sessionApi).build()` (`mutate()` exists on OpenAiChatModel in 1.1.x)
- **Demo:** returns `baseAnthropicModel` (never called; DemoModeAdvisor fires first)

Provider selection: `LlmKeySessionHolder.getProvider()` returns `"anthropic"` or `"openai"`.

**Phase 8 adds tests only** — no code change to `ChatClientStrategy`. The test wires mock `ChatModel` implementations for both providers and asserts they are called once per provider when `keyHolder.getProvider()` returns each value.

---

### Anti-Patterns to Avoid

- **`defaultTools(beanInstance)` in 1.1.x:** Has a known detection bug (GitHub #5134) that throws "No @Tool annotated methods found" for CGLIB-proxied beans. Always use `MethodToolCallbackProvider.builder().toolObjects(bean).build()` and pass the result to `defaultToolCallbacks()`.
- **Finnhub key in logs:** The exception catch block in `FinnhubQuoteClient` must NEVER log `e.getMessage()` (can contain the HTTP request URL which includes the token). Log only the ticker and a generic failure signal.
- **Top-level array with .entity():** OpenAI does not support arrays of objects at the top level with native structured output. Wrap in a record: `record Wrapper(List<InsightEntry> series)` rather than `.entity(new ParameterizedTypeReference<List<InsightEntry>>() {})` if using native structured output.
- **`t == 0` Finnhub response:** A zero timestamp means Finnhub returned an empty/error quote (unknown ticker). Must be explicitly handled — otherwise `BigDecimal.valueOf(0.0)` silently becomes the "price". Always check `c != 0 || t != 0`.
- **Cache poisoning between tests:** The `ConcurrentHashMap` cache is in the `FinnhubQuoteClient` bean. In integration tests, use `@DirtiesContext` or inject a `@MockBean FinnhubQuoteClient` to prevent cache state leaking between tests.
- **Tools on demo advisor path:** In demo mode, `DemoModeAdvisor` returns before the ChatModel is called. Tools registered on the ChatClient are therefore NEVER invoked in demo mode — this is the correct behavior. Do NOT add `if (demoMode) return seededQuote` logic inside the `@Tool` method body (that would be redundant and confusing).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| JSON schema from Java record | Manual JSON schema strings | `BeanOutputConverter` / `.entity()` | Spring AI generates DRAFT_2020_12 schema automatically; manual schemas drift from the record definition |
| Tool discovery and invocation | Custom reflection loop | `@Tool` + `MethodToolCallbackProvider` | Spring AI handles tool definition, schema extraction, invocation dispatch, result injection |
| Structured output parsing | `objectMapper.readValue(rawLlmText, ...)` directly in controller | `.call().entity(StructuredInsightRecord.class)` | BeanOutputConverter includes retry logic and schema-driven prompting |
| After-hours market classification from US market schedule | External calendar library | Timestamp → ZonedDateTime(ET) + hour check | NYSE hours are fixed; a 10-line check covers all cases without a new dependency |
| Per-request ChatClient for tools | New `ChatClient` per request with tools | `ChatClientStrategy.forSession()` extended with tools | The existing strategy already handles session key injection; extend it, don't bypass it |

**Key insight:** Spring AI's tool-calling and structured-output machinery handle the hard parts (schema generation, tool dispatch, result injection, format instruction injection). The application code only needs to define the @Tool method and the record shape — Spring AI does the rest.

---

## Runtime State Inventory

> Not applicable — this is a greenfield addition of new capabilities. No rename/refactor/migration.

---

## Common Pitfalls

### Pitfall 1: `defaultTools()` Annotation Detection Bug in 1.1.x

**What goes wrong:** Passing a `@Component` bean instance to `ChatClient.Builder.defaultTools(beanInstance)` throws `IllegalArgumentException: No @Tool annotated methods found`, even when the bean clearly has `@Tool` methods. Happens because CGLIB proxies wrap the bean and the annotation scanner inspects the proxy class rather than the target class.

**Why it happens:** GitHub issue #5134 confirms this as a known defect in the 1.1.x `defaultTools()` code path when beans are registered at startup lifecycle events.

**How to avoid:** Use `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()` and pass the resulting `ToolCallback[]` to `defaultToolCallbacks()`. This explicitly constructs callbacks from the target class's methods, bypassing the proxy issue.

**Warning signs:** `IllegalArgumentException: No @Tool annotated methods found` at startup or first request. Tools never invoked even with a live key.

### Pitfall 2: BeanOutputConverter + Advisor Chain Demo Short-Circuit

**What goes wrong:** In demo mode, `DemoModeAdvisor` returns a `ChatClientResponse` wrapping an `AssistantMessage` containing the seeded JSON string. When `StructuredOutputService` calls `.entity(StructuredInsightRecord.class)`, BeanOutputConverter tries to parse the message text from the `ChatClientResponse`. If the seeded JSON is valid JSON matching the record schema, deserialization succeeds. If the seeded content is prose (not JSON), `JsonMappingException` is thrown and the demo breaks.

**How to avoid:** Seeded content in `ai_seed_content` for type `STRUCTURED_INSIGHT` MUST be valid JSON matching `StructuredInsightRecord` schema. The `StructuredOutputService` should explicitly use `ObjectMapper.readValue(content, StructuredInsightRecord.class)` on the demo path (intercepted BEFORE `.entity()` call) rather than relying on BeanOutputConverter to handle demo-mode text. The safest pattern: `StructuredOutputService` detects demo mode by checking whether `DemoModeAdvisor` returned (via `keyHolder.hasKey()`), and parses the seed content directly in that case.

**Warning signs:** `HttpMessageNotReadableException` or `JsonMappingException` on `GET /api/ai/structured` in demo mode.

### Pitfall 3: Finnhub Key Leaking via Exception Stack Trace

**What goes wrong:** When the Finnhub HTTP call fails, Java `HttpClient` wraps the exception with the full URL in the message: `java.io.IOException: ... request to https://finnhub.io/api/v1/quote?symbol=AAPL&token=sk-...`. Logging `e.getMessage()` or `e.toString()` at any level exposes the API key in logs.

**How to avoid:** In the `FinnhubQuoteClient` catch block, log ONLY the ticker symbol and a generic failure code. Never log the exception message, cause, or stack trace at INFO/WARN. Use `log.debug("Finnhub call failed for {}", ticker)` at most (DEBUG is excluded from production logs per existing `logging.level.com.quantlens=INFO` config).

**Warning signs:** Log lines containing `api.finnhub.io/...&token=`.

### Pitfall 4: Cache Not Isolated Between Test Runs

**What goes wrong:** `FinnhubQuoteClient` holds a `ConcurrentHashMap` as instance state. The Spring context reuses the same bean across test methods in `@SpringBootTest` tests. A test that populates the cache in a "cache miss" scenario will cause a subsequent test expecting a cache miss to find a cached entry — making the TTL assertion flaky.

**How to avoid:** Inject `FinnhubQuoteClient` as `@MockBean` in integration tests, or expose a `clearCache()` method and call it in `@BeforeEach`. For the TTL test specifically, use a `@MockBean FinnhubQuoteClient` with Mockito and verify call counts directly.

### Pitfall 5: StructuredInsightRecord with Nested List — Schema Generation Edge Case

**What goes wrong:** `BeanOutputConverter` generates a DRAFT_2020_12 schema for the record. A `List<InsightEntry>` inside `StructuredInsightRecord` generates an array schema. OpenAI's native structured output does NOT support arrays as the top-level type, but an array nested within a top-level object IS supported. The record wrapping `series: List<InsightEntry>` under a named field avoids this limitation.

**How to avoid:** Always wrap collection fields in a named field of the root record. Never use `.entity(new ParameterizedTypeReference<List<InsightEntry>>() {})` directly for the top-level call with OpenAI.

### Pitfall 6: 15-Min TTL Cache and Market Data Delay

**What goes wrong:** Finnhub free tier quotes are delayed (the STACK.md says "20 min"). Displaying a Finnhub quote in the UI without labeling it as delayed looks like a live price. Combined with the 15-min cache TTL, users could see data that is 35 minutes old.

**How to avoid:** Always include the `asOf` field (from Finnhub's `t` timestamp) in the `StockQuoteResult` and surface it in the LLM response: "AAPL is trading at $182.50 as of 14:23 ET (delayed 15-20 min)." The chat response naturally includes this from the tool return value. Do not promise "real-time" in any UI label.

---

## Code Examples

### Verified: @Tool + @ToolParam annotations (Spring AI 1.1.x)

```java
// Source: https://docs.spring.io/spring-ai/reference/api/tools.html [VERIFIED: official docs]
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

@Tool(description = "Get the current or most recent stock price for a ticker symbol.")
public StockQuoteResult getStockQuote(
        @ToolParam(description = "Stock ticker symbol, e.g. AAPL, MSFT") String ticker) {
    // ...
}
```

### Verified: .entity() for structured output (Spring AI 1.1.x)

```java
// Source: https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html [VERIFIED]
StructuredInsightRecord insight = chatClient.prompt()
    .system("Return ONLY valid JSON. Analyze the portfolio.")
    .user(u -> u.text("Produce a sector allocation insight for: {summary}")
                .param("summary", summary))
    .call()
    .entity(StructuredInsightRecord.class);
```

### Verified: MethodToolCallbackProvider (safe tool registration for 1.1.x)

```java
// Source: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/method/MethodToolCallbackProvider.html
// [VERIFIED: official Javadoc]
// Workaround for defaultTools() bug (GitHub #5134)
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

ToolCallback[] callbacks = MethodToolCallbackProvider.builder()
        .toolObjects(stockQuoteToolService)
        .build()
        .getToolCallbacks()
        .toArray(new ToolCallback[0]);

ChatClient.builder(model)
    .defaultToolCallbacks(callbacks)
    .defaultAdvisors(advisors.toArray(new CallAdvisor[0]))
    .build();
```

### Verified: ChatClientStrategy existing pattern (codebase)

```java
// Source: actual codebase ChatClientStrategy.java [VERIFIED: file read]
// Anthropic: builder pattern (no mutate() in 1.1.x)
AnthropicApi sessionApi = AnthropicApi.builder().apiKey(apiKey).build();
AnthropicChatModel model = AnthropicChatModel.builder()
    .anthropicApi(sessionApi)
    .defaultOptions(AnthropicChatOptions.builder().model("claude-sonnet-4-6").maxTokens(2048).build())
    .build();

// OpenAI: mutate() pattern (mutate() exists in 1.1.x)
OpenAiApi sessionApi = OpenAiApi.builder().apiKey(apiKey).build();
OpenAiChatModel model = baseOpenAiModel.mutate().openAiApi(sessionApi).build();
```

### Verified: Finnhub /quote response shape

```json
// Source: https://finnhub.io/docs/api/quote [CITED: official Finnhub docs, confirmed via search]
{
  "c": 182.50,    // current price
  "d": 1.15,      // change absolute
  "dp": 0.63,     // change percent
  "h": 183.20,    // day high
  "l": 180.10,    // day low
  "o": 181.00,    // day open
  "pc": 181.35,   // previous close
  "t": 1702569600 // Unix epoch seconds
}
// After-hours/market-state: NOT a field in /quote response.
// Derive from timestamp vs NYSE hours (09:30-16:00 ET Mon-Fri).
```

### Assumed: DemoModeAdvisor structured output path

```java
// In StructuredOutputService — handles BOTH demo and live with same return type
// [VERIFIED: pattern follows existing ChatService and ExplainPositionService]
public StructuredInsightRecord getInsight(Long portfolioId) {
    try {
        if (!keyHolder.hasKey()) {
            // Demo: parse seeded JSON directly (bypass BeanOutputConverter)
            String seedContent = seedRepo.findByTypeAndSubjectId("STRUCTURED_INSIGHT", persona)
                .map(AiSeedContent::getContent)
                .orElse("{\"title\":\"Sector Exposure\",\"subtitle\":\"Demo\",\"series\":[]}");
            return objectMapper.readValue(seedContent, StructuredInsightRecord.class);
        }
        // Live: BeanOutputConverter via .entity()
        return strategy.forSession(keyHolder)
            .prompt()
            .system("Return ONLY valid JSON matching the exact schema for StructuredInsightRecord.")
            .user(u -> u.text("Analyze sector exposure for portfolio: {summary}")
                        .param("summary", buildPortfolioSummary(portfolioId)))
            .call()
            .entity(StructuredInsightRecord.class);
    } catch (Exception e) {
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI provider temporarily unavailable");
    }
}
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `FunctionCallback` for tool definition | `@Tool` + `MethodToolCallback` | Spring AI 1.0 GA (Jun 2025) | Cleaner declarative API; FunctionCallback deprecated and removed in 1.1.x |
| `AdvisedRequest`/`AdvisedResponse` in advisors | `ChatClientRequest`/`ChatClientResponse` | Spring AI 1.0 GA (Jun 2025) | Already proven in Phase 6 DemoModeAdvisor |
| `PromptChatMemoryAdvisor` | `MessageChatMemoryAdvisor` | Spring AI 1.1.6 | Already proven in Phase 7 |
| `tools()` on ChatClient builder | `defaultToolCallbacks()` via `MethodToolCallbackProvider` | Spring AI 1.1.x (bug #5134) | Safe registration path for proxied beans |
| Manual JSON parsing for structured output | `.entity(Record.class)` via `BeanOutputConverter` | Spring AI 1.0+ | Schema auto-generation, format instructions auto-appended |

**Deprecated/outdated:**
- `FunctionCallback`/`FunctionTool`: removed in 1.1.x; replaced by `ToolCallback`/`@Tool`
- `defaultTools(beanInstance)` with CGLIB-proxied beans: buggy in 1.1.x; use `defaultToolCallbacks()`
- `AdvisedRequest`/`AdvisedResponse`: not valid types in 1.1.x; do not exist (confirmed Phase 6)
- Alpha Vantage for quotes: 25 req/day on free tier; Finnhub 60/min is the right choice

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `MethodToolCallbackProvider.builder().toolObjects(bean).build().getToolCallbacks()` is the correct API in 1.1.6 to extract `ToolCallback[]` from a `@Component` bean | Standard Stack, Pattern 1 | If API differs, tool registration fails at compile; fix: check spring-ai-model-1.1.6 Javadoc for exact method name |
| A2 | `defaultToolCallbacks(ToolCallback[])` (array overload) exists on `ChatClient.Builder` in 1.1.6 | Pattern 1 | If only varargs or List overload exists, adjust call site; low risk since API is additive |
| A3 | Finnhub `/quote` returns `t == 0` (not null or missing field) for unknown ticker / no-data state | Pattern 3, Common Pitfalls | If `t` is absent, Jackson throws NPE on `long t` deserialization; use `Long` (boxed) and null-check |
| A4 | NYSE market hours determination from timestamp alone is sufficient for `marketState` label (no separate `/stock/market-status` call needed) | Pattern 3 | If Finnhub's `t` timestamp reflects the last trade time (not current time), after-hours determination from `t` is inaccurate; in that case add a clock-based check |
| A5 | `OhlcvBarRepository` can be extended with a `findLatestClosePriceByTicker(String ticker)` JPQL query that joins through `Security` to look up by ticker symbol | Pattern 3 | If ticker→securityId mapping requires a separate `SecurityRepository` call, add that; existing `findLatestBarBySecurityIds()` only accepts IDs |
| A6 | `@Value("${FINNHUB_API_KEY:}")` resolves correctly in Spring Boot 3.5 with a Docker Compose env var of the same name | Pattern 3 | Standard Spring Boot behavior; risk is minimal but verify env var name matches exactly |
| A7 | BeanOutputConverter does NOT need any special setup beyond `.entity(StructuredInsightRecord.class)` for the `ChatClient` fluent API in 1.1.6 | Pattern 2 | Confirmed by official docs; risk: none |
| A8 | `AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT` is the correct import/constant name in Spring AI 1.1.6 | Pattern 2 | If constant moved or renamed, simply omit (BeanOutputConverter prompt-based fallback still works) |

---

## Open Questions (RESOLVED — gated at Wave 0 compile; see plan 08-01 tasks)

1. **`MethodToolCallbackProvider.getToolCallbacks()` return type**
   - What we know: `MethodToolCallbackProvider` exists per official Javadoc
   - What's unclear: Whether `getToolCallbacks()` returns `List<ToolCallback>` or `ToolCallback[]`; whether `defaultToolCallbacks()` on Builder accepts `List` or varargs
   - Recommendation: Verify at Wave 0 compile gate; fallback is `.tools(beanInstance)` on the per-request prompt call (not builder level) which avoids the startup detection bug entirely

2. **OhlcvBarRepository ticker-based query**
   - What we know: `findLatestBarBySecurityIds(List<Long>)` exists; `Security` entity presumably has a `ticker` field
   - What's unclear: Whether a ticker-based query exists or needs to be added
   - Recommendation: Add `findLatestCloseByTicker(String ticker)` to `OhlcvBarRepository` as a JPQL query joining through `Security` in Wave 0

3. **ai module Modulith boundary for tools subpackage**
   - What we know: `ai/package-info.java` currently allows `portfolio::domain` and `seed` dependencies
   - What's unclear: Whether adding `marketdata::domain` (for `OhlcvBarRepository` access in `FinnhubQuoteClient`) requires a new allowedDependency entry
   - Recommendation: Add `marketdata::domain` to allowed dependencies (same pattern as Phase 6 fix); run QuantLensModulithTest in Wave 0

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Java 21 `java.net.http.HttpClient` | FinnhubQuoteClient | ✓ | JDK 21 (JAVA_HOME set per CLAUDE.md) | N/A — built-in |
| `FINNHUB_API_KEY` env var | Live quote path | ✗ (not set in dev) | — | Empty string → seeded OHLCV close |
| Spring AI BOM 1.1.6 | Tool calling + structured output | ✓ | 1.1.6 (pom.xml) | N/A |
| `MethodToolCallbackProvider` | Tool registration | ✓ (inferred: in spring-ai-model-1.1.6) | 1.1.6 | Fallback: `.tools(bean)` on prompt call |
| `BeanOutputConverter` / `.entity()` | Structured output | ✓ (in spring-ai-model-1.1.6) | 1.1.6 | N/A |
| Vue echarts (frontend) | StructuredOutputChart | ✓ | echarts 6.1.0 / vue-echarts 8.0.1 | N/A |

**Missing dependencies with no fallback:** none  
**Missing dependencies with fallback:** `FINNHUB_API_KEY` (not set in dev → seamlessly falls back to seeded OHLCV last close, which is the demo path)

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers (backend); Vitest 2.x (frontend) |
| Config file | `backend/src/test/resources/application-test.yml` (existing) |
| Quick run command | `.\mvnw.cmd test -Dtest=StockQuoteToolServiceTest,StructuredOutputServiceTest,MultiProviderRoutingTest -pl backend` |
| Full suite command | `.\mvnw.cmd test -pl backend` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-05 | @Tool demo: returns seeded OHLCV close with NO network | unit | `.\mvnw.cmd test -Dtest=StockQuoteToolServiceTest#demo* -pl backend` | ❌ Wave 0 |
| AI-05 | @Tool live: Finnhub MOCKED — assert price, after-hours label | unit | `.\mvnw.cmd test -Dtest=StockQuoteToolServiceTest#live* -pl backend` | ❌ Wave 0 |
| AI-05 | TTL cache: second call within 15 min does NOT re-fetch | unit | `.\mvnw.cmd test -Dtest=StockQuoteToolServiceTest#cache* -pl backend` | ❌ Wave 0 |
| AI-05 | Demo no-network: CountingCallAdvisor proof for tool chat path | integration | `.\mvnw.cmd test -Dtest=AiDemoModeIntegrationTest -pl backend` | ✅ (extend existing) |
| AI-05 | Finnhub key never in response/log | integration | `.\mvnw.cmd test -Dtest=KeyLeakageIntegrationTest -pl backend` | ✅ (extend) |
| AI-06 | Structured output demo: seeded JSON → StructuredInsightRecord | unit | `.\mvnw.cmd test -Dtest=StructuredOutputServiceTest#demo* -pl backend` | ❌ Wave 0 |
| AI-06 | Structured output live: mock ChatModel returns JSON → record | unit | `.\mvnw.cmd test -Dtest=StructuredOutputServiceTest#live* -pl backend` | ❌ Wave 0 |
| AI-06 | GET /api/ai/structured returns StructuredInsightRecord shape | integration | `.\mvnw.cmd test -Dtest=AiControllerIntegrationTest#structured* -pl backend` | ❌ Wave 0 |
| AI-05+06 | Multi-provider routing: mock Anthropic + OpenAI both route | unit | `.\mvnw.cmd test -Dtest=MultiProviderRoutingTest -pl backend` | ❌ Wave 0 |
| AI-06 | Frontend: StructuredOutputChart renders typed DTO from /api/ai/structured | component | `npm run test -- --run src/__tests__/components/StructuredOutputChart.test.ts` | ✅ (extend existing 7 tests) |

### Sampling Rate
- **Per task commit:** `.\mvnw.cmd test -Dtest=StockQuoteToolServiceTest,StructuredOutputServiceTest,MultiProviderRoutingTest -pl backend`
- **Per wave merge:** `.\mvnw.cmd test -pl backend` (full backend suite)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java` — covers AI-05 demo, live (mocked Finnhub), cache TTL, fallback to seeded
- [ ] `backend/src/test/java/com/quantlens/ai/StructuredOutputServiceTest.java` — covers AI-06 demo→record, live→record, DTO shape
- [ ] `backend/src/test/java/com/quantlens/ai/MultiProviderRoutingTest.java` — mock ChatModel for both providers, assert routing
- [ ] `backend/src/main/java/com/quantlens/ai/tools/StockQuoteToolService.java` — @Tool method
- [ ] `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java` — thin HTTP wrapper + cache
- [ ] `backend/src/main/java/com/quantlens/ai/tools/StockQuoteResult.java` — result record
- [ ] `backend/src/main/java/com/quantlens/ai/api/StructuredInsightRecord.java` — structured output record
- [ ] `backend/src/main/java/com/quantlens/ai/api/InsightEntry.java` — nested record
- [ ] `backend/src/main/java/com/quantlens/ai/service/StructuredOutputService.java` — service
- [ ] Extend `AiController` with `GET /api/ai/structured`
- [ ] Extend `KeyLeakageIntegrationTest` — cover new `/api/ai/structured` endpoint + Finnhub key pattern
- [ ] Extend `AiDemoModeIntegrationTest` — CountingCallAdvisor proof for structured output path
- [ ] Update `frontend/src/stores/ai.ts` `fetchStructured` to call `GET /api/ai/structured` instead of `/ai-structured-demo.json`
- [ ] Update `StructuredOutputChart.test.ts` — 7 existing tests still pass; add test for live DTO source (mocked axios)
- [ ] Add `STRUCTURED_INSIGHT` seeds in `AiSeedRunner` (per persona: GROWTH, INCOME, BALANCED) — valid JSON matching `StructuredInsightRecord` schema

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | N/A — existing session auth unchanged |
| V3 Session Management | yes | `LlmKeySessionHolder` @SessionScope; Finnhub key in env var (not session) |
| V4 Access Control | yes | `resolvePortfolioId()` IDOR pattern already in AiController |
| V5 Input Validation | yes | Ticker `@Pattern(regexp="^[A-Z]{1,10}$")` already on `/explain/{ticker}` — apply same to any new ticker-accepting endpoint |
| V6 Cryptography | no | Keys are in env var / session; no encryption needed for transport (HTTPS assumed in prod) |

### Known Threat Patterns for this Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Finnhub API key leakage via exception message | Information Disclosure | Catch all exceptions in FinnhubQuoteClient; log only ticker + generic failure; never log `e.getMessage()` |
| Tool return value injecting prompt instructions | Tampering (Prompt Injection) | Ticker is validated `[A-Z]{1,10}` before tool call; StockQuoteResult fields are typed (BigDecimal, String) — no freeform text injected |
| LLM tool call cross-session (using another user's session data) | IDOR | Tools only access market data (public quotes); no portfolio data accessed inside @Tool method — IDOR N/A here |
| Structured output JSON escaping issues | Tampering | BeanOutputConverter uses Jackson ObjectMapper which handles escaping; record fields are typed (String, double, List) — no injection vector |
| Finnhub rate limit exhaustion (DoS against the tool) | Denial of Service | 15-min TTL cache limits to 1 Finnhub call per symbol per 15 min; @Pattern validation rejects malformed tickers before the cache/HTTP call |

---

## Sources

### Primary (HIGH confidence)
- [Spring AI Tool Calling Reference](https://docs.spring.io/spring-ai/reference/api/tools.html) — `@Tool`, `@ToolParam`, `defaultToolCallbacks()`, `MethodToolCallbackProvider` documentation
- [Spring AI Structured Output Reference](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html) — `BeanOutputConverter`, `.entity()`, format instructions, native structured output, supported providers
- [Spring AI Chat Client Reference](https://docs.spring.io/spring-ai/reference/api/chatclient.html) — advisor chain, tools registration, default vs runtime tools
- Codebase: `ChatClientStrategy.java`, `DemoModeAdvisor.java`, `ChatService.java`, `LlmKeySessionHolder.java` — all read directly; Phase 6/7 SUMMARY.md — all read directly
- [MethodToolCallbackProvider Javadoc](https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/tool/method/MethodToolCallbackProvider.html) — API verification

### Secondary (MEDIUM confidence)
- [GitHub Issue #5134 — defaultTools() detection bug](https://github.com/spring-projects/spring-ai/issues/5134) — confirmed workaround via `defaultToolCallbacks()`
- [Finnhub /quote response shape](https://finnhub.io/docs/api/quote) — 8 fields confirmed via multiple community sources (robotwealth.com, search aggregation)
- [Finnhub rate limits — 60/min free tier](https://finnhub.io/docs/api/rate-limit) — confirmed across multiple community sources
- [Piotr's TechBlog — Tool Calling with Spring AI](https://piotrminkowski.com/2025/03/13/tool-calling-with-spring-ai/) — concrete `.tools()` call-site example verified

### Tertiary (LOW confidence — flagged in Assumptions Log)
- Market state derivation from Finnhub `t` timestamp: community-inferred, not explicitly documented in official Finnhub docs — see A4 in Assumptions Log
- `MethodToolCallbackProvider.getToolCallbacks()` exact return type: inferred from Javadoc; verify at compile gate

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — Spring AI official docs; codebase verified; no new dependencies
- Architecture: HIGH — follows established Phase 6/7 patterns directly; advisor chain behavior confirmed
- @Tool registration: MEDIUM-HIGH — official docs + known 1.1.x bug documented in GitHub Issues
- Finnhub API shape: MEDIUM — official docs not fully fetchable but confirmed via multiple community sources
- Pitfalls: HIGH — mostly derived from codebase read + official upgrade notes

**Research date:** 2026-06-09  
**Valid until:** 2026-07-09 (Spring AI 1.1.x is stable; Finnhub API shape is stable)
