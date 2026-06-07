package com.quantlens.portfolio;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import com.quantlens.portfolio.domain.Transaction;
import com.quantlens.portfolio.domain.TransactionRepository;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Golden-value capture test — prints alice's exact seed-derived constants so they
 * can be baked into assertion constants in Plans 02–04.
 * <p>
 * <strong>This test is {@code @Disabled} and MUST remain disabled in CI.</strong>
 * Enable it once manually on a running Testcontainers DB to print the golden values,
 * then record them as constants in {@code PortfolioControllerIntegrationTest}.
 * <p>
 * The seed is fully deterministic (MersenneTwister seed=42, SERIES_START=2022-09-12)
 * so values printed by this test will be identical on every machine.
 */
class GoldenValuePrinterTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PortfolioRepository portfolioRepository;

    @Autowired
    private PositionRepository positionRepository;

    @Autowired
    private OhlcvBarRepository ohlcvBarRepository;

    @Autowired
    private SecurityRepository securityRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    @Disabled("Golden-value capture — enable once to print seed constants, then re-disable")
    @Transactional(readOnly = true)
    void printAliceSeedConstants() {
        System.out.println("\n=======================================================");
        System.out.println("  GOLDEN VALUE PRINTER — alice (seed=42, 2022-09-12)");
        System.out.println("=======================================================\n");

        // --- Resolve alice's portfolio ---
        AppUser alice = appUserRepository.findByUsername("alice")
                .orElseThrow(() -> new IllegalStateException("alice not found in seed data"));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(alice.getId());
        if (portfolios.isEmpty()) {
            throw new IllegalStateException("alice has no portfolio in seed data");
        }
        Portfolio portfolio = portfolios.get(0);
        System.out.println("Alice user ID   : " + alice.getId());
        System.out.println("Alice portfolio : " + portfolio.getId() + " — " + portfolio.getName());
        System.out.println();

        // --- Positions ---
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolio.getId());
        System.out.println("--- Positions (" + positions.size() + ") ---");
        List<Long> securityIds = positions.stream()
                .map(p -> p.getSecurity().getId())
                .toList();

        // Latest closes for current market values
        List<OhlcvBar> latestBars = ohlcvBarRepository.findLatestBarBySecurityIds(securityIds);
        java.util.Map<Long, BigDecimal> latestCloseMap = new java.util.HashMap<>();
        for (OhlcvBar bar : latestBars) {
            latestCloseMap.put(bar.getSecurity().getId(), bar.getClosePrice());
        }

        BigDecimal totalMarketValue = BigDecimal.ZERO;
        BigDecimal totalCostBasis   = BigDecimal.ZERO;
        for (Position pos : positions) {
            Security sec = pos.getSecurity();
            BigDecimal close    = latestCloseMap.getOrDefault(sec.getId(), BigDecimal.ZERO);
            BigDecimal mktValue = pos.getQuantity().multiply(close).setScale(2, RoundingMode.HALF_UP);
            BigDecimal costBas  = pos.getQuantity().multiply(pos.getAvgCostBasis()).setScale(2, RoundingMode.HALF_UP);
            totalMarketValue = totalMarketValue.add(mktValue);
            totalCostBasis   = totalCostBasis.add(costBas);

            System.out.printf("  %-6s  qty=%-10s  avgCostBasis=%-12s  latestClose=%-12s  mktValue=%-12s%n",
                    sec.getTicker(),
                    pos.getQuantity().toPlainString(),
                    pos.getAvgCostBasis().toPlainString(),
                    close.toPlainString(),
                    mktValue.toPlainString());
        }
        System.out.println();
        System.out.println("Total market value   : " + totalMarketValue.setScale(2, RoundingMode.HALF_UP));
        System.out.println("Total cost basis     : " + totalCostBasis.setScale(2, RoundingMode.HALF_UP));
        BigDecimal unrealizedGain = totalMarketValue.subtract(totalCostBasis);
        System.out.println("Total unrealized P&L : " + unrealizedGain.setScale(2, RoundingMode.HALF_UP));
        System.out.println();

        // --- SPX500 benchmark first and last bar ---
        List<Security> benchmarks = securityRepository.findByBenchmarkTrue();
        System.out.println("--- Benchmark securities (isBenchmark=true): " + benchmarks.size() + " ---");
        for (Security bm : benchmarks) {
            System.out.println("  Benchmark: " + bm.getTicker() + " — " + bm.getName() + " (id=" + bm.getId() + ")");
            List<OhlcvBar> bmBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(List.of(bm.getId()));
            if (!bmBars.isEmpty()) {
                OhlcvBar first = bmBars.get(0);
                OhlcvBar last  = bmBars.get(bmBars.size() - 1);
                System.out.println("  Bar count   : " + bmBars.size());
                System.out.println("  First bar   : date=" + first.getBarDate() + "  close=" + first.getClosePrice());
                System.out.println("  Last bar    : date=" + last.getBarDate()  + "  close=" + last.getClosePrice());
            }
        }
        System.out.println();

        // --- Transactions ---
        List<Transaction> transactions = transactionRepository.findByPortfolioIdChronological(portfolio.getId());
        System.out.println("--- Transactions (" + transactions.size() + ") ---");
        for (Transaction tx : transactions) {
            System.out.printf("  %s  %-4s  %-6s  qty=%-10s  price=%-12s%n",
                    tx.getTxDate(),
                    tx.getTxType(),
                    tx.getSecurity().getTicker(),
                    tx.getQuantity().toPlainString(),
                    tx.getPrice().toPlainString());
        }
        System.out.println();

        // --- OhlcvBar count per position security ---
        System.out.println("--- OhlcvBar counts per security ---");
        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds);
        java.util.Map<String, Long> barCounts = new java.util.LinkedHashMap<>();
        for (OhlcvBar bar : allBars) {
            barCounts.merge(bar.getSecurity().getTicker(), 1L, Long::sum);
        }
        barCounts.forEach((ticker, count) ->
                System.out.println("  " + ticker + ": " + count + " bars"));
        System.out.println();

        System.out.println("=======================================================");
        System.out.println("  Copy the above values into PortfolioControllerIntegrationTest");
        System.out.println("  as static final constants for golden-value assertions.");
        System.out.println("=======================================================\n");
    }
}
