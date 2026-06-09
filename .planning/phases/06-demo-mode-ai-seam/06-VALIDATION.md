---
phase: 6
slug: demo-mode-ai-seam
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-09
---

# Phase 6 — Validation Strategy

> Per-phase validation contract. The key-leakage integration test is the security gate.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + Spring Boot Test + Testcontainers (`AbstractPostgresIntegrationTest`) + Mockito |
| **Frontend** | Vitest + @vue/test-utils |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=DemoModeAdvisorTest,LlmKeySessionHolderTest,KeyLeakageIntegrationTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (frontend) `npm run test` |
| **Estimated runtime** | backend ~120s; frontend <15s |

> NO live-provider network calls in tests — demo mode uses seeded content (no key); live paths are unit-tested with mocked/stubbed providers.

---

## Sampling Rate

- **Per task commit:** DemoModeAdvisorTest + the relevant feature test
- **Per wave:** `.\mvnw.cmd verify` (backend) / `npm run test` (frontend)
- **Phase gate:** full backend + frontend green; KeyLeakageIntegrationTest PASS (mandatory)

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| AI-01 | DemoModeAdvisor short-circuits with seeded content when no key (NO chain.nextCall) | unit | `DemoModeAdvisorTest#shortCircuitsInDemoMode` |
| AI-01 | explain + commentary return seeded content with zero provider call | integration | `AiDemoModeIntegrationTest` |
| AI-02 | **key never in any response body or log line** (security gate) | integration | `KeyLeakageIntegrationTest` |
| AI-02 | @SessionScope key isolated across sessions; advisor passes through when key present | integration+unit | `AiKeyControllerTest#sessionIsolation`, `DemoModeAdvisorTest#passesThroughWhenKeyPresent` |
| AI-07 | GET /api/ai/explain/{id} → narrative DTO (seeded) | integration | `AiControllerIntegrationTest#explainReturnsSeededContent` |
| AI-08 | GET /api/ai/commentary → commentary DTO (seeded) | integration | `AiControllerIntegrationTest#commentaryReturnsSeededContent` |
| AI-02 (FE) | BYOKeyModal submit does NOT persist key to localStorage | component | `BYOKeyModal.spec.ts#keyNotInLocalStorage` |
| AI-01 (FE) | AiModeBadge shows Demo without key / Live with key | component | `AiModeBadge.spec.ts` |
| AI-07/08 (FE) | ExplainDrawer opens on holding click + narrative; CommentaryCard renders headline+body | component | `ExplainDrawer.spec.ts`, `CommentaryCard.spec.ts` |

---

## Wave 0 Requirements

Backend (com.quantlens.ai):
- [ ] spring-ai-starter-model-anthropic + -openai added to pom; key-less startup config (sentinel) verified
- [ ] DemoModeAdvisorTest (short-circuit + pass-through), LlmKeySessionHolderTest, KeyLeakageIntegrationTest, AiKeyControllerTest, AiControllerIntegrationTest, AiDemoModeIntegrationTest (RED scaffolds) + AiSeedContent table/repo + seeder stub
Frontend:
- [ ] BYOKeyModal.spec.ts, AiModeBadge.spec.ts, ExplainDrawer.spec.ts, CommentaryCard.spec.ts

---

## Manual-Only Verifications

| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Popup opens, key entry flips mode badge to Live; demo content looks LLM-authored | AI-01/02 | visual + (optional) real key | `docker compose up` → open popup → (demo) seeded panels; (optional live) paste a real key |
| Explain drawer + commentary card render convincingly for screenshots | AI-07/08 | visual | click holding → drawer; dashboard commentary card |

---

## Validation Sign-Off

- [x] Key-leakage integration test is the mandatory security gate
- [x] Demo-mode tests assert NO provider call (no network)
- [x] Wave 0 covers AI starters + all test scaffolds + seed content
- [x] No watch-mode flags
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-09 (wave_0_complete flips true after Plan 06-01 executes)
