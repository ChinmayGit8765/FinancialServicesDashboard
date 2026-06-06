package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link Transaction}.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
}
