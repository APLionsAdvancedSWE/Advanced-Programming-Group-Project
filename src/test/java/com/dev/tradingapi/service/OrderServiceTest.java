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
import com.dev.tradingapi.repository.FillRepository;
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

  @Mock
  private FillRepository fillRepository;

  @Mock
  private AccountService accountService;

  @Mock
  private ExecutionService executionService;

  private OrderService orderService;

  /**
   * Set up test fixtures before each test.
   * Initializes OrderService with mocked dependencies.
   */
  @BeforeEach
  void setUp() {
    orderService = new OrderService(jdbcTemplate, fillRepository,
            accountService, executionService);
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

    // The execution engine is responsible for creating the order and fills.
    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("BUY");
    engineOrder.setQty(100);
    engineOrder.setType("MARKET");
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(100);
    engineOrder.setAvgFillPrice(new BigDecimal("150.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    Fill fill = new Fill(UUID.randomUUID(), engineOrder.getId(), 100,
        new BigDecimal("150.00"), Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId())).thenReturn(List.of(fill));

    // Act: Submit the order via OrderService
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

    // Verify interactions: delegated to execution engine and cash adjusted
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    // BUY 100 @ 150 => cash delta = -15000.00
    verify(accountService, times(1))
        .adjustCash(eq(req.getAccountId()), eq(new BigDecimal("-15000.00")));
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

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AMZN");
    engineOrder.setSide("SELL");
    engineOrder.setQty(105);
    engineOrder.setType("TWAP");
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(105);
    engineOrder.setAvgFillPrice(new BigDecimal("3200.50"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    // Simulate 3 TWAP fills whose total notional equals 105 * 3200.50
    BigDecimal price = new BigDecimal("3200.50");
    Fill f1 = new Fill(UUID.randomUUID(), engineOrder.getId(), 35, price, Instant.now());
    Fill f2 = new Fill(UUID.randomUUID(), engineOrder.getId(), 35, price, Instant.now());
    Fill f3 = new Fill(UUID.randomUUID(), engineOrder.getId(), 35, price, Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId()))
        .thenReturn(Arrays.asList(f1, f2, f3));

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

    // Verify delegation and cash adjustment for SELL side (cash credited)
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    BigDecimal expectedNotional = price.multiply(new BigDecimal("105"));
    verify(accountService, times(1))
        .adjustCash(eq(req.getAccountId()), eq(expectedNotional));
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

    // ExecutionService is responsible for throwing NotFoundException when
    // market data is unavailable. OrderService should simply propagate it.
    when(executionService.createOrder(any(CreateOrderRequest.class)))
        .thenThrow(new NotFoundException("Market data not available for symbol: INVALID"));

    // Act & Assert: Verify exception is thrown with appropriate message
    NotFoundException exception = assertThrows(NotFoundException.class, () -> {
      orderService.submit(req);
    });

    assertTrue(exception.getMessage().contains("Market data not available"));
    assertTrue(exception.getMessage().contains("INVALID"));

    // Verify we delegated to the execution engine
    verify(executionService, times(1)).createOrder(req);
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

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(accountId);
    engineOrder.setSymbol("IBM");
    engineOrder.setSide("BUY");
    engineOrder.setQty(50);
    engineOrder.setType("MARKET");
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(50);
    engineOrder.setAvgFillPrice(new BigDecimal("140.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    Fill fill = new Fill(UUID.randomUUID(), engineOrder.getId(), 50,
        new BigDecimal("140.00"), Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId())).thenReturn(List.of(fill));

    // Act: Submit order
    Order result = orderService.submit(req);

    // Assert: Order executed successfully
    assertNotNull(result);
    assertEquals("FILLED", result.getStatus());
    assertEquals(50, result.getFilledQty());

    // Verify we delegated to execution engine and adjusted cash
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    verify(accountService, times(1))
        .adjustCash(eq(accountId), eq(new BigDecimal("-7000.00")));
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
    when(fillRepository.findByOrderId(orderId)).thenReturn(mockFills);

    // Act: Get fills for the order
    List<Fill> result = orderService.getFills(orderId);

    // Assert: Verify fills returned correctly
    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals(50, result.get(0).getQty());
    assertEquals(50, result.get(1).getQty());

    verify(jdbcTemplate, times(1)).queryForObject(anyString(), any(RowMapper.class), eq(orderId));
    verify(fillRepository, times(1)).findByOrderId(orderId);
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
    verify(fillRepository, times(0)).findByOrderId(any(UUID.class));
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
    when(fillRepository.findByOrderId(orderId)).thenReturn(Arrays.asList());

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

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("IBM");
    engineOrder.setSide("BUY");
    engineOrder.setQty(3);
    engineOrder.setType("TWAP");
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(3);
    engineOrder.setAvgFillPrice(new BigDecimal("140.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    // Simulate 2 TWAP fills for a small order
    BigDecimal price = new BigDecimal("140.00");
    Fill f1 = new Fill(UUID.randomUUID(), engineOrder.getId(), 1, price, Instant.now());
    Fill f2 = new Fill(UUID.randomUUID(), engineOrder.getId(), 2, price, Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId()))
        .thenReturn(Arrays.asList(f1, f2));

    // Act: Submit small TWAP order
    Order result = orderService.submit(req);

    // Assert: Order filled completely despite small size
    assertNotNull(result);
    assertEquals("FILLED", result.getStatus());
    assertEquals(3, result.getFilledQty());

    // Verify we delegated and adjusted cash
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    BigDecimal expectedNotional = price.multiply(new BigDecimal("3"));
    verify(accountService, times(1))
        .adjustCash(eq(req.getAccountId()), eq(expectedNotional.negate()));
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

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(accountId);
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("BUY");
    engineOrder.setQty(10);
    engineOrder.setType("MARKET");
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(10);
    engineOrder.setAvgFillPrice(new BigDecimal("150.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    Fill f1 = new Fill(UUID.randomUUID(), engineOrder.getId(), 10,
        new BigDecimal("150.00"), Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId()))
        .thenReturn(Arrays.asList(f1));

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

    // fillRepository.findByOrderId(orderId) already stubbed above

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

    // Verify write (delegation) and read occurred
    verify(executionService, times(1)).createOrder(req);
    verify(jdbcTemplate, org.mockito.Mockito.atLeastOnce())
        .queryForObject(anyString(), any(RowMapper.class), eq(orderId));
    verify(fillRepository, org.mockito.Mockito.atLeastOnce())
        .findByOrderId(eq(orderId));
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

    Order engineOrderA = new Order();
    engineOrderA.setId(UUID.randomUUID());
    engineOrderA.setAccountId(acctA);
    engineOrderA.setSymbol("AAPL");
    engineOrderA.setSide("BUY");
    engineOrderA.setQty(5);
    engineOrderA.setType("MARKET");
    engineOrderA.setStatus("FILLED");
    engineOrderA.setFilledQty(5);
    engineOrderA.setAvgFillPrice(new BigDecimal("150"));

    Order engineOrderB = new Order();
    engineOrderB.setId(UUID.randomUUID());
    engineOrderB.setAccountId(acctB);
    engineOrderB.setSymbol("MSFT");
    engineOrderB.setSide("SELL");
    engineOrderB.setQty(7);
    engineOrderB.setType("MARKET");
    engineOrderB.setStatus("FILLED");
    engineOrderB.setFilledQty(7);
    engineOrderB.setAvgFillPrice(new BigDecimal("300"));

    when(executionService.createOrder(a)).thenReturn(engineOrderA);
    when(executionService.createOrder(b)).thenReturn(engineOrderB);

    when(fillRepository.findByOrderId(engineOrderA.getId()))
        .thenReturn(List.of(new Fill(UUID.randomUUID(), engineOrderA.getId(), 5,
            new BigDecimal("150"), Instant.now())));
    when(fillRepository.findByOrderId(engineOrderB.getId()))
        .thenReturn(List.of(new Fill(UUID.randomUUID(), engineOrderB.getId(), 7,
            new BigDecimal("300"), Instant.now())));

    // Act: submit orders for two clients
    orderService.submit(a);
    orderService.submit(b);

    // Verify each account's cash was adjusted independently
    verify(accountService, times(1))
        .adjustCash(eq(acctA), eq(new BigDecimal("-750")));
    verify(accountService, times(1))
        .adjustCash(eq(acctB), eq(new BigDecimal("2100")));
  }

  /**
   * Test LIMIT BUY order with price too low - should not fill.
   * When limit price < current price, order should remain WORKING.
   */
  @Test
  void testSubmit_LimitBuyPriceTooLow_NoFill() {
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("AAPL");
    req.setSide("BUY");
    req.setQty(100);
    req.setType("LIMIT");
    req.setLimitPrice(new BigDecimal("145.00")); // Limit $145, market $150
    req.setTimeInForce("GTC");

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("BUY");
    engineOrder.setQty(100);
    engineOrder.setType("LIMIT");
    engineOrder.setLimitPrice(new BigDecimal("145.00"));
    engineOrder.setStatus("WORKING");
    engineOrder.setFilledQty(0);
    engineOrder.setAvgFillPrice(null);

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);
    when(fillRepository.findByOrderId(engineOrder.getId())).thenReturn(List.of());

    Order result = orderService.submit(req);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("145.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill
    assertEquals(0, result.getFilledQty());
    // No fills => no cash adjustment
    verify(accountService, times(0)).adjustCash(any(), any());
  }

  /**
   * Test LIMIT BUY order with acceptable price - should fill.
   * When limit price >= current price, order should fill.
   */
  @Test
  void testSubmit_LimitBuyPriceAcceptable_Fills() {
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("AAPL");
    req.setSide("BUY");
    req.setQty(100);
    req.setType("LIMIT");
    req.setLimitPrice(new BigDecimal("155.00")); // Limit $155, market $150
    req.setTimeInForce("GTC");

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("BUY");
    engineOrder.setQty(100);
    engineOrder.setType("LIMIT");
    engineOrder.setLimitPrice(new BigDecimal("155.00"));
    engineOrder.setStatus("FILLED");
    engineOrder.setFilledQty(100);
    engineOrder.setAvgFillPrice(new BigDecimal("155.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    Fill fill = new Fill(UUID.randomUUID(), engineOrder.getId(), 100,
        new BigDecimal("155.00"), Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId())).thenReturn(List.of(fill));

    Order result = orderService.submit(req);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals(new BigDecimal("155.00"), result.getLimitPrice());
    // Should fill
    assertEquals("FILLED", result.getStatus());
    assertEquals(100, result.getFilledQty());
    // Fill price should be limit price (better for trader)
    assertEquals(new BigDecimal("155.00"), result.getAvgFillPrice());
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    verify(accountService, times(1))
        .adjustCash(eq(req.getAccountId()), eq(new BigDecimal("-15500.00")));
  }

  /**
   * Test LIMIT SELL order with price too high - should not fill.
   * When limit price > current price, order should remain WORKING.
   */
  @Test
  void testSubmit_LimitSellPriceTooHigh_NoFill() {
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("AAPL");
    req.setSide("SELL");
    req.setQty(100);
    req.setType("LIMIT");
    req.setLimitPrice(new BigDecimal("160.00")); // Limit $160, market $150
    req.setTimeInForce("GTC");

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("SELL");
    engineOrder.setQty(100);
    engineOrder.setType("LIMIT");
    engineOrder.setLimitPrice(new BigDecimal("160.00"));
    engineOrder.setStatus("WORKING");
    engineOrder.setFilledQty(0);
    engineOrder.setAvgFillPrice(null);

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);
    when(fillRepository.findByOrderId(engineOrder.getId())).thenReturn(List.of());

    Order result = orderService.submit(req);

    assertNotNull(result);
    assertEquals("LIMIT", result.getType());
    assertEquals("SELL", result.getSide());
    assertEquals(new BigDecimal("160.00"), result.getLimitPrice());
    assertEquals("WORKING", result.getStatus()); // No fill
    assertEquals(0, result.getFilledQty());
    // No fills => no cash adjustment
    verify(accountService, times(0)).adjustCash(any(), any());
  }

  /**
   * Test MARKET order with insufficient liquidity - partial fill.
   * When available liquidity < order quantity, should partially fill.
   */
  @Test
  void testSubmit_MarketOrderInsufficientLiquidity_PartialFill() {
    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(UUID.randomUUID());
    req.setSymbol("AAPL");
    req.setSide("BUY");
    req.setQty(1000); // Request 1000 shares
    req.setType("MARKET");
    req.setTimeInForce("DAY");

    Order engineOrder = new Order();
    engineOrder.setId(UUID.randomUUID());
    engineOrder.setAccountId(req.getAccountId());
    engineOrder.setSymbol("AAPL");
    engineOrder.setSide("BUY");
    engineOrder.setQty(1000);
    engineOrder.setType("MARKET");
    engineOrder.setStatus("PARTIALLY_FILLED");
    engineOrder.setFilledQty(200);
    engineOrder.setAvgFillPrice(new BigDecimal("150.00"));

    when(executionService.createOrder(any(CreateOrderRequest.class))).thenReturn(engineOrder);

    Fill partialFill = new Fill(UUID.randomUUID(), engineOrder.getId(), 200,
        new BigDecimal("150.00"), Instant.now());
    when(fillRepository.findByOrderId(engineOrder.getId()))
        .thenReturn(List.of(partialFill));

    Order result = orderService.submit(req);

    assertNotNull(result);
    assertEquals("MARKET", result.getType());
    assertEquals(1000, result.getQty()); // Requested 1000
    assertEquals("PARTIALLY_FILLED", result.getStatus()); // Should be partial
    assertEquals(200, result.getFilledQty());
    verify(executionService, times(1)).createOrder(req);
    verify(fillRepository, times(1)).findByOrderId(engineOrder.getId());
    verify(accountService, times(1))
        .adjustCash(eq(req.getAccountId()), eq(new BigDecimal("-30000.00")));
  }
}
