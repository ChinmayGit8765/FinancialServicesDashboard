---
status: partial
phase: 06-demo-mode-ai-seam
source: [06-VERIFICATION.md, 06-04-SUMMARY.md]
started: 2026-06-09
updated: 2026-06-09
---

## Current Test

[awaiting human visual testing — automatable gates all pass: backend 147 tests, frontend 82 tests, KeyLeakage security gate green, executable no-network proof green]

## Tests

Require `docker compose up` → http://localhost:5173 → log in. These are the headline AI screenshots for the README.

### 1. Demo mode out of the box (no key)
expected: explain drawer + daily commentary + structured-output panel all render authored, LLM-looking content with NO key entered
result: [pending]

### 2. BYO-key popup → Live badge flip
expected: open the popup, choose Anthropic/OpenAI, paste a key → top-bar badge flips Demo→Live; "return to demo" clears it. (Optional: a real key makes responses live.)
result: [pending]

### 3. Persona-distinct commentary
expected: Alice (growth) / Bob (income) / Charlie (balanced) commentary reads distinctly and matches each portfolio
result: [pending]

### 4. Explain-this-position drawer
expected: click a holding row → drawer opens with a narrative naming that ticker; persona-neutral content (no contradicting share counts); X closes it
result: [pending]

### 5. Structured-output panel renders
expected: the seeded structured-output chart renders (ECharts)
result: [pending]

### 6. (security spot-check) key never visible
expected: after entering a key, it never appears in the page, network responses, or any visible state; refresh keeps demo/live per session
result: [pending]

## Summary

total: 6
passed: 0
issues: 0
pending: 6
skipped: 0
blocked: 0

## Gaps

None blocking — key-leak security gate + executable no-network proof are automated and green. These are visual/UX confirmations + the README hero AI screenshots.
