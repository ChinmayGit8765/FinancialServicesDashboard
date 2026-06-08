package com.quantlens.analytics.api;

import java.util.List;

/**
 * Risk scorecard for a portfolio.
 * <p>
 * All statistical quantities are {@code double}. Monetary values are inside
 * the nested {@link VarResultDto#amount()} which is {@code BigDecimal}.
 *
 * @param sharpeRatio          annualized Sharpe ratio (excess return / std × √252)
 * @param annualizedVolatility annualized volatility (std of daily log returns × √252)
 * @param maxDrawdown          largest peak-to-trough decline as a negative fraction
 *                             e.g. {@code -0.18} = 18% drawdown from peak
 * @param beta                 portfolio beta vs SPX500 benchmark
 *                             (cov(r_port, r_bench) / var(r_bench))
 * @param var                  2–3 VaR entries: HISTORICAL, PARAMETRIC, optionally CVaR_HISTORICAL
 */
public record RiskScorecardDto(
        double sharpeRatio,
        double annualizedVolatility,
        double maxDrawdown,
        double beta,
        List<VarResultDto> var
) {}
