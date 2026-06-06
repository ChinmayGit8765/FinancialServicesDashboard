# Feature Research

**Domain:** AI-augmented quantitative portfolio & market-intelligence dashboard (portfolio/resume piece)
**Researched:** 2026-06-07
**Confidence:** HIGH

---

## Framing: The Resume-Signal Lens

Every feature below is evaluated against one question: **does this feature convince a hiring manager or senior engineer within 90 seconds of looking at the README screenshot?**

Resume signal = (Java/Spring depth) + (Spring AI frontier features) + (genuine fin-math credibility) + (instant demo-ability). Features that don't move at least one of those dials are anti-features for v1.

---

## Feature Landscape

### Table Stakes (Demo Feels Hollow Without These)

These are the baseline. A reviewer who sees any of them missing will think "this is incomplete." They are not what you are being hired for, but missing them invalidates the rest.

| Feature | Why Expected | Complexity | Demo-mode Behavior | Notes |
|---------|--------------|------------|-------------------|-------|
| Holdings view — position list with current value, weight, cost basis, unrealized P&L per position | Any portfolio tool shows positions; missing = not a portfolio tool | LOW | Seeded portfolio (3–5 demo users, ~10–15 positions each); prices come from seeded price series | Static Postgres rows; seeded price series covers ~2 years of daily OHLCV |
| Portfolio-level P&L — total unrealized gain/loss, daily change, time-series P&L curve | Every dashboard from Yahoo Finance to Bloomberg shows this | LOW | Computed from seeded price series; displayed as line chart | Store transactions + price series; compute at query time or cache |
| Allocation breakdown — asset-class / sector / ticker pie or treemap | Users expect to see "what do I own and in what proportion" at a glance | LOW | Seeded sector tags on each holding | Requires sector metadata on securities |
| Transaction history — buy/sell log with date, quantity, price, running cost basis | Any brokerage UI has this | LOW | Seeded rows in transactions table | Simple CRUD list; paginated |
| Risk-metric scorecard — Sharpe ratio, annualized volatility, max drawdown, beta vs benchmark, VaR (95%) | These are the canonical five risk numbers; a quant dashboard missing them is not a quant dashboard | MEDIUM | Computed from seeded return series; benchmark = seeded S&P 500 proxy series | Must be correct; use Apache Commons Math or similar; do NOT hand-roll matrix operations |
| Correlation heatmap — pairwise return correlation across holdings | Standard quant visualization; tells the viewer "you understand diversification" | MEDIUM | Computed from seeded daily return matrix | ECharts heatmap; color scale from -1 (blue) to +1 (red) |
| Benchmark comparison — portfolio return vs S&P 500 proxy on same chart | Any serious tool compares to a benchmark | LOW | Seeded index return series | One extra time series alongside portfolio curve |
| Login / session — demo user switcher | Without this there is no "portfolio" concept and nothing to screenshot | LOW | Three seeded users (e.g., "Alice — growth", "Bob — income", "Charlie — balanced"); password-free or fixed password | No OAuth in v1; seeded users in DB; session cookie |
| Dockerized full stack, single command start | A portfolio piece that needs manual setup is one that never gets demoed | LOW | docker-compose up; seed runs on first start | Postgres + pgvector + backend + frontend containers |

---

### Differentiators (Resume-Signal Features)

These are what get you the interview call. Each maps to a specific hiring signal. Do not skip any of them — the combination is the argument.

#### Quant / Fin-Math Differentiators

