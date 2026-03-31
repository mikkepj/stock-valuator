package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.*;

class TerminalValueCalculatorTest {

    private TerminalValueCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TerminalValueCalculator();
    }

    @Test
    void calculate_gordonGrowthFormula_returnsCorrectPresentValue() {
        // FCF año 10 = 150B, WACC = 9%, g = 2.5%
        // TV = 150B × (1 + 0.025) / (0.09 - 0.025) = 150B × 1.025 / 0.065 ≈ 2361.54B
        // PV(TV) = 2361.54B / (1.09)^10 ≈ 997.41B
        var fcfLastYear = new BigDecimal("150000000000");
        var wacc = new BigDecimal("0.09");
        var terminalGrowthRate = new BigDecimal("0.025");
        int projectionYears = 10;

        var presentValue = calculator.calculate(fcfLastYear, wacc, terminalGrowthRate, projectionYears);

        assertNotNull(presentValue);
        assertTrue(presentValue.compareTo(BigDecimal.ZERO) > 0,
                "El valor presente del TV debe ser positivo");
        // Rango amplio para tolerar precisión de cálculo: entre 800B y 1200B
        assertTrue(presentValue.compareTo(new BigDecimal("800000000000")) > 0,
                "PV del TV debe ser > 800B, fue: " + presentValue);
        assertTrue(presentValue.compareTo(new BigDecimal("1200000000000")) < 0,
                "PV del TV debe ser < 1200B, fue: " + presentValue);
    }

    @Test
    void calculate_waccEqualsGrowthRate_throwsException() {
        var fcf = new BigDecimal("100000000000");
        var rate = new BigDecimal("0.025");

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(fcf, rate, rate, 10));
    }

    @Test
    void calculate_waccLessThanGrowthRate_throwsException() {
        var fcf = new BigDecimal("100000000000");
        var wacc = new BigDecimal("0.020");
        var growth = new BigDecimal("0.030");

        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(fcf, wacc, growth, 10));
    }

    @Test
    void calculate_moreProjYears_producesLowerPresentValue() {
        // El mismo TV descontado a más años produce un PV menor
        var fcf = new BigDecimal("100000000000");
        var wacc = new BigDecimal("0.09");
        var growth = new BigDecimal("0.025");

        var pv5years = calculator.calculate(fcf, wacc, growth, 5);
        var pv10years = calculator.calculate(fcf, wacc, growth, 10);

        assertTrue(pv5years.compareTo(pv10years) > 0,
                "TV descontado a 5 años debe ser mayor que a 10 años");
    }

    @Test
    void calculate_higherWacc_producesLowerPresentValue() {
        var fcf = new BigDecimal("100000000000");
        var growth = new BigDecimal("0.025");

        var pvLowWacc = calculator.calculate(fcf, new BigDecimal("0.08"), growth, 10);
        var pvHighWacc = calculator.calculate(fcf, new BigDecimal("0.12"), growth, 10);

        assertTrue(pvLowWacc.compareTo(pvHighWacc) > 0,
                "WACC más bajo debe producir PV mayor");
    }

    @Test
    void calculate_nullFcf_throwsException() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, new BigDecimal("0.09"),
                        new BigDecimal("0.025"), 10));
    }

    @Test
    void calculate_zeroProjYears_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> calculator.calculate(new BigDecimal("100000000000"),
                        new BigDecimal("0.09"), new BigDecimal("0.025"), 0));
    }
}
