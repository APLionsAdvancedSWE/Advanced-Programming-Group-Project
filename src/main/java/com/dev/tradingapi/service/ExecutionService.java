package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
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
 * Order execution service that creates orders, generates fills, and updates positions.
 */
@Service
public class ExecutionService {

  private final JdbcTemplate jdbcTemplate;
  private final MarketService marketService;
  private final RiskService riskService;

  /**
   * Constructs ExecutionService with required dependencies.
   *
   * @param jdbcTemplate database operations
   * @param marketService market quotes
   * @param riskService order validation
   */
  public ExecutionService(JdbcTemplate jdbcTemplate, MarketService marketService,
                         RiskService riskService) {
    this.jdbcTemplate = jdbcTemplate;
    this.marketService = marketService;
    this.riskService = riskService;
  }

  /**
   * Creates and executes an order with fills and position updates.
   *
   * <p>Dependencies: MarketService.getQuote(), RiskService.validate()
   *
   * @param req order creation request
   * @return created Order with FILLED status
   * @throws NotFoundException if market data unavailable
   * @throws RiskException if order violates risk limits
   */
  public Order createOrder(CreateOrderRequest req) {
    Quote mark = marketService.getQuote(req.getSymbol());
    if (mark == null) {
      throw new NotFoundException("Market data not available for symbol: " + req.getSymbol());
    }

    riskService.validate(req, mark);

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

    List<Fill> fills = generateFills(order, mark);
    updatePositions(order.getAccountId(), fills);
    updateOrderStatus(order, fills);

    return order;
  }

  /**
   * Generates fills for an order based on market conditions.
   * 
   * <p>For LIMIT orders: Validates that the limit price is acceptable before filling.
   * BUY limit orders fill only if limitPrice >= current market price.
   * SELL limit orders fill only if limitPrice <= current market price.
   * When filled, uses the limit price (better execution for the trader).
   * 
   * <p>For MARKET orders: Fills immediately at current market price.
   * 
   * <p>All orders respect available liquidity - may result in partial fills or no fills
   * if insufficient shares are available at the price.
   *
   * @param order the order to fill
   * @param mark current market quote
   * @return list of generated fills (may be empty if price not acceptable or no liquidity)
   */
  private List<Fill> generateFills(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    BigDecimal currentPrice = mark.getLast();
    Instant now = Instant.now();
    
    int fillQty = 0;
    BigDecimal fillPrice = currentPrice;
    
    if ("LIMIT".equalsIgnoreCase(order.getType()) && order.getLimitPrice() != null) {
      BigDecimal limitPrice = order.getLimitPrice();
      boolean priceAcceptable = false;
      
      if ("BUY".equalsIgnoreCase(order.getSide())) {
        priceAcceptable = limitPrice.compareTo(currentPrice) >= 0;
      } else if ("SELL".equalsIgnoreCase(order.getSide())) {
        priceAcceptable = limitPrice.compareTo(currentPrice) <= 0;
      }
      
      if (!priceAcceptable) {
        return fills;
      }
      
      fillPrice = limitPrice;
    }
    
    int availableQty = calculateAvailableLiquidity(order, mark);
    fillQty = Math.min(availableQty, order.getQty());
    
    if (fillQty > 0) {
      Fill fill = new Fill(UUID.randomUUID(), order.getId(), fillQty, fillPrice, now);
      fills.add(fill);
      saveFill(fill);
    }
    
    return fills;
  }

  /**
   * Calculates available liquidity at the current price.
   * 
   * <p>Simulates market depth by using 10% of the quote volume or a minimum of 50 shares,
   * whichever is larger. The result is capped at 10,000 shares to prevent unrealistic
   * fill quantities. This ensures orders may receive partial fills or no fills when
   * market liquidity is limited.
   *
   * @param order the order requesting liquidity
   * @param mark current market quote
   * @return available quantity at the price (between 50 and 10,000 shares)
   */
  private int calculateAvailableLiquidity(Order order, Quote mark) {
    long volume = mark.getVolume();
    int availableFromVolume = (int) (volume * 0.1);
    int minLiquidity = 50;
    
    return Math.max(minLiquidity, Math.min(availableFromVolume, 10000));
  }

