package com.quantlens.analytics.api;

/**
 * Forecast result for a portfolio over a forward horizon.
 * <p>
 * All band values are {@code double} (chart-ready; no monetary precision needed for
 * percentile fan-chart rendering). The arrays have length equal to {@code horizonDays}.
 * <p>
 * The percentile bands are computed from 5000 Monte Carlo paths and extracted via
 * nearest-rank method. For GBM, {@code p50[t]} ≈ S₀·exp((μ−σ²/2)·t/252) — the
 * Ito-correct log-normal median (not the mean).
 *
 * @param model       the stochastic model used for simulation
 * @param horizonDays number of trading days projected forward (1–504); clamped at controller
 * @param p5          5th-percentile portfolio value at each step (array length = horizonDays)
 * @param p25         25th-percentile portfolio value at each step
 * @param p50         median (50th percentile) — Ito-correct log-normal median for GBM
 * @param p75         75th-percentile portfolio value at each step
 * @param p95         95th-percentile portfolio value at each step
 */
public record ForecastDto(
        ModelType model,
        int       horizonDays,
        double[]  p5,
        double[]  p25,
        double[]  p50,
        double[]  p75,
        double[]  p95
) {}
