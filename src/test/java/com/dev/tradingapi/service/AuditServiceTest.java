package com.dev.tradingapi.service;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.repository.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AuditService.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  @InjectMocks
  private AuditService auditService;

  /**
   * Test logging a request successfully - typical valid case.
   */
  @Test
  void testLogRequest_Success_TypicalCase() {
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);
    when(auditLogRepository.save(any(AuditLog.class))).thenReturn(log);

    auditService.logRequest(log);

    verify(auditLogRepository, times(1)).save(log);
  }

  /**
   * Test logging request handles exception gracefully - invalid case.
   */
  @Test
  void testLogRequest_ExceptionHandled_InvalidCase() {
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);
    when(auditLogRepository.save(any(AuditLog.class)))
            .thenThrow(new RuntimeException("Database error"));

    assertDoesNotThrow(() -> auditService.logRequest(log));
    verify(auditLogRepository, times(1)).save(log);
  }

  /**
   * Test search with no filters - typical valid case.
   */
  @Test
  void testSearch_NoFilters_TypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", "POST", "/orders", 201, 50);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.search(null, null, null, null, null, 0, 10);

    assertNotNull(results);
    assertEquals(2, results.size());
    verify(auditLogRepository, times(1)).findAll(any(Sort.class));
  }

  /**
   * Test search with API key filter - typical valid case.
   */
  @Test
  void testSearch_WithApiKeyFilter_TypicalCase() {
    AuditLog log1 = new AuditLog("test-key", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("other-key", "POST", "/orders", 201, 50);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.search("test-key", null, null, null, null, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("test-key", results.get(0).getApiKey());
  }

  /**
   * Test search with accountId filter - typical valid case.
   */
  @Test
  void testSearch_WithAccountIdFilter_TypicalCase() {
    UUID accountId1 = UUID.randomUUID();
    UUID accountId2 = UUID.randomUUID();

    AuditLog log1 = new AuditLog("key1", accountId1, "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", accountId2, "POST", "/orders", 201, 50);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.search(null, accountId1.toString(), null, null, null, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals(accountId1, results.get(0).getAccountId());
  }

  /**
   * Test search with path filter - atypical valid case.
   */
  @Test
  void testSearch_WithPathFilter_AtypicalCase() {
    AuditLog log1 = new AuditLog("key", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key", "POST", "/orders", 201, 50);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.search(null, null, "/health", null, null, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("/health", results.get(0).getPath());
  }

  /**
   * Test search with time range filter - atypical valid case.
   */
  @Test
  void testSearch_WithTimeRange_AtypicalCase() {
    Instant start = Instant.parse("2025-10-22T00:00:00Z");
    Instant end = Instant.parse("2025-10-22T23:59:59Z");

    AuditLog log1 = new AuditLog("key", "GET", "/health", 200, 10);
    log1.setTs(Instant.parse("2025-10-22T12:00:00Z"));

    AuditLog log2 = new AuditLog("key", "POST", "/orders", 201, 50);
    log2.setTs(Instant.parse("2025-10-21T12:00:00Z"));

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.search(null, null, null, start, end, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
  }

  /**
   * Test search with pagination - atypical valid case.
   */
  @Test
  void testSearch_WithPagination_AtypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", "POST", "/orders", 201, 50);
    AuditLog log3 = new AuditLog("key3", "GET", "/market", 200, 30);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2, log3));

    List<AuditLog> results = auditService.search(null, null, null, null, null, 1, 2);

    assertNotNull(results);
    assertEquals(1, results.size());
  }

  /**
   * Test search with empty result - atypical valid case.
   */
  @Test
  void testSearch_EmptyResult_AtypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1));

    List<AuditLog> results = auditService.search("nonexistent", null, null, null, null, 0, 10);

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  /**
   * Test search with invalid time range - invalid case.
   */
  @Test
  void testSearch_InvalidTimeRange_InvalidCase() {
    Instant start = Instant.parse("2025-10-23T00:00:00Z");
    Instant end = Instant.parse("2025-10-22T00:00:00Z"); // end before start

    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);
    log.setTs(Instant.parse("2025-10-22T12:00:00Z"));

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log));

    List<AuditLog> results = auditService.search(null, null, null, start, end, 0, 10);

    assertNotNull(results);
    assertTrue(results.isEmpty());
  }

  /**
   * Test getAllLogs retrieves all logs in descending order - typical valid case.
   */
  @Test
  void testGetAllLogs_RetrievesAllLogs_TypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", "POST", "/orders", 201, 50);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log1, log2));

    List<AuditLog> results = auditService.getAllLogs();

    assertNotNull(results);
    assertEquals(2, results.size());
    verify(auditLogRepository, times(1)).findAll(any(Sort.class));
  }
}