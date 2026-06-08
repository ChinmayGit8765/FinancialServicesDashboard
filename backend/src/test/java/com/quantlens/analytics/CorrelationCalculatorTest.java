package com.quantlens.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for CorrelationCalculator.
 * <p>
 * No Spring context, no Testcontainers. Tests validate structural properties
 * (symmetry, diagonal) and a golden-value assertion for the AAPL–MSFT pair.
 * <p>
 * RED scaffold — assertions reference GOLDEN_CORR_AAPL_MSFT = 0.0 until
 * Plan 04-02 implements PearsonsCorrelation and the printer supplies the value.
 */
class CorrelationCalculatorTest {

    // -----------------------------------------------------------------------
    // Golden-value constants — FILL from AnalyticsGoldenValuePrinterTest
    // -----------------------------------------------------------------------

    private static final double GOLDEN_CORR_AAPL_MSFT = 0.0; // FILL from printer

    // -----------------------------------------------------------------------
    // RISK-02: Structural properties
    // -----------------------------------------------------------------------

    /**
     * The correlation matrix must be symmetric: matrix[i][j] == matrix[j][i].
     * RED until Plan 04-02 fills in real PearsonsCorrelation computation.
     */
    @Test
    void correlationMatrix_isSymmetric() {
        // TODO: obtain real CorrelationMatrixDto from CorrelationCalculator via integration slice in Plan 04-02
        // Stub returns empty matrix — test will fail until Plan 04-02 populates real data
        List<List<Double>> matrix = List.of(); // FILL: correlationCalculator.computeCorrelationMatrix(portfolioId).matrix()
        for (int i = 0; i < matrix.size(); i++) {
            for (int j = 0; j < matrix.size(); j++) {
                double ij = matrix.get(i).get(j);
                double ji = matrix.get(j).get(i);
                assertThat(ij).as("matrix[%d][%d] should equal matrix[%d][%d]", i, j, j, i)
                        .isCloseTo(ji, within(1e-10));
            }
        }
    }

    /**
     * The diagonal of the correlation matrix must be exactly 1.0 (every series correlates perfectly with itself).
     * RED until Plan 04-02.
     */
    @Test
    void correlationMatrix_diagonalIsOne() {
        // TODO: obtain real CorrelationMatrixDto in Plan 04-02
        List<List<Double>> matrix = List.of(); // FILL
        for (int i = 0; i < matrix.size(); i++) {
            assertThat(matrix.get(i).get(i))
                    .as("diagonal entry matrix[%d][%d] must be 1.0", i, i)
                    .isEqualTo(1.0);
        }
    }

    // -----------------------------------------------------------------------
    // RISK-02: Golden-value assertion
    // -----------------------------------------------------------------------

    /**
     * AAPL–MSFT Pearson correlation must match the seed-derived golden value within ±0.001.
     * RED until Plan 04-02.
     */
    @Test
    void aapl_msft_correlation_matchesGolden() {
        double aaplMsftCorr = 0.0; // FILL: extract from matrix at tickers.indexOf("AAPL"), tickers.indexOf("MSFT")
        assertThat(aaplMsftCorr).isCloseTo(GOLDEN_CORR_AAPL_MSFT, within(0.001));
    }
}
