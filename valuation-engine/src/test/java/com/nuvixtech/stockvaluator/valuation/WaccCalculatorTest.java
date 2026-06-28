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
    void calculate_msftWithMarketCapAndImpliedErp_producesMarketAlignedWacc() {
        // MSFT FY2024: market cap ~$2.78T, deuda $97B → equity weight ~97%
        var financials = new CompanyFinancials(
                "MSFT",
                List.of(new BigDecimal("75000000000")),
                new BigDecimal("97000000000"),    // totalDebt
                new BigDecimal("18000000000"),    // cashAndEquivalents
                new BigDecimal("268000000000"),   // totalEquity (valor en libros)
                new BigDecimal("2385000000"),     // interestExpense
                new BigDecimal("21795000000"),    // incomeTaxExpense
                new BigDecimal("1.107"),          // beta real
                7433000000L,                      // sharesOutstanding
                new BigDecimal("140000000000"),   // ebitda
                new BigDecimal("2780000000000"),  // marketCap ~$2.78T
                List.of(),
                null
        );
        // ERP implícito de mercado (~4.5%)
        BigDecimal impliedErp = new BigDecimal("0.045");

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, impliedErp);

        // Con market cap $2.78T y deuda $97B → equity weight ~97%
        // Ke = 4.5% + 1.107×4.5% ≈ 9.48%
        // WACC ≈ 0.97×9.48% + 0.03×Kd ≈ ~9.2%–9.5%
        assertTrue(wacc.compareTo(new BigDecimal("0.08")) > 0,
                "WACC MSFT con market cap debe ser > 8%, fue: " + wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.11")) < 0,
                "WACC MSFT con market cap debe ser < 11%, fue: " + wacc);
    }

    @Test
    void calculate_effectiveTaxRate_usesRealRateWhenAvailable() {
        // NVDA FY2024: incomeTaxExpense=2.7B, ebitda≈27B, interestExpense≈0.3B
        // pretaxIncome ≈ ebitda - interestExpense = 26.7B
        // taxRate efectiva = 2.7B / 26.7B ≈ 10.1% (muy por debajo del 21% fijo)
        // Con Kd menor (menos tax shield a 21%), el WACC resultante debe ser distinto
        var financialsLowTax = new CompanyFinancials(
                "NVDA",
                List.of(new BigDecimal("40000000000")),
                new BigDecimal("10000000000"),   // totalDebt
                new BigDecimal("5000000000"),    // cash
                new BigDecimal("50000000000"),   // totalEquity
                new BigDecimal("300000000"),     // interestExpense
                new BigDecimal("2700000000"),    // incomeTaxExpense (tasa efectiva ~10%)
                new BigDecimal("1.5"),           // beta
                2000000000L,
                new BigDecimal("27000000000"),   // ebitda
                BigDecimal.ZERO,
                List.of(),
                null
        );
        var financialsHighTax = new CompanyFinancials(
                "NVDA_HIGH",
                List.of(new BigDecimal("40000000000")),
                new BigDecimal("10000000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("50000000000"),
                new BigDecimal("300000000"),
                new BigDecimal("7000000000"),    // incomeTaxExpense (tasa efectiva ~26%)
                new BigDecimal("1.5"),
                2000000000L,
                new BigDecimal("27000000000"),   // mismo ebitda
                BigDecimal.ZERO,
                List.of(),
                null
        );

        var waccLowTax = calculator.calculate(financialsLowTax, RISK_FREE_RATE, MARKET_RISK_PREMIUM);
        var waccHighTax = calculator.calculate(financialsHighTax, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // Mayor tasa efectiva → mayor tax shield → Kd menor → WACC menor
        assertTrue(waccHighTax.compareTo(waccLowTax) < 0,
                "Mayor tasa impositiva efectiva debe producir WACC menor (más tax shield). " +
                "lowTax=" + waccLowTax + " highTax=" + waccHighTax);
    }

    @Test
    void calculate_effectiveTaxRate_fallsBackTo21WhenOutOfRange() {
        // incomeTaxExpense > pretaxIncome → tasa calculada > 100%, usar fallback 21%
        // También: ebitda - interestExpense <= 0 → pretaxIncome inválido, usar 21%
        var financialsNegativePretax = new CompanyFinancials(
                "TEST_NEGATIVE",
                List.of(new BigDecimal("5000000000")),
                new BigDecimal("20000000000"),
                new BigDecimal("2000000000"),
                new BigDecimal("10000000000"),
                new BigDecimal("8000000000"),    // interestExpense > ebitda → pretaxIncome negativo
                new BigDecimal("1000000000"),
                new BigDecimal("1.0"),
                500000000L,
                new BigDecimal("5000000000"),    // ebitda < interestExpense
                BigDecimal.ZERO,
                List.of(),
                null
        );

        // No debe lanzar excepción; debe retornar WACC válido usando fallback 21%
        var wacc = calculator.calculate(financialsNegativePretax, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertNotNull(wacc);
        assertTrue(wacc.compareTo(BigDecimal.ZERO) > 0,
                "WACC debe ser positivo incluso con pretaxIncome negativo, fue: " + wacc);
    }

    @Test
    void calculate_effectiveTaxRate_capsAt50Percent() {
        // Si el ratio incomeTaxExpense/pretaxIncome > 50%, usar fallback 21%
        // (indica anomalía contable, no tasa real)
        var financialsAnomalousTax = new CompanyFinancials(
                "TEST_CAP",
                List.of(new BigDecimal("5000000000")),
                new BigDecimal("10000000000"),
                new BigDecimal("1000000000"),
                new BigDecimal("20000000000"),
                new BigDecimal("500000000"),
                new BigDecimal("9000000000"),    // impuesto = 9B sobre pretax ≈ 9.5B → >50%
                new BigDecimal("1.0"),
                500000000L,
                new BigDecimal("10000000000"),   // ebitda
                BigDecimal.ZERO,
                List.of(),
                null
        );

        // Debe retornar WACC válido usando el fallback
        var wacc = calculator.calculate(financialsAnomalousTax, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertNotNull(wacc);
        assertTrue(wacc.compareTo(BigDecimal.ZERO) > 0,
                "WACC debe ser positivo con tasa anomalía, fue: " + wacc);
    }

    @Test
    void calculate_debtSpread_highCoverageRatio_usesLowSpread() {
        // ICR = ebitda / interestExpense = 27B / 300M = 90x → spread 0.63% (AAA/AA)
        // Kd = (riskFreeRate + 0.0063) × (1 - taxRate)
        // Kd_spread debe ser menor que Kd_ratio para esta empresa con deuda muy manejable
        var financials = new CompanyFinancials(
                "AAPL_HIGH_ICR",
                List.of(new BigDecimal("100000000000")),
                new BigDecimal("110000000000"),  // totalDebt
                new BigDecimal("50000000000"),
                new BigDecimal("60000000000"),
                new BigDecimal("300000000"),     // interestExpense muy bajo → ICR ~90x
                new BigDecimal("29000000000"),
                new BigDecimal("1.24"),
                15000000000L,
                new BigDecimal("27000000000"),   // ebitda
                new BigDecimal("2800000000000"), // marketCap
                List.of(),
                null
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // WACC debe ser razonable y positivo
        assertNotNull(wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.05")) > 0,
                "WACC debe ser > 5%, fue: " + wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.15")) < 0,
                "WACC debe ser < 15%, fue: " + wacc);
        // El breakdown debe contener debtSpread
        // (verificado vía DcfCalculatorTest — aquí solo comprobamos WACC razonable)
    }

    @Test
    void calculate_debtSpread_lowCoverageRatio_usesHighSpread() {
        // ICR = ebitda / interestExpense = 2B / 2.5B = 0.8x → spread 8.64% (CCC)
        // Empresa con ICR < 0.8 → costo de deuda muy alto
        var financialsDistressed = new CompanyFinancials(
                "DISTRESSED",
                List.of(new BigDecimal("1000000000")),
                new BigDecimal("20000000000"),   // totalDebt alto
                new BigDecimal("500000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("2500000000"),    // interestExpense casi igual a ebitda → ICR ~0.8x
                new BigDecimal("200000000"),
                new BigDecimal("1.8"),
                500000000L,
                new BigDecimal("2000000000"),    // ebitda
                BigDecimal.ZERO,
                List.of(),
                null
        );
        // Empresa sana equivalente con el mismo beta pero sin problemas de deuda
        var financialsHealthy = new CompanyFinancials(
                "HEALTHY",
                List.of(new BigDecimal("1000000000")),
                new BigDecimal("20000000000"),
                new BigDecimal("500000000"),
                new BigDecimal("5000000000"),
                new BigDecimal("200000000"),     // interestExpense bajo → ICR ~10x
                new BigDecimal("1000000000"),
                new BigDecimal("1.8"),
                500000000L,
                new BigDecimal("2000000000"),
                BigDecimal.ZERO,
                List.of(),
                null
        );

        var waccDistressed = calculator.calculate(financialsDistressed, RISK_FREE_RATE, MARKET_RISK_PREMIUM);
        var waccHealthy = calculator.calculate(financialsHealthy, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        assertTrue(waccDistressed.compareTo(waccHealthy) > 0,
                "Empresa en dificultades (ICR<0.8) debe tener WACC mayor que empresa sana. " +
                "distressed=" + waccDistressed + " healthy=" + waccHealthy);
    }

    @Test
    void calculate_debtSpread_aAACompany_waccReflectsLowSpread() {
        // ICR = 100B ebitda / 1B interestExpense = 100x → AAA, spread = 0.63%
        // Kd = (riskFreeRate=4.5% + spread=0.63%) × (1 - taxRate)
        // Con taxRate=21%: Kd = 5.13% × 0.79 ≈ 4.05%
        // Método anterior (interestExpense/totalDebt): 1B/50B = 2% × 0.79 = 1.58%
        // Con spread: Kd sube de 1.58% → 4.05% para empresa AAA con deuda barata heredada
        var financials = new CompanyFinancials(
                "AAA_COMPANY",
                List.of(new BigDecimal("80000000000")),
                new BigDecimal("50000000000"),   // totalDebt
                new BigDecimal("10000000000"),
                new BigDecimal("200000000000"),  // totalEquity
                new BigDecimal("1000000000"),    // interestExpense bajo (1B) → ICR=100x
                new BigDecimal("20000000000"),   // incomeTaxExpense
                new BigDecimal("1.0"),
                5000000000L,
                new BigDecimal("100000000000"), // ebitda
                BigDecimal.ZERO,
                List.of(),
                null
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // Equity weight = 200B/(200B+50B) = 80%, Ke = 4.5%+1.0×5.5% = 10%
        // Con spread Kd≈4.05%: WACC ≈ 0.80×10% + 0.20×4.05% ≈ 8.81%
        // Sin spread Kd≈1.58%: WACC ≈ 0.80×10% + 0.20×1.58% ≈ 8.32%
        // El valor con spread debe ser mayor que 8.5% (por encima del método anterior)
        assertTrue(wacc.compareTo(new BigDecimal("0.085")) > 0,
                "Con spread AAA, WACC debe reflejar Kd basado en riskFreeRate+spread, fue: " + wacc);
    }

    @Test
    void calculate_debtSpread_fallbackWhenNoInterestExpense() {
        // Si interestExpense = 0: ICR indefinido → no hay costo de deuda, WACC = Ke
        var financials = buildFinancials(
                new BigDecimal("50000000000"),   // tiene deuda pero no paga intereses
                new BigDecimal("50000000000"),
                BigDecimal.ZERO,                 // interestExpense = 0
                new BigDecimal("8000000000"),
                new BigDecimal("1.0")
        );

        var wacc = calculator.calculate(financials, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // Ke = 0.045 + 1.0×0.055 = 0.10; con Kd=0 y deuda, WACC < Ke
        assertNotNull(wacc);
        assertTrue(wacc.compareTo(BigDecimal.ZERO) > 0,
                "WACC debe ser positivo aunque interestExpense=0, fue: " + wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.10")) <= 0,
                "WACC no puede superar Ke cuando Kd=0, fue: " + wacc);
    }

    @Test
    void calculate_sizeRiskPremium_zeroForMegaCap() {
        // market cap > $100B → sizeRiskPremium = 0%
        // Verificado directamente: calculateSizeRiskPremium($2.78T) = 0
        var megaCapValue = new BigDecimal("2780000000000");
        var sizeRiskPremium = calculator.calculateSizeRiskPremium(megaCapValue);

        assertEquals(0, sizeRiskPremium.compareTo(BigDecimal.ZERO),
                "Mega cap >$100B debe tener sizeRiskPremium = 0, fue: " + sizeRiskPremium);
    }

    @Test
    void calculate_sizeRiskPremium_appliedForSmallCap() {
        // market cap $200M < $300M → sizeRiskPremium = 2.0%
        var microCapValue = new BigDecimal("200000000");
        var sizeRiskPremiumMicro = calculator.calculateSizeRiskPremium(microCapValue);

        assertEquals(0, sizeRiskPremiumMicro.compareTo(new BigDecimal("0.020")),
                "Micro cap <$300M debe tener sizeRiskPremium = 2.0%, fue: " + sizeRiskPremiumMicro);

        // market cap $5B → prima 1.0%
        var midCapValue = new BigDecimal("5000000000");
        var sizeRiskPremiumMid = calculator.calculateSizeRiskPremium(midCapValue);

        assertEquals(0, sizeRiskPremiumMid.compareTo(new BigDecimal("0.010")),
                "Mid cap $2B-$10B debe tener sizeRiskPremium = 1.0%, fue: " + sizeRiskPremiumMid);
    }

    @Test
    void calculate_sizeRiskPremium_addedToBreakdown() {
        // El breakdown del DcfCalculator debe incluir sizeRiskPremium
        // (verificado indirectamente: si WACC cambia con market cap, la prima fue aplicada)
        var financialsSmall = new CompanyFinancials(
                "SMALL_CHECK",
                List.of(new BigDecimal("1000000000")),
                BigDecimal.ZERO,                  // sin deuda → WACC = Ke puro
                BigDecimal.ZERO,
                new BigDecimal("500000000"),
                BigDecimal.ZERO,
                new BigDecimal("100000000"),
                new BigDecimal("1.0"),
                10000000L,
                new BigDecimal("200000000"),
                new BigDecimal("150000000"),      // $150M → prima 2.0%
                List.of(),
                null
        );

        var wacc = calculator.calculate(financialsSmall, RISK_FREE_RATE, MARKET_RISK_PREMIUM);

        // Ke sin prima = 4.5% + 1.0×5.5% = 10%; con prima 2% → 12%
        assertTrue(wacc.compareTo(new BigDecimal("0.115")) > 0,
                "WACC con prima de tamaño 2% debe ser > 11.5%, fue: " + wacc);
        assertTrue(wacc.compareTo(new BigDecimal("0.13")) < 0,
                "WACC con prima de tamaño 2% debe ser < 13%, fue: " + wacc);
    }

    @Test
    void calculate_nullFinancials_throwsException() {
        assertThrows(NullPointerException.class,
                () -> calculator.calculate(null, RISK_FREE_RATE, MARKET_RISK_PREMIUM));
    }

    // Helper para construir CompanyFinancials con los campos relevantes para WACC (sin market cap)
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
                new BigDecimal("50000000000"),
                BigDecimal.ZERO,
                List.of(),
                null
        );
    }
}
