# Plan: Echte Belohnungen – Backend/API (GAME Phase 3a)

## Summary
Eltern legen echte Belohnungen mit Punktepreis an, ändern sie und deaktivieren sie. Kinder wählen eine Belohnung als
Sparziel, lösen sie mit ihrem Guthaben ein (sofortige Abbuchung, Status „wartet auf Eltern“), und Eltern markieren die
Einlösung als erfüllt oder lehnen sie mit Begründung ab (Rückbuchung). Alles bleibt im Modul `gamification`; Abbuchung
und Rückbuchung sind normale Journal-Buchungen (`REWARD`, `REWARD_REFUND`), die es im SQL-`CHECK` und im OpenAPI-Enum
schon gibt. Die App folgt in Phase 3b.

## User Story
Als Kind möchte ich auf eine echte Belohnung sparen und sie mit meinen Punkten einlösen, damit meine Punkte einen echten
Wert haben; als Elternteil möchte ich Belohnungen ausloben und Einlösungen bestätigen oder ablehnen.

## Problem → Solution
Punkte lassen sich nur sammeln und vergleichen; `REWARD`/`REWARD_REFUND` sind vorgesehen, werden aber nie gebucht →
Belohnungskatalog, Sparziel je Kind und Einlösungen mit atomarer Abbuchung/Rückbuchung, über die REST-API nutzbar.

## Metadata
- **Complexity**: Large (≈ 25 Dateien, folgt aber durchgehend bestehenden Mustern)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: 3 „Echte Belohnungen“ → Teil 3a Backend/API (3b App folgt)
- **Requirements**: REQ-GAME-040, -041 (nur Server-Anteil), -042, -043
- **Estimated Files**: ≈ 12 neu, ≈ 10 geändert

---

## UX Design

N/A — Backend/API. Die Bildschirme (Eltern-Verwaltung unter „Familie“, Kind-Ansicht unter „Ich“, Sparziel auf „Ich“ und
„Heute“, Hinweise) baut Phase 3b auf diesen Endpunkten.

### Interaction Changes (API)
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| `GET /gamification/rewards` | – | Belohnungen lesen (alle Rollen) | `includeArchived=true` zeigt auch deaktivierte |
| `POST /gamification/rewards` | – | Belohnung anlegen (Eltern) | 201 |
| `PUT /gamification/rewards/{rewardId}` | – | Belohnung ändern (Eltern) | deaktivierte → 409 |
| `DELETE /gamification/rewards/{rewardId}` | – | Belohnung deaktivieren (Eltern) | 204; laufende Einlösungen bleiben |
| `POST /gamification/rewards/{rewardId}/redemptions` | – | Einlösen (Kind, eigenes Guthaben) | 201; zu wenig Guthaben / deaktiviert → 409 |
| `GET /gamification/redemptions?status=&limit=` | – | Einlösungen (Kind: eigene, Eltern: alle) | neueste zuerst; App fragt `status=PENDING` für den Eltern-Hinweis ab |
| `POST /gamification/redemptions/{redemptionId}/fulfillment` | – | Erfüllt (Eltern) | nur aus `PENDING`, sonst 409 |
| `POST /gamification/redemptions/{redemptionId}/rejection` | – | Ablehnen mit Begründung (Eltern) | Pflicht-Begründung, Rückbuchung |
| `GET /gamification/members/{memberId}/savings-goal` | – | Sparziel mit Fortschritt (Kind eigenes, Eltern alle) | ohne Ziel: `reward = null` |
| `PUT /gamification/members/{memberId}/savings-goal` | – | Sparziel wählen (nur das Kind selbst) | nur aktive Belohnung |
| `DELETE /gamification/members/{memberId}/savings-goal` | – | Sparziel entfernen (nur das Kind selbst) | 204, idempotent |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/01-requirements/gamification.prd.md` | 202–233 | REQ-GAME-040–043 mit Abnahmekriterien |
| P0 | `docs/03-design/gamification.md` | all | Journal-Regeln (`EntryKind`, Gesammelt/Guthaben, `source_key`), Sichtbarkeit, Badges |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | all | `EntryKind`, `totalsFrom`, Konstanten, Source-Key-Funktionen |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepository.kt` | all | `dbQuery`, `insertIgnore`, Mapping; Einlösung schreibt in dieselbe Tabelle |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/` (`CatalogTask.kt`, `CatalogService.kt`, `CatalogRepository.kt`) | all | Vorlage für Belohnungskatalog: Validierung, Archivieren, 404/409 |
| P0 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt` | 77–119 | Bedingter Statuswechsel (`UPDATE … WHERE status IN`) + Folgeschreiben in **derselben** Transaktion, `TransitionOutcome` |
| P0 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt` | all | Routen, `requireParent`, `uuidParameter`, Envelope |
| P1 | `backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt` | 50–133 | Katalog-Routen, `includeArchived`, Body-Parsing |
| P1 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt` | all | Service-Stil: `Actor`, Sichtbarkeit, `requireChild`, Logging |
| P1 | `backend/core/src/main/kotlin/de/familyplanner/core/persistence/DatabaseFactory.kt` | 38–52 | Pool-Größe 1 + `SERIALIZABLE` → eine `dbQuery` ist atomar und serialisiert |
| P1 | `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/*` | all | Fixture und In-Memory-Repos erweitern |
| P1 | `backend/app/src/test/kotlin/de/familyplanner/app/GamificationApiTest.kt` + `ApiTestSupport.kt` | all | API-Test mit Vertragsprüfung (`call` validiert gegen `openapi.yaml`), `addPairedChild` |
| P2 | `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | 95–135 | Koin-Module und Routen verdrahten |
| P2 | `api/openapi.yaml` | 934–1040, 1843–1935, 1975–2145 | Pfad-/Schema-/Envelope-Stil, `Conflict`-Response, Parameter |

## External Documentation

No external research needed — feature uses established internal patterns (Exposed v1 DSL, Ktor, Flyway wie in Phase 1a/2a).

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogTask.kt:6-23
const val TASK_TITLE_MAX = 40
const val TASK_EMOJI_MAX = 16

@JvmInline
value class CatalogTaskId(
    val value: String,
)

data class CatalogTask(
    val id: CatalogTaskId,
    val title: String,
    val emoji: String?,
    val points: Int,
    val archived: Boolean,
)
```
Pakete nach Fachbegriff: `de.familyplanner.gamification.rewards` (Katalog, Sparziel, Einlösung, Services, Repositories),
Persistenz in `…gamification.persistence`, Web in `…gamification.web`. Englische Code-Namen, deutsche Fehlermeldungen.

