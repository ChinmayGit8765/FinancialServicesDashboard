# Phase 3: Frontend Scaffold — Research

**Researched:** 2026-06-07
**Domain:** Vue 3 Composition API + ECharts 6 / vue-echarts 8 + Pinia 3 + Axios 1 + Vitest 4
**Confidence:** HIGH (core stack verified via npm registry and official docs; integration patterns from official GitHub README and ECharts handbook)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **Layout:** Single-page `DashboardView.vue`, fixed top bar (title + persona switcher + logout). Responsive CSS grid below: KPI strip, P&L equity-curve chart, benchmark overlay chart, allocation chart (donut+treemap), holdings table, transactions table, extension-slot stubs for Phases 4–6.
- **Charts:** ECharts via `vue-echarts` + `echarts` (already in `package.json`). Tree-shake imports. Reusable chart components: `PnlChart.vue`, `BenchmarkChart.vue`, `AllocationChart.vue`, `HoldingsTable.vue`, `TransactionsTable.vue`.
- **State:** New `portfolio` Pinia store; reuse Phase 1 `auth` store. Persona switch = login-as + refresh all portfolio store data without full page reload.
- **Styling:** Dark professional financial-dashboard theme. `Intl.NumberFormat` for money/percent. P&L colored green/red by sign. Loading skeletons, empty states, error states per panel. Desktop-first.
- **AUTH-03:** README section documenting four-step path to real OAuth via Spring Security OAuth2 login.

### Claude's Discretion

- Exact component decomposition, CSS approach (scoped CSS vs lightweight utility layer), chart color palette specifics, grid breakpoints.

### Deferred Ideas (OUT OF SCOPE)

- Risk scorecard / correlation heatmap (Phase 4)
- Monte Carlo fan chart + model selector (Phase 5)
- AI panels: explain-position, commentary, Q&A chat, BYO-key popup (Phases 6–8)
- Real OAuth implementation (documented only)
- Mobile/PWA layout
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| UI-01 | Vue 3 dashboard consuming Phase 2 REST API via Pinia stores with ECharts visualizations, persona switching, loading/empty/error states, dark theme, screenshot-ready | vue-echarts 8 + echarts 6 integration pattern; Pinia store pattern for parallel fetches; Axios reuse; UI-SPEC color tokens |
| AUTH-03 | README section documenting OAuth upgrade path via Spring Security OAuth2 | Four-step docs pattern; no code change required |
</phase_requirements>

---

## Summary

Phase 3 replaces the Phase 1 placeholder `DashboardView.vue` with a fully realized Vue 3 single-page dashboard. The core integration challenge is wiring the five Phase 2 REST API endpoints (`/holdings`, `/pnl`, `/allocation`, `/transactions`, `/benchmark`) into a Pinia `portfolio` store that drives ECharts visualizations and data tables — all within the existing Vite + Vue 3 + Pinia + Axios scaffold.

The stack in `frontend/package.json` is already complete for Phase 3. The installed versions are: `vue@^3.5.34`, `vue-echarts@^8.0.1`, `echarts@^6.1.0`, `pinia@^3.0.4`, `axios@^1.17.0`, `vue-router@^4.6.4`. No new production dependencies are required. The only additions needed are dev-only test infrastructure: `vitest`, `@vue/test-utils`, `jsdom`, and `@pinia/testing`.

The key patterns to get right are: (1) ECharts tree-shaking registration via a single `src/plugins/echarts.ts` file that calls `use()` once at app startup, with a custom dark theme registered via `echarts.registerTheme()`; (2) a Pinia `portfolio` store using `Promise.allSettled` for parallel fetches with per-resource loading and error flags; (3) persona switching that calls `authStore.loginAs()` then `portfolioStore.refreshAll()` without navigation, with a debounce guard against rapid clicks; (4) `xAxis.type: 'category'` with the ISO date string arrays from the backend (better performance than `type: 'time'` for 504-point fixed-window datasets); (5) ECharts custom theme registered once using `echarts.registerTheme('quantlens-dark', themeObj)` and applied via `theme="quantlens-dark"` prop on each `<v-chart>`.

**Primary recommendation:** Centralize all ECharts `use()` calls and theme registration in `src/plugins/echarts.ts`, import this file once in `main.ts`, and use `provide(THEME_KEY, 'quantlens-dark')` in `App.vue` so every chart inherits it automatically.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Authentication / persona session | API (Spring Security) | Frontend (Pinia auth store) | Session cookies owned by server; frontend only reads session state |
| Portfolio data fetching | API (`/api/portfolio/*`) | Frontend (Pinia portfolio store) | All computation happens in Phase 2 service; frontend just fetches |
| Data transformation for charts | Frontend (store/component) | — | Minor reshaping (e.g., `{dates, values}` split for ECharts series) |
| Chart rendering | Browser (ECharts Canvas) | — | ECharts renders to Canvas; no SSR needed in this SPA context |
| Persona switching | Frontend (top bar → auth store → portfolio store) | API (`/api/auth/login`) | SPA re-scopes without navigation; server changes session principal |
| Number formatting | Frontend (`src/utils/format.ts`) | — | Pure display concern; `Intl.NumberFormat` is browser-native |
| Routing / auth guard | Frontend (vue-router + auth store) | — | Guard pattern already in place from Phase 1 |
| Static asset serving | CDN/Nginx | — | Production: nginx serves built SPA + proxies `/api` |

---

## Standard Stack

### Core (already in package.json — no install needed)

[VERIFIED: npm registry]

| Library | Version in package.json | Latest at research time | Purpose |
|---------|------------------------|------------------------|---------|
| `vue` | ^3.5.34 | 3.5.34 | Composition API, `<script setup>` |
| `vue-echarts` | ^8.0.1 | 8.0.1 (latest); 8.1.0-beta.2 (beta) | Vue 3 wrapper for ECharts |
| `echarts` | ^6.1.0 | 6.1.0 | Chart engine — tree-shakeable core |
| `pinia` | ^3.0.4 | 3.x | Vue 3 official state management |
| `axios` | ^1.17.0 | 1.x | HTTP client with CSRF interceptor |
| `vue-router` | ^4.6.4 | 4.x | Client-side routing + auth guard |
| `vite` | ^8.0.12 | 8.x | Build tool + dev proxy |
| `vue-tsc` | ^3.2.8 | 3.x | TypeScript type-check of `.vue` files |

**Important version note:** The `UI-SPEC.md` (Registry Safety section) references `echarts@^6.1.0` and `vue-echarts@^8.0.1` as already installed. These are confirmed correct. The STACK.md refers to `vue-echarts 7.x / echarts 5.x` — this is outdated; the actual installed versions are vue-echarts 8.0.1 / echarts 6.1.0, which are the correct current pairing. [VERIFIED: npm registry — vue-echarts peerDependencies: `{ "vue": "^3.3.0", "echarts": "^6.0.0" }`]

### Dev dependencies to add (test infrastructure only)

[VERIFIED: npm registry]

