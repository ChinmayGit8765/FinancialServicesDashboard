package com.quantlens.mcp.tools;

import java.math.BigDecimal;

/**
 * MCP tool result for get_risk_metrics.
 * <p>
 * Concrete record — no Spring/JPA annotations, no interface implementation.
 * Required for reliable JSON schema generation by the MCP annotation scanner.
 * Fields map from RiskScorecardDto; VaR amounts extracted by method=="HISTORICAL"/"PARAMETRIC".
 */
public record RiskMetricsResult(
        double sharpeRatio,
        double annualizedVolatility,
        double maxDrawdown,
        double beta,
        BigDecimal historicalVar95,   // positive loss amount, method=="HISTORICAL"
        BigDecimal parametricVar95    // positive loss amount, method=="PARAMETRIC"
) {}
