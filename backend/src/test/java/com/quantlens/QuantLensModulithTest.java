package com.quantlens;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

/**
 * Spring Modulith architecture verification test.
 * <p>
 * Verifies that the declared package boundaries form a valid modular structure:
 * no illegal cross-module references and no cycles. This test passes once the
 * module packages (marketdata, portfolio, security, seed) exist — it does NOT
 * require a running Spring context or database connection.
 */
class QuantLensModulithTest {

    @Test
    void applicationModulesShouldBeValid() {
        ApplicationModules.of(QuantLensApplication.class).verify();
    }
}
