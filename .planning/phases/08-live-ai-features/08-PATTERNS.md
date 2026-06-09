# Phase 8: Live AI Features - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 15 new/modified files
**Analogs found:** 11 / 15 (4 files have no codebase analog — use RESEARCH.md patterns)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `com/quantlens/ai/tools/StockQuoteToolService.java` | service | request-response | RESEARCH.md Pattern 1 | no-analog (new Spring AI @Tool pattern) |
| `com/quantlens/ai/tools/FinnhubQuoteClient.java` | service | request-response | RESEARCH.md Pattern 3 | no-analog (thin HTTP + cache, no codebase precedent) |
| `com/quantlens/ai/tools/StockQuoteResult.java` | model (record) | — | `ai/api/CitationDto.java` | role-match (ai DTO record) |
| `com/quantlens/ai/api/StructuredInsightRecord.java` | model (record) | — | `ai/api/ExplainResponseDto.java` + `CitationDto.java` | role-match (ai DTO record) |
| `com/quantlens/ai/api/InsightEntry.java` | model (record) | — | `ai/api/CitationDto.java` | role-match (nested ai DTO record) |
| `com/quantlens/ai/service/StructuredOutputService.java` | service | request-response | `ai/service/ChatService.java` | exact (demo ObjectMapper.readValue parse + live .entity() path) |
| `com/quantlens/ai/api/AiController.java` (extend) | controller | request-response | `ai/api/AiController.java` (self) | exact |
| `com/quantlens/ai/seed/AiSeedRunner.java` (extend) | seed/config | batch | `ai/seed/AiSeedRunner.java` (self) | exact |
| `com/quantlens/ai/chat/ChatClientStrategy.java` (extend) | service | request-response | `ai/chat/ChatClientStrategy.java` (self) | exact |
| `com/quantlens/ai/package-info.java` (verify) | config | — | `ai/package-info.java` (self) | exact |
| `backend/src/test/.../StockQuoteToolServiceTest.java` | test | — | `ai/ChatServiceLivePathTest.java` | role-match (Mockito unit test) |
| `backend/src/test/.../StructuredOutputServiceTest.java` | test | — | `ai/ChatServiceLivePathTest.java` | role-match (Mockito unit test) |
| `backend/src/test/.../MultiProviderRoutingTest.java` | test | — | `ai/ChatServiceLivePathTest.java` | role-match (Mockito unit test + strategy mock) |
| `frontend/src/stores/ai.ts` (modify) | store | request-response | `frontend/src/stores/ai.ts` (self) | exact |
| `frontend/src/__tests__/components/StructuredOutputChart.test.ts` (extend) | test | — | `frontend/src/__tests__/components/StructuredOutputChart.test.ts` (self) | exact |

---

## Pattern Assignments

### `com/quantlens/ai/tools/StockQuoteToolService.java` (service, @Tool)

**Analog:** RESEARCH.md Pattern 1 — no codebase analog exists.

**Imports pattern** (from RESEARCH.md Pattern 1):
```java
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
```

**Core @Tool pattern** (RESEARCH.md Pattern 1, lines 243-258):
```java
@Component
public class StockQuoteToolService {

    private final FinnhubQuoteClient finnhubClient;

    // Constructor injection only

    @Tool(description = "Get the current stock price for a ticker symbol. "
            + "Returns price, change, market state (regular/after-hours/closed), and timestamp.")
    public StockQuoteResult getStockQuote(
            @ToolParam(description = "Stock ticker symbol, e.g. AAPL, MSFT") String ticker) {
        return finnhubClient.getQuote(ticker);
    }
}
```

**No demo/live branch inside @Tool body.** The DemoModeAdvisor short-circuits in demo mode before the ChatModel invokes any tool. The @Tool method body runs ONLY on the live path. Do NOT add `if (!keyHolder.hasKey()) return seededQuote` inside the method — that is redundant and misleading.

**Registration point** — `ChatClientStrategy.forSession()` (see ChatClientStrategy section below for the exact injection site).

---

### `com/quantlens/ai/tools/FinnhubQuoteClient.java` (service, thin HTTP + TTL cache)

**Analog:** RESEARCH.md Pattern 3 — no codebase analog exists.

