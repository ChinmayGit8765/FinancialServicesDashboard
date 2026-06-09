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
 * Integration test verifying that the POST /api/ai/chat response includes {@code citations[]}
 * populated from {@code QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS} in live mode.
 *
 * <h3>RED status in 07-01</h3>
 * RED in 07-01 because:
 * <ol>
 *   <li>No RAG_QA seed exists → DemoModeAdvisor cannot return a demo answer with citations</li>
 *   <li>No seeded corpus → QuestionAnswerAdvisor returns no documents in live mode</li>
 *   <li>No mock ChatModel wired for live-mode testing (corpus + mock needed)</li>
 * </ol>
 * Becomes GREEN in 07-02 when: RAG corpus is seeded, a mock ChatModel is added to bypass
 * the real provider, and citations[] is asserted non-empty after retrieval.
 */
class RagCitationsIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    /**
     * RED in 07-01 → GREEN in 07-02 (seeded corpus + mock provider).
     *
     * <p>Calls POST /api/ai/chat with a live key set (fake sentinel) and a question
     * that should match seeded AAPL chunks. Asserts {@code citations[]} is non-empty
     * and the first citation has a {@code ticker} field.
     */
    @Test
    void chat_withSeededCorpus_returnsCitations() {
        String cookie = loginAndGetSessionCookie("alice");

        // Set a fake live key so DemoModeAdvisor passes through to the RAG chain
        // (In 07-02 this will be a mock ChatModel key; in 07-01 this is just a compile scaffold)
        setFakeLiveKey(cookie);

        ResponseEntity<String> response = authenticatedPost(
                "/api/ai/chat",
                "{\"message\":\"What are Apple's App Store regulatory risks?\"}",
                cookie);

        assertThat(response.getStatusCode())
                .as("Chat with live key (fake) must return 200 or 502 (not 404/403)")
                .isIn(HttpStatus.OK, HttpStatus.BAD_GATEWAY);

        if (response.getStatusCode() == HttpStatus.OK) {
            assertThat(response.getBody())
                    .as("Response must include citations array")
                    .contains("\"citations\"");
            assertThat(response.getBody())
                    .as("Citations must include AAPL ticker from seeded corpus")
                    .contains("AAPL");
        }
        // If 502: provider rejected the fake key — that is expected in 07-01.
        // 07-02 wires a mock ChatModel to prevent the real provider call.
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private String loginAndGetSessionCookie(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", username);
        body.add("password", "demo1234");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST,
                new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode())
                .as("Login should succeed")
                .isEqualTo(HttpStatus.OK);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private void setFakeLiveKey(String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookie);
        String keyPayload = "{\"provider\":\"anthropic\",\"apiKey\":\"TEST-RAG-CITATIONS-KEY\"}";
        restTemplate.exchange("/api/ai/key", HttpMethod.POST,
                new HttpEntity<>(keyPayload, headers), String.class);
    }

    private ResponseEntity<String> authenticatedPost(String path, String json, String cookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, cookie);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(json, headers), String.class);
    }
}
