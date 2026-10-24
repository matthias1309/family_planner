# Moduldesign: `tasks` – Aufgabenkatalog und Board (Backend)

## Abgedeckte Anforderungen

REQ-TASK-010, REQ-TASK-020, -021, -022, -023, -024, -025 (Phase 2, Backend/API) — siehe [tasks.prd.md](../01-requirements/tasks.prd.md).
Die App-Seite folgt in Phase 3. Konventionen für Feature-Module: [ADR-0010](../02-architecture/adr/0010-backend-feature-modules.md);
Ereignisse an andere Module: [ADR-0016](../02-architecture/adr/0016-domain-events-outbox.md).

## Domänenmodell & Regeln

| Begriff | Definition |
|---|---|
| `CatalogTask` | Vorbereitete Aufgabe („Magnetschild“): `title` (1–40), `emoji?` (≤ 16, leer = keins), `points` (1–100), `archived` |
| `TaskCard` | Karte eines Kindes. Titel, Symbol, Punkte sind beim Zuweisen **kopiert**; `catalogTaskId` nur Herkunft (`SET NULL` beim Löschen) |
| `TaskStatus` | `TODO` → `REVIEW` → `DONE`; `DONE` ist endgültig |
| Löschen im Katalog | archiviert (`archived_at`); archivierte Aufgaben sind nicht zuweisbar und nicht änderbar |
| Zuweisen | nur an Mitglieder mit Rolle `CHILD`; 1–20 Katalog-IDs je Aufruf, Duplikate ergeben je eine Karte |
| Board | eine Spur je Kind (nach Name), auch leer; Karten `TODO` (nach `assignedAt`), `REVIEW` (nach `reportedAt`), `DONE` der letzten 7 Tage (neueste zuerst) |
| Verlauf | alle `DONE`-Karten eines Kindes, neueste zuerst, `limit` 1–100 (Standard 50) |

### Statusmaschine (Rolle × Übergang)

| Übergang | Endpunkt | Wer | von → nach | Besonderheit |
|---|---|---|---|---|
| `REPORT` | `…/report` | Kind, eigene Karte | `TODO` → `REVIEW` | löscht `rejectionReason`, setzt `reportedAt` |
| `WITHDRAW` | `…/withdrawal` | Kind, eigene Karte | `REVIEW` → `TODO` | löscht `reportedAt` |
| `ACCEPT` | `…/acceptance` | Eltern | `REVIEW`/`TODO` → `DONE` | setzt `doneAt`, `doneBy`; schreibt `task.accepted.v1` in derselben Transaktion |
| `REJECT` | `…/rejection` | Eltern | `REVIEW` → `TODO` | optionale Begründung (≤ 200) wird gespeichert |
| Entfernen | `DELETE …/cards/{id}` | Eltern | `TODO`/`REVIEW` → gelöscht | `DONE` → `409` |

Falsche Rolle oder fremde Karte → `403`; falscher Ausgangsstatus → `409`; das bedingte `UPDATE … WHERE status IN (…)` entscheidet
atomar (zweite gleichzeitige Abnahme trifft 0 Zeilen, es entsteht kein zweites Ereignis).

## Schnittstellen

Endpunkte siehe `api/openapi.yaml` (Tag `tasks`):

- `GET|POST /api/v1/tasks/catalog` (Eltern; `?includeArchived=true`), `PUT|DELETE /api/v1/tasks/catalog/{catalogTaskId}` (Eltern; DELETE archiviert, 204)
- `POST /api/v1/tasks/cards` (Eltern, 201), `DELETE /api/v1/tasks/cards/{cardId}` (Eltern, 204)
- `GET /api/v1/tasks/board`, `GET /api/v1/tasks/members/{memberId}/history` (alle Mitglieder)
- `POST /api/v1/tasks/cards/{cardId}/report|withdrawal|acceptance|rejection` (200 mit der geänderten Karte)

Nach `acceptance` ruft `BoardService` `EventNotifier.dispatchPending()` auf; Fehler der Zustellung scheitern den Request nicht.

## DB-Tabellen

`task_catalog`, `task_cards` — Migration `V8__tasks.sql` (Zeitstempel als Epoch-Millis, IDs als UUID-Text; `CHECK` auf Status und Punkte).
Ereignisse liegen in `core_domain_events` (Migration `V7`, siehe [core.md](core.md)).

## Eingabegrenzen

Titel 1–40, Symbol ≤ 16, Punkte 1–100, 1–20 Aufgaben je Zuweisung, Begründung ≤ 200, Verlauf-`limit` 1–100.

## Unit-Testfälle

`CatalogServiceTest`, `TaskTransitionsTest`, `BoardServiceTest` (Fakes in `tasks/src/test/.../fakes`): Validierung und Grenzen, Kopieren
beim Zuweisen, nur Kinder, archivierte/unbekannte Aufgaben, Spuren und Sortierung, 7-Tage-Fenster, Rechte je Übergang, Abnahme
schreibt genau ein Ereignis, Zurückweisen mit/ohne Grund, `DONE` ist endgültig, Entfernen. Integration: `ExposedCatalogRepositoryTest`,
`ExposedTaskCardRepositoryTest` (echte SQLite, Ereignis atomar, parallele Abnahme, Migrations-Checks), API: `TasksCatalogApiTest`,
`TasksBoardApiTest` (Vertragsprüfung gegen `openapi.yaml`).