**Imports pattern** (RESEARCH.md Pattern 3):
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ConcurrentHashMap;
```

**Key injection pattern** (RESEARCH.md Pattern 3, line 391):
```java
@Value("${FINNHUB_API_KEY:}")  // empty string = no key = demo fallback
private String finnhubApiKey;
```

**Cache + live/demo branch pattern** (RESEARCH.md Pattern 3, lines 395-443):
```java
public StockQuoteResult getQuote(String ticker) {
    if (finnhubApiKey == null || finnhubApiKey.isBlank()) {
        return buildSeededQuote(ticker);  // Demo: seeded OHLCV last close
    }
    // Check cache first (15-min TTL)
    CachedQuote cached = cache.get(ticker);
    if (cached != null && (System.currentTimeMillis() - cached.fetchedAt()) < TTL_MILLIS) {
        return cached.result();
    }
    // Live: call Finnhub — catch block MUST NOT log e.getMessage() (contains token in URL)
    try {
        // ... HttpClient.send() ...
        cache.put(ticker, new CachedQuote(result, System.currentTimeMillis()));
        return result;
    } catch (Exception e) {
        log.warn("Finnhub call failed for ticker {} — falling back to seeded", ticker);
        return buildSeededQuote(ticker);
    }
}
```

**Key leak prevention** (RESEARCH.md Pitfall 3): The catch block MUST log only the ticker, never `e.getMessage()` (Java HttpClient wraps the exception with the full URL including `?token=...` in the message).

**Zero-timestamp guard** (RESEARCH.md Pitfall 1, line 421):
```java
if (raw.c() == 0.0 && raw.t() == 0L) {
    log.warn("Finnhub returned zero price for ticker {} — falling back to seeded", ticker);
    return buildSeededQuote(ticker);
}
```

**`buildSeededQuote` — OhlcvBarRepository integration:** The existing `OhlcvBarRepository.findLatestBarBySecurityIds(List<Long>)` (line 33) takes security IDs, not tickers. A new JPQL query `findLatestCloseByTicker(String ticker)` must be added to `OhlcvBarRepository`, joining through `Security.ticker` (the `Security` entity has `ticker` as a `@Column(unique=true)` at line 26 of `Security.java`).

---

### `com/quantlens/ai/tools/StockQuoteResult.java` (model record)

**Analog:** `backend/src/main/java/com/quantlens/ai/api/CitationDto.java` (lines 1-20) and `ExplainResponseDto.java` (lines 1-14).

**Record DTO pattern** (`CitationDto.java` lines 15-20):
```java
package com.quantlens.ai.api;   // NB: StockQuoteResult goes in tools package

/**
 * Javadoc with @param per component.
 */
public record CitationDto(
        String ticker,
        String section,
        String source,
        String excerpt
) {}
```

**Apply to StockQuoteResult:**
```java
package com.quantlens.ai.tools;

public record StockQuoteResult(
        String ticker,
        java.math.BigDecimal price,
        String asOf,         // ISO-8601 datetime string
        String marketState,  // "REGULAR" | "AFTER_HOURS" | "PRE_MARKET" | "CLOSED" | "DEMO"
        String source        // "FINNHUB" | "SEEDED"
) {}
```

No Jackson annotations needed — Spring AI serializes tool return values via its own ObjectMapper. All fields are Java-native or `BigDecimal` (as per project convention in `Security.java` which uses `BigDecimal` for all price fields, never `double`).

---

### `com/quantlens/ai/api/StructuredInsightRecord.java` (model record)

**Analog:** `backend/src/main/java/com/quantlens/ai/api/ExplainResponseDto.java` (record pattern) + `CitationDto.java` (nested reference).

**Record pattern** (`ExplainResponseDto.java` lines 1-14):
```java
package com.quantlens.ai.api;

/**
 * Response DTO for {@code GET /api/ai/structured}.
 *
 * <p>In demo mode the record is deserialized from authored seed JSON (type STRUCTURED_INSIGHT).
 * In live mode it is populated by BeanOutputConverter via .entity(StructuredInsightRecord.class).
 *
 * @param title    chart headline
 * @param subtitle optional subtitle (nullable)
 * @param series   list of labeled values (never null; may be empty)
 */
public record StructuredInsightRecord(
        String title,
        String subtitle,
        java.util.List<InsightEntry> series
) {}
```

**BeanOutputConverter constraint:** The record fields must be Jackson-deserializable without custom annotations. `List<InsightEntry>` nested under a named field avoids the OpenAI top-level-array restriction documented in RESEARCH.md Pitfall 5.

---

### `com/quantlens/ai/api/InsightEntry.java` (model record, nested)

**Analog:** `CitationDto.java` (lines 15-20) — same flat record pattern.

```java
package com.quantlens.ai.api;

