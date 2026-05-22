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
     * {@code fxRateToUsd}: multiply monetary fields by this rate to convert to USD (1.0 for USD companies).
     */
    public FinancialStatement toIncomeEntity(FmpIncomeStatement dto, Company company, BigDecimal fxRateToUsd) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.INCOME);

        entity.setRevenue(convert(dto.revenue(), fxRateToUsd));
        entity.setOperatingIncome(convert(dto.operatingIncome(), fxRateToUsd));
        entity.setNetIncome(convert(dto.netIncome(), fxRateToUsd));
        entity.setEbitda(convert(dto.ebitda(), fxRateToUsd));
        entity.setInterestExpense(convert(Math.abs(dto.interestExpense()), fxRateToUsd));
        entity.setIncomeTaxExpense(convert(Math.abs(dto.incomeTaxExpense()), fxRateToUsd));
        entity.setSharesOutstanding(dto.sharesOutstanding()); // unidades, no se convierten
        entity.setRawData(toJson(dto));

        return entity;
    }

    /**
     * Maps FMP Balance Sheet → FinancialStatement entity (type BALANCE).
     * {@code fxRateToUsd}: multiply monetary fields by this rate to convert to USD (1.0 for USD companies).
     */
    public FinancialStatement toBalanceEntity(FmpBalanceSheet dto, Company company, BigDecimal fxRateToUsd) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.BALANCE);

        entity.setTotalDebt(convert(dto.totalDebt(), fxRateToUsd));
        entity.setCashAndEquivalents(convert(dto.cashAndCashEquivalents(), fxRateToUsd));
        entity.setTotalEquity(convert(dto.totalEquity(), fxRateToUsd));
        entity.setTotalAssets(convert(dto.totalAssets(), fxRateToUsd));
        entity.setRawData(toJson(dto));

        return entity;
    }

    /**
     * Maps FMP Cash Flow Statement → FinancialStatement entity (type CASHFLOW).
     * CapEx is normalized to positive value.
     * {@code fxRateToUsd}: multiply monetary fields by this rate to convert to USD (1.0 for USD companies).
     */
    public FinancialStatement toCashFlowEntity(FmpCashFlowStatement dto, Company company, BigDecimal fxRateToUsd) {
        int year = extractYear(dto.calendarYear(), dto.date());
        var entity = new FinancialStatement(company, year, StatementType.CASHFLOW);

        entity.setOperatingCashFlow(convert(dto.operatingCashFlow(), fxRateToUsd));
        // FMP returns CapEx as negative → normalize to positive
        entity.setCapitalExpenditure(convert(Math.abs(dto.capitalExpenditure()), fxRateToUsd));
        entity.setFreeCashFlow(convert(dto.freeCashFlow(), fxRateToUsd));
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

    private BigDecimal convert(long rawValue, BigDecimal fxRateToUsd) {
        return BigDecimal.valueOf(rawValue).multiply(fxRateToUsd, java.math.MathContext.DECIMAL128);
    }

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
