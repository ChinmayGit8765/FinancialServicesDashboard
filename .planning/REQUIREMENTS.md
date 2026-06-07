# Requirements: QuantLens — AI Portfolio & Market Intelligence Dashboard

**Defined:** 2026-06-07
**Core Value:** A working, screenshot-ready dashboard that demonstrates real quantitative-finance analytics narrated by a real Spring AI layer — convincing with zero setup, genuinely live when you add a key.

## v1 Requirements

Requirements for the initial release (the screenshot-ready demo). Each maps to roadmap phases.

### Foundation & Infrastructure

- [x] **DATA-01**: Developer can launch the full stack (backend, frontend, Postgres+pgvector) with a single `docker compose up`
- [x] **DATA-02**: On first start the system seeds demo users, ~15 securities, ~2 years of daily OHLCV price series, plus benchmark and factor-return series
- [x] **DATA-03**: pgvector schema initializes automatically on a cold start so RAG storage works with no manual setup

### Authentication & Demo Users

- [x] **AUTH-01**: User can log in by selecting one of three seeded demo personas (e.g. growth / income / balanced)
- [x] **AUTH-02**: User session persists across page refresh and scopes which portfolio is shown
- [ ] **AUTH-03**: README documents how to wire real OAuth (Google/GitHub) as the production upgrade path

### Portfolio

- [ ] **PORT-01**: User can view holdings with current value, weight, cost basis, and unrealized P&L per position
- [ ] **PORT-02**: User can view portfolio-level P&L (total unrealized gain/loss and daily change) as a time-series curve
- [ ] **PORT-03**: User can view an allocation breakdown by sector / asset class (pie or treemap)
- [x] **PORT-04**: User can view transaction history (buy/sell log with date, quantity, price, running cost basis)
- [ ] **PORT-05**: User can compare portfolio return against an S&P 500 proxy benchmark on the same chart

### Risk Metrics

- [ ] **RISK-01**: User can view a risk scorecard — Sharpe ratio, annualized volatility, max drawdown, beta vs benchmark, and 95% VaR
- [ ] **RISK-02**: User can view a pairwise return-correlation heatmap across holdings
- [ ] **RISK-03**: User can view VaR computed by both parametric and historical methods side by side

### Stochastic Forecasting ("Potential Futures")

- [ ] **SIM-01**: User can view a Monte Carlo fan chart of projected portfolio value with percentile bands (p5/p25/p50/p75/p95)
- [ ] **SIM-02**: User can switch the forecast between four models — GBM, Merton jump-diffusion, Heston, and historical block bootstrap
- [ ] **SIM-03**: Documentation explains the rationale, assumptions, and limitations of each stochastic model and why it was chosen

### Causation & Attribution

- [ ] **ATTR-01**: User can view Fama-French 3-factor attribution of portfolio returns (alpha + factor betas as a contribution chart) explaining what drives returns

### Arbitrage Detection

- [ ] **ARB-01**: User can view a cointegration-based pairs scanner listing candidate pairs with cointegration p-value, current spread Z-score, and mean-reversion signal

### AI Layer (Spring AI, multi-provider)

- [ ] **AI-01**: All AI features work with zero API key via seeded responses (demo mode), authored against the seeded data so they match the charts
- [ ] **AI-02**: User can open a BYO-key popup, choose a provider (Anthropic Claude or OpenAI), and enter a session-only key that switches AI features to live
- [ ] **AI-03**: User can ask freeform natural-language questions about the portfolio in a chat that retains conversation memory
- [ ] **AI-04**: User can ask questions answered from embedded 10-K / earnings filings via RAG over pgvector
- [ ] **AI-05**: User can request a live quote in chat and see the LLM invoke a quote tool (tool calling)
- [ ] **AI-06**: At least one AI response is delivered as typed structured output that drives a chart directly
- [ ] **AI-07**: User can click a holding to get an AI-generated "explain this position" narrative
- [ ] **AI-08**: User sees an AI-generated daily portfolio commentary on the dashboard

### MCP (product)

- [ ] **MCP-01**: The app exposes portfolio analytics as an MCP server via `@McpTool` (e.g. `get_portfolio_summary`, `get_risk_metrics`, `get_position_detail`)
- [ ] **MCP-02**: Documentation shows how to connect an MCP client (e.g. Claude Code) to the server

### Developer Tooling

- [x] **DEVX-01**: The repo configures dev-side MCP servers for Claude Code (e.g. context7 for current Spring docs, a Postgres MCP) to aid development

### Frontend

- [ ] **UI-01**: The Vue 3 (Composition API) front end presents all the above in a cohesive single-page dashboard using ECharts visualizations

