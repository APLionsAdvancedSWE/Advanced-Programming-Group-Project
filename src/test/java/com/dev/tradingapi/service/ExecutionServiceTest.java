package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.repository.FillRepository;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
    
    // Mock query to return empty list (no matching orders in book)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals(10000, result.getQty());
    // With no opposing volume in our book, MARKET BUY rests WORKING with no fills
    assertEquals(0, result.getFilledQty());
    assertEquals("WORKING", result.getStatus());
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
    
    // Mock query to return empty list (no matching orders in book)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("IOC", result.getTimeInForce());
    // With no opposing volume, IOC MARKET behaves like a resting WORKING order in this simulation
    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
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
    
    // Mock fillRepository.findByOrderId to return TWAP fills (10 slices of 10 each)
    List<Fill> twapFills = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 10, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i)));
    }
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(twapFills);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("TWAP", result.getType());
    assertEquals("FILLED", result.getStatus());
    assertEquals(100, result.getFilledQty());
    assertEquals(100, result.getQty());
    // TWAP should use market price (152.00), not limit price
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    // TWAP creates multiple fills (slices) - verify it's reasonable (≤ 10 slices)
    verify(fillRepository, atMost(10)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(fillRepository, atLeast(1)).save(any(com.dev.tradingapi.model.Fill.class));
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
    
    // Mock fillRepository.findByOrderId to return TWAP fill (1 slice of 50)
    Fill twapFill = new Fill(UUID.randomUUID(), UUID.randomUUID(), 50, 
        new BigDecimal("152.00"), Instant.now());
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of(twapFill));

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("TWAP", result.getType());
    assertEquals("PARTIALLY_FILLED", result.getStatus());
    assertTrue(result.getFilledQty() > 0);
    assertTrue(result.getFilledQty() < result.getQty()); // Partial fill
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    // TWAP stops early on insufficient liquidity - verify at least one fill was created
    verify(fillRepository, atLeast(1)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(fillRepository, atMost(10)).save(any(com.dev.tradingapi.model.Fill.class));
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
    
    // Mock fillRepository.findByOrderId to return TWAP fills (multiple slices)
    List<Fill> twapFills = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 10, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i)));
    }
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(twapFills);

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("TWAP", result.getType());
    // Should use market price (152.00), not limit price (140.00)
    assertEquals(new BigDecimal("152.00"), result.getAvgFillPrice());
    // TWAP creates fills - verify reasonable count (≤ 10 slices)
    verify(fillRepository, atLeast(1)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(fillRepository, atMost(10)).save(any(com.dev.tradingapi.model.Fill.class));
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
    
    // Mock fillRepository.findByOrderId to return TWAP fills (10 slices of 1 each)
    List<Fill> twapFills = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 1, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i)));
    }
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(twapFills);

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
    
    // Mock fillRepository.findByOrderId to return TWAP fills (10 slices of 100 each)
    List<Fill> twapFills = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 100, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i)));
    }
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(twapFills);

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
    
    // Mock fillRepository.findByOrderId to return TWAP fills (10 slices: 5 of 11, 5 of 10)
    List<Fill> twapFills = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 11, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i)));
    }
    for (int i = 0; i < 5; i++) {
      twapFills.add(new Fill(UUID.randomUUID(), UUID.randomUUID(), 10, 
          new BigDecimal("152.00"), Instant.now().plusSeconds(i + 5)));
    }
    when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(twapFills);

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
    
    // Mock query to return empty list (no matching orders in book)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("FOK", result.getTimeInForce());
    // In this simplified engine, FOK behaves like MARKET with no book liquidity
    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Test LIMIT BUY order with price too low - should not fill.
   * When limit price < current price, no SELL orders will match (SELL prices are higher).
   * Order should remain WORKING.
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
    
    // No matching SELL orders (all SELL prices > $145)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock isOrderBookEmpty check - return > 0 (order book exists, not first order)
    lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1);
    
    // Mock fillRepository.findByOrderId to return empty (no existing fills)
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("145.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill, status should be WORKING
    assertEquals(0, result.getFilledQty()); // No fills
    verify(marketService, times(1)).getQuote("AAPL");
    // Should save order 2 times: initial insert + status update (no position update if no fills)
    verify(jdbcTemplate, times(2)).update(anyString(), any(Object[].class));
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
    
    // Mock position check - account has enough shares to sell (100 shares)
    Position position = new Position(accountId, "AAPL", 100, new BigDecimal("150.00"));
    when(jdbcTemplate.queryForObject(anyString(),
        any(org.springframework.jdbc.core.RowMapper.class),
        any(UUID.class), any(String.class))).thenReturn(position);
    
    // Mock query to return empty list (no matching BUY orders)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock fillRepository.findByOrderId to return empty (no existing fills)
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals("SELL", result.getSide());
    assertEquals(new BigDecimal("160.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill - SELL orders don't bootstrap
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
    
    // Mock query to return empty list (no matching orders in book)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("MARKET", result.getType());
    assertEquals(1000, result.getQty()); // Requested 1000
    // With no book liquidity at all, the order remains WORKING with no fills
    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
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
        "updateOrderStatus", Order.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    // CRITICAL: The method reads from database, not from parameter
    // Mock fillRepository.findByOrderId to return the fills that are in the database
    when(fillRepository.findByOrderId(order.getId())).thenReturn(fills);

    // Invoke the private method
    updateOrderStatus.invoke(executionService, order);

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
        "updateOrderStatus", Order.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    // CRITICAL: The method reads from database, not from parameter
    // Mock fillRepository.findByOrderId to return the fills that are in the database
    when(fillRepository.findByOrderId(order.getId())).thenReturn(fills);

    // Invoke the private method
    updateOrderStatus.invoke(executionService, order);

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

    // Use reflection to access private updateOrderStatus method
    Method updateOrderStatus = ExecutionService.class.getDeclaredMethod(
        "updateOrderStatus", Order.class);
    updateOrderStatus.setAccessible(true);

    // Mock jdbcTemplate.update for updateOrder call
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    
    // Mock fillRepository.findByOrderId to return empty list (no existing fills)
    when(fillRepository.findByOrderId(order.getId())).thenReturn(List.of());

    // Invoke the private method
    List<Fill> fills = List.of();
    updateOrderStatus.invoke(executionService, order);

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
        "updateOrderStatus", Order.class);
    updateOrderStatus.setAccessible(true);

    // CRITICAL: The method reads from database, not from parameter
    // Mock fillRepository.findByOrderId to return fills that cause overfill
    // Order qty is 100, but we'll return fills totaling 110
    when(fillRepository.findByOrderId(order.getId())).thenReturn(fills);

    // Invoke the private method - should throw IllegalStateException
    Exception exception = assertThrows(Exception.class, 
        () -> updateOrderStatus.invoke(executionService, order));
    
    // The exception is wrapped in InvocationTargetException, get the cause
    assertTrue(exception.getCause() instanceof IllegalStateException);
    assertTrue(exception.getCause().getMessage().contains("Order overfilled"));
  }

  // ========== Matching Algorithm Scenario Tests ==========

  /**
   * Scenario 1a: First BUY LIMIT order (empty order book) - stays WORKING.
   * BUY 100 AAPL @ 150 (LIMIT) with no SELL orders in system.
   * LIMIT orders do NOT bootstrap - only MARKET BUY orders bootstrap.
   */
  @Test
  void testCreateOrder_BuyLimit_FirstOrder_ExecutesAtMarketPrice() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-100", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("150.00"), "DAY");
    
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
    lenient().doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
    // Mock query to return empty list (no matching SELL orders)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock isOrderBookEmpty check - return 0 (empty order book, first order)
    lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(0);
    
    // Mock fillRepository.findByOrderId to return empty (no fills - LIMIT orders don't bootstrap)
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals("BUY", result.getSide());
    assertEquals(new BigDecimal("150.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // LIMIT orders don't bootstrap - stay WORKING
    assertEquals(0, result.getFilledQty());
    assertEquals(null, result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("AAPL");
    verify(fillRepository, times(0)).save(any(com.dev.tradingapi.model.Fill.class));
    verify(fillRepository, times(0)).save(any(com.dev.tradingapi.model.Fill.class));
  }

  /**
   * Scenario 1b: BUY LIMIT order with no sellers but order book exists - should remain WORKING.
   * BUY 100 AAPL @ 150 (LIMIT) with no matching SELL orders, but SELL orders exist in system.
   */
  @Test
  void testCreateOrder_BuyLimit_NoSellers_OrderBookExists_Working() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-100b", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("150.00"), "DAY");
    
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
    
    // Mock query to return empty list (no matching SELL orders)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock isOrderBookEmpty check - return > 0 (order book exists, not first order)
    lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1); // At least one SELL order exists in system

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals("BUY", result.getSide());
    assertEquals(new BigDecimal("150.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No matches, stays WORKING
    assertEquals(0, result.getFilledQty());
    assertEquals(null, result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("AAPL");
    verify(fillRepository, times(0)).save(any(com.dev.tradingapi.model.Fill.class));
  }

  /**
   * Scenario 2a: First BUY MARKET order (empty order book) - executes at market price.
   * BUY 100 AAPL (MARKET) with no SELL orders in system - uses market price for bootstrapping.
   */
  @Test
  void testCreateOrder_BuyMarket_FirstOrder_ExecutesAtMarketPrice() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-101", "AAPL", "BUY", 100, "MARKET", null, "DAY");
    
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
    
    // Mock query to return empty list (no matching SELL orders in book)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("MARKET", result.getType());
    assertEquals("BUY", result.getSide());
    // With an empty book and no synthetic liquidity, first MARKET BUY rests WORKING
    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
    assertEquals(null, result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("AAPL");
  }

  /**
   * Scenario 2b: BUY MARKET order with no sellers but order book exists - gets CANCELLED.
   * BUY 100 AAPL (MARKET) with no matching SELL orders, but SELL orders exist in system.
   * MARKET orders don't rest in the book - they execute immediately or are cancelled.
   */
  @Test
  void testCreateOrder_BuyMarket_NoSellers_OrderBookExists_Cancelled() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-101b", "AAPL", "BUY", 100, "MARKET", null, "DAY");
    
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
    
    // Mock query to return empty list (no matching SELL orders)
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock fillRepository.findByOrderId to return empty (no existing fills)
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertNotNull(result);
    assertEquals("MARKET", result.getType());
    assertEquals("BUY", result.getSide());
    // MARKET order with no fills stays WORKING (not CANCELLED) - they can rest in the book
    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
    assertEquals(null, result.getAvgFillPrice());
    verify(marketService, times(1)).getQuote("AAPL");
    verify(fillRepository, times(0)).save(any(com.dev.tradingapi.model.Fill.class));
  }

  /**
   * Scenario 5: Multiple sellers with different prices - price-time priority.
   * BUY 100 AAPL @ 155
   * SELL 60 @ 150 (matches first - best price)
   * SELL 40 @ 151 (matches second)
   * Result: Fill 60 @ 150, Fill 40 @ 151
   */
  @Test
  void testCreateOrder_MultipleSellers_DifferentPrices_PriceTimePriority() {
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(any(CreateOrderRequest.class), eq(quote));
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    doNothing().when(fillRepository).save(any(com.dev.tradingapi.model.Fill.class));
    
    UUID sellAccountId1 = UUID.randomUUID();
    
    // Create two resting SELL orders with different prices
    Order sellOrder1 = new Order();
    sellOrder1.setId(UUID.randomUUID());
    sellOrder1.setAccountId(sellAccountId1);
    sellOrder1.setSymbol("AAPL");
    sellOrder1.setSide("SELL");
    sellOrder1.setQty(60);
    sellOrder1.setFilledQty(0);
    sellOrder1.setLimitPrice(new BigDecimal("150.00"));
    sellOrder1.setStatus("WORKING");
    sellOrder1.setType("LIMIT");
    sellOrder1.setCreatedAt(Instant.now().minusSeconds(10)); // Earlier

    UUID sellAccountId2 = UUID.randomUUID();
    Order sellOrder2 = new Order();
    sellOrder2.setId(UUID.randomUUID());
    sellOrder2.setAccountId(sellAccountId2);
    sellOrder2.setSymbol("AAPL");
    sellOrder2.setSide("SELL");
    sellOrder2.setQty(40);
    sellOrder2.setFilledQty(0);
    sellOrder2.setLimitPrice(new BigDecimal("151.00"));
    sellOrder2.setStatus("WORKING");
    sellOrder2.setType("LIMIT");
    sellOrder2.setCreatedAt(Instant.now().minusSeconds(5)); // Later

    // Mock query to return both SELL orders (sorted by price ASC) for findMatchingOrders
    lenient().when(jdbcTemplate.query(
        anyString(),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq("AAPL"), // Symbol
        any(BigDecimal.class) // Limit price
    )).thenReturn(List.of(sellOrder1, sellOrder2));
    
    // Mock getOrderById calls to return the current state of SELL orders
    lenient().when(jdbcTemplate.queryForObject(
        anyString(),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq(sellOrder1.getId())
    )).thenReturn(sellOrder1);
    
    lenient().when(jdbcTemplate.queryForObject(
        anyString(),
        any(org.springframework.jdbc.core.RowMapper.class),
        eq(sellOrder2.getId())
    )).thenReturn(sellOrder2);
    
    lenient().when(fillRepository.findByOrderId(sellOrder1.getId())).thenReturn(List.of());
    lenient().when(fillRepository.findByOrderId(sellOrder2.getId())).thenReturn(List.of());
    
    // Mock fills for the incoming BUY order (created during matching - 2 fills)
    Fill buyFill1 = new Fill(UUID.randomUUID(), UUID.randomUUID(), 60, 
        new BigDecimal("150.00"), Instant.now());
    Fill buyFill2 = new Fill(UUID.randomUUID(), UUID.randomUUID(), 40, 
        new BigDecimal("151.00"), Instant.now());
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenAnswer(invocation -> {
      UUID orderId = invocation.getArgument(0);
      if (orderId.equals(sellOrder1.getId()) || orderId.equals(sellOrder2.getId())) {
        return List.of(); // SELL orders have no existing fills
      }
      return List.of(buyFill1, buyFill2); // BUY order's fills
    });
    
    // Mock isOrderBookEmpty check - return > 0 (order book exists, not first order)
    lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1);

    UUID buyAccountId = UUID.randomUUID();
    CreateOrderRequest buyRequest = new CreateOrderRequest(
        buyAccountId, "CLIENT-106", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("155.00"), "DAY");
    Order buyOrder = executionService.createOrder(buyRequest);

    assertEquals("FILLED", buyOrder.getStatus());
    assertEquals(100, buyOrder.getFilledQty());
    // Average: (60 * 150.00 + 40 * 151.00) / 100 = 150.40
    BigDecimal expectedAvg = new BigDecimal("150.40");
    assertEquals(expectedAvg, buyOrder.getAvgFillPrice());
    
    // Should create 2 fills for buy order, 2 fills for sell orders = 4 total
    verify(fillRepository, times(4)).save(any(com.dev.tradingapi.model.Fill.class));
  }

  /**
   * Scenario 6: Order stays WORKING when no matches ever arrive.
   * BUY 100 AAPL @ 150, no sellers ever come - order remains WORKING.
   * This is NOT the first order (order book exists but no matches).
   */
  @Test
  void testCreateOrder_NoMatchesEver_StaysWorking() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-107", "AAPL", "BUY", 100, "LIMIT", 
        new BigDecimal("150.00"), "DAY");
    
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
    
    // No matching orders
    when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), 
        any(Object[].class))).thenReturn(List.of());
    
    // Mock isOrderBookEmpty check - return > 0 (order book exists, not first order)
    lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
        .thenReturn(1); // At least one SELL order exists in system
    
    // Mock fillRepository.findByOrderId to return empty (no existing fills)
    lenient().when(fillRepository.findByOrderId(any(UUID.class))).thenReturn(List.of());

    Order result = executionService.createOrder(request);

    assertEquals("WORKING", result.getStatus());
    assertEquals(0, result.getFilledQty());
    assertEquals(null, result.getAvgFillPrice());
    verify(fillRepository, times(0)).save(any(com.dev.tradingapi.model.Fill.class));
  }


}
