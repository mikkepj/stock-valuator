package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Input del motor DCF. Contiene los datos financieros históricos y de mercado
 * necesarios para calcular el valor intrínseco de una empresa.
 *
 * Los FCF históricos deben estar en orden ascendente (año más antiguo primero).
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
        BigDecimal ebitda
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

        if (historicalFcf.isEmpty()) {
            throw new IllegalArgumentException("historicalFcf debe contener al menos un año");
        }
        if (sharesOutstanding <= 0) {
            throw new IllegalArgumentException("sharesOutstanding debe ser mayor que cero");
        }

        historicalFcf = List.copyOf(historicalFcf);
    }
}
