package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc-based CSRF and clearKey integration tests for {@code AiKeyController}.
 *
 * <p>These tests use {@code MockMvc} with {@code @WithMockUser} because the CSRF token
 * lifecycle (deferred loading, cookie materialization) is difficult to replicate accurately
 * in {@code TestRestTemplate}-based tests where cookies are managed manually. MockMvc's
 * {@code SecurityMockMvcRequestPostProcessors.csrf()} provides authoritative CSRF test support.
 *
 * <h3>CR-01: DELETE requires CSRF token</h3>
 * {@link #deleteKey_withoutCsrfToken_returns403()} proves that DELETE /api/ai/key is NOT
 * CSRF-exempt — omitting the token results in 403.
 *
 * {@link #clearKey_withCsrfToken_returns200_withDemoMode()} proves that DELETE /api/ai/key
 * succeeds and returns {@code mode="demo"} when the CSRF token is present.
 */
@AutoConfigureMockMvc
class AiKeyControllerCsrfTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * CR-01: DELETE /api/ai/key without a CSRF token must return 403 Forbidden.
     *
     * <p>Before the fix, {@code DELETE /api/ai/key} was CSRF-exempt (method-agnostic
     * path matcher). After the fix, only {@code POST /api/ai/key} is exempt. This test
     * proves the fix is effective — a CSRF attack that forces DELETE without the token
     * will be rejected.
     */
    @Test
    @WithMockUser(username = "alice")
    void deleteKey_withoutCsrfToken_returns403() throws Exception {
        mockMvc.perform(delete("/api/ai/key"))
                // No .with(csrf()) — token is absent
                .andExpect(status().isForbidden());
    }

    /**
     * CR-01 + clearKey: DELETE /api/ai/key with a valid CSRF token must return 200
     * with {@code mode="demo"}.
     *
     * <p>This is the authoritative clearKey integration test (replacing the
     * {@code TestRestTemplate}-based version which cannot easily obtain the CSRF token
     * with Spring Security 6 deferred CSRF loading).
     */
    @Test
    @WithMockUser(username = "alice")
    void clearKey_withCsrfToken_returns200_withDemoMode() throws Exception {
        // First set a key — POST is CSRF-exempt, so no token needed
        mockMvc.perform(post("/api/ai/key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"openai\",\"apiKey\":\"test-clear-key\"}")
                        .with(csrf()))   // csrf() is still correct here — exempt means it's ignored
                .andExpect(status().isOk());

        // Then clear it — DELETE requires CSRF token
        mockMvc.perform(delete("/api/ai/key").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("demo"));
    }
}