  /**
   * Updates positions for an account based on fills.
   *
   * @param accountId account identifier
   * @param fills list of fills to process
   */
  private void updatePositions(UUID accountId, List<Fill> fills) {
    for (Fill fill : fills) {
      // Get the order to find the symbol
      Order order = getOrder(fill.getOrderId());
      String symbol = order.getSymbol();
      
      Position existing = getPosition(accountId, symbol);
      if (existing == null) {
        existing = new Position(accountId, symbol, 0, BigDecimal.ZERO);
      }

      existing.updateWithFill(fill.getQty(), fill.getPrice());
      savePosition(existing);
    }
  }

  /**
   * Updates order status based on fills.
   * 
   * <p>Calculates total filled quantity and average fill price from the provided fills.
   * Sets order status dynamically:
   * - WORKING: No fills occurred (order remains in market)
   * - PARTIALLY_FILLED: Some fills but less than order quantity
   * - FILLED: Total fills meet or exceed order quantity
   *
   * @param order the order to update
   * @param fills list of fills (may be empty)
   */
  private void updateOrderStatus(Order order, List<Fill> fills) {
    int totalFilled = fills.stream().mapToInt(Fill::getQty).sum();
    BigDecimal totalCost = fills.stream()
        .map(fill -> fill.getPrice().multiply(BigDecimal.valueOf(fill.getQty())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);

    order.setFilledQty(totalFilled);
    if (totalFilled > 0) {
      order.setAvgFillPrice(totalCost.divide(BigDecimal.valueOf(totalFilled),
          RoundingMode.HALF_UP));
    }

    if (totalFilled == 0) {
      if ("NEW".equals(order.getStatus())) {
        order.setStatus("WORKING");
      }
    } else if (totalFilled >= order.getQty()) {
      order.setStatus("FILLED");
    } else {
      order.setStatus("PARTIALLY_FILLED");
    }

    updateOrder(order);
  }

  /**
   * Saves an order to the database.
   *
   * @param order the order to save
   */
  private void saveOrder(Order order) {
    String sql = "INSERT INTO orders (id, account_id, client_order_id, symbol, side, qty, "
        + "type, limit_price, time_in_force, status, filled_qty, avg_fill_price, created_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.update(sql, order.getId(), order.getAccountId(), order.getClientOrderId(),
        order.getSymbol(), order.getSide(), order.getQty(), order.getType(),
        order.getLimitPrice(), order.getTimeInForce(), order.getStatus(),
        order.getFilledQty(), order.getAvgFillPrice(), order.getCreatedAt());
  }

  /**
   * Updates an existing order in the database.
   *
   * @param order the order to update
   */
  private void updateOrder(Order order) {
    String sql = "UPDATE orders SET status = ?, filled_qty = ?, avg_fill_price = ? WHERE id = ?";
    jdbcTemplate.update(sql, order.getStatus(), order.getFilledQty(),
        order.getAvgFillPrice(), order.getId());
  }

  /**
   * Saves a fill to the database.
   *
   * @param fill the fill to save
   */
  private void saveFill(Fill fill) {
    String sql = "INSERT INTO fills (id, order_id, qty, price, ts) VALUES (?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql, fill.getId(), fill.getOrderId(), fill.getQty(),
        fill.getPrice(), fill.getTs());
  }

  /**
   * Gets an order by ID.
   *
   * @param orderId order identifier
   * @return order or null if not found
   */
  private Order getOrder(UUID orderId) {
    String sql = "SELECT * FROM orders WHERE id = ?";
    try {
      return jdbcTemplate.queryForObject(sql, new OrderMapper(), orderId);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Gets a position for an account and symbol.
   *
   * @param accountId account identifier
   * @param symbol trading symbol
   * @return position or null if not found
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
   * Saves a position to the database.
   *
   * @param position the position to save
   */
  private void savePosition(Position position) {
    String sql = "INSERT INTO positions (account_id, symbol, qty, avg_cost) "
        + "VALUES (?, ?, ?, ?) ON CONFLICT (account_id, symbol) "
        + "DO UPDATE SET qty = ?, avg_cost = ?";

    jdbcTemplate.update(sql, position.getAccountId(), position.getSymbol(),
        position.getQty(), position.getAvgCost(), position.getQty(),
        position.getAvgCost());
  }

  /**
   * Row mapper for Order objects.
   */
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

  /**
   * Row mapper for Fill objects.
   */
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

  /**
   * Row mapper for Position objects.
   */
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