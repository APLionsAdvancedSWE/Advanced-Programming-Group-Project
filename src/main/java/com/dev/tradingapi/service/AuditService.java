package com.dev.tradingapi.service;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.repository.AuditLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
   * @param path optional path filter
   * @param start optional start time
   * @param end optional end time
   * @param page page number (0-indexed)
   * @param pageSize number of results per page
   * @return list of matching audit logs
   */
  public List<AuditLog> search(String apiKey, String path,
                               Instant start, Instant end,
                               int page, int pageSize) {
    Pageable pageable = PageRequest.of(page, pageSize,
            Sort.by(Sort.Direction.DESC, "ts"));

    // Apply filters based on what's provided
    if (apiKey != null && path != null && start != null && end != null) {
      // All filters
      return auditLogRepository.findByApiKey(apiKey).stream()
              .filter(log -> log.getPath().equals(path))
              .filter(log -> log.getTs().isAfter(start) && log.getTs().isBefore(end))
              .sorted((a, b) -> b.getTs().compareTo(a.getTs()))
              .skip((long) page * pageSize)
              .limit(pageSize)
              .toList();
    } else if (apiKey != null) {
      return auditLogRepository.findByApiKey(apiKey).stream()
              .sorted((a, b) -> b.getTs().compareTo(a.getTs()))
              .skip((long) page * pageSize)
              .limit(pageSize)
              .toList();
    } else if (path != null) {
      return auditLogRepository.findByPath(path).stream()
              .sorted((a, b) -> b.getTs().compareTo(a.getTs()))
              .skip((long) page * pageSize)
              .limit(pageSize)
              .toList();
    } else if (start != null && end != null) {
      return auditLogRepository.findByTsBetween(start, end).stream()
              .sorted((a, b) -> b.getTs().compareTo(a.getTs()))
              .skip((long) page * pageSize)
              .limit(pageSize)
              .toList();
    } else {
      // No filters - return all with pagination
      return auditLogRepository.findAll(pageable).getContent();
    }
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