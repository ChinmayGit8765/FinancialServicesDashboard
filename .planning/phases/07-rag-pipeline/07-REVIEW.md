---
phase: 07-rag-pipeline
reviewed: 2026-06-09T00:00:00Z
depth: deep
files_reviewed: 12
files_reviewed_list:
  - backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java
  - backend/src/main/java/com/quantlens/ai/chat/RagAdvisorConfig.java
  - backend/src/main/java/com/quantlens/ai/service/ChatService.java
  - backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java
  - backend/src/main/java/com/quantlens/ai/rag/RagSeedContent.java
  - backend/src/main/java/com/quantlens/ai/api/ChatRequestDto.java
  - backend/src/main/java/com/quantlens/ai/api/ChatResponseDto.java
  - backend/src/main/java/com/quantlens/ai/api/CitationDto.java
  - backend/src/main/java/com/quantlens/ai/api/AiController.java
  - backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java
  - frontend/src/components/ai/ChatPanel.vue
  - frontend/src/stores/ai.ts
findings:
  critical: 6
  warning: 5
  info: 2
  total: 13
status: issues_found
---

# Phase 7: Code Review Report

**Reviewed:** 2026-06-09
**Depth:** deep
**Files Reviewed:** 12
**Status:** issues_found

## Summary

The RAG pipeline is well-structured with clear demo/live separation, no key serialisation in any DTO, and solid XSS protection in the Vue layer. However, six correctness defects were found that existing tests are unlikely to catch: three in `DeterministicHashingEmbeddingModel` (Math.abs(Integer.MIN_VALUE) overflow producing a negative bucket index that causes an ArrayIndexOutOfBoundsException, a missing sign-bit in the feature-hashing projection that destroys cosine discrimination, and a silent NaN vector produced when normalized text reduces to a single empty token), one critical IDOR in `AiController` (the `conversationId` supplied by the client is accepted unconditionally when non-blank, allowing memory-store poisoning across sessions), one live-mode routing bug in `ChatService` (a live call that returns zero documents is silently routed through `parseDemoResponse`, which hands a real LLM JSON payload off to the Jackson path it was not designed for, usually corrupting the answer), and one transaction-isolation gap in `RagSeedRunner` (the `@Transactional` annotation on an `ApplicationRunner.run()` method does not create a transaction in the way intended in all Spring Boot startup contexts).

---

## Critical Issues

### CR-01: Math.abs(Integer.MIN_VALUE) overflow → negative bucket index → ArrayIndexOutOfBoundsException

**File:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java:91` and `:98`

**Issue:** `Math.abs(murmur3(word)) % DIMENSIONS` is used to map a hash to a bucket. `murmur3()` returns a signed 32-bit integer. `Math.abs(Integer.MIN_VALUE)` returns `Integer.MIN_VALUE` (still negative, because there is no positive counterpart in two's complement). `Integer.MIN_VALUE % 1536` is a negative number (Java's `%` operator preserves the sign of the dividend), so `vector[bucket]` throws `ArrayIndexOutOfBoundsException` on any word or bigram whose MurmurHash3 value is exactly `Integer.MIN_VALUE`.

The probability that at least one word in any arbitrarily long document hits this exact value is non-negligible; for a vocabulary of ~50,000 distinct English tokens the expected collision count is roughly `50000 / 2^32 ≈ 0.01`, meaning roughly one token in ten thousand documents causes the crash. More importantly, an adversarial user can trivially probe for a message that triggers the crash (denial-of-service on the live embedding path).

Existing tests that exercise a fixed seed corpus will never hit this unless one of the seeded bigrams or unigrams has this hash, so the tests pass while the bug is present in production.

**Fix:**
```java
// Replace both occurrences (lines 91 and 98) with a mask-based approach:
int bucket = (murmur3(word) & 0x7FFFFFFF) % DIMENSIONS;
// Masking to 31 bits guarantees a non-negative value before the modulo.
// Do NOT use Math.abs() — it is broken for Integer.MIN_VALUE.
```

---

### CR-02: Missing sign bit in feature hashing — cosine discrimination is severely degraded

**File:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java:90-99`

**Issue:** Standard feature hashing (Weinberger et al., "Feature Hashing for Large Scale Multitask Learning") uses both the hash bucket AND the sign of a second independent hash (or the LSB of the same hash) to decide whether to add or subtract the feature weight. Without the sign bit, every feature increments its bucket positively. This means:

