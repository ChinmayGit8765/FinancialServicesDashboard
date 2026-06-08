---
phase: 04-quant-risk-engine
verified: 2026-06-08T00:00:00Z
status: human_needed
score: 14/14
overrides_applied: 0
human_verification:
  - test: "Open http://localhost:8080, log in as Alice, confirm KPI strip shows real Sharpe Ratio and Ann. Volatility values (no 'Phase 4' placeholders)"
    expected: "Real numeric values rendered in the KPI strip, not placeholder text"
    why_human: "Cannot verify rendered UI text without running the full stack under docker compose"
  - test: "Confirm the Risk Scorecard panel shows Sharpe, Ann. Volatility, Max Drawdown, Beta KPI cards and a side-by-side Historical vs Parametric VaR table with Method/Confidence/Horizon/Amount/% columns and positive amounts"
    expected: "All four KPI cards visible; VaR table with HISTORICAL and PARAMETRIC rows showing positive dollar amounts"
    why_human: "Visual layout and data rendering require a live browser session"
  - test: "Confirm the Correlation Heatmap renders with the blue→white→red scale (-1..+1), tickers on both axes, and per-cell values shown"
    expected: "5×5 heatmap with AAPL/MSFT/NVDA/AMZN/TSLA tickers on both axes; blue-white-red gradient; cell values at 2 decimal places"
    why_human: "ECharts heatmap rendering requires browser canvas; cannot verify color scale or axis labels programmatically"
  - test: "Confirm the Attribution bar chart shows Alpha / Mkt-RF / SMB / HML contributions with green bars for positive and red for negative values, and top-positioned labels"
    expected: "Four bars labeled Alpha (-1.95%), Mkt-RF (~14.6%), SMB (~0.0%), HML (~0.0%); Mkt-RF bar green, Alpha bar red; percent labels at top"
    why_human: "ECharts bar chart rendering requires browser canvas; bar colors are set via itemStyle not CSS class"
  - test: "Confirm the Pairs table shows either cointegrated pairs or the empty-state message 'No cointegrated pairs found in current holdings' (alice's GBM-seeded portfolio is expected to produce 0 pairs)"
    expected: "Empty state message or a valid table of pairs with p-value, hedge β, Z-score, signal badge columns"
    why_human: "Requires live render to confirm empty-state copy renders cleanly and is not a broken component"
  - test: "Switch persona to Bob, then Charlie — confirm all four panels re-scope without a full page reload and show no console errors"
    expected: "Panels reload in place after persona switch; no JavaScript console errors; no stale data from previous persona"
    why_human: "Race-guard behavior and persona-switch UX require interactive testing in a browser DevTools session"
---

# Phase 04: Quant Risk Engine — Verification Report

**Phase Goal:** Users can view a full risk scorecard, correlation heatmap, Fama-French factor attribution, and cointegration pairs scanner — each metric backed by golden-value unit tests proving correctness before the AI layer narrates them.

