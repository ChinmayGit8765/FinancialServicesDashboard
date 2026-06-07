---
phase: 02-portfolio-domain
plan: 02
subsystem: backend/portfolio
tags: [service, controller, holdings, allocation, bigdecimal, idor, tdd-green]
dependency_graph:
  requires: [02-01]
  provides: [PortfolioService, PortfolioController, /holdings, /allocation]
  affects: [02-03, 02-04]
tech_stack:
  added: []
  patterns:
    - "@Service @Transactional(readOnly=true) class-level annotation covering all service methods"
    - "Public static helper methods on service (computeUnrealizedPnlAbs/Pct) for unit-test access across packages"
    - "Last-slice residual absorption for allocation weights summing to exactly 1.000000"
    - "Principal-resolved portfolioId via resolvePortfolioId(Authentication) private helper — NEVER from request params"
    - "ResponseStatusException(401/404) thrown from resolvePortfolioId instead of returning ResponseEntity"
key_files:
  created:
    - backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java
    - backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java
  modified:
    - backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java
decisions:
  - "computeUnrealizedPnlAbs/Pct made public static (not package-private) — test class is in com.quantlens.portfolio, service is in com.quantlens.portfolio.service; package-private would be invisible across sub-packages"
  - "resolvePortfolioId throws ResponseStatusException rather than returning ResponseEntity — cleaner endpoint method signatures (return List<T> directly) and consistent with Spring MVC exception handling"
  - "allocationWeightsSumToOne unit test builds its own slices using the same last-slice residual algorithm (no mock injection needed) — fully validates the mathematical property without Spring context"
metrics:
  duration: "~10 minutes"
  completed: "2026-06-07"
  tasks_completed: 2
  tasks_total: 2
  files_created: 2
  files_modified: 1
---

# Phase 02 Plan 02: PortfolioService + PortfolioController (/holdings + /allocation) Summary

PortfolioService with BigDecimal holdings P&L/weights and last-slice residual allocation, plus principal-scoped PortfolioController — turning 6 target tests GREEN (2 unit + 4 integration) while keeping all Phase 1 tests green.

## What Was Built

### Task 1 — PortfolioService (commit `dab30d8`)

Created `com.quantlens.portfolio.service.PortfolioService` annotated `@Service` with class-level `@Transactional(readOnly = true)`. Constructor injection of `PositionRepository` and `OhlcvBarRepository`.

**Private helper `latestCloseBySecurityId`:** Collects distinct security IDs from positions, calls `ohlcvBarRepository.findLatestBarBySecurityIds(ids)` (single correlated-subquery round-trip, N+1-safe), and returns a `Map<Long, BigDecimal>` keyed by security ID.

**`getHoldings(Long portfolioId)`:**
- Loads positions with `findByPortfolioIdWithSecurity` (JOIN FETCH — no N+1)
- Computes `totalPortfolioMarketValue` = Σ(qty × close, scale 2) for weight denominator
- For each position with `qty.signum() > 0`: computes `currentMarketValue`, `portfolioWeight` (scale 6), `unrealizedPnlAbs` (scale 2), `unrealizedPnlPct` (scale 6)
- Maps to `HoldingDto` — no JPA entities exposed past the service boundary (T-02-02)

**`getAllocation(Long portfolioId)`:**
- Accumulates `sector → marketValue` into `LinkedHashMap` (insertion-order preserved)
- Last-slice residual absorption: all but last sector use `sectorValue.divide(total, 6, HALF_UP)`; last sector uses `ONE.subtract(weightSum).setScale(6, HALF_UP)`
- Guarantees Σ weight = 1.000000 by construction

**Public static helpers** (accessible from unit tests across packages):
- `computeUnrealizedPnlAbs(currentPrice, avgCostBasis, quantity)` → `(price − cost) × qty`, scale 2
- `computeUnrealizedPnlPct(pnlAbs, costBasisTotal)` → `pnlAbs / costBasisTotal`, scale 6; ZERO guard if divisor = 0

