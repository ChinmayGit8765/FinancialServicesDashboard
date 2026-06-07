# Phase 3: Frontend Scaffold — Pattern Map

**Mapped:** 2026-06-07
**Files analyzed:** 19 new/modified files
**Analogs found:** 17 / 19

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `src/stores/portfolio.ts` | store | CRUD | `src/stores/auth.ts` | role-match |
| `src/api/portfolio.ts` | api-client | request-response | `src/api/auth.ts` | exact |
| `src/utils/format.ts` | utility | transform | none in codebase | no analog |
| `src/plugins/echarts.ts` | plugin/config | transform | `src/main.ts` (app.use pattern) | partial |
| `src/views/DashboardView.vue` | view | CRUD | `src/views/DashboardView.vue` (existing placeholder) | exact — replace |
| `src/views/LoginView.vue` | view | request-response | `src/views/LoginView.vue` (existing) | exact — modify |
| `src/components/TopBar.vue` | component | event-driven | `src/views/DashboardView.vue` (header section) | partial |
| `src/components/KpiCard.vue` | component | request-response | `src/views/DashboardView.vue` (info-item pattern) | partial |
| `src/components/PnlChart.vue` | component | CRUD | none in codebase (first ECharts component) | no analog |
| `src/components/BenchmarkChart.vue` | component | CRUD | `src/components/PnlChart.vue` (sibling) | sibling-match |
| `src/components/AllocationChart.vue` | component | CRUD | `src/components/PnlChart.vue` (sibling) | sibling-match |
| `src/components/HoldingsTable.vue` | component | CRUD | `src/views/DashboardView.vue` (info-grid pattern) | partial |
| `src/components/TransactionsTable.vue` | component | CRUD | `src/components/HoldingsTable.vue` (sibling) | sibling-match |
| `src/components/SignedValue.vue` | component | transform | `src/views/DashboardView.vue` (.portfolio-id pattern) | partial |
| `src/components/SlotPlaceholder.vue` | component | static | `src/views/DashboardView.vue` (.placeholder-notice) | partial |
| `src/App.vue` | root | event-driven | `src/App.vue` (existing) | exact — modify |
| `src/main.ts` | entry | config | `src/main.ts` (existing) | exact — modify |
| `src/style.css` | global-styles | config | `src/style.css` (existing Vite scaffold) | exact — replace |
| `frontend/vitest.config.ts` + test files | test | batch | none in codebase | no analog |

---

## Pattern Assignments

### `src/stores/portfolio.ts` (store, CRUD)

**Analog:** `src/stores/auth.ts`

**Imports pattern** (auth.ts lines 1–3):
```typescript
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { login as apiLogin, logout as apiLogout, me as apiMe } from '../api/auth'
```
For portfolio store, replace the api imports with portfolio api functions:
```typescript
import { defineStore } from 'pinia'
import { ref, reactive } from 'vue'
import type { HoldingDto, PortfolioPnlDto, AllocationSliceDto, TransactionDto, BenchmarkComparisonDto, PageResponse } from '../api/portfolio'
import axios from 'axios'
```

**Store shape — setup-function style** (auth.ts lines 5–79):
The existing auth store uses the `defineStore('name', () => { ... return {} })` setup-function form — NOT the options API form. All new stores must match this convention.
```typescript
export const useAuthStore = defineStore('auth', () => {
  // reactive state as individual refs
  const username = ref<string | null>(null)
  const authenticated = ref(false)
  const initialized = ref(false)

  // computed derived state
  const isAuthenticated = computed(() => authenticated.value)

  // async actions — try/catch/finally with loading flag set in finally
  async function loginAs(usernameArg: string, password: string): Promise<boolean> {
    try {
      const result = await apiLogin(usernameArg, password)
      if (result.authenticated) {
        await refresh()
        return true
      }
      return false
    } catch {
      return false
    }
  }

  // explicit $reset-equivalent: clear each ref individually
  // (Pinia setup stores do not get auto-$reset — implement manually)

  return { username, authenticated, initialized, isAuthenticated, loginAs, refresh, logout }
})
```

**Per-resource async state pattern** — use a `reactive()` wrapper (from RESEARCH.md Pattern 4) instead of three individual refs per resource, mirroring the auth store's per-action loading flag pattern:
```typescript
interface AsyncState<T> {
  data: T | null
  loading: boolean
  error: string | null
}
function asyncState<T>(init: T | null = null): AsyncState<T> {
  return reactive({ data: init, loading: false, error: null })
}
// Usage:
const holdings = asyncState<HoldingDto[]>([])
const pnl      = asyncState<PortfolioPnlDto>()
```

**Error handling pattern** (auth.ts lines 27–31 and 58–62):
```typescript
// Pattern: try/catch with typed error; best-effort catch discards with no rethrow
try {
  await apiLogout()
} catch {
  // best-effort
}
// For data fetches: set .error string; do NOT rethrow; use finally for loading flag
try {
  const { data } = await axios.get(...)
  holdings.data = data
} catch (e: any) {
  holdings.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load holdings'
} finally {
  holdings.loading = false
}
```

