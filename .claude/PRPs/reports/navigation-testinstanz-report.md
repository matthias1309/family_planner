# Implementation Report: Navigation & Testinstanz (TASK Phase 1)

## Summary
Untere `NavigationBar` mit vier rollenabhängigen Hauptbereichen, Startseite „Heute“ mit Karte „Heute gibt es“, neue
Übersicht „Essen“, Platzhalter für „Aufgaben“/„Ich“. Testinstanz (Compose-Profil `test`, Port 8097, `./data-test`) und
Test-Build der App (`FP_TEST_INSTANCE`). ADR-0014/0015. Keine Änderung an `api/` und `backend/`.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Medium | Medium |
| Confidence | – | hoch (Geräte-Abnahme steht aus) |
| Files Changed | ≈ 20 | 22 geändert, 9 neu |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | ADRs 0014/0015 | Done | |
| 2 | „Heute“ in `feature:auth` | Done | `mainGraph` ohne `navController`-Parameter (nicht mehr gebraucht) |
| 3 | „Essen“ + Heute-Karte in `feature:meals` | Done | |
| 4 | Leiste und Hauptbereiche in `app` | Done | Datei heißt `TopLevelDestination.kt` (detekt `MatchingDeclarationName`) |
| 5 | Test-Build | Done | APK geprüft: `de.familyplanner.android.test`, „Familienplaner Test“ |
| 6 | Testinstanz auf dem Pi | Done | `docker compose config`: 1 bzw. 2 Services; `bash -n update.sh` ok; shellcheck nicht installiert |
| 7 | Tests | Done | |
| 8 | Version 0.8.0/9, Doku, Traceability, Checkliste | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlint, detekt, lintDebug |
| Unit Tests | Pass | auth, meals, app |
| Build | Pass | assembleDebug, assembleRelease (R8), Test-Build-Release |
| Coverage | Pass | `koverVerifyDebug` ≥ 80 % |
| Integration / Gerät | Offen | Abnahme auf den Testhandys (Checkliste in `android/README.md`) |

## Deviations from Plan
- `REQ-TASK-001 parent sees family as main area`: prüft nur die Sichtbarkeit von „Familie“ in der Leiste, nicht den Tipp auf den
  Familien-Screen (`FamilyViewModel` bräuchte fünf Abhängigkeiten im App-Testmodul). „Familie ohne Zurück“ deckt `ScreensTest` (auth) ab.
- `AppNavHostTest`: Fake `me()` folgt der Session, sonst überschrieb das Auffrischen die Rolle auf Kind.
- Zusätzlich `family screen with back handler shows back button` (auth).

## Issues Encountered
- Nach dem Auffrischen (`/me`) überschrieb der Test-Fake die Eltern-Rolle → Fake angepasst (siehe oben).

## Tests Written
`MainDestinationsTest` (3), `TodayMealTest` (9), `ScreensTest` auth (+4/-2), `AppNavHostTest` (+6, 1 angepasst), `MealsModuleTest` (+1 Zeile).

## Next Steps
- [ ] Code-Review, Commit, PR
- [ ] Testinstanz auf dem Pi einrichten, Abnahme-Checkliste auf den Testhandys
