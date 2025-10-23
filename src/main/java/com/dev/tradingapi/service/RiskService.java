package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Quote;

/**
 * Service for validating trading orders against risk limits.
 */
public class RiskService {

  /**
   * Validates an order request against risk limits.
   *
   * @param req the order request to validate
   * @param mark the current market quote
   * @throws RiskException if the order violates risk limits
   */
  public void validate(CreateOrderRequest req, Quote mark) {
    if (req.getQty() > mark.getVolume()) {
      throw new RiskException("Quantity is greater than the market quantity");
    }
  }
}