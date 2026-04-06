package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SensitivityAnalyzerTest {

    private SensitivityAnalyzer analyzer;
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
        analyzer = new SensitivityAnalyzer();

        financials = new CompanyFinancials(
                "TEST",
                List.of(
                        new BigDecimal("80000000000"),
                        new BigDecimal("90000000000"),
                        new BigDecimal("100000000000")
                ),
                new BigDecimal("50000000000"),
                new BigDecimal("20000000000"),
                new BigDecimal("80000000000"),
                new BigDecimal("2000000000"),
                new BigDecimal("8000000000"),
                new BigDecimal("1.1"),
                5000000000L,
                new BigDecimal("110000000000"),
                BigDecimal.ZERO,
                List.of()
        );

        baseParams = new DcfParameters(
                new BigDecimal("0.045"),
                new BigDecimal("0.055"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("150")
        );
    }

    @Test
    void analyze_returnsMatrix5x5() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        assertNotNull(matrix);
        // 5 variaciones de WACC
        assertEquals(5, matrix.size(), "La matriz debe tener 5 columnas de WACC");
        // Cada columna debe tener 5 filas de growth rate
        for (var entry : matrix.entrySet()) {
            assertEquals(5, entry.getValue().size(),
                    "Cada columna de WACC debe tener 5 filas de growth, falló en: " + entry.getKey());
        }
    }

    @Test
    void analyze_matrixKeysRepresentWaccVariations() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        // Las claves deben representar ajustes de WACC: -0.01, -0.005, 0.00, +0.005, +0.01
        assertTrue(matrix.containsKey("-1.00%"), "Debe contener clave -1.00%");
        assertTrue(matrix.containsKey("-0.50%"), "Debe contener clave -0.50%");
        assertTrue(matrix.containsKey("0.00%"),  "Debe contener clave 0.00%");
        assertTrue(matrix.containsKey("+0.50%"), "Debe contener clave +0.50%");
        assertTrue(matrix.containsKey("+1.00%"), "Debe contener clave +1.00%");
    }

    @Test
    void analyze_matrixRowKeysRepresentGrowthVariations() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        // Las filas deben representar ajustes de growth rate
        var firstColumn = matrix.values().iterator().next();
        assertTrue(firstColumn.containsKey("-2.00%"), "Debe contener fila -2.00%");
        assertTrue(firstColumn.containsKey("-1.00%"), "Debe contener fila -1.00%");
        assertTrue(firstColumn.containsKey("0.00%"),  "Debe contener fila 0.00%");
        assertTrue(firstColumn.containsKey("+1.00%"), "Debe contener fila +1.00%");
        assertTrue(firstColumn.containsKey("+2.00%"), "Debe contener fila +2.00%");
    }

    @Test
    void analyze_centralCell_matchesBaseCalculation() {
        var baseResult = dcfCalculator.calculate(financials, baseParams);
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        // La celda central (WACC=0%, growth=0%) debe coincidir con el cálculo base
        var centralValue = matrix.get("0.00%").get("0.00%");

        assertNotNull(centralValue, "La celda central debe existir");
        assertEquals(0, centralValue.compareTo(baseResult.intrinsicValuePerShare()),
                "La celda central debe coincidir con el resultado base. " +
                "Esperado: " + baseResult.intrinsicValuePerShare() + ", fue: " + centralValue);
    }

    @Test
    void analyze_lowerWacc_producesHigherIntrinsicValue() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        // Para el mismo growth rate, menor WACC → mayor valor intrínseco
        var growthKey = "0.00%";
        var valueHigherWacc = matrix.get("+1.00%").get(growthKey);
        var valueLowerWacc = matrix.get("-1.00%").get(growthKey);

        assertTrue(valueLowerWacc.compareTo(valueHigherWacc) > 0,
                "Menor WACC debe producir mayor valor intrínseco");
    }

    @Test
    void analyze_higherGrowth_producesHigherIntrinsicValue() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        // Para el mismo WACC, mayor growth → mayor valor intrínseco
        var waccKey = "0.00%";
        var valueLowGrowth = matrix.get(waccKey).get("-2.00%");
        var valueHighGrowth = matrix.get(waccKey).get("+2.00%");

        assertTrue(valueHighGrowth.compareTo(valueLowGrowth) > 0,
                "Mayor tasa de crecimiento debe producir mayor valor intrínseco");
    }

    @Test
    void analyze_allCellsArePositive() {
        var matrix = analyzer.analyze(financials, baseParams, dcfCalculator);

        for (var waccEntry : matrix.entrySet()) {
            for (var growthEntry : waccEntry.getValue().entrySet()) {
                assertTrue(growthEntry.getValue().compareTo(BigDecimal.ZERO) > 0,
                        "Todas las celdas deben ser positivas. Falló en WACC=" +
                        waccEntry.getKey() + ", growth=" + growthEntry.getKey());
            }
        }
    }

    @Test
    void analyze_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class,
                () -> analyzer.analyze(null, baseParams, dcfCalculator));
    }

    @Test
    void analyze_nullParams_throwsException() {
        assertThrows(NullPointerException.class,
                () -> analyzer.analyze(financials, null, dcfCalculator));
    }
}
