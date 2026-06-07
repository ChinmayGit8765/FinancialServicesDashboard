---
phase: 01-data-foundation
fixed_at: 2026-06-07T12:02:00Z
review_path: .planning/phases/01-data-foundation/01-REVIEW.md
iteration: 1
findings_in_scope: 15
fixed: 13
skipped: 2
status: partial
---

# Phase 01: Code Review Fix Report

**Fixed at:** 2026-06-07T12:02:00Z
**Source review:** `.planning/phases/01-data-foundation/01-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 15 (7 Critical + 8 Warning; Info excluded from scope)
- Fixed: 13
- Skipped: 2 (verified false positives)
- Final test result: **16/16 PASS** (`BUILD SUCCESS`)
- Docker Compose config check: **exit 0**

---

## Fixed Issues

### CR-01: Demo password hardcoded in plain-text

**Files modified:** `backend/src/main/java/com/quantlens/security/api/AuthController.java`, `backend/src/main/java/com/quantlens/seed/SeedRunner.java`, `backend/src/main/resources/application.yml`, `frontend/src/views/LoginView.vue`
**Commit:** `e037a06`
**Applied fix:** Moved demo password to Spring property `quantlens.demo.password` (default `demo1234`, overridable via `QUANTLENS_DEMO_PASSWORD` env var). `AuthController` and `SeedRunner` now inject it via `@Value`. Removed the `static final String DEMO_PASSWORD` constant from compiled bytecode. Also fixed IN-04 in the same commit: `LoginView.vue` now uses `p.passwordHint` from the server response instead of the local `DEMO_PASSWORD` constant, so the one-click login stays in sync with any future password change.

---

### CR-02: `GbmGenerator` stateful instance field causes concurrency data corruption

**Files modified:** `backend/src/main/java/com/quantlens/seed/GbmGenerator.java`, `backend/src/main/java/com/quantlens/seed/SeedRunner.java`
**Commit:** `bd66ea7`
**Applied fix:** Removed the `lastMktExcessReturns` instance field. Introduced a new `OhlcvResult` record that bundles the OHLCV row lists and the market excess-return array. `generateOhlcv()` now returns `OhlcvResult`; `generateFactors()` now accepts `double[] mktExcessReturns` as a parameter. `SeedRunner` unpacks the result and passes `ohlcvResult.mktExcessReturns()` directly into `generateFactors()`. The component is now stateless and safe for concurrent callers.

---

### CR-03: `AbstractPostgresIntegrationTest` discards `execInContainer` result — silent failure if extension creation fails

**Files modified:** `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java`
**Commit:** `c362339`
**Applied fix:** Added `ExecResult` capture and exit-code check with a descriptive `IllegalStateException` on failure. The user is kept as `quantlens` (not `postgres`) because in the `pgvector/pgvector:pg16` Testcontainers image, specifying `POSTGRES_USER=quantlens` creates `quantlens` as the database superuser — the `postgres` role does not exist. Switching to `-U postgres` (as suggested in the review) caused all 5 test classes to fail with `FATAL: role "postgres" does not exist`, which was caught and corrected. The partial suggestion (exit code check) was applied; the user-change suggestion was reverted as a false positive. See "Skipped Issues" for the false positive note.

---

### CR-04: JSON injection in `SecurityConfig` login success handler

**Files modified:** `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java`
**Commit:** `106d583`
**Applied fix:** Added `import com.fasterxml.jackson.databind.ObjectMapper` and `import java.util.Map`. Added `private static final ObjectMapper MAPPER = new ObjectMapper()`. Replaced the string-concatenation JSON response with `MAPPER.writeValueAsString(Map.of("authenticated", true, "username", authentication.getName()))`. This prevents JSON injection if a username ever contains special characters.

---

### CR-05: nginx forces `Secure` flag on `JSESSIONID` — breaks HTTP-only demo login

**Files modified:** `frontend/nginx.conf`
**Commit:** `a217859`
**Applied fix:** Removed `proxy_cookie_flags ~JSESSIONID secure samesite=lax;`. Added explanatory comment documenting why the `Secure` flag must not be forced for an HTTP-only demo stack. `SameSite=Lax` is already set by Spring at `server.servlet.session.cookie.same-site: lax` — the nginx directive was both redundant and harmful.

---

### CR-07: Foreign key constraints missing `ON DELETE CASCADE` — portfolio/security deletes throw FK violations

**Files modified:** `backend/src/main/resources/db/migration/V2__add_fk_cascade.sql` (new file)
**Commit:** `3df3ae0`
**Applied fix:** Created Flyway migration V2 that drops and re-adds the FK constraints on `portfolios.user_id`, `positions.portfolio_id`, `positions.security_id`, `transactions.portfolio_id`, `transactions.security_id`, and `ohlcv_bars.security_id` with appropriate `ON DELETE CASCADE` (for parent→child deletions) and `ON DELETE RESTRICT` (for security references in positions/transactions where data should be preserved). Flyway applied all 3 migrations cleanly in the test run.

---

### WR-01: `Security.isIsBenchmark()` double-`is` accessor from boolean field naming

**Files modified:** `backend/src/main/java/com/quantlens/marketdata/domain/Security.java`, `backend/src/main/java/com/quantlens/marketdata/domain/SecurityRepository.java`
**Commit:** `108086f`
**Applied fix:** Renamed field from `isBenchmark` to `benchmark`. Updated accessor to `isBenchmark()` / `setBenchmark()`. Updated `SecurityRepository.findByIsBenchmarkTrue()` to `findByBenchmarkTrue()`. Verified no other callers of the old accessor existed in the codebase.

---

### WR-02: `AppUser.passwordHash` column length 100 too short for future password encoders

**Files modified:** `backend/src/main/java/com/quantlens/portfolio/domain/AppUser.java`, `backend/src/main/resources/db/migration/V3__widen_password_hash.sql` (new file)
**Commit:** `48b64eb`
**Applied fix:** Updated JPA `@Column(length = 255)` on `passwordHash`. Created Flyway migration V3 that runs `ALTER TABLE app_users ALTER COLUMN password_hash TYPE VARCHAR(255)`.

---

### WR-03: `AuthController.personas()` uses unbounded `findAll()` on a public endpoint

**Files modified:** `backend/src/main/java/com/quantlens/portfolio/domain/AppUserRepository.java`, `backend/src/main/java/com/quantlens/security/api/AuthController.java`
**Commit:** `75cc806`
**Applied fix:** Added `findTop10ByOrderByIdAsc()` to `AppUserRepository` with Javadoc explaining the cap. Updated `personas()` to call the bounded query. This prevents a full table scan on the unauthenticated endpoint as the user table grows.

---

### WR-04: `AuthController.me()` performs two separate database round-trips — no `@Transactional`

**Files modified:** `backend/src/main/java/com/quantlens/security/api/AuthController.java`
**Commit:** `75cc806` (same commit as WR-03)
**Applied fix:** Added `@Transactional(readOnly = true)` to the `me()` method. Both the user lookup and the portfolio lookup now execute within a single read-only transaction, ensuring a consistent snapshot.

---

### WR-05: `GbmGenerator.nextTradingDay()` not applied to `startDate` — first bar can land on a weekend

**Files modified:** `backend/src/main/java/com/quantlens/seed/GbmGenerator.java`
**Commit:** `b83b89a`
**Applied fix:** Added a normalization loop at the top of both `generateOhlcv()` and `generateFactors()` that advances `startDate` past any weekend before generating bars. The current `SERIES_START` (2022-09-12, a Monday) is unaffected; the fix protects future callers who pass a weekend date.

---

### WR-06: `SeedRunner` uses `Math.floor(double)` for sell quantity — floating-point arithmetic

**Files modified:** `backend/src/main/java/com/quantlens/seed/SeedRunner.java`
**Commit:** `d9b5ba2`
**Applied fix:** Replaced `double qty` / `Math.floor(qty * 0.3)` with `BigDecimal.valueOf(shares[i])` / `.multiply(new BigDecimal("0.3")).setScale(0, RoundingMode.FLOOR)`. All quantity arithmetic in `seedPortfolioPositions()` now uses `BigDecimal` throughout. The `double qty` variable was eliminated; `bdQty` handles both the BUY quantity and the remaining-after-sell quantity.

---

### WR-07: `PersonaIntegrationTest.extractJsonField()` naive JSON parser

**Files modified:** `backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java`
**Commit:** `993f1dd`
**Applied fix:** Replaced the hand-rolled string parser with `new ObjectMapper().readTree(json).path(fieldName).asText()`. All four `@Test` methods and both private helpers updated to declare `throws Exception` since `readTree` is checked. The test now handles nested JSON and numeric fields robustly.

---

### WR-08: `docker-compose.yml` frontend starts before backend is healthy

**Files modified:** `docker-compose.yml`
**Commit:** `0c7d138`
**Applied fix:** Changed `frontend.depends_on` from a simple `- backend` list entry to a map entry with `condition: service_healthy`. The `backend` service already has a `HEALTHCHECK` in its `Dockerfile` (`/actuator/health`, `start_period: 60s`), so no additional changes were needed.

---

## Skipped Issues

### CR-06: `VectorStoreSchemaTest.embeddingColumnIsVector1536` — wrong `atttypmod`

**File:** `backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java:44`
**Reason:** Verified false positive — test passes as written. The test uses `pt.typname = 'vector'` in addition to `atttypmod = 1536`. Running the full suite (16/16 pass) confirms `embeddingColumnIsVector1536` is green. In the `pgvector/pgvector:pg16` image, `atttypmod` for `vector(1536)` is stored as `1536` (not 1540 as the review claimed). Test left unchanged; REVIEW.md annotated.

---

### CR-03 (partial): Reviewer suggested using `-U postgres` superuser

**File:** `backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java:48`
**Reason:** Partially a false positive for the user-change suggestion. The `pgvector/pgvector:pg16` image with `POSTGRES_USER=quantlens` does NOT create a `postgres` role — `quantlens` is the only superuser. Switching to `-U postgres` broke all 5 test classes with `FATAL: role "postgres" does not exist`. The valuable part of CR-03 (checking the `ExecResult` exit code) was applied; the username was reverted to `quantlens`. REVIEW.md annotated.

---

_Fixed: 2026-06-07T12:02:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
