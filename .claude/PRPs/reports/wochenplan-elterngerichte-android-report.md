# Implementation Report: Wochenplan & Elterngerichte – App inkl. Offline-Cache (Phase 3b)

## Summary
`feature:meals` zeigt den Essensplan (Woche Mo–So, Gericht + Koch/Köchin, „noch offen“, „Heute“), Eltern legen Gerichte in
einem eigenen Editor an, ändern/verschieben und löschen sie. Der zuletzt synchronisierte Stand (diese und nächste Woche
plus jede angesehene Woche) liegt in einem DataStore-JSON-Cache (ADR-0011) und wird offline mit „Offline – Stand: …“ gezeigt.
`api/` und `backend/` sind unverändert.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch; Geräte-Abnahme steht aus |
| Files Changed | ≈ 25 neu, ≈ 12 geändert | 24 neu, 18 geändert/verschoben (inkl. Doku) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Branch & Ausgangslage | Complete | Branch `feat/wochenplan-elterngerichte-android`; Planungs-Änderungen (CLAUDE.md, PRD, Plan) aus dem schmutzigen `main` mitgenommen |
| 1 | ADR-0011 | Complete | |
| 2 | Parsing teilen, `currentWeek`, Modelle | Complete | `Parsing.kt` → `data/Parsing.kt` |
| 3 | Validierung | Complete | |
| 4 | Remote-Repository + Mapper | Complete | |
| 5 | Cache (DataStore) | Complete | |
| 6 | Use Cases | Complete | |
| 7 | Essensplan UI | Complete | |
| 8 | Gericht-Editor | Complete | |
| 9 | Navigation, Home, Koin, Version 0.3.0 | Complete | |
| 10 | Qualität & Coverage | Complete | ktlint, detekt, lint, Kover |
| 11 | Doku, Traceability, PRD | Complete | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt lintDebug` |
| Unit Tests | Pass | 87 neue Tests im Modul `feature:meals`, 1 neuer in `app` |
| Build | Pass | `assembleDebug` |
| Coverage | Pass | `koverVerify` ≥ 80 % |
| Integration | Pass | MockWebServer (`RemoteMealPlanRepositoryTest`), echter DataStore (`DataStorePlanCacheTest`) |
| Edge Cases | Pass | Sonntag/Montag-Grenze, kaputte Cache-Datei, Version, Besitzerwechsel, Doppelklick, 409/403/404 |
| Gegen lokales Backend / Gerät | Offen | Abnahme Phase 3b (android/README.md) |

## Deviations from Plan
- Zusätzliche Tests über die Liste hinaus (Taken-Day-Auswahl, Koch kein Elternteil mehr, Löschfehler, Namenskürzung, ohne Session).
- `RemoteMealPlanRepository` nennt den `MembersApi`-Parameter `memberApi`, weil die Methode `members()` heißt.
- detekt `ForEachOnRange`: Tag-Chips iterieren über `WeekDays` statt über eine Range.

## Issues Encountered
- JUnit verlangt `void`-Testmethoden: `runBlocking`-Tests, die mit `assertNotNull` enden, schlugen mit `InvalidTestClassError` fehl → Reihenfolge der Assertions getauscht.
- Robolectric-Fenster ist klein: untere Listenelemente mit `assertExists`/`performScrollTo` statt `assertIsDisplayed` (bekannt aus 2b).

## Tests Written

| Test File | Tests | Area |
|---|---|---|
| `domain/plan/ValidationTest` | 9 | Grenzen, Zeilenbereinigung |
| `domain/plan/UseCasesTest` | 15 | Namen, Cache-Fallback, Editor, Speichern |
| `domain/attendance/PlanWeeksTest` | +1 | `currentWeek` |
| `data/RemoteMealPlanRepositoryTest` | 12 | Pfade, Mapping, Fehler |
| `data/DataStorePlanCacheTest` | 9 | Persistenz, Besitzer, Aufräumen, kaputte Datei |
| `ui/PlanViewModelsTest` | 26 | Essensplan + Editor |
| `ui/PlanScreensTest` | 15 | Anzeige, Rollen, Offline |
| `MealsModuleTest` | erweitert | neue ViewModels, Cache-Override |
| `AppNavHostTest` | +1 | Home zeigt „Essensplan“ |

## Next Steps
- [ ] Code Review (`/ecc:code-review`)
- [ ] PR (`/ecc:prp-pr`)
- [ ] Geräte-Abnahme Phase 3b; danach Datum in `traceability.md` und README-Checkliste abhaken
