---
phase: 06-demo-mode-ai-seam
verified: 2026-06-09T00:00:00Z
status: human_needed
score: 4/4
overrides_applied: 0
human_verification:
  - test: "Demo badge renders as 'Demo' in the top bar; badge flips to 'Live · Claude' or 'Live · GPT' after pasting a real key; returns to 'Demo' after clearing the key"
    expected: "AiModeBadge pill changes text and color class (demo = muted, live = up-color) in real browser without page reload"
    why_human: "AiModeBadge is props-driven and the visual state-flip requires an actual DOM observation in a live browser session; no automated test drives the full round-trip through the session key POST and badge re-render"
  - test: "Daily commentary card renders persona-accurate authored text for Alice (growth), Bob (income), and Charlie (balanced); each persona shows distinct commentary"
    expected: "CommentaryCard shows headline + body + 4 bullet points; Alice commentary references NVDA/AAPL/MSFT; Bob references JPM/BAC/XOM/KO; Charlie references diversified mix; no blank content"
    why_human: "Requires switching personas and visually reading the rendered content for accuracy and distinctness; automated tests only assert non-blank values"
  - test: "Clicking a holding row (e.g. AAPL) opens the ExplainDrawer with seeded narrative naming that ticker; drawer closes on overlay click or X button"
    expected: "Drawer slides in with 2-3 paragraph narrative that mentions 'AAPL', closes on X/overlay click, panel z-index is above TopBar"
    why_human: "Row-click event, drawer animation, z-index layering, and narrative content quality cannot be verified programmatically"
  - test: "'Connect Live AI' button opens BYOKeyModal; the popup shows provider radios (Anthropic Claude / OpenAI GPT), a password input, and the security reassurance line"
    expected: "Modal renders visually with overlay, provider radios, type=password input, 'Your key is used only for this session and is never saved or transmitted to us.' text, Cancel and Connect Live AI buttons"
    why_human: "Visual styling, overlay, and overall screenshot-worthiness are manual judgements"
  - test: "Structured-output chart renders the 5-sector seeded fixture (title: 'AI-Detected Sector Exposure') as a horizontal bar chart with 'Structured Output · Demo' badge"
    expected: "StructuredOutputChart card visible on dashboard, title text readable, 5 bars rendered, demo affordance visible"
    why_human: "ECharts canvas rendering and chart layout require visual inspection"
---

# Phase 6: Demo-Mode AI Seam Verification Report

