package com.quantlens.ai.api;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for {@code POST /api/ai/key}.
 *
 * <p>This record is a request-only DTO — it is deserialised from JSON and never
 * serialised back to JSON. The {@code apiKey} field is validated as {@code @NotBlank}
 * and consumed only within the server process (NEVER echoed in any response).
 *
 * @param provider the LLM provider: {@code "anthropic"} or {@code "openai"}
 * @param apiKey   the user-supplied API key (session-only; never persisted or logged)
 */
public record AiKeyRequest(
        @NotBlank String provider,
        @NotBlank String apiKey
) {}
