---
phase: 02-portfolio-domain
reviewed: 2026-06-07T00:00:00Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java
  - backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java
  - backend/src/main/java/com/quantlens/portfolio/api/HoldingDto.java
  - backend/src/main/java/com/quantlens/portfolio/api/PortfolioPnlDto.java
  - backend/src/main/java/com/quantlens/portfolio/api/DateValueDto.java
  - backend/src/main/java/com/quantlens/portfolio/api/AllocationSliceDto.java
  - backend/src/main/java/com/quantlens/portfolio/api/TransactionDto.java
  - backend/src/main/java/com/quantlens/portfolio/api/BenchmarkComparisonDto.java
  - backend/src/main/java/com/quantlens/portfolio/domain/PositionRepository.java
  - backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java
  - backend/src/main/java/com/quantlens/marketdata/domain/OhlcvBarRepository.java
  - backend/src/main/resources/application.yml
  - backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java
  - backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java
  - backend/src/test/java/com/quantlens/portfolio/GoldenValuePrinterTest.java
findings:
  critical: 5
  warning: 7
  info: 4
  total: 16
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-06-07T00:00:00Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Reviewed the full Phase 2 implementation: `PortfolioService`, `PortfolioController`, all DTO records, both repository interfaces, and test classes. The code is generally well-structured with correct IDOR prevention and solid BigDecimal discipline in the common paths. However, five critical defects were found:

1. `getPortfolioPnl` crashes with `IndexOutOfBoundsException` on an empty portfolio (no positions with qty > 0).
2. `getBenchmarkComparison` crashes with `ArrayIndexOutOfBoundsException` when the benchmark has fewer bars than the equity curve, and also silently produces wrong data if their calendars diverge.
3. The `runningCostMap` in `getTransactions` is keyed per-security but transactions for **multiple securities** all share the same map — the algorithm is multi-security-unaware and produces wrong cost-basis values for any portfolio that holds more than one ticker.
4. `resolvePortfolioId` contains two extra DB round-trips on every request (AppUser lookup + Portfolio lookup) that could instead be one, and uses a `List` return from `PortfolioRepository` without a `LIMIT 1`, making it susceptible to inconsistent results if a user somehow has multiple portfolios.
5. The paginated `TransactionDto` `tradeValue` uses `tx.getQuantity().multiply(tx.getPrice()).setScale(2, ...)`, which will produce an incorrect (or at minimum unexplained) value for SELL transactions — the service does not negate quantity for sells, so the displayed trade value is always positive regardless of direction.

---

## Critical Issues

### CR-01: `getPortfolioPnl` crashes with `IndexOutOfBoundsException` on empty portfolio

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:255`

**Issue:** `getPortfolioPnl` calls `buildEquityCurve(positions)` and then immediately indexes the result without checking for emptiness:

```java
BigDecimal totalMarketValue = equityCurve.get(equityCurve.size() - 1).value();  // line 255
```

When `positions` is empty (or all positions have `qty <= 0`), `buildEquityCurve` returns an empty list — it filters out non-positive-qty positions on line 564, collects zero security IDs, calls `findAllBySecurityIdsOrdered` with an empty list, and the `closesBySecId` map stays empty, causing the `firstDate` resolution to throw `IllegalStateException`. That exception message is internal and will leak to the HTTP response as a 500.

More practically: even if the IllegalStateException is caught, returning a 500 for a legitimate "user has no holdings" state is wrong. The correct response is a PnlDto with zeros and an empty curve, or a 204.

The same crash happens in `getBenchmarkComparison` (line 293) which also calls `buildEquityCurve` and then unconditionally reads `equityCurve.get(0)` on line 314.

**Fix:**
```java
public PortfolioPnlDto getPortfolioPnl(Long portfolioId) {
    List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

    // Guard: no active positions → return zeroed-out DTO with empty curve
    List<Position> active = positions.stream()
            .filter(p -> p.getQuantity().signum() > 0).toList();
    if (active.isEmpty()) {
        return new PortfolioPnlDto(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                List.of());
    }

    List<DateValueDto> equityCurve = buildEquityCurve(positions);
    // ... rest of existing logic unchanged
}
```

Apply the same guard to `getBenchmarkComparison` before using `equityCurve.get(0)` on line 314.

**Note:** The existing test suite does not exercise the empty-portfolio path, so this defect does not contradict any passing test.

---

### CR-02: `getBenchmarkComparison` crashes or silently produces wrong data when benchmark calendar diverges from portfolio calendar

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:317-321`

