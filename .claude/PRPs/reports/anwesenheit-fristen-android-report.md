# Implementation Report: Anwesenheit & Fristen – Android-Eltern-UI (Phase 2b)

## Summary
Neues Modul `android/feature/meals` mit Wochenübersicht (alle), Profil-/Ausnahmen-/Fristen-Screens (Eltern), Einstieg
über einen Home-Slot. Backend und `api/` unverändert (`git diff main -- api backend` leer).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files Changed | ~35 neu, ~10 geändert | ~40 neu, 12 geändert |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Branch & Ausgangslage | Done | Branch `feat/anwesenheit-fristen-android`; Arbeitsbaum enthielt nur die Planungs-Artefakte (Plan, PRD-Zeile) und ging mit |
| 1 | Modul-Gerüst + Home-Slot | Done | Formatter nach `core:ui` verschoben |
| 2 | Domäne: Modelle, Planwochen | Done | |
| 3 | Domäne: Validierung | Done | |
| 4 | Daten: Mapper, Repository | Done | Mapper in `Mappers`/`SettingsMappers`/`Parsing` geteilt (detekt `TooManyFunctions`) |
| 5 | Use Cases | Done | zusätzlich `ExceptionUseCases` (Bündel, hält ViewModel-Parameterliste kurz) |
| 6 | Wochenübersicht | Done | |
| 7 | Profile-Liste | Done | |
| 8 | Profil bearbeiten | Done | |
| 9 | Ausnahmen | Done | |
| 10 | Fristeinstellungen | Done | Wochentag-Auswahl als `DropdownMenu` statt `ExposedDropdownMenuBox` (weniger experimentelle API) |
| 11 | Navigation & Koin | Done | `MealsModuleTest` prüft den Graphen |
| 12 | Qualität & Coverage | Done | ktlint, detekt, lint, Tests, Kover (82,7 %), assembleDebug grün |
| 13 | Doku | Done | Moduldesign, Traceability, PRD, android/README |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt lintDebug` |
| Unit Tests | Pass | feature:meals: 101 Tests; auth/app-Tests grün |
| Build | Pass | `assembleDebug` |
| Integration | N/A | MockWebServer-Tests decken die API-Anbindung; kein Lauf gegen das lokale Backend/Gerät |
| Edge Cases | Pass | Zeitumstellung, Sonntag/Montag-Grenze, Parität vor Referenzwoche, 409/403/offline, später Wochenwechsel |

## Deviations from Plan
- Dropdown (siehe Task 10), Mapper-Split, `ExceptionUseCases`-Bündel.
- `consumer-rules.pro` und `src/test/resources/robolectric.properties` (SDK 36) im neuen Modul ergänzt (Plan nannte sie nicht).
- Fake-Repository mit `weekGates` (CompletableDeferred), um den späten Wochenwechsel-Response deterministisch zu testen.

## Issues Encountered
- **Android-String-Ressourcen trimmen führende/abschließende Leerzeichen** (`" (kocht)"` ergab „Ben(kocht)“). Von den
  Screen-Tests gefunden; Texte als Format-Strings (`%1$s (kocht)`) umgesetzt.
- Robolectric mit sehr hohem Fenster (h2400dp) lief in OOM; Tests prüfen untere Listenelemente mit `assertExists`.

## Tests Written
feature:meals: `PlanWeeksTest`, `ValidationTest`, `UseCasesTest`, `RemoteAttendanceRepositoryTest`, `ViewModelsTest`,
`ScreensTest`, `LabelsTest`, `MealsModuleTest`; `AppNavHostTest` um den Home-Einstieg ergänzt.

## Offen
- Geräte-Abnahme (Checkliste in `android/README.md`), danach Traceability-Status auf „abgenommen“ mit Datum.
- Nicht gegen lokales Backend/Emulator geprüft; Date-Range-Picker und TimePicker sind nur kompiliert, nicht in Tests
  bedient (Umrechnung `pickerMillisToDate` ist getestet).

## Next Steps
- [ ] `/ecc:code-review`
- [ ] PR (`/ecc:prp-pr`)
