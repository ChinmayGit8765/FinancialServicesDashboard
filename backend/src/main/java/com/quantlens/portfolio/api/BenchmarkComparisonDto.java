package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO returned by {@code GET /api/portfolio/benchmark}.
 * <p>
 * Parallel arrays optimised for an ECharts line chart with two series.
 * Both series are indexed to 100.0000 on {@code dates[0]} (the first shared
 * trading day, 2022-09-12). Terminology: "cumulative simple return indexed
 * to 100" / "normalised price series".
 * <p>
 * {@code dates} uses {@link String} rather than {@code LocalDate} because
 * the ECharts xAxis {@code data} array expects string labels; the service
 * calls {@code LocalDate.toString()} (ISO-8601) before adding to the list.
 *
 * @param dates           ISO-8601 date strings in ascending order (e.g. "2022-09-12");
 *                        NOT {@code List<LocalDate>} — ECharts xAxis expects string labels
 * @param portfolioSeries portfolio value indexed to 100 on day 0 — scale 4
 * @param benchmarkSeries SPX500 proxy close indexed to 100 on day 0 — scale 4
 */
public record BenchmarkComparisonDto(
        List<String> dates,
        List<BigDecimal> portfolioSeries,
        List<BigDecimal> benchmarkSeries
) {}
