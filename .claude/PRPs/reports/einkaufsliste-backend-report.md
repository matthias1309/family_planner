# Implementation Report: Einkaufsliste – Backend (Phase 6a)

## Summary
`GET/POST/DELETE/PUT …/weeks/{weekStart}/shopping-list…`: berechnete, zusammengeführte Einkaufsliste aus freigegebenen
Gerichten, freie Einträge (max. 50/Woche) und Häkchen (mengenabhängig). Schreiben nur Eltern.

## Tasks Completed
All 9 tasks (Doku, OpenAPI, Migration V6, Domäne, Repository, Service, Routen, API-Tests, Traceability) — TDD.

## Validation Results
| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlint, detekt via `./gradlew check` |
| Unit/Integration Tests | Pass | 13 + 4 + 10 + 8 neue Tests |
| Coverage | Pass | `koverVerify` |
| Android-Client | Pass | `:core:api:compileDebugKotlin`, `:feature:meals:testDebugUnitTest` |
| Docker-Image / manuelle Validierung | Not run | offen |

## Files Changed
OpenAPI, `docs/03-design/meals.md`, traceability, PRD, V6-Migration, 3 Domänen-/Service-Dateien, 2 Persistenzdateien,
2 Web-Dateien, `MealsModule`, `Application`, 5 Testdateien (inkl. Fake, `PlanFixture`).

## Deviations from Plan
- Der Generator benennt die Eigenschaft `entries` von `ShoppingList` in `propertyEntries` um (Kotlin-Reservierung). JSON bleibt `entries`;
  der Mapper nutzt `propertyEntries` — in 6b beachten.
- `ShoppingListService.setChecked` wirft `NotFound`, wenn `setItemChecked` fehlschlägt (im Plan bevorzugt); Hilfsfunktionen wegen detekt `ThrowsCount`.
- `saveCheck` per `deleteWhere` + `insert` (statt `upsert`).

## Issues Encountered
detekt `ThrowsCount` in `setChecked` → in Hilfsfunktionen aufgeteilt.

## Next Steps
- [ ] `/code-review`, dann PR
- [ ] Docker-Build und manuelle Validierung
- [ ] Phase 6b (App)
