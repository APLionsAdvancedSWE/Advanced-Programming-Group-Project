package com.dev.tradingapi.controller;

import com.dev.tradingapi.dto.AccountCreateRequest;
import com.dev.tradingapi.dto.AccountCreateResponse;
import com.dev.tradingapi.dto.AccountRiskUpdateRequest;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.service.AccountService;
import com.dev.tradingapi.service.PnlService;
import com.dev.tradingapi.service.PositionService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
  private final AccountService accountService;

  /**
   * Constructs a new {@code AccountController} with the required services.
   *
   * @param pnlService service responsible for computing total PnL
   * @param positionService service managing open positions per account
   * @param accountService service providing account data and balances
   */
  public AccountController(PnlService pnlService,
                          PositionService positionService,
                          AccountService accountService) {
    this.positionService = positionService;
    this.pnlService = pnlService;
    this.accountService = accountService;
  }

  /**
   * Creates a new trading account.
   *
   * @param request payload containing username, password, and optional display name
   * @return details of the newly created account, including an auth token
   */
  @PostMapping("/create")
  public ResponseEntity<AccountCreateResponse> createAccount(
      @RequestBody AccountCreateRequest request) {
    if (request.getName() == null || request.getName().isBlank()
          || request.getUsername() == null || request.getUsername().isBlank()
          || request.getPassword() == null || request.getPassword().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "Name, username, and password must be provided");
    }

    if (accountService.usernameExists(request.getUsername())) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
              "Username already exists");
    }

    String passwordHash = hashPassword(request.getPassword());

    String name = request.getName() != null && !request.getName().isBlank()
        ? request.getName() : request.getUsername();

    BigDecimal initialBalance = request.getInitialBalance() != null
        ? request.getInitialBalance()
        : BigDecimal.ZERO;

    String authToken = UUID.randomUUID().toString();

    var account = accountService.createAccount(name, request.getUsername(),
        passwordHash, initialBalance, authToken);

    return ResponseEntity.status(HttpStatus.CREATED)
        .body(new AccountCreateResponse(account.getId(), name,
            request.getUsername(), authToken));
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

  /**
   * Sets or updates the risk limits for an account.
   * Any fields omitted in the request will leave the existing value unchanged.
   */
  @PostMapping("/{accountId}/risk")
  public ResponseEntity<Void> updateRiskLimits(@PathVariable UUID accountId,
                                               @RequestBody AccountRiskUpdateRequest request) {
    try {
      accountService.updateRiskLimits(accountId,
          request.getMaxOrderQty(),
          request.getMaxNotional(),
          request.getMaxPositionQty());
      return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException ex) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
    }
  }

  private String hashPassword(String password) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hashBytes = digest.digest(password.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hashBytes);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  @ExceptionHandler(IllegalStateException.class)
  public ResponseEntity<String> handleIllegalState(IllegalStateException ex) {
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ex.getMessage());
  }
}
