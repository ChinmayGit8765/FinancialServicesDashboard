package com.quantlens.analytics.api;

import java.util.List;

/**
 * Pairwise return-correlation matrix for a portfolio's holdings.
 * <p>
 * The matrix is N×N where N = {@code tickers.size()}. The diagonal is {@code 1.0}.
 * The matrix is symmetric: {@code matrix[i][j] == matrix[j][i]}.
 * Values are Pearson correlation coefficients in the range [−1, +1].
 *
 * @param tickers ticker labels for row/column indices
 * @param matrix  N×N correlation matrix (row-major); {@code matrix[i][j]} is the
 *                Pearson correlation between the return series of
 *                {@code tickers.get(i)} and {@code tickers.get(j)}
 */
public record CorrelationMatrixDto(
        List<String> tickers,
        List<List<Double>> matrix
) {}
