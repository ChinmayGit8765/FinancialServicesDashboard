---
phase: 02-portfolio-domain
verified: 2026-06-07T12:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 02: Portfolio Domain Verification Report

**Phase Goal:** Users can view their complete portfolio — holdings with P&L, allocation breakdown, transaction history, and benchmark comparison — all computed from seeded data through a clean REST API.
**Verified:** 2026-06-07
**Status:** PASSED
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | GET /api/portfolio/holdings returns 5 holdings with all per-position P&L and weight fields | VERIFIED | PortfolioController.getHoldings → resolvePortfolioId → PortfolioService.getHoldings; returns HoldingDto with 10 fields including unrealizedPnlAbs, unrealizedPnlPct, portfolioWeight; integration test getHoldings_alice_returns5Holdings asserts array length == 5 and all field names |
| 2 | GET /api/portfolio/pnl returns a 504-entry equity curve starting 2022-09-12 plus total P&L and daily change | VERIFIED | PortfolioService.getPortfolioPnl calls buildEquityCurve using findAllBySecurityIdsOrdered; daily change uses curve[size-1] and curve[size-2] (Pitfall 3 avoided); integration tests assert equityCurve.size()==504 and dates[0]=="2022-09-12" |
| 3 | GET /api/portfolio/allocation returns sector slices whose weights sum to exactly 1.000000 | VERIFIED | Last-slice residual absorption: BigDecimal.ONE.subtract(weightSum) at line 231; integration test getAllocation_weightSumsToOne asserts sum within 0.000001; unit test allocationWeightsSumToOne asserts exact BigDecimal.ONE |
| 4 | GET /api/portfolio/transactions returns paginated most-recent-first with GAAP running cost basis (SELL uses avgCostNow not sellPrice) | VERIFIED | buildRunningCostMap: SELL path reduces by qty*avgCostNow, explicitly NOT sellPrice (documented in comments); two-pass service; @PageableDefault(size=20); application.yml max-page-size: 500; integration tests getTransactions_mostRecentFirst and getTransactions_paginationWorks |
| 5 | GET /api/portfolio/benchmark returns parallel arrays with both series rebased to exactly 100.0000 on day 0, dates ascending | VERIFIED | rebaseToIndex: value.divide(base,6,HALF_UP).multiply(100).setScale(4,HALF_UP); portfolioBase=equityCurve.get(0).value(), benchmarkBase=spxBars.get(0).getClosePrice(); integration test getBenchmark_datesSortedAscending asserts day-0 values compare to "100.0000"==0 and dates sorted ascending |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/portfolio/api/HoldingDto.java` | 10-component record DTO for PORT-01 | VERIFIED | Exists; 10 fields: ticker, name, sector, quantity, avgCostBasis, currentPrice, currentMarketValue, portfolioWeight, unrealizedPnlAbs, unrealizedPnlPct; no @JsonFormat |
| `backend/src/main/java/com/quantlens/portfolio/api/PortfolioPnlDto.java` | P&L DTO with equityCurve for PORT-02 | VERIFIED | record PortfolioPnlDto with List<DateValueDto> equityCurve |
| `backend/src/main/java/com/quantlens/portfolio/api/AllocationSliceDto.java` | Allocation slice DTO for PORT-03 | VERIFIED | record AllocationSliceDto(label, weight, marketValue) |
| `backend/src/main/java/com/quantlens/portfolio/api/TransactionDto.java` | Transaction DTO with runningCostBasis for PORT-04 | VERIFIED | record TransactionDto with runningCostBasis field; LocalDate txDate |
| `backend/src/main/java/com/quantlens/portfolio/api/BenchmarkComparisonDto.java` | Benchmark DTO for PORT-05; dates is List<String> | VERIFIED | record BenchmarkComparisonDto(dates:List<String>, portfolioSeries:List<BigDecimal>, benchmarkSeries:List<BigDecimal>) |
| `backend/src/main/java/com/quantlens/portfolio/api/DateValueDto.java` | Shared date-value point record | VERIFIED | record DateValueDto(LocalDate date, BigDecimal value) |
| `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java` | Service with all 4 computation methods + helpers | VERIFIED | @Service @Transactional(readOnly=true); getHoldings, getAllocation, getPortfolioPnl, getBenchmarkComparison, getTransactions all present; public static helpers for unit tests |
| `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java` | Controller with 5 endpoints, IDOR-safe | VERIFIED | @RestController @RequestMapping("/api/portfolio"); 5 @GetMapping methods; resolvePortfolioId(Authentication) private helper; zero @RequestParam/@PathVariable for portfolio identity |
| `backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java` | findByPortfolioIdWithSecurity with JOIN FETCH | VERIFIED | @Query with "SELECT p FROM Position p JOIN FETCH p.security WHERE p.portfolio.id = :portfolioId" |
| `backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java` | findLatestBarBySecurityIds + findAllBySecurityIdsOrdered | VERIFIED | Both methods present; correlated MAX subquery for latest; ORDER BY security.id ASC, barDate ASC for ordered |
| `backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java` | findByPortfolioIdWithSecurity(Pageable) + findByPortfolioIdChronological | VERIFIED | Both methods present; paginated DESC and chronological ASC variants |
| `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` | 7 unit tests covering all 5 PORT requirements | VERIFIED | All 7 test methods present and substantive: unrealizedPnlFormula, allocationWeightsSumToOne, dailyChangeDerivesFromEquityCurve, totalUnrealizedGainFormula, runningCostBasisAfterBuy, runningCostBasisAfterProportionalSell, benchmarkBothSeriesStartAt100 |
| `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` | Integration tests for all 5 endpoints + auth gate | VERIFIED | extends AbstractPostgresIntegrationTest; 10 test methods covering auth gate + all 5 endpoints; loginAndGetSessionCookie/authenticatedGet helpers present |
| `backend/src/test/java/com/quantlens/portfolio/GoldenValuePrinterTest.java` | @Disabled golden-value printer | VERIFIED | Exists; @Disabled annotation present at line 57 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| PortfolioController.resolvePortfolioId | Authentication.getName() → AppUser → Portfolio | principal-resolution helper | VERIFIED | Lines 183-195: getName() → appUserRepository.findByUsername → portfolioRepository.findByUserId; zero @RequestParam/@PathVariable for portfolio identity confirmed by grep |
| PortfolioService.getHoldings | PositionRepository.findByPortfolioIdWithSecurity + OhlcvBarRepository.findLatestBarBySecurityIds | N+1-safe queries | VERIFIED | Line 131: positionRepository.findByPortfolioIdWithSecurity; line 631: ohlcvBarRepository.findLatestBarBySecurityIds |
| PortfolioService.getPortfolioPnl | OhlcvBarRepository.findAllBySecurityIdsOrdered | bulk ordered bars for equity curve | VERIFIED | Line 569: ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds) in buildEquityCurve |
| PortfolioService.getBenchmarkComparison | SecurityRepository.findByBenchmarkTrue + bulk bars | SPX500 series alongside portfolio | VERIFIED | Lines 296-307: findByBenchmarkTrue() + findAllBySecurityIdsOrdered([spx.getId()]) |
| PortfolioService.getTransactions | TransactionRepository.findByPortfolioIdChronological (pass 1) + findByPortfolioIdWithSecurity (pass 2) | two-pass cost-basis algorithm | VERIFIED | Lines 97-113: chronological load for buildRunningCostMap, then paginated load for display |
| buildRunningCostMap SELL path | avgCostNow (not sellPrice) | runningCost.divide(runningQty,6,HALF_UP) | VERIFIED | Lines 360-365: avgCostNow computed from runningCost/runningQty; comment explicitly states "Basis reduced by sellQty × avgCostNow, NOT sellQty × sellPrice" |
| Allocation last slice | BigDecimal.ONE.subtract(weightSum) | residual absorption | VERIFIED | Line 231: exact expression present; unit test asserts sum compareTo BigDecimal.ONE |
| Daily change | curve[size-1] and curve[size-2] | NOT curve[0] as previous | VERIFIED | Lines 503-504: equityCurve.get(equityCurve.size() - 1) and equityCurve.get(equityCurve.size() - 2); unit test explicitly asserts result != first-to-last change |
| Benchmark rebasing | value/base*100 at scale 4 | rebaseToIndex static helper | VERIFIED | Lines 524-531: value.divide(base,6,HALF_UP).multiply(100).setScale(4,HALF_UP); integration test asserts day-0 values == 100.0000 by BigDecimal.compareTo |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| PortfolioController.getHoldings | List<HoldingDto> | PortfolioService.getHoldings → PositionRepository + OhlcvBarRepository | DB queries via JPA/JPQL | FLOWING |
| PortfolioController.getPnl | PortfolioPnlDto with equityCurve | PortfolioService.getPortfolioPnl → buildEquityCurve → findAllBySecurityIdsOrdered | DB query returning 504×N rows | FLOWING |
| PortfolioController.getAllocation | List<AllocationSliceDto> | PortfolioService.getAllocation → PositionRepository + OhlcvBarRepository | DB queries; LinkedHashMap sector aggregation | FLOWING |
| PortfolioController.getTransactions | Page<TransactionDto> | PortfolioService.getTransactions → TransactionRepository (two passes) | DB queries; running cost map from full chronological scan | FLOWING |
| PortfolioController.getBenchmark | BenchmarkComparisonDto | PortfolioService.getBenchmarkComparison → SecurityRepository + OhlcvBarRepository | DB queries; both series from real seeded bars | FLOWING |

All endpoints return live-computed data from seeded Postgres. No hardcoded empty arrays or static returns detected. SUMMARY confirms "None — all five endpoints return live data from seeded Postgres; no hardcoded empty values or placeholder text."

### Behavioral Spot-Checks

Step 7b: The phase target is a Spring Boot backend requiring Testcontainers (Docker) for integration tests. The context confirms `mvnw verify` was run with 34 tests / 0 failures immediately before this verification. Direct server-startup checks without Docker are not possible in this environment. The test suite result (34 tests, 0 failures, 1 skipped) constitutes the behavioral evidence for all 5 endpoints.

| Behavior | Evidence | Status |
|----------|----------|--------|
| Holdings endpoint returns 5 entries for alice | getHoldings_alice_returns5Holdings in integration test — part of passing 34-test suite | PASS |
| PnL equity curve has 504 entries starting 2022-09-12 | getPnl_aliceEquityCurve504Entries + getPnl_equityCurveStartDate — passing | PASS |
| Allocation weights sum to 1.000000 | getAllocation_weightSumsToOne (integration) + allocationWeightsSumToOne (unit) — passing | PASS |
| Transactions are paginated and most-recent-first | getTransactions_mostRecentFirst + getTransactions_paginationWorks — passing | PASS |
| Benchmark series both start at 100.0000 and dates ascending | getBenchmark_datesSortedAscending + getBenchmark_seriesSameLength — passing | PASS |
| Unauthenticated request returns 401 | getHoldings_unauthenticated_returns401 — passing | PASS |

### Probe Execution

No probe scripts declared in PLAN files. No `scripts/*/tests/probe-*.sh` pattern applicable to this phase. Phase delivers runnable endpoints verified by the Maven test suite.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| PORT-01 | 02-01-PLAN, 02-02-PLAN | User can view holdings with current value, weight, cost basis, and unrealized P&L per position | SATISFIED | HoldingDto (10 fields); getHoldings computes all fields; integration test asserts 5-holding array with all fields present |
| PORT-02 | 02-01-PLAN, 02-03-PLAN | User can view portfolio-level P&L as a time-series curve | SATISFIED | PortfolioPnlDto with equityCurve:List<DateValueDto>; buildEquityCurve returns 504 entries; daily change uses curve[last]-curve[last-1]; integration test asserts 504 entries and 2022-09-12 start date |
| PORT-03 | 02-01-PLAN, 02-02-PLAN | User can view an allocation breakdown by sector | SATISFIED | getAllocation returns List<AllocationSliceDto>; last-slice residual absorption guarantees weights sum to exactly 1.000000; integration test asserts sector slices present and weight sum correct |
| PORT-04 | 02-01-PLAN, 02-04-PLAN | User can view transaction history with running cost basis | SATISFIED | getTransactions returns Page<TransactionDto>; buildRunningCostMap implements GAAP average-cost (SELL reduces by avgCostNow not sellPrice); paginated with @PageableDefault(size=20); max-page-size: 500 in application.yml; two integration tests green |
| PORT-05 | 02-01-PLAN, 02-03-PLAN | User can compare portfolio return against S&P 500 proxy benchmark | SATISFIED | getBenchmarkComparison loads SPX500 via findByBenchmarkTrue; rebaseToIndex produces 100.0000 on day 0 for both series; BenchmarkComparisonDto.dates is List<String> for ECharts; integration test asserts day-0 values and ascending dates |

All 5 PORT requirements assigned to Phase 2 in REQUIREMENTS.md are satisfied. REQUIREMENTS.md traceability table lists PORT-01 through PORT-05 as Phase 2, Pending — all can now be marked Complete.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | — |

No debt markers (TBD, FIXME, XXX), no stub patterns, no placeholder text found in any file in the `com.quantlens.portfolio` package. Grep across all portfolio source files returned zero matches.

No bare `.divide(x)` calls (single-argument divide without scale/RoundingMode) found in PortfolioService.java — every division specifies scale and HALF_UP as required.

No `@JsonFormat` or `@JsonProperty` annotations in any DTO file — BigDecimal serializes as JSON number for ECharts as intended.

No `@RequestParam` or `@PathVariable` carrying a portfolio/user identifier in PortfolioController — IDOR prevention confirmed by code inspection.

### Human Verification Required

None. All required behaviors are verifiable by code inspection and the confirmed test suite results. Phase goal is fully achieved by automated evidence.

## Gaps Summary

No gaps. All 5 PORT requirements are implemented with substantive, wired, data-flowing artifacts. The 34-test / 0-failure suite result corroborates correctness of all computation logic. The phase goal is achieved.

---

_Verified: 2026-06-07T12:00:00Z_
_Verifier: Claude (gsd-verifier)_
