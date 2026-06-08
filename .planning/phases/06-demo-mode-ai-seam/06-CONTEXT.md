# Phase 6: Demo-Mode AI Seam - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Establish the Spring AI layer and the demo↔live seam that makes every AI panel work with ZERO API key (seeded) yet flip to live the moment a key is entered via the popup. This is the architectural keystone for all later AI features (RAG Phase 7, live tool calling/structured output Phase 8). Deliver: the multi-provider ChatClient + a single `DemoModeAdvisor`, a session-scoped key holder with strict no-leak handling, authored seeded content, the "explain this position" panel, the daily commentary card, and the BYO-key popup.

Requirements covered: AI-01 (seeded demo mode for all AI), AI-02 (BYO-key popup, session-only, key-leak test), AI-07 (explain-this-position), AI-08 (daily commentary).

The "structured-output chart" panel is established as a seam-demonstrating stub here; its LIVE structured output (AI-06) and live tool calling (AI-05) land in Phase 8. RAG Q&A (AI-03/04) lands in Phase 7.
</domain>

<decisions>
## Implementation Decisions

### Spring AI wiring
- Add Spring AI starters to backend pom (BOM 1.1.6 already pinned in dependencyManagement): `spring-ai-starter-model-anthropic` and `spring-ai-starter-model-openai`. New `com.quantlens.ai` Modulith module (reads `portfolio::domain` + `analytics` as needed via named interfaces).
- A `ChatClient` (or per-request builder) is the entry point for all AI features. Because no key is configured by default, auto-config must NOT fail at startup with no key — guard the provider beans so the app boots key-less (demo mode default). Research to confirm the cleanest way to boot Spring AI Anthropic/OpenAI starters with no key present (conditional beans / lazy).

### Demo↔live seam (the keystone)
- `DemoModeAdvisor` implements Spring AI's `CallAdvisor`, placed FIRST in the advisor chain. It reads the session-scoped `LlmKeySessionHolder`:
  - **No key (demo mode, default):** short-circuit — return an authored seeded `ChatResponse` selected by the request's intent/key (e.g. holding ticker, persona, feature). NEVER calls `nextCall()` / a real provider. No network, no key needed.
  - **Key present (live mode):** pass through the chain to the real provider, injecting the session key per-request.
- Per-request key injection (live): `chatModel.mutate().apiKey(sessionKey)` (per architecture research) on the selected provider's model — never a persisted/global key. Provider chosen from the session holder.

### Key handling & security (AI-02)
- `LlmKeySessionHolder` = `@SessionScope` bean holding `{provider (ANTHROPIC|OPENAI), apiKey, demoMode}`. Set via a `POST /api/ai/key` endpoint (body: provider + key); cleared on logout / `DELETE /api/ai/key`.
- The key is NEVER: logged (no `SimpleLoggerAdvisor` at DEBUG carrying the key; no request-body logging interceptor over this endpoint; scrub from any error), echoed back to the client (the endpoint returns only `{mode, provider}`, never the key), or persisted beyond the HTTP session (no DB, no disk). A dedicated **key-leakage integration test** asserts: response bodies never contain the key substring; logs don't contain it; GET status returns mode/provider only.
- A `GET /api/ai/status` (or /mode) returns `{mode: demo|live, provider?}` so the frontend can show the indicator (never the key).

