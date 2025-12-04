package com.dev.tradingapi.dto;

import com.dev.tradingapi.model.AuditLog;
import java.util.UUID;

/**
 * Data Transfer Object for audit log responses.
 * Masks sensitive information like API keys before sending to clients.
 */
public class AuditLogView {

  private UUID id;
  private String ts;
  private String maskedApiKey;
  private UUID accountId;
  private String method;
  private String path;
  private Integer status;
  private Integer latencyMs;
  private String bodyHash;

  /**
   * Masks an API key to show only the last 4 characters.
   *
   * @param apiKey the full API key
   * @return masked API key (e.g., "****abcd")
   */
  public static String maskApiKey(String apiKey) {
    if (apiKey == null || apiKey.length() <= 4) {
      return "****";
    }
    return "****" + apiKey.substring(apiKey.length() - 4);
  }

  /**
   * Creates an AuditLogView from an AuditLog entity.
   *
   * @param log the audit log entity
   * @return view object with masked sensitive data
   */
  public static AuditLogView from(AuditLog log) {
    AuditLogView view = new AuditLogView();
    view.id = log.getId();
    view.ts = log.getTs() != null ? log.getTs().toString() : null;
    view.maskedApiKey = maskApiKey(log.getApiKey());
    view.accountId = log.getAccountId();
    view.method = log.getMethod();
    view.path = log.getPath();
    view.status = log.getStatus();
    view.latencyMs = log.getLatencyMs();
    view.bodyHash = log.getBodyHash();
    return view;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public String getTs() {
    return ts;
  }

  public String getMaskedApiKey() {
    return maskedApiKey;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getMethod() {
    return method;
  }

  public String getPath() {
    return path;
  }

  public Integer getStatus() {
    return status;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public String getBodyHash() {
    return bodyHash;
  }
}