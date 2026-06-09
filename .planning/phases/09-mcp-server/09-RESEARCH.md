# Phase 9: MCP Server — Research

**Researched:** 2026-06-10
**Domain:** Spring AI 1.1.6 `@McpTool` / Streamable-HTTP MCP server, Spring Security integration, error hygiene, `.mcp.json` client config
**Confidence:** HIGH (Maven artifact IDs and yml keys verified against official Spring AI docs; SecurityContext propagation confirmed via official Spring blog + community sources)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Add the Spring AI MCP-server starter (BOM 1.1.6 — research confirms exact artifact). New `com.quantlens.mcp` Modulith module reading `portfolio` + `analytics` services/named interfaces.
- `@McpTool` (+ `@McpToolParam`) beans exposing: `get_portfolio_summary`, `get_risk_metrics`, `get_position_detail`.
- Streamable HTTP transport at `/mcp` (Spring AI MCP server, WebMVC).
- `/mcp` requires authentication (success criterion 2). Research decides principal-vs-persona-param approach.
- MCP error responses must be clean — never Java stack traces or internal messages.
- Committed `.mcp.json` pointing at `http://localhost:8080/mcp` with required auth. Separate from Phase-1 dev `.mcp.json`.
- Tests: tool-method correctness, `/mcp` auth → 401, error hygiene → no stack trace.

### Claude's Discretion
- Exact MCP starter artifact + transport config (research confirmed `spring-ai-starter-mcp-server-webmvc`).
- The auth approach (principal vs persona-param) — research recommends: principal via `SecurityContextHolder` (see §Auth below).
- Precise tool result shapes (concrete Java records — required for schema gen).
- Whether `.mcp.json` lives at root or in docs (root preferred for Claude Code discoverability).

### Deferred Ideas (OUT OF SCOPE)
- `@McpResource` exposure of filings/documents (MCP-03).
- MCP Security with full OAuth/enterprise auth.
- Additional tools beyond the three.
- Streaming/notifications over MCP.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| MCP-01 | The app exposes portfolio analytics as an MCP server via `@McpTool` (`get_portfolio_summary`, `get_risk_metrics`, `get_position_detail`) | Starter artifact, annotation pattern, and principal-access pattern confirmed |
| MCP-02 | Documentation shows how to connect an MCP client (e.g. Claude Code) to the server | `.mcp.json` shape with auth headers confirmed via Claude Code docs |
</phase_requirements>

---

## Summary

Spring AI 1.1.6 ships a `spring-ai-starter-mcp-server-webmvc` starter that embeds a Streamable-HTTP MCP server into the existing Spring Boot application on the same port (8080). Tools are declared by annotating `@Component` beans with `@McpTool` and `@McpToolParam` from `org.springframework.ai.mcp.annotation`; the annotation scanner auto-registers them at boot — no explicit bean registration code needed.

The critical auth question: **`SecurityContextHolder.getContext().getAuthentication()` is available inside `@McpTool` method bodies when the request arrives over Streamable HTTP through Spring Security's filter chain.** The MCP handler runs inside the servlet pipeline, so the same filter that populates the `SecurityContext` for REST controllers also populates it for MCP calls. This enables the tools to call `resolvePortfolioId(authentication.getName())` — the same pattern already used in `PortfolioController` and `AnalyticsController` — giving per-user portfolio scoping without a persona parameter. HTTP Basic auth is the appropriate machine-client auth for this demo; the `/mcp` endpoint must be exempt from CSRF (MCP POSTs cannot carry a cookie-based CSRF token) and must be added to `SecurityConfig` with explicit HTTP Basic support.

Error hygiene: when `@McpTool` methods throw, Spring AI MCP propagates the exception message verbatim to the MCP client. Wrapping tool bodies in try/catch and returning a safe `CallToolResult` with `isError=true` is the portable solution — it works without any external community library and matches the pattern of `GlobalAiExceptionHandler` already present in the codebase.

**Primary recommendation:** `spring-ai-starter-mcp-server-webmvc`, `@McpTool` on a `@Component` bean in `com.quantlens.mcp`, `SecurityContextHolder` for principal, `/mcp` exempt from CSRF + HTTP Basic on `/mcp/**` in `SecurityConfig`, tool-level try/catch returning `CallToolResult.error(safeMessage)`.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| MCP protocol handling (list-tools, call-tool) | API / Backend — embedded in Spring Boot | — | Spring AI starter handles the protocol inside the servlet container |
| Tool execution (portfolio summary, risk, position detail) | API / Backend — `mcp` module | delegates to `portfolio` + `analytics` modules | No recompute; thin protocol-adapter calling existing services |
| Authentication gate on `/mcp` | API / Backend — Spring Security filter chain | — | Same filter chain that protects `/api/**`; just extended to `/mcp/**` |
| Principal → portfolioId resolution inside tools | API / Backend — `mcp` module (copies controller pattern) | `portfolio` module PortfolioRepository | Same correlated-subquery as REST controllers |
| `.mcp.json` client config | Repository root (static artifact) | — | Consumed by Claude Code at load time from project root |

