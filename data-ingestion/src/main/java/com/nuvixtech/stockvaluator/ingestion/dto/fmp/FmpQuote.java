package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Maps to FMP endpoint: /api/v3/quote/{ticker}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FmpQuote(
    String symbol,
    String name,
    BigDecimal price,
    @JsonProperty("marketCap") long marketCap,
    BigDecimal pe,
    @JsonProperty("sharesOutstanding") long sharesOutstanding,
    String exchange,
    BigDecimal eps
) {}
