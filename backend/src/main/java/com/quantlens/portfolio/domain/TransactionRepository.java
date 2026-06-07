package com.quantlens.portfolio.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Spring Data repository for {@link Transaction}.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Paginated transaction history for a portfolio, with the associated
     * {@link com.quantlens.marketdata.domain.Security} eagerly loaded.
     * <p>
     * Returns most-recent-first ({@code txDate DESC}); {@code id DESC} provides
     * a stable tiebreaker for same-day transactions (consistent with SeedRunner
     * insertion order). JOIN FETCH is safe for pagination here because
     * {@code Transaction → Security} is a single-valued association (not a
     * collection join).
     * <p>
     * WR-04: An explicit {@code countQuery} is provided to prevent Hibernate 6.x from
     * auto-deriving a count query from the JOIN FETCH + ORDER BY clause, which can
     * trigger HHH90003004 warnings and produce a redundant join in some Hibernate 6.2+ builds.
     *
     * @param portfolioId the owning portfolio's primary key
     * @param pageable    Spring Data page/sort descriptor
     * @return a page of transactions with securities eagerly populated
     */
    @Query(value = """
            SELECT t FROM Transaction t
            JOIN FETCH t.security
            WHERE t.portfolio.id = :portfolioId
            ORDER BY t.txDate DESC, t.id DESC
            """,
           countQuery = """
            SELECT count(t) FROM Transaction t
            WHERE t.portfolio.id = :portfolioId
            """)
    Page<Transaction> findByPortfolioIdWithSecurity(
            @Param("portfolioId") Long portfolioId,
            Pageable pageable);

    /**
     * All transactions for a portfolio in chronological ascending order.
     * <p>
     * Used by PortfolioService to compute {@code runningCostBasis} — the
     * running average-cost accounting scan must process transactions
     * BUY-first, SELL-second in date order. A separate query (not the
     * paginated one above) is used because the cost-basis scan requires
     * ALL transactions, not just one page.
     *
     * @param portfolioId the owning portfolio's primary key
     * @return all transactions with securities, oldest-first ({@code txDate ASC, id ASC})
     */
    @Query("""
            SELECT t FROM Transaction t
            JOIN FETCH t.security
            WHERE t.portfolio.id = :portfolioId
            ORDER BY t.txDate ASC, t.id ASC
            """)
    List<Transaction> findByPortfolioIdChronological(@Param("portfolioId") Long portfolioId);
}