### VALIDATION
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogTask.kt:32-41
fun validated(input: CatalogTaskInput): CatalogTaskInput {
    val title = validatedName(input.title, "Titel", TASK_TITLE_MAX)
    val emoji = input.emoji?.trim()?.takeIf { it.isNotEmpty() }
    if (emoji != null && emoji.length > TASK_EMOJI_MAX) throw AppException.Validation("Symbol ist zu lang")
    if (input.points !in TASK_POINTS_MIN..TASK_POINTS_MAX) {
        throw AppException.Validation("Punkte müssen zwischen $TASK_POINTS_MIN und $TASK_POINTS_MAX liegen")
    }
    return CatalogTaskInput(title, emoji, input.points)
}
```

### ERROR_HANDLING
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/catalog/CatalogService.kt:26-40
suspend fun update(id: CatalogTaskId, input: CatalogTaskInput): CatalogTask {
    val existing = catalog.findById(id) ?: throw AppException.NotFound(TASK_NOT_FOUND)
    val task = toTask(id, validated(input))
    if (existing.archived || !catalog.update(task, clock.instant())) throw AppException.Conflict(ARCHIVED)
    ...
}
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/board/BoardService.kt (remove)
when (cards.remove(id)) {
    TransitionOutcome.CHANGED -> log.info("Task card removed: {}", id.value)
    TransitionOutcome.NOT_FOUND -> throw AppException.NotFound(CARD_NOT_FOUND)
    TransitionOutcome.WRONG_STATUS -> throw AppException.Conflict("Erledigte Aufgaben können nicht entfernt werden")
}
```
`AppException.Validation` → 400, `Forbidden` → 403 (Standardtext „Nur für Eltern“), `NotFound` → 404, `Conflict` → 409.
Reihenfolge wie im Bestand: **erst Rolle (403), dann Eingabe (400), dann Existenz (404), dann Zustand (409)** – mit
Ausnahme von Zugriffen auf Mitglieder: dort erst Existenz, dann Recht (`requireChild` vor dem Sichtbarkeits-Check, siehe
`AccountService.account`).

### LOGGING_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt:29,57
private val log = LoggerFactory.getLogger(AccountService::class.java)
log.info("Manual points booked: {} to {}", amount, memberId.value)
```
Je schreibender Aktion eine `info`-Zeile mit IDs, ohne Freitext (keine Begründungen/Titel loggen).

### REPOSITORY_PATTERN (bedingter Wechsel + Folgeschreiben in einer Transaktion)
```kotlin
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/persistence/ExposedTaskCardRepository.kt:77-109
override suspend fun transition(...): TransitionOutcome =
    dbQuery(db) {
        val changed =
            TaskCardsTable.update(
                { (TaskCardsTable.id eq id.value) and (TaskCardsTable.status inList transition.from().map { it.name }) },
            ) { ... }
        if (changed == 0) return@dbQuery if (row(id) != null) TransitionOutcome.WRONG_STATUS else TransitionOutcome.NOT_FOUND
        if (transition == TaskTransition.ACCEPT) recordAccepted(id, by, at)
        TransitionOutcome.CHANGED
    }
```
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedLedgerRepository.kt:23-38
LedgerTable.insertIgnore {
    it[id] = entry.id.value
    it[memberId] = entry.memberId.value
    it[kind] = entry.kind.name
    ...
}
```
`dbQuery` öffnet genau eine Transaktion; Pool-Größe 1 + `TRANSACTION_SERIALIZABLE` (`DatabaseFactory`) serialisiert
alle Transaktionen → „Guthaben prüfen + Einlösung + Abbuchung“ in **einer** `dbQuery` ist race-frei.

### SERVICE_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/account/AccountService.kt:22-43
class AccountService(
    private val members: MemberRepository,
    private val ledger: LedgerRepository,
    private val badges: BadgeService,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun account(actor: Actor, memberId: MemberId, limit: Int): Account {
        if (limit !in 1..ENTRIES_LIMIT_MAX) throw AppException.Validation("Ungültiger Wert für 'limit'")
        members.requireChild(memberId)
        if (!actor.isParent && actor.id != memberId) throw AppException.Forbidden("Nur eigenes Punktekonto")
        ...
    }
}
```

### ROUTE_PATTERN
```kotlin
// SOURCE: backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/GamificationRoutes.kt:51-56
post("/api/v1/gamification/members/{memberId}/entries") {
    val parent = call.requireParent().toActor()
    val request = call.receive<ManualEntryRequest>()
    val entry = accounts.book(parent, call.targetMemberId(), request.amount, request.reason)
    call.respond(HttpStatusCode.Created, LedgerEntryResponse(success = true, data = entry.toApi()))
}
// SOURCE: backend/tasks/src/main/kotlin/de/familyplanner/tasks/web/TaskRoutes.kt:66-70,124-127
delete("/api/v1/tasks/catalog/{catalogTaskId}") {
    call.requireParent()
    catalog.archive(CatalogTaskId(call.uuidParameter("catalogTaskId")))
    call.respond(HttpStatusCode.NoContent)
}
private fun ApplicationCall.includeArchived(): Boolean {
    val raw = request.queryParameters["includeArchived"] ?: return false
    return raw.toBooleanStrictOrNull() ?: throw AppException.Validation("Ungültiger Wert für 'includeArchived'")
}
```

### TEST_STRUCTURE
```kotlin
// SOURCE: backend/gamification/src/test/kotlin/de/familyplanner/gamification/account/AccountServiceTest.kt:17-44
class AccountServiceTest {
    private val fixture = GamificationFixture()

