---
status: partial
phase: 09-mcp-server
source: [09-VERIFICATION.md, 09-VALIDATION.md, 09-02-SUMMARY.md]
started: 2026-06-10
updated: 2026-06-10
---

## Current Test

[awaiting human/live testing — all automatable gates pass: backend 215 tests green;
Modulith verify green; POST /mcp unauthenticated → 401 (boot proof); tool-correctness
golden values + error-hygiene (no stack-trace leak) green; README MCP section present]

## Tests

Require `docker compose up` so `/mcp` is live on port 8080, plus an MCP client (Claude Code).
The committed `.mcp.json` `quantlens` entry auto-loads inside this project directory.

### 1. (live) Claude Code connects to the product MCP server
expected: from this project dir, `claude mcp get quantlens` shows the server; in a Claude Code
session, `/mcp` lists the three tools (get_portfolio_summary, get_risk_metrics, get_position_detail).
The `${QUANTLENS_MCP_AUTH:-…}` default decodes to alice:demo1234 and authenticates (HTTP Basic).
result: [pending]

### 2. (live) Tools return correct seeded analytics over the wire
expected: ask "use get_risk_metrics" → Sharpe ≈ 0.364, 95% VaR ≈ $1464.52 (alice Growth);
"use get_portfolio_summary" → total market value matches the dashboard; "get_position_detail AAPL"
→ AAPL holding. Values match the REST API / Vue UI (same golden-tested services, no recompute).
result: [pending]

### 3. (security spot-check) /mcp is auth-gated; errors carry no stack traces
expected: an MCP call with no/!wrong credentials is rejected (401); a forced tool error returns a
clean message with no Java stack trace, class name, or secret. (Proven in tests; confirm live.)
result: [pending]

### 4. (screenshot) MCP tools in a Claude Code session for the README
expected: capture a screenshot of Claude Code calling a QuantLens MCP tool and rendering the
computed result — the "freshest 2026 resume signal" demo image.
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps

None blocking. The MCP tool beans are proven correct by direct-bean golden-value tests, the
transport+auth+boundary are proven by the boot + 401 + Modulith gates, and the docs are in place.
Items 1, 2, 4 inherently need a running stack + live MCP client (the end-to-end JSON-RPC handshake
is documented manual-only per 09-VALIDATION.md) and overlap with the README screenshot goal.
