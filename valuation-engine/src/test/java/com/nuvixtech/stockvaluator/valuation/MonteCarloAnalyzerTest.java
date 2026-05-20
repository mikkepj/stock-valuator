package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MonteCarloAnalyzerTest {

    private MonteCarloAnalyzer analyzer;
    private CompanyFinancials financials;
    private DcfParameters params;

    @BeforeEach
    void setUp() {
        analyzer = new MonteCarloAnalyzer(new DcfCalculator(
                new FreeCashFlowProjector(),
                new WaccCalculator(),
                new TerminalValueCalculator()
        ));

        financials = new CompanyFinancials(
                "AAPL",
                List.of(
                        new BigDecimal("80000000000"),
                        new BigDecimal("90000000000"),
                        new BigDecimal("100000000000"),
                        new BigDecimal("110000000000"),
                        new BigDecimal("120000000000")
                ),
                new BigDecimal("120000000000"),  // totalDebt
                new BigDecimal("65000000000"),   // cash
                new BigDecimal("60000000000"),   // totalEquity
                new BigDecimal("4000000000"),    // interestExpense
                new BigDecimal("30000000000"),   // incomeTaxExpense
                new BigDecimal("1.2"),           // beta
                15_500_000_000L,                 // shares
                new BigDecimal("130000000000"),  // ebitda
                new BigDecimal("2500000000000"), // marketCap
                List.of(),
                "Technology"
        );

        params = new DcfParameters(
                new BigDecimal("0.045"),
                new BigDecimal("0.045"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("178.50")
        );
    }

    @Test
    void analyze_returns1000Simulations() {
        var result = analyzer.analyze(financials, params, 1000);

        assertEquals(1000, result.simulationCount(),
                "La simulación debe ejecutar exactamente 1000 iteraciones");
    }

    @Test
    void analyze_p10AlwaysLowerThanP90() {
        var result = analyzer.analyze(financials, params, 1000);

        assertTrue(result.p10().compareTo(result.p90()) < 0,
                "P10 debe ser siempre menor que P90. p10=" + result.p10() + " p90=" + result.p90());
    }

    @Test
    void analyze_p25AlwaysLowerThanP75() {
        var result = analyzer.analyze(financials, params, 500);

        assertTrue(result.p25().compareTo(result.p75()) < 0,
                "P25 debe ser siempre menor que P75");
    }

    @Test
    void analyze_p50ApproximatesBaseScenario() {
        // P50 (mediana) debe estar razonablemente cerca del IV base (dentro de ±50%)
        var baseResult = new DcfCalculator(
                new FreeCashFlowProjector(), new WaccCalculator(), new TerminalValueCalculator()
        ).calculate(financials, params);
        BigDecimal baseIV = baseResult.intrinsicValuePerShare();

        var mcResult = analyzer.analyze(financials, params, 2000);

        BigDecimal lowerBound = baseIV.multiply(new BigDecimal("0.5"));
        BigDecimal upperBound = baseIV.multiply(new BigDecimal("1.5"));
        assertTrue(mcResult.p50().compareTo(lowerBound) >= 0 && mcResult.p50().compareTo(upperBound) <= 0,
                "P50 debe estar dentro del ±50% del IV base. base=" + baseIV + " p50=" + mcResult.p50());
    }

    @Test
    void analyze_percentilesAreOrdered() {
        var result = analyzer.analyze(financials, params, 500);

        assertTrue(result.p10().compareTo(result.p25()) <= 0, "P10 <= P25");
        assertTrue(result.p25().compareTo(result.p50()) <= 0, "P25 <= P50");
        assertTrue(result.p50().compareTo(result.p75()) <= 0, "P50 <= P75");
        assertTrue(result.p75().compareTo(result.p90()) <= 0, "P75 <= P90");
    }

    @Test
    void analyze_deterministicWithSameSeed() {
        // Con la misma semilla aleatoria debe producir exactamente los mismos resultados
        var analyzer1 = new MonteCarloAnalyzer(new DcfCalculator(
                new FreeCashFlowProjector(), new WaccCalculator(), new TerminalValueCalculator()
        ), 42L);
        var analyzer2 = new MonteCarloAnalyzer(new DcfCalculator(
                new FreeCashFlowProjector(), new WaccCalculator(), new TerminalValueCalculator()
        ), 42L);

        var result1 = analyzer1.analyze(financials, params, 200);
        var result2 = analyzer2.analyze(financials, params, 200);

        assertEquals(0, result1.p50().compareTo(result2.p50()),
                "Con la misma semilla los resultados deben ser idénticos");
    }

    @Test
    void analyze_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class,
                () -> analyzer.analyze(null, params, 100));
    }

    @Test
    void analyze_nullParams_throwsException() {
        assertThrows(NullPointerException.class,
                () -> analyzer.analyze(financials, null, 100));
    }

    @Test
    void analyze_zeroPerturbations_allPercentilesEqual() {
        // Con σ=0 todas las simulaciones deben producir el mismo IV → p10 = p50 = p90
        var analyzerZeroSigma = new MonteCarloAnalyzer(new DcfCalculator(
                new FreeCashFlowProjector(), new WaccCalculator(), new TerminalValueCalculator()
        ), 0.0, 0.0);  // waccSigma=0, growthSigma=0

        var result = analyzerZeroSigma.analyze(financials, params, 100);

        assertEquals(0, result.p10().compareTo(result.p90()),
                "Con σ=0 todos los percentiles deben ser iguales. p10=" + result.p10() + " p90=" + result.p90());
    }
}
