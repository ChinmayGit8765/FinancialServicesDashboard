---
phase: 4
slug: quant-risk-engine
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-08
---

# Phase 4 — Validation Strategy

> Per-phase validation contract. Golden-value correctness is the defining gate of this phase.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + Spring Boot Test; Testcontainers via `AbstractPostgresIntegrationTest` |
| **Frontend** | Vitest + @vue/test-utils (Phase 3) |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=RiskCalculatorTest,FamaFrenchCalculatorTest,CorrelationCalculatorTest,CointegrationScannerTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (in frontend/) `npm run test` |
| **Estimated runtime** | backend ~90–150s; frontend <15s |

> Host: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"` before `./mvnw`.

---

## Sampling Rate

- **Per task commit:** the relevant calculator unit test (fast, no Spring context)
- **Per wave:** `.\mvnw.cmd verify` (backend) / `npm run test` (frontend)
- **Phase gate:** full backend suite + frontend tests green; all golden-value tests pass before verify

---

## Per-Req Verification Map (golden-value driven)

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| RISK-01 | Sharpe (log, 252) / annualized vol / max drawdown / beta = golden ±tol | unit | `RiskCalculatorTest#*GoldenValue` |
| RISK-03 | Historical + Parametric VaR > 0, = golden ±0.01, labelled method/confidence/horizon | unit+integration | `RiskCalculatorTest#*Var*`, `AnalyticsControllerIntegrationTest#risk_endpoint_*` |
| RISK-02 | Correlation matrix symmetric, diag=1, AAPL–MSFT = golden ±0.001 | unit | `CorrelationCalculatorTest#*` |
| ATTR-01 | FF OLS params.length=4 (intercept+3β), R²=golden, contributions sum to excess return ±0.001 | unit | `FamaFrenchCalculatorTest#*` |
| ARB-01 | ADF p-value(mackinnonp) matches ref ±0.01; spread Z-score formula; /pairs fields tickerY/tickerX/pValue/zScore/signal | unit+integration | `CointegrationScannerTest#*`, `AnalyticsControllerIntegrationTest#pairs_*` |
| RISK/ATTR/ARB | endpoints `/risk`,`/correlation`,`/attribution`,`/pairs` return 200 for alice w/ required fields | integration | `AnalyticsControllerIntegrationTest#*` |
| RISK-02/ATTR/ARB | frontend panels render loading/empty/populated | component | `npm run test -- RiskScorecard|CorrelationHeatmap|AttributionChart|PairsTable` |

---

## Wave 0 Requirements

Backend (com.quantlens.analytics test pkg):
- [ ] RiskCalculatorTest, FamaFrenchCalculatorTest, CorrelationCalculatorTest, CointegrationScannerTest (RED golden-value scaffolds)
- [ ] AnalyticsGoldenValuePrinterTest (`@Disabled` one-time printer to capture seed-derived expected values)
- [ ] AnalyticsControllerIntegrationTest (extends AbstractPostgresIntegrationTest)

Frontend:
- [ ] RiskScorecard.test.ts, CorrelationHeatmap.test.ts, AttributionChart.test.ts, PairsTable.test.ts

---

## Manual-Only Verifications

| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Heatmap colors (−1 blue → +1 red), attribution bar, pairs table render in dark theme | RISK-02/ATTR/ARB | visual | `docker compose up` → dashboard panels populated, no console errors |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 deps
- [x] Sampling continuity maintained
- [x] Wave 0 covers all golden-value + integration tests
- [x] No watch-mode flags
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-08 (wave_0_complete flips true after Plan 04-01 executes)
