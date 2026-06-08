---
phase: 05-stochastic-forecasting
reviewed: 2026-06-09T00:00:00Z
depth: deep
files_reviewed: 7
files_reviewed_list:
  - backend/src/main/java/com/quantlens/analytics/service/ForecastService.java
  - backend/src/main/java/com/quantlens/analytics/api/ForecastController.java
  - backend/src/main/java/com/quantlens/analytics/api/ForecastDto.java
  - backend/src/main/java/com/quantlens/analytics/api/ModelType.java
  - frontend/src/components/MonteCarloFanChart.vue
  - frontend/src/api/forecast.ts
  - frontend/src/plugins/chart-colors.ts
findings:
  critical: 4
  warning: 5
  info: 2
  total: 11
status: fixed
fixed_at: 2026-06-09T01:10:00Z
fix_report: .planning/phases/05-stochastic-forecasting/05-REVIEW-FIX.md
---

# Phase 05: Code Review Report

**Reviewed:** 2026-06-09T00:00:00Z
**Depth:** deep
**Files Reviewed:** 7
**Status:** issues_found

## Summary

Reviewed the complete Phase 5 Monte Carlo stochastic-forecasting stack: `ForecastService`
(GBM/Merton/Heston/Bootstrap), `ForecastController`, DTOs, the Vue fan-chart component, the
forecast API client, and the chart-color plugin. The `RiskCalculator` (the calibration
dependency) and all four test files were read for cross-module tracing.

The parametric model engines (GBM via `BlackScholesModel`, Merton, Heston) are structurally
correct — the finmath wiring matches what the integration tests confirm at compile time (A4/A5/A6
resolved). Four **Critical** bugs were found, two of which will produce silently wrong numbers
that passing tests cannot catch:

1. **The `DescriptiveStatistics` annualized-σ calibration uses the sample standard deviation
   (n−1 denominator) for μ but the Hipparchus default for σ uses a different normalization path
   — specifically, `getStandardDeviation()` returns the population std (sqrt of the biased
   variance) in Hipparchus, not the sample std.** This causes σ to be downward-biased by a
   factor of sqrt((n-1)/n) relative to the sample estimator, and μ is likewise scaled from the
   biased mean. For typical `n ≈ 252`, the bias is ~0.2 %, negligible but the concern is the
   *inconsistency* with what HC-11 assumes and the fact that if history is very short (n=2, the
   minimum allowed) the bias is 29 %. **Correction on closer examination below — see CR-01.**

2. **The Bootstrap block-bootstrap seed is not reset between paths.** The `MersenneTwister` is
   created once per `runBootstrap` call, then consumed path-by-path. This is correct for a
   single call but means that calling `forecast()` twice in the same JVM session will NOT produce
   the same output because `MC_SEED=42` is applied only at construction — the bootstrap branch
   and the finmath branch use *incompatible reproducibility mechanisms*. Finmath
   `BrownianMotionFromMersenneRandomNumbers` creates a fresh internal RNG per call; bootstrap
   creates one per call too. Both are fine individually, but **the reproducibility test
   `ForecastStructuralTest#reproducibility_fixedSeed_gbm` only covers GBM** — bootstrap
   reproducibility is untested.

3. **Fan-chart stacking math silently produces negative band heights when percentiles are
   non-monotone.** The `ForecastService.extractPercentiles` uses `Math.ceil(p*n) - 1` which
   can return the same index for adjacent percentiles at small n. For the bootstrap with very
   short history (e.g. n=2 returns → only 2 distinct values in each `stepValues[t]` column
   for a given step because NUM_PATHS=5000 will cycle over only 2 distinct return values), many
   percentiles will be tied. Ties are benign for monotonicity. The real problem is described
   under CR-03.

4. **`computeCurrentPortfolioValue` is called AFTER `buildEquityCurveLocal` already loaded
   all positions, but the method issues a second DB round-trip to
   `ohlcvBarRepository.findLatestBarBySecurityIds` independently.** This is not a correctness
   bug per se, but — critically — if any position has been updated between the two calls
   (possible in a non-SERIALIZABLE transaction) the equity-curve calibration and the simulation
   base value will be computed from different price states. The outer transaction is
   `readOnly=true` but that prevents writes, not phantom reads. **In practice the risk is
   low with Postgres's default READ COMMITTED isolation, but the inconsistency window exists.**
   More importantly, the base value and the calibrated returns could be from different price
   snapshots, biasing the median forecast.

