---
phase: 3
slug: frontend-scaffold
audit_date: 2026-06-07
auditor: ui-review agent
baseline: 03-UI-SPEC.md
screenshots: not captured (no dev server detected at localhost:3000 / 5173 / 8080)
registry_audit: skipped — shadcn not initialized; no third-party registries declared
fix_date: 2026-06-07
fix_status: fixed
---

# Phase 3 — UI Review

**Audited:** 2026-06-07
**Baseline:** `03-UI-SPEC.md` (approved design contract)
**Screenshots:** Not captured — no dev server running; code-only audit

---

## Pillar Scores

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 3/4 | Error copy omits "Check your connection" clause from spec; slot labels use title-case not spec format in one case |
| 2. Visuals (layout / hierarchy) | 3/4 | Grid structure and wireframe match; App.vue body background is `#0f172a` not `--color-bg-base`; KPI Phase-4 cards use a wrapping div tooltip instead of spec's `opacity + tooltip` on the card element |
| 3. Color / Design Token Fidelity | 3/4 | All components use CSS vars correctly; four hardcoded hex literals appear in ECharts series configs and one treemap borderColor — all values match the token values but bypass the CSS var system |
| 4. Typography & Spacing | 3/4 | LoginView uses rem-based spacing throughout instead of the `--space-*` token scale; badge font-size is 12px not the spec 11px label size; sort indicator is 10px (matches spec) |
| 5. ECharts Theming | 4/4 | Theme object is a verbatim match of spec; transparent bg, correct axis/grid/tooltip colors, 8-color palette, dashed benchmark line, gradient area fill all correct |
| 6. Accessibility | 2/4 | LoginView input uses bare `outline: none` without replacement; `aria-busy` placed on `<template>` (void element) in both tables instead of the container; min-height on pagination buttons is 28px — below 44px WCAG 2.5.5 minimum |

**Overall: 18/24**

---

## Top 3 Priority Fixes

1. **`outline: none` on login input without replacement** (LoginView.vue:287) — keyboard users lose all focus indication on the most critical interactive element on the login page. Fix: remove `outline: none` and rely on the global `:focus-visible` rule in `style.css`, or add `outline: 2px solid var(--color-accent); outline-offset: 2px` to `.field input:focus-visible` (the `:focus-visible` override already exists on line 295 but is not sufficient because the bare `:focus` rule at line 291 sets `border-color: var(--color-accent)` while suppressing the visible ring — a user on keyboard will see a faint border change only).

2. **`aria-busy` on `<template>` instead of the container element** (HoldingsTable.vue:132, TransactionsTable.vue:34) — `<template>` is a non-rendered Vue fragment; ARIA attributes on it are silently dropped by the browser. The spec mandates `aria-busy="true"` on the container. Fix: move `aria-busy` and `aria-label` to the `<tbody>` element wrapping the skeleton rows, or to the outermost wrapper div of each table component.

3. **Pagination button `min-height: 28px`** (TransactionsTable.vue:240) — violates the spec's WCAG 2.5.5 mandate of 44×44px minimum touch target on all interactive controls including pagination. Fix: set `min-height: 44px` and ensure `min-width: 44px` (or `padding` equivalent) on `.page-btn`.

---

## Detailed Findings

### Pillar 1: Copywriting (3/4)

**Matches spec:**
- Empty states match verbatim: "No holdings in this portfolio." (HoldingsTable.vue:151), "No transactions recorded." (TransactionsTable.vue:53), "No P&L data available." (PnlChart.vue:111), "No allocation data. Add holdings to see your sector breakdown." (AllocationChart.vue:183), "No benchmark data available." (BenchmarkChart.vue:109).
- Login error message matches spec: "Invalid credentials. Try one of the demo personas below." (LoginView.vue:47).
- Logout button label "Log out" correct (TopBar.vue:83).
- Persona switch loading text "Switching to [Persona]…" correct (TopBar.vue:72).
- Top-bar subtitle "AI Portfolio Intelligence" correct (TopBar.vue:57).
- Login persona buttons use "Log in as [Persona]" pattern (LoginView.vue:87).

**Deviations:**

