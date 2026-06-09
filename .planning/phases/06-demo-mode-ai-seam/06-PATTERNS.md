# Phase 6: Demo-Mode AI Seam - Pattern Map

**Mapped:** 2026-06-09
**Files analyzed:** 28 new/modified files
**Analogs found:** 22 / 28 (6 files have no codebase analog — point to RESEARCH.md code blocks)

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `backend/.../ai/package-info.java` | config | — | `analytics/package-info.java` | exact |
| `backend/.../ai/chat/DemoModeAdvisor.java` | middleware | request-response | no analog — Spring AI new | none (RESEARCH Pattern 2) |
| `backend/.../ai/chat/ChatClientStrategy.java` | service | request-response | no analog — Spring AI new | none (RESEARCH Pattern 5) |
| `backend/.../ai/api/AiKeyController.java` | controller | request-response | `security/api/AuthController.java` | role-match |
| `backend/.../ai/api/AiController.java` | controller | request-response | `analytics/api/AnalyticsController.java` | exact |
| `backend/.../ai/api/AiStatusDto.java` | model (DTO) | — | `analytics/api/VarResultDto.java` | exact (record style) |
| `backend/.../ai/api/AiKeyRequest.java` | model (DTO) | — | `analytics/api/VarResultDto.java` | exact (record style) |
| `backend/.../ai/api/ExplainResponseDto.java` | model (DTO) | — | `analytics/api/VarResultDto.java` | exact (record style) |
| `backend/.../ai/api/CommentaryDto.java` | model (DTO) | — | `analytics/api/VarResultDto.java` | exact (record style) |
| `backend/.../ai/service/ExplainPositionService.java` | service | request-response | no analog — Spring AI new | none (RESEARCH Pattern 9) |
| `backend/.../ai/service/CommentaryService.java` | service | request-response | no analog — Spring AI new | none (RESEARCH Pattern 9) |
| `backend/.../ai/seed/AiSeedContent.java` | model (entity) | — | `portfolio/domain/Position.java` | exact |
| `backend/.../ai/seed/AiSeedContentRepository.java` | model (repo) | CRUD | `seed/SeedLogRepository.java` | exact |
| `backend/.../ai/seed/AiSeedRunner.java` | utility | batch | `seed/SeedRunner.java` | exact |
| `backend/src/main/resources/db/migration/V4__ai_seed_content.sql` | migration | — | `V1__schema.sql` | exact |
| `backend/.../ai/session/LlmKeySessionHolder.java` | service | request-response | no analog | none (RESEARCH Pattern 6) |
| `backend/src/main/resources/application.yml` (additions) | config | — | existing `application.yml` | exact |
| `backend/.../ai/DemoModeAdvisorTest.java` | test (unit) | — | `analytics/RiskCalculatorTest.java` | role-match |
| `backend/.../ai/KeyLeakageIntegrationTest.java` | test (integration) | — | `analytics/AnalyticsControllerIntegrationTest.java` | exact |
| `backend/.../ai/AiControllerIntegrationTest.java` | test (integration) | — | `analytics/AnalyticsControllerIntegrationTest.java` | exact |
| `backend/.../ai/AiKeyControllerTest.java` | test (integration) | — | `security/AuthIntegrationTest.java` | exact |
| `frontend/src/stores/ai.ts` | store | request-response | `frontend/src/stores/portfolio.ts` | exact |
| `frontend/src/api/ai.ts` | utility | request-response | `frontend/src/api/analytics.ts` | exact |
| `frontend/src/components/ai/BYOKeyModal.vue` | component | request-response | `frontend/src/components/AllocationChart.vue` (card+states pattern) | role-match |
| `frontend/src/components/ai/AiModeBadge.vue` | component | request-response | `frontend/src/components/TopBar.vue` (badge placement, CSS token style) | role-match |
| `frontend/src/components/ai/ExplainDrawer.vue` | component | request-response | `frontend/src/components/RiskScorecard.vue` (loading/error/populated states) | role-match |
| `frontend/src/components/ai/CommentaryCard.vue` | component | request-response | `frontend/src/components/KpiCard.vue` + `RiskScorecard.vue` | role-match |
| `frontend/src/views/DashboardView.vue` (modified) | component | — | existing `DashboardView.vue` | exact (slot replacement) |

---

## Pattern Assignments

---

### `backend/.../ai/package-info.java` (config)

**Analog:** `backend/src/main/java/com/quantlens/analytics/package-info.java`

**Module declaration pattern** (full file):
```java
// analytics/package-info.java
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"marketdata::domain", "portfolio::domain"})
package com.quantlens.analytics;
```

**Apply to new file:** Same shape. The `ai` module reads `portfolio::domain` for holdings data and `analytics` is not in scope. Declare:
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "AI",
        allowedDependencies = {"portfolio::domain"})