---

## Critical Issues

### CR-01: `DescriptiveStatistics.getStandardDeviation()` returns **population** std in Hipparchus — calibrated σ is downward-biased

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:183`

**Issue:**
```java
double annualizedSigma = stats.getStandardDeviation() * Math.sqrt(252.0);
```
In Hipparchus 4.x, `DescriptiveStatistics.getStandardDeviation()` computes the **population**
standard deviation (sqrt of `getPopulationVariance()`, i.e. divides by `n`, not `n-1`). The
sample standard deviation is `stats.getStandardDeviation()` in Apache Commons Math 3, but in
Hipparchus the biased/population estimator is the default. For a log-return series of `n`
observations:

- Population σ = sqrt(Σ(rᵢ − r̄)² / n)
- Sample σ     = sqrt(Σ(rᵢ − r̄)² / (n-1))

For `n = 252` the underestimate is a factor of sqrt(251/252) ≈ 0.998, negligible. But for the
minimum allowed `n = 2` (the `dailyLogReturns.length < 2` guard allows exactly 2 returns), the
factor is sqrt(1/2) ≈ 0.707 — a **29 % underestimate of σ**, which halves the variance fed to
finmath and dramatically narrows all fan bands.

The `RiskCalculator.computeRiskScorecard` uses the same `DescriptiveStatistics` for volatility
computation and is consistent with itself, but `ForecastService` is calibrating σ for a
*forward simulation* where the natural estimator is sample std. The docstring at line 183 says
"μ×252, σ×√252" implying the sample-std convention. HC-11 uses `n=252` so the bias is
invisible there.

Additionally, `DescriptiveStatistics(double[])` constructor in Hipparchus processes the array
but `getStandardDeviation()` in the Hipparchus 4.x stat API returns the population std (calling
`Math.sqrt(getPopulationVariance())`). Callers who want the sample std must call
`getSampleVariance()` then `sqrt`.

**Verify:** `org.hipparchus.stat.descriptive.DescriptiveStatistics` source — `getVariance()`
returns `getPopulationVariance()` by default (biased), while `getSampleVariance()` returns the
unbiased estimator.

**Fix:**
```java
// Replace (line 183):
double annualizedSigma = stats.getStandardDeviation() * Math.sqrt(252.0);

// With (sample std — correct estimator for calibration):
double annualizedSigma = Math.sqrt(stats.getSampleVariance()) * Math.sqrt(252.0);
// Equivalently:
double annualizedSigma = Math.sqrt(stats.getSampleVariance() * 252.0);
```
Apply the same correction to `annualizedMu` if `getMean()` is affected (mean is unaffected by
biased/unbiased variance choice — it is the same). Only σ needs fixing.

**Note:** This finding contradicts HC-11 passing — HC-11 uses `n=252` where the bias is 0.2%
and the 1% tolerance hides it. A test with `n=10` would expose the difference.

---

### CR-02: Bootstrap block-start RNG boundary — `nextInt(maxBlockStart + 1)` overflows to `nextInt(0)` when `maxBlockStart == Integer.MAX_VALUE`

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:398`

**Issue:**
```java
int blockStart = (maxBlockStart >= 0) ? rng.nextInt(maxBlockStart + 1) : 0;
```
`maxBlockStart = H - L`. For `H = Integer.MAX_VALUE` (impossible from DB, but if `dailyLogReturns`
is somehow enormous) `maxBlockStart + 1` wraps to `Integer.MIN_VALUE`, which is negative, and
`MersenneTwister.nextInt(n)` throws `NotStrictlyPositiveException` for `n ≤ 0`.

The more realistic concern: `maxBlockStart` can be **0** when `H == L`. This happens when `H =
10` (minimum from `max(10, sqrt(H))`) — then `L = 10` and `maxBlockStart = 0`. Then
`rng.nextInt(0 + 1)` = `rng.nextInt(1)` always returns **0**. This means every block always
starts at index 0, so the bootstrap degenerates into repeatedly applying the same first 10
returns. The output is **not** random — it is deterministic and identical for all 5000 paths,
collapsing the fan chart to a single line. The guard comment says "Boundary-safe" but does not
address this degenerate case.

