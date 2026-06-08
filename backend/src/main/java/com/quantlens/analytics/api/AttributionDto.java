package com.quantlens.analytics.api;

/**
 * Fama-French 3-factor attribution result for a portfolio.
 * <p>
 * All fields are {@code double} (statistical quantities, not monetary).
 * Regression: {@code r_excess = α + β_mkt × MktRf + β_smb × SMB + β_hml × HML + ε}
 * via OLS ({@code OLSMultipleLinearRegression}).
 *
 * @param alphaAnnualized       intercept × 252 (annualized daily alpha)
 * @param betaMkt               loading on Mkt-RF factor
 * @param betaSmb               loading on SMB factor
 * @param betaHml               loading on HML factor
 * @param rSquared              coefficient of determination (R²) for the regression
 * @param contribMktAnnualized  betaMkt × mean(MktRf) × 252 (annualized return contribution)
 * @param contribSmbAnnualized  betaSmb × mean(SMB) × 252
 * @param contribHmlAnnualized  betaHml × mean(HML) × 252
 */
public record AttributionDto(
        double alphaAnnualized,
        double betaMkt,
        double betaSmb,
        double betaHml,
        double rSquared,
        double contribMktAnnualized,
        double contribSmbAnnualized,
        double contribHmlAnnualized
) {}