---

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `spring-ai-starter-mcp-server-webmvc` | 1.1.6 (from BOM) | Embeds Streamable-HTTP MCP server in Spring MVC app | Official Spring AI starter; WebMVC variant coexists with existing Spring Boot web app on port 8080 |
| `spring-ai-bom` | 1.1.6 (already in pom.xml) | Pins all `spring-ai-*` artifact versions | Already present; no version change |
| `org.springframework.ai.mcp.annotation.McpTool` | 1.1.6 | Marks a bean method as an MCP tool | Part of the WebMVC starter transitive graph |
| `org.springframework.ai.mcp.annotation.McpToolParam` | 1.1.6 | Annotates method parameters with description + required flag | Part of the WebMVC starter |

### Not Needed

The community `spring-ai-community/mcp-security` library (versions `0.1.x`) only targets Spring AI 2.0.x milestones. The `0.0.6` version targets 1.1.x but adds OAuth2 machinery that is overkill for HTTP Basic. **Do not add `mcp-security` as a dependency.** HTTP Basic + Spring Security's existing filter chain is sufficient and already present.

### Maven Addition

```xml
<!-- Phase 9: MCP Server (Streamable-HTTP, WebMVC, same port as app) -->
<!-- BOM 1.1.6 already imported above — no version needed here -->
<!-- VERIFIED: docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html -->
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-mcp-server-webmvc</artifactId>
</dependency>
```

**Version verification:** The BOM `spring-ai-bom:1.1.6` (already in `pom.xml`) pins all `spring-ai-*` artifacts. No explicit version element needed. [VERIFIED: docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html]

---

## Package Legitimacy Audit

Only one new Maven artifact; part of the official `org.springframework.ai` group already present in the project at versions 1.1.6.

| Package | Registry | Org | slopcheck | Disposition |
|---------|----------|-----|-----------|-------------|
| `spring-ai-starter-mcp-server-webmvc` | Maven Central | `org.springframework.ai` (official Spring) | N/A — same org as existing starters | Approved |

No packages removed. No packages flagged. The artifact is in the same `groupId` as `spring-ai-starter-model-anthropic` and `spring-ai-starter-vector-store-pgvector` already present and legitimate in `pom.xml`. [VERIFIED: docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html]

---

## Architecture Patterns

### System Architecture Diagram

```
Claude Code (MCP client)
        │  HTTP POST /mcp  (Authorization: Basic alice:demo1234)
        ▼
┌───────────────────────────────────────────────────────────────┐
│  Spring Boot (port 8080)                                       │
│                                                               │
│  Spring Security Filter Chain                                  │
│   ├── CSRF filter  (/mcp exempt — machine client)             │
│   └── BasicAuthenticationFilter  (/mcp/** → populates         │
│        SecurityContextHolder)                                  │
│              │  (if 401 → return 401 JSON, stop)              │
│              ▼                                                 │
│  McpWebMvcServerController  (registered at /mcp by starter)   │
│    handles: initialize / list-tools / call-tool               │
│              │                                                 │
│              ▼                                                 │
│  PortfolioMcpTools  (@Component, @McpTool methods)            │
│   ├── get_portfolio_summary(persona?) → SecurityContextHolder  │
│   │     │ authentication.getName() → resolvePortfolioId()     │
│   │     └─→ portfolioService.getHoldings() + getAllocation()   │
│   │                                                           │
│   ├── get_risk_metrics(persona?) → same auth pattern          │
│   │     └─→ riskCalculator.computeRiskScorecard()             │
│   │                                                           │
│   └── get_position_detail(ticker) → same auth pattern         │
│         └─→ portfolioService.getHoldings() filtered by ticker │
│                                                               │
│  (all tool exceptions caught → CallToolResult with isError)   │
└───────────────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
backend/src/main/java/com/quantlens/
└── mcp/                              # Spring Modulith module
    ├── package-info.java             # @ApplicationModule(allowedDependencies = {"portfolio::api", "analytics::api"})
    └── tools/
        ├── PortfolioMcpTools.java    # @Component with @McpTool methods
        └── McpToolResponse*.java     # concrete result records (see below)
```

Result records are separate from the REST DTOs to keep the Modulith dependency clean — the MCP module may only use named interfaces, not internal service-layer types.

### Pattern 1: @McpTool Bean Declaration

