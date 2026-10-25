-- GAME phase 1: immutable points ledger per child. Corrections are counter-entries, never updates.
-- source_key makes credits from events idempotent (task:<cardId>, cooking:<dishId>); NULL for manual entries.
CREATE TABLE game_ledger (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  kind TEXT NOT NULL CHECK (kind IN ('TASK','COOKING','MANUAL','SHOP','REWARD','REWARD_REFUND')),
  amount INTEGER NOT NULL CHECK (amount <> 0),
  title TEXT NOT NULL,
  note TEXT,
  source_key TEXT UNIQUE,
  created_by TEXT REFERENCES members(id),
  booked_at INTEGER NOT NULL
);
CREATE INDEX idx_game_ledger_member ON game_ledger(member_id, booked_at);

-- Single row of family-wide game settings; missing row means defaults.
CREATE TABLE game_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  cooking_points INTEGER NOT NULL CHECK (cooking_points BETWEEN 1 AND 20),
  updated_at INTEGER NOT NULL
);
