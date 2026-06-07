---
phase: 03-frontend-scaffold
plan: "02"
subsystem: frontend
tags: [pinia, axios, portfolio-api, dto, vitest, tdd, 401-interceptor]
dependency_graph:
  requires:
    - "03-01: Vitest infra, @/ alias, pinia installed"
    - "02-01/02-03: Java PortfolioController + DTO records (DTO shapes verified)"
  provides:
    - "api/portfolio.ts: 6 DTO interfaces + PageResponse<T> + 5 typed fetch fns"
    - "stores/portfolio.ts: usePortfolioStore with per-resource AsyncState + refreshAll"
    - "Global 401 response interceptor in api/auth.ts (excludes /auth/me, /auth/login)"
  affects:
    - "Phase 03 Plans 03-05: all dashboard components bind to portfolioStore.*.data/loading/error"
    - "Phase 03 TopBar: calls portfolioStore.refreshAll() on persona switch"
tech_stack:
  added: []
  patterns:
    - "asyncState<T>() reactive factory with 'as AsyncState<T>' cast for vue reactive() generic unwrap"
    - "refreshVersion module-scoped counter for Promise.allSettled race guard"
    - "setActivePinia(createPinia()) + vi.mock('axios') store test pattern (NOT createTestingPinia)"
    - "401 interceptor in api/auth.ts with router.push fallback to window.location.href"
key_files:
  created:
    - frontend/src/api/portfolio.ts
    - frontend/src/stores/portfolio.ts
    - frontend/src/__tests__/portfolioStore.test.ts
  modified:
    - frontend/src/api/auth.ts
decisions:
  - "[03-02] asyncState<T>() casts return as AsyncState<T>: vue reactive() unwraps nested generic refs producing 'UnwrapRef<T>' which is incompatible with 'T | null'; cast is safe (runtime shape identical)"
  - "[03-02] 401 interceptor includes window.location.href fallback for circular-import edge case (T-03-06/A2)"
  - "[03-02] fetchTransactions uses size:10 (dashboard panel); backend default is 20; max-page-size is 500"
metrics:
  duration: "~3 minutes"
  completed: "2026-06-07"
  tasks: 2
  files: 4
---

# Phase 03 Plan 02: Portfolio Data Layer Summary

**One-liner:** Typed portfolio API module (6 DTOs + 5 fetch fns), Pinia portfolio store with per-resource AsyncState and race-safe refreshAll, and a loop-safe global 401 interceptor — all unit-tested and build-green.

## What Was Built

| Artifact | Purpose |
|----------|---------|
| `frontend/src/api/portfolio.ts` | 6 DTO interfaces verified against Java records (TransactionDto: 7 fields, no id); PageResponse<T>; 5 typed fetch fns using shared axios singleton (no interceptor re-registration) |
| `frontend/src/api/auth.ts` | Added global 401 response interceptor after CSRF request interceptor; excludes /auth/me and /auth/login; fallback to window.location.href |
| `frontend/src/stores/portfolio.ts` | Setup-function defineStore matching auth.ts convention; asyncState<T>() reactive factory; 5 fetch actions with try/catch/finally; refreshAll with Promise.allSettled + refreshVersion guard; $reset |
| `frontend/src/__tests__/portfolioStore.test.ts` | 5 behavior tests: fetchPnl success, fetchPnl 500, fetchHoldings 401, refreshAll 5× axios.get, rapid double-refreshAll no-throw |

## Task Commits

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | api/portfolio.ts + 401 interceptor | `09fac88` | api/portfolio.ts, api/auth.ts |
| 2 (RED) | portfolioStore.test.ts failing | `146b31b` | src/__tests__/portfolioStore.test.ts |
| 2 (GREEN) | stores/portfolio.ts implementation | `6dca3ce` | src/stores/portfolio.ts |

## TDD Gate Compliance

- RED gate: `test(03-02)` commit `146b31b` — tests fail (store file missing)
- GREEN gate: `feat(03-02)` commit `6dca3ce` — 5/5 tests pass
- REFACTOR gate: not needed (no structural cleanup required)

## Verification

- `npm run test` — 18/18 passing (13 format.test.ts + 5 portfolioStore.test.ts)
- `npm run build` — exits 0; vue-tsc type-check passes with `as AsyncState<T>` cast
- `portfolio.ts` grep: no `axios.defaults` or `axios.interceptors` lines (T-03-03)
- `auth.ts` contains `router.push('/login')` inside response interceptor excluding `/auth/me` and `/auth/login` (T-03-04)
- `TransactionDto` has `tradeValue` field and no `id` field (verified vs Java record)
- `refreshAll` uses `Promise.allSettled` and `refreshVersion` guard (confirmed in source)

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] TypeScript generic unwrap error: `reactive<T>()` returns `UnwrapRef<T>` not `T`**
- **Found during:** Task 2 GREEN — `npm run build` (vue-tsc)
- **Issue:** `return reactive({ data: init, ... })` infers return type as `{ data: UnwrapRef<T> | null; ... }` which is incompatible with `AsyncState<T>` for generic T (TypeScript cannot prove `UnwrapRef<T> extends T`)
- **Fix:** Added `as AsyncState<T>` cast to `asyncState<T>()` return statement — runtime shape is identical, cast is safe
- **Files modified:** `frontend/src/stores/portfolio.ts`
- **Commit:** `6dca3ce`

## Known Stubs

None — all fetch functions call real endpoints; DTO types match verified Java records.

## Threat Surface Scan

No new network endpoints introduced. The 401 interceptor is a client-side redirect only — no new trust boundaries. Threat mitigations T-03-03 through T-03-06 all applied as planned.

## Self-Check: PASSED
