# Plan: Einreichung & Freigabe – Backend (Phase 4a)

## Summary
Erweitert `backend/meals` um den Kochtag-Prozess der Kinder: Ein kochendes Kind reicht für einen seiner
Anwesenheitstage ein Gericht mit Zutaten ein (Status „wartet auf Freigabe“, ggf. „nach Frist“), ändert es oder sagt das
Kochen der Woche mit Begründung ab. Eltern geben frei oder lehnen mit Begründung ab, tragen Kindergerichte
stellvertretend ein (direkt „freigegeben“) und sehen einen Wochenstatus bis „bereit zur Bestellung“. Dazu kommen Migration
`V4`, erweiterte Repository-/Service-Schicht, neue Routen und der OpenAPI-Vertrag 0.4.0. Die Bedienung in der App folgt
als eigener Plan **Phase 4b**.

## User Story
Als kochendes Kind möchte ich mein Gericht samt Zutaten selbst einreichen, ändern oder absagen, und als Elternteil möchte
ich Kindergerichte freigeben oder ablehnen und auf einen Blick sehen, was vor der Bestellung noch fehlt — damit der
Kochtag-Prozess ohne WhatsApp-Abstimmung läuft.

## Problem → Solution
Gerichte kennen keinen Status, nur Eltern dürfen schreiben, Koch/Köchin muss Elternteil sein → Gerichte tragen Status
(`SUBMITTED`/`APPROVED`/`REJECTED`), Ablehnungsgrund und „nach Frist“; Kinder schreiben eigene Gerichte unter den
Regeln „kocht diese Woche“, „nur Anwesenheitstage“, „ein Gericht je Woche“; Absagen je Kind und Woche; Wochenstatus.

