package com.quantlens.ai;

import com.quantlens.ai.chat.DemoModeAdvisor;
import com.quantlens.ai.seed.AiSeedContent;
import com.quantlens.ai.seed.AiSeedContentRepository;
import com.quantlens.ai.session.LlmKeySessionHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemoModeAdvisor}.
 *
 * <p>No Spring context — pure Mockito test. These tests PASS now because the advisor
 * is fully implemented in Task 2. They serve as the executable specification for the
 * advisor's demo/live mode contract.
 *
 * <p>A1 verification: only {@link ChatClientRequest} / {@link ChatClientResponse} are imported
 * (not the pre-1.0 {@code AdvisedRequest}/{@code AdvisedResponse}).
 */
class DemoModeAdvisorTest {

    private AiSeedContentRepository seedRepo;
    private LlmKeySessionHolder keyHolder;
    private DemoModeAdvisor advisor;
    private CallAdvisorChain chain;

    @BeforeEach
    void setUp() {
        seedRepo  = mock(AiSeedContentRepository.class);
        keyHolder = mock(LlmKeySessionHolder.class);
        chain     = mock(CallAdvisorChain.class);
        advisor   = new DemoModeAdvisor(keyHolder, seedRepo);
    }

    @Test
    void shortCircuitsInDemoMode_returnsSeededContent_withoutCallingChain() {
        when(keyHolder.hasKey()).thenReturn(false);
        when(seedRepo.findByTypeAndSubjectId("EXPLAIN_POSITION", "AAPL"))
                .thenReturn(Optional.of(new AiSeedContent("EXPLAIN_POSITION", "AAPL", "AAPL narrative")));

        ChatClientRequest request = buildRequest("EXPLAIN_POSITION", "AAPL");
        ChatClientResponse response = advisor.adviseCall(request, chain);

        // Chain MUST NOT be called in demo mode (T-06-03: no network call)
        verifyNoInteractions(chain);
        assertThat(extractContent(response)).isEqualTo("AAPL narrative");
    }

    @Test
    void shortCircuitsInDemoMode_returnsFallback_whenNoSeedFound() {
        when(keyHolder.hasKey()).thenReturn(false);
        when(seedRepo.findByTypeAndSubjectId("EXPLAIN_POSITION", "XYZ"))
                .thenReturn(Optional.empty());

        ChatClientRequest request = buildRequest("EXPLAIN_POSITION", "XYZ");
        ChatClientResponse response = advisor.adviseCall(request, chain);

        verifyNoInteractions(chain);
        assertThat(extractContent(response)).isEqualTo("AI narrative not available in demo mode.");
    }

    @Test
    void passesThroughWhenKeyPresent_delegatesToChain_withoutSeedLookup() {
        when(keyHolder.hasKey()).thenReturn(true);

        ChatClientRequest request = buildRequest("EXPLAIN_POSITION", "AAPL");
        ChatClientResponse expected = mock(ChatClientResponse.class);
        when(chain.nextCall(request)).thenReturn(expected);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        assertThat(result).isSameAs(expected);
        verify(chain).nextCall(request);
        // Seed repo MUST NOT be queried in live mode
        verifyNoInteractions(seedRepo);
    }

    @Test
    void getName_returnsDemoModeAdvisor() {
        assertThat(advisor.getName()).isEqualTo("DemoModeAdvisor");
    }

    @Test
    void getOrder_returnsHighestPrecedence() {
        assertThat(advisor.getOrder()).isEqualTo(Integer.MIN_VALUE);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a minimal {@link ChatClientRequest} with the given advisor context params.
     *
     * <p>Uses the Spring AI 1.1.x {@code ChatClientRequest.builder()} API.
     * A non-null {@link Prompt} is required by the builder — an empty one suffices for
     * unit tests because the DemoModeAdvisor only reads from {@code request.context()},
     * never from the prompt text itself.
     * These context params correspond to the {@code AI_SEED_TYPE} / {@code AI_SEED_SUBJECT}
     * keys set by the service layer before calling the ChatClient.
     */
    private ChatClientRequest buildRequest(String seedType, String seedSubject) {
        Map<String, Object> context = new HashMap<>();
        context.put("AI_SEED_TYPE", seedType);
        context.put("AI_SEED_SUBJECT", seedSubject);
        return ChatClientRequest.builder()
                .prompt(new Prompt(""))   // non-null required by builder; content unused in demo path
                .context(context)
                .build();
    }

    /**
     * Extracts the text content from the first generation of a {@link ChatClientResponse}.
     */
    private String extractContent(ChatClientResponse response) {
        return response.chatResponse()
                .getResult()
                .getOutput()
                .getText();
    }
}
