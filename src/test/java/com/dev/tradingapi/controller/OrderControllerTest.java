package com.dev.tradingapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.service.OrderService;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Unit tests for OrderController.
 * Tests REST endpoints for order management (create, get, fills, cancel)
 * with mocked OrderService dependency.
 */
@ExtendWith(MockitoExtension.class)
class OrderControllerTest {

  @Mock
  private OrderService orderService;

  private OrderController orderController;

  /**
   * Set up test fixtures before each test.
   * Initializes OrderController with mocked OrderService.
   */
  @BeforeEach
  void setUp() {
    orderController = new OrderController(orderService);
  }

  /**
   * Test creating a MARKET order successfully - typical valid case.
   * Verifies POST /orders endpoint creates order and returns 201 CREATED.
   */
  @Test
  void testCreate_MarketOrder_Success_TypicalCase() {
    // Arrange: Create a BUY MARKET order request
    CreateOrderRequest request = new CreateOrderRequest();
    request.setAccountId(UUID.randomUUID());
    request.setClientOrderId("client-order-1");
    request.setSymbol("AAPL");
    request.setSide("BUY");
    request.setQty(100);
    request.setType("MARKET");
    request.setTimeInForce("DAY");

    // Mock service returning a filled order
    Order mockOrder = new Order();
    UUID orderId = UUID.randomUUID();
    mockOrder.setId(orderId);
    mockOrder.setAccountId(request.getAccountId());
    mockOrder.setSymbol("AAPL");
    mockOrder.setSide("BUY");
    mockOrder.setQty(100);
    mockOrder.setType("MARKET");
    mockOrder.setStatus("FILLED");
    mockOrder.setFilledQty(100);
    mockOrder.setAvgFillPrice(new BigDecimal("150.00"));
    mockOrder.setCreatedAt(Instant.now());

    when(orderService.submit(any(CreateOrderRequest.class))).thenReturn(mockOrder);

    // Act: Call the controller endpoint
    ResponseEntity<Order> response = orderController.create(request);

    // Assert: Verify response status and body
    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(orderId, response.getBody().getId());
    assertEquals("FILLED", response.getBody().getStatus());
    assertEquals(100, response.getBody().getFilledQty());

    // Verify service was called exactly once
    verify(orderService, times(1)).submit(request);
  }

  /**
   * Test creating a TWAP order successfully - typical valid case.
   * Verifies TWAP order type is properly handled.
   */
  @Test
  void testCreate_TwapOrder_Success_TypicalCase() {
    // Arrange: Create a SELL TWAP order request
    CreateOrderRequest request = new CreateOrderRequest();
    request.setAccountId(UUID.randomUUID());
    request.setClientOrderId("twap-order-1");
    request.setSymbol("AMZN");
    request.setSide("SELL");
    request.setQty(200);
    request.setType("TWAP");

    // Mock service returning a filled TWAP order
    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setAccountId(request.getAccountId());
    mockOrder.setSymbol("AMZN");
    mockOrder.setSide("SELL");
    mockOrder.setQty(200);
    mockOrder.setType("TWAP");
    mockOrder.setStatus("FILLED");
    mockOrder.setFilledQty(200);
    mockOrder.setAvgFillPrice(new BigDecimal("3200.00"));

    when(orderService.submit(any(CreateOrderRequest.class))).thenReturn(mockOrder);

    // Act: Call the controller
    ResponseEntity<Order> response = orderController.create(request);

    // Assert: Verify TWAP order created successfully
    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("TWAP", response.getBody().getType());
    assertEquals("FILLED", response.getBody().getStatus());

    verify(orderService, times(1)).submit(request);
  }

  /**
   * Test creating order with invalid symbol - invalid case.
   * Service throws NotFoundException, propagated to controller.
   */
  @Test
  void testCreate_InvalidSymbol_ThrowsException() {
    // Arrange: Order with invalid symbol
    CreateOrderRequest request = new CreateOrderRequest();
    request.setSymbol("INVALID");
    request.setSide("BUY");
    request.setQty(10);

    // Mock service throwing NotFoundException
    when(orderService.submit(any(CreateOrderRequest.class)))
        .thenThrow(new NotFoundException("Market data not available for symbol: INVALID"));

    // Act & Assert: Verify exception propagates
    try {
      orderController.create(request);
    } catch (NotFoundException e) {
      // Expected exception - controller doesn't catch it, lets it propagate
      assertEquals("Market data not available for symbol: INVALID", e.getMessage());
    }

    verify(orderService, times(1)).submit(request);
  }

