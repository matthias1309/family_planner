-- GAME phase 4: avatar shop. Items are a fixed catalog in code (AvatarCatalog); item ids are never changed.
-- A purchase books a SHOP ledger entry (source_key shop:<member>:<item>) in the same transaction.
CREATE TABLE game_avatar_items (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  item_id TEXT NOT NULL,
  price INTEGER NOT NULL CHECK (price > 0),
  purchased_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, item_id)
);

-- The avatar a child wears: one row per slot; no rows = no avatar yet.
CREATE TABLE game_avatar_slots (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  slot TEXT NOT NULL,
  item_id TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, slot)
);
