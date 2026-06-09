/**
 * Spring Modulith module boundary for the MCP server module.
 * <p>
 * This module is a thin protocol adapter: it translates Spring AI MCP tool invocations
 * into delegation calls on the existing portfolio and analytics service beans.
 * No business logic is recomputed here.
 * <p>
 * Allowed cross-module dependencies:
 * <ul>
 *   <li>{@code portfolio::service} — PortfolioService (getHoldings, getAllocation, getPortfolioPnl)</li>
 *   <li>{@code portfolio::api} — HoldingDto, AllocationSliceDto, PortfolioPnlDto, DateValueDto</li>
 *   <li>{@code portfolio::domain} — PortfolioRepository.findPortfolioIdByUsername (principal resolution)</li>
 *   <li>{@code analytics::service} — RiskCalculator.computeRiskScorecard</li>
 *   <li>{@code analytics::api} — RiskScorecardDto, VarResultDto</li>
 * </ul>
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "MCP",
        allowedDependencies = {
                "portfolio::service",   // PortfolioService delegation
                "portfolio::api",       // DTOs returned by PortfolioService
                "portfolio::domain",    // PortfolioRepository.findPortfolioIdByUsername
                "analytics::service",   // RiskCalculator delegation
                "analytics::api"        // DTOs returned by RiskCalculator
        })
package com.quantlens.mcp;
