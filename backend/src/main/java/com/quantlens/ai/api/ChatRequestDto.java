package com.quantlens.ai.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for POST /api/ai/chat.
 *
 * @param message        the user's question (required; max 2000 chars — T-07-DOS DoS prevention)
 * @param conversationId optional — server falls back to HTTP session ID if null or blank
 *                       (T-07-IDOR: conversationId is server-derived; arbitrary client IDs
 *                       are only honoured if provided — memory is scoped to sessionId by default)
 */
public record ChatRequestDto(
        @NotBlank @Size(max = 2000) String message,
        String conversationId
) {}
