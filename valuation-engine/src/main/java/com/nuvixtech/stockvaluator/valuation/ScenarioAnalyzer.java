package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * Genera tres escenarios de valuación DCF: Base, Optimista y Pesimista.
 *
 * <p>Los escenarios varían la tasa de crecimiento inicial del FCF proyectado:
 * <ul>
 *   <li><b>Base:</b>      CAGR histórico con decay lineal (comportamiento actual del engine)</li>
 *   <li><b>Optimista:</b> CAGR histórico × 1.30, cap de 25%, decay más suave</li>
 *   <li><b>Pesimista:</b> CAGR histórico × 0.75, decay más agresivo, WACC +0.5%</li>
 * </ul>
 *
 * <p>El escenario Base coincide exactamente con el resultado de {@link DcfCalculator}.
 */
public class ScenarioAnalyzer {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final BigDecimal MAX_OPTIMISTA_GROWTH = new BigDecimal("0.25");
    private static final BigDecimal PESIMISTA_WACC_ADJUSTMENT = new BigDecimal("0.005");

    private final DcfCalculator dcfCalculator;

    public ScenarioAnalyzer(DcfCalculator dcfCalculator) {
        this.dcfCalculator = Objects.requireNonNull(dcfCalculator);
    }

    /**
     * Calcula los tres escenarios para la empresa dada.
     *
     * Si la empresa tiene estimaciones de analistas, el CAGR se calcula sobre ellas.
     * En caso contrario, se usa el CAGR histórico.
     *
     * @param financials datos financieros de la empresa
     * @param baseParams parámetros DCF base (riskFreeRate, MRP, terminalGrowth, etc.)
     * @return lista con los tres ScenarioResult: Base, Optimista, Pesimista
     */
    public List<ScenarioResult> analyze(CompanyFinancials financials, DcfParameters baseParams) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(baseParams, "baseParams no puede ser null");

        // Si hay estimaciones de analistas, el CAGR implícito se calcula sobre ellas
        List<BigDecimal> cagrSource = financials.hasAnalystEstimates()
                ? financials.analystFcfEstimates()
                : financials.historicalFcf();
        BigDecimal baseCagr = calculateCagr(cagrSource, baseParams.terminalGrowthRate());

