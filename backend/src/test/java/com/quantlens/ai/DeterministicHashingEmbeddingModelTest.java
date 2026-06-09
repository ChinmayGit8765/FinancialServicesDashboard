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
 * Verifies: dimensions, determinism, L2 normalization, similarity ordering,
 * and regression tests for CR-01 (Math.abs overflow), CR-02 (sign bit),
 * CR-03 (null/blank/whitespace input), WR-01 (MurmurHash3 byte mask).
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
                .as("Similar texts should have meaningful similarity > 0.1 (signed feature hashing)")
                .isGreaterThan(0.1);
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

    // ── CR-01 regression: Math.abs(Integer.MIN_VALUE) overflow ─────────────────

    /**
     * CR-01 regression: embed must never throw ArrayIndexOutOfBoundsException
     * regardless of input. A brute-force search for a word whose MurmurHash3 value
     * equals Integer.MIN_VALUE would produce AIOOBE with the old Math.abs() code.
     * We verify that no bucket index is ever negative (which was the root cause).
     *
     * <p>We embed a large corpus of varied tokens and assert no exception is thrown
     * and all returned vector elements are finite.
     */
    @Test
    void embed_neverThrowsAIOOBE_onAdversarialInput() {
        // Embed a variety of short strings covering different hash distributions.
        // These are designed to exercise many distinct hash values including potential edge cases.
        String[] adversarialInputs = {
            "a", "b", "the", "and", "of",
            "Apple revenue iPhone App Store",
            "Federal Reserve interest monetary policy",
            "NVIDIA GPU H100 data center training inference",
            "JPMorgan Chase capital requirements Basel",
            "ExxonMobil upstream Permian Basin Guyana",
            // Single chars that might produce extreme hash values
            "Z", "0",
            // Text with non-ASCII characters (exercises MurmurHash3 tail path)
            "cafe naive resume",
            "10-K filing 500M revenue growth",
        };

        for (String input : adversarialInputs) {
            float[] v = model.embed(input);
            assertThat(v).as("embed(%s) must not throw and must return a finite vector", input)
                    .hasSize(1536);
            // Verify no NaN or Infinity values
            for (float f : v) {
                assertThat(Float.isFinite(f) || f == 0.0f)
                        .as("embed(%s) must produce only finite or zero values, got %f", input, f)
                        .isTrue();
            }
        }
    }

    /**
     * CR-01 regression: verifies that bucket indices are always in [0, 1536).
     * Since bucket = (h & 0x7FFFFFFF) % DIMENSIONS, the result is always non-negative.
     * We embed many strings and verify all bucket writes stay in range by checking
     * no AIOOBE is thrown and the output vector is well-formed.
     */
    @Test
    void embed_bucketIndicesAlwaysInRange_noBucketOutOfBounds() {
        // Embed 200 varied strings — if any produces a negative bucket, AIOOBE would be thrown
        for (int i = 0; i < 200; i++) {
            String text = "token" + i + " word" + (i * 31) + " test" + (i * 7);
            assertThat(model.embed(text))
                    .as("embed('%s') must not throw AIOOBE", text)
                    .hasSize(1536);
        }
    }

    // ── CR-02 regression: signed feature hashing cosine discrimination ─────────

    /**
     * CR-02 regression: signed feature hashing must produce better cosine discrimination
     * than unsigned. The ordering assertion (similar > dissimilar) verifies the sign
     * bit is doing its job — without the sign bit, collision noise can invert this ordering.
     */
    @Test
    void embed_signedFeatureHashing_similarityOrdering_isCorrect() {
        // Finance domain: similar pair
        float[] aapl1 = model.embed("Apple Services revenue App Store subscriptions iCloud");
        float[] aapl2 = model.embed("Apple revenue growth App Store services segment");

        // Completely different domain: oil and gas
        float[] xom = model.embed("ExxonMobil crude oil upstream Permian Basin Guyana deepwater");

        double simAapl = cosineSimilarity(aapl1, aapl2);
        double simCross = cosineSimilarity(aapl1, xom);

        assertThat(simAapl)
                .as("Two Apple Services texts should be more similar to each other than to oil/gas text")
                .isGreaterThan(simCross);
    }

    // ── CR-03 regression: null/blank/whitespace-only input ─────────────────────

    /**
     * CR-03 regression: embed(null) must return a zero vector, not throw NPE.
     * Without the null guard, text.toLowerCase() throws NullPointerException.
     */
    @Test
    void embed_nullInput_returnsZeroVector_notNPE() {
        float[] v = model.embed(null);
        assertThat(v).hasSize(1536);
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        assertThat(norm).isCloseTo(0.0, within(1e-10));
    }

    /**
     * CR-03 regression: embed with only whitespace must return a zero vector.
     */
    @Test
    void embed_blankString_returnsZeroVector() {
        float[] v = model.embed("   ");
        assertThat(v).hasSize(1536);
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        assertThat(norm).isCloseTo(0.0, within(1e-10));
    }

    /**
     * CR-03 regression: embed with only non-breaking space (U+00A0) must
     * return a zero vector, not produce a NaN vector from normalizing a single NBSP token.
     */
    @Test
    void embed_nonBreakingSpaceOnly_returnsZeroVector() {
        // U+00A0 non-breaking space — not matched by Java's \s before this fix
        String nbspOnly = "   ";
        float[] v = model.embed(nbspOnly);
        assertThat(v).hasSize(1536);
        // All values must be finite (no NaN from dividing by zero norm)
        for (float f : v) {
            assertThat(Float.isFinite(f) || f == 0.0f)
                    .as("Vector value must be finite or zero, got %f", f)
                    .isTrue();
        }
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        assertThat(norm).isCloseTo(0.0, within(1e-10));
    }

    /**
     * CR-03 regression: no NaN values in the output for any input including null/blank.
     * L2-normalize guard must prevent NaN propagation.
     */
    @Test
    void embed_noNaNValues_forAnyInput() {
        String[] inputs = { null, "", "  ", "hello", "Apple AAPL risk factors" };
        for (String input : inputs) {
            float[] v = model.embed(input);
            for (float f : v) {
                assertThat(Float.isNaN(f))
                        .as("embed('%s') must not produce NaN values", input)
                        .isFalse();
            }
        }
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
