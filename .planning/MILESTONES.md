# Milestones

## v1.0 (Shipped: 2026-06-10)

**QuantLens — AI-augmented portfolio & market intelligence dashboard.** A demo-first, screenshot-ready
app that fuses quantitative finance with modern Spring AI engineering: it runs convincingly with zero
setup (no keys) and turns genuinely live when you paste your own LLM key.

**Scope:** 10 phases, 33 plans, 41 tasks. 32/32 v1 requirements delivered. Backend 219 tests + frontend
86 tests green. Java 21 · Spring Boot 3.5.13 · Spring AI 1.1.6 · Spring Modulith 1.4.11 · pgvector/pg16 ·
Vue 3 + Vite + Pinia + ECharts. `docker compose up` from a clean checkout.

**Key accomplishments:**

- **Data foundation (P1):** one-command Docker stack (db → backend → frontend, healthcheck-ordered,
  pgvector named volume); Flyway schema + idempotent correlated-GBM seeder (~15 securities × ~2y OHLCV,
  S&P 500 benchmark, Fama-French factors); 3 seeded demo personas with session-scoped portfolios; locked
  1536-dim embedding. Verified cold-start: alice/demo1234 → portfolioId:1 live from DB.
- **Portfolio domain (P2):** holdings + P&L equity curve + allocation + paginated transactions (running
  cost basis) + benchmark rebasing — principal-scoped REST API, golden-value tested.
- **Frontend (P3):** Vue 3 single-page dashboard (ECharts), persona switcher with live re-scoping, auth
  flow + documented OAuth upgrade path.
- **Quant risk engine (P4):** Sharpe, VaR (historical + parametric), beta, volatility, correlation
  heatmap, Fama-French factor attribution (OLS), Engle-Granger cointegration pairs scanner — each backed
  by hand-computed correctness anchors, not just regression goldens.
- **Stochastic forecasting (P5):** 4-model Monte Carlo "potential futures" fan chart (GBM, Merton
  jump-diffusion, Heston stochastic vol, historical block bootstrap) via finmath-lib + Hipparchus, with
  a model-rationale doc (docs/MODELS.md).
- **Demo-mode AI seam (P6):** DemoModeAdvisor (CallAdvisor short-circuit) + LlmKeySessionHolder +
  ChatClientStrategy — seeded AI narration with an executable no-network proof; BYO-key popup flips
  demo↔live; key never logged/echoed/persisted.
- **RAG pipeline (P7):** pgvector ingestion + deterministic zero-key embedding (MurmurHash3 feature
  hashing) + QuestionAnswerAdvisor + NL Q&A chat with per-session conversation memory (IDOR-safe).
- **Live AI features (P8):** Finnhub @Tool quote integration (header auth, TTL cache, after-hours
  label, seeded fallback), multi-provider ChatClient (Claude + OpenAI), structured output (BeanOutputConverter)
  driving a typed chart — same component in demo + live.
- **Product MCP server (P9):** 3 @McpTool beans (get_portfolio_summary / get_risk_metrics /
  get_position_detail) over Streamable HTTP at /mcp, HTTP-Basic-gated (401 without creds), IDOR-safe
  principal resolution, static-message error hygiene, committed .mcp.json + README docs.
- **Polish & docs (P10):** springdoc OpenAPI spec (public, leak-free — key-intake & passwordHint excluded),
  Spring Modulith living boundary contract + generated module diagram, README screenshot scaffolding +
  Capture Guide, cosmetic cleanups.

**Engineering signals:** Spring Modulith boundaries enforced as a build-time ArchUnit contract; a clean
demo↔live seam (seeded fixtures ⇄ real LLM via session key); golden + hand-computed quant tests; threat
models per phase with security-auditor + code-review gates; zero-key RAG; an @McpTool product server.

**Deferred to human (live-key UAT):** capturing the live-mode README screenshots and the end-to-end MCP
wire test — both require the user's own LLM key and are the intended payoff of the BYO-key popup. The
underlying wiring is test-verified.

---
