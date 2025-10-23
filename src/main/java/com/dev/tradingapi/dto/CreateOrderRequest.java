package com.dev.tradingapi.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Represents the JSON body for creating a new order.
 */
public class CreateOrderRequest {

  private UUID accountId;
  private String clientOrderId;
  private String symbol;
  private String side;           // BUY / SELL
  private int qty;
  private String type;           // MARKET / LIMIT / TWAP
  private BigDecimal limitPrice;
  private String timeInForce;

  /**
   * Default constructor.
   */
  public CreateOrderRequest() {
    // Default constructor for framework use
  }

  /**
   * Constructs a CreateOrderRequest with all fields.
   *
   * @param accountId account identifier
   * @param clientOrderId client order identifier
   * @param symbol trading symbol
   * @param side buy or sell side
   * @param qty order quantity
   * @param type order type
   * @param limitPrice limit price for limit orders
   * @param timeInForce time in force setting
   */
  public CreateOrderRequest(UUID accountId, String clientOrderId, String symbol, String side,
                            int qty, String type, BigDecimal limitPrice, String timeInForce) {
    this.accountId = accountId;
    this.clientOrderId = clientOrderId;
    this.symbol = symbol;
    this.side = side;
    this.qty = qty;
    this.type = type;
    this.limitPrice = limitPrice;
    this.timeInForce = timeInForce;
  }

 
  public UUID getAccountId() {
    return accountId;
  }

 
  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }

 
  public String getClientOrderId() {
    return clientOrderId;
  }

  
  public void setClientOrderId(String clientOrderId) {
    this.clientOrderId = clientOrderId;
  }


  public String getSymbol() {
    return symbol;
  }

 
  public void setSymbol(String symbol) {
    this.symbol = symbol;
  }

 
  public String getSide() {
    return side;
  }

 
  public void setSide(String side) {
    this.side = side;
  }

  
  public int getQty() {
    return qty;
  }


  public void setQty(int qty) {
    this.qty = qty;
  }

  public String getType() {
    return type;
  }


  public void setType(String type) {
    this.type = type;
  }


  public BigDecimal getLimitPrice() {
    return limitPrice;
  }

 
  public void setLimitPrice(BigDecimal limitPrice) {
    this.limitPrice = limitPrice;
  }


  public String getTimeInForce() {
    return timeInForce;
  }

  public void setTimeInForce(String timeInForce) {
    this.timeInForce = timeInForce;
  }
}