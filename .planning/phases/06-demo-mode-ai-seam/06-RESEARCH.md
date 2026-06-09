# Phase 6: Demo-Mode AI Seam (Spring AI 1.1.6) - Research

**Researched:** 2026-06-09
**Domain:** Spring AI 1.1.6 advisor chain, per-request key injection, demo↔live seam, key-leak prevention
**Confidence:** HIGH (API shapes verified against official Javadoc 1.1.x and Anthropic model docs; key-injection pattern confirmed via GitHub source; one architectural workaround tagged [ASSUMED] below)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Add `spring-ai-starter-model-anthropic` and `spring-ai-starter-model-openai` starters (BOM 1.1.6 already pinned). New `com.quantlens.ai` Modulith module.
- `DemoModeAdvisor` implements Spring AI's `CallAdvisor`, placed FIRST in the advisor chain. Reads session-scoped `LlmKeySessionHolder`. No key → short-circuit with seeded `ChatResponse`. Key present → pass through to real provider.
- Per-request key injection via the provider model's Builder (see Section "Per-Request Key Injection" below for verified pattern).
- `LlmKeySessionHolder` = `@SessionScope` bean: `{provider, apiKey, demoMode}`. Set via `POST /api/ai/key`; cleared on logout / `DELETE /api/ai/key`.
- Key NEVER logged, echoed back, or persisted. A dedicated key-leakage integration test is the security gate.
- `GET /api/ai/status` returns `{mode, provider?}` only — never the key.
- Seeded content stored as authored fixtures in DB table `ai_seed_content(type, subject_id, content)`, populated by idempotent seeder.
- `GET /api/ai/explain/{holdingId}` (AI-07) and `GET /api/ai/commentary` (AI-08) go through ChatClient + DemoModeAdvisor.
- Frontend: BYO-key popup (provider selector + password input); `ai` Pinia store; mode badge in top bar. Key never to localStorage.
- Default live model: `claude-sonnet-4-6` (Anthropic). Default OpenAI live model: `gpt-4o`.
- Structured-output panel stub establishes the seam; live wiring in Phase 8.

### Claude's Discretion
- Exact advisor wiring vs a strategy bean, fixture storage shape, endpoint grouping, how to boot Spring AI starters key-less, popup UX details.
- Research to confirm Spring AI 1.1.6 `CallAdvisor` API, key-less startup, and model key injection.

### Deferred Ideas (OUT OF SCOPE)
- RAG Q&A over filings (Phase 7).
- Live tool calling / structured output driving a chart (Phase 8).
- `@McpTool` server (Phase 9).
- Persisting user keys (explicitly out of scope — session-only).
- Streaming responses (v2 — AI-09).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| AI-01 | All AI features work with zero API key via seeded responses (demo mode), authored against seeded data so they match the charts | Seeded fixture table + DemoModeAdvisor short-circuit pattern |
| AI-02 | User can open BYO-key popup, choose a provider, enter a session-only key that switches AI to live | LlmKeySessionHolder @SessionScope pattern; per-request model rebuild; leakage test gate |
| AI-07 | User can click a holding to get an AI-generated "explain this position" narrative | GET /api/ai/explain/{holdingId} + ExplainPositionService + drawer component |
| AI-08 | User sees an AI-generated daily portfolio commentary on the dashboard | GET /api/ai/commentary + CommentaryService + dashboard card |
</phase_requirements>

---

## Summary

Phase 6 installs the Spring AI layer from zero and delivers the demo↔live seam that all subsequent AI phases (7, 8) extend. The keystone design is a `DemoModeAdvisor` (implements `CallAdvisor`) placed first in every `ChatClient` advisor chain. In demo mode (no key) it short-circuits the chain, returning authored seed content from the DB without calling any provider. In live mode (key present in session) it passes through — the controller rebuilds the `ChatModel` per-request with the session key injected via the provider's `Builder` pattern.

The critical API-key startup constraint requires a concrete workaround: the Spring AI Anthropic and OpenAI starters will fail to start if `spring.ai.anthropic.api-key` / `spring.ai.openai.api-key` are absent or blank. The safe solution confirmed for 1.1.x is to supply a placeholder non-blank sentinel value (e.g., `"DEMO_NO_KEY"`) in `application.yml`, then override per-request with the session key via a newly constructed `AnthropicApi`/`OpenAiApi` + model `Builder`. The `DemoModeAdvisor` short-circuits before any network call fires, so the sentinel never reaches the provider.

The per-request key injection pattern for 1.1.x is: `AnthropicApi.builder().apiKey(sessionKey).build()` → `AnthropicChatModel.builder().anthropicApi(newApi)..build()` (and the analogous OpenAI path). The `mutate()` shorthand exists on `OpenAiChatModel` only; Anthropic requires the full Builder. Both are verified against official 1.1.x Javadoc.

Model IDs verified against official Anthropic docs: `claude-sonnet-4-6` (default live), `claude-opus-4-8`, `claude-haiku-4-5-20251001`. Spring AI's own docs still list `claude-sonnet-4-5` as its default — override in `application.yml` with `claude-sonnet-4-6`.

**Primary recommendation:** Implement the `DemoModeAdvisor` as the sole demo/live switch in one place; never scatter `if (demoMode)` in services; all AI features route through the same `ChatClientStrategy.forSession()` factory, which always runs through the advisor chain.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Demo/live mode toggle | API / Backend — DemoModeAdvisor | — | Session state and ChatModel construction are server-side; client only shows mode badge |
| LLM key storage | API / Backend — @SessionScope bean | — | Keys MUST NOT leave server; session bean enforces no-persistence |
| Key submission UI | Browser / Client | Frontend Server (Spring MVC) | Popup → POST /api/ai/key; key travels one-way client→server |
| Mode status UI | Browser / Client | API (GET /api/ai/status) | Badge reads status endpoint; key never returned |
| Seeded AI content | Database / Storage | API service layer | Fixtures in `ai_seed_content` table; queried by feature + subject |
| Explain-position narrative | API / Backend | Browser (renders in drawer) | ChatClientStrategy → DemoModeAdvisor → seeded or live |
| Daily commentary | API / Backend | Browser (renders as card) | Same path as above |
| Provider model construction | API / Backend — ChatClientStrategy | — | Per-request Builder construction stays on server |

---

## Standard Stack

### Core (additions for Phase 6 — all BOM-managed by spring-ai-bom:1.1.6)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-starter-model-anthropic` | 1.1.6 (BOM) | Anthropic ChatModel auto-config | Official starter for Claude; includes AnthropicApi, AnthropicChatModel, auto-config |
| `spring-ai-starter-model-openai` | 1.1.6 (BOM) | OpenAI ChatModel auto-config | Official starter for GPT; includes OpenAiApi, OpenAiChatModel |

[VERIFIED: official Spring AI docs https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html]
[VERIFIED: official Spring AI docs https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html]

### Supporting (existing — no new additions needed for Phase 6)

