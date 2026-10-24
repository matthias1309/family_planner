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

## App-Seite (Phase 3)

Modul `android/feature/tasks` (Paket `de.familyplanner.android.feature.tasks`, Aufbau `domain/` · `data/` · `ui/` wie `feature:meals`; die beiden
Module kennen sich nicht, ADR-0001/0009). Keine Backend- oder API-Änderung. Lokaler Stand und Hinweise folgen
[ADR-0011](../02-architecture/adr/0011-offline-read-cache.md) und [ADR-0013](../02-architecture/adr/0013-local-reminders-and-status-sync.md),
die Navigation [ADR-0014](../02-architecture/adr/0014-app-main-navigation.md).

### Bildschirme

| Bildschirm | Route | Wer | Inhalt |
|---|---|---|---|
| Board | `TasksHomeRoute` (Hauptbereich „Aufgaben“) | alle | Reiter je Kind (Kind: eigene Spur zuerst, Eltern: zusätzlich „Zur Abnahme (n)“ vorn); Abschnitte Todo · Zur Abnahme · Erledigt (7 Tage); „Verlauf anzeigen“ |
| Zuweisen | `AssignRoute(memberId?)` | Eltern | Kind wählen (vorbelegt mit dem aktuellen Reiter), Katalogaufgaben abhaken, „Zuweisen (n)“, 1–20 |
| Katalog | `CatalogRoute` | Eltern | aktive Aufgaben, „Neu“, Antippen = bearbeiten |
| Katalog bearbeiten | `CatalogEditRoute(id?)` | Eltern | Titel (0/40), Symbol, Punkte-Stepper 1–100, Archivieren mit Bestätigung |
| Verlauf | `HistoryRoute(memberId, name)` | alle | die letzten 50 erledigten Karten, neueste zuerst |
| „Heute“-Karte | `TodayTasksRoute` | alle | Kind: „Deine Aufgaben“ (Anzahl offen, bis zu 3 Titel, Zahl zurückgewiesener); Eltern: „n warten auf Abnahme“ |

### Aktionen je Rolle und Status (`cardActions`, REQ-TASK-022–024)

| Betrachter | Karte | Aktionen | Anmerkung |
|---|---|---|---|
| Kind | eigene `TODO` | Erledigt | → `REVIEW` |
| Kind | eigene `REVIEW` | Zurückziehen | → `TODO` |
| Kind | fremde Karte, `DONE` | – | nur lesen |
| Eltern | `REVIEW` | Abnehmen · Zurückweisen (Dialog, Begründung optional) · Entfernen (Menü) | Entfernen mit Bestätigung |
| Eltern | `TODO` | Direkt abnehmen · Entfernen (beide im Menü, mit Bestätigung) | |
| Eltern | `DONE` | – | |
| alle | offline | – | Aktionen nur im Heimnetz, keine Warteschlange |

`409`/`404` (jemand war schneller) zeigt die Servermeldung und lädt das Board neu. Zurückweisen sendet immer einen Body (`{}` ohne Grund):
Retrofit lehnt einen `null`-Body ab.

### Lokaler Stand

`BoardCache` (DataStore-Datei `task_board.json`, Format getrennt von der Domäne, `version` 1) hält das letzte Board **eines** Mitglieds; ein
Stand eines anderen Mitglieds, eine kaputte Datei oder eine unbekannte Version gelten als leer. Er wird beim Laden, nach jeder eigenen
Aktion und vom Hintergrund-Abgleich geschrieben. Das Board zeigt bei `Network`/`Certificate`/`Server` den Stand mit
„Offline – Stand vom …“; Badge und „Heute“ lesen ebenfalls daraus.

### Hinweise (REQ-TASK-030, `taskNotices`)

Eigener Abgleich `SyncTaskNoticesUseCase` (WorkManager alle 30 min und beim App-Start, Mutex gegen Doppelmeldungen), Kanal „Aufgaben“,
Benachrichtigungs-IDs ab 100 000. Tippen öffnet „Aufgaben“ (`TaskOpenRequests`). Der erste Abgleich ohne Stand legt nur die Basislinie an.
Wer das Board geöffnet hat, hat die Änderung gesehen und bekommt dazu keinen Hinweis mehr (der Stand wird beim Laden mitgeführt).

| Empfänger | Vorher → jetzt | Hinweis |
|---|---|---|
| Eltern | Karte nicht `REVIEW` oder neu → `REVIEW` | „Lea hat „Bad putzen“ erledigt“ |
| Kind (nur eigene Spur) | Karte neu, `TODO` | „Neue Aufgabe: …“ |
| Kind | `TODO`/`REVIEW` → `DONE` | „„…“ abgenommen“ mit „+n Punkte“ |
| Kind | `REVIEW` → `TODO` | „„…“ zurückgewiesen“ mit Grund |
| beide | Karte verschwunden, eigene Aktionen | kein Hinweis |

### Badge

`ReviewBadge.count` = Zahl der Karten in `REVIEW` aus dem lokalen Stand, nur für Eltern und nur, wenn der Stand dem angemeldeten Mitglied
gehört; angezeigt an „Aufgaben“ in der Leiste (REQ-TASK-003).

### Unit-Testfälle (App)

`BoardRulesTest`, `CatalogValidationTest`, `NoticeRulesTest`, `UseCasesTest`, `SyncTaskNoticesUseCaseTest` (Regeln, Cache-Rückfall,
Basislinie, 401), `BoardViewModelTest`, `FlowViewModelsTest` (Zuweisen, Katalog, Verlauf), `ReviewBadgeTest`, `TodayTasksTest`;
Integration: `RemoteTaskRepositoryTest`, `RemoteCatalogRepositoryTest` (MockWebServer), `DataStoreBoardCacheTest`, `AndroidTaskNotifierTest`,
`BoardScreenTest`, `TaskScreensTest`, `TasksModuleTest`; App: `AppNavHostTest`.
