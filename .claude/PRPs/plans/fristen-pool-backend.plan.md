# Plan: Fristen & Pool – Backend/API (TASK Phase 4)

## Summary
Karten bekommen eine optionale Frist (`dueAt`), die Eltern beim Zuweisen setzen und später ändern oder entfernen; der
Server liefert zusätzlich `reportedLate` und `selfAssigned`. Katalogaufgaben bekommen den Schalter `childSelectable`;
Kinder lesen den Pool, nehmen daraus eine Karte (Todo) oder melden sie sofort (Review) und dürfen selbst genommene
Todo-Karten zurücklegen. Alles nur additiv in `api/openapi.yaml`, damit die heutige Familien-App weiterläuft.

## User Story
Als Elternteil möchte ich Aufgaben mit Frist verteilen und festlegen, welche Aufgaben sich Kinder selbst nehmen dürfen;
als Kind möchte ich mir solche Aufgaben nehmen oder sofort als erledigt melden — damit Termine klar sind und spontan
Erledigtes Punkte bringt.

## Problem → Solution
Karten haben keine Frist; nur Eltern erzeugen Karten → Karten mit optionaler Frist + Verspätungs-Kennzeichen; ein
Pool freigegebener Katalogaufgaben, aus dem Kinder Karten für sich erzeugen.

## Metadata
- **Complexity**: Large (≈ 20 Dateien, Domäne + Persistenz + API + Tests + Doku)
- **Source PRD**: `docs/01-requirements/tasks.prd.md`
- **PRD Phase**: 4 – Fristen & Pool Backend/API (REQ-TASK-011, 026, 027, 028)
- **Estimated Files**: ~20

---

## UX Design
N/A — Backend/API. Die App-Seite (Screens, Überfällig-Markierung, Erinnerung REQ-TASK-031) folgt in Phase 5.