WARNING — Error copy is abbreviated vs spec.
- Spec mandates: `"Failed to load [panel name]. Check your connection and try again."`
- Actual in PnlChart.vue:105: `"Failed to load P&L data."` (no connection/retry instruction).
- Actual in BenchmarkChart.vue:104: `"Failed to load benchmark."` (truncated).
- Actual in AllocationChart.vue:177: `"Failed to load allocation."` (truncated).
- The "Retry" button is present, satisfying the interactive part, but the instructional text is missing from all chart error states.

WARNING — Slot placeholder label casing divergence.
- Spec format: `"[Feature Name] — Phase [N]"` (e.g., "Risk Scorecard — Phase 4").
- Actual: "Monte Carlo Forecast — Phase 5" (DashboardView.vue:156). Spec says "Monte Carlo forecast — Phase 5" (lowercase 'f'). Minor but a copy-contract deviation.
- KPI card Phase-4 label "Risk Score — Phase 4" (DashboardView.vue:104) vs spec's `kpi-risk-score` slot label "Risk Score" — acceptable.

---

### Pillar 2: Visuals / Layout (3/4)

**Matches spec:**
- 12-column CSS Grid with `gap: var(--space-xl)` (32px) confirmed in DashboardView.vue:210–211.
- Column spans match wireframe: PnlChart col-7, BenchmarkChart col-5, Allocation col-6, Risk slot col-6, all full-width slots col-12 (DashboardView.vue:119–189).
- TopBar is `position: fixed; top: 0; width: 100%; height: 48px; z-index: 100` — exact spec match (TopBar.vue:89–96).
- KPI strip uses `repeat(auto-fit, minmax(180px, 1fr))` matching spec's "auto-fit min 180px" (DashboardView.vue:219).
- Phase-4 KPI placeholder `opacity: 0.5` per spec (DashboardView.vue:225–227).
- Responsive breakpoint at `max-width: 1279px` stacks all non-full-width columns — correct (DashboardView.vue:237–248).
- `padding-top: 48px` on `.dashboard-main` matches spec's top-bar offset (DashboardView.vue:205).
- SlotPlaceholder renders centered label, dashed border, correct heights for all phase stubs.

**Deviations:**

WARNING — App.vue body background is hardcoded `#0f172a` (`--color-bg-elevated`), not `--color-bg-base` (`#0b0f1a`).
- Spec: `DashboardView.dashboard-page { background: var(--color-bg-base) }` (which is `#0b0f1a`, the deepest layer).
- App.vue:21: `background: #0f172a` — this is the elevated layer, not the base canvas. DashboardView.vue correctly sets `background: var(--color-bg-base)` on `.dashboard-page`, so the dashboard canvas is correct. However, the body background visible in any blank areas (scroll overshoot, login page edges) is the wrong shade — `#0f172a` vs `#0b0f1a`.
- LoginView.vue:133: `.login-page { background: var(--color-bg-base) }` — login page background is correct, but the body beneath it is still `#0f172a`. Practically invisible difference but a token contract violation.

WARNING — Phase-4 KPI placeholder implemented as wrapping `<div class="kpi-placeholder">` with HTML `title` tooltip.
- Spec: "Phase 4 placeholder — same card shell; primary value = `—`; label describes future metric; opacity 0.5; tooltip on hover: 'Available in Phase 4'".
- Actual: `<div class="kpi-placeholder" title="Available in Phase 4">` wrapping a real `<KpiCard>`. The tooltip is a native browser title attribute (inaccessible to touch, no custom styling) rather than a styled tooltip component. Opacity is correctly 0.5. The KpiCard inside lacks the `tooltip` prop/slot. Acceptable for Phase 3 but the spec implied an accessible tooltip.

WARNING — `<main>` element at DashboardView.vue:77 lacks `aria-label`. The spec does not explicitly require this but it is a standard landmark label. Minor.

---

### Pillar 3: Color / Design Token Fidelity (3/4)

**Matches spec:**
- All 20 CSS custom properties from the spec are declared verbatim in `style.css` with the correct hex values.
- Border radius tokens (`--radius-sm/md/lg/xl/pill`) all present.
- Shadow tokens (`--shadow-card/float/modal`) all present.
- Fan chart reservation tokens (`--color-fan-*`) present.
- Every component uses `var(--token)` for all background, border, text, and shadow properties in scoped CSS.
- 60/30/10 distribution: background-base/elevated dominate correctly. Accent (`--color-accent`, `--color-up`, `--color-down`) is restricted to logo, focus rings, ticker text, sort indicators, signed values, badge text — matches the exhaustive spec list.

