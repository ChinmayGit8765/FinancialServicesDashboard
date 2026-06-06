package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Position}.
 */
public interface PositionRepository extends JpaRepository<Position, Long> {
}
