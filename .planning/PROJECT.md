# QuantLens — AI Portfolio & Market Intelligence Dashboard

> *Working title — rename freely.*

## What This Is

An AI-augmented portfolio & market intelligence dashboard that fuses a quantitative-finance backend with a modern Spring AI layer. A **Spring Boot** service computes genuine portfolio analytics — P&L, allocation, and risk metrics (Sharpe, VaR, beta, volatility, correlation matrices), **stochastic forecasts of potential futures** (Monte Carlo across multiple models), **causal return/risk attribution**, and **arbitrage detection**. A **Spring AI** layer on top adds natural-language Q&A over the portfolio, RAG over earnings reports / 10-Ks / filings, tool calling for live quotes, and structured output that drives the **Vue 3** charts directly. It is built as a flagship portfolio/resume piece signalling a quant-leaning AI engineer: Java/Spring depth + modern Spring AI + Vue + quantitative finance.

## Core Value

A working, screenshot-ready dashboard that demonstrates **real quantitative-finance analytics narrated by a real Spring AI layer** — instantly convincing with zero setup (seeded data + seeded LLM responses), yet genuinely live the moment you paste your own LLM key.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

<!-- Current scope. All are hypotheses until shipped and validated. -->

**Quant backend (Spring Boot)**
- [ ] Portfolio model: holdings, transactions, P&L, allocation breakdowns
- [ ] Risk metrics: Sharpe, VaR, beta, volatility, correlation matrices
- [ ] Stochastic forecasting: Monte Carlo across multiple models (GBM + e.g. jump-diffusion / Heston / bootstrap) rendered as fan charts of projected portfolio paths
- [ ] Causation logic: factor attribution / causal analysis of what's driving returns and risk
- [ ] Arbitrage detectors: statistical-arb / pairs (cointegration) / parity-violation scanning over the seeded universe
- [ ] Live quote integration via a market-data API (consumed through Spring AI tool calling)
- [ ] **Documented rationale for each stochastic model** — why GBM vs jump-diffusion vs Heston vs bootstrap, the assumptions each makes, when it applies, and its limitations (written for the README/docs, not just code comments)

**AI layer (Spring AI, multi-provider)**
- [ ] Multi-provider ChatClient (Anthropic Claude + OpenAI); provider + key chosen at runtime
- [ ] Seeded LLM response mode — realistic, authored-to-look-LLM-generated output so the app works with zero key
- [ ] BYO-key popup that flips seeded → live against the user's own key (session-only)
- [ ] Natural-language Q&A over the portfolio
- [ ] RAG over earnings reports / 10-Ks / filings into a vector store
- [ ] Tool calling to pull live quotes
- [ ] Structured output that drives Vue charts directly
- [ ] "Explain this position" AI panel
- [ ] AI-generated daily portfolio commentary
- [ ] At least one capability exposed as a declarative `@McpTool` (product MCP server)

**Frontend (Vue 3 Composition API)**
- [ ] Dashboard with ECharts/Chart.js visualizations (allocation, P&L, risk, correlation heatmap, forecast fan charts)
- [ ] BYO LLM-key popup (session-only) for live AI demos and README screenshots
- [ ] AI panels: explain-this-position, daily commentary, Q&A chat

**Platform / demo**
- [ ] Seeded demo users with pre-built portfolios (log in as a demo user)
- [ ] Docker / docker-compose for the full stack (from day one)
- [ ] Dev-side MCP servers configured for Claude Code (e.g. context7 for current Spring AI docs, a Postgres MCP)
- [ ] Documentation on wiring real OAuth (Google/GitHub) as the multi-user upgrade path

### Out of Scope

<!-- Explicit boundaries. Includes reasoning to prevent re-adding. -->

- Real identity-provider auth (OAuth) **implementation** — demo uses seeded users; OAuth is documented as the upgrade path, not built for v1
- Real brokerage / trading integration — seeded portfolio + live quotes only; no order execution
- Persisting user-supplied LLM API keys — session-only, for safety and demo simplicity
- Real-money or order execution — analytics only, never places trades
- Native mobile app — web-first

## Context

- **Author profile:** quantitative finance + software engineering background; this is a flagship portfolio/resume piece deliberately positioned to read as a quant-leaning AI engineer — the rare profile most devs can't fake.
- **Tech currency matters:** Spring AI is well past 1.0 GA (current stream ~1.0.8 / 1.1.7, with 2.0.0 in milestones as of mid-2026). The build deliberately showcases current building blocks (ChatClient, advisors, tool calling, structured outputs, memory, vector stores) and the genuine frontier (agentic workflows + declarative `@McpTool` development + MCP Security).
- **Demo must impress instantly:** seeded data + seeded LLM responses mean the README screenshots look like a live AI-driven quant dashboard with zero configuration; the real Spring AI plumbing is underneath for anyone who adds a key.

## Constraints

- **Tech stack:** Spring Boot + Spring AI (multi-provider), Vue 3 Composition API, ECharts/Chart.js, Postgres (with pgvector likely for RAG), Docker / docker-compose — Java/Spring + Vue + quant is the intended signal.
- **Security:** never persist user-supplied LLM keys; the popup key is session-only.
- **Demo-first:** every feature must degrade gracefully to a seeded mode with no external keys or network dependencies, so the app always runs and always demos.
- **Quant credibility:** risk, stochastic-forecasting, causal, and arbitrage math must be correct and defensible — this is the part the author's fin-math background makes credible.

## Key Decisions

<!-- Decisions that constrain future work. -->

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Multi-provider Spring AI (Claude + OpenAI) | Strongest "Spring AI depth" signal; flexible for whatever key a viewer has | — Pending |
| Seeded LLM responses + live BYO-key toggle | App is always demo-able with zero setup, yet the real plumbing is genuinely there | — Pending |
| Seeded demo users; OAuth documented, not built | Fast to a demo-able state while still showing the production auth path | — Pending |
| Session-only LLM keys (never persisted) | Safety + simplicity; matches the "try it / screenshot it" intent | — Pending |
| Docker / docker-compose from day one | Reproducible, deployable, credible portfolio artifact | — Pending |
| Dev MCP servers + product `@McpTool` | The freshest thing on a 2026 resume; aids development and showcases MCP | — Pending |
| Expand quant scope: stochastic futures + causation + arbitrage | Differentiates from generic AI dashboards; leans into fin-math credibility | — Pending |
| Prefer established quant/math libraries over hand-rolled math | Correctness, credibility, and speed — don't reinvent Monte Carlo / stats primitives (research to identify the actual libs) | — Pending |
| Document the rationale behind each stochastic-model choice | Shows fin-math judgement, not just coding; turns the math into a resume talking point | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd:complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-06-07 after initialization*
