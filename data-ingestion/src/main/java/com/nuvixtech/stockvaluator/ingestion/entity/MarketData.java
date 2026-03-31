package com.nuvixtech.stockvaluator.ingestion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_data")
public class MarketData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(name = "market_cap")
    private BigDecimal marketCap;

    private BigDecimal beta;

    @Column(name = "pe_ratio")
    private BigDecimal peRatio;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    protected MarketData() {} // JPA

    public MarketData(Company company, BigDecimal price) {
        this.company = company;
        this.price = price;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public Company getCompany() { return company; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getMarketCap() { return marketCap; }
    public void setMarketCap(BigDecimal marketCap) { this.marketCap = marketCap; }

    public BigDecimal getBeta() { return beta; }
    public void setBeta(BigDecimal beta) { this.beta = beta; }

    public BigDecimal getPeRatio() { return peRatio; }
    public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
}