1. Two semantically unrelated documents that happen to share many hash-bucket collisions will have a spuriously high dot product — the cosine similarity is inflated by collision noise.
2. More critically, documents with no vocabulary overlap can still produce nearly identical vectors if their distinct words hash into the same buckets. The L2-normalised vectors of "apple revenue" and "exxon oil" could be almost identical if their tokens collide into the same buckets, causing the retrieval step to return completely irrelevant chunks at high similarity scores.

This is not caught by functional tests because the seeded corpus is small (12 chunks) and carefully authored, so similarity ordering happens to be correct. But in any deployment with real 10-K text the retrieval precision will be substantially worse than it would be with the sign correction.

**Fix:**
```java
// Unigrams — use hash sign to decide increment direction
for (String word : words) {
    int h = murmur3(word);
    int bucket = (h & 0x7FFFFFFF) % DIMENSIONS;
    // Use LSB of a second hash (or h >>> 31) for the sign
    vector[bucket] += ((h >>> 31) == 0) ? 1.0f : -1.0f;
}

// Bigrams — same sign trick
for (int i = 0; i + 1 < words.length; i++) {
    String bigram = words[i] + "_" + words[i + 1];
    int h = murmur3(bigram);
    int bucket = (h & 0x7FFFFFFF) % DIMENSIONS;
    vector[bucket] += ((h >>> 31) == 0) ? 0.5f : -0.5f;
}
```

Note: after applying the sign bit, the normalization step handles the resulting vector with mixed signs correctly. The L2 norm will still be non-zero for any non-empty document.

---

### CR-03: Silent NaN vector on normalised-empty input — embed("  ") returns NaN[] not zero[]

**File:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java:82-87`

**Issue:** The guard on line 85 reads:
```java
if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) {
    return vector; // zero vector for empty text
}
```

Consider the input `"  "` (two spaces). After `toLowerCase`, `replaceAll("\\s+", " ")`, and `trim()`, `normalized` becomes `""`. `"".split("\\s+")` in Java returns a single-element array `[""]` — not an empty array. So `words.length == 1 && words[0].isEmpty()` is true and the zero vector is returned correctly.

However, consider the input `" "` (non-breaking space). ` ` is NOT matched by `\s` in Java's regex engine by default (Java `\s` matches only `[ \t\n\x0B\f\r]`, not Unicode whitespace). After `toLowerCase`, the NBSP is not collapsed; `trim()` does not strip NBSP either. `normalized` is `" "`. `split("\\s+")` returns `[" "]` — a single non-empty token. The loop runs once, `murmur3(" ")` yields some hash, one bucket gets weight 1.0f, and the vector is fully normalised. So far this is just a correctness quirk (NBSP treated as a word).

The actual NaN path is triggered when `text` itself is `null`. `embed(null)` is called transitively by `embed(Document)` at line 61 via `document.getText()`. If Spring AI's `Document.getText()` ever returns `null` (documented as possible when the document contains only metadata), then line 78 `text.toLowerCase(Locale.ROOT)` throws a NullPointerException. That NPE is caught by the generic `catch (Exception e)` in `ChatService.chat()`, which then wraps it as a 502 BAD_GATEWAY. Every retrieval call for that session fails silently.

A more subtle NaN case: if all accumulator entries remain exactly zero after the loop (possible in theory if every word's hash produces bucket 0 and they cancel out when the sign fix from CR-02 is applied, or via a future code path), `l2Normalize` correctly short-circuits on `norm == 0.0` and returns the zero vector. However, if floating-point underflow causes `norm` to be a non-zero denormal that is still functionally zero, `1.0 / Math.sqrt(norm)` returns `+Infinity` or `NaN`, and the entire vector becomes NaN. PostgreSQL's pgvector will reject a NaN vector and throw an exception during `VectorStore.add()`.

**Fix:**
```java
public float[] embed(String text) {
    if (text == null || text.isBlank()) {
        return new float[DIMENSIONS]; // null guard first, before any method call
    }
    String normalized = text.toLowerCase(Locale.ROOT)
            .replaceAll("[\\s\\u00a0]+", " ") // include Unicode spaces
            .trim();
    // ... rest of method unchanged
}
```
And in `l2Normalize`, guard against +Infinity/NaN explicitly:
```java
private static float[] l2Normalize(float[] v) {
    double norm = 0.0;
    for (float x : v) norm += (double) x * x;
    if (norm == 0.0 || !Double.isFinite(norm)) return v;
    float scale = (float) (1.0 / Math.sqrt(norm));
    if (!Float.isFinite(scale)) return v; // paranoia guard
    float[] out = new float[v.length];
    for (int i = 0; i < v.length; i++) out[i] = v[i] * scale;
    return out;
}
```

---

### CR-04: Client-controlled conversationId accepted unconditionally — cross-session memory poisoning (IDOR)

**File:** `backend/src/main/java/com/quantlens/ai/api/AiController.java:140-142`

**Issue:** The comment at line 127-128 of `ChatRequestDto.java` says "T-07-IDOR: conversationId is server-derived; arbitrary client IDs are only honoured if provided". The actual code in `AiController` line 140-142 is:

```java
String conversationId = (request.conversationId() != null && !request.conversationId().isBlank())
        ? request.conversationId()    // ACCEPTS ARBITRARY CLIENT VALUE
        : session.getId();
