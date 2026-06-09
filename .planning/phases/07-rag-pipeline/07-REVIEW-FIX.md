---
phase: 07-rag-pipeline
fixed_at: 2026-06-09T18:47:00Z
review_path: .planning/phases/07-rag-pipeline/07-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 11
skipped: 0
status: all_fixed
---

# Phase 7: Code Review Fix Report

**Fixed at:** 2026-06-09T18:47:00Z
**Source review:** .planning/phases/07-rag-pipeline/07-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (6 Critical + 5 Warning; IN-01 also fixed as in-scope per objective)
- Fixed: 11
- Skipped: 0

**Build verification:** Backend `.\mvnw.cmd verify` — 178 tests, 0 failures, 0 errors.
Frontend `npm run test` — 85 tests passed; `npm run build` — success.

---

## Fixed Issues

### CR-01: Math.abs(Integer.MIN_VALUE) overflow — negative bucket index — AIOOBE

**Files modified:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java`, `backend/src/test/java/com/quantlens/ai/DeterministicHashingEmbeddingModelTest.java`
**Commit:** 19a897f
**Applied fix:** Replaced `Math.abs(murmur3(word)) % DIMENSIONS` with `(h & 0x7FFFFFFF) % DIMENSIONS` for both unigram and bigram bucket index calculations. `Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE` (negative in two's-complement), causing AIOOBE; the bitmask approach strips the sign bit cleanly. Added regression tests `embed_neverThrowsAIOOBE_onAdversarialInput` and `embed_bucketIndicesAlwaysInRange_noBucketOutOfBounds`.

---

### CR-02: Missing sign bit in feature hashing — cosine discrimination degraded

**Files modified:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java`, `backend/src/test/java/com/quantlens/ai/DeterministicHashingEmbeddingModelTest.java`
**Commit:** 19a897f (same commit as CR-01/CR-03/WR-01)
**Applied fix:** Applied Weinberger 2009 signed feature hashing: `vector[bucket] += ((h >>> 31) == 0) ? 1.0f : -1.0f` for unigrams and `±0.5f` for bigrams. The MSB of the hash determines increment direction, reducing collision noise and improving cosine discrimination. Added regression test `embed_signedFeatureHashing_similarityOrdering_isCorrect` verifying Apple texts are more similar to each other than to oil/gas texts. Similarity threshold in the existing test lowered from `> 0.3` to `> 0.1` since signed hashing produces smaller dot products for partially-overlapping vectors.

---

### CR-03: Silent NaN vector on null/blank input

**Files modified:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java`, `backend/src/test/java/com/quantlens/ai/DeterministicHashingEmbeddingModelTest.java`
**Commit:** 19a897f (same commit as CR-01/CR-02/WR-01)
**Applied fix:** Added `if (text == null || text.isBlank()) return new float[DIMENSIONS];` guard at the top of `embed()` before any method call on `text`. Widened whitespace regex from `\\s+` to `[\\s\\u00a0]+` to include Unicode non-breaking space. Added `!Double.isFinite(norm)` guard in `l2Normalize()` and `!Float.isFinite(scale)` paranoia guard. Added regression tests: `embed_nullInput_returnsZeroVector_notNPE`, `embed_blankString_returnsZeroVector`, `embed_nonBreakingSpaceOnly_returnsZeroVector`, `embed_noNaNValues_forAnyInput`.

---

### CR-04: Client-controlled conversationId — cross-session memory poisoning (IDOR)

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/AiController.java`, `backend/src/test/java/com/quantlens/ai/AiControllerIntegrationTest.java`
**Commit:** 3237ccc
**Applied fix:** Replaced the conditional `conversationId = (request.conversationId() != null && !request.conversationId().isBlank()) ? request.conversationId() : session.getId()` with the unconditional `String conversationId = session.getId()`. The `ChatRequestDto.conversationId` field is retained for API compatibility but its value is now always ignored by the controller. Added integration regression test `chat_clientSuppliedConversationId_isIgnored_sessionIdUsedInstead` verifying that requests with arbitrary client-supplied conversationIds return 200 (field ignored, no error).

---

### CR-05: Live-mode zero-document response silently routed through parseDemoResponse

