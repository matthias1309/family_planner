# Implementation Report: Rezeptsammlung – Backend (Phase 5a)

## Summary
Rezeptsammlung im Backend (anlegen/ändern/löschen nur Eltern, lesen/suchen alle) und strukturierte Zutaten (Name, optional Menge/Einheit)
für Rezepte und Gerichte. Gerichte merken sich `recipeId`/`servings`. API additiv erweitert (Vertrag 0.5.0), `ingredients: string[]`
bleibt als Anzeigetext erhalten. Migration V5. Keine App-Änderung außer einer Test-Fixture.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch; Plan reichte ohne Rückfragen |
| Files Changed | ~30 (≈ 14 neu, ≈ 16 geändert) | 37 (siehe git status) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Moduldesign | done | |
| 2 | OpenAPI 0.5.0 | done | Client generiert |
| 3 | Migration V5 | done | Semikolons aus SQL-Kommentaren entfernt (Test-Splitter) |
| 4 | Zutaten-Domäne | done | `isValidQuantity` extrahiert (detekt ComplexCondition) |
| 5 | Dish + Repository | done | |
| 6 | Rezept-Domäne/Repository | done | `validatedSteps` auf eine Fehlerstelle reduziert (detekt ThrowsCount) |
| 7 | Services | done | |
| 8 | Routen/Wiring | done | |
| 9 | API-/Contract-/Migrationstests | done | `ContractValidator` reicht Query-Parameter durch |
| 10 | Android-Fixture | done | `structuredIngredients` in `dishJson` |
| 11 | Doku/Traceability | done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlint + detekt (`./gradlew check`) |
| Unit Tests | Pass | `:meals:test` |
| Build / Coverage | Pass | `./gradlew check koverVerify` |
| Android | Pass | `:core:api:compileDebugKotlin :feature:meals:testDebugUnitTest` |
| Integration | Pass | `:app:test` (MealsRecipeApiTest: 14 Tests, Contract-Validierung) |
| Pi-Start / Docker-Image / manuelle Checks | nicht ausgeführt | siehe Next Steps |

## Deviations from Plan
- `MealPlanServiceTest`: Tests für Rohzeilen-Limit (101 Zeilen) und „30 Zutaten plus Leerzeilen“ nach `IngredientTest` verschoben, da
  `ingredientsFromLines` nun außerhalb des Services läuft (Route). Zusätzlich `RecipeServiceTest` „deleted recipe is gone“ und
  `MealsRecipeApiTest` „too long search query“ ergänzt.
- Kleinere Tests in `CookingWeekServiceTest`, `DishReviewServiceTest`, `PlanTest`, `WeekStatusTest` auf `lines(...)` umgestellt (Typänderung).

## Issues Encountered
- Migrationstest-Splitter trennt an `;` → Kommentare ohne Semikolon.
- detekt: ComplexCondition, ThrowsCount → umstrukturiert.

## Tests Written
`IngredientTest` (8), `RecipeRulesTest` (5), `RecipeServiceTest` (7), `ExposedRecipeRepositoryTest` (4), `MealsMigrationV5Test` (1),
`MealsRecipeApiTest` (14), plus 6 neue Fälle in `MealPlanServiceTest`/`ExposedMealPlanRepositoryTest`.

## Next Steps
- [ ] Code Review (`/ecc:code-review`), danach Commit und PR
- [ ] Vor Merge: `ls backend/*/src/main/resources/db/migration` (keine parallele V5)
- [ ] Manuelle Prüfung: `:app:run` (Log „Successfully applied 5 migrations“), Docker-Image, Pi-Deploy mit App Stand 4b
