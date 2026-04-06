package com.nuvixtech.stockvaluator.api.valuation.controller;

import com.nuvixtech.stockvaluator.api.valuation.dto.FcfEstimateRequest;
import com.nuvixtech.stockvaluator.api.valuation.service.FcfEstimateService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/companies")
public class FcfEstimateController {

    private final FcfEstimateService fcfEstimateService;

    public FcfEstimateController(FcfEstimateService fcfEstimateService) {
        this.fcfEstimateService = fcfEstimateService;
    }

    /**
     * Guarda estimaciones de FCF para un ticker.
     * Los valores deben estar en orden ascendente (año+1 al año+N).
     *
     * Ejemplo para MSFT desde Koyfin:
     * POST /api/v1/companies/MSFT/fcf-estimates
     * {"estimates": [99000000000, 122000000000, 146000000000, 174000000000, 210000000000]}
     */
    @PostMapping("/{ticker}/fcf-estimates")
    public ResponseEntity<Void> saveEstimates(@PathVariable String ticker,
                                              @Valid @RequestBody FcfEstimateRequest request) {
        fcfEstimateService.save(ticker, request.estimates());
        return ResponseEntity.noContent().build();
    }
}
