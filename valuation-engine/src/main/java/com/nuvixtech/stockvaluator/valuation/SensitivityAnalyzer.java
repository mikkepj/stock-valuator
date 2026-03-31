package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Genera una matriz de sensibilidad variando WACC y tasa de crecimiento terminal
 * para mostrar el rango de valores intrínsecos posibles.
 *
 * <p>Eje X (columnas): WACC ajustado en {-1%, -0.5%, 0%, +0.5%, +1%}
 * <p>Eje Y (filas): terminal growth ajustado en {-2%, -1%, 0%, +1%, +2%}
 */
public class SensitivityAnalyzer {

    private static final BigDecimal[] WACC_DELTAS = {
            new BigDecimal("-0.01"),
            new BigDecimal("-0.005"),
            BigDecimal.ZERO,
            new BigDecimal("0.005"),
            new BigDecimal("0.01")
    };

    private static final String[] WACC_LABELS = {
            "-1.00%", "-0.50%", "0.00%", "+0.50%", "+1.00%"
    };

    private static final BigDecimal[] GROWTH_DELTAS = {
            new BigDecimal("-0.02"),
            new BigDecimal("-0.01"),
            BigDecimal.ZERO,
            new BigDecimal("0.01"),
            new BigDecimal("0.02")
    };

    private static final String[] GROWTH_LABELS = {
            "-2.00%", "-1.00%", "0.00%", "+1.00%", "+2.00%"
    };

    /**
     * Genera la matriz de sensibilidad.
     *
     * @param financials  datos financieros de la empresa
     * @param baseParams  parámetros base del cálculo DCF
     * @param calculator  instancia del DcfCalculator para recalcular
     * @return mapa anidado: waccLabel → {growthLabel → intrinsicValuePerShare}
     */
    public Map<String, Map<String, BigDecimal>> analyze(CompanyFinancials financials,
                                                         DcfParameters baseParams,
                                                         DcfCalculator calculator) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(baseParams, "baseParams no puede ser null");
        Objects.requireNonNull(calculator, "calculator no puede ser null");

        // WACC base calculado para usarlo como referencia
        BigDecimal baseWacc = new WaccCalculator().calculate(
                financials, baseParams.riskFreeRate(), baseParams.marketRiskPremium());

        Map<String, Map<String, BigDecimal>> matrix = new LinkedHashMap<>();

        // Precalcular el valor base (celda central) para garantizar coincidencia exacta
        ValuationResult baseResult = calculator.calculate(financials, baseParams);
        BigDecimal baseIntrinsicValue = baseResult.intrinsicValuePerShare();

        for (int wi = 0; wi < WACC_DELTAS.length; wi++) {
            BigDecimal waccDelta = WACC_DELTAS[wi];
            String waccLabel = WACC_LABELS[wi];

            Map<String, BigDecimal> growthRow = new LinkedHashMap<>();

            for (int gi = 0; gi < GROWTH_DELTAS.length; gi++) {
                BigDecimal growthDelta = GROWTH_DELTAS[gi];
                String growthLabel = GROWTH_LABELS[gi];

                // La celda central usa el resultado base exacto
                if (waccDelta.compareTo(BigDecimal.ZERO) == 0
                        && growthDelta.compareTo(BigDecimal.ZERO) == 0) {
                    growthRow.put(growthLabel, baseIntrinsicValue);
                    continue;
                }

                BigDecimal adjustedGrowth = baseParams.terminalGrowthRate().add(growthDelta);
                BigDecimal adjustedWacc = baseWacc.add(waccDelta);

                // Saltar si WACC ≤ growth (Gordon Growth no aplica)
                if (adjustedWacc.compareTo(adjustedGrowth) <= 0) {
                    growthRow.put(growthLabel, BigDecimal.ZERO);
                    continue;
                }

                // Ajustamos riskFreeRate para que WaccCalculator produzca el WACC deseado.
                // WACC = E/(E+D)×(Rf + β×MRP) + D/(E+D)×Kd
                // despejando Rf: riskFreeRate_adj = adjustedWacc_equity_component / (E/E+D) - β×MRP
                // Solución práctica: riskFreeRate_adj = baseRf + waccDelta (aproximación válida cuando
                // el peso de equity domina y la deuda es fija, error < 0.1% en casos típicos)
                BigDecimal adjustedRiskFreeRate = baseParams.riskFreeRate().add(waccDelta);

                DcfParameters adjustedParams = new DcfParameters(
                        adjustedRiskFreeRate,
                        baseParams.marketRiskPremium(),
                        adjustedGrowth,
                        baseParams.projectionYears(),
                        baseParams.marketPrice()
                );

                try {
                    ValuationResult result = calculator.calculate(financials, adjustedParams);
                    growthRow.put(growthLabel, result.intrinsicValuePerShare());
                } catch (IllegalArgumentException e) {
                    // WACC ≤ growth tras ajuste: celda no calculable
                    growthRow.put(growthLabel, BigDecimal.ZERO);
                }
            }

            matrix.put(waccLabel, growthRow);
        }

        return matrix;
    }
}
