---
phase: 10-polish-documentation
reviewed: 2026-06-10T00:00:00Z
depth: standard
files_reviewed: 10
files_reviewed_list:
  - backend/pom.xml
  - backend/src/main/java/com/quantlens/config/OpenApiConfig.java
  - backend/src/main/java/com/quantlens/config/package-info.java
  - backend/src/main/java/com/quantlens/security/config/SecurityConfig.java
  - backend/src/main/java/com/quantlens/ai/api/AiKeyController.java
  - backend/src/test/java/com/quantlens/OpenApiDocsIntegrationTest.java
  - backend/src/test/java/com/quantlens/QuantLensModulithTest.java
  - README.md
  - docs/screenshots/.gitkeep
  - frontend/src/components/ai/StructuredOutputChart.vue
findings:
  critical: 1
  warning: 2
  info: 3
  total: 6
status: resolved
resolution: "6/6 fixed (CR-01 passwordHint @Schema hidden + test, WR-01 mode-neutral badge, WR-02 no global security item, IN-01 diagram file assertion, IN-02 cross-platform regen cmd, IN-03 show-actuator false). Backend + frontend suites green."
---

# Phase 10: Code Review Report

**Reviewed:** 2026-06-10
**Depth:** standard
**Files Reviewed:** 10
**Status:** issues_found

## Summary

Phase 10 adds springdoc OpenAPI documentation, a Spring Modulith living contract, README scaffolding,
and cosmetic Vue cleanup. The security-sensitive surface (public doc endpoints, `@Hidden` on key-intake,
CSRF-exempt scoping) is well-structured and the `permitAll` block is correctly scoped to the four doc
paths only. However there is one critical gap in the stated T-10-01 no-leak guarantee: the no-leak test
misses the `passwordHint` field of `PersonaDto`, which IS published in the live spec. Two warnings cover
a hardcoded "Demo" badge that mislabels the chart in live mode, and a global security requirement in the
OpenAPI bean that misrepresents several public endpoints as requiring auth.

---

## Critical Issues

### CR-01: No-leak test does not check `PersonaDto.passwordHint` — field is present in the published spec

**File:** `backend/src/test/java/com/quantlens/OpenApiDocsIntegrationTest.java:55-61`

**Issue:** `apiDocs_doesNotLeakKeyFields` checks `.doesNotContain("AiKeyRequest")`,
`.doesNotContain("llmKey")`, `.doesNotContain("\"password\"")`, and two key-prefix patterns. However,
`AuthController.GET /api/auth/personas` returns `PersonaDto`, a `@RestController` response type that
springdoc auto-discovers and adds to the `#/components/schemas` section. `PersonaDto` has a field named
`passwordHint`. When springdoc serializes the schema it emits the JSON key `"passwordHint"` — NOT
`"password"` — so the existing `.doesNotContain("\"password\"")` check does NOT catch it. The test
passes today while the live spec contains a field that semantically represents a password value. The
SUMMARY correctly flags this as T-10-01 schema hygiene; the test does not enforce it for this field.

**Fix:** Add `doesNotContain("passwordHint")` to the no-leak assertion chain, and suppress the field
from the spec either by adding `@Schema(hidden = true)` on the `PersonaDto.passwordHint` record
component or by applying `@Hidden` at the `AuthController.personas()` method level. Suppressing the
field from the schema is the correct fix because the hint value is already returned in the response body
at runtime and that runtime contract is intentional — it is only the presence of the word `passwordHint`
in the machine-readable spec that constitutes an unnecessary signal.

```java
// Option A — suppress at the DTO level (preferred: keeps the spec clean regardless of caller)
public record PersonaDto(
        String username,
        String persona,
        @io.swagger.v3.oas.annotations.media.Schema(hidden = true) String passwordHint) {}

// Then extend the test assertion:
assertThat(response.getBody())
        .doesNotContain("AiKeyRequest")
        .doesNotContain("llmKey")
        .doesNotContain("\"password\"")
        .doesNotContain("passwordHint")   // <-- add this
        .doesNotContain("sk-ant-")
        .doesNotContain("sk-proj-");
```

---

## Warnings

### WR-01: `StructuredOutputChart.vue` demo badge is hardcoded — shows "Demo" in live mode too

**File:** `frontend/src/components/ai/StructuredOutputChart.vue:90-93`

**Issue:** The badge block renders unconditionally when there is no loading/error state:

```html
<div v-if="!props.loading && !props.error" class="chart-badge">
  <span class="demo-label">Structured Output · Demo</span>
</div>
```

The label text is a static string "Structured Output · Demo". The component receives `structured`,
`loading`, and `error` props but no `mode` prop, so it cannot differentiate demo from live. When a user
pastes a real key and the mode badge in the header flips to LIVE, this chart panel will continue to say
"Demo" — a direct contradiction visible in the same viewport, and a defect in the screenshot captures
described in the README Capture Guide.

**Fix:** Either pass a `mode` prop down from the parent and condition the label on it, or rename the
label to a mode-neutral string such as "Structured Output" that is accurate in both modes. The simplest
correct fix that requires no prop threading:

```html
<!-- Remove the mode qualifier from the hardcoded string -->
<div v-if="!props.loading && !props.error" class="chart-badge">
  <span class="demo-label">Structured Output</span>
</div>
```

If live-vs-demo distinction in the badge is desired in a future pass, add:
```typescript
const props = defineProps<{
  structured: StructuredChartDto | null
  loading: boolean
  error: string | null
  mode?: 'demo' | 'live'   // optional, defaults to 'demo'
}>()
```
and bind the label text to `props.mode === 'live' ? 'Structured Output · Live' : 'Structured Output · Demo'`.

