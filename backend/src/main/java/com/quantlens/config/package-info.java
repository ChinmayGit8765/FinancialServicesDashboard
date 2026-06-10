/**
 * Application-wide infrastructure configuration (Phase 10).
 * <p>
 * Standalone Spring Modulith module — holds cross-cutting @Configuration beans (OpenAPI/springdoc
 * metadata) that depend only on external libraries, never on other QuantLens modules. Declared
 * explicitly (empty allowedDependencies) so the module-boundary contract and generated diagram
 * stay accurate and the module reads as intentional, not accidental.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Config",
        allowedDependencies = {})
package com.quantlens.config;
