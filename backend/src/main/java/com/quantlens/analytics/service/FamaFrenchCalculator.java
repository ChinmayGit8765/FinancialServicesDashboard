package com.quantlens.analytics.service;

import com.quantlens.analytics.api.AttributionDto;
import com.quantlens.marketdata.domain.FactorReturn;
import com.quantlens.marketdata.domain.FactorReturnRepository;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.stat.regression.OLSMultipleLinearRegression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Computes Fama-French 3-factor attribution for a portfolio.
 * <p>
 * Regression: {@code r_excess = α + β_mkt × MktRf + β_smb × SMB + β_hml × HML + ε}
 * via Hipparchus {@code OLSMultipleLinearRegression}. Output: alpha (annualized), factor
 * betas, R², and per-factor return contributions (β × mean(factor) × 252).
 * <p>
 * Date alignment (ATTR-01 correctness requirement): log return at index i corresponds to
 * ln(close[i+1]/close[i]), which aligns with the factor series at day i+1.
 * Hence: {@code factorRows.get(i + 1)} is used for each return index i.
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only.
 */
@Service
@Transactional(readOnly = true)
public class FamaFrenchCalculator {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final FactorReturnRepository factorReturnRepository;

    public FamaFrenchCalculator(PositionRepository positionRepository,
                                OhlcvBarRepository ohlcvBarRepository,
                                FactorReturnRepository factorReturnRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.factorReturnRepository = factorReturnRepository;
    }

    /**
     * Computes Fama-French 3-factor attribution for the given portfolio.
     * <p>
     * Steps:
     * <ol>
     *   <li>Build equity curve from constant-current-holdings (quantity > 0)</li>
     *   <li>Compute 503 log returns from the 504-bar equity curve</li>
     *   <li>Load 504 factor rows ordered by factorDate ASC</li>
     *   <li>Align: return[i] ↔ factorRows.get(i+1) (PITFALL 5 — critical)</li>
     *   <li>Run OLS: {@code excess = α + β_mkt×MktRf + β_smb×SMB + β_hml×HML}</li>
     *   <li>Annualize: alphaAnn = params[0]*252; contrib_k = params_k × mean(factor_k) × 252</li>
     * </ol>
     * <p>
     * DO NOT add an intercept column to xMatrix — Hipparchus adds it automatically.
     * (Adding a ones-column would make the system rank-deficient → NaN params.)
     *
     * @param portfolioId the portfolio to attribute
     * @return AttributionDto with alpha (annualized), 3 factor betas, R², and contributions
     * @throws IllegalStateException if factor alignment fails or equity curve has < 2 points
     */
    public AttributionDto computeAttribution(Long portfolioId) {
        // --- Build portfolio equity curve ---
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);
        List<DateValueDto> curve = buildEquityCurveLocal(positions);

        if (curve.size() < 2) {
            throw new IllegalStateException(
                    "Equity curve too short for attribution: " + curve.size() + " points");
        }

        // --- Compute log returns (curve.size()-1 returns) ---
        int nReturns = curve.size() - 1; // should be 503 for 504-bar seeded data
        double[] returns = new double[nReturns];
        for (int i = 0; i < nReturns; i++) {
            double p1 = curve.get(i + 1).value().doubleValue();
            double p0 = curve.get(i).value().doubleValue();
            returns[i] = (p0 == 0.0) ? 0.0 : Math.log(p1 / p0);
        }

        // --- Load factor returns ordered ASC ---
        List<FactorReturn> factorRows = factorReturnRepository.findAllByOrderByFactorDateAsc();

        // Defensive alignment guard: factorRows must have exactly nReturns + 1 entries
        // (504 factor rows for 503 log returns from 504-bar equity curve)
        if (factorRows.size() != nReturns + 1) {
            throw new IllegalStateException(
                    "Factor alignment failure: expected " + (nReturns + 1) + " factor rows " +
                    "for " + nReturns + " portfolio log returns, but got " + factorRows.size() +
                    ". Ensure FactorReturn table has the same 504-day trading calendar as OHLCV bars.");
        }

        // --- Build X matrix and excess return vector (aligned at i+1) ---
        // xMatrix[i] = {MktRf[i+1], SMB[i+1], HML[i+1]} for each return index i
        // excessReturns[i] = returns[i] - RF[i+1]
        // DO NOT add a ones-column — Hipparchus adds intercept automatically (PITFALL 2)
        double[] excessReturns = new double[nReturns];
        double[][] xMatrix = new double[nReturns][3];
        double[] mktRfArr = new double[nReturns];
        double[] smbArr = new double[nReturns];
        double[] hmlArr = new double[nReturns];

