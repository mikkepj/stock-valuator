package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Percentiles de la distribución de valores intrínsecos obtenidos por simulación Monte Carlo.
 */
public record MonteCarloResult(
        BigDecimal p10,
        BigDecimal p25,
        BigDecimal p50,
        BigDecimal p75,
        BigDecimal p90,
        int simulationCount
) {
    public MonteCarloResult {
        Objects.requireNonNull(p10, "p10 no puede ser null");
        Objects.requireNonNull(p25, "p25 no puede ser null");
        Objects.requireNonNull(p50, "p50 no puede ser null");
        Objects.requireNonNull(p75, "p75 no puede ser null");
        Objects.requireNonNull(p90, "p90 no puede ser null");
        if (simulationCount <= 0) {
            throw new IllegalArgumentException("simulationCount debe ser mayor que cero");
        }
    }
}
