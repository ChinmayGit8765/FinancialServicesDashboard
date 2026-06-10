---
phase: 09-mcp-server
reviewed: 2026-06-10T00:00:00Z
depth: standard
files_reviewed: 12
files_reviewed_list:
  - backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java
  - backend/src/main/java/com/quantlens/mcp/tools/PortfolioSummaryResult.java
  - backend/src/main/java/com/quantlens/mcp/tools/RiskMetricsResult.java
  - backend/src/main/java/com/quantlens/mcp/tools/PositionDetailResult.java
  - backend/src/main/java/com/quantlens/security/config/SecurityConfig.java
  - backend/src/main/resources/application.yml
  - backend/pom.xml
  - backend/src/main/java/com/quantlens/mcp/package-info.java
  - backend/src/test/java/com/quantlens/mcp/PortfolioMcpToolsTest.java
  - backend/src/test/java/com/quantlens/mcp/McpAuthIntegrationTest.java
  - .mcp.json
  - README.md
findings:
  critical: 2
  warning: 4
  info: 2
  total: 8
status: resolved
resolution: "6 fixed, CR-01 rejected (false positive — Claude Code DOES expand ${VAR:-default} in .mcp.json headers), WR-04 deferred (established pattern). Suite 215 green."
---

# Phase 09: Code Review Report

**Reviewed:** 2026-06-10
**Depth:** standard
**Files Reviewed:** 12
**Status:** issues_found

## Summary

The MCP server slice is structurally sound: auth gating works, principal resolution is
IDOR-safe, error hygiene is correctly implemented, and the thin-adapter pattern is
respected. Two Critical issues are present: the `.mcp.json` override mechanism silently
does not work (shell substitution syntax in a JSON file), and `resolvePortfolioId()` does
not reject Spring Security's `AnonymousAuthenticationToken`. Both are reachable in the
deployed artifact. Four Warnings cover the CSRF exemption scope, null VaR values silently
reaching the LLM, test coverage gaps on error-hygiene paths, and direct repository access
bypassing the service layer.

---

## Critical Issues

### CR-01: `.mcp.json` credential override is non-functional — hardcoded demo credential always used

**File:** `.mcp.json:20`

**Issue:** The `Authorization` header value uses shell parameter-expansion syntax:

```json
"Authorization": "Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}"
```

Claude Code (and every conforming MCP client) reads `.mcp.json` as a plain JSON string
and sends that literal string as the header — it does NOT perform shell variable
substitution in JSON header values. The `${QUANTLENS_MCP_AUTH:-...}` syntax is only
expanded by a POSIX shell (bash, sh). As a result:

1. The `QUANTLENS_MCP_AUTH` environment variable override is completely ignored.
2. The literal string `Basic ${QUANTLENS_MCP_AUTH:-YWxpY2U6ZGVtbzEyMzQ=}` is sent as
   the Authorization header value, which is NOT valid HTTP Basic authentication.
3. Every MCP tool invocation through `.mcp.json` will fail with 401 — the MCP server
   is functionally broken for the developer workflow the README documents.

**Fix:**

Option A — static demo credential (simplest, acceptable because it is the publicly
documented demo password):
```json
"Authorization": "Basic YWxpY2U6ZGVtbzEyMzQ="
```

Option B — use the `env` field for the variable and compose in a wrapper script:
```json
"quantlens": {
  "type": "http",
  "url": "http://localhost:8080/mcp",
  "headers": {
    "Authorization": "Basic YWxpY2U6ZGVtbzEyMzQ="
  },
  "_comment": "Override: set QUANTLENS_MCP_AUTH env var and regenerate this file for non-demo deployments"
}
```

Option C — if Claude Code ever supports env-variable interpolation in headers (it does
not as of 2026-06), document that explicitly and add a build step to generate the file.

---

### CR-02: `resolvePortfolioId()` passes `AnonymousAuthenticationToken` through the `isAuthenticated()` guard

**File:** `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java:255-258`

**Issue:** Spring Security's `AnonymousAuthenticationToken` has `isAuthenticated()` return
`true` by design (it is a legitimate anonymous principal). The guard:

```java
if (auth == null || !auth.isAuthenticated()) {
    throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
}
```

