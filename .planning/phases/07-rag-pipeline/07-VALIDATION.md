---
phase: 7
slug: rag-pipeline
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-09
---

# Phase 7 — Validation Strategy

> Demo RAG must work offline (deterministic embedding); key-leakage gate stays the security gate.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + Spring Boot Test + Testcontainers pgvector (`AbstractPostgresIntegrationTest`) |
| **Frontend** | Vitest + @vue/test-utils |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=DeterministicHashingEmbeddingModelTest,RagAdvisorConfigTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (frontend) `npm run test` |

---

## Sampling Rate
- **Per task commit:** the embedding unit test + relevant integration test
- **Per wave:** `.\mvnw.cmd verify` / `npm run test`
- **Phase gate:** full backend + frontend green; KeyLeakage gate (now covering /api/ai/chat) green; demo no-network proof green

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| AI-04 | DeterministicHashingEmbeddingModel: dims==1536, deterministic, L2-normalized, similar>dissimilar | unit | `DeterministicHashingEmbeddingModelTest` |
| AI-04 | Seeded 10-K chunks retrievable from pgvector by similarity (rag-v1, idempotent) | integration | `RagSeedIntegrationTest#seededChunks_findable` |
| AI-04 | Citations[] populated from RETRIEVED_DOCUMENTS (mock provider, live) | integration | `RagCitationsIntegrationTest` |
| AI-03 | POST /api/ai/chat returns 200 + answer in demo mode | integration | `ChatDemoModeIntegrationTest` |
| AI-03 | Demo chat: counting advisor nextCall()==0 (no network) | integration | `ChatDemoModeIntegrationTest#demoMode_chat_zeroNetworkCalls` |
| AI-03 | Multi-turn conversation retains memory (mock provider) | integration | `ChatMemoryIntegrationTest` |
| AI-02 gate | key never echoed/logged incl. /api/ai/chat | integration | `KeyLeakageIntegrationTest` (extended) |
| Frontend | ChatPanel: send → message + citations + loading | component | `npm run test -- ChatPanel` |

---

## Wave 0 Requirements
- [ ] spring-ai-starter-vector-store-pgvector in pom; PgVectorStore bean over existing vector_store (initialize-schema=false); DeterministicHashingEmbeddingModel (@Primary) + RagAdvisorConfig (QuestionAnswerAdvisor + MessageChatMemoryAdvisor beans)
- [ ] DeterministicHashingEmbeddingModelTest, RagAdvisorConfigTest, ChatDemoModeIntegrationTest, RagSeedIntegrationTest, ChatMemoryIntegrationTest, RagCitationsIntegrationTest (RED scaffolds)
- [ ] RAG seeder (seed_log rag-v1) for 3–5 10-K excerpts; ChatPanel.spec.ts; extend KeyLeakageIntegrationTest with /api/ai/chat
- [ ] Wave 0 verifies research assumptions A1-A8 at compile (Embedding ctor, EmbeddingRequest getter, @Primary EmbeddingModel resolution, QuestionAnswerAdvisor is CallAdvisor, RETRIEVED_DOCUMENTS access)

---

## Manual-Only Verifications
| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Chat panel Q&A with citations renders; multi-turn memory; demo answers cite seeded 10-Ks | AI-03/04 | visual | `docker compose up` → ask "What does Apple say about AI risk?" → answer + citation chips |

---

## Validation Sign-Off
- [x] Demo RAG offline via deterministic embedding (no key/network)
- [x] Key-leakage gate extended to /api/ai/chat
- [x] Demo no-network proof (counting advisor) reused
- [x] Wave 0 covers pgvector store + embedding + advisors + all test scaffolds
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-09 (wave_0_complete flips true after Plan 07-01 executes)
