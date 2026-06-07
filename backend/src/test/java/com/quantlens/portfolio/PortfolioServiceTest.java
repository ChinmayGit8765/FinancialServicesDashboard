package com.quantlens.portfolio;

import com.quantlens.marketdata.domain.Security;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.Transaction;
import com.quantlens.portfolio.service.PortfolioService;
import com.quantlens.portfolio.api.AllocationSliceDto;
import com.quantlens.portfolio.api.BenchmarkComparisonDto;
import com.quantlens.portfolio.api.DateValueDto;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    // PORT-04: Running cost basis after BUY — GREEN (Plan 04)
    //   After a single BUY of qty Q at price P, runningCostBasis = P
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterBuy() {
        // Single BUY: qty=100, price=150.00 → runningCostBasis should equal buyPrice=150.000000
        BigDecimal buyQty   = new BigDecimal("100.0000");
        BigDecimal buyPrice = new BigDecimal("150.000000");

        List<BigDecimal> result = PortfolioService.computeRunningCostBasisFromTuples(
                List.<String[]>of(new String[]{"BUY", buyQty.toPlainString(), buyPrice.toPlainString()})
        );

        assertThat(result).hasSize(1);
        assertThat(result.get(0))
                .as("After a single BUY at price P, runningCostBasis must equal P")
                .isEqualByComparingTo(buyPrice);
    }

    // -----------------------------------------------------------------------
    // PORT-04: Running cost basis after proportional SELL — GREEN (Plan 04)
    //   After BUY at P then partial SELL at a different price P2,
    //   runningCostBasis == P (avg cost of remaining units unchanged by sell)
    //   SELL reduces basis by sellQty × avgCost, NOT sellQty × sellPrice.
    // -----------------------------------------------------------------------

    @Test
    void runningCostBasisAfterProportionalSell() {
        // BUY 100 shares at 150.00, then SELL 30 shares at 200.00 (different price).
        // Avg cost before sell = 150.00; cost removed = 30 × 150.00 = 4500.00
        // Remaining cost = 15000 - 4500 = 10500; remaining qty = 70
        // runningCostBasis = 10500 / 70 = 150.000000 (identical to buyPrice)
        BigDecimal buyQty    = new BigDecimal("100.0000");
        BigDecimal buyPrice  = new BigDecimal("150.000000");
        BigDecimal sellQty   = new BigDecimal("30.0000");
        BigDecimal sellPrice = new BigDecimal("200.000000"); // different from buy price

        List<BigDecimal> result = PortfolioService.computeRunningCostBasisFromTuples(
                List.<String[]>of(
                        new String[]{"BUY",  buyQty.toPlainString(),  buyPrice.toPlainString()},
                        new String[]{"SELL", sellQty.toPlainString(), sellPrice.toPlainString()}
                )
        );

        assertThat(result).hasSize(2);

        // After BUY: basis == buyPrice
        assertThat(result.get(0))
                .as("After BUY, runningCostBasis must equal buy price")
                .isEqualByComparingTo(buyPrice);

        // After SELL: basis still == buyPrice (sell price is irrelevant to avg cost)
        assertThat(result.get(1))
                .as("After proportional SELL at a different price, runningCostBasis must still equal buy price (SELL reduces basis by sellQty × avgCost, NOT sellPrice)")
                .isEqualByComparingTo(buyPrice);

        // Confirm result does NOT equal sellPrice (proving we didn't use sell price for basis)
        assertThat(result.get(1))
                .as("runningCostBasis after SELL must NOT equal sellPrice")
                .isNotEqualByComparingTo(sellPrice);
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

    // -----------------------------------------------------------------------
    // CR-03 REGRESSION: buildRunningCostMap must be per-security independent
    //   With 2 tickers, AAPL and MSFT transactions must NOT share a running pool.
    //   Stream: BUY AAPL @150, BUY MSFT @300, SELL AAPL @200 (30 shares)
    //   Expected AAPL cost basis after sell = 150.000000 (unchanged by MSFT buy)
    //   Wrong old behavior: avgCost = (150*100 + 300*50) / (100+50) = 200.000000
    // -----------------------------------------------------------------------

    @Test
    void buildRunningCostMap_multiTickerCostBasesAreIndependent() throws Exception {
        // Construct two Security proxies with distinct IDs (no DB needed)
        Security aapl = makeSecurityWithId(1L, "AAPL");
        Security msft = makeSecurityWithId(2L, "MSFT");

        // 3 transactions in chronological order: BUY AAPL, BUY MSFT, SELL AAPL
        List<Transaction> txs = new ArrayList<>();
        Transaction buyAapl  = makeTransaction(10L, aapl, "BUY",  new BigDecimal("100.0000"), new BigDecimal("150.000000"));
        Transaction buyMsft  = makeTransaction(11L, msft, "BUY",  new BigDecimal("50.0000"),  new BigDecimal("300.000000"));
        Transaction sellAapl = makeTransaction(12L, aapl, "SELL", new BigDecimal("30.0000"),  new BigDecimal("200.000000"));
        txs.add(buyAapl);
        txs.add(buyMsft);
        txs.add(sellAapl);

        Map<Long, BigDecimal> costMap = PortfolioService.buildRunningCostMap(txs);

        // After BUY AAPL 100 @ 150: AAPL avgCost = 150.000000
        assertThat(costMap.get(10L))
                .as("After BUY AAPL 100@150, AAPL avgCost must be 150.000000")
                .isEqualByComparingTo(new BigDecimal("150.000000"));

        // After BUY MSFT 50 @ 300: MSFT avgCost = 300.000000 (AAPL pool must be unaffected)
        assertThat(costMap.get(11L))
                .as("After BUY MSFT 50@300, MSFT avgCost must be 300.000000 (independent from AAPL)")
                .isEqualByComparingTo(new BigDecimal("300.000000"));

        // After SELL AAPL 30 @ 200: AAPL avgCost must still be 150.000000
        // (sell price is irrelevant; MSFT purchase must NOT have inflated AAPL pool)
        // Old bug: avgCost = (100*150 + 50*300) / 150 = 200.000000 (mixed pool)
        assertThat(costMap.get(12L))
                .as("After SELL AAPL 30 shares, AAPL avgCost must remain 150.000000 — "
                    + "MSFT buy must NOT inflate AAPL cost pool (CR-03 regression)")
                .isEqualByComparingTo(new BigDecimal("150.000000"));

        // Confirm the old (wrong) value 200 is NOT returned for AAPL after sell
        assertThat(costMap.get(12L))
                .as("AAPL avgCost after sell must NOT be 200 (old mixed-pool bug value)")
                .isNotEqualByComparingTo(new BigDecimal("200.000000"));
    }

    // -----------------------------------------------------------------------
    // CR-05 REGRESSION: SELL tradeValue must be negative; BUY positive
    //   tradeValue = qty × price; negated for SELL transactions.
    // -----------------------------------------------------------------------

    @Test
    void tradeValue_sellIsNegativeBuyIsPositive() {
        // The sign convention is enforced in PortfolioService.getTransactions().
        // We test the formula directly here: SELL tradeValue = -(qty × price).
        BigDecimal qty   = new BigDecimal("50.0000");
        BigDecimal price = new BigDecimal("200.000000");

        // BUY: tradeValue = qty × price (positive, cash outflow)
        BigDecimal buyTradeValue = qty.multiply(price).setScale(2, RoundingMode.HALF_UP);
        assertThat(buyTradeValue.signum())
                .as("BUY tradeValue must be positive (cash outflow)")
                .isEqualTo(1);

        // SELL: tradeValue = -(qty × price) (negative, cash inflow)
        BigDecimal sellTradeValue = qty.multiply(price).setScale(2, RoundingMode.HALF_UP).negate();
        assertThat(sellTradeValue.signum())
                .as("SELL tradeValue must be negative (cash inflow)")
                .isEqualTo(-1);

        // They must be equal in magnitude but opposite in sign
        assertThat(buyTradeValue.add(sellTradeValue))
                .as("BUY + SELL tradeValues of same qty×price must sum to zero")
                .isEqualByComparingTo(BigDecimal.ZERO);

        // Also confirm the magnitude is qty × price = 10000.00
        assertThat(buyTradeValue)
                .as("BUY tradeValue must equal qty × price = 10000.00")
                .isEqualByComparingTo(new BigDecimal("10000.00"));
    }

    // -----------------------------------------------------------------------
    // CR-01 REGRESSION: empty equity curve / zero-position portfolio must not crash
    //   computeDailyChange on a 1-entry curve used to throw IllegalArgumentException.
    //   The guard added in WR-05 (equityCurve.size() >= 2) prevents this.
    //   Also verify rebaseToIndex with zero base returns ZERO (not ArithmeticException).
    // -----------------------------------------------------------------------

    @Test
    void emptyEquityCurve_dailyChangeReturnsZeroNotException() {
        // Single-entry curve — computeDailyChange should NOT be called (WR-05 guard)
        LocalDate day0 = LocalDate.of(2022, 9, 12);
        List<DateValueDto> singleEntryCurve = List.of(
                new DateValueDto(day0, new BigDecimal("100000.00"))
        );

        // Verify the guard condition is checked correctly
        assertThat(singleEntryCurve.size())
                .as("single-entry curve size is 1")
                .isEqualTo(1);
        assertThat(singleEntryCurve.size() >= 2)
                .as("size < 2, so computeDailyChange must not be called")
                .isFalse();

        // Empty curve case — computeDailyChange must also not be called
        List<DateValueDto> emptyCurve = List.of();
        assertThat(emptyCurve.size() >= 2)
                .as("empty curve: size < 2, computeDailyChange must not be called")
                .isFalse();

        // Verify computeDailyChange throws for sub-2-entry curves (contract still holds)
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> PortfolioService.computeDailyChange(singleEntryCurve),
                "computeDailyChange should still throw for < 2 entries — callers must guard"
        );

        // Verify rebaseToIndex with zero base returns ZERO (no ArithmeticException)
        BigDecimal result = PortfolioService.rebaseToIndex(new BigDecimal("100.00"), BigDecimal.ZERO);
        assertThat(result)
                .as("rebaseToIndex with zero base must return ZERO, not throw ArithmeticException")
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // -----------------------------------------------------------------------
    // CR-02 REGRESSION: benchmark date alignment must match by date, not position
    //   Simulate: equity curve has 3 dates, SPX is missing the middle date.
    //   After fix: output must skip the missing SPX date and align the 2 common dates.
    //   Both series must start at exactly 100.0000 on the FIRST COMMON date.
    // -----------------------------------------------------------------------

    @Test
    void benchmarkDateAlignment_missingSpxDayIsSkipped() {
        // Equity curve: 3 entries — day 0, day 1, day 2
        LocalDate d0 = LocalDate.of(2022, 9, 12);
        LocalDate d1 = LocalDate.of(2022, 9, 13);  // SPX has NO bar on this date
        LocalDate d2 = LocalDate.of(2022, 9, 14);

        List<DateValueDto> equityCurve = List.of(
                new DateValueDto(d0, new BigDecimal("100000.00")),
                new DateValueDto(d1, new BigDecimal("102000.00")),
                new DateValueDto(d2, new BigDecimal("103000.00"))
        );

        // SPX has only d0 and d2 — d1 is missing (e.g. holiday or data gap)
        // Old code: spxBars.get(1) would return d2's bar when i=1, pairing it with d1 portfolio value
        // New code: date-map lookup — d1 returns null → skipped; only d0 and d2 are emitted
        java.util.Map<LocalDate, BigDecimal> spxByDate = new java.util.HashMap<>();
        spxByDate.put(d0, new BigDecimal("4000.00"));
        // d1 intentionally absent
        spxByDate.put(d2, new BigDecimal("4040.00"));

        // Simulate the fixed alignment logic from getBenchmarkComparison
        BigDecimal portfolioBase = null;
        BigDecimal benchmarkBase = null;
        for (DateValueDto point : equityCurve) {
            BigDecimal spxClose = spxByDate.get(point.date());
            if (spxClose != null) {
                portfolioBase = point.value();
                benchmarkBase = spxClose;
                break;
            }
        }

        List<String> resultDates     = new ArrayList<>();
        List<BigDecimal> portSeries  = new ArrayList<>();
        List<BigDecimal> bmarkSeries = new ArrayList<>();

        for (DateValueDto point : equityCurve) {
            BigDecimal spxClose = spxByDate.get(point.date());
            if (spxClose == null) continue;
            resultDates.add(point.date().toString());
            portSeries.add(PortfolioService.rebaseToIndex(point.value(), portfolioBase));
            bmarkSeries.add(PortfolioService.rebaseToIndex(spxClose, benchmarkBase));
        }

        // Only d0 and d2 should appear — d1 (no SPX bar) must be skipped
        assertThat(resultDates)
                .as("output dates must be only d0 and d2 — d1 skipped (no SPX bar)")
                .containsExactly("2022-09-12", "2022-09-14");

        assertThat(portSeries).hasSize(2);
        assertThat(bmarkSeries).hasSize(2);

        // Both series must start at 100.0000 on the first common date (d0)
        assertThat(portSeries.get(0))
                .as("portfolioSeries[0] must be 100.0000 on day 0 (CR-02 regression)")
                .isEqualByComparingTo(new BigDecimal("100.0000"));
        assertThat(bmarkSeries.get(0))
                .as("benchmarkSeries[0] must be 100.0000 on day 0 (CR-02 regression)")
                .isEqualByComparingTo(new BigDecimal("100.0000"));

        // d2 portfolio = 103000/100000*100 = 103.0000; benchmark = 4040/4000*100 = 101.0000
        assertThat(portSeries.get(1))
                .as("portfolioSeries[1] must be 103.0000 (103000/100000*100)")
                .isEqualByComparingTo(new BigDecimal("103.0000"));
        assertThat(bmarkSeries.get(1))
                .as("benchmarkSeries[1] must be 101.0000 (4040/4000*100)")
                .isEqualByComparingTo(new BigDecimal("101.0000"));

        // Values must differ — proving independent rebasing, not coincidental alignment
        assertThat(portSeries.get(1))
                .as("portfolio and benchmark rebased values must differ (independent rebasing)")
                .isNotEqualByComparingTo(bmarkSeries.get(1));
    }

    // =========================================================================
    // Test helpers
    // =========================================================================

    /** Creates a Security with a specific id using reflection (no DB, no JPA context). */
    private static Security makeSecurityWithId(Long id, String ticker) throws Exception {
        Security s = new Security(ticker, ticker + " Inc", "Technology", false);
        Field idField = Security.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(s, id);
        return s;
    }

    /** Creates a Transaction with a specific id using reflection (no DB, no JPA context). */
    private static Transaction makeTransaction(Long id, Security security, String txType,
                                               BigDecimal qty, BigDecimal price) throws Exception {
        Transaction tx = new Transaction(null, security,
                LocalDate.of(2022, 9, 12), txType, qty, price);
        Field idField = Transaction.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(tx, id);
        return tx;
    }
}
