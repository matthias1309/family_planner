# Plan: Punktekonto Backend/API (GAME Phase 1a, inkl. REQ-MEAL-034)

## Summary
Neues Backend-Modul `gamification` mit einem unveränderlichen **Buchungsjournal** je Kind, aus dem *Gesammelt* und *Guthaben*
berechnet werden. Punkte entstehen automatisch aus den Domänenereignissen *Aufgabe abgenommen* (`task.accepted.v1`, gibt es
seit TASK Phase 2) und dem neuen *Kochen bestätigt* (`cooking.confirmed.v1`), das `meals` schreibt, wenn ein Elternteil ein
freigegebenes Kindergericht am oder nach dem Kochtag als gekocht bestätigt (REQ-MEAL-034). Dazu: Kochpunktwert-Einstellung,
manuelle Buchung durch Eltern, Kontostand mit Verlauf und Rangliste. API zuerst in `api/openapi.yaml`, nur additiv. Die App
folgt in Phase 1b.

## User Story
Als Kind möchte ich für abgenommene Aufgaben und bestätigtes Kochen automatisch Punkte bekommen und meinen Stand, meinen
Verlauf und die Rangliste sehen; als Elternteil möchte ich den Küchenboard-Stand übernehmen und Fehler per Gegenbuchung
korrigieren — damit das Küchenboard digital wird.

## Problem → Solution
Abnahmen schreiben schon Ereignisse in die Outbox, aber niemand bucht Punkte; Kochen kennt keinen Zustand „gekocht“ →
`meals` bekommt die Kochbestätigung mit Ereignis, `gamification` abonniert beide Ereignisse (idempotent je Quelle) und bietet
Konto, Rangliste, manuelle Buchung und Einstellungen über REST an.

## Metadata
- **Complexity**: Large (≈ 40 Dateien, neues Gradle-Modul, erster echter Ereignis-Abonnent)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: Phase 1 — Punktekonto, Teil **1a Backend/API** (1b App folgt in eigenem Plan)
- **Requirements**: REQ-GAME-010, 011, 012, 013, 014 (Backend-Teil), REQ-MEAL-034 (Backend-Teil)
- **Estimated Files**: ≈ 40 (inkl. Tests und Doku)

---

## Entscheidungen (Planung 2026-10-25)

Die Open Questions des PRD werden mit den dort genannten Vorschlägen entschieden; bei Bedarf nach dem Testbetrieb ändern.

| Thema | Entscheidung | Begründung |
|---|---|---|
| Zustellung / Idempotenz | Abonnent `gamification.points` am `DomainEventDispatcher` (ADR-0016); Gutschrift in **eigener** Transaktion des Abonnenten, eindeutiger `source_key` (`task:<cardId>`, `cooking:<dishId>`), doppelte Zustellung → `INSERT OR IGNORE` | ADR-0016 ersetzt die PRD-Notiz „Gutschrift in derselben Transaktion“: das Ereignis ist atomar mit dem Statuswechsel, die Gutschrift ist mindestens-einmal + idempotent |
| Nachholen | Cursor startet bei 0 → alle Abnahmen seit TASK Phase 2 werden beim ersten Start gutgeschrieben | Gewollt laut ADR-0016 („Abnahmen davor zählen trotzdem“) |
| Buchungszeitpunkt | `booked_at` = Zeitpunkt aus dem Ereignis (`acceptedAt` / `confirmedAt`), nicht „jetzt“ | Nachgeholte Buchungen stehen am richtigen Datum im Verlauf |
| Kochpunktwert | Einstellung in `gamification` (`game_settings`), Standard **3**, Bereich 1–20; gilt für Ereignisse, die **ab der Änderung verarbeitet** werden | `meals` kennt keine Punkte (Modulgrenze); Zustellung läuft synchron im Request, also praktisch „ab Bestätigung“ |
| Arten im Journal | Alle sechs Arten (`TASK`, `COOKING`, `MANUAL`, `SHOP`, `REWARD`, `REWARD_REFUND`) schon jetzt in SQL-CHECK **und** OpenAPI-Enum; Phase 1 bucht nur die ersten drei | SQLite kann CHECK nicht ändern (Tabellen-Neubau); Android-kotlinx scheitert an unbekannten Enum-Werten → spätere Phasen bleiben additiv |
| Gesammelt / Guthaben | Aus dem Journal per `SUM` berechnet (kein gespeicherter Stand); Gesammelt = `TASK`+`COOKING`+`MANUAL` | PRD-Regel, YAGNI für Caching |
| Manuelle Buchung | Nur Eltern, nur auf Kinder, Betrag ±1–1000 (≠ 0), Begründung 1–200 Zeichen Pflicht (wird `title`) | REQ-GAME-013; Startbuchung vom Küchenboard = normale manuelle Buchung |
| Rangliste | Alle Kinder (auch ohne Buchung, 0 Punkte), nach Gesammelt absteigend, Gleichstand gleicher Rang (1, 1, 3), dann nach Name; ohne Guthaben; Avatar folgt in Phase 4 | REQ-GAME-014, Sichtbarkeitsregel (Guthaben nur selbst + Eltern) |
| Konto-Sichtbarkeit | `GET …/members/{id}/account`: Kind nur eigenes (sonst 403), Eltern alle Kinder; Eltern-Mitglied → 400 „Eltern sammeln keine Punkte“ | PRD „Sichtbarkeit“ |
| Verlauf | Neueste zuerst, `limit` 1–100 (Standard 50) wie Aufgabenverlauf; keine Seitenblätterung | Familienvolumen; wie `getTaskHistory` |
| Kochbestätigung | `POST /api/v1/meals/dishes/{dishId}/cooking-confirmation` (Eltern) → `204`; nur `APPROVED`, Koch ist `CHILD`, `meal_date <= heute` (Zone `Europe/Berlin`), nur einmal; Gericht bekommt `cookedAt` im API-Modell | REQ-MEAL-034; Stil wie `approval`/`rejection` |
| Fehler bei Kochbestätigung | Unbekannt 404; Elterngericht 400; nicht freigegeben / vor dem Kochtag / schon bestätigt 409 | Zustandsabhängig → 409 wie bei Freigabe |
| Ungültige Ereignisse | Mitglied unbekannt oder kein Kind → **überspringen** mit `log.warn`, nicht werfen | Sonst bliebe der Abonnent dauerhaft an diesem Ereignis hängen |
| `Actor` | Eigene `data class Actor` in `gamification.account` | Wie `tasks`/`meals` (Modulgrenze) |
| Migrationen | `V9__meals_cooking_confirmation.sql` (meals), `V10__gamification.sql` (gamification) | Global fortlaufend (ADR-0010); V1–V8 belegt |
| Tabellenpräfix | `game_` | Wie `meal_`/`task_` (Modulkürzel) |
| API-Version | `info.version: 0.7.0`, Tag `gamification` | Additiv |

---

## UX Design
N/A — reine Backend-/API-Änderung. Die App-Seite (Bereich „Ich“, Rangliste, Eltern-Ansicht unter „Familie“, Kochbestätigung
im Essensplan) folgt in Phase 1b.

