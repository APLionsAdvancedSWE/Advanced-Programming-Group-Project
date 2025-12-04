package com.dev.tradingapi.service;

import com.dev.tradingapi.model.Quote;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * <p><b>Market data orchestration service</b>.</p>
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Fetches historical intraday bars from Alpha Vantage (month-scoped)
 *   and normalizes timestamps.</li>
 *   <li>Caches bars in memory and on disk (JSON) to avoid repeated network calls.</li>
 *   <li>Serves a "current" quote by selecting the bar at or
 *   just before a computed target minute (same wall-clock minute one year ago).</li>
 *   <li>Runs a background simulation that replays bars at a fixed interval.</li>
 * </ul>
 *
 * <p><b>Time and session semantics:</b></p>
 * <ul>
 *   <li>Alpha Vantage intraday keys are in New York local time.
 *   They are parsed as ZonedDateTime in America/New_York then stored as UTC Instants.</li>
 *   <li>The "target minute" is computed as: now in NY → minus 1 year → truncated to minute.</li>
 *   <li>If inside regular trading hours (09:30–16:00 NY), use that minute;
 *   if after close, use the same day's close;
 *   if before open, use the previous trading day's close.</li>
 *   <li>Quote selection is "at or before" the target Instant to handle missing minutes.</li>
 * </ul>
 *
 * <p><b>Caching:</b></p>
 * <ul>
 *   <li>Disk cache path: <code>src/main/resources/mockdata</code></li>
 *   <li>File naming: <code>{SYMBOL}-yyyy-MM.json</code> (e.g., IBM-2024-10.json).</li>
 *   <li>Load order: memory → file → fetch.
 *   Successful fetches are persisted to file and memory.</li>
 * </ul>
 *
 * <p><b>Fetching:</b></p>
 * <ul>
 *   <li>Endpoint: Alpha Vantage TIME_SERIES_INTRADAY with month scoping.</li>
 *   <li>Recommended params: <code>interval=1min</code>,
 *   optionally <code>extended_hours=false</code> to exclude pre/post market.</li>
 *   <li>Basic handling for "no data" payloads.</li>
 * </ul>
 *
 * <p><b>Simulation:</b></p>
 * <ul>
 *   <li>Uses a single-threaded ScheduledExecutorService
 *   to emit the next bar per symbol at fixed intervals (e.g., 1s per bar).</li>
 *   <li>Pointers are positioned to the bar at or
 *   after the target minute so playback starts at the corresponding time of day.</li>
 *   <li>When all symbols are exhausted, the task can self-cancel (see implementation).</li>
 * </ul>
 *
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>API key is injected from <code>application.properties</code> (or environment variables)
 *   using Spring’s <code>@Value</code> annotation.</li>
 * </ul>
 *
 * <p><b>Limitations:</b></p>
 * <ul>
 *   <li>Holiday calendar is not explicitly modeled; weekend rollbacks are handled,
 *   but exchange holidays or early closes may require scanning back to a day with data.</li>
 *   <li>Disk writes are JSON.</li>
 * </ul>
 */
@Service
public class MarketService {

  private final String apiKey;
  private static final String BASE_URL = "https://www.alphavantage.co/query";
  private static final Path MOCKDATA_DIR = Path.of("src/main/resources/mockdata");

  private final Map<String, List<Quote>> cache = new ConcurrentHashMap<>();
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
  private final ObjectMapper mapper;
  private final MarketDataRepository marketDataRepository;
  private final HttpClient client;
  /**
   * <p>Constructs the MarketService.</p>
   *
   * <p>Uses Spring's preconfigured ObjectMapper (with JavaTimeModule)
   * for JSON serialization and deserialization of time types.</p>
   *
   * @param mapper the ObjectMapper injected by Spring
   */

  @Autowired
  public MarketService(ObjectMapper mapper,
                       MarketDataRepository marketDataRepository,
                       @Value("${alphavantage.api.key}") String apiKey) {
    this(mapper, marketDataRepository, apiKey, HttpClient.newHttpClient());
  }

  // Package-visible for testing
  MarketService(ObjectMapper mapper,
                MarketDataRepository marketDataRepository,
                String apiKey,
                HttpClient client) {
    this.mapper = mapper;  // Spring’s mapper already has JavaTimeModule etc.
    this.marketDataRepository = marketDataRepository;
    this.apiKey = apiKey;
    this.client = client;
  }

