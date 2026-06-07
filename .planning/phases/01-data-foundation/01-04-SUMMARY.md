---
phase: 01-data-foundation
plan: "04"
subsystem: frontend-infra-devx
tags: [vue3, vite, pinia, axios, csrf, docker, docker-compose, nginx, mcp, readme]
dependency_graph:
  requires:
    - 01-03 (SecurityConfig JSON handlers, /api/auth/login + /api/auth/me + /api/auth/personas, CookieCsrfTokenRepository)
    - 01-02 (seeded app_users, portfolios; docker/db/00-init.sql created)
    - 01-01 (backend Maven wrapper, Spring Boot scaffold, /actuator/health)
  provides:
    - Vue 3 SPA shell: login form + one-click persona switcher + dashboard placeholder
    - src/api/auth.ts: Axios withCredentials + X-XSRF-TOKEN CSRF interceptor; login/logout/me/personas
    - src/stores/auth.ts: Pinia auth store (username, persona, portfolioId, authenticated); refresh() restores session
    - src/router/index.ts: vue-router /login + / with nav guard calling refresh on first load
    - backend/Dockerfile: multi-stage eclipse-temurin:21-jdk → :21-jre, layertools, non-root, healthcheck
    - frontend/Dockerfile: node:22-alpine Vite build → nginx:alpine; nginx.conf SPA + /api proxy
    - docker-compose.yml: db (pgvector/pg16 + named volume + healthcheck) → backend (service_healthy) → frontend
    - .mcp.json: context7 + @henkey/postgres-mcp-server (non-deprecated Postgres MCP)
    - README.md: quickstart, demo personas, cold-start verification, OAuth upgrade path, DEVX-01 MCP setup
  affects:
    - All downstream phases (Phase 2+: frontend/backend fully dockerized and accessible)
    - Phase 3 (ECharts charts wired into this Vue shell)
    - Phase 6+ (BYO-key popup built into this shell)
tech_stack:
  added:
    - vue@3.5.34 (Composition API + <script setup>)
    - vite@8.0.16 (build tool + dev proxy)
    - "@vitejs/plugin-vue@6.0.6 (Vue SFC compilation)"
    - pinia@3.0.4 (state management)
    - axios@1.17.0 (HTTP client with withCredentials + CSRF interceptor)
    - vue-echarts@8.0.1 (peer dep installed; charts deferred to Phase 3)
    - echarts@6.1.0 (peer dep installed; charts deferred to Phase 3)
    - vue-router@4 (SPA routing)
    - nginx:alpine (Docker — serves the Vite build in prod)
    - node:22-alpine (Docker — builds the Vite project)
    - pgvector/pgvector:pg16 (Docker — Postgres with pgvector extension)
    - eclipse-temurin:21-jdk/jre (Docker — builds and runs Spring Boot)
  patterns:
    - "Axios interceptor reads XSRF-TOKEN cookie and attaches X-XSRF-TOKEN on mutating requests — SPA-safe CSRF (T-01-14)"
    - "Pinia store refresh() calls /api/auth/me to restore session after page reload (AUTH-02)"
    - "Navigation guard on protected routes: calls refresh() on first load, redirects to /login if 401"
    - "One-click persona switcher: fetches /api/auth/personas, calls loginAs(username, 'demo1234'), navigates to /"
    - "Backend multi-stage Dockerfile: layertools extract for layer caching (dependencies → spring-boot-loader → snapshot-dependencies → application)"
    - "Named Docker volume postgres_data (not bind mount) — required on Windows/WSL2 (T-01-12)"
    - "db healthcheck + backend depends_on condition: service_healthy — prevents HikariCP timeout on cold start (T-01-13)"
    - "SPRING_PROFILES_ACTIVE=demo in compose — activates seeder; no AI keys in compose (T-01-11)"
    - "nginx proxy_cookie_flags ~JSESSIONID secure samesite=lax — forwards session cookie with SameSite defence"
