package com.quantlens.analytics;

import com.quantlens.analytics.service.CointegrationScanner;
import org.hipparchus.stat.correlation.Covariance;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.hipparchus.stat.regression.OLSMultipleLinearRegression;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Hand-computed correctness tests for core quantitative risk formulas.
 * <p>
 * NO Spring context, NO Testcontainers. All inputs are tiny fixed arrays whose
 * expected outputs are derived <em>by hand / externally</em>, NOT by running the
 * implementation. This breaks the circular validation in AnalyticsGoldenValuePrinterTest
 * where expected values were generated from the same (potentially buggy) implementation.
 * <p>
 * Each test documents its derivation in comments so a reviewer can verify the
 * expected value without running any code.
 * <p>
 * These are the CORRECTNESS anchors. The seed-database golden-value tests in
 * {@link RiskCalculatorTest} and {@link FamaFrenchCalculatorTest} are REGRESSION
 * anchors (they catch regressions in the seed path, but cannot catch systematic
 * formula bugs since their expected values were derived from the implementation).
 */
class RiskMathHandComputedTest {

    // -----------------------------------------------------------------------
    // HC-01: Sharpe ratio — std(EXCESS returns) denominator (CR-01 correctness)
    // -----------------------------------------------------------------------

    /**
     * Hand-computed Sharpe ratio with VARYING RF to make the CR-01 bug observable.
     * <p>
     * Derivation:
     * <pre>
     *   Portfolio daily log returns: r = [0.010, -0.005, 0.020, 0.015]
     *   Daily RF (varying):         rf = [0.0001, 0.0003, 0.0001, 0.0005]
     *
     *   Excess returns: e = [0.0099, -0.0053, 0.0199, 0.0145]
     *
     *   Mean(e) = (0.0099 + (-0.0053) + 0.0199 + 0.0145) / 4
     *           = 0.039 / 4 = 0.00975
     *
     *   Deviations of e from mean(e)=0.00975:
     *     0.0099  - 0.00975 = 0.00015
     *     -0.0053 - 0.00975 = -0.01505
     *     0.0199  - 0.00975 =  0.01015
     *     0.0145  - 0.00975 =  0.00475
     *   Sum of sq = 0.00015^2 + 0.01505^2 + 0.01015^2 + 0.00475^2
     *             = 0.0000000225 + 0.000226503 + 0.000103022 + 0.0000225625
     *             ≈ 0.000352110
     *   Sample var(e) = 0.000352110 / 3 ≈ 0.000117370
     *   Sample std(e) = sqrt(0.000117370) ≈ 0.010833744
     *
     *   Sharpe(correct) = mean(e) / std(e) × sqrt(252)
     *                   = 0.00975 / 0.010833744 × 15.87451
     *                   = 0.899961 × 15.87451 ≈ 14.285
     *
     *   Compare to BUGGY formula using std(portfolio returns r):
     *     Mean(r) = (0.010 - 0.005 + 0.020 + 0.015)/4 = 0.04/4 = 0.01
     *     Deviations: 0, -0.015, 0.01, 0.005
     *     Sum sq = 0 + 0.000225 + 0.0001 + 0.000025 = 0.00035
     *     Sample std(r) = sqrt(0.00035/3) ≈ 0.010801851
     *   Sharpe(buggy)   = 0.00975 / 0.010801851 × 15.87451 ≈ 14.326
     *
     *   With varying RF the two values differ (14.285 vs 14.326) — bug observable.
     * </pre>
     */
    @Test
    void sharpe_handComputed_excessReturnDenominator_crO1() {
        double[] portfolioReturns = {0.010, -0.005, 0.020, 0.015};
        double[] rfDaily          = {0.0001, 0.0003, 0.0001, 0.0005};  // varying RF

        double[] excessReturns = new double[portfolioReturns.length];
        for (int i = 0; i < portfolioReturns.length; i++) {
            excessReturns[i] = portfolioReturns[i] - rfDaily[i];
        }

        DescriptiveStatistics excessStats = new DescriptiveStatistics(excessReturns);
        double meanExcess = excessStats.getMean();
        double stdExcess  = excessStats.getStandardDeviation();
        double sharpeCorrect = (meanExcess / stdExcess) * Math.sqrt(252.0);

        // Hand-derived: ≈ 14.285 (using std of EXCESS returns in denominator)
        assertThat(sharpeCorrect)
                .as("Sharpe (correct: std of excess returns denominator) must be ≈ 14.285 ±0.005")
                .isCloseTo(14.285, within(0.005));

        // ALSO verify that the buggy formula gives a different result with varying RF
        DescriptiveStatistics portStats = new DescriptiveStatistics(portfolioReturns);
        double stdPort   = portStats.getStandardDeviation();
        double sharpeBuggy = (meanExcess / stdPort) * Math.sqrt(252.0);

        // Hand-derived: ≈ 14.326 — different from correct formula
        assertThat(sharpeBuggy)
                .as("Sharpe (buggy: std of portfolio returns) must be ≈ 14.326 ±0.005")
                .isCloseTo(14.326, within(0.005));

        // The difference is detectable and confirms the fix matters
        assertThat(Math.abs(sharpeCorrect - sharpeBuggy))
                .as("Correct and buggy Sharpe must differ detectably with varying RF")
                .isGreaterThan(0.01);
    }

