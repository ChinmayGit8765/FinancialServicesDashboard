---
phase: 8
slug: live-ai-features
status: approved
nyquist_compliant: true
wave_0_complete: false
created: 2026-06-09
---

# Phase 8 — Validation Strategy

> Demo paths (seeded quote + seeded structured JSON) proven offline before any live key path. No real network in tests.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Backend** | JUnit 5 + Spring Boot Test + Testcontainers (mocked Finnhub/provider — no real net) |
| **Frontend** | Vitest + @vue/test-utils |
| **Quick run** | `.\mvnw.cmd -q test -Dtest=StockQuoteToolServiceTest,StructuredOutputServiceTest,MultiProviderRoutingTest` (JAVA_HOME=Temurin 21) |
| **Full suite** | `.\mvnw.cmd verify` + (frontend) `npm run test` |

---

## Sampling Rate
- **Per task commit:** the relevant tool/structured/routing test
- **Per wave:** `.\mvnw.cmd verify` / `npm run test`
- **Phase gate:** full backend + frontend green; KeyLeakage gate (incl. Finnhub key) green; demo no-network proof green

---

## Per-Req Verification Map

| Req | Behavior | Type | Command |
|-----|----------|------|---------|
| AI-05 | @Tool demo: returns seeded OHLCV last close, NO network | unit | `StockQuoteToolServiceTest#demo*` |
| AI-05 | @Tool live: mocked Finnhub → price + after-hours label | unit | `StockQuoteToolServiceTest#live*` |
| AI-05 | TTL cache: 2nd call <15min does not re-fetch | unit | `StockQuoteToolServiceTest#cache*` |
| AI-05 | demo no-network proof for tool chat path (counting advisor==0) | integration | `AiDemoModeIntegrationTest` (extended) |
| AI-06 | structured demo: seeded JSON → StructuredInsightRecord | unit | `StructuredOutputServiceTest#demo*` |
| AI-06 | structured live: mock ChatModel JSON → record (BeanOutputConverter) | unit | `StructuredOutputServiceTest#live*` |
| AI-06 | GET /api/ai/structured → record shape | integration | `AiControllerIntegrationTest#structured*` |
| AI-05/06 | multi-provider: mock Anthropic + OpenAI both route via forSession() | unit | `MultiProviderRoutingTest` |
| AI-02 gate | Finnhub + LLM key never echoed/logged incl. /api/ai/structured | integration | `KeyLeakageIntegrationTest` (extended) |
| AI-06 | StructuredOutputChart renders typed DTO from /api/ai/structured | component | `npm run test -- StructuredOutputChart` |

---

## Wave 0 Requirements
- [ ] StockQuoteToolService (@Tool via MethodToolCallbackProvider — NOT defaultTools), FinnhubQuoteClient (thin Java HttpClient + 15-min TTL cache + after-hours from timestamp), StockQuoteResult; StructuredInsightRecord + InsightEntry + StructuredOutputService; GET /api/ai/structured
- [ ] StockQuoteToolServiceTest, StructuredOutputServiceTest, MultiProviderRoutingTest (RED); extend AiControllerIntegrationTest (structured), KeyLeakageIntegrationTest (/api/ai/structured + Finnhub key), AiDemoModeIntegrationTest (structured no-network proof)
- [ ] STRUCTURED_INSIGHT seeds (per persona) valid JSON matching the record; ai store fetchStructured → /api/ai/structured; StructuredOutputChart.test.ts live-DTO test
- [ ] Wave 0 verifies open questions at compile (MethodToolCallbackProvider return type; ticker-based OHLCV query; marketdata::domain in ai package-info)

---

## Manual-Only Verifications
| Behavior | Req | Why Manual | Steps |
|----------|-----|------------|-------|
| Live tool call: ask for a quote with a BYO key → LLM invokes Finnhub @Tool | AI-05 | needs live LLM + Finnhub key | `docker compose up` + keys → ask "what's AAPL trading at?" |
| Structured-output chart renders (demo + live) | AI-06 | visual | structured panel renders the typed DTO |
| Multi-provider live switch (Claude↔OpenAI) | AI-05/06 | needs live keys | switch provider in popup → successful live answer |

---

## Validation Sign-Off
- [x] Demo tool + structured paths proven offline (no net); counting-advisor no-network proof
- [x] KeyLeakage gate extended (LLM + Finnhub keys)
- [x] Multi-provider routing test (mock both)
- [x] Wave 0 covers tools + structured + routing + all scaffolds
- [x] `nyquist_compliant: true`

**Approval:** approved 2026-06-09 (wave_0_complete flips true after Plan 08-01 executes)