**Verified:** 2026-06-08
**Status:** human_needed — all automated checks pass; 6 visual/interactive items require human verification under `docker compose up`
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Risk scorecard (Sharpe, vol, drawdown, beta, VaR) backed by golden-value unit tests | VERIFIED | `RiskCalculatorTest` — 10 tests with baked constants: GOLDEN_SHARPE=0.36442669, GOLDEN_ANNUAL_VOL=0.34621361, GOLDEN_MAX_DRAWDOWN=-0.33891522, GOLDEN_BETA=1.94917921, GOLDEN_HIST_VAR_AMOUNT=1464.52. All tests green per 04-02-SUMMARY. |
| 2 | VaR computed by both HISTORICAL and PARAMETRIC methods, positive monetary amounts, method/confidence/horizon labelled | VERIFIED | `RiskCalculator.java` lines 177-190: both `VarResultDto` entries constructed with hardcoded method labels, confidence=0.95, horizonDays=1, amounts computed as `-getPercentile(5.0) * value` and `(1.645 * σ - μ) * value`. Tests `historicalVar_alice_isPositive`, `parametricVar_alice_isPositive`, `var_alice_hasCorrectLabels` all present with real assertions. |
| 3 | Correlation heatmap backed by golden-value unit test (Pearson, symmetric, diagonal=1.0, AAPL-MSFT golden) | VERIFIED | `CorrelationCalculatorTest` — 4 tests: `correlationMatrix_isSymmetric`, `correlationMatrix_diagonalIsOne`, `correlationMatrix_isSquareAndCorrectSize` (5×5), `aapl_msft_correlation_matchesGolden` (GOLDEN_CORR_AAPL_MSFT=1.0). `PearsonsCorrelation` used; diagonal forced to 1.0 at line 154 of CorrelationCalculator.java. |
| 4 | Fama-French 3-factor attribution backed by golden-value tests (params.length=4, R²=0.9999, betaMkt>0, contributions sum) | VERIFIED | `FamaFrenchCalculatorTest` — 4 tests with baked constants GOLDEN_R2=0.99990070, GOLDEN_BETA_MKT=1.94824470. Critical alignment `factorRows.get(i + 1)` present at line 120 of FamaFrenchCalculator.java; no manual intercept column per Hipparchus contract. |
| 5 | Cointegration scanner backed by golden-value tests (MacKinnon p-value ≈ 0.034 at tau=-3.0, ADF on residual spread) | VERIFIED | `CointegrationScannerTest` — 5 tests: `adfPValue_knownStatistic_matches` asserts `mackinnonPValue(-3.0) ≈ 0.034 ±0.01`, `spreadZScore_formulaCorrect`, boundary tests at TAU_MAX/TAU_MIN, `adfStatistic_shortSpread_returnsNaN`. ADF runs on `estimateResiduals()` (line 184 of CointegrationScanner.java), not on raw log prices. |
| 6 | All four analytics endpoints (GET /risk, /correlation, /attribution, /pairs) are principal-scoped and return 401 when unauthenticated | VERIFIED | `AnalyticsController.java`: all 4 `@GetMapping` methods call `resolvePortfolioId(authentication)` before any service call; no `@RequestParam` or `@PathVariable` accepting portfolioId found anywhere in the class. `AnalyticsControllerIntegrationTest#getRisk_unauthenticated_returns401` asserts 401. |
| 7 | Analytics Modulith module boundary passes QuantLensModulithTest (allowedDependencies: marketdata::domain + portfolio::domain only, no portfolio::service) | VERIFIED | `analytics/package-info.java`: `@ApplicationModule(allowedDependencies = {"marketdata::domain", "portfolio::domain"})`. `portfolio::service` absent. Local `DateValueDto` created (04-02-SUMMARY decision) to avoid cross-module violation. QuantLensModulithTest GREEN per 04-02 and 04-03 summaries. |
| 8 | RiskScorecard.vue renders Sharpe/vol/maxDrawdown/beta KPI cards + VaR comparison table with method/confidence/horizon labels | VERIFIED | `RiskScorecard.vue` lines 37-81: four `<KpiCard>` instances (Sharpe, Ann. Volatility, Max Drawdown, Beta); `<table class="var-table">` with columns Method/Confidence/Horizon/Amount/% iterating `risk.var`. |
| 9 | CorrelationHeatmap.vue renders ECharts heatmap with visualMap -1..+1 (blue→white→red) | VERIFIED | `CorrelationHeatmap.vue` lines 48-56: `visualMap: { min: -1, max: 1, color: ['#ef4444','#f8fafc','#3b82f6'] }`. Series type `'heatmap'`. Three-state loading/error/empty/chart present. |
| 10 | AttributionChart.vue renders FF contribution bar chart (Alpha, Mkt-RF, SMB, HML) | VERIFIED | `AttributionChart.vue` lines 21-27: labels `['Alpha','Mkt-RF','SMB','HML']`; values `[dto.alphaAnnualized, dto.contribMktAnnualized, dto.contribSmbAnnualized, dto.contribHmlAnnualized] * 100`. Series type `'bar'`. |
| 11 | PairsTable.vue renders candidate pairs with p-value, hedge ratio, Z-score, signal badge | VERIFIED | `PairsTable.vue`: table columns Pair/p-value/Hedge β/Z-score/Signal; signal badge computed via `signalClass()` mapping LONG_Y_SHORT_X→`signal-buy`, SHORT_Y_LONG_X→`signal-sell`, NEUTRAL→`signal-neutral`. |
| 12 | DashboardView.vue wires all four panels, replacing Phase-4 SlotPlaceholders | VERIFIED | `DashboardView.vue` imports RiskScorecard, CorrelationHeatmap, AttributionChart, PairsTable. grep confirms `SlotPlaceholder label="Risk Scorecard — Phase 4"` and `SlotPlaceholder label="Correlation Heatmap — Phase 4"` are absent; all four real components present in template. |
| 13 | Pinia portfolio store exposes risk/correlation/attribution/pairs AsyncState + fetch actions wired into refreshAll and $reset | VERIFIED | `portfolio.ts`: `fetchRisk` hits `'/api/portfolio/risk'`; all four fetches appear in `refreshAll` Promise.allSettled (9 total calls); all four functions and state objects in return block. Race-guard version parameter passed to all four. |
| 14 | Full backend test suite (70 tests, 0 failures, 2 skipped @Disabled) + frontend (48 tests green, npm run build exit 0) | VERIFIED | 04-03-SUMMARY table: 70 run, 0 failures, 2 skipped (both @Disabled printer tests). 04-04-SUMMARY: frontend 48/48 green, `npm run build` exit 0. Commits 90604fc and 7bd2a95 confirm final state. |

