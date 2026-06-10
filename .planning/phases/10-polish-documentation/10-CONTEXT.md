# Phase 10: Polish & Documentation - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Make the repository read as senior-engineer work and close the demo loop. Three deliverables (DOCS-01 + the two infra success criteria):

1. **OpenAPI / springdoc** — `springdoc-openapi-starter-webmvc-ui` so `/v3/api-docs` (+ Swagger UI at `/swagger-ui.html`) documents every portfolio, analytics, and AI endpoint. Permit the doc endpoints through Spring Security.
2. **Spring Modulith living contract** — an `ApplicationModules.verify()` ArchUnit-style test (already exists as `QuantLensModulithTest`; promote it to an explicit, documented module-boundary contract) PLUS a generated module diagram (`Documenter` → PlantUML/C4 + the module canvas) written to a docs location, so the dependency graph (ai → portfolio+analytics+marketdata+seed; analytics → portfolio+marketdata; mcp → portfolio+analytics; auth → portfolio) is a living, verified artifact.
3. **README screenshots + final polish** — README structured for screenshots of the running dashboard (demo mode: all AI panels, fan-chart model selector, RAG Q&A) captured via LIVE mode with a real LLM key; plus a short "how the demo↔live seam works" + "capture guide". Cosmetic cleanups: stale `StructuredOutputChart.vue` "DEMO STUB" comment (the component renders real DTOs now), and any other stale "stub"/TODO comments surfaced.

Requirement covered: DOCS-01 (+ the two non-requirement success criteria: OpenAPI, Modulith contract).
</domain>

<decisions>
## Implementation Decisions

### OpenAPI (springdoc)
- Add `org.springdoc:springdoc-openapi-starter-webmvc-ui` (current 2.x compatible with Spring Boot 3.5 / Spring 6.2). NO version if a BOM manages it; otherwise pin the latest 2.8.x line — research confirms the exact compatible version.
- `OpenAPI` bean with title/description/version + a `securityScheme` note for the session-cookie + HTTP Basic (MCP) auth so the spec is accurate.
- **Security**: permit `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` in `SecurityConfig` (these are public API docs for a demo — additive `.requestMatchers(...).permitAll()` BEFORE `.anyRequest().authenticated()`); they must NOT leak secrets (no key fields in any schema — verify).
- A test asserts `/v3/api-docs` returns 200 and contains the portfolio, analytics, and AI paths.

### Spring Modulith contract + diagram
- Keep `QuantLensModulithTest#applicationModulesShouldBeValid` (the `verify()` gate) as the living contract; add a second test method that runs `Documenter` to render the module diagram + canvas into `backend/target/spring-modulith-docs` (or a committed `docs/` location) — generation must not fail. Reference the diagram from the README/architecture docs.
- Do NOT weaken any module boundary to make generation pass; the graph is already valid (Phase 9 verified). If `Documenter` needs PlantUML, use its built-in component diagram (no external Graphviz dependency at test time).

### README + screenshots + polish
- Restructure/extend README with a clear screenshot section: placeholders (`docs/screenshots/*.png`) + captions for the key demo views, and a short **Capture Guide** (docker compose up → log in as alice → open the BYO-key popup → paste a real LLM key → screenshot each AI panel in live mode). The actual image capture is a **human step** (the user explicitly wants to take these pictures with their own key) — Phase 10 lays down everything around it and marks capture as human-needed UAT.
- Add an Architecture/Docs pointer to: the OpenAPI URL, the Modulith diagram, the stochastic-model rationale doc, and the OAuth upgrade path (already in README).
- Cosmetic: fix the stale `StructuredOutputChart.vue` "DEMO STUB" header comment to describe the real demo↔live behavior; sweep for other stale "stub"/"TODO" comments in the AI frontend/back-end and correct or remove.

### Claude's Discretion
- Exact springdoc version, the OpenAPI bean metadata, where the Modulith diagram is written/committed, and the precise README screenshot layout — at Claude's discretion within the above. Research to confirm the springdoc artifact + version for Spring Boot 3.5.13.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- `QuantLensModulithTest` already runs `ApplicationModules.of(QuantLensApplication.class).verify()` (fast, no context) — extend, don't replace.
- `SecurityConfig.filterChain` with `.authorizeHttpRequests(...).anyRequest().authenticated()` + the actuator/permit pattern — add the springdoc permits the same way.
- Controllers are annotated REST controllers (portfolio, analytics, ai, auth, mcp) — springdoc auto-discovers them; no per-endpoint annotation work required for a baseline spec.
- README already has: Quick Start, Demo Personas, Cold-Start Verification, Stochastic Forecasting Models, Architecture, Dev MCP Servers, Product MCP Server, OAuth Upgrade Path, Production Notes — Phase 10 adds Screenshots + Docs pointers, no rewrite.

### Established Patterns
- Additive `SecurityConfig` permits (actuator health is already permitted); BigDecimal money; golden-tested services; no-secret-leak hygiene (extend to the OpenAPI schema — assert no key fields appear).
- Cosmetic comment cleanups are zero-risk; verify the frontend test suite (86) + backend suite (215) stay green.

### Integration Points
- springdoc endpoints under `/v3/api-docs` + `/swagger-ui` (permit in security). Modulith `Documenter` writes to a docs dir. README references both. No new runtime modules; no DB changes.
</code_context>

<specifics>
## Specific Ideas
- This is the final phase — the repo should look senior: a generated, verified module diagram + a complete OpenAPI spec + a screenshot-rich README are the three "senior-engineer" signals in the goal.
- The live-LLM screenshots are the payoff of the BYO-key popup the user asked for — make the capture path frictionless and clearly documented; the user captures the images.
- Keep every existing test green; add the OpenAPI-paths test + the Modulith diagram-generation test.
</specifics>

<deferred>
## Deferred Ideas
- Auto-committing rendered PNG screenshots (needs the user's live key — human step).
- Per-endpoint rich OpenAPI annotations / examples (baseline auto-spec is sufficient for v1; note the enhancement path).
- Publishing the OpenAPI spec to an external portal / GitHub Pages (v2).
- MCP-03 @McpResource, streaming, additional tools (already deferred in Phase 9).
</deferred>
