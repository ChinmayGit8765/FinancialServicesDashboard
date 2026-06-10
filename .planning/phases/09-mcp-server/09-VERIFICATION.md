---
phase: 09-mcp-server
verified: 2026-06-10T00:00:00Z
status: human_needed
score: 3/3 must-haves verified
overrides_applied: 0
human_verification:
  - test: "An MCP client connects to http://localhost:8080/mcp via the committed .mcp.json and calls all three tools"
    expected: "get_portfolio_summary, get_risk_metrics, get_position_detail each return correctly-computed JSON from seeded data"
    why_human: "Needs a running docker compose stack and a live MCP client (Claude Code or equivalent); cannot be tested by static analysis or without starting the server"
---

# Phase 9: MCP Server Verification Report

**Phase Goal:** Portfolio analytics are exposed as a product MCP server via @McpTool beans on Streamable HTTP transport, secured by Spring Security, with a .mcp.json config that allows Claude Code to connect — and documentation shows any MCP client how to use it.
**Verified:** 2026-06-10
**Status:** human_needed
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | An MCP client can connect to http://localhost:8080/mcp using the committed .mcp.json and call get_portfolio_summary, get_risk_metrics, and get_position_detail, receiving correctly-computed responses from seeded data | ? HUMAN_NEEDED | Tool beans, transport config, and .mcp.json are all in place and proven by direct-bean tests (see SC-1 detail below); end-to-end wire test requires running stack + live MCP client |
| 2 | The /mcp endpoint requires authentication before any tool is reachable; MCP error responses never contain Java stack traces | ✓ VERIFIED | SecurityConfig.java:137-142 + McpAuthIntegrationTest.java:28-40 + PortfolioMcpTools.java:107-119 |
| 3 | The README documents how any MCP client connects to the server and what tools are available | ✓ VERIFIED | README.md:169-229 — "Product MCP Server" section present with transport/auth table, 3-tool catalog, .mcp.json block, connect steps |

**Score:** 3/3 truths supported by code evidence; SC-1 end-to-end wire path needs human confirmation (documented manual-only per 09-VALIDATION.md)

---

## Success Criterion Detail

### SC-1: MCP client can connect and call the three tools (receiving correct seeded-data responses)

**Automated evidence — tool bean correctness:**

`PortfolioMcpToolsTest.java` exercises all three tools as direct Spring beans against a live Testcontainers Postgres with seeded data:

- `getPortfolioSummary_returnsCorrectTotalValue` (line 91): asserts `totalMarketValue` equals `PortfolioService.getPortfolioPnl(aliceId)` — service-anchored, no magic number. Allocation list non-empty.
- `getRiskMetrics_matchesGoldenValues` (line 108): Sharpe 0.36442669 (±0.001), annualized vol 0.34621361 (±0.0001), max drawdown -0.33891522 (±0.0001), beta 1.94917921 (±0.001), historical VaR 1464.52 (±0.01) — verbatim from RiskCalculatorTest golden constants, proving no recompute.
- `getPositionDetail_aapl_returnsCorrectHolding` + `getPositionDetail_lowercaseTicker_isSanitizedAndResolves` (lines 125, 138): AAPL holding resolves; "aapl" is uppercased before lookup.

All 5 tests green in the full 213-test suite (09-02-SUMMARY.md, 0 failures).

**Automated evidence — transport + annotation scanner registered:**

`McpAuthIntegrationTest` boots the full application context with `spring-ai-starter-mcp-server-webmvc` loaded. The test issues `POST /mcp` with no credentials and asserts `HTTP 401`. This proves: (a) the MCP starter loaded successfully, (b) `/mcp` is a mapped endpoint, (c) Spring Security gates it before tool dispatch. `QuantLensModulithTest` (ApplicationModules.verify()) passes with the mcp module and new named interfaces — no boundary violations, no cycles (09-01-SUMMARY.md).

