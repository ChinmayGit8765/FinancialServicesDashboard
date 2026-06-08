---
phase: 04-quant-risk-engine
reviewed: 2026-06-08T00:00:00Z
depth: deep
files_reviewed: 18
files_reviewed_list:
  - backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java
  - backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java
  - backend/src/main/java/com/quantlens/analytics/service/FamaFrenchCalculator.java
  - backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java
  - backend/src/main/java/com/quantlens/analytics/service/DateValueDto.java
  - backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java
  - backend/src/main/java/com/quantlens/analytics/api/RiskScorecardDto.java
  - backend/src/main/java/com/quantlens/analytics/api/VarResultDto.java
  - backend/src/main/java/com/quantlens/analytics/api/CorrelationMatrixDto.java
  - backend/src/main/java/com/quantlens/analytics/api/AttributionDto.java
  - backend/src/main/java/com/quantlens/analytics/api/PairResultDto.java
  - backend/src/main/java/com/quantlens/analytics/package-info.java
  - frontend/src/api/portfolio.ts
  - frontend/src/api/analytics.ts
  - frontend/src/stores/portfolio.ts
  - frontend/src/components/RiskScorecard.vue
  - frontend/src/components/CorrelationHeatmap.vue
  - frontend/src/components/AttributionChart.vue
  - frontend/src/components/PairsTable.vue
findings:
  critical: 4
  warning: 4
  info: 2
  total: 10
status: issues_found
---

# Phase 04: Code Review Report

**Reviewed:** 2026-06-08
**Depth:** deep
**Files Reviewed:** 18 (19 physical — analytics.ts counted separately from portfolio.ts)
**Status:** issues_found

## Summary

The quantitative risk engine is a substantial implementation with good structural choices: log returns, 252-day annualization, Hipparchus for statistics, ADF on residual spread, MacKinnon polynomial p-values, and IDOR prevention via principal-only portfolio resolution. The Fama-French OLS setup is textbook-correct (no manual intercept column, correct i+1 alignment).

However, four blockers compromise mathematical correctness and one breaks runtime availability:

1. The Sharpe ratio denominator uses std(portfolio returns) instead of std(excess returns) — a subtle but material formula error that inflates Sharpe when RF is non-trivial.
2. Beta is computed against date-misaligned benchmark returns — the benchmark series is truncated from the beginning (oldest history) rather than aligned to the portfolio's observation window.
3. The Fama-French factor-alignment guard is an exact equality check that throws on any portfolio whose equity curve does not produce exactly 503 returns — i.e., every real portfolio that isn't exactly 504 bars long.
4. The ECharts heatmap tooltip reports the wrong ticker pair because the y-index is not adjusted for the reversed y-axis.

The golden value tests pass because they are generated from the same buggy implementation (the printer prints, the tests assert those printed values). Tests cannot validate math correctness when the golden values are derived from the code under test itself.

---

## Critical Issues

### CR-01: Sharpe Ratio — Denominator Is std(portfolio returns) Not std(excess returns)

