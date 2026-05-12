package com.nuvixtech.stockvaluator.api.valuation.controller;

import com.nuvixtech.stockvaluator.api.valuation.dto.CalculateRequest;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.service.ValuationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/valuations")
@Tag(name = "Valuations", description = "DCF valuation endpoints")
public class ValuationController {

    private final ValuationService valuationService;

    public ValuationController(ValuationService valuationService) {
        this.valuationService = valuationService;
    }

    @GetMapping("/{ticker}")
    @Operation(summary = "Retorna la valuación DCF más reciente cacheada para un ticker")
    public ResponseEntity<ValuationResponse> getValuation(@PathVariable String ticker) {
        return ResponseEntity.ok(valuationService.getLatestValuation(ticker.toUpperCase()));
    }

    @PostMapping("/{ticker}/calculate")
    @Operation(summary = "Fuerza un recálculo DCF. Body opcional: {\"betaOverride\": 1.5}")
    public ResponseEntity<ValuationResponse> calculate(
            @PathVariable String ticker,
            @Valid @RequestBody(required = false) CalculateRequest request) {
        BigDecimal betaOverride = request != null ? request.betaOverride() : null;
        return ResponseEntity.ok(valuationService.calculate(ticker.toUpperCase(), betaOverride));
    }
}
