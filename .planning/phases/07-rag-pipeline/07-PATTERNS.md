# Phase 7: RAG Pipeline - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 14 new/modified files
**Analogs found:** 11 / 14 (3 have no codebase analog — use RESEARCH.md code blocks)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `backend/.../ai/embedding/DeterministicHashingEmbeddingModel.java` | service | transform | — no analog | none — use RESEARCH.md Pattern 1 |
| `backend/.../ai/chat/RagAdvisorConfig.java` | config | request-response | `backend/.../ai/chat/ChatClientStrategy.java` | partial (same advisor chain context) |
| `backend/.../ai/rag/RagSeedRunner.java` | service | batch | `backend/.../ai/seed/AiSeedRunner.java` | exact |
| `backend/.../ai/rag/RagSeedContent.java` | model | — | `backend/.../ai/seed/AiSeedContent.java` | exact |
| `backend/.../ai/service/ChatService.java` | service | request-response | `backend/.../ai/service/CommentaryService.java` | exact |
| `backend/.../ai/api/ChatRequestDto.java` | model | — | `backend/.../ai/api/ExplainResponseDto.java` | exact |
| `backend/.../ai/api/ChatResponseDto.java` | model | — | `backend/.../ai/api/CommentaryDto.java` | exact |
| `backend/.../ai/api/AiController.java` (extend) | controller | request-response | self (existing file) | exact |
| `backend/.../ai/api/CitationDto.java` | model | — | `backend/.../ai/api/ExplainResponseDto.java` | role-match |
| `backend/src/test/.../ai/ChatDemoModeIntegrationTest.java` | test | request-response | `backend/src/test/.../ai/AiDemoModeIntegrationTest.java` | exact |
| `backend/src/test/.../ai/RagSeedIntegrationTest.java` | test | batch | `backend/src/test/.../ai/AiDemoModeIntegrationTest.java` + `AbstractPostgresIntegrationTest` | role-match |
| `backend/src/test/.../ai/KeyLeakageIntegrationTest.java` (extend) | test | request-response | self (existing file) | exact |
| `frontend/src/stores/ai.ts` (extend) | store | request-response | self (existing file) | exact |
| `frontend/src/components/ai/ChatPanel.vue` | component | request-response | `frontend/src/components/ai/CommentaryCard.vue` | exact |

---

## Pattern Assignments

### `backend/.../ai/embedding/DeterministicHashingEmbeddingModel.java` (service, transform)

**Analog:** None in codebase. Use RESEARCH.md Pattern 1 verbatim.

**No-analog reason:** The project has no existing `EmbeddingModel` implementation. This is the first pure-Java embedding class.

**Use RESEARCH.md Pattern 1 lines 210–310** — the full class including `@Component @Primary`, `call(EmbeddingRequest)`, `embed(String)`, `l2Normalize`, and inline `murmur3` hash. Reproduce exactly.

**Key flags for Wave 0 compile verification (from RESEARCH.md Assumptions Log):**
- A1: `new Embedding(float[], Integer)` constructor — verify against `spring-ai-core` JAR
- A2: `EmbeddingRequest.getInstructions()` getter name — verify at compile
- A3: `@Primary` resolves bean conflict with auto-configured `OpenAiEmbeddingModel`

**Imports pattern** (derive from RESEARCH.md Pattern 1 + standard project conventions):
```java
package com.quantlens.ai.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.Embedding;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
```

---

### `backend/.../ai/chat/RagAdvisorConfig.java` (@Configuration, request-response)

**Analog:** `backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java`

This is the closest structural analog — it lives in the same `ai.chat` package, registers beans that slot into the `List<CallAdvisor>` that `ChatClientStrategy` already injects (lines 54–56 of ChatClientStrategy), and uses the Spring AI advisor API.

**No analog in codebase for `@Configuration` bean registration of Spring AI advisors** — use RESEARCH.md Code Examples section ("Complete RagAdvisorConfig.java") verbatim.

**Advisor auto-registration context** (ChatClientStrategy.java lines 54–63):
```java
// ChatClientStrategy already injects ALL CallAdvisor beans and sorts them:
private final List<CallAdvisor> advisors;

public ChatClientStrategy(AnthropicChatModel baseAnthropicModel,
                          OpenAiChatModel baseOpenAiModel,
                          List<CallAdvisor> advisors) {
    this.advisors = advisors.stream()
            .sorted(AnnotationAwareOrderComparator.INSTANCE)
            .toList();
}
```
No changes needed to `ChatClientStrategy`. The `@Bean`-declared `QuestionAnswerAdvisor` and `MessageChatMemoryAdvisor` from `RagAdvisorConfig` are picked up automatically.

