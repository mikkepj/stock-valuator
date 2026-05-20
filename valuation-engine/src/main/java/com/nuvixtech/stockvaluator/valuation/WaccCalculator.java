package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calcula el Weighted Average Cost of Capital (WACC) usando CAPM para el costo
 * de equity y el costo efectivo de deuda ajustado por spread crediticio (tablas Damodaran).
 */
public class WaccCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 10;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.21");

    // Prima de tamaño sobre Ke según market cap (Duff & Phelps / Damodaran)
    private static final long CAP_MEGA  = 100_000_000_000L; // >$100B → 0%
    private static final long CAP_LARGE =  10_000_000_000L; // $10B–$100B → 0.5%
    private static final long CAP_MID   =   2_000_000_000L; // $2B–$10B → 1.0%
    private static final long CAP_SMALL =     300_000_000L; // $300M–$2B → 1.5%
                                                             // <$300M → 2.0%
    // Tabla de spreads crediticios sobre riskFreeRate según Interest Coverage Ratio (Damodaran)
    // Formato: {ICR_mínimo, spread}; se evalúa de mayor a menor ICR
    private static final double[][] CREDIT_SPREAD_TABLE = {
            {8.50, 0.0063},   // AAA/AA
            {6.50, 0.0078},   // A+
            {5.50, 0.0098},   // A
            {4.25, 0.0113},   // A-
            {3.00, 0.0167},   // BBB
            {2.50, 0.0222},   // BB+
            {2.00, 0.0283},   // BB
            {1.50, 0.0353},   // B+
            {1.25, 0.0423},   // B
            {0.80, 0.0593},   // B-
            {0.00, 0.0864},   // CCC y menor
    };

    /**
     * Calcula el WACC de una empresa.
     *
     * <p>Fórmulas:
     * <ul>
     *   <li>Cost of Equity (Ke) = Rf + Beta × (Rm - Rf)  [CAPM]</li>
     *   <li>Cost of Debt (Kd) = interestExpense / totalDebt × (1 - taxRate)</li>
     *   <li>WACC = E/(E+D) × Ke + D/(E+D) × Kd</li>
     * </ul>
     *
     * @param financials         datos financieros de la empresa
     * @param riskFreeRate       tasa libre de riesgo (ej: 0.045)
     * @param marketRiskPremium  prima de riesgo de mercado (ej: 0.055)
     * @return WACC como decimal (ej: 0.089 para 8.9%)
     */
    public BigDecimal calculate(CompanyFinancials financials,
                                BigDecimal riskFreeRate,
                                BigDecimal marketRiskPremium) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(riskFreeRate, "riskFreeRate no puede ser null");
        Objects.requireNonNull(marketRiskPremium, "marketRiskPremium no puede ser null");

        BigDecimal costOfEquity = calculateCostOfEquity(
                financials.beta(), riskFreeRate, marketRiskPremium, financials.equityValue());

        BigDecimal totalDebt = financials.totalDebt();
        BigDecimal totalEquity = financials.totalEquity();

        // Si no hay deuda, WACC = Cost of Equity
        if (totalDebt.compareTo(BigDecimal.ZERO) == 0) {
            return costOfEquity.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal taxRate = calculateEffectiveTaxRate(
                financials.incomeTaxExpense(), financials.ebitda(), financials.interestExpense());
        BigDecimal costOfDebt = calculateCostOfDebt(
                financials.interestExpense(), financials.ebitda(), totalDebt, taxRate, riskFreeRate);

        // Usar market cap si está disponible; si no, valor en libros (totalEquity)
        BigDecimal equityValue = financials.equityValue();
        BigDecimal totalCapital = equityValue.add(totalDebt, MC);
        BigDecimal equityWeight = equityValue.divide(totalCapital, MC);
        BigDecimal debtWeight = totalDebt.divide(totalCapital, MC);

        return equityWeight.multiply(costOfEquity, MC)
                .add(debtWeight.multiply(costOfDebt, MC), MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Ke = Rf + Beta × MRP + sizeRiskPremium */
    private BigDecimal calculateCostOfEquity(BigDecimal beta, BigDecimal riskFreeRate,
                                              BigDecimal marketRiskPremium, BigDecimal equityValue) {
        BigDecimal capm = riskFreeRate.add(beta.multiply(marketRiskPremium, MC), MC);
        BigDecimal sizeRiskPremium = calculateSizeRiskPremium(equityValue);
        return capm.add(sizeRiskPremium, MC);
    }

    /** Prima de tamaño según market cap (Duff & Phelps). Solo aplica cuando equityValue = market cap real. */
    BigDecimal calculateSizeRiskPremium(BigDecimal equityValue) {
        if (equityValue == null || equityValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        long cap = equityValue.longValue();
        if (cap >= CAP_MEGA)  return BigDecimal.ZERO;
        if (cap >= CAP_LARGE) return new BigDecimal("0.005");
        if (cap >= CAP_MID)   return new BigDecimal("0.010");
        if (cap >= CAP_SMALL) return new BigDecimal("0.015");
        return new BigDecimal("0.020");
    }

    /**
     * Kd = (riskFreeRate + creditSpread) × (1 - taxRate).
     * El spread se determina por el Interest Coverage Ratio (ebitda / interestExpense)
     * usando las tablas de Damodaran. Fallback a interestExpense/totalDebt si ICR no aplica.
     */
    private BigDecimal calculateCostOfDebt(BigDecimal interestExpense, BigDecimal ebitda,
                                            BigDecimal totalDebt, BigDecimal taxRate,
                                            BigDecimal riskFreeRate) {
        if (interestExpense.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal creditSpread = calculateCreditSpread(ebitda, interestExpense);
        BigDecimal grossCostOfDebt = riskFreeRate.add(creditSpread, MC);
        BigDecimal taxShield = BigDecimal.ONE.subtract(taxRate, MC);
        return grossCostOfDebt.multiply(taxShield, MC);
    }

    /** Determina el spread crediticio según el Interest Coverage Ratio (ICR = ebitda / interestExpense). */
    BigDecimal calculateCreditSpread(BigDecimal ebitda, BigDecimal interestExpense) {
        if (interestExpense.compareTo(BigDecimal.ZERO) <= 0) {
            // Sin gasto de intereses → empresa sin deuda o con cobertura perfecta → spread AAA
            return new BigDecimal("0.0063");
        }
        if (ebitda.compareTo(BigDecimal.ZERO) <= 0) {
            // EBITDA negativo → spread máximo CCC
            return new BigDecimal("0.0864");
        }
        double icr = ebitda.divide(interestExpense, MC).doubleValue();
        for (double[] entry : CREDIT_SPREAD_TABLE) {
            if (icr >= entry[0]) {
                return BigDecimal.valueOf(entry[1]);
            }
        }
        return new BigDecimal("0.0864");
    }

    /**
     * Calcula la tasa impositiva efectiva real: incomeTaxExpense / (ebitda - interestExpense).
     * Fallback a 21% si el pretaxIncome es <= 0 o la tasa calculada queda fuera del rango [1%, 50%].
     */
    BigDecimal calculateEffectiveTaxRate(BigDecimal incomeTaxExpense, BigDecimal ebitda,
                                         BigDecimal interestExpense) {
        if (incomeTaxExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_TAX_RATE;
        }
        BigDecimal pretaxIncome = ebitda.subtract(interestExpense, MC);
        if (pretaxIncome.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_TAX_RATE;
        }
        BigDecimal effectiveRate = incomeTaxExpense.divide(pretaxIncome, MC);
        BigDecimal MIN_RATE = new BigDecimal("0.01");
        BigDecimal MAX_RATE = new BigDecimal("0.50");
        if (effectiveRate.compareTo(MIN_RATE) < 0 || effectiveRate.compareTo(MAX_RATE) > 0) {
            return DEFAULT_TAX_RATE;
        }
        return effectiveRate;
    }
}
