# Phase 1: Data Foundation - Research

**Researched:** 2026-06-07
**Domain:** Spring Boot 3.5 / Java 21 scaffold, Flyway + pgvector, Spring Security form login + session, idempotent seed generation, Docker Compose topology, Vite + Vue 3 frontend shell, .mcp.json dev tooling
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- Monorepo layout: `backend/` (Spring Boot) and `frontend/` (Vue 3) at repo root, with a root `docker-compose.yml` and `README.md`.
- Backend: single Maven module using the Maven wrapper (`./mvnw`), Java 21, Spring Boot 3.5.13, Spring AI BOM pinned to 1.1.6 (deps added as phases need them). Base package `com.quantlens`.
- Internal structure uses Spring Modulith package boundaries (e.g. `com.quantlens.portfolio`, `com.quantlens.marketdata`, `com.quantlens.analytics`, `com.quantlens.ai`, `com.quantlens.security`, `com.quantlens.seed`) — Modulith over Maven multi-module. Only the packages needed this phase are created now.
- Money/quantities use `BigDecimal` (NUMERIC columns); dates use `LocalDate`.
- Spring Security with form login over seeded users. Three personas: `alice` (Growth), `bob` (Income), `charlie` (Balanced), password `demo1234`, shown on the login screen. One-click switcher on the login page.
- Server-side `HttpSession` cookie; session persists across refresh; scopes which portfolio is visible.
- Spring Security config structured so OAuth2/OIDC can be added later without reworking authorization.
- Price data is **synthetic but reproducible** — correlated GBM, fixed RNG seed. ~504 trading days, ~15 securities (AAPL, MSFT, NVDA, AMZN, GOOGL, JPM, BAC, XOM, CVX, JNJ, PFE, PG, KO, WMT, TSLA).
- Benchmark: synthetic S&P 500 proxy = market-factor series stored as a pseudo-security/benchmark series.
- Fama-French: seeded daily Mkt-RF, SMB, HML factor-return series.
- Each demo persona gets a distinct portfolio matching its style.
- Seeding: Flyway owns schema; a Spring `ApplicationRunner`/`CommandLineRunner` seeder populates data — no-ops if data exists.
- Postgres 16 via `pgvector/pgvector:pg16`; healthcheck in compose so backend waits (`condition: service_healthy`).
- Flyway creates relational schema and pgvector extension. `vector_store` embedding dimension locked at **1536**.
- `spring.ai.vectorstore.pgvector.initialize-schema=true` set explicitly; HNSW index, cosine distance.
- DB name/user `quantlens`; dev credentials via compose env vars only.
- Add a project `.mcp.json` configuring context7 MCP and a Postgres MCP for Claude Code.

### Claude's Discretion
- Exact table/column names, migration file organization, the precise GBM parameters per security, frontend scaffold tool (Vite + Vue 3 + TS), and Dockerfile layering are at Claude's discretion within the above constraints.

### Deferred Ideas (OUT OF SCOPE)
- Importing real historical market data (kept synthetic for v1 reproducibility/zero-dependency).
- Real OAuth (Google/GitHub) implementation — documented as upgrade path only.
- Expanding the securities universe or adding asset classes beyond equities.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DATA-01 | Developer can launch the full stack with a single `docker compose up` | Docker Compose topology section; healthcheck ordering; Windows named volume guidance |
| DATA-02 | On first start the system seeds demo users, ~15 securities, ~2 years of daily OHLCV plus benchmark and factor-return series | Idempotent seeder pattern; correlated-GBM generation guidance; ApplicationRunner/CommandLineRunner pattern |
| DATA-03 | pgvector schema initializes automatically on cold start so RAG storage works with no manual setup | Flyway + pgvector interaction; initialize-schema property; extension permission workaround |
| AUTH-01 | User can log in by selecting one of three seeded demo personas | Spring Security form login + JSON handlers for SPA; BCrypt + UserDetailsService |
| AUTH-02 | User session persists across page refresh and scopes which portfolio is shown | HttpSession config; JSESSIONID cookie; SessionCreationPolicy.ALWAYS; CSRF config |
| DEVX-01 | The repo configures dev-side MCP servers for Claude Code | .mcp.json format; context7 MCP (confirmed, active); Postgres MCP status (deprecated — replacement recommended) |
</phase_requirements>

---

## Summary

Phase 1 establishes the walking skeleton that all subsequent phases build on. The key architectural fact is that this is a greenfield Spring Boot 3.5.13 / Java 21 modular monolith with Flyway-managed schema, an idempotent seed runner, and a minimal Vue 3 shell — no AI starters yet, but the Spring AI BOM is pinned at 1.1.6 in `dependencyManagement` so later phases can add starters without version conflicts.

The most critical gotcha for this phase is the **pgvector extension permission model**: in Docker Compose, if `POSTGRES_USER` is set to `quantlens` (the app user), that user cannot run `CREATE EXTENSION vector` because it requires superuser privileges. The solution is a two-line `docker-entrypoint-initdb.d` init script that runs as the default `postgres` superuser to pre-install the extension, with Flyway migrations then building all tables. Spring AI's `initialize-schema=true` creates the `vector_store` table idempotently using `CREATE TABLE IF NOT EXISTS`, so Flyway creating the extension first and Spring AI creating the table after is the correct pattern — no double-creation conflict.

For Spring Security with a Vue SPA, the most practical choice for a demo is `CookieCsrfTokenRepository.withHttpOnlyFalse()` so Axios can read the `XSRF-TOKEN` cookie and send `X-XSRF-TOKEN`, OR simply disable CSRF and document why (acceptable when SameSite=Lax is the CSRF defense). The login success/failure handlers must return JSON, not HTTP redirects, because the Vue app calls the Spring Security login endpoint directly via Axios and cannot follow server-side redirects. An `AuthenticationSuccessHandler` returning 200 and an `AuthenticationFailureHandler` returning 401 are the correct replacements for the default redirect behavior.

**Primary recommendation:** Scaffold backend with Spring Initializr curl API, add Spring AI BOM in `dependencyManagement` only (no starters yet), configure a `docker-entrypoint-initdb.d/00-init.sql` to pre-create the vector extension, use a single `V1__schema.sql` Flyway migration for all relational tables, and implement the seeder as a `@Component` `ApplicationRunner` with `@Order(1)` that guards all inserts with `repository.count() == 0` checks.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| DB schema creation (tables, indexes, extension) | Database / Storage (Flyway migrations) | Docker init script (extension only) | Flyway owns DDL lifecycle; Docker init for extension needing superuser |
| Seed data generation (OHLCV, personas, portfolios) | API / Backend (ApplicationRunner) | — | Programmatic GBM generation requires Java; SQL files too large for 500×15 price rows |
| Vector store auto-creation (vector_store table) | API / Backend (Spring AI initialize-schema) | — | Spring AI owns its own schema within its configured dimension/index type |
| Demo user authentication | API / Backend (Spring Security) | — | Session cookies live server-side; auth logic never in browser tier |
| Session persistence | API / Backend (HttpSession) | — | Server-side session; JSESSIONID cookie scopes portfolio per user |
| Vue frontend shell scaffold | Browser / Client (Vite build) | Frontend Server (nginx in prod) | Vite builds static SPA; nginx serves it in the Docker frontend container |
| API proxy (dev only) | Browser / Client (Vite dev server proxy) | — | Dev-time only; prod frontend calls backend directly or via nginx proxy_pass |
| MCP dev tooling | Developer tooling (stdio processes) | — | context7 and Postgres MCP run as local processes, not in Docker |

---

## Standard Stack