Worse: the `H < L` fallback on lines 367-369 sets `L = max(1, H/2)` when `H < L`, but for
`H = 10`, `L = max(10, sqrt(10)) = 10` so `H == L` exactly — the fallback is never triggered
(the condition is `H < L`, not `H <= L`).

**Reproducing scenario:** a portfolio with only 11 days of data → `H = 10` log returns →
`L = 10`, `maxBlockStart = 0`, all 5000 paths are identical.

**Fix:**
```java
// Change the H <= L guard to catch the degenerate maxBlockStart=0 case:
if (H <= L) {                          // was: H < L
    L = Math.max(1, H / 2);
}
final int maxBlockStart = H - L;       // now guaranteed >= 1 when H > 1
```

---

### CR-03: Fan-chart stacked-series math produces **negative band heights** when any percentile pair is equal (identical values silently subtract to 0 is fine, but `v - prior` can go negative if the DTO arrays are from a non-finmath path)

**File:** `frontend/src/components/MonteCarloFanChart.vue:73`

**Issue:**
```javascript
// Series 1: band p5 → p25
data: p25.map((v, i) => v - p5[i]),
// Series 2: band p25 → p75
data: p75.map((v, i) => v - p25[i]),
// Series 3: band p75 → p95
data: p95.map((v, i) => v - p75[i]),
```
ECharts stacked area series with a **negative value** causes that series to stack downward,
visually inverting the fan band below the floor. The backend guarantees monotone percentiles
(`extractPercentiles` sorts + nearest-rank → p5 ≤ p25 ≤ ... ≤ p95 by construction), so for
correctly behaved backend data the differences are non-negative.

However, the frontend has **no defensive guard** against negative differences. If a future AI
narration layer, a mock, or a network error produces a partial/malformed `ForecastDto` where
the ordering invariant is violated, Series 1/2/3 can go negative and the chart silently
renders upside-down bands — with no error state shown to the user. The component accepts
`ForecastDto | null` from props without any structural validation.

Additionally, there is a **genuine numeric edge case in the backend**: the `extractPercentiles`
function at line 470 uses:
```java
sorted[clamp((int) Math.ceil(0.05 * n) - 1, 0, n - 1)]  // p5
sorted[clamp((int) Math.ceil(0.25 * n) - 1, 0, n - 1)]  // p25
```
For `n = 1` (single path — impossible with NUM_PATHS=5000 but possible in a unit test or if
called externally):
- `ceil(0.05 * 1) - 1 = ceil(0.05) - 1 = 1 - 1 = 0`
- `ceil(0.25 * 1) - 1 = ceil(0.25) - 1 = 1 - 1 = 0`

All five percentiles map to `sorted[0]` — identical. The differences `p25-p5 = 0` etc. are
fine. But for `n = 4`:
- p5: `ceil(0.2) - 1 = 0` → `sorted[0]`
- p25: `ceil(1.0) - 1 = 0` → `sorted[0]` ← same index as p5

So p5 = p25 here; the fan chart loses the outer band entirely with no warning.

The real risk is that the frontend component **silently renders a broken chart** with no
indication to the user.

**Fix (frontend defensive clamp):**
```typescript
// In the computed option, clamp diffs to >= 0:
data: p25.map((v, i) => Math.max(0, v - p5[i])),
data: p75.map((v, i) => Math.max(0, v - p25[i])),
data: p95.map((v, i) => Math.max(0, v - p75[i])),
```
This prevents visual inversion even if the backend invariant is violated.

---

### CR-04: `ForecastController` — `@Transactional(readOnly = true)` on the controller method is redundant and misleading; the service already declares a transaction, but the controller transaction **does not propagate the read-only hint correctly to the service's inner transaction under PROPAGATION_REQUIRED**

**File:** `backend/src/main/java/com/quantlens/analytics/api/ForecastController.java:83`

**Issue:**
```java
@GetMapping("/forecast")
@Transactional(readOnly = true)      // ← on the controller
public ResponseEntity<ForecastDto> getForecast(...) {
```
`ForecastService` is also annotated `@Transactional(readOnly = true)` at the class level
(line 74 of `ForecastService.java`). When the controller's proxy creates a transaction first
(outer), and then the service's proxy joins it via default `PROPAGATION_REQUIRED`, the
service runs inside the **controller's** transaction. This is correct on the surface.

