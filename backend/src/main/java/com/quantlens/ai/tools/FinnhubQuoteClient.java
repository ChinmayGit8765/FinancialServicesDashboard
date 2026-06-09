package com.quantlens.ai.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thin Java {@link HttpClient} wrapper for the Finnhub {@code /quote} endpoint with a
 * 15-minute TTL cache and a seeded-OHLCV fallback for demo mode.
 *
 * <h2>Key-leak prevention (T-08-LEAK-FH)</h2>
 * The catch block logs ONLY the ticker — never {@code e.getMessage()} or {@code e.toString()}.
 * Java {@link HttpClient} wraps the request URL (which includes {@code ?token=...}) into the
 * exception message; logging the message would leak the key.
 *
 * <h2>Demo mode</h2>
 * When {@code FINNHUB_API_KEY} is blank (default), {@link #getQuote(String)} immediately calls
 * {@link #buildSeededQuote(String)} — no network call is made.
 *
 * <h2>Test injection</h2>
 * The package-private constructor accepting an {@link HttpClient} allows tests to inject a stub.
 */
@Component
public class FinnhubQuoteClient {

    private static final Logger log = LoggerFactory.getLogger(FinnhubQuoteClient.class);
    private static final String BASE_URL = "https://finnhub.io/api/v1/quote";
    private static final long   TTL_MILLIS = 15L * 60 * 1000; // 15 minutes

    private final ConcurrentHashMap<String, CachedQuote> cache = new ConcurrentHashMap<>();

    private HttpClient               httpClient;   // non-final: injectable via ReflectionTestUtils in tests
    private final ObjectMapper       objectMapper;
    private final OhlcvBarRepository ohlcvRepo;

    @Value("${FINNHUB_API_KEY:}")
    private String finnhubApiKey;

    // ── primary constructor (Spring-managed; uses JDK default HttpClient) ─────

    public FinnhubQuoteClient(ObjectMapper objectMapper, OhlcvBarRepository ohlcvRepo) {
        this(objectMapper, ohlcvRepo, HttpClient.newHttpClient(), "");
    }

    // ── package-private test constructor (accepts injectable HttpClient) ──────

    FinnhubQuoteClient(ObjectMapper objectMapper, OhlcvBarRepository ohlcvRepo,
                       HttpClient httpClient, String finnhubApiKey) {
        this.objectMapper    = objectMapper;
        this.ohlcvRepo       = ohlcvRepo;
        this.httpClient      = httpClient;
        this.finnhubApiKey   = finnhubApiKey;
    }

    // ── public API ────────────────────────────────────────────────────────────

    /**
     * Returns a {@link StockQuoteResult} for the given ticker.
     *
     * <ul>
     *   <li>Blank key → seeded last close (demo/no-network path)</li>
     *   <li>Cache hit within 15-min TTL → cached result (no Finnhub call)</li>
     *   <li>Live → Finnhub {@code /quote} call; {@code c==0 && t==0} → seeded fallback</li>
     *   <li>Any exception → seeded fallback; catch block NEVER logs the exception message</li>
     * </ul>
     *
     * @param ticker stock ticker symbol (e.g. "AAPL")
     * @return a non-null {@link StockQuoteResult}
     */
    public StockQuoteResult getQuote(String ticker) {
        if (finnhubApiKey == null || finnhubApiKey.isBlank()) {
            return buildSeededQuote(ticker);
        }

        // Check TTL cache
        CachedQuote cached = cache.get(ticker);
        if (cached != null && (System.currentTimeMillis() - cached.fetchedAt()) < TTL_MILLIS) {
            return cached.result();
        }

        // Live Finnhub call
        try {
            String uri = BASE_URL + "?symbol=" + ticker + "&token=" + finnhubApiKey;
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            FinnhubQuoteResponse raw = objectMapper.readValue(response.body(),
                    FinnhubQuoteResponse.class);

            // t == 0 means Finnhub returned no data (unknown ticker or pre-market no data)
            if (raw.c() == 0.0 && raw.t() == 0L) {
                log.warn("Finnhub returned zero price for ticker {} — falling back to seeded", ticker);
                return buildSeededQuote(ticker);
            }

            String marketState = deriveMarketState(raw.t());
            StockQuoteResult result = new StockQuoteResult(
                    ticker,
                    BigDecimal.valueOf(raw.c()).setScale(2, RoundingMode.HALF_UP),
                    Instant.ofEpochSecond(raw.t()).toString(),
                    marketState,
                    "FINNHUB"
            );

            cache.put(ticker, new CachedQuote(result, System.currentTimeMillis()));
            return result;

        } catch (Exception e) {
            // T-08-LEAK-FH: NEVER log e.getMessage() — Java HttpClient embeds ?token=... in the URL
            log.warn("Finnhub call failed for ticker {} — falling back to seeded", ticker);
            return buildSeededQuote(ticker);
        }
    }

    // ── package-private for test isolation (Pitfall 4) ───────────────────────

    void clearCache() {
        cache.clear();
    }

    // ── private helpers ───────────────────────────────────────────────────────

    /**
     * Derives the NYSE market state from a Unix epoch-seconds timestamp.
     *
     * <p>NYSE regular hours are Mon–Fri 09:30–16:00 ET. Pre-market starts at 04:00 ET.
     * Weekends and outside pre-market hours are CLOSED.
     */
    private String deriveMarketState(long epochSeconds) {
        ZonedDateTime dt = Instant.ofEpochSecond(epochSeconds)
                .atZone(ZoneId.of("America/New_York"));
        DayOfWeek day = dt.getDayOfWeek();
        LocalTime time = dt.toLocalTime();

        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return "CLOSED";
        }

        LocalTime preMarketOpen = LocalTime.of(4, 0);
        LocalTime marketOpen    = LocalTime.of(9, 30);
        LocalTime marketClose   = LocalTime.of(16, 0);

        if (time.isBefore(preMarketOpen)) return "CLOSED";
        if (time.isBefore(marketOpen))    return "PRE_MARKET";
        if (time.isBefore(marketClose))   return "REGULAR";
        return "AFTER_HOURS";
    }

    /**
     * Builds a {@link StockQuoteResult} from the most recent seeded OHLCV close.
     * Returns a zero-price result if no seeded data is found.
     */
    private StockQuoteResult buildSeededQuote(String ticker) {
        Optional<OhlcvBar> latestBar = ohlcvRepo.findLatestCloseByTicker(ticker);
        if (latestBar.isPresent()) {
            OhlcvBar bar = latestBar.get();
            return new StockQuoteResult(
                    ticker,
                    bar.getClosePrice().setScale(2, RoundingMode.HALF_UP),
                    bar.getBarDate().toString(),
                    "DEMO",
                    "SEEDED"
            );
        }
        // No seeded data — return zero-price sentinel
        return new StockQuoteResult(ticker, BigDecimal.ZERO.setScale(2), "demo", "DEMO", "SEEDED");
    }

    // ── private nested records ────────────────────────────────────────────────

    private record CachedQuote(StockQuoteResult result, long fetchedAt) {}

    private record FinnhubQuoteResponse(double c, double d, double dp,
                                        double h, double l, double o,
                                        double pc, long t) {}
}
