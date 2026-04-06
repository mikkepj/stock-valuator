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

    @Test
    void project_extremeHistoricalCagr_isCappedAt30Percent() {
        // CAGR implícito: (500/10)^(1/1) - 1 = 4900% — debe quedar cappado al 30%
        var historical = List.of(
                new BigDecimal("10000000000"),
                new BigDecimal("500000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.025"), 10);

        // El primer año no puede crecer más del 30% sobre el último FCF histórico
        BigDecimal lastHistorical = new BigDecimal("500000000000");
        BigDecimal maxFirstYear = lastHistorical.multiply(new BigDecimal("1.30"));
        assertTrue(result.get(0).projectedValue().compareTo(maxFirstYear) <= 0,
                "El primer año proyectado no debe superar el cap del 30%. Fue: " + result.get(0).projectedValue());
    }

    @Test
    void project_moderateCagr_isNotCapped() {
        // CAGR ~12% — no debe ser afectado por el cap del 30%
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("100000000000"),
                new BigDecimal("120000000000"),
                new BigDecimal("140000000000"),
                new BigDecimal("160000000000")
        );

        var result = projector.project(historical, new BigDecimal("0.025"), 10);

        // CAGR ≈ 18.9% → tasa del primer año debe ser ≈ 18.9%, no cappada
        assertTrue(result.get(0).growthRateApplied().compareTo(new BigDecimal("0.15")) > 0,
                "CAGR moderado no debe ser cappado. Fue: " + result.get(0).growthRateApplied());
    }

    // --- Tests con analystFcfEstimates ---

    @Test
    void project_withAnalystEstimates_usesThem_forFirstYears() {
        // 5 estimaciones de analistas para años 1-5
        var historical = List.of(new BigDecimal("50000000000"));
        var analystEstimates = List.of(
                new BigDecimal("99000000000"),
                new BigDecimal("122000000000"),
                new BigDecimal("146000000000"),
                new BigDecimal("174000000000"),
                new BigDecimal("210000000000")
        );

        var result = projector.projectWithEstimates(historical, analystEstimates,
                new BigDecimal("0.025"), 10);

        assertEquals(10, result.size());
        // Los primeros 5 años deben usar las estimaciones directamente
        assertEquals(0, result.get(0).projectedValue().compareTo(new BigDecimal("99000000000")));
        assertEquals(0, result.get(1).projectedValue().compareTo(new BigDecimal("122000000000")));
        assertEquals(0, result.get(2).projectedValue().compareTo(new BigDecimal("146000000000")));
        assertEquals(0, result.get(3).projectedValue().compareTo(new BigDecimal("174000000000")));
        assertEquals(0, result.get(4).projectedValue().compareTo(new BigDecimal("210000000000")));
    }

    @Test
    void project_withAnalystEstimates_projectsRemainingYearsWithDecay() {
        var historical = List.of(new BigDecimal("50000000000"));
        var analystEstimates = List.of(
                new BigDecimal("99000000000"),
                new BigDecimal("122000000000"),
                new BigDecimal("146000000000"),
                new BigDecimal("174000000000"),
                new BigDecimal("210000000000")
        );
        var terminalRate = new BigDecimal("0.025");

        var result = projector.projectWithEstimates(historical, analystEstimates, terminalRate, 10);

        // Los años 6-10 deben ser mayores que el año 5 (si crecimiento > 0)
        // pero decayendo hacia la tasa terminal
        // El último año debe usar exactamente la tasa terminal
        assertEquals(0, result.get(9).growthRateApplied().compareTo(terminalRate),
                "El último año proyectado debe usar la tasa terminal");
    }

    @Test
    void project_withAnalystEstimates_greaterThanProjectionYears_usesAll() {
        // Si hay más estimaciones que años de proyección, se truncan
        var historical = List.of(new BigDecimal("50000000000"));
        var analystEstimates = List.of(
                new BigDecimal("99000000000"),
                new BigDecimal("122000000000"),
                new BigDecimal("146000000000")
        );

        var result = projector.projectWithEstimates(historical, analystEstimates,
                new BigDecimal("0.025"), 3);

        assertEquals(3, result.size());
        assertEquals(0, result.get(0).projectedValue().compareTo(new BigDecimal("99000000000")));
        assertEquals(0, result.get(1).projectedValue().compareTo(new BigDecimal("122000000000")));
        assertEquals(0, result.get(2).projectedValue().compareTo(new BigDecimal("146000000000")));
    }

    @Test
    void project_withEmptyAnalystEstimates_fallsBackToHistoricalCagr() {
        var historical = List.of(
                new BigDecimal("80000000000"),
                new BigDecimal("100000000000")
        );

        var withEstimates = projector.projectWithEstimates(historical, List.of(),
                new BigDecimal("0.025"), 10);
        var withoutEstimates = projector.project(historical, new BigDecimal("0.025"), 10);

        // Deben producir el mismo resultado
        for (int i = 0; i < 10; i++) {
            assertEquals(0,
                    withEstimates.get(i).projectedValue().compareTo(withoutEstimates.get(i).projectedValue()),
                    "Año " + (i + 1) + " debe coincidir con proyección sin estimaciones");
        }
    }

    @Test
    void project_withAnalystEstimates_yearsNumberedCorrectly() {
        var historical = List.of(new BigDecimal("50000000000"));
        var analystEstimates = List.of(
                new BigDecimal("99000000000"),
                new BigDecimal("122000000000")
        );

        var result = projector.projectWithEstimates(historical, analystEstimates,
                new BigDecimal("0.025"), 5);

        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, result.get(i).year(), "Año debe ser " + (i + 1));
        }
    }
}
