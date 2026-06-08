package com.quantlens.analytics;

import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;
import net.finmath.stochastic.RandomVariable;
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Hand-computed correctness tests for GBM Monte Carlo math (HC-11).
 * <p>
 * NO Spring context, NO Testcontainers. All expected values are derived
 * <em>externally</em> (analytically from stochastic calculus), NOT by running
 * ForecastService. This breaks circular validation — see RiskMathHandComputedTest
 * for the pattern rationale.
 * <p>
 * These are the CORRECTNESS anchors for Phase 5. Plan 05-02 (engine implementation)
 * must keep these tests GREEN — any drift above tolerance indicates a formula bug.
 * <p>
 * <strong>RED state in Plan 05-01:</strong> HC-11 exercises the finmath GBM chain
 * directly (not via ForecastService stub), so it is expected to be GREEN or NEAR-GREEN
 * even in 05-01 as a finmath API verification gate. If the assertions fail, it indicates
 * either an Ito correction bug in the simulation or a finmath API name mismatch.
 */
class ForecastMathHandComputedTest {

    // -----------------------------------------------------------------------
    // HC-11: GBM analytic mean + Ito-correct median anchor
    // -----------------------------------------------------------------------

    /**
     * HC-11: GBM analytic-mean check — Ito-correct drift guard.
     * <p>
     * Derivation (independent of ForecastService):
     * <pre>
     *   GBM model: dS = μ·S·dt + σ·S·dW
     *   In log-space: d(ln S) = (μ − σ²/2)·dt + σ·dW   ← Ito's lemma
     *
     *   Given: S₀ = 100.0, μ = 0.10 (annualized drift), σ = 0.20 (annualized vol), T = 1 year
     *
     *   GBM analytic mean (first moment):
     *     E[S_T] = S₀ · exp(μ · T)
     *            = 100 · exp(0.10 · 1)
     *            = 100 · 1.10517091808...
     *            = 110.517091808...
     *     → expected: 110.517 (rounded to 3dp)
     *
     *   GBM log-normal median (Ito-correct):
     *     median[S_T] = S₀ · exp((μ − σ²/2) · T)
     *                 = 100 · exp(0.10 − 0.04/2)
     *                 = 100 · exp(0.10 − 0.02)
     *                 = 100 · exp(0.08)
     *                 = 100 · 1.08328706767...
     *                 = 108.328706767...
     *     → expected: 108.329 (rounded to 3dp)
     *
     *   Ito correction guard:
     *     If Ito correction is MISSING (using μ not μ−σ²/2 in log-space):
     *       median would ≈ S₀ · exp(μ · T) = 110.517 (wrong — equals mean, no Jensen gap)
     *     If Ito correction is PRESENT (finmath BlackScholesModel applies it internally):
     *       median ≈ 108.329 &lt; mean ≈ 110.517  ← log-normal skewness sign check
     *
     *   Test assertions:
     *     1. mean(S_T paths) ∈ [110.517 × 0.99, 110.517 × 1.01]  → within 1% (MC sampling noise)
     *     2. median(S_T paths) ≈ 108.329 ± 1%                    → Ito-correct median (1% for MC noise at 5000 paths)
     *     3. median &lt; mean                                         → log-normal sign check
     * </pre>
     *
     * This test is HC-11 — the hand-computed constants 110.517 and 108.329 are derived
     * analytically, NOT from running ForecastService. Running ForecastService to generate
     * the expected values would be circular validation and would not catch a systematic
     * Ito correction bug.
     */
    @Test
    void gbm_analyticMean_itoCorrect_hc11() throws Exception {
        // GIVEN: fixed S₀, μ, σ (the HC-11 parameter set)
        final double s0    = 100.0;
        final double mu    = 0.10;   // annualized drift
        final double sigma = 0.20;   // annualized volatility
        final int    numPaths = 5000;
        final int    seed     = 42;

        // WHEN: run 5000 paths × 252 steps via finmath GBM chain (no ForecastService)
        // This is the same finmath chain that ForecastService will use for GBM in Plan 05-02.
        TimeDiscretization td = new TimeDiscretizationFromArray(
                0.0,        // initialTime
                252,        // numberOfTimeSteps (1 year of trading days)
                1.0 / 252.0 // deltaT (one trading day in year fraction)
        );

        BrownianMotionFromMersenneRandomNumbers bm =
                new BrownianMotionFromMersenneRandomNumbers(td, 1, numPaths, seed);

        // BlackScholesModel applies Ito correction internally (log-space drift = μ − σ²/2)
        BlackScholesModel gbmModel = new BlackScholesModel(
                s0, mu, sigma, new RandomVariableFromArrayFactory());

        MonteCarloProcess process = new EulerSchemeFromProcessModel(gbmModel, bm);
        MonteCarloAssetModel sim  = new MonteCarloAssetModel(process);

        // Extract all path values at T=252 (1 year = time index 252)
        RandomVariable rvAtT = sim.getAssetValue(252, 0);
        double[] pathsAtT = rvAtT.getRealizations(); // double[] of length 5000

        // Compute mean and median from the simulation output
        DescriptiveStatistics statsAtT = new DescriptiveStatistics(pathsAtT);
        double actualMean   = statsAtT.getMean();
        double actualMedian = statsAtT.getPercentile(50.0);

        // THEN: hand-derived expected values (NOT from ForecastService output)
        double expectedMean   = s0 * Math.exp(mu * 1.0);                    // 100 · e^0.10 = 110.517
        double expectedMedian = s0 * Math.exp((mu - sigma * sigma / 2.0) * 1.0); // 100 · e^0.08 = 108.329

        // Assertion 1: mean within 1% of analytic mean (MC sampling noise budget)
        assertThat(actualMean)
                .as("HC-11: GBM E[S_T] must match analytic S₀·exp(μT)=%.3f within 1%%. " +
                    "If this fails, the Ito correction may be missing from the simulation.",
                    expectedMean)
                .isCloseTo(expectedMean, within(expectedMean * 0.01));

        // Assertion 2: median within 1% of Ito-correct log-normal median.
        // 1% tolerance accounts for MC sampling noise at 5000 paths (SE of median ≈ 0.35–0.50%).
        // The critical guard is assertion 3 (median < mean) — the percentage bound here is a
        // coarse sanity check, not a tight numerical guarantee (which would need ~500k paths).
        assertThat(actualMedian)
                .as("HC-11: GBM median must match Ito-correct S₀·exp((μ−σ²/2)T)=%.3f within 1%%. " +
                    "If median≈mean (both ≈110.517), the Ito σ²/2 correction is missing.",
                    expectedMedian)
                .isCloseTo(expectedMedian, within(expectedMedian * 0.01));

        // Assertion 3: median < mean (log-normal distribution property; guards against sign flip)
        assertThat(actualMedian)
                .as("HC-11: GBM median (%.3f) must be strictly less than mean (%.3f). " +
                    "For a log-normal distribution, Jensen's inequality ensures median < mean. " +
                    "If median >= mean, the log-normal simulation has a sign error.",
                    actualMedian, actualMean)
                .isLessThan(actualMean);
    }
}