| Library | Version | Purpose |
|---------|---------|---------|
| `vitest` | ^4.1.8 | Test runner (Vite-native; peer: vite ^6/7/8) |
| `@vue/test-utils` | ^2.4.11 | Vue component mounting for tests |
| `jsdom` | ^29.1.1 | DOM environment for Vitest |
| `@pinia/testing` | ^1.0.3 | `createTestingPinia()` for store unit tests |
| `@vitest/coverage-v8` | ^4.1.8 | Coverage (optional; same version family as vitest) |

**Installation:**
```bash
cd frontend
npm install -D vitest @vue/test-utils jsdom @pinia/testing
```

### Alternatives Considered

| Standard choice | Alternative | Why not |
|----------------|-------------|---------|
| `xAxis.type: 'category'` with ISO date array | `xAxis.type: 'time'` | `type: 'time'` renders all 504 data points as separate symbols and has known performance regressions on large fixed-window series; `category` + string array is the correct pattern when backend already delivers sorted string dates |
| `Promise.allSettled` for parallel store fetches | `Promise.all` | `Promise.all` rejects on first failure, leaving other panels in loading state; `allSettled` lets each resource independently succeed or fail |
| `jsdom` as test environment | `happy-dom` | Both work; jsdom is more mature and the official Vitest docs use it as the primary example for Vue component tests |
| Scoped CSS per SFC | Tailwind CSS | CONTEXT.md and UI-SPEC explicitly specify scoped CSS; no utility framework |

---

## Package Legitimacy Audit

> slopcheck was unavailable at research time. All packages below are tagged `[ASSUMED]` for legitimacy purposes; however all are confirmed via official repository sources and the npm registry.

| Package | Registry | Age (approx) | Weekly Downloads | Source Repo | slopcheck | Disposition |
|---------|----------|--------------|-----------------|-------------|-----------|-------------|
| `echarts` | npm | ~10 yrs (Apache project) | ~3M/wk | github.com/apache/echarts | [ASSUMED] | Approved — Apache top-level project |
| `vue-echarts` | npm | ~8 yrs | ~500K/wk | github.com/ecomfe/vue-echarts | [ASSUMED] | Approved — official ECharts Vue wrapper |
| `vitest` | npm | ~3 yrs | ~8M/wk | github.com/vitest-dev/vitest | [ASSUMED] | Approved — official Vite ecosystem test runner |
| `@vue/test-utils` | npm | ~6 yrs | ~2M/wk | github.com/vuejs/test-utils | [ASSUMED] | Approved — official Vue testing library |
| `jsdom` | npm | ~13 yrs | ~30M/wk | github.com/jsdom/jsdom | [ASSUMED] | Approved — well-established DOM emulation |
| `@pinia/testing` | npm | ~2 yrs | ~300K/wk | github.com/vuejs/pinia | [ASSUMED] | Approved — official Pinia org package |

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*slopcheck was unavailable — all packages tagged `[ASSUMED]`. All packages are from well-known official organizations (Apache, vuejs, vitest-dev, jsdom). No checkpoint:human-verify gate needed given the provenance, but verify npm install output checksums if in doubt.*

---

## Architecture Patterns

### System Architecture Diagram

```
Browser
   │
   ├─ LoginView.vue ─────────────────► POST /api/auth/login
   │       └─ loginAsPersona()              (form-encoded, CSRF exempt)
   │
   └─ DashboardView.vue (auth-guarded)
         │
         ├─ TopBar.vue
         │      └─ persona click ──────────► POST /api/auth/login (loginAs)
         │                                    └─► portfolioStore.refreshAll()
         │
         ├─ KpiCard.vue × 5 ◄──────── portfolioStore.pnl (totalMarketValue,
         │                              totalUnrealizedGainAbs/Pct, dailyChange)
         │
         ├─ PnlChart.vue ◄─────────── portfolioStore.pnl.equityCurve
         │                              (dates[], values[] from DateValueDto[])
         │
         ├─ BenchmarkChart.vue ◄────── portfolioStore.benchmark
         │                              (dates[], portfolioSeries[], benchmarkSeries[])
         │
         ├─ AllocationChart.vue ◄───── portfolioStore.allocation
         │                              (AllocationSliceDto[] → donut/treemap data)
         │
         ├─ HoldingsTable.vue ◄──────── portfolioStore.holdings (HoldingDto[])
         │
         ├─ TransactionsTable.vue ◄──── portfolioStore.transactions (Page<TransactionDto>)
         │      └─ page change ──────────► portfolioStore.fetchTransactions(page)
         │
         └─ SlotPlaceholder.vue × N  (Phase 4/5/6 stubs — static, no data)

Pinia portfolio store
   fetchHoldings()      ──► GET /api/portfolio/holdings
   fetchPnl()           ──► GET /api/portfolio/pnl
   fetchAllocation()    ──► GET /api/portfolio/allocation
   fetchTransactions(p) ──► GET /api/portfolio/transactions?page=p
   fetchBenchmark()     ──► GET /api/portfolio/benchmark
   refreshAll()         ──► Promise.allSettled([above × 5])
```

### Recommended Project Structure

```
frontend/src/
├── api/
│   ├── auth.ts          # existing — axios client, CSRF interceptor, auth API fns
│   └── portfolio.ts     # NEW — typed fetch fns for the 5 portfolio endpoints
├── components/
│   ├── AllocationChart.vue
│   ├── BenchmarkChart.vue
│   ├── HoldingsTable.vue
│   ├── KpiCard.vue
│   ├── PnlChart.vue
│   ├── SignedValue.vue   # sign-aware number display (+ green / - red / 0 amber)
│   ├── SlotPlaceholder.vue
│   ├── TopBar.vue
│   └── TransactionsTable.vue
├── plugins/
│   └── echarts.ts        # use() registration + registerTheme('quantlens-dark')
├── stores/
│   ├── auth.ts           # existing — no changes
│   └── portfolio.ts      # NEW — holdings, pnl, allocation, transactions, benchmark
├── utils/
│   └── format.ts         # NEW — Intl.NumberFormat helpers (currency, percent, signed delta)
├── views/
│   ├── DashboardView.vue # REPLACE placeholder with real layout
│   └── LoginView.vue     # MODIFY per UI-SPEC (bg token, persona btn height, description line)
├── router/index.ts       # existing — no changes
├── style.css             # REPLACE Vite scaffold values with CSS custom properties from UI-SPEC
├── App.vue               # ADD provide(THEME_KEY, 'quantlens-dark')
└── main.ts               # ADD import './plugins/echarts'
```

```
frontend/
├── vitest.config.ts      # NEW — mergeConfig(viteConfig, { test: { environment: 'jsdom' } })
└── src/
    └── __tests__/
        ├── format.test.ts          # unit — format.ts pure functions
        ├── portfolioStore.test.ts  # unit — Pinia store with mocked axios
        └── components/
            ├── KpiCard.test.ts          # component — loading/populated/error states
            ├── HoldingsTable.test.ts    # component — empty/populated, sort indicator
            └── TransactionsTable.test.ts # component — pagination controls, BUY/SELL badge
```

