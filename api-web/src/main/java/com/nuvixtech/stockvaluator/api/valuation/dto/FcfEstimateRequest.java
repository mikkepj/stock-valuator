package com.nuvixtech.stockvaluator.api.valuation.dto;

import jakarta.validation.constraints.NotEmpty;
import java.math.BigDecimal;
import java.util.List;

public record FcfEstimateRequest(
        @NotEmpty(message = "La lista de estimaciones no puede estar vacía")
        List<BigDecimal> estimates
) {}
