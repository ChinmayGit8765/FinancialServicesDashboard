package com.quantlens.ai;

import com.quantlens.ai.embedding.DeterministicHashingEmbeddingModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for {@link DeterministicHashingEmbeddingModel}.
 *
 * <p>No Spring context required — tests the embedding logic directly.
 * Verifies: dimensions, determinism, L2 normalization, and similarity ordering.
 *
 * <p>Wave 0 compile-flag resolution:
 * <ul>
 *   <li>A1: {@code new Embedding(float[], Integer)} — verified if this class compiles</li>
 *   <li>A2: {@code EmbeddingRequest.getInstructions()} — verified if this class compiles</li>
 * </ul>
 */
class DeterministicHashingEmbeddingModelTest {

    private DeterministicHashingEmbeddingModel model;

    @BeforeEach
    void setUp() {
        model = new DeterministicHashingEmbeddingModel();
    }

    @Test
    void dimensions_returns1536() {
        assertThat(model.dimensions()).isEqualTo(1536);
    }

    @Test
    void embed_iDeterministic_sameTextProducesSameVector() {
        float[] v1 = model.embed("Apple revenue declined in iPhone segment");
        float[] v2 = model.embed("Apple revenue declined in iPhone segment");

        assertThat(v1).isEqualTo(v2);
    }

    @Test
    void embed_l2NormIsApproximately1() {
        float[] v = model.embed("Apple regulatory risk App Store Digital Markets Act");

        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        double l2Norm = Math.sqrt(norm);

        assertThat(l2Norm).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void embed_l2NormIsApproximately1_forShortText() {
        float[] v = model.embed("AAPL risk");

        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        double l2Norm = Math.sqrt(norm);

        assertThat(l2Norm).isCloseTo(1.0, within(1e-4));
    }

    @Test
    void embed_similarTexts_haveHigherCosineSimilarity_thanDissimilarTexts() {
        // Similar pair: both about Apple revenue / iPhone
        float[] v1 = model.embed("Apple revenue declined in iPhone segment");
        float[] v2 = model.embed("Apple revenue growth in iPhone sales");

        // Dissimilar: Federal Reserve interest rates
        float[] v3 = model.embed("Federal Reserve interest rate decision monetary policy");

        double sim12 = cosineSimilarity(v1, v2);
        double sim13 = cosineSimilarity(v1, v3);

        assertThat(sim12)
                .as("Similar Apple/iPhone texts should score higher than Apple vs Fed Reserve texts")
                .isGreaterThan(sim13);
        assertThat(sim12)
                .as("Similar texts should have meaningful similarity > 0.3")
                .isGreaterThan(0.3);
    }

    @Test
    void call_embeddingRequest_returnsCorrectShape() {
        // A1 + A2 compile verification via EmbeddingRequest.getInstructions() and Embedding(float[], Integer)
        EmbeddingRequest request = new EmbeddingRequest(
                List.of("Apple risk factors", "MSFT cloud revenue"),
                null);
        EmbeddingResponse response = model.call(request);

        assertThat(response.getResults()).hasSize(2);
        assertThat(response.getResults().get(0).getOutput()).hasSize(1536);
        assertThat(response.getResults().get(1).getOutput()).hasSize(1536);
    }

    @Test
    void embed_emptyText_returnsZeroVector() {
        float[] v = model.embed("");
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        // Empty text → zero vector (no normalization possible)
        assertThat(norm).isCloseTo(0.0, within(1e-10));
    }

    // ── private helper ─────────────────────────────────────────────────────────

    private static double cosineSimilarity(float[] a, float[] b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
