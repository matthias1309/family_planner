# Architektur

## Kontext

```
 Android-App ──(HTTPS im Heimnetz)──▶ Backend (Ktor, Container) ──▶ SQLite (./data)
```

Deploy-Meldungen (Update/Rollback) gehen per ntfy an die Eltern; Push an die App läuft nicht über ntfy (ADR-0007).

- **Nutzer:** eine Familie (keine Mandantenfähigkeit im MVP)
- **Betrieb:** Raspberry Pi 5 (`dockerhome`), geteilt mit ~15 weiteren Containern
- **Erreichbarkeit:** nur im Heimnetz (kein Fernzugriff im MVP)

## Bausteine

| Baustein | Verantwortung |
|---|---|
| `api/openapi.yaml` → `backend/api-contract` | Vertrag; daraus werden die Modelle für Server (und später Client) generiert |
| `backend/core` | Auth, Familienmitglieder, gemeinsame Typen, Event-Bus zwischen Modulen |
| `backend/tasks` | Kanban-Board, Aufgaben |
| `backend/gamification` | Punkte, Belohnungen (reagiert auf Events aus `tasks`) |
| `backend/meals` | Essensplan, Rezepte, Einkaufsliste |
| `backend/board` | Infoboard / Pinnwand |
| `backend/app` | Ktor-Server, verdrahtet die Module |
| `android/` | App, Feature-Module spiegeln die Backend-Module |

## Entscheidungen

Siehe [adr/](adr/).

| ADR | Titel | Status |
|---|---|---|
| [0001](adr/0001-monorepo-modular-monolith.md) | Monorepo & modularer Monolith | Akzeptiert |
| [0002](adr/0002-kotlin-stack.md) | Kotlin für Backend und Android | Akzeptiert |
| [0003](adr/0003-sqlite.md) | SQLite als Datenbank | Akzeptiert |
| [0004](adr/0004-api-first-openapi.md) | API-first mit OpenAPI | Akzeptiert |
| [0005](adr/0005-deployment-raspberry-pi.md) | Deployment auf dem Pi | Akzeptiert |
| [0006](adr/0006-network-access-lan-only.md) | Zugriff nur im Heimnetz | Akzeptiert |
| [0007](adr/0007-meal-deadlines-without-remote-access.md) | Essensplan-Fristen ohne Fernzugriff | Akzeptiert |
| [0008](adr/0008-device-pairing.md) | Geräte-Anmeldung per Kopplungscode | Akzeptiert |
| [0009](adr/0009-android-app-architecture.md) | Android-App-Architektur | Akzeptiert |
| [0010](adr/0010-backend-feature-modules.md) | Backend-Feature-Module | Akzeptiert |
| [0011](adr/0011-app-offline-cache.md) | Lokaler Lese-Cache der App | Akzeptiert |
| [0012](adr/0012-recipes-and-rewe-import.md) | Rezeptsammlung und Rezept-Import in der App | Akzeptiert |