| Feature | Resume Signal | Complexity | Demo-mode Behavior | Inter-feature Dependencies | Notes |
|---------|---------------|------------|-------------------|--------------------------|-------|
| **Monte Carlo fan chart — GBM baseline** | "Knows stochastic processes" — signal level 1 | MEDIUM | Seeded fan chart rendered from pre-computed percentile bands (p5/p25/p50/p75/p95); JSON stored in DB or fixture | Requires portfolio holdings + return series | ECharts confidence-band area series; 1 000–5 000 paths sufficient for visual demo |
| **Monte Carlo — Merton jump-diffusion model** | "Understands fat tails and discontinuities" — signal level 2 | MEDIUM | Same seeded percentile bands; labeled "Jump-Diffusion" in model selector | Requires GBM model as baseline to compare | Parameter: jump intensity λ, mean jump size μ_J, jump vol σ_J; explain in docs |
| **Monte Carlo — Heston stochastic-volatility model** | "Knows vol surface and variance mean-reversion" — signal level 3 | HIGH | Same seeded bands; labeled "Heston" | Requires GBM + jump-diffusion; needs parameter calibration from return series | Parameters: κ (reversion speed), θ (long-run vol), ξ (vol of vol), ρ (correlation); document each |
| **Monte Carlo — historical bootstrap (block bootstrap)** | "Understands model-free simulation and path dependence" — signal level 4 | MEDIUM | Seeded bands; labeled "Bootstrap" | Requires price history | Block length matters for autocorrelation; document why blocks not i.i.d. draws |
| **Model selector UI + documented rationale** | The selector alone shows engineering judgment; the rationale doc shows fin-math judgment | LOW (UI) / LOW (docs) | All four models selectable; fan charts swap on click; seeded data for all four | All four MC models | This turns into a README talking point and interview story |
| **Factor attribution — Fama-French 3-factor (or 5-factor) decomposition** | "Knows return attribution beyond just market beta" — differentiates from 95% of AI dashboards | HIGH | Seeded factor loadings (α, β_mkt, β_smb, β_hml); bar chart of factor contributions | Requires returns time series + seeded factor return series (Mkt-RF, SMB, HML) | Download Ken French data library; store seeded factor returns; OLS regression per holding |
| **Arbitrage scanner — cointegration-based pairs detector** | "Has done statistical arbitrage work" — rare signal | HIGH | Seeded pairs universe; pre-computed Engle-Granger test statistics and Z-scores; flagged pairs shown in table | Requires multi-security price series in seeded universe | Show: pair name, cointegration p-value, current spread Z-score, signal (mean-revert long/short/neutral) |
| **VaR breakdown — parametric + historical VaR side by side** | Shows understanding of VaR methodology assumptions | MEDIUM | Computed from seeded returns; both methods shown | Requires return series | Parametric (Gaussian) vs historical; CVaR/ES as a bonus column |

#### Spring AI Differentiators

These map directly to Spring AI building blocks that interviewers will recognize. Each one is listed with the exact API surface it exercises.

