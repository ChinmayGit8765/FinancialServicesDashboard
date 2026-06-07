# Phase 4: Quant Risk Engine - Context

**Gathered:** 2026-06-07
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Deliver the quantitative risk engine — the credibility core of the project — as correct, golden-value-tested computations exposed via REST and rendered in the dashboard slots Phase 3 reserved. Covers the risk scorecard (Sharpe, annualized volatility, max drawdown, beta, 95% VaR), VaR by two methods (parametric + historical) side by side, a pairwise return-correlation heatmap, Fama-French 3-factor attribution, and a cointegration-based pairs scanner.

**Full-stack:** backend math (Hipparchus) + REST endpoints + golden-value unit tests, AND the frontend panels filling the Phase 3 SlotPlaceholders (risk scorecard, correlation heatmap, attribution chart, pairs table). The math MUST be correct and test-proven before the AI layer (Phase 6+) narrates it.

Requirements covered: RISK-01, RISK-02, RISK-03, ATTR-01, ARB-01.
</domain>

<decisions>
## Implementation Decisions

### Libraries & numeric conventions
- Add **Hipparchus 4.0.3** (`hipparchus-core`, `hipparchus-stat`) to backend pom — `OLSMultipleLinearRegression`, `Covariance`, `PearsonsCorrelation`, `DescriptiveStatistics`, `NormalDistribution`, `TDistribution`. (finmath-lib is reserved for Phase 5 Monte Carlo — not needed here.)
- **Numeric convention:** statistical quantities (returns, Sharpe, vol, beta, correlation, betas, p-values, z-scores) are computed/returned as `double` (Hipparchus is double-based; ratios don't need BigDecimal). Monetary values (VaR in currency, market values) remain `BigDecimal`. Document this split.

### Risk metrics (RISK-01)
- **Returns:** daily **log returns** `ln(P_t / P_{t-1})` for Sharpe and volatility (consistent, additive). Portfolio return series from the constant-current-holdings equity curve (reuse Phase 2 `buildEquityCurve`).
- **Sharpe:** `mean(daily excess log return) / std(daily log return) × √252`. Risk-free: use the seeded Fama-French RF if present; otherwise rf = 0 (documented). Annualize with 252.
- **Annualized volatility:** `std(daily log returns) × √252`.
- **Max drawdown:** largest peak-to-trough decline on the equity curve (as a negative %).
- **Beta:** `cov(portfolio returns, benchmark returns) / var(benchmark returns)` (Hipparchus Covariance) vs the SPX500 proxy.
- **95% VaR:** computed both ways (RISK-03) — see below.

### VaR — two methods side by side (RISK-03)
- **Historical VaR (95%):** 5th percentile of the portfolio daily return distribution × current portfolio value (`DescriptiveStatistics.getPercentile(5)`).
- **Parametric (Gaussian) VaR (95%):** `z(0.95) × σ_daily × value` where `z=1.645` (`NormalDistribution.inverseCumulativeProbability`).
- Return both with explicit `method`, `confidence` (0.95), and `horizon` (1-day) labels; optionally CVaR/ES as a bonus column.

### Correlation heatmap (RISK-02)
- Pairwise **Pearson** correlation across holdings' daily return series (`PearsonsCorrelation` on the aligned return matrix). Return a labelled matrix (tickers × tickers, values −1..+1) for an ECharts heatmap (color scale blue −1 → red +1).

### Fama-French 3-factor attribution (ATTR-01)
- OLS regression of portfolio **excess** returns on the seeded factor series (Mkt-RF, SMB, HML) via `OLSMultipleLinearRegression`. Output: alpha (intercept, annualized), β_mkt, β_smb, β_hml, R², and per-factor return **contributions** (β × factor mean return) as a bar chart. Document the regression spec.

### Cointegration pairs scanner (ARB-01)
- **Engle-Granger 2-step** over candidate pairs from the seeded universe: (1) OLS hedge ratio `y = α + β·x + ε`; (2) **ADF test on the residual spread** to test stationarity. ADF is hand-assembled from Hipparchus OLS + t-statistic with the **MacKinnon (1994/2010) critical values / p-value approximation** (verify the constants in research). For each flagged pair return: pair (tickers), cointegration p-value, hedge ratio β, current spread **Z-score** ((spread − mean)/std), and a mean-reversion **signal** (long/short/neutral by Z thresholds, e.g. |Z|>2). Scope to a bounded candidate set (e.g. same-sector pairs) to keep it demo-fast.

### Surface (REST + frontend)
- Backend endpoints (session/principal-scoped, `@Transactional(readOnly=true)`, in `com.quantlens.analytics` Modulith module): `GET /api/portfolio/risk` (scorecard + both VaRs), `GET /api/portfolio/correlation` (matrix), `GET /api/portfolio/attribution` (FF), `GET /api/portfolio/pairs` (cointegration scanner). Reuse the Phase 2 principal-resolution + equity-curve/return helpers (may need to expose a shared returns helper).
- Frontend: components filling the Phase 3 slots — `RiskScorecard.vue` (KPI cards incl. the two slot KPIs), `CorrelationHeatmap.vue` (ECharts heatmap), `AttributionChart.vue` (bar), `PairsTable.vue` — wired through the Pinia portfolio store; dark theme + states per UI-SPEC.

### Testing
- **Golden-value unit tests** for every metric (Sharpe, vol, drawdown, beta, both VaRs, correlation, FF betas/alpha, ADF statistic/p-value, Z-score) computed against the deterministic seed (seed=42) with documented expected values and tolerances. Plus endpoint integration tests (Testcontainers) and frontend Vitest/component tests for the new panels. This is the phase's defining quality gate.

### Claude's Discretion
- Whether to add a new `analytics` Modulith module vs extend `portfolio`; exact endpoint grouping; candidate-pair selection heuristic; CVaR inclusion; precise tolerance values — at Claude's discretion within the above. Research to confirm ADF critical-value constants and the FF regression details.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets (Phases 1–3)
- Backend: `Security`(sector,isBenchmark), `OhlcvBar`(getClosePrice), `FactorReturn`(Mkt-RF/SMB/HML), portfolio repos; `PortfolioService` with `buildEquityCurve` (constant-holdings), latest-close helper, principal resolution; N+1-safe queries. `AbstractPostgresIntegrationTest` (Testcontainers).
- Frontend: Pinia portfolio store, ECharts plugin + `quantlens-dark` theme + `CHART_COLORS`, SlotPlaceholder, KpiCard, SignedValue, format util, DashboardView grid with labelled Phase-4 slots (risk scorecard, correlation heatmap). UI-SPEC heatmap color-scale + fan-chart conventions.

### Established Patterns
- BigDecimal money / double stats; Spring Modulith boundaries (cross-module via named interfaces); @Transactional(readOnly=true) read endpoints; golden-value testing against seed=42; Vue `<script setup>` + scoped CSS tokens; chart components bind to store async state.

### Integration Points
- New analytics endpoints consumed by new dashboard panels that replace the Phase-3 SlotPlaceholders. Reuse the seeded factor series + equity curve. May need to extract a shared daily-return helper from PortfolioService into the analytics module / a named interface.
</code_context>

<specifics>
## Specific Ideas

- Correctness is the whole point — every metric needs a golden-value test with a hand-derivable or documented expected value. Watch the classic traps: annualization factor (252 not 365), log vs simple returns, covariance vs correlation, ADF on the spread residual (NOT raw prices), Sharpe serial-correlation.
- Keep the pairs scanner bounded (candidate set) so the demo stays fast and the ADF stays stable.
- Frontend panels must slot into the existing grid without restructuring (Phase 3 reserved them).
</specifics>

<deferred>
## Deferred Ideas

- Monte Carlo stochastic forecasting + model selector (Phase 5).
- 5-factor Fama-French, additional arbitrage detectors (v2).
- AI narration of these metrics (Phase 6+).
- Live Heston calibration (v2).
</deferred>