**Score:** 14/14 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/analytics/package-info.java` | Modulith module declaration with allowedDependencies | VERIFIED | Contains `@ApplicationModule(displayName = "Analytics", allowedDependencies = {"marketdata::domain", "portfolio::domain"})` |
| `backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java` | 4 principal-scoped endpoints, resolvePortfolioId | VERIFIED | All 4 `@GetMapping` handlers present; `resolvePortfolioId` copied verbatim; no IDOR vectors |
| `backend/src/main/java/com/quantlens/analytics/api/RiskScorecardDto.java` | Scorecard DTO record | VERIFIED | `record RiskScorecardDto` with exact fields per plan |
| `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java` | Scorecard + both VaR methods, >90 lines | VERIFIED | 351 lines; log returns, √252, getPercentile, 1.645 all present |
| `backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java` | Pearson correlation matrix, >30 lines | VERIFIED | 192 lines; `PearsonsCorrelation`, `getCorrelationMatrix()`, diagonal forced to 1.0 |
| `backend/src/main/java/com/quantlens/analytics/service/FamaFrenchCalculator.java` | FF 3-factor OLS, >60 lines | VERIFIED | 251 lines; `OLSMultipleLinearRegression`, `factorRows.get(i + 1)` alignment, no manual intercept |
| `backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java` | Engle-Granger + ADF + MacKinnon, >90 lines | VERIFIED | 312 lines; `estimateResiduals`, MacKinnon constants, `getSector`, MAX_CANDIDATE_PAIRS=20 cap |
| `backend/src/main/java/com/quantlens/marketdata/domain/FactorReturnRepository.java` | findAllByOrderByFactorDateAsc query | VERIFIED | Method present at line 22 |
| `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java` | 8+ tests with real golden constants | VERIFIED | 10 tests, no 0.0 placeholders, all baked from printer |
| `backend/src/test/java/com/quantlens/analytics/CorrelationCalculatorTest.java` | Symmetric, diagonal, AAPL-MSFT golden | VERIFIED | 4 tests including `correlationMatrix_isSquareAndCorrectSize` beyond plan spec |
| `backend/src/test/java/com/quantlens/analytics/FamaFrenchCalculatorTest.java` | paramsLength, R² golden, betaMkt positive, contributions sum | VERIFIED | 4 tests, all golden constants baked |
| `backend/src/test/java/com/quantlens/analytics/CointegrationScannerTest.java` | MacKinnon p-value ≈ 0.034, spreadZScore formula | VERIFIED | 5 tests (3 MacKinnon, 1 Z-score, 1 NaN guard) |
| `backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java` | 401 unauth + 6 endpoint tests | VERIFIED | 7 tests; extends AbstractPostgresIntegrationTest; GOLDEN_HIST_VAR_AMOUNT=1464.52 (not 0.0) |
| `backend/src/test/java/com/quantlens/analytics/AnalyticsGoldenValuePrinterTest.java` | @Disabled golden-value printer | VERIFIED | `@Disabled` annotation on `printGoldenValues_aliceGrowthPortfolio` method; extends AbstractPostgresIntegrationTest; wired to all 4 services |
| `frontend/src/api/analytics.ts` | 5 TypeScript DTO interfaces | VERIFIED | All 5 interfaces present: VarResultDto, RiskScorecardDto, CorrelationMatrixDto, AttributionDto, PairResultDto; signal typed as literal union |
| `frontend/src/components/RiskScorecard.vue` | Risk KPI cards + dual-VaR table, contains "VaR" | VERIFIED | All 4 KpiCard instances + `<table class="var-table">` iterating `risk.var` |
| `frontend/src/components/CorrelationHeatmap.vue` | ECharts heatmap, contains "heatmap" | VERIFIED | Series type `'heatmap'`; visualMap -1..1 |
| `frontend/src/components/AttributionChart.vue` | FF contribution bar chart, contains "bar" | VERIFIED | Series type `'bar'`; labels Alpha/Mkt-RF/SMB/HML |
| `frontend/src/components/PairsTable.vue` | Cointegration pairs table, contains "signal" | VERIFIED | Signal badge rendered via `signalClass()` |
| `frontend/src/stores/portfolio.ts` | 4 new analytics AsyncState + fetch actions | VERIFIED | fetchRisk, fetchCorrelation, fetchAttribution, fetchPairs all present and wired |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AnalyticsController.java` | `PortfolioRepository.findPortfolioIdByUsername` | `resolvePortfolioId` helper | VERIFIED | Line 146: `portfolioRepository.findPortfolioIdByUsername(username).orElseThrow(...)` |
| `AnalyticsControllerIntegrationTest` | `AbstractPostgresIntegrationTest` | `extends` | VERIFIED | `class AnalyticsControllerIntegrationTest extends AbstractPostgresIntegrationTest` |
| `RiskCalculator` | `OhlcvBarRepository.findAllBySecurityIdsOrdered` | equity-curve replication | VERIFIED | Line 250: `ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds)` |
| `RiskCalculator` | `DescriptiveStatistics.getPercentile` | historical VaR 5th percentile | VERIFIED | Line 173: `stats.getPercentile(5.0)` |
| `RiskCalculator` | `SecurityRepository.findByBenchmarkTrue` | beta vs SPX500 | VERIFIED | Line 96: `securityRepository.findByBenchmarkTrue()` |
| `FamaFrenchCalculator` | `FactorReturnRepository.findAllByOrderByFactorDateAsc` | factor series load + i+1 alignment | VERIFIED | Line 98; alignment at line 120 `factorRows.get(i + 1)` |
| `CointegrationScanner` | `OLSMultipleLinearRegression.estimateResiduals` | ADF on residual spread | VERIFIED | Line 184: `step1.estimateResiduals()` — ADF applied to residuals, not raw prices |
| `CointegrationScanner` | `Security.getSector` | bounded same-sector candidate pairs | VERIFIED | Line 108: `.collect(Collectors.groupingBy(Security::getSector))` |
| `portfolio.ts` fetchRisk | `/api/portfolio/risk` | axios.get | VERIFIED | Line 171: `axios.get<RiskScorecardDto>('/api/portfolio/risk')` |
| `DashboardView.vue` | `RiskScorecard.vue` / `CorrelationHeatmap.vue` | component import replacing SlotPlaceholder | VERIFIED | Imports at lines 14-17; both real components present; Phase-4 SlotPlaceholder labels absent |
| `CorrelationHeatmap.vue` | `store.correlation` | props binding | VERIFIED | DashboardView binds `portfolioStore.correlation.data` to `:correlation` prop |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `RiskScorecard.vue` | `risk` prop (RiskScorecardDto) | `portfolioStore.risk.data` ← `fetchRisk` ← `axios.get('/api/portfolio/risk')` ← `RiskCalculator.computeRiskScorecard` ← Postgres seed | Yes — Hipparchus computation against Testcontainers DB | FLOWING |
| `CorrelationHeatmap.vue` | `correlation` prop (CorrelationMatrixDto) | `portfolioStore.correlation.data` ← `fetchCorrelation` ← `axios.get('/api/portfolio/correlation')` ← `CorrelationCalculator.computeCorrelationMatrix` ← Postgres seed | Yes — PearsonsCorrelation against seeded OHLCV bars | FLOWING |
| `AttributionChart.vue` | `attribution` prop (AttributionDto) | `portfolioStore.attribution.data` ← `fetchAttribution` ← `axios.get('/api/portfolio/attribution')` ← `FamaFrenchCalculator.computeAttribution` ← Postgres seed | Yes — OLS regression against seeded factor returns | FLOWING |
| `PairsTable.vue` | `pairs` prop (PairResultDto[]) | `portfolioStore.pairs.data` ← `fetchPairs` ← `axios.get('/api/portfolio/pairs')` ← `CointegrationScanner.scanPairs` ← Postgres seed | Yes — Engle-Granger scan (correctly returns empty for GBM all-1.0 seed) | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED — requires running Testcontainers (database + Spring Boot context). No standalone runnable entry point available without `docker compose up`. Backend tests serve as the runnable behavioral checks.

