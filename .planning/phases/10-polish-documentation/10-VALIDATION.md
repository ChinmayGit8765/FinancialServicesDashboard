---
phase: 10
slug: polish-documentation
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-10
---

# Phase 10 — Validation Strategy

> OpenAPI spec is live + leak-free; the Modulith graph is a verified, diagram-generating contract; README/docs polish is asserted by file checks (live screenshot capture is human-only).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + Spring Boot Test + Testcontainers (`AbstractPostgresIntegrationTest`) |
| **Frontend** | Vitest + @vue/test-utils |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=OpenApiDocsIntegrationTest,QuantLensModulithTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (frontend) `npm run test` |
| **Estimated runtime** | ~90–150s backend |

---

## Sampling Rate
- **Per task commit:** the relevant OpenAPI / Modulith test
- **Per wave:** `.\mvnw.cmd verify` + `npm run test`
- **Phase gate:** full backend suite green (≥215 + new) + frontend suite green (86); OpenAPI leak-free; Modulith diagram generated

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| DOCS-01 / SC-2 | `GET /v3/api-docs` → 200 and JSON contains `/api/portfolio`, `/api/portfolio/risk`, `/api/ai/` paths | integration | `OpenApiDocsIntegrationTest#apiDocs_exposesPortfolioAnalyticsAiPaths` |
| DOCS-01 / SC-2 | `/v3/api-docs` + `/swagger-ui.html` reachable WITHOUT auth (permitted) | integration | `OpenApiDocsIntegrationTest#apiDocs_isPubliclyAccessible` |
| SC-2 (security) | OpenAPI JSON contains NO `apiKey`/`llmKey`/secret schema field (no key leak) | integration | `OpenApiDocsIntegrationTest#apiDocs_doesNotLeakKeyFields` |
| SC-3 | `ApplicationModules.verify()` passes (living boundary contract) | unit | `QuantLensModulithTest#applicationModulesShouldBeValid` |
| SC-3 | `Documenter` renders module diagram + canvases without error to `target/spring-modulith-docs` | unit | `QuantLensModulithTest#generatesModuleDocumentation` |
| DOCS-01 / SC-1 | README has a Screenshots section + capture guide referencing the BYO-key live path | file | `grep -i "screenshot" README.md`; capture-guide present |
| polish | `StructuredOutputChart.vue` no longer contains "DEMO STUB"; frontend suite green | file+component | `! grep -q "DEMO STUB" StructuredOutputChart.vue`; `npm run test` |

---

## Wave 0 Requirements
- [ ] `springdoc-openapi-starter-webmvc-ui:2.8.17` in pom (explicit version — not BOM-managed); `OpenApiConfig` bean (title/description/version + auth scheme note)
- [ ] SecurityConfig permits `/v3/api-docs`, `/v3/api-docs/**`, `/swagger-ui.html`, `/swagger-ui/**` (additive, before `.anyRequest().authenticated()`)
- [ ] `OpenApiDocsIntegrationTest` (paths present + publicly accessible + no key leak) — RED scaffold
- [ ] `QuantLensModulithTest#generatesModuleDocumentation` (Documenter, headless) — RED scaffold
- [ ] README Screenshots section + capture guide; `docs/screenshots/` placeholder; StructuredOutputChart.vue comment fix
- [ ] Wave 0 verifies open questions at compile (springdoc bean wiring; Documenter API surface)

---

## Manual-Only Verifications
| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| README screenshots captured in LIVE mode with a real LLM key (all AI panels, fan-chart selector, RAG Q&A) | DOCS-01 / SC-1 | needs the user's own LLM key + visual capture | `docker compose up` → log in as alice → BYO-key popup → paste key → screenshot each AI panel → save to `docs/screenshots/` |

---

## Validation Sign-Off
- [x] OpenAPI live + publicly reachable + leak-free (asserted)
- [x] Modulith verify + diagram generation asserted (headless, no external binary)
- [x] README/docs polish asserted by file checks; live capture is human-only (the BYO-key payoff)
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-10 (wave_0_complete flips true after Plan 10-01 executes)