| Feature | Spring AI API Exercised | Resume Signal | Complexity | Demo-mode Behavior | Notes |
|---------|------------------------|---------------|------------|-------------------|-------|
| **NL portfolio Q&A chat** — freeform questions ("Why is my Sharpe ratio low?", "What is my largest position?") | `ChatClient`, `MessageChatMemoryAdvisor` (conversation memory), `PromptChatMemoryAdvisor` (fallback) | "Uses ChatClient + memory advisors correctly" | MEDIUM | Seeded canned responses keyed by question pattern; live mode calls real LLM with portfolio context injected as system message | Conversation ID per session; memory advisor stores last N turns |
| **RAG over 10-Ks / earnings filings** — ask questions answered from embedded documents ("What did Apple say about AI in their last 10-K?") | `QuestionAnswerAdvisor`, `VectorStore` (pgvector), `EmbeddingModel`, document ingestion pipeline | "Has built a real RAG pipeline with vector store" | HIGH | 3–5 seeded documents pre-embedded in pgvector; seeded LLM answer stored; query hits real vector store even in demo mode (embeddings already in DB) | BYO-key flips answer generation to live; embedding already done at seed time — no key needed for retrieval |
| **Tool calling for live quotes** — chat or panel requests current price; LLM decides to call the tool | `@Tool` / `FunctionCallback`, tool registration on `ChatClient` | "Has wired real tool calling, not just prompt engineering" | MEDIUM | Demo mode: tool returns seeded quote (last known price from seeded series); live mode: calls real market-data API (Alpha Vantage / Yahoo Finance / Polygon free tier) | Show the tool invocation in UI (thought process / tool call panel) for maximum visual impact |
| **Structured output driving charts** — LLM returns typed Java record; frontend renders directly | `BeanOutputConverter<T>`, `.entity(MyRecord.class)` on `ChatClient` response | "Understands structured output, not just freeform text" | MEDIUM | Seeded structured JSON stored as fixture; frontend renders from same DTO whether demo or live | Define `PortfolioChartData`, `RiskSummaryOutput` records; LLM fills them; Vue consumes them |
| **"Explain this position" AI panel** — click a holding, get AI-generated narrative (what the company does, why it's in your portfolio, current risk context) | `ChatClient` with system prompt carrying holding data + RAG context from filings | "AI integrated into a specific domain workflow, not bolted on" | MEDIUM | Seeded explanations per holding (one per demo user's portfolio); live mode calls LLM | Per-holding seeded explanation in DB; shown in slide-out panel or modal |
| **AI daily portfolio commentary** — one AI-generated paragraph shown on dashboard home | `ChatClient` with structured system prompt carrying portfolio snapshot | "Shows AI-as-narrator pattern used in real fintech products" | LOW | Seeded commentary per demo user, refreshed date shown; live mode re-generates on login | One `ChatClient` call with portfolio JSON summary as system context; store result with timestamp |
| **`@McpTool` exposure — portfolio analytics as MCP server** | `@McpTool`, `@McpToolParam`, `@McpResource`, Spring AI MCP server boot starter | "Has built an MCP server — the freshest signal on a 2026 resume" | MEDIUM | MCP server runs regardless; tools return seeded data in demo mode, live computed data otherwise | Expose at minimum: `get_portfolio_summary`, `get_risk_metrics`, `get_position_detail`; document that Claude Code can use this server to query the live portfolio |
| **Multi-provider toggle (Anthropic Claude + OpenAI)** — BYO-key popup lets user choose provider | `ChatClient.Builder`, provider-specific auto-config, runtime provider selection | "Knows Spring AI's provider abstraction, not just one SDK" | MEDIUM | Popup shows both options; demo mode bypasses both; live mode routes to selected provider | Store provider choice + key in session (never DB); `ChatModel` bean selected at request time |

---

### Anti-Features (Deliberately Not Building for v1)

Features that seem obviously good but actively hurt the resume piece. Document the reasoning to prevent scope creep.

