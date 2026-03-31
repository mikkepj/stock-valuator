package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to FMP endpoint: /api/v3/cash-flow-statement/{ticker}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpCashFlowStatement(
    String date,
    String symbol,
    String period,
    @JsonProperty("operatingCashFlow") long operatingCashFlow,
    @JsonProperty("capitalExpenditure") long capitalExpenditure,
    @JsonProperty("freeCashFlow") long freeCashFlow,
    @JsonProperty("calendarYear") String calendarYear
) {}
