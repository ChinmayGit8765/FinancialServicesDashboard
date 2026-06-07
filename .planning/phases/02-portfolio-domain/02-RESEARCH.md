# Phase 2: Portfolio Domain (REST API + Computations) — Research

**Researched:** 2026-06-07
**Domain:** Spring REST controllers / PortfolioService / financial math (P&L, equity curve, benchmark)
**Confidence:** HIGH

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- Session-scoped REST under `/api/portfolio`; portfolio derived from authenticated principal (SecurityContext), NOT a path variable.
- Five endpoints exactly: `GET /api/portfolio/holdings`, `/pnl`, `/allocation`, `/transactions`, `/benchmark`.
- `PortfolioService` in `com.quantlens.portfolio` owns all computation.
- Market data (OHLCV, securities) accessed via `marketdata.domain` named interface only.
- Current price = latest OHLCV close. Unrealized P&L = (currentClose − avgCostBasis) × quantity.
- Equity/P&L curve = constant-current-holdings valuation over every historical trading day.
- Running cost basis in transactions = cumulative average cost (buy/sell chronological).
- All monetary math in `BigDecimal`; scale 6 for prices/ratios, 2 for display money.
- Typed Java `record` DTOs (HoldingDto, PortfolioPnlDto, AllocationSliceDto, TransactionDto, BenchmarkComparisonDto).
- Pagination via Spring Data `Pageable`/`Page` for transactions.
- Integration tests extend `AbstractPostgresIntegrationTest`, authenticated as seeded persona, assert golden values from seed=42.
- Unit tests for pure computation methods.
- Spring Modulith boundary: add `allowedDependencies = {"marketdata::domain"}` to `portfolio` module if not already present (it already is — confirmed in Phase 1 output).
- `@Transactional(readOnly=true)` on all read endpoints.

### Claude's Discretion

- Exact DTO field names/shapes.
- Whether benchmark is folded into pnl or separate endpoint (context locks it as separate).
- Precise rebasing base (e.g. 100 or portfolio start value).
- Pagination wrapper shape.

### Deferred Ideas (OUT OF SCOPE)

- Vue views / ECharts rendering (Phase 3).
- Risk metrics: Sharpe, VaR, beta, volatility, correlation (Phase 4).
- Monte Carlo, stochastic forecasting (Phase 5).
- AI narration of the portfolio (Phase 6+).
- Realized P&L / tax-lot accounting beyond running cost basis.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PORT-01 | User can view holdings with current value, weight, cost basis, and unrealized P&L per position | Average-cost basis section; Holdings computation patterns; N+1 avoidance query design |
| PORT-02 | User can view portfolio-level P&L (total unrealized gain/loss and daily change) as a time-series curve | Equity curve math section; off-by-one analysis; daily change aggregation |
| PORT-03 | User can view an allocation breakdown by sector / asset class (pie or treemap) | Allocation weight math; BigDecimal rounding to 100% section |
| PORT-04 | User can view transaction history (buy/sell log with date, quantity, price, running cost basis) | Running average-cost basis section; Pageable pattern |
| PORT-05 | User can compare portfolio return against an S&P 500 proxy benchmark on the same chart | Benchmark rebasing math; common-base series section |
</phase_requirements>

---

## Summary

Phase 2 builds the financial computation backbone that every downstream phase (AI narration, risk metrics, frontend charts) depends on. The key credibility risk is not Spring mechanics — it is financial math that is subtly wrong and embarrassing when a quant-literate reviewer inspects it. Three areas require special care: (1) off-by-one on the first-day return in the equity curve, (2) correct average-cost basis maintenance when sells reduce the position, and (3) correct benchmark rebasing so both series share the same geometric base — not additive alignment. The Spring patterns are well-understood and low-risk.

The seeded data is fully known (MersenneTwister seed 42, deterministic OHLCV). This is a large advantage: every golden value in the tests can be computed offline from the GBmGenerator algorithm before a line of service code is written, and then asserted exactly (with `BigDecimal.compareTo` tolerance of ±0.01 for display values). The planner should include a "compute golden values" task before writing assertions.

**Primary recommendation:** Write the pure computation methods as package-private static helpers first, unit-test them with hand-crafted inputs, then wire them to JPA repositories. This separates the "math is right" proof from the "JPA query is efficient" concern and catches correctness bugs before they interact with Spring context.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Current price lookup | API / Backend (PortfolioService) | Database (OhlcvBar query) | Latest close is a server-side DB read; no client-side price inference |
| Unrealized P&L per position | API / Backend (PortfolioService) | — | Pure arithmetic on server; client gets a pre-computed value |
| Portfolio-level equity curve | API / Backend (PortfolioService) | Database (OhlcvBar bulk query) | 504 daily close prices × N positions — bulk-fetched and aggregated server-side |
| Allocation weights | API / Backend (PortfolioService) | — | Sum and divide are server-side; client gets ready-to-render slices |
| Running cost basis in transactions | API / Backend (PortfolioService) | — | Stateful chronological scan over transaction history; not computable in a single SQL aggregate |
| Benchmark series | API / Backend (PortfolioService) | Database (OhlcvBar query for SPX500) | Single security's close series converted to cumulative return on the server |
| Pagination | API / Backend (Spring Data Pageable) | — | Standard repository concern; not a client-side operation |
| Authentication / principal resolution | API / Backend (Spring Security) | — | SecurityContext → username → AppUser → Portfolio; established in Phase 1 |
| JSON serialization | API / Backend (Jackson) | — | BigDecimal scale and field names set server-side; Vue binds directly to the shape |

---

## Standard Stack

No new dependencies are needed for Phase 2. All required libraries are already on the classpath from Phase 1.

### Core (already present — Phase 1)

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring Boot 3.5.13 | 3.5.13 | REST controllers, DI, transaction management | Locked in Phase 1 |
| Spring Data JPA | (Boot-managed) | OhlcvBarRepository, PositionRepository, TransactionRepository | Already wired; Phase 1 entities available |
| Spring Security | (Boot-managed) | Session auth — `Authentication` parameter injection | Phase 1 SecurityConfig already provides session auth |
| Jackson Databind | (Boot-managed) | JSON serialization of record DTOs | Already on classpath; `BigDecimal` serializes as JSON number by default |
| Spring Modulith 1.4.11 | 1.4.11 (BOM) | Package boundary enforcement | Already wired; `portfolio` module already has `allowedDependencies = {"marketdata::domain"}` |