/**
 * @param label  sector or category label (e.g. "Technology")
 * @param value  allocation percentage (0-100)
 */
public record InsightEntry(
        String label,
        double value
) {}
```

Note: `double` is used here (not `BigDecimal`) because BeanOutputConverter generates a JSON schema from the record. Jackson maps `double` to JSON `number` without issues. `BigDecimal` would work but requires the LLM to produce a JSON number that Jackson can unmarshal to BigDecimal — this is simpler.

---

### `com/quantlens/ai/service/StructuredOutputService.java` (service, request-response)

**Analog:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java` — exact match for the demo `ObjectMapper.readValue` parse pattern + live `.entity()` call.

**Imports pattern** (from `ChatService.java` lines 1-18, adapted):
```java
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.ai.api.StructuredInsightRecord;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
```

**Constructor injection pattern** (`ChatService.java` lines 79-85):
```java
public StructuredOutputService(ChatClientStrategy strategy,
                                LlmKeySessionHolder keyHolder,
                                PortfolioRepository portfolioRepository,
                                ObjectMapper objectMapper) {
    this.strategy            = strategy;
    this.keyHolder           = keyHolder;
    this.portfolioRepository = portfolioRepository;
    this.objectMapper        = objectMapper;
}
```

**Demo vs live branching pattern** (RESEARCH.md Pattern 2 "Assumed" block, lines 664-686, cross-checked against `ChatService.parseDemoResponse` lines 186-228):

The key difference from `ChatService` is that `StructuredOutputService` must explicitly branch on `keyHolder.hasKey()` BEFORE calling `.entity()`, because BeanOutputConverter will attempt to parse the demo seed JSON as structured output — which works only if the seed JSON is already valid. The safer pattern (matching RESEARCH.md recommendation) is to parse it explicitly with `objectMapper.readValue()` in demo mode, bypassing BeanOutputConverter entirely:

```java
public StructuredInsightRecord getInsight(Long portfolioId) {
    Portfolio portfolio = portfolioRepository.findById(portfolioId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    String personaKey = portfolio.getStyle().toUpperCase();  // mirrors CommentaryService line 89

    try {
        if (!keyHolder.hasKey()) {
            // Demo path: parse seeded JSON directly — bypass BeanOutputConverter (Pitfall 2)
            String seedContent = /* seedRepo.findByTypeAndSubjectId("STRUCTURED_INSIGHT", personaKey) */
                    ...
            return objectMapper.readValue(seedContent, StructuredInsightRecord.class);
            // Uses same ObjectMapper.readValue pattern as ChatService.parseDemoResponse (line 198)
        }

        // Live path: BeanOutputConverter via .entity() (RESEARCH.md Pattern 2)
        return strategy.forSession(keyHolder)
                .prompt()
                .system("Return ONLY valid JSON matching the schema. You are a portfolio analyst.")
                .user(u -> u.text("Analyze sector exposure for portfolio: {summary}")
                            .param("summary", buildPortfolioSummary(portfolioId)))
                .advisors(spec -> spec
                        .param("AI_SEED_TYPE",    "STRUCTURED_INSIGHT")
                        .param("AI_SEED_SUBJECT", personaKey))
                .call()
                .entity(StructuredInsightRecord.class);

    } catch (ResponseStatusException rse) {
        throw rse;
    } catch (Exception e) {
        // T-07-LEAK pattern (mirrors ChatService line 167-169, CommentaryService line 104-106)
        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                "AI provider temporarily unavailable");
    }
}
```

**Persona key derivation** — copy verbatim from `CommentaryService.java` lines 87-89:
```java
Portfolio portfolio = portfolioRepository.findById(portfolioId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
String personaKey = portfolio.getStyle().toUpperCase();
```

**Error handling pattern** — copy from `CommentaryService.java` lines 103-109:
```java
} catch (ResponseStatusException rse) {
    throw rse;
} catch (Exception e) {
    // Never echo provider error messages (could carry the key)
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "AI provider temporarily unavailable");
}
```

---

### `com/quantlens/ai/api/AiController.java` (extend — add GET /api/ai/structured)

**Analog:** `AiController.java` (self, lines 1-169) — add a new endpoint following the exact same pattern as `commentary()` (lines 111-116).

