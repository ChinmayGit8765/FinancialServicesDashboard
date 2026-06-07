package com.quantlens.portfolio;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit tests for pure computation methods in PortfolioService.
 * <p>
 * <strong>RED scaffold</strong> — turns green as Plans 02–04 implement
 * {@code PortfolioService} and its static helper methods. Each test asserts
 * the intended behaviour with hand-crafted inputs; the production methods do
 * not yet exist so tests that call them will fail to compile or will fail at
 * runtime until Plans 02–04 provide the implementations.
 * <p>
 * Tests that do not yet have a method to call use
 * {@code fail("not yet implemented: <method>")} as a placeholder so the suite
 * COMPILES but remains RED, satisfying the Nyquist contract that a real gate
 * exists before implementation begins.
 * <p>
 * No Spring context, no Testcontainers — plain JUnit 5 with hand-crafted inputs.
 */
class PortfolioServiceTest {

    // -----------------------------------------------------------------------
    // PORT-01: Unrealized P&L formula
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
        BigDecimal costBasis = avgCostBasis.multiply(quantity);  // 7500
        BigDecimal expectedPnlPct = expectedPnlAbs.divide(costBasis, 6, RoundingMode.HALF_UP);
        // = 0.200000

        // RED scaffold: PortfolioService.computeUnrealizedPnlAbs() does not exist yet
        fail("not yet implemented: PortfolioService.computeUnrealizedPnlAbs/Pct");

