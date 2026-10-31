# Implementation Report: Badges in der App (GAME Phase 2b)

## Summary
Das Modul `feature:gamification` zeigt die Badges aus Phase 2a: Abschnitt „Badges n/m“ auf „Ich“, Neu-Hinweis-Dialog, Seite
„Badges von <Name>“ (Rangliste antippbar, nur online) und Badge-Abschnitt auf dem Punktekonto der Eltern. Lokaler Stand
(`game_points.json`) auf Version 2. Keine Backend-/API-Änderung. App 0.11.0 / `versionCode` 12.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | – | hoch; Plan passte ohne Abweichung in der Architektur |
| Files Changed | ≈ 8 neue, ≈ 14 geänderte | 8 neue (3 Tests inkl. `BadgesTest`), 23 geänderte |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | Domänenmodell `Badges.kt` | Done | |
| 2 | Snapshot + `PointsLoad.Fresh.newBadges` | Done | |
| 3 | Port, Remote-Adapter, Mapper | Done | |
| 4 | Lokaler Stand Version 2 | Done | |
| 5 | Use Cases (`LoadBadgesUseCase`, Neu-Erkennung) | Done | `fetch` mit `Fetched`-Klasse und `filterIsInstance` statt `when` |
| 6 | `BadgeList`, `NewBadgesDialog` | Done | |
| 7 | „Ich“ erweitert | Done | |
| 8 | Seite „Badges von …“ | Done | |
| 9 | Eltern-Abschnitt Punktekonto | Done | |
| 10 | Navigation, Koin, Strings, Version | Done | |
| 11 | Tests | Done | |
| 12 | Doku (Design, Traceability, PRD) | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck`, `detekt`, `lintDebug` |
| Unit Tests | Pass | `testDebugUnitTest` (alle Module) |
| Build | Pass | `assembleDebug`, `assembleRelease` |
| Coverage | Pass | `koverVerifyDebug` |
| Manual (Testhandys) | Offen | Abnahme laut Plan steht aus |

## Deviations from Plan
- `strings.xml`: „???“ muss als `\?\?\?` escaped werden (sonst liest aapt es als Theme-Attribut).
- `ChildAccountViewModel.refresh()` setzt `error` auf den ersten Fehler (Konto vor Badges) und behält zuvor geladene Teile bei Teilfehlern.
- `@Suppress("LongParameterList")` am Test-Helfer `badge(...)`; zwei zusätzliche Tests (leere Badge-Liste, Fortschritt `10/10`,
  Member-Badges-Fehler, Eltern-Screen) für die Edge-Case-Liste.

## Issues Encountered
- macOS-`sed -i` braucht `''`; Versions-Bump wurde wiederholt.
- Import der Composable `MemberBadgesRoute` neben der gleichnamigen Route-Klasse fehlte (wie bei `ChildAccountRoute` gelöst).

## Tests Written

| Test File | Coverage |
|---|---|
| `domain/BadgesTest` | `newlyEarned` (Basislinie, neu, maskiert) |
| `domain/UseCasesTest` | Kind lädt Badges, Neu-Erkennung, Basislinie, Eltern ohne Badges, halber Stand, Geschwister |
| `data/RemotePointsRepositoryTest` | Mapping erhalten/offen/maskiert, Datum fehlt → Serverfehler |
| `data/DataStorePointsCacheTest` | Badges überleben Neustart, Version 1 gilt als leer |
| `ui/ViewModelsTest` | Me (Dialog, kein Duplikat), Member Badges, Punktekonto (Fortschritt, Teilfehler) |
| `ui/ScreensTest` | Liste, leer, 10/10, Dialog, Geschwister-Klick, offline, Member-Seite, Punktekonto |
| `GamificationModuleTest`, `AppNavHostTest` | Koin-Auflösung, VM-Konstruktoren |

## Next Steps
- [ ] `/code-review`
- [ ] PR (`/prp-pr`)
- [ ] Abnahme auf den Testhandys gegen die Testinstanz (`FP_TEST_INSTANCE=true ./gradlew :app:assembleRelease`)
