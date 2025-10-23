package com.dev.tradingapi.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * This class defines the Position model.
 */
public class Position {

  private UUID accountId;
  private String symbol;
  private int qty;
  private BigDecimal avgCost;

  /**
   * Constructs a new Position object.
   *
   * @param accountId the owning account's unique ID
   * @param symbol the symbol/ticker for this position
   * @param qty number of shares/contracts held
   * @param avgCost average cost per share/contract
   */
  public Position(UUID accountId, String symbol, int qty, BigDecimal avgCost) {
    this.accountId = accountId;
    this.symbol = symbol;
    this.qty = qty;
    this.avgCost = avgCost;
  }

  /**
   * Returns the unique account id that owns this position.
   * This links the position to a specific trading account.
   *
   * @return the account ID associated with this position
   */
  public UUID getAccountId() {
    return accountId;
  }

  /**
   * Returns the trading symbol or ticker for this position.
   * Example: "AAPL", "TSLA", or "MSFT".
   *
   * @return the trading symbol for this position
   */
  public String getSymbol() {
    return symbol;
  }

  /**
   * Returns the total quantity of shares or contracts currently held for this position.
   *
   * @return the total position quantity
   */
  public int getQty() {
    return qty;
  }

  /**
   * Returns the average cost basis per share or contract for this position.
   * This value is used to calculate mark-to-market profit and loss.
   *
   * @return the average cost per unit
   */
  public BigDecimal getAvgCost() {
    return avgCost;
  }

  /**
   * Updates the total quantity for this position.
   *
   * @param qty the new total quantity held
   */
  public void setQty(int qty) {
    this.qty = qty;
  }

  /**
   * Updates the average cost basis for this position.
   * Should reflect the weighted average purchase price.
   *
   * @param avgCost the new average cost per share or contract
   */
  public void setAvgCost(BigDecimal avgCost) {
    this.avgCost = avgCost;
  }

  /**
   * Updates the position after a trade fill.
   * This recalculates the weighted average cost based on fill price and quantity.
   *
   * @param fillQty the number of units bought/sold (positive for buy, negative for sell)
   * @param fillPrice the price per unit for this fill
   */
  public void updateWithFill(int fillQty, BigDecimal fillPrice) {
    // only recalc avg cost if the trade adds to the position
    if (qty + fillQty != 0) {
      BigDecimal totalCostBefore = avgCost.multiply(BigDecimal.valueOf(qty));
      BigDecimal totalCostNew = fillPrice.multiply(BigDecimal.valueOf(fillQty));
      BigDecimal newAvgCost = totalCostBefore.add(totalCostNew)
              .divide(BigDecimal.valueOf(qty + fillQty), RoundingMode.HALF_UP);
      this.avgCost = newAvgCost;
    }
    this.qty += fillQty;
  }
}