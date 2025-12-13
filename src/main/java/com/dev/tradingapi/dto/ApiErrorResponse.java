package com.dev.tradingapi.dto;

/**
 * Standard error payload returned by the Trading API.
 */
public class ApiErrorResponse {

  private final int status;
  private final String error;
  private final String message;

  /**
   * Creates a new standardized API error response.
   *
   * @param status HTTP status code
   * @param error short application-specific error code
   * @param message human-readable error description
   */
  public ApiErrorResponse(int status, String error, String message) {
    this.status = status;
    this.error = error;
    this.message = message;
  }

  public int getStatus() {
    return status;
  }

  public String getError() {
    return error;
  }

  public String getMessage() {
    return message;
  }
}
