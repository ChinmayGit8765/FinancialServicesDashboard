# Phase 10: Polish & Documentation — Pattern Map

**Mapped:** 2026-06-10
**Files analyzed:** 7
**Analogs found:** 7 / 7

---

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|-------------------|------|-----------|----------------|---------------|
| `backend/pom.xml` | config | — | `backend/pom.xml` lines 86–122 (existing dep blocks) | exact |
| `backend/src/main/java/com/quantlens/config/OpenApiConfig.java` | config | request-response | `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` | role-match |
| `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` | config/security | request-response | itself — additive edit to lines 87–92 | exact |
| `backend/src/test/java/com/quantlens/OpenApiPathsIntegrationTest.java` | test | request-response | `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java` | exact |
| `backend/src/test/java/com/quantlens/QuantLensModulithTest.java` | test | — | itself — additive second `@Test` method | exact |
| `README.md` | docs | — | `README.md` existing `## Stochastic Forecasting Models` / `## Product MCP Server` section structure | exact |
| `frontend/src/components/ai/StructuredOutputChart.vue` | component | — | `frontend/src/components/ai/ExplainDrawer.vue` lines 1–8 (header comment style) | role-match |

---

## Pattern Assignments

### `backend/pom.xml` (config — dependency addition)

**Analog:** `backend/pom.xml` lines 86–122

**Existing Phase-comment + dependency block pattern** (lines 86–122):
```xml
<!-- Spring Modulith -->
<dependency>
    <groupId>org.springframework.modulith</groupId>
    <artifactId>spring-modulith-starter-core</artifactId>
</dependency>

<!-- Phase 9: MCP Server ... -->
<!-- BOM 1.1.6 already imported above — no version needed here -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Pattern to copy for Phase 10 addition** — insert after the existing `spring-modulith-starter-core` block, before the `postgresql` runtime dep:
```xml
<!-- Phase 10: OpenAPI spec + Swagger UI — no version in Spring Boot BOM, pin explicitly -->
<!-- springdoc 2.8.17 built against Spring Boot 3.5.13 (exact match) -->
<!-- github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17 -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

Key rule: no BOM manages springdoc — `<version>` is mandatory here (unlike every Spring AI dep above it).

---

### `backend/src/main/java/com/quantlens/config/OpenApiConfig.java` (config — new @Configuration bean)

**Analog:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` lines 1–21, 66–79

**Package / imports pattern** (SecurityConfig.java lines 1–21):
```java
package com.quantlens.security.config;

import ...;