does NOT block anonymous users — it passes them through to `auth.getName()` which returns
`"anonymousUser"`, which then fails the repository lookup and ultimately throws 401. This
means the security boundary relies on the repository lookup as the actual gate rather than
the explicit auth check. In the current deployment this is safe because `/mcp` is behind
`anyRequest().authenticated()` and `httpBasic` — Spring Security will reject the request
before the tool bean is invoked. However:

1. If the MCP tool bean is ever invoked from a non-HTTP context (e.g., internal event,
   scheduled job, test without proper auth setup), the guard silently passes the anonymous
   token through.
2. The contract implied by the Javadoc ("throws 401 if unauthenticated") is violated for
   `AnonymousAuthenticationToken`.
3. The `PortfolioMcpToolsTest` sets up a real `UsernamePasswordAuthenticationToken` so
   it does not catch this gap.

**Fix:** Add an explicit `instanceof` check to reject anonymous principals:

```java
private Long resolvePortfolioId() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()
            || auth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    String username = auth.getName();
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

This is the same pattern used in Spring Security's `AbstractSecurityInterceptor` and is
consistent with the pattern `AnalyticsController.resolvePortfolioId(Authentication auth)`
uses (the injected `Authentication` from Spring MVC is never the anonymous token when a
request passes the `anyRequest().authenticated()` gate).

---

## Warnings

### WR-01: CSRF exemption for `/mcp` is method-unbounded — covers all HTTP verbs

**File:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java:137`

**Issue:** The CSRF exemption:

```java
AntPathRequestMatcher.antMatcher("/mcp")
```

exempts ALL HTTP methods (GET, POST, PATCH, DELETE, etc.) on `/mcp`. The comment
justifies this as safe because `/mcp` requires HTTP Basic. However, browsers that have
cached Basic credentials (after the first prompted login) will automatically resend the
`Authorization: Basic ...` header on cross-origin requests — Basic auth is not
CSRF-immune. The Streamable-HTTP MCP transport only uses POST, so a method-scoped
exemption is sufficient and safer:

```java
AntPathRequestMatcher.antMatcher(HttpMethod.POST, "/mcp")
```

This is the same pattern already used for `/api/ai/key` and `/api/ai/chat` on lines
126 and 132. The inconsistency is itself a signal of incomplete threat modeling for
this endpoint.

---

### WR-02: Null VaR values silently reach `RiskMetricsResult` and the LLM

**File:** `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java:139-155`

**Issue:** The `BigDecimalRef` loop:

```java
BigDecimalRef histVar = new BigDecimalRef();
BigDecimalRef paramVar = new BigDecimalRef();
for (VarResultDto var : scorecard.var()) {
    if ("HISTORICAL".equals(var.method())) { histVar.value = var.amount(); }
    else if ("PARAMETRIC".equals(var.method())) { paramVar.value = var.amount(); }
}
```

If `scorecard.var()` is empty, or contains neither "HISTORICAL" nor "PARAMETRIC"
entries, both `histVar.value` and `paramVar.value` remain `null`. These nulls are then
passed directly into `RiskMetricsResult`:

```java
RiskMetricsResult result = new RiskMetricsResult(
    ..., histVar.value, paramVar.value);
```

`RiskMetricsResult` record accepts `null` `BigDecimal` fields (no `@NonNull`). Jackson
serializes them as JSON `null`. The LLM receives:

```json
{"historicalVar95": null, "parametricVar95": null}
```

without any indication that data is missing. This is a silent data quality failure. In
production the three VaR entries are always present from `RiskCalculator`, but in a
future refactor or if `CVaR_HISTORICAL` ordering changes, this can break silently.

`RiskScorecardDto.var` Javadoc documents "2–3 VaR entries: HISTORICAL, PARAMETRIC,
optionally CVaR_HISTORICAL" — so the current code is defensible against the spec, but
the failure mode is invisible.

**Fix:** Validate after the loop and return an error result if either VaR method is absent:

```java
if (histVar.value == null || paramVar.value == null) {
    log.error("get_risk_metrics: VaR list missing HISTORICAL or PARAMETRIC entry (methods found: {})",
              scorecard.var().stream().map(VarResultDto::method).toList());
    return McpSchema.CallToolResult.builder()
            .content(List.of(new McpSchema.TextContent("Risk metrics unavailable")))
            .isError(true)
            .build();
}
```

