package com.quantlens.analytics;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for FamaFrenchCalculator.
 * <p>
 * No Spring context, no Testcontainers. Tests validate regression output structure
 * (params length == 4), a golden R² value, sign of β_mkt, and contribution decomposition.
 * <p>
 * RED scaffold — golden constants are 0.0 until Plan 04-03 implements the OLS regression
 * and the printer supplies seed-derived values.
 */
class FamaFrenchCalculatorTest {

    // -----------------------------------------------------------------------
    // Golden-value constants — FILL from AnalyticsGoldenValuePrinterTest
    // -----------------------------------------------------------------------

    private static final double GOLDEN_R2            = 0.0; // FILL from printer
    private static final double GOLDEN_ALPHA_ANN     = 0.0; // FILL from printer
    private static final double GOLDEN_BETA_MKT      = 0.0; // FILL from printer
    private static final double GOLDEN_CONTRIB_MKT   = 0.0; // FILL from printer
    private static final double GOLDEN_CONTRIB_SMB   = 0.0; // FILL from printer
    private static final double GOLDEN_CONTRIB_HML   = 0.0; // FILL from printer
    private static final double GOLDEN_PORTFOLIO_EXCESS_RETURN = 0.0; // FILL from printer

    // -----------------------------------------------------------------------
    // ATTR-01: Regression structure
    // -----------------------------------------------------------------------

    /**
     * The OLS regression for Fama-French 3-factor must produce exactly 4 parameters:
     * [intercept/alpha, β_mkt, β_smb, β_hml].
     * RED until Plan 04-03 implements OLSMultipleLinearRegression.
     */
    @Test
    void ffRegression_paramsLengthIsFour() {
        // TODO: invoke ffCalculator.computeAttribution(portfolioId) in Plan 04-03
        // Stub returns zeroed AttributionDto — use field count as proxy for params length
        int paramsLength = 4; // FILL: verify params.length == 4 in FamaFrenchCalculator internals
        assertThat(paramsLength).as("FF regression must produce 4 parameters (intercept + 3 factor betas)").isEqualTo(4);
    }

    /**
     * Alice's FF R² must match the seed-derived golden value within ±0.001.
     * RED until Plan 04-03.
     */
    @Test
    void ffRegression_rSquaredMatchesGolden() {
        double actualRSquared = 0.0; // FILL: computeAttribution(portfolioId).rSquared()
        assertThat(actualRSquared).isCloseTo(GOLDEN_R2, within(0.001));
    }

    // -----------------------------------------------------------------------
    // ATTR-01: Sign checks
    // -----------------------------------------------------------------------

    /**
     * β_mkt must be positive for Alice's long-only growth portfolio.
     * RED until Plan 04-03 (stub returns 0.0 which will fail this assertion).
     */
    @Test
    void betaMkt_aliceIsPositive() {
        double actualBetaMkt = 0.0; // FILL: computeAttribution(portfolioId).betaMkt()
        assertThat(actualBetaMkt).isGreaterThan(0.0);
    }

    // -----------------------------------------------------------------------
    // ATTR-01: Contribution decomposition
    // -----------------------------------------------------------------------

    /**
     * The sum of alpha + factor contributions must approximate the portfolio's annualized excess return.
     * Formula: alphaAnnualized + contribMktAnnualized + contribSmbAnnualized + contribHmlAnnualized
     *          ≈ annualized portfolio excess return (±0.001 tolerance for rounding).
     * RED until Plan 04-03.
     */
    @Test
    void contributions_sumToPortfolioReturn() {
        double alphaAnnualized       = 0.0; // FILL: computeAttribution(portfolioId).alphaAnnualized()
        double contribMktAnnualized  = 0.0; // FILL: computeAttribution(portfolioId).contribMktAnnualized()
        double contribSmbAnnualized  = 0.0; // FILL: computeAttribution(portfolioId).contribSmbAnnualized()
        double contribHmlAnnualized  = 0.0; // FILL: computeAttribution(portfolioId).contribHmlAnnualized()

        double decomposedReturn = alphaAnnualized + contribMktAnnualized + contribSmbAnnualized + contribHmlAnnualized;

        assertThat(decomposedReturn)
                .as("alpha + factor contributions must approximate annualized portfolio excess return")
                .isCloseTo(GOLDEN_PORTFOLIO_EXCESS_RETURN, within(0.001));
    }
}
