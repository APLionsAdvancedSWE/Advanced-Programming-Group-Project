package com.dev.tradingapi.dto;

import java.util.UUID;

/**
 * Response payload returned after successfully creating a new account.
 */
public class AccountCreateResponse {
  private UUID id;
  private String name;
  private String username;
  private String authToken;

  /**
   * Constructs a response describing a newly created account.
   *
   * @param id unique identifier of the account
   * @param name display name of the account holder
   * @param username login username associated with the account
   * @param authToken API authentication token issued for the account
   */
  public AccountCreateResponse(UUID id, String name, String username, String authToken) {
    this.id = id;
    this.name = name;
    this.username = username;
    this.authToken = authToken;
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getUsername() {
    return username;
  }

  public String getAuthToken() {
    return authToken;
  }
}
