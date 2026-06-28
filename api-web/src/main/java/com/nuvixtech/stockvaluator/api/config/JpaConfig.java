package com.nuvixtech.stockvaluator.api.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "com.nuvixtech.stockvaluator")
@EnableJpaRepositories(basePackages = "com.nuvixtech.stockvaluator")
public class JpaConfig {
}
