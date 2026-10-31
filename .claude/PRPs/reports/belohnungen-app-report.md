# Implementation Report: Echte Belohnungen in der App (GAME Phase 3b)

## Summary
Kind- und Eltern-Bildschirme für Belohnungen im Modul `feature:gamification`: Sparziel-Karte auf „Ich“ und „Heute“, Seite „Belohnungen“
(Sparziel, Einlösen, eigene Einlösungen), Seite „Belohnungen verwalten“ (anlegen/ändern/deaktivieren, erfüllen/ablehnen), Zeile
„Belohnungen · n warten“ unter „Familie“ und WorkManager-Abgleich mit Systembenachrichtigungen (ADR-0013). Keine Backend-/API-Änderung. App 0.12.0 / 13.

## Tasks Completed
Alle 15 Tasks des Plans (Domäne, Ports, Mapper/Remote, Cache v3, Use Cases, Hinweis-Regeln + Abgleich, Stand/Notifier/Worker/Open-Request,
Komponenten, „Ich“, Kind-Seite, Eltern-Seite + Familie, „Heute“, Navigation/Koin/App/Strings/Version, Tests, Doku).

## Validation Results

| Level | Status | Notes |
|---|---|---|
| ktlint / detekt / lintDebug | Pass | |
| Unit Tests | Pass | `:feature:gamification` + `:app` |
| Kover (≥ 80 %) | Pass | `koverVerifyDebug` |
| Build | Pass | `assembleDebug`, `assembleRelease` |
| Manuelle Abnahme (Testhandys) | Offen | Liste im Plan |

## Deviations from Plan
- `RedemptionList` ohne Parameter `names`/`showNames` (die Eltern-Seite rendert wartende Einlösungen als eigene Zeilen mit Namen).
- Detekt: `RewardRepository` mit `@Suppress("TooManyFunctions")` (spiegelt 11 Endpunkte); `ApiRedemption.toDomain()` auf einen Rückgabepunkt umgebaut.
- Bestehende `ScreensTest`-Fälle für „Ich“ scrollen jetzt zur Rangliste (neue Karte schiebt sie aus dem Testfenster).
- Kind-Seite: Bei `Conflict` wird erst neu geladen und dann der Fehler gesetzt, damit `refresh()` ihn nicht löscht.

## Issues Encountered
Keine offenen. Nicht verifiziert: Benachrichtigungen und WorkManager-Zeitverhalten auf echten Geräten (`AndroidRewardNotifier`/Worker nur per Lint/Build geprüft, wie bei Aufgaben).

## Next Steps
- [ ] Code-Review (`/ecc:code-review`)
- [ ] PR (`/ecc:prp-pr`)
- [ ] Abnahme auf Testhandys gegen Testinstanz (Backend 3a deployt)
