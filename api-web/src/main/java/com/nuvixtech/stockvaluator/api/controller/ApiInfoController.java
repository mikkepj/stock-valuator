package com.nuvixtech.stockvaluator.api.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ApiInfoController {

    @GetMapping("/info")
    public Map<String, Object> info() {
        return Map.of(
                "application", "Stock Valuator",
                "version", "0.1.0-SNAPSHOT",
                "description", "DCF-based intrinsic value calculator",
                "timestamp", LocalDateTime.now().toString()
        );
    }
}
