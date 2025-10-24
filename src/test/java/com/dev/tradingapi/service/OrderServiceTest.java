package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Unit tests for OrderService.
 * Tests order lifecycle operations including submit, get, fills, and cancel
 * with mocked dependencies (JdbcTemplate, MarketService, RiskService).
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  private JdbcTemplate jdbcTemplate;

  @Mock
  private MarketService marketService;

  @Mock
  private RiskService riskService;

  private OrderService orderService;

  /**
   * Set up test fixtures before each test.
   * Initializes OrderService with mocked dependencies.
   */
  @BeforeEach
  void setUp() {
    orderService = new OrderService(jdbcTemplate, marketService, riskService);
  }

  /**
   * Test submitting a MARKET order successfully - typical valid case.
   * Verifies that a MARKET order creates a single fill at market price,
   * persists order and fill, and updates positions.
   */
  @Test
  void testSubmit_MarketOrder_Success_TypicalCase() {
    // Arrange: Create a BUY MARKET order request
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setClientOrderId("client-123");
    req.setSymbol("AAPL");
    req.setSide("BUY");
    req.setQty(100);
    req.setType("MARKET");
    req.setTimeInForce("DAY");

    // Mock market data returning a quote with price $150.00
    Quote quote = new Quote("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"),
        new BigDecimal("148.00"), new BigDecimal("150.00"), 1000000L, Instant.now());
    when(marketService.getQuote("AAPL")).thenReturn(quote);

    // Mock risk validation to pass
    doNothing().when(riskService).validate(any(CreateOrderRequest.class), any(Quote.class));

    // Mock JDBC operations for order, fill, and position persistence
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class),
        eq("AAPL"))).thenReturn(null); // No existing position

    // Act: Submit the order
    Order result = orderService.submit(req);

    // Assert: Verify the order was created with correct properties
    assertNotNull(result);
    assertEquals("AAPL", result.getSymbol());
    assertEquals("BUY", result.getSide());
    assertEquals(100, result.getQty());
    assertEquals("MARKET", result.getType());
    assertEquals("FILLED", result.getStatus()); // Should be fully filled
    assertEquals(100, result.getFilledQty());
    assertEquals(new BigDecimal("150.00"), result.getAvgFillPrice());

    // Verify interactions: market data, risk check, and persistence
    verify(marketService, times(1)).getQuote("AAPL");
    verify(riskService, times(1)).validate(req, quote);
    // 1 saveOrder + 1 saveFill + 1 savePosition + 1 updateOrder = 4 total
    verify(jdbcTemplate, times(4)).update(anyString(), any(Object[].class));
  }

  /**
   * Test submitting a TWAP order successfully - typical valid case.
   * Verifies that a TWAP order splits into multiple equal fills,
   * distributing remainders evenly across the first slices.
   */
  @Test
  void testSubmit_TwapOrder_Success_TypicalCase() {
    // Arrange: Create a SELL TWAP order for 105 shares (should split into slices)
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setClientOrderId("client-twap-1");
    req.setSymbol("AMZN");
    req.setSide("SELL");
    req.setQty(105); // Will split into slices with some getting extra share
    req.setType("TWAP");
    req.setTimeInForce("DAY");

    // Mock market data
    Quote quote = new Quote("AMZN", new BigDecimal("3190.00"), new BigDecimal("3210.00"),
        new BigDecimal("3180.00"), new BigDecimal("3200.50"), 500000L, Instant.now());
    when(marketService.getQuote("AMZN")).thenReturn(quote);

    // Mock risk validation to pass
    doNothing().when(riskService).validate(any(CreateOrderRequest.class), any(Quote.class));

    // Mock JDBC operations
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class),
        eq("AMZN"))).thenReturn(null);

    // Act: Submit the TWAP order
    Order result = orderService.submit(req);

    // Assert: Verify TWAP behavior (should create multiple fills up to 10 slices)
    assertNotNull(result);
    assertEquals("AMZN", result.getSymbol());
    assertEquals("SELL", result.getSide());
    assertEquals(105, result.getQty());
    assertEquals("TWAP", result.getType());
    assertEquals("FILLED", result.getStatus());
    assertEquals(105, result.getFilledQty()); // All shares filled across slices

    // Verify multiple fill inserts (one for order, multiple for fills, one for
    // position)
    // For TWAP with 105 shares: 10 slices (10 base + 5 remainder =
    // 11,11,11,11,11,10,10,10,10,10)
    // 1 saveOrder + 10 saveFills + 1 savePosition + 1 updateOrder = 13 total
    verify(jdbcTemplate, times(13)).update(anyString(), any(Object[].class));
  }

  /**
   * Test submitting order when market data unavailable - invalid case.
   * Should throw NotFoundException when quote is not available for symbol.
   */
  @Test
  void testSubmit_MarketDataUnavailable_ThrowsNotFoundException() {
    // Arrange: Create order request for a symbol with no market data
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("INVALID");
    req.setSide("BUY");
    req.setQty(10);
    req.setType("MARKET");

    // Mock market service returning null (symbol not found)
    when(marketService.getQuote("INVALID")).thenReturn(null);

    // Act & Assert: Verify exception is thrown with appropriate message
    NotFoundException exception = assertThrows(NotFoundException.class, () -> {
      orderService.submit(req);
    });

    assertTrue(exception.getMessage().contains("Market data not available"));
    assertTrue(exception.getMessage().contains("INVALID"));

    // Verify risk validation was never called since market data check failed first
    verify(riskService, times(0)).validate(any(), any());
  }

  /**
   * Test submitting order with existing position - atypical valid case.
   * Verifies that position is updated correctly when account already holds the
   * symbol.
   */
  @Test
  void testSubmit_WithExistingPosition_UpdatesCorrectly() {
    // Arrange: Create BUY order that will add to existing position
    CreateOrderRequest req = new CreateOrderRequest();
    UUID accountId = UUID.randomUUID();
    req.setAccountId(accountId);
    req.setSymbol("IBM");
    req.setSide("BUY");
    req.setQty(50);
    req.setType("MARKET");

    // Mock market data
    Quote quote = new Quote("IBM", new BigDecimal("138.00"), new BigDecimal("142.00"),
        new BigDecimal("137.00"), new BigDecimal("140.00"), 300000L, Instant.now());
    when(marketService.getQuote("IBM")).thenReturn(quote);

    doNothing().when(riskService).validate(any(), any());

    // Mock existing position: account holds 100 shares at avg cost $135
    Position existingPos = new Position(accountId, "IBM", 100, new BigDecimal("135.00"));
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(accountId), eq("IBM")))
        .thenReturn(existingPos);

    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    // Act: Submit order
    Order result = orderService.submit(req);

    // Assert: Order executed successfully
    assertNotNull(result);
    assertEquals("FILLED", result.getStatus());
    assertEquals(50, result.getFilledQty());

    // Verify position update was called (existing position should be updated with
    // new fill)
    // 1 saveOrder + 1 saveFill + 1 savePosition + 1 updateOrder = 4 total
    verify(jdbcTemplate, times(4)).update(anyString(), any(Object[].class));
  }

  /**
   * Test getOrder by ID successfully - typical valid case.
   * Verifies retrieving an existing order returns correct details.
   */
  @Test
  void testGetOrder_Success_TypicalCase() {
    // Arrange: Mock an existing order in database
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setSymbol("AAPL");
    mockOrder.setSide("BUY");
    mockOrder.setQty(100);
    mockOrder.setStatus("FILLED");
    mockOrder.setCreatedAt(Instant.now());

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);

    // Act: Retrieve the order
    Order result = orderService.getOrder(orderId);

    // Assert: Verify correct order returned
    assertNotNull(result);
    assertEquals(orderId, result.getId());
    assertEquals("AAPL", result.getSymbol());
    assertEquals("FILLED", result.getStatus());

    verify(jdbcTemplate, times(1)).queryForObject(anyString(), any(RowMapper.class), eq(orderId));
  }

  /**
   * Test getOrder when order not found - invalid case.
   * Should throw NotFoundException when order ID doesn't exist.
   */
  @Test
  void testGetOrder_NotFound_ThrowsNotFoundException() {
    // Arrange: Mock JDBC throwing exception for non-existent order
    UUID nonExistentId = UUID.randomUUID();
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(nonExistentId)))
        .thenThrow(new RuntimeException("No result"));

    // Act & Assert: Verify NotFoundException is thrown
    NotFoundException exception = assertThrows(NotFoundException.class, () -> {
      orderService.getOrder(nonExistentId);
    });

    assertTrue(exception.getMessage().contains("Order not found"));
    assertTrue(exception.getMessage().contains(nonExistentId.toString()));
  }

  /**
   * Test getFills for an order successfully - typical valid case.
   * Verifies retrieving fills returns list ordered by timestamp.
   */
  @Test
  void testGetFills_Success_TypicalCase() {
    // Arrange: Mock an order with multiple fills
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setStatus("FILLED");

    // Mock fills for the order
    Fill fill1 = new Fill(UUID.randomUUID(), orderId, 50, new BigDecimal("150.00"), Instant.now());
    Fill fill2 = new Fill(UUID.randomUUID(), orderId, 50, new BigDecimal("150.10"),
        Instant.now().plusSeconds(1));
    List<Fill> mockFills = Arrays.asList(fill1, fill2);

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockFills);

    // Act: Get fills for the order
    List<Fill> result = orderService.getFills(orderId);

    // Assert: Verify fills returned correctly
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(50, result.get(0).getQty());
    assertEquals(50, result.get(1).getQty());

    verify(jdbcTemplate, times(1)).queryForObject(anyString(), any(RowMapper.class), eq(orderId));
    verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), eq(orderId));
  }

  /**
   * Test getFills when order doesn't exist - invalid case.
   * Should throw NotFoundException before attempting to query fills.
   */
  @Test
  void testGetFills_OrderNotFound_ThrowsNotFoundException() {
    // Arrange: Mock order lookup failing
    UUID nonExistentId = UUID.randomUUID();
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(nonExistentId)))
        .thenThrow(new RuntimeException("No result"));

    // Act & Assert: Verify exception thrown during order validation
    assertThrows(NotFoundException.class, () -> {
      orderService.getFills(nonExistentId);
    });

    // Verify fills query was never attempted
    verify(jdbcTemplate, times(0)).query(anyString(), any(RowMapper.class), any(UUID.class));
  }

  /**
   * Test getFills returns empty list - atypical valid case.
   * When order exists but has no fills (edge case, shouldn't normally happen).
   */
  @Test
  void testGetFills_EmptyList_AtypicalCase() {
    // Arrange: Order exists but has no fills
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setStatus("NEW");

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(Arrays.asList());

    // Act: Get fills
    List<Fill> result = orderService.getFills(orderId);

    // Assert: Empty list returned (not null)
    assertNotNull(result);
    assertTrue(result.isEmpty());
  }

  /**
   * Test cancel order successfully - typical valid case.
   * Verifies canceling an active order updates status to CANCELLED.
   */
  @Test
  void testCancel_ActiveOrder_Success_TypicalCase() {
    // Arrange: Mock an active order that can be cancelled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setSymbol("AAPL");
    mockOrder.setStatus("NEW"); // Active status
    mockOrder.setFilledQty(0);

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

    // Act: Cancel the order
    Order result = orderService.cancel(orderId);

    // Assert: Verify status changed to CANCELLED
    assertNotNull(result);
    assertEquals(orderId, result.getId());
    assertEquals("CANCELLED", result.getStatus());

    // Verify update was called to persist cancellation
    verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
  }

  /**
   * Test cancel already filled order - atypical valid case.
   * Should return order unchanged when already in terminal FILLED state.
   */
  @Test
  void testCancel_FilledOrder_ReturnsUnchanged() {
    // Arrange: Mock an order already filled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setStatus("FILLED"); // Terminal status
    mockOrder.setFilledQty(100);

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);

    // Act: Attempt to cancel
    Order result = orderService.cancel(orderId);

    // Assert: Status remains FILLED
    assertNotNull(result);
    assertEquals("FILLED", result.getStatus());

    // Verify no update was attempted (already terminal)
    verify(jdbcTemplate, times(0)).update(anyString(), any(Object[].class));
  }

  /**
   * Test cancel already cancelled order - atypical valid case.
   * Should return order unchanged when already CANCELLED.
   */
  @Test
  void testCancel_CancelledOrder_ReturnsUnchanged() {
    // Arrange: Mock an order already cancelled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setStatus("CANCELLED"); // Already cancelled

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(mockOrder);

    // Act: Attempt to cancel again
    Order result = orderService.cancel(orderId);

    // Assert: Status remains CANCELLED
    assertNotNull(result);
    assertEquals("CANCELLED", result.getStatus());

    // Verify no update was attempted
    verify(jdbcTemplate, times(0)).update(anyString(), any(Object[].class));
  }

  /**
   * Test cancel non-existent order - invalid case.
   * Should throw NotFoundException when order doesn't exist.
   */
  @Test
  void testCancel_OrderNotFound_ThrowsNotFoundException() {
    // Arrange: Mock order lookup failing
    UUID nonExistentId = UUID.randomUUID();
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(nonExistentId)))
        .thenThrow(new RuntimeException("No result"));

    // Act & Assert: Verify exception is thrown
    assertThrows(NotFoundException.class, () -> {
      orderService.cancel(nonExistentId);
    });

    // Verify no update was attempted
    verify(jdbcTemplate, times(0)).update(anyString(), any(Object[].class));
  }

  /**
   * Test submitting small TWAP order - atypical valid case.
   * For very small quantities, TWAP should create minimum 2 slices.
   */
  @Test
  void testSubmit_SmallTwapOrder_CreateMinimumSlices() {
    // Arrange: TWAP order with only 3 shares (should create 2-3 slices, not 1)
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("IBM");
    req.setSide("BUY");
    req.setQty(3); // Small quantity
    req.setType("TWAP");

    Quote quote = new Quote("IBM", new BigDecimal("138.00"), new BigDecimal("142.00"),
        new BigDecimal("137.00"), new BigDecimal("140.00"), 300000L, Instant.now());
    when(marketService.getQuote("IBM")).thenReturn(quote);

    doNothing().when(riskService).validate(any(), any());
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class), eq("IBM")))
        .thenReturn(null);

    // Act: Submit small TWAP order
    Order result = orderService.submit(req);

    // Assert: Order filled completely despite small size
    assertNotNull(result);
    assertEquals("FILLED", result.getStatus());
    assertEquals(3, result.getFilledQty());

    // Verify multiple fills were created (at least 2 for TWAP)
    // 1 saveOrder + 3 saveFills + 1 savePosition + 1 updateOrder = 6 total
    verify(jdbcTemplate, times(6)).update(anyString(), any(Object[].class));
  }

  /**
   * Test write-then-read flow: submit an order (write) then retrieve it and its
   * fills (read). Verifies persistence interactions are used for both.
   */
  @Test
  void testWriteThenRead_SubmitThenGetOrderAndFills() {
    // Arrange: MARKET order
    CreateOrderRequest req = new CreateOrderRequest();
    UUID accountId = UUID.randomUUID();
    req.setAccountId(accountId);
    req.setSymbol("AAPL");
    req.setSide("BUY");
    req.setQty(10);
    req.setType("MARKET");

    Quote quote = new Quote("AAPL", new BigDecimal("149.00"), new BigDecimal("151.00"),
        new BigDecimal("148.00"), new BigDecimal("150.00"), 100000L, Instant.now());
    when(marketService.getQuote("AAPL")).thenReturn(quote);
    doNothing().when(riskService).validate(any(), any());
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class),
        eq("AAPL"))).thenReturn(null);

    // Act: Write - submit order
    Order created = orderService.submit(req);

    // Arrange read stubs based on created order id
    UUID orderId = created.getId();
    Order stored = new Order();
    stored.setId(orderId);
    stored.setAccountId(accountId);
    stored.setSymbol("AAPL");
    stored.setSide("BUY");
    stored.setQty(10);
    stored.setStatus("FILLED");

    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(stored);

    Fill f1 = new Fill(UUID.randomUUID(), orderId, 10, new BigDecimal("150.00"),
        Instant.now());
    when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(orderId)))
        .thenReturn(Arrays.asList(f1));

    // Act: Read - get order and fills
    Order fetched = orderService.getOrder(orderId);

    // Assert: Data round-trips as expected
    assertNotNull(fetched);
    assertEquals(orderId, fetched.getId());
    assertEquals("AAPL", fetched.getSymbol());
    List<Fill> fetchedFills = orderService.getFills(orderId);
    assertNotNull(fetchedFills);
    assertEquals(1, fetchedFills.size());
    assertEquals(10, fetchedFills.get(0).getQty());

    // Verify write and read occurred (allowing for multiple lookups internally)
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
        .queryForObject(anyString(), any(RowMapper.class), eq(orderId));
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
        .query(anyString(), any(RowMapper.class), eq(orderId));
  }

  /**
   * Test two clients submit orders without interference.
   * Verifies JDBC persistence receives both accountIds across updates.
   */
  @Test
  void testSubmit_TwoClients_AreIsolated() {
    // Arrange two different accounts
    UUID acctA = UUID.randomUUID();

    CreateOrderRequest a = new CreateOrderRequest();
    a.setAccountId(acctA);
    a.setSymbol("AAPL");
    a.setSide("BUY");
    a.setQty(5);
    a.setType("MARKET");

    UUID acctB = UUID.randomUUID();

    CreateOrderRequest b = new CreateOrderRequest();
    b.setAccountId(acctB);
    b.setSymbol("MSFT");
    b.setSide("SELL");
    b.setQty(7);
    b.setType("MARKET");

    when(marketService.getQuote("AAPL")).thenReturn(new Quote("AAPL",
        new BigDecimal("149"), new BigDecimal("151"), new BigDecimal("148"),
        new BigDecimal("150"), 1000L, Instant.now()));
    when(marketService.getQuote("MSFT")).thenReturn(new Quote("MSFT",
        new BigDecimal("299"), new BigDecimal("301"), new BigDecimal("298"),
        new BigDecimal("300"), 1000L, Instant.now()));
    doNothing().when(riskService).validate(any(), any());
    when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
    when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(UUID.class), any()))
        .thenReturn(null);

    // Act: submit orders for two clients
    orderService.submit(a);
    orderService.submit(b);

    // Capture all update parameter arrays to verify both accountIds appeared
    org.mockito.ArgumentCaptor<Object[]> paramsCaptor = org.mockito.ArgumentCaptor.forClass(
        Object[].class);
    verify(jdbcTemplate, org.mockito.Mockito.atLeast(1))
        .update(anyString(), paramsCaptor.capture());

    boolean seenA = false;
    boolean seenB = false;
    for (Object[] arr : paramsCaptor.getAllValues()) {
      for (Object v : arr) {
        if (acctA.equals(v)) {
          seenA = true;
        }
        if (acctB.equals(v)) {
          seenB = true;
        }
      }
    }

    assertTrue(seenA, "Updates should contain account A id");
    assertTrue(seenB, "Updates should contain account B id");
  }
}