**Files modified:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java`, `backend/src/test/java/com/quantlens/ai/ChatServiceLivePathTest.java`
**Commit:** 5856f96
**Applied fix:** Changed the routing branch from `if (docs != null)` to `if (docsObj != null)` using `Object docsObj = context.get(RETRIEVED_DOCUMENTS)` — making the null-vs-empty-list contract explicit. Live responses (RETRIEVED_DOCUMENTS present even as empty list) are returned directly as raw text; only genuinely-null RETRIEVED_DOCUMENTS (QA advisor never ran) goes to parseDemoResponse. This prevents a live LLM answer starting with `{` from being corrupted by the demo JSON parser. Added unit tests in `ChatServiceLivePathTest`: `chat_livePathWithEmptyDocs_doesNotParseDemoResponse` and `chat_demoPathWithNullDocs_parsesDemoResponse`.

---

### CR-06: Non-deterministic document IDs causing duplicate rows on crash-restart

**Files modified:** `backend/src/main/java/com/quantlens/ai/rag/RagSeedContent.java`, `backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java`, `backend/src/test/java/com/quantlens/ai/RagSeedIntegrationTest.java`
**Commit:** ce8766a + bfe3897
**Applied fix:** `RagSeedContent.toDocument()` now assigns a stable deterministic UUID via `UUID.nameUUIDFromBytes(nameKey.getBytes(UTF_8))` where `nameKey = ticker.lowercase + "-" + section.lowercase.replace(" ", "-").replace("&","and")`. The type-3 (name-based) UUID is deterministic (same input → same UUID every JVM start), valid UUID format (required by PgVectorStore — raw strings like `aapl-risk-factors` caused `IllegalArgumentException: Invalid UUID string`), and unique per ticker/section combination. Added `ragSeed_reRunWithSameIds_doesNotDuplicateChunks` to `RagSeedIntegrationTest` verifying stable UUID computation and that re-adding chunks does not exceed the expected AAPL chunk count of 2.

---

### WR-01: MurmurHash3 tail block signed byte — non-standard hash value

**Files modified:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java`
**Commit:** 19a897f (same commit as CR-01/CR-02/CR-03)
**Applied fix:** Added `& 0xff` mask to `data[i+3]` in the 4-byte block loop: `((data[i + 3] & 0xff) << 24)`. Previously the byte was sign-extended to int before shifting, deviating from the standard unsigned-byte interpretation for bytes with high bit set (value >= 0x80). Now all four bytes in the block are consistently masked.

---

### WR-02: MessageWindowChatMemory singleton — unbounded growth on session expiry

**Files modified:** `backend/src/main/java/com/quantlens/ai/chat/ChatMemorySessionListener.java` (new file)
**Commit:** 15731a6
**Applied fix:** Created `ChatMemorySessionListener` — a `@Component` that listens for `HttpSessionDestroyedEvent` and calls `chatMemory.clear(sessionId)`. This evicts the conversation history entry from `MessageWindowChatMemory`'s internal `ConcurrentHashMap` when the HTTP session expires, preventing unbounded heap growth in long-running deployments. Works in tandem with CR-04: now that conversationId == sessionId, the session destroy event precisely targets the right memory entry.

---

### WR-03: parseDemoResponse unchecked cast — ClassCastException on numeric JSON fields

**Files modified:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java`
**Commit:** 5856f96 (same commit as CR-05/WR-04)
**Applied fix:** Changed `objectMapper.readValue(trimmed, Map.class)` to use `TypeReference<Map<String, Object>>`. Changed all citation field access from `c.getOrDefault(...)` to `String.valueOf(c.getOrDefault(...))`. This handles Integer/Long/Boolean values from Jackson's untyped deserialization (e.g., `"year": 2023` becomes `Integer`, not `String`) without ClassCastException. Added `chat_demoResponse_withNumericFieldValues_doesNotThrowClassCastException` in `ChatServiceLivePathTest`.

---

### WR-04: rawText NPE when getResult() returns null

**Files modified:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java`
**Commit:** 5856f96 (same commit as CR-05/WR-03)
**Applied fix:** Replaced chained `clientResponse.chatResponse().getResult().getOutput().getText()` with a null-safe two-step: `var generation = clientResponse.chatResponse().getResult(); String rawText = (generation != null && generation.getOutput() != null) ? generation.getOutput().getText() : "";`. Returns empty string instead of NPE → 502 when the LLM returns no content. Added `chat_nullGeneration_returnsEmptyAnswer_notNPE` test.

---

### WR-05: Frontend sendMessage — orphaned user message on error

**Files modified:** `frontend/src/stores/ai.ts`
**Commit:** 34f37df
**Applied fix:** Added `chatMessages.value.pop()` in the `catch` block of `sendMessage()` to roll back the optimistic user message push on network/API error. Prevents accumulation of orphaned user bubbles (user turns with no assistant reply) in the chat history after repeated failures. The `pop()` is safe because the optimistic push happened just before the try block and the try block only adds an assistant turn on success.

---

### IN-01: RagSeedRunner Javadoc chunk count inaccurate (12 vs actual 10)

**Files modified:** `backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java`
**Commit:** ce8766a (same commit as CR-06)
**Applied fix:** Corrected class Javadoc from "12 authored 10-K-style chunks across AAPL, MSFT, NVDA, JPM, XOM (2-3 chunks per ticker)" to "10 authored 10-K-style chunks across AAPL, MSFT, NVDA, JPM, XOM (2 chunks per ticker)". Corrected `buildChunks()` Javadoc similarly. Added cross-reference to CR-06 stable-ID guarantee. Actual implementation: AAPL×2 + MSFT×2 + NVDA×2 + JPM×2 + XOM×2 = 10.

---

## Skipped Issues

None — all 11 in-scope findings were fixed.

---

_Fixed: 2026-06-09T18:47:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