### Interaction Changes (API)
| Endpoint | Rolle | Wirkung |
|---|---|---|
| `POST /api/v1/meals/dishes/{dishId}/cooking-confirmation` | Eltern | Kindergericht als gekocht markieren + Ereignis `cooking.confirmed.v1` → `204` |
| `GET /api/v1/meals/weeks/{weekStart}/plan` (bestehend) | alle | `Dish.cookedAt` neu (nullable) |
| `GET /api/v1/gamification/leaderboard` | alle | Kinder mit Rang und Gesammelt |
| `GET /api/v1/gamification/members/{memberId}/account?limit=` | Kind (eigenes), Eltern | Gesammelt, Guthaben, Buchungen neueste zuerst |
| `POST /api/v1/gamification/members/{memberId}/entries` | Eltern | manuelle Buchung `{amount, reason}` → `201` mit Buchung |
| `GET /api/v1/gamification/settings` | alle | `{cookingPoints}` |
| `PUT /api/v1/gamification/settings` | Eltern | Kochpunktwert ändern → `200` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/02-architecture/adr/0016-domain-events-outbox.md` | all | Outbox, mindestens einmal, Abonnenten idempotent, Start bei seq 0 |
| P0 | `docs/02-architecture/adr/0010-backend-feature-modules.md` | all | Modul-, Pfad-, Migrations-, Präfix- und Fake-Konventionen |
| P0 | `backend/core/src/main/kotlin/de/familyplanner/core/events/*.kt` | all | `DomainEvent`, `TaskAccepted`, Codec, `recordEvent`, `DomainEventDispatcher.subscribe`, `EventNotifier` |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt` | 75-130 | Bedingtes Update + `recordEvent` im selben `dbQuery`, Outcome-Klassifizierung |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardService.kt` | all | `events.dispatchPending()` nach dem Commit, Outcome → Exception |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | all | `review()`, `toDish()` — hier kommt `confirmCooked` und `cookedAt` dazu |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt`, `DishReviewService.kt`, `MealPlanRepository.kt` | all | `Dish`, `DishStatus`, Outcome-Enums, Service-Stil |
| P0 | `api/openapi.yaml` | 1-17, 638-654, 792-830, 915-1015, 1415-1437, 1720-1850 | Header/Tags, Endpunkt-Stil, Parameter (`MemberId`, `DishId`, `HistoryLimit`), Standard-Responses, `Dish`, Envelopes |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Attendance.kt` | 1-40 | `ZONE = Europe/Berlin`, `LocalDate.ofInstant(clock.instant(), ZONE)` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt`, `PlanApiMappers.kt` | all | Routenstil, `Dish.toApi()` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/DeadlineSettingsService.kt`, `persistence/ExposedDeadlineSettingsRepository.kt` | all | Einstellungs-Muster (Upsert, Defaults) |
| P1 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt` | 80-140 | `historyLimit()`-Parsing, `toActor()` |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/auth/RoleGuard.kt`, `error/AppException.kt`, `error/Validation.kt` | all | `requireParent`, `uuidParameter`, Fehlercodes, `validatedName` |
| P1 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt`, `backend/app/build.gradle.kts`, `backend/settings.gradle.kts`, `backend/tasks/build.gradle.kts` | all | Verdrahtung, Kover |
| P2 | `backend/tasks/src/test/kotlin/de/familyplanner/tasks/fakes/TasksFixture.kt`, `InMemoryTaskRepositories.kt` | all | Fixture- und Fake-Muster (`CountingEventNotifier`) |
| P2 | `backend/tasks/src/test/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepositoryTest.kt` | all | Temp-SQLite, Ereignis atomar, parallele Aufrufe |
| P2 | `backend/core/src/test/kotlin/de/familyplanner/core/events/DomainEventDispatcherTest.kt` | all | Dispatcher in Tests |
| P2 | `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryMealPlanRepository.kt`, `PlanFixture.kt` | all | Fake erweitern |
| P2 | `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt`, `TasksBoardApiTest.kt`, `MealsSubmissionApiTest.kt` | all | `client.call` mit Vertragsprüfung, `setupFamilyWithChild()`, `makeCook`, `dishJson` |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| SQLite UNIQUE + NULL | sqlite.org/lang_createtable.html#unique_constraints | Mehrere `NULL` in einer UNIQUE-Spalte sind erlaubt → manuelle Buchungen ohne `source_key` |
| SQLite INSERT OR IGNORE | sqlite.org/lang_conflict.html | Verletzung von UNIQUE → Zeile wird still übersprungen; Exposed: `Table.insertIgnore { }` (`org.jetbrains.exposed.v1.jdbc.insertIgnore`), `insertedCount` 0/1 |
| SQLite ALTER TABLE ADD COLUMN | sqlite.org/lang_altertable.html | Spalte mit `REFERENCES` nur mit Default `NULL` — passt für `cooked_by` |

KEY_INSIGHT: Der Abonnent läuft **außerhalb** einer Transaktion nach dem Commit des Auslösers; der Cursor wird erst nach
erfolgreichem Handler gespeichert. APPLIES_TO: Task 6. GOTCHA: Abstürze zwischen Gutschrift und Cursor-Speicherung führen zu
erneuter Zustellung → ohne eindeutigen `source_key` doppelte Punkte.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt:13-20, 63-66
enum class DishStatus { SUBMITTED, APPROVED, REJECTED }

@JvmInline
value class DishId(
    val value: String,
)

enum class ReviewOutcome { SAVED, NOT_FOUND, NOT_PENDING }
```
Bezeichner und KDoc **englisch**, Nutzertexte (Exception-Messages) **deutsch**. Pakete nach Fachlichkeit
(`ledger`, `account`, `settings`, `persistence`, `web`).

### EVENT_TYPE (core)
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEvents.kt:9-20
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
```
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEventCodec.kt:7-26
const val TASK_ACCEPTED = "task.accepted.v1"
fun encode(event: DomainEvent): Pair<String, String> =
    when (event) {
        is TaskAccepted -> TASK_ACCEPTED to json.encodeToString(event)
    }
fun decode(type: String, payload: String): DomainEvent? =
    when (type) {
        TASK_ACCEPTED -> json.decodeFromString<TaskAccepted>(payload)
        else -> null
    }
```

### SERVICE_PATTERN (Outcome → Exception, Logging, Zustellung nach Commit)
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/DishReviewService.kt:9-34
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
`BoardService` ruft nach erfolgreicher Abnahme `events.dispatchPending()` (`EventNotifier` aus `core`) — genauso nach der
Kochbestätigung.

### REPOSITORY_PATTERN (bedingtes Update + Ereignis im selben `dbQuery`)
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt (transition)
dbQuery(db) {
    val changed = TaskCardsTable.update({ (TaskCardsTable.id eq id.value) and (TaskCardsTable.status inList transition.from().map { it.name }) }) {
        ...
    }
    if (changed == 0) return@dbQuery if (exists(id)) TransitionOutcome.WRONG_STATUS else TransitionOutcome.NOT_FOUND
    if (transition == ACCEPT) recordEvent(acceptedEvent(id, by, at), at)
    TransitionOutcome.CHANGED
}
```
Zeitstempel als Epoch-Millis (`long`), Datumswerte als ISO-Text (`meal_date`).

### SETTINGS_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/DeadlineSettingsService.kt:20
suspend fun effectiveFor(week: PlanWeek): DeadlineSettings = repository.findEffective(week) ?: DeadlineSettings.defaults(week)
```
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedDeadlineSettingsRepository.kt:44-56
dbQuery(db) {
    MealDeadlineSettingsTable.upsert {
        it[effectiveFrom] = settings.effectiveFrom.monday.toString()
        ...
        it[updatedAt] = at.toEpochMilli()
    }
}
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt:30-35
authenticate(AUTH_DEVICE) {
    post("/api/v1/meals/dishes/{dishId}/approval") {
        call.requireParent()
        reviews.approve(DishId(call.uuidParameter("dishId")))
        call.respond(HttpStatusCode.NoContent)
    }
```
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt:130-133
private fun ApplicationCall.historyLimit(): Int {
    val raw = request.queryParameters["limit"] ?: return HISTORY_LIMIT_DEFAULT
    return raw.toIntOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'limit'")
```
Generierte Modelle aus `de.familyplanner.api.model`; Mapper `toApi()` in `web/*ApiMappers.kt`; Instants per `toString()`.

### KOIN_PATTERN
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/TasksModule.kt:12-19
/** `Database`, `MemberRepository` and `EventNotifier` come from `coreModule`. */
fun tasksModule(clock: Clock) =
    module {
        single<CatalogRepository> { ExposedCatalogRepository(get()) }
        ...
        single { BoardService(get(), get(), get(), get(), clock) }
    }
```

### MIGRATION_PATTERN
```sql
-- SOURCE: backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql:1-5
-- Phase 4: children submit dishes, parents approve or reject. Existing dishes were planned by parents and stay approved.
ALTER TABLE meal_dishes ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'
  CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'));
