package com.quantlens.infra;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the pgvector vector_store schema (DATA-03).
 * <p>
 * RED scaffold — turns green in Plan 02 when Flyway migration creates the vector_store table.
 * <p>
 * Asserts:
 * <ul>
 *   <li>A {@code vector_store} table exists in the public schema</li>
 *   <li>Its {@code embedding} column has the pgvector type with dimension 1536</li>
 * </ul>
 */
@Disabled("RED — turns green in Plan 02 when Flyway migration creates the vector_store table")
class VectorStoreSchemaTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void vectorStoreTableExists() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables " +
                "WHERE table_schema = 'public' AND table_name = 'vector_store'",
                Long.class);
        assertThat(count).as("vector_store table should exist").isEqualTo(1L);
    }

    @Test
    void embeddingColumnIsVector1536() {
        // Query pg_attribute to check the column type and dimensions
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_attribute pa " +
                "JOIN pg_class pc ON pa.attrelid = pc.oid " +
                "JOIN pg_type pt ON pa.atttypid = pt.oid " +
                "WHERE pc.relname = 'vector_store' " +
                "  AND pa.attname = 'embedding' " +
                "  AND pt.typname = 'vector' " +
                "  AND pa.atttypmod = 1536",
                Long.class);
        assertThat(count)
                .as("vector_store.embedding should be type vector with dimension 1536")
                .isEqualTo(1L);
    }
}