package com.quantlens.ai;
```

---

### `backend/.../ai/chat/DemoModeAdvisor.java` (middleware, request-response)

**Analog:** NONE in codebase. Use RESEARCH.md Pattern 2 exclusively.

**Source pattern:** RESEARCH.md lines 228–287 — `DemoModeAdvisor` full implementation.

Key contracts to preserve from RESEARCH:
- Implements `CallAdvisor` (NOT `AdvisedRequest`/`AdvisedResponse` — those are pre-1.0)
- `getName()` returns `"DemoModeAdvisor"`
- `getOrder()` returns `Ordered.HIGHEST_PRECEDENCE`
- `adviseCall(ChatClientRequest, CallAdvisorChain)` — checks `keyHolder.hasKey()`
  - false → lookup `seedRepo.findByTypeAndSubjectId(type, subject)` and build synthetic response
  - true → delegate `chain.nextCall(request)`
- Context keys read from `request.context()`: `"AI_SEED_TYPE"` and `"AI_SEED_SUBJECT"`
- `ChatClientResponse` constructed as: `new ChatClientResponse(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))), ctx)` [ASSUMED A2 — verify at compile]

**Inject `LlmKeySessionHolder` by constructor** (Spring creates a scoped proxy — RESEARCH Pitfall 3).

---

### `backend/.../ai/chat/ChatClientStrategy.java` (service, request-response)

**Analog:** NONE in codebase. Use RESEARCH.md Pattern 5 exclusively.

**Source pattern:** RESEARCH.md lines 368–438 — full `ChatClientStrategy` implementation.

Critical API notes (RESEARCH Key Injection table, lines 715–729):
- `AnthropicChatModel` has NO `mutate()` — use `AnthropicChatModel.builder()` directly
- `OpenAiChatModel` HAS `mutate()` — use `baseOpenAiModel.mutate().openAiApi(sessionApi).build()`
- `AnthropicApi.builder().apiKey(sessionKey).build()` constructs a session-scoped API
- `AnthropicChatModel.builder().anthropicApi(sessionApi).defaultOptions(...).build()` — verify minimum fields compile (RESEARCH Open Question 1 / Assumption A3)

---

### `backend/.../ai/api/AiKeyController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/quantlens/security/api/AuthController.java`

**Imports pattern** (AuthController.java lines 1–14):
```java
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
```
For `AiKeyController` replace with `PostMapping`, `DeleteMapping`, `RequestBody`, `Valid`, `HttpSession` imports.

**Controller declaration pattern** (AuthController.java lines 35–54):
```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Value("${quantlens.demo.password:demo1234}")
    private String demoPasswordHint;

    private final AppUserRepository appUserRepository;
    // ...
    public AuthController(AppUserRepository appUserRepository, ...) {
        this.appUserRepository = appUserRepository;
        // ...
    }
```
Apply: `@RestController @RequestMapping("/api/ai")`, constructor-inject `LlmKeySessionHolder`.

**Session-write + response DTO pattern** (AuthController.java lines 84–100):
```java
@GetMapping("/me")
@Transactional(readOnly = true)
public ResponseEntity<MeDto> me(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(401).build();
    }
    String username = authentication.getName();
    return appUserRepository.findByUsername(username)
            .map(user -> { ... return ResponseEntity.ok(new MeDto(...)); })
            .orElse(ResponseEntity.status(401).build());
}
```
The key endpoint mirrors this `ResponseEntity<DtoRecord>` return shape. Response NEVER includes the API key — only `{mode, provider}` (RESEARCH Pattern 7 + Pitfall 5).

**Source pattern for POST/DELETE/GET shape:** RESEARCH.md lines 488–528 — complete `AiKeyController` implementation.

**SecurityConfig update required:** Add `"/api/ai/key"` to the `ignoringRequestMatchers` CSRF block alongside the existing login/logout exemptions (SecurityConfig.java line 118):
```java
.ignoringRequestMatchers("/api/auth/login", "/api/auth/logout")
// Add:
// .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout", "/api/ai/key")
```
All `/api/ai/*` routes are covered by `.anyRequest().authenticated()` at line 91 — no new permit rules needed.

---

### `backend/.../ai/api/AiController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java`

**Imports pattern** (AnalyticsController.java lines 1–16):
```java
import com.quantlens.analytics.service.CointegrationScanner;
// ...
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
```
Add `@PathVariable` import for `explain/{holdingId}`.

**Controller declaration + constructor injection pattern** (AnalyticsController.java lines 44–64):
```java
@RestController
@RequestMapping("/api/portfolio")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final RiskCalculator riskCalculator;
    // ...
    public AnalyticsController(PortfolioRepository portfolioRepository,
                               RiskCalculator riskCalculator, ...) {
        this.portfolioRepository = portfolioRepository;
        // ...
    }
```

**Principal resolution pattern — copy verbatim** (AnalyticsController.java lines 141–148):
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
This is the IDOR-safe principal resolution pattern. `AiController` uses it for both `explain` and `commentary` endpoints — never a `@RequestParam portfolioId`.

**readOnly endpoint pattern** (AnalyticsController.java lines 76–81):
```java
@GetMapping("/risk")
@Transactional(readOnly = true)
public ResponseEntity<RiskScorecardDto> getRisk(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return ResponseEntity.ok(riskCalculator.computeRiskScorecard(portfolioId));
}
```
Apply: `@GetMapping("/explain/{holdingId}")` and `@GetMapping("/commentary")`, both `@Transactional(readOnly = true)`, both delegate to service layer.

---

### `backend/.../ai/api/AiStatusDto.java`, `AiKeyRequest.java`, `ExplainResponseDto.java`, `CommentaryDto.java` (model, DTO records)

**Analog:** `backend/src/main/java/com/quantlens/analytics/api/VarResultDto.java` and `RiskScorecardDto.java`

**DTO record pattern** (VarResultDto.java full file):
```java
package com.quantlens.analytics.api;

import java.math.BigDecimal;

/**
 * Value-at-Risk result for a single computation method.
 * [Javadoc with @param per field]
 */
