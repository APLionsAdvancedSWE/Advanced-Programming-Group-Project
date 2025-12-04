package com.dev.tradingapi.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * This class defines the Account model.
 */
public class Account {
  private UUID id;
  private String name;
  private String authToken;
  private int maxOrderQty;
  private BigDecimal maxNotional;
  private int maxPositionQty;
  private Instant createdAt;
  private BigDecimal initialBalance;
  private BigDecimal cashBalance;

  /**
   * Constructs a new Account object.
   *
   * @param id unique account identifier
   * @param name display name for the account
  * @param authToken authentication token for the account
   * @param maxOrderQty max quantity per order
   * @param maxNotional max dollar value per order
   * @param maxPositionQty max total position size
   * @param createdAt creation timestamp
   */
  public Account(UUID id, String name, String authToken, int maxOrderQty,
                 BigDecimal maxNotional, int maxPositionQty,
                 Instant createdAt, BigDecimal initialBalance) {
    this.id = id;
    this.name = name;
    this.authToken = authToken;
    this.maxOrderQty = maxOrderQty;
    this.maxNotional = maxNotional;
    this.maxPositionQty = maxPositionQty;
    this.createdAt = createdAt;
    this.initialBalance = initialBalance;
    this.cashBalance = initialBalance;
  }

  /**
   * Returns the unique id for this trading account.
   *
   * @return the account's UUID
   */
  public UUID getId() {
    return id;
  }

  /**
   * Returns the display name associated with this account.
   * This is a human-readable label such as "Retail Trader 1" or "Institutional Bot".
   *
   * @return the account display name
   */
  public String getName() {
    return name;
  }

  /**
  * Returns the authentication token assigned to this account.
   *
   * @return the account's API key string
   */
  public String getAuthToken() {
    return authToken;
  }

  /**
   * Returns the maximum quantity of shares or contract
   * that may be included in a single order for this account.
   *
   * @return maximum order quantity
   */
  public int getMaxOrderQty() {
    return maxOrderQty;
  }

  /**
   * Returns the maximum total notional value (price × quantity) permitted for a single order.
   *
   * @return max dollar value allowed per order
   */
  public BigDecimal getMaxNotional() {
    return maxNotional;
  }

  /**
   *  Returns the maximum total position size allowed for this account in any single trading symbol.
   *
   * @return maximum position quantity
   */
  public int getMaxPositionQty() {
    return maxPositionQty;
  }

  /**
   * Returns the timestamp indicating when this account was initially created.
   *
   * @return account creation time
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Updates the maximum order quantity limit.
   *
   * @param maxOrderQty new per-order quantity limit
   */
  public void setMaxOrderQty(int maxOrderQty) {
    this.maxOrderQty = maxOrderQty;
  }

  /**
   * Updates the maximum notional value limit.
   *
   * @param maxNotional new per-order dollar limit
   */
  public void setMaxNotional(BigDecimal maxNotional) {
    this.maxNotional = maxNotional;
  }

  /**
   * Updates the maximum position size limit.
   *
   * @param maxPositionQty new total position limit
   */
  public void setMaxPositionQty(int maxPositionQty) {
    this.maxPositionQty = maxPositionQty;
  }

  /**
   * Returns the initial funded balance for this account.
   * This value represents the starting capital and remains constant
   * unless the account baseline is explicitly reset.
   *
   * @return the account's initial starting balance
   */
  public BigDecimal getInitialBalance() {
    return initialBalance;
  }

  /**
   * Returns the current available cash balance for this account.
   * This amount reflects funds remaining after trades, fees, and other adjustments.
   *
   * @return the current cash balance
   */
  public BigDecimal getCashBalance() {
    return cashBalance;
  }

  /**
   * Adjusts the account's cash balance by the specified delta amount.
   * A positive delta increases available cash (e.g., after a sale),
   * while a negative delta decreases it (e.g., after a purchase or fee).
   *
   * @param delta the change in cash balance to apply
   */
  public void adjustCashBalance(BigDecimal delta) {
    this.cashBalance = this.cashBalance.add(delta);
  }
}
