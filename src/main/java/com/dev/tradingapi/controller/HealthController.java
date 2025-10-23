package com.dev.tradingapi.controller;

import com.dev.tradingapi.service.AuditService;
import com.dev.tradingapi.service.MarketService;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for comprehensive system health checks.
 * Provides detailed health status for all system components.
 */
@RestController
@RequestMapping("/health")
public class HealthController {

  private final DataSource dataSource;
  private final AuditService auditService;
  private final MarketService marketService;

  /**
   * Constructor with dependency injection.
   *
   * @param dataSource the database datasource
   * @param auditService the audit logging service
   * @param marketService the market data service
   */
  @Autowired
  public HealthController(DataSource dataSource,
                          AuditService auditService,
                          MarketService marketService) {
    this.dataSource = dataSource;
    this.auditService = auditService;
    this.marketService = marketService;
  }

  /**
   * Comprehensive health check endpoint.
   * Checks database connectivity, audit system, and market data service.
   *
   * @return ResponseEntity with detailed health status
   */
  @GetMapping
  public ResponseEntity<Map<String, Object>> health() {
    Map<String, String> components = new HashMap<>();
    boolean allHealthy = true;

    // Check database connectivity
    try {
      dataSource.getConnection().close();
      components.put("database", "healthy");
    } catch (Exception e) {
      components.put("database", "unhealthy");
      allHealthy = false;
    }

    // Check audit logging system
    try {
      auditService.getAllLogs();
      components.put("auditLogging", "healthy");
    } catch (Exception e) {
      components.put("auditLogging", "unhealthy");
      allHealthy = false;
    }

    // Check market data service
    try {
      marketService.getQuote("AAPL");
      components.put("marketData", "healthy");
    } catch (Exception e) {
      components.put("marketData", "unhealthy");
      allHealthy = false;
    }

    // Build response
    Map<String, Object> health = new HashMap<>();
    health.put("status", allHealthy ? "ok" : "degraded");
    health.put("timestamp", Instant.now().toString());
    health.put("components", components);
    health.put("version", "0.0.1-SNAPSHOT");

    return ResponseEntity.ok(health);
  }
}