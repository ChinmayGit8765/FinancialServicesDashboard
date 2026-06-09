package com.quantlens.ai.api;

/**
 * A single 10-K chunk citation returned alongside a chat answer.
 *
 * <p>Citations are populated from {@code QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS} in live mode.
 * In demo mode, citations come from the authored seed content in {@code ai_seed_content}
 * (DemoModeAdvisor short-circuits before retrieval runs).
 *
 * @param ticker   ticker symbol from chunk metadata (e.g. "AAPL")
 * @param section  filing section (e.g. "Risk Factors", "MD&A")
 * @param source   filing reference (e.g. "AAPL 10-K FY2023")
 * @param excerpt  first ~200 chars of the retrieved chunk text
 */
public record CitationDto(
        String ticker,
        String section,
        String source,
        String excerpt
) {}
