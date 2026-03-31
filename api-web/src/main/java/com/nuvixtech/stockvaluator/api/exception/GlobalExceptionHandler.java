package com.nuvixtech.stockvaluator.api.exception;

import com.nuvixtech.stockvaluator.ingestion.service.FinancialDataService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(TickerNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTickerNotFound(
            TickerNotFoundException ex, HttpServletRequest request) {
        log.warn("Ticker no encontrado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(LocalDateTime.now(), 404, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(InsufficientDataException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientData(
            InsufficientDataException ex, HttpServletRequest request) {
        log.warn("Datos insuficientes: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(LocalDateTime.now(), 422, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(FinancialDataService.IngestionException.class)
    public ResponseEntity<ErrorResponse> handleIngestion(
            FinancialDataService.IngestionException ex, HttpServletRequest request) {
        log.error("Error de ingestion: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ErrorResponse(LocalDateTime.now(), 422, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Error inesperado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErrorResponse(LocalDateTime.now(), 500,
                        "Error interno del servidor", request.getRequestURI()));
    }
}