| Anti-Feature | Surface Appeal | Why It Hurts v1 | What to Do Instead |
|--------------|---------------|-----------------|-------------------|
| **Real OAuth (Google/GitHub SSO)** | "Production ready" | 2–3 days of plumbing that adds zero AI or quant signal; distracts from the differentiators; complicates demo flow | Seeded users with session cookie; document OAuth as the upgrade path in README |
| **Real brokerage API integration (Plaid, Alpaca, IBKR)** | "Actually connects to your account" | OAuth + broker API negotiation is days of effort; adds operational risk; contradicts "screenshot-ready with zero setup"; no quant signal | Seeded portfolio + live market quotes via tool calling is sufficient and more reproducible |
| **Order execution / trading** | "Full trading system" | Legal/regulatory red flags; completely out of scope for an analytics showcase; would terrify a quant hiring manager seeing it on a demo | Analytics only; never place orders; state this explicitly in README |
| **Portfolio optimization (mean-variance / Black-Litterman)** | "Shows Markowitz frontier" | Correct implementation requires numerical convex solver (CVXPY equivalent in Java); easy to get subtly wrong; adds another 3–5 day feature with marginal resume delta over the MC + attribution already planned | Mention in README "roadmap: efficient frontier" as v2 |
| **Real-time streaming price updates (WebSocket)** | "Live ticking prices" | Adds WebSocket infra, connection management, reconnect logic, and makes demo fragile without a live feed; adds zero AI or quant signal | Refresh-on-demand (button or timed poll) via tool-calling quote fetch is sufficient |
| **Fine-tuned or custom LLM** | "Domain-specific model" | Requires GPU, training data curation, RLHF — weeks of ML work; the resume signal is Spring AI integration, not model training | RAG over filings + careful system prompts achieves domain grounding without training |
| **Sentiment analysis from social media / news feeds** | "NLP on news" | Requires reliable news API (costly), NLP pipeline, and produces low-quality signals; dilutes the clean quant + Spring AI story | RAG over 10-Ks is more credible and produces better answers |
| **Mobile-responsive PWA / native app** | "Works on phone" | Web-first is the constraint; mobile layout costs significant frontend time with zero backend/AI signal | Desktop web layout; add note in README about mobile as future work |
| **User-defined watchlists, alerts, custom portfolios** | "CRUD features" | Adds user-management complexity (auth, persistence, permissions) without any resume signal; blurs demo focus | Three seeded portfolios cover three personas cleanly |
| **Backtesting engine** | "Validates strategy" | Correct backtesting (slippage, look-ahead bias, transaction costs) requires serious infra; easy to do badly; out of scope of an analytics dashboard | Monte Carlo forecasting is the forward-looking complement; mention backtesting as v2 |
| **Persisting LLM API keys** | "Convenience for returning users" | Security anti-pattern; specifically called out in PROJECT.md constraints; session-only is the right answer | Session-only; popup shows on each fresh session |
| **CI/CD pipeline, Kubernetes deployment** | "Production infra" | Zero resume delta for a quant-AI engineer; adds setup complexity | Docker Compose is sufficient and immediately reproducible |

---

## Feature Dependencies

```
Seeded price series (OHLCV, ~2 years, ~15 securities)
    └──required by──> Holdings / P&L / Allocation view
    └──required by──> Risk metrics (Sharpe, VaR, beta, vol, correlation)
    └──required by──> Monte Carlo (all models) — parameter estimation
    └──required by──> Factor attribution — return series needed for OLS
    └──required by──> Cointegration / arbitrage scanner
    └──required by──> Tool calling demo-mode quote responses

Risk metrics (Sharpe, VaR, beta, vol)
    └──required by──> "Explain this position" AI panel (context injected)
    └──required by──> Daily AI commentary (portfolio snapshot context)
    └──required by──> NL Q&A (portfolio context in system prompt)

Monte Carlo GBM (model 1)
    └──required before──> Jump-diffusion (model 2) — baseline to compare
    └──required before──> Heston (model 3) — baseline comparison
    └──required before──> Bootstrap (model 4) — baseline comparison
    └──required by──> Model selector UI

pgvector + EmbeddingModel (seeded at startup)
    └──required by──> RAG / QuestionAnswerAdvisor
    └──required by──> "Explain this position" (RAG context)
    └──required by──> VectorStoreChatMemoryAdvisor (optional memory store)

ChatClient (with demo-mode intercept layer)
    └──required by──> NL Q&A
    └──required by──> "Explain this position"
    └──required by──> Daily commentary
    └──required by──> Structured output driving charts
    └──required by──> Tool calling

@McpTool server
    └──requires──> Risk metrics (exposes computed values)
    └──requires──> Holdings service (exposes position data)
    └──enhances──> Tool calling (MCP client can call same tools)

Seeded LLM responses (demo intercept)
    └──required by──> All AI features in zero-key mode
    └──unlocked by──> BYO-key popup (flips to live ChatClient calls)

Demo user seeding (Postgres seed on first start)
    └──required by──> All features (no user = no portfolio = no demo)
    └──required by──> Docker Compose "zero setup" claim
```

### Dependency Notes