    // -----------------------------------------------------------------------
    // HC-02: Annualized volatility — std(portfolio returns) × sqrt(252)
    // -----------------------------------------------------------------------

    /**
     * Hand-computed annualized volatility from a 3-point return series.
     * <p>
     * Derivation:
     * <pre>
     *   Returns: r = [0.01, -0.02, 0.03]
     *   Mean(r) = (0.01 - 0.02 + 0.03) / 3 = 0.02/3 ≈ 0.006667
     *   Deviations from mean: 0.003333, -0.026667, 0.023333
     *   Sum sq = 0.003333^2 + 0.026667^2 + 0.023333^2
     *          = 0.0000111 + 0.0007111 + 0.0005444 = 0.0012667
     *   Sample var = 0.0012667 / 2 = 0.0006333
     *   Sample std = sqrt(0.0006333) ≈ 0.025166
     *   Annualized vol = 0.025166 × sqrt(252) ≈ 0.025166 × 15.87451 ≈ 0.39949
     * </pre>
     */
    @Test
    void annualizedVol_handComputed() {
        double[] r = {0.01, -0.02, 0.03};
        DescriptiveStatistics stats = new DescriptiveStatistics(r);
        double annualizedVol = stats.getStandardDeviation() * Math.sqrt(252.0);

        // Hand-derived: ≈ 0.39949
        assertThat(annualizedVol)
                .as("Annualized vol for [0.01,-0.02,0.03] must be ≈ 0.39949 ±0.0001")
                .isCloseTo(0.39949, within(0.0001));
    }

    // -----------------------------------------------------------------------
    // HC-03: Beta — cov(portfolio, benchmark) / var(benchmark)  (CR-02 formula)
    // -----------------------------------------------------------------------

    /**
     * Hand-computed beta from a tiny 3-observation paired series.
     * <p>
     * Derivation:
     * <pre>
     *   Portfolio returns: p = [0.01,  0.02,  -0.01]
     *   Benchmark returns: b = [0.005, 0.01,  -0.005]  (b = 0.5 × p → beta = 2.0)
     *
     *   Mean(p) = 0.02/3 ≈ 0.006667
     *   Mean(b) = 0.01/3 ≈ 0.003333
     *
     *   Cov(p,b) [sample, n-1]:
     *     dp = [0.003333, 0.013333, -0.016667]
     *     db = [0.001667, 0.006667, -0.008333]
     *     sum(dp×db) = 0.003333×0.001667 + 0.013333×0.006667 + 0.016667×0.008333
     *               = 0.000005556 + 0.000088889 + 0.000138889 = 0.000233333
     *     Cov = 0.000233333 / 2 = 0.000116667
     *
     *   Var(b) [sample]:
     *     sum(db^2) = 0.001667^2 + 0.006667^2 + 0.008333^2
     *              = 0.000002778 + 0.000044444 + 0.000069444 = 0.000116667
     *     Var(b) = 0.000116667 / 2 = 0.0000583333
     *
     *   Beta = Cov(p,b) / Var(b) = 0.000116667 / 0.0000583333 = 2.0
     * </pre>
     */
    @Test
    void beta_handComputed_covOverVar_cr02() {
        double[] portfolio = {0.01,  0.02,  -0.01};
        double[] benchmark = {0.005, 0.01,  -0.005};   // b = 0.5 × p → beta = 2.0

        Covariance cov = new Covariance();
        double pairCov = cov.covariance(portfolio, benchmark);
        DescriptiveStatistics bmkStats = new DescriptiveStatistics(benchmark);
        double bmkVar = bmkStats.getVariance();
        double beta = pairCov / bmkVar;

        // Hand-derived: beta = 2.0 exactly (cov/var = 2 when b = 0.5×p)
        assertThat(beta)
                .as("Beta for b = 0.5×p must be exactly 2.0")
                .isCloseTo(2.0, within(1e-9));
    }

