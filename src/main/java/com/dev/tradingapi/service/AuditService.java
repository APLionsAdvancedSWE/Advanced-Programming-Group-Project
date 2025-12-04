package com.dev.tradingapi.service;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

/**
 * Service for managing audit logs.
 * Handles logging of API requests and searching through audit history.
 */
@Service
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  /**
   * Constructor with dependency injection.
   *
   * @param auditLogRepository the audit log repository
   */
  @Autowired
  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  /**
   * Log an API request.
   *
   * @param log the audit log entry to save
   */
  public void logRequest(AuditLog log) {
    try {
      auditLogRepository.save(log);
    } catch (Exception e) {
      // Don't let logging failures break the API
      System.err.println("Failed to save audit log: " + e.getMessage());
    }
  }

  /**
   * Search audit logs with optional filters.
   *
   * @param apiKey optional API key filter
   * @param accountId optional account ID filter
   * @param path optional path filter
   * @param start optional start time
   * @param end optional end time
   * @param page page number (0-indexed)
   * @param pageSize number of results per page
   * @return list of matching audit logs
   */
  public List<AuditLog> search(String apiKey, String accountId, String path,
                               Instant start, Instant end,
                               int page, int pageSize) {
    // Start with all logs
    List<AuditLog> results = auditLogRepository.findAll(
            Sort.by(Sort.Direction.DESC, "ts"));

    // Apply filters
    if (apiKey != null) {
      results = results.stream()
              .filter(log -> apiKey.equals(log.getApiKey()))
              .toList();
    }

    if (accountId != null) {
      results = results.stream()
              .filter(log -> accountId.equals(log.getAccountId() != null
                      ? log.getAccountId().toString() : null))
              .toList();
    }

    if (path != null) {
      results = results.stream()
              .filter(log -> path.equals(log.getPath()))
              .toList();
    }

    if (start != null) {
      results = results.stream()
              .filter(log -> log.getTs().isAfter(start))
              .toList();
    }

    if (end != null) {
      results = results.stream()
              .filter(log -> log.getTs().isBefore(end))
              .toList();
    }

    // Apply pagination
    return results.stream()
            .skip((long) page * pageSize)
            .limit(pageSize)
            .toList();
  }

  /**
   * Get all audit logs (for testing/admin purposes).
   *
   * @return all audit logs
   */
  public List<AuditLog> getAllLogs() {
    return auditLogRepository.findAll(Sort.by(Sort.Direction.DESC, "ts"));
  }
}