**Issue:** The benchmark loop iterates `equityCurve.size()` times and uses positional indexing into `spxBars`:

```java
for (int i = 0; i < equityCurve.size(); i++) {
    dates.add(equityCurve.get(i).date().toString());
    portfolioSeries.add(rebaseToIndex(equityCurve.get(i).value(), portfolioBase));
    benchmarkSeries.add(rebaseToIndex(spxBars.get(i).getClosePrice(), benchmarkBase)); // line 320
}
```

Two failure modes:

**a) ArrayIndexOutOfBoundsException:** If `spxBars.size() < equityCurve.size()` (e.g., the benchmark security has fewer seeded bars than portfolio securities), `spxBars.get(i)` throws `IndexOutOfBoundsException` at i = spxBars.size().

**b) Silently wrong dates/values:** The code assumes `spxBars` and `equityCurve` share an identical calendar and are sorted identically. This assumption is documented but not enforced. If there is even one calendar mismatch — a holiday gap in one series but not the other — the positional pairing silently aligns wrong dates together, producing a benchmark comparison that looks plausible but is numerically incorrect.

**Fix:** Align by date, not by position:
```java
// Build a date → close map for the benchmark
Map<LocalDate, BigDecimal> spxByDate = spxBars.stream()
        .collect(Collectors.toMap(b -> b.getBarDate(), OhlcvBar::getClosePrice));

for (DateValueDto point : equityCurve) {
    BigDecimal spxClose = spxByDate.get(point.date());
    if (spxClose == null) {
        continue; // skip dates with no benchmark bar (or throw, or use previous)
    }
    dates.add(point.date().toString());
    portfolioSeries.add(rebaseToIndex(point.value(), portfolioBase));
    benchmarkSeries.add(rebaseToIndex(spxClose, benchmarkBase));
}
```

**Note:** The test `getBenchmark_seriesSameLength` passes because the seeded data happens to have matching calendars. If the seed ever changes or a new benchmark security is added, the test will start crashing. Flag as needing test verification.

---

### CR-03: `buildRunningCostMap` is multi-security-blind — wrong cost basis for portfolios with more than one ticker

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:347-374`

**Issue:** `buildRunningCostMap` maintains a single `runningQty` / `runningCost` accumulator across ALL transactions in the portfolio, regardless of which security each transaction belongs to. The query in `findByPortfolioIdChronological` returns every transaction for the portfolio, for all tickers, sorted by date. When the chronological stream mixes BUY AAPL, BUY MSFT, SELL AAPL, the algorithm treats all three as if they were the same security:

```java
// Iteration 1: BUY AAPL 100 @ 150  → runningQty=100, runningCost=15000
// Iteration 2: BUY MSFT 50 @ 300   → runningQty=150, runningCost=30000  (WRONG: MSFT mixed into AAPL's pool)
// Iteration 3: SELL AAPL 30 @ 200  → avgCostNow = 30000/150 = 200 (WRONG: averaged across both tickers)
```

The running cost basis displayed in the `TransactionDto` will be nonsensical for any portfolio holding more than one ticker — which is every real portfolio in the seed data (alice holds AAPL, MSFT, NVDA, AMZN, TSLA).

**Fix:** Key the accumulators by `securityId`:
```java
static Map<Long, BigDecimal> buildRunningCostMap(List<Transaction> chronologicalTxs) {
    Map<Long, BigDecimal> result = new HashMap<>();
    Map<Long, BigDecimal> runningQtyMap  = new HashMap<>();
    Map<Long, BigDecimal> runningCostMap = new HashMap<>();

    for (Transaction tx : chronologicalTxs) {
        Long secId = tx.getSecurity().getId();
        BigDecimal qty   = tx.getQuantity();
        BigDecimal price = tx.getPrice();
        BigDecimal runningQty  = runningQtyMap.getOrDefault(secId, BigDecimal.ZERO);
        BigDecimal runningCost = runningCostMap.getOrDefault(secId, BigDecimal.ZERO);

        if ("BUY".equals(tx.getTxType())) {
            runningCost = runningCost.add(qty.multiply(price));
            runningQty  = runningQty.add(qty);
        } else { // SELL
            BigDecimal avgCostNow = runningQty.signum() == 0
                    ? BigDecimal.ZERO
                    : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
            runningCost = runningCost.subtract(qty.multiply(avgCostNow));
            runningQty  = runningQty.subtract(qty);
        }

        runningQtyMap.put(secId, runningQty);
        runningCostMap.put(secId, runningCost);

        BigDecimal avgCostAtThisPoint = runningQty.signum() == 0
                ? BigDecimal.ZERO
                : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
        result.put(tx.getId(), avgCostAtThisPoint);
    }
    return result;
}
```

The same bug exists in `computeRunningCostBasisFromTuples` (lines 387–415), which is the tuple-based overload used by unit tests. That overload is documented as operating on a single security stream, which is fine — but the entity-based `buildRunningCostMap` must be fixed.

**Note:** The unit tests `runningCostBasisAfterBuy` and `runningCostBasisAfterProportionalSell` both use `computeRunningCostBasisFromTuples` with a single-security scenario, so they pass and do not detect this bug. The integration test `getTransactions_mostRecentFirst` only checks date ordering, not the `runningCostBasis` value. This defect contradicts no passing test but will produce wrong values for all multi-ticker portfolios.

---

### CR-04: `getPortfolioPnl` derives `totalMarketValue` from equity curve's last point instead of latest closes — diverges from `getHoldings` and may be stale

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:255`

