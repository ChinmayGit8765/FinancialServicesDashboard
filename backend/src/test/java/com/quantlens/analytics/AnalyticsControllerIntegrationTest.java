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
import static org.assertj.core.api.Assertions.within;

/**
 * Integration tests for the Analytics REST API.
 * <p>
 * Extends {@link AbstractPostgresIntegrationTest} — inherits
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}, {@code @ActiveProfiles("test")},
 * and the static Testcontainers pgvector16 container.
 * <p>
 * <strong>RED scaffold</strong> — {@link #getRisk_unauthenticated_returns401()} passes
 * immediately (auth boundary is live from Phase 1 SecurityConfig). All other tests are RED
 * scaffolds that reference golden-value constants filled after Plan 04-02 implements the math.
 */
class AnalyticsControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    // -----------------------------------------------------------------------
    // Golden-value constants — FILL from AnalyticsGoldenValuePrinterTest
    // -----------------------------------------------------------------------

    // Captured from AnalyticsGoldenValuePrinterTest 2026-06-08 (seed=42, alice Growth Portfolio)
    private static final double GOLDEN_HIST_VAR_AMOUNT = 1464.52;

    // -----------------------------------------------------------------------
    // T-04-02: Auth gate — passes immediately (Phase 1 SecurityConfig already wired)
    // -----------------------------------------------------------------------

    /**
     * Unauthenticated request to GET /api/portfolio/risk must return 401.
     * This test passes NOW and proves the IDOR auth gate is live (T-04-01, T-04-02).
     */
    @Test
    void getRisk_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/portfolio/risk",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("unauthenticated GET /api/portfolio/risk must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // RISK-01: /risk endpoint
    // -----------------------------------------------------------------------

    /**
     * GET /api/portfolio/risk returns 200 for alice.
     * RED until Plan 04-02 wires real computation (stub returns 200 with zeroed DTO).
     */
    @Test
    void risk_endpoint_returns200_alice() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/risk", cookie);
        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/risk for alice should return 200")
                .isEqualTo(HttpStatus.OK);
    }

    /**
     * The VaR array in the risk response must contain entries labelled "HISTORICAL" and "PARAMETRIC".
     * RED until Plan 04-02 populates real VaR list entries.
     */
    @Test
    void risk_endpoint_varMethodsLabelled() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/risk", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode varArray = body.path("var");
        assertThat(varArray.isArray()).as("risk.var must be a JSON array").isTrue();

        // RED until Plan 04-02: stub returns empty var list — these assertions will fail until then
        boolean hasHistorical = false;
        boolean hasParametric = false;
        for (JsonNode entry : varArray) {
            String method = entry.path("method").asText();
            if ("HISTORICAL".equals(method)) hasHistorical = true;
            if ("PARAMETRIC".equals(method)) hasParametric = true;
        }
        assertThat(hasHistorical).as("var array must contain a HISTORICAL entry").isTrue();
        assertThat(hasParametric).as("var array must contain a PARAMETRIC entry").isTrue();
    }

    /**
     * Historical VaR amount must match the seed-derived golden value within ±0.01 currency units.
     * RED until Plan 04-02.
     */
    @Test
    void risk_endpoint_varAmountsMatchGoldenValues() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/risk", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode varArray = body.path("var");

        double historicalVarAmount = 0.0;
        for (JsonNode entry : varArray) {
            if ("HISTORICAL".equals(entry.path("method").asText())) {
                historicalVarAmount = entry.path("amount").asDouble();
                break;
            }
        }
        // RED until Plan 04-02: GOLDEN_HIST_VAR_AMOUNT is 0.0 placeholder
        assertThat(historicalVarAmount)
                .as("HISTORICAL VaR amount must match golden value ±0.01")
                .isCloseTo(GOLDEN_HIST_VAR_AMOUNT, within(0.01));
    }

    // -----------------------------------------------------------------------
    // RISK-02: /correlation endpoint
    // -----------------------------------------------------------------------

    /**
     * GET /api/portfolio/correlation tickers array must match alice's 5 holdings.
     * RED until Plan 04-02 wires real CorrelationCalculator (stub returns empty tickers).
     */
    @Test
    void correlation_endpoint_tickersMatchHoldings() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/correlation", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode tickers = body.path("tickers");
        assertThat(tickers.isArray()).as("correlation.tickers must be a JSON array").isTrue();
        // RED: stub returns empty — will have 5 entries after Plan 04-02
        assertThat(tickers.size())
                .as("alice has 5 holdings; correlation matrix must have 5 tickers")
                .isEqualTo(5);
    }

    // -----------------------------------------------------------------------
    // ATTR-01: /attribution endpoint
    // -----------------------------------------------------------------------

    /**
     * GET /api/portfolio/attribution response must contain alphaAnnualized, betaMkt, rSquared fields.
     * RED until Plan 04-03.
     */
    @Test
    void attribution_endpoint_hasRequiredFields() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/attribution", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.has("alphaAnnualized"))
                .as("attribution response must contain alphaAnnualized").isTrue();
        assertThat(body.has("betaMkt"))
                .as("attribution response must contain betaMkt").isTrue();
        assertThat(body.has("rSquared"))
                .as("attribution response must contain rSquared").isTrue();
    }

    // -----------------------------------------------------------------------
    // ARB-01: /pairs endpoint
    // -----------------------------------------------------------------------

    /**
     * GET /api/portfolio/pairs response must return a JSON array; if non-empty, each entry
     * must contain tickerY, tickerX, pValue, spreadZScore, signal fields.
     * RED (stub returns empty array) until Plan 04-03 implements Engle-Granger scanner.
     */
    @Test
    void pairs_responseHasRequiredFields() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/pairs", cookie);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).as("pairs response must be a JSON array").isTrue();

        // When Plan 04-03 returns actual pairs, verify required fields on first entry
        if (body.size() > 0) {
            JsonNode first = body.get(0);
            assertThat(first.has("tickerY")).as("pair entry must have tickerY").isTrue();
            assertThat(first.has("tickerX")).as("pair entry must have tickerX").isTrue();
            assertThat(first.has("pValue")).as("pair entry must have pValue").isTrue();
            assertThat(first.has("spreadZScore")).as("pair entry must have spreadZScore").isTrue();
            assertThat(first.has("signal")).as("pair entry must have signal").isTrue();
        }
    }

    // -----------------------------------------------------------------------
    // Private helpers — copied verbatim from PortfolioControllerIntegrationTest
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
