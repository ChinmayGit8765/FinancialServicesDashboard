package com.quantlens.analytics.api;

import java.math.BigDecimal;

/**
 * Value-at-Risk result for a single computation method.
 * <p>
 * Numeric convention: statistical quantities ({@code confidence}, {@code percentage})
 * are {@code double}; monetary values ({@code amount}) are {@code BigDecimal}.
 *
 * @param method      computation method — one of {@code "HISTORICAL"}, {@code "PARAMETRIC"},
 *                    {@code "CVaR_HISTORICAL"}
 * @param confidence  confidence level e.g. {@code 0.95} for 95% VaR
 * @param horizonDays holding period in trading days e.g. {@code 1} for 1-day VaR
 * @param amount      VaR as a POSITIVE monetary loss ({@code BigDecimal} for money)
 * @param percentage  VaR as a fraction of portfolio value e.g. {@code 0.018} = 1.8%
 *                    ({@code double} for statistics)
 */
public record VarResultDto(
        String method,
        double confidence,
        int horizonDays,
        BigDecimal amount,
        double percentage
) {}
