package com.quantlens.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * JPA entity for the {@code factor_returns} table.
 * <p>
 * Daily Fama-French 3-factor return series (Mkt-RF, SMB, HML, Rf).
 * All factor values are {@link BigDecimal} mapped to {@code NUMERIC(10,6)}.
 */
@Entity
@Table(name = "factor_returns")
public class FactorReturn {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "factor_date", nullable = false, unique = true)
    private LocalDate factorDate;

    /** Market excess return (Mkt - Rf). */
    @Column(name = "mkt_rf", nullable = false, precision = 10, scale = 6)
    private BigDecimal mktRf;

    /** Small-minus-big size factor. */
    @Column(name = "smb", nullable = false, precision = 10, scale = 6)
    private BigDecimal smb;

    /** High-minus-low value factor. */
    @Column(name = "hml", nullable = false, precision = 10, scale = 6)
    private BigDecimal hml;

    /** Risk-free rate (daily). */
    @Column(name = "rf", nullable = false, precision = 10, scale = 6)
    private BigDecimal rf = BigDecimal.ZERO;

    // ── constructors ──────────────────────────────────────────────────────────

    protected FactorReturn() {
    }

    public FactorReturn(LocalDate factorDate, BigDecimal mktRf,
                        BigDecimal smb, BigDecimal hml, BigDecimal rf) {
        this.factorDate = factorDate;
        this.mktRf = mktRf;
        this.smb = smb;
        this.hml = hml;
        this.rf = rf;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public LocalDate getFactorDate() { return factorDate; }
    public void setFactorDate(LocalDate factorDate) { this.factorDate = factorDate; }

    public BigDecimal getMktRf() { return mktRf; }
    public void setMktRf(BigDecimal mktRf) { this.mktRf = mktRf; }

    public BigDecimal getSmb() { return smb; }
    public void setSmb(BigDecimal smb) { this.smb = smb; }

    public BigDecimal getHml() { return hml; }
    public void setHml(BigDecimal hml) { this.hml = hml; }

    public BigDecimal getRf() { return rf; }
    public void setRf(BigDecimal rf) { this.rf = rf; }
}
