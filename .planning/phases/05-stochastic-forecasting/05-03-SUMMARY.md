---
phase: 05-stochastic-forecasting
plan: 03
subsystem: frontend
tags: [vue3, echarts, monte-carlo, fan-chart, pinia, typescript, models-doc, SIM-01, SIM-02, SIM-03]

# Dependency graph
requires:
  - phase: 05-01
    provides: ForecastDto Java record (p5/p25/p50/p75/p95 + model + horizonDays) + MonteCarloFanChart.test.ts RED scaffold
  - phase: 05-02
    provides: GET /api/portfolio/forecast returning real simulation data for all 4 models

provides:
  - frontend/src/api/forecast.ts (ForecastDto + ModelType TypeScript types)
  - frontend/src/plugins/chart-colors.ts FAN_COLORS export (resolved CSS tokens for canvas use)
  - frontend/src/stores/portfolio.ts forecast asyncState + fetchForecast action
  - frontend/src/components/MonteCarloFanChart.vue (band-difference fan + model selector + states)
  - frontend/src/views/DashboardView.vue (Phase-3 SlotPlaceholder replaced with fan chart)
  - docs/MODELS.md (SIM-03 non-specialist rationale for all 4 models)
  - README.md markdown hyperlink to docs/MODELS.md

affects: [06-spring-ai-narration]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ECharts band-difference stacking: p5 absolute base (transparent), then diffs p25-p5/p75-p25/p95-p75 on stack:'fan'; median p50 separate non-stacked line"
    - "FAN_COLORS resolved via getComputedStyle at module init in chart-colors.ts — ECharts canvas cannot read CSS vars at paint time (Pitfall 4 mitigated)"
    - "asyncState<ForecastDto> + fetchForecast follows Phase-4 fetchPairs pattern exactly (race-guard version, 401 handling, refreshAll + $reset)"
    - "VChart stub in test setup: defineComponent({ name: 'VChart' }) registered via config.global.stubs so findComponent({ name: 'VChart' }) works in jsdom"

key-files:
  created:
    - frontend/src/api/forecast.ts
    - frontend/src/components/MonteCarloFanChart.vue
    - docs/MODELS.md
  modified:
    - frontend/src/plugins/chart-colors.ts
    - frontend/src/stores/portfolio.ts
    - frontend/src/views/DashboardView.vue
    - frontend/src/__tests__/setup.ts
    - frontend/src/__tests__/portfolioStore.test.ts
    - README.md

key-decisions:
  - "FAN_COLORS resolved once at module init via getComputedStyle — avoids var(--...) strings that ECharts canvas cannot resolve at paint time (Pitfall 4)"
  - "Band-difference stacking: series carry p25-p5, p75-p25, p95-p75 diffs (not absolute percentiles) so ECharts stack accumulation produces correct band heights"
  - "VChart stub in test setup via config.global.stubs with name:'VChart' — vue-echarts exports as 'Echarts'; stub intercepts at global level so locked RED test scaffold's findComponent({ name: 'VChart' }) passes"
  - "portfolioStore.test.ts refreshAll count updated 9→10 for Phase-5 forecast addition; race-guard batch offsets updated (1-9→1-10, 10-18→11-20)"
  - "Task 4 (checkpoint:human-verify) handled autonomously per unattended-run instructions: build exits 0 + 56 tests green; visual checklist deferred to manual review"

requirements-completed: [SIM-01, SIM-02, SIM-03]

# Metrics
duration: 15min
completed: 2026-06-09
---

# Phase 5 Plan 03: Frontend Fan Chart + MODELS.md Summary

**Fan chart (band-difference ECharts, FAN_COLORS canvas-safe), 4-model selector, store forecast state, DashboardView wiring — MonteCarloFanChart.test.ts GREEN (8/8); 56 total tests pass; build exits 0; docs/MODELS.md (SIM-03) written + linked from README**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-06-08T14:25:12Z
- **Completed:** 2026-06-08T14:36:14Z
- **Tasks:** 3 auto + 1 checkpoint (autonomous approval)
- **Files created:** 3 (forecast.ts, MonteCarloFanChart.vue, docs/MODELS.md)
- **Files modified:** 5 (chart-colors.ts, portfolio.ts, DashboardView.vue, setup.ts, portfolioStore.test.ts) + README.md

