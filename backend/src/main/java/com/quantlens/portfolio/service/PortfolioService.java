package com.quantlens.portfolio.service;

import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.api.AllocationSliceDto;
import com.quantlens.portfolio.api.BenchmarkComparisonDto;
import com.quantlens.portfolio.api.DateValueDto;
import com.quantlens.portfolio.api.HoldingDto;
import com.quantlens.portfolio.api.PortfolioPnlDto;
import com.quantlens.portfolio.api.TransactionDto;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import com.quantlens.portfolio.domain.Transaction;
import com.quantlens.portfolio.domain.TransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Service responsible for all portfolio computation in Phase 2.
 * <p>
 * All methods are read-only: the class-level {@code @Transactional(readOnly = true)}
 * annotation applies to every public method, enabling Hibernate's read-only
 * optimisations and consistent snapshot semantics.
 * <p>
 * <strong>Module boundary:</strong> {@code com.quantlens.portfolio} declares
 * {@code allowedDependencies = {"marketdata::domain"}} so direct imports from
 * {@code com.quantlens.marketdata.domain} are permitted here.
 * <p>
 * <strong>BigDecimal scale convention:</strong>
 * <ul>
 *   <li>Display money (market values, P&amp;L absolute): scale 2, {@code HALF_UP}</li>
 *   <li>Prices, ratios, percentages, weights: scale 6, {@code HALF_UP}</li>
 *   <li>Equity-curve index values: scale 4, {@code HALF_UP}</li>
 *   <li>Every {@code divide()} call MUST specify scale and {@link RoundingMode}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final SecurityRepository securityRepository;
    private final TransactionRepository transactionRepository;

    public PortfolioService(PositionRepository positionRepository,
                            OhlcvBarRepository ohlcvBarRepository,
                            SecurityRepository securityRepository,
                            TransactionRepository transactionRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.securityRepository = securityRepository;
        this.transactionRepository = transactionRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Returns a paginated, most-recent-first transaction history for a portfolio,
     * with each entry annotated with the running average cost basis at that point.
     * <p>
     * Algorithm (two-pass):
     * <ol>
     *   <li>Load ALL transactions chronologically (txDate ASC, id ASC) and build a
     *       {@code Map<Long, BigDecimal>} of transactionId → runningCostBasis via
     *       the package-private {@link #buildRunningCostMap(List)} helper.</li>
     *   <li>Load the requested page (txDate DESC, id DESC) and map each
     *       {@code Transaction} to a {@code TransactionDto} by looking up its
     *       running cost basis from the map.</li>
     * </ol>
     *
     * @param portfolioId the owning portfolio's primary key (principal-resolved by controller)
     * @param pageable    page/sort descriptor — caller specifies most-recent-first
     * @return a page of TransactionDto; never a page of raw Transaction entities (Pitfall 6)
     */
    public Page<TransactionDto> getTransactions(Long portfolioId, Pageable pageable) {
        // Pass 1: chronological scan to build running average-cost map
        List<Transaction> chronological =
                transactionRepository.findByPortfolioIdChronological(portfolioId);
        Map<Long, BigDecimal> runningCostMap = buildRunningCostMap(chronological);

        // Pass 2: fetch the requested display page (most-recent-first) and map to DTOs
        Page<Transaction> page =
                transactionRepository.findByPortfolioIdWithSecurity(portfolioId, pageable);

        return page.map(tx -> new TransactionDto(
                tx.getTxDate(),
                tx.getTxType(),
                tx.getSecurity().getTicker(),
                tx.getQuantity(),
                tx.getPrice().setScale(6, RoundingMode.HALF_UP),
                tx.getQuantity().multiply(tx.getPrice()).setScale(2, RoundingMode.HALF_UP),
                runningCostMap.getOrDefault(tx.getId(), BigDecimal.ZERO)
        ));
    }

    /**
     * Returns the holdings list for a portfolio.
     * <p>
     * For each position with positive quantity, computes:
     * <ul>
     *   <li>{@code currentMarketValue} = qty × latestClose (scale 2)</li>
     *   <li>{@code portfolioWeight} = positionMarketValue / totalPortfolioMarketValue (scale 6)</li>
     *   <li>{@code unrealizedPnlAbs} = (latestClose − avgCostBasis) × qty (scale 2)</li>
     *   <li>{@code unrealizedPnlPct} = unrealizedPnlAbs / (avgCostBasis × qty) (scale 6)</li>
     * </ul>
     *
     * @param portfolioId the owning portfolio's primary key (principal-resolved by controller)
     * @return list of holdings with per-position P&amp;L and weight; empty if no positions
     */
    public List<HoldingDto> getHoldings(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        Map<Long, BigDecimal> latestCloses = latestCloseBySecurityId(positions);

        // Compute total portfolio market value for weight denominator
        BigDecimal totalPortfolioMarketValue = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> {
                    BigDecimal close = latestCloses.getOrDefault(
                            p.getSecurity().getId(), BigDecimal.ZERO);
                    return p.getQuantity().multiply(close).setScale(2, RoundingMode.HALF_UP);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<HoldingDto> holdings = new ArrayList<>();
        for (Position pos : positions) {
            if (pos.getQuantity().signum() <= 0) {
                continue; // defensive guard — seeded data always has qty > 0
            }
            BigDecimal close = latestCloses.getOrDefault(pos.getSecurity().getId(), BigDecimal.ZERO);
            BigDecimal qty = pos.getQuantity();
            BigDecimal avgCost = pos.getAvgCostBasis();

            BigDecimal currentMarketValue = qty.multiply(close).setScale(2, RoundingMode.HALF_UP);

            BigDecimal portfolioWeight = totalPortfolioMarketValue.signum() == 0
                    ? BigDecimal.ZERO
                    : currentMarketValue.divide(totalPortfolioMarketValue, 6, RoundingMode.HALF_UP);

            BigDecimal unrealizedPnlAbs = computeUnrealizedPnlAbs(close, avgCost, qty);

            BigDecimal costBasisTotal = avgCost.multiply(qty);
            BigDecimal unrealizedPnlPct = computeUnrealizedPnlPct(unrealizedPnlAbs, costBasisTotal);

            holdings.add(new HoldingDto(
                    pos.getSecurity().getTicker(),
                    pos.getSecurity().getName(),
                    pos.getSecurity().getSector(),
                    qty,
                    avgCost,
                    close.setScale(6, RoundingMode.HALF_UP),
                    currentMarketValue,
                    portfolioWeight,
                    unrealizedPnlAbs,
                    unrealizedPnlPct
            ));
        }
        return holdings;
    }

    /**
     * Returns sector allocation slices for a portfolio.
     * <p>
     * Aggregates positions by sector, computing each sector's market value.
     * Weights are computed using the last-slice residual absorption algorithm
     * so the sum of all {@link AllocationSliceDto#weight()} fields equals
     * <em>exactly</em> {@code 1.000000} by {@link BigDecimal#compareTo}.
     * <p>
     * Algorithm (RESEARCH.md Pattern 5):
     * <ol>
     *   <li>For all but the last sector: {@code weight = sectorValue / totalValue} (scale 6)</li>
     *   <li>For the last sector: {@code weight = BigDecimal.ONE.subtract(weightSum)} (scale 6)</li>
     * </ol>
     *
     * @param portfolioId the owning portfolio's primary key (principal-resolved by controller)
     * @return sector allocation slices; empty if no positions
     */
    public List<AllocationSliceDto> getAllocation(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        Map<Long, BigDecimal> latestCloses = latestCloseBySecurityId(positions);

        // Accumulate sector → totalMarketValue (LinkedHashMap preserves insertion order)
        BigDecimal totalValue = BigDecimal.ZERO;
        Map<String, BigDecimal> sectorValues = new LinkedHashMap<>();

        for (Position pos : positions) {
            if (pos.getQuantity().signum() <= 0) {
                continue;
            }
            BigDecimal close = latestCloses.getOrDefault(pos.getSecurity().getId(), BigDecimal.ZERO);
            BigDecimal mktValue = pos.getQuantity().multiply(close).setScale(2, RoundingMode.HALF_UP);
            totalValue = totalValue.add(mktValue);
            sectorValues.merge(pos.getSecurity().getSector(), mktValue, BigDecimal::add);
        }

        if (sectorValues.isEmpty()) {
            return List.of();
        }

        // Build slices with last-slice residual absorption (RESEARCH.md Pattern 5)
        List<AllocationSliceDto> slices = new ArrayList<>();
        BigDecimal weightSum = BigDecimal.ZERO;
        List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sectorValues.entrySet());

        for (int i = 0; i < entries.size(); i++) {
            BigDecimal sectorMktValue = entries.get(i).getValue();
            BigDecimal weight;
            if (i == entries.size() - 1) {
                // Last slice absorbs rounding residual to guarantee sum = 1.000000
                weight = BigDecimal.ONE.subtract(weightSum).setScale(6, RoundingMode.HALF_UP);
            } else {
                weight = sectorMktValue.divide(totalValue, 6, RoundingMode.HALF_UP);
                weightSum = weightSum.add(weight);
            }
            slices.add(new AllocationSliceDto(entries.get(i).getKey(), weight, sectorMktValue));
        }
        return slices;
    }

    /**
     * Returns portfolio-level P&amp;L with a full constant-current-holdings equity curve.
     * <p>
     * Computes: total market value (from equity curve's last point), total cost basis,
     * total unrealized gain, and daily change (curve[last] − curve[last−1]).
     *
     * @param portfolioId the owning portfolio's primary key (principal-resolved by controller)
     * @return P&amp;L summary with 504-entry equity curve starting 2022-09-12
     */
    public PortfolioPnlDto getPortfolioPnl(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        List<DateValueDto> equityCurve = buildEquityCurve(positions);

        BigDecimal totalMarketValue = equityCurve.get(equityCurve.size() - 1).value();

        // totalCostBasis = Σ(qty × avgCostBasis), scale 2
        BigDecimal totalCostBasis = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getQuantity().multiply(p.getAvgCostBasis()).setScale(2, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealizedGainAbs = computeTotalUnrealizedGainAbs(totalMarketValue, totalCostBasis);
        BigDecimal totalUnrealizedGainPct = computeTotalUnrealizedGainPct(totalUnrealizedGainAbs, totalCostBasis);

        BigDecimal[] dailyChange = computeDailyChange(equityCurve);
        BigDecimal dailyChangeAbs = dailyChange[0];
        BigDecimal dailyChangePct = dailyChange[1];

        return new PortfolioPnlDto(
                totalMarketValue,
                totalCostBasis,
                totalUnrealizedGainAbs,
                totalUnrealizedGainPct,
                dailyChangeAbs,
                dailyChangePct,
                equityCurve
        );
    }

    /**
     * Returns a benchmark comparison with both portfolio and SPX500 series rebased to 100 on day 0.
     * <p>
     * Both series use the full 504-day seeded window. Dates are ISO-8601 strings ascending.
     * Rebasing: {@code idx(i) = value(i) / value(0) × 100}, scale 4 HALF_UP.
     *
     * @param portfolioId the owning portfolio's primary key (principal-resolved by controller)
     * @return parallel arrays: dates, portfolioSeries, benchmarkSeries — all same length
     */
    public BenchmarkComparisonDto getBenchmarkComparison(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        List<DateValueDto> equityCurve = buildEquityCurve(positions);

        // Load SPX500 (the single benchmark security)
        List<Security> benchmarkSecurities = securityRepository.findByBenchmarkTrue();
        if (benchmarkSecurities.isEmpty()) {
            throw new IllegalStateException("No benchmark security found (findByBenchmarkTrue returned empty)");
        }
        if (benchmarkSecurities.size() > 1) {
            throw new IllegalStateException("Multiple benchmark securities found — expected exactly one SPX500");
        }
        Security spx = benchmarkSecurities.get(0);

        // Load SPX500 bars (504 bars ordered by barDate ASC)
        List<OhlcvBar> spxBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(
                List.of(spx.getId()));

        // Build parallel arrays aligned by position (both share the same 504-day calendar)
        List<String> dates = new ArrayList<>(equityCurve.size());
        List<BigDecimal> portfolioSeries = new ArrayList<>(equityCurve.size());
        List<BigDecimal> benchmarkSeries = new ArrayList<>(equityCurve.size());

        BigDecimal portfolioBase = equityCurve.get(0).value();
        BigDecimal benchmarkBase = spxBars.get(0).getClosePrice();

        for (int i = 0; i < equityCurve.size(); i++) {
            dates.add(equityCurve.get(i).date().toString());
            portfolioSeries.add(rebaseToIndex(equityCurve.get(i).value(), portfolioBase));
            benchmarkSeries.add(rebaseToIndex(spxBars.get(i).getClosePrice(), benchmarkBase));
        }

        return new BenchmarkComparisonDto(dates, portfolioSeries, benchmarkSeries);
    }

    // =========================================================================
    // Package-private static helpers (callable from unit tests in same package)
    // =========================================================================

    /**
     * Builds a map of transactionId → runningCostBasis by scanning transactions
     * chronologically (txDate ASC, id ASC — Pitfall 5).
     * <p>
     * Algorithm (RESEARCH.md Pattern 1, GAAP average-cost):
     * <ul>
     *   <li>BUY: {@code runningCost += qty × price;  runningQty += qty}</li>
     *   <li>SELL: {@code avgCostNow = runningCost / runningQty (scale 6);
     *       runningCost -= sellQty × avgCostNow;  runningQty -= sellQty}</li>
     *   <li>After each tx: {@code runningCostBasis = runningQty == 0 ? 0 : runningCost / runningQty}</li>
     * </ul>
     * CRITICAL: SELL reduces basis by {@code sellQty × avgCostNow}, NEVER by
     * {@code sellQty × sellPrice} — mixing realized gain with basis reduction is wrong.
     *
     * @param chronologicalTxs transactions sorted txDate ASC, id ASC
     * @return map from transaction id to the running avg cost basis after that transaction
     */
    static Map<Long, BigDecimal> buildRunningCostMap(List<Transaction> chronologicalTxs) {
        Map<Long, BigDecimal> result = new HashMap<>();
        BigDecimal runningQty  = BigDecimal.ZERO;
        BigDecimal runningCost = BigDecimal.ZERO;

        for (Transaction tx : chronologicalTxs) {
            BigDecimal qty   = tx.getQuantity();
            BigDecimal price = tx.getPrice();

            if ("BUY".equals(tx.getTxType())) {
                runningCost = runningCost.add(qty.multiply(price));
                runningQty  = runningQty.add(qty);
            } else { // SELL
                BigDecimal avgCostNow = runningQty.signum() == 0
                        ? BigDecimal.ZERO
                        : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
                // Basis reduced by sellQty × avgCostNow, NOT sellQty × sellPrice
                runningCost = runningCost.subtract(qty.multiply(avgCostNow));
                runningQty  = runningQty.subtract(qty);
            }

            BigDecimal avgCostAtThisPoint = runningQty.signum() == 0
                    ? BigDecimal.ZERO
                    : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
            result.put(tx.getId(), avgCostAtThisPoint);
        }
        return result;
    }

    /**
     * Unit-test-friendly overload: compute running cost basis from primitive tuples
     * without requiring JPA entity construction.
     * <p>
     * Each element of {@code txTuples} is a {@code String[3]} of {@code {txType, qty, price}}
     * where txType is "BUY" or "SELL", qty and price are decimal strings.
     * Returns the running cost basis after each transaction (same index as input).
     *
     * @param txTuples list of {txType, qty, price} string triples, chronological order
     * @return list of running cost basis values (scale 6) after each transaction
     */
    public static List<BigDecimal> computeRunningCostBasisFromTuples(List<String[]> txTuples) {
        List<BigDecimal> result = new ArrayList<>();
        BigDecimal runningQty  = BigDecimal.ZERO;
        BigDecimal runningCost = BigDecimal.ZERO;

        for (String[] tuple : txTuples) {
            String txType = tuple[0];
            BigDecimal qty   = new BigDecimal(tuple[1]);
            BigDecimal price = new BigDecimal(tuple[2]);

            if ("BUY".equals(txType)) {
                runningCost = runningCost.add(qty.multiply(price));
                runningQty  = runningQty.add(qty);
            } else { // SELL
                BigDecimal avgCostNow = runningQty.signum() == 0
                        ? BigDecimal.ZERO
                        : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
                // Basis reduced by sellQty × avgCostNow, NOT sellQty × sellPrice
                runningCost = runningCost.subtract(qty.multiply(avgCostNow));
                runningQty  = runningQty.subtract(qty);
            }

            BigDecimal avgCostAtThisPoint = runningQty.signum() == 0
                    ? BigDecimal.ZERO
                    : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
            result.add(avgCostAtThisPoint);
        }
        return result;
    }

    // =========================================================================
    // Public static helpers (callable from unit tests without Spring)
    // =========================================================================

    /**
     * Computes unrealized P&amp;L absolute: {@code (currentPrice − avgCostBasis) × quantity}.
     *
     * @param currentPrice  latest close price (scale 6)
     * @param avgCostBasis  average cost per share (scale 6)
     * @param quantity      shares held (scale 4)
     * @return absolute unrealized P&amp;L, scale 2 {@code HALF_UP}
     */
    public static BigDecimal computeUnrealizedPnlAbs(BigDecimal currentPrice,
                                                      BigDecimal avgCostBasis,
                                                      BigDecimal quantity) {
        return currentPrice.subtract(avgCostBasis)
                .multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes unrealized P&amp;L percentage: {@code pnlAbs / costBasisTotal}.
     * <p>
     * Guards against a zero {@code costBasisTotal} by returning {@link BigDecimal#ZERO}.
     *
     * @param unrealizedPnlAbs the absolute P&amp;L (scale 2)
     * @param costBasisTotal   {@code avgCostBasis × quantity}; must not be null
     * @return percentage (decimal, not × 100), scale 6 {@code HALF_UP}; ZERO if divisor is zero
     */
    public static BigDecimal computeUnrealizedPnlPct(BigDecimal unrealizedPnlAbs,
                                                      BigDecimal costBasisTotal) {
        if (costBasisTotal.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return unrealizedPnlAbs.divide(costBasisTotal, 6, RoundingMode.HALF_UP);
    }

    /**
     * Computes total unrealized gain absolute: {@code totalMarketValue − totalCostBasis}.
     *
     * @param totalMarketValue the portfolio's current total market value (scale 2)
     * @param totalCostBasis   Σ(qty × avgCostBasis) across all positions (scale 2)
     * @return total unrealized gain absolute, scale 2
     */
    public static BigDecimal computeTotalUnrealizedGainAbs(BigDecimal totalMarketValue,
                                                            BigDecimal totalCostBasis) {
        return totalMarketValue.subtract(totalCostBasis).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Computes total unrealized gain percentage: {@code gainAbs / totalCostBasis}.
     * <p>
     * Guards against a zero {@code totalCostBasis} by returning {@link BigDecimal#ZERO}.
     *
     * @param totalUnrealizedGainAbs the absolute total gain (scale 2)
     * @param totalCostBasis         Σ(qty × avgCostBasis); must not be null
     * @return percentage (decimal), scale 6 {@code HALF_UP}; ZERO if divisor is zero
     */
    public static BigDecimal computeTotalUnrealizedGainPct(BigDecimal totalUnrealizedGainAbs,
                                                            BigDecimal totalCostBasis) {
        if (totalCostBasis.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalUnrealizedGainAbs.divide(totalCostBasis, 6, RoundingMode.HALF_UP);
    }

    /**
     * Computes daily change (absolute and percentage) from an equity curve.
     * <p>
     * {@code dailyChangeAbs = curve[last].value − curve[last−1].value} (scale 2).
     * {@code dailyChangePct = dailyChangeAbs / curve[last−1].value} (scale 6).
     * <p>
     * <strong>PITFALL (RESEARCH.md Pitfall 3):</strong> Daily change uses the last TWO
     * entries of the curve — NOT first-to-last. Using curve[0] as "previous" would return
     * the change over the full historical window, not one day.
     *
     * @param equityCurve list of date-value points sorted ascending by date; must have ≥ 2 entries
     * @return two-element array: {@code [dailyChangeAbs, dailyChangePct]}
     * @throws IllegalArgumentException if the curve has fewer than 2 entries
     */
    public static BigDecimal[] computeDailyChange(List<DateValueDto> equityCurve) {
        if (equityCurve.size() < 2) {
            throw new IllegalArgumentException(
                    "Equity curve must have at least 2 entries to compute daily change; got "
                    + equityCurve.size());
        }
        BigDecimal latestValue   = equityCurve.get(equityCurve.size() - 1).value();
        BigDecimal previousValue = equityCurve.get(equityCurve.size() - 2).value();

        BigDecimal dailyChangeAbs = latestValue.subtract(previousValue).setScale(2, RoundingMode.HALF_UP);
        BigDecimal dailyChangePct = previousValue.signum() == 0
                ? BigDecimal.ZERO
                : dailyChangeAbs.divide(previousValue, 6, RoundingMode.HALF_UP);

        return new BigDecimal[]{dailyChangeAbs, dailyChangePct};
    }

    /**
     * Rebases a value to an index relative to a base value: {@code value / base × 100}.
     * <p>
     * Both portfolio and benchmark series are independently rebased so that
     * {@code portfolioSeries[0] == benchmarkSeries[0] == 100.0000} (RESEARCH.md Pattern 4).
     *
     * @param value the value at position i in the series
     * @param base  the value at position 0 in the same series (day-0 denominator)
     * @return index value, scale 4 {@code HALF_UP}; ZERO if base is zero
     */
    public static BigDecimal rebaseToIndex(BigDecimal value, BigDecimal base) {
        if (base.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return value.divide(base, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Constant-current-holdings equity curve.
     *
     * <p>This curve values the portfolio's CURRENT (final) holdings across every
     * historical trading day in the seeded window, as if those shares were held
     * throughout. This is a dashboard equity curve — it shows how the current
     * portfolio WOULD have performed, not how it DID perform (the latter requires
     * reconstructing historical holdings from transactions, which is deferred).
     *
     * <p>Assumption: defensible for a demo dashboard. Limitation: overstates
     * performance if high-performing stocks were bought late. Must be labelled
     * in the UI with a tooltip: "Based on current holdings valued historically".
     *
     * <p>Algorithm (RESEARCH.md Pattern 2):
     * <ol>
     *   <li>Fetch all bars for the portfolio's security IDs via {@code findAllBySecurityIdsOrdered}
     *       (one JDBC round-trip, ordered by securityId ASC, barDate ASC).</li>
     *   <li>Group into {@code Map<securityId, TreeMap<date, close>>}.</li>
     *   <li>Determine the common date range: max of per-security firstKey, min of lastKey.</li>
     *   <li>For each date: {@code dayValue = Σ position.qty × close(date)}, scale 2.</li>
     * </ol>
     *
     * @param positions list of active positions with security already JOIN-FETCHed
     * @return 504-entry list of DateValueDto sorted ascending by date
     */
    private List<DateValueDto> buildEquityCurve(List<Position> positions) {
        List<Long> securityIds = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());

        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds);

        // Group bars into Map<securityId, TreeMap<date, close>>
        Map<Long, NavigableMap<LocalDate, BigDecimal>> closesBySecId = new TreeMap<>();
        for (OhlcvBar bar : allBars) {
            closesBySecId
                    .computeIfAbsent(bar.getSecurity().getId(), id -> new TreeMap<>())
                    .put(bar.getBarDate(), bar.getClosePrice());
        }

        // Find common date range: intersection of all per-security date sets
        LocalDate firstDate = closesBySecId.values().stream()
                .map(NavigableMap::firstKey)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars found for portfolio positions"));
        LocalDate lastDate = closesBySecId.values().stream()
                .map(NavigableMap::lastKey)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars found for portfolio positions"));

        // Collect all trading dates in the common range from any security's map
        // (all securities share the same calendar — safe to take any one)
        NavigableMap<LocalDate, BigDecimal> referenceDates = closesBySecId.values().iterator().next();

        List<DateValueDto> curve = new ArrayList<>();
        for (LocalDate date : referenceDates.subMap(firstDate, true, lastDate, true).keySet()) {
            BigDecimal dayValue = BigDecimal.ZERO;
            for (Position pos : positions) {
                if (pos.getQuantity().signum() <= 0) {
                    continue;
                }
                NavigableMap<LocalDate, BigDecimal> secMap = closesBySecId.get(pos.getSecurity().getId());
                BigDecimal close = secMap != null ? secMap.get(date) : null;
                if (close == null) {
                    continue; // defensive guard — should not happen with shared calendar
                }
                dayValue = dayValue.add(pos.getQuantity().multiply(close));
            }
            curve.add(new DateValueDto(date, dayValue.setScale(2, RoundingMode.HALF_UP)));
        }
        return curve;
    }

    /**
     * Fetches the latest OHLCV close for each security in the given positions list.
     * <p>
     * Delegates to {@link OhlcvBarRepository#findLatestBarBySecurityIds(List)} — a single
     * correlated-subquery round-trip returning one bar per security (N+1-safe).
     *
     * @param positions positions whose security IDs to resolve
     * @return map of securityId → latestClosePrice; empty if positions is empty
     */
    private Map<Long, BigDecimal> latestCloseBySecurityId(List<Position> positions) {
        if (positions.isEmpty()) {
            return Map.of();
        }
        List<Long> securityIds = positions.stream()
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());

        return ohlcvBarRepository.findLatestBarBySecurityIds(securityIds).stream()
                .collect(Collectors.toMap(
                        bar -> bar.getSecurity().getId(),
                        OhlcvBar::getClosePrice
                ));
    }
}
