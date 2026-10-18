# Implementation Report: Einreichung & Freigabe – App (Phase 4b)

## Summary
`feature:meals` unterstützt jetzt den Kochtag-Prozess: Status-Badges im Essensplan, Karte „Wochenstatus“ (alle; Eltern mit
Eintragen/Absagen/Zurücknehmen), Karte „Mein Kochtag“ (kochendes Kind), Gericht-Screen mit vier Modi (`PARENT_EDIT`,
`PARENT_REVIEW`, `PARENT_MOVE`, `CHILD`), Freigeben/Ablehnen mit Begründung, Absage/Rücknahme. Bei `dayValid=false` können Eltern nur
verschieben oder löschen. `api/` und `backend/` sind unverändert (`git diff origin/main -- api/ backend/` leer).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch (alle Gradle-Prüfungen grün) |
| Files Changed | ≈ 14 neu, ≈ 20 geändert | 21 neu (davon 8 Testdateien/Fakes), 25 geändert |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Branch & Ausgangslage | Done | `feat/einreichung-freigabe-android` von `docs/einreichung-freigabe-android-plan`; Client 0.4.0 vorhanden |
| 1 | Domäne Plan – Dish-Status, Validierung | Done | |
| 2 | Daten Plan – Mapper & Cache | Done | `CACHE_VERSION` bleibt 1 |
| 3 | Domäne & Daten Cooking | Done | |
| 4 | Use Cases Cooking & Editor-Daten | Done | |
| 5 | Editor-Regeln | Done | |
| 6 | UI – Gericht-Screen mit Modi | Done | Sections in `DishEditSections.kt` |
| 7 | UI – Essensplan, Wochenstatus, Mein Kochtag | Done | |
| 8 | Navigation, Koin, Version | Done | `versionCode = 4`, `versionName = "0.4.0"` |
| 9 | Qualität & Coverage | Done | ktlint, detekt, lint, Tests, Kover, assembleDebug grün |
| 10 | Doku, Traceability, PRD | Done | Phase 4 `complete`; Geräte-Abnahme steht aus |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt lintDebug` |
| Unit Tests | Pass | 375 Tests im Projekt, 0 Fehler; neu u. a. 40 + 36 (ViewModels/Screens), 10 (EditorRules), 11 (RemoteCookingRepository) |
| Build | Pass | `assembleDebug`, `koverVerify` |
| Integration | N/A | MockWebServer-Tests; kein Lauf gegen echtes Backend/Emulator |
| Edge Cases | Pass | siehe Tests (Cache alt/unbekannt, Doppelklick, Wochenwechsel, offline, Kind ohne Kochwoche, …) |

Befehl: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` → exit 0.

## Files Changed
Neu: `domain/cooking/{Models,CookingRepository,UseCases}.kt`, `data/cooking/{Mappers,RemoteCookingRepository}.kt`,
`domain/plan/EditorRules.kt`, `ui/dish/{DishEditArgs,DishEditSections}.kt`, `ui/plan/{StatusCards,StatusActions,ReasonDialog}.kt`,
Tests/Fakes (`CookingFakes`, `RemoteCookingRepositoryTest`, `domain/cooking/UseCasesTest`, `EditorRulesTest`,
`CookingViewModelsTest`, `CookingScreensTest`). Geändert: Plan-Domäne/-Daten, `WeekPlanViewModel/Screen`, `DishEditViewModel/Screen`,
`MealsModule`, `MealsNavigation`, `strings.xml`, bestehende Tests, `app/build.gradle.kts`, README, Moduldesign, Traceability, PRD.

## Deviations from Plan
- `WeekPlanUiState.planWeek` und `DishEditUiState.now` haben Defaults (statt Pflichtparameter), damit bestehende Tests kompilierbar bleiben.
- `WeekStatusCard` wird nur gezeigt, wenn Status oder Statusfehler vorliegt (sonst leere Karte während des Ladens).
- `StatusActions` liegt in eigener Datei (detekt `MatchingDeclarationName`).
- `DishEditViewModel.delete()` prüft zusätzlich `canDelete` (Kind darf nie löschen, auch nicht per Event).
- Fixture `orderDeadline` = 15:00Z (16:00 MEZ nach Zeitumstellung 25.10.).
- Tippt man im Editor auf einen belegten/nicht erlaubten Tag, wird jetzt der erste erlaubte Tag vorausgewählt (`preselectDate`);
  der 3b-Test „date taken by another device“ wurde entsprechend angepasst.

## Issues Encountered
- Lokales `main` war veraltet (Stand PR #10); die Gegenprobe `git diff … -- api/ backend/` wurde deshalb gegen `origin/main` gefahren (leer).
- Leere LazyColumn-Items verschoben Tageszeilen im Robolectric-Fenster → Items nur bei Inhalt hinzugefügt.
- Dialog-/Composable-Aufruf in Lambda (`stringResource`) → Strings vorab aufgelöst.

## Tests Written
Siehe oben; REQ-IDs im Namen (REQ-MEAL-020, -021, -022, -023, -030, -031, -033, -002).

## Offene Punkte
- Geräte-Abnahme Phase 4b (README-Checkliste) und Datum in `traceability.md` stehen aus.
- Server erlaubt weiterhin Freigabe bei `dayValid=false` (App bietet sie nicht an) – siehe Plan „Risks“.
- Nichts committet/gepusht.

## Next Steps
- [ ] Code Review via `/ecc:code-review`
- [ ] Commit + PR via `/ecc:prp-pr`
