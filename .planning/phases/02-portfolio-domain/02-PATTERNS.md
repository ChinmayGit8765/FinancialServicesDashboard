# Phase 2: Portfolio Domain - Pattern Map

**Mapped:** 2026-06-07
**Files analyzed:** 11 new files (1 controller, 1 service, 5 DTOs, 2 test files, 2 repository extensions)
**Analogs found:** 11 / 11

---

## File Classification

| New / Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---------------------|------|-----------|----------------|---------------|
| `com/quantlens/portfolio/api/PortfolioController.java` | controller | request-response | `com/quantlens/security/api/AuthController.java` | exact |
| `com/quantlens/portfolio/service/PortfolioService.java` | service | CRUD / transform | `com/quantlens/seed/SeedRunner.java` (BigDecimal math) + `AuthController.java` (principal resolution) | role-match |
| `com/quantlens/portfolio/api/HoldingDto.java` | DTO record | request-response | `com/quantlens/security/api/MeDto.java` | exact |
| `com/quantlens/portfolio/api/PortfolioPnlDto.java` | DTO record | request-response | `com/quantlens/security/api/MeDto.java` | exact |
| `com/quantlens/portfolio/api/AllocationSliceDto.java` | DTO record | request-response | `com/quantlens/security/api/PersonaDto.java` | exact |
| `com/quantlens/portfolio/api/TransactionDto.java` | DTO record | request-response | `com/quantlens/security/api/MeDto.java` | exact |
| `com/quantlens/portfolio/api/BenchmarkComparisonDto.java` | DTO record | request-response | `com/quantlens/security/api/PersonaDto.java` | exact |
| `com/quantlens/portfolio/domain/PositionRepository.java` (extend) | repository | CRUD | `com/quantlens/marketdata/domain/SecurityRepository.java` | exact |
| `com/quantlens/portfolio/domain/TransactionRepository.java` (extend) | repository | CRUD + pagination | `com/quantlens/marketdata/domain/SecurityRepository.java` | exact |
| `com/quantlens/marketdata/domain/OhlcvBarRepository.java` (extend) | repository | bulk query | `com/quantlens/marketdata/domain/SecurityRepository.java` | exact |
| `com/quantlens/portfolio/PortfolioServiceTest.java` (test) | unit test | — | `com/quantlens/seed/SeedRunnerIntegrationTest.java` (style) | role-match |
| `com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` (test) | integration test | request-response | `com/quantlens/security/AuthIntegrationTest.java` + `PersonaIntegrationTest.java` | exact |

---

## Pattern Assignments

### `com/quantlens/portfolio/api/PortfolioController.java` (controller, request-response)

**Analog:** `backend/src/main/java/com/quantlens/security/api/AuthController.java`

**Imports pattern** (lines 1–14):
```java
package com.quantlens.portfolio.api;

import com.quantlens.portfolio.domain.AppUserRepository;
import com.quantlens.portfolio.domain.Portfolio;
import com.quantlens.portfolio.domain.PortfolioRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
// + import for Pageable, PageableDefault, Sort, Page from Spring Data
```

**Class declaration and constructor-injection pattern** (lines 35–54):
```java
@RestController
@RequestMapping("/api/portfolio")
public class PortfolioController {

    private final AppUserRepository appUserRepository;
    private final PortfolioRepository portfolioRepository;
    private final PortfolioService portfolioService;

    public PortfolioController(AppUserRepository appUserRepository,
                               PortfolioRepository portfolioRepository,
                               PortfolioService portfolioService) {
        this.appUserRepository = appUserRepository;
        this.portfolioRepository = portfolioRepository;
        this.portfolioService = portfolioService;
    }
```
No `@Autowired` — constructor injection only, matching `AuthController` lines 50–54.

**Principal-resolution pattern** (AuthController lines 84–99 — THE authoritative pattern):
```java
@GetMapping("/holdings")
@Transactional(readOnly = true)
public ResponseEntity<List<HoldingDto>> getHoldings(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        return ResponseEntity.status(401).build();
    }
    String username = authentication.getName();
    return appUserRepository.findByUsername(username)
            .map(user -> {
                List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());
                if (portfolios.isEmpty()) {
                    return ResponseEntity.<List<HoldingDto>>status(404).build();
                }
                Long portfolioId = portfolios.get(0).getId();
                return ResponseEntity.ok(portfolioService.getHoldings(portfolioId));
            })
            .orElse(ResponseEntity.status(401).build());
}
```
Mirror this pattern exactly for all 5 endpoints. Extract the user→portfolio resolution into a private helper to avoid repeating the 5-line block across all endpoints (RESEARCH.md recommends this).