---

### Probe Execution

Step 7c: No `scripts/*/tests/probe-*.sh` files declared or found for this phase. SKIPPED.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|---------|
| RISK-01 | 04-01, 04-02, 04-04 | User can view a risk scorecard — Sharpe ratio, annualized volatility, max drawdown, beta vs benchmark, and 95% VaR | SATISFIED | RiskCalculator computes all 5 metrics with golden-value tests; RiskScorecard.vue renders them; endpoint returns 200 for alice |
| RISK-02 | 04-01, 04-02, 04-04 | User can view a pairwise return-correlation heatmap across holdings | SATISFIED | CorrelationCalculator returns symmetric N×N matrix; CorrelationHeatmap.vue renders ECharts heatmap; golden tests pass |
| RISK-03 | 04-01, 04-02, 04-04 | User can view VaR computed by both parametric and historical methods side by side | SATISFIED | Both HISTORICAL and PARAMETRIC VarResultDto entries in RiskScorecardDto.var; side-by-side VaR table in RiskScorecard.vue |
| ATTR-01 | 04-01, 04-03, 04-04 | User can view Fama-French 3-factor attribution of portfolio returns | SATISFIED | FamaFrenchCalculator with correct i+1 alignment; R²=0.9999 golden-tested; AttributionChart.vue bar chart |
| ARB-01 | 04-01, 04-03, 04-04 | User can view a cointegration-based pairs scanner | SATISFIED | CointegrationScanner with Engle-Granger 2-step, ADF on residuals, MacKinnon p-value ≈0.034 golden-tested; PairsTable.vue renders results |

