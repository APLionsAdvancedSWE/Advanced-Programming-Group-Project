package com.dev.tradingapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.service.OrderService;
import java.util.List;
import java.util.UUID;
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
    assertEquals("FILLED", order.getStatus());
    assertEquals(50, order.getFilledQty());

    // Verify order row persisted
    Order storedOrder = jdbcTemplate.queryForObject(
        "SELECT * FROM orders WHERE id = ?",
        new BeanPropertyRowMapper<>(Order.class),
        order.getId());
    assertNotNull(storedOrder);

    // Verify fills persisted
    List<Fill> fills = jdbcTemplate.query(
        "SELECT * FROM fills WHERE order_id = ?",
        new BeanPropertyRowMapper<>(Fill.class),
        order.getId());
    assertNotNull(fills);
    int totalFillQty = fills.stream().mapToInt(Fill::getQty).sum();
    assertEquals(50, totalFillQty);

    // Verify position updated (check qty directly to avoid model mapping issues)
    Integer positionQty = jdbcTemplate.queryForObject(
        "SELECT qty FROM positions WHERE account_id = ? AND symbol = ?",
        Integer.class,
        accountId,
        "IBM");
    assertNotNull(positionQty);
    assertEquals(50, positionQty.intValue());
  }
}
