package com.nuvixtech.stockvaluator.api.controller;

import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService;
import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService.IngestionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/ingest")
public class IngestionController {

    private static final Logger log = LoggerFactory.getLogger(IngestionController.class);
    private final FinancialDataService financialDataService;

    public IngestionController(FinancialDataService financialDataService) {
        this.financialDataService = financialDataService;
    }

    /**
     * POST /api/v1/ingest/{ticker}
     * Triggers full data ingestion for a given ticker.
     */
    @PostMapping("/{ticker}")
    public ResponseEntity<IngestionResult> ingestTicker(@PathVariable String ticker) {
        log.info("Ingestion requested for ticker: {}", ticker);
        IngestionResult result = financialDataService.ingest(ticker);
        return ResponseEntity.ok(result);
    }

    /**
     * Exception handler for ingestion errors.
     */
    @ExceptionHandler(FinancialDataService.IngestionException.class)
    public ResponseEntity<Map<String, String>> handleIngestionError(
            FinancialDataService.IngestionException ex) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "Ingestion failed",
                "message", ex.getMessage()
        ));
    }
}
