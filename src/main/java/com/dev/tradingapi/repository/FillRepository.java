package com.dev.tradingapi.repository;

import com.dev.tradingapi.model.Fill;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed repository for performing CRUD operations on {@link Fill} entities.
 */
@Repository
public class FillRepository {

  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<Fill> fillRowMapper = (rs, rowNum) -> {
    Fill fill = new Fill();
    fill.setId(rs.getObject("id", UUID.class));
    fill.setOrderId(rs.getObject("order_id", UUID.class));
    fill.setQty(rs.getInt("qty"));
    fill.setPrice(rs.getBigDecimal("price"));
    fill.setTs(rs.getTimestamp("ts").toInstant());
    return fill;
  };

  /**
   * Creates a new repository instance using the provided {@link JdbcTemplate}.
   *
   * @param jdbcTemplate JDBC template used to execute SQL statements
   */
  public FillRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Persists a new fill record to the database.
   *
   * @param fill the fill to save
   */
  public void save(Fill fill) {
    String sql = "INSERT INTO fills (id, order_id, qty, price, ts) VALUES (?, ?, ?, ?, ?)";
    jdbcTemplate.update(sql, fill.getId(), fill.getOrderId(), fill.getQty(),
        fill.getPrice(), fill.getTs());
  }

  /**
   * Retrieves all fills for a specific order, ordered by timestamp ascending.
   *
   * @param orderId the order identifier
   * @return list of fills for the order (may be empty)
   */
  public List<Fill> findByOrderId(UUID orderId) {
    String sql = "SELECT * FROM fills WHERE order_id = ? ORDER BY ts ASC";
    return jdbcTemplate.query(sql, fillRowMapper, orderId);
  }

  /**
   * Retrieves a fill by its identifier.
   *
   * @param fillId unique identifier of the fill
   * @return the fill if found, or null
   */
  public Fill findById(UUID fillId) {
    String sql = "SELECT * FROM fills WHERE id = ?";
    List<Fill> results = jdbcTemplate.query(sql, fillRowMapper, fillId);
    return results.isEmpty() ? null : results.get(0);
  }
}

