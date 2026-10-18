# Plan: Wochenplan & Elterngerichte – Backend (Phase 3a)

## Summary
Erweitert das Modul `backend/meals` um den Wochenplan: Für jeden Tag einer Woche (Mo–So) höchstens ein Gericht mit
Koch/Köchin und Freitext-Zutaten. Eltern legen Gerichte an, ändern, verschieben und löschen sie (REQ-MEAL-032), alle
Mitglieder lesen den Plan jeder Woche (REQ-MEAL-001). Dazu kommen Migration `V3`, Repository, Service, Routen und der
OpenAPI-Vertrag. Plananzeige, Bearbeitung und Offline-Cache (REQ-MEAL-002) in der App folgen als eigener Plan **Phase 3b**.

## User Story
Als Elternteil möchte ich die Tage einer Woche mit Gerichten und Zutaten belegen, und als Familienmitglied möchte ich für
jeden Tag sehen, was es gibt und wer kocht — damit der Zettel am Kühlschrank überflüssig wird.

## Problem → Solution
`meals` kennt nur Anwesenheit und Fristen, es gibt kein Gericht und keinen Plan → `meals` speichert Gerichte je Tag
(Name, Koch/Köchin, Zutatenzeilen) und liefert für jede Woche sieben Tage, jeder mit Gericht oder „noch offen“.

