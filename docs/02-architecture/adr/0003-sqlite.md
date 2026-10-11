# ADR-0003: SQLite als Datenbank

- **Status:** Akzeptiert
- **Datum:** 2026-10-11

## Kontext

Eine Familie, wenige gleichzeitige Nutzer, geringe Schreiblast. Auf dem Pi läuft bereits ein Postgres für
Paperless; ein eigener DB-Container würde zusätzlich RAM kosten.

## Entscheidung

- SQLite-Datei im Bind-Mount `./data/family-planner.db`.
- **WAL-Modus**, `foreign_keys=ON`, `busy_timeout` gesetzt; ein Schreib-Connection-Pool der Größe 1.
- Schema-Migrationen mit **Flyway**.
- Backup: konsistente Kopie per `VACUUM INTO` (oder SQLite Online-Backup) nach Zeitplan, nie die laufende Datei kopieren.

## Alternativen

- **Postgres (eigener Container):** robuster bei vielen Schreibern, aber mehr RAM und Betrieb — hier nicht nötig.
- **Paperless-Postgres mitbenutzen:** spart RAM, koppelt aber zwei unabhängige Systeme (Updates, Backups) — verworfen.

## Konsequenzen

- Kein zusätzlicher Container, keine DB-Zugangsdaten.
- Tests laufen mit echter SQLite (In-Memory oder Temp-Datei) — keine Mocks nötig für Integrationstests.
- Wechsel zu Postgres bleibt durch Exposed + Repository-Pattern mit überschaubarem Aufwand möglich.
