package com.quantlens.ai;

import com.quantlens.AbstractPostgresIntegrationTest;
import com.quantlens.ai.chat.RagAdvisorConfig;
import com.quantlens.ai.embedding.DeterministicHashingEmbeddingModel;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test validating that {@link RagAdvisorConfig} wires all Phase 7 beans correctly.
 *
 * <p>Verifies Wave 0 assumptions:
 * <ul>
 *   <li>A3: {@code @Primary DeterministicHashingEmbeddingModel} is the resolved {@link EmbeddingModel}</li>
 *   <li>A4: {@link MessageChatMemoryAdvisor} bean is present (builder API compiles)</li>
 *   <li>A7: {@link VectorStore} bean is present (manual bean with DeterministicHashingEmbeddingModel)</li>
 *   <li>A8: {@link QuestionAnswerAdvisor} bean is present (implements CallAdvisor, auto-registered)</li>
 * </ul>
 */
class RagAdvisorConfigTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorStore vectorStore;

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private MessageChatMemoryAdvisor messageChatMemoryAdvisor;

    @Autowired
    private QuestionAnswerAdvisor questionAnswerAdvisor;

    @Test
    void embeddingModel_isDeterministicHashingModel_resolvesA3() {
        // A3: @Primary resolves DeterministicHashingEmbeddingModel over OpenAiEmbeddingModel
        assertThat(embeddingModel)
                .as("The @Primary EmbeddingModel must be DeterministicHashingEmbeddingModel (A3)")
                .isInstanceOf(DeterministicHashingEmbeddingModel.class);
    }

    @Test
    void embeddingModel_dimensions_is1536() {
        assertThat(embeddingModel.dimensions())
                .as("EmbeddingModel.dimensions() must be 1536")
                .isEqualTo(1536);
    }

    @Test
    void vectorStore_beanPresent_resolvesA7() {
        // A7: VectorStore is wired (manual bean with DeterministicHashingEmbeddingModel)
        assertThat(vectorStore)
                .as("VectorStore bean must be present in context (A7)")
                .isNotNull();
    }

    @Test
    void chatMemory_beanPresent() {
        assertThat(chatMemory)
                .as("ChatMemory (MessageWindowChatMemory) bean must be present")
                .isNotNull();
    }

    @Test
    void messageChatMemoryAdvisor_beanPresent_resolvesA4() {
        // A4: MessageChatMemoryAdvisor.builder(chatMemory).order(int).build() compiles
        assertThat(messageChatMemoryAdvisor)
                .as("MessageChatMemoryAdvisor bean must be present (A4)")
                .isNotNull();
    }

    @Test
    void questionAnswerAdvisor_beanPresent_resolvesA8() {
        // A8: QuestionAnswerAdvisor implements CallAdvisor (auto-registered in ChatClientStrategy)
        assertThat(questionAnswerAdvisor)
                .as("QuestionAnswerAdvisor bean must be present in context (A8)")
                .isNotNull();
    }
}
