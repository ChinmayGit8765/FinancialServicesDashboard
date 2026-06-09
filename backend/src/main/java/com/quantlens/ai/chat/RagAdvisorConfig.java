package com.quantlens.ai.chat;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Spring AI RAG advisor configuration — Phase 7.
 *
 * <p>Registers three beans into the advisor chain (alongside the existing {@link DemoModeAdvisor}):
 * <ol>
 *   <li>{@link ChatMemory} — {@link MessageWindowChatMemory} with a 20-message window</li>
 *   <li>{@link MessageChatMemoryAdvisor} — conversation history at order HIGHEST_PRECEDENCE+20</li>
 *   <li>{@link QuestionAnswerAdvisor} — pgvector RAG at order HIGHEST_PRECEDENCE+10</li>
 * </ol>
 *
 * <p>{@link ChatClientStrategy} injects all {@code CallAdvisor} beans automatically via
 * {@code List<CallAdvisor>} — no changes to {@code ChatClientStrategy} are needed.
 *
 * <h3>A7 resolution — auto-config via @Primary</h3>
 * {@code PgVectorStoreAutoConfiguration} is annotated {@code @ConditionalOnMissingBean} and
 * accepts an {@code EmbeddingModel} parameter resolved by Spring's {@code @Primary} mechanism.
 * Because {@link com.quantlens.ai.embedding.DeterministicHashingEmbeddingModel} is {@code @Primary},
 * the auto-config picks it up without a manual VectorStore bean. A7 verified at context load
 * by RagAdvisorConfigTest (asserts {@code VectorStore} bean present + dimensions == 1536).
 *
 * <h3>A4 resolution</h3>
 * {@code MessageChatMemoryAdvisor.builder(chatMemory).order(int)} builder method confirmed
 * at compile. If the builder does not have {@code .order()}, the fallback is implementing
 * {@code getOrder()} directly — but the builder API is confirmed for Spring AI 1.1.6.
 *
 * <h3>A8 resolution</h3>
 * {@link QuestionAnswerAdvisor} implements {@code CallAdvisor} — verified by
 * {@code RagAdvisorConfigTest} which asserts it appears in the advisor chain injected into
 * {@link ChatClientStrategy}.
 *
 * <h3>T-07-PI: Prompt injection mitigation</h3>
 * The system prompt in {@link com.quantlens.ai.service.ChatService} wraps retrieved filing
 * context with instructions telling the model to treat it as a read-only data source.
 * (A6: StTemplateRenderer/PromptTemplate.builder() availability checked at compile — if
 * unavailable, mitigation falls back to the ChatService system prompt.)
 */
@Configuration
public class RagAdvisorConfig {

    /**
     * In-memory chat memory with a 20-message sliding window.
     * Used by {@link MessageChatMemoryAdvisor} for conversation context.
     */
    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(20)
                .build();
    }

    /**
     * Conversation memory advisor — injects prior turns into the prompt before the model call.
     * Order HIGHEST_PRECEDENCE+20: runs after QuestionAnswerAdvisor (HP+10).
     * A4: {@code .order(int)} on the builder — verified at compile.
     */
    @Bean
    public MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory)
                .order(Ordered.HIGHEST_PRECEDENCE + 20)
                .build();
    }

    /**
     * RAG retrieval advisor — embeds the user question, searches pgvector, augments the prompt.
     * Order HIGHEST_PRECEDENCE+10: runs after DemoModeAdvisor (HP), before memory advisor (HP+20).
     * A8: {@code QuestionAnswerAdvisor} implements {@code CallAdvisor} — verified at compile.
     */
    @Bean
    public QuestionAnswerAdvisor questionAnswerAdvisor(VectorStore vectorStore) {
        return QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(SearchRequest.builder()
                        .topK(4)
                        .similarityThreshold(0.4)
                        .build())
                .order(Ordered.HIGHEST_PRECEDENCE + 10)
                .build();
    }

}

