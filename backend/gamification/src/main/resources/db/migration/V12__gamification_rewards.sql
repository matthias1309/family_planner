-- GAME phase 3: real rewards. Deactivated rewards keep their row (archived_at) so redemptions and goals stay readable.
CREATE TABLE game_rewards (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  emoji TEXT,
  price INTEGER NOT NULL CHECK (price BETWEEN 1 AND 10000),
  archived_at INTEGER,
  created_by TEXT REFERENCES members(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Redemption snapshot (title/emoji/price at the time of redeeming). The debit is a REWARD ledger entry with
-- source_key reward:<id>; a rejection books REWARD_REFUND with source_key reward-refund:<id> in the same transaction.
CREATE TABLE game_redemptions (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  reward_id TEXT NOT NULL REFERENCES game_rewards(id),
  title TEXT NOT NULL,
  emoji TEXT,
  price INTEGER NOT NULL CHECK (price > 0),
  status TEXT NOT NULL CHECK (status IN ('PENDING','FULFILLED','REJECTED')),
  rejection_reason TEXT,
  requested_at INTEGER NOT NULL,
  decided_by TEXT REFERENCES members(id),
  decided_at INTEGER
);
CREATE INDEX idx_game_redemptions_status ON game_redemptions(status, requested_at);
CREATE INDEX idx_game_redemptions_member ON game_redemptions(member_id, requested_at);

-- At most one savings goal per child.
CREATE TABLE game_savings_goals (
  member_id TEXT PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
  reward_id TEXT NOT NULL REFERENCES game_rewards(id),
  set_at INTEGER NOT NULL
);