### Documentation

- [ ] **DOCS-01**: README includes screenshots demonstrating the app running live with an LLM (captured via live mode)

## Definition of Done

Cross-cutting quality gates that apply to the whole v1, not a single phase:

- Risk metrics, Monte Carlo simulations, and cointegration tests each have golden-value unit tests proving correctness (credibility is the core value)
- Spring AI dependencies are BOM-pinned to 1.1.6 and artifact IDs validated against the current `spring-ai-starter-*` naming
- LLM session keys are never logged, echoed to the client, or persisted
- The app runs end-to-end from `docker compose up` with no API key (full demo mode)
- `spring.ai.vectorstore.pgvector.initialize-schema=false` is set explicitly (Flyway V1 owns the `vector_store` DDL — must stay false in all phases); monetary values use `BigDecimal`

## v2 Requirements

Deferred to a future release. Tracked but not in the current roadmap.

### Quant
- **SIM-04**: Live Heston parameter calibration from real return data when a key is provided (v1 uses fixed illustrative params)
- **ATTR-02**: 5-factor Fama-French upgrade (add RMW, CMA)
- **ARB-02**: Additional arbitrage detectors (covered-call parity, FX CIP violations)
- **OPT-01**: Efficient frontier / mean-variance portfolio optimization
- **BACK-01**: Backtesting engine with look-ahead-bias prevention

### AI / Platform
- **AI-09**: Streaming AI responses (SSE) for the Q&A chat
- **MCP-03**: `@McpResource` exposure of filing documents
- **AUTH-04**: Real OAuth (Google/GitHub) implementation

## Out of Scope

Explicitly excluded for v1. Documented to prevent scope creep.

| Feature | Reason |
|---------|--------|
| Real OAuth implementation | 2–3 days of plumbing, zero AI/quant signal; documented as upgrade path instead (AUTH-03) |
| Real brokerage integration (Plaid/Alpaca/IBKR) | Days of OAuth + broker negotiation; contradicts zero-setup demo; seeded portfolio + live quotes suffice |
| Order execution / trading | Regulatory red flags; analytics-only showcase |
| Real-time WebSocket price streaming | Fragile demo without a live feed; no AI/quant signal; refresh-on-demand suffices |
| Fine-tuned / custom LLM | Weeks of ML work; the signal is Spring AI integration, not model training |
| Sentiment analysis from news/social feeds | Costly APIs, low-quality signal; RAG over 10-Ks is more credible |
| Mobile / PWA / native app | Web-first constraint; no backend/AI signal |
| Persisting LLM API keys | Security anti-pattern; session-only is the right answer |
| CI/CD / Kubernetes | Zero resume delta; Docker Compose is reproducible enough |
| Watchlists / alerts / user-created portfolios | User-management complexity, no resume signal; three seeded personas suffice |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| DATA-03 | Phase 1 | Complete |
| AUTH-01 | Phase 1 | Complete |
| AUTH-02 | Phase 1 | Complete |
| DEVX-01 | Phase 1 | Complete |
| PORT-01 | Phase 2 | Pending |
| PORT-02 | Phase 2 | Pending |
| PORT-03 | Phase 2 | Pending |
| PORT-04 | Phase 2 | Complete |
| PORT-05 | Phase 2 | Pending |
| AUTH-03 | Phase 3 | Pending |
| UI-01 | Phase 3 | Pending |
| RISK-01 | Phase 4 | Pending |
| RISK-02 | Phase 4 | Pending |
| RISK-03 | Phase 4 | Pending |
| ATTR-01 | Phase 4 | Pending |
| ARB-01 | Phase 4 | Pending |
| SIM-01 | Phase 5 | Pending |
| SIM-02 | Phase 5 | Pending |
| SIM-03 | Phase 5 | Pending |
| AI-01 | Phase 6 | Pending |
| AI-02 | Phase 6 | Pending |
| AI-07 | Phase 6 | Pending |
| AI-08 | Phase 6 | Pending |
| AI-03 | Phase 7 | Pending |
| AI-04 | Phase 7 | Pending |
| AI-05 | Phase 8 | Pending |
| AI-06 | Phase 8 | Pending |
| MCP-01 | Phase 9 | Pending |
| MCP-02 | Phase 9 | Pending |
| DOCS-01 | Phase 10 | Pending |

**Coverage:**
- v1 requirements: 32 total
- Mapped to phases: 32
- Unmapped: 0

---
*Requirements defined: 2026-06-07*
*Last updated: 2026-06-07 — traceability populated by roadmapper*