**Pageable endpoint pattern** (RESEARCH.md Spring REST Patterns section):
```java
@GetMapping("/transactions")
@Transactional(readOnly = true)
public ResponseEntity<Page<TransactionDto>> getTransactions(
        Authentication authentication,
        @PageableDefault(size = 20, sort = "txDate", direction = Sort.Direction.DESC)
        Pageable pageable) {
    // ... resolve portfolio then:
    return ResponseEntity.ok(portfolioService.getTransactions(portfolioId, pageable));
}
```

**Module-boundary note:** `PortfolioController` is inside `com.quantlens.portfolio` — the module `package-info.java` (line 1–3) already declares `allowedDependencies = {"marketdata::domain"}`. No additional `@NamedInterface` needed for the `api` subpackage — it is consumed by HTTP clients only, not by other Spring Modulith modules.

---

### `com/quantlens/portfolio/service/PortfolioService.java` (service, CRUD + transform)

**Analog:** `AuthController.java` (principal-resolution chain) + entity fields from `Position.java`, `Transaction.java`, `OhlcvBar.java`, `Security.java`

**Package and class declaration:**
```java
package com.quantlens.portfolio.service;

import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import com.quantlens.portfolio.domain.Transaction;
import com.quantlens.portfolio.domain.TransactionRepository;
import com.quantlens.portfolio.api.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional(readOnly = true)   // all methods are read-only per CONTEXT.md
public class PortfolioService { ... }
```
`@Service` + constructor injection (no `@Autowired`) — same convention as `AuthController`.

**Entity field access pattern** — use the exact getter names from Phase 1 entities:
- `Position`: `.getId()`, `.getSecurity()`, `.getQuantity()`, `.getAvgCostBasis()`
- `Transaction`: `.getId()`, `.getSecurity()`, `.getTxDate()`, `.getTxType()`, `.getQuantity()`, `.getPrice()`
- `OhlcvBar`: `.getSecurity()`, `.getBarDate()`, `.getClosePrice()` (NOT `.close` — the column is `close_price`, field is `closePrice`)
- `Security`: `.getId()`, `.getTicker()`, `.getName()`, `.getSector()`, `.isBenchmark()`

**BigDecimal scale convention** (from CONTEXT.md + RESEARCH.md):
- Prices/ratios: `scale(6, RoundingMode.HALF_UP)`
- Display money: `scale(2, RoundingMode.HALF_UP)`
- All `divide()` calls MUST include scale + RoundingMode — never bare `.divide(other)`

**Javadoc model-assumption comment pattern** (copy verbatim per RESEARCH.md Pattern 2):
```java
/**
 * Constant-current-holdings equity curve.
 *
 * <p>This curve values the portfolio's CURRENT (final) holdings across every
 * historical trading day in the seeded window, as if those shares were held
 * throughout. This is a dashboard equity curve — it shows how the current
 * portfolio WOULD have performed, not how it DID perform (the latter requires
 * reconstructing historical holdings from transactions, which is deferred).
 *
 * <p>Assumption: defensible for a demo dashboard. Limitation: overstates
 * performance if high-performing stocks were bought late. Must be labelled
 * in the UI with a tooltip: "Based on current holdings valued historically".
 */
```

---

### `com/quantlens/portfolio/api/HoldingDto.java` (DTO record, request-response)

**Analog:** `backend/src/main/java/com/quantlens/security/api/MeDto.java` (lines 1–14)

**Pattern to copy:**
```java
package com.quantlens.security.api;

/**
 * DTO returned by {@code GET /api/auth/me} for the authenticated user.
 * ...
 * @param username    the authenticated user's login name
 * @param persona     the persona style label (Growth, Income, Balanced)
 * @param portfolioId the ID of the portfolio belonging to this user
 */
public record MeDto(String username, String persona, Long portfolioId) {
}
```

