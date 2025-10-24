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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
      
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
  
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
      ConcurrentHashMap<String, List<Quote>> cache = 
          (ConcurrentHashMap<String, List<Quote>>) cacheField.get(marketService);
      cache.put(symbol, cachedQuotes);
    } catch (Exception e) {
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
}
