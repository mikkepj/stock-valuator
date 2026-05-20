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
    private final QualityScoreCalculator qualityScoreCalculator;

    public DcfCalculator(FreeCashFlowProjector fcfProjector,
                         WaccCalculator waccCalculator,
                         TerminalValueCalculator terminalValueCalculator) {
        this(fcfProjector, waccCalculator, terminalValueCalculator, new QualityScoreCalculator());
    }

    public DcfCalculator(FreeCashFlowProjector fcfProjector,
                         WaccCalculator waccCalculator,
                         TerminalValueCalculator terminalValueCalculator,
                         QualityScoreCalculator qualityScoreCalculator) {
        this.fcfProjector = Objects.requireNonNull(fcfProjector);
        this.waccCalculator = Objects.requireNonNull(waccCalculator);
        this.terminalValueCalculator = Objects.requireNonNull(terminalValueCalculator);
        this.qualityScoreCalculator = Objects.requireNonNull(qualityScoreCalculator);
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

        // 2. Proyectar FCFs futuros:
        //    - Si hay CapEx → proyección en dos etapas con ROIC (Mejora 5)
        //    - Si hay estimaciones de analistas → projectWithEstimates
        //    - Fallback → CAGR histórico con decay lineal
        List<ProjectedFcf> projectedFcfs;
        if (financials.capitalExpenditure() != null
                && financials.capitalExpenditure().compareTo(BigDecimal.ZERO) > 0) {
            projectedFcfs = fcfProjector.projectWithRoic(
                    financials.historicalFcf(), financials,
                    params.terminalGrowthRate(), params.projectionYears());
        } else {
            projectedFcfs = fcfProjector.projectWithEstimates(
                    financials.historicalFcf(), financials.analystFcfEstimates(),
                    params.terminalGrowthRate(), params.projectionYears());
        }

        // 3. Calcular suma del PV de FCFs proyectados
        BigDecimal sumPvFcfs = calculateSumPvFcfs(projectedFcfs, wacc);

        // 4. Calcular PV del Terminal Value
        BigDecimal lastFcf = projectedFcfs.get(projectedFcfs.size() - 1).projectedValue();
        BigDecimal pvTerminalValue = terminalValueCalculator.calculate(
                lastFcf, wacc, params.terminalGrowthRate(), params.projectionYears());

        // 5. Net Debt ajustado (incluye leases, pensiones e interés minoritario si están disponibles)
        BigDecimal netDebt = financials.adjustedNetDebt();

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
        BigDecimal effectiveTaxRate = waccCalculator.calculateEffectiveTaxRate(
                financials.incomeTaxExpense(), financials.ebitda(), financials.interestExpense());
        BigDecimal creditSpread = waccCalculator.calculateCreditSpread(
                financials.ebitda(), financials.interestExpense());
        // Exit multiple: EBITDA del último año proyectado estimado por la tasa de crecimiento del FCF año N
        BigDecimal lastGrowthRate = projectedFcfs.get(projectedFcfs.size() - 1).growthRateApplied();
        BigDecimal ebitdaProjectedLastYear = financials.ebitda()
                .multiply(BigDecimal.ONE.add(lastGrowthRate, MC).pow(params.projectionYears(), MC), MC);
        BigDecimal pvTerminalValueExitMultiple = terminalValueCalculator.calculateExitMultiple(
                ebitdaProjectedLastYear, wacc, financials.sector(), params.projectionYears());
        BigDecimal sizeRiskPremium = waccCalculator.calculateSizeRiskPremium(financials.equityValue());

        // 9. ROIC vs WACC: consistencia del crecimiento proyectado (Mejora 6)
        BigDecimal roic = calculateRoic(financials, effectiveTaxRate);
        BigDecimal capexForReinvest = financials.capitalExpenditure() != null
                ? financials.capitalExpenditure() : BigDecimal.ZERO;
        BigDecimal nopat = financials.ebitda()
                .multiply(BigDecimal.ONE.subtract(effectiveTaxRate, MC), MC);
        BigDecimal reinvestmentRate = (nopat.compareTo(BigDecimal.ZERO) > 0 && capexForReinvest.compareTo(BigDecimal.ZERO) > 0)
                ? capexForReinvest.divide(nopat, MC).min(BigDecimal.ONE)
                : BigDecimal.ZERO;
        BigDecimal maxSustainableGrowth = roic.multiply(reinvestmentRate, MC);
        BigDecimal avgProjectedGrowth = projectedFcfs.stream()
                .map(ProjectedFcf::growthRateApplied)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(projectedFcfs.size()), MC);
        BigDecimal growthExceedsRoic = (roic.compareTo(BigDecimal.ZERO) > 0
                && avgProjectedGrowth.compareTo(roic) > 0)
                ? BigDecimal.ONE : BigDecimal.ZERO;

        Map<String, BigDecimal> breakdown = buildBreakdown(sumPvFcfs, pvTerminalValue,
                pvTerminalValueExitMultiple, netDebt, wacc, effectiveTaxRate, creditSpread,
                sizeRiskPremium, roic, maxSustainableGrowth, growthExceedsRoic);

        int qualityScore = qualityScoreCalculator.calculate(financials, wacc);

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
                breakdown,
                null,                   // monteCarloResult se llena por ValuationService
                qualityScore
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
                                                    BigDecimal terminalValueExitMultiple,
                                                    BigDecimal netDebt, BigDecimal wacc,
                                                    BigDecimal effectiveTaxRate,
                                                    BigDecimal creditSpread,
                                                    BigDecimal sizeRiskPremium,
                                                    BigDecimal roic,
                                                    BigDecimal maxSustainableGrowth,
                                                    BigDecimal growthExceedsRoic) {
        Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        breakdown.put("sumPvFcfs", sumPvFcfs);
        breakdown.put("terminalValue", terminalValue);
        breakdown.put("terminalValueExitMultiple", terminalValueExitMultiple);
        breakdown.put("netDebt", netDebt);
        breakdown.put("wacc", wacc.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("effectiveTaxRate", effectiveTaxRate.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("creditSpread", creditSpread.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("sizeRiskPremium", sizeRiskPremium.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("roic", roic.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("maxSustainableGrowth", maxSustainableGrowth.setScale(6, RoundingMode.HALF_UP));
        breakdown.put("growthExceedsRoic", growthExceedsRoic);
        return Collections.unmodifiableMap(breakdown);
    }

    /**
     * Calcula ROIC = NOPAT / investedCapital.
     * NOPAT = ebitda × (1 - taxRate); investedCapital = totalDebt + totalEquity - cash.
     * Retorna cero si los datos no permiten el cálculo.
     */
    private BigDecimal calculateRoic(CompanyFinancials financials, BigDecimal taxRate) {
        BigDecimal nopat = financials.ebitda()
                .multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);
        BigDecimal investedCapital = financials.totalDebt()
                .add(financials.totalEquity(), MC)
                .subtract(financials.cashAndEquivalents(), MC);
        if (investedCapital.compareTo(BigDecimal.ZERO) <= 0 || nopat.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return nopat.divide(investedCapital, MC);
    }
}
