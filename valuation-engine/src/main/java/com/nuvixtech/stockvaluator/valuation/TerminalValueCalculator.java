package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calcula el Valor Terminal usando el Gordon Growth Model y lo descuenta al presente.
 */
public class TerminalValueCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * Calcula el valor presente del Terminal Value.
     *
     * <p>Fórmulas:
     * <ul>
     *   <li>TV = FCF_N × (1 + g) / (WACC - g)  [Gordon Growth Model]</li>
     *   <li>PV(TV) = TV / (1 + WACC)^N</li>
     * </ul>
     *
     * @param lastProjectedFcf   FCF del último año de proyección
     * @param wacc               costo ponderado de capital
     * @param terminalGrowthRate tasa de crecimiento perpetua (debe ser < WACC)
     * @param projectionYears    número de años de proyección (para descontar al presente)
     * @return valor presente del Terminal Value
     * @throws IllegalArgumentException si WACC ≤ terminalGrowthRate
     */
    public BigDecimal calculate(BigDecimal lastProjectedFcf, BigDecimal wacc,
                                BigDecimal terminalGrowthRate, int projectionYears) {
        Objects.requireNonNull(lastProjectedFcf, "lastProjectedFcf no puede ser null");
        Objects.requireNonNull(wacc, "wacc no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");

        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }
        if (wacc.compareTo(terminalGrowthRate) <= 0) {
            throw new IllegalArgumentException(
                    "WACC (" + wacc + ") debe ser mayor que terminalGrowthRate (" + terminalGrowthRate + ")");
        }

        // TV = FCF_N × (1 + g) / (WACC - g)
        BigDecimal terminalValue = lastProjectedFcf
                .multiply(BigDecimal.ONE.add(terminalGrowthRate, MC), MC)
                .divide(wacc.subtract(terminalGrowthRate, MC), MC);

        // PV(TV) = TV / (1 + WACC)^N
        BigDecimal discountFactor = BigDecimal.ONE.add(wacc, MC).pow(projectionYears, MC);

        return terminalValue.divide(discountFactor, MC)
                .setScale(0, RoundingMode.HALF_UP);
    }
}
