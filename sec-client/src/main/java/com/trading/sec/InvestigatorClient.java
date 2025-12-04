package com.trading.sec;

import java.util.List;
import java.util.Scanner;

/**
 * Interactive SEC investigator client.
 * Demonstrates multiple instances can run simultaneously.
 */
public class InvestigatorClient {

  private final ApiService api;
  private final PatternDetector detector;
  private final String investigatorId;

  public InvestigatorClient(String baseUrl, String apiKey, String investigatorId) {
    this.api = new ApiService(baseUrl, apiKey);
    this.detector = new PatternDetector();
    this.investigatorId = investigatorId;
  }

  public void run() {
    Scanner scanner = new Scanner(System.in);

    System.out.println("╔════════════════════════════════════════════════════════╗");
    System.out.println("║     SEC COMPLIANCE INVESTIGATION TOOL                  ║");
    System.out.println("║     Investigator ID: " + investigatorId + "                          ║");
    System.out.println("╚════════════════════════════════════════════════════════╝");
    System.out.println();

    // Check connection
    if (!api.checkHealth()) {
      System.out.println("ERROR: Cannot connect to trading service!");
      return;
    }
    System.out.println("✓ Connected to trading service\n");

    boolean running = true;
    while (running) {
      System.out.println("\n--- Investigation Menu ---");
      System.out.println("1. Query recent audit logs");
      System.out.println("2. Query logs by account ID");
      System.out.println("3. Detect high-frequency patterns");
      System.out.println("4. Show account statistics");
      System.out.println("5. Exit");
      System.out.print("\nSelect option: ");

      String choice = scanner.nextLine().trim();
      System.out.println();

      switch (choice) {
        case "1":
          queryRecentLogs();
          break;
        case "2":
          queryByAccount(scanner);
          break;
        case "3":
          detectPatterns();
          break;
        case "4":
          showStatistics();
          break;
        case "5":
          running = false;
          System.out.println("Investigator " + investigatorId + " signing off...");
          break;
        default:
          System.out.println("Invalid option. Please try again.");
      }
    }

    api.close();
    scanner.close();
  }

  private void queryRecentLogs() {
    System.out.println("[" + investigatorId + "] Querying recent audit logs...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 10);
    System.out.println("Found " + logs.size() + " recent log entries:\n");
    logs.forEach(log -> System.out.println("  " + log));
  }

  private void queryByAccount(Scanner scanner) {
    System.out.print("Enter Account ID to investigate: ");
    String accountId = scanner.nextLine().trim();

    System.out.println("[" + investigatorId + "] Querying logs for account: " + accountId);
    List<AuditLogEntry> logs = api.fetchAuditLogs(accountId, null, null, 0, 50);
    System.out.println("Found " + logs.size() + " log entries for this account:\n");
    logs.forEach(log -> System.out.println("  " + log));
  }

  private void detectPatterns() {
    System.out.println("[" + investigatorId + "] Analyzing for suspicious patterns...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 100);
    List<PatternDetector.SuspiciousPattern> patterns =
            detector.detectHighFrequency(logs, 3, 10);

    if (patterns.isEmpty()) {
      System.out.println("No suspicious high-frequency patterns detected.");
    } else {
      System.out.println("ALERT: Found " + patterns.size() + " suspicious pattern(s):\n");
      patterns.forEach(p -> System.out.println("  " + p));
    }
  }

  private void showStatistics() {
    System.out.println("[" + investigatorId + "] Calculating account statistics...");
    List<AuditLogEntry> logs = api.fetchAuditLogs(null, null, null, 0, 100);
    var stats = detector.getAccountStatistics(logs);

    System.out.println("Account Statistics (" + stats.size() + " accounts):\n");
    stats.values().forEach(s -> System.out.println("  " + s));
  }

  public static void main(String[] args) {
    String baseUrl = "http://localhost:8080";
    String apiKey = "sec-investigator-001";

    // Investigator ID from args or default
    String investigatorId = args.length > 0 ? args[0] : "INV-001";

    InvestigatorClient client = new InvestigatorClient(baseUrl, apiKey, investigatorId);
    client.run();
  }
}