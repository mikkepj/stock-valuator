package com.nuvixtech.stockvaluator.api.valuation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Body opcional para POST /api/v1/valuations/{ticker}/calculate.
 * Permite sobreescribir parámetros del modelo para un cálculo puntual
 * sin modificar la configuración global.
 */
public record CalculateRequest(
        @DecimalMin("0.1") @DecimalMax("5.0")
        BigDecimal betaOverride
) {
    public CalculateRequest() {
        this(null);
    }
}
