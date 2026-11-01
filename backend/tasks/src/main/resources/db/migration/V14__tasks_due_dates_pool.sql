-- TASK phase 4: optional due date per card, cards children took from the pool, pool release per catalog task.
ALTER TABLE task_catalog ADD COLUMN child_selectable INTEGER NOT NULL DEFAULT 0 CHECK (child_selectable IN (0,1));
ALTER TABLE task_cards ADD COLUMN due_at INTEGER;
ALTER TABLE task_cards ADD COLUMN self_assigned INTEGER NOT NULL DEFAULT 0 CHECK (self_assigned IN (0,1));
