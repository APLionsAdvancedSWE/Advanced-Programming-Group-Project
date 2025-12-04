-- Minimal schema for test database to support integration tests

CREATE TABLE IF NOT EXISTS market_bars (
  id UUID PRIMARY KEY,
  symbol VARCHAR(50) NOT NULL,
  ts TIMESTAMP WITH TIME ZONE NOT NULL,
  open NUMERIC(18,4) NOT NULL,
  high NUMERIC(18,4) NOT NULL,
  low NUMERIC(18,4) NOT NULL,
  close NUMERIC(18,4) NOT NULL,
  volume BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_market_bars_symbol_ts
  ON market_bars(symbol, ts);
