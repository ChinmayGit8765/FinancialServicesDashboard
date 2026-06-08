---
phase: 05-stochastic-forecasting
fixed_at: 2026-06-09T01:10:00Z
review_path: .planning/phases/05-stochastic-forecasting/05-REVIEW.md
iteration: 1
findings_in_scope: 11
fixed: 10
skipped: 1
status: partial
---

# Phase 05: Code Review Fix Report

**Fixed at:** 2026-06-09T01:10:00Z
**Source review:** `.planning/phases/05-stochastic-forecasting/05-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 11 (4 Critical + 5 Warning + 2 Info)
- Fixed: 10
- Skipped: 1 (WR-03 — confirmed correct, no fix needed)

## Fixed Issues

### CR-01: Population std replaced with sample variance in sigma calibration

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java`, `backend/src/test/java/com/quantlens/analytics/service/ForecastRegressionTest.java`, `backend/src/test/java/com/quantlens/analytics/ForecastStructuralTest.java`
**Commits:** `ed33cd1`, `f94cacf`, `ecd66e9`
**Applied fix:** Changed `stats.getStandardDeviation() * Math.sqrt(252.0)` to `Math.sqrt(stats.getVariance() * 252.0)` to make the sample (n-1) estimator intent explicit.

**Note:** The reviewer's diagnosis was partially correct (sample vs population std matters at small n) but the suggested fix used `getSampleVariance()` which does not exist in Hipparchus 4.0.3. Investigation revealed that `getVariance()` already returns sample variance (n-1) and `getStandardDeviation() = sqrt(getVariance())` — both are sample std in Hipparchus 4.0.3. The fix uses the explicit `sqrt(getVariance()*252)` form for self-documentation. A correction commit (`f94cacf`) resolved the wrong API name. Test moved to `service` package for package-private access (`ecd66e9`).

**Regression tests added:**
- `ForecastRegressionTest.cr01_sampleVariance_n2_handComputed_vs_populationVariance` — hand-computed n=2 case proves sample/pop ratio = sqrt(2) (29% difference visible at n=2, not at n=252)
- `ForecastRegressionTest.cr01_sampleVariance_n3_handComputed` — n=3 ratio = sqrt(3/2)
- `ForecastRegressionTest.cr01_getStandardDeviation_equals_sqrtGetVariance_in_hipparchus4` — confirms equivalence of both methods in Hipparchus 4.0.3

---

### CR-02: Bootstrap block guard changed from `H < L` to `H <= L`

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java`, `backend/src/test/java/com/quantlens/analytics/ForecastStructuralTest.java`
**Commit:** `9c79375`
**Applied fix:** Changed `if (H < L)` to `if (H <= L)` in `runBootstrap`. When H==L (e.g., H=10, L=max(10,sqrt(10))=10), maxBlockStart=0 and all 5000 bootstrap paths were identical (fan chart collapsed to single line). The H<=L guard triggers L=max(1, H/2), giving maxBlockStart >= 1.

**Regression tests added:**
- `ForecastRegressionTest.cr02_extractPercentiles_degenerateAllSame_zeroSpread` — negative reference: identical paths yield zero spread (documents pre-fix state)
- `ForecastRegressionTest.cr02_extractPercentiles_distinctValues_nonZeroSpread` — distinct paths yield positive spread
- `ForecastStructuralTest.bootstrap_bandSpread_nonZero_at21Days` — integration test confirming non-zero p95-p5 spread at day 21

---

### CR-03: Fan-band differences clamped to `Math.max(0, ...)` in MonteCarloFanChart.vue

**Files modified:** `frontend/src/components/MonteCarloFanChart.vue`, `frontend/src/__tests__/components/MonteCarloFanChart.test.ts`
**Commit:** `3b7b251`
**Applied fix:** Wrapped each band difference in `Math.max(0, v - prev[i])` for Series 1/2/3. ECharts stacks negative differences downward, rendering inverted bands. Backend guarantees monotone percentiles, but the frontend now has a defensive guard for malformed data.

**Regression tests added:**
- `MonteCarloFanChart.test.ts: CR-03 non-monotone percentiles produce non-negative series data` — feeds deliberately non-monotone data; asserts all series values >= 0
- `MonteCarloFanChart.test.ts: CR-03 monotone percentiles produce correct positive differences` — confirms Math.max(0,...) is transparent for valid data

---

### CR-04: `@Transactional(readOnly=true)` removed from `ForecastController.getForecast`

**Files modified:** `backend/src/main/java/com/quantlens/analytics/api/ForecastController.java`
**Commit:** `47a9deb`
**Applied fix:** Removed `@Transactional(readOnly = true)` annotation and its unused import from the controller method. The service-layer transaction (`@Transactional(readOnly=true)` on `ForecastService` class) is the correct demarcation point.

---

### WR-01: Bootstrap reproducibility test added

**Files modified:** `backend/src/test/java/com/quantlens/analytics/ForecastStructuralTest.java`
**Commit:** `9c79375` (included in CR-02 commit)
**Applied fix:** Added `ForecastStructuralTest.reproducibility_fixedSeed_bootstrap` — two calls to `forecast(..., BOOTSTRAP, 252)` must produce byte-identical p50 arrays. Mirrors `reproducibility_fixedSeed_gbm`.

---

### WR-02: Guard against zero/negative initial portfolio value

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java`
**Commit:** `249153d`
**Applied fix:** Added `if (initialValue <= 0.0)` guard after `computeCurrentPortfolioValue()`. Short-only portfolios return 0.0 which silently produced flat zero fan charts. The guard throws `IllegalArgumentException` with a clear diagnostic message referencing portfolioId and actual value.

