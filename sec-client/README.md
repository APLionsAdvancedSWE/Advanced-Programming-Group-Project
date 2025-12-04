# SEC Compliance Investigation Client

Standalone Java application for SEC investigators to query audit logs and detect suspicious trading patterns.

## Features

- Query audit logs via REST API
- Detect high-frequency trading (3+ requests in 10 min)
- Calculate error rates and latency statistics
- Generate investigation reports (TXT/CSV)
- Support multiple concurrent investigators

## Prerequisites

- Java 17+
- Maven 3.8+
- Trading service running on http://localhost:8080

## Build

mvn clean compile

## Running

### Connection Test
mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"

### Pattern Detection
mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"

### Report Generation
mvn exec:java -Dexec.mainClass="com.trading.sec.TestReportGeneration"

### Interactive Client
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-ID"

## Multiple Instances

The service distinguishes clients by:
- API credentials (sec-investigator:sec-pass)
- HTTP sessions (each instance = separate connection)
- Query parameters (each investigator queries different data)

Multiple investigators can run simultaneously without interference.

## Architecture

Multiple Investigators → HTTP Client → REST API → Audit Service → Database

Each investigator authenticates independently and queries via separate HTTP sessions.

## Pattern Detection Algorithms

**High-Frequency Trading:**
- Flags accounts with 3+ requests in 10-minute window
- Uses sliding window analysis

**High Error Rate:**
- Flags accounts with 20%+ error rate
- Minimum 5 requests required

## Generated Reports

**TXT Format:**
- Executive summary
- Flagged patterns
- Account statistics
- Recent activity

**CSV Format:**
- All audit log entries
- Timestamp, account, method, path, status, latency

## End-to-End Testing

See E2E_TEST_CHECKLIST.md for complete manual testing procedures.

## Project Structure

src/main/java/com/trading/sec/
├── ApiService.java          # HTTP client for REST API
├── AuditLogEntry.java       # Data model
├── PatternDetector.java     # Algorithm implementations
├── ReportGenerator.java     # TXT/CSV generation
├── InvestigatorClient.java  # Interactive CLI
├── TestConnection.java      # Connection test
├── TestPatternDetection.java # Pattern test
└── TestReportGeneration.java # Report test

## Dependencies

- Apache HttpClient 5.2.1
- Gson 2.10.1
- Maven 3.8+

## Troubleshooting

**Connection refused:** Verify service is running on port 8080
**401 Unauthorized:** Check credentials in ApiService.java
**No patterns detected:** Generate test data with curl commands