package com.quantlens.ai.api;

import com.quantlens.ai.service.CommentaryService;
import com.quantlens.ai.service.ExplainPositionService;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller exposing AI-powered portfolio endpoints.
 *
 * <h3>Endpoints</h3>
 * <dl>
 *   <dt>GET /api/ai/explain/{ticker}</dt>
 *   <dd>Returns an AI-generated explanation of the named holding position.
 *       The {@code ticker} path variable is a string ticker symbol (e.g. {@code "AAPL"}),
 *       NOT a numeric holdingId — this matches the {@code ai_seed_content.subject_id}
 *       column and {@link com.quantlens.portfolio.api.HoldingDto} which is keyed by ticker.
 *       <strong>Contract decision:</strong> ticker path avoids exposing internal DB IDs and
 *       matches the seed (type=EXPLAIN_POSITION, subjectId=ticker).</dd>
 *   <dt>GET /api/ai/commentary</dt>
 *   <dd>Returns a daily AI portfolio commentary scoped to the authenticated user's persona.</dd>
 * </dl>
 *
 * <h3>IDOR prevention (T-04-01 pattern applied here)</h3>
 * The portfolio identity is derived <em>exclusively</em> from the authenticated principal via
 * {@link #resolvePortfolioId(Authentication)} — copied verbatim from {@code AnalyticsController}.
 * No {@code @RequestParam} or numeric id {@code @PathVariable} accepts a portfolio identity.
 *
 * <h3>Module boundary</h3>
 * {@code AiController} is inside {@code com.quantlens.ai.api}. Cross-module reads:
 * {@code portfolio::domain} (PortfolioRepository — declared in {@code ai/package-info.java}).
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final PortfolioRepository portfolioRepository;
    private final ExplainPositionService explainService;
    private final CommentaryService commentaryService;

    public AiController(PortfolioRepository portfolioRepository,
                        ExplainPositionService explainService,
                        CommentaryService commentaryService) {
        this.portfolioRepository = portfolioRepository;
        this.explainService      = explainService;
        this.commentaryService   = commentaryService;
    }

    /**
     * Returns an AI-generated explanation for the named position.
     *
     * <p>The {@code ticker} path variable is the stock ticker symbol (e.g. {@code "AAPL"}).
     * This is the contract established in Phase 6 for all downstream plans (06-02/03/04).
     *
     * @param ticker         the ticker symbol identifying the holding (e.g. {@code "AAPL"})
     * @param authentication the Spring Security principal
     * @return 200 with {@link ExplainResponseDto}; 401 if unauthenticated or no portfolio
     */
    @GetMapping("/explain/{ticker}")
    @Transactional(readOnly = true)
    public ResponseEntity<ExplainResponseDto> explain(
            @PathVariable String ticker,
            Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(explainService.explain(portfolioId, ticker));
    }

    /**
     * Returns a daily AI portfolio commentary for the authenticated user's portfolio.
     *
     * @param authentication the Spring Security principal
     * @return 200 with {@link CommentaryDto}; 401 if unauthenticated or no portfolio
     */
    @GetMapping("/commentary")
    @Transactional(readOnly = true)
    public ResponseEntity<CommentaryDto> commentary(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(commentaryService.commentary(portfolioId));
    }

    // =========================================================================
    // Private helper — principal resolution (copied verbatim from AnalyticsController)
    // =========================================================================

    /**
     * Derives the authenticated user's portfolio ID from the Spring Security principal.
     *
     * <p>This is the ONLY place in {@code AiController} where a portfolio identity is
     * resolved. The portfolioId is NEVER accepted as a {@code @RequestParam} or
     * {@code @PathVariable} (IDOR prevention — T-04-01 pattern).
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
