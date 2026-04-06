package com.nuvixtech.stockvaluator.ingestion.repository;

import com.nuvixtech.stockvaluator.ingestion.entity.FcfEstimate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FcfEstimateRepository extends JpaRepository<FcfEstimate, Long> {

    List<FcfEstimate> findByCompanyTickerOrderByFiscalYearAsc(String ticker);

    Optional<FcfEstimate> findByCompanyTickerAndFiscalYear(String ticker, Integer fiscalYear);

    void deleteByCompanyTicker(String ticker);
}
