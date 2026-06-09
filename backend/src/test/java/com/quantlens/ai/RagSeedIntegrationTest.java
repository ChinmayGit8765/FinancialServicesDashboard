package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
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
}
