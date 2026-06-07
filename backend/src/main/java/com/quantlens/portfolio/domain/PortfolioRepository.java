package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link Portfolio}.
 */
public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

    List<Portfolio> findByUserId(Long userId);

    /**
     * Resolves the portfolio ID for a given username in a single JPQL query.
     * <p>
     * WR-01 fix: replaces the two-round-trip lookup
     * ({@code appUserRepository.findByUsername} + {@code portfolioRepository.findByUserId})
     * with a single correlated subquery so resolvePortfolioId uses one DB call instead of two.
     * <p>
     * WR-06 fix: returning {@code Optional.empty()} for both "user not found" and
     * "user has no portfolio" collapses both into a uniform 401 response in the controller,
     * preventing an attacker from distinguishing between a non-existent username and a
     * valid user without a portfolio.
     *
     * @param username the authenticated principal's username
     * @return the portfolio ID, or empty if the user or portfolio does not exist
     */
    @Query("SELECT p.id FROM Portfolio p WHERE p.user.username = :username")
    Optional<Long> findPortfolioIdByUsername(@Param("username") String username);
}
