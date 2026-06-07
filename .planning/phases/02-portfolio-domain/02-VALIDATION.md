---
phase: 2
slug: portfolio-domain
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-07
---

# Phase 2 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (Phase 1 wired); Testcontainers pgvector via `AbstractPostgresIntegrationTest` |
| **Config file** | `backend/src/test/resources/application-test.yml` (exists) |
| **Quick run command** | `.\mvnw.cmd -q test -Dtest=PortfolioServiceTest` (JAVA_HOME=Temurin 21 first) |
| **Full suite command** | `.\mvnw.cmd verify` |
| **Estimated runtime** | ~60–120s (Testcontainers) |

> Host: `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"` before `./mvnw`.

---

## Sampling Rate

- **After every task commit:** `.\mvnw.cmd -q test -Dtest=PortfolioServiceTest` (pure math, fast)
- **After every plan wave:** `.\mvnw.cmd verify`
- **Before verify:** full suite green
- **Max feedback latency:** ~120s

---

## Per-Task Verification Map

| Req ID | Behavior | Test Type | Automated Command | Status |
|--------|----------|-----------|-------------------|--------|
| PORT-01 | holdings: 5 positions w/ ticker/sector/qty/cost/price/pnl/weight (alice) | integration | `PortfolioControllerIntegrationTest#getHoldings_alice_returns5Holdings` | ⬜ |
| PORT-01 | weights sum to 1.000000 | unit | `PortfolioServiceTest#allocationWeightsSumToOne` | ⬜ |
| PORT-01 | unrealized P&L = (close − avgCost)·qty | unit | `PortfolioServiceTest#unrealizedPnlFormula` | ⬜ |
| PORT-02 | equity curve = 504 entries; start 2022-09-12 | integration | `PortfolioControllerIntegrationTest#getPnl_*` | ⬜ |
| PORT-02 | daily change = curve[last]−curve[last-1]; total gain = mktVal−costBasis | unit | `PortfolioServiceTest#dailyChange*,totalUnrealizedGainFormula` | ⬜ |
| PORT-03 | allocation slices cover portfolio sectors; weights sum to 1 | integration | `PortfolioControllerIntegrationTest#getAllocation_*` | ⬜ |
| PORT-04 | transactions paginated, most-recent-first; running cost basis correct | integration+unit | `PortfolioControllerIntegrationTest#getTransactions_*`, `PortfolioServiceTest#runningCostBasis*` | ⬜ |
| PORT-05 | portfolio vs benchmark both rebased to 100 day-0; series same length, dates ascending | unit+integration | `PortfolioServiceTest#benchmarkBothSeriesStartAt100`, `PortfolioControllerIntegrationTest#getBenchmark_*` | ⬜ |

*Status: ⬜ pending · ✅ green · ❌ red*

---

## Wave 0 Requirements

- [ ] `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` — unit tests for pure computation (P&L, weights, daily change, running cost basis, benchmark rebasing)
- [ ] `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` — `@SpringBootTest(RANDOM_PORT)` + `AbstractPostgresIntegrationTest`, authenticated calls as alice
- [ ] One-time `@Disabled` golden-value printer test to capture exact seed-derived constants, then bake them into assertions

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Endpoints return chart-ready JSON usable by Phase 3 ECharts | PORT-01..05 | Visual binding is Phase 3 | `curl -b cookie.txt http://localhost:8080/api/portfolio/holdings` returns expected shape |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity maintained
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-07
