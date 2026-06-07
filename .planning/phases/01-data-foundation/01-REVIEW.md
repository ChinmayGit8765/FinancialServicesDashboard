---
phase: 01-data-foundation
reviewed: 2026-06-07T00:00:00Z
depth: standard
files_reviewed: 47
files_reviewed_list:
  - backend/pom.xml
  - backend/Dockerfile
  - backend/src/main/java/com/quantlens/QuantLensApplication.java
  - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturn.java
  - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturnRepository.java
  - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBar.java
  - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java
  - backend/src/main/java/com/quantlens/marketdata/domain/Security.java
  - backend/src/main/java/com/quantlens/marketdata/domain/SecurityRepository.java
  - backend/src/main/java/com/quantlens/portfolio/domain/AppUser.java
  - backend/src/main/java/com/quantlens/portfolio/domain/AppUserRepository.java
  - backend/src/main/java/com/quantlens/portfolio/domain/Portfolio.java
  - backend/src/main/java/com/quantlens/portfolio/domain/PortfolioRepository.java
  - backend/src/main/java/com/quantlens/portfolio/domain/Position.java
  - backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java
  - backend/src/main/java/com/quantlens/portfolio/domain/Transaction.java
  - backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java
  - backend/src/main/java/com/quantlens/security/QuantLensUserDetailsService.java
  - backend/src/main/java/com/quantlens/security/api/AuthController.java
  - backend/src/main/java/com/quantlens/security/api/MeDto.java
  - backend/src/main/java/com/quantlens/security/api/PersonaDto.java
  - backend/src/main/java/com/quantlens/security/config/SecurityConfig.java
  - backend/src/main/java/com/quantlens/seed/GbmGenerator.java
  - backend/src/main/java/com/quantlens/seed/PasswordEncoderConfig.java
  - backend/src/main/java/com/quantlens/seed/SeedLog.java
  - backend/src/main/java/com/quantlens/seed/SeedLogRepository.java
  - backend/src/main/java/com/quantlens/seed/SeedRunner.java
  - backend/src/main/resources/application.yml
  - backend/src/main/resources/db/migration/V1__schema.sql
  - backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java
  - backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java
  - backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java
  - backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java
  - backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java
  - backend/src/test/resources/application-test.yml
  - docker-compose.yml
  - docker/db/00-init.sql
  - .mcp.json
  - frontend/Dockerfile
  - frontend/nginx.conf
  - frontend/package.json
  - frontend/vite.config.ts
  - frontend/index.html
  - frontend/src/main.ts
  - frontend/src/App.vue
  - frontend/src/api/auth.ts
  - frontend/src/router/index.ts
  - frontend/src/stores/auth.ts
  - frontend/src/views/DashboardView.vue
  - frontend/src/views/LoginView.vue
findings:
  critical: 7
  warning: 8
  info: 4
  total: 19
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-06-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 47
**Status:** issues_found

## Summary

This is the walking-skeleton foundation for QuantLens: Flyway schema, correlated-GBM seeder, Spring Security session-based auth, and a Vue 3 SPA. The overall architecture is solid — BigDecimal is used correctly for prices, the idempotence guard pattern is sound, and CSRF wiring is thoughtful. However, seven blocking defects were found that will either expose user data, corrupt demo data, or break session authentication in a deployed scenario. Eight additional warnings cover logic errors and robustness gaps that will surface in later phases.

---

## Critical Issues

### CR-01: Demo password hardcoded in plain-text in `AuthController` and `SeedRunner` — exposed via public API

