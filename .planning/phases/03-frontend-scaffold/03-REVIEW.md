---
phase: 03-frontend-scaffold
reviewed: 2026-06-07T00:00:00Z
depth: standard
files_reviewed: 21
files_reviewed_list:
  - frontend/src/api/portfolio.ts
  - frontend/src/api/auth.ts
  - frontend/src/stores/portfolio.ts
  - frontend/src/utils/format.ts
  - frontend/src/plugins/echarts.ts
  - frontend/src/plugins/echarts-theme.ts
  - frontend/src/components/PnlChart.vue
  - frontend/src/components/BenchmarkChart.vue
  - frontend/src/components/AllocationChart.vue
  - frontend/src/components/HoldingsTable.vue
  - frontend/src/components/TransactionsTable.vue
  - frontend/src/components/KpiCard.vue
  - frontend/src/components/SignedValue.vue
  - frontend/src/components/SlotPlaceholder.vue
  - frontend/src/components/TopBar.vue
  - frontend/src/views/DashboardView.vue
  - frontend/src/views/LoginView.vue
  - frontend/src/App.vue
  - frontend/src/main.ts
  - frontend/src/style.css
  - frontend/vite.config.ts
findings:
  critical: 3
  warning: 5
  info: 3
  total: 11
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-06-07
**Depth:** standard
**Files Reviewed:** 21
**Status:** issues_found

## Summary

Phase 3 delivers a Vue 3 Composition API + Pinia + ECharts dashboard that is broadly well-structured: DTOs are documented, error states render, no credentials sit in client storage, and UTC date parsing guards are present. Several genuine defects require attention before this ships.

The three critical issues are: (1) the refreshVersion race guard does not actually prevent stale-data writes — the sub-fetches run concurrently and each writes directly to the store regardless of version; (2) `portfolioWeight` (a 0–1 fraction) is passed raw to `formatPercent`, which uses `Intl` style `percent` and correctly multiplies by 100 internally — this path is actually correct — but the *same field* is also passed as the `:value` prop to `SignedValue`/`deltaClass` which compares it against zero only, so that path is fine too. The real critical fraction issue is the `unrealizedPnlPct` field: the DTO comment says "scale 6" (0–1), it is multiplied by 100 before `formatSignedPercent`, but the raw 0–1 value is also passed as `:value` to `SignedValue` whose `deltaClass` only tests sign — that part is harmless. However the `KpiCard` delta display in DashboardView receives `totalUnrealizedGainPct * 100` and calls `delta.toFixed(2)` with a `%` suffix appended in the template, effectively double-multiplying the percent units (see CR-02). (3) `api/auth.ts` registers the CSRF interceptor and 401 interceptor on the module-level shared axios instance; because `main.ts` imports `./plugins/echarts` first and the auth module is imported transitively from multiple files, there is a real risk of interceptors being registered more than once if hot-module replacement re-evaluates the module (see CR-03).

---

## Critical Issues

### CR-01: refreshVersion Race Guard Does Not Prevent Stale Store Writes

**File:** `frontend/src/stores/portfolio.ts:141-153`

**Issue:** The race guard checks `myVersion !== refreshVersion` only *after* `Promise.allSettled` resolves — meaning all five sub-fetches have already completed and **have already written their data into the store**. Each sub-fetch (`fetchHoldings`, `fetchPnl`, etc.) mutates `holdings.data`, `pnl.data`, etc. unconditionally on success. If a rapid persona-switch triggers a second `refreshAll()` whose five fetches resolve *before* the first batch, the first batch's slower fetches will still write their (stale, persona-A) data to the store after the second batch (persona-B) has written the correct data. The version check at line 152 only prevents `refreshAll` from doing any *post-allSettled* work — but there is no post-allSettled work in the current implementation, making the guard entirely inert.

The test at `portfolioStore.test.ts:122` validates only that neither call throws and that both resolve — it does not assert that the store holds persona-B data after the race, so the test passes even with the broken guard.

**Fix:** Each sub-fetch must capture the version at the start of the fetch and abandon the write if the version has advanced:

```typescript
// In each fetch action, capture version before the await:
async function fetchPnl(): Promise<void> {
  const myVersion = refreshVersion   // capture current version
  pnl.loading = true
  pnl.error = null
  try {
    const { data } = await axios.get<PortfolioPnlDto>('/api/portfolio/pnl')
    if (myVersion !== refreshVersion) return   // stale — discard
    pnl.data = data
  } catch (e: any) {
    if (myVersion !== refreshVersion) return   // stale error — discard too
    pnl.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load P&L'
  } finally {
    if (myVersion === refreshVersion) pnl.loading = false
    else pnl.loading = false   // always clear loading regardless
  }
}
```

