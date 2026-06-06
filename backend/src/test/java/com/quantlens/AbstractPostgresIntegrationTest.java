package com.quantlens;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared Testcontainers base class for all integration tests.
 * <p>
 * Starts a {@code pgvector/pgvector:pg16} container and exposes it via
 * {@code @ServiceConnection} so Spring Boot auto-wires the datasource from the
 * container's JDBC URL — no hardcoded connection strings needed.
 * <p>
 * The {@code vector} and {@code uuid-ossp} extensions are created in
 * {@link #initExtensions()} before the Spring context starts, so Flyway
 * migrations and the vector_store schema can initialize correctly.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractPostgresIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                    .withDatabaseName("quantlens_test")
                    .withUsername("quantlens")
                    .withPassword("quantlens_test");

    @BeforeAll
    static void initExtensions() throws Exception {
        // Ensure the vector and uuid-ossp extensions exist before Spring context starts.
        // The container's postgres superuser can create extensions; the app user cannot.
        POSTGRES.execInContainer(
                "psql", "-U", "quantlens", "-d", "quantlens_test",
                "-c", "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
        );
    }
}
