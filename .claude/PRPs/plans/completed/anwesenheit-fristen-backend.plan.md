# Plan: Anwesenheit & Fristen – Backend (Phase 2a)

## Summary
Legt das erste Feature-Modul `backend/meals` an und setzt darin Anwesenheitsmuster, frei eintragbare Ausnahmen,
konfigurierbare Bestellfrist und die Berechnung der Kind-Einreichfrist um (REQ-MEAL-010–013). Die Fachlogik sind reine,
gründlich getestete Domänenfunktionen (Zeitzone `Europe/Berlin`); dazu kommen Migration, Repositories, Services, Routen
und der OpenAPI-Vertrag. Die Eltern-UI in der App folgt als eigener Plan **Phase 2b**.

## User Story
Als Elternteil möchte ich je Kind das Wechselmodell und Ausnahmen (Ferien) hinterlegen und die Bestellfrist einstellen,
damit Kochtage und Einreichfristen jeder Planwoche automatisch stimmen — und jedes kochende Kind sieht, bis wann es
einreichen muss.

## Problem → Solution
`core` kennt nur Mitglieder/Rollen, kein Modul `meals`, kein Anwesenheitsmodell → Modul `meals` liefert per API für jede
Planwoche die Anwesenheitstage jedes Kindes (Muster + Ausnahmen) sowie Bestellfrist und Kind-Frist; Eltern pflegen Profil
(„kocht“, Muster), Ausnahmen und Fristeinstellungen.

## Metadata
- **Complexity**: Large (neues Gradle-Modul + neue Muster für Feature-Module, ~30 Dateien, jede klein)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 2 – Anwesenheit & Fristen (Teil **2a**: Backend/API; Teil 2b: Android-Eltern-UI folgt)
- **Estimated Files**: ~30 (inkl. Tests, Doku, ADR)

---

## UX Design

### Before
N/A — reine Backend-/API-Änderung. Eltern können Anwesenheit heute nirgends hinterlegen.

### After
N/A — Bedienung kommt in Phase 2b über die App; bis dahin per `curl`. API-Ablauf:

