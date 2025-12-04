package com.dev.tradingapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing an audit log entry.
 * Records all API calls made to the trading service for compliance and debugging.
 */
@Entity
@Table(name = "audit_logs", indexes = {
  @Index(name = "idx_audit_api_key", columnList = "api_key"),
  @Index(name = "idx_audit_account_id", columnList = "account_id"),
  @Index(name = "idx_audit_path", columnList = "path"),
  @Index(name = "idx_audit_ts", columnList = "ts")
})
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  private UUID id;

  @Column(name = "ts", nullable = false)
  private Instant ts;

  @Column(name = "api_key")
  private String apiKey;

  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "method")
  private String method;

  @Column(name = "path")
  private String path;

  @Column(name = "status")
  private Integer status;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "body_hash")
  private String bodyHash;

  /**
   * Default constructor.
   */
  public AuditLog() {
    this.ts = Instant.now();
  }

  /**
   * Constructor with all fields including accountId.
   *
   * @param apiKey API key used for the request
   * @param accountId Account ID making the request
   * @param method HTTP method (GET, POST, etc.)
   * @param path Request path
   * @param status HTTP status code
   * @param latencyMs Request latency in milliseconds
   */
  public AuditLog(String apiKey, UUID accountId, String method, String path,
                  Integer status, Integer latencyMs) {
    this();
    this.apiKey = apiKey;
    this.accountId = accountId;
    this.method = method;
    this.path = path;
    this.status = status;
    this.latencyMs = latencyMs;
  }

  /**
   * Constructor without accountId for backward compatibility with existing tests.
   *
   * @param apiKey API key used for the request
   * @param method HTTP method (GET, POST, etc.)
   * @param path Request path
   * @param status HTTP status code
   * @param latencyMs Request latency in milliseconds
   */
  public AuditLog(String apiKey, String method, String path,
                  Integer status, Integer latencyMs) {
    this();
    this.apiKey = apiKey;
    this.accountId = null;
    this.method = method;
    this.path = path;
    this.status = status;
    this.latencyMs = latencyMs;
  }

  // Getters and Setters

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public Instant getTs() {
    return ts;
  }

  public void setTs(Instant ts) {
    this.ts = ts;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  public String getMethod() {
    return method;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public String getPath() {
    return path;
  }

  public void setPath(String path) {
    this.path = path;
  }

  public Integer getStatus() {
    return status;
  }

  public void setStatus(Integer status) {
    this.status = status;
  }

  public Integer getLatencyMs() {
    return latencyMs;
  }

  public void setLatencyMs(Integer latencyMs) {
    this.latencyMs = latencyMs;
  }

  public String getBodyHash() {
    return bodyHash;
  }

  public void setBodyHash(String bodyHash) {
    this.bodyHash = bodyHash;
  }

  @Override
  public String toString() {
    return "AuditLog{"
            + "id=" + id
            + ", ts=" + ts
            + ", apiKey='" + apiKey + '\''
            + ", accountId=" + accountId
            + ", method='" + method + '\''
            + ", path='" + path + '\''
            + ", status=" + status
            + ", latencyMs=" + latencyMs
            + '}';
  }
}