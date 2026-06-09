package com.quantlens.ai.api;

import java.util.List;

/**
 * Response DTO for POST /api/ai/chat.
 *
 * @param answer    the generated (or demo-authored) answer text
 * @param citations retrieved 10-K chunks that grounded the answer; empty list in demo mode
 *                  (citations in demo mode come from authored seed content, not RETRIEVED_DOCUMENTS)
 */
public record ChatResponseDto(
        String answer,
        List<CitationDto> citations
) {}
