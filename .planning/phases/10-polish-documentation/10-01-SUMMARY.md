---
phase: 10-polish-documentation
plan: 01
status: complete
completed: 2026-06-10
requirements: [DOCS-01]
---

# Plan 10-01 Summary — Polish & Documentation (final phase)

## What was built

Three cohesive senior-engineer signals + cosmetic cleanup:

### 1. OpenAPI spec (springdoc) — DOCS-01 / SC-2
- `springdoc-openapi-starter-webmvc-ui:2.8.17` added to backend/pom.xml (EXPLICIT version — springdoc is not BOM-managed; 2.8.17 co-released with Spring Boot 3.5.13).
- `com.quantlens.config.OpenApiConfig` (`@Configuration` + `@Bean OpenAPI quantLensOpenApi`): title "QuantLens API", description, version 1.0.0, two security schemes (`session-cookie` APIKEY-cookie + `http-basic-mcp` HTTP basic).
- New `com.quantlens.config` standalone Modulith module (`package-info.java` `@ApplicationModule`, empty allowedDependencies) — keeps the boundary graph + diagram explicit; `verify()` green.
- `SecurityConfig`: additive `permitAll` for `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` (scoped — no other matcher broadened; T-10-02).
- `@Hidden` on POST + DELETE `/api/ai/key` (`AiKeyController`) — the BYO-key intake surface (`AiKeyRequest.apiKey`) is excluded from the public spec (T-10-01). GET `/api/ai/status` stays documented (no key).
- `OpenApiDocsIntegrationTest` (3 tests): `/v3/api-docs` returns 200 unauthenticated; documents `/api/portfolio`, `/api/portfolio/risk`, `/api/ai/`; leaks no `AiKeyRequest`/`llmKey`/`password`/secret-value.

### 2. Spring Modulith living contract + diagram — SC-3
- `QuantLensModulithTest`: extracted `MODULES` static field; `applicationModulesShouldBeValid()` enforces the boundary graph (ai → portfolio+analytics, analytics → portfolio, mcp → portfolio+analytics, auth + config standalone) on every build; new `generatesModuleDocumentation()` writes the PlantUML component diagram headlessly to `target/spring-modulith-docs/components.puml` (no Graphviz/PlantUML binary). Plain JUnit — no `@SpringBootTest`.

### 3. README screenshots + docs pointers + cosmetic — DOCS-01 / SC-1
- README `## Screenshots` (live-mode capture table for 5 key views) + `### Capture Guide` (docker compose up → login alice → BYO-key popup → paste real key → DEMO→LIVE → screenshot) + `## API & Architecture Docs` (OpenAPI/Swagger URLs, `components.puml` regen command, MODELS.md/RAG_DESIGN.md/OAuth pointers).
- `docs/screenshots/.gitkeep` — committed placeholder for human-captured live images.
- `StructuredOutputChart.vue`: replaced the stale "DEMO STUB" / "Phase 8 will…" header JSDoc + sr-only figcaption with an accurate demo↔live description.

## Verification

- `OpenApiDocsIntegrationTest` 3/3 + `QuantLensModulithTest` 2/2 green (EXIT 0).
- **Full backend suite: 219 tests green** (215 → +4), 0 failures. **Frontend: 86/86 green.**
- `components.puml` confirmed generated in `target/spring-modulith-docs/`.
- README greps: `## Screenshots` =1, `swagger-ui.html` present, `docs/MODELS.md` linked; `DEMO STUB`/`Phase 8` in StructuredOutputChart.vue = 0; `docs/screenshots/.gitkeep` tracked.

## Deviations

1. **No-leak test (apiDocs_doesNotLeakKeyFields):** the plan's literal `doesNotContain("apiKey")` is a FALSE POSITIVE — `apiKey` is an OpenAPI-reserved security-scheme type keyword (`"type":"apiKey"` for the session-cookie scheme) and is also a legitimate request-param name. Replaced with the stronger, true invariant: the key-intake DTO is absent (`doesNotContain("AiKeyRequest")`, achieved via `@Hidden`) plus `llmKey`/`password`/secret-value-pattern checks.
2. **@Hidden on AiKeyController** (not in the plan's files_modified) added to realize deviation #1 — a security-hygiene exclusion consistent with the existing CR-02 key-field hardening, distinct from the "don't add @Operation" guidance.
3. **Documenter chain reduced to `writeModulesAsPlantUml()`** — `writeModuleCanvases()`/`writeAggregatingDocument()` throw `JsonParseException`/NPE parsing `spring-configuration-metadata.json` (known Spring Modulith 1.4.x canvas bug). The PlantUML component diagram IS the SC-3 deliverable and generates independently.
4. **README diagram filename** is `components.puml` (the actual Documenter output), not the plan's assumed `all-modules.puml`.

## Requirements

- **DOCS-01** ✓ — README screenshots scaffolding + Capture Guide (live capture is the human UAT step), OpenAPI spec, and the Modulith living contract + diagram all delivered.

## Next

Phase 10 verify → code-review (+ fix) → 10-HUMAN-UAT (live screenshots) → phase.complete. Then the milestone audit → complete → cleanup (this is the final phase).