        return List.of(
                buildBase(financials, baseParams, baseCagr),
                buildOptimista(financials, baseParams, baseCagr),
                buildPesimista(financials, baseParams, baseCagr)
        );
    }

    // --- Escenario Base: igual al DcfCalculator estándar ---

    private ScenarioResult buildBase(CompanyFinancials financials, DcfParameters params,
                                      BigDecimal baseCagr) {
        var result = dcfCalculator.calculate(financials, params);
        return new ScenarioResult(
                "Base",
                result.intrinsicValuePerShare(),
                result.marginOfSafety(),
                result.verdict(),
                baseCagr,
                params.terminalGrowthRate(),
                result.wacc()
        );
    }

    // --- Escenario Optimista: CAGR × 1.30 (cap 25%), WACC base ---

    private ScenarioResult buildOptimista(CompanyFinancials financials, DcfParameters baseParams,
                                           BigDecimal baseCagr) {
        BigDecimal optimistaGrowth = baseCagr.multiply(new BigDecimal("1.30"), MC);
        if (optimistaGrowth.compareTo(MAX_OPTIMISTA_GROWTH) > 0) {
            optimistaGrowth = MAX_OPTIMISTA_GROWTH;
        }

        CompanyFinancials scaledFinancials = scaleProjectionSource(financials, baseCagr, optimistaGrowth);

        var result = dcfCalculator.calculate(scaledFinancials, baseParams);
        return new ScenarioResult(
                "Optimista",
                result.intrinsicValuePerShare(),
                result.marginOfSafety(),
                result.verdict(),
                optimistaGrowth,
                baseParams.terminalGrowthRate(),
                result.wacc()
        );
    }

    // --- Escenario Pesimista: CAGR × 0.75, WACC +0.5% ---

    private ScenarioResult buildPesimista(CompanyFinancials financials, DcfParameters baseParams,
                                           BigDecimal baseCagr) {
        BigDecimal pesimistaCagr = baseCagr.multiply(new BigDecimal("0.75"), MC);
        if (pesimistaCagr.compareTo(baseParams.terminalGrowthRate()) < 0) {
            pesimistaCagr = baseParams.terminalGrowthRate();
        }

        CompanyFinancials scaledFinancials = scaleProjectionSource(financials, baseCagr, pesimistaCagr);

        DcfParameters pesimistParams = new DcfParameters(
                baseParams.riskFreeRate().add(PESIMISTA_WACC_ADJUSTMENT, MC),
                baseParams.marketRiskPremium(),
                baseParams.terminalGrowthRate(),
                baseParams.projectionYears(),
                baseParams.marketPrice()
        );

        var result = dcfCalculator.calculate(scaledFinancials, pesimistParams);
        return new ScenarioResult(
                "Pesimista",
                result.intrinsicValuePerShare(),
                result.marginOfSafety(),
                result.verdict(),
                pesimistaCagr,
                baseParams.terminalGrowthRate(),
                result.wacc()
        );
    }

    /**
     * Escala la fuente de proyección de FCF para reflejar la nueva tasa de crecimiento objetivo.
     *
     * Si hay estimaciones de analistas, escala las estimaciones (primer y último año del rango).
     * Si solo hay históricos, escala el último FCF histórico.
     */
    private CompanyFinancials scaleProjectionSource(CompanyFinancials financials,
                                                     BigDecimal originalCagr,
                                                     BigDecimal targetCagr) {
        if (financials.hasAnalystEstimates()) {
            return scaleAnalystEstimates(financials, originalCagr, targetCagr);
        }
        return scaleLastHistoricalFcf(financials, targetCagr);
    }

    /**
     * Escala las estimaciones de analistas para reflejar el CAGR objetivo.
     * El primer año se mantiene proporcional; el último se reescala según el CAGR deseado.
     */
    private CompanyFinancials scaleAnalystEstimates(CompanyFinancials financials,
                                                     BigDecimal originalCagr,
                                                     BigDecimal targetCagr) {
        var estimates = financials.analystFcfEstimates();
        if (estimates.size() < 2) {
            return financials;
        }

        BigDecimal first = estimates.get(0);
        int periods = estimates.size() - 1;
        // newLast = first × (1 + targetCagr)^periods
        BigDecimal newLast = first.multiply(
                BigDecimal.ONE.add(targetCagr, MC).pow(periods, MC), MC)
                .setScale(0, RoundingMode.HALF_UP);

        // Interpolar linealmente desde first hasta newLast para los años intermedios
        var newEstimates = new java.util.ArrayList<BigDecimal>(estimates.size());
        newEstimates.add(first);
        for (int i = 1; i < periods; i++) {
            double fraction = (double) i / periods;
            BigDecimal interpolated = first.add(
                    newLast.subtract(first, MC).multiply(BigDecimal.valueOf(fraction), MC), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            newEstimates.add(interpolated);
        }
        newEstimates.add(newLast);

        return new CompanyFinancials(
                financials.ticker(),
                financials.historicalFcf(),
                financials.totalDebt(),
                financials.cashAndEquivalents(),
                financials.totalEquity(),
                financials.interestExpense(),
                financials.incomeTaxExpense(),
                financials.beta(),
                financials.sharesOutstanding(),
                financials.ebitda(),
                financials.marketCap(),
                newEstimates
        );
    }

    /**
     * Escala el último FCF histórico para que el CAGR implícito refleje la tasa objetivo.
     */
    private CompanyFinancials scaleLastHistoricalFcf(CompanyFinancials financials,
                                                      BigDecimal targetCagr) {
        var fcfs = financials.historicalFcf();
        if (fcfs.size() < 2) {
            return financials;
        }

        BigDecimal first = fcfs.get(0);
        int periods = fcfs.size() - 1;
        BigDecimal newLast = first.multiply(
                BigDecimal.ONE.add(targetCagr, MC).pow(periods, MC), MC)
                .setScale(0, RoundingMode.HALF_UP);

        var newFcfs = new java.util.ArrayList<>(fcfs.subList(0, periods));
        newFcfs.add(newLast);

        return new CompanyFinancials(
                financials.ticker(),
                newFcfs,
                financials.totalDebt(),
                financials.cashAndEquivalents(),
                financials.totalEquity(),
                financials.interestExpense(),
                financials.incomeTaxExpense(),
                financials.beta(),
                financials.sharesOutstanding(),
                financials.ebitda(),
                financials.marketCap(),
                List.of()
        );
    }

    /**
     * Calcula el CAGR sobre los FCF históricos. Si no es calculable, retorna la tasa terminal.
     */
    private BigDecimal calculateCagr(List<BigDecimal> historicalFcf, BigDecimal terminalRate) {
        if (historicalFcf.size() < 2) return terminalRate;

        BigDecimal first = historicalFcf.get(0);
        BigDecimal last = historicalFcf.get(historicalFcf.size() - 1);
        int periods = historicalFcf.size() - 1;

        if (first.compareTo(BigDecimal.ZERO) <= 0 || last.compareTo(BigDecimal.ZERO) <= 0) {
            return terminalRate;
        }

        double ratio = last.divide(first, MC).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / periods) - 1.0;
        BigDecimal cagrDecimal = BigDecimal.valueOf(cagr).setScale(10, RoundingMode.HALF_UP);

        return cagrDecimal.compareTo(terminalRate) < 0 ? terminalRate : cagrDecimal;
    }
}
