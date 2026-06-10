package com.quantlens.seed;

import com.quantlens.marketdata.domain.FactorReturn;
import com.quantlens.marketdata.domain.FactorReturnRepository;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.AppUser;
import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import com.quantlens.portfolio.domain.Transaction;
import com.quantlens.portfolio.domain.TransactionRepository;
import com.quantlens.seed.GbmGenerator.OhlcvResult;
import com.quantlens.seed.GbmGenerator.OhlcvRow;
import com.quantlens.seed.GbmGenerator.SecuritySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Idempotent seed runner that populates demo data on first application start.
 *
 * <h2>Idempotence</h2>
 * Guarded by a {@code seed_log} row with {@code id="v1"}.  The flag is set
 * {@code completed=true} at the END of the single {@link Transactional} run,
 * so a partial seed (e.g. JVM crash halfway through) rolls back entirely and
 * will re-run cleanly on the next restart.
 *
 * <h2>Ordering</h2>
 * {@code @Order(1)} ensures this runner executes before any other
 * {@link ApplicationRunner} or {@link org.springframework.boot.CommandLineRunner}
 * that might depend on seed data.
 *
 * <h2>PasswordEncoder</h2>
 * The {@link PasswordEncoder} is constructor-injected from the single
 * {@link PasswordEncoderConfig#passwordEncoder()} bean.  It is NEVER
 * instantiated directly here ({@code new BCryptPasswordEncoder()}) — that
 * would risk a strength mismatch with the bean used by Spring Security login.
 */
@Component
@Order(1)
public class SeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedRunner.class);

    // v2: added the COP↔XOM cointegrated Energy pair to Bob's Income portfolio.
    // (Bump forces a re-seed; the idempotence guard skips seeding once a version is completed.)
    private static final String SEED_VERSION = "v2";

    /** Dedicated RNG seed for the cointegrated partner — independent of the main price stream. */
    private static final long PARTNER_RNG_SEED = 1042L;

    // Trading-day calendar anchor: first Monday of 2023 series (~504 days to ~end 2024)
    private static final LocalDate SERIES_START = LocalDate.of(2022, 9, 12);

    /**
     * Demo password injected from {@code quantlens.demo.password} (default: demo1234).
     * Override with {@code QUANTLENS_DEMO_PASSWORD} env var.  Kept out of compiled
     * bytecode as a static final — only materialized at runtime via the Spring
     * property system.
     */
    @Value("${quantlens.demo.password:demo1234}")
    private String demoPassword;

    private final SeedLogRepository seedLogRepository;
    private final SecurityRepository securityRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final FactorReturnRepository factorReturnRepository;
    private final AppUserRepository appUserRepository;
    private final PortfolioRepository portfolioRepository;
    private final PositionRepository positionRepository;
    private final TransactionRepository transactionRepository;
    private final GbmGenerator gbmGenerator;
    private final PasswordEncoder passwordEncoder;

    public SeedRunner(SeedLogRepository seedLogRepository,
                      SecurityRepository securityRepository,
                      OhlcvBarRepository ohlcvBarRepository,
                      FactorReturnRepository factorReturnRepository,
                      AppUserRepository appUserRepository,
                      PortfolioRepository portfolioRepository,
                      PositionRepository positionRepository,
                      TransactionRepository transactionRepository,
                      GbmGenerator gbmGenerator,
                      PasswordEncoder passwordEncoder) {
        this.seedLogRepository = seedLogRepository;
        this.securityRepository = securityRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.factorReturnRepository = factorReturnRepository;
        this.appUserRepository = appUserRepository;
        this.portfolioRepository = portfolioRepository;
        this.positionRepository = positionRepository;
        this.transactionRepository = transactionRepository;
        this.gbmGenerator = gbmGenerator;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean alreadyDone = seedLogRepository.findById(SEED_VERSION)
                .map(SeedLog::isCompleted)
                .orElse(false);
        if (alreadyDone) {
            log.info("SeedRunner: seed_log {} already completed — skipping", SEED_VERSION);
            return;
        }

        log.info("SeedRunner: starting data seed (seed_log {} not yet completed)...", SEED_VERSION);

        // 1. Build the securities universe + benchmark spec
        List<SecuritySpec> specs = buildSpecs();

        // 2. Generate OHLCV via correlated GBM (fixed seed 42).
        // OhlcvResult carries both the OHLCV rows and the market excess-return series
        // (passed directly into generateFactors — no shared mutable state on GbmGenerator).
        OhlcvResult ohlcvResult = gbmGenerator.generateOhlcv(specs, SERIES_START);
        List<List<OhlcvRow>> ohlcvData = ohlcvResult.rows();

        // 3. Persist securities and their OHLCV bars
        // The last spec is the benchmark (is_benchmark=true)
        List<Security> securities = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            SecuritySpec spec = specs.get(i);
            boolean isBenchmark = spec.ticker().equals("SPX500");
            Security sec = securityRepository.save(
                    new Security(spec.ticker(), nameFor(spec.ticker()),
                                 sectorFor(spec.ticker()), isBenchmark));
            securities.add(sec);

            List<OhlcvRow> rows = ohlcvData.get(i);
            List<OhlcvBar> bars = new ArrayList<>(rows.size());
            for (OhlcvRow row : rows) {
                bars.add(new OhlcvBar(sec, row.date(),
                        row.open(), row.high(), row.low(), row.close(), row.volume()));
            }
            ohlcvBarRepository.saveAll(bars);
        }
        int totalBars = ohlcvData.stream().mapToInt(List::size).sum();
        log.info("SeedRunner: saved {} securities with {} total OHLCV bars",
                securities.size(), totalBars);

        // 4. Persist Fama-French factor returns. Pass mktExcessReturns from OhlcvResult
        // so GbmGenerator stays stateless — no side-channel through instance fields.
        List<GbmGenerator.FactorRow> factorRows =
                gbmGenerator.generateFactors(ohlcvResult.mktExcessReturns(), SERIES_START);
        List<FactorReturn> factors = new ArrayList<>(factorRows.size());
        for (GbmGenerator.FactorRow fr : factorRows) {
            factors.add(new FactorReturn(fr.date(), fr.mktRf(), fr.smb(), fr.hml(), fr.rf()));
        }
        factorReturnRepository.saveAll(factors);
        log.info("SeedRunner: saved {} factor_returns rows", factors.size());

        // 5. Seed demo users (BCrypt-hashed via the injected PasswordEncoder bean)
        String hashedPassword = passwordEncoder.encode(demoPassword);
        AppUser alice   = appUserRepository.save(new AppUser("alice",   hashedPassword, "Growth"));
        AppUser bob     = appUserRepository.save(new AppUser("bob",     hashedPassword, "Income"));
        AppUser charlie = appUserRepository.save(new AppUser("charlie", hashedPassword, "Balanced"));
        log.info("SeedRunner: saved 3 demo users (alice/Growth, bob/Income, charlie/Balanced)");

        // Build a ticker→Security map for easy portfolio construction
        Map<String, Security> byTicker = new java.util.HashMap<>();
        for (Security sec : securities) {
            byTicker.put(sec.getTicker(), sec);
        }

        // 5b. Cointegrated Energy partner. COP is generated as a stationary-spread partner of XOM
        // (logCOP = alpha + 1.0*logXOM + AR(1) spread) using a DEDICATED RNG, so it does not perturb
        // any other security's prices (all existing golden values are preserved). It gives the
        // Engle-Granger/ADF CointegrationScanner a genuine, mathematically-valid cointegrated pair to
        // detect in Bob's Income portfolio — no scanner change, no threshold loosening.
        List<OhlcvRow> xomRows = ohlcvData.get(specIndexOf(specs, "XOM"));
        List<OhlcvRow> copRows = gbmGenerator.generateCointegratedPartner(
                xomRows, 105.0, 1.0, 0.85, 0.02, PARTNER_RNG_SEED);
        Security cop = securityRepository.save(
                new Security("COP", nameFor("COP"), sectorFor("COP"), false));
        List<OhlcvBar> copBars = new ArrayList<>(copRows.size());
        for (OhlcvRow row : copRows) {
            copBars.add(new OhlcvBar(cop, row.date(),
                    row.open(), row.high(), row.low(), row.close(), row.volume()));
        }
        ohlcvBarRepository.saveAll(copBars);
        log.info("SeedRunner: saved cointegrated Energy partner COP (XOM-coupled) with {} bars",
                copBars.size());

        // First OHLCV date is the cost basis reference; last close is ~"current" price
        // We use the close of bar 250 (~halfway) as the early-buy cost basis
        // and bars 0–250 as "early" buys, 251–503 as "recent" activity

        // 6. Alice — Growth (tech-heavy)
        Portfolio alicePortfolio = portfolioRepository.save(
                new Portfolio(alice, "Alice's Growth Portfolio", "Growth"));
        seedGrowthPortfolio(alicePortfolio, byTicker, ohlcvData, specs);

        // 7. Bob — Income (financials + energy + staples)
        Portfolio bobPortfolio = portfolioRepository.save(
                new Portfolio(bob, "Bob's Income Portfolio", "Income"));
        seedIncomePortfolio(bobPortfolio, byTicker, ohlcvData, specs);
        // Add the cointegrated COP position so the scanner detects the COP↔XOM Energy pair for Bob.
        seedPositionWithHistory(bobPortfolio, cop, copRows, 45.0);

        // 8. Charlie — Balanced (mixed)
        Portfolio charliePortfolio = portfolioRepository.save(
                new Portfolio(charlie, "Charlie's Balanced Portfolio", "Balanced"));
        seedBalancedPortfolio(charliePortfolio, byTicker, ohlcvData, specs);

        log.info("SeedRunner: saved 3 portfolios with positions and transaction history");

        // 9. Mark seed complete (must be LAST — rolled back if anything above fails)
        SeedLog seedLog = new SeedLog(SEED_VERSION);
        seedLog.setCompleted(true);
        seedLog.setCompletedAt(LocalDateTime.now());
        seedLogRepository.save(seedLog);

        log.info("SeedRunner: seed complete — seed_log {} marked completed", SEED_VERSION);
    }

    // ── universe definition ───────────────────────────────────────────────────

    /**
     * Build the full security spec list.  Order is deterministic and matters
     * for the GBM RNG sequence: OHLCV universe first, then the benchmark last.
     */
    private List<SecuritySpec> buildSpecs() {
        return List.of(
            // Tech — higher beta + vol
            new SecuritySpec("AAPL",   180.0, 0.10, 0.28, 1.2),
            new SecuritySpec("MSFT",   410.0, 0.12, 0.25, 1.15),
            new SecuritySpec("NVDA",   450.0, 0.20, 0.45, 1.4),
            new SecuritySpec("AMZN",   180.0, 0.12, 0.30, 1.3),
            new SecuritySpec("GOOGL",  140.0, 0.10, 0.26, 1.2),
            // Financials — moderate beta
            new SecuritySpec("JPM",    195.0, 0.08, 0.22, 1.1),
            new SecuritySpec("BAC",     38.0, 0.07, 0.25, 1.05),
            // Energy — moderate beta, mean-reverting tendency modeled as lower mu
            new SecuritySpec("XOM",    110.0, 0.06, 0.24, 0.95),
            new SecuritySpec("CVX",    160.0, 0.06, 0.23, 0.90),
            // Healthcare — defensive, lower beta
            new SecuritySpec("JNJ",    160.0, 0.05, 0.18, 0.75),
            new SecuritySpec("PFE",     30.0, 0.04, 0.22, 0.70),
            // Consumer Staples — low beta + vol
            new SecuritySpec("PG",     155.0, 0.05, 0.16, 0.65),
            new SecuritySpec("KO",      60.0, 0.04, 0.15, 0.60),
            new SecuritySpec("WMT",     65.0, 0.06, 0.17, 0.65),
            // Auto — high beta + vol
            new SecuritySpec("TSLA",   225.0, 0.15, 0.55, 1.4),
            // Benchmark: market-factor proxy (S&P 500 synthetic); beta=1, sigma=market sigma
            new SecuritySpec("SPX500", 100.0, 0.07, 0.18, 1.0)
        );
    }

    // ── portfolio seeding helpers ─────────────────────────────────────────────

    /** Growth persona: tech-heavy. Tickers: AAPL, MSFT, NVDA, AMZN, TSLA. */
    private void seedGrowthPortfolio(Portfolio portfolio,
                                     Map<String, Security> byTicker,
                                     List<List<OhlcvRow>> ohlcvData,
                                     List<SecuritySpec> specs) {
        String[] tickers = {"AAPL", "MSFT", "NVDA", "AMZN", "TSLA"};
        double[] shares  = {50.0, 20.0, 30.0, 40.0, 15.0};
        seedPortfolioPositions(portfolio, byTicker, ohlcvData, specs, tickers, shares);
    }

    /** Income persona: financials + energy + consumer staples. */
    private void seedIncomePortfolio(Portfolio portfolio,
                                     Map<String, Security> byTicker,
                                     List<List<OhlcvRow>> ohlcvData,
                                     List<SecuritySpec> specs) {
        String[] tickers = {"JPM", "BAC", "XOM", "CVX", "PG", "KO", "WMT"};
        double[] shares  = {30.0, 80.0, 40.0, 25.0, 35.0, 60.0, 50.0};
        seedPortfolioPositions(portfolio, byTicker, ohlcvData, specs, tickers, shares);
    }

    /** Balanced persona: mixed across all sectors. */
    private void seedBalancedPortfolio(Portfolio portfolio,
                                       Map<String, Security> byTicker,
                                       List<List<OhlcvRow>> ohlcvData,
                                       List<SecuritySpec> specs) {
        String[] tickers = {"AAPL", "JPM", "XOM", "JNJ", "PG", "MSFT", "KO"};
        double[] shares  = {20.0, 20.0, 25.0, 20.0, 30.0, 15.0, 40.0};
        seedPortfolioPositions(portfolio, byTicker, ohlcvData, specs, tickers, shares);
    }

    /**
     * Create positions and a BUY (day 50) + optional partial SELL (day 200) history.
     */
    private void seedPortfolioPositions(Portfolio portfolio,
                                         Map<String, Security> byTicker,
                                         List<List<OhlcvRow>> ohlcvData,
                                         List<SecuritySpec> specs,
                                         String[] tickers,
                                         double[] shares) {
        for (int i = 0; i < tickers.length; i++) {
            String ticker = tickers[i];
            Security sec = byTicker.get(ticker);
            if (sec == null) continue;

            int specIdx = specIndexOf(specs, ticker);
            if (specIdx < 0) continue;

            seedPositionWithHistory(portfolio, sec, ohlcvData.get(specIdx), shares[i]);
        }
    }

    /**
     * Create one position with a BUY (bar 50) + optional partial SELL (bar 200) history.
     * Extracted so derived securities (the cointegrated COP partner, which is not in the spec list)
     * can be seeded with identical buy/sell/cost-basis logic.
     */
    private void seedPositionWithHistory(Portfolio portfolio, Security sec,
                                         List<OhlcvRow> rows, double shareCount) {
        // Early buy at bar 50
        int buyBar = Math.min(50, rows.size() - 1);
        BigDecimal buyPrice = rows.get(buyBar).close();
        // Use BigDecimal throughout for all quantity arithmetic (WR-06)
        BigDecimal bdQty = BigDecimal.valueOf(shareCount);
        BigDecimal quantity = bdQty.setScale(4, RoundingMode.HALF_UP);
        LocalDate buyDate = rows.get(buyBar).date();

        transactionRepository.save(new Transaction(
                portfolio, sec, buyDate, "BUY", quantity, buyPrice));

        // Partial sell at bar 200 (sell ~30% of position).
        if (rows.size() > 200) {
            int sellBar = 200;
            BigDecimal sellPrice = rows.get(sellBar).close();
            BigDecimal sellQty = bdQty.multiply(new BigDecimal("0.3"))
                    .setScale(0, RoundingMode.FLOOR);
            if (sellQty.compareTo(BigDecimal.ONE) >= 0) {
                BigDecimal sellQuantity = sellQty.setScale(4, RoundingMode.HALF_UP);
                LocalDate sellDate = rows.get(sellBar).date();
                transactionRepository.save(new Transaction(
                        portfolio, sec, sellDate, "SELL", sellQuantity, sellPrice));
                bdQty = bdQty.subtract(sellQty);
            }
        }

        // Current position: remaining shares at original buy cost basis
        BigDecimal finalQty = bdQty.setScale(4, RoundingMode.HALF_UP);
        positionRepository.save(new Position(portfolio, sec, finalQty, buyPrice));
    }

    // ── metadata helpers ──────────────────────────────────────────────────────

    private static int specIndexOf(List<SecuritySpec> specs, String ticker) {
        for (int i = 0; i < specs.size(); i++) {
            if (specs.get(i).ticker().equals(ticker)) return i;
        }
        return -1;
    }

    private static String nameFor(String ticker) {
        return switch (ticker) {
            case "AAPL"   -> "Apple Inc.";
            case "MSFT"   -> "Microsoft Corporation";
            case "NVDA"   -> "NVIDIA Corporation";
            case "AMZN"   -> "Amazon.com Inc.";
            case "GOOGL"  -> "Alphabet Inc.";
            case "JPM"    -> "JPMorgan Chase & Co.";
            case "BAC"    -> "Bank of America Corporation";
            case "XOM"    -> "Exxon Mobil Corporation";
            case "CVX"    -> "Chevron Corporation";
            case "COP"    -> "ConocoPhillips";
            case "JNJ"    -> "Johnson & Johnson";
            case "PFE"    -> "Pfizer Inc.";
            case "PG"     -> "Procter & Gamble Co.";
            case "KO"     -> "The Coca-Cola Company";
            case "WMT"    -> "Walmart Inc.";
            case "TSLA"   -> "Tesla Inc.";
            case "SPX500" -> "S&P 500 Benchmark (Synthetic)";
            default       -> ticker;
        };
    }

    private static String sectorFor(String ticker) {
        return switch (ticker) {
            case "AAPL", "MSFT", "NVDA", "AMZN", "GOOGL" -> "Technology";
            case "JPM", "BAC"                             -> "Financials";
            case "XOM", "CVX", "COP"                      -> "Energy";
            case "JNJ", "PFE"                             -> "Healthcare";
            case "PG", "KO", "WMT"                        -> "Consumer Staples";
            case "TSLA"                                   -> "Automotive";
            case "SPX500"                                 -> "Benchmark";
            default                                       -> "Other";
        };
    }
}
