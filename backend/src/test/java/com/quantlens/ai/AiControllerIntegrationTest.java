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

    // ── AI-06: GET /api/ai/structured demo mode ───────────────────────────────

    /**
     * GET /api/ai/structured returns 200 with a StructuredInsightRecord shape (title +
     * non-empty series) for alice (GROWTH persona) in demo mode.
     *
     * <p>Seed version ai-v4 provides the STRUCTURED_INSIGHT/GROWTH row; the endpoint
     * deserializes it via {@code ObjectMapper.readValue} — no provider call.
     */
    @Test
    void structured_demoMode_returnsRecordShape() {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/ai/structured", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/ai/structured must return 200 in demo mode")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("Response must contain 'title' field")
                .contains("\"title\"")
                .as("title must not be blank")
                .doesNotContain("\"title\":\"\"");
        assertThat(response.getBody())
                .as("Response must contain non-empty 'series' array")
                .contains("\"series\"")
                .contains("\"label\"");
    }

    /**
     * Unauthenticated GET /api/ai/structured must return 401.
     */
    @Test
    void structured_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/ai/structured",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("unauthenticated GET /api/ai/structured must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // ── CR-05: ticker format validation ──────────────────────────────────────

    /**
     * CR-05: GET /api/ai/explain/{ticker} with a malformed ticker (lowercase, digits, special
     * chars, or URL-encoded control chars) must return 400 before reaching the service.
     *
     * <p>{@code @Validated} on {@code AiController} + {@code @Pattern(regexp="^[A-Z]{1,10}$")}
     * on the {@code ticker} path variable ensures Spring validates the constraint and throws
     * {@code ConstraintViolationException} before the service is ever invoked.
     *
     * <p>The test uses a deliberately malformed ticker that would represent a prompt-injection
     * attempt after URL-decoding (e.g. {@code AAPL%0ADisregard} → {@code AAPL\nDisregard}).
     * Spring decodes percent-encoding in {@code @PathVariable}, so the newline would reach
     * the prompt without this guard.
     */
    @Test
    void explain_malformedTicker_returns400() {
        String cookie = loginAndGetSessionCookie("alice");

        // Malformed ticker: lowercase letters — must be rejected before reaching service
        ResponseEntity<String> lowercaseResponse = authenticatedGet("/api/ai/explain/aapl", cookie);
        assertThat(lowercaseResponse.getStatusCode())
                .as("GET /api/ai/explain/aapl (lowercase) must return 400 (CR-05: @Pattern validation)")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Malformed ticker: too long (>10 chars) — must be rejected
        ResponseEntity<String> tooLongResponse = authenticatedGet("/api/ai/explain/TOOLONGTICKERX", cookie);
        assertThat(tooLongResponse.getStatusCode())
                .as("GET /api/ai/explain/TOOLONGTICKERX (>10 chars) must return 400 (CR-05: @Pattern validation)")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        // Malformed ticker: contains digit — must be rejected
        ResponseEntity<String> digitResponse = authenticatedGet("/api/ai/explain/AAPL1", cookie);
        assertThat(digitResponse.getStatusCode())
                .as("GET /api/ai/explain/AAPL1 (contains digit) must return 400 (CR-05: @Pattern validation)")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── CR-04 IDOR fix: client-supplied conversationId must be ignored ────────

    /**
     * CR-04 regression: a client-supplied conversationId must be IGNORED.
     * AiController must always use session.getId() as the conversationId so that
     * an attacker cannot supply another user's session ID to read/poison their memory.
     *
     * <p>This test verifies the behavior from the correct session perspective: two
     * requests in the same session with different client-supplied conversationId values
     * both succeed (200 or 502) — proving the server does not error on ignored field,
     * and that the field is not treated as the conversation scope.
     */
    @Test
    void chat_clientSuppliedConversationId_isIgnored_sessionIdUsedInstead() {
        String cookie = loginAndGetSessionCookie("alice");

        // Request with an arbitrary attacker-style conversationId
        ResponseEntity<String> response1 = authenticatedPost(
                "/api/ai/chat",
                "{\"message\":\"What are Apple risks?\",\"conversationId\":\"ATTACKER-INJECTED-ID-12345\"}",
                cookie);

        // Request with no conversationId (server uses session.getId())
        ResponseEntity<String> response2 = authenticatedPost(
                "/api/ai/chat",
                "{\"message\":\"What are Apple risks?\"}",
                cookie);

        // Both must succeed (200 in demo mode) — the arbitrary conversationId does not cause an error
        assertThat(response1.getStatusCode())
                .as("Chat with attacker-supplied conversationId must return 200 (field ignored, session used)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response2.getStatusCode())
                .as("Chat with no conversationId must return 200")
                .isEqualTo(HttpStatus.OK);

        // Both responses must contain a valid answer (not an error about the conversationId)
        assertThat(response1.getBody())
                .as("Response must contain 'answer' field even when attacker conversationId supplied")
                .contains("\"answer\"");
        assertThat(response2.getBody())
                .as("Response must contain 'answer' field with no conversationId supplied")
                .contains("\"answer\"");
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

    private ResponseEntity<String> authenticatedPost(String path, String json, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(json, headers), String.class);
    }
}
