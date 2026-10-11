# Implementation Report: Fundament & AUTH – Backend, CI, Deployment (Phase 1a)

## Summary
Gradle-Multi-Modul-Backend (`api-contract`, `core`, `app`) mit OpenAPI-Vertrag, SQLite/Flyway/Exposed, AUTH (Mitglieder, Rollen,
Setup-Token, Kopplungscode, Geräte-Token, Widerruf), Ktor-App mit TLS-Option, Dockerfile, GitHub-Actions-CI und Pull-Deployment
(Compose, update.sh mit Rollback, systemd-Timer).

## Assessment vs Reality
| Metric | Predicted | Actual |
|---|---|---|
| Complexity | Large | Large |
| Files | ~45 | ~75 (inkl. Tests, Fakes, Gradle-Wrapper) |

## Tasks Completed
| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Voraussetzungen | done | JDK 21 + Gradle 9.8.1 waren bereits installiert |
| 1 | Anforderungen & ADR | done | `auth.prd.md`, ADR-0008, `core.md`, Architektur-Kontextgrafik korrigiert |
| 2 | OpenAPI | done | |
| 3 | Gradle-Gerüst | done | Deviation: Versionen s. u. |
| 4 | Persistenz | done | Deviation: Backup-Prüfung s. u. |
| 5 | Domäne & Services | done | RED (223 Compile-Fehler) → GREEN |
| 6 | Auth & Routen | done | Deviation: 401-Envelope s. u. |
| 7 | App-Modul | done | |
| 8 | Integrations-/Contract-Tests | done | |
| 9 | Qualität & Coverage | done | ktlint, detekt ohne Befunde; Line-Coverage 94 % |
| 10 | Container-Image | done | lokal gebaut, Container `healthy`, Curl-Ablauf geprüft |
| 11 | CI | done | Workflow geschrieben; Lauf erst nach Push/PR prüfbar |
| 12 | Deployment | done | Compose `config` ok, `bash -n` ok; Pi-Probe offen |
| 13 | Doku & Traceability | done | |

## Validation Results
| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck`, `detekt` |
| Unit Tests | Pass | core 34, api-contract 1 |
| Build | Pass | `check koverVerify`, `:app:buildFatJar`, `docker build` |
| Integration | Pass | app 28 Tests, jeder Endpunkt gegen `openapi.yaml` validiert; Container-Smoke-Test per curl |
| Edge Cases | Pass | leer/zu lang/ADMIN-Rolle/kaputtes JSON/Nicht-UUID/Rate-Limit/widerrufenes Gerät |

## Deviations from Plan
- **Versionen:** deutlich neuer als die Plan-Mindeststände (Kotlin 2.4.21, Ktor 3.6.0, Exposed 1.5.1, Flyway 13.10.0,
  sqlite-jdbc 3.53.4.0, Hikari 7.1.0, Koin 4.2.2, Logback 1.6.5). JUnit 5.13.4 statt 6.x (kotlin-test-junit5-Kompatibilität),
  swagger-request-validator 2.46.1 statt 3.0.0, detekt 1.23.8 läuft unverändert mit Kotlin 2.4.
- **UUID-Typ:** `typeMappings UUID → kotlin.String` im Generator, sonst entstehen `@Contextual java.util.UUID`-Felder.
  `ErrorResponse.data` aus dem Schema entfernt (generierte `Any?`-Contextual-Felder).
- **401-Envelope:** Ktor-Bearer hat kein `challenge {}`; stattdessen `status(HttpStatusCode.Unauthorized)` in StatusPages.
- **Atomare Operationen:** Statt `dbQuery` über mehrere Repos (Deadlock-Gefahr bei Pool-Größe 1) sind Setup und Kopplung als
  atomare Repository-Methoden (`createFirstParentWithDevice`, `PairingCodeRepository.redeem`) umgesetzt.
- **Backup-Bug gefunden:** Hikari legt die DB-Datei beim Pool-Start an; Existenz wird deshalb *vor* dem Pool geprüft
  (`databaseExisted`), sonst würde beim Erststart ein leeres Backup entstehen.
- **gen-cert.sh:** Keystore-Alias `planner` (`-name planner`), damit `AppConfig` den Schlüssel findet.
- Systemuhr in Produktion als `Clock.tickMillis`, damit API-Zeitstempel konsistent auf Millisekunden stehen.

## Issues Encountered
- Docker-Daemon lief nicht → Docker Desktop gestartet.
- Mehrere Dateien landeten kurz im falschen Verzeichnis (fehlgeschlagenes `cd`); verschoben, nichts verloren.

## Noch offen (nicht lokal prüfbar)
- CI-Lauf auf GitHub (PR/Merge), GHCR-Paket auf *public* stellen.
- Pi-Einrichtung, Timer, ntfy-Meldung, optionale Rollback-Probe, Uptime-Kuma-Monitor.
- `shellcheck` war nicht installiert.

## Next Steps
- [ ] `/ecc:code-review`, danach `/ecc:prp-commit` und `/ecc:prp-pr`
- [ ] Phase 1b (Android-Gerüst, Kopplungs-UI, Zertifikat-Pinning)
