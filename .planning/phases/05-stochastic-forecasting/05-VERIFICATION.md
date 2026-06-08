---
phase: 05-stochastic-forecasting
verified: 2026-06-09T00:00:00Z
status: human_needed
score: 7/7
overrides_applied: 0
human_verification:
  - test: "Fan chart renders widening percentile bands"
    expected: "Monte Carlo fan chart shows blue widening p5/p25/p50/p75/p95 bands over ~252 days with a visible dark median line; bands widen from left to right"
    why_human: "ECharts canvas rendering cannot be verified by grep or static analysis; requires browser viewport"
  - test: "All four model selector buttons trigger re-fetch and distinct band shapes"
    expected: "Clicking GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP each triggers a network request and the chart updates with visually distinct band shapes; JUMP_DIFFUSION/HESTON show fatter lower tails than GBM"
    why_human: "Reactive re-fetch and visual band-shape differences require live browser interaction"
  - test: "Heston illustrative-parameters disclaimer is visible when HESTON is selected"
    expected: "A small italic disclaimer 'Heston parameters are illustrative, not calibrated to live option prices' appears below the model selector when HESTON is active"
    why_human: "Conditional v-if DOM visibility with style requires visual inspection; static analysis confirms the condition is coded correctly but not that it renders visibly"
  - test: "docs/MODELS.md reads clearly for a non-specialist"
    expected: "The four model sections (GBM, Merton, Heston, Block Bootstrap) explain rationale, assumptions, parameters, and limitations in plain English without unexplained jargon; the fan-chart reading guide and 'potential futures, not predictions' framing are present and clear"
    why_human: "Prose clarity and non-specialist readability are subjective and require human judgment"
---

# Phase 5: Stochastic Forecasting — Verification Report

**Phase Goal:** Users can view a Monte Carlo fan chart of projected portfolio value across four switchable models (GBM, Merton jump-diffusion, Heston, block bootstrap) with percentile bands, and read documented rationale for each model.
**Verified:** 2026-06-09T00:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All four models simulate and return monotone p5/p25/p50/p75/p95 percentile bands | VERIFIED | `ForecastService.java` implements `runGbm`, `runMerton`, `runHeston`, `runBootstrap`; `extractPercentiles` uses clone+sort+nearest-rank which is monotone by construction; `ForecastStructuralTest.percentileBands_areMonotonicallyOrdered_allModels` asserts this at every step for all 4 models |
| 2 | GBM is Ito-correct (HC-11 guard — median < mean, mean ≈ 110.517, median ≈ 108.329) | VERIFIED | `ForecastMathHandComputedTest.gbm_analyticMean_itoCorrect_hc11` wires finmath GBM chain directly and asserts analytic mean within 1%, median within 1%, median < mean; constants 110.517 and 108.329 present in the test; finmath `BlackScholesModel` applies Ito correction internally (no manual drift term added — verified by code comment and plan compliance) |
| 3 | Seed=42 reproducibility: two GBM runs produce byte-identical p50 arrays | VERIFIED | `ForecastStructuralTest.reproducibility_fixedSeed_gbm` asserts `run1.p50()` equals `run2.p50()`; `MC_SEED=42` is a static final constant in `ForecastService.java` used uniformly across all runners |
| 4 | GET /api/portfolio/forecast is principal-scoped (IDOR), enforces ModelType enum (400 on unknown), and clamps horizon [1,504] | VERIFIED | `ForecastController.java` line 89 clamps via `Math.max(1, Math.min(504, horizon))`; `ModelType` typed `@RequestParam` gives 400 automatically; `resolvePortfolioId` uses `Authentication` only — no `@RequestParam` portfolioId; `ForecastControllerIntegrationTest` tests all three: 401 unauthenticated, 400 unknown model, 200 for all 4 models, horizon clamped at 1 and 504 |
| 5 | MonteCarloFanChart.vue renders 5 ECharts series with band-difference stacking and a median line from ForecastDto props | VERIFIED | `MonteCarloFanChart.vue` series block: Series 0 base at p5 (transparent, stack:'fan'); Series 1 diff p25-p5 (bandOuter, stack:'fan'); Series 2 diff p75-p25 (bandInner, stack:'fan'); Series 3 diff p95-p75 (bandOuter, stack:'fan'); Series 4 p50 absolute (no stack, FAN_COLORS.median). FAN_COLORS uses resolved CSS tokens (not var() strings — Pitfall 4 mitigated). Test `MonteCarloFanChart.test.ts` confirms findComponent({name:'VChart'}) exists when forecast data present |
| 6 | User can switch between all four models and the chart re-fetches | VERIFIED | `MonteCarloFanChart.vue`: `selectedModel` ref, `watch(selectedModel, emit('update:model'))`; 4 toggle buttons rendered via `MODEL_LABELS = ['GBM', 'JUMP_DIFFUSION', 'HESTON', 'BOOTSTRAP']`; `DashboardView.vue` wires `@update:model="(m) => portfolioStore.fetchForecast(m)"`; `portfolioStore.fetchForecast` calls `axios.get('/api/portfolio/forecast', { params: { model, horizon } })` |
| 7 | docs/MODELS.md exists with sections for all 4 models including rationale, assumptions, illustrative-param disclaimers, and a fan-chart reading guide; README links to it | VERIFIED | `docs/MODELS.md` is 168 lines covering GBM, Merton Jump-Diffusion, Heston Stochastic Volatility, and Historical Block Bootstrap; each section has What/Why/Assumptions/Parameters/Limitations; Merton jump params and all Heston params explicitly labeled "illustrative"; fan-chart reading guide in intro; "potential futures, not predictions" framing present; appendix covers Ito correction, Feller condition, block-length rationale, seed=42 determinism. `README.md` contains `[Model documentation](docs/MODELS.md)` — confirmed by grep |