**File:** `backend/src/main/java/com/quantlens/security/api/AuthController.java:37`
**Also:** `backend/src/main/java/com/quantlens/seed/SeedRunner.java:63`
**Issue:** `DEMO_PASSWORD_HINT = "demo1234"` in `AuthController` is returned verbatim in every response from the **public** `GET /api/auth/personas` endpoint (no authentication required). This means the plaintext password is permanently embedded in a JSON response body readable by any anonymous HTTP client — including crawlers, proxy logs, and browser history. While the project is demo-only, this pattern is structurally identical to a credential leak: the password is the real BCrypt-input value, the endpoint is unauthenticated, and the response is JSON (easily scraped). In addition, `SeedRunner.DEMO_PASSWORD` holds the same string as a `private static final` field, which means it appears in the compiled `.class` file and is trivially extracted with `javap -c` or `strings` on the JAR.
**Fix:** For a demo application the password hint is acceptable as a UI affordance, but it must not originate from production code. Move it to a `@Value`-injectable config property with a dev-profile default, so it is absent from the production profile:
```yaml
# application-dev.yml / application-demo.yml only
quantlens.demo.password-hint: demo1234
```
```java
// AuthController
@Value("${quantlens.demo.password-hint:}")
private String demoPasswordHint;
```
This keeps the string out of compiled production bytecode and out of production API responses.

---

### CR-02: `GbmGenerator` is a stateful `@Component` with an instance field — concurrent calls corrupt each other's data

**File:** `backend/src/main/java/com/quantlens/seed/GbmGenerator.java:105`
**Issue:** `lastMktExcessReturns` is a mutable instance field on a Spring `@Component` (singleton-scoped by default). `generateOhlcv()` writes it; `generateFactors()` reads it. If two threads call `generateOhlcv()` concurrently (e.g., two parallel test contexts in the same JVM, or if a future controller ever calls the generator), the second call will overwrite the array while the first call's `generateFactors()` is still reading it, producing silently incorrect factor-return data. Worse, if `generateFactors()` is called before `generateOhlcv()` completes in another thread, it reads stale or partially-written data with no exception.

The `IllegalStateException` guard at line 207 only protects against calling `generateFactors()` with a null reference — it does not protect against the race where `generateOhlcv()` is called a second time mid-way through `generateFactors()`.
**Fix:** Remove the instance field entirely. Pass the market excess returns as a method parameter or as part of a return value:
```java
public record OhlcvResult(
    List<List<OhlcvRow>> rows,
    double[] mktExcessReturns   // passed into generateFactors()
) {}

public OhlcvResult generateOhlcv(List<SecuritySpec> specs, LocalDate startDate) { ... }

public List<FactorRow> generateFactors(double[] mktExcessReturns, LocalDate startDate) { ... }
```
Then in `SeedRunner`:
```java
GbmGenerator.OhlcvResult ohlcvResult = gbmGenerator.generateOhlcv(specs, SERIES_START);
List<GbmGenerator.FactorRow> factorRows =
    gbmGenerator.generateFactors(ohlcvResult.mktExcessReturns(), SERIES_START);
```
This makes the component stateless and safe for any number of callers.

---

### CR-03: `AbstractPostgresIntegrationTest` runs `CREATE EXTENSION` as the wrong superuser — silently succeeds by accident

**File:** `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java:48-51`
**Issue:** The `execInContainer` call runs `psql -U quantlens -d quantlens_test`. The `quantlens` user is not a PostgreSQL superuser, so `CREATE EXTENSION IF NOT EXISTS vector` requires the `CREATE` privilege on the database **plus** the extension being pre-installed. In the `pgvector/pgvector:pg16` image the extension is pre-installed in the `$SHAREDIR/extension` directory, so the command will succeed — but only by relying on an undocumented image behavior. If the image is ever changed, or if a CI environment uses a plain `postgres:16` image without pgvector bundled, this will silently fail (the `execInContainer` result is not checked) and every test that touches `vector_store` will fail with a cryptic Flyway error rather than a clear "extension not found" message.

Additionally, the `execInContainer` return value (`ExecResult`) is discarded — there is no check that the psql command returned exit code 0.
**Fix:** Run the extension creation as the `postgres` superuser (which the Testcontainers `PostgreSQLContainer` always creates), and check the result:
```java
@BeforeAll
static void initExtensions() throws Exception {
    Container.ExecResult result = POSTGRES.execInContainer(
        "psql", "-U", "postgres", "-d", "quantlens_test",
        "-c", "CREATE EXTENSION IF NOT EXISTS vector; CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";"
    );
    if (result.getExitCode() != 0) {
        throw new IllegalStateException(
            "Failed to create extensions: " + result.getStderr());
    }
}
```

