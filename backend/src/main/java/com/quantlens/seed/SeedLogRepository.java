package com.quantlens.seed;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for the {@link SeedLog} idempotence guard.
 */
public interface SeedLogRepository extends JpaRepository<SeedLog, String> {
}
