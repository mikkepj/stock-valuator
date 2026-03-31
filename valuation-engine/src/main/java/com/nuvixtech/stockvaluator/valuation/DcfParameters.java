package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Parámetros de configuración para el cálculo DCF.
 */
public record DcfParameters(
        BigDecimal riskFreeRate,
        BigDecimal marketRiskPremium,
        BigDecimal terminalGrowthRate,
        int projectionYears,
        BigDecimal marketPrice
) {
    public DcfParameters {
        Objects.requireNonNull(riskFreeRate, "riskFreeRate no puede ser null");
        Objects.requireNonNull(marketRiskPremium, "marketRiskPremium no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        Objects.requireNonNull(marketPrice, "marketPrice no puede ser null");

        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }
        if (marketPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("marketPrice debe ser mayor que cero");
        }
    }

    /** Parámetros por defecto usados en producción. */
    public static DcfParameters defaults(BigDecimal marketPrice) {
        return new DcfParameters(
                new BigDecimal("0.045"),
                new BigDecimal("0.055"),
                new BigDecimal("0.025"),
                10,
                marketPrice
        );
    }
}
