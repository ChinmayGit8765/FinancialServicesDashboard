---
phase: 03-frontend-scaffold
verified: 2026-06-07T07:00:00Z
status: human_needed
score: 10/10
overrides_applied: 0
human_verification:
  - test: "Open dashboard at >=1280px. Confirm dark theme: canvas #0b0f1a, cards #1e293b — no light backgrounds."
    expected: "All panels dark. No white/light-grey backgrounds anywhere on the page."
    why_human: "Color rendering can only be confirmed by eye against a running stack."
  - test: "P&L equity curve renders: confirm line + gradient area fill, approximately 504 data points, no ECharts console errors."
    expected: "Smooth accent line with a fade-out area fill beneath it. No console errors about missing chart modules."
    why_human: "Canvas rendering and ECharts registration errors are runtime-only and not detectable by grep."
  - test: "Benchmark chart shows two distinct lines: Portfolio (sky-blue solid) and S&P 500 (grey dashed). Both start at index value 100."
    expected: "Two visually distinct lines, both originating near 100 on the y-axis."
    why_human: "Visual differentiation between solid/dashed line styles and the starting values require a running chart."
  - test: "Allocation donut renders with 8-color sectors. Toggle the Donut/Treemap button to confirm the view switches."
    expected: "Donut view first; treemap shows labeled sector squares after toggle click. No full reload."
    why_human: "Vue reactivity and ECharts option switching only observable at runtime."
  - test: "Holdings table shows rows with green (positive) / red (negative) P&L colors using SignedValue. Clicking Ticker, Mkt Value, and P&L $ column headers changes sort order."
    expected: "Colored P&L cells, sort arrows appear, row order changes on each click, third click resets."
    why_human: "Color classes apply correctly only when CSS tokens are resolved at runtime; sort behavior requires interaction."
  - test: "Transactions table: BUY badge is green, SELL badge is red. Click Next/Prev pagination controls."
    expected: "Badge pill colors distinct. Prev disabled on page 1; Next disabled on last page. Page counter increments."
    why_human: "Badge colors depend on CSS var token resolution; disabled state depends on live page data."
  - test: "KPI strip: confirm 3 real value cards (Market Value, Unrealized P&L, Daily Change) show populated numbers, and 2 Phase-4 cards show '—' at 50% opacity."
    expected: "Real dollar values in first 3 cards; dash placeholders at visibly reduced opacity in last 2."
    why_human: "KPI values depend on live API data; opacity rendering verified visually."
  - test: "Persona switch Alice -> Bob: click 'Bob' pill. Confirm 'Switching to Bob…' text appears, then data reloads to Bob's portfolio — NO full page reload (URL stays /)."
    expected: "Pill text changes during switch; after switch all panels show Bob's data; URL never changes."
    why_human: "Full page reload vs SPA re-scope requires visual observation; data correctness (Alice vs Bob values) requires live API."
  - test: "Rapid persona switch: click Alice then immediately click Bob before the first switch completes. Confirm only Bob's data is shown after both complete."
    expected: "No Alice data appears in any panel once settled; no console errors or mixed-data artifacts."
    why_human: "Race-guard behaviour (refreshVersion) is only observable at runtime with real network latency."
  - test: "Error states: stop the backend, refresh the dashboard. Confirm each data panel shows a red error state with a Retry link."
    expected: "Each chart/table shows the error panel with the static copy ('Failed to load…') and a Retry button."
    why_human: "Requires stopping the backend service; error rendering confirmed visually."
  - test: "Logout: click Log out. Confirm redirect to /login and no portfolio data accessible without re-login."
    expected: "Browser navigates to /login immediately after click."
    why_human: "Redirect and session invalidation require runtime verification."
  - test: "Session persistence: log in as Alice, press F5. Confirm dashboard reloads Alice's data without showing /login."
    expected: "Dashboard comes back directly with Alice's portfolio, not the login page."
    why_human: "Cookie persistence only verifiable in a live browser session."
  - test: "Phase 4/5/6 slot stubs: confirm all 6 SlotPlaceholder regions appear with dashed borders and correct labels (Risk Scorecard, Correlation Heatmap, Monte Carlo Forecast, AI Daily Commentary, AI Q&A, LLM Key)."
    expected: "Dashed-border boxes with muted text labels, no shimmer, correct phase labels."
    why_human: "Visual layout and label text require a running app; label presence already verified by code."
  - test: "README item 14: confirm OAuth Upgrade Path section contains the LlmKeySessionHolder AI-seam guarantee."
    expected: "Text clearly states no code changes are needed to LlmKeySessionHolder or AI components when upgrading auth."
    why_human: "Content quality and clarity judgment."
