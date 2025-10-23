package com.dev.tradingapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.service.AuditService;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for AuditController.
 * Tests the audit logs retrieval endpoint with mocking.
 */
@ExtendWith(MockitoExtension.class)
class AuditControllerTest {

  @Mock
  private AuditService auditService;

  private AuditController auditController;

  /**
   * Set up test fixtures before each test.
   */
  @BeforeEach
  void setUp() {
    auditController = new AuditController(auditService);
  }

  /**
   * Test getting logs with no filters - typical valid case.
   */
  @Test
  void testGetLogs_NoFilters_TypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", "POST", "/orders", 201, 50);
    List<AuditLog> logs = Arrays.asList(log1, log2);

    when(auditService.search(isNull(), isNull(), isNull(), isNull(), eq(0), eq(50)))
            .thenReturn(logs);

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, null, null, null, 0, 50);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, ((List<?>) response.getBody().get("items")).size());
    assertEquals(0, response.getBody().get("page"));
    assertEquals(50, response.getBody().get("pageSize"));
  }

  /**
   * Test getting logs with API key filter - typical valid case.
   */
  @Test
  void testGetLogs_WithApiKeyFilter_TypicalCase() {
    AuditLog log = new AuditLog("test-key", "GET", "/health", 200, 10);
    when(auditService.search(eq("test-key"), isNull(), isNull(), isNull(), eq(0), eq(50)))
            .thenReturn(Arrays.asList(log));

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs("test-key", null, null, null, 0, 50);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    List<?> items = (List<?>) response.getBody().get("items");
    assertEquals(1, items.size());
  }

  /**
   * Test getting logs with path filter - atypical valid case.
   */
  @Test
  void testGetLogs_WithPathFilter_AtypicalCase() {
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);
    when(auditService.search(isNull(), eq("/health"), isNull(), isNull(), eq(0), eq(50)))
            .thenReturn(Arrays.asList(log));

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, "/health", null, null, 0, 50);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    List<?> items = (List<?>) response.getBody().get("items");
    assertEquals(1, items.size());
  }

  /**
   * Test getting logs with date range - atypical valid case.
   */
  @Test
  void testGetLogs_WithDateRange_AtypicalCase() {
    String from = "2025-10-22T00:00:00Z";
    String to = "2025-10-22T23:59:59Z";
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);

    when(auditService.search(isNull(), isNull(), any(Instant.class),
            any(Instant.class), eq(0), eq(50)))
            .thenReturn(Arrays.asList(log));

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, null, from, to, 0, 50);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    List<?> items = (List<?>) response.getBody().get("items");
    assertEquals(1, items.size());
  }

  /**
   * Test getting logs with custom pagination - atypical valid case.
   */
  @Test
  void testGetLogs_CustomPagination_AtypicalCase() {
    when(auditService.search(isNull(), isNull(), isNull(), isNull(), eq(2), eq(10)))
            .thenReturn(Arrays.asList());

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, null, null, null, 2, 10);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(2, response.getBody().get("page"));
    assertEquals(10, response.getBody().get("pageSize"));
  }

  /**
   * Test getting logs with invalid date format - invalid case.
   */
  @Test
  void testGetLogs_InvalidDateFormat_InvalidCase() {
    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, null, "invalid-date", null, 0, 50);

    assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    assertTrue(response.getBody().containsKey("error"));
    assertTrue(response.getBody().containsKey("message"));
  }

  /**
   * Test getting logs returns empty list - atypical valid case.
   */
  @Test
  void testGetLogs_EmptyResult_AtypicalCase() {
    when(auditService.search(any(), any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(Arrays.asList());

    ResponseEntity<Map<String, Object>> response =
            auditController.getLogs(null, null, null, null, 0, 50);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(0, response.getBody().get("total"));
    assertTrue(((List<?>) response.getBody().get("items")).isEmpty());
  }
}