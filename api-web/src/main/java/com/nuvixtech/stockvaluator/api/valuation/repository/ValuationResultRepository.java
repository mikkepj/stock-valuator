package com.nuvixtech.stockvaluator.api.valuation.repository;

import com.nuvixtech.stockvaluator.api.valuation.entity.ValuationResultEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ValuationResultRepository extends JpaRepository<ValuationResultEntity, Long> {

    Optional<ValuationResultEntity> findFirstByCompanyTickerOrderByCalculatedAtDesc(String ticker);
}