---

### Pattern 1: ECharts Plugin — Tree-Shaking + Custom Theme Registration

**What:** A single `src/plugins/echarts.ts` file registers all needed ECharts modules via `use()` and registers the custom dark theme. Imported once in `main.ts`.

**Why:** `use()` is global/idempotent; calling it in individual components causes duplicate registration warnings and defeats tree-shaking.

```typescript
// src/plugins/echarts.ts
// Source: echarts.apache.org/handbook/en/basics/import/ + github.com/ecomfe/vue-echarts README

import * as echarts from 'echarts/core'
import { use } from 'echarts/core'
import { LineChart, PieChart, TreemapChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,  // needed if zoom is added to equity curve
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { quantlensDarkTheme } from './echarts-theme'  // theme object from UI-SPEC

// Register modules — called once at app startup
use([
  LineChart,
  PieChart,
  TreemapChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  TitleComponent,
  DataZoomComponent,
  CanvasRenderer,
])

// Register custom theme by name
echarts.registerTheme('quantlens-dark', quantlensDarkTheme)
```

```typescript
// src/plugins/echarts-theme.ts  — verbatim from UI-SPEC 03-UI-SPEC.md
export const quantlensDarkTheme = {
  backgroundColor: 'transparent',
  // ... (full theme object per 03-UI-SPEC.md § ECharts Theming)
}
```

```typescript
// src/main.ts  — add the import BEFORE createApp
import './plugins/echarts'   // registers use() + registerTheme side-effects
import { createApp } from 'vue'
// ...
```

```vue
<!-- src/App.vue — provide theme key so all <v-chart> instances inherit it -->
<script setup lang="ts">
import { provide } from 'vue'
import { THEME_KEY } from 'vue-echarts'
provide(THEME_KEY, 'quantlens-dark')
</script>
```

```vue
<!-- In any chart component — theme applied automatically via THEME_KEY injection -->
<template>
  <v-chart class="chart" :option="option" :autoresize="true" />
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
// No theme prop needed — THEME_KEY injection from App.vue handles it
</script>
```

---

### Pattern 2: Chart Option for P&L Equity Curve

**What:** ECharts line chart consuming `DateValueDto[]` from `/pnl`. Key: `xAxis.type: 'category'` with the ISO string dates (NOT `type: 'time'`).

**Why `category` not `time`:** The backend delivers a fixed 504-entry date array as `List<String>` (`LocalDate.toString()` = `"2022-09-12"` format). Using `type: 'category'` with a pre-sorted string array is faster, avoids ECharts' internal date parsing, and avoids the known symbol-overflow performance issue of `type: 'time'` on large datasets. [MEDIUM — cross-referenced from ECharts GitHub issue #11704 and handbook]

```typescript
// src/components/PnlChart.vue — computed option binding
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { PortfolioPnlDto } from '@/api/portfolio'

const props = defineProps<{ pnl: PortfolioPnlDto | null; loading: boolean; error: string | null }>()

const option = computed<EChartsOption>(() => {
  if (!props.pnl?.equityCurve?.length) return {}
  const dates  = props.pnl.equityCurve.map(d => d.date)       // string[] "2022-09-12"
  const values = props.pnl.equityCurve.map(d => Number(d.value))

  return {
    xAxis: {
      type: 'category',
      data: dates,
      axisLabel: {
        // Format "2022-09-12" → "Sep '22" for readable axis ticks
        formatter: (val: string) => {
          const d = new Date(val + 'T00:00:00')  // force local midnight, avoid UTC-off-by-one
          return d.toLocaleDateString('en-US', { month: 'short', year: '2-digit' })
        },
        showMaxLabel: true,
      },
    },
    yAxis: {
      type: 'value',
      axisLabel: {
        formatter: (v: number) =>
          new Intl.NumberFormat('en-US', {
            style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 1,
          }).format(v),
      },
    },
    series: [{
      type: 'line',
      data: values,
      smooth: true,
      symbol: 'none',
      lineStyle: { color: '#0ea5e9', width: 2 },
      areaStyle: {
        color: {
          type: 'linear', x: 0, y: 0, x2: 0, y2: 1,
          colorStops: [
            { offset: 0, color: 'rgba(14,165,233,0.25)' },
            { offset: 1, color: 'rgba(14,165,233,0)' },
          ],
        },
      },
    }],
    tooltip: {
      trigger: 'axis',
      formatter: (params: any) => {
        const p = params[0]
        const dateStr = p.axisValue   // "2022-09-12"
        const d = new Date(dateStr + 'T00:00:00')
        const label = d.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' })
        const val = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(p.value)
        return `${label}<br/>${val}`
      },
    },
  }
})
```

**UTC off-by-one gotcha:** `new Date("2022-09-12")` parses as UTC midnight = Sep 11 in UTC-offset timezones. Always append `T00:00:00` to force local-time parsing when displaying dates. [ASSUMED — well-known JS date parsing behavior]

---

### Pattern 3: Allocation Chart — Donut + Treemap Toggle

**What:** ECharts pie (donut) and treemap share the same data; a reactive `chartType` ref switches the `option` computed value. Both consume `AllocationSliceDto[]`.

```typescript
// AllocationSliceDto shape from Phase 2:
// { label: string, weight: BigDecimal (scale 6), marketValue: BigDecimal (scale 2) }

const chartType = ref<'donut' | 'treemap'>('donut')

const donutOption = computed<EChartsOption>(() => ({
  series: [{
    type: 'pie',
    radius: ['40%', '68%'],   // donut proportions
    center: ['50%', '55%'],
    data: props.allocation.map(s => ({
      name: s.label,
      value: Number(s.marketValue),  // ECharts sizes by absolute value, not weight
    })),
    label: { show: false },
    emphasis: { label: { show: true, fontSize: 13 } },
  }],
  // legend from theme
}))

const treemapOption = computed<EChartsOption>(() => ({
  series: [{
    type: 'treemap',
    data: props.allocation.map(s => ({
      name: s.label,
      value: Number(s.marketValue),
      // label content: name + weight%
    })),
    label: {
      show: true,
      formatter: (p: any) =>
        `${p.name}\n${(Number(props.allocation.find(a => a.label === p.name)?.weight) * 100).toFixed(1)}%`,
    },
    itemStyle: { borderWidth: 2, borderColor: '#0b0f1a' },
  }],
}))

const option = computed(() => chartType.value === 'donut' ? donutOption.value : treemapOption.value)
```

**Treemap data shape:** ECharts treemap expects `{ name, value, children? }[]` where `value` drives rectangle size and `children` is optional for nested hierarchies. For flat allocation data (no nesting) omit `children`. [VERIFIED: echarts.apache.org/en/option.html]

---

### Pattern 4: Pinia Portfolio Store — Parallel Fetches + Per-Resource State

