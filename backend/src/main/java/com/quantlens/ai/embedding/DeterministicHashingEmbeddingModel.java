package com.quantlens.ai.embedding;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Zero-key deterministic EmbeddingModel using MurmurHash3-based bigram feature hashing
 * projected into 1536 dimensions with L2 normalization.
 *
 * <p>No external dependencies, no network, fully deterministic (same text → same vector every
 * time). Produces genuine cosine similarity: documents with overlapping vocabulary produce
 * closer vectors than dissimilar ones. Registered as {@code @Primary} to override the
 * {@code OpenAiEmbeddingModel} auto-configured by {@code spring-ai-starter-model-openai},
 * ensuring no key is needed for embedding at seed time or query time.
 *
 * <p>Used for BOTH seeding (RagSeedRunner) AND query-time retrieval (QuestionAnswerAdvisor)
 * in both demo and live modes — ensuring embedding-space consistency throughout v1. Only
 * answer generation switches to the real LLM provider when a key is present.
 *
 * <p>Technique: signed feature hashing (Weinberger et al. 2009, MurmurHash3 bigram projection).
 * The sign bit (h >>> 31) determines whether a feature increments or decrements its bucket,
 * reducing the collision noise that degrades cosine discrimination.
 * See: https://en.wikipedia.org/wiki/Feature_hashing
 *
 * <p>Compile-time assumption resolutions (Wave 0):
 * <ul>
 *   <li>A1: {@code new Embedding(float[], Integer)} constructor — resolved at compile</li>
 *   <li>A2: {@code EmbeddingRequest.getInstructions()} getter name — resolved at compile</li>
 *   <li>A3: {@code @Primary} overrides OpenAiEmbeddingModel — verified by RagAdvisorConfigTest</li>
 * </ul>
 */
@Component
@Primary
public class DeterministicHashingEmbeddingModel implements EmbeddingModel {

    private static final int DIMENSIONS = 1536;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        // A2: getInstructions() — resolved at compile; if this fails check EmbeddingRequest.getInputs()
        List<String> inputs = request.getInstructions();
        List<Embedding> results = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            float[] vector = embed(inputs.get(i));
            // A1: new Embedding(float[], Integer) — resolved at compile
            results.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(results);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * Embeds the given text into a 1536-dimensional L2-normalized float vector.
     * Public to allow direct use in tests and RagSeedRunner if needed.
     *
     * <p>CR-03: null/blank text returns a zero vector without throwing NPE or producing NaN.
     * CR-01: bucket index uses {@code (h & 0x7FFFFFFF) % DIMENSIONS} instead of
     *        {@code Math.abs(h) % DIMENSIONS} — Math.abs(Integer.MIN_VALUE) is still negative.
     * CR-02: sign bit {@code (h >>> 31)} determines increment direction per
     *        Weinberger 2009 signed feature hashing — reduces collision noise.
     *
     * @param text the text to embed (null/blank returns zero vector; any length otherwise)
     * @return a 1536-dim L2-normalized float array (zero vector for null/blank/empty input)
     */
    public float[] embed(String text) {
        // CR-03: null/blank guard — must come BEFORE any method call on text
        if (text == null || text.isBlank()) {
            return new float[DIMENSIONS];
        }
        // Normalize: lowercase, collapse whitespace including non-breaking space (CR-03: \\u00a0)
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\u00a0]+", " ")
                .trim();

        String[] words = normalized.split("\\s+");
        float[] vector = new float[DIMENSIONS];

        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) {
            return vector; // zero vector for empty text
        }

        // Unigrams — weight 1.0
        // CR-01: use (h & 0x7FFFFFFF) % DIMENSIONS — Math.abs(Integer.MIN_VALUE) returns MIN_VALUE
        // CR-02: use sign bit (h >>> 31) to choose increment direction (Weinberger 2009)
        for (String word : words) {
            int h = murmur3(word);
            int bucket = (h & 0x7FFFFFFF) % DIMENSIONS;
            vector[bucket] += ((h >>> 31) == 0) ? 1.0f : -1.0f;
        }

        // Bigrams (consecutive word pairs) — weight 0.5 (down-weight vs unigrams)
        // CR-01 + CR-02: same fixes applied here
        for (int i = 0; i + 1 < words.length; i++) {
            String bigram = words[i] + "_" + words[i + 1];
            int h = murmur3(bigram);
            int bucket = (h & 0x7FFFFFFF) % DIMENSIONS;
            vector[bucket] += ((h >>> 31) == 0) ? 0.5f : -0.5f;
        }

        return l2Normalize(vector);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    /**
     * L2-normalizes the given vector in-place (returns a new array).
     * CR-03: guards against zero norm and non-finite scale to prevent NaN vectors.
     */
    private static float[] l2Normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        if (norm == 0.0 || !Double.isFinite(norm)) return v;
        float scale = (float) (1.0 / Math.sqrt(norm));
        if (!Float.isFinite(scale)) return v; // paranoia guard against +Infinity
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * scale;
        return out;
    }

    /**
     * Inline MurmurHash3 (32-bit) — no external dependency.
     * Algorithm: Austin Appleby's MurmurHash3 (public domain).
     *
     * <p>WR-01: all four bytes in the 4-byte block loop are masked with {@code & 0xff}
     * (including the previously-unmasked {@code data[i+3]}) to match the standard
     * unsigned-byte interpretation.
     */
    private static int murmur3(String key) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        int length = data.length;
        int seed = 0x9747b28c;
        int h1 = seed;
        final int c1 = 0xcc9e2d51, c2 = 0x1b873593;
        int roundedEnd = (length & 0xFFFFFFFC);
        for (int i = 0; i < roundedEnd; i += 4) {
            // WR-01: (data[i+3] & 0xff) << 24 — add & 0xff mask on the last byte
            int k1 = (data[i]     & 0xff)        |
                     ((data[i + 1] & 0xff) <<  8) |
                     ((data[i + 2] & 0xff) << 16) |
                     ((data[i + 3] & 0xff) << 24);
            k1 *= c1;
            k1 = Integer.rotateLeft(k1, 15);
            k1 *= c2;
            h1 ^= k1;
            h1 = Integer.rotateLeft(h1, 13);
            h1 = h1 * 5 + 0xe6546b64;
        }
        int k1 = 0;
        switch (length & 0x03) {
            case 3: k1 ^= (data[roundedEnd + 2] & 0xff) << 16;
            // fallthrough
            case 2: k1 ^= (data[roundedEnd + 1] & 0xff) << 8;
            // fallthrough
            case 1:
                k1 ^= data[roundedEnd] & 0xff;
                k1 *= c1;
                k1 = Integer.rotateLeft(k1, 15);
                k1 *= c2;
                h1 ^= k1;
        }
        h1 ^= length;
        // fmix32
        h1 ^= h1 >>> 16;
        h1 *= 0x85ebca6b;
        h1 ^= h1 >>> 13;
        h1 *= 0xc2b2ae35;
        h1 ^= h1 >>> 16;
        return h1;
    }
}
