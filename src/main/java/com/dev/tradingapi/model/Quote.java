package com.dev.tradingapi.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * This class defines the Quote model.
 */
public class Quote {

  private String symbol;
  private BigDecimal open;
  private BigDecimal high;
  private BigDecimal low;
  private BigDecimal close;
  private long volume;
  private Instant ts;

  /**
   * Constructs a new Quote object using OHLCV data.
   *
   * @param symbol trading symbol (e.g., "IBM")
   * @param open opening price
   * @param high high price
   * @param low low price
   * @param close closing price
   * @param volume trading volume
   * @param ts timestamp when data was refreshed
   */
  public Quote(String symbol, BigDecimal open, BigDecimal high, BigDecimal low,
               BigDecimal close, long volume, Instant ts) {
    this.symbol = symbol;
    this.open = open;
    this.high = high;
    this.low = low;
    this.close = close;
    this.volume = volume;
    this.ts = ts;
  }

  /**
   * Returns the trading symbol or ticker for this quote.
   * Example: "AAPL", "TSLA", or "IBM".
   *
   * @return the symbol/ticker for this quote
   */
  public String getSymbol() {
    return symbol;
  }

  /**
   * Returns the opening price for this symbol during the time period.
   *
   * @return the open price
   */
  public BigDecimal getOpen() {
    return open;
  }

  /**
   * Returns the highest traded price for this symbol during the time period.
   *
   * @return the high price
   */
  public BigDecimal getHigh() {
    return high;
  }

  /**
   * Returns the lowest traded price for this symbol during the time period.
   *
   * @return the low price
   */
  public BigDecimal getLow() {
    return low;
  }

  /**
   * Returns the final traded price (closing price) for this symbol
   * during the time period.
   *
   * @return the close price
   */
  public BigDecimal getClose() {
    return close;
  }

  /**
   * Returns the total trading volume for this symbol during the time period.
   *
   * @return the number of shares/contracts traded
   */
  public long getVolume() {
    return volume;
  }

  /**
   * Returns the timestamp representing when this quote snapshot
   * was last refreshed or retrieved from the data source.
   *
   * @return the quote timestamp
   */
  public Instant getTs() {
    return ts;
  }

  /**
   * Calculates an "average" price for convenience.
   * Uses (high + low) / 2 as an approximation.
   *
   * @return average of high and low prices
   */
  public BigDecimal getMidPrice() {
    return high.add(low).divide(BigDecimal.valueOf(2), BigDecimal.ROUND_HALF_UP);
  }

  @Override
  public String toString() {
    return String.format("Quote[%s | open=%s, high=%s, low=%s, close=%s, vol=%d]",
            symbol, open, high, low, close, volume);
  }
}