| Library | Version | Purpose |
|---------|---------|---------|
| `spring-boot-starter-data-jpa` | (Boot-managed) | ai_seed_content repository |
| `spring-boot-starter-security` | (Boot-managed) | SecurityConfig — /api/ai/* endpoints authenticated |
| `spring-boot-starter-web` | (Boot-managed) | REST controllers |
| Vue 3 + Pinia + axios | existing | ai store + popup modal |

### pom.xml additions

```xml
<!-- Add to <dependencies> — versions resolved from spring-ai-bom:1.1.6 -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

---

## Package Legitimacy Audit

These are the Spring AI starters governed by the spring-ai-bom:1.1.6 already pinned in pom.xml. Both artifact IDs are from `org.springframework.ai`, the official Spring project group.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `spring-ai-starter-model-anthropic` | Maven Central (org.springframework.ai) | Part of Spring AI 1.0 GA (Jun 2025) | High (Spring project) | github.com/spring-projects/spring-ai | N/A — official Spring project | Approved |
| `spring-ai-starter-model-openai` | Maven Central (org.springframework.ai) | Part of Spring AI 1.0 GA (Jun 2025) | High (Spring project) | github.com/spring-projects/spring-ai | N/A — official Spring project | Approved |

*slopcheck not run — both artifacts are from `org.springframework.ai` groupId under the official Spring project umbrella; registry presence already confirmed by BOM resolution in prior phases.*

---

## Architecture Patterns

### System Architecture Diagram

```
Browser
  │  POST /api/ai/key {provider, apiKey}  (password input — never localStorage)
  │  GET  /api/ai/status                  (returns {mode, provider} only)
  │  GET  /api/ai/explain/{holdingId}     
  │  GET  /api/ai/commentary
  ▼
Spring MVC (com.quantlens.ai.api)
  │  AiController — resolves Authentication → portfolioId; reads LlmKeySessionHolder
  │
  ▼
ChatClientStrategy.forSession(LlmKeySessionHolder)
  │  demo mode:  chatModel = anthropicChatModel (sentinel key — never called)
  │  live mode:  chatModel = AnthropicChatModel.builder()
  │                             .anthropicApi(AnthropicApi.builder()
  │                                 .apiKey(sessionKey).build())
  │                             .defaultOptions(...).build()
  │  → ChatClient.builder(chatModel).defaultAdvisors(demoModeAdvisor).build()
  │
  ▼
Advisor Chain
  [1] DemoModeAdvisor  ──── hasKey? ──NO──► lookup ai_seed_content(type, subjectId)
          │                                 → ChatClientResponse with AssistantMessage
         YES
          ▼
  [2..N] Future advisors (QuestionAnswerAdvisor — Phase 7; MemoryAdvisor — Phase 7)
          ▼
  ChatModel (Anthropic / OpenAI) — only reached in live mode
          ▼
  ChatClientResponse → controller → JSON DTO → Pinia store → Vue component
```

### Recommended Project Structure (new for Phase 6)

```
com.quantlens.ai/
├── package-info.java                   # @ApplicationModule(allowedDependencies = {"portfolio::domain", "analytics"})
├── api/
│   ├── AiController.java               # GET explain/{holdingId}, GET commentary
│   ├── AiKeyController.java            # POST /api/ai/key, DELETE /api/ai/key, GET /api/ai/status
│   ├── AiStatusDto.java                # {mode: "demo"|"live", provider?: "anthropic"|"openai"}
│   ├── AiKeyRequest.java               # {provider, apiKey}
│   ├── ExplainResponseDto.java         # {narrative: String}
│   └── CommentaryDto.java              # {headline, body, bulletPoints[]}
├── chat/
│   ├── DemoModeAdvisor.java            # CallAdvisor — the keystone seam
│   └── ChatClientStrategy.java         # forSession() factory: builds ChatClient + injects key
├── seed/
│   ├── AiSeedContent.java              # JPA entity: (id, type, subjectId, content)
│   ├── AiSeedContentRepository.java    # findByTypeAndSubjectId()
│   └── AiSeedRunner.java               # @Order(2) idempotent seeder — AI fixtures
└── service/
    ├── ExplainPositionService.java     # reads Position, builds prompt context, calls ChatClient
    └── CommentaryService.java          # reads Portfolio summary, calls ChatClient

com.quantlens.auth.session/            # EXISTING module location for LlmKeySessionHolder
└── LlmKeySessionHolder.java           # @Component @SessionScope — new file in existing module

frontend/src/
├── stores/ai.ts                        # Pinia ai store (mode, provider, actions: setKey, clearKey, fetchExplain, fetchCommentary)
├── api/ai.ts                           # typed axios calls → AiStatusDto, ExplainResponseDto, CommentaryDto
└── components/ai/
    ├── BYOKeyModal.vue                 # popup — password input, provider selector, submit → POST /api/ai/key
    ├── AiModeBadge.vue                 # top bar badge driven by /api/ai/status
    ├── ExplainDrawer.vue               # slide-out per holding row click
    └── CommentaryCard.vue              # dashboard card — AI-08
```

---

### Pattern 1: CallAdvisor Interface (Spring AI 1.1.x)

The `CallAdvisor` and `CallAdvisorChain` interfaces are the verified 1.1.x API. `AdvisedRequest`/`AdvisedResponse` are from older pre-1.0 code — do not use.

```java
// Source: https://docs.spring.io/spring-ai/reference/api/advisors.html (1.1.x)
public interface CallAdvisor extends Advisor {
    ChatClientResponse adviseCall(
        ChatClientRequest chatClientRequest,
        CallAdvisorChain callAdvisorChain);
}

public interface CallAdvisorChain {
    ChatClientResponse nextCall(ChatClientRequest chatClientRequest);
}

public interface Advisor extends Ordered {
    String getName();
    // getOrder() inherited from org.springframework.core.Ordered
}
```

[VERIFIED: https://docs.spring.io/spring-ai/reference/api/advisors.html]

### Pattern 2: DemoModeAdvisor — Short-Circuit Implementation

```java
// Source: Spring AI 1.1.x CallAdvisor API + ChatClientResponse constructor
// [ASSUMED] — ChatClientResponse constructor signature (String content)
//            is inferred from the API; verify at compile time that
//            new ChatClientResponse(new ChatResponse(List.of(new Generation(
//            new AssistantMessage(content)))), Map.of()) compiles correctly.
@Component
public class DemoModeAdvisor implements CallAdvisor {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE; // fires before all others

    private final LlmKeySessionHolder keyHolder;
    private final AiSeedContentRepository seedRepo;

    public DemoModeAdvisor(LlmKeySessionHolder keyHolder,
                           AiSeedContentRepository seedRepo) {
        this.keyHolder = keyHolder;
        this.seedRepo = seedRepo;
    }

    @Override
    public String getName() {
        return "DemoModeAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request,
                                         CallAdvisorChain chain) {
        if (keyHolder.hasKey()) {
            // Live mode — pass through to next advisor / model
            return chain.nextCall(request);
        }

        // Demo mode — short-circuit: return seeded content, no network call
        String type    = (String) request.context().get("AI_SEED_TYPE");
        String subject = (String) request.context().get("AI_SEED_SUBJECT");

        String content = seedRepo.findByTypeAndSubjectId(type, subject)
                .map(AiSeedContent::getContent)
                .orElse("AI narrative not available in demo mode.");

        return buildSyntheticResponse(content, request.context());
    }

    private ChatClientResponse buildSyntheticResponse(String content,
                                                       Map<String, Object> ctx) {
        // Construct a synthetic ChatResponse wrapping an AssistantMessage
        // ChatClientResponse(ChatResponse chatResponse, Map<String,Object> context)
        // [VERIFIED constructor signature: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/client/ChatClientResponse.html]
        ChatResponse chatResponse = new ChatResponse(
            List.of(new Generation(new AssistantMessage(content))));
        return new ChatClientResponse(chatResponse, ctx);
    }
}
```

**Key contract:** The DemoModeAdvisor reads two context keys set by the service layer before calling ChatClient:
- `AI_SEED_TYPE` — e.g., `"EXPLAIN_POSITION"` or `"DAILY_COMMENTARY"`
- `AI_SEED_SUBJECT` — e.g., `"AAPL"`, `"GROWTH_PERSONA"`, or `""` for persona-level content

These are set by the service via `.advisors(spec -> spec.param("AI_SEED_TYPE", ...).param("AI_SEED_SUBJECT", ...))` at call time.

### Pattern 3: Registering Advisors on ChatClient

```java
// Source: https://docs.spring.io/spring-ai/reference/api/chatclient.html
// DemoModeAdvisor is the ONLY advisor in Phase 6.
// QuestionAnswerAdvisor (Phase 7) and MessageChatMemoryAdvisor (Phase 7) slot in later.
ChatClient client = ChatClient.builder(chatModel)
    .defaultAdvisors(demoModeAdvisor)       // Phase 6 — first and only
    // Phase 7 adds: .defaultAdvisors(demoModeAdvisor, ragAdvisor, memoryAdvisor)
    .build();
```

Context parameters for demo mode are passed per-call:
```java
// Source: https://docs.spring.io/spring-ai/reference/api/chatclient.html
chatClient.prompt()
    .advisors(spec -> spec
        .param("AI_SEED_TYPE", "EXPLAIN_POSITION")
        .param("AI_SEED_SUBJECT", ticker))
    .user(u -> u.text(explainPromptTemplate).param("ticker", ticker)
                .param("metrics", metricsJson))
    .call()
    .content();
```

[VERIFIED: https://docs.spring.io/spring-ai/reference/api/chatclient.html]

### Pattern 4: Booting Starters with No Real Key (Key-Less Startup)

**Finding:** Spring AI 1.1.x Anthropic and OpenAI starters require `spring.ai.anthropic.api-key` / `spring.ai.openai.api-key` to be non-blank at startup. The auto-configuration beans are conditional on the property being set. Setting them to a non-blank placeholder avoids the startup failure; the `DemoModeAdvisor` short-circuits before any network call fires in demo mode.

[ASSUMED: The exact startup behavior (exception type, whether it's a context-load failure or a runtime failure) is not explicitly documented. The safe strategy below avoids the question entirely.]

```yaml
# application.yml — "DEMO_NO_KEY" is a recognizable sentinel; never reaches a provider
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:DEMO_NO_KEY}
      chat:
        options:
          model: claude-sonnet-4-6          # override Spring AI's default of claude-sonnet-4-5
          max-tokens: 2048
    openai:
      api-key: ${OPENAI_API_KEY:DEMO_NO_KEY}
      chat:
        options:
          model: gpt-4o
          max-tokens: 2048
    # Disable the single auto-built ChatClient bean; we construct per-request in ChatClientStrategy
    chat:
      client:
        enabled: false
```

**Why the sentinel works safely:**
1. App starts with `DEMO_NO_KEY` as the configured key.
2. User hits any AI endpoint → `DemoModeAdvisor` checks `LlmKeySessionHolder.hasKey()` → false → returns seeded response. No ChatModel.call() is ever reached.
3. No HTTP request is made to Anthropic/OpenAI, so `DEMO_NO_KEY` is never sent to any provider.
4. When a user submits a real key via the popup, `ChatClientStrategy.forSession()` builds a NEW model with the real key — the `DEMO_NO_KEY` configuration-level key is completely bypassed.

**Alert for the planner:** Add a Wave 0 integration test that asserts the Spring context loads with both `spring.ai.anthropic.api-key=DEMO_NO_KEY` and `spring.ai.openai.api-key=DEMO_NO_KEY` — this is the startup smoke gate.

### Pattern 5: Per-Request Session Key Injection (Live Mode)

**Verified pattern for 1.1.x:** Both providers use an `ApiKey` interface + `Builder` pattern. The `AnthropicApi.Builder` and `OpenAiApi.Builder` both expose `.apiKey(String)`. The model `Builder` accepts the API instance. `OpenAiChatModel` also has a `mutate()` shorthand.

```java
// Source: Javadoc https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/anthropic/api/AnthropicApi.Builder.html
//         https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/anthropic/AnthropicChatModel.Builder.html
//         https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatModel.Builder.html
//         https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/api/OpenAiApi.Builder.html

@Service
public class ChatClientStrategy {

    // Auto-configured prototype models — hold the baseline config (sentinel key, default options)
    // These are the ONLY ChatModel beans in the context; ChatClient beans are disabled.
    private final AnthropicChatModel baseAnthropicModel;
    private final OpenAiChatModel    baseOpenAiModel;
    private final DemoModeAdvisor    demoAdvisor;

    // Injected from auto-configured beans (spring-ai-starter-model-anthropic injects AnthropicApi)
    private final AnthropicApi baseAnthropicApi;
    private final OpenAiApi    baseOpenAiApi;

    // ... constructor omitted for brevity

    /**
     * Builds a per-request ChatClient with the session key injected into the model.
     * In demo mode (no key) the DemoModeAdvisor short-circuits before the model is called,
     * so the baseModel with the sentinel key is used — it is never actually invoked.
     */
    public ChatClient forSession(LlmKeySessionHolder keyHolder) {
        ChatModel model = buildModel(keyHolder);
        return ChatClient.builder(model)
            .defaultAdvisors(demoAdvisor)   // Phase 7 adds ragAdvisor, memoryAdvisor here
            .build();
    }

    private ChatModel buildModel(LlmKeySessionHolder keyHolder) {
        if (!keyHolder.hasKey()) {
            return baseAnthropicModel;  // demo: model is never called — advisor short-circuits
        }
        return switch (keyHolder.getProvider()) {
            case "anthropic" -> buildAnthropicModel(keyHolder.getApiKey());
            case "openai"    -> buildOpenAiModel(keyHolder.getApiKey());
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + keyHolder.getProvider());
        };
    }

    private AnthropicChatModel buildAnthropicModel(String apiKey) {
        // Build a new AnthropicApi with the session key — NEVER store this
        // [VERIFIED: AnthropicApi.Builder.apiKey(String) exists in 1.1.x Javadoc]
        AnthropicApi sessionApi = AnthropicApi.builder()
            .apiKey(apiKey)
            .build();

        // Re-use the base model's options and other config via the Builder
        // [VERIFIED: AnthropicChatModel.Builder.anthropicApi(AnthropicApi) exists in 1.1.x Javadoc]
        return AnthropicChatModel.builder()
            .anthropicApi(sessionApi)
            .defaultOptions(AnthropicChatOptions.builder()
                .model("claude-sonnet-4-6")
                .maxTokens(2048)
                .build())
            .build();
    }

    private OpenAiChatModel buildOpenAiModel(String apiKey) {
        // OpenAiChatModel.mutate() returns a Builder pre-populated with base config
        // [VERIFIED: OpenAiChatModel.mutate() exists in 1.1.x Javadoc]
        // [VERIFIED: OpenAiApi.Builder.apiKey(String) exists in 1.1.x Javadoc]
        OpenAiApi sessionApi = OpenAiApi.builder()
            .apiKey(apiKey)
            .build();

        return baseOpenAiModel.mutate()
            .openAiApi(sessionApi)
            .build();
    }
}
```

**Compile-time flags for the planner:**
- `AnthropicChatModel.Builder` does NOT have a `mutate()` method — use `AnthropicChatModel.builder()` directly. [VERIFIED: 1.1.x Javadoc shows no mutate() on AnthropicChatModel]
- `OpenAiChatModel.mutate()` DOES exist. [VERIFIED: 1.1.x Javadoc]
- `AnthropicApi.Builder.apiKey(String)` DOES exist. [VERIFIED: 1.1.x Javadoc]
- `OpenAiApi.Builder.apiKey(String)` DOES exist. [VERIFIED: 1.1.x Javadoc]
- `AnthropicChatModel.Builder.anthropicApi(AnthropicApi)` DOES exist. [VERIFIED: 1.1.x Javadoc]
- `OpenAiChatModel.Builder.openAiApi(OpenAiApi)` DOES exist. [VERIFIED: 1.1.x Javadoc]

### Pattern 6: LlmKeySessionHolder — @SessionScope Bean

The `@SessionScope` pattern already exists in this codebase (the security module uses it conceptually). Mirror the same structure:

```java
// Source: spring.io/guides/spring-security (SessionScope pattern)
// Location: com.quantlens.auth.session (existing auth module, new file)
@Component
@SessionScope
public class LlmKeySessionHolder {