```
Eltern (einmalig)                                  Alle (jede Woche)
───────────────────────────────────────────        ──────────────────────────────────────────
PUT  /api/v1/meals/profiles/{kindId}               GET /api/v1/meals/weeks/2026-10-26/attendance
 {cooks: true, pattern: {type: ALTERNATING,         → orderDeadline: 2026-10-25T15:00:00Z (So 16:00)
  weekA: [TUESDAY,WEDNESDAY,THURSDAY],                members: [{memberId, cooks,
  weekB: [TUESDAY..SUNDAY],                                      days: [{date, present, source}],
  referenceWeek: "2026-10-19"}}                                  submissionDeadline: Do 16:00 | So 16:00 | null}]
POST /api/v1/meals/profiles/{kindId}/exceptions
 {from: "2026-12-21", to: "2027-01-03",
  presence: PRESENT, note: "Weihnachtsferien"}
PUT  /api/v1/meals/settings
 {orderDeadlineDay: SUNDAY, orderDeadlineTime: "16:00",
  childDeadlineTime: "16:00"}  → gilt ab übernächster Woche
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Kochkind festlegen | – | `PUT /api/v1/meals/profiles/{memberId}` (`cooks`) | nur für Mitglieder mit Rolle CHILD |
| Wechselmodell | – | Muster `ALWAYS` oder `ALTERNATING` (Woche A/B + Referenzwoche) | Entscheidung Nutzer 2026-10-17 |
| Ferien/Abweichung | – | Ausnahmen (Zeitraum, anwesend/abwesend), löschbar | Überlappung → 409 |
| Bestellfrist | fest „So 16:00“ | `PUT /api/v1/meals/settings` | gilt ab der nächsten Planwoche |
| Frist sehen | – | `GET …/weeks/{weekStart}/attendance` | für alle Mitglieder lesbar |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Modulgrenzen, API-first, ADR-Pflicht, TDD, REQ-IDs in Testnamen |
| P0 | `docs/01-requirements/meals.prd.md` | „Fachliche Regeln“, REQ-MEAL-010–013, Phase 2 | fachliche Quelle |
| P0 | `docs/02-architecture/adr/0001-monorepo-modular-monolith.md`, `0007-meal-deadlines-without-remote-access.md` | all | Modulgrenze `meals → core`, Fristlogik |
| P0 | `backend/core/src/main/kotlin/de/familyplanner/core/member/*.kt` | all | Muster Domäne/Repository/Service |
| P0 | `backend/core/src/main/kotlin/de/familyplanner/core/web/*.kt`, `auth/RoleGuard.kt` | all | Muster Routen, Mapper, Rollenprüfung |
| P0 | `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt`, `MemberDeviceApiTest.kt` | all | Integrations-/Contract-Testmuster |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/persistence/*.kt`, `resources/db/migration/V1__auth.sql` | all | Tabellen, `dbQuery`, Flyway-Ort |
| P1 | `backend/core/src/test/kotlin/de/familyplanner/core/fakes/*.kt` | all | Fakes, die nach `testFixtures` wandern |
| P1 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 64-84 | Koin- und Routen-Registrierung |
| P1 | `api/openapi.yaml` | all | Envelope-Konvention, `allOf`+`nullable`, Fehlerantworten |
| P2 | `.claude/PRPs/reports/fundament-auth-backend-report.md` | „Deviations“ | Gotchas: Deadlock bei verschachteltem `dbQuery`, UUID-Mapping |
| P2 | `docs/03-design/core.md` | all | Vorlage für `docs/03-design/meals.md` |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Gradle Test Fixtures | https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures | Plugin `java-test-fixtures` in `core`; Quellen in `src/testFixtures/kotlin`; Nutzer: `testImplementation(testFixtures(project(":core")))`. Funktioniert mit `kotlin("jvm")`. |
| Exposed 1.x Upsert | Exposed-Doku „DSL CRUD operations → upsert“ | `org.jetbrains.exposed.v1.jdbc.upsert` — SQLite unterstützt `ON CONFLICT`; Schlüssel = Primary Key der Tabelle. |
| Flyway Classpath-Scan | Flyway-Doku „Migrations → Discovery“ | `classpath:db/migration` findet Skripte aus **allen** Jars/Modulen auf dem Classpath (auch im Fat-JAR). Versionen sind **global** pro Schema-History, nicht pro Modul. |
| java.time | JDK 21 API | `ZonedDateTime.of(date, time, ZoneId.of("Europe/Berlin")).toInstant()` behandelt Sommer-/Winterzeit; `TemporalAdjusters.previousOrSame(MONDAY)`; `ChronoUnit.WEEKS.between`. |

KEY_INSIGHT: Flyway-Versionen sind über Module hinweg global.
APPLIES_TO: Task 3 (Migration `V2__meals_attendance.sql`), ADR-0010.
GOTCHA: Phase 3 läuft parallel und braucht ebenfalls eine Migration → Phase 2a nimmt **V2**, Phase 3 **V3**. Wird Phase 3 zuerst gemergt, muss die später gemergte Migration auf die nächste freie Nummer umbenannt werden (vor dem Deploy; auf dem Pi darf nie eine niedrigere Version nachträglich auftauchen, `outOfOrder` bleibt aus).

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/Member.kt:5-17
@JvmInline
value class MemberId(
    val value: String,
)

enum class Role { PARENT, CHILD }

data class Member(
    val id: MemberId,
    val name: String,
    val role: Role,
    val createdAt: Instant,
)
```
Pakete nach Fachgebiet (`member`, `device`, `pairing`, `web`, `persistence`), Fehlermeldungen deutsch, Code/Kommentare englisch.

### ERROR_HANDLING
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/Validation.kt:7-17
fun validatedName(value: String, field: String, max: Int): String {
    val trimmed = value.trim()
    if (trimmed.isEmpty() || trimmed.length > max) {
        throw AppException.Validation("$field muss zwischen 1 und $max Zeichen lang sein")
    }
    return trimmed
}
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/MemberService.kt:30
suspend fun get(id: MemberId): Member = members.findById(id) ?: throw AppException.NotFound("Mitglied nicht gefunden")
```
Fehler **nur** als `AppException` (`Validation` 400, `Forbidden` 403, `NotFound` 404, `Conflict` 409); `StatusPages` in `app`
erzeugt Envelope. Keine eigenen Fehlercodes nötig.

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/MemberService.kt:15,24
private val log = LoggerFactory.getLogger(MemberService::class.java)
log.info("Member created: {}", member.id.value)
```
Nur IDs loggen, keine Namen/Notizen.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/ExposedMemberRepository.kt:18-33,74-80
class ExposedMemberRepository(private val db: Database) : MemberRepository {
    override suspend fun findById(id: MemberId): Member? =
        dbQuery(db) {
            MembersTable.selectAll().where { MembersTable.id eq id.value }.singleOrNull()?.toMember()
        }
}
internal fun ResultRow.toMember() = Member(id = MemberId(this[MembersTable.id]), ...)
```
Imports aus `org.jetbrains.exposed.v1.core.*` / `org.jetbrains.exposed.v1.jdbc.*` (Exposed 1.x — keine alten `org.jetbrains.exposed.sql`-Beispiele).
Zeitstempel als Epoch-Millis (`long`), Kalenderdaten als ISO-Text `yyyy-MM-dd` (lexikografisch vergleichbar).
**Atomare Prüf-und-Schreib-Operationen in *einer* Repository-Methode** (wie `createFirstParentWithDevice`), nie `dbQuery` verschachteln
(Pool-Größe 1 → Deadlock).

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/MemberService.kt:10-26
class MemberService(
    private val members: MemberRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun create(name: String, role: Role): Member {
        val validName = validatedName(name, "Name", MEMBER_NAME_MAX)
        if (members.existsByName(validName)) throw AppException.Conflict("Name bereits vergeben")
        ...
    }
}
```
`Clock` und ID-Erzeugung injiziert; kein `Instant.now()`/`LocalDate.now()` ohne Clock.

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/web/MemberRoutes.kt:28-49
authenticate(AUTH_DEVICE) {
    get("/api/v1/members") {
        call.principalOrThrow()
        call.respond(MemberListResponse(success = true, data = members.list().map { it.toApi() }))
    }
    post("/api/v1/members/{memberId}/pairing-codes") {
        call.requireParent()
        val memberId = MemberId(call.uuidParameter("memberId"))
        ...
        call.respond(HttpStatusCode.Created, PairingCodeResponse(success = true, data = issued.toApi()))
    }
}
```
Mapper als Extension-Funktionen mit Import-Alias für API-Typen (`import de.familyplanner.api.model.Member as ApiMember`, `ApiMappers.kt:7-12`).

### KOIN_AND_WIRING
```kotlin
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/CoreModule.kt:20-34 ; app/Application.kt:68-83
fun coreModule(dbPath: Path, setupToken: String?, clock: Clock) = module {
    single(createdAtStart = true) { DatabaseFactory.create(dbPath, clock) }
    single<MemberRepository> { ExposedMemberRepository(get()) }
    single { MemberService(get(), clock) }
}
install(Koin) { slf4jLogger(); modules(coreModule(config.dbPath, config.setupToken, clock)) }
routing { memberRoutes(get<MemberService>(), get<PairingService>()) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/core/src/test/kotlin/de/familyplanner/core/member/MemberServiceTest.kt:12-27
class MemberServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val service = MemberService(InMemoryMemberRepository(), clock)

    @Test
    fun `REQ-AUTH-001 parent creates member`() =
        runTest {
            // Act
            val member = service.create("Lea", Role.CHILD)
            // Assert
            assertEquals("Lea", member.name)
        }
}
// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/MemberDeviceApiTest.kt:19-30
@Test
fun `REQ-AUTH-001 parent creates and lists members`() =
    testApplication {
        application { module(testConfig(dir)) }
        val family = setupFamilyWithChild()
        val list = client.call(HttpMethod.Get, "/api/v1/members", family.parentToken)   // validiert gegen openapi.yaml
        assertEquals(HttpStatusCode.OK, list.status)
    }
```
kotlin.test + JUnit 5, `runTest`, Fakes statt Mocks, AAA-Kommentare, Testname beginnt mit REQ-ID.

---

## Domänenmodell & Regeln (verbindlich für die Umsetzung)

| Begriff | Definition |
|---|---|
| `ZONE` | `ZoneId.of("Europe/Berlin")` — alle Kalender-/Uhrzeitberechnungen |
| Planwoche (`PlanWeek`) | Montag (LocalDate) einer Woche Mo–So; Konstruktor wirft `Validation("Woche muss mit einem Montag beginnen")`, wenn kein Montag |
| Laufende Woche zu Planwoche P | `P − 7 Tage` … `P − 1 Tag` (Fristen liegen hier) |
| Aktuelle Planwoche (zum Zeitpunkt *now*) | `monday(today in ZONE) + 7 Tage` |
| `AttendancePattern.Always` | an allen Tagen anwesend |
| `AttendancePattern.Alternating(weekA, weekB, referenceWeek)` | `weekA`/`weekB`: `Set<DayOfWeek>`; `referenceWeek`: Montag einer A-Woche. Wochentyp von Datum d: `floorMod(WEEKS.between(referenceWeek, monday(d)), 2) == 0` → A, sonst B (funktioniert auch vor der Referenzwoche). Mindestens ein Tag in `weekA ∪ weekB`. |
| `AttendanceProfile` | `memberId`, `cooks: Boolean`, `pattern`. Nur für Rolle CHILD. Ohne gespeichertes Profil gilt **Default**: `cooks=false`, `Always`. |
| `AttendanceException` | `id` (UUID), `memberId`, `from ≤ to` (inklusive), `presence: PRESENT/ABSENT`, `note?` (getrimmt, 0–60 Zeichen; leer → null), `createdAt`. Länge max. **366 Tage**. Überlappung mit bestehender Ausnahme desselben Kindes → `Conflict("Zeitraum überschneidet sich mit einer bestehenden Ausnahme")` (Entscheidung Nutzer 2026-10-17). |
| Anwesenheit an Tag d | Ausnahme, die d enthält → deren `presence` (`source=EXCEPTION`), sonst Muster (`source=PATTERN`) |
| `DeadlineSettings` | `orderDeadlineDay: DayOfWeek` (Default SUNDAY), `orderDeadlineTime: LocalTime` (16:00), `childDeadlineTime: LocalTime` (16:00), `effectiveFrom: PlanWeek` |
| Gültige Einstellungen für Planwoche P | Eintrag mit größtem `effectiveFrom ≤ P`; keiner → Defaults |
| Ändern der Einstellungen (REQ-MEAL-013) | `effectiveFrom = aktuelle Planwoche + 7 Tage` (= „nächste Planwoche“; die gerade geplante Woche behält ihre Frist). Erneutes Ändern mit gleichem `effectiveFrom` überschreibt (Upsert). |
| Bestellfrist für P | `ZonedDateTime(laufendeWoche.monday + (orderDeadlineDay − 1), orderDeadlineTime, ZONE).toInstant()` |
| Kind-Frist für P (REQ-MEAL-012) | nur wenn `cooks`. Ist das Kind in P an **keinem** Tag anwesend → `null` (kann nicht kochen). Sonst: `last` = letzter Anwesenheitstag in der laufenden Woche; keiner → Bestellfrist; sonst `min(ZonedDateTime(last, childDeadlineTime, ZONE), Bestellfrist)` |

Beispiel (Abnahmekriterien): Muster A = Di–Do, B = Di–So. Laufende Woche Typ A → `last` = Do → **Do 16:00**. Typ B → `last` = So →
min(So 16:00, Bestellfrist So 16:00) = **Bestellfrist**. `Always` → So → **Bestellfrist**.

---

## API-Vertrag (Task 2, `api/openapi.yaml`)

Tag `meals`. Alle Endpunkte mit `deviceToken`; Lesen für alle Mitglieder, Schreiben nur Eltern.

| Methode | Pfad | Rolle | Body | Antwort |
|---|---|---|---|---|
| GET | `/api/v1/meals/profiles` | alle | – | 200 `AttendanceProfileListResponse` (alle CHILD-Mitglieder, Default-Profil wenn keins gespeichert; Reihenfolge wie `members`) |
| PUT | `/api/v1/meals/profiles/{memberId}` | Eltern | `UpdateAttendanceProfileRequest {cooks, pattern}` | 200 `AttendanceProfileResponse` / 400 (Muster ungültig, Mitglied ist PARENT) / 403 / 404 |
| GET | `/api/v1/meals/profiles/{memberId}/exceptions` | alle | – | 200 `AttendanceExceptionListResponse` (sortiert nach `from`) / 400 / 404 |
| POST | `/api/v1/meals/profiles/{memberId}/exceptions` | Eltern | `CreateAttendanceExceptionRequest {from, to, presence, note?}` | 201 `AttendanceExceptionResponse` / 400 / 403 / 404 / 409 |
| DELETE | `/api/v1/meals/exceptions/{exceptionId}` | Eltern | – | 204 / 400 / 403 / 404 |
| GET | `/api/v1/meals/settings` | alle | – | 200 `DeadlineSettingsOverviewResponse {current, scheduled?}` |
| PUT | `/api/v1/meals/settings` | Eltern | `UpdateDeadlineSettingsRequest {orderDeadlineDay, orderDeadlineTime, childDeadlineTime}` | 200 `DeadlineSettingsOverviewResponse` / 400 / 403 |
| GET | `/api/v1/meals/weeks/{weekStart}/attendance` | alle | – | 200 `WeekAttendanceResponse` / 400 (kein Montag/kein Datum) |

Schemas:
- `Weekday`: enum `MONDAY … SUNDAY` (Namen = `java.time.DayOfWeek`, Mapping per `valueOf(name)`).
- `PatternType`: enum `ALWAYS, ALTERNATING`. `Presence`: enum `PRESENT, ABSENT`. `AttendanceSource`: enum `PATTERN, EXCEPTION`.
- `AttendancePattern {type (req), weekA: Weekday[] (uniqueItems), weekB: Weekday[] (uniqueItems), referenceWeek: string/date nullable}` — **kein `oneOf`** (Generator); bei `ALWAYS` liefert der Server `weekA=[]`, `weekB=[]`, `referenceWeek=null`.
- `AttendanceProfile {memberId (uuid), cooks, pattern}` (alle required).
- `AttendanceException {id, memberId, from (date), to (date), presence, note (nullable, maxLength 60), createdAt (date-time)}`.
- `DeadlineSettings {orderDeadlineDay, orderDeadlineTime, childDeadlineTime, effectiveFrom (date)}`; Uhrzeit `type: string, pattern: '^([01][0-9]|2[0-3]):[0-5][0-9]$'`.
- `DeadlineSettingsOverview {current (required), scheduled (allOf + nullable)}`.
- `AttendanceDay {date, present, source}`; `MemberWeekAttendance {memberId, cooks, days (7 Einträge Mo–So), submissionDeadline (date-time, nullable)}`;
  `WeekAttendance {weekStart (date), orderDeadline (date-time), members[]}`.
- Envelopes explizit je Typ wie bestehend (`{success (req), data (allOf+nullable bzw. array nullable), error (allOf+nullable)}`).
- Parameter `ExceptionId` (uuid), `WeekStart` (`in: path, schema: {type: string, format: date}`).
- Datum/Zeit-Ausgabe: Daten als `LocalDate.toString()`, Zeitpunkte als `Instant.toString()` (UTC, wie `createdAt`).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/02-architecture/adr/0010-backend-feature-modules.md` | CREATE | Konvention für Feature-Module (Gradle, Koin, Routen, Migrationsnummern, testFixtures) |
| `docs/02-architecture/README.md` | UPDATE | ADR-0010 in Liste/Tabelle |
| `docs/03-design/meals.md` | CREATE | Moduldesign Anwesenheit & Fristen (Regeln oben, Tabellen, Testfälle) |
| `api/openapi.yaml` | UPDATE | Tag `meals`, 8 Operationen, Schemas (API-first) |
| `backend/settings.gradle.kts` | UPDATE | `include(":meals")` |
| `backend/meals/build.gradle.kts` | CREATE | Feature-Modul, hängt nur von `:core` ab |
| `backend/core/build.gradle.kts` | UPDATE | `java-test-fixtures` |
| `backend/core/src/testFixtures/kotlin/de/familyplanner/core/fakes/*.kt` | MOVE | `Builders.kt`, `MutableClock.kt`, `InMemoryStore.kt`, `FakeSecretGenerator.kt` aus `src/test` (Package bleibt) |
| `backend/meals/src/main/resources/db/migration/V2__meals_attendance.sql` | CREATE | 3 Tabellen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/MealsModule.kt` | CREATE | Koin-Modul |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Attendance.kt` | CREATE | Domänentypen (`PlanWeek`, Pattern, Profile, Exception, Presence, Source, `ZONE`) |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceCalculator.kt` | CREATE | reine Funktionen: Anwesenheit je Tag/Woche |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/Deadlines.kt` | CREATE | `DeadlineSettings`, reine Fristfunktionen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceRepository.kt` | CREATE | Interface Profile + Ausnahmen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/DeadlineSettingsRepository.kt` | CREATE | Interface Einstellungen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/MealsTables.kt` | CREATE | Exposed-Tabellen |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedAttendanceRepository.kt` | CREATE | inkl. atomarer Überlappungsprüfung |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/persistence/ExposedDeadlineSettingsRepository.kt` | CREATE | Upsert, „gültig für P“ |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt` | CREATE | Profile, Ausnahmen, Wochenübersicht |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/DeadlineSettingsService.kt` | CREATE | Lesen/Ändern mit Wirksamkeit ab nächster Planwoche |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/MealsApiMappers.kt` | CREATE | Domäne ↔ API-Modelle, Datums-/Zeit-Parsing |
| `backend/meals/src/main/kotlin/de/familyplanner/meals/web/AttendanceRoutes.kt` | CREATE | 8 Routen |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/fakes/InMemoryAttendanceRepositories.kt` | CREATE | Fakes |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/AttendanceCalculatorTest.kt` | CREATE | REQ-MEAL-010/011 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/DeadlinesTest.kt` | CREATE | REQ-MEAL-012/013 inkl. DST |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/AttendanceServiceTest.kt` | CREATE | Validierung, Rollen, Überlappung |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/attendance/DeadlineSettingsServiceTest.kt` | CREATE | REQ-MEAL-013 |
| `backend/meals/src/test/kotlin/de/familyplanner/meals/persistence/ExposedAttendanceRepositoryTest.kt` | CREATE | echte SQLite (TempDir) inkl. V1+V2 |
| `backend/app/build.gradle.kts` | UPDATE | `implementation(project(":meals"))`, `kover(project(":meals"))` |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `mealsModule(clock)` + `attendanceRoutes(...)` |
| `backend/app/src/test/kotlin/de/familyplanner/app/ApiTestSupport.kt` | UPDATE | Helfer `putProfile`, Fixed-Clock-Konstante |
| `backend/app/src/test/kotlin/de/familyplanner/app/MealsAttendanceApiTest.kt` | CREATE | Integrations-/Contract-Tests |
| `backend/README.md` | UPDATE | Modultabelle um `meals` |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-010–013 |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 2 → in-progress, 2a/2b, Plan-Link |

## NOT Building

- Android-UI für Muster, Ausnahmen, Einstellungen und Fristanzeige → **Phase 2b** (eigener Plan).
- Wochenplan, Gerichte, Kochtag-Auswahl (REQ-MEAL-021 nutzt später `WeekAttendance`) → Phase 3/4.
- Erinnerungen (nutzen später `submissionDeadline`) → Phase 6.
- Anwesenheit/Muster für Eltern (immer anwesend, nicht Teil der Übersicht).
- Ausnahmen bearbeiten (nur anlegen/löschen; Ändern = löschen + neu).
- Historie/Audit der Musteränderungen; Löschen von Mitgliedern (Cascade ist im SQL vorbereitet).
- Event-Bus in `core` (noch kein Modul-übergreifender Bedarf).
- Änderungen an bestehenden AUTH-Endpunkten oder am `Member`-Schema.

---

## Step-by-Step Tasks

### Task 1: ADR & Moduldesign (V-Modell links)
- **ACTION**: `docs/02-architecture/adr/0010-backend-feature-modules.md` (Format `0000-template.md`) und `docs/03-design/meals.md` anlegen; ADR in `docs/02-architecture/README.md` eintragen.
- **IMPLEMENT** ADR-0010 „Backend-Feature-Module“: Gradle-Modul `backend/<feature>` hängt nur von `:core` ab (`api(project(":core"))` nicht nötig → `implementation`); Paket `de.familyplanner.<feature>`; eigenes Koin-Modul `<feature>Module(clock)`; Routen-Extension `Route.<x>Routes(...)`, registriert in `app/Application.kt`; Pfade `/api/v1/<feature>/…`, Tag `<feature>`; Migrationen im Modul unter `src/main/resources/db/migration/`, **globale fortlaufende Versionsnummer** mit Modulpräfix im Namen (`V2__meals_attendance.sql`), bei Merge-Kollision die später gemergte umnummerieren, nie eine bereits deployte Version ändern; Tabellenpräfix `<feature>_`; FK auf `members(id)` erlaubt (Modul hängt von `core` ab); geteilte Test-Fakes über `testFixtures` von `core`. Alternativen: Migrationen zentral in `core` (verletzt Modulgrenze), eigener Flyway-Ort + eigene History-Tabelle je Modul (mehr Komplexität, Reihenfolge der FKs unklar) — verworfen.
- **IMPLEMENT** `meals.md` nach `docs/03-design/README.md`: abgedeckte REQ-MEAL-010–013, Abschnitt „Domänenmodell & Regeln“ dieses Plans (inkl. Entscheidung „zwei Wochentypen“ und „Überlappung → 409“), Endpunkte (Verweis openapi), Tabellen (Task 3), Unit-Testfälle (Testing Strategy). Vermerk: Anwesenheitsmodell verallgemeinert den PRD-Wortlaut „Ankunft Di, Abreise Do/So“ (A = Di–Do, B = Di–So).
- **GOTCHA**: CLAUDE.md verlangt ADR vor Abweichungen — das Zwei-Wochentypen-Modell ist eine Verallgemeinerung, keine Abweichung; in `meals.md` dokumentieren genügt.
- **VALIDATE**: Links funktionieren; ADR-Tabelle aktuell.

### Task 2: OpenAPI-Vertrag (Skill `contract-first`, ADR-0004)
- **ACTION**: `api/openapi.yaml` um Abschnitt „API-Vertrag“ oben erweitern; `info.version` → `0.2.0`; Tag `meals` ergänzen.
- **IMPLEMENT**: Operationen mit `operationId` (`listAttendanceProfiles`, `updateAttendanceProfile`, `listAttendanceExceptions`, `createAttendanceException`, `deleteAttendanceException`, `getDeadlineSettings`, `updateDeadlineSettings`, `getWeekAttendance`), jede mit `'401'`, `'429'` und den passenden Fehlerantworten (`$ref: '#/components/responses/...'`). Eingabegrenzen ins Schema (`note` maxLength 60, Uhrzeit-Pattern, `uniqueItems`, `required`-Listen).
- **MIRROR**: Envelope-Schemas wie `MemberResponse`/`MemberListResponse` (`openapi.yaml:362-388`).
- **GOTCHA**: Kein `oneOf`/Discriminator (Generator erzeugt unbrauchbare Typen). Nullable Objekt-Referenz nur als `allOf: [$ref] + nullable: true`. `required` sorgfältig — sonst werden Felder nullable. Die Android-Generierung (`android/core/api`, `jvm-retrofit2`) läuft in der Android-CI auf `api/**` mit — muss weiter kompilieren (keine Nutzung nötig).
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` erzeugt u. a. `AttendancePattern`, `WeekAttendanceResponse`, `Weekday`; `cd android && ./gradlew :core:api:compileDebugKotlin` grün.

### Task 3: Modul-Gerüst, testFixtures, Migration
- **ACTION**: Modul anlegen, Fakes von `core` nach `testFixtures` verschieben, Migration schreiben.
- **IMPLEMENT**:
  - `backend/settings.gradle.kts`: `include(":api-contract", ":core", ":meals", ":app")`.
  - `backend/meals/build.gradle.kts`:
    ```kotlin
    plugins { id("familyplanner.kotlin-jvm") }
    dependencies {
        implementation(project(":core"))
        implementation(libs.koin.ktor)
        implementation(libs.logback)
        testImplementation(testFixtures(project(":core")))
    }
    ```
  - `backend/core/build.gradle.kts`: Plugin `` `java-test-fixtures` `` ergänzen; `dependencies { testFixturesImplementation(libs.coroutines.core) }` falls nötig. Dateien `Builders.kt`, `MutableClock.kt`, `InMemoryStore.kt`, `FakeSecretGenerator.kt` per `git mv` nach `backend/core/src/testFixtures/kotlin/de/familyplanner/core/fakes/` (Package unverändert). `TokenHasherAccess.kt` bleibt in `src/test` (testet `internal`).
  - `V2__meals_attendance.sql`:
    ```sql
    CREATE TABLE meal_attendance_profiles (
      member_id TEXT PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
      cooks INTEGER NOT NULL CHECK (cooks IN (0,1)),
      pattern_type TEXT NOT NULL CHECK (pattern_type IN ('ALWAYS','ALTERNATING')),
      week_a_mask INTEGER NOT NULL DEFAULT 0 CHECK (week_a_mask BETWEEN 0 AND 127),
      week_b_mask INTEGER NOT NULL DEFAULT 0 CHECK (week_b_mask BETWEEN 0 AND 127),
      reference_week TEXT,
      updated_at INTEGER NOT NULL
    );
    CREATE TABLE meal_attendance_exceptions (
      id TEXT PRIMARY KEY,
      member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
      from_date TEXT NOT NULL,
      to_date TEXT NOT NULL,
      presence TEXT NOT NULL CHECK (presence IN ('PRESENT','ABSENT')),
      note TEXT,
      created_at INTEGER NOT NULL,
      CHECK (from_date <= to_date)
    );
    CREATE INDEX idx_meal_exceptions_member ON meal_attendance_exceptions(member_id, from_date);
    CREATE TABLE meal_deadline_settings (
      effective_from TEXT PRIMARY KEY,
      order_deadline_day INTEGER NOT NULL CHECK (order_deadline_day BETWEEN 1 AND 7),
      order_deadline_time TEXT NOT NULL,
      child_deadline_time TEXT NOT NULL,
      updated_at INTEGER NOT NULL
    );
    ```
    Tagesmaske: Bit `(dayOfWeek.value − 1)` (Mo = Bit 0 … So = Bit 6).
- **GOTCHA**: `core`-Testressource `db/extra/V2__extra.sql` liegt in anderem Ort und nur im `core`-Test-Classpath → keine Kollision. Flyway im `meals`-Test findet V1 (aus `core`-Jar) **und** V2 (Modulressource) über `classpath:db/migration`. Falls ktlint/detekt für `testFixtures` nicht greifen, ist das akzeptabel (Testcode).
- **VALIDATE**: `cd backend && ./gradlew :core:test :meals:compileKotlin` grün (core-Tests finden Fakes weiterhin).

### Task 4: Domäne – reine Funktionen (TDD: erst Tests RED)
- **ACTION**: `Attendance.kt`, `AttendanceCalculator.kt`, `Deadlines.kt` mit Tests `AttendanceCalculatorTest`, `DeadlinesTest`.
- **IMPLEMENT** (Typen):
  ```kotlin
  val ZONE: ZoneId = ZoneId.of("Europe/Berlin")

  @JvmInline value class PlanWeek(val monday: LocalDate) {
      init { if (monday.dayOfWeek != DayOfWeek.MONDAY) throw AppException.Validation("Woche muss mit einem Montag beginnen") }
      val days: List<LocalDate> get() = (0L..6L).map { monday.plusDays(it) }
      fun previous() = PlanWeek(monday.minusWeeks(1))
      fun next() = PlanWeek(monday.plusWeeks(1))
      companion object {
          fun containing(date: LocalDate) = PlanWeek(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
          /** The week currently being planned: the one after today's week. */
          fun current(clock: Clock) = containing(LocalDate.now(clock.withZone(ZONE))).next()
      }
  }
  sealed interface AttendancePattern {
      data object Always : AttendancePattern
      data class Alternating(val weekA: Set<DayOfWeek>, val weekB: Set<DayOfWeek>, val referenceWeek: PlanWeek) : AttendancePattern
  }
  enum class Presence { PRESENT, ABSENT }
  enum class AttendanceSource { PATTERN, EXCEPTION }
  @JvmInline value class ExceptionId(val value: String)
  data class AttendanceProfile(val memberId: MemberId, val cooks: Boolean, val pattern: AttendancePattern) {
      companion object { fun default(memberId: MemberId) = AttendanceProfile(memberId, cooks = false, pattern = AttendancePattern.Always) }
  }
  data class AttendanceException(val id: ExceptionId, val memberId: MemberId, val from: LocalDate, val to: LocalDate,
      val presence: Presence, val note: String?, val createdAt: Instant) {
      fun contains(date: LocalDate) = !date.isBefore(from) && !date.isAfter(to)
      fun overlaps(other: AttendanceException) = !from.isAfter(other.to) && !to.isBefore(other.from)
  }
  data class AttendanceDay(val date: LocalDate, val present: Boolean, val source: AttendanceSource)
  ```
  `AttendanceCalculator.kt` (Top-Level-Funktionen): `fun AttendancePattern.isPresentOn(date: LocalDate): Boolean`,
  `fun attendanceOn(date, profile, exceptions): AttendanceDay`, `fun attendanceFor(week: PlanWeek, profile, exceptions): List<AttendanceDay>`.
  `Deadlines.kt`: `data class DeadlineSettings(orderDeadlineDay, orderDeadlineTime, childDeadlineTime, effectiveFrom: PlanWeek)` mit
  `companion fun defaults(effectiveFrom)` (SUNDAY, 16:00, 16:00, Konstanten `DEFAULT_DEADLINE_TIME = LocalTime.of(16, 0)`);
  `fun orderDeadline(week: PlanWeek, settings): Instant`;
  `fun submissionDeadline(week: PlanWeek, settings, profile, exceptions): Instant?` (Regeln aus „Domänenmodell & Regeln“).
- **MIRROR**: NAMING_CONVENTION; Validierung über `AppException.Validation`.
- **GOTCHA**: `Clock.withZone(ZONE)` — `MutableClock.withZone` gibt `this` (UTC) zurück! Deshalb `LocalDate.ofInstant(clock.instant(), ZONE)` statt `LocalDate.now(clock.withZone(...))` verwenden. Wochenparität mit `Math.floorMod`. Keine Magic Numbers (`DAYS_PER_WEEK = 7`).
- **VALIDATE**: `./gradlew :meals:test --tests '*AttendanceCalculatorTest*' --tests '*DeadlinesTest*'` grün.

### Task 5: Repositories (Interfaces, Exposed, Fakes)
- **ACTION**: Interfaces, Exposed-Implementierungen, In-Memory-Fakes, `ExposedAttendanceRepositoryTest`.
- **IMPLEMENT**:
  ```kotlin
  interface AttendanceRepository {
      suspend fun findProfiles(): List<AttendanceProfile>
      suspend fun findProfile(memberId: MemberId): AttendanceProfile?
      suspend fun saveProfile(profile: AttendanceProfile, at: Instant): AttendanceProfile      // upsert
      suspend fun findExceptions(memberId: MemberId): List<AttendanceException>            // sortiert nach from
      suspend fun findExceptionsBetween(from: LocalDate, to: LocalDate): List<AttendanceException> // alle Mitglieder, überlappend
      /** Atomically inserts unless it overlaps an existing exception of the same member; returns false on overlap. */
      suspend fun createExceptionIfNoOverlap(exception: AttendanceException): Boolean
      suspend fun deleteException(id: ExceptionId): Boolean
  }
  interface DeadlineSettingsRepository {
      suspend fun findEffective(week: PlanWeek): DeadlineSettings?   // größtes effectiveFrom <= week
      suspend fun findScheduledAfter(week: PlanWeek): DeadlineSettings? // kleinstes effectiveFrom > week
      suspend fun save(settings: DeadlineSettings, at: Instant)       // upsert über effective_from
  }
  ```
  `MealsTables.kt`: `MealAttendanceProfilesTable`, `MealAttendanceExceptionsTable`, `MealDeadlineSettingsTable` (Spalten wie SQL; Datum als `varchar(10)`, Uhrzeit `varchar(5)`, Masken `integer`). Mapping-Helfer `Set<DayOfWeek>.toMask()` / `Int.toDays()` (internal, in `MealsTables.kt` oder `persistence/Mappers.kt`).
  Überlappung in **einer** `dbQuery`: `selectAll().where { (memberId eq m) and (fromDate lessEq new.to) and (toDate greaterEq new.from) }.empty()` → dann `insert`.
- **MIRROR**: REPOSITORY_PATTERN; `ExposedMemberRepository.insertMember` für Insert, `ExposedDeviceRepository.revoke` für Update/Delete mit Zeilenzahl.
- **GOTCHA**: Kein verschachteltes `dbQuery`. `upsert` aus `org.jetbrains.exposed.v1.jdbc.upsert`. ISO-Datumsstrings sind lexikografisch vergleichbar — nur weil immer `yyyy-MM-dd` (Jahr 4-stellig) gespeichert wird. Fakes in `meals/src/test/.../fakes/InMemoryAttendanceRepositories.kt` mit identischer Semantik (Sortierung, Überlappung, Upsert).
- **VALIDATE**: `ExposedAttendanceRepositoryTest` (TempDir, `DatabaseFactory.create(dir.resolve("t.db"), clock)`, Mitglied per `ExposedMemberRepository.create(aMember())` anlegen): Profil-Upsert, Masken-Rundreise, Überlappung → `false`, angrenzende Zeiträume ok, Delete, Settings `findEffective`/`findScheduledAfter`.

### Task 6: Services (TDD)
- **ACTION**: `AttendanceService`, `DeadlineSettingsService` + Tests mit Fakes.
- **IMPLEMENT**:
  ```kotlin
  class AttendanceService(
      private val members: MemberRepository,
      private val attendance: AttendanceRepository,
      private val settings: DeadlineSettingsService,
      private val clock: Clock,
      private val newId: () -> String = { UUID.randomUUID().toString() },
  ) {
      suspend fun profiles(): List<AttendanceProfile>          // alle CHILD aus members.findAll(), gespeichert oder default
      suspend fun updateProfile(memberId: MemberId, cooks: Boolean, pattern: AttendancePattern): AttendanceProfile
      suspend fun exceptions(memberId: MemberId): List<AttendanceException>
      suspend fun addException(memberId: MemberId, from: LocalDate, to: LocalDate, presence: Presence, note: String?): AttendanceException
      suspend fun removeException(id: ExceptionId)
      suspend fun week(week: PlanWeek): WeekAttendance
  }
  data class MemberWeekAttendance(val memberId: MemberId, val cooks: Boolean, val days: List<AttendanceDay>, val submissionDeadline: Instant?)
  data class WeekAttendance(val week: PlanWeek, val orderDeadline: Instant, val members: List<MemberWeekAttendance>)

  class DeadlineSettingsService(private val repository: DeadlineSettingsRepository, private val clock: Clock) {
      suspend fun effectiveFor(week: PlanWeek): DeadlineSettings = repository.findEffective(week) ?: DeadlineSettings.defaults(week)
      suspend fun overview(): DeadlineSettingsOverview   // current = effectiveFor(PlanWeek.current(clock)), scheduled = findScheduledAfter(current)
      suspend fun update(day: DayOfWeek, orderTime: LocalTime, childTime: LocalTime): DeadlineSettingsOverview  // effectiveFrom = current.next()
  }
  ```
  `requireChild(memberId)`: `members.findById` → null ⇒ `NotFound("Mitglied nicht gefunden")`; Rolle PARENT ⇒ `Validation("Anwesenheit gilt nur für Kinder")`.
  Muster-Validierung: `Alternating` braucht `referenceWeek` (Montag) und ≥ 1 Tag in A ∪ B, sonst `Validation`. Ausnahme-Validierung: `from ≤ to`, Länge ≤ 366 Tage, Notiz getrimmt ≤ 60 (leer → null); Überlappung ⇒ `Conflict(...)`. `removeException`: false ⇒ `NotFound("Ausnahme nicht gefunden")`.
  `week()`: Mitglieder, Profile, `findExceptionsBetween(week.previous().monday, week.days.last())` (laufende + Planwoche), Settings je **einmal** laden, dann rein rechnen. Log `info` bei Profil-/Ausnahme-/Settings-Änderung (nur IDs).
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING, LOGGING_PATTERN.
- **GOTCHA**: Reihenfolge der Kinder = Reihenfolge von `members.findAll()` (createdAt). Repositories nacheinander aufrufen, nicht verschachtelt.
- **VALIDATE**: `./gradlew :meals:test` grün.

### Task 7: Routen, Mapper, Koin, App-Verdrahtung
- **ACTION**: `MealsApiMappers.kt`, `AttendanceRoutes.kt`, `MealsModule.kt`; `app` anpassen.
- **IMPLEMENT**:
  - `fun mealsModule(clock: Clock) = module { single<AttendanceRepository> { ExposedAttendanceRepository(get()) }; single<DeadlineSettingsRepository> { ExposedDeadlineSettingsRepository(get()) }; single { DeadlineSettingsService(get(), clock) }; single { AttendanceService(get(), get(), get(), clock) } }` — `Database` und `MemberRepository` kommen aus `coreModule`.
  - `fun Route.attendanceRoutes(attendance: AttendanceService, settings: DeadlineSettingsService)` mit `authenticate(AUTH_DEVICE) { … }`; Lesen `call.principalOrThrow()`, Schreiben `call.requireParent()`; IDs per `call.uuidParameter(...)`; `weekStart` per Helfer `call.weekParameter()` → `LocalDate.parse` (Fehler ⇒ `Validation("Ungültiges Datum 'weekStart'")`) → `PlanWeek(...)`.
  - Mapper: `parseDate(value, field)`, `parseTime(value, field)` (`LocalTime.parse`, Fehler ⇒ `Validation`), `DayOfWeek ↔ ApiWeekday` per `valueOf(name)`, Pattern-Mapping (`ALWAYS` ignoriert weekA/weekB/referenceWeek; `ALTERNATING` ohne `referenceWeek` ⇒ `Validation`). Zeiten als `"%02d:%02d"` ausgeben.
  - `app/build.gradle.kts`: `implementation(project(":meals"))`, `kover(project(":meals"))`.
  - `Application.kt`: `modules(coreModule(...), mealsModule(clock))`; im `routing`: `attendanceRoutes(get<AttendanceService>(), get<DeadlineSettingsService>())`.
- **MIRROR**: ROUTE_PATTERN, KOIN_AND_WIRING.
- **GOTCHA**: Generierte Modelle haben `String` für `date`/`date-time` (`dateLibrary=string`) — Parsing nur in Mappern. Generierte Enum-Konstanten in UPPERCASE (`enumPropertyNaming`). Koin-`get()` in `app` braucht Import `org.koin.ktor.ext.get`.
- **VALIDATE**: `./gradlew :app:compileKotlin` grün; `FP_SETUP_TOKEN=local-setup-token-123 ./gradlew :app:run` startet, Flyway meldet „Applied 2 migrations“ bei leerer DB.

### Task 8: Integrations- & Contract-Tests
- **ACTION**: `MealsAttendanceApiTest` in `backend/app/src/test/...`; Helfer in `ApiTestSupport.kt`.
- **IMPLEMENT**: Fixe Uhr `val FIXED_NOW: Clock = Clock.fixed(Instant.parse("2026-10-14T10:00:00Z"), ZoneOffset.UTC)` (Mi, 14.10.2026 → aktuelle Planwoche 2026-10-19); `application { module(testConfig(dir), FIXED_NOW) }`; Helfer `putAlternatingProfile(token, memberId)`. Tests (jeder Request über `client.call(...)` → Contract-Validierung):
  - `REQ-MEAL-010 parent sets alternating pattern and week attendance follows it` (Referenz 2026-10-19 = A: Planwoche 19.10. → Di–Do anwesend; Planwoche 26.10. → Di–So).
  - `REQ-MEAL-010 child without profile is present every day`.
  - `REQ-MEAL-011 exception overrides pattern and removal restores it` (PRESENT in Abwesenheit, ABSENT in Anwesenheit, DELETE → Muster; `source` prüfen).
  - `REQ-MEAL-011 overlapping exception returns 409`.
  - `REQ-MEAL-012 submission deadline is Thursday 16:00 when child leaves Thursday` (Planwoche 2026-10-26 → laufende Woche 19.–25.10. Typ A → `2026-10-22T14:00:00Z`) und `… equals order deadline when present until Sunday` (Planwoche 2026-11-02 → laufende Woche 26.10.–01.11. Typ B → `2026-11-01T15:00:00Z`, Winterzeit seit 25.10.!).
  - `REQ-MEAL-013 changed order deadline applies from next plan week` (PUT FRIDAY 18:00 → GET settings: `current` unverändert, `scheduled.effectiveFrom=2026-10-26`; Woche 26.10. hat Bestellfrist Fr 23.10. 18:00 = `2026-10-23T16:00:00Z`, Woche 19.10. unverändert).
  - `REQ-AUTH-002 child cannot change profiles, exceptions or settings` (403 ×4) und `child can read week attendance and profiles` (200).
  - Validierung (400, `invalidRequestExpected = true` wo das Schema schon verletzt ist): `weekStart` kein Montag / kein Datum, Uhrzeit `"25:00"`, `ALTERNATING` ohne `referenceWeek`, Profil für PARENT, Ausnahme `from > to`, Notiz 61 Zeichen; 404 für unbekanntes Mitglied/unbekannte Ausnahme.
- **MIRROR**: TEST_STRUCTURE (`MemberDeviceApiTest`).
- **GOTCHA**: Zeitpunkte in UTC vergleichen (Sommerzeit bis 25.10.2026 03:00 → 16:00 = 14:00Z; danach 15:00Z). Elternteil-ID für den PARENT-Fall über `GET /api/v1/me` holen.
- **VALIDATE**: `./gradlew :app:test` grün.

### Task 9: Qualität, Coverage, Doku & Traceability
- **ACTION**: Statische Analyse und Coverage; Doku nachziehen.
- **IMPLEMENT**: `backend/README.md` Modultabelle (`meals` – Anwesenheit & Fristen, später Wochenplan …); `traceability.md` Zeilen REQ-MEAL-010–013: Design `[meals](../03-design/meals.md)`, Unit `AttendanceCalculatorTest`/`DeadlinesTest`/`AttendanceServiceTest`/`DeadlineSettingsServiceTest` (`REQ-MEAL-01x …`), Integration `MealsAttendanceApiTest`, Status „Backend umgesetzt (App folgt in 2b)“; `meals.prd.md` Phase-2-Zeile (siehe unten).
- **VALIDATE**: alle Validation Commands unten grün.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-010 always pattern is present every day` | `Always`, beliebige Woche | 7× present, PATTERN | |
| `REQ-MEAL-010 alternating pattern uses week A in reference week` | A=Di–Do, B=Di–So, Ref 2026-10-19, Woche 19.10. | nur Di, Mi, Do present | |
| `REQ-MEAL-010 alternating pattern uses week B in following week` | Woche 26.10. | Di–So present, Mo absent | |
| `REQ-MEAL-010 parity works before reference week and across years` | Woche 2026-10-12 (B), 2026-12-28 / 2027-01-04 | B bzw. korrekte Parität | ja |
| `REQ-MEAL-011 present exception overrides absent pattern day` | Mo als PRESENT-Ausnahme | Mo present, EXCEPTION | |
| `REQ-MEAL-011 absent exception overrides present pattern day` | Mi–Fr ABSENT | absent, EXCEPTION | |
| `REQ-MEAL-011 exception boundaries are inclusive` | from=to=Do | nur Do betroffen | ja |
| `REQ-MEAL-012 deadline is Thursday 16:00 when last present day is Thursday` | laufende Woche Typ A | Do 16:00 Berlin | |
| `REQ-MEAL-012 deadline equals order deadline when present until Sunday` | Typ B | Bestellfrist | |
| `REQ-MEAL-012 permanent child deadline equals order deadline` | `Always` | Bestellfrist | |
| `REQ-MEAL-012 deadline never later than order deadline` | Bestellfrist Fr 12:00, Kind bis So | Fr 12:00 | ja |
| `REQ-MEAL-012 child absent all running week falls back to order deadline` | ABSENT-Ausnahme laufende Woche | Bestellfrist | ja |
| `REQ-MEAL-012 child absent all plan week has no deadline` | ABSENT-Ausnahme Planwoche | `null` | ja |
| `REQ-MEAL-012 non-cooking child has no deadline` | `cooks=false` | `null` | |
| `REQ-MEAL-012 deadline respects daylight saving change` | So 25.10.2026 bzw. 29.03.2026 | 15:00Z bzw. 14:00Z | ja |
| `REQ-MEAL-013 defaults are Sunday 16:00` | keine Settings | SUNDAY, 16:00, 16:00 | |
| `REQ-MEAL-013 changed settings apply from next plan week` | now Mi 14.10.; update | effectiveFrom 26.10.; Woche 19.10. alt | |
| `REQ-MEAL-013 changing twice in same week overwrites scheduled settings` | 2× update | 1 Eintrag, letzter Wert | ja |
| `REQ-MEAL-013 current plan week is computed in Berlin time` | now `2026-10-18T22:30:00Z` (= Mo 19.10. 00:30 Berlin) | aktuelle Planwoche 2026-10-26 (UTC-Rechnung ergäbe fälschlich 19.10.) | ja |
| `PlanWeek rejects non-Monday` | 2026-10-20 | `AppException.Validation` | ja |
| `REQ-MEAL-010 alternating pattern without days is rejected` | A=B=∅ | Validation | ja |
| `REQ-MEAL-010 profile for parent is rejected` / `unknown member returns not found` | | Validation / NotFound | ja |
| `REQ-MEAL-011 overlapping exception is rejected, adjacent is accepted` | 1.–5. + 5.–7. / 6.–7. | Conflict / ok | ja |
| `REQ-MEAL-011 exception longer than 366 days or from after to is rejected` | | Validation | ja |
| `REQ-MEAL-011 note is trimmed, blank becomes null, 61 chars rejected` | | | ja |
| `REQ-MEAL-011 removing unknown exception returns not found` | | NotFound | ja |

