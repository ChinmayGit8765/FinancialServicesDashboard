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

    // ── internal state ────────────────────────────────────────────────────────

    /**
     * Market excess returns captured by {@link #generateOhlcv};
     * consumed by {@link #generateFactors}.
     */
    private double[] lastMktExcessReturns;

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
     * @return list of OHLCV row lists, one inner list per security (same order as specs)
     */
    public List<List<OhlcvRow>> generateOhlcv(List<SecuritySpec> specs, LocalDate startDate) {
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
        this.lastMktExcessReturns = mktExcessReturns;

        // Per-security: draw idiosyncratic Z series and build OHLCV rows
        List<List<OhlcvRow>> result = new ArrayList<>(specs.size());
        for (SecuritySpec spec : specs) {
            double[] zIdio = new double[TRADING_DAYS];
            for (int d = 0; d < TRADING_DAYS; d++) {
                zIdio[d] = rng.nextGaussian();
            }

            List<OhlcvRow> rows = new ArrayList<>(TRADING_DAYS);
            double s = spec.startPrice();
            LocalDate date = startDate;

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
        return result;
    }

    /**
     * Generate synthetic Fama-French 3-factor return rows.
     *
     * <p>Must be called AFTER {@link #generateOhlcv} so that market excess
     * returns have been captured.  SMB and HML use a separate
     * {@link MersenneTwister} seeded with {@code RNG_SEED + 1} to keep them
     * independent of the price-series sequence while remaining reproducible.
     *
     * @param startDate first date in the factor series
     * @return list of {@link FactorRow} of length {@link #TRADING_DAYS}
     */
    public List<FactorRow> generateFactors(LocalDate startDate) {
        if (lastMktExcessReturns == null) {
            throw new IllegalStateException("Call generateOhlcv() before generateFactors()");
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
                    bd(lastMktExcessReturns[d]),
                    bd(smb),
                    bd(hml),
                    bd(rfDaily)
            ));
            date = nextTradingDay(date);
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