    private String provider;    // "anthropic" | "openai" — null in demo
    private String apiKey;      // NEVER serialized, logged, echoed

    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // Getter/setter — no @ToString, no @JsonInclude, no logging annotations
    public String getProvider() { return provider; }
    public String getApiKey()   { return apiKey; }

    public void setKey(String provider, String apiKey) {
        this.provider = provider;
        this.apiKey   = apiKey;
    }

    public void clear() {
        this.provider = null;
        this.apiKey   = null;
    }
}
```

**Session scope pitfall:** `@SessionScope` beans must be injected via `@Autowired` or constructor injection where the injection site is a Spring-managed bean. Do NOT inject `LlmKeySessionHolder` into a singleton that stores it as a field — Spring provides a scoped proxy, but the advisor is a singleton. The advisor should accept `LlmKeySessionHolder` by constructor (Spring creates a proxy). Alternatively, inject `HttpSession` and retrieve the holder directly. Both are safe.

### Pattern 7: AiKeyController — Key Submission and Status

```java
// Source: AuthController.java pattern (same module)
@RestController
@RequestMapping("/api/ai")
public class AiKeyController {

    private final LlmKeySessionHolder keyHolder;

    // POST /api/ai/key — sets provider + key in session
    @PostMapping("/key")
    public ResponseEntity<AiStatusDto> setKey(@RequestBody @Valid AiKeyRequest request,
                                               HttpSession session) {
        // Validate provider name
        if (!"anthropic".equals(request.provider()) && !"openai".equals(request.provider())) {
            return ResponseEntity.badRequest().build();
        }
        // Validate key format (basic — never log the key itself)
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        keyHolder.setKey(request.provider(), request.apiKey());
        // Return mode/provider — NEVER return the key
        return ResponseEntity.ok(new AiStatusDto("live", request.provider()));
    }

