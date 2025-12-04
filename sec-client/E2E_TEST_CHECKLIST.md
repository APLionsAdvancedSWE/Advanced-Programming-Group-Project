# SEC Compliance Client - End-to-End Test Checklist

## Prerequisites
- Service running on `http://localhost:8080`
- Demo data generated (run curl commands to create audit logs)

---

## Test 1: Connection Test
**Objective:** Verify client can connect to service

**Steps:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"
```

**Expected:** Shows "Service status: UP ✓" and fetches audit logs

**Result:** [ ] Pass [ ] Fail

---

## Test 2: Query by AccountId
**Objective:** Filter audit logs by specific account

**Steps:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="TEST-001"
# Select option 2, enter: aaaa1111-1111-1111-1111-111111111111
```

**Expected:** Shows only logs for account aaaa1111

**Result:** [ ] Pass [ ] Fail

---

## Test 3: Pattern Detection
**Objective:** Detect high-frequency trading patterns

**Steps:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestPatternDetection"
```

**Expected:** Flags account aaaa1111 as HIGH_FREQUENCY if 3+ requests exist

**Result:** [ ] Pass [ ] Fail

---

## Test 4: Report Generation
**Objective:** Generate investigation reports

**Steps:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.TestReportGeneration"
```

**Expected:** Creates `sec_investigation_report.txt` and `audit_logs_export.csv`

**Result:** [ ] Pass [ ] Fail

---

## Test 5: Multiple Client Instances (CRITICAL)
**Objective:** Multiple investigators query simultaneously

**Steps:**
1. Terminal 1: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INV-A"`
2. Terminal 2: `mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="INV-B"`
3. Both select option 1 (query logs) at same time

**Expected:** Both receive responses without interference

**Result:** [ ] Pass [ ] Fail

---

## Test 6: Authentication
**Objective:** Verify client authenticates correctly

**Steps:**
```bash
# Check ApiService.java has Basic Auth header
grep "Authorization" src/main/java/com/trading/sec/ApiService.java
```

**Expected:** Shows "Basic " + encoded credentials

**Result:** [ ] Pass [ ] Fail

---

## Test 7: Error Handling - Service Down
**Objective:** Client handles service unavailable

**Steps:**
1. Stop service (Ctrl+C)
2. Run: `mvn exec:java -Dexec.mainClass="com.trading.sec.TestConnection"`

**Expected:** Shows connection error, doesn't crash

**Result:** [ ] Pass [ ] Fail

---

## Test 8: Empty Results
**Objective:** Handle no matching audit logs

**Steps:**
```bash
mvn exec:java -Dexec.mainClass="com.trading.sec.InvestigatorClient" -Dexec.args="TEST"
# Select option 2, enter: 00000000-0000-0000-0000-000000000000
```

**Expected:** Shows "No logs found" or similar

**Result:** [ ] Pass [ ] Fail