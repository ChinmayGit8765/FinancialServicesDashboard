---
phase: 05-stochastic-forecasting
plan: 02
subsystem: api
tags: [finmath-lib, monte-carlo, GBM, heston, merton, bootstrap, stochastic-forecasting, java, percentile-bands]

# Dependency graph
requires:
  - phase: 05-01
    provides: ForecastService stub + ForecastController + ForecastDto + ModelType + RED test scaffolds

provides:
  - ForecastService (complete 4-model Monte Carlo engine: GBM/Merton/Heston/Bootstrap)
  - Per-step percentile bands (p5/p25/p50/p75/p95) via nearest-rank sort+index extraction
  - /api/portfolio/forecast returning real simulation data for all 4 models
  - Full backend suite green (96 tests, 0 failures)

affects: [05-03-frontend-fan-chart]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "finmath AssetModelMonteCarloSimulationModel chain: TimeDiscretizationFromArray → BrownianMotionFromMersenneRandomNumbers → model → EulerSchemeFromProcessModel → MonteCarloAssetModel → getAssetValue(t,0).getRealizations()"
    - "extractPercentiles: clone+sort+clamp nearest-rank, monotone by construction"
    - "extractBands helper: loops t=1..horizonDays over AssetModelMonteCarloSimulationModel"
    - "Bootstrap: MersenneTwister(42), L=max(10,√H) block resample, boundary-safe blockStart ∈ [0, H-L]"
    - "Java 21 switch expression on ModelType enum (exhaustive, no default needed)"

key-files:
  modified:
    - backend/src/main/java/com/quantlens/analytics/service/ForecastService.java

key-decisions:
  - "finmath BlackScholesModel applies Ito correction (μ−σ²/2) in log-space automatically — no manual drift term added (T-05-04 mitigation, HC-11 guard)"
  - "Heston FULL_TRUNCATION scheme prevents variance going negative (T-05-05 mitigation)"
  - "Bootstrap L=max(10,√H): preserves ~monthly autocorrelation / volatility clustering; boundary-safe blockStart = rng.nextInt(H-L+1)"
  - "extractBands accepts AssetModelMonteCarloSimulationModel interface — works for both MonteCarloAssetModel (GBM/Heston) and MonteCarloMertonModel (Merton implements same interface)"
  - "Simulation stays synchronous per RESEARCH.md §Performance Note — no cache/executor added"
  - "Bootstrap uses step-major double[horizonDays][NUM_PATHS] matrix for memory-efficient per-step percentile extraction"

requirements-completed: [SIM-01, SIM-02]

# Metrics
duration: 6min
completed: 2026-06-09
---

# Phase 5 Plan 02: Monte Carlo Engine Implementation Summary

**4-model Monte Carlo engine (GBM/Merton/Heston/Bootstrap) + percentile extraction fully implemented in ForecastService, replacing the UnsupportedOperationException stub — all 16 forecast tests GREEN, full backend suite 96 tests passing**

## Performance

- **Duration:** 6 min
- **Started:** 2026-06-08T14:16:23Z
- **Completed:** 2026-06-08T14:22:?Z
- **Tasks:** 2 (implemented together in one atomic commit)
- **Files modified:** 1 (ForecastService.java)

## Accomplishments

- Replaced UnsupportedOperationException stub with four complete model runners:
  - **runGbm**: finmath BlackScholesModel → EulerScheme → MonteCarloAssetModel chain; finmath applies Ito (μ−σ²/2) internally; HC-11 guard confirms correctness
  - **runMerton**: MonteCarloMertonModel 10-param ctor (A5-confirmed); illustrative λ=0.10, μ_J=−0.10, σ_J=0.15
  - **runHeston**: HestonModel (theta BEFORE kappa — A4-confirmed), FULL_TRUNCATION, 2-factor BM; Feller guard already in constructor
  - **runBootstrap**: Hipparchus MersenneTwister(42), L=max(10,√H) block resample, boundary-safe
