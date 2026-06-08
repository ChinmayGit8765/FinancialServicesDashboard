---
phase: 04-quant-risk-engine
plan: "04"
subsystem: frontend
tags: [vue, pinia, echarts, risk, analytics, dashboard]
dependency_graph:
  requires: ["04-01", "04-02", "04-03"]
  provides: ["RiskScorecard", "CorrelationHeatmap", "AttributionChart", "PairsTable", "portfolio-store-analytics"]
  affects: ["DashboardView", "portfolio-store"]
tech_stack:
  added: []
  patterns: ["asyncState factory", "props-driven components", "ECharts heatmap/bar", "three-state loading/error/empty"]
key_files:
  created:
    - frontend/src/components/RiskScorecard.vue
    - frontend/src/components/CorrelationHeatmap.vue
    - frontend/src/components/AttributionChart.vue
    - frontend/src/components/PairsTable.vue
  modified:
    - frontend/src/stores/portfolio.ts
    - frontend/src/views/DashboardView.vue
    - frontend/src/__tests__/portfolioStore.test.ts
decisions:
  - "RiskScorecard is props-driven (not store-reading) so tests mount without store context"
  - "formatPercent takes 0-1 fraction; annualizedVolatility passed directly without *100"
  - "portfolioStore.test.ts refreshAll count updated 5->9 to reflect Phase-4 additions"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-08"
  tasks_completed: 2
  files_modified: 7
---

# Phase 4 Plan 04: Frontend Analytics Panels Summary

**One-liner:** Four analytics dashboard panels (RiskScorecard with dual-VaR table, ECharts correlation heatmap, Fama-French attribution bar chart, cointegration PairsTable) wired into DashboardView replacing all Phase-4 SlotPlaceholders, backed by four new Pinia asyncState resources.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Extend portfolio store + RiskScorecard + PairsTable | b36a6d8 | portfolio.ts, RiskScorecard.vue, PairsTable.vue |
| 2 | CorrelationHeatmap + AttributionChart + DashboardView wiring | 90604fc | CorrelationHeatmap.vue, AttributionChart.vue, DashboardView.vue, portfolioStore.test.ts |

## What Was Built

### Store Extension (portfolio.ts)
- Added `risk`, `correlation`, `attribution`, `pairs` as `asyncState<T>` reactive state entries
- Added `fetchRisk`, `fetchCorrelation`, `fetchAttribution`, `fetchPairs` — exact fetchBenchmark pattern with version-guard (T-04-09: race guard on all four)
- All four added to `refreshAll` Promise.allSettled and `$reset`
- Return block exposes all 8 new symbols

### RiskScorecard.vue
- Props-driven: `{ risk, loading, error }` + `emit('retry')`
- Loading: four shimmer skeletons
- Populated: four `<KpiCard>` instances (Sharpe, Ann. Volatility, Max Drawdown, Beta) + VaR comparison table (Method / Confidence / Horizon / Amount / %)
- Error: `role="alert"` + `.retry-btn` with static copy (T-04-08)

### PairsTable.vue
- Props-driven: `{ pairs, loading, error }` + `emit('retry')`
- Table: Pair | p-value | Hedge β | Z-score | Signal badge
- Signal badges: `.signal-buy` (LONG_Y_SHORT_X), `.signal-sell` (SHORT_Y_LONG_X), `.signal-neutral`
- Three-state: skeleton/error/empty/table

### CorrelationHeatmap.vue
- ECharts heatmap with `data: [xIdx, yIdx, value]` layout
- `visualMap` min -1 / max 1 / colors `['#ef4444','#f8fafc','#3b82f6']` (red→white→blue)
- yAxis reversed so matrix[0][0] diagonal maps to top-left
- No `theme` prop (THEME_KEY provided globally in App.vue)
- Three-state: skeleton/error/empty/chart

### AttributionChart.vue
- ECharts bar chart: Alpha / Mkt-RF / SMB / HML labels
- Values: `dto.{alpha,contribMkt,contribSmb,contribHml}Annualized * 100`
- Per-bar color: `#22c55e` (≥0) or `#ef4444` (<0)
- Labels at `position: 'top'` showing `{value.toFixed(2)}%`
- Three-state: skeleton/error/empty/chart

