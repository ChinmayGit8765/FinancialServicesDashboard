package com.quantlens.security.api;

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
public record PersonaDto(String username, String persona, String passwordHint) {
}