**Apply as:**
```java
package com.quantlens.portfolio.api;

import java.math.BigDecimal;

/**
 * DTO returned by {@code GET /api/portfolio/holdings} — one entry per position.
 * <p>
 * All monetary fields use {@link BigDecimal}; Jackson serialises them as JSON
 * numbers (not strings) for direct ECharts binding.
 *
 * @param ticker              security ticker symbol
 * @param name                security full name
 * @param sector              sector classification
 * @param quantity            shares held — NUMERIC(18,4)
 * @param avgCostBasis        average cost per share — scale 6
 * @param currentPrice        latest OHLCV close — scale 6
 * @param currentMarketValue  qty × currentPrice — scale 2
 * @param portfolioWeight     positionValue / totalValue — scale 6 (0..1)
 * @param unrealizedPnlAbs    (currentPrice − avgCostBasis) × qty — scale 2
 * @param unrealizedPnlPct    unrealizedPnlAbs / (avgCostBasis × qty) — scale 6
 */
public record HoldingDto(
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
Rules: one `package` declaration, one Javadoc block with `@param` per field, then the compact record, closing `{}` on same line. No `@JsonProperty` needed — Jackson maps camelCase record components to camelCase JSON automatically.

---

### `com/quantlens/portfolio/api/PortfolioPnlDto.java` (DTO record, request-response)

**Analog:** `MeDto.java` (structure) — but contains a nested record, which has no direct analog in Phase 1 (closest: `MeDto` shows the record convention; the nesting is new).

**Pattern:**
```java
package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Nested value type for a single point on the equity curve.
 * {@link LocalDate} serialises as {@code "2022-09-12"} (Spring Boot auto-registers
 * {@code JavaTimeModule} with {@code WRITE_DATES_AS_TIMESTAMPS=false}).
 *
 * @param date  trading day
 * @param value portfolio market value on this day — scale 2
 */
public record DateValueDto(LocalDate date, BigDecimal value) {}

/**
 * DTO returned by {@code GET /api/portfolio/pnl}.
 *
 * @param totalMarketValue       Σ(qty × latestClose) — scale 2
 * @param totalCostBasis         Σ(qty × avgCostBasis) — scale 2
 * @param totalUnrealizedGainAbs totalMarketValue − totalCostBasis — scale 2
 * @param totalUnrealizedGainPct totalUnrealizedGainAbs / totalCostBasis — scale 6
 * @param dailyChangeAbs         equityCurve[last] − equityCurve[last−1] — scale 2
 * @param dailyChangePct         dailyChangeAbs / equityCurve[last−1] — scale 6
 * @param equityCurve            504-entry constant-current-holdings curve
 */
public record PortfolioPnlDto(
    BigDecimal totalMarketValue,
    BigDecimal totalCostBasis,
    BigDecimal totalUnrealizedGainAbs,
    BigDecimal totalUnrealizedGainPct,
    BigDecimal dailyChangeAbs,
    BigDecimal dailyChangePct,
    List<DateValueDto> equityCurve
) {}
```
Both record types go in the same file `PortfolioPnlDto.java` (the nested `DateValueDto` is a package-level type in the same compilation unit, or a separate file — either is fine, prefer separate file for reuse by `BenchmarkComparisonDto` tests).

---

### `com/quantlens/portfolio/api/AllocationSliceDto.java` (DTO record, request-response)

**Analog:** `PersonaDto.java` (lines 1–14) — three-field record, no collections.

**PersonaDto pattern to mirror:**
```java
package com.quantlens.security.api;

/**
 * DTO representing a demo persona for the one-click login switcher.
 * ...
 * @param username     the login username
 * @param persona      the persona style label
 * @param passwordHint the shared demo password shown on the login screen
 */
public record PersonaDto(String username, String persona, String passwordHint) {
}
```

**Apply as:**
```java
package com.quantlens.portfolio.api;

import java.math.BigDecimal;

/**
 * DTO for one allocation slice in {@code GET /api/portfolio/allocation}.
 * <p>
 * ECharts pie chart expects {@code {name, value}} pairs.  The {@code label}
 * field maps to ECharts {@code name}; Phase 3 chooses {@code weight} or
 * {@code marketValue} for the {@code value} axis.
 *
 * @param label       sector name (e.g. "Technology")
 * @param weight      fraction of portfolio — scale 6 (0.000000–1.000000)
 * @param marketValue total $ value in this sector — scale 2
 */
