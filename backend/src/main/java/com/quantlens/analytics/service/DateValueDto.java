package com.quantlens.analytics.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A single point on a time-series value curve used internally by the analytics module.
 * <p>
 * Mirrors {@code com.quantlens.portfolio.api.DateValueDto} but lives in the analytics
 * module to avoid a cross-module dependency violation: Spring Modulith only permits
 * {@code analytics} to depend on {@code portfolio::domain}, not {@code portfolio::api}.
 *
 * @param date  the trading day
 * @param value the portfolio or security market value on this day — scale 2
 */
record DateValueDto(LocalDate date, BigDecimal value) {}
