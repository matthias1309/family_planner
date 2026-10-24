# Implementation Report: Aufgaben-Board in der App (TASK Phase 3)

## Summary
Neues Android-Feature-Modul `feature:tasks`: Board mit Reitern je Kind (Todo · Zur Abnahme · Erledigt), Aktionen je Rolle, Zuweisen,
Katalog (anlegen/bearbeiten/archivieren), Verlauf, lokaler Stand für Offline-Lesen (DataStore je Mitglied), Aufgaben-Karte auf „Heute“,
Abnahme-Badge an der Leiste und Hinweise per Hintergrund-Abgleich (WorkManager, eigener Kanal „Aufgaben“). Der Platzhalter „Aufgaben“
ist ersetzt. Keine Backend-/API-Änderung, keine neue Bibliothek. Version App 0.9.0 / `versionCode` 10.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch; offen bleibt nur die Geräte-Abnahme |
| Files Changed | ≈ 45 | 44 Hauptdateien + 19 Testdateien im neuen Modul, 10 geänderte/neue in `app`, 4 Dokus |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Modul anlegen | [done] Complete | `consumer-rules.pro` ergänzt (Gradle verlangt die Datei) |
| 2 | Domäne Board und Katalog | [done] Complete | `CachedBoard` als benannte Klasse für `observe()` |
| 3 | Daten (Remote + lokaler Stand) | [done] Complete | `reject` sendet immer einen Body (`{}`) |
| 4 | Use Cases | [done] Complete | `MoveCardUseCase` ohne `Clock` (Stand behält `syncedAt`) |
| 5 | Hinweise (REQ-TASK-030) | [done] Complete | `TaskNotice` in eigener Datei (detekt) |
| 6 | Board-Bildschirm | [done] Complete | Fehler beim Aktionsversuch schließt den Dialog, Meldung steht in der Liste |
| 7 | Zuweisen, Katalog, Verlauf | [done] Complete | Board lädt beim Zurückkehren neu (`LifecycleResumeEffect`) statt über einen Ergebnis-Key |
| 8 | „Heute“, Badge, App-Verdrahtung | [done] Complete | `PlaceholderScreens.kt` → `MeRoute.kt` (detekt) |
| 9 | Doku, Traceability, Checkliste | [done] Complete | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | [done] Pass | `ktlintCheck detekt lintDebug` |
| Unit Tests | [done] Pass | 114 im neuen Modul, 20 in `app` |
| Build | [done] Pass | `assembleDebug assembleRelease` (R8); Test-Build `de.familyplanner.android.test`, 0.9.0-test |
| Coverage | [done] Pass | `koverVerifyDebug` (≥ 80 %) |
| Integration | [done] Pass | MockWebServer, DataStore, Robolectric (Notifier, Screens), Koin-Graph |
| Edge Cases | [done] Pass | siehe Tests: Basislinie, 401, 409, offline, fremder Stand, Reiter klemmen, Doppeltippen (`isBusy`) |

## Deviations from Plan
- Zuweisen/Katalog melden Änderungen nicht über einen `savedStateHandle`-Key, sondern das Board und der Katalog laden beim Zurückkehren
  neu (einfacher, gleiche Wirkung).
- Eine zurückgewiesene Karte ohne Begründung ist vom Backend nicht von einer frischen Zuweisung zu unterscheiden
  (`rejectionReason = null`); der rote Hinweis erscheint daher nur mit Begründung.
- Die Hinweise zählen „zurückgewiesen“ auf „Heute“ ebenfalls nur mit Begründung.

## Issues Encountered
- Mehrere Compose-Testdetails (Lazy-Einträge außerhalb des Bildschirms, zusammengeführter Semantik-Baum bei FAB und Badge) →
  `scrollTo`-Hilfe und `useUnmergedTree` in den Tests.
- `PlaceholderScreens.kt`/`NoticeRules.kt`: detekt `MatchingDeclarationName` → Dateien umbenannt bzw. getrennt.

## Tests Written

| Test File | Tests | Coverage |
|---|---|---|
| `BoardRulesTest`, `CatalogValidationTest`, `NoticeRulesTest`, `UseCasesTest`, `SyncTaskNoticesUseCaseTest` | Domäne | Regeln, Validierung, Cache-Rückfall, Hinweise |
| `BoardViewModelTest`, `FlowViewModelsTest`, `ReviewBadgeTest`, `TodayTasksTest` | ViewModels | Board, Zuweisen, Katalog, Verlauf, Badge, Heute |
| `RemoteTaskRepositoryTest`, `RemoteCatalogRepositoryTest`, `DataStoreBoardCacheTest`, `AndroidTaskNotifierTest` | Daten | API-Pfade/Bodies, Stand, Benachrichtigungen |
| `BoardScreenTest`, `TaskScreensTest`, `TasksModuleTest` | UI/Koin | Screens, Dialoge, Graph |
| `AppNavHostTest` (neu: 6) | App | Board im Bereich, Badge, Heute-Karte, Hinweis-Tippen |

## Next Steps
- [ ] Code-Review (`/ecc:code-review`)
- [ ] PR anlegen, CI grün
- [ ] Geräte-Abnahme laut `android/README.md` („Abnahme-Checkliste TASK Phase 3“); danach PRD Phase 3 auf `complete`
