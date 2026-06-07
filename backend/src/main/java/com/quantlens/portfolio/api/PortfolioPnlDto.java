package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO returned by {@code GET /api/portfolio/pnl}.
 * <p>
 * Provides portfolio-level P&amp;L summary scalars plus a full equity curve.
 * The equity curve uses the constant-current-holdings assumption: current position
 * quantities valued across every historical trading day in the seeded window.
 * <p>
 * All monetary fields use {@link BigDecimal}; Jackson serialises them as JSON
 * numbers for direct ECharts binding.
 *
 * @param totalMarketValue       Σ(qty × latestClose) across all positions — scale 2
 * @param totalCostBasis         Σ(qty × avgCostBasis) across all positions — scale 2
 * @param totalUnrealizedGainAbs totalMarketValue − totalCostBasis — scale 2
 * @param totalUnrealizedGainPct totalUnrealizedGainAbs / totalCostBasis — scale 6 (0..∞)
 * @param dailyChangeAbs         equityCurve[last].value − equityCurve[last−1].value — scale 2
 * @param dailyChangePct         dailyChangeAbs / equityCurve[last−1].value — scale 6
 * @param equityCurve            504-entry constant-current-holdings curve; each entry is a
 *                               {@link DateValueDto} with the trading date and portfolio value
 */
public record PortfolioPnlDto(
        BigDecimal totalMarketValue,
        BigDecimal totalCostBasis,
        BigDecimal totalUnrealizedGainAbs,
        BigDecimal totalUnrealizedGainPct,
        BigDecimal dailyChangeAbs,
        BigDecimal dailyChangePct,
        List<DateValueDto> equityCurve
) {}