**refreshAll with Promise.allSettled + race guard** (RESEARCH.md Pattern 4 lines 522–533):
```typescript
let refreshVersion = 0
async function refreshAll() {
  const myVersion = ++refreshVersion
  await Promise.allSettled([
    fetchHoldings(), fetchPnl(), fetchAllocation(), fetchTransactions(0), fetchBenchmark(),
  ])
  if (myVersion !== refreshVersion) return
}
```

---

### `src/api/portfolio.ts` (api-client, request-response)

**Analog:** `src/api/auth.ts`

**Imports + axios setup** (auth.ts lines 1–19):
```typescript
import axios from 'axios'

// All requests send cookies (JSESSIONID) — required for session auth
axios.defaults.withCredentials = true

// Read CSRF token from the XSRF-TOKEN cookie set by CookieCsrfTokenRepository
function getCsrfToken(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

// Attach X-XSRF-TOKEN header to every mutating request
axios.interceptors.request.use(config => {
  const token = getCsrfToken()
  if (token && ['post', 'put', 'delete', 'patch'].includes(config.method?.toLowerCase() ?? '')) {
    config.headers['X-XSRF-TOKEN'] = token
  }
  return config
})
```
IMPORTANT: `api/portfolio.ts` must NOT re-register the `withCredentials` default or the CSRF interceptor — `api/auth.ts` already sets them globally on the axios singleton when it is imported. The portfolio module simply imports axios and uses it.

**Function export pattern** (auth.ts lines 43–51):
```typescript
// Named async function exports — not default exports, not a class
export async function login(username: string, password: string): Promise<LoginResponse> {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  const response = await axios.post<LoginResponse>('/api/auth/login', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
  return response.data
}
```
Portfolio functions follow the same shape but use `axios.get`:
```typescript
export async function fetchHoldings(): Promise<HoldingDto[]> {
  const response = await axios.get<HoldingDto[]>('/api/portfolio/holdings')
  return response.data
}
```

**Nullable-return for graceful 401** (auth.ts lines 64–71):
```typescript
// GET /api/auth/me — returns null on 401 so auth store detects unauthenticated state
export async function me(): Promise<MeResponse | null> {
  try {
    const response = await axios.get<MeResponse>('/api/auth/me')
    return response.data
  } catch {
    return null
  }
}
```
Portfolio fetches do NOT use this pattern — they let errors propagate to the store which converts them to `.error` strings. Only `me()` swallows 401 silently.

**Interface declarations** (auth.ts lines 21–37):
```typescript
// Interfaces declared in the same file as the functions that use them
export interface PersonaInfo {
  username: string
  persona: string
  passwordHint: string
}
export interface MeResponse {
  username: string
  persona: string
  portfolioId: number
}
```
Portfolio DTO interfaces follow the same pattern — all in `api/portfolio.ts`, all exported.

**401 response interceptor** (RESEARCH.md Pattern 6) — add to `api/auth.ts` (modify, not portfolio.ts):
```typescript
// ADD after existing interceptors in api/auth.ts
import router from '../router'
axios.interceptors.response.use(
  response => response,
  error => {
    if (error?.response?.status === 401) {
      const url: string = error.config?.url ?? ''
      if (!url.includes('/auth/me') && !url.includes('/auth/login')) {
        router.push('/login')
      }
    }
    return Promise.reject(error)
  }
)
```

---

### `src/utils/format.ts` (utility, transform)

**No codebase analog** — this is the first utility module. Patterns come from RESEARCH.md Pattern 7 directly.

Conventions to match from the existing codebase:
- Named exports only (no default export) — matches auth.ts and api/auth.ts pattern
- Pure functions, no side effects, no imports from Vue or axios
- TypeScript — all parameters typed, return types explicit

**Tabular-nums usage hint** (DashboardView.vue line 203):
```css
/* Existing codebase already uses this pattern in .portfolio-id: */
font-variant-numeric: tabular-nums;
```
The `format.ts` functions produce strings; consumers apply the CSS class. `SignedValue.vue` applies `.val-up / .val-down / .val-flat` classes; tables apply `font-family: var(--font-mono); font-variant-numeric: tabular-nums` via scoped CSS.

---

### `src/plugins/echarts.ts` (plugin/config, transform)

**Analog:** `src/main.ts` (app registration pattern)

**app.use() registration pattern** (main.ts lines 1–14):
```typescript
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

const app = createApp(App)
app.use(createPinia())
app.use(router)
app.mount('#app')
```
The echarts plugin is a side-effect module (no export default). It is imported in `main.ts` for its `use()` and `registerTheme()` side effects before `createApp`. The import line goes at the top of `main.ts`:
```typescript
// main.ts — ADD before createApp
import './plugins/echarts'   // registers ECharts modules + quantlens-dark theme
```