```java
// Source: docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html
// Package confirmed: org.springframework.ai.mcp.annotation
package com.quantlens.mcp.tools;

import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

@Component
public class PortfolioMcpTools {

    @McpTool(
        name = "get_portfolio_summary",
        description = "Returns the authenticated user's portfolio: total market value, " +
                      "total unrealized P&L, daily change, and sector allocation weights."
    )
    public PortfolioSummaryResult getPortfolioSummary() {
        try {
            Long portfolioId = resolvePortfolioId();
            // delegate to portfolioService ...
            return new PortfolioSummaryResult(...);
        } catch (PortfolioNotFoundException e) {
            // Return safe MCP error — no stack trace
            return new PortfolioSummaryResult("error", "Portfolio not found for authenticated user");
        } catch (Exception e) {
            log.error("get_portfolio_summary failed (not forwarded to client)", e);
            return new PortfolioSummaryResult("error", "Portfolio summary unavailable");
        }
    }

    @McpTool(
        name = "get_risk_metrics",
        description = "Returns risk metrics for the authenticated user's portfolio: " +
                      "annualized Sharpe ratio, annualized volatility, max drawdown, " +
                      "beta vs SPX500, historical VaR (95%, 1-day), parametric VaR (95%, 1-day)."
    )
    public RiskMetricsResult getRiskMetrics() { ... }

    @McpTool(
        name = "get_position_detail",
        description = "Returns a single holding's detail by ticker symbol for the authenticated user."
    )
    public PositionDetailResult getPositionDetail(
        @McpToolParam(description = "Ticker symbol, e.g. AAPL", required = true)
        String ticker
    ) { ... }
}
```

**Key rules:**
- Method names can be anything; the `name` attribute in `@McpTool` is the MCP protocol identifier (use `snake_case` to match convention).
- No parameters are needed to supply the portfolio identity — the principal comes from `SecurityContextHolder`.
- Return types MUST be concrete records or POJOs. Do NOT return `List<SomeInterface>` or `Object`.

### Pattern 2: Principal Resolution in Tool Methods

```java
// Reuses the PortfolioRepository.findPortfolioIdByUsername correlated-subquery
// exactly as PortfolioController.resolvePortfolioId does.
// Source: existing codebase pattern (AnalyticsController.java:141–148)
private Long resolvePortfolioId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
        throw new McpAuthException("Authentication required");
    }
    return portfolioRepository.findPortfolioIdByUsername(auth.getName())
        .orElseThrow(() -> new McpAuthException("Portfolio not found"));
}
```

**Why `SecurityContextHolder` works:** The Spring AI WebMVC MCP server registers a standard `@RequestMapping` handler on `/mcp`. Spring Security's `BasicAuthenticationFilter` runs before the dispatcher servlet, populates the `SecurityContext` on the current thread, and clears it after the response. The MCP handler and any `@McpTool` invocations it dispatches run on that same thread, so the context is present. [VERIFIED: spring.io/blog/2025/09/30/spring-ai-mcp-server-security/ — "the tool will look up the name of the user from the SecurityContext..."]

### Pattern 3: Concrete Result Records

```java
// All MCP tool return types MUST be concrete records for reliable JSON schema generation.
// Source: docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html

public record PortfolioSummaryResult(
    BigDecimal totalMarketValue,
    BigDecimal totalCostBasis,
    BigDecimal totalUnrealizedGainAbs,
    BigDecimal totalUnrealizedGainPct,
    BigDecimal dailyChangeAbs,
    BigDecimal dailyChangePct,
    List<AllocationEntry> allocation   // concrete record — no interface
) {}

public record AllocationEntry(String sector, BigDecimal weight, BigDecimal marketValue) {}

public record RiskMetricsResult(
    double sharpeRatio,
    double annualizedVolatility,
    double maxDrawdown,
    double beta,
    BigDecimal historicalVar95,        // positive loss amount
    BigDecimal parametricVar95
) {}

public record PositionDetailResult(
    String ticker,
    String name,
    String sector,
    BigDecimal quantity,
    BigDecimal avgCostBasis,
    BigDecimal currentPrice,
    BigDecimal currentMarketValue,
    BigDecimal portfolioWeight,
    BigDecimal unrealizedPnlAbs,
    BigDecimal unrealizedPnlPct
) {}
```

### Pattern 4: Error Result Without Stack Traces

```java
// Approach: Catch all exceptions in each tool method body.
// Return a result record that the planner must design to include an optional error field,
// OR use Spring AI's CallToolResult with isError=true.
// The CallToolResult approach is cleaner — it maps directly to the MCP protocol error shape.
// Source: Spring AI MCP SDK (io.modelcontextprotocol.sdk.McpSchema.CallToolResult)

import io.modelcontextprotocol.sdk.McpSchema;

@McpTool(name = "get_portfolio_summary", ...)
public McpSchema.CallToolResult getPortfolioSummary() {
    try {
        Long portfolioId = resolvePortfolioId();
        PortfolioSummaryResult result = buildSummary(portfolioId);
        // Serialize to JSON string for the text content
        String json = objectMapper.writeValueAsString(result);
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(json)))
            .isError(false)
            .build();
    } catch (McpAuthException e) {
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent(e.getMessage())))
            .isError(true)
            .build();
    } catch (Exception e) {
        log.error("get_portfolio_summary failed (detail not forwarded)", e);
        return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Portfolio summary unavailable")))
            .isError(true)
            .build();
    }
}
```

