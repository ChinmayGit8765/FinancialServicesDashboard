package com.quantlens.portfolio.api;

import java.math.BigDecimal;

/**
 * DTO returned by {@code GET /api/portfolio/holdings} — one entry per position.
 * <p>
 * All monetary fields use {@link BigDecimal}; Jackson serialises them as JSON
 * numbers (not strings) for direct ECharts binding. No {@code @JsonFormat} annotations
 * are present — BigDecimal serialises as a JSON number by default in Spring Boot.
 *
 * @param ticker             security ticker symbol
 * @param name               security full name
 * @param sector             sector classification (e.g. "Technology")
 * @param quantity           shares held — NUMERIC(18,4), scale 4
 * @param avgCostBasis       average cost per share at acquisition — scale 6
 * @param currentPrice       latest OHLCV close price — scale 6
 * @param currentMarketValue qty × currentPrice — scale 2 (display money)
 * @param portfolioWeight    positionValue / totalPortfolioValue — scale 6 (0..1)
 * @param unrealizedPnlAbs   (currentPrice − avgCostBasis) × qty — scale 2
 * @param unrealizedPnlPct   unrealizedPnlAbs / (avgCostBasis × qty) — scale 6
 */
public record HoldingDto(
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