  private static final DateTimeFormatter AV_FORMATTER =
          DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private static final ZoneId NY_ZONE = ZoneId.of("America/New_York");

  // -------- NYSE session helpers (regular hours only) --------
  private static final LocalTime OPEN = LocalTime.of(9, 30);
  private static final LocalTime CLOSE = LocalTime.of(16, 0);

  // Simulation state
  private final Map<String, Integer> simPos = new ConcurrentHashMap<>();
  private final Map<String, LocalDate> lastPrintedNyDate = new ConcurrentHashMap<>();
  private ScheduledFuture<?> simTask;

  /*
   * <p>Determines whether the specified time falls within regular
   * New York Stock Exchange trading hours (09:30–16:00 local time).</p>
   *
   * @param t the local time to evaluate
   * @return {@code true} if the time is within regular trading hours, otherwise {@code false}
   */
  /*
  Unused for now
    private static boolean isRegularHours(LocalTime t) {
      return !t.isBefore(OPEN) && !t.isAfter(CLOSE);
    }
  */

  /**
   * <p>Determines the previous trading day by skipping weekends.</p>
   * <p>Holidays and half-days are not handled.</p>
   *
   * <p>Saturday rolls back to Friday, Sunday also rolls back to Friday.</p>
   *
   * @param dateInNy date in the America/New_York time zone
   * @return the previous trading day adjusted for weekends
   */
  private static LocalDate previousTradingDay(LocalDate dateInNy) {
    LocalDate d = dateInNy.minusDays(1);
    // Skip weekends. (Holidays not handled.)
    DayOfWeek dow = d.getDayOfWeek();
    if (dow == DayOfWeek.SUNDAY) {
      d = d.minusDays(2); // Sun -> Fri
    } else if (dow == DayOfWeek.SATURDAY) {
      d = d.minusDays(1); // Sat -> Fri
    }
    return d;
  }

  /*
    <p>Returns the New York open timestamp (09:30) for the given date.</p>

    @param d local date in New York
   * @return ZonedDateTime at 09:30 for the given date
   */

  /*
   Unused for now
    private static ZonedDateTime nyOpen(LocalDate d) {
      return d.atTime(OPEN).atZone(NY_ZONE);
    }
  */

  /**
   * <p>Returns the New York close timestamp (16:00) for the given date.</p>
   *
   * @param d local date in New York
   * @return ZonedDateTime at 16:00 for the given date
   */
  private static ZonedDateTime nyClose(LocalDate d) {
    return d.atTime(CLOSE).atZone(NY_ZONE);
  }


  /**
   * <p>Computes the target simulation minute as an Instant in UTC.</p>
   *
   * <p><b>Logic:</b></p>
   * <ol>
   *   <li>Take the current New York time, truncate to minute, subtract one year.</li>
   *   <li>If inside regular hours (09:30–16:00), use that minute.</li>
   *   <li>If after close, use that day's close.</li>
   *   <li>If before open, use the previous trading day's close.</li>
   * </ol>
   *
   * <p>Handles rollovers like New Year's Day (01/01 → 12/31) and weekends.</p>
   *
   * @return the computed target Instant in UTC
   */
  private static Instant targetMinuteInstantUtc() {
    ZonedDateTime nowNy = ZonedDateTime.now(NY_ZONE).withSecond(0).withNano(0);
    ZonedDateTime oneYearAgoNy = nowNy.minusYears(1);

    LocalDate d = oneYearAgoNy.toLocalDate();
    LocalTime t = oneYearAgoNy.toLocalTime();

    // Inside regular hours → use that exact minute
    if (!t.isBefore(OPEN) && !t.isAfter(CLOSE)) {
      return oneYearAgoNy.toInstant();
    }

    // After close → use SAME day close
    if (t.isAfter(CLOSE)) {
      return nyClose(d).toInstant();
    }

    // Before open → use PREVIOUS trading day's close (handles weekend rollover like 01/01 -> 12/31)
    LocalDate prevTd = previousTradingDay(d);
    return nyClose(prevTd).toInstant();
  }


  /**
   * <p>From a list of quotes sorted ascending by timestamp,
   * returns the last quote whose timestamp is less than or equal to the target.</p>
   * <p>If none exist, returns the first quote.</p>
   *
   * @param sorted list of quotes sorted ascending
   * @param target target timestamp (UTC)
   * @return the quote at or before the target time
   */
  private static Quote pickAtOrBefore(List<Quote> sorted, Instant target) {
    Quote last = null;
    for (Quote q : sorted) {
      if  (!q.getTs().isAfter(target)) {
        last = q;
      } else {
        break;
      }
    }
    return (last != null) ? last : sorted.get(0);
  }

