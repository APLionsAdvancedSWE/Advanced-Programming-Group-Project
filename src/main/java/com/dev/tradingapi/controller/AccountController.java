package com.dev.tradingapi.controller;

import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.service.AccountService;
import com.dev.tradingapi.service.PnlService;
import com.dev.tradingapi.service.PositionService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * REST controller providing account-related endpoints.
 *
 * <p>This controller exposes basic account operations such as:</p>
 * <ul>
 *   <li>Retrieving open positions for a given account</li>
 *   <li>Calculating total profit and loss (PnL) for a given account</li>
 * </ul>
 *
 * <p>Acts as a high-level API layer that delegates core logic to the
 * {@link AccountService}, {@link PositionService}, and {@link PnlService}.</p>
 */
@RestController
@RequestMapping("/accounts")
public class AccountController {

  private final PnlService pnlService;
  private final PositionService positionService;

  /**
   * Constructs a new {@code AccountController} with the required services.
   *
   * @param pnlService service responsible for computing total PnL
   * @param positionService service managing open positions per account
   */
  public AccountController(PnlService pnlService, PositionService positionService) {
    this.positionService = positionService;
    this.pnlService = pnlService;
  }

  /**
   * Retrieves all open positions for a specific account.
   *
   * @param accountId the unique account identifier
   * @return list of active {@link Position} objects associated with the account
   */
  @GetMapping("/{accountId}/positions")
  public List<Position> positions(@PathVariable UUID accountId) {
    List<Position> positions = positionService.getByAccountId(accountId);
    if (positions == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
              "No positions found for account with Id: " + accountId);
    }
    return positions;
  }

  /**
   * Calculates and returns the total profit or loss for the specified account.
   *
   * @param accountId the unique account identifier
   * @return total PnL value as a {@link BigDecimal}
   */
  @GetMapping("/{accountId}/pnl")
  public BigDecimal pnl(@PathVariable UUID accountId) {
    BigDecimal pnlNum = pnlService.getForAccount(accountId);
    if (pnlNum == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
              "Account with Id: " + accountId + " was not found");
    }
    return pnlNum;
  }
}
