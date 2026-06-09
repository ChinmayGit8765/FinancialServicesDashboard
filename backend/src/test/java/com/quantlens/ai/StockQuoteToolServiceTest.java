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
import com.quantlens.marketdata.domain.Security;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link FinnhubQuoteClient} and {@link StockQuoteToolService}.
 *
 * <p>Tests the demo seeded path, live Finnhub path (mocked HTTP), TTL cache,
 * zero-price/timestamp fallback (CR-03/CR-04), the ACTIVE Finnhub key-leak sentinel
 * test (T-08-LEAK-FH), and regression tests for all Phase-08 critical/warning fixes.
 *
 * <p>Because the test lives in {@code com.quantlens.ai} (not {@code com.quantlens.ai.tools}),
 * the package-private 4-arg constructor is not directly accessible. We use the public
 * primary constructor and then inject {@code httpClient} / {@code finnhubApiKey} via
 * {@link ReflectionTestUtils#setField} — this is the correct pattern for testing internal
 * fields across packages without widening visibility.
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

    /** Helper: build a real OhlcvBar with a given close price. */
    private OhlcvBar makeBar(String ticker, BigDecimal close, LocalDate date) {
        Security sec = new Security(ticker, ticker + " Inc", "Technology", false);
        return new OhlcvBar(sec, date, close, close, close, close, 1_000_000L);
    }

    /**
     * Helper: build a {@link FinnhubQuoteClient} via the public constructor and inject
     * the httpClient + key via ReflectionTestUtils (avoids package-private access from
     * a different test package).
     */
    private FinnhubQuoteClient buildClient(String apiKey) {
        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo);
        ReflectionTestUtils.setField(client, "httpClient",    mockHttpClient);
        ReflectionTestUtils.setField(client, "finnhubApiKey", apiKey);
        return client;
    }

    // ── FinnhubQuoteClient tests ─────────────────────────────────────────────

    /**
     * T1-DEMO: with blank finnhubApiKey, getQuote returns seeded last close from
     * OhlcvBarRepository. No HttpClient call is made.
     */
    @Test
    void demoFallback_noKey_returnsSeededLastClose() {
        BigDecimal expectedClose = new BigDecimal("178.50");
        OhlcvBar bar = makeBar("AAPL", expectedClose, LocalDate.of(2024, 1, 15));
        when(ohlcvRepo.findLatestCloseByTicker("AAPL")).thenReturn(Optional.of(bar));

        // Primary constructor sets blank key → demo path
        FinnhubQuoteClient client = new FinnhubQuoteClient(objectMapper, ohlcvRepo);
        StockQuoteResult result = client.getQuote("AAPL");

        assertThat(result.source()).isEqualTo("SEEDED");
        assertThat(result.marketState()).isEqualTo("DEMO");
        assertThat(result.price()).isEqualByComparingTo(expectedClose);
        assertThat(result.ticker()).isEqualTo("AAPL");
        verifyNoInteractions(mockHttpClient);
    }

    /**
     * T2-LIVE: with a key set and stubbed HttpClient returning a valid c/t payload,
     * getQuote returns source=FINNHUB with a marketState derived from the timestamp.
     * Timestamp 1700000000 = 2023-11-14 22:13:20 UTC = 17:13:20 ET → AFTER_HOURS.
     *
     * <p>CR-01 regression: the HTTP request must NOT contain the token in the URI;
     * the token must be in the X-Finnhub-Token header.
     */
    @Test
    @SuppressWarnings("unchecked")
    void live_returnsFinnhubPrice_withMarketState() throws Exception {
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,"
                + "\"o\":188.0,\"pc\":187.75,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = buildClient("test-api-key");
        StockQuoteResult result = client.getQuote("AAPL");

        assertThat(result.source()).isEqualTo("FINNHUB");
        assertThat(result.price()).isEqualByComparingTo(new BigDecimal("189.25"));
        assertThat(result.marketState()).isEqualTo("AFTER_HOURS");
        assertThat(result.ticker()).isEqualTo("AAPL");
        assertThat(result.asOf()).isNotBlank();

        // CR-01 regression: verify token is NOT in the URI
        verify(mockHttpClient).send(
                argThat(req -> !req.uri().toString().contains("token=")),
                any());
    }

    /**
     * T2b-CR01-HEADER: the outgoing request carries the token in the X-Finnhub-Token
     * header, NOT in the URL query string (CR-01 — prevents JDK/Spring HTTP log exposure).
     */
    @Test
    @SuppressWarnings("unchecked")
    void live_tokenInHeader_notInUri() throws Exception {
        final String API_KEY = "my-secret-key-12345";
        String jsonBody = "{\"c\":100.0,\"d\":0.0,\"dp\":0.0,\"h\":101.0,\"l\":99.0,"
                + "\"o\":100.0,\"pc\":99.5,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = buildClient(API_KEY);
        client.getQuote("TSLA");

        verify(mockHttpClient).send(
                argThat(req -> {
                    // URI must NOT contain the token
                    String uriStr = req.uri().toString();
                    assertThat(uriStr).as("URI must not contain the API key (CR-01)").doesNotContain(API_KEY);
                    assertThat(uriStr).as("URI must not contain a 'token=' parameter (CR-01)").doesNotContain("token=");
                    // Header must carry the token
                    assertThat(req.headers().firstValue("X-Finnhub-Token"))
                            .as("X-Finnhub-Token header must carry the API key (CR-01)")
                            .isPresent()
                            .hasValue(API_KEY);
                    return true;
                }),
                any());
    }

    /**
     * T3-CACHE: second call within 15-min TTL window returns cached result; only one HTTP call.
     */
    @Test
    @SuppressWarnings("unchecked")
    void cacheTtl_secondCallWithinWindow_doesNotRefetch() throws Exception {
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,"
                + "\"o\":188.0,\"pc\":187.75,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = buildClient("test-api-key");

        StockQuoteResult first  = client.getQuote("AAPL");
        StockQuoteResult second = client.getQuote("AAPL");

        assertThat(second).isSameAs(first);
        verify(mockHttpClient, times(1)).send(any(), any());
    }

    /**
     * T3b-CR02-EXPIRY: CR-02 regression — an expired cache entry triggers a fresh fetch.
     * We manipulate the cache directly via clearCache() to simulate expiry (since setting
     * an artificially short TTL is not exposed), verify the client re-fetches.
     */
    @Test
    @SuppressWarnings("unchecked")
    void cache_expiredEntry_triggersRefresh() throws Exception {
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,"
                + "\"o\":188.0,\"pc\":187.75,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        FinnhubQuoteClient client = buildClient("test-api-key");

        StockQuoteResult first = client.getQuote("AAPL");

        // Simulate expiry by clearing the cache via ReflectionTestUtils (clearCache is package-private)
        ReflectionTestUtils.invokeMethod(client, "clearCache");

        StockQuoteResult second = client.getQuote("AAPL");

        // Both results have correct FINNHUB source — but they are separate instances
        assertThat(first.source()).isEqualTo("FINNHUB");
        assertThat(second.source()).isEqualTo("FINNHUB");
        // After cache clear, exactly 2 HTTP calls were made
        verify(mockHttpClient, times(2)).send(any(), any());
    }

    /**
     * T4-ZERO: a Finnhub payload with c==0 && t==0 falls back to buildSeededQuote.
     */
    @Test
    @SuppressWarnings("unchecked")
    void zeroTimestamp_fallsBackToSeeded() throws Exception {
        String jsonBody = "{\"c\":0.0,\"d\":0.0,\"dp\":0.0,\"h\":0.0,\"l\":0.0,"
                + "\"o\":0.0,\"pc\":0.0,\"t\":0}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        BigDecimal seededClose = new BigDecimal("155.00");
        OhlcvBar bar = makeBar("MSFT", seededClose, LocalDate.of(2024, 1, 10));
        when(ohlcvRepo.findLatestCloseByTicker("MSFT")).thenReturn(Optional.of(bar));

        FinnhubQuoteClient client = buildClient("test-api-key");
        StockQuoteResult result = client.getQuote("MSFT");

        assertThat(result.source()).isEqualTo("SEEDED");
    }

    /**
     * T4b-CR03: CR-03 regression — Finnhub returns zero price with a valid (non-zero)
     * timestamp (e.g. market closed, real ticker). The result must fall back to seeded
     * and must NOT be cached with source=FINNHUB and price=0.
     */
    @Test
    @SuppressWarnings("unchecked")
    void cr03_zeroPriceValidTimestamp_fallsBackToSeeded_notCached() throws Exception {
        // c=0.0 but t is a real timestamp — this was the CR-03 bug
        String jsonBody = "{\"c\":0.0,\"d\":0.0,\"dp\":0.0,\"h\":0.0,\"l\":0.0,"
                + "\"o\":0.0,\"pc\":150.0,\"t\":1700000000}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        BigDecimal seededClose = new BigDecimal("148.75");
        OhlcvBar bar = makeBar("NVDA", seededClose, LocalDate.of(2024, 1, 10));
        when(ohlcvRepo.findLatestCloseByTicker("NVDA")).thenReturn(Optional.of(bar));

        FinnhubQuoteClient client = buildClient("test-api-key");
        StockQuoteResult result = client.getQuote("NVDA");

        // Must fall back to seeded — zero price is never a valid quote
        assertThat(result.source())
                .as("CR-03: zero price with valid timestamp must fall back to seeded, not cache $0.00")
                .isEqualTo("SEEDED");
        assertThat(result.price())
                .as("CR-03: result price must be the seeded close, not $0.00")
                .isEqualByComparingTo(seededClose);
    }

    /**
     * T4c-CR04: CR-04 regression — Finnhub returns non-zero price with zero timestamp
     * (c=189.25, t=0). The result must fall back to seeded — the 1970-01-01 epoch asOf
     * and wrong market state must never be cached or returned.
     */
    @Test
    @SuppressWarnings("unchecked")
    void cr04_nonZeroPriceZeroTimestamp_fallsBackToSeeded_noEpochAsOf() throws Exception {
        // c is real, but t=0 — this is the CR-04 bug (deriveMarketState(0L) gives AFTER_HOURS
        // with asOf="1970-01-01T00:00:00Z")
        String jsonBody = "{\"c\":189.25,\"d\":1.50,\"dp\":0.80,\"h\":190.0,\"l\":187.0,"
                + "\"o\":188.0,\"pc\":187.75,\"t\":0}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.body()).thenReturn(jsonBody);
        when(mockHttpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        BigDecimal seededClose = new BigDecimal("185.00");
        OhlcvBar bar = makeBar("AAPL", seededClose, LocalDate.of(2024, 1, 10));
        when(ohlcvRepo.findLatestCloseByTicker("AAPL")).thenReturn(Optional.of(bar));

        FinnhubQuoteClient client = buildClient("test-api-key");
        StockQuoteResult result = client.getQuote("AAPL");

        // Must fall back to seeded — epoch-zero timestamp is unusable
        assertThat(result.source())
                .as("CR-04: non-zero price with t=0 must fall back to seeded (no epoch-1970 asOf)")
                .isEqualTo("SEEDED");
        assertThat(result.asOf())
                .as("CR-04: asOf must NOT be the epoch-1970 timestamp")
                .doesNotContain("1970");
        // Price must be seeded, not the live 189.25 with a broken timestamp
        assertThat(result.price())
                .as("CR-04: price must be the seeded close, not the live price with a broken timestamp")
                .isEqualByComparingTo(seededClose);
    }

    /**
     * T4d-WR01: WR-01 regression — null ticker must return a safe sentinel, not NPE.
     */
    @Test
    void wr01_nullTicker_returnsSentinelNotNpe() {
        FinnhubQuoteClient client = buildClient("test-api-key");

        StockQuoteResult result = client.getQuote(null);

        assertThat(result).as("WR-01: null ticker must not throw NPE").isNotNull();
        assertThat(result.ticker()).isEqualTo("UNKNOWN");
        assertThat(result.source()).isEqualTo("SEEDED");
        assertThat(result.price()).isEqualByComparingTo(BigDecimal.ZERO);
        // HttpClient must not be called — null ticker is a guard-exit
        verifyNoInteractions(mockHttpClient);
    }

    /**
     * T4e-WR01: WR-01 regression — blank ticker must also return a safe sentinel.
     */
    @Test
    void wr01_blankTicker_returnsSentinelNotNpe() {
        FinnhubQuoteClient client = buildClient("test-api-key");

        StockQuoteResult result = client.getQuote("   ");

        assertThat(result).as("WR-01: blank ticker must not throw NPE").isNotNull();
        assertThat(result.ticker()).isEqualTo("UNKNOWN");
        assertThat(result.source()).isEqualTo("SEEDED");
        verifyNoInteractions(mockHttpClient);
    }

    /**
     * T5-LEAK (ACTIVE SENTINEL): T-08-LEAK-FH proof.
     *
     * <p>Set the key to TEST-FH-SENTINEL, stub HttpClient.send() to THROW an IOException
     * whose message embeds the sentinel. Assert:
     * <ol>
     *   <li>result is the seeded fallback (source=SEEDED)</li>
     *   <li>NO captured log line (formatted message or throwable message) contains TEST-FH-SENTINEL</li>
     *   <li>no field of the returned StockQuoteResult contains TEST-FH-SENTINEL</li>
     * </ol>
     *
     * <p>This is the ACTIVE sentinel test — it forces the catch path and proves the catch
     * block NEVER logs {@code e.getMessage()} (which would contain the sentinel).
     * It is NOT the vacuous demo-blank-key path: the key IS set, and the HttpClient throws.
     *
     * <p>CR-01 note: with the header fix the sentinel is no longer in the URI, but the
     * exception message still embeds it (to cover future regressions). The catch block
     * must still not log it.
     */
    @Test
    @SuppressWarnings("unchecked")
    void finnhubKeySentinelNeverLogged_onForcedFailure() throws Exception {
        final String SENTINEL = "TEST-FH-SENTINEL";

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
        logger.setLevel(Level.TRACE);

        try {
            FinnhubQuoteClient client = buildClient(SENTINEL);
            StockQuoteResult result = client.getQuote("AAPL");

            // (a) result is the seeded fallback — NOT the demo-blank-key path (key IS set)
            assertThat(result.source())
                    .as("On IOException with live key set, result must be seeded fallback")
                    .isEqualTo("SEEDED");

            // (b) NO log line contains the sentinel — catch block must log only the ticker
            for (ILoggingEvent event : listAppender.list) {
                assertThat(event.getFormattedMessage())
                        .as("Log formatted message must NOT contain the Finnhub sentinel (T-08-LEAK-FH)")
                        .doesNotContain(SENTINEL);
                if (event.getThrowableProxy() != null) {
                    // Catch block must NOT log throwable — if it did, the token would appear here
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
     * unchanged — no demo/live branch inside the tool body.
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
     * T7-DELEGATE-LIVE: tool delegates for live path result unchanged.
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

    /**
     * T8-IN01: IN-01 — lowercase ticker is canonicalized to uppercase before the
     * FinnhubQuoteClient call, so "aapl" → client receives "AAPL".
     */
    @Test
    void in01_lowercaseTicker_canonicalizedToUppercase() {
        StockQuoteResult expected = new StockQuoteResult("AAPL", new BigDecimal("178.50"),
                "2024-01-15", "DEMO", "SEEDED");
        when(mockFinnhubClient.getQuote("AAPL")).thenReturn(expected);

        StockQuoteToolService toolService = new StockQuoteToolService(mockFinnhubClient);
        StockQuoteResult result = toolService.getStockQuote("aapl");

        assertThat(result).isSameAs(expected);
        // Verify the client was called with uppercase "AAPL", not "aapl"
        verify(mockFinnhubClient).getQuote("AAPL");
        verify(mockFinnhubClient, never()).getQuote("aapl");
    }

    /**
     * T9-IN01: IN-01 — mixed-case ticker "Aapl" is canonicalized to "AAPL".
     */
    @Test
    void in01_mixedCaseTicker_canonicalizedToUppercase() {
        StockQuoteResult expected = new StockQuoteResult("AAPL", new BigDecimal("178.50"),
                "2024-01-15", "DEMO", "SEEDED");
        when(mockFinnhubClient.getQuote("AAPL")).thenReturn(expected);

        StockQuoteToolService toolService = new StockQuoteToolService(mockFinnhubClient);
        toolService.getStockQuote("Aapl");

        verify(mockFinnhubClient).getQuote("AAPL");
    }
}
