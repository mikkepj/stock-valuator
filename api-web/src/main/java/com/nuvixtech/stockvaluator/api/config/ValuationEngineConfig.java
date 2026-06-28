package com.nuvixtech.stockvaluator.api.config;

import com.nuvixtech.stockvaluator.valuation.DcfCalculator;
import com.nuvixtech.stockvaluator.valuation.FreeCashFlowProjector;
import com.nuvixtech.stockvaluator.valuation.MonteCarloAnalyzer;
import com.nuvixtech.stockvaluator.valuation.QualityScoreCalculator;
import com.nuvixtech.stockvaluator.valuation.ScenarioAnalyzer;
import com.nuvixtech.stockvaluator.valuation.SensitivityAnalyzer;
import com.nuvixtech.stockvaluator.valuation.TerminalValueCalculator;
import com.nuvixtech.stockvaluator.valuation.WaccCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra los componentes del valuation-engine como beans de Spring.
 * El engine es Java puro, sin anotaciones Spring propias.
 */
@Configuration
public class ValuationEngineConfig {

    @Bean
    public FreeCashFlowProjector freeCashFlowProjector() {
        return new FreeCashFlowProjector();
    }

    @Bean
    public WaccCalculator waccCalculator() {
        return new WaccCalculator();
    }

    @Bean
    public TerminalValueCalculator terminalValueCalculator() {
        return new TerminalValueCalculator();
    }

    @Bean
    public QualityScoreCalculator qualityScoreCalculator() {
        return new QualityScoreCalculator();
    }

    @Bean
    public DcfCalculator dcfCalculator(FreeCashFlowProjector projector,
                                        WaccCalculator waccCalculator,
                                        TerminalValueCalculator terminalValueCalculator,
                                        QualityScoreCalculator qualityScoreCalculator) {
        return new DcfCalculator(projector, waccCalculator, terminalValueCalculator,
                qualityScoreCalculator);
    }

    @Bean
    public SensitivityAnalyzer sensitivityAnalyzer() {
        return new SensitivityAnalyzer();
    }

    @Bean
    public ScenarioAnalyzer scenarioAnalyzer(DcfCalculator dcfCalculator) {
        return new ScenarioAnalyzer(dcfCalculator);
    }

    @Bean
    public MonteCarloAnalyzer monteCarloAnalyzer(DcfCalculator dcfCalculator) {
        return new MonteCarloAnalyzer(dcfCalculator);
    }
}
