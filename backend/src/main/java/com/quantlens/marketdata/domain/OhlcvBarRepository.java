package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link OhlcvBar}.
 */
public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {

    /**
     * Returns the latest (most recent) OHLCV bar per security for a given set of security IDs.
     * <p>
     * Used by PortfolioService for current-price lookups in the holdings endpoint.
     * The correlated MAX subquery selects the most recent {@code barDate} for each
     * security group, returning exactly one bar per security ID. This avoids the
     * N+1 problem of fetching the latest bar with a separate query per security.
     *
     * @param securityIds the list of security primary keys to look up
     * @return one bar per security ID (the bar with the latest barDate for each)
     */
    @Query("""
            SELECT b FROM OhlcvBar b
            WHERE b.security.id IN :securityIds
              AND b.barDate = (
                  SELECT MAX(b2.barDate) FROM OhlcvBar b2
                  WHERE b2.security.id = b.security.id
              )
            """)
    List<OhlcvBar> findLatestBarBySecurityIds(@Param("securityIds") List<Long> securityIds);

    /**
     * Returns all OHLCV bars for a set of securities, ordered for time-series iteration.
     * <p>
     * Used by PortfolioService for equity-curve computation (constant-current-holdings)
     * and benchmark comparison. Returns 504 × N rows in a single JDBC round-trip
     * (e.g. 504 × 7 = 3 528 rows for alice's portfolio — well within memory).
     * Ordering by {@code security.id ASC, barDate ASC} allows the service to group
     * and iterate by security in one pass without sorting in Java.
     *
     * @param securityIds the list of security primary keys
     * @return all bars for the given securities, ordered by security ID then date ascending
     */
    @Query("""
            SELECT b FROM OhlcvBar b
            WHERE b.security.id IN :securityIds
            ORDER BY b.security.id ASC, b.barDate ASC
            """)
    List<OhlcvBar> findAllBySecurityIdsOrdered(@Param("securityIds") List<Long> securityIds);
}