/**
 * [Javadoc explaining the bean's purpose and key decisions]
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    ...
    @Bean
    public SecurityFilterChain filterChain(...) throws Exception {
```

**Pattern to copy for OpenApiConfig** — same package-level @Configuration + single @Bean shape:
```java
package com.quantlens.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * <p>
 * springdoc-openapi auto-discovers all {@code @RestController} endpoints; this bean
 * adds metadata (title, version) and security scheme documentation only.
 * The MCP endpoint ({@code /mcp}) uses {@code @McpTool @Component} — not a
 * {@code @RestController} — and is therefore automatically excluded from the spec.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quantLensOpenApi() {
        return new OpenAPI()
            .info(new Info()
                .title("QuantLens API")
                .description("AI-augmented portfolio & market intelligence dashboard")
                .version("1.0.0"))
            .addSecurityItem(new SecurityRequirement().addList("session-cookie"))
            .components(new Components()
                .addSecuritySchemes("session-cookie",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.COOKIE)
                        .name("JSESSIONID")
                        .description("Session cookie set by POST /api/auth/login"))
                .addSecuritySchemes("http-basic-mcp",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("basic")
                        .description("HTTP Basic for /mcp (machine-client auth)")));
    }
}
```

---

### `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` (config/security — additive edit)

**Analog:** itself, lines 84–93

**Existing permitAll block** (lines 84–93 — the EXACT text being modified):
```java
.authorizeHttpRequests(auth -> auth
        // Public endpoints — permitAll keeps these open regardless of login mechanism
        // (OAuth upgrade seam: add oauth2Login without touching these rules)
        .requestMatchers(
                "/api/auth/login",
                "/api/auth/logout",
                "/api/auth/personas",
                "/actuator/health"
        ).permitAll()
        .anyRequest().authenticated()
)
```

**Pattern to apply** — extend the existing `.requestMatchers(...)` call with four new path strings, keeping exact indentation (8 spaces + 8 spaces):
```java
.requestMatchers(
        "/api/auth/login",
        "/api/auth/logout",
        "/api/auth/personas",
        "/actuator/health",
        // Phase 10: OpenAPI / Swagger UI — public API docs for demo
        // Both exact (/v3/api-docs) and wildcard (/**) needed; see RESEARCH Pitfall 2
        "/v3/api-docs",
        "/v3/api-docs/**",
        "/swagger-ui.html",
        "/swagger-ui/**"
).permitAll()
```

No other changes to the file. CSRF note: springdoc endpoints are GET-only; no CSRF exemption needed.

---

### `backend/src/test/java/com/quantlens/OpenApiPathsIntegrationTest.java` (test — new integration test)

**Analog:** `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java`

**Class declaration + @Autowired TestRestTemplate pattern** (KeyLeakageIntegrationTest.java lines 52–59):
```java
class KeyLeakageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
    ...
```

**No-auth GET + status + body assertion pattern** (KeyLeakageIntegrationTest.java lines 104–110 adapted for unauthenticated GET — `/v3/api-docs` is `permitAll` so no cookie needed):
```java
ResponseEntity<String> statusResponse = authenticatedGet("/api/ai/status", sessionCookie);
assertThat(statusResponse.getStatusCode())
        .as("GET /api/ai/status should return 200")
        .isEqualTo(HttpStatus.OK);
assertThat(statusResponse.getBody())
        .as("GET /api/ai/status response must NOT contain the API key")
        .doesNotContain(testKey);
```

**doesNotContain assertion pattern** (KeyLeakageIntegrationTest.java lines 97–103):
```java
assertThat(setKeyResponse.getBody())
        .as("POST /api/ai/key response must NOT have an apiKey field")
        .doesNotContainIgnoringCase("apiKey");
```

**Pattern to copy for OpenApiPathsIntegrationTest**:
```java
package com.quantlens;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test: /v3/api-docs returns 200, contains expected path prefixes,
 * and does NOT expose secret field names (apiKey, llmKey, password).
 */
class OpenApiPathsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void openApiSpecContainsExpectedPaths() {
        // /v3/api-docs is permitAll — no auth header needed
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);

        assertThat(response.getStatusCode())
                .as("GET /v3/api-docs must return 200 (permitAll configured in SecurityConfig)")
                .isEqualTo(HttpStatus.OK);

        String body = response.getBody();
        assertThat(body)
                .as("Spec must document portfolio paths")
                .contains("/api/portfolio");
        assertThat(body)
                .as("Spec must document analytics paths")
                .contains("/api/analytics");
        assertThat(body)
                .as("Spec must document AI paths")
                .contains("/api/ai");
        // No-secret-leak assertions (extends KeyLeakageIntegrationTest hygiene to schema level)
        assertThat(body)
                .as("Spec must NOT expose apiKey as a schema field name (T-06-01 schema hygiene)")
                .doesNotContain("\"apiKey\"");
        assertThat(body)
                .as("Spec must NOT expose llmKey as a schema field name")
                .doesNotContain("\"llmKey\"");
    }
}
```

**Import pattern** (AiControllerIntegrationTest.java lines 1–17 — canonical import set for this test type):
```java
import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;
```

---

### `backend/src/test/java/com/quantlens/QuantLensModulithTest.java` (test — additive second method)

**Analog:** itself lines 1–20

**Existing class structure** (full file, 20 lines):
```java
package com.quantlens;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith architecture verification test.
 * <p>
 * Verifies that the declared package boundaries form a valid modular structure:
 * no illegal cross-module references and no cycles. This test passes once the
 * module packages (marketdata, portfolio, security, seed) exist — it does NOT
 * require a running Spring context or database connection.
 */
class QuantLensModulithTest {