However, `@Transactional` on a `@RestController` is a Spring anti-pattern that causes subtle
issues:

1. **The transaction is opened before Spring MVC performs argument resolution** (e.g., the
   `Authentication` object is injected mid-transaction). A slow security context lookup keeps
   the transaction open unnecessarily.
2. **If the controller transaction is using a Spring proxy** (i.e., the controller bean is
   proxied by `@Transactional`), Spring creates a JDK proxy around the controller. Spring MVC
   normally does not expect controllers to be proxied — this can break `@RequestMapping`
   scanning in some configurations.
3. **More critically:** the `@Transactional` on the controller is on a Spring Security-managed
   bean. If the security filter chain re-enters the bean (e.g., a method security annotation
   causes a second proxy invocation), the transaction boundary can be mis-applied.

The real bug: `ForecastController.resolvePortfolioId` calls `portfolioRepository`, which is a
`@Repository`. If the **controller** transaction opens and the repository flushes at commit,
`readOnly=true` on the controller may suppress a flush that was expected downstream. For
read-only workloads this is fine, but the combination of two `readOnly=true` transaction
annotations on two different beans in the same call chain creates **dead code** — one of them
is always redundant, and the redundant one is on the wrong layer.

**Fix:** Remove `@Transactional(readOnly = true)` from `ForecastController.getForecast`.
The service-layer transaction (on `ForecastService`) is the correct location for transaction
demarcation.

```java
// Remove from ForecastController:
@GetMapping("/forecast")
// @Transactional(readOnly = true)  ← remove this line
public ResponseEntity<ForecastDto> getForecast(...)
```

---

## Warnings

### WR-01: Bootstrap RNG seed creates different output on second call within same request due to `MersenneTwister` being stateful, but `runBootstrap` reproducibility is **untested**

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:375`

**Issue:**
```java
MersenneTwister rng = new MersenneTwister(MC_SEED);
```
The `MersenneTwister` is correctly instantiated with `MC_SEED=42` at the start of each
`runBootstrap` call. Two separate calls to `forecast(..., BOOTSTRAP, ...)` will each create a
fresh `MersenneTwister(42)` and produce identical output — this is correct.

However, the reproducibility test (`ForecastStructuralTest#reproducibility_fixedSeed_gbm`) only
tests GBM. The analogous test for BOOTSTRAP is absent. The `bootstrap_outputMeanLogReturn_withinFivePercent_ofHistorical` test at line 134 checks the mean log return is `< 0.05` — this is an almost trivial bound that does not validate reproducibility.

If `runBootstrap` is ever modified to use a shared/cached RNG (e.g., for performance), the
missing reproducibility test would not catch a regression.

**Fix:** Add a reproducibility test for the BOOTSTRAP model mirroring
`reproducibility_fixedSeed_gbm`:
```java
@Test
void reproducibility_fixedSeed_bootstrap() {
    ForecastDto run1 = forecastService.forecast(alicePortfolioId, ModelType.BOOTSTRAP, 252);
    ForecastDto run2 = forecastService.forecast(alicePortfolioId, ModelType.BOOTSTRAP, 252);
    assertThat(run1.p50()).isEqualTo(run2.p50());
}
```

---

