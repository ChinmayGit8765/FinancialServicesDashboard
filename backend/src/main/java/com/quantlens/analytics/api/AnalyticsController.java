package com.quantlens.analytics.api;

import com.quantlens.analytics.service.CointegrationScanner;
import com.quantlens.analytics.service.CorrelationCalculator;
import com.quantlens.analytics.service.FamaFrenchCalculator;
import com.quantlens.analytics.service.RiskCalculator;
import com.quantlens.portfolio.domain.PortfolioRepository;
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
 * REST controller exposing analytics endpoints under {@code /api/portfolio}.
 *
 * <h3>Endpoints</h3>
 * <dl>
 *   <dt>GET /api/portfolio/risk</dt>
 *   <dd>Authenticated — returns the risk scorecard (Sharpe, vol, drawdown, beta, VaR).</dd>
 *   <dt>GET /api/portfolio/correlation</dt>
 *   <dd>Authenticated — returns the pairwise return-correlation matrix.</dd>
 *   <dt>GET /api/portfolio/attribution</dt>
 *   <dd>Authenticated — returns Fama-French 3-factor attribution.</dd>
 *   <dt>GET /api/portfolio/pairs</dt>
 *   <dd>Authenticated — returns cointegration pairs scan results.</dd>
 * </dl>
 *
 * <h3>IDOR prevention (T-04-01)</h3>
 * The portfolio identity is derived <em>exclusively</em> from the authenticated principal via
 * {@link #resolvePortfolioId(Authentication)}. No {@code @RequestParam} or {@code @PathVariable}
 * accepts a portfolio or user identifier anywhere in this class.
 *
 * <h3>Module boundary</h3>
 * {@code AnalyticsController} is inside {@code com.quantlens.analytics.api}, a sub-package
 * of the {@code analytics} module. Allowed cross-module reads: {@code marketdata::domain}
 * and {@code portfolio::domain} (declared in {@code analytics/package-info.java}).
 */
@RestController
@RequestMapping("/api/portfolio")
public class AnalyticsController {

    private final PortfolioRepository portfolioRepository;
    private final RiskCalculator riskCalculator;
    private final CorrelationCalculator correlationCalculator;
    private final FamaFrenchCalculator ffCalculator;
    private final CointegrationScanner cointegrationScanner;

    public AnalyticsController(PortfolioRepository portfolioRepository,
                               RiskCalculator riskCalculator,
                               CorrelationCalculator correlationCalculator,
                               FamaFrenchCalculator ffCalculator,
                               CointegrationScanner cointegrationScanner) {
        this.portfolioRepository = portfolioRepository;
        this.riskCalculator = riskCalculator;
        this.correlationCalculator = correlationCalculator;
        this.ffCalculator = ffCalculator;
        this.cointegrationScanner = cointegrationScanner;
    }

    // =========================================================================
    // Endpoints
    // =========================================================================

    /**
     * Returns the authenticated user's risk scorecard.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with risk scorecard; 401 if unauthenticated or portfolio not found
     */
    @GetMapping("/risk")
    @Transactional(readOnly = true)
    public ResponseEntity<RiskScorecardDto> getRisk(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(riskCalculator.computeRiskScorecard(portfolioId));
    }

    /**
     * Returns the authenticated user's pairwise return-correlation matrix.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with correlation matrix; 401 if unauthenticated or portfolio not found
     */
    @GetMapping("/correlation")
    @Transactional(readOnly = true)
    public ResponseEntity<CorrelationMatrixDto> getCorrelation(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(correlationCalculator.computeCorrelationMatrix(portfolioId));
    }

    /**
     * Returns the authenticated user's Fama-French 3-factor attribution.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with attribution result; 401 if unauthenticated or portfolio not found
     */
    @GetMapping("/attribution")
    @Transactional(readOnly = true)
    public ResponseEntity<AttributionDto> getAttribution(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(ffCalculator.computeAttribution(portfolioId));
    }

    /**
     * Returns the authenticated user's cointegration pairs scan.
     *
     * @param authentication injected by Spring Security from the current session
     * @return 200 with pairs list (empty until Plan 04-03); 401 if unauthenticated or portfolio not found
     */
    @GetMapping("/pairs")
    @Transactional(readOnly = true)
    public ResponseEntity<List<PairResultDto>> getPairs(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(cointegrationScanner.scanPairs(portfolioId));
    }

    // =========================================================================
    // Private helper — principal resolution (copied verbatim from PortfolioController)
    // =========================================================================

    /**
     * Derives the authenticated user's portfolio ID from the Spring Security principal.
     * <p>
     * This is the ONLY place in {@code AnalyticsController} where a portfolio identity
     * is resolved. Uses a single JPQL correlated-subquery via
     * {@link PortfolioRepository#findPortfolioIdByUsername(String)}.
     * <p>
     * Both "user not found" and "user has no portfolio" return 401, preventing
     * an attacker from distinguishing valid usernames (IDOR prevention T-04-01, T-04-02).
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