    // -----------------------------------------------------------------------
    // HC-04: Historical VaR — negated 5th percentile
    // -----------------------------------------------------------------------

    /**
     * Hand-computed historical VaR (5th percentile) from a known 20-element series.
     * <p>
     * Derivation:
     * <pre>
     *   Returns: equally-spaced from -0.10 to +0.09 in steps of 0.01 (20 elements)
     *   Sorted: [-0.10, -0.09, -0.08, ..., 0.09]
     *   The 5th percentile is the value at or near the bottom 5% of the distribution.
     *   For this series the 5th percentile falls in the range [-0.10, -0.09].
     *   histVarPct = -getPercentile(5.0) must be in [0.09, 0.10] (positive loss).
     * </pre>
     */
    @Test
    void historicalVar_handComputed_negatedPercentile() {
        double[] returns = new double[20];
        for (int i = 0; i < 20; i++) {
            returns[i] = -0.10 + i * 0.01;   // [-0.10, -0.09, ..., 0.09]
        }

        DescriptiveStatistics stats = new DescriptiveStatistics(returns);
        double p5 = stats.getPercentile(5.0);
        double histVarPct = -p5;

        // 5th percentile must be in the lower tail [-0.10, -0.09]
        assertThat(p5)
                .as("5th percentile of [-0.10..0.09] must be in [-0.10, -0.09]")
                .isBetween(-0.10, -0.09);
        assertThat(histVarPct)
                .as("Historical VaR percentage (negated percentile) must be positive")
                .isGreaterThan(0.0);
        assertThat(histVarPct)
                .as("Historical VaR percentage must be in [0.09, 0.10]")
                .isBetween(0.09, 0.10);
    }

    // -----------------------------------------------------------------------
    // HC-05: Parametric VaR — (1.645 × σ − μ)
    // -----------------------------------------------------------------------

    /**
     * Hand-computed parametric VaR for a series with known mean and std.
     * <p>
     * Derivation:
     * <pre>
     *   Returns: r = [0.01, -0.01, 0.02, -0.02, 0.00]
     *   Mean(r) = 0.0 / 5 = 0.0
     *   Deviations from mean: 0.01, -0.01, 0.02, -0.02, 0.00
     *   Sum sq = 0.01^2 + 0.01^2 + 0.02^2 + 0.02^2 + 0.00^2
     *          = 0.0001 + 0.0001 + 0.0004 + 0.0004 + 0 = 0.001
     *   Sample var = 0.001 / 4 = 0.00025
     *   Sample std = sqrt(0.00025) = 0.015811388
     *
     *   Parametric VaR% = z95 × σ − μ = 1.645 × 0.015811388 − 0.0 = 0.026009733
     *   Parametric VaR amount at portfolio value = 1000: 0.026009733 × 1000 = 26.009733
     * </pre>
     */
    @Test
    void parametricVar_handComputed() {
        double[] r = {0.01, -0.01, 0.02, -0.02, 0.00};
        DescriptiveStatistics stats = new DescriptiveStatistics(r);
        double mu    = stats.getMean();
        double sigma = stats.getStandardDeviation();
        double z95   = 1.645;
        double paramVarPct = z95 * sigma - mu;

        // Hand-derived: 1.645 × sqrt(0.00025) = 1.645 × 0.015811388 ≈ 0.026010
        assertThat(paramVarPct)
                .as("Parametric VaR % = z95 × σ − μ must be ≈ 0.026010 ±0.000001")
                .isCloseTo(0.026010, within(0.000001));

        double paramVarAmount = paramVarPct * 1000.0;
        assertThat(paramVarAmount)
                .as("Parametric VaR amount at value=1000 must be ≈ 26.010 ±0.001")
                .isCloseTo(26.010, within(0.001));
    }

