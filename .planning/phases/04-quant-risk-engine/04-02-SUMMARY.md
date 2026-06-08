---
phase: 04-quant-risk-engine
plan: 02
subsystem: analytics
tags: [risk, var, correlation, hipparchus, golden-values, modulith]
dependency_graph:
  requires: [04-01]
  provides: [RISK-01, RISK-02, RISK-03]
  affects: [analytics-controller, frontend-charts]
tech_stack:
  added: [analytics.service.DateValueDto (local record to satisfy Modulith boundary)]
  patterns: [golden-value-printer, log-returns, hipparchus-descriptive-statistics, pearson-correlation]
key_files:
  created:
    - backend/src/main/java/com/quantlens/analytics/service/DateValueDto.java
  modified:
    - backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java
    - backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java
    - backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java
    - backend/src/test/java/com/quantlens/analytics/CorrelationCalculatorTest.java
    - backend/src/test/java/com/quantlens/analytics/AnalyticsControllerIntegrationTest.java
    - backend/src/test/java/com/quantlens/analytics/AnalyticsGoldenValuePrinterTest.java
decisions:
  - "Local analytics.service.DateValueDto record created to keep analytics module within portfolio::domain Modulith boundary (portfolio.api not permitted)"
  - "GBM seed uses shared market factor only (no idiosyncratic noise) — all-1.0 Pearson correlation is mathematically correct, not a code bug; GOLDEN_CORR_AAPL_MSFT=1.0"
  - "RiskCalculatorTest and CorrelationCalculatorTest promoted from pure-unit stubs (0.0 placeholder assertions) to integration tests extending AbstractPostgresIntegrationTest — required to execute real equity-curve computation against seeded DB"
  - "RF alignment: factorRows.get(i+1).getRf() aligns with return[i] (factor for day i+1 pairs with log return from day i to i+1); fallback to rf=0 if factor count != returns+1"
  - "CVaR_HISTORICAL added as optional 3rd VaR entry (mean of returns <= p5) per plan bonus criterion"
metrics:
  duration_minutes: 95
  completed_date: "2026-06-08"
  tasks_completed: 2
  files_changed: 7
---

# Phase 04 Plan 02: RiskCalculator + CorrelationCalculator Summary

**One-liner:** Hipparchus-powered Sharpe/vol/drawdown/beta + dual VaR (historical + parametric) + Pearson correlation matrix — all golden-tested against deterministic GBM seed (seed=42, 2022-09-12).

## What Was Built

### Task 1: RiskCalculator — scorecard + dual VaR

`RiskCalculator.computeRiskScorecard(Long portfolioId)` computes six metrics over alice's seeded equity curve:

- **Sharpe ratio**: mean(daily excess log returns) / std(log returns) × √252; RF from FactorReturn aligned at i+1
- **Annualized volatility**: std(log returns) × √252 (sample std, n-1)
- **Max drawdown**: most-negative peak-to-trough on equity curve, in [-1,0]
- **Beta vs SPX500**: cov(portfolio, benchmark) / var(benchmark) via Hipparchus Covariance
- **Historical VaR (95%, 1-day)**: −getPercentile(5.0) × portfolioValue, positive BigDecimal
- **Parametric Gaussian VaR (95%, 1-day)**: (1.645 × σ − μ) × portfolioValue, positive BigDecimal
- **CVaR_HISTORICAL** (bonus): mean of returns ≤ p5 × portfolioValue

Golden values captured (seed=42, SERIES_START=2022-09-12, alice Growth Portfolio):

| Metric | Golden Value |
|--------|-------------|
| SHARPE | 0.36442669 |
| ANNUAL_VOL | 0.34621361 |
| MAX_DRAWDOWN | −0.33891522 |
| BETA | 1.94917921 |
| HIST_VAR_AMOUNT | $1,464.52 |

### Task 2: CorrelationCalculator — Pearson matrix

`CorrelationCalculator.computeCorrelationMatrix(Long portfolioId)` computes:

- All holdings with quantity > 0, sorted by ticker ASC for stable column order
- Single-query bar fetch via `findAllBySecurityIdsOrdered`; WR-02 date intersection for aligned series
- `double[nDays][nSecurities]` log-return matrix → `new PearsonsCorrelation(matrix).getCorrelationMatrix()`
- Diagonal forced to exactly 1.0; identity matrix fallback for < 2 common dates
- Returns `CorrelationMatrixDto(tickers, 5×5 matrix)` for alice

Golden value: CORR_AAPL_MSFT = 1.0 (GBM seed shares market factor only — all returns perfectly linearly dependent).