  // ---------------------- Public API ----------------------

  /**
   * <p>Retrieves the most recent available quote for a symbol.</p>
   *
  * <p><b>Resolution order:</b></p>
  * <ol>
  *   <li>In-memory cache</li>
  *   <li>Database cache in <code>market_bars</code></li>
  *   <li>Legacy JSON mock files (for bootstrapping only)</li>
  *   <li>Fetch from Alpha Vantage and persist to DB</li>
  * </ol>
   *
   * <p><b>Selection logic:</b></p>
   * <ul>
   *   <li>Computes a "target minute" (same wall-clock minute one year ago in New York).</li>
   *   <li>Returns the quote at or before that target minute.</li>
   * </ul>
   *
   * <p>Timestamps from Alpha Vantage are parsed as New York local time
   * and stored as UTC Instants to avoid daylight saving issues.</p>
   *
   * @param symbol ticker symbol such as AAPL or TSLA
   * @return the closest available Quote, or null if no data is available
   */
  public Quote getQuote(String symbol) {
    if (symbol == null) {
      return null;
    }
    
    Instant target = targetMinuteInstantUtc();

    // 1) in-memory
    List<Quote> quotes = cache.get(symbol);
    if (quotes != null && !quotes.isEmpty()) {
      return pickAtOrBefore(quotes, target);
    }

    // 2) database cache
    List<Quote> fromDb = marketDataRepository.findBySymbolOrderByTsAsc(symbol);
    if (fromDb != null && !fromDb.isEmpty()) {
      cache.put(symbol, fromDb);
      return pickAtOrBefore(fromDb, target);
    }

    // 3) file cache (legacy mockdata for offline/demo)
    List<Quote> fromFile = readCacheFromFile(symbol);
    if (fromFile != null && !fromFile.isEmpty()) {
      cache.put(symbol, fromFile);
      // also persist to DB so subsequent runs can rely on database
      marketDataRepository.saveQuotes(symbol, fromFile);
      return pickAtOrBefore(fromFile, target);
    }

    // 4) Alpha Vantage
    List<Quote> fetched = fetchIntraday(symbol);
    if (fetched != null && !fetched.isEmpty()) {
      cache.put(symbol, fetched);
      // persist to DB only; JSON files are legacy/dev-only
      marketDataRepository.saveQuotes(symbol, fetched);
      if (simTask != null && !simTask.isCancelled()) {
        int startIdx = indexAtOrBefore(fetched, target);
        simPos.put(symbol, startIdx);
      }
      return pickAtOrBefore(fetched, target);
    }

    return null;
  }

