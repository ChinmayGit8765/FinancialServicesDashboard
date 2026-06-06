package com.quantlens.portfolio.domain;

import com.quantlens.marketdata.domain.Security;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * JPA entity for the {@code positions} table.
 * <p>
 * Current holdings for a portfolio.  All quantity and cost-basis fields are
 * {@link BigDecimal} mapped to NUMERIC columns — never {@code double}.
 */
@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(name = "avg_cost_basis", nullable = false, precision = 18, scale = 6)
    private BigDecimal avgCostBasis;

    // ── constructors ──────────────────────────────────────────────────────────

    protected Position() {
    }

    public Position(Portfolio portfolio, Security security,
                    BigDecimal quantity, BigDecimal avgCostBasis) {
        this.portfolio = portfolio;
        this.security = security;
        this.quantity = quantity;
        this.avgCostBasis = avgCostBasis;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getAvgCostBasis() { return avgCostBasis; }
    public void setAvgCostBasis(BigDecimal avgCostBasis) { this.avgCostBasis = avgCostBasis; }
}
