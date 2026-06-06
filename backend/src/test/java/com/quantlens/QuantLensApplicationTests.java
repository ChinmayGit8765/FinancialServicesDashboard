package com.quantlens;

import org.junit.jupiter.api.Test;

/**
 * Smoke test: verifies the Spring application context loads successfully
 * against a real Postgres Testcontainer (same container shared by all
 * integration test classes via {@link AbstractPostgresIntegrationTest}).
 */
class QuantLensApplicationTests extends AbstractPostgresIntegrationTest {

	@Test
	void contextLoads() {
	}

}