**New field + constructor extension** (mirror lines 63-76):
```java
// Add to constructor parameters and field list:
private final StructuredOutputService structuredOutputService;
```

**New endpoint pattern** (mirror `commentary` at lines 111-116):
```java
/**
 * Returns a typed structured-output insight DTO for the authenticated user's portfolio.
 * Demo: seeded JSON deserialized to StructuredInsightRecord. Live: BeanOutputConverter.
 */
@GetMapping("/structured")
@Transactional(readOnly = true)
public ResponseEntity<StructuredInsightRecord> structured(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return ResponseEntity.ok(structuredOutputService.getInsight(portfolioId));
}
```

**resolvePortfolioId** — already present at lines 161-168; do not duplicate.

**Class-level annotations already present** (`@Validated`, `@RestController`, `@RequestMapping("/api/ai")`) — no changes needed.

---

### `com/quantlens/ai/chat/ChatClientStrategy.java` (extend — register @Tool)

**Analog:** `ChatClientStrategy.java` (self, lines 76-81) — extend `forSession()` to add `defaultToolCallbacks()`.

**Current forSession pattern** (lines 76-81):
```java
public ChatClient forSession(LlmKeySessionHolder keyHolder) {
    ChatModel model = buildModel(keyHolder);
    return ChatClient.builder(model)
            .defaultAdvisors(advisors.toArray(new CallAdvisor[0]))
            .build();
}
```

**Extended forSession pattern** (RESEARCH.md Pattern 1 registration block, lines 278-290):
```java
// New import:
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;

// New field (injected via constructor):
private final StockQuoteToolService stockQuoteToolService;

// Extended forSession():
public ChatClient forSession(LlmKeySessionHolder keyHolder) {
    ChatModel model = buildModel(keyHolder);
    ToolCallback[] toolCallbacks = MethodToolCallbackProvider.builder()
            .toolObjects(stockQuoteToolService)
            .build()
            .getToolCallbacks()
            .toArray(new ToolCallback[0]);
    return ChatClient.builder(model)
            .defaultToolCallbacks(toolCallbacks)              // MUST come before defaultAdvisors
            .defaultAdvisors(advisors.toArray(new CallAdvisor[0]))
            .build();
}
```

**Critical:** Use `MethodToolCallbackProvider` + `defaultToolCallbacks()`, NOT `defaultTools(beanInstance)`. The `defaultTools()` overload has a known CGLIB proxy detection bug in Spring AI 1.1.x (GitHub #5134) documented in RESEARCH.md Pitfall 1.

**DemoModeAdvisor interaction:** Tools registered at builder level are part of the ChatModel's tool spec. DemoModeAdvisor fires at `HIGHEST_PRECEDENCE` and short-circuits before the ChatModel is invoked. Therefore the @Tool method body is NEVER executed in demo mode — no defensive guard needed inside the tool.

---

### `com/quantlens/ai/seed/AiSeedRunner.java` (extend — add STRUCTURED_INSIGHT seeds)

**Analog:** `AiSeedRunner.java` (self) — copy the `buildFixtures()` return pattern exactly.

**Seed version bump** (line 65): Change `AI_SEED_VERSION = "ai-v3"` to `"ai-v4"`.

**New constant** (mirror lines 67-69):
```java
private static final String STRUCTURED_INSIGHT = "STRUCTURED_INSIGHT";
```

**New seed fixture pattern** (mirror lines 332-347 for DAILY_COMMENTARY "GROWTH"):
```java
// Add to buildFixtures() return List.of(...):
new AiSeedContent(STRUCTURED_INSIGHT, "GROWTH",
    "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
    "\"series\":[" +
    "{\"label\":\"Technology\",\"value\":62.5}," +
    "{\"label\":\"Automotive\",\"value\":17.3}," +
    "{\"label\":\"Consumer Staples\",\"value\":10.1}," +
    "{\"label\":\"Cash\",\"value\":10.1}" +
    "]}"),

new AiSeedContent(STRUCTURED_INSIGHT, "INCOME",
    "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
    "\"series\":[" +
    "{\"label\":\"Financials\",\"value\":36.2}," +
    "{\"label\":\"Energy\",\"value\":28.4}," +
    "{\"label\":\"Consumer Staples\",\"value\":25.9}," +
    "{\"label\":\"Cash\",\"value\":9.5}" +
    "]}"),

new AiSeedContent(STRUCTURED_INSIGHT, "BALANCED",
    "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
    "\"series\":[" +
    "{\"label\":\"Technology\",\"value\":32.1}," +
    "{\"label\":\"Financials\",\"value\":18.6}," +
    "{\"label\":\"Healthcare\",\"value\":15.4}," +
    "{\"label\":\"Consumer Staples\",\"value\":14.2}," +
    "{\"label\":\"Energy\",\"value\":11.8}," +
    "{\"label\":\"Cash\",\"value\":7.9}" +
    "]}")
```

**IMPORTANT:** The JSON content MUST be valid and match the `StructuredInsightRecord` schema exactly (`title`, `subtitle`, `series:[{label,value}]`). Any deviation causes `JsonMappingException` in the demo path (RESEARCH.md Pitfall 2).

**Upsert guard** (lines 97-108): The existing upsert logic (`findByTypeAndSubjectId` + `save`) handles the new rows automatically — no code change needed beyond adding the fixtures and bumping the seed version.

---

### `com/quantlens/ai/package-info.java` (verify — already correct)

**Analog:** `ai/package-info.java` (self, lines 20-23):
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "AI",
        allowedDependencies = {"portfolio::domain", "marketdata::domain", "seed"})