ALTER TABLE meal_dishes ADD COLUMN rejection_reason TEXT;
```

### LOGGING_PATTERN
`log.info("Dish approved: {}", id.value)` — nur IDs, Arten, Beträge; nie Titel, Begründungen, Token. Fehlerhafte/übersprungene
Ereignisse `log.warn("… seq {} …", …)`.

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/tasks/src/test/kotlin/de/familyplanner/tasks/fakes/TasksFixture.kt:15-33
class TasksFixture(start: Instant = Instant.parse("2026-10-24T10:00:00Z")) {
    val clock = MutableClock(start)
    val members = InMemoryMemberRepository()
    ...
    val notifier = CountingEventNotifier()
    suspend fun parent(): Actor = Actor(members.create(aMember(role = Role.PARENT)).id, Role.PARENT)
    suspend fun child(name: String = "Kind-${++nextChild}"): Actor =
        Actor(members.create(aMember(name = name, role = Role.CHILD)).id, Role.CHILD)
}
```
Repo-Tests: `@TempDir dir`, `db = DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))`, Mitglieder über
`ExposedMemberRepository(db).create(aMember(...))`. API-Tests: `testApplication { application { module(testConfig(dir), FIXED_NOW) } }`,
`client.call(method, path, token, body)` prüft gegen den Vertrag; `FIXED_NOW` = Mi 2026-10-14 10:00 UTC.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | Kochbestätigung, `Dish.cookedAt`, Tag `gamification`, 5 Operationen, Schemas; `info.version` 0.7.0 |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEvents.kt` | UPDATE | `CookingConfirmed` |
| `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEventCodec.kt` | UPDATE | `cooking.confirmed.v1` |
| `backend/core/src/test/kotlin/de/familyplanner/core/events/DomainEventCodecTest.kt` | UPDATE | Round-Trip `CookingConfirmed` |
| `backend/meals/src/main/resources/db/migration/V9__meals_cooking_confirmation.sql` | CREATE | `cooked_at`, `cooked_by` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | UPDATE | Spalten |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | UPDATE | `Dish.cookedAt`, `CookOutcome` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanRepository.kt` | UPDATE | `confirmCooked(...)` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | UPDATE | `confirmCooked` + Ereignis, `toDish` liest `cookedAt` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/CookingConfirmationService.kt` | CREATE | REQ-MEAL-034 |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATE | Service registrieren |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt` | UPDATE | Route `cooking-confirmation` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanApiMappers.kt` | UPDATE | `cookedAt` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryMealPlanRepository.kt` | UPDATE | `confirmCooked` + `recordedEvents` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/CookingConfirmationServiceTest.kt` | CREATE | Unit |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepositoryTest.kt` | UPDATE | Ereignis atomar, parallel, V9 |
| `backend/settings.gradle.kts` | UPDATE | `":gamification"` |
| `backend/gamification/build.gradle.kts` | CREATE | Kopie von `tasks/build.gradle.kts` |
| `backend/gamification/src/main/resources/db/migration/V10__gamification.sql` | CREATE | Journal + Einstellungen |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/GamificationModule.kt` | CREATE | Koin + Abonnentenname |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | CREATE | Modell, Arten, Konstanten |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerRepository.kt` | CREATE | Port |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/PointsEventHandler.kt` | CREATE | Ereignis → Gutschrift (REQ-GAME-010/011) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/Account.kt` | CREATE | `Actor`, `Account`, `LeaderboardEntry`, `rankedLeaderboard()` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt` | CREATE | REQ-GAME-012/013/014 |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/settings/GameSettings.kt` | CREATE | Port + Konstanten |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/settings/GameSettingsService.kt` | CREATE | REQ-GAME-011 Einstellung |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/GamificationTables.kt` | CREATE | Exposed-Tabellen |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepository.kt` | CREATE | Adapter |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedGameSettingsRepository.kt` | CREATE | Adapter |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt` | CREATE | Routen |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationApiMappers.kt` | CREATE | Mapping |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/InMemoryGamificationRepositories.kt` | CREATE | Fakes |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/GamificationFixture.kt` | CREATE | Fixture |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/ledger/PointsEventHandlerTest.kt` | CREATE | Unit |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/account/LeaderboardTest.kt` | CREATE | Unit (rein) |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/account/AccountServiceTest.kt` | CREATE | Unit |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/settings/GameSettingsServiceTest.kt` | CREATE | Unit |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepositoryTest.kt` | CREATE | Integration inkl. Idempotenz, Summen, CHECKs |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/PointsSubscriptionTest.kt` | CREATE | Integration: Dispatcher + Handler + echte DB, Nachholen, doppelte Zustellung |
| `backend/app/build.gradle.kts` | UPDATE | `implementation`/`kover(project(":gamification"))` |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | Modul, Abonnement, Routen, `cookingRoutes`-Parameter |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsCookingConfirmationApiTest.kt` | CREATE | API REQ-MEAL-034 |
| `backend/app/src/test/kotlin/de/familyplanner/app/GamificationApiTest.kt` | CREATE | API REQ-GAME-010–014 end-to-end |
| `backend/README.md` | UPDATE | Modultabelle `gamification` |
| `docs/03-design/gamification.md` | CREATE | Moduldesign |
| `docs/03-design/meals.md` | UPDATE | Abschnitt Kochbestätigung (REQ-MEAL-034) |
| `docs/03-design/core.md` | UPDATE | Ereignistyp `cooking.confirmed.v1`, erster Abonnent |
| `docs/04-verification/traceability.md` | UPDATE | REQ-GAME-010–014, REQ-MEAL-034 |
| `docs/01-requirements/gamification.prd.md` | UPDATE | Open Questions als entschieden markieren, Bericht-Verweis |

## NOT Building

- Kein Android-Code (Phase 1b): kein Bereich „Ich“, keine Rangliste in der App, keine Kochbestätigung im Essensplan-UI, kein Offline-Cache.
- Keine Badges (Phase 2), keine Belohnungen/Sparziele (Phase 3), kein Avatar/Shop (Phase 4) — nur Enum-Werte `SHOP`, `REWARD`, `REWARD_REFUND` werden vorgesehen.
- Keine Prüfung „Guthaben darf nicht negativ werden“ (betrifft erst Käufe/Einlösungen); manuelle Abzüge dürfen ins Minus.
- Kein Zurücknehmen einer Kochbestätigung, keine Sperre für Bearbeiten/Löschen eines gekochten Gerichts (die Buchung bleibt im Journal; Korrektur per manueller Gegenbuchung).
- Keine Rangliste je Woche/Monat (Could), keine von Eltern einstellbaren Punkte pro Aufgabe außer über den Katalog.
- Kein Zwischenspeichern der Summen, keine Seitenblätterung im Verlauf, kein Ändern/Löschen von Buchungen.
- Keine Hinweise/Benachrichtigungen.
- Kein gemeinsames `Actor` in `core`.

---

## Step-by-Step Tasks

### Task 1: OpenAPI-Vertrag
- **ACTION**: `api/openapi.yaml` erweitern (zuerst, ADR-0004).
- **IMPLEMENT**:
  - `info.version: 0.7.0`; Tag `- name: gamification` nach `tasks`.
  - Pfad `/api/v1/meals/dishes/{dishId}/cooking-confirmation` (`post`, `operationId: confirmDishCooked`, `tags: [meals]`,
    `summary: Kindergericht als gekocht bestätigen (nur Eltern, am oder nach dem Kochtag)`, Parameter `DishId`, `204`,
    `400/401/403/404/409/429`) — direkt nach `rejection`.
  - `Dish`: Eigenschaft `cookedAt: { type: string, format: date-time, nullable: true, description: Von einem Elternteil als gekocht bestätigt }` (nicht in `required`).
  - Gamification-Pfade (alle `tags: [gamification]`):
    - `GET /api/v1/gamification/leaderboard` → `getLeaderboard`, `200 LeaderboardResponse`, `401/429`.
    - `GET /api/v1/gamification/members/{memberId}/account` → `getPointsAccount`, Parameter `MemberId`, `HistoryLimit`, `200 PointsAccountResponse`, `400/401/403/404/429`.
    - `POST /api/v1/gamification/members/{memberId}/entries` → `bookPoints`, Body `ManualEntryRequest`, `201 LedgerEntryResponse`, `400/401/403/404/429`.
    - `GET /api/v1/gamification/settings` → `getGameSettings`, `200 GameSettingsResponse`, `401/429`.
    - `PUT /api/v1/gamification/settings` → `updateGameSettings`, Body `GameSettings`, `200 GameSettingsResponse`, `400/401/403/429`.
  - Schemas:
    - `LedgerEntryKind: { type: string, enum: [TASK, COOKING, MANUAL, SHOP, REWARD, REWARD_REFUND] }`
    - `LedgerEntry`: `required [id, kind, amount, title, bookedAt]`; `id` uuid, `kind`, `amount` integer, `title` string, `note` (string, nullable), `bookedAt` date-time.
    - `PointsAccount`: `required [memberId, collected, balance, entries]`; `collected`, `balance` integer; `entries` array `LedgerEntry`.
    - `LeaderboardEntry`: `required [memberId, name, rank, collected]`; `rank` integer minimum 1.
    - `Leaderboard`: `required [entries]`.
    - `ManualEntryRequest`: `required [amount, reason]`; `amount` integer −1000..1000 (description „≠ 0“), `reason` string 1–200.
    - `GameSettings`: `required [cookingPoints]`; `cookingPoints` integer 1–20.
    - Envelopes nach Muster `TaskCardResponse`: `LedgerEntryResponse`, `PointsAccountResponse`, `LeaderboardResponse`, `GameSettingsResponse`.
- **MIRROR**: `approveDish` (638-654), `getTaskHistory` (792-809), Envelope `CatalogTaskResponse`.
- **GOTCHA**: `dateLibrary=string` → `date-time` ist `String`; im Mapper `Instant.toString()`. Nur additiv: bestehende Felder
  nicht ändern. `bookedAt` von nachgeholten Buchungen kann in der Vergangenheit liegen — kein Problem.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:openApiGenerate`; `cd android && ./gradlew :core:api:compileDebugKotlin`.