**Use RESEARCH.md Code Examples — "Complete RagAdvisorConfig.java"** (lines 826–858 of RESEARCH.md):
```java
@Configuration
public class RagAdvisorConfig {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
            .maxMessages(20)
            .build();
    }

    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
            .order(Ordered.HIGHEST_PRECEDENCE + 20)
            .build();
    }

    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore)
            .searchRequest(SearchRequest.builder()
                .topK(4)
                .similarityThreshold(0.4)
                .build())
            .order(Ordered.HIGHEST_PRECEDENCE + 10)
            .build();
    }
}
```

**Assumption A8 (highest risk):** `QuestionAnswerAdvisor` must implement `CallAdvisor` for `ChatClientStrategy`'s `List<CallAdvisor>` injection to pick it up. If it implements a different interface, register it explicitly via `ChatClientStrategy` constructor injection instead.

---

### `backend/.../ai/rag/RagSeedRunner.java` (service, batch)

**Analog:** `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java`

**Imports pattern** (AiSeedRunner.java lines 1–16):
```java
package com.quantlens.ai.rag;

import com.quantlens.seed.SeedLog;
import com.quantlens.seed.SeedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
```

**Class declaration + order** (AiSeedRunner.java lines 59–75 pattern):
```java
@Component
@Order(3)   // after AiSeedRunner @Order(2)
public class RagSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSeedRunner.class);
    private static final String RAG_SEED_VERSION = "rag-v1";

    private final VectorStore vectorStore;
    private final SeedLogRepository seedLogRepository;

    public RagSeedRunner(VectorStore vectorStore,
                         SeedLogRepository seedLogRepository) {
        this.vectorStore        = vectorStore;
        this.seedLogRepository  = seedLogRepository;
    }
```

**Idempotency guard pattern** (AiSeedRunner.java lines 79–117 — copy this structure exactly):
```java
@Override
@Transactional
public void run(ApplicationArguments args) {
    boolean alreadyDone = seedLogRepository.findById(RAG_SEED_VERSION)
            .map(SeedLog::isCompleted)
            .orElse(false);
    if (alreadyDone) {
        log.info("RagSeedRunner: seed_log {} already completed — skipping", RAG_SEED_VERSION);
        return;
    }

    log.info("RagSeedRunner: starting RAG corpus seed (seed_log {} not yet completed)...",
            RAG_SEED_VERSION);

    List<Document> chunks = buildChunks();
    vectorStore.add(chunks);
    log.info("RagSeedRunner: seeded {} chunks into vector_store", chunks.size());

    // Mark seed complete — written LAST so a mid-seed failure rolls back (T-06-07)
    SeedLog seedLog = new SeedLog(RAG_SEED_VERSION);
    seedLog.setCompleted(true);
    seedLog.setCompletedAt(LocalDateTime.now());
    seedLogRepository.save(seedLog);

    log.info("RagSeedRunner: seed_log {} marked completed", RAG_SEED_VERSION);
}
```

**Chunk authoring pattern** (RESEARCH.md lines 882–903):
```java
private List<Document> buildChunks() {
    return List.of(
        new Document(
            "Apple Inc. faces significant regulatory scrutiny regarding its App Store policies...",
            Map.of("ticker", "AAPL", "section", "Risk Factors",
                   "source", "AAPL 10-K FY2023", "year", "2023")
        ),
        // ... 10-20 chunks across AAPL, MSFT, NVDA, JPM, XOM
        // Sections: "Risk Factors", "MD&A", "Business Overview"
        // 200-500 words per chunk, authored Java string literals (no PDF parsing)
    );
}
```

---

### `backend/.../ai/rag/RagSeedContent.java` (model, data class)

**Analog:** `backend/src/main/java/com/quantlens/ai/seed/AiSeedContent.java`

This class is a lightweight metadata holder for chunk construction, NOT a JPA entity (chunks go into `vector_store` via `VectorStore.add()`, not via JPA). Model it as a simple record or POJO:

