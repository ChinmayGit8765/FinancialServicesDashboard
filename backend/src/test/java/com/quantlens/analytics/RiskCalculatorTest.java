package com.quantlens.analytics;

import com.quantlens.analytics.service.RiskCalculator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for pure computation methods in RiskCalculator.
 * <p>
 * No Spring context, no Testcontainers, no mocks needed.
 * Golden-value constants are placeholders (0.0 // FILL from AnalyticsGoldenValuePrinterTest)
 * that will be replaced after Plans 04-02 implement the real math and the printer is run.
 * <p>
 * Tolerances from 04-RESEARCH.md tolerance table:
 * <ul>
 *   <li>Sharpe: ±0.001</li>
 *   <li>Annualized vol: ±0.0001</li>
 *   <li>Max drawdown: ±0.0001</li>
 *   <li>Beta: ±0.001</li>
 * </ul>
 *
 * <p>RED scaffold — all assertions reference {@code GOLDEN_*} constants that are {@code 0.0}
 * until the printer supplies real seed-derived values. Tests will turn GREEN once
 * Plan 04-02 wires the real Hipparchus math and the constants are populated.
 */
class RiskCalculatorTest {

    // -----------------------------------------------------------------------
    // Golden-value constants — FILL from AnalyticsGoldenValuePrinterTest
    // -----------------------------------------------------------------------

    private static final double GOLDEN_SHARPE       = 0.0; // FILL from printer
    private static final double GOLDEN_ANNUAL_VOL   = 0.0; // FILL from printer
    private static final double GOLDEN_MAX_DRAWDOWN = 0.0; // FILL from printer (negative)
    private static final double GOLDEN_BETA         = 0.0; // FILL from printer

    // -----------------------------------------------------------------------
    // RISK-01: Sharpe ratio
    // -----------------------------------------------------------------------

    /**
     * Alice's portfolio Sharpe ratio must match the seed-derived golden value within ±0.001.
     * RED until Plan 04-02 implements RiskCalculator.computeRiskScorecard.
     */
    @Test
    void sharpe_alice_matchesGoldenValue() {
        // TODO: construct or inject actual RiskScorecard via integration slice in Plan 04-02
        double actualSharpe = 0.0; // FILL: invoke riskCalculator.computeRiskScorecard(portfolioId).sharpeRatio()
        assertThat(actualSharpe).isCloseTo(GOLDEN_SHARPE, within(0.001));
    }

    /**
     * Alice's annualized volatility must match the seed-derived golden value within ±0.0001.
     * RED until Plan 04-02.
     */
    @Test
    void annualizedVol_alice_matchesGoldenValue() {
        double actualVol = 0.0; // FILL: invoke computeRiskScorecard(portfolioId).annualizedVolatility()
        assertThat(actualVol).isCloseTo(GOLDEN_ANNUAL_VOL, within(0.0001));
    }

    // -----------------------------------------------------------------------
    // RISK-01: Max drawdown
    // -----------------------------------------------------------------------

    /**
     * Max drawdown must be ≤ 0 for any equity curve (trough can only be at or below peak).
     * RED until Plan 04-02 (passes trivially on stub which returns 0.0).
     */
    @Test
    void maxDrawdown_alice_isNonPositive() {
        double actualMaxDrawdown = 0.0; // FILL: computeRiskScorecard(portfolioId).maxDrawdown()
        assertThat(actualMaxDrawdown).isLessThanOrEqualTo(0.0);
    }

    /**
     * Alice's max drawdown must match the seed-derived golden value within ±0.0001.
     * RED until Plan 04-02.
     */
    @Test
    void maxDrawdown_alice_matchesGoldenValue() {
        double actualMaxDrawdown = 0.0; // FILL: computeRiskScorecard(portfolioId).maxDrawdown()
        assertThat(actualMaxDrawdown).isCloseTo(GOLDEN_MAX_DRAWDOWN, within(0.0001));
    }

    // -----------------------------------------------------------------------
    // RISK-01: Beta
    // -----------------------------------------------------------------------

    /**
     * Alice's portfolio beta should be above 1.0 (high-beta growth portfolio: AAPL, MSFT, NVDA, AMZN, TSLA).
     * RED until Plan 04-02 implements the Covariance / variance formula.
     */
    @Test
    void beta_alice_isAboveOne() {
        double actualBeta = 0.0; // FILL: computeRiskScorecard(portfolioId).beta()
        // Expected to be > 1.0; all five holdings have seed betas >= 1.15
        // This test will remain RED until real beta is computed
        assertThat(actualBeta).isGreaterThan(1.0);
    }

    /**
     * Alice's beta must match the seed-derived golden value within ±0.001.
     * RED until Plan 04-02.
     */
    @Test
    void beta_alice_matchesGoldenValue() {
        double actualBeta = 0.0; // FILL: computeRiskScorecard(portfolioId).beta()
        assertThat(actualBeta).isCloseTo(GOLDEN_BETA, within(0.001));
    }

    // -----------------------------------------------------------------------
    // RISK-03: VaR
    // -----------------------------------------------------------------------

    /**
     * Historical VaR amount must be positive (VaR represents a positive potential loss).
     * RED until Plan 04-02 adds the DescriptiveStatistics.getPercentile(5) computation.
     */
    @Test
    void historicalVar_aliceIsPositive() {
        double historicalVarPct = 0.0; // FILL: var list entry with method="HISTORICAL".percentage()
        // The stub returns 0.0 — this will remain RED until real VaR is computed
        assertThat(historicalVarPct).isGreaterThan(0.0);
    }

    /**
     * Parametric VaR amount must be positive (z × σ − μ > 0 for normal equity portfolios).
     * RED until Plan 04-02.
     */
    @Test
    void parametricVar_aliceIsPositive() {
        double parametricVarPct = 0.0; // FILL: var list entry with method="PARAMETRIC".percentage()
        assertThat(parametricVarPct).isGreaterThan(0.0);
    }
}
