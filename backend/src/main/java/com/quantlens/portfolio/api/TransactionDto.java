package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for one transaction entry in {@code GET /api/portfolio/transactions}.
 * <p>
 * The list is most-recent-first when paginated. {@code runningCostBasis} is
 * computed by the service scanning all transactions chronologically ascending
 * (BUY → SELL order) and applying the running average-cost accounting formula;
 * it is NOT stored in the database.
 * <p>
 * {@link LocalDate} serialises as an ISO-8601 string (e.g. {@code "2022-09-12"})
 * via Spring Boot's auto-registered {@code JavaTimeModule}.
 *
 * @param txDate           trade date
 * @param txType           "BUY" or "SELL"
 * @param ticker           security ticker symbol
 * @param quantity         shares traded — scale 4 (NUMERIC(18,4))
 * @param price            price per share at trade time — scale 6
 * @param tradeValue       qty × price — scale 2 (convenience field for Phase 3 display)
 * @param runningCostBasis cumulative average cost per share after applying this trade — scale 6
 */
public record TransactionDto(
        LocalDate txDate,
        String txType,
        String ticker,
        BigDecimal quantity,
        BigDecimal price,
        BigDecimal tradeValue,
        BigDecimal runningCostBasis
) {}
