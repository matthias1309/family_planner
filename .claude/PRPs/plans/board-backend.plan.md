# Plan: Aufgaben-Board Backend/API (TASK Phase 2)

## Summary
Neues Backend-Modul `tasks` mit Aufgabenkatalog (die „Magnetschilder“), Karten je Kind und den Statusübergängen
Todo → Abnahme → Done inklusive Rechteprüfung je Rolle. Bei jeder Abnahme schreibt `tasks` in **derselben Transaktion**
ein Domänenereignis *Aufgabe abgenommen* in ein neues Ereignisprotokoll in `core` (Outbox). Ein Dispatcher in `core` stellt
die Ereignisse an Abonnenten zu; in dieser Phase gibt es noch keinen Abonnenten — `gamification` (GAME Phase 1) liest später
**alle** bis dahin aufgelaufenen Abnahmen nach. API zuerst in `api/openapi.yaml`, nur additiv.

## User Story
Als Elternteil möchte ich vorbereitete Aufgaben mit Punktwert pflegen, Kindern zuweisen und erledigte Aufgaben abnehmen,
als Kind meine Aufgaben als erledigt melden — damit das Küchenboard digital wird und jede Abnahme später zuverlässig Punkte
bringt.

## Problem → Solution
Kein Board im Backend, kein Mechanismus für modulübergreifende Ereignisse → Modul `tasks` mit Katalog, Karten, reiner
Übergangslogik und REST-API; `core` bekommt ein dauerhaftes Ereignisprotokoll mit Zustellung je Abonnent (ADR-0016).

## Metadata
- **Complexity**: Large (≈ 35 Dateien, neues Gradle-Modul, neues Architekturmuster)
- **Source PRD**: `docs/01-requirements/tasks.prd.md`
- **PRD Phase**: Phase 2 — Board Backend/API
- **Requirements**: REQ-TASK-010, 020, 021, 022, 023, 024, 025 (Backend-Teil; App folgt in Phase 3)
- **Parallel zu**: TASK Phase 1 (läuft in einer anderen Session, nur `android/` + `deploy/`) — keine gemeinsamen Dateien außer
  `docs/04-verification/traceability.md` und `docs/01-requirements/tasks.prd.md` (andere Zeilen; beim Merge Konflikte von Hand lösen)

---

## Entscheidungen (Planung 2026-10-24)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Ereignis-Zustellung | **Outbox** in `core`: Tabelle `core_domain_events` (fortlaufende `seq`), Cursor je Abonnent in `core_event_cursors`; Ereignis wird in derselben DB-Transaktion wie der Statuswechsel geschrieben | Keine verlorenen Punkte bei Absturz zwischen Statuswechsel und Gutschrift; **neue Abonnenten lesen alle früheren Ereignisse nach** → Abnahmen während des Testbetriebs vor GAME zählen trotzdem |
| Zustellzeitpunkt | Synchron im Request nach dem Commit (`dispatcher.dispatchPending()`), zusätzlich einmal beim Start | Deterministisch testbar, keine Hintergrund-Jobs; Fehler eines Abonnenten scheitern den Request nicht |
| Garantie | Mindestens einmal; Abonnenten müssen idempotent sein (GAME: eindeutiger Schlüssel je Karte) | Cursor wird nach jedem erfolgreich verarbeiteten Ereignis gespeichert; bei Fehler ab diesem Ereignis erneut |
| Ereignistypen | Payload-Klassen in `core.events` (`TaskAccepted`), Codec mit explizitem `when` statt polymorpher Serialisierung | Konsumenten dürfen `tasks` nicht kennen (ADR-0001); explizites Mapping ist einfach und versioniert (`task.accepted.v1`) |
| Karte vs. Katalog | Titel, Emoji, Punkte werden beim Zuweisen **kopiert**; `catalog_task_id` nur als Herkunft (nullable, `ON DELETE SET NULL`) | PRD-Regel (wie Gericht aus Rezept, ADR-0012) |
| Katalog löschen | `DELETE` **archiviert** (`archived_at`); archivierte Aufgaben nicht zuweisbar | PRD REQ-TASK-010 |
| Antwort der Übergänge | `200` mit der geänderten Karte (`TaskCardResponse`) | App kann ohne Neuladen aktualisieren |
| Done-Sichtbarkeit | Board liefert Done-Karten der letzten **7 Tage** (`DONE_VISIBLE_DAYS`), Verlauf je Kind alle Done-Karten, neueste zuerst, `limit` 1–100 (Standard 50) | PRD REQ-TASK-025; Open Question im PRD mit Vorschlag 7 Tage |
| Sichtbarkeit | Board und Verlauf für alle angemeldeten Mitglieder; Katalog nur Eltern | PRD-Regel „alle sehen alle Lanes“, REQ-TASK-010 |
| Zuweisen an | Nur Mitglieder mit Rolle `CHILD`; 1–20 Katalogaufgaben pro Aufruf, Duplikate erlaubt (je eine Karte) | PRD REQ-TASK-020 |
| `Actor` | Eigene kleine `data class Actor` in `tasks.board` (wie `meals.plan.Actor`) | Modulgrenze; Hochziehen nach `core` wäre ein Umbau von `meals` (nicht in diesem Scope) |
| Migrationen | `V7__core_domain_events.sql` (core), `V8__tasks.sql` (tasks) | Globale Nummerierung (ADR-0010); V1–V6 belegt |

---

## UX Design
N/A — reine Backend-/API-Änderung. Die App-Seite folgt in TASK Phase 3.