key_files:
  created:
    - frontend/package.json (vue, vite, @vitejs/plugin-vue, pinia, axios, vue-echarts, echarts, vue-router)
    - frontend/vite.config.ts (/api proxy to http://localhost:8080)
    - frontend/index.html (QuantLens title)
    - frontend/src/main.ts (createApp + createPinia + router)
    - frontend/src/App.vue (RouterView root)
    - frontend/src/api/auth.ts (Axios withCredentials + CSRF interceptor + login/logout/me/personas)
    - frontend/src/stores/auth.ts (Pinia auth store: loginAs/refresh/logout)
    - frontend/src/router/index.ts (vue-router /login + / with auth guard)
    - frontend/src/views/LoginView.vue (login form + one-click persona switcher + demo1234 hint)
    - frontend/src/views/DashboardView.vue (persona/username/portfolioId display + session-persists note)
    - backend/Dockerfile (eclipse-temurin:21-jdk build → :21-jre runtime, non-root, healthcheck)
    - frontend/Dockerfile (node:22-alpine build → nginx:alpine serve)
    - frontend/nginx.conf (SPA try_files + /api proxy_pass to backend:8080)
    - docker-compose.yml (db + backend + frontend; named volume; healthcheck; service_healthy)
    - .mcp.json (context7 + @henkey/postgres-mcp-server)
    - README.md (quickstart, personas, verification, OAuth upgrade path, DEVX-01)
  modified:
    - frontend/vite.config.ts (added /api proxy)
    - frontend/index.html (updated title)
key-decisions:
  - "layertools (not new tools extract --layers --launcher) used in backend Dockerfile — tools extract outputs to a named subdirectory (backend-0.0.1-SNAPSHOT/), breaking the COPY --from=build paths; layertools outputs directly to /workspace, matching the Dockerfile's COPY targets. Both work; layertools is deprecated-warning-only, still functional."
  - "vue-echarts and echarts installed as dependencies but no chart components created — packages needed in package.json for Phase 3 import without version changes"
  - "@henkey/postgres-mcp-server chosen (not @modelcontextprotocol/server-postgres which is deprecated/archived July 2025 with SQL injection CVE)"
  - "proxy_cookie_flags ~JSESSIONID secure samesite=lax added to nginx.conf to enforce SameSite=Lax on the session cookie as it crosses the nginx→browser boundary"
  - "Cold-start checkpoint auto-approved per autonomous run instructions — verified programmatically via curl and docker compose ps"
requirements-completed: [DATA-01, DEVX-01]
duration: ~45min
completed: 2026-06-07
---

# Phase 01 Plan 04: Vue 3 Shell + Dockerfiles + docker-compose + .mcp.json Summary

**Vue 3 (Vite + Pinia + Axios CSRF interceptor) login/persona-switcher shell + multi-stage Dockerfiles + docker-compose wiring db→backend→frontend with pgvector named volume and healthcheck ordering, closing the Phase 1 walking skeleton end-to-end with a verified cold-start (alice/demo1234 → portfolioId:1 live from DB).**

## Performance

- **Duration:** ~45 minutes
- **Started:** 2026-06-07T00:30:00Z
- **Completed:** 2026-06-07T01:14:06Z
- **Tasks:** 2 (npm legitimacy gate + final cold-start auto-approved per autonomous mode)
- **Files created:** 16
- **Files modified:** 2

## Accomplishments

- Vue 3 shell builds (`npm run build` exits 0, dist/ produced, 96 modules) with login form + one-click persona switcher (fetched from `/api/auth/personas`) showing the `demo1234` hint; DashboardView displays live `persona`/`username`/`portfolioId` from the Pinia auth store
- Axios client sets `withCredentials=true` and reads `XSRF-TOKEN` cookie on every response, attaching `X-XSRF-TOKEN` on mutating requests — correct SPA CSRF pattern for `CookieCsrfTokenRepository`
- `docker compose up` cold-start verified: db healthy, backend healthy, frontend up; seed counts confirmed (securities=16, ohlcv_bars=8064, users=3, factors=504); login curl returns `{"authenticated":true,"username":"alice"}`; `/api/auth/me` returns `{"username":"alice","persona":"Growth","portfolioId":1}`
- All npm packages verified on registry before install (autonomous legitimacy gate): vue 3.5.34, vite 8.0.16, pinia 3.0.4, axios 1.17.0, vue-echarts 8.0.1, echarts 6.1.0, @upstash/context7-mcp 3.1.0, @henkey/postgres-mcp-server 1.0.5
- `.mcp.json` uses `@henkey/postgres-mcp-server` — the deprecated `@modelcontextprotocol/server-postgres` (archived July 2025, SQL injection CVE) is explicitly not used

## Task Commits

1. **Task 1: Vue 3 shell** - `7e2f52f` (feat)
2. **Task 2: Dockerfiles, docker-compose, .mcp.json, README** - `0f0e31c` (feat)

## Verified Package Versions (npm legitimacy gate — auto-approved)

| Package | Resolved version | Registry verified |
|---------|-----------------|-------------------|
| vue | 3.5.34 | npmjs.com/package/vue |
| vite | 8.0.16 | npmjs.com/package/vite |
| @vitejs/plugin-vue | 6.0.7 | npmjs.com/package/@vitejs/plugin-vue |
| pinia | 3.0.4 | npmjs.com/package/pinia |
| axios | 1.17.0 | npmjs.com/package/axios |
| vue-echarts | 8.0.1 | npmjs.com/package/vue-echarts |
| echarts | 6.1.0 | npmjs.com/package/echarts |
| @upstash/context7-mcp | 3.1.0 | npmjs.com/package/@upstash/context7-mcp |
| @henkey/postgres-mcp-server | 1.0.5 | npmjs.com/package/@henkey/postgres-mcp-server |

## Cold-Start Verification Results (auto-verified)

| Check | Command | Result |
|-------|---------|--------|
| `docker compose config` | config syntax | Exit 0 |
| `docker build -f frontend/Dockerfile frontend` | frontend image | Exit 0 — node:22-alpine → nginx:alpine |
| `docker build -f backend/Dockerfile backend` | backend image | Exit 0 — eclipse-temurin:21-jdk → :21-jre |
| `docker compose up -d` | cold start | All 3 containers up |
| `docker compose ps` | health check | db (healthy), backend (healthy), frontend (up) |
| `SELECT count(*) FROM securities` | seed count | 16 |
| `SELECT count(*) FROM ohlcv_bars` | seed count | 8064 |
| `SELECT count(*) FROM app_users` | seed count | 3 |
| `SELECT count(*) FROM factor_returns` | seed count | 504 |
| pgvector schema | `udt_name` | vector |
| `POST /api/auth/login alice/demo1234` | login | `{"authenticated":true,"username":"alice"}` |
| `GET /api/auth/me` (with cookie) | whoami | `{"username":"alice","persona":"Growth","portfolioId":1}` |
| `GET /api/auth/personas` | public list | 3 personas with demo1234 hint |
| `GET http://localhost:5173/` | frontend | nginx serves index.html |

## Files Created/Modified

- `frontend/package.json` — vue, vite, pinia, axios, vue-echarts, echarts, vue-router
- `frontend/vite.config.ts` — /api proxy to localhost:8080
- `frontend/index.html` — QuantLens title
- `frontend/src/main.ts` — createApp + createPinia + router
- `frontend/src/App.vue` — RouterView root (global CSS reset)
- `frontend/src/api/auth.ts` — Axios withCredentials=true, XSRF-TOKEN cookie interceptor, login/logout/me/personas
- `frontend/src/stores/auth.ts` — Pinia auth store; refresh() calls /api/auth/me to restore session
- `frontend/src/router/index.ts` — vue-router /login + / protected with nav guard
- `frontend/src/views/LoginView.vue` — login form + persona switcher + demo1234 hint displayed
- `frontend/src/views/DashboardView.vue` — persona/username/portfolioId from live DB read
- `backend/Dockerfile` — multi-stage eclipse-temurin:21-jdk→:21-jre, layertools, non-root, healthcheck
- `frontend/Dockerfile` — node:22-alpine Vite build → nginx:alpine serve
- `frontend/nginx.conf` — SPA try_files + /api proxy_pass to backend:8080 + SameSite=Lax cookie flags
- `docker-compose.yml` — db (pgvector/pg16, named volume, healthcheck) → backend (service_healthy) → frontend
- `.mcp.json` — context7 + project-db (@henkey/postgres-mcp-server)
- `README.md` — quickstart, verification commands, OAuth upgrade path, DEVX-01 MCP setup

## Decisions Made

1. **layertools not tools extract:** Spring Boot 3.3+ recommends `java -Djarmode=tools extract --layers --launcher` over the old `layertools`, but the new command extracts to a subdirectory named after the jar file (`backend-0.0.1-SNAPSHOT/`), not to `/workspace` directly. The COPY instructions in stage 2 reference `/workspace/dependencies/`, `/workspace/application/`, etc., which only exist with the old `layertools` command. Reverted to `layertools` (deprecated-warning-only, still fully functional) rather than adding a `mv` command or shell glob.

2. **@henkey/postgres-mcp-server in .mcp.json:** Chosen over the deprecated `@modelcontextprotocol/server-postgres` (archived July 2025, SQL injection CVE) per RESEARCH.md Package Audit and CONTEXT.md DEVX-01 guidance. Verified at npmjs.com/package/@henkey/postgres-mcp-server (version 1.0.5, actively maintained).

3. **Autonomous checkpoint handling:** Both plan checkpoints (npm legitimacy gate, final cold-start verification) were auto-approved per the `<autonomous_checkpoint_handling>` instructions in the objective. The legitimacy gate was satisfied by running `npm view <pkg> version` for all packages before install. The cold-start gate was satisfied by programmatic `docker compose up`, `docker compose ps`, curl verification, and `docker compose down`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] layertools vs tools extract directory structure**
- **Found during:** Task 2 — first `docker compose up` failed with "not found: /workspace/spring-boot-loader"
- **Issue:** Updated Dockerfile to use `java -Djarmode=tools -jar target/*.jar extract --layers --launcher` (the non-deprecated command per Spring Boot 3.3+). The new command extracts to a subdirectory named after the jar file (`backend-0.0.1-SNAPSHOT/`), not to the current working directory. The COPY commands in stage 2 expected `/workspace/dependencies/`, `/workspace/application/`, etc., which only exist with the old layertools output.
- **Fix:** Reverted to `java -Djarmode=layertools -jar target/*.jar extract`. The deprecated warning is cosmetic — the command still works correctly and outputs the expected flat directory structure.
- **Files modified:** `backend/Dockerfile`
- **Verification:** `docker build -f backend/Dockerfile backend` exits 0; `docker compose up` brings backend healthy
- **Committed in:** `0f0e31c` (Task 2 commit, updated before final commit)