**Phase Goal:** Every AI panel renders realistic seeded content with zero API key via a single DemoModeAdvisor; a BYO-key popup flips all panels to live mode; key never logged/echoed/persisted.
**Verified:** 2026-06-09
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | All AI panels render authored seeded content with no API key (demo mode default) | VERIFIED | DemoModeAdvisor short-circuits at HIGHEST_PRECEDENCE; 16 seed rows in AiSeedRunner; AiDemoModeIntegrationTest asserts 200 + non-blank narrative/commentary; CommentaryCard/ExplainDrawer/StructuredOutputChart wired into DashboardView |
| 2 | BYO-key popup posts session-only key; key NEVER logged, echoed, or persisted | VERIFIED | KeyLeakageIntegrationTest asserts key absent from all 4 response bodies and all log lines; LlmKeySessionHolder has no @ToString/@JsonInclude; AiKeyController returns only {mode,provider}; ai store setKey() discards apiKey after POST; BYOKeyModal clears keyInput before any await; $reset has no apiKey field |
| 3 | User can click a holding and see an explain-this-position narrative (AI-07) | VERIFIED | ExplainPositionService routes through ChatClientStrategy.forSession() + DemoModeAdvisor; IDOR guard (ticker validated against principal holdings before any ChatClient call); AiController GET /explain/{ticker} wired; HoldingsTable emits explain(ticker) event; DashboardView opens ExplainDrawer on that event |
| 4 | User sees AI-generated daily portfolio commentary on the dashboard (AI-08) | VERIFIED | CommentaryService routes through ChatClientStrategy.forSession() + DemoModeAdvisor; persona key derived from Portfolio.getStyle().toUpperCase(); DAILY_COMMENTARY storage convention parsed into CommentaryDto(headline, body, bulletPoints); CommentaryCard wired in DashboardView onMounted |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `backend/src/main/java/com/quantlens/ai/chat/DemoModeAdvisor.java` | CallAdvisor short-circuit at HIGHEST_PRECEDENCE; reads AI_SEED_TYPE/AI_SEED_SUBJECT | VERIFIED | Implements CallAdvisor; getOrder()=Ordered.HIGHEST_PRECEDENCE; adviseCall short-circuits when !keyHolder.hasKey(); reads context params; builds ChatClientResponse(ChatResponse, Map) |
| `backend/src/main/java/com/quantlens/ai/session/LlmKeySessionHolder.java` | @SessionScope; no @ToString/@JsonInclude; hasKey/getProvider/getApiKey/setKey/clear | VERIFIED | @Component @SessionScope; plain private String fields; no @ToString; no serialisation annotations; complete method set |
| `backend/src/main/java/com/quantlens/ai/api/AiKeyController.java` | POST/DELETE /api/ai/key; GET /api/ai/status; key never returned | VERIFIED | All 3 endpoints present; POST returns {mode:"live",provider} only; DELETE returns {mode:"demo"}; GET returns {mode,provider} only; @ExceptionHandler(IllegalArgumentException) generic body |
| `backend/src/main/java/com/quantlens/ai/api/AiController.java` | GET /explain/{ticker} + GET /commentary; IDOR-safe resolvePortfolioId | VERIFIED | Both GET endpoints present; path var is String ticker; resolvePortfolioId() copied from AnalyticsController; portfolio ID never from request |
| `backend/src/main/java/com/quantlens/ai/service/ExplainPositionService.java` | explain(portfolioId,ticker) → routes through seam; IDOR guard before ChatClient | VERIFIED | strategy.forSession(keyHolder).prompt().advisors(AI_SEED_TYPE/AI_SEED_SUBJECT).call().content(); NOT_FOUND thrown before ChatClient call for unheld ticker; BAD_GATEWAY wrapper for provider errors; no if(demoMode) |
| `backend/src/main/java/com/quantlens/ai/service/CommentaryService.java` | commentary(portfolioId) → persona key → DAILY_COMMENTARY seam | VERIFIED | Portfolio.getStyle().toUpperCase() → persona key; routes through seam with AI_SEED_TYPE=DAILY_COMMENTARY; parseCommentary() splits headline/body/bullets per convention; no if(demoMode) |
| `backend/src/main/resources/db/migration/V4__ai_seed_content.sql` | ai_seed_content DDL with uq_ai_seed UNIQUE constraint | VERIFIED | CREATE TABLE IF NOT EXISTS ai_seed_content with CONSTRAINT uq_ai_seed UNIQUE (type, subject_id) |
| `backend/src/main/java/com/quantlens/ai/seed/AiSeedRunner.java` | 16 authored fixtures (13 EXPLAIN_POSITION + 3 DAILY_COMMENTARY); idempotent via seed_log ai-v1 | VERIFIED | @Component @Order(2) ApplicationRunner; seed_log "ai-v1" guard at top of run(); 16 fixture objects in buildFixtures(); completion row written LAST; per-row findByTypeAndSubjectId guard |
| `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java` | Security gate: key absent from all 4 response bodies and all log lines | VERIFIED | Logback ListAppender on root logger; hits POST /key, GET /status, GET /explain/AAPL (200-or-502), GET /commentary (200-or-502); asserts doesNotContain(testKey) and doesNotContainIgnoringCase("apiKey") |
| `backend/src/test/java/com/quantlens/ai/AiDemoModeIntegrationTest.java` | EXECUTABLE no-network proof: chain.nextCall() count == 0 in demo mode | VERIFIED | CountingCallAdvisor at HIGHEST_PRECEDENCE+1 via @TestConfiguration; NEXT_CALL_COUNT.get() asserted isZero() after each demo endpoint call; asserts 200 + non-blank narrative/headline |
| `backend/src/test/java/com/quantlens/ai/ExplainPositionServiceTest.java` | IDOR unit test: NOT_FOUND for unheld ticker; verifyNoInteractions(strategy) | VERIFIED | 4 tests: unheld ticker → NOT_FOUND + verifyNoInteractions(strategy); empty portfolio → NOT_FOUND; happy path returns narrative; case-insensitive ticker match |
| `backend/src/main/java/com/quantlens/security/config/SecurityConfig.java` | /api/ai/key in CSRF ignoringRequestMatchers | VERIFIED | .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout", "/api/ai/key"); /api/ai/* stays under .anyRequest().authenticated() |
| `backend/src/main/resources/application.yml` | DEMO_NO_KEY sentinel; spring.ai.chat.client.enabled=false; org.springframework.ai=WARN | VERIFIED | api-key: ${ANTHROPIC_API_KEY:DEMO_NO_KEY}; api-key: ${OPENAI_API_KEY:DEMO_NO_KEY}; chat.client.enabled: false; logging.level.org.springframework.ai: WARN |
| `frontend/src/stores/ai.ts` | setKey() posts key, never assigns to state; $reset has no apiKey | VERIFIED | setKey() forwards apiKey in POST body; status.data = returned {mode,provider} only; apiKey goes out of scope after finally; $reset clears only status/explanation/commentary/structured — no apiKey field |
| `frontend/src/components/ai/BYOKeyModal.vue` | type="password"; key cleared before any await; emits submitted+close; no localStorage/sessionStorage | VERIFIED | type="password" autocomplete="new-password"; keyInput.value='' set before await; emit('submitted')/emit('close') fire synchronously before await aiStore.setKey(); no localStorage/sessionStorage reference |
| `frontend/src/components/ai/AiModeBadge.vue` | Demo/Live pill; props-driven | VERIFIED | Props-driven (mode, provider); "Demo" text + muted class; "Live" text + up-color class; providerLabel() maps anthropic→Claude, openai→GPT |
| `frontend/src/components/ai/ExplainDrawer.vue` | Slide-in panel; narrative prop; three states; z-index 200 | VERIFIED | position: fixed; z-index 200; narrative: string|null prop; shimmer skeleton when loading; error copy when error; narrative paragraphs when populated; overlay click closes |
| `frontend/src/components/ai/CommentaryCard.vue` | headline+body+bulletPoints rendered; three states | VERIFIED | headline as h3; body as p; bulletPoints as ul/li; shimmer skeleton when loading; static error copy + retry on error |
| `frontend/src/components/ai/StructuredOutputChart.vue` | VChart horizontal bar; three states; demo-only stub | VERIFIED (by SUMMARY) | SUMMARY confirms 7 StructuredOutputChart tests green; VChart rendered from StructuredChartDto; "Structured Output · Demo" badge in component |
| `frontend/public/ai-structured-demo.json` | StructuredChartDto shape: title + series | VERIFIED | {"title":"AI-Detected Sector Exposure","subtitle":"Structured output (demo)","series":[5 label/value entries]} |
| `frontend/src/views/DashboardView.vue` | CommentaryCard + StructuredOutputChart + ExplainDrawer + BYOKeyModal wired; onMounted fetchStatus/fetchCommentary/fetchStructured | VERIFIED | All 4 components imported and used; onMounted calls fetchStatus()/fetchCommentary()/fetchStructured(); @explain wires HoldingsTable to ExplainDrawer; @submitted refreshes status+commentary |
| `frontend/src/components/TopBar.vue` | AiModeBadge in .user-area | VERIFIED | AiModeBadge imported; `<AiModeBadge :mode="aiMode" :provider="aiProvider" />` inside .user-area div before username display |
| `frontend/src/components/HoldingsTable.vue` | defineEmits includes explain(ticker: string); row click emits explain | VERIFIED | defineEmits includes `explain: [ticker: string]`; first 80 lines show the emit definition; SUMMARY confirms row click with role="button", tabindex, @click, @keydown.enter/space |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `application.yml` | Spring AI starters | `api-key: ${ANTHROPIC_API_KEY:DEMO_NO_KEY}` | VERIFIED | DEMO_NO_KEY sentinel present for both anthropic and openai providers |
| `DemoModeAdvisor` | `AiSeedContentRepository.findByTypeAndSubjectId` | context param AI_SEED_TYPE/AI_SEED_SUBJECT | VERIFIED | adviseCall() reads request.context().get("AI_SEED_TYPE") and "AI_SEED_SUBJECT"; calls seedRepo.findByTypeAndSubjectId(type, subject) |
| `LlmKeySessionHolder` | `AiKeyController` + `DemoModeAdvisor` + services | @SessionScope bean injected by constructor | VERIFIED | AiKeyController, DemoModeAdvisor, ExplainPositionService, CommentaryService all receive keyHolder via constructor injection |
| `AiSeedContentRepository` | `ai_seed_content` table | `findByTypeAndSubjectId` derived query | VERIFIED | Interface extends JpaRepository; findByTypeAndSubjectId declared; V4 DDL creates table with matching columns |
| `SecurityConfig` | `/api/ai/key` | CSRF ignoringRequestMatchers | VERIFIED | .ignoringRequestMatchers("/api/auth/login", "/api/auth/logout", "/api/ai/key") on line 118 of SecurityConfig.java |
| `ExplainPositionService` | `ChatClientStrategy.forSession()` | `.prompt().advisors(AI_SEED_TYPE/AI_SEED_SUBJECT).call().content()` | VERIFIED | strategy.forSession(keyHolder).prompt().system().user().advisors(spec -> spec.param(...)).call().content() present |
| `CommentaryService` | persona resolution (GROWTH/INCOME/BALANCED) | `portfolio.getStyle().toUpperCase()` | VERIFIED | portfolioRepository.findById(portfolioId); portfolio.getStyle().toUpperCase() → personaKey |
| `stores/ai.ts setKey()` | `/api/ai/key` | axios.post('/api/ai/key', { provider, apiKey }) | VERIFIED | axios.post call present; only returned data (AiStatus) assigned to status.data; apiKey not stored |
| `HoldingsTable.vue` | `ExplainDrawer` via `DashboardView` | emit('explain', ticker) → @explain handler | VERIFIED | HoldingsTable defineEmits includes explain; DashboardView @explain="(ticker) => { explainOpen = true; void aiStore.fetchExplanation(ticker) }" |
| `DashboardView.vue` | AI store actions | onMounted fetchStatus/fetchCommentary/fetchStructured | VERIFIED | All three calls present in onMounted(); submitted event refreshes fetchStatus()+fetchCommentary() |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `DemoModeAdvisor.adviseCall()` | content (String) | `AiSeedContentRepository.findByTypeAndSubjectId(type, subject)` | Yes — JPA derived query against ai_seed_content table | FLOWING |
| `ExplainPositionService.explain()` | content (String) | `strategy.forSession(keyHolder).prompt()...call().content()` → DemoModeAdvisor → seed DB | Yes — seam routes to real DB seed rows | FLOWING |
| `CommentaryService.commentary()` | content (String) | same seam path → seed DB; parseCommentary() splits into headline/body/bullets | Yes — DB-backed seed rows with structured convention | FLOWING |
| `CommentaryCard.vue` | commentary (CommentaryDto) | `aiStore.commentary.data` ← axios.get('/api/ai/commentary') | Yes — fetched on mount from live backend | FLOWING |
| `ExplainDrawer.vue` | narrative (string) | `aiStore.explanation.data?.narrative` ← axios.get('/api/ai/explain/{ticker}') | Yes — triggered by HoldingsTable row click | FLOWING |
| `StructuredOutputChart.vue` | structured (StructuredChartDto) | `aiStore.structured.data` ← axios.get('/ai-structured-demo.json') | Yes — static seeded fixture (intentional demo-only stub) | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED — server not running. The AiDemoModeIntegrationTest and KeyLeakageIntegrationTest embedded within the build serve as the executable behavioral probes (run within Testcontainers). SUMMARY reports BUILD SUCCESS with all AI tests passing.