public record VarResultDto(
        String method,
        double confidence,
        int horizonDays,
        BigDecimal amount,
        double percentage
) {}
```

Rules from this pattern:
- `public record ClassName(...){}` — no class body, no getters, no setters
- Javadoc with `@param` for each field
- `BigDecimal` for monetary values; `double` for statistical ratios; `String` for labels
- `AiKeyRequest` fields: `String provider`, `String apiKey` — no `@JsonIgnore` needed on a record used only as request body (Jackson deserializes from JSON; never serialized back)
- `AiStatusDto` fields: `String mode`, `String provider` — `provider` can be `null` in demo mode; use `@JsonInclude(NON_NULL)` or let Jackson serialize null as absent

---

### `backend/.../ai/seed/AiSeedContent.java` (model, entity)

**Analog:** `backend/src/main/java/com/quantlens/portfolio/domain/Position.java`

**Entity pattern** (Position.java lines 1–71):
```java
package com.quantlens.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
// ...
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    // protected no-arg constructor
    protected Position() {}

    // public all-args constructor
    public Position(Portfolio portfolio, Security security,
                    BigDecimal quantity, BigDecimal avgCostBasis) { ... }

    // getters only (no @ToString, no Lombok, no @EqualsAndHashCode)
    public Long getId() { return id; }
    // ...
}
```

Apply to `AiSeedContent`:
```java
@Entity
@Table(name = "ai_seed_content")
public class AiSeedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String type;          // 'EXPLAIN_POSITION' | 'DAILY_COMMENTARY'

    @Column(name = "subject_id", nullable = false, length = 32)
    private String subjectId;     // ticker or persona key

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    protected AiSeedContent() {}
    public AiSeedContent(String type, String subjectId, String content) { ... }
    // getters only
}
```

Also apply from `AppUser.java` lines 1–14 (same import/annotation style, `jakarta.persistence.*`).

---

### `backend/.../ai/seed/AiSeedContentRepository.java` (model, CRUD)

**Analog:** `backend/src/main/java/com/quantlens/seed/SeedLogRepository.java`

**Repository interface pattern** (SeedLogRepository.java full file):
```java
package com.quantlens.seed;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SeedLogRepository extends JpaRepository<SeedLog, String> {
}
```

Apply: `JpaRepository<AiSeedContent, Long>` plus one derived query:
```java
Optional<AiSeedContent> findByTypeAndSubjectId(String type, String subjectId);
```
Spring Data derives this from the method name — no `@Query` needed.

---

### `backend/.../ai/seed/AiSeedRunner.java` (utility, batch)

**Analog:** `backend/src/main/java/com/quantlens/seed/SeedRunner.java`

**@Order + ApplicationRunner declaration** (SeedRunner.java lines 59–60):
```java
@Component
@Order(1)
public class SeedRunner implements ApplicationRunner {
```
`AiSeedRunner` uses `@Order(2)` — runs after `SeedRunner @Order(1)`.

**Idempotency guard pattern** (SeedRunner.java lines 112–121):
```java
@Override
@Transactional
public void run(ApplicationArguments args) {
    boolean alreadyDone = seedLogRepository.findById(SEED_VERSION)
            .map(SeedLog::isCompleted)
            .orElse(false);
    if (alreadyDone) {
        log.info("SeedRunner: seed_log v1 already completed — skipping");
        return;
    }
    // ... seed work ...
    // Mark complete LAST — rolled back if anything above fails
    SeedLog seedLog = new SeedLog(SEED_VERSION);
    seedLog.setCompleted(true);
    seedLog.setCompletedAt(LocalDateTime.now());
    seedLogRepository.save(seedLog);
}
```
`AiSeedRunner` checks `seedLogRepository.findById("ai-v1")` for its own key. The idempotency row is written **last** in the transaction.

**Logger pattern** (SeedRunner.java lines 62–63):
```java
private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);
// Usage:
log.info("SeedRunner: saved {} securities with {} total OHLCV bars", securities.size(), totalBars);
```
Match exactly: static final `LoggerFactory.getLogger`. Never `System.out.println`.

**Persona holdings reference** (SeedRunner.java lines 246–274 — persona tick lists):
```java
// Growth persona: AAPL, MSFT, NVDA, AMZN, TSLA
// Income persona: JPM, BAC, XOM, CVX, PG, KO, WMT
// Balanced persona: AAPL, JPM, XOM, JNJ, PG, MSFT, KO
```
`AiSeedRunner` must produce content that references these specific tickers and approximate share counts.

---

### `backend/src/main/resources/db/migration/V4__ai_seed_content.sql` (migration)

**Analog:** `backend/src/main/resources/db/migration/V1__schema.sql`

**Flyway migration pattern** (V1__schema.sql lines 1–16):
```sql
-- V1__schema.sql
-- Flyway-owned DDL for all relational tables + vector_store.

