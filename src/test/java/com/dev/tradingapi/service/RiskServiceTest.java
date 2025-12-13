package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dev.tradingapi.dto.CreateOrderRequest;
import com.dev.tradingapi.exception.RiskException;
import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for RiskService.
 * Tests order validation against risk limits with minimal essential cases.
 */
@ExtendWith(MockitoExtension.class)
class RiskServiceTest {
  @Mock
  private AccountService accountService;

  @Mock
  private PositionService positionService;

  private RiskService riskService;

  @BeforeEach
  void setUp() {
    riskService = new RiskService(accountService, positionService);
  }

  @Test
  void testValidate_ValidQuantity_ShouldPass() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-001", "AAPL", "BUY", 100, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    // Account limits high enough to allow this order
    Account account = new Account(accountId, "Test", "token", 1000,
        new BigDecimal("1000000"), 10000, Instant.now(),
        new BigDecimal("50000"), new BigDecimal("50000"));
    Mockito.when(accountService.getById(accountId)).thenReturn(account);

    assertDoesNotThrow(() -> riskService.validate(request, quote));
  }

  @Test
  void testValidate_QuantityExceedsVolume_ShouldThrowException() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-002", "AAPL", "BUY", 1500, "MARKET", null, "DAY");
    
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("155.00"), 
        new BigDecimal("148.00"), 
        new BigDecimal("152.00"), 
        1000L, 
        Instant.now());

    RiskException exception = assertThrows(RiskException.class, 
        () -> riskService.validate(request, quote));
    
    assert (exception.getMessage().contains("Quantity is greater than the market quantity"));
  }

  @Test
  void testValidate_ExceedsMaxOrderQty_ShouldThrowException() {
    UUID accountId = UUID.randomUUID();
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-003", "AAPL", "BUY", 200, "MARKET", null, "DAY");

    Quote quote = new Quote("AAPL",
        new BigDecimal("150.00"),
        new BigDecimal("155.00"),
        new BigDecimal("148.00"),
        new BigDecimal("152.00"),
        1000L,
        Instant.now());

    Account account = new Account(accountId, "Test", "token", 100,
        new BigDecimal("1000000"), 10000, Instant.now(),
        new BigDecimal("50000"), new BigDecimal("50000"));
    Mockito.when(accountService.getById(accountId)).thenReturn(account);

    assertThrows(RiskException.class, () -> riskService.validate(request, quote));
  }

  @Test
  void testValidate_ExceedsMaxNotional_ShouldThrowException() {
    UUID accountId = UUID.randomUUID();
    // 100 shares at 200 = 20,000 notional
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-004", "AAPL", "BUY", 100, "MARKET", null, "DAY");

    Quote quote = new Quote("AAPL",
        new BigDecimal("200.00"),
        new BigDecimal("205.00"),
        new BigDecimal("195.00"),
        new BigDecimal("200.00"),
        1000L,
        Instant.now());

    // maxNotional set below required 20,000
    Account account = new Account(accountId, "Test", "token", 1000,
        new BigDecimal("15000.00"), 10000, Instant.now(),
        new BigDecimal("50000"), new BigDecimal("50000"));
    Mockito.when(accountService.getById(accountId)).thenReturn(account);

    assertThrows(RiskException.class, () -> riskService.validate(request, quote));
  }

  @Test
  void testValidate_ExceedsMaxPositionQty_OnBuy_ShouldThrowException() {
    UUID accountId = UUID.randomUUID();
    // Current position is 90, order buys 20 -> new position 110 > maxPositionQty 100
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-005", "AAPL", "BUY", 20, "MARKET", null, "DAY");

    Quote quote = new Quote("AAPL",
        new BigDecimal("100.00"),
        new BigDecimal("101.00"),
        new BigDecimal("99.00"),
        new BigDecimal("100.00"),
        1000L,
        Instant.now());

    Account account = new Account(accountId, "Test", "token", 1000,
        new BigDecimal("1000000"), 100, Instant.now(),
        new BigDecimal("50000"), new BigDecimal("50000"));
    Mockito.when(accountService.getById(accountId)).thenReturn(account);
    // Simulate existing long position of 90
    com.dev.tradingapi.model.Position pos =
        new com.dev.tradingapi.model.Position(accountId, "AAPL", 90, new BigDecimal("95.00"));
    Mockito.when(positionService.get(accountId, "AAPL")).thenReturn(pos);

    assertThrows(RiskException.class, () -> riskService.validate(request, quote));
  }

  @Test
  void testValidate_ExceedsMaxPositionQty_OnSell_ShouldThrowException() {
    UUID accountId = UUID.randomUUID();
    // Current short is -90, order sells 20 more -> -110, abs(110) > maxPositionQty 100
    CreateOrderRequest request = new CreateOrderRequest(
        accountId, "CLIENT-006", "AAPL", "SELL", 20, "MARKET", null, "DAY");

    Quote quote = new Quote("AAPL",
        new BigDecimal("100.00"),
        new BigDecimal("101.00"),
        new BigDecimal("99.00"),
        new BigDecimal("100.00"),
        1000L,
        Instant.now());

    Account account = new Account(accountId, "Test", "token", 1000,
        new BigDecimal("1000000"), 100, Instant.now(),
        new BigDecimal("50000"), new BigDecimal("50000"));
    Mockito.when(accountService.getById(accountId)).thenReturn(account);
    // Simulate existing short position of -90
    com.dev.tradingapi.model.Position pos =
        new com.dev.tradingapi.model.Position(accountId, "AAPL", -90, new BigDecimal("105.00"));
    Mockito.when(positionService.get(accountId, "AAPL")).thenReturn(pos);

    assertThrows(RiskException.class, () -> riskService.validate(request, quote));
  }
}
