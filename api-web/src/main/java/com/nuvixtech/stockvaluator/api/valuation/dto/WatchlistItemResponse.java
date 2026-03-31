package com.nuvixtech.stockvaluator.api.valuation.dto;

import java.math.BigDecimal;

public record WatchlistItemResponse(
        String ticker,
        String companyName,
        BigDecimal currentPrice,
        BigDecimal intrinsicValue,
        BigDecimal marginOfSafety,
        String verdict
) {}
