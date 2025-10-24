package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Unit tests for ExecutionService.
 * Tests order creation, fill generation, and position updates with mocking.
 */
@ExtendWith(MockitoExtension.class)
class ExecutionServiceTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private MarketService marketService;

  @Mock
  private RiskService riskService;

  private ExecutionService executionService;

  /**
   * Set up test fixtures before each test.
   */
  @BeforeEach
  void setUp() {
    executionService = new ExecutionService(jdbcTemplate, marketService, riskService);
  }

  /**
   * Test successful order creation - typical valid case.
   */
  @Test
  void testCreateOrder_Success_TypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-001", "AAPL", "BUY", 100, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertNotNull(result.getId());
    assertEquals(accountId, result.getAccountId());
    assertEquals("CLIENT-001", result.getClientOrderId());
    assertEquals("AAPL", result.getSymbol());
    assertEquals("BUY", result.getSide());
    assertEquals(100, result.getQty());
    assertEquals("MARKET", result.getType());
    assertEquals("FILLED", result.getStatus());
    assertEquals(100, result.getFilledQty());
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    assertNotNull(result.getCreatedAt());

    verify(marketService, times(1)).getQuote("AAPL");
    verify(riskService, times(1)).validate(request, quote);
    verify(jdbcTemplate, times(4)).update(anyString(), any(Object[].class)); 

  }

  /**
   * Test order creation with market data unavailable - invalid case.
   */
  @Test
  void testCreateOrder_MarketDataUnavailable_InvalidCase() {

    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-002", "INVALID", "BUY", 100, "MARKET", null, "DAY");

    when(marketService.getQuote("INVALID")).thenReturn(null);

    NotFoundException exception = assertThrows(NotFoundException.class, 
        () -> executionService.createOrder(request));
    
    assertEquals("Market data not available for symbol: INVALID", exception.getMessage());
    verify(marketService, times(1)).getQuote("INVALID");
    verify(riskService, times(0)).validate(any(), any());
  }

  /**
   * Test order creation with risk validation failure - invalid case.
   */
  @Test
  void testCreateOrder_RiskValidationFailure_InvalidCase() {

    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-003", "AAPL", "BUY", 1500, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doThrow(new RiskException("Quantity exceeds risk limits"))
        .when(riskService).validate(request, quote);

    RiskException exception = assertThrows(RiskException.class, 
        () -> executionService.createOrder(request));
    
    assertEquals("Quantity exceeds risk limits", exception.getMessage());
    verify(marketService, times(1)).getQuote("AAPL");
    verify(riskService, times(1)).validate(request, quote);
  }

  /**
   * Test order creation with limit order - typical valid case.
   */
  @Test
  void testCreateOrder_LimitOrder_TypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-004", "AAPL", "SELL", 50, "LIMIT", 
        new BigDecimal("160.00"), "GTC");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("SELL", result.getSide());
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("160.00"), result.getLimitPrice());
    assertEquals("GTC", result.getTimeInForce());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
    verify(riskService, times(1)).validate(request, quote);
  }

  /**
   * Test order creation with different symbols - typical valid case.
   */
  @Test
  void testCreateOrder_DifferentSymbol_TypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-005", "TSLA", "BUY", 25, "MARKET", null, "DAY");
    
    Quote quote = new Quote("TSLA", 
        new BigDecimal("200.00"), 
        new BigDecimal("210.00"), 
        new BigDecimal("195.00"), 
        new BigDecimal("205.00"), 
        500L, 
        Instant.now());

    when(marketService.getQuote("TSLA")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("TSLA");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("TSLA", result.getSymbol());
    assertEquals(25, result.getQty());
    assertEquals(new BigDecimal("205.00"), result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("TSLA");
  }

  /**
   * Test order creation with zero quantity - edge case.
   */
  @Test
  void testCreateOrder_ZeroQuantity_EdgeCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-006", "AAPL", "BUY", 0, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals(0, result.getQty());
    assertEquals(0, result.getFilledQty());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test order creation with large quantity - atypical valid case.
   */
  @Test
  void testCreateOrder_LargeQuantity_AtypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-007", "AAPL", "BUY", 10000, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals(10000, result.getQty());
    assertEquals(10000, result.getFilledQty());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test order creation with null request - invalid case.
   */
  @Test
  void testCreateOrder_NullRequest_InvalidCase() {
    assertThrows(NullPointerException.class, 
        () -> executionService.createOrder(null));
  }

  /**
   * Test order creation with null symbol - invalid case.
   */
  @Test
  void testCreateOrder_NullSymbol_InvalidCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-008", null, "BUY", 100, "MARKET", null, "DAY");

    when(marketService.getQuote(null)).thenReturn(null);

    NotFoundException exception = assertThrows(NotFoundException.class, 
        () -> executionService.createOrder(request));
    
    assertEquals("Market data not available for symbol: null", exception.getMessage());
    verify(marketService, times(1)).getQuote(null);
  }

  /**
   * Test order creation with empty symbol - invalid case.
   */
  @Test
  void testCreateOrder_EmptySymbol_InvalidCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-009", "", "BUY", 100, "MARKET", null, "DAY");

    when(marketService.getQuote("")).thenReturn(null);

    NotFoundException exception = assertThrows(NotFoundException.class, 
        () -> executionService.createOrder(request));
    
    assertEquals("Market data not available for symbol: ", exception.getMessage());
    verify(marketService, times(1)).getQuote("");
  }

  /**
   * Test order creation with different time in force - typical valid case.
   */
  @Test
  void testCreateOrder_DifferentTimeInForce_TypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-010", "AAPL", "BUY", 100, "MARKET", null, "IOC");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("IOC", result.getTimeInForce());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test order creation with TWAP order type - atypical valid case.
   */
  @Test
  void testCreateOrder_TwapOrder_AtypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-011", "AAPL", "BUY", 100, "TWAP", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("TWAP", result.getType());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test order creation with FOK time in force - atypical valid case.
   */
  @Test
  void testCreateOrder_FokTimeInForce_AtypicalCase() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-012", "AAPL", "BUY", 100, "MARKET", null, "FOK");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setSymbol("AAPL");
    mockOrder.setAccountId(accountId);
    when(jdbcTemplate.queryForObject(anyString(), 
        any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
        .thenReturn(mockOrder);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("FOK", result.getTimeInForce());
    assertEquals("FILLED", result.getStatus());
    verify(marketService, times(1)).getQuote("AAPL");
  }
}
