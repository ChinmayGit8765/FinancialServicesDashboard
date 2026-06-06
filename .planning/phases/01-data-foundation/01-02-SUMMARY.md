---
phase: 01-data-foundation
plan: "02"
subsystem: backend-schema-seed
tags: [flyway, pgvector, jpa, gbm, hipparchus, seed-data, testcontainers]
dependency_graph:
  requires:
    - 01-01 (Spring Boot scaffold, AbstractPostgresIntegrationTest base, @Disabled red tests)
  provides:
    - docker/db/00-init.sql (superuser CREATE EXTENSION vector + uuid-ossp)
    - backend/src/main/resources/db/migration/V1__schema.sql (all relational + vector_store(1536)/HNSW)
    - JPA entities: Security, OhlcvBar, FactorReturn, AppUser, Portfolio, Position, Transaction, SeedLog
    - Spring Data repositories for all entities
    - GbmGenerator (MersenneTwister(42), Ito-corrected, BigDecimal-safe)
    - SeedRunner (@Order(1) ApplicationRunner @Transactional, seed_log idempotence guard)
    - PasswordEncoderConfig (single BCryptPasswordEncoder @Bean)
    - Modulith named-interfaces: marketdata::domain, portfolio::domain
  affects:
    - Plan 01-03 (SecurityConfig autowires PasswordEncoderConfig bean)
    - All downstream phases (data layer is seeded and schema is locked)
tech_stack:
  added:
    - org.hipparchus:hipparchus-core:4.0.3
    - org.hipparchus:hipparchus-stat:4.0.3
  patterns:
    - "Flyway V1 owns vector_store DDL — initialize-schema=false in all profiles"
    - "Static-init Testcontainers container (no @Testcontainers) prevents cross-class lifecycle stop"
    - "Spring Modulith @NamedInterface for sub-package API exposure"
    - "seed_log completed flag set at END of @Transactional run (partial-seed rollback safety)"
    - "BigDecimal.valueOf(d).setScale(6, HALF_UP) — never new BigDecimal(double)"
    - "MersenneTwister.nextGaussian() for N(0,1) draws in Hipparchus 4.x"
key_files:
  created:
    - docker/db/00-init.sql
    - backend/src/main/resources/db/migration/V1__schema.sql
    - backend/src/main/java/com/quantlens/marketdata/domain/Security.java
    - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBar.java
    - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturn.java
    - backend/src/main/java/com/quantlens/marketdata/domain/SecurityRepository.java
    - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java
    - backend/src/main/java/com/quantlens/marketdata/domain/FactorReturnRepository.java
    - backend/src/main/java/com/quantlens/marketdata/domain/package-info.java
    - backend/src/main/java/com/quantlens/portfolio/domain/AppUser.java
    - backend/src/main/java/com/quantlens/portfolio/domain/Portfolio.java
    - backend/src/main/java/com/quantlens/portfolio/domain/Position.java
    - backend/src/main/java/com/quantlens/portfolio/domain/Transaction.java
    - backend/src/main/java/com/quantlens/portfolio/domain/AppUserRepository.java
    - backend/src/main/java/com/quantlens/portfolio/domain/PortfolioRepository.java
    - backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java
    - backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java
    - backend/src/main/java/com/quantlens/portfolio/domain/package-info.java
    - backend/src/main/java/com/quantlens/seed/PasswordEncoderConfig.java
    - backend/src/main/java/com/quantlens/seed/SeedLog.java
    - backend/src/main/java/com/quantlens/seed/SeedLogRepository.java
    - backend/src/main/java/com/quantlens/seed/GbmGenerator.java
    - backend/src/main/java/com/quantlens/seed/SeedRunner.java
  modified:
    - backend/pom.xml (hipparchus-core + hipparchus-stat 4.0.3)
    - backend/src/main/java/com/quantlens/portfolio/package-info.java (allowedDependencies)
    - backend/src/main/java/com/quantlens/seed/package-info.java (allowedDependencies)
    - backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java (static-init container)
    - backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java (@Disabled removed)
    - backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java (@Disabled removed)
decisions:
  - "GbmGenerator uses MersenneTwister.nextGaussian() directly (Hipparchus 4.x removed NormalDistribution(RandomGenerator, mu, sigma) constructor and sample() method)"
  - "AbstractPostgresIntegrationTest uses static-init .start() instead of @Testcontainers/@Container to prevent JUnit 5 stopping the shared container between test classes"
  - "marketdata.domain and portfolio.domain exposed as @NamedInterface so portfolio + seed modules can reference entity types without Modulith violation"
  - "testcontainers.reuse.enable=true added to ~/.testcontainers.properties (local dev only)"
  - "SeedRunner seeds 16 securities (15 equities + SPX500 benchmark) = 8064 OHLCV bars (504 days x 16)"
