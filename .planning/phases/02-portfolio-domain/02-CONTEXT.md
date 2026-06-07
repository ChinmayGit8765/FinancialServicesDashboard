# Phase 2: Portfolio Domain - Context

**Gathered:** 2026-06-07
**Status:** Ready for planning
**Mode:** Smart discuss (autonomous) — recommended answers auto-accepted per "use all recommended"

<domain>
## Phase Boundary

Deliver the backend REST API and computations that expose a demo user's complete portfolio from the Phase 1 seeded data: holdings with P&L, portfolio-level P&L time-series, allocation breakdown, paginated transaction history, and benchmark comparison. All values are computed server-side from the seeded OHLCV/securities/portfolio data and returned as typed, chart-ready JSON DTOs.

**In scope:** `PortfolioService` + REST controllers + DTOs in the `com.quantlens.portfolio` module; computation of current value, weights, unrealized P&L, daily change, equity/P&L curve, sector/asset-class allocation, running cost basis, and portfolio-vs-benchmark return series; integration + unit tests.

**Out of scope (Phase 3):** the Vue views, ECharts visualizations, and any frontend wiring. Phase 2's "user can view" criteria are satisfied at the API level (data is retrievable and correct); Phase 3 renders it. **No UI-SPEC for this phase.**

Requirements covered: PORT-01, PORT-02, PORT-03, PORT-04, PORT-05.
</domain>

<decisions>
## Implementation Decisions

### API Surface
- Session-scoped REST under `/api/portfolio` — the portfolio is derived from the authenticated principal (reuse the SecurityContext pattern from Phase 1's AuthController), NOT a portfolioId in the path. All endpoints require authentication.
- Endpoints:
  - `GET /api/portfolio/holdings` → list of positions, each with ticker, name, sector, quantity, avg cost basis, current price, current market value, portfolio weight, unrealized P&L (absolute + %).
  - `GET /api/portfolio/pnl` → portfolio-level P&L: total market value, total unrealized gain/loss (abs + %), daily change (abs + %), and a time-series equity curve over the seeded date window.
  - `GET /api/portfolio/allocation` → breakdown by sector (primary) and asset class, each with weight and market value (chart-ready for pie/treemap).
  - `GET /api/portfolio/transactions?page=&size=` → paginated buy/sell log (date, side, ticker, quantity, price, running cost basis), most-recent first.
  - `GET /api/portfolio/benchmark` → portfolio cumulative return vs the seeded S&P 500 proxy (the `benchmark=true` security) over the same window, both rebased to a common base (e.g. 100 or portfolio start value) for same-chart comparison.

### Computation
- `PortfolioService` (in `com.quantlens.portfolio`) owns all computation. It reads market data via the `marketdata.domain` named interface (OHLCV + securities) and the portfolio repositories.
- Current price = latest OHLCV close per security. Unrealized P&L = (currentClose − avgCostBasis) × quantity. Weight = positionMarketValue / totalPortfolioMarketValue.
- Daily change = (latest close − previous close) aggregated across holdings (abs + %).
- Equity/P&L curve: value the **current holdings** across each historical trading day in the seeded window (constant-current-holdings valuation — a clean, defensible dashboard equity curve). Document this assumption in code + the model rationale.
- Running cost basis in the transaction list = cumulative average cost as buys/sells are applied chronologically.
- Benchmark series = SPX500 proxy daily closes converted to cumulative return and rebased to match the portfolio curve's base for overlay.
- All monetary math uses `BigDecimal` with a consistent scale (6 for ratios/prices, 2 for display money where appropriate); returns/percentages as `BigDecimal` or double-for-display per DTO field (prefer BigDecimal serialized as JSON number).

### Contracts (DTOs)
- Typed Java `record` DTOs per endpoint (e.g. `HoldingDto`, `PortfolioPnlDto`, `AllocationSliceDto`, `TransactionDto`, `BenchmarkComparisonDto`), serialized by Jackson. Shapes are chart-ready so Phase 3 ECharts can bind directly (arrays of {date,value} for series, {label,weight,value} for allocation).
- Pagination uses Spring Data `Pageable`/`Page` (or a thin custom page wrapper) for transactions.

### Module / Architecture
- New code lives in `com.quantlens.portfolio` (service + `api` subpackage for controllers/DTOs). Respect Spring Modulith boundaries — cross-module reads go through `marketdata.domain` named interface (already established in Phase 1). Add a `@NamedInterface` if the portfolio domain needs to expose types to controllers.

### Testing
- Integration tests (Testcontainers, pgvector image, reuse `AbstractPostgresIntegrationTest`) hitting each endpoint authenticated as a seeded persona, asserting response shape and key computed values against the deterministic seed (seed=42 → stable golden numbers; assert with tolerance where floating).
- Unit tests for the pure computation methods (P&L, weights, daily change, allocation, running cost basis, rebased benchmark) with hand-checked inputs.

### Claude's Discretion
- Exact DTO field names/shapes, whether benchmark is folded into the pnl endpoint or separate, the precise rebasing base, and pagination wrapper shape are at Claude's discretion within the above.
</decisions>

<code_context>
## Existing Code Insights

### Reusable Assets (from Phase 1)
- JPA entities + repositories: `Security`, `OhlcvBar`, `FactorReturn` (marketdata.domain, `@NamedInterface`); `AppUser`, `Portfolio`, `Position`, `Transaction` (portfolio.domain). `Security.isBenchmark()` flags the SPX500 proxy.
- Spring Security with session auth; `AuthController.me()` shows how to resolve the current user → portfolioId from the SecurityContext.
- `AbstractPostgresIntegrationTest` (Testcontainers pgvector base) for integration tests.
- Deterministic seed (seed=42): 16 securities, ~504 OHLCV bars each, 3 personas with portfolios + positions + transactions, 504 factor rows.

### Established Patterns
- BigDecimal/NUMERIC money, `LocalDate` dates, Spring Modulith package boundaries, Flyway migrations (V1–V3 exist), `@Transactional(readOnly=true)` for read endpoints, Jackson for JSON.

### Integration Points
- Controllers under `com.quantlens.portfolio.api`; consumed by Phase 3 Vue views via the existing Vite `/api` proxy + session cookie/CSRF already wired in Phase 1's frontend shell.
</code_context>

<specifics>
## Specific Ideas

- Values must be correct and stable against the seed (this is the quant-credibility foundation the AI layer will later narrate) — golden-value tests required.
- Keep endpoints chart-ready so Phase 3 binds with minimal transformation.
- Read-only endpoints; no mutation of portfolio data in this phase.
</specifics>

<deferred>
## Deferred Ideas

- Vue views / ECharts rendering of this data (Phase 3).
- Risk metrics (Sharpe/VaR/beta/correlation), Monte Carlo, factor attribution, arbitrage (Phases 4–5).
- AI narration of the portfolio (Phases 6+).
- Realized P&L / tax-lot accounting beyond running cost basis.
</deferred>