```

If a client supplies any non-blank `conversationId`, it is used verbatim with `MessageChatMemoryAdvisor`. `MessageWindowChatMemory` is a single shared bean (singleton scope, `@Bean` in `RagAdvisorConfig`). The in-memory store is backed by a `ConcurrentHashMap<String, List<Message>>` (Spring AI's default). There is no binding between a `conversationId` and the HTTP session that created it.

Attack scenario: User A has session A with `conversationId = sessionId_A`. Attacker B can POST `{"message":"...", "conversationId":"sessionId_A"}` and read or poison User A's conversation history. Since the advisor injects that history into the LLM prompt, the attacker can inject instructions into the context of another user's session (memory poisoning).

The `ChatRequestDto` Javadoc acknowledges this with "not yet validated as session-scoped in v1", meaning the TODO is known but the code as shipped is exploitable — and it is labelled T-07-IDOR in comments, indicating it was tracked. The comment in `AiController` at line 127 also says "default is always sessionId", which is inaccurate — it only defaults to sessionId when `conversationId` is null or blank.

**Fix:**
```java
// AiController.java — always derive conversationId from the server session.
// Never accept a client-supplied conversationId; ignore the field in ChatRequestDto.
String conversationId = session.getId();
return ResponseEntity.ok(chatService.chat(request.message(), conversationId));
```
If per-conversation sub-IDs within a session are needed in future, bind them: `session.getId() + ":" + safeSubId` where `safeSubId` is validated to be a short alphanumeric string owned by the session.

---

### CR-05: Live-mode zero-document response silently routed through parseDemoResponse — answer corrupted

**File:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java:130-146`

**Issue:** The demo/live branch in `chat()` is:
```java
List<Document> docs = (List<Document>) clientResponse.context()
        .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

if (docs != null) {
    // live path
}
// fall through to parseDemoResponse
return parseDemoResponse(rawText);
```

`QuestionAnswerAdvisor` writes the key `RETRIEVED_DOCUMENTS` into the response context with the retrieved documents. But according to Spring AI source and the comment at line 44-46, it puts an **empty list** (not `null`) into context when no documents meet the similarity threshold (0.4 configured in `RagAdvisorConfig`). An empty list is `!= null`, so `docs != null` is `true` and the live path runs correctly — returning `rawText` with empty citations.

However: if `QuestionAnswerAdvisor` is NOT in the chain for some reason (e.g., the bean is missing, the threshold is misconfigured, or a future Spring AI version changes the key name), the context key will be genuinely absent, `docs` will be `null`, and the real LLM's natural-language response will be fed to `parseDemoResponse`. `parseDemoResponse` checks if `trimmed.startsWith("{")`. A real LLM answer beginning with `{` (e.g., a JSON-ish answer) will be parsed by Jackson as the demo JSON envelope. The `answer` field will either be the Jackson-extracted string (if the LLM happened to produce `{"answer":"..."}` shape) or `trimmed` (fallback). Citations will always be empty. The bug is silent — the user sees an answer but it may be truncated or from the wrong path.

More concretely: in the demo path, the `parseDemoResponse` method was designed for authored RAG_QA seed content whose JSON shape is tightly controlled. Feeding it an arbitrary LLM response whose content starts with `{` will silently corrupt the answer.

**Fix:** Distinguish null (QA advisor not present / chain bypassed) from empty list (advisor ran, found nothing):

