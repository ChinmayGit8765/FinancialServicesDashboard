package com.quantlens.ai.rag;

import org.springframework.ai.document.Document;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
     * <p>CR-06: The document ID is a STABLE, deterministic UUID derived from
     * {@code ticker + "-" + section} using {@link UUID#nameUUIDFromBytes(byte[])}
     * (type-3 UUID, name-based). This ensures that re-running {@code VectorStore.add()}
     * after a crash-and-restart produces the same UUID IDs and does not insert duplicate
     * embedding rows — the seed is idempotent at the document-ID level.
     *
     * <p>Without a stable ID, {@code new Document(text, metadata)} auto-generates a fresh
     * UUID on every JVM start, causing duplicate rows in pgvector when the {@code seed_log}
     * guard fails between {@code vectorStore.add()} and {@code seedLogRepository.save()}.
     *
     * <p>The UUID is derived from the lower-cased {@code ticker-section} key so it is
     * both valid UUID format (required by PgVectorStore) and deterministic.
     *
     * @return a {@link Document} with a stable UUID ID, ready for {@code VectorStore.add()}
     */
    public Document toDocument() {
        // CR-06: stable deterministic UUID derived from ticker+section
        // UUID.nameUUIDFromBytes generates a type-3 (name-based MD5) UUID — always the same
        // for the same input bytes, always a valid UUID string (required by PgVectorStore).
        String nameKey = ticker.toLowerCase(Locale.ROOT)
                + "-"
                + section.toLowerCase(Locale.ROOT).replace(" ", "-").replace("&", "and");
        String stableId = UUID.nameUUIDFromBytes(nameKey.getBytes(StandardCharsets.UTF_8)).toString();
        return new Document(
                stableId,
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
