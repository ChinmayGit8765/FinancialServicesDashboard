package com.quantlens.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.ai.api.StructuredInsightRecord;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.seed.AiSeedContent;
import com.quantlens.ai.seed.AiSeedContentRepository;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Service that returns a typed {@link StructuredInsightRecord} for the authenticated user.
 *
 * <p>Routes through {@link ChatClientStrategy#forSession(LlmKeySessionHolder)} with
 * an explicit demo/live branch on {@link LlmKeySessionHolder#hasKey()}:
 * <ul>
 *   <li><strong>Demo mode</strong> ({@code hasKey()==false}): reads the {@code STRUCTURED_INSIGHT}
 *       seed for the user's persona and deserializes it via {@code ObjectMapper.readValue}
 *       — no provider call, no network, no BeanOutputConverter invocation.</li>
 *   <li><strong>Live mode</strong> ({@code hasKey()==true}): calls
 *       {@code strategy.forSession(keyHolder)...call().entity(StructuredInsightRecord.class)}
 *       so Spring AI's BeanOutputConverter auto-generates the JSON schema and the LLM
 *       returns a typed record.</li>
 * </ul>
 *
 * <h2>Why branch explicitly (not via DemoModeAdvisor alone)?</h2>
 * BeanOutputConverter injects format instructions into the prompt for both demo and live
 * calls. Parsing the result with {@code .entity()} in demo mode works only if the seed
 * JSON is schema-valid — but the safer pattern is to deserialize explicitly, which also
 * ensures that DemoModeAdvisor's seed content never reaches {@code BeanOutputConverter}'s
 * response parser (08-RESEARCH Pitfall 2).
 *
 * <h2>Persona key mapping</h2>
 * Matches {@code CommentaryService}: {@code portfolio.getStyle().toUpperCase()} →
 * {@code "GROWTH"} | {@code "INCOME"} | {@code "BALANCED"}.
 *
 * <h2>T-08-LEAK: key non-disclosure</h2>
 * All provider exceptions are caught and re-thrown as 502 BAD_GATEWAY with a generic message.
 * The provider error is NEVER echoed (could carry the API key).
 *
 * <h2>T-08-IDOR-ST</h2>
 * The {@code portfolioId} parameter is ALWAYS derived from the authenticated principal
 * in {@code AiController.resolvePortfolioId()} — never from the request body.
 */
@Service
@Transactional(readOnly = true)
public class StructuredOutputService {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutputService.class);

    private static final String STRUCTURED_INSIGHT = "STRUCTURED_INSIGHT";

    /** Fallback seed JSON used when no seed row exists for the persona. */
    private static final String FALLBACK_SEED =
            "{\"title\":\"Sector Exposure\",\"subtitle\":\"Demo\",\"series\":[]}";

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PortfolioRepository portfolioRepository;
    private final AiSeedContentRepository seedRepo;
    private final ObjectMapper objectMapper;

    public StructuredOutputService(ChatClientStrategy strategy,
                                   LlmKeySessionHolder keyHolder,
                                   PortfolioRepository portfolioRepository,
                                   AiSeedContentRepository seedRepo,
                                   ObjectMapper objectMapper) {
        this.strategy            = strategy;
        this.keyHolder           = keyHolder;
        this.portfolioRepository = portfolioRepository;
        this.seedRepo            = seedRepo;
        this.objectMapper        = objectMapper;
    }

    /**
     * Returns a structured sector-exposure insight for the given portfolio.
     *
     * @param portfolioId the authenticated user's portfolio ID (IDOR-safe — derived from principal)
     * @return a {@link StructuredInsightRecord} with title, optional subtitle, and non-null series
     * @throws ResponseStatusException 401 if no portfolio found; 502 on provider error
     */
    public StructuredInsightRecord getInsight(Long portfolioId) {
        // Resolve persona key from portfolio style — verbatim from CommentaryService lines 87-89
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String personaKey = portfolio.getStyle().toUpperCase();

        try {
            if (!keyHolder.hasKey()) {
                // ── Demo path: parse seeded JSON directly ──────────────────────────────
                // Bypass BeanOutputConverter entirely (08-RESEARCH Pitfall 2).
                // objectMapper.readValue mirrors ChatService.parseDemoResponse pattern (line 198).
                String seedContent = seedRepo
                        .findByTypeAndSubjectId(STRUCTURED_INSIGHT, personaKey)
                        .map(AiSeedContent::getContent)
                        .orElse(FALLBACK_SEED);
                return objectMapper.readValue(seedContent, StructuredInsightRecord.class);
            }

            // ── Live path: BeanOutputConverter via .entity() ───────────────────────
            // BeanOutputConverter auto-generates a JSON schema from StructuredInsightRecord
            // and injects format instructions into the prompt. The LLM must return valid JSON.
            return strategy.forSession(keyHolder)
                    .prompt()
                    .system("Return ONLY valid JSON matching the schema. You are a portfolio analyst.")
                    .user(u -> u.text("Analyze sector exposure for portfolio: {summary}")
                                .param("summary", buildPortfolioSummary(portfolio, personaKey)))
                    .advisors(spec -> spec
                            .param("AI_SEED_TYPE",    STRUCTURED_INSIGHT)
                            .param("AI_SEED_SUBJECT", personaKey))
                    .call()
                    .entity(StructuredInsightRecord.class);

        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            // T-08-LEAK: never echo provider error messages (could carry the API key)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider temporarily unavailable");
        }
    }

    /**
     * Builds a short portfolio summary string for the live-mode prompt.
     *
     * <p>No additional repository wiring required — the portfolio style is already loaded.
     *
     * @param portfolio  the authenticated user's portfolio entity
     * @param personaKey the uppercased persona key (e.g. "GROWTH")
     * @return a short descriptor string for the LLM prompt
     */
    private String buildPortfolioSummary(Portfolio portfolio, String personaKey) {
        return portfolio.getName() + " (" + personaKey + " style)";
    }
}
