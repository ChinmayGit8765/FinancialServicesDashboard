package com.quantlens.ai.tools;

import java.math.BigDecimal;

/**
 * Typed result returned by the {@code getStockQuote} tool.
 *
 * <p>Spring AI serializes this record via its own ObjectMapper when the LLM invokes the tool.
 * No Jackson annotations are needed.
 *
 * @param ticker      stock ticker symbol (e.g. "AAPL")
 * @param price       current or last-known price (BigDecimal per project money convention)
 * @param asOf        ISO-8601 datetime string representing when the quote was taken
 * @param marketState one of "REGULAR", "AFTER_HOURS", "PRE_MARKET", "CLOSED", or "DEMO"
 * @param source      one of "FINNHUB" (live) or "SEEDED" (demo/fallback)
 */
public record StockQuoteResult(
        String ticker,
        BigDecimal price,
        String asOf,
        String marketState,
        String source
) {}
