# Phase 5: Stochastic Forecasting — Research

**Researched:** 2026-06-08
**Domain:** Monte Carlo stochastic simulation (finmath-lib 6.1.7 + Hipparchus 4.0.3), fan-chart rendering (vue-echarts 8 / ECharts 6), historical block bootstrap
**Confidence:** HIGH for finmath API (confirmed against GitHub source + test files); HIGH for Hipparchus percentile extraction (confirmed from Phase 4 code); MEDIUM for ECharts fan-chart stacking technique (confirmed from official handbook + community examples); HIGH for mathematical formulas (stochastic calculus standard results)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Models and library**
- Add `net.finmath:finmath-lib:6.1.7` to backend pom. Use its Monte Carlo SDE processes: GBM (`BlackScholesModel` + `EulerSchemeFromProcessModel`), Merton jump-diffusion (`MertonModel`), Heston (`HestonModel` with `FULL_TRUNCATION` scheme). Implement historical block bootstrap directly (no finmath dependency for bootstrap).
- Numeric: simulation in `double` (finmath/Hipparchus). Final percentile band values as `double` for chart bands. Money base = current portfolio value (BigDecimal → double for projection base).

**Forecast model**
- Project portfolio value forward; default horizon 252 trading days (configurable). Base = current total portfolio market value.
- Calibrate μ and σ from portfolio historical daily log returns (reuse Phase 2/4 logic), annualized. Jump-diffusion adds illustrative λ, μ_J, σ_J. Heston uses fixed illustrative κ, θ, ξ, ρ with Feller condition enforced + UI disclaimer. Bootstrap resamples historical return blocks.
- 5000 paths, fixed RNG seed = 42.
- Output: p5/p25/p50/p75/p95 per horizon step.

**Surface (REST + frontend)**
- Endpoint: `GET /api/portfolio/forecast?model={GBM|JUMP_DIFFUSION|HESTON|BOOTSTRAP}&horizon={days}` — principal-scoped, `@Transactional(readOnly=true)`, returns `{ model, horizonDays, dates|steps, p5[], p25[], p50[], p75[], p95[] }`.
- Frontend: `MonteCarloFanChart.vue` (ECharts stacked/area bands + median line, dark theme) + model selector. Fills Phase-3 "Monte Carlo — Phase 5" SlotPlaceholder. New Pinia store state `forecast` (with selected model); switching model re-fetches.

**SIM-03 documentation**
- `docs/MODELS.md` — non-specialist rationale for all 4 models.

**Testing**
- Percentile ordering; bands widen with horizon; GBM median ≈ base × exp((μ−σ²/2)·t); reproducibility under fixed seed; bootstrap mean/vol ≈ historical moments; finmath integration test; frontend component test; hand-computed GBM analytic-mean anchor.

### Claude's Discretion
- Exact finmath API usage per model, path count tuning, horizon default, whether forecast lives in analytics vs new module, percentile interpolation method, ECharts band rendering technique.

### Deferred Ideas (OUT OF SCOPE)
- Live Heston calibration from real return data (v2 — SIM-04).
- Additional models (SABR, variance gamma) — v2.
- AI narration of the forecast (Phase 6+).
- Backtesting (out of scope).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SIM-01 | User can view a Monte Carlo fan chart of projected portfolio value with p5/p25/p50/p75/p95 percentile bands | finmath-lib API for all 4 models; percentile extraction; ECharts fan-chart technique |
| SIM-02 | User can switch the forecast between GBM, Merton jump-diffusion, Heston, and historical block bootstrap | Model enum on endpoint; Pinia store model-selector pattern; re-fetch on model switch |
| SIM-03 | Documentation explains rationale, assumptions, and limitations of each stochastic model | `docs/MODELS.md` outline with per-model sections |
</phase_requirements>

---

## Summary

Phase 5 delivers Monte Carlo stochastic forecasting rendered as a percentile fan chart. The backend is a Spring service in `com.quantlens.analytics` (or a `forecast` sub-package) that runs 5000 paths × 252 steps per request, extracts five percentile bands, and returns them as a time-series JSON. Four models are supported: GBM (the calibrated base), Merton jump-diffusion (regime stress), Heston stochastic-vol (smile-aware), and historical block bootstrap (empirical, regime-free). The frontend replaces the Phase-3 SlotPlaceholder with `MonteCarloFanChart.vue` using ECharts stacked area series.

The most critical implementation detail is the **Ito correction in GBM**: the simulation drift must use `(μ − σ²/2)` in the exponent, not `μ`. Without this correction the expected portfolio value drifts upward faster than the model intends, producing an optimistic bias that is mathematically wrong. A unit test that checks `E[S_T] ≈ S_0 · e^{μT}` (analytic mean) is the primary correctness gate.

The second critical detail is **dependency management**: finmath-lib 6.1.7 pulls `commons-math3:3.6.1` as a compile-scope transitive dependency. Spring Boot 3.5.13 does not directly manage `commons-math3`, so there is no version conflict, but the older library will appear on the classpath alongside Hipparchus 4.0.3. Since Hipparchus is a fork of Commons Math with a different groupId (`org.hipparchus` vs `org.apache.commons`), there is no class collision. However, finmath itself uses Commons Math 3 internally — this cannot be excluded without breaking finmath. Accept this as harmless coexistence; do not try to exclude `commons-math3`.

**Primary recommendation:** Place the forecast service at `com.quantlens.analytics.service.ForecastService` (inside the existing `analytics` Modulith module), add finmath-lib to pom.xml, implement the four model runners as private methods, extract percentiles using `Arrays.sort` + index arithmetic (faster and more transparent than DescriptiveStatistics for this use case), and cache the result per (portfolioId, model, horizon) with a short TTL to avoid re-running 5000-path simulation on every poll.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Monte Carlo path generation (GBM/Merton/Heston) | API / Backend | — | CPU-bound simulation; must not run on request thread — use `CompletableFuture` or cached result |
| Historical block bootstrap path generation | API / Backend | — | Also CPU-bound; pure Java array manipulation over log-return history |
| μ/σ calibration from log returns | API / Backend | — | Reuse existing `RiskCalculator.logReturns()` + `DescriptiveStatistics` |
| Percentile extraction (p5/p25/p50/p75/p95) | API / Backend | — | `Arrays.sort` + index arithmetic on path terminal/step values |
| REST endpoint + principal scoping | API / Backend | — | Copy `resolvePortfolioId` pattern from `AnalyticsController` |
| Fan-chart band rendering | Browser / Client | — | ECharts stacked area series; no server-side rendering needed |
| Model selector state | Browser / Client | — | Pinia `forecast.selectedModel` ref; switching triggers `fetchForecast` |
| Fan-chart color tokens | Browser / Client | — | `--color-fan-p50`, `--color-fan-band-1`, `--color-fan-band-2` from `style.css` |

---

## Standard Stack

### Core (already in pom.xml — no new addition needed)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `org.hipparchus:hipparchus-stat` | 4.0.3 | `DescriptiveStatistics` for μ/σ calibration; log-return computation | Already in pom; Phase 4 uses it |
| `org.hipparchus:hipparchus-core` | 4.0.3 | `MersenneTwister` (fallback RNG); array math | Already in pom |

### New Addition — Phase 5 Only

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `net.finmath:finmath-lib` | 6.1.7 | `BrownianMotionFromMersenneRandomNumbers`, `EulerSchemeFromProcessModel`, `BlackScholesModel`, `MertonModel`, `HestonModel`, `MonteCarloAssetModel`, `TimeDiscretizationFromArray` — complete Monte Carlo SDE engine | Only pure-Java library shipping GBM, Heston, and Merton as ready-to-run `MonteCarloProcess` implementations. Written by Prof. Christian Fries (LMU Munich). [VERIFIED: Maven Central + GitHub finmath/finmath-lib] |

**Maven coordinates:**
```xml
<dependency>
    <groupId>net.finmath</groupId>
    <artifactId>finmath-lib</artifactId>
    <version>6.1.7</version>
</dependency>
```

