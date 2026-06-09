package com.quantlens.ai.api;

/**
 * Response DTO for {@code GET /api/ai/explain/{ticker}}.
 *
 * <p>In demo mode the narrative is the authored seed content from {@code ai_seed_content}
 * (type={@code "EXPLAIN_POSITION"}, subjectId=ticker). In live mode it is the real
 * LLM response.
 *
 * @param narrative the position explanation narrative text
 */
public record ExplainResponseDto(
        String narrative
) {}