**File:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java:135-139`

**Issue:** `DescriptiveStatistics stats` is constructed from `portfolioReturns` (line 135), and `stdR = stats.getStandardDeviation()` is std(r_portfolio). The Sharpe numerator is `meanExcess = mean(r - rf)`, but the denominator is `std(r)`, not `std(r - rf)`. The canonical Sharpe formula is:

```
Sharpe = mean(r_excess) / std(r_excess) × √252
```

When RF is non-zero (e.g., 5% annually ≈ 0.02% daily), `std(r - rf)` ≈ `std(r)` for equity portfolios because subtracting a near-constant RF barely moves the variance. However the formula is still wrong:

- The comment at line 137 says "sample std (n-1) — correct for Sharpe", which is correct for the denominator choice (sample) but the wrong series (portfolio vs. excess).
- The golden tests pass because the seeded RF values are small enough that `std(r_excess) ≈ std(r)` within the ±0.001 tolerance — the bug is hidden by loose tolerances.

**Fix:**
```java
// Replace lines 135–139 with:
DescriptiveStatistics stats = new DescriptiveStatistics(portfolioReturns);
DescriptiveStatistics excessStats = new DescriptiveStatistics(excessReturns);
double meanExcess = excessStats.getMean();
double stdExcess  = excessStats.getStandardDeviation(); // std of EXCESS returns — correct Sharpe denominator
double stdR       = stats.getStandardDeviation();       // std of portfolio returns — used only for vol
double sharpe = (stdExcess == 0.0) ? Double.NaN : (meanExcess / stdExcess) * Math.sqrt(252.0);
double annualizedVol = stdR * Math.sqrt(252.0);         // vol is std of portfolio returns, not excess
```

The `dailySigma` used in parametric VaR (line 184) must remain `stats.getStandardDeviation()` (portfolio returns), which is already correct.

---

### CR-02: Beta Computed Against Date-Misaligned Benchmark Returns

**File:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java:148-153`

**Issue:** `trimToSameLength(benchmarkReturns, portfolioReturns.length)` takes the **first** N elements of `benchmarkReturns` (line 334–337). The portfolio equity curve is built on the intersection of common trading dates among its holdings. The benchmark returns are built from all available bars via `buildCurveFromSingleSecurity` (line 108), which starts from the benchmark's earliest data. If the benchmark has 756 bars (3 years) and the portfolio has 252 returns (1 year), `trimToSameLength` returns the benchmark returns from 2 years ago — not the same calendar window as the portfolio.

The result: beta is computed as `cov(r_portfolio_2024, r_benchmark_2021) / var(r_benchmark_2021)`, which is meaningless. Only by coincidence (if portfolio and benchmark history start at the same date) is this correct.

**Fix:** Align benchmark returns to the portfolio's actual date range before computing beta:

```java
// After computing benchmarkReturns, find the portfolio start/end dates.
// Use the equity curve dates to slice the benchmark series at matching dates.
// The simplest robust approach: build benchmark returns as a NavigableMap<LocalDate, Double>,
// then extract the returns for exactly the dates used by the portfolio curve.

// Alternatively, build a date-keyed TreeMap for benchmark returns and iterate over
// the same sortedDates used to build portfolioReturns:
NavigableMap<LocalDate, Double> bmkReturnByDate = new TreeMap<>();
List<DateValueDto> bmkCurve = buildCurveFromSingleSecurity(bmkBars);
for (int i = 1; i < bmkCurve.size(); i++) {
    double p0 = bmkCurve.get(i-1).value().doubleValue();
    double p1 = bmkCurve.get(i).value().doubleValue();
    LocalDate d = bmkCurve.get(i).date();
    bmkReturnByDate.put(d, p0 == 0.0 ? 0.0 : Math.log(p1 / p0));
}
// Then, for each date in the portfolio's equity curve (curve.get(i).date() for i >= 1),
// look up bmkReturnByDate.get(date) to build the aligned benchmark return array.
```

This bug also affects the golden-value tests: `GOLDEN_BETA = 1.94917921` was derived from the misaligned implementation, so that test value is itself wrong.

---

### CR-03: Fama-French Factor Alignment Guard Throws on Non-504-Bar Portfolios

**File:** `backend/src/main/java/com/quantlens/analytics/service/FamaFrenchCalculator.java:102-107`

**Issue:** The guard at line 102 requires `factorRows.size() == nReturns + 1` as an exact equality. This hardcodes the assumption that every portfolio has exactly 503 log returns (from 504 bars). In practice:

- A portfolio containing a stock with fewer than 504 trading days of history will have `nReturns < 503`.
- The `factorReturnRepository.findAllByOrderByFactorDateAsc()` always returns all 504 rows.
- So `factorRows.size()` (504) ≠ `nReturns + 1` (e.g., 301) → `IllegalStateException` is thrown for every real-world portfolio that isn't exactly 504 bars long.

