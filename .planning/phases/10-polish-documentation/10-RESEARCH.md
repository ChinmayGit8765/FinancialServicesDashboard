# Phase 10: Polish & Documentation — Research

**Researched:** 2026-06-10
**Domain:** springdoc-openapi, Spring Modulith Documenter, README polish
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` (2.x compatible with Spring Boot 3.5 / Spring 6.2). No version if a BOM manages it; otherwise pin the latest 2.8.x line.
- `OpenAPI` bean with title/description/version + a `securityScheme` note for session-cookie + HTTP Basic (MCP) auth.
- Security: permit `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` in `SecurityConfig` (additive `.requestMatchers(...).permitAll()` BEFORE `.anyRequest().authenticated()`).
- A test asserts `/v3/api-docs` returns 200 and contains the portfolio, analytics, and AI paths.
- Keep `QuantLensModulithTest#applicationModulesShouldBeValid` (verify() gate); add a second test method that runs `Documenter` to generate the module diagram + canvas into `backend/target/spring-modulith-docs`.
- Do NOT weaken any module boundary to make generation pass.
- Restructure/extend README with a screenshot section: placeholders (`docs/screenshots/*.png`) + captions + a short Capture Guide. Actual image capture is a **human step** (UAT).
- Fix the stale `StructuredOutputChart.vue` "DEMO STUB" header comment; sweep for other stale stub/TODO comments.

### Claude's Discretion
- Exact springdoc version, the OpenAPI bean metadata, where the Modulith diagram is written/committed, and the precise README screenshot layout.