### Interaction Changes (API)
| Endpoint | Rolle | Wirkung |
|---|---|---|
| `GET /api/v1/tasks/catalog` | Eltern | aktive Katalogaufgaben (`?includeArchived=true` auch archivierte) |
| `POST /api/v1/tasks/catalog` | Eltern | anlegen → `201` |
| `PUT /api/v1/tasks/catalog/{catalogTaskId}` | Eltern | ändern (nicht archivierte) |
| `DELETE /api/v1/tasks/catalog/{catalogTaskId}` | Eltern | archivieren → `204` |
| `POST /api/v1/tasks/cards` | Eltern | `{memberId, catalogTaskIds[]}` → `201` mit neuen Karten |
| `GET /api/v1/tasks/board` | alle | Lanes je Kind mit Todo/Abnahme/Done(7 Tage) |
| `GET /api/v1/tasks/members/{memberId}/history` | alle | Done-Karten des Kindes, neueste zuerst |
| `POST /api/v1/tasks/cards/{cardId}/report` | Kind (eigene) | `TODO → REVIEW` |
| `POST /api/v1/tasks/cards/{cardId}/withdrawal` | Kind (eigene) | `REVIEW → TODO` |
| `POST /api/v1/tasks/cards/{cardId}/acceptance` | Eltern | `REVIEW`/`TODO → DONE` + Ereignis |
| `POST /api/v1/tasks/cards/{cardId}/rejection` | Eltern | `REVIEW → TODO`, optional `{reason}` |
| `DELETE /api/v1/tasks/cards/{cardId}` | Eltern | entfernen (nur `TODO`/`REVIEW`) → `204` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/02-architecture/adr/0010-backend-feature-modules.md` | all | Modul-, Pfad-, Migrations- und Fake-Konventionen |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/DishReviewService.kt` | all | Service mit atomarem Statuswechsel, Outcome → Exception, Logging |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | 1-150 | `dbQuery`, bedingte Updates, `JdbcTransaction`-Helfer |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt` | 1-80 | CRUD-Service mit `RecipeInput`, `newId`, Validierung |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt` | all | Routen, `requireParent`, `uuidParameter`, `toActor` |
| P0 | `backend/core/src/main/kotlin/de/familyplanner/core/persistence/Transactions.kt` | all | `dbQuery` = `withContext(IO) { transaction(db) }` |
| P0 | `api/openapi.yaml` | 1-17, 637-680, 682-760, 1278-1330 | Header/Tags, Endpunkt-Stil, Parameter, Standard-Responses, Envelope-Schemas |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/auth/RoleGuard.kt` | all | `principalOrThrow`, `requireParent`, `uuidParameter` |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt`, `Validation.kt` | all | Fehlercodes, `validatedName` |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/CoreModule.kt` | all | Koin-Verdrahtung `core` |
| P1 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 70-100 | Modul- und Routenregistrierung |
| P1 | `backend/app/build.gradle.kts`, `backend/meals/build.gradle.kts`, `backend/settings.gradle.kts` | all | Neues Modul eintragen, Kover |
| P1 | `backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql`, `V5__meals_recipes.sql` | all | SQL-Stil (CHECK, FK, Kommentare) |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/RecipeTables.kt` | all | Exposed-Tabellen, Längenkonstanten |
| P2 | `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt` | all | `call(...)` mit Vertragsprüfung, `setupFamilyWithChild()` |
| P2 | `backend/app/src/test/kotlin/de/familyplanner/app/MealsSubmissionApiTest.kt` | 1-120 | API-Test-Stil |
| P2 | `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/DishReviewServiceTest.kt` | all | Service-Test-Stil, Fixture |
| P2 | `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepositoryTest.kt` | 1-60 | Repo-Test mit echter SQLite-Datei |
| P2 | `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/PlanFixture.kt` | all | Fixture-Muster |
| P2 | `backend/core/src/test/kotlin/de/familyplanner/core/persistence/DatabaseFactoryTest.kt` | all | Core-DB-Test |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Transactional Outbox | microservices.io/patterns/data/transactional-outbox.html | Ereignis in derselben Transaktion wie die Zustandsänderung schreiben; Zustellung danach, mindestens einmal |
| Exposed transactions | jetbrains.com/help/exposed/transactions.html | Transaktion ist thread-gebunden; innerhalb eines `transaction {}`-Blocks keine weitere `dbQuery` (Thread-Wechsel durch `withContext`) |
| SQLite AUTOINCREMENT | sqlite.org/autoinc.html | `INTEGER PRIMARY KEY AUTOINCREMENT` vergibt nie eine Nummer erneut — monotone `seq` für Cursor |

KEY_INSIGHT: `dbQuery` wechselt per `withContext(Dispatchers.IO)` den Thread; Exposed-Transaktionen hängen am Thread.
APPLIES_TO: Task 2 und Task 6. GOTCHA: Das Ereignis muss **im selben `dbQuery`-Block** mit einer nicht-suspendierenden
Erweiterung `JdbcTransaction.recordEvent(...)` geschrieben werden — niemals über einen zweiten `dbQuery`-Aufruf.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/Recipe.kt:6-29
@JvmInline
value class RecipeId(
    val value: String,
)

data class Recipe(
    val id: RecipeId,
    val name: String,
    ...
)

/** Result of an atomic recipe write. */
enum class RecipeSaveOutcome { SAVED, NAME_TAKEN, NOT_FOUND }
```
Backend: Bezeichner und KDoc **englisch**, Nutzertexte (Exception-Messages) **deutsch**. Pakete nach Fachlichkeit
(`catalog`, `board`, `persistence`, `web`).

### SERVICE_PATTERN (Outcome → Exception, Logging)
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/DishReviewService.kt:8-35
class DishReviewService(
    private val plan: MealPlanRepository,
    private val clock: Clock,
) {
    private val log = LoggerFactory.getLogger(DishReviewService::class.java)

    suspend fun approve(id: DishId) {
        requireReviewed(plan.review(id, DishStatus.APPROVED, null, clock.instant()))
        log.info("Dish approved: {}", id.value)
    }

    private fun requireReviewed(outcome: ReviewOutcome) {
        when (outcome) {
            ReviewOutcome.SAVED -> Unit
            ReviewOutcome.NOT_FOUND -> throw AppException.NotFound("Gericht nicht gefunden")
            ReviewOutcome.NOT_PENDING -> throw AppException.Conflict("Das Gericht wartet nicht auf Freigabe")
        }
    }
}
```

### CRUD_INPUT_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/RecipeService.kt:14-38
/** What a caller wants to store; validated and completed by [RecipeService]. */
data class RecipeInput(val name: String, val servings: Int, ...)

