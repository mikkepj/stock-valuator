package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Simulación Monte Carlo sobre FCF y WACC para producir una distribución de
 * valores intrínsecos por acción y calcular percentiles clave.
 *
 * Variables perturbadas por simulación:
 *   growthRate ~ Normal(μ=CAGR, σ=growthSigma)
 *   wacc       ~ Normal(μ=baseWacc, σ=waccSigma)
 *   terminalGrowth ~ Uniform(1.5%, 3.5%)
 */
public class MonteCarloAnalyzer {

    private static final MathContext MC = MathContext.DECIMAL128;

    private static final double DEFAULT_WACC_SIGMA   = 0.01;   // ±1% std para WACC
    private static final double DEFAULT_GROWTH_SIGMA = 0.05;   // ±5% std para growthRate
    private static final double TERMINAL_GROWTH_MIN  = 0.015;
    private static final double TERMINAL_GROWTH_MAX  = 0.035;
    private static final double MAX_GROWTH_RATE      = 0.30;
    private static final double MIN_GROWTH_RATE      = -0.10;
    private static final double MIN_WACC             = 0.04;
    private static final double MAX_WACC             = 0.25;

    private final DcfCalculator dcfCalculator;
    private final Long seed;
    private final double waccSigma;
    private final double growthSigma;

    /** Constructor estándar: usa σ defaults y semilla aleatoria. */
    public MonteCarloAnalyzer(DcfCalculator dcfCalculator) {
        this(dcfCalculator, null, DEFAULT_WACC_SIGMA, DEFAULT_GROWTH_SIGMA);
    }

    /** Constructor con semilla fija para resultados deterministas (tests). */
    public MonteCarloAnalyzer(DcfCalculator dcfCalculator, long seed) {
        this(dcfCalculator, seed, DEFAULT_WACC_SIGMA, DEFAULT_GROWTH_SIGMA);
    }

    /** Constructor con σ custom (waccSigma=0 y growthSigma=0 → sin perturbaciones). */
    public MonteCarloAnalyzer(DcfCalculator dcfCalculator, double waccSigma, double growthSigma) {
        this(dcfCalculator, null, waccSigma, growthSigma);
    }

    private MonteCarloAnalyzer(DcfCalculator dcfCalculator, Long seed,
                                double waccSigma, double growthSigma) {
        this.dcfCalculator = Objects.requireNonNull(dcfCalculator, "dcfCalculator no puede ser null");
        this.seed = seed;
        this.waccSigma = waccSigma;
        this.growthSigma = growthSigma;
    }

    /**
     * Ejecuta N simulaciones Monte Carlo y retorna los percentiles de la distribución de IV/share.
     *
     * @param financials      datos financieros de la empresa
     * @param baseParams      parámetros DCF base (WACC, terminalGrowth, etc.)
     * @param simulationCount número de simulaciones (recomendado: 1000)
     * @return percentiles p10, p25, p50, p75, p90 del valor intrínseco por acción
     */
    public MonteCarloResult analyze(CompanyFinancials financials, DcfParameters baseParams,
                                    int simulationCount) {
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(baseParams, "baseParams no puede ser null");
        if (simulationCount <= 0) {
            throw new IllegalArgumentException("simulationCount debe ser mayor que cero");
        }

        Random rng = seed != null ? new Random(seed) : new Random();

        // CAGR histórico como μ del growthRate perturbado
        double baseCagr = calculateCagr(financials.historicalFcf(),
                baseParams.terminalGrowthRate().doubleValue());
        double baseWacc = calculateBaseWacc(financials, baseParams);

        List<BigDecimal> ivSamples = new ArrayList<>(simulationCount);

        for (int i = 0; i < simulationCount; i++) {
            double perturbedGrowth = clamp(
                    baseCagr + (growthSigma > 0 ? rng.nextGaussian() * growthSigma : 0.0),
                    MIN_GROWTH_RATE, MAX_GROWTH_RATE);
            double perturbedWacc = clamp(
                    baseWacc + (waccSigma > 0 ? rng.nextGaussian() * waccSigma : 0.0),
                    MIN_WACC, MAX_WACC);
            double perturbedTerminal = (waccSigma > 0 || growthSigma > 0)
                    ? TERMINAL_GROWTH_MIN + rng.nextDouble() * (TERMINAL_GROWTH_MAX - TERMINAL_GROWTH_MIN)
                    : baseParams.terminalGrowthRate().doubleValue();

            // Construir parámetros perturbados manteniendo riskFreeRate y MRP base
            DcfParameters perturbedParams = buildPerturbedParams(baseParams,
                    perturbedWacc, perturbedTerminal);

            // Escalar el último FCF histórico con el growthRate perturbado
            CompanyFinancials perturbedFinancials = scaleLastFcf(financials, baseCagr, perturbedGrowth);

            try {
                var result = dcfCalculator.calculate(perturbedFinancials, perturbedParams);
                if (result.intrinsicValuePerShare().compareTo(BigDecimal.ZERO) > 0) {
                    ivSamples.add(result.intrinsicValuePerShare());
                }
            } catch (Exception ignored) {
                // Simulación degenerada (WACC ≤ g): ignorar este punto
            }
        }

        if (ivSamples.isEmpty()) {
            BigDecimal fallback = dcfCalculator.calculate(financials, baseParams).intrinsicValuePerShare();
            return new MonteCarloResult(fallback, fallback, fallback, fallback, fallback, simulationCount);
        }

        Collections.sort(ivSamples);
        return new MonteCarloResult(
                percentile(ivSamples, 10),
                percentile(ivSamples, 25),
                percentile(ivSamples, 50),
                percentile(ivSamples, 75),
                percentile(ivSamples, 90),
                simulationCount
        );
    }

