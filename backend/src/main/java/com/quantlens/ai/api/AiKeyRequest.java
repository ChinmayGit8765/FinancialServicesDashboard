package com.quantlens.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for {@code POST /api/ai/key}.
 *
 * <p>This record is a request-only DTO — it is deserialised from JSON and never
 * serialised back to JSON. The {@code apiKey} field is validated as {@code @NotBlank}
 * and consumed only within the server process (NEVER echoed in any response).
 *
 * <p>{@code @Size(max = 200)} on both fields prevents resource exhaustion from
 * oversized payloads (WR-03). Real API keys are bounded (Anthropic ~40 chars,
 * OpenAI ~51 chars) — 200 chars is generous while eliminating the attack surface.
 *
 * @param provider the LLM provider: {@code "anthropic"} or {@code "openai"}
 * @param apiKey   the user-supplied API key (session-only; never persisted or logged)
 */
public record AiKeyRequest(
        @NotBlank @Size(max = 200) String provider,
        @NotBlank @Size(max = 200) String apiKey
) {}
