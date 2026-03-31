package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Orquestador del cálculo DCF completo. Coordina los componentes del engine
 * para producir un ValuationResult a partir de CompanyFinancials y DcfParameters.
 */
public class DcfCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;

    private final FreeCashFlowProjector fcfProjector;
    private final WaccCalculator waccCalculator;
    private final TerminalValueCalculator terminalValueCalculator;

    public DcfCalculator(FreeCashFlowProjector fcfProjector,
                         WaccCalculator waccCalculator,
                         TerminalValueCalculator terminalValueCalculator) {
        this.fcfProjector = Objects.requireNonNull(fcfProjector);
        this.waccCalculator = Objects.requireNonNull(waccCalculator);
        this.terminalValueCalculator = Objects.requireNonNull(terminalValueCalculator);
    }

    /**
     * Ejecuta el cálculo DCF completo.
     *
     * @param financials datos financieros de la empresa
     * @param params     parámetros del modelo DCF
     * @return resultado con valor intrínseco, veredicto y datos de desglose
     */
    public ValuationResult calculate(CompanyFinancials financials, DcfParameters params) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(params, "params no puede ser null");

        // 1. Calcular WACC
        BigDecimal wacc = waccCalculator.calculate(financials, params.riskFreeRate(), params.marketRiskPremium());

        // 2. Proyectar FCFs futuros
        List<ProjectedFcf> projectedFcfs = fcfProjector.project(
                financials.historicalFcf(), params.terminalGrowthRate(), params.projectionYears());

        // 3. Calcular suma del PV de FCFs proyectados
        BigDecimal sumPvFcfs = calculateSumPvFcfs(projectedFcfs, wacc);

        // 4. Calcular PV del Terminal Value
        BigDecimal lastFcf = projectedFcfs.get(projectedFcfs.size() - 1).projectedValue();
        BigDecimal pvTerminalValue = terminalValueCalculator.calculate(
                lastFcf, wacc, params.terminalGrowthRate(), params.projectionYears());

        // 5. Net Debt = Total Debt - Cash
        BigDecimal netDebt = financials.totalDebt().subtract(financials.cashAndEquivalents(), MC);

        // 6. Intrinsic Value per Share
        BigDecimal enterpriseValue = sumPvFcfs.add(pvTerminalValue, MC).subtract(netDebt, MC);
        BigDecimal sharesOutstanding = BigDecimal.valueOf(financials.sharesOutstanding());
        BigDecimal intrinsicValuePerShare = enterpriseValue.divide(sharesOutstanding, MC)
                .setScale(2, RoundingMode.HALF_UP);

        // 7. Margen de seguridad y veredicto
        BigDecimal marginOfSafety = ValuationResult.calculateMarginOfSafety(
                intrinsicValuePerShare, params.marketPrice());
        Verdict verdict = ValuationResult.calculateVerdict(marginOfSafety);

        // 8. Construir breakdown para transparencia
        Map<String, BigDecimal> breakdown = buildBreakdown(sumPvFcfs, pvTerminalValue, netDebt, wacc);

        return new ValuationResult(
                financials.ticker(),
                intrinsicValuePerShare,
                params.marketPrice(),
                marginOfSafety,
                verdict,
                wacc,
                params.terminalGrowthRate(),
                params.projectionYears(),
                pvTerminalValue,
                netDebt,
                projectedFcfs,
                Collections.emptyMap(), // sensitivityMatrix se llena por SensitivityAnalyzer
                breakdown
        );
    }

    /** Descuenta cada FCF proyectado al presente y suma. PV = FCF / (1+WACC)^año */
    private BigDecimal calculateSumPvFcfs(List<ProjectedFcf> projectedFcfs, BigDecimal wacc) {
        BigDecimal sum = BigDecimal.ZERO;
        for (ProjectedFcf projected : projectedFcfs) {
            BigDecimal discountFactor = BigDecimal.ONE.add(wacc, MC).pow(projected.year(), MC);
            BigDecimal pv = projected.projectedValue().divide(discountFactor, MC);
            sum = sum.add(pv, MC);
        }
        return sum.setScale(0, RoundingMode.HALF_UP);
    }

    private Map<String, BigDecimal> buildBreakdown(BigDecimal sumPvFcfs, BigDecimal terminalValue,
                                                    BigDecimal netDebt, BigDecimal wacc) {
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("sumPvFcfs", sumPvFcfs);
        breakdown.put("terminalValue", terminalValue);
        breakdown.put("netDebt", netDebt);
        breakdown.put("wacc", wacc.setScale(6, RoundingMode.HALF_UP));
        return Collections.unmodifiableMap(breakdown);
    }
}
