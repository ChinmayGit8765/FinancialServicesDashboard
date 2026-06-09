package com.quantlens.ai.api;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * DTO representing the current AI mode.
 *
 * <p>Returned by {@code GET /api/ai/status}, {@code POST /api/ai/key}, and
 * {@code DELETE /api/ai/key}. The {@code apiKey} is NEVER included — this record
 * only carries mode/provider state (RESEARCH Pitfall 5 / T-06-01).
 *
 * @param mode     {@code "demo"} when no key is set; {@code "live"} when a key is active
 * @param provider {@code "anthropic"} or {@code "openai"} in live mode; {@code null} in demo
 *                 ({@code @JsonInclude(NON_NULL)} omits null provider from the JSON output)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiStatusDto(
        String mode,
        String provider
) {}
