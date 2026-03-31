package com.nuvixtech.stockvaluator.ingestion.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nuvixtech.stockvaluator.ingestion.dto.fmp.*;
import com.nuvixtech.stockvaluator.ingestion.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Transforms FMP API DTOs into normalized JPA entities.
 * <p>
 * Key normalizations:
 * - CapEx: FMP returns negative values → stored as positive
 * - CalendarYear: extracted from date string or calendarYear field
 * - Raw JSON: preserved as backup in JSONB column
 */
@Component
public class FinancialDataMapper {

    private static final Logger log = LoggerFactory.getLogger(FinancialDataMapper.class);
    private final ObjectMapper objectMapper;

    public FinancialDataMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Maps FMP Income Statement → FinancialStatement entity (type INCOME).
     */
    public FinancialStatement toIncomeEntity(FmpIncomeStatement dto, Company company) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.INCOME);

        entity.setRevenue(BigDecimal.valueOf(dto.revenue()));
        entity.setOperatingIncome(BigDecimal.valueOf(dto.operatingIncome()));
        entity.setNetIncome(BigDecimal.valueOf(dto.netIncome()));
        entity.setEbitda(BigDecimal.valueOf(dto.ebitda()));
        entity.setInterestExpense(BigDecimal.valueOf(Math.abs(dto.interestExpense())));
        entity.setIncomeTaxExpense(BigDecimal.valueOf(Math.abs(dto.incomeTaxExpense())));
        entity.setSharesOutstanding(dto.sharesOutstanding());
        entity.setRawData(toJson(dto));

        return entity;
    }

    /**
     * Maps FMP Balance Sheet → FinancialStatement entity (type BALANCE).
     */
    public FinancialStatement toBalanceEntity(FmpBalanceSheet dto, Company company) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.BALANCE);

        entity.setTotalDebt(BigDecimal.valueOf(dto.totalDebt()));
        entity.setCashAndEquivalents(BigDecimal.valueOf(dto.cashAndCashEquivalents()));
        entity.setTotalEquity(BigDecimal.valueOf(dto.totalEquity()));
        entity.setTotalAssets(BigDecimal.valueOf(dto.totalAssets()));
        entity.setRawData(toJson(dto));

        return entity;
    }

    /**
     * Maps FMP Cash Flow Statement → FinancialStatement entity (type CASHFLOW).
     * CapEx is normalized to positive value.
     */
    public FinancialStatement toCashFlowEntity(FmpCashFlowStatement dto, Company company) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.CASHFLOW);

        entity.setOperatingCashFlow(BigDecimal.valueOf(dto.operatingCashFlow()));
        // FMP returns CapEx as negative → normalize to positive
        entity.setCapitalExpenditure(BigDecimal.valueOf(Math.abs(dto.capitalExpenditure())));
        entity.setFreeCashFlow(BigDecimal.valueOf(dto.freeCashFlow()));
        entity.setRawData(toJson(dto));

        return entity;
    }

    /**
     * Maps FMP Profile + Quote → Company entity (create or update).
     */
    public Company toCompanyEntity(FmpCompanyProfile profile) {
        var company = new Company(profile.symbol(), profile.companyName());
        company.setSector(profile.sector());
        company.setIndustry(profile.industry());
        company.setExchange(profile.exchange());
        company.setCurrency(profile.currency());
        return company;
    }

    /**
     * Maps FMP Profile → MarketData entity.
     */
    public MarketData toMarketDataEntity(FmpCompanyProfile profile, Company company) {
        var marketData = new MarketData(company, profile.price());
        marketData.setMarketCap(BigDecimal.valueOf(profile.marketCap()));
        marketData.setBeta(profile.beta());
        return marketData;
    }

    // --- Private helpers ---

    private int extractYear(String calendarYear, String date) {
        if (calendarYear != null && !calendarYear.isBlank()) {
            try {
                return Integer.parseInt(calendarYear);
            } catch (NumberFormatException e) {
                log.warn("Could not parse calendarYear '{}', falling back to date", calendarYear);
            }
        }
        // Fallback: extract year from date "2024-09-28"
        if (date != null && date.length() >= 4) {
            return Integer.parseInt(date.substring(0, 4));
        }
        throw new IllegalArgumentException("Cannot extract year from calendarYear=%s, date=%s"
                .formatted(calendarYear, date));
    }

    private String toJson(Object dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize DTO to JSON: {}", e.getMessage());
            return "{}";
        }
    }
}