public record AllocationSliceDto(String label, BigDecimal weight, BigDecimal marketValue) {
}
```

---

### `com/quantlens/portfolio/api/TransactionDto.java` (DTO record, request-response)

**Analog:** `MeDto.java` (record structure) — note `Transaction.java` entity (lines 1–88) provides exact field names.

**Entity field names to mirror in the DTO** (from `Transaction.java` lines 39–49):
- `txDate` → `LocalDate txDate`
- `txType` → `String txType` (values: `"BUY"` or `"SELL"`)
- `quantity` → `BigDecimal quantity` (scale 4)
- `price` → `BigDecimal price` (scale 6)

**Apply as:**
```java
package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO for one transaction in {@code GET /api/portfolio/transactions}.
 * <p>
 * Most-recent-first when paginated. {@code runningCostBasis} is the computed
 * cumulative average cost per share at the moment of this transaction (not stored
 * in the DB — derived by the service scanning transactions chronologically ascending).
 *
 * @param txDate           trade date
 * @param txType           "BUY" or "SELL"
 * @param ticker           security ticker
 * @param quantity         shares traded — scale 4
 * @param price            price per share at trade — scale 6
 * @param tradeValue       qty × price — scale 2 (convenience for Phase 3)
 * @param runningCostBasis avg cost per share after this trade — scale 6
 */
public record TransactionDto(
    LocalDate txDate,
    String txType,
    String ticker,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal tradeValue,
    BigDecimal runningCostBasis
) {}
```

---

### `com/quantlens/portfolio/api/BenchmarkComparisonDto.java` (DTO record, request-response)

**Analog:** `PersonaDto.java` (three-field record structure) — parallel array shape is new (no Phase 1 analog), but the record convention is identical.

```java
package com.quantlens.portfolio.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO returned by {@code GET /api/portfolio/benchmark}.
 * <p>
 * Parallel arrays optimised for ECharts line chart with two series.
 * Both series are indexed to 100.0000 on {@code dates[0]} (the first shared
 * trading day, 2022-09-12). Terminology: "cumulative simple return indexed
 * to 100" / "normalised price series".
 *
 * @param dates           ISO-8601 date strings, ascending, e.g. "2022-09-12"
 * @param portfolioSeries portfolio value indexed to 100 on day 0 — scale 4
 * @param benchmarkSeries SPX500 proxy close indexed to 100 on day 0 — scale 4
 */
