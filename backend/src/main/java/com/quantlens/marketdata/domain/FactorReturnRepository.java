package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository for {@link FactorReturn}.
 */
public interface FactorReturnRepository extends JpaRepository<FactorReturn, Long> {

    /**
     * Returns all 504 factor-return rows ordered by {@code factorDate} ascending.
     * <p>
     * Used by {@code FamaFrenchCalculator} and {@code RiskCalculator} to align
     * the daily risk-free rate (RF) and factor series with the OHLCV trading calendar.
     * The seeded data guarantees exactly 504 rows sharing the same date range as the
     * OHLCV bars (SERIES_START=2022-09-12, seed=42).
     *
     * @return factor return rows ordered factorDate ASC; expected size 504
     */
    List<FactorReturn> findAllByOrderByFactorDateAsc();
}

