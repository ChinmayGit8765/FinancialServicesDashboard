package com.quantlens.portfolio.api;

import java.math.BigDecimal;

/**
 * DTO for one allocation slice in {@code GET /api/portfolio/allocation}.
 * <p>
 * ECharts pie chart expects {@code {name, value}} pairs. The {@code label}
 * field maps to ECharts {@code name}; Phase 3 may use either {@code weight}
 * or {@code marketValue} for the {@code value} axis depending on whether
 * the chart is percentage-weighted or dollar-weighted.
 *
 * @param label       sector name (e.g. "Technology") — maps to ECharts {@code name}
 * @param weight      fraction of total portfolio by market value — scale 6 (0.000000..1.000000)
 * @param marketValue total market value in this sector — scale 2 (display money)
 */
public record AllocationSliceDto(String label, BigDecimal weight, BigDecimal marketValue) {}
