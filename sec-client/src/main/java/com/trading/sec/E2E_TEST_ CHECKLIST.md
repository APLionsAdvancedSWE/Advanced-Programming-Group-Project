# SEC Compliance Client - End-to-End Test Checklist

This checklist documents manual end-to-end tests for the SEC Compliance Client with the Trading Service API.

## Prerequisites

- [ ] Trading Service is running on `http://localhost:8080`
- [ ] Service database contains test data (run data generation commands below)
- [ ] SEC Client is built: `cd sec-client && mvn clean compile`

## Test Data Generation

Run these commands to populate the service with test data:
```bash
# Generate test audit logs with different accounts
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health
curl -H "X-Account-Id: aaaa1111-1111-1111-1111-111111111111" http://localhost:8080/health
curl -H "X-Account-Id: bbbb2222-2222-2222-2222-222222222222" http://localhost:8080/health
curl -H "X-Account-Id: bbbb2222-2222-2222-2222-222222222222" http://localhost:8080/health
curl -H "X-Account-Id: cccc3333-3333-3333-3333-333333333333" http://localhost:8080/health
```

---

## Test 1: Service Health Check

**Objective:** Verify SEC client can connect to trading service

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"`

**Expected Output:**
```
Service status: UP ✓
```

**Status:** [ ] Pass [ ] Fail

---

## Test 2: Query Recent Audit Logs

**Objective:** Verify client can fetch and display audit logs

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"`

**Expected Output:**
- Shows list of audit log entries
- Each entry displays: timestamp, account, method, path, status, latency
- Minimum 6 entries (from test data generation)

**Status:** [ ] Pass [ ] Fail

---

## Test 3: Query Audit Logs by AccountId

**Objective:** Verify accountId filtering works correctly

**Steps:**
1. Run interactive client: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-A"`
2. Select option: `2` (Query logs by account ID)
3. Enter accountId: `aaaa1111-1111-1111-1111-111111111111`

**Expected Output:**
- Shows ONLY 3 log entries for account aaaa1111
- Does NOT show entries for bbbb2222 or cccc3333
- All entries display correct accountId

**Verification via API:**
```bash
curl "http://localhost:8080/audit/logs?accountId=aaaa1111-1111-1111-1111-111111111111" | jq
```

**Status:** [ ] Pass [ ] Fail

---

## Test 4: Detect High-Frequency Trading Patterns

**Objective:** Verify pattern detection identifies suspicious activity

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"`

**Expected Output:**
```
--- Pattern Detection: High Frequency ---
[HIGH] HIGH_FREQUENCY - Account: aaaa1111-1111-1111-1111-111111111111 - Account made 3 requests in 10 minutes
```

**Verification:**
- Account aaaa1111 should be flagged (3 requests in short time)
- Accounts bbbb2222 and cccc3333 should NOT be flagged
- Severity marked as "HIGH"

**Status:** [ ] Pass [ ] Fail

---

## Test 5: Detect High Error Rate Patterns

**Objective:** Verify error rate detection works

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"`

**Expected Output:**
```
--- Pattern Detection: High Error Rate ---
No high error rate patterns detected.
```

**Note:** With test data, all requests return 200 OK, so no errors expected.

**Status:** [ ] Pass [ ] Fail

---

## Test 6: Account Statistics Summary

**Objective:** Verify client calculates account statistics correctly

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"`

**Expected Output:**
```
--- Account Statistics ---
Account aaaa1111-1111-1111-1111-111111111111: 3 requests, 0 errors, <X>ms avg latency
Account bbbb2222-2222-2222-2222-222222222222: 2 requests, 0 errors, <Y>ms avg latency
Account cccc3333-3333-3333-3333-333333333333: 1 requests, 0 errors, <Z>ms avg latency
```

**Verification:**
- All 3 accounts appear
- Request counts match test data (3, 2, 1)
- Error counts are 0
- Average latency values are reasonable (< 1000ms)

**Status:** [ ] Pass [ ] Fail

---

## Test 7: Generate Investigation Report

**Objective:** Verify report generation creates proper TXT file

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestReportGeneration"`
2. Check file created: `ls sec_investigation_report.txt`
3. View contents: `cat sec_investigation_report.txt`

**Expected Output:**
- File `sec_investigation_report.txt` exists in sec-client/ directory
- Contains sections:
    - SUMMARY (total logs, patterns, accounts)
    - SUSPICIOUS PATTERNS DETECTED (with aaaa1111 flagged)
    - ACCOUNT ACTIVITY STATISTICS (all 3 accounts listed)
    - RECENT ACTIVITY LOG (last 10 entries)

**Status:** [ ] Pass [ ] Fail

---

## Test 8: Export to CSV

**Objective:** Verify CSV export for further analysis

**Steps:**
1. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestReportGeneration"`
2. Check file created: `ls audit_logs_export.csv`
3. View contents: `cat audit_logs_export.csv`

**Expected Output:**
- File `audit_logs_export.csv` exists
- First line is header: `Timestamp,Account ID,Method,Path,Status,Latency (ms)`
- Subsequent lines contain audit log data
- All 6 test entries present

**Status:** [ ] Pass [ ] Fail

---

## Test 9: Multiple Client Instances

**Objective:** Verify multiple investigators can use service simultaneously

**Steps:**
1. Open two terminal windows
2. Terminal 1: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-A"`
3. Terminal 2: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INVESTIGATOR-B"`
4. Terminal 1: Select option `1` (Query recent logs)
5. Terminal 2: Select option `3` (Detect patterns)
6. Both should complete successfully

**Expected Behavior:**
- Both clients connect successfully
- Both show "Investigator ID" in their headers (A and B)
- Both can query API simultaneously without errors
- Service logs show requests from both clients
- Results are independent (no interference)

**Verification:**
- Both terminals display results
- No error messages
- Service continues responding to both

**Status:** [ ] Pass [ ] Fail

---

## Test 10: Client Handles Service Down

**Objective:** Verify graceful error handling when service unavailable

**Steps:**
1. Stop the Trading Service (Ctrl+C in service terminal)
2. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"`

**Expected Output:**
```
Service status: DOWN ✗
Error: Cannot connect to trading service at http://localhost:8080
```

**Verification:**
- Client doesn't crash
- Clear error message displayed
- Client exits cleanly

**Status:** [ ] Pass [ ] Fail

---

## Test 11: Query with No Results

**Objective:** Verify client handles empty results gracefully

**Steps:**
1. Ensure service is running but database is empty (restart service)
2. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"`

**Expected Output:**
```
Found 0 log entries
```

**Verification:**
- No crash or exception
- Clear message about no data
- Client continues to work

**Status:** [ ] Pass [ ] Fail

---

## Test 12: Query with Invalid AccountId

**Objective:** Verify invalid UUID is handled properly

**Steps:**
1. Run interactive client: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient"`
2. Select option: `2` (Query by account)
3. Enter invalid accountId: `invalid-uuid-format`

**Expected Output:**
- Returns 0 results (no match found)
- No crash or exception

**Status:** [ ] Pass [ ] Fail

---

## Summary

**Total Tests:** 12  
**Passed:** ___  
**Failed:** ___

**Notes:**
- All tests should pass with properly configured service and test data
- Tests 1-8 exercise core functionality
- Test 9 demonstrates multiple client requirement
- Tests 10-12 verify error handling

**Date Tested:** ___________  
**Tester:** ___________