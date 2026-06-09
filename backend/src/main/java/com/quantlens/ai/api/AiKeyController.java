package com.quantlens.ai.api;

import com.quantlens.ai.session.LlmKeySessionHolder;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.MethodArgumentNotValidException;

/**
 * REST controller for LLM API key management.
 *
 * <h3>Endpoints</h3>
 * <dl>
 *   <dt>POST /api/ai/key</dt>
 *   <dd>Submit provider + API key — switches to live mode. Returns {@link AiStatusDto}
 *       with {@code mode="live"} and {@code provider}. NEVER returns the key.</dd>
 *   <dt>DELETE /api/ai/key</dt>
 *   <dd>Clear the session key — reverts to demo mode. Returns {@link AiStatusDto}
 *       with {@code mode="demo"}.</dd>
 *   <dt>GET /api/ai/status</dt>
 *   <dd>Returns current AI mode. Returns {@link AiStatusDto} with {@code mode} and
 *       {@code provider} only — NEVER the key (T-06-01 / RESEARCH Pitfall 5).</dd>
 * </dl>
 *
 * <h3>Security</h3>
 * All endpoints require an authenticated session (covered by {@code .anyRequest().authenticated()}
 * in {@code SecurityConfig}). Only {@code POST /api/ai/key} is CSRF-exempt (method-scoped
 * {@code AntPathRequestMatcher.antMatcher(POST, ...)} — before the first authenticated request
 * cycle the CSRF cookie may not yet be set). {@code DELETE /api/ai/key} requires the
 * {@code X-XSRF-TOKEN} header, which the Axios interceptor supplies automatically after login.
 * (CR-01 fix: the prior method-agnostic path exemption inadvertently exempted DELETE too.)
 * {@link #handleIllegalArgument(IllegalArgumentException)} returns a generic error message —
 * the exception detail (which may contain a provider name) is never forwarded (Pitfall 5).
 */
@RestController
@RequestMapping("/api/ai")
public class AiKeyController {

    private final LlmKeySessionHolder keyHolder;

    public AiKeyController(LlmKeySessionHolder keyHolder) {
        this.keyHolder = keyHolder;
    }

    /**
     * Sets the provider and API key for this session, switching to live mode.
     *
     * <p>The key is accepted one-way into the session and never returned. The
     * provider whitelist ({@code anthropic}, {@code openai}) is enforced here
     * before delegating to {@link LlmKeySessionHolder#setKey(String, String)}.
     *
     * @param request the validated key request (provider + apiKey, both @NotBlank)
     * @return 200 with {@code {mode:"live", provider}} on success; 400 on invalid provider
     */
    @PostMapping("/key")
    public ResponseEntity<AiStatusDto> setKey(@RequestBody @Valid AiKeyRequest request) {
        if (!"anthropic".equals(request.provider()) && !"openai".equals(request.provider())) {
            // Return bad request with no detail — provider name is not sensitive but
            // we keep the error generic to avoid leaking validation logic (Pitfall 5)
            return ResponseEntity.badRequest().build();
        }
        keyHolder.setKey(request.provider(), request.apiKey());
        // NEVER return the key — only mode + provider (T-06-01)
        return ResponseEntity.ok(new AiStatusDto("live", request.provider()));
    }

    /**
     * Clears the session key, reverting to demo mode.
     *
     * @return 200 with {@code {mode:"demo"}} (provider omitted by @JsonInclude NON_NULL)
     */
    @DeleteMapping("/key")
    public ResponseEntity<AiStatusDto> clearKey() {
        keyHolder.clear();
        return ResponseEntity.ok(new AiStatusDto("demo", null));
    }

    /**
     * Returns the current AI mode for the mode badge.
     *
     * <p>This is the source of truth for the frontend badge component — it only
     * reflects whether a key is set and which provider, never the key itself.
     *
     * @return {@code {mode:"live", provider}} or {@code {mode:"demo"}}
     */
    @GetMapping("/status")
    public AiStatusDto getStatus() {
        if (keyHolder.hasKey()) {
            return new AiStatusDto("live", keyHolder.getProvider());
        }
        return new AiStatusDto("demo", null);
    }

    /**
     * Generic error handler for bean-validation failures ({@link MethodArgumentNotValidException}).
     *
     * <p>Spring Boot's default error serializer includes the field name in the {@code errors[].field}
     * property (e.g. {@code "field":"apiKey"}), which reveals the API parameter name to callers.
     * This handler replaces that with a generic body containing no field names or submitted values.
     * (CR-02: information disclosure via default validation error body.)
     *
     * @param ex the caught validation exception
     * @return 400 with a generic error body — no field enumeration
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidation(MethodArgumentNotValidException ex) {
        // Never include field names, messages, or rejected values — generic body only (CR-02)
        return ResponseEntity.badRequest().body("{\"error\":\"Invalid request\"}");
    }

    /**
     * Generic error handler for {@link IllegalArgumentException}.
     *
     * <p>Returns a generic error body — exception message is never forwarded, which
     * prevents any exception detail (which might reference the provider or key context)
     * from leaking to the client (RESEARCH Pitfall 5).
     *
     * @param ex the caught exception
     * @return 400 with a generic error body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body("{\"error\":\"Invalid configuration\"}");
    }
}
