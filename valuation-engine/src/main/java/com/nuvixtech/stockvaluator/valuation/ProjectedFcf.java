package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Representa un año de FCF proyectado con la tasa de crecimiento aplicada.
 */
public record ProjectedFcf(
        int year,
        BigDecimal projectedValue,
        BigDecimal growthRateApplied
) {
    public ProjectedFcf {
        if (year <= 0) {
            throw new IllegalArgumentException("year debe ser mayor que cero, fue: " + year);
        }
        Objects.requireNonNull(projectedValue, "projectedValue no puede ser null");
        Objects.requireNonNull(growthRateApplied, "growthRateApplied no puede ser null");
    }
}
