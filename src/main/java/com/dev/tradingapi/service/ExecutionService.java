package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
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
 * Order execution service that creates orders, generates fills, and updates positions.
 */
@Service
public class ExecutionService {

  private final JdbcTemplate jdbcTemplate;
  private final MarketService marketService;
  private final RiskService riskService;
  private final FillRepository fillRepository;

  /**
   * Constructs ExecutionService with required dependencies.
   *
   * @param jdbcTemplate database operations
   * @param marketService market quotes
   * @param riskService order validation
   * @param fillRepository repository for fill operations
   */
  public ExecutionService(JdbcTemplate jdbcTemplate, MarketService marketService,
                         RiskService riskService, FillRepository fillRepository) {
    this.jdbcTemplate = jdbcTemplate;
    this.marketService = marketService;
    this.riskService = riskService;
    this.fillRepository = fillRepository;
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

    // Validate order type and limitPrice combination
    // MARKET orders should not have a limitPrice (they execute at best available price)
    // LIMIT orders must have a limitPrice
    //  if ("MARKET".equalsIgnoreCase(req.getType()) && req.getLimitPrice() != null) {
    //  MARKET orders ignore limitPrice - set to null
    //  This is more forgiving than rejecting the order
    //  }
    if ("LIMIT".equalsIgnoreCase(req.getType()) && req.getLimitPrice() == null) {
      throw new IllegalArgumentException("LIMIT orders must specify a limitPrice");
    }

    Order order = new Order();
    order.setId(UUID.randomUUID());
    order.setAccountId(req.getAccountId());
    order.setClientOrderId(req.getClientOrderId());
    order.setSymbol(req.getSymbol());
    order.setSide(req.getSide());
    order.setQty(req.getQty());
    order.setType(req.getType());
    // MARKET orders: set limitPrice to null (ignore any provided value)
    // LIMIT orders: use the provided limitPrice
    order.setLimitPrice("MARKET".equalsIgnoreCase(req.getType()) ? null : req.getLimitPrice());
    order.setTimeInForce(req.getTimeInForce());
    order.setStatus("NEW");
    order.setFilledQty(0);
    order.setAvgFillPrice(BigDecimal.ZERO);
    order.setCreatedAt(Instant.now());

    saveOrder(order);

    // Handle zero quantity orders - they should be cancelled immediately
    if (order.getQty() == 0) {
      order.setStatus("CANCELLED");
      updateOrder(order);
      return order;
    }

    List<Fill> fills = generateFills(order, mark);
    updatePositions(order, fills);
    updateOrderStatus(order);

    // MARKET orders: Cancel only if no fills occurred (no liquidity available)
    // If partially filled, order remains PARTIALLY_FILLED (can complete later)
    // This is a simulation-friendly approach that allows MARKET orders to rest
    if ("MARKET".equalsIgnoreCase(order.getType()) && order.getFilledQty() == 0) {
      // Only cancel if no fills occurred at all (no liquidity available)
      order.setStatus("CANCELLED");
      updateOrder(order);
    }

    return order;
  }

