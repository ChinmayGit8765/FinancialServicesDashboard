---
phase: 08-live-ai-features
fixed_at: 2026-06-09T23:57:00Z
review_path: .planning/phases/08-live-ai-features/08-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 10
skipped: 0
status: all_fixed
---

# Phase 08: Code Review Fix Report

**Fixed at:** 2026-06-09T23:57:00Z
**Source review:** `.planning/phases/08-live-ai-features/08-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (5 Critical + 5 Warning; IN-01 also applied as bonus)
- Fixed: 10
- Skipped: 0

**Build results:**
- Backend: `.\mvnw.cmd -B verify` — **207 tests run, 0 failures, 0 errors, 2 skipped** (pre-existing skips). BUILD SUCCESS.
- Frontend: `npm run test` — **86 tests, 16 test files, 0 failures**. `npm run build` — BUILD SUCCESS.
- `StockQuoteToolServiceTest`: 15 tests (was 7) — all green, 8 new regression tests added.
- `KeyLeakageIntegrationTest`: unchanged test logic, all green; comments updated (IN-03).

---

## Fixed Issues

### CR-01: Finnhub token moved from URL to X-Finnhub-Token request header

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`, `backend/src/test/java/com/quantlens/ai/KeyLeakageIntegrationTest.java`
**Commit:** f123b69
**Applied fix:** Replaced `BASE_URL + "?symbol=" + ticker + "&token=" + finnhubApiKey` with `BASE_URL + "?symbol=" + ticker` plus `.header("X-Finnhub-Token", finnhubApiKey)` on the HttpRequest builder. The token never appears in the URI string.
**Regression test:** `T2b-CR01-HEADER` — verifies the outgoing `HttpRequest` has no `token=` in the URI and has `X-Finnhub-Token` header set to the API key.

---

### CR-02: TTL cache check-then-act race — CachedQuote now stores absolute expiry

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`
**Commit:** f123b69 (same commit as CR-01; both in FinnhubQuoteClient)
**Applied fix:** `CachedQuote` record changed from `(result, fetchedAt)` to `(result, expiresAt)` with an `isExpired()` method that does `System.currentTimeMillis() >= expiresAt`. Cache write uses `System.currentTimeMillis() + TTL_MILLIS`. The TTL check is now `!cached.isExpired()` — self-contained in the record rather than a computed expression at the call site.
**Regression test:** `T3b-CR02-EXPIRY` — verifies that after `clearCache()` the client makes a second HTTP call (simulating expiry).

---

### CR-03: Zero-price result no longer cached as valid FINNHUB quote

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`
**Commit:** f123b69
**Applied fix:** Guard changed from `if (raw.c() == 0.0 && raw.t() == 0L)` to `if (raw.c() == 0.0 || raw.t() == 0L)`. Zero price alone (regardless of timestamp) now triggers the seeded fallback. The `cache.put` is only reached when price is non-zero AND timestamp is non-zero.
**Regression test:** `T4b-CR03` — `c=0.0, t=1700000000` (valid timestamp, zero price) → result is `source=SEEDED`, price equals the seeded close, not $0.00.

---

### CR-04: Non-zero price with zero timestamp falls back to seeded (no epoch-1970 asOf)

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`
**Commit:** f123b69
**Applied fix:** Same `||` guard as CR-03 (applied together). `t == 0L` now independently triggers fallback even when `c` is non-zero.
**Regression test:** `T4c-CR04` — `c=189.25, t=0` → result is `source=SEEDED`, `asOf` does NOT contain "1970", price equals the seeded close.

---

### CR-05: ChatClientStrategy exception message no longer embeds attacker-controlled provider string

**Files modified:** `backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java`
**Commit:** 195dd47
**Applied fix:** `default -> throw new IllegalArgumentException("Unknown provider: " + keyHolder.getProvider())` changed to `default -> throw new IllegalArgumentException("Unknown provider")`. The provider string (which comes from the request body) is never concatenated into the exception message, preventing log injection via newlines or ANSI escape sequences.

---

### WR-01: Null/blank ticker guard added at top of getQuote()

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/FinnhubQuoteClient.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`
**Commit:** f123b69
**Applied fix:** Added guard at the top of `getQuote()`: `if (ticker == null || ticker.isBlank())` returns a safe `StockQuoteResult("UNKNOWN", BigDecimal.ZERO, "N/A", "CLOSED", "SEEDED")` without touching `ConcurrentHashMap` (which throws NPE on null key).
**Regression tests:** `T4d-WR01` (null ticker) and `T4e-WR01` (blank ticker) — both return `ticker=UNKNOWN, source=SEEDED` with no NPE; `mockHttpClient` is not called.

---

### WR-02: Null provider guard in ChatClientStrategy.buildModel()

**Files modified:** `backend/src/main/java/com/quantlens/ai/chat/ChatClientStrategy.java`
**Commit:** 195dd47
**Applied fix:** Added null check before the switch: `String provider = keyHolder.getProvider(); if (provider == null) { return baseAnthropicModel; }`. This prevents a `NullPointerException` (which bypasses `handleIllegalArgument`) if `hasKey()` contract is ever relaxed.

---

### WR-03: StructuredInsightRecord.series null coerced to empty list

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/StructuredInsightRecord.java`
**Commit:** 1f1da9e
**Applied fix:** Added `@JsonSetter(nulls = Nulls.AS_EMPTY)` annotation on the `series` record component. Jackson now coerces `"series":null` or a missing `series` field to `List.of()` during deserialization — the frontend chart iterator never receives null.

---

### WR-04: InsightEntry.value changed to boxed Double with null/NaN guard

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/InsightEntry.java`
**Commit:** 3e08606
**Applied fix:** `double value` changed to `Double value`. Added compact constructor that coerces `null` and non-finite values (`Double.isFinite(value) == false`) to `0.0`. This prevents `MismatchedInputException` (502) when the LLM returns `{"value":null}`.

---

### WR-05: @Transactional(readOnly=true) removed from AiController endpoint methods

**Files modified:** `backend/src/main/java/com/quantlens/ai/api/AiController.java`
**Commit:** 5cd8a9a
**Applied fix:** Removed `@Transactional(readOnly = true)` from `explain()`, `commentary()`, and `structured()` methods. Removed the now-unused `import org.springframework.transaction.annotation.Transactional`. The service-layer `@Transactional` annotations are sufficient and own the transaction boundary correctly, without pinning a JDBC connection across multi-second LLM calls.

---

### IN-01 (bonus): Ticker canonicalized to uppercase in StockQuoteToolService

**Files modified:** `backend/src/main/java/com/quantlens/ai/tools/StockQuoteToolService.java`, `backend/src/test/java/com/quantlens/ai/StockQuoteToolServiceTest.java`
**Commit:** 7ccedb3
**Applied fix:** `return finnhubClient.getQuote(ticker != null ? ticker.toUpperCase(Locale.ROOT) : ticker)` — LLM-supplied lowercase/mixed-case tickers are canonicalized before the Finnhub call and cache key lookup.
**Regression tests:** `T8-IN01` and `T9-IN01` verify `"aapl"` and `"Aapl"` both dispatch to `getQuote("AAPL")`.

---

_Fixed: 2026-06-09T23:57:00Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
