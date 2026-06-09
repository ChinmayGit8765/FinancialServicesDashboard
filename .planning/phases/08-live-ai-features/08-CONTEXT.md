# Phase 8: Live AI Features - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Complete the Spring AI showcase: (AI-05) tool calling for live stock quotes via a Finnhub `@Tool`, and (AI-06) at least one AI response delivered as typed structured output (BeanOutputConverter) that drives a Vue ECharts chart — plus verifying multi-provider routing (Claude ↔ OpenAI) through the existing `ChatClientStrategy.forSession()`. Demo-mode fallbacks are proven before any live path. Builds on the Phase 6 seam + Phase 7 chat.

Requirements covered: AI-05, AI-06. (Success criterion 3 — multi-provider routing — is verified here against the Phase-6 ChatClientStrategy.)
</domain>

<decisions>
## Implementation Decisions

### Tool calling — live quotes (AI-05)
- A Spring AI `@Tool`-annotated method `getStockQuote(ticker)` registered on the ChatClient (live tool calling). Returns a small typed result (ticker, price, asOf, marketState/after-hours label, source).
- **Demo path (no network):** the tool returns the last seeded OHLCV close for the ticker (from market data). Used when no live quote source is available. In demo CHAT (no LLM key), the DemoModeAdvisor short-circuits with a seeded answer that includes the quote — the LLM never runs.
- **Live path:** if a Finnhub API key is configured (env `FINNHUB_API_KEY` and/or a session field), the tool calls the real Finnhub quote API with a **15-minute TTL cache** (avoid rate limits) and **correct after-hours/market-state labeling**; otherwise it falls back to the seeded last close. The Finnhub key is SEPARATE from the LLM key (a market-data credential) — handle it like the LLM key for leakage (never log/echo/persist beyond config/session). Live LLM mode is where the model decides to invoke the @Tool.
- Add the Finnhub client to pom (research to confirm: `io.finnhub:kotlin-client:2.0.22` per STACK, or a thin Java HTTP client for the single /quote endpoint to avoid the Kotlin dependency — recommend the lightest option).

### Structured output → chart (AI-06)
- At least one AI response delivered as a typed Java `record` via Spring AI structured output (`BeanOutputConverter` / `.entity(MyRecord.class)`). Choose a chart-shaped record (e.g. an AI "risk/allocation insight" → labeled values for a small chart).
- **Same DTO path in both modes:** demo → the StructuredOutputChart fixture/seeded JSON deserialized into the SAME record; live → the LLM fills the record via structured output. The Vue ECharts chart renders from that one DTO regardless of mode. This upgrades the Phase-6 `StructuredOutputChart.vue` stub (which read a static JSON) to the real structured-output mechanism (demo seeded answer + live LLM-generated typed output through the seam).
- Demo short-circuit (DemoModeAdvisor) returns the seeded structured JSON; live returns the LLM-generated record. Endpoint: `GET /api/ai/structured` (or reuse the existing structured fetch) → the typed DTO.

### Multi-provider (success criterion 3)
- No new provider code — Phase 6 `ChatClientStrategy.forSession()` already builds Anthropic (builder) or OpenAI (mutate) per session key. Phase 8 adds tests proving BOTH providers route to a successful live call from the same entry point (using mock/stub ChatModels in tests; real multi-provider is a live UAT item). Confirm provider selection from `LlmKeySessionHolder.getProvider()`.

### Testing
- @Tool demo path: returns seeded last close with NO network (unit/integration). @Tool live path: Finnhub call MOCKED (WireMock or a mocked client) — assert TTL cache (second call within 15 min doesn't re-fetch) + after-hours labeling; NO real network. Structured output: demo (seeded JSON → record) + live (mock provider returns JSON → BeanOutputConverter → record) → chart DTO shape. Multi-provider routing test (mock both). Demo no-network proof (counting advisor) for the tool/structured chat paths. KeyLeakage gate extended to any new endpoint + Finnhub key never leaks. Frontend: StructuredOutputChart renders the typed DTO; quote display if any. No real network in tests.

### Claude's Discretion
- Exact @Tool signature + result record, the structured-output record/chart choice, Finnhub client vs thin HTTP, Finnhub key handling (env vs session vs both), endpoint grouping, and whether quotes surface via chat only or also a small UI affordance — at Claude's discretion within the above. Research to confirm Spring AI 1.1.6 @Tool registration + BeanOutputConverter + Finnhub client + cache approach.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Phase 6/7 ai module: ChatClientStrategy (multi-provider per-session key — Anthropic builder / OpenAI mutate), DemoModeAdvisor (short-circuit seam), ChatService/chat, RagAdvisorConfig (advisor chain), LlmKeySessionHolder, AiController, ai_seed_content + seeders, CountingCallAdvisor no-network proof, KeyLeakageIntegrationTest. Phase 1: OhlcvBar (latest close for seeded quotes), market data repos.
- Frontend: ai store + components incl. StructuredOutputChart.vue (Phase-6 stub reading static JSON — upgrade to the real DTO path), ChatPanel, dark theme.

### Established Patterns
- Demo/live via advisor short-circuit; @SessionScope key; principal-scoped readOnly endpoints; Modulith boundaries; idempotent seeding; Testcontainers; no-network executable proof; ECharts components bind to typed DTOs; key never logged/echoed/persisted.

### Integration Points
- @Tool registered on the live ChatClient (ChatClientStrategy) — demo short-circuits before tools run. Finnhub client + cache in the ai (or marketdata) module. StructuredOutputChart upgraded to fetch the typed structured DTO (demo seeded / live LLM). Provider routing already in ChatClientStrategy.
</code_context>

<specifics>
## Specific Ideas

- Tool calling + structured output are the last two Spring AI building blocks to showcase — make them visibly work in the demo (seeded) and genuinely live with a key.
- Finnhub is optional/credentialed — the demo MUST still work with zero external keys (seeded last close). Don't break the zero-setup demo.
- Reuse the no-network executable proof so the demo tool/structured paths are provably offline.
</specifics>

<deferred>
## Deferred Ideas

- @McpTool product server (Phase 9).
- Streaming responses (v2 — AI-09).
- Real-time WebSocket price streaming (out of scope).
- Additional tools beyond quotes (v2).
</deferred>
