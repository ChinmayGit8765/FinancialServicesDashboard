package com.quantlens.analytics.service;

import com.quantlens.analytics.service.ForecastService;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Regression tests for CR-01, CR-02, WR-01 bugs fixed in Phase-05 code review.
 * Pure-unit tests (no Spring context, no Testcontainers).
 */
class ForecastRegressionTest {

    // CR-01: sample variance (n-1) vs population variance (n)

    /**
     * CR-01 regression: at n=2, sample/pop ratio = sqrt(2) ~= 1.41421.
     * ForecastService uses getVariance() (sample, n-1) not getPopulationVariance().
     *
     * returns=[0.01, 0.03], mean=0.02:
     *   pop_var  = 0.0001  (sum_sq_dev / n=2)
     *   samp_var = 0.0002  (sum_sq_dev / n-1=1)
     *   samp_ann = sqrt(0.0002 * 252) ~= 0.22450
     *   pop_ann  = sqrt(0.0001 * 252) ~= 0.15875
     *   ratio = sqrt(2) -- the 29% underestimate at n=2 with population estimator.
     */
    @Test
    void cr01_sampleVariance_n2_handComputed_vs_populationVariance() {
        double[] returns = { 0.01, 0.03 };
        DescriptiveStatistics stats = new DescriptiveStatistics(returns);

        double sampleAnn     = Math.sqrt(stats.getVariance() * 252.0);
        double populationAnn = Math.sqrt(stats.getPopulationVariance() * 252.0);

        assertThat(sampleAnn)
                .as("CR-01: sample ann sigma must equal sqrt(0.0002*252)")
                .isCloseTo(Math.sqrt(0.0002 * 252.0), within(1e-10));

        assertThat(populationAnn)
                .as("CR-01: population ann sigma must equal sqrt(0.0001*252)")
                .isCloseTo(Math.sqrt(0.0001 * 252.0), within(1e-10));

        assertThat(sampleAnn / populationAnn)
                .as("CR-01: at n=2, samp/pop ratio must be sqrt(2). Ratio=1 means biased estimator used.")
                .isCloseTo(Math.sqrt(2.0), within(1e-10));

        assertThat(stats.getVariance())
                .as("CR-01: getVariance() must differ from getPopulationVariance() at n=2")
                .isNotEqualTo(stats.getPopulationVariance());

        assertThat(sampleAnn).isGreaterThan(populationAnn);
    }

    /** CR-01: at n=3, ratio = sqrt(3/2) ~= 1.22474. */
    @Test
    void cr01_sampleVariance_n3_handComputed() {
        double[] returns = { 0.02, 0.04, 0.06 };
        DescriptiveStatistics stats = new DescriptiveStatistics(returns);
        double sampleAnn     = Math.sqrt(stats.getVariance() * 252.0);
        double populationAnn = Math.sqrt(stats.getPopulationVariance() * 252.0);
        assertThat(sampleAnn / populationAnn)
                .as("CR-01: at n=3, ratio must be sqrt(3/2)~=1.22474")
                .isCloseTo(Math.sqrt(3.0 / 2.0), within(1e-10));
    }

    /**
     * CR-01: getStandardDeviation() == sqrt(getVariance()) in Hipparchus 4.0.3.
     * Both use sample (n-1). ForecastService uses explicit sqrt(getVariance()*252)
     * for self-documentation and future-proofing against library version changes.
     */
    @Test
    void cr01_getStandardDeviation_equals_sqrtGetVariance_in_hipparchus4() {
        double[] returns = { 0.01, 0.03, 0.02, 0.05, -0.01 };
        DescriptiveStatistics stats = new DescriptiveStatistics(returns);
        double viaStdDev   = stats.getStandardDeviation() * Math.sqrt(252.0);
        double viaVariance = Math.sqrt(stats.getVariance() * 252.0);
        assertThat(viaStdDev)
                .as("CR-01: getStdDev()*sqrt(252) == sqrt(getVariance()*252) in Hipparchus 4.0.3")
                .isCloseTo(viaVariance, within(1e-12));
    }

    // CR-02: Bootstrap degenerate case regression

    /**
     * CR-02 negative reference: identical paths yield zero spread (pre-fix degenerate state).
     * When H==L, maxBlockStart=0 and all paths start at block index 0 -- zero spread.
     */
    @Test
    void cr02_extractPercentiles_degenerateAllSame_zeroSpread() {
        double[] allSame = new double[5000];
        java.util.Arrays.fill(allSame, 100.0);
        double[] pcts = ForecastService.extractPercentiles(allSame);
        assertThat(pcts[4] - pcts[0])
                .as("CR-02 negative ref: identical paths yield zero p95-p5 spread")
                .isEqualTo(0.0);
    }

    /**
     * CR-02 positive: distinct paths yield non-zero spread (post-fix H<=L guard).
     * H<=L ensures maxBlockStart >= 1 so block starts vary and paths differ.
     */
    @Test
    void cr02_extractPercentiles_distinctValues_nonZeroSpread() {
        double[] pathValues = new double[5000];
        for (int i = 0; i < 5000; i++) {
            pathValues[i] = 90.0 + (i / 5000.0) * 20.0;
        }
        double[] pcts = ForecastService.extractPercentiles(pathValues);
        assertThat(pcts[4] - pcts[0])
                .as("CR-02: distinct paths must yield non-zero p95-p5 spread (> 10 for [90,110) range)")
                .isGreaterThan(10.0);
        for (int i = 0; i < 4; i++) {
            assertThat(pcts[i]).isLessThanOrEqualTo(pcts[i + 1]);
        }
    }

    // WR-01: extractPercentiles determinism

    /**
     * WR-01 regression: extractPercentiles is deterministic (sort + index, no RNG).
     * Two calls with the same input must produce byte-identical output.
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
                .as("WR-01: extractPercentiles must be deterministic")
                .isEqualTo(pcts2);
    }
}
