---
status: partial
phase: 05-stochastic-forecasting
source: [05-VERIFICATION.md, 05-03-SUMMARY.md]
started: 2026-06-09
updated: 2026-06-09
---

## Current Test

[awaiting human visual/editorial testing — automatable gates (backend 105 tests, frontend 58 tests, build) all pass; MC math anchored by hand-computed HC-11 + boundary regression tests]

## Tests

Require `docker compose up` → http://localhost:5173 → log in. Double as README screenshots.

### 1. Fan chart renders with widening bands
expected: p5–p95 shaded bands + median line over the forward horizon; bands widen with time
result: [pending]

### 2. Model switching
expected: selector (GBM / Jump-Diffusion / Heston / Bootstrap) → fan chart re-fetches and band shape changes per model
result: [pending]

### 3. Heston disclaimer
expected: when Heston selected, an "illustrative parameters (not calibrated)" disclaimer is visible
result: [pending]

### 4. MODELS.md reads clearly
expected: docs/MODELS.md explains each model's rationale/assumptions/params/limitations understandably for a non-specialist; linked from README
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps

None blocking — all code/math wiring verified (7/7 automated must-haves; reproducible seed=42; Ito-correct GBM anchored by HC-11).
