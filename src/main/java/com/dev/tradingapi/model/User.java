package com.dev.tradingapi.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * Represents a system user with authentication credentials and role-based access.
 * Links to Account entity for traders.
 */
@Entity
@Table(name = "users")
public class User {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(unique = true, nullable = false)
  private String username;

  @Column(nullable = false)
  private String password; // Stored as BCrypt hash

  @Column(nullable = false)
  private String role; // SEC_INVESTIGATOR, ADMIN, TRADER

  @Column(name = "account_id")
  private UUID accountId; // Foreign key to accounts table (nullable for non-traders)

  /**
   * Default constructor for JPA.
   * Required by JPA specification for entity instantiation.
   */
  public User() {
    // Default constructor for JPA
  }

  /**
   * Creates a new user with the specified credentials and role.
   *
   * @param username the username
   * @param password the password (will be hashed)
   * @param role the user role
   * @param accountId the linked account ID (null for non-traders)
   */
  public User(String username, String password, String role, UUID accountId) {
    this.username = username;
    this.password = password;
    this.role = role;
    this.accountId = accountId;
  }

  // Getters and Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public void setAccountId(UUID accountId) {
    this.accountId = accountId;
  }
}