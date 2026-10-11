# Plan: Fundament & AUTH – Backend, CI, Deployment (Phase 1a)

## Summary
Legt das lauffähige Backend-Grundgerüst an: Gradle-Multi-Modul-Build (`api-contract`, `core`, `app`), OpenAPI-Vertrag,
SQLite/Flyway/Exposed, minimales `AUTH` (Mitglieder mit Rollen, Erst-Einrichtung, Geräte-Kopplung per Kopplungscode,
Geräte-Token) sowie CI (Tests, Lint, Coverage, arm64-Image nach GHCR) und Pull-Deployment auf dem Pi mit Rollback.
Die Android-Seite (Gerüst, Kopplungs-UI, Zertifikat-Pinning) folgt als eigener Plan **Phase 1b**.

## User Story
Als Elternteil möchte ich die Familienmitglieder mit Rollen anlegen und jedes Handy per Kopplungscode einem Mitglied
zuordnen, damit alle folgenden Funktionen (Essensplaner) wissen, wer etwas tut und ob es eine Eltern-Aktion ist.

## Problem → Solution
Repository enthält nur Doku/ADRs, keinen Code, keine CI → Backend-Image läuft auf dem Pi (Port 8095, HTTPS),
Mitglieder und Geräte sind per API verwaltbar, jede grüne `main` wird automatisch deployt.

## Metadata
- **Complexity**: Large (Greenfield, ~45 Dateien, aber jede klein)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 1 – Fundament & AUTH (Teil **1a**: Backend/CI/Deploy; Teil 1b: Android folgt)
- **Estimated Files**: ~45 (inkl. Doku, Gradle-Wrapper, Deploy-Skripte)

---

## UX Design

### Before
N/A — kein System vorhanden.

### After
N/A — reine Backend-/Betriebsänderung. Bedienung erfolgt in Phase 1b über die App; bis dahin per `curl`
(siehe Manual Validation). API-Abläufe:

```
Erst-Einrichtung (einmalig)            Weiteres Gerät koppeln
───────────────────────────            ───────────────────────────────────────────
Elternhandy                            Elternhandy                 Kinderhandy
POST /api/v1/setup                     POST /api/v1/members  (Kind anlegen)
 {setupToken aus .env,                 POST /api/v1/members/{id}/pairing-codes
  parentName, deviceName}               → {code: "483920", expiresAt}
 → {token, member, device}                 ── Code mündlich/zeigen ──▶
                                                                    POST /api/v1/devices/pair
                                                                     {code, deviceName}
                                                                    → {token, member, device}
Alle weiteren Requests: Authorization: Bearer <token>
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Erst-Einrichtung | – | `POST /api/v1/setup` mit Setup-Token | Nur solange keine Mitglieder existieren |
| Mitglied anlegen | – | `POST /api/v1/members` (Eltern) | Rolle PARENT/CHILD |
| Gerät koppeln | – | Kopplungscode (6 Ziffern, 15 min, einmalig) | ADR-0008 |
| Gerät entfernen | – | `DELETE /api/v1/devices/{id}` (Eltern) | z. B. Handy verloren |
| Deployment | manuell | Merge auf `main` → Pi zieht `stable` binnen 5 min | ntfy meldet Erfolg/Rollback |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `CLAUDE.md` | all | Stack, Modulgrenzen, Ports, Pi-Einschränkungen |
| P0 | `docs/02-architecture/adr/0001…0007` | all | Alle Leitentscheidungen; besonders 0003 (SQLite-Pragmas), 0005 (CI/Deploy), 0006 (TLS) |
| P0 | `.claude/rules/ecc/kotlin/*.md` | all | Stil (kein `!!`, `val`, sealed types), Tests (Fakes statt Mocks), Security |
| P1 | `.claude/rules/ecc/common/patterns.md` | all | Antwort-Envelope `{success, data, error}` (ADR-0004 verweist darauf) |
| P1 | `.claude/rules/ecc/common/security.md` | all | Rate Limiting, keine Secrets im Code, Fehlermeldungen ohne Interna |
| P1 | `docs/01-requirements/meals.prd.md` | „Voraussetzung aus AUTH“, Phase 1 | REQ-AUTH-001/002 |
| P2 | `docs/04-verification/README.md`, `traceability.md` | all | Testnamen mit REQ-ID, Matrix pflegen |
| P2 | `.claude/settings.json` | all | Erlaubte Befehle (`./gradlew`, `docker build`), Deny für `deploy/certs/**`, `.env` |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Exposed 1.0 | https://blog.jetbrains.com/kotlin/2026/01/exposed-1-0-is-now-available/ , Migration Guide | Packages jetzt `org.jetbrains.exposed.v1.core.*` (Table, Column, `eq`, `and` als Top-Level-Imports) und `org.jetbrains.exposed.v1.jdbc.*` (Database, transaction, selectAll, insert). Braucht Kotlin ≥ 2.2. Ältere Tutorials (`org.jetbrains.exposed.sql`) **nicht** kopieren. |
| Ktor 3 | https://ktor.io/docs/releases.html | Mindestens **3.4.1** (CVE-2026-68762, WebSocket-DoS vor 3.4.1). Plugins: ContentNegotiation, StatusPages, Authentication (bearer), RateLimit, CallLogging, CallId. |
| Flyway + SQLite | https://documentation.red-gate.com/fd/sqlite-184127609.html | Treiber `org.xerial:sqlite-jdbc`, URL `jdbc:sqlite:<pfad>`. SQLite-Unterstützung ist Teil von `flyway-core` (Community); falls „Unsupported Database: SQLite“ → zusätzliches `org.flywaydb:flyway-database-*`-Modul der verwendeten Flyway-Version auf Maven Central suchen. |
| OpenAPI-Contract-Test | https://central.sonatype.com/artifact/com.atlassian.oai/swagger-request-validator-core | `OpenApiInteractionValidator.createForSpecificationUrl(uri).build()`; `validate(SimpleRequest, SimpleResponse)` liefert `ValidationReport`. Testen, dass ein absichtlich falscher Body auch wirklich einen Fehler meldet (bekanntes „no errors“-Problem bei falschem Content-Type). |
| OpenAPI Generator (Gradle) | https://openapi-generator.tech/docs/generators/kotlin | `generatorName = "kotlin"`, `globalProperties models=""`, `serializationLibrary=kotlinx_serialization`. |

---

## Patterns to Mirror

> **Greenfield:** Es gibt noch keinen Code. Die folgenden Snippets sind die **verbindlichen Referenzmuster**, die dieser
> Plan etabliert (SOURCE = Datei, die sie in diesem Plan anlegt). Spätere Module (meals) spiegeln genau diese Muster.

### NAMING_CONVENTION
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/Member.kt (neu)
```kotlin
package de.familyplanner.core.member

@JvmInline
value class MemberId(val value: String)   // UUID als String

enum class Role { PARENT, CHILD }

data class Member(
    val id: MemberId,
    val name: String,
    val role: Role,
    val createdAt: Instant,
)
```
- Basis-Package `de.familyplanner`; pro Modul `de.familyplanner.<modul>`; Unterpakete nach Fachlichkeit
  (`member`, `device`, `pairing`), nicht nach Typ.
- Interfaces ohne Präfix (`MemberRepository`), Implementierung mit Technik-Präfix (`ExposedMemberRepository`).
- Konstanten `SCREAMING_SNAKE_CASE` im `companion object` oder Top-Level `private const val`.
- Exposed-Tabellen: `object MembersTable : Table("members")`.
- Routen-Funktionen: `fun Route.memberRoutes()`.

### ERROR_HANDLING
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt (neu)
```kotlin
enum class ErrorCode(val httpStatus: Int) {
    VALIDATION_FAILED(400), UNAUTHORIZED(401), INVALID_PAIRING_CODE(401), FORBIDDEN(403),
    NOT_FOUND(404), CONFLICT(409), RATE_LIMITED(429), SETUP_DISABLED(503), INTERNAL(500),
}

sealed class AppException(val code: ErrorCode, override val message: String) : RuntimeException(message) {
    class Validation(message: String) : AppException(ErrorCode.VALIDATION_FAILED, message)
    class Unauthorized(message: String = "Nicht angemeldet") : AppException(ErrorCode.UNAUTHORIZED, message)
    class InvalidPairingCode : AppException(ErrorCode.INVALID_PAIRING_CODE, "Kopplungscode ungültig oder abgelaufen")
    class Forbidden(message: String = "Nur für Eltern") : AppException(ErrorCode.FORBIDDEN, message)
    class NotFound(message: String) : AppException(ErrorCode.NOT_FOUND, message)
    class Conflict(message: String) : AppException(ErrorCode.CONFLICT, message)
    class SetupDisabled : AppException(ErrorCode.SETUP_DISABLED, "Einrichtung nicht verfügbar")
}
```
- Services werfen `AppException`; **nur** `StatusPages` (app) übersetzt in HTTP + Envelope.
- Unerwartete `Throwable` → `log.error("Unhandled error on {} {}", method, path, cause)`, Antwort `INTERNAL`
  mit generischer Meldung „Interner Fehler“ (keine Stacktraces/SQL an den Client).
- Fehlertexte an Nutzer auf **Deutsch**, Log-Meldungen auf Englisch.

```kotlin
// SOURCE: backend/app/src/main/kotlin/de/familyplanner/app/plugins/StatusPages.kt (neu)
fun Application.configureStatusPages() {
    install(StatusPages) {
        exception<AppException> { call, e -> call.respondError(e.code, e.message) }
        exception<BadRequestException> { call, _ -> call.respondError(ErrorCode.VALIDATION_FAILED, "Ungültige Anfrage") }
        exception<Throwable> { call, e ->
            call.application.log.error("Unhandled error on {} {}", call.request.httpMethod.value, call.request.path(), e)
            call.respondError(ErrorCode.INTERNAL, "Interner Fehler")
        }
        status(HttpStatusCode.TooManyRequests) { call, _ -> call.respondError(ErrorCode.RATE_LIMITED, "Zu viele Anfragen") }
    }
}
```

### API_ENVELOPE
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/web/Responses.kt (neu)
```kotlin
// Generierte Envelope-Typen (aus openapi.yaml) haben alle die Form {success, data, error}.
suspend fun ApplicationCall.respondError(code: ErrorCode, message: String) =
    respond(HttpStatusCode.fromValue(code.httpStatus),
        ErrorResponse(success = false, data = null, error = ApiError(code = ApiError.Code.valueOf(code.name), message = message)))

// Erfolg: respond(HttpStatusCode.Created, MemberResponse(success = true, data = member.toApi(), error = null))
```

### LOGGING_PATTERN
// SOURCE: backend/app/src/main/resources/logback.xml (neu)
```xml
<pattern>%d{ISO8601} %-5level [%X{callId}] %logger{36} - %msg%n</pattern>
```
- SLF4J-Logger je Klasse: `private val log = LoggerFactory.getLogger(PairingService::class.java)`.
- INFO: Start, Migrationen, Backup, Setup ausgeführt, Gerät gekoppelt/entfernt (mit memberId/deviceId, **nie** Token/Code).
- WARN: fehlgeschlagene Kopplung (ohne Code), Rate-Limit ausgelöst. ERROR: unbehandelte Ausnahmen.
- `CallLogging` mit `callIdMdc("callId")`; **Authorization-Header niemals loggen**.

### REPOSITORY_PATTERN
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/member/ExposedMemberRepository.kt (neu)
```kotlin
interface MemberRepository {
    suspend fun findAll(): List<Member>
    suspend fun findById(id: MemberId): Member?
    suspend fun existsByName(name: String): Boolean
    suspend fun count(): Long
    suspend fun create(member: Member): Member
}

class ExposedMemberRepository(private val db: Database) : MemberRepository {
    override suspend fun findAll(): List<Member> = dbQuery(db) {
        MembersTable.selectAll().orderBy(MembersTable.createdAt).map { it.toMember() }
    }
    override suspend fun create(member: Member): Member = dbQuery(db) {
        MembersTable.insert {
            it[id] = member.id.value; it[name] = member.name
            it[role] = member.role.name; it[createdAt] = member.createdAt.toEpochMilli()
        }
        member
    }
    // …
    private fun ResultRow.toMember() = Member(
        id = MemberId(this[MembersTable.id]), name = this[MembersTable.name],
        role = Role.valueOf(this[MembersTable.role]), createdAt = Instant.ofEpochMilli(this[MembersTable.createdAt]),
    )
}

// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/persistence/Transactions.kt (neu)
suspend fun <T> dbQuery(db: Database, block: JdbcTransaction.() -> T): T =
    withContext(Dispatchers.IO) { transaction(db) { block() } }
```
- Zeitstempel als **INTEGER Epoch-Millis** (`long(...)`), Mapping auf `java.time.Instant` im Repository — vermeidet
  Datums-Dialektprobleme von SQLite.
- IDs als `TEXT` (UUID-String, `varchar("id", 36)`).

### SERVICE_PATTERN
// SOURCE: backend/core/src/main/kotlin/de/familyplanner/core/pairing/PairingService.kt (neu)
```kotlin
class PairingService(
    private val members: MemberRepository,
    private val devices: DeviceRepository,
    private val codes: PairingCodeRepository,
    private val secrets: SecretGenerator,
    private val clock: Clock,
) {
    suspend fun createCode(memberId: MemberId): IssuedPairingCode {
        members.findById(memberId) ?: throw AppException.NotFound("Mitglied nicht gefunden")
        val code = secrets.numericCode(PAIRING_CODE_LENGTH)
        val expiresAt = clock.instant().plus(PAIRING_CODE_TTL)
        codes.replaceForMember(memberId, hash(code), expiresAt)   // alte ungenutzte Codes verfallen
        return IssuedPairingCode(code, expiresAt)
    }
    // pair(code, deviceName): Hash nachschlagen → abgelaufen/benutzt → InvalidPairingCode
    //   → markUsed + Device mit neuem Token anlegen → PairingResult(token, member, device)

    companion object {
        const val PAIRING_CODE_LENGTH = 6
        val PAIRING_CODE_TTL: Duration = Duration.ofMinutes(15)
    }
}
```
- Konstruktor-Injection, `Clock` injiziert (Tests mit `Clock.fixed`), Zufall über `SecretGenerator`-Interface
  (Tests mit Fake).
- Domänenobjekte unveränderlich (`data class`, `copy`).

### TEST_STRUCTURE
// SOURCE: backend/core/src/test/kotlin/de/familyplanner/core/pairing/PairingServiceTest.kt (neu)
```kotlin
class PairingServiceTest {
    private val clock = MutableClock(Instant.parse("2026-10-12T10:00:00Z"))
    private val members = InMemoryMemberRepository()
    private val service = PairingService(members, InMemoryDeviceRepository(), InMemoryPairingCodeRepository(),
        FakeSecretGenerator(code = "123456", token = "tok"), clock)

    @Test
    fun `REQ-AUTH-002 expired pairing code is rejected`() = runTest {
        // Arrange
        val child = members.create(aMember(role = Role.CHILD))
        service.createCode(child.id)
        clock.advance(Duration.ofMinutes(16))
        // Act + Assert
        assertFailsWith<AppException.InvalidPairingCode> { service.pair("123456", "Handy Lea") }
    }
}

// SOURCE: backend/app/src/test/kotlin/de/familyplanner/app/AuthApiTest.kt (neu) — Integration
class AuthApiTest {
    @TempDir lateinit var dir: Path

    @Test
    fun `REQ-AUTH-002 child device cannot create members`() = testApplication {
        application { module(testConfig(dir)) }
        val child = setupFamilyWithChild()                // Helper: setup + member + pair
        val response = client.post("/api/v1/members") {
            bearerAuth(child.token); contentType(ContentType.Application.Json)
            setBody("""{"name":"Gast","role":"CHILD"}""")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        contract.assertValid("POST", "/api/v1/members", response)   // OpenAPI-Contract-Check
    }
}
```
- JUnit 5 + `kotlin.test`-Assertions + `kotlinx-coroutines-test` (`runTest`). **Fakes statt Mocks** (In-Memory-Repos
  in `src/test/.../fakes/`); MockK nur, wenn ein Fake unverhältnismäßig wäre.
- Testname = Backtick-Satz, beginnt mit REQ-ID, wenn er eine Anforderung abdeckt.
- AAA-Kommentare (`// Arrange`, `// Act`, `// Assert`).
- Integrationstests: echte SQLite-Datei in `@TempDir` (kein In-Memory, weil Pool + WAL).

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `docs/01-requirements/auth.prd.md` | CREATE | REQ-AUTH-001…003 + REQ-OPS-001 mit Abnahmekriterien (Basis der Testnamen) |
| `docs/02-architecture/adr/0008-device-pairing.md` | CREATE | Entscheidung Kopplungscode + Geräte-Token; TLS in Ktor |
| `docs/02-architecture/README.md` | UPDATE | ADR-0008 in Tabelle; `api-contract` als Baustein |
| `docs/03-design/core.md` | CREATE | Moduldesign core/AUTH (Domäne, Tabellen, Endpunkte, Unit-Testfälle) |
| `docs/04-verification/traceability.md` | UPDATE | REQ-AUTH-001…003, REQ-OPS-001 mit Testnamen |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 1 → in-progress, Plan-Link (bereits durch diesen Plan erledigt) |
| `api/openapi.yaml` | CREATE | Vertrag: health, setup, pair, me, members, pairing-codes, devices |
| `.gitignore` | UPDATE | `deploy/certs/`, `*.p12`, `*.key` |
| `backend/settings.gradle.kts` | CREATE | Module `api-contract`, `core`, `app` |
| `backend/build.gradle.kts` | CREATE | Plugins `apply false` |
| `backend/gradle.properties` | CREATE | `kotlin.code.style=official`, JVM-Args für Gradle |
| `backend/gradle/libs.versions.toml` | CREATE | Versionskatalog |
| `backend/gradlew`, `gradlew.bat`, `gradle/wrapper/*` | CREATE | Gradle-Wrapper (committen) |
| `backend/buildSrc/build.gradle.kts` | CREATE | Convention-Plugin-Build |
| `backend/buildSrc/src/main/kotlin/familyplanner.kotlin-jvm.gradle.kts` | CREATE | Toolchain 21, JUnit 5, ktlint, detekt |
| `backend/config/detekt/detekt.yml` | CREATE | `buildUponDefaultConfig`, minimale Abweichungen |
| `backend/api-contract/build.gradle.kts` | CREATE | Modelle aus `api/openapi.yaml` generieren |
| `backend/core/build.gradle.kts` | CREATE | Abhängigkeiten core |
| `backend/core/src/main/resources/db/migration/V1__auth.sql` | CREATE | Tabellen members, devices, pairing_codes |
| `backend/core/src/main/kotlin/de/familyplanner/core/error/AppException.kt` | CREATE | Fehlermodell |
| `.../core/member/{Member,MemberRepository,ExposedMemberRepository,MemberService}.kt` | CREATE | REQ-AUTH-001 |
| `.../core/device/{Device,DeviceRepository,ExposedDeviceRepository,DeviceService}.kt` | CREATE | REQ-AUTH-002/003 |
| `.../core/pairing/{PairingCode,PairingCodeRepository,ExposedPairingCodeRepository,PairingService,SetupService}.kt` | CREATE | REQ-AUTH-002 |
| `.../core/security/{SecretGenerator,TokenHasher}.kt` | CREATE | Zufall + SHA-256 |
| `.../core/persistence/{Tables,Transactions,DatabaseFactory,DatabaseBackup}.kt` | CREATE | DB-Setup, Pragmas, Flyway, Backup |
| `.../core/auth/{DevicePrincipal,RoleGuard,DeviceAuthentication}.kt` | CREATE | Bearer-Auth + Eltern-Guard (auch für spätere Module) |
| `.../core/web/{Responses,ApiMappers,AuthRoutes,MemberRoutes,DeviceRoutes}.kt` | CREATE | Routen + Mapping auf generierte Modelle |
| `.../core/CoreModule.kt` | CREATE | Koin-Modul |
| `backend/app/build.gradle.kts` | CREATE | Ktor-App, Fat-JAR, Kover-Aggregation |
| `.../app/{Application,AppConfig}.kt` | CREATE | Start, Konfiguration aus Env, TLS-Connector |
| `.../app/plugins/{Serialization,StatusPages,Security,RateLimiting,Monitoring}.kt` | CREATE | Ktor-Plugins |
| `.../app/HealthRoutes.kt` | CREATE | `/health` |
| `backend/app/src/main/resources/logback.xml` | CREATE | Log-Format |
| `backend/core/src/test/...` | CREATE | Unit-Tests + Fakes |
| `backend/app/src/test/...` | CREATE | Integrations- + Contract-Tests |
| `backend/Dockerfile` | CREATE | Laufzeit-Image (Temurin JRE 21) |
| `backend/.dockerignore` | CREATE | Nur Fat-JAR in den Kontext |
| `.github/workflows/backend.yml` | CREATE | CI: check + koverVerify, arm64-Image nach GHCR |
| `deploy/docker-compose.yml` | CREATE | Dienst `family-planner` |
| `deploy/.env.example` | CREATE | Variablen ohne Werte |
| `deploy/update.sh` | CREATE | Pull, Health-Wait, Rollback, ntfy |
| `deploy/family-planner-update.{service,timer}` | CREATE | systemd-Timer (5 min) |
| `deploy/gen-cert.sh` | CREATE | Selbstsigniertes Zertifikat + PKCS12 |
| `deploy/README.md` | UPDATE | Einrichtung auf dem Pi Schritt für Schritt |
| `backend/README.md` | UPDATE | Build-/Test-Befehle, Modulübersicht |

## NOT Building

- **Android-App** (Gerüst, Kopplungs-UI, Zertifikat-Pinning, generierter Client) → Plan **Phase 1b**.
- **Merkmal „kocht“ und Anwesenheitsmuster** aus REQ-AUTH-001 → gehören fachlich ins Modul `meals` (ADR-0001:
  core kennt keine Feature-Begriffe) und kommen mit **Phase 2**. In `auth.prd.md` entsprechend vermerken.
- Mitglieder umbenennen/löschen, Rolle ändern → erst bei Bedarf (YAGNI); bei Tippfehler DB-Reset im Pilotvorlauf.
- Event-Bus in `core` (erst nötig mit `gamification`).
- Feature-Module `meals`, `tasks`, `gamification`, `board` (leere Module werden nicht angelegt).
- Zeitgesteuertes tägliches Backup → späterer OPS-Schritt; jetzt nur **Backup vor Migration beim Start**.
- Mandantenfähigkeit, Passwörter, Token-Ablauf/Refresh (Token gilt bis zum Entfernen des Geräts).
- Uptime-Kuma-Monitor anlegen (manueller Schritt in der README).

---

## Step-by-Step Tasks

### Task 0: Voraussetzungen lokal
- **ACTION**: JDK 21 und (einmalig) Gradle installieren, damit der Wrapper erzeugt werden kann.
- **IMPLEMENT**: Den Nutzer bitten bzw. ausführen: `brew install --cask temurin@21` und `brew install gradle`.
  Danach `java -version` → 21.
- **GOTCHA**: Auf dem Mac ist aktuell **kein JDK** installiert. Docker ist vorhanden (arm64 → Image lokal nativ testbar).
- **VALIDATE**: `java -version` und `gradle --version` laufen.

### Task 1: Anforderungen & ADR (V-Modell links)
- **ACTION**: `docs/01-requirements/auth.prd.md`, `docs/02-architecture/adr/0008-device-pairing.md`, `docs/03-design/core.md` anlegen.
- **IMPLEMENT** `auth.prd.md` (kurz; Format wie `meals.prd.md`, Abschnitt „Anforderungen“):
  - **REQ-AUTH-001 Mitglieder & Rollen** – Given Elternteil, When Mitglied mit Name + Rolle angelegt, Then erscheint es
    in der Liste · Given Kind, When es ein Mitglied anlegen will, Then 403 · Given Name existiert, Then 409 ·
    Given Name leer/> 40 Zeichen, Then 400. Hinweis: „kocht“ + Anwesenheitsmuster → `meals` (Phase 2).
  - **REQ-AUTH-002 Geräte-Anmeldung** – Given keine Mitglieder + gültiges Setup-Token, When Setup, Then erstes
    Elternteil + Gerät + Token · Given Mitglieder existieren, When Setup, Then 409 · Given falsches Setup-Token, Then
    401 · Given Kopplungscode (6 Ziffern, 15 min, einmalig), When Gerät koppelt, Then erhält es ein Token für dieses
    Mitglied · Given Code abgelaufen/benutzt/unbekannt, Then 401 · Given neuer Code für Mitglied, Then alter Code
    ungültig · Given Request ohne/mit ungültigem Token, Then 401 · Given Kind-Gerät, When Eltern-Endpunkt, Then 403.
  - **REQ-AUTH-003 Gerät entfernen** – Given Elternteil, When es ein Gerät entfernt, Then wird dessen Token mit 401
    abgewiesen.
  - **REQ-OPS-001 Betrieb** – `/health` meldet DB-Erreichbarkeit; neues `stable`-Image wird binnen 5 min deployt; bei
    fehlschlagendem Healthcheck Rollback + ntfy; Backup vor jeder Migration.
- **IMPLEMENT** ADR-0008 (Format `0000-template.md`): Entscheidung Kopplungscode (6 Ziffern, SecureRandom, 15 min TTL,
  einmalig, nur Hash gespeichert, Rate-Limit 5/min/IP) → Geräte-Token (32 Byte SecureRandom, Base64URL, nur SHA-256
  in DB, Bearer-Header, gültig bis Entfernen); Erst-Einrichtung per `FP_SETUP_TOKEN` (nur solange keine Mitglieder);
  TLS terminiert **in Ktor** (PKCS12-Keystore aus `certs/`), interner HTTP-Port nur für Docker-Healthcheck.
  Alternativen: PIN pro Mitglied, Name+Passwort, Reverse-Proxy (Caddy) für TLS — jeweils mit Begründung verworfen.
- **IMPLEMENT** `core.md` gemäß `docs/03-design/README.md` (REQ-IDs, Domänenmodell, Endpunkte → Verweis openapi,
  Tabellen, Unit-Testfälle aus „Testing Strategy“ unten).
- **VALIDATE**: Dokumente verlinkt (ADR-Tabelle, `01-requirements/README.md` MVP-Schnitt verweist auf `auth.prd.md`).

### Task 2: OpenAPI-Vertrag (ADR-0004, Skill `contract-first`)
- **ACTION**: `api/openapi.yaml` (OpenAPI 3.0.3 — 3.1 vermeiden, Generator/Validator-Unterstützung) anlegen.
- **IMPLEMENT**:
  - `servers: [{url: https://planner.home.arpa:8095}]`; `securitySchemes.deviceToken: {type: http, scheme: bearer}`;
    global `security: [deviceToken: []]`, Ausnahmen `security: []` für `/health`, `/api/v1/setup`, `/api/v1/devices/pair`.
  - Endpunkte:
    | Methode | Pfad | Auth | Body | Antwort |
    |---|---|---|---|---|
    | GET | `/health` | – | – | 200 `HealthStatus {status: "ok", version}` / 503 |
    | POST | `/api/v1/setup` | – | `SetupRequest {setupToken, parentName, deviceName}` | 201 `PairingResultResponse` / 401 / 409 / 503 |
    | POST | `/api/v1/devices/pair` | – | `PairDeviceRequest {code, deviceName}` | 201 `PairingResultResponse` / 400 / 401 / 429 |
    | GET | `/api/v1/me` | alle | – | 200 `MeResponse` (`{member, deviceId}`) |
    | GET | `/api/v1/members` | alle | – | 200 `MemberListResponse` |
    | POST | `/api/v1/members` | Eltern | `CreateMemberRequest {name, role}` | 201 `MemberResponse` / 400 / 403 / 409 |
    | POST | `/api/v1/members/{memberId}/pairing-codes` | Eltern | – | 201 `PairingCodeResponse` (`{code, expiresAt}`) / 404 |
    | GET | `/api/v1/devices` | Eltern | – | 200 `DeviceListResponse` |
    | DELETE | `/api/v1/devices/{deviceId}` | Eltern | – | 204 / 404 |
  - Schemas: `Role` (enum PARENT, CHILD), `Member {id, name, role, createdAt}`, `Device {id, memberId, name, createdAt,
    lastSeenAt?}`, `PairingCode {code, expiresAt}`, `PairingResult {token, member, device}`, `Me {member, deviceId}`,
    `ApiError {code (enum = ErrorCode-Namen), message}`.
  - Envelopes **explizit je Typ** (kein Generic, kein `allOf`): `XxxResponse {success: boolean (required), data: Xxx
    (nullable), error: ApiError (nullable)}`; `ErrorResponse` mit `data` weggelassen/nullable. Jede Operation listet
    ihre Fehlerstatus mit `ErrorResponse`.
  - Validierungsgrenzen im Schema: `name` minLength 1, maxLength 40; `deviceName` 1–60; `code` pattern `^[0-9]{6}$`;
    `setupToken` minLength 16.
  - Zeitfelder `type: string, format: date-time`.
- **GOTCHA**: `operationId` überall setzen (für 1b-Client). `required`-Listen pflegen, sonst werden alle Felder nullable.
- **VALIDATE**: `docker run --rm -v "$PWD/api:/api" redocly/cli lint /api/openapi.yaml` (oder Task 3 Generierung läuft fehlerfrei).

### Task 3: Gradle-Gerüst
- **ACTION**: Build-Struktur unter `backend/` anlegen; Wrapper erzeugen (`cd backend && gradle wrapper`).
- **IMPLEMENT**:
  - `settings.gradle.kts`: `rootProject.name = "family-planner-backend"`; `include(":api-contract", ":core", ":app")`;
    `dependencyResolutionManagement { repositories { mavenCentral() } }`.
  - `libs.versions.toml` — **aktuelle stabile Versionen auf Maven Central prüfen**, Mindeststände:
    Kotlin ≥ 2.2 · Ktor ≥ 3.4.1 · Exposed 1.0.x · Flyway 11.x (oder neuer, s. Gotcha) · sqlite-jdbc 3.50.x · HikariCP 6.x ·
    Koin 4.1.x (`koin-ktor`, `koin-logger-slf4j`) · kotlinx-serialization-json · Logback 1.5.x ·
    OpenAPI-Generator-Plugin 7.x · Kover 0.9.x · ktlint-gradle 13.x · detekt 1.23.8 · JUnit 5.13 ·
    kotlinx-coroutines-test · MockK 1.14 · swagger-request-validator-core 2.46.0.
  - `buildSrc`: Convention-Plugin `familyplanner.kotlin-jvm`: `kotlin("jvm")`, `jvmToolchain(21)`,
    `org.jlleitschuh.gradle.ktlint`, `io.gitlab.arturbosch.detekt` (`buildUponDefaultConfig = true`,
    `config.setFrom(rootDir/config/detekt/detekt.yml)`), `tasks.test { useJUnitPlatform() }`, Test-Deps (junit-jupiter,
    kotlin-test-junit5, coroutines-test).
  - `api-contract`: Plugin `org.openapi.generator`; `openApiGenerate { generatorName = "kotlin"; inputSpec =
    "$rootDir/../api/openapi.yaml"; outputDir = layout.buildDirectory.dir("generated/openapi"); packageName =
    "de.familyplanner.api"; modelPackage = "de.familyplanner.api.model"; globalProperties = mapOf("models" to "",
    "modelDocs" to "false", "modelTests" to "false"); configOptions = mapOf("serializationLibrary" to
    "kotlinx_serialization", "dateLibrary" to "string", "enumPropertyNaming" to "UPPERCASE") }`;
    `sourceSets.main.kotlin.srcDir(".../src/main/kotlin")`; `compileKotlin` + `runKtlintCheck*` **dependsOn
    openApiGenerate**; ktlint/detekt für generierten Code **ausschließen**.
  - `core` → `api(project(":api-contract"))`, ktor-server-core/auth/content-negotiation, exposed-core/jdbc,
    sqlite-jdbc, hikari, flyway-core, koin-ktor, logback (nur runtime in app).
  - `app` → `implementation(project(":core"))`, ktor-server-netty, status-pages, rate-limit, call-logging, call-id,
    koin; Plugin `io.ktor.plugin` (Fat-JAR `buildFatJar`, `mainClass = "de.familyplanner.app.ApplicationKt"`);
    Kover-Aggregation: `dependencies { kover(project(":core")) }`, `kover { reports { filters { excludes {
    packages("de.familyplanner.api.model") } }; verify { rule { minBound(80) } } } }`; Test-Systemproperty
    `systemProperty("openapi.spec", rootDir.resolve("../api/openapi.yaml").absolutePath)`.
- **GOTCHA**:
  - `dateLibrary=string`: Zeitfelder werden `String`; Mapper formatieren `Instant.toString()` (ISO-8601 UTC). Bewusst
    gewählt, weil `java8` mit kotlinx_serialization Infrastruktur-Serializer erwartet, die bei `models`-only nicht
    generiert werden.
  - Erzeugte Modelle prüfen: Wenn sie `@Contextual`/fehlende Imports enthalten → `additionalProperties`/`typeMappings`
    anpassen, nicht den generierten Code editieren.
  - detekt 1.23.x mit Kotlin 2.2: Nur Task `detekt` (ohne Typauflösung) nutzen; falls Inkompatibilität → detekt 2.x
    (falls stabil) oder `detekt { ignoreFailures = false }` mit eigener Kotlin-Version per `detektPlugins`-Resolution.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:compileKotlin` erzeugt `Member`, `MemberResponse`, … unter `build/generated`.

### Task 4: Persistenz (core) — TDD
- **ACTION**: Migration, Tabellen, `DatabaseFactory`, `DatabaseBackup`, `dbQuery`.
- **IMPLEMENT**:
  - `V1__auth.sql`:
    ```sql
    CREATE TABLE members (
      id TEXT PRIMARY KEY, name TEXT NOT NULL UNIQUE COLLATE NOCASE,
      role TEXT NOT NULL CHECK (role IN ('PARENT','CHILD')), created_at INTEGER NOT NULL);
    CREATE TABLE devices (
      id TEXT PRIMARY KEY, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
      name TEXT NOT NULL, token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL,
      last_seen_at INTEGER, revoked_at INTEGER);
    CREATE TABLE pairing_codes (
      code_hash TEXT PRIMARY KEY, member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
      expires_at INTEGER NOT NULL, used_at INTEGER);
    CREATE INDEX idx_devices_member ON devices(member_id);
    CREATE INDEX idx_pairing_codes_member ON pairing_codes(member_id);
    ```
  - `Tables.kt`: Exposed-`Table`-Objekte exakt passend (`varchar("id",36)`, `long("created_at")`, `long(...).nullable()`),
    `override val primaryKey`.
  - `DatabaseFactory.create(dbPath: Path): Database`: Verzeichnis anlegen; HikariConfig `jdbcUrl = "jdbc:sqlite:$dbPath"`,
    `maximumPoolSize = 1` (ADR-0003), `addDataSourceProperty("foreign_keys","true")`, `("journal_mode","WAL")`,
    `("busy_timeout","5000")`; danach `DatabaseBackup.beforeMigration(...)`, dann `Flyway.configure().dataSource(ds)
    .locations("classpath:db/migration").load().migrate()`; `Database.connect(ds, databaseConfig = DatabaseConfig {
    defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE })`.
  - `DatabaseBackup`: nur wenn DB-Datei existiert **und** Flyway `info().pending()` nicht leer: `VACUUM INTO
    '<data>/backups/family-planner-<yyyyMMdd-HHmmss>.db'` über eine eigene JDBC-Verbindung; nur die neuesten
    `MAX_BACKUPS = 10` behalten; Pfad ohne Nutzereingabe (keine Injection), Dateiname aus Zeitstempel.
- **MIRROR**: REPOSITORY_PATTERN (`dbQuery`), LOGGING_PATTERN (INFO „Database backup written to …“, „Applied n migrations“).
- **IMPORTS**: `org.jetbrains.exposed.v1.core.Table`, `org.jetbrains.exposed.v1.jdbc.Database`,
  `org.jetbrains.exposed.v1.jdbc.transactions.transaction`, `org.jetbrains.exposed.v1.core.DatabaseConfig`
  (genauen Ort im 1.0-Migration-Guide prüfen), `com.zaxxer.hikari.*`, `org.flywaydb.core.Flyway`.
- **GOTCHA**: SQLite + Exposed brauchen `TRANSACTION_SERIALIZABLE`, sonst Fehler „unsupported isolation level“.
  `foreign_keys` gilt pro Verbindung → als DataSource-Property, nicht einmalig per SQL. `VACUUM INTO` nicht innerhalb
  einer Transaktion ausführen. Flyway-Tabelle `flyway_schema_history` nicht in Exposed modellieren.
- **VALIDATE**: `DatabaseFactoryTest`: frische Temp-DB → Tabellen existieren, `PRAGMA foreign_keys` = 1,
  `PRAGMA journal_mode` = wal; zweite Migration-Version (Test-Ressource) → Backup-Datei entsteht; > 10 Backups → älteste gelöscht.

### Task 5: Domäne & Services (core) — TDD
- **ACTION**: Tests zuerst (RED), dann Implementierung für `member`, `device`, `pairing`, `security`.
- **IMPLEMENT**:
  - `SecretGenerator` (Interface) + `SecureRandomSecretGenerator`: `numericCode(len)` (gleichverteilt, `SecureRandom.nextInt(10)`
    je Ziffer), `token()` = 32 Byte → `Base64.getUrlEncoder().withoutPadding()`.
  - `TokenHasher.sha256Hex(value)`; Vergleich des Setup-Tokens mit `MessageDigest.isEqual` (konstante Zeit).
  - `MemberService.create(name, role)`: `name.trim()`, 1–40 Zeichen sonst `Validation`; `existsByName` (case-insensitiv)
    → `Conflict("Name bereits vergeben")`; UUID via `UUID.randomUUID()`, `createdAt = clock.instant()`. `list()`.
  - `SetupService.setup(setupToken, parentName, deviceName)`: konfiguriertes Token `null`/leer → `SetupDisabled`;
    falsches Token → `Unauthorized("Setup-Token ungültig")`; `members.count() > 0` → `Conflict("Einrichtung bereits
    erfolgt")`; sonst Elternteil + Gerät anlegen → `PairingResult`. Prüfung + Anlage in **einer** Transaktion
    (Race: zwei gleichzeitige Setups) — dafür Repository-Methode `createFirstParentWithDevice(...)` oder Service nutzt
    `dbQuery` mit allen Schritten.
  - `PairingService.createCode(memberId)` (siehe SERVICE_PATTERN) und `pair(code, deviceName)`: Format prüfen
    (`^[0-9]{6}$`, sonst `Validation`), Hash suchen; nicht gefunden / `usedAt != null` / `expiresAt <= now` →
    `InvalidPairingCode` (+ WARN-Log ohne Code); sonst `markUsed` und Gerät mit `tokenHash` anlegen — atomar in einer
    Transaktion, damit ein Code nicht doppelt genutzt werden kann.
  - `DeviceService.authenticate(token): DevicePrincipal?`: Hash → Gerät mit `revokedAt == null` → Mitglied;
    `lastSeenAt` nur aktualisieren, wenn älter als `LAST_SEEN_UPDATE_INTERVAL = 5 min`. `list()`, `revoke(deviceId)`
    (setzt `revokedAt`; unbekannt → `NotFound`).
  - `Device`-Domäne enthält **keinen** Token/Hash nach außen (Hash nur im Repository).
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING, TEST_STRUCTURE (In-Memory-Fakes in `core/src/test/.../fakes/`).
- **GOTCHA**: Token/Code nie loggen, nie in Exceptions-Messages. Nie `!!`. `Clock` überall injizieren
  (kein `Instant.now()` im Produktivcode).
- **VALIDATE**: `./gradlew :core:test` grün; Unit-Tests aus Tabelle „Testing Strategy“.

### Task 6: Auth-Infrastruktur & Routen (core)
- **ACTION**: Bearer-Authentifizierung, Eltern-Guard, Routen, Mapper, Koin-Modul.
- **IMPLEMENT**:
  - `DevicePrincipal(deviceId: String, memberId: MemberId, role: Role)`.
  - `fun AuthenticationConfig.deviceAuth(service: DeviceService)` → `bearer(AUTH_DEVICE) { authenticate { cred ->
    service.authenticate(cred.token) } }`; `const val AUTH_DEVICE = "device"`.
  - `RoleGuard.kt`: `fun ApplicationCall.principalOrThrow(): DevicePrincipal` und `fun ApplicationCall.requireParent():
    DevicePrincipal` (wirft `Forbidden`). Damit sind Rollenprüfungen in **jeder** Route explizit; `meals` nutzt später dasselbe.
  - `AuthRoutes.kt`: `Route.authRoutes(setup, pairing)` — `post("/api/v1/setup")`, `post("/api/v1/devices/pair")`
    mit `rateLimit(RateLimitName(PAIRING_RATE_LIMIT))`.
  - `MemberRoutes.kt`: `authenticate(AUTH_DEVICE) { get("/api/v1/me"); get/post("/api/v1/members");
    post("/api/v1/members/{memberId}/pairing-codes") }`.
  - `DeviceRoutes.kt`: `get("/api/v1/devices")`, `delete("/api/v1/devices/{deviceId}")` → `respond(HttpStatusCode.NoContent)`.
  - `ApiMappers.kt`: `Member.toApi()`, `Device.toApi()`, … (Instant → `toString()`); Request-DTOs → Validierung im Service.
  - `CoreModule.kt`: `fun coreModule(dbPath: Path, setupToken: String?, clock: Clock) = module { single { DatabaseFactory.create(dbPath) }; single<MemberRepository> { ExposedMemberRepository(get()) }; … }`.
- **MIRROR**: API_ENVELOPE, ERROR_HANDLING.
- **GOTCHA**: Pfad-Parameter `memberId`/`deviceId` als UUID validieren (`runCatching { UUID.fromString(it) }` →
  `Validation`), bevor DB-Zugriff. 401-Antworten von Ktor-Auth (`challenge`) ebenfalls im Envelope:
  `bearer { … }` + `challenge { call.respondError(ErrorCode.UNAUTHORIZED, "Nicht angemeldet") }` — sonst leerer Body
  und der Contract-Test schlägt fehl.
- **VALIDATE**: kompiliert; abgedeckt durch Integrationstests in Task 8.

### Task 7: App-Modul (Ktor-Server, Konfiguration, TLS)
- **ACTION**: `Application.kt`, `AppConfig.kt`, Plugins, `HealthRoutes.kt`, `logback.xml`.
- **IMPLEMENT**:
  - `AppConfig` aus Umgebungsvariablen (fail-fast mit klarer Meldung bei ungültigen Werten):
    | Variable | Default | Bedeutung |
    |---|---|---|
    | `FP_PORT` | 8095 | öffentlicher Port (HTTPS, wenn TLS an) |
    | `FP_HEALTH_PORT` | 8096 | interner HTTP-Port **nur 127.0.0.1** für Docker-Healthcheck |
    | `FP_DB_PATH` | `./data/family-planner.db` | SQLite-Datei |
    | `FP_SETUP_TOKEN` | – | optional; ≥ 16 Zeichen, sonst Start-Abbruch |
    | `FP_TLS_ENABLED` | `false` | im Compose `true` |
    | `FP_TLS_KEYSTORE` | `/app/certs/planner.p12` | PKCS12 |
    | `FP_TLS_KEYSTORE_PASSWORD` | – | Pflicht, wenn TLS an |
    | `FP_VERSION` | `dev` | Git-SHA, vom Image gesetzt |
    | `TZ` | – | `Europe/Berlin` im Container |
  - `main()`: `embeddedServer(Netty, configure = { if (tls) sslConnector(keyStore, alias, pw, pw) { port = FP_PORT }
    else connector { port = FP_PORT }; connector { host = "127.0.0.1"; port = FP_HEALTH_PORT } }, module = { module(config) })`.
  - `fun Application.module(config: AppConfig, clock: Clock = Clock.systemUTC())`: Koin (`coreModule`), Serialization
    (`Json { ignoreUnknownKeys = true; explicitNulls = false }`), StatusPages, Monitoring (CallId mit
    `generate { UUID.randomUUID().toString() }`, CallLogging), RateLimit (global 120/min pro `call.request.origin.remoteHost`;
    `PAIRING_RATE_LIMIT` 5/min), Authentication (`deviceAuth`), Routing (`healthRoutes`, `authRoutes`, `memberRoutes`,
    `deviceRoutes`). `module` muss **ohne** Netty-Connectoren testbar sein (Tests nutzen `testApplication`).
  - `/health`: `SELECT 1` über `dbQuery`; ok → 200 `{status:"ok", version}`; Fehler → 503 `{status:"error"}` + ERROR-Log.
- **GOTCHA**: Ktor-RateLimit-Konfiguration in Tests überschreibbar machen (`AppConfig.rateLimits`), sonst flaky.
  `-Xmx` gehört ins Dockerfile, nicht in den Code. Keystore-Datei liegt außerhalb des Repos (`deploy/certs/` ist
  per settings.json deny + .gitignore geschützt — **nicht lesen**).
- **VALIDATE**: `./gradlew :app:run` lokal mit `FP_SETUP_TOKEN=…` → `curl http://localhost:8095/health` → 200.

### Task 8: Integrations- & Contract-Tests (app)
- **ACTION**: `ContractValidator` + Tests je Endpunkt mit echter Temp-SQLite.
- **IMPLEMENT**:
  - `ContractValidator`: `OpenApiInteractionValidator.createForSpecificationUrl(File(System.getProperty("openapi.spec")).toURI().toString()).build()`;
    `assertValid(method, path, response: HttpResponse, requestBody: String? = null)` baut `SimpleRequest`/`SimpleResponse`
    (Status, `Content-Type`, Body) und schlägt mit allen Report-Meldungen fehl.
  - Test-Helper: `testConfig(dir)` (DB in `@TempDir`, Setup-Token `"test-setup-token-123456"`, hohe Rate-Limits),
    `ApplicationTestBuilder.setupFamilyWithChild()` → `(parentToken, childId, childToken)`.
  - Jeder Integrationstest ruft zusätzlich `contract.assertValid(...)` auf.
  - Ein Negativtest für den Validator selbst (manipulierter Body → Report hat Fehler), damit ein stiller Validator auffällt.
- **VALIDATE**: `./gradlew :app:test` grün.

### Task 9: Qualität & Coverage
- **ACTION**: `./gradlew check koverVerify` grün bekommen.
- **IMPLEMENT**: ktlint-/detekt-Befunde beheben (nicht unterdrücken); Coverage ≥ 80 % (ohne generierte Modelle).
- **VALIDATE**: `cd backend && ./gradlew check koverVerify koverHtmlReport`.

### Task 10: Container-Image
- **ACTION**: `backend/Dockerfile`, `backend/.dockerignore`.
- **IMPLEMENT**:
  ```dockerfile
  FROM eclipse-temurin:21-jre
  RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* \
      && useradd --system --uid 10001 planner && mkdir -p /app/data /app/certs && chown -R planner /app
  WORKDIR /app
  COPY app/build/libs/app-all.jar /app/app.jar
  ARG VERSION=dev
  ENV FP_VERSION=$VERSION TZ=Europe/Berlin FP_DB_PATH=/app/data/family-planner.db
  USER planner
  EXPOSE 8095
  HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 CMD curl -fsS http://127.0.0.1:8096/health || exit 1
  ENTRYPOINT ["java","-Xmx256m","-XX:+UseSerialGC","-XX:+ExitOnOutOfMemoryError","-jar","/app/app.jar"]
  ```
  `.dockerignore`: alles außer `app/build/libs/app-all.jar`.
- **GOTCHA**: Bind-Mount `./data` auf dem Pi muss für UID 10001 beschreibbar sein (`chown 10001 data` in README).
  Fat-JAR-Name aus `buildFatJar` prüfen (`app-all.jar`). sqlite-jdbc enthält arm64-Linux-Native-Lib — kein Extra nötig.
- **VALIDATE**: `cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .` →
  `docker run --rm -e FP_SETUP_TOKEN=local-setup-token-123 -p 8095:8095 family-planner:local` → `/health` 200, Container `healthy`.

### Task 11: CI (GitHub Actions)
- **ACTION**: `.github/workflows/backend.yml`.
- **IMPLEMENT**:
  - Trigger: `push` auf `main` und `pull_request`, `paths: [backend/**, api/**, .github/workflows/backend.yml]`.
  - Job `test` (`ubuntu-latest`): checkout → `actions/setup-java@v4` (temurin 21) → `gradle/actions/setup-gradle@v4` →
    `working-directory: backend` `./gradlew check koverVerify` → Upload Test-/Kover-Reports bei Fehler.
  - Job `image` (`needs: test`, `runs-on: ubuntu-24.04-arm`, `permissions: {contents: read, packages: write}`):
    `./gradlew :app:buildFatJar` → `docker/setup-buildx-action` → bei `push` auf `main`: `docker/login-action`
    (ghcr.io, `${{ github.actor }}`, `${{ secrets.GITHUB_TOKEN }}`) → `docker/build-push-action` mit
    `context: backend`, `platforms: linux/arm64`, `build-args: VERSION=${{ github.sha }}`,
    `tags: ghcr.io/matthias1309/family-planner:${{ github.sha }}, ghcr.io/matthias1309/family-planner:stable`,
    `push: ${{ github.event_name == 'push' && github.ref == 'refs/heads/main' }}`.
  - `concurrency: { group: backend-${{ github.ref }}, cancel-in-progress: true }`.
- **GOTCHA**: Image-Name muss **kleingeschrieben** sein. Bei PRs nur bauen, nicht pushen. Nach dem ersten Push das
  GHCR-Paket einmalig manuell auf **public** stellen (ADR-0005) — Hinweis in README.
- **VALIDATE**: PR öffnen → beide Jobs grün; nach Merge Tags `sha` + `stable` in GHCR.

### Task 12: Deployment auf dem Pi
- **ACTION**: Dateien unter `deploy/`.
- **IMPLEMENT**:
  - `docker-compose.yml`: Service `family-planner`, `image: ghcr.io/matthias1309/family-planner:stable`,
    `container_name: family-planner`, `ports: ["8095:8095"]`, `env_file: .env`, `environment: FP_TLS_ENABLED: "true"`,
    `volumes: [./data:/app/data, ./certs:/app/certs:ro]`, `restart: unless-stopped`, `init: true`.
  - `.env.example`: `FP_SETUP_TOKEN=`, `FP_TLS_KEYSTORE_PASSWORD=`, `NTFY_URL=`, `NTFY_TOPIC=` (keine Werte).
  - `gen-cert.sh <dns-name> <ip>`: `openssl req -x509 -newkey rsa:3072 -sha256 -days 1825 -nodes -subj "/CN=$1"
    -addext "subjectAltName=DNS:$1,IP:$2"` → `certs/planner.crt` + `planner.key`; `openssl pkcs12 -export` →
    `certs/planner.p12` (Passwort aus `.env`). `planner.crt` wird in Phase 1b in die App übernommen.
  - `update.sh` (`set -euo pipefail`, im Compose-Verzeichnis):
    1. `prev=$(docker image inspect --format '{{.Id}}' $IMAGE 2>/dev/null || true)`
    2. `docker compose pull -q`; `new=$(docker image inspect …)`; gleich → exit 0.
    3. `new` == Inhalt von `.failed-image` → exit 0 (kein Endlos-Rollback auf dasselbe kaputte Image).
    4. `docker compose up -d`; bis 120 s `docker inspect --format '{{.State.Health.Status}}' family-planner` pollen.
    5. `healthy` → `rm -f .failed-image`, ntfy „Family Planner aktualisiert (<FP_VERSION>)“.
    6. sonst → `echo "$new" > .failed-image`; `docker tag "$prev" $IMAGE`; `docker compose up -d`;
       ntfy (Priority high) „Rollback – neues Image unhealthy“; exit 1.
    ntfy: `curl -fsS -H "Title: Family Planner" -d "$msg" "$NTFY_URL/$NTFY_TOPIC" || true`.
  - `family-planner-update.service` (`Type=oneshot`, `WorkingDirectory=/opt/containers/apps/family-planner`,
    `ExecStart=/opt/containers/apps/family-planner/update.sh`) + `.timer` (`OnBootSec=2min`, `OnUnitActiveSec=5min`).
  - `README.md`: Einrichtung Schritt für Schritt (Verzeichnis, `.env` aus Beispiel, `chown 10001 data`, Zertifikat,
    Pi-hole-DNS `planner.home.arpa`, `systemctl enable --now family-planner-update.timer`, Erst-Einrichtung per curl,
    Uptime-Kuma-Monitor auf `https://planner.home.arpa:8095/health` mit „Zertifikatfehler ignorieren“).
- **GOTCHA**: Backup macht die App selbst vor Migrationen (Task 4) — `update.sh` braucht kein sqlite3 auf dem Host.
  Ports laut CLAUDE.md: nur 8095 veröffentlichen; 8096 bleibt im Container.
- **VALIDATE**: `docker compose -f deploy/docker-compose.yml config` ok; `bash -n deploy/update.sh`;
  `shellcheck deploy/*.sh` (falls installiert).

### Task 13: Doku & Traceability abschließen
- **ACTION**: `traceability.md`, `backend/README.md`, PRD-Phase.
- **IMPLEMENT**: Matrix-Zeilen REQ-AUTH-001…003, REQ-OPS-001 mit Design (`03-design/core.md`), Unit-/Integrationstestnamen,
  Status „umgesetzt (Backend)“; E2E-Spalte bleibt bis Phase 1b leer. Backend-README: Befehle aus „Validation Commands“.
- **VALIDATE**: Jede REQ-AUTH-ID hat mindestens einen Test, dessen Name mit der ID beginnt (`grep -r "REQ-AUTH-00" backend`).

---

## Testing Strategy

### Unit Tests (core)

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-AUTH-001 parent creates member` | name „Lea“, CHILD | Member mit UUID, createdAt = Clock | |
| `REQ-AUTH-001 blank name is rejected` | „  “ | `Validation` | ✔ |
| `REQ-AUTH-001 name longer than 40 chars is rejected` | 41 Zeichen | `Validation` | ✔ |
| `REQ-AUTH-001 duplicate name is rejected case-insensitively` | „lea“ nach „Lea“ | `Conflict` | ✔ |
| `REQ-AUTH-002 setup creates first parent and device` | gültiges Token | PairingResult, Rolle PARENT | |
| `REQ-AUTH-002 setup with wrong token is rejected` | falsches Token | `Unauthorized` | ✔ |
| `REQ-AUTH-002 setup is rejected once members exist` | 2. Aufruf | `Conflict` | ✔ |
| `REQ-AUTH-002 setup is disabled without configured token` | Token null | `SetupDisabled` | ✔ |
| `REQ-AUTH-002 pairing code has six digits and expires in 15 minutes` | createCode | Code `^\d{6}$`, expiresAt = now+15m | |
| `REQ-AUTH-002 valid code pairs device to member` | Code, „Handy Lea“ | Token, Device.memberId = Kind | |
| `REQ-AUTH-002 expired pairing code is rejected` | +16 min | `InvalidPairingCode` | ✔ |
| `REQ-AUTH-002 pairing code can be used only once` | 2× pair | 2. → `InvalidPairingCode` | ✔ |
| `REQ-AUTH-002 new code invalidates previous code` | 2× createCode | alter Code ungültig | ✔ |
| `REQ-AUTH-002 malformed code is rejected` | „12ab“ | `Validation` | ✔ |
| `REQ-AUTH-002 pairing code for unknown member fails` | zufällige ID | `NotFound` | ✔ |
| `REQ-AUTH-002 token authenticates its device` | Token | Principal mit Rolle | |
| `REQ-AUTH-003 revoked device token is rejected` | revoke → authenticate | `null` | ✔ |
| `last seen is updated at most every five minutes` | 2 Aufrufe in 1 min | 1 Update | ✔ |
| `token and code are never stored in plain text` | Repo-Inhalt prüfen | nur Hashes | ✔ |
| `SecureRandomSecretGenerator produces url-safe 43-char tokens` | – | `^[A-Za-z0-9_-]{43}$` | |
| `DatabaseFactory enables foreign keys and WAL` | Temp-DB | Pragmas gesetzt | |
| `backup is written before pending migrations and rotated` | 11 Läufe | 10 Backups | ✔ |

### Integration Tests (app, mit Contract-Check)

| Test | Erwartung |
|---|---|
| `REQ-OPS-001 health reports ok` | 200 `{status: ok}` |
| `REQ-AUTH-002 setup then me returns parent` | 201 → `/me` 200, Rolle PARENT |
| `REQ-AUTH-002 second setup returns conflict` | 409 Envelope |
| `REQ-AUTH-002 request without token is unauthorized` | 401 Envelope (nicht leerer Body) |
| `REQ-AUTH-001 parent creates and lists members` | 201, Liste enthält Kind |
| `REQ-AUTH-002 child device cannot create members` | 403 |
| `REQ-AUTH-002 child pairs with code and sees itself in me` | 201 → `/me` = Kind |
| `REQ-AUTH-003 parent revokes device and its token stops working` | 204 → 401 |
| `invalid member id in path returns 400` | 400 |
| `unknown json fields are ignored, malformed json returns 400` | 201 / 400 |
| `pairing endpoint is rate limited` | 6. Versuch/min → 429 Envelope |
| `contract validator detects invalid response` | Report hat Fehler |

### Edge Cases Checklist
- [x] Empty input (leere Namen, leerer Body)
- [x] Maximum size input (Name 40/41 Zeichen, deviceName 60/61)
- [x] Invalid types (Rolle „ADMIN“, kaputtes JSON, Nicht-UUID im Pfad)
- [x] Concurrent access (doppeltes Setup, doppelte Code-Einlösung → atomar in einer Transaktion)
- [ ] Network failure — n/a (Backend)
- [x] Permission denied (Kind → Eltern-Endpunkte, widerrufenes Gerät)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Befunde

### Unit Tests
```bash
cd backend && ./gradlew :core:test
```
EXPECT: alle grün

### Full Test Suite + Coverage
```bash
cd backend && ./gradlew check koverVerify
```
EXPECT: grün, Coverage ≥ 80 %

### Database Validation
```bash
cd backend && ./gradlew :core:test --tests '*DatabaseFactoryTest*'
```
EXPECT: Migration V1 angewendet, Pragmas aktiv

### Image
```bash
cd backend && ./gradlew :app:buildFatJar && docker build -t family-planner:local .
```
EXPECT: Image baut; Container wird `healthy`

### Deploy-Dateien
```bash
docker compose -f deploy/docker-compose.yml config
```
EXPECT: gültige Compose-Datei (mit Dummy-`.env` aus `.env.example`)

### Manual Validation
- [ ] Lokal: Container starten, `POST /api/v1/setup` mit Setup-Token → Token; `GET /api/v1/me` mit Bearer → Elternteil.
- [ ] Kind anlegen, Kopplungscode erzeugen, `POST /api/v1/devices/pair` → Kind-Token; Kind-Token auf `POST /api/v1/members` → 403.
- [ ] Gerät entfernen → Token liefert 401.
- [ ] PR: CI grün; nach Merge: GHCR-Tags `<sha>` und `stable`, Paket auf public gestellt.
- [ ] Pi: README-Schritte, Timer aktiv, nach ≤ 5 min ntfy „aktualisiert“, `https://planner.home.arpa:8095/health` (mit `-k`) → 200.
- [ ] Rollback-Probe (optional): Image mit absichtlich kaputtem Healthcheck lokal taggen → `update.sh` rollt zurück + ntfy.

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] `./gradlew check koverVerify` grün, Coverage ≥ 80 %
- [ ] Jede REQ-AUTH-ID und REQ-OPS-001 durch benannte Tests abgedeckt, Traceability gepflegt
- [ ] Jeder Endpunkt durch Contract-Test gegen `api/openapi.yaml` geprüft
- [ ] Keine ktlint-/detekt-Befunde
- [ ] Image läuft auf dem Pi hinter HTTPS auf Port 8095, Update + Rollback per Timer funktionieren

## Completion Checklist
- [ ] Code folgt den in „Patterns to Mirror“ etablierten Mustern (sie sind ab jetzt Referenz für `meals`)
- [ ] Fehler nur über `AppException` + StatusPages, Envelope überall (auch 401/429)
- [ ] Keine Tokens/Codes/Passwörter in Logs, Code oder Repo (`.env`, `certs/` ignoriert)
- [ ] Rate Limiting aktiv (global + Kopplung/Setup)
- [ ] Clock/SecretGenerator injiziert, keine `Instant.now()`/`Random` im Produktivcode
- [ ] ADR-0008 + `auth.prd.md` + `core.md` geschrieben
- [ ] Keine Scope-Erweiterung über „NOT Building“ hinaus
- [ ] Self-contained — keine Rückfragen nötig

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Versionskonflikte Exposed 1.0 / Kotlin 2.2 / detekt | M | M | Mindestversionen oben; detekt ohne Typauflösung; bei Problem `ecc:kotlin-build-resolver` |
| Flyway-Version erkennt SQLite nicht | L | M | Integrationstest schlägt sofort fehl → passendes `flyway-database-*`-Modul ergänzen |
| Generierte Modelle passen nicht zu kotlinx.serialization | M | M | `dateLibrary=string`; Optionen anpassen statt generierten Code zu ändern |
| arm64-Runner / GHCR-Rechte | L | M | `ubuntu-24.04-arm` (öffentliches Repo), `packages: write`; Paket public stellen |
| Endlos-Rollback bei kaputtem `stable` | M | H | `.failed-image`-Marker in `update.sh` |
| Datenverlust bei fehlerhafter Migration | L | H | `VACUUM INTO`-Backup vor jeder ausstehenden Migration, 10 Generationen |
| Brute-Force auf 6-stelligen Code | L | M | 15 min TTL, einmalig, 5 Versuche/min/IP, nur LAN |
| Kein JDK lokal | H | L | Task 0 |

## Notes
- **Entscheidungen des Nutzers (2026-10-11):** Geräte-Anmeldung per **Kopplungscode** (Erst-Elternteil per Setup-Token);
  Phase 1 aufgeteilt in **1a Backend/CI/Deploy** (dieser Plan) und **1b Android** (eigener Plan, danach).
- Abweichung von REQ-AUTH-001-Wortlaut: „kocht“ + Anwesenheitsmuster wandern nach `meals` (Phase 2), weil `core` laut
  ADR-0001 keine Feature-Begriffe kennen soll. In `auth.prd.md` dokumentieren.
- TLS in Ktor statt Reverse-Proxy: kein zusätzlicher Container auf dem geteilten Pi; in ADR-0008 festhalten.
- `docs/02-architecture/README.md` zeigt noch ntfy als Push-Weg der App — durch ADR-0007 überholt; bei Task 1 die
  Kontextgrafik korrigieren (ntfy nur für Deploy-Meldungen).
- Für die Umsetzung passt `/ecc:prp-implement` mit diesem Plan; TDD-Reihenfolge innerhalb der Tasks 4–8 einhalten.
