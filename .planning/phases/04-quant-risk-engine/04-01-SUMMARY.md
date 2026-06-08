---
phase: 04-quant-risk-engine
plan: 01
subsystem: backend/analytics + frontend/api
tags: [analytics, modulith, dtos, stub-services, red-tests, golden-value-printer]
dependency_graph:
  requires: []
  provides:
    - com.quantlens.analytics Modulith module with @ApplicationModule boundary
    - 5 DTO Java records (VarResultDto, RiskScorecardDto, CorrelationMatrixDto, AttributionDto, PairResultDto)
    - 4 stub @Service classes (RiskCalculator, CorrelationCalculator, FamaFrenchCalculator, CointegrationScanner)
    - AnalyticsController — 4 principal-scoped endpoints (GET /risk /correlation /attribution /pairs)
    - FactorReturnRepository.findAllByOrderByFactorDateAsc() query
    - 6 backend RED test scaffolds + 4 frontend RED test scaffolds
    - AnalyticsGoldenValuePrinterTest @Disabled golden-value printer
    - frontend/src/api/analytics.ts — 5 TypeScript DTO interfaces
  affects:
    - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturnRepository.java (extended)
    - frontend/tsconfig.app.json (test exclusion fix)
tech_stack:
  added: []
  patterns:
    - Spring Modulith @ApplicationModule with allowedDependencies (marketdata::domain + portfolio::domain)
    - Java record DTOs (compact style matching DateValueDto analog)
    - resolvePortfolioId copied verbatim from PortfolioController (IDOR T-04-01 prevention)
    - @Disabled golden-value printer pattern (mirrors GoldenValuePrinterTest)
    - RED test scaffold pattern — golden constants as 0.0 placeholders, FILL from printer
key_files:
  created:
    - backend/src/main/java/com/quantlens/analytics/package-info.java
    - backend/src/main/java/com/quantlens/analytics/api/VarResultDto.java
    - backend/src/main/java/com/quantlens/analytics/api/RiskScorecardDto.java
    - backend/src/main/java/com/quantlens/analytics/api/CorrelationMatrixDto.java
    - backend/src/main/java/com/quantlens/analytics/api/AttributionDto.java
    - backend/src/main/java/com/quantlens/analytics/api/PairResultDto.java
    - backend/src/main/java/com/quantlens/analytics/api/AnalyticsController.java
    - backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java
    - backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java
    - backend/src/main/java/com/quantlens/analytics/service/FamaFrenchCalculator.java
    - backend/src/main/java/com/quantlens/analytics/service/CointegrationScanner.java
    - backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java
    - backend/src/test/java/com/quantlens/analytics/CorrelationCalculatorTest.java
    - backend/src/test/java/com/quantlens/analytics/FamaFrenchCalculatorTest.java
    - backend/src/test/java/com/quantlens/analytics/CointegrationScannerTest.java
    - backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java
    - backend/src/test/java/com/quantlens/analytics/AnalyticsGoldenValuePrinterTest.java
    - frontend/src/api/analytics.ts
    - frontend/src/__tests__/components/RiskScorecard.test.ts
    - frontend/src/__tests__/components/CorrelationHeatmap.test.ts
    - frontend/src/__tests__/components/AttributionChart.test.ts
    - frontend/src/__tests__/components/PairsTable.test.ts
  modified:
    - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturnRepository.java
    - frontend/tsconfig.app.json
decisions:
  - "[04-01] analytics module is a new com.quantlens.analytics Modulith module (not extending portfolio) — confirmed in 04-RESEARCH.md Open Question 1; analytics replicates equity-curve logic locally rather than depending on portfolio::service"
  - "[04-01] portfolio::service NOT in allowedDependencies — analytics reads only from marketdata::domain and portfolio::domain named interfaces"
  - "[04-01] resolvePortfolioId copied verbatim from PortfolioController — no divergence in IDOR control pattern"
  - "[04-01] tsconfig.app.json exclude __tests__ — test files must not be included in vue-tsc production build compilation"