package com.quantlens.ai;
```

`marketdata::domain` is **already listed** (added in Phase 6). `FinnhubQuoteClient` and `StockQuoteToolService` both reside in `com.quantlens.ai.tools` — fully within the `ai` module. No change to `package-info.java` is needed. Verify with `QuantLensModulithTest` in Wave 0.

---

### `backend/src/test/.../StockQuoteToolServiceTest.java` (test, unit)

**Analog:** `ChatServiceLivePathTest.java` (lines 1-202) — Mockito `@ExtendWith(MockitoExtension.class)` unit test without Spring context.

**Test class pattern** (`ChatServiceLivePathTest.java` lines 37-43):
```java
@ExtendWith(MockitoExtension.class)
class StockQuoteToolServiceTest {

    @Mock
    private FinnhubQuoteClient finnhubClient;  // mock the HTTP layer

    @Mock
    private OhlcvBarRepository ohlcvRepo;      // for seeded fallback
```

**Test cases to cover:**
1. `demoPath_returnsSeededOhlcvClose()` — mock `FinnhubQuoteClient.getQuote()` returns a `StockQuoteResult` with `source="SEEDED"` when called (verifies the tool delegates to the client, not that it calls Finnhub directly).
2. `livePath_returnsFinnhubPrice_withMarketState()` — mock `FinnhubQuoteClient.getQuote()` returns a live result with `source="FINNHUB"`.
3. `cacheTtl_secondCallWithinWindow_doesNotRefetch()` — test on `FinnhubQuoteClient` directly (not `StockQuoteToolService`): mock `HttpClient` (or use a real `FinnhubQuoteClient` with a `finnhubApiKey` set and `httpClient` wired to a WireMock/mock); populate cache; assert second call within 15 min reuses cached value.

**Import pattern** (mirrors `ChatServiceLivePathTest.java` lines 1-28):
```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;
```

---

### `backend/src/test/.../StructuredOutputServiceTest.java` (test, unit)

**Analog:** `ChatServiceLivePathTest.java` (lines 37-202) — Mockito unit test for service-level demo/live branching.

**Mock pattern** (mirrors `ChatServiceLivePathTest.java` lines 39-42):
```java
@Mock private ChatClientStrategy strategy;
@Mock private LlmKeySessionHolder keyHolder;
@Mock private PortfolioRepository portfolioRepository;
// ObjectMapper: use real instance (matches ChatService constructor pattern, line 79)
```

**Demo path test** (mirrors `chat_demoPathWithNullDocs_parsesDemoResponse` at lines 85-107):
```java
@Test
void demoPath_seedJsonDeserialized_toStructuredInsightRecord() {
    // when keyHolder.hasKey() == false, service reads seed and calls objectMapper.readValue
    when(keyHolder.hasKey()).thenReturn(false);
    // ... stub seedRepo or inject seeded content directly ...
    StructuredInsightRecord result = service.getInsight(portfolioId);
    assertThat(result.title()).isNotBlank();
    assertThat(result.series()).isNotEmpty();
}
```

**Live path test** — mock `ChatClient.CallResponseSpec.entity()` to return a `StructuredInsightRecord`:
```java
@Test
void livePath_mockChatModelReturnsRecord_shapeIsCorrect() {
    when(keyHolder.hasKey()).thenReturn(true);
    // stub strategy.forSession(...).prompt()...call().entity(StructuredInsightRecord.class)
    // Use mock chain pattern from ChatServiceLivePathTest lines 188-200
    StructuredInsightRecord result = service.getInsight(portfolioId);
    assertThat(result.series()).allMatch(e -> e.value() >= 0 && e.value() <= 100);
}
```

**Mock ChatClient chain pattern** (`ChatServiceLivePathTest.java` lines 188-200):
```java
private ChatClient buildMockChatClient(StructuredInsightRecord returnVal) {
    ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
    ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
    ChatClient mockClient = mock(ChatClient.class);
    when(mockClient.prompt()).thenReturn(requestSpec);
    when(requestSpec.system(anyString())).thenReturn(requestSpec);
    when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
    when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
    when(requestSpec.call()).thenReturn(callSpec);
    when(callSpec.entity(StructuredInsightRecord.class)).thenReturn(returnVal);
    return mockClient;
}
```

---

### `backend/src/test/.../MultiProviderRoutingTest.java` (test, unit)

**Analog:** `ChatServiceLivePathTest.java` (overall structure) + `ChatClientStrategy.java` (lines 85-96 switch block).

**Purpose:** Prove that `ChatClientStrategy.forSession()` routes to the correct `ChatModel` based on `keyHolder.getProvider()` — mock both `AnthropicChatModel` and `OpenAiChatModel`.

**Core assertion pattern:**
```java
@Test
void anthropicProvider_buildsAnthropicModel() {
    when(keyHolder.hasKey()).thenReturn(true);
    when(keyHolder.getProvider()).thenReturn("anthropic");
    when(keyHolder.getApiKey()).thenReturn("test-key");
    ChatClient client = strategy.forSession(keyHolder);
    // assert client was built — no exception thrown, client is not null
    assertThat(client).isNotNull();
}

@Test
void openaiProvider_buildsOpenAiModel() {
    when(keyHolder.hasKey()).thenReturn(true);
    when(keyHolder.getProvider()).thenReturn("openai");
    when(keyHolder.getApiKey()).thenReturn("test-key");
    ChatClient client = strategy.forSession(keyHolder);
    assertThat(client).isNotNull();
}
```

**No Spring context needed** — `ChatClientStrategy` is a plain `@Service`; the constructor takes concrete model instances that can be mocked.

---

### `backend/src/test/.../KeyLeakageIntegrationTest.java` (extend)

**Analog:** `KeyLeakageIntegrationTest.java` (self, lines 130-153) — add two new endpoint assertions following the existing `authenticatedGet` + `doesNotContain(testKey)` pattern.

**Extension pattern** (lines 107-128 as template):
```java
// After existing commentary assertion (line 127):

// NEW: Call GET /api/ai/structured — must not return Finnhub key or LLM key
ResponseEntity<String> structuredResponse = authenticatedGet("/api/ai/structured", sessionCookie);
assertThat(structuredResponse.getStatusCode())
        .as("GET /api/ai/structured is reachable (200 demo) or fails gracefully (502 live)")
        .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
assertThat(structuredResponse.getBody())
        .as("GET /api/ai/structured response must NEVER contain the API key (T-08-LEAK)")
        .doesNotContain(testKey);
```

**Finnhub key leak extension:** The test also needs to verify that any configured `FINNHUB_API_KEY` never appears in responses. Since the test environment has no `FINNHUB_API_KEY` set (`@Value("${FINNHUB_API_KEY:}")` resolves to blank), the demo fallback path runs and Finnhub is never called. The leakage assertion is vacuously safe for `FINNHUB_API_KEY` in test, but the test documents the threat in a comment.

---

### `backend/src/test/.../AiDemoModeIntegrationTest.java` (extend)

**Analog:** `AiDemoModeIntegrationTest.java` (self, lines 59-96) — add a test proving `GET /api/ai/structured` in demo mode returns seeded content with zero network calls.

**Extension pattern** (mirrors `demoMode_commentary_returnsSeededContent_withZeroNetworkCalls` at lines 80-96):
```java
@Test
void demoMode_structured_returnsSeededContent_withZeroNetworkCalls() {
    String cookie = loginAndGetSessionCookie("alice");

    ResponseEntity<String> response = authenticatedGet("/api/ai/structured", cookie);

    assertThat(response.getStatusCode())
            .as("Demo mode GET /api/ai/structured must return 200")
            .isEqualTo(HttpStatus.OK);
    assertThat(response.getBody())
            .as("Demo structured must return seeded non-blank title")
            .contains("title")
            .doesNotContain("\"title\":\"\"");
    assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
            .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode")
            .isZero();
}
```

The `@BeforeEach resetCounter()` already resets `NEXT_CALL_COUNT.set(0)` (line 51) — the new test gets this for free.

---

### `frontend/src/stores/ai.ts` (modify — fetchStructured URL change)

**Analog:** `ai.ts` (self, lines 144-156) — change the `fetchStructured` action URL from the static file to the live backend endpoint.

**Current stub pattern** (lines 145-156):
```typescript
async function fetchStructured(): Promise<void> {
  structured.loading = true
  structured.error = null
  try {
    const { data } = await axios.get<StructuredChartDto>('/ai-structured-demo.json')
    structured.data = data
  } catch (e: any) {
    structured.error = 'Failed to load structured output data'
  } finally {
    structured.loading = false
  }
}
```

**Updated pattern** (change only the URL — one line):
```typescript
const { data } = await axios.get<StructuredChartDto>('/api/ai/structured')
```

All other code — `asyncState<StructuredChartDto>()` declaration, error handling shape, `$reset()` clearing — remains identical. The `StructuredChartDto` type from `src/api/ai.ts` is already the correct shape (`title`, `subtitle`, `series: [{label, value}]`) matching `StructuredInsightRecord` — no type changes needed.

**Error message update** (same function):
```typescript
structured.error = 'Failed to load structured output'  // drop "data" to match other errors
```

---

### `frontend/src/__tests__/components/StructuredOutputChart.test.ts` (extend)

**Analog:** `StructuredOutputChart.test.ts` (self, lines 1-74) — all 7 existing tests remain; add 1-2 new tests.

**Existing test imports pattern** (lines 1-5) — no changes:
```typescript
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import StructuredOutputChart from '../../components/ai/StructuredOutputChart.vue'
import type { StructuredChartDto } from '../../api/ai'
```

**New test pattern** (mirrors existing `renders title text and VChart when populated` at lines 25-33):
```typescript
it('renders live DTO shape from /api/ai/structured (title + series)', () => {
  // The same StructuredChartDto shape is used regardless of source (static JSON vs live API)
  const liveDto: StructuredChartDto = {
    title: 'Sector Exposure',
    subtitle: 'AI-Detected Allocation',
    series: [
      { label: 'Technology', value: 62.5 },
      { label: 'Financials', value: 18.6 },
    ],
  }
  const wrapper = mount(StructuredOutputChart, {
    props: { structured: liveDto, loading: false, error: null },
  })
  expect(wrapper.text()).toContain('Sector Exposure')
  expect(wrapper.findComponent({ name: 'VChart' }).exists()).toBe(true)
})
```

The `StructuredOutputChart.vue` component itself requires **no changes** — it already accepts `StructuredChartDto | null` as a prop and renders correctly for any conforming data.

---

## Shared Patterns

### Demo/Live Short-Circuit (applies to all new backend services)
**Source:** `DemoModeAdvisor.java` lines 84-98 (advisor) + `ChatService.java` lines 139-162 (consumer)

The canonical pattern for demo/live branching in `StructuredOutputService` differs from `CommentaryService` and `ExplainPositionService` because `StructuredOutputService` uses `.entity()` (BeanOutputConverter) on the live path. The demo path MUST explicitly parse JSON with `objectMapper.readValue()` BEFORE calling `.entity()` — the `keyHolder.hasKey()` check gates this. Mirror `ChatService.parseDemoResponse` (lines 186-228) for the `readValue` structure.

### Error Handling / Key Non-Disclosure (T-07-LEAK, T-08-LEAK)
**Source:** `ChatService.java` lines 164-170, `CommentaryService.java` lines 103-109, `ExplainPositionService.java` lines 100-107

All service catch blocks follow the same pattern:
```java
} catch (ResponseStatusException rse) {
    throw rse;
} catch (Exception e) {
    // NEVER echo e.getMessage() — may contain the API key or Finnhub token URL
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "AI provider temporarily unavailable");
}
```
Apply to `StructuredOutputService.getInsight()`. Apply to `FinnhubQuoteClient.getQuote()` catch block with `log.warn("Finnhub call failed for ticker {}", ticker)` only (RESEARCH.md Pitfall 3).

### Principal Resolution / IDOR Prevention (applies to AiController extension)
**Source:** `AiController.java` lines 161-168

```java
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```
The new `/api/ai/structured` endpoint uses this existing method — no duplication.

### Ticker Validation (applies to any new ticker-accepting endpoint or method)
**Source:** `AiController.java` lines 97-99

```java
@PathVariable @Pattern(regexp = "^[A-Z]{1,10}$",
        message = "ticker must be 1-10 uppercase letters")