### Deferred Ideas (OUT OF SCOPE)
- Auto-committing rendered PNG screenshots (needs the user's live key — human step).
- Per-endpoint rich OpenAPI annotations / examples.
- Publishing the OpenAPI spec to an external portal / GitHub Pages.
- MCP-03 @McpResource, streaming, additional tools.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DOCS-01 | README includes screenshots demonstrating the app running live with an LLM (captured via live mode) | Screenshot placeholders, Capture Guide, and README restructuring are all researchable and implementable; actual image capture is a human step |
</phase_requirements>

---

## Summary

Phase 10 is a pure polish phase: zero new business logic, no DB changes, no new Modulith modules. Three work streams run in parallel.

**Stream 1 — springdoc-openapi:** Add one dependency (`springdoc-openapi-starter-webmvc-ui:2.8.17`), two `SecurityConfig` lines, and one `@Configuration` bean. springdoc auto-discovers all existing `@RestController` endpoints with no per-endpoint annotation work. The MCP module uses `@McpTool` on a non-`@RestController` `@Component` — springdoc will ignore it entirely (correct behaviour; `/mcp` is not a REST API). One integration test asserts the spec returns 200 and contains the expected API path prefixes.

**Stream 2 — Spring Modulith Documenter:** `spring-modulith-docs` is already on the test classpath via `spring-modulith-starter-test` (locked at BOM 1.4.11). No new dependencies needed. Add a single second test method to `QuantLensModulithTest` that calls `new Documenter(modules).writeModulesAsPlantUml().writeModuleCanvases()`. Output lands in `target/spring-modulith-docs/` automatically. No external Graphviz binary is required — springdoc generates `.adoc` (AsciiDoc) + embedded PlantUML source that renders headlessly. The test must not fail; since `verify()` already passes (Phase 9), generation will succeed.

**Stream 3 — README + StructuredOutputChart polish:** Additive README edit (insert Screenshots section + Capture Guide + Docs pointer). Fix the `StructuredOutputChart.vue` header comment from the now-stale Phase 8 forward reference. No logic changes.

**Primary recommendation:** Pin springdoc at `2.8.17` explicitly (springdoc BOM is NOT imported by Spring Boot parent — must pin manually). Use `new Documenter(modules).writeModulesAsPlantUml().writeModuleCanvases()` in a `@Test` method alongside the existing `verify()` test.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| OpenAPI spec generation | API/Backend (springdoc servlet filter) | — | springdoc hooks into Spring MVC dispatcher; no frontend involvement |
| Swagger UI | API/Backend (springdoc static resources) | — | Served as static resources from the backend on `/swagger-ui.html` |
| Module diagram generation | Test / build artifact | — | Runs at `mvn test` time; writes to `target/`; no runtime component |
| README screenshots | Static docs (human-captured) | — | Human step; Phase 10 scaffolds the location + captions |
| Comment cleanup | Frontend + Backend source | — | Vue SFC + Java source; zero logic impact |

---

## Standard Stack

### Core (new addition this phase)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.springdoc:springdoc-openapi-starter-webmvc-ui` | **2.8.17** | OpenAPI 3 spec + Swagger UI for Spring WebMVC | Official library for Spring Boot 3.x OpenAPI; auto-discovers `@RestController` endpoints; v2.8.17 was built against Spring Boot 3.5.13 (exact match) [VERIFIED: github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17] |

### Already Present (no change needed)

| Library | Version | Purpose | Notes |
|---------|---------|---------|-------|
| `spring-modulith-starter-test` | 1.4.11 (BOM) | Pulls in `spring-modulith-docs` for `Documenter` | Already in pom.xml test scope [VERIFIED: docs.spring.io/spring-modulith/reference/appendix.html] |
| `spring-modulith-starter-core` | 1.4.11 (BOM) | Runtime module boundary enforcement | Already in pom.xml |

### Version Verification

springdoc-openapi-starter-webmvc-ui `2.8.17` release notes confirm:
- Dependency: `spring-boot:3.5.13` [VERIFIED: github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17]
- Release date: April 2026
- No BOM manages springdoc within the Spring Boot parent POM — **must pin version explicitly**

**Installation (pom.xml addition):**
```xml
<!-- Phase 10: OpenAPI spec + Swagger UI — no version in Spring Boot BOM, pin explicitly -->
<!-- springdoc 2.8.17 was built against Spring Boot 3.5.13 (exact match) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.8.17</version>
</dependency>
```

---

## Package Legitimacy Audit

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.17` | Maven Central | ~4 yrs (project since 2019) | High (official Spring doc library) | github.com/springdoc/springdoc-openapi | N/A (Java/Maven) | Approved [VERIFIED: github.com/springdoc/springdoc-openapi] |

*slopcheck is an npm tool; this phase adds one Maven dependency, not npm. Maven legitimacy verified by official GitHub release and Spring Boot co-release cadence.*

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
HTTP request → Spring Security filterChain
                │
                ├── /v3/api-docs/**        permitAll → springdoc OpenApiWebMvcResource
                ├── /swagger-ui/**         permitAll → springdoc SwaggerUiHome
                ├── /swagger-ui.html       permitAll → springdoc SwaggerUiHome
                │
                ├── /api/**                authenticated → @RestController (portfolio, analytics, ai, auth)
                │                                          ↑ autodiscovered by springdoc
                └── /mcp                   authenticated → @McpTool @Component
                                                          ↑ NOT @RestController → springdoc ignores it
```

### Recommended Structure (additions only)

```
backend/src/main/java/com/quantlens/
└── config/
    └── OpenApiConfig.java           # new — OpenAPI bean + securitySchemes

backend/src/test/java/com/quantlens/
└── QuantLensModulithTest.java       # extend: add diagramGeneration() method
└── OpenApiPathsIntegrationTest.java # new — GET /v3/api-docs → 200 + path assertions

docs/
└── screenshots/                     # new — README placeholder PNGs land here
    └── .gitkeep

target/spring-modulith-docs/         # generated by Documenter at test time (git-ignored)
    ├── all-modules.puml              # PlantUML overview diagram
    ├── *.canvas                      # Module canvases (AsciiDoc)
    └── all-docs.adoc                 # Aggregated document
```

### Pattern 1: springdoc Security Permit

Additive insert into the existing `SecurityConfig.filterChain` `authorizeHttpRequests` block:

```java
// Source: springdoc.org/faq.html + SpringDoc official docs
.requestMatchers(
    "/api/auth/login",
    "/api/auth/logout",
    "/api/auth/personas",
    "/actuator/health",
    // Phase 10: OpenAPI / Swagger UI — public API docs for a demo
    "/v3/api-docs",
    "/v3/api-docs/**",
    "/swagger-ui.html",
    "/swagger-ui/**"
).permitAll()
```

CSRF note: these endpoints are all GET-only. No CSRF exemption needed. [VERIFIED: springdoc.org/faq.html]

### Pattern 2: OpenAPI Bean

```java
// Source: springdoc.org — OpenAPI bean configuration
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

No `@Hidden` or per-controller annotations required — springdoc discovers all `@RestController` beans automatically. The `/mcp` endpoint uses `@McpTool` on a `@Component` (not `@RestController`), so it is already excluded from the spec with no configuration needed. [VERIFIED: springdoc.org docs — "springdoc only supports @RestController endpoints"]

### Pattern 3: Modulith Documenter Test Method

```java
// Source: docs.spring.io/spring-modulith/reference/documentation.html
// Add to existing QuantLensModulithTest alongside applicationModulesShouldBeValid()

private static final ApplicationModules MODULES =
    ApplicationModules.of(QuantLensApplication.class);

@Test
void applicationModulesShouldBeValid() {
    MODULES.verify();
}

@Test
void writeModuleDiagrams() {
    new Documenter(MODULES)
        .writeModulesAsPlantUml()       // target/spring-modulith-docs/all-modules.puml
        .writeModuleCanvases()          // target/spring-modulith-docs/*.canvas.adoc
        .writeAggregatingDocument();    // target/spring-modulith-docs/all-docs.adoc
}
```

Key facts about `Documenter`:
- Provided by `spring-modulith-docs` (included in `spring-modulith-starter-test` — already on classpath) [VERIFIED: docs.spring.io/spring-modulith/reference/appendix.html]
- Generates PlantUML **source** (`.adoc` + `.puml` files) — **no external Graphviz or PlantUML binary required** at test time [VERIFIED: docs.spring.io/spring-modulith/reference/documentation.html]
- Output directory: `target/spring-modulith-docs/` (Maven) — auto-created by Documenter
- Method is fast (no Spring context load, no DB) — same as `verify()`
- `writeModulesAsPlantUml()` produces C4 component diagram by default; override with `DiagramOptions.defaults().withStyle(DiagramStyle.UML)` if UML preferred

### Pattern 4: OpenAPI Paths Integration Test

```java
// Extend AbstractPostgresIntegrationTest (uses WebEnvironment.RANDOM_PORT + Testcontainers)
@Test
void openApiSpecContainsExpectedPaths() throws Exception {
    ResponseEntity<String> response = restTemplate
        .withBasicAuth("alice", "demo1234")  // or use .getForEntity without auth if permitAll works
        .getForEntity("/v3/api-docs", String.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    String body = response.getBody();
    assertThat(body).contains("/api/portfolio");
    assertThat(body).contains("/api/analytics");
    assertThat(body).contains("/api/ai");
    assertThat(body).doesNotContain("apiKey");   // no secret field leak
    assertThat(body).doesNotContain("llmKey");   // no LLM key in schema
}
```

Note: since `/v3/api-docs` is `permitAll`, `TestRestTemplate` without credentials is correct (no auth header needed).

### Anti-Patterns to Avoid

- **Springdoc BOM assumption:** The Spring Boot parent POM does NOT import a springdoc BOM. Version MUST be pinned explicitly in pom.xml.
- **Per-endpoint @Operation annotations for baseline spec:** Not needed — auto-discovery covers all `@RestController` endpoints. Per-endpoint docs are a v2 enhancement.
- **Adding @Hidden to the MCP component:** Not needed — `@McpTool @Component` (non-`@RestController`) is invisible to springdoc automatically.
- **External Graphviz for Documenter:** Not needed — Documenter emits PlantUML source, not rendered images.
- **Weakening module boundaries for diagram generation:** Never do this. The existing `verify()` already passes; Documenter reads the same module model.
- **CSRF exemption for `/v3/api-docs`:** Not needed — all springdoc endpoints are GET-only.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OpenAPI spec generation | Custom spec writer / reflection code | `springdoc-openapi-starter-webmvc-ui` | Handles generics, Spring MVC parameter binding, validation annotations, security schemes |
| Module diagram generation | Custom dependency graph traversal | `Documenter.writeModulesAsPlantUml()` | Spring Modulith already has the module model in memory after `verify()` |
| Swagger UI hosting | Copied static HTML | springdoc's bundled Swagger UI 5.x | springdoc bundles and serves the UI; no static file management needed |

---

## Common Pitfalls

### Pitfall 1: springdoc version NOT managed by Spring Boot BOM
**What goes wrong:** Developer omits version assuming Boot parent manages it → build fails or resolves stale cached version.
**Why it happens:** Spring Boot parent POM manages hundreds of dependencies, but springdoc is a third-party library maintained independently.
**How to avoid:** Always pin `<version>2.8.17</version>` explicitly in the `<dependency>` block. There is no springdoc BOM imported by this project.
**Warning signs:** Maven prints "Downloading springdoc 2.3.0" (old cached version) or fails with "version required".

### Pitfall 2: Security blocking /v3/api-docs at RANDOM_PORT in integration test
**What goes wrong:** `OpenApiPathsIntegrationTest` gets 401 instead of 200 even though `permitAll` is configured.
**Why it happens:** The `permitAll` block in `SecurityConfig` lists exact strings. If the test hits `/v3/api-docs` (no trailing slash) but the permit only covers `/v3/api-docs/**`, the exact path may not match.
**How to avoid:** Permit both `/v3/api-docs` (exact) AND `/v3/api-docs/**` (wildcards). The test hits the exact path.
**Warning signs:** 401 response in `OpenApiPathsIntegrationTest` with a correctly-booted context.

### Pitfall 3: Documenter test needs Spring context
**What goes wrong:** Developer annotates `writeModuleDiagrams()` test class with `@SpringBootTest` thinking Documenter needs beans — this inflates test time and requires a DB.
**Why it happens:** Misunderstanding — `ApplicationModules.of(Class)` uses classpath scanning, not Spring DI.
**How to avoid:** Keep `QuantLensModulithTest` as a plain JUnit class (no `@SpringBootTest`, no `@ExtendWith(SpringExtension.class)`). Both `verify()` and `Documenter` calls work without a Spring context.
**Warning signs:** Test class is slow (>30s) or requires `@ActiveProfiles("test")`.

### Pitfall 4: stale `figcaption` in StructuredOutputChart.vue
**What goes wrong:** The `<figcaption class="sr-only">` still says "Phase 8 will replace this demo fixture with a live BeanOutputConverter-typed record" — Phase 8 is complete.
**Why it happens:** Forward-looking comment written in Phase 6, not updated at Phase 8 completion.
**How to avoid:** Update BOTH the `<script setup>` header comment AND the `<figcaption>` text. The `<figcaption>` is screen-reader-visible so accuracy matters.

---

## Code Examples

### Full SecurityConfig permit block (updated)
```java
// Source: existing SecurityConfig.java pattern + springdoc.org/faq.html
.requestMatchers(
    "/api/auth/login",
    "/api/auth/logout",
    "/api/auth/personas",
    "/actuator/health",
    "/v3/api-docs",          // exact path
    "/v3/api-docs/**",       // sub-paths (groups etc.)
    "/swagger-ui.html",
    "/swagger-ui/**"
).permitAll()
```

### StructuredOutputChart.vue corrected header comment
```typescript
/**
 * StructuredOutputChart — renders a horizontal bar chart from a StructuredChartDto
 * (title + series[]).
 *
 * Demo mode: DashboardView injects a static fixture via the ai store (ai_seed_content V4).
 * Live mode (Phase 8, AI-06): the same store slot is populated by a live
 * BeanOutputConverter-typed record from /api/ai/structured — same DTO shape, no chart changes.
 *
 * Three states: shimmer skeleton (loading), static error (error), chart (populated).
 */
```
Also update `<figcaption class="sr-only">` to:
```html
<figcaption class="sr-only">
  AI structured output chart showing detected sector exposure.
  In demo mode this uses seeded data; in live mode it is driven by a
  BeanOutputConverter-typed record from the AI backend.
</figcaption>
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `springfox` (Swagger 2 / OpenAPI 2) | `springdoc-openapi` (OpenAPI 3) | 2020-onwards | springfox is abandoned; springdoc is the only maintained option for Spring Boot 3 |
| `@EnableSwagger2` annotation | No annotation required | springdoc v2 | springdoc 2.x auto-configures on classpath detection |
| `PromptChatMemoryAdvisor` | `MessageChatMemoryAdvisor` (already done) | Spring AI 1.1.6 | Already handled in Phase 7; not relevant to Phase 10 |

**Deprecated:**
- `springfox-swagger2` / `springfox-swagger-ui`: abandoned, incompatible with Spring Boot 3. Do NOT use.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | springdoc 2.8.17 release date is April 2026 (GitHub showed "April 11" without year) | Standard Stack | Low risk — the Boot 3.5.13 co-release is confirmed; exact calendar date is cosmetic |

*All other claims were verified via official sources (springdoc GitHub releases, Spring Modulith reference docs).*

---

## Open Questions

1. **Should the generated Modulith docs be committed to `docs/` or remain in `target/` only?**
   - What we know: `target/` is git-ignored; `docs/` would make the diagram browsable on GitHub without a local build.
   - What's unclear: User preference for committing a generated artifact.
   - Recommendation: Write to `target/spring-modulith-docs/` at test time (default, no config needed); add a README link showing how to regenerate with `./mvnw test -pl backend -Dtest=QuantLensModulithTest`; do NOT commit generated files. Clean separation of source vs artifact.

2. **`/swagger-ui/index.html` — needs explicit permit?**
   - What we know: `/swagger-ui/**` covers all sub-paths including `/swagger-ui/index.html`.
   - Recommendation: The wildcard `/**` is sufficient; no explicit `/swagger-ui/index.html` needed.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 (Temurin) | Maven test run | ✓ | 21.0.11 (from CLAUDE.md) | — |
| Maven wrapper (`./mvnw`) | All backend tasks | ✓ | Spring Initializr generated | — |
| Docker | Integration test Testcontainers | ✓ | 28.0.4 (from CLAUDE.md) | — |
| External Graphviz | Modulith diagram rendering | Not needed | — | Documenter emits PlantUML source only |
| External PlantUML binary | Modulith diagram rendering | Not needed | — | Documenter embeds PlantUML library |

**Missing dependencies with no fallback:** None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test + Testcontainers |
| Config file | `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java` |
| Quick run command | `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"; .\mvnw test -pl backend -Dtest=QuantLensModulithTest,OpenApiPathsIntegrationTest` |
| Full suite command | `$env:JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"; .\mvnw test -pl backend` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DOCS-01 | README screenshots scaffolded (placeholders + Capture Guide) | manual / UAT | Human captures images with live key | N/A — human step |
| (infra) | `/v3/api-docs` returns 200 and contains `/api/portfolio`, `/api/analytics`, `/api/ai` | integration | `.\mvnw test -pl backend -Dtest=OpenApiPathsIntegrationTest` | ❌ Wave 0 |
| (infra) | `/v3/api-docs` response does NOT contain `apiKey` or `llmKey` field names | integration | same test class | ❌ Wave 0 |
| (infra) | `ApplicationModules.verify()` passes | unit (fast) | `.\mvnw test -pl backend -Dtest=QuantLensModulithTest` | ✅ exists |
| (infra) | `Documenter` generates diagram without exception | unit (fast) | same test class, second method | ❌ Wave 0 |
| (frontend) | Existing 86-test Vue suite stays green | unit | `cd frontend && npm test` | ✅ exists |

### Sampling Rate

- **Per task commit:** `.\mvnw test -pl backend -Dtest=QuantLensModulithTest,OpenApiPathsIntegrationTest`
- **Per wave merge:** `.\mvnw test -pl backend`
- **Phase gate:** Full backend suite (215 existing + 2 new) green + Vue suite (86) green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/src/test/java/com/quantlens/OpenApiPathsIntegrationTest.java` — covers infra `/v3/api-docs` path assertions and no-secret-leak assertion
- [ ] Second `@Test void writeModuleDiagrams()` method in `QuantLensModulithTest.java` — covers Documenter generation

*(No test framework install needed — JUnit 5 + Testcontainers already present)*

---

## Security Domain

> `security_enforcement` not explicitly disabled — included.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Not introducing new auth surface |
| V3 Session Management | no | No session changes |
| V4 Access Control | yes | `permitAll` on doc endpoints is intentional (public docs for a demo); verify before `.anyRequest().authenticated()` |
| V5 Input Validation | no | No new input surfaces |
| V6 Cryptography | no | No crypto |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| OpenAPI spec leaks internal implementation (secret field names, internal IDs) | Information Disclosure | Assert in `OpenApiPathsIntegrationTest` that `apiKey`, `llmKey`, `password`, `token` do NOT appear as schema property names |
| Swagger UI CSRF (write operations via Swagger UI) | Tampering | All state-changing endpoints remain CSRF-protected; Swagger UI just renders the spec — cannot bypass existing CSRF filter |

**No-secret-leak hygiene:** The existing `KeyLeakageIntegrationTest` covers LLM key leakage. The new `OpenApiPathsIntegrationTest` extends this to the schema level: the OpenAPI JSON must not expose `apiKey`, `llmKey`, or `password` as schema field names (these would expose internal implementation detail). [ASSUMED: specific field names to check — verify against actual controller DTOs]

---

## Sources

### Primary (HIGH confidence)
- [springdoc-openapi v2.8.17 GitHub release](https://github.com/springdoc/springdoc-openapi/releases/tag/v2.8.17) — version confirmed, Spring Boot 3.5.13 co-release confirmed
- [Spring Modulith Reference — Documenting Application Modules](https://docs.spring.io/spring-modulith/reference/documentation.html) — Documenter API, output location, no Graphviz required
- [Spring Modulith Reference — Appendix](https://docs.spring.io/spring-modulith/reference/appendix.html) — `spring-modulith-docs` included in `spring-modulith-starter-test`
- [springdoc.org FAQ](https://springdoc.org/faq.html) — CSRF handling, path requirements, GET-only endpoints

### Secondary (MEDIUM confidence)
- [WebSearch: springdoc 2.8.x Boot 3.5 compatibility] — corroborated by GitHub release data above

---

## Metadata

**Confidence breakdown:**
- Standard stack (springdoc version): HIGH — confirmed by official GitHub release notes co-releasing with Spring Boot 3.5.13
- Architecture (Security permit paths, Documenter API): HIGH — verified against official Spring Modulith reference docs and springdoc FAQ
- Pitfalls: HIGH — derived from code inspection of existing SecurityConfig + confirmed anti-patterns from springdoc docs

**Research date:** 2026-06-10
**Valid until:** 2026-07-10 (30 days — springdoc 2.8.x is stable; Spring Modulith 1.4.x is stable)