    // DELETE /api/ai/key — clears session key, returns to demo
    @DeleteMapping("/key")
    public ResponseEntity<AiStatusDto> clearKey() {
        keyHolder.clear();
        return ResponseEntity.ok(new AiStatusDto("demo", null));
    }

    // GET /api/ai/status — mode badge source; key never included
    @GetMapping("/status")
    public AiStatusDto getStatus() {
        if (keyHolder.hasKey()) {
            return new AiStatusDto("live", keyHolder.getProvider());
        }
        return new AiStatusDto("demo", null);
    }
}
```

Register `/api/ai/key` (POST/DELETE) and `/api/ai/status` (GET) in `SecurityConfig.filterChain()` as authenticated (`.anyRequest().authenticated()` already covers them). Also exempt `/api/ai/key` from CSRF matching the existing pattern for session-mutating endpoints — OR ensure the frontend sends `X-XSRF-TOKEN` (already done by the axios interceptor in `auth.ts`).

### Pattern 8: Seeded Content — DB Table + Seeder

```sql
-- Flyway migration (V7__ai_seed_content.sql or part of V6 for this phase)
CREATE TABLE ai_seed_content (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(64)  NOT NULL,   -- 'EXPLAIN_POSITION', 'DAILY_COMMENTARY'
    subject_id VARCHAR(32)  NOT NULL,   -- ticker ('AAPL') or persona ('GROWTH')
    content    TEXT         NOT NULL,
    CONSTRAINT uq_ai_seed UNIQUE (type, subject_id)
);
```

```java
// AiSeedRunner.java — @Order(2), after SeedRunner @Order(1)
// Idempotency: check seed_log for "ai-v1" key before inserting
// Seeds are authored text keyed to the seeded portfolio data from Phase 1.
// Growth persona (Alice): AAPL, MSFT, NVDA, AMZN, TSLA
// Income persona (Bob): JPM, BAC, XOM, CVX, PG, KO, WMT
// Balanced persona (Charlie): AAPL, JPM, XOM, JNJ, PG, MSFT, KO
```

**Authored content strategy (AI-01 / Pitfall 12 — seeded vs live drift):**
- Run the actual ChatClient against the real seeded portfolio numbers once with a real key to generate the initial fixtures. Store those real LLM outputs as the DB seeds. This guarantees internal consistency with the displayed charts (Pitfall 12 prevention).
- Content should be 2–4 paragraphs, use the specific tickers and approximate numbers from the seeded data, and include mild hedging language ("appears well-positioned," "historically demonstrated").
- The commentary should vary per persona (growth commentary ≠ income commentary).
- Seed at minimum: one `EXPLAIN_POSITION` per holding (15 tickers × 3 personas = use the per-ticker rows, not per-persona — the narrative can reference the ticker universally), plus one `DAILY_COMMENTARY` per persona (3 rows).

### Pattern 9: ExplainPositionService and CommentaryService

```java
// ExplainPositionService.java
@Service
@Transactional(readOnly = true)
public class ExplainPositionService {

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PortfolioService    portfolioService;   // reads position metrics

