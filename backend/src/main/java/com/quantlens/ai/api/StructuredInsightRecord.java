package com.quantlens.ai.api;

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
 * @param series   list of labeled values (never null; may be empty)
 */
public record StructuredInsightRecord(
        String title,
        String subtitle,
        List<InsightEntry> series
) {}
