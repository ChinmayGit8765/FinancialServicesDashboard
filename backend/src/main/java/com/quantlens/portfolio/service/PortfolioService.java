package com.quantlens.portfolio.service;

import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.portfolio.api.AllocationSliceDto;
import com.quantlens.portfolio.api.HoldingDto;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 *   <li>Every {@code divide()} call MUST specify scale and {@link RoundingMode}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PortfolioService {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;

    public PortfolioService(PositionRepository positionRepository,
                            OhlcvBarRepository ohlcvBarRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

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

    // =========================================================================
    // Package-private static helpers (callable from unit tests without Spring)
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

    // =========================================================================
    // Private helper
    // =========================================================================

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
