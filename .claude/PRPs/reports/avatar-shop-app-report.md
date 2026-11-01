# Implementation Report: Avatar & Shop – App (GAME Phase 4b)

## Summary
Kombinierter Bildschirm „Avatar & Shop“ (Vorschau, 14 Rubriken, Kauf mit Bestätigung und Sparziel-Hinweis, Speichern), Avatar-Karte auf „Ich“ und
„Heute“ (Kind), Avatare in Rangliste (Ich + Familie) und Board-Reitern, Namensnennung. Keine API-Änderung (0.10.0). App 0.13.0 (versionCode 14).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files Changed | ≈ 16 neu, ≈ 16 geändert | ≈ 30 neu (inkl. 9 Testdateien), ≈ 22 geändert |

## Tasks Completed
Tasks 1–10 erledigt (Renderer asynchron, Domäne, Daten/Cache/Directory, Editor-VM, Editor-UI + Navigation, Avatare überall, Koin/Strings/Version, Tests, Doku).

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck`, `detekt`, `lintDebug` |
| Unit Tests | Pass | `testDebugUnitTest` aller Module |
| Coverage | Pass | `koverVerifyDebug` |
| Build | Pass | `assembleDebug` |
| Manuell (Testhandy) | offen | Checkliste im Plan |

## Deviations from Plan
- `defaultLook(catalog)` liefert `AvatarLook?` (statt `error(...)`); `AvatarEditorData` trägt zusätzlich `startLook` (gespeicherter oder Standard-Look).
- Auf Server-Konflikt (409) wird der Fehlertext über das Neuladen hinweg behalten (`refresh(keepError)`), sonst wäre er sofort gelöscht worden.
- 401 setzt zusätzlich die im Speicher gehaltenen Avatare zurück (nicht nur den Cache).
- Bestehende Me-Tests (`AppNavHostTest`, `RewardScreensTest`) scrollen jetzt zu Knoten, weil die neue Karte die Liste verlängert.

## Issues Encountered
Ein versehentlich blockierender Shell-Befehl und ein fehlgeschlagenes `sed -i` (macOS) – behoben, keine Auswirkung auf das Ergebnis.

## Tests Written
`AvatarRulesTest`, `AvatarUseCasesTest`, `AvatarDirectoryTest`, `RemoteAvatarRepositoryTest`, `DataStoreAvatarCacheTest`, `AvatarViewModelsTest`,
`AvatarScreensTest`; angepasst: `AvatarImageSpikeTest`, `ViewModelsTest`, `RewardViewModelsTest`, `ScreensTest`, `GamificationModuleTest`, `AppNavHostTest`.
Ergänzt: `BoardScreenTest` (Avatar-Slot), `ScreensTest` (Avatar in Rangliste).

## Next Steps
- [ ] Abnahme auf dem Testhandy
- [ ] `/code-review`, dann PR
