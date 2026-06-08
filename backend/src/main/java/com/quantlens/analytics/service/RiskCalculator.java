package com.quantlens.analytics.service;

import com.quantlens.analytics.api.RiskScorecardDto;
import com.quantlens.analytics.api.VarResultDto;
import com.quantlens.marketdata.domain.FactorReturn;
import com.quantlens.marketdata.domain.FactorReturnRepository;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.stat.correlation.Covariance;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes risk scorecard metrics for a portfolio.
 * <p>
 * Metrics: annualized Sharpe ratio, annualized volatility, max drawdown,
 * beta vs SPX500 benchmark, historical VaR (95% 1-day), and parametric Gaussian VaR.
 * All statistical quantities are {@code double}; monetary VaR amounts are {@code BigDecimal}.
 * <p>
 * Math convention (04-RESEARCH.md):
 * <ul>
 *   <li>Log returns: ln(P_t / P_{t-1})</li>
 *   <li>Sharpe = mean(excess returns) / std(returns) × √252; RF from FactorReturn</li>
 *   <li>Annualized vol = std(log returns) × √252</li>
 *   <li>Max drawdown = most-negative peak-to-trough on equity curve (in [-1,0])</li>
 *   <li>Beta = cov(portfolio, benchmark) / var(benchmark) via Hipparchus Covariance</li>
 *   <li>Historical VaR = −getPercentile(5.0) × value (positive loss)</li>
 *   <li>Parametric VaR = (1.645 × σ − μ) × value (positive loss)</li>
 * </ul>
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only (per {@code analytics/package-info.java} allowedDependencies).
 */
@Service
@Transactional(readOnly = true)
public class RiskCalculator {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final SecurityRepository securityRepository;
    private final FactorReturnRepository factorReturnRepository;

    public RiskCalculator(PositionRepository positionRepository,
                          OhlcvBarRepository ohlcvBarRepository,
                          SecurityRepository securityRepository,
                          FactorReturnRepository factorReturnRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.securityRepository = securityRepository;
        this.factorReturnRepository = factorReturnRepository;
    }

    /**
     * Computes the risk scorecard for the given portfolio.
     *
     * @param portfolioId the portfolio to score
     * @return RiskScorecardDto with Sharpe, vol, drawdown, beta, and dual VaR
     * @throws IllegalArgumentException if equity curve has fewer than 252 returns
     * @throws IllegalStateException    if no benchmark (SPX500) is found or more than one exists
     */
    public RiskScorecardDto computeRiskScorecard(Long portfolioId) {
        // Load positions with security eagerly fetched
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        // REUSE: mirrors PortfolioService.buildEquityCurve
        List<DateValueDto> curve = buildEquityCurveLocal(positions);

        double[] portfolioReturns = logReturns(curve);

        if (portfolioReturns.length < 252) {
            throw new IllegalArgumentException(
                    "Insufficient data: equity curve yields " + portfolioReturns.length +
                    " returns; minimum 252 required for annualized metrics.");
        }

        // --- Benchmark (SPX500) log returns ---
        List<Security> benchmarks = securityRepository.findByBenchmarkTrue();
        if (benchmarks.isEmpty()) {
            throw new IllegalStateException("No benchmark security found (isBenchmark=true). " +
                    "SPX500 must be seeded with isBenchmark=true.");
        }
        if (benchmarks.size() > 1) {
            throw new IllegalStateException("Multiple benchmark securities found (expected exactly 1 SPX500). " +
                    "Found: " + benchmarks.stream().map(Security::getTicker).collect(Collectors.joining(", ")));
        }
        Security benchmark = benchmarks.get(0);
        List<OhlcvBar> bmkBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(
                List.of(benchmark.getId()));
        List<DateValueDto> bmkCurve = buildCurveFromSingleSecurity(bmkBars);
        double[] benchmarkReturns = logReturns(bmkCurve);

        // --- Factor returns for RF alignment ---
        List<FactorReturn> factorRows = factorReturnRepository.findAllByOrderByFactorDateAsc();

        // --- Sharpe ratio ---
        // Factor row alignment: return[i] = ln(close[i+1]/close[i]) aligns with factorRows[i+1]
        // (see 04-RESEARCH.md "Correct alignment" section)
        double[] rfDailyArr = new double[portfolioReturns.length];
        boolean rfAligned = factorRows.size() == portfolioReturns.length + 1;
        if (!rfAligned) {
            // Fall back to rf=0 — documented; factorRows should be 504 for 503 returns
            // This guard protects against edge cases but should not trigger for seeded data
        }
        for (int i = 0; i < portfolioReturns.length; i++) {
            if (rfAligned) {
                rfDailyArr[i] = factorRows.get(i + 1).getRf().doubleValue();
            }
            // else rfDailyArr[i] remains 0.0
        }

        double[] excessReturns = new double[portfolioReturns.length];
        for (int i = 0; i < portfolioReturns.length; i++) {
            excessReturns[i] = portfolioReturns[i] - rfDailyArr[i];
        }

        DescriptiveStatistics stats = new DescriptiveStatistics(portfolioReturns);
        double meanExcess = Arrays.stream(excessReturns).average().orElse(0.0);
        double stdR = stats.getStandardDeviation(); // sample std (n-1) — correct for Sharpe

        double sharpe = (stdR == 0.0) ? Double.NaN : (meanExcess / stdR) * Math.sqrt(252.0);

        // --- Annualized volatility ---
        double annualizedVol = stdR * Math.sqrt(252.0);

        // --- Max drawdown (on equity curve, not returns) ---
        double maxDrawdown = computeMaxDrawdown(curve);

        // --- Beta ---
        double[] bmkReturnsTrimmed = trimToSameLength(benchmarkReturns, portfolioReturns.length);
        Covariance cov = new Covariance();
        double pairCov = cov.covariance(portfolioReturns, bmkReturnsTrimmed);
        DescriptiveStatistics bmkStats = new DescriptiveStatistics(bmkReturnsTrimmed);
        double bmkVar = bmkStats.getVariance(); // sample variance — consistent with pairCov
        double beta = (bmkVar == 0.0) ? Double.NaN : pairCov / bmkVar;

        // --- Current portfolio value (for VaR monetary amounts) ---
        List<Long> secIds = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());
        Map<Long, BigDecimal> latestClose = latestCloseBySecurityId(secIds);
        double currentPortfolioValue = 0.0;
        for (Position pos : positions) {
            if (pos.getQuantity().signum() <= 0) continue;
            BigDecimal close = latestClose.get(pos.getSecurity().getId());
            if (close != null) {
                currentPortfolioValue += pos.getQuantity().multiply(close).doubleValue();
            }
        }

