package com.trading.sec;

import java.util.List;
import java.util.Map;

/**
 * Test to demonstrate report generation capabilities.
 */
public class TestReportGeneration {
  public static void main(String[] args) {
    String baseUrl = "http://localhost:8080";
    String apiKey = "sec-investigator-001";

    ApiService api = new ApiService(baseUrl, apiKey);
    PatternDetector detector = new PatternDetector();
    ReportGenerator reporter = new ReportGenerator();

    System.out.println("=== SEC Investigation Report Generation ===\n");

    // Fetch and analyze data
    System.out.println("1. Fetching audit logs...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 100);
    System.out.println("   Found " + logs.size() + " log entries");

    System.out.println("\n2. Detecting suspicious patterns...");
    List<PatternDetector.SuspiciousPattern> patterns =
            detector.detectHighFrequency(logs, 3, 10);
    patterns.addAll(detector.detectHighErrorRate(logs, 20.0));
    System.out.println("   Found " + patterns.size() + " suspicious patterns");

    System.out.println("\n3. Calculating account statistics...");
    Map<String, PatternDetector.AccountStats> stats =
            detector.getAccountStatistics(logs);
    System.out.println("   Analyzed " + stats.size() + " accounts");

    // Generate reports
    System.out.println("\n4. Generating investigation report...");
    boolean reportSuccess = reporter.generateReport(
            logs, patterns, stats, "sec_investigation_report.txt");

    if (reportSuccess) {
      System.out.println("   ✓ Report saved to: sec_investigation_report.txt");
    } else {
      System.out.println("   ✗ Failed to generate report");
    }

    System.out.println("\n5. Exporting data to CSV...");
    boolean csvSuccess = reporter.exportToCSV(logs, "audit_logs_export.csv");

    if (csvSuccess) {
      System.out.println("   ✓ CSV exported to: audit_logs_export.csv");
    } else {
      System.out.println("   ✗ Failed to export CSV");
    }

    api.close();
    System.out.println("\n=== Report Generation Complete ===");
    System.out.println("\nGenerated files:");
    System.out.println("  - sec_investigation_report.txt (Full investigation report)");
    System.out.println("  - audit_logs_export.csv (Raw data for further analysis)");
  }
}