CREATE TABLE IF NOT EXISTS securities (
    id           BIGSERIAL PRIMARY KEY,
    ticker       VARCHAR(10)  NOT NULL UNIQUE,
    -- ...
);
CREATE INDEX IF NOT EXISTS idx_ohlcv_security_date
    ON ohlcv_bars (security_id, bar_date DESC);
```

Rules from this analog:
- Leading comment with filename and purpose
- `CREATE TABLE IF NOT EXISTS` (idempotent-safe, though Flyway checksums prevent re-runs)
- `BIGSERIAL PRIMARY KEY` for surrogate PK
- `UNIQUE` constraint naming matches RESEARCH.md: `CONSTRAINT uq_ai_seed UNIQUE (type, subject_id)`
- Version must be `V4__` — V1/V2/V3 already exist; next is V4

Apply (RESEARCH.md lines 534–544):
```sql
-- V4__ai_seed_content.sql
CREATE TABLE IF NOT EXISTS ai_seed_content (
    id         BIGSERIAL PRIMARY KEY,
    type       VARCHAR(64)  NOT NULL,
    subject_id VARCHAR(32)  NOT NULL,
    content    TEXT         NOT NULL,
    CONSTRAINT uq_ai_seed UNIQUE (type, subject_id)
);
```

---

### `backend/.../ai/session/LlmKeySessionHolder.java` (service, request-response)

**Analog:** NONE in codebase (no existing `@SessionScope` bean). Use RESEARCH.md Pattern 6.

**Source pattern:** RESEARCH.md lines 452–483 — full `LlmKeySessionHolder` implementation.

Critical safety rules (RESEARCH Pitfall 3):
- `@Component @SessionScope` — Spring creates a scoped CGLIB proxy automatically
- NO `@ToString` — prevents accidental key logging (Pitfall 4)
- NO `@JsonInclude` / `@JsonSerialize` — this bean is never serialized to JSON
- Accessor `getApiKey()` exists for `ChatClientStrategy` but must NOT be exposed via any DTO
- Field `apiKey` is a plain `private String` — never `static`, never `volatile`
- Location: `com.quantlens.ai.session` (sub-package of the new `ai` module (com.quantlens.ai.session))

---

### `backend/src/main/resources/application.yml` (additions)

**Analog:** Existing `application.yml` structure (not read in full — additions are additive).

**Source pattern:** RESEARCH.md Pattern 4 (lines 329–349):
```yaml
spring:
  ai:
    anthropic:
      api-key: ${ANTHROPIC_API_KEY:DEMO_NO_KEY}
      chat:
        options:
          model: claude-sonnet-4-6
          max-tokens: 2048
    openai:
      api-key: ${OPENAI_API_KEY:DEMO_NO_KEY}
      chat:
        options:
          model: gpt-4o
          max-tokens: 2048
    chat:
      client:
        enabled: false      # prevents ambiguous ChatClient bean (Pitfall 6); verify property name at compile (Open Question 2)
logging:
  level:
    org.springframework.ai: WARN   # prevents SimpleLoggerAdvisor key leakage (Pitfall 4)