  /**
   * Generates fills for an order by matching against existing orders in the order book.
   * 
   * <p>Implements price-time priority matching:
   * - BUY orders match against SELL orders where BUY price >= SELL price
   * - SELL orders match against BUY orders where SELL price <= BUY price
   * - Execution price uses the resting order's limit price
   * - Orders are matched in price-time priority (best price first, then earliest)
   * 
   * <p>For MARKET orders: Treated as infinite limit (matches any compatible price)
   * For LIMIT orders: Only matches if price is compatible
   * For TWAP orders: Uses synthetic liquidity (not matched against order book)
   *
   * @param order the incoming order to fill
   * @param mark current market quote
   * @return list of generated fills (may be empty if no matches found)
   */
  private List<Fill> generateFills(Order order, Quote mark) {
    List<Fill> fills = new ArrayList<>();
    BigDecimal marketPrice = mark.getLast();
    Instant now = Instant.now();
  
    // TWAP orders use synthetic liquidity (not matched against order book)
    if ("TWAP".equalsIgnoreCase(order.getType())) {
      return generateTwapFills(order, mark, now);
    }
  
    // Determine matching price for LIMIT orders (MARKET handled separately in findMatchingOrders)
    BigDecimal matchingPrice = null;
    if ("LIMIT".equalsIgnoreCase(order.getType())) {
      if (order.getLimitPrice() == null) {
        // Invalid LIMIT order without price
        return fills;
      }
      matchingPrice = order.getLimitPrice();
    } else if (!"MARKET".equalsIgnoreCase(order.getType())) {
      // Invalid order type
      return fills;
    }
  
    // Find matching orders from the order book
    List<Order> matchingOrders = findMatchingOrders(order, matchingPrice);
  
    int remainingQty = order.getQty();
  
    // Match against each resting order in price-time priority
    // This includes PARTIALLY_FILLED orders - they remain in the order book until fully filled
    for (Order restingOrder : matchingOrders) {
      if (remainingQty <= 0) {
        break;
      }
  
      // CRITICAL: Re-read the resting order from database to get current state
      // This prevents matching against stale data (e.g., orders already filled by previous matches)
      Order currentRestingOrder = getOrderById(restingOrder.getId());
      if (currentRestingOrder == null) {
        continue; // Order was deleted or doesn't exist
      }
      
      // Check if order is still eligible for matching (not fully filled or cancelled)
      if ("FILLED".equals(currentRestingOrder.getStatus()) 
          || "CANCELLED".equals(currentRestingOrder.getStatus())) {
        continue; // Order is no longer available
      }
  
      // Calculate how much of the resting order is still available
      // For PARTIALLY_FILLED orders, this is the remaining unfilled quantity
      int restingRemaining = currentRestingOrder.getQty() - currentRestingOrder.getFilledQty();
      if (restingRemaining <= 0) {
        continue; // This order is already fully filled
      }
  
      // Determine fill quantity (min of what we need and what's available)
      int fillQty = Math.min(remainingQty, restingRemaining);
  
      // Execution price uses the resting order's limit price (standard exchange rule)
      // Standard exchange rule: execution price = resting order's limit price
      // This gives price improvement to the incoming order
      // 
      // For LIMIT orders: must have a limit price, so this should never be null
      // For MARKET orders in the book (shouldn't happen, but if it does): use market price
      BigDecimal executionPrice;
      if (currentRestingOrder.getLimitPrice() != null) {
        executionPrice = currentRestingOrder.getLimitPrice();
      } else {
        // This should only happen for MARKET orders (which shouldn't be in the book)
        // Log a warning and use market price as fallback
        System.err.println("WARNING: Resting order " + currentRestingOrder.getId() 
            + " has null limitPrice but status is " + currentRestingOrder.getStatus()
            + ". Using market price " + marketPrice + " as fallback.");
        executionPrice = marketPrice;
      }
  
      // Create fill for the incoming order
      Fill incomingFill = new Fill(
          UUID.randomUUID(),
          order.getId(),
          fillQty,
          executionPrice,
          now
      );
      fills.add(incomingFill);
      fillRepository.save(incomingFill);
  
      // Create fill for the resting order (both sides get fills)
      Fill restingFill = new Fill(
          UUID.randomUUID(),
          currentRestingOrder.getId(),
          fillQty,
          executionPrice,
          now
      );
      fillRepository.save(restingFill);
  
      // Update the resting order's filled quantity and status
      // Use currentRestingOrder to ensure we're working with latest state
      currentRestingOrder.setFilledQty(currentRestingOrder.getFilledQty() + fillQty);
      updateRestingOrderStatus(currentRestingOrder);
  
      // Update positions for the resting order's account
      updatePositionsForFill(currentRestingOrder, restingFill);
  
      remainingQty -= fillQty;
    }
  
    // Bootstrapping: Only for the very first MARKET BUY order when the entire order book is empty
    // 
    // NOTE: This is a SIMULATED bootstrap to allow trading without historical orders.
    // In real exchanges, the first order would become a resting limit order and wait
    // for another order to cross it. This simulation allows demos to work immediately.
    //
    // Behavior:
    // - First MARKET BUY order (completely empty order book): Execute at market price from API
    // - Once any order exists in the book, bootstrapping is permanently disabled
    // - MARKET orders that partially match are NOT topped up - they remain PARTIALLY_FILLED
    // - SELL orders NEVER bootstrap - they must wait for matching BUY orders
    //
    // This ensures:
    // 1. Only the very first MARKET BUY order can bootstrap (when book is completely empty)
    // 2. After first trade, all orders must match against existing orders
    // 3. Partially filled MARKET orders stay PARTIALLY_FILLED (no synthetic completion)
    if (fills.isEmpty() 
        && remainingQty > 0 
        && "BUY".equalsIgnoreCase(order.getSide())
        && "MARKET".equalsIgnoreCase(order.getType())
        && isCompletelyEmptyBook(order.getSymbol())) {
      // First MARKET BUY order: use market price to bootstrap (external reference price)
      int fillQty = remainingQty;
      BigDecimal executionPrice = marketPrice; // Use market price from quote API
      
      Fill fill = new Fill(
          UUID.randomUUID(),
          order.getId(),
          fillQty,
          executionPrice,
          now
      );
      fills.add(fill);
      fillRepository.save(fill);
    }
    // If fills occurred (partial match) or order book is not empty,
    // order stays WORKING/PARTIALLY_FILLED
    // This means subsequent orders must wait for matching orders to arrive
  
    return fills;
  }

