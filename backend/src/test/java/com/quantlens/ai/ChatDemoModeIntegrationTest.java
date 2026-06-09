package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test proving that POST /api/ai/chat in demo mode reads ONLY from the DB seed
 * — no provider call fires (T-06-03b pattern applied to the new chat endpoint).
 *
 * <h3>EXECUTABLE no-network proof</h3>
 * A test-only {@link CountingCallAdvisor} bean (order HIGHEST_PRECEDENCE+1) sits just after
 * {@link com.quantlens.ai.chat.DemoModeAdvisor} (HIGHEST_PRECEDENCE). In demo mode
 * DemoModeAdvisor short-circuits — the counting advisor is never reached and its counter
 * stays at 0. That zero is the executable proof no network call fired.
 *
 * <h3>RED status in 07-01</h3>
 * {@code demoMode_chat_returnsAnswer_withZeroNetworkCalls} is RED in 07-01 because no RAG_QA
 * seed content exists yet (DemoModeAdvisor returns an empty/generic answer or 502, not a
 * non-blank authored response). The test becomes GREEN in 07-02 when RAG_QA seeds are authored.
 */
@Import(ChatDemoModeIntegrationTest.NoNetworkProofConfig.class)
class ChatDemoModeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetCounter() {
        NoNetworkProofConfig.NEXT_CALL_COUNT.set(0);
    }

    /**
     * RED in 07-01 (no RAG_QA seed) → GREEN in 07-02 (after RAG_QA seed rows authored).
     *
     * <p>Asserts: 200, non-blank answer field, counter == 0 (DemoModeAdvisor short-circuited).
     */
    @Test
    void demoMode_chat_returnsAnswer_withZeroNetworkCalls() {
        String cookie = loginAndGetSessionCookie("alice");

        ResponseEntity<String> response = authenticatedPost(
                "/api/ai/chat",
                "{\"message\":\"What are Apple's key risks?\"}",
                cookie);

        assertThat(response.getStatusCode())
                .as("POST /api/ai/chat must return 200 in demo mode")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("Demo chat response must contain 'answer' field")
                .contains("\"answer\"");
        assertThat(response.getBody())
                .as("Demo chat answer must not be blank")
                .doesNotContain("\"answer\":\"\"");
        assertThat(response.getBody())
                .as("Demo chat response must contain 'citations' array (parsed from authored RAG_QA JSON)")
                .contains("\"citations\"");
        assertThat(response.getBody())
                .as("Demo citations must include at least one ticker from the seeded corpus")
                .satisfiesAnyOf(
                        body -> assertThat(body).contains("\"AAPL\""),
                        body -> assertThat(body).contains("\"NVDA\""),
                        body -> assertThat(body).contains("\"JPM\""),
                        body -> assertThat(body).contains("\"XOM\"")
                );
        assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
                .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode")
                .isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

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
                .as("Login for %s should succeed", username)
                .isEqualTo(HttpStatus.OK);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private ResponseEntity<String> authenticatedPost(String path, String json, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.POST,
                new HttpEntity<>(json, headers), String.class);
    }

    // ── inner classes ────────────────────────────────────────────────────────────

    /**
     * Registers a counting advisor immediately after DemoModeAdvisor so the test can prove
     * the chain never advances past the short-circuit in demo mode.
     */
    @TestConfiguration
    static class NoNetworkProofConfig {
        static final AtomicInteger NEXT_CALL_COUNT = new AtomicInteger(0);

        @Bean
        CallAdvisor chatCountingCallAdvisor() {
            return new CountingCallAdvisor();
        }
    }

    /** Counts every time the chain advances into it (DemoModeAdvisor did NOT short-circuit). */
    static class CountingCallAdvisor implements CallAdvisor {
        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            NoNetworkProofConfig.NEXT_CALL_COUNT.incrementAndGet();
            return chain.nextCall(request);
        }

        @Override
        public String getName() {
            return "ChatCountingCallAdvisor";
        }

        @Override
        public int getOrder() {
            // Just after DemoModeAdvisor (HIGHEST_PRECEDENCE) — reached only if it does not short-circuit
            return Ordered.HIGHEST_PRECEDENCE + 1;
        }
    }
}