public record BenchmarkComparisonDto(
    List<String> dates,
    List<BigDecimal> portfolioSeries,
    List<BigDecimal> benchmarkSeries
) {}
```
`dates` is `List<String>` (not `List<LocalDate>`) because the ECharts xAxis `data` array expects string labels. The service converts `LocalDate` to `date.toString()` (ISO-8601) before adding to the list.

---

### `com/quantlens/portfolio/domain/PositionRepository.java` (extend — add `@Query` methods)

**Analog:** `SecurityRepository.java` (lines 1–16) — the pattern for adding named derived queries and `@Query` methods to a bare `JpaRepository`.

**Current state** (PositionRepository.java lines 1–9): bare `JpaRepository<Position, Long>` with no methods.

**SecurityRepository pattern to copy:**
```java
package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SecurityRepository extends JpaRepository<Security, Long> {
    Optional<Security> findByTicker(String ticker);
    List<Security> findByBenchmarkTrue();
}
```

**Apply as** (add to PositionRepository):
```java
package com.quantlens.portfolio.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface PositionRepository extends JpaRepository<Position, Long> {

    /**
     * Loads positions with their Security eagerly in a single JOIN FETCH query.
     * Prevents N+1 when iterating position.getSecurity() in PortfolioService.
     */
    @Query("SELECT p FROM Position p JOIN FETCH p.security WHERE p.portfolio.id = :portfolioId")
    List<Position> findByPortfolioIdWithSecurity(@Param("portfolioId") Long portfolioId);
}
```

---

### `com/quantlens/portfolio/domain/TransactionRepository.java` (extend — add `@Query` + pageable)

**Analog:** `SecurityRepository.java` for the `@Query` pattern; `AuthIntegrationTest.java` for how `Pageable` result is used end-to-end.

**Apply as:**
```java
package com.quantlens.portfolio.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    /**
     * Paginated transaction history, security eagerly loaded, most-recent-first.
     * ORDER BY in the JPQL is the stable sort; {@code id DESC} breaks same-day ties.
     * JOIN FETCH is safe for pagination here because Transaction→Security is a
     * single-valued association (not a collection join).
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.security
        WHERE t.portfolio.id = :portfolioId
        ORDER BY t.txDate DESC, t.id DESC
        """)
    Page<Transaction> findByPortfolioIdWithSecurity(
        @Param("portfolioId") Long portfolioId,
        Pageable pageable);

    /**
     * All transactions for cost-basis computation — ascending chronological order.
     * The service uses this separate query (not the paginated one) to compute
     * runningCostBasis: iterates all transactions in BUY→SELL chronological order
     * before mapping them to the DESC-ordered page for display.
     */
    @Query("""
        SELECT t FROM Transaction t
        JOIN FETCH t.security
        WHERE t.portfolio.id = :portfolioId
        ORDER BY t.txDate ASC, t.id ASC
        """)
    List<Transaction> findByPortfolioIdChronological(@Param("portfolioId") Long portfolioId);
}
```

---

### `com/quantlens/marketdata/domain/OhlcvBarRepository.java` (extend — add bulk `@Query` methods)

**Analog:** `SecurityRepository.java` for the `@Query` convention.

**Current state** (OhlcvBarRepository.java lines 1–8): bare `JpaRepository<OhlcvBar, Long>`.

**Apply as:**
```java
package com.quantlens.marketdata.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface OhlcvBarRepository extends JpaRepository<OhlcvBar, Long> {

    /**
     * Latest OHLCV bar per security — used for current-price lookups (holdings endpoint).
     * Returns one bar per security ID; the correlated subquery is evaluated once per group.
     */
    @Query("""
        SELECT b FROM OhlcvBar b
        WHERE b.security.id IN :securityIds
          AND b.barDate = (
              SELECT MAX(b2.barDate) FROM OhlcvBar b2
              WHERE b2.security.id = b.security.id
          )
        """)
    List<OhlcvBar> findLatestBarBySecurityIds(@Param("securityIds") List<Long> securityIds);

    /**
     * All OHLCV bars for a set of securities, ordered for time-series iteration.
     * Used by equity-curve and benchmark endpoints. Returns 504 × N rows in a
     * single JDBC round-trip (504 × 7 = 3528 rows for alice — well within memory).
     */
    @Query("""
        SELECT b FROM OhlcvBar b
        WHERE b.security.id IN :securityIds
        ORDER BY b.security.id ASC, b.barDate ASC
        """)
    List<OhlcvBar> findAllBySecurityIdsOrdered(@Param("securityIds") List<Long> securityIds);
}
```

---

### `com/quantlens/portfolio/PortfolioServiceTest.java` (unit test)

**Analog:** `SeedRunnerIntegrationTest.java` (style/structure) — but this is a **pure unit test** with no Spring context. The Phase 1 test closest to a pure unit test is `SeedRunnerIntegrationTest`; however, `PortfolioServiceTest` requires no Testcontainers. Mirror the JUnit 5 + AssertJ import style.

**Package and imports pattern** (from `SeedRunnerIntegrationTest.java` lines 1–8, adapted):
```java
package com.quantlens.portfolio;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
```
No `@SpringBootTest`, no `@Autowired` — plain JUnit 5 with hand-crafted inputs. The test class does NOT extend `AbstractPostgresIntegrationTest`.

**Test method naming convention** (from `SeedRunnerIntegrationTest.java`): `verbNounPredicate` in camelCase, e.g. `unrealizedPnlFormula`, `allocationWeightsSumToOne`, `benchmarkBothSeriesStartAt100`.

**AssertJ assertion style** (from `AuthIntegrationTest.java` lines 52–54):
```java
assertThat(actual).as("description").isEqualByComparingTo(expected);
// For BigDecimal: use .isEqualByComparingTo() not .isEqualTo() to ignore trailing zeros
// For tolerance: use .isCloseTo(expected, within(new BigDecimal("0.01")))
```

---

### `com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` (integration test)

**Analogs:**
- `AuthIntegrationTest.java` — login→cookie→authenticated GET pattern (exact match)
- `PersonaIntegrationTest.java` — multi-user scoping assertions + `extractJsonField` helper

**Package and class declaration** (AuthIntegrationTest.java lines 1–35):
```java
package com.quantlens.portfolio;