### Edge Cases Checklist
- [x] Empty input (keine Tage im Muster, leere Notiz, kein Profil gespeichert)
- [x] Maximum size input (Notiz 60/61, Ausnahme 366/367 Tage)
- [x] Invalid types (kein Montag, kein Datum, Uhrzeit 25:00, unbekannter Wochentag/Enum, Nicht-UUID)
- [x] Concurrent access (Überlappungsprüfung + Insert atomar in einer Transaktion)
- [ ] Network failure — n/a (Backend)
- [x] Permission denied (Kind → alle Schreib-Endpunkte 403; ohne Token 401)
- [x] Zeit: Sommer-/Winterzeitwechsel, Jahreswechsel, Wochenparität vor der Referenzwoche

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Befunde

### Unit Tests
```bash
cd backend && ./gradlew :core:test :meals:test
```
EXPECT: alle grün (core-Tests laufen weiter mit den nach `testFixtures` verschobenen Fakes)

### Full Test Suite + Coverage
```bash
cd backend && ./gradlew check koverVerify
```
EXPECT: grün, Coverage ≥ 80 %

### Database Validation
```bash
cd backend && ./gradlew :meals:test --tests '*ExposedAttendanceRepositoryTest*'
```
EXPECT: V1 + V2 angewendet, Constraints greifen

