---
phase: 04-quant-risk-engine
fixed_at: 2026-06-08T21:05:00Z
review_path: .planning/phases/04-quant-risk-engine/04-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 04: Code Review Fix Report

**Fixed at:** 2026-06-08T21:05:00Z
**Source review:** `.planning/phases/04-quant-risk-engine/04-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (4 Critical + 4 Warning/Info)
- Fixed: 8
- Skipped: 0

---

## Fixed Issues

### CR-01: Sharpe Ratio Denominator

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java`
**Commit:** `fcf6304`
**Applied fix:** Replaced `DescriptiveStatistics stats` (portfolio returns) as the Sharpe denominator with a new `DescriptiveStatistics excessStats` built from the excess return series. The correct formula `mean(r_excess)/std(r_excess)*sqrt(252)` is now used. `stdR` (portfolio returns std) is retained solely for annualised vol. Also added `Logger`/`LoggerFactory` field.

**Why golden values didn't change:** The seeded RF series is near-constant and tiny (~0.01% daily), so `std(excess) ≈ std(portfolio)` within the ±0.001 test tolerance for this specific dataset. The fix is mathematically correct and observable with varying RF (demonstrated in HC-01 hand-computed test).

---

### CR-02: Beta Date-Misalignment

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java`
**Commit:** `fcf6304`
**Applied fix:** Replaced `trimToSameLength(benchmarkReturns, N)` (which took the oldest N benchmark bars regardless of dates) with a `NavigableMap<LocalDate, Double> bmkReturnByDate` keyed on the return's closing date. For each portfolio return at date `curve.get(i+1).date()`, the benchmark return for the same date is looked up and paired. Dates where the benchmark has no bar are paired-dropped from both series.

**Why golden values didn't change:** The seeded benchmark (SPX500) and portfolio both start on the same date (2022-09-12), so `trimToSameLength` and the date-aligned approach produce the same window for this dataset. The fix matters for real portfolios with shorter history than the benchmark.

---

### CR-03: Fama-French Exact-504 Guard

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/FamaFrenchCalculator.java`
**Commit:** `19a7d8f`
**Applied fix:** Removed the `factorRows.size() != nReturns + 1` exact-equality guard entirely. Replaced the positional `factorRows.get(i + 1)` lookup with a `Map<LocalDate, FactorReturn> factorByDate` built via `Collectors.toMap(FactorReturn::getFactorDate, fr -> fr)`. Each return index `i` now looks up the factor row by `curve.get(i + 1).date()`, throwing `IllegalStateException` if no factor row exists for that date (actionable error vs. silent misalignment).

---

### CR-04: Heatmap Tooltip Y-Index Inversion

**Files modified:** `frontend/src/components/CorrelationHeatmap.vue`
**Commit:** `1714580`
**Applied fix:** Changed the tooltip formatter to use `dto.tickers[n - 1 - yi]` instead of `dto.tickers[yi]` for the y-axis ticker name. Since `yAxis.data` is `[...dto.tickers].reverse()`, visual row at data-y-index `yi` corresponds to `tickers[n-1-yi]`. Without this fix, off-diagonal cells in the tooltip displayed the wrong ticker name (e.g., the cell for TSLA/MSFT would say AAPL/MSFT).

---

### WR-02: Z-Score Look-Ahead in CointegrationScanner

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java`
**Commit:** `2266f81`
**Applied fix:** Excluded the current (last) residual from the `DescriptiveStatistics` window used for Z-score normalization. Changed from `new DescriptiveStatistics(residuals)` to `new DescriptiveStatistics(Arrays.copyOf(residuals, residuals.length - 1))`. The `currentSpread` variable is now extracted before building the stats object. For series near `MIN_SPREAD_LENGTH=10`, the buggy version could inflate std by ~10× when the last point is an outlier, making extreme Z-scores appear moderate (hand-computed test HC-10: true Z=14.7 vs buggy Z=1.8 for a 5-element series with a large outlier).

---

### WR-03: Correlation Insufficient-Data Warning

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java`
**Commit:** `fd7562c`
**Applied fix:** Added `log.warn(...)` in the `commonDates.size() < 2` branch so the identity-matrix fallback is observable in production logs. The warning explains that off-diagonal zeros in this case indicate missing data, not genuine zero correlation. Added `Logger`/`LoggerFactory` field to the class.

---

### WR-04: Silent RF Fallback in RiskCalculator

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java`
**Commit:** `fcf6304`
**Applied fix:** Added `log.warn(...)` in the `!rfAligned` branch (when `factorRows.size() != portfolioReturns.length + 1`). The warning includes both the actual and expected counts and directs the operator to check FactorReturn seeding. Previously the fallback to `rf=0` (raw-return Sharpe instead of excess-return Sharpe) was completely silent.

---

### IN-02: NormalDistribution Static Final in CointegrationScanner

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java`
**Commit:** `2266f81`
**Applied fix:** Extracted `new NormalDistribution(0, 1)` from inside `mackinnonPValue()` to a `private static final NormalDistribution STANDARD_NORMAL` field. Removed the per-call instantiation.

---

### CIRCULAR-GOLDEN-VALUE FIX: RiskMathHandComputedTest

**Files modified:** `backend/src/test/java/com/quantlens/analytics/RiskMathHandComputedTest.java` (new), `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java`, `backend/src/test/java/com/quantlens/analytics/FamaFrenchCalculatorTest.java`
**Commit:** `443bb52`
**Applied fix:** Created `RiskMathHandComputedTest` with 10 pure unit tests (no Spring context, no Testcontainers) whose expected values are derived by hand or via external polynomial evaluation, NOT by running the implementation:

| Test | Formula Tested | Hand-Derived Expected Value |
|------|---------------|----------------------------|
| HC-01 | Sharpe: std(excess) denominator | 14.285 (correct) vs 14.326 (buggy) with varying RF |
| HC-02 | Annualized vol: std(r)×sqrt(252) | ≈ 0.39949 for [0.01,-0.02,0.03] |
| HC-03 | Beta: cov/var | 2.0 exactly when b=0.5×p |
| HC-04 | Hist VaR: −p5 percentile | ∈ [0.09, 0.10] for 20-element equispaced series |
| HC-05 | Parametric VaR: z95×σ−μ | 0.026010 for zero-mean 5-point series |
| HC-06 | Max drawdown: peak-to-trough | −25/110≈−0.22727 for [100,110,90,95,85] |
| HC-07 | Log returns: ln(P_t/P_{t−1}) | Math.log(1.1) and Math.log(0.9) reference |
| HC-08 | FF OLS: alpha, beta_mkt, R² | alpha=0, beta=1.25, R²=1.0 for y=1.25×MktRf |
| HC-09 | MacKinnon p-value at τ=−3.0 | ≈ 0.034 (polynomial + statsmodels cross-check) |
| HC-10 | Z-score look-ahead (WR-02) | buggy≈1.773, correct≈14.722 for [1,2,1,2,10] outlier |

Updated `RiskCalculatorTest` and `FamaFrenchCalculatorTest` Javadoc to explicitly label them as REGRESSION anchors and cross-reference `RiskMathHandComputedTest` as the CORRECTNESS anchor.

---

## Build Results

**Backend (`.\mvnw.cmd -B verify`):** 80 tests passed, 2 skipped (`@Disabled` golden-value printers), 0 failures, BUILD SUCCESS

**Frontend (`npm run test`):** 48 tests passed, 9 test files, 0 failures

**Frontend (`npm run build`):** BUILD SUCCESS (791 kB bundle, chunk-size warning is pre-existing)

---

_Fixed: 2026-06-08_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