## Metadata
- **Complexity**: Medium (folgt den Mustern aus Phase 2a, ~18 Dateien, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 3 – Wochenplan & Elterngerichte (Teil **3a**: Backend/API; Teil 3b: App inkl. Offline-Cache folgt)
- **Estimated Files**: ~18 (inkl. Tests und Doku)

---

## UX Design

### Before
N/A — reine Backend-/API-Änderung. Ein Plan existiert nur auf dem Zettel.

### After
N/A — Bedienung kommt in Phase 3b; bis dahin per `curl`. API-Ablauf:

```
Eltern                                                Alle
──────────────────────────────────────────────        ──────────────────────────────────────────
POST /api/v1/meals/dishes                             GET /api/v1/meals/weeks/2026-10-19/plan
 {date: "2026-10-20", cookId: <Mama>,                  → weekStart: 2026-10-19
  name: "Spaghetti Bolognese",                           days: [{date: 2026-10-19, dish: null},      ← „noch offen“
  ingredients: ["500 g Hack", "Spaghetti"]}                     {date: 2026-10-20, dish: {id, cookId,
 → 201 Dish | 409 Tag schon belegt                                 name, ingredients}}, … (7 Einträge)]
PUT    /api/v1/meals/dishes/{dishId}  (ändern/verschieben)
DELETE /api/v1/meals/dishes/{dishId}  (Tag wieder offen)
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Plan ansehen | Zettel | `GET /api/v1/meals/weeks/{weekStart}/plan` | alle Mitglieder, jede Woche (2000–2100) |
| Elterngericht eintragen | Zettel | `POST /api/v1/meals/dishes` | nur Eltern, Koch/Köchin muss Elternteil sein (Kinder: Phase 4) |
| Gericht ändern/verschieben | radieren | `PUT /api/v1/meals/dishes/{dishId}` | Zieltag belegt → 409 |
| Tag freigeben | radieren | `DELETE /api/v1/meals/dishes/{dishId}` | Tag ist wieder „noch offen“ |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Modulgrenzen, API-first, TDD, REQ-IDs in Testnamen |
| P0 | `docs/01-requirements/meals.prd.md` | „Fachliche Regeln“, REQ-MEAL-001/002/032, Phase 3 | fachliche Quelle |
| P0 | `docs/02-architecture/adr/0010-backend-feature-modules.md` | all | Konventionen Feature-Modul (Migrationsnummer, Tabellenpräfix, Koin, Routen) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt` | all | Service-Muster in `meals` (Validierung, Logging, `requireChild`) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedAttendanceRepository.kt` | 88-118 | atomares Prüfen+Schreiben in **einer** `dbQuery` |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/AttendanceRoutes.kt`, `MealsApiMappers.kt`, `RequestParsing.kt` | all | Routen, Mapper, `parseDate`, `weekParameter` |
| P0 | `backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt`, `ApiTestSupport.kt` | all | Integrations-/Contract-Testmuster, `FIXED_NOW`, `parentId` |
| P1 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Attendance.kt` | 12-38 | `PlanWeek`, `ZONE`, `DAYS_PER_WEEK` werden wiederverwendet |
| P1 | `backend/meals/src/main/resources/db/migration/V2__meals_attendance.sql`, `persistence/MealsTables.kt` | all | SQL-/Tabellenstil |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryAttendanceRepositories.kt` | all | Fake-Stil |
| P1 | `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedAttendanceRepositoryTest.kt` | 1-50 | Repository-Test gegen echte SQLite |
| P1 | `api/openapi.yaml` | 178-330, 575-786 | Meals-Abschnitt, Envelopes, Parameter |
| P2 | `docs/03-design/meals.md` | all | wird um Phase 3 erweitert |
| P2 | `.claude/PRPs/reports/anwesenheit-fristen-backend-report.md` | „Deviations“ | detekt `TooManyFunctions` (>15 je Datei) |

## External Documentation

No external research needed — feature uses established internal patterns (Exposed 1.x DSL, Ktor, Flyway wie in Phase 2a).

KEY_INSIGHT: Flyway-Versionen sind global über alle Module (ADR-0010).
APPLIES_TO: Task 3 (`V3__meals_plan.sql`).
GOTCHA: Auf `main` existieren V1 (`core`) und V2 (`meals`). Phase 3a nimmt **V3**. Bereits deployte Migrationen nie ändern.

KEY_INSIGHT: Exposed `deleteWhere` braucht in 1.x den Import `org.jetbrains.exposed.v1.jdbc.deleteWhere`, `update` den Import `org.jetbrains.exposed.v1.jdbc.update`; `batchInsert` aus `org.jetbrains.exposed.v1.jdbc.batchInsert`.
APPLIES_TO: Task 5.
GOTCHA: Keine Beispiele mit altem Paket `org.jetbrains.exposed.sql` übernehmen.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Attendance.kt:55-58,70-82
@JvmInline
value class ExceptionId(
    val value: String,
)

data class AttendanceException(
    val id: ExceptionId,
    val memberId: MemberId,
    val from: LocalDate,
    ...
)
```
Paket je Fachgebiet (`de.familyplanner.meals.plan` neu, analog `…meals.attendance`), Fehlermeldungen deutsch, Code/Kommentare englisch.
Grenzwerte als `const val` oben in der Service-Datei (`EXCEPTION_NOTE_MAX`, `AttendanceService.kt:14-15`).

### ERROR_HANDLING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt:73-75,102-105
if (!attendance.createExceptionIfNoOverlap(exception)) {
    throw AppException.Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme")
}
private suspend fun requireChild(memberId: MemberId) {
    val member = members.findById(memberId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
    if (member.role != Role.CHILD) throw AppException.Validation("Anwesenheit gilt nur für Kinder")
}
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/Validation.kt:7-17
fun validatedName(value: String, field: String, max: Int): String  // trims, 1..max, sonst Validation("$field muss zwischen 1 und $max Zeichen lang sein")
```
Fehler nur als `AppException` (`Validation` 400, `Forbidden` 403, `NotFound` 404, `Conflict` 409); `StatusPages` in `app` baut das Envelope.

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt:37,54
private val log = LoggerFactory.getLogger(AttendanceService::class.java)
log.info("Attendance profile saved: {}", memberId.value)
```
Nur IDs loggen — keine Gerichtsnamen oder Zutaten.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedAttendanceRepository.kt:88-108
override suspend fun createExceptionIfNoOverlap(exception: AttendanceException): Boolean =
    dbQuery(db) {
        val overlaps = MealAttendanceExceptionsTable.selectAll().where { ... }.any()
        if (overlaps) return@dbQuery false
        MealAttendanceExceptionsTable.insert { it[id] = exception.id.value; ... }
        true
    }
```
Prüfen und Schreiben in **einer** `dbQuery` (Pool-Größe 1 — verschachtelte `dbQuery` = Deadlock). Daten als ISO-Text `yyyy-MM-dd`,
Zeitstempel als Epoch-Millis. Mapping `ResultRow → Domäne` als `internal fun ResultRow.toX()` am Dateiende.

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt:30-36,85-100
class AttendanceService(
    private val members: MemberRepository,
    private val attendance: AttendanceRepository,
    private val settings: DeadlineSettingsService,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
)
suspend fun week(week: PlanWeek): WeekAttendance { /* alles einmal laden, dann rein rechnen */ }
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/web/AttendanceRoutes.kt:33-47,63-88,108-112
fun Route.attendanceRoutes(attendance: AttendanceService, settings: DeadlineSettingsService) {
    authenticate(AUTH_DEVICE) {
        get("/api/v1/meals/weeks/{weekStart}/attendance") {
            call.principalOrThrow()
            call.respond(WeekAttendanceResponse(success = true, data = attendance.week(call.weekParameter()).toApi()))
        }
    }
}
post(...) { call.requireParent(); val request = call.receive<CreateAttendanceExceptionRequest>(); ...
            call.respond(HttpStatusCode.Created, AttendanceExceptionResponse(success = true, data = exception.toApi())) }
delete(...) { call.requireParent(); attendance.removeException(ExceptionId(call.uuidParameter("exceptionId"))); call.respond(HttpStatusCode.NoContent) }
private fun ApplicationCall.weekParameter(): PlanWeek  // private in AttendanceRoutes.kt
```
Mapper als Extension-Funktionen mit Import-Alias für API-Typen (`import de.familyplanner.api.model.AttendanceDay as ApiAttendanceDay`).

### KOIN_AND_WIRING
```kotlin
// SOURCE: backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt:13-19 ; app/Application.kt:74,87
fun mealsModule(clock: Clock) = module {
    single<AttendanceRepository> { ExposedAttendanceRepository(get()) }
    single { AttendanceService(get(), get(), get(), clock) }
}
modules(coreModule(config.dbPath, config.setupToken, clock), mealsModule(clock))
routing { attendanceRoutes(get<AttendanceService>(), get<DeadlineSettingsService>()) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/AttendanceServiceTest.kt:21-33
class AttendanceServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-14T10:00:00Z"))
    private val memberRepository = InMemoryMemberRepository()
    private val service = AttendanceService(memberRepository, InMemoryAttendanceRepository(), ..., clock)
    private suspend fun child(): Member = memberRepository.create(aMember(role = Role.CHILD))
    @Test
    fun `REQ-MEAL-010 profiles list every child with default when none saved`() = runTest { /* Arrange / Act / Assert */ }
}
// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt:29-33,67-68
private fun ApplicationTestBuilder.start() = application { module(testConfig(dir), FIXED_NOW) }
private suspend fun HttpClient.parentId(token: String) =
    checkNotNull(json.decodeFromString<MeResponse>(call(HttpMethod.Get, "/api/v1/me", token).bodyAsText()).data).member.id
```
kotlin.test + JUnit 5, `runTest`, Fakes statt Mocks, AAA-Kommentare, Testname beginnt mit REQ-ID; `client.call(...)` validiert
Request **und** Response gegen `openapi.yaml` (`invalidRequestExpected = true`, wenn der Request das Schema absichtlich verletzt).

---

## Domänenmodell & Regeln (verbindlich für die Umsetzung)

| Begriff | Definition |
|---|---|
| `Dish` | `id: DishId` (UUID), `date: LocalDate`, `cookId: MemberId`, `name: String`, `ingredients: List<String>` |
| Ein Gericht je Tag | Ein Tag hat höchstens ein Gericht (eine Hauptmahlzeit). Zweites Gericht am selben Tag → `Conflict("An diesem Tag ist schon ein Gericht geplant")`. |
| `PlanDay` | `date`, `dish: Dish?` — `null` = „noch offen“ (REQ-MEAL-001) |
| `WeekPlan` | `week: PlanWeek`, `days`: genau 7 `PlanDay` Mo–So (aus `PlanWeek.days`) |
| Woche lesen | jede Woche mit Montag im Jahr 2000–2100; alle Mitglieder. Welche Woche die App standardmäßig zeigt (laufende Woche, ab Sonntag Wechsel zur kommenden), entscheidet die App (Phase 3b). |
| Name | getrimmt, 1–60 Zeichen (`DISH_NAME_MAX = 60`, `validatedName(name, "Name", DISH_NAME_MAX)`) |
| Zutaten | Freitext, eine Zeile pro Zutat (PRD). Jede Zeile getrimmt, Leerzeilen entfernt, Reihenfolge bleibt. Max. 80 Zeichen je Zeile (`INGREDIENT_MAX = 80`) → `Validation("Eine Zutat darf höchstens 80 Zeichen lang sein")`; max. 30 Zeilen (`INGREDIENTS_MAX_COUNT = 30`) → `Validation("Höchstens 30 Zutaten je Gericht")`. **0 Zeilen erlaubt** (Reste, Essen gehen) — Pflicht-Zutaten gelten erst für Kindergerichte (REQ-MEAL-020, Phase 4). Zählung der 30 nach dem Entfernen der Leerzeilen. |
| Koch/Köchin (3a) | `cookId` muss existieren (`NotFound("Mitglied nicht gefunden")`) und Rolle PARENT haben (`Validation("Koch/Köchin muss ein Elternteil sein")`). Phase 4 öffnet das für Kinder (REQ-MEAL-031 Stellvertretung). |
| Ändern (`PUT`) | ersetzt `date`, `cookId`, `name`, `ingredients` vollständig; Verschieben auf einen von einem **anderen** Gericht belegten Tag → `Conflict` (gleiche Meldung); unbekannte ID → `NotFound("Gericht nicht gefunden")` |
| Löschen | unbekannte ID → `NotFound("Gericht nicht gefunden")`; danach ist der Tag „noch offen“ |
| Vergangene Tage | erlaubt (keine Sperre; Korrekturen möglich) |
| REQ-MEAL-032 „Zutaten stehen auf der Einkaufsliste“ | Teil von Phase 5 (Einkaufsliste); 3a speichert die Zutaten so, dass Phase 5 sie je Gericht lesen kann |

Entscheidungen (in `docs/03-design/meals.md` dokumentieren): ein Gericht je Tag; Zutaten für Elterngerichte optional;
Koch/Köchin in 3a nur Eltern; Zutaten als eigene Zeilen in der DB (Reihenfolge, späterer Ausbau zu strukturierten Zutaten
und Einkaufsliste), in der API als `string[]`.

---

## API-Vertrag (Task 2, `api/openapi.yaml`)

Tag `meals`, alle Endpunkte mit `deviceToken`. `info.version` → `0.3.0`.

| Methode | Pfad | operationId | Rolle | Body | Antwort |
|---|---|---|---|---|---|
| GET | `/api/v1/meals/weeks/{weekStart}/plan` | `getWeekPlan` | alle | – | 200 `WeekPlanResponse` / 400 / 401 / 429 |
| POST | `/api/v1/meals/dishes` | `createDish` | Eltern | `SaveDishRequest` | 201 `DishResponse` / 400 / 401 / 403 / 404 / 409 / 429 |
| PUT | `/api/v1/meals/dishes/{dishId}` | `updateDish` | Eltern | `SaveDishRequest` | 200 `DishResponse` / 400 / 401 / 403 / 404 / 409 / 429 |
| DELETE | `/api/v1/meals/dishes/{dishId}` | `deleteDish` | Eltern | – | 204 / 400 / 401 / 403 / 404 / 429 |

Schemas (Stil wie bestehend):
```yaml
    Dish:
      type: object
      required: [id, date, cookId, name, ingredients]
      properties:
        id: { type: string, format: uuid }
        date: { type: string, format: date }
        cookId: { type: string, format: uuid }
        name: { type: string, minLength: 1, maxLength: 60 }
        ingredients:
          type: array
          maxItems: 30
          items: { type: string, minLength: 1, maxLength: 80 }

    SaveDishRequest:
      type: object
      required: [date, cookId, name, ingredients]
      properties:
        date: { type: string, format: date }
        cookId: { type: string, format: uuid }
        name: { type: string, minLength: 1, maxLength: 60 }
        ingredients:
          type: array
          description: Eine Zeile pro Zutat; Leerzeilen werden entfernt.
          items: { type: string, maxLength: 80 }

    PlanDay:
      type: object
      required: [date]
      properties:
        date: { type: string, format: date }
        dish:
          allOf:
            - $ref: '#/components/schemas/Dish'
          nullable: true

    WeekPlan:
      type: object
      required: [weekStart, days]
      properties:
        weekStart: { type: string, format: date }
        days:
          type: array
          minItems: 7
          maxItems: 7
          items: { $ref: '#/components/schemas/PlanDay' }
```
- `SaveDishRequest.ingredients` hat **kein** `maxItems` und Items **ohne** `minLength`: Leerzeilen sind erlaubt und werden
  serverseitig entfernt; die 30-Zeilen-Grenze prüft der Service nach dem Entfernen (sonst würde der Contract-Validator
  gültige Eingaben mit Leerzeilen ablehnen).
- Envelopes `DishResponse` und `WeekPlanResponse` exakt wie `WeekAttendanceResponse` (`openapi.yaml:774-786`).
- Parameter `DishId` wie `ExceptionId` (`name: dishId, in: path, required: true, schema: {type: string, format: uuid}`).
- Pfade direkt unter `/api/v1/meals/weeks/{weekStart}/attendance` einfügen (Meals-Block zusammenhalten); Schemas nach
  `WeekAttendanceResponse` anhängen.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | 4 Operationen, Parameter `DishId`, 6 Schemas, Version 0.3.0 (API-first) |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „Wochenplan & Elterngerichte (Phase 3)“, Titel, Tabelle, Schnittstellen |
| `backend/meals/src/main/resources/db/migration/V3__meals_plan.sql` | CREATE | Tabellen `meal_dishes`, `meal_dish_ingredients` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Plan.kt` | CREATE | `DishId`, `Dish`, `PlanDay`, `WeekPlan`, `SaveOutcome`, reine Funktion `weekPlanOf` |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanRepository.kt` | CREATE | Interface |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | CREATE | Validierung, Rollenprüfung Koch/Köchin, Konflikte, Logging |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/PlanTables.kt` | CREATE | Exposed-Tabellen (eigene Datei, `MealsTables.kt` bleibt Anwesenheit) |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepository.kt` | CREATE | atomares „Tag frei?“ + Schreiben |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanApiMappers.kt` | CREATE | Domäne ↔ API (eigene Datei wegen detekt `TooManyFunctions`) |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/PlanRoutes.kt` | CREATE | 4 Routen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RequestParsing.kt` | UPDATE | `weekParameter()` hierher verschieben (von `AttendanceRoutes.kt`, `internal`), damit beide Routen-Dateien es nutzen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/AttendanceRoutes.kt` | UPDATE | private `weekParameter` entfernen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | UPDATE | Repository + Service registrieren |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `planRoutes(get<MealPlanService>())` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryMealPlanRepository.kt` | CREATE | Fake mit identischer Semantik |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/PlanTest.kt` | CREATE | reine Funktion `weekPlanOf` |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/plan/MealPlanServiceTest.kt` | CREATE | REQ-MEAL-001/032, Validierung |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedMealPlanRepositoryTest.kt` | CREATE | echte SQLite inkl. V1–V3 |
| `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt` | UPDATE | `memberIdOf(token)` (aus `MealsAttendanceApiTest.parentId` verschoben) |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt` | UPDATE | privaten `parentId` durch `memberIdOf` ersetzen |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsPlanApiTest.kt` | CREATE | Integrations-/Contract-Tests |
| `backend/README.md` | UPDATE | Modultabelle: `meals` – Anwesenheit, Fristen, Wochenplan |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-001, REQ-MEAL-032 (REQ-MEAL-002 bleibt offen bis 3b) |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 3 → in-progress, 3a/3b, Plan-Link |

## NOT Building

- App: Plananzeige, Wochenwechsel, Eingabe der Elterngerichte, **Offline-Cache (REQ-MEAL-002)** → Phase 3b (eigener Plan).
- Kindergerichte, Status („wartet auf Freigabe“, „freigegeben“, „nach Frist“, „Tag ungültig“), Absage, Stellvertretung → Phase 4.
  (Phase 4 ergänzt `meal_dishes` per `ALTER TABLE ADD COLUMN`, z. B. `status` mit Default `APPROVED`, und lockert die Koch-Regel.)
- Einkaufsliste und Abhaken (REQ-MEAL-040/041, zweiter Teil von REQ-MEAL-032) → Phase 5.
- Mehrere Mahlzeiten pro Tag, Rezepte, strukturierte Zutaten (Menge/Einheit), Picnic.
- Server-Zeitstempel für „zuletzt synchronisiert“ (die App merkt sich den Zeitpunkt ihres letzten erfolgreichen Abrufs).
- ETag/Conditional Requests, Änderungshistorie, `createdBy`-Spalte.
- Änderungen an bestehenden Attendance-Endpunkten oder -Schemas.

---

## Step-by-Step Tasks

### Task 1: Moduldesign erweitern (V-Modell links)
- **ACTION**: `docs/03-design/meals.md` erweitern.
- **IMPLEMENT**: Titel → `# Moduldesign: \`meals\` – Anwesenheit, Fristen & Wochenplan`; „Abgedeckte Anforderungen“ um REQ-MEAL-001, -002 (App, 3b), -032 ergänzen; neuer Abschnitt `## Wochenplan & Elterngerichte (Phase 3)` mit der Tabelle aus „Domänenmodell & Regeln“, den Entscheidungen, den vier Endpunkten unter „Schnittstellen“, Tabellen `meal_dishes`/`meal_dish_ingredients` unter „DB-Tabellen“ (Migration `V3__meals_plan.sql`, eindeutiger Index auf `meal_date`) und Hinweis „Offline-Lesen: App-Seite, Phase 3b“.
- **MIRROR**: bestehende Abschnittsstruktur von `meals.md`.
- **GOTCHA**: Kein neues ADR nötig — keine Abweichung von bestehenden Entscheidungen.
- **VALIDATE**: Links funktionieren; Regeln stimmen mit diesem Plan überein.

### Task 2: OpenAPI-Vertrag (Skill `contract-first`, ADR-0004)
- **ACTION**: `api/openapi.yaml` gemäß „API-Vertrag“ erweitern.
- **IMPLEMENT**: 4 Operationen mit `tags: [meals]`, deutschen `summary`s („Wochenplan Mo–So mit Gericht je Tag“, „Gericht planen (nur Eltern)“, „Gericht ändern oder verschieben (nur Eltern)“, „Gericht löschen, Tag wird wieder offen (nur Eltern)“), Fehlerantworten per `$ref: '#/components/responses/...'`; Parameter `DishId`; Schemas `Dish`, `SaveDishRequest`, `PlanDay`, `WeekPlan`, `DishResponse`, `WeekPlanResponse`.
- **MIRROR**: `createAttendanceException` (`openapi.yaml:233-255`) für POST, `deleteAttendanceException` (`257-271`) für DELETE, `getWeekAttendance` (`306-322`) für GET.
- **GOTCHA**: Nullable Objekt nur als `allOf: [$ref] + nullable: true` (Generator). `required` sorgfältig setzen, sonst werden Felder nullable. Android-Generierung (`android/core/api`) läuft in der Android-CI bei `api/**` mit und muss kompilieren.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` erzeugt `Dish`, `SaveDishRequest`, `WeekPlanResponse`; `cd android && ./gradlew :core:api:compileDebugKotlin` grün.

### Task 3: Migration
- **ACTION**: `backend/meals/src/main/resources/db/migration/V3__meals_plan.sql` anlegen.
- **IMPLEMENT**:
  ```sql
  -- One dish per day: unique index (not a table constraint) so Phase 4 can replace it if needed.
  CREATE TABLE meal_dishes (
    id TEXT PRIMARY KEY,
    meal_date TEXT NOT NULL,
    cook_member_id TEXT NOT NULL REFERENCES members(id),
    name TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
  );

  CREATE UNIQUE INDEX idx_meal_dishes_date ON meal_dishes(meal_date);

  CREATE TABLE meal_dish_ingredients (
    dish_id TEXT NOT NULL REFERENCES meal_dishes(id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    text TEXT NOT NULL,
    PRIMARY KEY (dish_id, position)
  );
  ```
- **MIRROR**: `V2__meals_attendance.sql` (Stil, Präfix `meal_`).
- **GOTCHA**: `DatabaseFactory.kt:51` setzt `foreign_keys=true`, `ON DELETE CASCADE` greift also. Das Repository löscht die Zutaten trotzdem explizit vor dem Gericht (unabhängig vom Pragma, im Test prüfbar). `cook_member_id` ohne Cascade: Mitglieder werden derzeit nicht gelöscht; ein unbekanntes `cookId` würde ohne Service-Prüfung am FK scheitern — deshalb prüft der Service vorher.
- **VALIDATE**: `ExposedMealPlanRepositoryTest` (Task 5) läuft gegen V1–V3.

### Task 4: Domäne (TDD: erst Tests RED)
- **ACTION**: `plan/Plan.kt` + `plan/PlanTest.kt`.
- **IMPLEMENT**:
  ```kotlin
  package de.familyplanner.meals.plan

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

  data class PlanDay(
      val date: LocalDate,
      val dish: Dish?,
  )

  data class WeekPlan(
      val week: PlanWeek,
      val days: List<PlanDay>,
  )

  /** Result of an atomic write that requires the dish's day to be free. */
  enum class SaveOutcome { SAVED, DAY_TAKEN, NOT_FOUND }

  /** Seven days Monday..Sunday; days without a dish stay open. Dishes outside [week] are ignored. */
  fun weekPlanOf(week: PlanWeek, dishes: List<Dish>): WeekPlan {
      val byDate = dishes.associateBy { it.date }
      return WeekPlan(week, week.days.map { PlanDay(it, byDate[it]) })
  }
  ```
- **MIRROR**: NAMING_CONVENTION; `PlanWeek` aus `de.familyplanner.meals.attendance` importieren.
- **GOTCHA**: Kein `Instant.now()` — Zeitstempel kommen aus dem Service (`clock.instant()`).
- **VALIDATE**: `./gradlew :meals:test --tests '*PlanTest*'` grün.

### Task 5: Repository (Interface, Exposed, Fake)
- **ACTION**: `plan/MealPlanRepository.kt`, `persistence/PlanTables.kt`, `persistence/ExposedMealPlanRepository.kt`, `fakes/InMemoryMealPlanRepository.kt`, `ExposedMealPlanRepositoryTest`.
- **IMPLEMENT**:
  ```kotlin
  interface MealPlanRepository {
      /** Dishes with a date in the inclusive range, sorted by date, ingredients in input order. */
      suspend fun findBetween(from: LocalDate, to: LocalDate): List<Dish>

      /** Atomically inserts unless the day already has a dish. Never returns NOT_FOUND. */
      suspend fun createIfDayFree(dish: Dish, at: Instant): SaveOutcome

      /** Atomically replaces the dish with [Dish.id] unless another dish occupies its (new) date. */
      suspend fun updateIfDayFree(dish: Dish, at: Instant): SaveOutcome

      suspend fun delete(id: DishId): Boolean
  }
  ```
  `PlanTables.kt`: `MealDishesTable("meal_dishes")` (`id` varchar 36 PK, `mealDate` varchar 10, `cookMemberId` varchar 36, `name` varchar 60, `createdAt`/`updatedAt` long), `MealDishIngredientsTable("meal_dish_ingredients")` (`dishId` varchar 36, `position` integer, `text` varchar 80, PK `(dishId, position)`). Längen als `private const val`.
  Exposed-Implementierung, jeweils **eine** `dbQuery`:
  - `findBetween`: Gerichte per `mealDate.between(...)` bzw. `(mealDate greaterEq from) and (mealDate lessEq to)` lesen, dann Zutaten mit `dishId inList ids` **in derselben Transaktion** holen, `orderBy(position)`, `groupBy { dishId }` — kein N+1.
  - `createIfDayFree`: `selectAll().where { mealDate eq date }.any()` → `DAY_TAKEN`; sonst `insert` + `batchInsert` der Zutaten (Position = Index) → `SAVED`.
  - `updateIfDayFree`: existiert `id` nicht → `NOT_FOUND`; anderes Gericht (`id neq`) am Zieldatum → `DAY_TAKEN`; sonst `update` (date, cook, name, updatedAt — `createdAt` bleibt), Zutaten `deleteWhere { dishId eq id }` + `batchInsert` → `SAVED`.
  - `delete`: erst Zutaten, dann Gericht löschen; Rückgabe `> 0` der Gerichtszeilen.
  Fake: `MutableMap<DishId, Dish>` mit identischer Semantik (Sortierung nach Datum, DAY_TAKEN-/NOT_FOUND-Regeln).
- **MIRROR**: REPOSITORY_PATTERN; `ExposedAttendanceRepository` (Tabellen-Imports, `toException()`-Mapper).
- **GOTCHA**: Kein verschachteltes `dbQuery`. Der eindeutige Index ist nur Sicherheitsnetz — der Service-Pfad muss `DAY_TAKEN` über die Vorabprüfung liefern, nicht über eine Exception. ISO-Datumsvergleich als Text ist korrekt, weil `parseDate` nur Jahre 2000–2100 zulässt.
- **VALIDATE**: `ExposedMealPlanRepositoryTest` (TempDir, `DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))`, Elternteil per `ExposedMemberRepository(db).create(aMember(role = Role.PARENT))`): Anlegen + Lesen inkl. Zutaten-Reihenfolge; zweites Gericht am selben Tag → `DAY_TAKEN`; Update ersetzt Zutaten und verschiebt; Verschieben auf belegten Tag → `DAY_TAKEN`; Update auf eigenen Tag → `SAVED`; unbekannte ID → `NOT_FOUND`; Delete entfernt Gericht und Zutaten (direkt `MealDishIngredientsTable` zählen); `findBetween` ignoriert Gerichte außerhalb des Bereichs.

### Task 6: Service (TDD)
- **ACTION**: `plan/MealPlanService.kt` + `MealPlanServiceTest` mit Fakes.
- **IMPLEMENT**:
  ```kotlin
  const val DISH_NAME_MAX = 60
  const val INGREDIENT_MAX = 80
  const val INGREDIENTS_MAX_COUNT = 30

  class MealPlanService(
      private val members: MemberRepository,
      private val plan: MealPlanRepository,
      private val clock: Clock,
      private val newId: () -> String = { UUID.randomUUID().toString() },
  ) {
      suspend fun week(week: PlanWeek): WeekPlan = weekPlanOf(week, plan.findBetween(week.monday, week.days.last()))

      suspend fun create(date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish
      suspend fun update(id: DishId, date: LocalDate, cookId: MemberId, name: String, ingredients: List<String>): Dish
      suspend fun delete(id: DishId)
  }
  ```
  Gemeinsamer privater Baustein `validated(id, date, cookId, name, ingredients): Dish` → `requireParentCook(cookId)`, `validatedName(name, "Name", DISH_NAME_MAX)`, `validatedIngredients(ingredients)` (trim, `filter { it.isNotEmpty() }`, Längen-/Anzahlprüfung). `SaveOutcome` → `DAY_TAKEN` ⇒ `Conflict("An diesem Tag ist schon ein Gericht geplant")`, `NOT_FOUND` ⇒ `NotFound("Gericht nicht gefunden")`. Log `info("Dish created: {}", id)`, `"Dish updated: {}"`, `"Dish deleted: {}"`.
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING, LOGGING_PATTERN.
- **GOTCHA**: Validierung **vor** jedem Repository-Zugriff außer der Mitgliedssuche; `when (outcome)` erschöpfend ohne `else`.
- **VALIDATE**: `./gradlew :meals:test` grün.

### Task 7: Routen, Mapper, Koin, App-Verdrahtung
- **ACTION**: `web/PlanApiMappers.kt`, `web/PlanRoutes.kt`, `weekParameter` nach `RequestParsing.kt` verschieben, `MealsModule.kt`, `Application.kt`.
- **IMPLEMENT**:
  - `RequestParsing.kt`: `internal fun ApplicationCall.weekParameter(): PlanWeek` (Code unverändert aus `AttendanceRoutes.kt:108-112`), dort entfernen; Imports nachziehen.
  - Mapper: `fun Dish.toApi() = ApiDish(id = id.value, date = date.toString(), cookId = cookId.value, name = name, ingredients = ingredients)`; `fun PlanDay.toApi()`; `fun WeekPlan.toApi() = ApiWeekPlan(weekStart = week.monday.toString(), days = days.map { it.toApi() })`.
  - `fun Route.planRoutes(plan: MealPlanService)` in `authenticate(AUTH_DEVICE) { … }`:
    - `get("/api/v1/meals/weeks/{weekStart}/plan")` → `principalOrThrow()`, `WeekPlanResponse(success = true, data = plan.week(call.weekParameter()).toApi())`.
    - `post("/api/v1/meals/dishes")` → `requireParent()`, `receive<SaveDishRequest>()`, `plan.create(parseDate(r.date, "date"), MemberId(parseUuid(r.cookId)), r.name, r.ingredients)` → 201 `DishResponse`.
    - `put("/api/v1/meals/dishes/{dishId}")` → `DishId(call.uuidParameter("dishId"))` → 200.
    - `delete("/api/v1/meals/dishes/{dishId}")` → 204.
  - `cookId` im Body ist ein UUID-String: `MemberId(...)` direkt; ungültiges Format fängt der Contract (`format: uuid`) im Test, zur Laufzeit führt es zu `NotFound` — akzeptabel. Alternativ kleiner Helfer `parseUuid(value, field)` in `RequestParsing.kt` (`UUID.fromString`, Fehler ⇒ `Validation("Ungültige Kennung '$field'")`) — **bevorzugt**, damit 400 statt 404.
  - `MealsModule.kt`: `single<MealPlanRepository> { ExposedMealPlanRepository(get()) }`, `single { MealPlanService(get(), get(), clock) }`.
  - `Application.kt`: im `routing` `planRoutes(get<MealPlanService>())`, Import `de.familyplanner.meals.web.planRoutes`, `de.familyplanner.meals.plan.MealPlanService`.
- **MIRROR**: ROUTE_PATTERN, KOIN_AND_WIRING.
- **GOTCHA**: Generierte Modelle haben `String` für `date`/`uuid`; Parsing nur in Routen/Mappern. Import-Alias `import de.familyplanner.api.model.Dish as ApiDish` usw. (Name kollidiert mit Domäne). detekt `TooManyFunctions` (15 je Datei) — darum eigene Mapper-Datei.
- **VALIDATE**: `./gradlew :app:compileKotlin` grün; `FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run` startet, Flyway meldet bei leerer DB „Successfully applied 3 migrations“.

### Task 8: Integrations- & Contract-Tests
- **ACTION**: `MealsPlanApiTest` in `backend/app/src/test/...`; `memberIdOf` nach `ApiTestSupport.kt`.
- **IMPLEMENT**: `suspend fun HttpClient.memberIdOf(token: String): String` in `ApiTestSupport.kt` (Inhalt von `MealsAttendanceApiTest.parentId`), dort ersetzen. Konstanten `DISHES = "/api/v1/meals/dishes"`, Helfer `planOf(token, weekStart): WeekPlan`, `createDish(token, body, invalid = false)`. Body-Helfer `dishJson(date, cookId, name = "Spaghetti", ingredients = listOf("Nudeln", "Tomaten"))`. Tests (alle Requests über `client.call` → Contract-Validierung, `FIXED_NOW`):
  - `REQ-MEAL-001 empty week shows seven open days` (Woche 2026-10-19: 7 Tage, `date` Mo..So, alle `dish == null`).
  - `REQ-MEAL-032 parent plans dish and every member sees it with cook` (POST Di 2026-10-20 → 201; Kind liest Plan → Di hat Name, `cookId` = Elternteil, Zutaten; übrige Tage offen).
  - `REQ-MEAL-032 ingredient lines are trimmed and blank lines removed` (`["  Nudeln ", "", "Tomaten"]` → `["Nudeln","Tomaten"]`).
  - `REQ-MEAL-032 parent changes and moves dish` (PUT neues Datum Mi + Name → Di offen, Mi belegt).
  - `REQ-MEAL-032 deleting dish reopens the day` (DELETE → 204, Tag `null`).
  - `REQ-MEAL-032 second dish on same day returns 409` und `… moving onto occupied day returns 409`.
  - `REQ-MEAL-001 member can switch to the following week` (GET 2026-10-26 → 200, Gericht vom 20.10. nicht enthalten).
  - `REQ-AUTH-002 child cannot create, change or delete dishes` (403 ×3) — Kind-Token aus `setupFamilyWithChild()`.
  - Validierung: leerer Name → 400 (`invalidRequestExpected = true`, minLength), Name nur Leerzeichen `"   "` → 400 (Schema gültig, Service lehnt ab), Zutat 81 Zeichen → 400 (invalid), 31 Zutaten → 400 (Schema gültig, Service), Kind als Koch/Köchin → 400, unbekannte `cookId` (zufällige UUID) → 404, `date` `"2026-13-01"` → 400 (invalid), `weekStart` Dienstag → 400, unbekannte Dish-ID bei PUT/DELETE → 404, Dish-ID `"abc"` → 400.
- **MIRROR**: TEST_STRUCTURE (`MealsAttendanceApiTest`).
- **GOTCHA**: `FIXED_NOW` ist Mi 14.10.2026; Daten im Test sind feste ISO-Strings, keine Berechnung mit `LocalDate.now()`. Wenn der Contract-Validator einen absichtlich ungültigen Request bemängelt, `invalidRequestExpected = true` setzen; bei schema-gültigen, fachlich falschen Requests **nicht**.
- **VALIDATE**: `./gradlew :app:test` grün.

### Task 9: Qualität, Coverage, Doku & Traceability
- **ACTION**: Statische Analyse und Coverage; Doku nachziehen.
- **IMPLEMENT**:
  - `backend/README.md`: Modulzeile `meals` → „Essensplaner: Anwesenheit & Fristen, Wochenplan“.
  - `traceability.md`: REQ-MEAL-001 → Design `[meals](../03-design/meals.md)`, Unit `PlanTest`, `MealPlanServiceTest` (`REQ-MEAL-001 …`), Integration `MealsPlanApiTest` (`REQ-MEAL-001 …`), Status „Backend umgesetzt (App folgt in 3b)“. REQ-MEAL-032 → Unit `MealPlanServiceTest`, `ExposedMealPlanRepositoryTest`, Integration `MealsPlanApiTest`, Status „Backend umgesetzt (App folgt in 3b; Einkaufsliste in Phase 5)“. REQ-MEAL-002 bleibt „offen“ mit Vermerk „App, Phase 3b“.
  - `meals.prd.md`: Phase-3-Zeile → `in-progress`, Beschreibung „… — aufgeteilt in 3a Backend/API und 3b App inkl. Offline-Cache“, PRP-Spalte `3a: \`.claude/PRPs/plans/wochenplan-elterngerichte-backend.plan.md\``.
- **VALIDATE**: alle Validation Commands unten grün.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-001 week plan has seven days Monday to Sunday` | Woche 2026-10-19, keine Gerichte | 7 `PlanDay`, 19.–25.10., alle `dish == null` | |
| `REQ-MEAL-001 dish appears on its day` | Gericht am 21.10. | Mi belegt, Rest offen | |
| `REQ-MEAL-001 dishes outside the week are ignored` | Gericht am 26.10. | Woche 19.10. komplett offen | ja |
| `REQ-MEAL-032 parent plans dish on open day` | Elternteil, Di, „Spaghetti“, 2 Zutaten | gespeichert, `week()` zeigt es | |
| `REQ-MEAL-032 name and ingredients are trimmed` | `"  Suppe "`, `[" Lauch ", "", "  "]` | `"Suppe"`, `["Lauch"]` | ja |
| `REQ-MEAL-032 dish without ingredients is allowed` | `[]` | gespeichert | ja |
| `REQ-MEAL-032 blank name is rejected` | `"   "` | `Validation` | ja |
| `REQ-MEAL-032 name longer than 60 is rejected` | 61 Zeichen | `Validation` | ja |
| `REQ-MEAL-032 ingredient longer than 80 is rejected` | 81 Zeichen | `Validation` | ja |
| `REQ-MEAL-032 more than 30 ingredients are rejected` | 31 Zeilen | `Validation` | ja |
| `REQ-MEAL-032 30 ingredients plus blank lines are accepted` | 30 Zeilen + 5 Leerzeilen | gespeichert, 30 | ja |
| `REQ-MEAL-032 cook must be a parent` | Kind als `cookId` | `Validation` | |
| `REQ-MEAL-032 unknown cook is rejected` | zufällige ID | `NotFound` | ja |
| `REQ-MEAL-032 second dish on same day conflicts` | zweimal Di | `Conflict` | |
| `REQ-MEAL-032 update replaces fields and moves dish` | Di → Do, neuer Name/Zutaten | Do belegt, Di offen | |
| `REQ-MEAL-032 update onto own day succeeds` | gleiches Datum, neuer Name | gespeichert | ja |
| `REQ-MEAL-032 moving onto occupied day conflicts` | Di → Mi (belegt) | `Conflict`, beide unverändert | ja |
| `REQ-MEAL-032 updating unknown dish fails` | zufällige ID | `NotFound` | ja |
| `REQ-MEAL-032 deleting dish reopens day` | delete | Tag offen | |
| `REQ-MEAL-032 deleting unknown dish fails` | zufällige ID | `NotFound` | ja |
| Repository (`ExposedMealPlanRepositoryTest`) | siehe Task 5 | Persistenz, Reihenfolge, Atomarität, Zutaten-Löschung | ja |

### Edge Cases Checklist
- [x] Empty input (leerer Name, keine Zutaten, nur Leerzeilen)
- [x] Maximum size input (60/61 Zeichen Name, 80/81 Zeichen Zutat, 30/31 Zeilen)
- [x] Invalid types (kein Datum, kein Montag, keine UUID, Jahr außerhalb 2000–2100 über `parseDate`)
- [x] Concurrent access (zwei Eltern belegen denselben Tag: Prüfung + Insert in einer Transaktion, Pool-Größe 1, eindeutiger Index als Netz)
- [ ] Network failure — App-Seite (Phase 3b, Offline-Cache)
- [x] Permission denied (Kind schreibt → 403; ohne Token → 401 über bestehende Auth)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew check
```
EXPECT: ktlint, detekt und Tests ohne Befunde

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
cd android && ./gradlew :core:api:compileDebugKotlin
```
EXPECT: generierter Client kompiliert mit den neuen Schemas

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Image baut (Migration liegt im Fat-JAR)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run
```
EXPECT: bei leerer DB „Successfully applied 3 migrations“; bei bestehender DB (Stand Phase 2) nur V3 angewendet

### Manual Validation
- [ ] Mit laufendem Server (`curl -k` gegen die lokale Instanz, Token aus Setup): Gericht für Dienstag anlegen → `GET …/weeks/<Montag>/plan` zeigt es, übrige Tage `dish: null`.
- [ ] Zweites Gericht für Dienstag → 409 mit deutscher Meldung.
- [ ] Gericht verschieben und löschen → Plan entsprechend.
- [ ] Mit Kind-Token: `GET` 200, `POST` 403.
- [ ] Nach Merge + Deploy auf dem Pi: Container startet, Log zeigt Migration V3, Health ok.

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
- [ ] Code follows discovered patterns (Service/Repository/Routen wie `attendance`)
- [ ] Error handling matches codebase style (`AppException`, deutsche Meldungen)
- [ ] Logging follows codebase conventions (nur IDs)
- [ ] Tests follow test patterns (Fakes, AAA, `runTest`, `client.call`)
- [ ] No hardcoded values (Grenzen als `const val`)
- [ ] Documentation updated
- [ ] No unnecessary scope additions (kein Status, keine Kindergerichte, keine Einkaufsliste)
- [ ] Self-contained — no questions needed during implementation

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Datenmodell passt nicht zu Phase 4 (Kindergerichte mit Status, Ablehnung, Absage) | M | M | Status per `ALTER TABLE ADD COLUMN` mit Default nachrüstbar; eindeutiger **Index** (nicht Constraint) auf `meal_date` lässt sich in Phase 4 ersetzen, falls z. B. abgelehnte Gerichte neben einem neuen stehen sollen |
| „Ein Gericht je Tag“ zu eng (z. B. Mittag + Abend) | L | M | PRD spricht von „Gericht je Tag“; Erweiterung um `slot` später per Migration |
| Zutaten-API als `string[]` reicht für Phase 5 (Abhaken) nicht | M | L | Einkaufsliste wird eigenes Modell in Phase 5 (aus Gerichten erzeugt); DB hält Zutaten bereits als Zeilen mit Position |
| Migrationsnummer kollidiert mit parallel entstehender Arbeit | L | H | Phase 3a ist die einzige laufende Backend-Arbeit; vor Merge `ls backend/*/src/main/resources/db/migration` prüfen |

## Notes
- Phase 3 wird wie Phase 1 und 2 aufgeteilt: **3a** (dieser Plan) liefert die API, **3b** baut darauf die App (Wochenplan-Screen
  für alle, Wochenwechsel „ab Sonntag kommende Woche“, Eltern-Eingabe mit Koch-Auswahl, Offline-Cache mit „Stand: …“-Hinweis
  für REQ-MEAL-002). Der 3b-Plan entsteht nach Merge von 3a mit `/ecc:prp-plan docs/01-requirements/meals.prd.md`.
- Annahmen, die vor der Umsetzung kurz bestätigt werden sollten (sonst gelten sie): ein Gericht je Tag; Zutaten bei
  Elterngerichten optional; Koch/Köchin eines Elterngerichts ist frei unter den Eltern wählbar (App schlägt den eigenen
  Namen vor).
- Die Koch/Köchin-Namen löst die App über `GET /api/v1/members` auf (wie die Kindernamen in Phase 2b); die API liefert nur IDs.