**Pattern** (AiSeedContent.java lines 26–81 — simplified, no JPA):
```java
package com.quantlens.ai.rag;

/**
 * Immutable data holder for a 10-K corpus chunk before seeding into vector_store.
 * NOT a JPA entity — chunks are stored via VectorStore.add(List<Document>).
 */
public record RagSeedContent(
    String text,      // chunk prose (200-500 words)
    String ticker,    // e.g. "AAPL"
    String section,   // e.g. "Risk Factors"
    String source,    // e.g. "AAPL 10-K FY2023"
    String year       // e.g. "2023"
) {
    public org.springframework.ai.document.Document toDocument() {
        return new org.springframework.ai.document.Document(
            text,
            java.util.Map.of(
                "ticker",  ticker,
                "section", section,
                "source",  source,
                "year",    year
            )
        );
    }
}
```

---

### `backend/.../ai/service/ChatService.java` (service, request-response)

**Analog:** `backend/src/main/java/com/quantlens/ai/service/CommentaryService.java`

This is the closest exact analog: same pattern of injecting `ChatClientStrategy` + `LlmKeySessionHolder`, calling `.forSession(keyHolder).prompt()...advisors(spec->spec.param(...))...call()`, wrapping exceptions as 502, and returning a typed DTO. Only the DTO shape and advisor params differ.

**Imports pattern** (CommentaryService.java lines 1–16):
```java
package com.quantlens.ai.service;

import com.quantlens.ai.api.ChatResponseDto;
import com.quantlens.ai.api.CitationDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
```

**Core service pattern** (CommentaryService.java lines 55–110 — copy structure verbatim, change params):
```java
@Service
public class ChatService {

    private static final String CHAT_SYSTEM_PROMPT =
            "You are a knowledgeable financial analyst assistant. " +
            "Answer questions about the portfolio and referenced 10-K filings. " +
            "Base your answer on the provided filing context. Be concise and accurate.";

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;

    public ChatService(ChatClientStrategy strategy, LlmKeySessionHolder keyHolder) {
        this.strategy  = strategy;
        this.keyHolder = keyHolder;
    }

    public ChatResponseDto chat(String message, String conversationId) {
        try {
            ChatClientResponse clientResponse = strategy.forSession(keyHolder)
                    .prompt()
                    .system(CHAT_SYSTEM_PROMPT)
                    .user(message)
                    .advisors(spec -> spec
                            .param("AI_SEED_TYPE",    "RAG_QA")
                            .param("AI_SEED_SUBJECT", "DEFAULT")
                            .param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .chatClientResponse();

            String answer = clientResponse.chatResponse()
                    .getResult()
                    .getOutput()
                    .getText();

            List<Document> docs = clientResponse.chatResponse()
                    .getMetadata()
                    .<List<Document>>get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

            List<CitationDto> citations = (docs != null)
                    ? docs.stream()
                          .map(d -> new CitationDto(
                              (String) d.getMetadata().getOrDefault("ticker",  ""),
                              (String) d.getMetadata().getOrDefault("section", ""),
                              (String) d.getMetadata().getOrDefault("source",  ""),
                              d.getText().substring(0, Math.min(200, d.getText().length()))))
                          .toList()
                    : List.of();

            return new ChatResponseDto(answer != null ? answer : "", citations);
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            // T-06-09: never echo provider error messages (could carry the key)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider temporarily unavailable");
        }
    }
}
```

**RESEARCH.md flag A5:** `clientResponse.chatResponse().getMetadata().get(RETRIEVED_DOCUMENTS)` return type — may need explicit cast `.<List<Document>>get(...)`. Verify at compile.

---

### `backend/.../ai/api/ChatRequestDto.java` (model, DTO)

**Analog:** `backend/src/main/java/com/quantlens/ai/api/ExplainResponseDto.java`

**Pattern** (ExplainResponseDto.java lines 1–14 — record style):
```java
package com.quantlens.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/ai/chat.
 *
 * @param message        the user's question (required, max 2000 chars for DoS prevention)
 * @param conversationId optional — defaults to HTTP session ID if null or blank
 */
public record ChatRequestDto(
        @NotBlank @Size(max = 2000) String message,
        String conversationId
) {}
```

---

### `backend/.../ai/api/ChatResponseDto.java` (model, DTO)

**Analog:** `backend/src/main/java/com/quantlens/ai/api/CommentaryDto.java`

