-- Phase 6: shopping list. The merged list is computed from approved dishes; only free entries and ticks are stored.
CREATE TABLE meal_shopping_items (
  id TEXT PRIMARY KEY,
  week_start TEXT NOT NULL,
  name TEXT NOT NULL,
  quantity REAL,
  unit TEXT,
  checked INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);

CREATE INDEX idx_meal_shopping_items_week ON meal_shopping_items(week_start, created_at);

-- Tick of a computed entry; it only counts while the entry still has checked_quantity (NULL = entry without quantity).
CREATE TABLE meal_shopping_checks (
  week_start TEXT NOT NULL,
  entry_key TEXT NOT NULL,
  checked_quantity REAL,
  checked_at INTEGER NOT NULL,
  PRIMARY KEY (week_start, entry_key)
);