The alignment logic should align factor rows to the portfolio's actual return dates, not require exact cardinality matching. The current i+1 offset logic is only correct when factorRows length = nReturns + 1.

**Fix:** Replace exact-equality guard and alignment with date-based lookup:

```java
// Instead of relying on positional i+1 offset, build a date-keyed map of factor rows.
Map<LocalDate, FactorReturn> factorByDate = factorRows.stream()
        .collect(Collectors.toMap(FactorReturn::getFactorDate, fr -> fr));

// For each return index i (corresponding to curve dates [i] → [i+1]):
for (int i = 0; i < nReturns; i++) {
    LocalDate returnDate = curve.get(i + 1).date(); // the "closing" date for return i
    FactorReturn fr = factorByDate.get(returnDate);
    if (fr == null) {
        throw new IllegalStateException(
            "No factor row for date " + returnDate + ". " +
            "Ensure FactorReturn table covers the full portfolio history.");
    }
    // ... rest of loop body unchanged
}
// Remove the factorRows.size() != nReturns + 1 guard entirely.
```

Note: the same fragility exists in `RiskCalculator.java` lines 118–128 (RF alignment), but there the fallback to rf=0 prevents a throw — just silent degradation. Still, date-based lookup is the correct approach in both places.

---

### CR-04: ECharts Heatmap Tooltip Shows Wrong Ticker Pair (Y-Axis Inversion Not Applied to Tooltip Index)

**File:** `frontend/src/components/CorrelationHeatmap.vue:24-35`

**Issue:** The heatmap data is built as `data.push([j, i, dto.matrix[i][j]])` where `j` = x-index, `i` = y-index (original row index). The yAxis is declared as `data: [...dto.tickers].reverse()`. In ECharts, `yAxis.data[0]` renders at the **bottom** of the chart, so the reversed array puts `tickers[n-1]` at the bottom (index 0) and `tickers[0]` at the top (index n-1). The tooltip formatter at line 33–35:

```javascript
const [xi, yi, v] = params.data
return `${dto.tickers[yi]} / ${dto.tickers[xi]}: ${v.toFixed(3)}`
```

`yi` is the original matrix row index `i`. But the visual cell at data-y-index `i` is rendered against `yAxis.data[i]` = `tickers[n-1-i]` (reversed). So the tooltip displays `dto.tickers[yi]` = `tickers[i]`, but the cell is visually labeled `tickers[n-1-i]`. For a 5-security matrix, the top-left cell (matrix[0][0]) displays at visual position (x=0, y=4) in ECharts terms because y-index 0 maps to the bottom. The tooltip would show "tickers[0] / tickers[0]" which is the diagonal, but it's not rendered at the visual top-left.

More critically, for off-diagonal cells, the tooltip says `tickers[i] / tickers[j]` but the visual row being hovered is `tickers[n-1-i]`.

**Fix:** Adjust the tooltip y-lookup to account for the reversed axis:

```javascript
tooltip: {
  formatter: (params: any) => {
    const [xi, yi, v] = params.data
    const n = dto.tickers.length
    // yi is the original row index; yAxis is reversed so visual row yi = tickers[n-1-yi]
    return `${dto.tickers[n - 1 - yi]} / ${dto.tickers[xi]}: ${v.toFixed(3)}`
  },
},
```

Alternatively, remove the yAxis reversal and instead push data as `[j, n-1-i, value]` to achieve the same visual top-left = matrix[0][0] placement without needing to adjust the tooltip.

---

## Warnings

### WR-01: CVaR Filter Uses `<=` Which May Include Observations at Exactly the 5th Percentile Twice