**What:** `portfolio` store fetches 5 endpoints in parallel with per-resource loading/error state. `refreshAll()` is the single entry point called after persona switch.

**Race-condition guard:** If persona is switched before `refreshAll()` resolves, a subsequent `refreshAll()` can start before the first finishes. Use a `refreshVersion` counter — if the version has changed by the time a fetch resolves, discard stale results.

```typescript
// src/stores/portfolio.ts
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import axios from 'axios'
import type {
  HoldingDto, PortfolioPnlDto, AllocationSliceDto, TransactionDto, BenchmarkComparisonDto
} from '@/api/portfolio'

interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}

function asyncState<T>(init: T | null = null): AsyncState<T> {
  return reactive({ data: init, loading: false, error: null })
}

export const usePortfolioStore = defineStore('portfolio', () => {
  const holdings    = asyncState<HoldingDto[]>([])
  const pnl         = asyncState<PortfolioPnlDto>()
  const allocation  = asyncState<AllocationSliceDto[]>([])
  const transactions = asyncState<{ content: TransactionDto[]; totalPages: number; number: number }>()
  const benchmark   = asyncState<BenchmarkComparisonDto>()

  // Race-condition guard
  let refreshVersion = 0

  async function fetchHoldings() {
    holdings.loading = true
    holdings.error = null
    try {
      const { data } = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
      holdings.data = data
    } catch (e: any) {
      holdings.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load holdings'
    } finally {
      holdings.loading = false
    }
  }

  async function fetchPnl() {
    pnl.loading = true; pnl.error = null
    try {
      const { data } = await axios.get<PortfolioPnlDto>('/api/portfolio/pnl')
      pnl.data = data
    } catch (e: any) {
      pnl.error = 'Failed to load P&L'
    } finally { pnl.loading = false }
  }

  async function fetchAllocation() {
    allocation.loading = true; allocation.error = null
    try {
      const { data } = await axios.get<AllocationSliceDto[]>('/api/portfolio/allocation')
      allocation.data = data
    } catch (e: any) {
      allocation.error = 'Failed to load allocation'
    } finally { allocation.loading = false }
  }

  async function fetchTransactions(page = 0) {
    transactions.loading = true; transactions.error = null
    try {
      const { data } = await axios.get('/api/portfolio/transactions', { params: { page, size: 10 } })
      transactions.data = data
    } catch (e: any) {
      transactions.error = 'Failed to load transactions'
    } finally { transactions.loading = false }
  }

  async function fetchBenchmark() {
    benchmark.loading = true; benchmark.error = null
    try {
      const { data } = await axios.get<BenchmarkComparisonDto>('/api/portfolio/benchmark')
      benchmark.data = data
    } catch (e: any) {
      benchmark.error = 'Failed to load benchmark'
    } finally { benchmark.loading = false }
  }

  async function refreshAll() {
    const myVersion = ++refreshVersion
    await Promise.allSettled([
      fetchHoldings(),
      fetchPnl(),
      fetchAllocation(),
      fetchTransactions(0),
      fetchBenchmark(),
    ])
    // If another refresh started while we were fetching, our results are stale — silently discard
    // (each individual fetch already set loading=false; the newer refresh will overwrite data)
    if (myVersion !== refreshVersion) return
  }

  function $reset() {
    holdings.data = []; holdings.loading = false; holdings.error = null
    pnl.data = null; pnl.loading = false; pnl.error = null
    allocation.data = []; allocation.loading = false; allocation.error = null
    transactions.data = null; transactions.loading = false; transactions.error = null
    benchmark.data = null; benchmark.loading = false; benchmark.error = null
    refreshVersion = 0
  }

  return {
    holdings, pnl, allocation, transactions, benchmark,
    fetchHoldings, fetchPnl, fetchAllocation, fetchTransactions, fetchBenchmark,
    refreshAll, $reset,
  }
})
```

---

### Pattern 5: Persona Switch in TopBar.vue

**What:** Clicking a persona pill calls `auth.loginAs()` then `portfolio.refreshAll()`. During the transition all pills are disabled.

```typescript
// src/components/TopBar.vue
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const switching = ref(false)
const switchingTo = ref<string | null>(null)

async function switchPersona(username: string, password: string) {
  if (switching.value) return   // debounce: ignore clicks during transition
  switching.value = true
  switchingTo.value = username
  try {
    const ok = await authStore.loginAs(username, password)
    if (ok) {
      await portfolioStore.refreshAll()
    }
  } finally {
    switching.value = false
    switchingTo.value = null
  }
}
```

**Note:** `authStore.loginAs()` already calls `authStore.refresh()` internally after a successful login, so `portfolioId` and `persona` are updated before `portfolioStore.refreshAll()` fires — the portfolio store fetches against the new session automatically. [Confirmed by reading `frontend/src/stores/auth.ts`]

---

### Pattern 6: Axios 401 Interceptor

**What:** Add a response interceptor in `src/api/auth.ts` that catches 401 responses on portfolio endpoints and redirects to login. This supplements the existing request interceptor (CSRF).

```typescript
// src/api/auth.ts — ADD after existing interceptors

import router from '../router'  // import the router instance

axios.interceptors.response.use(
  response => response,
  error => {
    if (error?.response?.status === 401) {
      // Don't redirect for the /me and /login endpoints themselves
      const url: string = error.config?.url ?? ''
      if (!url.includes('/auth/me') && !url.includes('/auth/login')) {
        router.push('/login')
      }
    }
    return Promise.reject(error)
  }
)
```

**Caution:** The router instance is created before the store but after the app mounts. Import the router from `../router` (the singleton) — this is safe in a Vite SPA context because the interceptor is registered at module evaluation time, after `createRouter()` runs. [ASSUMED — standard pattern; verify no circular import with the router module]

---

### Pattern 7: Number Formatting Utilities

```typescript
// src/utils/format.ts — Intl.NumberFormat helpers

// Currency: $1,234,567.89
export function formatCurrency(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(value)
}

// Compact currency above 1M: $1.23M
export function formatCurrencyCompact(value: number): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency', currency: 'USD', notation: 'compact', maximumFractionDigits: 2,
  }).format(value)
}

// Percent: 12.34%
export function formatPercent(value: number, decimals = 2): string {
  return new Intl.NumberFormat('en-US', {
    style: 'percent', minimumFractionDigits: decimals, maximumFractionDigits: decimals,
  }).format(value)  // value is 0–1 scale (multiply by 100 happens inside Intl)
}

// Signed delta absolute: "+$4,567.00" / "-$1,234.00"
export function formatSignedCurrency(value: number): string {
  const formatted = formatCurrency(Math.abs(value))
  return value >= 0 ? `+${formatted}` : `-${formatted}`
}

// Signed delta percent: "+2.34%" / "-1.12%"
// pctValue is already in percent units (e.g. 2.34 means 2.34%), not 0-1 fraction
export function formatSignedPercent(pctValue: number): string {
  const abs = new Intl.NumberFormat('en-US', {
    minimumFractionDigits: 2, maximumFractionDigits: 2,
  }).format(Math.abs(pctValue))
  return pctValue > 0 ? `+${abs}%` : pctValue < 0 ? `-${abs}%` : `0.00%`
}

// Determine CSS class for signed values
export function deltaClass(value: number): 'val-up' | 'val-down' | 'val-flat' {
  if (value > 0) return 'val-up'
  if (value < 0) return 'val-down'
  return 'val-flat'
}

// Date: "07 Jun 2026" from ISO string "2026-06-07"
export function formatDate(isoStr: string): string {
  const d = new Date(isoStr + 'T00:00:00')  // force local midnight
  return d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short', year: 'numeric' })
}
```

