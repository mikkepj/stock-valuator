package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProjectedFcfTest {

    @Test
    void constructor_validData_createsInstance() {
        var projected = new ProjectedFcf(1, new BigDecimal("105000000000"), new BigDecimal("0.05"));

        assertEquals(1, projected.year());
        assertEquals(new BigDecimal("105000000000"), projected.projectedValue());
        assertEquals(new BigDecimal("0.05"), projected.growthRateApplied());
    }

    @Test
    void constructor_negativeYear_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedFcf(-1, new BigDecimal("100000000"), new BigDecimal("0.05")));
    }

    @Test
    void constructor_zeroYear_throwsException() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProjectedFcf(0, new BigDecimal("100000000"), new BigDecimal("0.05")));
    }

    @Test
    void constructor_nullProjectedValue_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ProjectedFcf(1, null, new BigDecimal("0.05")));
    }

    @Test
    void constructor_nullGrowthRate_throwsException() {
        assertThrows(NullPointerException.class,
                () -> new ProjectedFcf(1, new BigDecimal("100000000"), null));
    }

    @Test
    void constructor_negativeProjectedValue_isAllowed() {
        // FCF negativo es válido (empresa con pérdidas)
        var projected = new ProjectedFcf(1, new BigDecimal("-5000000000"), new BigDecimal("-0.10"));
        assertTrue(projected.projectedValue().compareTo(BigDecimal.ZERO) < 0);
    }
}