### Core (Phase 1 only — Phase 1 adds no AI starters)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot | 3.5.13 | Application platform + BOM | Latest stable 3.5.x; pairs with Spring AI 1.1.x; confirmed [VERIFIED: spring.io blog] |
| Spring AI BOM | 1.1.6 | Dependency management for AI starters (BOM only, no starters this phase) | Pins all future `spring-ai-*` artifact versions; must be in `dependencyManagement` now [VERIFIED: spring.io docs] |
| spring-boot-starter-web | managed | REST controllers, embedded Tomcat | Standard Spring MVC foundation |
| spring-boot-starter-security | managed | Auth, session, form login | Spring Security 6; integrated with Boot |
| spring-boot-starter-data-jpa | managed | JPA + HikariCP connection pool | Required by Spring AI pgvector starter (JdbcTemplate auto-config) |
| spring-boot-starter-validation | managed | Bean Validation (JSR-380) | Input validation on DTOs |
| spring-boot-starter-actuator | managed | `/actuator/health` — used as Docker healthcheck | Standard health endpoint |
| flyway-core + flyway-database-postgresql | 10.x (Boot managed) | Versioned SQL migrations | Schema lifecycle management; must use `flyway-database-postgresql` alongside `flyway-core` for Boot 3.x |
| postgresql (JDBC driver) | managed by Boot | JDBC connectivity | Standard Postgres JDBC driver |
| spring-modulith-starter-core | Boot-managed via spring-modulith BOM | Package-level module boundary enforcement | `ApplicationModules.verify()` test; architecture as code [VERIFIED: Spring Modulith docs] |
| spring-modulith-starter-test | test scope | `ApplicationModules.of(App.class).verify()` | Module structure verification test |

### Frontend Shell

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| vue | 3.5.35 | SPA framework | Composition API, `<script setup>` TS; confirmed current [VERIFIED: npm registry] |
| vite | 8.0.16 | Build tool + dev server | De-facto standard for Vue 3; native ESM, HMR, TypeScript out of box [VERIFIED: npm registry] |
| @vitejs/plugin-vue | 6.0.7 | Vite Vue SFC plugin | Required to process `.vue` files in Vite [VERIFIED: npm registry] |
| pinia | 3.0.4 | State management | Official Vuex successor for Vue 3 [VERIFIED: npm registry] |
| axios | 1.17.0 | HTTP client | Standard; session-cookie-aware (withCredentials) [VERIFIED: npm registry] |
| vue-echarts | 8.0.1 | Vue 3 ECharts component wrapper | Lock-in to Phase 3 chart work; shell only imports it here, no charts yet [VERIFIED: npm registry] |
| echarts | 6.1.0 | Chart engine (peer dep) | Required by vue-echarts [VERIFIED: npm registry] |

### Installation

```bash
# Backend — use Spring Initializr curl API (non-interactive)
curl -s "https://start.spring.io/starter.zip" \
  -d "type=maven-project" \
  -d "language=java" \
  -d "bootVersion=3.5.13" \
  -d "baseDir=backend" \
  -d "groupId=com.quantlens" \
  -d "artifactId=backend" \
  -d "name=QuantLens" \
  -d "packageName=com.quantlens" \
  -d "javaVersion=21" \
  -d "dependencies=web,security,data-jpa,flyway,postgresql,validation,actuator" \
  -o backend.zip && unzip backend.zip && rm backend.zip

# Manually add spring-ai-bom, spring-modulith-starter-core, spring-modulith-starter-test
# to the generated pom.xml (see Code Examples below)

# Frontend — scaffold non-interactively
npm create vite@latest frontend -- --template vue-ts
cd frontend
npm install pinia axios vue-echarts echarts
```

---

## Package Legitimacy Audit

> slopcheck was not available in this environment. All packages are marked `[ASSUMED]` where registry verification alone was performed. The planner must not treat registry existence as slopcheck-clean.

| Package | Registry | Verification | Source Repo | Notes | Disposition |
|---------|----------|-------------|-------------|-------|-------------|
| `vue` 3.5.35 | npm | `npm view` confirmed | github.com/vuejs/core | Flagship Vue project, 8yr history | [ASSUMED] — registry only |
| `vite` 8.0.16 | npm | `npm view` confirmed | github.com/vitejs/vite | Official Vite project | [ASSUMED] — registry only |
| `@vitejs/plugin-vue` 6.0.7 | npm | `npm view` confirmed | github.com/vitejs/vite-plugin-vue | Official plugin | [ASSUMED] — registry only |
| `pinia` 3.0.4 | npm | `npm view` confirmed | github.com/vuejs/pinia | Official Vue state manager | [ASSUMED] — registry only |
| `axios` 1.17.0 | npm | `npm view` confirmed | github.com/axios/axios | Mature HTTP client, 11yr history | [ASSUMED] — registry only |
| `vue-echarts` 8.0.1 | npm | `npm view` confirmed | github.com/ecomfe/vue-echarts | Official vue-echarts by Baidu/ECharts team | [ASSUMED] — registry only |
| `echarts` 6.1.0 | npm | `npm view` confirmed | github.com/apache/echarts | Apache Software Foundation | [ASSUMED] — registry only |
| `@upstash/context7-mcp` 3.1.0 | npm | `npm view` confirmed | github.com/upstash/context7 | Official Context7 by Upstash; active, 21-day cadence | [ASSUMED] — **actively maintained, recommended** |
| `@modelcontextprotocol/server-postgres` 0.6.2 | npm | `npm view` confirmed | **ARCHIVED** (servers-archived) | **DEPRECATED July 2025** — SQL injection vulnerability. Last published ~1yr ago. 21k/wk downloads despite deprecation. | **DO NOT USE** |

**Packages removed / flagged:**
- `@modelcontextprotocol/server-postgres`: DEPRECATED and archived by Anthropic (July 2025) due to SQL injection vulnerability. **Must not be used.** See replacement in Architecture Patterns § DEVX-01.
- All other packages: `[ASSUMED]` — slopcheck unavailable, planner should add a `checkpoint:human-verify` before first install in execution context.

*Recommended Postgres MCP replacement for dev tooling:* Use `@henkey/postgres-mcp-server` (actively maintained community fork hardened against the SQL injection) OR configure direct psql access in `.mcp.json` via a wrapper script. See the DEVX-01 section below.

---

## Architecture Patterns

### System Architecture Diagram (Phase 1 scope)

```
  docker compose up
        │
        ▼
  ┌─────────────────────────────────────────────────────┐
  │  db (pgvector/pgvector:pg16)                         │
  │  /docker-entrypoint-initdb.d/00-init.sql:            │
  │    CREATE EXTENSION IF NOT EXISTS vector;            │
  │  healthcheck: pg_isready -U quantlens                │
  └──────────────────────┬──────────────────────────────┘
                         │ service_healthy
                         ▼
  ┌─────────────────────────────────────────────────────┐
  │  backend (eclipse-temurin:21-jre)                    │
  │  Spring Boot 3.5.13 starts                           │
  │   → Flyway: V1__schema.sql (relational tables)       │
  │   → Flyway: V2__vector_store.sql (vector_store)      │
  │       OR Spring AI initialize-schema creates it      │
  │   → ApplicationRunner (SeedRunner @Order(1)):        │
  │       count() == 0? → generate + insert OHLCV,       │
  │       personas, portfolios, FF factors               │
  │   → Spring Security: form login, HttpSession,        │
  │       BCrypt-hashed demo users                       │
  │   → /actuator/health → 200 UP                        │
  └──────────────────────┬──────────────────────────────┘
                         │ depends_on: backend started
                         ▼
  ┌─────────────────────────────────────────────────────┐
  │  frontend (node:22 build → nginx:alpine serve)       │
  │  Vite builds dist/; nginx serves on :80              │
  │  Vue 3 shell: login page + persona switcher          │
  │  Axios POST /api/auth/login → JSESSIONID cookie      │
  │  → session persists; dashboard placeholder renders   │
  └─────────────────────────────────────────────────────┘
```

