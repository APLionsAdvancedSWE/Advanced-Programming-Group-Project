package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Position;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * <b>PositionService</b> — tracks open positions per account (by symbol)
 * and updates them incrementally as fills come in.
 *
 * <p>Each Position represents a live snapshot:
 * one entry per (accountId, symbol).</p>
 *
 * <p>When a new fill arrives, the position is updated using weighted
 * average cost (WAC) logic.</p>
 */
@Service
public class PositionService {

  /** In-memory map of positions by account -> symbol. */
  private final Map<UUID, Map<String, Position>> positionsByAccount = new ConcurrentHashMap<>();

  private static final int SCALE = 10;

  /**
   * Returns all open positions for a given account.
   *
   * @param accountId the account ID
   * @return list of open positions (empty if none)
   */
  public List<Position> getByAccountId(UUID accountId) {
    return new ArrayList<>(positionsByAccount
            .getOrDefault(accountId, Collections.emptyMap())
            .values());
  }

  /**
   * Returns a specific position for an account and symbol.
   *
   * @param accountId the account ID
   * @param symbol the symbol/ticker
   * @return Position or null if none exists
   */
  public Position get(UUID accountId, String symbol) {
    return positionsByAccount
            .getOrDefault(accountId, Collections.emptyMap())
            .get(symbol);
  }

  /**
   * Applies a fill to the account’s position for a symbol.
   * Creates, updates, or removes a position as needed.
   *
   * @param accountId account ID
   * @param symbol ticker symbol
   * @param fillQty signed quantity (positive = buy, negative = sell)
   * @param fillPrice execution price
   */
  public synchronized void applyFill(UUID accountId,
                                     String symbol, int fillQty, BigDecimal fillPrice) {
    if (fillQty == 0) {
      return;
    }

    positionsByAccount.putIfAbsent(accountId, new ConcurrentHashMap<>());
    Map<String, Position> accountPositions = positionsByAccount.get(accountId);
    Position pos = accountPositions.get(symbol);

    // Case 1: No existing position -> create new one
    if (pos == null) {
      Position newPos = new Position(accountId, symbol, fillQty, fillPrice);
      accountPositions.put(symbol, newPos);
      return;
    }

    int oldQty = pos.getQty();
    int newQty = oldQty + fillQty;

    // Case 2: Closing position completely
    if (newQty == 0) {
      accountPositions.remove(symbol);
      return;
    }

    // Case 3: Adding/reducing same side
    if (Integer.signum(oldQty) == Integer.signum(fillQty)) {
      BigDecimal oldNotional = pos.getAvgCost().multiply(BigDecimal.valueOf(Math.abs(oldQty)));
      BigDecimal newNotional = fillPrice.multiply(BigDecimal.valueOf(Math.abs(fillQty)));
      BigDecimal newAvg = oldNotional.add(newNotional)
              .divide(BigDecimal.valueOf(Math.abs(newQty)), SCALE, RoundingMode.HALF_UP);

      pos.setQty(newQty);
      pos.setAvgCost(newAvg);
      return;
    }

    // Case 4: Reducing or flipping sides
    if (Math.abs(fillQty) < Math.abs(oldQty)) {
      // partial close — keep avg cost
      pos.setQty(newQty);
    } else {
      // flip sides — new avg cost = fill price
      pos.setQty(newQty);
      pos.setAvgCost(fillPrice);
    }
  }
}