---

### CR-04: `SecurityConfig` JSON success handler builds JSON by string concatenation — username XSS / JSON injection

**File:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java:142`
**Issue:** The `jsonSuccessHandler` writes:
```java
response.getWriter().write("{\"authenticated\":true,\"username\":\"" + username + "\"}");
```
The `username` is taken directly from `authentication.getName()`. If a username ever contains a double-quote, backslash, or newline character, the JSON response will be malformed or injectable. For example, a username of `admin","role":"admin` would produce the string `{"authenticated":true,"username":"admin","role":"admin"}`. While the current seeded usernames are safe, the `app_users` table schema accepts any `VARCHAR(50)` value, and future phases may add user registration. This is a JSON injection vulnerability.
**Fix:** Escape the username using Jackson (already on the classpath via Spring Boot):
```java
import com.fasterxml.jackson.databind.ObjectMapper;

// In SecurityConfig, inject or create an ObjectMapper
private static final ObjectMapper MAPPER = new ObjectMapper();

private AuthenticationSuccessHandler jsonSuccessHandler() {
    return (request, response, authentication) -> {
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        String body = MAPPER.writeValueAsString(
            Map.of("authenticated", true, "username", authentication.getName()));
        response.getWriter().write(body);
    };
}
```

---

### CR-05: nginx proxies `JSESSIONID` with `secure` flag unconditionally — breaks HTTP-only dev/demo deployments

**File:** `frontend/nginx.conf:18`
**Issue:** The directive `proxy_cookie_flags ~JSESSIONID secure samesite=lax;` unconditionally sets the `Secure` flag on the `JSESSIONID` cookie. In the `docker-compose.yml` stack the frontend is exposed on `http://localhost:5173` (plain HTTP, no TLS). When a browser receives a cookie with `Secure` over HTTP, it silently drops the cookie per RFC 6265. This means the session cookie will never be stored by the browser in the default Docker Compose setup, causing every request after login to be treated as unauthenticated — the auth flow will appear broken in any non-HTTPS deployment (all local demo scenarios).
**Fix:** Remove the `secure` flag from the nginx cookie directive for the HTTP-only development/demo scenario, or make it conditional. The `SameSite=Lax` is already configured in `application.yml` at the Spring level; the nginx-level directive is redundant and harmful here:
```nginx
location /api/ {
    proxy_pass http://backend:8080/api/;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    # Do NOT force Secure flag — the demo stack runs over HTTP
    # SameSite=Lax is already set by Spring's session.cookie.same-site: lax
}
```
If a production HTTPS deployment is added later, the `Secure` flag should be set there via Spring's `server.servlet.session.cookie.secure: true` rather than nginx rewriting.

---

### CR-06: `VectorStoreSchemaTest.embeddingColumnIsVector1536` uses wrong `atttypmod` — test passes trivially or never

**File:** `backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java:44`
**Issue:** The query checks `pa.atttypmod = 1536`. In PostgreSQL, `atttypmod` for a `vector(n)` column is **not** stored as `n` directly. The pgvector extension encodes the dimension as `n + 4` in `atttypmod` (following the same `typmod` convention as `varchar(n)` which stores `n + 4`). For `vector(1536)` the actual `atttypmod` value is `1540`, not `1536`. The current assertion will therefore always return 0 rows, causing the test to assert `count == 1` against 0 — this test **always fails** (or, if the assertion is `isEqualTo(1L)` and returns 0, it will be a test failure that was presumably missed during the scaffold phase).