**Score:** 7/7 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/pom.xml` | finmath-lib 6.1.7 dependency | VERIFIED | Lines 119-122: `net.finmath:finmath-lib:6.1.7` present, comment notes commons-math3 harmless coexistence, no exclusions |
| `backend/src/main/java/com/quantlens/analytics/api/ModelType.java` | GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP enum | VERIFIED | 43-line file; enum ModelType with exactly 4 constants; javadoc explains typed @RequestParam auto-validation (T-05-02) |
| `backend/src/main/java/com/quantlens/analytics/api/ForecastDto.java` | Record with model+horizonDays+p5/p25/p50/p75/p95 | VERIFIED | Record ForecastDto(ModelType, int, double[], double[], double[], double[], double[]) with javadoc @param per field |
| `backend/src/main/java/com/quantlens/analytics/api/ForecastController.java` | GET /forecast, IDOR+DoS+enum mitigations | VERIFIED | resolvePortfolioId uses Authentication only; horizon clamped; ModelType typed param; 120-line file, no stubs |
| `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java` | 4-model Monte Carlo engine, extractPercentiles, Feller guard | VERIFIED | 521-line file; runGbm/runMerton/runHeston/runBootstrap all implemented; extractPercentiles (nearest-rank); Feller guard in constructor; no UnsupportedOperationException stubs remaining; OhlcvBarRepository injection present |
| `backend/src/test/java/com/quantlens/analytics/ForecastMathHandComputedTest.java` | HC-11 GBM analytic mean/median anchor (no Spring) | VERIFIED | Single test; finmath GBM chain exercised directly; 110.517 and 108.329 constants present; median < mean guard; 1% tolerance (documented deviation from plan's original 0.5%) |
| `backend/src/test/java/com/quantlens/analytics/ForecastStructuralTest.java` | Ordering, widening, reproducibility, bootstrap moment, 4-model distinct | VERIFIED | 5 tests: monotone ordering all models; GBM band widens; seed=42 reproducibility; bootstrap mean log-return < 0.05; 4 models produce ≥3 distinct p95 values |
| `backend/src/test/java/com/quantlens/analytics/ForecastFinmathIntegrationTest.java` | GBM/Heston/Merton finmath instantiation smoke | VERIFIED | 3 tests confirming finmath API: GBM 5000 paths; Heston Feller + FULL_TRUNCATION + 2-factor BM; Merton 10-param constructor |
| `backend/src/test/java/com/quantlens/analytics/ForecastControllerIntegrationTest.java` | 401/400/200 endpoint assertions | VERIFIED | 7 tests: unauthenticated 401; unknown model 400; GBM/JUMP_DIFFUSION/HESTON/BOOTSTRAP each 200 with correct JSON shape; horizon clamped at 1 and 504 |
| `frontend/src/api/forecast.ts` | ForecastDto + ModelType TypeScript types | VERIFIED | DTO-only module; `ModelType = 'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP'`; ForecastDto interface with all 7 fields matching Java record |
| `frontend/src/plugins/chart-colors.ts` | FAN_COLORS with resolved CSS tokens | VERIFIED | FAN_COLORS exported with median/bandInner/bandOuter resolved via cssVar() helper at module init; fallbacks provided for SSR/test env |
| `frontend/src/stores/portfolio.ts` | forecast asyncState + fetchForecast + refreshAll + $reset | VERIFIED | fetchForecast at line 251 with race-guard, axios.get('/api/portfolio/forecast'), 401 handling; included in refreshAll Promise.allSettled; cleared in $reset; forecast and fetchForecast in return block |
| `frontend/src/components/MonteCarloFanChart.vue` | Fan chart + model selector + loading/error/empty + Heston disclaimer | VERIFIED | 319-line file; 5 series with band-difference stacking; FAN_COLORS throughout (no hardcoded hex, no var() strings); 4-button model selector; Heston disclaimer with v-if; loading skeleton; error state with retry; empty state; no :theme prop |
| `frontend/src/views/DashboardView.vue` | MonteCarloFanChart in place of Phase-3 SlotPlaceholder | VERIFIED | Lines 205-213: MonteCarloFanChart bound to portfolioStore.forecast.data/.loading/.error; @retry="retryForecast"; @update:model re-fetches. No "Monte Carlo Forecast — Phase 5" SlotPlaceholder found. retryForecast handler at line 96 |
| `frontend/src/__tests__/components/MonteCarloFanChart.test.ts` | 8 tests: states + 5-series render + model selector | VERIFIED | 8 tests (3 loading/error/empty states + renders VChart + 4 model buttons + emit on click + active class + active switch) |
| `docs/MODELS.md` | 4 models with rationale/assumptions/params/limitations + SIM-03 | VERIFIED | 168-line file with all 4 model sections; illustrative param disclaimers; fan-chart reading guide; appendix with Ito/Feller/block-length/seed notes |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DashboardView.vue` | `portfolioStore.fetchForecast` | `@update:model="(m) => portfolioStore.fetchForecast(m)"` | VERIFIED | Lines 211 and 96 of DashboardView.vue confirm wiring; re-fetch triggered on model switch |
| `MonteCarloFanChart.vue` | `/api/portfolio/forecast` | `portfolioStore.forecast` asyncState bound to props | VERIFIED | props.forecast receives portfolioStore.forecast.data; store calls axios.get('/api/portfolio/forecast'); data flows from API to chart option computed |
| `MonteCarloFanChart.vue` series areaStyle | `FAN_COLORS.bandOuter / bandInner / median` | Resolved via getComputedStyle at module init in chart-colors.ts | VERIFIED | FAN_COLORS imported in MonteCarloFanChart.vue; series 1,3 use bandOuter; series 2 uses bandInner; series 4 (median) uses FAN_COLORS.median — confirmed in series block lines 77, 95, 103; no var(--...) strings in series styling |
| `ForecastService.forecast` | `RiskCalculator.buildEquityCurveLocal + logReturns` | Calibration of annualized μ, σ from portfolio daily log returns | VERIFIED | ForecastService.java lines 170-171: `riskCalculator.buildEquityCurveLocal(positions)` and `RiskCalculator.logReturns(curve)` called in forecast() |
| `ForecastService GBM/Heston/Merton runners` | `finmath MonteCarloAssetModel.getAssetValue(t,0).getRealizations()` | `EulerSchemeFromProcessModel` over `BrownianMotionFromMersenneRandomNumbers(seed=42)` | VERIFIED | extractBands method lines 441-452 loops t=1..horizonDays calling `sim.getAssetValue(t,0).getRealizations()`; all three finmath runners pass `AssetModelMonteCarloSimulationModel` to extractBands |
| `ForecastService bootstrap runner` | `Hipparchus MersenneTwister(42)` | Block resample of historical log returns, L=max(10,√H) | VERIFIED | runBootstrap lines 362-421: `new MersenneTwister(MC_SEED)` at line 375; `L = Math.max(10, (int) Math.sqrt(H))` at line 364; boundary-safe blockStart at line 398 |
| `README.md` | `docs/MODELS.md` | Markdown hyperlink `[Model documentation](docs/MODELS.md)` | VERIFIED | grep confirms line 100 of README.md: `See [Model documentation](docs/MODELS.md) for rationale...` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `MonteCarloFanChart.vue` | `props.forecast` (ForecastDto) | `portfolioStore.forecast.data` ← `axios.get('/api/portfolio/forecast')` ← `ForecastService.forecast()` ← finmath simulations over `buildEquityCurveLocal` + `logReturns` from seeded Postgres positions | Yes — 5000-path simulation over real portfolio history; no static arrays | FLOWING |
| `MonteCarloFanChart.vue` series areaStyle.color | `FAN_COLORS.bandOuter/bandInner/median` | `cssVar()` resolved from `--color-fan-*` CSS tokens at module init | Yes — resolved from live DOM CSS at startup | FLOWING |