## Metadata
- **Complexity**: Large (folgt den Mustern aus 2a/3a, ~28 Dateien inkl. Tests/Doku, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 4 – Einreichung & Freigabe (Teil **4a**: Backend/API; Teil 4b: App folgt)
- **Estimated Files**: ~28

---

## UX Design

### Before
N/A — reine Backend-/API-Änderung. Kinder schicken Gericht und Zutaten per WhatsApp, Eltern tragen sie als Elterngericht ein.

### After
N/A — Bedienung kommt in Phase 4b; bis dahin per `curl`. API-Ablauf:

```
Kind (kocht, anwesend)                         Eltern                                    Alle
─────────────────────────────────────────      ─────────────────────────────────────     ───────────────────────────────
POST /api/v1/meals/dishes                      POST …/dishes/{id}/approval   → 204       GET …/weeks/{w}/plan
 {date, cookId: <ich>, name, ingredients}      POST …/dishes/{id}/rejection  → 204        dish.status, rejectionReason,
 → 201 status=SUBMITTED, late=true|false        {reason: "kein Gemüse"}                    late, dayValid
PUT  /api/v1/meals/dishes/{id}  (eigenes)      POST /api/v1/meals/dishes                 GET …/weeks/{w}/status
 → status=SUBMITTED (auch nach Freigabe)        {cookId: <Kind>, …} → APPROVED            readyToOrder, openDays,
PUT  …/weeks/{w}/cancellations/{ich}           PUT|DELETE …/cancellations/{Kind}          cooks[state, deadline, …]
 {reason: "krank"} → Gericht der Woche weg
DELETE …/weeks/{w}/cancellations/{ich}  (zurücknehmen)
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Kind reicht ein | WhatsApp | `POST /api/v1/meals/dishes` mit `cookId` = eigene ID | → `SUBMITTED`; nach Frist `late=true` |
| Kind ändert | WhatsApp | `PUT /api/v1/meals/dishes/{id}` (nur eigenes) | → immer wieder `SUBMITTED` |
| Kind sagt ab | WhatsApp | `PUT /api/v1/meals/weeks/{w}/cancellations/{memberId}` | Gericht der Woche wird gelöscht; Rücknahme per `DELETE` |
| Eltern geben frei / lehnen ab | mündlich | `POST …/dishes/{id}/approval` / `…/rejection` | nur aus `SUBMITTED`; Ablehnung braucht Begründung |
| Eltern tragen für Kind ein | als Elterngericht | `POST /api/v1/meals/dishes` mit `cookId` = Kind | direkt `APPROVED` |
| Wochenstatus | Zettel | `GET /api/v1/meals/weeks/{w}/status` | alle Mitglieder |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Modulgrenzen, API-first, TDD, REQ-IDs in Testnamen |
| P0 | `docs/01-requirements/meals.prd.md` | „Fachliche Regeln“, REQ-MEAL-020…033, Phase 4 | fachliche Quelle |
| P0 | `docs/03-design/meals.md` | all | Domänenregeln Phase 2/3 (Fristen, ein Gericht je Tag, Zutatengrenzen) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | all | wird erweitert |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | all | atomares Prüfen+Schreiben in **einer** `dbQuery` |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt` | 17-28, 85-100 | `WeekAttendance`/`MemberWeekAttendance` (Anwesenheit + `submissionDeadline` je Kind) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt`, `PlanApiMappers.kt`, `RequestParsing.kt`, `CallParameters.kt` | all | Routen-/Mapper-Muster, `parseUuid`, `weekParameter` |
| P0 | `backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt`, `ApiTestSupport.kt` | all | Integrations-/Contract-Tests, `FIXED_NOW`, `memberIdOf`, `setupFamilyWithChild` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Attendance.kt` | 12-38 | `ZONE`, `PlanWeek.current(clock)`, `PlanWeek.containing` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Deadlines.kt` | 35-50 | Fristregel (wann `submissionDeadline` null ist) |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/auth/RoleGuard.kt`, `DevicePrincipal.kt` | all | `principalOrThrow`, `requireParent`, `uuidParameter` |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt`, `fakes/*.kt`, `persistence/ExposedMealPlanRepositoryTest.kt` | all | Test-/Fake-Stil, werden erweitert |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt` | 20-70 | Profil-/Ausnahme-JSON für Testaufbau |
| P1 | `api/openapi.yaml` | 306-405, 858-945 | Meals-Pfade, Dish-Schemas, Envelopes |
| P2 | `.claude/PRPs/reports/wochenplan-elterngerichte-backend-report.md` | „Deviations“, „Issues“ | `buildFatJar`-Eigenheit, ktlintFormat |

## External Documentation

No external research needed — feature uses established internal patterns (Exposed 1.x DSL, Ktor, Flyway, SQLite wie in 2a/3a).

KEY_INSIGHT: SQLite `ALTER TABLE … ADD COLUMN` erlaubt `NOT NULL` nur mit Nicht-NULL-Default; `CHECK` ist erlaubt, `UNIQUE`/`PRIMARY KEY` nicht.
APPLIES_TO: Task 3 (`V4__meals_submissions.sql`).
GOTCHA: Kein `DROP`/Neuaufbau von `meal_dishes` — bestehende Daten (Elterngerichte) bekommen per Default `status='APPROVED'`, `late=0`.

KEY_INSIGHT: Flyway-Versionen sind global (ADR-0010). Auf `main`: V1 (`core`), V2, V3 (`meals`). Phase 4a nimmt **V4**.
APPLIES_TO: Task 3.
GOTCHA: Bereits deployte Migrationen (V1–V3) nie ändern.

KEY_INSIGHT: Exposed 1.x: `upsert` aus `org.jetbrains.exposed.v1.jdbc.upsert` (bereits in `ExposedAttendanceRepository` genutzt), `update`/`deleteWhere`/`batchInsert` aus `org.jetbrains.exposed.v1.jdbc`.
APPLIES_TO: Task 5.
GOTCHA: Keine Beispiele mit altem Paket `org.jetbrains.exposed.sql` übernehmen.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt:7-32
@JvmInline
value class DishId(
    val value: String,
)

data class Dish(
    val id: DishId,
    val date: LocalDate,
    val cookId: MemberId,
    val name: String,
    val ingredients: List<String>,
)

/** Result of an atomic write that requires the dish's day to be free. */
enum class SaveOutcome { SAVED, DAY_TAKEN, NOT_FOUND }
```
Paket `de.familyplanner.meals.plan` (alles Neue hier, eigene Dateien je Verantwortung). Fehlermeldungen deutsch, Code und
Kommentare englisch. Grenzwerte als `const val` oben in der Service-Datei (`MealPlanService.kt:14-19`).

### ERROR_HANDLING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:74-77,95-101
private suspend fun requireParentCook(cookId: MemberId) {
    val cook = members.findById(cookId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
    if (cook.role != Role.PARENT) throw AppException.Validation("Koch/Köchin muss ein Elternteil sein")
}
private fun requireSaved(outcome: SaveOutcome) {
    when (outcome) {
        SaveOutcome.SAVED -> Unit
        SaveOutcome.DAY_TAKEN -> throw AppException.Conflict("An diesem Tag ist schon ein Gericht geplant")
        SaveOutcome.NOT_FOUND -> throw AppException.NotFound(DISH_NOT_FOUND)
    }
}
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt:31-33
class Forbidden(message: String = "Nur für Eltern") : AppException(ErrorCode.FORBIDDEN, message)
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/Validation.kt
fun validatedName(value: String, field: String, max: Int): String  // trim, 1..max, sonst Validation("$field muss zwischen 1 und $max Zeichen lang sein")
```
Nur `AppException` (`Validation` 400, `Forbidden` 403, `NotFound` 404, `Conflict` 409); `StatusPages` baut das Envelope.
`when` über Outcomes erschöpfend ohne `else`.

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:27,39
private val log = LoggerFactory.getLogger(MealPlanService::class.java)
log.info("Dish created: {}", dish.id.value)
```
Nur IDs und Status loggen — keine Gerichtsnamen, Zutaten oder Begründungen (`"Dish rejected: {}"`, `"Cooking cancelled: {} {}"` mit memberId + weekStart).

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt:64-86
override suspend fun updateIfDayFree(dish: Dish, at: Instant): SaveOutcome =
    dbQuery(db) {
        val exists = MealDishesTable.selectAll().where { MealDishesTable.id eq dish.id.value }.any()
        if (!exists) return@dbQuery SaveOutcome.NOT_FOUND
        val taken = MealDishesTable.selectAll()
            .where { (MealDishesTable.mealDate eq dish.date.toString()) and (MealDishesTable.id neq dish.id.value) }.any()
        if (taken) return@dbQuery SaveOutcome.DAY_TAKEN
        MealDishesTable.update({ MealDishesTable.id eq dish.id.value }) { … }
        MealDishIngredientsTable.deleteWhere { MealDishIngredientsTable.dishId eq dish.id.value }
        insertIngredients(dish)
        SaveOutcome.SAVED
    }
```
Prüfen und Schreiben in **einer** `dbQuery` (Pool-Größe 1 — verschachtelte `dbQuery` = Deadlock). Daten als ISO-Text,
Zeitstempel Epoch-Millis, Enums als `name`-Text (`presence = Presence.valueOf(...)`, `ExposedAttendanceRepository.kt:103,143`),
Booleans als `bool(...)` auf `INTEGER CHECK (x IN (0,1))` (`MealsTables.kt:7` / `V2:4`). Mapper `internal fun ResultRow.toX()` am Dateiende.

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt:21-41
class MealPlanService(
    private val members: MemberRepository,
    private val plan: MealPlanRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun week(week: PlanWeek): WeekPlan = weekPlanOf(week, plan.findBetween(week.monday, week.days.last()))
    suspend fun create(date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish {
        val dish = validated(DishId(newId()), date, cookId, name, ingredients)
        requireSaved(plan.createIfDayFree(dish, clock.instant()))
        log.info("Dish created: {}", dish.id.value)
        return dish
    }
}
```
Erst alles laden, dann rein rechnen (`AttendanceService.week`, `AttendanceService.kt:85-100`). Reine Regeln als Top-Level-Funktionen
(wie `submissionDeadline` in `Deadlines.kt`), damit sie ohne Fakes testbar sind.

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt:24-53
fun Route.planRoutes(plan: MealPlanService) {
    authenticate(AUTH_DEVICE) {
        post("/api/v1/meals/dishes") {
            call.requireParent()
            val request = call.receive<SaveDishRequest>()
            val dish = plan.create(parseDate(request.date, "date"), request.cook(), request.name, request.ingredients)
            call.respond(HttpStatusCode.Created, DishResponse(success = true, data = dish.toApi()))
        }
        delete("/api/v1/meals/dishes/{dishId}") { call.requireParent(); plan.delete(call.dishId()); call.respond(HttpStatusCode.NoContent) }
    }
}
private fun ApplicationCall.dishId() = DishId(uuidParameter("dishId"))
private fun SaveDishRequest.cook() = MemberId(parseUuid(cookId, "cookId"))
```
Mapper als Extension-Funktionen, Import-Alias für kollidierende API-Typen (`import de.familyplanner.api.model.Dish as ApiDish`).

### KOIN_AND_WIRING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt:16-24 ; app/Application.kt:76,84-90
single<MealPlanRepository> { ExposedMealPlanRepository(get()) }
single { MealPlanService(get(), get(), clock) }
routing { … planRoutes(get<MealPlanService>()) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt:19-48
class MealPlanServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val memberRepository = InMemoryMemberRepository()
    private var nextId = 0
    private val service = MealPlanService(memberRepository, InMemoryMealPlanRepository(), clock) { "dish-${++nextId}" }
    @Test
    fun `REQ-MEAL-032 parent plans dish on open day`() = runTest { // Arrange / Act / Assert }
}
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/AttendanceServiceTest.kt:22-32
private val service = AttendanceService(memberRepository, InMemoryAttendanceRepository(),
    DeadlineSettingsService(InMemoryDeadlineSettingsRepository(), clock), clock)
// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt:31-66
private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }
private suspend fun HttpClient.createDish(token: String, body: String, invalid: Boolean = false): HttpResponse =
    call(HttpMethod.Post, DISHES, token, body, invalidRequestExpected = invalid)
```
kotlin.test + JUnit 5, `runTest`, Fakes statt Mocks, AAA-Kommentare, Testname beginnt mit REQ-ID; `client.call(...)` validiert
Request **und** Response gegen `openapi.yaml` (`invalidRequestExpected = true` nur bei absichtlich schema-ungültigen Requests).

---

## Domänenmodell & Regeln (verbindlich für die Umsetzung)

Entscheidungen des Anwenders (2026-10-18): Ändern nach der Frist erlaubt und als „nach Frist“ markiert; Absage durch Kind
**und** Eltern, Rücknahme möglich; Wochenstatus für **alle** Mitglieder lesbar; Kinder löschen nicht selbst (nur absagen).

| Begriff | Definition |
|---|---|
| `DishStatus` | `SUBMITTED` („wartet auf Freigabe“), `APPROVED` („freigegeben“), `REJECTED` („abgelehnt“). Bestehende und von Eltern gespeicherte Gerichte sind `APPROVED`. |
| `Dish` (erweitert) | + `status: DishStatus = APPROVED`, `rejectionReason: String? = null`, `late: Boolean = false` (Defaults am Ende, damit bestehende Konstruktoraufrufe kompilieren) |
| `Actor` | `id: MemberId`, `role: Role` — wer schreibt (aus `DevicePrincipal` in den Routen) |
| Kindergericht | Gericht, dessen `cookId` ein Mitglied mit Rolle CHILD ist |
| Planwoche des Gerichts | `PlanWeek.containing(dish.date)` |
| Kocht in Woche W | Kind hat in `AttendanceService.week(W)` einen Eintrag mit `submissionDeadline != null` (heißt: `cooks=true` **und** in W an mind. einem Tag anwesend; siehe `Deadlines.kt:39-50`) |
| Regeln für **jedes** Kindergericht (Kind oder Eltern speichern) | (1) Kind kocht in der Planwoche, sonst `Validation("Das Kind kocht in dieser Woche nicht")`; (2) Tag ist Anwesenheitstag, sonst `Validation("Das Kind ist an diesem Tag nicht da")` (REQ-MEAL-021); (3) mind. eine Zutat nach dem Bereinigen, sonst `Validation("Bitte mindestens eine Zutat angeben")` (REQ-MEAL-020); (4) höchstens **ein** Gericht je Kind und Planwoche, sonst `Conflict("Für diese Woche ist schon ein Gericht eingereicht")`; (5) keine Absage für Kind+Woche, sonst `Conflict("Das Kochen für diese Woche ist abgesagt")`. (4)+(5) atomar im Repository. |
| Kind als Akteur | darf nur `cookId == eigene ID` anlegen und nur eigene Gerichte ändern (sonst `Forbidden("Nur für eigene Gerichte")`); `cookId` beim Ändern nicht wechseln (gleiche Regel); Planwoche des (neuen) Datums ≥ `PlanWeek.current(clock)`, sonst `Validation("Für diese Woche kann nicht mehr eingereicht werden")`; Löschen: 403 (unverändert `requireParent`) |
| Status beim Speichern | Kind: immer `SUBMITTED`, `rejectionReason = null`, `late = now > submissionDeadline` (bei **jedem** Speichern neu, REQ-MEAL-020/022). Eltern: immer `APPROVED`, `rejectionReason = null`; `late` beim Anlegen `false`, beim Ändern übernommen, wenn `cookId` gleich bleibt, sonst `false` (REQ-MEAL-031). |
| Elterngericht | Koch/Köchin Elternteil: Regeln aus Phase 3 unverändert (Zutaten optional, keine Wochen-/Anwesenheitsprüfung, vergangene Tage erlaubt) |
| Ein Gericht je Tag | bleibt (`DAY_TAKEN` → 409); gilt für Kinder- und Elterngerichte gemeinsam |
| Freigabe (REQ-MEAL-030) | nur Eltern, nur aus `SUBMITTED` → `APPROVED`; sonst `Conflict("Das Gericht wartet nicht auf Freigabe")`; unbekannt → `NotFound("Gericht nicht gefunden")` |
| Ablehnung (REQ-MEAL-030) | nur Eltern, nur aus `SUBMITTED` → `REJECTED` mit `reason` (`validatedName(reason, "Begründung", REASON_MAX)`, `REASON_MAX = 200`); fehlt/leer → 400. Abgelehntes Gericht bleibt am Tag stehen (blockiert ihn), bis das Kind es ändert (→ `SUBMITTED`) oder Eltern es löschen. |
| `Cancellation` | `memberId`, `week: PlanWeek`, `reason` (1–200, getrimmt), `createdAt` |
| Absage (REQ-MEAL-023) | `PUT …/weeks/{w}/cancellations/{memberId}`: Kind nur für sich selbst (`Forbidden("Nur für eigene Gerichte")`), Eltern für jedes Kind. Ziel existiert (404 `Mitglied nicht gefunden`), ist Kind (`Validation("Nur Kinder können das Kochen absagen")`), kocht in W (Regel oben), W ≥ aktuelle Planwoche (Meldung wie oben). Speichert/überschreibt die Absage (idempotent) und **löscht atomar** das Gericht des Kindes in W samt Zutaten (Tag wieder offen, Zutaten nicht mehr in der späteren Einkaufsliste). |
| Absage zurücknehmen | `DELETE …/weeks/{w}/cancellations/{memberId}`: gleiche Rechte; keine Absage → `NotFound("Keine Absage vorhanden")`; danach ist Einreichen wieder möglich |
| `dayValid` (REQ-MEAL-021) | beim Lesen berechnet: Kindergericht und Kind an dem Tag laut Anwesenheit (inkl. Ausnahmen) abwesend → `false`; sonst `true`. Antworten von `POST`/`PUT` liefern immer `true` (gerade geprüft). |
| `CookState` | `NOT_SUBMITTED`, `SUBMITTED`, `APPROVED`, `REJECTED`, `CANCELLED` |
| `CookStatus` | `memberId`, `submissionDeadline: Instant?`, `state`, `dishId: DishId?`, `late`, `dayValid`, `cancellationReason: String?` |
| Köche einer Woche | alle Kinder, die in W kochen, **plus** Kinder mit einem Gericht in W (auch wenn sie inzwischen nicht mehr kochen); Reihenfolge wie `AttendanceService.week(W).members` |
| Zustand je Koch | Absage vorhanden → `CANCELLED` (+ Begründung); sonst Gericht in W → dessen Status; sonst `NOT_SUBMITTED` |
| `WeekStatus` (REQ-MEAL-033) | `week`, `orderDeadline`, `openDays` (Tage ohne Gericht), `cooks: List<CookStatus>`, `readyToOrder = openDays leer ∧ alle Gerichte der Woche APPROVED ∧ alle dayValid` (PRD: „alle Tage belegt und alle Kindergerichte freigegeben“; ein nicht eingereichtes Kind blockiert nicht, wenn alle Tage belegt sind) |
| Lesen | Plan und Status: alle Mitglieder, jede Woche 2000–2100 |

Statusübergänge:

| Aktion | Akteur | vorher | nachher |
|---|---|---|---|
| anlegen | Kind (eigenes) | – | `SUBMITTED`, `late` berechnet |
| anlegen | Eltern | – | `APPROVED` |
| ändern | Kind (eigenes) | `SUBMITTED` / `APPROVED` / `REJECTED` | `SUBMITTED`, Grund gelöscht, `late` neu |
| ändern | Eltern | beliebig | `APPROVED`, Grund gelöscht |
| freigeben | Eltern | `SUBMITTED` | `APPROVED` |
| ablehnen | Eltern | `SUBMITTED` | `REJECTED` + Grund |
| absagen | Kind / Eltern | Gericht der Woche (beliebig) | gelöscht, Absage gespeichert |
| löschen | Eltern | beliebig | gelöscht |

---

## API-Vertrag (Task 2, `api/openapi.yaml`)

Tag `meals`, alle Endpunkte mit `deviceToken`. `info.version` → `0.4.0`.

Geändert:

| Methode | Pfad | Änderung |
|---|---|---|
| POST | `/api/v1/meals/dishes` | summary „Gericht planen (Eltern) oder einreichen (kochendes Kind, nur für sich)“; Rollenhinweis in `description`; Antworten unverändert (403 = Kind für andere) |
| PUT | `/api/v1/meals/dishes/{dishId}` | summary „Gericht ändern oder verschieben (Eltern; Kind nur eigenes)“ |
| – | Schema `Dish` | + `status`, `late`, `dayValid` (required), `rejectionReason` (nullable) |

Neu:

| Methode | Pfad | operationId | Rolle | Body | Antwort |
|---|---|---|---|---|---|
| POST | `/api/v1/meals/dishes/{dishId}/approval` | `approveDish` | Eltern | – | 204 / 400 / 401 / 403 / 404 / 409 / 429 |
| POST | `/api/v1/meals/dishes/{dishId}/rejection` | `rejectDish` | Eltern | `RejectDishRequest` | 204 / 400 / 401 / 403 / 404 / 409 / 429 |
| PUT | `/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}` | `cancelCooking` | Kind (selbst) / Eltern | `CancelCookingRequest` | 200 `CookingCancellationResponse` / 400 / 401 / 403 / 404 / 429 |
| DELETE | `/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}` | `withdrawCookingCancellation` | Kind (selbst) / Eltern | – | 204 / 400 / 401 / 403 / 404 / 429 |
| GET | `/api/v1/meals/weeks/{weekStart}/status` | `getWeekStatus` | alle | – | 200 `WeekStatusResponse` / 400 / 401 / 429 |

Schemas (Stil wie bestehend; nach `WeekPlanResponse` anhängen):
```yaml
    DishStatus:
      type: string
      description: SUBMITTED = wartet auf Freigabe, APPROVED = freigegeben, REJECTED = abgelehnt
      enum: [SUBMITTED, APPROVED, REJECTED]

    # Dish: required um status, late, dayValid erweitern; Properties ergänzen:
        status: { $ref: '#/components/schemas/DishStatus' }
        rejectionReason: { type: string, maxLength: 200, nullable: true }
        late: { type: boolean, description: Vom Kind nach seiner Frist gespeichert }
        dayValid: { type: boolean, description: false, wenn das kochende Kind an dem Tag nicht (mehr) anwesend ist }

    RejectDishRequest:
      type: object
      required: [reason]
      properties:
        reason: { type: string, minLength: 1, maxLength: 200 }

    CancelCookingRequest:
      type: object
      required: [reason]
      properties:
        reason: { type: string, minLength: 1, maxLength: 200 }

    CookingCancellation:
      type: object
      required: [memberId, weekStart, reason]
      properties:
        memberId: { type: string, format: uuid }
        weekStart: { type: string, format: date }
        reason: { type: string, minLength: 1, maxLength: 200 }

    CookState:
      type: string
      enum: [NOT_SUBMITTED, SUBMITTED, APPROVED, REJECTED, CANCELLED]

    CookStatus:
      type: object
      required: [memberId, state, late, dayValid]
      properties:
        memberId: { type: string, format: uuid }
        submissionDeadline: { type: string, format: date-time, nullable: true }
        state: { $ref: '#/components/schemas/CookState' }
        dishId: { type: string, format: uuid, nullable: true }
        late: { type: boolean }
        dayValid: { type: boolean }
        cancellationReason: { type: string, maxLength: 200, nullable: true }

    WeekStatus:
      type: object
      required: [weekStart, orderDeadline, readyToOrder, openDays, cooks]
      properties:
        weekStart: { type: string, format: date }
        orderDeadline: { type: string, format: date-time }
        readyToOrder: { type: boolean }
        openDays:
          type: array
          items: { type: string, format: date }
        cooks:
          type: array
          items: { $ref: '#/components/schemas/CookStatus' }
