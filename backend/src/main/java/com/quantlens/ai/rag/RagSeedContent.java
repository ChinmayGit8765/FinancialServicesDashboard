package com.quantlens.ai.rag;

import org.springframework.ai.document.Document;

import java.util.Map;

/**
 * Immutable data holder for a 10-K corpus chunk before seeding into {@code vector_store}.
 *
 * <p>NOT a JPA entity — chunks are stored via {@code VectorStore.add(List<Document>)},
 * not via JPA. Metadata fields (ticker, section, source, year) are surfaced as
 * {@link com.quantlens.ai.api.CitationDto} fields in chat responses.
 *
 * @param text    chunk prose (200–500 authored words — no PDF parsing needed for v1)
 * @param ticker  ticker symbol (e.g. "AAPL")
 * @param section filing section (e.g. "Risk Factors", "MD&A", "Business Overview")
 * @param source  filing reference (e.g. "AAPL 10-K FY2023")
 * @param year    fiscal year string (e.g. "2023")
 */
public record RagSeedContent(
        String text,
        String ticker,
        String section,
        String source,
        String year
) {
    /**
     * Converts this chunk into a Spring AI {@link Document} with metadata attached.
     * The metadata keys (ticker, section, source, year) map directly to
     * {@link com.quantlens.ai.api.CitationDto} fields.
     *
     * @return a {@link Document} ready for {@code VectorStore.add()}
     */
    public Document toDocument() {
        return new Document(
                text,
                Map.of(
                        "ticker",  ticker,
                        "section", section,
                        "source",  source,
                        "year",    year
                )
        );
    }
}
