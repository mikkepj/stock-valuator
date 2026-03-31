package com.nuvixtech.stockvaluator.valuation;

/**
 * DCF Valuation Engine — core calculation module.
 * <p>
 * This module is a pure Java library with ZERO framework dependencies.
 * It receives data via DTOs and returns valuation results.
 * All logic is unit-testable without Spring context.
 * </p>
 *
 * <h3>Components (to be implemented in Phase 2):</h3>
 * <ul>
 *   <li>{@code FreeCashFlowProjector} — projects future FCFs from historical data</li>
 *   <li>{@code WaccCalculator} — calculates Weighted Average Cost of Capital</li>
 *   <li>{@code TerminalValueCalculator} — Gordon Growth or Exit Multiple</li>
 *   <li>{@code DcfCalculator} — orchestrates the full DCF valuation</li>
 * </ul>
 *
 * @since 0.1.0
 */
public final class ValuationEngineInfo {

    public static final String MODULE_NAME = "valuation-engine";
    public static final String VERSION = "0.1.0-SNAPSHOT";

    private ValuationEngineInfo() {} // utility class
}