All 5 phase requirements satisfied. REQUIREMENTS.md traceability table correctly marks all 5 as Complete.

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AnalyticsGoldenValuePrinterTest.java` | 127 | `// TODO: call CointegrationScanner.mackinnonPValue(-3.0)...` | INFO | Stale comment from RED scaffold — the method is now public and tested directly in CointegrationScannerTest. No code path affected; printer is @Disabled. Not a BLOCKER. |
| `AnalyticsControllerIntegrationTest.java` | 129 | `// RED until Plan 04-02: GOLDEN_HIST_VAR_AMOUNT is 0.0 placeholder` | INFO | Stale comment — `GOLDEN_HIST_VAR_AMOUNT` is `1464.52` on line 44 (not 0.0). Comment is misleading but harmless; the actual assertion is correct. Not a BLOCKER. |

No TBD, FIXME, or XXX markers found in any analytics module file. No `0.0 // FILL` placeholder constants remain in any test file. No hardcoded empty returns in any calculator service.

---

### Math Correctness Spot-Checks

The following correctness traps were verified directly in the code (not from SUMMARY claims):

**Log returns / 252 annualization:**
- RiskCalculator uses `Math.log(p1 / p0)` (line 225 of `logReturns` helper) and `* Math.sqrt(252.0)` for Sharpe and vol. CORRECT.

**VaR sign:**
- Historical VaR: `histVarPct = -stats.getPercentile(5.0)` (line 173) — negation produces positive loss. CORRECT.
- Parametric VaR: `paramVarPct = z95 * dailySigma - dailyMean` (line 185) — positive for normal equity portfolio. CORRECT.
- Both amounts are `BigDecimal` (as required by contract). CORRECT.

