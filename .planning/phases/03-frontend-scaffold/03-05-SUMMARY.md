---
phase: 03-frontend-scaffold
plan: 05
subsystem: frontend
tags: [vue, dashboard, topbar, persona-switch, kpi, echarts, auth, readme]
dependency_graph:
  requires: [03-01, 03-02, 03-03, 03-04]
  provides: [TopBar.vue, DashboardView.vue (real grid), LoginView.vue (tokenized), README AUTH-03]
  affects: [frontend/src/components, frontend/src/views, README.md]
tech_stack:
  added: []
  patterns: [persona-switch-debounce, refreshVersion-race-guard, CSS-token-only-styling, phase-slot-stubs]
key_files:
  created:
    - frontend/src/components/TopBar.vue
  modified:
    - frontend/src/views/DashboardView.vue
    - frontend/src/views/LoginView.vue
    - README.md
decisions:
  - "TopBar encapsulates persona-list fetch (not prop-drilled from DashboardView) for cohesion"
  - "DashboardView drops useAuthStore import — persona state owned by TopBar; DashboardView is pure data grid"
  - "Phase-4 placeholder KPI cards use KpiCard with primary='—' and 50% opacity wrapper div"
  - "PersonaInfo extended locally in LoginView with optional description?: string — gracefully absent today"
metrics:
  duration: "4 minutes"
  completed: "2026-06-07T06:13:33Z"
  tasks: 3
  files: 4
requirements_satisfied: [UI-01, AUTH-03]
---

# Phase 3 Plan 5: TopBar + DashboardView Grid + LoginView Polish + AUTH-03 Summary

**One-liner:** Fixed top bar with debounced persona switcher (loginAs+refreshAll, race-safe), real 12-column dashboard grid wiring portfolio store to all five chart/table components plus Phase 4/5/6 SlotPlaceholder stubs, tokenized LoginView, and README OAuth AI-seam guarantee for AUTH-03.

---

## Tasks Completed

| Task | Name | Commit | Key Files |
|------|------|--------|-----------|
| 1 | TopBar.vue — fixed bar, persona switcher, logout | bbc218a | frontend/src/components/TopBar.vue |
| 2 | Replace DashboardView.vue with real grid + slots | 871dd4a | frontend/src/views/DashboardView.vue |
| 3 | Tokenize LoginView.vue + augment README AUTH-03 | 75e0d3c | frontend/src/views/LoginView.vue, README.md |
| 4 | Checkpoint: automated gates passed (build + test) | — | Deferred manual checklist below |

---

## What Was Built

### TopBar.vue (new)
- Fixed 48px bar (`position:fixed; top:0; z-index:100`), `var(--color-bg-elevated)` background
- Left: QuantLens wordmark (20px/700/`--color-accent`) + "AI Portfolio Intelligence" subtitle (11px/muted)
- Center: `<nav aria-label="Persona switcher">` — 3 pills, `aria-pressed` on active, 44px min-height, `--radius-pill`
- `switchPersona()`: debounced with `switching` ref; `await authStore.loginAs(username, 'demo1234')` then `await portfolioStore.refreshAll()`; rapid second click is ignored while in-flight (T-03-12)
- "Switching to {Persona}…" text replaces active pill text during transition; all pills disabled while switching
- Right: `@username` (14px/secondary) + "Log out" button (transparent, border/destructive on hover)
- No v-html anywhere

### DashboardView.vue (full replace)
- `onMounted(() => portfolioStore.refreshAll())` — data loads immediately on view entry
- KPI strip: Market Value, Unrealized P&L (with secondary + delta), Daily Change (with secondary + delta) + 2 Phase-4 KpiCard shells (primary="—", opacity 0.5)
- Row 2: PnlChart (col 7) + BenchmarkChart (col 5)
- Row 3: AllocationChart (col 6) + SlotPlaceholder "Risk Scorecard — Phase 4" (col 6, 280px)
- Row 4: SlotPlaceholder "Correlation Heatmap — Phase 4" (col 12, 240px)
- Row 5: SlotPlaceholder "Monte Carlo Forecast — Phase 5" (col 12, 320px)
- Row 6: HoldingsTable (col 12) with `@retry="retryHoldings"`
- Row 7: TransactionsTable (col 12) with `@page-change="handleTransactionsPage"` (NOT `@page`)
- Row 8: SlotPlaceholder "AI Daily Commentary — Phase 6" (col 12, 120px)
- Row 9: SlotPlaceholder "AI Q&A — Phase 6" (col 8, 400px) + SlotPlaceholder "LLM Key — Phase 6" (col 4, 320px)
- Responsive: ≤1279px all chart/panel cols stack to col-12
- Phase 1 "Walking Skeleton" badge completely removed
- No v-html; CSS tokens only

