package com.nuvixtech.stockvaluator.ingestion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fcf_estimate",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_fcf_estimate",
           columnNames = {"company_id", "fiscal_year"}
       ))
public class FcfEstimate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "estimated_fcf", nullable = false, precision = 20, scale = 2)
    private BigDecimal estimatedFcf;

    @Column(name = "source", nullable = false, length = 50)
    private String source = "MANUAL";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected FcfEstimate() {}

    public FcfEstimate(Company company, Integer fiscalYear, BigDecimal estimatedFcf) {
        this.company = company;
        this.fiscalYear = fiscalYear;
        this.estimatedFcf = estimatedFcf;
    }

    public Long getId() { return id; }
    public Company getCompany() { return company; }
    public Integer getFiscalYear() { return fiscalYear; }
    public BigDecimal getEstimatedFcf() { return estimatedFcf; }
    public void setEstimatedFcf(BigDecimal estimatedFcf) { this.estimatedFcf = estimatedFcf; }
    public String getSource() { return source; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
