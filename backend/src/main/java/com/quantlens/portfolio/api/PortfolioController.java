package com.quantlens.portfolio.api;

import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import com.quantlens.portfolio.service.PortfolioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * REST controller exposing portfolio analytics endpoints under {@code /api/portfolio}.
 *
 * <h3>Endpoints (Plans 02–03)</h3>
 * <dl>
 *   <dt>GET /api/portfolio/holdings</dt>
 *   <dd>Authenticated — returns the current user's holdings with per-position P&amp;L and weights.</dd>
 *   <dt>GET /api/portfolio/allocation</dt>
 *   <dd>Authenticated — returns sector allocation slices whose weights sum to exactly 1.000000.</dd>
 *   <dt>GET /api/portfolio/pnl</dt>
 *   <dd>Authenticated — returns portfolio-level P&amp;L totals and a 504-entry constant-current-holdings
 *       equity curve starting 2022-09-12.</dd>
 *   <dt>GET /api/portfolio/benchmark</dt>
 *   <dd>Authenticated — returns parallel date/portfolioSeries/benchmarkSeries arrays, both series
 *       independently rebased to 100.0000 on day 0.</dd>
 * </dl>
 *
 * <h3>IDOR prevention (T-02-01)</h3>
 * The portfolio identity is derived <em>exclusively</em> from the authenticated principal via
 * {@link #resolvePortfolioId(Authentication)} — mirroring the
 * {@code AuthController.me()} chain from Phase 1 (lines 84–99). No {@code @RequestParam}
 * or {@code @PathVariable} accepts a portfolio or user identifier anywhere in this class.
 *
 * <h3>Module boundary note</h3>
 * {@code PortfolioController} is inside {@code com.quantlens.portfolio.api}, which is a
 * sub-package of the {@code portfolio} module. No additional {@code @NamedInterface} is
 * needed — the {@code api} package is consumed only by HTTP clients, not other modules.
 */
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final AppUserRepository appUserRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;

    public PortfolioController(AppUserRepository appUserRepository,
                               PortfolioRepository portfolioRepository,
                               PortfolioService portfolioService) {
        this.appUserRepository = appUserRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
    }

    // =========================================================================
    // Endpoints
    // =========================================================================

    /**
     * Returns the authenticated user's holdings with per-position P&amp;L and portfolio weights.
     * <p>
     * {@code @Transactional(readOnly = true)} is redundant here (PortfolioService is already
     * class-level read-only transactional) but is retained for self-documentation.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with holdings list; 401 if unauthenticated; 404 if no portfolio found
     */
    @GetMapping("/holdings")
    @Transactional(readOnly = true)
    public ResponseEntity<List<HoldingDto>> getHoldings(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(portfolioService.getHoldings(portfolioId));
    }

    /**
     * Returns the authenticated user's sector allocation breakdown.
     * <p>
     * Weights across all returned slices sum to exactly {@code 1.000000} via
     * last-slice residual absorption in {@link PortfolioService#getAllocation(Long)}.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with allocation slices; 401 if unauthenticated; 404 if no portfolio found
     */
    @GetMapping("/allocation")
    @Transactional(readOnly = true)
    public ResponseEntity<List<AllocationSliceDto>> getAllocation(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(portfolioService.getAllocation(portfolioId));
    }

    /**
     * Returns the authenticated user's portfolio-level P&amp;L with a full equity curve.
     * <p>
     * The equity curve uses the constant-current-holdings assumption: current position
     * quantities valued across every historical trading day in the seeded 504-day window
     * starting 2022-09-12. Dates in the {@link DateValueDto} entries serialize as ISO-8601
     * strings (e.g. {@code "2022-09-12"}) via Spring Boot's auto-registered JavaTimeModule.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with P&amp;L summary and 504-entry equity curve; 401/404 on auth/portfolio errors
     */
    @GetMapping("/pnl")
    @Transactional(readOnly = true)
    public PortfolioPnlDto getPnl(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return portfolioService.getPortfolioPnl(portfolioId);
    }

    /**
     * Returns a benchmark comparison for the authenticated user's portfolio vs SPX500.
     * <p>
     * Both series are independently rebased to exactly {@code 100.0000} on day 0
     * (the first shared trading day: 2022-09-12). The {@code dates} array contains ISO-8601
     * strings for ECharts xAxis binding. All three arrays have the same length (504).
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with parallel arrays: dates, portfolioSeries, benchmarkSeries; 401/404 on errors
     */
    @GetMapping("/benchmark")
    @Transactional(readOnly = true)
    public BenchmarkComparisonDto getBenchmark(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return portfolioService.getBenchmarkComparison(portfolioId);
    }

    // =========================================================================
    // Private helper — principal resolution (mirrors AuthController.me() lines 84–99)
    // =========================================================================

    /**
     * Derives the authenticated user's portfolio ID from the Spring Security principal.
     * <p>
     * This is the ONLY place in {@code PortfolioController} where a portfolio identity
     * is resolved. The chain:
     * <ol>
     *   <li>{@code Authentication.getName()} → username (from session)</li>
     *   <li>{@code AppUserRepository.findByUsername(username)} → {@code AppUser}</li>
     *   <li>{@code PortfolioRepository.findByUserId(userId)} → first portfolio</li>
     * </ol>
     * The portfolioId is NEVER accepted as a {@code @RequestParam} or {@code @PathVariable}
     * (IDOR prevention — RESEARCH.md Security Domain V4, threat T-02-01).
     *
     * @param authentication the Spring Security authentication object
     * @return the resolved portfolio ID
     * @throws ResponseStatusException 401 if unauthenticated or user not found
     * @throws ResponseStatusException 404 if the user has no portfolio
     */
    private Long resolvePortfolioId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        String username = authentication.getName();
        var user = appUserRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());
        if (portfolios.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio found");
        }
        return portfolios.get(0).getId();
    }
}
