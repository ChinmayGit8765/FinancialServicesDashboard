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
 * Integration tests for {@code AiController} AI feature endpoints.
 *
 * <h3>GREEN now (auth gate)</h3>
 * {@link #explain_unauthenticated_returns401()} — passes immediately because
 * {@code SecurityConfig.anyRequest().authenticated()} is already wired.
 *
 * <h3>RED for 06-02/03 (feature content)</h3>
 * {@link #explainReturnsSeededContent()} and {@link #commentaryReturnsSeededContent()} are
 * RED until Plan 06-02 seeds the {@code ai_seed_content} table and Plan 06-03 wires the
 * real service implementations. The stubs return empty strings/lists so these assertions
 * fail with assertion errors (not compile errors).
 */
class AiControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // ── GREEN now: auth gate ──────────────────────────────────────────────────

    /**
     * Unauthenticated request to GET /api/ai/explain/AAPL must return 401.
     * PASSES now — SecurityConfig already wired.
     */
    @Test
    void explain_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/explain/AAPL",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("unauthenticated GET /api/ai/explain/AAPL must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Unauthenticated request to GET /api/ai/commentary must return 401.
     * PASSES now — SecurityConfig already wired.
     */
    @Test
    void commentary_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/commentary",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("unauthenticated GET /api/ai/commentary must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── RED for 06-02/03: feature content ────────────────────────────────────

    /**
     * GET /api/ai/explain/AAPL must return 200 with a non-blank narrative.
     *
     * <p><strong>RED until Plan 06-02 seeds EXPLAIN_POSITION/AAPL content and Plan 06-03
     * wires ExplainPositionService.</strong> Currently returns 200 with empty narrative
     * (stub) — the non-blank assertion fails here intentionally.
     */
    @Test
    void explainReturnsSeededContent() {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/ai/explain/AAPL", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/ai/explain/AAPL must return 200")
                .isEqualTo(HttpStatus.OK);
        // RED until 06-02/03: stub returns {"narrative":""} — this assertion fails
        assertThat(response.getBody())
                .as("explain narrative must be non-blank (RED until 06-02 seeds content + 06-03 wires service)")
                .contains("narrative")
                .doesNotContain("\"narrative\":\"\"");
    }

    /**
     * GET /api/ai/commentary must return 200 with non-empty headline, body, bulletPoints.
     *
     * <p><strong>RED until Plan 06-02 seeds DAILY_COMMENTARY content and Plan 06-03
     * wires CommentaryService.</strong>
     */
    @Test
    void commentaryReturnsSeededContent() {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/ai/commentary", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/ai/commentary must return 200")
                .isEqualTo(HttpStatus.OK);
        // RED until 06-02/03: stub returns {"headline":"","body":"","bulletPoints":[]}
        assertThat(response.getBody())
                .as("commentary headline must be non-blank (RED until 06-02 seeds content + 06-03 wires service)")
                .contains("headline")
                .doesNotContain("\"headline\":\"\"");
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
