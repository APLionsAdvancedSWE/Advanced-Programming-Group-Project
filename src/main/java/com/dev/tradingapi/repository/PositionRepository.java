package com.dev.tradingapi.repository;

import com.dev.tradingapi.model.Position;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed repository for reading positions from the database.
 *
 * <p>Backed by the <code>positions</code> table defined in the schema
 * (account_id, symbol, qty, avg_cost).</p>
 */
@Repository
public class PositionRepository {

  private final JdbcTemplate jdbcTemplate;

  public PositionRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static class PositionRowMapper implements RowMapper<Position> {
    @Override
    public Position mapRow(ResultSet rs, int rowNum) throws SQLException {
      UUID accountId = rs.getObject("account_id", UUID.class);
      String symbol = rs.getString("symbol");
      int qty = rs.getInt("qty");
      BigDecimal avgCost = rs.getBigDecimal("avg_cost");
      return new Position(accountId, symbol, qty, avgCost);
    }
  }

  private static final PositionRowMapper POSITION_ROW_MAPPER = new PositionRowMapper();

  /**
   * Returns all positions for the specified account.
   *
   * @param accountId the owning account's UUID
   * @return list of positions (possibly empty, never {@code null})
   */
  public List<Position> findByAccountId(UUID accountId) {
    String sql = "SELECT account_id, symbol, qty, avg_cost FROM positions WHERE account_id = ?";
    return jdbcTemplate.query(sql, POSITION_ROW_MAPPER, accountId);
  }
}
