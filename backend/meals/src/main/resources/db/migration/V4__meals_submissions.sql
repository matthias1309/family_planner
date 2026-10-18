-- Phase 4: children submit dishes, parents approve or reject. Existing dishes were planned by parents and stay approved.
ALTER TABLE meal_dishes ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'
  CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'));
ALTER TABLE meal_dishes ADD COLUMN rejection_reason TEXT;
ALTER TABLE meal_dishes ADD COLUMN late INTEGER NOT NULL DEFAULT 0 CHECK (late IN (0,1));

-- "One dish per child and plan week" looks up dishes by cook and date range.
CREATE INDEX idx_meal_dishes_cook ON meal_dishes(cook_member_id, meal_date);

CREATE TABLE meal_cooking_cancellations (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  week_start TEXT NOT NULL,
  reason TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (member_id, week_start)
);
