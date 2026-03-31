package com.nuvixtech.stockvaluator.ingestion.repository;

import com.nuvixtech.stockvaluator.ingestion.entity.MarketData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MarketDataRepository extends JpaRepository<MarketData, Long> {

    Optional<MarketData> findTopByCompanyTickerOrderByFetchedAtDesc(String ticker);
}
