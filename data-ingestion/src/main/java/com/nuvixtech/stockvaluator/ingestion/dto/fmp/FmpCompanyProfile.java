package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Maps to FMP endpoint: /api/v3/profile/{ticker}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpCompanyProfile(
    String symbol,
    @JsonProperty("companyName") String companyName,
    String sector,
    String industry,
    String exchange,
    String currency,
    BigDecimal beta,
    BigDecimal price,
    @JsonProperty("mktCap") long marketCap,
    @JsonProperty("lastDiv") BigDecimal lastDividend
) {}
