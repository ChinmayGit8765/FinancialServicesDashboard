package com.quantlens.analytics.api;

/**
 * Cointegration pair result from the Engle-Granger 2-step pairs scanner.
 * <p>
 * All fields are {@code double} for statistics / {@code String} for identifiers.
 *
 * @param tickerY      dependent series ticker (Y in OLS: Y = α + β·X + ε)
 * @param tickerX      independent series ticker (X)
 * @param hedgeRatio   OLS slope β from step 1 (log-price regression Y ~ X)
 * @param adfStatistic ADF τ-statistic computed on the OLS residual spread
 * @param pValue       MacKinnon (1994/2010) approximate p-value for the ADF τ-statistic;
 *                     values ≤ 0.05 suggest cointegration at 95% confidence
 * @param spreadZScore current spread Z-score: (spread − mean(spread)) / std(spread);
 *                     |Z| > 2 triggers a mean-reversion signal
 * @param signal       mean-reversion signal:
 *                     {@code "LONG_Y_SHORT_X"} when spreadZScore < −2 (spread below mean),
 *                     {@code "SHORT_Y_LONG_X"} when spreadZScore > +2 (spread above mean),
 *                     {@code "NEUTRAL"} otherwise
 */
public record PairResultDto(
        String tickerY,
        String tickerX,
        double hedgeRatio,
        double adfStatistic,
        double pValue,
        double spreadZScore,
        String signal
) {}
