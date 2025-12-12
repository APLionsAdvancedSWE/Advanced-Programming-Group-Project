package com.dev.tradingapi.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.dto.AccountCreateRequest;
import com.dev.tradingapi.dto.AccountCreateResponse;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.repository.AccountRepository;
import com.dev.tradingapi.service.AccountService;
import com.dev.tradingapi.service.PnlService;
import com.dev.tradingapi.service.PositionService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unit tests for {@link AccountController} covering account creation, positions, and pnl.
 */
@ExtendWith(MockitoExtension.class)
class AccountControllerTest {

  @Mock
  private PnlService pnlService;

  @Mock
  private PositionService positionService;

  @Mock
  private AccountRepository accountRepository;

  private AccountService accountService;

  private AccountController accountController;

  @BeforeEach
  void setUp() {
    accountService = new AccountService(accountRepository);
    accountController = new AccountController(pnlService, positionService, accountService);
  }

  @Test
  void createAccount_Success_ReturnsCreatedWithAuthToken() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setName("Test User");
    request.setUsername("testuser1");
    request.setPassword("s3cr3tPass!");

    when(accountRepository.findByUsername("testuser1")).thenReturn(Optional.empty());
    when(accountRepository.save(anyString(), anyString(), anyString(), anyString(),
      org.mockito.ArgumentMatchers.any(BigDecimal.class)))
        .thenAnswer(invocation -> {
          UUID id = UUID.randomUUID();
          String name = invocation.getArgument(0);
          String username = invocation.getArgument(1);
          String authToken = invocation.getArgument(3);
            return new com.dev.tradingapi.model.Account(id, name, authToken, 0,
              java.math.BigDecimal.ZERO, 0,
              java.time.Instant.now(), java.math.BigDecimal.ZERO,
              java.math.BigDecimal.ZERO);
        });

    ResponseEntity<AccountCreateResponse> response = accountController.createAccount(request);

    assertNotNull(response);
    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertNotNull(response.getBody());
    assertEquals("Test User", response.getBody().getName());
    assertEquals("testuser1", response.getBody().getUsername());
    assertNotNull(response.getBody().getId());
    assertNotNull(response.getBody().getAuthToken());
  }

  @Test
  void createAccount_MissingUsernameOrPassword_BadRequest() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setName("Bad User");
    request.setUsername("");
    request.setPassword("");

    try {
      accountController.createAccount(request);
    } catch (ResponseStatusException ex) {
      assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    verify(accountRepository, never())
      .save(anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void createAccount_NullUsername_BadRequest() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setUsername(null);
    request.setPassword("validPassword");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.createAccount(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void createAccount_BlankUsername_BadRequest() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setUsername("   ");
    request.setPassword("validPassword");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.createAccount(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void createAccount_NullPassword_BadRequest() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setUsername("user1");
    request.setPassword(null);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.createAccount(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void createAccount_BlankPassword_BadRequest() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setUsername("user1");
    request.setPassword("   ");

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.createAccount(request));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  @Test
  void createAccount_UsernameAlreadyExists_Conflict() {
    AccountCreateRequest request = new AccountCreateRequest();
    request.setName("Existing User");
    request.setUsername("existing");
    request.setPassword("password");

    when(accountRepository.findByUsername("existing"))
      .thenReturn(Optional.of(new com.dev.tradingapi.model.Account(
        UUID.randomUUID(), "Existing User", "api-key", 0,
        java.math.BigDecimal.ZERO, 0,
        java.time.Instant.now(), java.math.BigDecimal.ZERO,
        java.math.BigDecimal.ZERO)));

    try {
      accountController.createAccount(request);
    } catch (ResponseStatusException ex) {
      assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
    }

    verify(accountRepository, never())
      .save(anyString(), anyString(), anyString(), anyString(), any());
  }

  @Test
  void positions_ReturnsList_WhenPositionsExist() {
    UUID accountId = UUID.randomUUID();
    Position position = new Position(accountId, "AAPL", 100, BigDecimal.valueOf(150));
    when(positionService.getByAccountId(eq(accountId)))
        .thenReturn(java.util.List.of(position));

    java.util.List<Position> result = accountController.positions(accountId);

    assertNotNull(result);
    assertEquals(1, result.size());
    assertEquals("AAPL", result.get(0).getSymbol());
  }

  @Test
  void positions_ThrowsNotFound_WhenNullReturned() {
    UUID accountId = UUID.randomUUID();
    when(positionService.getByAccountId(eq(accountId)))
        .thenReturn(null);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.positions(accountId));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void pnl_ReturnsValue_WhenPresent() {
    UUID accountId = UUID.randomUUID();
    when(pnlService.getForAccount(eq(accountId)))
        .thenReturn(new BigDecimal("123.45"));

    BigDecimal result = accountController.pnl(accountId);

    assertNotNull(result);
    assertEquals(new BigDecimal("123.45"), result);
  }

  @Test
  void pnl_ThrowsNotFound_WhenNull() {
    UUID accountId = UUID.randomUUID();
    when(pnlService.getForAccount(eq(accountId)))
        .thenReturn(null);

    ResponseStatusException ex = assertThrows(ResponseStatusException.class,
        () -> accountController.pnl(accountId));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  void handleIllegalState_ReturnsInternalServerErrorWithMessage() {
    IllegalStateException ex = new IllegalStateException("SHA-256 algorithm not available");

    ResponseEntity<String> response = accountController.handleIllegalState(ex);

    assertNotNull(response);
    assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    assertEquals("SHA-256 algorithm not available", response.getBody());
  }
}