import com.quantlens.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import static org.assertj.core.api.Assertions.assertThat;

class PortfolioControllerIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;
```
Extends `AbstractPostgresIntegrationTest` — inherits `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `@ActiveProfiles("test")` + Testcontainers pgvector16 container.

**Login-and-get-cookie helper** (copy from `PersonaIntegrationTest.java` lines 73–91):
```java
private String loginAndGetSessionCookie(String username) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
    body.add("username", username);
    body.add("password", "demo1234");

    ResponseEntity<String> response = restTemplate.exchange(
            "/api/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(body, headers),
            String.class);

    assertThat(response.getStatusCode())
            .as("Login for %s should succeed", username)
            .isEqualTo(HttpStatus.OK);
    return response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
}
```

**Authenticated GET helper** (derived from `PersonaIntegrationTest.java` lines 93–105):
```java
private ResponseEntity<String> authenticatedGet(String path, String sessionCookie) {
    HttpHeaders headers = new HttpHeaders();
    headers.add(HttpHeaders.COOKIE, sessionCookie);
    return restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
}
```

**JSON field extraction helper** (copy from `PersonaIntegrationTest.java` lines 128–135):
```java
private String extractJsonField(String json, String fieldName) throws Exception {
    com.fasterxml.jackson.databind.JsonNode node =
            new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    assertThat(node.has(fieldName))
            .as("field '%s' not found in JSON: %s", fieldName, json)
            .isTrue();
    return node.path(fieldName).asText();
}
```

**Test method pattern** (from `AuthIntegrationTest.java` lines 37–53):
```java
@Test
void getHoldings_alice_returns200() {
    String cookie = loginAndGetSessionCookie("alice");
    ResponseEntity<String> response = authenticatedGet("/api/portfolio/holdings", cookie);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotEmpty();
}
```