    @Test
    void applicationModulesShouldBeValid() {
        ApplicationModules.of(QuantLensApplication.class).verify();
    }
}
```

**Pattern to apply** — extract modules to a static field and add a second test method alongside the existing one. Requires one new import (`org.springframework.modulith.docs.Documenter`):

```java
package com.quantlens;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Spring Modulith architecture verification and documentation tests.
 * <p>
 * Both methods use classpath scanning only — NO Spring context, NO database.
 * {@link #applicationModulesShouldBeValid()} is the living module-boundary contract.
 * {@link #writeModuleDiagrams()} generates PlantUML source + module canvases to
 * {@code target/spring-modulith-docs/} (git-ignored; regenerate with
 * {@code ./mvnw test -pl backend -Dtest=QuantLensModulithTest}).
 */
class QuantLensModulithTest {

    private static final ApplicationModules MODULES =
            ApplicationModules.of(QuantLensApplication.class);

    @Test
    void applicationModulesShouldBeValid() {
        MODULES.verify();
    }

    @Test
    void writeModuleDiagrams() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()    // target/spring-modulith-docs/all-modules.puml
                .writeModuleCanvases()        // target/spring-modulith-docs/*.canvas.adoc
                .writeAggregatingDocument(); // target/spring-modulith-docs/all-docs.adoc
    }
}
```

Key facts: no `@SpringBootTest`, no `@ActiveProfiles` — Documenter uses classpath scanning like `verify()`. `spring-modulith-docs` is already on classpath via `spring-modulith-starter-test` (pom.xml line 111).

---

### `README.md` (docs — additive sections)

**Analog:** `README.md` existing section structure — `## Stochastic Forecasting Models` (line 96) and `## Product MCP Server` (line 169)

**Section heading + horizontal-rule divider pattern** (README.md lines 96–103):
```markdown
## Stochastic Forecasting Models

The Monte Carlo fan chart supports four models: ...

See [Model documentation](docs/MODELS.md) for rationale...

---
```

**Cross-reference to docs/ file pattern** (line 100):
```markdown
See [Model documentation](docs/MODELS.md) for rationale, key assumptions, parameters used...
```

**Pattern to copy** — insert a `## Screenshots` section and a `## API & Architecture Docs` section using the same heading level, prose intro, horizontal-rule dividers, and relative `docs/` links:

```markdown
## Screenshots

> **Human step:** Capture these screenshots in live mode (BYO-key popup → paste a real LLM API key).
> See **Capture Guide** below.

| View | File | Description |
|------|------|-------------|
| Dashboard — demo mode | `docs/screenshots/dashboard-demo.png` | All AI panels, fan-chart model selector |
| AI Q&A (RAG) | `docs/screenshots/rag-chat.png` | Chat panel with source citations |
| Structured output | `docs/screenshots/structured-output.png` | Horizontal bar chart from BeanOutputConverter |
| Fan chart — Heston | `docs/screenshots/fan-chart-heston.png` | Monte Carlo fan chart, Heston model |
| BYO-key popup | `docs/screenshots/byo-key.png` | One-click key popup (session-only) |

### Capture Guide

1. `docker compose up` (or `docker compose up --build` on first run)
2. Open http://localhost:5173 → log in as **alice** (`demo1234`)
3. Click the key icon → paste a real Anthropic or OpenAI API key (session-only, never persisted)
4. The AI mode badge changes from **DEMO** to **LIVE**
5. Screenshot each AI panel (commentary, explain-position drawer, RAG chat, structured output)
6. Cycle the fan-chart model selector; screenshot each model
7. Save PNGs to `docs/screenshots/` with the filenames above

---

## API & Architecture Docs

- **OpenAPI spec:** http://localhost:8080/v3/api-docs (JSON)
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **Module diagram:** regenerate with `./mvnw test -pl backend -Dtest=QuantLensModulithTest`
  → `backend/target/spring-modulith-docs/all-modules.puml` (PlantUML source)
- **Stochastic models:** [docs/MODELS.md](docs/MODELS.md)
- **OAuth upgrade path:** [OAuth Upgrade Path](#oauth-upgrade-path) section below

---
```

---

### `frontend/src/components/ai/StructuredOutputChart.vue` (component — header comment + figcaption edit)

**Analog:** `frontend/src/components/ai/ExplainDrawer.vue` lines 1–8

**Accurate, present-tense header comment pattern** (ExplainDrawer.vue lines 1–8):
```typescript
<script setup lang="ts">
/**
 * ExplainDrawer — slide-in panel showing AI narrative for a holding.
 *
 * Fixed-position panel on the right side, z-index 200 (above TopBar at 100).
 * Three states: loading (shimmer skeleton), error (static copy + retry), populated (narrative).
 * Hidden entirely when open=false.
 */
```

Note: no "STUB", no "Phase N will…" forward references — accurate description of what the component does now.

**Current stale text to replace** (StructuredOutputChart.vue lines 2–11):
```typescript
/**
 * StructuredOutputChart — DEMO STUB only.
 *
 * Renders a horizontal bar chart from a StructuredChartDto (title + series[]).
 * In demo mode, DashboardView fetches /ai-structured-demo.json into the ai store.
 * Phase 8 (AI-06) will swap the source for a live BeanOutputConverter-typed record
 * via the existing /api/ai endpoint — no chart changes required (same DTO shape).
 *
 * Three states: shimmer skeleton (loading), static error (error), chart (populated).
 */
```

**Replacement header comment** (mirror ExplainDrawer's present-tense style):
```typescript
/**
 * StructuredOutputChart — renders a horizontal bar chart from a StructuredChartDto
 * (title + series[]).
 *
 * Demo mode: DashboardView injects a static fixture via the ai store (ai_seed_content V4).
 * Live mode (AI-06): the same store slot is populated by a live
 * BeanOutputConverter-typed record from /api/ai/structured — same DTO shape, no chart changes.
 *
 * Three states: shimmer skeleton (loading), static error (error), chart (populated).
 */
```

**Current stale figcaption to replace** (StructuredOutputChart.vue lines 122–125):
```html
<figcaption class="sr-only">
  AI structured output chart showing detected sector exposure from seeded demo data.
  Phase 8 will replace this demo fixture with a live BeanOutputConverter-typed record.
</figcaption>
```

**Replacement figcaption** (screen-reader-visible — accuracy matters):
```html
<figcaption class="sr-only">
  AI structured output chart showing detected sector exposure.
  In demo mode this uses seeded data; in live mode it is driven by a
  BeanOutputConverter-typed record from the AI backend.
</figcaption>
```

---

## Shared Patterns

### @Configuration bean — package and class shape
**Source:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` lines 1–21, 66–68
**Apply to:** `OpenApiConfig.java`
```java
package com.quantlens.config;   // place in config/, parallel to security/config/

import ...;

/**
 * [Javadoc with purpose + key decisions]
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI quantLensOpenApi() { ... }
}
```

### Integration test base class + TestRestTemplate
**Source:** `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java` + `backend/src/test/java/com/quantlens/ai/AiControllerIntegrationTest.java` lines 31–35
**Apply to:** `OpenApiPathsIntegrationTest.java`
```java
class OpenApiPathsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
```
The base class provides `@SpringBootTest(webEnvironment = RANDOM_PORT)`, `@ActiveProfiles("test")`, Testcontainers Postgres. `TestRestTemplate` is auto-configured.

### pom.xml dependency block comment style
**Source:** `backend/pom.xml` lines 115–122 (Phase 9 block)
**Apply to:** Phase 10 springdoc dependency block
```xml
<!-- Phase N: [short purpose] — [version/BOM note] -->
<!-- [verification source URL or note] -->
<dependency>
    <groupId>...</groupId>
    <artifactId>...</artifactId>
    <version>...</version>   <!-- only when BOM does NOT manage it -->
</dependency>
```

---

## No Analog Found

None. All 7 files have close analogs in the existing codebase.

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/`, `backend/src/test/java/com/quantlens/`, `frontend/src/components/ai/`, `backend/pom.xml`, `README.md`
**Files scanned:** 10 source files read
**Pattern extraction date:** 2026-06-10
