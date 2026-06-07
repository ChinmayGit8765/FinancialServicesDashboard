# Phase 3: Frontend Scaffold - Context

**Gathered:** 2026-06-07
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Build the cohesive Vue 3 single-page dashboard that presents all Phase 2 portfolio data via ECharts, with the demo-persona switcher and the existing auth flow, plus README documentation of the OAuth upgrade path. This replaces the Phase 1 placeholder DashboardView with a real, screenshot-ready dashboard driven by Pinia stores fetching the REST API.

**In scope:** Vue 3 (Composition API, `<script setup>`) views + components, ECharts visualizations, Pinia `portfolio` store + reuse of the `auth` store, persona switching that re-scopes all data without a full reload, number/currency/percent formatting, loading/empty/error states, a minimal but polished dark theme, and the AUTH-03 OAuth upgrade-path docs in README.

**Out of scope:** new backend endpoints (Phase 2 is the API; consume it as-is), risk metrics / Monte Carlo / AI panels (Phases 4–8 add those views later), real OAuth implementation (documented only).

Requirements covered: AUTH-03, UI-01.
</domain>

<decisions>
## Implementation Decisions

### Layout & Views
- Single-page dashboard (`DashboardView.vue`) with a fixed top bar: app title/logo, the demo-persona switcher (Alice/Bob/Charlie), and a logout control.
- Responsive grid below the bar:
  - A KPI strip (total market value, total unrealized P&L abs+%, daily change) — placeholder cards for future risk metrics live here too.
  - P&L equity-curve line chart (portfolio value over the seeded window).
  - Benchmark overlay line chart (portfolio vs S&P 500 proxy, both rebased to 100).
  - Allocation chart (donut by sector, with a treemap option).
  - Holdings table (ticker, name, sector, qty, avg cost, price, market value, weight, unrealized P&L abs+%).
  - Transactions table (paginated; date, side, ticker, qty, price, running cost basis).
- Login flow stays as the Phase 1 `LoginView` (form + one-click persona buttons); after login the dashboard loads.

### Charts
- ECharts via `vue-echarts` + `echarts` (already in `frontend/package.json`). Tree-shake imports. Reusable chart components: `PnlChart.vue`, `BenchmarkChart.vue`, `AllocationChart.vue`; table components `HoldingsTable.vue`, `TransactionsTable.vue`.
- Charts bind directly to the chart-ready DTO shapes from Phase 2 (date/value arrays, allocation slices). Minimal transformation in the store/components.

### State (Pinia)
- New `portfolio` store with actions `fetchHoldings`, `fetchPnl`, `fetchAllocation`, `fetchTransactions(page)`, `fetchBenchmark`, holding reactive state + loading/error flags. Uses the existing Axios client (`withCredentials` + `X-XSRF-TOKEN` interceptor from Phase 1).
- Reuse the Phase 1 `auth` store for persona/session. Switching persona calls login-as-persona then refreshes all portfolio store data (re-scope without full page reload).

### Styling / UX
- Modern, professional **dark** financial-dashboard theme (deep neutral background, one accent color for positive/negative deltas — green up / red down, legible mono-ish numerics). Screenshot-ready for the README.
- Format money/percent with `Intl.NumberFormat` (BigDecimal arrives as JSON numbers). Color P&L green/red by sign.
- Loading skeletons/spinners per panel, empty states, and a friendly error state if an API call fails.
- Desktop-first layout (web-first constraint); reasonably graceful narrower widths but no mobile commitment.

### AUTH-03 (OAuth upgrade docs)
- README section documenting the four-step path to add real OAuth (Google/GitHub) via Spring Security OAuth2 login without changing downstream AI features / session handling — aligned with the auth seam established in Phase 1.

### Claude's Discretion
- Exact component decomposition, CSS approach (scoped CSS vs a lightweight utility layer), chart color palette specifics, and grid breakpoints are at Claude's discretion within the dark-professional direction. A UI-SPEC will pin the visual contract.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets (Phase 1 + 2)
- `frontend/`: Vite + Vue 3 + Pinia + vue-router scaffold; `src/api/auth.ts` (Axios withCredentials + XSRF interceptor), `src/stores/auth.ts`, `src/router`, `LoginView.vue` (persona switcher), placeholder `DashboardView.vue`, nginx `/api` proxy. `echarts` + `vue-echarts` already in package.json.
- Backend Phase 2 REST API: `GET /api/portfolio/{holdings,pnl,allocation,transactions,benchmark}` — chart-ready DTOs, session/principal-scoped.

### Established Patterns
- Session-cookie auth + CSRF token interceptor; Pinia store pattern; component `<script setup>`; dark-agnostic placeholder styling to be replaced.

### Integration Points
- Vite dev proxy → backend:8080; production nginx serves built SPA + proxies /api. Persona switch via `/api/auth` login + store refresh.
</code_context>

<specifics>
## Specific Ideas

- This is the phase that produces the README hero screenshots — visual polish matters.
- Re-scope on persona switch must be smooth (no full reload), proving the SPA + session design.
- Leave clear, labelled placeholders where Phase 4 (risk scorecard, correlation heatmap), Phase 5 (Monte Carlo fan chart), and Phase 6+ (AI panels) will slot in, so later phases extend rather than restructure.
</specifics>

<deferred>
## Deferred Ideas

- Risk scorecard / correlation heatmap visuals (Phase 4).
- Monte Carlo fan chart + model selector (Phase 5).
- AI panels: explain-position, commentary, Q&A chat, BYO-key popup (Phases 6–8).
- Real OAuth implementation (documented only).
- Mobile/PWA layout.
</deferred>
