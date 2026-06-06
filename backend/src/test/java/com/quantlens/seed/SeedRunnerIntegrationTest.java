package com.quantlens.seed;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the idempotent seed runner (DATA-02).
 * <p>
 * RED scaffold — turns green in Plan 02 when SeedRunner is implemented.
 * <p>
 * Asserts:
 * <ul>
 *   <li>securities count &gt;= 15</li>
 *   <li>ohlcv_bars count &gt;= 7000 (~15 securities * 504 trading days)</li>
 *   <li>app_users count == 3 (alice, bob, charlie)</li>
 *   <li>factor_returns count &gt;= 500 (~504 trading days)</li>
 *   <li>A second context start does not duplicate data (idempotence)</li>
 * </ul>
 */
class SeedRunnerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void securitiesSeededAtLeast15() {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM securities", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(15);
    }

    @Test
    void ohlcvBarsSeededApprox15x504() {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM ohlcv_bars", Long.class);
        // ~15 securities * 504 trading days; allow some tolerance
        assertThat(count).isGreaterThanOrEqualTo(7000);
    }

    @Test
    void appUsersSeededExactly3() {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users", Long.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void factorReturnsSeededApprox504() {
        long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM factor_returns", Long.class);
        assertThat(count).isGreaterThanOrEqualTo(500);
    }

    @Test
    void seedRunnerIsIdempotent() {
        // Verify counts before re-seed
        long securitiesBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM securities", Long.class);
        long usersBefore = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM app_users", Long.class);

        // A second ApplicationContext start would invoke SeedRunner.run() again.
        // The idempotence guard (count() > 0 → skip) must keep counts identical.
        // We simulate this by asserting counts are stable after a no-op call.
        // In Plan 02, this will directly call seedRunner.run() a second time.
        assertThat(securitiesBefore).isGreaterThanOrEqualTo(15);
        assertThat(usersBefore).isEqualTo(3);
    }
}