### Task 2: Ereignistyp `CookingConfirmed` in `core`
- **ACTION**: `DomainEvents.kt`, `DomainEventCodec.kt`, Test.
- **IMPLEMENT**:
  ```kotlin
  /** A parent confirmed that a child cooked its approved dish (REQ-MEAL-034); GAME credits cooking points once per [dishId]. */
  @Serializable
  data class CookingConfirmed(
      val dishId: String,
      val memberId: String,
      val dishName: String,
      val date: String,
      val confirmedBy: String,
      val confirmedAt: Long,
  ) : DomainEvent
  ```
  Codec: `const val COOKING_CONFIRMED = "cooking.confirmed.v1"`, je ein Zweig in `encode`/`decode`.
- **MIRROR**: EVENT_TYPE.
- **GOTCHA**: `encode` ist ein exhaustives `when` über das sealed Interface — Compiler erzwingt den neuen Zweig. `date` als ISO-Text (`LocalDate.toString()`).
- **VALIDATE**: `DomainEventCodecTest` um `CookingConfirmed round-trips` ergänzen; `./gradlew :core:test`.

### Task 3: Kochbestätigung in `meals` (REQ-MEAL-034)
- **ACTION**: Migration, Tabelle, Modell, Port, Adapter, Service, Route, Mapper, Fake.
- **IMPLEMENT**:
  - `V9__meals_cooking_confirmation.sql`:
    ```sql
    -- GAME phase 1: a parent confirms that a child cooked its approved dish (REQ-MEAL-034); this triggers cooking points.
    ALTER TABLE meal_dishes ADD COLUMN cooked_at INTEGER;
    ALTER TABLE meal_dishes ADD COLUMN cooked_by TEXT REFERENCES members(id);
    ```
  - `PlanTables.kt`: `val cookedAt = long("cooked_at").nullable()`, `val cookedBy = varchar("cooked_by", ID_LENGTH).nullable()`.
  - `Plan.kt`: `Dish` um `val cookedAt: Instant? = null` (letzter Parameter, Default) ergänzen;
    `enum class CookOutcome { CONFIRMED, NOT_FOUND, NOT_APPROVED, TOO_EARLY, ALREADY_COOKED }`.
  - `MealPlanRepository`:
    ```kotlin
    /**
     * Atomically marks the dish cooked if it is APPROVED, not cooked yet and dated on or before [today];
     * on success records [CookingConfirmed] in the same transaction.
     */
    suspend fun confirmCooked(id: DishId, by: MemberId, today: LocalDate, at: Instant): CookOutcome
    ```
  - `ExposedMealPlanRepository.confirmCooked`:
    ```kotlin
    dbQuery(db) {
        val changed = MealDishesTable.update({
            (MealDishesTable.id eq id.value) and (MealDishesTable.status eq DishStatus.APPROVED.name) and
                MealDishesTable.cookedAt.isNull() and (MealDishesTable.mealDate lessEq today.toString())
        }) {
            it[cookedAt] = at.toEpochMilli()
            it[cookedBy] = by.value
            it[updatedAt] = at.toEpochMilli()
        }
        if (changed == 0) return@dbQuery cookOutcomeOf(id)
        val row = MealDishesTable.selectAll().where { MealDishesTable.id eq id.value }.single()
        recordEvent(
            CookingConfirmed(id.value, row[MealDishesTable.cookMemberId], row[MealDishesTable.name], row[MealDishesTable.mealDate], by.value, at.toEpochMilli()),
            at,
        )
        CookOutcome.CONFIRMED
    }
    ```
    `private fun JdbcTransaction.cookOutcomeOf(id)`: Zeile fehlt → `NOT_FOUND`; `cookedAt != null` → `ALREADY_COOKED`;
    Status ≠ `APPROVED` → `NOT_APPROVED`; sonst `TOO_EARLY`. `toDish()` liest `cookedAt` (`Instant.ofEpochMilli`).
    `createIfDayFree`/`updateIfDayFree` lassen `cooked_at` unberührt (Insert: `NULL`).
  - `CookingConfirmationService(members: MemberRepository, plan: MealPlanRepository, events: EventNotifier, clock: Clock)`:
    ```kotlin
    suspend fun confirm(id: DishId, by: MemberId) {
        val dish = plan.findById(id) ?: throw AppException.NotFound("Gericht nicht gefunden")
        val cook = members.findById(dish.cookId)
        if (cook?.role != Role.CHILD) throw AppException.Validation("Nur Kindergerichte können als gekocht bestätigt werden")
        val today = LocalDate.ofInstant(clock.instant(), ZONE)
        requireConfirmed(plan.confirmCooked(id, by, today, clock.instant()))
        log.info("Dish cooked: {}", id.value)
        events.dispatchPending()
    }
    ```
    `requireConfirmed`: `NOT_FOUND` → NotFound „Gericht nicht gefunden“; `NOT_APPROVED` → Conflict „Das Gericht ist nicht freigegeben“;
    `TOO_EARLY` → Conflict „Das Gericht kann erst am Kochtag bestätigt werden“; `ALREADY_COOKED` → Conflict „Das Gericht ist schon als gekocht bestätigt“.
  - `MealsModule`: `single { CookingConfirmationService(get(), get(), get(), clock) }`.
  - `CookingRoutes`: Parameter `confirmations: CookingConfirmationService`; Route
    ```kotlin
    post("/api/v1/meals/dishes/{dishId}/cooking-confirmation") {
        val parent = call.requireParent()
        confirmations.confirm(DishId(call.uuidParameter("dishId")), parent.memberId)
        call.respond(HttpStatusCode.NoContent)
    }
    ```
  - `PlanApiMappers.Dish.toApi()`: `cookedAt = cookedAt?.toString()`.
  - Fake `InMemoryMealPlanRepository`: `confirmCooked` mit gleicher Logik auf der Map, hängt `CookingConfirmed` an `val recordedEvents = mutableListOf<CookingConfirmed>()`.
- **MIRROR**: REPOSITORY_PATTERN, SERVICE_PATTERN, ROUTE_PATTERN, MIGRATION_PATTERN.
- **IMPORTS**: `de.familyplanner.core.events.CookingConfirmed`, `…recordEvent`, `…EventNotifier`, `de.familyplanner.meals.attendance.ZONE`,
  `org.jetbrains.exposed.v1.core.isNull`, `…lessEq`, `…and`, `…eq`.
