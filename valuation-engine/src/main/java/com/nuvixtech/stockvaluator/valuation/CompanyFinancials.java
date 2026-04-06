package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Input del motor DCF. Contiene los datos financieros históricos y de mercado
 * necesarios para calcular el valor intrínseco de una empresa.
 *
 * Los FCF históricos deben estar en orden ascendente (año más antiguo primero).
 * Si se proveen estimaciones de analistas (analystFcfEstimates), se usan directamente
 * para los primeros N años de proyección en lugar de derivar el CAGR de los históricos.
 */
public record CompanyFinancials(
        String ticker,
        List<BigDecimal> historicalFcf,
        BigDecimal totalDebt,
        BigDecimal cashAndEquivalents,
        BigDecimal totalEquity,
        BigDecimal interestExpense,
        BigDecimal incomeTaxExpense,
        BigDecimal beta,
        long sharesOutstanding,
        BigDecimal ebitda,
        BigDecimal marketCap,          // market cap para ponderar WACC; si es 0 usa totalEquity como fallback
        List<BigDecimal> analystFcfEstimates  // estimaciones de analistas (orden ascendente, año+1 al año+N)
) {
    public CompanyFinancials {
        Objects.requireNonNull(ticker, "ticker no puede ser null");
        Objects.requireNonNull(historicalFcf, "historicalFcf no puede ser null");
        Objects.requireNonNull(totalDebt, "totalDebt no puede ser null");
        Objects.requireNonNull(cashAndEquivalents, "cashAndEquivalents no puede ser null");
        Objects.requireNonNull(totalEquity, "totalEquity no puede ser null");
        Objects.requireNonNull(interestExpense, "interestExpense no puede ser null");
        Objects.requireNonNull(incomeTaxExpense, "incomeTaxExpense no puede ser null");
        Objects.requireNonNull(beta, "beta no puede ser null");
        Objects.requireNonNull(ebitda, "ebitda no puede ser null");
        Objects.requireNonNull(marketCap, "marketCap no puede ser null");
        if (analystFcfEstimates == null) {
            analystFcfEstimates = List.of();
        }

        if (historicalFcf.isEmpty()) {
            throw new IllegalArgumentException("historicalFcf debe contener al menos un año");
        }
        if (sharesOutstanding <= 0) {
            throw new IllegalArgumentException("sharesOutstanding debe ser mayor que cero");
        }

        historicalFcf = List.copyOf(historicalFcf);
        analystFcfEstimates = List.copyOf(analystFcfEstimates);
    }

    /** Retorna el market cap si es positivo, o totalEquity como fallback (valor en libros). */
    public BigDecimal equityValue() {
        return marketCap.compareTo(BigDecimal.ZERO) > 0 ? marketCap : totalEquity;
    }

    /** Indica si hay estimaciones de analistas disponibles. */
    public boolean hasAnalystEstimates() {
        return !analystFcfEstimates.isEmpty();
    }
}
