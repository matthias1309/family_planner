-- GAME phase 2: badges awarded to a child. Awarded at most once per badge and never revoked.
-- badge_id is the stable id from the catalog in code (Badge.id) - never rename.
CREATE TABLE game_badges (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  badge_id TEXT NOT NULL,
  awarded_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, badge_id)
);
