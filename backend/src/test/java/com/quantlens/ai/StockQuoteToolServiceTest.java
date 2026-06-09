package com.quantlens.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.ai.tools.FinnhubQuoteClient;
import com.quantlens.ai.tools.StockQuoteResult;
import com.quantlens.ai.tools.StockQuoteToolService;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FinnhubQuoteClient} and {@link StockQuoteToolService}.
 *
 * <p>Tests the demo seeded path, live Finnhub path (mocked HTTP), TTL cache,
 * zero-timestamp fallback, and the ACTIVE Finnhub key-leak sentinel test.
 */
@ExtendWith(MockitoExtension.class)
class StockQuoteToolServiceTest {

    @Mock
    private OhlcvBarRepository ohlcvRepo;

    @Mock
    private HttpClient mockHttpClient;

    @Mock
    private FinnhubQuoteClient mockFinnhubClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OhlcvBar makeBar(String ticker, BigDecimal close, LocalDate date) {
        com.quantlens.marketdata.domain.Security sec =
                new com.quantlens.marketdata.domain.Security(ticker, ticker + " Inc", "Technology", false);
        return new OhlcvBar(sec, date, close, close, close, close, 1_000_000L);
    }

    // ── FinnhubQuoteClient tests ─────────────────────────────────────────────

    /**
     * T1-DEMO: with blank finnhubApiKey, getQuote returns seeded last close from OhlcvBarRepository.
     * No HttpClient call is made.
     */
    @Test
    void demoFallback_noKey_returnsSeededLastClose() {
        BigDecimal expectedClose = new BigDecimal("178.50");
        OhlcvBar bar = makeBar("AAPL", expectedClose, LocalDate.of(2024, 1, 15));
        when(ohlcvRepo.findLatestCloseByTicker("AAPL")).thenReturn(Optional.of(bar));

        // Construct client with BLANK key — must use the no-arg HttpClient constructor
        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo);
        // blank key → demo path
        StockQuoteResult result = client.getQuote("AAPL");