```
- Envelopes `CookingCancellationResponse` und `WeekStatusResponse` exakt wie `WeekPlanResponse` (`openapi.yaml:933-945`).
- `RejectDishRequest.reason`/`CancelCookingRequest.reason` mit `minLength: 1`: leerer String ist schema-ungültig (Test mit
  `invalidRequestExpected = true`); `"   "` ist schema-gültig und wird vom Service abgelehnt.
- Pfade: `…/dishes/{dishId}/approval` und `/rejection` direkt nach `/api/v1/meals/dishes/{dishId}`; `…/weeks/{weekStart}/status`
  und `…/cancellations/{memberId}` direkt nach `…/weeks/{weekStart}/plan`. Parameter `MemberId`, `WeekStart`, `DishId` wiederverwenden.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | 5 neue Operationen, `Dish` erweitert, 9 Schemas, Version 0.4.0 (API-first) |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „Einreichung & Freigabe (Phase 4)“, Titel, abgedeckte REQs, Schnittstellen, DB-Tabellen |
| `backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql` | CREATE | Spalten `status`, `rejection_reason`, `late`; Index Koch/Datum; Tabelle `meal_cooking_cancellations` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | UPDATE | `DishStatus`, `Actor`, `Cancellation`, `Dish`-Felder, `PlanDay.dayValid`, `SaveOutcome`/`ReviewOutcome`, `weekPlanOf` mit Gültigkeit |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/ChildCooking.kt` | CREATE | reine Regeln: kocht/anwesend, `isLate`, Wochengrenze für Kinder |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/WeekStatus.kt` | CREATE | `CookState`, `CookStatus`, `WeekStatus`, reine Funktion `weekStatusOf` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanRepository.kt` | UPDATE | `findById`, Kochwochen-Prüfung, `review`, Absagen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | UPDATE | rollenabhängiges Anlegen/Ändern, Kinderregeln, `dayValid` beim Lesen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/DishReviewService.kt` | CREATE | Freigeben/Ablehnen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/CookingWeekService.kt` | CREATE | Absage, Rücknahme, Wochenstatus |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | UPDATE | neue Spalten, `MealCookingCancellationsTable` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | UPDATE | neue Methoden, atomare Prüfungen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt` | UPDATE | POST/PUT mit `Actor` statt `requireParent` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanApiMappers.kt` | UPDATE | neue Dish-Felder, `toActor()` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingRoutes.kt` | CREATE | Freigabe, Ablehnung, Absage, Status |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/CookingApiMappers.kt` | CREATE | Status/Absage ↔ API (eigene Datei wegen detekt `TooManyFunctions`) |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATE | Services registrieren, `MealPlanService` bekommt `AttendanceService` |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `cookingRoutes(...)` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryMealPlanRepository.kt` | UPDATE | identische Semantik zu Exposed |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/PlanTest.kt` | UPDATE | `weekPlanOf` mit `dayValid` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/ChildCookingTest.kt` | CREATE | reine Regeln |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/WeekStatusTest.kt` | CREATE | `weekStatusOf` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt` | UPDATE | neuer Konstruktor; REQ-MEAL-020/021/022/031 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/DishReviewServiceTest.kt` | CREATE | REQ-MEAL-030 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/CookingWeekServiceTest.kt` | CREATE | REQ-MEAL-023/033 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepositoryTest.kt` | UPDATE | Status-Spalten, Kochwoche, Review, Absagen gegen echte SQLite (V1–V4) |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt` | UPDATE | Rollen-Test anpassen (Kind darf eigenes anlegen), neue Felder prüfen |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsSubmissionApiTest.kt` | CREATE | Integrations-/Contract-Tests Phase 4 |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt` | UPDATE | Test-JSON `dishJson` um neue Pflichtfelder ergänzen (sonst scheitert das Decoding des neu generierten Modells) |
| `backend/README.md` | UPDATE | Modulzeile `meals` |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-020…023, 030, 031, 033 |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 4: 4a/4b, Plan-Link (Status `in-progress` bereits bei der Planung gesetzt) |

## NOT Building

- App: Einreichen, Ändern, Absagen, Freigabe-UI, Stellvertretungs-Editor, Wochenstatus-Screen → **Phase 4b** (eigener Plan).
- Benachrichtigungen bei Freigabe/Ablehnung (REQ-MEAL-052) und Erinnerungen (REQ-MEAL-050/051) → Phase 6. 4a liefert dafür
  nur die Daten (Status, Grund, Absage, Frist im Wochenstatus).
- Einkaufsliste (REQ-MEAL-040/041); 4a sorgt nur dafür, dass Absage das Gericht löscht und Status `APPROVED` abfragbar ist → Phase 5.
- Automatische Qualitätsprüfung („enthält Gemüse“), Vorschläge der Kinder für Elterngerichte, Rezepte.
- Sperre nach Frist (Entscheidung: keine Sperre, nur Markierung).
- Kinder löschen eigene Gerichte (Entscheidung: nur absagen).
- Historie/Audit (wer hat wann freigegeben), `createdBy`-Spalte, Server-Push.
- Zurücknehmen einer Freigabe/Ablehnung durch Eltern (Eltern ändern das Gericht stattdessen → `APPROVED`, oder löschen es).
- Änderungen an Anwesenheits-/Frist-Endpunkten.

---

## Step-by-Step Tasks

### Task 1: Moduldesign erweitern (V-Modell links)
- **ACTION**: `docs/03-design/meals.md` erweitern.
- **IMPLEMENT**: Titel → `# Moduldesign: \`meals\` – Anwesenheit, Fristen, Wochenplan & Einreichung`; „Abgedeckte Anforderungen“
  um REQ-MEAL-020, -021, -022, -023, -030, -031, -033 (Phase 4) ergänzen; in der Phase-3-Tabelle die Zeile „Koch/Köchin … Phase 4
  öffnet das …“ auf „siehe Phase 4“ verweisen lassen; neuer Abschnitt `## Einreichung & Freigabe (Phase 4)` mit den Tabellen aus
  „Domänenmodell & Regeln“ (inkl. Statusübergängen und den Anwender-Entscheidungen vom 2026-10-18); „Schnittstellen“ um die
  fünf neuen Endpunkte und die geänderte Rolle von `POST/PUT /dishes` ergänzen; „DB-Tabellen“ um Migration `V4__meals_submissions.sql`.
