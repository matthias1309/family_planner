# Plan: Badges – Backend/API (GAME Phase 2a)

## Summary

Das Modul `backend/gamification` bekommt einen festen **Badge-Katalog im Code** (12 Badges, davon 3 geheim), reine
Regelfunktionen über eine aus dem Journal berechnete Statistik je Kind und eine Tabelle `game_badges`, in der jede Vergabe
einmalig und unwiderruflich steht. Ausgewertet wird nach jeder Buchung (Ereignis-Abonnent und manuelle Buchung) sowie einmal
beim Start (Nachholen für Bestandsdaten). Ein neuer Endpunkt `GET /api/v1/gamification/members/{memberId}/badges` liefert die
Übersicht mit Fortschritt; geheime, nicht erhaltene Badges werden maskiert, Geschwister sehen nur erhaltene Badges.

## User Story

Als Kind möchte ich Badges automatisch bekommen, wenn ich eine Bedingung erfülle, und sehen, welche ich noch erreichen kann
(mit Fortschritt), damit ich wie in Videospielen auf etwas hinarbeiten kann.

## Problem → Solution

Punkte entstehen und sind sichtbar (Phase 1), aber außer der Rangliste gibt es keinen Anreiz →
Backend vergibt Badges automatisch nach festen Regeln und stellt eine Übersicht bereit, die die App in Phase 2b anzeigt.

## Metadata

- **Complexity**: Medium (≈ 16 Dateien, ~600 Zeilen inkl. Tests)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: 2 – Badges, Teil **2a Backend/API** (REQ-GAME-020, REQ-GAME-021). Die App (2b) bekommt einen eigenen Plan,
  wie bei Phase 1a/1b.
- **Estimated Files**: 7 neu, 9 geändert

---

## Entscheidungen (Planung 2026-10-31)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Aufteilung | Phase 2 = 2a Backend/API (dieser Plan) + 2b App (eigener Plan danach) | Wie Phase 1; API zuerst (ADR-0004) |
| Katalog | `enum class Badge` in `gamification.badges`, je Eintrag stabile `id` (String, **nie ändern**, steht in der DB), Emoji, Titel, Beschreibung, `secret`, optional `hint`, `target`, `metric: (BadgeStats) -> Int` | PRD: fester Katalog im Code; Enum erfüllt detekt `MagicNumber` (`ignoreEnums: true`); jede Regel ist „Messwert ≥ Ziel“ → Fortschritt einheitlich `min(metric, target)/target` |
| Statistik | `BadgeStats` wird je Kind aus **allen** Journalbuchungen berechnet (reine Funktion `badgeStatsFrom(entries)`): Anzahl `TASK`-Buchungen, Anzahl `COOKING`-Buchungen, Gesammelt, längste Tagesserie mit Aufgabe, Tage mit Aufgabe **und** Kochen | Journal ist die einzige Quelle; Familienvolumen → kein Caching (YAGNI) |
| Tagesgrenzen | Zone `Europe/Berlin` über eigenes `BADGE_ZONE` in `gamification` (nicht `meals.ZONE` – Modulgrenze); Tag einer Buchung = `bookedAt` in dieser Zone (bei Aufgaben = Abnahmezeitpunkt) | Feature-Module kennen sich nicht |
| Speicherung | Tabelle `game_badges(member_id, badge_id, awarded_at)`, PK `(member_id, badge_id)`, `INSERT OR IGNORE` | „höchstens einmal, nie entzogen“ ist strukturell garantiert; parallele Auswertungen sind harmlos |
| Kein Entzug | Vergabe wird nie gelöscht, auch wenn eine negative Korrektur Gesammelt wieder unter das Ziel senkt | REQ-GAME-020 |
| Auswertungszeitpunkt | `BadgeService.evaluate(memberId)` (1) im `PointsEventHandler` nach jeder gültigen Gutschrift – **auch wenn `append` false liefert** (Wiederholung nach Absturz holt fehlende Badges nach), (2) in `AccountService.book` nach dem Anlegen, (3) `evaluateAll()` einmal beim Start nach dem Nachhol-Dispatch | PRD „nach jeder Buchung“; Bestandsdaten aus Phase 1 und künftig neue Katalogeinträge werden nachträglich vergeben |
| `awardedAt` | Zeitpunkt der Auswertung (`clock.instant()`), nicht historisch rekonstruiert | KISS; beim Start-Nachholen bekommen Altbadges das Startdatum |
| Fehler bei Auswertung | Ereignis-Pfad: Ausnahme nicht fangen → Dispatcher wiederholt (Gutschrift idempotent). Start-Pfad: je Kind fangen, `log.error` mit Ausnahme, mit nächstem Kind weiter | Nichts still verschlucken; ein Kind darf den Rest nicht blockieren |
| Endpunkt | `GET /api/v1/gamification/members/{memberId}/badges` → `BadgeOverview { memberId, earnedCount, totalCount, items[] }`; Fehler wie Konto: unbekannt 404, Eltern-Mitglied 400 | Gleicher Ressourcen-Stil wie `…/account` |
| Sichtbarkeit | **Eigene Übersicht oder Eltern**: alle Badges in Katalogreihenfolge – erhalten (mit `awardedAt`), offen mit `progress`, geheim & offen **maskiert** (`title`/`description`/`emoji`/`progress` = `null`, `hint` sichtbar). **Geschwisterkind**: nur erhaltene Badges (vollständig, geheime auch – sie sind ja verdient), kein `progress`, `totalCount` trotzdem gesamt | REQ-GAME-021; Kinder sehen Geschwister-Badges, aber nicht deren Fortschritt (keine Detailansicht fremder Konten) |
| Maskierung | Server liefert `null`, die App zeigt „???“ | Darstellung gehört in die App; Bedingung verlässt den Server nie |
| Hinweis „neues Badge“ | Backend liefert nur `awardedAt`; die App merkt sich gesehene Badge-IDs lokal (Phase 2b) | Kein „gesehen“-Zustand im Backend nötig (YAGNI); Hintergrund-Abgleich ADR-0013 in 2b |
| Migration | `V11__gamification_badges.sql` in `backend/gamification` | Global fortlaufend (ADR-0010); V1–V10 belegt |
| API-Version | `info.version: 0.8.0` | Additiv |
| Kind-Prüfung | `requireChild` aus `AccountService` als `internal suspend fun MemberRepository.requireChild(id)` nach `gamification/Members.kt` ziehen und in beiden Services nutzen | DRY, gleiche Fehlermeldungen |