**Plugin file structure** (RESEARCH.md Pattern 1):
```typescript
// src/plugins/echarts.ts — tree-shaking pattern
import * as echarts from 'echarts/core'            // NOT 'echarts' (avoids full bundle)
import { use } from 'echarts/core'
import { LineChart, PieChart, TreemapChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { quantlensDarkTheme } from './echarts-theme'

use([LineChart, PieChart, TreemapChart, GridComponent, TooltipComponent,
     LegendComponent, TitleComponent, DataZoomComponent, CanvasRenderer])
echarts.registerTheme('quantlens-dark', quantlensDarkTheme)
```

---

### `src/App.vue` (root, modify)

**Analog:** `src/App.vue` (existing — lines 1–23)

**Existing file to modify** (App.vue lines 1–23):
```vue
<script setup lang="ts">
// Root component — just the router-view; each view owns its own layout
</script>

<template>
  <RouterView />
</template>

<style>
/* Global resets */
*, *::before, *::after { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, sans-serif;
  background: #0f172a;
  color: #e2e8f0;
  line-height: 1.5;
}
</style>
```

**THEME_KEY provide — add to script setup** (RESEARCH.md Pattern 1):
```typescript
// Replace the empty script setup block with:
<script setup lang="ts">
import { provide } from 'vue'
import { THEME_KEY } from 'vue-echarts'
provide(THEME_KEY, 'quantlens-dark')
</script>
```
The `<style>` global reset block stays; the `body { background: #0f172a }` hardcoded value is superseded by the CSS custom property `--color-bg-elevated` declared in the replaced `style.css`, but the hardcoded value is a safe fallback.

---

### `src/style.css` (global-styles, replace)

**Analog:** `src/style.css` (existing — to be replaced entirely)

**Existing file is the Vite scaffold default** (style.css lines 1–297) — it uses light-themed tokens like `--bg: #fff`, `--accent: #aa3bff`. This file is a full replace, not a patch.

**What to preserve from the existing file:**
- `font-synthesis: none`, `text-rendering: optimizeLegibility`, `-webkit-font-smoothing: antialiased` (style.css lines 24–27)
- `body { margin: 0 }` (line 54)

**New token set from UI-SPEC** — full `:root` block to write:
```css
:root {
  /* Background layers */
  --color-bg-base:      #0b0f1a;
  --color-bg-elevated:  #0f172a;
  --color-bg-surface:   #1e293b;
  --color-bg-overlay:   #263248;

  /* Borders */
  --color-border:       #334155;
  --color-border-subtle:#1e293b;

  /* Text */
  --color-text-primary: #e2e8f0;
  --color-text-secondary:#94a3b8;
  --color-text-muted:   #64748b;
  --color-text-inverse: #0b0f1a;

  /* Accent */
  --color-accent:       #0ea5e9;
  --color-accent-light: #38bdf8;
  --color-accent-subtle:#1e3a5f;

  /* Semantic P&L */
  --color-up:           #22c55e;
  --color-up-subtle:    rgba(34, 197, 94, 0.12);
  --color-down:         #ef4444;
  --color-down-subtle:  rgba(239, 68, 68, 0.12);
  --color-warn:         #f59e0b;
  --color-warn-subtle:  rgba(245, 158, 11, 0.12);
  --color-destructive:  #f87171;

  /* Border radius */
  --radius-sm:   4px;
  --radius-md:   8px;
  --radius-lg:   12px;
  --radius-xl:   16px;
  --radius-pill: 9999px;

  /* Elevation */
  --shadow-card:  0 4px 16px rgba(0, 0, 0, 0.40);
  --shadow-float: 0 8px 32px rgba(0, 0, 0, 0.55);
  --shadow-modal: 0 20px 60px rgba(0, 0, 0, 0.70);

  /* Spacing */
  --space-xs:  4px;
  --space-sm:  8px;
  --space-md:  16px;
  --space-lg:  24px;
  --space-xl:  32px;
  --space-2xl: 48px;
  --space-3xl: 64px;

  /* Typography */
  --font-sans: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Oxygen, sans-serif;
  --font-mono: ui-monospace, 'Cascadia Code', Consolas, monospace;

  /* Phase 5 fan chart tokens (reserved) */
  --color-fan-p50:    #0ea5e9;
  --color-fan-band-1: rgba(14, 165, 233, 0.25);
  --color-fan-band-2: rgba(14, 165, 233, 0.12);
}
```

**Note on existing hardcoded colors in LoginView and DashboardView:** Both views currently use hardcoded hex values (e.g., `#0f172a`, `#1e293b`, `#334155`, `#38bdf8`, `#f87171`). After `style.css` is replaced with tokens, the new DashboardView and all new components use `var(--token)` exclusively. The LoginView is modified in place to swap hardcoded values → CSS vars.

---

### `src/views/DashboardView.vue` (view, CRUD — full replace)

**Analog:** `src/views/DashboardView.vue` (existing Phase 1 placeholder)

