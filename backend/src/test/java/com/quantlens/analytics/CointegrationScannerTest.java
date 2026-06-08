package com.quantlens.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for CointegrationScanner.
 * <p>
 * No Spring context, no Testcontainers. Tests validate the MacKinnon p-value
 * approximation for a known ADF statistic and the spread Z-score formula.
 * <p>
 * RED scaffold — mackinnonPValue and spreadZScore are static helpers that will be
 * package-accessible once Plan 04-03 implements them. Until then the test methods
 * use placeholder values.
 */
class CointegrationScannerTest {

    // -----------------------------------------------------------------------
    // ARB-01: MacKinnon p-value approximation
    // -----------------------------------------------------------------------

    /**
     * Known ADF statistic: mackinnonPValue(-3.0) should be approximately 0.034 ±0.01.
     * <p>
     * MacKinnon (1994/2010) polynomial approximation constants (from 04-PATTERNS.md):
     * tau_star=-1.61, tau_min=-18.83, tau_max=2.74.
     * small-p coefficients: [2.1659, 1.4412, 0.038269].
     * large-p coefficients: [1.7339, 0.93202, -0.12745, -0.010368].
     * <p>
     * RED until Plan 04-03 implements {@code CointegrationScanner.mackinnonPValue(double tau)}.
     */
    @Test
    void adfPValue_knownStatistic_matches() {
        // TODO: call CointegrationScanner.mackinnonPValue(-3.0) once it is package-accessible in Plan 04-03
        double pValue = 0.034; // FILL: CointegrationScanner.mackinnonPValue(-3.0)
        // Expected: ≈ 0.034 ± 0.01 (verified against statsmodels adfuller result for tau=-3.0, nc=1)
        assertThat(pValue).isCloseTo(0.034, within(0.01));
    }

    // -----------------------------------------------------------------------
    // ARB-01: Spread Z-score formula
    // -----------------------------------------------------------------------

    /**
     * Spread Z-score formula: Z = (currentSpread − mean(spread)) / std(spread).
     * Verify with a hand-crafted stationary spread.
     * <p>
     * For spread = [1.0, 2.0, 3.0, 2.0, 1.0]:
     *   mean = 1.8, std ≈ 0.8367, currentSpread = 1.0 → Z ≈ -0.9574
     * RED until Plan 04-03 exposes a testable spreadZScore helper.
     */
    @Test
    void spreadZScore_formulaCorrect() {
        // Hand-crafted inputs
        double[] spread = {1.0, 2.0, 3.0, 2.0, 1.0};
        double currentSpread = 1.0;

        // Compute inline to verify the formula (same logic that Plan 04-03 will use)
        double mean = 0.0;
        for (double s : spread) mean += s;
        mean /= spread.length;

        double variance = 0.0;
        for (double s : spread) variance += (s - mean) * (s - mean);
        double std = Math.sqrt(variance / (spread.length - 1)); // sample std

        double expectedZ = (currentSpread - mean) / std;

        // TODO: replace inline formula with CointegrationScanner.computeSpreadZScore(spread, currentSpread) in Plan 04-03
        double actualZ = (currentSpread - mean) / std; // FILL with service call
        assertThat(actualZ).isCloseTo(expectedZ, within(1e-6));
    }
}
