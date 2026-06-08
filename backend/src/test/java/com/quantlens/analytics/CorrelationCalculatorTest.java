package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.api.CorrelationMatrixDto;
import com.quantlens.analytics.service.CorrelationCalculator;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for CorrelationCalculator golden-value assertions.
 * <p>
 * Uses the seeded Testcontainers Postgres database (MersenneTwister seed=42,
 * SERIES_START=2022-09-12) — results are fully deterministic.
 * <p>
 * Golden-value constants captured from AnalyticsGoldenValuePrinterTest on 2026-06-08.
 * <p>
 * NOTE: The seeded GBM generator drives all securities with a common market factor
 * and no idiosyncratic component, producing near-perfect pairwise correlation (≈1.0).
 * The structural assertions (symmetry, diagonal=1.0) are the primary correctness proof;
 * the AAPL-MSFT golden value confirms the seed-derived correlation is stable across runs.
 */
class CorrelationCalculatorTest extends AbstractPostgresIntegrationTest {

    // -----------------------------------------------------------------------
    // Golden-value constants — captured from AnalyticsGoldenValuePrinterTest 2026-06-08
    // seed=42, SERIES_START=2022-09-12, alice's Growth Portfolio
    // GBM seed produces near-perfect inter-security correlation (common market factor only)
    // -----------------------------------------------------------------------

    private static final double GOLDEN_CORR_AAPL_MSFT = 1.0;

    @Autowired
    private CorrelationCalculator correlationCalculator;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private CorrelationMatrixDto corrDto;

    @BeforeEach
    @Transactional
    void resolveAlicePortfolio() {
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(alice.getId());
        assertThat(portfolios).as("alice must have at least one portfolio").isNotEmpty();
        Long portfolioId = portfolios.get(0).getId();
        corrDto = correlationCalculator.computeCorrelationMatrix(portfolioId);
    }

    // -----------------------------------------------------------------------
    // RISK-02: Structural properties
    // -----------------------------------------------------------------------

    /**
     * The correlation matrix must be symmetric: matrix[i][j] == matrix[j][i] within 1e-9.
     */
    @Test
    void correlationMatrix_isSymmetric() {
        List<List<Double>> matrix = corrDto.matrix();
        assertThat(matrix).as("correlation matrix must be non-empty").isNotEmpty();
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = 0; j < matrix.size(); j++) {
                double ij = matrix.get(i).get(j);
                double ji = matrix.get(j).get(i);
                assertThat(ij)
                        .as("matrix[%d][%d] must equal matrix[%d][%d] (symmetry)", i, j, j, i)
                        .isCloseTo(ji, within(1e-9));
            }
        }
    }

    /**
     * The diagonal of the correlation matrix must be exactly 1.0.
     * Every series must correlate perfectly with itself.
     */
    @Test
    void correlationMatrix_diagonalIsOne() {
        List<List<Double>> matrix = corrDto.matrix();
        assertThat(matrix).as("correlation matrix must be non-empty").isNotEmpty();
        for (int i = 0; i < matrix.size(); i++) {
            assertThat(matrix.get(i).get(i))
                    .as("diagonal entry matrix[%d][%d] must be exactly 1.0", i, i)
                    .isEqualTo(1.0);
        }
    }

    /**
     * The matrix must be N×N where N = number of holdings (alice has 5 holdings with qty > 0).
     */
    @Test
    void correlationMatrix_isSquareAndCorrectSize() {
        List<List<Double>> matrix = corrDto.matrix();
        List<String> tickers = corrDto.tickers();
        assertThat(tickers).as("alice has 5 holdings; tickers list must have 5 entries").hasSize(5);
        assertThat(matrix).as("correlation matrix must have 5 rows").hasSize(5);
        for (int i = 0; i < matrix.size(); i++) {
            assertThat(matrix.get(i))
                    .as("row %d must have 5 columns", i)
                    .hasSize(5);
        }
    }

    // -----------------------------------------------------------------------
    // RISK-02: Golden-value assertion
    // -----------------------------------------------------------------------

    /**
     * AAPL-MSFT Pearson correlation must match the seed-derived golden value within ±0.001.
     * The seeded GBM model drives all securities with a shared market factor only,
     * yielding near-perfect correlation (1.0) across all pairs for this seed.
     */
    @Test
    void aapl_msft_correlation_matchesGolden() {
        List<String> tickers = corrDto.tickers();
        int aaplIdx = tickers.indexOf("AAPL");
        int msftIdx = tickers.indexOf("MSFT");
        assertThat(aaplIdx).as("AAPL must appear in tickers list").isGreaterThanOrEqualTo(0);
        assertThat(msftIdx).as("MSFT must appear in tickers list").isGreaterThanOrEqualTo(0);
        double aaplMsftCorr = corrDto.matrix().get(aaplIdx).get(msftIdx);
        assertThat(aaplMsftCorr)
                .as("AAPL-MSFT correlation must match golden value within ±0.001")
                .isCloseTo(GOLDEN_CORR_AAPL_MSFT, within(0.001));
    }
}
