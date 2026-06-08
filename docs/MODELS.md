# Stochastic Forecast Models

This document explains the four models available in the Monte Carlo fan chart on the QuantLens dashboard. Each model simulates thousands of potential future paths for the portfolio's value and summarises those paths into five percentile bands (p5, p25, p50, p75, p95) plus a median line.

## How to Read the Fan Chart

The fan chart shows 252 trading-day (one-year) forward projections. Each day has a range from the 5th to the 95th percentile of 5,000 simulated paths:

- **Narrow part near day 1** — the near future is more certain; paths haven't diverged yet.
- **Widening shape toward the right** — uncertainty compounds over time.
- **Dark blue line** — the median (50th percentile): half of simulated paths end above this, half below.
- **Darker inner band (p25–p75)** — the interquartile range; the "middle half" of outcomes.
- **Lighter outer bands (p5–p25 and p75–p95)** — the tails; extreme but plausible outcomes.

> **Important:** These are potential futures, not predictions. The models describe the range of outcomes *consistent with historical data*, not a forecast of what *will* happen. Treat the fan chart as a stress-test and scenario tool, not a price target.

---

## Model 1: Geometric Brownian Motion (GBM)

### What it is
GBM is the classic textbook model for equity prices. The portfolio value evolves as a random walk in log-space, meaning daily log-returns are drawn independently from a normal distribution with constant drift and volatility.

### Why it's included
GBM is the industry baseline — transparent, well-understood, and easy to explain. It underlies the Black-Scholes option pricing framework and is the starting point for any quantitative finance discussion. It gives a clean, symmetric fan that is easy to compare against the other three models.

### Key assumptions
- Daily log-returns are independent and identically distributed (no memory).
- Drift (μ) and volatility (σ) are constant over the forecast horizon.
- Returns are normally distributed — no fat tails, no skewness.

### Parameters used
- **μ** — annualised mean daily log-return, calibrated from the portfolio's 2-year price history.
- **σ** — annualised standard deviation of log-returns from the same window.

The simulation uses the Ito-correct formulation: the drift in log-space is `(μ − σ²/2)`, not `μ`. Without this correction, the median would drift upward at a mathematically incorrect rate. The expected portfolio value under GBM is `S₀ · exp(μ · t)`.

### Known limitations
- No jumps: sudden large drops (earnings shocks, macro events) are impossible.
- Constant volatility: real markets have volatility clustering — quiet periods followed by turbulent periods.
- Fat tails: rare large moves occur far more often in real markets than a normal distribution predicts.
- Drift estimation noise: the standard error of the annualised drift estimated from 2 years of daily data is approximately `σ / √504`, which is similar in magnitude to the estimate itself. The fan chart width is more reliable than the absolute level.

---

## Model 2: Merton Jump-Diffusion

### What it is
Merton's jump-diffusion model adds a compound Poisson jump process on top of GBM. Between jumps the portfolio follows the same log-normal dynamics as GBM. At random times (controlled by a jump intensity λ), a discrete shock — drawn from a log-normal distribution — is applied to the portfolio value instantaneously.

### Why it's included
Equity portfolios experience sudden, discontinuous drops due to earnings surprises, geopolitical events, or sector-wide shocks. GBM cannot produce these — it only generates paths that change continuously. Merton captures the left-tail skew that practitioners observe and that makes worst-case scenarios fatter than a pure normal model implies.

### Key assumptions
- Jumps arrive as a Poisson process with constant intensity λ (expected jumps per year).
- Each jump size is log-normally distributed with mean log-jump `μ_J` and standard deviation `σ_J`.
- The continuous diffusion component (σ) and calibrated drift (μ) are shared with GBM.

### Parameters used
- **σ** — calibrated from portfolio history (same as GBM).
- **λ = 0.10** (approximately one large jump per 10 years on average) — **illustrative**.
- **μ_J = −0.10** (mean log-jump of −10%, representing a downward shock) — **illustrative**.
- **σ_J = 0.15** (jump-magnitude standard deviation of 15%) — **illustrative**.

> These jump parameters are **illustrative equity stress values**, not calibrated to current option prices. Calibrating jump parameters rigorously requires an implied-volatility surface from traded options, which this demo does not use. The values are chosen to be plausible for a diversified equity portfolio.

### Known limitations
- Jump parameters are illustrative, not calibrated to live market data.
- Jump intensity is assumed constant; in reality jumps cluster during crises.
- Model does not distinguish between idiosyncratic and systematic jump sources.

---

## Model 3: Heston Stochastic Volatility

### What it is
The Heston model extends GBM by making the volatility itself random. The variance (σ²) follows a mean-reverting CIR (Cox-Ingersoll-Ross) process: when volatility is high it tends to revert toward a long-run average level θ; when it is low it tends to rise back. The asset and variance processes are correlated with coefficient ρ (the "leverage effect": assets tend to fall when volatility spikes).

### Why it's included
Volatility clustering is one of the most robust empirical features of equity returns — tranquil periods are followed by turbulent periods, and back again. Heston captures this and produces a non-symmetric smile in the implied volatility surface. It is the first-generation stochastic-volatility model and a standard reference point in quantitative finance.

