package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.dev.tradingapi.model.Account;
import com.dev.tradingapi.repository.AccountRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AccountServiceTest {

  private final AccountRepository accountRepository = Mockito.mock(AccountRepository.class);
  private final AccountService accountService = new AccountService(accountRepository);

  @Test
  void saveAndGetById_storeAccountInMemory() {
    UUID id = UUID.randomUUID();
    Account account = new Account(id, "Test", "token", 1,
        BigDecimal.TEN, 5, java.time.Instant.now(), BigDecimal.ZERO, BigDecimal.ZERO);

    accountService.save(account);

    Account found = accountService.getById(id);
    assertNotNull(found);
    assertEquals(id, found.getId());
  }

  @Test
  void adjustCash_throwsIfAccountMissing() {
    UUID id = UUID.randomUUID();

    assertThrows(IllegalArgumentException.class,
        () -> accountService.adjustCash(id, BigDecimal.TEN));
  }

  @Test
  void adjustCash_updatesExistingAccountBalance() {
    UUID id = UUID.randomUUID();
    Account account = new Account(id, "Test", "token", 1,
        BigDecimal.TEN, 5, java.time.Instant.now(), BigDecimal.ZERO, BigDecimal.ZERO);

    accountService.save(account);
    accountService.adjustCash(id, BigDecimal.TEN);

    assertEquals(BigDecimal.TEN, accountService.getById(id).getCashBalance());
  }

  @Test
  void updateRiskLimits_usesInMemoryAccountWhenPresent() {
    UUID id = UUID.randomUUID();
    Account account = new Account(id, "Test", "token", 1,
        BigDecimal.ONE, 1, java.time.Instant.now(), BigDecimal.ZERO, BigDecimal.ZERO);

    accountService.save(account);

    Account updated = accountService.updateRiskLimits(id, 5, BigDecimal.TEN, 7);

    assertEquals(5, updated.getMaxOrderQty());
    assertEquals(BigDecimal.TEN, updated.getMaxNotional());
    assertEquals(7, updated.getMaxPositionQty());

    Mockito.verify(accountRepository).updateRiskLimits(id, 5, BigDecimal.TEN, 7);
  }

  @Test
  void updateRiskLimits_loadsFromRepositoryWhenNotInMemory() {
    UUID id = UUID.randomUUID();
    Account fromDb = new Account(id, "Db", "token", 2,
        BigDecimal.valueOf(100), 3, java.time.Instant.now(), BigDecimal.ZERO, BigDecimal.ZERO);

    Mockito.when(accountRepository.findById(id)).thenReturn(Optional.of(fromDb));

    Account updated = accountService.updateRiskLimits(id, null, null, null);

    assertEquals(2, updated.getMaxOrderQty());
    assertEquals(BigDecimal.valueOf(100), updated.getMaxNotional());
    assertEquals(3, updated.getMaxPositionQty());

    Mockito.verify(accountRepository)
      .updateRiskLimits(id, 2, BigDecimal.valueOf(100), 3);
  }
}