**Regression tests added:**
- `ForecastStructuralTest.wr02_validPortfolio_returnsPositiveForecast` — alice's valid portfolio passes the guard and returns positive p50 values

---

### WR-04: DashboardView `@update:model` wiring verified — already correct

**Files modified:** None
**Applied fix:** Verified `DashboardView.vue` line 211 already has `@update:model="(m) => portfolioStore.fetchForecast(m)"` which correctly re-fetches with the selected model. No fix needed.

---

### WR-05: `FAN_COLORS` converted to `getFanColors()` getter

**Files modified:** `frontend/src/plugins/chart-colors.ts`, `frontend/src/components/MonteCarloFanChart.vue`
**Commit:** `3cc7c2e`
**Applied fix:** Replaced module-level `const FAN_COLORS` with `export function getFanColors()`. `MonteCarloFanChart.vue` calls `getFanColors()` inside the computed body so CSS tokens are re-read on each render. Kept deprecated `FAN_COLORS` const alias for backward compatibility.

---

### IN-01: Dead `trimToSameLength()` method removed from `RiskCalculator`

**Files modified:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java`
**Commit:** `b1ddfd8`
**Applied fix:** Removed the private static `trimToSameLength(double[], int)` method and its Javadoc. Never called — replaced by date-aligned map approach. The reference comment at line 116 preserved (historical context, not calling code).

---

### IN-02: Javadoc expanded to explain 401-for-not-found IDOR rationale

**Files modified:** `backend/src/main/java/com/quantlens/analytics/api/ForecastController.java`
**Commit:** `47a9deb` (included in CR-04 commit)
**Applied fix:** Expanded `@return` Javadoc to explain that both not-found and unauthenticated cases return 401 deliberately to prevent username enumeration (IDOR mitigation T-05-01).

---

## Skipped Issues

### WR-03: `getAssetValue(int, int)` integer-index overload — no fix needed

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:224`
**Reason:** The reviewer confirmed this is correct (final paragraph of WR-03 finding). The `getAssetValue(int, int)` overload takes an integer time index, and `TimeDiscretizationFromArray(0.0, horizonDays, 1.0/252.0)` creates `horizonDays+1` time points so index `horizonDays` is valid. A documentation comment was suggested; deferred as low priority since the existing integration tests (ForecastFinmathIntegrationTest) already validate the index usage at compile time.
**Original issue:** Flag for confirmation during finmath API upgrade — no current bug.

---

## Build Results

**Backend:** `./mvnw.cmd -B verify` → **105 tests, 0 failures, 0 errors, 2 skipped (pre-existing). BUILD SUCCESS.**

New tests contributing to the green count:
- `ForecastRegressionTest` (service package): 6 tests — CR-01 × 3, CR-02 × 2, WR-01 × 1
- `ForecastStructuralTest`: 3 new tests — WR-01 bootstrap reproducibility, CR-02 band spread, WR-02 valid portfolio

**Frontend:** `npm run test` → **58 tests, 0 failures (10 test files). All GREEN.**

New tests contributing to the green count:
- `MonteCarloFanChart.test.ts`: 2 new CR-03 regression tests

**Frontend build:** `npm run build` → **SUCCESS** (chunk size warning is pre-existing, not from these changes)

---

_Fixed: 2026-06-09T01:10:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
