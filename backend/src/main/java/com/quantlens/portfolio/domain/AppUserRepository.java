package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data repository for {@link AppUser}.
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    /**
     * Returns the first 10 users ordered by id ascending.
     * <p>
     * Used by the public {@code GET /api/auth/personas} endpoint as a server-side
     * size cap — prevents a full table scan on an unauthenticated endpoint when the
     * user table grows beyond the current 3 demo users (WR-03).
     */
    List<AppUser> findTop10ByOrderByIdAsc();
}