**Unauthenticated-returns-401 pattern** (from SecurityConfig's entry point — confirm in test):
```java
@Test
void getHoldings_unauthenticated_returns401() {
    ResponseEntity<String> response = restTemplate.exchange(
            "/api/portfolio/holdings", HttpMethod.GET, HttpEntity.EMPTY, String.class);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
}
```

---

## Shared Patterns

### Authentication — Principal Resolution Chain
**Source:** `backend/src/main/java/com/quantlens/security/api/AuthController.java` lines 84–99
**Apply to:** All 5 endpoint methods in `PortfolioController`

```java
// Step 1: null-check the Authentication object
if (authentication == null || !authentication.isAuthenticated()) {
    return ResponseEntity.status(401).build();
}
// Step 2: resolve username
String username = authentication.getName();
// Step 3: look up AppUser
appUserRepository.findByUsername(username)
    .map(user -> {
        // Step 4: resolve Portfolio
        List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());
        if (portfolios.isEmpty()) {
            return ResponseEntity.<T>status(404).build();
        }
        Long portfolioId = portfolios.get(0).getId();
        // Step 5: delegate to service
        return ResponseEntity.ok(portfolioService.someMethod(portfolioId));
    })
    .orElse(ResponseEntity.status(401).build());
```
**Security constraint:** portfolioId MUST always be derived from the principal. Never accept a portfolioId from request parameters or path variables (IDOR prevention — RESEARCH.md Security Domain V4).

### `@Transactional(readOnly = true)` Placement
**Source:** `AuthController.java` line 85
**Apply to:** All service methods in `PortfolioService` (the annotation on the class-level `@Transactional(readOnly=true)` covers all methods; add it at service class level, not just on controller methods)

### Constructor Injection Style
**Source:** `AuthController.java` lines 47–54
**Apply to:** `PortfolioController`, `PortfolioService`

```java
// No @Autowired — constructor injection only
public PortfolioController(AppUserRepository appUserRepository,
                           PortfolioRepository portfolioRepository,
                           PortfolioService portfolioService) {
    this.appUserRepository = appUserRepository;
    this.portfolioRepository = portfolioRepository;
    this.portfolioService = portfolioService;
}
```

### `@Query` JPQL Text-Block Style
**Source:** RESEARCH.md N+1 Prevention section (pattern confirmed against existing `SecurityRepository.java` style)
**Apply to:** `PositionRepository`, `TransactionRepository`, `OhlcvBarRepository` extensions

```java
@Query("""
    SELECT ... FROM ... WHERE ...
    """)
ReturnType methodName(@Param("paramName") ParamType param);
```
Java 15+ text blocks (triple-quote). One blank line after the closing `"""`. Always import `org.springframework.data.jpa.repository.Query` and `org.springframework.data.repository.query.Param`.

### BigDecimal Division Pattern
**Source:** RESEARCH.md Pitfall 2 — all `divide()` calls include scale + RoundingMode
**Apply to:** All arithmetic in `PortfolioService`

```java
// ALWAYS
BigDecimal weight = positionValue.divide(totalValue, 6, RoundingMode.HALF_UP);
// NEVER
BigDecimal weight = positionValue.divide(totalValue);  // throws ArithmeticException
```
Import: `import java.math.RoundingMode;`

### Module Boundary — marketdata.domain Access
**Source:** `backend/src/main/java/com/quantlens/portfolio/package-info.java` lines 1–3 and `marketdata/domain/package-info.java` lines 1–6

```java
// portfolio/package-info.java — already exists, DO NOT MODIFY
@org.springframework.modulith.ApplicationModule(
        displayName = "Portfolio",
        allowedDependencies = {"marketdata::domain"})
package com.quantlens.portfolio;
```
`PortfolioService` and `PortfolioController` import from `com.quantlens.marketdata.domain.*` directly — this is legal because the `marketdata.domain` package declares `@NamedInterface("domain")` and `portfolio` lists it in `allowedDependencies`. No new `package-info.java` entries are needed for Phase 2.

### Record DTO Convention
**Source:** `MeDto.java` lines 1–14, `PersonaDto.java` lines 1–14
**Apply to:** All 5 DTO records + `DateValueDto`

Rules:
1. `package` declaration
2. Import only what the record fields need (no unused imports)
3. Javadoc with `@param` per component
4. `public record Name(...)  {}` — compact body `{}` on the same line as closing paren, OR single newline then `{}`. Match existing DTOs.
5. No `@JsonProperty` annotations — Jackson camelCase mapping is automatic.
6. No custom serializers unless specifically required.

### Integration Test Login + Cookie Pattern
**Source:** `AuthIntegrationTest.java` lines 37–108, `PersonaIntegrationTest.java` lines 73–135
**Apply to:** `PortfolioControllerIntegrationTest`

The two-step pattern is the only way to test authenticated endpoints with `TestRestTemplate` (which does not follow redirects):
1. `POST /api/auth/login` with form-encoded body → extract `SET-COOKIE` header
2. Subsequent GET requests include `COOKIE: <value>` header

---

## No Analog Found

All Phase 2 files have close analogs in the Phase 1 codebase. The only genuinely new structural elements are:

| Element | Reason | Resolution |
|---------|--------|------------|
| Nested `DateValueDto` record inside `PortfolioPnlDto` | No Phase 1 DTO uses a `List<NestedRecord>` field | Use `MeDto` record convention; define `DateValueDto` as a separate top-level record in `PortfolioPnlDto.java` or its own file |
| `Page<TransactionDto>` return type | No Phase 1 controller returns paginated data | RESEARCH.md provides the `@PageableDefault` + `Page<T>` pattern explicitly; Spring Data `Pageable` is standard |
| `NavigableMap<LocalDate, BigDecimal>` for equity curve grouping | No Phase 1 service builds time-series maps | Standard Java — `TreeMap` implements `NavigableMap`; RESEARCH.md Pattern 2 provides the algorithm verbatim |

---

## Metadata

**Analog search scope:** `backend/src/main/java/com/quantlens/` (all packages) + `backend/src/test/java/com/quantlens/`
**Files scanned:** 31 source files + 7 test files
**Pattern extraction date:** 2026-06-07
