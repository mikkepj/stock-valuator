package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Proyecta FCFs futuros a partir de datos históricos usando CAGR con decay lineal
 * hacia la tasa de crecimiento terminal.
 */
public class FreeCashFlowProjector {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 10;

    /**
     * Proyecta FCFs futuros aplicando decay lineal desde el CAGR histórico hasta la tasa terminal.
     *
     * @param historicalFcf     FCFs históricos en orden ascendente (más antiguo primero)
     * @param terminalGrowthRate tasa de crecimiento terminal (ej: 0.025)
     * @param projectionYears   número de años a proyectar
     * @return lista de FCFs proyectados, uno por año
     */
    public List<ProjectedFcf> project(List<BigDecimal> historicalFcf,
                                       BigDecimal terminalGrowthRate,
                                       int projectionYears) {
        Objects.requireNonNull(historicalFcf, "historicalFcf no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }

        BigDecimal initialRate = calculateInitialRate(historicalFcf, terminalGrowthRate);
        BigDecimal lastFcf = lastPositiveFcf(historicalFcf);

        List<ProjectedFcf> projections = new ArrayList<>(projectionYears);
        BigDecimal currentFcf = lastFcf;

        for (int year = 1; year <= projectionYears; year++) {
            BigDecimal growthRate = interpolateRate(initialRate, terminalGrowthRate, year, projectionYears);
            currentFcf = currentFcf.multiply(BigDecimal.ONE.add(growthRate), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            projections.add(new ProjectedFcf(year, currentFcf, growthRate.setScale(SCALE, RoundingMode.HALF_UP)));
        }

        return projections;
    }

    /**
     * Calcula el CAGR sobre todos los datos históricos como tasa de arranque.
     * Si hay un solo dato, retorna la tasa terminal.
     */
    private BigDecimal calculateInitialRate(List<BigDecimal> historical, BigDecimal terminalRate) {
        if (historical.size() < 2) {
            return terminalRate;
        }

        BigDecimal first = historical.get(0);
        BigDecimal last = historical.get(historical.size() - 1);
        int periods = historical.size() - 1;

        // No se puede calcular CAGR si el primer valor es negativo o cero
        if (first.compareTo(BigDecimal.ZERO) <= 0 || last.compareTo(BigDecimal.ZERO) <= 0) {
            return terminalRate;
        }

        // CAGR = (last/first)^(1/periods) - 1
        double ratio = last.divide(first, MC).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / periods) - 1.0;

        BigDecimal cagrDecimal = BigDecimal.valueOf(cagr).setScale(SCALE, RoundingMode.HALF_UP);

        // Si el CAGR es menor que la terminal rate, usar la terminal rate
        if (cagrDecimal.compareTo(terminalRate) < 0) {
            return terminalRate;
        }

        return cagrDecimal;
    }

    /**
     * Interpolación lineal desde initialRate hasta terminalRate a lo largo de los años.
     * En el último año retorna exactamente terminalRate.
     */
    private BigDecimal interpolateRate(BigDecimal initialRate, BigDecimal terminalRate,
                                        int currentYear, int totalYears) {
        if (currentYear == totalYears) {
            return terminalRate;
        }
        // fraction va de 0 (año 1) a 1 (último año)
        double fraction = (double) (currentYear - 1) / (totalYears - 1);
        double interpolated = initialRate.doubleValue()
                + fraction * (terminalRate.doubleValue() - initialRate.doubleValue());
        return BigDecimal.valueOf(interpolated).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Retorna el último FCF positivo de la lista. Si todos son negativos, retorna el último.
     */
    private BigDecimal lastPositiveFcf(List<BigDecimal> historical) {
        for (int i = historical.size() - 1; i >= 0; i--) {
            if (historical.get(i).compareTo(BigDecimal.ZERO) > 0) {
                return historical.get(i);
            }
        }
        return historical.get(historical.size() - 1);
    }
}
