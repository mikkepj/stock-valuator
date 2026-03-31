package com.nuvixtech.stockvaluator.api.exception;

public class TickerNotFoundException extends RuntimeException {
    public TickerNotFoundException(String ticker) {
        super("No se encontraron datos para el ticker: " + ticker);
    }
}
