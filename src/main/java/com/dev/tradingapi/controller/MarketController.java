package com.dev.tradingapi.controller;

import com.dev.tradingapi.model.Quote;
import com.dev.tradingapi.service.MarketService;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * <p>REST controller that exposes endpoints for accessing market data.</p>
 *
 * <p>This controller provides a simple HTTP interface to the
 * {@link com.dev.tradingapi.service.MarketService MarketService},
 * allowing clients to retrieve simulated historical quote data
 * for a given ticker symbol.</p>
 *
 * <p>Base path: <code>/market</code></p>
 */
@RestController
@RequestMapping("/market")
public class MarketController {

  private final MarketService marketService;

  public MarketController(MarketService marketService) {
    this.marketService = marketService;
  }

  /**
   * GET /market/quote/{symbol}
   * Returns the current simulated quote for the given symbol.
   */
  @GetMapping("/quote/{symbol}")
  public Quote getQuote(@PathVariable String symbol) {
    Quote quote = marketService.getQuote(symbol.toUpperCase(Locale.ROOT));
    if (quote == null) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,
              "Quote not found for symbol: " + symbol);
    }
    return quote;
  }
}
