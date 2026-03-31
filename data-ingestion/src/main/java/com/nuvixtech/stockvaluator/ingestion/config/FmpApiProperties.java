package com.nuvixtech.stockvaluator.ingestion.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps to the 'fmp.api' section in application.yml.
 * Spring Boot 3 binds these automatically — no @Value needed.
 */
@ConfigurationProperties(prefix = "fmp.api")
public record FmpApiProperties(
    String baseUrl,
    String key,
    int timeoutSeconds,
    int maxRetries
) {}