metrics:
  duration: "~35 minutes"
  completed_date: "2026-06-07"
  tasks_completed: 2
  files_created: 23
  files_modified: 6
---

# Phase 01 Plan 02: Flyway Schema + pgvector + GBM Seeder + PasswordEncoder Summary

**One-liner:** Flyway V1 migration owns all relational tables + vector_store(1536)/HNSW; correlated GBM seeder (MersenneTwister seed 42) populates 16 securities with 8064 OHLCV bars, 504 factor rows, and 3 BCrypt-hashed demo personas; single PasswordEncoder bean declared.

## What Was Built

### Task 1: Schema, Init Script, JPA Entities, Repositories

1. **docker/db/00-init.sql** — Superuser `CREATE EXTENSION IF NOT EXISTS vector` + `uuid-ossp`, mounted to `/docker-entrypoint-initdb.d/` in Docker Compose so extensions are available before Flyway runs.

2. **V1__schema.sql** — Single Flyway migration owning:
   - `securities` (id, ticker, name, sector, is_benchmark)
   - `ohlcv_bars` (NUMERIC(18,6) prices, BIGINT volume, idx_ohlcv_security_date)
   - `factor_returns` (mkt_rf, smb, hml, rf — NUMERIC(10,6))
   - `app_users` (username, password_hash, persona, external_id nullable)
   - `portfolios`, `positions` (NUMERIC(18,4) qty, NUMERIC(18,6) cost_basis), `transactions`
   - `seed_log` (id VARCHAR PK, completed BOOLEAN, completed_at TIMESTAMP)
   - `vector_store` (`embedding vector(1536)`) + HNSW index `spring_ai_vector_index`
   - `initialize-schema: false` confirmed in both `application.yml` and `application-test.yml`

3. **JPA entities** — All price/money fields `BigDecimal`, all date fields `LocalDate`. No `double`/`float` for financial columns.

4. **Spring Data repositories** — SecurityRepository (findByTicker, findByIsBenchmarkTrue), OhlcvBarRepository, FactorReturnRepository, AppUserRepository (findByUsername), PortfolioRepository (findByUserId), PositionRepository, TransactionRepository.

### Task 2: PasswordEncoder + GBM Generator + SeedRunner

5. **PasswordEncoderConfig** — Single `@Configuration` `@Bean PasswordEncoder` returning `new BCryptPasswordEncoder()`. Plan 03's SecurityConfig will autowire this bean — it must not redefine it.

6. **SeedLog + SeedLogRepository** — Idempotence guard entity; `SeedRunner` sets `completed=true` at the **end** of its `@Transactional` run so a partial seed rolls back cleanly.

7. **GbmGenerator** — `MersenneTwister(42)` + `nextGaussian()` (Hipparchus 4.x API). Market-factor draws first (deterministic RNG order), then per-security idiosyncratic draws. Ito-corrected discretization `(mu - sigma*sigma/2)*dt`. BigDecimal conversion via `BigDecimal.valueOf(d).setScale(6, HALF_UP)` throughout.

8. **SeedRunner** — `@Component @Order(1) ApplicationRunner @Transactional`: seeds 15 equities + SPX500 benchmark (16 total), 8064 OHLCV bars, 504 Fama-French rows, 3 users (alice/bob/charlie, BCrypt-hashed `demo1234` via injected PasswordEncoder), one portfolio each with style-appropriate positions and a BUY/SELL transaction history.

## Verification Results

| Check | Command | Result |
|-------|---------|--------|
| VectorStoreSchemaTest | `./mvnw test -Dtest=VectorStoreSchemaTest` | 2/2 PASS |
| SeedRunnerIntegrationTest | `./mvnw test -Dtest=SeedRunnerIntegrationTest` | 5/5 PASS |
| Both together | `./mvnw test -Dtest=VectorStoreSchemaTest,SeedRunnerIntegrationTest` | 7/7 PASS |
| QuantLensModulithTest | `./mvnw test -Dtest=QuantLensModulithTest` | 1/1 PASS |
| All three together | `./mvnw test -Dtest=QuantLensModulithTest,VectorStoreSchemaTest,SeedRunnerIntegrationTest` | 8/8 PASS |

