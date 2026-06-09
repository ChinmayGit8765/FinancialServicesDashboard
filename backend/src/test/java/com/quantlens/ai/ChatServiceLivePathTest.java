package com.quantlens.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.ai.api.ChatResponseDto;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.service.ChatService;
import com.quantlens.ai.session.LlmKeySessionHolder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ChatService} focusing on the live/demo path routing fix (CR-05)
 * and the WR-03 (String.valueOf for numeric JSON fields) and WR-04 (null getResult()) fixes.
 *
 * <p>These tests use Mockito to stub the ChatClientStrategy and assert service-level behaviour
 * without requiring a Spring context or database.
 */
@ExtendWith(MockitoExtension.class)
class ChatServiceLivePathTest {

    @Mock
    private ChatClientStrategy strategy;

    @Mock
    private LlmKeySessionHolder keyHolder;

    // ── CR-05 regression: live response starting with '{' must NOT be demo-parsed ─

    /**
     * CR-05 regression: when QuestionAnswerAdvisor ran (RETRIEVED_DOCUMENTS is present in context,
     * even as an empty list), the live path must be taken — the raw LLM text must be returned
     * as-is, regardless of whether it starts with '{'.
     *
     * <p>Before the fix, an empty RETRIEVED_DOCUMENTS list ({@code docs != null} was true but
     * the demo fallthrough was also reachable via the wrong code path). This test verifies that
     * a response whose text starts with '{' is NOT fed through parseDemoResponse.
     */
    @Test
    void chat_livePathWithEmptyDocs_doesNotParseDemoResponse() {
        // A real LLM answer that starts with '{' — NOT a demo JSON envelope
        String liveAnswer = "{\"recommendation\": \"Buy AAPL on regulatory risk discount\"}";

        ChatClientResponse mockResponse = buildMockResponse(liveAnswer,
                Map.of(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of()));

        ChatClient mockClient = buildMockChatClient(mockResponse);
        when(strategy.forSession(any())).thenReturn(mockClient);

        ChatService service = new ChatService(strategy, keyHolder, new ObjectMapper());
        ChatResponseDto result = service.chat("Apple regulatory risk?", "test-session");

        // CR-05: live path returns raw text — must NOT attempt to parse as demo JSON
        // The answer must be the raw LLM text, not a demo-parsed field extraction
        assertThat(result.answer())
                .as("Live path: raw LLM text starting with '{' must be returned as-is, not demo-parsed")
                .isEqualTo(liveAnswer);
        assertThat(result.citations())
                .as("Live path with empty RETRIEVED_DOCUMENTS: citations must be empty list")
                .isEmpty();
    }

    /**
     * CR-05 regression: when RETRIEVED_DOCUMENTS is null (demo mode, QA advisor never ran),
     * parseDemoResponse IS invoked. A properly-shaped demo JSON answer must be parsed.
     */
    @Test
    void chat_demoPathWithNullDocs_parsesDemoResponse() {
        String demoJson = "{\"answer\":\"Apple faces App Store regulatory risk.\",\"citations\":[{\"ticker\":\"AAPL\",\"section\":\"Risk Factors\",\"source\":\"AAPL 10-K FY2023\",\"excerpt\":\"regulatory scrutiny\"}]}";

        // Context has NO RETRIEVED_DOCUMENTS key (null — demo mode)
        ChatClientResponse mockResponse = buildMockResponse(demoJson, Map.of());

        ChatClient mockClient = buildMockChatClient(mockResponse);
        when(strategy.forSession(any())).thenReturn(mockClient);

        ChatService service = new ChatService(strategy, keyHolder, new ObjectMapper());
        ChatResponseDto result = service.chat("Apple regulatory risk?", "test-session");

        // Demo path: JSON envelope is parsed
        assertThat(result.answer())
                .as("Demo path: answer field from JSON must be extracted")
                .isEqualTo("Apple faces App Store regulatory risk.");
        assertThat(result.citations())
                .as("Demo path: citations array must be parsed from JSON")
                .hasSize(1);
        assertThat(result.citations().get(0).ticker())
                .as("Citation ticker must be AAPL")
                .isEqualTo("AAPL");
    }

