package com.dev.tradingapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an order violates risk constraints.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class RiskException extends RuntimeException {
    public RiskException(String message) {
        super(message);
    }
}