### Badge-Katalog (Startbestand)

Reihenfolge = Anzeige-Reihenfolge. `id` ist der DB-Schlüssel.

| # | `id` | Emoji | Titel | Beschreibung (Bedingung) | Messwert | Ziel | Geheim / Hinweis |
|---|---|---|---|---|---|---|---|
| 1 | `first-task` | 🧹 | Erste Aufgabe | Erledige deine erste Aufgabe | tasks | 1 | – |
| 2 | `tasks-10` | ✅ | Fleißig | Erledige 10 Aufgaben | tasks | 10 | – |
| 3 | `tasks-50` | 🏆 | Haushaltsprofi | Erledige 50 Aufgaben | tasks | 50 | – |
| 4 | `first-cooking` | 🍳 | Erstes Gericht | Koche dein erstes Gericht | cookings | 1 | – |
| 5 | `cooking-10` | 👨‍🍳 | Küchenchef | Koche 10 Gerichte | cookings | 10 | – |
| 6 | `cooking-25` | ⭐ | Sterneköchin/-koch | Koche 25 Gerichte | cookings | 25 | – |
| 7 | `points-100` | 💯 | Hunderter | Sammle 100 Punkte | collected | 100 | – |
| 8 | `points-500` | 💎 | Punktesammler | Sammle 500 Punkte | collected | 500 | – |
| 9 | `streak-7` | 🔥 | Dranbleiber | Erledige an 7 Tagen in Folge eine Aufgabe | longestTaskStreak | 7 | – |
| 10 | `streak-30` | 🌋 | Unaufhaltsam | Erledige an 30 Tagen in Folge eine Aufgabe | longestTaskStreak | 30 | geheim – „Bleib richtig lange dran“ |
| 11 | `all-rounder` | 🦸 | Allrounder | Erledige an einem Tag eine Aufgabe und koche | taskAndCookingDays | 1 | geheim – „Haushalt und Küche …“ |
| 12 | `points-1000` | 👑 | Legende | Sammle 1000 Punkte | collected | 1000 | geheim – „Nur für die ganz Großen“ |

Hinweise: Startbuchung vom Küchenboard (`MANUAL`) zählt für Gesammelt → `points-*` können sofort nach der Übernahme vergeben
werden (gewollt). `tasks`/`cookings` zählen nur Buchungen mit `amount > 0` der jeweiligen Art.

---

## UX Design

N/A — Backend/API. Die App-Darstellung (Badge-Übersicht unter „Ich“, Hinweis bei neuem Badge, Badges eines
Geschwisterkinds über die Rangliste) folgt in Phase 2b.

### Interaction Changes (API)

| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /api/v1/gamification/members/{id}/badges` | – | `200 BadgeOverviewResponse` | alle Rollen; Sichtbarkeit s. o. |
| Aufgabe abnehmen / Kochen bestätigen | Gutschrift | Gutschrift + ggf. Badge-Vergabe | keine Antwortänderung |
| `POST …/members/{id}/entries` | Buchung | Buchung + ggf. Badge-Vergabe | Antwort unverändert |
| Backend-Start | Nachhol-Dispatch | + `evaluateAll()` | vergibt Badges für Bestandsdaten |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/PointsEventHandler.kt` | all | Hier wird `evaluate` eingehängt |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt` | all | Service-/Fehler-/Logging-Stil, `requireChild` zum Auslagern, `book` bekommt `evaluate` |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | all | `EntryKind`, `Totals`, `totalsFrom`, Konstanten |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepository.kt` | all | Exposed-v1-Stil, `insertIgnore`, `dbQuery`, `toEntry()` |
| P0 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 91-124 | Koin-Module, Abonnement, `ApplicationStarted`-Nachholen, Routen |
| P0 | `api/openapi.yaml` | 934-1023, 1963-2073 | Gamification-Pfade und -Schemas, Envelope-Muster |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt` + `GamificationApiMappers.kt` | all | Routen-/Mapper-Stil, `targetMemberId()` |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/GamificationTables.kt` | all | Tabellenobjekte, Längenkonstanten |
| P1 | `backend/gamification/src/main/resources/db/migration/V10__gamification.sql` | all | Migrationsstil (Kommentar, `REFERENCES members ON DELETE CASCADE`) |
| P1 | `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/*.kt` | all | Fixture + In-Memory-Repos, die erweitert werden |
| P1 | `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/PointsSubscriptionTest.kt` | all | Integrationstest mit echter DB + Dispatcher |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/GamificationApiTest.kt` + `ApiTestSupport.kt` | all | API-Tests mit Vertragsprüfung, `setupFamilyWithChild` |
| P2 | `backend/core/src/main/kotlin/de/familyplanner/core/events/DomainEventDispatcher.kt` | all | Fehlschlag → Wiederholung beim nächsten Dispatch |
| P2 | `docs/03-design/gamification.md`, `docs/04-verification/traceability.md` | all | Doku, die ergänzt wird |
| P2 | `backend/config/detekt/detekt.yml` | 1-25 | `MagicNumber ignoreEnums`, `TooManyFunctions 15`, `ReturnCount 4` |

## External Documentation

Keine externe Recherche nötig — nur etablierte interne Muster (Exposed v1 `insertIgnore`, Ktor-Routen, Koin, Flyway).

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt:6-21, 46-53
@JvmInline
value class LedgerEntryId(val value: String)

enum class EntryKind(val countsAsCollected: Boolean) { TASK(true), COOKING(true), MANUAL(true), SHOP(false), ... }

const val MANUAL_AMOUNT_MAX = 1000
fun taskSourceKey(cardId: String) = "task:$cardId"
```
Pakete nach Fachbegriff (`ledger`, `account`, `settings`, `persistence`, `web`) → neu: `badges`.
Tabellen `game_*`, Exposed-Objekte `…Table`, Adapter `Exposed…Repository`, Fakes `InMemory…Repository`.

### ERROR_HANDLING
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt:36-39, 65-68
if (limit !in 1..ENTRIES_LIMIT_MAX) throw AppException.Validation("Ungültiger Wert für 'limit'")
requireChild(memberId)
if (!actor.isParent && actor.id != memberId) throw AppException.Forbidden("Nur eigenes Punktekonto")

