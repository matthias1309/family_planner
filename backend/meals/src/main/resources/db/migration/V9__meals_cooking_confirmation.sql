-- GAME phase 1: a parent confirms that a child cooked its approved dish (REQ-MEAL-034); this triggers cooking points.
ALTER TABLE meal_dishes ADD COLUMN cooked_at INTEGER;
ALTER TABLE meal_dishes ADD COLUMN cooked_by TEXT REFERENCES members(id);