- Implemented `extractBands` helper accepting `AssetModelMonteCarloSimulationModel` interface (shared by GBM/Heston/Merton)
- `extractPercentiles` (nearest-rank, clone+sort+clamp) was already in the stub — reused unchanged
- Java 21 switch expression on ModelType enum (exhaustive, clean dispatch)
- All 16 forecast tests GREEN in 47 seconds (incl. controller integration)
- Full backend suite: 96 tests, 0 failures, 0 errors, 2 skipped (pre-existing)

## Confirmed Math Correctness

| Test | What it verifies | Result |
|------|-----------------|--------|
| HC-11 (ForecastMathHandComputedTest) | GBM E[S_T] within 1% of S₀·e^(μT)=110.517; median within 1% of 108.329; median<mean | GREEN |
| ForecastFinmathIntegrationTest (3 tests) | GBM/Heston/Merton instantiate + 5000 paths; Heston FULL_TRUNCATION | GREEN |
| ForecastStructuralTest.percentileBands_areMonotonicallyOrdered | p5≤p25≤p50≤p75≤p95 at every step for all 4 models | GREEN |
| ForecastStructuralTest.bandsWiden_withHorizon_gbm | p95−p5 at day 252 > at day 1 | GREEN |
| ForecastStructuralTest.reproducibility_fixedSeed_gbm | Two seed=42 runs produce byte-identical p50 arrays | GREEN |
| ForecastStructuralTest.bootstrap_outputMeanLogReturn | Bootstrap daily log return < 0.05 (moment preservation smoke) | GREEN |
| ForecastStructuralTest.fourModels_produceDistinctBandShapes | ≥3 distinct p95[251] values across models | GREEN |
| ForecastControllerIntegrationTest (7 tests) | 401 unauthenticated; 400 unknown model; 200+correct JSON shape for GBM/JUMP_DIFFUSION/HESTON/BOOTSTRAP; horizon clamp 1 and 504 | GREEN |

## Task Commits

1. **Task 1+2: Implement all 4 runners** - `fb4583f` (feat)
   - GBM + Merton + Heston finmath runners
   - Bootstrap pure-Java runner
   - extractBands + extractPercentiles helpers
   - Switch dispatch in forecast()

## Files Created/Modified

- `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java` — full engine (282 lines added, 18 removed; stub replaced)

## Deviations from Plan

None — plan executed exactly as written.

The finmath API signatures confirmed in 05-01-SUMMARY (A4: Heston theta-before-kappa, A5: Merton 10-param ctor, A6: createRandomVariable(double)) were used verbatim. No deviations from the RESEARCH.md §finmath_chain instructions.

Tasks 1 and 2 were implemented atomically in a single commit because the bootstrap runner was straightforward pure-Java and the extractPercentiles helper from the stub was already correct — splitting into two commits would have required a temporary incomplete state.

## Known Stubs

None — all four model runners are fully implemented. The ForecastService is no longer stubbed.

## Threat Flags

No new security surfaces beyond those enumerated in the plan's threat model (T-05-01/02/03/04/05 all mitigated):
- T-05-04 (Ito correction): finmath BlackScholesModel applies (μ−σ²/2) internally; HC-11 guards against regression
- T-05-05 (Heston variance): FULL_TRUNCATION + Feller guard in constructor

## Next Phase Readiness

Plan 05-03 (frontend fan chart) can now:
- Call GET /api/portfolio/forecast returning real p5/p25/p50/p75/p95 bands for all 4 models
- Confirm MonteCarloFanChart.vue test scaffold (RED) is the only remaining gap

---
*Phase: 05-stochastic-forecasting*
*Completed: 2026-06-09*

## Self-Check: PASSED

### Created files exist
- `backend/src/main/java/com/quantlens/analytics/service/ForecastService.java` ✓

### Commits verified
- `fb4583f` feat(05-02): implement GBM, Merton, Heston finmath runners + bootstrap + percentile extraction ✓

### Build gates
- `./mvnw test -Dtest=ForecastMathHandComputedTest,ForecastFinmathIntegrationTest` → Tests run: 4, Failures: 0 ✓
- `./mvnw test -Dtest=ForecastStructuralTest,ForecastControllerIntegrationTest` → Tests run: 12, Failures: 0 ✓
- `./mvnw verify` → Tests run: 96, Failures: 0, Errors: 0, BUILD SUCCESS ✓
