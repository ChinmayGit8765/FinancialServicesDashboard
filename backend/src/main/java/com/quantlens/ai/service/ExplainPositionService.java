package com.quantlens.ai.service;

import com.quantlens.ai.api.ExplainResponseDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.service.PortfolioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service that generates a natural-language explanation for a portfolio position.
 *
 * <p>In demo mode the {@link com.quantlens.ai.chat.DemoModeAdvisor} short-circuits the
 * ChatClient call and returns the authored seed content for the ticker from
 * {@code ai_seed_content}. In live mode the real LLM is invoked with the session key.
 *
 * <p><strong>TODO 06-03:</strong> Implement the real explain logic — build a metrics JSON
 * from the holding, construct the prompt with {@code EXPLAIN_SYSTEM_PROMPT} /
 * {@code EXPLAIN_USER_TEMPLATE}, and set the {@code AI_SEED_TYPE} / {@code AI_SEED_SUBJECT}
 * advisor params so the DemoModeAdvisor can look up the seed content.
 *
 * <p>This stub returns an empty narrative so the controller + integration tests compile
 * and the endpoints return 200 in RED state.
 */
@Service
@Transactional(readOnly = true)
public class ExplainPositionService {

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PortfolioService portfolioService;

    public ExplainPositionService(ChatClientStrategy strategy,
                                   LlmKeySessionHolder keyHolder,
                                   PortfolioService portfolioService) {
        this.strategy        = strategy;
        this.keyHolder       = keyHolder;
        this.portfolioService = portfolioService;
    }

    /**
     * Returns a position explanation for the given ticker in the given portfolio.
     *
     * <p>Stub implementation — returns an empty narrative. Real implementation in Plan 06-03.
     *
     * @param portfolioId the authenticated user's portfolio ID (IDOR-safe — derived from principal)
     * @param ticker      the ticker symbol (e.g. {@code "AAPL"}) — matches {@code ai_seed_content.subject_id}
     * @return an {@link ExplainResponseDto} with the narrative (empty stub until Plan 06-03)
     */
    public ExplainResponseDto explain(Long portfolioId, String ticker) {
        // TODO 06-03: build metrics JSON, set AI_SEED_TYPE/AI_SEED_SUBJECT advisor params,
        // call strategy.forSession(keyHolder).prompt()...call().content()
        return new ExplainResponseDto("");
    }
}