- **GOTCHA**:
  - `meal_date` ist ISO-Text → `lessEq today.toString()` ist korrekt (siehe KDoc `findBetween`).
  - „Heute“ in `Europe/Berlin`, nicht UTC (`ZONE` aus `Attendance.kt`).
  - Der Rollencheck des Kochs passiert vor dem atomaren Update; Rollen ändern sich nicht → ok.
  - Parallele Doppelbestätigung: zweites `UPDATE … cooked_at IS NULL` trifft 0 Zeilen → genau ein Ereignis. Test mit zwei `async`.
  - `Application.kt` muss den neuen `cookingRoutes`-Parameter übergeben (Task 7).
- **VALIDATE**: `CookingConfirmationServiceTest`, `ExposedMealPlanRepositoryTest`, `./gradlew :meals:test`.

### Task 4: Modul `gamification` anlegen
- **ACTION**: Gradle-Modul, Migration, Tabellen, Koin.
- **IMPLEMENT**:
  - `settings.gradle.kts`: `include(":api-contract", ":core", ":meals", ":tasks", ":gamification", ":app")`.
  - `gamification/build.gradle.kts` = Kopie von `tasks/build.gradle.kts`.
  - `V10__gamification.sql`:
    ```sql
    -- GAME phase 1: immutable points ledger per child. Corrections are counter-entries, never updates.
    -- source_key makes credits from events idempotent (task:<cardId>, cooking:<dishId>); NULL for manual entries.
    CREATE TABLE game_ledger (
      seq INTEGER PRIMARY KEY AUTOINCREMENT,
      id TEXT NOT NULL UNIQUE,
      member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
      kind TEXT NOT NULL CHECK (kind IN ('TASK','COOKING','MANUAL','SHOP','REWARD','REWARD_REFUND')),
      amount INTEGER NOT NULL CHECK (amount <> 0),
      title TEXT NOT NULL,
      note TEXT,
      source_key TEXT UNIQUE,
      created_by TEXT REFERENCES members(id),
      booked_at INTEGER NOT NULL
    );
    CREATE INDEX idx_game_ledger_member ON game_ledger(member_id, booked_at);

    -- Single row of family-wide game settings; missing row means defaults.
    CREATE TABLE game_settings (
      id INTEGER PRIMARY KEY CHECK (id = 1),
      cooking_points INTEGER NOT NULL CHECK (cooking_points BETWEEN 1 AND 20),
      updated_at INTEGER NOT NULL
    );
    ```
  - `persistence/GamificationTables.kt`: `object LedgerTable : Table("game_ledger")` (`seq = long("seq").autoIncrement()`, PK `seq`; `id`, `memberId`, `kind`, `amount = integer`, `title`, `note.nullable()`, `sourceKey.nullable()`, `createdBy.nullable()`, `bookedAt = long`), `object GameSettingsTable : Table("game_settings")` (`id = integer`, PK; `cookingPoints`, `updatedAt`). Längenkonstanten wie `PlanTables.kt`.
  - `GamificationModule.kt`:
    ```kotlin
    /** Stable cursor name of the points subscriber (ADR-0016) — never rename. */
    const val POINTS_SUBSCRIBER = "gamification.points"

    /** `Database` and `MemberRepository` come from `coreModule`. */
    fun gamificationModule(clock: Clock) =
        module {
            single<LedgerRepository> { ExposedLedgerRepository(get()) }
            single<GameSettingsRepository> { ExposedGameSettingsRepository(get()) }
            single { GameSettingsService(get(), clock) }
            single { AccountService(get(), get(), clock) }
            single { PointsEventHandler(get(), get(), get()) }
        }
    ```
- **MIRROR**: KOIN_PATTERN, MIGRATION_PATTERN.
- **GOTCHA**: `POINTS_SUBSCRIBER` steht schon so im Board-Plan (Notes) — exakt diesen Namen verwenden.
- **VALIDATE**: `./gradlew :gamification:compileKotlin`.

### Task 5: Journal-Modell und Adapter
- **ACTION**: `ledger/LedgerEntry.kt`, `ledger/LedgerRepository.kt`, `persistence/ExposedLedgerRepository.kt`.
- **IMPLEMENT**:
  - ```kotlin
    @JvmInline value class LedgerEntryId(val value: String)

    enum class EntryKind(val countsAsCollected: Boolean) {
        TASK(true), COOKING(true), MANUAL(true), SHOP(false), REWARD(false), REWARD_REFUND(false),
    }

    /** One immutable booking; [sourceKey] is set for credits from events and makes them idempotent. */
    data class LedgerEntry(
        val id: LedgerEntryId, val memberId: MemberId, val kind: EntryKind, val amount: Int, val title: String,
        val note: String?, val sourceKey: String?, val createdBy: MemberId?, val bookedAt: Instant,
    )

    /** Collected only grows with earned points (and manual corrections); balance is the sum of everything. */
    data class Totals(val collected: Int, val balance: Int) { companion object { val ZERO = Totals(0, 0) } }

    const val MANUAL_AMOUNT_MAX = 1000
    const val REASON_MAX = 200
    const val ENTRIES_LIMIT_DEFAULT = 50
    const val ENTRIES_LIMIT_MAX = 100
    fun taskSourceKey(cardId: String) = "task:$cardId"
    fun cookingSourceKey(dishId: String) = "cooking:$dishId"
    ```
  - `LedgerRepository`:
    ```kotlin
    /** Appends [entry]; returns false (and stores nothing) if an entry with the same non-null sourceKey exists. */
    suspend fun append(entry: LedgerEntry): Boolean
    suspend fun totalsOf(memberId: MemberId): Totals
    /** Totals of every member that has at least one entry. */
    suspend fun allTotals(): Map<MemberId, Totals>
    /** Newest first (bookedAt, then insertion order). */
    suspend fun entriesOf(memberId: MemberId, limit: Int): List<LedgerEntry>
    ```
  - Adapter: `append` per `LedgerTable.insertIgnore { … }.insertedCount > 0`. Summen per SQL:
    `amount.sum()` gesamt und `Case().When(kind inList COLLECTED, amount).Else(intLiteral(0))`-Summe — **oder einfacher**: alle
    `(kind, amount)` des Mitglieds laden und in Kotlin summieren (`totalsFrom(rows)` als reine Funktion in `LedgerEntry.kt`,
    vom Fake mitbenutzt). Familienvolumen (wenige Tausend Zeilen) → **die einfache Variante wählen**.
    `entriesOf`: `orderBy(bookedAt DESC, seq DESC).limit(limit)`.
- **MIRROR**: REPOSITORY_PATTERN, NAMING_CONVENTION.
- **IMPORTS**: `org.jetbrains.exposed.v1.jdbc.insertIgnore`, `…selectAll`, `org.jetbrains.exposed.v1.core.SortOrder`, `…eq`.
- **GOTCHA**:
  - Falls `insertIgnore` in Exposed 1.5.1 nicht wie erwartet `insertedCount` liefert: im selben `dbQuery` erst
    `selectAll().where { sourceKey eq … }.any()` prüfen, dann `insert` — sicher, weil der Dispatcher-Mutex Zustellungen serialisiert
    und manuelle Buchungen keinen `sourceKey` haben; UNIQUE bleibt als Netz.
  - SQLite `OR IGNORE` überspringt auch **CHECK**- und NOT-NULL-Verletzungen still (nicht aber Fremdschlüssel, die werfen weiter).
    Deshalb: Werte vor `append` validieren (Betrag ≠ 0 kommt aus Service/Ereignis), und der Handler prüft das Mitglied vorher (Task 6).
    Der Test „V10 enforces kind and non-zero amount“ nutzt ein normales `insert`, nicht `append`.
  - `seq` ist interne Reihenfolge, nach außen nur `id` (UUID).
- **VALIDATE**: `ExposedLedgerRepositoryTest`.

