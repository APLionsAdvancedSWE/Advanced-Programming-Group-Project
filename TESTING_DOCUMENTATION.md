# Testing Documentation - Equivalence Partitions

## Unit Tests - Audit System

### AuditService.search()
**Parameters:** apiKey, accountId, path, start, end, page, pageSize

**Equivalence Partitions:**
- Valid typical: Non-null filters with valid data
- Valid atypical: Null filters (fetch all), edge cases
- Invalid: Malformed UUIDs, invalid date ranges
- Boundary: Empty results, page 0, large page numbers

**Test Coverage:**
- testSearch_NoFilters - All null (valid typical)
- testSearch_WithApiKeyFilter - apiKey filter (valid typical)
- testSearch_WithAccountIdFilter - UUID filter (valid typical)
- testSearch_WithPathFilter - path filter (valid atypical)
- testSearch_WithTimeRange - date range (valid atypical)
- testSearch_WithPagination - pagination (valid atypical)
- testSearch_EmptyResult - no matches (boundary)

### AuditLoggingFilter.extractAccountId()
**Parameters:** HttpServletRequest, path

**Equivalence Partitions:**
- Valid: UUID in header, path, or query param
- Invalid: Malformed UUID, missing UUID
- Boundary: null values, empty strings

**Test Coverage:**
- testDoFilter_ExtractsAccountIdFromHeader - header extraction (valid)
- testDoFilter_ExtractsAccountIdFromPath - path extraction (valid)
- testDoFilter_ExtractsAccountIdFromQueryParam - query param (valid)
- testDoFilter_InvalidAccountIdFormat - malformed UUID (invalid)
- testDoFilter_NoAccountId - all sources null (boundary)
- testDoFilter_AccountIdPriorityHeaderOverPath - priority order (atypical)

### AuditLog Entity
**Parameters:** apiKey, accountId, method, path, status, latencyMs

**Equivalence Partitions:**
- Valid: Standard HTTP methods, valid status codes, positive latency
- Invalid: null required fields
- Boundary: Status 0, latency 0, MAX_INT values

**Test Coverage:**
Tested through service and integration tests

## API Tests

### GET /audit/logs
**Parameters:** apiKey, accountId, path, from, to, page, pageSize, Authentication

**Equivalence Partitions:**
- Valid typical: Valid filters with authentication
- Valid atypical: No filters, large page numbers
- Invalid: Invalid ISO dates, wrong credentials, no auth
- Boundary: Empty results, page 0

**Test Coverage:**
- testGetLogs_NoFilters - defaults (valid typical)
- testGetLogs_WithApiKeyFilter - filter by key (valid typical)
- testGetLogs_WithAccountIdFilter - filter by account (valid typical)
- testGetLogs_WithPathFilter - filter by path (valid atypical)
- testGetLogs_WithDateRange - date filtering (valid atypical)
- testGetLogs_CustomPagination - pagination (valid atypical)
- testGetLogs_EmptyResult - no matches (boundary)
- testGetLogs_UnauthorizedAccess - no auth (invalid)

### GET /health
**Parameters:** X-Account-Id header (optional)

**Equivalence Partitions:**
- Valid: With or without accountId header
- Invalid: N/A (public endpoint)

**Test Coverage:**
- testHealth_NoHeader - no accountId (valid typical)
- testHealth_WithAccountId - with accountId (valid atypical)

## Integration Tests

### AuditIntegrationTest
**What's Being Integrated:**

1. **testFilterToServiceToDatabaseIntegration**
    - Components: AuditLoggingFilter → AuditService → AuditLogRepository → H2 Database
    - Tests: Request capture and persistence flow
    - Validates: End-to-end audit logging

2. **testControllerToServiceToRepositoryIntegration**
    - Components: AuditController → AuditService → AuditLogRepository
    - Tests: API query to database retrieval
    - Validates: Audit log retrieval works

3. **testSecurityIntegrationBlocksUnauthorizedAccess**
    - Components: Spring Security → DatabaseUserDetailsService → UserRepository
    - Tests: Authentication layer
    - Validates: 401 for anonymous users

4. **testSecurityIntegrationBlocksTraderRole**
    - Components: Spring Security → Authorization matrix
    - Tests: Role-based access control
    - Validates: 403 for insufficient permissions

**Note:** Integration tests temporarily excluded from CI pending deployment configuration. Tests pass locally and are fully documented.

## Test Execution

Run all tests:
```
mvn clean test
```

Generate coverage:
```
mvn jacoco:report
```

Coverage reports: `target/site/jacoco/index.html`

## Coverage Summary

- Unit Tests: 105 tests
- Integration Tests: 4 tests (run manually)
- Branch Coverage: 91% (audit components)