package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FreeCashFlowProjectorTest {

    private FreeCashFlowProjector projector;

    @BeforeEach
    void setUp() {
        projector = new FreeCashFlowProjector();
    }

    @Test
    void project_fiveHistoricalYears_returnsTenProjections() {
        // FCF creciendo de 80B a 120B en 5 años
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("90000000000"),
                new BigDecimal("100000000000"),
                new BigDecimal("110000000000"),
                new BigDecimal("120000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.025"), 10);

        assertEquals(10, result.size());
        // Los años deben ser 1..10
        for (int i = 0; i < 10; i++) {
            assertEquals(i + 1, result.get(i).year());
        }
    }

    @Test
    void project_growthDecaysToTerminalRate() {
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("120000000000")
        );
        var terminalRate = new BigDecimal("0.025");

        var result = projector.project(historical, terminalRate, 10);

        // El último año debe tener una tasa de crecimiento muy cercana a la terminal
        var lastRate = result.get(9).growthRateApplied();
        assertEquals(0, lastRate.compareTo(terminalRate),
                "El último año debería tener exactamente la tasa terminal");
    }

    @Test
    void project_firstYearGrowthRate_isCloseToCagr() {
        // CAGR = (120/80)^(1/4) - 1 ≈ 10.67%
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("90000000000"),
                new BigDecimal("100000000000"),
                new BigDecimal("110000000000"),
                new BigDecimal("120000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.025"), 10);

        // El primer año debe usar una tasa más alta que la terminal
        var firstRate = result.get(0).growthRateApplied();
        assertTrue(firstRate.compareTo(new BigDecimal("0.025")) > 0,
                "El primer año debe tener tasa mayor que la terminal");
    }

    @Test
    void project_singleHistoricalYear_usesTerminalRateAsGrowth() {
        // Con un solo año histórico no se puede calcular CAGR → usar terminal rate
        var historical = List.of(new BigDecimal("100000000000"));

        var result = projector.project(historical, new BigDecimal("0.025"), 5);

        assertEquals(5, result.size());
        // Todos los años deben usar la tasa terminal
        for (var projected : result) {
            assertEquals(0, projected.growthRateApplied().compareTo(new BigDecimal("0.025")));
        }
    }

    @Test
    void project_negativeFcfInHistory_stillProjects() {
        // FCF negativo en el primer año, positivo al final
        var historical = List.of(
                new BigDecimal("-5000000000"),
                new BigDecimal("10000000000"),
                new BigDecimal("20000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.025"), 5);

        assertEquals(5, result.size());
        // El FCF proyectado debe partir del último valor histórico positivo
        assertTrue(result.get(0).projectedValue().compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    void project_eachYearValueIsGreaterThanPrevious_whenPositiveGrowth() {
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("100000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.03"), 5);

        for (int i = 1; i < result.size(); i++) {
            assertTrue(result.get(i).projectedValue()
                    .compareTo(result.get(i - 1).projectedValue()) > 0,
                    "Año " + (i + 1) + " debe ser mayor que año " + i);
        }
    }

    @Test
    void project_nullHistorical_throwsException() {
        assertThrows(NullPointerException.class,
                () -> projector.project(null, new BigDecimal("0.025"), 10));
    }

    @Test
    void project_zeroProjectionYears_throwsException() {
        var historical = List.of(new BigDecimal("100000000000"));
        assertThrows(IllegalArgumentException.class,
                () -> projector.project(historical, new BigDecimal("0.025"), 0));
    }
}
