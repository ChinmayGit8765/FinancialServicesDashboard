package com.quantlens.analytics.service;

import com.quantlens.analytics.api.CorrelationMatrixDto;
import com.quantlens.marketdata.domain.OhlcvBar;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.portfolio.domain.Position;
import com.quantlens.portfolio.domain.PositionRepository;
import org.hipparchus.linear.RealMatrix;
import org.hipparchus.stat.correlation.PearsonsCorrelation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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
 * Computes the pairwise Pearson return-correlation matrix for a portfolio's holdings.
 * <p>
 * Uses Hipparchus {@code PearsonsCorrelation} on aligned daily log-return series.
 * The resulting N×N matrix (diagonal forced to exactly 1.0, symmetric) is returned as a
 * labelled {@link CorrelationMatrixDto} for ECharts heatmap rendering.
 * <p>
 * Tickers are ordered deterministically by ticker symbol ASC so the matrix column/row
 * order is stable across calls.
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only.
 */
@Service
@Transactional(readOnly = true)
public class CorrelationCalculator {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;

    public CorrelationCalculator(PositionRepository positionRepository,
                                 OhlcvBarRepository ohlcvBarRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
    }

    /**
     * Computes the pairwise Pearson correlation matrix for the given portfolio's holdings.
     * <p>
     * Algorithm:
     * <ol>
     *   <li>Load all positions with quantity &gt; 0; order by ticker ASC for stability.</li>
     *   <li>Fetch all bars for the holding securities in one query; group by securityId.</li>
     *   <li>Intersect trading date sets so every per-security return series has the same length.</li>
     *   <li>Build a {@code double[nDays][nSecurities]} matrix (column k = security k's log returns).</li>
     *   <li>Pass to {@code new PearsonsCorrelation(matrix).getCorrelationMatrix()}.</li>
     *   <li>Force diagonal entries to exactly 1.0; convert to nested List.</li>
     * </ol>
     *
     * @param portfolioId the portfolio to analyse
     * @return CorrelationMatrixDto with tickers (ASC) and N×N Pearson matrix
     */
    public CorrelationMatrixDto computeCorrelationMatrix(Long portfolioId) {
        List<Position> positions = positionRepository.findByPortfolioIdWithSecurity(portfolioId);

        // Only positions with quantity > 0; order by ticker ASC for stable matrix column order
        List<Position> activePositions = positions.stream()
                .filter(p -> p.getQuantity().signum() > 0)
                .sorted(Comparator.comparing(p -> p.getSecurity().getTicker()))
                .collect(Collectors.toList());

        if (activePositions.isEmpty()) {
            return new CorrelationMatrixDto(List.of(), List.of());
        }

        List<String> tickers = activePositions.stream()
                .map(p -> p.getSecurity().getTicker())
                .collect(Collectors.toList());

        List<Long> securityIds = activePositions.stream()
                .map(p -> p.getSecurity().getId())
                .collect(Collectors.toList());

        // Fetch all bars in one query; group by securityId → TreeMap<date, close>
        List<OhlcvBar> allBars = ohlcvBarRepository.findAllBySecurityIdsOrdered(securityIds);
        Map<Long, NavigableMap<LocalDate, BigDecimal>> closesBySecId = new TreeMap<>();
        for (OhlcvBar bar : allBars) {
            closesBySecId
                    .computeIfAbsent(bar.getSecurity().getId(), id -> new TreeMap<>())
                    .put(bar.getBarDate(), bar.getClosePrice());
        }

        // Find common trading dates across all securities (true intersection — mirrors WR-02 fix)
        LocalDate firstDate = closesBySecId.values().stream()
                .map(NavigableMap::firstKey)
                .max(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars found for portfolio holdings"));
        LocalDate lastDate = closesBySecId.values().stream()
                .map(NavigableMap::lastKey)
                .min(Comparator.naturalOrder())
                .orElseThrow(() -> new IllegalStateException("No OHLCV bars found for portfolio holdings"));

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
        if (commonDates == null || commonDates.size() < 2) {
            return new CorrelationMatrixDto(tickers, identityMatrix(tickers.size()));
        }

        List<LocalDate> sortedDates = new ArrayList<>(commonDates);

        int nDays = sortedDates.size() - 1;  // log-return series length
        int nSecurities = activePositions.size();

        // Build double[nDays][nSecurities] return matrix (column k = security k's log returns)
        double[][] returnMatrix = new double[nDays][nSecurities];
        for (int k = 0; k < nSecurities; k++) {
            Long secId = activePositions.get(k).getSecurity().getId();
            NavigableMap<LocalDate, BigDecimal> secMap = closesBySecId.get(secId);
            if (secMap == null) {
                throw new IllegalStateException(
                        "No OHLCV bars found for security ID " + secId +
                        " (ticker=" + activePositions.get(k).getSecurity().getTicker() + ")");
            }
            for (int i = 0; i < nDays; i++) {
                LocalDate d0 = sortedDates.get(i);
                LocalDate d1 = sortedDates.get(i + 1);
                double p0 = secMap.get(d0).doubleValue();
                double p1 = secMap.get(d1).doubleValue();
                returnMatrix[i][k] = (p0 == 0.0) ? 0.0 : Math.log(p1 / p0);
            }
        }

        // Compute Pearson correlation matrix: n×k matrix (rows=trading days, cols=securities)
        RealMatrix corr = new PearsonsCorrelation(returnMatrix).getCorrelationMatrix();

        // Convert to List<List<Double>> with diagonal forced to exactly 1.0
        List<List<Double>> matrix = new ArrayList<>(nSecurities);
        for (int i = 0; i < nSecurities; i++) {
            List<Double> row = new ArrayList<>(nSecurities);
            for (int j = 0; j < nSecurities; j++) {
                if (i == j) {
                    row.add(1.0); // diagonal forced to exactly 1.0
                } else {
                    row.add(corr.getEntry(i, j));
                }
            }
            matrix.add(row);
        }

        return new CorrelationMatrixDto(tickers, matrix);
    }

    /**
     * Returns an N×N identity matrix (all zeros except diagonal = 1.0).
     * Used as fallback when there is insufficient data for correlation.
     */
    private static List<List<Double>> identityMatrix(int n) {
        List<List<Double>> m = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            List<Double> row = new ArrayList<>(n);
            for (int j = 0; j < n; j++) {
                row.add(i == j ? 1.0 : 0.0);
            }
            m.add(row);
        }
        return m;
    }

    /**
     * Builds a single-security price curve as a DateValueDto list, sorted ascending by date.
     * Used internally — exposed here as a utility for callers that need aligned return series.
     */
    @SuppressWarnings("unused")
    static List<DateValueDto> buildCurveFromBars(List<OhlcvBar> bars) {
        return bars.stream()
                .sorted(Comparator.comparing(OhlcvBar::getBarDate))
                .map(b -> new DateValueDto(b.getBarDate(), b.getClosePrice()))
                .collect(Collectors.toList());
    }
}
