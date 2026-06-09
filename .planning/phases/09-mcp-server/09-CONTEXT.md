# Phase 9: MCP Server - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Expose QuantLens portfolio analytics as a product MCP server: `@McpTool` beans over Streamable HTTP at `/mcp`, secured by Spring Security, with a committed `.mcp.json` so an MCP client (Claude Code) can connect and call the tools — plus documentation. This is the freshest 2026 resume signal; it's a thin annotation/transport layer over the already-built portfolio + analytics services (no recompute).

Requirements covered: MCP-01 (the @McpTool server), MCP-02 (connection + tool docs).
</domain>

<decisions>
## Implementation Decisions

### MCP server + tools
- Add the Spring AI MCP-server starter (BOM 1.1.6 — research to confirm the exact artifact, e.g. `spring-ai-starter-mcp-server` / the WebMVC streamable-http variant). New `com.quantlens.mcp` Modulith module (reads `portfolio` + `analytics` services/named interfaces).
- `@McpTool` (+ `@McpToolParam`) beans exposing at minimum:
  - `get_portfolio_summary` → total market value, P&L, allocation summary (delegates to PortfolioService).
  - `get_risk_metrics` → Sharpe, annualized vol, max drawdown, beta, 95% VaR (delegates to RiskCalculator/analytics).
  - `get_position_detail` → a single holding's detail by ticker (delegates to PortfolioService.getHoldings filtered).
  - These return computed values from the SEEDED data (reuse the existing services — no duplicate math).

### Transport + auth
- Streamable HTTP transport at `/mcp` (Spring AI MCP server, WebMVC). Configure `annotation-scanner.enabled` / the streamable-http server per Spring AI 1.1.6.
- **`/mcp` requires authentication** before any tool is reachable (success criterion 2). Research to pin how Spring AI 1.1.6 MCP carries auth on the streamable-http transport and how the @McpTool establishes a principal:
  - Preferred: Spring Security protects `/mcp/**` (HTTP Basic for a machine client OR the existing session cookie). The tools resolve the portfolio from the authenticated principal (reuse `resolvePortfolioId`).
  - If per-call principal isn't cleanly available over MCP transport, fall back to a **persona/portfolio parameter** on each tool (e.g. `persona: GROWTH|INCOME|BALANCED`) while STILL requiring auth on `/mcp` — auth gates access, the param selects which seeded portfolio. Research decides the cleanest of the two; document the choice.

### Error hygiene
- MCP error responses must be clean — **never** Java stack traces or internal messages. Map exceptions to MCP error objects with safe messages (no leak of keys/internals). A test asserts an error response contains no stack-trace markers.

### Config + docs
- Committed `.mcp.json` (project root or documented) pointing an MCP client at `http://localhost:8080/mcp` with the required auth (e.g. HTTP Basic with a demo user, or the documented session approach). This is separate from the Phase-1 dev `.mcp.json` (context7/postgres) — this one is the PRODUCT MCP server (`quantlens`); keep both coherent.
- README (MCP-02): how any MCP client connects to `/mcp`, the auth needed, and the tool catalog (names, params, what each returns). Mention Claude Code specifically.

### Testing
- Tool-method tests: each @McpTool method returns the correct computed value from seeded data (delegate-and-verify against the existing services; e.g. get_risk_metrics matches RiskCalculator golden values; get_portfolio_summary totals match PortfolioService). Auth: GET/POST to `/mcp` without auth → 401 (no tool reachable). Error hygiene: a forced tool error returns an MCP error with NO stack-trace text. If a full MCP-protocol integration test is feasible in Spring AI 1.1.6 (a test MCP client calling list-tools + call-tool), include one; otherwise test the tool beans + the security + an HTTP-level /mcp auth test. No external network.

### Claude's Discretion
- Exact MCP starter artifact + transport config, the auth approach (principal vs persona-param) per research, the precise tool result shapes, and whether `.mcp.json` lives at root or in docs — at Claude's discretion within the above. Research to confirm Spring AI 1.1.6 @McpTool + streamable-http + MCP Security.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 2 PortfolioService (holdings/P&L/allocation + resolvePortfolioId), Phase 4 RiskCalculator/analytics (Sharpe/VaR/beta/etc., golden-tested), AnalyticsController principal pattern, Spring Security session auth, Modulith named interfaces, AbstractPostgresIntegrationTest. Phase 1 dev `.mcp.json` (context7 + postgres) — the product MCP server config is distinct.
- Seeded deterministic data (seed=42) for stable tool responses.

### Established Patterns
- Principal-scoped readOnly services; Modulith boundaries + named interfaces; golden-value tests; clean error mapping (Phase 6 GlobalAiExceptionHandler — extend the no-leak principle to MCP); Spring Security config; idempotent/deterministic seeded data.

### Integration Points
- New `com.quantlens.mcp` module delegates to portfolio + analytics services. Spring Security extended to protect `/mcp/**`. `.mcp.json` + README for clients. No frontend (MCP is a server-to-server API).
</code_context>

<specifics>
## Specific Ideas

- This is the resume headline ("built an MCP server" — freshest 2026 signal). Make the tools genuinely useful (real computed analytics) and the docs clear enough that anyone can connect Claude Code and query the live portfolio.
- Reuse the existing golden-tested services — do NOT reimplement the math in the tools.
- Keep error responses clean (no stack traces) and `/mcp` authenticated.
</specifics>

<deferred>
## Deferred Ideas

- @McpResource exposure of filings/documents (v2 — MCP-03).
- MCP Security with full OAuth/enterprise auth (basic/session is sufficient for the demo; note the upgrade path).
- Additional tools beyond the three (v2).
- Streaming/notifications over MCP (v2).
</deferred>