Verify the correct value:
```sql
SELECT pa.atttypmod FROM pg_attribute pa
JOIN pg_class pc ON pa.attrelid = pc.oid
WHERE pc.relname = 'vector_store' AND pa.attname = 'embedding';
-- Returns 1540 for vector(1536)
```
**Fix:** Either use the correct `atttypmod` value, or query the human-readable form:
```java
Long count = jdbcTemplate.queryForObject(
    "SELECT COUNT(*) FROM pg_attribute pa " +
    "JOIN pg_class pc ON pa.attrelid = pc.oid " +
    "JOIN pg_type pt ON pa.atttypid = pt.oid " +
    "WHERE pc.relname = 'vector_store' " +
    "  AND pa.attname = 'embedding' " +
    "  AND pt.typname = 'vector' " +
    "  AND (pa.atttypmod - 4) = 1536",  // pgvector stores dim+4
    Long.class);
```

---

### CR-07: `positions` table missing `ON DELETE CASCADE` — foreign key to `portfolios` causes constraint violation on portfolio deletion

**File:** `backend/src/main/resources/db/migration/V1__schema.sql:62-69`
**Issue:** The `positions` table has:
```sql
portfolio_id BIGINT NOT NULL REFERENCES portfolios(id)
```
There is no `ON DELETE CASCADE` or `ON DELETE SET NULL`. The same is true for the `transactions` table (line 74). If a portfolio row is deleted — which will occur in future phases when user management is added, or in tests that clean up data — the database will throw a foreign key constraint violation rather than cascading the delete. The `portfolios` table itself has the same issue with `app_users(id)` (line 56). This is a data integrity gap that will manifest as a runtime error the first time any code attempts to delete a user or portfolio.

The `ohlcv_bars` → `securities` reference (line 21) has the same missing cascade, meaning deleting a security will fail if it has price data.
**Fix:** Add explicit `ON DELETE CASCADE` to all dependent references, or `ON DELETE RESTRICT` if intentional (but then document it and add application-layer guards):
```sql
-- positions
portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
security_id  BIGINT NOT NULL REFERENCES securities(id) ON DELETE RESTRICT,

-- transactions
portfolio_id BIGINT NOT NULL REFERENCES portfolios(id) ON DELETE CASCADE,
security_id  BIGINT NOT NULL REFERENCES securities(id) ON DELETE RESTRICT,

-- portfolios
user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,

-- ohlcv_bars
security_id BIGINT NOT NULL REFERENCES securities(id) ON DELETE CASCADE,
```

---

## Warnings

### WR-01: `Security.isIsBenchmark()` accessor — double-`is` prefix from boolean field naming

**File:** `backend/src/main/java/com/quantlens/marketdata/domain/Security.java:62`
**Issue:** The field is declared as `private boolean isBenchmark` with the `is` prefix. Java Bean convention generates a getter named `isIsBenchmark()`, which is semantically redundant and will surface in Jackson serialization as the JSON key `isBenchmark` (Jackson strips the `is` prefix), while JPA/Hibernate will also use the accessor. This creates a mismatch: the database column is `is_benchmark`, the Java field is `isBenchmark`, and the generated accessor is `isIsBenchmark()`. While Jackson handles this correctly by convention, it is a code smell that will confuse future developers and cause the wrong method name to appear in repository derived queries (Spring Data will generate `findByIsBenchmarkTrue()` from the field name, not the accessor — this actually works, but the accessor name is still confusing).
**Fix:** Rename the field to drop the `is` prefix:
```java
@Column(name = "is_benchmark", nullable = false)
private boolean benchmark = false;

public boolean isBenchmark() { return benchmark; }
public void setBenchmark(boolean benchmark) { this.benchmark = benchmark; }
```
And update `SecurityRepository.findByIsBenchmarkTrue()` to `findByBenchmarkTrue()`.

---

### WR-02: `AppUser.passwordHash` column length 100 too short for BCrypt output with higher cost factors

