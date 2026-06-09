# Phase 9: MCP Server - Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 10 new/modified files
**Analogs found:** 8 / 10 (2 have no analog — use RESEARCH.md blocks)

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/src/main/java/com/quantlens/mcp/package-info.java` | config | — | `backend/src/main/java/com/quantlens/analytics/package-info.java` | exact |
| `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java` | service | request-response | `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java` | role-match (principal pattern identical) |
| `backend/src/main/java/com/quantlens/mcp/tools/PortfolioSummaryResult.java` | model | — | `backend/src/main/java/com/quantlens/portfolio/api/PortfolioPnlDto.java` (record) | role-match |
| `backend/src/main/java/com/quantlens/mcp/tools/RiskMetricsResult.java` | model | — | `backend/src/main/java/com/quantlens/analytics/api/RiskScorecardDto.java` (record) | role-match |
| `backend/src/main/java/com/quantlens/mcp/tools/PositionDetailResult.java` | model | — | `backend/src/main/java/com/quantlens/portfolio/api/HoldingDto.java` (record) | role-match |
| `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` (modify) | config | request-response | itself — additive edit only | exact |
| `backend/src/main/resources/application.yml` (modify) | config | — | RESEARCH.md §Code Examples (no codebase analog for MCP yml keys) | no analog |
| `backend/src/test/java/com/quantlens/mcp/PortfolioMcpToolsTest.java` | test | request-response | `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java` | exact |
| `backend/src/test/java/com/quantlens/mcp/McpAuthIntegrationTest.java` | test | request-response | `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java` | role-match |
| `.mcp.json` (modify — add `quantlens` entry) | config | — | itself (Phase-1 `context7` + `project-db` entries already present) | partial |

---

## Pattern Assignments

### `com/quantlens/mcp/package-info.java` (config, Modulith boundary)

**Analog:** `backend/src/main/java/com/quantlens/analytics/package-info.java`

**Full analog file** (lines 1–4):
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "Analytics",
        allowedDependencies = {"marketdata::domain", "portfolio::domain"})
package com.quantlens.analytics;
```

**Copy pattern — adapt for mcp:**
```java
@org.springframework.modulith.ApplicationModule(
        displayName = "MCP",
        allowedDependencies = {
            "portfolio::api",    // PortfolioService, HoldingDto, PortfolioPnlDto, AllocationSliceDto
            "analytics::api",    // RiskCalculator, RiskScorecardDto
            "portfolio::domain"  // PortfolioRepository.findPortfolioIdByUsername
        })
package com.quantlens.mcp;
```

**Key difference from analytics:** The `mcp` module needs `portfolio::api` (not just `portfolio::domain`) because it delegates to `PortfolioService` (the service bean, not just the repository). The `analytics` module reads `portfolio::domain` directly (bypassing PortfolioService) — the `mcp` module should go through the named API interface instead.

---

### `com/quantlens/mcp/tools/PortfolioMcpTools.java` (service, request-response)

**Analog:** `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java`

The `@McpTool` bean has no codebase analog for the MCP annotation itself. The principal-resolution pattern, service delegation, and error structure are copied verbatim from `AnalyticsController` and adapted for the MCP return-type contract. See RESEARCH.md §Pattern 1 for `@McpTool` annotation syntax.

**Imports pattern** — copy from `AnalyticsController.java` lines 1–16, add:
```java
// MCP-specific additions (no codebase analog — from RESEARCH.md §Standard Stack)
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
// For CallToolResult error shape (RESEARCH.md §Pattern 4 — verify class path at compile time)
import io.modelcontextprotocol.sdk.McpSchema;

// Existing patterns to keep
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.service.PortfolioService;
import com.quantlens.analytics.service.RiskCalculator;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
```

**Principal-resolution pattern** — copy verbatim from `AnalyticsController.java` lines 141–148, adapted for `SecurityContextHolder` pull (no method parameter injection in MCP tools):
```java
// AnalyticsController.java lines 141–148 — the EXISTING resolvePortfolioId uses
// Authentication injected as a parameter. In @McpTool methods there is no parameter
// injection; instead pull from SecurityContextHolder (same underlying object):
private Long resolvePortfolioId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = auth.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```
The `PortfolioRepository.findPortfolioIdByUsername` call is the same correlated-subquery used in both `PortfolioController.java:185` and `AnalyticsController.java:146`.

