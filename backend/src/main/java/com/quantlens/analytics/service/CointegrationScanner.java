package com.quantlens.analytics.service;

import com.quantlens.analytics.api.PairResultDto;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.Security;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.distribution.continuous.NormalDistribution;
import org.hipparchus.stat.descriptive.DescriptiveStatistics;
import org.hipparchus.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Engle-Granger 2-step cointegration pairs scanner for a portfolio.
 * <p>
 * Step 1: OLS hedge ratio {@code y = α + β·x + ε} on log-price series.
 * Step 2: ADF test on the RESIDUAL SPREAD (NOT raw log prices) using Hipparchus OLS +
 * MacKinnon (1994/2010) p-value approximation.
 * <p>
 * Candidate pairs are bounded to same-sector holdings (max 20 pairs) to keep scan
 * time demo-fast. Only pairs with MacKinnon p-value &lt; 0.05 are returned.
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only.
 */
@Service
@Transactional(readOnly = true)
public class CointegrationScanner {

    // MacKinnon (2010) ADF critical values — constant-only ("c") case, asymptotic
    // [CITED: statsmodels/tsa/adfvalues.py, MacKinnon 2010 tables]
    static final double ADF_CV_1PCT  = -3.430;
    static final double ADF_CV_5PCT  = -2.862;
    static final double ADF_CV_10PCT = -2.567;

    // MacKinnon boundary constants for p-value polynomial (constant-only "c", n=1)
    private static final double TAU_MAX   =  2.74;   // tau above this → p-value ≈ 1.0
    private static final double TAU_MIN   = -18.83;  // tau below this → p-value ≈ 0.0
    private static final double TAU_STAR  = -1.61;   // boundary between small-p and large-p regimes

    // Small-p regime polynomial coefficients (tau <= TAU_STAR)
    private static final double SMALL_P_0 = 2.1659;
    private static final double SMALL_P_1 = 1.4412;
    private static final double SMALL_P_2 = 0.038269;

    // Large-p regime polynomial coefficients (tau > TAU_STAR)
    private static final double LARGE_P_0 =  1.7339;
    private static final double LARGE_P_1 =  0.93202;
    private static final double LARGE_P_2 = -0.12745;
    private static final double LARGE_P_3 = -0.010368;

    /** Maximum candidate pairs to evaluate (DoS guard — T-04-06). */
    private static final int MAX_CANDIDATE_PAIRS = 20;

    /** Minimum spread length to run ADF (guard against degenerate pairs). */
    private static final int MIN_SPREAD_LENGTH = 10;

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final SecurityRepository securityRepository;

    public CointegrationScanner(PositionRepository positionRepository,
                                OhlcvBarRepository ohlcvBarRepository,
                                SecurityRepository securityRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.securityRepository = securityRepository;
    }

