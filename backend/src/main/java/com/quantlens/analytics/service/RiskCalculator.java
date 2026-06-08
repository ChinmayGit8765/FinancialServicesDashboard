package com.quantlens.analytics.service;

import com.quantlens.analytics.api.RiskScorecardDto;
import com.quantlens.marketdata.domain.FactorReturnRepository;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Computes risk scorecard metrics for a portfolio.
 * <p>
 * Metrics: annualized Sharpe ratio, annualized volatility, max drawdown,
 * beta vs SPX500 benchmark, historical VaR (95% 1-day), and parametric Gaussian VaR.
 * All statistical quantities are {@code double}; monetary VaR amounts are {@code BigDecimal}.
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only (per {@code analytics/package-info.java} allowedDependencies).
 */
@Service
@Transactional(readOnly = true)
public class RiskCalculator {

    private final PositionRepository positionRepository;
    private final OhlcvBarRepository ohlcvBarRepository;
    private final SecurityRepository securityRepository;
    private final FactorReturnRepository factorReturnRepository;

    public RiskCalculator(PositionRepository positionRepository,
                          OhlcvBarRepository ohlcvBarRepository,
                          SecurityRepository securityRepository,
                          FactorReturnRepository factorReturnRepository) {
        this.positionRepository = positionRepository;
        this.ohlcvBarRepository = ohlcvBarRepository;
        this.securityRepository = securityRepository;
        this.factorReturnRepository = factorReturnRepository;
    }

    /**
     * Computes the risk scorecard for the given portfolio.
     * <p>
     * Real implementation lands in Plan 04-02 (Hipparchus Sharpe/vol/drawdown/beta/VaR).
     *
     * @param portfolioId the portfolio to score
     * @return a stub RiskScorecardDto with zeroed fields
     */
    // STUB: real implementation in Plan 04-02
    public RiskScorecardDto computeRiskScorecard(Long portfolioId) {
        return new RiskScorecardDto(0.0, 0.0, 0.0, 0.0, List.of());
    }
}
