package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resultado completo del cálculo DCF para una empresa.
 */
public record ValuationResult(
        String ticker,
        BigDecimal intrinsicValuePerShare,
        BigDecimal marketPrice,
        BigDecimal marginOfSafety,
        Verdict verdict,
        BigDecimal wacc,
        BigDecimal terminalGrowthRate,
        int projectionYears,
        BigDecimal terminalValue,
        BigDecimal netDebt,
        List<ProjectedFcf> projectedFcfs,
        Map<String, Map<String, BigDecimal>> sensitivityMatrix,
        Map<String, BigDecimal> breakdown,
        MonteCarloResult monteCarloResult, // null si no se ejecutó simulación
        int qualityScore                   // 0–100
) {
    public ValuationResult {
        Objects.requireNonNull(ticker, "ticker no puede ser null");
        Objects.requireNonNull(intrinsicValuePerShare, "intrinsicValuePerShare no puede ser null");
        Objects.requireNonNull(marketPrice, "marketPrice no puede ser null");
        Objects.requireNonNull(marginOfSafety, "marginOfSafety no puede ser null");
        Objects.requireNonNull(verdict, "verdict no puede ser null");
        Objects.requireNonNull(wacc, "wacc no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        Objects.requireNonNull(terminalValue, "terminalValue no puede ser null");
        Objects.requireNonNull(netDebt, "netDebt no puede ser null");
        Objects.requireNonNull(projectedFcfs, "projectedFcfs no puede ser null");
        Objects.requireNonNull(sensitivityMatrix, "sensitivityMatrix no puede ser null");
        Objects.requireNonNull(breakdown, "breakdown no puede ser null");
        // monteCarloResult puede ser null (campo opcional)

        projectedFcfs = List.copyOf(projectedFcfs);
    }

    /**
     * Calcula el margen de seguridad: (intrinsicValue - marketPrice) / marketPrice * 100
     */
    public static BigDecimal calculateMarginOfSafety(BigDecimal intrinsicValue, BigDecimal marketPrice) {
        return intrinsicValue.subtract(marketPrice)
                .divide(marketPrice, 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Determina el veredicto según el margen de seguridad.
     * UNDERVALUED si > 15%, OVERVALUED si < -15%, FAIR_VALUE en el resto.
     */
    public static Verdict calculateVerdict(BigDecimal marginOfSafety) {
        if (marginOfSafety.compareTo(new BigDecimal("15")) > 0) {
            return Verdict.UNDERVALUED;
        } else if (marginOfSafety.compareTo(new BigDecimal("-15")) < 0) {
            return Verdict.OVERVALUED;
        } else {
            return Verdict.FAIR_VALUE;
        }
    }
}
