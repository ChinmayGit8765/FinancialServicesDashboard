---
phase: 01-data-foundation
plan: "01"
subsystem: backend-scaffold
tags: [spring-boot, spring-modulith, spring-ai, testcontainers, pgvector, java21]
dependency_graph:
  requires: []
  provides:
    - backend/pom.xml (Spring Boot 3.5.13, Spring AI BOM 1.1.6, Spring Modulith BOM 1.4.11)
    - com.quantlens base package with Modulith module packages
    - application.yml + application-test.yml
    - Six Wave 0 test scaffolds (Testcontainers pgvector base + red integration tests)
  affects:
    - All downstream plans in Phase 1 (schema/seed, auth, compose/frontend/MCP)
tech_stack:
  added:
    - Spring Boot 3.5.13
    - Spring AI BOM 1.1.6 (dependency management only, no starters)
    - Spring Modulith BOM 1.4.11
    - spring-modulith-starter-core
    - spring-modulith-starter-test (test)
    - spring-boot-testcontainers (test)
    - org.testcontainers:postgresql (test)
    - org.testcontainers:junit-jupiter (test)
    - flyway-database-postgresql
  patterns:
    - "@Modulithic @SpringBootApplication entry point"
    - "@ApplicationModule package-info.java per Modulith module"
    - "Testcontainers @ServiceConnection for integration tests"
    - "AbstractPostgresIntegrationTest shared base class"
key_files:
  created:
    - backend/pom.xml
    - backend/mvnw
    - backend/mvnw.cmd
    - backend/.mvn/wrapper/maven-wrapper.properties
    - backend/src/main/java/com/quantlens/QuantLensApplication.java
    - backend/src/main/java/com/quantlens/marketdata/package-info.java
    - backend/src/main/java/com/quantlens/portfolio/package-info.java
    - backend/src/main/java/com/quantlens/security/package-info.java
    - backend/src/main/java/com/quantlens/seed/package-info.java
    - backend/src/main/resources/application.yml
    - backend/src/test/resources/application-test.yml
    - backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java
    - backend/src/test/java/com/quantlens/QuantLensModulithTest.java
    - backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java
    - backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java
    - backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java
    - backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java
  modified: []
decisions:
  - "initialize-schema: false — Flyway owns vector_store DDL; Spring AI will not auto-create the table (prevents DDL divergence in all future phases)"
  - "Spring Modulith BOM 1.4.11 used (plan specified 1.4.11, not RESEARCH.md's assumed 1.3.5)"
  - "QuantLensModulithTest does not require Spring context or DB — fast module boundary check (<15s)"
  - "AbstractPostgresIntegrationTest uses @BeforeAll execInContainer to create vector+uuid-ossp extensions (superuser required)"
metrics:
  duration: "7 minutes"
  completed_date: "2026-06-07"
  tasks_completed: 3
  files_created: 17
  files_modified: 0
---

# Phase 01 Plan 01: Backend Scaffold + Wave 0 Test Scaffolds Summary

**One-liner:** Spring Boot 3.5.13 modular monolith scaffold with Spring AI BOM 1.1.6 + Spring Modulith 1.4.11 pinned, four Modulith package boundaries declared, and six Wave 0 Testcontainers integration test scaffolds (one green, five @Disabled-red).

## What Was Built

This plan establishes the walking skeleton of the QuantLens backend:

1. **Spring Boot 3.5.13 project** generated via Spring Initializr with: web, security, data-jpa, flyway, postgresql, validation, actuator.

2. **pom.xml pinned BOMs:**
   - Spring AI BOM 1.1.6 in `dependencyManagement` (no starters — BOM-only this phase)
   - Spring Modulith BOM 1.4.11 in `dependencyManagement`
   - Added: spring-modulith-starter-core, spring-modulith-starter-test, spring-boot-testcontainers, org.testcontainers:postgresql, org.testcontainers:junit-jupiter

3. **QuantLensApplication.java** annotated with both `@Modulithic` and `@SpringBootApplication`.

4. **Four Modulith module packages** with `@ApplicationModule` package-info.java:
   - `com.quantlens.marketdata` — Market Data module
   - `com.quantlens.portfolio` — Portfolio module
   - `com.quantlens.security` — Security module
   - `com.quantlens.seed` — Seed module
   - No analytics/ai/mcp packages (deferred to later phases)

5. **application.yml** with:
   - Datasource via `${SPRING_DATASOURCE_*}` env vars (defaults to localhost/quantlens)
   - `ddl-auto: validate`, `open-in-view: false`
   - Flyway enabled pointing at `classpath:db/migration`
   - pgvector: `initialize-schema: false`, `dimensions: 1536`, `COSINE_DISTANCE`, `HNSW`
   - `spring.ai.chat.client.enabled: false`
   - Session cookie `same-site: lax`, `session-tracking-modes: cookie`
   - Management: health only, `show-details: never`

