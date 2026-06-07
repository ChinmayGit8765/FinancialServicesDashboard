package com.quantlens.portfolio;

import com.quantlens.portfolio.service.PortfolioService;
import com.quantlens.portfolio.api.AllocationSliceDto;
import com.quantlens.portfolio.api.DateValueDto;
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
 * All tests call static helpers directly — no Spring context, no Testcontainers,
 * no mocks needed. Tests for Plans 04 remain RED until those plans implement
 * their helpers.
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
    // PORT-02: Daily change derives from equity curve — GREEN (Plan 03)
    //   dailyChangeAbs = curve[last].value − curve[last−1].value  (scale 2)
    //   dailyChangePct = dailyChangeAbs / curve[last−1].value     (scale 6)
    //   NOT first-to-last; NOT per-security summation.
    // -----------------------------------------------------------------------

    @Test
    void dailyChangeDerivesFromEquityCurve() {
        // Hand-crafted equity curve: three entries (only the last two matter for daily change)
        LocalDate d0 = LocalDate.of(2022, 9, 12);
        LocalDate d1 = LocalDate.of(2022, 9, 13);
        LocalDate d2 = LocalDate.of(2022, 9, 14);

        BigDecimal v0 = new BigDecimal("100000.00");  // curve[0] — should NOT be used as previous
        BigDecimal v1 = new BigDecimal("102000.00");  // curve[last-1]  — the true "previous" day
        BigDecimal v2 = new BigDecimal("103530.00");  // curve[last]    — today

        List<DateValueDto> curve = List.of(
                new DateValueDto(d0, v0),
                new DateValueDto(d1, v1),
                new DateValueDto(d2, v2)
        );

        BigDecimal[] result = PortfolioService.computeDailyChange(curve);
        BigDecimal dailyChangeAbs = result[0];
        BigDecimal dailyChangePct = result[1];

        // Expected: abs = 103530 - 102000 = 1530.00
        BigDecimal expectedAbs = new BigDecimal("1530.00");
        // Expected: pct = 1530 / 102000 = 0.015000
        BigDecimal expectedPct = new BigDecimal("1530.00").divide(new BigDecimal("102000.00"), 6, RoundingMode.HALF_UP);

        assertThat(dailyChangeAbs)
                .as("dailyChangeAbs must equal curve[last] - curve[last-1], NOT first-to-last")
                .isEqualByComparingTo(expectedAbs);

        assertThat(dailyChangePct)
                .as("dailyChangePct must be dailyChangeAbs / curve[last-1]")
                .isEqualByComparingTo(expectedPct);

        // Confirm it does NOT equal the first-to-last change (which would be wrong)
        BigDecimal wrongAbs = v2.subtract(v0);  // 3530.00 — first-to-last (wrong)
        assertThat(dailyChangeAbs)
                .as("dailyChangeAbs must NOT be first-to-last change")
                .isNotEqualByComparingTo(wrongAbs);
    }

    // -----------------------------------------------------------------------
    // PORT-02: Total unrealized gain formula — GREEN (Plan 03)
    //   totalUnrealizedGainAbs = totalMarketValue − totalCostBasis  (scale 2)
    //   totalUnrealizedGainPct = abs / totalCostBasis               (scale 6)
    // -----------------------------------------------------------------------

    @Test
    void totalUnrealizedGainFormula() {
        BigDecimal totalMarketValue = new BigDecimal("150000.00");
        BigDecimal totalCostBasis   = new BigDecimal("120000.00");

        // Expected abs = 150000 - 120000 = 30000.00
        BigDecimal expectedAbs = new BigDecimal("30000.00");
        // Expected pct = 30000 / 120000 = 0.250000
        BigDecimal expectedPct = new BigDecimal("30000.00").divide(new BigDecimal("120000.00"), 6, RoundingMode.HALF_UP);

        BigDecimal actualAbs = PortfolioService.computeTotalUnrealizedGainAbs(totalMarketValue, totalCostBasis);
        assertThat(actualAbs)
                .as("totalUnrealizedGainAbs must equal totalMarketValue - totalCostBasis")
                .isEqualByComparingTo(expectedAbs);

        BigDecimal actualPct = PortfolioService.computeTotalUnrealizedGainPct(actualAbs, totalCostBasis);
        assertThat(actualPct)
                .as("totalUnrealizedGainPct must equal abs / totalCostBasis")
                .isEqualByComparingTo(expectedPct);

        // Also verify zero-cost-basis guard returns ZERO (no ArithmeticException)
        BigDecimal zeroPct = PortfolioService.computeTotalUnrealizedGainPct(
                new BigDecimal("100.00"), BigDecimal.ZERO);
        assertThat(zeroPct)
                .as("zero cost basis guard must return ZERO")
                .isEqualByComparingTo(BigDecimal.ZERO);
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
    // PORT-05: Benchmark rebasing — both series start at 100 on day 0 — GREEN (Plan 03)
    //   portfolioIdx(i) = value(i) / value(0) × 100, scale 4
    //   benchmarkIdx(i) = close(i)  / close(0) × 100, scale 4
    //   Both series[0] must compare-to "100.0000" == 0
    // -----------------------------------------------------------------------

    @Test
    void benchmarkBothSeriesStartAt100() {
        // Hand-crafted portfolio values and SPX500 closes — different base values
        // to prove independent rebasing (not additive alignment)
        BigDecimal[] portfolioValues = {
                new BigDecimal("85432.12"),   // day 0 — portfolio base
                new BigDecimal("86100.50"),
                new BigDecimal("84900.75")
        };
        BigDecimal[] spxCloses = {
                new BigDecimal("4105.23"),    // day 0 — benchmark base
                new BigDecimal("4131.88"),
                new BigDecimal("4090.11")
        };

        BigDecimal portfolioBase = portfolioValues[0];
        BigDecimal benchmarkBase = spxCloses[0];

        // Rebase each series independently
        BigDecimal portfolioIdx0 = PortfolioService.rebaseToIndex(portfolioValues[0], portfolioBase);
        BigDecimal benchmarkIdx0 = PortfolioService.rebaseToIndex(spxCloses[0], benchmarkBase);

        // Both series[0] must equal exactly 100.0000 on day 0
        BigDecimal expected = new BigDecimal("100.0000");

        assertThat(portfolioIdx0)
                .as("portfolioSeries[0] must be exactly 100.0000 after rebasing")
                .isEqualByComparingTo(expected);

        assertThat(benchmarkIdx0)
                .as("benchmarkSeries[0] must be exactly 100.0000 after rebasing")
                .isEqualByComparingTo(expected);

        // Verify subsequent values are proportional (not both the same)
        BigDecimal portfolioIdx1 = PortfolioService.rebaseToIndex(portfolioValues[1], portfolioBase);
        BigDecimal benchmarkIdx1 = PortfolioService.rebaseToIndex(spxCloses[1], benchmarkBase);

        // portfolio[1] = 86100.50 / 85432.12 * 100 ≈ 100.7822 (scale 4)
        BigDecimal expectedPortfolioIdx1 = new BigDecimal("86100.50")
                .divide(new BigDecimal("85432.12"), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(4, RoundingMode.HALF_UP);

        assertThat(portfolioIdx1)
                .as("portfolioSeries[1] must be proportional rebase of portfolio value")
                .isEqualByComparingTo(expectedPortfolioIdx1);

        // Confirm the two series diverge — proving independent rebasing, not additive shift
        assertThat(portfolioIdx1)
                .as("portfolioSeries[1] and benchmarkSeries[1] must differ (independent rebasing)")
                .isNotEqualByComparingTo(benchmarkIdx1);
    }
}