**Issue:** `getPortfolioPnl` sets `totalMarketValue` from the last entry of the equity curve:

```java
BigDecimal totalMarketValue = equityCurve.get(equityCurve.size() - 1).value();
```

The equity curve is built using `findAllBySecurityIdsOrdered` (all 504 bars per security). The last bar in the curve is the bar with the latest `barDate` in the common date intersection — which is `min(lastKey across all securities)`. If one security's data ends earlier than the others (e.g., 503 vs 504 bars due to a seed inconsistency), the `totalMarketValue` is computed one day stale for all positions.

Meanwhile, `getHoldings` uses `findLatestBarBySecurityIds` (a `MAX(barDate)` correlated subquery) for each security independently, which always picks the absolute latest bar per ticker regardless of calendar intersection. As a result, `getHoldings.currentMarketValue` and `getPortfolioPnl.totalMarketValue` can diverge even within the same session if securities have different bar-count lengths.

This is a correctness contract violation: the two endpoints purport to show the same portfolio total but can compute different numbers from the same underlying data.

**Fix:** In `getPortfolioPnl`, compute `totalMarketValue` using `latestCloseBySecurityId` (the same helper used by `getHoldings`) rather than pulling it from the curve tail:

```java
Map<Long, BigDecimal> latestCloses = latestCloseBySecurityId(positions);
BigDecimal totalMarketValue = positions.stream()
        .filter(p -> p.getQuantity().signum() > 0)
        .map(p -> {
            BigDecimal close = latestCloses.getOrDefault(p.getSecurity().getId(), BigDecimal.ZERO);
            return p.getQuantity().multiply(close).setScale(2, RoundingMode.HALF_UP);
        })
        .reduce(BigDecimal.ZERO, BigDecimal::add);
```

**Note:** Tests check `equityCurve.size() == 504` and `dates[0] == "2022-09-12"` but do not assert that `totalMarketValue` matches the value returned by the holdings endpoint, so this divergence is untested.

---

### CR-05: `TransactionDto.tradeValue` is always positive — SELL quantities are not negated

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:111`

**Issue:** Trade value is computed as:

```java
tx.getQuantity().multiply(tx.getPrice()).setScale(2, RoundingMode.HALF_UP)
```

`getQuantity()` returns the raw quantity from the `transactions` table. The `Transaction` entity stores quantity as a positive number (a `NUMERIC(18,4)` with no sign recorded — direction is captured by `txType`). As a result, both BUY and SELL `TransactionDto` entries show a positive `tradeValue`. A financial UI would typically show SELL proceeds as negative (cash inflow, outflow distinction), or at minimum the field name/contract must document that it is an unsigned magnitude.

If Phase 3 uses `tradeValue` directly to plot a cash-flow chart (e.g., "money out of wallet"), SELL transactions will show the wrong sign and inflate the apparent spending.

**Fix (minimal — sign):**
```java
BigDecimal rawValue = tx.getQuantity()
        .multiply(tx.getPrice())
        .setScale(2, RoundingMode.HALF_UP);
