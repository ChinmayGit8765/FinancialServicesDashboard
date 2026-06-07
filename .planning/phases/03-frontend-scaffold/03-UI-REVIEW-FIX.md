---
phase: 3
fixed_at: 2026-06-07T16:50:00Z
review_path: .planning/phases/03-frontend-scaffold/03-UI-REVIEW.md
iteration: 1
findings_in_scope: 12
fixed: 12
skipped: 0
status: all_fixed
build_status: pass
test_status: pass (35/35)
---

# Phase 3 — UI Review Fix Report

**Fixed at:** 2026-06-07
**Source review:** `.planning/phases/03-frontend-scaffold/03-UI-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 12 (2 BLOCKER + 10 WARNING/minor)
- Fixed: 12
- Skipped: 0
- Build: `npm run build` exits 0 (716 modules, chunk-size warning pre-exists)
- Tests: `npm run test` — 35/35 pass

---

## Fixed Issues

### BL-01: `outline: none` on `.field input` — BLOCKER (WCAG 2.4.7)

**Files modified:** `frontend/src/views/LoginView.vue`
**Commit:** `7853cb1`
**Applied fix:** Deleted the `outline: none` line from `.field input`. The
`:focus-visible` override at `outline: 2px solid var(--color-accent); outline-offset: 2px`
was already present and now applies without the bare-focus suppression. Keyboard
focus is fully visible on the login input fields.

Also applied in the same commit:
- `role="alert"` added to the error `<p>` so screen readers announce it dynamically.
- `.app-title` font-size `2rem` (32px) → `24px` (spec Display token).
- Exact rem→token substitutions: `--space-xs` (0.25rem), `--space-xl` (2rem margins),
  `--space-md` (1rem page padding), field gap token.

---

### BL-02: `aria-busy`/`aria-label` on `<template>` fragment — BLOCKER (WCAG 4.1.2)

**Files modified:** `frontend/src/components/HoldingsTable.vue`, `frontend/src/components/TransactionsTable.vue`
**Commit:** `b1d671a`
**Applied fix:** Moved `aria-busy` and `aria-label` from the `<template v-if="loading">` fragment
(which renders no DOM node, so ARIA was silently dropped) to the containing `<tbody>` element
using dynamic Vue bindings:
```html
<tbody
  :aria-busy="loading || undefined"
  :aria-label="loading ? 'Loading holdings' : undefined"
>
```
Using `|| undefined` ensures the attribute is absent (not `false`) when not loading, which is
correct per ARIA semantics. The `<template>` retains its `v-if="loading"` for conditional
rendering but no longer carries ARIA attributes.

Also applied in the same commit:
- Pagination `.page-btn` min-height `28px` → `44px`, added `min-width: 44px` (WCAG 2.5.5).
- Badge font-size `12px` → `11px` (spec Label size).
- AllocationChart `.toggle-btn:focus-visible` `outline-offset: 2px` → `3px` (matches global token).

---

### WR-app: App.vue body reset hardcodes `#0f172a` / `#e2e8f0`

**Files modified:** `frontend/src/App.vue`
**Commit:** `dc58993`
**Applied fix:** Replaced `background: #0f172a` with `background: var(--color-bg-elevated)` and
`color: #e2e8f0` with `color: var(--color-text-primary)`. The global body reset now tracks the
design-token system end-to-end.

---

### WR-colors: Hardcoded hex literals in ECharts series configs bypass CSS-var system

**Files modified:** `frontend/src/plugins/chart-colors.ts` (new), `frontend/src/components/PnlChart.vue`,
`frontend/src/components/BenchmarkChart.vue`, `frontend/src/components/AllocationChart.vue`
**Commit:** `8150611`
**Applied fix:** Created `frontend/src/plugins/chart-colors.ts` — a small module that reads CSS
custom properties from `:root` via `getComputedStyle` at module init time, with fallback literals
matching the token definitions. All four hardcoded series colors now reference `CHART_COLORS.*`:

| File | Was | Now |
|------|-----|-----|
| `PnlChart.vue:55` | `'#0ea5e9'` | `CHART_COLORS.accent` |
| `BenchmarkChart.vue:53` | `'#0ea5e9'` | `CHART_COLORS.accent` |
| `BenchmarkChart.vue:57` | `'#334155'` | `CHART_COLORS.border` |
| `BenchmarkChart.vue:68` | `'#94a3b8'` | `CHART_COLORS.textSecondary` |
| `AllocationChart.vue:115` | `'#0b0f1a'` | `CHART_COLORS.bgBase` |

---

### WR-copy: Chart error states missing "Check your connection and try again."

**Files modified:** `frontend/src/components/PnlChart.vue`, `frontend/src/components/BenchmarkChart.vue`,
`frontend/src/components/AllocationChart.vue`
**Commit:** `873d1ff`
**Applied fix:** Appended ` Check your connection and try again.` to the error `<span>` in all
three charts, matching the spec's mandated copy: `"Failed to load [panel name]. Check your
connection and try again."` The Retry button was already present.

---

### WR-tokens: KpiCard `gap: 4px` hardcoded instead of `var(--space-xs)`

**Files modified:** `frontend/src/components/KpiCard.vue`
**Commit:** `6ebec36`
**Applied fix:** Replaced `gap: 4px` with `gap: var(--space-xs)` in `.kpi-card`. Visually
identical (both 4px) but eliminates the token bypass.

---

### WR-empty: HoldingsTable empty state missing spec SVG bar-chart icon

**Files modified:** `frontend/src/components/HoldingsTable.vue`
**Commit:** `29fddf9`
**Applied fix:** Added an inline `aria-hidden` SVG (48×48px, three rectangular bars outlined in
`currentColor = --color-text-muted`) above the "No holdings in this portfolio." text. Updated
`.empty-cell` to flex-column layout with `--space-sm` gap between icon and text.

---

## Skipped Issues

None — all in-scope findings were addressed.

---

## Build + Test Gates

```
npm run build   → exit 0  (716 modules, pre-existing chunk-size advisory only)
npm run test    → 35/35 tests pass
```

---

_Fixed: 2026-06-07T16:50:00Z_
_Fixer: Claude Sonnet 4.6 (gsd-code-fixer)_
_Iteration: 1_
