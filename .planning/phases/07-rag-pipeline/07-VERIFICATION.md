---
phase: 07-rag-pipeline
verified: 2026-06-09T00:00:00Z
status: human_needed
score: 8/10 must-haves verified
overrides_applied: 0
re_verification: false
human_verification:
  - test: "Demo Q&A end-to-end: log in as Alice, ask 'What does Apple say about AI risk in their 10-K?', confirm AI answer appears with citation chip (e.g. AAPL · Risk Factors) and excerpt tooltip; confirm mode badge reads 'demo'"
    expected: "Chat panel shows user message, then assistant message with at least one citation chip; clicking/hovering chip shows excerpt; zero network calls to LLM provider; AI mode badge = demo"
    why_human: "Cannot verify DOM rendering, citation chip hover state, or visual layout programmatically; requires running docker compose + browser"
  - test: "Multi-turn conversation memory: after first Q&A turn, ask a follow-up ('What did I ask about in my previous question?') in the same session; confirm the response references content from the first turn"
    expected: "Second assistant reply demonstrates awareness of the prior turn — e.g. references AAPL, risk factors, or the substance of the first message; this proves MessageChatMemoryAdvisor actually injected prior context"
    why_human: "ChatMemoryIntegrationTest only asserts turn2.getBody() != null (not that prior-turn content was injected into the second request). The test accepts 200 or 502, meaning DemoModeAdvisor short-circuits the memory advisor in demo mode. Actual memory retention requires a live session with a session key set (live mode) or a mock ChatModel that records received messages — neither is wired in the current test suite. Cannot verify memory injection without running the app."
  - test: "Live mode citation path: enter a real BYO key (Anthropic or OpenAI), ask 'What are NVIDIA's AI infrastructure risks?', confirm citations[] in the response come from RETRIEVED_DOCUMENTS (not the authored RAG_QA seed) — e.g. the excerpt text matches actual seeded chunk text rather than the authored demo JSON"
    expected: "With a real key, QuestionAnswerAdvisor runs, retrieves top-4 chunks from pgvector, and the citations in the response contain excerpt text from the seeded corpus (not the authored JSON content); the answer is LLM-generated (not verbatim seed content)"
    why_human: "RagCitationsIntegrationTest only asserts 200 or 502 with a fake key; when the fake key produces 502 the citations assertion is skipped entirely. Live path requires a real LLM key to exercise the full RETRIEVED_DOCUMENTS path end-to-end."
---

# Phase 7: RAG Pipeline Verification Report

**Phase Goal:** Users can ask freeform natural-language questions about the portfolio (with conversation memory) and get answers from embedded SEC 10-K filings via QuestionAnswerAdvisor wired to pgvector — all working in demo mode via pre-seeded embeddings with no key required.
**Verified:** 2026-06-09
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths (Roadmap Success Criteria)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| SC-1 | User can ask freeform NL questions in a chat interface and receive contextually accurate answers; chat retains conversation memory across turns | ? UNCERTAIN | Chat panel exists and POST /api/ai/chat works in demo; memory advisor is wired (MessageChatMemoryAdvisor @HP+20); but ChatMemoryIntegrationTest only proves the endpoint responds twice (isIn(OK, BAD_GATEWAY) + body != null), not that prior-turn content was injected — human verification required |
| SC-2 | User can ask questions answered from seeded 10-K filings and receive answers with citations; works in demo mode with no API key | VERIFIED | ChatDemoModeIntegrationTest: 200 + non-blank answer + citations[] with AAPL/NVDA/JPM/XOM ticker + NEXT_CALL_COUNT==0; RagSeedIntegrationTest: AAPL chunk retrievable + rag-v1 idempotency proven; authored RAG_QA JSON with genuine citations parsed by ChatService |
| SC-3 | Entering a BYO key switches answer generation to live LLM while using the same vector retrieval path | ? UNCERTAIN | Architecture is correct: DemoModeAdvisor at HP, QuestionAnswerAdvisor at HP+10 — both share the same DeterministicHashingEmbeddingModel for retrieval; ChatService branches on RETRIEVED_DOCUMENTS presence (not keyHolder); RagCitationsIntegrationTest accepts 200-or-502 and skips citations check on 502; live path requires human verification with a real key |

