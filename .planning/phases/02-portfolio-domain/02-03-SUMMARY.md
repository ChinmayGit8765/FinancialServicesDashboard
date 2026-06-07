---
phase: 02-portfolio-domain
plan: 03
subsystem: backend/portfolio
tags: [service, controller, equity-curve, pnl, benchmark, bigdecimal, constant-holdings, rebasing, tdd-green]
dependency_graph:
  requires: [02-02]
  provides: [PortfolioService.getPortfolioPnl, PortfolioService.getBenchmarkComparison, /pnl, /benchmark]
  affects: [02-04]
tech_stack:
  added: []
  patterns:
    - "Constant-current-holdings equity curve: NavigableMap<LocalDate, BigDecimal> grouping via TreeMap; common-date-range intersection via max(firstKey) / min(lastKey)"
    - "Daily change uses curve[last] - curve[last-1] (off-by-one Pitfall 3 avoided); NEVER first-to-last"
    - "Benchmark rebasing: value(i) / value(0) * 100, scale 4 HALF_UP — both series independently rebased to 100.0000 on day 0"
    - "SecurityRepository.findByBenchmarkTrue() for SPX500 lookup; guard for empty/multiple results"
    - "Public static helpers (computeTotalUnrealizedGainAbs/Pct, computeDailyChange, rebaseToIndex) for unit-test access across packages"
    - "BenchmarkComparisonDto.dates uses List<String> via LocalDate.toString() for ECharts xAxis binding"
key_files:
  modified:
    - backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java
    - backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java
    - backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java
    - backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java
decisions:
  - "SecurityRepository added to PortfolioService constructor (not a separate service) — benchmark is a portfolio computation concern, not a separate domain"
  - "buildEquityCurve is private (not public static) — it depends on OhlcvBarRepository injection; only the pure-math steps (computeDailyChange, rebaseToIndex, computeTotalUnrealizedGainAbs/Pct) are public static helpers accessible to unit tests"
  - "rebaseToIndex is public static to allow unit-test verification of the rebasing invariant (both series start at 100.0000) without Spring context"
  - "SPX500 guard throws IllegalStateException on empty/multiple — fail-fast at startup rather than returning silent wrong data"
  - "getBenchmark_datesSortedAscending extended with portfolioSeries[0]/benchmarkSeries[0] == 100.0000 assertions per plan requirement"
metrics:
  duration: "~20 minutes"
  completed: "2026-06-07"
  tasks_completed: 2
  tasks_total: 2
  files_modified: 4
---

# Phase 02 Plan 03: PortfolioService equity-curve P&L + benchmark rebasing; PortfolioController /pnl + /benchmark Summary

PortfolioService constant-current-holdings equity curve (504 entries, 2022-09-12 start), daily-change computation, and dual-series benchmark rebasing to 100.0000 on day 0 — turning 3 unit tests and 4 integration tests GREEN while keeping all Phase 01–02 tests passing.

## What Was Built

### Task 1 — PortfolioService extension (commit `5e1fa02`)

**Constructor update:** Added `SecurityRepository securityRepository` parameter (beside existing `PositionRepository` and `OhlcvBarRepository`).

**Private helper `buildEquityCurve(List<Position> positions)`:**

The constant-current-holdings equity curve implementation (RESEARCH.md Pattern 2). Carries the verbatim model-assumption Javadoc:

> "This curve values the portfolio's CURRENT (final) holdings across every historical trading day in the seeded window, as if those shares were held throughout. This is a dashboard equity curve — it shows how the current portfolio WOULD have performed, not how it DID perform."

Algorithm:
1. Calls `findAllBySecurityIdsOrdered(securityIds)` — single JDBC round-trip returning 504 × N rows ordered by `(securityId ASC, barDate ASC)`
2. Groups into `Map<Long, NavigableMap<LocalDate, BigDecimal>>` (TreeMap per security for date-ordered access)
3. Finds common date range: `firstDate = max(per-security firstKey)`, `lastDate = min(per-security lastKey)` — in practice the full 504-day range since all securities share GbmGenerator's calendar
4. For each date: `dayValue = Σ position.qty × close(date)`, scale 2 HALF_UP
5. Returns `List<DateValueDto>` sorted ascending by date (504 entries, starting 2022-09-12)

