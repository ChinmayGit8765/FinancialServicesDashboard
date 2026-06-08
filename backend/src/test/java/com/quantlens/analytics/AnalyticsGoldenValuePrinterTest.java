package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.service.CointegrationScanner;
import com.quantlens.analytics.service.CorrelationCalculator;
import com.quantlens.analytics.service.FamaFrenchCalculator;
import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.domain.AppUser;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Golden-value capture test — prints alice's exact seed-derived risk constants
 * so they can be baked into assertion constants in RiskCalculatorTest and
 * AnalyticsControllerIntegrationTest.
 * <p>
 * <strong>This test is {@code @Disabled} and MUST remain disabled in CI.</strong>
 * Enable it once manually after Plans 04-02 and 04-03 implement the real math:
 * {@code .\mvnw.cmd test -Dtest=AnalyticsGoldenValuePrinterTest}
 * <p>
 * The seed is fully deterministic (MersenneTwister seed=42, SERIES_START=2022-09-12)
 * so values printed by this test will be identical on every machine.
 */
class AnalyticsGoldenValuePrinterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    RiskCalculator riskCalculator;

    @Autowired
    CorrelationCalculator correlationCalculator;

    @Autowired
    FamaFrenchCalculator ffCalc;

    @Autowired
    CointegrationScanner scanner;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Test
    @Disabled("Golden-value capture — enable once to print seed constants, then re-disable")
    @Transactional(readOnly = true)
    void printGoldenValues_aliceGrowthPortfolio() {
        System.out.println("\n=======================================================");
        System.out.println("  ANALYTICS GOLDEN VALUE PRINTER — alice (seed=42, 2022-09-12)");
        System.out.println("=======================================================\n");

        // --- Resolve alice's portfolio (same pattern as GoldenValuePrinterTest) ---
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(alice.getId());
        if (portfolios.isEmpty()) {
            throw new IllegalStateException("alice has no portfolio in seed data");
        }
        Long portfolioId = portfolios.get(0).getId();
        System.out.println("Alice user ID   : " + alice.getId());
        System.out.println("Alice portfolio : " + portfolioId + " — " + portfolios.get(0).getName());
        System.out.println();

        // --- Risk scorecard ---
        var scorecard = riskCalculator.computeRiskScorecard(portfolioId);
        System.out.printf("SHARPE=%.8f%n",                scorecard.sharpeRatio());
        System.out.printf("ANNUAL_VOL=%.8f%n",            scorecard.annualizedVolatility());
        System.out.printf("MAX_DRAWDOWN=%.8f%n",          scorecard.maxDrawdown());
        System.out.printf("BETA=%.8f%n",                  scorecard.beta());

        scorecard.var().forEach(v ->
            System.out.printf("VAR_%s_PCT=%.8f  AMOUNT=%s%n",
                    v.method(), v.percentage(), v.amount()));
        System.out.println();

        // --- VaR percentages ---
        scorecard.var().stream()
                .filter(v -> "HISTORICAL".equals(v.method()))
                .findFirst()
                .ifPresent(v -> System.out.printf("HIST_VAR_PCT=%.8f%n", v.percentage()));
        scorecard.var().stream()
                .filter(v -> "PARAMETRIC".equals(v.method()))
                .findFirst()
                .ifPresent(v -> System.out.printf("PARAM_VAR_PCT=%.8f%n", v.percentage()));
        System.out.println();

        // --- Correlation matrix (AAPL-MSFT golden value) ---
        var corrDto = correlationCalculator.computeCorrelationMatrix(portfolioId);
        int aaplIdx = corrDto.tickers().indexOf("AAPL");
        int msftIdx = corrDto.tickers().indexOf("MSFT");
        System.out.printf("CORRELATION_TICKERS=%s%n", corrDto.tickers());
        if (aaplIdx >= 0 && msftIdx >= 0) {
            System.out.printf("CORR_AAPL_MSFT=%.8f%n", corrDto.matrix().get(aaplIdx).get(msftIdx));
        }
        System.out.println();

        // --- Fama-French attribution ---
        var attr = ffCalc.computeAttribution(portfolioId);
        System.out.printf("ALPHA_ANNUALIZED=%.8f%n",      attr.alphaAnnualized());
        System.out.printf("BETA_MKT=%.8f%n",              attr.betaMkt());
        System.out.printf("BETA_SMB=%.8f%n",              attr.betaSmb());
        System.out.printf("BETA_HML=%.8f%n",              attr.betaHml());
        System.out.printf("R_SQUARED=%.8f%n",             attr.rSquared());
        System.out.printf("CONTRIB_MKT_ANN=%.8f%n",       attr.contribMktAnnualized());
        System.out.printf("CONTRIB_SMB_ANN=%.8f%n",       attr.contribSmbAnnualized());
        System.out.printf("CONTRIB_HML_ANN=%.8f%n",       attr.contribHmlAnnualized());
        System.out.println();

        // --- Pairs (cointegration scanner) ---
        var pairs = scanner.scanPairs(portfolioId);
        System.out.println("PAIRS_COUNT=" + pairs.size());
        pairs.forEach(p ->
            System.out.printf("PAIR %s/%s  hedge=%.8f  adf=%.8f  pValue=%.8f  zScore=%.8f  signal=%s%n",
                    p.tickerY(), p.tickerX(),
                    p.hedgeRatio(), p.adfStatistic(), p.pValue(),
                    p.spreadZScore(), p.signal()));
        System.out.println();

        // --- ADF sanity check (MacKinnon p-value for known tau=-3.0) ---
        // TODO: call CointegrationScanner.mackinnonPValue(-3.0) once it is package-accessible in Plan 04-03
        // Expected: ≈ 0.034 ± 0.01
        // System.out.printf("MACKINNON_PVALUE_TAU_NEG3=%.8f%n", CointegrationScanner.mackinnonPValue(-3.0));

        System.out.println("=======================================================");
        System.out.println("  Copy the above constants into RiskCalculatorTest and");
        System.out.println("  AnalyticsControllerIntegrationTest as GOLDEN_* fields.");
        System.out.println("=======================================================\n");
    }
}
