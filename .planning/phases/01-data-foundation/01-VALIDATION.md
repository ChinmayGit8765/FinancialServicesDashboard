---
phase: 1
slug: data-foundation
status: approved
nyquist_compliant: true
wave_0_complete: true
created: 2026-06-07
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + Spring Boot Test (managed by Boot parent); Testcontainers Postgres (pgvector image) for integration |
| **Config file** | `backend/src/test/resources/application-test.yml` (Wave 0 creates) |
| **Quick run command** | `./mvnw -q test -Dtest=QuantLensModulithTest` (set `JAVA_HOME` to Temurin 21 first) |
| **Full suite command** | `./mvnw verify` |
| **Estimated runtime** | ~60–120 seconds (Testcontainers cold start dominates) |

> **Host note:** `export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-21.0.11.10-hotspot"` before any `./mvnw` invocation — default `java` on PATH is JDK 11.

---

## Sampling Rate

- **After every task commit:** Run `./mvnw -q test -Dtest=QuantLensModulithTest` (module-boundary check, <10s)
- **After every plan wave:** Run `./mvnw verify`
- **Before `/gsd:verify-work`:** Full suite green + all observable signals below pass
- **Max feedback latency:** ~120 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (planner-assigned) | — | 0 | — | — | Test scaffolds + test profile exist | infra | `./mvnw -q test-compile` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | DATA-01 | — | 3 containers reach healthy from cold start | smoke | `docker compose up -d && docker compose ps` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | DATA-02 | — | Seeder populates securities/OHLCV/users/factors idempotently | integration | `./mvnw test -Dtest=SeedRunnerIntegrationTest` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | DATA-03 | — | `vector_store.embedding` is `vector(1536)` | integration | `./mvnw test -Dtest=VectorStoreSchemaTest` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | AUTH-01 | T-1 session | login 200 JSON / bad creds 401 JSON | integration | `./mvnw test -Dtest=AuthIntegrationTest` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | AUTH-02 | T-1 session | session cookie persists; persona scopes portfolio | integration | `./mvnw test -Dtest=PersonaIntegrationTest` | ❌ W0 | ⬜ pending |
| (planner-assigned) | — | 1+ | DEVX-01 | — | `.mcp.json` present + valid context7 entry | file assertion | `test -f .mcp.json` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky. Task IDs are assigned by the planner; rows map requirements → expected automated checks.*

---

## Wave 0 Requirements

- [ ] `backend/src/test/resources/application-test.yml` — test profile (Testcontainers pgvector)
- [ ] `backend/src/test/java/com/quantlens/QuantLensModulithTest.java` — module boundary verification
- [ ] `backend/src/test/java/com/quantlens/seed/SeedRunnerIntegrationTest.java` — DATA-02
- [ ] `backend/src/test/java/com/quantlens/infra/VectorStoreSchemaTest.java` — DATA-03
- [ ] `backend/src/test/java/com/quantlens/security/AuthIntegrationTest.java` — AUTH-01, AUTH-02
- [ ] `backend/src/test/java/com/quantlens/security/PersonaIntegrationTest.java` — AUTH-02 persona scoping

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Full cold-start stack health | DATA-01 | Requires Docker daemon; not run in unit CI | `docker compose down -v && docker compose up -d`, then `docker compose ps` → all healthy |
| Login flow end-to-end via browser/curl | AUTH-01/02 | Exercises real cookie + CSRF round-trip | `curl -c c.txt -X POST .../api/auth/login -d user...` then `curl -b c.txt .../api/auth/me` returns 200 |
| Claude Code can connect to MCP servers | DEVX-01 | Requires Claude Code runtime | Run `claude mcp list` / open project; context7 + Postgres MCP reachable |

---

## Validation Sign-Off

- [x] All tasks have automated verify or Wave 0 dependencies
- [x] Sampling continuity: no 3 consecutive tasks without automated verify
- [x] Wave 0 covers all MISSING references
- [x] No watch-mode flags
- [x] Feedback latency < 120s
- [x] `nyquist_compliant: true` set in frontmatter

**Approval:** approved 2026-06-07
