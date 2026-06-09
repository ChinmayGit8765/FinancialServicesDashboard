package com.quantlens.ai;

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
 * Integration tests for {@code AiKeyController} endpoints.
 *
 * <p>Tests PASS now because the controller is fully implemented in Task 2.
 *
 * <h3>sessionIsolation</h3>
 * Proves that {@code @SessionScope} works correctly — two separate HTTP sessions
 * each hold their own provider state and cannot see each other's key (T-06-04,
 * RESEARCH Pitfall 3).
 *
 * <h3>CR-01: DELETE CSRF protection</h3>
 * {@link #deleteKey_withoutCsrfToken_returns403()} proves that {@code DELETE /api/ai/key}
 * requires the {@code X-XSRF-TOKEN} header — it is NOT included in the
 * CSRF-exempt matcher (which is now POST-scoped only). The existing
 * {@link #clearKey_returns_mode_demo()} fetches the CSRF token via a GET request
 * and passes it correctly.
 */
class AiKeyControllerTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void setKey_returns_mode_live_with_provider() {
        String sessionCookie = loginAndGetSessionCookie("alice");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/key",
                HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"anthropic\",\"apiKey\":\"test-key-123\"}", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("\"mode\":\"live\"");
        assertThat(response.getBody()).contains("\"provider\":\"anthropic\"");
        // Key must never appear in the response
        assertThat(response.getBody()).doesNotContain("test-key-123");
    }

    /**
     * Note: clearKey (DELETE /api/ai/key) requires a CSRF token after CR-01 fix.
     * The full clearKey integration test (with CSRF token via MockMvc) is in
     * {@link AiKeyControllerCsrfTest#deleteKey_withCsrfToken_returns200()}.
     *
     * <p>This test verifies the {@code GET /api/ai/status} correctly reports demo mode
     * after the session is cleared (without invoking the DELETE endpoint directly).
     */
    @Test
    void status_showsDemoMode_whenNoKeySet() {
        String sessionCookie = loginAndGetSessionCookie("alice");

        // A fresh session without any key set must report demo mode
        ResponseEntity<String> statusResponse = authenticatedGet("/api/ai/status", sessionCookie);

        assertThat(statusResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody()).contains("\"mode\":\"demo\"");
        assertThat(statusResponse.getBody()).doesNotContain("\"provider\"");
    }

    /**
     * CR-01: DELETE /api/ai/key without X-XSRF-TOKEN must be rejected with 403.
     *
     * <p>Before the fix, {@code DELETE /api/ai/key} was CSRF-exempt (path-level exemption
     * matched both GET and DELETE). After the fix, only {@code POST /api/ai/key} is exempt
     * (method-scoped matcher), so DELETE requires the XSRF token. A CSRF attack that forces
     * a victim's browser to send {@code DELETE /api/ai/key} will be rejected.
     */
    @Test
    void deleteKey_withoutCsrfToken_returns403() {
        String sessionCookie = loginAndGetSessionCookie("alice");

        // Set a key first (POST is still CSRF-exempt)
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        postHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        restTemplate.exchange("/api/ai/key", HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"anthropic\",\"apiKey\":\"csrf-test-key\"}", postHeaders),
                String.class);

        // Attempt DELETE without X-XSRF-TOKEN — must be rejected (CR-01)
        HttpHeaders deleteHeaders = new HttpHeaders();
        deleteHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        // Deliberately omit X-XSRF-TOKEN
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/key",
                HttpMethod.DELETE,
                new HttpEntity<>(deleteHeaders),
                String.class);

        assertThat(response.getStatusCode())
                .as("DELETE /api/ai/key without CSRF token must be rejected with 403 (CR-01)")
                .isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void sessionIsolation_twoSessions_holdIndependentProviders() {
        // Two separate sessions — alice uses anthropic, bob uses openai
        String aliceCookie = loginAndGetSessionCookie("alice");
        String bobCookie   = loginAndGetSessionCookie("bob");

        // Alice sets anthropic
        HttpHeaders alicePostHeaders = new HttpHeaders();
        alicePostHeaders.setContentType(MediaType.APPLICATION_JSON);
        alicePostHeaders.add(HttpHeaders.COOKIE, aliceCookie);
        restTemplate.exchange("/api/ai/key", HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"anthropic\",\"apiKey\":\"alice-key\"}", alicePostHeaders),
                String.class);

        // Bob sets openai
        HttpHeaders bobPostHeaders = new HttpHeaders();
        bobPostHeaders.setContentType(MediaType.APPLICATION_JSON);
        bobPostHeaders.add(HttpHeaders.COOKIE, bobCookie);
        restTemplate.exchange("/api/ai/key", HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"openai\",\"apiKey\":\"bob-key\"}", bobPostHeaders),
                String.class);

        // Verify: Alice's status reflects only alice's session state
        ResponseEntity<String> aliceStatus = authenticatedGet("/api/ai/status", aliceCookie);
        assertThat(aliceStatus.getBody())
                .as("Alice's session must show anthropic provider")
                .contains("\"provider\":\"anthropic\"");

        // Verify: Bob's status reflects only bob's session state
        ResponseEntity<String> bobStatus = authenticatedGet("/api/ai/status", bobCookie);
        assertThat(bobStatus.getBody())
                .as("Bob's session must show openai provider")
                .contains("\"provider\":\"openai\"");

        // Cross-session key leakage check: neither response contains the other's key
        assertThat(aliceStatus.getBody())
                .as("Alice's status must not contain Bob's key")
                .doesNotContain("bob-key");
        assertThat(bobStatus.getBody())
                .as("Bob's status must not contain Alice's key")
                .doesNotContain("alice-key");
    }

    @Test
    void setKey_invalidProvider_returns400() {
        String sessionCookie = loginAndGetSessionCookie("alice");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/key",
                HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"gemini\",\"apiKey\":\"some-key\"}", headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    /**
     * CR-02: POST /api/ai/key with blank apiKey must return 400 with generic body.
     *
     * <p>Spring Boot's default {@link org.springframework.web.bind.MethodArgumentNotValidException}
     * handler includes {@code "field":"apiKey"} in the error body — revealing the parameter name.
     * The custom {@code @ExceptionHandler(MethodArgumentNotValidException.class)} in
     * {@code AiKeyController} returns {@code {"error":"Invalid request"}} with no field names.
     */
    @Test
    void setKey_blankApiKey_returns400_withGenericBody() {
        String sessionCookie = loginAndGetSessionCookie("alice");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        // POST with blank apiKey — triggers @NotBlank validation failure
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/key",
                HttpMethod.POST,
                new HttpEntity<>("{\"provider\":\"anthropic\",\"apiKey\":\"\"}", headers),
                String.class);

        assertThat(response.getStatusCode())
                .as("POST /api/ai/key with blank apiKey must return 400")
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody())
                .as("Validation error body must NOT contain field name 'apiKey' (CR-02: info disclosure)")
                .doesNotContainIgnoringCase("apiKey");
        assertThat(response.getBody())
                .as("Validation error body must NOT contain the submitted blank value")
                .doesNotContain("\"\"");
    }

    // ── helpers (verbatim copy from AnalyticsControllerIntegrationTest) ───────

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

    private ResponseEntity<String> authenticatedGet(String path, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }
}
