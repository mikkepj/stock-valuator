package com.nuvixtech.stockvaluator.ingestion.dto.fmp;

import java.math.BigDecimal;

public record FmpForexQuote(
        String ticker,
        BigDecimal bid,
        BigDecimal ask,
        BigDecimal open,
        BigDecimal low,
        BigDecimal high,
        BigDecimal changes,
        String date
) {}
