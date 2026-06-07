package com.quantlens;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;

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
 * <p>
 * <strong>Container lifecycle:</strong> The container is started in a static
 * initializer block and kept alive for the entire JVM lifetime (until the Ryuk
 * resource reaper shuts it down on JVM exit).  This avoids the
 * {@code @Testcontainers} / {@code @Container static} pattern, where the JUnit 5
 * extension stops the container after each concrete test class finishes — which
 * kills the datasource held in Spring's cached {@code ApplicationContext} and
 * breaks any subsequent test class that tries to use it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractPostgresIntegrationTest {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("pgvector/pgvector:pg16")
                .withDatabaseName("quantlens_test")
                .withUsername("quantlens")
                .withPassword("quantlens_test");
        POSTGRES.start();
    }

    @BeforeAll
    static void initExtensions() throws Exception {
        // Ensure the vector and uuid-ossp extensions exist before the Spring context
        // starts. In the pgvector/pgvector:pg16 Testcontainers setup the POSTGRES_USER
        // ("quantlens") is itself the database superuser (the image grants Superuser when
        // POSTGRES_USER is set), so we run as "quantlens" here — not "postgres", which
        // does not exist when a custom POSTGRES_USER is specified.
        // The ExecResult exit code is checked to give a clear failure message if the
        // extension creation ever fails (CR-03: previously the result was silently discarded).
        org.testcontainers.containers.Container.ExecResult result =
                POSTGRES.execInContainer(
                        "psql", "-U", "quantlens", "-d", "quantlens_test",
                        "-c", "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
                );
        if (result.getExitCode() != 0) {
            throw new IllegalStateException(
                    "Failed to create extensions: " + result.getStderr());
        }
    }
}