**Pattern** (CommentaryDto.java lines 1–19 — record with List field):
```java
package com.quantlens.ai.api;

import java.util.List;

/**
 * Response DTO for POST /api/ai/chat.
 *
 * @param answer    the generated (or demo-authored) answer text
 * @param citations retrieved 10-K chunks that grounded the answer; empty in demo mode
 *                  (citations come from authored seed content in that case)
 */
public record ChatResponseDto(
        String answer,
        List<CitationDto> citations
) {}
```

---

### `backend/.../ai/api/CitationDto.java` (model, DTO)

**Analog:** `backend/src/main/java/com/quantlens/ai/api/ExplainResponseDto.java`

**Pattern** (same record style):
```java
package com.quantlens.ai.api;

/**
 * A single 10-K chunk citation returned alongside a chat answer.
 *
 * @param ticker   ticker symbol from chunk metadata (e.g. "AAPL")
 * @param section  filing section (e.g. "Risk Factors")
 * @param source   filing reference (e.g. "AAPL 10-K FY2023")
 * @param excerpt  first ~200 chars of the retrieved chunk text
 */
public record CitationDto(
        String ticker,
        String section,
        String source,
        String excerpt
) {}
```

---

### `backend/.../ai/api/AiController.java` (extend — add POST /chat endpoint)

**Analog:** Self. The new `@PostMapping("/chat")` follows the exact pattern of the existing `@GetMapping("/explain/{ticker}")` (lines 86–95) and `@GetMapping("/commentary")` (lines 103–108).

**Existing controller injection pattern** (AiController.java lines 58–68 — add `ChatService` + `HttpSession`):
```java
// Add to constructor:
private final ChatService chatService;

// Updated constructor:
public AiController(PortfolioRepository portfolioRepository,
                    ExplainPositionService explainService,
                    CommentaryService commentaryService,
                    ChatService chatService) {
    this.portfolioRepository = portfolioRepository;
    this.explainService      = explainService;
    this.commentaryService   = commentaryService;
    this.chatService         = chatService;
}
```

**New endpoint pattern** (RESEARCH.md Pattern 8 lines 630–644):
```java
@PostMapping("/chat")
public ResponseEntity<ChatResponseDto> chat(
        @RequestBody @Valid ChatRequestDto request,
        Authentication auth,
        HttpSession session) {
    // conversationId falls back to session ID (MessageChatMemoryAdvisor requires it)
    String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
            ? request.conversationId()
            : session.getId();
    ChatResponseDto response = chatService.chat(request.message(), conversationId);
    return ResponseEntity.ok(response);
}
```

**Auth pattern** (AiController.java lines 125–132 — resolvePortfolioId NOT required for chat, which is not portfolio-scoped, but authentication is still required via Spring Security):
```java
// No resolvePortfolioId call needed for chat — no portfolio data is accessed.
// Spring Security enforces authentication on /api/** already.
// If per-portfolio scoping of Q&A is desired in v2, follow resolvePortfolioId pattern.
```

**Add imports:**
```java
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
```

---

### `backend/src/test/.../ai/ChatDemoModeIntegrationTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java`

This is an exact copy of the no-network proof pattern. Replicate the `CountingCallAdvisor` + `NoNetworkProofConfig` inner class structure verbatim.

**Class declaration + import pattern** (AiDemoModeIntegrationTest.java lines 1–46):
```java
package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.*;
import org.springframework.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;

@Import(ChatDemoModeIntegrationTest.NoNetworkProofConfig.class)
class ChatDemoModeIntegrationTest extends AbstractPostgresIntegrationTest {
```

**CountingCallAdvisor inner class pattern** (AiDemoModeIntegrationTest.java lines 139–167 — copy verbatim):
```java
@TestConfiguration
static class NoNetworkProofConfig {
    static final AtomicInteger NEXT_CALL_COUNT = new AtomicInteger(0);

    @Bean
    CallAdvisor countingCallAdvisor() {
        return new CountingCallAdvisor();
    }
}

static class CountingCallAdvisor implements CallAdvisor {
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        NoNetworkProofConfig.NEXT_CALL_COUNT.incrementAndGet();
        return chain.nextCall(request);
    }

    @Override
    public String getName() { return "CountingCallAdvisor"; }

    @Override
    public int getOrder() {
        // Just after DemoModeAdvisor (HIGHEST_PRECEDENCE) — reached only if it does not short-circuit
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
```