### Probe Execution

Step 7c: No probe-*.sh scripts declared or discovered for this phase. The AI test suite (DemoModeAdvisorTest, LlmKeySessionHolderTest, KeyLeakageIntegrationTest, AiKeyControllerTest, AiSeedRunnerTest, AiDemoModeIntegrationTest, AiControllerIntegrationTest, ExplainPositionServiceTest) serves as the executable verification suite. SUMMARY records full backend suite BUILD SUCCESS.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|------------|-------------|--------|----------|
| AI-01 | 06-01, 06-02, 06-03, 06-04 | All AI features work with zero API key via seeded responses (demo mode) | SATISFIED | DemoModeAdvisor short-circuits entire advisor chain in demo mode; 16 seed rows cover all panels; AiDemoModeIntegrationTest EXECUTABLE no-network proof (chain.nextCall()==0 in demo) |
| AI-02 | 06-01, 06-02, 06-04 | BYO-key popup for session-only key switching to live; key never logged/echoed/persisted | SATISFIED | AiKeyController POST/DELETE /api/ai/key; LlmKeySessionHolder @SessionScope no-@ToString; KeyLeakageIntegrationTest security gate; BYOKeyModal clears keyInput; ai store never stores apiKey |
| AI-07 | 06-01, 06-03, 06-04 | User can click a holding for AI explain-this-position narrative | SATISFIED | ExplainPositionService + AiController GET /explain/{ticker}; IDOR guard (ExplainPositionServiceTest); HoldingsTable @explain event; ExplainDrawer rendered in DashboardView |
| AI-08 | 06-01, 06-03, 06-04 | User sees AI-generated daily portfolio commentary on dashboard | SATISFIED | CommentaryService + AiController GET /commentary; persona key mapping from Portfolio.getStyle(); CommentaryCard with three-state rendering wired in DashboardView |