**Script setup convention** (DashboardView.vue lines 1–12):
```typescript
<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()

async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
</script>
```
The new DashboardView keeps the same store import pattern, extends it with `usePortfolioStore`, and calls `portfolioStore.refreshAll()` on `onMounted`.

**Header structure to evolve** (DashboardView.vue lines 16–26):
```html
<header class="dashboard-header">
  <div class="brand">
    <h1>QuantLens</h1>
    <span class="phase-badge">Phase 1 — Walking Skeleton</span>   <!-- REMOVE -->
  </div>
  <div class="user-info">
    <span class="persona-tag">{{ authStore.persona }}</span>
    <span class="username">@{{ authStore.username }}</span>
    <button class="logout-btn" @click="handleLogout">Log out</button>
  </div>
</header>
```
The phase-badge is removed. The persona-tag + username area is replaced by `<TopBar>` component.

**Logout button scoped CSS pattern** (DashboardView.vue lines 127–137):
```css
.logout-btn {
  padding: 0.4rem 1rem;
  background: transparent;
  border: 1px solid #334155;       /* → var(--color-border) */
  border-radius: 6px;              /* → var(--radius-md) */
  color: #94a3b8;                  /* → var(--color-text-secondary) */
  cursor: pointer;
  font-size: 0.85rem;
  transition: border-color 0.15s, color 0.15s;
}
.logout-btn:hover {
  border-color: #f87171;           /* → var(--color-destructive) */
  color: #f87171;                  /* → var(--color-destructive) */
}
```
New components replace all hardcoded hex values with CSS custom properties.

**Grid layout pattern** — the new DashboardView uses CSS Grid (from UI-SPEC). The existing placeholder uses `flex-direction: column` — this is replaced:
```css
/* NEW — 12-column CSS grid for dashboard content */
.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(12, 1fr);
  gap: var(--space-xl);
  padding: var(--space-lg);
  padding-top: calc(var(--space-2xl) + var(--space-md)); /* top bar height + breathing room */
}
```

---

### `src/views/LoginView.vue` (view, request-response — modify in place)

**Analog:** `src/views/LoginView.vue` (existing — lines 1–298)

**Script setup block** (LoginView.vue lines 1–57) — keep as-is. The `onMounted` persona-fetch, `handleLogin`, and `loginAsPersona` functions are unchanged. Only add optional `description` handling:
```typescript
// Add to PersonaInfo type usage (description is optional — degrade gracefully):
// If backend exposes p.description, render it; if not, omit (v-if="p.description")
```

**Persona button scoped CSS — values to token-ize** (LoginView.vue lines 183–221):
```css
/* Current hardcoded values → new CSS var equivalents: */
background: #0f172a           →  var(--color-bg-elevated)
border: 1px solid #334155     →  1px solid var(--color-border)
border-radius: 8px            →  var(--radius-md)
color: #e2e8f0                →  var(--color-text-primary)
.persona-btn:hover background: #1e3a5f  →  var(--color-accent-subtle)
.persona-btn:hover border-color: #38bdf8 →  var(--color-accent-light)
```

**Login card background** (LoginView.vue lines 129–137):
```css
.login-card {
  background: #1e293b;          /* → var(--color-bg-surface) */
  border-radius: 12px;          /* → var(--radius-lg) */
  padding: 2.5rem 2rem;
  max-width: 420px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5);  /* → var(--shadow-modal) */
}
```

**Login page background** (LoginView.vue line 125):
```css
.login-page {
  background: #0f172a;          /* → var(--color-bg-base) per UI-SPEC (#0b0f1a, slightly deeper) */
}
```

**Persona button minimum height** — UI-SPEC requires 44px min height. Add:
```css
.persona-btn {
  min-height: 44px;             /* ADD — WCAG 2.5.5 touch target */
}
```

---

### `src/components/TopBar.vue` (component, event-driven)

**Analog:** `src/views/DashboardView.vue` header section (lines 16–26, 65–137)

**Header/brand scoped CSS** (DashboardView.vue lines 74–106):
```css
.dashboard-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1rem 2rem;
  background: #1e293b;          /* → var(--color-bg-elevated) */
  border-bottom: 1px solid #334155;  /* → var(--color-border) */
}
.brand h1 {
  font-size: 1.5rem;            /* → 20px per UI-SPEC, weight 700 */
  font-weight: 700;
  color: #38bdf8;               /* → var(--color-accent) */
}
```

**TopBar structure** — three slots: brand left, persona switcher center, user+logout right. The persona pill pattern comes from LoginView:
```css
/* Active persona pill (from LoginView persona-btn hover state): */
border: 1px solid var(--color-accent);
background: var(--color-accent-subtle);
color: var(--color-text-primary);
border-radius: var(--radius-pill);
padding: 6px 16px;
min-height: 44px;

/* Inactive persona pill: */
border: 1px solid var(--color-border);
color: var(--color-text-secondary);
```

