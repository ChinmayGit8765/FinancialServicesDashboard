package com.quantlens.ai.session;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

/**
 * Session-scoped holder for the user-supplied LLM API key.
 *
 * <p>This bean lives in {@code com.quantlens.ai.session} (a sub-package of the
 * {@code ai} Modulith module). Spring creates a CGLIB scoped proxy automatically
 * when this bean is injected into singleton beans — each request resolves the
 * actual session-bound instance through the proxy (T-06-04, RESEARCH Pitfall 3).
 *
 * <h2>Security invariants (T-06-01)</h2>
 * <ul>
 *   <li>No {@code @ToString} — prevents accidental key logging.</li>
 *   <li>No {@code @JsonInclude} / {@code @JsonSerialize} — this bean is never
 *       serialised to JSON; {@code getApiKey()} is consumed only by
 *       {@code ChatClientStrategy} on the server side.</li>
 *   <li>{@code apiKey} is a plain non-static, non-volatile {@code private String}
 *       field — never stored in a static map or thread-local.</li>
 * </ul>
 *
 * <p>Key lifecycle: set on {@code POST /api/ai/key}; cleared on
 * {@code DELETE /api/ai/key} and on session invalidation at logout.
 */
@Component
@SessionScope
public class LlmKeySessionHolder {

    private String provider;   // "anthropic" | "openai" — null in demo mode
    private String apiKey;     // NEVER serialised, logged, or echoed in any response

    // ── state queries ─────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when a non-blank API key has been set for this session.
     *
     * @return {@code true} if live mode is active; {@code false} in demo mode
     */
    public boolean hasKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    /**
     * Returns the selected provider name.
     *
     * @return {@code "anthropic"}, {@code "openai"}, or {@code null} in demo mode
     */
    public String getProvider() { return provider; }

    /**
     * Returns the raw API key for use by {@code ChatClientStrategy}.
     *
     * <p>This value MUST NOT be included in any HTTP response, log line, or DTO.
     * It is used exclusively to construct a per-request {@code AnthropicApi} or
     * {@code OpenAiApi} instance inside the server process.
     *
     * @return the API key string, or {@code null} in demo mode
     */
    public String getApiKey() { return apiKey; }

    // ── mutators ──────────────────────────────────────────────────────────────

    /**
     * Sets the provider and API key for this session, switching to live mode.
     *
     * @param provider one of {@code "anthropic"} or {@code "openai"}
     * @param apiKey   the user-supplied API key (NEVER stored beyond this session)
     */
    public void setKey(String provider, String apiKey) {
        this.provider = provider;
        this.apiKey   = apiKey;
    }

    /**
     * Clears the provider and API key, reverting to demo mode.
     */
    public void clear() {
        this.provider = null;
        this.apiKey   = null;
    }
}
