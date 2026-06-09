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
 * Integration tests proving that demo mode reads ONLY from the DB seed — no provider call.
 *
 * <h3>EXECUTABLE no-network proof (T-06-03b)</h3>
 * A test-only {@link CountingCallAdvisor} bean is registered with order
 * {@code HIGHEST_PRECEDENCE + 1} — i.e. immediately AFTER {@link com.quantlens.ai.chat.DemoModeAdvisor}
 * ({@code HIGHEST_PRECEDENCE}). {@code ChatClientStrategy} injects all {@link CallAdvisor}
 * beans, so this advisor sits just below DemoModeAdvisor in the chain. In demo mode
 * DemoModeAdvisor short-circuits and NEVER calls {@code chain.nextCall()}, so the counting
 * advisor's {@code adviseCall} is never reached and the counter stays at 0. That zero is the
 * executable proof that no provider/network call occurred — asserted, not inferred from the
 * absence of a key.
 *
 * <p>This test MUST NOT mock a live provider — the demo path reads the DB only.
 */
@Import(AiDemoModeIntegrationTest.NoNetworkProofConfig.class)
class AiDemoModeIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @BeforeEach
    void resetCounter() {
        NoNetworkProofConfig.NEXT_CALL_COUNT.set(0);
    }

    /**
     * Demo mode explain returns 200 with seeded, non-blank content AND proves zero network:
     * the downstream-chain counter must be 0 (DemoModeAdvisor short-circuited).
     */
    @Test
    void demoMode_explain_returnsSeededContent_withZeroNetworkCalls() {
        String cookie = loginAndGetSessionCookie("alice");

        ResponseEntity<String> response = authenticatedGet("/api/ai/explain/AAPL", cookie);

        assertThat(response.getStatusCode())
                .as("Demo mode GET /api/ai/explain/AAPL must return 200 (no real key needed)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("Demo explain must return seeded non-blank narrative")
                .contains("narrative")
                .doesNotContain("\"narrative\":\"\"");
        assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
                .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode")
                .isZero();
    }

    /**
     * Demo mode commentary returns 200 with seeded, non-blank content AND proves zero network.
     */
    @Test
    void demoMode_commentary_returnsSeededContent_withZeroNetworkCalls() {
        String cookie = loginAndGetSessionCookie("alice");

        ResponseEntity<String> response = authenticatedGet("/api/ai/commentary", cookie);

        assertThat(response.getStatusCode())
                .as("Demo mode GET /api/ai/commentary must return 200 (no real key needed)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("Demo commentary must return seeded non-blank headline")
                .contains("headline")
                .doesNotContain("\"headline\":\"\"");
        assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
                .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode")
                .isZero();
    }

    /**
     * Demo mode structured output returns 200 with seeded, non-blank content AND proves zero network:
     * the downstream-chain counter must be 0 (DemoModeAdvisor short-circuited before any provider call).
     *
     * <p>This is the executable no-network proof for T-08-DEMO-NET (structured path). The
     * {@link CountingCallAdvisor} sits at {@code HIGHEST_PRECEDENCE + 1}
     * (immediately after DemoModeAdvisor). In demo mode DemoModeAdvisor short-circuits and never
     * calls {@code chain.nextCall()}, so the counter stays zero — provably offline.
     */
    @Test
    void demoMode_structured_returnsSeededContent_withZeroNetworkCalls() {
        String cookie = loginAndGetSessionCookie("alice");

        ResponseEntity<String> response = authenticatedGet("/api/ai/structured", cookie);

        assertThat(response.getStatusCode())
                .as("Demo mode GET /api/ai/structured must return 200 (no real key needed)")
                .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
                .as("Demo structured must return seeded non-blank title")
                .contains("title")
                .doesNotContain("\"title\":\"\"");
        assertThat(NoNetworkProofConfig.NEXT_CALL_COUNT.get())
                .as("EXECUTABLE no-network proof: chain.nextCall() must never fire in demo mode (T-08-DEMO-NET)")
                .isZero();
    }

    /**
     * Key-less app startup smoke gate (A4): the context boots with sentinel keys only
     * (spring.ai.chat.client.enabled=false disables the ambiguous ChatClient bean), and
     * /api/ai/status reports demo mode when no key has been set.
     */
    @Test
    void keylessStartup_statusReportsDemoMode() {
        String cookie = loginAndGetSessionCookie("alice");
        ResponseEntity<String> statusResponse = authenticatedGet("/api/ai/status", cookie);
        assertThat(statusResponse.getStatusCode())
                .as("GET /api/ai/status should return 200 — proves context booted with sentinel keys (A4)")
                .isEqualTo(HttpStatus.OK);
        assertThat(statusResponse.getBody())
                .as("Status must show demo mode when no key has been set")
                .contains("\"mode\":\"demo\"");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────

    private String loginAndGetSessionCookie(String username) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("username", username);
        body.add("password", "demo1234");
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/auth/login", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
        assertThat(response.getStatusCode()).as("Login for %s should succeed", username).isEqualTo(HttpStatus.OK);
        return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
    }

    private ResponseEntity<String> authenticatedGet(String path, String sessionCookie) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.COOKIE, sessionCookie);
        return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    /**
     * Registers a counting advisor immediately after DemoModeAdvisor so the test can prove
     * the chain never advances past the short-circuit in demo mode.
     */
    @TestConfiguration
    static class NoNetworkProofConfig {
        static final AtomicInteger NEXT_CALL_COUNT = new AtomicInteger(0);

        @Bean
        CallAdvisor countingCallAdvisor() {
            return new CountingCallAdvisor();
        }
    }

    /** Counts every time the chain advances into it (i.e. DemoModeAdvisor did NOT short-circuit). */
    static class CountingCallAdvisor implements CallAdvisor {
        @Override
        public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
            NoNetworkProofConfig.NEXT_CALL_COUNT.incrementAndGet();
            return chain.nextCall(request);
        }

        @Override
        public String getName() {
            return "CountingCallAdvisor";
        }

        @Override
        public int getOrder() {
            // Just after DemoModeAdvisor (HIGHEST_PRECEDENCE) — reached only if it does not short-circuit
            return Ordered.HIGHEST_PRECEDENCE + 1;
        }
    }
}