**POST helper pattern** (derive from loginAndGetSessionCookie in AiDemoModeIntegrationTest.java lines 117–133, extended with POST body):
```java
private ResponseEntity<String> authenticatedPost(String path, String json, String sessionCookie) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.add(HttpHeaders.COOKIE, sessionCookie);
    return restTemplate.exchange(path, HttpMethod.POST,
            new HttpEntity<>(json, headers), String.class);
}
```

**Key test assertions for chat:**
```java
@Test
void demoMode_chat_returnsAnswer_withZeroNetworkCalls() {
    String cookie = loginAndGetSessionCookie("alice");

    ResponseEntity<String> response = authenticatedPost(
            "/api/ai/chat",
            "{\"message\":\"What are Apple's key risks?\"}",
            cookie);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"answer\"").doesNotContain("\"answer\":\"\"");
    assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
            .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode")
            .isZero();
}
```

---

### `backend/src/test/.../ai/RagSeedIntegrationTest.java` (test, batch)

**Analog:** `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java` (base) + `AiDemoModeIntegrationTest.java` (structure)

**Class base pattern** (AbstractPostgresIntegrationTest.java lines 1–62 — extend directly):
```java
package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;

class RagSeedIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Test
    void seededChunks_findable_withDeterministicEmbedding() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Apple regulatory risk App Store")
                        .topK(4)
                        .similarityThreshold(0.1)   // low threshold for deterministic model
                        .build());
        assertThat(results).isNotEmpty();
        assertThat(results).anyMatch(d ->
                "AAPL".equals(d.getMetadata().get("ticker")));
    }

    @Test
    void ragSeed_isIdempotent() {
        // Running seed again should not double the chunks (seed_log guard)
        // The vectorStore row count check verifies idempotency
        // (Inject SeedLogRepository and check rag-v1 is completed)
    }
}
```

---

### `backend/src/test/.../ai/KeyLeakageIntegrationTest.java` (extend)

**Analog:** Self. Add `/api/ai/chat` to the existing leakage assertions.

**Extension pattern** (KeyLeakageIntegrationTest.java lines 108–137 — add after existing commentary check):
```java
// 5. Call POST /api/ai/chat — must not return key
HttpHeaders chatHeaders = new HttpHeaders();
chatHeaders.setContentType(MediaType.APPLICATION_JSON);
chatHeaders.add(HttpHeaders.COOKIE, sessionCookie);
String chatPayload = "{\"message\":\"What are the key risks?\"}";
ResponseEntity<String> chatResponse = restTemplate.exchange(
        "/api/ai/chat", HttpMethod.POST,
        new HttpEntity<>(chatPayload, chatHeaders), String.class);
assertThat(chatResponse.getBody())
        .as("POST /api/ai/chat response must NEVER contain the API key (T-06-01)")
        .doesNotContain(testKey);
```

---

### `frontend/src/stores/ai.ts` (extend)

**Analog:** Self. Add `chatMessages`, `chatLoading`, `chatError`, `sendMessage` alongside existing pattern.

**Existing asyncState + action pattern** (ai.ts lines 8–17 and lines 118–129 — follow exactly):
```typescript
// Add to existing state section (follow existing asyncState convention):
const chatMessages = ref<ChatMessage[]>([])
const chatLoading = ref(false)
const chatError = ref<string | null>(null)

// Add interfaces near top of file:
export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
}

export interface Citation {
  ticker: string
  section: string
  source: string
  excerpt: string
}
```

**sendMessage action pattern** (follow fetchCommentary lines 118–129 structure — try/finally, status assignment, error string):
```typescript
async function sendMessage(message: string, conversationId?: string): Promise<void> {
  chatMessages.value.push({ role: 'user', content: message })
  chatLoading.value = true
  chatError.value = null
  try {
    const { data } = await axios.post<{ answer: string; citations: Citation[] }>(
      '/api/ai/chat',
      { message, conversationId }
    )
    chatMessages.value.push({
      role: 'assistant',
      content: data.answer,
      citations: data.citations ?? []
    })
  } catch (e: any) {
    chatError.value = e?.response?.status === 401 ? 'Session expired' : 'Failed to get AI response'
  } finally {
    chatLoading.value = false
  }
}
```

**$reset extension** (ai.ts lines 158–163 — add chat fields):
```typescript
// Add to $reset():
chatMessages.value = []; chatLoading.value = false; chatError.value = null
```

