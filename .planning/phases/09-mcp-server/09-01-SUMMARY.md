---
phase: 09-mcp-server
plan: 01
status: complete
completed: 2026-06-10
requirements: [MCP-01, MCP-02]
---

# Plan 09-01 Summary — MCP Server Slice (starter + tools + auth + boundary)

## What was built

The thinnest end-to-end product MCP server for QuantLens — a thin protocol adapter over
the already-golden-tested portfolio + analytics services (no recompute):

- **Starter + transport**: `spring-ai-starter-mcp-server-webmvc` (BOM 1.1.6, no explicit version).
  `application.yml` → `spring.ai.mcp.server`: name `quantlens-mcp`, version 1.0.0, protocol
  `STREAMABLE`, type `SYNC`, `annotation-scanner.enabled: true`, `streamable-http.mcp-endpoint: /mcp`.
- **`com.quantlens.mcp` Modulith module** (`@ApplicationModule`) with `allowedDependencies =
  {portfolio::service, portfolio::api, portfolio::domain, analytics::service, analytics::api}`.
  Four NEW `@NamedInterface` package-info files expose the service/api packages (the mcp module is
  the first cross-boundary consumer of those service beans).
- **`PortfolioMcpTools`** (`@Component`) — three `@McpTool` beans delegating to `PortfolioService`
  + `RiskCalculator`:
  - `get_portfolio_summary` → total MV / cost basis / unrealized P&L / daily change / sector allocation
  - `get_risk_metrics` → Sharpe, annualized vol, max drawdown, beta, historical + parametric VaR95
  - `get_position_detail(ticker)` → single holding (ticker sanitized: uppercase, `[A-Z0-9.]`, max 10)
  - Principal resolved ONLY from `SecurityContextHolder` → `findPortfolioIdByUsername` (IDOR-safe, T-09-02);
    every body try/catch → `CallToolResult.builder().isError(true)` with a STATIC safe message
    (full exception logged internally, never forwarded — T-09-03).
- **Three concrete result records** (`PortfolioSummaryResult` + nested `AllocationEntry`,
  `RiskMetricsResult`, `PositionDetailResult`) — BigDecimal money, no interfaces/annotations.
- **SecurityConfig** (two additive edits): `/mcp` added to CSRF `.ignoringRequestMatchers(...)`
  (T-09-04 — safe only because `/mcp` requires auth); `.httpBasic(realmName "QuantLens MCP")` added,
  coexisting with `formLogin`. `/mcp` stays under `.anyRequest().authenticated()`.
- **`.mcp.json`**: third `quantlens` entry — `{ type: http, url: http://localhost:8080/mcp,
  headers.Authorization: "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}" }`. context7 + project-db preserved.
- **RED scaffolds**: `PortfolioMcpToolsTest` (golden-value correctness + lowercase-ticker sanitization
  + `toolException_doesNotLeakStackTrace`), `McpAuthIntegrationTest` (`mcp_withoutAuth_returns401`).

## Verification

- `./mvnw -q -DskipTests test-compile` → **EXIT 0** (main + test sources compile; open questions resolved).
- `./mvnw -q test -Dtest=QuantLensModulithTest,McpAuthIntegrationTest` → **EXIT 0**:
  - `ApplicationModules.verify()` **passes** with the mcp module + new named interfaces (boundary valid, no cycles).
  - App **boots** with the MCP starter; `McpServerAnnotationScannerAutoConfiguration` +
    `serverAnnotatedBeanRegistry` load with no error; **POST /mcp without auth → 401** (gate fires before tool dispatch).
- `PortfolioMcpToolsTest` golden-value assertions are intentionally **RED until Plan 09-02** (greened there).

## Open Questions Resolved

- **Open Q1 — CallToolResult class path / SDK coordinates**: the result type is
  `io.modelcontextprotocol.spec.McpSchema.CallToolResult`, built via
  `McpSchema.CallToolResult.builder().content(List.of(new McpSchema.TextContent(json))).isError(bool).build()`.
  SDK jar: the MCP Java SDK (`io.modelcontextprotocol.sdk`) pulled transitively by the starter.
  The **concrete-result-record fallback was NOT needed** — the real CallToolResult builder is on the classpath.
- **Annotation package correction (vs plan assumption)**: `@McpTool` / `@McpToolParam` live in
  **`org.springaicommunity.mcp.annotation`** (the spring-ai community MCP annotations jar) — NOT
  `org.springframework.ai.mcp.annotation` as the plan text guessed. Imports corrected accordingly.
- **Open Q2 — annotation-scanner**: `spring.ai.mcp.server.annotation-scanner.enabled: true` confirmed;
  at boot `McpServerAnnotationScannerAutoConfiguration` discovered the `@McpTool` beans (registry bean
  present, no errors) — the three tools are registered on `/mcp`.
- **A3 — SecurityContextHolder availability in @McpTool**: resolves cleanly at compile;
  `SecurityContextHolder.getContext().getAuthentication()` is the principal source (no `Authentication`
  param injection in @McpTool methods). Bean-level auth resolution (mock context for alice) is exercised
  by `PortfolioMcpToolsTest` in Plan 09-02; the HTTP 401 path already proves the security filter precedes the MCP handler.
- **.mcp.json demo credential**: base64 `YWxpY2U6ZGVtbzEyMzQ=` decodes to `alice:demo1234` — the seeded
  demo password already shown on the login screen and README. Safe to commit; overridable via `QUANTLENS_MCP_AUTH`.

## Deviations

- Annotation import package corrected from the plan's assumed `org.springframework.ai.mcp.annotation`
  to the actual `org.springaicommunity.mcp.annotation` (Open Q1 resolution). No scope change.

## Files

pom.xml, application.yml, SecurityConfig.java, 5 package-info.java (4 named-interface + 1 mcp module),
PortfolioMcpTools.java, 3 result records, PortfolioMcpToolsTest.java, McpAuthIntegrationTest.java, .mcp.json.

## Next

Plan 09-02: green the `PortfolioMcpToolsTest` golden-value + error-hygiene assertions (already authored
and passing-by-construction), run the full backend suite, and write the README MCP section (MCP-02 docs half).
