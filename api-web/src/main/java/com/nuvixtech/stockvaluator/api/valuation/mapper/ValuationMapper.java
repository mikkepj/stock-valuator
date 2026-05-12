package com.nuvixtech.stockvaluator.api.valuation.mapper;

import com.nuvixtech.stockvaluator.api.valuation.dto.ScenarioResultDto;
import com.nuvixtech.stockvaluator.api.valuation.dto.ValuationResponse;
import com.nuvixtech.stockvaluator.api.valuation.dto.WatchlistItemResponse;
import com.nuvixtech.stockvaluator.api.valuation.entity.ValuationResultEntity;
import com.nuvixtech.stockvaluator.ingestion.entity.Company;
import com.nuvixtech.stockvaluator.ingestion.entity.MarketData;
import com.nuvixtech.stockvaluator.valuation.ScenarioResult;
import com.nuvixtech.stockvaluator.valuation.ValuationResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ValuationMapper {

    /** Convierte el resultado del engine + escenarios + company a entidad JPA para persistir. */
    public ValuationResultEntity toEntity(ValuationResult result, List<ScenarioResult> scenarios,
                                           Company company, BigDecimal betaUsed) {
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
        entity.setBetaUsed(betaUsed);
        entity.setSensitivityMatrix(result.sensitivityMatrix());
        entity.setBreakdown(result.breakdown());
        entity.setScenarios(scenariosToMaps(scenarios));
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
                entity.getBetaUsed(),
                mapsToScenarioDtos(entity.getScenarios()),
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

    private List<Map<String, Object>> scenariosToMaps(List<ScenarioResult> scenarios) {
        if (scenarios == null) return Collections.emptyList();
        return scenarios.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("scenarioName", s.scenarioName());
            m.put("intrinsicValuePerShare", s.intrinsicValuePerShare());
            m.put("marginOfSafety", s.marginOfSafety());
            m.put("verdict", s.verdict().name());
            m.put("initialGrowthRate", s.initialGrowthRate());
            m.put("terminalGrowthRate", s.terminalGrowthRate());
            m.put("wacc", s.wacc());
            return m;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private List<ScenarioResultDto> mapsToScenarioDtos(List<Map<String, Object>> scenarios) {
        if (scenarios == null) return Collections.emptyList();
        return scenarios.stream().map(m -> new ScenarioResultDto(
                (String) m.get("scenarioName"),
                toBigDecimal(m.get("intrinsicValuePerShare")),
                toBigDecimal(m.get("marginOfSafety")),
                (String) m.get("verdict"),
                toBigDecimal(m.get("initialGrowthRate")),
                toBigDecimal(m.get("terminalGrowthRate")),
                toBigDecimal(m.get("wacc"))
        )).toList();
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        return new BigDecimal(value.toString());
    }
}
