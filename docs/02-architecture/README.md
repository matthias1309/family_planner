# Architektur

## Kontext

```
 Android-App ──(HTTPS im Heimnetz)──▶ Backend (Ktor, Container) ──▶ SQLite (./data)
                                              │
                                              └──▶ ntfy (Push, vorhanden auf dem Pi)
```

- **Nutzer:** eine Familie (keine Mandantenfähigkeit im MVP)
- **Betrieb:** Raspberry Pi 5 (`dockerhome`), geteilt mit ~15 weiteren Containern
- **Erreichbarkeit:** nur im Heimnetz (kein Fernzugriff im MVP)

## Bausteine

| Baustein | Verantwortung |
|---|---|
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