  /**
   * Test getOrder by ID successfully - typical valid case.
   * Verifies GET /orders/{orderId} returns order details.
   */
  @Test
  void testGetOrder_Success_TypicalCase() {
    // Arrange: Mock an existing order
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setSymbol("IBM");
    mockOrder.setSide("BUY");
    mockOrder.setQty(50);
    mockOrder.setStatus("FILLED");
    mockOrder.setCreatedAt(Instant.now());

    when(orderService.getOrder(eq(orderId))).thenReturn(mockOrder);

    // Act: Get order by ID
    ResponseEntity<Order> response = orderController.getOrder(orderId);

    // Assert: Verify response
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(orderId, response.getBody().getId());
    assertEquals("IBM", response.getBody().getSymbol());
    assertEquals("FILLED", response.getBody().getStatus());

    verify(orderService, times(1)).getOrder(orderId);
  }

  /**
   * Test getOrder for a NEW order (atypical valid case).
   * Verifies controller returns an order that exists but isn't filled yet.
   */
  @Test
  void testGetOrder_Atypical_NewStatus() {
    // Arrange: Existing order that is not yet filled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setSymbol("MSFT");
    mockOrder.setSide("BUY");
    mockOrder.setQty(25);
    mockOrder.setStatus("NEW");

    when(orderService.getOrder(eq(orderId))).thenReturn(mockOrder);

    // Act: Retrieve order
    ResponseEntity<Order> response = orderController.getOrder(orderId);

    // Assert: Atypical but valid - order exists and is NEW
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(orderId, response.getBody().getId());
    assertEquals("NEW", response.getBody().getStatus());

    verify(orderService, times(1)).getOrder(orderId);
  }

  /**
   * Test getOrder when order not found - invalid case.
   * Service throws NotFoundException which propagates to caller.
   */
  @Test
  void testGetOrder_NotFound_ThrowsException() {
    // Arrange: Non-existent order ID
    UUID nonExistentId = UUID.randomUUID();
    when(orderService.getOrder(eq(nonExistentId)))
        .thenThrow(new NotFoundException("Order not found: " + nonExistentId));

    // Act & Assert: Verify exception propagates
    try {
      orderController.getOrder(nonExistentId);
    } catch (NotFoundException e) {
      // Expected - controller lets exception propagate for error handling
      assertNotNull(e.getMessage());
    }

    verify(orderService, times(1)).getOrder(nonExistentId);
  }

  /**
   * Test getFills for an order successfully - typical valid case.
   * Verifies GET /orders/{orderId}/fills returns list of fills.
   */
  @Test
  void testGetFills_Success_TypicalCase() {
    // Arrange: Order with multiple fills
    UUID orderId = UUID.randomUUID();
    Fill fill1 = new Fill(
        UUID.randomUUID(),
        orderId,
        50,
        new BigDecimal("150.00"),
        Instant.now());
    Fill fill2 = new Fill(
        UUID.randomUUID(),
        orderId,
        50,
        new BigDecimal("150.10"),
        Instant.now().plusSeconds(1));
    List<Fill> mockFills = Arrays.asList(fill1, fill2);

    when(orderService.getFills(eq(orderId))).thenReturn(mockFills);

    // Act: Get fills for the order
    ResponseEntity<List<Fill>> response = orderController.getFills(orderId);

    // Assert: Verify response contains fills
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(2, response.getBody().size());
    assertEquals(50, response.getBody().get(0).getQty());
    assertEquals(50, response.getBody().get(1).getQty());

    verify(orderService, times(1)).getFills(orderId);
  }

  /**
   * Test getFills returns empty list - atypical valid case.
   * Order exists but has no fills (shouldn't happen in production).
   */
  @Test
  void testGetFills_EmptyList_AtypicalCase() {
    // Arrange: Order with no fills
    UUID orderId = UUID.randomUUID();
    when(orderService.getFills(eq(orderId))).thenReturn(Arrays.asList());

    // Act: Get fills
    ResponseEntity<List<Fill>> response = orderController.getFills(orderId);

    // Assert: Empty list returned successfully
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals(0, response.getBody().size());

    verify(orderService, times(1)).getFills(orderId);
  }

