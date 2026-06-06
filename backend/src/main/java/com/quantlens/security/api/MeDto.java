package com.quantlens.security.api;

/**
 * DTO returned by {@code GET /api/auth/me} for the authenticated user.
 * <p>
 * Contains the minimal information the Vue dashboard needs to show the current
 * persona and to scope API calls to the correct portfolio.
 *
 * @param username    the authenticated user's login name
 * @param persona     the persona style label (Growth, Income, Balanced)
 * @param portfolioId the ID of the portfolio belonging to this user — proves AUTH-02 scoping
 */
public record MeDto(String username, String persona, Long portfolioId) {
}
