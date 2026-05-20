package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Calcula el Valor Terminal usando Gordon Growth Model y/o Exit Multiple sectorial.
 */
public class TerminalValueCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    // EV/EBITDA de salida por sector (Damodaran industry multiples)
    private static final java.util.Map<String, Integer> SECTOR_EXIT_MULTIPLES =
            java.util.Map.ofEntries(
                    java.util.Map.entry("Technology",          20),
                    java.util.Map.entry("Semiconductors",      18),
                    java.util.Map.entry("Software",            25),
                    java.util.Map.entry("Healthcare",          15),
                    java.util.Map.entry("Consumer Defensive",  14),
                    java.util.Map.entry("Consumer Cyclical",   13),
                    java.util.Map.entry("Energy",               8),
                    java.util.Map.entry("Financials",          12),
                    java.util.Map.entry("Industrials",         12),
                    java.util.Map.entry("Utilities",           10),
                    java.util.Map.entry("Real Estate",         18),
                    java.util.Map.entry("Communication",       14),
                    java.util.Map.entry("Materials",           10),
                    java.util.Map.entry("Default",             14)
            );

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

    /**
     * Calcula el valor presente del Terminal Value usando Exit Multiple sectorial.
     *
     * <p>Fórmula: TV = EBITDA_N × sectorMultiple; PV(TV) = TV / (1 + WACC)^N
     *
     * @param ebitdaLastYear EBITDA proyectado del último año de proyección
     * @param wacc           costo ponderado de capital
     * @param sector         sector de la empresa (ej: "Technology", "Energy")
     * @param projectionYears número de años de proyección
     * @return valor presente del Terminal Value por exit multiple
     */
    public BigDecimal calculateExitMultiple(BigDecimal ebitdaLastYear, BigDecimal wacc,
                                            String sector, int projectionYears) {
        Objects.requireNonNull(ebitdaLastYear, "ebitdaLastYear no puede ser null");
        Objects.requireNonNull(wacc, "wacc no puede ser null");
        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }

        int multiple = SECTOR_EXIT_MULTIPLES.getOrDefault(
                sector != null ? sector : "Default", SECTOR_EXIT_MULTIPLES.get("Default"));

        BigDecimal terminalValue = ebitdaLastYear.multiply(BigDecimal.valueOf(multiple), MC);
        BigDecimal discountFactor = BigDecimal.ONE.add(wacc, MC).pow(projectionYears, MC);

        return terminalValue.divide(discountFactor, MC)
                .setScale(0, RoundingMode.HALF_UP);
    }

    /** Retorna el múltiplo EV/EBITDA de salida para un sector dado. */
    public int getExitMultipleForSector(String sector) {
        return SECTOR_EXIT_MULTIPLES.getOrDefault(
                sector != null ? sector : "Default", SECTOR_EXIT_MULTIPLES.get("Default"));
    }
}