        for (int i = 0; i < nReturns; i++) {
            FactorReturn fr = factorRows.get(i + 1); // CRITICAL: use i+1 alignment (PITFALL 5)
            double mktRf = fr.getMktRf().doubleValue();
            double smb = fr.getSmb().doubleValue();
            double hml = fr.getHml().doubleValue();
            double rf = fr.getRf().doubleValue();

            mktRfArr[i] = mktRf;
            smbArr[i] = smb;
            hmlArr[i] = hml;

            xMatrix[i][0] = mktRf;
            xMatrix[i][1] = smb;
            xMatrix[i][2] = hml;

            excessReturns[i] = returns[i] - rf;
        }

        // --- OLS regression ---
        // reg.newSampleData(excess, xMatrix) — Hipparchus adds intercept automatically
        // params = [alpha_daily, beta_mkt, beta_smb, beta_hml]
        OLSMultipleLinearRegression reg = new OLSMultipleLinearRegression();
        reg.newSampleData(excessReturns, xMatrix);

        double[] params = reg.estimateRegressionParameters();

        // Explicit structural guard: params must have length 4 (intercept + 3 factor betas)
        if (params.length != 4) {
            throw new IllegalStateException(
                    "FF regression produced " + params.length + " parameters; expected 4 " +
                    "(intercept + β_mkt + β_smb + β_hml). Check xMatrix column count.");
        }

        double alphaDaily = params[0];   // intercept (daily)
        double betaMkt = params[1];
        double betaSmb = params[2];
        double betaHml = params[3];

        // --- Annualize ---
        double alphaAnnualized = alphaDaily * 252.0;
        double rSquared = reg.calculateRSquared();

        // --- Factor contributions: β_k × mean(factor_k) × 252 ---
        double meanMktRf = Arrays.stream(mktRfArr).average().orElse(0.0);
        double meanSmb = Arrays.stream(smbArr).average().orElse(0.0);
        double meanHml = Arrays.stream(hmlArr).average().orElse(0.0);

        double contribMkt = betaMkt * meanMktRf * 252.0;
        double contribSmb = betaSmb * meanSmb * 252.0;
        double contribHml = betaHml * meanHml * 252.0;

        return new AttributionDto(
                alphaAnnualized,
                betaMkt,
                betaSmb,
                betaHml,
                rSquared,
                contribMkt,
                contribSmb,
                contribHml
        );
    }

    // -----------------------------------------------------------------------
    // Package-private helpers
    // -----------------------------------------------------------------------

    /**
     * Builds an equity curve from portfolio positions.
     * <p>
     * REUSE: mirrors PortfolioService.buildEquityCurve — only positions with quantity > 0 included.
     */
    List<DateValueDto> buildEquityCurveLocal(List<Position> positions) {
        List<Long> securityIds = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .map(p -> p.getSecurity().getId())
                .distinct()
                .collect(Collectors.toList());

        if (securityIds.isEmpty()) {
            return List.of();
        }

        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds);

        // Group bars into Map<securityId, TreeMap<date, close>>
        Map<Long, NavigableMap<LocalDate, BigDecimal>> closesBySecId = new TreeMap<>();
        for (OhlcvBar bar : allBars) {
            closesBySecId
                    .computeIfAbsent(bar.getSecurity().getId(), id -> new TreeMap<>())
                    .put(bar.getBarDate(), bar.getClosePrice());
        }

        // Intersect date sets across all holdings
        LocalDate firstDate = closesBySecId.values().stream()
                .map(NavigableMap::firstKey)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars for portfolio positions"));
        LocalDate lastDate = closesBySecId.values().stream()
                .map(NavigableMap::lastKey)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars for portfolio positions"));

        Set<LocalDate> commonDates = null;
        for (NavigableMap<LocalDate, BigDecimal> secMap : closesBySecId.values()) {
            Set<LocalDate> secDates = new TreeSet<>(
                    secMap.subMap(firstDate, true, lastDate, true).keySet());
            if (commonDates == null) {
                commonDates = secDates;
            } else {
                commonDates.retainAll(secDates);
            }
        }
        if (commonDates == null) {
            commonDates = new TreeSet<>();
        }

        List<DateValueDto> curve = new ArrayList<>();
        for (LocalDate date : commonDates) {
            BigDecimal dayValue = BigDecimal.ZERO;
            for (Position pos : positions) {
                if (pos.getQuantity().signum() <= 0) continue;
                NavigableMap<LocalDate, BigDecimal> secMap = closesBySecId.get(pos.getSecurity().getId());
                BigDecimal close = secMap != null ? secMap.get(date) : null;
                if (close == null) continue;
                dayValue = dayValue.add(pos.getQuantity().multiply(close));
            }
            curve.add(new DateValueDto(date, dayValue.setScale(2, RoundingMode.HALF_UP)));
        }
        return curve;
    }
}
