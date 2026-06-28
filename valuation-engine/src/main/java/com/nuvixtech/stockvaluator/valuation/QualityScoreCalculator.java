package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Calcula un Quality Score de 0–100 basado en 5 dimensiones de calidad del negocio.
 *
 * Cada dimensión aporta 0, 10 o 20 puntos:
 *   1. FCF Growth      — CAGR histórico: >10% → 20; 0-10% → 10; <0% → 0
 *   2. FCF Consistency — Años con FCF positivo: todos → 20; alguno negativo → 0
 *   3. ROIC vs WACC    — ROIC > WACC → 20; ROIC en (WACC-5%, WACC) → 10; < → 0
 *   4. Leverage        — netDebt/ebitda: <2x → 20; 2-4x → 10; >4x → 0
 *   5. Margin Trend    — FCF margin últimos 3 años: estable o creciendo → 20;
 *                        cayendo 0-3pp → 10; cayendo >3pp → 0
 */
public class QualityScoreCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    /**
     * Calcula el quality score para la empresa.
     *
     * @param financials datos financieros de la empresa
     * @param wacc       WACC calculado (para comparación ROIC vs WACC)
     * @return puntuación entera entre 0 y 100
     */
    public int calculate(CompanyFinancials financials, BigDecimal wacc) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(wacc, "wacc no puede ser null");

        int score = 0;
        score += scoreFcfGrowth(financials.historicalFcf());
        score += scoreFcfConsistency(financials.historicalFcf());
        score += scoreRoicVsWacc(financials, wacc);
        score += scoreLeverage(financials);
        score += scoreMarginTrend(financials.historicalFcf());

        return Math.max(0, Math.min(100, score));
    }

    // --- Dimensión 1: FCF Growth ---

    private int scoreFcfGrowth(List<BigDecimal> historical) {
        if (historical.size() < 2) return 10;  // datos insuficientes → neutro
        BigDecimal first = historical.get(0);
        BigDecimal last  = historical.get(historical.size() - 1);
        if (first.compareTo(BigDecimal.ZERO) <= 0 || last.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        int periods = historical.size() - 1;
        double cagr = Math.pow(last.divide(first, MC).doubleValue(), 1.0 / periods) - 1.0;

        if (cagr > 0.10) return 20;
        if (cagr > 0.00) return 10;
        return 0;
    }

    // --- Dimensión 2: FCF Consistency ---

    private int scoreFcfConsistency(List<BigDecimal> historical) {
        boolean allPositive = historical.stream()
                .allMatch(fcf -> fcf.compareTo(BigDecimal.ZERO) > 0);
        return allPositive ? 20 : 0;
    }

    // --- Dimensión 3: ROIC vs WACC ---

    private int scoreRoicVsWacc(CompanyFinancials financials, BigDecimal wacc) {
        BigDecimal taxableBase = financials.ebitda().subtract(financials.interestExpense(), MC);
        BigDecimal taxRate;
        if (taxableBase.compareTo(BigDecimal.ZERO) <= 0
                || financials.incomeTaxExpense().compareTo(BigDecimal.ZERO) <= 0) {
            taxRate = new BigDecimal("0.25");
        } else {
            taxRate = financials.incomeTaxExpense().divide(taxableBase, MC)
                    .max(BigDecimal.ZERO).min(new BigDecimal("0.40"));
        }

        BigDecimal nopat = financials.ebitda()
                .multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
        BigDecimal investedCapital = financials.totalDebt()
                .add(financials.totalEquity(), MC)
                .subtract(financials.cashAndEquivalents(), MC);

        if (investedCapital.compareTo(BigDecimal.ZERO) <= 0
                || nopat.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }

        BigDecimal roic = nopat.divide(investedCapital, MC);

        if (roic.compareTo(wacc) > 0) return 20;
        // zona intermedia: ROIC entre (WACC - 5%) y WACC
        BigDecimal threshold = wacc.subtract(new BigDecimal("0.05"), MC);
        if (roic.compareTo(threshold) >= 0) return 10;
        return 0;
    }

    // --- Dimensión 4: Leverage ---

    private int scoreLeverage(CompanyFinancials financials) {
        if (financials.ebitda().compareTo(BigDecimal.ZERO) <= 0) return 0;

        BigDecimal netDebt = financials.adjustedNetDebt();
        // Deuda neta negativa (más caja que deuda) → excelente
        if (netDebt.compareTo(BigDecimal.ZERO) <= 0) return 20;

        BigDecimal ratio = netDebt.divide(financials.ebitda(), MC);
        double r = ratio.doubleValue();

        if (r < 2.0)  return 20;
        if (r <= 4.0) return 10;
        return 0;
    }

    // --- Dimensión 5: Margin Trend ---

    private int scoreMarginTrend(List<BigDecimal> historical) {
        // Necesitamos al menos 3 puntos para calcular tendencia
        if (historical.size() < 3) return 10;  // datos insuficientes → neutro

        int n = historical.size();
        // Tomamos los últimos 3 años para evaluar la tendencia reciente
        BigDecimal y1 = historical.get(n - 3);
        BigDecimal y2 = historical.get(n - 2);
        BigDecimal y3 = historical.get(n - 1);

        // Si algún año es cero o negativo, no se puede calcular margen → neutro
        if (y1.compareTo(BigDecimal.ZERO) <= 0) return 10;

        // "FCF margin" sin revenue: usamos el crecimiento relativo inter-año como proxy
        // Δ% año2 vs año1, Δ% año3 vs año2
        double delta1 = y2.subtract(y1, MC).divide(y1.abs(), MC).doubleValue();
        double delta2 = y3.subtract(y2, MC).divide(y2.abs(), MC).doubleValue();
        double trend = (delta1 + delta2) / 2.0;

        // Tendencia promedio positiva o mínimamente negativa (>-3pp)
        if (trend >= -0.03) return 20;
        // Cayendo entre 3% y 15%
        if (trend >= -0.15) return 10;
        return 0;
    }
}
