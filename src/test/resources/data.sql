-- Test-only seed data for integration tests

INSERT INTO market_bars (id, symbol, ts, open, high, low, close, volume) VALUES
  (RANDOM_UUID(), 'AAPL', TIMESTAMP WITH TIME ZONE '2024-01-03T15:59:00Z', 170.00, 171.00, 169.50, 170.50, 1000000),
  (RANDOM_UUID(), 'IBM',  TIMESTAMP WITH TIME ZONE '2024-01-03T15:59:00Z', 130.00, 131.00, 129.50, 130.50, 500000);
