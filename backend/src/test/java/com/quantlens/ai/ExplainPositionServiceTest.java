package com.quantlens.ai;

import com.quantlens.ai.chat.ChatClientStrategy;
import com.quantlens.ai.service.ExplainPositionService;
import com.quantlens.ai.session.LlmKeySessionHolder;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ExplainPositionService}.
 *
 * <p>No Spring context — pure Mockito tests. Focuses on:
 * <ol>
 *   <li>IDOR guard: NOT_FOUND thrown before any ChatClient call for an unheld ticker</li>
 *   <li>Happy path: returns the narrative from the ChatClient when the ticker is held</li>
 * </ol>
 *
 * <h2>T-06-08 IDOR proof</h2>
 * When the requested ticker is NOT in the principal's holdings, the service throws
 * {@link HttpStatus#NOT_FOUND} and {@code verifyNoInteractions(strategy)} confirms
 * that the ChatClient is NEVER reached — no seed lookup, no provider call, no AI charge.
 */
class ExplainPositionServiceTest {

    private ChatClientStrategy strategy;
    private LlmKeySessionHolder keyHolder;
    private PositionRepository positionRepository;
    private ExplainPositionService service;

    @BeforeEach
    void setUp() {
        strategy           = mock(ChatClientStrategy.class);
        keyHolder          = mock(LlmKeySessionHolder.class);
        positionRepository = mock(PositionRepository.class);
        service = new ExplainPositionService(strategy, keyHolder, positionRepository);
    }

    // ── IDOR guard tests ──────────────────────────────────────────────────────

    /**
     * T-06-08: When the requested ticker is NOT in the principal's holdings, the service
     * must throw NOT_FOUND BEFORE any ChatClient call.
     *
     * <p>{@code verifyNoInteractions(strategy)} proves the advisor chain (and thus the seed
     * lookup and any provider call) is never reached for an unheld ticker.
     */
    @Test
    void explain_tickerNotInPortfolio_throwsNotFound_beforeAnyChatClientCall() {
        // Holdings contain MSFT but NOT the requested TSLA
        Position msftPosition = buildPosition("MSFT", "Technology", BigDecimal.valueOf(20));
        when(positionRepository.findByPortfolioIdWithSecurity(1L))
                .thenReturn(List.of(msftPosition));

        assertThatThrownBy(() -> service.explain(1L, "TSLA"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                });

        // ChatClientStrategy MUST NOT be touched — proves no AI call for unheld ticker
        verifyNoInteractions(strategy);
    }

    /**
     * Empty holdings list: any ticker request must throw NOT_FOUND without calling the strategy.
     */
    @Test
    void explain_emptyPortfolio_throwsNotFound() {
        when(positionRepository.findByPortfolioIdWithSecurity(99L))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.explain(99L, "AAPL"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex ->
                        assertThat(((ResponseStatusException) ex).getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND));

        verifyNoInteractions(strategy);
    }

    // ── Happy path ────────────────────────────────────────────────────────────

    /**
     * Happy path: when the ticker is in the principal's holdings, the service routes through
     * the ChatClient strategy and returns an ExplainResponseDto with the content.
     *
     * <p>Uses RETURNS_DEEP_STUBS so the fluent ChatClient builder chain compiles without
     * manually stubbing every intermediate call spec object.
     */
    @Test
    void explain_tickerInPortfolio_returnsNarrativeFromChatClient() {
        // Holdings contain AAPL — the requested ticker
        Position aaplPosition = buildPosition("AAPL", "Technology", BigDecimal.valueOf(50));
        when(positionRepository.findByPortfolioIdWithSecurity(1L))
                .thenReturn(List.of(aaplPosition));

        // Deep-stub the ChatClient fluent chain: strategy.forSession() → client.prompt()
        // → .system() → .user() → .advisors() → .call() → .content() = "seeded narrative"
        ChatClient deepClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(strategy.forSession(keyHolder)).thenReturn(deepClient);
        when(deepClient.prompt()
                .system(anyString())
                .user(anyString())
                .advisors(org.mockito.ArgumentMatchers.<java.util.function.Consumer<
                        org.springframework.ai.chat.client.ChatClient.AdvisorSpec>>any())
                .call()
                .content())
                .thenReturn("AAPL narrative from seed");

        var result = service.explain(1L, "AAPL");

        assertThat(result).isNotNull();
        assertThat(result.narrative()).isEqualTo("AAPL narrative from seed");
    }

    /**
     * Case-insensitive ticker matching: "aapl" in the request should match "AAPL" in holdings.
     */
    @Test
    void explain_tickerMatchIsCaseInsensitive() {
        Position aaplPosition = buildPosition("AAPL", "Technology", BigDecimal.valueOf(50));
        when(positionRepository.findByPortfolioIdWithSecurity(1L))
                .thenReturn(List.of(aaplPosition));

        ChatClient deepClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(strategy.forSession(keyHolder)).thenReturn(deepClient);
        when(deepClient.prompt()
                .system(anyString())
                .user(anyString())
                .advisors(org.mockito.ArgumentMatchers.<java.util.function.Consumer<
                        org.springframework.ai.chat.client.ChatClient.AdvisorSpec>>any())
                .call()
                .content())
                .thenReturn("AAPL narrative");

        // Request with lowercase ticker — must still find the holding
        var result = service.explain(1L, "aapl");
        assertThat(result.narrative()).isEqualTo("AAPL narrative");
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a minimal {@link Position} mock with the given ticker.
     * Only the fields read by {@link ExplainPositionService} are stubbed.
     */
    private Position buildPosition(String ticker, String sector, BigDecimal qty) {
        var security = mock(com.quantlens.marketdata.domain.Security.class);
        when(security.getTicker()).thenReturn(ticker);
        when(security.getName()).thenReturn(ticker + " Inc.");
        when(security.getSector()).thenReturn(sector);

        var position = mock(Position.class);
        when(position.getSecurity()).thenReturn(security);
        when(position.getQuantity()).thenReturn(qty);
        when(position.getAvgCostBasis()).thenReturn(BigDecimal.valueOf(100.00));
        return position;
    }
}