    public String explain(Long holdingId, Authentication auth) {
        // Build context for the prompt AND for the DemoModeAdvisor seed lookup
        HoldingDto holding = portfolioService.getHolding(auth, holdingId);

        // Metrics JSON is included in the live prompt — the demo advisor ignores the prompt
        // and returns the seeded fixture directly.
        String metricsJson = buildMetricsJson(holding);

        return strategy.forSession(keyHolder)
            .prompt()
            .system(EXPLAIN_SYSTEM_PROMPT)
            .user(u -> u.text(EXPLAIN_USER_TEMPLATE)
                        .param("ticker",  holding.ticker())
                        .param("metrics", metricsJson))
            .advisors(spec -> spec
                .param("AI_SEED_TYPE",    "EXPLAIN_POSITION")
                .param("AI_SEED_SUBJECT", holding.ticker()))
            .call()
            .content();
    }
}
```

The prompt template in live mode must produce a response structurally similar to the seeded content (same tone, similar length, same JSON shape if structured output is used). This prevents visible mode-switch discrepancy (Pitfall 12).

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| LLM API HTTP client | Custom RestTemplate/WebClient wrapper | `AnthropicChatModel` / `OpenAiChatModel` via starter | Retry, error handling, streaming, request/response mapping all handled |
| Advisor chain ordering | Custom middleware chain | Spring AI `CallAdvisor` + `getOrder()` | Chain execution is managed by Spring AI's `ChatClientAdvisorChain` |
| Session-scoped state | ThreadLocal or static map | `@SessionScope` Spring bean | Spring manages lifecycle tied to `HttpSession`; scoped proxy handles singleton injection |
| Key format validation | Regex in controller | Simple blank-check + provider whitelist; real validation only at first real LLM call | Over-engineering; wrong key is caught immediately on first live API call |
| Demo content selection | Fuzzy/semantic matching | Exact DB lookup by (type, subjectId) | No embedding model needed at demo time; deterministic; fast |

---

## Model IDs — Verified

[VERIFIED against Anthropic official model table: https://platform.claude.com/docs/en/docs/about-claude/models/overview]

| Model | API ID | Spring AI config value | Status |
|-------|--------|----------------------|--------|
| Claude Sonnet 4.6 (default live) | `claude-sonnet-4-6` | `spring.ai.anthropic.chat.options.model=claude-sonnet-4-6` | Current (latest) |
| Claude Opus 4.8 | `claude-opus-4-8` | `spring.ai.anthropic.chat.options.model=claude-opus-4-8` | Current (most capable) |
| Claude Haiku 4.5 | `claude-haiku-4-5-20251001` | alias: `claude-haiku-4-5` | Current (fastest) |
| Claude Sonnet 4.5 | `claude-sonnet-4-5-20250929` | alias: `claude-sonnet-4-5` | Legacy (still available) |

**Important:** Spring AI 1.1.x docs still reference `claude-sonnet-4-5` as the default model ID. The project must explicitly set `spring.ai.anthropic.chat.options.model=claude-sonnet-4-6` in `application.yml` to use the current Sonnet generation.

[VERIFIED against OpenAI: `gpt-4o` is current and documented in Spring AI 1.1.x OpenAI docs]

---

## Common Pitfalls

### Pitfall 1: AdvisedRequest / AdvisedResponse Compile Errors

**What goes wrong:** Pre-1.0 tutorial code uses `AdvisedRequest` and `AdvisedResponse` as the advisor method parameters. These types were renamed to `ChatClientRequest` / `ChatClientResponse` in the 1.0 → 1.1 transition. Code using the old names will not compile against 1.1.6.

**How to avoid:** Use `ChatClientRequest` and `ChatClientResponse` exclusively. The `context()` method on `ChatClientRequest` replaces the old `.adviseContext()`.

**Warning signs:** `cannot find symbol: class AdvisedRequest` at compile time.

[VERIFIED: PITFALLS.md Pitfall 1 + Spring AI upgrade notes]

### Pitfall 2: Startup Failure with Blank API Key

**What goes wrong:** Adding `spring-ai-starter-model-anthropic` without setting `spring.ai.anthropic.api-key` to a non-blank value causes Spring context load failure. The auto-configuration expects a non-blank key.

**How to avoid:** Always set `spring.ai.anthropic.api-key=${ANTHROPIC_API_KEY:DEMO_NO_KEY}` and `spring.ai.openai.api-key=${OPENAI_API_KEY:DEMO_NO_KEY}`. The sentinel `DEMO_NO_KEY` satisfies the binding requirement and is never sent to any provider (DemoModeAdvisor short-circuits first).

**Also** set `spring.ai.chat.client.enabled=false` to prevent the auto-configured single `ChatClient` bean from being ambiguous between providers.

**Warning signs:** `No spring.ai.anthropic.api-key property set` or similar `BeanCreationException` at startup.

[ASSUMED: exact error message and whether it is a binding failure vs. lazy bean failure — verify at compile/run time]

### Pitfall 3: Singleton Promotion of @SessionScope Bean

**What goes wrong:** `@SessionScope` beans injected into singletons are only safe via Spring's scoped proxy. If a singleton bean stores the `LlmKeySessionHolder` reference in a plain field (not via the proxy), all sessions share one key — catastrophic security failure.

**How to avoid:** Always inject `LlmKeySessionHolder` via constructor injection in `@Component` or `@Service` classes — Spring automatically wraps it in a CGLIB scoped proxy. Never store it in a static field. Verify the proxy is active with: `assertThat(keyHolder.getClass().getSimpleName()).contains("CGLIB")` in a unit test.

**Warning signs:** One user's key leaks into another user's session. Alice's commentary returns Bob's seeded content.

[VERIFIED: Spring Docs @SessionScope + existing SecurityConfig pattern in this codebase]

### Pitfall 4: SimpleLoggerAdvisor at DEBUG Level

**What goes wrong:** `SimpleLoggerAdvisor` logs the full `ChatClientRequest` and `ChatClientResponse` at DEBUG level, which includes the prompt text. If the prompt contains embedded data from `LlmKeySessionHolder` (it should not — the key should never be in a prompt), or if an error response from the provider echoes back request parameters, the key could appear in logs.

**How to avoid:**
1. Never register `SimpleLoggerAdvisor` in any profile — especially not in `test` or `dev`.
2. Set `logging.level.org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor=WARN` in `application.yml`.
3. Set `logging.level.org.springframework.ai=WARN` globally; only enable DEBUG for specific troubleshooting sessions.
4. Add a Logback `TurboFilter` to scrub any line matching known key formats before it reaches appenders.

```xml
<!-- logback-spring.xml -->
<turboFilter class="ch.qos.logback.classic.turbo.MarkerFilter">
    <!-- Additionally add a custom TurboFilter to redact key patterns -->
