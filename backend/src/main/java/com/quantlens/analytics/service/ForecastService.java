package com.quantlens.analytics.service;

import com.quantlens.analytics.api.ForecastDto;
import com.quantlens.analytics.api.ModelType;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import net.finmath.montecarlo.BrownianMotionFromMersenneRandomNumbers;
import net.finmath.montecarlo.RandomVariableFromArrayFactory;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloAssetModel;
import net.finmath.montecarlo.assetderivativevaluation.MonteCarloMertonModel;
import net.finmath.montecarlo.assetderivativevaluation.models.BlackScholesModel;
import net.finmath.montecarlo.assetderivativevaluation.models.HestonModel;
import net.finmath.montecarlo.process.EulerSchemeFromProcessModel;
import net.finmath.time.TimeDiscretizationFromArray;
import org.hipparchus.random.MersenneTwister;
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
 *       with Ito correction (drift = μ − σ²/2 in log-space; applied internally by finmath).</li>
 *   <li><strong>JUMP_DIFFUSION</strong> — Merton jump-diffusion via finmath
 *       {@code MonteCarloMertonModel}.</li>
 *   <li><strong>HESTON</strong> — Heston stochastic-vol model via finmath {@code HestonModel}
 *       with {@code FULL_TRUNCATION} scheme. Feller condition enforced at construction time.</li>
 *   <li><strong>BOOTSTRAP</strong> — Historical block bootstrap; resamples observed log-return
 *       blocks of length L = max(10, √H) to preserve volatility clustering.</li>
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
 *
 * <h3>Performance (RESEARCH.md §Performance Note)</h3>
 * <p>
 * Caching is deliberately deferred for Phase 5. Synchronous, single-shot simulation per
 * request is acceptable (5000 paths × ≤504 steps completes within the request latency budget).
 * No async layer, queue, executor pool, or cache is added here.
 * </p>
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
     *   <li>Dispatch to model runner (GBM, Merton, Heston, or Bootstrap)</li>
     * </ol>
     *
     * @param portfolioId the authenticated user's portfolio (resolved by controller, never from request)
     * @param model       the stochastic model to use
     * @param horizonDays number of trading days to project (already clamped to [1,504] by controller)
     * @return ForecastDto with p5/p25/p50/p75/p95 percentile bands per step
     * @throws IllegalArgumentException if fewer than 2 daily log returns are available for calibration
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
        // CR-01 fix: use sample variance (n-1 denominator) not population variance (n denominator).
        // Hipparchus getStandardDeviation() returns population std (biased sqrt(sum/n)).
        // The unbiased sample estimator sqrt(getSampleVariance() * 252) is correct for calibration.
        // For n=252 the error is ~0.2%; for n=2 (minimum allowed) the bias is 29%.
        DescriptiveStatistics stats = new DescriptiveStatistics(dailyLogReturns);
        double annualizedMu    = stats.getMean()              * 252.0;
        double annualizedSigma = Math.sqrt(stats.getSampleVariance() * 252.0);

        log.debug("Calibration for portfolioId={}: annualizedMu={}, annualizedSigma={}, nReturns={}",
                  portfolioId, annualizedMu, annualizedSigma, dailyLogReturns.length);

        // --- Step 4: Compute current portfolio value ---
        double initialValue = computeCurrentPortfolioValue(positions);

        log.debug("Portfolio initial value for portfolioId={}: {}", portfolioId, initialValue);

        // --- WR-02 guard: refuse to simulate from a non-positive base value ---
        // A short-only portfolio or zero-value portfolio returns initialValue=0.0, which causes
        // GBM/Heston/Merton to produce S_t=0 for all t, and Bootstrap to stay flat at 0.
        // Surface this as a clear error rather than silently returning a misleading zero fan.
        if (initialValue <= 0.0) {
            throw new IllegalArgumentException(
                    "Portfolio initial value is zero or negative (portfolioId=" + portfolioId +
                    ", value=" + initialValue + "). Cannot run Monte Carlo simulation from " +
                    "a non-positive base value. Portfolio must contain long positions with " +
                    "available price data.");
        }

        // --- Step 5: Dispatch to model runner ---
        return switch (model) {
            case GBM             -> runGbm(horizonDays, annualizedMu, annualizedSigma, initialValue, model);
            case JUMP_DIFFUSION  -> runMerton(horizonDays, annualizedMu, annualizedSigma, initialValue, model);
            case HESTON          -> runHeston(horizonDays, annualizedMu, annualizedSigma, initialValue, model);
            case BOOTSTRAP       -> runBootstrap(horizonDays, dailyLogReturns, initialValue, model);
        };
    }

    // -------------------------------------------------------------------------
    // Private model runners
    // -------------------------------------------------------------------------

    /**
     * Runs GBM (Geometric Brownian Motion) simulation via finmath BlackScholesModel.
     *
     * <p>finmath BlackScholesModel applies the Ito correction internally:
     * the log-space drift is (μ − σ²/2)dt. Do NOT manually add a (−σ²/2) drift term.
     * HC-11 (ForecastMathHandComputedTest) guards against Ito-correction bugs in CI.
     * (T-05-04 mitigation)
     *
     * @param horizonDays     number of trading days to simulate
     * @param annualizedMu    calibrated annualized drift
     * @param annualizedSigma calibrated annualized volatility
     * @param initialValue    base portfolio value
     * @param model           ModelType.GBM (for DTO construction)
     */
    private ForecastDto runGbm(int horizonDays, double annualizedMu, double annualizedSigma,
                                double initialValue, ModelType model) {
        try {
            // Step 1: Time grid — 0 to horizonDays steps of 1/252 year each
            var td = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);

            // Step 2: 1-factor Brownian motion with fixed seed (reproducibility)
            var bm = new BrownianMotionFromMersenneRandomNumbers(td, 1, NUM_PATHS, MC_SEED);

            // Step 3: GBM model — BlackScholesModel handles Ito (μ−σ²/2) in log-space automatically
            var rvf   = new RandomVariableFromArrayFactory();
            var gbmModel = new BlackScholesModel(initialValue, annualizedMu, annualizedSigma, rvf);

            // Step 4: Wire Euler scheme and simulation wrapper
            var process = new EulerSchemeFromProcessModel(gbmModel, bm);
            var sim     = new MonteCarloAssetModel(process);

            // Step 5: Extract per-step percentile bands
            return extractBands(sim, horizonDays, model);

        } catch (Exception e) {
            throw new IllegalStateException("GBM simulation failed", e);
        }
    }

    /**
     * Runs Merton jump-diffusion simulation via finmath MonteCarloMertonModel.
     *
     * <p>Uses illustrative jump parameters (λ, μ_J, σ_J). The 10-parameter constructor
     * is confirmed at compile time in ForecastFinmathIntegrationTest (A5 resolved).
     *
     * @param horizonDays     number of trading days to simulate
     * @param annualizedMu    calibrated annualized drift
     * @param annualizedSigma calibrated annualized volatility
     * @param initialValue    base portfolio value
     * @param model           ModelType.JUMP_DIFFUSION (for DTO construction)
     */
    private ForecastDto runMerton(int horizonDays, double annualizedMu, double annualizedSigma,
                                   double initialValue, ModelType model) {
        try {
            var td  = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);
            var rvf = new RandomVariableFromArrayFactory();

            // MonteCarloMertonModel 10-param constructor (A5 confirmed):
            // (td, numPaths, seed, S0, mu, sigma, jumpIntensity, jumpSizeMean, jumpSizeStDev, factory)
            var mertonSim = new MonteCarloMertonModel(
                    td,
                    NUM_PATHS,
                    MC_SEED,
                    initialValue,
                    annualizedMu,
                    annualizedSigma,
                    JUMP_LAMBDA,
                    JUMP_MU_J,
                    JUMP_SIGMA_J,
                    rvf
            );

            return extractBands(mertonSim, horizonDays, model);

        } catch (Exception e) {
            throw new IllegalStateException("Merton jump-diffusion simulation failed", e);
        }
    }

    /**
     * Runs Heston stochastic-vol simulation via finmath HestonModel with FULL_TRUNCATION.
     *
     * <p>Uses 2 Brownian factors (asset SDE + variance CIR SDE).
     * FULL_TRUNCATION prevents the discretized variance from going negative (T-05-05 mitigation).
     * The Feller condition 2κθ > ξ² is enforced in the constructor (already verified above).
     *
     * <p>HestonModel constructor order (A4 confirmed): (S0, riskFreeRate, volatility=sqrt(V0),
     * discountRate, theta, kappa, xi, rho, Scheme, Factory) — theta BEFORE kappa.
     *
     * @param horizonDays     number of trading days to simulate
     * @param annualizedMu    calibrated annualized drift
     * @param annualizedSigma calibrated annualized volatility (used only for logging; V0=HESTON_V0)
     * @param initialValue    base portfolio value
     * @param model           ModelType.HESTON (for DTO construction)
     */
    private ForecastDto runHeston(int horizonDays, double annualizedMu, double annualizedSigma,
                                   double initialValue, ModelType model) {
        try {
            var td  = new TimeDiscretizationFromArray(0.0, horizonDays, 1.0 / 252.0);
            var rvf = new RandomVariableFromArrayFactory();

            // HestonModel constructor (A4 confirmed): theta BEFORE kappa
            // (S0, riskFreeRate, volatility=sqrt(V0), discountRate, theta, kappa, xi, rho, Scheme, Factory)
            var hestonModel = new HestonModel(
                    rvf.createRandomVariable(initialValue),          // S₀
                    rvf.createRandomVariable(annualizedMu),          // drift (riskFreeRate)
                    rvf.createRandomVariable(Math.sqrt(HESTON_V0)), // volatility = sqrt(V₀)
                    rvf.createRandomVariable(0.0),                   // discountRate (0 for equity)
                    rvf.createRandomVariable(HESTON_THETA),          // long-run variance θ (before κ)
                    rvf.createRandomVariable(HESTON_KAPPA),          // mean-reversion speed κ
                    rvf.createRandomVariable(HESTON_XI),             // vol-of-vol ξ
                    rvf.createRandomVariable(HESTON_RHO),            // asset-variance correlation ρ
                    HestonModel.Scheme.FULL_TRUNCATION,              // prevents V < 0 (T-05-05)
                    rvf
            );

            // Heston needs 2 Brownian factors (asset + variance SDE)
            var bm      = new BrownianMotionFromMersenneRandomNumbers(td, 2, NUM_PATHS, MC_SEED);
            var process = new EulerSchemeFromProcessModel(hestonModel, bm);
            var sim     = new MonteCarloAssetModel(process);

            return extractBands(sim, horizonDays, model);

        } catch (Exception e) {
            throw new IllegalStateException("Heston simulation failed", e);
        }
    }

    /**
     * Runs historical block bootstrap simulation using Hipparchus MersenneTwister.
     *
     * <p>Block bootstrap algorithm:
     * <ol>
     *   <li>Compute H = dailyLogReturns.length (number of historical observations)</li>
     *   <li>Set L = max(10, (int)√H) — block length preserves ~monthly autocorrelation /
     *       volatility clustering vs i.i.d. resampling. For typical 252-day history, L≈16;
     *       for 504-day history, L≈22.</li>
     *   <li>For each of NUM_PATHS paths, start at initialValue and repeatedly:
     *       pick blockStart = rng.nextInt(H - L + 1) (boundary-safe: last block start ensures
     *       full block of L observations fits within [0, H-1], preventing index overrun),
     *       then compound currentValue *= exp(logReturn) over L steps until horizonDays filled</li>
     *   <li>Record per-step value for each path; extract percentiles across paths at each step</li>
     * </ol>
     *
     * <p>Block length rationale: L≈√H preserves the autocorrelation structure of historical
     * returns (volatility clustering / GARCH-like effects) without over-constraining the
     * resample. i.i.d. daily resampling (L=1) destroys clustering; very long blocks reduce
     * the number of distinct resamples. L=max(10,√H) is a standard practical choice.
     *
     * @param horizonDays       number of trading days to project
     * @param dailyLogReturns   historical daily log returns from calibration
     * @param initialValue      base portfolio value
     * @param model             ModelType.BOOTSTRAP (for DTO construction)
     */
    private ForecastDto runBootstrap(int horizonDays, double[] dailyLogReturns,
                                      double initialValue, ModelType model) {
        int H = dailyLogReturns.length;
        // Block length: max(10, √H) — preserves ~monthly autocorrelation / volatility clustering
        int L = Math.max(10, (int) Math.sqrt(H));

        // CR-02 fix: guard H <= L (not just H < L) to catch degenerate maxBlockStart=0 case.
        // When H==L (e.g., H=10, L=max(10,sqrt(10))=10), maxBlockStart=0 and all 5000 paths
        // are identical (rng.nextInt(1) always returns 0). Change guard to H <= L so L=H/2
        // is applied, ensuring maxBlockStart >= 1 and the bootstrap produces distinct paths.
        if (H <= L) {
            L = Math.max(1, H / 2);
        }

        // Boundary-safe: blockStart ∈ [0, H-L] ensures full block [blockStart, blockStart+L-1]
        // fits within the dailyLogReturns array (Pitfall 6 prevention)
        final int maxBlockStart = H - L;

        MersenneTwister rng = new MersenneTwister(MC_SEED);

        // step-major value buffer: pathValues[t][path] = portfolio value at step t for given path
        // We accumulate into per-step arrays to extract percentiles without materializing the full
        // [horizonDays × NUM_PATHS] matrix (memory-efficient streaming approach).
        double[] p5  = new double[horizonDays];
        double[] p25 = new double[horizonDays];
        double[] p50 = new double[horizonDays];
        double[] p75 = new double[horizonDays];
        double[] p95 = new double[horizonDays];

        // pathSnapshot[path] = value of path at the current time step (rolling)
        // stepValues[t] will be filled path-by-path using a transposed approach
        // We build a step-major matrix to enable per-step percentile extraction
        double[][] stepValues = new double[horizonDays][NUM_PATHS];

        for (int path = 0; path < NUM_PATHS; path++) {
            double currentValue = initialValue;
            int stepsRemaining = horizonDays;
            int stepIdx = 0;

            while (stepsRemaining > 0) {
                // Boundary-safe block start: nextInt(maxBlockStart + 1) gives [0, maxBlockStart]
                int blockStart = (maxBlockStart >= 0) ? rng.nextInt(maxBlockStart + 1) : 0;

                // Apply the block of L returns (or fewer if near the horizon end)
                int blockLen = Math.min(L, stepsRemaining);
                for (int i = 0; i < blockLen; i++) {
                    currentValue *= Math.exp(dailyLogReturns[blockStart + i]);
                    stepValues[stepIdx][path] = currentValue;
                    stepIdx++;
                }
                stepsRemaining -= blockLen;
            }
        }

        // Extract percentile bands per step
        for (int t = 0; t < horizonDays; t++) {
            double[] pcts = extractPercentiles(stepValues[t]);
            p5[t]  = pcts[0];
            p25[t] = pcts[1];
            p50[t] = pcts[2];
            p75[t] = pcts[3];
            p95[t] = pcts[4];
        }

        return new ForecastDto(model, horizonDays, p5, p25, p50, p75, p95);
    }

    /**
     * Extracts per-step percentile bands from a finmath MonteCarloAssetModel.
     * Iterates t=1..horizonDays, calls getAssetValue(t,0).getRealizations(), extracts p5..p95.
     *
     * @param sim         finmath simulation model (GBM or Heston — both implement MonteCarloAssetModel)
     * @param horizonDays number of steps
     * @param model       model type for the DTO
     * @return ForecastDto with five percentile bands
     */
    private ForecastDto extractBands(net.finmath.montecarlo.assetderivativevaluation.AssetModelMonteCarloSimulationModel sim,
                                      int horizonDays, ModelType model) throws Exception {
        double[] p5  = new double[horizonDays];
        double[] p25 = new double[horizonDays];
        double[] p50 = new double[horizonDays];
        double[] p75 = new double[horizonDays];
        double[] p95 = new double[horizonDays];

        for (int t = 1; t <= horizonDays; t++) {
            double[] realizations = sim.getAssetValue(t, 0).getRealizations();
            double[] pcts = extractPercentiles(realizations);
            int idx = t - 1;
            p5[idx]  = pcts[0];
            p25[idx] = pcts[1];
            p50[idx] = pcts[2];
            p75[idx] = pcts[3];
            p95[idx] = pcts[4];
        }

        return new ForecastDto(model, horizonDays, p5, p25, p50, p75, p95);
    }

    /**
     * Extracts p5/p25/p50/p75/p95 from an array of path values.
     * Uses nearest-rank method (sort + index arithmetic) — faster than DescriptiveStatistics
     * for large arrays (single sort, no internal copy per percentile call).
     * Sorting is done on a clone; the original array is not modified.
     * Result is monotone by construction (sorted array + ascending percentile levels).
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
