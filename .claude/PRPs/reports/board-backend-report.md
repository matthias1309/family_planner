# Implementation Report: Aufgaben-Board Backend/API (TASK Phase 2)

## Summary
Neues Gradle-Modul `tasks` (Katalog, Karten, Statusübergänge mit Rechteprüfung, REST-API) und ein Ereignisprotokoll (Outbox) in `core`
nach ADR-0016. Bei jeder Abnahme wird `task.accepted.v1` in derselben Transaktion geschrieben; der Dispatcher stellt nach dem Request
und beim Start zu (noch ohne Abonnenten). API nur additiv (`openapi.yaml` 0.6.0).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large (≈ 35 Dateien) | Large |
| Confidence | – | hoch; alle Plan-Tasks umgesetzt |
| Files Changed | ≈ 35 | ≈ 45 (inkl. Tests und Doku) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 1 | ADR-0016 | Done | |
| 2 | Ereignisprotokoll in `core` | Done | Test-Migration `V2__extra.sql` → `V100__extra.sql` (sonst Flyway-Validierung wegen V7) |
| 3 | OpenAPI-Vertrag | Done | |
| 4 | Modul `tasks` | Done | |
| 5 | Katalog | Done | |
| 6 | Karten und Übergänge | Done | Hilfsfunktionen in `BoardRules.kt` ausgelagert (detekt: TooManyFunctions/ThrowsCount) |
| 7 | Routen und Mapper | Done | Optionaler Body bei `rejection` über Content-Type-Prüfung; Test mit und ohne Body |
| 8 | App-Verdrahtung | Done | |
| 9 | Tests | Done | |
| 10 | Doku, Traceability | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `./gradlew check` (ktlint + detekt) |
| Unit Tests | Pass | Fakes + Fixture, `CatalogServiceTest`, `TaskTransitionsTest`, `BoardServiceTest`, `DomainEventCodecTest` |
| Build | Pass | `check koverVerify :app:buildFatJar` grün, Kover ≥ 80 % |
| Integration | Pass | Repo-Tests auf echter SQLite, Dispatcher, API-Tests mit Vertragsprüfung |
| Android | Teilweise | `:core:api:compileDebugKotlin` grün; `assembleDebug` nicht vollständig gelaufen |
| Manuell (curl / Pi-DB-Kopie) | Offen | siehe Plan „Manual Validation“ |

## Deviations from Plan
- `createAll` im Karten-Adapter speichert auch `rejectionReason`, `reportedAt`, `doneAt` (Tests legen Karten in beliebigem Status an).
- `BoardService`-Hilfen in `BoardRules.kt` (Plan: alles in `BoardService`), wegen detekt.

## Issues Encountered
- Bestehender `DatabaseFactoryTest` nutzte Test-Migration Version 2 → mit V7 nicht mehr „in Reihenfolge“; auf V100 umbenannt.

## Next Steps
- [ ] Code-Review (`/ecc:code-review`)
- [ ] Manuelle Validierung (curl, Migration auf Kopie der Pi-DB)
- [ ] PR
