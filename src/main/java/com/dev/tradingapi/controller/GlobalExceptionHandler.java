package com.dev.tradingapi.controller;

import com.dev.tradingapi.dto.ApiErrorResponse;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/**
 * Centralized exception handling for REST controllers.
 *
 * <p>Maps domain-specific exceptions to appropriate HTTP status codes so
 * clients receive clear, consistent error responses.</p>
 */
@ControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Maps {@link RiskException} to HTTP 400 Bad Request.
   */
  @ExceptionHandler(RiskException.class)
  public ResponseEntity<ApiErrorResponse> handleRiskException(RiskException ex) {
    HttpStatus status = HttpStatus.BAD_REQUEST;
    ApiErrorResponse body = new ApiErrorResponse(
        status.value(),
        "RISK_VIOLATION",
        ex.getMessage());
    return ResponseEntity.status(status).body(body);
  }

  /**
   * Maps {@link NotFoundException} to HTTP 404 Not Found.
   */
  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ApiErrorResponse> handleNotFoundException(NotFoundException ex) {
    HttpStatus status = HttpStatus.NOT_FOUND;
    ApiErrorResponse body = new ApiErrorResponse(
        status.value(),
        "NOT_FOUND",
        ex.getMessage());
    return ResponseEntity.status(status).body(body);
  }
}
