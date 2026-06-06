package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link FactorReturn}.
 */
public interface FactorReturnRepository extends JpaRepository<FactorReturn, Long> {
}
