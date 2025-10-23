package com.dev.tradingapi.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a trading order in the system.
 * Orders can be buy or sell orders with various types and time-in-force settings.
 */
public class Order {

  private UUID id;
  private UUID accountId;
  private String clientOrderId;
  private String symbol;
  private String side;
  private int qty;
  private String type;
  private BigDecimal limitPrice;
  private String timeInForce;
  private String status;
  private int filledQty;
  private BigDecimal avgFillPrice;
  private Instant createdAt;

  /**
   * Default constructor.
   */
  public Order() {
  }

  /**
   * Gets the order identifier.
   *
   * @return order ID
   */
  public UUID getId() {
    return id;
  }

  /**
   * Sets the order identifier.
   *
   * @param id order ID
   */
  public void setId(UUID id) {
    this.id = id;
  }

  /**
   * Gets the account identifier.
   *
   * @return account ID
   */
  public UUID getAccountId() {
    return accountId;
  }

  /**
   * Sets the account identifier.
   *
   * @param accountId account ID
   */
  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

  /**
   * Gets the client order identifier.
   *
   * @return client order ID
   */
  public String getClientOrderId() {
    return clientOrderId;
  }

  /**
   * Sets the client order identifier.
   *
   * @param clientOrderId client order ID
   */
  public void setClientOrderId(String clientOrderId) {
    this.clientOrderId = clientOrderId;
  }

  /**
   * Gets the trading symbol.
   *
   * @return symbol
   */
  public String getSymbol() {
    return symbol;
  }

  /**
   * Sets the trading symbol.
   *
   * @param symbol trading symbol
   */
  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

  /**
   * Gets the order side (BUY/SELL).
   *
   * @return order side
   */
  public String getSide() {
    return side;
  }

  /**
   * Sets the order side (BUY/SELL).
   *
   * @param side order side
   */
  public void setSide(String side) {
    this.side = side;
  }

  /**
   * Gets the order quantity.
   *
   * @return quantity
   */
  public int getQty() {
    return qty;
  }

  /**
   * Sets the order quantity.
   *
   * @param qty quantity
   */
  public void setQty(int qty) {
    this.qty = qty;
  }

  /**
   * Gets the order type (MARKET/LIMIT).
   *
   * @return order type
   */
  public String getType() {
    return type;
  }

  /**
   * Sets the order type (MARKET/LIMIT).
   *
   * @param type order type
   */
  public void setType(String type) {
    this.type = type;
  }

  /**
   * Gets the limit price (for LIMIT orders).
   *
   * @return limit price
   */
  public BigDecimal getLimitPrice() {
    return limitPrice;
  }

  /**
   * Sets the limit price (for LIMIT orders).
   *
   * @param limitPrice limit price
   */
  public void setLimitPrice(BigDecimal limitPrice) {
    this.limitPrice = limitPrice;
  }

  /**
   * Gets the time in force (DAY/GTC/IOC/FOK).
   *
   * @return time in force
   */
  public String getTimeInForce() {
    return timeInForce;
  }

  /**
   * Sets the time in force (DAY/GTC/IOC/FOK).
   *
   * @param timeInForce time in force
   */
  public void setTimeInForce(String timeInForce) {
    this.timeInForce = timeInForce;
  }

  /**
   * Gets the order status (NEW/FILLED/CANCELLED).
   *
   * @return order status
   */
  public String getStatus() {
    return status;
  }

  /**
   * Sets the order status (NEW/FILLED/CANCELLED).
   *
   * @param status order status
   */
  public void setStatus(String status) {
    this.status = status;
  }

  /**
   * Gets the filled quantity.
   *
   * @return filled quantity
   */
  public int getFilledQty() {
    return filledQty;
  }

  /**
   * Sets the filled quantity.
   *
   * @param filledQty filled quantity
   */
  public void setFilledQty(int filledQty) {
    this.filledQty = filledQty;
  }

  /**
   * Gets the average fill price.
   *
   * @return average fill price
   */
  public BigDecimal getAvgFillPrice() {
    return avgFillPrice;
  }

  /**
   * Sets the average fill price.
   *
   * @param avgFillPrice average fill price
   */
  public void setAvgFillPrice(BigDecimal avgFillPrice) {
    this.avgFillPrice = avgFillPrice;
  }

  /**
   * Gets the order creation timestamp.
   *
   * @return creation timestamp
   */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the order creation timestamp.
   *
   * @param createdAt creation timestamp
   */
  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public String toString() {
    return "Order{" +
        "id=" + id +
        ", accountId=" + accountId +
        ", clientOrderId='" + clientOrderId + '\'' +
        ", symbol='" + symbol + '\'' +
        ", side='" + side + '\'' +
        ", qty=" + qty +
        ", type='" + type + '\'' +
        ", limitPrice=" + limitPrice +
        ", timeInForce='" + timeInForce + '\'' +
        ", status='" + status + '\'' +
        ", filledQty=" + filledQty +
        ", avgFillPrice=" + avgFillPrice +
        ", createdAt=" + createdAt +
        '}';
  }
}