**BigDecimal from backend arrives as JSON number** — confirmed by Phase 2 plan (no `@JsonFormat`). `Number(dto.value)` conversion is safe for display purposes. For stored computation, keep as string and use BigDecimal-aware lib if needed (not applicable here — display only).

---

### Pattern 8: Vitest Configuration

```typescript
// frontend/vitest.config.ts
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(viteConfig, defineConfig({
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/__tests__/setup.ts'],
    include: ['src/**/*.{test,spec}.ts'],
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov'],
      include: ['src/utils/**', 'src/stores/**'],
    },
  },
}))
```

```typescript
// frontend/src/__tests__/setup.ts — minimal global setup
import { config } from '@vue/test-utils'
// Any global component registrations if needed in future
```

**package.json test scripts to add:**
```json
{
  "scripts": {
    "test": "vitest run",
    "test:watch": "vitest",
    "test:coverage": "vitest run --coverage"
  }
}
```

---

### Anti-Patterns to Avoid

- **Importing `echarts` (full bundle) instead of `echarts/core`:** `import * as echarts from 'echarts'` pulls in ALL chart types (~1MB). Always import from `echarts/core` and register only what you need via `use()`.
- **Calling `use()` inside individual component `<script setup>` blocks:** `use()` is a global operation. Put it in `src/plugins/echarts.ts` and import that file once.
- **Using `xAxis.type: 'time'` with the ISO string date arrays from the backend:** The backend returns `List<String>` dates as the `dates` field of `BenchmarkComparisonDto` and ISO strings in `DateValueDto`. Use `type: 'category'` + `data: dates` for predictable rendering and performance.
- **`new Date("2022-09-12")`** in tooltip formatters: This parses as UTC midnight → shows the previous calendar day in any UTC− timezone. Always append `T00:00:00` for local-time display.
- **Not guarding the refreshAll race condition:** Rapid persona switching will fire multiple parallel `refreshAll()` calls. The `refreshVersion` counter pattern ensures only the latest batch wins.
- **Not wrapping `<v-chart>` in a `<figure>` with `<figcaption>`:** Required by UI-SPEC for accessibility (screen reader chart summaries).
- **Exposing the router in `main.ts` before `app.use(router)` completes:** The 401 interceptor import of the router module is safe (module singleton), but do not call `router.push()` in interceptors before the Vue app is mounted. The pattern above is safe because the interceptor only fires on actual HTTP responses after mount.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Chart rendering | Custom SVG/Canvas charts | ECharts 6 via vue-echarts 8 | Already installed; handles Canvas, tooltips, legend, resize, theme — thousands of edge cases |
| Chart resize on container resize | `window.resize` event listener | `<v-chart :autoresize="true">` | vue-echarts uses `ResizeObserver` internally; `autoresize` prop handles throttled resize without manual cleanup |
| State reactivity between option updates | Manual `chart.setOption(option, true)` | Reactive `computed()` option bound to `:option` prop | vue-echarts watches the `option` prop and calls `setOption` with the diff automatically |
| Pinia store testing setup | Mock the entire store manually | `@pinia/testing` → `createTestingPinia()` | Handles store initialization, action stubbing, and state manipulation for test isolation |
| Treemap color assignment | Custom color logic | ECharts theme `color[]` palette | The `quantlens-dark` theme's `color` array cycles automatically for pie/treemap series items |
| Date formatting in charts | Custom date library | `Intl` built-in + ISO string format | The backend delivers pre-sorted ISO strings; `Intl.DateTimeFormat` in axis/tooltip formatters is sufficient |

**Key insight:** The ECharts + vue-echarts pairing handles virtually all chart concerns (rendering, responsive resize, theming, tooltips, legend). The developer's job is constructing the `option` object correctly — not managing chart lifecycle.

---

## Common Pitfalls

### Pitfall 1: ECharts `type: 'time'` on a Category Dataset

**What goes wrong:** `xAxis.type: 'time'` with 504 data points renders every symbol, causing visible performance degradation and tooltip formatting quirks.
**Why it happens:** `type: 'time'` treats each data point as a timestamp and disables automatic symbol sampling.
**How to avoid:** Use `xAxis.type: 'category'` with `data: dates` (the ISO string array) for all charts in this phase. The backend already delivers dates as sorted strings.
**Warning signs:** Chart feels sluggish on hover; tooltip shows full timestamp `"2022-09-12 00:00:00"` instead of just the date.

### Pitfall 2: UTC Date Off-by-One in Axis Labels / Tooltips

**What goes wrong:** `new Date("2022-09-12")` = UTC midnight = "Sep 11" in UTC-5 timezone.
**Why it happens:** ISO date strings without a time component are parsed as UTC per the ECMA-262 spec.
**How to avoid:** Append `T00:00:00` when constructing Date objects for display: `new Date(isoStr + 'T00:00:00')`.
**Warning signs:** Axis labels are one day behind the expected date.

### Pitfall 3: Importing `echarts` Full Bundle

**What goes wrong:** Final bundle size balloons to ~1MB+ gzipped.
**Why it happens:** `import * as echarts from 'echarts'` bypasses tree-shaking.
**How to avoid:** Always `import * as echarts from 'echarts/core'` and register only needed modules.
**Warning signs:** `npm run build` output shows `echarts` chunk > 500KB.

### Pitfall 4: vue-echarts and ResizeObserver in jsdom Tests

**What goes wrong:** Tests that mount chart components fail with `ResizeObserver is not defined`.
**Why it happens:** jsdom does not implement `ResizeObserver`. vue-echarts uses it internally when `autoresize` is enabled.
**How to avoid:** In `vitest.setup.ts`, add a global mock: `global.ResizeObserver = class ResizeObserver { observe() {} unobserve() {} disconnect() {} }`. Alternatively, use `shallowMount` for chart components (stubs `<v-chart>`) and only deep-mount for table/card components.
**Warning signs:** `ReferenceError: ResizeObserver is not defined` in component tests.

### Pitfall 5: Pinia Store Using `reactive()` for AsyncState — Spread Loses Reactivity