**Alternative (simpler):** Return a concrete result record that includes a `String status` field (`"ok"` or `"error"`) and a `String errorMessage` field (null on success). The framework serializes it as JSON — no stack trace can leak. This is simpler but less aligned with the MCP protocol spec (`isError`). The `CallToolResult` approach is preferred. [ASSUMED — exact SDK class availability in the 1.1.6 BOM's bundled MCP SDK should be compile-verified]

### Pattern 5: Security Configuration Extension

```java
// Add to SecurityConfig.filterChain — two changes required:
// 1. Exempt /mcp from CSRF (machine client cannot use the XSRF-TOKEN cookie)
// 2. Enable HTTP Basic for /mcp/** requests (MCP client sends Authorization: Basic header)
// Source: standard Spring Security patterns + official MCP security blog

.csrf(csrf -> csrf
    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
    .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout")
    .ignoringRequestMatchers(
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/key"),
        AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/api/ai/chat"),
        // Phase 9: MCP POST exempt — machine client cannot carry XSRF-TOKEN cookie
        AntPathRequestMatcher.antMatcher("/mcp")
    )
)
.httpBasic(basic -> basic
    // HTTP Basic is needed for MCP client authentication on /mcp.
    // It is harmless on other endpoints — the Vue SPA uses form-login/session,
    // not HTTP Basic, so there is no conflict.
    .realmName("QuantLens MCP")
)
```

**CSRF rationale:** The MCP client (Claude Code) makes HTTP POST requests to `/mcp`. It cannot read the `XSRF-TOKEN` cookie and replay it as an `X-XSRF-TOKEN` header. Without exemption, all MCP calls return 403. The exemption is safe because the endpoint is protected by HTTP Basic authentication — the attacker cannot forge a CSRF request without knowing the credentials.

**HTTP Basic + session coexistence:** Spring Security allows multiple authentication mechanisms in one filter chain. Adding `.httpBasic(...)` does not remove form login. The Vue SPA continues to use session-cookie auth on `/api/**`. The MCP client sends `Authorization: Basic` on `/mcp`. Both work in the same application.

### Anti-Patterns to Avoid

- **Using `@Tool` instead of `@McpTool`:** `@Tool` (package `org.springframework.ai.tool.annotation`) is the LLM tool-calling annotation used with `ChatClient`. `@McpTool` is the MCP server-side annotation. They are completely different subsystems. Do not confuse them.
- **Returning `Object` or `List<SomeInterface>` from tools:** The MCP annotation scanner generates a JSON schema from the method's return type. Polymorphic or `Object` return types produce a vague schema that clients cannot reliably parse.
- **Depending on `spring-ai-community/mcp-security:0.1.x`:** Version 0.1.x targets Spring AI 2.0.x milestones. It will not compile against 1.1.6. The 0.0.6 version targets 1.1.x but adds unnecessary OAuth2 complexity.
- **Using SSE transport:** The SSE transport is deprecated; the Spring AI docs instruct using Streamable HTTP for all new servers.
- **Putting portfolio-resolution logic inside `@McpTool` annotations' metadata:** The annotation scanner reads the method signature for schema; runtime logic (resolvePortfolioId, service calls) goes entirely in the method body.
- **Not exempting `/mcp` from CSRF:** Every MCP POST call will silently return 403, and the MCP client reports "connection failed" with no useful error message.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| MCP protocol (initialize/list-tools/call-tool) | Custom servlet / Spring MVC controller | `spring-ai-starter-mcp-server-webmvc` | Protocol is non-trivial (session IDs, capability negotiation, schema gen); starter handles all of it |
| JSON schema generation for tool parameters | Manual Jackson schema | `@McpToolParam` annotation + starter's scanner | The scanner reads the method signature and generates the MCP-compatible JSON schema automatically |
| Tool registration with MCP server | `McpServer.addTool(...)` manual calls | `annotation-scanner.enabled=true` (default) | The starter auto-discovers all `@Component` beans with `@McpTool` methods at startup |
| MCP error envelope | Custom JSON response | `McpSchema.CallToolResult.builder().isError(true)` | Correct MCP protocol error shape; guaranteed to be handled by conforming clients |
| HTTP Basic credential verification | Custom filter | Spring Security `.httpBasic()` | Battle-tested; integrates with existing `UserDetailsService` + BCrypt |

**Key insight:** The MCP protocol layer is completely abstracted. The tool author writes plain Java methods; the starter handles protocol negotiation, schema exposure, request dispatching, and response marshalling.

---

## Common Pitfalls

### Pitfall 1: CSRF 403 on Every MCP Call
**What goes wrong:** The existing `SecurityConfig` enables `CookieCsrfTokenRepository`. An MCP POST to `/mcp` is rejected with 403 before reaching the MCP handler. The MCP client shows a cryptic "connection refused" or "unexpected response" error.
**Why it happens:** Machine clients (Claude Code) cannot read `HttpOnly` cookies and replay them as `X-XSRF-TOKEN` headers.
**How to avoid:** Add `/mcp` to `.ignoringRequestMatchers(...)` in `SecurityConfig`. The endpoint is still protected by authentication.
**Warning signs:** `curl -X POST http://localhost:8080/mcp -H "Authorization: Basic ..."` returns HTTP 403.

### Pitfall 2: Authentication Not Available in Tool Method Thread
**What goes wrong:** `SecurityContextHolder.getContext().getAuthentication()` returns `null` inside a tool method.
**Why it happens:** Unlikely with `spring-ai-starter-mcp-server-webmvc` because the MCP handler runs in the servlet request thread, but could happen if tool execution is delegated to a different thread pool (e.g., `@Async`).
**How to avoid:** Do NOT annotate tool methods or their services with `@Async`. If async execution is needed later, propagate the security context via `SecurityContextHolder.MODE_INHERITABLETHREADLOCAL` or `DelegatingSecurityContextCallable`. In Phase 9 — no async needed.
**Warning signs:** `NullPointerException` at `authentication.getName()` or `resolvePortfolioId` returning `Optional.empty()` for a known-valid user.

### Pitfall 3: Tool Returns an Interface or `Object`
**What goes wrong:** The annotation scanner cannot generate a useful JSON schema for the tool. MCP clients may fail to parse the response or report schema validation errors.
**Why it happens:** Reusing `HoldingDto` (which is a concrete record) is fine; but returning `List<HoldingDto>` where the list type is inferred as a generic interface reference causes schema generation issues in some Spring AI versions.
**How to avoid:** Define dedicated `*Result` records in `com.quantlens.mcp.tools` for each tool's return type. Keep them concrete, flat, and free of Spring/JPA annotations.
**Warning signs:** MCP client receives empty schema (`{}`) for a tool's output in `list_tools` response.

### Pitfall 4: `@McpTool` on Non-`@Component` Bean
**What goes wrong:** The annotation scanner only discovers beans in the Spring application context. A plain class with `@McpTool` methods that is not annotated `@Component` (or `@Service`, `@Bean`, etc.) is silently ignored.
**How to avoid:** Always annotate the containing class with `@Component`. Verify at startup that the tool count matches expectations by inspecting the `/mcp` panel in Claude Code (`/mcp` command).
**Warning signs:** `list_tools` response returns an empty array `[]`.

### Pitfall 5: `spring-ai-starter-mcp-server` (stdio) Instead of `-webmvc`
**What goes wrong:** Adding the wrong starter (`spring-ai-starter-mcp-server` without the `-webmvc` suffix) configures a stdio transport that is only reachable from a co-located process, not from an HTTP client. The MCP endpoint `/mcp` is never created.
**How to avoid:** The correct artifact for an HTTP-accessible MCP server embedded in a WebMVC application is `spring-ai-starter-mcp-server-webmvc`. [VERIFIED: docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html]
**Warning signs:** No HTTP endpoint at `/mcp` after startup; no `McpWebMvcServerController` in the Spring context.

### Pitfall 6: Exception Message Leaked to MCP Client
**What goes wrong:** An uncaught `RuntimeException` in a tool method causes Spring AI to return the exception message verbatim in the MCP error response. Database errors ("No portfolio found for user X"), stack frames, and internal class names are visible to the MCP client (Claude Code's console).
**Why it happens:** Spring AI MCP does not have a built-in exception mapper equivalent to `@RestControllerAdvice`. The tool framework simply catches `Throwable` and wraps `e.getMessage()` in the error content.
**How to avoid:** Wrap every tool method body in a `try { ... } catch (Exception e)` block. Log the full exception internally (with stack trace) and return a `CallToolResult` with a safe, generic message. Do NOT rethrow.

---

## Code Examples

### Complete application.yml additions

```yaml
# Source: docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html
spring:
  ai:
    mcp:
      server:
        name: quantlens-mcp
        version: 1.0.0
        protocol: STREAMABLE
        type: SYNC
        annotation-scanner:
          enabled: true          # default true; explicit for clarity
        streamable-http:
          mcp-endpoint: /mcp    # default /mcp; explicit for documentation
```

These properties coexist with existing `spring.ai.*` properties already in `application.yml`. No port change — the MCP server shares port 8080.

### Modulith module declaration

```java
// backend/src/main/java/com/quantlens/mcp/package-info.java
@org.springframework.modulith.ApplicationModule(
    allowedDependencies = {
        "portfolio::api",    // PortfolioService, HoldingDto, PortfolioPnlDto, AllocationSliceDto
        "analytics::api",    // RiskCalculator, RiskScorecardDto
        "portfolio::domain"  // PortfolioRepository.findPortfolioIdByUsername
    }
)
package com.quantlens.mcp;
```

### Full SecurityConfig changes (additions only)

```java
// In filterChain(...):
// 1. Add to .csrf(...).ignoringRequestMatchers:
AntPathRequestMatcher.antMatcher("/mcp")

// 2. Add .httpBasic() alongside existing .formLogin():
.httpBasic(basic -> basic.realmName("QuantLens MCP"))
```

### `.mcp.json` product server entry

```json
// Project root .mcp.json — extends the existing Phase-1 entry
// Source: code.claude.com/docs/en/mcp — env var expansion in headers confirmed
{
  "mcpServers": {
    "context7": {
      "command": "npx",
      "args": ["-y", "@upstash/context7-mcp@latest"],
      "env": {}
    },
    "project-db": {
      "command": "npx",
      "args": ["-y", "@henkey/postgres-mcp-server"],
      "env": {
        "POSTGRES_CONNECTION_STRING": "postgresql://quantlens:quantlens_dev@localhost:5432/quantlens"
      }
    },
    "quantlens": {
      "type": "http",
      "url": "http://localhost:8080/mcp",
      "headers": {
        "Authorization": "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}"
      }
    }
  }
}
```

**Notes on the `quantlens` entry:**
- `type: "http"` is the correct Claude Code keyword; `"streamable-http"` is an alias that also works. [VERIFIED: code.claude.com/docs/en/mcp — "the `type` field accepts `streamable-http` as an alias for `http`"]
- `Authorization: Basic <token>` where the token is `base64(username:password)`. The default value `YWxpY2U6ZGVtbzEyMzQ=` decodes to `alice:demo1234`, matching the seeded demo user.
- Use `${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}` so any developer can override via an environment variable without modifying the committed file.
- The Base64 token is NOT a secret credential — it is the same `demo1234` password displayed on the login screen. It is safe to commit.
- Claude Code environment variable expansion in `.mcp.json` headers is confirmed supported. [VERIFIED: code.claude.com/docs/en/mcp]

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `spring-ai-mcp-server-boot-starter` (pre-1.0 naming) | `spring-ai-starter-mcp-server-webmvc` | Spring AI 1.0 GA (June 2025) | Artifact ID rename; old ID no longer resolves |
| SSE transport for HTTP MCP servers | Streamable HTTP transport | MCP spec 2025 | SSE deprecated; use `protocol: STREAMABLE` |
| Manual `McpServer.addTool()` registration | `@McpTool` + `annotation-scanner.enabled=true` | Spring AI 1.0 GA | Declarative approach; no registration code needed |
| `PromptChatMemoryAdvisor` (unrelated — note for Phase 8 compatibility) | `MessageChatMemoryAdvisor` | Spring AI 1.1.6 | Already correctly handled in Phase 8 |

**Deprecated/outdated:**
- `spring-ai-mcp-server-boot-starter` (without `-webmvc`): The bare artifact is for stdio transport only. Any SSE-based tutorial is outdated.
- `McpApiKeyConfigurer` from `spring-ai-community/mcp-security:0.0.6`: Only targets 1.1.x but requires an additional community dependency. HTTP Basic from `spring-boot-starter-security` (already present) is simpler and sufficient.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | `McpSchema.CallToolResult` class is available in the MCP SDK bundled with `spring-ai-starter-mcp-server-webmvc:1.1.6` and its builder API matches the pattern shown | Code Examples / Pattern 4 | If class name or builder differs, use the concrete result-record approach (status field) instead |
| A2 | `org.springframework.ai.mcp.annotation.McpTool` is the correct package path in 1.1.6 | Standard Stack | If the package differs (e.g., relocated to `org.springframework.ai.tool.mcp`), update imports at compile time |
| A3 | `SecurityContextHolder.getContext().getAuthentication()` is non-null inside `@McpTool` methods invoked over Streamable HTTP via the WebMVC dispatcher | Pattern 2 | If null, fall back to adding a `persona` parameter to each tool (GROWTH/INCOME/BALANCED → resolves portfolioId from a seeded mapping) |

---

## Open Questions (RESOLVED — gated at Wave 0 compile; see 09-01 tasks)

1. **`McpSchema.CallToolResult` exact class path in 1.1.6 BOM**
   - What we know: The Spring AI MCP module bundles the MCP Java SDK. The SDK has a `CallToolResult` type.
   - What's unclear: Whether it is `io.modelcontextprotocol.sdk.McpSchema.CallToolResult` or a Spring AI wrapper class.
   - Recommendation: At compile time, inspect the starter's transitive dependencies (`./mvnw dependency:tree`) and check the actual SDK class. If not found, use the concrete result-record fallback.

2. **`annotation-scanner.enabled` default in 1.1.6**
   - What we know: Documentation states the default is `true`.
   - What's unclear: Whether a misconfigured Spring context (e.g., missing `@ApplicationModule` boundaries) could prevent scanner from finding beans in `com.quantlens.mcp`.
   - Recommendation: Set `annotation-scanner.enabled: true` explicitly in `application.yml` (already in the config example above). Verify on first build with `/mcp` in Claude Code.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | Backend build | Yes (CLAUDE.md: Temurin 21.0.11) | 21.0.11 | — |
| Maven wrapper | Build | Yes (`./mvnw.cmd` in project) | Managed | — |
| `spring-ai-starter-mcp-server-webmvc` | MCP server | Via BOM (fetch on build) | 1.1.6 | — |
| `spring-boot-starter-security` | HTTP Basic auth | Already in pom.xml | 3.5.13 | — |
| Claude Code (MCP client for testing) | `.mcp.json` verification | Available (dev machine) | Latest | Manual `curl` tests |

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (already in project) |
| Config file | `src/test/java/...` (existing pattern) |
| Quick run command | `.\mvnw.cmd test -pl backend -Dtest=PortfolioMcpToolsTest -Dspring.profiles.active=test` |
| Full suite command | `.\mvnw.cmd test -pl backend` |

### Phase Requirements to Test Map

| Req ID | Behavior | Test Type | Automated Command | Exists? |
|--------|----------|-----------|-------------------|---------|
| MCP-01a | `get_portfolio_summary` returns correct total market value matching PortfolioService | Unit (service-level) | `mvnw test -Dtest=PortfolioMcpToolsTest#getPortfolioSummary_returnsCorrectTotalValue` | Wave 0 |
| MCP-01b | `get_risk_metrics` returns Sharpe/VaR values matching RiskCalculator golden values | Unit (service-level) | `mvnw test -Dtest=PortfolioMcpToolsTest#getRiskMetrics_matchesGoldenValues` | Wave 0 |
| MCP-01c | `get_position_detail` for "AAPL" returns correct holding details | Unit (service-level) | `mvnw test -Dtest=PortfolioMcpToolsTest#getPositionDetail_aapl_returnsCorrectHolding` | Wave 0 |
| MCP-01d | POST to `/mcp` without auth returns 401 | Integration (HTTP-level) | `mvnw test -Dtest=McpAuthIntegrationTest#mcp_withoutAuth_returns401` | Wave 0 |
| MCP-01e | Forced tool exception produces no stack trace in response | Unit (error hygiene) | `mvnw test -Dtest=PortfolioMcpToolsTest#toolException_doesNotLeakStackTrace` | Wave 0 |
| MCP-02 | `.mcp.json` `quantlens` entry connects Claude Code to live MCP server | Manual smoke test | `claude mcp get quantlens` then `/mcp` in Claude Code session | Manual |

### Test Strategy Details

**Tool-correctness tests (MCP-01a, b, c):** Call the `@Component` bean's methods directly (no MCP protocol needed). Inject the bean with `@SpringBootTest` + Testcontainers (reusing `AbstractPostgresIntegrationTest` pattern), set up a mock `SecurityContext` with an authenticated `alice` principal, and assert returned record fields against known seeded values. Do NOT go through the HTTP layer for these — calling the bean directly is simpler and equally proves correctness.

```java
// Pattern for tool-correctness test
@SpringBootTest
@Transactional(readOnly = true)
class PortfolioMcpToolsTest extends AbstractPostgresIntegrationTest {

    @Autowired PortfolioMcpTools tools;

    @BeforeEach
    void setupSecurity() {
        // Populate SecurityContextHolder as HTTP Basic would
        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken("alice", null, List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void clearSecurity() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getPortfolioSummary_returnsCorrectTotalValue() {
        var result = tools.getPortfolioSummary(); // or unwrap CallToolResult
        assertThat(result).isNotNull();
        // assert totalMarketValue matches PortfolioService.getPortfolioPnl for alice
    }
}
```

**Auth test (MCP-01d):** Use `TestRestTemplate` (no credentials) to POST to `http://localhost:{port}/mcp`. Expect 401. Does NOT require MCP protocol — the 401 fires before the MCP handler.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class McpAuthIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired TestRestTemplate restTemplate;

    @Test
    void mcp_withoutAuth_returns401() {
        ResponseEntity<String> response = restTemplate.postForEntity("/mcp", "{}", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
```

**Error-hygiene test (MCP-01e):** Inject a mock `PortfolioRepository` that throws `RuntimeException("INTERNAL_DB_ERROR_secret")`. Call the tool method. Assert that the returned result's content does NOT contain "INTERNAL_DB_ERROR_secret", "NullPointerException", or any class name pattern (`com.quantlens.*`, `java.lang.*`).

```java
@Test
void toolException_doesNotLeakStackTrace() {
    // Arrange: PortfolioRepository throws RuntimeException
    // (via mock or spying on the tool with a broken dependency)
    var result = tools.getPortfolioSummaryWithBrokenRepo(); // CallToolResult
    assertThat(result.isError()).isTrue();
    String content = extractTextContent(result);
    assertThat(content).doesNotContain("RuntimeException");
    assertThat(content).doesNotContain("com.quantlens");
    assertThat(content).doesNotContain("INTERNAL_DB_ERROR_secret");
}
```

**MCP-protocol integration test (feasibility):** A full in-process MCP client test (list-tools → call-tool) is feasible in Spring AI 1.1.6 using `McpSyncClient` or `McpClient` from the bundled MCP Java SDK, pointed at the server's HTTP endpoint. However, this adds significant test complexity (client setup, protocol negotiation) for limited additional coverage over the service-level tests. **Recommendation:** Skip the full protocol test in Phase 9 scope. The layered approach (service-level tool tests + HTTP auth test + error hygiene test) covers all requirements. Note the MCP-protocol integration test as a Wave 2 enhancement.

### Sampling Rate

- Per task commit: `.\mvnw.cmd test -pl backend -Dtest=PortfolioMcpToolsTest`
- Per wave merge: `.\mvnw.cmd test -pl backend`
- Phase gate: Full suite green before `/gsd:verify-work`

### Wave 0 Gaps

- [ ] `McpAuthIntegrationTest.java` — covers MCP-01d
- [ ] `PortfolioMcpToolsTest.java` — covers MCP-01a, b, c, e
- [ ] `com.quantlens.mcp.tools.PortfolioMcpTools.java` — the bean itself
- [ ] Result records: `PortfolioSummaryResult.java`, `RiskMetricsResult.java`, `PositionDetailResult.java`
- [ ] `com.quantlens.mcp.package-info.java` — Modulith boundary declaration

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | HTTP Basic via Spring Security `DaoAuthenticationProvider` (existing `UserDetailsService`) |
| V3 Session Management | no — MCP is stateless HTTP Basic | N/A; no session created for MCP requests |
| V4 Access Control | yes | Principal-scoped portfolio resolution (same IDOR prevention as REST controllers) |
| V5 Input Validation | yes (ticker parameter) | `@McpToolParam` + tool-level null/blank guard on `ticker`; delegate to existing service validation |
| V6 Cryptography | no | N/A — no new crypto primitives |

### Known Threat Patterns for Spring AI MCP + Spring Security

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Unauthenticated MCP access | Elevation of privilege | Require auth on `/mcp/**` (HTTP Basic gate) |
| IDOR via forged principal | Elevation of privilege | Derive portfolioId from authenticated principal only; no user/portfolio ID in tool params |
| Stack trace leak in MCP error | Information disclosure | Tool-level try/catch; never include exception message or class names in `CallToolResult` error content |
| CSRF attack on `/mcp` | Tampering | Exempt `/mcp` from CSRF only because HTTP Basic is required (no session-based CSRF surface) |
| Tool parameter injection (ticker) | Tampering | Sanitize: uppercase, alphanumeric + `.` only; max 10 chars; delegate to existing `getHoldings()` which does the DB filtering |

---

## Sources

### Primary (HIGH confidence)
- [Spring AI MCP Server Boot Starters](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-server-boot-starter-docs.html) — artifact IDs (`spring-ai-starter-mcp-server-webmvc`, `spring-ai-starter-mcp-server`), transport configuration
- [Spring AI Streamable HTTP Server Starter](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-streamable-http-server-boot-starter-docs.html) — all `spring.ai.mcp.server.*` properties including `streamable-http.mcp-endpoint`, `name`, `version`, `protocol: STREAMABLE`
- [Spring AI MCP Server Annotations](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html) — `@McpTool`, `@McpToolParam`, `org.springframework.ai.mcp.annotation` package, annotation-scanner, return type serialization
- [Claude Code MCP Documentation](https://code.claude.com/docs/en/mcp) — `.mcp.json` format with `type: "http"`, `url`, `headers`, `Authorization: Basic` pattern, `${VAR:-default}` expansion in headers
- [Securing MCP Servers with Spring AI (spring.io blog)](https://spring.io/blog/2025/09/30/spring-ai-mcp-server-security/) — SecurityContextHolder availability in `@McpTool` methods, OAuth2 vs API key approach

### Secondary (MEDIUM confidence)
- [Spring AI MCP Security Recipe (Craig Walls, Medium)](https://thetalkingapp.medium.com/spring-ai-recipe-securing-an-mcp-server-with-an-api-key-0a4b84fdf0dc) — API key auth pattern for MCP, confirms security is purely Spring Security
- [Securing MCP Servers with API Key (imhoratiu.wordpress.com)](https://imhoratiu.wordpress.com/2025/12/01/how-to-secure-a-spring-ai-mcp-server-with-an-api-key-via-spring-security/) — confirms standard Spring Security approach works for `/mcp`
- [Spring AI MCP Issue #2857](https://github.com/spring-projects/spring-ai/issues/2857) — exception message verbatim leak confirmed; tool-level catch is the workaround
- Existing codebase: `AnalyticsController.java:141–148` — `resolvePortfolioId(Authentication)` pattern reused verbatim

### Tertiary (LOW — training knowledge, not freshly verified in this session)
- `McpSchema.CallToolResult` exact class path in 1.1.6 SDK — needs compile-time verification

---

## Metadata

**Confidence breakdown:**
- Standard stack (artifact ID, yml properties): HIGH — verified against official Spring AI docs
- `@McpTool` annotation + auto-registration: HIGH — verified against official Spring AI annotation docs
- `SecurityContextHolder` availability in tools: HIGH — confirmed via official Spring blog
- `.mcp.json` format with Basic auth headers: HIGH — confirmed via official Claude Code docs
- `CallToolResult` API: MEDIUM — class existence confirmed by issue references; exact builder API is ASSUMED
- Testing strategy: HIGH — follows existing `AbstractPostgresIntegrationTest` pattern in the project

**Research date:** 2026-06-10
**Valid until:** 2026-08-10 (Spring AI 1.1.x is stable; unlikely to change before 2.0 GA)
