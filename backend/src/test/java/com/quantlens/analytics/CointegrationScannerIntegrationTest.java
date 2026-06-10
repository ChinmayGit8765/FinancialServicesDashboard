package com.quantlens.analytics;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.analytics.api.PairResultDto;
import com.quantlens.analytics.service.CointegrationScanner;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving the seeded COP↔XOM cointegrated pair is detected end-to-end for Bob's
 * Income portfolio — the demo's pairs-trading signal.
 * <p>
 * COP is seeded as an Ornstein-Uhlenbeck stationary-spread partner of XOM
 * ({@code GbmGenerator.generateCointegratedPartner}), so the spread is genuinely stationary and the
 * UNCHANGED Engle-Granger/ADF {@link CointegrationScanner} detects the pair at 95% (p &lt; 0.05) —
 * no threshold loosening. Ordinary shared-factor-GBM holdings (XOM↔CVX) remain non-cointegrated.
 */
class CointegrationScannerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private CointegrationScanner scanner;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Test
    @Transactional
    void bobIncomePortfolio_detectsSeededCopXomPair() {
        AppUser bob = appUserRepository.findByUsername("bob")
                .orElseThrow(() -> new IllegalStateException("bob not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(bob.getId());
        assertThat(portfolios).as("bob must have an Income portfolio").isNotEmpty();

        List<PairResultDto> pairs = scanner.scanPairs(portfolios.get(0).getId());

        assertThat(pairs)
                .as("Bob's Income portfolio must surface the seeded cointegrated pair")
                .isNotEmpty();

        PairResultDto copXom = pairs.stream()
                .filter(p -> (p.tickerX().equals("COP") && p.tickerY().equals("XOM"))
                          || (p.tickerX().equals("XOM") && p.tickerY().equals("COP")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected a COP↔XOM pair; got: " + pairs));

        assertThat(copXom.pValue())
                .as("COP↔XOM must be cointegrated at 95%% (ADF MacKinnon p < 0.05)")
                .isLessThan(0.05);
        assertThat(copXom.hedgeRatio())
                .as("hedge ratio (OLS β on log-prices) should be a sensible positive number near 1")
                .isGreaterThan(0.0);
    }
}