  /**
   * Generates TWAP fills using synthetic liquidity (not matched against order book).
   * TWAP orders are executed in slices over time using market price.
   *
   * @param order the TWAP order
   * @param mark current market quote
   * @param now current timestamp
   * @return list of TWAP fills
   */
  private List<Fill> generateTwapFills(Order order, Quote mark, Instant now) {
    List<Fill> fills = new ArrayList<>();
    BigDecimal twapPrice = mark.getLast();
    int totalQty = order.getQty();
    int slices = Math.min(10, totalQty);
    int baseSliceQty = totalQty / slices;
    int remainder = totalQty % slices;
  
    int remainingQty = totalQty;
  
    for (int i = 0; i < slices && remainingQty > 0; i++) {
      int sliceQty = baseSliceQty + (i < remainder ? 1 : 0);
      sliceQty = Math.min(sliceQty, remainingQty);
  
      int availableLiquidity = calculateAvailableLiquidity(mark);
      int fillQty = Math.min(sliceQty, availableLiquidity);
  
      if (fillQty <= 0) {
        break;
      }
  
      Fill fill = new Fill(
          UUID.randomUUID(),
          order.getId(),
          fillQty,
          twapPrice,
          now.plusSeconds(i)
      );
  
      fills.add(fill);
      fillRepository.save(fill);
  
      remainingQty -= fillQty;
  
      if (fillQty < sliceQty) {
        break;
      }
    }
  
    return fills;
  }