private suspend fun requireChild(id: MemberId) {
    val member = members.findById(id) ?: throw AppException.NotFound("Mitglied nicht gefunden")
    if (member.role != Role.CHILD) throw AppException.Validation("Eltern sammeln keine Punkte")
}
```
Erst Existenz (404), dann Rolle (400). Meldungen auf Deutsch. `StatusPages` mappt `AppException` → Envelope.

### LOGGING_PATTERN
```kotlin
// SOURCE: PointsEventHandler.kt:21, 57, 77 ; AccountService.kt:54
private val log = LoggerFactory.getLogger(PointsEventHandler::class.java)
log.warn("Skipping points for seq {}: member {} is not a child", seq, credit.memberId)
log.info("Points credited: {} {} to {}", credit.kind, credit.amount, credit.memberId)
log.info("Manual points booked: {} to {}", amount, memberId.value)
```
Englisch, SLF4J-Platzhalter, nur IDs (keine Namen/Titel). Neu: `log.info("Badge awarded: {} to {}", badge.id, memberId.value)`,
`log.error("Badge evaluation failed for {}", memberId.value, failure)`.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepository.kt:23-38, 58-69
override suspend fun append(entry: LedgerEntry): Boolean =
    dbQuery(db) {
        val statement = LedgerTable.insertIgnore { it[id] = entry.id.value; ... }
        statement.insertedCount > 0
    }

override suspend fun entriesOf(memberId: MemberId, limit: Int): List<LedgerEntry> =
    dbQuery(db) {
        LedgerTable.selectAll()
            .where { LedgerTable.memberId eq memberId.value }
            .orderBy(LedgerTable.bookedAt to SortOrder.DESC, LedgerTable.seq to SortOrder.DESC)
            .limit(limit)
            .map { it.toEntry() }
    }
```
Imports: `org.jetbrains.exposed.v1.core.*`, `org.jetbrains.exposed.v1.jdbc.*` (Exposed v1!). Interface im Fachpaket, Adapter in `persistence`.

### SERVICE_PATTERN
```kotlin
// SOURCE: AccountService.kt:20-26
class AccountService(
    private val members: MemberRepository,
    private val ledger: LedgerRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val log = LoggerFactory.getLogger(AccountService::class.java)
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt:39-43, 62
get("/api/v1/gamification/members/{memberId}/account") {
    val actor = call.principalOrThrow().toActor()
    val account = accounts.account(actor, call.targetMemberId(), call.entriesLimit())
    call.respond(PointsAccountResponse(success = true, data = account.toApi()))
}
private fun ApplicationCall.targetMemberId() = MemberId(uuidParameter("memberId"))
```

### MAPPER_PATTERN
```kotlin
// SOURCE: GamificationApiMappers.kt:17-25, 35
fun LedgerEntry.toApi() = ApiLedgerEntry(id = id.value, kind = LedgerEntryKind.valueOf(kind.name), ..., bookedAt = bookedAt.toString())
fun LeaderboardEntry.toApi() = ApiLeaderboardEntry(memberId = memberId.value, name = name, rank = rank, collected = collected)
```
Generierte Modelle mit `as Api…`-Alias importieren, wenn der Domänenname gleich ist.

### KOIN_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/GamificationModule.kt:17-24
fun gamificationModule(clock: Clock) =
    module {
        single<LedgerRepository> { ExposedLedgerRepository(get()) }
        single { AccountService(get(), get(), clock) }
        single { PointsEventHandler(get(), get(), get()) }
    }
```

### MIGRATION_PATTERN
```sql
-- SOURCE: backend/gamification/src/main/resources/db/migration/V10__gamification.sql:1-15
-- GAME phase 1: immutable points ledger per child. Corrections are counter-entries, never updates.
CREATE TABLE game_ledger (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  ...
);
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/gamification/src/test/kotlin/de/familyplanner/gamification/ledger/PointsEventHandlerTest.kt:11-36
class PointsEventHandlerTest {
    private val fixture = GamificationFixture()

