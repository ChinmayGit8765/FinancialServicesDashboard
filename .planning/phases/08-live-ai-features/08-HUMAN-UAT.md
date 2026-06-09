---
status: partial
phase: 08-live-ai-features
source: [08-VERIFICATION.md, 08-03-SUMMARY.md]
started: 2026-06-10
updated: 2026-06-10
---

## Current Test

[awaiting human/live testing — automatable gates all pass: backend 207 tests, frontend 86 tests, demo no-network proofs (tool + structured) green, KeyLeakage gate (LLM + Finnhub) green]

## Tests

Require `docker compose up` → http://localhost:5173 → log in. Items 1, 3 benefit from a real BYO LLM key (+ optional FINNHUB_API_KEY).

### 1. (live) Tool calling for a quote
expected: with a BYO key, ask "what's AAPL trading at?" → the LLM invokes the getStockQuote @Tool; returns price + as-of; with a FINNHUB_API_KEY set it's a real quote (15-min cache, after-hours labelled), else seeded last close. Key never visible.
result: [pending]

### 2. Structured-output chart renders (demo + live)
expected: the structured-output panel renders the typed DTO as a chart — seeded JSON in demo, LLM-generated (BeanOutputConverter) in live — same component/shape both modes
result: [pending]

### 3. (live) Multi-provider switch
expected: switch Anthropic ↔ OpenAI in the BYO-key popup → a successful (non-502) live AI call from the same ChatClientStrategy.forSession() entry point
result: [pending]

### 4. Demo quote works offline
expected: in demo (no keys), asking for a quote returns the seeded last close — no network
result: [pending]

### 5. (security spot-check) keys never visible
expected: neither the LLM key nor the Finnhub token appears in any response, the quote URL, or logs
result: [pending]

## Summary

total: 5
passed: 0
issues: 0
pending: 5
skipped: 0
blocked: 0

## Gaps

None blocking — demo tool + structured paths proven offline (no-network proofs + key-leak gate green; Finnhub uses header auth so the token never hits the URL). Items 1, 3 inherently need live keys; the wiring is verified correct in code. (Minor: StructuredOutputChart.vue has stale "DEMO STUB" comments — cosmetic, fix in Phase 10 polish.)