**Transport + config wiring:**
- `pom.xml:121` — `spring-ai-starter-mcp-server-webmvc` declared under Spring AI BOM 1.1.6 (no explicit version, BOM-managed).
- `application.yml:53-62` — `spring.ai.mcp.server`: name `quantlens-mcp`, version 1.0.0, protocol `STREAMABLE`, type `SYNC`, `annotation-scanner.enabled: true`, `streamable-http.mcp-endpoint: /mcp`.
- `.mcp.json:15-20` — `quantlens` entry: `type: http`, `url: http://localhost:8080/mcp`, `headers.Authorization: "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}"` (base64 = alice:demo1234).

**What automation cannot prove:** Whether the MCP JSON-RPC wire protocol (tools/list → tools/call) actually produces correct JSON to an external MCP client when the stack is running. This requires `docker compose up` + an MCP client session.

---

### SC-2: /mcp requires authentication; error responses never contain Java stack traces

**Authentication gate — VERIFIED:**

`SecurityConfig.java:84-94`: `.anyRequest().authenticated()` covers `/mcp` (no `permitAll` for `/mcp`). Line 142: `.httpBasic(basic -> basic.realmName("QuantLens MCP"))` added for machine-client auth, coexisting with `formLogin`. `McpAuthIntegrationTest` (HTTP 401 without credentials) proves the gate fires before tool dispatch.

**CSRF exemption scope — correct:**

`SecurityConfig.java:137`: `AntPathRequestMatcher.antMatcher("/mcp")` — the CSRF exemption is scoped to the exact path `/mcp`, not `/mcp/**`. The comment at line 133-137 documents the rationale: "Exemption is safe because /mcp requires HTTP Basic authentication — not session-based." Auth gate remains; this is not a security relaxation.

**Error hygiene — VERIFIED:**

`PortfolioMcpTools.java:113-119` (getPortfolioSummary generic catch), 167-173 (getRiskMetrics), 231-237 (getPositionDetail): every catch block logs the full exception internally via `log.error(...)` and returns `McpSchema.CallToolResult.builder().isError(true)` with a static safe string — never `e.getMessage()`, never class names, never stack trace forwarding.

`PortfolioMcpToolsTest.java:147-166` (`toolException_doesNotLeakStackTrace`): a `PortfolioService` mock throws `RuntimeException("INTERNAL_DB_ERROR_secret_xyz")`; the test asserts the result text does NOT contain the secret, "RuntimeException", "com.quantlens", or "java.lang". GREEN in 213-test suite.

---

### SC-3: README documents connection and tool catalog

**VERIFIED.**

`README.md:169-229` — "Product MCP Server (MCP-01 / MCP-02)" section:
- Transport/auth table: endpoint, protocol, auth method, demo credential (line 185-188)
- Three-tool catalog with params and returns (lines 198-200)
- `.mcp.json` quantlens block reproduced verbatim (lines 207-211)
- `QUANTLENS_MCP_AUTH` override instruction (lines 214-216)
- Step-by-step Claude Code connect instructions: `docker compose up` → `claude mcp get quantlens` → call tools (lines 219-225)
- Generic MCP client connect instructions (lines 228-229)

---

## Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java` | 3 @McpTool beans, SecurityContextHolder principal, per-tool try/catch with static safe errors | ✓ VERIFIED | Lines 76-238; all 3 tools present; principal from SecurityContextHolder:255-261; try/catch with static messages on every tool |
| `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` | httpBasic added; /mcp in CSRF ignoringRequestMatchers; anyRequest().authenticated() covers /mcp | ✓ VERIFIED | httpBasic:142; CSRF exemption:137; anyRequest():94 |
| `backend/src/main/resources/application.yml` | spring.ai.mcp.server config (STREAMABLE, /mcp, annotation-scanner enabled) | ✓ VERIFIED | Lines 52-62 |
| `backend/pom.xml` | spring-ai-starter-mcp-server-webmvc (BOM-managed) | ✓ VERIFIED | Line 121 |
| `backend/src/main/java/com/quantlens/mcp/package-info.java` | @ApplicationModule with allowedDependencies for portfolio+analytics | ✓ VERIFIED | Lines 17-26; 5 allowed dependencies |
| `backend/src/main/java/com/quantlens/portfolio/service/package-info.java` | @NamedInterface("service") | ✓ VERIFIED | Line 6 |
| `backend/src/main/java/com/quantlens/portfolio/api/package-info.java` | @NamedInterface("api") | ✓ VERIFIED | Line 7 |
| `backend/src/main/java/com/quantlens/portfolio/domain/package-info.java` | @NamedInterface("domain") | ✓ VERIFIED | Line 5 |
| `backend/src/main/java/com/quantlens/analytics/service/package-info.java` | @NamedInterface("service") | ✓ VERIFIED | Line 6 |
| `backend/src/main/java/com/quantlens/analytics/api/package-info.java` | @NamedInterface("api") | ✓ VERIFIED | Line 7 |
| `.mcp.json` | quantlens entry: type http, url http://localhost:8080/mcp, Basic auth | ✓ VERIFIED | Lines 15-20 |
| `README.md` | Product MCP Server section with connect steps + tool catalog | ✓ VERIFIED | Lines 169-229 |
| `backend/src/test/java/com/quantlens/mcp/PortfolioMcpToolsTest.java` | Golden/service-anchored correctness + error hygiene | ✓ VERIFIED | 5 tests; golden values from RiskCalculatorTest |
| `backend/src/test/java/com/quantlens/mcp/McpAuthIntegrationTest.java` | POST /mcp → 401 | ✓ VERIFIED | Line 38-40 |

---

## Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `PortfolioMcpTools.getPortfolioSummary` | `PortfolioService.getPortfolioPnl` + `getAllocation` | Direct Spring injection (constructor line 56) | ✓ WIRED | Calls at lines 85-86 |
| `PortfolioMcpTools.getRiskMetrics` | `RiskCalculator.computeRiskScorecard` | Direct Spring injection (constructor line 57) | ✓ WIRED | Call at line 136 |
| `PortfolioMcpTools.getPositionDetail` | `PortfolioService.getHoldings` | Direct Spring injection | ✓ WIRED | Call at line 195 |
| `PortfolioMcpTools.resolvePortfolioId` | `SecurityContextHolder` → `PortfolioRepository.findPortfolioIdByUsername` | SecurityContextHolder:255 → repository:261 | ✓ WIRED | IDOR-safe: no portfolio ID in tool params |
| `application.yml` MCP config | Spring AI annotation scanner → `@McpTool` beans | `annotation-scanner.enabled: true`; `QuantLensModulithTest` ApplicationModules.verify() | ✓ WIRED (compile/boot proven) | Annotation scanner auto-config confirmed in 09-01-SUMMARY.md |
| `.mcp.json quantlens` | `http://localhost:8080/mcp` | `type: http`, Basic auth header | ✓ WIRED (static) | Wire works at runtime — needs running stack for end-to-end |
| `SecurityConfig` | `/mcp` requires HTTP Basic auth | `anyRequest().authenticated()` + `.httpBasic(...)` | ✓ WIRED | McpAuthIntegrationTest proves 401 gate |

---

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `getPortfolioSummary` | `pnl`, `alloc` | `PortfolioService.getPortfolioPnl` / `getAllocation` (JPA → seeded Postgres) | Yes — service-anchored test asserts equality with live service output | ✓ FLOWING |
| `getRiskMetrics` | `scorecard` | `RiskCalculator.computeRiskScorecard` (Hipparchus math over seeded price series) | Yes — golden values match RiskCalculatorTest (±tolerance) | ✓ FLOWING |
| `getPositionDetail` | `holdings` | `PortfolioService.getHoldings` (JPA → seeded holdings) | Yes — AAPL holding resolved and asserted non-null | ✓ FLOWING |

---