String ticker
```
If a ticker path variable is added to the structured endpoint or the @Tool method receives ticker from a user message, apply the same `@Pattern` constraint.

### Idempotent Seed Upsert (applies to AiSeedRunner extension)
**Source:** `AiSeedRunner.java` lines 97-108

```java
Optional<AiSeedContent> existing = aiSeedContentRepository
        .findByTypeAndSubjectId(fixture.getType(), fixture.getSubjectId());
if (existing.isPresent()) {
    existing.get().setContent(fixture.getContent());
    aiSeedContentRepository.save(existing.get());
} else {
    aiSeedContentRepository.save(fixture);
}
```
New `STRUCTURED_INSIGHT` seeds are inserted by this same loop — no changes to the loop logic needed, only to `buildFixtures()` and the seed version constant.

### Vue Store Async State Pattern (applies to ai.ts)
**Source:** `ai.ts` lines 8-17

```typescript
interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}
function asyncState<T>(init: T | null = null): AsyncState<T> {
  return reactive({ data: init, loading: false, error: null }) as AsyncState<T>
}
```
The `structured = asyncState<StructuredChartDto>()` declaration (line 26) already follows this pattern. No new state declarations needed.

### No-Network Proof via CountingCallAdvisor (applies to integration test extensions)
**Source:** `AiDemoModeIntegrationTest.java` lines 139-167

```java
@TestConfiguration
static class NoNetworkProofConfig {
    static final AtomicInteger NEXT_CALL_COUNT = new AtomicInteger(0);

