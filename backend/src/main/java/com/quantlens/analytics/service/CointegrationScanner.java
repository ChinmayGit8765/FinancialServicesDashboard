package com.quantlens.analytics.service;

import com.quantlens.analytics.api.PairResultDto;
import com.quantlens.marketdata.domain.OhlcvBarRepository;
import com.quantlens.marketdata.domain.SecurityRepository;
import com.quantlens.portfolio.domain.PositionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Engle-Granger 2-step cointegration pairs scanner for a portfolio.
 * <p>
 * Step 1: OLS hedge ratio {@code y = α + β·x + ε} on log-price series.
 * Step 2: ADF test on the residual spread using Hipparchus OLS + MacKinnon (1994/2010)
 * p-value approximation. Candidate pairs are bounded to same-sector holdings (max 20 pairs)
 * to keep scan time demo-fast.
 * <p>
 * Module dependency: reads from {@code marketdata::domain} and {@code portfolio::domain}
 * named interfaces only.
 */
@Service
@Transactional(readOnly = true)
public class CointegrationScanner {

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
     * Real implementation lands in Plan 04-03 (Engle-Granger ADF with MacKinnon p-values).
     *
     * @param portfolioId the portfolio to scan
     * @return an empty list (stub — no pairs until Plan 04-03 implements the math)
     */
    // STUB: Plan 04-03
    public List<PairResultDto> scanPairs(Long portfolioId) {
        return List.of();
    }
}
