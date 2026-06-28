package com.nuvixtech.stockvaluator.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExchangeRateResponse(
        String result,
        Map<String, BigDecimal> rates
) {}
