package com.nuvixtech.stockvaluator.api.watchlist.service;

import com.nuvixtech.stockvaluator.api.exception.TickerNotFoundException;
import com.nuvixtech.stockvaluator.api.valuation.dto.WatchlistItemResponse;
import com.nuvixtech.stockvaluator.api.valuation.mapper.ValuationMapper;
import com.nuvixtech.stockvaluator.api.valuation.repository.ValuationResultRepository;
import com.nuvixtech.stockvaluator.api.watchlist.entity.WatchlistEntry;
import com.nuvixtech.stockvaluator.api.watchlist.repository.WatchlistRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.CompanyRepository;
import com.nuvixtech.stockvaluator.ingestion.repository.MarketDataRepository;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final CompanyRepository companyRepository;
    private final ValuationResultRepository valuationResultRepository;
    private final MarketDataRepository marketDataRepository;
    private final ValuationMapper mapper;

    public WatchlistService(WatchlistRepository watchlistRepository,
                             CompanyRepository companyRepository,
                             ValuationResultRepository valuationResultRepository,
                             MarketDataRepository marketDataRepository,
                             ValuationMapper mapper) {
        this.watchlistRepository = watchlistRepository;
        this.companyRepository = companyRepository;
        this.valuationResultRepository = valuationResultRepository;
        this.marketDataRepository = marketDataRepository;
        this.mapper = mapper;
    }

    @Cacheable("watchlist")
    public List<WatchlistItemResponse> getWatchlist() {
        return watchlistRepository.findAll().stream()
                .map(entry -> {
                    var ticker = entry.getCompany().getTicker();
                    var latestValuation = valuationResultRepository
                            .findFirstByCompanyTickerOrderByCalculatedAtDesc(ticker);
                    var latestMarket = marketDataRepository
                            .findTopByCompanyTickerOrderByFetchedAtDesc(ticker);
                    return latestValuation.map(v -> mapper.toWatchlistItem(v, latestMarket.orElse(null)))
                            .orElse(null);
                })
                .filter(item -> item != null)
                .toList();
    }

    @CacheEvict(value = "watchlist", allEntries = true)
    @Transactional
    public WatchlistItemResponse add(String ticker) {
        String t = ticker.toUpperCase();
        var company = companyRepository.findByTicker(t)
                .orElseThrow(() -> new TickerNotFoundException(t));

        if (!watchlistRepository.existsByCompanyTicker(t)) {
            var entry = new WatchlistEntry();
            entry.setCompany(company);
            watchlistRepository.save(entry);
        }

        var latestValuation = valuationResultRepository
                .findFirstByCompanyTickerOrderByCalculatedAtDesc(t)
                .orElseThrow(() -> new TickerNotFoundException(t));
        var latestMarket = marketDataRepository.findTopByCompanyTickerOrderByFetchedAtDesc(t);
        return mapper.toWatchlistItem(latestValuation, latestMarket.orElse(null));
    }

    @CacheEvict(value = "watchlist", allEntries = true)
    @Transactional
    public void remove(String ticker) {
        String t = ticker.toUpperCase();
        var entry = watchlistRepository.findByCompanyTicker(t)
                .orElseThrow(() -> new TickerNotFoundException(t));
        watchlistRepository.delete(entry);
    }
}
