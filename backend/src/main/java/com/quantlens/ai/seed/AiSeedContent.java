package com.quantlens.ai.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code ai_seed_content} table.
 *
 * <p>Stores authored AI fixture content keyed by ({@code type}, {@code subjectId}).
 * <ul>
 *   <li>{@code type} — content category: {@code "EXPLAIN_POSITION"} or {@code "DAILY_COMMENTARY"}</li>
 *   <li>{@code subjectId} — subject key: ticker symbol (e.g. {@code "AAPL"}) or persona key
 *       (e.g. {@code "GROWTH"})</li>
 *   <li>{@code content} — the authored narrative text returned by the DemoModeAdvisor</li>
 * </ul>
 *
 * <p>No {@code @ToString} — prevents accidental content logging.
 * No Lombok — follows Position.java style (T-06-01).
 */
@Entity
@Table(name = "ai_seed_content")
public class AiSeedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(name = "subject_id", nullable = false, length = 32)
    private String subjectId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // ── constructors ──────────────────────────────────────────────────────────

    protected AiSeedContent() {
    }

    /**
     * Creates a new seed content row.
     *
     * @param type      content category (e.g. {@code "EXPLAIN_POSITION"})
     * @param subjectId subject key (ticker or persona)
     * @param content   the authored narrative text
     */
    public AiSeedContent(String type, String subjectId, String content) {
        this.type = type;
        this.subjectId = subjectId;
        this.content = content;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getType() { return type; }

    public String getSubjectId() { return subjectId; }

    public String getContent() { return content; }

    /**
     * Updates the authored content for an existing seed row.
     *
     * <p>Used by {@link AiSeedRunner} ai-v2 to overwrite persona-specific narratives
     * from ai-v1 with persona-neutral content (WR-06). The (type, subjectId) key is
     * immutable; only the content text changes.
     *
     * @param content the new authored narrative text
     */
    public void setContent(String content) {
        this.content = content;
    }
}
