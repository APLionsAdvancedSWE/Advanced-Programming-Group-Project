package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
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
  private final MarketService marketService;
  private final RiskService riskService;

  /**
   * Constructs the service.
   *
   * @param jdbcTemplate  database access helper
   * @param marketService quote provider used for pricing fills
   * @param riskService   pre-trade risk validator
   */
  public OrderService(JdbcTemplate jdbcTemplate, MarketService marketService,
      RiskService riskService) {
    this.jdbcTemplate = jdbcTemplate;
    this.marketService = marketService;
    this.riskService = riskService;
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
   * @return persisted order with updated status and averages
   */
  public Order submit(CreateOrderRequest req) {
    // 1) get a price snapshot; 404 if symbol not available
    Quote mark = marketService.getQuote(req.getSymbol());
    if (mark == null) {
      throw new NotFoundException("Market data not available for symbol: " + req.getSymbol());
    }

    // 2) pre-trade risk validation (size/notional/etc.)
    riskService.validate(req, mark);

    // 3) create and persist the order record
    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setAccountId(req.getAccountId());
    order.setClientOrderId(req.getClientOrderId());
    order.setSymbol(req.getSymbol());
    order.setSide(req.getSide());
    order.setQty(req.getQty());
    order.setType(req.getType());
    order.setLimitPrice(req.getLimitPrice());
    order.setTimeInForce(req.getTimeInForce());
    order.setStatus("NEW");
    order.setFilledQty(0);
    order.setAvgFillPrice(BigDecimal.ZERO);
    order.setCreatedAt(Instant.now());
    saveOrder(order);

    // 4) execution model: MARKET -> single fill; TWAP -> equal slices
    List<Fill> fills;
    if ("TWAP".equalsIgnoreCase(req.getType())) {
      fills = generateTwapFills(order, mark);
    } else {
      fills = generateMarketFill(order, mark);
    }

    // 5) update positions and order status based on fills
    updatePositions(order.getAccountId(), order.getSide(), order.getSymbol(), fills);
    updateOrderStatus(order, fills);
    return order;
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
      throw new NotFoundException("Order not found: " + orderId);
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
    String sql = "SELECT * FROM fills WHERE order_id = ? ORDER BY ts ASC";
    return jdbcTemplate.query(sql, new FillMapper(), orderId);
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

  // ---------- Helpers ----------

  /**
   * Generates a single market fill at the last price.
   */
  private List<Fill> generateMarketFill(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    BigDecimal price = mark.getLast();
    Fill fill = new Fill(UUID.randomUUID(), order.getId(), order.getQty(), price, Instant.now());
    saveFill(fill);
    fills.add(fill);
    return fills;
  }

  /**
   * Generates multiple TWAP slices with near-equal quantities at the current
   * price.
   */
  private List<Fill> generateTwapFills(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    int total = order.getQty();
    // at least 2 slices and cap at 10 to keep it simple for the mock
    int slices = Math.min(10, Math.max(2, total));
    int base = total / slices;
    int rem = total % slices; // distribute remainders to the first 'rem' slices
    BigDecimal price = mark.getLast();
    Instant now = Instant.now();
    for (int i = 0; i < slices; i++) {
      int q = base + (i < rem ? 1 : 0);
      if (q <= 0) {
        continue;
      }
      Fill f = new Fill(UUID.randomUUID(), order.getId(), q, price, now);
      saveFill(f);
      fills.add(f);
    }
    return fills;
  }

  /**
   * Applies fills to the account's position for the symbol.
   *
   * <p>
   * SELL fills reduce the position (negative sign), BUY fills increase it.
   * </p>
   */
  private void updatePositions(UUID accountId, String side, String symbol, List<Fill> fills) {
    int signed = ("SELL".equalsIgnoreCase(side)) ? -1 : 1;
    Position pos = getPosition(accountId, symbol);
    if (pos == null) {
      pos = new Position(accountId, symbol, 0, BigDecimal.ZERO);
    }
    for (Fill f : fills) {
      pos.updateWithFill(signed * f.getQty(), f.getPrice());
    }
    savePosition(pos);
  }

  /**
   * Updates order aggregate fields (filledQty, avgFillPrice, status) and persists
   * them.
   */
  private void updateOrderStatus(Order order, List<Fill> fills) {
    int totalFilled = fills.stream().mapToInt(Fill::getQty).sum();
    BigDecimal totalCost = fills.stream()
        .map(f -> f.getPrice().multiply(BigDecimal.valueOf(f.getQty())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    order.setFilledQty(totalFilled);
    if (totalFilled > 0) {
      order.setAvgFillPrice(totalCost.divide(BigDecimal.valueOf(totalFilled), 
          RoundingMode.HALF_UP));
    }
    order.setStatus(totalFilled >= order.getQty() ? "FILLED" : "PARTIALLY_FILLED");
    updateOrder(order);
  }

  /**
   * Persists a new order row.
   */
  private void saveOrder(Order order) {
    String sql = "INSERT INTO orders (id, account_id, client_order_id, symbol, side, qty, type, "
        + "limit_price, time_in_force, status, filled_qty, avg_fill_price, created_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql, order.getId(), order.getAccountId(), order.getClientOrderId(),
        order.getSymbol(), order.getSide(), order.getQty(), order.getType(),
        order.getLimitPrice(), order.getTimeInForce(), order.getStatus(),
        order.getFilledQty(), order.getAvgFillPrice(), order.getCreatedAt());
  }

  /**
   * Persists updates to an existing order row.
   */
  private void updateOrder(Order order) {
    String sql = "UPDATE orders SET status = ?, filled_qty = ?, avg_fill_price = ? WHERE id = ?";
    jdbcTemplate.update(sql, order.getStatus(), order.getFilledQty(),
        order.getAvgFillPrice(), order.getId());
  }

  /**
   * Persists a new fill row.
   */
  private void saveFill(Fill fill) {
    String sql = "INSERT INTO fills (id, order_id, qty, price, ts) VALUES (?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql, fill.getId(), fill.getOrderId(), fill.getQty(),
        fill.getPrice(), fill.getTs());
  }

  /**
   * Fetches the current position for account+symbol, or null if absent.
   */
  private Position getPosition(UUID accountId, String symbol) {
    String sql = "SELECT * FROM positions WHERE account_id = ? AND symbol = ?";
    try {
      return jdbcTemplate.queryForObject(sql, new PositionMapper(), accountId, symbol);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Inserts or updates a position row (upsert).
   */
  private void savePosition(Position position) {
    String sql = "INSERT INTO positions (account_id, symbol, qty, avg_cost) VALUES (?, ?, ?, ?) "
        + "ON CONFLICT (account_id, symbol) DO UPDATE SET qty = EXCLUDED.qty,"
        + " avg_cost = EXCLUDED.avg_cost";
    jdbcTemplate.update(sql, position.getAccountId(), position.getSymbol(),
        position.getQty(), position.getAvgCost());
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

  /** Maps a row from the fills table to a Fill object. */
  private static class FillMapper implements RowMapper<Fill> {
    @Override
    public Fill mapRow(ResultSet rs, int rowNum) throws SQLException {
      Fill fill = new Fill();
      fill.setId(rs.getObject("id", UUID.class));
      fill.setOrderId(rs.getObject("order_id", UUID.class));
      fill.setQty(rs.getInt("qty"));
      fill.setPrice(rs.getBigDecimal("price"));
      fill.setTs(rs.getTimestamp("ts").toInstant());
      return fill;
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