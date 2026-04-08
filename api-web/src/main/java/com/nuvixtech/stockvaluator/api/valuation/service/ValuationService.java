package com.nuvixtech.stockvaluator.api.valuation.service;

import com.nuvixtech.stockvaluator.api.exception.InsufficientDataException;
import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.mapper.ValuationMapper;
import com.nuvixtech.stockvaluator.api.valuation.repository.ValuationResultRepository;
import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.StatementType;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FcfEstimateRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FinancialStatementRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.MarketDataRepository;
import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService;
import com.nuvixtech.stockvaluator.valuation.CompanyFinancials;
import com.nuvixtech.stockvaluator.valuation.DcfCalculator;
import com.nuvixtech.stockvaluator.valuation.DcfParameters;
import com.nuvixtech.stockvaluator.valuation.ScenarioAnalyzer;
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
    private final FcfEstimateRepository fcfEstimateRepository;
    private final FinancialDataService financialDataService;
    private final DcfCalculator dcfCalculator;
    private final SensitivityAnalyzer sensitivityAnalyzer;
    private final ScenarioAnalyzer scenarioAnalyzer;
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
            FcfEstimateRepository fcfEstimateRepository,
            FinancialDataService financialDataService,
            DcfCalculator dcfCalculator,
            SensitivityAnalyzer sensitivityAnalyzer,
            ScenarioAnalyzer scenarioAnalyzer,
            ValuationMapper mapper,
            @Value("${stockvaluator.dcf.risk-free-rate:0.045}") BigDecimal riskFreeRate,
            @Value("${stockvaluator.dcf.market-risk-premium:0.045}") BigDecimal marketRiskPremium,
            @Value("${stockvaluator.dcf.terminal-growth-rate:0.025}") BigDecimal terminalGrowthRate,
            @Value("${stockvaluator.dcf.projection-years:10}") int projectionYears) {
        this.valuationRepository = valuationRepository;
        this.statementRepository = statementRepository;
        this.marketDataRepository = marketDataRepository;
        this.companyRepository = companyRepository;
        this.fcfEstimateRepository = fcfEstimateRepository;
        this.financialDataService = financialDataService;
        this.dcfCalculator = dcfCalculator;
        this.sensitivityAnalyzer = sensitivityAnalyzer;
        this.scenarioAnalyzer = scenarioAnalyzer;
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
    @Transactional(readOnly = true)
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

        var marketData = marketDataRepository.findTopByCompanyTickerOrderByFetchedAtDesc(t)
                .orElseThrow(() -> new InsufficientDataException(t, "sin datos de mercado"));

        var financials = buildCompanyFinancials(t, marketData);

        var params = new DcfParameters(
                riskFreeRate, marketRiskPremium, terminalGrowthRate,
                projectionYears, marketData.getPrice()
        );

        var result = dcfCalculator.calculate(financials, params);
        var sensitivityMatrix = sensitivityAnalyzer.analyze(financials, params, dcfCalculator);
        var scenarios = scenarioAnalyzer.analyze(financials, params);

        // Reconstruir result con la sensitivity matrix completa
        var resultWithSensitivity = new com.nuvixtech.stockvaluator.valuation.ValuationResult(
                result.ticker(), result.intrinsicValuePerShare(), result.marketPrice(),
                result.marginOfSafety(), result.verdict(), result.wacc(),
                result.terminalGrowthRate(), result.projectionYears(), result.terminalValue(),
                result.netDebt(), result.projectedFcfs(), sensitivityMatrix, result.breakdown()
        );

        var entity = mapper.toEntity(resultWithSensitivity, scenarios, company);
        var saved = valuationRepository.save(entity);
        log.info("Valuación guardada para {}: IV={}, verdict={}",
                t, result.intrinsicValuePerShare(), result.verdict());

        return mapper.toResponse(saved);
    }

    /**
     * Construye el input del engine a partir de los datos persistidos en DB.
     * Combina datos de CASHFLOW, BALANCE e INCOME en orden ascendente por año.
     * El beta real viene de market_data.
     */
    private CompanyFinancials buildCompanyFinancials(String ticker,
            com.nuvixtech.stockvaluator.ingestion.entity.MarketData marketData) {
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

        // Estimaciones de analistas: se usan directamente para los primeros N años de proyección
        // (no se calcula CAGR sobre ellas, evitando el doble conteo de crecimiento)
        var fcfEstimates = fcfEstimateRepository.findByCompanyTickerOrderByFiscalYearAsc(ticker);
        var analystFcfEstimates = fcfEstimates.stream()
                .map(com.nuvixtech.stockvaluator.ingestion.entity.FcfEstimate::getEstimatedFcf)
                .toList();

        if (!analystFcfEstimates.isEmpty()) {
            log.info("Usando {} FCF estimates de analistas para {}", analystFcfEstimates.size(), ticker);
        }

        // Tomar el balance más reciente
        var latestBalance = balances.isEmpty() ? null : balances.get(0);
        // Tomar el income más reciente
        var latestIncome = incomes.isEmpty() ? null : incomes.get(0);
        // Tomar el cashflow más reciente para shares outstanding
        var latestCashFlow = cashFlows.get(0);

        // interestExpense e incomeTaxExpense: preferir INCOME, fallback a CASHFLOW
        BigDecimal interestExpense = safeValue(latestIncome, FinancialStatement::getInterestExpense);
        if (interestExpense.compareTo(BigDecimal.ZERO) == 0) {
            interestExpense = safeValue(latestCashFlow, FinancialStatement::getInterestExpense);
        }
        BigDecimal incomeTaxExpense = safeValue(latestIncome, FinancialStatement::getIncomeTaxExpense);
        if (incomeTaxExpense.compareTo(BigDecimal.ZERO) == 0) {
            incomeTaxExpense = safeValue(latestCashFlow, FinancialStatement::getIncomeTaxExpense);
        }

        // Beta real desde market_data; fallback a 1.0 si no está disponible
        BigDecimal beta = (marketData.getBeta() != null && marketData.getBeta().compareTo(BigDecimal.ZERO) > 0)
                ? marketData.getBeta()
                : BigDecimal.ONE;

        // sharesOutstanding desde CASHFLOW; fallback a INCOME
        Long shares = latestCashFlow.getSharesOutstanding();
        if (shares == null || shares == 0L) {
            shares = latestIncome != null ? latestIncome.getSharesOutstanding() : null;
        }
        if (shares == null || shares == 0L) {
            throw new InsufficientDataException(ticker, "shares outstanding no disponible");
        }

        log.debug("buildCompanyFinancials {}: beta={}, shares={}, interestExp={}, taxExp={}",
                ticker, beta, shares, interestExpense, incomeTaxExpense);

        // Market cap = precio × shares (para ponderar WACC correctamente)
        BigDecimal marketCap = marketData.getMarketCap() != null && marketData.getMarketCap().compareTo(BigDecimal.ZERO) > 0
                ? marketData.getMarketCap()
                : marketData.getPrice().multiply(BigDecimal.valueOf(shares));

        return new CompanyFinancials(
                ticker,
                historicalFcf,
                safeValue(latestBalance, FinancialStatement::getTotalDebt),
                safeValue(latestBalance, FinancialStatement::getCashAndEquivalents),
                safeValue(latestBalance, FinancialStatement::getTotalEquity),
                interestExpense,
                incomeTaxExpense,
                beta,
                shares,
                safeValue(latestIncome != null ? latestIncome : latestCashFlow, FinancialStatement::getEbitda),
                marketCap,
                analystFcfEstimates
        );
    }

    private BigDecimal safeValue(FinancialStatement stmt,
                                  java.util.function.Function<FinancialStatement, BigDecimal> getter) {
        if (stmt == null) return BigDecimal.ZERO;
        var val = getter.apply(stmt);
        return val != null ? val : BigDecimal.ZERO;
    }
}
