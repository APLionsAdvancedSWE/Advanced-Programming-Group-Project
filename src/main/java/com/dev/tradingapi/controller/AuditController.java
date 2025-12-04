package com.dev.tradingapi.controller;

import com.dev.tradingapi.dto.AuditLogView;
import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.service.AuditService;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for audit log operations.
 * Provides endpoints to retrieve and search audit logs.
 */
@RestController
@RequestMapping("/audit")
public class AuditController {

  private final AuditService auditService;

  /**
   * Constructor with dependency injection.
   *
   * @param auditService the audit service
   */
  @Autowired
  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  /**
   * Get audit logs with optional filtering.
   * Supports filtering by API key, account ID, path, date range, and pagination.
   *
   * @param apiKey optional API key filter
   * @param accountId optional account ID filter
   * @param path optional request path filter
   * @param from optional start timestamp (ISO format)
   * @param to optional end timestamp (ISO format)
   * @param page page number (default 0)
   * @param pageSize number of results per page (default 50)
   * @return ResponseEntity with list of audit logs
   */
  @GetMapping("/logs")
  public ResponseEntity<Map<String, Object>> getLogs(
          @RequestParam(required = false) String apiKey,
          @RequestParam(required = false) String accountId,
          @RequestParam(required = false) String path,
          @RequestParam(required = false) String from,
          @RequestParam(required = false) String to,
          @RequestParam(defaultValue = "0") int page,
          @RequestParam(defaultValue = "50") int pageSize) {

    try {
      // Parse timestamps if provided
      Instant startTime = from != null ? Instant.parse(from) : null;
      Instant endTime = to != null ? Instant.parse(to) : null;

      // Search with filters
      List<AuditLog> logs = auditService.search(
              apiKey, accountId, path, startTime, endTime, page, pageSize
      );

      // Convert to views with masked API keys
      List<AuditLogView> views = logs.stream()
              .map(AuditLogView::from)
              .toList();

      // Build response
      Map<String, Object> response = new HashMap<>();
      response.put("items", views);
      response.put("page", page);
      response.put("pageSize", pageSize);
      response.put("total", views.size());

      return ResponseEntity.ok(response);

    } catch (Exception e) {
      // Handle errors (e.g., invalid timestamp format)
      Map<String, Object> error = new HashMap<>();
      error.put("error", "Failed to retrieve audit logs");
      error.put("message", e.getMessage());
      return ResponseEntity.badRequest().body(error);
    }
  }
}