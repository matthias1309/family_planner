# Family Planner

Familienplaner mit Android-App und selbst gehostetem Backend auf einem Raspberry Pi 5.
Features (modular): Kanban-Aufgaben, Gamification, Essensplaner, Infoboard.

## Stack

- **Backend:** Kotlin + Ktor, SQLite (WAL) via Exposed, Migrationen mit Flyway — `backend/`
- **Android:** Kotlin + Jetpack Compose, Clean Architecture — `android/`
- **API-Vertrag:** OpenAPI 3 in `api/` ist die Quelle der Wahrheit; Client/Server-Modelle werden daraus generiert
- **Deployment:** GitHub Actions baut/testet und pusht `linux/arm64`-Image nach GHCR (Tag `stable`); der Pi zieht es selbst per systemd-Timer (`deploy/`, ADR-0005)

## Architektur-Leitplanken

- **Modularer Monolith:** ein Gradle-Modul pro Feature (`tasks`, `gamification`, `meals`, `board`) plus `core`.
  Feature-Module kennen sich nicht gegenseitig; Kommunikation nur über Interfaces/Events in `core`.
- API-first: erst `api/openapi.yaml` ändern, dann implementieren.
- Entscheidungen werden als ADR in `docs/02-architecture/adr/` festgehalten — vor Abweichungen ein neues ADR schreiben.

## Dokumentation (leichtes V-Modell)

| Ebene | Dokument | Verifiziert durch |
|---|---|---|
| Anforderungen | `docs/01-requirements/` (IDs `REQ-<MODUL>-<NR>`) | E2E-/Abnahmetests |
| Architektur | `docs/02-architecture/` + ADRs | Integrationstests |
| Moduldesign | `docs/03-design/<modul>.md` | Unit-Tests (TDD) |

Tests referenzieren die REQ-ID im Namen, z. B. `` `REQ-TASK-001 task moves to done when completed`() ``.
Die Zuordnung wird in `docs/04-verification/traceability.md` gepflegt.

## Arbeitsweise

Pro Feature: Anforderung (REQ) → `/ecc:plan` → TDD → `/ecc:code-review` → PR → CI grün → Deploy.
Regeln: `.claude/rules/ecc/common/` und `.claude/rules/ecc/kotlin/`.

| Situation | Befehl / Werkzeug |
|---|---|
| Anforderungen erarbeiten | `/ecc:prp-prd` → `docs/01-requirements/` |
| Erster lauffähiger Stand aus dem PRD (MVP) | `/ecc:orch-build-mvp` |
| Neues Feature end-to-end (Recherche, Plan, TDD, Review, Commit) | `/ecc:orch-add-feature` |
| Bestehendes Feature ändern | `/ecc:orch-change-feature` |
| Fehler beheben (erst fehlschlagender Regressionstest) | `/ecc:orch-fix-defect` |
| API-Änderung | Skill `contract-first` — zuerst `api/openapi.yaml` (ADR-0004) |
| Commit-Message prüfen | Agent `ecc:commit-reviewer` |

Die `orch-*`-Befehle sind noch nicht erprobt — nach dem ersten Einsatz hier Erfahrungen ergänzen.

## Zielsystem (Pi `dockerhome`)

- Raspberry Pi 5, 8 GB RAM, Debian 13 arm64, Docker + Compose, viele andere Container (Home Assistant, Paperless, …)
- **Memory-cgroup ist deaktiviert** → Docker-`mem_limit` wirkt nicht; JVM-Heap immer explizit begrenzen (`-Xmx`)
- Apps liegen unter `/opt/containers/apps/<name>/`, Daten als Bind-Mount `./data`
- App-Zugriff nur im Heimnetz per HTTPS (selbstsigniertes Zertifikat, in der App gepinnt) — kein Fernzugriff (ADR-0006)
- Vorhandene Dienste nutzbar: Pi-hole (lokaler DNS-Name), ntfy (Push), Uptime Kuma (Monitoring)
- Belegte Ports nicht verwenden (53, 1883, 2222, 3000–3002, 8000, 8080, 8081, 8086, 8090, 8123, 8443); Family Planner: **8095**
