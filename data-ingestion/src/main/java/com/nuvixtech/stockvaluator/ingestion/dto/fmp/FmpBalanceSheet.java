package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Maps to FMP endpoint: /api/v3/balance-sheet-statement/{ticker}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpBalanceSheet(
    String date,
    String symbol,
    String period,
    @JsonProperty("totalDebt") long totalDebt,
    @JsonProperty("cashAndCashEquivalents") long cashAndCashEquivalents,
    @JsonProperty("totalStockholdersEquity") long totalEquity,
    @JsonProperty("totalAssets") long totalAssets,
    @JsonProperty("calendarYear") String calendarYear
) {}
