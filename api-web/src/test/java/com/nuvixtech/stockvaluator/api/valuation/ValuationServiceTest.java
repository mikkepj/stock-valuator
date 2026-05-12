package com.nuvixtech.stockvaluator.api.valuation;

import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.entity.ValuationResultEntity;
import com.nuvixtech.stockvaluator.api.valuation.mapper.ValuationMapper;
import com.nuvixtech.stockvaluator.api.valuation.repository.ValuationResultRepository;
import com.nuvixtech.stockvaluator.api.valuation.service.ValuationService;
import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import com.nuvixtech.stockvaluator.ingestion.entity.FinancialStatement;
import com.nuvixtech.stockvaluator.ingestion.entity.MarketData;
import com.nuvixtech.stockvaluator.ingestion.entity.StatementType;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FcfEstimateRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.FinancialStatementRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.MarketDataRepository;
import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService;
import com.nuvixtech.stockvaluator.valuation.DcfCalculator;
import com.nuvixtech.stockvaluator.valuation.ScenarioAnalyzer;
import com.nuvixtech.stockvaluator.valuation.SensitivityAnalyzer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ValuationServiceTest {

    @Mock private ValuationResultRepository valuationRepository;
    @Mock private FinancialStatementRepository statementRepository;
    @Mock private MarketDataRepository marketDataRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private FinancialDataService financialDataService;
    @Mock private FcfEstimateRepository fcfEstimateRepository;
    @Mock private DcfCalculator dcfCalculator;
    @Mock private SensitivityAnalyzer sensitivityAnalyzer;
    @Mock private ScenarioAnalyzer scenarioAnalyzer;
    @Mock private ValuationMapper mapper;

    private ValuationService service;

    @BeforeEach
    void setUp() {
        service = new ValuationService(
                valuationRepository,
                statementRepository,
                marketDataRepository,
                companyRepository,
                fcfEstimateRepository,
                financialDataService,
                dcfCalculator,
                sensitivityAnalyzer,
                scenarioAnalyzer,
                mapper,
                new BigDecimal("0.045"),
                new BigDecimal("0.045"),
                new BigDecimal("0.025"),
                10
        );
    }

    @Test
    void getLatestValuation_tickerWithExistingResult_returnsResponse() {
        var company = buildCompany("AAPL");
        var entity = buildEntity(company);
        var expectedResponse = buildResponse("AAPL");

        when(valuationRepository.findFirstByCompanyTickerOrderByCalculatedAtDesc("AAPL"))
                .thenReturn(Optional.of(entity));
        when(mapper.toResponse(entity)).thenReturn(expectedResponse);

        var result = service.getLatestValuation("AAPL");

        assertEquals("AAPL", result.ticker());
        verify(valuationRepository).findFirstByCompanyTickerOrderByCalculatedAtDesc("AAPL");
        verify(dcfCalculator, never()).calculate(any(), any());
    }

    @Test
    void getLatestValuation_tickerNotFound_throwsTickerNotFoundException() {
        when(valuationRepository.findFirstByCompanyTickerOrderByCalculatedAtDesc("UNKNOWN"))
                .thenReturn(Optional.empty());

        assertThrows(TickerNotFoundException.class,
                () -> service.getLatestValuation("UNKNOWN"));
    }

    @Test
    void calculate_tickerWithData_runsEngineAndPersists() {
        var company = buildCompany("MSFT");
        var statements = buildStatements(company);
        var marketData = buildMarketData(company);
        var entity = buildEntity(company);
        var expectedResponse = buildResponse("MSFT");

        when(companyRepository.findByTicker("MSFT")).thenReturn(Optional.of(company));
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("MSFT"), eq(StatementType.CASHFLOW))).thenReturn(statements);
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("MSFT"), eq(StatementType.BALANCE))).thenReturn(statements);
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("MSFT"), eq(StatementType.INCOME))).thenReturn(statements);
        when(marketDataRepository.findTopByCompanyTickerOrderByFetchedAtDesc("MSFT"))
                .thenReturn(Optional.of(marketData));
        when(fcfEstimateRepository.findByCompanyTickerOrderByFiscalYearAsc(any())).thenReturn(Collections.emptyList());
        when(dcfCalculator.calculate(any(), any())).thenReturn(buildValuationResult("MSFT"));
        when(sensitivityAnalyzer.analyze(any(), any(), any())).thenReturn(Collections.emptyMap());
        when(scenarioAnalyzer.analyze(any(), any())).thenReturn(Collections.emptyList());
        when(mapper.toEntity(any(), any(), eq(company), any())).thenReturn(entity);
        when(valuationRepository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(expectedResponse);

        var result = service.calculate("MSFT");

        assertEquals("MSFT", result.ticker());
        verify(valuationRepository).save(any());
        verify(dcfCalculator).calculate(any(), any());
    }

    @Test
    void calculate_companyNotFound_triggersIngestionFirst() {
        var company = buildCompany("GOOG");
        var statements = buildStatements(company);
        var marketData = buildMarketData(company);
        var entity = buildEntity(company);
        var expectedResponse = buildResponse("GOOG");

        // Primera llamada: no existe. Post-ingestion: existe.
        when(companyRepository.findByTicker("GOOG"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(company));
        when(financialDataService.ingest("GOOG"))
                .thenReturn(new FinancialDataService.IngestionResult(
                        "GOOG", "Alphabet Inc.", 5, 5, 5, "150.00"));
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("GOOG"), eq(StatementType.CASHFLOW))).thenReturn(statements);
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("GOOG"), eq(StatementType.BALANCE))).thenReturn(statements);
        when(statementRepository.findByCompanyTickerAndStatementTypeOrderByFiscalYearDesc(
                eq("GOOG"), eq(StatementType.INCOME))).thenReturn(statements);
        when(marketDataRepository.findTopByCompanyTickerOrderByFetchedAtDesc("GOOG"))
                .thenReturn(Optional.of(marketData));
        when(dcfCalculator.calculate(any(), any())).thenReturn(buildValuationResult("GOOG"));
        when(sensitivityAnalyzer.analyze(any(), any(), any())).thenReturn(Collections.emptyMap());
        when(scenarioAnalyzer.analyze(any(), any())).thenReturn(Collections.emptyList());
        when(mapper.toEntity(any(), any(), eq(company), any())).thenReturn(entity);
        when(valuationRepository.save(entity)).thenReturn(entity);
        when(mapper.toResponse(entity)).thenReturn(expectedResponse);

        service.calculate("GOOG");

        verify(financialDataService).ingest("GOOG");
    }

    // --- Helpers ---

    /** Subclase anónima para acceder al constructor protected de Company */
    private Company buildCompany(String ticker) {
        Company c = new Company() {};
        c.setTicker(ticker);
        c.setName(ticker + " Inc.");
        c.setSector("Technology");
        return c;
    }

    private ValuationResultEntity buildEntity(Company company) {
        var e = new ValuationResultEntity();
        e.setCompany(company);
        e.setCalculatedAt(LocalDateTime.now());
        e.setIntrinsicValue(new BigDecimal("200.00"));
        e.setMarketPrice(new BigDecimal("178.50"));
        e.setMarginOfSafety(new BigDecimal("12.04"));
        e.setVerdict("FAIR_VALUE");
        e.setWacc(new BigDecimal("0.089"));
        e.setTerminalGrowth(new BigDecimal("0.025"));
        e.setProjectionYears(10);
        e.setTerminalValue(new BigDecimal("1000000000000"));
        e.setNetDebt(new BigDecimal("48000000000"));
        e.setSensitivityMatrix(Collections.emptyMap());
        e.setBreakdown(Collections.emptyMap());
        return e;
    }

    private ValuationResponse buildResponse(String ticker) {
        return new ValuationResponse(
                ticker, ticker + " Inc.", "Technology",
                new BigDecimal("200.00"), new BigDecimal("178.50"),
                new BigDecimal("12.04"), "FAIR_VALUE",
                new BigDecimal("0.089"), new BigDecimal("0.025"), 10,
                new BigDecimal("1000000000000"), new BigDecimal("48000000000"),
                null,
                Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap(),
                LocalDateTime.now()
        );
    }

    private List<FinancialStatement> buildStatements(Company company) {
        FinancialStatement s = new FinancialStatement(company, 2023, StatementType.CASHFLOW);
        s.setPeriod("FY");
        s.setFreeCashFlow(new BigDecimal("100000000000"));
        s.setOperatingCashFlow(new BigDecimal("110000000000"));
        s.setCapitalExpenditure(new BigDecimal("10000000000"));
        s.setTotalDebt(new BigDecimal("110000000000"));
        s.setCashAndEquivalents(new BigDecimal("62000000000"));
        s.setTotalEquity(new BigDecimal("62000000000"));
        s.setInterestExpense(new BigDecimal("3900000000"));
        s.setIncomeTaxExpense(new BigDecimal("29000000000"));
        s.setSharesOutstanding(15441926000L);
        s.setEbitda(new BigDecimal("125000000000"));
        return List.of(s);
    }

    private MarketData buildMarketData(Company company) {
        MarketData m = new MarketData(company, new BigDecimal("178.50"));
        m.setBeta(new BigDecimal("1.24"));
        return m;
    }

    private com.nuvixtech.stockvaluator.valuation.ValuationResult buildValuationResult(String ticker) {
        return new com.nuvixtech.stockvaluator.valuation.ValuationResult(
                ticker,
                new BigDecimal("200.00"),
                new BigDecimal("178.50"),
                new BigDecimal("12.04"),
                com.nuvixtech.stockvaluator.valuation.Verdict.FAIR_VALUE,
                new BigDecimal("0.089"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("1000000000000"),
                new BigDecimal("48000000000"),
                Collections.emptyList(),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }
}