**`getPortfolioPnl(Long portfolioId)`:**
- Loads positions (JOIN FETCH — N+1-safe), builds equity curve via private helper
- `totalMarketValue` = `curve.get(last).value()`
- `totalCostBasis` = Σ(qty × avgCostBasis, scale 2)
- `totalUnrealizedGainAbs/Pct` via public static helpers (guards zero denominator)
- `dailyChangeAbs/Pct` = `curve[last] − curve[last-1]` via `computeDailyChange` (Pitfall 3 avoided — NOT first-to-last)
- Returns `PortfolioPnlDto` with full 504-entry equity curve

**`getBenchmarkComparison(Long portfolioId)`:**
- Builds portfolio equity curve (reuses private helper)
- Loads SPX500 via `securityRepository.findByBenchmarkTrue()` (guarded: empty → IllegalStateException, multiple → IllegalStateException)
- Loads SPX500 bars via `findAllBySecurityIdsOrdered([spxId])` — 504 bars ordered by barDate ASC (Pitfall 4 — alignment guaranteed by ORDER BY)
- Iterates both series in lockstep (same 504-day calendar guaranteed by seed design)
- Rebase: `portfolioIdx(i) = equityCurve[i].value / portfolioBase × 100`, scale 4; `benchmarkIdx(i) = spxClose(i) / benchmarkBase × 100`, scale 4 — **independent** rebasing, not additive shift
- Builds `dates` as `List<String>` via `LocalDate.toString()` (ISO-8601 for ECharts xAxis)
- Returns `BenchmarkComparisonDto(dates, portfolioSeries, benchmarkSeries)`

**New public static helpers (unit-test accessible across packages):**
- `computeTotalUnrealizedGainAbs(totalMarketValue, totalCostBasis)` → `totalMarketValue − totalCostBasis`, scale 2
- `computeTotalUnrealizedGainPct(abs, totalCostBasis)` → `abs / totalCostBasis`, scale 6; ZERO guard
- `computeDailyChange(equityCurve)` → `BigDecimal[]{dailyChangeAbs, dailyChangePct}` from `curve[last] − curve[last-1]`
- `rebaseToIndex(value, base)` → `value / base × 100`, scale 4; ZERO guard

**PortfolioServiceTest updated — three tests turned GREEN:**
- `dailyChangeDerivesFromEquityCurve`: 3-entry hand-crafted curve; asserts `abs = curve[2] − curve[1]` (not `curve[2] − curve[0]`); confirms off-by-one is NOT present
- `totalUnrealizedGainFormula`: hand-crafted total values; asserts `abs = totalMarketValue − totalCostBasis`; verifies zero-denominator guard
- `benchmarkBothSeriesStartAt100`: two independent base values (portfolio $85,432.12, SPX $4,105.23); asserts both `portfolioIdx[0]` and `benchmarkIdx[0]` `compareTo("100.0000") == 0`; confirms subsequent values diverge (independent rebasing, not additive alignment)

### Task 2 — PortfolioController extension (commit `2a6d293`)

**`GET /pnl`:**
```java
@GetMapping("/pnl")
@Transactional(readOnly = true)
public PortfolioPnlDto getPnl(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return portfolioService.getPortfolioPnl(portfolioId);
}
```

**`GET /benchmark`:**
```java
@GetMapping("/benchmark")
@Transactional(readOnly = true)
public BenchmarkComparisonDto getBenchmark(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return portfolioService.getBenchmarkComparison(portfolioId);
}
```

Both endpoints:
- Reuse the existing `resolvePortfolioId(Authentication)` private helper — principal-scoped, IDOR-safe (T-02-01)
- No `@RequestParam` or `@PathVariable` accepts a portfolio/user identifier
- Return DTO directly (not wrapped in `ResponseEntity`) — consistent with the plan's return-wrapping convention note