        // --- Historical VaR (95%, 1-day) ---
        // getPercentile(5.0) returns a negative number → negate for positive loss
        double histVarPct = -stats.getPercentile(5.0);
        BigDecimal histVarAmount = BigDecimal.valueOf(histVarPct * currentPortfolioValue)
                .setScale(2, RoundingMode.HALF_UP);

        VarResultDto historicalVar = new VarResultDto(
                "HISTORICAL", 0.95, 1, histVarAmount, histVarPct);

        // --- Parametric Gaussian VaR (95%, 1-day) ---
        // z = 1.645 (documented constant; NormalDistribution.inverseCumulativeProbability(0.95) ≈ 1.6449)
        double z95 = 1.645;
        double dailyMean = stats.getMean();
        double dailySigma = stats.getStandardDeviation();
        double paramVarPct = z95 * dailySigma - dailyMean; // positive for normal equity portfolio
        BigDecimal paramVarAmount = BigDecimal.valueOf(paramVarPct * currentPortfolioValue)
                .setScale(2, RoundingMode.HALF_UP);

        VarResultDto parametricVar = new VarResultDto(
                "PARAMETRIC", 0.95, 1, paramVarAmount, paramVarPct);

        // --- Optional: CVaR / Expected Shortfall (Historical) ---
        double p5threshold = stats.getPercentile(5.0);
        double[] tailReturns = Arrays.stream(portfolioReturns)
                .filter(r -> r <= p5threshold)
                .toArray();
        double cvarPct = (tailReturns.length > 0)
                ? -(Arrays.stream(tailReturns).average().orElse(0.0))
                : histVarPct;
        BigDecimal cvarAmount = BigDecimal.valueOf(cvarPct * currentPortfolioValue)
                .setScale(2, RoundingMode.HALF_UP);
        VarResultDto cvarEntry = new VarResultDto(
                "CVaR_HISTORICAL", 0.95, 1, cvarAmount, cvarPct);