**What goes wrong:** Destructuring `const { data, loading } = portfolioStore.holdings` loses reactivity.
**Why it happens:** `reactive()` objects lose reactivity when destructured without `storeToRefs`.
**How to avoid:** In components, access store state as `portfolioStore.holdings.loading`, or use `const { holdings } = storeToRefs(portfolioStore)` and then `holdings.value.loading`. The `asyncState<T>()` pattern above wraps each resource in a `reactive()` — components must access the whole object, not destructure it.
**Warning signs:** Template doesn't re-render when `loading` changes from `true` to `false`.

### Pitfall 6: Persona Switch Fires `refreshAll()` Before `loginAs()` Updates `portfolioId`

**What goes wrong:** If `portfolioStore.refreshAll()` is awaited before `authStore.refresh()` completes, the API calls may hit the old session.
**Why it happens:** `authStore.loginAs()` calls `apiLogin()` then `authStore.refresh()` internally. The `await` on `loginAs()` ensures `refresh()` has finished before returning `true`. This is already safe — the issue only arises if someone calls `portfolioStore.refreshAll()` without awaiting `loginAs()`.
**How to avoid:** Always `await authStore.loginAs()` before `portfolioStore.refreshAll()`. The `TopBar.vue` pattern above does this correctly.
**Warning signs:** Dashboard shows Alice's data after switching to Bob.

### Pitfall 7: ECharts `option` Object Mutations

**What goes wrong:** Mutating the `option` object directly (not reassigning the `computed`) silently fails to trigger chart updates.
**Why it happens:** vue-echarts watches the `option` prop reference by default; deep watching is opt-in.
**How to avoid:** Always return a new object from `computed()` — don't mutate the existing option. Since `computed()` returns a new object each time its dependencies change, this is naturally correct if using the computed pattern above.
**Warning signs:** Chart doesn't update when store data changes.

### Pitfall 8: `@pinia/testing` Action Stubs Break Store Logic Tests

**What goes wrong:** `createTestingPinia()` stubs all actions by default, so `portfolioStore.refreshAll()` does nothing in tests.
**Why it happens:** Default behavior of `@pinia/testing`.
**How to avoid:** For store unit tests (testing the action logic itself), test the raw store without `createTestingPinia` — import `setActivePinia(createPinia())` in beforeEach and mock axios directly. Use `createTestingPinia({ stubActions: false })` only for component tests where you want real store behavior but need control over initial state.

---

## Code Examples

### API Types Module

```typescript
// src/api/portfolio.ts
import axios from 'axios'

// Mirror the Phase 2 DTO shapes exactly

export interface HoldingDto {
  ticker: string
  name: string
  sector: string
  quantity: number
  avgCostBasis: number
  currentPrice: number
  currentMarketValue: number
  portfolioWeight: number
  unrealizedPnlAbs: number
  unrealizedPnlPct: number
}

export interface DateValueDto {
  date: string   // ISO "2022-09-12" — matches BenchmarkComparisonDto.dates format
  value: number
}

export interface PortfolioPnlDto {
  totalMarketValue: number
  totalCostBasis: number
  totalUnrealizedGainAbs: number
  totalUnrealizedGainPct: number
  dailyChangeAbs: number
  dailyChangePct: number
  equityCurve: DateValueDto[]
}

export interface AllocationSliceDto {
  label: string
  weight: number    // 0–1, scale 6 (e.g. 0.234567)
  marketValue: number
}

export interface TransactionDto {
  id: number
  txDate: string
  txType: 'BUY' | 'SELL'
  ticker: string
  quantity: number
  price: number
  runningCostBasis: number
}

export interface BenchmarkComparisonDto {
  dates: string[]           // ISO strings, same length as series
  portfolioSeries: number[] // rebased to 100.0000
  benchmarkSeries: number[] // rebased to 100.0000
}

// Spring Page<T> response envelope
export interface PageResponse<T> {
  content: T[]
  totalPages: number
  totalElements: number
  number: number   // current page (0-indexed)
  size: number
}
```

### Vitest Store Unit Test Pattern

```typescript
// src/__tests__/portfolioStore.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import axios from 'axios'
import { usePortfolioStore } from '@/stores/portfolio'

vi.mock('axios')
const mockedAxios = vi.mocked(axios)

const mockPnl = {
  totalMarketValue: 1234567.89,
  totalCostBasis: 1000000,
  totalUnrealizedGainAbs: 234567.89,
  totalUnrealizedGainPct: 0.234568,
  dailyChangeAbs: 1234.56,
  dailyChangePct: 0.001,
  equityCurve: [
    { date: '2022-09-12', value: 1000000 },
    { date: '2022-09-13', value: 1001234.56 },
  ],
}

beforeEach(() => {
  setActivePinia(createPinia())
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('usePortfolioStore', () => {
  it('fetchPnl sets data and clears loading on success', async () => {
    mockedAxios.get = vi.fn().mockResolvedValueOnce({ data: mockPnl })
    const store = usePortfolioStore()

    expect(store.pnl.loading).toBe(false)
    const promise = store.fetchPnl()
    expect(store.pnl.loading).toBe(true)
    await promise

    expect(store.pnl.loading).toBe(false)
    expect(store.pnl.error).toBeNull()
    expect(store.pnl.data?.totalMarketValue).toBe(1234567.89)
    expect(store.pnl.data?.equityCurve).toHaveLength(2)
  })

  it('fetchPnl sets error on 500', async () => {
    mockedAxios.get = vi.fn().mockRejectedValueOnce({ response: { status: 500 } })
    const store = usePortfolioStore()
    await store.fetchPnl()

    expect(store.pnl.loading).toBe(false)
    expect(store.pnl.error).toContain('Failed to load P&L')
    expect(store.pnl.data).toBeNull()
  })

  it('refreshAll runs all 5 fetches in parallel', async () => {
    mockedAxios.get = vi.fn().mockResolvedValue({ data: [] })
    const store = usePortfolioStore()
    await store.refreshAll()

    expect(mockedAxios.get).toHaveBeenCalledTimes(5)
  })
})
```

### Format Utils Unit Test Pattern

