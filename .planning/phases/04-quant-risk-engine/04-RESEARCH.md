# Phase 4: Quant Risk Engine — Research

**Researched:** 2026-06-08
**Domain:** Quantitative finance math (Hipparchus 4.0.3 Java APIs), Spring Modulith analytics module, Vue 3 ECharts panels
**Confidence:** HIGH for all metric formulas and Hipparchus APIs; MEDIUM for MacKinnon ADF p-value constants (correct from statsmodels source but not MacKinnon's original 1996 paper directly); HIGH for frontend ECharts heatmap pattern.

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

**Libraries & numeric conventions**
- Hipparchus 4.0.3 (`hipparchus-core`, `hipparchus-stat`) already in `pom.xml` — confirmed present.
- APIs to use: `OLSMultipleLinearRegression`, `Covariance`, `PearsonsCorrelation`, `DescriptiveStatistics`, `NormalDistribution`, `TDistribution`.
- `finmath-lib` reserved for Phase 5 Monte Carlo — NOT needed here.
- Statistical quantities (returns, Sharpe, vol, beta, correlation, betas, p-values, z-scores) as `double`. Monetary values (VaR in currency) as `BigDecimal`. Document the split.

**Returns convention (RISK-01)**
- Daily **log returns** `ln(P_t / P_{t-1})` for Sharpe and volatility.
- Portfolio return series from the constant-current-holdings equity curve (reuse Phase 2 `buildEquityCurve`).
- Sharpe: `mean(daily excess log return) / std(daily log return) × √252`. RF from seeded FactorReturn if present; otherwise rf = 0 (documented). Annualize with 252.
- Annualized vol: `std(daily log returns) × √252`.
- Max drawdown: largest peak-to-trough decline on equity curve (negative %).
- Beta: `cov(portfolio returns, benchmark returns) / var(benchmark returns)` (Hipparchus Covariance).

**VaR — two methods side by side (RISK-03)**
- Historical VaR (95%): `DescriptiveStatistics.getPercentile(5)` × current portfolio value.
- Parametric Gaussian VaR (95%): `z(0.95) × σ_daily × value` where `z = 1.645` (`NormalDistribution.inverseCumulativeProbability`).
- Return both with `method`, `confidence` (0.95), `horizon` (1-day) labels.

**Correlation heatmap (RISK-02)**
- Pairwise **Pearson** correlation across holdings' daily return series (`PearsonsCorrelation`).
- Return labelled matrix (tickers × tickers, −1..+1) for ECharts heatmap.

**Fama-French 3-factor attribution (ATTR-01)**
- OLS regression of portfolio **excess** returns on seeded factor series (Mkt-RF, SMB, HML) via `OLSMultipleLinearRegression`.
- Output: alpha (intercept, annualized), β_mkt, β_smb, β_hml, R², per-factor contributions (β × mean(factor)).

**Cointegration pairs scanner (ARB-01)**
- Engle-Granger 2-step: OLS hedge ratio, then ADF on residual spread.
- ADF from Hipparchus OLS + t-statistic + MacKinnon (1994/2010) critical values.
- Per pair: tickers, cointegration p-value, hedge ratio β, current spread Z-score, signal.
- Bounded candidate set (same-sector pairs).

**Backend surface**
- `com.quantlens.analytics` Modulith module (at Claude's discretion — confirmed as separate module).
- Endpoints: `GET /api/portfolio/risk`, `GET /api/portfolio/correlation`, `GET /api/portfolio/attribution`, `GET /api/portfolio/pairs`.
- `@Transactional(readOnly=true)`, session/principal-scoped.

**Frontend**
- `RiskScorecard.vue`, `CorrelationHeatmap.vue`, `AttributionChart.vue`, `PairsTable.vue` filling Phase 3 `SlotPlaceholder` slots.
- Wired through Pinia portfolio store; dark theme + states per UI-SPEC.

**Testing**
- Golden-value unit tests for every metric against seed=42.
- Endpoint integration tests (Testcontainers).
- Frontend Vitest/component tests.

### Claude's Discretion
- Whether `analytics` module is new vs extends `portfolio` → **create new `com.quantlens.analytics` module** (see Architecture section).
- Exact endpoint grouping.
- Candidate-pair selection heuristic.
- CVaR inclusion.
- Precise tolerance values.
- Research to confirm ADF critical-value constants and FF regression details.

### Deferred Ideas (OUT OF SCOPE)
- Monte Carlo stochastic forecasting + model selector (Phase 5).
- 5-factor Fama-French, additional arbitrage detectors (v2).
- AI narration of these metrics (Phase 6+).
- Live Heston calibration (v2).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| RISK-01 | Risk scorecard: Sharpe ratio, annualized vol, max drawdown, beta, 95% VaR | Sharpe/vol/drawdown/beta formulas section; Hipparchus API detail; golden-value derivation guide |
| RISK-02 | Pairwise return-correlation heatmap across holdings | PearsonsCorrelation section; ECharts heatmap option shape |
| RISK-03 | VaR by both parametric and historical methods side by side | VaR two-method section; sign convention; DescriptiveStatistics.getPercentile(5) detail |
| ATTR-01 | Fama-French 3-factor attribution: alpha + factor betas as contribution chart | FF regression spec; newSampleData layout; alpha annualization; seeded factor alignment |
| ARB-01 | Cointegration pairs scanner: p-value, spread Z-score, mean-reversion signal | Engle-Granger 2-step; ADF construction; MacKinnon critical values; Z-score and signal thresholds |
</phase_requirements>

---

## Summary

Phase 4 is the credibility core: every number displayed must be mathematically correct and reproducible from the fixed seed. The math is non-trivial in three areas — (1) the Fama-French regression alignment between portfolio returns and seeded factor series, (2) the ADF test construction which Hipparchus does not provide pre-built (must hand-roll from OLS primitives), and (3) getting VaR sign conventions right. All other metrics (Sharpe, vol, drawdown, beta, correlation) are straightforward Hipparchus calls once the return series is computed correctly.

The seed is fully deterministic (MersenneTwister(42), SERIES_START=2022-09-12). All 504 factor-return rows are already persisted with dates aligned to the same trading calendar as OHLCV bars. This means the FF regression date-alignment problem is trivially solved: both portfolio returns and factor returns share the same 504-day date set, and the daily RF rate is stored in `FactorReturn.rf`.

The decisive architectural recommendation is to create a new `com.quantlens.analytics` Spring Modulith module, expose a shared `DailyReturnPort` named interface from it (or from `portfolio::service`), and have the analytics service read from `marketdata::domain` and `portfolio::domain` named interfaces. This avoids bloating `PortfolioService` with 600+ lines of statistical code.

**Primary recommendation:** Build `RiskCalculator`, `FamaFrenchCalculator`, and `CointegrationScanner` as Spring beans in `com.quantlens.analytics.service`, tested in isolation as pure functions, then wire to four thin controller endpoints. Use the `@Disabled` golden-value printer pattern from Phase 2 to derive expected values before writing assertions.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Log-return computation | API / Backend (`com.quantlens.analytics`) | Database (OhlcvBar reads) | Stateless math over BigDecimal → double; computed server-side |
| Risk scorecard metrics | API / Backend (`RiskCalculator`) | — | Pure statistical functions; no client-side computation |
| VaR (historical + parametric) | API / Backend (`RiskCalculator`) | — | Needs full return distribution; not a client concern |
| Correlation matrix | API / Backend (`CorrelationCalculator`) | — | Matrix computation; client receives pre-computed NxN matrix |
| FF 3-factor regression | API / Backend (`FamaFrenchCalculator`) | Database (FactorReturn reads) | OLS regression on paired time series; factor data in DB |
| ADF cointegration test | API / Backend (`CointegrationScanner`) | — | Hand-rolled ADF statistic from Hipparchus OLS; CPU-bound but fast for small candidate sets |
| Risk scorecard display | Frontend / Browser (`RiskScorecard.vue`) | — | KPI cards + VaR comparison table; binds to store |
| Correlation heatmap render | Frontend / Browser (`CorrelationHeatmap.vue`) | — | ECharts heatmap with visualMap −1..+1 |
| Attribution bar chart | Frontend / Browser (`AttributionChart.vue`) | — | ECharts bar; contributions as stacked or grouped bars |
| Pairs table | Frontend / Browser (`PairsTable.vue`) | — | Simple HTML table with signal badge |
| State management | Frontend / Browser (Pinia `portfolioStore` extension) | — | Four new actions follow established AsyncState pattern |

---

## Standard Stack

No new Maven or npm packages are needed. All required Hipparchus classes are in `hipparchus-core:4.0.3` and `hipparchus-stat:4.0.3`, already in `pom.xml`. [VERIFIED: codebase pom.xml confirms both artifacts at 4.0.3]

### Core (already in pom.xml — no additions needed)

| Library | Version | Module | Purpose |
|---------|---------|--------|---------|
| `org.hipparchus:hipparchus-core` | 4.0.3 | `backend` | `NormalDistribution`, `TDistribution`, `MersenneTwister` |
| `org.hipparchus:hipparchus-stat` | 4.0.3 | `backend` | `OLSMultipleLinearRegression`, `Covariance`, `PearsonsCorrelation`, `DescriptiveStatistics` |
| Spring Boot 3.5.13 / Spring Data JPA | Boot-managed | `backend` | REST, repositories |
| `echarts` | 6.1.0 | `frontend` | ECharts heatmap, bar chart [VERIFIED: npm view confirmed 8.0.1 vue-echarts / 6.1.0 echarts] |
| `vue-echarts` | 8.0.1 | `frontend` | Vue 3 component wrapper |

**No `pom.xml` changes required for Phase 4.**

---

## Package Legitimacy Audit

No new packages are installed in this phase. All computation uses Hipparchus 4.0.3 (already in pom.xml from Phase 1) and frontend packages already installed.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Hipparchus 4.0.3 — Exact API Reference

This section documents the precise API calls to use. All are confirmed from the Hipparchus official Javadocs. [CITED: https://hipparchus.org/apidocs/]

### `OLSMultipleLinearRegression` — package `org.hipparchus.stat.regression`

```java
// Two overloads for loading data:

// (A) Separate arrays — preferred for clarity
void newSampleData(double[] y, double[][] x)
// y: dependent variable observations (length n)
// x: independent variable matrix (n rows × k columns) — DO NOT include intercept column
//    Hipparchus adds the intercept automatically (noIntercept defaults to false)

// (B) Flat array — flattened row-major [y0, x0_1, x0_2, ..., y1, x1_1, x1_2, ...]
void newSampleData(double[] data, int nobs, int nvars)

// Results:
double[] estimateRegressionParameters()
// Returns: [intercept, b1, b2, ..., bk] — intercept is index 0

double[] estimateRegressionParametersStandardErrors()
// Returns: [se_intercept, se_b1, se_b2, ..., se_bk]

double calculateRSquared()
// Returns R² = 1 − SSR/SSTO; NaN if variance of y is zero

double calculateAdjustedRSquared()
// Returns adjusted R² = 1 − (1−R²)(n−1)/(n−k−1)

double[] estimateResiduals()
// Returns: residuals ŷ_i − y_i (useful for cointegration Step 1)
```

**Critical note — intercept handling:** `noIntercept` defaults to `false`, meaning the regression ALWAYS includes an intercept column. When calling `newSampleData(double[] y, double[][] x)`, the `x` matrix must contain ONLY the factor columns (k columns), NOT an additional ones-column. Hipparchus prepends the ones-column internally. If you accidentally add an intercept column manually, the regression will be rank-deficient and produce NaN.

**Overload disambiguation:** The flat `newSampleData(double[] data, int nobs, int nvars)` lays out data as `[y_0, x_{0,1}, x_{0,2}, ..., x_{0,k}, y_1, x_{1,1}, ...]`. The `nvars` parameter counts the k regressors (excluding y), matching the external column count before Hipparchus adds the intercept.

### `DescriptiveStatistics` — package `org.hipparchus.stat.descriptive`

```java
DescriptiveStatistics stats = new DescriptiveStatistics();
for (double r : returns) stats.addValue(r);
// or: new DescriptiveStatistics(double[] values)

double getPercentile(double p)   // p in [0,100]; getPercentile(5) = 5th percentile
double getMean()
double getStandardDeviation()    // SAMPLE std dev (divides by n−1) — correct for Sharpe
double getVariance()             // SAMPLE variance
```

**getPercentile behavior:** Uses NIST Sample Percentile definition (type 6 in R terminology). For VaR: `getPercentile(5)` returns the 5th percentile of daily returns — a negative number for a typical equity portfolio. Verify sign is negative before multiplying by portfolio value to get a positive VaR loss number.

### `PearsonsCorrelation` — package `org.hipparchus.stat.correlation`

```java
// Pass the n×k return matrix (n observations, k securities as columns)
PearsonsCorrelation pc = new PearsonsCorrelation(double[][] data);
RealMatrix corrMatrix = pc.getCorrelationMatrix();
// corrMatrix.getEntry(i, j) = Pearson correlation between column i and column j
// Diagonal = 1.0; symmetric
```

**Layout:** Each column is one security's return series. Row i is trading day i. All series must be the same length (use only dates where ALL securities have returns — handled by `buildEquityCurve`'s intersection logic).

### `Covariance` — package `org.hipparchus.stat.correlation`

```java
// For beta: need pairwise covariance between portfolio and benchmark returns
Covariance cov = new Covariance();
double pairCov = cov.covariance(double[] portfolioReturns, double[] benchmarkReturns);
// Note: Covariance.covariance() returns SAMPLE covariance (divides by n−1) — correct

// For benchmark variance:
DescriptiveStatistics bStats = new DescriptiveStatistics(benchmarkReturns);
double benchmarkVar = bStats.getVariance(); // sample variance, consistent with pairCov
```

### `NormalDistribution` — package `org.hipparchus.distribution.continuous`

```java
NormalDistribution normal = new NormalDistribution(0, 1); // standard normal
double z95 = normal.inverseCumulativeProbability(0.95); // = 1.6448536...
// For 95% 1-sided VaR: z = Φ^{-1}(0.95) ≈ 1.6449
// Convention lock: per CONTEXT.md, use z = 1.645 (documented constant, not recomputed)
```

**Note:** `inverseCumulativeProbability(0.95)` returns ~1.6448536. The CONTEXT.md locks `z = 1.645` as the documented constant. Using `NormalDistribution` directly at test time to verify this value is fine — the golden-value test should assert within ±0.001.

### `TDistribution` — package `org.hipparchus.distribution.continuous`

```java
// Used for ADF p-value in cointegration — see Cointegration section for full construction
TDistribution tDist = new TDistribution(df); // df = degrees of freedom
double pValue = tDist.cumulativeProbability(tStat); // P(T ≤ tStat)
// For a left-tail test (ADF null is τ < 0): p-value = cumulativeProbability(tStat)
```

---

## Financial Math: Formula-by-Formula Reference

### Metric 1: Log Returns from Equity Curve

**Source:** Log returns are the universal standard for daily equity analytics. They are time-additive, small enough for the normality approximation, and consistent with the GBM model used to generate the seed data.

```java
// From PortfolioService.buildEquityCurve() → List<DateValueDto> curve
// curve[i].value() is BigDecimal; convert to double for statistics
double[] logReturns(List<DateValueDto> curve) {
    double[] r = new double[curve.size() - 1];
    for (int i = 1; i < curve.size(); i++) {
        double p1 = curve.get(i).value().doubleValue();
        double p0 = curve.get(i - 1).value().doubleValue();
        r[i - 1] = Math.log(p1 / p0);
        // Guard: if p0 == 0, skip or assign 0 (defensive; seeded data has p0 > 0)
    }
    return r;
}
```

**Important:** The equity curve has 504 entries (bars[0]..bars[503]). This yields 503 daily log returns.

### Metric 2: Sharpe Ratio

**Formula:**
```
Sharpe = (mean(r_excess) / std(r)) × √252
r_excess[i] = logReturn[i] − rfDaily[i]
rfDaily[i]  = FactorReturn.rf for that date   (seeded: RF_ANNUAL/252 = 0.04/252 ≈ 0.000159)
```

**If RF not aligned:** Use `rf = 0` and document. The seeded factor series aligns exactly with the OHLCV trading calendar (same 504 days, same start date), so date-matched RF lookup is trivial.

```java
// Both returns[] and rfDailyArr[] must be length 503 and date-aligned
double[] excessReturns = new double[returns.length];
for (int i = 0; i < returns.length; i++) {
    excessReturns[i] = returns[i] - rfDailyArr[i]; // rfDailyArr from FactorReturn.rf
}

DescriptiveStatistics stats = new DescriptiveStatistics(returns); // use total returns for vol
double meanExcess = Arrays.stream(excessReturns).average().orElse(0.0);
double stdR = stats.getStandardDeviation(); // sample std, denominator n−1

double sharpe = (stdR == 0.0) ? Double.NaN : (meanExcess / stdR) * Math.sqrt(252.0);
```

**Annualization:** `× √252`, NOT `× √365`. [VERIFIED reasoning: 252 trading days/year is the universal convention for daily equity data; PITFALLS.md confirms this explicitly]

**Worked approximation from seed parameters:** AAPL has `mu=0.10, sigma=0.28`. Under the GBM model, the expected daily log return is `(mu - sigma²/2) × DT = (0.10 - 0.0392) × (1/252) ≈ 0.000242`. Daily RF = `0.04/252 ≈ 0.000159`. Daily excess return ≈ `0.000083`. Daily vol ≈ `0.28/√252 ≈ 0.01764`. Expected AAPL Sharpe ≈ `0.000083/0.01764 × √252 ≈ 0.075`. Portfolio Sharpe is a blend and will depend on the realized seed path — print it with the golden-value printer test.

### Metric 3: Annualized Volatility

```java
double annualizedVol = stats.getStandardDeviation() * Math.sqrt(252.0);
// stats computed from log returns array
```

### Metric 4: Max Drawdown

**Algorithm:** Iterate the equity curve tracking the running peak; drawdown at each point = `(peak − value) / peak`. Return as a negative percentage.

```java
static double maxDrawdown(List<DateValueDto> curve) {
    double peak = Double.NEGATIVE_INFINITY;
    double maxDd = 0.0; // will be <= 0 for any drawdown
    for (DateValueDto pt : curve) {
        double v = pt.value().doubleValue();
        if (v > peak) peak = v;
        double dd = (peak > 0) ? (v - peak) / peak : 0.0;
        if (dd < maxDd) maxDd = dd;
    }
    return maxDd; // negative number, e.g. -0.18 for 18% drawdown
}
```

**Return convention:** Return as a `double` in `[−1, 0]`. The DTO field `maxDrawdownPct` documents the convention: "negative; e.g. −0.18 = 18% drawdown from peak". The frontend formats it as `formatSignedPercent(maxDrawdown * 100)`.

### Metric 5: Beta

**Formula:** `β = cov(r_portfolio, r_benchmark) / var(r_benchmark)`

This equals the OLS slope coefficient if you regressed portfolio returns on benchmark returns — both methods give identical results (covariance/variance IS the OLS slope). Use `Covariance` for simplicity.

```java
// benchmarkReturns: 503 log returns of SPX500 equity curve
// portfolioReturns: 503 log returns of portfolio equity curve
Covariance cov = new Covariance();
double pairCov = cov.covariance(portfolioReturns, benchmarkReturns); // sample covariance
DescriptiveStatistics bmkStats = new DescriptiveStatistics(benchmarkReturns);
double bmkVar = bmkStats.getVariance(); // sample variance
double beta = (bmkVar == 0.0) ? Double.NaN : pairCov / bmkVar;
```

**Beta from seed context:** SPX500 spec is `mu=0.07, sigma=0.18, beta=1.0`. The benchmark equity curve is generated with the same market factor. Portfolio betas depend on the weighted average of individual security betas. Alice's Growth portfolio holds AAPL (β=1.2), MSFT (β=1.15), NVDA (β=1.4), AMZN (β=1.3), TSLA (β=1.4) — expected realized beta > 1.2 approximately.

### Metric 6: Historical VaR (95%, 1-day)

**Exact formula:**

```java
// returns: double[] of 503 portfolio daily log returns
DescriptiveStatistics stats = new DescriptiveStatistics(returns);
double returnAtP5 = stats.getPercentile(5.0); // 5th percentile — a NEGATIVE number
double currentPortfolioValue = ...; // BigDecimal from PortfolioService → double

// VaR as a POSITIVE loss number (convention: VaR = loss magnitude)
double historicalVaR = -returnAtP5 * currentPortfolioValue;
// Sign: returnAtP5 is negative → negating makes historicalVaR positive
// If return at 5th percentile is -2%, historicalVaR = +2% × portfolioValue
```

**Sign convention (critical):** The CONTEXT.md and PITFALLS.md both specify VaR as a positive loss number. `getPercentile(5)` returns a negative number for a loss; negating it gives a positive VaR. The DTO field `historicalVarAmount` is `BigDecimal` (monetary), labeled "1-day 95% Historical VaR (positive = potential loss)".

**Alternative with simple returns:** Using log returns for VaR is the consistent choice (matches Sharpe vol convention). Simple returns would give a slightly different VaR; log returns slightly overstate losses for large tail moves (log return of -20% corresponds to a -18.1% simple return). For a 1-day VaR with typical values, the difference is negligible.

### Metric 7: Parametric Gaussian VaR (95%, 1-day)

```java
// z = Φ^{-1}(0.95) ≈ 1.6449 — locked as 1.645 per CONTEXT.md
double z95 = 1.645; // documented constant; verify with NormalDistribution at runtime

DescriptiveStatistics stats = new DescriptiveStatistics(returns);
double dailySigma = stats.getStandardDeviation();
double dailyMean  = stats.getMean();

// Full parametric VaR formula (includes mean drift for 1-day horizon):
// VaR = -(μ - z × σ) × portfolioValue
// For 1-day, mean is negligible but include it for correctness:
double parametricVaR = -(dailyMean - z95 * dailySigma) * currentPortfolioValue;
// = (z × σ - μ) × value — positive for normal equity portfolios
```

**Simplified form (common in practice):** If `μ ≈ 0` for a 1-day horizon, `parametricVaR ≈ z × σ_daily × value = 1.645 × σ × value`. The mean term `μ × value` is typically < 0.01% × value and can be omitted with documentation. Include it for precision.

### Optional: CVaR / Expected Shortfall (ES)

For Gaussian ES at 95%: `ES = φ(z95) / (1 − 0.95) × σ × value = (φ(1.645) / 0.05) × σ × value`
where `φ(z) = (1/√2π) × exp(−z²/2) ≈ 0.1031` at z=1.645.
`ES_parametric = 0.1031 / 0.05 × σ × value ≈ 2.063 × σ × value`.

For historical ES: mean of returns below the 5th percentile.

```java
// Historical CVaR (optional):
double p5threshold = stats.getPercentile(5.0);
double[] tailReturns = Arrays.stream(returns)
    .filter(r -> r <= p5threshold)
    .toArray();
double historicalCVaR = (tailReturns.length > 0)
    ? -(Arrays.stream(tailReturns).average().orElse(0.0)) * currentPortfolioValue
    : historicalVaR;
```

---

## Fama-French 3-Factor Attribution (ATTR-01)

### Regression Specification

```
r_portfolio_excess[t] = α + β_mkt × MktRf[t] + β_smb × SMB[t] + β_hml × HML[t] + ε[t]

where:
  r_portfolio_excess[t] = r_portfolio[t] − rf[t]   (daily log portfolio return minus daily RF)
  MktRf[t], SMB[t], HML[t]  = from FactorReturn entity for date t
  α = annualized as α_daily × 252
```

### Date Alignment

The seeded factor series and OHLCV series share the identical 504-day trading calendar (both use `GbmGenerator.nextTradingDay()` from `SERIES_START = 2022-09-12`). Since log returns are computed from adjacent bars (504 bars → 503 returns), the factor series must be shortened to 503 entries by dropping the FIRST factor row (which corresponds to day 0, when there is no preceding day to compute a return from). Alternatively, use factors[1]..factors[503] (i.e., align factor[t] with return from day[t-1] to day[t]).

**Correct alignment:**

```java
// equityCurve: 504 DateValueDto entries (day 0..503)
// factorRows: 504 FactorRow entries for same dates

// Log return on day t = ln(close[t] / close[t-1]), correlated with factor on day t
// So r[i] (i = 0..502) corresponds to return from day[i] to day[i+1]
// Factor row for that return = factorRows corresponding to date curve.get(i+1).date()

// Implementation: zip portfolioLogReturns[0..502] with factorRows for dates[1..503]
// portfolioLogReturns[i] ↔ factorRows for equityCurve.get(i+1).date()
```

**Java implementation:**

```java
// returns503: double[503] portfolio log returns
// Load factor returns ordered by factorDate ASC from FactorReturnRepository
// Drop the first factor row (it corresponds to the base date, no return yet)

double[] mktRf = new double[503];
double[] smb   = new double[503];
double[] hml   = new double[503];
double[] rf503 = new double[503];

for (int i = 0; i < 503; i++) {
    FactorReturn fr = factorRows.get(i + 1); // factor for day i+1 (aligns with return[i])
    mktRf[i] = fr.getMktRf().doubleValue();
    smb[i]   = fr.getSmb().doubleValue();
    hml[i]   = fr.getHml().doubleValue();
    rf503[i] = fr.getRf().doubleValue();
}

// Excess returns
double[] excessReturns = new double[503];
for (int i = 0; i < 503; i++) {
    excessReturns[i] = returns503[i] - rf503[i];
}

// Build the X matrix: 503 rows × 3 columns [MktRf, SMB, HML]
double[][] xMatrix = new double[503][3];
for (int i = 0; i < 503; i++) {
    xMatrix[i][0] = mktRf[i];
    xMatrix[i][1] = smb[i];
    xMatrix[i][2] = hml[i];
}

OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
reg.newSampleData(excessReturns, xMatrix); // Hipparchus adds intercept automatically
double[] params = reg.estimateRegressionParameters();
// params[0] = α (daily intercept)
// params[1] = β_mkt
// params[2] = β_smb
// params[3] = β_hml

double[] stdErrors = reg.estimateRegressionParametersStandardErrors();
double rSquared    = reg.calculateRSquared();
double alphaAnnualized = params[0] * 252.0; // daily alpha × 252 → annualized
```

### Factor Contributions

```java
// Contribution of each factor = β × mean(factor over sample period)
double meanMktRf = Arrays.stream(mktRf).average().orElse(0.0);
double meanSmb   = Arrays.stream(smb).average().orElse(0.0);
double meanHml   = Arrays.stream(hml).average().orElse(0.0);

double contribMkt = params[1] * meanMktRf * 252.0; // annualized
double contribSmb = params[2] * meanSmb   * 252.0;
double contribHml = params[3] * meanHml   * 252.0;
// α_annualized + contribMkt + contribSmb + contribHml ≈ portfolio annualized excess return
```

### Seeded Factor Characteristics

From `GbmGenerator.generateFactors`:
- **Mkt-RF:** Market excess return = gross return minus RF_DAILY. Derived from the same MersenneTwister(42) market-factor draws as the OHLCV series. Mean over 504 days ≈ `(MU_MKT - 0.5*SIGMA_MKT²) * DT + 0` in expectation; realized mean depends on the seed.
- **SMB:** `MersenneTwister(43)` (seed 42+1), daily sigma `0.10/√252 ≈ 0.006299`. Mean ≈ 0 (zero-mean process).
- **HML:** Same RNG as SMB (sequential draws), daily sigma `0.12/√252 ≈ 0.007559`. Mean ≈ 0.
- **RF:** `0.04/252 ≈ 0.000159` (constant daily fraction stored in each FactorRow).

**Implication for FF attribution:** Alice's Growth portfolio is tech-heavy (all five holdings have beta > 1.1). Expected results from seed: `β_mkt > 1.0`, `β_smb < 0` (large-cap tilt), `β_hml < 0` (growth tilt), alpha close to zero (by construction of GBM). Print with the golden-value printer.

### Attribution DTO

```java
public record AttributionDto(
    double alphaAnnualized,     // intercept × 252; e.g. 0.02 = 2% annual alpha
    double betaMkt,
    double betaSmb,
    double betaHml,
    double rSquared,
    double contribMkt,          // β_mkt × mean(MktRf) × 252
    double contribSmb,          // β_smb × mean(SMB) × 252
    double contribHml,          // β_hml × mean(HML) × 252
    double alphaStdError,       // for display confidence
    double betaMktStdError
) {}
```

---

## Cointegration Pairs Scanner (ARB-01)

### Engle-Granger 2-Step: Full Construction

**Step 1: OLS hedge ratio regression**

For a candidate pair (ticker Y, ticker X), both using their daily LOG price series (not returns):

```java
// logPriceY[i] = ln(close_Y[i]) for i = 0..503
// logPriceX[i] = ln(close_X[i]) for i = 0..503

OLSMultipleLinearRegression step1 = new OLSMultipleLinearRegression();
double[][] xCol = new double[504][1]; // single predictor
for (int i = 0; i < 504; i++) xCol[i][0] = logPriceX[i];
step1.newSampleData(logPriceY, xCol);
// params[0] = α (intercept), params[1] = β (hedge ratio)
double hedgeRatio = step1.estimateRegressionParameters()[1];
double[] residuals = step1.estimateResiduals(); // spread[i] = logPriceY[i] - α - β×logPriceX[i]
```

**Critical warning:** ADF is applied to the RESIDUAL SPREAD `ε[i]`, NOT raw log prices. Testing log prices for a unit root is expected to fail (log prices are non-stationary by construction) — only testing the spread residual makes sense for cointegration.

**Step 2: ADF test on residuals**

The ADF test with constant only (no trend), lag order p=1 (sufficient for synthetic GBM-generated series):

```
ΔS[t] = c + δ × S[t-1] + Σ_{j=1}^{p} φ_j × ΔS[t-j] + η[t]
H₀: δ = 0 (unit root, non-stationary spread → NOT cointegrated)
H₁: δ < 0 (mean-reverting spread → cointegrated)
ADF statistic τ = δ̂ / SE(δ̂)
```

For `p=1` lag:

```java
// spread: double[504] residuals from Step 1
// Build ADF regression data:
//   y[i] = spread[i+1] - spread[i]  (ΔS[t]) for i = 0..501   (n=502 observations)
//   x1[i] = spread[i]               (S[t-1])
//   x2[i] = spread[i+1] - spread[i] (ΔS[t-1]) — one lag of the difference

int n = spread.length; // 504 for residuals from 504-bar series
int adfN = n - 2;      // 502 observations (lose 2: one for diff, one for lag)

double[] adfY  = new double[adfN];
double[][] adfX = new double[adfN][2]; // [S_{t-1}, ΔS_{t-1}]

for (int i = 0; i < adfN; i++) {
    // t corresponds to index i+2 in original spread array
    adfY[i]     = spread[i + 2] - spread[i + 1];  // ΔS[t]
    adfX[i][0]  = spread[i + 1];                   // S[t-1]
    adfX[i][1]  = spread[i + 1] - spread[i];       // ΔS[t-1]
}

OLSMultipleLinearRegression adfReg = new OLSMultipleLinearRegression();
adfReg.newSampleData(adfY, adfX);
// params[0] = intercept (c), params[1] = δ, params[2] = φ_1
double[] adfParams = adfReg.estimateRegressionParameters();
double[] adfSE     = adfReg.estimateRegressionParametersStandardErrors();

double delta = adfParams[1];  // coefficient on S[t-1]
double seDelta = adfSE[1];    // standard error of δ
double tStat = delta / seDelta; // ADF test statistic τ
```

### MacKinnon Critical Values and P-Value Approximation

**The correct critical values to use are NOT standard t-distribution critical values.** The ADF statistic follows the Dickey-Fuller distribution, which is non-standard. For the case of a cointegration residual (tested after an OLS regression was already run), the critical values are further adjusted per MacKinnon (1994/2010).

**For the Engle-Granger 2-step residual with constant only:**

The appropriate critical values at finite sample T≈500 are approximately:
- **1%:** −3.96 [CITED: MacKinnon 2010 response surface, n=2 cointegrating variables, constant only]
- **5%:** −3.37 [CITED: same source]
- **10%:** −3.07 [CITED: same source]

These are stricter (more negative) than the standard ADF critical values for a raw series (−3.43/−2.86/−2.57) because the spread has already been fitted via OLS, reducing its variability and inflating false-positive cointegration signals if standard critical values are used.

**For a simpler implementation in a demo context**, use the standard MacKinnon (2010) ADF critical values for 1 variable, constant case (acceptable approximation for cointegration residuals given the synthetic nature of the seeded data):

```java
// MacKinnon 2010 asymptotic critical values, constant only (1 variable, "c" regression)
// [CITED: statsmodels/tsa/adfvalues.py, MacKinnon 2010 tables]
static final double ADF_CV_1PCT  = -3.430; // tau_c at 1%
static final double ADF_CV_5PCT  = -2.862; // tau_c at 5%
static final double ADF_CV_10PCT = -2.567; // tau_c at 10%
```

**P-value approximation using MacKinnon response surface (recommended for display):**

The p-value is approximated from the normalized statistic using response surface regression, following MacKinnon (1994/2010) as implemented in Python's statsmodels. For the constant-only ("c") case with 1 variable:

```java
/**
 * Approximate MacKinnon (2010) ADF p-value for "c" regression, asymptotic.
 * Uses the same polynomial response surface as Python statsmodels mackinnonp().
 * 
 * [CITED: github.com/statsmodels/statsmodels/blob/main/statsmodels/tsa/adfvalues.py]
 * tau_star_c[0] = -1.61 (boundary between small-p and large-p regimes)
 * tau_max_c[0]  =  2.74 (τ above this → p-value ≈ 1.0)
 * tau_min_c[0]  = -18.83 (τ below this → p-value ≈ 0.0)
 *
 * small p (τ <= tau_star): use tau_c_smallp[0] = [2.1659, 1.4412, 3.8269e-2]
 *   normalized = (τ - tau_c_smallp[0]) / tau_c_smallp[1]  — rough normal approximation
 * large p (τ > tau_star):  use tau_c_largep[0] = [1.7339, 0.93202, -0.12745, -0.010368]
 *   p ≈ NormalDistribution.cumulativeProbability(eval(largep, τ))
 *
 * NOTE: These constants are [ASSUMED] to be the correct MacKinnon 2010 values as
 * extracted from statsmodels source. Verify against the published mackinnonp()
 * output for a known τ value (e.g., τ = -3.0 should give p ≈ 0.034 for "c", 1 var).
 */
double approximatePValue(double tau) {
    if (tau > 2.74)  return 1.0;
    if (tau < -18.83) return 0.0;

    NormalDistribution nd = new NormalDistribution(0, 1);

    if (tau <= -1.61) {
        // Small p regime: polynomial in τ normalized
        // Coefficients: smallp[0] coeffs for "c", n=1: [2.1659, 1.4412, 0.038269]
        double lstar = 2.1659 + 1.4412 * tau + 0.038269 * tau * tau;
        return nd.cumulativeProbability(lstar);
    } else {
        // Large p regime: coefficients for "c", n=1: [1.7339, 0.93202, -0.12745, -0.010368]
        double lstar = 1.7339 + 0.93202 * tau - 0.12745 * tau * tau - 0.010368 * tau * tau * tau;
        return nd.cumulativeProbability(lstar);
    }
}
```

**Golden-value verification:** For `τ = −3.0`, the expected p-value from statsmodels `mackinnonp(-3.0, "c", 1)` is approximately 0.034. Run this check in the golden-value printer test.

**Practical threshold for demo:** Report a pair as "cointegrated" if `p-value < 0.05`. With the bounded same-sector candidate set (at most ~10 pairs), the multiple-testing concern is reduced. Flag in comments that a Bonferroni correction at `α/10 = 0.005` would be more rigorous.

### Spread Z-Score and Signal

```java
// After passing ADF test (p < 0.05), compute current spread Z-score
DescriptiveStatistics spreadStats = new DescriptiveStatistics(residuals);
double spreadMean = spreadStats.getMean();
double spreadStd  = spreadStats.getStandardDeviation();
double currentSpread = residuals[residuals.length - 1]; // most recent spread
double zScore = (spreadStd == 0.0) ? 0.0 : (currentSpread - spreadMean) / spreadStd;

// Signal thresholds (standard pairs-trading convention)
String signal;
if (zScore >  2.0) signal = "SHORT_Y_LONG_X";  // spread too high; mean-revert down
else if (zScore < -2.0) signal = "LONG_Y_SHORT_X";   // spread too low; mean-revert up
else signal = "NEUTRAL";
```

### Candidate Pair Selection (Bounded Set)

To keep the scanner demo-fast (ADF for each pair is O(n) but the OLS is still O(n²) in total for N pairs):

```java
// Group seeded securities by sector; same-sector pairs only
// Alice's Growth: {AAPL, MSFT, NVDA, AMZN, TSLA} → sector: Technology/Automotive
// Generate C(5,2)=10 pairs for Alice's Growth portfolio
// Bob's Income: {JPM, BAC, XOM, CVX, PG, KO, WMT} → mixed; subgroup by sector:
//   Financials: (JPM,BAC); Energy: (XOM,CVX); Consumer Staples: (PG,KO), (PG,WMT), (KO,WMT)
// Use all portfolio holdings + benchmark securities for the candidate universe
```

**Hard limit:** Cap at 20 candidate pairs maximum regardless of portfolio size. For demo portfolios (5-7 holdings), same-sector selection naturally produces <15 pairs.

### Pairs Scan DTO

```java
public record PairResultDto(
    String tickerY,
    String tickerX,
    double hedgeRatio,        // β from Step 1 OLS
    double adfStatistic,      // τ
    double pValue,            // MacKinnon approximation
    double spreadZScore,      // (currentSpread - mean) / std
    String signal             // "LONG_Y_SHORT_X" | "SHORT_Y_LONG_X" | "NEUTRAL"
) {}
```

---

## Architecture Patterns

### Spring Modulith: New `analytics` Module

Create `com.quantlens.analytics` as a new Spring Modulith module, separate from `portfolio`. This avoids adding ~500 lines of Hipparchus statistical code to `PortfolioService`.

```
com.quantlens.analytics/
├── package-info.java           (@ApplicationModule annotation)
├── api/
│   ├── AnalyticsController.java   (REST endpoints — 4 routes under /api/portfolio/)
│   ├── RiskScorecardDto.java      (record)
│   ├── CorrelationMatrixDto.java  (record)
│   ├── AttributionDto.java        (record)
│   └── PairResultDto.java         (record)
└── service/
    ├── RiskCalculator.java        (@Component, pure statistics)
    ├── FamaFrenchCalculator.java  (@Component, OLS regression)
    └── CointegrationScanner.java  (@Component, ADF + z-score)
```

**Module boundary declaration:**

```java
// com/quantlens/analytics/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"marketdata::domain", "portfolio::service"}
)
package com.quantlens.analytics;
```

The `analytics` module needs access to:
1. `marketdata::domain` — for `OhlcvBarRepository`, `FactorReturnRepository`, `SecurityRepository` (already a named interface from Phase 1).
2. `portfolio::service` — to reuse `PortfolioService.buildEquityCurve()` (or a shared helper). **This requires adding `@NamedInterface("service")` to `com/quantlens/portfolio/service/package-info.java`.** This was deferred in Phase 2 and is now the time to add it.

**Alternative (simpler, recommended):** Extract the return-computation logic into a package-private static helper in the analytics module's own `service/` package, and load raw equity curve data by calling `OhlcvBarRepository` directly. The `analytics` module then only depends on `marketdata::domain`. The `PortfolioService.buildEquityCurve()` method becomes an optional reuse — duplicate the equity-curve reading logic in `RiskCalculator` since it's short (20 lines) and avoids a module dependency. Mark with a `// REUSE: mirrors PortfolioService.buildEquityCurve` comment.

**Recommended choice:** Keep the analytics module depending only on `marketdata::domain` (no `portfolio::service` named interface needed). The equity curve is simply re-derived from `OhlcvBarRepository` + the principal's positions (loaded from `PositionRepository` via `portfolio::domain`).

```java
// com/quantlens/analytics/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {"marketdata::domain", "portfolio::domain"}
)
package com.quantlens.analytics;
```

`portfolio::domain` is the entities+repositories package, already a named interface from Phase 1.

### System Architecture Diagram

```
HTTP GET /api/portfolio/risk (principal-scoped)
         │
         ▼
AnalyticsController
  │ resolve principal → portfolio (same chain as PortfolioController)
  │
  ▼
RiskCalculator.computeRiskScorecard(portfolioId, principalPositions)
  │
  ├──▶ PositionRepository.findByPortfolioIdWithSecurity()    ─── portfolio positions
  ├──▶ OhlcvBarRepository.findAllBySecurityIdsOrdered()      ─── 504 bars × N positions
  │         │
  │         └──▶ buildEquityCurve() → List<DateValueDto>     (mirrors PortfolioService)
  │                    │
  │                    └──▶ logReturns(curve)                 → double[503]
  │
  ├──▶ SecurityRepository.findByBenchmarkTrue()              ─── SPX500
  ├──▶ OhlcvBarRepository.findAllBySecurityIdsOrdered([spx]) ─── SPX500 bars
  │         └──▶ logReturns(spxCurve)                        → double[503]
  │
  ├──▶ FactorReturnRepository.findAllByOrderByFactorDateAsc() ─── 504 factor rows
  │
  └──▶ computeMetrics(portfolioReturns, benchmarkReturns, factorRows)
            ├── Sharpe, vol, drawdown, beta → RiskScorecardDto
            ├── historicalVaR, parametricVaR → VarDto (nested in scorecard)
            └── Return RiskScorecardDto

HTTP GET /api/portfolio/correlation
  └──▶ PearsonsCorrelation(n×k returns matrix)
            └──▶ CorrelationMatrixDto {tickers[], matrix[][]}

HTTP GET /api/portfolio/attribution
  └──▶ FamaFrenchCalculator.compute(portfolioExcessReturns, mktRf, smb, hml)
            └──▶ OLSMultipleLinearRegression → AttributionDto

HTTP GET /api/portfolio/pairs
  └──▶ CointegrationScanner.scan(candidatePairs, logPriceSeries)
            ├── Step 1: OLS hedge ratio per pair
            ├── Step 2: ADF on residuals
            └──▶ List<PairResultDto> (filtered by p < 0.05)
```

### Recommended Project Structure

```
backend/src/main/java/com/quantlens/
├── analytics/
│   ├── package-info.java
│   ├── api/
│   │   ├── AnalyticsController.java
│   │   ├── RiskScorecardDto.java
│   │   ├── VarResultDto.java
│   │   ├── CorrelationMatrixDto.java
│   │   ├── AttributionDto.java
│   │   └── PairResultDto.java
│   └── service/
│       ├── RiskCalculator.java
│       ├── FamaFrenchCalculator.java
│       └── CointegrationScanner.java
├── marketdata/domain/          (Phase 1, unchanged)
├── portfolio/domain/           (Phase 1, unchanged)
├── portfolio/api/              (Phase 2, unchanged)
├── portfolio/service/
│   ├── package-info.java       (ADD @NamedInterface("service") OR leave analytics depending on ::domain only)
│   └── PortfolioService.java   (Phase 2, unchanged)
└── seed/                       (Phase 1, unchanged)

frontend/src/
├── components/
│   ├── RiskScorecard.vue       (new — fills Phase 3 slot "Risk Scorecard — Phase 4")
│   ├── CorrelationHeatmap.vue  (new — fills "Correlation Heatmap — Phase 4")
│   ├── AttributionChart.vue    (new)
│   └── PairsTable.vue          (new)
├── api/
│   └── analytics.ts            (new — 4 fetch functions for analytics endpoints)
└── stores/
    └── portfolio.ts            (EXTEND: add risk, correlation, attribution, pairs AsyncState)
```

---

## DTO Shapes

### RiskScorecardDto

```java
public record VarResultDto(
    String method,       // "HISTORICAL" or "PARAMETRIC"
    double confidence,   // 0.95
    int horizonDays,     // 1
    BigDecimal amount,   // positive monetary VaR in portfolio currency
    double percentage    // VaR as percentage of portfolio value (e.g. 0.018 = 1.8%)
) {}

public record RiskScorecardDto(
    double sharpeRatio,
    double annualizedVolatility,   // e.g. 0.22 = 22%
    double maxDrawdown,            // e.g. -0.18 = 18% drawdown (negative)
    double beta,
    List<VarResultDto> var         // always 2 entries: historical + parametric
) {}
```

### CorrelationMatrixDto

```java
public record CorrelationMatrixDto(
    List<String> tickers,      // ordered list of ticker labels
    List<List<Double>> matrix  // tickers.size() × tickers.size(); diagonal = 1.0
) {}
```

### AttributionDto

```java
public record AttributionDto(
    double alphaAnnualized,
    double betaMkt,
    double betaSmb,
    double betaHml,
    double rSquared,
    double contribMktAnnualized,
    double contribSmbAnnualized,
    double contribHmlAnnualized
) {}
```

---

## Frontend: ECharts Integration

### Correlation Heatmap (`CorrelationHeatmap.vue`)

The `quantlens-dark` theme already registers a `visualMap` with the correct color scale (blue=−1, white=0, red=+1). [VERIFIED: codebase `echarts-theme.ts`]

```typescript
// Source: ECharts 6 official docs — heatmap series type
// [ASSUMED] option structure matches ECharts 6.1.0 API

function buildHeatmapOption(dto: CorrelationMatrixDto): EChartsOption {
  const n = dto.tickers.length
  const data: [number, number, number][] = []
  for (let i = 0; i < n; i++) {
    for (let j = 0; j < n; j++) {
      data.push([j, i, dto.matrix[i][j]])
      // ECharts heatmap: [xIndex, yIndex, value]
      // x axis = column (j), y axis = row (i)
    }
  }
  return {
    tooltip: {
      formatter: (params: any) => {
        const [xi, yi, v] = params.data
        return `${dto.tickers[yi]} / ${dto.tickers[xi]}: ${v.toFixed(3)}`
      }
    },
    xAxis: {
      type: 'category',
      data: dto.tickers,
      axisLabel: { rotate: 45 }
    },
    yAxis: {
      type: 'category',
      data: [...dto.tickers].reverse(), // reverse so (0,0) is top-left
    },
    visualMap: {
      min: -1,
      max: 1,
      calculable: true,
      orient: 'horizontal',
      left: 'center',
      bottom: 8,
      // colors from theme visualMap: ['#ef4444', '#f8fafc', '#3b82f6']
      // ECharts visualMap.color is listed from high to low
      color: ['#ef4444', '#f8fafc', '#3b82f6'],
    },
    series: [{
      type: 'heatmap',
      data,
      label: {
        show: true,
        formatter: (p: any) => p.data[2].toFixed(2),
        fontSize: 10,
        color: '#1e293b'  // dark text on the colored cells
      },
      emphasis: { itemStyle: { shadowBlur: 10 } }
    }]
  }
}
```

**Note on yAxis direction:** ECharts heatmap places y=0 at the bottom by default. Reversing the `yAxis.data` array ensures the matrix appears with ticker[0] at the top-left (matching the `matrix[0][0]` diagonal), which is the standard correlation matrix layout.

### Attribution Bar Chart (`AttributionChart.vue`)

```typescript
function buildAttributionOption(dto: AttributionDto): EChartsOption {
  const labels = ['Alpha', 'Mkt-RF', 'SMB', 'HML']
  const values = [
    dto.alphaAnnualized * 100,        // convert to percentage
    dto.contribMktAnnualized * 100,
    dto.contribSmbAnnualized * 100,
    dto.contribHmlAnnualized * 100,
  ]
  const colors = values.map(v => v >= 0 ? '#22c55e' : '#ef4444') // green/red per sign

  return {
    tooltip: { formatter: '{b}: {c}%' },
    xAxis: { type: 'category', data: labels },
    yAxis: { type: 'value', axisLabel: { formatter: '{value}%' } },
    series: [{
      type: 'bar',
      data: values.map((v, i) => ({ value: v, itemStyle: { color: colors[i] } })),
      label: { show: true, position: 'top', formatter: (p: any) => `${p.value.toFixed(2)}%` }
    }]
  }
}
```

### Store Extension (`portfolio.ts`)

```typescript
// Add to portfolio.ts store — follow existing AsyncState pattern exactly

const risk        = asyncState<RiskScorecardDto>(null)
const correlation = asyncState<CorrelationMatrixDto>(null)
const attribution = asyncState<AttributionDto>(null)
const pairs       = asyncState<PairResultDto[]>(null)

async function fetchRisk(version?: number): Promise<void> {
  risk.loading = true; risk.error = null
  try {
    const { data } = await axios.get<RiskScorecardDto>('/api/portfolio/risk')
    if (version !== undefined && version !== refreshVersion) return
    risk.data = data
  } catch (e: any) {
    if (version !== undefined && version !== refreshVersion) return
    risk.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load risk metrics'
  } finally { risk.loading = false }
}
// ... same pattern for fetchCorrelation, fetchAttribution, fetchPairs

// Add all 4 to refreshAll() Promise.allSettled call
// Add all 4 to $reset()
```

---

## Golden Values: How to Derive and Test

### The Golden-Value Printer Pattern (from Phase 2)

Add a `@Test @Disabled("Golden value printer — run once to derive expected values")` test:

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Disabled("Run manually: ./mvnw test -Dtest=AnalyticsGoldenValuePrinterTest")
class AnalyticsGoldenValuePrinterTest extends AbstractPostgresIntegrationTest {

    @Autowired RiskCalculator riskCalculator;
    @Autowired FamaFrenchCalculator ffCalc;
    @Autowired CointegrationScanner scanner;

    @Test
    void printGoldenValues_aliceGrowthPortfolio() {
        // Resolve alice's portfolio from the seeded DB
        // Call each metric computation
        // Print all values with 8 decimal places for use as test constants
        System.out.printf("SHARPE=%.8f%n", ...);
        System.out.printf("ANNUAL_VOL=%.8f%n", ...);
        System.out.printf("MAX_DRAWDOWN=%.8f%n", ...);
        System.out.printf("BETA=%.8f%n", ...);
        System.out.printf("HIST_VAR_PCT=%.8f%n", ...);
        System.out.printf("PARAM_VAR_PCT=%.8f%n", ...);
        System.out.printf("ALPHA_ANNUALIZED=%.8f%n", ...);
        System.out.printf("BETA_MKT=%.8f%n", ...);
        System.out.printf("R_SQUARED=%.8f%n", ...);
    }
}
```

Then copy the printed values as constants into `AnalyticsServiceTest`.

### Tolerance Strategy

| Metric | Tolerance | Rationale |
|--------|-----------|-----------|
| Sharpe ratio | ±0.001 | Floating-point accumulation over 503 returns |
| Annualized vol | ±0.0001 | Similar accumulation |
| Max drawdown | ±0.0001 | Iterative comparison |
| Beta | ±0.001 | Covariance over 503 pairs |
| Historical VaR % | ±0.0001 | Percentile on 503 points is stable |
| Parametric VaR % | ±0.0001 | Formula-exact from std dev |
| FF alpha (annualized) | ±0.0001 | OLS is exact to double precision |
| FF betas | ±0.0001 | OLS |
| R² | ±0.001 | |
| ADF t-stat | ±0.01 | OLS on 502 points |
| Spread Z-score | ±0.001 | Mean/std on 504 points |
| ADF p-value | ±0.01 | Response-surface polynomial approximation |

**For monetary VaR (`BigDecimal`):** Use `compareTo` with `delta = 0.01` (1 cent).

### Known Approximate Values from Seed Parameters

These are expectations from GBM model parameters, NOT the exact realized values (which depend on the MersenneTwister(42) path). Use as sanity checks that realized values are in the right ballpark:

| Metric | Expected Range | Note |
|--------|----------------|------|
| Alice portfolio Sharpe | 0.05 to 0.35 | High-beta tech, 2-year path, could be negative |
| Alice annualized vol | 0.22 to 0.38 | Weighted avg vol of AAPL(0.28)/MSFT(0.25)/NVDA(0.45)/AMZN(0.30)/TSLA(0.55) |
| Alice beta | 1.2 to 1.4 | Weighted avg of betas > 1.1 for all 5 holdings |
| SPX500 annual vol | ~0.18 | By spec (sigma=0.18) |
| FF R² (Alice) | 0.70 to 0.90 | Tech-heavy portfolio tracks market closely |
| FF β_mkt (Alice) | 1.1 to 1.4 | High-beta portfolio |

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| OLS regression | Manual normal equations / matrix inverse | `OLSMultipleLinearRegression` | Handles QR decomposition, numerical stability, SE computation |
| Pearson correlation matrix | Nested loops + covariance | `PearsonsCorrelation(double[][] data)` | Handles numerical precision, returns `RealMatrix` |
| Normal distribution quantile | Approximation formula | `NormalDistribution.inverseCumulativeProbability(0.95)` | Accurate to machine precision |
| ADF critical values | Made-up numbers | MacKinnon 2010 constants (documented in this file) | Non-standard distribution; wrong values produce incorrect p-values |
| Sample std deviation | `sum(x-mean)²/n` | `DescriptiveStatistics.getStandardDeviation()` | Divides by n-1 (sample), which is correct; `n` (population) is wrong for financial returns |
| Z-score for VaR | T-distribution | Fixed `z = 1.645` | VaR at 95% uses the normal quantile, not a t-distribution t-statistic |

---

## Common Pitfalls

### Pitfall 1: Using `n` instead of `n−1` for volatility (Population vs Sample Std Dev)

**What goes wrong:** `Math.sqrt(variance/n)` (population std dev) instead of `Math.sqrt(variance/(n-1))` (sample std dev). For 503 returns, the difference is 503/502 ≈ 0.2% — small but wrong, and inconsistent with the Sharpe calculation if the two are mixed.

**How to avoid:** Use `DescriptiveStatistics.getStandardDeviation()` exclusively. It uses `n-1` (sample) by default. Do NOT compute `std = Math.sqrt(sumSqDev / n)`.

**Warning signs:** Your vol is systematically 0.1% lower than a reference implementation.

### Pitfall 2: Forgetting the Intercept in `OLSMultipleLinearRegression.estimateRegressionParameters()`

**What goes wrong:** Indexing `params[0]` as β_mkt instead of the intercept (α). The Hipparchus OLS ALWAYS prepends an intercept term when `noIntercept=false` (the default). Result: every FF beta is off by one index.

**How to avoid:** Document clearly: `params[0]=intercept, params[1]=β_mkt, params[2]=β_smb, params[3]=β_hml`. Add an assertion in the golden-value test that `params.length == 4` for a 3-factor regression.

**Worked layout verification:**
```java
reg.newSampleData(y, xMatrix); // xMatrix is 503×3
double[] p = reg.estimateRegressionParameters();
// p.length == 4 (intercept + 3 betas) -- assert this
assert p.length == 4 : "Expected [intercept, β_mkt, β_smb, β_hml] but got length " + p.length;
```

### Pitfall 3: ADF Test on Raw Log Prices Instead of Spread Residuals

**What goes wrong:** Running ADF on `logPriceY` or `logPriceX` (which have a unit root by construction in GBM data) will almost never reject H0. Running ADF on the OLS residuals tests whether the spread is stationary.

**How to avoid:** Always run ADF on `step1.estimateResiduals()`, not on the input price series.

### Pitfall 4: VaR Sign Inversion

**What goes wrong:** `getPercentile(5)` returns a negative number (e.g., −0.018 for −1.8% daily return). Reporting this directly as VaR gives a negative VaR, which is confusing or wrong depending on convention.

**How to avoid:** VaR = `−getPercentile(5) × portfolioValue`. Negative return × negative sign = positive VaR amount. The DTO field `amount` is a `BigDecimal` and must be positive. The `VarResultDto` contract should state: "amount is a positive number representing the potential loss".

### Pitfall 5: Date Misalignment in Fama-French Regression

**What goes wrong:** Using `factorRows.get(i)` instead of `factorRows.get(i+1)` when aligning factors with returns. Return[0] = `ln(close[1]/close[0])` corresponds to "what happened on day 1", which is explained by factors on day 1. Factor row index 0 corresponds to day 0 (the base date, no return yet).

**How to avoid:** Factor for return `r[i]` = `factorRows.get(i+1)`. Verified by: `equityCurve.get(i+1).date()` must equal `factorRow.getFactorDate()`.

Add this assertion in the golden-value printer:
```java
assert equityCurve.get(1).date().equals(factorRows.get(1).getFactorDate())
    : "Factor date misalignment at index 1";
```

### Pitfall 6: `OLSMultipleLinearRegression` requires at least `nobs > nvars + 1`

**What goes wrong:** With 503 observations and 3 regressors + intercept (4 params total), the rank condition `503 >> 4` is easily satisfied. But for the ADF regression with lag=1 on a short spread, if the spread is too short (< 10 observations), Hipparchus will throw `SingularMatrixException`.

**How to avoid:** Guard: `if (spread.length < 10) continue;` before ADF computation. All seeded pairs use 504-bar series, so `adfN = 502` — no issue in practice.

### Pitfall 7: `getPercentile` with fewer than 20 observations produces unreliable results

**What goes wrong:** Historical VaR at 5% confidence on 503 daily returns uses the 25th-worst return (503 × 0.05 ≈ 25). This is statistically reasonable. On a shorter series (e.g., 60-day lookback), you'd have only 3 observations in the 5% tail.

**How to avoid:** The seeded 503-return series is adequate. Document in the service: "Historical VaR uses a 503-day daily return window from the seed". Add a guard: `if (returns.length < 252) throw new IllegalArgumentException("Insufficient history for reliable VaR")`.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (all four endpoints require auth) | Spring Security session — Phase 1 SecurityConfig |
| V3 Session Management | yes (analytics scoped to authenticated user's portfolio) | Unchanged from Phase 1/2 |
| V4 Access Control | yes (never accept portfolioId from URL; derive from principal) | Same pattern as Phase 2 controller |
| V5 Input Validation | minimal (no user input; query params from principal only) | No free-form user input in analytics endpoints |
| V6 Cryptography | no | No new crypto |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| IDOR: request analytics for another user's portfolio | Elevation of Privilege | Resolve portfolioId from `Authentication.getName()` → AppUser → Portfolio; never from request params |
| DoS via expensive analytics recalculation | Availability | Analytics for 503 daily returns completes in <100ms; no pagination/caching needed for MVP. Add caching in Phase 6+ if needed |
| Correlation matrix with 15 securities: O(n²) but bounded | Availability | 15×15 = 225 pairs; trivially fast |
| Pairs scanner: ADF for 20 pairs | Availability | Each ADF is O(502) arithmetic ops; all 20 pairs complete in <10ms |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (Phase 1 wired) |
| Integration base class | `AbstractPostgresIntegrationTest` (Testcontainers) |
| Quick run command | `.\mvnw.cmd test -Dtest=RiskCalculatorTest,FamaFrenchCalculatorTest,CointegrationScannerTest` |
| Full suite command | `.\mvnw.cmd test` |
| Frontend tests | `npm run test:unit` (Vitest, established in Phase 3) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File |
|--------|----------|-----------|-------------------|------|
| RISK-01 | Sharpe ratio for alice = GOLDEN_SHARPE ±0.001 | Unit | `.\mvnw.cmd test -Dtest=RiskCalculatorTest#sharpe_alice_matchesGoldenValue` | Wave 0 |
| RISK-01 | Annualized vol for alice = GOLDEN_VOL ±0.0001 | Unit | `RiskCalculatorTest#annualizedVol_alice_matchesGoldenValue` | Wave 0 |
| RISK-01 | Max drawdown for alice ≤ 0 | Unit | `RiskCalculatorTest#maxDrawdown_alice_isNonPositive` | Wave 0 |
| RISK-01 | Max drawdown for alice = GOLDEN_DD ±0.0001 | Unit | `RiskCalculatorTest#maxDrawdown_alice_matchesGoldenValue` | Wave 0 |
| RISK-01 | Beta for alice > 1.0 (high-beta portfolio) | Unit | `RiskCalculatorTest#beta_alice_isAboveOne` | Wave 0 |
| RISK-01 | Beta for alice = GOLDEN_BETA ±0.001 | Unit | `RiskCalculatorTest#beta_alice_matchesGoldenValue` | Wave 0 |
| RISK-03 | Historical VaR amount > 0 | Unit | `RiskCalculatorTest#historicalVar_aliceIsPositive` | Wave 0 |
| RISK-03 | Parametric VaR amount > 0 | Unit | `RiskCalculatorTest#parametricVar_aliceIsPositive` | Wave 0 |
| RISK-03 | Historical VaR amount = GOLDEN_HIST_VAR ±0.01 | Integration | `AnalyticsControllerIntegrationTest#risk_endpoint_varAmountsMatchGoldenValues` | Wave 0 |
| RISK-03 | Response includes method="HISTORICAL" and method="PARAMETRIC" labels | Integration | `AnalyticsControllerIntegrationTest#risk_endpoint_varMethodsLabelled` | Wave 0 |
| RISK-02 | Correlation matrix is symmetric | Unit | `CorrelationCalculatorTest#correlationMatrix_isSymmetric` | Wave 0 |
| RISK-02 | Diagonal entries = 1.0 | Unit | `CorrelationCalculatorTest#correlationMatrix_diagonalIsOne` | Wave 0 |
| RISK-02 | AAPL–MSFT correlation = GOLDEN_CORR_AAPL_MSFT ±0.001 | Unit | `CorrelationCalculatorTest#aapl_msft_correlation_matchesGolden` | Wave 0 |
| ATTR-01 | FF regression params.length == 4 | Unit | `FamaFrenchCalculatorTest#ffRegression_paramsLengthIsFour` | Wave 0 |
| ATTR-01 | R² for alice FF = GOLDEN_R2 ±0.001 | Unit | `FamaFrenchCalculatorTest#ffRegression_rSquaredMatchesGolden` | Wave 0 |
| ATTR-01 | β_mkt > 0 (Alice is long market) | Unit | `FamaFrenchCalculatorTest#betaMkt_aliceIsPositive` | Wave 0 |
| ATTR-01 | α_annualized + contribMkt + contribSmb + contribHml ≈ portfolio annualized excess return ±0.001 | Unit | `FamaFrenchCalculatorTest#contributions_sumToPortfolioReturn` | Wave 0 |
| ARB-01 | ADF p-value approximation: mackinnonp(-3.0, "c", 1) ≈ 0.034 ±0.01 | Unit | `CointegrationScannerTest#adfPValue_knownStatistic_matches` | Wave 0 |
| ARB-01 | Spread Z-score for a known stationary spread = (currentSpread - mean) / std | Unit | `CointegrationScannerTest#spreadZScore_formulaCorrect` | Wave 0 |
| ARB-01 | /api/portfolio/pairs response has fields: tickerY, tickerX, pValue, zScore, signal | Integration | `AnalyticsControllerIntegrationTest#pairs_responseHasRequiredFields` | Wave 0 |
| RISK-01 | GET /api/portfolio/risk returns 200 for alice | Integration | `AnalyticsControllerIntegrationTest#risk_endpoint_returns200_alice` | Wave 0 |
| RISK-02 | GET /api/portfolio/correlation tickers array matches portfolio holdings | Integration | `AnalyticsControllerIntegrationTest#correlation_endpoint_tickersMatchHoldings` | Wave 0 |
| ATTR-01 | GET /api/portfolio/attribution response has alphaAnnualized, betaMkt, rSquared | Integration | `AnalyticsControllerIntegrationTest#attribution_endpoint_hasRequiredFields` | Wave 0 |

### Wave 0 Gaps

- [ ] `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java` — unit tests for pure math (no Spring context needed)
- [ ] `backend/src/test/java/com/quantlens/analytics/FamaFrenchCalculatorTest.java`
- [ ] `backend/src/test/java/com/quantlens/analytics/CointegrationScannerTest.java`
- [ ] `backend/src/test/java/com/quantlens/analytics/AnalyticsGoldenValuePrinterTest.java` — `@Disabled` printer
- [ ] `backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java` — extends `AbstractPostgresIntegrationTest`
- [ ] `frontend/src/__tests__/components/RiskScorecard.test.ts`
- [ ] `frontend/src/__tests__/components/CorrelationHeatmap.test.ts`
- [ ] `frontend/src/__tests__/components/AttributionChart.test.ts`
- [ ] `frontend/src/__tests__/components/PairsTable.test.ts`

### Sampling Rate

- **Per task commit:** `.\mvnw.cmd test -Dtest=RiskCalculatorTest`
- **Per wave merge:** `.\mvnw.cmd test`
- **Phase gate:** Full suite + `npm run test:unit` green before `/gsd:verify-work`

---

## Environment Availability

No new external dependencies. All tools confirmed present:

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 (JAVA_HOME) | Maven build | Yes | Temurin 21.0.11 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` | — |
| Hipparchus 4.0.3 (both modules) | Statistics computation | Yes | In pom.xml, both `hipparchus-core` and `hipparchus-stat` | — |
| Docker | Testcontainers | Yes | 28.0.4 | — |
| Node 22 / npm 11 | Frontend build | Yes | Confirmed in CLAUDE.md | — |
| echarts 6.1.0 | ECharts heatmap | Yes | Confirmed `npm view echarts version = 6.1.0` | — |
| vue-echarts 8.0.1 | Vue chart wrapper | Yes | Confirmed `npm view vue-echarts version = 8.0.1` | — |

**Missing dependencies with no fallback:** none.

---

## State of the Art

| Old Approach | Current Approach | Impact |
|--------------|------------------|--------|
| Apache Commons Math (ACM) OLS | Hipparchus 4.0.3 (ACM successor, same API) | Drop-in; use `OLSMultipleLinearRegression` identical API |
| ADF from a dedicated library (statsmodels/R) | Hand-assembled from Hipparchus OLS + MacKinnon polynomial | Java ecosystem has no pre-built ADF; primitives are all present in Hipparchus |
| Simple returns for Sharpe | Log returns | Log returns are additive, consistent with GBM model; difference is negligible for daily data but convention matters |
| Annualize vol with 365 | Annualize with 252 (trading days) | Correct convention; 365 overstates by ~√(365/252) ≈ 1.20 |
| VaR without method label | VaR with explicit method + confidence + horizon labels | Required by CONTEXT.md and PITFALLS.md; non-negotiable for credibility |

**Deprecated/outdated:**
- `Covariance.covariance(x, y, boolean)` overload with `boolean biasCorrected` param: use `cov.covariance(x, y)` which defaults to sample (bias-corrected) covariance (n-1 denominator). [ASSUMED based on ACM/Hipparchus ancestor; verify at test time]

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | MacKinnon 2010 p-value constants extracted from statsmodels: tau_star=-1.61, tau_min=-18.83, tau_max=2.74, small-p coeffs=[2.1659, 1.4412, 0.038269], large-p coeffs=[1.7339, 0.93202, -0.12745, -0.010368] | Cointegration: P-value approximation | Wrong p-values displayed; verify by checking `mackinnonp(-3.0, "c", 1) ≈ 0.034` |
| A2 | `OLSMultipleLinearRegression` params[0] is always the intercept when `noIntercept=false` (default) | FF regression and ADF | All betas would be off by one index |
| A3 | `DescriptiveStatistics.getStandardDeviation()` uses n-1 (sample) denominator | Sharpe, vol, beta | Vol would be systematically ~0.1% too low if n denominator used |
| A4 | `Covariance.covariance(x,y)` (no third arg) returns sample covariance (n-1 denominator) | Beta calculation | Beta would be biased for small n; negligible for n=503 but convention matters |
| A5 | Factor row index i+1 in factorRows aligns with return index i (return from day[i] to day[i+1]) | FF regression date alignment | Regression regresses returns on the wrong (lagged) factor values |
| A6 | ECharts 6.1.0 heatmap series `data` format is `[xIndex, yIndex, value]` | CorrelationHeatmap.vue | Heatmap renders rotated or with wrong cell positions |
| A7 | `PortfolioService.buildEquityCurve` can be replicated in analytics module by loading same bars from OhlcvBarRepository | Architecture pattern | Module dependency complexity if wrong; use portfolio::domain named interface instead |
| A8 | Engle-Granger critical values appropriate for demo: use standard MacKinnon 1-variable "c" values (−3.43/−2.86/−2.57) rather than 2-variable cointegration-residual values (−3.96/−3.37/−3.07) | Cointegration critical values | Test is anti-conservative; accepts pairs that a strict test would reject; acceptable for synthetic demo data |

---

## Open Questions (RESOLVED)

1. **Should `portfolio::domain` or `portfolio::service` be the named interface for analytics?**
   - What we know: `portfolio::domain` is already a named interface (entities + repos). Using it directly from analytics avoids adding `@NamedInterface("service")` to `portfolio/service/package-info.java`.
   - What's unclear: Whether the analytics module needs any service-layer helpers from PortfolioService (answer: no — the equity curve logic is short enough to replicate).
   - Recommendation: analytics module depends only on `portfolio::domain` + `marketdata::domain`. No Phase 2 code changes.

2. **Should CVaR (Expected Shortfall) be included in the VaR response?**
   - What we know: CONTEXT.md says "optionally CVaR/ES as a bonus column". The formula is documented above.
   - Recommendation: Include as a third `VarResultDto` entry with `method="CVaR_HISTORICAL"`. Only 5 lines of extra code; adds demonstrable sophistication.

3. **Which t-distribution to use for the standard ADF p-value (as a sanity check)?**
   - The ADF statistic follows the Dickey-Fuller distribution, NOT a standard t-distribution. The TDistribution p-value is NOT correct for ADF and must NOT be used. Use the MacKinnon polynomial approximation exclusively.
   - Include a comment in `CointegrationScanner.java`: `// WARNING: Do NOT use TDistribution.cumulativeProbability here. ADF follows the Dickey-Fuller distribution. Use mackinnonPValue().`

---

## Sources

### Primary (HIGH confidence)
- Hipparchus 4.0.3 Javadocs — `OLSMultipleLinearRegression`, `PearsonsCorrelation`, `DescriptiveStatistics`, `NormalDistribution` API shapes [CITED: https://hipparchus.org/apidocs/]
- Codebase: `GbmGenerator.java` — exact seed parameters, factor generation RNG, daily RF = 0.04/252 [VERIFIED: codebase]
- Codebase: `SeedRunner.java` — Alice's portfolio specs, buy at bar 50, partial sell at bar 200, final quantities [VERIFIED: codebase]
- Codebase: `FactorReturn.java` — entity field names `mktRf`, `smb`, `hml`, `rf` [VERIFIED: codebase]
- Codebase: `pom.xml` — `hipparchus-core:4.0.3` and `hipparchus-stat:4.0.3` already present [VERIFIED: codebase]
- Codebase: `PortfolioService.java` — `buildEquityCurve`, `latestCloseBySecurityId` helpers [VERIFIED: codebase]
- Codebase: `DashboardView.vue` — Phase 3 SlotPlaceholder slot labels and grid positions [VERIFIED: codebase]
- Codebase: `portfolio.ts` store — `AsyncState` pattern, `refreshAll`, `$reset` [VERIFIED: codebase]
- Codebase: `echarts-theme.ts` — visualMap color array already defined [VERIFIED: codebase]

### Secondary (MEDIUM confidence)
- MacKinnon 2010 ADF critical values and p-value polynomial coefficients [CITED: statsmodels/tsa/adfvalues.py extracted constants; see github.com/statsmodels/statsmodels]
- Sharpe ratio annualization: 252 trading days convention [CITED: Lo (2002) "The Statistics of Sharpe Ratios", Financial Analysts Journal — also in PITFALLS.md]
- Fama-French 3-factor model specification: excess return regression on Mkt-RF, SMB, HML [CITED: Fama & French (1993) "Common risk factors in the returns on stocks and bonds", Journal of Financial Economics]
- Engle-Granger 2-step cointegration procedure [CITED: Engle & Granger (1987) "Co-integration and error correction", Econometrica; PITFALLS.md Pitfall 7]
- VaR parametric formula: `-(μ - z×σ) × value` [CITED: PITFALLS.md Pitfall 3; standard textbook formula]

### Tertiary (LOW confidence)
- MacKinnon (1996) vs MacKinnon (2010) distinction: statsmodels uses 2010 updated tables. The exact paper confirming the specific polynomial coefficient values has not been directly verified from the primary publication — only from the statsmodels implementation.

---

## Metadata

**Confidence breakdown:**
- Standard stack / APIs: HIGH — both Hipparchus modules confirmed in pom.xml; API signatures from Javadocs
- Financial math formulas (Sharpe, vol, drawdown, beta, VaR, FF): HIGH — textbook-grade formulas, cross-checked against PITFALLS.md and CONTEXT.md
- Fama-French alignment logic: HIGH — directly derived from GbmGenerator source code
- ADF construction: HIGH for OLS structure, MEDIUM for MacKinnon p-value constants (statsmodels source, not primary paper)
- ECharts heatmap option shape: MEDIUM — pattern is correct per ECharts 5/6 docs but exact API details marked [ASSUMED] for ECharts 6.1.0
- Architecture (new analytics module): HIGH — follows Spring Modulith patterns established in Phase 1

**Research date:** 2026-06-08
**Valid until:** Stable indefinitely (tied to locked seed=42 and Hipparchus 4.0.3 API, both frozen for this project).
