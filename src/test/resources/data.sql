-- Test-only seed data for integration tests

DELETE FROM market_bars;

INSERT INTO market_bars (id, symbol, ts, open, high, low, close, volume) VALUES
  ('00000000-0000-0000-0000-000000000001', 'AAPL', TIMESTAMP '2024-01-03 15:59:00', 170.00, 171.00, 169.50, 170.50, 1000000),
  ('00000000-0000-0000-0000-000000000002', 'IBM',  TIMESTAMP '2024-01-03 15:59:00', 130.00, 131.00, 129.50, 130.50, 500000);