6. **application-test.yml** — same pgvector block; no hardcoded jdbc URL (Testcontainers @ServiceConnection provides datasource at runtime).

7. **Six Wave 0 test scaffolds:**
   - `AbstractPostgresIntegrationTest` — Testcontainers base using `pgvector/pgvector:pg16` + `@ServiceConnection`; creates vector + uuid-ossp extensions via `execInContainer` in `@BeforeAll`
   - `QuantLensModulithTest` — `ApplicationModules.of(QuantLensApplication.class).verify()` — **GREEN**
   - `SeedRunnerIntegrationTest` — asserts seed counts (>=15 securities, >=7000 ohlcv_bars, 3 users, >=500 factors) and idempotence — **@Disabled RED until Plan 02**
   - `VectorStoreSchemaTest` — asserts vector_store table with embedding vector(1536) — **@Disabled RED until Plan 02**
   - `AuthIntegrationTest` — asserts login 200/401 JSON and session persistence — **@Disabled RED until Plan 03**
   - `PersonaIntegrationTest` — asserts alice/bob/charlie have distinct portfolios and correct personas (Growth/Income/Balanced) — **@Disabled RED until Plan 03**

## Verification Results

| Check | Command | Result |
|-------|---------|--------|
| test-compile exits 0 | `./mvnw -q test-compile` (JAVA_HOME=Temurin 21) | PASS |
| QuantLensModulithTest passes | `./mvnw test -Dtest=QuantLensModulithTest` | BUILD SUCCESS (12s) |
| spring-ai-bom 1.1.6 present | grep pom.xml | FOUND |
| spring-modulith-bom 1.4.11 present | grep pom.xml | FOUND |
| No spring-ai-starter-* | grep pom.xml | NONE FOUND |
| Only 4 module packages | ls com/quantlens | marketdata, portfolio, security, seed only |
| initialize-schema: false | grep application.yml | CONFIRMED |
| All 6 test files compile | `./mvnw -q test-compile` | PASS |

## Deviations from Plan

### Auto-fixed Issues

None — plan executed exactly as written.

### Notes

- **Spring Modulith version:** RESEARCH.md referenced 1.3.5 as `[ASSUMED]`. PLAN.md locks 1.4.11 — used 1.4.11 per plan instruction.
- **initialize-schema:** CONTEXT.md references `initialize-schema: true` (an older decision), but PLAN.md explicitly locks `false` with the comment "Flyway V1 owns the vector_store DDL". Used `false` per PLAN.md — this is the correct disposition for a Flyway-managed schema.
- **Checkpoint auto-approved:** Autonomous run with `auto_advance: true`. All verification checks passed (BUILD SUCCESS for QuantLensModulithTest, all six files compile, pom.xml contents validated).

## Commits

| Task | Description | Hash |
|------|-------------|------|
| 1 | Spring Initializr scaffold + pinned BOMs + @Modulithic | e1828b4 |
| 2 | Modulith module packages + application/test profiles | 9dc6492 |
| 3 | Wave 0 test scaffolds (TDD RED gate + QuantLensModulithTest GREEN) | a337a6f |

## Known Stubs

None — this plan creates infrastructure scaffolds only. No data flows to UI rendering in this plan.

## Threat Flags

None — this plan creates no new network endpoints, auth paths, file access patterns, or schema changes at trust boundaries beyond what the threat model already captures.

## Self-Check: PASSED

Files verified to exist:
- backend/pom.xml ✓
- backend/src/main/java/com/quantlens/QuantLensApplication.java ✓
- backend/src/main/java/com/quantlens/marketdata/package-info.java ✓
- backend/src/main/java/com/quantlens/portfolio/package-info.java ✓
- backend/src/main/java/com/quantlens/security/package-info.java ✓
- backend/src/main/java/com/quantlens/seed/package-info.java ✓
- backend/src/main/resources/application.yml ✓
- backend/src/test/resources/application-test.yml ✓
- backend/src/test/java/com/quantlens/AbstractPostgresIntegrationTest.java ✓
- backend/src/test/java/com/quantlens/QuantLensModulithTest.java ✓
- backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java ✓
- backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java ✓
- backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java ✓
- backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java ✓

Commits verified:
- e1828b4: feat(01-01): scaffold Spring Boot 3.5.13 backend with pinned BOMs ✓
- 9dc6492: feat(01-01): add Modulith module packages and application/test profiles ✓
- a337a6f: test(01-01): add Wave 0 test scaffolds — Testcontainers base + six red tests ✓