**File:** `backend/src/main/java/com/quantlens/portfolio/domain/AppUser.java:27`
**Also:** `backend/src/main/resources/db/migration/V1__schema.sql:49`
**Issue:** `password_hash VARCHAR(100)` and the corresponding JPA `@Column(length = 100)`. BCrypt output at the default strength (10 rounds) is exactly 60 characters. However, BCrypt with strength 12 or higher still outputs 60 characters — this is fine for BCrypt specifically. The risk is that if the `PasswordEncoderConfig` is ever changed to a different algorithm (e.g., `Argon2PasswordEncoder` or `SCryptPasswordEncoder`), the encoded output can exceed 100 characters — Argon2 output is typically 95+ characters and can be longer with custom parameters. A 100-character column will cause a `DataIntegrityViolationException` on first login attempt in that scenario. The Spring Security reference recommends 256 characters for future-proofing.
**Fix:** Increase to 255 or 256 characters in both the JPA entity and the Flyway migration:
```sql
password_hash VARCHAR(255) NOT NULL,
```
```java
@Column(name = "password_hash", nullable = false, length = 255)
```

---

### WR-03: `SeedRunner` uses `findAll()` on `appUserRepository` indirectly via `AuthController.personas()` — unbounded query on a public endpoint

**File:** `backend/src/main/java/com/quantlens/security/api/AuthController.java:57`
**Issue:** `personas()` calls `appUserRepository.findAll()` with no pagination and no upper bound. For a demo app with 3 users this is harmless, but since `GET /api/auth/personas` is a **public** endpoint (no authentication required, listed in `permitAll()`), any anonymous user can trigger a full table scan on `app_users`. In future phases when the user table grows (or if the endpoint is reused for non-demo scenarios), this becomes an unguarded amplification vector. It also leaks the count and usernames of all users in the system to unauthenticated callers.
**Fix:** Add an explicit limit query or use a dedicated projection that only returns the three intended demo personas:
```java
// Option A: limit via a derived query
List<AppUser> findTop10ByOrderByIdAsc();

// Option B: the endpoint is inherently bounded for demo; document this constraint
// and add a server-side page size cap when Phase 5 adds real user management
```

---

### WR-04: `AuthController.me()` performs two separate database round-trips in the same request — no `@Transactional`

**File:** `backend/src/main/java/com/quantlens/security/api/AuthController.java:80-86`
**Issue:** The `me()` method calls `appUserRepository.findByUsername(username)` and then, inside the `map()` lambda, calls `portfolioRepository.findByUserId(user.getId())`. These are two separate transactions with a time gap between them. In a concurrent scenario — unlikely with 3 demo users but architecturally wrong — a portfolio could be deleted between the first and second query, causing `portfolios.isEmpty()` to be true even though the user has a portfolio, and the endpoint would return `portfolioId: null` for a user who has a portfolio. The method should be `@Transactional(readOnly = true)` to ensure both reads see a consistent snapshot.
**Fix:**
```java
@GetMapping("/me")
@Transactional(readOnly = true)
public ResponseEntity<MeDto> me(Authentication authentication) { ... }
```

---

### WR-05: `GbmGenerator.nextTradingDay()` is called unconditionally on `startDate` — first bar date can land on a weekend

**File:** `backend/src/main/java/com/quantlens/seed/GbmGenerator.java:249-254`
**Issue:** `generateOhlcv()` initializes `LocalDate date = startDate` (line 151) and adds the first bar with that date, then advances via `nextTradingDay(date)` for subsequent bars. `nextTradingDay()` advances to the **next** day that is Mon-Fri — it does not validate that `startDate` itself is a weekday. `SERIES_START = LocalDate.of(2022, 9, 12)` is a Monday, so this is currently safe. However, `startDate` is a public parameter — if any future caller passes a Saturday or Sunday, bar index 0 will have a weekend date, violating the assumption that all `bar_date` values are trading days, and potentially conflicting with the `UNIQUE (security_id, bar_date)` constraint if the same weekend date is passed in two different calls.
**Fix:** Validate or normalize `startDate` at the top of `generateOhlcv()`:
```java
// Normalize startDate to a trading day
LocalDate tradingStart = startDate;
while (tradingStart.getDayOfWeek().getValue() > 5) {
    tradingStart = tradingStart.plusDays(1);
}
LocalDate date = tradingStart;
```

