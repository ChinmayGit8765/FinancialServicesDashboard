package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.ai.rag.RagSeedContent;
import com.quantlens.seed.SeedLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying that the RAG corpus is seeded into {@code vector_store}
 * and retrievable by similarity search using {@link com.quantlens.ai.embedding.DeterministicHashingEmbeddingModel}.
 *
 * <h3>RED status in 07-01</h3>
 * Both tests are RED in 07-01 because {@code buildChunks()} returns an empty list and the
 * {@code rag-v1} seed_log row is not written. Tests become GREEN in 07-02 when the corpus
 * authoring is added and {@code RagSeedRunner} writes chunks + the completion marker.
 */
class RagSeedIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private SeedLogRepository seedLogRepository;

    /**
     * RED in 07-01 (empty corpus) → GREEN in 07-02 (seeded corpus).
     *
     * <p>Queries pgvector for Apple regulatory / App Store text using the deterministic
     * embedding model and asserts at least one AAPL-metadata chunk is returned.
     */
    @Test
    void seededChunks_findable_withDeterministicEmbedding() {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Apple regulatory risk App Store")
                        .topK(4)
                        .similarityThreshold(0.1)   // low threshold — deterministic model
                        .build());

        assertThat(results)
                .as("Seeded corpus must return at least one result for Apple regulatory query")
                .isNotEmpty();
        assertThat(results)
                .as("At least one result must have ticker=AAPL metadata")
                .anyMatch(d -> "AAPL".equals(d.getMetadata().get("ticker")));
    }

    /**
     * RED in 07-01 (rag-v1 not completed) → GREEN in 07-02 (seed writes completion marker).
     *
     * <p>Asserts the {@code seed_log} row {@code rag-v1} is marked completed, proving
     * the idempotency guard ran the seed and wrote its completion marker exactly once.
     */
    @Test
    void ragSeed_isIdempotent() {
        boolean completed = seedLogRepository.findById("rag-v1")
                .map(com.quantlens.seed.SeedLog::isCompleted)
                .orElse(false);

        assertThat(completed)
                .as("seed_log rag-v1 must be marked completed after RagSeedRunner runs")
                .isTrue();
    }

    /**
     * CR-06 regression: re-running {@code VectorStore.add()} with the same chunk IDs must
     * NOT produce duplicate rows. With stable deterministic IDs from {@link RagSeedContent#toDocument()},
     * a second add() of the same document is idempotent (pgvector upserts on conflict or
     * the existing row is unchanged).
     *
     * <p>This test adds the same chunks a second time and asserts the total result count
     * for an AAPL query does not exceed the expected per-ticker chunk count (2 for AAPL).
     * Duplicate rows would return the same document twice, inflating the result count
     * above the expected ceiling.
     */
    @Test
    void ragSeed_reRunWithSameIds_doesNotDuplicateChunks() {
        // Build the same AAPL chunks that RagSeedRunner seeded
        List<Document> aaplChunks = List.of(
            new RagSeedContent(
                "Apple Inc. faces significant regulatory scrutiny regarding its App Store policies...",
                "AAPL", "Risk Factors", "AAPL 10-K FY2023", "2023"
            ).toDocument(),
            new RagSeedContent(
                "Apple's Services segment delivered net revenue of approximately 85.2 billion...",
                "AAPL", "MD&A", "AAPL 10-K FY2023", "2023"
            ).toDocument()
        );

        // Both documents have the same stable IDs as what was already seeded by RagSeedRunner
        assertThat(aaplChunks.get(0).getId())
                .as("AAPL Risk Factors chunk must have stable ID 'aapl-risk-factors'")
                .isEqualTo("aapl-risk-factors");
        assertThat(aaplChunks.get(1).getId())
                .as("AAPL MD&A chunk must have stable ID 'aapl-mdanda'")
                .isEqualTo("aapl-mdanda");

        // Re-add the same chunks — must not duplicate rows
        vectorStore.add(aaplChunks);

        // Query for AAPL chunks — result count must not exceed 2 (one per seeded chunk)
        // If duplicates were inserted, topK(10) would return >2 AAPL results
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("Apple regulatory App Store risk")
                        .topK(10)
                        .similarityThreshold(0.01)
                        .build());

        long aaplCount = results.stream()
                .filter(d -> "AAPL".equals(d.getMetadata().get("ticker")))
                .count();

        assertThat(aaplCount)
                .as("CR-06: re-seeding with stable IDs must not duplicate AAPL chunks (expected <= 2, got %d)", aaplCount)
                .isLessThanOrEqualTo(2);
    }
}