All 4 Phase-6 requirement IDs (AI-01, AI-02, AI-07, AI-08) are SATISFIED. No orphaned requirements.

REQUIREMENTS.md traceability table marks all four as "Phase 6 — Complete" — consistent with codebase evidence.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| — | — | — | — | — |

No TBD/FIXME/XXX markers found in any Phase 6 AI production or test files. No stub return patterns found in production service files (services are fully implemented). No hardcoded-empty props at call sites in DashboardView. No localStorage/sessionStorage writes in ai store or BYOKeyModal. No @ToString on LlmKeySessionHolder.

Note on `BYOKeyModal` emit-before-await: emits `submitted`+`close` synchronously before `await aiStore.setKey()`. This is a documented intentional deviation (fixing a test timing issue) noted in 06-04-SUMMARY. The key is still cleared before the await. T-06-11 invariant preserved.

Note on `AiModeBadge` being props-driven rather than store-reading: deliberate design decision per 06-04-SUMMARY. TopBar derives mode/provider from `useAiStore().status.data` and passes as props. Test scaffold passes props directly — consistent and correct.

### Human Verification Required

#### 1. Demo Mode Badge and Live Flip

**Test:** Log in as alice/demo1234. Observe the top bar. Note the "Demo" badge. Open "Connect Live AI" popup, select Anthropic, paste a real Anthropic key, submit. Observe the badge.
**Expected:** Badge initially shows "Demo" (muted pill). After key submission, badge shows "Live · Claude" (up-color/green pill). Commentary card refreshes. Clearing the key (DELETE /api/ai/key) reverts badge to "Demo".
**Why human:** AiModeBadge state-flip requires visual browser observation; the full round-trip (modal submit → store setKey() → fetchStatus() → badge re-render) is a live browser interaction.