    @Test
    fun `REQ-GAME-012 child sees own collected, balance and entries newest first`() =
        runTest {
            val child = fixture.child()
            credit(child, EntryKind.TASK, 2)
            ...
            assertEquals(4, account.totals.balance)
        }
}
```
Integrationstests der Repositories wie `ExposedBadgeRepositoryTest` (`@TempDir`, `DatabaseFactory.create(dir.resolve("t.db"), MutableClock(now))`,
Kind über `ExposedMemberRepository(db).create(aMember(role = Role.CHILD))`). API-Tests wie `GamificationApiTest`
(`testApplication { start() … }`, `client.call(...)` prüft Request und Response gegen `openapi.yaml`, `setUpFamily`,
`addPairedChild` für ein Geschwisterkind).

---

## Design Decisions (in dieser Phase getroffen)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Preis | ganze Punkte 1–10 000 (`REWARD_PRICE_MAX`) | „Freizeitpark 200“; Obergrenze gegen Tippfehler |
| Titel / Symbol | Titel 1–40 Zeichen, Symbol optional ≤ 16 (wie Katalogaufgaben) | einheitliche Eingabegrenzen, App kann Formular wiederverwenden |
| Deaktivieren | `DELETE` setzt `archived_at`, kein Reaktivieren, keine Änderung danach (409) | wie Aufgabenkatalog (YAGNI); neu anlegen geht immer |
| Sichtbarkeit Katalog | alle Rollen lesen; `includeArchived=true` für alle erlaubt | Sparziel eines Kindes kann auf eine deaktivierte Belohnung zeigen |
| Einlösung | eine Transaktion: Belohnung aktiv? → Guthaben ≥ Preis? → `game_redemptions` (`PENDING`) + Buchung `REWARD` (−Preis, `source_key = reward:<redemptionId>`) → Sparziel löschen, wenn es diese Belohnung war | REQ-GAME-042; kein Doppel-Einlösen unter Last; Ziel erreicht |
| Wer löst ein | nur ein Kind für sich selbst (Eltern → 403 „Nur Kinder lösen Belohnungen ein“) | PRD: „mit **meinem** Guthaben“ |
| Zu wenig Guthaben | 409 „Nicht genug Guthaben“ (Zustandskonflikt, keine ungültige Eingabe) | App zeigt Hinweis; Guthaben kann sich zwischen Anzeige und Tippen ändern |
| Snapshot | Einlösung speichert `title`, `emoji`, `price` zum Zeitpunkt der Einlösung | Spätere Preis-/Titeländerung ändert laufende Einlösungen nicht |
| Erfüllt | `PENDING → FULFILLED`, bedingtes `UPDATE`, `decided_by`, `decided_at` | REQ-GAME-043 |
| Ablehnen | `PENDING → REJECTED`, Begründung Pflicht 1–200, in **derselben** Transaktion Buchung `REWARD_REFUND` (+Preis, `source_key = reward-refund:<redemptionId>`, `note` = Begründung) | REQ-GAME-043; Rückbuchung genau einmal |
| Rückziehen durch das Kind | nicht vorgesehen | nicht im PRD |
| Sparziel | Tabelle `game_savings_goals` (PK `member_id`), ein Ziel je Kind; setzen/entfernen nur das Kind selbst; lesen Kind (eigenes) und Eltern; Fortschritt `current = clamp(balance, 0, price)`, `target = price`, `redeemable = active && balance ≥ price` | REQ-GAME-041; Eltern sehen den Fortschritt im Punktekonto |
| Deaktivierte Ziel-Belohnung | Ziel bleibt bestehen, `reward.archived = true`, `redeemable = false` | Kind sieht, warum es nicht mehr geht, und wählt neu |
| Eltern-Hinweis bei Einlösung | **kein** Push im Backend; App fragt `GET /redemptions?status=PENDING` beim Status-Abgleich ab (Muster ADR-0013) | Phase 3b; kein neues ADR nötig |
| Badges | Ab-/Rückbuchungen zählen nicht für „Gesammelt“ → **kein** `badges.evaluate` | `EntryKind.countsAsCollected = false` |
| Gesammelt / Rangliste | unverändert (Ausgaben zählen nicht) | Design Phase 1 |
| Verlauf | Ab-/Rückbuchungen erscheinen im Punktekonto; Titel `Belohnung: <title>` bzw. `Zurückgebucht: <title>` | Kind versteht den Verlauf |

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `api/openapi.yaml` | UPDATE | Version 0.8.0 → 0.9.0; Pfade + Schemas + Parameter (API-first, ADR-0004) |
| `backend/gamification/src/main/resources/db/migration/V12__gamification_rewards.sql` | CREATE | Tabellen `game_rewards`, `game_redemptions`, `game_savings_goals` |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/Reward.kt` | CREATE | `RewardId`, `Reward`, `RewardInput`, `validated`, Grenzen |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/Redemption.kt` | CREATE | `RedemptionId`, `RedemptionStatus`, `Redemption`, `RedeemOutcome`, `DecisionOutcome`, Source-Keys |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/SavingsGoal.kt` | CREATE | `SavingsGoal` (+ reine Funktion `savingsGoalOf`) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RewardRepository.kt` | CREATE | Katalog + Sparziel |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RedemptionRepository.kt` | CREATE | atomares Einlösen/Entscheiden, Auflisten |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RewardService.kt` | CREATE | Katalog + Sparziel (REQ-GAME-040/041) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/rewards/RedemptionService.kt` | CREATE | Einlösen, Liste, Erfüllt/Ablehnen (REQ-GAME-042/043) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/RewardTables.kt` | CREATE | Exposed-Tabellen (eigene Datei, `GamificationTables.kt` bleibt klein) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedRewardRepository.kt` | CREATE | Katalog + Sparziel |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepository.kt` | CREATE | Einlösen/Entscheiden inkl. Journal-Buchung in derselben Transaktion |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/RewardRoutes.kt` | CREATE | eigene Routen-Datei (detekt `TooManyFunctions`) |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/web/RewardApiMappers.kt` | CREATE | Domäne ↔ API-Modelle |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/ledger/LedgerEntry.kt` | UPDATE | `rewardSourceKey`, `rewardRefundSourceKey` neben den bestehenden Source-Keys |
| `backend/gamification/src/main/kotlin/de/familyplanner/gamification/GamificationModule.kt` | UPDATE | Koin: neue Repositories + Services |
| `backend/app/src/main/kotlin/de/familyplanner/app/Application.kt` | UPDATE | `rewardRoutes(get(), get())` einhängen |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/InMemoryGamificationRepositories.kt` | UPDATE | `InMemoryRewardRepository`, `InMemoryRedemptionRepository` (bucht in `InMemoryLedgerRepository`) |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/fakes/GamificationFixture.kt` | UPDATE | `rewards`, `redemptions` Services |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/rewards/RewardTest.kt` | CREATE | Validierung, `savingsGoalOf` |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/rewards/RewardServiceTest.kt` | CREATE | REQ-GAME-040/041 |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/rewards/RedemptionServiceTest.kt` | CREATE | REQ-GAME-042/043 |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/ExposedRewardRepositoryTest.kt` | CREATE | Integration Katalog/Ziel |
| `backend/gamification/src/test/kotlin/de/familyplanner/gamification/persistence/ExposedRedemptionRepositoryTest.kt` | CREATE | Integration: Atomarität, Rückbuchung, `CHECK` |
| `backend/app/src/test/kotlin/de/familyplanner/app/RewardsApiTest.kt` | CREATE | API inkl. Vertragsprüfung (eigene Datei, `GamificationApiTest` hat schon 284 Zeilen) |
| `docs/03-design/gamification.md` | UPDATE | Abschnitt „Belohnungen (Phase 3a)“ |
| `docs/04-verification/traceability.md` | UPDATE | Zeilen REQ-GAME-040–043 |
| `docs/01-requirements/gamification.prd.md` | UPDATE | Phase 3: Bericht/Status nach Umsetzung |

## NOT Building

