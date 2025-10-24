package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.model.Quote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for PnlService.
 * Tests profit and loss calculations for accounts with essential scenarios.
 */
@ExtendWith(MockitoExtension.class)
class PnlServiceTest {

  @Mock
  private MarketService marketService;

  @Mock
  private AccountService accountService;

  @Mock
  private PositionService positionService;

  private PnlService pnlService;

  private UUID accountId;
  private Account testAccount;

  @BeforeEach
  void setUp() {
    pnlService = new PnlService(marketService, accountService, positionService);
    accountId = UUID.randomUUID();
    testAccount = new Account(
        accountId,
        "Test Account",
        "test-api-key",
        1000,
        new BigDecimal("100000"),
        5000,
        Instant.now(),
        new BigDecimal("10000.00")
    );
  }

  /**
   * Test PnL calculation with no positions - should return zero.
   */
  @Test
  void testGetForAccount_NoPositions_ShouldReturnZero() {
 
    when(accountService.getById(accountId)).thenReturn(testAccount);
    when(positionService.getByAccountId(accountId)).thenReturn(Collections.emptyList());

    BigDecimal result = pnlService.getForAccount(accountId);

    assertEquals(BigDecimal.ZERO.setScale(10), result);
  }

  /**
   * Test PnL calculation with profitable position.
   */
  @Test
  void testGetForAccount_ProfitablePosition_ShouldReturnPositivePnL() {
    BigDecimal initialBalance = new BigDecimal("10000.00");
    BigDecimal cashBalance = new BigDecimal("9500.00"); // Reduced by trade cost
    testAccount.adjustCashBalance(cashBalance.subtract(initialBalance));
    
    Position position = new Position(accountId, "AAPL", 100, new BigDecimal("150.00"));
    Quote quote = new Quote("AAPL", 
        new BigDecimal("150.00"), 
        new BigDecimal("160.00"), 
        new BigDecimal("145.00"), 
        new BigDecimal("155.00"), 
        1000L, 
        Instant.now());

    when(accountService.getById(accountId)).thenReturn(testAccount);
    when(positionService.getByAccountId(accountId)).thenReturn(Arrays.asList(position));
    when(marketService.getQuote("AAPL")).thenReturn(quote);

    BigDecimal result = pnlService.getForAccount(accountId);

    // Expected PnL = (cashBalance + positionValue) - initialBalance
    // = (9500 + 155*100) - 10000 = (9500 + 15500) - 10000 = 15000
    BigDecimal expectedPnL = new BigDecimal("15000.00").setScale(10);
    assertEquals(expectedPnL, result);
  }

  /**
   * Test PnL calculation with losing position.
   */
  @Test
  void testGetForAccount_LosingPosition_ShouldReturnNegativePnL() {
    BigDecimal initialBalance = new BigDecimal("10000.00");
    BigDecimal cashBalance = new BigDecimal("10500.00"); // Increased by trade proceeds
    testAccount.adjustCashBalance(cashBalance.subtract(initialBalance));
    
    Position position = new Position(accountId, "TSLA", 50, new BigDecimal("200.00"));
    Quote quote = new Quote("TSLA", 
        new BigDecimal("200.00"), 
        new BigDecimal("210.00"), 
        new BigDecimal("190.00"), 
        new BigDecimal("180.00"), 
        2000L, 
        Instant.now());

    when(accountService.getById(accountId)).thenReturn(testAccount);
    when(positionService.getByAccountId(accountId)).thenReturn(Arrays.asList(position));
    when(marketService.getQuote("TSLA")).thenReturn(quote);

    BigDecimal result = pnlService.getForAccount(accountId);

    // Expected PnL = (cashBalance + positionValue) - initialBalance
    // = (10500 + 180*50) - 10000 = (10500 + 9000) - 10000 = 9500
    BigDecimal expectedPnL = new BigDecimal("9500.00").setScale(10);
    assertEquals(expectedPnL, result);
  }

  /**
   * Test constructor with null services - should throw exception.
   */
  @Test
  void testConstructor_NullServices_ShouldThrowException() {
    assertThrows(NullPointerException.class, () -> 
        new PnlService(null, accountService, positionService));
    
    assertThrows(NullPointerException.class, () -> 
        new PnlService(marketService, null, positionService));
    
    assertThrows(NullPointerException.class, () -> 
        new PnlService(marketService, accountService, null));
  }
}