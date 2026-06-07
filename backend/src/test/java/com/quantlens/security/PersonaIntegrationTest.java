package com.quantlens.security;

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
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests proving AUTH-02 persona scoping.
 * <p>
 * RED scaffold — turns green in Plan 03 when SecurityConfig, AppUser entity,
 * Portfolio entity, and persona-scoping are implemented.
 * <p>
 * Tests:
 * <ul>
 *   <li>Each of alice, bob, and charlie resolves a distinct portfolio identity</li>
 *   <li>The portfolio ID / persona returned for each user is different</li>
 *   <li>Session cookie from one persona login does not expose another's portfolio</li>
 * </ul>
 */
class PersonaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void aliceBobCharlieHaveDistinctPortfolioIdentities() throws Exception {
        String alicePortfolioId = getPortfolioIdForUser("alice");
        String bobPortfolioId   = getPortfolioIdForUser("bob");
        String charliePortfolioId = getPortfolioIdForUser("charlie");

        assertThat(alicePortfolioId)
                .as("alice and bob should have distinct portfolios")
                .isNotEqualTo(bobPortfolioId);
        assertThat(alicePortfolioId)
                .as("alice and charlie should have distinct portfolios")
                .isNotEqualTo(charliePortfolioId);
        assertThat(bobPortfolioId)
                .as("bob and charlie should have distinct portfolios")
                .isNotEqualTo(charliePortfolioId);
    }

    @Test
    void alicePersonaIsGrowth() throws Exception {
        String persona = getPersonaForUser("alice");
        assertThat(persona).as("alice's persona should be Growth").containsIgnoringCase("growth");
    }

    @Test
    void bobPersonaIsIncome() throws Exception {
        String persona = getPersonaForUser("bob");
        assertThat(persona).as("bob's persona should be Income").containsIgnoringCase("income");
    }

    @Test
    void charliePersonaIsBalanced() throws Exception {
        String persona = getPersonaForUser("charlie");
        assertThat(persona).as("charlie's persona should be Balanced").containsIgnoringCase("balanced");
    }

    // --- Helpers ---

    private String loginAndGetSessionCookie(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", username);
        body.add("password", "demo1234");

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                String.class);

        assertThat(response.getStatusCode())
                .as("Login for %s should succeed", username)
                .isEqualTo(HttpStatus.OK);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private String getMeResponse(String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/me",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private String getPortfolioIdForUser(String username) throws Exception {
        String cookie = loginAndGetSessionCookie(username);
        String meBody = getMeResponse(cookie);
        // Expected JSON: {"username":"alice","persona":"Growth","portfolioId":1,...}
        // Extract portfolioId — in Plan 03 this will be a real field
        assertThat(meBody).as("me response should contain portfolioId").contains("portfolioId");
        return extractJsonField(meBody, "portfolioId");
    }

    private String getPersonaForUser(String username) throws Exception {
        String cookie = loginAndGetSessionCookie(username);
        String meBody = getMeResponse(cookie);
        assertThat(meBody).as("me response should contain persona").contains("persona");
        return extractJsonField(meBody, "persona");
    }

    /**
     * Robust JSON field extraction using Jackson ObjectMapper (WR-07).
     * Returns the field value as a string — numeric fields are returned as their
     * string representation (e.g., {@code "3"} for {@code portfolioId:3}).
     */
    private String extractJsonField(String json, String fieldName) throws Exception {
        com.fasterxml.jackson.databind.JsonNode node =
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
        assertThat(node.has(fieldName))
                .as("field '%s' not found in JSON: %s", fieldName, json)
                .isTrue();
        return node.path(fieldName).asText();
    }
}
