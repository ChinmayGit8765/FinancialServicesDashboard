---
phase: 03-frontend-scaffold
plan: "01"
subsystem: frontend
tags: [vitest, vite, echarts, css-tokens, format-utils, path-alias]
dependency_graph:
  requires: []
  provides:
    - "@/ path alias (vite.config.ts + tsconfig.app.json) — all later plans import via @/"
    - "Vitest jsdom infrastructure (vitest.config.ts + setup.ts + test scripts)"
    - "Dark-theme CSS token set on :root (style.css)"
    - "format.ts seven helpers (formatCurrency, formatCurrencyCompact, formatPercent, formatSignedCurrency, formatSignedPercent, deltaClass, formatDate)"
    - "ECharts tree-shaken plugin + quantlens-dark theme (echarts.ts + echarts-theme.ts)"
    - "THEME_KEY provided app-wide via App.vue"
  affects:
    - "All Phase 3 plans — @/ imports now resolve"
    - "Phase 4-6 components — CSS tokens available"
    - "Phase 3 Plan 02+ — Vitest infra ready for store/component tests"
tech_stack:
  added:
    - "vitest@4.1.8 (dev)"
    - "@vue/test-utils@2.4.11 (dev)"
    - "jsdom@29.1.1 (dev)"
    - "@pinia/testing@1.0.3 (dev)"
  patterns:
    - "Vite resolve.alias @ → ./src via fileURLToPath/URL"
    - "TypeScript compilerOptions.paths with ignoreDeprecations:6.0 for TS6 compat"
    - "mergeConfig(viteConfig) in vitest.config.ts inherits the @ alias"
    - "globalThis cast in setup.ts for DOM tsconfig compat"
    - "MONTH_ABBR array for locale-stable 3-char month abbreviations"
    - "echarts/core import (tree-shaken) + use() + registerTheme once at startup"
key_files:
  created:
    - frontend/vitest.config.ts
    - frontend/src/__tests__/setup.ts
    - frontend/src/__tests__/format.test.ts
    - frontend/src/utils/format.ts
    - frontend/src/plugins/echarts.ts
    - frontend/src/plugins/echarts-theme.ts
  modified:
    - frontend/vite.config.ts
    - frontend/tsconfig.app.json
    - frontend/package.json
    - frontend/src/style.css
    - frontend/src/App.vue
    - frontend/src/main.ts
decisions:
  - "[03-01] ignoreDeprecations:6.0 in tsconfig — baseUrl is deprecated in TS6 but required for paths; silenced rather than removed"
  - "[03-01] MONTH_ABBR array in formatDate — en-GB locale on Windows outputs 'Sept' not 'Sep'; hardcoded array produces locale-stable 3-char abbreviations matching UI-SPEC"
  - "[03-01] globalThis cast in setup.ts — 'global' identifier not available in DOM lib tsconfig; globalThis is the portable cross-environment reference"
metrics:
  duration: "~5 minutes"
  completed: "2026-06-07"
  tasks: 3
  files: 12
---

# Phase 03 Plan 01: Frontend Foundation Summary

**One-liner:** Vitest jsdom infra, `@`→`./src` alias, dark-theme CSS tokens, 7-function format.ts, and tree-shaken ECharts plugin with quantlens-dark theme registered app-wide.

## What Was Built

