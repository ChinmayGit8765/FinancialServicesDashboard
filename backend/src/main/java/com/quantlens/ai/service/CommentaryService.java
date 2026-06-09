package com.quantlens.ai.service;

import com.quantlens.ai.api.CommentaryDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service that generates a daily portfolio commentary narrative.
 *
 * <p>Routes through {@link ChatClientStrategy#forSession(LlmKeySessionHolder)} so that
 * {@link com.quantlens.ai.chat.DemoModeAdvisor} can short-circuit in demo mode and return
 * the authored seed content for the persona from {@code ai_seed_content}. In live mode
 * the real LLM is invoked.
 *
 * <h2>Persona key mapping</h2>
 * The persona key ({@code GROWTH} | {@code INCOME} | {@code BALANCED}) is derived from
 * the {@link Portfolio#getStyle()} field, which is stored as a title-case string
 * ({@code "Growth"} / {@code "Income"} / {@code "Balanced"}) by the seeder. This service
 * converts it to uppercase to match the {@code ai_seed_content.subject_id} column.
 *
 * <ul>
 *   <li>alice (portfolioId=1) → style="Growth"  → persona key="GROWTH"</li>
 *   <li>bob   (portfolioId=2) → style="Income"  → persona key="INCOME"</li>
 *   <li>charlie (portfolioId=3) → style="Balanced" → persona key="BALANCED"</li>
 * </ul>
 *
 * <h2>DAILY_COMMENTARY content parsing</h2>
 * The raw {@code content} string from {@code ai_seed_content} encodes structured commentary:
 * <ol>
 *   <li>Line 1 = headline (no prefix)</li>
 *   <li>Blank line separator</li>
 *   <li>Body paragraph (one or two prose sentences)</li>
 *   <li>Blank line separator</li>
 *   <li>Bullet lines prefixed with {@code "- "}</li>
 * </ol>
 * Fallback: if the content does not match this convention (e.g. a free-form live response),
 * headline = first sentence (up to first '.'), body = remainder, bulletPoints = empty.
 *
 * <h2>No if(demoMode) branch</h2>
 * This service contains no demo/live conditional. The single demo/live switch lives
 * exclusively in {@link com.quantlens.ai.chat.DemoModeAdvisor}.
 */
@Service
@Transactional(readOnly = true)
public class CommentaryService {

    private static final String COMMENTARY_SYSTEM_PROMPT =
            "You are a concise portfolio strategist providing a daily market commentary. " +
            "Format your response with: " +
            "1) A single headline sentence. " +
            "2) A blank line. " +
            "3) A body paragraph (1-2 sentences). " +
            "4) A blank line. " +
            "5) 3-4 bullet points prefixed with '- ' covering key holdings.";

    private final ChatClientStrategy strategy;
    private final LlmKeySessionHolder keyHolder;
    private final PortfolioRepository portfolioRepository;

    public CommentaryService(ChatClientStrategy strategy,
                              LlmKeySessionHolder keyHolder,
                              PortfolioRepository portfolioRepository) {
        this.strategy            = strategy;
        this.keyHolder           = keyHolder;
        this.portfolioRepository = portfolioRepository;
    }

    /**
     * Returns a daily portfolio commentary for the given portfolio.
     *
     * @param portfolioId the authenticated user's portfolio ID (IDOR-safe — derived from principal)
     * @return a {@link CommentaryDto} with headline, body, bulletPoints
     * @throws ResponseStatusException 502 on provider error (no message echo — T-06-09)
     */
    public CommentaryDto commentary(Long portfolioId) {
        // Resolve persona key from portfolio style (domain layer — no service layer dependency)
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        String personaKey = portfolio.getStyle().toUpperCase();

        // Route through the advisor chain — DemoModeAdvisor short-circuits in demo mode
        try {
            String content = strategy.forSession(keyHolder)
                    .prompt()
                    .system(COMMENTARY_SYSTEM_PROMPT)
                    .user("Provide daily commentary for a " + portfolio.getStyle() + " portfolio.")
                    .advisors(spec -> spec
                            .param("AI_SEED_TYPE", "DAILY_COMMENTARY")
                            .param("AI_SEED_SUBJECT", personaKey))
                    .call()
                    .content();
            return parseCommentary(content != null ? content : "");
        } catch (ResponseStatusException rse) {
            throw rse;
        } catch (Exception e) {
            // T-06-09: never echo provider error messages (could carry the key)
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "AI provider temporarily unavailable");
        }
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Parses the DAILY_COMMENTARY storage convention into a {@link CommentaryDto}.
     *
     * <p>Convention (from AiSeedRunner Javadoc and 06-02-SUMMARY):
     * <pre>
     * Line 1: headline
     * (blank line)
     * body paragraph
     * (blank line)
     * - bullet 1
     * - bullet 2
     * ...
     * </pre>
     *
     * <p>Fallback: if the content does not match this structure (no blank-line separators
     * or no bullet lines), headline = first sentence, body = remainder, bulletPoints = empty.
     *
     * @param content the raw content string from the advisor
     * @return a parsed {@link CommentaryDto}
     */
    static CommentaryDto parseCommentary(String content) {
        if (content == null || content.isBlank()) {
            return new CommentaryDto("", "", List.of());
        }

        // Split by blank lines (one or more consecutive blank lines)
        String[] sections = content.split("\\n\\n+");

        if (sections.length >= 2) {
            // Convention format: sections[0]=headline, sections[1]=body, sections[2+]=bullets
            String headline = sections[0].strip();
            String body     = sections[1].strip();

            List<String> bullets = new ArrayList<>();
            // Collect bullet lines from section 2 onwards (or from any section containing "- ")
            for (int i = 2; i < sections.length; i++) {
                String section = sections[i].strip();
                // Each bullet-block section may have multiple "- " lines
                Arrays.stream(section.split("\\n"))
                        .map(String::strip)
                        .filter(line -> line.startsWith("- "))
                        .map(line -> line.substring(2).strip())
                        .forEach(bullets::add);
            }

            // If no explicit bullet section but body contains bullet lines, extract them
            if (bullets.isEmpty()) {
                String[] bodyLines = body.split("\\n");
                List<String> nonBulletLines = new ArrayList<>();
                for (String line : bodyLines) {
                    String stripped = line.strip();
                    if (stripped.startsWith("- ")) {
                        bullets.add(stripped.substring(2).strip());
                    } else if (!stripped.isBlank()) {
                        nonBulletLines.add(stripped);
                    }
                }
                if (!bullets.isEmpty()) {
                    body = String.join(" ", nonBulletLines);
                }
            }

            return new CommentaryDto(headline, body, List.copyOf(bullets));
        }

        // Fallback: free-form live response — headline = first sentence, body = remainder
        String text = content.strip();
        int dotIdx = text.indexOf('.');
        if (dotIdx > 0 && dotIdx < text.length() - 1) {
            String headline = text.substring(0, dotIdx + 1).strip();
            String body     = text.substring(dotIdx + 1).strip();
            return new CommentaryDto(headline, body, List.of());
        }

        // Last resort: whole content as headline
        return new CommentaryDto(text, "", List.of());
    }
}
