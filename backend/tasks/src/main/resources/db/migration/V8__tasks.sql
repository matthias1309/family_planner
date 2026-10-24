-- TASK phase 2: prepared tasks ("magnets") that parents assign to children.
CREATE TABLE task_catalog (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  emoji TEXT,
  points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 100),
  archived_at INTEGER,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- A card copies title, emoji and points from the catalog when assigned; later catalog edits do not change it.
CREATE TABLE task_cards (
  id TEXT PRIMARY KEY,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  catalog_task_id TEXT REFERENCES task_catalog(id) ON DELETE SET NULL,
  title TEXT NOT NULL,
  emoji TEXT,
  points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 100),
  status TEXT NOT NULL CHECK (status IN ('TODO','REVIEW','DONE')),
  rejection_reason TEXT,
  assigned_at INTEGER NOT NULL,
  reported_at INTEGER,
  done_at INTEGER,
  done_by TEXT REFERENCES members(id)
);
CREATE INDEX idx_task_cards_member_status ON task_cards(member_id, status, done_at);
