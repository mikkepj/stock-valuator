package com.nuvixtech.stockvaluator.api.valuation.entity;

import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "valuation_result")
public class ValuationResultEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @Column(name = "intrinsic_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal intrinsicValue;

    @Column(name = "market_price", nullable = false, precision = 12, scale = 4)
    private BigDecimal marketPrice;

    @Column(name = "margin_of_safety", nullable = false, precision = 8, scale = 4)
    private BigDecimal marginOfSafety;

    @Column(name = "verdict", nullable = false, length = 20)
    private String verdict;

    @Column(name = "wacc", precision = 8, scale = 6)
    private BigDecimal wacc;

    @Column(name = "terminal_growth", precision = 8, scale = 6)
    private BigDecimal terminalGrowth;

    @Column(name = "projection_years")
    private Integer projectionYears;

    @Column(name = "terminal_value", precision = 20, scale = 2)
    private BigDecimal terminalValue;

    @Column(name = "net_debt", precision = 20, scale = 2)
    private BigDecimal netDebt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sensitivity_matrix", columnDefinition = "jsonb")
    private Map<String, Map<String, BigDecimal>> sensitivityMatrix;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "breakdown", columnDefinition = "jsonb")
    private Map<String, BigDecimal> breakdown;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scenarios", columnDefinition = "jsonb")
    private List<Map<String, Object>> scenarios;

    @PrePersist
    void prePersist() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
    }

    // --- Getters y setters ---

    public Long getId() { return id; }

    public Company getCompany() { return company; }
    public void setCompany(Company company) { this.company = company; }

    public LocalDateTime getCalculatedAt() { return calculatedAt; }
    public void setCalculatedAt(LocalDateTime calculatedAt) { this.calculatedAt = calculatedAt; }

    public BigDecimal getIntrinsicValue() { return intrinsicValue; }
    public void setIntrinsicValue(BigDecimal intrinsicValue) { this.intrinsicValue = intrinsicValue; }

    public BigDecimal getMarketPrice() { return marketPrice; }
    public void setMarketPrice(BigDecimal marketPrice) { this.marketPrice = marketPrice; }

    public BigDecimal getMarginOfSafety() { return marginOfSafety; }
    public void setMarginOfSafety(BigDecimal marginOfSafety) { this.marginOfSafety = marginOfSafety; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public BigDecimal getWacc() { return wacc; }
    public void setWacc(BigDecimal wacc) { this.wacc = wacc; }

    public BigDecimal getTerminalGrowth() { return terminalGrowth; }
    public void setTerminalGrowth(BigDecimal terminalGrowth) { this.terminalGrowth = terminalGrowth; }

    public Integer getProjectionYears() { return projectionYears; }
    public void setProjectionYears(Integer projectionYears) { this.projectionYears = projectionYears; }

    public BigDecimal getTerminalValue() { return terminalValue; }
    public void setTerminalValue(BigDecimal terminalValue) { this.terminalValue = terminalValue; }

    public BigDecimal getNetDebt() { return netDebt; }
    public void setNetDebt(BigDecimal netDebt) { this.netDebt = netDebt; }

    public Map<String, Map<String, BigDecimal>> getSensitivityMatrix() { return sensitivityMatrix; }
    public void setSensitivityMatrix(Map<String, Map<String, BigDecimal>> sensitivityMatrix) {
        this.sensitivityMatrix = sensitivityMatrix;
    }

    public Map<String, BigDecimal> getBreakdown() { return breakdown; }
    public void setBreakdown(Map<String, BigDecimal> breakdown) { this.breakdown = breakdown; }

    public List<Map<String, Object>> getScenarios() { return scenarios; }
    public void setScenarios(List<Map<String, Object>> scenarios) { this.scenarios = scenarios; }
}
