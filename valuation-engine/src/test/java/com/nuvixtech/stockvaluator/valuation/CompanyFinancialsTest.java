package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CompanyFinancialsTest {

    @Test
    void constructor_validData_createsInstance() {
        var fcf = List.of(new BigDecimal("80000000000"), new BigDecimal("90000000000"),
                new BigDecimal("100000000000"), new BigDecimal("110000000000"),
                new BigDecimal("120000000000"));

        var financials = new CompanyFinancials(
                "AAPL",
                fcf,
                new BigDecimal("110000000000"),  // totalDebt
                new BigDecimal("62000000000"),    // cashAndEquivalents
                new BigDecimal("50000000000"),    // totalEquity
                new BigDecimal("3900000000"),     // interestExpense
                new BigDecimal("29000000000"),    // incomeTaxExpense
                new BigDecimal("1.24"),           // beta
                15441926000L,                     // sharesOutstanding
                new BigDecimal("125000000000"),   // ebitda
                BigDecimal.ZERO,                  // marketCap (usa totalEquity como fallback)
                List.of()                         // sin estimaciones de analistas
        );

        assertEquals("AAPL", financials.ticker());
        assertEquals(5, financials.historicalFcf().size());
        assertEquals(new BigDecimal("1.24"), financials.beta());
    }

    @Test
    void constructor_nullTicker_throwsException() {
        var fcf = List.of(new BigDecimal("100000000000"));

        assertThrows(NullPointerException.class, () -> new CompanyFinancials(
                null, fcf,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1.0"), 1000L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        ));
    }

    @Test
    void constructor_nullHistoricalFcf_throwsException() {
        assertThrows(NullPointerException.class, () -> new CompanyFinancials(
                "AAPL", null,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1.0"), 1000L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        ));
    }

    @Test
    void constructor_emptyHistoricalFcf_throwsException() {
        assertThrows(IllegalArgumentException.class, () -> new CompanyFinancials(
                "AAPL", Collections.emptyList(),
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1.0"), 1000L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        ));
    }

    @Test
    void constructor_nullTotalDebt_throwsException() {
        var fcf = List.of(new BigDecimal("100000000000"));

        assertThrows(NullPointerException.class, () -> new CompanyFinancials(
                "AAPL", fcf,
                null, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1.0"), 1000L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        ));
    }

    @Test
    void constructor_zeroOrNegativeSharesOutstanding_throwsException() {
        var fcf = List.of(new BigDecimal("100000000000"));

        assertThrows(IllegalArgumentException.class, () -> new CompanyFinancials(
                "AAPL", fcf,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("1.0"), 0L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        ));
    }

    @Test
    void historicalFcf_returnsImmutableList() {
        var fcf = List.of(new BigDecimal("100000000000"));
        var financials = new CompanyFinancials(
                "MSFT", fcf,
                BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE,
                new BigDecimal("0.9"), 1000L, BigDecimal.TEN, BigDecimal.ZERO, List.of()
        );

        assertThrows(UnsupportedOperationException.class,
                () -> financials.historicalFcf().add(BigDecimal.ZERO));
    }
}
