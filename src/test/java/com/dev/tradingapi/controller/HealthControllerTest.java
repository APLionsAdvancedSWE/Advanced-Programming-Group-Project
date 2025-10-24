package com.dev.tradingapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.service.AuditService;
import com.dev.tradingapi.service.MarketService;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for HealthController.
 * Tests the comprehensive health check endpoint.
 */
class HealthControllerTest {

  private HealthController healthController;
  private DataSource dataSource;
  private AuditService auditService;
  private MarketService marketService;

  /**
   * Set up test fixtures before each test.
   */
  @BeforeEach
  void setUp() throws Exception {
    dataSource = mock(DataSource.class);
    auditService = mock(AuditService.class);
    marketService = mock(MarketService.class);

    // Mock successful connections
    Connection mockConnection = mock(Connection.class);
    when(dataSource.getConnection()).thenReturn(mockConnection);
    when(auditService.getAllLogs()).thenReturn(new ArrayList<>());
    when(marketService.getQuote(anyString()))
            .thenReturn(new Quote("AAPL", BigDecimal.TEN, BigDecimal.TEN,
                    BigDecimal.TEN, BigDecimal.TEN, 100L, Instant.now()));

    healthController = new HealthController(dataSource, auditService, marketService);
  }

  /**
   * Test health endpoint returns OK status - typical valid case.
   */
  @Test
  void testHealth_AllComponentsHealthy_TypicalCase() {
    ResponseEntity<Map<String, Object>> response = healthController.health();

    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("ok", response.getBody().get("status"));
  }

  /**
   * Test health endpoint response structure - atypical valid case.
   */
  @Test
  void testHealth_ResponseStructure_AtypicalCase() {
    ResponseEntity<Map<String, Object>> response = healthController.health();

    assertNotNull(response.getBody());
    assertTrue(response.getBody().containsKey("status"));
    assertTrue(response.getBody().containsKey("timestamp"));
    assertTrue(response.getBody().containsKey("components"));
    assertTrue(response.getBody().containsKey("version"));
  }

  /**
   * Test health endpoint components check - atypical valid case.
   */
  @Test
  void testHealth_ComponentsHealthy_AtypicalCase() {
    ResponseEntity<Map<String, Object>> response = healthController.health();

    assertNotNull(response.getBody());
    @SuppressWarnings("unchecked")
    Map<String, String> components =
            (Map<String, String>) response.getBody().get("components");

    assertEquals("healthy", components.get("database"));
    assertEquals("healthy", components.get("auditLogging"));
    assertEquals("healthy", components.get("marketData"));
  }

  /**
   * Test health endpoint with database failure - invalid case.
   */
  @Test
  void testHealth_DatabaseUnhealthy_InvalidCase() throws Exception {
    when(dataSource.getConnection()).thenThrow(new RuntimeException("DB error"));

    ResponseEntity<Map<String, Object>> response = healthController.health();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("degraded", response.getBody().get("status"));

    @SuppressWarnings("unchecked")
    Map<String, String> components =
            (Map<String, String>) response.getBody().get("components");
    assertEquals("unhealthy", components.get("database"));
  }

  /**
   * Test health endpoint with audit service failure - invalid case.
   */
  @Test
  void testHealth_AuditServiceUnhealthy_InvalidCase() {
    when(auditService.getAllLogs()).thenThrow(new RuntimeException("Audit error"));

    ResponseEntity<Map<String, Object>> response = healthController.health();

    assertEquals("degraded", response.getBody().get("status"));

    @SuppressWarnings("unchecked")
    Map<String, String> components =
            (Map<String, String>) response.getBody().get("components");
    assertEquals("unhealthy", components.get("auditLogging"));
  }
}