    /**
     * WR-04 regression: when chatResponse().getResult() is null, the service must return
     * an empty answer string, not throw NullPointerException.
     */
    @Test
    void chat_nullGeneration_returnsEmptyAnswer_notNPE() {
        // Mock a response where getResult() returns null (content-filter rejection)
        ChatResponse mockChatResponse = mock(ChatResponse.class);
        when(mockChatResponse.getResult()).thenReturn(null);

        Map<String, Object> contextWithDocs = new HashMap<>();
        contextWithDocs.put(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());

        ChatClientResponse mockClientResponse = mock(ChatClientResponse.class);
        when(mockClientResponse.chatResponse()).thenReturn(mockChatResponse);
        when(mockClientResponse.context()).thenReturn(contextWithDocs);

        ChatClient mockClient = buildMockChatClient(mockClientResponse);
        when(strategy.forSession(any())).thenReturn(mockClient);

        ChatService service = new ChatService(strategy, keyHolder, new ObjectMapper());
        ChatResponseDto result = service.chat("Any question?", "test-session");

        // WR-04: must not throw NPE — must return empty answer
        assertThat(result.answer())
                .as("Null generation must produce empty answer, not NPE → 502")
                .isEqualTo("");
        assertThat(result.citations()).isEmpty();
    }

    /**
     * WR-03 regression: parseDemoResponse must handle numeric JSON field values without
     * ClassCastException. Jackson deserialises numeric JSON values as Integer/Long,
     * not String — using String.valueOf() handles both safely.
     */
    @Test
    void chat_demoResponse_withNumericFieldValues_doesNotThrowClassCastException() {
        // Numeric year field — Jackson would deserialise this as Integer, not String
        String demoJsonWithNumericYear = "{\"answer\":\"JPM regulatory answer.\",\"citations\":[{\"ticker\":\"JPM\",\"section\":\"Risk Factors\",\"source\":\"JPM 10-K\",\"excerpt\":\"capital requirements\",\"year\":2023}]}";

        ChatClientResponse mockResponse = buildMockResponse(demoJsonWithNumericYear, Map.of());

        ChatClient mockClient = buildMockChatClient(mockResponse);
        when(strategy.forSession(any())).thenReturn(mockClient);

        ChatService service = new ChatService(strategy, keyHolder, new ObjectMapper());
        // WR-03: must not throw ClassCastException when year is Integer
        ChatResponseDto result = service.chat("JPM capital?", "test-session");

        assertThat(result.answer())
                .as("Demo JSON with numeric field must be parsed without ClassCastException")
                .isEqualTo("JPM regulatory answer.");
        assertThat(result.citations()).hasSize(1);
        assertThat(result.citations().get(0).ticker()).isEqualTo("JPM");
    }

    // ── mock builder helpers ────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private ChatClientResponse buildMockResponse(String rawText, Map<String, Object> contextMap) {
        Generation mockGeneration = mock(Generation.class);
        AssistantMessage mockOutput = mock(AssistantMessage.class);
        when(mockOutput.getText()).thenReturn(rawText);
        when(mockGeneration.getOutput()).thenReturn(mockOutput);

        ChatResponse mockChatResponse = mock(ChatResponse.class);
        when(mockChatResponse.getResult()).thenReturn(mockGeneration);

        ChatClientResponse mockClientResponse = mock(ChatClientResponse.class);
        when(mockClientResponse.chatResponse()).thenReturn(mockChatResponse);

        // Return a mutable map so we can put null-like absent keys
        Map<String, Object> context = new HashMap<>(contextMap);
        when(mockClientResponse.context()).thenReturn(context);

        return mockClientResponse;
    }

    @SuppressWarnings("unchecked")
    private ChatClient buildMockChatClient(ChatClientResponse response) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient mockClient = mock(ChatClient.class);

        when(mockClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.advisors(any(java.util.function.Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.chatClientResponse()).thenReturn(response);

        return mockClient;
    }
}
