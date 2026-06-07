package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Security}.
 */
public interface SecurityRepository extends JpaRepository<Security, Long> {

    Optional<Security> findByTicker(String ticker);

    List<Security> findByBenchmarkTrue();
}