- **Seeded price series is the foundation:** Everything quant-side depends on it. It must be seeded before any metric is computed. Plan a dedicated `DataSeeder` component run at container startup.
- **pgvector at seed time:** RAG works in demo mode because embeddings are pre-computed and stored. This means `EmbeddingModel` must be called once during seed generation (build time or first-run), not on every demo. This decouples demo RAG from the LLM key entirely.
- **Demo-mode intercept layer is cross-cutting:** Build a `DemoModeInterceptor` or `SeededChatModel` wrapper early. All AI features route through it. BYO-key popup replaces the wrapper at session scope. If this isn't built first, every AI feature needs its own mock — which becomes unmaintainable.
- **GBM before Heston:** Heston parameters (κ, θ, ξ, ρ) are calibrated from the same return series used by GBM. Implement in order: GBM → jump-diffusion → Heston → bootstrap.
- **@McpTool depends on the services, not the reverse:** The MCP server is a thin annotation layer over existing service beans. Build the services first, annotate last. This also means the MCP server is always correct by construction.

---

## MVP Definition

### Launch With (v1 — the screenshot-ready demo)

The v1 target is: someone opens the README, sees screenshots, runs `docker-compose up`, logs in as Alice, and within 2 minutes is looking at AI-narrated quant analytics with working fan charts and a RAG Q&A panel.

- [ ] Seeded demo users (3) + seeded price series + Docker Compose — without this nothing else demos
- [ ] Holdings / P&L / Allocation views — table stakes; must be present before anything else is credible
- [ ] Risk metrics scorecard (Sharpe, vol, VaR, beta, correlation heatmap) — core quant signal
- [ ] Monte Carlo fan chart with all 4 models + model selector — primary stochastic differentiator
- [ ] Factor attribution (Fama-French 3-factor) — causal/attribution differentiator
- [ ] Cointegration pairs scanner — arbitrage differentiator
- [ ] NL Q&A with `MessageChatMemoryAdvisor` + demo-mode intercept — primary Spring AI showcase
- [ ] RAG over seeded 10-K documents via `QuestionAnswerAdvisor` + pgvector — Spring AI RAG showcase
- [ ] Tool calling for live quotes (demo-mode returns seeded price) — Spring AI tool calling showcase
- [ ] Structured output driving one chart (e.g., risk summary bar chart via `BeanOutputConverter`) — Spring AI structured output showcase
- [ ] "Explain this position" panel (ChatClient + RAG context injection) — AI-in-workflow showcase
- [ ] AI daily commentary (one ChatClient call with portfolio snapshot) — AI-as-narrator showcase
- [ ] `@McpTool` exposure of portfolio + risk services — MCP server showcase (freshest 2026 signal)
- [ ] Multi-provider toggle (Claude + OpenAI) + BYO-key popup — Spring AI provider abstraction showcase
- [ ] Demo-mode intercept layer (SeededChatModel / fixture store) — enables all of the above with zero key

### Add After Validation (v1.x)

- [ ] Heston parameter calibration from real-time return data when key is provided (currently seeded params)
- [ ] 5-factor Fama-French upgrade (add RMW, CMA factors) — easy extension of attribution feature
- [ ] `@McpResource` exposure of filings documents (complement to `@McpTool`)
- [ ] Streaming AI responses (SSE) for Q&A chat — visual improvement, low complexity

### Future Consideration (v2+)

- [ ] Efficient frontier / portfolio optimization — correct solver integration
- [ ] Backtesting engine — proper look-ahead-bias prevention
- [ ] OAuth (Google/GitHub) — documented upgrade path
- [ ] Additional arbitrage detectors (covered-call parity, CIP violations for FX)
- [ ] Additional MC models (SABR, variance gamma)

---

## Feature Prioritization Matrix

