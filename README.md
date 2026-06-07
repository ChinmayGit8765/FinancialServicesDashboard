# QuantLens — AI Portfolio & Market Intelligence Dashboard

An AI-augmented portfolio and market intelligence dashboard fusing a quantitative-finance Spring Boot backend with a Vue 3 SPA. Features real portfolio analytics (P&L, risk metrics, Sharpe, VaR, Monte Carlo), Spring AI narration, and a one-click demo mode — no API keys needed.

---

## Quick Start

```bash
docker compose up
```

- Frontend: http://localhost:5173
- Backend API: http://localhost:8080
- PostgreSQL: localhost:5432 (user/db: `quantlens`, password: `quantlens_dev`)

No API keys, no manual setup. On first start the backend seeds demo users, ~15 securities with ~2 years of synthetic daily OHLCV, benchmark data, and Fama-French factor series.

---

## Demo Personas

All personas share the password **`demo1234`**. Use the one-click login buttons on the login page, or log in with the form.

| Persona | Username | Style | Description |
|---------|----------|-------|-------------|
| Growth | `alice` | Growth | Tech-heavy, high-beta portfolio |
| Income | `bob` | Income | Dividend-focused, defensive holdings |
| Balanced | `charlie` | Balanced | Diversified across sectors |

Sessions persist across page refresh — the JSESSIONID cookie is scoped to the logged-in persona's portfolio.

---

## Cold-Start Verification

After `docker compose up`, verify the stack is healthy:

### 1. Container health

```bash
docker compose ps
```

Expected: `db` (healthy), `backend` (running), `frontend` (running).

### 2. Schema and seed counts

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT count(*) AS securities  FROM securities;
  SELECT count(*) AS ohlcv_bars  FROM ohlcv_bars;
  SELECT count(*) AS users       FROM app_users;
  SELECT count(*) AS factors     FROM factor_returns;
"
```

Expected: securities >= 15, ohlcv_bars ~7560, users = 3, factors ~504.

### 3. pgvector schema

```bash
docker compose exec db psql -U quantlens -d quantlens -c "
  SELECT column_name, udt_name
  FROM information_schema.columns
  WHERE table_name='vector_store' AND column_name='embedding';
"
```

Expected: `udt_name = vector`.

### 4. Login flow (curl)

```bash
# Login as alice
curl -s -c cookies.txt \
  -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "username=alice&password=demo1234"
# Expected: {"authenticated":true,"username":"alice"}

# Fetch session user
curl -s -b cookies.txt http://localhost:8080/api/auth/me
# Expected: {"username":"alice","persona":"Growth","portfolioId":1}
```

### 5. UI walkthrough

1. Open http://localhost:5173
2. Click **"Log in as Alice"** — lands on the dashboard placeholder showing Alice's persona and portfolio ID
3. Refresh the page — session persists (no re-login required)
4. Log out and repeat as Bob or Charlie — different `portfolioId` each time

---

## Architecture

```
docker compose up
      │
      ▼
db (pgvector/pgvector:pg16, named volume, healthcheck)
      │ service_healthy
      ▼
backend (eclipse-temurin:21, Spring Boot 3.5.13)
      │ Flyway migrations → ApplicationRunner seeder → Spring Security
      │ depends_on: backend started
      ▼
frontend (node:22 build → nginx:alpine)
      Vite SPA, Pinia + Axios (withCredentials, XSRF-TOKEN interceptor)
```

**Stack:** Java 21 + Spring Boot 3.5.13 + Spring AI 1.1.6 (BOM-pinned, no starters yet) + Spring Modulith + Flyway + pgvector/Postgres 16 + Vue 3 (Composition API) + Vite + Pinia + Axios + ECharts (charts deferred to Phase 3)

---

## Dev MCP Servers (DEVX-01)

The repo includes `.mcp.json` configuring two Claude Code MCP servers for development:

### context7 — current framework docs in Claude context

```json
"context7": {
  "command": "npx",
  "args": ["-y", "@upstash/context7-mcp@latest"]
}
```

Provides current Spring AI, Hipparchus, finmath-lib, and Vue/Vite documentation directly in Claude Code's context. No configuration required.

### project-db — query the local dev database from Claude Code

```json
"project-db": {
  "command": "npx",
  "args": ["-y", "@henkey/postgres-mcp-server"],
  "env": {
    "POSTGRES_CONNECTION_STRING": "postgresql://quantlens:quantlens_dev@localhost:5432/quantlens"
  }
}
```

**Note:** `@henkey/postgres-mcp-server` is the recommended replacement for `@modelcontextprotocol/server-postgres`, which was **deprecated and archived in July 2025** due to a SQL-injection vulnerability. Do NOT use the deprecated package.

**Alternative (user-scoped, keeps credentials out of version control):**

```bash
# Run once per developer — stored in ~/.claude.json, not committed to the repo
claude mcp add --transport stdio project-db \
  --env POSTGRES_CONNECTION_STRING=postgresql://quantlens:quantlens_dev@localhost:5432/quantlens \
  -- npx -y @henkey/postgres-mcp-server
```

Use the user-scoped approach if you prefer not to commit credentials to version control (the `quantlens_dev` password in `.mcp.json` is a dev-only non-secret, but the fallback is available).

After configuring, verify with `claude mcp list` inside this project directory.

---

## OAuth Upgrade Path

The current auth uses Spring Security form login with seeded BCrypt users — intentionally simple for the demo. The `SecurityConfig` is structured so an OAuth2/OIDC login can be added later without reworking the authorization rules:

1. Add `spring-boot-starter-oauth2-client` to `pom.xml`
2. Configure `spring.security.oauth2.client.*` properties (Google, GitHub, or any OIDC provider)
3. Add `.oauth2Login(oauth2 -> oauth2.successHandler(...))` to the existing `SecurityFilterChain` — the JSON success handler pattern is the same
4. Provision real users by mapping the OAuth `sub` claim to the `external_id` column in `app_users`
5. Remove the seeded BCrypt users from production configuration

No architectural changes to the authorization rules (route protection, session scoping, portfolio isolation) are required.

---

## Production Notes

- The `POSTGRES_PASSWORD: quantlens_dev` in `docker-compose.yml` is a dev-only, non-secret placeholder. For production, inject real credentials via environment variables or Docker secrets — never commit production passwords.
- AI provider keys (Anthropic, OpenAI) are **never** stored server-side. The BYO-key popup (Phase 6) stores keys in the user's browser session only.
- The `SPRING_PROFILES_ACTIVE: demo` profile activates the seed runner and demo AI responses. Remove this env var in production.

---

## Development

```bash
# Backend (host — requires JDK 21)
export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo

# Frontend (Vite dev server with /api proxy to localhost:8080)
cd frontend && npm run dev
# → http://localhost:5173 (hot-reload)
```

For the full containerized stack: `docker compose up`.