## Accomplishments

### Task 1: Forecast types + FAN_COLORS + store extension

- Created `frontend/src/api/forecast.ts`: DTO-only module declaring `ModelType = 'GBM' | 'JUMP_DIFFUSION' | 'HESTON' | 'BOOTSTRAP'` and `ForecastDto` interface matching the Java record (p5/p25/p50/p75/p95 number[] + model + horizonDays). No interceptors — follows analytics.ts pattern.
- Extended `chart-colors.ts`: added `FAN_COLORS` with `median`, `bandInner`, `bandOuter` resolved via the existing `cssVar()` helper from `--color-fan-p50`/`--color-fan-band-1`/`--color-fan-band-2`. Critical: ECharts canvas cannot read CSS vars at paint time — this module-init resolution prevents transparent/black band rendering.
- Extended `portfolio.ts`: added `forecast = asyncState<ForecastDto>(null)`, `fetchForecast` action (race-guard + 401 handling mirrors `fetchPairs`), wired into `refreshAll` Promise.allSettled, cleared in `$reset`, exported in return block.

### Task 2: MonteCarloFanChart.vue + DashboardView wiring

- Created `MonteCarloFanChart.vue` (108-line script, 55-line template, 90-line CSS):
  - **5 ECharts series**: base floor at p5 (transparent), diffs p25-p5/p75-p25/p95-p75 (stacked bands), p50 median line (non-stacked). Band-difference stacking is critical — absolute percentile values would render incorrect widths (Pitfall 4 fully mitigated).
  - **FAN_COLORS throughout**: `bandOuter`, `bandInner`, `median` — no hardcoded hex, no `var(--...)` strings in series styling.
  - **Model selector**: 4 toggle buttons (GBM/JUMP_DIFFUSION/HESTON/BOOTSTRAP), `selectedModel` ref, `watch → emit('update:model')` on change.
  - **Heston disclaimer**: `<p v-if="selectedModel === 'HESTON'">` paragraph visible when Heston is selected.
  - **Loading/error/empty states**: follows PnlChart.vue conventions (skeleton, static-copy error + retry, empty text).
  - **No `:theme` prop**: THEME_KEY propagates automatically from App.vue.
- Modified `DashboardView.vue`: replaced the Phase-3 SlotPlaceholder (lines 202-205) with `MonteCarloFanChart` bound to `portfolioStore.forecast.*`; added `retryForecast` handler; `@update:model` calls `portfolioStore.fetchForecast(m)`.
- Fixed `setup.ts` (Rule 3): added ECharts renderer import + VChart/Echarts stubs so `findComponent({ name: 'VChart' })` works in jsdom.
- Fixed `portfolioStore.test.ts` (Rule 3): updated refreshAll count 9→10 and race-guard batch offsets for Phase-5 addition.

### Task 3: docs/MODELS.md (SIM-03) + README link

- Created `docs/MODELS.md` (224 lines): four model sections each with what/why/assumptions/params/limitations; fan-chart reading guide in intro; Merton and Heston params explicitly labeled "illustrative, not calibrated"; appendix covering Ito correction derivation, Feller condition proof (2×2×0.04=0.16>0.09=0.09), block-length rationale (L≈√504≈22), and seed=42 determinism.
- Added `[Model documentation](docs/MODELS.md)` hyperlink in README.md under new "Stochastic Forecasting Models" section.

### Task 4 (checkpoint:human-verify — autonomous)

Per unattended-run instructions: automated proof gates satisfied (`npm run build` exits 0; `npm run test` 56/56 green). Visual verification (fan chart renders, model switching, Heston disclaimer, MODELS.md readability) is deferred to human review.

## Task Commits

