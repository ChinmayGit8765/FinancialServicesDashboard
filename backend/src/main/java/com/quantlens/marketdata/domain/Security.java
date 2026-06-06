package com.quantlens.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code securities} table.
 * <p>
 * Represents a tradable equity (or a benchmark pseudo-security when
 * {@code isBenchmark} is {@code true}).  All price/quantity fields use
 * {@link java.math.BigDecimal} mapped to NUMERIC columns — never {@code double}.
 */
@Entity
@Table(name = "securities")
public class Security {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String ticker;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String sector;

    @Column(name = "is_benchmark", nullable = false)
    private boolean isBenchmark = false;

    // ── constructors ──────────────────────────────────────────────────────────

    protected Security() {
    }

    public Security(String ticker, String name, String sector, boolean isBenchmark) {
        this.ticker = ticker;
        this.name = name;
        this.sector = sector;
        this.isBenchmark = isBenchmark;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getTicker() { return ticker; }
    public void setTicker(String ticker) { this.ticker = ticker; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSector() { return sector; }
    public void setSector(String sector) { this.sector = sector; }

    public boolean isIsBenchmark() { return isBenchmark; }
    public void setIsBenchmark(boolean isBenchmark) { this.isBenchmark = isBenchmark; }
}
