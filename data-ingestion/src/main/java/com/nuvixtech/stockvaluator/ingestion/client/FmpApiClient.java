package com.nuvixtech.stockvaluator.ingestion.client;

import com.nuvixtech.stockvaluator.ingestion.config.FmpApiProperties;
import com.nuvixtech.stockvaluator.ingestion.dto.fmp.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * HTTP client for Financial Modeling Prep API.
 * Uses Spring Boot 3.2+ RestClient for clean, synchronous calls.
 */
@Component
public class FmpApiClient {

    private static final Logger log = LoggerFactory.getLogger(FmpApiClient.class);

    private final RestClient restClient;
    private final String apiKey;

    public FmpApiClient(RestClient fmpRestClient, FmpApiProperties properties) {
        this.restClient = fmpRestClient;
        this.apiKey = properties.key();
    }

    /**
     * GET /api/v3/profile/{ticker}
     */
    public Optional<FmpCompanyProfile> getCompanyProfile(String ticker) {
        log.debug("Fetching company profile for {}", ticker);
        try {
            List<FmpCompanyProfile> result = restClient.get()
                    .uri("/profile?symbol={symbol}&apikey={key}", ticker, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null && !result.isEmpty()
                    ? Optional.of(result.getFirst())
                    : Optional.empty();
        } catch (RestClientException e) {
            log.error("Failed to fetch profile for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * GET /api/v3/income-statement/{ticker}?period=annual&limit={years}
     */
    public List<FmpIncomeStatement> getIncomeStatements(String ticker, int years) {
        log.debug("Fetching {} years of income statements for {}", years, ticker);
        try {
            List<FmpIncomeStatement> result = restClient.get()
                    .uri("/income-statement?symbol={symbol}&period=annual&limit={limit}&apikey={key}",
                            ticker, years, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch income statements for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /**
     * GET /api/v3/balance-sheet-statement/{ticker}?period=annual&limit={years}
     */
    public List<FmpBalanceSheet> getBalanceSheets(String ticker, int years) {
        log.debug("Fetching {} years of balance sheets for {}", years, ticker);
        try {
            List<FmpBalanceSheet> result = restClient.get()
                    .uri("/balance-sheet-statement?symbol={symbol}&period=annual&limit={limit}&apikey={key}",
                            ticker, years, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch balance sheets for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /**
     * GET /api/v3/cash-flow-statement/{ticker}?period=annual&limit={years}
     */
    public List<FmpCashFlowStatement> getCashFlowStatements(String ticker, int years) {
        log.debug("Fetching {} years of cash flow statements for {}", years, ticker);
        try {
            List<FmpCashFlowStatement> result = restClient.get()
                    .uri("/cash-flow-statement?symbol={symbol}&period=annual&limit={limit}&apikey={key}",
                            ticker, years, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null ? result : List.of();
        } catch (RestClientException e) {
            log.error("Failed to fetch cash flow statements for {}: {}", ticker, e.getMessage());
            return List.of();
        }
    }

    /**
     * GET /api/v3/quote/{ticker}
     */
    public Optional<FmpQuote> getQuote(String ticker) {
        log.debug("Fetching quote for {}", ticker);
        try {
            List<FmpQuote> result = restClient.get()
                    .uri("/quote?symbol={symbol}&apikey={key}", ticker, apiKey)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {});
            return result != null && !result.isEmpty()
                    ? Optional.of(result.getFirst())
                    : Optional.empty();
        } catch (RestClientException e) {
            log.error("Failed to fetch quote for {}: {}", ticker, e.getMessage());
            return Optional.empty();
        }
    }
}