---

# Phase 3: Frontend Scaffold Verification Report

**Phase Goal:** A cohesive Vue 3 single-page dashboard presents all portfolio data via ECharts, the demo-user switcher, and the auth flow — with the OAuth upgrade path documented.
**Verified:** 2026-06-07T07:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Dashboard renders all five portfolio views (holdings, P&L, allocation, transactions, benchmark) driven by the portfolio store | VERIFIED | `DashboardView.vue` lines 120–177: PnlChart, BenchmarkChart, AllocationChart, HoldingsTable, TransactionsTable all bound to `portfolioStore.*` resources; `onMounted(() => portfolioStore.refreshAll())` wires initial data load |
| 2 | Persona switch (Alice/Bob/Charlie) re-scopes all data via loginAs + refreshAll without a full page reload | VERIFIED | `TopBar.vue` lines 30–44: `switchPersona()` calls `await authStore.loginAs(username, 'demo1234')` then `await portfolioStore.refreshAll()` with no `router.push('/')` — SPA re-scope only |
| 3 | Rapid persona switching shows no mixed data (refreshVersion guard + switching debounce) | VERIFIED | `portfolio.ts` lines 141–153: `let refreshVersion = 0` module-scoped; `refreshAll()` captures `myVersion = ++refreshVersion` and returns early when `myVersion !== refreshVersion`; `TopBar.vue` `switching` ref guard prevents concurrent calls |
| 4 | Phase 4/5/6 extension slots render as labelled placeholders without altering the grid | VERIFIED | `DashboardView.vue` lines 146–190: 6 `<SlotPlaceholder>` instances with correct Phase 4/5/6 labels and minHeight values; grid CSS unmodified |
| 5 | ECharts visualizations driven by the Pinia portfolio store via the Phase 2 REST API | VERIFIED | `portfolio.ts` store: 5 axios.get calls to `/api/portfolio/*`; each chart component receives `.data/.loading/.error` from store resources; `echarts.ts` registers modules from `echarts/core` (tree-shaken) |
| 6 | PnlChart renders equity curve as a line with gradient area fill | VERIFIED | `PnlChart.vue` lines 50–69: line series, `smooth: true`, `symbol: 'none'`, areaStyle linear gradient rgba(14,165,233,0.25)→0 |
| 7 | BenchmarkChart renders two rebased-to-100 lines (portfolio solid accent, S&P 500 dashed secondary) | VERIFIED | `BenchmarkChart.vue` lines 47–70: two series — Portfolio `lineStyle:{color:'#0ea5e9',width:2}`, S&P 500 `lineStyle:{color:'#94a3b8',width:1.5,type:'dashed'}` |
| 8 | AllocationChart renders a donut by default and switches to treemap via segmented toggle | VERIFIED | `AllocationChart.vue`: `chartType = ref<'donut'\|'treemap'>('donut')`; `donutOption` (pie radius ['40%','68%']), `treemapOption` (type:'treemap'); `<div role="group" aria-label="Chart type">` toggle buttons |
| 9 | README documents the OAuth upgrade path including that LlmKeySessionHolder / downstream AI need no code changes (AUTH-03) | VERIFIED | `README.md` line 173: explicit paragraph — "LlmKeySessionHolder and all downstream Spring AI features depend on the HTTP session, not on the login mechanism… No changes to LlmKeySessionHolder or any AI component are required" |
| 10 | formatCurrency/formatSignedPercent/deltaClass/formatDate produce correct UI-SPEC outputs; 35/35 tests pass (build exits 0) | VERIFIED | `format.ts` exports all 7 helpers; `setup.ts` has global ResizeObserver mock; context states `npm run build` exits 0 (715 modules), `npm run test` 35/35 green |

