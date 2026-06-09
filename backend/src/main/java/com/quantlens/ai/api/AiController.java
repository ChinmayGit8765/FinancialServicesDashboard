package com.quantlens.ai.api;

import com.quantlens.ai.service.ChatService;
import com.quantlens.ai.service.CommentaryService;
import com.quantlens.ai.service.ExplainPositionService;
import com.quantlens.ai.service.StructuredOutputService;
import com.quantlens.portfolio.domain.PortfolioRepository;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
 *       matches the seed (type=EXPLAIN_POSITION, subjectId=ticker).
 *       <strong>CR-05:</strong> ticker is constrained to {@code ^[A-Z]{1,10}$} via
 *       {@code @Pattern} — malformed tickers (e.g. with URL-encoded newlines or
 *       prompt-injection characters) are rejected with 400 before reaching the service.</dd>
 *   <dt>GET /api/ai/commentary</dt>
 *   <dd>Returns a daily AI portfolio commentary scoped to the authenticated user's persona.</dd>
 * </dl>
 *
 * <h3>IDOR prevention (T-04-01 pattern applied here)</h3>
 * The portfolio identity is derived <em>exclusively</em> from the authenticated principal via
 * {@link #resolvePortfolioId(Authentication)} — copied verbatim from {@code AnalyticsController}.
 * No {@code @RequestParam} or numeric id {@code @PathVariable} accepts a portfolio identity.
 *
 * <h3>Ticker validation (CR-05)</h3>
 * {@code @Validated} on the class activates constraint validation for {@code @PathVariable}
 * parameters. The {@code @Pattern(regexp="^[A-Z]{1,10}$")} on the {@code ticker} parameter
 * ensures only uppercase letter-only ticker symbols reach the service. Spring's
 * {@code @PathVariable} decodes percent-encoding — so {@code AAPL%0A} would become
 * {@code AAPL\n}, which would be injected into the LLM prompt without this guard.
 *
 * <h3>Module boundary</h3>
 * {@code AiController} is inside {@code com.quantlens.ai.api}. Cross-module reads:
 * {@code portfolio::domain} (PortfolioRepository — declared in {@code ai/package-info.java}).
 */
@Validated
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final PortfolioRepository portfolioRepository;
    private final ExplainPositionService explainService;
    private final CommentaryService commentaryService;
    private final ChatService chatService;
    private final StructuredOutputService structuredOutputService;

    public AiController(PortfolioRepository portfolioRepository,
                        ExplainPositionService explainService,
                        CommentaryService commentaryService,
                        ChatService chatService,
                        StructuredOutputService structuredOutputService) {
        this.portfolioRepository      = portfolioRepository;
        this.explainService           = explainService;
        this.commentaryService        = commentaryService;
        this.chatService              = chatService;
        this.structuredOutputService  = structuredOutputService;
    }

    /**
     * Returns an AI-generated explanation for the named position.
     *
     * <p>The {@code ticker} path variable is the stock ticker symbol (e.g. {@code "AAPL"}).
     * This is the contract established in Phase 6 for all downstream plans (06-02/03/04).
     *
     * <p>CR-05: {@code @Pattern(regexp="^[A-Z]{1,10}$")} rejects malformed tickers (e.g.
     * those containing URL-decoded newlines or other prompt-injection characters) with 400
     * before the value ever reaches {@code ExplainPositionService} or an LLM prompt.
     *
     * @param ticker         the ticker symbol identifying the holding (e.g. {@code "AAPL"}),
     *                       must match {@code ^[A-Z]{1,10}$}
     * @param authentication the Spring Security principal
     * @return 200 with {@link ExplainResponseDto}; 400 on malformed ticker; 401 if
     *         unauthenticated or no portfolio; 404 if ticker not in user's holdings
     */
    @GetMapping("/explain/{ticker}")
    @Transactional(readOnly = true)
    public ResponseEntity<ExplainResponseDto> explain(
            @PathVariable @Pattern(regexp = "^[A-Z]{1,10}$",
                    message = "ticker must be 1-10 uppercase letters")
            String ticker,
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

    /**
     * Returns a typed structured-output insight DTO for the authenticated user's portfolio.
     *
     * <p>Demo mode: the {@code STRUCTURED_INSIGHT} seed for the user's persona is deserialized
     * via {@code ObjectMapper.readValue} — no provider call, no network.
     * Live mode: {@code BeanOutputConverter} generates the JSON schema and the LLM returns
     * a typed {@link StructuredInsightRecord}.
     *
     * <p>T-08-IDOR-ST: portfolio identity derived exclusively from the authenticated principal
     * via {@link #resolvePortfolioId(Authentication)}.
     *
     * @param authentication the Spring Security principal
     * @return 200 with {@link StructuredInsightRecord}; 401 if unauthenticated or no portfolio;
     *         502 on provider error
     */
    @GetMapping("/structured")
    @Transactional(readOnly = true)
    public ResponseEntity<StructuredInsightRecord> structured(Authentication authentication) {
        Long portfolioId = resolvePortfolioId(authentication);
        return ResponseEntity.ok(structuredOutputService.getInsight(portfolioId));
    }

    /**
     * Handles a freeform chat Q&A request routed through the advisor chain (RAG + memory).
     *
     * <p>In demo mode {@link com.quantlens.ai.chat.DemoModeAdvisor} short-circuits and returns
     * authored seed content. In live mode {@link com.quantlens.ai.chat.RagAdvisorConfig}
     * QuestionAnswerAdvisor retrieves relevant 10-K chunks and MessageChatMemoryAdvisor
     * provides conversation history.
     *
     * <p>T-07-IDOR: The conversationId is ALWAYS derived from the server-assigned HTTP session ID.
     * Any client-supplied {@code conversationId} in the request body is IGNORED. This prevents
     * cross-session memory poisoning (IDOR): without this fix, attacker B could supply
     * User A's sessionId as conversationId and read or inject into A's conversation history.
     *
     * @param request the chat request (message; conversationId field ignored — use session.getId())
     * @param session the HTTP session (sole source of the conversationId)
     * @return 200 with {@link ChatResponseDto}; 502 on provider error
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDto> chat(
            @RequestBody @Valid ChatRequestDto request,
            Authentication authentication,
            HttpSession session) {
        // CR-04/T-07-IDOR: ALWAYS use server-assigned session ID — never trust client-supplied conversationId.
        // A client providing an arbitrary conversationId could read or poison another user's memory.
        String conversationId = session.getId();
        return ResponseEntity.ok(chatService.chat(request.message(), conversationId));
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