        // When implemented, assertions should be:
        // BigDecimal actualPnlAbs = PortfolioService.computeUnrealizedPnlAbs(currentPrice, avgCostBasis, quantity);
        // assertThat(actualPnlAbs).as("unrealized P&L absolute").isEqualByComparingTo(expectedPnlAbs);
        // BigDecimal actualPnlPct = PortfolioService.computeUnrealizedPnlPct(actualPnlAbs, avgCostBasis, quantity);
        // assertThat(actualPnlPct).as("unrealized P&L percent").isEqualByComparingTo(expectedPnlPct);
    }

    // -----------------------------------------------------------------------
    // PORT-01 / PORT-03: Allocation weights sum to 1
    //   For any set of allocation slices, Σ weight must equal 1.000000
    // -----------------------------------------------------------------------

    @Test
    void allocationWeightsSumToOne() {
        // RED scaffold: AllocationSliceDto + PortfolioService.computeAllocation() do not exist yet
        fail("not yet implemented: PortfolioService.computeAllocation");

        // When implemented:
        // List<AllocationSliceDto> slices = portfolioService.computeAllocation(positions, latestCloses);
        // BigDecimal totalWeight = slices.stream()
        //     .map(AllocationSliceDto::weight)
        //     .reduce(BigDecimal.ZERO, BigDecimal::add);
        // assertThat(totalWeight)
        //     .as("allocation weights must sum to exactly 1.000000")
        //     .isEqualByComparingTo(BigDecimal.ONE);
    }

    // -----------------------------------------------------------------------
    // PORT-02: Daily change derives from equity curve
    //   dailyChangeAbs = equityCurve[last].value - equityCurve[last-1].value
    //   dailyChangePct = dailyChangeAbs / equityCurve[last-1].value
    // -----------------------------------------------------------------------

    @Test
    void dailyChangeDerivesFromEquityCurve() {
        // Hand-crafted equity curve (two entries)
        BigDecimal previousValue = new BigDecimal("50000.00");
        BigDecimal latestValue   = new BigDecimal("51500.00");

        BigDecimal expectedChangeAbs = latestValue.subtract(previousValue); // 1500.00
        BigDecimal expectedChangePct = expectedChangeAbs.divide(previousValue, 6, RoundingMode.HALF_UP);
        // = 0.030000

        // RED scaffold: PortfolioService.computeDailyChange() does not exist yet
        fail("not yet implemented: PortfolioService.computeDailyChange");

        // When implemented:
        // BigDecimal actualChangeAbs = PortfolioService.computeDailyChangeAbs(curve);
        // assertThat(actualChangeAbs).as("daily change abs").isEqualByComparingTo(expectedChangeAbs);
        // BigDecimal actualChangePct = PortfolioService.computeDailyChangePct(curve);
        // assertThat(actualChangePct).as("daily change pct").isEqualByComparingTo(expectedChangePct);
    }

    // -----------------------------------------------------------------------
    // PORT-02: Total unrealized gain formula
    //   totalUnrealizedGainAbs = totalMarketValue - totalCostBasis
    //   totalUnrealizedGainPct = totalUnrealizedGainAbs / totalCostBasis
    // -----------------------------------------------------------------------

    @Test
    void totalUnrealizedGainFormula() {
        BigDecimal totalMarketValue = new BigDecimal("55000.00");
        BigDecimal totalCostBasis   = new BigDecimal("40000.00");

        BigDecimal expectedGainAbs = totalMarketValue.subtract(totalCostBasis); // 15000.00
        BigDecimal expectedGainPct = expectedGainAbs.divide(totalCostBasis, 6, RoundingMode.HALF_UP);
        // = 0.375000

        // RED scaffold: PortfolioService.computeTotalUnrealizedGain() does not exist yet
        fail("not yet implemented: PortfolioService.computeTotalUnrealizedGain");

        // When implemented:
        // BigDecimal actualGainAbs = PortfolioService.computeTotalUnrealizedGainAbs(totalMarketValue, totalCostBasis);
        // assertThat(actualGainAbs).as("total unrealized gain abs").isEqualByComparingTo(expectedGainAbs);
        // BigDecimal actualGainPct = PortfolioService.computeTotalUnrealizedGainPct(totalMarketValue, totalCostBasis);
        // assertThat(actualGainPct).as("total unrealized gain pct").isEqualByComparingTo(expectedGainPct);
    }

    // -----------------------------------------------------------------------
    // PORT-04: Running cost basis after BUY
    //   After the first BUY: runningCostBasis = buyPrice (no prior position)
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterBuy() {
        // Single BUY: 50 shares at $150.00
        BigDecimal buyQuantity = new BigDecimal("50.0000");
        BigDecimal buyPrice    = new BigDecimal("150.000000");

        // Expected: running cost basis = 150.000000 (only one buy, no prior holdings)
        BigDecimal expectedBasis = new BigDecimal("150.000000");

        // RED scaffold: PortfolioService.computeRunningCostBasis() does not exist yet
        fail("not yet implemented: PortfolioService.computeRunningCostBasis");

        // When implemented:
        // BigDecimal actualBasis = PortfolioService.computeRunningCostBasisAfterBuy(
        //     BigDecimal.ZERO, BigDecimal.ZERO, buyQuantity, buyPrice);
        // assertThat(actualBasis).as("running cost basis after first BUY")
        //     .isEqualByComparingTo(expectedBasis);
    }

    // -----------------------------------------------------------------------
    // PORT-04: Running cost basis after proportional SELL
    //   After a SELL, the average cost per remaining share is unchanged
    //   (SeedRunner does a 30%-floor proportional SELL at a different price,
    //   but the average cost of the remaining units stays at the BUY price)
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterProportionalSell() {
        // Starting position: 50 shares at avg cost $150 (after the BUY)
        BigDecimal initialQty  = new BigDecimal("50.0000");
        BigDecimal initialCost = new BigDecimal("150.000000"); // avg cost per share
        BigDecimal sellQty     = new BigDecimal("15.0000");    // 30% of 50
        BigDecimal sellPrice   = new BigDecimal("200.000000"); // irrelevant to avg cost

        // After SELL: qty = 35, total cost dollars = 50*150 - 15*150 = 7500-2250 = 5250
        // avgCost remains 5250/35 = 150.000000 (unchanged by proportional sell)
        BigDecimal expectedBasis = new BigDecimal("150.000000");

        // RED scaffold: PortfolioService.computeRunningCostBasis() does not exist yet
        fail("not yet implemented: PortfolioService.computeRunningCostBasisAfterSell");

        // When implemented:
        // BigDecimal actualBasis = PortfolioService.computeRunningCostBasisAfterSell(
        //     initialQty, initialCost, sellQty, sellPrice);
        // assertThat(actualBasis).as("running cost basis unchanged after proportional sell")
        //     .isEqualByComparingTo(expectedBasis);
    }

    // -----------------------------------------------------------------------
    // PORT-05: Benchmark rebasing — both series start at 100 on day 0
    // -----------------------------------------------------------------------

    @Test
    void benchmarkBothSeriesStartAt100() {
        // Hand-crafted inputs: portfolio series and benchmark series (3 days)
        // Both must be indexed to 100 on day 0
        BigDecimal portfolioBase = new BigDecimal("45000.00");
        BigDecimal benchmarkBase = new BigDecimal("115.000000");

        BigDecimal expectedDay0 = new BigDecimal("100.0000");

        // RED scaffold: PortfolioService.computeBenchmarkComparison() does not exist yet
        fail("not yet implemented: PortfolioService.computeBenchmarkComparison");

        // When implemented:
        // BenchmarkComparisonDto dto = portfolioService.getBenchmarkComparison(portfolioId);
        // assertThat(dto.portfolioSeries().get(0))
        //     .as("portfolio series must start at 100 on day 0")
        //     .isEqualByComparingTo(expectedDay0);
        // assertThat(dto.benchmarkSeries().get(0))
        //     .as("benchmark series must start at 100 on day 0")
        //     .isEqualByComparingTo(expectedDay0);
    }
}
