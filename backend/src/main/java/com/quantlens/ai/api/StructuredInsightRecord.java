package com.quantlens.ai.api;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import java.util.List;

/**
 * Response DTO for {@code GET /api/ai/structured}.
 *
 * <p>In demo mode the record is deserialized from authored seed JSON (type STRUCTURED_INSIGHT)
 * via {@code ObjectMapper.readValue}. In live mode it is populated by
 * {@code BeanOutputConverter} via {@code .entity(StructuredInsightRecord.class)}.
 *
 * <p>Wrapping {@code series} under a named field avoids the OpenAI top-level-array
 * restriction — {@code BeanOutputConverter} generates a JSON schema from this record;
 * a top-level {@code List} would violate the schema requirement.
 *
 * @param title    chart headline (never blank in valid seed content)
 * @param subtitle optional subtitle (nullable; may be null in live mode)
 * @param series   list of labeled values (never null; may be empty — WR-03: null coerced to
 *                 empty list via {@code @JsonSetter(nulls = Nulls.AS_EMPTY)} so the frontend
 *                 chart iterator never receives null)
 */
public record StructuredInsightRecord(
        String title,
        String subtitle,
        // WR-03: coerce null series (from LLM omitting the field or seed JSON with "series":null)
        // to an empty list rather than propagating null to the frontend chart renderer.
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<InsightEntry> series
) {}
