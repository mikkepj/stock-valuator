package com.nuvixtech.stockvaluator.valuation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class SectorDefaultsTest {

    @Test
    void forSector_technology_returnsSectorBeta() {
        var params = SectorDefaults.forSector("Technology", new BigDecimal("178.50"));

        // Beta Damodaran Technology ≈ 1.48
        assertTrue(params.marketRiskPremium().compareTo(new BigDecimal("0.040")) >= 0,
                "ERP Technology debe ser >= 4.0%");
        // terminalGrowthRate Technology = 3.0%
        assertEquals(0, params.terminalGrowthRate().compareTo(new BigDecimal("0.030")),
                "terminalGrowthRate Technology debe ser 3.0%, fue: " + params.terminalGrowthRate());
        assertEquals(new BigDecimal("178.50"), params.marketPrice());
    }

    @Test
    void forSector_unknownSector_returnsGlobalDefaults() {
        var paramsUnknown  = SectorDefaults.forSector("UnknownXYZ", new BigDecimal("100"));
        var paramsDefaults = DcfParameters.defaults(new BigDecimal("100"));

        assertEquals(0, paramsUnknown.terminalGrowthRate().compareTo(paramsDefaults.terminalGrowthRate()),
                "Sector desconocido debe usar terminalGrowthRate global default");
        assertEquals(0, paramsUnknown.marketRiskPremium().compareTo(paramsDefaults.marketRiskPremium()),
                "Sector desconocido debe usar marketRiskPremium global default");
    }

    @Test
    void forSector_nullSector_returnsGlobalDefaults() {
        var paramsNull     = SectorDefaults.forSector(null, new BigDecimal("100"));
        var paramsDefaults = DcfParameters.defaults(new BigDecimal("100"));

        assertEquals(0, paramsNull.terminalGrowthRate().compareTo(paramsDefaults.terminalGrowthRate()),
                "Sector null debe usar terminalGrowthRate global default");
    }

    @Test
    void forSector_utilities_hasLowerTerminalGrowthThanTechnology() {
        var techParams     = SectorDefaults.forSector("Technology", new BigDecimal("100"));
        var utilitiesParams = SectorDefaults.forSector("Utilities", new BigDecimal("100"));

        assertTrue(techParams.terminalGrowthRate().compareTo(utilitiesParams.terminalGrowthRate()) > 0,
                "Technology debe tener terminalGrowthRate mayor que Utilities. " +
                "tech=" + techParams.terminalGrowthRate() + " util=" + utilitiesParams.terminalGrowthRate());
    }

    @Test
    void forSector_energy_hasLowerTerminalGrowthThanSoftware() {
        var softwareParams = SectorDefaults.forSector("Software", new BigDecimal("100"));
        var energyParams   = SectorDefaults.forSector("Energy", new BigDecimal("100"));

        assertTrue(softwareParams.terminalGrowthRate().compareTo(energyParams.terminalGrowthRate()) > 0,
                "Software debe tener terminalGrowthRate mayor que Energy. " +
                "software=" + softwareParams.terminalGrowthRate() + " energy=" + energyParams.terminalGrowthRate());
    }

    @Test
    void forSector_semiconductors_usesDamodaranBetaInErp() {
        // Semiconductors ERP en el plan = 4.5% (mismo que global)
        var params = SectorDefaults.forSector("Semiconductors", new BigDecimal("100"));

        assertNotNull(params);
        assertTrue(params.projectionYears() > 0,
                "projectionYears debe ser positivo");
        assertTrue(params.marketRiskPremium().compareTo(BigDecimal.ZERO) > 0,
                "marketRiskPremium debe ser positivo");
    }
}
