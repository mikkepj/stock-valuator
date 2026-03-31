package com.nuvixtech.stockvaluator.api.valuation.service;

import com.nuvixtech.stockvaluator.api.exception.InsufficientDataException;
import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.mapper.ValuationMapper;
import com.nuvixtech.stockvaluator.api.valuation.repository.ValuationResultRepository;
import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.StatementType;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FinancialStatementRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.MarketDataRepository;
import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService;
import com.nuvixtech.stockvaluator.valuation.CompanyFinancials;
import com.nuvixtech.stockvaluator.valuation.DcfCalculator;
import com.nuvixtech.stockvaluator.valuation.DcfParameters;
import com.nuvixtech.stockvaluator.valuation.SensitivityAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ValuationService {

    private static final Logger log = LoggerFactory.getLogger(ValuationService.class);

    private final ValuationResultRepository valuationRepository;
    private final FinancialStatementRepository statementRepository;
    private final MarketDataRepository marketDataRepository;
    private final CompanyRepository companyRepository;
    private final FinancialDataService financialDataService;
    private final DcfCalculator dcfCalculator;
    private final SensitivityAnalyzer sensitivityAnalyzer;
    private final ValuationMapper mapper;

    private final BigDecimal riskFreeRate;
    private final BigDecimal marketRiskPremium;
    private final BigDecimal terminalGrowthRate;
    private final int projectionYears;

    public ValuationService(
            ValuationResultRepository valuationRepository,
            FinancialStatementRepository statementRepository,
            MarketDataRepository marketDataRepository,
            CompanyRepository companyRepository,
            FinancialDataService financialDataService,
            DcfCalculator dcfCalculator,
            SensitivityAnalyzer sensitivityAnalyzer,
            ValuationMapper mapper,
            @Value("${stockvaluator.dcf.risk-free-rate:0.045}") BigDecimal riskFreeRate,
            @Value("${stockvaluator.dcf.market-risk-premium:0.055}") BigDecimal marketRiskPremium,
            @Value("${stockvaluator.dcf.terminal-growth-rate:0.025}") BigDecimal terminalGrowthRate,
            @Value("${stockvaluator.dcf.projection-years:10}") int projectionYears) {
        this.valuationRepository = valuationRepository;
        this.statementRepository = statementRepository;
        this.marketDataRepository = marketDataRepository;
        this.companyRepository = companyRepository;
        this.financialDataService = financialDataService;
        this.dcfCalculator = dcfCalculator;
        this.sensitivityAnalyzer = sensitivityAnalyzer;
        this.mapper = mapper;
        this.riskFreeRate = riskFreeRate;
        this.marketRiskPremium = marketRiskPremium;
        this.terminalGrowthRate = terminalGrowthRate;
        this.projectionYears = projectionYears;
    }

    /**
     * Retorna la valuación más reciente cacheada. Lanza TickerNotFoundException si no hay datos.
     */
    @Cacheable(value = "valuations", key = "#ticker.toUpperCase()")
    public ValuationResponse getLatestValuation(String ticker) {
        String t = ticker.toUpperCase();
        log.debug("getLatestValuation: {}", t);
        var entity = valuationRepository.findFirstByCompanyTickerOrderByCalculatedAtDesc(t)
                .orElseThrow(() -> new TickerNotFoundException(t));
        return mapper.toResponse(entity);
    }

    /**
     * Fuerza un recálculo: ingesta si necesario, corre el engine, persiste y limpia cache.
     */
    @CacheEvict(value = "valuations", key = "#ticker.toUpperCase()")
    @Transactional
    public ValuationResponse calculate(String ticker) {
        String t = ticker.toUpperCase();
        log.info("calculate: {}", t);

        // Ingestar si la empresa no existe aún
        if (companyRepository.findByTicker(t).isEmpty()) {
            log.info("Ticker {} no encontrado, ejecutando ingestion previa", t);
            financialDataService.ingest(t);
        }

        var company = companyRepository.findByTicker(t)
                .orElseThrow(() -> new TickerNotFoundException(t));

        var financials = buildCompanyFinancials(t);

        var marketData = marketDataRepository.findTopByCompanyTickerOrderByFetchedAtDesc(t)
                .orElseThrow(() -> new InsufficientDataException(t, "sin datos de mercado"));

        var params = new DcfParameters(
                riskFreeRate, marketRiskPremium, terminalGrowthRate,
                projectionYears, marketData.getPrice()
        );

        var result = dcfCalculator.calculate(financials, params);
        var sensitivityMatrix = sensitivityAnalyzer.analyze(financials, params, dcfCalculator);

        // Reconstruir result con la sensitivity matrix completa
        var resultWithSensitivity = new com.nuvixtech.stockvaluator.valuation.ValuationResult(
                result.ticker(), result.intrinsicValuePerShare(), result.marketPrice(),
                result.marginOfSafety(), result.verdict(), result.wacc(),
                result.terminalGrowthRate(), result.projectionYears(), result.terminalValue(),
                result.netDebt(), result.projectedFcfs(), sensitivityMatrix, result.breakdown()
        );

        var entity = mapper.toEntity(resultWithSensitivity, company);
        var saved = valuationRepository.save(entity);
        log.info("Valuación guardada para {}: IV={}, verdict={}",
                t, result.intrinsicValuePerShare(), result.verdict());

        return mapper.toResponse(saved);
    }

    /**
     * Construye el input del engine a partir de los datos persistidos en DB.
     * Combina datos de CASHFLOW, BALANCE e INCOME en orden ascendente por año.
     */
    private CompanyFinancials buildCompanyFinancials(String ticker) {
        var cashFlows = statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                ticker, StatementType.CASHFLOW);
        var balances = statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                ticker, StatementType.BALANCE);
        var incomes = statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                ticker, StatementType.INCOME);

        if (cashFlows.isEmpty()) {
            throw new InsufficientDataException(ticker, "sin estados de flujo de caja");
        }

        // FCF histórico en orden ascendente (más antiguo primero)
        var historicalFcf = cashFlows.reversed().stream()
                .map(FinancialStatement::getFreeCashFlow)
                .filter(fcf -> fcf != null && fcf.compareTo(BigDecimal.ZERO) != 0)
                .toList();

        if (historicalFcf.isEmpty()) {
            throw new InsufficientDataException(ticker, "FCF histórico no disponible o cero");
        }

        // Tomar el balance más reciente
        var latestBalance = balances.isEmpty() ? null : balances.get(0);
        // Tomar el income más reciente
        var latestIncome = incomes.isEmpty() ? null : incomes.get(0);
        // Tomar el cashflow más reciente para beta y shares
        var latestCashFlow = cashFlows.get(0);

        return new CompanyFinancials(
                ticker,
                historicalFcf,
                safeValue(latestBalance, FinancialStatement::getTotalDebt),
                safeValue(latestBalance, FinancialStatement::getCashAndEquivalents),
                safeValue(latestBalance, FinancialStatement::getTotalEquity),
                safeValue(latestIncome, FinancialStatement::getInterestExpense),
                safeValue(latestIncome, FinancialStatement::getIncomeTaxExpense),
                new BigDecimal("1.0"), // beta se obtiene de market_data, no de financial_statement
                latestCashFlow.getSharesOutstanding() != null ? latestCashFlow.getSharesOutstanding() : 1_000_000_000L,
                safeValue(latestIncome, FinancialStatement::getEbitda)
        );
    }

    private BigDecimal safeValue(FinancialStatement stmt,
                                  java.util.function.Function<FinancialStatement, BigDecimal> getter) {
        if (stmt == null) return BigDecimal.ZERO;
        var val = getter.apply(stmt);
        return val != null ? val : BigDecimal.ZERO;
    }
}