    @Bean
    CallAdvisor countingCallAdvisor() { return new CountingCallAdvisor(); }
}

static class CountingCallAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        NoNetworkProofConfig.NEXT_CALL_COUNT.incrementAndGet();
        return chain.nextCall(request);
    }
    @Override public int getOrder() { return Ordered.HIGHEST_PRECEDENCE + 1; }
}
```
The new `demoMode_structured_returnsSeededContent_withZeroNetworkCalls()` test extends the existing class with `@Import(NoNetworkProofConfig.class)` already in place — no new configuration needed.

---

## No Analog Found

Files with no close match in the codebase (use RESEARCH.md patterns instead):

| File | Role | Data Flow | Reason |
|---|---|---|---|
| `ai/tools/StockQuoteToolService.java` | service | request-response | First `@Tool`-annotated component in codebase; no Spring AI tool-calling precedent exists |
| `ai/tools/FinnhubQuoteClient.java` | service | request-response | First external HTTP integration with in-memory TTL cache; no `HttpClient` wrapper or `ConcurrentHashMap` cache pattern exists in codebase |
| `backend/src/test/.../MultiProviderRoutingTest.java` | test | — | No existing test mocks both `AnthropicChatModel` and `OpenAiChatModel` and asserts `ChatClientStrategy` routing |
| `marketdata/domain/OhlcvBarRepository.java` (new query) | repository | CRUD | New `findLatestCloseByTicker(String)` JPQL query — existing `findLatestBarBySecurityIds` takes IDs not tickers; pattern to follow is the existing `@Query` JPQL format at lines 25-32 of `OhlcvBarRepository.java` |

---

## Metadata

**Analog search scope:**
- `backend/src/main/java/com/quantlens/ai/` (all subdirectories)
- `backend/src/main/java/com/quantlens/marketdata/domain/`
- `backend/src/test/java/com/quantlens/ai/`
- `frontend/src/stores/ai.ts`
- `frontend/src/components/ai/StructuredOutputChart.vue`
- `frontend/src/__tests__/components/StructuredOutputChart.test.ts`

**Files scanned:** 18 source files read directly; 2 additional via Glob

**Pattern extraction date:** 2026-06-09
