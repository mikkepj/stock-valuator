package com.nuvixtech.stockvaluator.ingestion.service;

import com.nuvixtech.stockvaluator.ingestion.client.ExchangeRateApiClient;
import com.nuvixtech.stockvaluator.ingestion.exception.CurrencyConversionException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class CurrencyConversionService {

    private final ExchangeRateApiClient exchangeRateApiClient;

    public CurrencyConversionService(ExchangeRateApiClient exchangeRateApiClient) {
        this.exchangeRateApiClient = exchangeRateApiClient;
    }

    /**
     * Returns the exchange rate from {@code fromCurrency} to USD.
     * If {@code fromCurrency} is already "USD", returns 1 without any HTTP call.
     *
     * @throws CurrencyConversionException if the rate is unavailable
     */
    public BigDecimal getExchangeRateToUsd(String fromCurrency) {
        if ("USD".equalsIgnoreCase(fromCurrency)) {
            return BigDecimal.ONE;
        }
        return exchangeRateApiClient.getUsdRate(fromCurrency)
                .orElseThrow(() -> new CurrencyConversionException(
                        "No exchange rate available for currency: " + fromCurrency +
                        ". Cannot convert financial statements to USD."));
    }
}
