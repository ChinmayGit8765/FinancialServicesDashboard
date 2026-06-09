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
 * <p>Technique: feature hashing (MurmurHash3 bigram projection).
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
     * @param text the text to embed (any length; empty text returns zero vector)
     * @return a 1536-dim L2-normalized float array
     */
    public float[] embed(String text) {
        // Normalize: lowercase, collapse whitespace
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();

        String[] words = normalized.split("\\s+");
        float[] vector = new float[DIMENSIONS];

        if (words.length == 0 || (words.length == 1 && words[0].isEmpty())) {
            return vector; // zero vector for empty text
        }

        // Unigrams — weight 1.0
        for (String word : words) {
            int bucket = Math.abs(murmur3(word)) % DIMENSIONS;
            vector[bucket] += 1.0f;
        }

        // Bigrams (consecutive word pairs) — weight 0.5 (down-weight vs unigrams)
        for (int i = 0; i + 1 < words.length; i++) {
            String bigram = words[i] + "_" + words[i + 1];
            int bucket = Math.abs(murmur3(bigram)) % DIMENSIONS;
            vector[bucket] += 0.5f;
        }

        return l2Normalize(vector);
    }

    // ── private helpers ────────────────────────────────────────────────────────

    private static float[] l2Normalize(float[] v) {
        double norm = 0.0;
        for (float x : v) norm += (double) x * x;
        if (norm == 0.0) return v;
        float scale = (float) (1.0 / Math.sqrt(norm));
        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) out[i] = v[i] * scale;
        return out;
    }

    /**
     * Inline MurmurHash3 (32-bit) — no external dependency.
     * Algorithm: Austin Appleby's MurmurHash3 (public domain).
     */
    private static int murmur3(String key) {
        byte[] data = key.getBytes(StandardCharsets.UTF_8);
        int length = data.length;
        int seed = 0x9747b28c;
        int h1 = seed;
        final int c1 = 0xcc9e2d51, c2 = 0x1b873593;
        int roundedEnd = (length & 0xFFFFFFFC);
        for (int i = 0; i < roundedEnd; i += 4) {
            int k1 = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
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
