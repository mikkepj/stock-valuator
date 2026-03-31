package com.nuvixtech.stockvaluator.api.exception;

public class InsufficientDataException extends RuntimeException {
    public InsufficientDataException(String ticker, String reason) {
        super("Datos insuficientes para calcular valuación de " + ticker + ": " + reason);
    }
}
