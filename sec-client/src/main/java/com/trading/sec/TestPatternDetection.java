package com.trading.sec;

import java.util.List;
import java.util.Map;

/**
 * Test to demonstrate pattern detection capabilities.
 */
public class TestPatternDetection {
  public static void main(String[] args) {
    String baseUrl = "http://localhost:8080";
    String apiKey = "sec-investigator-001";

    ApiService api = new ApiService(baseUrl, apiKey);
    PatternDetector detector = new PatternDetector();

    System.out.println("=== SEC Compliance Analysis ===\n");

    // Fetch audit logs
    System.out.println("Fetching audit logs...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 100);
    System.out.println("Analyzing " + logs.size() + " log entries\n");

    if (logs.isEmpty()) {
      System.out.println("No logs found. Generate some test data first.");
      api.close();
      return;
    }

    // Pattern 1: High Frequency Trading
    System.out.println("--- Pattern Detection: High Frequency ---");
    List<PatternDetector.SuspiciousPattern> highFreq =
            detector.detectHighFrequency(logs, 3, 10);

    if (highFreq.isEmpty()) {
      System.out.println("No high-frequency patterns detected.");
    } else {
      highFreq.forEach(System.out::println);
    }

    // Pattern 2: High Error Rate
    System.out.println("\n--- Pattern Detection: High Error Rate ---");
    List<PatternDetector.SuspiciousPattern> highError =
            detector.detectHighErrorRate(logs, 20.0);

    if (highError.isEmpty()) {
      System.out.println("No high error rate patterns detected.");
    } else {
      highError.forEach(System.out::println);
    }

    // Account Statistics
    System.out.println("\n--- Account Statistics ---");
    Map<String, PatternDetector.AccountStats> stats =
            detector.getAccountStatistics(logs);

    if (stats.isEmpty()) {
      System.out.println("No account activity found.");
    } else {
      stats.values().forEach(System.out::println);
    }

    api.close();
    System.out.println("\n=== Analysis Complete ===");
  }
}