**Transitive dependencies pulled by finmath-lib 6.1.7:** [VERIFIED: finmath.net/finmath-lib/dependencies.html]
- `org.apache.commons:commons-math3:3.6.1` — finmath uses CM3 internally for some math primitives
- `org.apache.commons:commons-lang3:3.18.0` — utility methods
- `org.jblas:jblas:1.2.4` — linear algebra (optional use within finmath)

**Conflict analysis:** [ASSUMED — based on groupId analysis + Spring Boot BOM inspection]
- `commons-math3` (groupId `org.apache.commons`) does NOT conflict with Hipparchus 4.0.3 (groupId `org.hipparchus`) — different packages, zero class collision.
- Spring Boot 3.5.13 BOM does not manage `commons-math3`, so Maven will use finmath's declared version 3.6.1 without conflict.
- `jblas` 1.2.4 has no known conflict with any Spring Boot managed dependency.
- **No exclusions needed.** Accept coexistence.

### Frontend (already installed)

| Library | Version | Purpose | Confirmed |
|---------|---------|---------|-----------|
| `echarts` | 6.1.0 | Chart engine — stacked area series, `stack` property, `areaStyle` | [VERIFIED: package.json per CLAUDE.md] |
| `vue-echarts` | 8.0.1 | Vue 3 wrapper | [VERIFIED: package.json per CLAUDE.md] |

**No new npm packages required for Phase 5.**

---

## Package Legitimacy Audit

> `slopcheck` was not available at research time. The only new Java package is `net.finmath:finmath-lib`.

