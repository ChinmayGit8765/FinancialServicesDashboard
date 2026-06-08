package com.quantlens.analytics.service;

import com.quantlens.analytics.api.ForecastDto;
import com.quantlens.analytics.api.ModelType;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Monte Carlo stochastic forecasting service for portfolio value projection (SIM-01, SIM-02).
 *
 * <h3>Supported models</h3>
 * <ul>
 *   <li><strong>GBM</strong> — Geometric Brownian Motion via finmath {@code BlackScholesModel}
 *       with Ito correction (drift = μ − σ²/2 in log-space). Engine in Plan 05-02.</li>
 *   <li><strong>JUMP_DIFFUSION</strong> — Merton jump-diffusion via finmath
 *       {@code MonteCarloMertonModel}. Engine in Plan 05-02.</li>
 *   <li><strong>HESTON</strong> — Heston stochastic-vol model via finmath {@code HestonModel}
 *       with {@code FULL_TRUNCATION} scheme. Illustrative parameters (not calibrated from data).
 *       Feller condition enforced at construction time. Engine in Plan 05-02.</li>
 *   <li><strong>BOOTSTRAP</strong> — Historical block bootstrap; resamples observed log-return
 *       blocks of length ≈ √H to preserve volatility clustering. Engine in Plan 05-02.</li>
 * </ul>
 *
 * <h3>Calibration</h3>
 * <p>
 * μ and σ are estimated from the portfolio's historical daily log returns
 * (via {@link RiskCalculator#logReturns} + {@link DescriptiveStatistics}), then annualized
 * (μ×252, σ×√252). The base portfolio value is computed as Σ qty × latestClose for long
 * positions using {@link OhlcvBarRepository#findLatestBarBySecurityIds}.
 * </p>
 *
 * <h3>Module boundary</h3>
 * <p>
 * This class is inside {@code com.quantlens.analytics.service}, part of the {@code analytics}
 * module. Allowed cross-module imports: {@code portfolio::domain} and {@code marketdata::domain}
 * (declared in {@code analytics/package-info.java}). Do NOT import {@code portfolio::service}.
 * </p>
 *
 * <h3>Constants (CONTEXT.md locked)</h3>
 * <ul>
 *   <li>MC_SEED = 42 — fixed seed for reproducible fan charts</li>
 *   <li>NUM_PATHS = 5000 — sufficient for smooth p5–p95 bands</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ForecastService {

    private static final Logger log = LoggerFactory.getLogger(ForecastService.class);

    // -------------------------------------------------------------------------
    // Simulation constants — CONTEXT.md locked, do NOT change
    // -------------------------------------------------------------------------

    /** Fixed RNG seed — reproducibility requirement from CONTEXT.md. */
    private static final int    MC_SEED   = 42;
    /** Number of Monte Carlo paths. Sufficient for smooth p5–p95 fan bands. */
    private static final int    NUM_PATHS = 5000;

    // Heston illustrative parameters (not calibrated from data — see UI/MODELS.md disclaimer)
    /** Heston mean-reversion speed. Feller: 2κθ > ξ² → 2×2.0×0.04 = 0.16 > 0.09 = 0.3². */
    private static final double HESTON_KAPPA = 2.0;
    /** Heston long-run variance. Corresponds to long-run vol ≈ 20%. */
    private static final double HESTON_THETA = 0.04;
    /** Heston vol-of-vol. Kept small enough to satisfy Feller condition. */
    private static final double HESTON_XI    = 0.3;
    /** Heston asset–variance correlation (leverage effect — negative for equities). */
    private static final double HESTON_RHO   = -0.7;
    /** Heston initial variance = calibrated annualized σ² (set to THETA for illustrative run). */
    private static final double HESTON_V0    = 0.04;

    // Merton jump-diffusion illustrative parameters
    /** Poisson jump intensity — ~1 jump per 10 years on average. */
    private static final double JUMP_LAMBDA  = 0.10;
    /** Mean log-jump size — downward bias (−10%). */
    private static final double JUMP_MU_J    = -0.10;
    /** Jump magnitude standard deviation (15%). */
    private static final double JUMP_SIGMA_J = 0.15;

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final PositionRepository  positionRepository;
    private final RiskCalculator      riskCalculator;
    /** Required for current portfolio value via {@code findLatestBarBySecurityIds}. */
    private final OhlcvBarRepository  ohlcvBarRepository;

    /**
     * Constructs the service and enforces the Heston Feller condition fail-fast.
     *
     * @param positionRepository  provides portfolio positions with security data
     * @param riskCalculator      provides equity-curve and log-return helpers
     * @param ohlcvBarRepository  provides latest close prices for portfolio value computation
     * @throws IllegalStateException if the Heston Feller condition 2κθ &gt; ξ² is violated
     *         (would indicate a misconfiguration of the locked constants)
     */
    public ForecastService(PositionRepository positionRepository,
                           RiskCalculator riskCalculator,
                           OhlcvBarRepository ohlcvBarRepository) {
        this.positionRepository = positionRepository;
        this.riskCalculator     = riskCalculator;
        this.ohlcvBarRepository = ohlcvBarRepository;

        // Fail-fast Feller condition guard — catches constant misconfiguration at startup
        if (2.0 * HESTON_KAPPA * HESTON_THETA <= HESTON_XI * HESTON_XI) {
            throw new IllegalStateException(
                    "Heston Feller condition violated: 2κθ ≤ ξ². " +
                    "kappa=" + HESTON_KAPPA + " theta=" + HESTON_THETA + " xi=" + HESTON_XI +
                    ". Feller requires 2×" + HESTON_KAPPA + "×" + HESTON_THETA +
                    " > " + (HESTON_XI * HESTON_XI));
        }
        log.debug("ForecastService initialized: Heston Feller condition satisfied " +
                  "(2κθ={} > ξ²={})", 2.0 * HESTON_KAPPA * HESTON_THETA, HESTON_XI * HESTON_XI);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Generates a Monte Carlo percentile forecast for the authenticated user's portfolio.
     *
     * <h4>Steps</h4>
     * <ol>
     *   <li>Load positions and build equity curve via {@link RiskCalculator#buildEquityCurveLocal}</li>
     *   <li>Compute daily log returns via {@link RiskCalculator#logReturns}</li>
     *   <li>Calibrate annualized μ and σ via {@link DescriptiveStatistics}</li>
     *   <li>Compute current portfolio value via latest closes</li>
     *   <li>Dispatch to model runner (STUB — engine arrives in Plan 05-02)</li>
     * </ol>
     *
     * @param portfolioId the authenticated user's portfolio (resolved by controller, never from request)
     * @param model       the stochastic model to use
     * @param horizonDays number of trading days to project (already clamped to [1,504] by controller)
     * @return ForecastDto with p5/p25/p50/p75/p95 percentile bands per step
     * @throws IllegalArgumentException if fewer than 2 daily log returns are available for calibration
     * @throws UnsupportedOperationException STUB — engine not yet implemented (Plan 05-02)
     */
    public ForecastDto forecast(Long portfolioId, ModelType model, int horizonDays) {
        // --- Step 1: Load positions and build equity curve ---
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);
        List<DateValueDto> curve = riskCalculator.buildEquityCurveLocal(positions);
        double[] dailyLogReturns = RiskCalculator.logReturns(curve);

        // --- Step 2: Guard insufficient history ---
        if (dailyLogReturns.length < 2) {
            throw new IllegalArgumentException(
                    "Insufficient data: need at least 2 daily log returns for calibration. " +
                    "Found " + dailyLogReturns.length + " returns for portfolioId=" + portfolioId);
        }

        // --- Step 3: Calibrate annualized μ and σ ---
        DescriptiveStatistics stats = new DescriptiveStatistics(dailyLogReturns);
        double annualizedMu    = stats.getMean()              * 252.0;
        double annualizedSigma = stats.getStandardDeviation() * Math.sqrt(252.0);

        log.debug("Calibration for portfolioId={}: annualizedMu={:.4f}, annualizedSigma={:.4f}, " +
                  "nReturns={}", portfolioId, annualizedMu, annualizedSigma, dailyLogReturns.length);

        // --- Step 4: Compute current portfolio value ---
        double initialValue = computeCurrentPortfolioValue(positions);

        log.debug("Portfolio initial value for portfolioId={}: {:.2f}", portfolioId, initialValue);

        // --- Step 5: Dispatch to model runner (STUB — Plan 05-02 implements the engine) ---
        // All models throw UnsupportedOperationException until Plan 05-02.
        // The test scaffolds in this plan (05-01) are intentionally RED until 05-02.
        throw new UnsupportedOperationException(
                "Monte Carlo engine not yet implemented — arrives in Plan 05-02. " +
                "model=" + model + " horizonDays=" + horizonDays +
                " portfolioId=" + portfolioId);
    }

    // -------------------------------------------------------------------------
    // Package-accessible helpers (used by tests)
    // -------------------------------------------------------------------------

    /**
     * Extracts p5/p25/p50/p75/p95 from an array of path values.
     * Uses nearest-rank method (sort + index arithmetic) — faster than DescriptiveStatistics
     * for large arrays (single sort, no internal copy per percentile call).
     * Sorting is done on a clone; the original array is not modified.
     *
     * @param pathValues double[] of per-path values at one time step (length = NUM_PATHS)
     * @return double[5] = {p5, p25, p50, p75, p95}
     */
    static double[] extractPercentiles(double[] pathValues) {
        double[] sorted = pathValues.clone();
        Arrays.sort(sorted);
        int n = sorted.length;
        return new double[]{
            sorted[clamp((int) Math.ceil(0.05 * n) - 1, 0, n - 1)],  // p5
            sorted[clamp((int) Math.ceil(0.25 * n) - 1, 0, n - 1)],  // p25
            sorted[clamp((int) Math.ceil(0.50 * n) - 1, 0, n - 1)],  // p50 (median)
            sorted[clamp((int) Math.ceil(0.75 * n) - 1, 0, n - 1)],  // p75
            sorted[clamp((int) Math.ceil(0.95 * n) - 1, 0, n - 1)]   // p95
        };
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the current portfolio market value as Σ qty × latestClose for long positions.
     * Mirrors the pattern in {@link RiskCalculator}'s VaR section (lines 197-210):
     * delegates to {@link OhlcvBarRepository#findLatestBarBySecurityIds} — single round-trip.
     *
     * @param positions all positions in the portfolio (with security eagerly fetched)
     * @return total market value as a double (for simulation base)
     */
    private double computeCurrentPortfolioValue(List<Position> positions) {
        List<Long> secIds = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());

        if (secIds.isEmpty()) {
            return 0.0;
        }

        Map<Long, BigDecimal> latestClose = ohlcvBarRepository.findLatestBarBySecurityIds(secIds)
                .stream()
                .collect(Collectors.toMap(
                        bar -> bar.getSecurity().getId(),
                        OhlcvBar::getClosePrice
                ));

        double total = 0.0;
        for (Position pos : positions) {
            if (pos.getQuantity().signum() <= 0) continue;
            BigDecimal close = latestClose.get(pos.getSecurity().getId());
            if (close == null) continue;
            total += pos.getQuantity().multiply(close).doubleValue();
        }
        return total;
    }
}