**Deviations:**

WARNING — Four hardcoded hex literals in ECharts series configurations bypass CSS variable system.

| File | Line | Literal | Correct token |
|------|------|---------|---------------|
| `PnlChart.vue` | 55 | `'#0ea5e9'` | should reference `--color-accent` (not possible directly in JS, but could use a computed constant) |
| `BenchmarkChart.vue` | 53 | `'#0ea5e9'` | same |
| `BenchmarkChart.vue` | 57 | `'#334155'` | `--color-border` |
| `BenchmarkChart.vue` | 68 | `'#94a3b8'` | `--color-text-secondary` |

These values are all numerically correct (matching the token definitions), so the rendered output is indistinguishable from the intended design at Phase 3. However, they break the token-first rule: if a token value changes in `style.css`, these series colors will not update.

WARNING — `AllocationChart.vue:115`: treemap `borderColor: '#0b0f1a'` hardcoded. This matches `--color-bg-base` but is not token-referenced.

WARNING — `App.vue:21–22`: body reset hardcodes `background: #0f172a` and `color: #e2e8f0` instead of using `var(--color-bg-elevated)` and `var(--color-text-primary)`. As the global reset, this is the worst place for a token bypass because it affects the entire document.

---

### Pillar 4: Typography & Spacing (3/4)

**Typography matches spec:**
- Brand name: `font-size: 20px; font-weight: 700; color: var(--color-accent)` — exact spec match (TopBar.vue:115–117). Spec states this is the only weight-700 use.
- KPI label: `font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em; color: var(--color-text-muted)` — verbatim match (KpiCard.vue:81–86).
- KPI primary value (Numeric-large): `font-size: 20px; font-weight: 600; font-family: var(--font-mono); font-variant-numeric: tabular-nums` — exact match (KpiCard.vue:89–93).
- Table column headers: `font-size: 11px; font-weight: 600; text-transform: uppercase; letter-spacing: 0.06em` — correct in both HoldingsTable (line 198) and TransactionsTable (line 114).
- Table body: `font-size: 14px` with monospace for numeric columns — correct.
- Sort indicator: `font-size: 10px` (HoldingsTable.vue:222) — spec says "10px, inline after header text" — correct.
- `--font-mono` token declared and used for all monetary/percent values.

**Typography deviations:**

WARNING — LoginView uses `rem` units throughout rather than the `--space-*` token scale or `px` per spec typography table.
- `.app-title { font-size: 2rem }` — spec says Display is 24px. `2rem` = 32px at default root font size (16px), which is 8px larger than the spec Display size of 24px. This is a measurable deviation.
- `.app-subtitle { font-size: 0.85rem }` = ~13.6px. Spec does not define a subtitle size for login, but the Body size is 14px; this is close.
- `.persona-name { font-size: 0.95rem }` = ~15.2px. Spec doesn't define this specifically but the inconsistency in the rem scale is notable.

WARNING — Badge font-size in TransactionsTable.vue is 12px (lines 161, 171) but the spec label size is 11px and the spec says BUY/SELL are badge text. Minor but a 1px deviation from spec.

**Spacing matches spec:**
- Dashboard grid `gap: var(--space-xl)` (32px) and `padding: var(--space-lg)` (24px) — correct.
- KPI strip `gap: var(--space-md)` (16px) — correct.
- TopBar `padding: 0 var(--space-lg)` — correct.
- Chart panels, table cells, error/empty states all use `var(--space-*)` tokens.

**Spacing deviations:**

WARNING — LoginView.vue uses `rem`-based margins and padding (e.g., `padding: 2.5rem 2rem`, `gap: 0.75rem`, `margin: 0 0 2rem`) instead of the `--space-*` token scale. The login page is outside the main dashboard grid but the spec token system is global. None of these rem values map cleanly to the 4px-multiple scale: `2.5rem = 40px` (not a token value), `0.75rem = 12px` (between `--space-sm: 8px` and `--space-md: 16px`).

WARNING — KpiCard `.kpi-card { gap: 4px }` uses a hardcoded `4px` instead of `var(--space-xs)`. Both equal 4px, so no visual difference, but it bypasses the token.

WARNING — Pagination button `padding: 4px 12px` is hardcoded (TransactionsTable.vue:237) — not a token value.