### Android-Vertrag kompiliert weiter
```bash
cd android && ./gradlew :core:api:compileDebugKotlin
```
EXPECT: grün (neue Modelle/`MealsApi` generiert, ungenutzt)

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Image baut; beim Start „Applied 2 migrations“ (frische DB) bzw. Backup + „Applied 1 migrations“ (bestehende DB mit V1)

### Manual Validation
- [ ] Lokal mit Kopie der Pi-Datenbank (oder frischer DB + Setup): Start migriert V2, Backup unter `data/backups/` entsteht.
- [ ] `curl` (Eltern-Token): Profil für ein Kind auf `ALTERNATING` (A=Di–Do, B=Di–So, Referenz = Montag einer „Do-Abreise“-Woche) → `GET /api/v1/meals/weeks/<nächster Montag>/attendance` zeigt die erwarteten Tage und Frist; Eltern bestätigen.
- [ ] Ausnahme anlegen/löschen ändert die Übersicht; überlappende Ausnahme → 409.
- [ ] `PUT /api/v1/meals/settings` → `scheduled` erscheint, aktuelle Woche unverändert.
- [ ] Nach Merge: CI grün, Pi zieht `stable`, `/health` ok, Flyway-Migration V2 auf dem Pi angewendet (Log), Backup vorhanden.

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] `./gradlew check koverVerify` grün, Coverage ≥ 80 %
- [ ] REQ-MEAL-010–013 durch benannte Unit- **und** Integrationstests abgedeckt, Traceability gepflegt
- [ ] Jeder neue Endpunkt durch Contract-Test gegen `api/openapi.yaml` geprüft
- [ ] `meals` hängt nur von `core` ab (`backend/meals/build.gradle.kts`), `core` kennt `meals` nicht
- [ ] Keine ktlint-/detekt-Befunde; Android-`core:api` kompiliert
- [ ] Migration V2 läuft auf bestehender V1-Datenbank (mit Backup)