**Score:** 1 VERIFIED + 2 UNCERTAIN = automated verification 8/10 on plan must-haves; SC-2 fully VERIFIED; SC-1 and SC-3 need human confirmation

---

### Plan Must-Haves Verification

#### 07-01 Must-Haves

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Backend compiles with pgvector starter + PgVectorStore bean wired over vector_store (initialize-schema=false) | VERIFIED | `pom.xml`: `spring-ai-starter-vector-store-pgvector` + `spring-ai-advisors-vector-store` present; `application.yml` initialize-schema: false confirmed unchanged; all 7 commits show BUILD SUCCESS |
| 2 | DeterministicHashingEmbeddingModel is @Primary EmbeddingModel: 1536 dims, deterministic, L2-normalized, similar texts score higher | VERIFIED | File exists at `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java`; `@Component @Primary`, `DIMENSIONS=1536`, MurmurHash3 bigrams, `l2Normalize()` present; DeterministicHashingEmbeddingModelTest 7/7 GREEN per SUMMARY |
| 3 | RagAdvisorConfig registers QuestionAnswerAdvisor + MessageChatMemoryAdvisor + ChatMemory beans; Spring context loads with all three | VERIFIED | `RagAdvisorConfig.java`: all three `@Bean` methods present; `MessageWindowChatMemory.builder().maxMessages(20)` at HP+20; `QuestionAnswerAdvisor.builder(vectorStore)` at HP+10; RagAdvisorConfigTest GREEN per SUMMARY |
| 4 | POST /api/ai/chat endpoint exists, is reachable (authenticated), and returns ChatResponseDto shape | VERIFIED | `AiController.java` line 134: `@PostMapping("/chat")`; `ChatRequestDto` (@NotBlank @Size(max=2000)), `ChatResponseDto(answer, citations[])`; CSRF exemption added; KeyLeakageIntegrationTest step 5 GREEN (200 or 502, no key leak) |
| 5 | Research assumptions A1-A8 resolved at compile and recorded in 07-01-SUMMARY | VERIFIED | A1-A8 table in 07-01-SUMMARY.md; A5 resolved as `clientResponse.context()` not `chatResponse().getMetadata()`; A7 confirmed auto-config; A8 confirmed via BaseAdvisor extends CallAdvisor |
| 6 | All Phase 7 RED test scaffolds exist and collected by runner; KeyLeakageIntegrationTest extended to /api/ai/chat | VERIFIED | All 6 test files exist (ChatDemoModeIntegrationTest, RagSeedIntegrationTest, ChatMemoryIntegrationTest, RagCitationsIntegrationTest, DeterministicHashingEmbeddingModelTest, RagAdvisorConfigTest); KeyLeakageIntegrationTest step 5 at line 130-145 confirmed in file |

#### 07-02 Must-Haves

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | POST /api/ai/chat returns 200 with seeded non-blank answer AND citations in demo mode with NO key and NO network (nextCall()==0) | VERIFIED | ChatDemoModeIntegrationTest.java: assertions at lines 70-91 confirmed in file; SUMMARY reports GREEN; NEXT_CALL_COUNT.isZero() is the executable no-network proof |
| 2 | Seeded 10-K chunks retrievable from pgvector by similarity (rag-v1, idempotent) using deterministic embedding | VERIFIED | RagSeedIntegrationTest.java: `similaritySearch("Apple regulatory risk App Store", topK=4, threshold=0.1)` asserts AAPL ticker; `ragSeed_isIdempotent` checks seed_log "rag-v1" completed=true; 10 chunks across AAPL/MSFT/NVDA/JPM/XOM confirmed in RagSeedRunner.buildChunks() |
| 3 | Live mode (mock provider): citations[] from RETRIEVED_DOCUMENTS; multi-turn memory retained across turns | UNCERTAIN | RagCitationsIntegrationTest accepts 200-or-502 and skips AAPL citation check on 502 (fake key always causes 502); ChatMemoryIntegrationTest only checks body != null, not actual memory injection — these pass because the assertions are weak, not because the behavior is proven |
| 4 | Full backend test suite is green | VERIFIED | 07-02-SUMMARY: 165 tests, 0 failures, 0 errors, 2 pre-existing skips; all named tests GREEN |