</turboFilter>
```

**Or simpler**: add a `ch.qos.logback.core.filter.EvaluatorFilter` in the appender config that denies messages containing the substring `sk-` or patterns matching long alphanumeric strings.

[VERIFIED: PITFALLS.md Pitfall 8 + SimpleLoggerAdvisor source]

### Pitfall 5: Echoing Key in API Response

**What goes wrong:** A naive implementation of `GET /api/ai/status` or `POST /api/ai/key` response might include the key for "confirmation." Or an error handler catches a `RuntimeException` from the key-injection code and includes the exception message — which may contain the key in a stack trace.

**How to avoid:**
- `POST /api/ai/key` returns `{mode, provider}` only — never the key.
- `GET /api/ai/status` returns `{mode, provider}` only.
- Register a `@ExceptionHandler` for `IllegalArgumentException` in the `AiKeyController` that returns a generic `{error: "Invalid configuration"}` message without any detail.
- Verify in the key-leakage test (see Validation section).

### Pitfall 6: Multi-Provider Ambiguous ChatClient Bean

**What goes wrong:** Adding both `spring-ai-starter-model-anthropic` AND `spring-ai-starter-model-openai` without disabling the auto-configured `ChatClient` bean causes a `NoUniqueBeanDefinitionException` — two `ChatModel` beans, one expected `ChatClient`.

**How to avoid:** Set `spring.ai.chat.client.enabled=false` in `application.yml`. `ChatClientStrategy` builds `ChatClient` instances per-request via `ChatClient.builder(model)`.

[VERIFIED: STACK.md multi-provider configuration section + Spring AI docs multi-provider pattern]

### Pitfall 7: Spring AI Model Default Still claude-sonnet-4-5

**What goes wrong:** Spring AI 1.1.x ships with `claude-sonnet-4-5` as the default Anthropic model. `claude-sonnet-4-5` is now a legacy model (deprecated path in Anthropic docs). The project should use `claude-sonnet-4-6`.

**How to avoid:** Explicitly set `spring.ai.anthropic.chat.options.model=claude-sonnet-4-6` in `application.yml`. This also ensures the seeded content was generated by the same model family that will be used in live mode, maintaining narrative consistency (Pitfall 12).

[VERIFIED: Anthropic model table; Spring AI Anthropic docs showing claude-sonnet-4-5 as default]

---

## Key Injection — Complete Verified API Reference

This section is the single authoritative reference for the planner. All entries are verified.

| Class | Method | Signature | Source |
|-------|--------|-----------|--------|
| `AnthropicApi.Builder` | `apiKey` | `Builder apiKey(String simpleApiKey)` | [VERIFIED: 1.1.x Javadoc] |
| `AnthropicApi.Builder` | `apiKey` | `Builder apiKey(ApiKey apiKey)` | [VERIFIED: 1.1.x Javadoc] |
| `AnthropicApi` | `builder` | `static Builder builder()` | [VERIFIED: 1.1.x Javadoc] |
| `AnthropicChatModel.Builder` | `anthropicApi` | `Builder anthropicApi(AnthropicApi api)` | [VERIFIED: 1.1.x Javadoc] |
| `AnthropicChatModel` | `builder` | `static Builder builder()` | [VERIFIED: 1.1.x Javadoc] |
| `AnthropicChatModel` | `mutate` | **DOES NOT EXIST in 1.1.x** | [VERIFIED: Javadoc — no mutate() on AnthropicChatModel] |
| `OpenAiApi.Builder` | `apiKey` | `Builder apiKey(String simpleApiKey)` | [VERIFIED: 1.1.x Javadoc] |
| `OpenAiApi` | `builder` | `static Builder builder()` | [VERIFIED: 1.1.x Javadoc] |
| `OpenAiChatModel` | `mutate` | `Builder mutate()` | [VERIFIED: 1.1.x Javadoc] |
| `OpenAiChatModel.Builder` | `openAiApi` | `Builder openAiApi(OpenAiApi api)` | [VERIFIED: 1.1.x Javadoc] |
| `NoopApiKey` | — | `class NoopApiKey implements ApiKey` in `org.springframework.ai.model` | [VERIFIED: 1.1.7 Javadoc — present in 1.1.x] |

`NoopApiKey` is present in 1.1.x (it was removed in 2.0.0-M7, not in 1.1.x). This project does NOT use `NoopApiKey` — the sentinel string approach is safer and clearer.

---

## Runtime State Inventory

This is NOT a rename/refactor/migration phase. No runtime state inventory required.

---

## Environment Availability Audit

No new external services. Spring AI providers are called only in live mode (not during startup or tests). All tests use mocks/stubs (no network).

| Dependency | Required By | Available | Notes |
|------------|-------------|-----------|-------|
| Java 21 | Spring Boot 3.5 | Yes | JAVA_HOME = Eclipse Temurin 21.0.11 |
| PostgreSQL (Testcontainers) | Integration tests | Yes | pgvector/pgvector:pg16 via AbstractPostgresIntegrationTest |
| Anthropic API | Live mode only | Conditional | Demo mode requires zero network |
| OpenAI API | Live mode only | Conditional | Demo mode requires zero network |
| Node 22 + npm | Frontend build | Yes | CLAUDE.md confirmed |

---

## Key-Leak Prevention — Concrete Controls

Four specific vectors and mitigations:

| Vector | Risk | Mitigation |
|--------|------|------------|
| `SimpleLoggerAdvisor` DEBUG logs | Key in prompt context logged | Never register this advisor; set `org.springframework.ai=WARN` in all profiles |
| Provider error responses containing request echoes | OpenAI/Anthropic errors may echo back headers in error message | Wrap all ChatModel calls in a try-catch; return generic error DTO; do NOT include exception.getMessage() in response |
| `@SessionScope` singleton promotion | Session scoped proxy not invoked; singleton stores one key | Constructor-inject `LlmKeySessionHolder` only; add proxy detection assertion in test |
| Echo in API response body | `POST /api/ai/key` or error handler returns key | Explicit response DTO only returns `{mode, provider}`; `@ExceptionHandler` returns generic message |

### Key-Leakage Integration Test (AI-02 Security Gate)

```java
// KeyLeakageIntegrationTest.java — extends AbstractPostgresIntegrationTest
// This test MUST pass before any live-mode code ships.
@Test
void apiKeyNeverAppearsInResponseOrLogs() throws Exception {
    String testKey = "TEST-SENTINEL-KEY-" + UUID.randomUUID();

    // 1. Submit the key via POST /api/ai/key
    MockHttpSession session = loginAs("alice");
    mockMvc.perform(post("/api/ai/key")
            .session(session)
            .contentType(APPLICATION_JSON)
            .content("{\"provider\":\"anthropic\",\"apiKey\":\"" + testKey + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.mode").value("live"))
        .andExpect(jsonPath("$.apiKey").doesNotExist())   // key MUST NOT be in response
        .andExpect(content().string(not(containsString(testKey)))); // belt-and-suspenders

    // 2. Call GET /api/ai/status — must not return key
    mockMvc.perform(get("/api/ai/status").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(testKey))));

    // 3. Call GET /api/ai/explain/1 — demo mode short-circuits; still must not echo key
    mockMvc.perform(get("/api/ai/explain/1").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(testKey))));

    // 4. Call GET /api/ai/commentary — same check
    mockMvc.perform(get("/api/ai/commentary").session(session))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString(testKey))));

    // 5. Assert logs — requires ListAppender or LogCaptor
    // All captured log lines from the test must not contain testKey
    assertThat(capturedLogLines).noneMatch(line -> line.contains(testKey));
}
```

**Note on log capture:** Use `ch.qos.logback:logback-classic` (already on test classpath via spring-boot-starter-test) with a `ListAppender<ILoggingEvent>` attached to the root logger for the duration of the test.

---

## Seeded Content Quality Guide

The authored fixtures must satisfy the consistency requirement from Pitfall 12 and AI-01.

**Generation strategy:** Before Phase 6 coding is complete, run the real ChatClient against the seeded data (from SeedRunner.java — exact holdings are known) with a real key to generate the initial fixtures. Use the outputs verbatim as seeds. This guarantees:
- Every ticker mentioned in the narrative exists in the portfolio.
- Every percentage/metric references the approximate seeded values.
- The tone matches what live mode will produce.

**Holdings reference for seeded content authors:**

| Persona | Tickers |
|---------|---------|
| Alice (Growth) | AAPL (50 shares), MSFT (14 shares after partial sell), NVDA (21 shares after partial sell), AMZN (28 shares after partial sell), TSLA (10 shares after partial sell) |
| Bob (Income) | JPM (21 shares after partial sell), BAC (56 shares after partial sell), XOM (28 shares after partial sell), CVX (17 shares after partial sell), PG (24 shares after partial sell), KO (42 shares after partial sell), WMT (35 shares after partial sell) |
| Charlie (Balanced) | AAPL (14 shares after partial sell), JPM (14 shares after partial sell), XOM (17 shares after partial sell), JNJ (14 shares after partial sell), PG (21 shares after partial sell), MSFT (10 shares after partial sell), KO (28 shares after partial sell) |

*Share counts are approximate (initial qty - 30% partial sell at bar 200), derived from SeedRunner.java.*

**Content seed rows needed (minimum viable):**
- 15 `EXPLAIN_POSITION` rows (one per ticker in the universe — Alice/Bob/Charlie share per-ticker explanations)
- 3 `DAILY_COMMENTARY` rows (one per persona: `GROWTH`, `INCOME`, `BALANCED`)

---

## Frontend Patterns

### ai.ts Store (Pinia)

```typescript
// frontend/src/stores/ai.ts — follows asyncState factory from portfolio.ts
import { defineStore } from 'pinia'
import { reactive } from 'vue'
import axios from 'axios'

interface AiStatus { mode: 'demo' | 'live'; provider?: 'anthropic' | 'openai' }
interface ExplainResponse { narrative: string }
interface CommentaryDto { headline: string; body: string; bulletPoints: string[] }