---

### WR-06: `SeedRunner.seedPortfolioPositions()` sets `avgCostBasis` to the buy price at bar 50, ignoring the partial sell — cost basis is wrong after SELL

**File:** `backend/src/main/java/com/quantlens/seed/SeedRunner.java:305-306`
**Issue:** After the partial SELL transaction is recorded, the remaining position is saved with `buyPrice` (bar 50 close) as the `avgCostBasis`. This is arithmetically correct only if the sell does not change the cost basis — which is true for FIFO or average-cost methods where a sell does not alter the remaining average. However, the `avgCostBasis` field is semantically "average cost of the **remaining** shares." If future analytics compute realized P&L as `(sell price - avgCostBasis) * sellQty`, they will get the right answer. But if they compute unrealized P&L as `(current price - avgCostBasis) * qty`, they also get the right answer. So the logic is not wrong per se — but the variable name `buyPrice` used as cost basis after a partial sell will confuse future readers. More importantly, the `qty` variable is mutated by the SELL branch (`qty -= sellQty`) but `buyPrice` is never updated to reflect any weighted-average recalculation. As long as cost basis is average-of-buys-only (standard), this is fine — but it is undocumented.

The actual bug here is that `qty` is a `double` used for financial share quantities, and `Math.floor(qty * 0.3)` uses floating-point arithmetic to compute a sell quantity, which is then converted to `BigDecimal`. For small `qty` values this can produce a `double` with a fractional bit that `BigDecimal.valueOf()` rounds correctly — but the intermediate `double` computation is unnecessary and could produce unexpected results at larger quantities. Use `BigDecimal.valueOf(qty).multiply(new BigDecimal("0.3"))` throughout.
**Fix:**
```java
// Use BigDecimal arithmetic for the sell quantity
BigDecimal bdQty = BigDecimal.valueOf(shares[i]);
BigDecimal sellQty = bdQty.multiply(new BigDecimal("0.3"))
    .setScale(0, RoundingMode.FLOOR);
if (sellQty.compareTo(BigDecimal.ONE) >= 0) {
    // ...
    BigDecimal remainingQty = bdQty.subtract(sellQty)
        .setScale(4, RoundingMode.HALF_UP);
    positionRepository.save(new Position(portfolio, sec, remainingQty, buyPrice));
}
```

---

### WR-07: `PersonaIntegrationTest.extractJsonField()` naive JSON parser silently truncates numeric values ending in `}`

**File:** `backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java:137-139`
**Issue:** For a numeric field at the end of the JSON object (e.g., `{"portfolioId":3}`), `indexOf(',', valueStart)` returns -1, so the fallback `indexOf('}', valueStart)` is used. This returns the index of `}`, and `substring(valueStart, valueEnd)` correctly extracts `3`. However, if the JSON contains nested objects or arrays after the field (e.g., `{"portfolioId":3,"extra":{...}}`), the `indexOf('}')` finds the first `}` which may be inside a nested value, not the field boundary. This is a fragile parser that can return wrong values silently. Given the test only needs to compare portfolio IDs for inequality (`isNotEqualTo`), a wrong but consistent parse would not catch a real failure.

The comment already acknowledges the fragility ("Naive JSON field extraction"). The fix is straightforward since Jackson `ObjectMapper` is available in the test classpath.
**Fix:** Replace with Jackson:
```java
private String extractJsonField(String json, String fieldName) throws Exception {
    com.fasterxml.jackson.databind.JsonNode node =
        new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
    return node.path(fieldName).asText();
}
```

---

### WR-08: `docker-compose.yml` frontend service does not wait for `backend` to be healthy — race condition on cold start