**Persona switch logic** (RESEARCH.md Pattern 5):
```typescript
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
const switching = ref(false)
const switchingTo = ref<string | null>(null)

async function switchPersona(username: string, password: string) {
  if (switching.value) return   // debounce
  switching.value = true
  switchingTo.value = username
  try {
    const ok = await authStore.loginAs(username, password)
    if (ok) await portfolioStore.refreshAll()
  } finally {
    switching.value = false
    switchingTo.value = null
  }
}
```

**Logout pattern** (DashboardView.vue lines 8–11):
```typescript
async function handleLogout() {
  await authStore.logout()
  await router.push('/login')
}
```

**Fixed positioning** — UI-SPEC: `position: fixed; top: 0; width: 100%; z-index: 100; height: 48px`.

---

### `src/components/KpiCard.vue` (component, request-response)

**Analog:** `src/views/DashboardView.vue` `.info-item` pattern (lines 178–205)

**Info-item pattern** (DashboardView.vue lines 178–200):
```css
.info-item {
  background: #0f172a;          /* → var(--color-bg-surface) */
  border: 1px solid #334155;    /* → var(--color-border) */
  border-radius: 8px;           /* → var(--radius-md) */
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}
.info-label {
  font-size: 0.7rem;            /* → 11px, uppercase per UI-SPEC */
  text-transform: uppercase;
  letter-spacing: 0.05em;       /* → 0.06em per UI-SPEC */
  color: #64748b;               /* → var(--color-text-muted) */
}
.info-value {
  font-size: 1.1rem;            /* → 20px/600/mono for Numeric-large per UI-SPEC */
  font-weight: 600;
  color: #e2e8f0;               /* → var(--color-text-primary) */
}
```

**Props pattern** (UI-SPEC):
```typescript
const props = defineProps<{
  label: string
  primary: string
  secondary?: string
  delta?: number
  loading: boolean
}>()
```

**Loading shimmer** — no existing analog in codebase. Use CSS animation:
```css
/* Shimmer skeleton — new pattern, no existing analog */
@keyframes shimmer {
  0%   { background-position: -200% center; }
  100% { background-position:  200% center; }
}
.skeleton {
  background: linear-gradient(
    90deg,
    var(--color-bg-surface) 25%,
    var(--color-bg-overlay) 50%,
    var(--color-bg-surface) 75%
  );
  background-size: 200% auto;
  animation: shimmer 1.4s linear infinite;
  border-radius: var(--radius-md);
}
```

**Signed delta left-border pattern** (UI-SPEC):
```css
/* Positive delta card: */
border-left: 3px solid var(--color-up);
/* Negative delta card: */
border-left: 3px solid var(--color-down);
/* No delta / neutral: */
border-left: 3px solid var(--color-border);
```

---

### `src/components/PnlChart.vue` (component, CRUD)

**No close codebase analog** — first ECharts component. Patterns from RESEARCH.md Pattern 2 + UI-SPEC.

**Component scaffold** (mirrors LoginView `<script setup>` convention):
```vue
<script setup lang="ts">
import { computed } from 'vue'
import VChart from 'vue-echarts'
import type { EChartsOption } from 'echarts/types/dist/shared'
import type { PortfolioPnlDto } from '@/api/portfolio'

const props = defineProps<{
  pnl: PortfolioPnlDto | null
  loading: boolean
  error: string | null
}>()

const option = computed<EChartsOption>(() => {
  if (!props.pnl?.equityCurve?.length) return {}
  // ... (RESEARCH.md Pattern 2 option object verbatim)
})
</script>

<template>
  <figure class="chart-panel" :aria-busy="loading" aria-label="Loading P&L chart">
    <div v-if="loading" class="skeleton" style="height: 320px" />
    <div v-else-if="error" class="chart-error">
      Failed to load P&L data. <button @click="$emit('retry')">Retry</button>
    </div>
    <div v-else-if="!props.pnl?.equityCurve?.length" class="chart-empty">
      No P&L data available.
    </div>
    <v-chart v-else class="chart" :option="option" :autoresize="true" />
    <figcaption class="sr-only">Portfolio value over time chart</figcaption>
  </figure>
</template>
```

**Key ECharts option conventions** (RESEARCH.md Pattern 2):
- `xAxis.type: 'category'` with ISO date string array (NOT `type: 'time'`)
- Append `'T00:00:00'` when constructing `new Date(isoStr)` to avoid UTC off-by-one
- `series[0].symbol: 'none'` — no dots by default
- Area fill: linear gradient `rgba(14,165,233,0.25)` → `rgba(14,165,233,0)` (top-to-bottom)
- No `theme` prop on `<v-chart>` — THEME_KEY injection from `App.vue` handles it

