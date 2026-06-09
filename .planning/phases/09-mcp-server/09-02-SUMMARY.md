---
phase: 09-mcp-server
plan: 02
status: complete
completed: 2026-06-10
requirements: [MCP-01, MCP-02]
---

# Plan 09-02 Summary — Green MCP tool tests + README docs

## What was done

- **Tool-correctness + error-hygiene tests GREEN** (`PortfolioMcpToolsTest`, 5 tests):
  - `getPortfolioSummary_returnsCorrectTotalValue` — `totalMarketValue` asserted against the live
    `PortfolioService.getPortfolioPnl(aliceId)` value (service-anchored, not a magic number); allocation non-empty.
  - `getRiskMetrics_matchesGoldenValues` — Sharpe 0.36442669 (±0.001), annualized vol 0.34621361 (±0.0001),
    max drawdown -0.33891522 (±0.0001), beta 1.94917921 (±0.001), historical VaR 1464.52 (±0.01) — all golden.
  - `getPositionDetail_aapl_returnsCorrectHolding` + `getPositionDetail_lowercaseTicker_isSanitizedAndResolves`
    — AAPL holding correct; `"aapl"` is uppercased before lookup (ticker sanitization proven).
  - `toolException_doesNotLeakStackTrace` — a mocked `PortfolioService` throwing
    `RuntimeException("INTERNAL_DB_ERROR_secret_xyz")` yields `isError=true` with content excluding the secret,
    `RuntimeException`, `com.quantlens`, and `java.lang` (full exception logged internally only).
- **`McpAuthIntegrationTest`** (POST /mcp → 401) GREEN.
- **Full backend suite GREEN — 213 tests** (was 207; +6 = 5 PortfolioMcpToolsTest + 1 McpAuthIntegrationTest), no regressions.
- **README "Product MCP Server (MCP-01 / MCP-02)" section** added, distinct from the dev "Dev MCP Servers"
  section: transport/auth table, the three-tool catalog table (params + returns), the `.mcp.json` quantlens
  block, the `alice:demo1234` demo credential + `QUANTLENS_MCP_AUTH` override, and Claude Code connect steps
  (`docker compose up` → `claude mcp get quantlens` → `/mcp`).

## Verification

- `./mvnw -q test -Dtest=PortfolioMcpToolsTest` → EXIT 0 (5/5). `McpAuthIntegrationTest` → 1/1.
- `./mvnw -q test` (full backend) → EXIT 0; surefire tally **213 tests, 0 failures, 0 errors**.
- README grep: `Product MCP Server` + `get_portfolio_summary` + `get_risk_metrics` + `get_position_detail`
  + `http://localhost:8080/mcp` all present → `README_MCP_OK`.

## Return shape

Tools return the real `io.modelcontextprotocol.spec.McpSchema.CallToolResult` (NOT the concrete-record
fallback). Tests extract the `TextContent` JSON and `ObjectMapper`-deserialize into the `*Result` records
before asserting. Confirmed in 09-01-SUMMARY.

## Requirements

- **MCP-01** ✓ — three `@McpTool` beans expose genuinely-correct portfolio analytics (golden/service-anchored,
  no recompute), `/mcp` is auth-gated (401 without credentials), errors carry no stack traces.
- **MCP-02** ✓ — `.mcp.json` quantlens entry committed; README documents connection, auth, and the tool catalog.

## Next

Phase 09 post-merge integration gate → gsd-verifier → gsd-code-review (+ fix) → 09-HUMAN-UAT → phase.complete.
Then Phase 10 (Polish & Documentation), the final phase, followed by the milestone audit → complete → cleanup.
