---
phase: "03-frontend-scaffold"
plan: "03"
subsystem: "frontend/charts"
tags: ["echarts", "vue3", "pinia", "charts", "visualization"]
dependency_graph:
  requires: ["03-01", "03-02"]
  provides: ["PnlChart", "BenchmarkChart", "AllocationChart"]
  affects: ["03-05-DashboardView"]
tech_stack:
  added: []
  patterns:
    - "ECharts xAxis.type:category with ISO date string arrays (not type:time)"
    - "T00:00:00 suffix on Date construction for UTC-safe local display"
    - "THEME_KEY provide/inject — no theme prop on v-chart"
    - "figure/figcaption accessibility scaffold with .sr-only"
    - "computed<EChartsOption> returning {} for empty/null data"
    - "chartType ref switching between donut and treemap computed options"
key_files:
  created:
    - "frontend/src/components/PnlChart.vue"
    - "frontend/src/components/BenchmarkChart.vue"
    - "frontend/src/components/AllocationChart.vue"
  modified: []
decisions:
  - "xAxis.type:category over type:time — performance + predictable rendering for 504-point fixed datasets"
  - "markLine at y=100 for BenchmarkChart baseline rule — avoids splitLine complexity"
  - "weightMap lookup in treemapOption label formatter — avoids array.find on every render tick"
  - "graphic text element for AllocationChart center label — avoids DOM injection, pure ECharts"
metrics:
  duration: "~8 minutes"
  completed_date: "2026-06-07"
  tasks_completed: 3
  files_changed: 3
---

# Phase 03 Plan 03: ECharts Chart Components Summary

Three ECharts data-visualization components built: PnlChart (equity-curve line + gradient area fill), BenchmarkChart (dual rebased-to-100 lines), AllocationChart (donut with treemap toggle).

## What Was Built

### Task 1 — PnlChart.vue (commit 16d5fc4)
Equity-curve line chart consuming `PortfolioPnlDto.equityCurve: DateValueDto[]`. Single smooth accent line (#0ea5e9, width 2) with linear gradient area fill from `rgba(14,165,233,0.25)` to `rgba(14,165,233,0)`. xAxis uses `type:'category'` with the ISO date string array; both axisLabel and tooltip formatters append `'T00:00:00'` to all Date constructions to prevent UTC off-by-one. All four states present: loading skeleton (320px shimmer), error ("Failed to load P&L data." + Retry button), empty ("No P&L data available."), populated v-chart. figure/figcaption accessibility scaffold with `.sr-only` figcaption. No theme prop; no v-html.

### Task 2 — BenchmarkChart.vue (commit 94a6fa1)
Dual-series benchmark comparison chart consuming `BenchmarkComparisonDto`. Portfolio series: #0ea5e9 width 2 solid. S&P 500 series: #94a3b8 width 1.5 `type:'dashed'`. Legend top-right. markLine at y=100 as a faint baseline rule using `--color-border` (#334155). xAxis `type:'category'` with dates array; UTC-safe date parsing in tooltip formatter. Same four-state scaffold as PnlChart. Empty copy "No benchmark data available." per UI-SPEC.

### Task 3 — AllocationChart.vue (commit 53a5d59)
Donut/treemap allocation chart consuming `AllocationSliceDto[]`. `chartType` ref switches between two computed options. Donut: pie radius `['40%','68%']` center `['50%','55%']`; center total market value via ECharts `graphic` text element; color palette from `quantlens-dark` theme `color[]` (not hardcoded). Treemap: `type:'treemap'` with `Map`-based weight lookup in label formatter for O(1) access; `itemStyle.borderWidth:2 borderColor:'#0b0f1a'`. Segmented toggle `role="group"` with `aria-pressed` state. Height 280px. All four states present; empty copy "No allocation data. Add holdings to see your sector breakdown."

## Verification Results

- `npm run build`: exits 0 (vue-tsc type-check + vite bundle — 3/3 tasks)
- `npm run test`: 18/18 tests pass (2 test files — no regressions, no ResizeObserver errors)
- All three files contain xAxis `type:'category'` and `T00:00:00` guards (PnlChart, BenchmarkChart)
- AllocationChart contains both a `pie` series (donut) and a `treemap` series, switched by `chartType` ref
- No `theme=` prop on any v-chart; no `v-html` anywhere
- All colors via `var(--token)` scoped CSS; only documented gradient `rgba()` values hardcoded

## Deviations from Plan

None — plan executed exactly as written.

## Security: T-03-07 / T-03-08 Compliance

- **T-03-07 (XSS):** Tooltip and axis formatters build strings from typed `number` values and ISO date strings only. No API-sourced strings are injected; no `v-html` used anywhere in Phase 3 chart components.
- **T-03-08 (Info disclosure):** All error states show only the static copy from UI-SPEC ("Failed to load X data." / "Retry?"). The raw error object/message is never rendered.

## Known Stubs

None. Components are fully wired to their prop types (`PortfolioPnlDto`, `BenchmarkComparisonDto`, `AllocationSliceDto[]`). Data connection to the portfolio store happens in Plan 05 (DashboardView) — by design, not a stub.

## Threat Flags

No new threat surface introduced. All three components are pure presentational (props-in, events-out). No network calls, no route changes, no new endpoints.

## Self-Check: PASSED

- frontend/src/components/PnlChart.vue: FOUND
- frontend/src/components/BenchmarkChart.vue: FOUND
- frontend/src/components/AllocationChart.vue: FOUND
- Commit 16d5fc4: FOUND (feat(03-03): PnlChart...)
- Commit 94a6fa1: FOUND (feat(03-03): BenchmarkChart...)
- Commit 53a5d59: FOUND (feat(03-03): AllocationChart...)
