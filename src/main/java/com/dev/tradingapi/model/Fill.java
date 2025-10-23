package com.dev.tradingapi.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Represents a fill of an order.
 * A fill occurs when an order is partially or completely executed at a specific price and quantity.
 */
public class Fill {

  private UUID id;
  private UUID orderId;
  private int qty;
  private BigDecimal price;
  private Instant ts;

  /**
   * Default constructor.
   */
  public Fill() {
    // Default constructor for framework use
  }

  /**
   * Constructs a Fill with all required fields.
   *
   * @param id unique fill identifier
   * @param orderId associated order identifier
   * @param qty quantity filled
   * @param price execution price
   * @param ts timestamp of execution
   */
  public Fill(UUID id, UUID orderId, int qty, BigDecimal price, Instant ts) {
    this.id = id;
    this.orderId = orderId;
    this.qty = qty;
    this.price = price;
    this.ts = ts;
  }

 
  public UUID getId() {
    return id;
  }


  public void setId(UUID id) {
    this.id = id;
  }

 
  public UUID getOrderId() {
    return orderId;
  }

 
  public void setOrderId(UUID orderId) {
    this.orderId = orderId;
  }

 
  public int getQty() {
    return qty;
  }


  public void setQty(int qty) {
    this.qty = qty;
  }

  
  public BigDecimal getPrice() {
    return price;
  }


  public void setPrice(BigDecimal price) {
    this.price = price;
  }


  public Instant getTs() {
    return ts;
  }


  public void setTs(Instant ts) {
    this.ts = ts;
  }

  @Override
  public String toString() {
    return "Fill{"
        + "id=" + id
        + ", orderId=" + orderId
        + ", qty=" + qty
        + ", price=" + price
        + ", ts=" + ts
        + '}';
  }
}