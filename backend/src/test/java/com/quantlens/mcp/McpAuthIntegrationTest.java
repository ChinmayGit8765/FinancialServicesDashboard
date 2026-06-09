package com.quantlens.mcp;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * HTTP-level auth gate for the product MCP server (T-09-01).
 * <p>
 * Proves three things at once: (1) the {@code spring-ai-starter-mcp-server-webmvc} starter
 * loaded and the application context booted; (2) {@code /mcp} is a live, mapped endpoint;
 * (3) Spring Security gates {@code /mcp} — an unauthenticated POST returns 401 BEFORE any
 * MCP tool can be dispatched. No tool is reachable without credentials.
 */
class McpAuthIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void mcp_withoutAuth_returns401() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // A minimal MCP JSON-RPC initialize-shaped body; auth must reject it before parsing.
        HttpEntity<String> request = new HttpEntity<>(
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"tools/list\"}", headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "/mcp", HttpMethod.POST, request, String.class);

        org.assertj.core.api.Assertions.assertThat(response.getStatusCode())
                .as("unauthenticated POST /mcp must return 401 — auth gate fires before tool dispatch")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