- **MIRROR**: bestehende Abschnittsstruktur von `meals.md`.
- **GOTCHA**: Kein neues ADR nötig — keine Abweichung von bestehenden Entscheidungen (ADR-0007 „keine Sperre“ ist im PRD bereits entschieden).
- **VALIDATE**: Regeln stimmen mit diesem Plan überein; Links funktionieren.

### Task 2: OpenAPI-Vertrag (Skill `contract-first`, ADR-0004)
- **ACTION**: `api/openapi.yaml` gemäß „API-Vertrag“ ändern.
- **IMPLEMENT**: 5 Operationen mit `tags: [meals]`, deutschen `summary`s („Kindergericht freigeben (nur Eltern)“, „Kindergericht mit
  Begründung ablehnen (nur Eltern)“, „Kochen einer Woche absagen (Kind für sich, Eltern für jedes Kind)“, „Absage zurücknehmen“,
  „Wochenstatus: offene Tage, Einreichungen, Freigaben“), Fehler per `$ref: '#/components/responses/...'`; `Dish` erweitern;
  neue Schemas inkl. der zwei Envelopes; `info.version: 0.4.0`.
- **MIRROR**: `createDish`/`deleteDish` (`openapi.yaml:340-405`), `getWeekPlan` (`323-338`), Envelope `WeekPlanResponse` (`933-945`).
- **GOTCHA**: Nullable-Objekte nur als `allOf: [$ref] + nullable: true`; nullable Strings als `{type: string, nullable: true}` (wie
  `submissionDeadline`). `required` sorgfältig setzen, sonst werden Felder im Generator nullable. Die Android-CI generiert bei
  `api/**` den Client neu — deshalb Task 10.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` erzeugt `DishStatus`, `WeekStatusResponse`, `CancelCookingRequest`;
  `cd android && ./gradlew :core:api:compileDebugKotlin` grün.

### Task 3: Migration
- **ACTION**: `backend/meals/src/main/resources/db/migration/V4__meals_submissions.sql` anlegen.
- **IMPLEMENT**:
  ```sql
  -- Phase 4: children submit dishes, parents approve or reject. Existing dishes were planned by parents and stay approved.
  ALTER TABLE meal_dishes ADD COLUMN status TEXT NOT NULL DEFAULT 'APPROVED'
    CHECK (status IN ('SUBMITTED','APPROVED','REJECTED'));
  ALTER TABLE meal_dishes ADD COLUMN rejection_reason TEXT;
  ALTER TABLE meal_dishes ADD COLUMN late INTEGER NOT NULL DEFAULT 0 CHECK (late IN (0,1));

  -- "One dish per child and plan week" looks up dishes by cook and date range.
  CREATE INDEX idx_meal_dishes_cook ON meal_dishes(cook_member_id, meal_date);

  CREATE TABLE meal_cooking_cancellations (
    member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    week_start TEXT NOT NULL,
    reason TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    PRIMARY KEY (member_id, week_start)
  );
  ```
- **MIRROR**: `V2__meals_attendance.sql`, `V3__meals_plan.sql` (Stil, Präfix `meal_`).
- **GOTCHA**: Der eindeutige Index `idx_meal_dishes_date` aus V3 bleibt (ein Gericht je Tag). Vor dem Merge
  `ls backend/*/src/main/resources/db/migration` prüfen (keine parallele V4).
- **VALIDATE**: `ExposedMealPlanRepositoryTest` läuft gegen V1–V4; Test „bestehendes Gericht ist nach Migration APPROVED“ ist durch
  das Default-Mapping abgedeckt (Insert ohne Status-Spalte gibt es nicht mehr — siehe Task 5, explizit gesetzt).

### Task 4: Domäne – Typen und reine Regeln (TDD: erst Tests RED)
- **ACTION**: `plan/Plan.kt` erweitern, `plan/ChildCooking.kt` und `plan/WeekStatus.kt` anlegen; `PlanTest`, `ChildCookingTest`, `WeekStatusTest`.
- **IMPLEMENT** (`Plan.kt`, Ergänzungen):
  ```kotlin
  enum class DishStatus { SUBMITTED, APPROVED, REJECTED }

  data class Dish(
      val id: DishId,
      val date: LocalDate,
      val cookId: MemberId,
      val name: String,
      val ingredients: List<String>,
      val status: DishStatus = DishStatus.APPROVED,
      val rejectionReason: String? = null,
      val late: Boolean = false,
  )

  /** A day of the plan; [dish] is null while the day is still open; [dayValid] is false if the cooking child is absent. */
  data class PlanDay(
      val date: LocalDate,
      val dish: Dish?,
      val dayValid: Boolean = true,
  )

  /** Who performs a write. */
  data class Actor(
      val id: MemberId,
      val role: Role,
  ) {
      val isParent: Boolean get() = role == Role.PARENT
  }

  data class Cancellation(
      val memberId: MemberId,
      val week: PlanWeek,
      val reason: String,
      val createdAt: Instant,
  )

  /** Result of an atomic write that requires the dish's day (and, for child cooks, the cook's week) to be free. */
  enum class SaveOutcome { SAVED, DAY_TAKEN, NOT_FOUND, COOK_WEEK_TAKEN, COOK_CANCELLED }

  enum class ReviewOutcome { SAVED, NOT_FOUND, NOT_PENDING }

  fun weekPlanOf(
      week: PlanWeek,
      dishes: List<Dish>,
      isDayValid: (Dish) -> Boolean = { true },
  ): WeekPlan {
      val byDate = dishes.associateBy { it.date }
      return WeekPlan(week, week.days.map { day -> byDate[day].let { PlanDay(day, it, it == null || isDayValid(it)) } })
  }
  ```
  `ChildCooking.kt` (reine Funktionen, `const val REASON_MAX = 200` hier oder im Service — **im Service** `MealPlanService.kt` neben den anderen Grenzen):
  ```kotlin
  /** Validates a child's dish against the child's attendance in the dish's plan week; [cook] is null if the child is unknown there. */
  fun requireChildMayCook(date: LocalDate, cook: MemberWeekAttendance?) {
      if (cook?.submissionDeadline == null) throw AppException.Validation("Das Kind kocht in dieser Woche nicht")
      if (cook.days.none { it.date == date && it.present }) throw AppException.Validation("Das Kind ist an diesem Tag nicht da")
  }

  fun isLate(deadline: Instant?, now: Instant): Boolean = deadline != null && now.isAfter(deadline)

  /** Children may only write for the week being planned or later weeks. */
  fun requireOpenForChildren(week: PlanWeek, clock: Clock) {
      if (week.monday.isBefore(PlanWeek.current(clock).monday)) {
          throw AppException.Validation("Für diese Woche kann nicht mehr eingereicht werden")
      }
  }

  /** True unless [dish] is cooked by a child who is absent on its day. */
  fun isDayValid(dish: Dish, attendance: WeekAttendance): Boolean {
      val cook = attendance.members.firstOrNull { it.memberId == dish.cookId } ?: return true
      return cook.days.firstOrNull { it.date == dish.date }?.present ?: true
  }
  ```
  `WeekStatus.kt`:
  ```kotlin
  enum class CookState { NOT_SUBMITTED, SUBMITTED, APPROVED, REJECTED, CANCELLED }

  data class CookStatus(
      val memberId: MemberId,
      val submissionDeadline: Instant?,
      val state: CookState,
      val dishId: DishId?,
      val late: Boolean,
      val dayValid: Boolean,
      val cancellationReason: String?,
  )

  data class WeekStatus(
      val week: PlanWeek,
      val orderDeadline: Instant,
      val openDays: List<LocalDate>,
      val cooks: List<CookStatus>,
      val readyToOrder: Boolean,
  )

  fun weekStatusOf(plan: WeekPlan, attendance: WeekAttendance, cancellations: List<Cancellation>): WeekStatus
  ```
  `weekStatusOf`: `planned = plan.days.filter { it.dish != null }`; Köche = `attendance.members.filter { it.submissionDeadline != null || planned.any { d -> d.dish!!.cookId == it.memberId } }`
  (ohne `!!`: `mapNotNull`); Zustand je Koch gemäß Regeltabelle; `openDays = plan.days.filter { it.dish == null }.map { it.date }`;
  `readyToOrder = openDays.isEmpty() && planned.all { it.dish?.status == DishStatus.APPROVED && it.dayValid }`.
- **MIRROR**: NAMING_CONVENTION; Importe `de.familyplanner.meals.attendance.{PlanWeek, WeekAttendance, MemberWeekAttendance}`, `de.familyplanner.core.member.Role`.
- **GOTCHA**: `Dish`-/`PlanDay`-Defaults am **Ende**, damit bestehende Tests (`Dish(DishId(..), date, cook, name, ingredients)`) kompilieren.
  Kein `Instant.now()`; Zeit kommt immer als Parameter/Clock. detekt `ReturnCount` max 4.
- **VALIDATE**: `./gradlew :meals:test --tests '*PlanTest*' --tests '*ChildCookingTest*' --tests '*WeekStatusTest*'` grün.

### Task 5: Repository (Interface, Exposed, Fake)
- **ACTION**: `MealPlanRepository.kt`, `PlanTables.kt`, `ExposedMealPlanRepository.kt`, `InMemoryMealPlanRepository.kt`, `ExposedMealPlanRepositoryTest`.
- **IMPLEMENT**:
  ```kotlin
  interface MealPlanRepository {
      suspend fun findBetween(from: LocalDate, to: LocalDate): List<Dish>
      suspend fun findById(id: DishId): Dish?

      /**
       * Atomically inserts unless the day already has a dish. With [cookWeek] (child cooks) it also requires that the cook has
       * no other dish in that week and no cancellation for it. Never returns NOT_FOUND.
       */
      suspend fun createIfDayFree(dish: Dish, at: Instant, cookWeek: PlanWeek? = null): SaveOutcome

      /** Like [createIfDayFree] for an existing dish; the dish itself does not count as taking its day or week. */
      suspend fun updateIfDayFree(dish: Dish, at: Instant, cookWeek: PlanWeek? = null): SaveOutcome

      suspend fun delete(id: DishId): Boolean

      /** Atomically sets [status]/[reason] if the dish is SUBMITTED. */
      suspend fun review(id: DishId, status: DishStatus, reason: String?, at: Instant): ReviewOutcome

      suspend fun findCancellations(week: PlanWeek): List<Cancellation>

      /** Atomically stores (or replaces) the cancellation and deletes the cook's dishes in that week, ingredients included. */
      suspend fun cancel(cancellation: Cancellation)

      suspend fun deleteCancellation(memberId: MemberId, week: PlanWeek): Boolean
  }
  ```
  `PlanTables.kt`: `MealDishesTable` + `status = varchar("status", STATUS_LENGTH)` (`STATUS_LENGTH = 9`), `rejectionReason = varchar("rejection_reason", REASON_LENGTH).nullable()`,
  `late = bool("late")`; neu `MealCookingCancellationsTable("meal_cooking_cancellations")` (`memberId` varchar 36, `weekStart` varchar 10,
  `reason` varchar 200, `createdAt` long, PK `(memberId, weekStart)`). `REASON_LENGTH = 200`.
  Exposed-Implementierung (jeweils **eine** `dbQuery`):
  - Insert/Update setzen `status`, `rejectionReason`, `late` explizit aus `Dish`.
  - Privater Helfer `JdbcTransaction.cookWeekOutcome(dish: Dish, week: PlanWeek): SaveOutcome?` — Gericht desselben Kochs mit
    `mealDate` zwischen `week.monday` und `week.days.last()` und `id neq dish.id` → `COOK_WEEK_TAKEN`; Absage `(cook, week.monday)` → `COOK_CANCELLED`; sonst `null`.
    Reihenfolge in create/update: (NOT_FOUND bei update) → DAY_TAKEN → cookWeekOutcome → schreiben.
  - `findById`: Gericht + Zutaten (`ingredientsOf(listOf(id))`).
  - `review`: Zeile lesen; fehlt → `NOT_FOUND`; Status ≠ `SUBMITTED` → `NOT_PENDING`; sonst `update { status, rejectionReason, updatedAt }` → `SAVED`.
  - `cancel`: `upsert` in `MealCookingCancellationsTable`; IDs der Gerichte des Kochs in der Woche lesen; Zutaten (`dishId inList ids`) und Gerichte löschen.
  - `deleteCancellation`: `deleteWhere { (memberId eq …) and (weekStart eq …) } > 0`.
  - `toDish` liest die neuen Spalten (`DishStatus.valueOf(...)`); `toCancellation()` am Dateiende.
  Fake: Maps für Gerichte und Absagen (`Pair<MemberId, PlanWeek>`), identische Semantik und Reihenfolge der Prüfungen.
- **MIRROR**: REPOSITORY_PATTERN; `ExposedAttendanceRepository` (`upsert`, Enum-Mapping).
- **GOTCHA**: Kein verschachteltes `dbQuery`; Helfer als `JdbcTransaction.`-Extensions (wie `insertIngredients`). Textvergleich der
  Daten ist korrekt (Jahre 2000–2100, `RequestParsing.kt:15`). detekt `TooManyFunctions` (15 je Klasse/Datei) — Klasse bleibt bei ~13.
- **VALIDATE**: `ExposedMealPlanRepositoryTest` (zusätzlich ein Kind per `ExposedMemberRepository(db).create(aMember(role = Role.CHILD))`):
  Status/Grund/late werden gespeichert und gelesen; zweites Gericht desselben Kochs in derselben Woche → `COOK_WEEK_TAKEN`, in der
  Folgewoche → `SAVED`; Update des eigenen Gerichts innerhalb der Woche → `SAVED`; Absage blockiert create → `COOK_CANCELLED`;
  `cancel` löscht Gericht + Zutaten der Woche, nicht die der Nachbarwoche; zweites `cancel` überschreibt den Grund; `deleteCancellation`
  true/false; `review` SAVED/NOT_PENDING/NOT_FOUND; `findById` inkl. Zutaten-Reihenfolge; ohne `cookWeek` keine Kochwochen-Prüfung (Elterngerichte).

### Task 6: `MealPlanService` erweitern (TDD)
- **ACTION**: `MealPlanService.kt` + `MealPlanServiceTest` (bestehende Tests auf neuen Konstruktor und `Actor` umstellen).
- **IMPLEMENT**:
  ```kotlin
  const val REASON_MAX = 200

  class MealPlanService(
      private val members: MemberRepository,
      private val plan: MealPlanRepository,
      private val attendance: AttendanceService,
      private val clock: Clock,
      private val newId: () -> String = { UUID.randomUUID().toString() },
  ) {
      suspend fun week(week: PlanWeek): WeekPlan {
          val weekAttendance = attendance.week(week)
          return weekPlanOf(week, plan.findBetween(week.monday, week.days.last())) { isDayValid(it, weekAttendance) }
      }
      suspend fun create(actor: Actor, date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish
      suspend fun update(actor: Actor, id: DishId, date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish
      suspend fun delete(id: DishId)  // unverändert
  }
  ```
  Ablauf `create`: Name/Zutaten validieren (wie bisher) → `requireActorMayCookFor(actor, cookId)` (Kind: `cookId == actor.id`, sonst
  `Forbidden("Nur für eigene Gerichte")`) → Koch laden (404) → wenn Koch PARENT: Phase-3-Weg (`createIfDayFree(dish, now)`) → wenn Koch
  CHILD: `childDish(...)` = Zutaten nicht leer, Woche `PlanWeek.containing(date)`, `if (!actor.isParent) requireOpenForChildren(week, clock)`,
  `entry = attendance.week(week).members.firstOrNull { it.memberId == cookId }`, `requireChildMayCook(date, entry)`, Status/late gemäß
  „Status beim Speichern“, `createIfDayFree(dish, now, cookWeek = week)`.
  Ablauf `update`: `existing = plan.findById(id) ?: NotFound`; Kind: `existing.cookId == actor.id && cookId == actor.id`, sonst 403;
  danach wie `create` mit `updateIfDayFree`; Eltern übernehmen `late` von `existing`, wenn `cookId` gleich bleibt.
  `requireSaved` erweitert: `COOK_WEEK_TAKEN` → `Conflict("Für diese Woche ist schon ein Gericht eingereicht")`,
  `COOK_CANCELLED` → `Conflict("Das Kochen für diese Woche ist abgesagt")`.
  Bisheriges `requireParentCook` entfällt (Koch darf jetzt Kind sein). Log: `"Dish created: {} {}"` mit ID und Status.
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING, LOGGING_PATTERN.
- **GOTCHA**: Datei unter ~200 Zeilen halten; Kinder-Zweig als private Funktionen (`childDish`, `parentDish`), Funktionen < 50 Zeilen,
  `LongParameterList` (Schwelle 8) beachten — ggf. `DishInput(date, cookId, name, ingredients)` als kleine Data-Class einführen und in
  Routen bauen (**bevorzugt**, hält Signaturen kurz). Ein Elternteil, der ein Gericht von Kind auf Elternteil umträgt, landet im Phase-3-Weg.
- **VALIDATE**: `./gradlew :meals:test --tests '*MealPlanServiceTest*'` grün (bestehende 18 Tests + neue, siehe Testing Strategy).

### Task 7: `DishReviewService` und `CookingWeekService` (TDD)
- **ACTION**: zwei Services + Tests.
- **IMPLEMENT**:
  ```kotlin
  class DishReviewService(private val plan: MealPlanRepository, private val clock: Clock) {
      suspend fun approve(id: DishId) = requireReviewed(plan.review(id, DishStatus.APPROVED, null, clock.instant()))
      suspend fun reject(id: DishId, reason: String) {
          val validReason = validatedName(reason, "Begründung", REASON_MAX)
          requireReviewed(plan.review(id, DishStatus.REJECTED, validReason, clock.instant()))
      }
      // ReviewOutcome.NOT_FOUND -> NotFound("Gericht nicht gefunden"); NOT_PENDING -> Conflict("Das Gericht wartet nicht auf Freigabe")
  }

  class CookingWeekService(
      private val members: MemberRepository,
      private val plan: MealPlanRepository,
      private val mealPlan: MealPlanService,
      private val attendance: AttendanceService,
      private val clock: Clock,
  ) {
      suspend fun cancel(actor: Actor, memberId: MemberId, week: PlanWeek, reason: String): Cancellation
      suspend fun withdraw(actor: Actor, memberId: MemberId, week: PlanWeek)
      suspend fun status(week: PlanWeek): WeekStatus =
          weekStatusOf(mealPlan.week(week), attendance.week(week), plan.findCancellations(week))
  }
  ```
  `cancel`: Kind nur für sich (403) → Ziel existiert (404) und ist CHILD (`Validation("Nur Kinder können das Kochen absagen")`) →
  `requireOpenForChildren(week, clock)` (für alle Akteure) → kocht in W (`attendance.week(week)…submissionDeadline != null`, sonst
  `Validation("Das Kind kocht in dieser Woche nicht")`) → Grund `validatedName(reason, "Begründung", REASON_MAX)` → `plan.cancel(...)`.
  `withdraw`: gleiche Rechte- und Mitgliedsprüfung; `deleteCancellation` false → `NotFound("Keine Absage vorhanden")`.
  Logs: `"Dish approved: {}"`, `"Dish rejected: {}"`, `"Cooking cancelled: {} {}"`, `"Cooking cancellation withdrawn: {} {}"`.
- **MIRROR**: SERVICE_PATTERN; Test-Setup mit `InMemoryMealPlanRepository`, `InMemoryAttendanceRepository`, `DeadlineSettingsService(InMemoryDeadlineSettingsRepository(), clock)`.
- **GOTCHA**: Grund-/Rechteprüfung vor jedem Schreiben. `status` berechnet die Anwesenheit zweimal (in `mealPlan.week` und hier) —
  bewusst akzeptiert (5 Mitglieder), nicht optimieren.
- **VALIDATE**: `./gradlew :meals:test` grün.

### Task 8: Routen, Mapper, Koin, App-Verdrahtung
- **ACTION**: `PlanRoutes.kt`, `PlanApiMappers.kt` ändern; `CookingRoutes.kt`, `CookingApiMappers.kt` anlegen; `MealsModule.kt`, `Application.kt`.
- **IMPLEMENT**:
  - `PlanApiMappers.kt`: `fun DevicePrincipal.toActor() = Actor(memberId, role)`; `Dish.toApi(dayValid: Boolean = true)` mit
    `status = ApiDishStatus.valueOf(status.name)`, `rejectionReason`, `late`, `dayValid`; `PlanDay.toApi()` → `dish?.toApi(dayValid)`.
  - `PlanRoutes.kt`: POST/PUT `val actor = call.principalOrThrow().toActor()` statt `requireParent()`; DELETE bleibt `requireParent()`.
  - `CookingRoutes.kt` — `fun Route.cookingRoutes(reviews: DishReviewService, cooking: CookingWeekService)` in `authenticate(AUTH_DEVICE)`:
    - `post("/api/v1/meals/dishes/{dishId}/approval")` → `requireParent()`, `reviews.approve(DishId(call.uuidParameter("dishId")))`, 204.
    - `post("/api/v1/meals/dishes/{dishId}/rejection")` → `requireParent()`, `receive<RejectDishRequest>()`, `reviews.reject(id, r.reason)`, 204.
    - `put("/api/v1/meals/weeks/{weekStart}/cancellations/{memberId}")` → `principalOrThrow().toActor()`, `receive<CancelCookingRequest>()`,
      `cooking.cancel(actor, MemberId(call.uuidParameter("memberId")), call.weekParameter(), r.reason)` → 200 `CookingCancellationResponse`.
    - `delete(…/cancellations/{memberId})` → 204.
    - `get("/api/v1/meals/weeks/{weekStart}/status")` → `principalOrThrow()`, 200 `WeekStatusResponse`.
  - `CookingApiMappers.kt`: `Cancellation.toApi()`, `CookStatus.toApi()` (`submissionDeadline?.toString()`, `state = ApiCookState.valueOf(state.name)`),
    `WeekStatus.toApi()` (`orderDeadline.toString()`, `openDays.map { it.toString() }`). Instants per `Instant.toString()` (ISO-8601 UTC),
    wie `MealsApiMappers.kt:97,103` (`submissionDeadline?.toString()`, `orderDeadline.toString()`).
  - `MealsModule.kt`: `single { MealPlanService(get(), get(), get(), clock) }`, `single { DishReviewService(get(), clock) }`,
    `single { CookingWeekService(get(), get(), get(), get(), clock) }`.
  - `Application.kt`: `cookingRoutes(get<DishReviewService>(), get<CookingWeekService>())` nach `planRoutes(...)`; Imports.
- **MIRROR**: ROUTE_PATTERN, KOIN_AND_WIRING.
- **GOTCHA**: `principalOrThrow`/`requireParent`/`uuidParameter` kommen aus `de.familyplanner.core.auth`. Import-Aliase für
  `DishStatus`, `CookStatus`, `CookState`, `WeekStatus` (Namenskollision mit Domäne). Die Reihenfolge der Prüfungen im Service bestimmt
  den Statuscode — 403 (fremdes Gericht) vor 400 (Regeln).
- **VALIDATE**: `./gradlew :app:compileKotlin` grün; `FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run` startet,
  Flyway meldet bei leerer DB „Successfully applied 4 migrations“.

### Task 9: Integrations- & Contract-Tests
- **ACTION**: `MealsPlanApiTest` anpassen, `MealsSubmissionApiTest` anlegen.
- **IMPLEMENT**:
  - `MealsPlanApiTest`: Test `REQ-AUTH-002 child cannot create, change or delete dishes` → umbenennen in
    `REQ-AUTH-002 child cannot write dishes of other cooks` (Kind legt mit `cookId` = Elternteil an → 403; ändert Elterngericht → 403;
    löscht → 403). In `REQ-MEAL-032 parent plans dish …` zusätzlich `status == APPROVED`, `late == false`, `dayValid == true` prüfen.
  - `MealsSubmissionApiTest` (Konstanten `DISHES`, `WEEK = "2026-10-19"`, `TUESDAY = "2026-10-20"`, `WEDNESDAY = "2026-10-21"`;
    Helfer `makeCook(parentToken, childId)` = `PUT /api/v1/meals/profiles/{childId}` mit
    `{"cooks":true,"pattern":{"type":"ALWAYS","weekA":[],"weekB":[]}}`; `dishJson(...)` wie in `MealsPlanApiTest`; `statusOf(token)`;
    `cancel(token, memberId, reason)`):
    - `REQ-MEAL-020 cooking child submits dish and it waits for approval` (201, `status=SUBMITTED`, `late=false`; Plan zeigt es).
    - `REQ-MEAL-020 child dish without ingredients is rejected` (400) und `… blank name is rejected` (`"   "` → 400).
    - `REQ-MEAL-020 submission after deadline is accepted and marked late` (Eltern legen Ausnahme `ABSENT` 2026-10-14…2026-10-18 an →
      Frist Di 13.10. 16:00 liegt vor `FIXED_NOW` → 201, `late=true`).
    - `REQ-MEAL-020 child that does not cook cannot submit` (ohne `makeCook` → 400).
    - `REQ-MEAL-020 child cannot submit for a past week` (Datum 2026-10-13 → 400).
    - `REQ-MEAL-020 second dish of the same child in a week returns 409`.
    - `REQ-MEAL-021 child cannot cook on an absent day` (Ausnahme `ABSENT` 2026-10-20…2026-10-20, Einreichen Di → 400).
    - `REQ-MEAL-021 dish becomes invalid when its day turns absent` (einreichen Di, dann Ausnahme `ABSENT` für Di → Plan `dayValid=false`, Status `readyToOrder=false`, Koch `dayValid=false`).
    - `REQ-MEAL-022 child edits pending dish and it stays pending` und `REQ-MEAL-022 editing an approved dish requires approval again`.
    - `REQ-MEAL-022 child cannot edit another member's dish` (Elterngericht → 403).
    - `REQ-MEAL-023 child cancels with reason and parents see it in week status` (`state=CANCELLED`, `cancellationReason`).
    - `REQ-MEAL-023 cancellation without reason is rejected` (`""` → 400 invalid; `"   "` → 400).
    - `REQ-MEAL-023 cancelling removes the submitted dish and reopens the day`.
    - `REQ-MEAL-023 submitting after cancellation returns 409` und `… withdrawn cancellation allows submitting again` (DELETE 204, dann POST 201).
    - `REQ-MEAL-023 child cannot cancel for a sibling` (Kind für Elternteil-ID oder zweites Kind → 403); Rücknahme ohne Absage → 404.
    - `REQ-MEAL-030 parent approves submitted dish` (204, Plan `APPROVED`) und `REQ-MEAL-030 parent rejects with reason and child sees it`
      (204, Kind liest Plan: `REJECTED`, `rejectionReason`); `… rejection without reason` (`""` → 400 invalid, `"  "` → 400).
    - `REQ-MEAL-030 child revises rejected dish and it waits for approval again` (`rejectionReason == null`).
    - `REQ-MEAL-030 approving a dish that is not pending returns 409` (Elterngericht) und `… unknown dish returns 404`; Kind → 403.
    - `REQ-MEAL-031 parent enters dish for child and it is approved` (`cookId` = Kind → 201 `APPROVED`; Kind ohne Zutaten → 400; Kind nicht kochend → 400).
    - `REQ-MEAL-033 week status lists open days, pending submissions and approvals` (Kind kocht, nichts eingereicht → 7 offene Tage, Koch
      `NOT_SUBMITTED` mit `submissionDeadline`; nach Einreichen → `SUBMITTED`, 6 offene Tage; `readyToOrder=false`).
    - `REQ-MEAL-033 week is ready to order when all days planned and approved` (Kind reicht Di ein, Eltern geben frei, Eltern belegen die
      übrigen 6 Tage → `readyToOrder=true`, `openDays` leer).
    - `REQ-MEAL-033 child can read week status` (Kind-Token → 200).
- **MIRROR**: TEST_STRUCTURE (`MealsPlanApiTest`, `MealsAttendanceApiTest`).
- **GOTCHA**: `FIXED_NOW` = Mi 14.10.2026 12:00 Berlin → aktuelle Planwoche 19.10.; Frist eines `ALWAYS`-Kinds = So 18.10. 16:00 (nicht
  verspätet). Feste ISO-Strings, kein `LocalDate.now()`. `invalidRequestExpected = true` nur bei schema-ungültigen Bodies. Das Kind aus
  `setupFamilyWithChild()` hat ohne Profil `cooks=false`. Bündeln mehrerer Fehlerfälle in einem Test ist erlaubt (wie 3a), Testname
  trägt dann die REQ-ID des Hauptfalls.
- **VALIDATE**: `./gradlew :app:test` grün.

### Task 10: Android-Testfixture an neuen Vertrag anpassen
- **ACTION**: `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt:61`.
- **IMPLEMENT**: `dishJson` um `"status":"APPROVED","late":false,"dayValid":true` ergänzen. Sonst **keine** Android-Änderung (App-Seite ist 4b).
- **GOTCHA**: Das generierte Android-Modell `Dish` hat die neuen Pflichtfelder ohne Default; die App selbst liest sie (noch) nicht
  (`Mappers.kt:16` nutzt nur `id, date, cookId, name, ingredients`). `SaveDishRequest` bleibt unverändert.
- **VALIDATE**: `cd android && ./gradlew :feature:meals:testDebugUnitTest` grün.

### Task 11: Qualität, Coverage, Doku & Traceability
- **ACTION**: Statische Analyse, Coverage, Doku nachziehen.
- **IMPLEMENT**:
  - `backend/README.md`: Modulzeile `meals` → „Essensplaner: Anwesenheit & Fristen, Wochenplan, Einreichung & Freigabe“.
  - `traceability.md`: REQ-MEAL-020, -021, -022, -023, -030, -031, -033 → Design `[meals](../03-design/meals.md)`, Unit-Tests
    (`ChildCookingTest`, `WeekStatusTest`, `MealPlanServiceTest`, `DishReviewServiceTest`, `CookingWeekServiceTest`,
    `ExposedMealPlanRepositoryTest` je nach REQ), Integration `MealsSubmissionApiTest` (`REQ-MEAL-0xx …`), Status
    „Backend umgesetzt (App folgt in 4b)“.
  - `meals.prd.md`: Phase-4-Zeile Beschreibung „… — aufgeteilt in 4a Backend/API und 4b App“, PRP-Spalte
    `4a: \`.claude/PRPs/plans/einreichung-freigabe-backend.plan.md\`` (bei Abschluss nach `completed/` verschieben, Report verlinken).
- **VALIDATE**: alle Validation Commands unten grün.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-021 week plan marks dish invalid when cook is absent` (`PlanTest`) | `isDayValid` → false für ein Gericht | `PlanDay.dayValid == false`, offene Tage `true` | ja |
| `REQ-MEAL-021 child may cook only on present days` (`ChildCookingTest`) | Eintrag Di–Do anwesend, Datum Fr | `Validation` | |
| `REQ-MEAL-020 child without deadline does not cook` | `submissionDeadline = null` / Eintrag fehlt | `Validation` | ja |
| `REQ-MEAL-020 late only after deadline` | now = Frist, now = Frist + 1 s, Frist null | false, true, false | ja |
| `REQ-MEAL-020 children cannot write for past weeks` | Woche vor `PlanWeek.current` | `Validation`; aktuelle und spätere Woche ok | ja |
| `REQ-MEAL-021 dish of parent is always day valid` | Koch nicht in `attendance.members` | true | |
| `REQ-MEAL-033 cook states` (`WeekStatusTest`) | Absage / SUBMITTED / APPROVED / REJECTED / nichts | `CANCELLED` (+Grund) / … / `NOT_SUBMITTED` | |
| `REQ-MEAL-033 ready only when all days planned, approved and valid` | 7 APPROVED; 1 SUBMITTED; 1 dayValid=false; 1 offener Tag | true; false; false; false | ja |
| `REQ-MEAL-033 child with dish but no longer cooking is listed` | `submissionDeadline=null`, Gericht in Woche | in `cooks` | ja |
| `REQ-MEAL-020 child submits dish and it waits for approval` (`MealPlanServiceTest`) | Kind kocht, Di | `SUBMITTED`, `late=false` | |
| `REQ-MEAL-020 child dish needs ingredients` | `["", " "]` | `Validation` | ja |
| `REQ-MEAL-020 late submission is marked` | Clock nach Frist | `late=true` | |
| `REQ-MEAL-020 child cannot submit for another cook` | `cookId` = Geschwister/Eltern | `Forbidden` | |
| `REQ-MEAL-020 one dish per child and week` | zweites Gericht Mi | `Conflict` | |
| `REQ-MEAL-020 submitting is blocked while cancelled` | Absage im Fake | `Conflict` | |
| `REQ-MEAL-021 child cannot pick an absent day` | Wechselmodell Di–Do, Fr | `Validation` | |
| `REQ-MEAL-021 week plan flags dish whose day became absent` | Gericht Di, dann Ausnahme ABSENT Di | `dayValid=false` | |
| `REQ-MEAL-022 child edit keeps pending` / `… approved dish needs approval again` / `… rejected dish is resubmitted` | | `SUBMITTED`, `rejectionReason=null` | |
| `REQ-MEAL-022 edit after deadline is marked late` | Clock nach Frist | `late=true` | ja |
| `REQ-MEAL-022 child cannot edit another member's dish` / `… cannot hand dish to another cook` | | `Forbidden` | ja |
| `REQ-MEAL-022 child can move dish within week` | Di → Mi | `SAVED`, kein `COOK_WEEK_TAKEN` | ja |
| `REQ-MEAL-031 parent enters child dish approved` | Eltern, `cookId` = Kind | `APPROVED`, `late=false` | |
| `REQ-MEAL-031 parent edit of child dish keeps late flag` | late=true, Eltern ändern Name | `APPROVED`, `late=true` | ja |
| `REQ-MEAL-031 parent cannot enter dish for non-cooking child` | Kind `cooks=false` | `Validation` | ja |
| `REQ-MEAL-032 …` (bestehend, 18 Tests) | wie 3a, mit `Actor(parent)` | unverändert | |
| `REQ-MEAL-030 approve submitted dish` (`DishReviewServiceTest`) | SUBMITTED | `APPROVED` | |
| `REQ-MEAL-030 reject needs reason` | `"  "`, 201 Zeichen | `Validation` | ja |
| `REQ-MEAL-030 reject stores reason` | `" kein Gemüse "` | `REJECTED`, `"kein Gemüse"` | |
| `REQ-MEAL-030 only pending dishes can be reviewed` | APPROVED / REJECTED | `Conflict` | ja |
| `REQ-MEAL-030 reviewing unknown dish fails` | zufällige ID | `NotFound` | ja |
| `REQ-MEAL-023 child cancels own week` (`CookingWeekServiceTest`) | Grund „krank“ | Absage, Gericht gelöscht | |
| `REQ-MEAL-023 cancellation needs reason` | `"   "` | `Validation` | ja |
| `REQ-MEAL-023 child cannot cancel for sibling` | anderes Kind | `Forbidden` | |
| `REQ-MEAL-023 parent cancels for child` | Eltern | Absage | |
| `REQ-MEAL-023 cancelling a non-cooking week fails` | Kind abwesend / `cooks=false` | `Validation` | ja |
| `REQ-MEAL-023 cancelling a past week fails` | Woche vor aktueller Planwoche | `Validation` | ja |
| `REQ-MEAL-023 cancelling a parent fails` | Elternteil-ID | `Validation` | ja |
| `REQ-MEAL-023 cancelling again replaces reason` | zweimal | ein Eintrag, neuer Grund | ja |
| `REQ-MEAL-023 withdraw cancellation` / `… without cancellation fails` | | entfernt / `NotFound` | ja |
| `REQ-MEAL-033 status combines plan, attendance and cancellations` | gemischt | wie `weekStatusOf` | |
| Repository (`ExposedMealPlanRepositoryTest`) | siehe Task 5 | Persistenz, Atomarität, Absage löscht Gericht | ja |

### Edge Cases Checklist
- [x] Empty input (leerer Name/Grund, keine Zutaten bei Kindergericht, nur Leerzeichen)
- [x] Maximum size input (Grund 200/201, bestehende Grenzen für Name/Zutaten)
- [x] Invalid types (kein Montag, keine UUID, Datum außerhalb 2000–2100)
- [x] Concurrent access (Prüfen + Schreiben in einer Transaktion; Pool-Größe 1; `findById` vor `update` ist nicht atomar zum Status — bei 5 Mitgliedern akzeptiert)
- [ ] Network failure — App-Seite (4b), WhatsApp-Fallback (Phase 6)
- [x] Permission denied (Kind für andere → 403, Kind gibt frei/löscht → 403, ohne Token → 401)
- [x] Zeitgrenzen (Frist genau jetzt = nicht verspätet; vergangene Woche; Ausnahme macht Tag ungültig)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew check
```
EXPECT: ktlint, detekt und Tests ohne Befunde (bei Formatbefunden `./gradlew ktlintFormat`)

### Unit Tests
```bash
cd backend && ./gradlew :meals:test
```
EXPECT: alle grün

### Full Test Suite + Coverage
```bash
cd backend && ./gradlew check koverVerify
```
EXPECT: keine Regressionen, Coverage ≥ 80 %

### API-Vertrag / Android-Client
```bash
cd android && ./gradlew :core:api:compileDebugKotlin :feature:meals:testDebugUnitTest
```
EXPECT: generierter Client kompiliert, App-Tests grün

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Image baut (vorher Zeitstempel von `app/build/libs/app-all.jar` prüfen, siehe 3a-Report)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run
```
EXPECT: bei leerer DB „Successfully applied 4 migrations“; bei bestehender DB (Stand Phase 3) nur V4, bestehende Gerichte `status=APPROVED`

### Manual Validation
- [ ] Lokal (`curl -k`, Tokens aus Setup/Kopplung): Kind als „kocht“ markieren, Kind reicht Di ein → Plan zeigt `SUBMITTED`; Status zeigt Koch `SUBMITTED`.
- [ ] Eltern lehnen mit Grund ab → Kind liest Plan mit Grund; Kind ändert → `SUBMITTED`; Eltern geben frei → `APPROVED`.
- [ ] Kind sagt ab → Gericht weg, Status `CANCELLED`; Rücknahme → wieder `NOT_SUBMITTED`.
- [ ] Eltern belegen übrige Tage → Status `readyToOrder: true`.
- [ ] Nach Merge + Deploy auf dem Pi: Container startet, Log zeigt Migration V4, bestehende Gerichte unverändert sichtbar, Health ok;
      App (Stand 3b) zeigt den Plan weiterhin.

---

## Acceptance Criteria
- [ ] All tasks completed
- [ ] All validation commands pass
- [ ] Tests written and passing (Namen mit REQ-ID)
- [ ] No type errors
- [ ] No lint errors (ktlint, detekt)
- [ ] API-Vertrag zuerst geändert, Contract-Tests grün
- [ ] Doku (Design, Traceability, PRD, README) aktuell

## Completion Checklist
- [ ] Code follows discovered patterns (Service/Repository/Routen wie `plan` und `attendance`)
- [ ] Error handling matches codebase style (`AppException`, deutsche Meldungen)
- [ ] Logging follows codebase conventions (nur IDs/Status)
- [ ] Tests follow test patterns (Fakes, AAA, `runTest`, `client.call`)
- [ ] No hardcoded values (Grenzen als `const val`)
- [ ] Documentation updated
- [ ] No unnecessary scope additions (keine Benachrichtigungen, keine Einkaufsliste, keine App-UI)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| App 3b und Backend 4a laufen gemeinsam: Eltern öffnen ein Kindergericht im 3b-Editor, der nur Eltern als Koch anbietet | L | M | Kinder können erst mit 4b einreichen; bis dahin entstehen Kindergerichte nur per `curl`. 4b behebt den Editor. |
| Neue Pflichtfelder in `Dish` brechen Android-Tests/-Decoding | H (ohne Task 10) | M | Task 10 passt die Fixture an; die ausgelieferte App ignoriert unbekannte Felder (`ignoreUnknownKeys = true`) |
| Fristberechnung für `late` weicht von der angezeigten Frist ab | L | M | Beide nutzen `AttendanceService.week(...).submissionDeadline`; keine zweite Berechnung |
| `MealPlanService` wächst über die Dateigrenzen / detekt-Schwellen | M | L | Reine Regeln in `ChildCooking.kt`, Review/Absage/Status in eigenen Services, `DishInput` gegen lange Parameterlisten |
| Abgelehntes Gericht blockiert den Tag für Eltern | M | L | Gewollt (Kind soll überarbeiten); Eltern können es löschen oder ändern; Wochenstatus zeigt `REJECTED` |
| Migrationsnummer kollidiert | L | H | Einzige laufende Backend-Arbeit; vor Merge Migrationsordner prüfen (ADR-0010) |

## Notes
- Phase 4 wird wie 1–3 aufgeteilt: **4a** (dieser Plan) liefert die API, **4b** baut die App: Kind sieht Frist und eigenen Stand,
  reicht ein/ändert/sagt ab (nur Anwesenheitstage wählbar, REQ-MEAL-021 in der UI), sieht Ablehnungsgrund; Eltern sehen Wochenstatus,
  geben frei/lehnen ab, tragen stellvertretend ein (Editor mit Kindern als Koch, nur deren Anwesenheitstage), Kennzeichnung
  „wartet auf Freigabe“, „nach Frist“, „Tag ungültig“ im Essensplan und im Offline-Cache. Der 4b-Plan entsteht nach Merge von 4a
  mit `/ecc:prp-plan docs/01-requirements/meals.prd.md`.
- Freigabe und Ablehnung antworten mit 204 (kein Body): Die App lädt danach Plan/Status ohnehin neu, und `dayValid` müsste sonst im
  Review-Service erneut berechnet werden.
- Ein Kindergericht über `POST /dishes` mit `cookId` = Kind ist sowohl Einreichung (Kind-Token) als auch Stellvertretung (Eltern-Token):
  der Status ergibt sich aus der Rolle des Aufrufers, nicht aus dem Body. Das hält den Vertrag klein und die App-Seite einheitlich.
- „Nach Frist“ wird bei jeder Speicherung durch das Kind neu bestimmt (Entscheidung 2026-10-18); die Kennzahl „fristgerecht
  eingereicht ≥ 80 %“ (PRD) lässt sich aus `late` der Kindergerichte ablesen. Stellvertretend eingetragene Gerichte zählen als nicht verspätet.
