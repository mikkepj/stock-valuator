package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DcfCalculatorTest {

    private DcfCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new DcfCalculator(
                new FreeCashFlowProjector(),
                new WaccCalculator(),
                new TerminalValueCalculator()
        );
    }

    @Test
    void calculate_withAaplData_returnsReasonableIntrinsicValue() {
        // Datos aproximados de AAPL FY2019-2023 (FCF en USD)
        var financials = new CompanyFinancials(
                "AAPL",
                List.of(
                        new BigDecimal("58896000000"),  // 2019
                        new BigDecimal("73365000000"),  // 2020
                        new BigDecimal("92953000000"),  // 2021
                        new BigDecimal("111443000000"), // 2022
                        new BigDecimal("99584000000")   // 2023
                ),
                new BigDecimal("110000000000"),  // totalDebt
                new BigDecimal("62000000000"),   // cashAndEquivalents
                new BigDecimal("62000000000"),   // totalEquity
                new BigDecimal("3900000000"),    // interestExpense
                new BigDecimal("29000000000"),   // incomeTaxExpense
                new BigDecimal("1.24"),          // beta
                15441926000L,                    // sharesOutstanding
                new BigDecimal("125000000000"),  // ebitda
                BigDecimal.ZERO,                 // marketCap (fallback a totalEquity)
                List.of()                        // sin estimaciones de analistas
        );

        var params = DcfParameters.defaults(new BigDecimal("178.50"));

        var result = calculator.calculate(financials, params);

        assertNotNull(result);
        assertEquals("AAPL", result.ticker());
        assertNotNull(result.intrinsicValuePerShare());
        assertNotNull(result.verdict());

        // El valor intrínseco calculado debe ser un número positivo razonable
        assertTrue(result.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0,
                "Intrinsic value debe ser positivo");

        // Para AAPL, un rango muy amplio: entre $50 y $500 por acción
        assertTrue(result.intrinsicValuePerShare().compareTo(new BigDecimal("50")) > 0,
                "Intrinsic value demasiado bajo: " + result.intrinsicValuePerShare());
        assertTrue(result.intrinsicValuePerShare().compareTo(new BigDecimal("500")) < 0,
                "Intrinsic value demasiado alto: " + result.intrinsicValuePerShare());
    }

    @Test
    void calculate_netDebt_isCorrectlySubtracted() {
        // totalDebt=100, cash=60 → netDebt=40
        var financials = new CompanyFinancials(
                "TEST",
                List.of(new BigDecimal("10000000000")),
                new BigDecimal("100000000000"),  // totalDebt
                new BigDecimal("60000000000"),   // cash
                new BigDecimal("50000000000"),
                new BigDecimal("3000000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("1.0"),
                1000000000L,
                new BigDecimal("15000000000"),
                BigDecimal.ZERO,
                List.of()
        );

        var params = DcfParameters.defaults(new BigDecimal("100"));
        var result = calculator.calculate(financials, params);

        // netDebt = 100B - 60B = 40B
        assertEquals(0, result.netDebt().compareTo(new BigDecimal("40000000000")),
                "Net debt debe ser 40B, fue: " + result.netDebt());
    }

    @Test
    void calculate_projectedFcfs_matchProjectionYears() {
        var financials = buildSimpleFinancials("MSFT");
        var params = new DcfParameters(
                new BigDecimal("0.045"),
                new BigDecimal("0.055"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("300")
        );

        var result = calculator.calculate(financials, params);

        assertEquals(10, result.projectedFcfs().size());
    }

    @Test
    void calculate_verdictUndervalued_whenIntrinsicAboveMarket() {
        // FCF alto, precio de mercado bajo → UNDERVALUED
        var financials = new CompanyFinancials(
                "CHEAP",
                List.of(new BigDecimal("100000000000"), new BigDecimal("120000000000")),
                BigDecimal.ZERO,                 // sin deuda
                new BigDecimal("10000000000"),
                new BigDecimal("200000000000"),
                BigDecimal.ZERO,
                new BigDecimal("10000000000"),
                new BigDecimal("0.8"),
                100000000L,                      // 100M shares
                new BigDecimal("130000000000"),
                BigDecimal.ZERO,
                List.of()
        );
        // Con FCF de 120B y solo 100M shares: IV estimada muy alta vs precio de $10
        var params = DcfParameters.defaults(new BigDecimal("10"));

        var result = calculator.calculate(financials, params);

        assertEquals(Verdict.UNDERVALUED, result.verdict(),
                "Debe ser UNDERVALUED con IV muy superior al precio de mercado");
    }

    @Test
    void calculate_verdictOvervalued_whenIntrinsicBelowMarket() {
        // FCF muy pequeño, precio de mercado alto → OVERVALUED
        var financials = new CompanyFinancials(
                "EXPENSIVE",
                List.of(new BigDecimal("1000000")),  // FCF minúsculo: 1M
                new BigDecimal("1000000000"),
                BigDecimal.ZERO,
                new BigDecimal("500000000"),
                new BigDecimal("50000"),
                new BigDecimal("100000"),
                new BigDecimal("1.5"),
                1000000000L,                         // 1000M shares
                new BigDecimal("2000000"),
                BigDecimal.ZERO,
                List.of()
        );
        // Con FCF de 1M y 1000M shares, IV por acción será fracción de centavo vs $500
        var params = DcfParameters.defaults(new BigDecimal("500"));

        var result = calculator.calculate(financials, params);

        assertEquals(Verdict.OVERVALUED, result.verdict(),
                "Debe ser OVERVALUED con IV muy inferior al precio de mercado");
    }

    @Test
    void calculate_breakdownContainsKeyFields() {
        var financials = buildSimpleFinancials("GOOG");
        var params = DcfParameters.defaults(new BigDecimal("150"));

        var result = calculator.calculate(financials, params);

        assertNotNull(result.breakdown());
        assertTrue(result.breakdown().containsKey("sumPvFcfs"),
                "breakdown debe contener sumPvFcfs");
        assertTrue(result.breakdown().containsKey("terminalValue"),
                "breakdown debe contener terminalValue");
        assertTrue(result.breakdown().containsKey("wacc"),
                "breakdown debe contener wacc");
    }

    @Test
    void calculate_nullFinancials_throwsException() {
        var params = DcfParameters.defaults(new BigDecimal("100"));
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, params));
    }

    @Test
    void calculate_nullParams_throwsException() {
        var financials = buildSimpleFinancials("TEST");
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(financials, null));
    }

    // Helper para financials simples
    private CompanyFinancials buildSimpleFinancials(String ticker) {
        return new CompanyFinancials(
                ticker,
                List.of(
                        new BigDecimal("50000000000"),
                        new BigDecimal("60000000000"),
                        new BigDecimal("70000000000")
                ),
                new BigDecimal("50000000000"),
                new BigDecimal("20000000000"),
                new BigDecimal("80000000000"),
                new BigDecimal("2000000000"),
                new BigDecimal("8000000000"),
                new BigDecimal("1.1"),
                5000000000L,
                new BigDecimal("80000000000"),
                BigDecimal.ZERO,
                List.of()
        );
    }
}
