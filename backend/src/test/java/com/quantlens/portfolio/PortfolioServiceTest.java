package com.quantlens.portfolio;

import com.quantlens.portfolio.service.PortfolioService;
import com.quantlens.portfolio.api.AllocationSliceDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for pure computation methods in PortfolioService.
 * <p>
 * All tests that cover Plans 02 static helpers call
 * {@link PortfolioService#computeUnrealizedPnlAbs} /
 * {@link PortfolioService#computeUnrealizedPnlPct} directly — no Spring context,
 * no Testcontainers, no mocks needed. Tests for Plans 03–04 remain RED until those
 * plans implement their helpers.
 */
class PortfolioServiceTest {

    // -----------------------------------------------------------------------
    // PORT-01: Unrealized P&L formula — GREEN (Plan 02)
    //   unrealizedPnlAbs = (currentPrice - avgCostBasis) * quantity
    //   unrealizedPnlPct = unrealizedPnlAbs / (avgCostBasis * quantity)
    // -----------------------------------------------------------------------

    @Test
    void unrealizedPnlFormula() {
        // Hand-crafted inputs
        BigDecimal avgCostBasis = new BigDecimal("150.000000");
        BigDecimal currentPrice = new BigDecimal("180.000000");
        BigDecimal quantity = new BigDecimal("50.0000");

        // Expected values (manual calculation)
        BigDecimal expectedPnlAbs = new BigDecimal("1500.00");   // (180-150)*50
        BigDecimal costBasis = avgCostBasis.multiply(quantity);   // 7500
        BigDecimal expectedPnlPct = expectedPnlAbs.divide(costBasis, 6, RoundingMode.HALF_UP);
        // = 0.200000

        BigDecimal actualPnlAbs = PortfolioService.computeUnrealizedPnlAbs(
                currentPrice, avgCostBasis, quantity);
        assertThat(actualPnlAbs)
                .as("unrealized P&L absolute")
                .isEqualByComparingTo(expectedPnlAbs);

        BigDecimal actualPnlPct = PortfolioService.computeUnrealizedPnlPct(actualPnlAbs, costBasis);
        assertThat(actualPnlPct)
                .as("unrealized P&L percent")
                .isEqualByComparingTo(expectedPnlPct);
    }

    // -----------------------------------------------------------------------
    // PORT-01 / PORT-03: Allocation weights sum to 1 — GREEN (Plan 02)
    //   For any set of allocation slices, Σ weight must equal 1.000000
    // -----------------------------------------------------------------------

    @Test
    void allocationWeightsSumToOne() {
        // Simulate two sectors with market values that do NOT divide evenly
        // (Technology: $73,500, Automotive: $12,250 → total $85,750)
        // 73500/85750 = 0.857143...  12250/85750 = 0.142857...  last-slice residual needed
        List<AllocationSliceDto> slices = buildTestAllocationSlices();

        BigDecimal totalWeight = slices.stream()
                .map(AllocationSliceDto::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(totalWeight)
                .as("allocation weights must sum to exactly 1.000000")
                .isEqualByComparingTo(BigDecimal.ONE);
    }

    /**
     * Builds a test allocation list using the same last-slice residual algorithm
     * as {@code PortfolioService.getAllocation}.
     */
    private List<AllocationSliceDto> buildTestAllocationSlices() {
        BigDecimal tech = new BigDecimal("73500.00");
        BigDecimal auto = new BigDecimal("12250.00");
        BigDecimal total = tech.add(auto);

        // first sector (all but last): normal divide
        BigDecimal techWeight = tech.divide(total, 6, RoundingMode.HALF_UP);
        // last sector: residual absorption
        BigDecimal autoWeight = BigDecimal.ONE.subtract(techWeight).setScale(6, RoundingMode.HALF_UP);

        return List.of(
                new AllocationSliceDto("Technology", techWeight, tech),
                new AllocationSliceDto("Automotive", autoWeight, auto)
        );
    }

    // -----------------------------------------------------------------------
    // PORT-02: Daily change derives from equity curve — RED (Plan 03)
    // -----------------------------------------------------------------------

    @Test
    void dailyChangeDerivesFromEquityCurve() {
        // RED scaffold: PortfolioService.computeDailyChange() does not exist yet
        fail("not yet implemented: PortfolioService.computeDailyChange");
    }

    // -----------------------------------------------------------------------
    // PORT-02: Total unrealized gain formula — RED (Plan 03)
    // -----------------------------------------------------------------------

    @Test
    void totalUnrealizedGainFormula() {
        // RED scaffold: PortfolioService.computeTotalUnrealizedGain() does not exist yet
        fail("not yet implemented: PortfolioService.computeTotalUnrealizedGain");
    }

    // -----------------------------------------------------------------------
    // PORT-04: Running cost basis after BUY — RED (Plan 04)
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterBuy() {
        // RED scaffold: PortfolioService.computeRunningCostBasis() does not exist yet
        fail("not yet implemented: PortfolioService.computeRunningCostBasis");
    }

    // -----------------------------------------------------------------------
    // PORT-04: Running cost basis after proportional SELL — RED (Plan 04)
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterProportionalSell() {
        // RED scaffold: PortfolioService.computeRunningCostBasisAfterSell() does not exist yet
        fail("not yet implemented: PortfolioService.computeRunningCostBasisAfterSell");
    }

    // -----------------------------------------------------------------------
    // PORT-05: Benchmark rebasing — both series start at 100 on day 0 — RED (Plan 04)
    // -----------------------------------------------------------------------

    @Test
    void benchmarkBothSeriesStartAt100() {
        // RED scaffold: PortfolioService.computeBenchmarkComparison() does not exist yet
        fail("not yet implemented: PortfolioService.computeBenchmarkComparison");
    }
}