BigDecimal tradeValue = "SELL".equals(tx.getTxType())
        ? rawValue.negate()
        : rawValue;
```

Or, if unsigned magnitude is intentional, document it explicitly in `TransactionDto.tradeValue` Javadoc and add a note in the DTO: *"always positive; use `txType` to determine direction"*.

---

## Warnings

### WR-01: `resolvePortfolioId` performs two extra repository queries on every endpoint call — N+1 at the controller layer

**File:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java:183-195`

**Issue:** Every protected endpoint resolves the portfolio via:
1. `appUserRepository.findByUsername(username)` — SELECT from `app_users`
2. `portfolioRepository.findByUserId(userId)` — SELECT from `portfolios`

These are two SQL round-trips per request, fired inside the controller's `@Transactional(readOnly = true)` scope. The service then fires its own queries. For a read-heavy dashboard with many simultaneous users this will be noticeable. More importantly, the `PortfolioRepository.findByUserId` returns a `List<Portfolio>` without any `LIMIT`, so if a user's account is in an inconsistent state with two rows, `portfolios.get(0)` silently picks whichever the database returns first — the choice is non-deterministic.

**Fix:** Add a dedicated JPQL method that resolves the portfolio ID in one query:

```java
// In PortfolioRepository:
@Query("SELECT p.id FROM Portfolio p WHERE p.user.id = (SELECT u.id FROM AppUser u WHERE u.username = :username)")
Optional<Long> findPortfolioIdByUsername(@Param("username") String username);
```

And in the controller:
```java
private Long resolvePortfolioId(Authentication authentication) {
    if (authentication == null || !authentication.isAuthenticated()) {
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    return portfolioRepository.findPortfolioIdByUsername(authentication.getName())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio found"));
}
```

This eliminates the user-not-found vs no-portfolio ambiguity in status codes and reduces it to one round-trip.

---

### WR-02: `buildEquityCurve` uses reference-calendar from an arbitrary map entry — wrong dates if securities have non-identical calendars

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:591`

**Issue:**

```java
NavigableMap<LocalDate, BigDecimal> referenceDates = closesBySecId.values().iterator().next();
```

This picks the first entry from a `TreeMap` (which iterates by key — security ID order, not by any richness of date coverage). The comment says "all securities share the same calendar — safe to take any one", but this assumption is not enforced by any query or guard. If one security has a gap (e.g., 503 bars instead of 504), the reference calendar is whichever security appears first by ID. If that security has the gap, a legitimate trading date is silently omitted from the equity curve. If a security with a complete calendar were chosen instead, the gap security would hit the `close == null` defensive guard on line 601-603 and be skipped, preserving the correct total.

More critically: the reference dates are iterated via `referenceDates.subMap(firstDate, true, lastDate, true)`, but the `firstDate` and `lastDate` are computed from the intersection of all securities' ranges. If the reference calendar itself has sparse gaps within that range, the curve will have fewer than expected entries.

**Fix:** Compute the reference date set as the union of all dates that appear in ALL securities' maps (intersection enforcement), rather than delegating to an arbitrarily chosen security's map:

```java
// Collect dates present in ALL security maps (true intersection)
Set<LocalDate> commonDates = null;
for (NavigableMap<LocalDate, BigDecimal> secMap : closesBySecId.values()) {
    Set<LocalDate> secDates = new TreeSet<>(secMap.subMap(firstDate, true, lastDate, true).keySet());
    if (commonDates == null) {
        commonDates = secDates;
    } else {
        commonDates.retainAll(secDates);
    }
}
for (LocalDate date : commonDates) { ... }
```

---

### WR-03: `getAllocation` last-slice weight may be negative or >1 if `totalValue` is zero but `sectorValues` is non-empty

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:226-237`

**Issue:** The allocation loop guards `sectorValues.isEmpty()` (line 217) but does not guard `totalValue == 0`. If all positions have a zero latest-close price (all close prices are `ZERO` via the `getOrDefault` fallback), `totalValue` will be zero but `sectorValues` will be non-empty (e.g., `{"Technology": 0.00}`). The loop will then attempt:

```java
weight = sectorMktValue.divide(totalValue, 6, RoundingMode.HALF_UP);  // 0 / 0 → ArithmeticException!
```

