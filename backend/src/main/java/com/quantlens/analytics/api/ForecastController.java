package com.quantlens.analytics.api;

import com.quantlens.analytics.service.ForecastService;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller exposing the Monte Carlo forecast endpoint under {@code /api/portfolio}.
 *
 * <h3>Endpoint</h3>
 * <dl>
 *   <dt>GET /api/portfolio/forecast</dt>
 *   <dd>Authenticated — returns Monte Carlo percentile bands (p5/p25/p50/p75/p95) for the
 *       authenticated user's portfolio. Query params:
 *       <ul>
 *         <li>{@code model} — one of {@code GBM}, {@code JUMP_DIFFUSION}, {@code HESTON},
 *             {@code BOOTSTRAP}. Default: {@code GBM}. Unknown values → 400 Bad Request
 *             automatically via Spring's {@code MethodArgumentTypeMismatchException}
 *             (T-05-02 mitigation).</li>
 *         <li>{@code horizon} — trading days to project forward. Default: {@code 252}.
 *             Clamped to [1, 504] server-side (T-05-03 DoS mitigation).</li>
 *       </ul>
 *   </dd>
 * </dl>
 *
 * <h3>IDOR prevention (T-05-01)</h3>
 * The portfolio identity is derived <em>exclusively</em> from the authenticated principal via
 * {@link #resolvePortfolioId(Authentication)}. No {@code @RequestParam} or {@code @PathVariable}
 * accepts a portfolio or user identifier anywhere in this class. An attacker cannot enumerate
 * other users' forecasts by supplying a different portfolio ID.
 *
 * <h3>Horizon clamping (T-05-03)</h3>
 * The {@code horizon} parameter is clamped to [1, 504] via
 * {@code Math.max(1, Math.min(504, horizon))} before passing to the service.
 * This prevents a malicious client from requesting an unbounded simulation
 * (e.g., horizon=Integer.MAX_VALUE → 5000 paths × billions of steps).
 *
 * <h3>Module boundary</h3>
 * {@code ForecastController} is inside {@code com.quantlens.analytics.api}, a sub-package
 * of the {@code analytics} module. Allowed cross-module reads: {@code marketdata::domain}
 * and {@code portfolio::domain} (declared in {@code analytics/package-info.java}).
 */
@RestController
@RequestMapping("/api/portfolio")
public class ForecastController {

    private final PortfolioRepository portfolioRepository;
    private final ForecastService forecastService;

    public ForecastController(PortfolioRepository portfolioRepository,
                               ForecastService forecastService) {
        this.portfolioRepository = portfolioRepository;
        this.forecastService     = forecastService;
    }

    // =========================================================================
    // Endpoints
    // =========================================================================

    /**
     * Returns Monte Carlo percentile bands for the authenticated user's portfolio.
     *
     * <p>
     * The portfolio identity is resolved from the authenticated principal — never from
     * a request parameter (IDOR prevention T-05-01). The {@code model} parameter is typed
     * as {@link ModelType} so Spring MVC validates it automatically (T-05-02). The
     * {@code horizon} is clamped to [1, 504] (T-05-03 DoS guard).
     *
     * @param authentication injected by Spring Security from the current session
     * @param model          stochastic model to use (default GBM; unknown → 400 automatically)
     * @param horizon        trading days to project (default 252; clamped to [1, 504])
     * @return 200 with {@link ForecastDto};
     *         401 if unauthenticated, user not found, or user has no portfolio
     *         (both not-found and unauthenticated return 401 deliberately to prevent
     *          username enumeration — IDOR mitigation T-05-01)
     * @throws org.springframework.web.server.ResponseStatusException (401) on auth failure
     */
    @GetMapping("/forecast")
    public ResponseEntity<ForecastDto> getForecast(
            Authentication authentication,
            @RequestParam(defaultValue = "GBM") ModelType model,
            @RequestParam(defaultValue = "252") int horizon) {
        Long portfolioId = resolvePortfolioId(authentication);
        int clampedHorizon = Math.max(1, Math.min(504, horizon));
        return ResponseEntity.ok(forecastService.forecast(portfolioId, model, clampedHorizon));
    }

    // =========================================================================
    // Private helper — principal resolution (copied verbatim from AnalyticsController)
    // =========================================================================

    /**
     * Derives the authenticated user's portfolio ID from the Spring Security principal.
     * <p>
     * This is the ONLY place in {@code ForecastController} where a portfolio identity
     * is resolved. Uses a single JPQL correlated-subquery via
     * {@link PortfolioRepository#findPortfolioIdByUsername(String)}.
     * <p>
     * Both "user not found" and "user has no portfolio" return 401, preventing
     * an attacker from distinguishing valid usernames (IDOR prevention T-05-01).
     * The portfolioId is NEVER accepted as a {@code @RequestParam} or {@code @PathVariable}.
     *
     * @param authentication the Spring Security authentication object
     * @return the resolved portfolio ID
     * @throws ResponseStatusException 401 if unauthenticated, user not found, or no portfolio
     */
    private Long resolvePortfolioId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String username = authentication.getName();
        return portfolioRepository.findPortfolioIdByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
