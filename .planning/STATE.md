# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-06-07)

**Core value:** A working, screenshot-ready dashboard that demonstrates real quantitative-finance analytics narrated by a real Spring AI layer — convincing with zero setup, genuinely live when you add a key.
**Current focus:** Phase 1 — Data Foundation

## Current Position

Phase: 1 of 10 (Data Foundation)
Plan: 0 of TBD in current phase
Status: Ready to plan
Last activity: 2026-06-07 — Roadmap created; 32 requirements mapped across 10 phases

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: —
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| - | - | - | - |

**Recent Trend:**
- Last 5 plans: —
- Trend: —

*Updated after each plan completion*

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- Stack locked: Java 21, Spring Boot 3.5.13, Spring AI 1.1.6 (BOM-pinned), finmath-lib 6.1.7, Hipparchus 4.0.3, pgvector/pg16, Vue 3 + ECharts 5, Finnhub for live quotes
- Market data: Finnhub (io.finnhub:kotlin-client:2.0.22) — 60 req/min free tier replaces Alpha Vantage
- Demo embeddings: pre-computed Flyway-seeded float arrays — no live embedding API at docker compose up
- Heston params: fixed illustrative (kappa=2, theta=0.04, sigma_v=0.3, rho=-0.7, v0=0.04), Feller condition enforced in code
- Embedding dimension: 1536 (OpenAI text-embedding-3-small) — locked at Flyway migration time, document in TECH_DECISIONS.md

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 2 research spike needed: ADF critical values (MacKinnon 1994) for hand-rolled Engle-Granger; finmath-lib HestonModel discretization (Euler-Maruyama vs Milstein); block bootstrap via Hipparchus
- Phase 4 research spike needed: Flyway binary column type for pre-computed float[] embeddings; structure-aware SEC EDGAR chunking; model.mutate() per-session key injection at Spring AI 1.1.6 (GitHub issue #2731)
- Phase 9 research spike needed: Spring AI MCP Streamable HTTP transport path config; Spring Security filter chain integration with spring-ai-mcp-server-boot-starter 1.1.6

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| v2 Quant | SIM-04: Live Heston calibration from real data | Deferred | Roadmap init |
| v2 Quant | ATTR-02: 5-factor Fama-French upgrade | Deferred | Roadmap init |
| v2 Quant | ARB-02: Additional arb detectors | Deferred | Roadmap init |
| v2 Quant | OPT-01: Efficient frontier / mean-variance optimization | Deferred | Roadmap init |
| v2 Quant | BACK-01: Backtesting engine | Deferred | Roadmap init |
| v2 AI | AI-09: Streaming AI responses (SSE) | Deferred | Roadmap init |
| v2 Platform | MCP-03: @McpResource for filing documents | Deferred | Roadmap init |
| v2 Platform | AUTH-04: Real OAuth implementation | Deferred | Roadmap init |

## Session Continuity

Last session: 2026-06-07
Stopped at: Roadmap and STATE.md created; REQUIREMENTS.md traceability table updated; ready to run /gsd:plan-phase 1
Resume file: None
