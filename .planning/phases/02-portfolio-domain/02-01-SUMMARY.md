---
phase: 02-portfolio-domain
plan: 01
subsystem: backend/portfolio
tags: [dto, repository, test-scaffold, jpa, n+1, tdd-red]
dependency_graph:
  requires: [01-04]
  provides: [DTO contracts, repository query methods, RED test gates]
  affects: [02-02, 02-03, 02-04]
tech_stack:
  added: []
  patterns:
    - Java record DTOs with Javadoc @param per component (MeDto/PersonaDto convention)
    - JOIN FETCH JPQL to prevent N+1 on Position→Security
    - Correlated MAX subquery for latest OHLCV bar per security
    - Bulk IN-clause query for equity-curve and benchmark series
    - JOIN FETCH + Pageable on single-valued association (safe pagination)
    - RED scaffold unit tests with fail("not yet implemented: ...") for Nyquist compliance
    - @Disabled golden-value printer pattern for deterministic seed constants
key_files:
  created:
    - backend/src/main/java/com/quantlens/portfolio/api/HoldingDto.java
    - backend/src/main/java/com/quantlens/portfolio/api/DateValueDto.java
    - backend/src/main/java/com/quantlens/portfolio/api/PortfolioPnlDto.java
    - backend/src/main/java/com/quantlens/portfolio/api/AllocationSliceDto.java
    - backend/src/main/java/com/quantlens/portfolio/api/TransactionDto.java
    - backend/src/main/java/com/quantlens/portfolio/api/BenchmarkComparisonDto.java
    - backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java
    - backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java
    - backend/src/test/java/com/quantlens/portfolio/GoldenValuePrinterTest.java
  modified:
    - backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java
    - backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java
    - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java
decisions:
  - "DateValueDto as separate file (not nested in PortfolioPnlDto) to enable reuse by GoldenValuePrinterTest and future benchmark DTO consumption"
  - "BenchmarkComparisonDto.dates is List<String> not List<LocalDate> — ECharts xAxis expects string labels"
  - "GoldenValuePrinterTest imports AppUser/AppUserRepository from portfolio.domain (not security.domain — confirmed by codebase)"
  - "RED scaffold tests use fail('not yet implemented: ...') so suite compiles but gates exist before implementation"
metrics:
  duration: "~15 minutes"
  completed: "2026-06-07"
  tasks_completed: 3
  tasks_total: 3
  files_created: 9
  files_modified: 3
---

# Phase 02 Plan 01: Wave 0 Foundation (DTOs + Repository Extensions + RED Scaffolds) Summary

Six record DTOs + five N+1-safe repository query methods + two compiling RED test scaffolds + one @Disabled golden-value printer, giving Plans 02-04 all contracts and gates before a single line of service logic is written.

## What Was Built

### Task 1 — Six DTO Record Contracts (commit `0c75bb5`)

Created `com.quantlens.portfolio.api` package with six Java record DTOs following the MeDto/PersonaDto convention (one package line, Javadoc with `@param` per component, compact record body, no `@JsonFormat`/`@JsonProperty`):

| DTO | Purpose | Key Type Decisions |
|-----|---------|-------------------|
| `HoldingDto` | One position per row for holdings endpoint | 10 components; BigDecimal for all monetary fields |
| `DateValueDto` | Single point on equity/time-series curve | `LocalDate` date (serialises as ISO string via JavaTimeModule) |
| `PortfolioPnlDto` | Portfolio P&L summary + equity curve | `List<DateValueDto> equityCurve` — separate file reusable |
| `AllocationSliceDto` | Sector allocation slice | `label` + `weight` (scale 6) + `marketValue` (scale 2) |
| `TransactionDto` | Paginated transaction entry | Includes computed `runningCostBasis` field |
| `BenchmarkComparisonDto` | Portfolio vs benchmark indexed series | `List<String> dates` — NOT `List<LocalDate>` (ECharts xAxis requirement) |

All BigDecimal fields serialise as JSON numbers (no `@JsonFormat`) per ECharts direct-binding requirement. `LocalDate` serialises as `"2022-09-12"` via Spring Boot's auto-registered JavaTimeModule.

### Task 2 — Repository Extensions (commit `95f0c1d`)

Extended three bare `JpaRepository` interfaces with five N+1-safe query methods using Java text-block JPQL:

**PositionRepository:**
- `findByPortfolioIdWithSecurity(Long portfolioId)` — `JOIN FETCH p.security` prevents N+1 on holdings iteration

**OhlcvBarRepository:**
- `findLatestBarBySecurityIds(List<Long> securityIds)` — correlated `MAX(b2.barDate)` subquery returns exactly one bar per security for current-price lookups
- `findAllBySecurityIdsOrdered(List<Long> securityIds)` — bulk `IN :securityIds ORDER BY security.id ASC, barDate ASC` returns entire OHLCV series (504×N rows) in one JDBC round-trip

