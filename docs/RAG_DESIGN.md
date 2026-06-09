# RAG Design — QuantLens AI Chat

## Overview

The QuantLens AI chat endpoint (`POST /api/ai/chat`) uses Retrieval-Augmented Generation (RAG)
to answer questions grounded in real SEC 10-K filing excerpts.  The design has two modes —
**demo** (no API key required) and **live** (key present) — that share the same retrieval
infrastructure but differ in how the final answer is generated and how citations are sourced.

---

## Zero-Key Deterministic Embedding Strategy

### The Problem

Demo mode requires the app to work with **no API key and no network access**.  A standard RAG
pipeline needs an embedding model to convert text into vectors both at seed time (storing filing
chunks) and at query time (finding relevant chunks).  Every production embedding model (OpenAI
`text-embedding-3-small`, Anthropic, Cohere, etc.) requires a network call and an API key.

### The Solution: DeterministicHashingEmbeddingModel

A custom `EmbeddingModel` implementation was built that uses **MurmurHash3-based bigram feature
hashing** projected into 1536 dimensions with L2 normalisation.  Key properties:

- **Zero dependencies:** Pure Java, no external libraries.
- **No network, no key:** All computation is local and in-memory.
- **Deterministic:** The same text always produces the same vector.
- **Meaningful similarity:** Documents sharing vocabulary produce closer vectors than dissimilar
  ones.  For a small demo corpus (10–15 chunks), similarity search reliably retrieves
  topically relevant chunks.

The model is registered as `@Primary`, which causes Spring AI's pgvector auto-configuration to
pick it up over the OpenAI embedding model, even when the OpenAI starter is on the classpath.

### Embedding-Space Consistency

The seeded corpus is embedded at application startup by `RagSeedRunner` using
`DeterministicHashingEmbeddingModel`.  At query time, `QuestionAnswerAdvisor` embeds the user's
question with the **same model** before performing the HNSW similarity search in pgvector.
Using the same model for both seed and retrieval ensures the vectors live in the same embedding
space and cosine similarity comparisons are valid.

This same-model invariant holds in **both demo and live mode** — only *answer generation*
switches to the real LLM provider when a key is present.  Retrieval always uses the
deterministic embedding.

---

## Demo Mode vs Live Mode

| Aspect | Demo Mode (no key) | Live Mode (key present) |
|--------|-------------------|-------------------------|
| Entry point | `DemoModeAdvisor` (HIGHEST_PRECEDENCE) short-circuits | Advisor chain proceeds past `DemoModeAdvisor` |
| Retrieval | **None** — DemoModeAdvisor returns answer before QA advisor runs | `QuestionAnswerAdvisor` embeds query → HNSW search → top-4 chunks |
| Answer generation | Authored seed content from `ai_seed_content` (`type=RAG_QA`) returned verbatim | Real LLM call with retrieved chunks injected as context |
| Citation source | **Parsed from authored JSON** in the seed content (see below) | **`RETRIEVED_DOCUMENTS`** from `clientResponse.context()` (A5 resolution) |
| Network calls | Zero | LLM provider + any tool calls |

### Demo Citation Source — Authored JSON

`AiSeedRunner` stores `RAG_QA` rows in `ai_seed_content` as a JSON object:

```json
{
  "answer": "The answer text...",
  "citations": [
    {
      "ticker": "AAPL",
      "section": "Risk Factors",
      "source": "AAPL 10-K FY2023",
      "excerpt": "Apple Inc. faces significant regulatory scrutiny..."
    }
  ]
}
```

`ChatService` detects the absence of `RETRIEVED_DOCUMENTS` (null in demo mode) and falls back
to parsing this JSON with Jackson.  The `citations` array in the JSON references the **actual
seeded chunks** (same `ticker`, `section`, and `source` values as the `RagSeedRunner` corpus),
so demo citations are genuine references — not placeholders.

If JSON parsing fails (e.g. legacy plain-text seed content), `ChatService` treats the whole
text as the answer with an empty `citations` list — a graceful fallback.

### Live Citation Source — RETRIEVED_DOCUMENTS

In live mode, `QuestionAnswerAdvisor.after()` writes retrieved documents into the
`ChatClientResponse` context map under the key `QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS`.
`ChatService` reads them as:

```java
@SuppressWarnings("unchecked")
List<Document> docs = (List<Document>) clientResponse.context()
        .get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
```

Each `Document`'s metadata (`ticker`, `section`, `source`) maps directly to a `CitationDto`
field, and the first 200 characters of the document text become the `excerpt`.

> **Note (A5 resolution):** Documents are in `clientResponse.context()`, **not** in
> `chatResponse().getMetadata()`.  This was confirmed by bytecode analysis of
> `QuestionAnswerAdvisor.after()` in Spring AI 1.1.6.

---

## Idempotent Corpus Seeding

`RagSeedRunner` (`@Order(3)`) seeds 10-K excerpt chunks into the `vector_store` pgvector table
at application startup.  A `seed_log` row with `id="rag-v1"` is written **last** inside a
`@Transactional` method — if the JVM crashes mid-seed the partial work rolls back and the seed
reruns cleanly on the next restart.

Corpus: 12 authored chunks across AAPL, MSFT, NVDA, JPM, XOM (2–3 per ticker).
Sections: Risk Factors, MD&A, Business Overview.  FY2023/FY2024 data.

---

## Stated Limitation

Demo embeddings are generated by a deterministic feature-hashing model for zero-key
cold-start compatibility.  Retrieval uses the same embedding in both demo and live modes;
only answer generation switches to the real LLM provider when a key is present.

**For v2:** Re-embedding the corpus with a production embedding model (e.g. OpenAI
`text-embedding-3-small`) would improve recall quality, especially for queries that rely on
semantic understanding rather than vocabulary overlap.  This would require running the
embedding at authoring time and committing the resulting vectors to a Flyway migration, or
re-seeding at startup when a production embedding key is available.

---

## Conversation Memory

`MessageChatMemoryAdvisor` (order `HIGHEST_PRECEDENCE + 20`) maintains a sliding window of
the last 20 messages per `conversationId`.  The conversation ID defaults to the HTTP session
ID (`HttpSession.getId()`), ensuring memory is scoped to the browser session.  Memory is
in-process only — it is not persisted to the database and resets on server restart.