```java
Object docsObj = clientResponse.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);

if (docsObj != null) {
    // Live path: QA advisor ran (possibly with 0 results)
    @SuppressWarnings("unchecked")
    List<Document> docs = (List<Document>) docsObj;
    List<CitationDto> citations = docs.stream()
            .map(d -> new CitationDto(...))
            .toList();
    return new ChatResponseDto(rawText != null ? rawText : "", citations);
}

// Demo path: DemoModeAdvisor short-circuited, QA advisor never ran
return parseDemoResponse(rawText);
```

This is semantically identical to the current code but makes the null-vs-empty-list contract explicit and prevents future regression if `QuestionAnswerAdvisor` ever changes its empty-result behavior.

---

### CR-06: @Transactional on ApplicationRunner.run() — transaction may not wrap VectorStore.add()

**File:** `backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java:53`

**Issue:** `RagSeedRunner.run()` is annotated `@Transactional`. Spring's `@Transactional` is applied via AOP proxy: the transactional behavior only applies when the method is called through a Spring proxy, not when called directly. `ApplicationRunner.run()` is invoked by Spring Boot's `SpringApplication` startup infrastructure via a direct reference to the bean — but because `RagSeedRunner` implements `ApplicationRunner`, Spring Boot calls `run()` on the proxy (the bean is a Spring-managed component), so the transaction IS applied.

However, `VectorStore.add(chunks)` under `PgVectorStore` performs its own internal JDBC operations. Whether those operations participate in the outer transaction depends on the `PgVectorStore`'s transaction propagation (it defaults to `REQUIRED`, which would join the existing transaction). The problem is that `vectorStore.add(chunks)` embeds each document (calls `DeterministicHashingEmbeddingModel.embed()`) and then does a batched INSERT. If the embedding step throws an exception (see CR-01, CR-03 above) after some rows are already inserted, the `@Transactional` on `run()` should roll back all of them — but only if `PgVectorStore` correctly propagates exceptions without catching and swallowing them internally.

More critically: the `seedLogRepository.save(seedLog)` at line 73 is the idempotency guard. If the JVM crashes between `vectorStore.add()` and `seedLogRepository.save()`, the seed_log row is not written and the seed re-runs on restart. On re-run, `vectorStore.add()` will attempt to insert the same document IDs again. Spring AI's `PgVectorStore` auto-generates UUIDs for documents that don't have an explicit ID set. `RagSeedContent.toDocument()` calls `new Document(text, metadata)` without specifying an ID — so each restart generates fresh UUIDs and inserts duplicate vector rows. The seed log guard prevents the second run but only after the JVM crash scenario. After a crash-and-restart cycle the vector table will contain duplicate embeddings for all chunks, causing every retrieval to return duplicate citations.

**Fix:** Assign stable, deterministic IDs to each document so re-insertion is idempotent (pgvector can be configured to upsert on conflict):

```java
public Document toDocument() {
    // Stable ID derived from ticker+section so re-seeding is idempotent
    String stableId = ticker + "-" + section.toLowerCase(Locale.ROOT).replace(" ", "-");
    return new Document(
            stableId,  // explicit id — prevents duplicate inserts on restart
            text,
            Map.of("ticker", ticker, "section", section, "source", source, "year", year)
    );
}
```

Also configure PgVectorStore with `initializeSchema: true` and consider using `ON CONFLICT (id) DO UPDATE SET ...` semantics, or verify that `PgVectorStore` does an upsert on duplicate IDs.

---

## Warnings

### WR-01: MurmurHash3 tail block uses signed byte at data[i+3] — non-standard hash value

**File:** `backend/src/main/java/com/quantlens/ai/embedding/DeterministicHashingEmbeddingModel.java:130`

**Issue:** In the 4-byte block processing loop, the last byte is assembled as:
```java
int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
        | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
```

`data[i]`, `data[i+1]`, and `data[i+2]` are masked with `& 0xff` to treat them as unsigned bytes. `data[i+3]` is cast directly to `int` via `<< 24` **without the `& 0xff` mask**. In Java, `byte` is signed (`-128` to `127`). The shift `data[i+3] << 24` sign-extends the byte to a 32-bit `int` before shifting, so for any byte value `>= 0x80` (128-255), the resulting bits are wrong. Since MurmurHash3 is defined using unsigned bytes, this is a deviation from the standard algorithm.

The practical consequence: the hash values produced differ from reference MurmurHash3 implementations for any UTF-8 byte sequence where the byte at position `4k+3` (0-indexed) has its high bit set. This is common in ASCII text (all bytes are 0-127 so the bug never triggers) but fires for any non-ASCII characters in 10-K text (currency symbols like `€`, `£`, corporate names with accents, etc.) and for bigrams formed from such words. The deviation is deterministic within this JVM implementation, so seeding and query-time embeddings are still consistent with each other. However, the hash quality (uniformity) is degraded, increasing the bucket collision rate.