| Package | Registry | Age | Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|-----------|-------------|
| `net.finmath:finmath-lib:6.1.7` | Maven Central | ~14 years (project since ~2012) | Moderate (academic/practitioner Java quant community) | [github.com/finmath/finmath-lib](https://github.com/finmath/finmath-lib) — active, 2026-06 commit | unavailable | Approved — well-known academic library by Prof. Christian Fries, LMU Munich; confirmed on Maven Central 6.1.7 [VERIFIED: central.sonatype.com] |

**Packages removed due to slopcheck [SLOP] verdict:** none

**Packages flagged as suspicious [SUS]:** none

*slopcheck was unavailable at research time. The package above is tagged `[VERIFIED: Maven Central]` because it was confirmed on the authoritative registry AND has a known provenance (academic author, public GitHub, 14-year publication history). No checkpoint:human-verify task is required for this well-established library.*

---

## Architecture Patterns

### System Architecture Diagram

```
HTTP GET /api/portfolio/forecast?model=GBM&horizon=252
           │
           ▼
   ForecastController
   (resolvePortfolioId, @Transactional readOnly)
           │
           ▼
   ForecastService
   ┌────────────────────────────────────────────────────────┐
   │  1. Calibrate μ, σ from portfolio log returns          │
   │     (RiskCalculator.buildEquityCurveLocal + logReturns)│
   │  2. Dispatch to model runner (switch on ModelType)     │
   │  3. Run 5000 paths × horizonDays steps                │
   │  4. Extract percentile bands per step                  │
   │  5. Return ForecastDto                                 │
   └────────────────────────────────────────────────────────┘
          │              │              │              │
      GBM runner   Merton runner   Heston runner  Bootstrap
   (finmath)       (finmath)       (finmath)      (pure Java)
          │
          ▼
   ForecastDto { model, horizonDays, steps[], p5[], p25[], p50[], p75[], p95[] }
          │
          ▼ JSON
   Pinia forecast asyncState
          │
          ▼
   MonteCarloFanChart.vue
   (ECharts stacked area bands + median line + model selector)
```

### Recommended Project Structure

```
backend/src/main/java/com/quantlens/analytics/
├── service/
│   ├── RiskCalculator.java          (existing — reuse logReturns + buildEquityCurveLocal)
│   ├── FamaFrenchCalculator.java    (existing)
│   ├── CointegrationScanner.java    (existing)
│   └── ForecastService.java         (NEW — Phase 5 Monte Carlo engine)
└── api/
    ├── AnalyticsController.java     (existing — add /forecast endpoint OR new controller)
    ├── ForecastDto.java             (NEW — record)
    └── ModelType.java               (NEW — enum GBM, JUMP_DIFFUSION, HESTON, BOOTSTRAP)

frontend/src/
├── components/
│   └── MonteCarloFanChart.vue       (NEW — fan chart + model selector)
├── api/
│   └── forecast.ts                  (NEW — ForecastDto type + fetchForecast function)
└── stores/
    └── portfolio.ts                 (EXTEND — add forecast asyncState + fetchForecast)

docs/
└── MODELS.md                        (NEW — SIM-03 non-specialist rationale doc)
```

---

## finmath-lib 6.1.7 — Exact API Reference

This section is the primary deliverable of this research phase. All class and method names are verified against the finmath-lib GitHub source at commit HEAD (June 2026). [VERIFIED: github.com/finmath/finmath-lib source]

### Core Classes and Import Paths

```java
// Time grid
import net.finmath.time.TimeDiscretization;
import net.finmath.time.TimeDiscretizationFromArray;

// Stochastic driver (Mersenne Twister with seed)
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.IndependentIncrements;

// Numerical scheme
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.montecarlo.process.MonteCarloProcess;

// Simulation wrapper
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationModel;

// Model implementations (in models/ subdirectory)
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.models.MertonModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel.Scheme;

// Random variable result type
import net.finmath.stochastic.RandomVariable;

// Random variable factory
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
```

### Step 1: Build TimeDiscretization

```java
// 252 daily steps in [0, 1] year. dt = 1/252.
// Constructor: TimeDiscretizationFromArray(initialTime, numberOfTimeSteps, deltaT)
// Result: 253 time points at indices 0..252 (index 0 = t=0, index 252 = t=1 year)
TimeDiscretization timeDiscretization = new TimeDiscretizationFromArray(
    0.0,          // initialTime
    horizonDays,  // numberOfTimeSteps (e.g. 252)
    1.0 / 252.0   // deltaT (one trading day)
);
```

### Step 2: Build BrownianMotion with Fixed Seed

```java
// Mersenne Twister seeded with SEED=42 for reproducibility.
// numberOfFactors:
//   GBM        = 1 (one Brownian factor)
//   Merton     = 3 (Merton uses internally: 1 diffusion + 2 jump factors)
//   Heston     = 2 (one for asset, one for variance — must match model requirement)
//   Bootstrap  = no finmath BM needed
BrownianMotionFromMersenneRandomNumbers brownianMotion =
    new BrownianMotionFromMersenneRandomNumbers(
        timeDiscretization,
        numberOfFactors,    // see per-model notes above
        5000,               // numberOfPaths
        42                  // FIXED SEED — reproducibility requirement from CONTEXT.md
    );
```

> **API note:** `BrownianMotionFromMersenneRandomNumbers` is NOT the same class as `BrownianMotionLazyInit`. Both exist in the library. The `FromMersenneRandomNumbers` variant is the one used in finmath's own test suite (`MonteCarloBlackScholesModelTest`) and is the correct choice here. [VERIFIED: finmath test source]

### Step 3a: GBM (BlackScholesModel)

The finmath `BlackScholesModel` handles the Ito correction internally. In log-space, the model evolves as:

```
ln S(t+dt) = ln S(t) + (μ − σ²/2)·dt + σ·√dt·Z
```

so `E[S(t)] = S₀·e^{μt}` holds exactly by construction. The `riskFreeRate` parameter in the constructor maps to the GBM drift μ (not the risk-free rate in a risk-neutral sense). For a calibrated portfolio forecast, pass the annualized historical mean log return as `riskFreeRate`.

```java
// BlackScholesModel constructor (double-based version):
// BlackScholesModel(double initialValue, double riskFreeRate, double volatility,
//                   RandomVariableFactory randomVariableFactory)
ProcessModel gbmModel = new BlackScholesModel(
    initialValue,             // S₀ = current portfolio value (double)
    annualizedMu,             // μ (annualized mean log return from calibration)
    annualizedSigma,          // σ (annualized volatility from calibration)
    new RandomVariableFromArrayFactory()
);

BrownianMotionFromMersenneRandomNumbers bm = new BrownianMotionFromMersenneRandomNumbers(
    timeDiscretization, 1, 5000, 42);

MonteCarloProcess process = new EulerSchemeFromProcessModel(gbmModel, bm);
AssetModelMonteCarloSimulationModel sim = new MonteCarloAssetModel(process);
```

**Ito-correctness verification formula (required unit test):**
```
E[S_T] = S₀ · e^{μ · T}       (analytic mean under GBM, T = 1 year)
median[S_T] ≈ S₀ · e^{(μ − σ²/2) · T}   (Ito-correct log-normal median)
```
If median > S₀·e^{(μ−σ²/2)·T} by more than 1%, the Ito correction is missing from the simulation.

### Step 3b: Merton Jump-Diffusion (MertonModel)

Merton adds a compound Poisson jump process on top of GBM:

```
dS/S = (μ − λ·(e^{μ_J + σ_J²/2} − 1)) dt + σ dW + (e^J − 1) dN_t
```

where `J ~ N(μ_J, σ_J²)` is the log-jump size, `N_t` is Poisson(λ).

The `MonteCarloMertonModel` convenience class is the simplest approach: it internally constructs a `MertonJumpProcess` as the stochastic driver and wraps it in `EulerSchemeFromProcessModel`.

```java
// MonteCarloMertonModel constructor:
// MonteCarloMertonModel(TimeDiscretization, int numberOfPaths, int seed,
//                       double initialValue, double riskFreeRate, double volatility,
//                       double jumpIntensity, double jumpSizeMean, double jumpSizeStDev,
//                       RandomVariableFactory)
MonteCarloMertonModel mertonSim = new MonteCarloMertonModel(
    timeDiscretization,
    5000,                 // numberOfPaths
    42,                   // FIXED SEED
    initialValue,         // S₀
    annualizedMu,         // drift (same as GBM calibration)
    annualizedSigma,      // σ (diffusion component)
    JUMP_LAMBDA,          // λ = 0.1 (illustrative — ~1 jump per 10 years)
    JUMP_MU_J,            // μ_J = -0.10 (mean log-jump = -10% — downward bias)
    JUMP_SIGMA_J,         // σ_J = 0.15 (jump vol = 15%)
    new RandomVariableFromArrayFactory()
);
// MonteCarloMertonModel extends MonteCarloAssetModel — use as AssetModelMonteCarloSimulationModel
AssetModelMonteCarloSimulationModel sim = mertonSim;
```

**Import for MonteCarloMertonModel:**
```java
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloMertonModel;
```

**Illustrative jump parameters (calibrate to illustrative equity values):**
```java
static final double JUMP_LAMBDA  = 0.10;  // 1 jump per 10 years on average
static final double JUMP_MU_J    = -0.10; // avg log-jump = -10% (negative skew = left tail)
static final double JUMP_SIGMA_J =  0.15; // jump magnitude std = 15%
```
These produce occasional -10% to -25% portfolio drops, which is plausible for equity stress scenarios. Document these as illustrative in the UI and MODELS.md.

### Step 3c: Heston Stochastic-Volatility (HestonModel)

The Heston SDE:
```
dS = μ·S dt + √V · S dW₁
dV = κ(θ − V) dt + ξ·√V dW₂     (dW₁·dW₂ = ρ·dt)
```

**Feller condition (required enforcement):** `2κθ > ξ²`. If violated, the variance process V can hit zero and reflect (REFLECTION scheme) or become negative (FULL_TRUNCATION scheme absorbs this). Use `FULL_TRUNCATION` scheme (matches finmath's own test suite). Always assert `2 * kappa * theta > xi * xi` before instantiation.

```java
// Illustrative parameters (empirically defensible equity values):
static final double HESTON_V0    = 0.04;  // initial variance = σ₀² = (0.20)² = calibrated σ²
static final double HESTON_KAPPA = 2.0;   // mean-reversion speed (moderate)
static final double HESTON_THETA = 0.04;  // long-run variance = (0.20)²
static final double HESTON_XI    = 0.3;   // vol-of-vol
static final double HESTON_RHO   = -0.7;  // leverage effect (negative correlation)

// Feller check: 2 × 2.0 × 0.04 = 0.16 > 0.09 = (0.3)² → Feller satisfied

// HestonModel constructor:
// HestonModel(RandomVariable initialValue, RandomVariable riskFreeRate,
//             RandomVariable volatility, RandomVariable discountRate,
//             RandomVariable theta, RandomVariable kappa, RandomVariable xi,
//             RandomVariable rho, Scheme scheme, RandomVariableFactory)
//
// "volatility" here = sqrt(v0) = initial vol (not variance).
// Use RandomVariable wrappers via the factory for scalar values:
RandomVariableFromArrayFactory rvFactory = new RandomVariableFromArrayFactory();

ProcessModel hestonModel = new HestonModel(
    rvFactory.createRandomVariable(initialValue),          // S₀
    rvFactory.createRandomVariable(annualizedMu),          // drift
    rvFactory.createRandomVariable(Math.sqrt(HESTON_V0)), // volatility = sqrt(v0)
    rvFactory.createRandomVariable(0.0),                   // discountRate (0 for equity)
    rvFactory.createRandomVariable(HESTON_THETA),          // long-run variance
    rvFactory.createRandomVariable(HESTON_KAPPA),          // mean-reversion speed
    rvFactory.createRandomVariable(HESTON_XI),             // vol-of-vol
    rvFactory.createRandomVariable(HESTON_RHO),            // correlation
    HestonModel.Scheme.FULL_TRUNCATION,                    // prevent variance going negative
    rvFactory
);

// Heston needs 2 Brownian factors (asset + variance)
BrownianMotionFromMersenneRandomNumbers bm = new BrownianMotionFromMersenneRandomNumbers(
    timeDiscretization, 2, 5000, 42);

MonteCarloProcess process = new EulerSchemeFromProcessModel(hestonModel, bm);
AssetModelMonteCarloSimulationModel sim = new MonteCarloAssetModel(process);
```

**Feller check assertion (must be in ForecastService constructor or static init):**
```java
if (2.0 * HESTON_KAPPA * HESTON_THETA <= HESTON_XI * HESTON_XI) {
    throw new IllegalStateException(
        "Heston Feller condition violated: 2κθ ≤ ξ². " +
        "kappa=" + HESTON_KAPPA + " theta=" + HESTON_THETA + " xi=" + HESTON_XI);
}
```

### Step 4: Extract Asset Values per Time Step

The `AssetModelMonteCarloSimulationModel` interface provides:
```java
// Get asset value at time index (0-based), assetIndex=0 for single-asset
RandomVariable getAssetValue(int timeIndex, int assetIndex)
    throws CalculationException;
```

For each time index from 1 to horizonDays:
```java
// Extract 5000 path realizations at step t
RandomVariable rv = sim.getAssetValue(t, 0);
double[] pathValues = rv.getRealizations();  // double[] of length 5000
```

> **API note:** The `RandomVariable.getRealizations()` method returns a `double[]` of length equal to `numberOfPaths`. [VERIFIED: github.com/finmath/finmath-lib RandomVariable.java interface]

### Step 5: Percentile Extraction

Sort and index. This is faster and more transparent than `DescriptiveStatistics.getPercentile()` for this use case (DescriptiveStatistics copies the array internally on every call anyway).

```java
/**
 * Extract p5, p25, p50, p75, p95 from a sorted double[] of path values.
 * Sort is done IN-PLACE on a copy — do not pass the original array.
 */
private static double[] extractPercentiles(double[] pathValues) {
    double[] sorted = pathValues.clone();
    Arrays.sort(sorted);
    int n = sorted.length;
    // Nearest-rank method: index = ceil(percentile/100 × n) − 1, clamped to [0, n-1]
    return new double[] {
        sorted[clamp((int) Math.ceil(0.05 * n) - 1, 0, n - 1)],  // p5
        sorted[clamp((int) Math.ceil(0.25 * n) - 1, 0, n - 1)],  // p25
        sorted[clamp((int) Math.ceil(0.50 * n) - 1, 0, n - 1)],  // p50 (median)
        sorted[clamp((int) Math.ceil(0.75 * n) - 1, 0, n - 1)],  // p75
        sorted[clamp((int) Math.ceil(0.95 * n) - 1, 0, n - 1)]   // p95
    };
}

private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
```

**Monotone ordering guarantee:** Sorting ensures p5 ≤ p25 ≤ p50 ≤ p75 ≤ p95 by construction. No post-sort clamping needed.

---

## Calibration: Estimating μ and σ

**Reuse existing helpers from RiskCalculator.** The `buildEquityCurveLocal()` and `logReturns()` methods are package-accessible statics (confirmed in `RiskCalculator.java` lines 261-269). Call them from `ForecastService` using the same equity-curve-building logic.

```java
// In ForecastService:
// 1. Build equity curve (reuse RiskCalculator pattern)
List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);
List<DateValueDto> curve = riskCalculator.buildEquityCurveLocal(positions);
double[] dailyLogReturns = RiskCalculator.logReturns(curve);

// 2. Calibrate annualized μ and σ
DescriptiveStatistics stats = new DescriptiveStatistics(dailyLogReturns);
double dailyMu    = stats.getMean();
double dailySigma = stats.getStandardDeviation();
double annualizedMu    = dailyMu    * 252.0;           // annualize mean
double annualizedSigma = dailySigma * Math.sqrt(252.0); // annualize vol

// 3. Get current portfolio value (base for simulation)
// Compute as sum(qty × latestClose) using same pattern as RiskCalculator
double initialValue = computeCurrentPortfolioValue(portfolioId);
```

**Drift estimation caveat (document in MODELS.md):** The historical mean log return is a noisy estimator of the true drift. For a 2-year daily history (504 returns), the standard error of the mean is σ/√504 ≈ 0.9% for σ=20% — comparable in magnitude to the estimate itself. The fan chart represents potential future paths, not a prediction. [ASSUMED — standard statistical result, well-documented in literature]

**Heston v0 calibration:** Set `HESTON_V0 = annualizedSigma² / 1.0` (use calibrated annualized variance as the initial variance). This ensures the Heston model starts with the same volatility regime as the GBM model and the fan charts are comparable at t=0.

---

## Historical Block Bootstrap

Block bootstrap is the only model that does NOT use finmath. It is implemented in pure Java.

### Algorithm

```
INPUT: dailyLogReturns double[] of length H (e.g. 504)
       blockLength L (see guidance below)
       numPaths = 5000
       horizonDays = 252
       seed = 42

FOR each path p in [0, numPaths):
    path[p][0] = initialValue        // S₀
    currentValue = initialValue
    t = 0
    WHILE t < horizonDays:
        // Pick a random start index in [0, H - L] (inclusive)
        blockStart = rng.nextInt(H - L + 1)
        FOR i in [blockStart, blockStart + L):
            IF t >= horizonDays: break
            currentValue = currentValue * exp(dailyLogReturns[i])
            path[p][t + 1] = currentValue
            t++
    path[p] complete

OUTPUT: path[numPaths][horizonDays + 1]
```

**Block length guidance:** [ASSUMED — standard bootstrap literature]
- Rule of thumb: `L ≈ max(10, sqrt(H))` where H is the history length.
- For H=504 (2 years): `L = max(10, sqrt(504)) ≈ max(10, 22) = 22` trading days (~1 month).
- Rationale: blocks preserve short-run autocorrelation in returns (momentum, volatility clustering). i.i.d. sampling (L=1) destroys these structures and underestimates tail risk. L=22 is a common practitioner choice for equity daily returns.
- Use non-overlapping blocks in the sampling step (the random start index is chosen uniformly from [0, H-L]) — overlapping blocks are allowed (circular bootstrap variant) but non-overlapping is simpler and sufficient here.

**RNG for bootstrap:** Use Hipparchus `MersenneTwister(42)` directly (already in pom):
```java
import org.hipparchus.random.MersenneTwister;
MersenneTwister rng = new MersenneTwister(42);
// rng.nextInt(H - L + 1) for block start
```

**Why blocks, not i.i.d.:** Daily equity returns exhibit volatility clustering (ARCH effects) and short-term momentum over 1-5 day horizons. Resampling individual days destroys these features, understating short-horizon tail risk and overstating long-horizon diversification. Blocks of ~22 days (~1 month) preserve intra-month return structure. Document this explicitly in MODELS.md.

**Bootstrap simulation handles non-normality automatically:** Because it resamples actual observed returns, it captures skewness, excess kurtosis, and any fat-tail behavior present in the historical data. No distributional assumption is made. This is its key advantage and the primary selling point in MODELS.md.

---

## Reproducibility

**Fixed seed = 42** wired in constants:
```java
private static final int MC_SEED = 42;
private static final int NUM_PATHS = 5000;
```

For finmath models: `BrownianMotionFromMersenneRandomNumbers(timeDiscretization, nFactors, NUM_PATHS, MC_SEED)` and `MonteCarloMertonModel(timeDiscretization, NUM_PATHS, MC_SEED, ...)`.

For bootstrap: `new MersenneTwister(MC_SEED)` — each `ForecastService.runBootstrap(...)` call creates a fresh Mersenne Twister from the same seed.

**Result:** Two calls to `GET /api/portfolio/forecast?model=GBM&horizon=252` on the same portfolio produce byte-identical JSON responses. README screenshots will be stable.

**Caching recommendation:** A simple `@Cacheable` or a local `ConcurrentHashMap<CacheKey, ForecastDto>` with a 5-minute TTL avoids re-running 5000-path simulation on every poll. Key = `(portfolioId, model, horizon)`. Given the fixed seed, the cached result is identical to a fresh computation.

---

## Performance

**Single-threaded estimate (5000 paths × 252 steps):**
- GBM: ~50–100ms (one Euler step per path per time step; highly vectorized in finmath). [ASSUMED — based on finmath benchmark literature]
- Merton: ~100–200ms (jump process adds overhead from compound Poisson draws). [ASSUMED]
- Heston: ~150–250ms (two SDEs per step). [ASSUMED]
- Bootstrap: ~20–50ms (pure array copy; no transcendental functions per step). [ASSUMED]

All four models are well within the 2-second p99 response time budget for a dashboard panel, assuming simulation runs on the request thread once. With caching, subsequent calls are sub-millisecond.

**Portfolio aggregate approach (locked in CONTEXT.md):** Simulate the single aggregate portfolio value (not per-asset). This is correct: treat the portfolio as a single asset with calibrated μ and σ from the portfolio-level equity curve. Multi-asset simulation (correlating 15 individual stocks) would be 15× more computation and requires a correlation matrix, introducing look-ahead bias risk and matrix non-PD handling. The aggregate approach avoids all of this and is the appropriate choice for a portfolio-level fan chart.

**Performance trap from PITFALLS.md:** Do NOT run simulation synchronously on the request thread for path counts > 1000. Options:
1. **Cache result** (simplest — since seed is fixed, cache is always valid for same (portfolio, model, horizon)).
2. **`@Async` + cache** if simulation must run asynchronously.
3. For Phase 5 demo: synchronous is acceptable if cached after first call. 5000 paths × 252 steps × GBM takes ~100ms, which is acceptable for a dashboard panel initial load.

---

## ECharts Fan-Chart Technique

### The Band-Difference Trick

ECharts stacked area series accumulate values additively. To render bands between percentiles (e.g., the area between p5 and p25), each series must contain the **difference** between adjacent percentile levels, not the absolute value. The base (p5) is rendered first; each subsequent band adds its height on top.

**Series layout for p5/p25/p50/p75/p95 fan chart:**

```typescript
// All series share the same stack key: 'fan'
// Series values at each step:
//   series[0] (base/invisible): p5 values (absolute)       — areaStyle: transparent
//   series[1] (band p5→p25):   p25[i] - p5[i]             — areaStyle: band-2 color
//   series[2] (band p25→p75):  p75[i] - p25[i]            — areaStyle: band-1 color (wider band)
//   series[3] (band p75→p95):  p95[i] - p75[i]            — areaStyle: band-2 color
//   series[4] (median line):   p50 values (absolute)       — NO stack; displayed on top

const series: EChartsSeries[] = [
  // Base: p5 (invisible fill — just establishes the stack floor)
  {
    type: 'line',
    data: p5,
    stack: 'fan',
    symbol: 'none',
    lineStyle: { opacity: 0 },
    areaStyle: { color: 'transparent' },
  },
  // Band: p5 → p25 (outer band, lighter)
  {
    type: 'line',
    data: p25.map((v, i) => v - p5[i]),
    stack: 'fan',
    symbol: 'none',
    lineStyle: { opacity: 0 },
    areaStyle: { color: 'var(--color-fan-band-2)' },  // rgba(14,165,233,0.12)
  },
  // Band: p25 → p75 (inner band, more opaque — the interquartile range)
  {
    type: 'line',
    data: p75.map((v, i) => v - p25[i]),
    stack: 'fan',
    symbol: 'none',
    lineStyle: { opacity: 0 },
    areaStyle: { color: 'var(--color-fan-band-1)' },  // rgba(14,165,233,0.25)
  },
  // Band: p75 → p95 (outer band, lighter)
  {
    type: 'line',
    data: p95.map((v, i) => v - p75[i]),
    stack: 'fan',
    symbol: 'none',
    lineStyle: { opacity: 0 },
    areaStyle: { color: 'var(--color-fan-band-2)' },  // rgba(14,165,233,0.12)
  },
  // Median line: p50 (NOT stacked — absolute values; drawn on top of all bands)
  {
    type: 'line',
    name: 'Median (p50)',
    data: p50,
    // NO stack property
    symbol: 'none',
    lineStyle: { color: 'var(--color-fan-p50)', width: 2 },  // #0ea5e9
    areaStyle: undefined,
  },
]
```

**Reserved CSS tokens (from `style.css` lines 65-68):**
```css
--color-fan-p50:    #0ea5e9;                      /* median line */
--color-fan-band-1: rgba(14, 165, 233, 0.25);    /* IQR band p25-p75 */
--color-fan-band-2: rgba(14, 165, 233, 0.12);    /* outer bands p5-p25, p75-p95 */
```

> **ECharts canvas limitation:** CSS custom properties cannot be read directly in a canvas context. Use the same fallback pattern as `CHART_COLORS` in `chart-colors.ts` — read via `getComputedStyle` at module init time and export as `FAN_COLORS.median`, `FAN_COLORS.bandInner`, `FAN_COLORS.bandOuter`.

### vue-echarts 8 Component Pattern

Follow the exact pattern from `PnlChart.vue`:
```vue
<v-chart
  class="chart"
  :option="option"
  :autoresize="true"
/>
<!-- NO :theme prop — THEME_KEY provided in App.vue propagates automatically -->
```

The `option` is a `computed<EChartsOption>` that rebuilds when `props.forecast` changes.

### Model Selector

A `<select>` element (or segmented buttons matching `AllocationChart.vue`'s `.chart-toggle` pattern) bound to `selectedModel` ref. On change, emit an event or call store directly:

```typescript
const selectedModel = ref<'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP'>('GBM')
watch(selectedModel, (model) => {
  portfolioStore.fetchForecast(model, horizon)
})
```

---

## Pinia Store Extension Pattern

Add to `portfolio.ts` following Phase-4 `risk`/`correlation` pattern exactly:

```typescript
// New type import
import type { ForecastDto } from '../api/forecast'

// New state
const forecast = asyncState<ForecastDto>(null)

// New fetch action
async function fetchForecast(
    model: 'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP' = 'GBM',
    horizon = 252,
    version?: number
): Promise<void> {
  forecast.loading = true
  forecast.error = null
  try {
    const { data } = await axios.get<ForecastDto>(
      '/api/portfolio/forecast',
      { params: { model, horizon } }
    )
    if (version !== undefined && version !== refreshVersion) return
    forecast.data = data
  } catch (e: any) {
    if (version !== undefined && version !== refreshVersion) return
    forecast.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load forecast'
  } finally {
    forecast.loading = false
  }
}
```

**Add to `refreshAll()`:** Include `fetchForecast('GBM', 252, myVersion)` in the `Promise.allSettled` call. Update `$reset()` to clear `forecast`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| GBM Euler discretization | Manual `S(t+dt) = S(t) * exp(...)` loop with hand-coded Mersenne Twister | `BlackScholesModel` + `EulerSchemeFromProcessModel` + `BrownianMotionFromMersenneRandomNumbers` | Ito correction, variance reduction, multi-factor correlation — all handled; correctness risk in manual implementation |
| Merton jump-diffusion | Manual Poisson jump sampling | `MonteCarloMertonModel` | Compound Poisson process implementation is non-trivial; finmath handles the drift compensation term correctly |
| Heston variance SDE | Manual CIR process discretization | `HestonModel` with `FULL_TRUNCATION` scheme | Variance negativity handling (Feller violation recovery) is non-trivial; the FULL_TRUNCATION scheme is the correct industrial choice |
| Percentile extraction | `DescriptiveStatistics.getPercentile()` in a 5000-element loop | `Arrays.sort` + index arithmetic | Faster (single sort per step vs internal copy per percentile call); more transparent (no hidden interpolation mode) |
| Block bootstrap RNG | Custom PRNG | Hipparchus `MersenneTwister(42)` | Already in pom; cryptographically adequate for simulation; tested |

---

## Common Pitfalls

### Pitfall 1: Missing Ito Correction — Upward Fan-Chart Bias

**What goes wrong:** Using `S(t+dt) = S(t) * exp(μ*dt + σ*√dt*Z)` instead of `exp((μ − σ²/2)*dt + ...)`. The missing `σ²/2` term makes the expected value drift upward at rate `e^{(μ+σ²/2)t}` instead of `e^{μt}`. For σ=20%, the bias is `e^{(0.04/2)·1} ≈ 2%` per year — noticeable in the fan chart median vs the base.

**Why finmath avoids it:** `BlackScholesModel` evolves in log-space internally. The model returns log S(t), and `applyStateSpaceTransform()` applies `exp()`. The drift in log-space is `(μ − σ²/2)*dt`. This is correct by construction. If you hand-roll instead of using finmath, you must include this term.

**Guard test:** Simulate 5000 paths × 1 step × dt=1 year. Verify `mean(S_T)` is within 1% of `S₀ · exp(μ · 1)`. If median > S₀·exp((μ−σ²/2)·1) by more than 1%, Ito correction is missing.

**How to detect early:** All fan chart paths trend upward even for σ=30% (high-vol) portfolio with μ≈0. The median should lie below the base for such a portfolio.

### Pitfall 2: Heston Feller Condition Violation

**What goes wrong:** The variance process `dV = κ(θ−V)dt + ξ√V dW₂` can reach zero or go negative if `2κθ ≤ ξ²`. With `FULL_TRUNCATION` scheme this is handled by clamping `V = max(V, 0)`, but paths that spend many steps at V=0 produce GBM-like segments, undermining the stochastic-vol signal.

**How to avoid:** With the illustrative params `κ=2, θ=0.04, ξ=0.3`: `2×2×0.04 = 0.16 > 0.09 = 0.3²`. Feller is satisfied. Assert this at service startup (see code above). If parameters are ever changed in configuration, the check will catch violations immediately.

**Warning sign:** All Heston fan chart paths look identical to GBM paths — vol-of-vol is suppressed because V is clamped at zero.

### Pitfall 3: commons-math3 Classpath Coexistence

**What goes wrong:** finmath-lib 6.1.7 pulls `commons-math3:3.6.1` as a compile dependency. If Spring Boot BOM were to pin `commons-math3` to a different version, Maven's nearest-wins rule would select the wrong version. However, Spring Boot 3.5.13 BOM does NOT manage `commons-math3`, so the finmath-declared version 3.6.1 wins without conflict.

**What to NOT do:** Do not add `<exclusions>` for `commons-math3` in the finmath dependency declaration. Finmath uses CM3 internally (e.g., for some distributions in MertonModel's Poisson process). Excluding it would cause `NoClassDefFoundError` at runtime.

**Verification:** After adding finmath to pom.xml, run `./mvnw dependency:tree | grep commons-math3` — should show exactly one entry `commons-math3:3.6.1` sourced from finmath.

### Pitfall 4: ECharts Stack Data Format — Absolute vs Difference

**What goes wrong:** Passing absolute percentile values (`p25`, `p75`, `p95`) to stacked series instead of differences. With `stack: 'fan'`, ECharts accumulates values: the rendered upper edge of band N is `sum(series 0..N)`. So band N should contain `percentile[N] - percentile[N-1]`, not the absolute percentile value.

**How to detect:** The rendered fan chart looks much wider than expected, or the bands are visible far above the actual projected portfolio value. Fix: pass differences as shown in the ECharts code example above.

### Pitfall 5: dt Scaling Error

**What goes wrong:** Annualized μ and σ are passed to a model that uses dt=1 (daily step count) instead of dt=1/252. The finmath models use the `TimeDiscretization` internally — as long as the time axis is in units of **years** (which `TimeDiscretizationFromArray(0.0, 252, 1.0/252.0)` ensures), passing annualized params is correct. The Euler scheme scales by `dt = 1/252` per step automatically.

**Verify:** Fan chart width at 1 year should show annualized σ range. For S₀=100, σ=20%, p5 ≈ 100·exp((μ−0.5×σ²)·1 − 1.645·σ·√1) ≈ 100·exp(μ·1 − 0.18). Check this matches the computed p5 band.

### Pitfall 6: Block Bootstrap — Boundary Condition

**What goes wrong:** If `blockStart + L > H` (block extends past the end of history), the bootstrap reads past the array. Fix: use `blockStart = rng.nextInt(H - L + 1)` — maximum start index is `H - L`, ensuring the full block fits within history.

---

## Code Examples

### Complete GBM Setup (production-ready snippet)

```java
// Source: finmath-lib test suite (MonteCarloBlackScholesModelTest.java) + this research

TimeDiscretization td = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);

BrownianMotionFromMersenneRandomNumbers bm =
    new BrownianMotionFromMersenneRandomNumbers(td, 1, NUM_PATHS, MC_SEED);

ProcessModel model = new BlackScholesModel(
    initialValue,
    annualizedMu,
    annualizedSigma,
    new RandomVariableFromArrayFactory()
);

AssetModelMonteCarloSimulationModel sim =
    new MonteCarloAssetModel(new EulerSchemeFromProcessModel(model, bm));

// Collect percentile bands per time step
double[][] pBands = new double[horizonDays][5]; // [step][p5,p25,p50,p75,p95]
for (int t = 1; t <= horizonDays; t++) {
    double[] vals = sim.getAssetValue(t, 0).getRealizations();
    pBands[t - 1] = extractPercentiles(vals);
}
```

### Complete Heston Setup

```java
// Source: finmath-lib HestonModelTest.java + this research (scheme=FULL_TRUNCATION confirmed)

assert 2.0 * HESTON_KAPPA * HESTON_THETA > HESTON_XI * HESTON_XI : "Feller violated";

RandomVariableFromArrayFactory rvf = new RandomVariableFromArrayFactory();
TimeDiscretization td = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);

ProcessModel hestonModel = new HestonModel(
    rvf.createRandomVariable(initialValue),
    rvf.createRandomVariable(annualizedMu),
    rvf.createRandomVariable(Math.sqrt(HESTON_V0)),
    rvf.createRandomVariable(0.0),
    rvf.createRandomVariable(HESTON_THETA),
    rvf.createRandomVariable(HESTON_KAPPA),
    rvf.createRandomVariable(HESTON_XI),
    rvf.createRandomVariable(HESTON_RHO),
    HestonModel.Scheme.FULL_TRUNCATION,
    rvf
);

BrownianMotionFromMersenneRandomNumbers bm =
    new BrownianMotionFromMersenneRandomNumbers(td, 2, NUM_PATHS, MC_SEED);

AssetModelMonteCarloSimulationModel sim =
    new MonteCarloAssetModel(new EulerSchemeFromProcessModel(hestonModel, bm));
```

### Complete Block Bootstrap

```java
// Source: standard bootstrap literature adapted for Java

MersenneTwister rng = new MersenneTwister(MC_SEED);
int H = dailyLogReturns.length;
int L = Math.max(10, (int) Math.sqrt(H));  // block length

double[][] paths = new double[NUM_PATHS][horizonDays + 1];
for (int p = 0; p < NUM_PATHS; p++) {
    paths[p][0] = initialValue;
    double v = initialValue;
    int t = 0;
    while (t < horizonDays) {
        int blockStart = rng.nextInt(H - L + 1);
        for (int i = blockStart; i < blockStart + L && t < horizonDays; i++, t++) {
            v = v * Math.exp(dailyLogReturns[i]);
            paths[p][t + 1] = v;
        }
    }
}

// Extract percentiles per step
double[][] pBands = new double[horizonDays][5];
double[] stepBuffer = new double[NUM_PATHS];
for (int t = 1; t <= horizonDays; t++) {
    for (int p = 0; p < NUM_PATHS; p++) stepBuffer[p] = paths[p][t];
    pBands[t - 1] = extractPercentiles(stepBuffer);
}
```

---

## MODELS.md Outline (SIM-03)

The file should be at `docs/MODELS.md` and linked from README. Target audience: a non-specialist hiring manager who can recognize that the author understands what they built and why. Each model section should be 150-250 words.

### Structure

```
# Stochastic Forecast Models

## Why Stochastic Forecasting?
[2 paragraphs: what Monte Carlo means in plain English; 
 why "potential futures" not "predictions"; fan chart reading guide]

## Model 1: Geometric Brownian Motion (GBM)
### What it is
[The baseline model — portfolio follows a random walk in log-space]
### Why it's included
[Industry standard; transparent assumptions; simplest calibration; good baseline]
### Key assumptions
[Log-normal returns; constant drift μ and volatility σ; continuous time]
### Parameters used
[μ = annualized mean log return from 2-year history; σ = annualized vol from same window]
### Known limitations
[No jumps; constant vol; normal distribution underestimates tail risk; drift estimation is noisy]

## Model 2: Merton Jump-Diffusion
### What it is
[GBM + occasional discrete jumps from a Poisson process]
### Why it's included
[Equity portfolios experience sudden drops (earnings shocks, macro events); 
 GBM misses these; Merton adds left-tail probability]
### Key assumptions
[Jumps arrive at average rate λ per year; each jump is normally distributed in log-space]
### Parameters used
[σ = calibrated; λ=0.10, μ_J=-0.10, σ_J=0.15 — illustrative equity stress values; 
 note: not calibrated to current option prices]
### Known limitations
[Jump parameters are illustrative, not calibrated; calibration requires option surface data 
 (not available in this demo); jump intensity assumed constant]

## Model 3: Heston Stochastic-Volatility
### What it is
[Like GBM but the volatility itself is random — it mean-reverts around a long-run level]
### Why it's included  
[Addresses the "volatility clustering" feature of equity markets — vol is not constant;
 demonstrates awareness of second-generation stochastic-vol models]
### Key assumptions
[Variance follows a CIR (mean-reverting) process; correlation ρ between asset and vol (leverage effect)]
### Parameters used
[κ=2.0 (mean-reversion speed), θ=0.04 (long-run variance = 20%²), ξ=0.3 (vol-of-vol), ρ=-0.7;
 DISCLAIMER: these are illustrative — Heston is calibrated from option prices in practice, 
 which requires live market data this demo does not use]
### Known limitations
[Parameters not calibrated; Feller condition must be maintained (2κθ>ξ²); 
 simulation is more computationally expensive; Euler discretization of Heston 
 has known bias for large ξ]

## Model 4: Historical Block Bootstrap
### What it is
[No distributional assumption — resample the actual observed daily returns in blocks of 
 ~22 trading days to build forward paths]
### Why it's included
[Captures whatever distributional properties the actual portfolio had: fat tails, 
 skewness, volatility clustering — without imposing a parametric model]
### Parameters used
[Block length L ≈ 22 trading days (empirically chosen to preserve monthly autocorrelation);
 all other inputs are the raw historical daily log returns]
### Known limitations
[Only possible futures are combinations of past observations — rare events not yet seen 
 in the 2-year history are invisible; past regime may not represent future regime;
 bootstrap assumes stationarity within the block]

## Appendix: Mathematical Notes
[Ito correction for GBM; Feller condition for Heston; block bootstrap block-length selection;
 note that all models use seed=42 for reproducibility — bands are deterministic 
 given the same portfolio history]
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Hand-rolled Euler-Maruyama for GBM | finmath-lib `BlackScholesModel` + `EulerSchemeFromProcessModel` | Available since finmath 1.x; common practice 2015+ | Correctness guaranteed; Ito-correct by construction |
| Single-model (GBM-only) MC fan chart | Model-switchable fan chart (GBM / Merton / Heston / Bootstrap) | Industry practice since 2000s; common in academic teaching tools | Demonstrates model awareness — the key resume signal |
| 95% confidence band only | p5/p25/p50/p75/p95 five-band fan chart | Became standard in forecasting dashboards ~2015 | More information; median line is explicitly visible; non-specialist-readable |
| BrownianMotionLazyInit | BrownianMotionFromMersenneRandomNumbers | finmath internal evolution | Explicit Mersenne Twister with seeded reproducibility; preferred for demo use cases |

**Deprecated/outdated patterns:**
- `MonteCarloBlackScholesModel(TimeDiscretization, numberOfPaths, initialValue, ...)` with default seed 3141: this constructor exists but uses a hardcoded seed. Use the `BrownianMotion`-based approach for explicit seed control.
- Commons Math 3 `NormalDistribution.sample()` for path generation: use finmath's native Brownian motion instead; CM3 for standalone path generation would require managing time discretization manually.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | commons-math3:3.6.1 pulled by finmath does not conflict with Spring Boot 3.5.13 BOM | Standard Stack / Pitfall 3 | ClassNotFoundException or version conflicts at runtime; fix: check `./mvnw dependency:tree` after adding finmath |
| A2 | GBM simulation timing: ~50–100ms for 5000 paths × 252 steps | Performance | If slower, simulation blocks request thread; fix: add caching TTL |
| A3 | Block length L=22 (approx. sqrt(504)) is appropriate for 2-year equity return history | Block Bootstrap | Too short: autocorrelation not preserved; too long: high variance in bootstrap estimate; risk: LOW — any block in [10,30] is defensible |
| A4 | HestonModel requires `RandomVariable`-typed constructor parameters (not double overloads) | finmath API Reference | API may have a double-based overload; if so, simplify by removing `rvFactory.createRandomVariable()` wrapping |
| A5 | `MonteCarloMertonModel` constructor signature matches the one documented (10-parameter version) | finmath API Reference / Merton | Compile error if signature differs; fix: check GitHub source for 6.1.7 tag |
| A6 | `rvFactory.createRandomVariable(double)` returns a valid `RandomVariable` for use in HestonModel | finmath API Reference / Heston | Null or wrong type at runtime; fix: use `new RandomVariableFromDoubleArray(0.0, value)` as fallback |

**If this table is empty:** All claims in this research were verified or cited. This table is NOT empty: A1–A6 require compile-time verification during Wave 0.

---

## Open Questions

1. **finmath 6.1.7 exact HestonModel constructor parameter order**
   - What we know: constructor takes `(initialValue, riskFreeRate, volatility, discountRate, theta, kappa, xi, rho, Scheme, RandomVariableFactory)` — verified from GitHub source
   - What's unclear: whether the parameter order for `theta`/`kappa` vs `kappa`/`theta` matches the documentation (some finmath versions had these swapped)
   - Recommendation: Write a Wave 0 integration test that verifies `E[variance_t] → theta` as `t → ∞` for the instantiated model. If mean-reversion is going the wrong way, swap kappa/theta.

2. **ForecastController: new controller vs extend AnalyticsController**
   - What we know: `AnalyticsController` handles 4 endpoints under `analytics` module; forecast is closely related
   - What's unclear: whether `/api/portfolio/forecast` belongs in `AnalyticsController` or a new `ForecastController`
   - Recommendation: Create a separate `ForecastController` to keep `AnalyticsController` focused. Both are in `com.quantlens.analytics.api`.

3. **Caching strategy for simulation results**
   - What we know: Fixed seed means results are deterministic per (portfolioId, model, horizon)
   - What's unclear: whether Spring `@Cacheable` (requiring a CacheManager bean) or a simple `ConcurrentHashMap` is preferred
   - Recommendation: Use a private `ConcurrentHashMap<String, ForecastDto>` with a manual TTL check. Avoids Spring cache configuration overhead for a demo-mode project.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | finmath-lib compilation + Spring Boot 3.5.13 | Yes (CLAUDE.md) | 21.0.11 | — |
| Maven Wrapper (`./mvnw`) | Build | Yes (CLAUDE.md) | Spring Initializr generated | — |
| `net.finmath:finmath-lib:6.1.7` | Monte Carlo simulation | NOT YET in pom.xml | 6.1.7 on Maven Central | — (must add) |
| `org.hipparchus:hipparchus-stat:4.0.3` | μ/σ calibration + percentile extraction | Yes (pom.xml confirmed) | 4.0.3 | — |
| `org.hipparchus:hipparchus-core:4.0.3` | MersenneTwister for bootstrap | Yes (pom.xml confirmed) | 4.0.3 | — |
| `echarts:6.1.0` | Fan chart rendering | Yes (package.json confirmed) | 6.1.0 | — |
| `vue-echarts:8.0.1` | Vue 3 ECharts wrapper | Yes (package.json confirmed) | 8.0.1 | — |

**Missing dependencies with no fallback:**
- `net.finmath:finmath-lib:6.1.7` — must be added to `backend/pom.xml` as Wave 0 task before any simulation code can compile.

**Missing dependencies with fallback:**
- None.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 (spring-boot-starter-test) + AssertJ (already in pom) |
| Config file | No separate config — inherits from Spring Boot test infrastructure |
| Quick run command | `./mvnw test -pl backend -Dtest=ForecastMathHandComputedTest -DJAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"` |
| Full suite command | `./mvnw test -pl backend -DJAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| SIM-01 | p5 ≤ p25 ≤ p50 ≤ p75 ≤ p95 at every time step for all 4 models | unit (structural) | `./mvnw test -Dtest=ForecastStructuralTest` | No — Wave 0 |
| SIM-01 | Band width increases with horizon (p95−p5 at t=252 > at t=1) | unit (structural) | `./mvnw test -Dtest=ForecastStructuralTest` | No — Wave 0 |
| SIM-01 | GBM: `E[S_T]` within 1% of `S₀·exp(μ·T)` (analytic mean sanity / Ito guard) | unit (hand-computed anchor) | `./mvnw test -Dtest=ForecastMathHandComputedTest` | No — Wave 0 |
| SIM-01 | GBM: two runs with seed=42 produce byte-identical p5/p25/p50/p75/p95 arrays | unit (reproducibility) | `./mvnw test -Dtest=ForecastStructuralTest` | No — Wave 0 |
| SIM-01 | Bootstrap: output mean log return within 5% of historical mean (moment preservation) | unit (structural) | `./mvnw test -Dtest=ForecastStructuralTest` | No — Wave 0 |
| SIM-01 | finmath model instantiation and path generation runs without exception | unit (integration-lite) | `./mvnw test -Dtest=ForecastFinmathIntegrationTest` | No — Wave 0 |
| SIM-02 | `GET /api/portfolio/forecast?model=GBM` returns 200 with correct JSON shape | integration (Testcontainers) | `./mvnw test -Dtest=ForecastControllerIntegrationTest` | No — Wave 0 |
| SIM-02 | Switching model (HESTON, JUMP_DIFFUSION, BOOTSTRAP) returns distinct band shapes | integration | `./mvnw test -Dtest=ForecastControllerIntegrationTest` | No — Wave 0 |
| SIM-01 | Frontend: fan chart renders 5 series; model selector changes `selectedModel` | component (Vitest) | `npm run test -- components/MonteCarloFanChart` | No — Wave 0 |

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=ForecastMathHandComputedTest,ForecastStructuralTest`
- **Per wave merge:** `./mvnw test -pl backend` (full backend test suite)
- **Phase gate:** Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `backend/src/test/.../analytics/ForecastMathHandComputedTest.java` — hand-computed GBM analytic mean anchor (HC-11: no circular validation)
- [ ] `backend/src/test/.../analytics/ForecastStructuralTest.java` — structural tests (ordering, widening, reproducibility, bootstrap moments)
- [ ] `backend/src/test/.../analytics/ForecastFinmathIntegrationTest.java` — finmath instantiation smoke test (no Spring context; just model + path generation)
- [ ] `backend/src/test/.../analytics/ForecastControllerIntegrationTest.java` — extends `AbstractPostgresIntegrationTest`; tests all 4 model endpoints
- [ ] `frontend/src/__tests__/components/MonteCarloFanChart.test.ts` — Vitest component test
- [ ] `net.finmath:finmath-lib:6.1.7` added to `backend/pom.xml` — blocks all backend compilation

### Hand-Computed GBM Analytic-Mean Anchor (HC-11)

```
Given: S₀ = 100.0, μ = 0.10 (annualized), σ = 0.20 (annualized), T = 1 year
Derived externally (NOT from the implementation):

GBM analytic mean:
  E[S_T] = S₀ · exp(μ · T) = 100 · exp(0.10) = 100 · 1.10517 = 110.517

GBM log-normal median (Ito-correct):
  median[S_T] = S₀ · exp((μ − σ²/2) · T)
              = 100 · exp(0.10 − 0.02)
              = 100 · exp(0.08)
              = 100 · 1.08329 = 108.329

Upward-bias check: if median > 110.517, Ito correction is MISSING (drift used μ not μ−σ²/2).

Test assertion: mean(paths[:, T=252]) ∈ [110.517 × 0.99, 110.517 × 1.01]  (within 1%)
Test assertion: median(paths[:, T=252]) ≈ 108.329 ± 0.5%
Test assertion: median < mean  (always true for log-normal, guards against sign flip)

This is HC-11 — derived independently, NOT from running ForecastService.
```

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | Yes | Same `@Transactional(readOnly=true)` + `resolvePortfolioId` principal scoping as Phase 4 |
| V3 Session Management | No (inherited from existing session security) | — |
| V4 Access Control | Yes | Portfolio scoping: user can only forecast their own portfolio |
| V5 Input Validation | Yes | `model` param: enum validation (reject unknown values with 400); `horizon`: clamp to [1, 504] to prevent unbounded simulation |
| V6 Cryptography | No | — |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unvalidated `model` enum parameter | Tampering | Validate against `ModelType.values()` enum; return `400 Bad Request` for unknown values |
| Unbounded `horizon` parameter causing CPU exhaustion | Denial of Service | Clamp horizon to [1, 504] (2 years max); log attempts to exceed |
| Portfolio ID injection (requesting another user's forecast) | Information Disclosure | `resolvePortfolioId(authentication)` from `AnalyticsController` pattern; never accept portfolioId from query param |

---

## Sources

### Primary (HIGH confidence)

- [finmath-lib GitHub source — MonteCarloBlackScholesModelTest.java](https://github.com/finmath/finmath-lib/blob/master/src/test/java/net/finmath/montecarlo/assetderivativevaluation/MonteCarloBlackScholesModelTest.java) — confirmed API: `BrownianMotionFromMersenneRandomNumbers`, `EulerSchemeFromProcessModel`, `MonteCarloAssetModel`
- [finmath-lib GitHub source — HestonModelTest.java](https://github.com/finmath/finmath-lib/blob/master/src/test/java/net/finmath/montecarlo/assetderivativevaluation/HestonModelTest.java) — confirmed: `HestonModel.Scheme.FULL_TRUNCATION`, 2-factor Brownian, `MonteCarloAssetModel`
- [finmath-lib GitHub source — HestonModel.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/assetderivativevaluation/models/HestonModel.java) — confirmed constructor signature and `Scheme` enum
- [finmath-lib GitHub source — MertonModel.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/assetderivativevaluation/models/MertonModel.java) — confirmed jump parameter handling
- [finmath-lib GitHub source — MonteCarloMertonModel.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/assetderivativevaluation/MonteCarloMertonModel.java) — confirmed constructor with seed + jump params
- [finmath-lib GitHub source — BrownianMotionFromMersenneRandomNumbers.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/BrownianMotionFromMersenneRandomNumbers.java) — confirmed `seed` parameter in constructor
- [finmath-lib GitHub source — RandomVariable.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/stochastic/RandomVariable.java) — confirmed `double[] getRealizations()`
- [finmath-lib GitHub source — TimeDiscretizationFromArray.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/time/TimeDiscretizationFromArray.java) — confirmed `(0.0, 252, 1.0/252.0)` constructor
- [finmath-lib GitHub source — BlackScholesModel.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/assetderivativevaluation/models/BlackScholesModel.java) — confirmed double-based constructor
- [finmath-lib GitHub source — MonteCarloAssetModel.java](https://github.com/finmath/finmath-lib/blob/master/src/main/java/net/finmath/montecarlo/assetderivativevaluation/MonteCarloAssetModel.java) — confirmed `getAssetValue(int timeIndex, int assetIndex)` returns `RandomVariable`
- [Maven Central — net.finmath:finmath-lib:6.1.7](https://central.sonatype.com/artifact/net.finmath/finmath-lib/6.1.7) — version confirmed, transitive deps: commons-math3:3.6.1, commons-lang3:3.18.0, jblas:1.2.4
- [finmath.net dependency page](https://www.finmath.net/finmath-lib/dependencies.html) — compile deps confirmed
- [Phase 4 RESEARCH.md + RiskCalculator.java](backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java) — confirmed `logReturns()` static helper, `buildEquityCurveLocal()` package-accessible method
- [style.css lines 65-68](frontend/src/style.css) — confirmed fan-chart CSS tokens: `--color-fan-p50`, `--color-fan-band-1`, `--color-fan-band-2`
- [PITFALLS.md Pitfall 5 + 6](../.planning/research/PITFALLS.md) — Ito correction requirement and Heston instability documented

### Secondary (MEDIUM confidence)

- [Apache ECharts Handbook — Stacked Area Chart](https://apache.github.io/echarts-handbook/en/how-to/chart-types/line/stacked-line/) — confirmed `stack` property and `areaStyle` for area accumulation
- [ECharts confidence band community examples](https://gist.github.com/helgasoft/b74c4f5c0532c83a8c0bbf571ae8cd02) — confirmed band-difference data format for stacked fan charts
- [CONTEXT.md locked decisions](05-CONTEXT.md) — Heston FULL_TRUNCATION, seed=42, 5000 paths, illustrative params confirmed as project requirements

### Tertiary (LOW confidence — flagged in Assumptions Log)

- Block length L≈sqrt(H) rule: widely cited in bootstrap literature (e.g., Politis & Romano 1994) but specific value is author judgment; any L in [10, 30] is defensible
- Performance timing estimates (50–250ms): based on typical Java JVM Monte Carlo benchmarks for path counts in the 5000 range; not measured on this specific machine

---

## Metadata

**Confidence breakdown:**
- finmath-lib API: HIGH — verified from GitHub source files and test suite
- Ito correction and GBM math: HIGH — standard stochastic calculus result
- Heston Feller condition and illustrative parameters: HIGH — well-documented in literature (PITFALLS.md cites arXiv paper)
- ECharts fan-chart stacking: MEDIUM — confirmed from handbook + community examples; band-difference trick is the standard technique
- Performance timing: LOW — estimated, not measured
- Block bootstrap block length: MEDIUM — standard rule of thumb, not analytically derived for this specific dataset

**Research date:** 2026-06-08
**Valid until:** 2026-07-08 for finmath API (stable library; API changes between minor versions are infrequent); 2026-06-22 for ECharts technique (stable API, v6 is mature)
