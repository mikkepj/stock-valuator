package com.nuvixtech.stockvaluator.ingestion.client;

import com.nuvixtech.stockvaluator.ingestion.dto.ExchangeRateResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Client for open.er-api.com — free, no API key required.
 * GET /v6/latest/{baseCurrency} returns rates vs all currencies.
 */
@Component
public class ExchangeRateApiClient {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiClient.class);
    private static final String BASE_URL = "https://open.er-api.com";

    private final RestClient restClient;

    public ExchangeRateApiClient() {
        this.restClient = RestClient.builder()
                .baseUrl(BASE_URL)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    /**
     * Returns the rate fromCurrency→USD, or empty if unavailable.
     */
    public Optional<BigDecimal> getUsdRate(String fromCurrency) {
        log.debug("Fetching exchange rate for {}/USD from open.er-api.com", fromCurrency);
        try {
            ExchangeRateResponse response = restClient.get()
                    .uri("/v6/latest/{currency}", fromCurrency)
                    .retrieve()
                    .body(ExchangeRateResponse.class);
            if (response == null || response.rates() == null) {
                return Optional.empty();
            }
            BigDecimal rate = response.rates().get("USD");
            if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(rate);
        } catch (RestClientException e) {
            log.error("Failed to fetch exchange rate for {}: {}", fromCurrency, e.getMessage());
            return Optional.empty();
        }
    }
}
