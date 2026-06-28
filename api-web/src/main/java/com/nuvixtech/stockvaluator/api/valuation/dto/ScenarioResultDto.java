package com.nuvixtech.stockvaluator.api.valuation.dto;

import java.math.BigDecimal;

public record ScenarioResultDto(
        String scenarioName,
        BigDecimal intrinsicValuePerShare,
        BigDecimal marginOfSafety,
        String verdict,
        BigDecimal initialGrowthRate,
        BigDecimal terminalGrowthRate,
        BigDecimal wacc
) {}
