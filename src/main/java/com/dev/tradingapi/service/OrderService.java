package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.model.Fill;
import com.dev.tradingapi.model.Order;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.repository.FillRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
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
  private final FillRepository fillRepository;
  private final AccountService accountService;

  /**
   * Constructs the service.
   *
   * @param jdbcTemplate  database access helper
   * @param marketService quote provider used for pricing fills
   * @param riskService   pre-trade risk validator
   * @param fillRepository repository for fill operations
   * @param accountService service managing account balances
   */
  public OrderService(JdbcTemplate jdbcTemplate, MarketService marketService,
                      RiskService riskService, FillRepository fillRepository,
                      AccountService accountService) {
    this.jdbcTemplate = jdbcTemplate;
    this.marketService = marketService;
    this.riskService = riskService;
    this.fillRepository = fillRepository;
    this.accountService = accountService;
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

    // 4) execution model: MARKET -> single fill; LIMIT -> check price and liquidity;
    // TWAP -> equal slices
    List<Fill> fills;
    if ("TWAP".equalsIgnoreCase(req.getType())) {
      fills = generateTwapFills(order, mark);
    } else if ("LIMIT".equalsIgnoreCase(req.getType())) {
      fills = generateLimitFill(order, mark);
    } else {
      fills = generateMarketFill(order, mark);
    }

    // 5) update cash balance, positions, and order status based on fills
    updateCashBalance(order.getAccountId(), order.getSide(), fills);
    updatePositions(order.getAccountId(), order.getSide(), order.getSymbol(), fills);
    updateOrderStatus(order, fills);
    return order;
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

  // ---------- Helpers ----------

  /**
   * Generates a single market fill at the last price with available liquidity.
   * 
   * <p>Respects available market liquidity - may result in partial fills if insufficient
   * shares are available. Returns empty list if no liquidity is available.
   */
  private List<Fill> generateMarketFill(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    BigDecimal price = mark.getLast();
    
    int availableQty = calculateAvailableLiquidity(mark);
    int fillQty = Math.min(availableQty, order.getQty());
    
    if (fillQty > 0) {
      Fill fill = new Fill(UUID.randomUUID(), order.getId(), fillQty, price, Instant.now());
      fillRepository.save(fill);
      fills.add(fill);
    }
    
    return fills;
  }

  /**
   * Generates fills for a LIMIT order based on price acceptance and available liquidity.
   * 
   * <p>For BUY orders: Fills only if limitPrice >= current market price (trader willing
   * to pay at least the current price).
   * 
   * <p>For SELL orders: Fills only if limitPrice <= current market price (trader willing
   * to sell at most the current price).
   * 
   * <p>When filled, uses the limit price for execution (better price for the trader).
   * Respects available liquidity - may result in partial fills or no fills if price
   * is not acceptable or insufficient shares are available.
   */
  private List<Fill> generateLimitFill(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    
    if (order.getLimitPrice() == null) {
      return fills;
    }
    
    BigDecimal currentPrice = mark.getLast();
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
    
    int availableQty = calculateAvailableLiquidity(mark);
    int fillQty = Math.min(availableQty, order.getQty());
    
    if (fillQty > 0) {
      Fill fill = new Fill(UUID.randomUUID(), order.getId(), fillQty, limitPrice, Instant.now());
      fillRepository.save(fill);
      fills.add(fill);
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
   * @param mark current market quote
   * @return available quantity at the price (between 50 and 10,000 shares)
   */
  private int calculateAvailableLiquidity(Quote mark) {
    long volume = mark.getVolume();
    int availableFromVolume = (int) (volume * 0.1);
    int minLiquidity = 50;
    
    return Math.max(minLiquidity, Math.min(availableFromVolume, 10000));
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
      fillRepository.save(f);
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
    int signed = "SELL".equalsIgnoreCase(side) ? -1 : 1;
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
   * Updates order aggregate fields (filledQty, avgFillPrice, status) and persists them.
   * 
   * <p>Calculates total filled quantity and average fill price from the provided fills.
   * Sets order status dynamically:
   * - WORKING: No fills occurred (order remains in market)
   * - PARTIALLY_FILLED: Some fills but less than order quantity
   * - FILLED: Total fills meet or exceed order quantity
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
   * Persists a new order row.
   */
  private void saveOrder(Order order) {
    String sql = "INSERT INTO orders (id, account_id, client_order_id, symbol, side, qty, type, "
        + "limit_price, time_in_force, status, filled_qty, avg_fill_price, created_at) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql, order.getId(), order.getAccountId(), order.getClientOrderId(),
        order.getSymbol(), order.getSide(), order.getQty(), order.getType(),
        order.getLimitPrice(), order.getTimeInForce(), order.getStatus(),
        order.getFilledQty(), order.getAvgFillPrice(),
        Timestamp.from(order.getCreatedAt()));
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
    String sqlPg = "INSERT INTO positions (account_id, symbol, qty, avg_cost) VALUES (?, ?, ?, ?) "
        + "ON CONFLICT (account_id, symbol) DO UPDATE SET qty = EXCLUDED.qty,"
        + " avg_cost = EXCLUDED.avg_cost";
    try {
      jdbcTemplate.update(sqlPg, position.getAccountId(), position.getSymbol(),
          position.getQty(), position.getAvgCost());
    } catch (Exception e) {
      // Fallback for H2 or databases without ON CONFLICT support
      String sqlMerge = "MERGE INTO positions KEY(account_id, symbol) VALUES (?, ?, ?, ?)";
      jdbcTemplate.update(sqlMerge, position.getAccountId(), position.getSymbol(),
          position.getQty(), position.getAvgCost());
    }
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