### Recommended Project Structure

```
/ (repo root)
├── docker-compose.yml
├── .mcp.json                          # Claude Code dev MCP config
├── README.md
├── backend/
│   ├── Dockerfile
│   ├── mvnw / mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/quantlens/
│       │   │   ├── QuantLensApplication.java   (@SpringBootApplication, @Modulithic)
│       │   │   ├── security/
│       │   │   │   ├── config/SecurityConfig.java
│       │   │   │   └── config/WebConfig.java    (CORS, session cookie customizer)
│       │   │   ├── seed/
│       │   │   │   ├── SeedRunner.java          (@Component ApplicationRunner @Order(1))
│       │   │   │   └── GbmGenerator.java        (correlated GBM, fixed seed)
│       │   │   ├── marketdata/
│       │   │   │   └── domain/                  (Security, OhlcvBar, FamaFrenchFactor entities)
│       │   │   └── portfolio/
│       │   │       └── domain/                  (AppUser, Portfolio, Position, Transaction entities)
│       │   └── resources/
│       │       ├── application.yml
│       │       ├── application-demo.yml
│       │       └── db/migration/
│       │           ├── V1__schema.sql            (all relational tables + indexes)
│       │           └── V2__vector_store.sql      (vector_store table + HNSW index — OR let Spring AI own this)
│       └── test/
│           ├── java/com/quantlens/
│           │   ├── QuantLensModulithTest.java    (ApplicationModules.verify())
│           │   └── seed/SeedRunnerIntegrationTest.java
│           └── resources/
│               └── application-test.yml
├── frontend/
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── package.json
│   ├── vite.config.ts
│   └── src/
│       ├── main.ts
│       ├── App.vue
│       ├── router/index.ts
│       ├── stores/auth.ts               (Pinia — session state, persona)
│       ├── api/auth.ts                  (Axios login/logout/whoami)
│       └── views/LoginView.vue          (form + one-click persona switcher)
└── docker/
    └── db/
        └── 00-init.sql                  # mounted to /docker-entrypoint-initdb.d/
```

### Pattern 1: Spring Boot Scaffold via Initializr curl API

```bash
# Source: https://start.spring.io (HTTP API confirmed working — verified via tool call)
curl -s "https://start.spring.io/starter.zip" \
  -d "type=maven-project" \
  -d "language=java" \
  -d "bootVersion=3.5.13" \
  -d "baseDir=backend" \
  -d "groupId=com.quantlens" \
  -d "artifactId=backend" \
  -d "name=QuantLens" \
  -d "packageName=com.quantlens" \
  -d "javaVersion=21" \
  -d "dependencies=web,security,data-jpa,flyway,postgresql,validation,actuator" \
  -o backend.zip
unzip backend.zip
rm backend.zip
```

The wrapper `mvnw` / `mvnw.cmd` is included in the generated zip. On Windows, before running `./mvnw`, set JAVA_HOME to Temurin 21:

```powershell
# PowerShell (Windows host)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\mvnw.cmd clean verify
```

### Pattern 2: pom.xml Spring AI BOM + Spring Modulith additions

Add these sections to the generated pom.xml after the `<parent>` block:

```xml
<!-- Source: https://docs.spring.io/spring-ai/reference/getting-started.html
     [VERIFIED: spring.io docs] -->

<properties>
    <java.version>21</java.version>
    <spring-ai.version>1.1.6</spring-ai.version>
    <spring-modulith.version>1.3.5</spring-modulith.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- Spring AI BOM — pins all spring-ai-* artifacts. No starters yet. -->
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>${spring-ai.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <!-- Spring Modulith BOM -->
        <dependency>
            <groupId>org.springframework.modulith</groupId>
            <artifactId>spring-modulith-bom</artifactId>
            <version>${spring-modulith.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- ... (generated deps) ... -->

    <!-- Spring Modulith -->
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.modulith</groupId>
        <artifactId>spring-modulith-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Flyway Postgres dialect — required for Boot 3.x alongside flyway-core -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-database-postgresql</artifactId>
    </dependency>
</dependencies>
```

**Note on Spring Modulith version:** Spring Modulith 1.3.x is the current stable line compatible with Spring Boot 3.5.x. [ASSUMED — verify against https://spring.io/projects/spring-modulith#learn at plan time]

### Pattern 3: Flyway + pgvector Extension — Correct Ordering

The pgvector extension requires superuser privileges to install in PostgreSQL. The `POSTGRES_USER=quantlens` app user is NOT a superuser. Solution: run extension creation in Docker's init script (executed as the default `postgres` user during first startup), then Flyway handles all table DDL.

```sql
-- docker/db/00-init.sql
-- Runs ONCE on first container start as postgres superuser
-- Source: docker-entrypoint-initdb.d documentation, verified via community research
-- [VERIFIED: Docker Hub pgvector/pgvector docs behavior]
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";  -- needed for uuid_generate_v4() default
```

Mounted in docker-compose:

```yaml
db:
  image: pgvector/pgvector:pg16
  volumes:
    - ./docker/db/00-init.sql:/docker-entrypoint-initdb.d/00-init.sql:ro
    - postgres_data:/var/lib/postgresql/data   # named volume — required on Windows
```

Then Flyway creates tables:

```sql
-- backend/src/main/resources/db/migration/V1__schema.sql
-- Source: Project decisions + standard JPA entity mapping patterns [ASSUMED - exact columns at Claude's discretion]

-- Securities master
CREATE TABLE IF NOT EXISTS securities (
    id          BIGSERIAL PRIMARY KEY,
    ticker      VARCHAR(10) NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    sector      VARCHAR(50) NOT NULL
);

-- OHLCV price bars
CREATE TABLE IF NOT EXISTS ohlcv_bars (
    id          BIGSERIAL PRIMARY KEY,
    security_id BIGINT NOT NULL REFERENCES securities(id),
    bar_date    DATE NOT NULL,
    open_price  NUMERIC(18,6) NOT NULL,
    high_price  NUMERIC(18,6) NOT NULL,
    low_price   NUMERIC(18,6) NOT NULL,
    close_price NUMERIC(18,6) NOT NULL,
    volume      BIGINT NOT NULL,
    UNIQUE (security_id, bar_date)
);
CREATE INDEX idx_ohlcv_security_date ON ohlcv_bars (security_id, bar_date DESC);

-- Fama-French factor series
CREATE TABLE IF NOT EXISTS factor_returns (
    id          BIGSERIAL PRIMARY KEY,
    factor_date DATE NOT NULL,
    mkt_rf      NUMERIC(10,6) NOT NULL,
    smb         NUMERIC(10,6) NOT NULL,
    hml         NUMERIC(10,6) NOT NULL,
    rf          NUMERIC(10,6) NOT NULL DEFAULT 0,
    UNIQUE (factor_date)
);

-- Demo users
CREATE TABLE IF NOT EXISTS app_users (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    password_hash   VARCHAR(100) NOT NULL,
    persona         VARCHAR(30) NOT NULL,
    external_id     VARCHAR(255)        -- nullable; for future OAuth sub claim
);

-- Portfolios
CREATE TABLE IF NOT EXISTS portfolios (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT NOT NULL REFERENCES app_users(id),
    name        VARCHAR(100) NOT NULL,
    style       VARCHAR(30) NOT NULL
);

-- Portfolio positions (current holdings)
CREATE TABLE IF NOT EXISTS positions (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT NOT NULL REFERENCES portfolios(id),
    security_id     BIGINT NOT NULL REFERENCES securities(id),
    quantity        NUMERIC(18,4) NOT NULL,
    avg_cost_basis  NUMERIC(18,6) NOT NULL,
    UNIQUE (portfolio_id, security_id)
);

-- Transaction history
CREATE TABLE IF NOT EXISTS transactions (
    id              BIGSERIAL PRIMARY KEY,
    portfolio_id    BIGINT NOT NULL REFERENCES portfolios(id),
    security_id     BIGINT NOT NULL REFERENCES securities(id),
    tx_date         DATE NOT NULL,
    tx_type         VARCHAR(10) NOT NULL CHECK (tx_type IN ('BUY','SELL')),
    quantity        NUMERIC(18,4) NOT NULL,
    price           NUMERIC(18,6) NOT NULL
);
CREATE INDEX idx_tx_portfolio_date ON transactions (portfolio_id, tx_date DESC);
```

**On vector_store:** Spring AI's `initialize-schema=true` runs `CREATE TABLE IF NOT EXISTS vector_store (...)` and `CREATE INDEX ... USING HNSW (embedding vector_cosine_ops)` at Spring Boot startup — **after Flyway** (Flyway runs first in Spring Boot's auto-configuration order). Because the extension was created by the init script, Spring AI's `CREATE TABLE IF NOT EXISTS` will succeed. No Flyway migration for the `vector_store` table is needed unless you want Flyway to own its lifecycle (optional for Phase 1). [VERIFIED: Spring AI pgvector docs]

