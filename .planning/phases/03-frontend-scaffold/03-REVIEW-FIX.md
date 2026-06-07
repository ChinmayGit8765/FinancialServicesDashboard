---
phase: 03-frontend-scaffold
fixed_at: 2026-06-07T16:33:50Z
review_path: .planning/phases/03-frontend-scaffold/03-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 3: Code Review Fix Report

**Fixed at:** 2026-06-07T16:33:50Z
**Source review:** .planning/phases/03-frontend-scaffold/03-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 8 (CR-01, CR-02, CR-03, WR-01, WR-02, WR-03, WR-04, WR-05)
- Fixed: 8
- Skipped: 0

Build gate: `npm run build` exits 0 (vue-tsc -b + vite build, 715 modules)
Test gate: `npm run test` — 35 tests passed across 5 test files

---

## Fixed Issues

### CR-01: refreshVersion Race Guard Does Not Prevent Stale Store Writes

**Files modified:** `frontend/src/stores/portfolio.ts`, `frontend/src/__tests__/portfolioStore.test.ts`
**Commit:** 107663a
**Applied fix:** Each sub-fetch (`fetchHoldings`, `fetchPnl`, `fetchAllocation`, `fetchTransactions`, `fetchBenchmark`) now accepts an optional `version?: number` parameter. After the awaited axios call resolves, it checks `if (version !== undefined && version !== refreshVersion) return` before writing to the store — both on the success and error paths. `refreshAll()` captures `myVersion = ++refreshVersion` and passes it to each sub-fetch. The `finally` block always clears `loading` regardless of version staleness to prevent stuck spinners.

The prior code checked version only _after_ `Promise.allSettled` — after all writes had already occurred — making the guard completely inert.

The existing race test (`rapid double refreshAll: ...`) was rewritten into `CR-01 race guard: stale first-batch pnl does not overwrite second-batch pnl data`. The new test uses distinct `personaAPnl` and `personaBPnl` payloads, holds persona A's pnl fetch open with a controlled Promise, lets batch B resolve first, then resolves A's slow fetch and asserts the store still holds persona B's `totalMarketValue: 999999.99` — not A's `111111.11`.

**Note:** This test required genuine async ordering care. The fix is classified as `fixed: requires human verification` for the logic path — the test asserts the corrected behavior directly and the fix is mechanically sound, but the async interleaving is subtle enough to warrant a human review pass.

---

### CR-02: KpiCard Delta Renders Sign-Color Contradiction for Near-Zero Negative Values

**Files modified:** `frontend/src/components/KpiCard.vue`
**Commit:** 4c18fb1
**Applied fix:** Introduced `effectiveDelta` computed that snaps values where `Math.abs(delta) < 0.005` to `0`. Both `deltaColorClass` and `borderClass` now use `effectiveDelta.value` so a `-0.003` delta shows `kpi-flat` / `border-flat` and displays `0.00%` — sign and color agree. A `deltaFormatted` computed pre-builds the display string (`+2.34%`, `-1.12%`, `0.00%`) removing inline template arithmetic. Template now renders `{{ deltaFormatted }}`.

---

### CR-03: axios Interceptors Registered at Module Evaluation — Double-Registration on HMR

**Files modified:** `frontend/src/api/auth.ts`
**Commit:** 3eeabc9
**Applied fix:** Wrapped both the CSRF request interceptor and the 401 response interceptor in a `globalThis.__axiosInterceptorsRegistered__` flag guard. The interceptors now register only once per browser tab lifetime. Subsequent Vite HMR re-evaluations of `auth.ts` skip the registration block. `axios.defaults.withCredentials = true` remains outside the guard (setting it repeatedly is idempotent and harmless). Behavior on first load (production and initial dev) is identical to before.

---

### WR-01: SignedValue :value vs :formatted Scale Mismatch in HoldingsTable

**Files modified:** `frontend/src/components/HoldingsTable.vue`
**Commit:** ce38af8
**Applied fix:** Changed `:value="h.unrealizedPnlPct"` to `:value="h.unrealizedPnlPct * 100"` in the P&L % column `<SignedValue>`. Both `:value` and `:formatted` now use the same percent-units scale (already-multiplied-by-100). The `deltaClass` sign test on `:value` is numerically correct for both scales (sign is unchanged by the multiplication), but consistency prevents future double-multiply bugs when copying the pattern.

---

### WR-02: Array Index :key in TransactionsTable

**Files modified:** `frontend/src/components/TransactionsTable.vue`
**Commit:** 09b39ae
**Applied fix:** Replaced `v-for="(tx, idx) in page.content" :key="idx"` with `v-for="tx in page.content" :key="\`${tx.txDate}-${tx.txType}-${tx.ticker}-${tx.quantity}-${tx.tradeValue}\`"`. The composite key is stable across page navigation — Vue will correctly create/destroy DOM nodes rather than patching existing ones when the user navigates between pages.

---

### WR-03: Retry Button Emits Page 0 Instead of Current Page

**Files modified:** `frontend/src/components/TransactionsTable.vue`
**Commit:** 09b39ae
**Applied fix:** Changed `@click="$emit('page-change', 0)"` to `@click="emit('page-change', props.page?.number ?? 0)"`. Now emits the page the user was on when the error occurred. Also switches from template-implicit `$emit` to the script-level `emit` reference for consistency with the rest of the component.

---

### WR-04: SlotPlaceholder minHeight Not Reactive

**Files modified:** `frontend/src/components/SlotPlaceholder.vue`
**Commit:** ad2d0ea
**Applied fix:** Added `import { computed } from 'vue'` and changed `const height = props.minHeight ?? '240px'` to `const height = computed(() => props.minHeight ?? '240px')`. The style binding `:style="{ minHeight: height }"` now auto-unwraps the computed ref and reacts to prop changes.

---

### WR-05: Pagination Shows "Page 1 of 0" on Empty Result

**Files modified:** `frontend/src/components/TransactionsTable.vue`
**Commit:** 09b39ae
**Applied fix:** Added `&& page.totalPages > 0` to the pagination `v-if` condition. The pagination div is now hidden when the backend returns `totalPages: 0` (empty portfolio). The empty-state row renders instead.

---

### IN-02 + IN-03: Redundant echarts Import, Missing void on refreshAll

**Files modified:** `frontend/src/plugins/echarts.ts`, `frontend/src/views/DashboardView.vue`
**Commit:** 92d8798
**Applied fix:**
- IN-02: Removed `import { use } from 'echarts/core'` — `use` is accessible as `echarts.use` via the existing namespace import. Updated call site to `echarts.use([...])`.
- IN-03: Added `void` operator before `portfolioStore.refreshAll()` in `onMounted` to make the intentional fire-and-forget explicit.

---

## Skipped Issues

None — all 8 in-scope findings were fixed.

---

_Fixed: 2026-06-07T16:33:50Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
