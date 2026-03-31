package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ValuationResultTest {

    @Test
    void calculateMarginOfSafety_intrinsicAboveMarket_positiveMargin() {
        // intrinsic=200, market=150 → margen = (200-150)/150*100 = 33.33%
        var margin = ValuationResult.calculateMarginOfSafety(
                new BigDecimal("200"), new BigDecimal("150"));
        assertEquals(new BigDecimal("33.33"), margin);
    }

    @Test
    void calculateMarginOfSafety_intrinsicBelowMarket_negativeMargin() {
        // intrinsic=100, market=150 → margen = (100-150)/150*100 = -33.33%
        var margin = ValuationResult.calculateMarginOfSafety(
                new BigDecimal("100"), new BigDecimal("150"));
        assertEquals(new BigDecimal("-33.33"), margin);
    }

    @Test
    void calculateMarginOfSafety_equalValues_zeroMargin() {
        var margin = ValuationResult.calculateMarginOfSafety(
                new BigDecimal("100"), new BigDecimal("100"));
        assertEquals(new BigDecimal("0.00"), margin);
    }

    @Test
    void calculateVerdict_marginAbove15_isUndervalued() {
        assertEquals(Verdict.UNDERVALUED, ValuationResult.calculateVerdict(new BigDecimal("33.33")));
    }

    @Test
    void calculateVerdict_marginBelow15_isFairValue() {
        assertEquals(Verdict.FAIR_VALUE, ValuationResult.calculateVerdict(new BigDecimal("10")));
    }

    @Test
    void calculateVerdict_marginAt15_isFairValue() {
        assertEquals(Verdict.FAIR_VALUE, ValuationResult.calculateVerdict(new BigDecimal("15")));
    }

    @Test
    void calculateVerdict_marginAt_negative15_isFairValue() {
        assertEquals(Verdict.FAIR_VALUE, ValuationResult.calculateVerdict(new BigDecimal("-15")));
    }

    @Test
    void calculateVerdict_marginBelowNegative15_isOvervalued() {
        assertEquals(Verdict.OVERVALUED, ValuationResult.calculateVerdict(new BigDecimal("-33.33")));
    }

    @Test
    void constructor_nullTicker_throwsException() {
        assertThrows(NullPointerException.class, () -> buildResult(null));
    }

    @Test
    void projectedFcfs_returnsImmutableList() {
        var result = buildResult("AAPL");
        assertThrows(UnsupportedOperationException.class,
                () -> result.projectedFcfs().add(new ProjectedFcf(99, BigDecimal.ONE, BigDecimal.ZERO)));
    }

    // Helper que construye un ValuationResult válido
    private ValuationResult buildResult(String ticker) {
        return new ValuationResult(
                ticker,
                new BigDecimal("162.30"),
                new BigDecimal("178.50"),
                new BigDecimal("-9.08"),
                Verdict.OVERVALUED,
                new BigDecimal("0.089"),
                new BigDecimal("0.025"),
                10,
                new BigDecimal("2840000000000"),
                new BigDecimal("49000000000"),
                List.of(new ProjectedFcf(1, new BigDecimal("100000000000"), new BigDecimal("0.05"))),
                Collections.emptyMap(),
                Collections.emptyMap()
        );
    }
}