This throws `ArithmeticException: Division by zero` for non-last entries, or produces `weight = BigDecimal.ONE.subtract(BigDecimal.ZERO) = 1.000000` for the last entry if there is only one sector (confusingly: the single-sector case never reaches the `divide` path because it is always the last-slice). For two or more sectors, the first sector's `divide` throws before the last-slice residual is reached.

**Fix:** Add a zero-total guard:

```java
if (totalValue.signum() == 0) {
    return List.of();
}
```

Place it immediately after the accumulation loop, before the slice-building loop.

---

### WR-04: `getTransactions` `Page<Transaction>` with JPQL `JOIN FETCH` and `ORDER BY` may trigger HHH90003004 Hibernate warning and incorrect counts with some DB/driver versions

**File:** `backend/src/main/java/com/quantlens/portfolio/domain/TransactionRepository.java:30-38`

**Issue:** The query uses a JPQL `JOIN FETCH` with an `ORDER BY` clause in a paginated `Page<>` return. In Hibernate 6.x (which Spring Boot 3.x ships), this pattern is generally safe because `Transaction → Security` is a many-to-one (single-valued) association. However, the `@Query` does not supply a `countQuery` parameter. Spring Data JPA generates the count query automatically by stripping `JOIN FETCH` — but this automatic rewriting is fragile if Hibernate's SQL translator encounters the original JPQL's `ORDER BY` or `JOIN FETCH` during count derivation. Observed behavior in some Hibernate 6.2+ builds: HHH90003004 warning + count query executing `SELECT count(*) FROM (SELECT DISTINCT ...)` with a redundant join, inflating query cost.

**Fix:** Provide an explicit count query:

```java
@Query(value = """
        SELECT t FROM Transaction t
        JOIN FETCH t.security
        WHERE t.portfolio.id = :portfolioId
        ORDER BY t.txDate DESC, t.id DESC
        """,
       countQuery = """
        SELECT count(t) FROM Transaction t
        WHERE t.portfolio.id = :portfolioId
        """)
Page<Transaction> findByPortfolioIdWithSecurity(
        @Param("portfolioId") Long portfolioId,
        Pageable pageable);
```

---

### WR-05: `computeDailyChange` throws `IllegalArgumentException` — not caught by any caller, produces unchecked 500

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:497-501`

**Issue:**

```java
if (equityCurve.size() < 2) {
    throw new IllegalArgumentException(
            "Equity curve must have at least 2 entries to compute daily change; got "
            + equityCurve.size());
}
```

The caller `getPortfolioPnl` (line 266) does not catch this. If the curve has exactly 1 entry — possible when a portfolio has a single position and that security has exactly one bar in the DB — the service throws `IllegalArgumentException`, which Spring translates to a 500 Internal Server Error with the message string leaked in the response body (depending on `server.error.include-message` config).

The `IllegalArgumentException` message includes internal implementation detail ("Equity curve must have at least 2 entries"). This is an information-disclosure issue under an adversarial lens.

**Fix:** In `getPortfolioPnl`, handle the single-entry curve gracefully before calling `computeDailyChange`:

```java
BigDecimal dailyChangeAbs = BigDecimal.ZERO;
BigDecimal dailyChangePct = BigDecimal.ZERO;
if (equityCurve.size() >= 2) {
    BigDecimal[] dailyChange = computeDailyChange(equityCurve);
    dailyChangeAbs = dailyChange[0];
    dailyChangePct = dailyChange[1];
}
```

---

### WR-06: `resolvePortfolioId` does not distinguish "user not found in DB" from "user found but has no portfolio" — both return 404 or 401 inconsistently

**File:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java:188-193`

**Issue:** If `findByUsername` returns empty, a 401 is thrown (line 189). If `findByUserId` returns empty, a 404 is thrown (line 192). These two conditions are logically different: an authenticated session whose principal name is not in the DB suggests a stale/corrupted session (401 is appropriate), but the status code difference leaks information about whether the username exists. An attacker with a valid session for a deleted user gets a 401; a valid session for a user with no portfolio gets a 404. This is a minor information disclosure.

More importantly, the 401 for "user not found" bypasses Spring Security's standard authentication error handling (e.g., `AuthenticationEntryPoint`), so the response format may differ from the standard auth error format, confusing the frontend.

**Fix:** Collapse both cases into a single 401 with no distinguishing message, or treat the no-portfolio case as a separate business error returned as 200 with an empty response rather than an HTTP error.

---

### WR-07: `application.yml` embeds a default demo password in plaintext — will be committed to source control