**File:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java:193-199`

**Issue:** The CVaR (Expected Shortfall) calculation at line 195 filters `r <= p5threshold` where `p5threshold = stats.getPercentile(5.0)`. The `getPercentile(5.0)` return value is itself in the tail (it's a return at or near the 5th percentile). Using `<=` means the boundary observation is included in the tail average. This is correct by convention — CVaR should average all returns at or below the VaR threshold. No numerical error, but it is worth documenting.

More importantly: `p5threshold` is a negative number (left tail for an equity portfolio). `histVarPct = -stats.getPercentile(5.0)` is positive (line 173). The CVaR filter correctly uses `r <= p5threshold` (negative threshold, capturing losses). No bug here — **flagging only because** the CVaR filter condition `r <= p5threshold` and the VaR negation `histVarPct = -p5threshold` are asymmetric in sign. A future maintainer may accidentally negate the filter.

**Fix:** Add a defensive assertion:
```java
assert p5threshold <= 0.0 : "5th percentile of equity log-returns should be negative";
```

---

### WR-02: `DescriptiveStatistics` for Z-Score in CointegrationScanner Uses Sample Std While ADF Uses Hipparchus OLS (Population Implicit)

**File:** `backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java:198-202`

**Issue:** The Z-score computation uses `DescriptiveStatistics.getStandardDeviation()` which is the **sample** standard deviation (Bessel-corrected, n-1 denominator). This is the conventional choice for Z-scores. The issue is that the Z-score is computed over the entire residual series including the most recent data point — specifically `currentSpread = residuals[residuals.length - 1]` is included in the mean and std calculation, then used as the query point.

This introduces a mild look-ahead contamination: the Z-score that triggers a trading signal uses the current day's spread in the normalization statistics. For a series of length 500, the effect is negligible (< 0.2%). For shorter series (near `MIN_SPREAD_LENGTH = 10`), including the current value in mean/std when that same value is the Z-score query point creates a 10% look-ahead bias in the normalization. For a 10-point series with a large outlier as the last point, the outlier inflates std and deflates the Z-score, making an extreme value appear moderate.

**Fix:** Exclude the last observation from the normalization statistics:

```java
// Compute mean and std over the historical portion only (exclude current spread)
DescriptiveStatistics ds = new DescriptiveStatistics(
        Arrays.copyOf(residuals, residuals.length - 1));
double spreadMean = ds.getMean();
double spreadStd  = ds.getStandardDeviation();
double currentSpread = residuals[residuals.length - 1];
double zScore = (spreadStd == 0.0) ? 0.0 : (currentSpread - spreadMean) / spreadStd;
```

---

### WR-03: `CorrelationCalculator` — Single-Security Portfolio Returns Identity Matrix Without Warning

**File:** `backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java:117-119`

**Issue:** When `commonDates.size() < 2`, the method returns an identity matrix (diagonal 1, off-diagonal 0). For a single holding (1×1 matrix) this is correct. For a 2-security portfolio where the two securities share only one common trading date (dates intersection has size 1), the return is a 2×2 identity matrix `[[1,0],[0,1]]` which implies zero correlation. The zero off-diagonal is mathematically meaningless (insufficient data) but will be silently rendered in the heatmap as blue cells (correlation = 0), potentially misleading the user.

The same condition also silently degrades for a portfolio that genuinely has 2+ securities but data gaps reduce common dates to 0 or 1.

**Fix:** Return a null/empty signal to the frontend when there is insufficient data, or add a `dataQuality` field:

```java
if (commonDates == null || commonDates.size() < 2) {
    // Log warning: insufficient common dates for meaningful correlation
    log.warn("Insufficient common trading dates ({}) for portfolio {}: returning identity matrix",
             commonDates == null ? 0 : commonDates.size(), "N/A");
    return new CorrelationMatrixDto(tickers, identityMatrix(tickers.size()));
}
```

At minimum, document clearly in the DTO that zero off-diagonal entries may mean "insufficient data" rather than "truly uncorrelated."

---

### WR-04: `RiskCalculator` — RF Alignment Falls Back Silently to rf=0 With No Log/Metric

**File:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java:118-128`