    /**
     * Scans for cointegrated pairs within the given portfolio's holdings.
     * <p>
     * Candidate pairs are same-sector holdings, capped at {@value #MAX_CANDIDATE_PAIRS}.
     * Each pair is tested via Engle-Granger 2-step: OLS hedge ratio on log prices, then
     * ADF test on the OLS residual spread. Only pairs with MacKinnon p-value &lt; 0.05
     * are included in the result.
     *
     * @param portfolioId the portfolio to scan
     * @return list of cointegrated pairs (possibly empty) with p-value &lt; 0.05
     */
    public List<PairResultDto> scanPairs(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        // Only consider long holdings (quantity > 0)
        List<Security> holdings = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(Position::getSecurity)
                .distinct()
                .collect(Collectors.toList());

        if (holdings.size() < 2) {
            return List.of();
        }

        // Group holdings by sector for same-sector candidate pair selection (getSector() — T-04-06 bound)
        Map<String, List<Security>> bySector = holdings.stream()
                .collect(Collectors.groupingBy(Security::getSector));

        // Generate same-sector C(n,2) candidate pairs, capped at MAX_CANDIDATE_PAIRS
        List<long[]> candidatePairs = new ArrayList<>();
        outer:
        for (List<Security> sectorGroup : bySector.values()) {
            for (int i = 0; i < sectorGroup.size(); i++) {
                for (int j = i + 1; j < sectorGroup.size(); j++) {
                    candidatePairs.add(new long[]{
                            sectorGroup.get(i).getId(),
                            sectorGroup.get(j).getId()
                    });
                    if (candidatePairs.size() >= MAX_CANDIDATE_PAIRS) {
                        break outer;
                    }
                }
            }
        }

        // Build a map from securityId → ticker for DTO construction
        Map<Long, String> tickerById = holdings.stream()
                .collect(Collectors.toMap(Security::getId, Security::getTicker, (a, b) -> a));

        // Pre-load all security bars in one query
        List<Long> allSecIds = holdings.stream()
                .map(Security::getId)
                .distinct()
                .collect(Collectors.toList());
        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(allSecIds);

        // Group bars by security ID into sorted date maps
        Map<Long, NavigableMap<LocalDate, Double>> logPriceBySecId = new TreeMap<>();
        for (OhlcvBar bar : allBars) {
            logPriceBySecId
                    .computeIfAbsent(bar.getSecurity().getId(), id -> new TreeMap<>())
                    .put(bar.getBarDate(), Math.log(bar.getClosePrice().doubleValue()));
        }

        List<PairResultDto> results = new ArrayList<>();

        for (long[] pair : candidatePairs) {
            long idY = pair[0];
            long idX = pair[1];

            NavigableMap<LocalDate, Double> logY = logPriceBySecId.get(idY);
            NavigableMap<LocalDate, Double> logX = logPriceBySecId.get(idX);

            if (logY == null || logX == null) continue;

            // Find common dates between the two series
            List<LocalDate> commonDates = logY.keySet().stream()
                    .filter(logX::containsKey)
                    .sorted()
                    .collect(Collectors.toList());

            int n = commonDates.size();
            if (n < MIN_SPREAD_LENGTH) continue; // guard against degenerate pairs (Pitfall 6)

            double[] logPriceY = new double[n];
            double[] logPriceX = new double[n];
            for (int i = 0; i < n; i++) {
                LocalDate d = commonDates.get(i);
                logPriceY[i] = logY.get(d);
                logPriceX[i] = logX.get(d);
            }

            // --- Step 1: OLS hedge ratio ---
            // y = α + β × x + ε  on log-price series
            double[][] xCol = new double[n][1];
            for (int i = 0; i < n; i++) {
                xCol[i][0] = logPriceX[i];
            }
            OLSMultipleLinearRegression step1 = new OLSMultipleLinearRegression();
            step1.newSampleData(logPriceY, xCol);
            double hedgeRatio = step1.estimateRegressionParameters()[1]; // params[1] = β
            // ADF is applied to THIS residual spread (NOT raw log prices — Pitfall 3)
            double[] residuals = step1.estimateResiduals();

            // --- Step 2: ADF on residual spread ---
            double tStat = adfStatistic(residuals);
            if (Double.isNaN(tStat)) continue; // degenerate — skip pair

            // WARNING: Do NOT use TDistribution here — ADF follows the Dickey-Fuller
            // distribution. Use mackinnonPValue() (MacKinnon polynomial approximation).
            double pValue = mackinnonPValue(tStat);

            // Only report pairs that pass the cointegration threshold
            if (pValue >= 0.05) continue;

            // --- Spread Z-score (for signal computation) ---
            DescriptiveStatistics ds = new DescriptiveStatistics(residuals);
            double spreadMean = ds.getMean();
            double spreadStd  = ds.getStandardDeviation();
            double currentSpread = residuals[residuals.length - 1];
            double zScore = (spreadStd == 0.0) ? 0.0 : (currentSpread - spreadMean) / spreadStd;

            // --- Mean-reversion signal ---
            String signal;
            if (zScore > 2.0) {
                signal = "SHORT_Y_LONG_X";  // spread too high; expect mean-reversion down
            } else if (zScore < -2.0) {
                signal = "LONG_Y_SHORT_X";  // spread too low; expect mean-reversion up
            } else {
                signal = "NEUTRAL";
            }

            String tickerY = tickerById.getOrDefault(idY, String.valueOf(idY));
            String tickerX = tickerById.getOrDefault(idX, String.valueOf(idX));

            results.add(new PairResultDto(tickerY, tickerX, hedgeRatio, tStat, pValue, zScore, signal));
        }

        return results;
    }

