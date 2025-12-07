/*
THIS IS JUST FOR TESTING TO MAKE SURE DIFFERENT SERVICES WORK.
This can be removed once tests cover the PnlService, AccountService, Position Service,
 and AccountController.
*/

package com.dev.tradingapi;

import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.service.AccountService;
import com.dev.tradingapi.service.PnlService;
import com.dev.tradingapi.service.PositionService;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Creates a demo account and seeds it with AAPL and AMZN fills at startup.
 */
@Component
public class StartupDataLoader {

  private final AccountService accountService;
  private final PositionService positionService;

  /**
   * Creates a new data loader used to seed demo trading data.
   *
   * @param accountService service used to persist and update accounts
   * @param positionService service used to apply demo fills and positions
   */
  public StartupDataLoader(AccountService accountService,
                           PositionService positionService) {
    this.accountService = accountService;
    this.positionService = positionService;
  }

  /**
   * Initializes demo trading data at application startup.
   * <p>
   * This method seeds the system with a single demo account and
   * a pair of starter positions to verify PnL and quote integration logic.
   * </p>
   * <ul>
   *   <li>Creates a mock {@code Account} with an initial cash balance of $100,000.</li>
   *   <li>Applies two simulated fills:
   *     <ul>
   *       <li>Buy 10 shares of AAPL at $190.00</li>
   *       <li>Buy 5 shares of AMZN at $140.00</li>
   *     </ul>
   *   </li>
   *   <li>Adjusts cash balances accordingly to reflect the trades.</li>
   * </ul>
   * <p>
   * The generated account ID and seeded trade details are printed to the console.
   * </p>
   * <p>
   * Used primarily for local development and validation of the
   * {@link PositionService}, {@link AccountService}, and {@link PnlService} integration.
   * </p>
   */
  @PostConstruct
  public void init() {
    // Create demo account
    UUID accId = UUID.randomUUID();
    Account account = new Account(
            accId,
            "Demo Account",
            "demo-api-key",
            1000,
            new BigDecimal("1000000"),
            10000,
            Instant.now(),
            new BigDecimal("100000.00")
    );
    // Save account to database via AccountService (which uses AccountRepository)
    accountService.save(account);

    // Seed fills for AAPL and AMZN (positive = buy)
    // AAPL: buy 10 @ 190.00
    int aaplQty = 10;
    BigDecimal aaplPrice = new BigDecimal("190.00");
    positionService.applyFill(accId, "AAPL", aaplQty, aaplPrice);
    // adjust cash: buys reduce cash (accountService.adjustCash expects delta)
    accountService.adjustCash(accId, aaplPrice.multiply(BigDecimal.valueOf(aaplQty)).negate());

    // AMZN: buy 5 @ 140.00
    int amznQty = 5;
    BigDecimal amznPrice = new BigDecimal("140.00");
    positionService.applyFill(accId, "AMZN", amznQty, amznPrice);
    accountService.adjustCash(accId, amznPrice.multiply(BigDecimal.valueOf(amznQty)).negate());

    System.out.println("=== Demo account created ===");
    System.out.println("accountId = " + accId);
    System.out.println("AAPL qty = " + aaplQty + " @ " + aaplPrice);
    System.out.println("AMZN qty = " + amznQty + " @ " + amznPrice);
    System.out.println("============================");
  }
}
