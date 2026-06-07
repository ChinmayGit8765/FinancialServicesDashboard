---
phase: 02-portfolio-domain
plan: 04
subsystem: portfolio-service
tags: [transactions, running-cost-basis, pagination, gaap-average-cost]
dependency_graph:
  requires: [02-03]
  provides: [PORT-04, transactions-endpoint, running-cost-basis-scan]
  affects: [PortfolioService, PortfolioController, application.yml]
tech_stack:
  added: []
  patterns:
    - GAAP average-cost running basis (BUY: add qty*price; SELL: reduce by sellQty*avgCostNow)
    - Two-pass transaction scan (chronological for cost map, paginated for display)
    - Spring Data Pageable + @PageableDefault(size=20, sort=txDate DESC)
    - page-size cap via spring.data.web.pageable.max-page-size=500
key_files:
  created: []
  modified:
    - backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java
    - backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java
    - backend/src/main/resources/application.yml
    - backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java
decisions:
  - computeRunningCostBasisFromTuples is public static (not package-private) to allow cross-subpackage access from PortfolioServiceTest in com.quantlens.portfolio
  - Two-pass approach: full chronological scan for cost map + separate paginated query for display — avoids sorting complexity inside a Page<T> mapping step
  - SELL basis reduction uses avgCostNow (runningCost/runningQty at point of sale), never sellPrice
metrics:
  duration: 15 minutes
  completed: 2026-06-07
  tasks_completed: 2
  files_modified: 4
---

# Phase 02 Plan 04: Transactions Endpoint + Running Cost Basis Summary

**One-liner:** Paginated /transactions endpoint with GAAP average-cost running basis (BUY adds qty*price to cost; SELL reduces by sellQty*avgCostNow, not sellPrice); two-pass service method returns Page<TransactionDto> most-recent-first with page-size capped at 500.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Add getTransactions + running-cost-basis scan to PortfolioService | ba41dd2 | PortfolioService.java, PortfolioServiceTest.java |
| 2 | Add /transactions endpoint + page-size cap; finalize suite | 66087e2 | PortfolioController.java, application.yml |

## What Was Built

### Task 1: PortfolioService.getTransactions + running-cost helpers

- `buildRunningCostMap(List<Transaction>)` — package-private static helper; chronological scan (txDate ASC, id ASC per Pitfall 5) builds `Map<Long, BigDecimal>` of transactionId → avgCostBasis after that trade. BUY: `runningCost += qty*price; runningQty += qty`. SELL: `avgCostNow = runningCost/runningQty (scale 6, HALF_UP)`; `runningCost -= sellQty*avgCostNow` (never sellPrice). Guard: runningQty==0 returns ZERO. Every divide() specifies scale=6 and HALF_UP.
- `computeRunningCostBasisFromTuples(List<String[]>)` — public static unit-test-friendly overload taking `{txType, qty, price}` string triples; no JPA entity construction needed in tests.
- `getTransactions(Long portfolioId, Pageable)` — two-pass: (1) `findByPortfolioIdChronological` to build cost map; (2) `findByPortfolioIdWithSecurity` (paginated, txDate DESC) mapped to `Page<TransactionDto>`. Returns `Page<TransactionDto>` never `Page<Transaction>` (Pitfall 6).
- `TransactionRepository` injected via constructor.

### Task 2: PortfolioController /transactions + application.yml cap

- `GET /api/portfolio/transactions` with `@PageableDefault(size=20, sort="txDate", direction=Sort.Direction.DESC)` — delegates to `portfolioService.getTransactions`. No portfolio/user identifier as request param (IDOR-safe T-02-01).
- `application.yml`: added `spring.data.web.pageable.max-page-size: 500` and `default-page-size: 20` under `spring.data.web.pageable` (DoS cap T-02-05). All existing keys preserved.

### Tests turned GREEN

Unit (PortfolioServiceTest):
- `runningCostBasisAfterBuy` — BUY 100@150 → basis==150.000000 ✓
- `runningCostBasisAfterProportionalSell` — BUY 100@150 then SELL 30@200 → basis still==150.000000 (not 200) ✓

Integration (PortfolioControllerIntegrationTest):
- `getTransactions_mostRecentFirst` — txDate[0] >= txDate[1]; totalElements>0; content is array ✓
- `getTransactions_paginationWorks` — page0 first date != page1 first date (disjoint pages) ✓

Full suite: **34 tests run, 0 failures, 1 skipped** (GoldenValuePrinterTest intentionally skipped).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing visibility] computeRunningCostBasisFromTuples made public static**
- **Found during:** Task 1 implementation — IDE reported "method not visible" in PortfolioServiceTest (cross-subpackage: test is in com.quantlens.portfolio, helper was in com.quantlens.portfolio.service)
- **Fix:** Changed `static` to `public static` on the tuple overload only; `buildRunningCostMap` remains package-private (used only within the service package)
- **Files modified:** PortfolioService.java

## Known Stubs

None — all five endpoints return live data from seeded Postgres; no hardcoded empty values or placeholder text.

## Threat Flags

No new threat surface introduced beyond the plan's threat model. The /transactions endpoint follows the same principal-resolution chain as the four existing endpoints (T-02-01 IDOR mitigated). Page-size cap deployed (T-02-05 mitigated). TransactionDto returns no sensitive fields beyond trade data (T-02-02 mitigated).

## Self-Check: PASSED

- ba41dd2 exists: `git log --oneline | grep ba41dd2` ✓
- 66087e2 exists: `git log --oneline | grep 66087e2` ✓
- PortfolioService.java contains `getTransactions` and `buildRunningCostMap` ✓
- PortfolioController.java contains `/transactions` endpoint ✓
- application.yml contains `max-page-size: 500` ✓
- Full suite: 34 passed, 0 failed ✓
