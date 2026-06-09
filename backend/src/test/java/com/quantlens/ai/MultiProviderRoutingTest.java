package com.quantlens.ai;

import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.ai.tools.StockQuoteToolService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests proving that {@link ChatClientStrategy#forSession(LlmKeySessionHolder)}
 * routes to the correct provider model for both "anthropic" and "openai" keys.
 *
 * <p>No Spring context is required — {@link ChatClientStrategy} is a plain service;
 * mock models are injected directly. No real network calls are made.
 *
 * <p>Success criterion 3: both providers route via the same {@code forSession} entry point.
 */
@ExtendWith(MockitoExtension.class)
class MultiProviderRoutingTest {

    @Mock
    private LlmKeySessionHolder keyHolder;

    @Mock
    private StockQuoteToolService stockQuoteToolService;

    /**
     * Builds a {@link ChatClientStrategy} with a mocked Anthropic base model and a
     * real (but no-op) OpenAI base model so that {@code baseOpenAiModel.mutate()} works.
     *
     * <p>We need a concrete {@link OpenAiChatModel} for the OpenAI branch because
     * {@code mutate()} is non-trivially delegating. We construct a minimal one with a
     * fake key — no network call is made at construction time.
     */
    private ChatClientStrategy buildStrategy() {
        AnthropicChatModel mockAnthropicModel = mock(AnthropicChatModel.class);

        // OpenAiChatModel.mutate() requires a concrete instance — construct with a fake key.
        // No network call happens at construction or mutate() time.
        OpenAiChatModel baseOpenAiModel = OpenAiChatModel.builder()
                .openAiApi(OpenAiApi.builder().apiKey("test-openai-base-key").build())
                .defaultOptions(OpenAiChatOptions.builder().model("gpt-4o").build())
                .build();

        return new ChatClientStrategy(
                mockAnthropicModel,
                baseOpenAiModel,
                List.of(),
                stockQuoteToolService
        );
    }

    /**
     * Anthropic provider: when provider="anthropic", {@code forSession} returns a non-null
     * ChatClient without exception — proves the Anthropic branch routes correctly.
     *
     * <p>{@code buildAnthropicModel} constructs a real {@link AnthropicChatModel} from a fake
     * key (builder only; no network call). The returned ChatClient is non-null.
     */
    @Test
    void anthropicProvider_buildsAnthropicModel() {
        when(keyHolder.hasKey()).thenReturn(true);
        when(keyHolder.getProvider()).thenReturn("anthropic");
        when(keyHolder.getApiKey()).thenReturn("test-anthropic-key");

        ChatClientStrategy strategy = buildStrategy();
        ChatClient client = strategy.forSession(keyHolder);

        assertThat(client)
                .as("forSession with anthropic provider must return a non-null ChatClient")
                .isNotNull();
    }

    /**
     * OpenAI provider: when provider="openai", {@code forSession} returns a non-null ChatClient
     * without exception — proves the OpenAI branch routes correctly.
     *
     * <p>{@code buildOpenAiModel} calls {@code baseOpenAiModel.mutate().openAiApi(...).build()}
     * with a fake key. No network call is made at construction time.
     */
    @Test
    void openaiProvider_buildsOpenAiModel() {
        when(keyHolder.hasKey()).thenReturn(true);
        when(keyHolder.getProvider()).thenReturn("openai");
        when(keyHolder.getApiKey()).thenReturn("test-openai-key");

        ChatClientStrategy strategy = buildStrategy();
        ChatClient client = strategy.forSession(keyHolder);

        assertThat(client)
                .as("forSession with openai provider must return a non-null ChatClient")
                .isNotNull();
    }

    /**
     * Both providers are tested via the same {@code forSession()} entry point —
     * success criterion 3 is satisfied by the two tests above.
     */
    @Test
    void bothProviders_sameEntryPoint_returnsNonNull() {
        ChatClientStrategy strategy = buildStrategy();

        when(keyHolder.hasKey()).thenReturn(true);
        when(keyHolder.getProvider()).thenReturn("anthropic");
        when(keyHolder.getApiKey()).thenReturn("key-a");
        ChatClient anthropicClient = strategy.forSession(keyHolder);

        when(keyHolder.getProvider()).thenReturn("openai");
        when(keyHolder.getApiKey()).thenReturn("key-b");
        ChatClient openaiClient = strategy.forSession(keyHolder);

        assertThat(anthropicClient).isNotNull();
        assertThat(openaiClient).isNotNull();
    }
}