### Task 6: Ereignis-Abonnent (REQ-GAME-010/011)
- **ACTION**: `ledger/PointsEventHandler.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Turns accepted tasks and confirmed cooking into credits; safe to call twice for the same event (ADR-0016). */
  class PointsEventHandler(
      private val members: MemberRepository,
      private val ledger: LedgerRepository,
      private val settings: GameSettingsService,
      private val newId: () -> String = { UUID.randomUUID().toString() },
  ) {
      suspend fun handle(stored: StoredEvent) {
          val credit = when (val event = stored.event) {
              is TaskAccepted -> Credit(event.memberId, EntryKind.TASK, event.points, event.title, taskSourceKey(event.cardId), event.acceptedBy, event.acceptedAt)
              is CookingConfirmed -> Credit(event.memberId, EntryKind.COOKING, settings.current().cookingPoints, event.dishName, cookingSourceKey(event.dishId), event.confirmedBy, event.confirmedAt)
              null -> return
          }
          book(stored.seq, credit)
      }
  }
  ```
  `book`: `members.findById(MemberId(credit.memberId))` — null oder nicht `CHILD` → `log.warn("Skipping points for seq {}: member {} is not a child", …)`
  und `return`; sonst `ledger.append(LedgerEntry(...))`; `true` → `log.info("Points credited: {} {} to {}", kind, amount, memberId)`,
  `false` → `log.debug("Points already credited for seq {}", seq)`. `Credit` ist eine private `data class`.
- **MIRROR**: SERVICE_PATTERN, LOGGING_PATTERN.
- **GOTCHA**:
  - `when` über `stored.event` (`DomainEvent?`) — exhaustiv mit `null`-Zweig, **kein** `else` (Kotlin-Regel): ein späterer neuer Ereignistyp
    erzwingt dann eine Entscheidung hier.
  - Der Handler darf für dauerhaft ungültige Daten nicht werfen (sonst Stau). Echte Fehler (DB weg) werfen und werden wiederholt.
  - Kochpunkte = Wert zum Verarbeitungszeitpunkt (Entscheidungstabelle).
- **VALIDATE**: `PointsEventHandlerTest`, `PointsSubscriptionTest`.

### Task 7: Konto, Rangliste, manuelle Buchung, Einstellungen (REQ-GAME-011–014)
- **ACTION**: `account/Account.kt`, `account/AccountService.kt`, `settings/GameSettings.kt`, `settings/GameSettingsService.kt`, `persistence/ExposedGameSettingsRepository.kt`.
- **IMPLEMENT**:
  - `Account.kt`:
    ```kotlin
    data class Actor(val id: MemberId, val role: Role) { val isParent: Boolean get() = role == Role.PARENT }
    data class Account(val memberId: MemberId, val totals: Totals, val entries: List<LedgerEntry>)
    data class LeaderboardEntry(val memberId: MemberId, val name: String, val rank: Int, val collected: Int)

    /** Highest collected first; ties share a rank (1, 1, 3) and are ordered by name (REQ-GAME-014). */
    fun rankedLeaderboard(children: List<Member>, totals: Map<MemberId, Totals>): List<LeaderboardEntry>
    ```
  - `AccountService(members: MemberRepository, ledger: LedgerRepository, clock: Clock, newId)`:
    - `leaderboard()`: Kinder aus `members.findAll()` (`role == CHILD`) + `ledger.allTotals()` → `rankedLeaderboard`.
    - `account(actor, memberId, limit)`: `limit` 1–100 sonst `Validation("Ungültiger Wert für 'limit'")`; `requireChild(memberId)`;
      Kind ≠ eigenes → `Forbidden("Nur eigenes Punktekonto")`; Rückgabe `Account(memberId, ledger.totalsOf(id), ledger.entriesOf(id, limit))`.
      Reihenfolge: erst Mitglied prüfen (404/400), dann Rechte (403) — wie `BoardService` (erst NotFound, dann Forbidden).
    - `book(actor, memberId, amount, reason)` (Route garantiert Eltern; Service prüft `actor.isParent` zusätzlich → Forbidden):
      `amount == 0` → `Validation("Der Betrag darf nicht 0 sein")`; `abs(amount) > MANUAL_AMOUNT_MAX` → `Validation("Der Betrag muss zwischen -1000 und 1000 liegen")`;
      `title = validatedName(reason, "Begründung", REASON_MAX)`; `requireChild`; `ledger.append(LedgerEntry(id, memberId, MANUAL, amount, title, null, null, actor.id, clock.instant()))`;
      `log.info("Manual points booked: {} to {}", amount, memberId)`; Buchung zurückgeben.
    - `private suspend fun requireChild(id)`: unbekannt → `NotFound("Mitglied nicht gefunden")`; Eltern → `Validation("Eltern sammeln keine Punkte")`.
  - `GameSettings.kt`: `data class GameSettings(val cookingPoints: Int)`, `const val COOKING_POINTS_DEFAULT = 3`, `COOKING_POINTS_MIN = 1`, `COOKING_POINTS_MAX = 20`;
    `interface GameSettingsRepository { suspend fun find(): GameSettings?; suspend fun save(settings: GameSettings, at: Instant) }`.
  - `GameSettingsService(repository, clock)`: `current() = repository.find() ?: GameSettings(COOKING_POINTS_DEFAULT)`;
    `update(cookingPoints)`: außerhalb 1–20 → `Validation("Kochpunkte müssen zwischen 1 und 20 liegen")`; speichern; `log.info("Cooking points set to {}", n)`.
  - `ExposedGameSettingsRepository`: `upsert` mit `id = 1` (SETTINGS-Muster).
- **MIRROR**: SERVICE_PATTERN, SETTINGS_PATTERN.
- **GOTCHA**: `allTotals()` enthält Kinder ohne Buchungen nicht → `Totals.ZERO` als Fallback. Eltern mit Buchungen kann es nicht geben
  (Handler + `book` prüfen `CHILD`), Rangliste filtert trotzdem nach Rolle.
- **VALIDATE**: `AccountServiceTest`, `LeaderboardTest`, `GameSettingsServiceTest`.

### Task 8: Routen, Mapper, App-Verdrahtung
- **ACTION**: `web/GamificationRoutes.kt`, `web/GamificationApiMappers.kt`, `app/build.gradle.kts`, `Application.kt`.
- **IMPLEMENT**:
  - `fun Route.gamificationRoutes(accounts: AccountService, settings: GameSettingsService)` in `authenticate(AUTH_DEVICE)`:
    `GET leaderboard` (`principalOrThrow()`), `GET members/{memberId}/account` (`toActor()`, `entriesLimit()` nach `historyLimit()`-Muster),
    `POST members/{memberId}/entries` (`requireParent()`, `receive<ManualEntryRequest>()`, `respond(Created, LedgerEntryResponse(true, entry.toApi()))`),
    `GET settings` (`principalOrThrow()`), `PUT settings` (`requireParent()`, `receive<ApiGameSettings>()`).
  - Mapper: `LedgerEntry.toApi()` (`kind = LedgerEntryKind.valueOf(kind.name)`, `bookedAt.toString()`), `Account.toApi()`, `LeaderboardEntry.toApi()`,
    `GameSettings.toApi()`, `DevicePrincipal.toActor()`. Namenskonflikte generiertes `GameSettings`/`LedgerEntry` ↔ Domäne per
    Import-Alias lösen (`import de.familyplanner.api.model.GameSettings as ApiGameSettings`, wie `PlanApiMappers.kt`).
  - `app/build.gradle.kts`: `implementation(project(":gamification"))`, `kover(project(":gamification"))`.
  - `Application.module`: `modules(..., tasksModule(clock), gamificationModule(clock))`; **direkt nach** `install(Koin)`:
    `get<DomainEventDispatcher>().subscribe(POINTS_SUBSCRIBER) { get<PointsEventHandler>().handle(it) }`;
    `cookingRoutes(get<DishReviewService>(), get<CookingWeekService>(), get<CookingConfirmationService>())`;
    `gamificationRoutes(get<AccountService>(), get<GameSettingsService>())`.
- **MIRROR**: ROUTE_PATTERN.
- **GOTCHA**:
  - Abonnieren **vor** dem `ApplicationStarted`-Nachlauf und vor dem ersten Request, sonst bucht der erste Request nicht sofort.
  - Handler-Referenz einmal holen (`val handler = get<PointsEventHandler>()`) statt `get()` im Lambda.
  - Kover 80 % aggregiert in `:app` — Mapper werden durch API-Tests abgedeckt.
- **VALIDATE**: API-Tests (Task 9), `./gradlew :app:test`.

