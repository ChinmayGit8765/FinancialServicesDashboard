---
phase: 5
slug: stochastic-forecasting
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-08
---

# Phase 5 — Validation Strategy

> Per-phase validation contract. Correctness anchored by a hand-computed GBM analytic-mean test (HC-11), not seed-circular values.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + AssertJ; Testcontainers via `AbstractPostgresIntegrationTest` |
| **Frontend** | Vitest + @vue/test-utils |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=ForecastMathHandComputedTest,ForecastStructuralTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (frontend) `npm run test` |
| **Estimated runtime** | backend ~120–180s (MC sims); frontend <15s |

> Host: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"` before `./mvnw`.

---

## Sampling Rate

- **Per task commit:** ForecastMathHandComputedTest + ForecastStructuralTest
- **Per wave:** `.\mvnw.cmd verify` (backend) / `npm run test` (frontend)
- **Phase gate:** full backend + frontend suites green; HC-11 anchor + reproducibility pass

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| SIM-01 | p5≤p25≤p50≤p75≤p95 at every step (all 4 models); bands widen with horizon | unit structural | `ForecastStructuralTest` |
| SIM-01 | GBM mean ≈ S₀·e^(μT) ±1%, median ≈ S₀·e^((μ−σ²/2)T), median<mean (Ito guard) | unit hand-computed | `ForecastMathHandComputedTest` (HC-11) |
| SIM-01 | seed=42 → byte-identical bands across two runs (reproducibility) | unit | `ForecastStructuralTest` |
| SIM-01 | bootstrap output mean log-return within 5% of historical (moment preservation) | unit | `ForecastStructuralTest` |
| SIM-01 | finmath model instantiation + path generation runs without exception (GBM/Heston/Merton) | unit | `ForecastFinmathIntegrationTest` |
| SIM-02 | `/api/portfolio/forecast?model=` returns 200 + correct JSON shape; 4 models give distinct bands | integration | `ForecastControllerIntegrationTest` |
| SIM-02 | frontend fan chart renders 5 series; model selector changes selectedModel + refetches | component | `npm run test -- MonteCarloFanChart` |
| SIM-03 | docs/MODELS.md exists with all 4 models' rationale/assumptions/params/limitations | file | `grep -i "Heston\|Merton\|bootstrap\|GBM" docs/MODELS.md` |

---

## Wave 0 Requirements

- [ ] `net.finmath:finmath-lib:6.1.7` added to backend/pom.xml (blocks all backend compile)
- [ ] ForecastMathHandComputedTest (HC-11 GBM analytic anchor)
- [ ] ForecastStructuralTest (ordering, widening, reproducibility, bootstrap moments)
- [ ] ForecastFinmathIntegrationTest (finmath smoke — no Spring context)
- [ ] ForecastControllerIntegrationTest (extends AbstractPostgresIntegrationTest; 4 models)
- [ ] frontend MonteCarloFanChart.test.ts

---

## Manual-Only Verifications

| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Fan chart renders (p5–p95 bands + median), model selector switches shape | SIM-01/02 | visual | `docker compose up` → MC panel populated, switch models |
| MODELS.md reads well for a non-specialist | SIM-03 | editorial | read docs/MODELS.md |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 deps
- [x] Correctness anchored by hand-computed HC-11 (not circular)
- [x] Wave 0 covers finmath dep + all test scaffolds
- [x] No watch-mode flags
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-08 (wave_0_complete flips true after Plan 05-01 executes)