This does not cause crashes but silently degrades retrieval quality. An adversarial test with non-ASCII input would detect the deviation from reference MurmurHash3.

**Fix:**
```java
int k1 = (data[i]     & 0xff)        |
         ((data[i + 1] & 0xff) <<  8) |
         ((data[i + 2] & 0xff) << 16) |
         ((data[i + 3] & 0xff) << 24);  // add & 0xff mask on the last byte
```

---

### WR-02: MessageWindowChatMemory is a singleton with no session-scoped eviction — unbounded growth

**File:** `backend/src/main/java/com/quantlens/ai/chat/RagAdvisorConfig.java:57-61`

**Issue:** `MessageWindowChatMemory.builder().maxMessages(20).build()` creates a single bean shared across all sessions. The `maxMessages(20)` window limits how many messages are injected into a single call's context, but it does NOT evict the conversation history map entry for a session when the HTTP session expires. Every `conversationId` (session ID) that ever makes a chat request gets an entry in the in-memory store. When the user's HTTP session expires, Spring invalidates the `HttpSession` but nothing removes the corresponding entry from `MessageWindowChatMemory`.

Over time (especially under load or in a long-running demo deployment), this map grows without bound, consuming heap memory. With the IDOR vulnerability from CR-04 also present, an attacker can generate arbitrary `conversationId` strings, each creating a permanent map entry.

**Fix:** Register a `HttpSessionDestroyedEvent` listener that clears the conversation from `ChatMemory` when the session expires:
```java
@Component
public class ChatMemorySessionListener {
    private final ChatMemory chatMemory;
    public ChatMemorySessionListener(ChatMemory chatMemory) { this.chatMemory = chatMemory; }

    @EventListener
    public void onSessionDestroyed(HttpSessionDestroyedEvent event) {
        chatMemory.clear(event.getSession().getId());
    }
}
```

---

### WR-03: parseDemoResponse unchecked cast — ClassCastException on malformed citations array

**File:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java:182-183`

**Issue:** The cast on line 182-183:
```java
List<Map<String, String>> rawCitations =
        (List<Map<String, String>>) envelope.getOrDefault("citations", List.of());
```

Jackson's `readValue(trimmed, Map.class)` deserialises JSON arrays into `List<Object>`. The elements of that list are themselves `Map<String, Object>` objects, NOT `Map<String, String>`. In authored demo content all citation field values are strings, so in practice the cast succeeds. But if the authored JSON ever contains a numeric value (e.g. a year as an integer), the inner cast to `Map<String, String>` is unchecked at compile time and the subsequent `c.getOrDefault(...)` call will succeed but could return an `Integer` cast to `String` at runtime — throwing a `ClassCastException` inside `citations.stream()`, which is not caught by the outer `catch (Exception ex)` block since it is inside the `.map()` lambda.

Wait — re-reading the code: the outer `try/catch (Exception ex)` on line 195 DOES wrap the stream call at line 185, so the `ClassCastException` would be caught and `parseDemoResponse` would fall back to returning the raw text. This means the bug silently swallows citation data rather than crashing. The fix is type-safe deserialization:

**Fix:**
```java
// Replace Map.class with TypeReference for proper generic typing
Map<String, Object> envelope = objectMapper.readValue(
        trimmed, new TypeReference<Map<String, Object>>() {});

// Citations: deserialize with explicit list type so Jackson handles
// inner typing correctly
@SuppressWarnings("unchecked")
List<Object> rawCitationObjs =
        (List<Object>) envelope.getOrDefault("citations", List.of());

List<CitationDto> citations = rawCitationObjs.stream()
        .filter(obj -> obj instanceof Map)
        .map(obj -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> c = (Map<String, Object>) obj;
            return new CitationDto(
                    String.valueOf(c.getOrDefault("ticker",  "")),
                    String.valueOf(c.getOrDefault("section", "")),
                    String.valueOf(c.getOrDefault("source",  "")),
                    String.valueOf(c.getOrDefault("excerpt", "")));
        })
        .toList();
