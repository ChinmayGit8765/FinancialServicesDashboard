package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single point on a time-series value curve.
 * <p>
 * {@link LocalDate} serialises as an ISO-8601 string (e.g. {@code "2022-09-12"})
 * via Spring Boot's auto-registered {@code JavaTimeModule} with
 * {@code WRITE_DATES_AS_TIMESTAMPS=false}. No custom serializer required.
 * <p>
 * Reused by {@link PortfolioPnlDto#equityCurve()}.
 *
 * @param date  the trading day
 * @param value the portfolio market value on this day — scale 2 (display money)
 */
public record DateValueDto(LocalDate date, BigDecimal value) {}