## Completion Checklist
- [ ] Code folgt den Mustern aus `core` (Domäne/Repository/Service/Routen/Mapper/Koin)
- [ ] Fehler nur über `AppException`; deutsche, nutzerfreundliche Meldungen
- [ ] Logging nur mit IDs
- [ ] `Clock` injiziert; alle Kalenderberechnungen in `Europe/Berlin`
- [ ] Keine Magic Numbers (16:00, 7, 366, 60 als benannte Konstanten)
- [ ] ADR-0010, `meals.md`, README, Traceability, PRD aktualisiert
- [ ] Keine Scope-Erweiterung über „NOT Building“ hinaus
- [ ] Self-contained — keine Rückfragen nötig

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Fristen falsch berechnet (Parität, DST, Jahreswechsel) | M | H | Reine Funktionen, Tabellentests inkl. 25.10./29.03. und Jahreswechsel; Eltern prüfen echte Wochen (Manual Validation) |
| Migrationsnummer kollidiert mit parallel geplanter Phase 3 | M | M | ADR-0010-Regel: 2a = V2, Phase 3 = V3; später gemergte umnummerieren, bevor sie deployt wird |
| `testFixtures`-Umzug bricht `core`-Tests/Lint | L | L | `git mv`, Package gleich; sofort `:core:test` laufen lassen |
| Generator erzeugt unpassende Typen (Arrays, nullable Refs) | M | M | Kein `oneOf`; `allOf`+`nullable`-Muster; generierten Code nicht ändern, Schema anpassen |
| Verschachtelte `dbQuery` → Deadlock (Pool 1) | L | H | Services rufen Repositories nacheinander; Prüf-und-Schreib atomar im Repository |
| Android-CI bricht durch neue generierte Typen | L | M | `:core:api:compileDebugKotlin` lokal prüfen |