    // --- helpers privados ---

    private double calculateCagr(List<BigDecimal> historical, double terminalRate) {
        if (historical.size() < 2) return terminalRate;
        BigDecimal first = historical.get(0);
        BigDecimal last = historical.get(historical.size() - 1);
        if (first.compareTo(BigDecimal.ZERO) <= 0 || last.compareTo(BigDecimal.ZERO) <= 0) {
            return terminalRate;
        }
        double ratio = last.divide(first, MC).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / (historical.size() - 1)) - 1.0;
        return clamp(cagr, MIN_GROWTH_RATE, MAX_GROWTH_RATE);
    }

    private double calculateBaseWacc(CompanyFinancials financials, DcfParameters params) {
        try {
            return new WaccCalculator()
                    .calculate(financials, params.riskFreeRate(), params.marketRiskPremium())
                    .doubleValue();
        } catch (Exception e) {
            return params.riskFreeRate()
                    .add(params.marketRiskPremium(), MC)
                    .doubleValue();
        }
    }

    /**
     * Escala el último FCF histórico para reflejar el nuevo growthRate objetivo.
     * Mantiene todos los años anteriores intactos (solo reemplaza el último).
     */
    private CompanyFinancials scaleLastFcf(CompanyFinancials financials,
                                            double originalCagr, double targetCagr) {
        var fcfs = financials.historicalFcf();
        if (fcfs.size() < 2 || originalCagr == targetCagr) return financials;

        BigDecimal first = fcfs.get(0);
        int periods = fcfs.size() - 1;
        BigDecimal newLast = first.multiply(
                BigDecimal.ONE.add(BigDecimal.valueOf(targetCagr), MC).pow(periods, MC), MC)
                .setScale(0, RoundingMode.HALF_UP).max(BigDecimal.ONE);

        var newFcfs = new ArrayList<>(fcfs.subList(0, periods));
        newFcfs.add(newLast);

        return new CompanyFinancials(
                financials.ticker(), newFcfs,
                financials.totalDebt(), financials.cashAndEquivalents(),
                financials.totalEquity(), financials.interestExpense(),
                financials.incomeTaxExpense(), financials.beta(),
                financials.sharesOutstanding(), financials.ebitda(),
                financials.marketCap(), financials.analystFcfEstimates(),
                financials.sector(), financials.operatingLeaseObligations(),
                financials.pensionLiabilities(), financials.minorityInterest(),
                financials.capitalExpenditure()
        );
    }

    /**
     * Construye parámetros DCF perturbados ajustando el riskFreeRate para que
     * el WACC resultante se acerque al valor objetivo sin tocar el cálculo interno.
     * En la práctica: pasa riskFreeRate+delta tal que el WACC cambia en la dirección deseada.
     */
    private DcfParameters buildPerturbedParams(DcfParameters base,
                                                double targetWacc, double targetTerminal) {
        // Ajustamos riskFreeRate para aproximar el WACC objetivo
        double waccDelta = targetWacc - base.riskFreeRate()
                .add(base.marketRiskPremium(), MC).doubleValue();
        BigDecimal newRfr = base.riskFreeRate()
                .add(BigDecimal.valueOf(waccDelta * 0.5), MC)
                .max(new BigDecimal("0.01"))
                .min(new BigDecimal("0.15"));

        return new DcfParameters(
                newRfr,
                base.marketRiskPremium(),
                BigDecimal.valueOf(targetTerminal),
                base.projectionYears(),
                base.marketPrice()
        );
    }

    private BigDecimal percentile(List<BigDecimal> sorted, int pct) {
        int n = sorted.size();
        // Interpolación lineal para percentil (índice 0-based)
        double rank = pct / 100.0 * (n - 1);
        int lower = (int) rank;
        int upper = Math.min(lower + 1, n - 1);
        double fraction = rank - lower;
        BigDecimal lo = sorted.get(lower);
        BigDecimal hi = sorted.get(upper);
        return lo.add(hi.subtract(lo, MC).multiply(BigDecimal.valueOf(fraction), MC), MC)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
