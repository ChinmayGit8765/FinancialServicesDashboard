package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.api.RiskScorecardDto;
import com.quantlens.analytics.api.VarResultDto;
import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for RiskCalculator — REGRESSION anchors.
 * <p>
 * Uses the seeded Testcontainers Postgres database (MersenneTwister seed=42,
 * SERIES_START=2022-09-12) — results are fully deterministic.
 * <p>
 * Golden-value constants recaptured from AnalyticsGoldenValuePrinterTest on 2026-06-08
 * after applying CR-01 (Sharpe denominator), CR-02 (beta date alignment), and
 * CR-03 (FF date-based factor lookup) fixes. Values are unchanged from pre-fix run
 * because: (a) the seeded RF series is near-constant so std(excess)≈std(portfolio)
 * within ±0.001 tolerance; (b) the seeded benchmark and portfolio share the same
 * start date so date-alignment and trimToSameLength give the same result.
 * <p>
 * These tests are REGRESSION anchors — they detect regressions in the seed path.
 * They cannot detect systematic formula bugs on their own because the expected
 * values are derived from the same implementation. For correctness anchors, see
 * {@link RiskMathHandComputedTest} which uses independently derived expected values.
 * <p>
 * Tolerances from 04-RESEARCH.md tolerance table:
 * <ul>
 *   <li>Sharpe: ±0.001</li>
 *   <li>Annualized vol: ±0.0001</li>
 *   <li>Max drawdown: ±0.0001</li>
 *   <li>Beta: ±0.001</li>
 *   <li>Historical VaR amount: ±0.01 currency units</li>
 * </ul>
 */
class RiskCalculatorTest extends AbstractPostgresIntegrationTest {

    // -----------------------------------------------------------------------
    // Golden-value constants — captured from AnalyticsGoldenValuePrinterTest 2026-06-08
    // seed=42, SERIES_START=2022-09-12, alice's Growth Portfolio
    // -----------------------------------------------------------------------

    private static final double GOLDEN_SHARPE       = 0.36442669;
    private static final double GOLDEN_ANNUAL_VOL   = 0.34621361;
    private static final double GOLDEN_MAX_DRAWDOWN = -0.33891522;
    private static final double GOLDEN_BETA         = 1.94917921;
    // Historical VaR monetary amount (printer: HIST_VAR_PCT=0.03693268 × currentPortfolioValue)
    private static final double GOLDEN_HIST_VAR_AMOUNT = 1464.52;

    @Autowired
    private RiskCalculator riskCalculator;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    private Long portfolioId;
    private RiskScorecardDto scorecard;

    @BeforeEach
    @Transactional
    void resolveAlicePortfolio() {
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(alice.getId());
        assertThat(portfolios).as("alice must have at least one portfolio").isNotEmpty();
        portfolioId = portfolios.get(0).getId();
        scorecard = riskCalculator.computeRiskScorecard(portfolioId);
    }

    // -----------------------------------------------------------------------
    // RISK-01: Sharpe ratio
    // -----------------------------------------------------------------------

    /**
     * Alice's portfolio Sharpe ratio must match the seed-derived golden value within ±0.001.
     * Formula: mean(daily excess log returns) / std(daily log returns) × √252
     */
    @Test
    void sharpe_alice_matchesGoldenValue() {
        assertThat(scorecard.sharpeRatio())
                .as("Sharpe ratio must match golden value within ±0.001")
                .isCloseTo(GOLDEN_SHARPE, within(0.001));
    }

    /**
     * Alice's annualized volatility must match the seed-derived golden value within ±0.0001.
     * Formula: std(daily log returns) × √252
     */
    @Test
    void annualizedVol_alice_matchesGoldenValue() {
        assertThat(scorecard.annualizedVolatility())
                .as("Annualized volatility must match golden value within ±0.0001")
                .isCloseTo(GOLDEN_ANNUAL_VOL, within(0.0001));
    }

    // -----------------------------------------------------------------------
    // RISK-01: Max drawdown
    // -----------------------------------------------------------------------

    /**
     * Max drawdown must be ≤ 0 for any equity curve (trough can only be at or below peak).
     */
    @Test
    void maxDrawdown_alice_isNonPositive() {
        assertThat(scorecard.maxDrawdown())
                .as("Max drawdown must be ≤ 0")
                .isLessThanOrEqualTo(0.0);
    }