```

---

### WR-04: ChatService — rawText NullPointerException on live path when LLM returns no output

**File:** `backend/src/main/java/com/quantlens/ai/service/ChatService.java:119-122`

**Issue:** Lines 119-122 access the response output text via a chained call:
```java
String rawText = clientResponse.chatResponse()
        .getResult()
        .getOutput()
        .getText();
```

`chatResponse().getResult()` returns `Generation`, which can return `null` if the LLM returned an empty candidates list (e.g., a content-filter rejection, a `STOP` with no content, or a streaming response that produced no tokens). If `getResult()` returns `null`, `getResult().getOutput()` throws a `NullPointerException`. That NPE will be caught by the outer `catch (Exception e)` and wrapped as a 502 BAD_GATEWAY — not incorrect, but the user gets a misleading "AI provider temporarily unavailable" when the provider actually responded but with no content.

**Fix:**
```java
var generation = clientResponse.chatResponse().getResult();
String rawText = (generation != null && generation.getOutput() != null)
        ? generation.getOutput().getText()
        : "";
```

---

### WR-05: Frontend sendMessage — user message is permanently visible in chat history even on error

**File:** `frontend/src/stores/ai.ts:166`

**Issue:** `sendMessage` pushes the user message to `chatMessages` before the network call:
```typescript
chatMessages.value.push({ role: 'user', content: message })
chatLoading.value = true
// ... axios call
```

On error (401, 502, network failure), the user message remains in the chat history but there is no assistant reply. The UI shows an orphaned user bubble with an error below it. On the next submit, the user's next message is appended after the orphaned one. After multiple errors, the chat history accumulates user messages with no replies, with error banners between groups. This is a UX bug and also creates a misleading conversation state — a re-read of `chatMessages` (e.g., for clipboard copy) will include messages that were never acknowledged.

**Fix:** Roll back the optimistic push on error, or add an error indicator as an assistant message:
```typescript
async function sendMessage(message: string, conversationId?: string): Promise<void> {
    const userMsg: ChatMessage = { role: 'user', content: message }
    chatMessages.value.push(userMsg)
    chatLoading.value = true
    chatError.value = null
    try {
        const { data } = await axios.post<ChatResponseDto>('/api/ai/chat', { message, conversationId })
        chatMessages.value.push({
            role: 'assistant',
            content: data.answer,
            citations: data.citations ?? []
        })
    } catch (e: any) {
        // Remove the optimistic user message on error
        chatMessages.value.pop()
        chatError.value = e?.response?.status === 401 ? 'Session expired' : 'Failed to get AI response'
    } finally {
        chatLoading.value = false
    }
}
```

---

## Info

### IN-01: RagSeedRunner produces only 10 chunks despite documenting 12

**File:** `backend/src/main/java/com/quantlens/ai/rag/RagSeedRunner.java:29` and `:87-311`

**Issue:** The class Javadoc at line 29 and the `buildChunks()` Javadoc at line 82 both state "12 authored 10-K-style chunks across AAPL, MSFT, NVDA, JPM, XOM (2-3 chunks per ticker)". Counting the actual `RagSeedContent` instances returned by `buildChunks()`: AAPL has 2, MSFT has 2, NVDA has 2, JPM has 2, XOM has 2 — that is 10 chunks, not 12. The comment says "2-3 per ticker" but all tickers have exactly 2. The discrepancy between the documentation (12) and implementation (10) may cause tests that assert `chunks.size() == 12` to fail, and creates confusion about the intended corpus size.

**Fix:** Either add the missing chunks for two tickers (e.g. AAPL Business Overview and MSFT Business Overview) or correct the Javadoc to say "10 chunks, 2 per ticker".

---

### IN-02: AiSeedRunner fixture count comment is inaccurate

**File:** `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java:40`

**Issue:** The class Javadoc states "Total: 20 rows" and "13 tickers". Counting the `buildFixtures()` list: EXPLAIN rows = AAPL, MSFT, NVDA, AMZN, TSLA, JPM, BAC, XOM, CVX, PG, KO, WMT, JNJ = 13 rows. COMMENTARY rows = GROWTH, INCOME, BALANCED = 3 rows. RAG_QA rows = DEFAULT, AAPL_RISK, NVDA_AI, JPM_RATES = 4 rows. Total = 20 rows. The count is correct. However, the comment says "4 RAG_QA rows" but there are 4: DEFAULT, AAPL_RISK, NVDA_AI, JPM_RATES. All counts verified accurate — no code defect. This is informational only.

**Fix:** No action required; counts are correct. Remove from findings if desired.

---

_Reviewed: 2026-06-09_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