  /**
   * <p>Fetches historical intraday Quote data for a symbol for the month one year ago.</p>
   *
   * <p>Data is retrieved from Alpha Vantage using the TIME_SERIES_INTRADAY function.</p>
   * <p>Each timestamp is parsed in America/New_York and converted to UTC for storage.</p>
   * <p>The result list is sorted ascending by timestamp.</p>
   *
   * <p>This method can be adjusted to use 1-minute intervals or exclude extended hours.</p>
   *
   * @param symbol ticker symbol
   * @return list of quotes sorted by timestamp ascending, or null if no data found
   */
  List<Quote> fetchIntraday(String symbol) {
    try {
      String month = YearMonth.now().minusYears(1)
              .format(DateTimeFormatter.ofPattern("yyyy-MM")); // e.g., 2024-10
      String url = String.format(
              "%s?function=TIME_SERIES_INTRADAY"
                      + "&symbol=%s"
                      + "&interval=1min"
                      + "&month=%s"
                      + "&outputsize=full"
                      + "&apikey=%s"
                      + "&extended_hours=false",
              BASE_URL, symbol, month, apiKey
      );

      HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
      HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());

      JsonNode root = mapper.readTree(res.body());
      JsonNode series = root.path("Time Series (1min)");
      if (series.isMissingNode()) {
        System.err.println("No intraday data found for " + symbol);
        return new ArrayList<>();
      }

      List<Quote> quotes = new ArrayList<>();
      for (String ts : (Iterable<String>) () -> series.fieldNames()) {
        JsonNode q = series.get(ts);

        // Parse AV key as NY local time, then convert to UTC Instant.
        Instant instant = LocalDateTime.parse(ts, AV_FORMATTER)
                .atZone(NY_ZONE)
                .toInstant();

        quotes.add(new Quote(
                symbol,
                new BigDecimal(q.get("1. open").asText()),
                new BigDecimal(q.get("2. high").asText()),
                new BigDecimal(q.get("3. low").asText()),
                new BigDecimal(q.get("4. close").asText()),
                q.get("5. volume").asLong(),
                instant
        ));
      }

      quotes.sort(Comparator.comparing(Quote::getTs)); // ascending by UTC Instant
      return quotes;
    } catch (IOException | InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Failed to fetch quotes from upstream service", e);
    }
  }

  /**
   * <p>Saves a list of quotes to disk as
   * a JSON file under <code>src/main/resources/mockdata</code>.</p>
   * <p>The filename format is
   * <code>{SYMBOL}-yyyy-MM.json</code> (for example, IBM-2024-10.json).</p>
   *
   * <p>Intended for local development only.</p>
   *
   * @param symbol ticker symbol
   * @param quotes list of quotes to save
   */
  void saveCacheToFile(String symbol, List<Quote> quotes) {
    try {
      if (!Files.exists(MOCKDATA_DIR)) {
        Files.createDirectories(MOCKDATA_DIR);
      }

      String date = YearMonth.now().minusYears(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
      Path filePath = MOCKDATA_DIR.resolve(symbol + "-" + date + ".json");

      mapper.writeValue(filePath.toFile(), quotes);
      System.out.println("Saved cache for " + symbol + " to " + filePath);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * <p>Loads cached quotes for the given symbol from the mockdata directory.</p>
   * <p>Reads the JSON file named <code>{SYMBOL}-yyyy-MM.json</code> if it exists.</p>
   *
   * <p>Returns a mutable list of quotes,
   * or null if the file does not exist or cannot be parsed.</p>
   *
   * @param symbol ticker symbol
   * @return list of quotes, or null if unavailable
   */
  private List<Quote> readCacheFromFile(String symbol) {
    try {
      String date = YearMonth.now().minusYears(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));
      Path filePath = MOCKDATA_DIR.resolve(symbol + "-" + date + ".json");

      if (!Files.exists(filePath)) {
        return new ArrayList<>();
      }

      List<Quote> quotes = Arrays.asList(mapper.readValue(filePath.toFile(), Quote[].class));
      System.out.println("Loaded cache for " + symbol + " from " + filePath);
      return new ArrayList<>(quotes);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read market data cache", e);
    }
  }

  /**
   * <p>Returns the index of the first quote whose timestamp
   * is greater than or equal to the target.</p>
   * <p>If all timestamps are before the target, returns <code>list.size()</code>.</p>
   *
   * <p>Used to determine the simulation starting point for each symbol.</p>
   *
   * @param list sorted list of quotes
   * @param target target timestamp
   * @return index of first quote at or after target
   */
  private static int indexAtOrAfter(List<Quote> list, Instant target) {
    int lo = 0;
    int hi = list.size() - 1;
    int ans = list.size();
    while (lo <= hi) {
      int mid = (lo + hi) >>> 1;
      Instant ts = list.get(mid).getTs();
      if (ts.compareTo(target) >= 0) {
        ans = mid;
        hi = mid - 1;
      } else {
        lo = mid + 1;
      }
    }
    return ans;
  }

  /**
   * <p>Returns the index of the last quote whose timestamp
   * is less than or equal to the target.
   * If all quotes are after the target, returns 0.</p>
   *
   * <p>Used to align simulation pointers to
   * the previous valid bar
   * (e.g., last trading minute before close).</p>
   *
   * @param list sorted list of quotes
   * @param target target timestamp
   * @return index of the quote at or before target
   */
  private static int indexAtOrBefore(List<Quote> list, Instant target) {
    int i = indexAtOrAfter(list, target);
    if (i == list.size()) {
      return list.size() - 1; // all quotes are before target
    }
    Instant ts = list.get(i).getTs();
    return ts.equals(target) ? i : Math.max(0, i - 1);
  }

  /**
   * <p>Loads cached data into memory, sets up simulation pointers,
   * and starts the background simulation.</p>
   *
   * <p><b>Behavior:</b></p>
   * <ul>
   *   <li>Loads all <code>*.json</code> files from the mockdata directory.</li>
   *   <li>Skips empty or corrupted cache files.</li>
   *   <li>Positions each symbol's pointer to the first bar
   *   at or after the target minute (one year ago).</li>
   *   <li>Runs a fixed-rate task that prints one simulated update per minute.</li>
   *   <li>Stops automatically when all symbols are exhausted.</li>
   * </ul>
   *
   * <p><b>Threading:</b></p>
   * <ul>
   *   <li>Uses a single-threaded ScheduledExecutorService for updates.</li>
   * </ul>
   */
  public void start() {
    System.out.println("Starting MarketService...");

    // 1) Refresh all known symbols into memory and DB.
    //    Known symbols come from two places:
    //    - Symbols that already have rows in the database
    //    - Symbols that have JSON cache files on disk
    //    For each symbol we prefer DB data; JSON is a fallback that
    //    also hydrates the DB.
    cache.clear();

    // Discover symbols from DB by selecting distinct symbols from market_bars.
    // We use JdbcTemplate indirectly via the repository: load all quotes for
    // each distinct symbol and cache them.
    try {
      List<String> dbSymbols = marketDataRepository.findDistinctSymbols();
      for (String symbol : dbSymbols) {
        List<Quote> fromDb = marketDataRepository.findBySymbolOrderByTsAsc(symbol);
        if (fromDb != null && !fromDb.isEmpty()) {
          cache.put(symbol, new ArrayList<>(fromDb));
        }
      }
    } catch (Exception e) {
      // If DB discovery fails for any reason, fall back to file-only discovery
      System.err.println("Failed to load symbols from database: " + e.getMessage());
    }

    // Also discover symbols from JSON files for any that are not yet in the
    // cache. When we see a file-only symbol, we load it via getQuote so that
    // DB and in-memory cache are hydrated consistently.
    if (Files.exists(MOCKDATA_DIR)) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(MOCKDATA_DIR, "*.json")) {
        for (Path path : stream) {
          String fileName = path.getFileName().toString();
          String symbol = fileName.split("-")[0];

          if (cache.containsKey(symbol)) {
            continue; // already loaded from DB
          }

          Quote q = getQuote(symbol);
          if (q == null) {
            System.err.println("No market data available to start simulation for symbol: "
                    + symbol);
          }
        }
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    System.out.println("Loaded " + cache.size() + " symbols from cache.");

    // 2) Position simulation pointers at the corresponding wall-clock minute (1y ago, NY)
    simPos.clear();
    Instant target = targetMinuteInstantUtc();
    for (Map.Entry<String, List<Quote>> e : cache.entrySet()) {
      List<Quote> quotes = e.getValue();
      int startIdx = indexAtOrBefore(quotes, target);
      simPos.put(e.getKey(), startIdx);
    }

    // 3) Run the simulation: one bar per second; stop when all symbols are exhausted
    if (simTask != null && !simTask.isCancelled()) {
      simTask.cancel(false);
    }
    simTask = scheduler.scheduleAtFixedRate(() -> {
      boolean anyLeft = false;

      for (Map.Entry<String, List<Quote>> entry : cache.entrySet()) {
        String symbol = entry.getKey();
        List<Quote> quotes = entry.getValue();
        int i = simPos.getOrDefault(symbol, 0);

        if (i < quotes.size()) {
          Quote q = quotes.get(i);
          simPos.put(symbol, i + 1);

          // 🕓 detect if day rolled over
          LocalDate nyDate = q.getTs().atZone(NY_ZONE).toLocalDate();
          LocalDate prev = lastPrintedNyDate.put(symbol, nyDate);
          if (prev != null && !prev.equals(nyDate)) {
            System.out.println("---- " + symbol
                    + " rolling to next trading day: " + nyDate + " ----");
          }

          System.out.println("Sim update: " + symbol + " -> " + q);
          anyLeft = true;
        }
      }

      if (!anyLeft) {
        System.out.println("Simulation finished; no more quotes for all symbols.");
        simTask.cancel(false);
      }
    }, 0, 1, TimeUnit.MINUTES);
  }

  /**
   * <p>Spring lifecycle hook that runs once after bean construction.</p>
   * <p>Invokes <code>start()</code> automatically when the application boots.</p>
   */
  @PostConstruct
  public void init() {
    System.out.println("Starting MarketService...");
    start();
  }
}
