package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QualityScoreCalculatorTest {

    private QualityScoreCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new QualityScoreCalculator();
    }

    @Test
    void calculate_perfectScore_allPositiveSignals() {
        // FCF creciendo fuertemente (CAGR > 10%), todos positivos,
        // ROIC > WACC, netDebt/ebitda < 2x, margen FCF estable
        var financials = new CompanyFinancials(
                "AAPL",
                List.of(
                        new BigDecimal("80000000000"),   // año 1
                        new BigDecimal("90000000000"),   // año 2
                        new BigDecimal("100000000000"),  // año 3
                        new BigDecimal("115000000000"),  // año 4
                        new BigDecimal("130000000000")   // año 5 — CAGR ~12.9%
                ),
                new BigDecimal("40000000000"),   // totalDebt
                new BigDecimal("50000000000"),   // cash → netDebt = -10B (negativo, sin deuda neta)
                new BigDecimal("200000000000"),  // totalEquity
                new BigDecimal("1000000000"),    // interestExpense
                new BigDecimal("25000000000"),   // incomeTaxExpense (~taxRate 25%)
                new BigDecimal("1.2"),
                15_000_000_000L,
                new BigDecimal("130000000000"),  // ebitda — netDebt/ebitda < 0 → excelente leverage
                new BigDecimal("2500000000000"),
                List.of(),
                "Technology"
        );
        // WACC bajo para que ROIC > WACC: ROIC = NOPAT/investedCapital
        // NOPAT = 130B * 0.75 = 97.5B; investedCapital = 40B + 200B - 50B = 190B → ROIC ≈ 51%
        BigDecimal wacc = new BigDecimal("0.09");
        // Revenue uniforme para margen FCF estable (no requerido por CompanyFinancials,
        // se estima internamente con los FCF históricos)

        int score = calculator.calculate(financials, wacc);

        assertEquals(100, score,
                "Todos los indicadores positivos deben dar score=100, fue: " + score);
    }

    @Test
    void calculate_zeroScore_allNegativeSignals() {
        // FCF decreciente (CAGR < 0%), algún año negativo,
        // ROIC < WACC, netDebt/ebitda > 4x, margen FCF cayendo
        var financials = new CompanyFinancials(
                "WEAK",
                List.of(
                        new BigDecimal("50000000000"),    // año 1
                        new BigDecimal("-5000000000"),    // año 2 negativo — FCF Consistency = 0
                        new BigDecimal("30000000000"),    // año 3
                        new BigDecimal("20000000000"),    // año 4
                        new BigDecimal("10000000000")     // año 5 — CAGR negativo
                ),
                new BigDecimal("500000000000"),  // totalDebt enorme
                new BigDecimal("10000000000"),   // cash
                new BigDecimal("50000000000"),   // totalEquity
                new BigDecimal("20000000000"),   // interestExpense
                new BigDecimal("1000000000"),
                new BigDecimal("2.0"),
                1_000_000_000L,
                new BigDecimal("80000000000"),   // ebitda — netDebt = 490B; ratio = 490/80 = 6.1x > 4x
                new BigDecimal("200000000000"),
                List.of(),
                "Energy"
        );
        // WACC alto para que ROIC < WACC
        // NOPAT ≈ 80B * 0.99 ≈ 79B; investedCapital = 500B + 50B - 10B = 540B → ROIC ≈ 14.6%
        // Usamos WACC del 20% para asegurar ROIC < WACC
        BigDecimal wacc = new BigDecimal("0.20");

        int score = calculator.calculate(financials, wacc);

        assertEquals(0, score,
                "Todos los indicadores negativos deben dar score=0, fue: " + score);
    }

    @Test
    void calculate_partialScore_mixedSignals() {
        // FCF positivos y creciendo (20 pts FCF Growth + 20 pts Consistency)
        // pero netDebt/ebitda > 4x (0 pts Leverage)
        // y ROIC > WACC (20 pts)
        // y margen FCF estable (20 pts)
        // → esperado: 80 puntos (falla solo Leverage)
        var financials = new CompanyFinancials(
                "MIX",
                List.of(
                        new BigDecimal("60000000000"),
                        new BigDecimal("70000000000"),
                        new BigDecimal("80000000000"),
                        new BigDecimal("90000000000"),
                        new BigDecimal("100000000000")   // CAGR ~13.6%
                ),
                new BigDecimal("600000000000"),  // totalDebt enorme → netDebt/ebitda > 4x
                new BigDecimal("20000000000"),   // cash
                new BigDecimal("100000000000"),  // totalEquity
                new BigDecimal("15000000000"),   // interestExpense
                new BigDecimal("20000000000"),   // incomeTaxExpense
                new BigDecimal("1.1"),
                5_000_000_000L,
                new BigDecimal("120000000000"),  // ebitda — netDebt = 580B; ratio = 580/120 ≈ 4.83x > 4x
                new BigDecimal("800000000000"),
                List.of(),
                "Industrials"
        );
        // ROIC = NOPAT / investedCapital
        // NOPAT = 120B × (1 - 20B / (120B - 15B)) = 120B × (1 - 0.19) ≈ 120B × 0.81 = 97B
        // investedCapital = 600B + 100B - 20B = 680B → ROIC ≈ 14.3%
        // WACC = 9% → ROIC > WACC ✓
        BigDecimal wacc = new BigDecimal("0.09");

        int score = calculator.calculate(financials, wacc);

        assertEquals(80, score,
                "Con leverage excesivo y el resto positivo debe dar 80 pts, fue: " + score);
    }

    @Test
    void calculate_roicDimension_requiresWaccForComparison() {
        // Mismos financials, distinto WACC → el score cambia solo en la dimensión ROIC vs WACC
        var financials = new CompanyFinancials(
                "ROIC_TEST",
                List.of(
                        new BigDecimal("50000000000"),
                        new BigDecimal("60000000000"),
                        new BigDecimal("70000000000")    // CAGR ~18.3%
                ),
                new BigDecimal("100000000000"),
                new BigDecimal("30000000000"),
                new BigDecimal("150000000000"),
                new BigDecimal("3000000000"),
                new BigDecimal("18000000000"),
                new BigDecimal("1.0"),
                3_000_000_000L,
                new BigDecimal("80000000000"),   // ebitda
                new BigDecimal("600000000000"),
                List.of(),
                null
        );
        // ROIC = NOPAT/investedCapital = (80B × ~0.78) / (100B + 150B - 30B) = 62B / 220B ≈ 28%
        BigDecimal lowWacc  = new BigDecimal("0.10");  // ROIC 28% > WACC 10% → +20 pts
        BigDecimal highWacc = new BigDecimal("0.40");  // ROIC 28% < WACC 40% → +0 pts

        int scoreLowWacc  = calculator.calculate(financials, lowWacc);
        int scoreHighWacc = calculator.calculate(financials, highWacc);

        assertEquals(20, scoreLowWacc - scoreHighWacc,
                "La dimensión ROIC vs WACC debe aportar exactamente 20 puntos de diferencia. "
                + "lowWacc=" + scoreLowWacc + " highWacc=" + scoreHighWacc);
    }

    @Test
    void calculate_leverageDimension_intermediateRange_gives10Points() {
        // netDebt/ebitda entre 2x y 4x → 10 puntos (intermedio)
        var financials = new CompanyFinancials(
                "MID_LEVER",
                List.of(
                        new BigDecimal("40000000000"),
                        new BigDecimal("50000000000"),
                        new BigDecimal("60000000000")   // CAGR ~22.5%
                ),
                new BigDecimal("200000000000"),  // totalDebt
                new BigDecimal("20000000000"),   // cash → netDebt = 180B
                new BigDecimal("100000000000"),  // totalEquity
                new BigDecimal("5000000000"),
                new BigDecimal("15000000000"),
                new BigDecimal("1.0"),
                2_000_000_000L,
                new BigDecimal("60000000000"),   // ebitda — ratio = 180/60 = 3x → intermedio
                new BigDecimal("400000000000"),
                List.of(),
                null
        );
        BigDecimal wacc = new BigDecimal("0.09");

        int score = calculator.calculate(financials, wacc);

        // FCF Growth 20 + Consistency 20 + ROIC (depende) + Leverage 10 + Margin 20 = ≥70
        assertTrue(score >= 60 && score <= 90,
                "Con leverage intermedio el score debe estar entre 60 y 90. Fue: " + score);
        // Verificar específicamente que leverage aporta 10 (no 0 ni 20)
        // Para aislar: score sin leverage debería ser score - 10 si cambiamos la deuda
        var financialsNoDebt = new CompanyFinancials(
                "NO_LEVER",
                financials.historicalFcf(),
                BigDecimal.ZERO, financials.cashAndEquivalents(), financials.totalEquity(),
                financials.interestExpense(), financials.incomeTaxExpense(), financials.beta(),
                financials.sharesOutstanding(), financials.ebitda(), financials.marketCap(),
                List.of(), null
        );
        int scoreNoDebt = calculator.calculate(financialsNoDebt, wacc);
        assertEquals(10, scoreNoDebt - score,
                "Sin deuda debe sumar 10 pts más que con leverage intermedio (3x). "
                + "noDebt=" + scoreNoDebt + " midLever=" + score);
    }

    @Test
    void calculate_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, new BigDecimal("0.09")));
    }

    @Test
    void calculate_nullWacc_throwsException() {
        var financials = new CompanyFinancials(
                "TEST",
                List.of(new BigDecimal("10000000000")),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100000000000"),
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                1_000_000_000L, new BigDecimal("10000000000"),
                BigDecimal.ZERO, List.of(), null
        );
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(financials, null));
    }

    @Test
    void calculate_scoreIsAlwaysBetween0And100() {
        var financials = new CompanyFinancials(
                "BOUND_TEST",
                List.of(new BigDecimal("10000000000"), new BigDecimal("20000000000")),
                new BigDecimal("100000000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("50000000000"),
                new BigDecimal("3000000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("1.5"),
                1_000_000_000L,
                new BigDecimal("30000000000"),
                new BigDecimal("200000000000"),
                List.of(),
                null
        );

        int score = calculator.calculate(financials, new BigDecimal("0.09"));

        assertTrue(score >= 0 && score <= 100,
                "El score debe estar siempre entre 0 y 100. Fue: " + score);
    }
}