**Chart panel scoped CSS:**
```css
.chart-panel {
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  border: 1px solid var(--color-border);
  padding: var(--space-md);
  min-height: 320px;
}
.chart { height: 320px; width: 100%; }
.chart-error { color: var(--color-down); border: 1px solid var(--color-down); border-radius: var(--radius-md); padding: var(--space-md); }
.chart-empty { color: var(--color-text-muted); text-align: center; padding: var(--space-xl); }
.sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0,0,0,0); }
```

---

### `src/components/BenchmarkChart.vue` (component, CRUD)

**Analog:** `src/components/PnlChart.vue` (sibling — same outer structure)

Same `<figure>` / loading / error / empty / `<v-chart>` scaffold as PnlChart. Key differences in the option object:

**Dual-series option** (RESEARCH.md Pattern 2, UI-SPEC BenchmarkChart section):
```typescript
// Two series sharing one xAxis.type: 'category'
series: [
  {
    name: 'Portfolio',
    type: 'line',
    data: props.benchmark.portfolioSeries,
    smooth: true,
    symbol: 'none',
    lineStyle: { color: '#0ea5e9', width: 2 },   // var(--color-accent)
  },
  {
    name: 'S&P 500',
    type: 'line',
    data: props.benchmark.benchmarkSeries,
    smooth: true,
    symbol: 'none',
    lineStyle: { color: '#94a3b8', width: 1.5, type: 'dashed' }, // var(--color-text-secondary)
  }
]
```

---

### `src/components/AllocationChart.vue` (component, CRUD)

**Analog:** `src/components/PnlChart.vue` (sibling — same outer structure)

Same scaffold. Key additions:

**Donut/treemap toggle ref** (RESEARCH.md Pattern 3):
```typescript
const chartType = ref<'donut' | 'treemap'>('donut')
const option = computed(() => chartType.value === 'donut' ? donutOption.value : treemapOption.value)
```

**Toggle control template** (DashboardView.vue `.info-grid` segmented pattern as starting point):
```html
<!-- Segmented toggle top-right of card -->
<div class="chart-toggle" role="group" aria-label="Chart type">
  <button :class="{ active: chartType === 'donut' }" @click="chartType = 'donut'">Donut</button>
  <button :class="{ active: chartType === 'treemap' }" @click="chartType = 'treemap'">Treemap</button>
</div>
```
Active segment CSS: `background: var(--color-accent-subtle); border-color: var(--color-accent)`.

Chart height: 280px (not 320px — per UI-SPEC).

---

### `src/components/HoldingsTable.vue` (component, CRUD)

**Analog:** `src/views/DashboardView.vue` `.info-grid` and `.info-item` patterns (lines 171–205)

**Table scoped CSS starting point** (evolve from DashboardView.vue):
```css
/* Evolve from .info-item pattern: */
.holdings-table {
  width: 100%;
  border-collapse: collapse;
  background: var(--color-bg-surface);
  border-radius: var(--radius-lg);
}
/* Striped rows (UI-SPEC): */
tbody tr:nth-child(odd)  { background: var(--color-bg-surface); }
tbody tr:nth-child(even) { background: var(--color-bg-elevated); }
tbody tr:hover           { background: var(--color-bg-overlay); }

/* Column header style (evolve from .info-label): */
th {
  font-size: 11px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--color-text-muted);
  padding: var(--space-xs) var(--space-md);
  text-align: left;
  position: sticky;
  top: 0;
  background: var(--color-bg-surface);
  border-bottom: 1px solid var(--color-border);
}
/* Numeric cells (evolve from .portfolio-id pattern in DashboardView.vue line 202): */
td.numeric {
  font-family: var(--font-mono);
  font-variant-numeric: tabular-nums;
  text-align: right;
}
/* Ticker column (evolve from .portfolio-id color): */
td.ticker {
  font-family: var(--font-mono);
  font-weight: 600;
  color: var(--color-accent);       /* same as .portfolio-id color: #38bdf8 */
}
```

**Sortable columns** — `aria-sort` on `<th>`:
```html
<th scope="col" :aria-sort="sortKey === 'ticker' ? sortDir : 'none'" @click="toggleSort('ticker')">
  Ticker <span v-if="sortKey === 'ticker'">{{ sortDir === 'ascending' ? '▲' : '▼' }}</span>
</th>
```

**Empty + error state** (evolve from DashboardView.vue `.placeholder-notice` pattern):
```html
<tr v-if="!holdings.length" class="empty-row">
  <td :colspan="10" class="empty-cell">No holdings in this portfolio.</td>
</tr>
```

**Skeleton loading rows** (same `shimmer` animation as KpiCard):
```html
<template v-if="loading">
  <tr v-for="i in 5" :key="i" class="skeleton-row" aria-hidden="true">
    <td v-for="j in 10" :key="j"><span class="skeleton-pill" /></td>
  </tr>
</template>
```

---

### `src/components/TransactionsTable.vue` (component, CRUD)

**Analog:** `src/components/HoldingsTable.vue` (sibling)

Identical outer scaffold. Key differences:

**No striping** — all rows `var(--color-bg-surface)` (per UI-SPEC).