```

---

### `backend/.../ai/KeyLeakageIntegrationTest.java` (test, integration)

**Analog:** `backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java`

**Base class + declarations** (AnalyticsControllerIntegrationTest.java lines 1–37):
```java
package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
```

**Login helper pattern — copy verbatim** (AnalyticsControllerIntegrationTest.java lines 214–232):
```java
private String loginAndGetSessionCookie(String username) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", "demo1234");

    ResponseEntity<String> response = restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

    assertThat(response.getStatusCode())
            .as("Login for %s should succeed", username)
            .isEqualTo(HttpStatus.OK);
    return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
}

private ResponseEntity<String> authenticatedGet(String path, String sessionCookie) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, sessionCookie);
    return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
}
```

**Key leakage test body:** RESEARCH.md lines 769–802 — full `apiKeyNeverAppearsInResponseOrLogs` test. Copy the `testKey = "TEST-SENTINEL-KEY-" + UUID.randomUUID()` pattern + `jsonPath("$.apiKey").doesNotExist()` + `content().string(not(containsString(testKey)))` assertions.

**`application-test.yml` additions required:**
```yaml
spring:
  ai:
    anthropic:
      api-key: TEST_SENTINEL
    openai:
      api-key: TEST_SENTINEL
```

---

### `backend/.../ai/AiControllerIntegrationTest.java` (test, integration)

**Analog:** `backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java`

Same base class, same `loginAndGetSessionCookie` / `authenticatedGet` helpers (copy verbatim from lines 214–238).

Test structure mirrors RESEARCH Validation section lines 945–959:
- `explainReturnsSeededContent` — GET `/api/ai/explain/1` returns 200 with non-blank `narrative` field
- `commentaryReturnsSeededContent` — GET `/api/ai/commentary` returns 200 with `headline`, `body`, `bulletPoints` fields
- `explain_unauthenticated_returns401` — pattern from `getRisk_unauthenticated_returns401()` lines 54–64

---

### `backend/.../ai/AiKeyControllerTest.java` (test, integration)

**Analog:** `backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java`

**Session-mutating POST test pattern** (AuthIntegrationTest.java lines 38–54):
```java
@Test
void loginSuccess() {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", "alice");
    body.add("password", "demo1234");

    ResponseEntity<String> response = restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("\"authenticated\":true");
}
```
For `AiKeyControllerTest`, POST body is JSON (`MediaType.APPLICATION_JSON`, not form-encoded). Tests: `setKey_returns_mode_live`, `clearKey_returns_mode_demo`, `sessionIsolation` (two separate sessions, each with different keys, verify `GET /api/ai/status` returns different providers).

---

### `backend/.../ai/DemoModeAdvisorTest.java` (test, unit)

**Analog:** No direct analog for unit tests in codebase; closest is `analytics/RiskCalculatorTest.java` (pure unit test, no Spring context).

**Unit test structure from RESEARCH.md lines 963–1003:**
```java
class DemoModeAdvisorTest {
    private AiSeedContentRepository seedRepo;
    private LlmKeySessionHolder keyHolder;
    private DemoModeAdvisor advisor;
    private CallAdvisorChain chain;

    @BeforeEach void setup() {
        seedRepo  = mock(AiSeedContentRepository.class);
        keyHolder = mock(LlmKeySessionHolder.class);
        chain     = mock(CallAdvisorChain.class);
        advisor   = new DemoModeAdvisor(keyHolder, seedRepo);
    }