**FF intercept handling:**
- `OLSMultipleLinearRegression.newSampleData(excess, xMatrix)` where xMatrix has 3 columns (not 4) — Hipparchus adds intercept automatically. CORRECT. No manual ones-column.
- Alignment: `factorRows.get(i + 1)` at line 120. CORRECT.

**ADF on residual spread (not raw prices):**
- Step 1: OLS on log prices → `residuals = step1.estimateResiduals()` (line 184).
- Step 2: ADF runs on `residuals` array. CORRECT.
- MacKinnon constants present and match plan spec: 2.1659, 1.4412, 0.038269, 1.7339, 0.93202, -0.12745, -0.010368, TAU_STAR=-1.61, TAU_MIN=-18.83, TAU_MAX=2.74. CORRECT.
- TDistribution explicitly warned against in Javadoc. CORRECT.

**IDOR (principal-only resolution):**
- `AnalyticsController` has zero `@RequestParam` or `@PathVariable` annotations (confirmed by grep returning only Javadoc comment references). CORRECT.
- All 4 endpoints call `resolvePortfolioId(authentication)` before any service method. CORRECT.

---

### Human Verification Required

The following items cannot be verified programmatically and require visual confirmation under `docker compose up`:

#### 1. KPI Strip — Real Values

**Test:** Log in as Alice; observe the top KPI strip.
**Expected:** "Sharpe Ratio" card shows a real numeric value (~0.36); "Ann. Volatility" shows a percentage (~34.6%). No "Phase 4" placeholder text visible.
**Why human:** Rendered DOM text under Vue 3 requires a live browser session.

#### 2. Risk Scorecard Panel

**Test:** Observe the Risk Scorecard panel (left side of row 3).
**Expected:** Four KPI cards: Sharpe Ratio, Ann. Volatility, Max Drawdown (negative percentage), Beta. Below them a VaR table with HISTORICAL and PARAMETRIC rows, both showing positive dollar amounts (~$1,464.52) and percentage, with confidence=95% and horizon=1d.
**Why human:** Layout (4-column grid, VaR table spanning full width) and number formatting require browser rendering.

#### 3. Correlation Heatmap Visual

**Test:** Observe the Correlation Heatmap panel (row 4, full width).
**Expected:** 5×5 heatmap grid with tickers (AAPL, AMZN, MSFT, NVDA, TSLA) on both axes, blue→white→red color scale from -1 to +1, per-cell numeric labels at 2 decimal places. Given GBM seed, all cells should show 1.00.
**Why human:** ECharts canvas rendering, axis rotation, and visualMap gradient cannot be verified without a browser.

#### 4. Attribution Bar Chart

**Test:** Observe the Attribution Chart panel (row 4b, left half).
**Expected:** Four bars labeled Alpha, Mkt-RF, SMB, HML. Alpha bar red (negative ~-1.95%), Mkt-RF bar green (positive ~14.56%), SMB and HML bars near zero. Percentage labels above each bar.
**Why human:** ECharts bar chart itemStyle colors are programmatic (not CSS classes); only browser canvas shows actual colors.

#### 5. Pairs Table or Empty State

**Test:** Observe the Pairs Table panel (row 4b, right half).
**Expected:** "No cointegrated pairs found in current holdings." empty-state message, because alice's GBM seed produces all-1.0 correlation making Engle-Granger residuals degenerate.
**Why human:** Requires confirming the empty-state message renders correctly rather than a broken/blank panel.

#### 6. Persona Switch Re-scoping

**Test:** While on Alice's dashboard, switch to Bob, then Charlie. Observe all four Phase-4 panels.
**Expected:** All four panels reload and show Bob's/Charlie's data without a full page reload; no JavaScript console errors; no data from the previous persona persisting.
**Why human:** Race-guard behavior (refreshVersion) and Vue reactivity under persona switch require interactive DevTools observation.

---

### Gaps Summary

No gaps. All 14 must-haves are VERIFIED at the code level. The 6 items above are expected human verification items that cannot be automated without a live browser session — they are not blockers.

Two stale comments (INFO severity) remain in test files from the RED scaffold stage. Both are harmless: the underlying code and constants they describe are correct. No action required.

---

_Verified: 2026-06-08_
_Verifier: Claude (gsd-verifier)_
