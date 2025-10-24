package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.AuditLog;
import com.dev.tradingapi.repository.AuditLogRepository;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit tests for AuditService.
 * Tests audit logging and search functionality with mocking.
 */
@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

  @Mock
  private AuditLogRepository auditLogRepository;

  private AuditService auditService;

  /**
   * Set up test fixtures before each test.
   */
  @BeforeEach
  void setUp() {
    auditService = new AuditService(auditLogRepository);
  }

  /**
   * Test logging a valid request - typical valid case.
   */
  @Test
  void testLogRequest_Success_TypicalCase() {
    AuditLog log = new AuditLog("test-key", "GET", "/health", 200, 10);
    when(auditLogRepository.save(any(AuditLog.class))).thenReturn(log);

    auditService.logRequest(log);

    verify(auditLogRepository, times(1)).save(log);
  }

  /**
   * Test logging with null log - invalid case.
   * Service attempts to save but catches the error gracefully.
   */
  @Test
  void testLogRequest_NullLog_InvalidCase() {
    // Service will try to save null, which will throw exception
    // But service catches it and doesn't propagate
    when(auditLogRepository.save(null))
            .thenThrow(new RuntimeException("Cannot save null"));

    auditService.logRequest(null);

    // Verify it tried to save (but failed gracefully)
    verify(auditLogRepository, times(1)).save(null);
  }

  /**
   * Test logging handles repository exception - atypical case.
   * Service should catch exception and not propagate it.
   */
  @Test
  void testLogRequest_RepositoryException_AtypicalCase() {
    AuditLog log = new AuditLog("test-key", "GET", "/health", 200, 10);
    when(auditLogRepository.save(any(AuditLog.class)))
            .thenThrow(new RuntimeException("DB error"));

    // Should not throw exception - logging failures shouldn't break API
    auditService.logRequest(log);

    verify(auditLogRepository, times(1)).save(log);
  }

  /**
   * Test search with no filters - typical valid case.
   */
  @Test
  void testSearch_NoFilters_TypicalCase() {
    AuditLog log1 = new AuditLog("key1", "GET", "/health", 200, 10);
    AuditLog log2 = new AuditLog("key2", "POST", "/orders", 201, 50);
    Page<AuditLog> page = new PageImpl<>(Arrays.asList(log1, log2));

    when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

    List<AuditLog> results = auditService.search(null, null, null, null, 0, 10);

    assertNotNull(results);
    assertEquals(2, results.size());
    verify(auditLogRepository, times(1)).findAll(any(Pageable.class));
  }

  /**
   * Test search with API key filter - typical valid case.
   */
  @Test
  void testSearch_WithApiKeyFilter_TypicalCase() {
    AuditLog log = new AuditLog("test-key", "GET", "/health", 200, 10);
    when(auditLogRepository.findByApiKey("test-key"))
            .thenReturn(Arrays.asList(log));

    List<AuditLog> results = auditService.search("test-key", null, null, null, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
    assertEquals("test-key", results.get(0).getApiKey());
  }

  /**
   * Test search with path filter - atypical valid case.
   */
  @Test
  void testSearch_WithPathFilter_AtypicalCase() {
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);
    when(auditLogRepository.findByPath("/health"))
            .thenReturn(Arrays.asList(log));

    List<AuditLog> results = auditService.search(null, "/health", null, null, 0, 10);

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
    AuditLog log = new AuditLog("key", "GET", "/health", 200, 10);

    when(auditLogRepository.findByTsBetween(start, end))
            .thenReturn(Arrays.asList(log));

    List<AuditLog> results = auditService.search(null, null, start, end, 0, 10);

    assertNotNull(results);
    assertEquals(1, results.size());
  }

  /**
   * Test search with pagination - atypical valid case.
   */
  @Test
  void testSearch_WithPagination_AtypicalCase() {
    Page<AuditLog> page = new PageImpl<>(Arrays.asList());

    when(auditLogRepository.findAll(any(Pageable.class))).thenReturn(page);

    List<AuditLog> results = auditService.search(null, null, null, null, 1, 5);

    assertNotNull(results);
    assertEquals(0, results.size());
  }

  /**
   * Test search with empty result - atypical valid case.
   */
  @Test
  void testSearch_EmptyResult_AtypicalCase() {
    when(auditLogRepository.findByApiKey("nonexistent"))
            .thenReturn(Arrays.asList());

    List<AuditLog> results = auditService.search("nonexistent", null, null, null, 0, 10);

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

    when(auditLogRepository.findByTsBetween(start, end))
            .thenReturn(Arrays.asList());

    List<AuditLog> results = auditService.search(null, null, start, end, 0, 10);

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
    AuditLog log3 = new AuditLog("key3", "GET", "/market/quote/AAPL", 200, 15);

    when(auditLogRepository.findAll(any(Sort.class)))
            .thenReturn(Arrays.asList(log3, log2, log1)); // Newest first

    List<AuditLog> results = auditService.getAllLogs();

    assertNotNull(results);
    assertEquals(3, results.size());
    verify(auditLogRepository, times(1)).findAll(any(Sort.class));
  }
}