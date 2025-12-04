package com.trading.sec;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates investigation reports from audit log analysis.
 * Exports findings to text files for SEC case documentation.
 */
public class ReportGenerator {

  /**
   * Generates a comprehensive investigation report.
   *
   * @param logs audit logs analyzed
   * @param patterns suspicious patterns found
   * @param stats account statistics
   * @param filename output file name
   * @return true if report generated successfully
   */
  public boolean generateReport(List<AuditLogEntry> logs,
                                List<PatternDetector.SuspiciousPattern> patterns,
                                Map<String, PatternDetector.AccountStats> stats,
                                String filename) {
    try (FileWriter writer = new FileWriter(filename)) {
      // Header
      writer.write("=".repeat(70) + "\n");
      writer.write("SEC COMPLIANCE INVESTIGATION REPORT\n");
      writer.write("Generated: " + LocalDateTime.now().format(
              DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
      writer.write("=".repeat(70) + "\n\n");

      // Summary
      writer.write("SUMMARY\n");
      writer.write("-".repeat(70) + "\n");
      writer.write(String.format("Total Audit Logs Analyzed: %d\n", logs.size()));
      writer.write(String.format("Suspicious Patterns Found: %d\n", patterns.size()));
      writer.write(String.format("Accounts Monitored: %d\n", stats.size()));
      writer.write("\n");

      // Suspicious Patterns
      if (!patterns.isEmpty()) {
        writer.write("SUSPICIOUS PATTERNS DETECTED\n");
        writer.write("-".repeat(70) + "\n");
        for (PatternDetector.SuspiciousPattern pattern : patterns) {
          writer.write(String.format("Severity: %s\n", pattern.getSeverity()));
          writer.write(String.format("Type: %s\n", pattern.getType()));
          writer.write(String.format("Account ID: %s\n", pattern.getAccountId()));
          writer.write(String.format("Description: %s\n", pattern.getDescription()));
          writer.write("\n");
        }
      } else {
        writer.write("SUSPICIOUS PATTERNS DETECTED\n");
        writer.write("-".repeat(70) + "\n");
        writer.write("No suspicious patterns detected in this analysis period.\n\n");
      }

      // Account Statistics
      writer.write("ACCOUNT ACTIVITY STATISTICS\n");
      writer.write("-".repeat(70) + "\n");
      if (!stats.isEmpty()) {
        for (PatternDetector.AccountStats stat : stats.values()) {
          writer.write(String.format("Account: %s\n", stat.getAccountId()));
          writer.write(String.format("  Total Requests: %d\n", stat.getTotalRequests()));
          writer.write(String.format("  Error Count: %d\n", stat.getErrorCount()));
          writer.write(String.format("  Avg Latency: %.1f ms\n", stat.getAvgLatencyMs()));
          writer.write("\n");
        }
      } else {
        writer.write("No account activity found in analysis period.\n\n");
      }

      // Recent Activity Log
      writer.write("RECENT ACTIVITY LOG (Last 10 entries)\n");
      writer.write("-".repeat(70) + "\n");
      int count = Math.min(10, logs.size());
      for (int i = 0; i < count; i++) {
        AuditLogEntry log = logs.get(i);
        writer.write(String.format("%s | Account: %s | %s %s | Status: %d\n",
                log.getTs(),
                log.getAccountId() != null ? log.getAccountId() : "N/A",
                log.getMethod(),
                log.getPath(),
                log.getStatus()));
      }

      writer.write("\n");
      writer.write("=".repeat(70) + "\n");
      writer.write("END OF REPORT\n");
      writer.write("=".repeat(70) + "\n");

      return true;
    } catch (IOException e) {
      System.err.println("Error generating report: " + e.getMessage());
      return false;
    }
  }

  /**
   * Generates a CSV export of audit logs for further analysis.
   *
   * @param logs audit logs to export
   * @param filename output CSV file name
   * @return true if export successful
   */
  public boolean exportToCSV(List<AuditLogEntry> logs, String filename) {
    try (FileWriter writer = new FileWriter(filename)) {
      // Header
      writer.write("Timestamp,Account ID,Method,Path,Status,Latency (ms)\n");

      // Data rows
      for (AuditLogEntry log : logs) {
        writer.write(String.format("%s,%s,%s,%s,%d,%d\n",
                log.getTs(),
                log.getAccountId() != null ? log.getAccountId() : "N/A",
                log.getMethod(),
                log.getPath(),
                log.getStatus(),
                log.getLatencyMs()));
      }

      return true;
    } catch (IOException e) {
      System.err.println("Error exporting to CSV: " + e.getMessage());
      return false;
    }
  }
}