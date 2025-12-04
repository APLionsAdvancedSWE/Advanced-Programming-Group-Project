package com.trading.sec;

import java.util.List;

/**
 * Simple test to verify API connection works.
 */
public class TestConnection {
  public static void main(String[] args) {
    String baseUrl = "http://localhost:8080";
    String apiKey = "sec-investigator-001";

    ApiService api = new ApiService(baseUrl, apiKey);

    System.out.println("=== SEC Compliance Client - Connection Test ===\n");

    // Test 1: Health check
    System.out.println("1. Checking service health...");
    boolean healthy = api.checkHealth();
    System.out.println("   Service status: " + (healthy ? "UP ✓" : "DOWN ✗"));

    if (!healthy) {
      System.out.println("\nError: Cannot connect to trading service at " + baseUrl);
      return;
    }

    // Test 2: Fetch recent audit logs
    System.out.println("\n2. Fetching recent audit logs...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 10);
    System.out.println("   Found " + logs.size() + " log entries\n");

    logs.forEach(log -> System.out.println("   " + log));

    api.close();
    System.out.println("\n=== Test Complete ===");
  }
}