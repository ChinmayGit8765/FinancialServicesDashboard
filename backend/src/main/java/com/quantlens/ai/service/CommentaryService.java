package com.quantlens.ai.service;

import com.quantlens.ai.api.CommentaryDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.service.PortfolioService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service that generates a daily portfolio commentary narrative.
 *
 * <p>In demo mode the {@link com.quantlens.ai.chat.DemoModeAdvisor} short-circuits and
 * returns the authored seed content for the user's persona (type={@code "DAILY_COMMENTARY"},
 * subjectId=persona key e.g. {@code "GROWTH"}). In live mode the real LLM is used.
 *
 * <p><strong>TODO 06-03:</strong> Implement the real commentary logic — resolve the persona
 * from the portfolioId, build a portfolio summary context, construct the prompt, and set
 * the {@code AI_SEED_TYPE} / {@code AI_SEED_SUBJECT} advisor params.
 *
 * <p>This stub returns empty fields so the controller + integration tests compile and
 * the endpoint returns 200 in RED state.
 */
@Service
@Transactional(readOnly = true)
public class CommentaryService {

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PortfolioService portfolioService;

    public CommentaryService(ChatClientStrategy strategy,
                              LlmKeySessionHolder keyHolder,
                              PortfolioService portfolioService) {
        this.strategy         = strategy;
        this.keyHolder        = keyHolder;
        this.portfolioService = portfolioService;
    }

    /**
     * Returns a daily portfolio commentary for the given portfolio.
     *
     * <p>Stub implementation — returns empty fields. Real implementation in Plan 06-03.
     *
     * @param portfolioId the authenticated user's portfolio ID (IDOR-safe — derived from principal)
     * @return a {@link CommentaryDto} with headline, body, bulletPoints (empty stub until Plan 06-03)
     */
    public CommentaryDto commentary(Long portfolioId) {
        // TODO 06-03: resolve persona from portfolioId, build portfolio summary, set advisor params,
        // call strategy.forSession(keyHolder).prompt()...call().content()
        return new CommentaryDto("", "", List.of());
    }
}
