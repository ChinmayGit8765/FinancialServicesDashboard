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
import java.time.LocalDate;

/**
 * JPA entity for the {@code transactions} table.
 * <p>
 * BUY / SELL transaction history per portfolio.  All price and quantity fields
 * are {@link BigDecimal} — never {@code double}.
 */
@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "portfolio_id", nullable = false)
    private Portfolio portfolio;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "security_id", nullable = false)
    private Security security;

    @Column(name = "tx_date", nullable = false)
    private LocalDate txDate;

    /** Either "BUY" or "SELL" — enforced by a CHECK constraint in V1__schema.sql. */
    @Column(name = "tx_type", nullable = false, length = 10)
    private String txType;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 6)
    private BigDecimal price;

    // ── constructors ──────────────────────────────────────────────────────────

    protected Transaction() {
    }

    public Transaction(Portfolio portfolio, Security security,
                       LocalDate txDate, String txType,
                       BigDecimal quantity, BigDecimal price) {
        this.portfolio = portfolio;
        this.security = security;
        this.txDate = txDate;
        this.txType = txType;
        this.quantity = quantity;
        this.price = price;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public Portfolio getPortfolio() { return portfolio; }
    public void setPortfolio(Portfolio portfolio) { this.portfolio = portfolio; }

    public Security getSecurity() { return security; }
    public void setSecurity(Security security) { this.security = security; }

    public LocalDate getTxDate() { return txDate; }
    public void setTxDate(LocalDate txDate) { this.txDate = txDate; }

    public String getTxType() { return txType; }
    public void setTxType(String txType) { this.txType = txType; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
}
