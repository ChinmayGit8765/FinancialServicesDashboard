package com.quantlens.mcp.tools;

import java.math.BigDecimal;
import java.util.List;

/**
 * MCP tool result for get_portfolio_summary.
 * <p>
 * Concrete record — no Spring/JPA annotations, no interface implementation.
 * Required for reliable JSON schema generation by the MCP annotation scanner.
 * Fields mirror PortfolioPnlDto plus a flat sector-allocation list.
 */
public record PortfolioSummaryResult(
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedGainAbs,
        BigDecimal totalUnrealizedGainPct,
        BigDecimal dailyChangeAbs,
        BigDecimal dailyChangePct,
        List<AllocationEntry> allocation
) {

    /**
     * Sector allocation entry — nested concrete record, no interface.
     */
    public record AllocationEntry(
            String sector,
            BigDecimal weight,
            BigDecimal marketValue
    ) {}
}