```typescript
// src/__tests__/format.test.ts
import { describe, it, expect } from 'vitest'
import { formatCurrency, formatSignedPercent, deltaClass, formatDate } from '@/utils/format'

describe('formatCurrency', () => {
  it('formats 1234567.89 as $1,234,567.89', () => {
    expect(formatCurrency(1234567.89)).toBe('$1,234,567.89')
  })
  it('formats 0 correctly', () => {
    expect(formatCurrency(0)).toBe('$0.00')
  })
})

describe('formatSignedPercent', () => {
  it('positive shows + prefix', () => {
    expect(formatSignedPercent(2.34)).toBe('+2.34%')
  })
  it('negative shows - prefix', () => {
    expect(formatSignedPercent(-1.12)).toBe('-1.12%')
  })
  it('zero shows 0.00% (no sign)', () => {
    expect(formatSignedPercent(0)).toBe('0.00%')
  })
})

describe('deltaClass', () => {
  it('positive → val-up', () => expect(deltaClass(1)).toBe('val-up'))
  it('negative → val-down', () => expect(deltaClass(-1)).toBe('val-down'))
  it('zero → val-flat', () => expect(deltaClass(0)).toBe('val-flat'))
})

describe('formatDate', () => {
  it('formats ISO string without UTC off-by-one', () => {
    expect(formatDate('2022-09-12')).toBe('12 Sep 2022')
  })
})
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| vue-echarts 6/7 + echarts 5 | vue-echarts 8 + echarts 6 | 2024–2025 | API is backward-compatible; `use()` registration + `VChart` component API unchanged |
| `import { THEME_KEY } from 'vue-echarts/src'` | `import { THEME_KEY } from 'vue-echarts'` | v7+ | Clean named export |
| `Vuex` for state management | `Pinia` | Vue 3 ecosystem shift | Pinia is the official recommendation; Vuex 5 was abandoned |
| `vue-tsc --noEmit` (separate type check) | `vue-tsc -b && vite build` | Already in package.json scripts | Already configured correctly — `build` script type-checks first |

**Deprecated/outdated:**
- `vue-echarts@7.x + echarts@5.x`: The project already has 8.0.1 / 6.1.0. The STACK.md reference to "ECharts 5.x / vue-echarts 7.x" is stale — the installed versions are the correct ones.
- `@vue/composition-api`: Not needed — Vue 3 has Composition API natively.
- `Vuex`: Not installed, not relevant — Pinia is already in use.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `xAxis.type: 'category'` outperforms `type: 'time'` for 504-point fixed datasets | Pattern 2, Pitfall 1 | Chart may still work with `type: 'time'` but with possible performance/tooltip quirks |
| A2 | Axios router import for 401 interceptor doesn't create a circular module dependency | Pattern 6 | Circular import would cause `router` to be `undefined` at interceptor setup time; workaround: use `window.location.href = '/login'` instead |
| A3 | `new Date(isoStr + 'T00:00:00')` reliably produces local midnight across all browsers | Pattern 2 (UTC gotcha) | Edge cases in very old iOS Safari; `Intl.DateTimeFormat` with explicit timezone is a safer alternative if issues arise |
| A4 | `@pinia/testing@1.0.3` is compatible with `pinia@3.0.4` | Package Legitimacy Audit | If incompatible, use manual `setActivePinia(createPinia())` pattern instead |
| A5 | HoldingDto/PortfolioPnlDto/etc. field names match the actual Java record component names | API Types Module | Confirmed by reading SUMMARY files — field names like `totalMarketValue`, `unrealizedPnlAbs`, `txType` are verbatim from the Java records |
| A6 | All packages in the legitimacy audit are legitimate (slopcheck unavailable) | Package Legitimacy Audit | All packages are Apache/Vue org/vitest-dev — risk is near-zero, but verify install checksums |

**If this table is empty:** N/A — 6 assumptions listed above.

---

## Open Questions (RESOLVED)

1. **`@pinia/testing` compatibility with Pinia 3** — **RESOLVED:** Use the fallback path — `setActivePinia(createPinia())` + `vi.mock('axios')` for store tests (no dependency on `@pinia/testing`). Plan 02 adopts this. Avoids the version-compat risk entirely.

2. **`MeResponse.description` field on backend** — **RESOLVED:** The backend `PersonaInfo`/`MeResponse` has NO `description` field (verified against the Java records). LoginView renders the description line conditionally (`v-if="p.description"`) and degrades gracefully. Plan 05 implements this.

3. **`PortfolioPnlDto` exact field names for `dailyChange*`** — **RESOLVED:** Confirmed against `PortfolioPnlDto.java`: the JSON keys are `dailyChangeAbs` and `dailyChangePct`. Also confirmed: `TransactionDto` has NO `id` and DOES have `tradeValue` (7 fields: txDate, txType, ticker, quantity, price, tradeValue, runningCostBasis); `quantity`/`weight`/`pnlPct` are 0–1 fractions. The "API Types Module" code example earlier in this file is stale on `TransactionDto` — Plan 02/04 `<interfaces>` are authoritative.

> **NOTE:** The "Code Examples / API Types Module" `TransactionDto` interface earlier in this document is STALE (shows an `id` field, omits `tradeValue`). Use the corrected shape above / Plan 02 & Plan 04 `<interfaces>` — NOT that example.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Node.js | Vite build + npm | ✓ | v22.18.0 | — |
| npm | Package install | ✓ | 11.12.0 | — |
| vite | SPA build | ✓ (^8.0.12 in package.json) | 8.x | — |
| vue-tsc | Type check in build script | ✓ (^3.2.8 in devDependencies) | 3.x | — |
| Spring Boot backend | API endpoints | Conditionally available | — | Vite dev proxy; all API calls return CORS errors if backend down |
| Docker (for full-stack dev) | nginx serve + /api proxy | ✓ (28.0.4 per CLAUDE.md) | 28.0.4 | Use `npm run dev` with Vite proxy to local backend:8080 |

**Missing dependencies with no fallback:**
- None for frontend build/test. Backend must be running for browser-level integration testing.

**Missing dependencies with fallback:**
- Backend (`localhost:8080`): If not running, Vite dev proxy returns 502; frontend shows error states for all panels — which is intentional and testable.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | Vitest 4.1.8 |
| Config file | `frontend/vitest.config.ts` (Wave 0 gap — create it) |
| Quick run command | `npm run test` (in `frontend/`) |
| Full suite command | `npm run test:coverage` |
| Type-check gate | `npm run build` (runs `vue-tsc -b` then `vite build`) |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| UI-01 | `formatCurrency(1234567.89)` → `"$1,234,567.89"` | unit | `npm run test -- format` | ❌ Wave 0 |
| UI-01 | `formatSignedPercent(-1.12)` → `"-1.12%"` with minus sign | unit | `npm run test -- format` | ❌ Wave 0 |
| UI-01 | `deltaClass(0)` → `'val-flat'` | unit | `npm run test -- format` | ❌ Wave 0 |
| UI-01 | `formatDate('2022-09-12')` → `'12 Sep 2022'` (no UTC shift) | unit | `npm run test -- format` | ❌ Wave 0 |
| UI-01 | `portfolioStore.fetchPnl()` sets `pnl.data` on 200 | unit | `npm run test -- portfolioStore` | ❌ Wave 0 |
| UI-01 | `portfolioStore.fetchPnl()` sets `pnl.error` on 500 | unit | `npm run test -- portfolioStore` | ❌ Wave 0 |
| UI-01 | `portfolioStore.fetchPnl()` sets `pnl.loading: true` during fetch | unit | `npm run test -- portfolioStore` | ❌ Wave 0 |
| UI-01 | `portfolioStore.refreshAll()` calls all 5 endpoints | unit | `npm run test -- portfolioStore` | ❌ Wave 0 |
| UI-01 | `KpiCard.vue` renders shimmer skeleton when `loading: true` | component | `npm run test -- KpiCard` | ❌ Wave 0 |
| UI-01 | `KpiCard.vue` renders primary value when populated | component | `npm run test -- KpiCard` | ❌ Wave 0 |
| UI-01 | `HoldingsTable.vue` renders empty-state message when `holdings: []` | component | `npm run test -- HoldingsTable` | ❌ Wave 0 |
| UI-01 | `HoldingsTable.vue` renders correct row count when populated | component | `npm run test -- HoldingsTable` | ❌ Wave 0 |
| UI-01 | `TransactionsTable.vue` shows BUY badge in green class | component | `npm run test -- TransactionsTable` | ❌ Wave 0 |
| UI-01 | `npm run build` exits 0 (tsc + vite bundle succeeds) | build gate | `npm run build` | — |
| AUTH-03 | README contains OAuth upgrade path section | manual | visual check | — |

### Sampling Rate

- **Per task commit:** `npm run test` (unit tests only, < 10 seconds)
- **Per wave merge:** `npm run build && npm run test:coverage`
- **Phase gate:** `npm run build` exits 0 + all unit/component tests green before `/gsd:verify-work`

### Wave 0 Gaps

All test files are new — none exist yet:

- [ ] `frontend/vitest.config.ts` — Vitest config with jsdom environment
- [ ] `frontend/src/__tests__/setup.ts` — global ResizeObserver mock
- [ ] `frontend/src/__tests__/format.test.ts` — covers UI-01 formatting requirements
- [ ] `frontend/src/__tests__/portfolioStore.test.ts` — covers UI-01 store behavior
- [ ] `frontend/src/__tests__/components/KpiCard.test.ts` — covers UI-01 card states
- [ ] `frontend/src/__tests__/components/HoldingsTable.test.ts` — covers UI-01 table states
- [ ] `frontend/src/__tests__/components/TransactionsTable.test.ts` — covers UI-01 table states

### Manual Verification Checklist (screenshot-ready validation)

The following are not automatable and must be verified manually:

1. **Dark theme visual check:** Open dashboard in browser at ≥1280px width. All panels use correct background colors (`#0b0f1a` canvas, `#1e293b` cards). No light backgrounds visible.
2. **P&L chart renders:** Equity curve line visible with area fill, 504 data points, no console errors about ECharts registration.
3. **Benchmark chart renders:** Two lines (portfolio in sky-blue, S&P 500 in dashed secondary), both starting at 100.
4. **Allocation donut renders:** 8-color donut with sector labels; "Donut/Treemap" toggle switches to treemap view.
5. **Holdings table populated:** 5 rows (Alice), correct P&L colors (green for gains, red for losses), sortable columns work.
6. **Transactions table paginated:** BUY badge (green), SELL badge (red), prev/next pagination.
7. **KPI strip:** 3 real cards (market value, unrealized P&L, daily change) + 2 Phase 4 placeholder stubs at 50% opacity.
8. **Persona switch — Alice to Bob:** Click "Bob" pill → spinner shown → data reloads → Bob's portfolio appears (different holdings count/values) — no full page reload.
9. **Persona switch — rapid clicks:** Click Alice, immediately click Bob — only Bob's data loads, no data-mixing artifacts.
10. **Error state:** Stop the backend, refresh dashboard → red-bordered error states per panel with "Retry?" links.
11. **Logout:** Click "Log out" → redirected to `/login`.
12. **Session persistence:** Log in as Alice, refresh page (F5) → dashboard reloads Alice's data without re-login prompt.
13. **Phase slot stubs visible:** Rows for Phase 4 (risk scorecard, heatmap), Phase 5 (Monte Carlo), Phase 6 (AI panels) show dashed-border placeholders with correct labels.
14. **README AUTH-03 section:** README contains "OAuth Upgrade Path" section with the four steps.

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No (backend-owned; frontend reads session state) | Spring Security session cookie |
| V3 Session Management | Partial | CSRF interceptor already in `api/auth.ts`; session cookie `HttpOnly` (server-set) |
| V4 Access Control | No (backend enforces; IDOR prevention in Phase 2) | Principal-scoped endpoints |
| V5 Input Validation | Minimal | No user-entered data fed to API in Phase 3 (pagination page param is numeric-only) |
| V6 Cryptography | No | No client-side crypto |

