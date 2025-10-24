package com.dev.tradingapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dev.tradingapi.model.Quote;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for MarketService.
 * Tests market data retrieval, caching, and basic functionality.
 */
@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

  private MarketService marketService;
  private final String testApiKey = "test-api-key";

  @BeforeEach
  void setUp() {
    ObjectMapper mapper = new ObjectMapper();
    // Ensure JavaTimeModule is registered so Instant serializes/deserializes
    mapper.findAndRegisterModules();
    marketService = new MarketService(mapper, testApiKey);
  }

  /**
   * Test successful quote retrieval from cache - typical case.
   */
  @Test
  void testGetQuote_FromCache_TypicalCase() {
    String symbol = "AAPL";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("150.00"),
        new BigDecimal("155.00"),
        new BigDecimal("148.00"),
        new BigDecimal("152.00"),
        1000000L,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(new BigDecimal("150.00"), result.getOpen());
    assertEquals(new BigDecimal("155.00"), result.getHigh());
    assertEquals(new BigDecimal("148.00"), result.getLow());
    assertEquals(new BigDecimal("152.00"), result.getClose());
    assertEquals(1000000L, result.getVolume());
    assertEquals(testTime, result.getTs());
  }

  /**
   * Test quote retrieval when no data is available - invalid case.
   */
  @Test
  void testGetQuote_NoDataAvailable_InvalidCase() {

    String symbol = "INVALID";

    Quote result = marketService.getQuote(symbol);

    assertNull(result);
  }

  /**
   * Test quote retrieval with null symbol - invalid case.
   */
  @Test
  void testGetQuote_NullSymbol_InvalidCase() {
    Quote result = marketService.getQuote(null);

    assertNull(result);
  }

  /**
   * Test quote retrieval with empty symbol - invalid case.
   */
  @Test
  void testGetQuote_EmptySymbol_InvalidCase() {
    Quote result = marketService.getQuote("");

    assertNull(result);
  }

  /**
   * Test quote retrieval with multiple quotes in cache - atypical case.
   */
  @Test
  void testGetQuote_MultipleQuotesInCache_AtypicalCase() {
    String symbol = "TSLA";
    Instant baseTime = Instant.now();

    Quote quote1 = new Quote(symbol,
        new BigDecimal("200.00"),
        new BigDecimal("205.00"),
        new BigDecimal("198.00"),
        new BigDecimal("202.00"),
        500000L,
        baseTime.minusSeconds(300));

    Quote quote2 = new Quote(symbol,
        new BigDecimal("202.00"),
        new BigDecimal("208.00"),
        new BigDecimal("200.00"),
        new BigDecimal("206.00"),
        750000L,
        baseTime.minusSeconds(120));

    Quote quote3 = new Quote(symbol,
        new BigDecimal("206.00"),
        new BigDecimal("210.00"),
        new BigDecimal("204.00"),
        new BigDecimal("208.00"),
        600000L,
        baseTime);

    List<Quote> cachedQuotes = Arrays.asList(quote1, quote2, quote3);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertTrue(result.getTs().isBefore(baseTime) || result.getTs().equals(baseTime));
  }

  /**
   * Test quote retrieval with zero volume - edge case.
   */
  @Test
  void testGetQuote_ZeroVolume_EdgeCase() {
    String symbol = "NVDA";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("400.00"),
        new BigDecimal("400.00"),
        new BigDecimal("400.00"),
        new BigDecimal("400.00"),
        0L,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(0L, result.getVolume());
    assertEquals(new BigDecimal("400.00"), result.getOpen());
    assertEquals(new BigDecimal("400.00"), result.getHigh());
    assertEquals(new BigDecimal("400.00"), result.getLow());
    assertEquals(new BigDecimal("400.00"), result.getClose());
  }

  /**
   * Test quote retrieval with very large volume - atypical case.
   */
  @Test
  void testGetQuote_VeryLargeVolume_AtypicalCase() {
    String symbol = "BTC";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("50000.00"),
        new BigDecimal("51000.00"),
        new BigDecimal("49000.00"),
        new BigDecimal("50500.00"),
        Long.MAX_VALUE,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(Long.MAX_VALUE, result.getVolume());
    assertEquals(new BigDecimal("50000.00"), result.getOpen());
    assertEquals(new BigDecimal("51000.00"), result.getHigh());
    assertEquals(new BigDecimal("49000.00"), result.getLow());
    assertEquals(new BigDecimal("50500.00"), result.getClose());
  }

  /**
   * Test quote retrieval with negative prices - invalid case.
   */
  @Test
  void testGetQuote_NegativePrices_InvalidCase() {
    String symbol = "ERROR";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("-100.00"),
        new BigDecimal("-90.00"),
        new BigDecimal("-110.00"),
        new BigDecimal("-95.00"),
        1000L,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(new BigDecimal("-100.00"), result.getOpen());
    assertEquals(new BigDecimal("-90.00"), result.getHigh());
    assertEquals(new BigDecimal("-110.00"), result.getLow());
    assertEquals(new BigDecimal("-95.00"), result.getClose());
  }

  /**
   * Test quote retrieval with very small price values - edge case.
   */
  @Test
  void testGetQuote_VerySmallPrices_EdgeCase() {
    String symbol = "PENNY";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("0.0001"),
        new BigDecimal("0.0002"),
        new BigDecimal("0.00005"),
        new BigDecimal("0.00015"),
        1000000000L,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(new BigDecimal("0.0001"), result.getOpen());
    assertEquals(new BigDecimal("0.0002"), result.getHigh());
    assertEquals(new BigDecimal("0.00005"), result.getLow());
    assertEquals(new BigDecimal("0.00015"), result.getClose());
    assertEquals(1000000000L, result.getVolume());
  }

  /**
   * Test quote retrieval with future timestamp - atypical case.
   */
  @Test
  void testGetQuote_FutureTimestamp_AtypicalCase() {
    String symbol = "FUTURE";
    Instant futureTime = Instant.now().plusSeconds(3600);
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("100.00"),
        new BigDecimal("105.00"),
        new BigDecimal("98.00"),
        new BigDecimal("102.00"),
        500000L,
        futureTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(futureTime, result.getTs());
    assertEquals(new BigDecimal("100.00"), result.getOpen());
    assertEquals(new BigDecimal("105.00"), result.getHigh());
    assertEquals(new BigDecimal("98.00"), result.getLow());
    assertEquals(new BigDecimal("102.00"), result.getClose());
    assertEquals(500000L, result.getVolume());
  }

  /**
   * Test quote retrieval with very old timestamp - edge case.
   */
  @Test
  void testGetQuote_VeryOldTimestamp_EdgeCase() {
    String symbol = "OLD";
    Instant oldTime = Instant.now().minusSeconds(86400 * 365);
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("50.00"),
        new BigDecimal("55.00"),
        new BigDecimal("48.00"),
        new BigDecimal("52.00"),
        250000L,
        oldTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>)
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(oldTime, result.getTs());
    assertEquals(new BigDecimal("50.00"), result.getOpen());
    assertEquals(new BigDecimal("55.00"), result.getHigh());
    assertEquals(new BigDecimal("48.00"), result.getLow());
    assertEquals(new BigDecimal("52.00"), result.getClose());
    assertEquals(250000L, result.getVolume());
  }

  /**
   * Test quote retrieval with special characters in symbol - edge case.
   */
  @Test
  void testGetQuote_SpecialCharactersInSymbol_EdgeCase() {
    String symbol = "TEST-SYMBOL_123";
    Instant testTime = Instant.now();
    Quote expectedQuote = new Quote(symbol,
        new BigDecimal("75.00"),
        new BigDecimal("80.00"),
        new BigDecimal("72.00"),
        new BigDecimal("78.00"),
        300000L,
        testTime);

    List<Quote> cachedQuotes = Arrays.asList(expectedQuote);

    try {
      var cacheField = MarketService.class.getDeclaredField("cache");
      cacheField.setAccessible(true);
      ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>) 
          cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      // Reflection access failed, test will continue without cache setup
    }

    Quote result = marketService.getQuote(symbol);

    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
    assertEquals(new BigDecimal("75.00"), result.getOpen());
    assertEquals(new BigDecimal("80.00"), result.getHigh());
    assertEquals(new BigDecimal("72.00"), result.getLow());
    assertEquals(new BigDecimal("78.00"), result.getClose());
    assertEquals(300000L, result.getVolume());
    assertEquals(testTime, result.getTs());
  }

  // ---------------- Additional branch-coverage tests ----------------

  @Test
  void testPickAtOrBefore_BeforeFirst_ReturnsFirst() throws Exception {
    // Build ascending quotes
    String symbol = "BRANCH";
    Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
    Instant t1 = Instant.parse("2024-01-01T10:01:00Z");
    Quote q0 = new Quote(symbol, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 
        1L, t0);
    Quote q1 = new Quote(symbol, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 
        2L, t1);
    List<Quote> list = Arrays.asList(q0, q1);

    var m = MarketService.class.getDeclaredMethod("pickAtOrBefore", List.class, Instant.class);
    m.setAccessible(true);

    // Target before the first timestamp -> should return first element (last ==
    // null branch)
    Quote picked = (Quote) m.invoke(null, list, Instant.parse("2024-01-01T09:59:59Z"));
    assertEquals(q0, picked);

    // Target exactly matches second -> returns that
    Quote picked2 = (Quote) m.invoke(null, list, t1);
    assertEquals(q1, picked2);
  }

  @Test
  void testIndexAtOrAfter_Boundaries() throws Exception {
    String symbol = "IDX";
    Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
    Instant t1 = Instant.parse("2024-01-01T10:01:00Z");
    Instant t2 = Instant.parse("2024-01-01T10:02:00Z");
    List<Quote> list = Arrays.asList(
        new Quote(symbol, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L, t0),
        new Quote(symbol, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 2L, t1),
        new Quote(symbol, BigDecimal.valueOf(2), BigDecimal.valueOf(2), BigDecimal.valueOf(2),
            BigDecimal.valueOf(2), 3L, t2));

    var m = MarketService.class.getDeclaredMethod("indexAtOrAfter", List.class, Instant.class);
    m.setAccessible(true);

    // Target before first -> 0
    int idx0 = (int) m.invoke(null, list, Instant.parse("2024-01-01T09:59:00Z"));
    assertEquals(0, idx0);

    // Exact match in middle -> index of that element
    int idx1 = (int) m.invoke(null, list, t1);
    assertEquals(1, idx1);

    // After all -> size()
    int idxEnd = (int) m.invoke(null, list, Instant.parse("2024-01-01T11:00:00Z"));
    assertEquals(list.size(), idxEnd);
  }

  @Test
  void testIndexAtOrBefore_Boundaries() throws Exception {
    String symbol = "IDX2";
    Instant t0 = Instant.parse("2024-01-01T10:00:00Z");
    Instant t1 = Instant.parse("2024-01-01T10:01:00Z");
    Instant t2 = Instant.parse("2024-01-01T10:02:00Z");
    List<Quote> list = Arrays.asList(
        new Quote(symbol, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, 1L, t0),
        new Quote(symbol, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 2L, t1),
        new Quote(symbol, BigDecimal.valueOf(2), BigDecimal.valueOf(2), BigDecimal.valueOf(2),
            BigDecimal.valueOf(2), 3L, t2));

    var m = MarketService.class.getDeclaredMethod("indexAtOrBefore", List.class, Instant.class);
    m.setAccessible(true);

    // Target before all -> returns 0 (clamped)
    int idxBeforeAll = (int) m.invoke(null, list, Instant.parse("2024-01-01T09:00:00Z"));
    assertEquals(0, idxBeforeAll);

    // Exact match -> index of that element
    int idxExact = (int) m.invoke(null, list, t2);
    assertEquals(2, idxExact);

    // Between t0 and t1 -> should return index of t0
    int idxBetween = (int) m.invoke(null, list, Instant.parse("2024-01-01T10:00:30Z"));
    assertEquals(0, idxBetween);

    // After all -> last index
    int idxAfterAll = (int) m.invoke(null, list, Instant.parse("2024-01-01T11:00:00Z"));
    assertEquals(2, idxAfterAll);
  }

  @Test
  void testPreviousTradingDay_WeekendAndMonday() throws Exception {
    var m = MarketService.class.getDeclaredMethod("previousTradingDay", LocalDate.class);
    m.setAccessible(true);

    // Monday -> previous Friday
    LocalDate monday = LocalDate.of(2024, 1, 8); // 2024-01-08 is Monday
    LocalDate prevFromMonday = (LocalDate) m.invoke(null, monday);
    assertEquals(LocalDate.of(2024, 1, 5), prevFromMonday);

    // Sunday -> Friday
    LocalDate sunday = LocalDate.of(2024, 1, 7);
    LocalDate prevFromSunday = (LocalDate) m.invoke(null, sunday);
    assertEquals(LocalDate.of(2024, 1, 5), prevFromSunday);

    // Saturday -> Friday
    LocalDate saturday = LocalDate.of(2024, 1, 6);
    LocalDate prevFromSaturday = (LocalDate) m.invoke(null, saturday);
    assertEquals(LocalDate.of(2024, 1, 5), prevFromSaturday);

    // Wednesday -> Tuesday
    LocalDate wed = LocalDate.of(2024, 1, 10);
    LocalDate prevFromWed = (LocalDate) m.invoke(null, wed);
    assertEquals(LocalDate.of(2024, 1, 9), prevFromWed);
  }

  @Test
  void testGetQuote_UsesFileCache_WhenMemoryEmpty() throws Exception {
    // Build a simple quote and persist via MarketService.saveCacheToFile (private)
    String symbol = "IBM";
    Instant ts = Instant.parse("2024-01-03T15:59:00Z");
    Quote q = new Quote(symbol, new BigDecimal("100"), new BigDecimal("101"), new BigDecimal("99"),
        new BigDecimal("100.5"), 1000L, ts);

    var save = MarketService.class.getDeclaredMethod("saveCacheToFile", String.class, List.class);
    save.setAccessible(true);
    save.invoke(marketService, symbol, List.of(q));

    // Ensure in-memory cache is empty for symbol so file path is exercised
    var cacheField = MarketService.class.getDeclaredField("cache");
    cacheField.setAccessible(true);
    @SuppressWarnings("unchecked")
    ConcurrentHashMap<String, List<Quote>> cache = (ConcurrentHashMap<String, List<Quote>>) 
        cacheField.get(marketService);
    cache.remove(symbol);

    Quote result = marketService.getQuote(symbol);
    assertNotNull(result);
    assertEquals(symbol, result.getSymbol());
  }

  @Test
  void testSaveAndReadCache_RoundTrip() throws Exception {
    String symbol = "ROUND";
    Instant ts = Instant.parse("2024-01-03T10:00:00Z");
    Quote q = new Quote(symbol, new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("9"),
        new BigDecimal("10.5"), 42L, ts);

    var save = MarketService.class.getDeclaredMethod("saveCacheToFile", String.class, List.class);
    save.setAccessible(true);
    save.invoke(marketService, symbol, List.of(q));

    var read = MarketService.class.getDeclaredMethod("readCacheFromFile", String.class);
    read.setAccessible(true);
    @SuppressWarnings("unchecked")
    List<Quote> out = (List<Quote>) read.invoke(marketService, symbol);

    assertNotNull(out);
    assertTrue(out.size() >= 1);
    assertEquals(symbol, out.get(0).getSymbol());
  }
}