**Service delegation pattern** — copy from `AnalyticsController.java` lines 76–82:
```java
// AnalyticsController.java lines 76–82 — delegate → return
@GetMapping("/risk")
@Transactional(readOnly = true)
public ResponseEntity<RiskScorecardDto> getRisk(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return ResponseEntity.ok(riskCalculator.computeRiskScorecard(portfolioId));
}
```
In `PortfolioMcpTools`, the equivalent structure is:
```java
@McpTool(name = "get_risk_metrics", description = "...")
public McpSchema.CallToolResult getRiskMetrics() {
    try {
        Long portfolioId = resolvePortfolioId();
        RiskScorecardDto scorecard = riskCalculator.computeRiskScorecard(portfolioId);
        RiskMetricsResult result = mapToRiskMetricsResult(scorecard);
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent(objectMapper.writeValueAsString(result))))
                .isError(false).build();
    } catch (Exception e) {
        log.error("get_risk_metrics failed (not forwarded to client)", e);
        return McpSchema.CallToolResult.builder()
                .content(List.of(new McpSchema.TextContent("Risk metrics unavailable")))
                .isError(true).build();
    }
}
```

**Error-hygiene pattern** — principle from `GlobalAiExceptionHandler.java` lines 93–104:
```java
// GlobalAiExceptionHandler.java lines 93–104
// Principle: log internally with full stack trace; NEVER include exception message in response.
log.error("Unhandled exception in AI controller layer (not forwarded to client)", ex);
return ResponseEntity.internalServerError()
                     .body("{\"error\":\"Internal server error\"}");
```
Applied to MCP tools: every `@McpTool` method wraps its entire body in `try { ... } catch (Exception e)`. The `catch` block calls `log.error(...)` with the exception (so the full trace appears in server logs) and returns `CallToolResult` with a static safe message string — never `e.getMessage()`.

**Class-level declaration:**
```java
@Component   // REQUIRED — annotation scanner only discovers Spring beans (RESEARCH.md Pitfall 4)
public class PortfolioMcpTools {
    private static final Logger log = LoggerFactory.getLogger(PortfolioMcpTools.class);
    // Constructor injection — same pattern as AnalyticsController lines 54–64
}
```

---

### Result Record Files (model, —)

**Analog:** `HoldingDto`, `PortfolioPnlDto`, `RiskScorecardDto` — all are Java records in their respective `api` packages.

**Record DTO pattern** — representative example, locate by searching `HoldingDto`:
```java
// The existing HoldingDto (portfolio/api) is a Java record with all-args canonical constructor.
// All MCP result records must follow the same style: concrete records, no Spring/JPA annotations,
// all fields non-null (or explicitly nullable BigDecimal), flat structure.
// Copy this pattern for PortfolioSummaryResult, RiskMetricsResult, PositionDetailResult.
```

**`PortfolioSummaryResult.java`** — fields mirror `PortfolioPnlDto` (`totalMarketValue`, `totalCostBasis`, `totalUnrealizedGainAbs`, `totalUnrealizedGainPct`, `dailyChangeAbs`, `dailyChangePct`) plus `List<AllocationEntry> allocation`. The `AllocationEntry` is a nested record (not a Spring-managed class). See RESEARCH.md §Pattern 3 for the exact field list.

**`RiskMetricsResult.java`** — maps from `RiskScorecardDto`. Fields: `double sharpeRatio`, `double annualizedVolatility`, `double maxDrawdown`, `double beta`, `BigDecimal historicalVar95`, `BigDecimal parametricVar95`. Extract VaR amounts from `RiskScorecardDto.var()` list by `method` field. See RESEARCH.md §Pattern 3.

