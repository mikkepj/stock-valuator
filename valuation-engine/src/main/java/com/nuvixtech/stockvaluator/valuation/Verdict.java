package com.nuvixtech.stockvaluator.valuation;

/**
 * Veredicto de valuación comparando precio de mercado vs valor intrínseco.
 */
public enum Verdict {
    /** Margen de seguridad > 15%: la acción cotiza por debajo de su valor intrínseco. */
    UNDERVALUED,
    /** Margen de seguridad entre -15% y 15%: la acción cotiza cerca de su valor intrínseco. */
    FAIR_VALUE,
    /** Margen de seguridad < -15%: la acción cotiza por encima de su valor intrínseco. */
    OVERVALUED
}
