package com.quantlens.ai.seed;

import com.quantlens.seed.SeedLog;
import com.quantlens.seed.SeedLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent seeder for AI fixture content in the {@code ai_seed_content} table.
 *
 * <p>{@code @Order(2)} ensures this runner executes after {@code SeedRunner @Order(1)}
 * which seeds the base portfolio data (securities, users, positions) that the AI
 * seed content references.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="ai-v1"}. The completion row is
 * written at the END of the transaction — if the JVM crashes mid-seed, the partial
 * work rolls back and re-runs cleanly on the next restart.
 *
 * <h2>TODO 06-02</h2>
 * The actual seed content (15 EXPLAIN_POSITION rows + 3 DAILY_COMMENTARY rows) is
 * written in Plan 06-02 once the AI service layer is wired and content is authored.
 * This stub is a no-op that proves the idempotency guard compiles and startup order
 * is correct.
 */
@Component
@Order(2)
public class AiSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AiSeedRunner.class);

    private static final String AI_SEED_VERSION = "ai-v1";

    private final AiSeedContentRepository aiSeedContentRepository;
    private final SeedLogRepository seedLogRepository;

    public AiSeedRunner(AiSeedContentRepository aiSeedContentRepository,
                        SeedLogRepository seedLogRepository) {
        this.aiSeedContentRepository = aiSeedContentRepository;
        this.seedLogRepository = seedLogRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadyDone = seedLogRepository.findById(AI_SEED_VERSION)
                .map(SeedLog::isCompleted)
                .orElse(false);
        if (alreadyDone) {
            log.info("AiSeedRunner: seed_log ai-v1 already completed — skipping");
            return;
        }

        log.info("AiSeedRunner: AI seed content not yet present — stub only, see Plan 06-02 for real content");

        // TODO 06-02: seed actual EXPLAIN_POSITION and DAILY_COMMENTARY rows here.
        // Content must reference the seeded portfolio tickers:
        //   Growth (Alice):   AAPL, MSFT, NVDA, AMZN, TSLA
        //   Income (Bob):     JPM, BAC, XOM, CVX, PG, KO, WMT
        //   Balanced (Charlie): AAPL, JPM, XOM, JNJ, PG, MSFT, KO
        // Do NOT write the ai-v1 completion row yet — let 06-02 write it after seeding real content.
        // Intentionally NOT saving a SeedLog row here so 06-02 can run the full seed.
    }
}
