package com.nuvixtech.stockvaluator.ingestion.exception;

public class CurrencyConversionException extends RuntimeException {
    public CurrencyConversionException(String message) {
        super(message);
    }
}
