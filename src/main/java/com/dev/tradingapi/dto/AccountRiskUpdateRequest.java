package com.dev.tradingapi.dto;

import java.math.BigDecimal;

/**
 * Request payload for updating account risk limits.
 */
public class AccountRiskUpdateRequest {

  private Integer maxOrderQty;
  private BigDecimal maxNotional;
  private Integer maxPositionQty;

  public Integer getMaxOrderQty() {
    return maxOrderQty;
  }

  public void setMaxOrderQty(Integer maxOrderQty) {
    this.maxOrderQty = maxOrderQty;
  }

  public BigDecimal getMaxNotional() {
    return maxNotional;
  }

  public void setMaxNotional(BigDecimal maxNotional) {
    this.maxNotional = maxNotional;
  }

  public Integer getMaxPositionQty() {
    return maxPositionQty;
  }

  public void setMaxPositionQty(Integer maxPositionQty) {
    this.maxPositionQty = maxPositionQty;
  }
}
