package com.quantlens.ai.service;

import com.quantlens.ai.api.ExplainResponseDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Service that generates a natural-language explanation for a portfolio position.
 *
 * <p>Routes through {@link ChatClientStrategy#forSession(LlmKeySessionHolder)} so that
 * {@link com.quantlens.ai.chat.DemoModeAdvisor} can short-circuit in demo mode and return
 * the authored seed content for the ticker from {@code ai_seed_content}. In live mode the
 * real LLM is invoked with the session key.
 *
 * <h2>IDOR safety (T-06-08)</h2>
 * The ticker is validated against the principal's holdings BEFORE any ChatClient call.
 * If the ticker is not in the portfolio, {@link HttpStatus#NOT_FOUND} is thrown immediately
 * and no seed lookup or provider call is ever made. The portfolioId is always derived from
 * the authenticated principal by the controller — never from a request parameter.
 *
 * <h2>No if(demoMode) branch</h2>
 * This service contains no demo/live conditional. The single demo/live switch lives
 * exclusively in {@link com.quantlens.ai.chat.DemoModeAdvisor}.
 */
@Service
@Transactional(readOnly = true)
public class ExplainPositionService {

    private static final String EXPLAIN_SYSTEM_PROMPT =
            "You are a concise, authoritative financial analyst. " +
            "Explain the given portfolio position in 2-3 clear paragraphs covering: " +
            "investment thesis, key financial metrics, and relevant risk factors. " +
            "Be specific, factual, and avoid generic platitudes.";

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PositionRepository positionRepository;

    public ExplainPositionService(ChatClientStrategy strategy,
                                   LlmKeySessionHolder keyHolder,
                                   PositionRepository positionRepository) {
        this.strategy           = strategy;
        this.keyHolder          = keyHolder;
        this.positionRepository = positionRepository;
    }

    /**
     * Returns a position explanation for the given ticker in the given portfolio.
     *
     * <p>The IDOR guard runs first: if the ticker is not in the principal's holdings,
     * {@link HttpStatus#NOT_FOUND} is thrown before any ChatClient call.
     *
     * @param portfolioId the authenticated user's portfolio ID (IDOR-safe — derived from principal)
     * @param ticker      the ticker symbol (e.g. {@code "AAPL"}) — matches {@code ai_seed_content.subject_id}
     * @return an {@link ExplainResponseDto} with the narrative
     * @throws ResponseStatusException 404 if the ticker is not in the principal's portfolio
     * @throws ResponseStatusException 502 on provider error (no message echo — T-06-09)
     */
    public ExplainResponseDto explain(Long portfolioId, String ticker) {
        // IDOR guard: validate ticker against principal's holdings BEFORE any ChatClient call
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);
        Position holding = positions.stream()
                .filter(p -> ticker.equalsIgnoreCase(p.getSecurity().getTicker()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Ticker not found in portfolio"));

        // Build a metrics summary from the matched holding for the live-mode prompt
        String metricsContext = buildMetricsContext(holding);

        // Route through the advisor chain — DemoModeAdvisor short-circuits in demo mode
        // by reading AI_SEED_TYPE/AI_SEED_SUBJECT from context; live mode calls the provider
        try {
            String content = strategy.forSession(keyHolder)
                    .prompt()
                    .system(EXPLAIN_SYSTEM_PROMPT)
                    .user("Explain the portfolio position for " + ticker + ". " + metricsContext)
                    .advisors(spec -> spec
                            .param("AI_SEED_TYPE", "EXPLAIN_POSITION")
                            .param("AI_SEED_SUBJECT", ticker.toUpperCase()))
                    .call()
                    .content();
            return new ExplainResponseDto(content != null ? content : "");
        } catch (ResponseStatusException rse) {
            // Let NOT_FOUND propagate (should not reach here — guard runs before this block)
            throw rse;
        } catch (Exception e) {
            // T-06-09: never echo provider error messages (could carry the key)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider temporarily unavailable");
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Builds a brief metrics summary string for the live-mode prompt.
     * In demo mode, DemoModeAdvisor ignores this and returns seed content directly.
     */
    private String buildMetricsContext(Position holding) {
        BigDecimal qty   = holding.getQuantity();
        String ticker    = holding.getSecurity().getTicker();
        String sector    = holding.getSecurity().getSector();
        BigDecimal cost  = holding.getAvgCostBasis();

        return String.format(
                "Metrics: ticker=%s, sector=%s, quantity=%s shares, " +
                "avg cost basis=%.2f per share.",
                ticker, sector, qty.toPlainString(),
                cost.setScale(2, RoundingMode.HALF_UP).doubleValue());
    }
}
