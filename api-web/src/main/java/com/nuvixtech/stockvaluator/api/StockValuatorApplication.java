package com.nuvixtech.stockvaluator.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nuvixtech.stockvaluator")
@EnableScheduling
public class StockValuatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockValuatorApplication.class, args);
    }
}
