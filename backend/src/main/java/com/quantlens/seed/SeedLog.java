package com.quantlens.seed;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * Idempotence guard for {@link SeedRunner}.
 * <p>
 * A single row with {@code id="v1"} is written at the END of the seeder's
 * {@code @Transactional} run, with {@code completed=true} and a timestamp.
 * On restart, {@code SeedRunner} checks this flag and no-ops if {@code true},
 * preventing any data duplication even if the context is restarted.
 */
@Entity
@Table(name = "seed_log")
public class SeedLog {

    @Id
    @Column(length = 50)
    private String id;

    @Column(nullable = false)
    private boolean completed = false;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    // ── constructors ──────────────────────────────────────────────────────────

    protected SeedLog() {
    }

    public SeedLog(String id) {
        this.id = id;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public String getId() { return id; }

    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }

    public LocalDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(LocalDateTime completedAt) { this.completedAt = completedAt; }
}
