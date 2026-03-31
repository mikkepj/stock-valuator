package com.nuvixtech.stockvaluator.api.watchlist.controller;

import com.nuvixtech.stockvaluator.api.valuation.dto.WatchlistItemResponse;
import com.nuvixtech.stockvaluator.api.watchlist.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/watchlist")
@Tag(name = "Watchlist", description = "Gestión de la lista de seguimiento de tickers")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "Retorna todos los tickers en watchlist con su última valuación")
    public ResponseEntity<List<WatchlistItemResponse>> getWatchlist() {
        return ResponseEntity.ok(watchlistService.getWatchlist());
    }

    @PostMapping("/{ticker}")
    @Operation(summary = "Agrega un ticker a la watchlist")
    public ResponseEntity<WatchlistItemResponse> add(@PathVariable String ticker) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(watchlistService.add(ticker.toUpperCase()));
    }

    @DeleteMapping("/{ticker}")
    @Operation(summary = "Elimina un ticker de la watchlist")
    public ResponseEntity<Void> remove(@PathVariable String ticker) {
        watchlistService.remove(ticker.toUpperCase());
        return ResponseEntity.noContent().build();
    }
}
