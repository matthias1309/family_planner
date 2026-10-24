# Implementation Report: Einkaufsliste in der App (Phase 6b)

## Summary
`feature:meals` zeigt die vom Server berechnete Einkaufsliste einer Planwoche: Wochenwahl, offene Einträge mit Herkunft, Abschnitt
„Erledigt“, Abhaken, freie Einträge hinzufügen/löschen (Eltern), Lesen für Kinder. Einstieg über Startseite und Wochenstatus-Karte.
Nur online, kein Cache. `api/` und `backend/` unverändert. App-Version 0.6.0 (versionCode 7).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium–Large | Medium |
| Files Changed | ≈ 9 neu, ≈ 12 geändert | 15 neu, 12 geändert |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Domäne `shopping` | Done | |
| 2 | Data `shopping` | Done | Antwort-Wrapper heißt `ShoppingListResponse`; Mapper unverändert wie geplant |
| 3 | ViewModel | Done | Schreibvorgänge über gemeinsame `write(...)`-Hilfsfunktion |
| 4 | Screen | Done | Zeilen in `ShoppingRows.kt` ausgelagert (`formatSources` rein testbar) |
| 5 | Navigation, Einstiege, Koin | Done | |
| 6 | Version, Doku, Traceability, Checkliste | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlintCheck, detekt, lintDebug |
| Unit Tests | Pass | `testDebugUnitTest` aller Module |
| Build | Pass | `assembleDebug` |
| Coverage | Pass | `koverVerify` |
| Integration | N/A | Lokales Backend/Emulator nicht ausgeführt; Geräte-Abnahme offen |

## Deviations from Plan
- Testfälle `REQ-MEAL-041 failed add keeps text…`, `failed delete shows error`, `delete without content succeeds` ergänzt.
- Fixture `carbonaraSource` hieß schon in `RecipeFakes`; neu `carbonaraShoppingSource`.

## Issues Encountered
- MockWebServer im Projekt ist `mockwebserver3` (`close()`, `body?.utf8()`); behoben.
- Statuskarten-Link im Robolectric-Test außerhalb des Sichtbereichs → `performScrollTo()`.

## Next Steps
- [ ] `/code-review`, dann PR
- [ ] Geräte-Abnahme 6b (`android/README.md`), danach PRD-Phase 6 auf `complete`
