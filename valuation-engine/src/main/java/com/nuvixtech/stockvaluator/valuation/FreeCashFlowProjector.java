package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Proyecta FCFs futuros a partir de datos históricos usando CAGR con decay lineal
 * hacia la tasa de crecimiento terminal.
 */
public class FreeCashFlowProjector {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int SCALE = 10;
    // Cap máximo para el CAGR histórico: evita que períodos de crecimiento excepcional
    // inflen el valor intrínseco de forma irreal
    private static final BigDecimal MAX_INITIAL_GROWTH_RATE = new BigDecimal("0.30");

    /**
     * Proyecta FCFs futuros usando estimaciones de analistas para los primeros N años,
     * y luego proyecta los años restantes con decay lineal desde la tasa implícita
     * de las estimaciones hacia la tasa terminal.
     *
     * Si analystEstimates está vacío, delega al método project() estándar.
     *
     * @param historicalFcf      FCFs históricos (base para calcular CAGR si no hay estimaciones)
     * @param analystEstimates   estimaciones de analistas en orden ascendente (año+1 al año+N)
     * @param terminalGrowthRate tasa de crecimiento terminal
     * @param projectionYears    número total de años a proyectar
     * @return lista de FCFs proyectados, uno por año
     */
    public List<ProjectedFcf> projectWithEstimates(List<BigDecimal> historicalFcf,
                                                    List<BigDecimal> analystEstimates,
                                                    BigDecimal terminalGrowthRate,
                                                    int projectionYears) {
        Objects.requireNonNull(historicalFcf, "historicalFcf no puede ser null");
        Objects.requireNonNull(analystEstimates, "analystEstimates no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }

        if (analystEstimates.isEmpty()) {
            return project(historicalFcf, terminalGrowthRate, projectionYears);
        }

        List<ProjectedFcf> projections = new ArrayList<>(projectionYears);

        // Años cubiertos por estimaciones de analistas (hasta el mínimo entre las estimaciones y projectionYears)
        int estimateYears = Math.min(analystEstimates.size(), projectionYears);

        // Años 1..estimateYears: usar directamente las estimaciones de analistas
        for (int year = 1; year <= estimateYears; year++) {
            BigDecimal estimatedFcf = analystEstimates.get(year - 1);
            // Calcular la tasa implícita de crecimiento respecto al año anterior
            BigDecimal prevFcf = year == 1
                    ? lastPositiveFcf(historicalFcf)
                    : analystEstimates.get(year - 2);
            BigDecimal growthRate = prevFcf.compareTo(BigDecimal.ZERO) > 0
                    ? estimatedFcf.subtract(prevFcf).divide(prevFcf, SCALE, RoundingMode.HALF_UP)
                    : terminalGrowthRate;
            projections.add(new ProjectedFcf(year, estimatedFcf, growthRate));
        }

        // Si las estimaciones cubren todos los años de proyección, hemos terminado
        if (estimateYears >= projectionYears) {
            return projections;
        }

        // Años estimateYears+1..projectionYears: proyectar con decay desde tasa implícita del último año
        // La tasa inicial para la segunda fase es la tasa del último año de estimaciones
        BigDecimal lastEstimate = analystEstimates.get(estimateYears - 1);
        BigDecimal prevEstimate = estimateYears > 1
                ? analystEstimates.get(estimateYears - 2)
                : lastPositiveFcf(historicalFcf);
        BigDecimal initialRate = prevEstimate.compareTo(BigDecimal.ZERO) > 0
                ? lastEstimate.subtract(prevEstimate).divide(prevEstimate, SCALE, RoundingMode.HALF_UP)
                : terminalGrowthRate;

        // Asegurar piso en terminal rate y techo en 30%
        if (initialRate.compareTo(terminalGrowthRate) < 0) {
            initialRate = terminalGrowthRate;
        }
        if (initialRate.compareTo(MAX_INITIAL_GROWTH_RATE) > 0) {
            initialRate = MAX_INITIAL_GROWTH_RATE;
        }

        int remainingYears = projectionYears - estimateYears;
        BigDecimal currentFcf = lastEstimate;

        for (int i = 1; i <= remainingYears; i++) {
            int year = estimateYears + i;
            BigDecimal growthRate = interpolateRate(initialRate, terminalGrowthRate, i, remainingYears);
            currentFcf = currentFcf.multiply(BigDecimal.ONE.add(growthRate), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            projections.add(new ProjectedFcf(year, currentFcf, growthRate.setScale(SCALE, RoundingMode.HALF_UP)));
        }

        return projections;
    }

    /**
     * Proyecta FCFs futuros aplicando decay lineal desde el CAGR histórico hasta la tasa terminal.
     *
     * @param historicalFcf     FCFs históricos en orden ascendente (más antiguo primero)
     * @param terminalGrowthRate tasa de crecimiento terminal (ej: 0.025)
     * @param projectionYears   número de años a proyectar
     * @return lista de FCFs proyectados, uno por año
     */
    public List<ProjectedFcf> project(List<BigDecimal> historicalFcf,
                                       BigDecimal terminalGrowthRate,
                                       int projectionYears) {
        Objects.requireNonNull(historicalFcf, "historicalFcf no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }

        BigDecimal initialRate = calculateInitialRate(historicalFcf, terminalGrowthRate);
        BigDecimal lastFcf = lastPositiveFcf(historicalFcf);

        List<ProjectedFcf> projections = new ArrayList<>(projectionYears);
        BigDecimal currentFcf = lastFcf;

        for (int year = 1; year <= projectionYears; year++) {
            BigDecimal growthRate = interpolateRate(initialRate, terminalGrowthRate, year, projectionYears);
            currentFcf = currentFcf.multiply(BigDecimal.ONE.add(growthRate), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            projections.add(new ProjectedFcf(year, currentFcf, growthRate.setScale(SCALE, RoundingMode.HALF_UP)));
        }

        return projections;
    }

    /**
     * Proyecta FCFs en dos etapas usando ROIC × reinvestmentRate.
     *
     * <p>Fase 1 (años 1-5): tasa = ROIC × reinvestmentRate, donde:
     *   NOPAT = ebitda × (1 - taxRate);
     *   investedCapital = totalDebt + totalEquity - cash;
     *   ROIC = NOPAT / investedCapital;
     *   reinvestmentRate = capex / NOPAT.
     *
     * <p>Fase 2 (años 6-N): decay lineal desde la tasa de fase 1 hasta terminalGrowthRate.
     *
     * <p>Si capitalExpenditure o los datos necesarios para ROIC no están disponibles,
     * delega a {@link #project(List, BigDecimal, int)} como fallback.
     *
     * @param historicalFcf     FCFs históricos en orden ascendente
     * @param financials        datos financieros con campos opcionales de CapEx
     * @param terminalGrowthRate tasa de crecimiento terminal
     * @param projectionYears   número total de años a proyectar
     * @return lista de FCFs proyectados, uno por año
     */
    public List<ProjectedFcf> projectWithRoic(List<BigDecimal> historicalFcf,
                                               CompanyFinancials financials,
                                               BigDecimal terminalGrowthRate,
                                               int projectionYears) {
        Objects.requireNonNull(historicalFcf, "historicalFcf no puede ser null");
        Objects.requireNonNull(financials, "financials no puede ser null");
        Objects.requireNonNull(terminalGrowthRate, "terminalGrowthRate no puede ser null");
        if (projectionYears <= 0) {
            throw new IllegalArgumentException("projectionYears debe ser mayor que cero");
        }

        BigDecimal capex = financials.capitalExpenditure();
        BigDecimal ebitda = financials.ebitda();
        BigDecimal incomeTax = financials.incomeTaxExpense();
        BigDecimal interestExp = financials.interestExpense();
        BigDecimal totalDebt = financials.totalDebt();
        BigDecimal totalEquity = financials.totalEquity();
        BigDecimal cash = financials.cashAndEquivalents();

        // Fallback si CapEx no está disponible o es cero
        if (capex == null || capex.compareTo(BigDecimal.ZERO) == 0
                || ebitda == null || ebitda.compareTo(BigDecimal.ZERO) <= 0) {
            return project(historicalFcf, terminalGrowthRate, projectionYears);
        }

        // taxRate efectiva: incomeTax / (ebitda - interestExp); clamped [0, 0.40]
        BigDecimal taxableBase = ebitda.subtract(interestExp, MC);
        BigDecimal taxRate;
        if (taxableBase.compareTo(BigDecimal.ZERO) <= 0 || incomeTax.compareTo(BigDecimal.ZERO) <= 0) {
            taxRate = new BigDecimal("0.25");  // default razonable
        } else {
            taxRate = incomeTax.divide(taxableBase, MC)
                    .max(BigDecimal.ZERO)
                    .min(new BigDecimal("0.40"));
        }

        // NOPAT = ebitda × (1 - taxRate)
        BigDecimal nopat = ebitda.multiply(BigDecimal.ONE.subtract(taxRate, MC), MC);

        // investedCapital = totalDebt + totalEquity - cash
        BigDecimal investedCapital = totalDebt.add(totalEquity, MC).subtract(cash, MC);

        // ROIC = NOPAT / investedCapital; fallback a project() si no es calculable
        if (investedCapital.compareTo(BigDecimal.ZERO) <= 0 || nopat.compareTo(BigDecimal.ZERO) <= 0) {
            return project(historicalFcf, terminalGrowthRate, projectionYears);
        }
        BigDecimal roic = nopat.divide(investedCapital, MC);

        // reinvestmentRate = capex / NOPAT; clamped [0, 1]
        BigDecimal reinvestmentRate = capex.divide(nopat, MC)
                .max(BigDecimal.ZERO)
                .min(BigDecimal.ONE);

        // growthPhase1 = ROIC × reinvestmentRate; piso: terminalRate, techo: 30%
        BigDecimal phase1Rate = roic.multiply(reinvestmentRate, MC)
                .max(terminalGrowthRate)
                .min(MAX_INITIAL_GROWTH_RATE);

        // Proyectar con decay en dos etapas
        int phase1Years = Math.min(5, projectionYears);
        int phase2Years = projectionYears - phase1Years;

        List<ProjectedFcf> projections = new ArrayList<>(projectionYears);
        BigDecimal currentFcf = lastPositiveFcf(historicalFcf);

        // Fase 1: tasa constante derivada de ROIC
        for (int year = 1; year <= phase1Years; year++) {
            currentFcf = currentFcf.multiply(BigDecimal.ONE.add(phase1Rate, MC), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            projections.add(new ProjectedFcf(year, currentFcf,
                    phase1Rate.setScale(SCALE, RoundingMode.HALF_UP)));
        }

        if (phase2Years == 0) {
            // Forzar tasa terminal en el último año de fase 1 para consistencia
            var last = projections.get(projections.size() - 1);
            projections.set(projections.size() - 1,
                    new ProjectedFcf(last.year(), last.projectedValue(),
                            terminalGrowthRate.setScale(SCALE, RoundingMode.HALF_UP)));
            return projections;
        }

        // Fase 2: decay lineal desde phase1Rate hasta terminalGrowthRate
        for (int i = 1; i <= phase2Years; i++) {
            int year = phase1Years + i;
            BigDecimal growthRate = interpolateRate(phase1Rate, terminalGrowthRate, i, phase2Years);
            currentFcf = currentFcf.multiply(BigDecimal.ONE.add(growthRate, MC), MC)
                    .setScale(0, RoundingMode.HALF_UP);
            projections.add(new ProjectedFcf(year, currentFcf,
                    growthRate.setScale(SCALE, RoundingMode.HALF_UP)));
        }

        return projections;
    }

    /**
     * Calcula el CAGR sobre todos los datos históricos como tasa de arranque.
     * Si hay un solo dato, retorna la tasa terminal.
     */
    private BigDecimal calculateInitialRate(List<BigDecimal> historical, BigDecimal terminalRate) {
        if (historical.size() < 2) {
            return terminalRate;
        }

        BigDecimal first = historical.get(0);
        BigDecimal last = historical.get(historical.size() - 1);
        int periods = historical.size() - 1;

        // No se puede calcular CAGR si el primer valor es negativo o cero
        if (first.compareTo(BigDecimal.ZERO) <= 0 || last.compareTo(BigDecimal.ZERO) <= 0) {
            return terminalRate;
        }

        // CAGR = (last/first)^(1/periods) - 1
        double ratio = last.divide(first, MC).doubleValue();
        double cagr = Math.pow(ratio, 1.0 / periods) - 1.0;

        BigDecimal cagrDecimal = BigDecimal.valueOf(cagr).setScale(SCALE, RoundingMode.HALF_UP);

        // Si el CAGR es menor que la terminal rate, usar la terminal rate como piso
        if (cagrDecimal.compareTo(terminalRate) < 0) {
            return terminalRate;
        }

        // Cap del 30%: evita que históricos excepcionales inflen irrealmente el IV
        if (cagrDecimal.compareTo(MAX_INITIAL_GROWTH_RATE) > 0) {
            return MAX_INITIAL_GROWTH_RATE;
        }

        return cagrDecimal;
    }

    /**
     * Interpolación lineal desde initialRate hasta terminalRate a lo largo de los años.
     * En el último año retorna exactamente terminalRate.
     */
    private BigDecimal interpolateRate(BigDecimal initialRate, BigDecimal terminalRate,
                                        int currentYear, int totalYears) {
        if (currentYear == totalYears) {
            return terminalRate;
        }
        // fraction va de 0 (año 1) a 1 (último año)
        double fraction = (double) (currentYear - 1) / (totalYears - 1);
        double interpolated = initialRate.doubleValue()
                + fraction * (terminalRate.doubleValue() - initialRate.doubleValue());
        return BigDecimal.valueOf(interpolated).setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Retorna el último FCF positivo de la lista. Si todos son negativos, retorna el último.
     */
    private BigDecimal lastPositiveFcf(List<BigDecimal> historical) {
        for (int i = historical.size() - 1; i >= 0; i--) {
            if (historical.get(i).compareTo(BigDecimal.ZERO) > 0) {
                return historical.get(i);
            }
        }
        return historical.get(historical.size() - 1);
    }
}