    @Test
    fun `REQ-GAME-010 accepted task credits its points`() =
        runTest {
            val child = fixture.child()

            fixture.handler.handle(fixture.stored(accepted(child.id.value)))

            val entry = fixture.ledger.entries.single()
            assertEquals(EntryKind.TASK, entry.kind)
        }
```
`kotlin.test` + `kotlinx.coroutines.test.runTest`, AAA mit Leerzeilen, Name beginnt mit REQ-ID.
Integration: `@TempDir` + `DatabaseFactory.create(dir.resolve("t.db"), clock)` (führt alle Migrationen aus), s. `PointsSubscriptionTest.kt:26-50`.
API: `testApplication { start(); val family = setupFamilyWithChild(); client.call(...) }` – `call` prüft Request + Response gegen `openapi.yaml`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | Pfad `…/members/{memberId}/badges`, Schemas `Badge`, `BadgeProgress`, `BadgeOverview`, `BadgeOverviewResponse`; Version 0.8.0 |
| `backend/gamification/src/main/resources/db/migration/V11__gamification_badges.sql` | CREATE | Tabelle `game_badges` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/Members.kt` | CREATE | `MemberRepository.requireChild` (aus `AccountService` ausgelagert) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/Badge.kt` | CREATE | Katalog-Enum, `BadgeStats`, `BADGE_ZONE` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/BadgeStats.kt` | CREATE | `badgeStatsFrom(entries)` + Serien-Berechnung (reine Funktionen) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/BadgeRepository.kt` | CREATE | Interface `award`, `awardedOf` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/BadgeService.kt` | CREATE | `evaluate`, `evaluateAll`, `overview` + Modell `BadgeOverview`/`BadgeView` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedBadgeRepository.kt` | CREATE | Adapter |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/GamificationTables.kt` | UPDATE | `BadgeTable` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerRepository.kt` + `persistence/ExposedLedgerRepository.kt` | UPDATE | `allEntriesOf(memberId)` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/PointsEventHandler.kt` | UPDATE | `BadgeService` injizieren, nach Gutschrift auswerten |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt` | UPDATE | `requireChild` auslagern, nach `book` auswerten |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/GamificationModule.kt` | UPDATE | Koin-Bindings |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt` + `GamificationApiMappers.kt` | UPDATE | Route + Mapper |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | Routen-Parameter, `evaluateAll()` beim Start |
| Tests: `fakes/*`, `badges/BadgeStatsTest.kt`, `badges/BadgeServiceTest.kt`, `persistence/ExposedBadgeRepositoryTest.kt`, `persistence/PointsSubscriptionTest.kt`, `ledger/PointsEventHandlerTest.kt`, `account/AccountServiceTest.kt`, `app/GamificationApiTest.kt`, `app/ApiTestSupport.kt` | CREATE/UPDATE | s. Testing Strategy |
| `docs/03-design/gamification.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/gamification.prd.md` | UPDATE | Doku, REQ-Zuordnung, Phasenstatus |

## NOT Building

- App-Seite (Übersicht, Hinweis bei neuem Badge, Hintergrund-Abgleich) → Phase 2b.
- „Gesehen“-Status im Backend, Push/ntfy-Benachrichtigung.
- Von Eltern definierbare Badges, gekaufte Badges, Entzug von Badges (PRD Won't).
- Historisch rekonstruiertes Vergabedatum.
- Badges für Shop/Belohnungen (Phasen 3/4 – Katalog ist später additiv erweiterbar; `evaluateAll` beim Start vergibt neue Einträge nachträglich).
- Badge-Anzahl in der Rangliste oder im Konto.
- Neuer Domain-Event-Typ „Badge vergeben“ (kein anderes Modul reagiert darauf; YAGNI).

---

## Step-by-Step Tasks

### Task 1: OpenAPI-Vertrag (ADR-0004, Skill `contract-first`)
- **ACTION**: `api/openapi.yaml` additiv erweitern; `info.version: 0.8.0`.
- **IMPLEMENT**: Pfad direkt nach `/api/v1/gamification/members/{memberId}/entries`:
  ```yaml
  /api/v1/gamification/members/{memberId}/badges:
    get:
      operationId: getBadges
      tags: [gamification]
      summary: Badges eines Kindes (eigene und Eltern mit offenen Badges und Fortschritt, Geschwister nur erhaltene)
      parameters:
        - $ref: '#/components/parameters/MemberId'
      responses:
        '200': { description: Badge-Übersicht, content: { application/json: { schema: { $ref: '#/components/schemas/BadgeOverviewResponse' } } } }
        '400': { $ref: '#/components/responses/ValidationFailed' }
        '401': { $ref: '#/components/responses/Unauthorized' }
        '404': { $ref: '#/components/responses/NotFound' }
        '429': { $ref: '#/components/responses/RateLimited' }
  ```
  (im Stil der Nachbarn mehrzeilig ausschreiben). Schemas nach `GameSettings`:
  ```yaml
  BadgeProgress:
    type: object
    required: [current, target]
    properties:
      current: { type: integer, minimum: 0, description: 'Auf target gekappt' }
      target: { type: integer, minimum: 1 }
  Badge:
    type: object
    required: [id, secret, earned]
    properties:
      id: { type: string, description: 'Stabiler Schlüssel aus dem Katalog' }
      emoji: { type: string, nullable: true, description: 'null bei geheimem, nicht erhaltenem Badge' }
      title: { type: string, nullable: true, description: 'null bei geheimem, nicht erhaltenem Badge (App zeigt ???)' }
      description: { type: string, nullable: true, description: 'Bedingung; null bei geheimem, nicht erhaltenem Badge' }
      hint: { type: string, nullable: true, description: 'Nur bei geheimen Badges' }
      secret: { type: boolean }
      earned: { type: boolean }
      awardedAt: { type: string, format: date-time, nullable: true }
      progress:
        allOf: [ { $ref: '#/components/schemas/BadgeProgress' } ]
        nullable: true
        description: 'Nur bei offenen, nicht maskierten Badges in der eigenen bzw. Eltern-Ansicht'
  BadgeOverview:
    type: object
    required: [memberId, earnedCount, totalCount, items]
    properties:
      memberId: { type: string, format: uuid }
      earnedCount: { type: integer, minimum: 0 }
      totalCount: { type: integer, minimum: 0, description: 'Alle Badges im Katalog inkl. geheimer' }
      items: { type: array, description: 'Katalogreihenfolge', items: { $ref: '#/components/schemas/Badge' } }
  BadgeOverviewResponse: # Envelope wie PointsAccountResponse
  ```
- **MIRROR**: `PointsAccountResponse` (Envelope `success`/`data` nullable `allOf`/`error`), Pfad `…/account`.
- **GOTCHA**: Kein Feld `entries` (Generator benennt um → `propertyEntries`); `items` verwenden. Nullable-`$ref` nur über `allOf` + `nullable` (OpenAPI 3.0.3). Kein `403` – jedes Familienmitglied darf lesen, nur der Umfang variiert.
- **VALIDATE**: `cd backend && ./gradlew :api-contract:build` (Modelle `Badge`, `BadgeProgress`, `BadgeOverview`, `BadgeOverviewResponse` generiert); `cd android && ./gradlew assembleDebug` bleibt grün.

### Task 2: Migration und Tabelle
- **ACTION**: `V11__gamification_badges.sql` anlegen, `BadgeTable` ergänzen.
- **IMPLEMENT**:
  ```sql
  -- GAME phase 2: badges awarded to a child. Awarded at most once per badge and never revoked.
  -- badge_id is the stable id from the catalog in code (Badge.id) - never rename.
  CREATE TABLE game_badges (
    member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
    badge_id TEXT NOT NULL,
    awarded_at INTEGER NOT NULL,
    PRIMARY KEY (member_id, badge_id)
  );
  ```
  ```kotlin
  object BadgeTable : Table("game_badges") {
      val memberId = varchar("member_id", ID_LENGTH)
      val badgeId = varchar("badge_id", BADGE_ID_LENGTH)
      val awardedAt = long("awarded_at")
      override val primaryKey = PrimaryKey(memberId, badgeId)
  }
  private const val BADGE_ID_LENGTH = 40
  ```
- **MIRROR**: MIGRATION_PATTERN, `GamificationTables.kt`.
- **GOTCHA**: Flyway findet die Datei über den Klassenpfad (`db/migration`), keine Registrierung nötig. Nummer 11 global prüfen (`find backend -path '*db/migration/*.sql'`).
- **VALIDATE**: `ExposedBadgeRepositoryTest` (Task 5) läuft gegen echte DB.

### Task 3: Katalog und Statistik (reine Funktionen, REQ-GAME-020/021)
- **ACTION**: `badges/Badge.kt` und `badges/BadgeStats.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Values computed from a child's whole ledger; input of every badge rule. */
  data class BadgeStats(
      val tasks: Int,
      val cookings: Int,
      val collected: Int,
      val longestTaskStreak: Int,
      val taskAndCookingDays: Int,
  ) { companion object { val ZERO = BadgeStats(0, 0, 0, 0, 0) } }

