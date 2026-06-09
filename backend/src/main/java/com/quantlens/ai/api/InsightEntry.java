package com.quantlens.ai.api;

/**
 * A labeled numeric value for a structured insight entry.
 *
 * <p>Used as elements of {@link StructuredInsightRecord#series()}.
 * {@code Double} (boxed) is used so that Jackson can assign {@code null} from the LLM
 * without throwing {@code MismatchedInputException}. The compact constructor coerces
 * {@code null} and non-finite values (NaN, Infinity) to {@code 0.0} so the frontend
 * chart never receives an invalid number (WR-04).
 *
 * <p>BeanOutputConverter's auto-generated JSON schema still maps to a JSON {@code number}
 * for a boxed {@code Double} field — no custom deserialization required.
 *
 * @param label  sector or category label (e.g. "Technology")
 * @param value  allocation percentage (0–100); null/NaN/Infinity coerced to 0.0
 */
public record InsightEntry(
        String label,
        Double value
) {
    // WR-04: compact constructor — coerce null/NaN/Infinity to 0.0 so the chart
    // never receives an invalid value even if the LLM returns {"value":null}.
    public InsightEntry {
        if (value == null || !Double.isFinite(value)) {
            value = 0.0;
        }
    }
}