**BUY/SELL badge pattern** (evolve from LoginView `.phase-badge`):
```css
/* LoginView.vue lines 98–106 — same pill + border pattern: */
.phase-badge {
  padding: 0.2em 0.6em;
  background: #1e3a5f;
  border: 1px solid #0ea5e9;
  border-radius: 9999px;        /* → var(--radius-pill) */
}
/* Adapt for BUY/SELL: */
.badge-buy  { color: var(--color-up);   background: var(--color-up-subtle);   border-radius: var(--radius-pill); padding: 2px 8px; }
.badge-sell { color: var(--color-down); background: var(--color-down-subtle); border-radius: var(--radius-pill); padding: 2px 8px; }
```

**Pagination controls:**
```html
<div class="pagination" role="navigation" aria-label="Transactions pagination">
  <button :disabled="currentPage === 0" @click="$emit('page', currentPage - 1)">Prev</button>
  <span>Page {{ currentPage + 1 }} of {{ totalPages }}</span>
  <button :disabled="currentPage >= totalPages - 1" @click="$emit('page', currentPage + 1)">Next</button>
</div>
```

---

### `src/components/SignedValue.vue` (component, transform)

**Analog:** `src/views/DashboardView.vue` `.portfolio-id` pattern (lines 200–204)

**Existing pattern to evolve** (DashboardView.vue lines 200–204):
```css
.portfolio-id {
  color: #38bdf8;                   /* → conditional: var(--color-up/down/warn) */
  font-variant-numeric: tabular-nums;
}
```

**Component shape:**
```vue
<script setup lang="ts">
import { computed } from 'vue'
import { deltaClass } from '@/utils/format'

const props = defineProps<{ value: number; formatted: string }>()
const cls = computed(() => deltaClass(props.value))
</script>

<template>
  <span :class="['signed-value', cls]">{{ formatted }}</span>
</template>

<style scoped>
.val-up   { color: var(--color-up); }
.val-down { color: var(--color-down); }
.val-flat { color: var(--color-warn); }
.signed-value { font-family: var(--font-mono); font-variant-numeric: tabular-nums; }
</style>
```

---

### `src/components/SlotPlaceholder.vue` (component, static)

**Analog:** `src/views/DashboardView.vue` `.placeholder-notice` section (lines 47–63, 205–221)

**Existing placeholder pattern** (DashboardView.vue lines 206–221):
```css
.placeholder-notice {
  border-top: 1px solid #334155;    /* → dashed border per UI-SPEC */
  padding-top: 1.25rem;
  font-size: 0.85rem;
  color: #64748b;                   /* → var(--color-text-muted) */
}
```

**Component shape** (UI-SPEC):
```vue
<script setup lang="ts">
const props = defineProps<{ label: string; minHeight?: string }>()
const height = props.minHeight ?? '240px'
</script>

<template>
  <div class="slot-placeholder" :style="{ minHeight: height }">
    <span class="slot-label">{{ label }}</span>
  </div>
</template>

<style scoped>
.slot-placeholder {
  background: var(--color-bg-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
  display: flex;
  align-items: center;
  justify-content: center;
}
.slot-label {
  font-size: 13px;
  color: var(--color-text-muted);
}
</style>
```

---

### `frontend/vitest.config.ts` + test files (test, batch)