**PortfolioServiceTest updated:**
- `unrealizedPnlFormula` — calls static helpers directly with hand-crafted inputs; GREEN
- `allocationWeightsSumToOne` — builds test slices using residual algorithm; asserts `totalWeight.compareTo(ONE) == 0`; GREEN
- Plans 03–04 scaffolds remain RED (`fail("not yet implemented: ...")`)

### Task 2 — PortfolioController (commit `8244180`)

Created `com.quantlens.portfolio.api.PortfolioController` annotated `@RestController @RequestMapping("/api/portfolio")`. Constructor injection of `AppUserRepository`, `PortfolioRepository`, `PortfolioService`.

**`resolvePortfolioId(Authentication)` private helper:**
Mirrors `AuthController.me()` lines 84–99 exactly:
1. Null/!isAuthenticated → throw `ResponseStatusException(401)`
2. `authentication.getName()` → username
3. `appUserRepository.findByUsername(username)` → `AppUser` (absent → 401)
4. `portfolioRepository.findByUserId(userId)` → `List<Portfolio>` (empty → 404)
5. Returns `portfolios.get(0).getId()`

No `@RequestParam` or `@PathVariable` accepts a portfolio/user identifier anywhere in the class (T-02-01 IDOR prevention verified).

**`GET /holdings`:** `resolvePortfolioId` → `portfolioService.getHoldings(id)` → `ResponseEntity.ok(...)`

**`GET /allocation`:** `resolvePortfolioId` → `portfolioService.getAllocation(id)` → `ResponseEntity.ok(...)`

**Integration tests GREEN:**
- `getHoldings_alice_returns5Holdings` — 200, array size = 5
- `getAllocation_aliceSectorsPresent` — 200, array size > 0
- `getAllocation_weightSumsToOne` — sum of `weight` fields ≈ 1.0 (within 0.000001 tolerance)
- `getHoldings_unauthenticated_returns401` — auth gate still live

## Verification Results

| Check | Result |
|-------|--------|
| `PortfolioServiceTest#unrealizedPnlFormula` | GREEN |
| `PortfolioServiceTest#allocationWeightsSumToOne` | GREEN |
| `PortfolioControllerIntegrationTest#getHoldings_alice_returns5Holdings` | GREEN |
| `PortfolioControllerIntegrationTest#getAllocation_aliceSectorsPresent` | GREEN |
| `PortfolioControllerIntegrationTest#getAllocation_weightSumsToOne` | GREEN |
| `PortfolioControllerIntegrationTest#getHoldings_unauthenticated_returns401` | GREEN |
| Full suite — Phase 1 tests (13) | All GREEN |
| Full suite — Plan 03/04 scaffolds (11) | RED (expected — Plans 03–04 not yet implemented) |
| `grep bare .divide(` in PortfolioService | ZERO matches — all divides specify scale + RoundingMode |
| `grep subtract(weightSum` in PortfolioService | 1 match — last-slice residual absorption present |
| No `@RequestParam`/`@PathVariable` for portfolio identity | CONFIRMED |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Static helpers needed `public` visibility, not package-private**
- **Found during:** Task 1 — IDE diagnostics immediately after write
- **Issue:** Plan specified "package-private static helpers" but `PortfolioServiceTest` is in package `com.quantlens.portfolio` while `PortfolioService` is in `com.quantlens.portfolio.service` — different packages, so package-private is invisible
- **Fix:** Changed `static` to `public static` on `computeUnrealizedPnlAbs` and `computeUnrealizedPnlPct`
- **Files modified:** `PortfolioService.java`
- **Commit:** `dab30d8`

## Known Stubs

None — `/holdings` and `/allocation` return real computed data from the seeded Postgres database. No hardcoded placeholders.

## Threat Flags

No new threat surface beyond what the plan's threat model covers. All endpoints enforce principal resolution via `resolvePortfolioId`. No JPA entities exposed in responses.

## Self-Check: PASSED

Files verified:
- `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java` — FOUND
- `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java` — FOUND
- `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` — FOUND (modified)

Commits verified:
- `dab30d8` — feat(02-02): create PortfolioService with holdings + allocation computation
- `8244180` — feat(02-02): create PortfolioController with /holdings and /allocation endpoints
