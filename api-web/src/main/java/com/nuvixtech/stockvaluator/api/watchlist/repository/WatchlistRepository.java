package com.nuvixtech.stockvaluator.api.watchlist.repository;

import com.nuvixtech.stockvaluator.api.watchlist.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WatchlistRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findAll();

    Optional<WatchlistEntry> findByCompanyTicker(String ticker);

    boolean existsByCompanyTicker(String ticker);
}
