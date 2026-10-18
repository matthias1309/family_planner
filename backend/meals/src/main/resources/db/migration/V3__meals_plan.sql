-- One dish per day: unique index (not a table constraint) so Phase 4 can replace it if needed.
CREATE TABLE meal_dishes (
  id TEXT PRIMARY KEY,
  meal_date TEXT NOT NULL,
  cook_member_id TEXT NOT NULL REFERENCES members(id),
  name TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE UNIQUE INDEX idx_meal_dishes_date ON meal_dishes(meal_date);

-- One row per free-text ingredient line, in input order.
CREATE TABLE meal_dish_ingredients (
  dish_id TEXT NOT NULL REFERENCES meal_dishes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  text TEXT NOT NULL,
  PRIMARY KEY (dish_id, position)
);