## Behavioral Spot-Checks

Step 7b: SKIPPED for SC-1 end-to-end (requires running server — routed to human verification below). Transport and auth spot-checks covered by McpAuthIntegrationTest (POST /mcp → 401, boot proven).

---

## Probe Execution

No `probe-*.sh` files declared or found for Phase 9. Phase gate was verified by `mvnw test` (213 tests, 0 failures, 0 errors — documented in 09-02-SUMMARY.md).

---

## Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| MCP-01 | 09-01-PLAN, 09-02-PLAN | @McpTool server with 3 tools, auth-gated, error hygiene | ✓ SATISFIED | PortfolioMcpTools.java, SecurityConfig.java, McpAuthIntegrationTest, PortfolioMcpToolsTest (all 5 green) |
| MCP-02 | 09-01-PLAN, 09-02-PLAN | .mcp.json quantlens entry + README connection + tool docs | ✓ SATISFIED | .mcp.json:15-20, README.md:169-229 |

---

## Anti-Patterns Found

No anti-patterns detected in `com.quantlens.mcp` package:
- No TBD/FIXME/XXX/TODO/HACK/PLACEHOLDER markers
- No stub returns (return null / return {} / return [])
- No exception message forwarding to client (every catch uses static strings)
- No hardcoded empty data passed to rendering paths

One note: The CSRF exemption at `SecurityConfig.java:137` uses `AntPathRequestMatcher.antMatcher("/mcp")` (exact path, not `/mcp/**`). This is correct for the Streamable HTTP single-endpoint pattern but worth confirming if MCP sub-paths are ever added in a future phase.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| (none) | — | — | — | — |

---

## Human Verification Required

### 1. End-to-End MCP Client Connection and Tool Call

**Test:** Start the stack with `docker compose up`, open a Claude Code session in the project directory, run `claude mcp get quantlens`, then invoke each tool:
- "Use get_portfolio_summary to show my portfolio value and allocation"
- "Use get_risk_metrics to show Sharpe ratio and 95% VaR"
- "Use get_position_detail with ticker AAPL"

**Expected:**
- `claude mcp get quantlens` shows `quantlens` server connected with 3 tools listed
- Each tool invocation returns correctly-computed JSON from alice's seeded portfolio (total market value non-zero, Sharpe ~0.364, AAPL position present)
- No stack trace or internal error text in any response

**Why human:** Requires a running `docker compose` stack and a live MCP client session. Static analysis and direct-bean tests prove the tool logic and transport config are correct, but the MCP JSON-RPC wire protocol handshake (tools/list + tools/call over Streamable HTTP with HTTP Basic auth) can only be confirmed with an actual running stack and client.

---

## Gaps Summary

No blocking gaps. All three success criteria are met at the code level:

1. **SC-1** — Three `@McpTool` beans exist, produce correctly-computed seeded values (golden-value tests), delegate to PortfolioService + RiskCalculator without recompute, resolve principal from SecurityContextHolder (IDOR-safe), and the transport/auth configuration is complete and boot-proven. The end-to-end wire test is classified as manual-only per 09-VALIDATION.md (requires running stack + MCP client).

2. **SC-2** — `/mcp` is behind `.anyRequest().authenticated()` + `.httpBasic(...)`. CSRF exemption is correctly scoped to `/mcp` only. Every tool's error path returns a static safe string with no exception details forwarded to the client. `McpAuthIntegrationTest` and `toolException_doesNotLeakStackTrace` are both green.

3. **SC-3** — README "Product MCP Server" section is substantive: transport/auth table, three-tool catalog, `.mcp.json` block, step-by-step Claude Code connect instructions, and generic MCP client guidance.

The sole item routed to human verification is the live end-to-end MCP client connection — intentionally documented as manual-only in 09-VALIDATION.md because it requires a running stack, and is already proven correct at the component level by 213 green tests.

---

_Verified: 2026-06-10_
_Verifier: Claude (gsd-verifier)_