### Behavioral Spot-Checks

Step 7b skipped — no runnable server available in the verification environment. The backend requires Docker + Postgres (Testcontainers) and the frontend requires a running Vite dev server. Spot-checks are deferred to the human verification tasks below.

The SUMMARY.md claims that the full backend suite ran as 96 tests, 0 failures, and that `npm run build` exits 0 with 56 frontend tests passing. These are documented as build-gate outputs in the summaries. Runtime probe via `bash` is not feasible without a running container environment.

### Probe Execution

No `scripts/*/tests/probe-*.sh` files declared or found for this phase. Probe execution not applicable.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| SIM-01 | 05-01-PLAN, 05-02-PLAN, 05-03-PLAN | User can view a Monte Carlo fan chart with p5/p25/p50/p75/p95 percentile bands | SATISFIED | MonteCarloFanChart.vue renders 5-band ECharts chart; ForecastService produces percentile arrays for all 4 models; ForecastStructuralTest validates monotone ordering and band widening |
| SIM-02 | 05-01-PLAN, 05-02-PLAN, 05-03-PLAN | User can switch forecast between GBM, Merton jump-diffusion, Heston, and block bootstrap | SATISFIED | ModelType enum with 4 constants; ForecastController dispatches to ForecastService; MonteCarloFanChart 4-button selector emits update:model; DashboardView re-fetches on model switch |
| SIM-03 | 05-03-PLAN | Documentation explains rationale, assumptions, and limitations of each stochastic model | SATISFIED | docs/MODELS.md contains 4 model sections each with What/Why/Assumptions/Parameters/Limitations; illustrative param disclaimers; Feller/Ito appendix; README hyperlink present |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None found | — | No TBD/FIXME/XXX/HACK/PLACEHOLDER markers in any phase-5 modified files | — | — |