    @Test void shortCircuitsInDemoMode() { ... verifyNoInteractions(chain) ... }
    @Test void passesThroughWhenKeyPresent() { ... verify(chain).nextCall(request) ... }
}
```
No `@SpringBootTest` — pure Mockito mocks. Import `org.mockito.Mockito.*` (already on classpath via spring-boot-starter-test).

---

## Frontend Pattern Assignments

---

### `frontend/src/stores/ai.ts` (store, request-response)

**Analog:** `frontend/src/stores/portfolio.ts`

**asyncState factory pattern** (portfolio.ts lines 22–32):
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
Copy this definition verbatim into `ai.ts` — do NOT import it from portfolio.ts (no cross-store imports).

**defineStore setup function pattern** (portfolio.ts lines 38–50):
```typescript
export const usePortfolioStore = defineStore('portfolio', () => {
  const holdings    = asyncState<HoldingDto[]>(null)
  // ...
  async function fetchHoldings(version?: number): Promise<void> {
    holdings.loading = true
    holdings.error = null
    try {
      const { data } = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
      holdings.data = data
    } catch (e: any) {
      holdings.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load holdings'
    } finally {
      holdings.loading = false
    }
  }
```

Apply the same shape to `ai.ts` state (`status`, `explanation`, `commentary`) and actions (`fetchStatus`, `setKey`, `clearKey`, `fetchExplanation`, `fetchCommentary`).

**Security invariant for setKey** (RESEARCH.md lines 862–866): `apiKey` param is sent directly to the server; NEVER assigned to any reactive state, never stored in `localStorage`. The `$reset()` function must NOT include an `apiKey` field in its state reset because the key is never in store state.

**$reset pattern** (portfolio.ts lines 309–320):
```typescript
function $reset(): void {
  holdings.data    = null; holdings.loading    = false; holdings.error    = null
  // ... (one line per resource)
}
```

---

### `frontend/src/api/ai.ts` (utility, request-response)

**Analog:** `frontend/src/api/analytics.ts`

**Module header comment pattern** (analytics.ts lines 1–8):
```typescript
// IMPORTANT: Do NOT set axios.defaults or register interceptors here.
// api/auth.ts already sets withCredentials: true and registers the CSRF request
// interceptor + 401 response interceptor globally on the shared axios singleton.
// This module declares DTO interfaces only — no fetch functions.
// Actual fetch calls live in the Pinia portfolio store (same pattern as portfolio.ts).
```
`ai.ts` follows the same rule: declare DTO interfaces only; all `axios.*` calls live in `stores/ai.ts`.

**DTO interface pattern** (analytics.ts lines 10–51):
```typescript
export interface VarResultDto {
  method: string        // "HISTORICAL" | "PARAMETRIC" | "CVaR_HISTORICAL"
  confidence: number
  // ...
}

export interface RiskScorecardDto {
  sharpeRatio: number
  // ...
}
```

Apply: export `AiStatus`, `ExplainResponse`, `CommentaryDto` interfaces that mirror the Java records exactly. Use `string` for string fields, `number` for numeric fields, `string[]` for `bulletPoints`.

---

### `frontend/src/components/ai/BYOKeyModal.vue` (component, request-response)

**Analog:** `frontend/src/components/AllocationChart.vue` (card panel + loading/error/populated states) and `frontend/src/components/KpiCard.vue` (CSS token style)

**CSS token usage pattern** (AllocationChart.vue lines 203–320 — all scoped styles):
```css
.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
}
```
No hardcoded hex — use only `var(--color-*)`, `var(--space-*)`, `var(--radius-*)`.

**Three-state template pattern** (AllocationChart.vue lines 174–199):
```html
<!-- Loading skeleton -->
<div v-if="loading" class="skeleton" style="height: 280px" aria-hidden="true" />

<!-- Error state — T-03-08: static copy only -->
<div v-else-if="error" class="chart-error" role="alert">
  <span>Failed to load allocation. Check your connection and try again.</span>
  <button class="retry-btn" @click="emit('retry')">Retry</button>
</div>

<!-- Populated state -->
<v-chart v-else ... />
```
Modal equivalent: `v-if="isOpen"` wraps the outer dialog; inner three states: submitting (spinner), error (generic message — never key-related detail), populated (form).

**Password input safety (RESEARCH.md lines 906–930):** `<input type="password" autocomplete="new-password">`. Local ref `keyInput` is cleared after `aiStore.setKey(provider, keyInput.value)` in the submit handler.

**Focus-visible accessibility pattern** (TopBar.vue lines 165–168):
```css
.persona-pill:focus-visible {
  outline: 2px solid var(--color-accent);
  outline-offset: 3px;
}
```
Apply to all interactive elements in the modal (inputs, buttons, radio buttons).

**defineProps + defineEmits pattern** (AllocationChart.vue lines 11–15):
```typescript
const props = defineProps<{
  allocation: AllocationSliceDto[] | null
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()
```
Apply: `defineProps<{ open: boolean }>()` and `defineEmits<{ close: []; submitted: [] }>()`.

---

### `frontend/src/components/ai/AiModeBadge.vue` (component, request-response)

**Analog:** `frontend/src/components/TopBar.vue`

**TopBar structure and CSS tokens** (TopBar.vue lines 52–86):
```html
<header class="top-bar" role="banner">
  <div class="brand"> ... </div>
  <nav aria-label="Persona switcher" class="persona-nav"> ... </nav>
  <div class="user-area">
    <span class="username-display">@{{ authStore.username }}</span>
    <button class="logout-btn" @click="handleLogout">Log out</button>
  </div>
</header>
```
`AiModeBadge` renders as a small `<span>` element placed inside the `<div class="user-area">` slot in `TopBar.vue`. It reads from `useAiStore().status.data.mode`.

**Pill/badge CSS pattern** (TopBar.vue lines 135–168 — `.persona-pill`):
```css
.persona-pill {
  padding: 6px 16px;
  border-radius: var(--radius-pill);
  font-size: 13px;
  font-weight: 600;
  border: 1px solid var(--color-border);
  background: transparent;
  color: var(--color-text-secondary);
}
.persona-pill.active {
  border-color: var(--color-accent);
  background: var(--color-accent-subtle);
  color: var(--color-text-primary);
}
```
Badge variant: read-only `<span>` (not a `<button>`), smaller padding, `var(--color-up)` for live mode, `var(--color-text-muted)` for demo mode.

**`<script setup>` with store pattern** (TopBar.vue lines 1–10):
```typescript
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'
// ...
const authStore = useAuthStore()
```
Apply: `import { useAiStore } from '../stores/ai'`; `const aiStore = useAiStore()`.

---

### `frontend/src/components/ai/ExplainDrawer.vue` (component, request-response)

**Analog:** `frontend/src/components/RiskScorecard.vue`

**Props-driven, loading/error/populated three-state pattern** (RiskScorecard.vue lines 1–15):
```typescript
const props = defineProps<{
  risk: RiskScorecardDto | null
  loading: boolean
  error: string | null
}>()
const emit = defineEmits<{ retry: [] }>()
```

**Three-state template** (RiskScorecard.vue lines 18–83):
```html
<template v-if="loading">
  <div class="skeleton kpi-skeleton" aria-hidden="true" />
</template>
<template v-else-if="error">
  <div class="chart-error" role="alert">
    <span>Failed to load risk metrics. Check your connection and try again.</span>
    <button class="retry-btn" @click="emit('retry')">Retry</button>
  </div>
</template>
<template v-else>
  <!-- populated content -->
</template>
```

**Shimmer skeleton animation** (RiskScorecard.vue lines 98–118):
```css
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  border-radius: var(--radius-md);
}
```
Copy this animation verbatim — it is the project-standard skeleton pattern.

Drawer-specific addition: a slide-in panel triggered by `open: boolean` prop. The overlay and `position: fixed` panel use `var(--color-bg-elevated)`, `z-index: 200` (above top-bar's `z-index: 100`).

---

### `frontend/src/components/ai/CommentaryCard.vue` (component, request-response)

**Analog:** `frontend/src/components/KpiCard.vue` (card container + CSS tokens) + `RiskScorecard.vue` (loading/error states)

**Card container pattern** (KpiCard.vue lines 54–74):
```html
<div v-else class="kpi-card" :class="borderClass">
  <span class="kpi-label">{{ label }}</span>
  <span class="kpi-primary">{{ primary }}</span>
  <span v-if="secondary" class="kpi-secondary">{{ secondary }}</span>
</div>
```
```css
.kpi-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  min-height: 88px;
}
```

**CommentaryCard is a full-width card.** It renders `headline` as `.kpi-primary` style, `body` as paragraph text (`color: var(--color-text-secondary)`), and `bulletPoints` as a `<ul>` with `color: var(--color-text-secondary)`. Skeleton height should be `120px` matching the `SlotPlaceholder` it replaces.

---

### `frontend/src/views/DashboardView.vue` (modified)

**Analog:** existing `frontend/src/views/DashboardView.vue`

**SlotPlaceholder replacement pattern** (DashboardView.vue lines 235–246):
```html
<!-- Row 8: AI Commentary slot (col 12) -->
<div class="col-12">
  <SlotPlaceholder label="AI Daily Commentary — Phase 6" minHeight="120px" />
