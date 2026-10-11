# family_planner

Familienplaner mit Android-App und selbst gehostetem Backend (Raspberry Pi, Docker).

Geplante Module: Kanban-Aufgaben · Gamification · Essensplaner · Infoboard

## Struktur

| Ordner | Inhalt |
|---|---|
| `api/` | OpenAPI-Vertrag zwischen App und Backend |
| `backend/` | Kotlin/Ktor-Backend (modularer Monolith, SQLite) |
| `android/` | Android-App (Kotlin, Jetpack Compose) |
| `deploy/` | Docker Compose & Deployment für den Pi |
| `docs/` | Anforderungen, Architektur (ADRs), Design, Verifikation |

Details zu Architektur und Arbeitsweise: [CLAUDE.md](CLAUDE.md) und [docs/](docs/README.md).