metrics:
  duration: ~20 minutes
  completed_date: "2026-06-08"
  tasks_completed: 3
  files_created: 22
  files_modified: 2
---

# Phase 04 Plan 01: Analytics Module Foundation (Wave 0) Summary

**One-liner:** `com.quantlens.analytics` Spring Modulith module with 5 DTO records, 4 stub services, 4-endpoint principal-scoped controller, FactorReturnRepository query, and 10 RED test scaffolds including a @Disabled golden-value printer.

## Tasks Completed

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Analytics module + 5 DTO records + FactorReturnRepository query | `730d65a` | package-info.java, 5 DTO records, FactorReturnRepository.java |
| 2 | Stub calculator services + AnalyticsController | `7364e35` | 4 service stubs, AnalyticsController.java |
| 3 | Wave 0 RED test scaffolds + analytics.ts | `2e02546` | 6 backend tests, 4 frontend tests, analytics.ts, tsconfig.app.json fix |

## What Was Built

### Backend

**`com.quantlens.analytics` Modulith module** (`package-info.java`):
- `@ApplicationModule(displayName = "Analytics", allowedDependencies = {"marketdata::domain", "portfolio::domain"})`
- No `portfolio::service` dependency — analytics replicates equity-curve logic locally (decision locked in RESEARCH.md)

**Five DTO records** in `analytics/api/`:
- `VarResultDto(String method, double confidence, int horizonDays, BigDecimal amount, double percentage)` — HISTORICAL/PARAMETRIC/CVaR_HISTORICAL
- `RiskScorecardDto(double sharpeRatio, double annualizedVolatility, double maxDrawdown, double beta, List<VarResultDto> var)`
- `CorrelationMatrixDto(List<String> tickers, List<List<Double>> matrix)`
- `AttributionDto(double alphaAnnualized, double betaMkt, double betaSmb, double betaHml, double rSquared, double contribMktAnnualized, double contribSmbAnnualized, double contribHmlAnnualized)`
- `PairResultDto(String tickerY, String tickerX, double hedgeRatio, double adfStatistic, double pValue, double spreadZScore, String signal)`
- Numeric convention enforced: statistics as `double`, monetary values as `BigDecimal`

**FactorReturnRepository** extended with:
- `List<FactorReturn> findAllByOrderByFactorDateAsc()` — Spring Data derived query, 504 rows ordered by factorDate ASC

**Four stub `@Service @Transactional(readOnly=true)` classes** in `analytics/service/`:
- `RiskCalculator` — injects PositionRepository, OhlcvBarRepository, SecurityRepository, FactorReturnRepository; `computeRiskScorecard(Long)` returns stub zeroed DTO; STUB: Plan 04-02
- `CorrelationCalculator` — injects PositionRepository, OhlcvBarRepository; `computeCorrelationMatrix(Long)` returns empty DTO; STUB: Plan 04-02
- `FamaFrenchCalculator` — injects PositionRepository, OhlcvBarRepository, FactorReturnRepository; `computeAttribution(Long)` returns zeroed DTO; STUB: Plan 04-03
- `CointegrationScanner` — injects PositionRepository, OhlcvBarRepository, SecurityRepository; `scanPairs(Long)` returns `List.of()`; STUB: Plan 04-03

**`AnalyticsController`** (`@RestController @RequestMapping("/api/portfolio")`):
- `GET /risk` → `ResponseEntity<RiskScorecardDto>`
- `GET /correlation` → `ResponseEntity<CorrelationMatrixDto>`
- `GET /attribution` → `ResponseEntity<AttributionDto>`
- `GET /pairs` → `ResponseEntity<List<PairResultDto>>`
- `resolvePortfolioId(Authentication)` copied verbatim from PortfolioController — IDOR T-04-01 mitigation
- No `@RequestParam` or `@PathVariable` accepts portfolioId — principal-only resolution

### Backend Tests (6 RED scaffolds + 1 @Disabled printer)

