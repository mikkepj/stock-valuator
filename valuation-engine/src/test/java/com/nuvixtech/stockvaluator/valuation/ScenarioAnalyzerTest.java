package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ScenarioAnalyzerTest {

    private ScenarioAnalyzer analyzer;
    private DcfCalculator dcfCalculator;
    private CompanyFinancials financials;
    private DcfParameters baseParams;

    @BeforeEach
    void setUp() {
        dcfCalculator = new DcfCalculator(
                new FreeCashFlowProjector(),
                new WaccCalculator(),
                new TerminalValueCalculator()
        );
        analyzer = new ScenarioAnalyzer(dcfCalculator);

        financials = new CompanyFinancials(
                "MSFT",
                List.of(
                        new BigDecimal("45000000000"),
                        new BigDecimal("55000000000"),
                        new BigDecimal("65000000000"),
                        new BigDecimal("72000000000"),
                        new BigDecimal("75000000000")
                ),
                new BigDecimal("97000000000"),
                new BigDecimal("18000000000"),
                new BigDecimal("268000000000"),
                new BigDecimal("2385000000"),
                new BigDecimal("21795000000"),
                new BigDecimal("1.107"),
                7433000000L,
                new BigDecimal("140000000000"),
                new BigDecimal("2780000000000"),
                List.of(),
                null
        );

        baseParams = new DcfParameters(
                new BigDecimal("0.045"),
                new BigDecimal("0.045"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("373.46")
        );
    }

    @Test
    void analyze_returnsThreeScenarios() {
        var scenarios = analyzer.analyze(financials, baseParams);

        assertEquals(3, scenarios.size());
    }

    @Test
    void analyze_scenarioNamesAreCorrect() {
        var scenarios = analyzer.analyze(financials, baseParams);

        assertTrue(scenarios.stream().anyMatch(s -> s.scenarioName().equals("Base")));
        assertTrue(scenarios.stream().anyMatch(s -> s.scenarioName().equals("Optimista")));
        assertTrue(scenarios.stream().anyMatch(s -> s.scenarioName().equals("Pesimista")));
    }

    @Test
    void analyze_optimistaGreaterThanBase() {
        var scenarios = analyzer.analyze(financials, baseParams);

        var base = scenarios.stream().filter(s -> s.scenarioName().equals("Base")).findFirst().orElseThrow();
        var optimista = scenarios.stream().filter(s -> s.scenarioName().equals("Optimista")).findFirst().orElseThrow();

        assertTrue(optimista.intrinsicValuePerShare().compareTo(base.intrinsicValuePerShare()) > 0,
                "Optimista debe ser mayor que Base. Optimista=" + optimista.intrinsicValuePerShare()
                        + " Base=" + base.intrinsicValuePerShare());
    }

    @Test
    void analyze_pesimistaSmallerThanBase() {
        var scenarios = analyzer.analyze(financials, baseParams);

        var base = scenarios.stream().filter(s -> s.scenarioName().equals("Base")).findFirst().orElseThrow();
        var pesimista = scenarios.stream().filter(s -> s.scenarioName().equals("Pesimista")).findFirst().orElseThrow();

        assertTrue(pesimista.intrinsicValuePerShare().compareTo(base.intrinsicValuePerShare()) < 0,
                "Pesimista debe ser menor que Base. Pesimista=" + pesimista.intrinsicValuePerShare()
                        + " Base=" + base.intrinsicValuePerShare());
    }

    @Test
    void analyze_allScenariosHavePositiveIV() {
        var scenarios = analyzer.analyze(financials, baseParams);

        for (var scenario : scenarios) {
            assertTrue(scenario.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0,
                    "IV debe ser positivo en escenario " + scenario.scenarioName());
        }
    }

    @Test
    void analyze_baseScenarioMatchesDcfCalculator() {
        var scenarios = analyzer.analyze(financials, baseParams);
        var base = scenarios.stream().filter(s -> s.scenarioName().equals("Base")).findFirst().orElseThrow();
        var directResult = dcfCalculator.calculate(financials, baseParams);

        assertEquals(0, base.intrinsicValuePerShare().compareTo(directResult.intrinsicValuePerShare()),
                "Escenario Base debe coincidir con DcfCalculator directo");
    }

    @Test
    void analyze_highCagrAboveCap_optimistaStillGreaterThanBase() {
        // CAGR ~43% (caso TSM): el cap absoluto de 25% caía por debajo del Base,
        // invirtiendo Optimista < Base. Con cap relativo (×1.10) debe mantenerse el orden.
        CompanyFinancials highGrowthFinancials = new CompanyFinancials(
                "TSM",
                List.of(
                        new BigDecimal("10000000000"),
                        new BigDecimal("14300000000"),
                        new BigDecimal("20449000000"),
                        new BigDecimal("29242000000"),
                        new BigDecimal("41816000000")
                ),
                new BigDecimal("50000000000"),
                new BigDecimal("80000000000"),
                new BigDecimal("300000000000"),
                new BigDecimal("3000000000"),
                new BigDecimal("20000000000"),
                new BigDecimal("1.264"),
                5189000000L,
                new BigDecimal("150000000000"),
                new BigDecimal("800000000000"),
                List.of(),
                "Technology"
        );

        var scenarios = analyzer.analyze(highGrowthFinancials, baseParams);
        var base = scenarios.stream().filter(s -> s.scenarioName().equals("Base")).findFirst().orElseThrow();
        var optimista = scenarios.stream().filter(s -> s.scenarioName().equals("Optimista")).findFirst().orElseThrow();
        var pesimista = scenarios.stream().filter(s -> s.scenarioName().equals("Pesimista")).findFirst().orElseThrow();

        assertTrue(optimista.intrinsicValuePerShare().compareTo(base.intrinsicValuePerShare()) > 0,
                "Optimista debe ser mayor que Base incluso con CAGR alto. Optimista="
                        + optimista.intrinsicValuePerShare() + " Base=" + base.intrinsicValuePerShare());
        assertTrue(pesimista.intrinsicValuePerShare().compareTo(base.intrinsicValuePerShare()) < 0,
                "Pesimista debe ser menor que Base. Pesimista="
                        + pesimista.intrinsicValuePerShare() + " Base=" + base.intrinsicValuePerShare());
    }

    @Test
    void analyze_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class, () -> analyzer.analyze(null, baseParams));
    }

    @Test
    void analyze_nullParams_throwsException() {
        assertThrows(NullPointerException.class, () -> analyzer.analyze(financials, null));
    }
}
