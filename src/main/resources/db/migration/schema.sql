CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE IF NOT EXISTS accounts (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  name TEXT NOT NULL,
  auth_token TEXT UNIQUE,
  username TEXT UNIQUE,
  password_hash TEXT,
  max_order_qty INT NOT NULL,
  max_notional NUMERIC(18,2) NOT NULL,
  max_position_qty INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS orders (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  account_id UUID NOT NULL REFERENCES accounts(id),
  client_order_id TEXT,
  symbol TEXT NOT NULL,
  side TEXT NOT NULL CHECK (side IN ('BUY','SELL')),
  qty INT NOT NULL CHECK (qty > 0),
  type TEXT NOT NULL CHECK (type IN ('MARKET','LIMIT','TWAP')),
  limit_price NUMERIC(18,4),
  time_in_force TEXT,
  status TEXT NOT NULL CHECK (status IN ('NEW','WORKING','PARTIALLY_FILLED','FILLED','CANCELLED','REJECTED')),
  filled_qty INT NOT NULL DEFAULT 0,
  avg_fill_price NUMERIC(18,4),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_orders_account ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);

CREATE TABLE IF NOT EXISTS fills (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  order_id UUID NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
  qty INT NOT NULL CHECK (qty > 0),
  price NUMERIC(18,4) NOT NULL,
  ts TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_fills_order ON fills(order_id);

CREATE TABLE IF NOT EXISTS positions (
  account_id UUID NOT NULL REFERENCES accounts(id),
  symbol TEXT NOT NULL,
  qty INT NOT NULL DEFAULT 0,
  avg_cost NUMERIC(18,4) NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, symbol)
);
CREATE INDEX IF NOT EXISTS idx_positions_account ON positions(account_id);
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);

-- Historical intraday market data (one row per bar)
CREATE TABLE IF NOT EXISTS market_bars (
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  symbol TEXT NOT NULL,
  ts TIMESTAMPTZ NOT NULL,
  open NUMERIC(18,4) NOT NULL,
  high NUMERIC(18,4) NOT NULL,
  low NUMERIC(18,4) NOT NULL,
  close NUMERIC(18,4) NOT NULL,
  volume BIGINT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_bars_symbol_ts
  ON market_bars(symbol, ts);
