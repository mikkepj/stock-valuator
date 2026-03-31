package com.nuvixtech.stockvaluator.ingestion.service;

import com.nuvixtech.stockvaluator.ingestion.client.FmpApiClient;
import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.StatementType;
import com.nuvixtech.stockvaluator.ingestion.mapper.FinancialDataMapper;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FinancialStatementRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.MarketDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the full ingestion flow for a given ticker:
 * 1. Fetch company profile → upsert Company
 * 2. Fetch financial statements (income, balance, cashflow) → persist
 * 3. Fetch market data → persist snapshot
 */
@Service
public class FinancialDataService {

    private static final Logger log = LoggerFactory.getLogger(FinancialDataService.class);
    private static final int DEFAULT_YEARS = 5;

    private final FmpApiClient fmpClient;
    private final FinancialDataMapper mapper;
    private final CompanyRepository companyRepository;
    private final FinancialStatementRepository statementRepository;
    private final MarketDataRepository marketDataRepository;

    public FinancialDataService(FmpApiClient fmpClient,
                                FinancialDataMapper mapper,
                                CompanyRepository companyRepository,
                                FinancialStatementRepository statementRepository,
                                MarketDataRepository marketDataRepository) {
        this.fmpClient = fmpClient;
        this.mapper = mapper;
        this.companyRepository = companyRepository;
        this.statementRepository = statementRepository;
        this.marketDataRepository = marketDataRepository;
    }

    /**
     * Full ingestion pipeline for a single ticker.
     * Returns the persisted Company entity.
     */
    @Transactional
    public IngestionResult ingest(String ticker) {
        String normalizedTicker = ticker.trim().toUpperCase();
        log.info("Starting ingestion for {}", normalizedTicker);

        // 1. Fetch and upsert company profile
        var profile = fmpClient.getCompanyProfile(normalizedTicker)
                .orElseThrow(() -> new IngestionException(
                        "Company profile not found for ticker: " + normalizedTicker));

        Company company = companyRepository.findByTicker(normalizedTicker)
                .map(existing -> updateCompany(existing, profile))
                .orElseGet(() -> companyRepository.save(mapper.toCompanyEntity(profile)));

        log.info("Company saved: {} ({})", company.getName(), company.getTicker());

        // 2. Fetch and persist financial statements
        int incomeCount = ingestIncomeStatements(normalizedTicker, company);
        int balanceCount = ingestBalanceSheets(normalizedTicker, company);
        int cashFlowCount = ingestCashFlowStatements(normalizedTicker, company);

        // 3. Fetch and persist market data
        var marketData = mapper.toMarketDataEntity(profile, company);
        marketDataRepository.save(marketData);
        log.info("Market data saved: price={}, beta={}", marketData.getPrice(), marketData.getBeta());

        var result = new IngestionResult(
                normalizedTicker,
                company.getName(),
                incomeCount,
                balanceCount,
                cashFlowCount,
                marketData.getPrice().toPlainString()
        );

        log.info("Ingestion complete for {}: {}", normalizedTicker, result);
        return result;
    }

    // --- Private helpers ---

    private int ingestIncomeStatements(String ticker, Company company) {
        var statements = fmpClient.getIncomeStatements(ticker, DEFAULT_YEARS);
        int count = 0;
        for (var dto : statements) {
            var entity = mapper.toIncomeEntity(dto, company);
            upsertStatement(entity, company.getId());
            count++;
        }
        log.debug("Persisted {} income statements for {}", count, ticker);
        return count;
    }

    private int ingestBalanceSheets(String ticker, Company company) {
        var sheets = fmpClient.getBalanceSheets(ticker, DEFAULT_YEARS);
        int count = 0;
        for (var dto : sheets) {
            var entity = mapper.toBalanceEntity(dto, company);
            upsertStatement(entity, company.getId());
            count++;
        }
        log.debug("Persisted {} balance sheets for {}", count, ticker);
        return count;
    }

    private int ingestCashFlowStatements(String ticker, Company company) {
        var statements = fmpClient.getCashFlowStatements(ticker, DEFAULT_YEARS);
        int count = 0;
        for (var dto : statements) {
            var entity = mapper.toCashFlowEntity(dto, company);
            upsertStatement(entity, company.getId());
            count++;
        }
        log.debug("Persisted {} cash flow statements for {}", count, ticker);
        return count;
    }

    /**
     * Upsert logic: if a statement for the same company/year/period/type exists, update it.
     */
    private void upsertStatement(FinancialStatement newEntity, Long companyId) {
        statementRepository.findByCompanyIdAndFiscalYearAndPeriodAndStatementType(
                companyId, newEntity.getFiscalYear(), newEntity.getPeriod(), newEntity.getStatementType()
        ).ifPresentOrElse(
                existing -> {
                    copyFields(newEntity, existing);
                    statementRepository.save(existing);
                },
                () -> statementRepository.save(newEntity)
        );
    }

    private void copyFields(FinancialStatement from, FinancialStatement to) {
        to.setRevenue(from.getRevenue());
        to.setOperatingIncome(from.getOperatingIncome());
        to.setNetIncome(from.getNetIncome());
        to.setEbit(from.getEbit());
        to.setEbitda(from.getEbitda());
        to.setInterestExpense(from.getInterestExpense());
        to.setIncomeTaxExpense(from.getIncomeTaxExpense());
        to.setTotalDebt(from.getTotalDebt());
        to.setCashAndEquivalents(from.getCashAndEquivalents());
        to.setTotalEquity(from.getTotalEquity());
        to.setTotalAssets(from.getTotalAssets());
        to.setOperatingCashFlow(from.getOperatingCashFlow());
        to.setCapitalExpenditure(from.getCapitalExpenditure());
        to.setFreeCashFlow(from.getFreeCashFlow());
        to.setSharesOutstanding(from.getSharesOutstanding());
        to.setRawData(from.getRawData());
    }

    private Company updateCompany(Company existing,
                                  com.nuvixtech.stockvaluator.ingestion.dto.fmp.FmpCompanyProfile profile) {
        existing.setName(profile.companyName());
        existing.setSector(profile.sector());
        existing.setIndustry(profile.industry());
        existing.setExchange(profile.exchange());
        existing.setCurrency(profile.currency());
        return companyRepository.save(existing);
    }

    // --- Result record ---

    public record IngestionResult(
            String ticker,
            String companyName,
            int incomeStatements,
            int balanceSheets,
            int cashFlowStatements,
            String currentPrice
    ) {}

    // --- Exception ---

    public static class IngestionException extends RuntimeException {
        public IngestionException(String message) { super(message); }
    }
}
