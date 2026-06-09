/**
 * Service layer of the Portfolio module — exposes PortfolioService as a Spring Modulith
 * named interface so the mcp module can legally inject and delegate to it.
 * Phase 9: mcp module is the first cross-boundary consumer of this service bean.
 */
@org.springframework.modulith.NamedInterface("service")
package com.quantlens.portfolio.service;