- `RiskCalculatorTest` — 8 pure unit test methods (sharpe, vol, drawdown ×2, beta ×2, histVar, paramVar)
- `CorrelationCalculatorTest` — 3 tests (symmetric, diagonal=1, AAPL-MSFT golden value)
- `FamaFrenchCalculatorTest` — 4 tests (paramsLength=4, rSquaredGolden, betaMktPositive, contributionsSum)
- `CointegrationScannerTest` — 2 tests (mackinnonPValue(-3.0)≈0.034, spreadZScore formula)
- `AnalyticsControllerIntegrationTest` — 7 tests (getRisk_unauthenticated_returns401 **GREEN NOW**, + 6 RED)
- `AnalyticsGoldenValuePrinterTest` — `@Disabled` printer; prints 9 seed-derived constants after Plans 02-03

### Frontend

**`frontend/src/api/analytics.ts`** — 5 TypeScript interfaces mirroring Java records exactly:
- `VarResultDto`, `RiskScorecardDto`, `CorrelationMatrixDto`, `AttributionDto`, `PairResultDto`
- `signal` typed as `'LONG_Y_SHORT_X' | 'SHORT_Y_LONG_X' | 'NEUTRAL'` literal union

**4 RED component tests** — import from non-existent components (intended RED until Plan 04-04):
- `RiskScorecard.test.ts`, `CorrelationHeatmap.test.ts`, `AttributionChart.test.ts`, `PairsTable.test.ts`
- Each covers loading/populated/error states matching KpiCard.test.ts pattern

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] tsconfig.app.json included __tests__ in production build compilation**
- **Found during:** Task 3 verification (`npm run build`)
- **Issue:** `tsconfig.app.json` include glob `src/**/*.ts` picked up all test files including the new RED scaffolds that import non-existent Vue components, causing `vue-tsc -b` to fail
- **Fix:** Added `"exclude": ["src/**/__tests__/**", "src/**/*.test.ts", "src/**/*.spec.ts"]` to `tsconfig.app.json`. The existing `KpiCard.test.ts`, `HoldingsTable.test.ts`, `TransactionsTable.test.ts` were not affected because those components already existed — this bug was latent and only manifested when RED test files (importing non-existent components) were added
- **Files modified:** `frontend/tsconfig.app.json`
- **Commit:** `2e02546`

## Known Stubs

All four service classes are intentional stubs returning zeroed/empty DTOs. They are tracked as STUB comments and will be replaced in Plans 04-02 and 04-03:
- `RiskCalculator.computeRiskScorecard` — all zeros, `var: List.of()` — Plan 04-02
- `CorrelationCalculator.computeCorrelationMatrix` — empty tickers/matrix — Plan 04-02
- `FamaFrenchCalculator.computeAttribution` — all zeros — Plan 04-03
- `CointegrationScanner.scanPairs` — `List.of()` — Plan 04-03

Frontend component tests (RED) import non-existent Vue components — intentional until Plan 04-04.

## Threat Surface Scan

All threat mitigations from the plan's `<threat_model>` are implemented:
- T-04-01 (IDOR): `resolvePortfolioId` copied verbatim; no `@RequestParam`/`@PathVariable` — CONFIRMED
- T-04-02 (Spoofing): `getRisk_unauthenticated_returns401` test is GREEN — CONFIRMED
- T-04-03 (Modulith boundary): `allowedDependencies = {"marketdata::domain", "portfolio::domain"}` only; `QuantLensModulithTest` passes — CONFIRMED
- T-04-SC (no new packages): confirmed, no new Maven/npm packages added

No new threat surface beyond what the plan's threat model covers.

## Self-Check: PASSED

All key files confirmed present. All three task commits confirmed in git log. All content assertions confirmed:
- `findAllByOrderByFactorDateAsc` in FactorReturnRepository
- `resolvePortfolioId` in AnalyticsController
- `@Disabled` in AnalyticsGoldenValuePrinterTest
- `RiskScorecardDto` in analytics.ts
- No `portfolio::service` in analytics allowedDependencies
