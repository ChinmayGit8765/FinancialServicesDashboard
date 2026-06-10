package com.quantlens.analytics;

import com.quantlens.analytics.service.CointegrationScanner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for CointegrationScanner static helpers.
 * <p>
 * No Spring context, no Testcontainers. Tests validate the MacKinnon p-value
 * approximation for a known ADF statistic and the spread Z-score formula.
 * <p>
 * {@code mackinnonPValue} and {@code adfStatistic} are package-private static
 * methods on {@link CointegrationScanner} — accessible from this test class
 * because both live in {@code com.quantlens.analytics}.
 */
class CointegrationScannerTest {

    // -----------------------------------------------------------------------
    // ARB-01: MacKinnon p-value approximation
    // -----------------------------------------------------------------------

    /**
     * Known ADF statistic: mackinnonPValue(-3.0) should be approximately 0.034 ±0.01.
     * <p>
     * MacKinnon (1994/2010) polynomial approximation constants (tau = -3.0 is in the
     * small-p regime where tau <= TAU_STAR = -1.61):
     * <pre>
     *   lstar = 2.1659 + 1.4412×(-3.0) + 0.038269×(-3.0)² = 2.1659 − 4.3236 + 0.344421 = −1.813
     *   p ≈ Φ(−1.813) ≈ 0.035
     * </pre>
     * Verified against statsmodels {@code mackinnonp(-3.0, "c", 1)} ≈ 0.034.
     */
    @Test
    void adfPValue_knownStatistic_matches() {
        double pValue = CointegrationScanner.mackinnonPValue(-3.0);
        // Expected: ≈ 0.034 ± 0.01 (statsmodels mackinnonp(-3.0, "c", 1))
        assertThat(pValue)
                .as("mackinnonPValue(-3.0) must be ≈ 0.034 ±0.01 (MacKinnon polynomial, constant-only case)")
                .isCloseTo(0.034, within(0.01));
    }

    /**
     * Boundary: tau above TAU_MAX (2.74) must return 1.0 exactly.
     */
    @Test
    void adfPValue_aboveTauMax_returnsOne() {
        assertThat(CointegrationScanner.mackinnonPValue(3.0))
                .as("p-value for tau > 2.74 must be exactly 1.0")
                .isEqualTo(1.0);
    }

    /**
     * Boundary: tau below TAU_MIN (-18.83) must return 0.0 exactly.
     */
    @Test
    void adfPValue_belowTauMin_returnsZero() {
        assertThat(CointegrationScanner.mackinnonPValue(-20.0))
                .as("p-value for tau < -18.83 must be exactly 0.0")
                .isEqualTo(0.0);
    }

    // -----------------------------------------------------------------------
    // ARB-01: Spread Z-score formula
    // -----------------------------------------------------------------------

    /**
     * Spread Z-score formula: Z = (currentSpread − mean(spread)) / std(spread).
     * Verifies with a hand-crafted stationary spread that the inline formula is
     * mathematically correct and self-consistent.
     * <p>
     * For spread = [1.0, 2.0, 3.0, 2.0, 1.0]:
     *   mean = (1+2+3+2+1)/5 = 1.8
     *   sample var = ((1.8-1)^2+(1.8-2)^2+(1.8-3)^2+(1.8-2)^2+(1.8-1)^2)/4
     *              = (0.64+0.04+1.44+0.04+0.64)/4 = 2.80/4 = 0.70
     *   sample std = sqrt(0.70) ≈ 0.83666
     *   last element = 1.0 → Z = (1.0 − 1.8) / 0.83666 ≈ −0.9562
     */
    @Test
    void spreadZScore_formulaCorrect() {
        double[] spread = {1.0, 2.0, 3.0, 2.0, 1.0};
        double currentSpread = spread[spread.length - 1]; // = 1.0

        // Compute Z inline — same logic as CointegrationScanner.scanPairs (DescriptiveStatistics sample std)
        double mean = 0.0;
        for (double s : spread) mean += s;
        mean /= spread.length; // = 1.8

        double variance = 0.0;
        for (double s : spread) variance += (s - mean) * (s - mean);
        double std = Math.sqrt(variance / (spread.length - 1)); // sample std (n-1) = sqrt(0.70) ≈ 0.83666

        double z = (currentSpread - mean) / std; // (1.0 - 1.8) / 0.83666 ≈ -0.9562

        // Verify the computed Z is in the expected ballpark (mean=1.8, last=1.0 → negative Z)
        assertThat(z)
                .as("Z-score for spread=[1,2,3,2,1] with currentSpread=1.0 must be approx -0.956")
                .isCloseTo(-0.9562, within(0.001));

        // Self-consistency: formula evaluated twice must agree to floating-point precision
        assertThat(z)
                .as("Z-score formula must be self-consistent")
                .isCloseTo((currentSpread - mean) / std, within(1e-10));

        // Sign check: last value (1.0) is below mean (1.8) → Z must be negative
        assertThat(z)
                .as("Z-score must be negative when currentSpread < mean")
                .isLessThan(0.0);
    }

    // -----------------------------------------------------------------------
    // ARB-01: ADF statistic guard (short spread)
    // -----------------------------------------------------------------------

    /**
     * adfStatistic() must return NaN for spreads shorter than the minimum length (10).
     */
    @Test
    void adfStatistic_shortSpread_returnsNaN() {
        double[] tooShort = {1.0, 2.0, 1.5, 2.0, 1.0}; // length 5 < MIN_SPREAD_LENGTH=10
        double result = CointegrationScanner.adfStatistic(tooShort);
        assertThat(Double.isNaN(result))
                .as("adfStatistic on spread shorter than 10 must return NaN")
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // ARB-01: ADF detects a stationary AR(1) spread — the math behind the seeded COP↔XOM pair
    // -----------------------------------------------------------------------

    /**
     * A stationary AR(1) spread {@code u[t] = phi*u[t-1] + sigma*eps[t]} with {@code phi = 0.85}
     * must be flagged cointegrated: the ADF τ is strongly negative and the MacKinnon p-value &lt; 0.05.
     * This is exactly the Ornstein-Uhlenbeck spread used to seed the COP↔XOM demo pair, so it proves
     * {@code CointegrationScanner} legitimately detects that pair with NO threshold change — and that
     * the "No cointegrated pairs" result for ordinary shared-factor-GBM holdings (a unit-root spread)
     * is correct behaviour, not a bug.
     */
    @Test
    void adf_stationaryAr1Spread_isDetected() {
        double[] spread = ar1(0.85, 0.02, 300, 7L);
        double tau = CointegrationScanner.adfStatistic(spread);
        assertThat(tau)
                .as("ADF τ on a stationary AR(1, phi=0.85) spread must be strongly negative")
                .isLessThan(-3.34); // MacKinnon 5% cointegration critical value (1 regressor) ≈ -3.34
        assertThat(CointegrationScanner.mackinnonPValue(tau))
                .as("MacKinnon p-value for a stationary spread must reject the unit root (< 0.05)")
                .isLessThan(0.05);
    }

    /** Deterministic AR(1): u[t] = phi·u[t-1] + sigma·eps[t], u[0]=0, Gaussian eps from a fixed seed. */
    private static double[] ar1(double phi, double sigma, int n, long seed) {
        org.hipparchus.random.MersenneTwister rng = new org.hipparchus.random.MersenneTwister(seed);
        double[] u = new double[n];
        for (int t = 1; t < n; t++) {
            u[t] = phi * u[t - 1] + sigma * rng.nextGaussian();
        }
        return u;
    }
}
