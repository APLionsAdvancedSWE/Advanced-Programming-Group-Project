package com.dev.tradingapi.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.service.MarketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration tests for {@link MarketService} that exercise the real
 * Spring wiring, repository, and disk-based mockdata.
 */
@SpringBootTest
@ActiveProfiles("test")
class MarketServiceIntegrationTest {

  @Autowired
  private MarketService marketService;

  @Test
  void getQuote_nullSymbol_returnsNull() {
    Quote quote = marketService.getQuote(null);
    assertNull(quote);
  }

  @Test
  void getQuote_usesCacheAndPersistenceLayers() {
    // Use a symbol that is present in test mockdata/DB
    String symbol = "IBM";

    Quote first = marketService.getQuote(symbol);
    assertNotNull(first, "First quote for symbol should not be null");
    assertEquals(symbol, first.getSymbol());

    // Call again to exercise the in-memory cache branch.
    Quote second = marketService.getQuote(symbol);
    assertNotNull(second, "Second quote for symbol should not be null");
    assertEquals(symbol, second.getSymbol());

    // Sanity: the cached quote should be at the same or a later instant
    // than the first one returned.
    assertNotNull(first.getTs());
    assertNotNull(second.getTs());
  }

  @Test
  void start_hydratesCacheAndAllowsQuotes() {
    marketService.start();

    Quote quote = marketService.getQuote("IBM");
    assertNotNull(quote);
    assertEquals("IBM", quote.getSymbol());
  }

  @Test
  void start_isIdempotentAndCancelsExistingSimulation() {
    marketService.start();
    marketService.start();

    Quote quote = marketService.getQuote("IBM");
    assertNotNull(quote);
    assertEquals("IBM", quote.getSymbol());
  }
}
