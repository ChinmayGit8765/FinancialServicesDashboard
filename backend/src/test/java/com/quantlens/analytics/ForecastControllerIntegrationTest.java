package com.quantlens.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Integration tests for the Monte Carlo forecast REST endpoint.
 * <p>
 * Extends {@link AbstractPostgresIntegrationTest} — inherits
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}, {@code @ActiveProfiles("test")},
 * and the static Testcontainers pgvector16 container with seeded data (alice/bob/charlie).
 *
 * <h3>IDOR prevention (T-05-01)</h3>
 * Tests verify that unauthenticated requests receive 401 (auth gate is live).
 * Authenticated tests use {@link #loginAndGetSessionCookie(String)} — the portfolio ID
 * is resolved server-side from the session; it is never in the request URL.
 *
 * <h3>RED state in Plan 05-01</h3>
 * All tests that call the actual endpoint with authentication will receive an HTTP 500
 * (or the ForecastService throws UnsupportedOperationException wrapped in a 500) until
 * Plan 05-02 implements the engine. The test assertions check for 200 + correct JSON shape,
 * so they are expected to FAIL until 05-02. The 401 test (auth gate) passes immediately.
 */
class ForecastControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // -----------------------------------------------------------------------
    // T-05-01: Auth gate — passes immediately (Phase 1 SecurityConfig already wired)
    // -----------------------------------------------------------------------

    /**
     * Unauthenticated request to GET /api/portfolio/forecast must return 401.
     * This test passes immediately and proves the IDOR auth gate is live (T-05-01).
     */
    @Test
    void forecast_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/portfolio/forecast?model=GBM",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("Unauthenticated GET /api/portfolio/forecast must return 401 (T-05-01 IDOR gate)")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    /**
     * Unknown model value must return 400 (Spring MVC enum validation — T-05-02).
     * RED until endpoint is wired. Once wired this should pass even before 05-02
     * because the 400 is returned before ForecastService is called.
     */
    @Test
    void forecast_unknownModel_returns400() {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet(
                "/api/portfolio/forecast?model=INVALID_MODEL_XYZ", cookie);
        assertThat(response.getStatusCode())
                .as("Unknown model value must return 400 Bad Request (T-05-02 enum validation)")
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // -----------------------------------------------------------------------
    // SIM-02: Authenticated GBM forecast — JSON shape
    // -----------------------------------------------------------------------

    /**
     * Authenticated GET /api/portfolio/forecast?model=GBM&horizon=252 must return 200
     * with the correct JSON shape: model=="GBM", horizonDays==252, p50 is array of size 252.
     * RED until Plan 05-02 implements the GBM engine.
     */
    @Test
    void forecast_gbm_returns200_withCorrectShape() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet(
                "/api/portfolio/forecast?model=GBM&horizon=252", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/forecast?model=GBM should return 200 for alice")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("model").asText())
                .as("Response body.model must equal 'GBM'")
                .isEqualTo("GBM");
        assertThat(body.path("horizonDays").asInt())
                .as("Response body.horizonDays must equal 252")
                .isEqualTo(252);
        assertThat(body.path("p50").isArray())
                .as("Response body.p50 must be a JSON array")
                .isTrue();
        assertThat(body.path("p50").size())
                .as("Response body.p50 must have exactly 252 elements (one per trading day)")
                .isEqualTo(252);
    }

    // -----------------------------------------------------------------------
    // SIM-02: All four models return 200 with distinct band shapes
    // -----------------------------------------------------------------------

    /**
     * All four models must return 200 with a p95 array when authenticated.
     * RED until Plan 05-02.
     */
    @Test
    void forecast_jumpDiffusion_returns200() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet(
                "/api/portfolio/forecast?model=JUMP_DIFFUSION&horizon=252", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/forecast?model=JUMP_DIFFUSION should return 200 for alice")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("model").asText()).isEqualTo("JUMP_DIFFUSION");
        assertThat(body.path("p95").isArray()).isTrue();
        assertThat(body.path("p95").size()).isEqualTo(252);
    }

    /**
     * Heston model must return 200 with correct JSON shape.
     * RED until Plan 05-02.
     */
    @Test
    void forecast_heston_returns200() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet(
                "/api/portfolio/forecast?model=HESTON&horizon=252", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/forecast?model=HESTON should return 200 for alice")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("model").asText()).isEqualTo("HESTON");
        assertThat(body.path("p95").isArray()).isTrue();
    }

    /**
     * Bootstrap model must return 200 with correct JSON shape.
     * RED until Plan 05-02.
     */
    @Test
    void forecast_bootstrap_returns200() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet(
                "/api/portfolio/forecast?model=BOOTSTRAP&horizon=252", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/forecast?model=BOOTSTRAP should return 200 for alice")
                .isEqualTo(HttpStatus.OK);
        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.path("model").asText()).isEqualTo("BOOTSTRAP");
        assertThat(body.path("p95").isArray()).isTrue();
    }

    // -----------------------------------------------------------------------
    // SIM-02: Horizon clamping — T-05-03 DoS guard
    // -----------------------------------------------------------------------

    /**
     * Horizon values outside [1,504] must be clamped server-side.
     * horizon=0 should be treated as 1; horizon=9999 as 504.
     * RED until Plan 05-02 (the clamp itself is live in 05-01, but the service throws).
     */
    @Test
    void forecast_horizonClamped_at1_and_504() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");

        // horizon=0 → clamped to 1 → horizonDays=1
        ResponseEntity<String> r1 = authenticatedGet(
                "/api/portfolio/forecast?model=GBM&horizon=0", cookie);
        assertThat(r1.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode b1 = objectMapper.readTree(r1.getBody());
        assertThat(b1.path("horizonDays").asInt())
                .as("horizon=0 must be clamped to 1")
                .isEqualTo(1);

        // horizon=9999 → clamped to 504
        ResponseEntity<String> r2 = authenticatedGet(
                "/api/portfolio/forecast?model=GBM&horizon=9999", cookie);
        assertThat(r2.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode b2 = objectMapper.readTree(r2.getBody());
        assertThat(b2.path("horizonDays").asInt())
                .as("horizon=9999 must be clamped to 504")
                .isEqualTo(504);
    }

    // -----------------------------------------------------------------------
    // Private helpers — copied verbatim from AnalyticsControllerIntegrationTest
    // -----------------------------------------------------------------------

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