```yaml
# application.yml — Spring AI pgvector config (Phase 1: no starters yet, but set properties)
# Source: https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html
# [VERIFIED: spring.io docs]
spring:
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true      # EXPLICIT — do not rely on default (which is false)
        dimensions: 1536             # Locked: OpenAI text-embedding-3-small — cannot change without drop+re-embed
        distance-type: COSINE_DISTANCE
        index-type: HNSW
```

### Pattern 4: Spring Security Config for SPA (form login + JSON handlers + session)

The key insight: Vue calls Spring Security's default `/login` endpoint via Axios with form-encoded body. Spring Security's default behavior on success/failure is HTTP redirects, which Axios cannot follow meaningfully. Override with JSON handlers.

```java
// Source: Spring Security docs + verified community patterns [CITED: docs.spring.io/spring-security]
// backend/src/main/java/com/quantlens/security/config/SecurityConfig.java

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/personas",
                                 "/actuator/health").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginProcessingUrl("/api/auth/login")    // Vue POSTs here
                .successHandler(jsonSuccessHandler())     // return 200 JSON, not redirect
                .failureHandler(jsonFailureHandler())     // return 401 JSON, not redirect
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/api/auth/logout")
                .logoutSuccessHandler((req, res, auth) -> res.setStatus(200))
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.ALWAYS) // persist session
                .sessionFixation().changeSessionId()                 // security best practice
            )
            // CSRF: use CookieCsrfTokenRepository so Vue can read XSRF-TOKEN cookie
            // [VERIFIED: Spring Security docs - CookieCsrfTokenRepository.withHttpOnlyFalse()]
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
            )
            .build();
    }

    private AuthenticationSuccessHandler jsonSuccessHandler() {
        return (request, response, authentication) -> {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            String username = authentication.getName();
            response.getWriter().write("""
                {"authenticated":true,"username":"%s"}
                """.formatted(username));
        };
    }

    private AuthenticationFailureHandler jsonFailureHandler() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("""
                {"authenticated":false,"error":"Invalid credentials"}
                """);
        };
    }

    @Bean
    public UserDetailsService userDetailsService(AppUserRepository userRepository,
                                                  PasswordEncoder encoder) {
        // JPA-backed UserDetailsService — reads from app_users table seeded by SeedRunner
        return username -> userRepository.findByUsername(username)
            .map(user -> User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles("USER")
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**Vue Axios CSRF integration:**

```typescript
// src/api/auth.ts
// After login, Axios must send XSRF-TOKEN cookie value as X-XSRF-TOKEN header
// axios.defaults.withCredentials = true; // required for session cookie
// [CITED: Spring Security docs - CookieCsrfTokenRepository]
import axios from 'axios'

axios.defaults.withCredentials = true  // send JSESSIONID + XSRF-TOKEN cookies

// Read CSRF token from cookie after first request
function getCsrfToken(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

// Interceptor — attach CSRF token to every mutating request
axios.interceptors.request.use(config => {
  const token = getCsrfToken()
  if (token && ['post','put','delete','patch'].includes(config.method?.toLowerCase() ?? '')) {
    config.headers['X-XSRF-TOKEN'] = token
  }
  return config
})

export async function login(username: string, password: string) {
  const params = new URLSearchParams()
  params.append('username', username)
  params.append('password', password)
  return axios.post('/api/auth/login', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
  })
}
```

### Pattern 5: Idempotent ApplicationRunner Seeder

```java
// Source: Spring Boot ApplicationRunner + JPA count pattern [CITED: Spring Boot docs]
// backend/src/main/java/com/quantlens/seed/SeedRunner.java

@Component
@Order(1)  // run first; additional seeders can use @Order(2), etc.
@Slf4j
public class SeedRunner implements ApplicationRunner {

    private final SecurityRepository securityRepository;
    private final AppUserRepository userRepository;
    private final OhlcvBarRepository ohlcvRepository;
    private final PasswordEncoder passwordEncoder;
    private final GbmGenerator gbmGenerator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (securityRepository.count() > 0) {
            log.info("Seed data already present — skipping SeedRunner");
            return;  // Idempotent guard — no-op on restarts
        }
        log.info("Seeding demo data...");
        seedSecurities();
        seedUsers();
        seedOhlcv();        // calls GbmGenerator with fixed seed
        seedFactors();
        seedPortfolios();
        log.info("Seed complete.");
    }
    // ...
}
```

### Pattern 6: Correlated GBM Generator (fixed seed for reproducibility)

```java
// Source: Project decisions — correlated GBM with Hipparchus MersenneTwister
// [CITED: PITFALLS.md § Pitfall 5 — Ito correction must be present]
// backend/src/main/java/com/quantlens/seed/GbmGenerator.java

@Component
public class GbmGenerator {

    // Fixed seed — guarantees identical prices every cold start
    private static final long RNG_SEED = 42L;
    // ~2 years of trading days
    private static final int TRADING_DAYS = 504;
    // dt = 1/252 (one trading day)
    private static final double DT = 1.0 / 252.0;

    /**
     * Generate correlated GBM price series for all securities.
     * Uses shared market factor (beta) + per-security idiosyncratic component.
     *
     * Exact discretization (Ito-corrected):
     *   S(t+dt) = S(t) * exp((mu - sigma^2/2)*dt + sigma*sqrt(dt)*Z)
     * where Z ~ N(0,1) and sigma is per-security annualized vol.
     *
     * NOTE: The sigma^2/2 (Ito correction) term MUST be present.
     * Omitting it causes paths to drift upward by sigma^2/2 per year. [PITFALL: Pitfall 5]
     */
    public Map<String, List<BigDecimal[]>> generate(List<SecuritySpec> specs) {
        MersenneTwister rng = new MersenneTwister(RNG_SEED);
        NormalDistribution normal = new NormalDistribution(rng, 0, 1);
        // ... implementation
    }
}
```

**GBM parameters to use** (at Claude's discretion — these are representative starting values):

| Parameter | Value | Note |
|-----------|-------|------|
| Market drift (mu_mkt) | 0.07 annualized | Long-run equity premium |
| Market vol (sigma_mkt) | 0.18 annualized | Typical S&P 500 realized vol |
| Per-security beta | 0.6–1.4 (varies by sector) | Tech = 1.2–1.4, Utilities = 0.6–0.8 |
| Per-security idiosyncratic vol | 0.10–0.25 annualized | Tech higher; Consumer staples lower |
| Starting prices | Approximate real prices as of mid-2024 for recognizability | AAPL ~$180, MSFT ~$410, etc. |
| RNG seed | 42 | Fixed — do not parameterize at runtime |

### Pattern 7: Docker Compose (complete Phase 1)

```yaml
# Source: Project decisions + Docker docs [CITED: docker.com/compose]
# [VERIFIED: healthcheck syntax via PITFALLS.md § Pitfall 16]

