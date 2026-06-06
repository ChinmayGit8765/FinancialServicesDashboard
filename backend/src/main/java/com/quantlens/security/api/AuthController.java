package com.quantlens.security.api;

import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the auth endpoints consumed by the Vue SPA.
 *
 * <h3>Endpoints</h3>
 * <dl>
 *   <dt>GET /api/auth/personas</dt>
 *   <dd>Public — returns the three demo personas with a shared password hint so
 *       the login screen can render the one-click switcher without a prior login.</dd>
 *   <dt>GET /api/auth/me</dt>
 *   <dd>Authenticated — returns the current user's username, persona label, and
 *       portfolio ID (proves AUTH-02: session scopes the portfolio).</dd>
 * </dl>
 *
 * <h3>Module boundary note</h3>
 * This controller is in {@code com.quantlens.security.api} and reads from
 * {@code portfolio.domain} which is exposed as a Spring Modulith {@code @NamedInterface}.
 * The security module declares {@code allowedDependencies = {"portfolio::domain"}} in its
 * {@code package-info.java}, so {@code QuantLensModulithTest} remains green.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String DEMO_PASSWORD_HINT = "demo1234";

    private final AppUserRepository appUserRepository;
    private final PortfolioRepository portfolioRepository;

    public AuthController(AppUserRepository appUserRepository,
                          PortfolioRepository portfolioRepository) {
        this.appUserRepository = appUserRepository;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * Lists the three seeded demo personas for the one-click login switcher.
     * <p>
     * This endpoint is public (no session required) so the login page can render
     * the persona buttons before the user logs in.  The {@code passwordHint} field
     * tells the user what password to type — acceptable for a demo app.
     */
    @GetMapping("/personas")
    public List<PersonaDto> personas() {
        return appUserRepository.findAll().stream()
                .map(user -> new PersonaDto(user.getUsername(), user.getPersona(), DEMO_PASSWORD_HINT))
                .toList();
    }

    /**
     * Returns the current authenticated user's identity and portfolio reference.
     * <p>
     * The portfolio ID proves AUTH-02: each persona's session is scoped to their own
     * portfolio.  Calling this endpoint with different session cookies (alice, bob, charlie)
     * must return three distinct portfolio IDs.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with {@link MeDto}, or 401 if unauthenticated (handled by SecurityConfig)
     */
    @GetMapping("/me")
    public ResponseEntity<MeDto> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).build();
        }

        String username = authentication.getName();

        return appUserRepository.findByUsername(username)
                .map(user -> {
                    List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());
                    Long portfolioId = portfolios.isEmpty() ? null : portfolios.get(0).getId();
                    return ResponseEntity.ok(new MeDto(user.getUsername(), user.getPersona(), portfolioId));
                })
                .orElse(ResponseEntity.status(401).build());
    }
}