  /** Fixed catalog (PRD); order = display order. [id] is stored in game_badges - never change it. */
  enum class Badge(
      val id: String,
      val emoji: String,
      val title: String,
      val description: String,
      val target: Int,
      val secret: Boolean = false,
      val hint: String? = null,
      val metric: (BadgeStats) -> Int,
  ) {
      FIRST_TASK("first-task", "🧹", "Erste Aufgabe", "Erledige deine erste Aufgabe", 1, metric = { it.tasks }),
      ... // alle 12 laut Katalogtabelle
      ;
      fun isMetBy(stats: BadgeStats) = metric(stats) >= target
      fun progressOf(stats: BadgeStats) = minOf(metric(stats), target)
  }

  val BADGE_ZONE: ZoneId = ZoneId.of("Europe/Berlin")
  ```
  `BadgeStats.kt`:
  ```kotlin
  fun badgeStatsFrom(entries: List<LedgerEntry>): BadgeStats {
      val taskDays = daysOf(entries, EntryKind.TASK)
      val cookingDays = daysOf(entries, EntryKind.COOKING)
      return BadgeStats(
          tasks = entries.count { it.kind == EntryKind.TASK && it.amount > 0 },
          cookings = entries.count { it.kind == EntryKind.COOKING && it.amount > 0 },
          collected = totalsFrom(entries.map { it.kind to it.amount }).collected,
          longestTaskStreak = longestStreak(taskDays),
          taskAndCookingDays = taskDays.intersect(cookingDays).size,
      )
  }
  private fun daysOf(entries: List<LedgerEntry>, kind: EntryKind): Set<LocalDate> =
      entries.filter { it.kind == kind && it.amount > 0 }.map { LocalDate.ofInstant(it.bookedAt, BADGE_ZONE) }.toSet()
  /** Length of the longest run of consecutive calendar days. */
  internal fun longestStreak(days: Set<LocalDate>): Int { sortiert durchlaufen, Lauf zurücksetzen wenn prev.plusDays(1) != day }
  ```
- **MIRROR**: NAMING_CONVENTION (`EntryKind`-Enum mit Konstruktor-Eigenschaften), `totalsFrom` wiederverwenden.
- **IMPORTS**: `de.familyplanner.gamification.ledger.{EntryKind, LedgerEntry, totalsFrom}`, `java.time.{LocalDate, ZoneId}`.
- **GOTCHA**: Enum-Konstruktor mit Lambda als **letztem** Parameter und benannt (`metric = { … }`), sonst schlecht lesbar; detekt `MagicNumber` ignoriert Enums. `MaxLineLength` beachten (Einträge mehrzeilig). Doppelte IDs verhindert ein Unit-Test. Emoji `👨‍🍳` ist eine ZWJ-Sequenz – in UTF-8-Datei unproblematisch.
- **VALIDATE**: `BadgeStatsTest` (Task 8).

### Task 4: `allEntriesOf` im Journal
- **ACTION**: `LedgerRepository.allEntriesOf(memberId): List<LedgerEntry>` (Reihenfolge egal, dokumentieren: „oldest first“ per `seq ASC`).
- **IMPLEMENT**: Exposed wie `entriesOf` ohne `limit`, `orderBy(LedgerTable.seq to SortOrder.ASC)`; In-Memory-Fake: `entries.filter { it.memberId == memberId }`.
- **MIRROR**: REPOSITORY_PATTERN.
- **VALIDATE**: Test in `ExposedLedgerRepositoryTest` (`REQ-GAME-020 allEntriesOf returns every entry of the member only`).

### Task 5: `BadgeRepository` + Adapter
- **ACTION**: Interface in `badges/`, Adapter in `persistence/`.
- **IMPLEMENT**:
  ```kotlin
  interface BadgeRepository {
      /** Stores the award; false (and nothing stored) if the member already has [badgeId]. */
      suspend fun award(memberId: MemberId, badgeId: String, at: Instant): Boolean
      /** badgeId → awardedAt of every badge the member has. */
      suspend fun awardedOf(memberId: MemberId): Map<String, Instant>
  }
  ```
  Adapter: `BadgeTable.insertIgnore { … }.insertedCount > 0`; `selectAll().where { memberId eq … }.associate { it[badgeId] to Instant.ofEpochMilli(it[awardedAt]) }`.
  Fake `InMemoryBadgeRepository` in `fakes/InMemoryGamificationRepositories.kt` (`val awards = mutableMapOf<Pair<MemberId, String>, Instant>()`).
- **MIRROR**: `ExposedLedgerRepository.append`.
- **GOTCHA**: `insertIgnore` verschluckt auch FK-Verletzungen (unbekanntes Mitglied) → Service ruft `award` nur für existierende Kinder.
- **VALIDATE**: `ExposedBadgeRepositoryTest`: zweites `award` → false, `awardedAt` unverändert; Mitglied löschen → Vergabe weg (CASCADE).

### Task 6: `BadgeService` (REQ-GAME-020/021)
- **ACTION**: `badges/BadgeService.kt` mit Modell.
- **IMPLEMENT**:
  ```kotlin
  data class BadgeView(
      val badge: Badge,
      val masked: Boolean,          // secret and not earned
      val awardedAt: Instant?,
      val progress: Int?,           // null when earned, masked or sibling view
  )
  data class BadgeOverview(val memberId: MemberId, val earnedCount: Int, val totalCount: Int, val items: List<BadgeView>)