Alternatively, replace `BigDecimalRef` with a stream lookup that returns `Optional`,
making the null case explicit:

```java
Optional<BigDecimal> histVar = scorecard.var().stream()
        .filter(v -> "HISTORICAL".equals(v.method()))
        .map(VarResultDto::amount)
        .findFirst();
Optional<BigDecimal> paramVar = scorecard.var().stream()
        .filter(v -> "PARAMETRIC".equals(v.method()))
        .map(VarResultDto::amount)
        .findFirst();
if (histVar.isEmpty() || paramVar.isEmpty()) { ... return error ... }
```

---

### WR-03: Error-hygiene test only covers `getPortfolioSummary` — `getRiskMetrics` and `getPositionDetail` exception paths are untested

**File:** `backend/src/test/java/com/quantlens/mcp/PortfolioMcpToolsTest.java:147-167`

**Issue:** `toolException_doesNotLeakStackTrace` mocks only `PortfolioService` and tests
only `getPortfolioSummary`. The catch blocks in `getRiskMetrics` (line 167-173 of
`PortfolioMcpTools`) and `getPositionDetail` (line 231-237) have identical structure and
identical intent, but are not covered by the error-hygiene assertion. A future maintenance
change to those catch blocks (e.g., accidentally adding `e.getMessage()` to the error
text) would not be caught by the test suite.

**Fix:** Add two analogous leak-hygiene tests:

```java
@Test
void getRiskMetrics_exception_doesNotLeakStackTrace() {
    RiskCalculator throwingCalc = mock(RiskCalculator.class);
    when(throwingCalc.computeRiskScorecard(anyLong()))
            .thenThrow(new RuntimeException("INTERNAL_RISK_ERROR_secret"));
    PortfolioMcpTools failingTools =
            new PortfolioMcpTools(portfolioService, throwingCalc, portfolioRepository, objectMapper);
    McpSchema.CallToolResult result = failingTools.getRiskMetrics();
    assertThat(result.isError()).isTrue();
    String text = extractText(result);
    assertThat(text).doesNotContain("INTERNAL_RISK_ERROR_secret")
                    .doesNotContain("RuntimeException");
}

@Test
void getPositionDetail_exception_doesNotLeakStackTrace() {
    PortfolioService throwingService = mock(PortfolioService.class);
    when(throwingService.getHoldings(anyLong()))
            .thenThrow(new RuntimeException("INTERNAL_HOLDINGS_ERROR_secret"));
    PortfolioMcpTools failingTools =
            new PortfolioMcpTools(throwingService, riskCalculator, portfolioRepository, objectMapper);
    McpSchema.CallToolResult result = failingTools.getPositionDetail("AAPL");
    assertThat(result.isError()).isTrue();
    String text = extractText(result);
    assertThat(text).doesNotContain("INTERNAL_HOLDINGS_ERROR_secret")
                    .doesNotContain("RuntimeException");
}
```

---

### WR-04: `PortfolioMcpTools` holds a direct `PortfolioRepository` reference — bypasses the service layer

**File:** `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java:53,60,260`

**Issue:** The MCP tool bean injects `PortfolioRepository` directly to call
`findPortfolioIdByUsername`. This means the `mcp` module accesses `portfolio::domain`
directly rather than through `portfolio::service`. While `portfolio::domain` is listed in
`allowedDependencies` in `package-info.java` and Modulith passes, this is the same
principal-resolution pattern that every other module (`AnalyticsController`,
`PortfolioController`) duplicates manually. The domain repository carries transaction and
persistence session semantics that the service layer is supposed to encapsulate.

A `resolvePortfolioId(String username)` method on `PortfolioService` (or a dedicated
`PortfolioSecurityService`) would give a single authoritative location for this logic
rather than having it copied across `AnalyticsController`, `PortfolioController`, and
now `PortfolioMcpTools`.

**Fix:** Add to `PortfolioService`:
```java
public Long resolvePortfolioIdByUsername(String username) {
    return portfolioRepository.findPortfolioIdByUsername(username)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
}
```