### Key assumptions
- Asset price follows: `dS = μ S dt + √V · S dW₁`
- Variance follows: `dV = κ(θ − V) dt + ξ √V dW₂`, where `dW₁ · dW₂ = ρ dt`
- Feller condition `2κθ > ξ²` ensures the variance process does not hit zero (enforced at startup: `2 × 2.0 × 0.04 = 0.16 > 0.09 = 0.3²`).

### Parameters used
| Parameter | Value | Meaning |
|-----------|-------|---------|
| κ | 2.0 | Mean-reversion speed |
| θ | 0.04 | Long-run variance = (20%)² |
| ξ | 0.3 | Vol-of-vol (volatility of the variance process) |
| ρ | −0.7 | Asset–variance correlation (leverage effect) |
| v₀ | 0.04 | Initial variance = calibrated σ² |

> All Heston parameters are **illustrative**. Properly calibrating the Heston model requires fitting implied-volatility surfaces from exchange-traded options — data this demo does not use. The parameters above are empirically plausible for an equity portfolio, but they are not market-calibrated. The dashboard displays a disclaimer when the Heston model is selected.

### Known limitations
- Parameters are illustrative, not calibrated to live option-market data.
- Euler-Maruyama discretisation of the CIR variance process has a known bias when ξ is large (here ξ = 0.3, which is moderate and within the acceptable range for daily discretisation).
- More computationally expensive than GBM or Merton (~150–250 ms per 5,000-path simulation versus ~50–100 ms for GBM).
- The leverage effect ρ = −0.7 is fixed; real portfolios exhibit time-varying correlation between returns and vol.

---

## Model 4: Historical Block Bootstrap

### What it is
The block bootstrap is a non-parametric approach: instead of fitting a parametric model, it resamples the actual observed daily log-returns from the portfolio's 2-year history to build forward paths. Returns are resampled in consecutive blocks of approximately 22 trading days (~1 month), rather than individually.

### Why it's included
Unlike the other three models, bootstrap makes **no distributional assumption**. It captures whatever statistical features were present in the actual data — fat tails, skewness, volatility clustering, non-linear dependencies — without requiring them to fit a specific parametric form. If the portfolio has historically exhibited unusual behaviour (sector concentration, leverage effects, regime shifts), those features appear directly in the bootstrap fan chart.

### Key assumptions
- The historical return series is stationary within each block (the underlying distribution does not change within a month).
- Past regimes are representative of future regimes — if markets enter an unprecedented regime (e.g., zero-interest-rate policy environment) not present in the 2-year history, the bootstrap cannot project it.
- Block boundaries are drawn independently (non-overlapping circular bootstrap variant).

### Parameters used
- All historical daily log-returns from the 2-year portfolio price history (~504 observations).
- **Block length L ≈ 22 trading days** (equal to `max(10, √504) ≈ 22`), chosen to preserve intra-month volatility clustering and short-run momentum.

### Known limitations
- Only generates paths that are combinations of past observations — rare events never seen in the 2-year window are invisible.
- Past regime may not represent future regime; a structural break makes historical resampling unreliable.
- Block length L = 22 is a rule-of-thumb (Politis & Romano 1994); optimal block length depends on the autocorrelation structure of the specific series.

---

## Appendix: Mathematical Notes

### Ito Correction for GBM

Under GBM, the log-price satisfies:

```
ln S(t) = ln S(0) + (μ − σ²/2) t + σ W(t)
```

The drift in log-space is `μ − σ²/2`, not `μ`. This is the Ito correction. Without it, the simulated expected portfolio value would drift at rate `exp((μ + σ²/2)t)` rather than the correct `exp(μt)`. For σ = 20%, the error compounds to approximately +2% per year.

The QuantLens implementation uses finmath-lib's `BlackScholesModel`, which applies the Ito correction by construction.

### Feller Condition for Heston

The CIR variance process `dV = κ(θ − V)dt + ξ√V dW₂` remains non-negative if and only if:

```
2κθ > ξ²
```

With κ = 2, θ = 0.04, ξ = 0.3: `2 × 2 × 0.04 = 0.16 > 0.09 = 0.09` — satisfied. The `FULL_TRUNCATION` scheme (from Heston 1993 implementation guidance) additionally clamps variance at 0 if numerical discretisation steps below it, providing a belt-and-suspenders guard.

### Block Bootstrap Block Length

The rule-of-thumb block length L ≈ √H (where H is the history length in days) balances two competing objectives:
- **Too short** (L = 1, i.i.d. resample): destroys volatility clustering and momentum, underestimating short-horizon tail risk.
- **Too long** (L = H): introduces high variance in the bootstrap estimate, overfitting to specific historical episodes.

For H = 504 (2 years), L = 22 trading days (~1 calendar month) preserves intra-month autocorrelation while keeping the bootstrap variance manageable.

### Determinism and Reproducibility

All four models use fixed random-number generator seed **42**. This means:
- Two calls to the same endpoint with the same model and portfolio data produce byte-identical results.
- Dashboard screenshots are stable.
- Changing the portfolio's underlying position data will change the calibrated μ and σ, which will change the simulated paths.
