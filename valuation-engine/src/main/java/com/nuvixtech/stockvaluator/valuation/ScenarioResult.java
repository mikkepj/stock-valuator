package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;

/**
 * Resultado de un escenario de valuación DCF.
 * Captura el IV calculado bajo hipótesis específicas de crecimiento.
 */
public record ScenarioResult(
        String scenarioName,
        BigDecimal intrinsicValuePerShare,
        BigDecimal marginOfSafety,
        Verdict verdict,
        BigDecimal initialGrowthRate,
        BigDecimal terminalGrowthRate,
        BigDecimal wacc
) {}
