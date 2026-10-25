# Implementation Report: Punktekonto Backend/API (GAME Phase 1a, inkl. REQ-MEAL-034)

## Summary
Neues Modul `gamification` (Journal `game_ledger`, Kochpunkt-Einstellung, Konto, Rangliste, manuelle Buchung, Abonnent `gamification.points`),
Kochbestätigung in `meals` (REQ-MEAL-034, Ereignis `cooking.confirmed.v1`), additiver OpenAPI-Vertrag 0.7.0.

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt` |
| Unit/Integration/API Tests | Pass | `./gradlew check koverVerify` (Kover ≥ 80 %) |
| Build | Pass | `:app:buildFatJar`; Android `assembleDebug` |
| Manual (curl, Pi-DB-Kopie) | Not run | offen vor Deploy: V9/V10 auf Kopie der Pi-DB, Nachholen alter Abnahmen prüfen |

## Deviations from Plan
- Antwortfeld `entries` heißt im Vertrag **`items`** (`PointsAccount.items`, `Leaderboard.items`): der OpenAPI-Generator benennt `entries` in `propertyEntries` um.
- Tests für REQ-MEAL-034 seeden Kindergerichte im Service-Test direkt über das Repository (Kinder dürfen für laufende/vergangene Wochen nicht über den Service einreichen); im API-Test liegt das „zu frühe“ Gericht in der Folgewoche (Koch darf nur ein Gericht je Woche haben).
- Gemeinsame Test-Helfer (`makeChildCook`, `enterApprovedChildDish`) in `ApiTestSupport.kt` ergänzt; die privaten Kopien in `MealsSubmissionApiTest` blieben unverändert.
- detekt: `book`/`requireConfirmed` und `ExposedMealPlanRepository.cookOutcomeOf` aufgeteilt bzw. auf Dateiebene verschoben.

## Files Changed
Ca. 45 Dateien (siehe `git status`): OpenAPI, `core` (Ereignis), `meals` (Migration V9, Service, Route), neues Modul `gamification` (Migration V10), `app` (Verdrahtung, 2 API-Tests), Docs (`gamification.md`, `meals.md`, `core.md`, Traceability, README, PRD).

## Next Steps
- [ ] `/code-review`, dann PR
- [ ] Vor Deploy: Pi-DB-Kopie prüfen (Migrationen, nachgebuchte Abnahmen)
- [ ] GAME Phase 1b (App) planen
