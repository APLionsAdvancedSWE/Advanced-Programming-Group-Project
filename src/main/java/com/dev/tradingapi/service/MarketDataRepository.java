package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-based repository for persisted intraday market data.
 *
 * <p>Backed by the <code>market_bars</code> table which is created in both
 * the H2 and PostgreSQL schemas. Each row represents a single OHLCV bar
 * at a specific timestamp for a symbol.</p>
 */
@Repository
public class MarketDataRepository {

  private final JdbcTemplate jdbcTemplate;

  public MarketDataRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Persists a batch of quotes for a symbol.
   *
   * <p>Existing rows for the symbol in the provided time range are deleted
   * first to keep the dataset consistent when re-fetching for a month.</p>
   *
   * @param symbol symbol these quotes belong to
   * @param quotes ordered list of quotes
   */
  public void saveQuotes(String symbol, List<Quote> quotes) {
    if (quotes == null || quotes.isEmpty()) {
      return;
    }

    Instant from = quotes.get(0).getTs();
    Instant to = quotes.get(quotes.size() - 1).getTs();

    String deleteSql = "DELETE FROM market_bars WHERE symbol = ? AND ts BETWEEN ? AND ?";
    jdbcTemplate.update(deleteSql, symbol,
        Timestamp.from(from), Timestamp.from(to));

    String insertSql = "INSERT INTO market_bars "
        + "(id, symbol, ts, open, high, low, close, volume) "
        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

    jdbcTemplate.batchUpdate(insertSql, quotes, quotes.size(), (ps, q) -> {
      ps.setObject(1, UUID.randomUUID());
      ps.setString(2, q.getSymbol());
      ps.setTimestamp(3, Timestamp.from(q.getTs()));
      ps.setBigDecimal(4, q.getOpen());
      ps.setBigDecimal(5, q.getHigh());
      ps.setBigDecimal(6, q.getLow());
      ps.setBigDecimal(7, q.getClose());
      ps.setLong(8, q.getVolume());
    });
  }

  /**
   * Loads all quotes for a symbol ordered by timestamp ascending.
   *
   * @param symbol ticker symbol
   * @return list of quotes, possibly empty
   */
  public List<Quote> findBySymbolOrderByTsAsc(String symbol) {
    String sql = "SELECT symbol, ts, open, high, low, close, volume "
        + "FROM market_bars WHERE symbol = ? ORDER BY ts ASC";
    return jdbcTemplate.query(sql, new Object[]{symbol}, new QuoteRowMapper());
  }

  /**
   * Returns all distinct symbols that currently have market data rows.
   *
   * @return list of unique symbols
   */
  public List<String> findDistinctSymbols() {
    String sql = "SELECT DISTINCT symbol FROM market_bars";
    return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getString("symbol"));
  }

  private static class QuoteRowMapper implements RowMapper<Quote> {

    @Override
    public Quote mapRow(ResultSet rs, int rowNum) throws SQLException {
      String symbol = rs.getString("symbol");
      BigDecimal open = rs.getBigDecimal("open");
      BigDecimal high = rs.getBigDecimal("high");
      BigDecimal low = rs.getBigDecimal("low");
      BigDecimal close = rs.getBigDecimal("close");
      long volume = rs.getLong("volume");
      Instant ts = rs.getTimestamp("ts").toInstant();
      return new Quote(symbol, open, high, low, close, volume, ts);
    }
  }
}
