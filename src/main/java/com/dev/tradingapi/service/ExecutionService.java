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

    // Reject zero-quantity orders up front (persist as CANCELLED for auditability).
    if (order.getQty() == 0) {
      order.setStatus("CANCELLED");
      saveOrder(order);
      return order;
    }

    // SELL-side holdings check: prevent over-selling beyond current position
    // including any existing open SELL orders for this account+symbol.
    if ("SELL".equalsIgnoreCase(order.getSide())) {
      Position position = getPosition(order.getAccountId(), order.getSymbol());
      int positionQty = (position != null) ? position.getQty() : 0;

      int openSellExposure = getOpenSellExposure(order.getAccountId(), order.getSymbol());
      int availableToSell = positionQty - openSellExposure;

      if (availableToSell < order.getQty()) {
        order.setStatus("CANCELLED");
        saveOrder(order);
        return order;
      }
    }

    // Persist the live order in the book before matching.
    saveOrder(order);

    List<Fill> fills = generateFills(order, mark);
    updatePositions(order, fills);
    updateOrderStatus(order);

    return order;
  }

  /**
   * Matches an incoming order against the order book up to maxQty.
   * Returns fills generated for the incoming order.
   */
  private int matchAgainstBook(
      Order incomingOrder,
      int maxQty,
      BigDecimal matchingPrice,
      BigDecimal marketPrice,
      Instant now,
      List<Fill> fills
  ) {
    int remainingQty = maxQty;

    List<Order> matchingOrders = findMatchingOrders(incomingOrder, matchingPrice);

    for (Order restingOrder : matchingOrders) {
      if (remainingQty <= 0) {
        break;
      }

      Order currentRestingOrder = getOrderById(restingOrder.getId());
      if (currentRestingOrder == null) {
        continue;
      }

      if ("FILLED".equals(currentRestingOrder.getStatus())
          || "CANCELLED".equals(currentRestingOrder.getStatus())) {
        continue;
      }

      int restingRemaining =
          currentRestingOrder.getQty() - currentRestingOrder.getFilledQty();
      if (restingRemaining <= 0) {
        continue;
      }

      int fillQty = Math.min(remainingQty, restingRemaining);

      BigDecimal executionPrice =
          currentRestingOrder.getLimitPrice() != null
              ? currentRestingOrder.getLimitPrice()
              : marketPrice;

      // Price protection (LIMIT only)
      if (matchingPrice != null) {
        if ("BUY".equalsIgnoreCase(incomingOrder.getSide())
            && executionPrice.compareTo(matchingPrice) > 0) {
          continue;
        }
        if ("SELL".equalsIgnoreCase(incomingOrder.getSide())
            && executionPrice.compareTo(matchingPrice) < 0) {
          continue;
        }
      }

      // Incoming fill
      Fill incomingFill = new Fill(
          UUID.randomUUID(),
          incomingOrder.getId(),
          fillQty,
          executionPrice,
          now
      );
      fills.add(incomingFill);
      fillRepository.save(incomingFill);

      // Resting fill
      Fill restingFill = new Fill(
          UUID.randomUUID(),
          currentRestingOrder.getId(),
          fillQty,
          executionPrice,
          now
      );
      fillRepository.save(restingFill);

      currentRestingOrder.setFilledQty(
          currentRestingOrder.getFilledQty() + fillQty
      );
      updateRestingOrderStatus(currentRestingOrder);
      updatePositionsForFill(currentRestingOrder, restingFill);

      remainingQty -= fillQty;
    }

    return remainingQty;
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
  
    if ("TWAP".equalsIgnoreCase(order.getType())) {
      return generateTwapFills(order, mark, now);
    }
  
    BigDecimal matchingPrice = null;
    if ("LIMIT".equalsIgnoreCase(order.getType())) {
      matchingPrice = order.getLimitPrice();
    }
  
    matchAgainstBook(
        order,
        order.getQty(),
        matchingPrice,
        marketPrice,
        now,
        fills
    );
  
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
    BigDecimal marketPrice = mark.getLast();
  
    int totalQty = order.getQty();

    int numSlices;
    if (totalQty >= 1000) {
      numSlices = 10;
    } else if (totalQty >= 100) {
      numSlices = 5;
    } else if (totalQty >= 50) {
      numSlices = 2;
    } else {
      numSlices = 1;
    }
  
    numSlices = Math.min(numSlices, 10);
  
    int baseSliceQty = totalQty / numSlices;
    int remainder = totalQty % numSlices;
  
    int remainingQty = totalQty;
  
    for (int i = 0; i < numSlices && remainingQty > 0; i++) {
      int sliceQty = baseSliceQty + (i < remainder ? 1 : 0);
  
      int afterSlice = matchAgainstBook(
          order,
          sliceQty,
          null,          
          marketPrice,
          now,
          fills
      );
  
      int filledThisSlice = sliceQty - afterSlice;
      remainingQty -= filledThisSlice;
  
      // No liquidity → stop TWAP early
      if (filledThisSlice == 0) {
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
    boolean isPriceProtected = matchingPrice != null;
  
    if ("BUY".equalsIgnoreCase(incomingOrder.getSide())) {
      if (!isPriceProtected) {
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'SELL' "
                + "AND account_id <> ? "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price ASC NULLS LAST, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            incomingOrder.getAccountId()
        );
      } else {
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'SELL' "
                + "AND account_id <> ? "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND (qty - filled_qty) > 0 "
                + "AND (limit_price IS NULL OR limit_price <= ?) "
                + "ORDER BY limit_price ASC NULLS LAST, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            incomingOrder.getAccountId(),
            matchingPrice
        );
      }
    } else {
      if (!isPriceProtected) {
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'BUY' "
                + "AND account_id <> ? "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND (qty - filled_qty) > 0 "
                + "ORDER BY limit_price DESC NULLS LAST, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            incomingOrder.getAccountId()
        );
      } else {
        return jdbcTemplate.query(
            "SELECT * FROM orders "
                + "WHERE symbol = ? "
                + "AND side = 'BUY' "
                + "AND account_id <> ? "
                + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED') "
                + "AND (qty - filled_qty) > 0 "
                + "AND (limit_price IS NULL OR limit_price >= ?) "
                + "ORDER BY limit_price DESC NULLS LAST, created_at ASC",
            new OrderMapper(),
            incomingOrder.getSymbol(),
            incomingOrder.getAccountId(),
            matchingPrice
        );
      }
    }
  }
  
  /**
   * Gets an order by its ID from the database.
   * Used to re-read order state to prevent stale data issues during matching.
   *
   * @param orderId the order ID
   * @return the order, or null if not found
   */
  private Order getOrderById(UUID orderId) {
    String sql = "SELECT * FROM orders WHERE id = ?";
    try {
      return jdbcTemplate.queryForObject(sql, new OrderMapper(), orderId);
    } catch (Exception e) {
      return null;
    }
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

    // Status determination:
    // With a pure order book, orders of all types (MARKET, LIMIT, TWAP)
    // can remain WORKING with zero fills. They rest in the book until
    // they either receive fills (PARTIALLY_FILLED / FILLED) or are
    // cancelled explicitly via another path.
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
   * Computes the total remaining quantity of open SELL orders for an account and symbol.
   * This is used to prevent over-selling beyond current position holdings.
   */
  private int getOpenSellExposure(UUID accountId, String symbol) {
    String sql = "SELECT COALESCE(SUM(qty - filled_qty), 0) FROM orders "
        + "WHERE account_id = ? AND symbol = ? "
        + "AND side = 'SELL' "
        + "AND status IN ('NEW', 'WORKING', 'PARTIALLY_FILLED')";
    Integer exposure = jdbcTemplate.queryForObject(sql, Integer.class, accountId, symbol);
    return exposure != null ? exposure : 0;
  }

  /**
   * Saves a position to the database.
   *
   * @param position the position to save
   */
  private void savePosition(Position position) {
    // If quantity is zero, remove the position row entirely to avoid
    // "zombie" positions with 0 qty but a stale avg_cost.
    if (position.getQty() == 0) {
      jdbcTemplate.update(
          "DELETE FROM positions WHERE account_id = ? AND symbol = ?",
          position.getAccountId(),
          position.getSymbol());
      return;
    }

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