## Notes
- **Entscheidungen des Nutzers (2026-10-17):** Phase 2 aufgeteilt in **2a Backend/API** (dieser Plan) und **2b Android-Eltern-UI**;
  Anwesenheitsmuster als **zwei Wochentypen** (A/B mit Tagesmengen + Referenzwoche) bzw. dauerhaft; **überlappende Ausnahmen → 409**.
- „Ab der nächsten Planwoche“ (REQ-MEAL-013) ist ausgelegt als: die gerade geplante Woche (Montag nach heute) behält ihre Frist;
  neue Werte gelten ab der Woche danach. In `meals.md` so festhalten.
- Kind ganz abwesend in der laufenden Woche → Frist = Bestellfrist (WhatsApp-Fallback, ADR-0007). In der Planwoche ganz abwesend →
  keine Frist, kann nicht kochen (Grundlage für REQ-MEAL-021 in Phase 4).
- `WeekAttendance` ist die Schnittstelle für Phase 3/4 (Kochtage) und Phase 6 (Erinnerungen); Phase 2b zeigt sie in der App an.
- Phase 3 (Wochenplan) läuft parallel und erweitert dasselbe Modul `backend/meals`: Datei-Kollisionen gering (eigene Pakete
  `plan/…`), aber `settings.gradle.kts`, `Application.kt`, `MealsModule.kt`, `openapi.yaml` werden von beiden berührt → wer zuerst
  merged, legt das Modul an; der andere rebased.
- Für die Umsetzung: `/ecc:prp-implement .claude/PRPs/plans/anwesenheit-fristen-backend.plan.md`; TDD-Reihenfolge in Tasks 4–8.
