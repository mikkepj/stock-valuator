package com.nuvixtech.stockvaluator.api.valuation.mapper;

import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.dto.WatchlistItemResponse;
import com.nuvixtech.stockvaluator.api.valuation.entity.ValuationResultEntity;
import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import com.nuvixtech.stockvaluator.ingestion.entity.MarketData;
import com.nuvixtech.stockvaluator.valuation.ValuationResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
public class ValuationMapper {

    /** Convierte el resultado del engine + company a entidad JPA para persistir. */
    public ValuationResultEntity toEntity(ValuationResult result, Company company) {
        var entity = new ValuationResultEntity();
        entity.setCompany(company);
        entity.setCalculatedAt(LocalDateTime.now());
        entity.setIntrinsicValue(result.intrinsicValuePerShare());
        entity.setMarketPrice(result.marketPrice());
        entity.setMarginOfSafety(result.marginOfSafety());
        entity.setVerdict(result.verdict().name());
        entity.setWacc(result.wacc());
        entity.setTerminalGrowth(result.terminalGrowthRate());
        entity.setProjectionYears(result.projectionYears());
        entity.setTerminalValue(result.terminalValue());
        entity.setNetDebt(result.netDebt());
        entity.setSensitivityMatrix(result.sensitivityMatrix());
        entity.setBreakdown(result.breakdown());
        return entity;
    }

    /** Convierte la entidad JPA a DTO de respuesta REST. */
    public ValuationResponse toResponse(ValuationResultEntity entity) {
        var company = entity.getCompany();
        return new ValuationResponse(
                company.getTicker(),
                company.getName(),
                company.getSector(),
                entity.getIntrinsicValue(),
                entity.getMarketPrice(),
                entity.getMarginOfSafety(),
                entity.getVerdict(),
                entity.getWacc(),
                entity.getTerminalGrowth(),
                entity.getProjectionYears() != null ? entity.getProjectionYears() : 10,
                entity.getTerminalValue(),
                entity.getNetDebt(),
                entity.getSensitivityMatrix(),
                entity.getBreakdown(),
                entity.getCalculatedAt()
        );
    }

    /** Convierte entidad JPA + market data a item resumido de watchlist. */
    public WatchlistItemResponse toWatchlistItem(ValuationResultEntity entity, MarketData marketData) {
        var company = entity.getCompany();
        return new WatchlistItemResponse(
                company.getTicker(),
                company.getName(),
                marketData != null ? marketData.getPrice() : entity.getMarketPrice(),
                entity.getIntrinsicValue(),
                entity.getMarginOfSafety(),
                entity.getVerdict()
        );
    }
}
