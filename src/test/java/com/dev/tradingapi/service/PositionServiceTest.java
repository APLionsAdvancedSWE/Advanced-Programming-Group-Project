package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

import com.dev.tradingapi.model.Position;
import com.dev.tradingapi.repository.PositionRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PositionServiceTest {

  @Mock
  private PositionRepository positionRepository;

  private PositionService positionService;

  @BeforeEach
  void setUp() {
    // For these unit tests we exercise the in-memory branch by
    // ensuring the repository returns an empty list for any account.
    positionService = new PositionService(positionRepository);
    when(positionRepository.findByAccountId(org.mockito.ArgumentMatchers.any(UUID.class)))
        .thenReturn(java.util.Collections.emptyList());
  }

  @Test
  void applyFill_zeroQuantity_doesNothing() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 0, BigDecimal.TEN);

    assertEquals(0, positionService.getByAccountId(accountId).size());
    assertNull(positionService.get(accountId, "IBM"));
  }

  @Test
  void applyFill_createsNewPositionWhenNoneExists() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 10, BigDecimal.TEN);

    Position pos = positionService.get(accountId, "IBM");
    assertEquals(10, pos.getQty());
    assertEquals(BigDecimal.TEN, pos.getAvgCost());
  }

  @Test
  void applyFill_closesPositionWhenQuantityGoesToZero() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 10, BigDecimal.TEN);
    positionService.applyFill(accountId, "IBM", -10, BigDecimal.TEN);

    assertNull(positionService.get(accountId, "IBM"));
    assertEquals(0, positionService.getByAccountId(accountId).size());
  }

  @Test
  void applyFill_addsOnSameSideAndRecomputesAverage() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 10, BigDecimal.TEN);          // cost 10
    positionService.applyFill(accountId, "IBM", 10, BigDecimal.valueOf(20));  // cost 20

    Position pos = positionService.get(accountId, "IBM");
    assertEquals(20, pos.getQty());
    // (10*10 + 10*20) / 20 = 15
    assertEquals(BigDecimal.valueOf(15), pos.getAvgCost().setScale(0));
  }

  @Test
  void applyFill_partialCloseKeepsAverageWhenSameSideRemains() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 10, BigDecimal.TEN);
    positionService.applyFill(accountId, "IBM", -5, BigDecimal.valueOf(20));

    Position pos = positionService.get(accountId, "IBM");
    assertEquals(5, pos.getQty());
    assertEquals(BigDecimal.TEN, pos.getAvgCost());
  }

  @Test
  void applyFill_flipSideSetsAverageToFillPrice() {
    UUID accountId = UUID.randomUUID();

    positionService.applyFill(accountId, "IBM", 5, BigDecimal.TEN);
    positionService.applyFill(accountId, "IBM", -10, BigDecimal.valueOf(20));

    Position pos = positionService.get(accountId, "IBM");
    assertEquals(-5, pos.getQty());
    assertEquals(BigDecimal.valueOf(20), pos.getAvgCost());
  }

  @Test
  void getByAccountId_returnsCopyOfPositions() {
    UUID accountId = UUID.randomUUID();
    positionService.applyFill(accountId, "IBM", 5, BigDecimal.TEN);

    List<Position> positions = positionService.getByAccountId(accountId);
    assertEquals(1, positions.size());
  }
}
