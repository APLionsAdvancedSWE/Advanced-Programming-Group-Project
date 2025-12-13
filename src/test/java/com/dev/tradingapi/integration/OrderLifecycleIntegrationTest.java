package com.dev.tradingapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.service.OrderService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests that exercise the order lifecycle across services and the
 * real database schema.
 *
 * <p>This uses the full Spring Boot context and the configured test database
 * (H2/Postgres with Flyway migrations) to verify that orders, fills and
 * positions are persisted and aggregated correctly.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class OrderLifecycleIntegrationTest {

  @Autowired
  private OrderService orderService;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void clearOrderBook() {
    // Ensure each test starts with a clean order book so that
    // demo data or previous tests do not affect matching.
    jdbcTemplate.update("DELETE FROM fills");
    jdbcTemplate.update("DELETE FROM orders");
  }

  @Test
  void submitMarketOrder_persistsOrderFillsAndPositions() {
    UUID accountId = UUID.randomUUID();

    // Insert a basic account row directly via SQL so the foreign key exists
    jdbcTemplate.update(
        "INSERT INTO accounts (id, name, auth_token, username, password_hash, max_order_qty, "
            + "max_notional, max_position_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        accountId,
        "Integration Test Account",
        "test-api-key",
        "it_user",
        "hash",
        10000,
        1_000_000,
        100_000);

    CreateOrderRequest req = new CreateOrderRequest();
    req.setAccountId(accountId);
    req.setClientOrderId("it-client-1");
    req.setSymbol("IBM");
    req.setSide("BUY");
    req.setQty(50);
    req.setType("MARKET");
    req.setTimeInForce("DAY");

    Order order = orderService.submit(req);

    assertNotNull(order);
    assertEquals("IBM", order.getSymbol());
    // With a pure order-book engine and no opposing SELL orders,
    // a first BUY MARKET order will rest WORKING with 0 fills.
    assertEquals("WORKING", order.getStatus());
    assertEquals(0, order.getFilledQty());

    // Verify order row persisted
    Order storedOrder = jdbcTemplate.queryForObject(
        "SELECT * FROM orders WHERE id = ?",
        new BeanPropertyRowMapper<>(Order.class),
        order.getId());
    assertNotNull(storedOrder);

    // Verify no fills yet (no opposing liquidity in the book)
    List<Fill> fills = jdbcTemplate.query(
        "SELECT * FROM fills WHERE order_id = ?",
        new BeanPropertyRowMapper<>(Fill.class),
        order.getId());
    assertNotNull(fills);
    int totalFillQty = fills.stream().mapToInt(Fill::getQty).sum();
    assertEquals(0, totalFillQty);

    // Verify position has not changed yet: either no row or zero qty.
    List<Integer> positionQtys = jdbcTemplate.query(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        (rs, rowNum) -> rs.getInt("qty"),
        accountId,
        "IBM");
    assertTrue(positionQtys.isEmpty() || positionQtys.get(0) == 0);
  }

  @Test
  void limitBuy_matchesRestingSell_createsFillsAndPosition() {
    UUID buyerId = UUID.randomUUID();
    UUID sellerId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO accounts (id, name, auth_token, username, password_hash, max_order_qty, "
            + "max_notional, max_position_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        buyerId, "Buyer", "buyer-key", "buyer", "hash", 10000, 1_000_000, 100_000);

    jdbcTemplate.update(
        "INSERT INTO accounts (id, name, auth_token, username, password_hash, max_order_qty, "
            + "max_notional, max_position_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        sellerId, "Seller", "seller-key", "seller", "hash", 10000, 1_000_000, 100_000);

    // Seed seller position so they can sell 100 IBM
    jdbcTemplate.update(
        "INSERT INTO positions (account_id, symbol, qty, avg_cost) VALUES (?, ?, ?, ?)",
        sellerId, "IBM", 100, 100.00);

    // Resting SELL LIMIT @ 100
    CreateOrderRequest restingSellReq = new CreateOrderRequest();
    restingSellReq.setAccountId(sellerId);
    restingSellReq.setClientOrderId("rest-sell-1");
    restingSellReq.setSymbol("IBM");
    restingSellReq.setSide("SELL");
    restingSellReq.setQty(100);
    restingSellReq.setType("LIMIT");
    restingSellReq.setLimitPrice(new java.math.BigDecimal("100.00"));
    restingSellReq.setTimeInForce("DAY");
    Order restingSell = orderService.submit(restingSellReq);
    assertEquals("WORKING", restingSell.getStatus());

    // Incoming BUY LIMIT @ 101 should match that resting SELL
    CreateOrderRequest buyReq = new CreateOrderRequest();
    buyReq.setAccountId(buyerId);
    buyReq.setClientOrderId("buy-1");
    buyReq.setSymbol("IBM");
    buyReq.setSide("BUY");
    buyReq.setQty(100);
    buyReq.setType("LIMIT");
    buyReq.setLimitPrice(new java.math.BigDecimal("101.00"));
    buyReq.setTimeInForce("DAY");

    Order buyOrder = orderService.submit(buyReq);
    assertEquals("FILLED", buyOrder.getStatus());
    assertEquals(100, buyOrder.getFilledQty());

    // Both sides should now be FILLED at the resting price 100.00
    Order updatedSell = jdbcTemplate.queryForObject(
        "SELECT * FROM orders WHERE id = ?",
        new BeanPropertyRowMapper<>(Order.class),
        restingSell.getId());
    assertNotNull(updatedSell);
    assertEquals("FILLED", updatedSell.getStatus());

    List<Fill> buyFills = jdbcTemplate.query(
        "SELECT * FROM fills WHERE order_id = ?",
        new BeanPropertyRowMapper<>(Fill.class),
        buyOrder.getId());
    assertEquals(1, buyFills.size());
    assertEquals(100, buyFills.get(0).getQty());
    assertEquals(0, buyFills.get(0).getPrice().compareTo(new java.math.BigDecimal("100.00")));

    // Buyer position should now be +100 IBM
    Integer buyerPosQty = jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        Integer.class,
        buyerId,
        "IBM");
    assertEquals(100, buyerPosQty.intValue());

    // Seller position should now be fully closed; the row may be deleted.
    List<Integer> sellerPosQtys = jdbcTemplate.query(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        (rs, rowNum) -> rs.getInt("qty"),
        sellerId,
        "IBM");
    assertTrue(sellerPosQtys.isEmpty() || sellerPosQtys.get(0) == 0);
  }

  @Test
  void twapBuy_createsMultipleFillsAndPosition() {
    UUID accountId = UUID.randomUUID();
    UUID sellerAccountId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO accounts (id, name, auth_token, username, password_hash, max_order_qty, "
            + "max_notional, max_position_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        accountId,
        "TWAP Account",
        "twap-key",
        "twap_user",
        "hash",
        10000,
        1_000_000,
        100_000);

    jdbcTemplate.update(
        "INSERT INTO accounts (id, name, auth_token, username, password_hash, max_order_qty, "
            + "max_notional, max_position_qty) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        sellerAccountId,
        "Seller Account",
        "seller-key",
        "seller_user",
        "hash",
        10000,
        1_000_000,
        100_000);

    // Create matching SELL orders in the book for TWAP to match against
    // TWAP splits 25 into slices of 10, 10, 5 - need matching SELL orders for each slice
    // Create enough orders to fill all 25 shares
    for (int i = 0; i < 3; i++) {
      UUID sellOrderId = UUID.randomUUID();
      int qty = (i < 2) ? 10 : 5;
      jdbcTemplate.update(
          "INSERT INTO orders (id, account_id, client_order_id, symbol, side, qty, type, "
              + "limit_price, time_in_force, status, filled_qty, avg_fill_price, created_at) "
              + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          sellOrderId,
          sellerAccountId,
          "sell-" + i,
          "IBM",
          "SELL",
          qty,
          "MARKET",
          null,
          "DAY",
          "WORKING",
          0,
          null,
          java.sql.Timestamp.from(java.time.Instant.now()));
    }

    CreateOrderRequest twapReq = new CreateOrderRequest();
    twapReq.setAccountId(accountId);
    twapReq.setClientOrderId("twap-1");
    twapReq.setSymbol("IBM");
    twapReq.setSide("BUY");
    twapReq.setQty(25);
    twapReq.setType("TWAP");
    twapReq.setTimeInForce("DAY");

    Order twapOrder = orderService.submit(twapReq);
    // With matching orders, TWAP should fill completely
    assertEquals("FILLED", twapOrder.getStatus());
    assertEquals(25, twapOrder.getFilledQty());

    List<Fill> twapFills = jdbcTemplate.query(
        "SELECT * FROM fills WHERE order_id = ?",
        new BeanPropertyRowMapper<>(Fill.class),
        twapOrder.getId());
    assertTrue(twapFills.size() >= 2);
    int totalQty = twapFills.stream().mapToInt(Fill::getQty).sum();
    assertEquals(25, totalQty);

    Integer posQty = jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        Integer.class,
        accountId,
        "IBM");
    assertEquals(25, posQty.intValue());
  }
}