### Seeded content (AI-01)
- Authored, LLM-looking content consistent with the seeded data, stored as seed data (a table like `ai_seed_content` keyed by (type, persona/holding) — e.g. per-holding "explain this position" narratives for each demo persona's positions, and one daily commentary per persona). Seeder (extend Phase 1 SeedRunner or a new idempotent seeder) populates them. The DemoModeAdvisor (or the feature services) select the right fixture by request context. Content should reference the persona's actual holdings/metrics so it matches the charts (no contradiction with displayed numbers).

### AI feature endpoints + panels
- `GET /api/ai/explain/{holdingId|ticker}` → explain-this-position narrative (AI-07). Frontend: a slide-out drawer/modal opened by clicking a holding row; replaces the Phase-3/4 "AI" slot affordance.
- `GET /api/ai/commentary` → daily portfolio commentary for the session persona (AI-08). Frontend: a commentary card on the dashboard home (fills a Phase-6 AI slot).
- Both principal-scoped, `@Transactional(readOnly=true)` where they read portfolio data; both go through the ChatClient + DemoModeAdvisor so they're seeded by default, live with a key.
- A structured-output panel stub may render seeded JSON-driven content to demonstrate the seam (live wiring in Phase 8).

### Frontend
- BYO-key popup modal: choose provider (Anthropic Claude / OpenAI), paste a key (password input, not stored in localStorage — sent to the session endpoint only), submit → mode flips to live; a clear/"return to demo" action calls DELETE. A persistent demo/live mode indicator (badge) in the top bar driven by `/api/ai/status`.
- New Pinia `ai` store (mode/provider state + actions: setKey, clearKey, fetchExplain, fetchCommentary). Reuse the axios singleton (session cookie + XSRF). The popup is the artifact the user explicitly wants for README screenshots.

### Provider model defaults (live mode)
- Default models when live (configurable): Anthropic `claude-sonnet-4-6` (current Sonnet) — or allow opus `claude-opus-4-8`; OpenAI a current GPT model. Research to confirm current Spring AI model property names + valid model IDs at build time. Demo mode needs none.

### Testing
- Key-leakage integration test (AI-02) — the security gate. Demo-mode integration tests: explain + commentary return seeded content with NO key configured (no outbound call — verify via no provider interaction / a stub). Advisor unit test: short-circuits when demoMode, passes through when key present. Frontend: popup component test (submit sets mode, never persists key to localStorage), mode indicator, explain drawer + commentary card states. Do NOT make live-provider network calls in tests.

### Claude's Discretion
- Exact advisor wiring vs a strategy bean, fixture storage shape, endpoint grouping, how to boot Spring AI starters key-less, and popup UX details — at Claude's discretion within the above. Research to confirm Spring AI 1.1.6 CallAdvisor API, key-less startup, and model.mutate() per-request key injection.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets
- Backend: Spring Security session auth (LlmKeySessionHolder mirrors the @SessionScope pattern; reuse principal resolution), portfolio + analytics modules (commentary/explain read holdings + metrics), SeedRunner (extend for seeded AI content), AbstractPostgresIntegrationTest. Spring AI BOM 1.1.6 pinned (no starters yet — add anthropic + openai).
- Frontend: Pinia store asyncState factory, axios singleton (session + XSRF), the Phase-3 reserved AI SlotPlaceholders (commentary, BYO-key) in DashboardView, dark-theme modal/card patterns, top bar (for the mode indicator).

### Established Patterns
- @SessionScope beans, principal-scoped readOnly endpoints, Modulith boundaries + named interfaces, BigDecimal money, golden/integration tests, Vue <script setup> + scoped CSS tokens, store asyncState + DashboardView slot replacement.

### Integration Points
- DemoModeAdvisor is cross-cutting — all AI features route through it. The popup writes to LlmKeySessionHolder. Phase 7 (RAG) and Phase 8 (live tool calling/structured output) extend this seam — design it so they slot in without rework.
</code_context>

<specifics>
## Specific Ideas

- The BYO-key popup + the "looks seeded with an LLM" demo content are EXACTLY what the user asked for ("seed llm responses ... make it look like I had it seeded with an llm" + "pop up on the page to put a api key in ... take pictures for the readme"). Make the seeded content genuinely convincing and the popup screenshot-worthy.
- Security is non-negotiable: the key-leakage test is the gate. Never log/echo/persist the key.
- Demo mode must require ZERO external dependency — no network call when no key.
</specifics>

<deferred>
## Deferred Ideas

- RAG Q&A over filings (Phase 7 — AI-03/04).
- Live tool calling for quotes + structured output driving a chart (Phase 8 — AI-05/06).
- @McpTool server (Phase 9).
- Persisting user keys (explicitly out of scope — session-only).
- Streaming responses (v2 — AI-09).
</deferred>
