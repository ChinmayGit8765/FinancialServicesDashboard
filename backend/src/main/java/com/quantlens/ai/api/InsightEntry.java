package com.quantlens.ai.api;

/**
 * A labeled numeric value for a structured insight entry.
 *
 * <p>Used as elements of {@link StructuredInsightRecord#series()}.
 * {@code double} is chosen (not {@code BigDecimal}) so that BeanOutputConverter's
 * auto-generated JSON schema maps cleanly to a JSON {@code number} without
 * requiring custom deserialization.
 *
 * @param label  sector or category label (e.g. "Technology")
 * @param value  allocation percentage (0–100)
 */
public record InsightEntry(
        String label,
        double value
) {}