**Return object extension** (ai.ts lines 166–180 — add new state + action):
```typescript
return {
  // existing...
  chatMessages, chatLoading, chatError,
  sendMessage,
}
```

**Also add to `frontend/src/api/ai.ts`** (same file, follow existing interface pattern at lines 10–34):
```typescript
export interface Citation {
  ticker: string
  section: string
  source: string
  excerpt: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  citations?: Citation[]
}

export interface ChatResponseDto {
  answer: string
  citations: Citation[]
}
```

---

### `frontend/src/components/ai/ChatPanel.vue` (component, request-response)

**Analog:** `frontend/src/components/ai/CommentaryCard.vue`

This is the exact structural analog: same three-state pattern (loading/error/populated), same `defineProps` + `defineEmits` style, same skeleton shimmer CSS, same dark-theme CSS variables, same `role="alert"` for errors, same retry button pattern.

**Script setup + props pattern** (CommentaryCard.vue lines 1–21 — adapt for chat):
```vue
<script setup lang="ts">
import type { ChatMessage, Citation } from '../../api/ai'
import { ref, nextTick, watch } from 'vue'

const props = defineProps<{
  messages: ChatMessage[]
  loading: boolean
  error: string | null
}>()

const emit = defineEmits<{
  send: [message: string]
}>()

const inputValue = ref('')
const messageList = ref<HTMLElement | null>(null)

// Scroll to bottom when messages change
watch(() => props.messages.length, async () => {
  await nextTick()
  if (messageList.value) {
    messageList.value.scrollTop = messageList.value.scrollHeight
  }
})

function submitMessage() {
  if (!inputValue.value.trim() || props.loading) return
  emit('send', inputValue.value.trim())
  inputValue.value = ''
}
</script>
```

**Template three-state pattern** (CommentaryCard.vue lines 23–70 — adapt):
```vue
<template>
  <div class="chat-panel">
    <!-- Header with AiModeBadge slot -->
    <div class="chat-header">
      <span class="chat-label">AI Q&amp;A</span>
    </div>

    <!-- Message list -->
    <div class="message-list" ref="messageList">
      <!-- Empty state -->
      <div v-if="props.messages.length === 0 && !props.loading" class="chat-empty">
        Ask a question about your portfolio or the 10-K filings…
      </div>

      <!-- Messages -->
      <div
        v-for="(msg, i) in props.messages"
        :key="i"
        :class="['message', msg.role === 'user' ? 'message--user' : 'message--assistant']"
      >
        <p class="message-content">{{ msg.content }}</p>
        <!-- Citation chips -->
        <div v-if="msg.citations && msg.citations.length > 0" class="citation-list">
          <span
            v-for="(c, ci) in msg.citations"
            :key="ci"
            class="citation-chip"
            :title="c.excerpt"
          >{{ c.ticker }} · {{ c.section }}</span>
        </div>
      </div>

      <!-- Loading shimmer (follow CommentaryCard.vue lines 27–35 skeleton pattern) -->
      <template v-if="props.loading">
        <div class="skeleton" style="height: 14px; width: 75%; margin-bottom: 8px" aria-hidden="true" />
        <div class="skeleton" style="height: 14px; width: 55%;" aria-hidden="true" />
      </template>
    </div>

    <!-- Error (follow CommentaryCard.vue lines 38–46 error pattern) -->
    <div v-if="props.error" class="chat-error" role="alert">
      <span>{{ props.error }}</span>
    </div>

    <!-- Input -->
    <div class="chat-input-row">
      <input
        v-model="inputValue"
        class="chat-input"
        placeholder="Ask about your portfolio or 10-K filings…"
        :disabled="props.loading"
        @keydown.enter.prevent="submitMessage"
        aria-label="Chat input"
      />
      <button
        class="send-btn"
        :disabled="props.loading || !inputValue.trim()"
        @click="submitMessage"
      >Send</button>
    </div>
  </div>
</template>
```

**CSS pattern** (CommentaryCard.vue lines 73–188 — copy CSS variables verbatim, add chat-specific layout):
```vue
<style scoped>
/* Card wrapper — copy from CommentaryCard.vue lines 74–82 */
.chat-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-lg);
  min-height: 240px;
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}

/* Skeleton shimmer — copy verbatim from CommentaryCard.vue lines 171–188 */
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
.skeleton {
  display: block;
  border-radius: var(--radius-sm);
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
}

/* Citation chip — accent-colored micro-badge */
.citation-chip {
  display: inline-flex;
  align-items: center;
  padding: 2px 8px;
  border-radius: var(--radius-pill);
  font-size: 11px;
  font-weight: 600;
  color: var(--color-accent);
  border: 1px solid var(--color-accent);
  background: var(--color-accent-subtle);
  cursor: default;
}
/* ... remaining layout styles at planner's discretion */
</style>
```

