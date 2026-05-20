package com.nuvixtech.stockvaluator.valuation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Parámetros DCF por sector según tablas de Damodaran.
 * Permite que el motor use defaults más precisos cuando se conoce el sector de la empresa.
 *
 * Campos por sector: terminalGrowthRate, marketRiskPremium (ERP implícito), projectionYears.
 * El betaOverride del usuario siempre prevalece sobre el beta sectorial.
 */
public class SectorDefaults {

    // {terminalGrowthRate, marketRiskPremium}
    private static final Map<String, BigDecimal[]> SECTOR_TABLE = Map.ofEntries(
            Map.entry("Technology",         new BigDecimal[]{ new BigDecimal("0.030"), new BigDecimal("0.045") }),
            Map.entry("Semiconductors",     new BigDecimal[]{ new BigDecimal("0.030"), new BigDecimal("0.045") }),
            Map.entry("Software",           new BigDecimal[]{ new BigDecimal("0.035"), new BigDecimal("0.045") }),
            Map.entry("Healthcare",         new BigDecimal[]{ new BigDecimal("0.025"), new BigDecimal("0.045") }),
            Map.entry("Consumer Defensive", new BigDecimal[]{ new BigDecimal("0.020"), new BigDecimal("0.045") }),
            Map.entry("Consumer Cyclical",  new BigDecimal[]{ new BigDecimal("0.020"), new BigDecimal("0.045") }),
            Map.entry("Energy",             new BigDecimal[]{ new BigDecimal("0.015"), new BigDecimal("0.045") }),
            Map.entry("Financials",         new BigDecimal[]{ new BigDecimal("0.025"), new BigDecimal("0.050") }),
            Map.entry("Industrials",        new BigDecimal[]{ new BigDecimal("0.025"), new BigDecimal("0.045") }),
            Map.entry("Utilities",          new BigDecimal[]{ new BigDecimal("0.020"), new BigDecimal("0.045") }),
            Map.entry("Real Estate",        new BigDecimal[]{ new BigDecimal("0.025"), new BigDecimal("0.045") }),
            Map.entry("Communication",      new BigDecimal[]{ new BigDecimal("0.025"), new BigDecimal("0.045") }),
            Map.entry("Materials",          new BigDecimal[]{ new BigDecimal("0.020"), new BigDecimal("0.045") })
    );

    private static final BigDecimal DEFAULT_TERMINAL_GROWTH = new BigDecimal("0.025");
    private static final BigDecimal DEFAULT_MRP             = new BigDecimal("0.055");
    private static final BigDecimal DEFAULT_RISK_FREE_RATE  = new BigDecimal("0.045");
    private static final int        DEFAULT_PROJECTION_YEARS = 10;

    /**
     * Retorna DcfParameters ajustados para el sector dado.
     * Fallback a los defaults globales si el sector es null o desconocido.
     */
    public static DcfParameters forSector(String sector, BigDecimal marketPrice) {
        BigDecimal[] sectorValues = sector != null ? SECTOR_TABLE.get(sector) : null;

        BigDecimal terminalGrowth = sectorValues != null ? sectorValues[0] : DEFAULT_TERMINAL_GROWTH;
        BigDecimal mrp            = sectorValues != null ? sectorValues[1] : DEFAULT_MRP;

        return new DcfParameters(
                DEFAULT_RISK_FREE_RATE,
                mrp,
                terminalGrowth,
                DEFAULT_PROJECTION_YEARS,
                marketPrice
        );
    }

    /** Retorna solo la tasa de crecimiento terminal para un sector. */
    public static BigDecimal terminalGrowthFor(String sector) {
        BigDecimal[] vals = sector != null ? SECTOR_TABLE.get(sector) : null;
        return vals != null ? vals[0] : DEFAULT_TERMINAL_GROWTH;
    }

    /** Retorna el ERP (market risk premium) para un sector. */
    public static BigDecimal marketRiskPremiumFor(String sector) {
        BigDecimal[] vals = sector != null ? SECTOR_TABLE.get(sector) : null;
        return vals != null ? vals[1] : DEFAULT_MRP;
    }
}
