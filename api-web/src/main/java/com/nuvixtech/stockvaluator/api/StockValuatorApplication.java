package com.nuvixtech.stockvaluator.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nuvixtech.stockvaluator")
@EntityScan(basePackages = "com.nuvixtech.stockvaluator")
@EnableJpaRepositories(basePackages = "com.nuvixtech.stockvaluator")
@EnableScheduling
public class StockValuatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockValuatorApplication.class, args);
    }
}