**DashboardView wiring** (DashboardView.vue line 272 — replace SlotPlaceholder):
```vue
<!-- Replace: <SlotPlaceholder label="AI Q&A — Phase 7" .../> -->
<ChatPanel
  :messages="aiStore.chatMessages"
  :loading="aiStore.chatLoading"
  :error="aiStore.chatError"
  @send="aiStore.sendMessage($event)"
  style="margin-top: 16px;"
/>
```

---

### `frontend/src/components/ai/ChatPanel.spec.ts` (test, component)

**Analog:** No spec files exist in the codebase (no `.spec.ts` found). Use standard Vue Test Utils + Vitest pattern. Reference RESEARCH.md validation section (lines 1003):

```typescript
// ChatPanel.spec.ts — Vitest + @vue/test-utils
import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import ChatPanel from './ChatPanel.vue'

describe('ChatPanel', () => {
  it('emits send event on button click', async () => {
    const wrapper = mount(ChatPanel, {
      props: { messages: [], loading: false, error: null }
    })
    await wrapper.find('.chat-input').setValue('What are AAPL risks?')
    await wrapper.find('.send-btn').trigger('click')
    expect(wrapper.emitted('send')?.[0]).toEqual(['What are AAPL risks?'])
  })

  it('renders user message and assistant message with citations', async () => {
    const messages = [
      { role: 'user', content: 'What are Apple risks?' },
      {
        role: 'assistant',
        content: 'Regulatory risk is significant.',
        citations: [{ ticker: 'AAPL', section: 'Risk Factors', source: 'AAPL 10-K FY2023', excerpt: '...' }]
      }
    ]
    const wrapper = mount(ChatPanel, { props: { messages, loading: false, error: null } })
    expect(wrapper.text()).toContain('What are Apple risks?')
    expect(wrapper.text()).toContain('Regulatory risk is significant.')
    expect(wrapper.find('.citation-chip').text()).toContain('AAPL')
  })

  it('disables input while loading', () => {
    const wrapper = mount(ChatPanel, {
      props: { messages: [], loading: true, error: null }
    })
    expect(wrapper.find('.chat-input').attributes('disabled')).toBeDefined()
    expect(wrapper.find('.send-btn').attributes('disabled')).toBeDefined()
  })
})
```

---

## Shared Patterns

### Authentication + Principal Scoping
**Source:** `backend/src/main/java/com/quantlens/ai/api/AiController.java` lines 125–132
**Apply to:** `AiController.java` (new `/chat` endpoint), `ChatService.java`

```java
// resolvePortfolioId NOT needed for /chat (not portfolio-scoped queries)
// But authentication IS required — Spring Security enforces this on /api/**
// Never accept portfolioId as @RequestParam or @PathVariable (IDOR prevention)
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

### Error Handling (502 wrapper, no message echo)
**Source:** `backend/src/main/java/com/quantlens/ai/service/CommentaryService.java` lines 103–109
**Apply to:** `ChatService.java`

```java
// T-06-09: never echo provider error messages (could carry the key)
} catch (ResponseStatusException rse) {
    throw rse;
} catch (Exception e) {
    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "AI provider temporarily unavailable");
}
```

### Idempotency Guard (seed_log)
**Source:** `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java` lines 79–87 + 111–117
**Apply to:** `RagSeedRunner.java`

```java
boolean alreadyDone = seedLogRepository.findById(AI_SEED_VERSION)
        .map(SeedLog::isCompleted)
        .orElse(false);