**Seed counts observed:** 16 securities, 8064 OHLCV bars, 504 factor_returns, 3 app_users, 3 portfolios.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Hipparchus 4.x API change: NormalDistribution constructor and sample()**
- **Found during:** Task 2 — IDE diagnostics after writing GbmGenerator
- **Issue:** `NormalDistribution(MersenneTwister, 0, 1)` constructor and `.sample()` method do not exist in Hipparchus 4.0.3. The RESEARCH.md code examples were written against an older API.
- **Fix:** Use `MersenneTwister(42).nextGaussian()` directly — `MersenneTwister` extends `IntRandomGenerator` which has `nextGaussian()` returning N(0,1) draws.
- **Files modified:** `GbmGenerator.java`
- **Commit:** 7accfcd

**2. [Rule 1 - Bug] Spring Modulith boundary violation: portfolio.domain references marketdata.domain**
- **Found during:** QuantLensModulithTest after Task 2
- **Issue:** `Position` and `Transaction` in `portfolio.domain` reference `Security` from `marketdata.domain`, which is a sub-package (internal by default in Spring Modulith). Violation: "Module 'portfolio' depends on non-exposed type Security within module 'marketdata'".
- **Fix:** Added `@NamedInterface("domain")` package-info.java in both `marketdata.domain` and `portfolio.domain`; updated `portfolio` module `allowedDependencies = {"marketdata::domain"}` and `seed` module `allowedDependencies = {"marketdata::domain", "portfolio::domain"}`.
- **Files modified:** `marketdata/domain/package-info.java` (created), `portfolio/domain/package-info.java` (created), `portfolio/package-info.java`, `seed/package-info.java`
- **Commit:** 7accfcd

**3. [Rule 1 - Bug] Testcontainers container stopped between test classes in same surefire fork**
- **Found during:** Task 2 verification with `-Dtest=VectorStoreSchemaTest,SeedRunnerIntegrationTest`
- **Issue:** JUnit 5 `@Testcontainers` + `@Container static` stops the container after each concrete test class. When VectorStoreSchemaTest finished, the container was stopped; SeedRunnerIntegrationTest found a dead HikariPool.
- **Fix:** Removed `@Testcontainers` and `@Container` from `AbstractPostgresIntegrationTest`; replaced with a `static {}` initializer block calling `POSTGRES.start()`. Container lifecycle is now managed by Testcontainers' Ryuk reaper at JVM exit — Spring's `ApplicationContext` cache is reused across both test classes.
- **Files modified:** `AbstractPostgresIntegrationTest.java`
- **Commit:** 7accfcd

### Notes

- `withReuse(true)` was tried first (requires `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`). While that property was set, the definitive fix was the static-init pattern which is independent of any per-developer config file.
- The `QuantLensApplicationModule` uses `exportedPackages` attribute which does not exist — checked via `javap` and used `@NamedInterface` instead.
- SeedRunner seeds 16 securities (15 equities + SPX500 benchmark), producing 16 * 504 = 8064 OHLCV bars (all passing the >=7000 assertion).

## Commits

| Task | Description | Hash |
|------|-------------|------|
| 1 | Flyway V1 schema + pgvector init + JPA entities/repos | 9f27cac |
| 2 | BCrypt bean + correlated GBM seeder + Modulith named-interfaces | 7accfcd |

## Known Stubs

None — all data flows are wired. The vector_store table exists with the correct schema; embeddings will be populated in Phase 7 (not a stub in this plan's scope).

## Threat Flags

None — no new network endpoints, auth paths, or schema changes beyond the plan's threat model.

## Self-Check: PASSED

Files verified to exist:
- docker/db/00-init.sql ✓
- backend/src/main/resources/db/migration/V1__schema.sql ✓
- backend/src/main/java/com/quantlens/seed/PasswordEncoderConfig.java ✓
- backend/src/main/java/com/quantlens/seed/GbmGenerator.java ✓
- backend/src/main/java/com/quantlens/seed/SeedRunner.java ✓
- backend/src/main/java/com/quantlens/seed/SeedLog.java ✓

Commits verified:
- 9f27cac: feat(01-02): Flyway V1 schema + pgvector init + JPA entities/repos ✓
- 7accfcd: feat(01-02): BCrypt bean + correlated GBM seeder + Modulith named-interfaces ✓

Tests verified:
- VectorStoreSchemaTest: 2/2 PASS ✓
- SeedRunnerIntegrationTest: 5/5 PASS ✓
- QuantLensModulithTest: 1/1 PASS ✓
