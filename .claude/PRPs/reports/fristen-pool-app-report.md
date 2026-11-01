# Implementation Report: Fristen & Pool – App (TASK Phase 5)

## Summary
Die Android-App nutzt die Phase-4-API: Frist beim Zuweisen und an der Karte, Freigabe-Schalter im Katalog, Pool-Screen für Kinder,
Frist/„Überfällig“/„Zu spät gemeldet“/„Selbst genommen“ auf Board und „Heute“, lokale Erinnerung 2 h vor Fristende (ADR-0013).

## Assessment vs Reality
| Metric | Predicted | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files Changed | ~35 | 53 geändert/neu (inkl. Tests, Doku) |

## Tasks Completed
Alle 14 Tasks erledigt (Domäne, Board-Regeln, Katalog/Pool, Ports/Use Cases, Erinnerungen, Sync, Datenschicht, Alarm/Receiver/Manifest,
Board-UI, Frist-Picker, Zuweisen/Katalog, Pool-Screen/Navigation/Koin, „Heute“/Strings/Formatierung/Fakes, Doku).

## Validation Results
| Level | Status |
|---|---|
| ktlintCheck, detekt, lintDebug | Pass |
| Unit-Tests (`testDebugUnitTest`, alle Module) | Pass (feature:tasks: 183 Tests) |
| Kover (`koverVerifyDebug`) | Pass |
| Build (`assembleDebug`) | Pass |
| Manuelle Geräte-Abnahme | offen (Checkliste in `android/README.md`) |

## Deviations from Plan
- `app`-Modul-Tests (`AppTaskFakes`, `AppNavHostTest`) mussten an die neuen Port-Signaturen angepasst werden (nicht im Plan).
- Pool-FAB nur online sichtbar (Plan: Aktionen offline ausgeblendet).
- `ShowTaskReminderUseCase` setzt `TaskReminder.at` auf die aktuelle Zeit (der Alarmzeitpunkt wird dort nicht gebraucht).
- Picker-Dialoge selbst nicht per Compose-Test bedient; getestet sind die Umrechnungsfunktionen und Button-Texte (wie im Plan vorgesehen).

## Issues Encountered
- Compose-Test fand FAB-Text nur im ungemergten Baum → `useUnmergedTree`.

## Next Steps
- [ ] `/code-review`, dann `/prp-pr`
- [ ] Geräte-Abnahme TASK Phase 5