class RecipeService(
    private val recipes: RecipeRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(createdBy: MemberId, input: RecipeInput): Recipe {
        val recipe = validated(RecipeId(newId()), createdBy, input)
        requireSaved(recipes.createIfNameFree(recipe, clock.instant()))
        log.info("Recipe created: {}", recipe.id.value)
        return recipe
    }
```

### REPOSITORY_PATTERN (atomar, bedingt)
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt:125-145
override suspend fun review(id: DishId, status: DishStatus, reason: String?, at: Instant): ReviewOutcome =
    dbQuery(db) {
        val current = MealDishesTable.selectAll().where { MealDishesTable.id eq id.value }.firstOrNull()
        ...
        MealDishesTable.update({ MealDishesTable.id eq id.value }) {
            it[MealDishesTable.status] = status.name
            ...
            it[updatedAt] = at.toEpochMilli()
        }
```
Für Karten: Übergang als **ein** bedingtes `update({ (id eq …) and (status inList from) })`; Rückgabewert 0 → erneut lesen, um
`NOT_FOUND` von `WRONG_STATUS` zu unterscheiden. Zeitstempel als Epoch-Millis (`long`), Datumswerte als ISO-Text.

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt:26-41
fun Route.cookingRoutes(reviews: DishReviewService, cooking: CookingWeekService) {
    authenticate(AUTH_DEVICE) {
        post("/api/v1/meals/dishes/{dishId}/approval") {
            call.requireParent()
            reviews.approve(DishId(call.uuidParameter("dishId")))
            call.respond(HttpStatusCode.NoContent)
        }
        put("/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}") {
            val actor = call.principalOrThrow().toActor()
            val request = call.receive<CancelCookingRequest>()
            val cancellation = cooking.cancel(actor, call.cookMemberId(), call.weekParameter(), request.reason)
            call.respond(CookingCancellationResponse(success = true, data = cancellation.toApi()))
        }
```
Generierte Modelle aus `de.familyplanner.api.model` (openapi-generator); Mapper-Funktionen `toApi()` in `web/*ApiMappers.kt`.

### MIGRATION_PATTERN
```sql
-- SOURCE: backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql:1-17
-- Phase 4: children submit dishes, parents approve or reject. ...
ALTER TABLE meal_dishes ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'
  CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'));
CREATE TABLE meal_cooking_cancellations (
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  ...
);
```

### KOIN_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt:22-37
/** `Database` and `MemberRepository` come from `coreModule`. */
fun mealsModule(clock: Clock) =
    module {
        single<RecipeRepository> { ExposedRecipeRepository(get()) }
        single { RecipeService(get(), clock) }
        ...
    }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/plan/DishReviewServiceTest.kt:12-33
class DishReviewServiceTest {
    private val fixture = PlanFixture()
    private val service = DishReviewService(fixture.planRepository, fixture.clock)

    @Test
    fun `REQ-MEAL-030 approve submitted dish`() =
        runTest {
            val dish = submittedDish()
            service.approve(dish.id)
            assertEquals(DishStatus.APPROVED, stored(dish.id)?.status)
        }
```
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedRecipeRepositoryTest.kt:30-45
@TempDir lateinit var dir: Path
@BeforeTest
fun setUp() = runTest {
    db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))
    parentId = ExposedMemberRepository(db).create(aMember(role = Role.PARENT)).id
    repository = ExposedRecipeRepository(db)
}
```
API-Tests: `testApplication { application { module(testConfig(dir), FIXED_NOW) } … client.call(HttpMethod.Post, path, token, body) }` —
`call` prüft Anfrage **und** Antwort gegen `api/openapi.yaml` (`ContractValidator`). Familie per `setupFamilyWithChild()`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/02-architecture/adr/0016-domain-events-outbox.md` | CREATE | Ereignis-Mechanismus |
| `api/openapi.yaml` | UPDATE | Tag `tasks`, 9 Pfade, Schemas, Parameter; `info.version` 0.6.0 |
| `backend/settings.gradle.kts` | UPDATE | `":tasks"` |
| `backend/tasks/build.gradle.kts` | CREATE | wie `meals` |
| `backend/core/src/main/resources/db/migration/V7__core_domain_events.sql` | CREATE | Outbox + Cursor |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEvents.kt` | CREATE | `DomainEvent`, `TaskAccepted`, `StoredEvent` |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEventCodec.kt` | CREATE | Typ ↔ JSON |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/EventLog.kt` | CREATE | `JdbcTransaction.recordEvent`, Lesen, Cursor |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEventDispatcher.kt` | CREATE | `EventNotifier`, Abonnenten, Zustellung, Mutex |
| `backend/README.md` | UPDATE | Modultabelle um `tasks` |
| `backend/core/src/main/kotlin/de/familyplanner/core/persistence/EventTables.kt` | CREATE | Exposed-Tabellen |
| `backend/core/src/main/kotlin/de/familyplanner/core/CoreModule.kt` | UPDATE | `single { DomainEventDispatcher(get()) }` |
| `backend/core/src/test/kotlin/de/familyplanner/core/events/DomainEventCodecTest.kt` | CREATE | Unit |
| `backend/core/src/test/kotlin/de/familyplanner/core/events/DomainEventDispatcherTest.kt` | CREATE | Integration (SQLite) |
| `backend/tasks/src/main/resources/db/migration/V8__tasks.sql` | CREATE | Katalog + Karten |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/TasksModule.kt` | CREATE | Koin |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogTask.kt` | CREATE | Modell, Limits, Validierung |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogRepository.kt` | CREATE | Port |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogService.kt` | CREATE | REQ-TASK-010 |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCard.kt` | CREATE | Modell, `TaskStatus`, `Actor`, Outcomes |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskTransitions.kt` | CREATE | Reine Übergangsregeln |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/TaskCardRepository.kt` | CREATE | Port |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardService.kt` | CREATE | REQ-TASK-020–025 |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/TasksTables.kt` | CREATE | Exposed-Tabellen |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedCatalogRepository.kt` | CREATE | Adapter |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt` | CREATE | Adapter inkl. Ereignis |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt` | CREATE | Routen |
| `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskApiMappers.kt` | CREATE | Mapping |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/fakes/InMemoryTaskRepositories.kt` | CREATE | Fakes |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/fakes/TasksFixture.kt` | CREATE | Fixture |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/catalog/CatalogServiceTest.kt` | CREATE | Unit |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/board/TaskTransitionsTest.kt` | CREATE | Unit |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/board/BoardServiceTest.kt` | CREATE | Unit |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/persistence/ExposedCatalogRepositoryTest.kt` | CREATE | Integration |
| `backend/tasks/src/test/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepositoryTest.kt` | CREATE | Integration inkl. Ereignis + Nebenläufigkeit |
| `backend/app/build.gradle.kts` | UPDATE | `implementation(project(":tasks"))`, `kover(project(":tasks"))` |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `tasksModule`, `taskRoutes`, Zustellung beim Start |
| `backend/app/src/test/kotlin/de/familyplanner/app/TasksCatalogApiTest.kt` | CREATE | API |
| `backend/app/src/test/kotlin/de/familyplanner/app/TasksBoardApiTest.kt` | CREATE | API |
| `docs/03-design/tasks.md` | CREATE | Moduldesign |
| `docs/03-design/core.md` | UPDATE | Abschnitt „Domänenereignisse (ADR-0016)“ |
| `docs/04-verification/traceability.md` | UPDATE | REQ-TASK-010, 020–025 |
| `docs/01-requirements/tasks.prd.md` | UPDATE | Bericht-Verweis |

## NOT Building

- Kein Android-Code (Phase 3), keine Hinweise/Benachrichtigungen (REQ-TASK-030, Phase 3).
- Kein Abonnent für *Aufgabe abgenommen* — Punkte bucht erst `gamification` (GAME Phase 1).
- Kein *Kochen bestätigt*-Ereignis (REQ-MEAL-034, GAME Phase 1) — der Codec wird dann um einen Typ erweitert.
- Kein Zurücknehmen einer Abnahme (`DONE` ist endgültig, PRD Open Question).
- Keine wiederkehrenden Aufgaben, Fälligkeiten, Eltern-Karten, Sortierung per Drag & Drop.
- Keine Hintergrund-Wiederholung der Zustellung (Timer); nur nach Requests und beim Start.
- Kein Aufräumen alter Ereignisse (Familienvolumen: wenige Tausend Zeilen pro Jahr).
- Keine Umstellung von `meals` auf ein gemeinsames `Actor` in `core`.

---

## Step-by-Step Tasks

### Task 1: ADR-0016 „Domänenereignisse über eine Outbox in core“
- **ACTION**: `docs/02-architecture/adr/0016-domain-events-outbox.md` nach `0000-template.md`.
- **IMPLEMENT**: Kontext (Feature-Module kennen sich nicht; `tasks` und `meals` müssen `gamification` über Abnahmen/Kochen
  informieren; Punkte dürfen nicht verloren gehen; GAME kommt nach TASK). Entscheidung (Tabellen, `recordEvent` in derselben
  Transaktion, Dispatcher mit Cursor je Abonnent, synchron nach dem Request + beim Start, mindestens einmal, Abonnenten idempotent,
  Payload-Typen versioniert in `core.events`, neue Abonnenten lesen ab `seq` 0 nach). Alternativen (direkter Aufruf über ein
  Interface in `core` ohne Persistenz — verliert Ereignisse vor GAME und bei Absturz; Koin-Event-Bus in-memory — dito;
  Handler in derselben Transaktion — koppelt Module an Persistenz-Details und blockiert den Request bei Fehlern; Message-Broker
  (MQTT läuft auf dem Pi) — zusätzlicher Betrieb ohne Nutzen). Konsequenzen (Ereignistabelle wächst, ok; Payload-Änderung =
  neuer Typname `…v2`; Fehler eines Abonnenten werden geloggt und beim nächsten Request wiederholt; Reihenfolge je Abonnent = `seq`).
- **VALIDATE**: Status „Akzeptiert“, Datum 2026-10-24, Verweise ADR-0001, 0003, 0010.

### Task 2: Ereignisprotokoll in `core`
- **ACTION**: Migration, Tabellen, Modelle, Codec, `recordEvent`, Dispatcher, Koin.
- **IMPLEMENT**:
  - `V7__core_domain_events.sql`:
    ```sql
    -- ADR-0016: domain events are written in the same transaction as the change that caused them (outbox).
    CREATE TABLE core_domain_events (
      seq INTEGER PRIMARY KEY AUTOINCREMENT,
      type TEXT NOT NULL,
      payload TEXT NOT NULL,
      occurred_at INTEGER NOT NULL
    );
    -- One cursor per subscriber: the last event it handled successfully. A new subscriber starts at 0 and catches up.
    CREATE TABLE core_event_cursors (
      subscriber TEXT PRIMARY KEY,
      last_seq INTEGER NOT NULL
    );
    ```
  - `persistence/EventTables.kt`: `object DomainEventsTable : Table("core_domain_events") { val seq = long("seq").autoIncrement(); val type = varchar("type", TYPE_LENGTH); val payload = text("payload"); val occurredAt = long("occurred_at"); override val primaryKey = PrimaryKey(seq) }`, `object EventCursorsTable : Table("core_event_cursors")` (`subscriber` varchar PK, `lastSeq` long).
  - `events/DomainEvents.kt`:
    ```kotlin
    /** Fact that another module may react to (ADR-0016). Payloads are versioned via their type name. */
    sealed interface DomainEvent

    /** A parent accepted a task card (REQ-TASK-023); GAME credits [points] to [memberId] once per [cardId]. */
    @Serializable
    data class TaskAccepted(
        val cardId: String,
        val memberId: String,
        val title: String,
        val points: Int,
        val acceptedBy: String,
        val acceptedAt: Long,
    ) : DomainEvent

    /** An event as read back from the log; [event] is `null` for a type this build does not know. */
    data class StoredEvent(val seq: Long, val type: String, val occurredAt: Instant, val event: DomainEvent?)
    ```
  - `events/DomainEventCodec.kt`: `internal object DomainEventCodec { const val TASK_ACCEPTED = "task.accepted.v1"; fun encode(event: DomainEvent): Pair<String, String> = when (event) { is TaskAccepted -> TASK_ACCEPTED to json.encodeToString(event) }; fun decode(type: String, payload: String): DomainEvent? = when (type) { TASK_ACCEPTED -> json.decodeFromString<TaskAccepted>(payload); else -> null } }` mit `Json { ignoreUnknownKeys = true }`. Unbekannter Typ → `null` (kein Absturz bei Downgrade).
  - `events/EventLog.kt`:
    ```kotlin
    /** Appends [event] inside the caller's transaction - call it from the same `dbQuery` block as the change (ADR-0016). */
    fun JdbcTransaction.recordEvent(event: DomainEvent, at: Instant) {
        val (type, payload) = DomainEventCodec.encode(event)
        DomainEventsTable.insert { it[this.type] = type; it[this.payload] = payload; it[occurredAt] = at.toEpochMilli() }
    }
    ```
    plus `internal`-Funktionen für den Dispatcher: `eventsAfter(seq, limit)`, `cursorOf(subscriber)`, `saveCursor(subscriber, seq)` (Upsert: `EventCursorsTable.upsert`).
  - `events/DomainEventDispatcher.kt`:
    ```kotlin
    /** What a module that records events needs: trigger delivery after its transaction committed. */
    fun interface EventNotifier {
        suspend fun dispatchPending()
    }

    /** Delivers logged events to subscribers in order, at least once (ADR-0016). */
    class DomainEventDispatcher(private val db: Database) : EventNotifier {
        private val log = LoggerFactory.getLogger(DomainEventDispatcher::class.java)
        private val subscribers = ConcurrentHashMap<String, suspend (StoredEvent) -> Unit>()
        private val mutex = Mutex()

        /** [name] identifies the cursor and must stay stable across releases. */
        fun subscribe(name: String, handler: suspend (StoredEvent) -> Unit) { subscribers[name] = handler }

        /** Delivers everything each subscriber has not handled yet; a failing subscriber stops at that event and retries next time. */
        override suspend fun dispatchPending() = mutex.withLock { subscribers.forEach { (name, handler) -> deliver(name, handler) } }
    }
    ```
    `deliver`: Schleife `batch = dbQuery { eventsAfter(cursor, BATCH_SIZE) }`, je Ereignis `handler(e)` → `dbQuery { saveCursor(name, e.seq) }`; bei Exception `log.error("Event subscriber {} failed at seq {} ({})", name, e.seq, e.type, ex)` und `return`. `BATCH_SIZE = 100`. Ohne Abonnenten: sofort fertig.
  - `CoreModule`: `single { DomainEventDispatcher(get()) }` und `single<EventNotifier> { get<DomainEventDispatcher>() }`.
- **MIRROR**: REPOSITORY_PATTERN, MIGRATION_PATTERN, KOIN_PATTERN.
- **IMPORTS**: `kotlinx.serialization.Serializable`, `kotlinx.serialization.json.Json`, `kotlinx.coroutines.sync.Mutex`, `…withLock`,
  `org.jetbrains.exposed.v1.jdbc.insert`, `…upsert`, `…selectAll`.
- **GOTCHA**:
  - Das Serialization-Plugin wendet `familyplanner.kotlin-jvm` schon an (`kotlin("plugin.serialization")`, geprüft); `serialization.json` kommt über `api-contract` (`api`).
  - Exposed `upsert` auf SQLite benötigt den PK als Konfliktziel — `EventCursorsTable` hat `subscriber` als PK.
  - `Mutex` nur prozessweit — genügt (ein Container je DB).
  - Handler laufen **außerhalb** einer Transaktion und öffnen eigene `dbQuery`s.
- **VALIDATE**: `DomainEventCodecTest`, `DomainEventDispatcherTest` (siehe Testing Strategy).

### Task 3: OpenAPI-Vertrag
- **ACTION**: `api/openapi.yaml` erweitern (zuerst, ADR-0004).
- **IMPLEMENT**:
  - `info.version: 0.6.0`; Tag `- name: tasks`.
  - Parameter: `CatalogTaskId`, `CardId` (`format: uuid`, path), `IncludeArchived` (query, boolean, default false), `HistoryLimit` (query, integer 1–100, default 50).
  - Schemas:
    - `TaskStatus: { type: string, enum: [TODO, REVIEW, DONE] }`
    - `CatalogTask`: `required [id, title, points, archived]`; `title` 1–40, `emoji` (string 1–16, nullable), `points` 1–100, `archived` boolean.
    - `CatalogTaskInput`: `required [title, points]`; `title`, `emoji` (nullable), `points`.
    - `TaskCard`: `required [id, memberId, title, points, status, assignedAt]`; `catalogTaskId` (uuid, nullable), `emoji` (nullable), `rejectionReason` (nullable, ≤ 200), `assignedAt`, `reportedAt` (nullable), `doneAt` (nullable) als `format: date-time`.
    - `AssignTasksRequest`: `required [memberId, catalogTaskIds]`, `catalogTaskIds` 1–20 Elemente uuid.
    - `RejectTaskRequest`: `reason` (string 1–200, optional — Objekt darf leer sein `{}`).
    - `TaskLane`: `required [memberId, name, cards]`.
    - `TaskBoard`: `required [lanes]`.
    - Envelopes wie `RecipeResponse`/`RecipeListResponse`: `CatalogTaskResponse`, `CatalogTaskListResponse`, `TaskCardResponse`, `TaskCardListResponse`, `TaskBoardResponse`.
  - Pfade wie Tabelle „Interaction Changes“, je mit `operationId` (`listCatalogTasks`, `createCatalogTask`, `updateCatalogTask`, `archiveCatalogTask`, `assignTasks`, `getTaskBoard`, `getTaskHistory`, `reportTask`, `withdrawTaskReport`, `acceptTask`, `rejectTask`, `removeTaskCard`), `tags: [tasks]`, deutscher `summary` mit Rollenhinweis, Standard-Responses `400/401/403/404/409/429` nach Bedarf (`$ref: '#/components/responses/…'`).
  - `rejection` nimmt optionalen Body: `requestBody: { required: false, content: application/json: schema RejectTaskRequest }`.
- **MIRROR**: Endpunkt `approveDish` (Zeilen 637-652), Envelope `RecipeResponse` (1278-1290).
- **GOTCHA**: Generierter Kotlin-Code (`de.familyplanner.api.model`) bildet `nullable` + nicht `required` als `?= null` ab; `date-time` ist wegen `dateLibrary=string` ein `String` → im Mapper `Instant.toString()`. `GeneratedModelSerializationTest` und Android-Client werden mitgeneriert — Android-Build muss grün bleiben (nur additiv).
- **VALIDATE**: `cd backend && ./gradlew :api-contract:openApiGenerate` ohne Fehler; `cd android && ./gradlew :core:api:compileDebugKotlin` (generiert den Client aus derselben Datei) baut.

### Task 4: Modul `tasks` anlegen
- **ACTION**: Gradle-Modul + Koin.
- **IMPLEMENT**: `settings.gradle.kts`: `include(":api-contract", ":core", ":meals", ":tasks", ":app")`. `tasks/build.gradle.kts` = Kopie von `meals/build.gradle.kts`. `TasksModule.kt`:
  ```kotlin
  /** `Database`, `MemberRepository` and `EventNotifier` come from `coreModule`. */
  fun tasksModule(clock: Clock) =
      module {
          single<CatalogRepository> { ExposedCatalogRepository(get()) }
          single<TaskCardRepository> { ExposedTaskCardRepository(get()) }
          single { CatalogService(get(), clock) }
          single { BoardService(get(), get(), get(), get(), clock) }
      }
  ```
- **VALIDATE**: `./gradlew :tasks:compileKotlin`.

### Task 5: Katalog (REQ-TASK-010)
- **ACTION**: Modell, Port, Service, Adapter, Migration (Teil 1).
- **IMPLEMENT**:
  - `catalog/CatalogTask.kt`: `value class CatalogTaskId`, `data class CatalogTask(id, title, emoji: String?, points: Int, archived: Boolean)`, `data class CatalogTaskInput(title, emoji, points)`, Konstanten `TASK_TITLE_MAX = 40`, `TASK_EMOJI_MAX = 16`, `TASK_POINTS_MIN = 1`, `TASK_POINTS_MAX = 100`; `fun validated(input): CatalogTaskInput` (Titel über `validatedName(input.title, "Titel", TASK_TITLE_MAX)`, Emoji: trim, leer → `null`, > 16 → `Validation("Symbol ist zu lang")`, Punkte außerhalb → `Validation("Punkte müssen zwischen 1 und 100 liegen")`).
  - `CatalogRepository`: `findAll(includeArchived: Boolean): List<CatalogTask>` (sortiert nach Titel, case-insensitive), `findById`, `create(task, at)`, `update(task, at): Boolean` (nur wenn nicht archiviert → sonst `false`), `archive(id, at): Boolean`, `findActiveByIds(ids): List<CatalogTask>`.
  - `CatalogService(catalog, clock, newId)`: `list`, `create`, `update` (NotFound „Aufgabe nicht gefunden“, archiviert → `Conflict("Die Aufgabe ist archiviert")`), `archive` (idempotent: schon archiviert → ok; unbekannt → NotFound). `log.info("Catalog task created: {}", id)`.
  - SQL in `V8__tasks.sql` (Teil 1):
    ```sql
    -- TASK phase 2: prepared tasks ("magnets") that parents assign to children.
    CREATE TABLE task_catalog (
      id TEXT PRIMARY KEY,
      title TEXT NOT NULL,
      emoji TEXT,
      points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 100),
      archived_at INTEGER,
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );
    ```
- **MIRROR**: CRUD_INPUT_PATTERN, SERVICE_PATTERN.
- **VALIDATE**: `CatalogServiceTest`, `ExposedCatalogRepositoryTest`.

### Task 6: Karten und Übergänge (REQ-TASK-020–025)
- **ACTION**: Modell, reine Regeln, Port, Service, Adapter, Migration (Teil 2).
- **IMPLEMENT**:
  - `board/TaskCard.kt`:
    ```kotlin
    enum class TaskStatus { TODO, REVIEW, DONE }
    data class Actor(val id: MemberId, val role: Role) { val isParent: Boolean get() = role == Role.PARENT }
    data class TaskCard(
        val id: TaskCardId, val memberId: MemberId, val catalogTaskId: CatalogTaskId?, val title: String, val emoji: String?,
        val points: Int, val status: TaskStatus, val rejectionReason: String?, val assignedAt: Instant,
        val reportedAt: Instant?, val doneAt: Instant?,
    )
    enum class TaskTransition { REPORT, WITHDRAW, ACCEPT, REJECT }
    enum class TransitionOutcome { CHANGED, NOT_FOUND, WRONG_STATUS }
    const val DONE_VISIBLE_DAYS = 7L
    const val HISTORY_LIMIT_DEFAULT = 50
    const val HISTORY_LIMIT_MAX = 100
    const val ASSIGN_MAX = 20
    ```
  - `board/TaskTransitions.kt` (rein, ohne IO):
    ```kotlin
    /** Statuses a [transition] may start from (REQ-TASK-022/023). */
    fun TaskTransition.from(): Set<TaskStatus> = when (this) {
        REPORT -> setOf(TODO); WITHDRAW -> setOf(REVIEW); ACCEPT -> setOf(REVIEW, TODO); REJECT -> setOf(REVIEW)
    }
    fun TaskTransition.to(): TaskStatus = when (this) { REPORT -> REVIEW; WITHDRAW, REJECT -> TODO; ACCEPT -> DONE }

    /** Children act only on their own lane and only report/withdraw; parents accept/reject (REQ-TASK-022/023). */
    fun TaskTransition.isAllowedFor(actor: Actor, card: TaskCard): Boolean = when (this) {
        REPORT, WITHDRAW -> !actor.isParent && card.memberId == actor.id
        ACCEPT, REJECT -> actor.isParent
    }
    fun TaskStatus.isRemovable(): Boolean = this != TaskStatus.DONE
    ```
  - `TaskCardRepository`:
    ```kotlin
    suspend fun createAll(cards: List<TaskCard>)
    suspend fun findById(id: TaskCardId): TaskCard?
    /** Cards not DONE plus DONE cards finished at or after [doneSince]. */
    suspend fun findBoard(doneSince: Instant): List<TaskCard>
    suspend fun findDone(memberId: MemberId, limit: Int): List<TaskCard>
    /**
     * Atomically moves the card if its status is in [from]; on ACCEPT it records [TaskAccepted] in the same transaction.
     * [reason] is stored on REJECT and cleared on REPORT.
     */
    suspend fun transition(id: TaskCardId, transition: TaskTransition, by: MemberId, reason: String?, at: Instant): TransitionOutcome
    /** Deletes the card unless it is DONE. */
    suspend fun remove(id: TaskCardId): TransitionOutcome
    ```
  - `ExposedTaskCardRepository.transition`:
    ```kotlin
    dbQuery(db) {
        val changed = TaskCardsTable.update({ (TaskCardsTable.id eq id.value) and (TaskCardsTable.status inList transition.from().map { it.name }) }) {
            it[status] = transition.to().name
            when (transition) {
                REPORT -> { it[reportedAt] = at.toEpochMilli(); it[rejectionReason] = null }
                WITHDRAW -> it[reportedAt] = null
                REJECT -> { it[reportedAt] = null; it[rejectionReason] = reason }
                ACCEPT -> { it[doneAt] = at.toEpochMilli(); it[doneBy] = by.value }
            }
        }
        if (changed == 0) return@dbQuery if (exists(id)) TransitionOutcome.WRONG_STATUS else TransitionOutcome.NOT_FOUND
        if (transition == ACCEPT) recordEvent(acceptedEvent(id, by, at), at)
        TransitionOutcome.CHANGED
    }
    ```
    `acceptedEvent` liest die Karte im selben Block (`selectAll().where { id }`) für `memberId`, `title`, `points`.
  - `BoardService(members: MemberRepository, catalog: CatalogRepository, cards: TaskCardRepository, events: EventNotifier, clock, newId)`:
    - `assign(memberId, catalogTaskIds)`: 1–20 IDs (sonst Validation), Mitglied existiert (`NotFound("Mitglied nicht gefunden")`) und ist `CHILD` (`Validation("Aufgaben können nur Kindern zugewiesen werden")`); `catalog.findActiveByIds(distinct)` — fehlt eine ID oder ist archiviert → `Validation("Aufgabe nicht verfügbar")`; je ID (Duplikate behalten, Reihenfolge der Eingabe) eine Karte mit Kopie von Titel/Emoji/Punkten, `TODO`, `assignedAt = now`; `cards.createAll`; Rückgabe der Karten. Log `"Task cards assigned: {} to {}"`.
    - `board()`: `children = members.findAll().filter { it.role == CHILD }.sortedBy { it.name }`, `cards.findBoard(now - 7 Tage)`, gruppiert je Kind; Karten sortiert: `TODO` (assignedAt aufsteigend), `REVIEW` (reportedAt aufsteigend), `DONE` (doneAt absteigend). Rückgabe `List<Lane(member, cards)>`; Kind ohne Karten → leere Lane.
    - `history(memberId, limit)`: `limit` 1–100 (sonst Validation), Mitglied existiert, `cards.findDone(memberId, limit)`.
    - `report/withdraw/accept/reject(actor, cardId[, reason])` über eine private `move(actor, id, transition, reason)`:
      `card = cards.findById(id) ?: NotFound("Karte nicht gefunden")`; `if (!transition.isAllowedFor(actor, card)) throw Forbidden(…)`
      (Kind fremde Karte: `"Nur eigene Aufgaben"`; Kind Eltern-Aktion: Standardtext „Nur für Eltern“);
      `outcome = cards.transition(...)`; `WRONG_STATUS` → `Conflict(conflictText(transition))` (z. B. REPORT „Die Aufgabe ist nicht mehr offen“, WITHDRAW/ACCEPT/REJECT „Die Aufgabe wartet nicht auf Abnahme“ bzw. für ACCEPT „Die Aufgabe ist schon erledigt“); `NOT_FOUND` → NotFound; danach `cards.findById(id)!!` zurückgeben. Bei `ACCEPT` anschließend `events.dispatchPending()`. Reason bei REJECT: optional, `null`/leer → `null`, sonst `validatedName(reason, "Begründung", REASON_MAX)` mit `REASON_MAX = 200` (eigene Konstante in `tasks`).
    - `remove(cardId)`: `NOT_FOUND` → NotFound, `WRONG_STATUS` → `Conflict("Erledigte Aufgaben können nicht entfernt werden")`.
    - Logs: `"Task card {}: {}"` mit Transition-Name und ID; nie Titel/Begründung loggen.
  - SQL `V8__tasks.sql` (Teil 2):
    ```sql
    -- A card copies title, emoji and points from the catalog when assigned; later catalog edits do not change it.
    CREATE TABLE task_cards (
      id TEXT PRIMARY KEY,
      member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
      catalog_task_id TEXT REFERENCES task_catalog(id) ON DELETE SET NULL,
      title TEXT NOT NULL,
      emoji TEXT,
      points INTEGER NOT NULL CHECK (points BETWEEN 1 AND 100),
      status TEXT NOT NULL CHECK (status IN ('TODO','REVIEW','DONE')),
      rejection_reason TEXT,
      assigned_at INTEGER NOT NULL,
      reported_at INTEGER,
      done_at INTEGER,
      done_by TEXT REFERENCES members(id)
    );
    CREATE INDEX idx_task_cards_member_status ON task_cards(member_id, status, done_at);
    ```
- **MIRROR**: SERVICE_PATTERN, REPOSITORY_PATTERN, MIGRATION_PATTERN.
- **IMPORTS**: `de.familyplanner.core.events.TaskAccepted`, `de.familyplanner.core.events.recordEvent`, `de.familyplanner.core.events.EventNotifier`, `org.jetbrains.exposed.v1.core.inList`, `…and`, `…eq`.
- **GOTCHA**:
  - `findById` + Rechteprüfung vor dem atomaren Update: zwischen beiden kann ein anderer Request den Status ändern — das ist ok,
    das bedingte Update entscheidet (`WRONG_STATUS` → 409). Rechte hängen nur an `memberId`/Rolle, die sich nicht ändern.
  - Doppelte gleichzeitige Abnahme: SQLite serialisiert Schreiber; das zweite `UPDATE … WHERE status IN (REVIEW, TODO)` trifft 0 Zeilen
    → kein zweites Ereignis. Test mit zwei parallelen `async` gegen echte DB.
  - `done_by` ist nur Information (für spätere Anzeige), kein API-Feld in dieser Phase.
  - `DONE_VISIBLE_DAYS` relativ zu `clock.instant()` (UTC-Instant reicht; kein Kalendertag nötig).
- **VALIDATE**: `TaskTransitionsTest`, `BoardServiceTest`, `ExposedTaskCardRepositoryTest`.

### Task 7: Routen und Mapper
- **ACTION**: `web/TaskRoutes.kt`, `web/TaskApiMappers.kt`.
- **IMPLEMENT**: `fun Route.taskRoutes(catalog: CatalogService, board: BoardService)` in `authenticate(AUTH_DEVICE)`:
  Katalog-Routen mit `call.requireParent()`; `POST /api/v1/tasks/cards` → `requireParent`, `call.receive<AssignTasksRequest>()`,
  `respond(HttpStatusCode.Created, TaskCardListResponse(true, cards.map { it.toApi() }))`; Board/History `principalOrThrow()`;
  Übergänge `val actor = call.principalOrThrow().toActor()`; `rejection` liest Body optional:
  `val reason = if (call.request.contentLength() ?: 0L > 0L) call.receive<RejectTaskRequest>().reason else null`.
  Query-Parameter `includeArchived` (`toBooleanStrictOrNull()`, ungültig → Validation) und `limit` (`toIntOrNull()`) — Hilfsfunktionen
  privat in `TaskRoutes.kt` (Muster `meals/web/CallParameters.kt`).
  Mapper: `CatalogTask.toApi()`, `TaskCard.toApi()` (`Instant.toString()`), `Lane.toApi()`, `DevicePrincipal.toActor()`.
- **MIRROR**: ROUTE_PATTERN.
- **GOTCHA**: `contentLength()` ist bei Chunked-Bodies `null` — Ktor-Test-Client sendet ohne Body keinen `Content-Type`; Alternative robust:
  `call.receiveNullable<RejectTaskRequest>()` in `runCatching` nur, wenn `call.request.contentType().match(ContentType.Application.Json)`.
  Die Variante im Test mit **und** ohne Body prüfen.
- **VALIDATE**: API-Tests (Task 9).

### Task 8: App-Verdrahtung
- **ACTION**: `app/build.gradle.kts`, `Application.kt`.
- **IMPLEMENT**: `implementation(project(":tasks"))`, `kover(project(":tasks"))`; in `module(...)`:
  `modules(coreModule(...), mealsModule(clock), tasksModule(clock))`; `taskRoutes(get<CatalogService>(), get<BoardService>())`;
  nach `routing { … }`: `monitor.subscribe(ApplicationStarted) { launch { get<DomainEventDispatcher>().dispatchPending() } }`
  (Startnachlauf; in dieser Phase ohne Abonnenten ein No-op).
- **GOTCHA**: Kover-Grenze 80 % gilt aggregiert in `:app` — neue Module müssen mit Tests abgedeckt sein. `ApplicationStarted`-Callback
  läuft im `Application`-Scope (`this.launch`).
- **VALIDATE**: `./gradlew :app:test`.

### Task 9: Tests
- **ACTION**: Tests nach Testing Strategy; Fakes `InMemoryCatalogRepository`, `InMemoryTaskCardRepository` (zeichnet akzeptierte
  Ereignisse in einer Liste `recordedEvents` auf, statt sie in eine DB zu schreiben), `TasksFixture` (MutableClock, `InMemoryMemberRepository`
  aus `core`-testFixtures, `parent()`/`child(name)`-Helfer, `catalogTask(points)`, `CountingEventNotifier` mit Feld `calls`).
  `BoardService` hängt nur am `EventNotifier` → Unit-Tests brauchen keine DB.
- **VALIDATE**: `./gradlew :core:test :tasks:test :app:test`.

### Task 10: Doku, Traceability, Version
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - `docs/03-design/tasks.md` nach dem Aufbau von `meals.md`: Abgedeckte Anforderungen, Domänenmodell & Regeln (Statusmaschine als
    Tabelle Rolle × Übergang × von → nach), Schnittstellen (Endpunkte), DB-Tabellen, Eingabegrenzen, Unit-Testfälle.
  - `docs/03-design/core.md`: Abschnitt „## Domänenereignisse (ADR-0016)“ (Tabellen, `recordEvent`, Dispatcher, Garantien, Typen).
  - `docs/04-verification/traceability.md`: Zeilen REQ-TASK-010, 020–025 (Unit: `CatalogServiceTest`, `TaskTransitionsTest`, `BoardServiceTest`;
    Integration: `ExposedCatalogRepositoryTest`, `ExposedTaskCardRepositoryTest`, `TasksCatalogApiTest`, `TasksBoardApiTest`; Status
    „umgesetzt (Backend)“).
  - `backend/README.md`: Modultabelle (Zeile `meals`) um eine Zeile `tasks` ergänzen (Design: `docs/03-design/tasks.md`).
  - PRD Phase 2: Bericht-Verweis.
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-TASK-010 parent creates catalog task with trimmed title` | `" Zimmer saugen "`, 2 | Titel „Zimmer saugen“, 2 Punkte | |
| `REQ-TASK-010 title, emoji and points are validated` | leer / 41 Zeichen / Emoji 17 / 0 / 101 Punkte | `Validation` | ja |
| `REQ-TASK-010 empty emoji is stored as none` | `"  "` | `emoji == null` | ja |
| `REQ-TASK-010 archived task is hidden and cannot be edited` | archivieren, dann `list()`/`update` | nicht in Liste; `Conflict` | |
| `REQ-TASK-010 archiving twice is fine, unknown task is not found` | | ok / `NotFound` | ja |
| `REQ-TASK-022 child may report and withdraw only own cards` (`TaskTransitionsTest`) | Kind A × Karte A/B | true/false | |
| `REQ-TASK-023 only parents accept and reject` | Kind / Eltern | false / true | |
| `REQ-TASK-023 transitions start only from allowed statuses` | alle `TaskTransition` | `from()`/`to()` wie Tabelle | |
| `REQ-TASK-020 assigning copies title, emoji and points` | Katalog 2 Punkte → zuweisen → Katalog auf 3 ändern | Karte behält 2 | |
| `REQ-TASK-020 same task twice gives two cards` | `[a, a]` | 2 Karten `TODO` | ja |
| `REQ-TASK-020 only children get tasks` | Eltern-ID | `Validation` | ja |
| `REQ-TASK-020 archived or unknown catalog task is rejected` | | `Validation` | ja |
| `REQ-TASK-020 between 1 and 20 tasks per call` | 0 / 21 | `Validation` | ja |
| `REQ-TASK-021 board has one lane per child sorted by name, also empty` | 2 Kinder, 1 ohne Karten | 2 Lanes | ja |
| `REQ-TASK-025 done cards older than 7 days leave the board but stay in history` | Done vor 8 Tagen / 6 Tagen | Board: nur 6-Tage-Karte; History: beide, neueste zuerst | ja |
| `REQ-TASK-022 child reports own card and withdraws it` | TODO → REVIEW → TODO | Status, `reportedAt` gesetzt/geleert | |
| `REQ-TASK-022 child cannot move a sibling's card` | | `Forbidden` | |
| `REQ-TASK-023 parent accepts reported card and an event is recorded` | REVIEW → DONE | `doneAt`, 1 Ereignis `TaskAccepted(points=2)`, `dispatchPending` 1× | |
| `REQ-TASK-023 parent accepts a todo card directly` | TODO → DONE | DONE, Ereignis | |
| `REQ-TASK-023 parent rejects with reason, child sees it, reporting clears it` | REVIEW → TODO „nicht sauber“ → REPORT | Grund gesetzt, danach `null` | |
| `REQ-TASK-023 rejection without reason is allowed` | `null`/`"  "` | TODO, Grund `null` | ja |
| `REQ-TASK-023 done cards cannot move` | DONE × alle Übergänge | `Conflict` | ja |
| `REQ-TASK-024 parent removes todo or review card, not done` | | weg / `Conflict` | |
| `REQ-TASK-021 history limit must be 1 to 100` | 0 / 101 | `Validation` | ja |
| `DomainEventCodec round-trips TaskAccepted, unknown type decodes to null` | | gleich / `null` | ja |

### Integration Tests

| Test | Setup | Expected |
|---|---|---|
| `DomainEventDispatcherTest`: `recorded events reach a subscriber in order` | Temp-DB, 3× `recordEvent` in `dbQuery`, 1 Abonnent | Abonnent erhält seq 1,2,3; Cursor = 3 |
| `… a new subscriber catches up from the beginning` | Ereignisse vor `subscribe` | alle nachgeliefert |
| `… a failing subscriber retries from the failed event and does not block others` | Abonnent A wirft bei seq 2, B ok | A: 1, dann beim nächsten Lauf 2,3; B: 1,2,3 |
| `… nothing is delivered twice after success` | 2× `dispatchPending` | je Ereignis 1 Aufruf |
| `… event of a rolled back transaction is not delivered` | `recordEvent` + Exception im selben `dbQuery` | 0 Ereignisse |
| `ExposedCatalogRepositoryTest` | CRUD, Sortierung case-insensitive, archivieren, `findActiveByIds` | wie Unit |
| `ExposedTaskCardRepositoryTest`: `REQ-TASK-023 accepting writes the card and the event atomically` | Karte REVIEW → ACCEPT | Zeile in `core_domain_events` mit Typ `task.accepted.v1` und Payload-Punkten |
| `… concurrent accepts record exactly one event` | 2× `async { transition(ACCEPT) }` | Outcomes {CHANGED, WRONG_STATUS}; 1 Ereignis | ja |
| `… board query keeps open cards and recent done cards` | | wie Unit |
| `… migration V8 enforces status and points checks` | Insert mit `status='X'` / `points=0` | SQL-Fehler | ja |
| `TasksCatalogApiTest`: `REQ-TASK-010 parent manages the catalog` | POST/GET/PUT/DELETE, `includeArchived` | 201/200/200/204, Vertrag gültig | |
| `… REQ-TASK-010 child gets 403 on the catalog` | Kind-Token | 403 | |
| `… invalid catalog input gets 400` | Punkte 0 | 400 (`invalidRequestExpected = true`) | ja |
| `TasksBoardApiTest`: `REQ-TASK-020 parent assigns and everyone sees the board` | zuweisen, Board als Kind und Eltern | 201; Lane „Lea“ mit 2 Karten `TODO` | |
| `… REQ-TASK-022 child reports and withdraws` | report → withdrawal | 200, Status | |
| `… REQ-TASK-023 parent accepts and rejects` | acceptance / rejection mit und ohne Body | 200; DONE / TODO mit Grund | |
| `… REQ-TASK-023 second acceptance is a conflict` | 2× acceptance | 200, 409 | ja |
| `… REQ-TASK-022 child cannot accept and cannot report for a sibling` | | 403 | |
| `… REQ-TASK-024 parent removes a card, done card cannot be removed` | | 204, 409 | |
| `… REQ-TASK-025 history returns done cards newest first` | | 200, Reihenfolge | |
| `… unknown card gets 404, malformed id 400` | | 404 / 400 | ja |

### Edge Cases Checklist
- [x] Leere Eingaben, Grenzwerte (Titel 40, Emoji 16, Punkte 1/100, 1–20 IDs, Limit 1–100)
- [x] Unbekannte / archivierte Katalogaufgabe, unbekannte Karte, unbekanntes Mitglied, Eltern als Ziel
- [x] Gleichzeitige Abnahme (genau ein Ereignis)
- [x] Fremde Lane, falsche Rolle (403)
- [x] Done ist endgültig
- [x] Rollback ohne Ereignis; Abonnent-Fehler blockiert Request nicht
- [x] Unbekannter Ereignistyp (Downgrade)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Funde

### Unit + Integration Tests
```bash
cd backend && ./gradlew :core:test :tasks:test :app:test
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd backend && ./gradlew check koverVerify && ./gradlew :app:buildFatJar
```
EXPECT: grün, Kover ≥ 80 %, Fat-JAR baut

### Android-Client bleibt baubar (generierter Vertrag)
```bash
cd android && ./gradlew assembleDebug
```
EXPECT: grün (nur neue Modelle/Endpunkte)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=test-setup-token-123456 FP_DB_PATH=/tmp/fp-board.db FP_TLS_ENABLED=false ./gradlew :app:run
sqlite3 /tmp/fp-board.db "select version, description, success from flyway_schema_history order by installed_rank"
```
EXPECT: V1–V8 erfolgreich; `core_domain_events`, `core_event_cursors`, `task_catalog`, `task_cards` vorhanden.
Auf einer **Kopie** der Pi-Datenbank (aus `data/backups/`) zusätzlich prüfen, dass V7/V8 auf den bestehenden Stand laufen.

### Manual Validation (lokal gegen `:app:run`, curl)
- [ ] Familie einrichten (`/api/v1/setup`), Kind anlegen und koppeln (wie `deploy/README.md`)
- [ ] Katalog: „Spülmaschine ausräumen“ 1 Punkt, „Bad putzen“ 3 Punkte anlegen; Kind-Token bekommt 403
- [ ] Beide dem Kind zuweisen → Board zeigt 2 Karten in Todo
- [ ] Kind meldet eine → REVIEW; Eltern nehmen ab → DONE; `select type, payload from core_domain_events` zeigt 1 Ereignis mit 3 Punkten
- [ ] Zweite Abnahme derselben Karte → 409, weiterhin 1 Ereignis
- [ ] Eltern weisen zurück mit Grund → Kind sieht `rejectionReason`
- [ ] Nach Deploy auf den Pi: Familien-App (Version 0.7.0) funktioniert unverändert; `/health` ok; Testinstanz (falls aus Phase 1 aktiv) zeigt dieselben Endpunkte

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-TASK-010, 020–025 haben Tests mit REQ-ID im Namen (Unit + API)
- [ ] Abnahme schreibt genau ein Ereignis `task.accepted.v1` in derselben Transaktion; parallele Abnahme → ein Ereignis
- [ ] Rechte: Katalog/Zuweisen/Abnehmen/Zurückweisen/Entfernen nur Eltern; Melden/Zurückziehen nur eigenes Kind
- [ ] API nur additiv, Vertrag (`ContractValidator`) für alle neuen Endpunkte geprüft; Android baut
- [ ] ADR-0016, `tasks.md`, `core.md`, Traceability aktualisiert

## Completion Checklist
- [ ] Modul folgt ADR-0010 (Paket, Koin, Routen, Migration, Tabellenpräfix, testFixtures)
- [ ] Fehler über `AppException` mit deutschen Texten; keine Stacktraces nach außen
- [ ] Logging `log.info` mit IDs, keine Titel/Begründungen/Token
- [ ] Tests nach Mustern (Fixture + Fakes, Temp-SQLite, `client.call` mit Vertragsprüfung)
- [ ] Keine Magic Numbers (Konstanten in `CatalogTask.kt`/`TaskCard.kt`, `BATCH_SIZE`)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen (Routen ggf. auf `CatalogRoutes.kt` + `BoardRoutes.kt` aufteilen)
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Ereignis außerhalb der Transaktion geschrieben (zweites `dbQuery`) → Verlust oder Geister-Ereignis | M | H | `recordEvent` nur als `JdbcTransaction`-Erweiterung; Rollback-Test; Review-Punkt |
| Migration V7/V8 kollidiert mit einer parallel entstehenden Migration (z. B. GAME) | L | M | ADR-0010-Regel: später gemergte umbenennen; GAME-Plan startet bei V9 |
| Abonnent wirft dauerhaft (Bug) → staut Ereignisse | L | M | Log `error` mit seq; andere Abonnenten laufen weiter; Fix + Neustart holt nach |
| Optionaler Body bei `rejection` wird vom Vertragsvalidator oder Ktor falsch behandelt | M | L | Beide Varianten im API-Test |
| Kover < 80 % durch neues Modul | M | M | Tests je Service/Adapter wie in `meals`; Mapper durch API-Tests abgedeckt |
| Familien-App bricht durch Backend-Deploy | L | H | Nur additive API; bestehende API-Tests unverändert grün |

## Notes
- **Schnittstelle für GAME Phase 1:** `dispatcher.subscribe("gamification.points") { stored -> (stored.event as? TaskAccepted)?.let { … } }`
  in `gamificationModule`; idempotent über eindeutigen Ledger-Schlüssel `TASK:<cardId>`. Weil der Cursor bei 0 startet, werden alle
  Abnahmen seit diesem Deploy nachgebucht.
- `EventNotifier`-Interface (Task 9) hält `BoardService` testbar ohne DB; `DomainEventDispatcher` implementiert es.
- Diese Phase läuft parallel zu TASK Phase 1 (andere Session, nur `android/`, `deploy/`). ADR-Nummern: Phase 1 belegt 0014/0015, dieser Plan 0016.
- Umsetzung laut CLAUDE.md in einer **neuen Session** mit `/ecc:prp-implement .claude/PRPs/plans/board-backend.plan.md`.
