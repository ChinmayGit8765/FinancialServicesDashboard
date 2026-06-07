package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link Position}.
 */
public interface PositionRepository extends JpaRepository<Position, Long> {

    /**
     * Loads all positions for a portfolio with their {@link com.quantlens.marketdata.domain.Security}
     * eagerly fetched in a single JOIN FETCH query.
     * <p>
     * Prevents the N+1 problem that would occur if {@code position.getSecurity()}
     * were called inside PortfolioService while iterating a lazily-loaded list —
     * each call would trigger a separate {@code SELECT} for the security row.
     * With JOIN FETCH, one query returns both positions and their securities.
     *
     * @param portfolioId the owning portfolio's primary key
     * @return positions with security eagerly populated, in DB natural order
     */
    @Query("SELECT p FROM Position p JOIN FETCH p.security WHERE p.portfolio.id = :portfolioId")
    List<Position> findByPortfolioIdWithSecurity(@Param("portfolioId") Long portfolioId);
}