**`PositionDetailResult.java`** — fields are a direct subset of `HoldingDto`: `ticker`, `name`, `sector`, `quantity`, `avgCostBasis`, `currentPrice`, `currentMarketValue`, `portfolioWeight`, `unrealizedPnlAbs`, `unrealizedPnlPct`. Populate from the single matching `HoldingDto` returned by `PortfolioService.getHoldings(portfolioId)` filtered by ticker.

**Rule:** All result records must be in `com.quantlens.mcp.tools` package and must NOT extend or implement any interface — the MCP annotation scanner generates JSON schema from the concrete record component types. Do NOT return `List<HoldingDto>` or any type from another module directly.

---

### `SecurityConfig.java` (modify — two additive changes)

**Analog:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` (the file itself — two targeted edits)

**Change 1: CSRF exemption.** Add to the existing `.ignoringRequestMatchers(...)` block at lines 125–132. The current block ends with:
```java
// SecurityConfig.java lines 125–132 — existing block
.ignoringRequestMatchers("/api/auth/login", "/api/auth/logout")
.ignoringRequestMatchers(
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/key"),
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/chat"))
```
Add one more matcher inside the second `ignoringRequestMatchers(...)` call:
```java
// Phase 9 addition — MCP POST exempt from CSRF (machine client cannot carry XSRF-TOKEN cookie)
AntPathRequestMatcher.antMatcher("/mcp")
```
Result after edit:
```java
.ignoringRequestMatchers(
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/key"),
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/chat"),
        // Phase 9: MCP Streamable-HTTP POST cannot carry XSRF-TOKEN cookie
        AntPathRequestMatcher.antMatcher("/mcp")
)
```

**Change 2: HTTP Basic.** Add `.httpBasic(...)` as a new fluent call in `filterChain(...)` after `.csrf(...)` and before `.exceptionHandling(...)`:
```java
// Phase 9 addition — HTTP Basic for MCP machine-client auth on /mcp
// Coexists with existing .formLogin() — Vue SPA continues using session cookies.
.httpBasic(basic -> basic.realmName("QuantLens MCP"))
```

No other changes to `SecurityConfig.java`. Do NOT change the `authorizeHttpRequests` block — `/mcp/**` already falls under `.anyRequest().authenticated()`, so it is protected by default.

---

### `application.yml` (modify — add MCP server config block)

**Analog:** None in codebase. Copy directly from RESEARCH.md §Code Examples "Complete application.yml additions":
```yaml
# Phase 9: MCP Server (Streamable-HTTP, same port as app — port 8080)
# Source: docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html
spring:
  ai:
    mcp:
      server:
        name: quantlens-mcp
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC
        annotation-scanner:
          enabled: true          # default true; explicit for clarity
        streamable-http:
          mcp-endpoint: /mcp    # default /mcp; explicit for documentation
```
Merge under the existing `spring.ai` key already in `application.yml`. Do not create a new top-level `spring` block — merge into the existing one.

---

### `backend/src/test/java/com/quantlens/mcp/PortfolioMcpToolsTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java`

**Class-level setup** — copy from `RiskCalculatorTest.java` lines 49–80:
```java
// RiskCalculatorTest.java lines 49–80 — class declaration, golden constants, @BeforeEach
class RiskCalculatorTest extends AbstractPostgresIntegrationTest {

    private static final double GOLDEN_SHARPE       = 0.36442669;
    private static final double GOLDEN_HIST_VAR_AMOUNT = 1464.52;

    @Autowired
    private RiskCalculator riskCalculator;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private Long portfolioId;

    @BeforeEach
    @Transactional
    void resolveAlicePortfolio() {
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        // ... resolve portfolioId from alice's portfolios
    }
```

**SecurityContext mock pattern** — from RESEARCH.md §Test Strategy Details (no codebase analog; this is the new pattern):
```java
// PortfolioMcpToolsTest.java — add these lifecycle methods alongside @BeforeEach above
@BeforeEach
void setupSecurity() {
    UsernamePasswordAuthenticationToken auth =
        new UsernamePasswordAuthenticationToken("alice", null, List.of());
    SecurityContextHolder.getContext().setAuthentication(auth);
}

@AfterEach
void clearSecurity() {
    SecurityContextHolder.clearContext();
}
```

**Golden-value assertion pattern** — copy from `AnalyticsControllerIntegrationTest.java` lines 114–133:
```java
// AnalyticsControllerIntegrationTest.java lines 114–133 — golden-value assertion
assertThat(historicalVarAmount)
        .as("HISTORICAL VaR amount must match golden value ±0.01")
        .isCloseTo(GOLDEN_HIST_VAR_AMOUNT, within(0.01));
```
Apply the same `isCloseTo(..., within(...))` pattern for each `@McpTool` method result field, using the same golden constants already in `RiskCalculatorTest` (`GOLDEN_SHARPE = 0.36442669`, `GOLDEN_HIST_VAR_AMOUNT = 1464.52`, etc.).

**Error-hygiene test pattern** — from RESEARCH.md §Test Strategy Details:
```java
@Test
void toolException_doesNotLeakStackTrace() {
    // Use a @MockBean or Mockito spy to force PortfolioService to throw
    // RuntimeException("INTERNAL_DB_ERROR_secret") before calling the real tool.
    // Assert the CallToolResult.isError() == true AND content does NOT contain
    // "RuntimeException", "com.quantlens", or the sentinel error string.
    var result = tools.getPortfolioSummary(); // CallToolResult
    assertThat(result.isError()).isTrue();
    // extract text content and assert no leakage
    assertThat(content).doesNotContain("RuntimeException");
    assertThat(content).doesNotContain("com.quantlens");
    assertThat(content).doesNotContain("INTERNAL_DB_ERROR_secret");
}
```

**`@Transactional(readOnly = true)`** on test class or individual methods — copy from `RiskCalculatorTest` line 76: `@BeforeEach @Transactional void resolveAlicePortfolio()`.

---

### `backend/src/test/java/com/quantlens/mcp/McpAuthIntegrationTest.java` (test, request-response)

**Analog:** `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java`

**Class declaration and field pattern** — copy from `KeyLeakageIntegrationTest.java` lines 52–56:
```java
// KeyLeakageIntegrationTest.java lines 52–56
class KeyLeakageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
```

**Unauthenticated 401 assertion** — copy the structural approach from `AnalyticsControllerIntegrationTest.java` lines 54–64:
```java
// AnalyticsControllerIntegrationTest.java lines 54–64 — 401 gate test
@Test
void getRisk_unauthenticated_returns401() {
    ResponseEntity<String> response = restTemplate.exchange(
            "/api/portfolio/risk",
            HttpMethod.GET,
            HttpEntity.EMPTY,
            String.class);
    assertThat(response.getStatusCode())
            .as("unauthenticated GET /api/portfolio/risk must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
}
```
Apply for `/mcp` with POST (MCP uses POST for all protocol messages):
```java
@Test
void mcp_withoutAuth_returns401() {
    ResponseEntity<String> response = restTemplate.postForEntity("/mcp", "{}", String.class);
    assertThat(response.getStatusCode())
            .as("POST /mcp without auth must return 401")
            .isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

**`AbstractPostgresIntegrationTest` inheritance:**
```java
// AbstractPostgresIntegrationTest.java lines 28–30
// @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
// @ActiveProfiles("test")
// Inherited by extending the class — no re-annotation needed.
class McpAuthIntegrationTest extends AbstractPostgresIntegrationTest { ... }
```

---

### `.mcp.json` (modify — add `quantlens` product entry)

**Analog:** The existing `.mcp.json` at project root (Phase-1 entries `context7` and `project-db`).

**Current file** (lines 1–16) — the existing structure to extend:
```json
{
  "mcpServers": {
    "context7": { ... },
    "project-db": { ... }
  }
}
```

**Add alongside existing entries** (from RESEARCH.md §Code Examples "`.mcp.json` product server entry"):
```json
"quantlens": {
  "type": "http",
  "url": "http://localhost:8080/mcp",
  "headers": {
    "Authorization": "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}"
  }
}
```
The Base64 value `YWxpY2U6ZGVtbzEyMzQ=` decodes to `alice:demo1234` (the seeded demo user). It is safe to commit — it is the same password shown on the login screen. Developers can override via the `QUANTLENS_MCP_AUTH` environment variable.

---

## Shared Patterns

### Principal Resolution (applies to `PortfolioMcpTools.java`)

**Source:** `AnalyticsController.java` lines 141–148 AND `PortfolioController.java` lines 180–187

Both controllers implement identical logic. The MCP tool replicates this but pulls from `SecurityContextHolder` (no method injection in `@McpTool`):
```java
// AnalyticsController.java lines 141–148 (canonical source — same in PortfolioController)
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = authentication.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```
In tools, replace `authentication` parameter with `SecurityContextHolder.getContext().getAuthentication()`. All other logic — the `isAuthenticated()` check, `getName()`, `findPortfolioIdByUsername`, and the `orElseThrow(401)` — is copied verbatim.

### Error Non-Disclosure (applies to `PortfolioMcpTools.java`)

**Source:** `GlobalAiExceptionHandler.java` lines 93–104

**Principle:** Log internally with full exception (stack trace visible in server logs). Return a static, generic safe message to the caller. Never include `e.getMessage()`, class names, or any dynamic content from the exception in the response body.
```java
// GlobalAiExceptionHandler.java lines 93–104
log.error("Unhandled exception in AI controller layer (not forwarded to client)", ex);
return ResponseEntity.internalServerError()
                     .body("{\"error\":\"Internal server error\"}");
```
For MCP tools: replace the `ResponseEntity` return with `CallToolResult.builder().content(...safe static string...).isError(true).build()`.

### `@Transactional(readOnly = true)` (applies to `PortfolioMcpTools.java`)

**Source:** `PortfolioService.java` line 57 (class-level), `AnalyticsController.java` line 77 (method-level)

The delegated services (`PortfolioService`, `RiskCalculator`) already have `@Transactional(readOnly = true)` at the class level. If `PortfolioMcpTools` calls multiple service methods in one tool invocation, annotate the tool method with `@Transactional(readOnly = true)` to ensure a single consistent snapshot — matching the approach in `AnalyticsController.java`.

### `AbstractPostgresIntegrationTest` Inheritance (applies to both test classes)

**Source:** `AbstractPostgresIntegrationTest.java` lines 28–62

All integration tests inherit this class to get:
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`
- `@ActiveProfiles("test")`
- Static `PostgreSQLContainer` (pgvector:pg16) via `@ServiceConnection`
- `@BeforeAll initExtensions()` — creates `vector` and `uuid-ossp` extensions

Do NOT re-annotate `@SpringBootTest` in the subclass — it inherits automatically.

### IDOR Prevention (applies to `PortfolioMcpTools.java`)

**Source:** `PortfolioController.java` lines 37–41 (Javadoc) and `AnalyticsController.java` lines 33–37

The portfolio identity is derived ONLY from the authenticated principal. No tool parameter accepts a user ID, portfolio ID, or persona. The ticker parameter in `get_position_detail` is allowed (it identifies a security, not a user) but must be sanitized (uppercase, alphanumeric + `.`, max 10 chars) before passing to `getHoldings()`.

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `backend/src/main/resources/application.yml` (MCP keys) | config | — | No MCP server configuration exists in the codebase; use RESEARCH.md §Code Examples "Complete application.yml additions" verbatim |
| `@McpTool` / `@McpToolParam` annotation syntax | — | — | No `@McpTool` beans exist yet; use RESEARCH.md §Pattern 1 and §Pattern 4 for the annotation declaration and `CallToolResult` error shape |

---

## Maven Dependency (applies to `pom.xml`)

**Source:** RESEARCH.md §Maven Addition

Add to `backend/pom.xml` within the `<dependencies>` block. The BOM `spring-ai-bom:1.1.6` is already imported — no `<version>` element needed:
```xml
<!-- Phase 9: MCP Server (Streamable-HTTP, WebMVC, same port as app) -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/`, `backend/src/test/java/com/quantlens/`, project root `.mcp.json`
**Files scanned:** 13 source files read directly
**Pattern extraction date:** 2026-06-10