**Score:** 10/10 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `frontend/src/views/DashboardView.vue` | Single-page dashboard grid wiring store to components + slots | VERIFIED | 250 lines; all 5 data components present and bound; 6 SlotPlaceholders; `@page-change` wired correctly |
| `frontend/src/components/TopBar.vue` | Fixed top bar with persona switcher + logout | VERIFIED | 207 lines; `loginAs` + `refreshAll` wired; `switching` debounce; `aria-pressed`; 44px min-height pills |
| `frontend/src/components/PnlChart.vue` | Equity-curve line chart with category xAxis and UTC-safe date parsing | VERIFIED | Contains `type: 'category'`, `T00:00:00` in both axisLabel and tooltip formatters; `v-chart` bound |
| `frontend/src/components/BenchmarkChart.vue` | Dual-line benchmark overlay chart | VERIFIED | 212 lines; two series; `type: 'dashed'` on S&P 500; all 4 states + figcaption |
| `frontend/src/components/AllocationChart.vue` | Donut/treemap allocation chart with toggle | VERIFIED | Both pie and treemap series; `role="group"` toggle; all 4 states |
| `frontend/src/components/KpiCard.vue` | KPI card with loading/populated/placeholder states | VERIFIED | `.skeleton` shimmer gated on `loading`; sign-driven left-border; `border-left: 3px solid var(--color-up/down/border)` |
| `frontend/src/components/HoldingsTable.vue` | Holdings table with sort + empty/loading states | VERIFIED | 324 lines; 10 columns; SignedValue for P&L; sortable Ticker/MktVal/P&L with `aria-sort`; skeleton rows; empty message |
| `frontend/src/components/TransactionsTable.vue` | Paginated transactions table with BUY/SELL badges | VERIFIED | `.badge-buy`/`.badge-sell` pill classes; `page-change` emit; prev/next disabled at bounds |
| `frontend/src/components/SignedValue.vue` | Sign-aware colored value display | VERIFIED | `deltaClass` import; val-up/val-down/val-flat classes; font-mono |
| `frontend/src/components/SlotPlaceholder.vue` | Phase 4/5/6 extension-slot stub | VERIFIED | `1px dashed var(--color-border)`; minHeight prop with 240px default |
| `frontend/src/stores/portfolio.ts` | Pinia portfolio store with per-resource async state + refreshAll | VERIFIED | 185 lines; AsyncState<T>; 5 fetch actions; refreshVersion race guard; Promise.allSettled; $reset |
| `frontend/src/api/portfolio.ts` | Typed DTO interfaces + axios fetch fns | VERIFIED | All 6 DTOs exported (TransactionDto has tradeValue, no id); 5 fetch fns; no interceptor registration |
| `frontend/src/api/auth.ts` | Global 401 response interceptor | VERIFIED | Response interceptor present; excludes /auth/me and /auth/login; `router.push('/login')` + window.location fallback |
| `frontend/src/utils/format.ts` | 7 formatting helpers | VERIFIED | All 7 exports present; pure functions; formatDate uses `T00:00:00` |
| `frontend/src/plugins/echarts.ts` | ECharts use() registration + quantlens-dark theme registration | VERIFIED | Imports from `echarts/core`; `use([LineChart, PieChart, TreemapChart, ...])` + `registerTheme('quantlens-dark', ...)` |
| `frontend/src/App.vue` | THEME_KEY provision | VERIFIED | `provide(THEME_KEY, 'quantlens-dark')` line 4 |
| `frontend/src/main.ts` | ECharts side-effect import first | VERIFIED | `import './plugins/echarts'` is line 1, before createApp |
| `frontend/vite.config.ts` | @ alias wired | VERIFIED | `resolve.alias: { '@': fileURLToPath(...) }` |
| `frontend/tsconfig.app.json` | TypeScript paths mapping | VERIFIED | `"paths": { "@/*": ["./src/*"] }` with `"baseUrl": "."` |
| `frontend/vitest.config.ts` | Vitest jsdom config | VERIFIED | `environment: 'jsdom'`; setupFiles; mergeConfig from vite.config |
| `frontend/src/__tests__/setup.ts` | Global ResizeObserver mock | VERIFIED | `globalThis.ResizeObserver = class ResizeObserver { observe(){} unobserve(){} disconnect(){} }` |
| `frontend/src/style.css` | Dark-theme token set on :root | VERIFIED | `--color-bg-base: #0b0f1a`; `--color-up: #22c55e`; full token set present |
| `README.md` | OAuth upgrade path with LlmKeySessionHolder AI-seam guarantee | VERIFIED | One "## OAuth Upgrade Path" section; contains "LlmKeySessionHolder"; states no AI code changes required |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `DashboardView.vue` | `stores/portfolio.ts` | `onMounted refreshAll` + resource bindings | WIRED | Line 17: `onMounted(() => portfolioStore.refreshAll())`; all 5 resources passed as props |
| `TopBar.vue` | `stores/auth.ts` + `stores/portfolio.ts` | `switchPersona: loginAs then refreshAll` | WIRED | Lines 36–39: `await authStore.loginAs(...)` then `await portfolioStore.refreshAll()` |
| `DashboardView.vue` | `SlotPlaceholder.vue` | Phase 4/5/6 slot stubs | WIRED | 6 `<SlotPlaceholder>` usages with label and minHeight |
| `DashboardView.vue` | `TransactionsTable.vue` | `@page-change` wired | WIRED | Line 175: `@page-change="handleTransactionsPage"` |
| `PnlChart.vue` | `vue-echarts VChart` | `<v-chart :option="option" :autoresize="true">` | WIRED | Line 115: `<v-chart v-else class="chart" :option="option" :autoresize="true" />` |
| `PnlChart.vue` | `api/portfolio.ts` | `import type PortfolioPnlDto` | WIRED | Line 5: `import type { PortfolioPnlDto } from '@/api/portfolio'` |
| `AllocationChart.vue` | `AllocationSliceDto` | donut/treemap option from props | WIRED | Lines 56–61 (donut) and 98–101 (treemap) map `props.allocation` |
| `SignedValue.vue` | `utils/format.ts` | `import deltaClass` | WIRED | Line 3: `import { deltaClass } from '@/utils/format'` |
| `HoldingsTable.vue` | `SignedValue.vue` | P&L columns | WIRED | Lines 165–174: two `<SignedValue>` usages for P&L $ and P&L % |
| `TransactionsTable.vue` | `TransactionDto` | props rows + page-change emit | WIRED | `defineEmits<{ 'page-change': [n: number] }>()` at line 13 |
| `api/auth.ts` | router | 401 response interceptor | WIRED | Line 2: `import router from '../router'`; line 33: `router.push('/login')` |
| `stores/portfolio.ts` | `api/portfolio.ts` | import DTO types | WIRED | Lines 4–11: all DTO types imported from `'../api/portfolio'` |
| `main.ts` | `plugins/echarts.ts` | side-effect import | WIRED | Line 1: `import './plugins/echarts'` |
| `App.vue` | vue-echarts THEME_KEY | `provide(THEME_KEY, 'quantlens-dark')` | WIRED | Line 4 |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `DashboardView.vue` → `PnlChart` | `portfolioStore.pnl.data` | `fetchPnl()` → `axios.get('/api/portfolio/pnl')` → Phase 2 REST | Yes — live DB query in Phase 2 backend | FLOWING |
| `DashboardView.vue` → `BenchmarkChart` | `portfolioStore.benchmark.data` | `fetchBenchmark()` → `axios.get('/api/portfolio/benchmark')` | Yes | FLOWING |
| `DashboardView.vue` → `AllocationChart` | `portfolioStore.allocation.data` | `fetchAllocation()` → `axios.get('/api/portfolio/allocation')` | Yes | FLOWING |
| `DashboardView.vue` → `HoldingsTable` | `portfolioStore.holdings.data` | `fetchHoldings()` → `axios.get('/api/portfolio/holdings')` | Yes | FLOWING |
| `DashboardView.vue` → `TransactionsTable` | `portfolioStore.transactions.data` | `fetchTransactions(page)` → `axios.get('/api/portfolio/transactions')` | Yes | FLOWING |
| `TopBar.vue` → persona pills | `personaList` | `personas()` → `axios.get('/api/auth/personas')` | Yes — backend returns seeded personas | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED for live rendering checks (require running server). The build and test gates are confirmed in context (build exits 0, 35/35 tests green) and cover the programmatically checkable behaviors.