  /**
   * Finds matching orders from the order book based on price-time priority.
   * 
   * <p>For BUY orders: Finds SELL orders where SELL limit_price <= BUY limit_price
   * For SELL orders: Finds BUY orders where BUY limit_price >= SELL limit_price
   * 
   * <p>For MARKET orders: Matches against any order (no price filter)
   * - MARKET BUY matches any SELL order at any price (even $160 if market is $150)
   * - MARKET SELL matches any BUY order at any price
   * 
   * <p>Includes PARTIALLY_FILLED orders in matching:
   * - Orders with status NEW, WORKING, or PARTIALLY_FILLED are eligible
   * - PARTIALLY_FILLED orders can continue to match until fully filled
   * 
   * <p>Orders are sorted by:
   * - Price priority (best price first)
   * - Time priority (earliest first for same price)
   *
   * @param incomingOrder the order seeking matches
   * @param matchingPrice the price to match against (limit price or null for MARKET)
   * @return list of matching orders sorted by price-time priority
   */
  private List<Order> findMatchingOrders(Order incomingOrder, BigDecimal matchingPrice) {
    boolean isMarketOrder = "MARKET".equalsIgnoreCase(incomingOrder.getType());
    
    if ("BUY".equalsIgnoreCase(incomingOrder.getSide())) {
      if (isMarketOrder) {
        // MARKET BUY: match any SELL order (no price filter)
        // NOTE: MARKET orders should not be in the book (they execute immediately or cancel)
        // This query only matches against LIMIT orders (limit_price IS NOT NULL)
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'SELL' "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND limit_price IS NOT NULL "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price ASC, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol()
        );
      } else {
        // LIMIT BUY: match SELL orders where SELL price <= BUY price
        // Only match against LIMIT orders (MARKET orders don't rest in book)
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'SELL' "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND limit_price IS NOT NULL "
                + "AND limit_price <= ? "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price ASC, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            matchingPrice
        );
      }
    } else {
      if (isMarketOrder) {
        // MARKET SELL: match any BUY order (no price filter)
        // NOTE: MARKET orders should not be in the book (they execute immediately or cancel)
        // This query only matches against LIMIT orders (limit_price IS NOT NULL)
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'BUY' "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND limit_price IS NOT NULL "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price DESC, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol()
        );
      } else {
        // LIMIT SELL: match BUY orders where BUY price >= SELL price
        // Only match against LIMIT orders (MARKET orders don't rest in book)
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'BUY' "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND limit_price IS NOT NULL "
                + "AND limit_price >= ? "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price DESC, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            matchingPrice
        );
      }
    }
  }

  /**
   * Checks if the order book is completely empty for a given symbol.
   * Used to determine if this is the very first order (bootstrapping scenario).
   * Once any order exists, bootstrapping is permanently disabled.
   *
   * @param symbol the trading symbol
   * @return true if no orders exist for this symbol, false otherwise
   */
  private boolean isCompletelyEmptyBook(String symbol) {
    Integer count = jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM orders WHERE symbol = ?",
        Integer.class,
        symbol
    );
    return count == null || count == 0;
  }

  /**
   * Updates a resting order's status after it has been matched.
   * Updates the order's filled quantity, average fill price, and status in the database.
   *
   * @param order the resting order that was matched
   */
  private void updateRestingOrderStatus(Order order) {
    // After matching, the order should have fills, so filledQty > 0
    // If somehow filledQty is 0, keep the current status (shouldn't happen after a match)
    if (order.getFilledQty() < order.getQty()) {
      order.setStatus("PARTIALLY_FILLED");
    } else {
      order.setStatus("FILLED");
    }

    // Recalculate average fill price from all fills for this order
    List<Fill> orderFills = fillRepository.findByOrderId(order.getId());
    if (orderFills != null && !orderFills.isEmpty()) {
      BigDecimal totalCost = orderFills.stream()
          .map(f -> f.getPrice().multiply(BigDecimal.valueOf(f.getQty())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      int totalFilled = orderFills.stream().mapToInt(Fill::getQty).sum();
      if (totalFilled > 0) {
        order.setAvgFillPrice(
            totalCost.divide(BigDecimal.valueOf(totalFilled), RoundingMode.HALF_UP)
        );
      }
    }

    jdbcTemplate.update(
        "UPDATE orders SET filled_qty = ?, status = ?, avg_fill_price = ? WHERE id = ?",
        order.getFilledQty(),
        order.getStatus(),
        order.getAvgFillPrice(),
        order.getId()
    );
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
   * Updates positions for an account based on fills.
   *
   * @param order the order whose fills to process
   * @param fills list of fills to process
   */
  private void updatePositions(Order order, List<Fill> fills) {
    for (Fill fill : fills) {
      updatePositionsForFill(order, fill);
    }
  }

  /**
   * Updates positions for a single fill.
   *
   * @param order the order associated with the fill
   * @param fill the fill to process
   */
  private void updatePositionsForFill(Order order, Fill fill) {
    UUID accountId = order.getAccountId();
    String symbol = order.getSymbol();
  
    Position position = getPosition(accountId, symbol);
    if (position == null) {
      position = new Position(accountId, symbol, 0, BigDecimal.ZERO);
    }
  
    int signedQty = "SELL".equalsIgnoreCase(order.getSide())
        ? -fill.getQty()
        : fill.getQty();
  
    position.updateWithFill(signedQty, fill.getPrice());
    savePosition(position);
  }
  
  /**
   * Updates order status based on fills.
   * 
   * <p>IMPORTANT: This method accumulates fills, not overwrites them.
   * For orders that are matched multiple times (e.g., PARTIALLY_FILLED orders
   * that complete later), this ensures filledQty is cumulative.
   * 
   * <p>Calculates total filled quantity and average fill price from the provided fills.
   * Sets order status dynamically:
   * - WORKING: No fills occurred (order remains in market)
   * - PARTIALLY_FILLED: Some fills but less than order quantity (order stays in order book)
   * - FILLED: Total fills meet or exceed order quantity (order removed from order book)
   * 
   * <p>PARTIALLY_FILLED orders remain in the order book and can be matched against
   * when new orders of the opposite side arrive. This enables:
   * - Partial fills to complete later when liquidity arrives
   * - MARKET orders to match at different prices over time
   * - Average fill price calculation across multiple fills
   *
   * @param order the order to update (may already have some fills from previous matches)
   */
  private void updateOrderStatus(Order order) {
    // Fills are ALREADY saved to DB at this point (saved in generateFills)
    // Use database as the single source of truth
    List<Fill> allFills = fillRepository.findByOrderId(order.getId());
    if (allFills == null) {
      allFills = new ArrayList<>();
    }

    int totalFilled = allFills.stream().mapToInt(Fill::getQty).sum();

    // Check for overfill: total filled should not exceed order quantity
    if (totalFilled > order.getQty()) {
      throw new IllegalStateException(
          String.format("Order overfilled: qty=%d, filled=%d",
              order.getQty(), totalFilled));
    }

    order.setFilledQty(totalFilled);

    // Status determination: MARKET, LIMIT, and TWAP orders can all be
    // WORKING (no fills), PARTIALLY_FILLED (some fills), or FILLED (complete)
    // Note: In this simulated system, MARKET orders can rest in the book if partially filled
    // This differs from real exchanges but is valid for simulation purposes
    if (totalFilled == 0) {
      order.setStatus("WORKING");
      order.setAvgFillPrice(null);
    } else if (totalFilled < order.getQty()) {
      order.setStatus("PARTIALLY_FILLED");
      recalculateAverageFillPrice(order);
    } else {
      order.setStatus("FILLED");
      recalculateAverageFillPrice(order);
    }

    updateOrder(order);
  }

  /**
   * Recalculates the average fill price for an order from all its fills in the database.
   *
   * @param order the order to recalculate average fill price for
   */
  private void recalculateAverageFillPrice(Order order) {
    List<Fill> allFills = fillRepository.findByOrderId(order.getId());
    if (allFills != null && !allFills.isEmpty()) {
      BigDecimal totalCost = allFills.stream()
          .map(f -> f.getPrice().multiply(BigDecimal.valueOf(f.getQty())))
          .reduce(BigDecimal.ZERO, BigDecimal::add);
      int totalFilled = allFills.stream().mapToInt(Fill::getQty).sum();
      if (totalFilled > 0) {
        order.setAvgFillPrice(
            totalCost.divide(BigDecimal.valueOf(totalFilled), RoundingMode.HALF_UP)
        );
      }
    }
  }

  /**
   * Retrieves an order by its ID from the database.
   * Returns null if the order doesn't exist.
   *
   * @param orderId the order identifier
   * @return the order if found, null otherwise
   */
  private Order getOrderById(UUID orderId) {
    String sql = "SELECT * FROM orders WHERE id = ?";
    try {
      List<Order> orders = jdbcTemplate.query(sql, new OrderMapper(), orderId);
      return orders.isEmpty() ? null : orders.get(0);
    } catch (Exception e) {
      return null;
    }
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
        order.getFilledQty(), order.getAvgFillPrice(),
        Timestamp.from(order.getCreatedAt()));
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