---

### Pillar 5: ECharts Theming (4/4)

**Full match with spec — all items verified:**

- `backgroundColor: 'transparent'` — correct (echarts-theme.ts:4).
- `textStyle` — color `#94a3b8` (matches `--color-text-secondary`), fontFamily matches system stack (line 7–9).
- `tooltip` — backgroundColor `#1e293b` (`--color-bg-surface`), borderColor `#334155` (`--color-border`), borderWidth 1, textStyle `#e2e8f0`, `extraCssText` with `border-radius: 8px` and box-shadow matching `--shadow-float` (lines 25–31).
- `grid` — `containLabel: true`, exact px values (l:16, r:16, t:32, b:24) — spec match (lines 33–38).
- `xAxis` — axisLine/tick color `#334155`, axisLabel `#64748b`, splitLine hidden (lines 41–45).
- `yAxis` — axisLine/tick hidden, axisLabel `#64748b`, splitLine dashed `#1e293b` (`--color-border-subtle`) (lines 48–52).
- Default `color` palette: `['#0ea5e9','#8b5cf6','#f59e0b','#22c55e','#ec4899','#14b8a6','#f97316','#6366f1']` — verbatim spec match (line 56).
- `line` — `smooth: true; symbolSize: 0; lineStyle.width: 2` — correct (lines 58–62).
- `visualMap` heatmap colors `['#ef4444','#f8fafc','#3b82f6']` — registered for Phase 4, correct (lines 67–68).
- Fan chart tokens `--color-fan-p50/band-1/band-2` reserved in style.css (lines 66–68 of style.css).

**Chart-specific theming verified:**
- PnlChart: portfolio line `#0ea5e9` (accent), gradient area fill `rgba(14,165,233,0.25)→0` — correct.
- BenchmarkChart: Portfolio `#0ea5e9` solid 2px; S&P 500 `#94a3b8` dashed 1.5px; baseline at 100 via `markLine` with `#334155` dashed — all correct.
- AllocationChart: donut/treemap toggle with `aria-pressed` states; center label in monospace `#e2e8f0`; colors come from theme `color[]` — correct.
- Theme registered once in `echarts.ts` and provided globally via `THEME_KEY` in `App.vue` — correct (App.vue:3–4).

---

### Pillar 6: Accessibility (2/4)

**Matches spec:**
- Global `:focus-visible` ring: `outline: 2px solid var(--color-accent); outline-offset: 3px` in style.css:82–85.
- Persona switcher: `<nav aria-label="Persona switcher">` with `aria-pressed` on each pill (TopBar.vue:61–68) — spec match.
- TopBar uses `<header role="banner">` (TopBar.vue:53).
- DashboardView uses `<main>` (DashboardView.vue:77).
- `aria-sort` on sortable HoldingsTable column headers: `aria-sort="ascending|descending|none"` on Ticker, Mkt Value, P&L columns (HoldingsTable.vue:90, 106, 118) — spec match.
- All chart panels use `<figure>` with `aria-busy` and `aria-label`, plus `<figcaption class="sr-only">` for text summaries (PnlChart.vue:95–126, BenchmarkChart.vue:94–125, AllocationChart.vue:144–197).
- KpiCard skeleton: `aria-busy="true"` and `aria-label="Loading {label}"` — correct (KpiCard.vue:51–53).
- BUY/SELL values always show text, not just color — correct.
- P&L signed values always carry `+` or `-` prefix (KpiCard.vue deltaFormatted, SignedValue formatted prop) — correct.
- `min-height: 44px` on persona pills (TopBar.vue:136), logout button (TopBar.vue:185), login persona buttons (LoginView.vue:196), submit button (LoginView.vue:316).

**BLOCKER findings:**

BLOCKER — `LoginView.vue:287` contains `outline: none` on `.field input:focus` without an accessible replacement.
```css
/* Line 291 */
.field input:focus {
  border-color: var(--color-accent);
}
/* Line 286–289 */
.field input {
  outline: none;   ← BLOCKER
  ...
}
```
The `outline: none` on the element itself overrides the global `:focus-visible` rule. Only `border-color` changes on focus, which fails WCAG 2.4.7 Focus Visible for keyboard users. The `focus-visible` override on line 295 does add an outline, but only for pointer-agnostic focus (modern browsers); older browser behavior and some testing scenarios will still hit the `outline: none` path. The correct fix is to remove `outline: none` entirely from `.field input` and keep only `.field input:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 2px }`.