    /**
     * Alice's max drawdown must match the seed-derived golden value within ±0.0001.
     */
    @Test
    void maxDrawdown_alice_matchesGoldenValue() {
        assertThat(scorecard.maxDrawdown())
                .as("Max drawdown must match golden value within ±0.0001")
                .isCloseTo(GOLDEN_MAX_DRAWDOWN, within(0.0001));
    }

    // -----------------------------------------------------------------------
    // RISK-01: Beta
    // -----------------------------------------------------------------------

    /**
     * Alice's portfolio beta should be above 1.0 (high-beta growth portfolio: AAPL, MSFT, NVDA, AMZN, TSLA).
     * All five holdings have seed betas >= 1.15.
     */
    @Test
    void beta_alice_isAboveOne() {
        assertThat(scorecard.beta())
                .as("Beta must be > 1.0 for alice's high-beta Growth portfolio")
                .isGreaterThan(1.0);
    }

    /**
     * Alice's beta must match the seed-derived golden value within ±0.001.
     * Formula: cov(portfolio returns, SPX500 returns) / var(SPX500 returns)
     */
    @Test
    void beta_alice_matchesGoldenValue() {
        assertThat(scorecard.beta())
                .as("Beta must match golden value within ±0.001")
                .isCloseTo(GOLDEN_BETA, within(0.001));
    }

    // -----------------------------------------------------------------------
    // RISK-03: VaR
    // -----------------------------------------------------------------------

    /**
     * Historical VaR percentage must be positive (VaR represents a positive potential loss).
     * Derived from -getPercentile(5.0) which is positive for a loss-incurring 5th percentile.
     */
    @Test
    void historicalVar_alice_isPositive() {
        Optional<VarResultDto> histVar = scorecard.var().stream()
                .filter(v -> "HISTORICAL".equals(v.method()))
                .findFirst();
        assertThat(histVar).as("HISTORICAL VaR entry must be present").isPresent();
        assertThat(histVar.get().percentage())
                .as("HISTORICAL VaR percentage must be > 0")
                .isGreaterThan(0.0);
        assertThat(histVar.get().amount())
                .as("HISTORICAL VaR amount must be positive")
                .isPositive();
    }

    /**
     * Parametric VaR percentage must be positive (1.645 × σ − μ > 0 for normal equity portfolios).
     */
    @Test
    void parametricVar_alice_isPositive() {
        Optional<VarResultDto> paramVar = scorecard.var().stream()
                .filter(v -> "PARAMETRIC".equals(v.method()))
                .findFirst();
        assertThat(paramVar).as("PARAMETRIC VaR entry must be present").isPresent();
        assertThat(paramVar.get().percentage())
                .as("PARAMETRIC VaR percentage must be > 0")
                .isGreaterThan(0.0);
        assertThat(paramVar.get().amount())
                .as("PARAMETRIC VaR amount must be positive")
                .isPositive();
    }

    /**
     * Historical VaR amount must match the seed-derived golden value within ±0.01 currency units.
     */
    @Test
    void historicalVarAmount_alice_matchesGolden() {
        Optional<VarResultDto> histVar = scorecard.var().stream()
                .filter(v -> "HISTORICAL".equals(v.method()))
                .findFirst();
        assertThat(histVar).as("HISTORICAL VaR entry must be present").isPresent();
        assertThat(histVar.get().amount().doubleValue())
                .as("HISTORICAL VaR amount must match golden value ±0.01")
                .isCloseTo(GOLDEN_HIST_VAR_AMOUNT, within(0.01));
    }

    /**
     * VaR list must contain entries labelled HISTORICAL and PARAMETRIC with confidence=0.95, horizonDays=1.
     */
    @Test
    void var_alice_hasCorrectLabels() {
        List<VarResultDto> varList = scorecard.var();
        assertThat(varList).as("var list must contain at least 2 entries").hasSizeGreaterThanOrEqualTo(2);

        boolean hasHistorical = varList.stream().anyMatch(v -> "HISTORICAL".equals(v.method()));
        boolean hasParametric = varList.stream().anyMatch(v -> "PARAMETRIC".equals(v.method()));
        assertThat(hasHistorical).as("var list must contain a HISTORICAL entry").isTrue();
        assertThat(hasParametric).as("var list must contain a PARAMETRIC entry").isTrue();

        varList.forEach(v -> {
            assertThat(v.confidence()).as("VaR confidence must be 0.95").isCloseTo(0.95, within(1e-9));
            assertThat(v.horizonDays()).as("VaR horizonDays must be 1").isEqualTo(1);
        });
    }
}
