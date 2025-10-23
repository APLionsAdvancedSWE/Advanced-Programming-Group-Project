package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * <p><b>PnLService</b> — Computes total profit and loss for an account.</p>
 *
 * <p>Formula:</p>
 * <pre>
 *   totalPnL = (cashBalance + Σ(currentPrice(symbol) × qty)) - initialBalance
 * </pre>
 *
 * <p>Depends on MarketService for latest prices.</p>
 */
@Service
public class PnlService {

  private final MarketService marketService;
  private final AccountService accountService;
  private final PositionService positionService;

  private static final MathContext MC = new MathContext(34, RoundingMode.HALF_UP);
  private static final int SCALE = 10;

  /**
   * Constructs a new {@code PnlService} instance with its required dependencies.
   * <p>
   * The PnL (Profit and Loss) service relies on three core components:
   * </p>
   * <ul>
   *   <li>{@link MarketService} — provides
   *   simulated market quotes for symbol valuation</li>
   *   <li>{@link AccountService} — retrieves account
   *   information including balances and limits</li>
   *   <li>{@link PositionService} — supplies current
   *   open positions used to calculate unrealized PnL</li>
   * </ul>
   * All parameters are validated to be non-null to ensure reliable service operation.
   *
   * @param marketService   the market data provider used to obtain latest quotes
   * @param accountService  the account manager used to access account details
   * @param positionService the position manager used to fetch open positions for valuation
   * @throws NullPointerException if any of the provided services are {@code null}
   */
  public PnlService(MarketService marketService,
                    AccountService accountService,
                    PositionService positionService) {
    this.marketService = Objects.requireNonNull(marketService);
    this.accountService = Objects.requireNonNull(accountService);
    this.positionService = Objects.requireNonNull(positionService);
  }

  /**
   * Calculates the total profit or loss for the given account.
   *
   * @param accountId the unique ID of the account
   * @return total PnL value as a BigDecimal
   */
  public BigDecimal getForAccount(UUID accountId) {
    Account account = accountService.getById(accountId);
    List<Position> positions = positionService.getByAccountId(accountId);

    BigDecimal portfolioValue = BigDecimal.ZERO;

    for (Position p : positions) {
      if (!p.getAccountId().equals(accountId)) {
        continue;
      }

      Quote quote = marketService.getQuote(p.getSymbol());
      if (quote == null || quote.getLast() == null) {
        continue;
      }

      BigDecimal markPrice = quote.getLast();
      BigDecimal positionValue = markPrice.multiply(BigDecimal.valueOf(p.getQty()), MC);
      portfolioValue = portfolioValue.add(positionValue, MC);
    }

    BigDecimal equity = account.getCashBalance().add(portfolioValue, MC);
    return equity.subtract(account.getInitialBalance(), MC)
            .setScale(SCALE, RoundingMode.HALF_UP);
  }
}