**File:** `docker-compose.yml:55-62`
**Issue:** The `frontend` service declares `depends_on: - backend` (simple dependency, no `condition`), while `backend` has `depends_on: db: condition: service_healthy`. The `backend` container has a `HEALTHCHECK` with a 60-second `start_period`. The `frontend` (nginx) starts as soon as the `backend` container starts — before the Spring Boot application has completed startup and before the `/actuator/health` check passes. On a cold start (first `docker compose up`), the SeedRunner executes (~10-30 seconds), during which any browser request to the Vue SPA that triggers an API call will receive a connection-refused or 502 Bad Gateway from nginx. The app will appear broken until the backend finishes seeding.
**Fix:**
```yaml
frontend:
  depends_on:
    backend:
      condition: service_healthy
```
This requires no other changes since `backend` already has a `HEALTHCHECK` defined in its Dockerfile.

---

## Info

### IN-01: `application.yml` exposes default dev credentials in source control

**File:** `backend/src/main/resources/application.yml:5`
**Issue:** `password: ${SPRING_DATASOURCE_PASSWORD:quantlens_dev}` — the fallback default `quantlens_dev` is committed to source control. If the environment variable is not set (e.g., running the JAR directly without the Docker environment), the application will connect to whatever PostgreSQL instance is on `localhost:5432` with the committed password. For a demo/dev project this is low-risk, but it means any fork of this repository ships with working database credentials by default.
**Fix:** Remove the fallback default value and fail fast when the variable is unset outside Docker, or use a clearly-invalid sentinel:
```yaml
password: ${SPRING_DATASOURCE_PASSWORD}
```

---

### IN-02: `.mcp.json` hardcodes database password in plain text — committed to version control

**File:** `.mcp.json:12`
**Issue:** `"POSTGRES_CONNECTION_STRING": "postgresql://quantlens:quantlens_dev@localhost:5432/quantlens"` contains the database password in plain text committed to the repository. MCP server configs are developer tooling, but this file will be present in every clone of the repo and will show up in `git log`, PR diffs, and CI artifact archives.
**Fix:** Use an environment variable reference (supported by most MCP server implementations):
```json
"POSTGRES_CONNECTION_STRING": "${QUANTLENS_DB_URL}"
```
Or document that `.mcp.json` should be added to `.gitignore` for any deployment that uses non-default credentials.

---

### IN-03: `SeedRunner` logs the count via `ohlcvBarRepository.count()` — extra full-table COUNT query after bulk insert

**File:** `backend/src/main/java/com/quantlens/seed/SeedRunner.java:139-140`
**Issue:** `ohlcvBarRepository.count()` issues a `SELECT COUNT(*) FROM ohlcv_bars` after saving all bars. With 16 securities × 504 bars = 8,064 rows just inserted, this is a full sequential scan of the table purely for a log message. It is not wrong, but it is wasteful and adds latency to the startup path. The count could be computed from the in-memory data at zero cost.
**Fix:**
```java
int totalBars = ohlcvData.stream().mapToInt(List::size).sum();
log.info("SeedRunner: saved {} securities with {} total OHLCV bars",
    securities.size(), totalBars);
```

---

### IN-04: `LoginView.vue` hardcodes `DEMO_PASSWORD = 'demo1234'` as a frontend constant — duplicates the hint already returned by the backend

**File:** `frontend/src/views/LoginView.vue:16`
**Issue:** The `DEMO_PASSWORD` constant is used in `loginAsPersona()` and also rendered in the template hint (`Demo password: {{ DEMO_PASSWORD }}`). The backend already returns `passwordHint` in the `PersonaDto` response (which includes `"demo1234"`). The frontend therefore has two sources of truth for the demo password: the local constant and the server response. If the demo password is ever changed server-side, the one-click login will fail silently (the `loginAsPersona` function uses the local constant, not `p.passwordHint`).
**Fix:** Use the hint from the `PersonaInfo` object returned by the server:
```typescript
async function loginAsPersona(p: PersonaInfo) {
  loading.value = true
  errorMessage.value = ''
  try {
    const ok = await authStore.loginAs(p.username, p.passwordHint)  // use server-provided hint
    ...
  }
}
```
And display the hint from the first persona in the list rather than a local constant.

---

_Reviewed: 2026-06-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
