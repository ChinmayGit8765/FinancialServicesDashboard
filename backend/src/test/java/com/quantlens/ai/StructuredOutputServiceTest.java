package com.quantlens.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.ai.api.InsightEntry;
import com.quantlens.ai.api.StructuredInsightRecord;
import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.seed.AiSeedContent;
import com.quantlens.ai.seed.AiSeedContentRepository;
import com.quantlens.ai.service.StructuredOutputService;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StructuredOutputService}.
 *
 * <p>Covers the four acceptance criteria from the plan:
 * <ol>
 *   <li>Demo path: seed JSON deserialized via ObjectMapper.readValue — no forSession call</li>
 *   <li>Live path: .entity(StructuredInsightRecord.class) → record returned</li>
 *   <li>Unknown portfolio → 401 UNAUTHORIZED</li>
 *   <li>Provider error on live path → 502 BAD_GATEWAY (no message echo)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class StructuredOutputServiceTest {

    @Mock
    private ChatClientStrategy strategy;

    @Mock
    private LlmKeySessionHolder keyHolder;

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private AiSeedContentRepository seedRepo;

    // Real ObjectMapper — matches ChatService constructor pattern (parseDemoResponse uses it)
    private final ObjectMapper objectMapper = new ObjectMapper();

    private StructuredOutputService service;

    /** A minimal Portfolio stub for alice (style="Growth") */
    private Portfolio stubPortfolio;

    private static final Long PORTFOLIO_ID = 1L;

    private static final String VALID_SEED_JSON =
            "{\"title\":\"Sector Exposure\",\"subtitle\":\"AI-Detected Allocation (Demo)\"," +
            "\"series\":[" +
            "{\"label\":\"Technology\",\"value\":62.5}," +
            "{\"label\":\"Automotive\",\"value\":17.3}," +
            "{\"label\":\"Cash\",\"value\":20.2}" +
            "]}";

    @BeforeEach
    void setUp() {
        service = new StructuredOutputService(strategy, keyHolder, portfolioRepository, seedRepo, objectMapper);

        // Stub a Portfolio with style="Growth" (alice persona)
        stubPortfolio = mock(Portfolio.class);
        when(stubPortfolio.getStyle()).thenReturn("Growth");
        when(stubPortfolio.getName()).thenReturn("Alice Growth Portfolio");
    }

    // ── Demo path ──────────────────────────────────────────────────────────────

    /**
     * demoPath_seedJsonDeserialized_toRecord: with hasKey()=false, getInsight reads
     * the STRUCTURED_INSIGHT seed for the persona and returns a StructuredInsightRecord
     * with non-blank title and non-empty series — no strategy.forSession call.
     */
    @Test
    void demoPath_seedJsonDeserialized_toRecord() throws Exception {
        when(keyHolder.hasKey()).thenReturn(false);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stubPortfolio));

        // Stub AiSeedContent with valid JSON
        AiSeedContent seedContent = mock(AiSeedContent.class);
        when(seedContent.getContent()).thenReturn(VALID_SEED_JSON);
        when(seedRepo.findByTypeAndSubjectId("STRUCTURED_INSIGHT", "GROWTH"))
                .thenReturn(Optional.of(seedContent));

        StructuredInsightRecord result = service.getInsight(PORTFOLIO_ID);

        assertThat(result.title())
                .as("Demo path: title must be non-blank")
                .isNotBlank();
        assertThat(result.series())
                .as("Demo path: series must be non-empty")
                .isNotEmpty();
        assertThat(result.series())
                .as("Demo path: series entries must have non-blank labels")
                .allMatch(e -> e.label() != null && !e.label().isBlank());

        // CRITICAL: strategy.forSession must NEVER be called in demo mode
        verify(strategy, never()).forSession(any());
    }

    /**
     * demoPath_usesPersonaKey_GROWTH: verifies the seed is looked up by the uppercase
     * persona key derived from portfolio.getStyle().toUpperCase().
     */
    @Test
    void demoPath_usesPersonaKey_GROWTH() {
        when(keyHolder.hasKey()).thenReturn(false);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stubPortfolio));

        AiSeedContent seedContent = mock(AiSeedContent.class);
        when(seedContent.getContent()).thenReturn(VALID_SEED_JSON);
        when(seedRepo.findByTypeAndSubjectId(eq("STRUCTURED_INSIGHT"), eq("GROWTH")))
                .thenReturn(Optional.of(seedContent));

        service.getInsight(PORTFOLIO_ID);

        // Verify lookup used uppercase persona key
        verify(seedRepo).findByTypeAndSubjectId("STRUCTURED_INSIGHT", "GROWTH");
    }

    /**
     * demoPath_fallbackSeed_whenNoSeedRow: when no seed row exists, the fallback
     * JSON is used and the service returns a record with a non-null title.
     */
    @Test
    void demoPath_fallbackSeed_whenNoSeedRow() {
        when(keyHolder.hasKey()).thenReturn(false);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stubPortfolio));
        when(seedRepo.findByTypeAndSubjectId(anyString(), anyString())).thenReturn(Optional.empty());

        StructuredInsightRecord result = service.getInsight(PORTFOLIO_ID);

        assertThat(result.title()).isNotNull();
        assertThat(result.series()).isNotNull();
        verify(strategy, never()).forSession(any());
    }

    // ── Live path ─────────────────────────────────────────────────────────────

    /**
     * livePath_entityReturnsRecord_shapeValid: with hasKey()=true, getInsight routes through
     * strategy.forSession(...).call().entity(StructuredInsightRecord.class) and returns that
     * record; all series values are finite numbers (0..100).
     */
    @Test
    @SuppressWarnings("unchecked")
    void livePath_entityReturnsRecord_shapeValid() {
        when(keyHolder.hasKey()).thenReturn(true);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stubPortfolio));

        StructuredInsightRecord expectedRecord = new StructuredInsightRecord(
                "Live Sector Exposure",
                "Generated by LLM",
                List.of(
                        new InsightEntry("Technology", 55.0),
                        new InsightEntry("Financials", 25.0),
                        new InsightEntry("Cash", 20.0)
                )
        );

        ChatClient mockClient = buildMockChatClientForEntity(expectedRecord);
        when(strategy.forSession(any())).thenReturn(mockClient);

        StructuredInsightRecord result = service.getInsight(PORTFOLIO_ID);

        assertThat(result.title())
                .as("Live path: title must match the mocked record")
                .isEqualTo("Live Sector Exposure");
        assertThat(result.series())
                .as("Live path: series must be non-empty")
                .isNotEmpty();
        assertThat(result.series())
                .as("Live path: all series values must be in range 0..100")
                .allMatch(e -> Double.isFinite(e.value()) && e.value() >= 0 && e.value() <= 100);

        // Seed repo must NOT be called in live mode
        verify(seedRepo, never()).findByTypeAndSubjectId(anyString(), anyString());
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    /**
     * unknownPortfolio_throws401: findById empty → ResponseStatusException UNAUTHORIZED.
     */
    @Test
    void unknownPortfolio_throws401() {
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getInsight(PORTFOLIO_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode())
                            .as("Unknown portfolio must throw 401 UNAUTHORIZED")
                            .isEqualTo(HttpStatus.UNAUTHORIZED);
                });
    }

    /**
     * providerError_wrappedAs502: a thrown provider exception on the live path surfaces
     * as 502 BAD_GATEWAY with a generic message (never echoing the cause).
     */
    @Test
    @SuppressWarnings("unchecked")
    void providerError_wrappedAs502() {
        when(keyHolder.hasKey()).thenReturn(true);
        when(portfolioRepository.findById(PORTFOLIO_ID)).thenReturn(Optional.of(stubPortfolio));

        // Simulate a provider exception containing key material in the message
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient mockClient = mock(ChatClient.class);
        when(mockClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenThrow(new RuntimeException("sk-secret-api-key-should-not-appear"));
        when(strategy.forSession(any())).thenReturn(mockClient);

        assertThatThrownBy(() -> service.getInsight(PORTFOLIO_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode())
                            .as("Provider error must be wrapped as 502 BAD_GATEWAY")
                            .isEqualTo(HttpStatus.BAD_GATEWAY);
                    // T-08-LEAK: the response must never echo the provider error message
                    assertThat(rse.getReason())
                            .as("502 reason must not echo provider error (key leak prevention)")
                            .doesNotContain("sk-secret-api-key-should-not-appear");
                });
    }

    // ── Mock builder helpers ───────────────────────────────────────────────────

    /**
     * Builds a mock ChatClient chain that returns the given record from .entity().
     */
    @SuppressWarnings("unchecked")
    private ChatClient buildMockChatClientForEntity(StructuredInsightRecord returnVal) {
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec callSpec = mock(ChatClient.CallResponseSpec.class);
        ChatClient mockClient = mock(ChatClient.class);

        when(mockClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.advisors(any(Consumer.class))).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callSpec);
        when(callSpec.entity(StructuredInsightRecord.class)).thenReturn(returnVal);

        return mockClient;
    }
}