  class BadgeService(
      private val members: MemberRepository,
      private val ledger: LedgerRepository,
      private val badges: BadgeRepository,
      private val clock: Clock,
  ) {
      /** Awards every badge whose rule is met and that the child does not have yet; safe to call any time. */
      suspend fun evaluate(memberId: MemberId) {
          val stats = badgeStatsFrom(ledger.allEntriesOf(memberId))
          val owned = badges.awardedOf(memberId).keys
          Badge.entries.filter { it.id !in owned && it.isMetBy(stats) }.forEach { badge ->
              if (badges.award(memberId, badge.id, clock.instant())) log.info("Badge awarded: {} to {}", badge.id, memberId.value)
          }
      }

      /** Startup catch-up for every child; one failing child does not stop the others. */
      suspend fun evaluateAll() {
          members.findAll().filter { it.role == Role.CHILD }.forEach { child ->
              try { evaluate(child.id) }
              catch (cancelled: CancellationException) { throw cancelled }
              catch (@Suppress("TooGenericExceptionCaught") failure: Exception) {
                  log.error("Badge evaluation failed for {}", child.id.value, failure)
              }
          }
      }

      suspend fun overview(actor: Actor, memberId: MemberId): BadgeOverview {
          members.requireChild(memberId)
          val awarded = badges.awardedOf(memberId)
          val fullView = actor.isParent || actor.id == memberId
          val stats = if (fullView) badgeStatsFrom(ledger.allEntriesOf(memberId)) else BadgeStats.ZERO
          val items = Badge.entries
              .filter { fullView || it.id in awarded }
              .map { badge -> viewOf(badge, awarded[badge.id], stats, fullView) }
          return BadgeOverview(memberId, awarded.keys.count { id -> Badge.entries.any { it.id == id } }, Badge.entries.size, items)
      }
  }
  ```
  `viewOf`: earned → `BadgeView(badge, masked=false, awardedAt, progress=null)`; nicht erhalten & `secret` → `masked=true, progress=null`; sonst `progress = badge.progressOf(stats)`.
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING (`requireChild` aus Task 7), Catch-Muster aus `DomainEventDispatcher.kt:46-55`.
- **IMPORTS**: `kotlinx.coroutines.CancellationException`, `de.familyplanner.gamification.account.Actor`, `de.familyplanner.gamification.requireChild`.
- **GOTCHA**: `earnedCount` nur über IDs zählen, die im Katalog noch existieren (falls je ein Eintrag entfällt, steht die Zeile noch in der DB). Badge-Vergabe **vor** `overview` passiert nicht implizit – `overview` schreibt nie. `evaluate` bewertet nur, ruft nicht `requireChild` (Aufrufer garantieren ein Kind).
- **VALIDATE**: `BadgeServiceTest` (Task 8).

### Task 7: Einhängen in Gutschrift und manuelle Buchung; `requireChild` auslagern
- **ACTION**:
  1. `gamification/Members.kt`: `internal suspend fun MemberRepository.requireChild(id: MemberId): Member` (Text/Fehler 1:1 aus `AccountService.kt:65-68`); `AccountService` nutzt es (`members.requireChild(memberId)`), private Funktion entfällt.
  2. `PointsEventHandler(members, ledger, settings, badges: BadgeService, newId)`: in `book()` nach dem `if (ledger.append(entry)) … else …`-Block **immer** `badges.evaluate(memberId)` aufrufen (nur erreichbar, wenn Kind und Betrag ≠ 0).
  3. `AccountService(members, ledger, badges: BadgeService, clock, newId)`: nach `ledger.append(entry)` und dem Log `badges.evaluate(memberId)`.
- **MIRROR**: bestehender Kontrollfluss; Parameterreihenfolge: Abhängigkeiten vor `clock`/`newId`.
- **GOTCHA**: Konstruktoränderungen ziehen `GamificationFixture`, `PointsSubscriptionTest.setUp` und Koin nach. Wirft `evaluate` im Ereignis-Pfad, wiederholt der Dispatcher das Ereignis; `append` liefert dann `false`, `evaluate` läuft trotzdem erneut → kein verlorenes Badge. Im manuellen Pfad: Buchung ist gespeichert, Request antwortet 500; nächste Buchung/Neustart holt nach (in Notes dokumentieren).
- **VALIDATE**: bestehende Tests grün + neue Handler-/Account-Tests (Task 8).

### Task 8: Tests (TDD – vor Task 3/5/6/7 schreiben)
- **ACTION**: Tests laut Testing Strategy anlegen; Fixture erweitern:
  ```kotlin
  val badgeRepository = InMemoryBadgeRepository()
  val badges = BadgeService(members, ledger, badgeRepository, clock)
  val accounts = AccountService(members, ledger, badges, clock) { "entry-${++nextId}" }
  val handler = PointsEventHandler(members, ledger, settings, badges) { "credit-${++nextId}" }
  ```
  Hilfsfunktion im Test: `entry(kind, amount, at: String)` baut `LedgerEntry` mit `Instant.parse(at)`.
- **MIRROR**: TEST_STRUCTURE.
- **GOTCHA**: Serien-Tests mit Zeiten nahe Mitternacht (z. B. `2026-10-24T22:30:00Z` = 00:30 Berlin am 25.) prüfen die Zone. `MutableClock` aus `core` testFixtures.
- **VALIDATE**: `cd backend && ./gradlew :gamification:test`.

### Task 9: Route, Mapper, Verdrahtung
- **ACTION**:
  - Mapper: `BadgeView.toApi()` → `ApiBadge(id = badge.id, emoji = badge.emoji.takeUnless { masked }, title = …, description = …, hint = badge.hint, secret = badge.secret, earned = awardedAt != null, awardedAt = awardedAt?.toString(), progress = progress?.let { BadgeProgress(it, badge.target) })`; `BadgeOverview.toApi()`.
  - Route in `gamificationRoutes(accounts, settings, badges: BadgeService)`:
    ```kotlin
    get("/api/v1/gamification/members/{memberId}/badges") {
        val actor = call.principalOrThrow().toActor()
        call.respond(BadgeOverviewResponse(success = true, data = badges.overview(actor, call.targetMemberId()).toApi()))
    }
    ```
  - Koin: `single<BadgeRepository> { ExposedBadgeRepository(get()) }`, `single { BadgeService(get(), get(), get(), clock) }`, `AccountService(get(), get(), get(), clock)`, `PointsEventHandler(get(), get(), get(), get())`.
  - `Application.kt`: `gamificationRoutes(get<AccountService>(), get<GameSettingsService>(), get<BadgeService>())`;
    ```kotlin
    // Catch-up delivery for events logged before the last shutdown (ADR-0016), then badges for existing data.
    monitor.subscribe(ApplicationStarted) {
        launch {
            get<DomainEventDispatcher>().dispatchPending()
            get<BadgeService>().evaluateAll()
        }
    }
    ```
- **MIRROR**: ROUTE_PATTERN, MAPPER_PATTERN, KOIN_PATTERN.
- **GOTCHA**: Namenskonflikt `Badge` (Domäne) vs. generiertes `de.familyplanner.api.model.Badge` → `as ApiBadge`; ebenso `BadgeOverview as ApiBadgeOverview`. Bei Ktor-Testanwendungen läuft `ApplicationStarted` ebenfalls – `evaluateAll` auf leerer DB ist harmlos.
- **VALIDATE**: `GamificationApiTest` (Task 10).

### Task 10: API-Tests
- **ACTION**: `ApiTestSupport.kt`: Kopplung eines Kindes aus `setupFamilyWithChild` in `suspend fun HttpClient.addPairedChild(parentToken: String, name: String): Pair<String, String>` (childId, token) auslagern und dort wiederverwenden. In `GamificationApiTest` Hilfsfunktion `badgesOf(token, memberId): BadgeOverview` (wie `accountOf`).
- **IMPLEMENT**: Tests s. Testing Strategy (Integration/API).
- **VALIDATE**: `cd backend && ./gradlew :app:test --tests '*GamificationApiTest*'`.

### Task 11: Doku, Traceability, PRD
- **ACTION**:
  - `docs/03-design/gamification.md`: Abschnitt „Badges (Phase 2a)“ – Katalogtabelle, `BadgeStats`, Auswertungszeitpunkte, Sichtbarkeit/Maskierung, Tabelle `game_badges`, Endpunkt, Unit-/Integrationstests; Kopfzeile „Abgedeckte Anforderungen“ um REQ-GAME-020/021 ergänzen.
  - `docs/04-verification/traceability.md`: Zeilen REQ-GAME-020/021 (Status „umgesetzt (Backend; App folgt in 2b)“).
  - PRD: Phase 2 PRP-Spalte bleibt auf diesen Plan zeigen; nach Umsetzung Bericht verlinken.
- **VALIDATE**: Links prüfen; REQ-IDs in Testnamen stimmen mit Traceability überein.

---

## Testing Strategy

### Unit Tests

| Test (Klasse) | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `BadgeStatsTest` `REQ-GAME-020 counts tasks and cookings` | 3 TASK, 2 COOKING, 1 MANUAL | tasks 3, cookings 2, collected = Summe | |
| `BadgeStatsTest` `REQ-GAME-020 negative manual lowers collected but not counts` | +100 MANUAL, −5 MANUAL | collected 95 | ja |
| `BadgeStatsTest` `REQ-GAME-020 longest streak uses Berlin days` | TASK 22:30Z am 24.10. und 08:00Z am 25.10. | Streak 1 (beides 25.10. Berlin) | ja (Zone) |
| `BadgeStatsTest` `REQ-GAME-020 longest streak finds the longest run` | Tage 1,2,3,5,6 | 3 | |
| `BadgeStatsTest` `REQ-GAME-020 empty ledger yields zero stats` | [] | `BadgeStats.ZERO` | ja |
| `BadgeStatsTest` `REQ-GAME-020 task and cooking on same day count` | TASK + COOKING am selben Berlin-Tag | taskAndCookingDays 1 | |
| `BadgeCatalogTest` (oder in `BadgeStatsTest`) `REQ-GAME-021 catalog ids are unique and stable` | `Badge.entries` | IDs eindeutig; feste Liste der 12 IDs (Schutz gegen Umbenennen) | ja |
| `BadgeServiceTest` `REQ-GAME-020 tenth cooking awards cooking-10` | 9 COOKING-Buchungen, evaluate, dann 10. + evaluate | erst nach der 10. vergeben, `awardedAt` = Uhr | PRD-Beispiel |
| `BadgeServiceTest` `REQ-GAME-020 badge is awarded only once and never revoked` | 100 Punkte → evaluate; −50 MANUAL → evaluate | `points-100` bleibt, `awardedAt` unverändert | ja |
| `BadgeServiceTest` `REQ-GAME-020 evaluateAll awards for existing data of every child` | 2 Kinder mit Buchungen, 1 Elternteil | beide Kinder bewertet, Eltern nicht | |
| `BadgeServiceTest` `REQ-GAME-021 own overview lists open badges with progress` | 7 Aufgaben | `tasks-10` progress 7, target 10; `first-task` earned | PRD-Beispiel 7/10 |
| `BadgeServiceTest` `REQ-GAME-021 secret badge is masked until earned` | ohne/mit 30er-Serie | masked true, progress null, hint gesetzt / danach unmaskiert | ja |
| `BadgeServiceTest` `REQ-GAME-021 sibling sees only earned badges` | Kind A schaut Kind B an | nur erhaltene, kein progress, totalCount 12 | |
| `BadgeServiceTest` `REQ-GAME-021 parent sees full overview` | Eltern-Actor | wie eigene Ansicht | |
| `BadgeServiceTest` `REQ-GAME-021 unknown member 404, parent member 400` | | `AppException.NotFound` / `Validation` | ja |
| `PointsEventHandlerTest` `REQ-GAME-020 accepted task awards first-task` | TaskAccepted | `first-task` in `badgeRepository` | |
| `PointsEventHandlerTest` `REQ-GAME-020 redelivered event still evaluates badges` | Buchung vorhanden, Badge fehlt, Ereignis erneut | Badge vergeben, 1 Buchung | ja (Wiederholung) |
| `PointsEventHandlerTest` `REQ-GAME-020 skipped event awards nothing` | Elternteil als Mitglied | keine Vergabe | ja |
| `AccountServiceTest` `REQ-GAME-020 manual booking evaluates badges` | +100 MANUAL | `points-100` vergeben | |

### Integration Tests

| Test | Setup | Erwartung |
|---|---|---|
| `ExposedBadgeRepositoryTest` `REQ-GAME-020 award is idempotent` | echte DB | zweites `award` false, Zeitpunkt bleibt |
| `ExposedBadgeRepositoryTest` `REQ-GAME-020 awards are removed with the member` | Mitglied löschen | `awardedOf` leer |
| `ExposedLedgerRepositoryTest` `REQ-GAME-020 allEntriesOf returns every entry of the member only` | 2 Mitglieder, >100 Buchungen | alle des einen, keine des anderen |
| `PointsSubscriptionTest` `REQ-GAME-020 dispatched task acceptance awards first-task once` | Dispatcher + Handler + BadgeService auf echter DB, zweimal dispatch | genau eine Zeile in `game_badges` |
| `GamificationApiTest` `REQ-GAME-020 accepted task shows first-task badge` | `acceptTask` | `GET …/badges` (Kind) → `first-task` earned, `earnedCount` 1, Vertrag gültig |
| `GamificationApiTest` `REQ-GAME-021 sibling sees only earned badges and secrets stay masked` | 2. Kind via `addPairedChild`, Buchung +100 für Kind 1 | Kind 2 sieht von Kind 1 nur erhaltene; Kind 1 sieht `points-1000` mit `title == null` |
| `GamificationApiTest` `REQ-GAME-021 badges of parent member 400, unknown 404` | | Statuscodes |

### Edge Cases Checklist
- [x] Leeres Journal (Kind ohne Buchung) → alle offen, progress 0
- [x] Negative Korrektur nach Vergabe → kein Entzug
- [x] Doppelte Zustellung / Wiederholung nach Fehler
- [x] Gleichzeitige Auswertung (Request + Dispatcher) → PK + `INSERT OR IGNORE`
- [x] Zeitzonen-Tagesgrenze bei Serien
- [x] Eltern-Mitglied / unbekanntes Mitglied
- [x] Geheimes Badge: Bedingung verlässt den Server nicht
- [ ] Netzwerkfehler – N/A (Backend)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Funde (`TooManyFunctions` ≤ 15 je Datei/Klasse; Serienlogik in `BadgeStats.kt` getrennt vom Service)

### Unit + Integration Tests
```bash
cd backend && ./gradlew :gamification:test :app:test
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
EXPECT: grün (nur neue Modelle/Endpunkt)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=test-setup-token-123456 FP_DB_PATH=/tmp/fp-badges.db FP_TLS_ENABLED=false ./gradlew :app:run
sqlite3 /tmp/fp-badges.db "select version, success from flyway_schema_history order by installed_rank"
```
EXPECT: V1–V11 erfolgreich, Tabelle `game_badges` vorhanden. Auf einer **Kopie** der Testinstanz-DB starten: Log zeigt
`Badge awarded: …` für Kinder mit Bestandsbuchungen; zweiter Start vergibt nichts Neues
(`select member_id, badge_id, awarded_at from game_badges`).

### Manual Validation (lokal gegen `:app:run`, curl)
- [ ] Familie einrichten, zwei Kinder anlegen und koppeln (wie `deploy/README.md`)
- [ ] Karte für Kind 1 zuweisen, melden, abnehmen → `GET …/members/{kind1}/badges` (Kind-1-Token): `first-task` earned, `tasks-10` progress 1/10
- [ ] Eltern buchen +100 für Kind 1 → `points-100` earned; −50 buchen → bleibt earned
- [ ] Kind-2-Token liest Badges von Kind 1 → nur erhaltene, kein `progress`
- [ ] Geheime Badges in Kind-1-Ansicht: `title`/`description`/`emoji` = null, `hint` gesetzt
- [ ] Neustart → keine doppelten Vergaben
- [ ] Nach Deploy: aktuelle Familien-App unverändert funktionsfähig; Testinstanz liefert `…/badges`

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Alle Validierungsbefehle grün
- [ ] Tests geschrieben (zuerst) und grün, Testnamen mit REQ-GAME-020/021
- [ ] OpenAPI 0.8.0 nur additiv; Android baut
- [ ] Doku (Moduldesign, Traceability) aktualisiert

## Completion Checklist
- [ ] Code folgt den Mustern oben (Exposed v1, Koin, Envelope, deutsche Fehlermeldungen, englische Logs)
- [ ] Fehler-/Wiederholungsverhalten wie in „Entscheidungen“
- [ ] Keine Magic Numbers außerhalb des Katalog-Enums
- [ ] Keine Mutationen von Domänenobjekten
- [ ] Badge-IDs dokumentiert als „nie ändern“
- [ ] Keine unnötigen Erweiterungen (NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Badge geht verloren, weil `evaluate` nach erfolgreicher Buchung fehlschlägt | L | M | Auswertung auch bei bereits vorhandener Buchung; `evaluateAll` beim Start |
| Startbuchung vom Küchenboard vergibt viele Badges auf einmal | H | L | Gewollt; App (2b) zeigt mehrere neue Badges gesammelt an |
| Serien wirken unfair, weil Abnahmetag statt Erledigungstag zählt | M | L | Dokumentiert; im Testbetrieb beobachten, ggf. später auf Meldezeitpunkt umstellen (neues Ereignisfeld) |
| Katalog-ID wird versehentlich umbenannt | L | M | Test mit fester ID-Liste; Kommentar im Enum und in der Migration |
| Volle Journal-Lesung je Auswertung wird langsam | L | L | Familienvolumen (≪ 10 000 Buchungen); bei Bedarf Aggregat-Query |
| Emoji-Darstellung auf älteren Android-Geräten | L | L | Phase 2b prüft auf dem Testhandy; notfalls App-seitiges Mapping per `id` |

## Notes
- Phase 1 ist laut PRD noch `in-progress` (Geräte-Abnahme). Phase 2 hängt fachlich nur vom fertigen Backend/App-Stand aus
  Phase 1 ab, der gemergt ist; die Geräte-Abnahme von Phase 1 kann parallel abgeschlossen werden.
- Phase 3 (Belohnungen) und 4 (Avatar) ändern ebenfalls `api/openapi.yaml` und `GamificationModule.kt`/`Application.kt` →
  nicht parallel in getrennten Sessions ohne abgestimmte Merge-Reihenfolge (PRD „Parallelism Notes“).
- Spätere Badges (z. B. „erstes Item gekauft“) erweitern `BadgeStats` und das Enum; `evaluateAll` beim Start vergibt sie
  nachträglich.
- Danach: Plan für Phase 2b (App) mit `/ecc:prp-plan docs/01-requirements/gamification.prd.md`.
