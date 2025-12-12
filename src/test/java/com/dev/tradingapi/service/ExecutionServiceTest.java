package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.repository.FillRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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

  @Mock
  private FillRepository fillRepository;

  private ExecutionService executionService;

  /**
   * Set up test fixtures before each test.
   */
  @BeforeEach
  void setUp() {
    executionService = new ExecutionService(jdbcTemplate, marketService, riskService,
        fillRepository);
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
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
    
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
    // 1 saveOrder + 1 savePosition + 1 updateOrder = 3 total (fill save is now in FillRepository)
    verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
    verify(fillRepository, times(1)).save(any(com.dev.tradingapi.model.Fill.class)); 

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
        new BigDecimal("150.00"), "GTC"); // Limit $150 <= market $152, should fill
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100000L, // High volume for liquidity
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
    assertEquals(new BigDecimal("150.00"), result.getLimitPrice());
    assertEquals("GTC", result.getTimeInForce());
    // Should fill (limit price <= market price) - either FILLED or PARTIALLY_FILLED
    assertTrue("FILLED".equals(result.getStatus())
            || "PARTIALLY_FILLED".equals(result.getStatus()));
    assertTrue(result.getFilledQty() > 0);
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

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals(0, result.getQty());
    assertEquals(0, result.getFilledQty());
    assertEquals("WORKING",
            result.getStatus()); // Zero quantity = no fill, status should be WORKING
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
   * Test order creation with TWAP order type - full fill with sufficient liquidity.
   * TWAP should create multiple slices and fill completely.
   */
  @Test
  void testCreateOrder_TwapOrder_FullFill() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-011", "AAPL", "BUY", 100, "TWAP", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100000L, // High volume for sufficient liquidity
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    assertEquals(100, result.getFilledQty());
    assertEquals(100, result.getQty());
    // TWAP should use market price (152.00), not limit price
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    // Should create multiple fills (slices) - for 100 shares, 10 slices of 10 each
    verify(fillRepository, times(10)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test TWAP order with partial fill due to insufficient liquidity.
   * When liquidity is exhausted mid-execution, should result in PARTIALLY_FILLED.
   * Order of 1000 shares with low liquidity (50 per slice) should only fill partially.
   */
  @Test
  void testCreateOrder_TwapOrder_PartialFill_InsufficientLiquidity() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-017", "AAPL", "BUY", 1000, "TWAP", null, "DAY");
    
    // Low volume means limited liquidity per slice (min 50, max 10000)
    // With volume 100, liquidity = max(50, min(10, 10000)) = 50 per slice
    // TWAP splits 1000 into 10 slices of 100 each
    // Each slice needs 100, but only 50 available, so partial fill
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100L, // Very low volume - liquidity will be 50 per slice (min liquidity)
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    assertEquals("PARTIALLY_FILLED", result.getStatus());
    assertTrue(result.getFilledQty() > 0);
    assertTrue(result.getFilledQty() < result.getQty()); // Partial fill
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    // Should create some fills but not all slices due to liquidity
    // First slice fills 50 (out of 100 needed), then stops
    verify(fillRepository, times(1))
        .save(any(com.dev.tradingapi.model.Fill.class));
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test TWAP order uses market price, not limit price.
   * Even if limit price is provided, TWAP should use market price.
   */
  @Test
  void testCreateOrder_TwapOrder_UsesMarketPrice_NotLimitPrice() {
    UUID accountId = UUID.randomUUID();
    // TWAP with limit price - should still use market price
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-018", "AAPL", "BUY", 50, "TWAP", 
        new BigDecimal("140.00"), "DAY"); // Limit price provided but should be ignored
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), // Market price
        100000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    // Should use market price (152.00), not limit price (140.00)
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test TWAP order with small quantity - should still create multiple slices.
   * For qty=10, should create 10 slices of 1 each.
   */
  @Test
  void testCreateOrder_TwapOrder_SmallQuantity_MultipleSlices() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-019", "AAPL", "BUY", 10, "TWAP", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    assertEquals(10, result.getFilledQty());
    // Should create 10 slices (one per share)
    verify(fillRepository, times(10)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test TWAP order with large quantity - should cap at 10 slices.
   * For qty=1000, should create 10 slices of 100 each.
   */
  @Test
  void testCreateOrder_TwapOrder_LargeQuantity_CappedSlices() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-020", "AAPL", "BUY", 1000, "TWAP", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000000L, // Very high volume for sufficient liquidity
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    assertEquals(1000, result.getFilledQty());
    // Should create exactly 10 slices (capped at 10), each of 100 shares
    verify(fillRepository, times(10)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test TWAP order with quantity that doesn't divide evenly.
   * For qty=105, should create 10 slices with remainder distributed.
   */
  @Test
  void testCreateOrder_TwapOrder_UnevenQuantity_RemainderDistribution() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-021", "AAPL", "BUY", 105, "TWAP", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
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
    assertEquals(105, result.getFilledQty());
    // Should create 10 slices: 5 slices of 11, 5 slices of 10 (105 = 10*10 + 5)
    verify(fillRepository, times(10)).save(any(com.dev.tradingapi.model.Fill.class));
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

  /**
   * Test LIMIT BUY order with price too low - should not fill.
   * When limit price < current price, order should remain WORKING.
   */
  @Test
  void testCreateOrder_LimitBuyPriceTooLow_NoFill() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-013", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("145.00"), "GTC"); // Limit $145, market $152
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), // Current price $152
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("145.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill, status should be WORKING
    assertEquals(0, result.getFilledQty()); // No fills
    verify(marketService, times(1)).getQuote("AAPL");
    // Should save order 3 times: initial insert + position update + status update
    verify(jdbcTemplate, times(3)).update(anyString(), any(Object[].class));
  }

  /**
   * Test LIMIT BUY order with acceptable price - should fill.
   * When limit price >= current price, order should fill.
   */
  @Test
  void testCreateOrder_LimitBuyPriceAcceptable_Fills() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-014", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("155.00"), "GTC"); // Limit $155, market $152
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100000L, // High volume for liquidity
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
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("155.00"), result.getLimitPrice());
    // Should fill (limit price >= market price)
    assertTrue("FILLED".equals(result.getStatus())
            || "PARTIALLY_FILLED".equals(result.getStatus()));
    assertTrue(result.getFilledQty() > 0); // Should have some fills
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test LIMIT SELL order with price too high - should not fill.
   * When limit price > current price, order should remain WORKING.
   */
  @Test
  void testCreateOrder_LimitSellPriceTooHigh_NoFill() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-015", "AAPL", "SELL", 100, "LIMIT", 
        new BigDecimal("160.00"), "GTC"); // Limit $160, market $152
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), // Current price $152
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(request, quote);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals("SELL", result.getSide());
    assertEquals(new BigDecimal("160.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill
    assertEquals(0, result.getFilledQty());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test MARKET order with insufficient liquidity - partial fill.
   * When available liquidity < order quantity, should partially fill.
   */
  @Test
  void testCreateOrder_MarketOrderInsufficientLiquidity_PartialFill() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-016", "AAPL", "BUY", 1000, "MARKET", null, "DAY");
    
    // Low volume (100) means ~10 shares available (10% of volume, but min 50)
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        100L, // Very low volume
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
    assertEquals("MARKET", result.getType());
    assertEquals(1000, result.getQty()); // Requested 1000
    assertEquals("PARTIALLY_FILLED", result.getStatus()); // Should be partial
    assertTrue(result.getFilledQty() > 0); // Some fills
    assertTrue(result.getFilledQty() < result.getQty()); // But not all
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test updateOrderStatus with partial fill - PARTIALLY_FILLED status.
   * Order with qty 100, one fill of 40, should result in PARTIALLY_FILLED.
   */
  @Test
  void testUpdateOrderStatus_PartialFill_PartiallyFilled() throws Exception {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setQty(100);
    order.setStatus("NEW");
    order.setFilledQty(0);

    List<Fill> fills = List.of(
        new Fill(UUID.randomUUID(), order.getId(), 40, 
            new BigDecimal("150.00"), Instant.now())
    );

    // Use reflection to access private updateOrderStatus method
    Method updateOrderStatus = ExecutionService.class.getDeclaredMethod(
        "updateOrderStatus", Order.class, List.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    // Invoke the private method
    updateOrderStatus.invoke(executionService, order, fills);

    assertEquals("PARTIALLY_FILLED", order.getStatus());
    assertEquals(40, order.getFilledQty());
    assertEquals(new BigDecimal("150.00"), order.getAvgFillPrice());
    verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
  }

  /**
   * Test updateOrderStatus with full fill - FILLED status.
   * Order with qty 100, two fills totaling 100 (60 + 40), should result in FILLED.
   */
  @Test
  void testUpdateOrderStatus_FullFill_Filled() throws Exception {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setQty(100);
    order.setStatus("NEW");
    order.setFilledQty(0);

    List<Fill> fills = List.of(
        new Fill(UUID.randomUUID(), order.getId(), 60, 
            new BigDecimal("150.00"), Instant.now()),
        new Fill(UUID.randomUUID(), order.getId(), 40, 
            new BigDecimal("151.00"), Instant.now())
    );

    // Use reflection to access private updateOrderStatus method
    Method updateOrderStatus = ExecutionService.class.getDeclaredMethod(
        "updateOrderStatus", Order.class, List.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    // Invoke the private method
    updateOrderStatus.invoke(executionService, order, fills);

    assertEquals("FILLED", order.getStatus());
    assertEquals(100, order.getFilledQty());
    // Average fill price: (60 * 150.00 + 40 * 151.00) / 100 = 150.40
    BigDecimal expectedAvgPrice = new BigDecimal("150.40");
    assertEquals(expectedAvgPrice, order.getAvgFillPrice());
    verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
  }

  /**
   * Test updateOrderStatus with no fills - WORKING status.
   * Order with qty 100, no fills, should result in WORKING.
   */
  @Test
  void testUpdateOrderStatus_NoFills_Working() throws Exception {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setQty(100);
    order.setStatus("NEW");
    order.setFilledQty(0);

    List<Fill> fills = List.of();

    // Use reflection to access private updateOrderStatus method
    Method updateOrderStatus = ExecutionService.class.getDeclaredMethod(
        "updateOrderStatus", Order.class, List.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    // Invoke the private method
    updateOrderStatus.invoke(executionService, order, fills);

    assertEquals("WORKING", order.getStatus());
    assertEquals(0, order.getFilledQty());
    assertEquals(null, order.getAvgFillPrice());
    verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
  }

  /**
   * Test updateOrderStatus with overfill - should throw IllegalStateException.
   * Order with qty 100, fills totaling 110, should throw exception.
   */
  @Test
  void testUpdateOrderStatus_Overfill_ThrowsException() throws Exception {
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setQty(100);
    order.setStatus("NEW");
    order.setFilledQty(0);

    List<Fill> fills = List.of(
        new Fill(UUID.randomUUID(), order.getId(), 60, 
            new BigDecimal("150.00"), Instant.now()),
        new Fill(UUID.randomUUID(), order.getId(), 50, 
            new BigDecimal("151.00"), Instant.now())
    );

    // Use reflection to access private updateOrderStatus method
    Method updateOrderStatus = ExecutionService.class.getDeclaredMethod(
        "updateOrderStatus", Order.class, List.class);
    updateOrderStatus.setAccessible(true);

    // Invoke the private method - should throw IllegalStateException
    Exception exception = assertThrows(Exception.class, 
        () -> updateOrderStatus.invoke(executionService, order, fills));
    
    // The exception is wrapped in InvocationTargetException, get the cause
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertEquals("Order overfilled", exception.getCause().getMessage());
  }
}