**File:** `backend/src/main/resources/application.yml:55`

**Issue:**

```yaml
quantlens:
  demo:
    password: ${QUANTLENS_DEMO_PASSWORD:demo1234}
```

The default `demo1234` is a fallback that will be active whenever the `QUANTLENS_DEMO_PASSWORD` env var is not set. The comment calls it intentional for the demo UX, which is defensible. However, the password is hardcoded in source — any developer who checks out the repo and runs locally gets a working credential without additional setup. If this project ever becomes less "demo" and more production-facing, this is a leaked credential waiting to cause an incident.

**Fix:** At minimum, document in `README` and `CLAUDE.md` that this value must be overridden via env var for any non-local deployment. Ideally, replace the hardcoded default with a sentinel that forces explicit configuration:

```yaml
quantlens:
  demo:
    password: ${QUANTLENS_DEMO_PASSWORD}   # required — no default; set explicitly
```

---

## Info

### IN-01: `extractJsonField` helper in `PortfolioControllerIntegrationTest` is defined but never called

**File:** `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java:348-354`

**Issue:** The `extractJsonField(String json, String fieldName)` method is declared as a private helper but is not invoked by any test method in the class. It is dead code.

**Fix:** Remove it, or promote it to `AbstractPostgresIntegrationTest` if it is intended for future use.

---

### IN-02: `getTransactions` pagination default sort is applied via `@PageableDefault` but not enforced against client-supplied sort parameters

**File:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java:138`

**Issue:** `@PageableDefault(size = 20, sort = "txDate", direction = Sort.Direction.DESC)` sets a default, but a client can override it with `?sort=id,asc` or any other field name. The JPQL query in `findByPortfolioIdWithSecurity` has a hardcoded `ORDER BY t.txDate DESC, t.id DESC` clause that ignores whatever the `Pageable` sort is. This means the `Pageable` sort parameter is silently ignored, which is misleading to API clients.

**Fix:** Either: (a) remove the `@PageableDefault` sort direction since the JPQL enforces it anyway; or (b) remove the hardcoded `ORDER BY` from the JPQL and rely on Spring Data's sort injection, and add a `@SortDefault` annotation. Document the effective sort in the controller Javadoc.

---

### IN-03: `PortfolioController.getPnl` returns raw `PortfolioPnlDto` instead of `ResponseEntity<PortfolioPnlDto>` — inconsistent with other endpoints

**File:** `backend/src/main/java/com/quantlens/portfolio/api/PortfolioController.java:115`

**Issue:** Three endpoints return `ResponseEntity<T>` while two (`getPnl`, `getBenchmark`) return the DTO directly. This is inconsistent and means `getPnl` and `getBenchmark` cannot return a custom status code (e.g., 204 for empty portfolio) without refactoring. It also makes it harder for the frontend to handle error cases uniformly.

**Fix:** For consistency, wrap all returns in `ResponseEntity.ok(...)`:
```java
public ResponseEntity<PortfolioPnlDto> getPnl(Authentication authentication) {
    Long portfolioId = resolvePortfolioId(authentication);
    return ResponseEntity.ok(portfolioService.getPortfolioPnl(portfolioId));
}
```

---

### IN-04: `buildEquityCurve` passes the full `positions` list (including zero-qty positions) to the `closesBySecId` map lookup but filters them inside the day-value loop — minor inconsistency

**File:** `backend/src/main/java/com/quantlens/portfolio/service/PortfolioService.java:563-609`

**Issue:** `securityIds` is correctly filtered to `qty > 0` on line 564 before querying bars. However, the day-value loop on line 596 iterates the unfiltered `positions` list and re-checks `pos.getQuantity().signum() <= 0` inside the loop. If a position has `qty == 0`, `closesBySecId.get(pos.getSecurity().getId())` will return `null` (since its security ID was not included in the bar fetch), the null guard on line 601 fires with `continue`, and it is silently skipped. This is functionally correct but architecturally confusing: the contract of `positions` as input to the loop is unclear.

**Fix:** Filter `positions` to active-only once at the top of `buildEquityCurve` and use that filtered list consistently throughout:

```java
List<Position> activePositions = positions.stream()
        .filter(p -> p.getQuantity().signum() > 0)
        .toList();
// ... use activePositions everywhere below
```

---

_Reviewed: 2026-06-07T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