---

### WR-02: `OpenApiConfig` attaches a global security requirement that misrepresents public endpoints

**File:** `backend/src/main/java/com/quantlens/config/OpenApiConfig.java:45-46`

**Issue:** `.addSecurityItem(new SecurityRequirement().addList("session-cookie"))` applies the
`session-cookie` scheme as a global security requirement to every operation in the spec. This means the
Swagger UI lock icon will show `session-cookie` as required on public endpoints such as
`GET /api/auth/personas`, `POST /api/auth/login`, and `GET /actuator/health` (if ever included). These
endpoints are `permitAll` in `SecurityConfig` and require no authentication. The mislabelling is
incorrect API documentation and will confuse API consumers and the Swagger UI "Authorize" flow — a
consumer following the spec will think they must authenticate before calling the login endpoint.

**Fix:** Remove the global `.addSecurityItem(...)` from `quantLensOpenApi()` and instead apply the
security requirement only to the authenticated operations, either via per-operation `@SecurityRequirement`
annotations on controllers or by adding a springdoc path-filter:

```java
// Remove this line from OpenApiConfig:
// .addSecurityItem(new SecurityRequirement().addList("session-cookie"));

// Instead annotate authenticated controllers:
// @SecurityRequirement(name = "session-cookie")  on PortfolioController, AnalyticsController, etc.
```

Alternatively, configure springdoc to apply the global security requirement only to paths under
`/api/portfolio/**` and `/api/ai/**` using a custom `OperationCustomizer` bean.

---

## Info

### IN-01: `QuantLensModulithTest.generatesModuleDocumentation()` has no assertion on the output file

**File:** `backend/src/test/java/com/quantlens/QuantLensModulithTest.java:35-41`

**Issue:** The test asserts only that `writeModulesAsPlantUml()` does not throw. It does NOT assert that
`target/spring-modulith-docs/components.puml` was actually created or is non-empty. A regression in the
Documenter that silently swallows the write (e.g., a permissions error or a Modulith 1.4.x change to the
output path) would leave the test green while the SC-3 artifact is not produced.

**Fix:** Add a file-existence assertion after the Documenter call:

```java
@Test
void generatesModuleDocumentation() throws Exception {
    new Documenter(MODULES).writeModulesAsPlantUml();

    java.nio.file.Path puml = java.nio.file.Path.of("target/spring-modulith-docs/components.puml");
    assertThat(puml).as("components.puml must be generated by Documenter").exists();
}
```

---

### IN-02: README regen command is Windows-only; no bash equivalent documented

**File:** `README.md:155`

**Issue:** The "Module diagram" regen command is:

```
.\mvnw.cmd test -pl backend -Dtest=QuantLensModulithTest
```

This is Windows PowerShell syntax. A Linux/macOS contributor would need `./mvnw` instead. The rest of
the README uses bash syntax for most commands (e.g., `docker compose`, `curl`). This inconsistency will
confuse non-Windows contributors.

**Fix:** Document both forms or use the cross-platform idiom:

```
# Windows (PowerShell)
.\mvnw.cmd test -pl backend -Dtest=QuantLensModulithTest

# Linux/macOS
./mvnw test -pl backend -Dtest=QuantLensModulithTest
```

---

### IN-03: No `springdoc.show-actuator=false` is explicitly set; suppression relies on a default

**File:** `backend/src/main/resources/application.yml` (no entry present)

**Issue:** springdoc by default does NOT include Spring Boot Actuator endpoints in the spec
(`springdoc.show-actuator` defaults to `false`). The current configuration relies on this default
silently. If `springdoc.show-actuator` is ever set to `true` (e.g., in a dev override profile),
`/actuator/health` (and any future actuator endpoints that are exposed) would appear in the public spec.
There is no test that asserts the actuator is absent from the spec. This is a defense-in-depth gap.

**Fix:** Make the intent explicit in `application.yml`:

```yaml
springdoc:
  show-actuator: false
```

Optionally add a test assertion:
```java
assertThat(response.getBody())
        .doesNotContain("/actuator");
```

---

_Reviewed: 2026-06-10_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

---

## Resolution Log (2026-06-10)

**All 6 findings fixed.** Backend suite re-run green (full suite, EXIT 0); frontend 86/86 green.

| ID | Verdict | Action |
|----|---------|--------|
| **CR-01** | **FIXED** | `@Schema(hidden = true)` on `PersonaDto.passwordHint` (hides from the spec; Jackson still serialises it, login page unaffected) + `.doesNotContain("passwordHint")` added to the no-leak test. |
| **WR-01** | **FIXED** | `StructuredOutputChart.vue` badge changed to mode-neutral "Structured Output" — accurate in both demo and live (fixes the live-mode screenshot contradiction). |
| **WR-02** | **FIXED** | Removed the global `addSecurityItem(session-cookie)` from `OpenApiConfig` — public endpoints (login/personas) are no longer falsely marked as requiring auth. Schemes stay documented in components. |
| **IN-01** | **FIXED** | `generatesModuleDocumentation()` now asserts `target/spring-modulith-docs/components.puml` exists. |
| **IN-02** | **FIXED** | README module-diagram regen command documents both Windows (`.\mvnw.cmd`) and Linux/macOS (`./mvnw`) forms. |
| **IN-03** | **FIXED** | `springdoc.show-actuator: false` made explicit in application.yml + `.doesNotContain("/actuator")` added to the no-leak test. |

**Net:** 6/6 fixed, 0 deferred. Suite green: backend (219+ with the strengthened assertions), frontend 86.
