package com.quantlens.ai.chat;

import com.quantlens.ai.embedding.DeterministicHashingEmbeddingModel;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

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
 * <h3>A7 resolution — manual VectorStore bean</h3>
 * The {@code spring-ai-starter-vector-store-pgvector} auto-config may not pick up the
 * {@code @Primary DeterministicHashingEmbeddingModel} if auto-config resolution order places
 * the OpenAI embedding model first. To guarantee the correct model is used, a manual
 * {@link VectorStore} bean is declared here with an explicit {@link DeterministicHashingEmbeddingModel}
 * parameter. This overrides the auto-configured bean and ensures embedding-space consistency
 * (A7 verified at context load by RagAdvisorConfigTest).
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

    /**
     * Manual PgVectorStore bean — A7 resolution.
     *
     * <p>Declares the VectorStore explicitly with {@link DeterministicHashingEmbeddingModel}
     * as the embedding model, bypassing any auto-config bean resolution ambiguity.
     * Settings mirror application.yml: initialize-schema=false (Flyway owns DDL), 1536 dims,
     * COSINE_DISTANCE, HNSW, table "vector_store" in schema "public".
     *
     * @param jdbcTemplate   the Spring-provided JDBC template (auto-wired from DataSource)
     * @param embeddingModel the deterministic embedding model (zero-key, @Primary)
     * @return the configured PgVectorStore
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   DeterministicHashingEmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(1536)
                .distanceType(PgVectorStore.PgDistanceType.COSINE_DISTANCE)
                .indexType(PgVectorStore.PgIndexType.HNSW)
                .initializeSchema(false)
                .schemaName("public")
                .vectorTableName("vector_store")
                .build();
    }
}