export const useAiStore = defineStore('ai', () => {
  const status     = reactive({ data: null as AiStatus | null, loading: false, error: null as string | null })
  const explanation = reactive({ data: null as ExplainResponse | null, loading: false, error: null as string | null })
  const commentary  = reactive({ data: null as CommentaryDto | null, loading: false, error: null as string | null })

  async function fetchStatus(): Promise<void> {
    status.loading = true; status.error = null
    try {
      const { data } = await axios.get<AiStatus>('/api/ai/status')
      status.data = data
    } catch { status.error = 'Failed to load AI status' }
    finally { status.loading = false }
  }

  // setKey: POST /api/ai/key — key travels one-way; never stored in store state
  async function setKey(provider: 'anthropic' | 'openai', apiKey: string): Promise<void> {
    const { data } = await axios.post<AiStatus>('/api/ai/key', { provider, apiKey })
    status.data = data   // receives {mode: 'live', provider} — key NOT in response
    // apiKey param goes out of scope here — never stored in Pinia state
  }

  async function clearKey(): Promise<void> {
    await axios.delete('/api/ai/key')
    status.data = { mode: 'demo' }
    explanation.data = null
    commentary.data = null
  }

  async function fetchExplanation(holdingId: number): Promise<void> {
    explanation.loading = true; explanation.error = null
    try {
      const { data } = await axios.get<ExplainResponse>(`/api/ai/explain/${holdingId}`)
      explanation.data = data
    } catch { explanation.error = 'Failed to load explanation' }
    finally { explanation.loading = false }
  }

  async function fetchCommentary(): Promise<void> {
    commentary.loading = true; commentary.error = null
    try {
      const { data } = await axios.get<CommentaryDto>('/api/ai/commentary')
      commentary.data = data
    } catch { commentary.error = 'Failed to load commentary' }
    finally { commentary.loading = false }
  }

  function $reset(): void {
    status.data = null; status.loading = false; status.error = null
    explanation.data = null; explanation.loading = false; explanation.error = null
    commentary.data = null; commentary.loading = false; commentary.error = null
  }

  return { status, explanation, commentary, fetchStatus, setKey, clearKey, fetchExplanation, fetchCommentary, $reset }
})
```

**Key security invariant in the store:** `setKey()` accepts `apiKey` as a parameter and immediately POSTs it to the server. The key is never assigned to any reactive state, never stored in `localStorage`, never in a cookie. After `setKey()` returns, the key string goes out of scope.

### BYOKeyModal.vue — UX Pattern

- Provider selector: two radio buttons (Anthropic Claude / OpenAI GPT)
- Key input: `<input type="password">` — browser autofill disabled (`autocomplete="new-password"`)
- Submit: calls `aiStore.setKey(provider, keyInput)` — clears `keyInput` after submission
- Cancel / Return to Demo: calls `aiStore.clearKey()`
- After submission: modal closes, mode badge updates
- Error state: display generic "Key rejected — check provider and key format" on 400/401

```
Screenshot-worthy UI:
┌─────────────────────────────────────────────────────────┐
│  Connect Your Own AI Key                            ✕   │
│                                                         │
│  Provider:  ● Anthropic Claude  ○ OpenAI GPT            │
│                                                         │
│  API Key:   [••••••••••••••••••••••••••••]              │
│             Your key is used only for this session and  │
│             is never saved or transmitted to us.        │
│                                                         │
│  [ Cancel ]                          [ Connect Live AI] │
└─────────────────────────────────────────────────────────┘
```

The modal content — especially the "never saved or transmitted" line and the live AI branding — is the README screenshot artifact.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test 3.5.13 + Testcontainers (existing) |
| Config file | `src/test/resources/application-test.yml` |
| Quick run command | `.\mvnw.cmd test -pl backend -Dtest="DemoModeAdvisorTest,LlmKeySessionHolderTest,AiKeyControllerTest"` |
| Full suite command | `.\mvnw.cmd test -pl backend` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| AI-01 | Demo mode: explain + commentary return seeded content with zero provider call | Integration | `.*AiDemoModeIntegrationTest` | No — Wave 0 |
| AI-01 | DemoModeAdvisor short-circuits when no key | Unit | `DemoModeAdvisorTest#shortCircuitsInDemoMode` | No — Wave 0 |
| AI-02 | Key never in any response body or log line | Integration | `KeyLeakageIntegrationTest` | No — Wave 0 |
| AI-02 | @SessionScope: different sessions isolated | Integration | `AiKeyControllerTest#sessionIsolation` | No — Wave 0 |
| AI-02 | DemoModeAdvisor passes through when key present | Unit | `DemoModeAdvisorTest#passesThroughWhenKeyPresent` | No — Wave 0 |
| AI-07 | GET /api/ai/explain/{id} returns ExplainResponseDto with narrative | Integration | `AiControllerIntegrationTest#explainReturnsSeededContent` | No — Wave 0 |
| AI-08 | GET /api/ai/commentary returns CommentaryDto | Integration | `AiControllerIntegrationTest#commentaryReturnsSeededContent` | No — Wave 0 |
| AI-02 (FE) | BYOKeyModal: submit does not persist key to localStorage | Component | `BYOKeyModal.spec.ts#keyNotInLocalStorage` | No — Wave 0 |
| AI-01 (FE) | AiModeBadge: shows "Demo" without key, "Live" with key | Component | `AiModeBadge.spec.ts` | No — Wave 0 |
| AI-07 (FE) | ExplainDrawer: opens on holding click, shows narrative | Component | `ExplainDrawer.spec.ts` | No — Wave 0 |
| AI-08 (FE) | CommentaryCard: renders headline + body | Component | `CommentaryCard.spec.ts` | No — Wave 0 |

### Key Test: DemoModeAdvisorTest (Unit)

```java
// No Spring context needed — pure unit test
class DemoModeAdvisorTest {

    private AiSeedContentRepository seedRepo;
    private LlmKeySessionHolder     keyHolder;
    private DemoModeAdvisor         advisor;
    private CallAdvisorChain        chain;

    @BeforeEach void setup() {
        seedRepo  = mock(AiSeedContentRepository.class);
        keyHolder = mock(LlmKeySessionHolder.class);
        chain     = mock(CallAdvisorChain.class);
        advisor   = new DemoModeAdvisor(keyHolder, seedRepo);
    }

    @Test void shortCircuitsInDemoMode() {
        when(keyHolder.hasKey()).thenReturn(false);
        when(seedRepo.findByTypeAndSubjectId("EXPLAIN_POSITION", "AAPL"))
            .thenReturn(Optional.of(new AiSeedContent("EXPLAIN_POSITION", "AAPL", "AAPL narrative")));

        ChatClientRequest request = buildRequest("EXPLAIN_POSITION", "AAPL");
        ChatClientResponse response = advisor.adviseCall(request, chain);

        verifyNoInteractions(chain);   // MUST NOT call chain.nextCall()
        assertThat(extractContent(response)).isEqualTo("AAPL narrative");
    }

    @Test void passesThroughWhenKeyPresent() {
        when(keyHolder.hasKey()).thenReturn(true);
        ChatClientRequest  request  = buildRequest("EXPLAIN_POSITION", "AAPL");
        ChatClientResponse expected = mock(ChatClientResponse.class);
        when(chain.nextCall(request)).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isSameAs(expected);
        verify(chain).nextCall(request);
        verifyNoInteractions(seedRepo);
    }
}
```

### Sampling Rate

