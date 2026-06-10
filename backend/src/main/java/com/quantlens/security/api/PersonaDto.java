package com.quantlens.security.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing a demo persona for the one-click login switcher.
 * <p>
 * Returned by {@code GET /api/auth/personas} — a public endpoint that the Vue login
 * page calls to populate the persona switcher buttons.
 *
 * @param username     the login username (alice, bob, charlie)
 * @param persona      the persona style label (Growth, Income, Balanced)
 * @param passwordHint the shared demo password shown on the login screen ("demo1234")
 */
public record PersonaDto(
        String username,
        String persona,
        // Phase 10 (T-10-01): the value is intentionally public (shown on the login screen), but the
        // "passwordHint" field name is not advertised in the machine-readable OpenAPI spec. @Schema
        // hides it from the doc only — Jackson still serialises it, so the login page is unaffected.
        @Schema(hidden = true) String passwordHint) {
}
