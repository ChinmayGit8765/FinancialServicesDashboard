/**
 * Service layer of the Analytics module — exposes RiskCalculator (and other analytics
 * services) as a Spring Modulith named interface so the mcp module can legally inject
 * and delegate to it.
 * Phase 9: mcp module is the first cross-boundary consumer of this service bean.
 */
@org.springframework.modulith.NamedInterface("service")
package com.quantlens.analytics.service;
