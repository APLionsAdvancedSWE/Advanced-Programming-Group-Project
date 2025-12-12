package com.dev.tradingapi.dto;

import java.math.BigDecimal;

/**
 * Request payload for creating a new account with username, password,
 * and optional initial balance.
 */
public class AccountCreateRequest {
  private String name;
  private String username;
  private String password;
  private BigDecimal initialBalance;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public BigDecimal getInitialBalance() {
    return initialBalance;
  }

  public void setInitialBalance(BigDecimal initialBalance) {
    this.initialBalance = initialBalance;
  }
}