    // -----------------------------------------------------------------------
    // Package-accessible static helpers (used by CointegrationScannerTest)
    // -----------------------------------------------------------------------

    /**
     * Computes the ADF test statistic for the given spread series.
     * <p>
     * Constructs a one-lag ADF regression:
     * {@code ΔS[t] = c + δ·S[t-1] + φ₁·ΔS[t-1] + η[t]}
     * and returns τ = δ̂ / SE(δ̂).
     * <p>
     * ADF must be run on the RESIDUAL SPREAD from Step 1 OLS — NOT on raw log prices.
     * Testing raw log prices for a unit root is trivially expected to fail.
     *
     * @param spread the residual spread series from Step 1 OLS (length ≥ 10)
     * @return ADF t-statistic (τ), or {@code Double.NaN} if series is too short
     */
    public static double adfStatistic(double[] spread) {
        if (spread.length < MIN_SPREAD_LENGTH) {
            return Double.NaN;
        }

        // adfN = spread.length - 2 (lose 2 observations: one for diff, one for lag)
        int adfN = spread.length - 2;

        double[] adfY  = new double[adfN];
        double[][] adfX = new double[adfN][2]; // columns: [S_{t-1}, ΔS_{t-1}]

        for (int i = 0; i < adfN; i++) {
            // t corresponds to index i+2 in the original spread array
            adfY[i]    = spread[i + 2] - spread[i + 1]; // ΔS[t]
            adfX[i][0] = spread[i + 1];                  // S[t-1]
            adfX[i][1] = spread[i + 1] - spread[i];      // ΔS[t-1]
        }

        // Hipparchus adds an intercept automatically (noIntercept = false by default)
        // params = [intercept (c), δ, φ₁]
        OLSMultipleLinearRegression adfReg = new OLSMultipleLinearRegression();
        adfReg.newSampleData(adfY, adfX);

        double[] adfParams = adfReg.estimateRegressionParameters();
        double[] adfSE     = adfReg.estimateRegressionParametersStandardErrors();

        double delta   = adfParams[1]; // coefficient on S[t-1]
        double seDelta = adfSE[1];     // standard error of δ

        if (seDelta == 0.0) {
            return Double.NaN; // degenerate case
        }

        return delta / seDelta; // ADF τ statistic
    }

    /**
     * Approximates the MacKinnon (2010) ADF p-value for the constant-only ("c") regression
     * with 1 cointegrating variable.
     * <p>
     * Uses the same polynomial response surface as Python's {@code statsmodels.tsa.stattools.adfuller}
     * with regression='c', autolag=None.
     * <p>
     * Reference check: {@code mackinnonPValue(-3.0)} ≈ 0.034 ± 0.01
     * (verified against {@code statsmodels.tsa.stattools.mackinnonp(-3.0, "c", 1)}).
     * <p>
     * WARNING: Do NOT use {@code TDistribution} for ADF p-values. The ADF statistic follows
     * the Dickey-Fuller distribution, which is non-standard and more left-skewed than Student's t.
     * Using TDistribution would significantly underestimate the p-value (overstate significance).
     *
     * @param tau the ADF test statistic
     * @return p-value in [0, 1]
     */
    public static double mackinnonPValue(double tau) {
        if (tau > TAU_MAX)  return 1.0;
        if (tau < TAU_MIN)  return 0.0;

        NormalDistribution nd = new NormalDistribution(0, 1);

        double lstar;
        if (tau <= TAU_STAR) {
            // Small p regime: tau <= -1.61
            // Polynomial: lstar = 2.1659 + 1.4412*tau + 0.038269*tau²
            lstar = SMALL_P_0 + SMALL_P_1 * tau + SMALL_P_2 * tau * tau;
        } else {
            // Large p regime: tau > -1.61
            // Polynomial: lstar = 1.7339 + 0.93202*tau - 0.12745*tau² - 0.010368*tau³
            lstar = LARGE_P_0 + LARGE_P_1 * tau + LARGE_P_2 * tau * tau + LARGE_P_3 * tau * tau * tau;
        }

        return nd.cumulativeProbability(lstar);
    }
}
