package com.quantlens.ai.api;

import java.util.List;

/**
 * Response DTO for {@code GET /api/ai/commentary}.
 *
 * <p>In demo mode the fields are populated from authored seed content (type={@code "DAILY_COMMENTARY"},
 * subjectId=persona key). In live mode they are parsed from the real LLM response.
 *
 * @param headline     one-line summary of today's portfolio posture
 * @param body         multi-sentence narrative body
 * @param bulletPoints key observations as a list of bullet strings
 */
public record CommentaryDto(
        String headline,
        String body,
        List<String> bulletPoints
) {}