| Artifact | Purpose |
|----------|---------|
| `frontend/vite.config.ts` | Added `resolve.alias { '@': fileURLToPath('./src') }` — all downstream `@/...` imports resolve |
| `frontend/tsconfig.app.json` | Added `baseUrl: "."` + `paths: { "@/*": ["./src/*"] }` + `ignoreDeprecations: "6.0"` so vue-tsc resolves `@/` during build |
| `frontend/vitest.config.ts` | `mergeConfig(viteConfig, { test: { environment: 'jsdom', globals: true, ... } })` — inherits the `@` alias |
| `frontend/src/__tests__/setup.ts` | `globalThis.ResizeObserver` no-op mock — prevents vue-echarts from crashing in jsdom |
| `frontend/src/__tests__/format.test.ts` | 13 unit tests covering all 7 exported helpers — TDD RED→GREEN |
| `frontend/src/utils/format.ts` | 7 pure Intl.NumberFormat helpers: formatCurrency, formatCurrencyCompact, formatPercent, formatSignedCurrency, formatSignedPercent, deltaClass, formatDate |
| `frontend/src/style.css` | Full :root token set replacing Vite scaffold: 18 color vars, 5 radius, 3 shadow, 7 spacing, 2 font, 3 fan-chart phase-5 reserved tokens |
| `frontend/src/plugins/echarts-theme.ts` | `quantlensDarkTheme` object verbatim from UI-SPEC |
| `frontend/src/plugins/echarts.ts` | `use([LineChart, PieChart, TreemapChart, GridComponent, TooltipComponent, LegendComponent, TitleComponent, DataZoomComponent, CanvasRenderer])` + `registerTheme('quantlens-dark', ...)` from `echarts/core` |
| `frontend/src/App.vue` | Added `provide(THEME_KEY, 'quantlens-dark')` — all `<v-chart>` instances inherit theme automatically |
| `frontend/src/main.ts` | `import './plugins/echarts'` as first line — registers ECharts before app creation |

## Task Commits

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | @ alias + Vitest infra | `1920b1d` | vite.config.ts, tsconfig.app.json, package.json, vitest.config.ts, setup.ts |
| 2 (RED) | format.test.ts failing | `72ca408` | src/__tests__/format.test.ts |
| 2 (GREEN) | format.ts implementation | `b9db199` | src/utils/format.ts |
| 3 | Dark tokens + ECharts + build | `9742d86` | style.css, echarts-theme.ts, echarts.ts, App.vue, main.ts |

## Verification

- `npm run build` exits 0 — vue-tsc type-check + vite bundle, `@/` alias resolves end-to-end
- `npm run test` — 13/13 tests passing (format.test.ts)
- ECharts bundle: `echarts/core` only — no `import * as echarts from 'echarts'` (verified by grep)
- `--color-bg-base: #0b0f1a` and `--color-up: #22c55e` confirmed in style.css
- `registerTheme('quantlens-dark'` confirmed in echarts.ts
- `provide(THEME_KEY, 'quantlens-dark')` confirmed in App.vue
- `import './plugins/echarts'` is first line of main.ts

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] formatDate locale returns "Sept" on Windows en-GB**
- **Found during:** Task 2 GREEN phase (test failure)
- **Issue:** `toLocaleDateString('en-GB', { month: 'short' })` returns "Sept" on Windows locale, but UI-SPEC expects "Sep" (3 chars)
- **Fix:** Replaced with a `MONTH_ABBR` const array producing locale-stable 3-char abbreviations
- **Files modified:** `frontend/src/utils/format.ts`
- **Commit:** `b9db199`

**2. [Rule 3 - Blocking] TS6 deprecates `baseUrl` — build error**
- **Found during:** Task 3 (npm run build)
- **Issue:** `tsconfig.app.json` with `baseUrl` caused `error TS5101: Option 'baseUrl' is deprecated` in TS 6.x
- **Fix:** Added `"ignoreDeprecations": "6.0"` to compilerOptions
- **Files modified:** `frontend/tsconfig.app.json`
- **Commit:** `9742d86`

**3. [Rule 3 - Blocking] `global` not defined in DOM tsconfig context**
- **Found during:** Task 3 (npm run build)
- **Issue:** `global.ResizeObserver = ...` in setup.ts caused `Cannot find name 'global'` (DOM lib doesn't have Node.js `global`)
- **Fix:** Changed to `(globalThis as any).ResizeObserver = ...`
- **Files modified:** `frontend/src/__tests__/setup.ts`
- **Commit:** `9742d86`

## Known Stubs

None — all artifacts in this plan are complete implementations with no placeholder data.

## Threat Surface Scan

No new network endpoints, auth paths, or trust boundary changes. ECharts imported tree-shaken from `echarts/core` per T-03-02 mitigation. Dev packages are dev-only and never shipped to the client bundle.

## Self-Check: PASSED