#### 07-03 Must-Haves

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Dashboard shows Q&A chat panel: user types question, presses Send, sees message + AI answer with citation chips | ? UNCERTAIN | ChatPanel.vue exists, DashboardView wired at line 274; automated build green; but actual rendering requires human verification |
| 2 | Chat panel shows empty/loading/error states and disables input while loading | VERIFIED | ChatPanel.vue: `v-if="props.messages.length === 0 && !props.loading"` empty state (line 56); `role="alert"` error block (line 87); `:disabled="props.loading"` on input (line 97) and send-btn (line 103); ChatPanel.spec.ts test 3 asserts disabled attribute |
| 3 | ChatPanel.spec.ts proves send-emits, message+citation rendering, loading-disabled behavior | VERIFIED | `frontend/src/components/ai/__tests__/ChatPanel.spec.ts` exists; 3 tests confirmed: emit send with trimmed text, renders user+assistant with .citation-chip containing ticker, loading disables both input and button; 85/85 frontend tests GREEN per SUMMARY |
| 4 | vue-tsc production build and frontend test suite green | VERIFIED | 07-03-SUMMARY: `npm run build` exit 0; 85/85 tests green |

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java` | Zero-key @Primary EmbeddingModel, 1536-dim | VERIFIED | 160 lines; implements EmbeddingModel; @Component @Primary; murmur3 + l2Normalize present |
| `backend/src/main/java/com/quantlens/ai/chat/RagAdvisorConfig.java` | QuestionAnswerAdvisor + MessageChatMemoryAdvisor + ChatMemory | VERIFIED | 91 lines; all 3 @Bean methods; correct order constants |
| `backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java` | Idempotent rag-v1 corpus seeder @Order(3) | VERIFIED | 314 lines; @Order(3); RAG_SEED_VERSION="rag-v1"; idempotency guard present; 10 chunks (AAPL x2, MSFT x2, NVDA x2, JPM x2, XOM x2) in buildChunks(); vectorStore.add(chunks) wired |
| `backend/src/main/java/com/quantlens/ai/service/ChatService.java` | ChatResponseDto chat() through advisor chain | VERIFIED | 201 lines; strategy.forSession(keyHolder).prompt()...advisors(RAG_QA/DEFAULT/CONVERSATION_ID)...chatClientResponse(); RETRIEVED_DOCUMENTS branch + parseDemoResponse(); 502 wrap present; no if(demoMode) |
| `backend/src/main/java/com/quantlens/ai/api/AiController.java` | POST /api/ai/chat returns ChatResponseDto | VERIFIED | @PostMapping("/chat") at line 134; conversationId fallback to session.getId(); @Valid ChatRequestDto |
| `backend/src/test/java/com/quantlens/ai/DeterministicHashingEmbeddingModelTest.java` | Embedding unit test (dims/determinism/L2/similarity) | VERIFIED | File exists; SUMMARY 7/7 GREEN |
| `backend/src/test/java/com/quantlens/ai/ChatDemoModeIntegrationTest.java` | Demo mode + zero-network proof | VERIFIED | File exists; CountingCallAdvisor inner class; assertions for 200, non-blank answer, citations, NEXT_CALL_COUNT==0 confirmed in file |
| `docs/RAG_DESIGN.md` | Deterministic embedding strategy + demo/live citation sourcing + limitation | VERIFIED | File exists; documents zero-key strategy, embedding-space consistency, demo vs live table, authored JSON citation source, RETRIEVED_DOCUMENTS live path (A5), idempotent seeding, memory scoping, v2 limitation |
| `frontend/src/components/ai/ChatPanel.vue` | Message list + input + citation chips, dark theme | VERIFIED | 298 lines; citation-chip class present; CSS uses design tokens only (var(--color-*) etc.); no v-html; 3 states implemented |
| `frontend/src/stores/ai.ts` | chatMessages/chatLoading/chatError + sendMessage action | VERIFIED | sendMessage action at line 165; pushes user turn + assistant turn; axios.post('/api/ai/chat'); $reset() clears chat state at line 199 |
| `frontend/src/components/ai/__tests__/ChatPanel.spec.ts` | Component test for send/render/loading | VERIFIED | 44 lines; 3 tests; emitted('send') assertion confirmed |
| `frontend/src/views/DashboardView.vue` | ChatPanel in AI Q&A slot | VERIFIED | Line 274: `<ChatPanel :messages="aiStore.chatMessages" :loading="aiStore.chatLoading" :error="aiStore.chatError" @send="aiStore.sendMessage($event)"` confirmed |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `backend/pom.xml` | PgVectorStore auto-config | spring-ai-starter-vector-store-pgvector dependency | VERIFIED | Dependency confirmed in SUMMARY; spring-ai-advisors-vector-store also added (QuestionAnswerAdvisor not transitive) |
| `RagAdvisorConfig.java` | ChatClientStrategy advisor chain | @Bean pickup via List<CallAdvisor> injection | VERIFIED | QuestionAnswerAdvisor implements BaseAdvisor extends CallAdvisor (A8); MessageChatMemoryAdvisor also CallAdvisor; both @Bean in @Configuration class |
| `AiController.java` | ChatService.chat() | @PostMapping("/chat") | VERIFIED | Line 134 @PostMapping("/chat"); chatService.chat(request.message(), conversationId) at line 143 |
| `RagSeedRunner.java` | vector_store table | vectorStore.add(chunks) via DeterministicHashingEmbeddingModel | VERIFIED | vectorStore.add(chunks) at line 66; @Primary embedding model resolves at auto-config |
| `AiSeedRunner.java` | ai_seed_content RAG_QA rows | buildFixtures() RAG_QA entries with authored JSON | VERIFIED | 4 RAG_QA rows (DEFAULT, AAPL_RISK, NVDA_AI, JPM_RATES) confirmed in AiSeedRunner.java; all contain {"answer":...,"citations":[...]} JSON |
| `ChatService.java` | CitationDto list | demo: parseDemoResponse() JSON; live: RETRIEVED_DOCUMENTS | VERIFIED | parseDemoResponse() at line 166; RETRIEVED_DOCUMENTS branch at line 130; no keyHolder branch |
| `frontend/src/stores/ai.ts` | POST /api/ai/chat | sendMessage axios.post('/api/ai/chat') | VERIFIED | Line 170: `axios.post<ChatResponseDto>('/api/ai/chat', { message, conversationId })` |
| `DashboardView.vue` | ChatPanel | AI Q&A slot replaces SlotPlaceholder | VERIFIED | Line 274: `<ChatPanel` with all 4 bindings; StructuredOutputChart retained above it |
| `ChatPanel.vue` | aiStore.sendMessage | @send event handler | VERIFIED | Line 278: `@send="aiStore.sendMessage($event)"` confirmed in DashboardView |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|-------------------|--------|
| `ChatPanel.vue` | `props.messages` | `aiStore.chatMessages` (ref<ChatMessage[]>) | Yes — sendMessage pushes user + assistant turns; assistant turn populated from POST /api/ai/chat response | FLOWING |
| `ChatService.java` | `docs` (RETRIEVED_DOCUMENTS) | `clientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS)` | Yes in live mode; null in demo mode (intentional — parseDemoResponse handles null case) | FLOWING (branched) |
| `RagSeedRunner.java` | chunks | `buildChunks()` | Yes — 10 authored Document objects with metadata (ticker/section/source/year); vectorStore.add() embeds via DeterministicHashingEmbeddingModel | FLOWING |
| `AiSeedRunner.java` | RAG_QA content | `buildFixtures()` RAG_QA entries | Yes — 4 rows with authored JSON answer+citations content seeded into ai_seed_content | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — no runnable entry points without docker compose. Backend requires live Postgres (AbstractPostgresIntegrationTest uses Testcontainers), which cannot be started in this context.

---

### Probe Execution

No probe scripts declared in PLAN files. No `scripts/*/tests/probe-*.sh` files found.

---

### Requirements Coverage

| Requirement | Source Plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| AI-03 | 07-01, 07-02, 07-03 | User can ask freeform NL questions about the portfolio in a chat that retains conversation memory | PARTIALLY SATISFIED | Chat endpoint, ChatPanel, sendMessage, memory advisor all wired; demo mode proven offline; memory retention not proven programmatically (ChatMemoryIntegrationTest assertion is weak: body != null only) |
| AI-04 | 07-01, 07-02, 07-03 | User can ask questions answered from embedded 10-K/earnings filings via RAG over pgvector | SATISFIED | 10 chunks seeded into pgvector; AAPL chunk retrievable by similarity (RagSeedIntegrationTest GREEN); demo citations from authored JSON; live path architecturally correct; ChatDemoModeIntegrationTest proves zero-network demo with citations |

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `ChatMemoryIntegrationTest.java` | Weak assertion: `isNotNull()` instead of verifying prior-turn content was injected | WARNING | Memory retention (AI-03) is not programmatically proven; accepted in 07-02-SUMMARY as planned scope limitation pending a mock ChatModel |
| `RagCitationsIntegrationTest.java` | Conditional assertion: citations check is skipped when status is 502 (which it always is with a fake key) | WARNING | Live-mode RETRIEVED_DOCUMENTS citation path is not programmatically proven; accepted in 07-02-SUMMARY as planned scope limitation |

No TBD/FIXME/XXX markers found in any phase 7 modified files. No placeholder/v-html anti-patterns in ChatPanel.vue.

---

### Human Verification Required

#### 1. Demo Q&A — Visual + Citation Chip Rendering

**Test:** From `docker compose up`, log in as Alice (alice / demo1234), scroll to "AI Q&A" panel (col-8, below StructuredOutputChart). Type "What does Apple say about AI risk in their 10-K?" and press Enter.
**Expected:** User message appears in the panel; AI answer appears with at least one citation chip showing "AAPL · Risk Factors" (or similar); hovering the chip reveals the excerpt tooltip. AI mode badge reads "demo". No network call fires to any LLM provider (demo mode uses seeded content).
**Why human:** DOM rendering, citation chip visual appearance, tooltip hover behavior, and mode badge cannot be verified programmatically. This is the core user-facing proof of SC-2.

#### 2. Multi-Turn Conversation Memory

**Test:** After the first Q&A turn above, without refreshing the page, type a follow-up: "What did I ask about in my previous question?" Press Enter.
**Expected:** The AI answer in the second turn references the subject of the first turn (e.g. mentions Apple, App Store risks, or AAPL) — proving the MessageChatMemoryAdvisor injected the first turn into the second request's context. Prior turn should remain visible in the panel.
**Why human:** `ChatMemoryIntegrationTest` only asserts `turn2.getBody() != null` and accepts 200 or 502. In demo mode, `DemoModeAdvisor` (order HIGHEST_PRECEDENCE) short-circuits before `MessageChatMemoryAdvisor` (order HP+20) can inject memory. Memory works in live mode (session key set). Human must verify with a live session that memory is actually retained — specifically that the second turn's LLM call carries the prior context.

#### 3. Live Mode Citation Path (BYO Key)

**Test:** Enter a real Anthropic or OpenAI key via the BYO-key popup. Then ask "What are NVIDIA's AI infrastructure risks?" in the chat panel.
**Expected:** Answer is LLM-generated (not verbatim seed content); citations[] contain excerpt text matching the seeded NVDA chunks (from `RagSeedRunner`); mode badge reads "live". This proves the RETRIEVED_DOCUMENTS path in ChatService (SC-3: same retrieval path, switched generation).
**Why human:** `RagCitationsIntegrationTest` uses a fake key that always causes 502, so the citations assertion is skipped. The live RETRIEVED_DOCUMENTS path from `clientResponse.context()` (A5 resolution) requires a real LLM key to exercise end-to-end. Cannot verify without a real API key.

---

### Gaps Summary

No hard FAIL gaps found. The two WARNINGs (ChatMemoryIntegrationTest and RagCitationsIntegrationTest) are weak-assertion tests that pass but do not prove the behavior — these are documented deviations in 07-02-SUMMARY accepted as scope limitations. The phase goal is substantially achieved but requires human sign-off on the three items above (particularly memory retention and live citation path) before SC-1 and SC-3 can be fully closed.

---

_Verified: 2026-06-09_
_Verifier: Claude (gsd-verifier)_