### DashboardView.vue
- Imports all four components
- KPI strip: Phase-4 placeholder `<div class="kpi-placeholder">` wrappers replaced with real `<KpiCard>` for Sharpe Ratio and Ann. Volatility bound to `portfolioStore.risk.data`
- Row 3 col-6: `SlotPlaceholder label="Risk Scorecard — Phase 4"` replaced with `<RiskScorecard>`
- Row 4 col-12: `SlotPlaceholder label="Correlation Heatmap — Phase 4"` replaced with `<CorrelationHeatmap>`
- New rows (4b): `<AttributionChart>` col-6 + `<PairsTable>` col-6 inserted between correlation row and Monte Carlo slot
- Phase-5 (Monte Carlo) and Phase-6 (AI) SlotPlaceholders remain untouched

## Test Results

| Test suite | Before | After |
|------------|--------|-------|
| RiskScorecard.test.ts | RED (file missing) | 3/3 GREEN |
| PairsTable.test.ts | RED (file missing) | 4/4 GREEN |
| CorrelationHeatmap.test.ts | RED (file missing) | 3/3 GREEN |
| AttributionChart.test.ts | RED (file missing) | 3/3 GREEN |
| portfolioStore.test.ts | 5/5 GREEN | 5/5 GREEN |
| All other Phase-3 suites | 34/34 GREEN | 34/34 GREEN |
| **Total** | 39/44 | **48/48** |

`npm run build`: exit 0 (chunk size advisory pre-exists, not a new error)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] portfolioStore.test.ts hardcoded call counts broke with 9-fetch refreshAll**
- **Found during:** Task 2 test run
- **Issue:** `refreshAll calls axios.get exactly 5 times` test expected 5 calls; the race-guard test used call indices 2/7 (5-call batch math). Expanding refreshAll to 9 calls broke both.
- **Fix:** Updated count assertion to 9; updated race-guard pnl indices from call 2/7 to call 2/11 (9-call batch math: batch B pnl = 9+2=11)
- **Files modified:** `frontend/src/__tests__/portfolioStore.test.ts`
- **Commit:** 90604fc

## Threat Mitigations Applied

| Threat ID | Applied? | Notes |
|-----------|----------|-------|
| T-04-01 (IDOR) | Inherent | Store fetches `/api/portfolio/<metric>` with no id param; backend derives from principal |
| T-04-08 (Info Disclosure) | Yes | All error states show static copy only; raw error object never reaches DOM |
| T-04-09 (Stale persona race) | Yes | All four new fetches pass version to guard; `refreshVersion` incremented in refreshAll |

## Manual Visual Checklist (Deferred — checkpoint:human-verify)

The automated gates (build + test) are green. The following items require visual verification under `docker compose up`:

- [ ] KPI strip shows real Sharpe Ratio and Ann. Volatility values (no "Phase 4" placeholders)
- [ ] Risk Scorecard panel: Sharpe, Ann. Volatility, Max Drawdown, Beta KpiCards + VaR table with Method/Confidence/Horizon/Amount/% columns, positive amounts
- [ ] Correlation Heatmap: blue→white→red scale (-1..+1), tickers on both axes, cell values shown
- [ ] Attribution bar chart: Alpha / Mkt-RF / SMB / HML bars, green/red per sign, top labels
- [ ] Pairs table: p-value, hedge β, Z-score, signal badge present for each pair
- [ ] Persona switch (Alice → Bob → Charlie): all four panels re-scope without full page reload, no console errors

## Known Stubs

None — all four panels are wired to live store state. `'—'` is intentional fallback when data is null (not yet loaded).

## Self-Check: PASSED

- FOUND: frontend/src/components/RiskScorecard.vue
- FOUND: frontend/src/components/CorrelationHeatmap.vue
- FOUND: frontend/src/components/AttributionChart.vue
- FOUND: frontend/src/components/PairsTable.vue
- FOUND: commit b36a6d8 (store + RiskScorecard + PairsTable)
- FOUND: commit 90604fc (CorrelationHeatmap + AttributionChart + DashboardView wiring)
- npm run test: 48/48 green
- npm run build: exit 0
