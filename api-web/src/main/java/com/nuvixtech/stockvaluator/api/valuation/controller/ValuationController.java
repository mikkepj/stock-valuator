package com.nuvixtech.stockvaluator.api.valuation.controller;

import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.service.ValuationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    @Operation(summary = "Fuerza un recálculo DCF fresco para el ticker dado")
    public ResponseEntity<ValuationResponse> calculate(@PathVariable String ticker) {
        return ResponseEntity.ok(valuationService.calculate(ticker.toUpperCase()));
    }
}
