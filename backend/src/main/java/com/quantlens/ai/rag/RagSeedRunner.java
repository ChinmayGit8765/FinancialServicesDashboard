package com.quantlens.ai.rag;

import com.quantlens.seed.SeedLog;
import com.quantlens.seed.SeedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Idempotent seeder for the 10-K RAG corpus into {@code vector_store}.
 *
 * <p>{@code @Order(3)} — runs after {@link com.quantlens.ai.seed.AiSeedRunner @Order(2)}
 * which seeds demo answers that {@link com.quantlens.ai.chat.DemoModeAdvisor} returns.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="rag-v1"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed the partial work
 * rolls back and re-runs cleanly on the next restart.
 *
 * <h2>Corpus authoring</h2>
 * Full 10-K chunk corpus (AAPL, MSFT, NVDA, JPM, XOM) is authored in {@link #buildChunks()}.
 * THIS PLAN (07-01) leaves {@code buildChunks()} returning an empty list — the actual corpus
 * prose lands in 07-02. The idempotency guard and seed runner scaffold are established here
 * so 07-02 only needs to fill {@code buildChunks()} and run the integration test.
 *
 * <h2>Why SeedLog is NOT written in 07-01</h2>
 * With an empty corpus, writing {@code rag-v1} completed would mask any future seed failure.
 * The guard is active but the completion row is intentionally deferred until 07-02 produces
 * real chunks. Tests in RagSeedIntegrationTest assert {@code rag-v1} completed after 07-02.
 */
@Component
@Order(3)
public class RagSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RagSeedRunner.class);
    private static final String RAG_SEED_VERSION = "rag-v1";

    private final VectorStore vectorStore;
    private final SeedLogRepository seedLogRepository;

    public RagSeedRunner(VectorStore vectorStore,
                         SeedLogRepository seedLogRepository) {
        this.vectorStore        = vectorStore;
        this.seedLogRepository  = seedLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadyDone = seedLogRepository.findById(RAG_SEED_VERSION)
                .map(SeedLog::isCompleted)
                .orElse(false);
        if (alreadyDone) {
            log.info("RagSeedRunner: seed_log {} already completed — skipping", RAG_SEED_VERSION);
            return;
        }

        log.info("RagSeedRunner: starting RAG corpus seed (seed_log {} not yet completed)...",
                RAG_SEED_VERSION);

        List<Document> chunks = buildChunks();
        if (chunks.isEmpty()) {
            // 07-01 skeleton: corpus authoring deferred to 07-02.
            // Do NOT write seed_log yet — rag-v1 completion requires the actual corpus.
            log.info("RagSeedRunner: buildChunks() returned empty list (07-01 skeleton) — " +
                     "corpus and seed_log completion deferred to 07-02");
            return;
        }

        vectorStore.add(chunks);
        log.info("RagSeedRunner: seeded {} chunks into vector_store", chunks.size());

        // Mark seed complete — written LAST so a mid-seed failure rolls back (T-06-07 pattern)
        SeedLog seedLog = new SeedLog(RAG_SEED_VERSION);
        seedLog.setCompleted(true);
        seedLog.setCompletedAt(LocalDateTime.now());
        seedLogRepository.save(seedLog);

        log.info("RagSeedRunner: seed_log {} marked completed", RAG_SEED_VERSION);
    }

    /**
     * Builds the 10-K RAG corpus chunks.
     *
     * <p><strong>07-01 skeleton:</strong> returns empty list. The full corpus (AAPL, MSFT,
     * NVDA, JPM, XOM — 2–4 chunks per ticker, 200–500 authored words each, sections:
     * Risk Factors / MD&amp;A / Business Overview) is authored in 07-02.
     *
     * @return list of {@link Document} chunks ready for {@code VectorStore.add()}
     */
    private List<Document> buildChunks() {
        // 07-02 fills this with authored 10-K excerpts per RagSeedContent.toDocument() pattern
        return List.of();
    }
}
