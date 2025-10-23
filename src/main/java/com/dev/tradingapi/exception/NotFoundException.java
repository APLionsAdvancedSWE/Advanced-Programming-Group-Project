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
}
