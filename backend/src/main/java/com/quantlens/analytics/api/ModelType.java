package com.quantlens.analytics.api;

/**
 * Stochastic model for Monte Carlo portfolio forecasting (SIM-02).
 *
 * <h3>Usage as {@code @RequestParam}</h3>
 * <p>
 * Used as a typed {@code @RequestParam} in {@link ForecastController#getForecast}.
 * Spring MVC converts the query-parameter string to this enum by name (case-sensitive).
 * Unknown values produce a {@code 400 Bad Request} automatically via
 * {@code MethodArgumentTypeMismatchException} — no raw string switch needed.
 * This is the SIM-02 DoS/Tampering mitigation <strong>T-05-02</strong>: the model parameter
 * is validated at the framework level, so no attacker-supplied arbitrary string can reach
 * the simulation engine.
 * </p>
 *
 * <h3>Model descriptions</h3>
 * <dl>
 *   <dt>GBM</dt>
 *   <dd>Geometric Brownian Motion — calibrated drift μ and volatility σ from portfolio
 *       historical log returns. Uses finmath {@code BlackScholesModel} with Ito correction
 *       built in. Default model.</dd>
 *   <dt>JUMP_DIFFUSION</dt>
 *   <dd>Merton jump-diffusion — GBM plus a compound Poisson jump process.
 *       Illustrative jump parameters: λ=0.10, μ_J=−0.10, σ_J=0.15.
 *       Uses finmath {@code MonteCarloMertonModel}.</dd>
 *   <dt>HESTON</dt>
 *   <dd>Heston stochastic-volatility model — variance follows a CIR mean-reverting
 *       process. Illustrative parameters (not calibrated): κ=2.0, θ=0.04, ξ=0.3, ρ=−0.7.
 *       Feller condition 2κθ&gt;ξ² is enforced at startup. Uses finmath {@code HestonModel}
 *       with {@code FULL_TRUNCATION} scheme.</dd>
 *   <dt>BOOTSTRAP</dt>
 *   <dd>Historical block bootstrap — resamples blocks of observed daily log returns
 *       (block length ≈ √H trading days) to preserve volatility clustering.
 *       Non-parametric; no distributional assumption. Pure Java, no finmath dependency.</dd>
 * </dl>
 */
public enum ModelType {
    GBM,
    JUMP_DIFFUSION,
    HESTON,
    BOOTSTRAP
}