if (alreadyDone) {
    log.info("RagSeedRunner: seed_log {} already completed — skipping", RAG_SEED_VERSION);
    return;
}
// ... seed ...
// Mark complete LAST so mid-seed failure rolls back:
SeedLog seedLog = new SeedLog(RAG_SEED_VERSION);
seedLog.setCompleted(true);
seedLog.setCompletedAt(LocalDateTime.now());
seedLogRepository.save(seedLog);
```

### No-Network Executable Proof (CountingCallAdvisor)
**Source:** `backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java` lines 139–167
**Apply to:** `ChatDemoModeIntegrationTest.java`

```java
// Copy CountingCallAdvisor + NoNetworkProofConfig inner classes verbatim.
// Assert: NoNetworkProofConfig.NEXT_CALL_COUNT.get() == 0 after demo-mode call.
```

### Vue Component Three-State Shell (loading / error / populated)
**Source:** `frontend/src/components/ai/CommentaryCard.vue` lines 27–71 + skeleton CSS lines 171–188
**Apply to:** `ChatPanel.vue`

```vue
<!-- Three states, copied from CommentaryCard pattern:
  1. v-if="loading" → shimmer skeleton divs with aria-hidden
  2. v-else-if="error" → role="alert" div with static copy + optional retry button
  3. v-else-if="data" → populated content
  (empty state: v-else fallback)
-->
```

### Pinia asyncState Action Pattern
**Source:** `frontend/src/stores/ai.ts` lines 118–129 (fetchCommentary)
**Apply to:** `sendMessage` action in `ai.ts`

```typescript
// Pattern: set loading=true → clear error → try axios → assign data → catch error string → finally loading=false
// sendMessage deviates slightly: pushes to messages array rather than assigning .data
// Error string follows: e?.response?.status === 401 ? 'Session expired' : 'Failed to ...'
```

### CSS Design Token Usage
**Source:** `frontend/src/components/ai/CommentaryCard.vue` lines 73–188
**Apply to:** `ChatPanel.vue` — use same CSS variables (never raw colors):
- `var(--color-bg-surface)`, `var(--color-bg-overlay)`, `var(--color-bg-elevated)`
- `var(--color-text-primary)`, `var(--color-text-secondary)`, `var(--color-text-muted)`
- `var(--color-accent)`, `var(--color-accent-subtle)`, `var(--color-border)`, `var(--color-down)`
- `var(--radius-sm)`, `var(--radius-md)`, `var(--radius-lg)`, `var(--radius-pill)`
- `var(--space-xs)`, `var(--space-sm)`, `var(--space-md)`, `var(--space-lg)`
- `var(--shadow-card)`

---

## No Analog Found

Files with no close codebase match (planner uses RESEARCH.md code blocks):

| File | Role | Data Flow | Reason | RESEARCH.md Reference |
|---|---|---|---|---|
| `DeterministicHashingEmbeddingModel.java` | service | transform | No `EmbeddingModel` implementation exists in codebase | Pattern 1 (lines 210–310) — full class, copy verbatim |
| `RagAdvisorConfig.java` | config | — | No `@Configuration` for Spring AI advisors exists | Code Examples section — "Complete RagAdvisorConfig.java" |
| `ChatPanel.spec.ts` | test | component | No frontend spec files exist in the project | RESEARCH.md validation section + standard Vitest/Vue Test Utils patterns |

---

## Critical Wave 0 Compile Flags

These assumptions from RESEARCH.md must be resolved before proceeding:

| ID | Flag | File | Fix if Wrong |
|---|---|---|---|
| A1 | `new Embedding(float[], Integer)` constructor | `DeterministicHashingEmbeddingModel` | Check `spring-ai-core` JAR via IDE autocomplete |
| A2 | `EmbeddingRequest.getInstructions()` getter | `DeterministicHashingEmbeddingModel` | Check actual getter name on `EmbeddingRequest` |
| A3 | `@Primary` resolves `EmbeddingModel` bean conflict | `DeterministicHashingEmbeddingModel` | Add `@ConditionalOnProperty` if needed |
| A4 | `MessageChatMemoryAdvisor.builder(chatMemory).order(int).build()` | `RagAdvisorConfig` | Set order via `Ordered` directly if builder lacks `.order()` |
| A5 | `chatResponse().getMetadata().<List<Document>>get(RETRIEVED_DOCUMENTS)` | `ChatService` | Try `chatClientResponse().context().get(...)` if null |
| A8 | `QuestionAnswerAdvisor` implements `CallAdvisor` | `ChatClientStrategy` auto-pickup | Inject `VectorStore` directly into strategy if interface mismatch |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/ai/`, `backend/src/test/java/com/quantlens/ai/`, `frontend/src/stores/`, `frontend/src/components/ai/`
**Files scanned:** 16 source files + 10 test files + 5 frontend files
**Pattern extraction date:** 2026-06-09