### LoginView.vue (modified in-place)
- All hardcoded hex values replaced: `#0f172a` → `var(--color-bg-base)`, `#1e293b` → `var(--color-bg-surface)`, `#334155` → `var(--color-border)`, `#0ea5e9` → `var(--color-accent)`, etc.
- Persona buttons: `min-height: 44px` (WCAG 2.5.5), `flex: 1`, `background: var(--color-bg-elevated)`, `border: var(--color-border)`, hover uses `--color-accent-subtle` + `--color-accent-light`
- Copywriting: "Log in as {Persona}" per UI-SPEC Copywriting Contract
- Optional description: `<span v-if="p.description" class="persona-desc">{{ p.description }}</span>` — degrades gracefully (backend PersonaInfo has no `description` field today)
- Script setup logic unchanged (onMounted, handleLogin, loginAsPersona)

### README.md (AUTH-03 augmented)
- OAuth Upgrade Path section now explicitly states the AI-seam guarantee:
  `LlmKeySessionHolder` and downstream Spring AI features depend on the HTTP session, not the login mechanism — swapping form login for OAuth2 login requires NO code changes to AI components

---

## Automated Gates

| Gate | Result |
|------|--------|
| `npm run build` | PASS (exit 0; 715 modules transformed) |
| `npm run test` | PASS (35/35 tests, 5 test files) |
| `grep -i "LlmKeySessionHolder" README.md` | FOUND |
| `grep -i "OAuth Upgrade" README.md` (count) | 1 (no duplicate heading) |

---

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Removed unused `authStore` variable from DashboardView**
- **Found during:** Task 2 verification (`npm run build` caught TS6133)
- **Issue:** `useAuthStore()` was imported and called but its return value was unused; TypeScript strict mode flagged it
- **Fix:** Removed import and call — TopBar owns persona state; DashboardView only needs portfolioStore
- **Files modified:** `frontend/src/views/DashboardView.vue`
- **Commit:** 871dd4a (inline fix before final commit)

**2. [Rule 1 - Bug] Removed unused `retryTransactions` function**
- **Found during:** Task 2 verification (`npm run build` caught TS6133)
- **Issue:** `retryTransactions` was defined but TransactionsTable only emits `page-change` (no retry emit)
- **Fix:** Removed the unused function; page-0 reload is handled by `handleTransactionsPage(0)` pattern from the component's own retry link
- **Files modified:** `frontend/src/views/DashboardView.vue`
- **Commit:** 871dd4a (inline fix before final commit)

---

## Task 4 — Checkpoint: Automated Verification (Unattended Run)

Per the autonomous checkpoint instructions, the automatable proof was executed:
- `npm run build` exits 0
- `npm run test` 35/35 green

Full visual verification is inherently manual. The following 14-point checklist is deferred for human verification when running the full stack:

### Deferred Manual Visual Checklist

1. Open the dashboard at ≥1280px. Confirm dark theme: canvas `#0b0f1a`, cards `#1e293b` — no light backgrounds.
2. P&L equity curve renders (line + gradient area, ~504 points, no console errors about ECharts registration).
3. Benchmark chart shows two lines (portfolio sky-blue solid, S&P 500 dashed secondary), both starting at 100.
4. Allocation donut renders with 8-color sectors; the Donut/Treemap toggle switches views.
5. Holdings table shows Alice's 5 rows with green/red P&L colors; Ticker/Market Value/P&L column sort works.
6. Transactions table: BUY badge green, SELL badge red, prev/next pagination works.
7. KPI strip: 3 real cards (Market Value, Unrealized P&L, Daily Change) + 2 Phase-4 placeholder cards (—, 50% opacity).
8. Persona switch Alice→Bob: click "Bob" → "Switching to…" spinner → data reloads to Bob's portfolio — NO full page reload.
9. Rapid switch: click Alice then immediately Bob → only Bob's data loads, no mixing.
10. Error states: stop the backend, refresh → each panel shows a red error state with a Retry link.
11. Logout → redirected to /login.
12. Session persistence: log in as Alice, F5 → dashboard reloads Alice's data without re-login.
13. Phase 4/5/6 slot stubs show dashed-border placeholders with correct labels.
14. README contains the OAuth Upgrade Path section with the LlmKeySessionHolder AI-seam note.

---

## Known Stubs

None that block the plan's goals. The Phase 4/5/6 SlotPlaceholder stubs are intentional and labelled per plan spec.

---

## Threat Flags

No new threat surface introduced. All threats in the plan's threat register were addressed:
- T-03-11 (CSRF on persona switch): /api/auth/login remains ignoringRequestMatchers; SameSite=Lax is the defence
- T-03-12 (stale persona data): `switching` ref debounce + portfolio store refreshVersion guard both in place
- T-03-13 (XSS): all persona/username strings via Vue interpolation; no v-html
- T-03-14 (logout): authStore.logout() clears state + 401 interceptor covers stale requests

---

## Self-Check

Files exist:
- frontend/src/components/TopBar.vue: CREATED
- frontend/src/views/DashboardView.vue: MODIFIED (full replace)
- frontend/src/views/LoginView.vue: MODIFIED
- README.md: MODIFIED

Commits:
- bbc218a: feat(03-05): TopBar.vue
- 871dd4a: feat(03-05): replace DashboardView.vue
- 75e0d3c: feat(03-05): tokenize LoginView.vue + augment README AUTH-03

## Self-Check: PASSED
