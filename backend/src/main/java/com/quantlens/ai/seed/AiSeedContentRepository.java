package com.quantlens.ai.seed;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data repository for {@link AiSeedContent} AI fixture content.
 *
 * <p>The derived query {@link #findByTypeAndSubjectId} looks up authored content
 * by the (type, subject_id) composite key — the same key used in the
 * {@code uq_ai_seed} unique constraint in the Flyway V4 migration.
 */
public interface AiSeedContentRepository extends JpaRepository<AiSeedContent, Long> {

    /**
     * Finds authored seed content by content type and subject identifier.
     *
     * @param type      content category (e.g. {@code "EXPLAIN_POSITION"}, {@code "DAILY_COMMENTARY"})
     * @param subjectId subject key (e.g. {@code "AAPL"}, {@code "GROWTH"})
     * @return the seed content row, or empty if not yet seeded
     */
    Optional<AiSeedContent> findByTypeAndSubjectId(String type, String subjectId);
}