### Task 9: Tests
- **ACTION**: Fakes, Fixture und Tests nach Testing Strategy.
- **IMPLEMENT**: `InMemoryLedgerRepository` (Liste, `append` respektiert `sourceKey`, Summen über `totalsFrom`), `InMemoryGameSettingsRepository`;
  `GamificationFixture` (MutableClock, `InMemoryMemberRepository`, `parent()`/`child(name)` liefern `Actor`, `AccountService`, `GameSettingsService`,
  `PointsEventHandler` mit `newId`-Zähler, Helfer `stored(event, seq)` → `StoredEvent`). `gamification/build.gradle.kts` hat
  `testImplementation(testFixtures(project(":core")))`.
- **GOTCHA**: API-Test für REQ-MEAL-034 braucht ein **freigegebenes Kindergericht am oder vor Mi 2026-10-14** (`FIXED_NOW`): Kind per
  `PUT /api/v1/meals/profiles/{id}` mit `ALWAYS_COOKS` als Koch einrichten (wie `MealsSubmissionApiTest.makeCook`), dann als **Eltern**
  `POST /api/v1/meals/dishes` mit `date = 2026-10-13` (laufende Woche; Eltern dürfen vergangene Wochen schreiben → `APPROVED`).
  „Zu früh“: Gericht am `2026-10-15`. Prüfen, dass das Anwesenheitsmuster auch die Woche ab 2026-10-12 abdeckt; falls nicht, Datum der
  Woche nehmen, die `makeCook` abdeckt, und die Uhr im Test entsprechend wählen (`module(testConfig(dir), clock)` nimmt jede `Clock`).
  Hilfsfunktionen (`makeCook`, `dishJson`, `ALWAYS_COOKS`) bei Bedarf aus `MealsSubmissionApiTest` in `ApiTestSupport.kt` hochziehen
  statt kopieren.
- **VALIDATE**: `./gradlew :core:test :meals:test :gamification:test :app:test`.

### Task 10: Doku, Traceability, PRD
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - `docs/03-design/gamification.md` nach Aufbau von `tasks.md`: Abgedeckte Anforderungen; Domänenmodell & Regeln (Journal, Arten-Tabelle
    mit „zählt für Gesammelt“, Idempotenz-Schlüssel, Abonnent `gamification.points`, Kochpunktwert, Rangliste mit Rangregel, Sichtbarkeit);
    Schnittstellen; DB-Tabellen; Eingabegrenzen; Unit-Testfälle. Hinweis, dass die PRD-Notiz „Gutschrift in derselben Transaktion“
    durch ADR-0016 (Ereignis atomar, Gutschrift mindestens einmal + idempotent) ersetzt ist.
  - `docs/03-design/meals.md`: Abschnitt „Kochbestätigung (REQ-MEAL-034)“ (Bedingungen, Outcomes → HTTP, Ereignis).
  - `docs/03-design/core.md` „Domänenereignisse“: Typ `cooking.confirmed.v1`, erster Abonnent `gamification.points`.
  - `docs/04-verification/traceability.md`: Zeilen REQ-MEAL-034 und REQ-GAME-010–014, Status „umgesetzt (Backend)“.
  - `backend/README.md`: Zeile `gamification` (Design: `docs/03-design/gamification.md`).
  - PRD: Open Questions „Rangliste“, „Punkte fürs Kochen“, „Startpunkte“ abhaken (Entscheidung wie Vorschlag, Datum 2026-10-25); Decisions Log ergänzen; Phase-1-Zeile um Bericht-Verweis 1a.
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `DomainEventCodec round-trips CookingConfirmed` | | gleich | |
| `REQ-MEAL-034 parent confirms approved child dish on its day` (`CookingConfirmationServiceTest`) | APPROVED, Datum = heute | `cookedAt` gesetzt, 1 Ereignis, `dispatchPending` 1× | |
| `REQ-MEAL-034 confirming after the cooking day is allowed` | Datum gestern | bestätigt | |
| `REQ-MEAL-034 confirming before the cooking day is a conflict` | Datum morgen | `Conflict`, kein Ereignis | ja |
| `REQ-MEAL-034 submitted or rejected dish cannot be confirmed` | SUBMITTED / REJECTED | `Conflict` | ja |
| `REQ-MEAL-034 parent dish cannot be confirmed` | Koch = Eltern | `Validation` | ja |
| `REQ-MEAL-034 second confirmation is a conflict` | 2× | `Conflict`, 1 Ereignis | ja |
| `REQ-MEAL-034 unknown dish is not found` | | `NotFound` | ja |
| `REQ-MEAL-034 today is taken in Europe/Berlin` | Uhr 2026-10-13T23:30Z (= 14.10. Berlin), Gericht 14.10. | bestätigt | ja |
| `REQ-GAME-010 accepted task credits its points` (`PointsEventHandlerTest`) | `TaskAccepted(points=2)` | 1 Buchung TASK +2, Titel = Kartentitel, `bookedAt` = `acceptedAt` | |
| `REQ-GAME-010 same task event twice credits once` | gleiches Ereignis 2× | 1 Buchung | ja |
| `REQ-GAME-011 confirmed cooking credits the cooking points` | Standard | COOKING +3 | |
| `REQ-GAME-011 changed cooking points apply to later confirmations` | Wert 5, dann Ereignis | +5; frühere Buchung bleibt +3 | |
| `REQ-GAME-010 event for unknown or parent member is skipped` | | keine Buchung, keine Exception | ja |
| `unknown event type is ignored` | `StoredEvent(event = null)` | keine Buchung | ja |
| `REQ-GAME-012 child sees own collected, balance and entries newest first` (`AccountServiceTest`) | TASK +2, COOKING +3, MANUAL −1 | Gesammelt 4, Guthaben 4, Reihenfolge | |
| `REQ-GAME-012 collected ignores spending kinds` | Fake mit SHOP −5 | Gesammelt unverändert, Guthaben −5 | ja |
| `REQ-GAME-012 child cannot see a sibling's account` | | `Forbidden` | |
| `REQ-GAME-012 parent sees every child's account; parent member is rejected; limit 1–100` | | ok / `Validation` / `Validation` | ja |
| `REQ-GAME-013 parent books +12 from the kitchen board` | +12 „Übernahme Küchenboard“ | MANUAL +12, Gesammelt/Guthaben +12 | |
| `REQ-GAME-013 negative correction lowers both values and may go below zero` | −2 bei Stand 0 | −2 / −2 | ja |
| `REQ-GAME-013 amount 0, over 1000 or empty reason is rejected` | | `Validation` | ja |
| `REQ-GAME-013 child cannot book` | Kind-Actor | `Forbidden` | |
| `REQ-GAME-014 leaderboard sorts by collected, ties share a rank` (`LeaderboardTest`) | 10, 10, 4 | Ränge 1, 1, 3; Gleichstand nach Name | ja |
| `REQ-GAME-014 child without entries is listed with zero` | | collected 0, letzter Rang | ja |
| `REQ-GAME-014 spending does not change the position` | SHOP-Buchung im Fake | Rang unverändert | |
| `REQ-GAME-011 cooking points default to 3 and must be 1–20` (`GameSettingsServiceTest`) | — / 0 / 21 / 5 | 3 / `Validation` / `Validation` / 5 | ja |

### Integration Tests

