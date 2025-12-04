package com.trading.sec;

/**
 * Represents an audit log entry from the Trading API.
 * Used by SEC investigators to track trading activity.
 */
public class AuditLogEntry {
  private String id;
  private String ts;
  private String maskedApiKey;
  private String accountId;
  private String method;
  private String path;
  private Integer status;
  private Integer latencyMs;

  public String getId() { return id; }
  public String getTs() { return ts; }
  public String getMaskedApiKey() { return maskedApiKey; }
  public String getAccountId() { return accountId; }
  public String getMethod() { return method; }
  public String getPath() { return path; }
  public Integer getStatus() { return status; }
  public Integer getLatencyMs() { return latencyMs; }

  @Override
  public String toString() {
    return String.format("[%s] Account: %s | %s %s | Status: %d | %dms",
            ts, accountId != null ? accountId : "N/A", method, path, status, latencyMs);
  }
}