| Feature | Resume Value | Implementation Cost | Priority |
|---------|-------------|---------------------|----------|
| Seeded data + Docker Compose | HIGH (demo-ability) | LOW | P1 |
| Holdings / P&L / Allocation | HIGH (table stakes) | LOW | P1 |
| Risk metrics scorecard | HIGH (quant credibility) | MEDIUM | P1 |
| Monte Carlo fan chart (GBM + 3 models) | HIGH (stochastic signal) | MEDIUM–HIGH | P1 |
| Demo-mode intercept (SeededChatModel) | HIGH (enables all AI demos) | MEDIUM | P1 |
| NL Q&A (ChatClient + memory advisor) | HIGH (Spring AI depth) | MEDIUM | P1 |
| RAG over 10-Ks (QuestionAnswerAdvisor) | HIGH (Spring AI RAG) | HIGH | P1 |
| Tool calling for quotes | HIGH (Spring AI tool calling) | MEDIUM | P1 |
| @McpTool server | HIGH (2026 frontier signal) | MEDIUM | P1 |
| Structured output driving charts | MEDIUM–HIGH (Spring AI API depth) | MEDIUM | P1 |
| "Explain this position" panel | HIGH (AI-in-workflow narrative) | MEDIUM | P1 |
| Daily AI commentary | MEDIUM (table stakes for AI demo) | LOW | P1 |
| Multi-provider toggle + BYO-key popup | HIGH (Spring AI provider abstraction) | MEDIUM | P1 |
| Factor attribution (Fama-French) | HIGH (causal/fin-math signal) | HIGH | P1 |
| Cointegration pairs scanner | HIGH (arb/quant signal) | HIGH | P1 |
| Correlation heatmap | MEDIUM (expected quant viz) | MEDIUM | P1 |
| Benchmark comparison chart | MEDIUM (table stakes) | LOW | P1 |
| VaR breakdown (parametric vs historical) | MEDIUM (methodology depth) | LOW–MEDIUM | P2 |
| Streaming AI responses (SSE) | LOW | LOW | P3 |
| Portfolio optimization / efficient frontier | MEDIUM | HIGH | P3 (v2) |
| Backtesting | LOW (out of scope) | HIGH | P3 (v2) |

**Priority key:**
- P1: Must have for v1 launch / README screenshots
- P2: Add once P1 features are working and tested
- P3: Defer to v2 or beyond

---

## Reference Product Landscape

| Feature | Portfolio Visualizer | Bloomberg PORT | Interactive Brokers PA | QuantLens (this project) |
|---------|---------------------|----------------|----------------------|--------------------------|
| Holdings + P&L | Yes | Yes | Yes | Yes (seeded) |
| Sharpe, VaR, beta | Yes | Yes | Yes | Yes (computed) |
| Correlation heatmap | Yes | Yes | No | Yes |
| Monte Carlo forecasting | Yes (GBM only) | Yes | No | Yes (4 models + selector) |
| Factor attribution | Yes (3-factor) | Yes (multi) | No | Yes (3-factor OLS) |
| Pairs / arb scanner | No | Bloomberg IB only | No | Yes (cointegration) |
| NL Q&A over portfolio | No | No | No | Yes (Spring AI RAG + memory) |
| RAG over filings | No | Yes (Bloomberg Intel.) | No | Yes (pgvector + QAAdvisor) |
| Tool calling / live quotes | No | Yes (terminal) | Yes | Yes (@Tool on ChatClient) |
| AI narrative commentary | No | No | No | Yes (daily + per-position) |
| MCP server | No | No | No | Yes (@McpTool, 2026) |
| Structured output to charts | N/A | N/A | N/A | Yes (BeanOutputConverter) |
| Zero-setup demo mode | No | No | No | Yes (seeded + Docker) |

The differentiated zone for QuantLens is the bottom half of that table: the combination of genuine quant depth (4-model MC, factor attribution, arb scanner) narrated by a fully-wired Spring AI layer (all building blocks used) is genuinely not available in any single open-source or commercial product targeted at a similar audience.

---

## Demo-mode vs Live-mode Behavior Per AI Feature