| Test | Setup | Expected |
|---|---|---|
| `ExposedMealPlanRepositoryTest`: `REQ-MEAL-034 confirming writes cooked_at and the event atomically` | Temp-DB, APPROVED-Kindergericht | `cooked_at` gesetzt, eine Zeile `cooking.confirmed.v1` mit `memberId` und `dishName` |
| `… concurrent confirmations record exactly one event` | 2× `async` | Outcomes {CONFIRMED, ALREADY_COOKED}; 1 Ereignis |
| `… outcomes for missing, pending and future dishes` | | NOT_FOUND / NOT_APPROVED / TOO_EARLY, kein Ereignis |
| `ExposedLedgerRepositoryTest`: `REQ-GAME-010 duplicate source key is ignored` | 2× `append` gleicher Key | `true`, `false`; 1 Zeile |
| `… manual entries without source key are all kept` | 2× MANUAL | 2 Zeilen |
| `… totals and entries newest first with limit` | gemischte Arten, gleiche `bookedAt` | Summen; Reihenfolge `bookedAt` desc, dann Einfügereihenfolge desc |
| `… migration V10 enforces kind and non-zero amount` | `kind='X'` / `amount=0` | SQL-Fehler |
| `PointsSubscriptionTest`: `REQ-GAME-010 events recorded before subscribing are credited once` | 2× `recordEvent(TaskAccepted)` per `dbQuery`, dann `subscribe(POINTS_SUBSCRIBER, handler::handle)` + 2× `dispatchPending` | 2 Buchungen, Cursor = 2 |
| `… redelivery after a lost cursor does not double credit` | Cursor in `core_event_cursors` auf 0 zurücksetzen, erneut zustellen | weiterhin 2 Buchungen |
| `MealsCookingConfirmationApiTest`: `REQ-MEAL-034 parent confirms cooked dish` | Koch-Kind, Elterngericht-Eintrag für 2026-10-13 | 204; Plan zeigt `cookedAt` |
| `… REQ-MEAL-034 child gets 403, future dish 409, second confirmation 409, unknown 404, bad id 400` | | wie angegeben |
| `GamificationApiTest`: `REQ-GAME-010 accepted task shows up in account and leaderboard` | Katalog 2 P., zuweisen, melden, abnehmen | Konto Kind: Gesammelt 2, Guthaben 2, 1 Buchung TASK; Rangliste Rang 1 |
| `… REQ-GAME-011 confirmed cooking credits cooking points` | Kochpunkte per PUT auf 4, Gericht bestätigen | COOKING +4 |
| `… REQ-GAME-013 parent books, child gets 403 on booking` | +12, −2 | 201; Konto 10/10; Kind 403 |
| `… REQ-GAME-012 child sees only own account` | zweites Kind | eigenes 200, fremdes 403, Eltern 200 |
| `… REQ-GAME-014 everyone sees the leaderboard` | Kind- und Eltern-Token | 200, sortiert |
| `… settings: child reads, only parents update, invalid value 400` | | 200 / 403 / 400 (`invalidRequestExpected = true`) |

### Edge Cases Checklist
- [x] Doppelte Zustellung, verlorener Cursor, Nachholen alter Ereignisse
- [x] Parallele Kochbestätigung (genau ein Ereignis)
- [x] Grenzwerte: Betrag ±1/±1000/0, Begründung 1/200, Kochpunkte 1/20, Limit 1/100
- [x] Mitglied unbekannt / Eltern als Ziel / fremdes Konto / falsche Rolle
- [x] Kochtag-Grenze in `Europe/Berlin`
- [x] Unbekannter Ereignistyp
- [x] Kind ohne Buchungen in der Rangliste, Gleichstand

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Funde (detekt `TooManyFunctions`/`ThrowsCount`: Hilfsfunktionen ggf. wie `tasks/board/BoardRules.kt` auslagern)

### Unit + Integration Tests
```bash
cd backend && ./gradlew :core:test :meals:test :gamification:test :app:test
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
EXPECT: grün (nur neue Modelle/Felder/Endpunkte)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=test-setup-token-123456 FP_DB_PATH=/tmp/fp-game.db FP_TLS_ENABLED=false ./gradlew :app:run
sqlite3 /tmp/fp-game.db "select version, description, success from flyway_schema_history order by installed_rank"
```
EXPECT: V1–V10 erfolgreich; `game_ledger`, `game_settings`, Spalten `meal_dishes.cooked_at/cooked_by` vorhanden.
Auf einer **Kopie** der Pi-Datenbank (aus `data/backups/`) und der Testinstanz-DB prüfen, dass V9/V10 laufen und beim Start
vorhandene `task.accepted.v1`-Ereignisse genau einmal gebucht werden (`select kind, amount, source_key from game_ledger`).

### Manual Validation (lokal gegen `:app:run`, curl)
- [ ] Familie einrichten, Kind anlegen und koppeln (wie `deploy/README.md`)
- [ ] Karte mit 2 Punkten zuweisen, melden, abnehmen → `GET …/members/{kind}/account` zeigt Gesammelt 2, Guthaben 2
- [ ] Kind als Koch einrichten, Eltern tragen Gericht für heute ein, `POST …/cooking-confirmation` → 204; Konto +3 COOKING
- [ ] Zweite Bestätigung → 409, weiterhin eine COOKING-Buchung
- [ ] `PUT /api/v1/gamification/settings {"cookingPoints":5}`, nächstes Gericht bestätigen → +5
- [ ] Manuelle Buchung +12 „Übernahme Küchenboard“, dann −2 → Konto 15/15 bzw. passend; Kind-Token bekommt 403
- [ ] Neustart des Backends → keine zusätzlichen Buchungen
- [ ] Nach Deploy: Familien-App (aktuelle Version) funktioniert unverändert; `/health` ok; Testinstanz zeigt die neuen Endpunkte

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-GAME-010–014 und REQ-MEAL-034 haben Tests mit REQ-ID im Namen (Unit + API)
- [ ] Kochbestätigung schreibt genau ein Ereignis `cooking.confirmed.v1` in derselben Transaktion; parallel → eines
- [ ] Jede Abnahme/Kochbestätigung ergibt genau eine Gutschrift, auch bei erneuter Zustellung und beim Nachholen
- [ ] Gesammelt/Guthaben nach PRD-Regel; Rangliste nach Gesammelt mit gemeinsamem Rang bei Gleichstand
- [ ] Rechte: Buchen, Einstellungen ändern, Kochen bestätigen nur Eltern; Konto nur eigenes Kind oder Eltern
- [ ] API nur additiv, Vertrag für alle neuen Endpunkte geprüft; Android baut
- [ ] `gamification.md`, `meals.md`, `core.md`, Traceability, README, PRD aktualisiert

## Completion Checklist
- [ ] Modul folgt ADR-0010 (Paket, Koin, Routen, Migration, Präfix `game_`, testFixtures)
- [ ] Fehler über `AppException` mit deutschen Texten
- [ ] Logging mit IDs/Arten/Beträgen, keine Titel/Begründungen/Token
- [ ] Tests nach Mustern (Fixture + Fakes, Temp-SQLite, `client.call` mit Vertragsprüfung)
- [ ] Keine Magic Numbers (Konstanten in `LedgerEntry.kt`, `GameSettings.kt`)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen
- [ ] Kein `!!`, exhaustives `when` ohne `else` über Ereignisse und Arten
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Doppelte Gutschrift bei erneuter Zustellung | M | H | UNIQUE `source_key` + `insertIgnore`; Test mit zurückgesetztem Cursor |
| Handler wirft dauerhaft → Ereignisstau (auch für spätere Abonnenten derselben Kette) | L | M | Ungültige Daten überspringen mit `warn`; nur echte Fehler werfen |
| Beim ersten Start werden alte Testabnahmen in der Familien-DB gebucht | M | L | Gewollt (ADR-0016); vor Deploy `select count(*) from core_domain_events` auf der Pi-DB-Kopie prüfen und Eltern informieren; Korrektur per manueller Gegenbuchung |
| Enum-Erweiterung später bricht alte App-Versionen | M | H | Alle sechs Arten jetzt im Vertrag |
| Kochtag-Vergleich in UTC statt Berlin | M | M | `ZONE` aus `meals`; Test um Mitternacht |
| Migrationsnummer kollidiert mit paralleler Arbeit | L | M | ADR-0010-Regel; derzeit keine andere Backend-Phase in Arbeit |
| Kover < 80 % durch neues Modul | M | M | Tests je Service/Adapter; Mapper über API-Tests |

## Notes
- Die App-Seite (Phase 1b) braucht: Konto (`getPointsAccount`) für „Ich“ inkl. Offline-Cache (ADR-0011), Rangliste, Eltern-Ansicht
  unter „Familie“ (Rangliste + Konto je Kind + manuelle Buchung + Kochpunkte), Knopf „gekocht“ im Essensplan für freigegebene
  Kindergerichte ab dem Kochtag (`Dish.cookedAt == null`).
- Spätere Phasen hängen sich an `LedgerRepository.append` (SHOP, REWARD, REWARD_REFUND) und an `PointsEventHandler` (Badge-Auswertung nach jeder Buchung, Phase 2).
- Umsetzung laut CLAUDE.md in einer **neuen Session** (Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/punktekonto-backend.plan.md`.