---

### Probe Execution

Step 7c: No probe scripts declared in PLAN frontmatter. No `scripts/*/tests/probe-*.sh` exist for this phase. SKIPPED.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| UI-01 | 03-01, 03-02, 03-03, 03-04, 03-05 | Vue 3 front end presents portfolio data in a cohesive single-page dashboard using ECharts visualizations | SATISFIED | DashboardView assembles PnlChart + BenchmarkChart + AllocationChart + HoldingsTable + TransactionsTable, all driven by the Pinia portfolio store hitting the Phase 2 REST API. ECharts registered tree-shaken via echarts/core. Marked complete in REQUIREMENTS.md traceability table. |
| AUTH-03 | 03-05 | README documents how to wire real OAuth (Google/GitHub) as the production upgrade path | SATISFIED | README.md "## OAuth Upgrade Path" section (single heading confirmed); contains 5-step upgrade instructions plus explicit LlmKeySessionHolder AI-seam guarantee paragraph. Marked complete in REQUIREMENTS.md traceability table. |

---

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| None | — | No TBD/FIXME/XXX/TODO/HACK markers found in `frontend/src/**` | — | — |
| `SlotPlaceholder.vue` occurrences in DashboardView | multiple | Intentional phase-slot stubs with dashed borders | Info only | These are the specified Phase 4/5/6 extension points — not blocking stubs |

