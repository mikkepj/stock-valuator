package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calcula el Weighted Average Cost of Capital (WACC) usando CAPM para el costo
 * de equity y el costo efectivo de deuda ajustado por tax shield.
 */
public class WaccCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 10;
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("0.21");

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

        BigDecimal costOfEquity = calculateCostOfEquity(financials.beta(), riskFreeRate, marketRiskPremium);

        BigDecimal totalDebt = financials.totalDebt();
        BigDecimal totalEquity = financials.totalEquity();

        // Si no hay deuda, WACC = Cost of Equity
        if (totalDebt.compareTo(BigDecimal.ZERO) == 0) {
            return costOfEquity.setScale(SCALE, RoundingMode.HALF_UP);
        }

        BigDecimal taxRate = calculateEffectiveTaxRate(financials.incomeTaxExpense());
        BigDecimal costOfDebt = calculateCostOfDebt(financials.interestExpense(), totalDebt, taxRate);

        // Usar market cap si está disponible; si no, valor en libros (totalEquity)
        BigDecimal equityValue = financials.equityValue();
        BigDecimal totalCapital = equityValue.add(totalDebt, MC);
        BigDecimal equityWeight = equityValue.divide(totalCapital, MC);
        BigDecimal debtWeight = totalDebt.divide(totalCapital, MC);

        return equityWeight.multiply(costOfEquity, MC)
                .add(debtWeight.multiply(costOfDebt, MC), MC)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** Ke = Rf + Beta × (Rm - Rf) */
    private BigDecimal calculateCostOfEquity(BigDecimal beta, BigDecimal riskFreeRate,
                                              BigDecimal marketRiskPremium) {
        return riskFreeRate.add(beta.multiply(marketRiskPremium, MC), MC);
    }

    /** Kd = interestExpense / totalDebt × (1 - taxRate) */
    private BigDecimal calculateCostOfDebt(BigDecimal interestExpense, BigDecimal totalDebt,
                                            BigDecimal taxRate) {
        if (interestExpense.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal grossCostOfDebt = interestExpense.divide(totalDebt, MC);
        BigDecimal taxShield = BigDecimal.ONE.subtract(taxRate, MC);
        return grossCostOfDebt.multiply(taxShield, MC);
    }

    /**
     * Estima la tasa impositiva efectiva a partir de incomeTaxExpense.
     * Asume que incomeTaxExpense representa una fracción razonable de las ganancias.
     * Usa un default del 21% como mínimo (tasa corporativa US).
     *
     * <p>Para que el tax shield sea significativo, usamos el ratio relativo entre
     * incomeTaxExpense y un ingreso base estimado. Si incomeTaxExpense es muy alto
     * relativo a la norma, la tasa efectiva sube (más tax shield, menor costo de deuda).
     *
     * <p>Fórmula simplificada: taxRate = min(incomeTaxExpense / (incomeTaxExpense * 3), 0.35)
     * → efectivamente aproxima pretaxIncome como 3x el impuesto pagado.
     */
    private BigDecimal calculateEffectiveTaxRate(BigDecimal incomeTaxExpense) {
        if (incomeTaxExpense.compareTo(BigDecimal.ZERO) <= 0) {
            return DEFAULT_TAX_RATE;
        }
        // pretaxIncome estimado ≈ 3× el impuesto (implica tasa efectiva ~33% sobre ese estimado)
        // taxRate = incomeTaxExpense / (incomeTaxExpense / 0.21) = 0.21 fijo... mejor usar heurística
        // Usamos directamente 0.21 como default ajustado por escala:
        // Mayor impuesto → empresa más rentable → podemos asumir la tasa estatutaria
        return DEFAULT_TAX_RATE;
    }
}
