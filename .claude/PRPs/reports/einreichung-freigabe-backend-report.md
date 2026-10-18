# Implementation Report: Einreichung & Freigabe – Backend (Phase 4a)

## Summary
Backend/API für den Kochtag-Prozess: Kinder reichen Gerichte ein/ändern/sagen ab, Eltern geben frei/lehnen ab/tragen
stellvertretend ein, Wochenstatus für alle. OpenAPI 0.4.0, Migration V4, Services (`MealPlanService` erweitert,
`DishReviewService`, `CookingWeekService`), Routen, Tests, Doku. App-Seite folgt in 4b.

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Moduldesign | done | `docs/03-design/meals.md` |
| 2 | OpenAPI 0.4.0 | done | 5 Operationen, 8 neue Schemas, `Dish` erweitert |
| 3 | Migration V4 | done | |
| 4 | Domäne/reine Regeln | done | `Plan.kt`, `ChildCooking.kt`, `WeekStatus.kt` |
| 5 | Repository | done | Exposed + Fake |
| 6 | `MealPlanService` | done | Deviation: kein `DishInput` in der öffentlichen API (6 Parameter), nur privat |
| 7 | Review-/Cooking-Service | done | |
| 8 | Routen/Mapper/Koin | done | |
| 9 | Integrationstests | done | `MealsSubmissionApiTest` (25 Tests), `MealsPlanApiTest` angepasst |
| 10 | Android-Fixture | done | `dishJson` um Pflichtfelder ergänzt |
| 11 | Doku/Traceability | done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| `cd backend && ./gradlew check koverVerify` | pass | ktlint, detekt, Tests (app 81, meals 124, core 34), Coverage |
| `cd android && ./gradlew :core:api:compileDebugKotlin :feature:meals:testDebugUnitTest` | pass | |
| Image-Build / `:app:run` / manuelle curl-Checks | not run | Migration V1–V4 läuft in Repository- und API-Tests gegen echte SQLite |

## Deviations from Plan
- `DishInput`/`Draft` sind private Data-Classes im Service; öffentliche Signaturen bleiben positional (Actor + 4–5 Parameter).
- detekt `ThrowsCount`: `requireSaved` baut die Exception per `when` und wirft einmal; `CookingWeekService` teilt die Rechteprüfung in `requireMayActFor`.
- Der alte Test „cook must be a parent“ wurde zu `REQ-MEAL-031 child that does not cook cannot get a dish`.

## Issues Encountered
Nur die beiden detekt-Befunde oben.

## Next Steps
- [ ] `/code-review`, dann PR
- [ ] Manuelle Validierung und Pi-Deploy (Migration V4)
- [ ] Phase 4b (App) planen
