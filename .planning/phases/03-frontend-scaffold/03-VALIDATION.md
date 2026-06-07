---
phase: 3
slug: frontend-scaffold
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-07
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for the Vue 3 dashboard.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | Vitest (+ @vue/test-utils + jsdom + @pinia/testing) — dev deps to add |
| **Config file** | `frontend/vitest.config.ts` (Wave 0) |
| **Quick run command** | `npm run test` (in `frontend/`) |
| **Full suite command** | `npm run test:coverage` |
| **Type/build gate** | `npm run build` (`vue-tsc -b` + `vite build`) must exit 0 |
| **Estimated runtime** | < 15s unit/component; build ~10–20s |

---

## Sampling Rate

- **After every task commit:** `npm run test` (unit/component)
- **After every wave:** `npm run build && npm run test`
- **Phase gate:** `npm run build` exits 0 + all unit/component tests green + manual screenshot checklist
- **Max feedback latency:** < 30s (excluding build)

---

## Per-Task Verification Map

| Req | Behavior | Type | Command | Status |
|-----|----------|------|---------|--------|
| UI-01 | formatCurrency/formatSignedPercent/deltaClass/formatDate (no UTC shift) | unit | `npm run test -- format` | ⬜ |
| UI-01 | portfolioStore fetch sets data/loading/error; refreshAll hits all 5 endpoints | unit | `npm run test -- portfolioStore` | ⬜ |
| UI-01 | KpiCard loading-skeleton vs populated | component | `npm run test -- KpiCard` | ⬜ |
| UI-01 | HoldingsTable empty-state vs populated row count | component | `npm run test -- HoldingsTable` | ⬜ |
| UI-01 | TransactionsTable BUY/SELL badge classes | component | `npm run test -- TransactionsTable` | ⬜ |
| UI-01 | full type-check + bundle | build | `npm run build` | ⬜ |
| AUTH-03 | README "OAuth Upgrade Path" section exists | manual/file | `grep -i "OAuth Upgrade" README.md` | ⬜ |

---

## Wave 0 Requirements

- [ ] `frontend/vitest.config.ts` (jsdom env), `frontend/src/__tests__/setup.ts` (ResizeObserver mock)
- [ ] `frontend/src/__tests__/format.test.ts`
- [ ] `frontend/src/__tests__/portfolioStore.test.ts`
- [ ] `frontend/src/__tests__/components/{KpiCard,HoldingsTable,TransactionsTable}.test.ts`
- [ ] `package.json` test scripts (`test`, `test:coverage`)

---

## Manual-Only Verifications (screenshot-ready checklist)

Verified manually (also drives README screenshots) — the 14-point list in 03-RESEARCH.md, key items:
- Dark theme renders (canvas `#0b0f1a`, cards `#1e293b`); P&L equity curve (504 pts), benchmark dual-line both at 100, allocation donut+treemap toggle.
- Holdings (5 rows, green/red P&L), transactions paginated with BUY/SELL badges, KPI strip + Phase-4/5/6 placeholder stubs.
- Persona switch Alice→Bob re-scopes without full reload; rapid-switch shows no data-mixing; error states on backend down; logout; session persists on F5.
- README OAuth upgrade-path section present (AUTH-03).

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 deps (build gate + Vitest)
- [x] Sampling continuity maintained
- [x] Wave 0 covers all test infra
- [x] No watch-mode flags (CI `vitest run`)
- [x] Feedback latency < 30s
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-07