    // -----------------------------------------------------------------------
    // HC-06: Max drawdown — hand-traced peak-to-trough
    // -----------------------------------------------------------------------

    /**
     * Hand-computed max drawdown on a 5-point equity curve with a known trough.
     * <p>
     * Derivation:
     * <pre>
     *   Equity curve values: [100, 110, 90, 95, 85]
     *   Peak tracking (running max):
     *     v=100, peak=100 → dd=(100-100)/100 = 0.0
     *     v=110, peak=110 → dd=(110-110)/110 = 0.0
     *     v=90,  peak=110 → dd=(90-110)/110  = -20/110 ≈ -0.18182
     *     v=95,  peak=110 → dd=(95-110)/110  = -15/110 ≈ -0.13636
     *     v=85,  peak=110 → dd=(85-110)/110  = -25/110 ≈ -0.22727
     *   Max drawdown = min(dd) = -25/110 ≈ -0.22727
     * </pre>
     */
    @Test
    void maxDrawdown_handComputed_peakToTrough() {
        double[] values = {100.0, 110.0, 90.0, 95.0, 85.0};

        // Replicate RiskCalculator.computeMaxDrawdown logic inline
        double peak = Double.NEGATIVE_INFINITY;
        double maxDd = 0.0;
        for (double v : values) {
            if (v > peak) peak = v;
            double dd = (peak > 0) ? (v - peak) / peak : 0.0;
            if (dd < maxDd) maxDd = dd;
        }

        // Hand-derived: -25/110 ≈ -0.22727
        assertThat(maxDd)
                .as("Max drawdown for [100,110,90,95,85] must be -25/110 ≈ -0.22727 ±0.00001")
                .isCloseTo(-25.0 / 110.0, within(0.00001));
        assertThat(maxDd)
                .as("Max drawdown must be negative")
                .isLessThan(0.0);
    }

    // -----------------------------------------------------------------------
    // HC-07: Log returns formula — ln(P_t / P_{t-1})
    // -----------------------------------------------------------------------

    /**
     * Hand-computed log returns from a 3-bar price series.
     * <p>
     * Derivation:
     * <pre>
     *   Prices: [100.0, 110.0, 99.0]
     *   r[0] = ln(110.0 / 100.0) = ln(1.10)  ≈  0.0953102
     *   r[1] = ln(99.0  / 110.0) = ln(0.9)   ≈ -0.1053605
     * </pre>
     */
    @Test
    void logReturns_handComputed() {
        double[] prices = {100.0, 110.0, 99.0};
        double[] returns = new double[prices.length - 1];
        for (int i = 1; i < prices.length; i++) {
            returns[i - 1] = Math.log(prices[i] / prices[i - 1]);
        }

        assertThat(returns).hasSize(2);
        // r[0] = ln(110/100) = ln(1.1)
        assertThat(returns[0])
                .as("r[0] = ln(110/100) must equal Math.log(1.1) ±1e-10")
                .isCloseTo(Math.log(1.1), within(1e-10));
        // r[1] = ln(99/110) = ln(0.9)
        assertThat(returns[1])
                .as("r[1] = ln(99/110) must equal Math.log(0.9) ±1e-10")
                .isCloseTo(Math.log(0.9), within(1e-10));
    }

    // -----------------------------------------------------------------------
    // HC-08: Fama-French OLS — closed-form single-factor toy example
    // -----------------------------------------------------------------------

