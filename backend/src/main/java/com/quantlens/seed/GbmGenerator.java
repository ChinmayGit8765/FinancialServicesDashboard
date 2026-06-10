package com.quantlens.seed;

import org.hipparchus.random.MersenneTwister;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Correlated Geometric Brownian Motion (GBM) price-series generator.
 *
 * <h2>GBM discretization (Ito-corrected)</h2>
 * <pre>
 *   S(t+dt) = S(t) * exp( (mu - sigma^2 / 2) * dt + sigma * sqrt(dt) * Z )
 * </pre>
 * The {@code sigma^2/2} Ito-correction term MUST be present. Omitting it
 * causes simulated paths to drift upward by {@code sigma^2/2} per year,
 * violating the golden property {@code E[S(T)] ≈ S(0) * exp(mu * T)}.
 *
 * <h2>Correlation model</h2>
 * Each security's innovation is a weighted mix of a shared market factor
 * and a per-security idiosyncratic component:
 * <pre>
 *   Z_i = beta_i * Z_mkt + sqrt(1 - beta_i^2) * Z_idio_i
 * </pre>
 * where all Z values are N(0,1) draws from {@link MersenneTwister#nextGaussian()}
 * using a single generator seeded with the fixed value {@code 42}.
 *
 * <h2>BigDecimal safety</h2>
 * All double values from the GBM loop are converted with
 * {@code BigDecimal.valueOf(d).setScale(6, RoundingMode.HALF_UP)}.
 * {@code new BigDecimal(double)} is NEVER used — it would capture binary
 * double rounding artefacts into the stored NUMERIC values.
 *
 * <h2>Thread safety</h2>
 * This component is stateless: {@link #generateOhlcv} returns an
 * {@link OhlcvResult} that bundles the OHLCV rows <em>and</em> the market
 * excess-return series.  Callers pass that series directly into
 * {@link #generateFactors} — no shared mutable state.
 */
@Component
public class GbmGenerator {

    /** Fixed RNG seed — guarantees identical prices on every cold start. */
    private static final long RNG_SEED = 42L;

    /** Number of simulated trading days (~2 years). */
    public static final int TRADING_DAYS = 504;

    /** dt = 1 trading day / 252 trading days per year. */
    private static final double DT = 1.0 / 252.0;

    /** Annualized market drift (long-run equity premium). */
    private static final double MU_MKT = 0.07;

    /** Annualized market volatility (typical S&P 500 realized vol). */
    private static final double SIGMA_MKT = 0.18;

    /** Risk-free rate (annualized). */
    private static final double RF_ANNUAL = 0.04;

    // ── public record types ───────────────────────────────────────────────────

    /**
     * Specification for a single security.
     *
     * @param ticker     Exchange ticker (e.g. "AAPL")
     * @param startPrice Starting price (approximate mid-2024 level for recognizability)
     * @param mu         Annualized drift
     * @param sigma      Annualized total volatility
     * @param beta       Market beta (0.6–1.4); drives correlation with market factor.
     *                   Values outside [0,1] are clamped for the correlation formula.
     */
    public record SecuritySpec(String ticker, double startPrice,
                               double mu, double sigma, double beta) {}

    /**
     * One row of generated OHLCV data.
     *
     * @param date   Trading date
     * @param open   Open price (BigDecimal, NUMERIC(18,6))
     * @param high   High price
     * @param low    Low price
     * @param close  Close price
     * @param volume Simulated daily volume
     */
    public record OhlcvRow(LocalDate date, BigDecimal open, BigDecimal high,
                           BigDecimal low, BigDecimal close, long volume) {}

    /**
     * One row of synthetic Fama-French factor returns.
     *
     * @param date   Factor date
     * @param mktRf  Market excess return (Mkt - Rf)
     * @param smb    Small-minus-big (synthetic)
     * @param hml    High-minus-low (synthetic)
     * @param rf     Risk-free rate (daily fraction)
     */
    public record FactorRow(LocalDate date, BigDecimal mktRf,
                            BigDecimal smb, BigDecimal hml, BigDecimal rf) {}

    /**
     * Result of {@link #generateOhlcv}: the per-security OHLCV row lists plus the
     * market excess-return series needed by {@link #generateFactors}.
     * <p>
     * Carrying the market returns here (instead of storing them as an instance field)
     * keeps this component stateless and safe for concurrent callers.
     *
     * @param rows              per-security OHLCV rows in the same order as the input specs
     * @param mktExcessReturns  daily market excess returns (mkt gross return minus rf) for
     *                          each simulated trading day; pass directly to
     *                          {@link #generateFactors(double[], LocalDate)}
     */
    public record OhlcvResult(List<List<OhlcvRow>> rows, double[] mktExcessReturns) {}

    // ── generation ────────────────────────────────────────────────────────────

    /**
     * Generate OHLCV series for all given securities.
     *
     * <p>All N(0,1) draws come from {@code MersenneTwister(42).nextGaussian()} in a
     * deterministic order: market factor draws first (days 0..TRADING_DAYS-1), then
     * per-security idiosyncratic draws in spec-list order.  This guarantees
     * byte-identical output across two invocations with the same spec list.
     *
     * @param specs     ordered list of security specifications
     * @param startDate the first trading date in the generated series
     * @return {@link OhlcvResult} containing per-security OHLCV rows and market excess returns
     */
    public OhlcvResult generateOhlcv(List<SecuritySpec> specs, LocalDate startDate) {
        // Normalize startDate to a trading day — if a weekend is passed, advance to Monday (WR-05).
        // This prevents bar[0] from carrying a weekend date, which would violate the
        // UNIQUE (security_id, bar_date) constraint if two calls share the same Saturday start.
        LocalDate tradingStart = startDate;
        while (tradingStart.getDayOfWeek().getValue() > 5) {
            tradingStart = tradingStart.plusDays(1);
        }

        MersenneTwister rng = new MersenneTwister(RNG_SEED);

        // Pre-generate market-factor Z draws (one per day) and compute market excess returns
        double[] zMkt = new double[TRADING_DAYS];
        for (int d = 0; d < TRADING_DAYS; d++) {
            zMkt[d] = rng.nextGaussian();
        }

        double rfDaily = RF_ANNUAL / 252.0;
        double[] mktExcessReturns = new double[TRADING_DAYS];
        for (int d = 0; d < TRADING_DAYS; d++) {
            // Ito-corrected market log-return
            double logRet = (MU_MKT - SIGMA_MKT * SIGMA_MKT / 2.0) * DT
                          + SIGMA_MKT * Math.sqrt(DT) * zMkt[d];
            double grossRet = Math.exp(logRet);
            mktExcessReturns[d] = (grossRet - 1.0) - rfDaily;
        }

        // Per-security: draw idiosyncratic Z series and build OHLCV rows
        List<List<OhlcvRow>> result = new ArrayList<>(specs.size());
        for (SecuritySpec spec : specs) {
            double[] zIdio = new double[TRADING_DAYS];
            for (int d = 0; d < TRADING_DAYS; d++) {
                zIdio[d] = rng.nextGaussian();
            }

            List<OhlcvRow> rows = new ArrayList<>(TRADING_DAYS);
            double s = spec.startPrice();
            LocalDate date = tradingStart;

            for (int d = 0; d < TRADING_DAYS; d++) {
                // Clamp beta into [0,1] for the correlation decomposition
                double betaCapped = Math.min(Math.max(spec.beta(), 0.0), 1.0);
                // Correlated innovation
                double z = betaCapped * zMkt[d]
                         + Math.sqrt(1.0 - betaCapped * betaCapped) * zIdio[d];

                // Ito-corrected GBM: the (mu - sigma^2/2)*dt term is MANDATORY
                double sigma = spec.sigma();
                double logRet = (spec.mu() - sigma * sigma / 2.0) * DT
                              + sigma * Math.sqrt(DT) * z;

                double open  = s;
                double close = s * Math.exp(logRet);

                // Intraday range: noise proportional to the day's move
                double absMove  = Math.abs(close - open);
                double rangeNoise = absMove * 0.3 * Math.abs(rng.nextGaussian());
                double high = Math.max(open, close) + rangeNoise;
                double low  = Math.min(open, close) - rangeNoise;
                // Guarantee: high >= max(open,close) and low <= min(open,close)
                high = Math.max(high, Math.max(open, close));
                low  = Math.max(low, 0.01); // prevent non-positive prices

                // Simulated volume: base 1M shares, elevated on large moves
                long volume = (long) (1_000_000L * (0.8 + 5.0 * Math.abs(logRet)));
                volume = Math.max(volume, 100_000L);

                rows.add(new OhlcvRow(
                        date,
                        bd(open), bd(high), bd(low), bd(close),
                        volume
                ));

                s = close;
                date = nextTradingDay(date);
            }
            result.add(rows);
        }
        return new OhlcvResult(result, mktExcessReturns);
    }

    /**
     * Generate synthetic Fama-French 3-factor return rows.
     *
     * <p>The {@code mktExcessReturns} array must be the one returned by
     * {@link #generateOhlcv} — passing it as a parameter (rather than storing it as an
     * instance field) keeps this component stateless and safe for concurrent callers.
     *
     * <p>SMB and HML use a separate {@link MersenneTwister} seeded with
     * {@code RNG_SEED + 1} to keep them independent of the price-series sequence while
     * remaining reproducible.
     *
     * @param mktExcessReturns daily market excess returns from {@link OhlcvResult#mktExcessReturns()}
     * @param startDate        first date in the factor series
     * @return list of {@link FactorRow} of length {@link #TRADING_DAYS}
     */
    public List<FactorRow> generateFactors(double[] mktExcessReturns, LocalDate startDate) {
        if (mktExcessReturns == null) {
            throw new IllegalArgumentException("mktExcessReturns must not be null — pass OhlcvResult.mktExcessReturns()");
        }
        // Normalize startDate to a trading day (mirrors generateOhlcv normalization)
        while (startDate.getDayOfWeek().getValue() > 5) {
            startDate = startDate.plusDays(1);
        }

        // SMB and HML: synthetic but plausible
        MersenneTwister rngFf = new MersenneTwister(RNG_SEED + 1);

        double rfDaily = RF_ANNUAL / 252.0;
        // Approximate annualized vols: SMB ~10%, HML ~12%
        double smbDailySigma = 0.10 / Math.sqrt(252.0);
        double hmlDailySigma = 0.12 / Math.sqrt(252.0);

        List<FactorRow> rows = new ArrayList<>(TRADING_DAYS);
        LocalDate date = startDate;
        for (int d = 0; d < TRADING_DAYS; d++) {
            double smb = smbDailySigma * rngFf.nextGaussian();
            double hml = hmlDailySigma * rngFf.nextGaussian();
            rows.add(new FactorRow(
                    date,
                    bd(mktExcessReturns[d]),
                    bd(smb),
                    bd(hml),
                    bd(rfDaily)
            ));
            date = nextTradingDay(date);
        }
        return rows;
    }

    /**
     * Generate a price series deliberately <strong>cointegrated</strong> with an existing base series.
     *
     * <p>Builds log-prices as {@code logB[t] = alpha + beta*logA[t] + u[t]}, where
     * {@code u[t] = phi*u[t-1] + sigmaU*eps[t]} is a stationary AR(1) / Ornstein-Uhlenbeck spread
     * ({@code |phi| < 1}). Because the spread {@code u} is stationary, the Engle-Granger residual of
     * regressing {@code logB} on {@code logA} is stationary, so the ADF test rejects the unit root
     * ({@code p < 0.05}) and {@code CointegrationScanner} legitimately detects the pair — no scanner
     * change and no threshold loosening; the quant stays defensible. {@code alpha} is chosen so the
     * series starts exactly at {@code startPrice} (with {@code u[0] = 0}).
     *
     * <p><strong>Determinism / isolation:</strong> uses a DEDICATED {@link MersenneTwister} seeded
     * with {@code rngSeed}, completely independent of the main price-series RNG stream. Adding a
     * partner therefore does NOT perturb any other security's prices, so all existing golden values
     * are preserved.
     *
     * @param baseRows   the partner's base series (e.g. XOM's OHLCV rows); dates are reused verbatim
     * @param startPrice the partner's starting price
     * @param beta       cointegration slope (≈1 for two like-for-like names)
     * @param phi        AR(1) spread persistence in (0,1); smaller = faster mean reversion
     * @param sigmaU     AR(1) spread innovation volatility (per day, in log space)
     * @param rngSeed    dedicated RNG seed (independent of the main stream)
     * @return OHLCV rows for the cointegrated partner, one per base row
     */
    public List<OhlcvRow> generateCointegratedPartner(List<OhlcvRow> baseRows, double startPrice,
                                                      double beta, double phi, double sigmaU,
                                                      long rngSeed) {
        if (baseRows == null || baseRows.isEmpty()) {
            throw new IllegalArgumentException("baseRows must be non-empty");
        }
        MersenneTwister rng = new MersenneTwister(rngSeed);
        int n = baseRows.size();
        double logA0 = Math.log(baseRows.get(0).close().doubleValue());
        // Pin B[0] = startPrice with u[0]=0:  ln(startPrice) = alpha + beta*logA0
        double alpha = Math.log(startPrice) - beta * logA0;

        List<OhlcvRow> rows = new ArrayList<>(n);
        double u = 0.0;
        double prevClose = startPrice;
        for (int t = 0; t < n; t++) {
            OhlcvRow base = baseRows.get(t);
            double logA = Math.log(base.close().doubleValue());
            if (t > 0) {
                u = phi * u + sigmaU * rng.nextGaussian();
            }
            double close = Math.exp(alpha + beta * logA + u);
            double open = (t == 0) ? startPrice : prevClose;

            // Intraday range: noise proportional to the day's move (mirrors the GBM path)
            double absMove = Math.abs(close - open);
            double rangeNoise = absMove * 0.3 * Math.abs(rng.nextGaussian());
            double high = Math.max(open, close) + rangeNoise;
            double low  = Math.min(open, close) - rangeNoise;
            high = Math.max(high, Math.max(open, close));
            low  = Math.max(low, 0.01);

            double logRet = (open > 0.0) ? Math.log(close / open) : 0.0;
            long volume = (long) (1_000_000L * (0.8 + 5.0 * Math.abs(logRet)));
            volume = Math.max(volume, 100_000L);

            rows.add(new OhlcvRow(base.date(), bd(open), bd(high), bd(low), bd(close), volume));
            prevClose = close;
        }
        return rows;
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    /**
     * Safe double-to-BigDecimal conversion.
     * Uses {@code BigDecimal.valueOf(d)} to avoid IEEE 754 binary artefacts,
     * then rounds to 6 decimal places with HALF_UP.
     * NEVER use {@code new BigDecimal(double)} — that captures rounding noise.
     */
    static BigDecimal bd(double d) {
        return BigDecimal.valueOf(d).setScale(6, RoundingMode.HALF_UP);
    }

    /** Advance to the next calendar day that is Mon–Fri (skips weekends). */
    private static LocalDate nextTradingDay(LocalDate date) {
        LocalDate next = date.plusDays(1);
        while (next.getDayOfWeek().getValue() > 5) { // 6=Saturday, 7=Sunday
            next = next.plusDays(1);
        }
        return next;
    }
}