- App-Bildschirme, Hinweise/Benachrichtigungen, lokaler Stand (Phase 3b)
- Shop, Avatar-Items und der Hinweis „Kauf drückt Guthaben unter das Sparziel“ (REQ-GAME-041 Kriterium 2 → Phase 4, braucht den Shop)
- Reaktivieren deaktivierter Belohnungen, Löschen von Belohnungen
- Zurückziehen einer Einlösung durch das Kind; Teil-Einlösung; Mengen/Lagerbestand; Einlösung „für“ ein anderes Kind
- Mehrere Sparziele, getrennte Spartöpfe, reserviertes Guthaben (PRD „Won't“)
- Push vom Server (ntfy/Firebase) – Hinweise kommen über den App-Abgleich (ADR-0013)
- Domänenereignisse für Einlösungen (alles bleibt im Modul `gamification`, kein anderes Modul hört zu)

---

## Step-by-Step Tasks

### Task 1: OpenAPI-Vertrag (API-first)
- **ACTION**: `api/openapi.yaml` erweitern, `info.version` → `0.9.0`.
- **IMPLEMENT**:
  - Parameter: `RewardId` (`rewardId`, path, uuid), `RedemptionId` (`redemptionId`, path, uuid), `RedemptionStatusFilter` (`status`, query, optional, `$ref RedemptionStatus`). `IncludeArchived` und `HistoryLimit` wiederverwenden.
  - Schemas:
    - `Reward` `{id uuid, title 1–40, emoji? (nullable, maxLength 16), price int 1–10000, archived bool}` – required `id,title,price,archived`
    - `RewardInput` `{title 1–40, emoji? nullable maxLength 16, price 1–10000}` – required `title,price`
    - `RedemptionStatus` enum `[PENDING, FULFILLED, REJECTED]`
    - `Redemption` `{id, memberId, rewardId, title, emoji? nullable, price, status, rejectionReason? nullable maxLength 200, requestedAt date-time, decidedAt? date-time nullable}` – required `id,memberId,rewardId,title,price,status,requestedAt`
    - `RejectRedemptionRequest` `{reason string 1–200}` – required `reason`
    - `SavingsGoalInput` `{rewardId uuid}` – required
    - `SavingsGoal` `{memberId, reward: allOf Reward nullable, balance int, progress: allOf BadgeProgress nullable (current/target), redeemable bool}` – required `memberId, balance, redeemable`; `description` an `progress`: „current = Guthaben, auf 0..target begrenzt; null ohne Ziel“
    - Envelopes wie `CatalogTaskResponse`/`CatalogTaskListResponse`: `RewardResponse`, `RewardListResponse`, `RedemptionResponse`, `RedemptionListResponse`, `SavingsGoalResponse`
  - Pfade (Tag `gamification`, Responses inkl. `401`, `429`; `403`/`404`/`409`/`400` wie unten):
    - `/api/v1/gamification/rewards`: `get` `listRewards` (`IncludeArchived`) → 200 `RewardListResponse` (400,401,429); `post` `createReward` (Eltern) → 201 `RewardResponse` (400,401,403,429)
    - `/api/v1/gamification/rewards/{rewardId}`: `put` `updateReward` → 200 (400,401,403,404,409,429); `delete` `archiveReward` → 204 (400,401,403,404,429), description „Laufende Einlösungen bleiben; Sparziele bleiben, sind aber nicht mehr einlösbar.“
    - `/api/v1/gamification/rewards/{rewardId}/redemptions`: `post` `redeemReward` (Kind) → 201 `RedemptionResponse` (400,401,403,404,409,429), description „Bucht den Preis sofort ab; 409 bei zu wenig Guthaben oder deaktivierter Belohnung.“ – **kein** Request-Body
    - `/api/v1/gamification/redemptions`: `get` `listRedemptions` (`RedemptionStatusFilter`, `HistoryLimit`) → 200 `RedemptionListResponse` (400,401,429), description „Kind: eigene; Eltern: alle. Neueste zuerst.“
    - `/api/v1/gamification/redemptions/{redemptionId}/fulfillment`: `post` `fulfillRedemption` (Eltern) → 200 `RedemptionResponse` (400,401,403,404,409,429)
    - `/api/v1/gamification/redemptions/{redemptionId}/rejection`: `post` `rejectRedemption` (Eltern, Body `RejectRedemptionRequest` required) → 200 (400,401,403,404,409,429)
    - `/api/v1/gamification/members/{memberId}/savings-goal`: `get` `getSavingsGoal` → 200 `SavingsGoalResponse` (400,401,403,404,429); `put` `setSavingsGoal` (Body `SavingsGoalInput`) → 200 (400,401,403,404,409,429); `delete` `clearSavingsGoal` → 204 (400,401,403,404,429)
- **MIRROR**: Pfade `/api/v1/tasks/catalog*` und `/api/v1/tasks/cards/{cardId}/rejection` (Zeilen 695–932), Schemas `CatalogTask*`, `RejectTaskRequest`, `BadgeProgress`.
- **GOTCHA**:
  - Liste im Schema heißt `data` (Array) wie `CatalogTaskListResponse`; **nicht** `entries` nennen (Generator benennt in `propertyEntries` um, siehe Design-Doc).
  - `BadgeProgress` für den Sparziel-Fortschritt wiederverwenden (gleiche Form) – sonst entsteht ein zweites gleiches Modell.
  - Der Android-Client generiert aus derselben Datei → nach dem Ändern muss `cd android && ./gradlew assembleDebug` grün bleiben.
- **VALIDATE**: `cd backend && ./gradlew :app:compileKotlin` (Server-Modelle generiert, neue Klassen `Reward`, `RewardInput`, `Redemption`, `RedemptionStatus`, `SavingsGoal`, `*Response` vorhanden).

### Task 2: Migration V12
- **ACTION**: `V12__gamification_rewards.sql` anlegen.
- **IMPLEMENT**:
```sql
-- GAME phase 3: real rewards. Deactivated rewards keep their row (archived_at) so redemptions and goals stay readable.
CREATE TABLE game_rewards (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  emoji TEXT,
  price INTEGER NOT NULL CHECK (price BETWEEN 1 AND 10000),
  archived_at INTEGER,
  created_by TEXT REFERENCES members(id),
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

-- Redemption snapshot (title/emoji/price at the time of redeeming). The debit is a REWARD ledger entry with
-- source_key reward:<id>; a rejection books REWARD_REFUND with source_key reward-refund:<id> in the same transaction.
CREATE TABLE game_redemptions (
  seq INTEGER PRIMARY KEY AUTOINCREMENT,
  id TEXT NOT NULL UNIQUE,
  member_id TEXT NOT NULL REFERENCES members(id) ON DELETE CASCADE,
  reward_id TEXT NOT NULL REFERENCES game_rewards(id),
  title TEXT NOT NULL,
  emoji TEXT,
  price INTEGER NOT NULL CHECK (price > 0),
  status TEXT NOT NULL CHECK (status IN ('PENDING','FULFILLED','REJECTED')),
  rejection_reason TEXT,
  requested_at INTEGER NOT NULL,
  decided_by TEXT REFERENCES members(id),
  decided_at INTEGER
);
CREATE INDEX idx_game_redemptions_status ON game_redemptions(status, requested_at);
CREATE INDEX idx_game_redemptions_member ON game_redemptions(member_id, requested_at);

-- At most one savings goal per child.
CREATE TABLE game_savings_goals (
  member_id TEXT PRIMARY KEY REFERENCES members(id) ON DELETE CASCADE,
  reward_id TEXT NOT NULL REFERENCES game_rewards(id),
  set_at INTEGER NOT NULL
);
```
- **MIRROR**: `V10__gamification.sql`, `V11__gamification_badges.sql` (Kommentarstil, `INTEGER` Epoch-Millis, FK auf `members`).
- **GOTCHA**: Versionen sind modulübergreifend global – höchste ist `V11` (gamification); `V12` ist frei. Migrationen nie nachträglich ändern. `game_rewards` wird nie gelöscht → FKs ohne `ON DELETE`.
- **VALIDATE**: Integrationstest aus Task 9 startet `DatabaseFactory.create` → Flyway läuft V1–V12.

### Task 3: Domäne `rewards`
- **ACTION**: `Reward.kt`, `Redemption.kt`, `SavingsGoal.kt` im Paket `de.familyplanner.gamification.rewards`; Source-Keys in `ledger/LedgerEntry.kt`.
- **IMPLEMENT**:
```kotlin
// Reward.kt
const val REWARD_TITLE_MAX = 40
const val REWARD_EMOJI_MAX = 16
const val REWARD_PRICE_MIN = 1
const val REWARD_PRICE_MAX = 10_000

@JvmInline value class RewardId(val value: String)

/** A real reward parents offer for points (REQ-GAME-040); [archived] = deactivated, no longer redeemable. */
data class Reward(val id: RewardId, val title: String, val emoji: String?, val price: Int, val archived: Boolean)

data class RewardInput(val title: String, val emoji: String?, val price: Int)

/** Trims the title, turns a blank emoji into `null` and checks all limits. */
fun validated(input: RewardInput): RewardInput  // wie CatalogTask.validated, Meldung "Preis muss zwischen 1 und 10000 liegen"

// Redemption.kt
@JvmInline value class RedemptionId(val value: String)
enum class RedemptionStatus { PENDING, FULFILLED, REJECTED }

/** Snapshot of the reward at redemption time; the debit is the ledger entry with [rewardSourceKey]. */
data class Redemption(
    val id: RedemptionId, val memberId: MemberId, val rewardId: RewardId,
    val title: String, val emoji: String?, val price: Int,
    val status: RedemptionStatus, val rejectionReason: String?,
    val requestedAt: Instant, val decidedAt: Instant?,
)

/** Result of the atomic redeem in [RedemptionRepository.redeem]. */
sealed interface RedeemOutcome {
    data class Redeemed(val redemption: Redemption) : RedeemOutcome
    data object RewardNotFound : RedeemOutcome
    data object RewardArchived : RedeemOutcome
    data class InsufficientBalance(val balance: Int, val price: Int) : RedeemOutcome
}

enum class DecisionOutcome { CHANGED, NOT_FOUND, NOT_PENDING }

const val REDEMPTIONS_LIMIT_DEFAULT = 50   // = ENTRIES_LIMIT_DEFAULT, eigener Name für Klarheit
const val REJECTION_REASON_MAX = 200

// SavingsGoal.kt
/** Goal of one child with progress; [reward] null = no goal (REQ-GAME-041). */
data class SavingsGoal(val memberId: MemberId, val reward: Reward?, val balance: Int) {
    val progress: Int? get() = reward?.let { balance.coerceIn(0, it.price) }
    val redeemable: Boolean get() = reward != null && !reward.archived && balance >= reward.price
}

// ledger/LedgerEntry.kt (neben taskSourceKey/cookingSourceKey)
fun rewardSourceKey(redemptionId: String) = "reward:$redemptionId"
fun rewardRefundSourceKey(redemptionId: String) = "reward-refund:$redemptionId"
```
- **MIRROR**: NAMING_CONVENTION, VALIDATION.
- **GOTCHA**: `validated` heißt im `tasks`-Modul gleich; hier ist es ein anderes Paket – kein Konflikt, aber in Tests eindeutig importieren. `REDEMPTIONS_LIMIT_MAX` = `ENTRIES_LIMIT_MAX` (100) wiederverwenden statt neu zu definieren. Kein Mutieren: `SavingsGoal` berechnet abgeleitete Werte.
- **VALIDATE**: `RewardTest` (Task 8) grün.

### Task 4: Repository-Interfaces
- **ACTION**: `RewardRepository.kt`, `RedemptionRepository.kt`.
- **IMPLEMENT**:
```kotlin
interface RewardRepository {
    /** Sorted by price, then title (case-insensitive). */
    suspend fun findAll(includeArchived: Boolean): List<Reward>
    suspend fun findById(id: RewardId): Reward?
    suspend fun create(reward: Reward, by: MemberId, at: Instant)
    /** Returns `false` if the reward is unknown or archived. */
    suspend fun update(reward: Reward, at: Instant): Boolean
    /** Archiving twice is fine. Returns `false` only if the reward is unknown. */
    suspend fun archive(id: RewardId, at: Instant): Boolean

    suspend fun goalOf(memberId: MemberId): RewardId?
    suspend fun setGoal(memberId: MemberId, rewardId: RewardId, at: Instant)   // upsert
    suspend fun clearGoal(memberId: MemberId)
}

interface RedemptionRepository {
    /**
     * In one transaction: reward exists and is active, balance >= price, inserts the PENDING redemption, books the
     * REWARD debit (title "Belohnung: <title>", source_key reward:<id>) and clears the child's goal if it is this reward.
     */
    suspend fun redeem(id: RedemptionId, memberId: MemberId, rewardId: RewardId, debitEntryId: String, at: Instant): RedeemOutcome

    suspend fun findById(id: RedemptionId): Redemption?

    /** Newest first (requestedAt, then insertion order); [memberId] null = all children, [status] null = all. */
    suspend fun find(memberId: MemberId?, status: RedemptionStatus?, limit: Int): List<Redemption>

    /** PENDING -> FULFILLED. */
    suspend fun fulfill(id: RedemptionId, by: MemberId, at: Instant): DecisionOutcome

    /** PENDING -> REJECTED and, in the same transaction, the REWARD_REFUND (+price, note = reason). */
    suspend fun reject(id: RedemptionId, by: MemberId, reason: String, at: Instant, refundEntryId: String): DecisionOutcome
}
```
- **MIRROR**: `CatalogRepository`, `TaskCardRepository.transition` (KDoc beschreibt Atomarität).
- **GOTCHA**: Die IDs der Buchungen (`debitEntryId`, `refundEntryId`) erzeugt der Service (`newId`), damit Tests deterministisch bleiben – das Repository erzeugt keine IDs.
- **VALIDATE**: kompiliert.

### Task 5: Exposed-Tabellen + Repositories
- **ACTION**: `RewardTables.kt`, `ExposedRewardRepository.kt`, `ExposedRedemptionRepository.kt`.
- **IMPLEMENT**:
  - Tabellen `RewardTable("game_rewards")`, `RedemptionTable("game_redemptions")` (PK `seq` autoIncrement wie `LedgerTable`), `SavingsGoalTable("game_savings_goals")` (PK `memberId`). Längen-Konstanten privat in der Datei (`ID_LENGTH = 36`, `TITLE_LENGTH = 40`, `EMOJI_LENGTH = 16`, `STATUS_LENGTH = 10`, `REASON_LENGTH = 200`).
  - `ExposedRewardRepository`: 1:1 nach `ExposedCatalogRepository` (Sortierung `sortedWith(compareBy<Reward> { it.price }.thenBy { it.title.lowercase() })`); `setGoal` per `SavingsGoalTable.upsert` (Muster `ExposedGameSettingsRepository.save`), `clearGoal` per `deleteWhere`.
  - `ExposedRedemptionRepository.redeem` – **eine** `dbQuery`:
    1. Reward-Zeile lesen → `null` ⇒ `RewardNotFound`; `archivedAt != null` ⇒ `RewardArchived`.
    2. Guthaben: `LedgerTable.selectAll().where { memberId eq … }.sumOf { it[LedgerTable.amount] }` (Balance = Summe aller Buchungen, siehe `totalsFrom`). `< price` ⇒ `InsufficientBalance(balance, price)`.
    3. `RedemptionTable.insert { … status = PENDING, requestedAt = at }`.
    4. `LedgerTable.insert { kind = REWARD, amount = -price, title = "Belohnung: $title", sourceKey = rewardSourceKey(id), createdBy = memberId, bookedAt = at }` – **`insert`, nicht `insertIgnore`** (ein Fehler muss die Transaktion zurückrollen, nicht still verschluckt werden).
    5. `SavingsGoalTable.deleteWhere { (memberId eq …) and (rewardId eq …) }`.
    6. `Redeemed(redemption)`.
  - `fulfill`/`reject`: `RedemptionTable.update({ (id eq …) and (status eq PENDING) }) { status, decidedBy, decidedAt, rejectionReason }`; `0` geänderte Zeilen ⇒ Zeile vorhanden? `NOT_PENDING` : `NOT_FOUND` (Muster `transition`). Bei `reject` danach `LedgerTable.insert { kind = REWARD_REFUND, amount = +price, title = "Zurückgebucht: $title", note = reason, sourceKey = rewardRefundSourceKey(id), createdBy = by, bookedAt = at }` – Preis/Titel/Mitglied aus der Einlösungszeile.
  - `find`: Bedingungen kombinieren (`Op.TRUE` als Start oder `andWhere`), `orderBy(requestedAt DESC, seq DESC)`, `limit`.
- **MIRROR**: REPOSITORY_PATTERN; `ExposedLedgerRepository` für LedgerTable-Spalten.
- **GOTCHA**:
  - `LedgerTable` liegt im selben Paket `persistence` – direkt nutzbar; **nicht** `LedgerRepository.append` aus dem Repository aufrufen (eigene Transaktion → nicht mehr atomar).
  - `insertIgnore` würde `CHECK`-/FK-Verletzungen verschlucken (siehe KDoc in `ExposedLedgerRepository`) → hier bewusst `insert`.
  - Exposed v1-Importe: `org.jetbrains.exposed.v1.core.{eq,and,SortOrder}`, `org.jetbrains.exposed.v1.jdbc.{insert,update,selectAll,upsert,deleteWhere,andWhere}`.
  - detekt `LongMethod` (≤ 60 Zeilen) → `redeem` in private `JdbcTransaction.`-Helfer aufteilen (`balanceOf`, `insertDebit`), wie `recordAccepted` in `ExposedTaskCardRepository`.
- **VALIDATE**: Task 9 grün.

### Task 6: Services
- **ACTION**: `RewardService.kt`, `RedemptionService.kt`.
- **IMPLEMENT**:
```kotlin
class RewardService(
    private val members: MemberRepository,
    private val rewards: RewardRepository,
    private val ledger: LedgerRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun list(includeArchived: Boolean): List<Reward>
    suspend fun create(actor: Actor, input: RewardInput): Reward          // !isParent -> Forbidden(); validated; log "Reward created: {}"
    suspend fun update(actor: Actor, id: RewardId, input: RewardInput): Reward // Forbidden, validated, NotFound("Belohnung nicht gefunden"), Conflict("Die Belohnung ist deaktiviert")
    suspend fun archive(actor: Actor, id: RewardId)                       // Forbidden, NotFound
    suspend fun goal(actor: Actor, memberId: MemberId): SavingsGoal       // requireChild; !isParent && actor.id != memberId -> Forbidden("Nur eigenes Sparziel"); balance = ledger.totalsOf(memberId).balance
    suspend fun setGoal(actor: Actor, memberId: MemberId, rewardId: RewardId): SavingsGoal // requireChild; actor.id != memberId -> Forbidden("Nur das Kind selbst wählt sein Sparziel"); reward NotFound; archived -> Conflict
    suspend fun clearGoal(actor: Actor, memberId: MemberId)               // requireChild; nur das Kind selbst
}

class RedemptionService(
    private val members: MemberRepository,
    private val redemptions: RedemptionRepository,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun redeem(actor: Actor, rewardId: RewardId): Redemption
    //   actor.isParent -> Forbidden("Nur Kinder lösen Belohnungen ein")
    //   members.requireChild(actor.id)
    //   when (outcome) RewardNotFound -> NotFound; RewardArchived -> Conflict("Die Belohnung ist deaktiviert");
    //   InsufficientBalance -> Conflict("Nicht genug Guthaben: ${o.balance} von ${o.price} Punkten")
    //   log.info("Reward redeemed: {} by {}", redemption.id.value, actor.id.value)
    suspend fun list(actor: Actor, status: RedemptionStatus?, limit: Int): List<Redemption>
    //   limit !in 1..ENTRIES_LIMIT_MAX -> Validation("Ungültiger Wert für 'limit'"); Kind -> nur eigene (memberId = actor.id), Eltern -> null
    suspend fun fulfill(actor: Actor, id: RedemptionId): Redemption       // Forbidden, decide(...)
    suspend fun reject(actor: Actor, id: RedemptionId, reason: String): Redemption
    //   Forbidden; validatedName(reason, "Begründung", REJECTION_REASON_MAX); NOT_FOUND -> NotFound("Einlösung nicht gefunden"); NOT_PENDING -> Conflict("Die Einlösung ist bereits entschieden")
}
```
- **MIRROR**: SERVICE_PATTERN, ERROR_HANDLING; `requireChild` aus `de.familyplanner.gamification.requireChild`.
- **GOTCHA**:
  - Sparziel-Fortschritt nutzt **Guthaben** (`balance`), nicht „Gesammelt“.
  - `goal()` liest das Ziel und dann die Belohnung; zeigt das Ziel auf eine unbekannte ID (sollte per FK nicht vorkommen) → wie „kein Ziel“ behandeln und `warn` loggen, nicht werfen.
  - Kein `badges.evaluate` (Ausgaben zählen nicht für Badges).
  - Nach `fulfill`/`reject` die Einlösung frisch lesen (`findById`) und zurückgeben (Muster `BoardService.move`).
  - detekt `TooManyFunctions` (≤ 15): Services bleiben darunter; private Helfer zählen mit.
- **VALIDATE**: Task 8 grün.

### Task 7: Koin, Routen, Mapper, App
- **ACTION**: `GamificationModule.kt`, `RewardRoutes.kt`, `RewardApiMappers.kt`, `Application.kt`.
- **IMPLEMENT**:
  - Koin: `single<RewardRepository> { ExposedRewardRepository(get()) }`, `single<RedemptionRepository> { ExposedRedemptionRepository(get()) }`, `single { RewardService(get(), get(), get(), clock) }`, `single { RedemptionService(get(), get(), clock) }`.
  - `fun Route.rewardRoutes(rewards: RewardService, redemptions: RedemptionService)` in `authenticate(AUTH_DEVICE) { … }`, aufgeteilt in `private fun Route.catalogRoutes`, `redemptionRoutes`, `goalRoutes` (Muster `TaskRoutes`). Rollenprüfung im Service (`Actor`), Routen holen `call.principalOrThrow().toActor()`; Body per `call.receive<ApiRewardInput>()` (Alias-Import wie `ApiCatalogTaskInput`).
  - Query-Parsing: `includeArchived()` (wie `TaskRoutes`), `redemptionStatus()` → `RedemptionStatus.entries.firstOrNull { it.name == raw } ?: throw Validation("Ungültiger Wert für 'status'")`, `limit()` wie `entriesLimit()`.
  - Mapper: `Reward.toApi()`, `Redemption.toApi()` (`requestedAt.toString()`, `decidedAt?.toString()`, Status über `ApiRedemptionStatus.valueOf(status.name)`), `SavingsGoal.toApi()` mit `progress = reward?.let { r -> BadgeProgress(current = balance.coerceIn(0, r.price), target = r.price) }` (kein `!!`).
  - `Application.kt`: neben `gamificationRoutes(...)` → `rewardRoutes(get<RewardService>(), get<RedemptionService>())`.
- **MIRROR**: ROUTE_PATTERN, `GamificationApiMappers.kt`.
- **GOTCHA**: Ablehnen braucht einen **Pflicht**-Body (anders als `optionalReason` bei Aufgaben) → `call.receive<RejectRedemptionRequest>()`; fehlender/ungültiger Body ergibt über das bestehende Status-Pages-Mapping 400 – im API-Test prüfen. Kein `!!` (detekt).
- **VALIDATE**: `./gradlew :app:compileKotlin`, Task 10 grün.

### Task 8: Unit-Tests (TDD – vor Task 5/6 schreiben)
- **ACTION**: In-Memory-Fakes + Fixture erweitern, Tests anlegen.
- **IMPLEMENT**:
  - `InMemoryRewardRepository` (Liste + Map für Ziele), `InMemoryRedemptionRepository(ledger: InMemoryLedgerRepository, rewards: InMemoryRewardRepository)` – bucht Ab-/Rückbuchung über `ledger.append` mit Source-Keys und löscht das Ziel, damit Service-Tests das Guthaben über `fixture.accounts` / `fixture.ledger` prüfen können.
  - `GamificationFixture`: `rewardRepository`, `redemptionRepository`, `rewards = RewardService(members, rewardRepository, ledger, clock) { "reward-${++nextId}" }`, `redemptions = RedemptionService(members, redemptionRepository, clock) { "redemption-${++nextId}" }`.
  - `RewardTest`: `REQ-GAME-040 title is trimmed and blank emoji becomes null`, `REQ-GAME-040 price 0, 10001 or title over 40 is rejected`, `REQ-GAME-041 progress is balance clamped to 0 and price`, `REQ-GAME-041 archived goal is not redeemable`.
  - `RewardServiceTest`: `REQ-GAME-040 parent creates reward and every child sees it`, `REQ-GAME-040 child cannot create, update or archive (403)`, `REQ-GAME-040 archived reward is hidden by default, listed with includeArchived and cannot be updated (409)`, `REQ-GAME-040 unknown reward 404`, `REQ-GAME-041 child sets goal and sees 120 of 200`, `REQ-GAME-041 parent reads goal but cannot set it`, `REQ-GAME-041 sibling cannot read goal`, `REQ-GAME-041 archived reward cannot become a goal (409)`, `REQ-GAME-041 no goal returns empty goal with balance`, `REQ-GAME-041 clearing twice is fine`.
  - `RedemptionServiceTest`: `REQ-GAME-042 redeem debits price and waits for parents`, `REQ-GAME-042 balance below price is rejected (409) and nothing is booked`, `REQ-GAME-042 balance exactly price is enough`, `REQ-GAME-042 parent cannot redeem (403)`, `REQ-GAME-042 archived reward cannot be redeemed but running redemptions stay (REQ-GAME-040)`, `REQ-GAME-042 redeeming the goal reward clears the goal`, `REQ-GAME-042 redemption does not change collected`, `REQ-GAME-042 child lists only own redemptions, parent lists all, status filter`, `REQ-GAME-043 fulfill completes pending redemption`, `REQ-GAME-043 reject refunds price and keeps reason`, `REQ-GAME-043 reject without reason is rejected (400)`, `REQ-GAME-043 deciding twice is a conflict and refunds only once`, `REQ-GAME-043 child cannot decide (403)`, `REQ-GAME-043 unknown redemption 404`.
- **MIRROR**: TEST_STRUCTURE; Testnamen mit REQ-ID (CLAUDE.md).
- **GOTCHA**: `GamificationFixture.child()` legt Kinder an; Guthaben mit `fixture.accounts.book(parent, child.id, 200, "Start")` aufbauen (zählt fürs Guthaben). `runTest` aus `kotlinx.coroutines.test`.
- **VALIDATE**: `./gradlew :gamification:test` – zuerst rot (RED), nach Task 5/6 grün.

### Task 9: Integrationstests der Repositories
- **ACTION**: `ExposedRewardRepositoryTest`, `ExposedRedemptionRepositoryTest`.
- **IMPLEMENT** (echte SQLite in `@TempDir`):
  - Reward: anlegen/ändern/archivieren, Sortierung, `update` auf archivierte → `false`, `archive` zweimal → `true`, unbekannt → `false`; Ziel upsert/clear; `CHECK price BETWEEN 1 AND 10000` greift bei direktem Insert.
  - Redemption: `REQ-GAME-042 redeem books debit with source key in the same transaction` (Ledger-Zeile `REWARD`, −Preis, `reward:<id>`), `… insufficient balance stores nothing`, `… two redeems for exactly one price succeed once` (zwei `redeem` hintereinander bei Guthaben = Preis → zweites `InsufficientBalance`), `REQ-GAME-043 reject books refund once`, `… fulfill after reject is NOT_PENDING`, `find` Reihenfolge/Filter/Limit, Ziel wird beim Einlösen gelöscht, FK-Cascade beim Löschen des Mitglieds (wie `ExposedBadgeRepositoryTest`).
- **MIRROR**: `ExposedBadgeRepositoryTest`, `ExposedLedgerRepositoryTest`.
- **GOTCHA**: Guthaben in Tests per `ExposedLedgerRepository(db).append(LedgerEntry(..., EntryKind.MANUAL, 200, ...))` anlegen. Belohnungen brauchen einen existierenden `created_by` (Eltern-Mitglied anlegen) oder `null`.
- **VALIDATE**: `./gradlew :gamification:test`.

### Task 10: API-Test mit Vertragsprüfung
- **ACTION**: `backend/app/src/test/kotlin/de/familyplanner/app/RewardsApiTest.kt`.
- **IMPLEMENT**: `REQ-GAME-040 parent creates reward, child lists it, child gets 403 on create`, `REQ-GAME-040 archive hides reward, update archived 409`, `REQ-GAME-041 child sets goal and reads progress, parent reads, sibling 403`, `REQ-GAME-042 child redeems, balance drops, parent sees pending redemption`, `REQ-GAME-042 insufficient balance 409`, `REQ-GAME-043 parent rejects with reason, child sees reason and refund`, `REQ-GAME-043 reject without body 400, second decision 409`, `REQ-GAME-042 invalid status filter 400`. Guthaben über `POST …/members/{id}/entries` (Eltern) aufbauen.
- **MIRROR**: `GamificationApiTest` (`start()`, `client.call(...)`, `json.decodeFromString<…Response>`), `setUpFamily`, `addPairedChild`.
- **GOTCHA**: `call(...)` validiert Request **und** Response gegen `openapi.yaml`; für bewusst ungültige Requests `invalidRequestExpected = true` setzen (Muster in bestehenden Tests suchen: `grep -n invalidRequestExpected backend/app/src/test`).
- **VALIDATE**: `./gradlew :app:test`.

### Task 11: Dokumentation
- **ACTION**: Design-Doc, Traceability, PRD.
- **IMPLEMENT**:
  - `docs/03-design/gamification.md`: Abschnitt „Belohnungen (Phase 3a)“ – Begriffe (Belohnung, Einlösung, Sparziel), Zustände `PENDING → FULFILLED | REJECTED`, Atomarität (eine Transaktion, Source-Keys), Sichtbarkeit/Rechte-Tabelle, Endpunkte, Tabellen V12, Eingabegrenzen, Testfälle. Kopfzeile „Abgedeckte Anforderungen“ um REQ-GAME-040–043 ergänzen; `EntryKind`-Zeile: `REWARD`, `REWARD_REFUND` werden ab Phase 3 gebucht.
  - `docs/04-verification/traceability.md`: Zeilen REQ-GAME-040, -041, -042, -043 (Unit/Integration wie oben, Status „Backend umgesetzt (3a), App folgt (3b)“).
  - `docs/01-requirements/gamification.prd.md`: Phase 3 Spalte „PRP Plan“ → „3a: umgesetzt, Bericht `.claude/PRPs/reports/belohnungen-backend-report.md`; 3b: offen“, Status bleibt `in-progress`.
- **VALIDATE**: Links auflösbar, Tabellen-Spaltenzahl stimmt.

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Einlösen genau Preis | Guthaben 200, Preis 200 | `PENDING`, Guthaben 0 | ja |
| Einlösen zu wenig | Guthaben 199, Preis 200 | 409, keine Buchung | ja |
| Negatives Guthaben + Ziel | Guthaben −20, Preis 200 | Fortschritt 0/200 | ja |
| Guthaben über Preis | 250 / 200 | Fortschritt 200/200, `redeemable` | ja |
| Ablehnen zweimal | 2× reject | 1 Rückbuchung, 2. Mal 409 | ja |
| Deaktiviert während Einlösung läuft | redeem, dann archive | Einlösung bleibt `PENDING`, entscheidbar | ja |
| Preis geändert nach Einlösung | redeem 200, update 300 | Einlösung zeigt 200 | ja |
| Gesammelt | Einlösung | `collected` unverändert, Rangliste unverändert | nein |

### Edge Cases Checklist
- [x] Leere/zu lange Titel, leeres Symbol, Preis 0/10001
- [x] Zu wenig Guthaben, exakt genug
- [x] Parallele Einlösungen (serialisiert durch eine Transaktion + Pool 1)
- [x] Rechte: Kind/Eltern/Geschwister je Endpunkt
- [x] Doppelte Entscheidung
- [x] Unbekannte IDs (404), ungültige UUID (400 über `uuidParameter`)

---

## Validation Commands

### Static Analysis
```bash
cd backend && ./gradlew ktlintCheck detekt
```
EXPECT: keine Funde

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
EXPECT: grün (nur neue Modelle)

### Database Validation
```bash
cd backend && FP_SETUP_TOKEN=test-setup-token-123456 FP_DB_PATH=/tmp/fp-rewards.db FP_TLS_ENABLED=false ./gradlew :app:run
sqlite3 /tmp/fp-rewards.db "select version, success from flyway_schema_history order by installed_rank"
```
EXPECT: V1–V12 erfolgreich, Tabellen `game_rewards`, `game_redemptions`, `game_savings_goals` vorhanden. Auf einer
**Kopie** der Testinstanz-DB starten: Migration läuft, bestehende Konten unverändert.

### Manual Validation (lokal gegen `:app:run`, curl)
- [ ] Familie einrichten, zwei Kinder koppeln (wie `deploy/README.md`)
- [ ] Eltern: „Freizeitpark“ 200 anlegen; Kind-Token `GET /gamification/rewards` → sichtbar
- [ ] Eltern buchen +120 für Kind 1; Kind 1 setzt Sparziel → `progress 120/200`, `redeemable false`
- [ ] Eltern buchen +80; Kind 1 löst ein → 201 `PENDING`; Konto: Guthaben 0, Verlauf „Belohnung: Freizeitpark −200“, Gesammelt 200; Sparziel leer
- [ ] Eltern `GET /gamification/redemptions?status=PENDING` → Einlösung sichtbar
- [ ] Eltern lehnen mit „Erst im Sommer“ ab → Guthaben 200, Kind sieht `rejectionReason`
- [ ] Erneut einlösen, Eltern markieren erfüllt → `FULFILLED`; zweites „erfüllt“ → 409
- [ ] Belohnung deaktivieren → Kind sieht sie nicht mehr, Einlösen → 409

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, Validation Commands grün
- [ ] Tests mit REQ-ID im Namen, Traceability gepflegt
- [ ] OpenAPI 0.9.0 und Implementierung stimmen überein (API-Tests mit Vertragsprüfung)
- [ ] Einlösen und Ablehnen sind atomar (Integrationstests)
- [ ] Android-Build bleibt grün

## Completion Checklist
- [ ] Code folgt den gefundenen Mustern (Katalog wie `tasks`, bedingter Wechsel wie `transition`)
- [ ] Fehler: 400/403/404/409 wie Bestand, deutsche Meldungen
- [ ] Logging: `info` je Schreibaktion, keine Freitexte im Log
- [ ] Keine hartkodierten Grenzen außerhalb der Konstanten
- [ ] Design-Doc, Traceability, PRD aktualisiert
- [ ] Keine Scope-Erweiterung (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Abbuchung ohne Einlösung oder umgekehrt | L | H | Eine `dbQuery`, `insert` statt `insertIgnore`, Integrationstest |
| Doppelte Rückbuchung | L | H | Bedingtes `UPDATE … WHERE status='PENDING'` + UNIQUE `source_key` |
| Vertrag bricht Android-Generator | M | M | `assembleDebug` als Validierung; nur additive Änderungen |
| detekt-Grenzen (`TooManyFunctions`, `LongMethod`) | M | L | eigene Dateien für Routen/Mapper/Tabellen, Helfer im Repository |
| Migration auf Testinstanz | L | M | Backup vor Migration (`DatabaseBackup`), Lauf auf DB-Kopie |

## Notes
- REQ-GAME-041 Kriterium 2 (Hinweis beim Item-Kauf) gehört zum Shop und wird in Phase 4 umgesetzt; das Sparziel-API liefert dafür schon `balance` und `progress`.
- Eltern-Hinweis bei neuer Einlösung (REQ-GAME-042) und Kind-Hinweis bei Ablehnung (REQ-GAME-043) entstehen in 3b über den App-Abgleich (ADR-0013); das Backend stellt dafür `GET /redemptions?status=PENDING` und die Einlösungen des Kindes bereit.
- Kein neues ADR: Die Entscheidungen bleiben im Modul und folgen ADR-0010/0016; sie werden im Design-Doc festgehalten.
- Umsetzung in einer neuen Session (Standard: Sonnet): `/ecc:prp-implement .claude/PRPs/plans/belohnungen-backend.plan.md`.