---

**Total deviations:** 1 auto-fixed (Rule 1 - bug)
**Impact on plan:** Single Dockerfile layer command change. No scope creep, no architectural impact.

## Known Stubs

None — all data flows are wired. The DashboardView displays `portfolioId` read live from the `/api/auth/me` endpoint, which reads from the seeded `portfolios` table. The login flow is fully functional end-to-end. The only intentional "placeholder" text in DashboardView explicitly documents that ECharts charts arrive in Phase 3 — this is per-plan behavior, not a stub.

## Threat Flags

None — no new network endpoints or trust boundaries beyond the plan's threat model. All STRIDE mitigations implemented:
- T-01-12 (named volume postgres_data) — DONE
- T-01-13 (db healthcheck + service_healthy + start_period 20s) — DONE
- T-01-14 (Axios XSRF-TOKEN interceptor + nginx proxy_cookie_flags SameSite=Lax) — DONE
- T-01-SC (npm legitimacy gate + @henkey/postgres-mcp-server replacing deprecated package) — DONE
- T-01-11 (dev credentials documented as non-secret in README + compose) — DONE

## Self-Check: PASSED

Files verified to exist:
- `frontend/src/api/auth.ts` — FOUND
- `frontend/src/stores/auth.ts` — FOUND
- `frontend/src/router/index.ts` — FOUND
- `frontend/src/views/LoginView.vue` — FOUND
- `frontend/src/views/DashboardView.vue` — FOUND
- `backend/Dockerfile` — FOUND
- `frontend/Dockerfile` — FOUND
- `frontend/nginx.conf` — FOUND
- `docker-compose.yml` — FOUND
- `.mcp.json` — FOUND
- `README.md` — FOUND

Commits verified:
- `7e2f52f`: feat(01-04): Vue 3 shell — login/persona switcher + dashboard placeholder + Axios auth client — FOUND
- `0f0e31c`: feat(01-04): Dockerfiles, docker-compose, .mcp.json, README — full cold-start verified — FOUND

Cold-start verification: PASSED (all curl checks, seed counts, service health — see Verification Results table)