</div>

<!-- Row 9: AI Q&A (col 8) + BYO Key (col 4) -->
<div class="col-8">
  <SlotPlaceholder label="AI Q&A — Phase 6" minHeight="400px" />
</div>
<div class="col-4">
  <SlotPlaceholder label="LLM Key — Phase 6" minHeight="320px" />
</div>
```
These three `SlotPlaceholder` elements are replaced in Phase 6:
- Row 8 `col-12` → `<CommentaryCard :commentary="aiStore.commentary.data" :loading="..." :error="..." />`
- Row 9 `col-8` → `<ExplainDrawer :open="explainOpen" :explanation="..." ... />` (or a static AI stub)
- Row 9 `col-4` → `<BYOKeyModal :open="keyModalOpen" @close="keyModalOpen = false" />`

**Import + onMounted pattern** (DashboardView.vue lines 1–24):
```typescript
import { onMounted, computed } from 'vue'
import { usePortfolioStore } from '../stores/portfolio'
// ...
const portfolioStore = usePortfolioStore()

onMounted(() => {
  void portfolioStore.refreshAll()
})
```
Add after existing imports:
```typescript
import { useAiStore } from '../stores/ai'
import CommentaryCard from '../components/ai/CommentaryCard.vue'
import ExplainDrawer from '../components/ai/ExplainDrawer.vue'
import BYOKeyModal from '../components/ai/BYOKeyModal.vue'

