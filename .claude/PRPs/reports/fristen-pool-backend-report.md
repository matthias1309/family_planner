# Implementation Report: Fristen & Pool – Backend/API (TASK Phase 4)

## Summary
Karten haben eine optionale Frist (`dueAt`, setzen beim Zuweisen, ändern/entfernen per `PUT …/due-date`) und liefern abgeleitet
`reportedLate` und `selfAssigned`. Katalogaufgaben tragen `childSelectable`; Kinder lesen den Pool (`GET /pool`), nehmen daraus eine
Karte (TODO oder sofort REVIEW) und legen selbst genommene TODO-Karten per `DELETE /cards/{id}` zurück. OpenAPI rein additiv.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch; Plan passte bis auf Detailfragen |
| Files Changed | ~20 | 35 (inkl. Doku, Tests) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | OpenAPI erweitern | Done | zusätzlich `description` bei `POST /cards`; `summary` von DELETE mit `: ` musste quoted werden |
| 2 | Migration V14 + Tabellen | Done | |
| 3 | Katalog-Domäne mit Freigabe | Done | |
| 4 | Frist-Regeln (`DueDates.kt`) | Done | |
| 5 | Karten-Domäne und Repository | Done | gemeinsame `outcome(...)`-Hilfe in `ExposedTaskCardRepository` |
| 6 | BoardService | Done | Hilfen `requireRemoved`/`requireOwnSelfAssigned` nach `BoardRules.kt` (detekt `TooManyFunctions`); doppelte private `validatedReason` entfernt |
| 7 | PoolService | Done | `requireTakeable` extrahiert (detekt `ThrowsCount`) |
| 8 | Routen und Mapper | Done | `DueDateParsing.kt`, `poolRoutes` |
| 9 | API-Tests | Done | |
| 10 | Doku | Done | Design, Traceability, PRD |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt` (via `./gradlew build`) |
| Unit Tests | Pass | `:tasks:test` |
| Build | Pass | `cd backend && ./gradlew build` – 616 Tests, 0 Fehler |
| Integration | Pass | `:app:test --tests 'de.familyplanner.app.Tasks*'` (Vertragsprüfung gegen `openapi.yaml`) |
| Android | Pass | `:feature:tasks:testDebugUnitTest`, `compileDebugUnitTestKotlin` |
| Manual (Testinstanz Port 8097) | Offen | nach Deploy: alte App bearbeitet freigegebene Aufgabe, Freigabe bleibt (`GET /pool`) |

## Files Changed
Neu: `V14__tasks_due_dates_pool.sql`, `DueDates.kt`, `PoolService.kt`, `DueDateParsing.kt`, `DueDatesTest.kt`, `PoolServiceTest.kt`,
`TasksPoolApiTest.kt`, dieser Bericht. Geändert: `api/openapi.yaml`, Katalog-/Board-Domäne, Exposed-Repositories, `TasksTables.kt`,
`TaskRoutes.kt`, `TaskApiMappers.kt`, `TasksModule.kt`, `Application.kt`, Fakes/Fixture, `BoardServiceTest`, `CatalogServiceTest`,
beide Exposed-Tests, `TasksBoardApiTest`, `TasksCatalogApiTest`, `docs/03-design/tasks.md`, `traceability.md`, `tasks.prd.md`.

## Deviations from Plan
- Detekt-Refactorings (siehe Tasks 6/7); keine Verhaltensänderung.
- Nach dem Review nachgezogen: `PUT …/due-date` liest den Body als `JsonObject` und verlangt den Schlüssel `dueAt` (`{}` → 400),
  weil das projektweite JSON ein fehlendes Nullable-Feld sonst still als `null` (Frist entfernen) lesen würde.
- TDD-Reihenfolge: Produktionscode und Tests je Schicht gemeinsam geschrieben, kein separater RED-Lauf.

## Issues Encountered
- YAML: `: ` in einem unquotierten `summary` brach den OpenAPI-Generator – quoted.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `DueDatesTest` | 7 | Fristgrenzen, Verspätung |
| `PoolServiceTest` | 7 | Pool nehmen, Fehlerfälle |
| `BoardServiceTest` (+11) | 11 | Frist setzen/ändern, Zurücklegen |
| `CatalogServiceTest` (+5) | 5 | Freigabe, Pool-Sortierung |
| `Exposed*RepositoryTest` (+6) | 6 | Spalten, bedingte Updates |
| `TasksBoardApiTest`/`TasksCatalogApiTest`/`TasksPoolApiTest` | 10 | Endpunkte mit Vertragsprüfung |

## Next Steps
- [ ] `/code-review`, dann `/prp-commit` und `/prp-pr`
- [ ] Nach Deploy auf der Testinstanz: manuelle Prüfung (Freigabe bleibt bei Bearbeiten in der alten App)
- [ ] Phase 5 (App) planen: `/ecc:prp-plan docs/01-requirements/tasks.prd.md`
