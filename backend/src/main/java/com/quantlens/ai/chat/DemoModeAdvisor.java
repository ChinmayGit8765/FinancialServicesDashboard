package com.quantlens.ai.chat;

import com.quantlens.ai.seed.AiSeedContent;
import com.quantlens.ai.seed.AiSeedContentRepository;
import com.quantlens.ai.session.LlmKeySessionHolder;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Spring AI {@link CallAdvisor} that implements the demo/live mode seam.
 *
 * <h2>Demo mode (no key)</h2>
 * When {@link LlmKeySessionHolder#hasKey()} returns {@code false}, this advisor
 * short-circuits the chain: it reads authored seed content from the DB and returns
 * a synthetic {@link ChatClientResponse} wrapping an {@link AssistantMessage}. The
 * real {@code ChatModel} is never called — no network request is made (T-06-03).
 *
 * <h2>Live mode (key present)</h2>
 * Delegates to {@link CallAdvisorChain#nextCall(ChatClientRequest)}, allowing the
 * chain to reach the real provider model with the session key injected by
 * {@link ChatClientStrategy}.
 *
 * <h2>Advisor chain position</h2>
 * {@code getOrder()} returns {@link Ordered#HIGHEST_PRECEDENCE} so this advisor fires
 * before all others. Phase 7 will slot {@code QuestionAnswerAdvisor} and
 * {@code MessageChatMemoryAdvisor} at lower priority.
 *
 * <h2>Context keys</h2>
 * The service layer sets these in the advisor params at call time:
 * <ul>
 *   <li>{@code "AI_SEED_TYPE"}    — e.g. {@code "EXPLAIN_POSITION"}, {@code "DAILY_COMMENTARY"}</li>
 *   <li>{@code "AI_SEED_SUBJECT"} — e.g. {@code "AAPL"}, {@code "GROWTH"}</li>
 * </ul>
 *
 * <h2>A1 / A2 verification</h2>
 * This class imports only {@link ChatClientRequest}/{@link ChatClientResponse} (not the
 * pre-1.0 {@code AdvisedRequest}/{@code AdvisedResponse}) — A1 confirmed at compile.
 * The {@link ChatClientResponse} is constructed with the verified 1.1.x constructor
 * {@code new ChatClientResponse(ChatResponse, Map)} — A2 confirmed at compile.
 */
@Component
public class DemoModeAdvisor implements CallAdvisor {

    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE;

    private final LlmKeySessionHolder keyHolder;
    private final AiSeedContentRepository seedRepo;

    public DemoModeAdvisor(LlmKeySessionHolder keyHolder,
                           AiSeedContentRepository seedRepo) {
        this.keyHolder = keyHolder;
        this.seedRepo  = seedRepo;
    }

    @Override
    public String getName() {
        return "DemoModeAdvisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    /**
     * Core advisor logic — demo short-circuit or live pass-through.
     *
     * @param request the chat client request containing context params
     * @param chain   the remaining advisor chain
     * @return a synthetic {@link ChatClientResponse} in demo mode, or the chain result in live mode
     */
    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request,
                                         CallAdvisorChain chain) {
        if (keyHolder.hasKey()) {
            // Live mode — pass through to next advisor / real model
            return chain.nextCall(request);
        }

        // Demo mode — short-circuit: return authored seed content, no network call (T-06-03)
        String type    = (String) request.context().get("AI_SEED_TYPE");
        String subject = (String) request.context().get("AI_SEED_SUBJECT");

        String content = seedRepo.findByTypeAndSubjectId(type, subject)
                .map(AiSeedContent::getContent)
                .orElse("AI narrative not available in demo mode.");

        return buildSyntheticResponse(content, request.context());
    }

    /**
     * Builds a synthetic {@link ChatClientResponse} wrapping an {@link AssistantMessage}.
     *
     * <p>A2 verification: {@code new ChatClientResponse(ChatResponse, Map)} compiles
     * against Spring AI 1.1.x — confirmed at Task 2 compile gate.
     *
     * @param content the authored narrative text from the DB
     * @param ctx     the original request context map (passed through to response)
     * @return a fully constructed {@link ChatClientResponse}
     */
    private ChatClientResponse buildSyntheticResponse(String content, Map<String, Object> ctx) {
        ChatResponse chatResponse = new ChatResponse(
                List.of(new Generation(new AssistantMessage(content))));
        return new ChatClientResponse(chatResponse, ctx);
    }
}
