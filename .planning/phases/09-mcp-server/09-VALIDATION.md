---
phase: 9
slug: mcp-server
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-10
---

# Phase 9 — Validation Strategy

> MCP tools reuse the golden-tested services; /mcp is authenticated; errors carry no stack traces.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test + Testcontainers (`AbstractPostgresIntegrationTest`) |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=PortfolioMcpToolsTest,McpAuthIntegrationTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` |
| **Estimated runtime** | ~90–150s |

---

## Sampling Rate
- **Per task commit:** PortfolioMcpToolsTest
- **Per wave:** `.\mvnw.cmd verify`
- **Phase gate:** full backend suite green; /mcp auth test + error-hygiene test green

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| MCP-01 | get_portfolio_summary total matches PortfolioService (seeded) | unit | `PortfolioMcpToolsTest#getPortfolioSummary*` |
| MCP-01 | get_risk_metrics matches RiskCalculator golden values | unit | `PortfolioMcpToolsTest#getRiskMetrics_matchesGoldenValues` |
| MCP-01 | get_position_detail(AAPL) correct holding | unit | `PortfolioMcpToolsTest#getPositionDetail_aapl*` |
| MCP-01 | POST /mcp without auth → 401 | integration | `McpAuthIntegrationTest#mcp_withoutAuth_returns401` |
| MCP-01 | forced tool exception → MCP error, NO stack-trace markers in response | unit | `PortfolioMcpToolsTest#toolException_doesNotLeakStackTrace` |
| MCP-02 | README documents connect + tool catalog; .mcp.json quantlens entry present | file | `grep -i "mcp" README.md`; `test -f .mcp.json` |

---

## Wave 0 Requirements
- [ ] `spring-ai-starter-mcp-server-webmvc` in pom; application.yml MCP server config (name/version, /mcp path, annotation-scanner); SecurityConfig protects /mcp/** + httpBasic + CSRF exemption for /mcp
- [ ] `com.quantlens.mcp` module: PortfolioMcpTools (@McpTool get_portfolio_summary/get_risk_metrics/get_position_detail delegating to PortfolioService + RiskCalculator; SecurityContextHolder → resolvePortfolioId; per-tool try/catch → safe error) + result records
- [ ] PortfolioMcpToolsTest (tool correctness via direct bean calls + mock SecurityContext) + McpAuthIntegrationTest (HTTP /mcp 401) — RED scaffolds
- [ ] `.mcp.json` quantlens entry (type:http, url http://localhost:8080/mcp, Basic auth alice:demo1234 via env default); README MCP section
- [ ] Wave 0 verifies open questions at compile (CallToolResult class path; SecurityContextHolder availability — fallback persona param if null)

---

## Manual-Only Verifications
| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Claude Code connects to /mcp via .mcp.json and calls the 3 tools | MCP-01/02 | needs Claude Code + running stack | `docker compose up` → `claude mcp get quantlens` / `/mcp` → call get_portfolio_summary etc. |

---

## Validation Sign-Off
- [x] Tools reuse golden-tested services (no recompute)
- [x] /mcp authenticated (401 without auth); error hygiene (no stack traces)
- [x] Wave 0 covers starter + tools + security + tests + .mcp.json/README
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-10 (wave_0_complete flips true after Plan 09-01 executes)
