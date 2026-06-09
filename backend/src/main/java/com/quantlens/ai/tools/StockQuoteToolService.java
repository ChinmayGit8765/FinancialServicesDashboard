package com.quantlens.ai.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Spring AI {@link Tool}-annotated service that exposes a stock-quote lookup to the LLM.
 *
 * <h2>Design</h2>
 * <ul>
 *   <li>No demo/live branch inside this method body — {@code DemoModeAdvisor} fires at
 *       {@code HIGHEST_PRECEDENCE} and short-circuits the chain before the ChatModel invokes
 *       any tool. The {@code @Tool} method runs ONLY on the live path.</li>
 *   <li>All HTTP, caching, fallback logic is in {@link FinnhubQuoteClient}.</li>
 * </ul>
 *
 * <h2>Registration</h2>
 * Registered on the live {@link org.springframework.ai.chat.client.ChatClient} via
 * {@link org.springframework.ai.tool.method.MethodToolCallbackProvider} (NOT {@code defaultTools()}
 * which has a known CGLIB detection bug in Spring AI 1.1.x, GitHub #5134).
 */
@Component
public class StockQuoteToolService {

    private final FinnhubQuoteClient finnhubClient;

    public StockQuoteToolService(FinnhubQuoteClient finnhubClient) {
        this.finnhubClient = finnhubClient;
    }

    /**
     * Returns the current or most recent stock price for the given ticker symbol.
     *
     * <p>In live mode: calls Finnhub (with a 15-min TTL cache) and returns the live price
     * with after-hours/pre-market labeling. In demo mode the DemoModeAdvisor short-circuits
     * before this method is invoked (the LLM never runs in demo mode).
     *
     * @param ticker stock ticker symbol (e.g. "AAPL", "MSFT")
     * @return a typed {@link StockQuoteResult}
     */
    @Tool(description = "Get the current or most recent stock price for a ticker symbol. "
            + "Returns price, change, market state (regular/after-hours/closed), and timestamp.")
    public StockQuoteResult getStockQuote(
            @ToolParam(description = "Stock ticker symbol, e.g. AAPL, MSFT") String ticker) {
        // IN-01: canonicalize to uppercase before lookup so "aapl" and "AAPL" produce
        // the same Finnhub request and share the same cache entry.
        return finnhubClient.getQuote(ticker != null ? ticker.toUpperCase(Locale.ROOT) : ticker);
    }
}
