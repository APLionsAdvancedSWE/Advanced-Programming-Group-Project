package com.trading.sec;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Analyzes audit logs to detect suspicious trading patterns.
 * Used by SEC investigators to flag potential compliance violations.
 */
public class PatternDetector {

  /**
   * Detects high-frequency trading patterns.
   * Flags accounts that make many requests in a short time period.
   *
   * @param logs audit logs to analyze
   * @param threshold minimum number of requests to flag
   * @param windowMinutes time window in minutes
   * @return list of suspicious patterns found
   */
  public List<SuspiciousPattern> detectHighFrequency(List<AuditLogEntry> logs,
                                                       int threshold,
                                                       int windowMinutes) {
    List<SuspiciousPattern> patterns = new ArrayList<>();

    // Group logs by account
    Map<String, List<AuditLogEntry>> byAccount = logs.stream()
        .filter(log -> log.getAccountId() != null)
        .collect(Collectors.groupingBy(AuditLogEntry::getAccountId));

    // Check each account for high frequency
    for (Map.Entry<String, List<AuditLogEntry>> entry : byAccount.entrySet()) {
      String accountId = entry.getKey();
      List<AuditLogEntry> accountLogs = entry.getValue();

      // Sort by timestamp
      accountLogs.sort(Comparator.comparing(AuditLogEntry::getTs));

      // Check sliding window
      for (int i = 0; i < accountLogs.size(); i++) {
        int count = 1;
        Instant startTime = Instant.parse(accountLogs.get(i).getTs());

        for (int j = i + 1; j < accountLogs.size(); j++) {
          Instant currentTime = Instant.parse(accountLogs.get(j).getTs());
          long minutesDiff = Duration.between(startTime, currentTime).toMinutes();

          if (minutesDiff <= windowMinutes) {
            count++;
          } else {
            break;
          }
        }

        if (count >= threshold) {
          patterns.add(new SuspiciousPattern(
              "HIGH_FREQUENCY",
              accountId,
              String.format("Account made %d requests in %d minutes", count, windowMinutes),
              "HIGH"
          ));
          break; // Only report once per account
        }
      }
    }

    return patterns;
  }

  /**
   * Detects accounts with unusually high error rates.
   *
   * @param logs audit logs to analyze
   * @param errorThresholdPercent minimum error rate to flag (0-100)
   * @return list of suspicious patterns found
   */
  public List<SuspiciousPattern> detectHighErrorRate(List<AuditLogEntry> logs,
                                                       double errorThresholdPercent) {
    List<SuspiciousPattern> patterns = new ArrayList<>();

    // Group logs by account
    Map<String, List<AuditLogEntry>> byAccount = logs.stream()
        .filter(log -> log.getAccountId() != null)
        .collect(Collectors.groupingBy(AuditLogEntry::getAccountId));

    for (Map.Entry<String, List<AuditLogEntry>> entry : byAccount.entrySet()) {
      String accountId = entry.getKey();
      List<AuditLogEntry> accountLogs = entry.getValue();

      long totalRequests = accountLogs.size();
      long errorRequests = accountLogs.stream()
          .filter(log -> log.getStatus() >= 400)
          .count();

      if (totalRequests >= 5) { // Only check accounts with meaningful sample size
        double errorRate = (errorRequests * 100.0) / totalRequests;

        if (errorRate >= errorThresholdPercent) {
          patterns.add(new SuspiciousPattern(
              "HIGH_ERROR_RATE",
              accountId,
              String.format("Account has %.1f%% error rate (%d errors out of %d requests)",
                  errorRate, errorRequests, totalRequests),
              "MEDIUM"
          ));
        }
      }
    }

    return patterns;
  }

  /**
   * Gets summary statistics for all accounts.
   *
   * @param logs audit logs to analyze
   * @return map of accountId to statistics
   */
  public Map<String, AccountStats> getAccountStatistics(List<AuditLogEntry> logs) {
    Map<String, AccountStats> stats = new HashMap<>();

    Map<String, List<AuditLogEntry>> byAccount = logs.stream()
        .filter(log -> log.getAccountId() != null)
        .collect(Collectors.groupingBy(AuditLogEntry::getAccountId));

    for (Map.Entry<String, List<AuditLogEntry>> entry : byAccount.entrySet()) {
      String accountId = entry.getKey();
      List<AuditLogEntry> accountLogs = entry.getValue();

      int totalRequests = accountLogs.size();
      int errorCount = (int) accountLogs.stream()
          .filter(log -> log.getStatus() >= 400)
          .count();

      double avgLatency = accountLogs.stream()
          .mapToInt(AuditLogEntry::getLatencyMs)
          .average()
          .orElse(0.0);

      stats.put(accountId, new AccountStats(accountId, totalRequests, errorCount, avgLatency));
    }

    return stats;
  }

  /**
   * Represents a suspicious pattern found in audit logs.
   */
  public static class SuspiciousPattern {
    private final String type;
    private final String accountId;
    private final String description;
    private final String severity;

    public SuspiciousPattern(String type, String accountId, String description, String severity) {
      this.type = type;
      this.accountId = accountId;
      this.description = description;
      this.severity = severity;
    }

    public String getType() { return type; }
    public String getAccountId() { return accountId; }
    public String getDescription() { return description; }
    public String getSeverity() { return severity; }

    @Override
    public String toString() {
      return String.format("[%s] %s - Account: %s - %s",
          severity, type, accountId, description);
    }
  }

  /**
   * Statistical summary for an account.
   */
  public static class AccountStats {
    private final String accountId;
    private final int totalRequests;
    private final int errorCount;
    private final double avgLatencyMs;

    public AccountStats(String accountId, int totalRequests, int errorCount, double avgLatencyMs) {
      this.accountId = accountId;
      this.totalRequests = totalRequests;
      this.errorCount = errorCount;
      this.avgLatencyMs = avgLatencyMs;
    }

    public String getAccountId() { return accountId; }
    public int getTotalRequests() { return totalRequests; }
    public int getErrorCount() { return errorCount; }
    public double getAvgLatencyMs() { return avgLatencyMs; }

    @Override
    public String toString() {
      return String.format("Account %s: %d requests, %d errors, %.1fms avg latency",
          accountId, totalRequests, errorCount, avgLatencyMs);
    }
  }
}