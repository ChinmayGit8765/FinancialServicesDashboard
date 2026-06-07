---
status: partial
phase: 03-frontend-scaffold
source: [03-VERIFICATION.md, 03-05-SUMMARY.md]
started: 2026-06-07
updated: 2026-06-07
---

## Current Test

[awaiting human visual testing — automatable gates (build exit 0, 35/35 tests) all pass]

## Tests

These require a live stack (`docker compose up` → http://localhost:5173) and a browser. They also double as the README hero-screenshot pass.

### 1. Dark theme renders
expected: canvas `#0b0f1a`, cards `#1e293b`; no light backgrounds at ≥1280px
result: [pending]

### 2. P&L equity curve renders
expected: line + gradient area, ~504 points, no ECharts console errors
result: [pending]

### 3. Benchmark chart renders
expected: two lines (portfolio sky-blue, S&P 500 dashed), both starting at 100
result: [pending]

### 4. Allocation donut + treemap toggle
expected: sector donut; toggle switches to treemap
result: [pending]

### 5. Holdings table populated
expected: 5 rows (Alice), green/red P&L colors, sortable columns
result: [pending]

### 6. Transactions paginated
expected: BUY (green) / SELL (red) badges, prev/next pagination
result: [pending]

### 7. KPI strip + Phase 4/5/6 slots
expected: market value, unrealized P&L, daily change cards + labelled dashed placeholders
result: [pending]

### 8. Persona switch Alice→Bob
expected: click Bob → spinner → Bob's portfolio loads, NO full page reload
result: [pending]

### 9. Rapid persona switch
expected: Alice then immediately Bob → only Bob's data shows (race guard) — no data mixing
result: [pending]

### 10. Error states
expected: stop backend, refresh → per-panel error states with retry
result: [pending]

### 11. Logout
expected: redirect to /login
result: [pending]

### 12. Session persistence
expected: login as Alice, F5 → dashboard reloads without re-login
result: [pending]

### 13. Accessibility
expected: keyboard focus ring visible on inputs/buttons; 44px touch targets
result: [pending]

### 14. README AUTH-03
expected: "OAuth Upgrade Path" section present with LlmKeySessionHolder no-code-change note
result: [pending]

## Summary

total: 14
passed: 0
issues: 0
pending: 14
skipped: 0
blocked: 0

## Gaps

None blocking — all code-level wiring verified (10/10 automated must-haves). These are visual/behavioural confirmations best done during the README screenshot pass.
