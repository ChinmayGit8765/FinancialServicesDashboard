---
status: partial
phase: 04-quant-risk-engine
source: [04-VERIFICATION.md, 04-04-SUMMARY.md]
started: 2026-06-08
updated: 2026-06-08
---

## Current Test

[awaiting human visual testing — automatable gates (backend 80 tests, frontend 48 tests, build) all pass; math independently verified via hand-computed tests]

## Tests

Require `docker compose up` → http://localhost:5173 → log in. Double as README screenshots.

### 1. KPI strip shows real risk values
expected: Sharpe / Vol KPI cards show computed numbers (no "Phase 4" placeholders)
result: [pending]

### 2. Risk Scorecard panel
expected: Sharpe, annualized vol, max drawdown, beta cards + HISTORICAL/PARAMETRIC VaR table (positive amounts, labelled confidence/horizon)
result: [pending]

### 3. Correlation heatmap
expected: 5×5 grid, blue(−1)→white(0)→red(+1), ticker labels, hover tooltip names the CORRECT pair
result: [pending]

### 4. Attribution bar chart
expected: alpha + Mkt-RF/SMB/HML contribution bars with percent labels; R² shown
result: [pending]

### 5. Pairs table
expected: renders (empty-state for the GBM seed is expected — see note); columns pair/p-value/hedge ratio/Z-score/signal
result: [pending]

### 6. Persona switch
expected: Alice→Bob→Charlie re-scopes all analytics panels without full reload, no console errors
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps

- Known: cointegration pairs scanner finds 0 pairs because the seed uses independent GBM (no cointegration by construction). The scanner is correct + hand-tested; consider seeding one cointegrated pair in the polish phase (Phase 10) to make the demo table non-empty. Tracked as a seed enhancement.