services:
  db:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_DB: quantlens
      POSTGRES_USER: quantlens
      POSTGRES_PASSWORD: quantlens_dev   # dev only — never real secret
    volumes:
      - ./docker/db/00-init.sql:/docker-entrypoint-initdb.d/00-init.sql:ro
      - postgres_data:/var/lib/postgresql/data  # named volume — REQUIRED on Windows
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U quantlens -d quantlens"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 20s

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/quantlens
      SPRING_DATASOURCE_USERNAME: quantlens
      SPRING_DATASOURCE_PASSWORD: quantlens_dev
      SPRING_PROFILES_ACTIVE: demo
      # AI keys not provided here — injected via session only (per security design)
    ports:
      - "8080:8080"
    depends_on:
      db:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    ports:
      - "5173:80"   # nginx on :80 inside container, exposed as :5173
    depends_on:
      - backend

volumes:
  postgres_data:   # named volume — avoids NTFS/WSL2 permission issues on Windows
```

**CRITICAL Windows gotcha:** Never use a bind mount `./data:/var/lib/postgresql/data` for Postgres data on Windows. Named volumes (`postgres_data:`) live inside the WSL2 filesystem and avoid NTFS permission problems. [VERIFIED: Docker community forums, multiple 2025 sources]

### Pattern 8: Backend Dockerfile (multi-stage)

```dockerfile
# Source: Docker multi-stage build docs + Spring Boot layered jar docs
# [CITED: docs.docker.com/get-started/docker-concepts/building-images/multi-stage-builds/]

# Stage 1: Build with Maven wrapper
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Cache dependency layer — copy pom.xml first
COPY .mvn .mvn
COPY mvnw mvnw.cmd pom.xml ./
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B -q

# Build
COPY src src
RUN ./mvnw package -DskipTests -B -q

