package com.quantlens.portfolio;

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
 * Integration tests for the Portfolio REST API.
 * <p>
 * Extends {@link AbstractPostgresIntegrationTest} — inherits
 * {@code @SpringBootTest(webEnvironment = RANDOM_PORT)}, {@code @ActiveProfiles("test")},
 * and the static Testcontainers pgvector16 container.
 * <p>
 * <strong>RED scaffold</strong> — {@link #getHoldings_unauthenticated_returns401()} passes
 * immediately (auth boundary is live from Phase 1). All other test methods are RED scaffolds
 * that call endpoints which 404 or return empty until Plans 02–04 add
 * {@code PortfolioController} and {@code PortfolioService}.
 */
class PortfolioControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules(); // registers JavaTimeModule for LocalDate parsing

    // -----------------------------------------------------------------------
    // Auth gate — passes immediately (Phase 1 SecurityConfig already wired)
    // -----------------------------------------------------------------------

    /**
     * Unauthenticated request to a protected portfolio endpoint must return 401.
     * This test passes NOW and proves the IDOR auth gate is live (T-02-01).
     */
    @Test
    void getHoldings_unauthenticated_returns401() {
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/portfolio/holdings",
                HttpMethod.GET,
                HttpEntity.EMPTY,
                String.class);
        assertThat(response.getStatusCode())
                .as("unauthenticated GET /api/portfolio/holdings must return 401")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // -----------------------------------------------------------------------
    // PORT-01: Holdings endpoint — RED scaffolds
    // -----------------------------------------------------------------------

    /**
     * Alice should have exactly 5 holdings (AAPL, MSFT, NVDA, AMZN, TSLA).
     * RED — endpoint returns 404 until Plan 02 adds PortfolioController.
     */
    @Test
    void getHoldings_alice_returns5Holdings() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/holdings", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/holdings for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).as("holdings response should be a JSON array").isTrue();
        assertThat(body.size())
                .as("alice should have exactly 5 holdings")
                .isEqualTo(5);
    }

    // -----------------------------------------------------------------------
    // PORT-02: P&L endpoint — RED scaffolds
    // -----------------------------------------------------------------------

    /**
     * Alice's equity curve should have exactly 504 entries (the full seeded window).
     * RED — endpoint returns 404 until Plan 03 adds the pnl endpoint.
     */
    @Test
    void getPnl_aliceEquityCurve504Entries() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/pnl", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/pnl for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode equityCurve = body.path("equityCurve");
        assertThat(equityCurve.isArray()).as("equityCurve should be an array").isTrue();
        assertThat(equityCurve.size())
                .as("equity curve should have 504 entries (full seeded window)")
                .isEqualTo(504);
    }

    /**
     * The first entry in alice's equity curve should be dated 2022-09-12
     * (the seeded series start date, first Monday in the GbmGenerator sequence).
     * RED — endpoint returns 404 until Plan 03 adds the pnl endpoint.
     */
    @Test
    void getPnl_equityCurveStartDate() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/pnl", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/pnl for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        String firstDate = body.path("equityCurve").get(0).path("date").asText();
        assertThat(firstDate)
                .as("equity curve start date should be 2022-09-12")
                .isEqualTo("2022-09-12");
    }

    // -----------------------------------------------------------------------
    // PORT-03: Allocation endpoint — RED scaffolds
    // -----------------------------------------------------------------------

    /**
     * Alice's allocation response should include all sectors present in her portfolio.
     * RED — endpoint returns 404 until Plan 03 adds the allocation endpoint.
     */
    @Test
    void getAllocation_aliceSectorsPresent() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/allocation", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/allocation for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).as("allocation response should be a JSON array").isTrue();
        assertThat(body.size())
                .as("allocation should have at least one sector slice")
                .isGreaterThan(0);
    }

    /**
     * The sum of all allocation slice weights must equal exactly 1.000000.
     * RED — endpoint returns 404 until Plan 03 adds the allocation endpoint.
     */
    @Test
    void getAllocation_weightSumsToOne() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/allocation", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/allocation for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        assertThat(body.isArray()).as("allocation should be a JSON array").isTrue();

        double totalWeight = 0.0;
        for (JsonNode slice : body) {
            totalWeight += slice.path("weight").asDouble();
        }
        assertThat(totalWeight)
                .as("allocation weights must sum to 1.0 (within floating rounding tolerance)")
                .isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.000001));
    }

    // -----------------------------------------------------------------------
    // PORT-04: Transactions endpoint — RED scaffolds
    // -----------------------------------------------------------------------

    /**
     * The first page of alice's transactions should be ordered most-recent-first.
     * RED — endpoint returns 404 until Plan 04 adds the transactions endpoint.
     */
    @Test
    void getTransactions_mostRecentFirst() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/transactions", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/transactions for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode content = body.path("content");
        assertThat(content.isArray()).as("transactions response should have a 'content' array").isTrue();
        assertThat(content.size()).as("should have at least 2 transactions").isGreaterThan(1);

        // Verify descending order: first txDate >= second txDate
        String firstDate  = content.get(0).path("txDate").asText();
        String secondDate = content.get(1).path("txDate").asText();
        assertThat(firstDate)
                .as("transactions should be most-recent-first")
                .isGreaterThanOrEqualTo(secondDate);
    }

    /**
     * Page 1 (zero-indexed) should return different transactions than page 0.
     * RED — endpoint returns 404 until Plan 04 adds the transactions endpoint.
     */
    @Test
    void getTransactions_paginationWorks() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");

        ResponseEntity<String> page0Response = authenticatedGet(
                "/api/portfolio/transactions?page=0&size=5", cookie);
        ResponseEntity<String> page1Response = authenticatedGet(
                "/api/portfolio/transactions?page=1&size=5", cookie);

        assertThat(page0Response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(page1Response.getStatusCode()).isEqualTo(HttpStatus.OK);

        JsonNode page0Content = objectMapper.readTree(page0Response.getBody()).path("content");
        JsonNode page1Content = objectMapper.readTree(page1Response.getBody()).path("content");

        assertThat(page0Content.size()).as("page 0 should have entries").isGreaterThan(0);
        assertThat(page1Content.size()).as("page 1 should have entries").isGreaterThan(0);

        // The first entry of page 0 must differ from the first entry of page 1
        String page0FirstDate = page0Content.get(0).path("txDate").asText();
        String page1FirstDate = page1Content.get(0).path("txDate").asText();
        assertThat(page0FirstDate)
                .as("page 0 and page 1 should return different transactions")
                .isNotEqualTo(page1FirstDate);
    }

    // -----------------------------------------------------------------------
    // PORT-05: Benchmark endpoint — RED scaffolds
    // -----------------------------------------------------------------------

    /**
     * The portfolio series and benchmark series must have the same length.
     * RED — endpoint returns 404 until Plan 04 adds the benchmark endpoint.
     */
    @Test
    void getBenchmark_seriesSameLength() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/benchmark", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/benchmark for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        int datesSize    = body.path("dates").size();
        int portfolioSize = body.path("portfolioSeries").size();
        int benchmarkSize = body.path("benchmarkSeries").size();

        assertThat(portfolioSize)
                .as("portfolioSeries and dates must be same length")
                .isEqualTo(datesSize);
        assertThat(benchmarkSize)
                .as("benchmarkSeries and dates must be same length")
                .isEqualTo(datesSize);
    }

    /**
     * The dates array in the benchmark response must be sorted ascending,
     * and both series must start at exactly 100.0000 on day 0 (rebasing verification).
     * RED — endpoint returns 404 until Plan 03 adds the benchmark endpoint.
     */
    @Test
    void getBenchmark_datesSortedAscending() throws Exception {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> response = authenticatedGet("/api/portfolio/benchmark", cookie);

        assertThat(response.getStatusCode())
                .as("GET /api/portfolio/benchmark for alice should return 200")
                .isEqualTo(HttpStatus.OK);

        JsonNode body = objectMapper.readTree(response.getBody());
        JsonNode dates = body.path("dates");
        assertThat(dates.isArray()).as("dates should be a JSON array").isTrue();
        assertThat(dates.size()).as("dates array should not be empty").isGreaterThan(0);

        // Verify ascending order
        String prev = "";
        for (JsonNode dateNode : dates) {
            String current = dateNode.asText();
            assertThat(current)
                    .as("dates[i] must be >= dates[i-1] (ascending order)")
                    .isGreaterThanOrEqualTo(prev);
            prev = current;
        }

        // First date should be 2022-09-12 (seeded series start)
        assertThat(dates.get(0).asText())
                .as("benchmark dates[0] should be the series start date 2022-09-12")
                .isEqualTo("2022-09-12");

        // Both series[0] must equal exactly 100.0000 (rebasing to common base on day 0)
        java.math.BigDecimal portfolioDay0 = new java.math.BigDecimal(
                body.path("portfolioSeries").get(0).asText());
        java.math.BigDecimal benchmarkDay0 = new java.math.BigDecimal(
                body.path("benchmarkSeries").get(0).asText());
        java.math.BigDecimal expected = new java.math.BigDecimal("100.0000");

        assertThat(portfolioDay0.compareTo(expected))
                .as("portfolioSeries[0] must be exactly 100.0000 after rebasing")
                .isEqualTo(0);
        assertThat(benchmarkDay0.compareTo(expected))
                .as("benchmarkSeries[0] must be exactly 100.0000 after rebasing")
                .isEqualTo(0);
    }

    // -----------------------------------------------------------------------
    // Private helpers — copied from PersonaIntegrationTest
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

    private String extractJsonField(String json, String fieldName) throws Exception {
        JsonNode node = objectMapper.readTree(json);
        assertThat(node.has(fieldName))
                .as("field '%s' not found in JSON: %s", fieldName, json)
                .isTrue();
        return node.path(fieldName).asText();
    }
}