### No new dependencies required

Phase 2 is pure Java computation + Spring MVC + Spring Data JPA. The financial math (average cost, cumulative return, weighted average) does not require Hipparchus — it is BigDecimal arithmetic. Hipparchus (already on classpath for Phase 4 quant metrics) must not be pulled into this phase's computation — BigDecimal suffices and is what the CONTEXT.md mandates.

### Jackson BigDecimal serialization note

By default, Jackson serializes `BigDecimal` as a JSON number (not a string). This is correct for ECharts consumption in Phase 3 — ECharts expects numeric values, not string-encoded numbers. Do NOT add `@JsonFormat(shape = JsonFormat.Shape.STRING)` on monetary DTO fields unless specifically required; it breaks ECharts direct binding.

**One subtlety:** `BigDecimal.toPlainString()` is used internally by Jackson. A value of `1234.560000` with scale 6 serializes as `1234.560000` (trailing zeros preserved by Jackson's default BigDecimal serializer). For display-money fields (scale 2), set `setScale(2, HALF_UP)` before returning. For percentage/ratio fields, scale 6 is correct per CONTEXT.md.

---

## Package Legitimacy Audit

No new packages are installed in this phase. All computation uses Java standard library (`BigDecimal`, `LocalDate`, `List`) and libraries already on the classpath from Phase 1.

**Packages removed due to slopcheck [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

---

## Architecture Patterns

### System Architecture Diagram

```
HTTP GET /api/portfolio/holdings
        │
        ▼
PortfolioController (com.quantlens.portfolio.api)
  │  injects Authentication (Spring Security)
  │
  ▼ resolve principal
AppUserRepository.findByUsername(name)   ──▶  app_users table
  │
  ▼ resolve portfolio
PortfolioRepository.findByUserId(userId)  ──▶  portfolios table
  │
  ▼
PortfolioService.getHoldings(portfolioId)
  │
  ├──▶ PositionRepository.findByPortfolioId(id)  ──▶  positions JOIN securities
  │        (FETCH JOIN to load Security in one query)
  │
  ├──▶ OhlcvBarRepository.findLatestCloseBySecurityIds(ids)
  │        (single query returning Map<securityId, latestClose>)
  │
  └──▶ computeHoldings(positions, latestCloses)
           │  avgCostBasis from Position.avgCostBasis (already persisted by SeedRunner)
           │  unrealizedPnl = (close − avgCost) × qty
           │  weight = positionValue / totalPortfolioValue
           ▼
       List<HoldingDto>   ──▶  JSON response
```

```
HTTP GET /api/portfolio/pnl
        │
        ▼
PortfolioService.getPortfolioPnl(portfolioId)
  │
  ├──▶ PositionRepository.findByPortfolioId  (current holdings)
  │
  ├──▶ OhlcvBarRepository.findAllClosesBySecurityIds(ids, dateRange)
  │        returns List<OhlcvBar> for all holdings over full seeded window
  │        (single query: WHERE security_id IN (:ids) ORDER BY bar_date ASC)
  │
  └──▶ computeEquityCurve(positions, allBars)
           │  constant-current-holdings assumption
           │  for each date d:  portfolioValue(d) = Σ qty_i × close_i(d)
           │  equityCurve[d] = portfolioValue(d)
           │
           │  dailyChange = equityCurve[last] − equityCurve[last−1]  (abs)
           │  dailyChangePct = dailyChange / equityCurve[last−1]
           ▼
       PortfolioPnlDto {totalValue, totalUnrealizedGain, totalUnrealizedGainPct,
                        dailyChange, dailyChangePct,
                        equityCurve: [{date, value}, ...]}
```

```
HTTP GET /api/portfolio/benchmark
        │
        ▼
PortfolioService.getBenchmarkComparison(portfolioId)
  │
  ├──▶ portfolio holdings (same as pnl — reuse)
  ├──▶ OhlcvBarRepository.findAllClosesBySecurityIds(portfolioIds)  (portfolio series)
  ├──▶ SecurityRepository.findByBenchmarkTrue()                      (SPX500 security)
  └──▶ OhlcvBarRepository.findAllClosesBySecurityIds([spx500Id])    (benchmark series)
           │
           └──▶ rebaseToCommonBase(portfolioSeries, benchmarkSeries)
                    │  base = portfolioValue on first shared trading day
                    │  portfolio rebased:  portfolioIdx(d) = portfolioValue(d) / base × 100
                    │  benchmark rebased:  benchmarkIdx(d)  = spxClose(d) / spxClose(day0) × 100
                    │  IMPORTANT: benchmark is rebased to 100 at the SAME first trading day
                    ▼
               BenchmarkComparisonDto {dates[], portfolioSeries[], benchmarkSeries[]}
```

### Recommended Project Structure

```
com.quantlens.portfolio/
├── api/
│   ├── PortfolioController.java          (REST endpoints — thin, delegates to service)
│   ├── HoldingDto.java                   (record)
│   ├── PortfolioPnlDto.java              (record; contains nested DateValueDto)
│   ├── AllocationSliceDto.java           (record)
│   ├── TransactionPageDto.java           (thin page wrapper — optional)
│   ├── TransactionDto.java               (record; includes runningCostBasis field)
│   └── BenchmarkComparisonDto.java       (record; contains dates[], portfolioSeries[], benchmarkSeries[])
└── service/
    └── PortfolioService.java             (all computation; @Transactional(readOnly=true))
```

The `domain/` subpackage already exists from Phase 1 and must NOT be modified (entities + repos are locked).

**Module boundary:** The `portfolio` module `package-info.java` already declares `allowedDependencies = {"marketdata::domain"}`. The new `api/` and `service/` subpackages are inside `com.quantlens.portfolio` and inherit this permission automatically — no new `package-info.java` entries needed for Phase 2 unless controllers expose types that need to be consumed by another module (they do not in this phase).

---

## Financial Math: Correctness-Critical Patterns

### Pattern 1: Average-Cost Basis from Transaction Stream

**What:** Running average cost as chronological BUY/SELL transactions are applied. The cost basis stored in the `positions` table is the seeder's initial basis; the transaction list must recompute it chronologically for the `/transactions` endpoint's `runningCostBasis` field.

**Algorithm (correct):**

```java
// Source: standard GAAP average-cost accounting; no external library needed
BigDecimal runningQty  = BigDecimal.ZERO;
BigDecimal runningCost = BigDecimal.ZERO;  // total cost basis dollars held

for (Transaction tx : sortedByDateAsc) {
    if ("BUY".equals(tx.getTxType())) {
        // avgCost = (oldQty * oldAvgCost + buyQty * buyPrice) / (oldQty + buyQty)
        runningCost = runningCost.add(tx.getQuantity().multiply(tx.getPrice()));
        runningQty  = runningQty.add(tx.getQuantity());
    } else { // SELL
        // Cost of units sold = sellQty * currentAvgCost
        BigDecimal avgCostNow = runningQty.signum() == 0
            ? BigDecimal.ZERO
            : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
        BigDecimal costOfSold = tx.getQuantity().multiply(avgCostNow);
        runningCost = runningCost.subtract(costOfSold);
        runningQty  = runningQty.subtract(tx.getQuantity());
    }
    BigDecimal avgCostAtThisPoint = runningQty.signum() == 0
        ? BigDecimal.ZERO
        : runningCost.divide(runningQty, 6, RoundingMode.HALF_UP);
    // record avgCostAtThisPoint as the TransactionDto.runningCostBasis field
}
```

**Known seed pattern:** SeedRunner seeds one BUY at bar 50 and one partial SELL at bar 200 (~30% of position). The running cost basis after the SELL is identical to the BUY price (because SeedRunner stores `buyPrice` as `avgCostBasis` on the `Position` entity and the sell does not change the average cost of the remaining units when it is a proportional sell at a different price). Verify this in the unit test.

**Pitfall:** Do NOT use the SELL price as a basis-reduction amount. A sell removes qty units at the current average cost; the proceeds are separate from basis accounting. Mixing realized gain with basis reduction is a common mistake.

### Pattern 2: Equity Curve — Constant-Current-Holdings

**What:** For the `/pnl` endpoint, the equity curve is the portfolio's market value at each historical trading day computed using the **current** (final) holdings — not reconstructing the actual historical holdings from the transaction stream.

**Algorithm:**

```java
// positions: the current Position entities (qty, avgCostBasis)
// allBars: List<OhlcvBar> for all holding securities, ALL 504 days, sorted by (securityId, barDate)

// Step 1: Build a map: securityId → sorted list of (date, close)
Map<Long, NavigableMap<LocalDate, BigDecimal>> closesBySecId = groupClosesBySecurityId(allBars);

// Step 2: Find the common date range (intersection of all securities)
LocalDate firstDate = closesBySecId.values().stream()
    .map(m -> m.firstKey())
    .max(Comparator.naturalOrder())  // latest "first date" among all series
    .orElseThrow();
LocalDate lastDate = closesBySecId.values().stream()
    .map(m -> m.lastKey())
    .min(Comparator.naturalOrder())  // earliest "last date" — intersection
    .orElseThrow();

// Step 3: For each date in range, compute portfolio value
SortedSet<LocalDate> tradingDays = allDatesInRange(closesBySecId, firstDate, lastDate);
List<DateValueDto> curve = new ArrayList<>();
for (LocalDate date : tradingDays) {
    BigDecimal dayValue = BigDecimal.ZERO;
    for (Position pos : positions) {
        BigDecimal close = closesBySecId
            .get(pos.getSecurity().getId())
            .get(date);          // all positions share the same calendar — safe
        dayValue = dayValue.add(pos.getQuantity().multiply(close));
    }
    curve.add(new DateValueDto(date, dayValue.setScale(2, RoundingMode.HALF_UP)));
}
```

**Off-by-one caveat (critical):** The equity curve starts at `bar[0]` (the seed series start date: 2022-09-12, normalized to the first Monday). The first return is computed from `bar[1]` to `bar[0]`, which requires `bar[0]` to exist. Since all 16 securities share the exact same 504-day calendar (GbmGenerator's `nextTradingDay` advances identically for all), the intersection is the full 504-day range — no date-intersection complexity in practice.

**Model assumption documentation (required in code comment):**

```java
/**
 * Constant-current-holdings equity curve.
 *
 * <p>This curve values the portfolio's CURRENT (final) holdings across every
 * historical trading day in the seeded window, as if those shares were held
 * throughout. This is a dashboard equity curve — it shows how the current
 * portfolio WOULD have performed, not how it DID perform (the latter requires
 * reconstructing historical holdings from transactions, which is deferred).
 *
 * <p>Assumption: defensible for a demo dashboard. Limitation: overstates
 * performance if high-performing stocks were bought late. Must be labelled
 * in the UI with a tooltip: "Based on current holdings valued historically".
 */
```

### Pattern 3: Daily Portfolio Change

**What:** The "daily change" in the `/pnl` response — how much the portfolio moved today vs yesterday.

**Algorithm (correct):**

```java
BigDecimal latestValue   = curve.get(curve.size() - 1).value();
BigDecimal previousValue = curve.get(curve.size() - 2).value();
BigDecimal dailyChangeAbs = latestValue.subtract(previousValue);
BigDecimal dailyChangePct = dailyChangeAbs
    .divide(previousValue, 6, RoundingMode.HALF_UP);
// return as-is; the % can be multiplied by 100 at the DTO layer if preferred
```

**Pitfall:** Do NOT compute daily change as `(latestClose_perSecurity - previousClose_perSecurity)` summed independently — this loses cross-position netting and gives a different number than the equity curve. Always derive daily change from the equity curve itself.

### Pattern 4: Benchmark Rebasing (PORT-05)

**What:** Produce two time series (portfolio, benchmark) on a common scale so ECharts can overlay them. The standard approach is to rebase both to 100 at the same starting date.

**Algorithm (correct):**

```java
// portfolioSeries and benchmarkSeries are both sorted by date (same calendar)
// Use the FIRST shared trading day as the base date.

BigDecimal portfolioBase  = portfolioSeries.get(0).value();  // portfolio value on day 0
BigDecimal benchmarkBase  = benchmarkSeries.get(0).close();  // SPX500 close on day 0

List<BigDecimal> portfolioIdx = new ArrayList<>();
List<BigDecimal> benchmarkIdx = new ArrayList<>();

for (int i = 0; i < portfolioSeries.size(); i++) {
    portfolioIdx.add(
        portfolioSeries.get(i).value()
            .divide(portfolioBase, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(4, RoundingMode.HALF_UP)
    );
    benchmarkIdx.add(
        benchmarkSeries.get(i).close()
            .divide(benchmarkBase, 6, RoundingMode.HALF_UP)
            .multiply(new BigDecimal("100"))
            .setScale(4, RoundingMode.HALF_UP)
    );
}
// Both series now start at exactly 100.000 on day 0
```

**Why simple returns, not log returns, for rebasing:** The rebasing formula `price(t) / price(0) × 100` is equivalent to the cumulative simple return plus 100. This is exactly right for a chart overlay — it shows how a $100 investment in each would have grown. Log returns are NOT used here because: (a) they cannot be directly compared on a price chart, (b) Phase 3's ECharts y-axis is a price scale not a log-return scale, and (c) the CONTEXT.md explicitly asks for "rebased to a common base for same-chart comparison," which is the simple-price-normalized convention.

**Terminology precision:** The resulting series are "cumulative simple return indexed to 100," sometimes called "price-relative" or "normalized price" series. Document this in code.

### Pattern 5: Allocation Weights

**What:** Portfolio weight per position, and sector aggregation for the `/allocation` endpoint.

**Algorithm:**

```java
// Step 1: compute per-position market value
BigDecimal totalValue = BigDecimal.ZERO;
Map<String, BigDecimal> sectorValues = new LinkedHashMap<>();  // preserve insertion order

for (Position pos : positions) {
    BigDecimal close = latestCloseMap.get(pos.getSecurity().getId());
    BigDecimal mktValue = pos.getQuantity().multiply(close)
                             .setScale(2, RoundingMode.HALF_UP);
    totalValue = totalValue.add(mktValue);
    sectorValues.merge(pos.getSecurity().getSector(), mktValue, BigDecimal::add);
}

// Step 2: compute weight per sector
List<AllocationSliceDto> slices = new ArrayList<>();
BigDecimal weightSum = BigDecimal.ZERO;
List<Map.Entry<String, BigDecimal>> entries = new ArrayList<>(sectorValues.entrySet());
for (int i = 0; i < entries.size(); i++) {
    BigDecimal weight;
    if (i == entries.size() - 1) {
        // Last slice absorbs rounding residual to guarantee sum = 1.000000
        weight = BigDecimal.ONE.subtract(weightSum).setScale(6, RoundingMode.HALF_UP);
    } else {
        weight = entries.get(i).getValue()
                    .divide(totalValue, 6, RoundingMode.HALF_UP);
        weightSum = weightSum.add(weight);
    }
    slices.add(new AllocationSliceDto(entries.get(i).getKey(), weight,
                                      entries.get(i).getValue()));
}
```

**Pitfall (PITFALLS.md Pitfall 15):** Weights that sum to 99.9999% or 100.0001% look like a bug to any reviewer. The last-slice residual absorption is the standard fix. Assert in tests that the sum of all `AllocationSliceDto.weight()` fields equals exactly `1.000000` using `BigDecimal.compareTo`.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SQL: latest close per security | Subquery loop in Java | JPQL: `SELECT b FROM OhlcvBar b WHERE b.barDate = (SELECT MAX(b2.barDate) FROM OhlcvBar b2 WHERE b2.security = b.security)` or Spring Data derived query | The DB can do this in one pass; looping in Java causes N+1 |
| SQL: all closes for N securities | N separate queries (one per security) | `WHERE b.security.id IN :ids ORDER BY b.security.id ASC, b.barDate ASC` | One query, streaming JPA result |
| Pagination | Custom offset/limit | Spring Data `Pageable` parameter + `Page<T>` return | Already in the stack; `Page` carries totalElements, totalPages for the frontend |
| Current user → Portfolio resolution | Re-implementing the pattern | Copy the `AuthController.me()` pattern: `Authentication` parameter → `appUserRepository.findByUsername(name)` → `portfolioRepository.findByUserId(id)` | Phase 1 established this pattern; no new boilerplate needed |
| Transaction sort order | Manual sorting in Java | `ORDER BY tx_date DESC, id DESC` in the JPQL / derived query | Stable sort; `id DESC` breaks ties on same-day transactions |
| Allocation sum-to-100% fix | Rounding all slices identically | Last-slice residual absorption (shown above) | Provably sums to exactly 1 — no floating-point noise possible |

---

## N+1 Query Prevention

This is the primary performance concern for Phase 2. With 5–7 positions per portfolio and 504 OHLCV bars per security, naive JPA loading causes:
- N queries for securities (one per position via `position.getSecurity()` lazy load)
- N × 504 queries for OHLCV bars

**Prevention strategy:**

### Holdings endpoint

```java
// In PositionRepository — add this derived query:
@Query("SELECT p FROM Position p JOIN FETCH p.security WHERE p.portfolio.id = :portfolioId")
List<Position> findByPortfolioIdWithSecurity(@Param("portfolioId") Long portfolioId);

// In OhlcvBarRepository — add this JPQL for latest close per security:
@Query("""
    SELECT b FROM OhlcvBar b
    WHERE b.security.id IN :securityIds
      AND b.barDate = (
          SELECT MAX(b2.barDate) FROM OhlcvBar b2
          WHERE b2.security.id = b.security.id
      )
    """)
List<OhlcvBar> findLatestBarBySecurityIds(@Param("securityIds") List<Long> securityIds);
```

### Equity curve + benchmark endpoints

```java
// In OhlcvBarRepository — add this for bulk close series:
@Query("""
    SELECT b FROM OhlcvBar b
    WHERE b.security.id IN :securityIds
    ORDER BY b.security.id ASC, b.barDate ASC
    """)
List<OhlcvBar> findAllBySecurityIdsOrdered(@Param("securityIds") List<Long> securityIds);
```

This returns 504 × N rows in a single JDBC round-trip. At 504 × 7 positions = 3528 rows, this is well within in-memory comfort.

### Transactions endpoint

```java
// In TransactionRepository — replace the bare JpaRepository with:
@Query("""
    SELECT t FROM Transaction t
    JOIN FETCH t.security
    WHERE t.portfolio.id = :portfolioId
    ORDER BY t.txDate DESC, t.id DESC
    """)
Page<Transaction> findByPortfolioIdWithSecurity(
    @Param("portfolioId") Long portfolioId,
    Pageable pageable);
```

Spring Data `Pageable` + `@Query` with `JOIN FETCH` works correctly in Spring Data JPA when the query does not use a collection join (Transaction → Security is a single-valued association — safe for pagination). If it were a collection join, a `CountQuery` override would be needed.

---

## Spring REST Patterns

### Pattern: Resolving Current User in a Controller

From Phase 1's `AuthController.me()` — the established pattern:

```java
// Source: Phase 1 AuthController.java (Phase 1 SUMMARY 01-03)
@GetMapping("/holdings")
@Transactional(readOnly = true)
public List<HoldingDto> getHoldings(Authentication authentication) {
    String username = authentication.getName();
    AppUser user = appUserRepository.findByUsername(username)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    List<Portfolio> portfolios = portfolioRepository.findByUserId(user.getId());
    if (portfolios.isEmpty()) {
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No portfolio found");
    }
    return portfolioService.getHoldings(portfolios.get(0).getId());
}
```

**Alternative:** extract user → portfolio resolution into a private helper method on the controller (or a thin `PortfolioPrincipalResolver` component) to avoid repeating the same 5-line block across 5 endpoints. The resolver pattern is cleaner but either way is correct.

### Pattern: Pageable for Transactions

```java
// Controller — CONTEXT.md locks ?page=&size= query params
@GetMapping("/transactions")
@Transactional(readOnly = true)
public Page<TransactionDto> getTransactions(
        Authentication authentication,
        @PageableDefault(size = 20, sort = "txDate", direction = Sort.Direction.DESC)
        Pageable pageable) {
    // ... resolve portfolio, then:
    return portfolioService.getTransactions(portfolioId, pageable);
}
```

Spring automatically binds `?page=0&size=20&sort=txDate,desc` to a `Pageable` object. The `@PageableDefault` annotation sets sensible defaults when the query params are absent. The controller returns `Page<TransactionDto>` directly — Jackson serializes it as a JSON object with `content`, `totalElements`, `totalPages`, `number`, `size` fields.

### Pattern: @Transactional(readOnly=true) Scope

`@Transactional(readOnly=true)` must be on the **service methods**, not just the controller. The controller delegates to the service, which loads all JPA entities inside the same transaction. Without the annotation on the service, Hibernate's second-level caching and dirty-checking optimizations are not applied. The annotation on the controller method is redundant but harmless if present.

### Pattern: Module Boundary for portfolio.api

The `com.quantlens.portfolio.api` package is inside the `portfolio` module. It is NOT a named interface — it is consumed only by HTTP clients (Vue), not by other Spring Modulith modules. Therefore no `@NamedInterface` annotation is needed on `api/package-info.java`. The `portfolio` module already has `allowedDependencies = {"marketdata::domain"}` from Phase 1.

**If a future module (e.g., the `ai` module in Phase 6) needs to call `PortfolioService` directly:** add `@NamedInterface("service")` to a new `portfolio/service/package-info.java` at that time. Do not add it now — YAGNI.

---

## DTO Shapes (Chart-Ready)

### HoldingDto

```java
public record HoldingDto(
    String ticker,
    String name,
    String sector,
    BigDecimal quantity,                  // NUMERIC(18,4) — scale 4
    BigDecimal avgCostBasis,              // NUMERIC(18,6) — scale 6
    BigDecimal currentPrice,              // latest close — scale 6
    BigDecimal currentMarketValue,        // qty × currentPrice — scale 2
    BigDecimal portfolioWeight,           // 0.000000–1.000000 — scale 6
    BigDecimal unrealizedPnlAbs,          // (price − cost) × qty — scale 2
    BigDecimal unrealizedPnlPct           // unrealizedPnlAbs / (avgCost × qty) — scale 6
) {}
```

### PortfolioPnlDto

```java
public record DateValueDto(LocalDate date, BigDecimal value) {}  // nested

public record PortfolioPnlDto(
    BigDecimal totalMarketValue,          // scale 2
    BigDecimal totalCostBasis,            // Σ(qty × avgCost) — scale 2
    BigDecimal totalUnrealizedGainAbs,    // totalMarketValue − totalCostBasis — scale 2
    BigDecimal totalUnrealizedGainPct,    // totalUnrealizedGainAbs / totalCostBasis — scale 6
    BigDecimal dailyChangeAbs,            // latestCurve − previousCurve — scale 2
    BigDecimal dailyChangePct,            // dailyChangeAbs / previousCurve — scale 6
    List<DateValueDto> equityCurve        // 504 entries, date + portfolioValue
) {}
```

`LocalDate` serializes as a JSON string `"2022-09-12"` via Jackson's JavaTimeModule (already registered by Spring Boot's auto-configuration). No custom serializer needed.

### AllocationSliceDto

```java
public record AllocationSliceDto(
    String label,          // sector name (e.g. "Technology")
    BigDecimal weight,     // 0.000000–1.000000
    BigDecimal marketValue // absolute $ value in this sector — scale 2
) {}
```

Phase 3 ECharts pie chart expects `{name, value}` pairs. The planner can note that `label` maps to ECharts `name` and `weight` or `marketValue` maps to `value` depending on whether the chart is weighted by percentage or by dollar amount. Both fields are provided so Phase 3 can choose.

### TransactionDto

```java
public record TransactionDto(
    LocalDate txDate,
    String txType,             // "BUY" or "SELL"
    String ticker,
    BigDecimal quantity,       // scale 4
    BigDecimal price,          // scale 6
    BigDecimal tradeValue,     // qty × price — scale 2 (Phase 3 convenience)
    BigDecimal runningCostBasis // avg cost per share at this point in time — scale 6
) {}
```

### BenchmarkComparisonDto

```java
public record BenchmarkComparisonDto(
    List<String> dates,              // ISO-8601 date strings ["2022-09-12", ...]
    List<BigDecimal> portfolioSeries,// indexed to 100 on day 0 — scale 4
    List<BigDecimal> benchmarkSeries // indexed to 100 on day 0 — scale 4
) {}
```

ECharts line chart with two series uses parallel arrays for dates and values — this shape binds directly.

---

## Common Pitfalls

### Pitfall 1: N+1 on Position → Security → OhlcvBar

**What goes wrong:** `PositionRepository.findAll()` returns `Position` entities with a lazy `Security` association. Iterating positions and calling `position.getSecurity().getTicker()` triggers one SQL per position. Then looking up OHLCV data per security triggers another N queries.

**Why it happens:** Hibernate lazy loading is the JPA default and looks like normal Java.

**How to avoid:** Use `JOIN FETCH` in all repository queries that cross the position → security boundary (shown in the N+1 section above). Verify with `spring.jpa.show-sql=true` in test profile and count query logs.

**Warning signs:** Test execution time > 5 seconds for a 7-position portfolio; visible `SELECT * FROM securities WHERE id=?` repeated in SQL logs.

### Pitfall 2: BigDecimal.divide() Without Scale and RoundingMode

**What goes wrong:**

```java
// WRONG — throws ArithmeticException for non-terminating decimals
BigDecimal weight = positionValue.divide(totalValue);

// CORRECT
BigDecimal weight = positionValue.divide(totalValue, 6, RoundingMode.HALF_UP);
```

`BigDecimal.divide(BigDecimal)` (no-arg) throws `ArithmeticException: Non-terminating decimal expansion` for any division that does not terminate in decimal (e.g., 1/3). Portfolio weights frequently hit this.

**How to avoid:** All `divide()` calls MUST specify scale and rounding mode. Zero exceptions here.

### Pitfall 3: Off-by-One in Equity Curve / Daily Change

**What goes wrong:** Using `curve.get(curve.size() - 1)` as "today" and `curve.get(0)` as "yesterday" to compute daily change — this gives the change over the full historical window, not one day.

**How to avoid:** Daily change = `curve[last] - curve[last - 1]`. The equity curve is sorted ascending by date. The last two entries are the most recent two trading days. Assert in the unit test that the daily change matches `latestPortfolioValue - previousDayPortfolioValue` explicitly computed.

### Pitfall 4: Benchmark Dates Out of Sync with Portfolio Dates

**What goes wrong:** The SPX500 security exists in the `ohlcv_bars` table with the same 504-day calendar as equity holdings (all generated by the same `GbmGenerator` with the same `nextTradingDay` sequence). However, if the JPQL query returns results in DB insertion order rather than `ORDER BY barDate ASC`, the parallel arrays `portfolioSeries` and `benchmarkSeries` will be misaligned.

**How to avoid:** Always `ORDER BY b.barDate ASC` in the OHLCV bulk query. Assert in the benchmark test that `dates[0]` equals the expected series start date (`2022-09-12`) and that both series arrays have the same length.

### Pitfall 5: Running Cost Basis Computed on Unsorted Transactions

**What goes wrong:** Transactions are not guaranteed to be in chronological order when fetched from the DB without an explicit `ORDER BY`. Computing running average cost on an unordered list produces different (wrong) results depending on DB row order.

**How to avoid:** Sort by `txDate ASC, id ASC` before computing running cost basis. Use `id ASC` as the tiebreaker for same-date transactions (consistent with the insertion order in SeedRunner, which inserts BUY before SELL).

### Pitfall 6: Returning Page<Entity> Instead of Page<DTO>

**What goes wrong:** Returning `Page<Transaction>` from the service/controller causes Jackson to serialize the JPA entity, which may: (a) trigger lazy loads for the `security` and `portfolio` associations, causing N+1 or `LazyInitializationException`; (b) expose internal fields like `passwordHash` if entity relationships chain back to `AppUser`.

**How to avoid:** Always return `Page<TransactionDto>` — convert entities to DTOs inside the service method: `transactionPage.map(tx -> toTransactionDto(tx, runningCostBasisMap))`.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit 5 + Spring Boot Test (already wired in Phase 1) |
| Integration base class | `AbstractPostgresIntegrationTest` (Testcontainers pgvector/pg16, static-init container) |
| Quick run command | `./mvnw test -Dtest=PortfolioServiceTest` |
| Full suite command | `./mvnw test` |

### Phase Requirements → Test Map

| Req ID | Behavior | Test Type | Automated Command |
|--------|----------|-----------|-------------------|
| PORT-01 | Holdings list for alice: 5 positions, correct ticker/sector/qty/cost/price/pnl/weight | Integration | `PortfolioControllerIntegrationTest#getHoldings_alice_returns5Holdings` |
| PORT-01 | Weight sum equals 1.000000 | Unit | `PortfolioServiceTest#allocationWeightsSumToOne` |
| PORT-01 | Unrealized P&L = (close − avgCost) × qty | Unit | `PortfolioServiceTest#unrealizedPnlFormula` |
| PORT-02 | Equity curve has 504 entries for alice | Integration | `PortfolioControllerIntegrationTest#getPnl_aliceEquityCurve504Entries` |
| PORT-02 | First equity curve entry date = 2022-09-12 | Integration | `PortfolioControllerIntegrationTest#getPnl_equityCurveStartDate` |
| PORT-02 | Daily change = equityCurve[last] − equityCurve[last-1] | Unit | `PortfolioServiceTest#dailyChangeDerivesFromEquityCurve` |
| PORT-02 | Total unrealized gain = totalMarketValue − totalCostBasis | Unit | `PortfolioServiceTest#totalUnrealizedGainFormula` |
| PORT-03 | Allocation slices cover all sectors in portfolio | Integration | `PortfolioControllerIntegrationTest#getAllocation_aliceSectorsPresent` |
| PORT-03 | Weight sum of all slices = 1.000000 (BigDecimal.compareTo) | Integration | `PortfolioControllerIntegrationTest#getAllocation_weightSumsToOne` |
| PORT-04 | Transactions page 0 returns most-recent-first | Integration | `PortfolioControllerIntegrationTest#getTransactions_mostRecentFirst` |
| PORT-04 | runningCostBasis after BUY = buyPrice | Unit | `PortfolioServiceTest#runningCostBasisAfterBuy` |
| PORT-04 | runningCostBasis after SELL does not change if proportional at same price | Unit | `PortfolioServiceTest#runningCostBasisAfterProportionalSell` |
| PORT-04 | Pagination: page 1 has different transactions than page 0 | Integration | `PortfolioControllerIntegrationTest#getTransactions_paginationWorks` |
| PORT-05 | Both benchmark series start at 100 on day 0 | Unit | `PortfolioServiceTest#benchmarkBothSeriesStartAt100` |
| PORT-05 | portfolioSeries and benchmarkSeries are same length | Integration | `PortfolioControllerIntegrationTest#getBenchmark_seriesSameLength` |
| PORT-05 | Benchmark response dates array sorted ascending | Integration | `PortfolioControllerIntegrationTest#getBenchmark_datesSortedAscending` |

### Golden Values from Seed (seed=42, SERIES_START=2022-09-12)

The seed is fully deterministic. The planner must include a task to compute these values offline (run `SeedRunner` in isolation or query the test DB after Phase 1's seeding) before writing assertions. Key quantities to compute:

| Quantity | How to Derive | Test Use |
|----------|---------------|----------|
| Alice's AAPL position quantity | SeedRunner: 50 shares − floor(50×0.3)=15 sold = 35.0000 shares | Assert in holdings response |
| Alice's AAPL avg cost basis | SeedRunner: buyPrice = AAPL.close[bar50] from GbmGenerator(seed=42) | Assert avgCostBasis exactly |
| Alice's portfolio total market value | Σ qty_i × latestClose_i (latest = bar503) | Assert totalMarketValue within ±0.01 |
| SPX500 close[0] | GbmGenerator: spec startPrice=100.0, bar0 close | Assert benchmarkSeries[0]=100.0000 |
| Number of transactions (alice) | 5 holdings × 2 transactions (BUY+SELL) = 10 total | Assert total transaction count |

A recommended approach: add a `@Test @Disabled("Golden value computation — run once to print values")` test that queries the seeded DB and prints all relevant numbers. Record them in the test class as constants. Then enable the golden-value tests with those constants.

### Wave 0 Gaps (files that must be created before implementation tasks begin)

- `backend/src/test/java/com/quantlens/portfolio/PortfolioServiceTest.java` — unit tests for pure computation methods (no Spring context needed; use `@ExtendWith(MockitoExtension.class)` or plain JUnit 5)
- `backend/src/test/java/com/quantlens/portfolio/PortfolioControllerIntegrationTest.java` — `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `AbstractPostgresIntegrationTest` base; uses `TestRestTemplate` to call endpoints authenticated as alice

### Sampling Rate

- **Per task commit:** `./mvnw test -Dtest=PortfolioServiceTest`
- **Per wave merge:** `./mvnw test`
- **Phase gate:** Full suite green before `/gsd:verify-work`

---

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | yes (all endpoints require auth) | Spring Security session — already wired in Phase 1 |
| V3 Session Management | yes (session scopes portfolio) | `SessionCreationPolicy.ALWAYS` + `changeSessionId()` — Phase 1 |
| V4 Access Control | yes (portfolio scoped to authenticated user; must not return another user's portfolio) | Resolve portfolio from `authentication.getName()` only; never accept `portfolioId` from request |
| V5 Input Validation | yes (page/size params) | `@PageableDefault` + Spring's `Pageable` binding prevents arbitrary offsets; cap `size` to 100 via `PageableHandlerMethodArgumentResolverCustomizer` or validation in service |
| V6 Cryptography | no | No new crypto in this phase |

### Known Threat Patterns for this Stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Insecure direct object reference: authenticated user requests another user's portfolio | Elevation of Privilege | Never accept portfolio ID from URL/request params; always derive portfolio from `Authentication` principal |
| Pageable abuse: `?size=10000` | DoS / Information Disclosure | Cap page size: add `spring.data.web.pageable.max-page-size=100` in `application.yml` or a `PageableHandlerMethodArgumentResolverCustomizer` bean |
| JSON mass exposure of Position entity | Information Disclosure | Return DTOs, never JPA entities; no `@JsonIgnore` gymnastics needed if entities are never exposed |
| LazyInitializationException leaking to HTTP response | Availability / Information Disclosure | All JPA loads inside `@Transactional(readOnly=true)` scope; DTOs assembled before transaction closes |

**Insecure direct object reference is the most important control in this phase.** Every endpoint must derive the portfolio ID from the authenticated principal via the established Phase 1 chain (`Authentication` → `AppUser` → `Portfolio`). Any endpoint that accepts `portfolioId` as a request parameter or path variable and does NOT re-validate ownership is a vulnerability, even in a demo app with 3 users.

---

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| Returning `double` for financial values | `BigDecimal` throughout monetary paths | Well-established — Spring Boot doesn't force this; must be enforced per project policy | Prevents rounding artifacts in P&L display |
| Exposing JPA entities as REST responses | Typed record DTOs | Spring 6 / Java 16+ records | Eliminates LazyInitializationException leakage; clean separation of concerns |
| `@RequestParam Long portfolioId` in portfolio endpoints | Principal-derived portfolio ID from `Authentication` | Best practice; enforced by CONTEXT.md | Eliminates IDOR vulnerability |
| Pagination via custom `offset`/`limit` | Spring Data `Pageable` with `@PageableDefault` | Spring Data 2.x+ | Standardized; ECharts-compatible page metadata included automatically |

---

## Environment Availability

All dependencies available. No external services needed.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 21 | Maven build | Yes | Temurin 21.0.11 at C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot | — |
| Docker | Integration tests (Testcontainers) | Yes | Docker 28.0.4 | — |
| Spring Boot 3.5.13 | All | Yes | In pom.xml | — |
| Spring Data JPA | Repository queries | Yes | Boot-managed | — |
| Testcontainers pgvector/pg16 | Integration tests | Yes | Already wired in AbstractPostgresIntegrationTest | — |
| Hipparchus (hipparchus-core, hipparchus-stat) | Already in pom.xml | Yes | 4.0.3 | Not needed for Phase 2 math |

**No missing dependencies.** Phase 2 requires no new Maven artifacts.

---

## Open Questions

1. **Should `PortfolioService` be extracted behind a named interface for the `ai` module?**
   - What we know: The `ai` module (Phase 6) will call `PortfolioService.getHoldings()` to narrate positions. Spring Modulith requires a `@NamedInterface` for cross-module calls.
   - What's unclear: Whether to pre-emptively add `@NamedInterface("service")` in Phase 2 or add it in Phase 6.
   - Recommendation: Defer to Phase 6. Adding it now without a consumer creates unnecessary structure. The CONTEXT.md's Deferred section confirms AI narration is out of scope for Phase 2.

2. **Correct handling of zero-quantity positions after sells?**
   - What we know: SeedRunner ensures `finalQty > 0` for all positions (30% sell of minimum 1 share leaves at least 70% > 0). So no position has zero quantity in the seed.
   - What's unclear: Whether to guard against zero-quantity positions in `getHoldings()`.
   - Recommendation: Add a guard (`position.getQuantity().signum() > 0`) defensively, but it will not trigger against the seed.

3. **Page size cap mechanism?**
   - What we know: `spring.data.web.pageable.max-page-size` in `application.yml` works globally for all `Pageable`-using endpoints.
   - What's unclear: Whether a global 100-element cap is appropriate or should be per-endpoint.
   - Recommendation: Set `spring.data.web.pageable.max-page-size=500` in `application.yml` (generous for transactions) and add `@PageableDefault(size=20)` on the controller method.

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Alice has exactly 5 positions (AAPL, MSFT, NVDA, AMZN, TSLA) with quantities derived from `50/20/30/40/15` minus 30% sell floor | Golden Values | Test assertions on position count / quantity would fail |
| A2 | All 16 securities share exactly the same 504-day trading calendar (no gaps between securities) | Equity Curve math | Date-intersection logic could produce fewer dates; equity curve length assertions would fail |
| A3 | The SPX500 security is the only `isBenchmark=true` record; `SecurityRepository.findByBenchmarkTrue()` returns exactly one | Benchmark section | Multiple benchmark securities would cause ambiguous benchmark series selection |
| A4 | `LocalDate` Jackson serialization works as `"2022-09-12"` string without additional config | DTO Shapes | Dates would serialize as numeric array `[2022, 9, 12]` (Jackson default without JavaTimeModule) — Spring Boot auto-configures JavaTimeModule, so this is likely safe, but must be verified in the integration test |

**Note on A4:** Spring Boot 3.x auto-configures `Jackson2ObjectMapperBuilderCustomizerAutoConfiguration` which registers `JavaTimeModule` and sets `WRITE_DATES_AS_TIMESTAMPS = false`. This means `LocalDate` serializes as `"2022-09-12"`. This is the correct behavior and is Spring Boot's default. [ASSUMED] based on training knowledge — verify in the first integration test by asserting `dates[0]` is a string `"2022-09-12"` not `[2022,9,12]`.

---

## Sources

### Primary (HIGH confidence)

- Phase 1 SUMMARY 01-02 (GbmGenerator source code, seed parameters, SERIES_START, security specs, portfolio positions) — `[VERIFIED: codebase]`
- Phase 1 SUMMARY 01-03 (AuthController pattern for principal resolution) — `[VERIFIED: codebase]`
- Phase 1 SUMMARY 01-04 (AbstractPostgresIntegrationTest, TestRestTemplate pattern) — `[VERIFIED: codebase]`
- CONTEXT.md 02 (all locked computation rules, DTO names, module boundaries) — `[VERIFIED: codebase]`
- Spring Data JPA `@Query` / `JOIN FETCH` / `Pageable` — `[ASSUMED]` standard Spring Data 3.x patterns; confirmed as unchanged from Spring Data 2.x in all known Spring Boot 3.x documentation
- `BigDecimal.divide(divisor, scale, RoundingMode)` API — `[VERIFIED: Java 21 standard library]`

### Secondary (MEDIUM confidence)

- Average-cost accounting method (running basis) — GAAP standard; textbook-grade accounting
- Benchmark rebasing to common index 100 — standard financial charting convention; described in any introductory finance text and verified against Bloomberg/Yahoo Finance chart methodology

### Tertiary (LOW confidence)

- `spring.data.web.pageable.max-page-size` property name — `[ASSUMED]`; should be verified against `spring.data.web.pageable.*` property documentation before use

---

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new dependencies; Phase 1 codebase fully known
- Architecture: HIGH — module boundaries, principal resolution, query patterns all established in Phase 1
- Financial math: HIGH — average-cost, constant-holdings curve, benchmark rebasing are textbook formulas; correctness verified by reasoning; golden values must be computed from seed at implementation time
- Pitfalls: HIGH — N+1, BigDecimal.divide(), off-by-one are documented and concrete

**Research date:** 2026-06-07
**Valid until:** This research is tied to the Phase 1 codebase state. Valid until Phase 1 entities/repos are modified (they are locked per Phase 1 SUMMARY — stable indefinitely).