No debt markers. No unresolved placeholders. No full-bundle ECharts import (`import * as echarts from 'echarts'` absent — only `echarts/core` used). No `v-html` anywhere. No hardcoded data returns from API routes in this phase's files.

---

### Human Verification Required

The 14 items below are the Plan 05 Task 4 blocking human-verification checkpoint, deferred from the autonomous run because they require a live stack. They do not indicate missing implementation — all code paths are wired and the build + unit test gate (35/35) passes. The items are visual/behavioural and cannot be confirmed by grep.

#### 1. Dark theme rendering

**Test:** Open the dashboard at >=1280px.
**Expected:** Canvas `#0b0f1a`, card surfaces `#1e293b` — no light backgrounds on any panel.
**Why human:** CSS token resolution and background colour rendering are only observable in a live browser.

#### 2. P&L equity curve renders

**Test:** Confirm the P&L chart shows a smooth line with gradient area fill and no ECharts console errors.
**Expected:** ~504-point equity curve with accent-colour line and fade-out area. No `[ECharts] Component ... is not installed` console errors.
**Why human:** ECharts canvas rendering and module-registration errors are runtime-only.

#### 3. Benchmark dual-line differentiation

**Test:** Confirm Portfolio line (sky-blue solid) and S&P 500 (grey dashed) are visually distinct and both start near 100.
**Expected:** Two clearly different line styles; both y-values approximately 100 at the left edge.
**Why human:** Visual line-style differentiation requires a rendered chart.

#### 4. Allocation donut/treemap toggle

**Test:** Allocation chart defaults to donut; clicking Treemap switches the view without reload.
**Expected:** Sector-coloured donut → labelled treemap squares on toggle click.
**Why human:** Vue reactivity + ECharts option swap only verifiable at runtime.