- **Per task commit:** `.\mvnw.cmd test -pl backend -Dtest="DemoModeAdvisorTest,AiKeyControllerTest"`
- **Per wave merge:** `.\mvnw.cmd test -pl backend`
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `tests/ai/stores/ai.spec.ts` — ai store unit tests
- [ ] `tests/ai/components/BYOKeyModal.spec.ts`
- [ ] `tests/ai/components/AiModeBadge.spec.ts`
- [ ] `tests/ai/components/ExplainDrawer.spec.ts`
- [ ] `tests/ai/components/CommentaryCard.spec.ts`
- [ ] `backend/.../ai/DemoModeAdvisorTest.java`
- [ ] `backend/.../ai/KeyLeakageIntegrationTest.java`
- [ ] `backend/.../ai/AiDemoModeIntegrationTest.java`
- [ ] `backend/.../ai/AiKeyControllerTest.java`
- [ ] `backend/.../ai/AiControllerIntegrationTest.java`
- [ ] Flyway migration: `V7__ai_seed_content.sql`
- [ ] `src/test/resources/application-test.yml` — add `spring.ai.anthropic.api-key=TEST_SENTINEL` and `spring.ai.openai.api-key=TEST_SENTINEL`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Session auth already in SecurityConfig; /api/ai/* requires authenticated session |
| V3 Session Management | yes (primary) | @SessionScope for key; SessionCreationPolicy.ALWAYS; invalidateHttpSession on logout already in SecurityConfig |
| V4 Access Control | yes | Each AI endpoint scoped to authenticated principal; no cross-session access |
| V5 Input Validation | yes | Provider whitelist validation; key blank-check; AiKeyRequest @Valid |
| V6 Cryptography | no | Keys are not encrypted at rest (session-only; no at-rest storage) |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| LLM key leakage in logs | Information Disclosure | `SimpleLoggerAdvisor` never registered; `org.springframework.ai` log level WARN |
| LLM key echo in response | Information Disclosure | Response DTOs never include key field; `@JsonIgnore` on apiKey field of any internal DTO |
| Session fixation → key theft | Elevation of Privilege | `sessionFixation().changeSessionId()` already in SecurityConfig |
| Cross-session key contamination | Information Disclosure | @SessionScope scoped proxy enforced; test asserts isolation |
| Provider key in error response | Information Disclosure | @ExceptionHandler returns generic message; exception.getMessage() never forwarded |
| Key in browser storage | Information Disclosure | Frontend: key param scoped to setKey() function; never assigned to reactive state |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Setting `spring.ai.anthropic.api-key=DEMO_NO_KEY` (non-blank placeholder) allows the Spring context to load without the starter throwing at startup | Pitfall 2, Pattern 4 | If the starter validates key format at startup (e.g., rejects keys not starting with `sk-ant-`), the app won't boot in demo mode. Fallback: add `@ConditionalOnProperty("quantlens.ai.live-mode.enabled")` around the auto-configuration beans. Verify at first compile + startup. |
| A2 | `ChatClientResponse` can be constructed with `new ChatClientResponse(chatResponse, context)` with a standard `ChatResponse(List.of(new Generation(new AssistantMessage(content))))` inner object | Pattern 2 (DemoModeAdvisor) | If the ChatClientResponse constructor signature differs from the Javadoc summary, the short-circuit build fails. Verify import and constructor signature at compile time. |
| A3 | The `AnthropicChatModel.Builder` constructor completes successfully with only `.anthropicApi()` and `.defaultOptions()` set (without toolCallingManager, retryTemplate, observationRegistry) | Pattern 5 (buildAnthropicModel) | If required fields are missing and no defaults are applied by the builder, build() may throw NullPointerException. Fallback: inject and pass the base model's existing beans to the builder. |
| A4 | `spring.ai.chat.client.enabled=false` is the correct property to disable the auto-configured `ChatClient` bean in 1.1.6 | Pitfall 6, Pattern 4 | If this property was renamed (older docs show it as `spring.ai.chat.client.enabled`), the auto-config may still create an ambiguous bean. Check Spring AI 1.1.6 auto-configuration source at compile time. |

---

## Open Questions

1. **AnthropicChatModel.Builder minimum required fields**
   - What we know: Builder accepts `anthropicApi`, `defaultOptions`, `retryTemplate`, `toolCallingManager`, `observationRegistry`.
   - What's unclear: Which fields have defaults that allow `build()` to succeed with only `anthropicApi` + `defaultOptions`.
   - Recommendation: On first compile, test `AnthropicChatModel.builder().anthropicApi(api).defaultOptions(opts).build()` in isolation. If it fails, inject the base model's `retryTemplate` and `observationRegistry` from the auto-configured `AnthropicChatModel` bean via the `mutate()` pattern approach of copying beans.

2. **`spring.ai.chat.client.enabled` vs `spring.ai.model.chat`**
   - What we know: Spring AI 1.1.0 docs say `spring.ai.anthropic.chat.enabled` was removed; `spring.ai.model.chat` selects the provider.
   - What's unclear: Whether `spring.ai.chat.client.enabled=false` remains valid in 1.1.6 or was also removed/renamed.
   - Recommendation: Check the auto-configuration source for `ChatClientAutoConfiguration` in 1.1.6; the property name must match exactly.

3. **`spring.ai.model.chat` with multiple providers**
   - What we know: The property selects which provider's `ChatClient` to auto-configure.
   - What's unclear: Whether setting `spring.ai.model.chat=none` while keeping both model starters on the classpath allows both `AnthropicChatModel` and `OpenAiChatModel` beans to be created without an ambiguous `ChatClient`.
   - Recommendation: Set `spring.ai.model.chat=none` AND `spring.ai.chat.client.enabled=false` (belt-and-suspenders) until confirmed.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `AdvisedRequest` / `AdvisedResponse` | `ChatClientRequest` / `ChatClientResponse` | Spring AI 1.0 → 1.1 | All custom advisor code from pre-1.0 tutorials will not compile |
| `PromptChatMemoryAdvisor.conversationId(id)` on builder | `.advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, id))` at call time | Spring AI 1.1.6 | Memory advisor code from 1.1.0 tutorials throws at runtime |
| `spring.ai.anthropic.chat.enabled=true/false` | `spring.ai.model.chat=anthropic|none` | Spring AI 1.1.x | Old property is silently ignored |
| `claude-sonnet-4-5` (Spring AI default) | `claude-sonnet-4-6` (current latest Sonnet) | Anthropic released 4.6 | Must explicitly override Spring AI's configured default |
| `claude-3-5-sonnet-latest` style aliases | `claude-sonnet-4-6` pinned ID (dateless format) | Anthropic 4.6+ | New ID format; dateless but still a pinned snapshot per Anthropic docs |

---

## Sources

### Primary (HIGH confidence)
- Spring AI 1.1.x Advisors API: https://docs.spring.io/spring-ai/reference/api/advisors.html — CallAdvisor interface, method signatures, ChatClientRequest/ChatClientResponse types
- Spring AI ChatClient API: https://docs.spring.io/spring-ai/reference/api/chatclient.html — builder, defaultAdvisors, per-call param, multi-provider
- Anthropic Chat 1.1.x: https://docs.spring.io/spring-ai/reference/api/chat/anthropic-chat.html — property names, model IDs
- OpenAI Chat 1.1.x: https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html — property names, model IDs
- AnthropicApi.Builder Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/anthropic/api/AnthropicApi.Builder.html — apiKey(String), baseUrl(), build()
- OpenAiApi.Builder Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/api/OpenAiApi.Builder.html — apiKey(String), build()
- AnthropicChatModel.Builder Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/anthropic/AnthropicChatModel.Builder.html — anthropicApi(), build()
- OpenAiChatModel.Builder Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatModel.Builder.html — openAiApi(), build()
- OpenAiChatModel Javadoc (mutate): https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/openai/OpenAiChatModel.html — mutate() confirmed
- ChatClientRequest Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/client/ChatClientRequest.html — context() map
- ChatClientResponse Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/chat/client/ChatClientResponse.html — constructor + builder
- ApiKey / NoopApiKey Javadoc: https://docs.spring.io/spring-ai/docs/current/api/org/springframework/ai/model/ApiKey.html — interface; NoopApiKey present in 1.1.x
- Anthropic model IDs: https://platform.claude.com/docs/en/docs/about-claude/models/overview — claude-sonnet-4-6, claude-opus-4-8, claude-haiku-4-5-20251001 confirmed current
- Spring AI upgrade notes (1.1.6 memory advisor changes): https://docs.spring.io/spring-ai/reference/upgrade-notes.html
- Existing codebase: SecurityConfig.java, SeedRunner.java, portfolio.ts, auth.ts — @SessionScope patterns, asyncState factory, axios XSRF interceptor

### Secondary (MEDIUM confidence)
- GitHub issue #3361 (dynamic API keys): https://github.com/spring-projects/spring-ai/issues/3361 — confirmed placeholder key workaround is the de-facto approach
- PITFALLS.md (this project): Key leakage vectors, Pitfall 8 — corroborates mitigation approach
- ARCHITECTURE.md (this project): DemoModeAdvisor design, ChatClientStrategy pattern, multi-provider wiring

### Tertiary (LOW confidence — see Assumptions Log)
- Spring AI 1.1.6 auto-configuration behavior with placeholder keys: not explicitly documented; inferred from conditional annotation inspection and issue tracker

---

## Metadata

**Confidence breakdown:**
- CallAdvisor API (ChatClientRequest/ChatClientResponse, method signatures): HIGH — verified against official Javadoc
- Key injection pattern (AnthropicApi.Builder, OpenAiChatModel.mutate): HIGH — verified against official Javadoc
- Key-less startup sentinel workaround: MEDIUM — inferred from issue tracker + conditional annotation behavior; flagged as A1
- ChatClientResponse construction in short-circuit path: MEDIUM — constructor signature verified; exact compile-time behavior flagged as A2
- AnthropicChatModel.Builder minimum required fields: MEDIUM — flagged as A3 open question
- Model IDs (claude-sonnet-4-6, etc.): HIGH — verified against official Anthropic model table
- Frontend patterns (Pinia asyncState, axios XSRF): HIGH — verified against existing codebase

**Research date:** 2026-06-09
**Valid until:** 2026-07-09 (Spring AI 1.1.x is stable; Anthropic model IDs may change if new models release)
