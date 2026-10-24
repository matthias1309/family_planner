-- ADR-0016: domain events are written in the same transaction as the change that caused them (outbox).
CREATE TABLE core_domain_events (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  type TEXT NOT NULL,
  payload TEXT NOT NULL,
  occurred_at INTEGER NOT NULL
);
-- One cursor per subscriber: the last event it handled successfully. A new subscriber starts at 0 and catches up.
CREATE TABLE core_event_cursors (
  subscriber TEXT PRIMARY KEY,
  last_seq INTEGER NOT NULL
);