#### 2. Persona-Accurate Commentary

**Test:** Log in as alice (Growth), note the CommentaryCard content. Switch to bob (Income) and charlie (Balanced) via the persona switcher.
**Expected:** Alice commentary references NVDA, AAPL, MSFT, AMZN, TSLA; mentions "growth", "AI infrastructure", or "Technology". Bob commentary references JPM, BAC, XOM, CVX, KO, PG; mentions "dividend", "income", "yield". Charlie commentary references a balanced/mixed portfolio. Each persona's commentary is distinctly different.
**Why human:** Automated tests only assert non-blank values. The actual authored content quality and persona distinctness require human reading.

#### 3. ExplainDrawer — Row Click and Narrative

**Test:** On the holdings table, click the AAPL row.
**Expected:** ExplainDrawer slides in from the right. Content (after loading skeleton clears) shows a 2-3 paragraph narrative that names "AAPL", references "Apple" or "iPhone", mentions "Technology sector", and uses hedging language. Clicking the X button or overlay closes the drawer.
**Why human:** Row-click event, drawer slide-in animation, z-index layering above TopBar, and narrative content accuracy require visual inspection.

#### 4. BYOKeyModal Screenshot Quality

**Test:** Click "Connect Live AI" button.
**Expected:** Modal appears centered with dark overlay. Title "Connect Your Own AI Key" visible. Two provider radio buttons (Anthropic Claude, OpenAI GPT). Password input field (masked). Security note "Your key is used only for this session and is never saved or transmitted to us." Cancel and "Connect Live AI" buttons.
**Why human:** Screenshot-worthiness and visual polish are subjective. This is the README screenshot artifact.

#### 5. Structured-Output Chart

**Test:** Observe the AI panel in the col-8 row (below Monte Carlo chart, above holdings table).
**Expected:** StructuredOutputChart card shows title "AI-Detected Sector Exposure", subtitle "Structured output (demo)", and a horizontal bar chart with 5 bars (Technology 42.5%, Financials 21.0%, Energy 14.3%, Consumer Staples 12.7%, Healthcare 9.5%). A "Structured Output · Demo" badge is visible.
**Why human:** ECharts canvas rendering and chart layout require visual inspection. Automated tests only stub vue-echarts.

### Gaps Summary

No gaps. All 4 must-have truths are VERIFIED at the code level. All artifacts exist and are substantive, wired, and data-flowing. No blockers found.

The 5 human verification items above are for visual/UX quality confirmation and README screenshot capture — they do not represent code defects. The automated test evidence (81 frontend tests green, full backend suite BUILD SUCCESS including KeyLeakageIntegrationTest, AiDemoModeIntegrationTest executable no-network proof) covers all behavioral contracts.

---

_Verified: 2026-06-09_
_Verifier: Claude (gsd-verifier)_
