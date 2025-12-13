package com.dev.tradingapi.repository;

import com.dev.tradingapi.model.Account;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * JDBC-backed repository for performing CRUD operations on {@link Account} entities.
 */
@Repository
public class AccountRepository {

  private final JdbcTemplate jdbcTemplate;

  private final RowMapper<Account> accountRowMapper = (rs, rowNum) -> new Account(
      (UUID) rs.getObject("id"),
      rs.getString("name"),
      rs.getString("auth_token"),
      rs.getInt("max_order_qty"),
      rs.getBigDecimal("max_notional"),
      rs.getInt("max_position_qty"),
      rs.getTimestamp("created_at").toInstant(),
      rs.getBigDecimal("initial_balance"),
      rs.getBigDecimal("cash_balance")
  );

  /**
   * Creates a new repository instance using the provided {@link JdbcTemplate}.
   *
   * @param jdbcTemplate JDBC template used to execute SQL statements
   */
  public AccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  /**
   * Looks up an account by its unique username.
   *
   * @param username username to search for
   * @return an {@link Optional} containing the account if found, otherwise empty
   */
  public Optional<Account> findByUsername(String username) {
    String sql = "SELECT * FROM accounts WHERE username = ?";
    return jdbcTemplate.query(sql, accountRowMapper, username).stream().findFirst();
  }

  /**
   * Persists a new account record to the database.
   *
   * @param name human-readable account name
   * @param username login username
   * @param passwordHash hashed password for authentication
   * @param authToken API authentication token issued for the account
   * @param initialBalance initial and starting cash balance
   * @return the newly created {@link Account}
   */
  public Account save(String name, String username, String passwordHash,
                      String authToken, BigDecimal initialBalance) {
    UUID id = UUID.randomUUID();
    String sql = "INSERT INTO accounts (id, name, auth_token, username, password_hash,"
        + " max_order_qty, max_notional, max_position_qty, initial_balance, cash_balance,"
        + " created_at) VALUES (?, ?, ?, ?, ?, 0, 0, 0, ?, ?, CURRENT_TIMESTAMP)";
    BigDecimal init = initialBalance != null ? initialBalance : BigDecimal.ZERO;
    jdbcTemplate.update(sql, id, name, authToken, username, passwordHash, init, init);
    return new Account(id, name, authToken, 0,
        BigDecimal.ZERO, 0,
        java.time.Instant.now(), init, init);
  }

  /**
   * Retrieves an account by its identifier.
   *
   * @param accountId unique identifier of the account
   * @return an {@link Optional} containing the account if found, otherwise empty
   */
  public Optional<Account> findById(UUID accountId) {
    String sql = "SELECT * FROM accounts WHERE id = ?";
    return jdbcTemplate.query(sql, accountRowMapper, accountId).stream().findFirst();
  }

  /**
   * Updates risk-limit settings for an existing account.
   *
   * @param accountId unique identifier of the account to update
   * @param maxOrderQty maximum allowed order quantity per order
   * @param maxNotional maximum notional exposure allowed across positions
   * @param maxPositionQty maximum allowed position quantity per symbol
   */
  public void updateRiskLimits(UUID accountId, int maxOrderQty,
                               BigDecimal maxNotional,
                               int maxPositionQty) {
    String sql = "UPDATE accounts SET max_order_qty = ?, max_notional = ?, max_position_qty = ? "
        + "WHERE id = ?";
    jdbcTemplate.update(sql, maxOrderQty, maxNotional, maxPositionQty, accountId);
  }

  /**
   * Persists an updated cash balance for the specified account.
   *
   * @param accountId account identifier
   * @param cashBalance new cash balance value
   */
  public void updateCashBalance(UUID accountId, BigDecimal cashBalance) {
    String sql = "UPDATE accounts SET cash_balance = ? WHERE id = ?";
    jdbcTemplate.update(sql, cashBalance, accountId);
  }
}