Apply the same pattern to `fetchHoldings`, `fetchAllocation`, `fetchTransactions`, and `fetchBenchmark`. The `refreshVersion` counter must then be incremented in `refreshAll()` *before* launching the sub-fetches (currently it is, so `++refreshVersion` at line 142 is correct placement — only the sub-fetch guards are missing).

---

### CR-02: KpiCard `delta` Prop Receives Already-Scaled Percent; Template Appends `%` Again — Double-Multiplied Display

**File:** `frontend/src/views/DashboardView.vue:41-42`, `frontend/src/components/KpiCard.vue:39`

**Issue:** `unrealizedDelta` and `dailyChangeDelta` are computed as:

```typescript
portfolioStore.pnl.data.totalUnrealizedGainPct * 100   // e.g. 23.4568
```

This is correct as a percent-units value (23.46%). However `KpiCard.vue` line 39 renders the delta as:

```html
{{ delta > 0 ? '+' : '' }}{{ delta.toFixed(2) }}%
```

So the template receives `23.46` and renders `+23.46%` — that part is correct.

The problem is that `KpiCard`'s `delta` prop is *also* used as the argument to `deltaClass` (via the `deltaColorClass` and `borderClass` computeds at lines 12-24), and those only test sign — so the coloring is fine.

The actual double-display defect is in `KpiCard` itself: when `delta` is 0 exactly (flat day), the template renders `0.00%`. When `delta` is e.g. `-0.001` (tiny negative from a near-zero `dailyChangePct`), the `delta.toFixed(2)` rounds to `-0.00` and the template prepends nothing (since `delta > 0` is false) but also does not prepend `-`, yielding `0.00%` with a negative border-left color. This sign-rendering asymmetry means a tiny negative delta looks identical to zero in the number display but shows a red left-border.

