package com.dev.tradingapi.service;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Service for validating trading orders against risk limits.
 */
@Service
public class RiskService {

  private final AccountService accountService;
  private final PositionService positionService;

  public RiskService(AccountService accountService, PositionService positionService) {
    this.accountService = accountService;
    this.positionService = positionService;
  }

  /**
   * Validates an order request against risk limits.
   *
   * @param req the order request to validate
   * @param mark the current market quote
   * @throws RiskException if the order violates risk limits
   */
  public void validate(CreateOrderRequest req, Quote mark) {
    // Basic market liquidity check
    if (req.getQty() > mark.getVolume()) {
      throw new RiskException("Quantity is greater than the market quantity");
    }

    UUID accountId = req.getAccountId();
    Account account = accountService.getById(accountId);
    if (account == null) {
      throw new RiskException("Account not found for risk validation: " + accountId);
    }

    // 1) Per-order max quantity
    int maxOrderQty = account.getMaxOrderQty();
    if (maxOrderQty > 0 && req.getQty() > maxOrderQty) {
      throw new RiskException("Order quantity " + req.getQty()
          + " exceeds maxOrderQty " + maxOrderQty);
    }

    // 2) Per-order notional limit
    BigDecimal maxNotional = account.getMaxNotional();
    if (maxNotional != null && maxNotional.signum() > 0) {
      BigDecimal price = mark.getLast();
      if (price != null) {
        BigDecimal notional = price.multiply(BigDecimal.valueOf(req.getQty()));
        if (notional.compareTo(maxNotional) > 0) {
          throw new RiskException("Order notional " + notional
              + " exceeds maxNotional " + maxNotional);
        }
      }
    }

    // 3) Resulting position limit per symbol
    int maxPositionQty = account.getMaxPositionQty();
    if (maxPositionQty > 0) {
      Position existing = positionService.get(accountId, req.getSymbol());
      int currentQty = existing != null ? existing.getQty() : 0;
      int signedQty = "SELL".equalsIgnoreCase(req.getSide())
          ? -req.getQty() : req.getQty();
      int newQty = currentQty + signedQty;
      if (Math.abs(newQty) > maxPositionQty) {
        throw new RiskException("Resulting position quantity " + newQty
            + " exceeds maxPositionQty " + maxPositionQty);
      }
    }
  }
}