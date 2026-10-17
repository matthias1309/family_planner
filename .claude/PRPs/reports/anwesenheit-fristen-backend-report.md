# Implementation Report: Anwesenheit & Fristen – Backend (Phase 2a)

## Summary
Neues Gradle-Modul `backend/meals` mit Anwesenheitsmustern (Always / Wechselmodell A/B), Ausnahmen, konfigurierbarer
Bestellfrist und Kind-Einreichfrist (REQ-MEAL-010–013): reine Domänenfunktionen (Europe/Berlin), Migration V2,
Exposed-Repositories, zwei Services, 8 Endpunkte inkl. OpenAPI-Vertrag 0.2.0.

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files Changed | ~30 | 34 (23 neu in `meals`, 4 verschoben, übrige angepasst) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | ADR-0010 & Moduldesign | Done | `meals.md`, Architektur-README |
| 2 | OpenAPI-Vertrag | Done | Version 0.2.0, Generator erzeugt Modelle |
| 3 | Modul-Gerüst, testFixtures, Migration | Done | 4 Fakes per `git mv` nach `core/src/testFixtures` |
| 4 | Domäne (reine Funktionen) | Done | Tests und Code gemeinsam geschrieben (kein separater RED-Lauf) |
| 5 | Repositories + Fakes | Done | |
| 6 | Services | Done | |
| 7 | Routen, Mapper, Koin | Done | `RequestParsing.kt` ausgelagert (detekt `TooManyFunctions`) |
| 8 | Integrations-/Contract-Tests | Done | 15 Tests, alle Requests gegen `openapi.yaml` validiert |
| 9 | Qualität & Doku | Done | README, Traceability, PRD |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis (`ktlintCheck detekt`) | Pass | |
| Unit/Integration (`check`) | Pass | 9 + 12 + 4 + 9 + 5 Tests in `meals`, 15 in `app` |
| Coverage (`koverVerify`) | Pass | Schwelle 80 % |
| Android-Vertrag (`:core:api:compileDebugKotlin`) | Pass | |
| Fat JAR + Start auf frischer DB | Pass | Flyway: „Successfully applied 2 migrations“, Tabellen vorhanden |
| Docker-Image bauen | Nicht ausgeführt | |
| Migration auf bestehender V1-DB mit Backup | Nicht ausgeführt | durch `DatabaseFactoryTest` (Backup vor Migration) abgedeckt |

## Deviations from Plan
- Mapper-Hilfsfunktionen (`parseDate`, `parseTime`, `formatTime`) in `RequestParsing.kt` ausgelagert, weil detekt `TooManyFunctions` (>15) meldete.
- `lateinit` auf Inline-Klasse `MemberId` im Repository-Test nicht erlaubt → normales `var`.
- Tests wurden zusammen mit der Implementierung geschrieben statt strikt Test-zuerst mit RED-Lauf.

## Issues Encountered
- Ein erster `buildFatJar`-Lauf schlug fehl, weil parallel der Android-Gradle-Build lief; Wiederholung war grün.
- Fehlerhafte Zeilenfilterung beim Aufräumen eines Tests zerlegte eine Assertion; sofort repariert, Tests grün.

## Offene manuelle Prüfungen (aus dem Plan)
- Kopie der Pi-Datenbank lokal migrieren (V2 + Backup).
- `curl`-Durchlauf mit echten Wochen und Eltern-Bestätigung der Fristen.
- Nach Merge: CI, Pi-Deploy, Flyway-Log, Backup.

## Next Steps
- [ ] `/code-review`
- [ ] `/prp-commit`, dann `/prp-pr`
- [ ] Phase 2b (Android-Eltern-UI) planen
