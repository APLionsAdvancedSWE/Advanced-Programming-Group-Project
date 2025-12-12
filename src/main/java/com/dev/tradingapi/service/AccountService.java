package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * <p><b>AccountService</b> — Manages account state, balances, and metadata.</p>
 *
 * <p>Responsibilities:</p>
 * <ul>
 *   <li>Store and retrieve account objects</li>
 *   <li>Maintain cash balances</li>
 *   <li>Adjust cash after fills or fees</li>
 * </ul>
 */
@Service
public class AccountService {

  /** Map of accountId → Account object. */
  private final Map<UUID, Account> accounts = new ConcurrentHashMap<>();
  private final AccountRepository accountRepository;


  public AccountService(AccountRepository accountRepository) {
    this.accountRepository = accountRepository;
  }

  /**
   * Registers or updates an account.
   *
   * @param account the account to add or replace
   */
  public void save(Account account) {
    accounts.put(account.getId(), account);
  }

  /**
   * Checks whether an account already exists for the provided username.
   *
   * @param username username to check
   * @return true if an account with this username exists, false otherwise
   */
  public boolean usernameExists(String username) {
    return accountRepository.findByUsername(username).isPresent();
  }

  /**
   * Creates and persists a new account with the given properties.
   * The initial balance is also used as the starting cash balance.
   *
   * @param name display name for the account
   * @param username login username
   * @param passwordHash hashed password
   * @param initialBalance starting cash balance
   * @param authToken API authentication token
   * @return the newly created Account
   */
  public Account createAccount(String name,
                               String username,
                               String passwordHash,
                               BigDecimal initialBalance,
                               String authToken) {
    Account account = accountRepository.save(name, username, passwordHash, authToken,
        initialBalance != null ? initialBalance : BigDecimal.ZERO);
    save(account);
    return account;
  }

  /**
   * Returns the account for the given ID.
   *
   * @param accountId the unique account ID
   * @return the Account object, or null if not found
   */
  public Account getById(UUID accountId) {
    Account cached = accounts.get(accountId);
    if (cached != null) {
      return cached;
    }

    return accountRepository.findById(accountId)
        .map(acc -> {
          accounts.put(accountId, acc);
          return acc;
        })
        .orElse(null);
  }

  /**
   * Adjusts the cash balance of an account.
   * Positive amount = deposit, Negative = withdrawal or trade cost.
   *
   * @param accountId account ID
   * @param delta amount to add/subtract
   */
  public void adjustCash(UUID accountId, BigDecimal delta) {
    Account a = getById(accountId);
    if (a == null) {
      throw new IllegalArgumentException("Account not found: " + accountId);
    }
    a.adjustCashBalance(delta);
    // Persist updated cash balance to the database.
    accountRepository.updateCashBalance(accountId, a.getCashBalance());
  }

  /**
   * Returns all registered accounts.
   *
   * @return list of Account objects
   */
  public Iterable<Account> getAll() {
    return accounts.values();
  }

  /**
   * Updates the risk limits for the specified account. Any null values in the
   * arguments will preserve the existing value.
   *
   * @param accountId account identifier
   * @param maxOrderQty new max order quantity (nullable)
   * @param maxNotional new max notional (nullable)
   * @param maxPositionQty new max position quantity (nullable)
   * @return the updated Account
   */
  public Account updateRiskLimits(UUID accountId, Integer maxOrderQty,
                                  BigDecimal maxNotional,
                                  Integer maxPositionQty) {
    Account inMemory = accounts.get(accountId);
    if (inMemory == null) {
      Account fromDb = accountRepository.findById(accountId)
          .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
      accounts.put(accountId, fromDb);
      inMemory = fromDb;
    }

    int newMaxOrderQty = maxOrderQty != null ? maxOrderQty : inMemory.getMaxOrderQty();
    BigDecimal newMaxNotional = maxNotional != null ? maxNotional : inMemory.getMaxNotional();
    int newMaxPositionQty = maxPositionQty != null ? maxPositionQty : inMemory.getMaxPositionQty();

    inMemory.setMaxOrderQty(newMaxOrderQty);
    inMemory.setMaxNotional(newMaxNotional);
    inMemory.setMaxPositionQty(newMaxPositionQty);

    accountRepository.updateRiskLimits(accountId, newMaxOrderQty, newMaxNotional,
        newMaxPositionQty);

    return inMemory;
  }
}

