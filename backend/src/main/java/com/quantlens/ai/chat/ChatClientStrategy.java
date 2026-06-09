package com.quantlens.ai.chat;

import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.ai.tools.StockQuoteToolService;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Factory service that builds a per-request {@link ChatClient} with the session key
 * injected into the underlying provider model.
 *
 * <h2>Demo mode</h2>
 * Returns a {@link ChatClient} built on top of the auto-configured
 * {@link AnthropicChatModel} (sentinel key). The model is never actually called
 * because {@link DemoModeAdvisor} short-circuits before reaching it.
 *
 * <h2>Live mode — Anthropic</h2>
 * Builds a new {@link AnthropicChatModel} via the full {@link AnthropicChatModel#builder()}
 * pattern (no {@code mutate()} — that method does not exist on Anthropic in 1.1.x; A3 confirmed).
 * Uses {@link AnthropicApi#builder()} to construct a session-scoped API with the user key.
 *
 * <h2>Live mode — OpenAI</h2>
 * Uses {@link OpenAiChatModel#mutate()} (which does exist in 1.1.x) and overrides the API
 * via {@link OpenAiApi#builder()}.
 *
 * <h2>Advisor chain extensibility</h2>
 * All {@link CallAdvisor} beans registered in the application context are injected and
 * sorted by {@link org.springframework.core.Ordered#getOrder()} before being added as
 * default advisors. This allows test configurations to register additional advisors
 * (e.g. a counting advisor at {@code HIGHEST_PRECEDENCE + 1}) that sit just after
 * {@link DemoModeAdvisor} in the chain — enabling executable no-network proofs.
 *
 * <h2>A3 verification</h2>
 * {@code AnthropicChatModel.builder().anthropicApi(api).defaultOptions(opts).build()} compiles
 * and the builder fills defaults for toolCallingManager/retryTemplate/observationRegistry
 * from the auto-configured beans — confirmed at Task 2 compile gate.
 */
@Service
public class ChatClientStrategy {

    private final AnthropicChatModel    baseAnthropicModel;
    private final OpenAiChatModel       baseOpenAiModel;
    private final List<CallAdvisor>     advisors;
    private final StockQuoteToolService stockQuoteToolService;

    public ChatClientStrategy(AnthropicChatModel baseAnthropicModel,
                              OpenAiChatModel baseOpenAiModel,
                              List<CallAdvisor> advisors,
                              StockQuoteToolService stockQuoteToolService) {
        this.baseAnthropicModel     = baseAnthropicModel;
        this.baseOpenAiModel        = baseOpenAiModel;
        // Sort by order so DemoModeAdvisor (HIGHEST_PRECEDENCE) fires first
        this.advisors = advisors.stream()
                .sorted(AnnotationAwareOrderComparator.INSTANCE)
                .toList();
        this.stockQuoteToolService  = stockQuoteToolService;
    }

    /**
     * Builds a per-request {@link ChatClient} with the session key injected and all
     * registered {@link CallAdvisor} beans in the chain (sorted by order).
     *
     * <p>In demo mode the {@link DemoModeAdvisor} fires first (HIGHEST_PRECEDENCE) and
     * short-circuits the chain — the underlying model with the sentinel key is never
     * invoked, so {@code DEMO_NO_KEY} is never sent to any provider.
     *
     * @param keyHolder the session-scoped key holder for the current request
     * @return a ready-to-use {@link ChatClient} with all registered advisors
     */
    /**
     * Builds a per-request {@link ChatClient} with the session key injected, all
     * registered {@link CallAdvisor} beans in the chain (sorted by order), and the
     * {@link StockQuoteToolService} registered via {@link MethodToolCallbackProvider}.
     *
     * <p>Tools are registered at builder level (not request level) via
     * {@code defaultToolCallbacks(ToolCallbackProvider...)} — the overload that accepts
     * a {@link MethodToolCallbackProvider} directly. This avoids the {@code defaultTools()}
     * CGLIB detection bug (Spring AI 1.1.x GitHub #5134).
     *
     * <p>Open Question 1 resolved at compile:
     * {@link MethodToolCallbackProvider#getToolCallbacks()} returns {@code ToolCallback[]}
     * (an array). {@code defaultToolCallbacks} accepts {@code ToolCallbackProvider...}
     * varargs — we pass the provider directly, which is the cleanest overload.
     *
     * @param keyHolder the session-scoped key holder for the current request
     * @return a ready-to-use {@link ChatClient} with tools and advisors registered
     */
    public ChatClient forSession(LlmKeySessionHolder keyHolder) {
        ChatModel model = buildModel(keyHolder);
        // Build MethodToolCallbackProvider — avoids defaultTools() CGLIB bug (#5134)
        // Open Q1: getToolCallbacks() returns ToolCallback[]; defaultToolCallbacks(ToolCallbackProvider...)
        // is the cleanest overload — pass the provider directly.
        MethodToolCallbackProvider toolProvider = MethodToolCallbackProvider.builder()
                .toolObjects(stockQuoteToolService)
                .build();
        return ChatClient.builder(model)
                .defaultToolCallbacks(toolProvider)                        // Phase 8: tool registration
                .defaultAdvisors(advisors.toArray(new CallAdvisor[0]))     // Phase 7: ragAdvisor, memoryAdvisor
                .build();
    }

    // ── private model builders ────────────────────────────────────────────────

    private ChatModel buildModel(LlmKeySessionHolder keyHolder) {
        if (!keyHolder.hasKey()) {
            // Demo: model is never called — DemoModeAdvisor short-circuits first
            return baseAnthropicModel;
        }
        return switch (keyHolder.getProvider()) {
            case "anthropic" -> buildAnthropicModel(keyHolder.getApiKey());
            case "openai"    -> buildOpenAiModel(keyHolder.getApiKey());
            default -> throw new IllegalArgumentException(
                    "Unknown provider: " + keyHolder.getProvider());
        };
    }

    /**
     * Builds a per-request {@link AnthropicChatModel} with the session API key.
     *
     * <p>AnthropicChatModel has NO {@code mutate()} method — use the full builder.
     * The builder provides defaults for toolCallingManager/retryTemplate/observationRegistry
     * (A3 resolved: build() succeeds with only anthropicApi + defaultOptions set).
     *
     * @param apiKey the user-supplied Anthropic API key
     * @return a new {@link AnthropicChatModel} for this request only; never stored
     */
    private AnthropicChatModel buildAnthropicModel(String apiKey) {
        AnthropicApi sessionApi = AnthropicApi.builder()
                .apiKey(apiKey)
                .build();
        return AnthropicChatModel.builder()
                .anthropicApi(sessionApi)
                .defaultOptions(AnthropicChatOptions.builder()
                        .model("claude-sonnet-4-6")
                        .maxTokens(2048)
                        .build())
                .build();
    }

    /**
     * Builds a per-request {@link OpenAiChatModel} with the session API key.
     *
     * <p>OpenAiChatModel has {@code mutate()} — use it to inherit base config.
     *
     * @param apiKey the user-supplied OpenAI API key
     * @return a new {@link OpenAiChatModel} for this request only; never stored
     */
    private OpenAiChatModel buildOpenAiModel(String apiKey) {
        OpenAiApi sessionApi = OpenAiApi.builder()
                .apiKey(apiKey)
                .build();
        return baseOpenAiModel.mutate()
                .openAiApi(sessionApi)
                .build();
    }
}