**Issue:** When `!rfAligned` (line 119), the code silently falls back to `rfDailyArr[i] = 0.0` for all entries, effectively computing a raw return Sharpe instead of an excess-return Sharpe. The comment says "documented; factorRows should be 504 for 503 returns" but there is no log statement. In production, if the `FactorReturn` table is mis-seeded or partially populated, the Sharpe ratio will be wrong with no observable signal — no log at WARN level, no health indicator, no error response.

The same silent degradation affects the Fama-French excess-return computation if the RF fallback fires there (it would throw instead due to the guard in FF, but the RF fallback in RiskCalculator is silent).

**Fix:**
```java
if (!rfAligned) {
    log.warn("RiskCalculator: factor rows count ({}) does not equal portfolioReturns.length + 1 ({}). " +
             "Falling back to rf=0 for Sharpe computation. " +
             "Check FactorReturn seeding.", factorRows.size(), portfolioReturns.length + 1);
}
```

---

## Info

### IN-01: `portfolio.ts` Store Missing Analytics API Fetch Functions in `api/portfolio.ts` — Split Is Inconsistent

**File:** `frontend/src/stores/portfolio.ts:67-237`

**Issue:** All analytics fetch calls in the Pinia store (`fetchRisk`, `fetchCorrelation`, `fetchAttribution`, `fetchPairs`) call `axios.get` directly at lines 171, 190, 209, 228 — they bypass the typed wrappers that the non-analytics calls use. The existing `frontend/src/api/portfolio.ts` defines `fetchHoldings()`, `fetchPnl()`, etc. as standalone typed functions. However, `frontend/src/api/analytics.ts` only defines DTO interfaces and no fetch functions — the store contains the actual axios calls inline.

This is an inconsistency: portfolio resources have typed API functions in `api/portfolio.ts`; analytics resources have only DTO types in `api/analytics.ts`. While not a bug, it means the two files have different scopes and any future API URL change for analytics requires editing the store file rather than the api file.

**Fix:** Move the analytics fetch calls to `api/analytics.ts` following the same pattern as `api/portfolio.ts`:
```typescript
// api/analytics.ts
export async function fetchRisk(): Promise<RiskScorecardDto> {
  const { data } = await axios.get<RiskScorecardDto>('/api/portfolio/risk')
  return data
}
// ... same for fetchCorrelation, fetchAttribution, fetchPairs
```

---

### IN-02: `CointegrationScanner` — `mackinnonPValue` Creates a `NormalDistribution` Instance on Every Call

**File:** `backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java:293-311`

**Issue:** `mackinnonPValue` is a `static` method called once per candidate pair (up to 20 times). Each call allocates `new NormalDistribution(0, 1)`. This is a trivial instantiation cost, but since the method is static and the distribution is always the same N(0,1), the instance should be a static final field:

```java
private static final NormalDistribution STANDARD_NORMAL = new NormalDistribution(0, 1);
```

**Fix:** Extract the `NormalDistribution` to a `private static final` constant.

---

## Cross-Cutting Notes

### Golden Value Tests Validate Against the Implementation's Own Output

The `AnalyticsGoldenValuePrinterTest` computes golden values by running the implementation against seeded data and printing the results. `RiskCalculatorTest` and `FamaFrenchCalculatorTest` then assert the same implementation produces the same output. This arrangement means:

- **CR-01 (wrong Sharpe std)**: The golden `GOLDEN_SHARPE = 0.36442669` was derived from the buggy formula. The test passes but validates the wrong value.
- **CR-02 (misaligned beta)**: `GOLDEN_BETA = 1.94917921` was derived from misaligned benchmark data. The test passes but the value is incorrect.
- **CR-03 (FF guard)**: Tests only run against the 504-bar seeded portfolio, which is the only case where the guard does not throw.

Tests that derive their expected values from the code under test cannot catch math errors. To catch CR-01 and CR-02, the golden values must be computed by an independent reference implementation (e.g., Python/pandas/statsmodels) using the same seeded input data.

---

_Reviewed: 2026-06-08_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
