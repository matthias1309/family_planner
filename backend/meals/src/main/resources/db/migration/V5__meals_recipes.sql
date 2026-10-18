-- Phase 5: recipes with structured ingredients (ADR-0012). Free-text lines stay as name-only ingredients.
CREATE TABLE meal_recipes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  name_key TEXT NOT NULL,
  servings INTEGER NOT NULL CHECK (servings BETWEEN 1 AND 20),
  source_url TEXT,
  created_by TEXT NOT NULL REFERENCES members(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Recipe names are unique ignoring case - name_key is lowercased in Kotlin (Locale.ROOT), so umlauts compare correctly.
CREATE UNIQUE INDEX idx_meal_recipes_name_key ON meal_recipes(name_key);

CREATE TABLE meal_recipe_ingredients (
  recipe_id TEXT NOT NULL REFERENCES meal_recipes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  name TEXT NOT NULL,
  quantity REAL,
  unit TEXT,
  PRIMARY KEY (recipe_id, position)
);

CREATE TABLE meal_recipe_steps (
  recipe_id TEXT NOT NULL REFERENCES meal_recipes(id) ON DELETE CASCADE,
  position INTEGER NOT NULL,
  text TEXT NOT NULL,
  PRIMARY KEY (recipe_id, position)
);

-- Existing dish ingredient lines keep their text as the ingredient name - quantity and unit stay NULL.
ALTER TABLE meal_dish_ingredients ADD COLUMN quantity REAL;
ALTER TABLE meal_dish_ingredients ADD COLUMN unit TEXT;

-- A dish remembers the recipe it was copied from - deleting the recipe keeps the dish.
ALTER TABLE meal_dishes ADD COLUMN recipe_id TEXT REFERENCES meal_recipes(id) ON DELETE SET NULL;
ALTER TABLE meal_dishes ADD COLUMN servings INTEGER CHECK (servings IS NULL OR servings BETWEEN 1 AND 20);
