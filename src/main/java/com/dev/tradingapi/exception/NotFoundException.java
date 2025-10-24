package com.dev.tradingapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource  is not found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class NotFoundException extends RuntimeException {

  /**
   * Constructs a NotFoundException with the specified detail message.
   *
   * @param message the detail message
   */
  public NotFoundException(String message) {
    super(message);
  }

  /**
   * Constructs a NotFoundException with the specified detail message and cause.
   * Preserves the original stack trace for debugging.
   *
   * @param message the detail message
   * @param cause the underlying cause
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }
}
