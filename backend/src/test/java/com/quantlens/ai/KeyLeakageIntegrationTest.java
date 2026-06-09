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
 * or log line (T-06-01, RESEARCH Key-Leak Prevention, AI-02).
 *
 * <p>This test PASSES now because:
 * <ul>
 *   <li>{@code POST /api/ai/key} returns {@code {mode, provider}} only — never the key</li>
 *   <li>{@code GET /api/ai/status} returns {@code {mode, provider}} only</li>
 *   <li>{@code GET /api/ai/explain/AAPL} returns 200 — AAPL is a seeded holding in alice's
 *       portfolio (Growth persona) so the endpoint does not 404, making the leakage
 *       assertion meaningful (not vacuously true)</li>
 *   <li>{@code GET /api/ai/commentary} returns 200</li>
 *   <li>No logging advisor is registered; {@code org.springframework.ai} is set to WARN</li>
 * </ul>
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

        // 3. Call GET /api/ai/explain/AAPL — AAPL is in alice's Growth portfolio (seeded in Phase 1)
        // A 404 here would make the leakage assertion vacuously true; we assert 200 (T-06-SC)
        ResponseEntity<String> explainResponse = authenticatedGet("/api/ai/explain/AAPL", sessionCookie);
        assertThat(explainResponse.getStatusCode())
                .as("GET /api/ai/explain/AAPL must return 200 — AAPL is a seeded holding for alice")
                .isEqualTo(HttpStatus.OK);
        assertThat(explainResponse.getBody())
                .as("GET /api/ai/explain/AAPL response must NOT contain the API key")
                .doesNotContain(testKey);

        // 4. Call GET /api/ai/commentary — same leakage check
        ResponseEntity<String> commentaryResponse = authenticatedGet("/api/ai/commentary", sessionCookie);
        assertThat(commentaryResponse.getStatusCode())
                .as("GET /api/ai/commentary must return 200")
                .isEqualTo(HttpStatus.OK);
        assertThat(commentaryResponse.getBody())
                .as("GET /api/ai/commentary response must NOT contain the API key")
                .doesNotContain(testKey);

        // 5. Assert no captured log line contains the test key
        List<String> capturedLines = logAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();
        assertThat(capturedLines)
                .as("No log line must contain the API key (T-06-02)")
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