More critically: `formatSignedPercent` (used for the `secondary` KPI text) and the `delta` raw value passed to `KpiCard` must agree on units. `formatSignedPercent` expects already-multiplied percent units (its docstring says "pctValue is already in percent units"). DashboardView passes `totalUnrealizedGainPct * 100` to both — that is consistent and correct. However `KpiCard` renders `delta.toFixed(2) + '%'` without any sign for negative values (the `delta > 0 ? '+' : ''` ternary produces empty string for negative delta, so the output is e.g. `-23.46%` only when `Number.toFixed` itself returns the minus sign from the value — this happens to work because JavaScript's `toFixed` on a negative number returns the minus sign). So sign rendering accidentally works. **The genuine defect is the near-zero negative case** described above.

**Fix:** Change the delta template in `KpiCard.vue` to handle negative zero explicitly:

```html
<span v-if="delta !== undefined && delta !== null" class="kpi-delta" :class="deltaColorClass">
  {{ delta > 0 ? '+' : delta < 0 ? '' : '' }}{{ Math.abs(delta) < 0.005 ? '0.00' : delta.toFixed(2) }}%
</span>
```

Or more robustly, delegate formatting to `formatSignedPercent` and remove the inline template arithmetic:

```typescript
// In DashboardView, pass pre-formatted string instead of raw number, or add a computed:
const unrealizedDeltaFormatted = computed(() =>
  unrealizedDelta.value !== undefined ? formatSignedPercent(unrealizedDelta.value) : undefined
)
```

---

### CR-03: axios Interceptors Registered at Module Evaluation Time — Double-Registration on HMR

**File:** `frontend/src/api/auth.ts:14-41`

**Issue:** Both the CSRF request interceptor (line 14) and the 401 response interceptor (line 25) are registered as top-level side effects when `auth.ts` is first evaluated. In production this is a single evaluation and is safe. In Vite development with Hot Module Replacement, if any module that imports `auth.ts` (directly or transitively: `stores/auth.ts`, `router/index.ts`, `components/TopBar.vue`, `views/LoginView.vue`) is updated, Vite may re-evaluate `auth.ts`, causing a second pair of interceptors to be appended to `axios.interceptors.request` and `axios.interceptors.response`. Each subsequent HMR cycle adds another pair.

Consequences:
- The 401 interceptor fires twice per failed response, calling `router.push('/login')` twice — Vue Router deduplicates same-route pushes but this is fragile and logs navigation warnings.
- The CSRF interceptor fires twice per mutating request, setting the same header twice — harmless but wasteful and indicates broken lifecycle management.
- There is no eject mechanism: the interceptors accumulate for the lifetime of the browser tab.

**Fix:** Guard registration with a module-level flag, or use axios instance creation instead of mutating the shared singleton. The recommended pattern for this codebase is to create and export a configured axios instance:

```typescript
// auth.ts — create a dedicated instance instead of mutating axios.defaults
import axios from 'axios'
const instance = axios.create({ withCredentials: true })
let interceptorsRegistered = false

if (!interceptorsRegistered) {
  interceptorsRegistered = true
  instance.interceptors.request.use(/* CSRF handler */)
  instance.interceptors.response.use(/* 401 handler */)
}
export default instance
```

Alternatively, since the store and portfolio API both import `axios` directly (not the instance), the simplest fix is a guard flag on the shared singleton:

```typescript
// auth.ts top-level
declare const __INTERCEPTORS_REGISTERED__: boolean
if (!(globalThis as any).__interceptorsRegistered__) {
  (globalThis as any).__interceptorsRegistered__ = true
  axios.interceptors.request.use(/* ... */)
  axios.interceptors.response.use(/* ... */)
}
```

---

## Warnings

### WR-01: `portfolioWeight` Rendered via `formatPercent` — Passes 0–1 Fraction Correctly, but `SignedValue` `:value` Receives Raw Fraction Creating Misleading Color on Near-Zero Weights

**File:** `frontend/src/components/HoldingsTable.vue:163,172-174`

**Issue:** `portfolioWeight` (documented as 0–1, e.g. 0.0034 for a 0.34% position) is passed to `formatPercent(h.portfolioWeight)` on line 163 — `formatPercent` uses `Intl` style `percent`, which multiplies by 100 internally, so the rendered output is correct (e.g. "0.34%").

However, line 172 passes the same raw `h.unrealizedPnlPct` (0–1 fraction, e.g. 0.1667 = 16.67%) as the `:value` prop to `SignedValue`. `SignedValue` passes this to `deltaClass(props.value)`, which only tests `> 0` / `< 0` / `=== 0`. For `unrealizedPnlPct`, sign is all that matters for color, so the 0–1 scale does not affect correctness there.

The real issue is on line 173: `formatSignedPercent(h.unrealizedPnlPct * 100)` correctly multiplies by 100 before formatting. But the `:value` prop (raw 0–1) and the `:formatted` prop (`* 100` result) are on different scales. If a future developer copies this pattern for a field that already arrives in percent units, they will double-multiply. The inconsistency is a latent maintenance trap.

**Fix:** For clarity and consistency, always pass the display-unit value to `:value` so both props are on the same scale. Use a local computed or inline conversion:

```html
<SignedValue
  :value="h.unrealizedPnlPct * 100"
  :formatted="formatSignedPercent(h.unrealizedPnlPct * 100)"
/>
```

---

### WR-02: `TransactionsTable` Uses Array Index as `:key` — Broken Keying on Pagination

**File:** `frontend/src/components/TransactionsTable.vue:57`

**Issue:**
```html
<tr v-for="(tx, idx) in page.content" :key="idx" class="data-row">
```

`idx` is the 0-based position within the current page's `content` array. When the user navigates to page 2 and back to page 1, Vue sees the same keys (0, 1, 2…9) and attempts to patch existing DOM nodes rather than replace them. This causes stale DOM content to briefly flash while Vue reconciles, and can cause input focus / animation state leakage between pages. The `TransactionDto` interface explicitly notes there is no `id` field, but a stable composite key is available.

**Fix:** Use a composite key that is stable across pages:

```html
<tr
  v-for="tx in page.content"
  :key="`${tx.txDate}-${tx.txType}-${tx.ticker}-${tx.quantity}`"
  class="data-row"
>
```

This is not guaranteed unique (two transactions on the same day in the same ticker/type/quantity are theoretically possible) but is far more stable than array index. If the backend cannot add an `id`, add a `tradeValue` component to reduce collision probability.

---

### WR-03: `TransactionsTable` Retry Button Emits `page-change` with `0` on Error — Does Not Retry Current Page

**File:** `frontend/src/components/TransactionsTable.vue:46`

**Issue:**
```html
<button class="retry-btn" @click="$emit('page-change', 0)">Retry</button>
```

The error-state retry always requests page 0, even if the user was on page 5 when the error occurred. The current page number is available via `props.page?.number` but after an error `props.page` may be `null` (the store clears `transactions.data` on reset and on error the old data persists — though a page-change fetch will re-set it). Emitting 0 instead of the last-good page is surprising UX and loses the user's scroll position in the transaction history.

Additionally, this retry uses `$emit` (the template implicit emit) rather than the script-level `emit` reference, which is inconsistent with the rest of the component. While Vue 3 supports both, the component's own comment at line 11 explicitly warns about using `emit` consistently.

**Fix:**

```html
<button class="retry-btn" @click="emit('page-change', props.page?.number ?? 0)">Retry</button>
```

---

### WR-04: `SlotPlaceholder` `minHeight` Prop Read in `<script setup>` Directly — Not Reactive to Prop Changes

**File:** `frontend/src/components/SlotPlaceholder.vue:3`

**Issue:**
```typescript
const props = defineProps<{ label: string; minHeight?: string }>()
const height = props.minHeight ?? '240px'
```

`height` is a plain `const`, not a computed ref. It captures the prop value at component creation time. If the parent ever passes a different `minHeight` dynamically (e.g. in a responsive layout), `height` will not update and the style binding will remain stale.

While current usage in `DashboardView` passes static string literals, this is still an incorrect pattern — `props` destructuring loses reactivity and the comment in the store file (`// Components access resources whole — do NOT destructure`) documents this exact pitfall.

**Fix:**

```typescript
import { computed } from 'vue'
const props = defineProps<{ label: string; minHeight?: string }>()
const height = computed(() => props.minHeight ?? '240px')
```

---

### WR-05: `TransactionsTable` Pagination Shown When `totalPages === 0` — "Page 1 of 0" Displayed on Empty Result

**File:** `frontend/src/components/TransactionsTable.vue:75-95`

**Issue:** The pagination `div` is shown when `page && !loading && !error` (line 75). When the backend returns `totalPages: 0` (empty portfolio, no transactions at all), the component correctly shows the empty state (`isEmpty()` returns true), so the data rows `<template v-else-if="page">` does not render. However, the pagination div condition is independent of the empty check — `page` is non-null (it's a valid `PageResponse` with `content: []`), `loading` is false, `error` is null, so the pagination renders showing **"Page 1 of 0"** with both Prev and Next disabled.

The test at `TransactionsTable.test.ts:82` passes `makePage([], 0, 0)` and checks for the empty-state text but does not assert that pagination is hidden.

**Fix:**

```html
<div
  v-if="page && !loading && !error && page.totalPages > 0"
  class="pagination"
  ...
>
```

---

## Info

### IN-01: `formatSignedCurrency` Returns `-$1,234.00` Not `−$1,234.00` — Minus Sign vs Hyphen-Minus

**File:** `frontend/src/utils/format.ts:36-38`

**Issue:**
```typescript
export function formatSignedCurrency(value: number): string {
  const formatted = formatCurrency(Math.abs(value))
  return value >= 0 ? `+${formatted}` : `-${formatted}`
}
```

The negative branch prepends a hyphen-minus (`U+002D`) rather than a proper minus sign (`U+2212`). The positive branch uses `+` which is ASCII. For financial displays, using `−` (U+2212) is conventional and matches what `Intl.NumberFormat` itself emits for signed numbers. The inconsistency (Intl uses `−` internally, this function uses `-`) means string comparisons in tests will pass against the hyphen-minus but screen readers and copy-paste behavior differ. This is minor but inconsistent with the `formatSignedPercent` function which also uses hyphen-minus.

**Fix:** Either accept the ASCII convention consistently throughout, or switch to `Intl.NumberFormat` with `signDisplay: 'always'` to get consistent Unicode:

```typescript
export function formatSignedCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency: 'USD',
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
    signDisplay: 'always',
  }).format(value)
}
```

---

### IN-02: `echarts.ts` Imports Both `* as echarts` and Named `{ use }` From Same Module — Redundant Import

**File:** `frontend/src/plugins/echarts.ts:5-6`

**Issue:**
```typescript
import * as echarts from 'echarts/core'
import { use } from 'echarts/core'
```

`use` is already available as `echarts.use`. The separate named import is redundant. Tree-shaking and bundling are unaffected (same module), but it creates confusion about whether `use` and `echarts.use` are the same function.

**Fix:** Remove the duplicate named import and call `echarts.use([...])` directly, or keep only `{ use }` and access `echarts.registerTheme` separately:

```typescript
import * as echarts from 'echarts/core'
// Remove: import { use } from 'echarts/core'
echarts.use([LineChart, PieChart, /* ... */])
echarts.registerTheme('quantlens-dark', quantlensDarkTheme)
```

---

### IN-03: `DashboardView` Calls `portfolioStore.refreshAll()` Without `await` in `onMounted`

**File:** `frontend/src/views/DashboardView.vue:17-19`

**Issue:**
```typescript
onMounted(() => {
  portfolioStore.refreshAll()
})
```

The Promise returned by `refreshAll()` is not awaited and not caught. If `refreshAll` itself somehow throws (it is documented as never throwing, but an uncaught microtask error would still be a silent failure), the error will be swallowed by the browser's unhandled rejection handler. `refreshAll` uses `Promise.allSettled` internally, so sub-fetch errors are captured — but errors in the version-guard bookkeeping logic itself would escape.

This is technically consistent with the store's design (loading states are communicated reactively through the store, not through the Promise), but the missing `await` + missing catch is a code-smell that makes intent unclear and breaks any lint rule requiring promise handling.

**Fix:** Add explicit fire-and-forget annotation or use `void`:

```typescript
onMounted(() => {
  void portfolioStore.refreshAll()
})
```

---

_Reviewed: 2026-06-07_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
