/**
 * Public API (DTOs) of the Analytics module — exposes RiskScorecardDto, VarResultDto,
 * CorrelationMatrixDto, and related DTOs as a Spring Modulith named interface so the
 * mcp module can legally read and map the DTOs returned by RiskCalculator.
 * Phase 9: mcp module is the first cross-boundary consumer of these DTO types.
 */
@org.springframework.modulith.NamedInterface("api")
package com.quantlens.analytics.api;
