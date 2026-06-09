package com.quantlens.ai;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
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

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security gate integration test: the API key MUST NEVER appear in any HTTP response
 * or log line (T-06-01, T-08-LEAK-ST, RESEARCH Key-Leak Prevention, AI-02, AI-05, AI-06).
 *
 * <p>Endpoints covered:
 * <ul>
 *   <li>{@code POST /api/ai/key} returns {@code {mode, provider}} only — never the key</li>
 *   <li>{@code GET /api/ai/status} returns {@code {mode, provider}} only</li>
 *   <li>{@code GET /api/ai/explain/AAPL} — AAPL is a seeded holding for alice; 200 or 502 graceful</li>
 *   <li>{@code GET /api/ai/commentary} — 200 or 502 graceful</li>
 *   <li>{@code POST /api/ai/chat} — 200 or 502 graceful (T-07-LEAK)</li>
 *   <li>{@code GET /api/ai/structured} — 200 or 502 graceful (T-08-LEAK-ST)</li>
 * </ul>
 *
 * <p>T-08-LEAK-FH: {@code FINNHUB_API_KEY} is blank in the test environment, so the seeded
 * fallback path runs and Finnhub is never called here. The endpoint-level assertion on
 * {@code /api/ai/structured} therefore does not exercise the Finnhub token path.
 * The MANDATORY security proof for Finnhub token non-disclosure is in
 * {@code StockQuoteToolServiceTest#finnhubKeySentinelNeverLogged_onForcedFailure} —
 * do NOT remove that test. (IN-03 fix: replaced the misleading "vacuously safe" wording.)
 *
 * <p><strong>IMPORTANT:</strong> This test uses a randomly generated test key
 * ({@code "TEST-SENTINEL-KEY-" + UUID}) to ensure no collision with any real key or
 * static string in the codebase.
 */
class KeyLeakageIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    private ListAppender<ILoggingEvent> logAppender;
    private Logger rootLogger;

    @BeforeEach
    void attachLogAppender() {
        rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        logAppender = new ListAppender<>();
        logAppender.start();
        rootLogger.addAppender(logAppender);
    }

    @AfterEach
    void detachLogAppender() {
        rootLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void apiKeyNeverAppearsInResponseOrLogs() {
        String testKey = "TEST-SENTINEL-KEY-" + UUID.randomUUID();
        String sessionCookie = loginAndGetSessionCookie("alice");

        // 1. Submit the key via POST /api/ai/key
        HttpHeaders postHeaders = new HttpHeaders();
        postHeaders.setContentType(MediaType.APPLICATION_JSON);
        postHeaders.add(HttpHeaders.COOKIE, sessionCookie);

        String keyPayload = "{\"provider\":\"anthropic\",\"apiKey\":\"" + testKey + "\"}";
        ResponseEntity<String> setKeyResponse = restTemplate.exchange(
                "/api/ai/key",
                HttpMethod.POST,
                new HttpEntity<>(keyPayload, postHeaders),
                String.class);

        assertThat(setKeyResponse.getStatusCode())
                .as("POST /api/ai/key should return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(setKeyResponse.getBody())
                .as("POST /api/ai/key response must contain mode=live")
                .contains("\"mode\":\"live\"");
        assertThat(setKeyResponse.getBody())
                .as("POST /api/ai/key response body must NOT contain the API key (T-06-01)")
                .doesNotContain(testKey);
        assertThat(setKeyResponse.getBody())
                .as("POST /api/ai/key response must NOT have an apiKey field")
                .doesNotContainIgnoringCase("apiKey");

        // 2. Call GET /api/ai/status — must not return key
        ResponseEntity<String> statusResponse = authenticatedGet("/api/ai/status", sessionCookie);
        assertThat(statusResponse.getStatusCode())
                .as("GET /api/ai/status should return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody())
                .as("GET /api/ai/status response must NOT contain the API key")
                .doesNotContain(testKey);

        // 3. Call GET /api/ai/explain/AAPL — AAPL IS a seeded holding for alice, so this is NOT a
        // vacuous 404. With a live key set (the fake TEST-SENTINEL key), the request routes to the
        // real provider, which rejects the fake key — the service wraps that as 502 BAD_GATEWAY
        // (never echoing the key). The security property under test is: regardless of status, the
        // key must NEVER appear in the response body. So we accept 200 (if a provider somehow
        // succeeded) OR 502 (live provider rejected the fake key) and assert no leak either way.
        ResponseEntity<String> explainResponse = authenticatedGet("/api/ai/explain/AAPL", sessionCookie);
        assertThat(explainResponse.getStatusCode())
                .as("GET /api/ai/explain/AAPL is reachable (200) or fails gracefully on the fake live key (502) — not 404")
                .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
        assertThat(explainResponse.getBody())
                .as("GET /api/ai/explain/AAPL response (incl. 502 error body) must NEVER contain the API key (T-06-01)")
                .doesNotContain(testKey);

        // 4. Call GET /api/ai/commentary — same leakage check (200 or graceful 502 on fake live key)
        ResponseEntity<String> commentaryResponse = authenticatedGet("/api/ai/commentary", sessionCookie);
        assertThat(commentaryResponse.getStatusCode())
                .as("GET /api/ai/commentary is reachable (200) or fails gracefully on the fake live key (502)")
                .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
        assertThat(commentaryResponse.getBody())
                .as("GET /api/ai/commentary response (incl. 502 error body) must NEVER contain the API key (T-06-01)")
                .doesNotContain(testKey);

        // 5. Call POST /api/ai/chat — must not return key (T-07-LEAK)
        HttpHeaders chatHeaders = new HttpHeaders();
        chatHeaders.setContentType(MediaType.APPLICATION_JSON);
        chatHeaders.add(HttpHeaders.COOKIE, sessionCookie);
        String chatPayload = "{\"message\":\"What are the key risks?\"}";
        ResponseEntity<String> chatResponse = restTemplate.exchange(
                "/api/ai/chat",
                HttpMethod.POST,
                new HttpEntity<>(chatPayload, chatHeaders),
                String.class);
        assertThat(chatResponse.getStatusCode())
                .as("POST /api/ai/chat is reachable (200) or fails gracefully on fake live key (502) — not 404/403")
                .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
        assertThat(chatResponse.getBody())
                .as("POST /api/ai/chat response must NEVER contain the API key (T-07-LEAK)")
                .doesNotContain(testKey);

        // 5b. Call GET /api/ai/structured — must not return LLM key in response (T-08-LEAK-ST).
        //
        // T-08-LEAK-FH: this endpoint can trigger StockQuoteToolService → FinnhubQuoteClient
        // if the LLM decides to call the @Tool. In this test env FINNHUB_API_KEY is blank,
        // so the seeded fallback runs and Finnhub is never called. The MANDATORY security proof
        // for Finnhub token non-disclosure is in
        // StockQuoteToolServiceTest#finnhubKeySentinelNeverLogged_onForcedFailure —
        // do NOT remove that test. (IN-03 fix: replaced the misleading "vacuously safe" comment.)
        ResponseEntity<String> structuredResponse = authenticatedGet("/api/ai/structured", sessionCookie);
        assertThat(structuredResponse.getStatusCode())
                .as("GET /api/ai/structured is reachable (200 demo seed) or fails gracefully on the fake live key (502) — not 404/403")
                .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);
        assertThat(structuredResponse.getBody())
                .as("GET /api/ai/structured response (incl. 502 error body) must NEVER contain the API key (T-08-LEAK-ST)")
                .doesNotContain(testKey);

        // 6. Assert no captured log line contains the test key
        // This step now also guards the structured endpoint (step 5b) since the appender
        // captures all log output produced during this test including any structured-path logs.
        List<String> capturedLines = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(capturedLines)
                .as("No log line must contain the API key (T-06-02, T-07-LEAK, T-08-LEAK-ST)")
                .noneMatch(line -> line.contains(testKey));
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