## Test Results

Full `mvnw.cmd verify` (Java 21, JAVA_HOME explicit):

| Test Class | Run | Fail | Skip | Status |
|-----------|-----|------|------|--------|
| AnalyticsControllerIntegrationTest | 7 | 0 | 0 | GREEN |
| AnalyticsGoldenValuePrinterTest | 1 | 0 | 1 | SKIPPED (@Disabled) |
| CorrelationCalculatorTest | 4 | 0 | 0 | GREEN |
| RiskCalculatorTest | 10 | 0 | 0 | GREEN |
| QuantLensModulithTest | 1 | 0 | 0 | GREEN |
| FamaFrenchCalculatorTest | 4 | 1 | 0 | RED (pre-existing 04-03 scaffold) |
| All others | 40 | 0 | 1 | GREEN |

**Total:** 67 run, 1 pre-existing failure (FamaFrenchCalculatorTest.betaMkt_aliceIsPositive — RED scaffold for Plan 04-03, not a regression), 2 skipped.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Fixed Spring Modulith cross-module boundary violation**
- **Found during:** Full `verify` run — `QuantLensModulithTest` failed with `Violations: analytics depends on portfolio via DateValueDto. Allowed targets: portfolio::domain.`
- **Issue:** `RiskCalculator` and `CorrelationCalculator` imported `com.quantlens.portfolio.api.DateValueDto` but the `analytics` module `package-info.java` only permits `portfolio::domain`, not `portfolio::api`
- **Fix:** Created `analytics.service.DateValueDto` (package-private record, same fields) and removed the cross-module import from both calculators
- **Files modified:** `analytics/service/DateValueDto.java` (new), `RiskCalculator.java`, `CorrelationCalculator.java`
- **Commit:** 8486496

**2. [Rule 1 - Bug] Promoted test architecture from stub to integration tests**
- **Found during:** Test execution — original RED scaffold had `double actualSharpe = 0.0; // FILL` with no Spring context
- **Issue:** Tests could not invoke real computation without Testcontainers + Spring Boot context
- **Fix:** Rewrote `RiskCalculatorTest` and `CorrelationCalculatorTest` as integration tests extending `AbstractPostgresIntegrationTest`, with `@BeforeEach` resolving alice's portfolio and invoking real calculators
- **Files modified:** `RiskCalculatorTest.java`, `CorrelationCalculatorTest.java`

**3. [Rule 1 - Diagnostic] GBM correlation all-1.0 investigated and confirmed correct**
- **Found during:** CorrelationCalculator development — Pearson matrix printed 1.0 everywhere
- **Investigation:** Debug confirmed 5 distinct security IDs `[1, 2, 3, 4, 15]` with distinct return column values; root cause is GBM seed driving all securities with a common market factor and zero idiosyncratic noise → returns are perfectly proportional → Pearson = 1.0 by mathematical definition
- **Resolution:** GOLDEN_CORR_AAPL_MSFT = 1.0 (correct); documented in CorrelationCalculatorTest Javadoc

## Known Stubs

None — all plan artifacts produce real computed values. The pre-existing `FamaFrenchCalculatorTest` stubs (0.0 // FILL) are out-of-scope for Plan 04-03.

## Threat Surface Scan

No new network endpoints or auth paths introduced. `DateValueDto` (local analytics copy) contains no PII, only LocalDate + BigDecimal. IDOR protection is unchanged — portfolioId is resolved from principal in `AnalyticsController.resolvePortfolioId` (Plan 04-01); calculators accept a pre-scoped Long, no request param bypasses auth (T-04-01 mitigated).

## Self-Check: PASSED

- `backend/src/main/java/com/quantlens/analytics/service/RiskCalculator.java` — FOUND
- `backend/src/main/java/com/quantlens/analytics/service/CorrelationCalculator.java` — FOUND
- `backend/src/main/java/com/quantlens/analytics/service/DateValueDto.java` — FOUND
- `backend/src/test/java/com/quantlens/analytics/RiskCalculatorTest.java` — FOUND
- `backend/src/test/java/com/quantlens/analytics/CorrelationCalculatorTest.java` — FOUND
- Commit 8486496 (RiskCalculator task) — FOUND
- Commit 4b46276 (CorrelationCalculator task) — FOUND
- 21 targeted tests (RiskCalculatorTest+CorrelationCalculatorTest+AnalyticsControllerIntegrationTest): 0 failures
- QuantLensModulithTest: PASSED (1/1) after Modulith fix