        return new RiskScorecardDto(
                sharpe, annualizedVol, maxDrawdown, beta,
                List.of(historicalVar, parametricVar, cvarEntry));
    }

    // -----------------------------------------------------------------------
    // Package-accessible static helpers (used also by CorrelationCalculator)
    // -----------------------------------------------------------------------

    /**
     * Computes log returns from an equity curve.
     * {@code r[i] = ln(curve[i+1].value / curve[i].value)} for i = 0..n-2.
     * Returns a double[] of length {@code curve.size() - 1}.
     */
    static double[] logReturns(List<DateValueDto> curve) {
        double[] r = new double[curve.size() - 1];
        for (int i = 1; i < curve.size(); i++) {
            double p1 = curve.get(i).value().doubleValue();
            double p0 = curve.get(i - 1).value().doubleValue();
            // Guard: if p0 == 0, assign 0 (defensive; seeded data always has p0 > 0)
            r[i - 1] = (p0 == 0.0) ? 0.0 : Math.log(p1 / p0);
        }
        return r;
    }

    /**
     * Builds an equity curve from a list of positions by replicating
     * {@code PortfolioService.buildEquityCurve} locally.
     * <p>
     * // REUSE: mirrors PortfolioService.buildEquityCurve
     * Groups bars into Map&lt;securityId, TreeMap&lt;date, close&gt;&gt;,
     * intersects date sets across all holdings, and emits dayValue = Σ qty × close.
     * Only positions with quantity &gt; 0 are included.
     */
    List<DateValueDto> buildEquityCurveLocal(List<Position> positions) {
        List<Long> securityIds = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());

        if (securityIds.isEmpty()) {
            return List.of();
        }

        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds);

        // Group bars into Map<securityId, TreeMap<date, close>>
        Map<Long, NavigableMap<LocalDate, BigDecimal>> closesBySecId = new TreeMap<>();
        for (OhlcvBar bar : allBars) {
            closesBySecId
                    .computeIfAbsent(bar.getSecurity().getId(), id -> new TreeMap<>())
                    .put(bar.getBarDate(), bar.getClosePrice());
        }

        // Find common date range: intersection of per-security date sets (WR-02 pattern from PortfolioService)
        Set<LocalDate> commonDates = null;
        LocalDate firstDate = closesBySecId.values().stream()
                .map(NavigableMap::firstKey)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars for portfolio positions"));
        LocalDate lastDate = closesBySecId.values().stream()
                .map(NavigableMap::lastKey)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars for portfolio positions"));

        for (NavigableMap<LocalDate, BigDecimal> secMap : closesBySecId.values()) {
            Set<LocalDate> secDates = new TreeSet<>(
                    secMap.subMap(firstDate, true, lastDate, true).keySet());
            if (commonDates == null) {
                commonDates = secDates;
            } else {
                commonDates.retainAll(secDates);
            }
        }
        if (commonDates == null) {
            commonDates = new TreeSet<>();
        }

        List<DateValueDto> curve = new ArrayList<>();
        for (LocalDate date : commonDates) {
            BigDecimal dayValue = BigDecimal.ZERO;
            for (Position pos : positions) {
                if (pos.getQuantity().signum() <= 0) continue;
                NavigableMap<LocalDate, BigDecimal> secMap = closesBySecId.get(pos.getSecurity().getId());
                BigDecimal close = secMap != null ? secMap.get(date) : null;
                if (close == null) continue;
                dayValue = dayValue.add(pos.getQuantity().multiply(close));
            }
            curve.add(new DateValueDto(date, dayValue.setScale(2, RoundingMode.HALF_UP)));
        }
        return curve;
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    /**
     * Builds a single-security equity curve from a pre-loaded list of bars.
     * Returns a DateValueDto list sorted ascending by barDate (close price as value).
     */
    private List<DateValueDto> buildCurveFromSingleSecurity(List<OhlcvBar> bars) {
        return bars.stream()
                .sorted(Comparator.comparing(OhlcvBar::getBarDate))
                .map(b -> new DateValueDto(b.getBarDate(), b.getClosePrice()))
                .collect(Collectors.toList());
    }

    /**
     * Computes max drawdown on the equity curve (peak-to-trough, returns value in [-1, 0]).
     * Returns 0.0 for a flat or monotonically rising curve.
     */
    private static double computeMaxDrawdown(List<DateValueDto> curve) {
        double peak = Double.NEGATIVE_INFINITY;
        double maxDd = 0.0;
        for (DateValueDto pt : curve) {
            double v = pt.value().doubleValue();
            if (v > peak) peak = v;
            double dd = (peak > 0) ? (v - peak) / peak : 0.0;
            if (dd < maxDd) maxDd = dd;
        }
        return maxDd; // negative number, e.g. -0.18 for 18% drawdown
    }

    /**
     * Trims {@code arr} to the first {@code length} elements.
     * If arr is already shorter, returns it unchanged.
     */
    private static double[] trimToSameLength(double[] arr, int length) {
        if (arr.length <= length) return arr;
        return Arrays.copyOf(arr, length);
    }

    /**
     * Fetches the latest close price for each security ID.
     * Delegates to OhlcvBarRepository.findLatestBarBySecurityIds — single round-trip.
     */
    private Map<Long, BigDecimal> latestCloseBySecurityId(List<Long> securityIds) {
        if (securityIds.isEmpty()) return Map.of();
        return ohlcvBarRepository.findLatestBarBySecurityIds(securityIds).stream()
                .collect(Collectors.toMap(
                        bar -> bar.getSecurity().getId(),
                        OhlcvBar::getClosePrice
                ));
    }
}
