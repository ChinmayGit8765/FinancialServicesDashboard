/**
 * Public API (DTOs) of the Portfolio module — exposes HoldingDto, AllocationSliceDto,
 * PortfolioPnlDto, DateValueDto, and PortfolioController as a Spring Modulith named
 * interface so the mcp module can legally read and map the DTOs returned by PortfolioService.
 * Phase 9: mcp module is the first cross-boundary consumer of these DTO types.
 */
@org.springframework.modulith.NamedInterface("api")
package com.quantlens.portfolio.api;