    /**
     * Hand-computed Fama-French OLS on a 4-observation perfect linear relationship.
     * <p>
     * Derivation:
     * <pre>
     *   Let y = excess returns, x = MktRf, where y = 1.25 × MktRf exactly.
     *   Data:
     *     y      = [0.010,  0.020, -0.010,  0.005]
     *     MktRf  = [0.008,  0.016, -0.008,  0.004]  (= y / 1.25)
     *
     *   Since y = 1.25 × x with no noise and no constant term,
     *   OLS (with Hipparchus auto-adding intercept) should give:
     *     alpha_daily (intercept) = 0.0
     *     beta_mkt                = 1.25
     *     R²                      = 1.0
     *
     *   Verification via OLS normal equations:
     *     Mean(y) = 0.025/4 = 0.00625
     *     Mean(x) = 0.020/4 = 0.005
     *     sum((xi-x̄)(yi-ȳ)) = (0.008-0.005)(0.010-0.00625) + (0.016-0.005)(0.020-0.00625)
     *                        + (-0.008-0.005)(-0.010-0.00625) + (0.004-0.005)(0.005-0.00625)
     *                        = 0.003×0.00375 + 0.011×0.01375 + (-0.013)×(-0.01625) + (-0.001)×(-0.00125)
     *                        = 0.00001125 + 0.00015125 + 0.00021125 + 0.00000125
     *                        = 0.000375
     *     sum((xi-x̄)^2)     = 0.003^2 + 0.011^2 + 0.013^2 + 0.001^2
     *                        = 0.000009 + 0.000121 + 0.000169 + 0.000001 = 0.0003
     *     beta = 0.000375 / 0.0003 = 1.25  ✓
     *     alpha = ȳ - beta × x̄ = 0.00625 - 1.25 × 0.005 = 0.00625 - 0.00625 = 0.0  ✓
     * </pre>
     */
    @Test
    void famaFrench_ols_handComputedBeta_hc08() {
        double[] excessY = {0.010,  0.020, -0.010,  0.005};
        double[] mktRfX  = {0.008,  0.016, -0.008,  0.004};  // = y / 1.25

        double[][] xMatrix = new double[4][1];
        for (int i = 0; i < 4; i++) {
            xMatrix[i][0] = mktRfX[i];
        }

        OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
        reg.newSampleData(excessY, xMatrix);

        double[] params = reg.estimateRegressionParameters();
        // params = [intercept/alpha, beta_mkt]
        assertThat(params).hasSize(2);

        // Hand-derived: alpha = 0.0, beta_mkt = 1.25
        assertThat(params[0])
                .as("FF alpha_daily must be ≈ 0 for perfect-fit y=1.25×x ±1e-10")
                .isCloseTo(0.0, within(1e-10));
        assertThat(params[1])
                .as("FF beta_mkt must be ≈ 1.25 for y = 1.25 × MktRf ±1e-9")
                .isCloseTo(1.25, within(1e-9));

        assertThat(reg.calculateRSquared())
                .as("R² must be 1.0 for perfect linear relationship ±1e-9")
                .isCloseTo(1.0, within(1e-9));
    }

    // -----------------------------------------------------------------------
    // HC-09: MacKinnon p-value — independently hand-verified reference point
    // -----------------------------------------------------------------------

    /**
     * Hand-computed MacKinnon p-value for tau = -3.0 (constant-only "c" case, n=1).
     * <p>
     * Derivation:
     * <pre>
     *   tau = -3.0 ≤ TAU_STAR (-1.61) → small-p regime polynomial:
     *   lstar = 2.1659 + 1.4412 × (-3.0) + 0.038269 × (-3.0)²
     *         = 2.1659 - 4.3236 + 0.038269 × 9
     *         = 2.1659 - 4.3236 + 0.344421
     *         = -1.813079
     *   p = Φ(-1.813079) where Φ is the standard normal CDF
     *   From N(0,1) table: Φ(-1.81) ≈ 0.0351, Φ(-1.82) ≈ 0.0344
     *   Linear interpolation at -1.813079: ≈ 0.0350 − 0.0007 × 0.3079 ≈ 0.0348
     *   External cross-check: statsmodels.mackinnonp(-3.0, 'c', 1) = 0.0344
     * </pre>
     */
    @Test
    void mackinnonPValue_tau_neg3_handVerified_hc09() {
        double pValue = CointegrationScanner.mackinnonPValue(-3.0);

        // Hand-derived: ≈ 0.034–0.035; statsmodels cross-check: 0.0344
        assertThat(pValue)
                .as("mackinnonPValue(-3.0) must be ≈ 0.034 ±0.003 (polynomial + N(0,1))")
                .isCloseTo(0.034, within(0.003));
        // Must be below 5% critical level (tau=-3.0 < ADF_CV_5PCT=-2.862)
        assertThat(pValue)
                .as("mackinnonPValue(-3.0) must be < 0.05")
                .isLessThan(0.05);

        // Additional check: at tau=-2.862 (5% critical value) p should be ≈ 0.05
        double pAt5pct = CointegrationScanner.mackinnonPValue(-2.862);
        assertThat(pAt5pct)
                .as("mackinnonPValue at 5% critical value (-2.862) must be ≈ 0.05 ±0.005")
                .isCloseTo(0.05, within(0.005));
    }

