package com.quantlens;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the springdoc OpenAPI spec (Phase 10, DOCS-01 / SC-2).
 * <p>
 * Proves the spec is (1) publicly reachable without authentication (permitAll), (2) documents the
 * portfolio, analytics, and AI endpoints, and (3) leaks no key/secret field names — the T-10-01
 * schema-hygiene gate extending the {@code KeyLeakageIntegrationTest} no-leak principle to the
 * generated OpenAPI document.
 */
class OpenApiDocsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void apiDocs_isPubliclyAccessible() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode())
                .as("GET /v3/api-docs must return 200 WITHOUT authentication (permitAll public docs)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("OpenAPI document must be present")
                .contains("\"openapi\"");
    }

    @Test
    void apiDocs_exposesPortfolioAnalyticsAiPaths() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("OpenAPI spec must document portfolio, analytics, and AI endpoints")
                .contains("/api/portfolio")
                .contains("/api/portfolio/risk")
                .contains("/api/ai/");
    }

    @Test
    void apiDocs_doesNotLeakKeyFields() {
        ResponseEntity<String> response = restTemplate.getForEntity("/v3/api-docs", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        // T-10-01 schema hygiene. NOTE: the bare substring "apiKey" is deliberately NOT forbidden —
        // it is an OpenAPI-reserved security-scheme type keyword ("type":"apiKey" for the session
        // cookie) and would be a false positive. The real invariant is that the BYO-key INTAKE
        // surface is not published at all: the @Hidden POST/DELETE /api/ai/key endpoints remove the
        // AiKeyRequest schema (whose field carries the user's key) from the spec.
        assertThat(response.getBody())
                .as("OpenAPI spec must NOT publish the key-intake DTO or any internal key/password field (T-10-01)")
                .doesNotContain("AiKeyRequest")
                .doesNotContain("llmKey")
                .doesNotContain("\"password\"")
                .doesNotContain("passwordHint")   // PersonaDto.passwordHint is @Schema(hidden=true)
                .doesNotContain("/actuator")       // springdoc.show-actuator=false (IN-03)
                .doesNotContain("sk-ant-")
                .doesNotContain("sk-proj-");
    }
}