**TransactionRepository:**
- `findByPortfolioIdWithSecurity(Long portfolioId, Pageable pageable)` — paginated `Page<Transaction>` with JOIN FETCH; safe because Transaction→Security is a single-valued association (no count-query override needed)
- `findByPortfolioIdChronological(Long portfolioId)` — `ORDER BY txDate ASC, id ASC` for running-cost-basis scan in Plan 04

No entity files or `package-info.java` were modified (module boundaries intact).

### Task 3 — RED Test Scaffolds + Golden-Value Printer (commit `4205177`)

**PortfolioServiceTest** (pure JUnit 5, no Spring context):
- 7 RED unit scaffolds matching the VALIDATION map exactly: `unrealizedPnlFormula`, `allocationWeightsSumToOne`, `dailyChangeDerivesFromEquityCurve`, `totalUnrealizedGainFormula`, `runningCostBasisAfterBuy`, `runningCostBasisAfterProportionalSell`, `benchmarkBothSeriesStartAt100`
- Each uses `fail("not yet implemented: ...")` so the suite compiles but remains RED until Plans 02-04 implement the helpers
- Hand-crafted inputs document the expected computation for implementers

**PortfolioControllerIntegrationTest** (extends `AbstractPostgresIntegrationTest`):
- `getHoldings_unauthenticated_returns401` — **passes immediately**, proving T-02-01 IDOR auth gate is live
- 9 RED endpoint scaffolds covering all VALIDATION map integration tests (PORT-01 through PORT-05); return 404 until Plans 02-04 add PortfolioController
- Includes `loginAndGetSessionCookie`, `authenticatedGet`, `extractJsonField` helpers copied from PersonaIntegrationTest

**GoldenValuePrinterTest** (`@Disabled`):
- Single `@Disabled @Test` that wires all 5 repositories (`PositionRepository`, `OhlcvBarRepository`, `SecurityRepository`, `AppUserRepository`, `PortfolioRepository`, `TransactionRepository`)
- Prints alice's positions (ticker/qty/avgCostBasis/mktValue), benchmark first/last bars, transaction log, and OhlcvBar counts per security
- `System.out` output ready to copy as constants into integration test assertions

## Verification Results

| Check | Result |
|-------|--------|
| `.\mvnw.cmd compile` | PASS |
| `.\mvnw.cmd test-compile` | PASS |
| `PortfolioServiceTest` (7 tests) | RED — all 7 fail with "not yet implemented" (expected Wave 0 state) |
| `getHoldings_unauthenticated_returns401` | PASS (auth gate live) |
| Phase 1 existing tests (13 tests) | All GREEN |
| `GoldenValuePrinterTest` | SKIPPED (@Disabled — correct) |
| Total suite: 34 run, 16 RED failures, 1 skipped | Expected Wave 0 state |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Wrong package for AppUser/AppUserRepository in GoldenValuePrinterTest**
- **Found during:** Task 3 — IDE diagnostics showed import resolution failure
- **Issue:** Initial draft imported `com.quantlens.security.domain.AppUser` — these classes live in `com.quantlens.portfolio.domain` (confirmed by codebase search)
- **Fix:** Corrected imports to `com.quantlens.portfolio.domain.AppUser` and `com.quantlens.portfolio.domain.AppUserRepository`
- **Files modified:** `GoldenValuePrinterTest.java`
- **Commit:** included in `4205177`

## Known Stubs

None — this plan creates interface contracts (DTOs + repository methods + test scaffolds), not data-returning implementations. All stub-like behavior is intentional RED scaffolding that Plans 02-04 will fill.

## Threat Flags

No new threat surface introduced. DTOs carry no portfolioId input field (T-02-01 mitigated). Repository methods accept server-resolved parameters only. `getHoldings_unauthenticated_returns401` confirms the auth gate is live.

## Self-Check: PASSED

Files verified:
- `backend/src/main/java/com/quantlens/portfolio/api/HoldingDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/DateValueDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/PortfolioPnlDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/AllocationSliceDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/TransactionDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/BenchmarkComparisonDto.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java` — FOUND (extended)
- `backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java` — FOUND (extended)
- `backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java` — FOUND (extended)
- `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` — FOUND
- `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` — FOUND
- `backend/src/test/java/com/quantlens/portfolio/GoldenValuePrinterTest.java` — FOUND

Commits verified:
- `0c75bb5` — feat(02-01): create six DTO record contracts
- `95f0c1d` — feat(02-01): extend repositories with N+1-safe query methods
- `4205177` — test(02-01): create RED test scaffolds + golden-value printer