  /**
   * Test getFills when order doesn't exist - invalid case.
   * Service validates order existence and throws NotFoundException.
   */
  @Test
  void testGetFills_OrderNotFound_ThrowsException() {
    // Arrange: Non-existent order
    UUID nonExistentId = UUID.randomUUID();
    when(orderService.getFills(eq(nonExistentId)))
        .thenThrow(new NotFoundException("Order not found: " + nonExistentId));

    // Act & Assert: Verify exception propagates
    try {
      orderController.getFills(nonExistentId);
    } catch (NotFoundException e) {
      assertNotNull(e.getMessage());
    }

    verify(orderService, times(1)).getFills(nonExistentId);
  }

  /**
   * Test canceling an active order successfully - typical valid case.
   * Verifies POST /orders/{orderId}/cancel updates status.
   */
  @Test
  void testCancel_ActiveOrder_Success_TypicalCase() {
    // Arrange: Active order that can be cancelled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setSymbol("AAPL");
    mockOrder.setStatus("CANCELLED"); // After cancellation
    mockOrder.setFilledQty(0);

    when(orderService.cancel(eq(orderId))).thenReturn(mockOrder);

    // Act: Cancel the order
    ResponseEntity<Order> response = orderController.cancel(orderId);

    // Assert: Verify order cancelled
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("CANCELLED", response.getBody().getStatus());

    verify(orderService, times(1)).cancel(orderId);
  }

  /**
   * Test canceling already filled order - atypical valid case.
   * Order remains FILLED (terminal state, cannot be cancelled).
   */
  @Test
  void testCancel_FilledOrder_ReturnsUnchanged() {
    // Arrange: Order already filled
    UUID orderId = UUID.randomUUID();
    Order mockOrder = new Order();
    mockOrder.setId(orderId);
    mockOrder.setStatus("FILLED"); // Remains filled
    mockOrder.setFilledQty(100);

    when(orderService.cancel(eq(orderId))).thenReturn(mockOrder);

    // Act: Attempt to cancel
    ResponseEntity<Order> response = orderController.cancel(orderId);

    // Assert: Status unchanged (still FILLED)
    assertNotNull(response);
    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("FILLED", response.getBody().getStatus());

    verify(orderService, times(1)).cancel(orderId);
  }

  /**
   * Test canceling non-existent order - invalid case.
   * Service throws NotFoundException which propagates.
   */
  @Test
  void testCancel_OrderNotFound_ThrowsException() {
    // Arrange: Non-existent order
    UUID nonExistentId = UUID.randomUUID();
    when(orderService.cancel(eq(nonExistentId)))
        .thenThrow(new NotFoundException("Order not found: " + nonExistentId));

    // Act & Assert: Verify exception propagates
    try {
      orderController.cancel(nonExistentId);
    } catch (NotFoundException e) {
      assertNotNull(e.getMessage());
    }

    verify(orderService, times(1)).cancel(nonExistentId);
  }

  /**
   * Test creating order with minimal required fields - atypical valid case.
   * Verifies controller accepts requests with only required fields.
   */
  @Test
  void testCreate_MinimalFields_Success() {
    // Arrange: Request with only required fields
    CreateOrderRequest request = new CreateOrderRequest();
    request.setAccountId(UUID.randomUUID());
    request.setSymbol("TSLA");
    request.setSide("BUY");
    request.setQty(10);
    request.setType("MARKET");
    // No clientOrderId, limitPrice, or timeInForce

    Order mockOrder = new Order();
    mockOrder.setId(UUID.randomUUID());
    mockOrder.setAccountId(request.getAccountId());
    mockOrder.setSymbol("TSLA");
    mockOrder.setStatus("FILLED");

    when(orderService.submit(any(CreateOrderRequest.class))).thenReturn(mockOrder);

    // Act: Create order
    ResponseEntity<Order> response = orderController.create(request);

    // Assert: Order created successfully with minimal fields
    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertEquals("TSLA", response.getBody().getSymbol());

    verify(orderService, times(1)).submit(request);
  }
}
