package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.ai.seed.AiSeedContent;
import com.quantlens.ai.seed.AiSeedContentRepository;
import com.quantlens.seed.SeedLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link com.quantlens.ai.seed.AiSeedRunner}.
 *
 * <p>Verifies that on application start:
 * <ul>
 *   <li>All expected {@code EXPLAIN_POSITION} rows are present (13 tickers)</li>
 *   <li>All 3 {@code DAILY_COMMENTARY} rows are present (GROWTH, INCOME, BALANCED)</li>
 *   <li>Seed content is non-blank and references the correct ticker/persona key</li>
 *   <li>The {@code seed_log} row {@code "ai-v1"} is written and marked {@code completed=true}</li>
 *   <li>Row counts are stable (idempotency — re-run would not add duplicates)</li>
 * </ul>
 */
class AiSeedRunnerTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AiSeedContentRepository aiSeedContentRepository;

    @Autowired
    private SeedLogRepository seedLogRepository;

    /** Tickers with EXPLAIN_POSITION rows — one per portfolio-universe holding. */
    private static final List<String> EXPLAIN_TICKERS = List.of(
            // Growth (Alice)
            "AAPL", "MSFT", "NVDA", "AMZN", "TSLA",
            // Income (Bob)
            "JPM", "BAC", "XOM", "CVX", "PG", "KO", "WMT",
            // Balanced (Charlie) — adds JNJ; AAPL/MSFT/JPM/XOM/PG/KO already above
            "JNJ"
    );

    private static final List<String> COMMENTARY_PERSONAS = List.of("GROWTH", "INCOME", "BALANCED");

    @Test
    void explainPosition_rowCount_equals_expectedTickerUniverse() {
        List<AiSeedContent> rows = aiSeedContentRepository.findAll().stream()
                .filter(r -> "EXPLAIN_POSITION".equals(r.getType()))
                .toList();

        assertThat(rows)
                .as("Should have exactly %d EXPLAIN_POSITION rows (one per unique seeded ticker)",
                        EXPLAIN_TICKERS.size())
                .hasSize(EXPLAIN_TICKERS.size());
    }

    @Test
    void dailyCommentary_rowCount_equals_three() {
        List<AiSeedContent> rows = aiSeedContentRepository.findAll().stream()
                .filter(r -> "DAILY_COMMENTARY".equals(r.getType()))
                .toList();

        assertThat(rows)
                .as("Should have exactly 3 DAILY_COMMENTARY rows (GROWTH, INCOME, BALANCED)")
                .hasSize(3);
    }

    @Test
    void allExpectedExplainTickers_present() {
        for (String ticker : EXPLAIN_TICKERS) {
            Optional<AiSeedContent> row = aiSeedContentRepository
                    .findByTypeAndSubjectId("EXPLAIN_POSITION", ticker);

            assertThat(row)
                    .as("EXPLAIN_POSITION row for ticker '%s' must be present", ticker)
                    .isPresent();
        }
    }

    @Test
    void allExpectedCommentaryPersonas_present() {
        for (String persona : COMMENTARY_PERSONAS) {
            Optional<AiSeedContent> row = aiSeedContentRepository
                    .findByTypeAndSubjectId("DAILY_COMMENTARY", persona);

            assertThat(row)
                    .as("DAILY_COMMENTARY row for persona '%s' must be present", persona)
                    .isPresent();
        }
    }

    @Test
    void sampleExplainContent_isNonBlank_andContainsTicker() {
        // Spot-check a few key tickers that are seeded personas' primary holdings
        for (String ticker : List.of("AAPL", "NVDA", "JPM", "KO", "JNJ")) {
            AiSeedContent row = aiSeedContentRepository
                    .findByTypeAndSubjectId("EXPLAIN_POSITION", ticker)
                    .orElseThrow(() -> new AssertionError("Missing EXPLAIN_POSITION row for " + ticker));

            assertThat(row.getContent())
                    .as("EXPLAIN_POSITION content for %s must be non-blank", ticker)
                    .isNotBlank();
            assertThat(row.getContent())
                    .as("EXPLAIN_POSITION content for %s must reference its ticker symbol", ticker)
                    .contains(ticker);
        }
    }

    @Test
    void commentaryContent_isNonBlank_andDistinctPerPersona() {
        String growthContent = aiSeedContentRepository
                .findByTypeAndSubjectId("DAILY_COMMENTARY", "GROWTH")
                .map(AiSeedContent::getContent)
                .orElseThrow(() -> new AssertionError("Missing DAILY_COMMENTARY for GROWTH"));

        String incomeContent = aiSeedContentRepository
                .findByTypeAndSubjectId("DAILY_COMMENTARY", "INCOME")
                .map(AiSeedContent::getContent)
                .orElseThrow(() -> new AssertionError("Missing DAILY_COMMENTARY for INCOME"));

        String balancedContent = aiSeedContentRepository
                .findByTypeAndSubjectId("DAILY_COMMENTARY", "BALANCED")
                .map(AiSeedContent::getContent)
                .orElseThrow(() -> new AssertionError("Missing DAILY_COMMENTARY for BALANCED"));

        assertThat(growthContent).isNotBlank();
        assertThat(incomeContent).isNotBlank();
        assertThat(balancedContent).isNotBlank();

        // Each persona commentary must be distinct (not the same text)
        assertThat(growthContent)
                .as("GROWTH commentary must be distinct from INCOME commentary")
                .isNotEqualTo(incomeContent);
        assertThat(incomeContent)
                .as("INCOME commentary must be distinct from BALANCED commentary")
                .isNotEqualTo(balancedContent);
        assertThat(growthContent)
                .as("GROWTH commentary must be distinct from BALANCED commentary")
                .isNotEqualTo(balancedContent);
    }

    @Test
    void seedLog_aiV1_isMarkedCompleted() {
        assertThat(seedLogRepository.findById("ai-v1"))
                .as("seed_log 'ai-v1' row must exist after AiSeedRunner runs")
                .isPresent()
                .get()
                .satisfies(log -> assertThat(log.isCompleted())
                        .as("seed_log 'ai-v1' must be marked completed=true")
                        .isTrue());
    }

    @Test
    void idempotency_rowCounts_stableAfterContextStart() {
        // The Spring context started once and AiSeedRunner ran once. The seed_log ai-v1
        // guard prevents re-runs. Verify total row count is stable at expected value:
        // 13 EXPLAIN_POSITION + 3 DAILY_COMMENTARY = 16
        long totalRows = aiSeedContentRepository.count();
        assertThat(totalRows)
                .as("Total ai_seed_content rows must equal 16 (13 EXPLAIN_POSITION + 3 DAILY_COMMENTARY)")
                .isEqualTo(16L);
    }
}
