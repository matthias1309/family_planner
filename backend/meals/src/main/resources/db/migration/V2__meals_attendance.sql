-- Day masks: bit (dayOfWeek - 1), Monday = bit 0 ... Sunday = bit 6.
CREATE TABLE meal_attendance_profiles (
  member_id TEXT PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
  cooks INTEGER NOT NULL CHECK (cooks IN (0,1)),
  pattern_type TEXT NOT NULL CHECK (pattern_type IN ('ALWAYS','ALTERNATING')),
  week_a_mask INTEGER NOT NULL DEFAULT 0 CHECK (week_a_mask BETWEEN 0 AND 127),
  week_b_mask INTEGER NOT NULL DEFAULT 0 CHECK (week_b_mask BETWEEN 0 AND 127),
  reference_week TEXT,
  updated_at INTEGER NOT NULL
);

CREATE TABLE meal_attendance_exceptions (
  id TEXT PRIMARY KEY,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  from_date TEXT NOT NULL,
  to_date TEXT NOT NULL,
  presence TEXT NOT NULL CHECK (presence IN ('PRESENT','ABSENT')),
  note TEXT,
  created_at INTEGER NOT NULL,
  CHECK (from_date <= to_date)
);

CREATE INDEX idx_meal_exceptions_member ON meal_attendance_exceptions(member_id, from_date);

CREATE TABLE meal_deadline_settings (
  effective_from TEXT PRIMARY KEY,
  order_deadline_day INTEGER NOT NULL CHECK (order_deadline_day BETWEEN 1 AND 7),
  order_deadline_time TEXT NOT NULL,
  child_deadline_time TEXT NOT NULL,
  updated_at INTEGER NOT NULL
);