### Interaction Changes (API)
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `POST /api/v1/tasks/cards` | `{memberId, catalogTaskIds}` | + optional `dueAt` (date-time, nullable) für alle Karten des Aufrufs | Eltern |
| `PUT /api/v1/tasks/cards/{cardId}/due-date` | – | NEU: Body `{dueAt: date-time \| null}` → 200 `TaskCardResponse` | Eltern; DONE → 409 |
| `GET /api/v1/tasks/pool` | – | NEU: freigegebene, aktive Katalogaufgaben → `CatalogTaskListResponse` | alle Mitglieder |
| `POST /api/v1/tasks/pool/{catalogTaskId}/cards` | – | NEU: Body `{alreadyDone: boolean}` → 201 `TaskCardResponse` | nur Kind, für sich selbst |
| `DELETE /api/v1/tasks/cards/{cardId}` | nur Eltern | Eltern wie bisher; **Kind** darf eigene `selfAssigned`-Karte in `TODO` entfernen | sonst 403 |
| `CatalogTask` | id, title, emoji, points, archived | + `childSelectable` | optional im Schema, Server sendet immer |
| `CatalogTaskInput` | title, emoji, points | + optional `childSelectable` (nullable) | create: null = false; update: null = unverändert |
| `TaskCard` | … | + `dueAt?`, `reportedLate`, `selfAssigned` | optional im Schema, Server sendet immer |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardService.kt` | all | Service, in den Frist/Pool/Zurücklegen kommen |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardRules.kt` | all | `newCard`, `availableTask`, Fehler-Mapping |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCard.kt` | all | Domänenmodell + Konstanten |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt` | all | bedingte UPDATE/DELETE-Muster |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt` | all | Routen, Rollen-Checks |
| P0 | `api/openapi.yaml` | 695–935, 2169–2310 | Tasks-Pfade und -Schemas |
| P1 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/*.kt` | all | Katalog-Domäne, Validierung |
| P1 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedCatalogRepository.kt`, `TasksTables.kt` | all | Tabellen/Mapping |
| P1 | `backend/tasks/src/test/kotlin/de/familyplanner/tasks/fakes/*.kt` | all | Fakes müssen mitwachsen |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/TasksBoardApiTest.kt` | 1–135 | API-Testmuster, `call(...)` mit Vertragsprüfung |
| P2 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RequestParsing.kt` | 15–45 | Parse-Fehler → `AppException.Validation` |
| P2 | `backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql` | all | `ALTER TABLE … ADD COLUMN` mit Bool-CHECK |
| P2 | `docs/03-design/tasks.md`, `docs/04-verification/traceability.md` | all | Doku, die aktualisiert wird |

## External Documentation
Keine externe Recherche nötig — nur bestehende interne Muster (Ktor, Exposed v1, Flyway, kotlinx.serialization).

---

## Patterns to Mirror

### NAMING / KONSTANTEN
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCard.kt:9-13
const val DONE_VISIBLE_DAYS = 7L
const val ASSIGN_MAX = 20
const val REASON_MAX = 200
```

### ERROR_HANDLING (Outcome → HTTP)
```kotlin
// SOURCE: BoardService.kt:83-89
when (cards.remove(id)) {
    TransitionOutcome.CHANGED -> log.info("Task card removed: {}", id.value)
    TransitionOutcome.NOT_FOUND -> throw AppException.NotFound(CARD_NOT_FOUND)
    TransitionOutcome.WRONG_STATUS -> throw AppException.Conflict("Erledigte Aufgaben können nicht entfernt werden")
}
```
Fehlertexte deutsch, an den Nutzer gerichtet. `AppException.Validation` → 400, `Forbidden` → 403, `NotFound` → 404, `Conflict` → 409.

### PARSE_ERRORS
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/RequestParsing.kt:20-29
try { LocalDate.parse(value) } catch (e: DateTimeParseException) {
    throw AppException.Validation("Ungültiges Datum '$field'").also { it.initCause(e) }
}
```

### LOGGING
```kotlin
// SOURCE: BoardService.kt:43, 100
log.info("Task cards assigned: {} to {}", created.size, memberId.value)
log.info("Task card {}: {}", transition, id.value)
```
Nur IDs loggen, keine Titel/Begründungen.

### REPOSITORY (bedingtes Update, atomar)
```kotlin
// SOURCE: ExposedTaskCardRepository.kt:84-108
val changed = TaskCardsTable.update({ (TaskCardsTable.id eq id.value) and (TaskCardsTable.status inList ...) }) { ... }
if (changed == 0) return@dbQuery if (row(id) != null) TransitionOutcome.WRONG_STATUS else TransitionOutcome.NOT_FOUND
```

### ROUTES
```kotlin
// SOURCE: TaskRoutes.kt:92-96, 100-102
delete("/api/v1/tasks/cards/{cardId}") { call.requireParent(); board.remove(call.cardId()); call.respond(HttpStatusCode.NoContent) }
post("/api/v1/tasks/cards/{cardId}/report") { call.respondCard(board.report(call.principalOrThrow().toActor(), call.cardId())) }
```

### TEST_STRUCTURE (Unit, Fakes)
```kotlin
// SOURCE: backend/tasks/src/test/.../board/BoardServiceTest.kt:16-38
class BoardServiceTest {
    private val fixture = TasksFixture()
    private val board = fixture.board
    @Test
    fun `REQ-TASK-020 assigning copies title, emoji and points`() = runTest { ... }
}
```
`TasksFixture` hat `MutableClock` mit Start `2026-10-24T10:00:00Z` (`fixture.clock`), `fixture.child()`, `fixture.parent()`, `fixture.catalogTask(...)`.

### TEST_STRUCTURE (API, Vertragsprüfung)
```kotlin
// SOURCE: backend/app/src/test/.../TasksBoardApiTest.kt:26-30, 120-128
private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }   // FIXED_NOW = 2026-10-14T10:00:00Z
client.call(HttpMethod.Post, "$TASKS/cards", family.parentToken, body, invalidRequestExpected = true)
```
`call(...)` prüft Request **und** Response gegen `openapi.yaml`; bei absichtlich ungültigem Request `invalidRequestExpected = true`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | neue Felder, Pfade `due-date`, `pool`, Schemas `SetDueDateRequest`, `TakePoolTaskRequest`; DELETE-Card-Beschreibung |
| `backend/tasks/src/main/resources/db/migration/V14__tasks_due_dates_pool.sql` | CREATE | Spalten `task_catalog.child_selectable`, `task_cards.due_at`, `task_cards.self_assigned` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/TasksTables.kt` | UPDATE | neue Spalten |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogTask.kt` | UPDATE | `childSelectable` in Task und Input |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogRepository.kt` | UPDATE | `findSelectable()` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogService.kt` | UPDATE | create/update mit Freigabe, `pool()` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedCatalogRepository.kt` | UPDATE | Spalte schreiben/lesen, `findSelectable` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCard.kt` | UPDATE | `dueAt`, `selfAssigned`, `reportedLate`, Konstanten |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/DueDates.kt` | CREATE | reine Regeln: `validatedDueAt`, `isReportedLate` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardRules.kt` | UPDATE | `newCard` mit `dueAt`/`selfAssigned`/Status |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCardRepository.kt` | UPDATE | `setDueAt`, `removeOwnSelfAssigned` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardService.kt` | UPDATE | `assign(…, dueAt)`, `setDueAt`, `remove(actor, id)` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/PoolService.kt` | CREATE | `take(actor, catalogTaskId, alreadyDone)` (hält `BoardService` klein) |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt` | UPDATE | Spalten, `setDueAt`, `removeOwnSelfAssigned` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskApiMappers.kt` | UPDATE | neue Felder |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt` | UPDATE | neue Routen, DELETE für Kinder, `dueAt` beim Zuweisen |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/DueDateParsing.kt` | CREATE | `parseDueAt(String?)` → `Instant?` |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/TasksModule.kt` | UPDATE | `PoolService` registrieren |
| `backend/app/.../TaskRoutes`-Aufruf (wo `taskRoutes(get(), get())` steht) | UPDATE | `PoolService` übergeben — Stelle per `grep -rn "taskRoutes(" backend/app/src/main` finden |
| `backend/tasks/src/test/.../fakes/InMemoryTaskRepositories.kt`, `TasksFixture.kt` | UPDATE | neue Methoden/Felder, `pool`-Service |
| `backend/tasks/src/test/.../board/DueDatesTest.kt` | CREATE | Regeln Frist/Verspätung |
| `backend/tasks/src/test/.../board/BoardServiceTest.kt` | UPDATE | Frist beim Zuweisen, ändern, Zurücklegen |
| `backend/tasks/src/test/.../board/PoolServiceTest.kt` | CREATE | Pool-Regeln |
| `backend/tasks/src/test/.../catalog/CatalogServiceTest.kt` | UPDATE | Freigabe |
| `backend/tasks/src/test/.../persistence/Exposed*RepositoryTest.kt` | UPDATE | neue Spalten, bedingte Updates |
| `backend/app/src/test/.../TasksBoardApiTest.kt`, `TasksCatalogApiTest.kt` | UPDATE | Frist-Endpunkte, Freigabe |
| `backend/app/src/test/.../TasksPoolApiTest.kt` | CREATE | Pool-Endpunkte |
| `docs/03-design/tasks.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/tasks.prd.md` | UPDATE | Design, Nachweise, Phasenstatus |

## NOT Building

- Keine App-Änderungen (Phase 5) — Android muss aber weiter bauen (generierte Modelle, siehe Task 1 GOTCHA).
- Keine Erinnerung/Benachrichtigung (REQ-TASK-031, App-seitig nach ADR-0013).
- Kein serverseitiges Feld „overdue“: Überfällig berechnet die App aus `dueAt` + Geräteuhr (REQ-TASK-027, offline).
- Keine geänderte Sortierung der Lane im Backend (`sortedForLane` bleibt); „überfällig oben“ sortiert die App.
- Kein Punktabzug, kein Limit, wie oft ein Kind dieselbe Pool-Aufgabe nimmt.
- Keine Frist für selbst genommene Karten (`dueAt` immer null; `due-date` darauf ist trotzdem erlaubt für Eltern — siehe Notes).

---

## Fachregeln (Referenz für alle Tasks)

- `dueAt` ist ein Zeitpunkt (`Instant`), im API als RFC-3339 `date-time` mit Offset (`2026-10-31T18:00:00+01:00` oder `…Z`).
- Beim **Setzen** (Zuweisen oder `due-date`) muss `dueAt` **nach `clock.instant()`** liegen und höchstens
  `DUE_DATE_MAX_DAYS = 365` Tage in der Zukunft — sonst `400 "Die Frist muss in der Zukunft liegen"` bzw.
  `"Die Frist darf höchstens 365 Tage in der Zukunft liegen"`. `null` = keine Frist / Frist entfernen.
- Frist ändern/entfernen: nur Eltern, Karte in `TODO` oder `REVIEW`; `DONE` → `409 "Erledigte Aufgaben können nicht mehr geändert werden"`.
- `reportedLate` (abgeleitet, **nicht gespeichert**): `dueAt != null && (reportedAt ?: doneAt) != null && (reportedAt ?: doneAt) > dueAt`.
  Direkt abgenommene TODO-Karten (ohne `reportedAt`) zählen also mit `doneAt`. Nach Zurückweisen ist `reportedAt` null → `false`.
- `selfAssigned` (gespeichert): `true` nur für Karten aus dem Pool.
- Pool lesen (`GET /pool`): alle angemeldeten Mitglieder; Inhalt = nicht archiviert **und** `childSelectable`, nach Titel sortiert.
- Pool nehmen: nur Rolle `CHILD` (Eltern → 403 `"Nur Kinder können Aufgaben aus dem Pool nehmen"`), Karte immer für den Aufrufer.
  Unbekannte Katalog-ID → 404 `"Aufgabe nicht gefunden"`; archiviert → 409 `"Die Aufgabe ist archiviert"`;
  nicht freigegeben → 403 `"Diese Aufgabe ist nicht frei wählbar"`.
  `alreadyDone = false` → `TODO`; `true` → `REVIEW` mit `reportedAt = now`. Titel/Emoji/Punkte kopiert, `dueAt = null`.
- Zurücklegen (`DELETE /cards/{id}` als Kind): nur eigene Karte mit `selfAssigned = true` in `TODO`.
  Sonst: fremde/zugewiesene Karte → 403 `"Nur selbst genommene Aufgaben können zurückgelegt werden"`;
  eigene selbst genommene Karte in `REVIEW`/`DONE` → 409 `"Nur offene Aufgaben können zurückgelegt werden"`; unbekannt → 404.
  Eltern verhalten sich wie bisher (alles außer `DONE`).
- `childSelectable` am Katalog: neu = `false` wenn nicht angegeben; `PUT` mit `childSelectable = null`/fehlend lässt den
  gespeicherten Wert **unverändert** (die heutige Familien-App sendet das Feld nicht und darf die Freigabe nicht löschen).

---

## Step-by-Step Tasks

### Task 1: OpenAPI erweitern (contract-first, ADR-0004)
- **ACTION**: `api/openapi.yaml` additiv ändern (Skill `contract-first` beachten).
- **IMPLEMENT**:
  - `CatalogTask.properties.childSelectable: { type: boolean, default: false }` — **nicht** in `required`.
  - `CatalogTaskInput.properties.childSelectable: { type: boolean, nullable: true, description: "Neu: fehlt = false. Ändern: fehlt = unverändert." }`.
  - `TaskCard.properties`: `dueAt: { type: string, format: date-time, nullable: true }`, `reportedLate: { type: boolean, default: false }`, `selfAssigned: { type: boolean, default: false }` — nicht required.
  - `AssignTasksRequest.properties.dueAt: { type: string, format: date-time, nullable: true }`.
  - Schema `SetDueDateRequest`: `type: object, required: [dueAt], properties: { dueAt: { type: string, format: date-time, nullable: true } }`.
  - Schema `TakePoolTaskRequest`: `type: object, required: [alreadyDone], properties: { alreadyDone: { type: boolean } }`.
  - Pfad `/api/v1/tasks/cards/{cardId}/due-date` `put` (`operationId: setTaskDueDate`, tags `[tasks]`, Parameter `CardId`, Body `SetDueDateRequest`, 200 `TaskCardResponse`, 400/401/403/404/409/429 wie `rejection`).
  - Pfad `/api/v1/tasks/pool` `get` (`operationId: getTaskPool`, 200 `CatalogTaskListResponse`, 401/429).
  - Pfad `/api/v1/tasks/pool/{catalogTaskId}/cards` `post` (`operationId: takePoolTask`, Parameter `CatalogTaskId`, Body `TakePoolTaskRequest`, 201 `TaskCardResponse`, 400/401/403/404/409/429).
  - `DELETE /api/v1/tasks/cards/{cardId}`: `summary` → „Karte entfernen (Eltern: nicht erledigte; Kind: eigene selbst genommene in TODO)“.
- **MIRROR**: Aufbau der bestehenden Tasks-Pfade (Zeilen 830–935).
- **GOTCHA**: Android generiert dieselben Modelle (`android/core/api`). Neue Response-Felder deshalb **nicht required**, sonst
  scheitern Android-Tests, die JSON ohne diese Felder dekodieren bzw. `CatalogTask(...)`-Konstruktoren ohne neue Parameter.
  Nach der Änderung `cd android && ./gradlew :feature:tasks:testDebugUnitTest` laufen lassen.
  Der Server sendet die Felder trotzdem immer (Mapper setzt sie explizit).
- **VALIDATE**: `cd backend && ./gradlew :api-contract:build` grün; generierte Klassen haben `childSelectable: Boolean? = false` usw.

### Task 2: Migration und Tabellen
- **ACTION**: `V14__tasks_due_dates_pool.sql` anlegen, `TasksTables.kt` erweitern.
- **IMPLEMENT**:
  ```sql
  -- TASK phase 4: optional due date per card, cards children took from the pool, pool release per catalog task.
  ALTER TABLE task_catalog ADD COLUMN child_selectable INTEGER NOT NULL DEFAULT 0 CHECK (child_selectable IN (0,1));
  ALTER TABLE task_cards ADD COLUMN due_at INTEGER;
  ALTER TABLE task_cards ADD COLUMN self_assigned INTEGER NOT NULL DEFAULT 0 CHECK (self_assigned IN (0,1));
  ```
  `TaskCatalogTable.childSelectable = bool("child_selectable")`, `TaskCardsTable.dueAt = long("due_at").nullable()`,
  `TaskCardsTable.selfAssigned = bool("self_assigned")`.
- **MIRROR**: `V4__meals_submissions.sql` (Bool als INTEGER mit CHECK), `MealsTables.kt` (`bool(...)`).
- **GOTCHA**: Höchste bestehende Version ist `V13` (gamification) — Flyway-Versionen sind global über alle Module.
- **VALIDATE**: `ExposedTaskCardRepositoryTest` startet mit `DatabaseFactory.create(...)` → Migration läuft.

### Task 3: Katalog-Domäne mit Freigabe (REQ-TASK-011)
- **ACTION**: `CatalogTask`, `CatalogTaskInput`, `CatalogRepository`, `CatalogService`, `ExposedCatalogRepository` erweitern.
- **IMPLEMENT**:
  - `CatalogTask(..., archived: Boolean, childSelectable: Boolean = false)`; `CatalogTaskInput(title, emoji, points, childSelectable: Boolean? = null)`.
    `validated(...)` reicht `childSelectable` durch.
  - `CatalogRepository.findSelectable(): List<CatalogTask>` — aktiv und freigegeben, nach Titel (case-insensitive).
  - `CatalogService.create`: `childSelectable = input.childSelectable ?: false`.
    `CatalogService.update`: `childSelectable = input.childSelectable ?: existing.childSelectable`.
    `CatalogService.pool() = catalog.findSelectable()`.
  - Exposed: `create`/`update` schreiben `childSelectable`, `toTask()` liest es; `findSelectable` mit
    `archivedAt.isNull() and (childSelectable eq true)`.
- **MIRROR**: `findAll` (Sortierung in Kotlin), `toTask` (CatalogService.kt:42-45).
- **GOTCHA**: Default-Parameter halten bestehende Aufrufer (`CatalogTaskInput(title, emoji, points)` in Routen/Tests) kompilierbar.
- **VALIDATE**: `CatalogServiceTest`: `REQ-TASK-011 new task is not selectable`, `… update without flag keeps release`,
  `… archived task leaves the pool`; `ExposedCatalogRepositoryTest`: Roundtrip + `findSelectable`.

### Task 4: Frist-Regeln als reine Funktionen (REQ-TASK-026/027)
- **ACTION**: `board/DueDates.kt` anlegen; Konstante `DUE_DATE_MAX_DAYS = 365L` in `TaskCard.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** A due date must lie after [now] and at most [DUE_DATE_MAX_DAYS] ahead (REQ-TASK-026). */
  internal fun validatedDueAt(dueAt: Instant?, now: Instant): Instant? { ... AppException.Validation(...) }

  /** Reported (or, without a report, accepted) after the due date (REQ-TASK-027). */
  fun TaskCard.isReportedLate(): Boolean { val finishedAt = reportedAt ?: doneAt; return dueAt != null && finishedAt != null && finishedAt.isAfter(dueAt) }
  ```
  Genau gleich = nicht verspätet.
- **VALIDATE**: `DueDatesTest` (`REQ-TASK-026 …`/`REQ-TASK-027 …`): Vergangenheit, genau jetzt, +365 d ok, +366 d abgelehnt, null;
  verspätet gemeldet, pünktlich, direkt abgenommen nach Frist, ohne Frist.

### Task 5: Karten-Domäne und Repository
- **ACTION**: `TaskCard` um `dueAt: Instant?` und `selfAssigned: Boolean` erweitern; `newCard` anpassen; Repository-Interface + Exposed + Fake.
- **IMPLEMENT**:
  - `TaskCard(..., doneAt: Instant?, dueAt: Instant? = null, selfAssigned: Boolean = false)`.
  - `newCard(id, memberId, task, now, dueAt: Instant? = null, selfAssigned: Boolean = false, alreadyDone: Boolean = false)`
    → Status `REVIEW` + `reportedAt = now` wenn `alreadyDone`, sonst `TODO`.
  - `TaskCardRepository`:
    ```kotlin
    /** Sets or clears the due date unless the card is DONE ([TransitionOutcome.WRONG_STATUS]). */
    suspend fun setDueAt(id: TaskCardId, dueAt: Instant?): TransitionOutcome
    /** Deletes the card only if it belongs to [memberId], is self-assigned and still TODO. */
    suspend fun removeOwnSelfAssigned(id: TaskCardId, memberId: MemberId): TransitionOutcome
    ```
  - Exposed: `createAll` schreibt `dueAt`/`selfAssigned`, `toCard()` liest sie. `setDueAt` = `update({ id eq … and status neq DONE })`
    + Outcome-Muster. `removeOwnSelfAssigned` = `deleteWhere { id eq … and memberId eq … and selfAssigned eq true and status eq TODO }`;
    bei 0 Zeilen: Karte fehlt → `NOT_FOUND`, sonst `WRONG_STATUS` (Service unterscheidet 403/409 vorher anhand der gelesenen Karte).
  - `InMemoryTaskCardRepository` analog.
- **MIRROR**: `transition`/`remove` in `ExposedTaskCardRepository.kt:77-119`.
- **GOTCHA**: `TaskTransition`-Logik (`REPORT`/`WITHDRAW`/`REJECT`) bleibt unverändert — `dueAt` wird von Übergängen nie angefasst.
- **VALIDATE**: `ExposedTaskCardRepositoryTest`: Roundtrip `dueAt`/`selfAssigned`; `setDueAt` auf DONE → WRONG_STATUS;
  `removeOwnSelfAssigned` für fremde/zugewiesene/REVIEW-Karte löscht nicht.

### Task 6: BoardService – Frist beim Zuweisen, ändern, Zurücklegen
- **ACTION**: `BoardService` erweitern; `remove` bekommt den Actor.
- **IMPLEMENT**:
  - `assign(memberId, catalogTaskIds, dueAt: Instant? = null)`: `val due = validatedDueAt(dueAt, now)`; an `newCard` durchreichen.
  - `setDueAt(id, dueAt)`: `val due = validatedDueAt(dueAt, clock.instant())`; `cards.setDueAt` → Outcome-Mapping
    (`WRONG_STATUS` → `Conflict("Erledigte Aufgaben können nicht mehr geändert werden")`); Log `"Task card due date set: {}"`; gibt `requireCard(id)` zurück.
    Rollenprüfung (nur Eltern) in der Route via `requireParent()` wie bei `remove`.
  - `remove(actor: Actor, id)`: Eltern → bisheriger Weg. Kind → `requireCard(id)`; wenn `card.memberId != actor.id || !card.selfAssigned`
    → `Forbidden("Nur selbst genommene Aufgaben können zurückgelegt werden")`; sonst `cards.removeOwnSelfAssigned(id, actor.id)`
    → `WRONG_STATUS` → `Conflict("Nur offene Aufgaben können zurückgelegt werden")`.
  - Funktionen < 50 Zeilen halten; falls `BoardService` > ~200 Zeilen wächst, Kind-Zweig in eine private Funktion.
- **VALIDATE**: `BoardServiceTest`: `REQ-TASK-026 assigning sets the due date on every card`, `… due date in the past is rejected`,
  `… parent changes and clears the due date`, `… due date of a done card cannot change`;
  `REQ-TASK-027 report after the due date is flagged late` (Frist setzen, `fixture.clock` über die Frist vorstellen, melden → `isReportedLate()`);
  `REQ-TASK-028 child puts back own self-assigned todo card`, `… child cannot remove assigned card`, `… reported self-assigned card cannot be put back`;
  bestehende `REQ-TASK-024`-Tests auf `remove(parent, id)` umstellen.

### Task 7: PoolService (REQ-TASK-028)
- **ACTION**: `board/PoolService.kt` anlegen, in `TasksModule` registrieren.
- **IMPLEMENT**:
  ```kotlin
  class PoolService(
      private val catalog: CatalogRepository,
      private val cards: TaskCardRepository,
      private val clock: Clock,
      private val newId: () -> String = { UUID.randomUUID().toString() },
  ) {
      suspend fun take(actor: Actor, catalogTaskId: CatalogTaskId, alreadyDone: Boolean): TaskCard
  }
  ```
  Ablauf: Eltern → Forbidden; `catalog.findById` → null = NotFound("Aufgabe nicht gefunden"); `archived` → Conflict("Die Aufgabe ist archiviert");
  `!childSelectable` → Forbidden("Diese Aufgabe ist nicht frei wählbar"); `newCard(..., selfAssigned = true, alreadyDone = alreadyDone)`;
  `cards.createAll(listOf(card))`; Log `"Pool task taken: {} by {}"` (Karten-ID, Member-ID).
  Kein Aufruf von `notifySubscribers` (kein Ereignis; Eltern-Hinweis entsteht im App-Abgleich, REQ-TASK-030).
- **MIRROR**: `BoardService.assign`, `TasksModule.kt` (`single { PoolService(get(), get(), clock) }`).
- **VALIDATE**: `PoolServiceTest`: nimmt → TODO + selfAssigned + kopierte Werte + `dueAt` null; `alreadyDone` → REVIEW mit `reportedAt`;
  Elternteil 403; nicht freigegeben 403; archiviert 409; unbekannt 404.

### Task 8: Routen und Mapper
- **ACTION**: `TaskRoutes.kt`, `TaskApiMappers.kt`, neue `DueDateParsing.kt`; Aufrufstelle von `taskRoutes(...)` in `backend/app` anpassen.
- **IMPLEMENT**:
  - `parseDueAt(value: String?): Instant?` mit `OffsetDateTime.parse(value).toInstant()`; `DateTimeParseException` → `Validation("Ungültige Frist")` mit `initCause`.
  - `POST /cards`: `board.assign(MemberId(request.memberId), ids, parseDueAt(request.dueAt))`.
  - `PUT /cards/{cardId}/due-date`: `requireParent()`; `receive<SetDueDateRequest>()`; `respondCard(board.setDueAt(cardId, parseDueAt(body.dueAt)))`.
  - `DELETE /cards/{cardId}`: statt `requireParent()` → `board.remove(call.principalOrThrow().toActor(), call.cardId())`.
  - Neue `poolRoutes(catalog, pool)`: `GET /api/v1/tasks/pool` (`principalOrThrow()`, `catalog.pool()`),
    `POST /api/v1/tasks/pool/{catalogTaskId}/cards` (`uuidParameter("catalogTaskId")`, `receive<TakePoolTaskRequest>()`, 201 `TaskCardResponse`).
  - `ApiCatalogTaskInput.toInput()` reicht `childSelectable` durch.
  - Mapper: `CatalogTask.toApi()` + `childSelectable`; `TaskCard.toApi()` + `dueAt = dueAt?.toString()`, `reportedLate = isReportedLate()`, `selfAssigned`.
- **GOTCHA**: `taskRoutes`-Signatur ändert sich → Aufrufstelle in `backend/app` (per grep finden) mitziehen. Generierte Felder sind
  nullable (`Boolean?`) — beim Lesen `?: false`/`?: null` behandeln.
- **VALIDATE**: `./gradlew :tasks:build :app:test`.

### Task 9: API-Tests mit Vertragsprüfung
- **ACTION**: `TasksBoardApiTest`, `TasksCatalogApiTest` ergänzen; `TasksPoolApiTest` anlegen (gleicher Aufbau, `FIXED_NOW = 2026-10-14T10:00:00Z` → Fristen z. B. `2026-10-18T18:00:00+02:00`).
- **IMPLEMENT** (Testnamen):
  - `REQ-TASK-026 parent assigns with a due date and changes it` (inkl. `null` entfernen, Antwort enthält `dueAt`)
  - `REQ-TASK-026 due date in the past gets 400, child gets 403, done card gets 409, malformed date gets 400`
  - `REQ-TASK-027 timely report is not flagged late` (Antwort enthält `reportedLate = false`). Den verspäteten Fall deckt
    `BoardServiceTest` mit `fixture.clock` (MutableClock) ab — der API-Test läuft mit festem `FIXED_NOW` und kann die Frist nicht überschreiten.
  - `REQ-TASK-011 parent releases a task for the pool, update without flag keeps it`
  - `REQ-TASK-028 child takes a pool task and reports it at once`
  - `REQ-TASK-028 parent cannot take, unreleased gets 403, archived gets 409, unknown gets 404`
  - `REQ-TASK-028 child puts back own taken card but not an assigned one`
- **GOTCHA**: absichtlich ungültige Bodies (fehlendes `alreadyDone`, kaputtes Datum, das schon das Schema verletzt) mit `invalidRequestExpected = true`.
- **VALIDATE**: `cd backend && ./gradlew :app:test --tests 'de.familyplanner.app.Tasks*'`.

### Task 10: Doku und Nachverfolgung
- **ACTION**: Doku aktualisieren.
- **IMPLEMENT**:
  - `docs/03-design/tasks.md`: „Abgedeckte Anforderungen“ + REQ-TASK-011/026/027/028; Domänentabelle (`childSelectable`, `dueAt`, `selfAssigned`,
    `reportedLate` abgeleitet); Statusmaschine: Zeile „Pool nehmen“ und „Zurücklegen (Kind)“; Schnittstellen; DB (`V14`); Eingabegrenzen (Frist ≤ 365 Tage).
  - `docs/04-verification/traceability.md`: Zeilen für REQ-TASK-011, 026, 027, 028 (Status „umgesetzt (Backend)“), REQ-TASK-031 „offen (Phase 5)“.
  - `docs/01-requirements/tasks.prd.md`: Phase 4 → `complete` mit Bericht-Link (macht `/prp-implement`).
- **VALIDATE**: Links prüfen; keine REQ-ID ohne Test.

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `validatedDueAt` | now − 1 min | 400 | ja |
| `validatedDueAt` | now exakt | 400 | ja |
| `validatedDueAt` | now + 365 d / + 366 d | ok / 400 | ja |
| `isReportedLate` | reportedAt = dueAt | false | ja |
| `isReportedLate` | reportedAt null, doneAt > dueAt | true | ja |
| Katalog update ohne Flag | freigegebene Aufgabe | bleibt freigegeben | ja |
| Pool nehmen | `alreadyDone = true` | REVIEW, `reportedAt = now` | nein |
| Zurücklegen | eigene, selbst genommen, REVIEW | 409 | ja |
| Frist ändern | DONE-Karte | 409 | ja |

### Edge Cases Checklist
- [x] Leere/fehlende Felder (`dueAt` fehlt beim Zuweisen = keine Frist; `childSelectable` fehlt beim PUT = unverändert)
- [x] Ungültige Typen (kaputtes Datum → 400)
- [x] Parallelzugriff (Zurücklegen nutzt bedingtes DELETE; Frist-Update bedingt auf `status != DONE`)
- [x] Berechtigungen (Kind/Eltern je Endpunkt)
- [x] Alte App: PUT ohne `childSelectable`, Responses mit zusätzlichen Feldern (Android `ignoreUnknownKeys = true`)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Befunde

### Unit + Integration Tests (Modul)
```bash
cd backend && ./gradlew :tasks:test
```

### API-Tests
```bash
cd backend && ./gradlew :app:test --tests 'de.familyplanner.app.Tasks*'
```

### Full Suite
```bash
cd backend && ./gradlew build
```
```bash
cd android && ./gradlew :feature:tasks:testDebugUnitTest
```
EXPECT: alles grün; Android kompiliert mit den neu generierten Modellen.

### Manual Validation
- [ ] Testinstanz (Port 8097, ADR-0015) nach Deploy: aktuelle Test-App zeigt Board und Katalog unverändert, Bearbeiten einer
      freigegebenen Katalogaufgabe in der alten App lässt die Freigabe bestehen (per `GET /pool` prüfen).

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validierungsbefehle grün
- [ ] REQ-TASK-011, 026, 027, 028 haben Unit- und API-Tests mit REQ-ID im Namen
- [ ] OpenAPI-Änderung rein additiv; Android-Build grün
- [ ] Design-Doku und Traceability aktualisiert

## Completion Checklist
- [ ] Muster aus BoardService/Exposed-Repositories übernommen (Outcome-Enum, bedingte Updates)
- [ ] Fehlertexte deutsch, Logs nur mit IDs
- [ ] Konstanten statt Magic Numbers (`DUE_DATE_MAX_DAYS`)
- [ ] Keine Kopplung zu anderen Feature-Modulen
- [ ] Dateien < 400 Zeilen, Funktionen < 50 Zeilen

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Alte Familien-App löscht Freigabe beim Bearbeiten | M | M | `childSelectable = null` → unverändert; API-Test dafür |
| Neue required-Felder brechen Android-Generat | M | H | Response-Felder optional mit Default; Android-Tests in Validierung |
| Zeitzonen-Verwirrung bei `dueAt` | M | M | API nimmt Offset-Zeitstempel, Server speichert UTC-Millis; App (Phase 5) rechnet in Europe/Berlin |
| `DELETE /cards` für Kinder öffnet zu viel | L | H | Bedingtes DELETE mit `member_id`, `self_assigned`, `status = TODO`; Tests für fremde/zugewiesene Karten |
| Pool-Missbrauch (dieselbe Aufgabe oft) | M | L | Bewusst kein Limit (PRD); Eltern weisen zurück/entfernen |

## Notes
- Eltern dürfen per `due-date` auch einer selbst genommenen Karte eine Frist geben; das PRD verbietet es nicht und es schadet nicht.
- `reportedLate` wird abgeleitet statt gespeichert, damit Zurückweisen/Zurückziehen es automatisch korrekt macht.
- Die App (Phase 5) braucht: `GET /pool`, `POST /pool/{id}/cards`, `PUT /cards/{id}/due-date`, `dueAt`/`reportedLate`/`selfAssigned` an der Karte,
  `childSelectable` am Katalog, `DELETE /cards/{id}` für Kinder.
- Umsetzung laut Projektregel in einer **neuen Session** (Sonnet): `/ecc:prp-implement .claude/PRPs/plans/fristen-pool-backend.plan.md`.
