package com.nuvixtech.stockvaluator.api.valuation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ValuationResponse(
        String ticker,
        String companyName,
        String sector,
        BigDecimal intrinsicValuePerShare,
        BigDecimal marketPrice,
        BigDecimal marginOfSafety,
        String verdict,
        BigDecimal wacc,
        BigDecimal terminalGrowthRate,
        int projectionYears,
        BigDecimal terminalValue,
        BigDecimal netDebt,
        BigDecimal betaUsed,
        List<ScenarioResultDto> scenarios,
        Map<String, Map<String, BigDecimal>> sensitivityMatrix,
        Map<String, BigDecimal> breakdown,
        LocalDateTime lastUpdated
) {}