**No codebase analog** — no existing test infrastructure. Use RESEARCH.md Pattern 8 verbatim:

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
    coverage: { provider: 'v8', reporter: ['text', 'lcov'], include: ['src/utils/**', 'src/stores/**'] },
  },
}))
```

```typescript
// frontend/src/__tests__/setup.ts
// ResizeObserver mock — required because vue-echarts uses it; jsdom does not implement it
global.ResizeObserver = class ResizeObserver {
  observe() {}
  unobserve() {}
  disconnect() {}
}
```

**Store test pattern** — use `setActivePinia(createPinia())` + `vi.mock('axios')` directly (NOT `createTestingPinia`) per RESEARCH.md Pitfall 8:
```typescript
import { setActivePinia, createPinia } from 'pinia'
import { vi, beforeEach, afterEach } from 'vitest'
import axios from 'axios'
vi.mock('axios')
beforeEach(() => { setActivePinia(createPinia()) })
afterEach(() => { vi.restoreAllMocks() })
```

**Component test pattern** — use `shallowMount` for chart components (stubs `<v-chart>`); `mount` for table/card components:
```typescript
import { shallowMount } from '@vue/test-utils'
import { createTestingPinia } from '@pinia/testing'
// shallowMount stubs <v-chart> automatically — avoids ResizeObserver issues
const wrapper = shallowMount(PnlChart, {
  props: { pnl: null, loading: true, error: null },
  global: { plugins: [createTestingPinia()] }
})
```

---

## Shared Patterns

### Session Cookie + CSRF Interceptor
**Source:** `src/api/auth.ts` lines 1–19
**Apply to:** All new components that make API calls indirectly via the portfolio store — they do NOT need to configure axios themselves. The interceptor is registered globally when `api/auth.ts` is first imported. `api/portfolio.ts` simply imports axios and calls `.get()`.
```typescript
// The CSRF interceptor is already global — no action needed in portfolio.ts
// All POST/PUT/DELETE requests automatically get X-XSRF-TOKEN header
// All requests automatically send cookies (withCredentials: true)
```

### Error Handling
**Source:** `src/stores/auth.ts` lines 27–31 and 56–62
**Apply to:** All store actions in `portfolio.ts`
```typescript
// Pattern: catch → set .error string, never rethrow from store actions
// Exception: the 401 response interceptor (added to api/auth.ts) handles redirect
try {
  // ...fetch...
} catch (e: any) {
  resource.error = e?.response?.status === 401 ? 'Session expired' : 'Failed to load [name]'
} finally {
  resource.loading = false
}
```

### `<script setup lang="ts">` Convention
**Source:** `src/views/LoginView.vue` line 1, `src/views/DashboardView.vue` line 1
**Apply to:** ALL new `.vue` components — every SFC uses `<script setup lang="ts">`.
```vue
<script setup lang="ts">
// No Options API, no defineComponent wrapper — setup script only
</script>
```

### Scoped CSS + CSS Custom Properties
**Source:** `src/views/LoginView.vue` `<style scoped>` block (lines 119–298), `src/views/DashboardView.vue` `<style scoped>` (lines 65–226)
**Apply to:** All new `.vue` components — use `<style scoped>` for component styles.
**Rule:** After `style.css` is replaced, NEVER use hardcoded hex values in component `<style scoped>` blocks. All color/spacing/radius/shadow values must use `var(--token)`.
```css
/* Wrong: */
background: #1e293b;
border-radius: 12px;
/* Correct: */
background: var(--color-bg-surface);
border-radius: var(--radius-lg);
```

### Auth Guard Pattern
**Source:** `src/router/index.ts` lines 24–39
**Apply to:** DashboardView (already protected by `meta: { requiresAuth: true }` — no changes needed). New components do not interact with the router directly.
```typescript
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth) return true
  const authStore = useAuthStore()
  if (!authStore.initialized) await authStore.refresh()
  if (!authStore.isAuthenticated) return { name: 'login' }
  return true
})
```

### Store Access in Components
**Source:** `src/views/DashboardView.vue` lines 3–11
**Apply to:** All new components that need auth or portfolio state.
```typescript
// Import store hook — call inside setup, not at module level
const authStore = useAuthStore()
const portfolioStore = usePortfolioStore()
// Access reactive state directly on the store object — do NOT destructure
// Wrong: const { holdings } = portfolioStore   (loses reactivity)
// Correct: portfolioStore.holdings.data, portfolioStore.holdings.loading
```

### Numeric Display Convention
**Source:** `src/views/DashboardView.vue` `.portfolio-id` (lines 200–204)
**Apply to:** All monetary/percent values in tables and KPI cards.
```css
/* Already established in DashboardView.vue: */
font-variant-numeric: tabular-nums;
/* Phase 3 extends with: */
font-family: var(--font-mono);   /* ui-monospace, 'Cascadia Code', Consolas, monospace */
```

### Focus Ring
**Source:** `src/views/LoginView.vue` `.field input:focus` (lines 265–267)
**Apply to:** All interactive elements in new components.
```css
/* Existing pattern in LoginView: */
.field input:focus { border-color: #38bdf8; }
/* Phase 3 standard (UI-SPEC accessibility): */
:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 3px; }
/* Never: outline: none without a replacement */
```

---

## No Analog Found

| File | Role | Data Flow | Reason |
|------|------|-----------|--------|
| `src/utils/format.ts` | utility | transform | No utility modules exist in the codebase yet; first pure-function utility |
| `frontend/vitest.config.ts` + test suite | test | batch | No test infrastructure exists at all in Phase 1/2 frontend |
| `src/components/PnlChart.vue` | component | CRUD | No ECharts components exist; `BenchmarkChart` and `AllocationChart` are siblings built after this one |

For these files, the executor should use RESEARCH.md patterns verbatim (Patterns 1–8 and the Vitest configuration section).

---

## Metadata

**Analog search scope:** `frontend/src/` — stores/, api/, views/, router/, App.vue, main.ts, style.css
**Files scanned:** 8 source files (all existing frontend source files)
**Key finding:** The existing codebase is a clean Phase 1 skeleton with consistent patterns. Every new file has at least one directly applicable pattern to copy from. The biggest gap is ECharts components (no existing analog) and test infrastructure (no existing tests). Both are fully covered by RESEARCH.md Patterns 1–8.
**Token alignment:** All 18 UI-SPEC CSS custom properties plus spacing, radius, and shadow tokens are mapped to their exact hex-value counterparts already in use in LoginView.vue and DashboardView.vue, ensuring visual continuity.
**Pattern extraction date:** 2026-06-07