**Integration tests turned GREEN:**
- `getPnl_aliceEquityCurve504Entries`: 200, `equityCurve` array length == 504
- `getPnl_equityCurveStartDate`: `equityCurve[0].date` == `"2022-09-12"` (ISO string, not `[2022,9,12]` — Jackson JavaTimeModule auto-configured by Spring Boot 3.x confirmed live)
- `getBenchmark_seriesSameLength`: `portfolioSeries.length == benchmarkSeries.length == dates.length`
- `getBenchmark_datesSortedAscending`: `dates[0] == "2022-09-12"`, strictly ascending, `portfolioSeries[0]` and `benchmarkSeries[0]` both `compareTo("100.0000") == 0`

## Verification Results

| Check | Result |
|-------|--------|
| `PortfolioServiceTest#dailyChangeDerivesFromEquityCurve` | GREEN |
| `PortfolioServiceTest#totalUnrealizedGainFormula` | GREEN |
| `PortfolioServiceTest#benchmarkBothSeriesStartAt100` | GREEN |
| `PortfolioServiceTest#unrealizedPnlFormula` (Plan 02 — no regression) | GREEN |
| `PortfolioServiceTest#allocationWeightsSumToOne` (Plan 02 — no regression) | GREEN |
| `PortfolioControllerIntegrationTest#getPnl_aliceEquityCurve504Entries` | GREEN |
| `PortfolioControllerIntegrationTest#getPnl_equityCurveStartDate` | GREEN |
| `PortfolioControllerIntegrationTest#getBenchmark_seriesSameLength` | GREEN |
| `PortfolioControllerIntegrationTest#getBenchmark_datesSortedAscending` | GREEN |
| `PortfolioControllerIntegrationTest#getHoldings_alice_returns5Holdings` (Plan 02 — no regression) | GREEN |
| `PortfolioControllerIntegrationTest#getAllocation_aliceSectorsPresent` (Plan 02 — no regression) | GREEN |
| `PortfolioControllerIntegrationTest#getAllocation_weightSumsToOne` (Plan 02 — no regression) | GREEN |
| `PortfolioControllerIntegrationTest#getHoldings_unauthenticated_returns401` (Plan 02 — no regression) | GREEN |
| Plans 04 unit scaffolds (runningCostBasisAfterBuy, runningCostBasisAfterProportionalSell) | RED (expected) |
| Plans 04 integration scaffolds (getTransactions_mostRecentFirst, getTransactions_paginationWorks) | RED (expected) |
| No `@RequestParam`/`@PathVariable` for portfolio identity | CONFIRMED |
| All `divide()` calls specify scale + RoundingMode | CONFIRMED |
| Equity curve carries constant-current-holdings Javadoc | CONFIRMED |
| Daily change uses `curve[size()-1]` and `curve[size()-2]` (not `get(0)`) | CONFIRMED |
| Both benchmark series[0] == 100.0000 (rebaseToIndex(v,v) == 100.0000 exactly) | CONFIRMED |

## Deviations from Plan

None — plan executed exactly as written.

## Known Stubs

None — `/pnl` and `/benchmark` return real computed data from the seeded Postgres database. The constant-holdings assumption is documented in Javadoc and is intentional for this demo-dashboard scope.

## Threat Flags

No new threat surface beyond the plan's threat model. Both endpoints enforce principal resolution via `resolvePortfolioId`. No JPA entities exposed in responses. SPX500 bars loaded via same bulk query pattern as equity positions (bounded result set, one JDBC round-trip).

## Self-Check: PASSED

Files verified:
- `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java` — FOUND
- `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` — FOUND (modified)
- `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` — FOUND (modified)

Commits verified:
- `5e1fa02` — feat(02-03): add equity-curve P&L + benchmark rebasing to PortfolioService
- `2a6d293` — feat(02-03): add /pnl and /benchmark endpoints to PortfolioController