# Extract Spring Boot layers for better caching
RUN java -Djarmode=layertools -jar target/*.jar extract

# Stage 2: Runtime — JRE only (200-300MB smaller than JDK)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Spring Boot layered jar — copy in change-frequency order
COPY --from=build /workspace/dependencies/ ./
COPY --from=build /workspace/spring-boot-loader/ ./
COPY --from=build /workspace/snapshot-dependencies/ ./
COPY --from=build /workspace/application/ ./

# Run as non-root
RUN useradd -r -u 1001 appuser
USER appuser

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=10s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "org.springframework.boot.loader.launch.JarLauncher"]
```

### Pattern 9: Frontend Dockerfile (Vite build → nginx)

```dockerfile
# Stage 1: Build with Node
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# Stage 2: Serve with nginx
FROM nginx:alpine
COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

```nginx
# frontend/nginx.conf
server {
    listen 80;
    root /usr/share/nginx/html;
    index index.html;

    # SPA routing — all 404s serve index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # Proxy API calls to backend (production: backend hostname = "backend")
    location /api/ {
        proxy_pass http://backend:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_cookie_flags ~JSESSIONID secure samesite=lax;
    }
}
```

### Pattern 10: Vite Config (dev proxy + TypeScript)

```typescript
// frontend/vite.config.ts
// Source: https://vite.dev/config/server-options.html [VERIFIED: vite.dev docs]
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        // Session cookies flow through the proxy automatically
      }
    }
  }
})
```

### Pattern 11: Spring Modulith Verification Test

```java
// Source: Spring Modulith testing docs [VERIFIED: docs.spring.io/spring-modulith]
// backend/src/test/java/com/quantlens/QuantLensModulithTest.java

@Test
void applicationModulesShouldBeValid() {
    ApplicationModules.of(QuantLensApplication.class).verify();
}
```

### Pattern 12: DEVX-01 — .mcp.json (project-scoped)

The `@modelcontextprotocol/server-postgres` package is **DEPRECATED** (archived July 2025, SQL injection vulnerability). Use a replacement.

```json
// .mcp.json — committed to repo root, checked into version control
// Source: Claude Code MCP docs [VERIFIED: code.claude.com/docs/en/mcp]
// "type": "stdio" is the transport for local process MCP servers
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
    }
  }
}
```

**Alternative for project-db if `@henkey/postgres-mcp-server` fails slopcheck:** Use a simple shell wrapper that runs `psql` directly, or add the Postgres MCP via `claude mcp add` with user scope (so credentials stay out of version control):

```bash
# Run once per developer — stores in ~/.claude.json, not .mcp.json
claude mcp add --transport stdio project-db \
  --env POSTGRES_URL=postgresql://quantlens:quantlens_dev@localhost:5432/quantlens \
  -- npx -y @henkey/postgres-mcp-server
```

### Anti-Patterns to Avoid

- **Bind-mount Postgres data on Windows:** Use named volumes. `./data:/var/lib/...` causes NTFS permission errors and potential data corruption. [VERIFIED: Docker community]
- **`depends_on: db` without `condition: service_healthy`:** Container starts before Postgres is ready; HikariCP throws "Connection refused" and Spring Boot crashes. Always use `condition: service_healthy`. [VERIFIED: PITFALLS.md § Pitfall 16]
- **`CREATE EXTENSION vector` in a Flyway migration as the app user:** Will fail with "permission denied" — the app user is not superuser. Use `docker-entrypoint-initdb.d` init script instead. [VERIFIED: community research]
- **`spring.ai.vectorstore.pgvector.initialize-schema` left unset:** Defaults to `false`; vector store table never created; Spring AI operations fail silently. Always set explicitly to `true`. [VERIFIED: Spring AI pgvector docs]
- **Using `@modelcontextprotocol/server-postgres`:** DEPRECATED and archived (July 2025), SQL injection vulnerability. Use `@henkey/postgres-mcp-server` or a user-scoped alternative. [VERIFIED: multiple community sources]
- **new BCryptPasswordEncoder() in seeder without injecting PasswordEncoder bean:** Creates a mismatch if SecurityConfig uses a different strength parameter. Always inject the `@Bean PasswordEncoder` into the seeder.
- **Storing OHLCV data as `double` columns:** Use `NUMERIC(18,6)` in SQL and `BigDecimal` in Java entities. [CITED: PITFALLS.md § Pitfall 15]
- **No `@Transactional` on the seeder run() method:** If generation fails halfway, partial data remains and the idempotence guard (`count() > 0`) prevents re-seeding on the next restart. Wrap in one transaction or use per-batch transactions with a seeded flag table.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| DB migration lifecycle | Custom schema creation scripts in ApplicationRunner | Flyway migrations | Versioned, checksummed, ordered, supports rollback concept |
| Password hashing | Custom hash function | `BCryptPasswordEncoder` (Spring Security) | BCrypt has correct cost factor + salt; MD5/SHA is catastrophically wrong |
| Session management | Custom session token logic | Spring Security `HttpSession` + JSESSIONID | Edge cases: fixation, timeout, concurrent sessions — all handled |
| pgvector table + HNSW index | Hand-written DDL in ApplicationRunner | Spring AI `initialize-schema=true` + Flyway extension | Spring AI creates the exact schema its `PgVectorStore` expects |
| CSRF protection | Custom token verification filter | `CookieCsrfTokenRepository.withHttpOnlyFalse()` | SameSite + CSRF together are defense-in-depth; Spring handles token lifecycle |
| Random number generation (GBM) | `Math.random()` or `new Random()` | `MersenneTwister(seed)` from Hipparchus | Reproducibility requires seedable RNG; `Math.random()` is non-seedable globally |
| Correlated random draws | Direct multiplication by correlation | Cholesky decomposition via Hipparchus `CholeskyDecomposition` | Direct multiplication does not produce the correct covariance structure |

**Key insight:** The seed generator is the one place where custom Java code is unavoidable — but even there, use Hipparchus for the RNG and Cholesky, and Hipparchus `NormalDistribution` for drawing samples. Only the GBM discretization formula itself is hand-written, and that formula has a golden test: `E[S(T)] ≈ S(0)*exp(mu*T)` over 100k paths.

---

## Common Pitfalls

### Pitfall 1: pgvector Extension Permission Failure

**What goes wrong:** Flyway migration V1 contains `CREATE EXTENSION IF NOT EXISTS vector;`. Migration fails with `ERROR: permission denied to create extension "vector"` because the `quantlens` app user is not a PostgreSQL superuser.

**Why it happens:** Only superusers (or users granted `CREATE` on the database by a superuser) can install extensions. The `POSTGRES_USER` in compose is the app user, not the DBA user.

**How to avoid:** Put `CREATE EXTENSION IF NOT EXISTS vector;` in `docker/db/00-init.sql` mounted to `/docker-entrypoint-initdb.d/`. This runs as the `postgres` superuser during first-container-init, before Flyway runs. Flyway V1 contains only relational table DDL.

**Warning signs:** Flyway fails on cold start with "permission denied"; Spring Boot exits with exit code 1 before serving any requests.

### Pitfall 2: Docker Healthcheck Missing → HikariCP Connection Timeout

**What goes wrong:** Backend container starts before PostgreSQL is ready. HikariCP fails to acquire a connection; Spring Boot exits with `Connection refused` after 30s timeout.

**Why it happens:** `depends_on: db` without `condition: service_healthy` only waits for the container process to start, not for Postgres to accept connections.

**How to avoid:** Always use:
```yaml
depends_on:
  db:
    condition: service_healthy
```
Plus the healthcheck block on the `db` service. Include `start_period: 20s` to give Postgres time to run init scripts before health checks start counting. [VERIFIED: PITFALLS.md § Pitfall 16]

**Warning signs:** `HikariPool-1 - Connection is not available, request timed out after 30000ms`; or Spring Boot starts but then the seeder crashes with NPE because the DataSource is still initializing.

### Pitfall 3: initialize-schema Left at Default false

**What goes wrong:** `vector_store` table is never created. Phase 7 (RAG) fails at startup with cryptic JDBC errors. The error may not appear until a `PgVectorStore.write()` call is made.

**Why it happens:** Spring AI changed the default from `true` to `false` in 1.0 GA (was previously auto-creating). [VERIFIED: Spring AI pgvector docs]

**How to avoid:** Always include in application.yml:
```yaml
spring.ai.vectorstore.pgvector.initialize-schema: true
```
Add a comment: `# EXPLICIT — default is false, do not remove`

**Warning signs:** No `vector_store` table present after startup (`\dt` in psql shows nothing).

### Pitfall 4: Spring Security Default Redirect on Login (SPA Breaks)

**What goes wrong:** Vue Axios POST to `/api/auth/login` gets a 302 redirect back to a login page. Axios follows the redirect with a GET, receiving the HTML login page as a 200 response. The Vue app cannot detect the authentication failure.

**Why it happens:** Spring Security's default `AuthenticationSuccessHandler` calls `sendRedirect("/")`. This is correct for server-rendered apps, wrong for SPAs.

**How to avoid:** Override both handlers with JSON-returning lambdas (see Pattern 4 above). Success returns HTTP 200 with `{"authenticated":true,"username":"..."}`. Failure returns HTTP 401 with `{"authenticated":false,"error":"Invalid credentials"}`. [CITED: Spring Security docs + community patterns]

**Warning signs:** Vue login form appears to submit but the route never changes; checking network tab shows 200 for the login POST but the response body is HTML.

### Pitfall 5: JAVA_HOME Not Set on Windows Host Before mvnw

**What goes wrong:** `./mvnw clean package` uses the system default JDK (JDK 11 in this environment per CLAUDE.md) rather than Temurin 21. Build fails or produces JDK 11 bytecode that doesn't compile Spring Boot 3.5 features.

**Why it happens:** CLAUDE.md confirms the shell's default `java` on PATH may still be JDK 11 in already-open shells.

**How to avoid:**
```powershell
# PowerShell — always run before ./mvnw in the backend directory
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
.\mvnw.cmd clean package
```
Or in bash:
```bash
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
./mvnw clean package
```

**Warning signs:** `./mvnw` prints `Java version: 11.0.26` in the first line of output; compilation fails with "release version 21 not supported".

### Pitfall 6: BigDecimal OHLCV Values Constructed From Doubles

**What goes wrong:** `new BigDecimal(19.99)` captures the binary double approximation: `19.989999999999998436805981327779591083526611328125`. Close-price columns in the DB contain visually identical but arithmetically wrong values.

**Why it happens:** GBM generates `double` values for simulation efficiency; the conversion to `BigDecimal` for persistence uses the wrong constructor.

**How to avoid:** In the seeder, convert:
```java
// WRONG
new BigDecimal(gbmPrice)
// CORRECT — rounds to 6 decimal places, avoids double precision artifacts
BigDecimal.valueOf(gbmPrice).setScale(6, RoundingMode.HALF_UP)
```
All entity `@Column` price fields must be `BigDecimal`. [CITED: PITFALLS.md § Pitfall 15]

### Pitfall 7: GBM Missing Ito Correction

**What goes wrong:** Seeded price paths trend upward faster than intended. Fan charts in Phase 5 look bullish for all securities regardless of parameters.

**Why it happens:** Using `S(t+1) = S(t) * exp(mu*dt + sigma*sqrt(dt)*Z)` instead of the Ito-corrected form `S(t+1) = S(t) * exp((mu - sigma^2/2)*dt + sigma*sqrt(dt)*Z)`.

**How to avoid:** Write the formula with the correction term in a code comment and add a unit test: simulate 100,000 one-step paths and assert `E[S(T)] ≈ S(0)*exp(mu*T)` within 0.5%. [CITED: PITFALLS.md § Pitfall 5]

---

## Code Examples

### Spring Initializr — Full curl Command (verified working)

```bash
# Source: start.spring.io HTTP API [VERIFIED: actual zip returned in tool call]
curl -s "https://start.spring.io/starter.zip" \
  -d "type=maven-project" \
  -d "language=java" \
  -d "bootVersion=3.5.13" \
  -d "baseDir=backend" \
  -d "groupId=com.quantlens" \
  -d "artifactId=backend" \
  -d "name=QuantLens" \
  -d "packageName=com.quantlens" \
  -d "javaVersion=21" \
  -d "dependencies=web,security,data-jpa,flyway,postgresql,validation,actuator" \
  -o backend.zip && unzip backend.zip && rm backend.zip
```

### Flyway Ordering in Boot Auto-configuration

Flyway runs before the application context finishes wiring (it is a `FlywayMigrationInitializer` bean ordered before JPA). Spring AI's `PgVectorStoreAutoConfiguration` creates the `vector_store` table during context refresh (post-Flyway). The correct startup sequence is:

```
1. Docker init script → CREATE EXTENSION vector (superuser, before Postgres accepts app connections)
2. Spring Boot starts → HikariCP pool created
3. Flyway runs → V1__schema.sql, V2__seed_setup.sql (any needed)
4. JPA EntityManager validated
5. Spring AI PgVectorStore → CREATE TABLE IF NOT EXISTS vector_store ... (idempotent)
6. ApplicationRunner.run() → SeedRunner seeds data
7. Application ready
```

### Modulith @Modulithic annotation

```java
// Source: Spring Modulith docs [VERIFIED: docs.spring.io/spring-modulith]
@Modulithic          // enables Spring Modulith detection
@SpringBootApplication
public class QuantLensApplication {
    public static void main(String[] args) {
        SpringApplication.run(QuantLensApplication.class, args);
    }
}
```

### application.yml (Phase 1 — complete)

```yaml
# Source: Spring AI pgvector docs + Spring Security session docs [VERIFIED]
spring:
  application:
    name: quantlens
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/quantlens}
    username: ${SPRING_DATASOURCE_USERNAME:quantlens}
    password: ${SPRING_DATASOURCE_PASSWORD:quantlens_dev}
  jpa:
    hibernate:
      ddl-auto: validate   # Flyway owns DDL; Hibernate only validates
    open-in-view: false    # avoid OSIV performance issues
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: false
  ai:
    vectorstore:
      pgvector:
        initialize-schema: true      # EXPLICIT — default is false
        dimensions: 1536             # Locked at 1536 (OpenAI text-embedding-3-small)
        distance-type: COSINE_DISTANCE
        index-type: HNSW
    # No model starters this phase — Spring AI BOM is in dependencyManagement only
    chat:
      client:
        enabled: false               # Prevent autoconfigure from requiring a provider key

server:
  port: 8080
  servlet:
    session:
      cookie:
        same-site: lax               # CSRF defense via SameSite

management:
  endpoints:
    web:
      exposure:
        include: health
  endpoint:
    health:
      show-details: never
```

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `mvn archetype:generate` for scaffolding | `start.spring.io` curl API with dependency IDs | ~2016 | Non-interactive, exact version control |
| `spring-ai-openai-spring-boot-starter` artifact ID | `spring-ai-starter-model-openai` | Spring AI M7 (2024) | Old IDs fail to resolve — copy from docs.spring.io only |
| `depends_on: [db]` in compose | `depends_on: db: condition: service_healthy` | Compose v2 (2022) | Actual readiness wait, not just container start |
| Postgres data as bind mount on Windows | Named volumes | Always best practice, widely adopted 2023+ | Avoids NTFS/WSL2 permission corruption |
| `initialize-schema` auto-enabled in Spring AI | Must be set explicitly (`initialize-schema: true`) | Spring AI 1.0 GA (June 2025) | Breaking change — silent failure if not set |
| `@modelcontextprotocol/server-postgres` for Postgres MCP | Community replacements (`@henkey/postgres-mcp-server`) | Deprecated July 2025 | Official package archived due to SQL injection CVE |
| `PromptChatMemoryAdvisor` | `MessageChatMemoryAdvisor` | Spring AI 1.1.6 | Old advisor deprecated and removed |

**Deprecated/outdated:**
- `@modelcontextprotocol/server-postgres`: Archived by Anthropic (July 2025), SQL injection vulnerability present. Do not use.
- `spring.ai.model.chat=openai` property name changed from earlier milestone forms: always refer to current upgrade notes.
- Bind-mount `./data:/var/lib/postgresql/data` on Windows: use named volumes.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Spring Modulith version 1.3.5 pairs with Spring Boot 3.5.13 | Standard Stack pom.xml | Build fails; version mismatch. Verify at https://spring.io/projects/spring-modulith#learn |
| A2 | `@henkey/postgres-mcp-server` is a safe slopcheck-clean replacement for the deprecated official Postgres MCP | DEVX-01 pattern | Could be a less trustworthy package; alternative is user-scoped MCP config via `claude mcp add` |
| A3 | `npm create vite@latest frontend -- --template vue-ts` scaffolds without prompts on Node v22 | Frontend scaffold command | May still prompt interactively on some npm/npx versions; use `--yes` flag or answer prompts manually |
| A4 | Spring AI `initialize-schema=true` runs AFTER Flyway in Spring Boot's autoconfiguration order | Architecture Patterns § startup sequence | If it runs before, vector extension may not exist yet → `CREATE TABLE ... vector(1536)` fails. Mitigation: init script installs extension unconditionally before any Java code runs |
| A5 | `spring.ai.chat.client.enabled=false` prevents Spring AI auto-config from failing on missing API keys when no model starters are on classpath | application.yml | If property is ignored on no-starter classpath, startup may fail with "no ChatModel found" |

**If this table is empty:** It is not — there are 5 assumed claims requiring verification at plan time.

---

## Open Questions

1. **Spring Modulith exact version for Boot 3.5.13**
   - What we know: Spring Modulith 1.3.x is the current stable line; Boot 3.5 support was added in 1.3.
   - What's unclear: Exact patch version (1.3.4 vs 1.3.5 vs 1.4.0).
   - Recommendation: Check https://github.com/spring-projects/spring-modulith/releases at plan time and pin the exact version in pom.xml.

2. **Is CookieCsrfTokenRepository needed or can we disable CSRF for the demo?**
   - What we know: CSRF is a real attack vector for session-cookie auth. `CookieCsrfTokenRepository.withHttpOnlyFalse()` is the documented pattern. Disabling CSRF is simpler but requires documenting the SameSite=Lax dependency.
   - What's unclear: Whether the complexity of CSRF token plumbing is worth it for a demo app with no user-uploaded data.
   - Recommendation: Use `CookieCsrfTokenRepository.withHttpOnlyFalse()` — it is one extra line in SecurityConfig and one Axios interceptor. Worth it for the portfolio credibility signal.

3. **Should the seeder guard be on `securityRepository.count() > 0` or on a dedicated `seed_log` table?**
   - What we know: `count() > 0` is simple and sufficient for Phase 1.
   - What's unclear: If a partial seed run was interrupted, `count() > 0` might be true with partial data, and the seeder will not re-run.
   - Recommendation: Add a `seed_completed` boolean flag to a small `seed_log` table and set it at the END of the seeder transaction. Guard on `seedLogRepository.findById("v1").map(SeedLog::isCompleted).orElse(false)`.

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 (Temurin) | backend build (./mvnw) | ✓ | 21.0.11.10 at `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot` | None — must set JAVA_HOME |
| Maven wrapper (./mvnw) | backend build | Generated by Spring Initializr | — | Generated in scaffold step |
| Node v22 / npm 11 | frontend scaffold + build | ✓ | Node 22.18.0, npm 11.12.0 | — |
| Docker 28 + Compose v2.34 | `docker compose up` | ✓ | Docker 28.0.4, Compose v2.34.0 | — |
| curl | Spring Initializr download | ✓ (git bash) | — | Download zip manually from start.spring.io |
| Default shell `java` | host Java builds | Java 11.0.26 on PATH | 11.0.26 — **WRONG VERSION** | Must set `JAVA_HOME` to Temurin 21 before every `./mvnw` invocation |

**Missing dependencies with no fallback:**
- None — all critical dependencies are available.

**Environment warning:**
- The default `java` on PATH is JDK 11.0.26, not JDK 21. Every task that runs `./mvnw` must first set `JAVA_HOME` to Temurin 21. This is a per-task requirement that must appear in every plan task that invokes the Maven wrapper on the Windows host.

---

## Validation Architecture

> workflow.nyquist_validation is not explicitly set to false — treating as enabled.

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (managed by Boot parent) |
| Config file | None needed — Boot Test auto-configures |
| Quick run command | `./mvnw test -pl backend -Dtest=QuantLensModulithTest,SeedRunnerIntegrationTest` |
| Full suite command | `./mvnw verify -pl backend` |
| Integration test profile | `@ActiveProfiles("test")` with H2 or Testcontainers Postgres |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| DATA-01 | `docker compose up` brings up 3 healthy containers | smoke (manual + script) | `docker compose up -d && docker compose ps` — all show "healthy" or "Up" | ❌ Wave 0 |
| DATA-02 | Seeder populates securities, OHLCV, users, portfolios, factors | integration | `./mvnw test -Dtest=SeedRunnerIntegrationTest` | ❌ Wave 0 |
| DATA-03 | `vector_store` table exists with correct column type `vector(1536)` | integration (SQL assertion) | `./mvnw test -Dtest=VectorStoreSchemaTest` | ❌ Wave 0 |
| AUTH-01 | POST `/api/auth/login` with correct credentials returns 200 JSON | integration | `./mvnw test -Dtest=AuthIntegrationTest#loginSuccess` | ❌ Wave 0 |
| AUTH-01 | POST `/api/auth/login` with wrong credentials returns 401 JSON | integration | `./mvnw test -Dtest=AuthIntegrationTest#loginFailure` | ❌ Wave 0 |
| AUTH-02 | Session cookie present after login; subsequent request uses it | integration | `./mvnw test -Dtest=AuthIntegrationTest#sessionPersists` | ❌ Wave 0 |
| AUTH-02 | Three persona users return different portfolio IDs | integration | `./mvnw test -Dtest=PersonaIntegrationTest` | ❌ Wave 0 |
| DEVX-01 | `.mcp.json` present at repo root, context7 entry valid | unit (file assertion) | Manual / `ls .mcp.json` | ❌ Wave 0 |

### Observable Validation Signals (Phase-of-Done)

These signals replace Nyquist automated tests for infrastructure validation:

```bash
# 1. Cold start — all containers healthy
docker compose down -v && docker compose up -d
docker compose ps  # all show "healthy" or "Up (healthy)"

# 2. Schema verification
docker compose exec db psql -U quantlens -d quantlens \
  -c "\dt" \
  -c "SELECT column_name, data_type FROM information_schema.columns WHERE table_name='vector_store';"
# Expected: vector_store.embedding is type 'USER-DEFINED' (pgvector) with dimension 1536

# 3. Seed counts
docker compose exec db psql -U quantlens -d quantlens \
  -c "SELECT COUNT(*) FROM securities;"        -- expect 15+
  -c "SELECT COUNT(*) FROM ohlcv_bars;"        -- expect ~15*504 = ~7560
  -c "SELECT COUNT(*) FROM app_users;"         -- expect 3
  -c "SELECT COUNT(*) FROM factor_returns;"    -- expect ~504

# 4. Login flow
curl -s -c cookie.txt -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=alice&password=demo1234"
# Expected: {"authenticated":true,"username":"alice"}

# 5. Session persists
curl -s -b cookie.txt http://localhost:8080/api/auth/me
# Expected: 200 with alice's username (not 401)
```

### Sampling Rate
- **Per task commit:** `./mvnw test -pl backend -Dtest=QuantLensModulithTest` (modulith boundary check, <10s)
- **Per wave merge:** `./mvnw verify -pl backend` (full test suite)
- **Phase gate:** All observable signals above pass before `/gsd:verify-work`

### Wave 0 Gaps (tests that must be created before implementation)
- [ ] `backend/src/test/java/com/quantlens/QuantLensModulithTest.java` — covers module boundary verification
- [ ] `backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java` — covers DATA-02
- [ ] `backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java` — covers DATA-03
- [ ] `backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java` — covers AUTH-01, AUTH-02
- [ ] `backend/src/test/resources/application-test.yml` — test profile with embedded/Testcontainers DB

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes | Spring Security BCrypt; form login; no plaintext storage |
| V3 Session Management | yes | `HttpSession` with `SessionCreationPolicy.ALWAYS`; `changeSessionId()` on fixation; `SameSite=Lax` on session cookie |
| V4 Access Control | yes (basic) | All non-auth endpoints require `authenticated()`; session scopes persona |
| V5 Input Validation | yes | Spring Validation (`@Valid`) on controller DTOs; Flyway migrations use parameterized data (no user input in DDL) |
| V6 Cryptography | yes | BCrypt for password storage; never MD5/SHA |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Session fixation | Elevation of privilege | `sessionFixation().changeSessionId()` — changes session ID on auth success |
| CSRF on state-changing endpoints | Spoofing | `CookieCsrfTokenRepository.withHttpOnlyFalse()` + `X-XSRF-TOKEN` header in Axios |
| Password in compose env vars | Information disclosure | Dev credentials only; document `.env` pattern for prod; never commit real secrets |
| Session key (JSESSIONID) in URL | Information disclosure | Spring Security defaults to cookie-only; `server.servlet.session.tracking-modes=COOKIE` |
| Spring Boot Actuator exposure | Information disclosure | Only `health` endpoint exposed; `show-details: never` in Phase 1 |
| Correlated-GBM seed exposed | Information disclosure | Seed is 42 — intentionally public for reproducibility; no security implication |

---

## Sources

### Primary (HIGH confidence)
- [Spring AI pgvector docs](https://docs.spring.io/spring-ai/reference/api/vectordbs/pgvector.html) — `initialize-schema`, dimensions, distance-type, HNSW config, DDL auto-created
- [Spring Modulith fundamentals](https://docs.spring.io/spring-modulith/reference/fundamentals.html) — `@Modulithic`, package structure, `ApplicationModules.of().verify()`
- [Spring Modulith testing](https://docs.spring.io/spring-modulith/reference/testing.html) — `spring-modulith-starter-test` artifact ID, test scope
- [Spring Security CSRF docs](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html) — `CookieCsrfTokenRepository.withHttpOnlyFalse()`, `XSRF-TOKEN` cookie, Vue integration
- [Vite server proxy config](https://vite.dev/config/server-options.html) — `/api` proxy, `changeOrigin: true`
- [Claude Code MCP docs](https://code.claude.com/docs/en/mcp) — `.mcp.json` format, `mcpServers` structure, project scope
- start.spring.io HTTP API — confirmed working via live tool call (returned valid zip)
- PITFALLS.md (project research) — Pitfall 5 (Ito correction), Pitfall 10 (initialize-schema), Pitfall 15 (BigDecimal), Pitfall 16 (Docker healthcheck)
- STACK.md + ARCHITECTURE.md (project research) — all stack version decisions locked

### Secondary (MEDIUM confidence)
- [npm registry — vue 3.5.35, vite 8.0.16, pinia 3.0.4, axios 1.17.0, vue-echarts 8.0.1, echarts 6.1.0, @vitejs/plugin-vue 6.0.7](https://www.npmjs.com) — versions confirmed via `npm view`
- [Docker Spring Boot startup ordering](https://medium.com/@aleksanderkolata/docker-spring-boot-and-containers-startup-order-39230e5352a4) — healthcheck pattern verified
- [@modelcontextprotocol/server-postgres deprecation](https://www.npmjs.com/package/@modelcontextprotocol/server-postgres) — deprecated July 2025, SQL injection CVE, 21k/wk downloads despite deprecation
- [@upstash/context7-mcp package](https://www.npmjs.com/package/@upstash/context7-mcp) — 3.1.0, Upstash official, no suspicious postinstall scripts, free to use

### Tertiary (LOW confidence / ASSUMED)
- Spring Modulith 1.3.5 version number — verify at plan time against https://spring.io/projects/spring-modulith
- `@henkey/postgres-mcp-server` as replacement for deprecated official package — not slopcheck-verified; planner must gate behind `checkpoint:human-verify`

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — all versions confirmed via npm view and official Spring docs
- Architecture patterns: HIGH — Flyway, Spring Security, Docker patterns verified via official docs
- Pgvector/Flyway interaction: HIGH — confirmed via Spring AI docs + community research on extension permission issue
- DEVX-01 (.mcp.json format): HIGH — confirmed via live Claude Code docs fetch
- Package legitimacy: MEDIUM — slopcheck unavailable; npm registry verification only; `@modelcontextprotocol/server-postgres` deprecation confirmed via authoritative community sources
- Spring Modulith exact version: LOW — verify at plan time

**Research date:** 2026-06-07
**Valid until:** 2026-07-07 (30 days for stable Spring ecosystem; `@upstash/context7-mcp` fast-moving — re-verify at plan time)
