package com.quantlens.mcp.tools;

import java.math.BigDecimal;

/**
 * MCP tool result for get_position_detail.
 * <p>
 * Concrete record — no Spring/JPA annotations, no interface implementation.
 * Required for reliable JSON schema generation by the MCP annotation scanner.
 * Fields are a direct subset of HoldingDto, populated from getHoldings() filtered by ticker.
 */
public record PositionDetailResult(
        String ticker,
        String name,
        String sector,
        BigDecimal quantity,
        BigDecimal avgCostBasis,
        BigDecimal currentPrice,
        BigDecimal currentMarketValue,
        BigDecimal portfolioWeight,
        BigDecimal unrealizedPnlAbs,
        BigDecimal unrealizedPnlPct
) {}