| AI Feature | Demo Mode (no key) | Live Mode (BYO key) |
|------------|-------------------|---------------------|
| NL Q&A | `SeededChatModel` returns stored JSON fixtures keyed by session user + question embedding bucket | Real `ChatClient` call with memory advisor chain; portfolio snapshot injected as system message |
| RAG Q&A | Vector retrieval runs against real pgvector (embeddings seeded at startup); answer text comes from fixture store | Vector retrieval unchanged; answer generated by live LLM using retrieved chunks |
| Tool calling (quotes) | `@Tool` method returns last seeded price for the requested ticker | `@Tool` method calls real market-data API (Alpha Vantage / Polygon free tier) |
| Structured output chart | Pre-serialized `PortfolioChartData` JSON served from fixture; Vue renders same DTO | `ChatClient.call().entity(PortfolioChartData.class)` populated by LLM; Vue renders same DTO |
| "Explain this position" | Stored per-holding narrative in DB (seeded) | Fresh ChatClient call with holding data + RAG context |
| Daily commentary | Stored per-user commentary string with seeded date | `ChatClient` call on login; result cached for session |
| @McpTool | Tools return live-computed values from seeded data (analytics always runs; only LLM answer is seeded) | Same; LLM client calling these tools now gets live data |

**Implementation pattern:** The `SeededChatModel` is a Spring `ChatModel` bean that reads fixtures from a `chat_fixtures` table. A `@SessionScope` bean holds `{provider, key, demoMode}`. A `@Primary @ConditionalOnMissingBean(ChatModel)` or a runtime-switching `DelegatingChatModel` selects between `SeededChatModel` and the real provider. This is the single most important infrastructure piece — build it in Phase 1 of the AI layer.

---

## Sources

- [Bloomberg PORT — Portfolio Analytics](https://professional.bloomberg.com/products/bloomberg-terminal/portfolio-analytics/)
- [Interactive Brokers PortfolioAnalyst](https://www.interactivebrokers.com/en/portfolioanalyst/overview.php)
- [Portfolio Visualizer](https://www.portfoliovisualizer.com/)
- [Spring AI Advisors API Reference](https://docs.spring.io/spring-ai/reference/api/advisors.html)
- [Spring AI Structured Output Reference](https://docs.spring.io/spring-ai/reference/api/structured-output-converter.html)
- [Spring AI MCP Server Annotations Reference](https://docs.spring.io/spring-ai/reference/api/mcp/mcp-annotations-server.html)
- [Spring AI Tool Calling Reference](https://docs.spring.io/spring-ai/reference/api/tools.html)
- [Spring AI 1.1 + MCP — Java Code Geeks](https://www.javacodegeeks.com/2026/03/spring-ai-1-1-and-themodel-context-protocolbuilding-production-ai-agentswithout-the-python-tax.html)
- [Building MCP Tools with Spring AI — JAVAPRO](https://javapro.io/2026/01/07/building-mcp-tools-for-ai-agents-using-spring-ai/)
- [Deep Learning Spring AI: Advisors, Structured Output, Tool Calling — Paradigma](https://en.paradigmadigital.com/dev/deep-learning-spring-ai-advisors-structured-output-tool-calling/)
- [RAG on SEC EDGAR Filings — Captide](https://www.captide.ai/insights/how-to-do-agentic-rag-on-sec-edgar-filings)
- [Financial Analysis Chatbot with RAG for 10-K/10-Q — Medium](https://medium.com/@RobuRishabh/financial-analysis-chatbot-for-10-q-10-k-reports-using-retrieval-augmented-generation-rag-ef3938892086)
- [GBM + Jump Diffusion Monte Carlo — GitHub arjundhatt13](https://github.com/arjundhatt13/jumpdiffusion)
- [Heston + Jump Diffusion Computational Framework — arXiv 2604.06068](https://arxiv.org/html/2604.06068)
- [Pairs Trading Cointegration — Hudson & Thames](https://hudsonthames.org/definitive-guide-to-pairs-trading/)
- [Fama-French Three-Factor Model — QuestDB Glossary](https://questdb.com/glossary/fama-french-three-factor-model/)
- [Awesome Quant libraries — GitHub wilsonfreitas](https://github.com/wilsonfreitas/awesome-quant)

---
*Feature research for: AI-augmented quantitative portfolio & market-intelligence dashboard (QuantLens)*
*Researched: 2026-06-07*