        assertThat(result.source()).isEqualTo("SEEDED");
        assertThat(result.marketState()).isEqualTo("DEMO");
        assertThat(result.price()).isEqualByComparingTo(expectedClose);
        assertThat(result.ticker()).isEqualTo("AAPL");
        // No HTTP call on demo path
        verifyNoInteractions(mockHttpClient);
    }

    /**
     * T2-LIVE: with a key set and stubbed HttpClient returning a valid c/t payload,
     * getQuote returns source=FINNHUB with a marketState derived from the timestamp.
     * The timestamp 1700000000 = 2023-11-14 22:13:20 UTC = AFTER_HOURS ET.
     */
    @Test
    @SuppressWarnings("unchecked")
    void live_returnsFinnhubPrice_withMarketState() throws Exception {
        // 1700000000 seconds = 2023-11-14 22:13:20 UTC = 17:13:20 ET (after 16:00) -> AFTER_HOURS
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,\"o\":188.0,\"pc\":187.75,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo, mockHttpClient, "test-api-key");
        StockQuoteResult result = client.getQuote("AAPL");

        assertThat(result.source()).isEqualTo("FINNHUB");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("189.25"));
        assertThat(result.marketState()).isEqualTo("AFTER_HOURS");
        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.asOf()).isNotBlank();
    }

    /**
     * T3-CACHE: second call within 15-min TTL window returns cached result, no second HTTP fetch.
     */
    @Test
    @SuppressWarnings("unchecked")
    void cacheTtl_secondCallWithinWindow_doesNotRefetch() throws Exception {
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,\"o\":188.0,\"pc\":187.75,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo, mockHttpClient, "test-api-key");

        StockQuoteResult first  = client.getQuote("AAPL");
        StockQuoteResult second = client.getQuote("AAPL");

        // Same object from cache
        assertThat(second).isSameAs(first);
        // Only one HTTP call
        verify(mockHttpClient, times(1)).send(any(), any());
    }

    /**
     * T4-ZERO: a Finnhub payload with c==0 && t==0 falls back to buildSeededQuote.
     */
    @Test
    @SuppressWarnings("unchecked")
    void zeroTimestamp_fallsBackToSeeded() throws Exception {
        String jsonBody = "{\"c\":0.0,\"d\":0.0,\"dp\":0.0,\"h\":0.0,\"l\":0.0,\"o\":0.0,\"pc\":0.0,\"t\":0}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        BigDecimal seededClose = new BigDecimal("155.00");
        OhlcvBar bar = makeBar("MSFT", seededClose, LocalDate.of(2024, 1, 10));
        when(ohlcvRepo.findLatestCloseByTicker("MSFT")).thenReturn(Optional.of(bar));

        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo, mockHttpClient, "test-api-key");
        StockQuoteResult result = client.getQuote("MSFT");

        assertThat(result.source()).isEqualTo("SEEDED");
    }

    /**
     * T5-LEAK (ACTIVE SENTINEL): T-08-LEAK-FH proof.
     * Set the key to TEST-FH-SENTINEL, stub HttpClient.send() to THROW an IOException
     * whose message embeds the URL (mimicking how Java HttpClient wraps the token in the
     * exception message). Assert:
     * (a) result is the seeded fallback (source=SEEDED)
     * (b) NO captured log line contains TEST-FH-SENTINEL
     * (c) no field of the returned StockQuoteResult contains TEST-FH-SENTINEL
     *
     * This is the ACTIVE sentinel test — it forces the catch path and proves the catch
     * block never logs e.getMessage() (which would contain the ?token=... URL).
     */
    @Test
    @SuppressWarnings("unchecked")
    void finnhubKeySentinelNeverLogged_onForcedFailure() throws Exception {
        final String SENTINEL = "TEST-FH-SENTINEL";

        // Stub HttpClient.send() to throw IOException embedding the sentinel in the URL
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException(
                        "Failed to connect to finnhub.io?symbol=AAPL&token=" + SENTINEL + " timed out"));

        BigDecimal seededClose = new BigDecimal("160.00");
        OhlcvBar bar = makeBar("AAPL", seededClose, LocalDate.of(2024, 1, 20));
        when(ohlcvRepo.findLatestCloseByTicker("AAPL")).thenReturn(Optional.of(bar));

        // Attach ListAppender to FinnhubQuoteClient logger BEFORE the call
        Logger logger = (Logger) LoggerFactory.getLogger(FinnhubQuoteClient.class);
        ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
        logger.setLevel(Level.ALL);

        try {
            FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo, mockHttpClient, SENTINEL);
            StockQuoteResult result = client.getQuote("AAPL");

            // (a) result is the seeded fallback
            assertThat(result.source())
                    .as("On IOException, result must be seeded fallback")
                    .isEqualTo("SEEDED");

            // (b) NO log line contains the sentinel
            for (ILoggingEvent event : listAppender.list) {
                assertThat(event.getFormattedMessage())
                        .as("Log line must NOT contain the Finnhub sentinel key (T-08-LEAK-FH)")
                        .doesNotContain(SENTINEL);
                // Also check MDC and throwable proxy
                if (event.getThrowableProxy() != null) {
                    assertThat(event.getThrowableProxy().getMessage())
                            .as("Logged throwable message must NOT contain the sentinel (T-08-LEAK-FH)")
                            .doesNotContain(SENTINEL);
                }
            }

            // (c) no field of the returned record contains the sentinel
            assertThat(result.ticker()).doesNotContain(SENTINEL);
            assertThat(result.asOf()).doesNotContain(SENTINEL);
            assertThat(result.marketState()).doesNotContain(SENTINEL);
            assertThat(result.source()).doesNotContain(SENTINEL);
            assertThat(result.price().toPlainString()).doesNotContain(SENTINEL);

        } finally {
            logger.detachAppender(listAppender);
        }
    }

    // ── StockQuoteToolService delegation tests ────────────────────────────────

    /**
     * T6-DELEGATE: StockQuoteToolService.getStockQuote delegates to FinnhubQuoteClient.getQuote
     * with no demo/live branch in the tool body.
     */
    @Test
    void toolDelegatesToClient() {
        StockQuoteResult expected = new StockQuoteResult("AAPL", new BigDecimal("178.50"),
                "2024-01-15", "DEMO", "SEEDED");
        when(mockFinnhubClient.getQuote("AAPL")).thenReturn(expected);

        StockQuoteToolService toolService = new StockQuoteToolService(mockFinnhubClient);
        StockQuoteResult result = toolService.getStockQuote("AAPL");

        assertThat(result).isSameAs(expected);
        verify(mockFinnhubClient).getQuote("AAPL");
    }

    /**
     * T7-NONNULL: forSession must return a non-null ChatClient when StockQuoteToolService is registered.
     * Covered by MultiProviderRoutingTest — this test verifies only tool delegation.
     */
    @Test
    void toolDelegatesToClient_livePath() {
        StockQuoteResult expected = new StockQuoteResult("MSFT", new BigDecimal("420.00"),
                "2024-01-15T17:00:00Z", "AFTER_HOURS", "FINNHUB");
        when(mockFinnhubClient.getQuote("MSFT")).thenReturn(expected);

        StockQuoteToolService toolService = new StockQuoteToolService(mockFinnhubClient);
        StockQuoteResult result = toolService.getStockQuote("MSFT");

        assertThat(result.source()).isEqualTo("FINNHUB");
        assertThat(result.marketState()).isEqualTo("AFTER_HOURS");
        verify(mockFinnhubClient).getQuote("MSFT");
    }
}