Remove the `PortfolioRepository` injection from `PortfolioMcpTools`; call
`portfolioService.resolvePortfolioIdByUsername(auth.getName())` instead. This also
removes `portfolio::domain` from the MCP module's allowed dependencies (the mcp module
should only depend on `portfolio::service` and `portfolio::api`).

---

## Info

### IN-01: `sanitizeTicker` evaluates the sanitized expression twice — redundant computation

**File:** `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java:274-276`

**Issue:** The method body:

```java
return ticker.toUpperCase()
        .replaceAll("[^A-Z0-9.]", "")
        .substring(0, Math.min(ticker.toUpperCase().replaceAll("[^A-Z0-9.]", "").length(), 10));
```

computes `ticker.toUpperCase().replaceAll("[^A-Z0-9.]", "")` twice. The result is
correct (both chains produce identical strings), but the redundancy is confusing and
allocates an extra intermediate `String`.

**Fix:**
```java
private String sanitizeTicker(String ticker) {
    if (ticker == null) return "";
    String cleaned = ticker.toUpperCase().replaceAll("[^A-Z0-9.]", "");
    return cleaned.length() > 10 ? cleaned.substring(0, 10) : cleaned;
}
```

---

### IN-02: `get_position_detail` error text returns user-supplied (sanitized) ticker — inconsistent with "static safe message" hygiene policy

**File:** `backend/src/main/java/com/quantlens/mcp/tools/PortfolioMcpTools.java:204`

**Issue:** The "not found" path:

```java
.content(List.of(new McpSchema.TextContent("Position not found for ticker: " + sanitized)))
```

echoes the sanitized user input back in the error message. The class Javadoc states
"only a static safe message is returned to the MCP client (T-09-03)" — this message is
not static. The ticker is sanitized (alphanumeric + `.`, max 10 chars) so there is no
injection risk; but it is inconsistent with the stated policy and could theoretically
aid in enumerating valid tickers if the attacker has multi-user access.

**Fix:** Use a static message, consistent with the other not-found paths:
```java
.content(List.of(new McpSchema.TextContent("Position not found for the requested ticker")))
```

---

_Reviewed: 2026-06-10_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_

---

## Resolution Log (2026-06-10)

Adjudicated by orchestrator; backend suite re-run green at **215 tests** after fixes.

| ID | Verdict | Action |
|----|---------|--------|
| **CR-01** | **REJECTED — false positive** | Claude Code **does** expand `${VAR:-default}` in `.mcp.json`, explicitly including `headers` values for HTTP servers (verified against official MCP docs, code.claude.com/docs/en/mcp.md, with a near-identical example). The `:-default` form is the *recommended* defensive pattern for committed configs. `.mcp.json` left unchanged. |
| **CR-02** | **FIXED** | `resolvePortfolioId()` now rejects `AnonymousAuthenticationToken` explicitly (`auth instanceof AnonymousAuthenticationToken`). |
| **WR-01** | **FIXED** | `/mcp` CSRF exemption scoped to `HttpMethod.POST` (matches `/api/ai/*` pattern). |
| **WR-02** | **FIXED** | `get_risk_metrics` guards null HISTORICAL/PARAMETRIC VaR → static "Risk metrics unavailable" error result (no `null` serialized to the LLM). |
| **WR-03** | **FIXED** | Added `getRiskMetrics_exception_doesNotLeakStackTrace` + `getPositionDetail_exception_doesNotLeakStackTrace` (PortfolioMcpToolsTest now 7). |
| **WR-04** | **DEFERRED (documented)** | Direct `PortfolioRepository` principal resolution is the **established** codebase pattern (`AnalyticsController`/`PortfolioController` do the same); `portfolio::domain` is a declared, Modulith-valid dependency. Centralizing into a `PortfolioService.resolvePortfolioIdByUsername` is a cross-cutting refactor tracked for a future cleanup, not phase-9 polish. |
| **IN-01** | **FIXED** | `sanitizeTicker` computes the cleaned form once. |
| **IN-02** | **FIXED** | `get_position_detail` not-found path uses a static message (no sanitized-input echo). |

**Net:** 5 fixed (CR-02, WR-01, WR-02, WR-03, IN-01, IN-02 — six findings), 1 rejected as a false positive (CR-01), 1 deferred with rationale (WR-04). Suite: 215 green, 0 failures.
