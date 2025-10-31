package com.dev.tradingapi.controller;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.service.OrderService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller exposing Order APIs.
 * Endpoints:
 * POST /orders — create a new order (MARKET or TWAP)
 * GET /orders/{orderId} — fetch a specific order
 * GET /orders/{orderId}/fills — list fills for an order
 * POST /orders/{orderId}:cancel — cancel an active order
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

  /**
   * Constructs the controller with its service dependency.
   *
   * @param orderService business service for order lifecycle
   */
  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  /**
   * Creates a new order.
   * Supports MARKET orders (single immediate fill) or TWAP (split into equal
   * chunks).
   *
   * @param req JSON payload describing the order to create
   * @return the created order with status and fill aggregates
   */
  @PostMapping
  public ResponseEntity<Order> create(@RequestBody CreateOrderRequest req) {
    // Delegate to service to validate risk, get market data, persist, and generate
    // fills
    Order order = orderService.submit(req);
    return new ResponseEntity<>(order, HttpStatus.CREATED);
  }

  /**
   * Retrieves a specific order by ID.
   *
   * @param orderId the order identifier
   * @return the order details
   */
  @GetMapping("/{orderId}")
  public ResponseEntity<Order> getOrder(@PathVariable("orderId") UUID orderId) {
    Order order = orderService.getOrder(orderId);
    return ResponseEntity.ok(order);
  }

  /**
   * Retrieves the most recent order by clientOrderId.
   * Useful for demos where client uses their own ID.
   *
   * @param clientOrderId the external client order identifier
   * @return the latest matching order
   */
  @GetMapping("/by-client/{clientOrderId}")
  public ResponseEntity<Order> getOrderByClientOrderId(@PathVariable("clientOrderId") String clientOrderId) {
    Order order = orderService.getOrderByClientOrderId(clientOrderId);
    return ResponseEntity.ok(order);
  }

  /**
   * Lists all fills belonging to a specific order.
   *
   * @param orderId the order identifier
   * @return list of fills, possibly empty
   */
  @GetMapping("/{orderId}/fills")
  public ResponseEntity<List<Fill>> getFills(@PathVariable("orderId") UUID orderId) {
    List<Fill> fills = orderService.getFills(orderId);
    return ResponseEntity.ok(fills);
  }

  /**
   * Cancels an active order if possible.
   *
   * @param orderId the order identifier
   * @return the updated order (CANCELLED if success, unchanged if already
   *         terminal)
   */
  @PostMapping("/{orderId}:cancel")
  public ResponseEntity<Order> cancel(@PathVariable("orderId") UUID orderId) {
    Order cancelled = orderService.cancel(orderId);
    return ResponseEntity.ok(cancelled);
  }
}
// 1