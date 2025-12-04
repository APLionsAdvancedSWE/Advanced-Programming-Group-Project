# SEC Compliance Investigation Client

A standalone Java application for SEC investigators to analyze trading activity through audit log analysis and pattern detection.

## Overview

The SEC Compliance Client is a read-only investigative tool that connects to the Trading Service API to:
- Query and filter audit logs by account, date range, and other criteria
- Detect suspicious trading patterns (high-frequency trading, error rates)
- Generate investigation reports for compliance documentation
- Support multiple investigators working simultaneously

This client demonstrates the "multiple different client programs" requirement - it is completely independent from retail trading clients and serves a distinct regulatory purpose.

## Features

### 1. Audit Log Analysis
- Query recent trading activity
- Filter by specific accountId
- Filter by date range
- Paginated results

### 2. Pattern Detection
- **High-Frequency Trading:** Flags accounts making excessive requests in short time windows
- **High Error Rate:** Identifies accounts with abnormal failure rates
- **Account Statistics:** Provides comprehensive activity summaries

### 3. Report Generation
- **TXT Reports:** Formatted investigation summaries with flagged patterns
- **CSV Export:** Raw data for spreadsheet analysis
- Both formats include timestamps, account details, and findings

### 4. Multiple Investigator Support
- Multiple instances can run simultaneously
- Each investigator has unique ID
- All share same Trading Service without interference

## Prerequisites

- Java 17+
- Maven 3.8+
- Trading Service running at `http://localhost:8080`

## Build Instructions
```bash
cd sec-client
mvn clean compile
```

## Running the Client

### Interactive Investigation Tool
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="YOUR-ID"
```

Example:
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INV-001"
```

**Menu Options:**
1. Query recent audit logs
2. Query logs by account ID
3. Detect high-frequency patterns
4. Show account statistics
5. Exit

### Test Programs

**Connection Test:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"
```

**Pattern Detection Test:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"
```

**Report Generation Test:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestReportGeneration"
```

## Configuration

Edit `src/main/resources/config.properties`:
```properties
api.base.url=http://localhost:8080
api.key=sec-investigator-001
```

- `api.base.url`: Trading Service URL
- `api.key`: Client identifier for API authentication

## Multiple Instances

### How It Works

Multiple investigators can run the client simultaneously:
```bash
# Terminal 1
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-A"

# Terminal 2  
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-B"
```

**How the Service Distinguishes Clients:**
1. **Same API Key:** All SEC clients use `api.key=sec-investigator-001`
2. **Same Client Type:** Service recognizes all as "SEC Compliance" clients
3. **Different Sessions:** Each investigator gets independent HTTP sessions
4. **Different Queries:** Investigators can query different accounts/time ranges simultaneously

The service's audit logs record:
- API key (identifies client type: SEC vs Retail)
- Query parameters (which account/data each investigator requested)
- Timestamps (when each request occurred)

This allows multiple investigators to work independently without seeing each other's queries, while the service tracks all activity.

## Architecture
```
┌─────────────────────────┐
│  SEC Investigator A     │
│  (InvestigatorClient)   │
└───────────┬─────────────┘
            │ HTTP Requests
            │ (api.key=sec-investigator-001)
            ↓
┌─────────────────────────┐
│   Trading Service API   │
│   localhost:8080        │
│   - /audit/logs         │
│   - /health             │
└───────────┬─────────────┘
            ↑
            │ HTTP Requests
            │ (api.key=sec-investigator-001)
┌───────────┴─────────────┐
│  SEC Investigator B     │
│  (InvestigatorClient)   │
└─────────────────────────┘
```

## API Usage

The client calls these Trading Service endpoints:

### GET /health
Check service availability.

**Request:**
```bash
GET http://localhost:8080/health
```

**Response:**
```json
{
  "status": "ok",
  "timestamp": "2025-11-23T23:00:00Z"
}
```

### GET /audit/logs
Query audit logs with optional filters.

**Request:**
```bash
GET http://localhost:8080/audit/logs?accountId={uuid}&page=0&pageSize=50
```

**Query Parameters:**
- `accountId` (optional): Filter by specific account UUID
- `from` (optional): Start timestamp (ISO format)
- `to` (optional): End timestamp (ISO format)
- `page` (optional): Page number (default: 0)
- `pageSize` (optional): Results per page (default: 50)

**Response:**
```json
{
  "total": 10,
  "page": 0,
  "pageSize": 50,
  "items": [
    {
      "id": "...",
      "ts": "2025-11-23T22:00:00Z",
      "maskedApiKey": "****key1",
      "accountId": "aaaa1111-1111-1111-1111-111111111111",
      "method": "GET",
      "path": "/orders",
      "status": 200,
      "latencyMs": 45
    }
  ]
}
```

## Pattern Detection Algorithms

### High-Frequency Trading Detection
- **Threshold:** Configurable number of requests within time window
- **Default:** 3+ requests in 10 minutes
- **Severity:** HIGH
- **Use Case:** Detect potential market manipulation or bot activity

### High Error Rate Detection
- **Threshold:** Configurable error percentage
- **Default:** 20%+ error rate (status >= 400)
- **Minimum Sample:** 5+ requests
- **Severity:** MEDIUM
- **Use Case:** Identify malfunctioning bots or suspicious probing

## Generated Reports

### Investigation Report (TXT)
Location: `sec_investigation_report.txt`

Contains:
- Executive summary
- Flagged suspicious patterns with severity levels
- Account activity statistics
- Recent transaction log

### CSV Export
Location: `audit_logs_export.csv`

Format:
```csv
Timestamp,Account ID,Method,Path,Status,Latency (ms)
2025-11-23T22:00:00Z,aaaa1111-...,GET,/orders,200,45
```

## End-to-End Testing

See [E2E_TEST_CHECKLIST.md](E2E_TEST_CHECKLIST.md) for complete testing procedures.

Quick test:
```bash
# 1. Generate test data
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health

# 2. Run pattern detection
mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"

# 3. Verify aaaa1111 is flagged for high-frequency trading
```

## Dependencies

- **Apache HttpClient 5.2.1:** HTTP communication
- **Gson 2.10.1:** JSON parsing

All managed via Maven - no manual downloads required.

## Project Structure
```
sec-client/
├── pom.xml                          # Maven configuration
├── README.md                        # This file
├── E2E_TEST_CHECKLIST.md           # Testing procedures
└── src/main/java/com/trading/sec/
    ├── ApiService.java              # HTTP client for Trading API
    ├── AuditLogEntry.java           # Data model
    ├── PatternDetector.java         # Analysis algorithms
    ├── ReportGenerator.java         # Report creation
    ├── InvestigatorClient.java      # Interactive CLI
    ├── TestConnection.java          # Connection test
    ├── TestPatternDetection.java    # Pattern test
    └── TestReportGeneration.java    # Report test
```

## Troubleshooting

**"Cannot connect to trading service"**
- Verify service is running: `curl http://localhost:8080/health`
- Check `config.properties` has correct URL

**"No logs found"**
- Database may be empty (H2 in-memory clears on restart)
- Generate test data (see E2E_TEST_CHECKLIST.md)

**"ClassNotFoundException"**
- Run `mvn clean compile` before executing

## Future Enhancements

- Date range filtering in interactive CLI
- Real-time monitoring mode
- Integration with external reporting systems
- Support for additional pattern detection algorithms
- Automated report scheduling