| Task | Commit | Files |
|------|--------|-------|
| Task 1 | `e028603` | forecast.ts (new), chart-colors.ts, portfolio.ts |
| Task 2 | `f10a5d0` | MonteCarloFanChart.vue (new), DashboardView.vue, setup.ts, portfolioStore.test.ts |
| Task 3 | `f437157` | docs/MODELS.md (new), README.md |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Fix] ECharts renderer not imported in test environment**
- **Found during:** Task 2 — first test run
- **Issue:** `MonteCarloFanChart.test.ts` tests that mount with actual forecast data failed with "Renderer 'undefined' is not imported". In production, `main.ts` imports `plugins/echarts` as a side-effect; tests bypass `main.ts`.
- **Fix:** Added `import '../plugins/echarts'` to `src/__tests__/setup.ts`.
- **Files modified:** `frontend/src/__tests__/setup.ts`
- **Commit:** `f10a5d0`

**2. [Rule 3 - Fix] VChart component name mismatch for findComponent**
- **Found during:** Task 2 — after ECharts renderer fix, 1 test still failed
- **Issue:** `wrapper.findComponent({ name: 'VChart' })` returned false because vue-echarts exports with internal name `'Echarts'`, not `'VChart'`. The locked test scaffold (05-01 RED) cannot be modified.
- **Fix:** Added `defineComponent({ name: 'VChart', ... })` stub via `config.global.stubs = { VChart: VChartStub, Echarts: VChartStub }` in `setup.ts`. The stub is a lightweight `<div>` — jsdom has no Canvas support.
- **Files modified:** `frontend/src/__tests__/setup.ts`
- **Commit:** `f10a5d0`

**3. [Rule 3 - Fix] portfolioStore test count and race-guard batch offsets**
- **Found during:** Task 2 — full test suite run after adding `fetchForecast` to `refreshAll`
- **Issue:** `portfolioStore.test.ts` hardcoded `refreshAll` as 9 calls; adding `fetchForecast` made it 10.
- **Fix:** Updated test description and mock setup (call count 9→10; race-guard batch B offset call 11→12).
- **Files modified:** `frontend/src/__tests__/portfolioStore.test.ts`
- **Commit:** `f10a5d0`

## Visual Verification Checklist (Deferred — Requires Manual Review)

Per autonomous checkpoint handling: build and tests are green (automated proof). The following visual items require human confirmation:

- [ ] Fan chart renders widening percentile bands over ~252 days with visible median line
- [ ] Clicking GBM/JUMP_DIFFUSION/HESTON/BOOTSTRAP buttons triggers re-fetch and band shape changes
- [ ] JUMP_DIFFUSION/HESTON show fatter or different lower tails than GBM
- [ ] HESTON: illustrative-parameters disclaimer visible
- [ ] docs/MODELS.md reads clearly for a non-specialist, all 4 models covered

## Known Stubs

None — all three deliverables (types, component, docs) are fully implemented and functional. The visual checklist above is deferred verification, not a functional stub.

## Threat Flags

No new security surfaces beyond the plan's threat model:
- T-05-01 (session auth on forecast): `fetchForecast` sets `error = 'Session expired'` on 401; no detail leak.
- T-05-06 (static error copy): component renders "Failed to load forecast…" — no server error detail exposed.
- T-05-02 (model selector): client constrained to `ModelType` literals; server independently validates and returns 400 for unknown values.

---

*Phase: 05-stochastic-forecasting*
*Completed: 2026-06-09*

## Self-Check: PASSED

### Created files exist
- `frontend/src/api/forecast.ts` ✓
- `frontend/src/components/MonteCarloFanChart.vue` ✓
- `docs/MODELS.md` ✓

### Commits verified
- `e028603` feat(05-03): forecast types, FAN_COLORS token resolution, store forecast state ✓
- `f10a5d0` feat(05-03): MonteCarloFanChart.vue + DashboardView wiring; GREEN test suite ✓
- `f437157` docs(05-03): SIM-03 MODELS.md + README link for all 4 stochastic models ✓

### Build + test gates
- `npm run build` → built in 1.35s ✓
- `npm run test -- --run` → 56 passed (56) ✓
- `grep -ci "bootstrap" docs/MODELS.md` → 8 ✓
- `grep -c "[..](docs/MODELS.md)" README.md` → 1 ✓