BLOCKER — `aria-busy` and `aria-label` on `<template>` fragments in both table loading states.
- HoldingsTable.vue:132: `<template v-if="loading" aria-busy="true" aria-label="Loading holdings">`
- TransactionsTable.vue:34: `<template v-if="loading" aria-busy="true" aria-label="Loading transactions">`
`<template>` renders no DOM node, so these ARIA attributes are lost. Spec mandates `aria-busy="true"` on the container. Move both attributes to `<tbody>` or wrap the skeleton rows in a `<tbody aria-busy="true" aria-label="...">`.

WARNING — Pagination controls `min-height: 28px` (TransactionsTable.vue:240). Spec mandates 44px minimum touch target (WCAG 2.5.5). The Previous/Next buttons are 28px tall — 36% below the minimum. Fix: set `min-height: 44px; min-width: 44px` on `.page-btn`.

WARNING — `AllocationChart.vue:259` toggle button `:focus-visible` uses `outline-offset: 2px` while the spec mandates `outline-offset: 3px`. Minor but inconsistent with the global token.

WARNING — No `<label>` element in LoginView.vue explicitly associates with the error message paragraph (line 120 `<p v-if="errorMessage" class="error">`). It should use `role="alert"` to be announced immediately on insertion. Currently it is a plain `<p>`, so screen readers may miss it on dynamic update without a page reload.

WARNING — `HoldingsTable.vue`: empty state SVG illustration described in spec ("simple SVG bar-chart outline 48px, --color-text-muted") is not implemented. The empty cell only shows text "No holdings in this portfolio." without the specified icon. This is a minor visual spec gap.

---

## Additional Minor Issues

- `DashboardView.vue:212`: `padding: var(--space-lg)` applies 24px on all sides including bottom. Spec states `padding: 0 24px` at desktop (horizontal only). The bottom padding from this rule means content has a 24px floor pad, which is likely fine visually but deviates from the spec's outer-padding declaration.
- `DashboardView.vue:247`: At `max-width: 1279px` the grid padding drops to `var(--space-md)` (16px) — this matches the spec's "0 16px at medium" breakpoint.
- No `<label>` wrapping or `aria-describedby` in `TransactionsTable.vue` retry logic — the retry button calls `emit('page-change', props.page?.number ?? 0)` which re-requests the current page rather than a true retry of page 0, but this is a logic concern rather than a UI-spec deviation.
- `KpiCard.vue` renders a delta line even when `secondary` prop is absent but `delta` is present. The spec shows delta and secondary as separate optional lines, both nullable. The implementation handles this correctly with `v-if` guards.

---

## Registry Audit

Registry audit: not applicable — shadcn not initialized; no third-party registries declared. ECharts and vue-echarts are trusted OSS packages installed via npm. No block vetting required.

---

## Files Audited

| File | Role |
|------|------|
| `.planning/phases/03-frontend-scaffold/03-UI-SPEC.md` | Design contract baseline |
| `frontend/src/style.css` | CSS token implementation |
| `frontend/src/plugins/echarts-theme.ts` | ECharts dark theme object |
| `frontend/src/plugins/echarts.ts` | ECharts registration + theme binding |
| `frontend/src/App.vue` | Global reset + THEME_KEY provider |
| `frontend/src/views/DashboardView.vue` | Main layout grid + all component wiring |
| `frontend/src/views/LoginView.vue` | Login page UI |
| `frontend/src/components/TopBar.vue` | Fixed top navigation bar |
| `frontend/src/components/KpiCard.vue` | KPI metric card |
| `frontend/src/components/SignedValue.vue` | Colored signed P&L display |
| `frontend/src/components/SlotPlaceholder.vue` | Extension slot stub |
| `frontend/src/components/HoldingsTable.vue` | Holdings data table |
| `frontend/src/components/TransactionsTable.vue` | Paginated transactions table |
| `frontend/src/components/PnlChart.vue` | ECharts equity curve |
| `frontend/src/components/BenchmarkChart.vue` | ECharts dual-line benchmark chart |
| `frontend/src/components/AllocationChart.vue` | ECharts donut/treemap allocation chart |
