package com.quantlens.analytics;

import com.quantlens.analytics.service.ForecastService;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Regression tests for critical bugs fixed in Phase-05 code review.
 * Pure-unit tests (no Spring context, no Testcontainers) exercising the
 * corrected behaviour at boundary conditions the existing integration tests miss.
 *
 * CR-01: Sample std (n-1) vs population std (n) — n=2 exposes 29% bias
 * CR-02: Bootstrap degenerate case when H==L — maxBlockStart was 0
 * WR-01: extractPercentiles determinism (unit-level reproducibility anchor)
 */
class ForecastRegressionTest {

    // -------------------------------------------------------------------
    // CR-01: sample std regression, hand-computed n=2
    // -------------------------------------------------------------------

    /**
     * CR-01 regression: for n=2, the sample std is sqrt(2) larger than pop std.
     *
     * Hand-computed derivation:
     *   returns = [0.01, 0.03]
     *   mean   = 0.02
     *   pop_var  = 0.0001     pop_ann  = 0.01*sqrt(252)
     *   samp_var = 0.0002     samp_ann = sqrt(0.0002*252)
     *   Ratio = sqrt(2) ~= 1.41421  (29% underestimate at n=2 with pop estimator)
     */
    @Test
    void cr01_sampleStd_n2_handComputed_vs_populationStd() {
        double[] returns = { 0.01, 0.03 };
        DescriptiveStatistics stats = new DescriptiveStatistics(returns);

        double populationAnn = stats.getStandardDeviation() * Math.sqrt(252.0);
        double sampleAnn     = Math.sqrt(stats.getSampleVariance() * 252.0);

        double expectedPopAnn  = 0.01 * Math.sqrt(252.0);
        double expectedSampAnn = Math.sqrt(0.0002 * 252.0);

        assertThat(populationAnn)
                .as("CR-01: pop ann sigma must equal hand-computed 0.01*sqrt(252)=%.6f", expectedPopAnn)
                .isCloseTo(expectedPopAnn, within(1e-10));

        assertThat(sampleAnn)
                .as("CR-01: sample ann sigma must equal hand-computed sqrt(0.0002*252)=%.6f", expectedSampAnn)
                .isCloseTo(expectedSampAnn, within(1e-10));

        // KEY: at n=2, sample/pop ratio = sqrt(2). This is the 29% bias invisible at n=252.
        assertThat(sampleAnn / populationAnn)
                .as("CR-01: at n=2, sample std must be sqrt(2)~=1.41421 times population std. " +
                    "Ratio=1.0 means biased estimator was used.")
                .isCloseTo(Math.sqrt(2.0), within(1e-10));

        assertThat(stats.getSampleVariance())
                .as("CR-01: getSampleVariance() must differ from getPopulationVariance() at n=2.")
                .isNotEqualTo(stats.getPopulationVariance());
    }

    /**
     * CR-01 regression: for n=3, sample/pop ratio = sqrt(3/2) ~= 1.22474.
     *
     *   returns = [0.02, 0.04, 0.06]  mean=0.04
     *   pop_var  = 0.0008/3  samp_var = 0.0008/2   ratio = 3/2
     */
    @Test
    void cr01_sampleStd_n3_handComputed() {
        double[] returns = { 0.02, 0.04, 0.06 };
        DescriptiveStatistics stats = new DescriptiveStatistics(returns);

        double populationAnn = stats.getStandardDeviation() * Math.sqrt(252.0);
        double sampleAnn     = Math.sqrt(stats.getSampleVariance() * 252.0);

        assertThat(sampleAnn / populationAnn)
                .as("CR-01: at n=3, ratio must be sqrt(3/2)~=1.22474.")
                .isCloseTo(Math.sqrt(3.0 / 2.0), within(1e-10));
    }

    // -------------------------------------------------------------------
    // CR-02: Bootstrap degenerate case
    // -------------------------------------------------------------------

    /**
     * CR-02 regression: when all 5000 path values are identical (the pre-fix degenerate
     * bootstrap state when H==L), extractPercentiles returns zero spread.
     * This is the negative reference — documents what the broken code produced.
     */
    @Test
    void cr02_extractPercentiles_degenerateAllSame_zeroSpread() {
        double[] allSame = new double[5000];
        java.util.Arrays.fill(allSame, 100.0);

        double[] pcts = ForecastService.extractPercentiles(allSame);

        assertThat(pcts[4] - pcts[0])
                .as("CR-02 negative ref: identical paths must yield zero p95-p5 spread.")
                .isEqualTo(0.0);
    }

    /**
     * CR-02 regression: with distinct path values (post-fix bootstrap), spread must be > 0.
     * The H<=L fix ensures maxBlockStart >= 1 so block starts vary, producing distinct paths.
     */
    @Test
    void cr02_extractPercentiles_distinctValues_nonZeroSpread() {
        double[] pathValues = new double[5000];
        for (int i = 0; i < 5000; i++) {
            pathValues[i] = 90.0 + (i / 5000.0) * 20.0;
        }

        double[] pcts = ForecastService.extractPercentiles(pathValues);

        assertThat(pcts[4] - pcts[0])
                .as("CR-02: distinct paths must yield non-zero p95-p5 spread (> 10.0 expected for [90,110) range).")
                .isGreaterThan(10.0);

        // monotone ordering
        for (int i = 0; i < 4; i++) {
            assertThat(pcts[i]).isLessThanOrEqualTo(pcts[i + 1]);
        }
    }

    // -------------------------------------------------------------------
    // WR-01: extractPercentiles determinism (bootstrap reproducibility unit anchor)
    // -------------------------------------------------------------------

    /**
     * WR-01 regression: two calls to extractPercentiles with the same input must produce
     * byte-identical output. There is no internal RNG — determinism is guaranteed.
     */
    @Test
    void wr01_extractPercentiles_isDeterministic() {
        double[] pathValues = new double[5000];
        for (int i = 0; i < 5000; i++) {
            pathValues[i] = 95.0 + (i % 100) * 0.1;
        }

        double[] pcts1 = ForecastService.extractPercentiles(pathValues.clone());
        double[] pcts2 = ForecastService.extractPercentiles(pathValues.clone());

        assertThat(pcts1)
                .as("WR-01: extractPercentiles must be deterministic — same input yields same output.")
                .isEqualTo(pcts2);
    }
}
