package com.nuvixtech.stockvaluator.ingestion.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "financial_statement",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_statement",
           columnNames = {"company_id", "fiscal_year", "period", "statement_type"}
       ))
public class FinancialStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(nullable = false, length = 10)
    private String period = "FY";

    @Column(name = "statement_type", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private StatementType statementType;

    // --- Normalized financial fields ---

    private BigDecimal revenue;

    @Column(name = "operating_income")
    private BigDecimal operatingIncome;

    @Column(name = "net_income")
    private BigDecimal netIncome;

    private BigDecimal ebit;
    private BigDecimal ebitda;

    @Column(name = "interest_expense")
    private BigDecimal interestExpense;

    @Column(name = "income_tax_expense")
    private BigDecimal incomeTaxExpense;

    @Column(name = "total_debt")
    private BigDecimal totalDebt;

    @Column(name = "cash_and_equivalents")
    private BigDecimal cashAndEquivalents;

    @Column(name = "total_equity")
    private BigDecimal totalEquity;

    @Column(name = "total_assets")
    private BigDecimal totalAssets;

    @Column(name = "operating_cash_flow")
    private BigDecimal operatingCashFlow;

    @Column(name = "capital_expenditure")
    private BigDecimal capitalExpenditure;

    @Column(name = "free_cash_flow")
    private BigDecimal freeCashFlow;

    @Column(name = "shares_outstanding")
    private Long sharesOutstanding;

    @Column(name = "operating_lease_obligations")
    private BigDecimal operatingLeaseObligations;

    @Column(name = "pension_liabilities")
    private BigDecimal pensionLiabilities;

    @Column(name = "minority_interest")
    private BigDecimal minorityInterest;

    @Column(name = "raw_data", columnDefinition = "jsonb")
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    private String rawData;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt = LocalDateTime.now();

    protected FinancialStatement() {} // JPA

    public FinancialStatement(Company company, Integer fiscalYear, StatementType statementType) {
        this.company = company;
        this.fiscalYear = fiscalYear;
        this.statementType = statementType;
    }

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public Company getCompany() { return company; }

    public Integer getFiscalYear() { return fiscalYear; }
    public void setFiscalYear(Integer fiscalYear) { this.fiscalYear = fiscalYear; }

    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }

    public StatementType getStatementType() { return statementType; }

    public BigDecimal getRevenue() { return revenue; }
    public void setRevenue(BigDecimal revenue) { this.revenue = revenue; }

    public BigDecimal getOperatingIncome() { return operatingIncome; }
    public void setOperatingIncome(BigDecimal operatingIncome) { this.operatingIncome = operatingIncome; }

    public BigDecimal getNetIncome() { return netIncome; }
    public void setNetIncome(BigDecimal netIncome) { this.netIncome = netIncome; }

    public BigDecimal getEbit() { return ebit; }
    public void setEbit(BigDecimal ebit) { this.ebit = ebit; }

    public BigDecimal getEbitda() { return ebitda; }
    public void setEbitda(BigDecimal ebitda) { this.ebitda = ebitda; }

    public BigDecimal getInterestExpense() { return interestExpense; }
    public void setInterestExpense(BigDecimal interestExpense) { this.interestExpense = interestExpense; }

    public BigDecimal getIncomeTaxExpense() { return incomeTaxExpense; }
    public void setIncomeTaxExpense(BigDecimal incomeTaxExpense) { this.incomeTaxExpense = incomeTaxExpense; }

    public BigDecimal getTotalDebt() { return totalDebt; }
    public void setTotalDebt(BigDecimal totalDebt) { this.totalDebt = totalDebt; }

    public BigDecimal getCashAndEquivalents() { return cashAndEquivalents; }
    public void setCashAndEquivalents(BigDecimal cashAndEquivalents) { this.cashAndEquivalents = cashAndEquivalents; }

    public BigDecimal getTotalEquity() { return totalEquity; }
    public void setTotalEquity(BigDecimal totalEquity) { this.totalEquity = totalEquity; }

    public BigDecimal getTotalAssets() { return totalAssets; }
    public void setTotalAssets(BigDecimal totalAssets) { this.totalAssets = totalAssets; }

    public BigDecimal getOperatingCashFlow() { return operatingCashFlow; }
    public void setOperatingCashFlow(BigDecimal operatingCashFlow) { this.operatingCashFlow = operatingCashFlow; }

    public BigDecimal getCapitalExpenditure() { return capitalExpenditure; }
    public void setCapitalExpenditure(BigDecimal capitalExpenditure) { this.capitalExpenditure = capitalExpenditure; }

    public BigDecimal getFreeCashFlow() { return freeCashFlow; }
    public void setFreeCashFlow(BigDecimal freeCashFlow) { this.freeCashFlow = freeCashFlow; }

    public Long getSharesOutstanding() { return sharesOutstanding; }
    public void setSharesOutstanding(Long sharesOutstanding) { this.sharesOutstanding = sharesOutstanding; }

    public BigDecimal getOperatingLeaseObligations() { return operatingLeaseObligations; }
    public void setOperatingLeaseObligations(BigDecimal v) { this.operatingLeaseObligations = v; }

    public BigDecimal getPensionLiabilities() { return pensionLiabilities; }
    public void setPensionLiabilities(BigDecimal v) { this.pensionLiabilities = v; }

    public BigDecimal getMinorityInterest() { return minorityInterest; }
    public void setMinorityInterest(BigDecimal v) { this.minorityInterest = v; }

    public String getRawData() { return rawData; }
    public void setRawData(String rawData) { this.rawData = rawData; }

    public LocalDateTime getFetchedAt() { return fetchedAt; }
}
