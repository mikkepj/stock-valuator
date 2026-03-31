package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WaccCalculatorTest {

    private WaccCalculator calculator;
    private static final BigDecimal RISK_FREE_RATE = new BigDecimal("0.045");
    private static final BigDecimal MARKET_RISK_PREMIUM = new BigDecimal("0.055");

    @BeforeEach
    void setUp() {
        calculator = new WaccCalculator();
    }

    @Test
    void calculate_typicalCompany_returnsReasonableWacc() {
        // Datos aproximados de AAPL 2023
        var financials = buildFinancials(
                new BigDecimal("110000000000"),  // totalDebt
                new BigDecimal("50000000000"),   // totalEquity
                new BigDecimal("3900000000"),    // interestExpense
                new BigDecimal("29000000000"),   // incomeTaxExpense
                new BigDecimal("1.24")           // beta
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // WACC de AAPL suele estar entre 7% y 12%
        assertNotNull(wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.05")) > 0,
                "WACC debe ser > 5%, fue: " + wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.20")) < 0,
                "WACC debe ser < 20%, fue: " + wacc);
    }

    @Test
    void calculate_companyWithNoDebt_usesOnlyCostOfEquity() {
        var financials = buildFinancials(
                BigDecimal.ZERO,                 // totalDebt = 0
                new BigDecimal("100000000000"),  // totalEquity
                BigDecimal.ZERO,                 // interestExpense = 0
                new BigDecimal("10000000000"),   // incomeTaxExpense
                new BigDecimal("1.0")            // beta
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // CAPM: 0.045 + 1.0 * 0.055 = 0.10
        // Sin deuda, WACC = Cost of Equity = 10%
        assertEquals(0, wacc.compareTo(new BigDecimal("0.10")),
                "WACC sin deuda debe ser igual al cost of equity (10%), fue: " + wacc);
    }

    @Test
    void calculate_higherBeta_producesHigherWacc() {
        var lowBeta = buildFinancials(
                new BigDecimal("50000000000"), new BigDecimal("50000000000"),
                new BigDecimal("2000000000"), new BigDecimal("5000000000"),
                new BigDecimal("0.5")
        );
        var highBeta = buildFinancials(
                new BigDecimal("50000000000"), new BigDecimal("50000000000"),
                new BigDecimal("2000000000"), new BigDecimal("5000000000"),
                new BigDecimal("2.0")
        );

        var waccLow = calculator.calculate(lowBeta, RISK_FREE_RATE, MARKET_RISK_PREMIUM);
        var waccHigh = calculator.calculate(highBeta, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertTrue(waccHigh.compareTo(waccLow) > 0,
                "Mayor beta debe producir mayor WACC");
    }

    @Test
    void calculate_higherInterestExpense_producesHigherWacc() {
        // Mayor gasto en intereses → mayor costo de deuda → mayor WACC
        var lowInterest = buildFinancials(
                new BigDecimal("100000000000"), new BigDecimal("100000000000"),
                new BigDecimal("2000000000"),   // interés bajo
                new BigDecimal("10000000000"),
                new BigDecimal("1.0")
        );
        var highInterest = buildFinancials(
                new BigDecimal("100000000000"), new BigDecimal("100000000000"),
                new BigDecimal("8000000000"),   // interés alto
                new BigDecimal("10000000000"),
                new BigDecimal("1.0")
        );

        var waccLow = calculator.calculate(lowInterest, RISK_FREE_RATE, MARKET_RISK_PREMIUM);
        var waccHigh = calculator.calculate(highInterest, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertTrue(waccHigh.compareTo(waccLow) > 0,
                "Mayor gasto en intereses debe producir mayor WACC");
    }

    @Test
    void calculate_waccAlwaysPositive() {
        var financials = buildFinancials(
                new BigDecimal("50000000000"), new BigDecimal("50000000000"),
                new BigDecimal("2000000000"), new BigDecimal("8000000000"),
                new BigDecimal("1.5")
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertTrue(wacc.compareTo(BigDecimal.ZERO) > 0, "WACC siempre debe ser positivo");
    }

    @Test
    void calculate_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, RISK_FREE_RATE, MARKET_RISK_PREMIUM));
    }

    // Helper para construir CompanyFinancials con los campos relevantes para WACC
    private CompanyFinancials buildFinancials(BigDecimal totalDebt, BigDecimal totalEquity,
                                              BigDecimal interestExpense, BigDecimal incomeTaxExpense,
                                              BigDecimal beta) {
        return new CompanyFinancials(
                "TEST",
                List.of(new BigDecimal("100000000000")),
                totalDebt,
                new BigDecimal("20000000000"),
                totalEquity,
                interestExpense,
                incomeTaxExpense,
                beta,
                1000000000L,
                new BigDecimal("50000000000")
        );
    }
}
