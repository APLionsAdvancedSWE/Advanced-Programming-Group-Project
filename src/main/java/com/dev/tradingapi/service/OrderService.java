package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.repository.FillRepository;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

/**
 * Order lifecycle service implemented with Spring's JdbcTemplate.
 *
 * <p>
 * Responsibilities:
 * </p>
 * <ul>
 * <li>Submit orders (MARKET, TWAP) with risk validation and market pricing</li>
 * <li>Persist orders and fills, update positions</li>
 * <li>Query order details and fills</li>
 * <li>Cancel active orders</li>
 * </ul>
 */
@Service
public class OrderService {

  private final JdbcTemplate jdbcTemplate;
  private final FillRepository fillRepository;
  private final AccountService accountService;
  private final ExecutionService executionService;

  /**
   * Constructs the service.
   *
   * @param jdbcTemplate  database access helper
   * @param fillRepository repository for fill operations
   * @param accountService service managing account balances
   * @param executionService matching engine used for order execution
   */
  public OrderService(JdbcTemplate jdbcTemplate, FillRepository fillRepository,
                      AccountService accountService, ExecutionService executionService) {
    this.jdbcTemplate = jdbcTemplate;
    this.fillRepository = fillRepository;
    this.accountService = accountService;
    this.executionService = executionService;
  }

  /**
   * Submits a new order.
   *
   * <p>
   * Behavior:
   * </p>
   * <ol>
   * <li>Fetch current market price for the symbol</li>
   * <li>Validate risk constraints</li>
   * <li>Persist the order with NEW status</li>
   * <li>Generate fills (single for MARKET, multiple for TWAP)</li>
   * <li>Update positions and order status (FILLED/PARTIALLY_FILLED)</li>
   * </ol>
   *
   * @param req order creation request
   * @return list of persisted orders (single order for non-TWAP, child orders for TWAP)
   */
  public List<Order> submit(CreateOrderRequest req) {
    // Delegate order creation and matching to the execution engine.
    List<Order> orders = executionService.createOrder(req);

    // Adjust account cash based on all fills for all orders.
    // For TWAP, aggregate fills from all child orders
    UUID accountId = orders.get(0).getAccountId();
    String side = orders.get(0).getSide();
    List<Fill> allFills = new ArrayList<>();
    for (Order order : orders) {
      List<Fill> fills = fillRepository.findByOrderId(order.getId());
      if (fills != null) {
        allFills.addAll(fills);
      }
    }
    updateCashBalance(accountId, side, allFills);

    return orders;
  }

  /**
   * Updates the account cash balance based on the executed fills.
   *
   * <p>BUY orders reduce cash (debit), SELL orders increase cash (credit).
   * The adjustment is the signed notional: sum(price × qty) with sign
   * -1 for BUY and +1 for SELL.</p>
   */
  private void updateCashBalance(UUID accountId, String side, List<Fill> fills) {
    if (fills == null || fills.isEmpty()) {
      return;
    }

    int sign = "SELL".equalsIgnoreCase(side) ? 1 : -1;
    BigDecimal totalNotional = fills.stream()
        .map(f -> f.getPrice().multiply(BigDecimal.valueOf(f.getQty())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (totalNotional.compareTo(BigDecimal.ZERO) == 0) {
      return;
    }

    BigDecimal delta = totalNotional.multiply(BigDecimal.valueOf(sign));
    accountService.adjustCash(accountId, delta);
  }

  /**
   * Returns a single order by its ID.
   *
   * @param orderId order identifier
   * @return the order if found
   * @throws NotFoundException if not found
   */
  public Order getOrder(UUID orderId) {
    String sql = "SELECT * FROM orders WHERE id = ?";
    try {
      return jdbcTemplate.queryForObject(sql, new OrderMapper(), orderId);
    } catch (Exception e) {
      throw new NotFoundException("Order not found: " + orderId, e);
    }
  }

  /**
   * Returns the most recent order matching the given clientOrderId.
   *
   * @param clientOrderId external client-provided identifier
   * @return the latest matching order
   * @throws NotFoundException if none found
   */
  public Order getOrderByClientOrderId(String clientOrderId) {
    String sql = "SELECT * FROM orders WHERE client_order_id = ? ORDER BY created_at DESC LIMIT 1";
    try {
      return jdbcTemplate.queryForObject(sql, new OrderMapper(), clientOrderId);
    } catch (Exception e) {
      throw new NotFoundException("Order not found for clientOrderId: " + clientOrderId, e);
    }
  }

  /**
   * Returns all fills associated with an order, ordered by timestamp ascending.
   *
   * @param orderId order identifier
   * @return list of fills (possibly empty)
   * @throws NotFoundException if the order does not exist
   */
  public List<Fill> getFills(UUID orderId) {
    // Ensure the order exists (throws 404 if not)
    getOrder(orderId);
    return fillRepository.findByOrderId(orderId);
  }

  /**
   * Attempts to cancel an order if it's not already terminal.
   *
   * @param orderId order identifier
   * @return updated order (status may become CANCELLED)
   */
  public Order cancel(UUID orderId) {
    Order order = getOrder(orderId);
    // If already terminal, return as-is
    if ("FILLED".equals(order.getStatus()) || "CANCELLED".equals(order.getStatus())) {
      return order;
    }

    order.setStatus("CANCELLED");
    updateOrder(order);
    return order;
  }

  /**
   * Persists updates to an existing order row.
   */
  private void updateOrder(Order order) {
    String sql = "UPDATE orders SET status = ?, filled_qty = ?, avg_fill_price = ? WHERE id = ?";
    jdbcTemplate.update(sql, order.getStatus(), order.getFilledQty(),
        order.getAvgFillPrice(), order.getId());
  }

  // ---------- RowMappers ----------

  /** Maps a row from the orders table to an Order object. */
  private static class OrderMapper implements RowMapper<Order> {
    @Override
    public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
      Order order = new Order();
      order.setId(rs.getObject("id", UUID.class));
      order.setAccountId(rs.getObject("account_id", UUID.class));
      order.setClientOrderId(rs.getString("client_order_id"));
      order.setSymbol(rs.getString("symbol"));
      order.setSide(rs.getString("side"));
      order.setQty(rs.getInt("qty"));
      order.setType(rs.getString("type"));
      order.setLimitPrice(rs.getBigDecimal("limit_price"));
      order.setTimeInForce(rs.getString("time_in_force"));
      order.setStatus(rs.getString("status"));
      order.setFilledQty(rs.getInt("filled_qty"));
      order.setAvgFillPrice(rs.getBigDecimal("avg_fill_price"));
      order.setCreatedAt(rs.getTimestamp("created_at").toInstant());
      return order;
    }
  }

  /** Maps a row from the positions table to a Position object. */
  private static class PositionMapper implements RowMapper<Position> {
    @Override
    public Position mapRow(ResultSet rs, int rowNum) throws SQLException {
      UUID accountId = rs.getObject("account_id", UUID.class);
      String symbol = rs.getString("symbol");
      int qty = rs.getInt("qty");
      BigDecimal avgCost = rs.getBigDecimal("avg_cost");
      return new Position(accountId, symbol, qty, avgCost);
    }
  }
}