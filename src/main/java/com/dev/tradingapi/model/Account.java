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
  private String apiKey;
  private int maxOrderQty;
  private BigDecimal maxNotional;
  private int maxPositionQty;
  private Instant createdAt;

  /**
   * Constructs a new Account object.
   *
   * @param id unique account identifier
   * @param name display name for the account
   * @param apiKey API key for authentication
   * @param maxOrderQty max quantity per order
   * @param maxNotional max dollar value per order
   * @param maxPositionQty max total position size
   * @param createdAt creation timestamp
   */
  public Account(UUID id, String name, String apiKey, int maxOrderQty,
                 BigDecimal maxNotional, int maxPositionQty, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.apiKey = apiKey;
    this.maxOrderQty = maxOrderQty;
    this.maxNotional = maxNotional;
    this.maxPositionQty = maxPositionQty;
    this.createdAt = createdAt;
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
   * Returns the API key assigned to this account.
   *
   * @return the account's API key string
   */
  public String getApiKey() {
    return apiKey;
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
}
