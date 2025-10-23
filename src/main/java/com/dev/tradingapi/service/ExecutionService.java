package com.dev.tradingapi.service;

import com.dev.tradingapi.model.*;
import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.NotFoundException;
import com.dev.tradingapi.exception.RiskException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.*;

/**
 * Order execution service that creates orders, generates fills, and updates positions.
 * 
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
    public ExecutionService(JdbcTemplate jdbcTemplate, MarketService marketService, RiskService riskService) {
        this.jdbcTemplate = jdbcTemplate;
        this.marketService = marketService;
        this.riskService = riskService;
    }

    /**
     * Creates and executes an order with fills and position updates.
     * 
     * Dependencies: MarketService.getQuote(), RiskService.validate()
     * 
     * @param req order creation request
     * @return created Order with FILLED status
     * @throws NotFoundException if market data unavailable
     * @throws RiskException if order violates risk limits
     */
    public Order create(CreateOrderRequest req) {
        Quote mark = marketService.getQuote(req.getSymbol());
        if (mark == null) {
            throw new NotFoundException("No market data available for symbol: " + req.getSymbol());
        }
        riskService.validate(req, mark);

        UUID orderId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbcTemplate.update("""
            INSERT INTO orders (
                id, account_id, client_order_id, symbol, side, qty, type,
                limit_price, time_in_force, status, filled_qty, avg_fill_price, created_at
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            orderId, req.getAccountId(), req.getClientOrderId(), req.getSymbol(), req.getSide(),
            req.getQty(), req.getType(), req.getLimitPrice(), req.getTimeInForce(),
            "NEW", 0, null, now
        );

        List<Fill> fills = generateFills(orderId, req, mark);

        if (fills.isEmpty()) {
            return get(orderId);
        }

        BigDecimal total = fills.stream()
                .map(f -> f.getPrice().multiply(BigDecimal.valueOf(f.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int totalQty = fills.stream().mapToInt(Fill::getQty).sum();
        
        if (totalQty == 0) {
            return get(orderId);
        }
        
        BigDecimal avgPrice = total.divide(BigDecimal.valueOf(totalQty), 4, RoundingMode.HALF_UP);

        jdbcTemplate.update("""
            UPDATE orders SET status = ?, filled_qty = ?, avg_fill_price = ?
            WHERE id = ?
            """, "FILLED", totalQty, avgPrice, orderId);

        for (Fill fill : fills) {
            jdbcTemplate.update("""
                INSERT INTO fills (id, order_id, qty, price, ts)
                VALUES (?, ?, ?, ?, ?)
                """, fill.getId(), fill.getOrderId(), fill.getQty(), fill.getPrice(), fill.getTs());
        }

        updatePositions(req.getAccountId(), req.getSymbol(), req.getSide(), fills);

        return get(orderId);
    }

    /**
     * Retrieves an order by ID.
     * 
     * @param orderId order identifier
     * @return Order object
     * @throws NotFoundException if order not found
     */
    public Order get(UUID orderId) {
        String sql = "SELECT * FROM orders WHERE id = ?";
        List<Order> orders = jdbcTemplate.query(sql, new OrderMapper(), orderId);
        if (orders.isEmpty()) throw new NotFoundException("Order not found: " + orderId);
        return orders.get(0);
    }

    /**
     * Retrieves all fills for an order.
     * 
     * @param orderId order identifier
     * @return list of Fill objects (empty if none)
     */
    public List<Fill> getFills(UUID orderId) {
        String sql = "SELECT * FROM fills WHERE order_id = ?";
        return jdbcTemplate.query(sql, new FillMapper(), orderId);
    }

    /**
     * Generates fills for an order using current market price.
     * 
     * @param orderId order identifier
     * @param req order request
     * @param mark current market quote
     * @return list of generated fills
     */
    private List<Fill> generateFills(UUID orderId, CreateOrderRequest req, Quote mark) {
        List<Fill> fills = new ArrayList<>();
        Instant now = Instant.now();
        BigDecimal price = mark.getLast();
        fills.add(new Fill(UUID.randomUUID(), orderId, req.getQty(), price, now));

        return fills;
    }

    /**
     * Updates account positions after order fills.
     * 
     * 
     * @param accountId account identifier
     * @param symbol trading symbol
     * @param side order side (BUY/SELL)
     * @param fills list of fills to process
     */
    private void updatePositions(UUID accountId, String symbol, String side, List<Fill> fills) {
        if (fills.isEmpty()) {
            return;
        }

        int totalFillQty = fills.stream().mapToInt(Fill::getQty).sum();
        int netQtyChange = "BUY".equalsIgnoreCase(side) ? totalFillQty : -totalFillQty;

        BigDecimal totalNotional = fills.stream().map(f -> f.getPrice()
        .multiply(BigDecimal.valueOf(f.getQty())))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avgFillPrice = totalNotional.divide(BigDecimal.valueOf(totalFillQty), 4, RoundingMode.HALF_UP);

        String selectSql = "SELECT qty, avg_cost FROM positions WHERE account_id = ? AND symbol = ?";
        List<Position> positions = jdbcTemplate.query(selectSql, 
                (rs, rowNum) -> new Position(accountId, symbol, rs.getInt("qty"), rs.getBigDecimal("avg_cost")),
                accountId, symbol);

        Position currentPosition = positions.isEmpty() ? new Position(accountId, symbol, 0, BigDecimal.ZERO) : positions.get(0);

        currentPosition.updateWithFill(netQtyChange, avgFillPrice);

        String upsertSql = """
            INSERT INTO positions (account_id, symbol, qty, avg_cost)
            VALUES (?, ?, ?, ?)
            ON CONFLICT (account_id, symbol)
            DO UPDATE SET qty = EXCLUDED.qty, avg_cost = EXCLUDED.avg_cost
            """;
        
        jdbcTemplate.update(upsertSql, accountId, symbol, currentPosition.getQty(), currentPosition.getAvgCost());
    }

    // added for JDBC implementation

    /**
     * Maps Order database records to Order objects.
     */
    private static class OrderMapper implements RowMapper<Order> {
        @Override
        public Order mapRow(ResultSet rs, int rowNum) throws SQLException {
            Order o = new Order();
            o.setId(UUID.fromString(rs.getString("id")));
            o.setAccountId(UUID.fromString(rs.getString("account_id")));
            o.setClientOrderId(rs.getString("client_order_id"));
            o.setSymbol(rs.getString("symbol"));
            o.setSide(rs.getString("side"));
            o.setQty(rs.getInt("qty"));
            o.setType(rs.getString("type"));
            o.setLimitPrice(rs.getBigDecimal("limit_price"));
            o.setTimeInForce(rs.getString("time_in_force"));
            o.setStatus(rs.getString("status"));
            o.setFilledQty(rs.getInt("filled_qty"));
            o.setAvgFillPrice(rs.getBigDecimal("avg_fill_price"));
            o.setCreatedAt(rs.getTimestamp("created_at").toInstant());
            return o;
        }
    }

    /**
     * Maps Fill database records to Fill objects.
     */
    private static class FillMapper implements RowMapper<Fill> {
        @Override
        public Fill mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Fill(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("order_id")),
                    rs.getInt("qty"),
                    rs.getBigDecimal("price"),
                    rs.getTimestamp("ts").toInstant());
        }
    }
}
