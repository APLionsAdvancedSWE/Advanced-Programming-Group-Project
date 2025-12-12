-- H2-compatible schema for local demo

CREATE TABLE IF NOT EXISTS accounts (
  id UUID PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  auth_token VARCHAR(255),
  username VARCHAR(255) UNIQUE,
  password_hash VARCHAR(255),
  max_order_qty INT NOT NULL,
  max_notional NUMERIC(18,2) NOT NULL,
  max_position_qty INT NOT NULL,
  initial_balance NUMERIC(18,4) NOT NULL DEFAULT 0,
  cash_balance NUMERIC(18,4) NOT NULL DEFAULT 0,
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS orders (
  id UUID PRIMARY KEY,
  account_id UUID NOT NULL,
  client_order_id VARCHAR(255),
  symbol VARCHAR(50) NOT NULL,
  side VARCHAR(10) NOT NULL,
  qty INT NOT NULL,
  type VARCHAR(20) NOT NULL,
  limit_price NUMERIC(18,4),
  time_in_force VARCHAR(20),
  status VARCHAR(30) NOT NULL,
  filled_qty INT NOT NULL DEFAULT 0,
  avg_fill_price NUMERIC(18,4),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_orders_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_orders_account ON orders(account_id);
CREATE INDEX IF NOT EXISTS idx_orders_symbol ON orders(symbol);

CREATE TABLE IF NOT EXISTS fills (
  id UUID PRIMARY KEY,
  order_id UUID NOT NULL,
  qty INT NOT NULL,
  price NUMERIC(18,4) NOT NULL,
  ts TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_fills_order FOREIGN KEY (order_id) REFERENCES orders(id) ON DELETE CASCADE
);
CREATE INDEX IF NOT EXISTS idx_fills_order ON fills(order_id);

CREATE TABLE IF NOT EXISTS positions (
  account_id UUID NOT NULL,
  symbol VARCHAR(50) NOT NULL,
  qty INT NOT NULL DEFAULT 0,
  avg_cost NUMERIC(18,4) NOT NULL DEFAULT 0,
  PRIMARY KEY (account_id, symbol),
  CONSTRAINT fk_positions_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);
CREATE INDEX IF NOT EXISTS idx_positions_account ON positions(account_id);
CREATE INDEX IF NOT EXISTS idx_positions_symbol ON positions(symbol);

-- Historical intraday market data (one row per bar)
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