const aiStore = useAiStore()
```
Add to `onMounted`: `void aiStore.fetchStatus()` and `void aiStore.fetchCommentary()`.

**TopBar modification:** Add `<AiModeBadge />` component import to `TopBar.vue` and place it in `.user-area` div before the username display:
```html
<div class="user-area">
  <AiModeBadge />           <!-- new — Phase 6 -->
  <span class="username-display">@{{ authStore.username }}</span>
  <button class="logout-btn" @click="handleLogout">Log out</button>
</div>
```

---

## Shared Patterns

### Principal Resolution (IDOR Prevention)
**Source:** `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java`, lines 141–148
**Apply to:** `AiController.java` — both `explain` and `commentary` endpoints
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
The portfolio ID is NEVER accepted as a `@RequestParam` or `@PathVariable` in any AI endpoint.

### @Transactional(readOnly = true)
**Source:** `AnalyticsController.java` lines 77–78; `AuthController.java` lines 85–86
**Apply to:** `AiController.explain()`, `AiController.commentary()`, any service methods that read portfolio data
```java
@GetMapping("/risk")
@Transactional(readOnly = true)
public ResponseEntity<RiskScorecardDto> getRisk(Authentication authentication) {
```

### DTO Record Style
**Source:** `VarResultDto.java` lines 1–26; `RiskScorecardDto.java` lines 1–26
**Apply to:** All four AI DTO records (`AiStatusDto`, `AiKeyRequest`, `ExplainResponseDto`, `CommentaryDto`)
```java
public record VarResultDto(
        String method,
        double confidence,
        // ...
) {}
```

### Idempotent Seeder Guard
**Source:** `SeedRunner.java` lines 112–121
**Apply to:** `AiSeedRunner.run()` — check `seedLogRepository.findById("ai-v1")` before inserting any rows; set `completed=true` at the very end of the transaction.

### Frontend asyncState Factory
**Source:** `frontend/src/stores/portfolio.ts` lines 22–32
**Apply to:** `stores/ai.ts` — copy the `AsyncState<T>` interface and `asyncState<T>()` factory function verbatim (do NOT import from portfolio.ts).

### CSS Token Design System
**Source:** All existing Vue components — `KpiCard.vue`, `AllocationChart.vue`, `TopBar.vue`, `RiskScorecard.vue`
**Apply to:** All new Vue components — `BYOKeyModal.vue`, `AiModeBadge.vue`, `ExplainDrawer.vue`, `CommentaryCard.vue`
Rule: zero hardcoded hex/rgb values. Use only `var(--color-*)`, `var(--space-*)`, `var(--radius-*)`, `var(--shadow-*)`, `var(--font-*)`.

### Integration Test Login Helper
**Source:** `AnalyticsControllerIntegrationTest.java` lines 214–238
**Apply to:** `KeyLeakageIntegrationTest.java`, `AiControllerIntegrationTest.java`, `AiKeyControllerTest.java` — copy `loginAndGetSessionCookie` and `authenticatedGet` helpers verbatim.

### Shimmer Skeleton Animation
**Source:** `KpiCard.vue` lines 114–130; `AllocationChart.vue` lines 263–278; `RiskScorecard.vue` lines 98–118
**Apply to:** `ExplainDrawer.vue`, `CommentaryCard.vue`, `BYOKeyModal.vue`
```css
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
.skeleton {
  background: linear-gradient(90deg,
    var(--color-bg-surface) 25%, var(--color-bg-overlay) 50%, var(--color-bg-surface) 75%);
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  border-radius: var(--radius-md);
}
```

---

## No Analog Found

Files with no close match in the codebase — planner must use RESEARCH.md code blocks as the pattern source:

| File | Role | Data Flow | Pattern Source |
|------|------|-----------|----------------|
| `backend/.../ai/chat/DemoModeAdvisor.java` | middleware | request-response | RESEARCH.md Pattern 2 (lines 228–287) |
| `backend/.../ai/chat/ChatClientStrategy.java` | service | request-response | RESEARCH.md Pattern 5 (lines 368–438) |
| `backend/.../ai/service/ExplainPositionService.java` | service | request-response | RESEARCH.md Pattern 9 (lines 566–593) |
| `backend/.../ai/service/CommentaryService.java` | service | request-response | RESEARCH.md Pattern 9 (shape only — commentary variant) |
| `backend/.../ai/session/LlmKeySessionHolder.java` | service | request-response | RESEARCH.md Pattern 6 (lines 452–483) |
| `backend/src/test/resources/application-test.yml` (additions) | config | — | RESEARCH.md Wave 0 Gaps (line 1025) |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/`, `backend/src/test/java/com/quantlens/`, `frontend/src/`
**Files scanned:** 35 source files
**Pattern extraction date:** 2026-06-09
