# Phase 5: Stochastic Forecasting - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Deliver forward-looking Monte Carlo forecasting of portfolio value across four switchable stochastic models (GBM, Merton jump-diffusion, Heston, historical block bootstrap), rendered as a percentile fan chart, plus the documented rationale for each model. Full-stack: backend Monte Carlo engine (finmath-lib) + REST endpoint + golden/structural tests, frontend fan chart + model selector filling the Phase-3 Monte Carlo SlotPlaceholder, and a non-specialist-readable models rationale doc (SIM-03 — explicitly requested by the user).

Requirements covered: SIM-01, SIM-02, SIM-03.
</domain>

<decisions>
## Implementation Decisions

### Models & library
- Add **finmath-lib 6.1.7** to backend pom (`net.finmath:finmath-lib`). Use its Monte Carlo SDE processes where available: GBM (BlackScholesModel / MonteCarloBlackScholesModel), Merton jump-diffusion (MertonModel), Heston (HestonModel). Implement **historical block bootstrap** directly (block-resample historical daily log returns to preserve autocorrelation — document why blocks, not i.i.d.).
- Numeric: simulation in `double` (finmath/Hipparchus); final percentile band values returned as `double` (chart) or `BigDecimal` for the money axis — chart bands as numbers is acceptable. Money base = current portfolio value (BigDecimal → double for the projection base).

### Forecast model
- Project **portfolio value** forward over a forward horizon (default 252 trading days ≈ 1 year; configurable). Base = current total portfolio market value.
- Calibrate drift μ and volatility σ from the portfolio's historical daily **log returns** (reuse Phase 2/4 return logic), annualized. GBM uses (μ, σ). Jump-diffusion adds illustrative λ (jump intensity), μ_J, σ_J. Heston uses fixed **illustrative** params κ (mean-reversion), θ (long-run var), ξ (vol-of-vol), ρ (correlation) with the **Feller condition** (2κθ > ξ²) enforced + a UI/doc disclaimer that they're illustrative (not calibrated). Bootstrap resamples historical return blocks.
- **5000 paths** (sufficient for smooth p5–p95 bands at 60fps; bounded for demo speed). **Fixed RNG seed** (e.g. 42) so the fan chart is reproducible for README screenshots.
- Output: per-horizon-step percentile bands p5/p25/p50/p75/p95 of projected portfolio value → a time series the fan chart consumes.

### Surface (REST + frontend)
- Backend endpoint in `com.quantlens.analytics` (or a `forecast` sub-package): `GET /api/portfolio/forecast?model={GBM|JUMP_DIFFUSION|HESTON|BOOTSTRAP}&horizon={days}` — principal-scoped, `@Transactional(readOnly=true)`, returns `{ model, horizonDays, dates|steps, p5[], p25[], p50[], p75[], p95[] }` (chart-ready).
- Frontend: `MonteCarloFanChart.vue` (ECharts stacked/area bands p5–p95 + p25–p75 + median line, dark theme per UI-SPEC fan-chart token conventions reserved in Phase 3) + a model selector (4 options). Fills the Phase-3 "Monte Carlo — Phase 5" SlotPlaceholder. New Pinia store state `forecast` (with selected model) via the asyncState factory; switching model re-fetches.

### SIM-03 documentation
- `docs/MODELS.md` (linked from README) — for each of the 4 models: what it is, why it's included, key assumptions, the parameters used (and that Heston's are illustrative), and known limitations — written for a non-specialist hiring manager. This is the resume talking-point doc the user explicitly asked for ("documentation on why I chose certain stochastic logic and rationales").

### Testing
- Structural/golden tests: percentile ordering (p5≤p25≤p50≤p75≤p95 at every step); bands widen with horizon; GBM median ≈ base × exp((μ−σ²/2)·t) sanity (Ito-correct drift — guard against the upward-bias pitfall); reproducibility under the fixed seed (two runs identical); bootstrap output mean/vol ≈ historical moments. finmath integration test (model instantiation + path generation). Frontend component test (fan chart renders bands + selector switches model). Hand-computed GBM analytic-mean check to anchor correctness (avoid circular validation).

### Claude's Discretion
- Exact finmath API usage per model, path count tuning, horizon default, whether forecast lives in analytics vs a new module, percentile interpolation method, and ECharts band rendering technique — at Claude's discretion within the above. Research to confirm finmath 6.1.7 APIs + the Ito-correct GBM discretization.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Backend: analytics module (RiskCalculator with equity-curve + log-return helpers; reuse for μ/σ calibration), portfolio repos, AbstractPostgresIntegrationTest. Hipparchus 4.0.3 in pom (stats/RNG: MersenneTwister). finmath NOT yet in pom — add it.
- Frontend: ECharts plugin + quantlens-dark theme (UI-SPEC reserved fan-chart percentile colors), AllocationChart/PnlChart scaffolds (band/area patterns), portfolio store asyncState factory + refreshAll, DashboardView with the "Monte Carlo — Phase 5" SlotPlaceholder, format util.

### Established Patterns
- double stats / BigDecimal money; principal-scoped readOnly endpoints (copy resolvePortfolioId); Spring Modulith boundaries; golden + hand-computed tests; ECharts components bind to store async state (no theme prop; global THEME_KEY); fixed-seed determinism for reproducible demo output.

### Integration Points
- New forecast endpoint consumed by MonteCarloFanChart replacing the Phase-3 MC slot. Reuse portfolio return/equity-curve calibration. finmath added to pom (verify it coexists with Hipparchus/Spring Boot 3.5.13 on Java 21).
</code_context>

<specifics>
## Specific Ideas

- Ito-correct GBM drift (use (μ − σ²/2) in the exponent) — the classic fan-chart upward-bias bug; guard with a test.
- Reproducible fan charts (fixed seed) so README screenshots are stable.
- Heston params are illustrative (not calibrated) — say so in the UI and docs to stay honest.
- The MODELS.md rationale doc is a deliberate resume artifact — make it genuinely good.
</specifics>

<deferred>
## Deferred Ideas

- Live Heston calibration from real return data (v2 — SIM-04).
- Additional models (SABR, variance gamma) — v2.
- AI narration of the forecast (Phase 6+).
- Backtesting (out of scope).
</deferred>