#### 5. Holdings table sort + P&L colours

**Test:** Click Ticker, Mkt Value, and P&L $ headers; confirm sort arrows appear and row order changes.
**Expected:** Green SignedValue for positive P&L, red for negative; sort indicators appear/disappear correctly.
**Why human:** CSS colour token resolution; interactive sort state visible only with real data.

#### 6. Transactions BUY/SELL badges + pagination

**Test:** Confirm BUY badge is green pill, SELL is red pill. Click Next/Prev.
**Expected:** Distinct badge colours; Prev disabled on page 1, Next disabled on last page; page counter increments.
**Why human:** Badge colours rely on CSS var resolution; pagination behaviour requires live page data.

#### 7. KPI strip values + Phase-4 placeholder opacity

**Test:** Confirm 3 real KPI cards show populated monetary values; 2 Phase-4 cards show '—' at ~50% opacity.
**Expected:** Market Value / Unrealized P&L / Daily Change show formatted dollar amounts. Risk Score and Sharpe Ratio cards are visibly dimmed.
**Why human:** Live API data required for KPI values; opacity rendering is visual.

#### 8. Persona switch without page reload

**Test:** Click 'Bob' pill while logged in as Alice. Confirm 'Switching to Bob…' spinner appears, then Bob's portfolio loads — URL stays at /.
**Expected:** No browser navigation event; data panels update in-place.
**Why human:** Full-page-reload vs SPA re-scope only observable in a live browser.

#### 9. Rapid persona switch — no data mixing

**Test:** Click Alice then immediately Bob before the first switch completes.
**Expected:** Only Bob's data is shown once settled. No Alice values remain in any panel.
**Why human:** Race-guard behaviour (refreshVersion) requires real network latency to exercise.

#### 10. Error states

**Test:** Stop the backend; refresh the dashboard.
**Expected:** Each data panel (PnlChart, BenchmarkChart, AllocationChart, HoldingsTable, TransactionsTable) shows its static error copy with a Retry button.
**Why human:** Requires stopping a service; error rendering confirmed visually.

#### 11. Logout redirect

**Test:** Click 'Log out'.
**Expected:** Browser navigates to /login immediately.
**Why human:** Router navigation to /login and session invalidation require runtime verification.

#### 12. Session persistence

**Test:** Log in as Alice, press F5.
**Expected:** Dashboard reloads with Alice's portfolio — no redirect to /login.
**Why human:** Cookie persistence only verifiable in a live browser session.

#### 13. Phase 4/5/6 slot stub visuals

**Test:** Confirm all 6 SlotPlaceholder regions show dashed-border boxes with correct labels.
**Expected:** "Risk Scorecard — Phase 4", "Correlation Heatmap — Phase 4", "Monte Carlo Forecast — Phase 5", "AI Daily Commentary — Phase 6", "AI Q&A — Phase 6", "LLM Key — Phase 6" — all with dashed border, no shimmer.
**Why human:** Label presence is code-verified; visual layout (dashed border, muted text, correct sizing) requires a rendered page.

#### 14. README AUTH-03 clarity

**Test:** Read the OAuth Upgrade Path section in README.md.
**Expected:** Clear statement that `LlmKeySessionHolder` and all downstream AI components require no code changes when upgrading from form login to OAuth2.
**Why human:** Content quality and clarity judgment for the documentation goal.

---

### Gaps Summary

No gaps. All 10 observable truths are VERIFIED at code level. All required artifacts exist, are substantive, and are correctly wired. Data flows from the Phase 2 REST API through the Pinia store to every chart and table component. No TBD/FIXME/XXX debt markers found. The 14 human-verification items are standard visual/behavioural checks for a front-end phase and do not indicate missing implementation.

The build gate (exit 0, 715 modules) and test gate (35/35 Vitest tests) confirm the mechanically-verifiable correctness of the wiring.

---

_Verified: 2026-06-07T07:00:00Z_
_Verifier: Claude (gsd-verifier)_