No `return null` / empty implementation stubs detected in phase-5 source files. The one historical stub (`UnsupportedOperationException` in ForecastService) was intentionally placed in Plan 05-01 and fully replaced in Plan 05-02 — no remnants found.

No hardcoded hex colors in `MonteCarloFanChart.vue` series styling (FAN_COLORS object used throughout). No `var(--...)` strings passed to ECharts canvas (Pitfall 4 correctly mitigated).

### Human Verification Required

The following visual and interactive behaviors cannot be verified programmatically. They come from the deferred `checkpoint:human-verify` in Plan 05-03 (Task 4) and from this verifier's analysis.

---

#### 1. Fan Chart Renders with Widening Bands and Visible Median

**Test:** From repo root run `docker compose up`, visit `http://localhost:5173`, log in as alice / demo1234, and scroll to the "Monte Carlo Forecast" panel.
**Expected:** A fan chart displays with blue widening percentile bands over ~252 days. A darker blue median line (p50) is visible on top of the bands. The bands are narrow near Day 1 and widen toward the right.
**Why human:** ECharts canvas rendering and CSS token resolution cannot be verified without a live browser context. Static analysis confirms all 5 series are defined and FAN_COLORS is resolved, but actual visual output requires inspection.

---

#### 2. All Four Models Trigger Re-fetch and Produce Distinct Band Shapes

**Test:** With the forecast panel visible, click each of GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP in sequence.
**Expected:** Each click triggers a network request (visible in browser DevTools) and the chart updates. JUMP_DIFFUSION and HESTON bands should show fatter or different lower tails than GBM; BOOTSTRAP should look empirically rough (not smooth).
**Why human:** Visual band-shape differences and re-fetch confirmation require live browser interaction. The structural test `fourModels_produceDistinctBandShapes` confirms ≥3 distinct p95 values, but visual distinctiveness requires a human eye.

---

#### 3. Heston Illustrative-Parameters Disclaimer Is Visible

**Test:** Click the HESTON model toggle button.
**Expected:** A small italic line reading "Heston parameters are illustrative, not calibrated to live option prices" is visible below the model selector.
**Why human:** The `v-if="selectedModel === 'HESTON'"` condition is verified in code, but actual DOM rendering and CSS styling (visibility, readability) require visual inspection.

---

#### 4. docs/MODELS.md Reads Clearly for a Non-Specialist

**Test:** Open `docs/MODELS.md` in a text editor or browser.
**Expected:** The document reads clearly for a non-specialist hiring manager. Each model section explains what it is, why it's included, the parameters used (with explicit "illustrative" labeling for Heston and Merton), and known limitations. The fan-chart reading guide is understandable without prior quant finance knowledge.
**Why human:** Prose clarity and non-specialist readability are subjective and cannot be assessed programmatically.

---

### Gaps Summary

No gaps found. All 7 observable truths are VERIFIED by codebase evidence. All 16 required artifacts exist, are substantive (no stubs), and are wired together with verified data flow. The 3 requirement IDs (SIM-01, SIM-02, SIM-03) are satisfied.

The `human_needed` status is due to the 4 human verification items above — they are visual/interactive checks that were explicitly deferred from the Plan 05-03 `checkpoint:human-verify` task (Task 4) per the autonomous-run convention. All automated verifications pass.

---

_Verified: 2026-06-09T00:00:00Z_
_Verifier: Claude (gsd-verifier)_