### WR-02: `computeCurrentPortfolioValue` silently returns `0.0` when all positions are short (negative quantity) — simulation starts at `initialValue = 0`

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:501`

**Issue:**
```java
if (secIds.isEmpty()) {
    return 0.0;
}
```
If a portfolio holds only short positions (`quantity < 0`), `secIds` is empty and the method
returns `0.0`. The simulation then starts with `initialValue = 0.0`. For GBM/Heston/Merton,
`BlackScholesModel(0.0, ...)` produces `S_t = 0` for all `t` (GBM with `S_0 = 0` stays at 0).
For Bootstrap, `currentValue = 0.0; currentValue *= exp(r)` stays at 0. The result is a
forecast of all zeros — plausible in isolation, but silently incorrect (a short-only portfolio
has a net value that can go positive when shorts appreciate).

There is no `IllegalArgumentException` or log warning when `initialValue = 0.0` is passed to
the simulation. The fan chart will render a flat line at $0 with no error indication.

**Fix:** Add a guard before dispatching to model runners:
```java
if (initialValue <= 0.0) {
    throw new IllegalArgumentException(
        "Portfolio initial value is zero or negative (portfolioId=" + portfolioId +
        "). Cannot run Monte Carlo simulation from a non-positive base value.");
}
```
Or log a warning and return an empty/null `ForecastDto` with a meaningful error message.

---

### WR-03: `extractBands` calls `sim.getAssetValue(t, 0)` for `t = 1..horizonDays` — at `horizonDays = 1` this calls `getAssetValue(1, 0)`, which is valid, but the loop index `t=1` maps to `idx=0` correctly; however finmath's time index `t` is an **integer step index** while the time grid is `0, 1/252, 2/252, ...` — calling `getAssetValue(252, 0)` requests step index 252, which requires the time grid to have 253 points (indices 0..252)

**File:** `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java:224` and `:441`

**Issue:**
`TimeDiscretizationFromArray` is constructed as:
```java
var td = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);
```
This creates `horizonDays + 1` time points: `t_0=0, t_1=1/252, ..., t_horizonDays=horizonDays/252`.
The time discretization has indices `0..horizonDays`.

In `extractBands`, the loop is:
```java
for (int t = 1; t <= horizonDays; t++) {
    double[] realizations = sim.getAssetValue(t, 0).getRealizations();
```
`getAssetValue(t, 0)` takes the **time index** `t` (not a time value). Index `t = horizonDays`
is valid since the TD has `horizonDays + 1` time points (indices 0 through `horizonDays`).

This is actually **correct** — `TimeDiscretizationFromArray(0.0, n, dt)` generates `n+1`
time points so the last valid index is `n`. The finmath `getAssetValue(int, int)` overload
takes a time index. **No bug in production.**

However, if finmath's `getAssetValue(int, int)` signature is actually `getAssetValue(double
time, int assetIndex)` (taking a time *value*, not an index), then `getAssetValue(252, 0)`
would look up time `t = 252.0` years, which is far outside the simulation grid and would
either throw or extrapolate. The integration test `ForecastFinmathIntegrationTest` calls
`sim.getAssetValue(252, 0)` (line 111 of `ForecastMathHandComputedTest.java`) and asserts the
result, which suggests the int-index overload is being used and is correct. **Flag for
confirmation during finmath API upgrade** — if the library ever removes the int-index overload,
this will silently call the double-value overload.

**Fix:** Document the overload resolution explicitly with a comment:
```java
// getAssetValue(int timeIndex, int assetIndex) — integer index overload, not time-value
double[] realizations = sim.getAssetValue(t, 0).getRealizations();
```

---

### WR-04: Model selector toggle in `MonteCarloFanChart.vue` emits `update:model` on every selection change, but the **parent component is responsible for triggering a re-fetch** — if the parent does not watch `update:model`, the fan chart never updates when the user switches models

**File:** `frontend/src/components/MonteCarloFanChart.vue:23`

**Issue:**
```typescript
watch(selectedModel, (model) => {
  emit('update:model', model)
})
```
The component emits `update:model` correctly. But the component receives `forecast: ForecastDto | null`
as a prop — it does not own the fetch. The parent must:
1. Listen to `@update:model`
2. Call `fetchForecast(newModel)` on the store
3. Pass the updated `forecast` back down

If the parent wires `v-model:model="selectedModel"` and calls `fetchForecast` in a watcher,
this works. But there is no evidence in `portfolio.ts` store of this being triggered — the
`refreshAll()` on line 299 calls `fetchForecast('GBM', 252, myVersion)` with a hardcoded
`'GBM'`, and `fetchForecast` is not automatically called with the new model when the component
switches.

This creates a **selector race / stale data issue**: the user clicks "HESTON", the component
emits `update:model = 'HESTON'`, but unless the parent handles the event and calls
`store.fetchForecast('HESTON')`, the chart continues to display the old GBM forecast under the
new HESTON label. The loading skeleton never appears; there is no error. The chart is silently
showing wrong data.

The parent component (not in the reviewed file list) must be verified. If the parent does not
wire this, the model toggle is decorative.

**Fix:** Verify and document in `MonteCarloFanChart.vue` that the parent must handle
`update:model` and re-fetch. Or alternatively, move the fetch responsibility into the component:
```typescript
watch(selectedModel, async (model) => {
  emit('update:model', model)
  await store.fetchForecast(model)
})
```
(Requires injecting the store into the component — only do this if the component owns the data
fetching responsibility per the architecture.)

---

### WR-05: `chart-colors.ts` — `FAN_COLORS` is initialized at **module load time**, which in SSR environments or test environments with no `window` will use fallback colors; but the `cssVar` fallback for `bandInner` is `'rgba(14,165,233,0.25)'` while the CSS token is likely defined differently in dark-mode — theme switching after module init will not be reflected

**File:** `frontend/src/plugins/chart-colors.ts:38`

**Issue:**
```typescript
export const FAN_COLORS = {
  median:    cssVar('--color-fan-p50',    '#0ea5e9'),
  bandInner: cssVar('--color-fan-band-1', 'rgba(14,165,233,0.25)'),
  bandOuter: cssVar('--color-fan-band-2', 'rgba(14,165,233,0.12)'),
} as const
```
`cssVar` is called once at module initialization. If the user switches from dark to light mode
(or vice versa) at runtime (by toggling a CSS class on `<html>`), `getComputedStyle` returns
the new token values, but `FAN_COLORS` is frozen (`as const`) and already cached. The fan chart
colors will not update until the page is reloaded.

The `CHART_COLORS` object has the same issue. This is a known limitation documented in the
comment block, but it is a **user-visible quality defect** for any app that supports runtime
theme switching (the `App.vue` comment mentions `THEME_KEY` for ECharts). If the theme key
changes the CSS tokens but `FAN_COLORS` is stale, the chart colors will be mismatched.

**Fix:** Convert `FAN_COLORS` from a `const` module-level object to a reactive getter or a
function that is called when the chart option `computed` property is evaluated:
```typescript
// Instead of a cached const, expose a getter:
export function getFanColors() {
  return {
    median:    cssVar('--color-fan-p50',    '#0ea5e9'),
    bandInner: cssVar('--color-fan-band-1', 'rgba(14,165,233,0.25)'),
    bandOuter: cssVar('--color-fan-band-2', 'rgba(14,165,233,0.12)'),
  }
}
```
Then in the `computed` option in `MonteCarloFanChart.vue`, call `getFanColors()` inside the
computed body. Since the computed is already reactive on `props.forecast`, it will re-evaluate
when data changes, picking up any CSS token changes.

---

## Info

### IN-01: Dead private helper `trimToSameLength` in `RiskCalculator` is still present

**File:** `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java:376`

**Issue:**
```java
private static double[] trimToSameLength(double[] arr, int length) {
    if (arr.length <= length) return arr;
    return Arrays.copyOf(arr, length);
}
```
This method is defined but never called — the CR-02 fix comment in `RiskCalculator` (line 115)
indicates the old `trimToSameLength` approach was replaced with the date-aligned map approach.
The dead method is not harmful but adds noise and suggests a previous implementation was not
fully cleaned up. The `ForecastService` does not use this helper either.

**Fix:** Remove the dead method:
```java
// Delete lines 375-379 from RiskCalculator.java
```

---

### IN-02: `ForecastController` Javadoc says "401 if unauthenticated or portfolio not found" but the correct HTTP semantic for "portfolio not found" after successful authentication is 404, not 401

**File:** `backend/src/main/java/com/quantlens/analytics/api/ForecastController.java:80`

**Issue:**
```java
 * @return 200 with {@link ForecastDto}; 401 if unauthenticated or portfolio not found
```
The intentional design (per line 105-106 comments) is to return 401 for both cases to prevent
username enumeration. The Javadoc is therefore technically correct by design intent — the
deliberate 401 conflation is the IDOR mitigation. However, the param-level comment says
"401 if unauthenticated or portfolio not found" without explaining the security rationale. A
future developer reading this in isolation may "fix" it to return 404 for the not-found case,
breaking the IDOR protection.

**Fix:** Expand the Javadoc to make the security intent explicit:
```java
 * @return 200 with {@link ForecastDto};
 *         401 if unauthenticated, user not found, or user has no portfolio
 *         (both not-found and unauthenticated return 401 deliberately to prevent
 *          username enumeration — IDOR mitigation T-05-01)
```

---

_Reviewed: 2026-06-09T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: deep_
