package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link OhlcvBar}.
 */
public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {
}
