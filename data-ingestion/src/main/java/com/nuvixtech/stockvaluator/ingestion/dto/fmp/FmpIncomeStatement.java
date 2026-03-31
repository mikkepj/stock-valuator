package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to FMP endpoint: /api/v3/income-statement/{ticker}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpIncomeStatement(
    String date,
    String symbol,
    String period,
    long revenue,
    @JsonProperty("operatingIncome") long operatingIncome,
    @JsonProperty("netIncome") long netIncome,
    long ebitda,
    @JsonProperty("interestExpense") long interestExpense,
    @JsonProperty("incomeTaxExpense") long incomeTaxExpense,
    @JsonProperty("weightedAverageShsOut") long sharesOutstanding,
    @JsonProperty("calendarYear") String calendarYear
) {}