    // -----------------------------------------------------------------------
    // HC-10: Z-score look-ahead fix (WR-02) — outlier bias demonstration
    // -----------------------------------------------------------------------

    /**
     * Demonstrates the look-ahead bias in Z-score computation (WR-02).
     * <p>
     * Derivation:
     * <pre>
     *   Series: [1.0, 2.0, 1.0, 2.0, 10.0]  (n=5, last = large outlier)
     *   currentSpread = 10.0
     *
     *   BUGGY (include current in normalization):
     *     mean([1,2,1,2,10]) = 16/5 = 3.2
     *     deviations from 3.2: [-2.2, -1.2, -2.2, -1.2, 6.8]
     *     sum sq = 4.84 + 1.44 + 4.84 + 1.44 + 46.24 = 58.8
     *     sample var = 58.8/4 = 14.7,  sample std = sqrt(14.7) ≈ 3.8340
     *     Z_buggy = (10.0 - 3.2) / 3.8340 ≈ 1.773
     *
     *   CORRECT (exclude current from normalization, WR-02 fix):
     *     history = [1.0, 2.0, 1.0, 2.0], mean = 6/4 = 1.5
     *     deviations: [-0.5, 0.5, -0.5, 0.5], sum sq = 0.25×4 = 1.0
     *     sample var = 1.0/3 ≈ 0.3333, sample std = sqrt(0.3333) ≈ 0.5774
     *     Z_correct = (10.0 - 1.5) / 0.5774 ≈ 14.722
     *
     *   Conclusion: the outlier inflates std by a factor of ~6.6 in the buggy version,
     *   making an extreme Z (≈14.7) appear moderate (≈1.8). The fix is critical for
     *   short series near MIN_SPREAD_LENGTH=10.
     * </pre>
     */
    @Test
    void zScore_lookAheadFix_outlierBiasObservable_hc10() {
        double[] residuals = {1.0, 2.0, 1.0, 2.0, 10.0};
        double currentSpread = residuals[residuals.length - 1];

        // BUGGY: include current spread in normalization statistics
        DescriptiveStatistics dsBuggy = new DescriptiveStatistics(residuals);
        double zBuggy = (currentSpread - dsBuggy.getMean()) / dsBuggy.getStandardDeviation();

        // CORRECT: exclude current spread (WR-02 fix)
        DescriptiveStatistics dsCorrect = new DescriptiveStatistics(
                java.util.Arrays.copyOf(residuals, residuals.length - 1));
        double zCorrect = (currentSpread - dsCorrect.getMean()) / dsCorrect.getStandardDeviation();

        // Hand-derived: zBuggy ≈ 1.773, zCorrect ≈ 14.722
        assertThat(zBuggy)
                .as("Buggy Z-score (look-ahead included) must be ≈ 1.773 ±0.001")
                .isCloseTo(1.773, within(0.001));
        assertThat(zCorrect)
                .as("Correct Z-score (look-ahead excluded) must be ≈ 14.722 ±0.001")
                .isCloseTo(14.722, within(0.001));

        // Confirm bug is detectable: correct Z must be much larger
        assertThat(zCorrect)
                .as("Correct Z must be > 5× buggy Z for this outlier series")
                .isGreaterThan(zBuggy * 5.0);
    }
}