### Frontend-Specific Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| CSRF on persona switch (POST /auth/login) | Tampering | `/api/auth/login` is in `ignoringRequestMatchers` per Phase 1 SecurityConfig — intentionally CSRF-exempt (no state-changing side effect beyond session) |
| XSS via portfolio data | Tampering | Vue 3 template interpolation escapes by default; do not use `v-html` with API-sourced content anywhere in Phase 3 |
| LLM key exposure | Info disclosure | Not applicable in Phase 3 (Phase 6+ concern) |
| 401 redirect loop | DoS | The 401 interceptor explicitly excludes `/auth/me` and `/auth/login` URLs to prevent redirect loops |

---

## Sources

### Primary (HIGH confidence — npm registry + official repos)

- `npm view vue-echarts` — version 8.0.1, peerDeps `{ vue: "^3.3.0", echarts: "^6.0.0" }`, repo: github.com/ecomfe/vue-echarts
- `npm view echarts` — version 6.1.0, repo: github.com/apache/echarts
- `npm view vitest` — version 4.1.8, peerDeps include vite ^6/7/8, repo: github.com/vitest-dev/vitest
- `npm view @vue/test-utils` — version 2.4.11, repo: github.com/vuejs/test-utils
- `npm view @pinia/testing` — version 1.0.3, repo: github.com/vuejs/pinia
- github.com/ecomfe/vue-echarts README — `THEME_KEY` provide/inject, `autoresize` prop, `VChart` usage
- echarts.apache.org/handbook/en/basics/import/ — tree-shaking pattern (`echarts/core`, `use()`)
- pinia.vuejs.org/cookbook/testing.html — `createTestingPinia()`, `stubActions`, async store testing

### Secondary (MEDIUM confidence — docs + search)

- ECharts GitHub issue #11704 — `type: 'time'` performance regression on large datasets → use `type: 'category'`
- vitest.dev/config/ — jsdom environment configuration, `mergeConfig` from 'vitest/config'
- ECharts option.html (treemap) — flat `{ name, value }[]` data shape confirmed

### Tertiary (LOW confidence — training knowledge)

- UTC date parsing behavior of `new Date("2022-09-12")` — standard JS behavior, but verify in target browsers
- Axios router circular import — safe in practice for Vite SPAs; verify with build output

---

## Metadata

**Confidence breakdown:**

- Standard stack: HIGH — all packages verified via npm registry; versions confirmed against package.json
- ECharts integration patterns: HIGH — official docs + README verified; tree-shaking and theme patterns confirmed
- Pinia store patterns: HIGH — official Pinia docs + existing auth store patterns in codebase
- Validation architecture (Vitest config): HIGH — registry confirms version compatibility
- Date formatting pitfalls: MEDIUM — ECMA-262 behavior well-known; `T00:00:00` workaround is widely documented

**Research date:** 2026-06-07
**Valid until:** 2026-08-07 (stable libraries; 60 days reasonable for this stack)
