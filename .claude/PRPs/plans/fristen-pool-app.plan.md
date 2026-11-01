# Plan: Fristen & Pool – App (TASK Phase 5)

## Summary
Die Android-App nutzt die Phase-4-API: Eltern setzen beim Zuweisen und an der Karte eine Frist und geben Katalogaufgaben für den
Pool frei; Board und „Heute“ zeigen Frist, „überfällig“ (aus Gerätuhr + Cache), „zu spät gemeldet“ und „selbst genommen“. Kinder
nehmen Aufgaben aus einem neuen Pool-Screen (nehmen / schon erledigt), legen selbst genommene zurück und werden 2 h vor Fristende
lokal erinnert (Alarm nach ADR-0013, eingeplant beim Hintergrund-Abgleich). Keine Backend- oder API-Änderung.

## User Story
Als Elternteil möchte ich Aufgaben eine Frist geben und Aufgaben für den Pool freigeben, und als Kind möchte ich Fristen sehen,
rechtzeitig erinnert werden und mir freigegebene Aufgaben selbst nehmen, damit „bis Samstag“-Aufgaben nicht untergehen und spontan
Erledigtes gutgeschrieben wird.

## Problem → Solution
Backend kennt Frist/Pool (Phase 4), die App ignoriert `dueAt`, `reportedLate`, `selfAssigned`, `childSelectable` → App liest, zeigt und
bearbeitet sie, plant Erinnerungen und bietet Kindern den Pool.

## Metadata
- **Complexity**: Large
- **Source PRD**: `docs/01-requirements/tasks.prd.md`
- **PRD Phase**: 5 – Fristen & Pool App (REQ-TASK-011, 026–028, 031)
- **Estimated Files**: ~35 (≈ 22 Produktion, ≈ 10 Tests, 3–4 Doku)

---

## UX Design

### Before
```
Board (Kind Ben)                       Zuweisen (Eltern)          Katalog bearbeiten
┌──────────────────────────┐           ┌───────────────────┐      ┌───────────────────┐
│ Todo                     │           │ Kind: [Ben] [Lea] │      │ Titel, Symbol     │
│ ┌ 🛁 Bad putzen   3 P ┐  │           │ ☑ Bad putzen  3 P │      │ Punkte [-] 3 [+]  │
│ │          [Erledigt] │  │           │ [Zuweisen (1)]    │      │ [Speichern]       │
│ └─────────────────────┘  │           └───────────────────┘      └───────────────────┘
│ (kein Knopf für Kinder)  │
└──────────────────────────┘
```

### After
```
Board (Kind Ben)                                  Pool (Kind)
┌──────────────────────────────────────┐          ┌──────────────────────────────────┐
│ Todo                                 │          │ Aufgabe nehmen                   │
│ ┌ 🛁 Bad putzen               3 P ┐  │          │ 🗑 Mülleimer leeren        1 P   │
│ │ Überfällig seit Sa. 01.11. 18:00 │ (rot, oben) │   [Nehmen]  [Schon erledigt]     │
│ │                     [Erledigt]  │  │          │ 🧺 Wäsche aufhängen        2 P   │
│ └─────────────────────────────────┘  │          │   [Nehmen]  [Schon erledigt]     │
│ ┌ 🗑 Mülleimer leeren          1 P ┐  │          │ [Zurück]                         │
│ │ Selbst genommen                 │  │          └──────────────────────────────────┘
│ │ [⋮ Zurücklegen]     [Erledigt]  │  │
│ └─────────────────────────────────┘  │          Zuweisen (Eltern)
│ Zur Abnahme                          │          ┌──────────────────────────────────┐
│ ┌ Zimmer  · Zu spät gemeldet ┐       │          │ Kind: [Ben] [Lea]                │
│                  [+ Aufgabe nehmen]  │ (FAB)    │ [Frist: keine]  → Datum + Uhrzeit│
└──────────────────────────────────────┘          │ ☑ Bad putzen  3 P                │
                                                  │ [Zuweisen (1)]                   │
Karte (Eltern) ⋮: Direkt abnehmen · Frist ändern · Entfernen   └──────────────────────────────────┘
Katalog bearbeiten: + Schalter „Für Kinder frei wählbar“; Katalogliste: „Frei wählbar“ unter dem Titel
„Heute“ Kind: „3 offen“, Titel mit „bis Sa. 01.11. 18:00“, „1 überfällig“ (rot); Eltern zusätzlich „n überfällig“
Benachrichtigung (Kind, 2 h vorher, offline): „Frist bald: Bad putzen“ / „Bis Sa. 01.11. 18:00 erledigen.“
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Zuweisen | Kind + Aufgaben | + optionale Frist (Datum, dann Uhrzeit, Vorschlag 18:00), „Frist entfernen“ | Frist gilt für alle Karten dieser Zuweisung |
| Karte (Eltern, TODO/REVIEW) | Menü: Direkt abnehmen/Entfernen | + „Frist ändern“ → Dialog (setzen/ändern/entfernen) | REQ-TASK-026 |
| Karte (alle) | Status-Zeile | + Frist / „Überfällig seit …“ / „Zu spät gemeldet“ / „Selbst genommen“ | REQ-TASK-027/028 |
| Todo-Abschnitt | nach Zuweisung | überfällige zuerst (nach Frist), dann Rest nach Zuweisung | REQ-TASK-027 |
| Board (Kind) | kein FAB | FAB „Aufgabe nehmen“ → Pool | REQ-TASK-028 |
| Karte (Kind, eigene selbst genommene TODO) | Erledigt | + Menü „Zurücklegen“ (ohne Bestätigung, erneut nehmbar) | REQ-TASK-028 |
| Katalog bearbeiten | Titel/Symbol/Punkte | + Schalter „Für Kinder frei wählbar“ | REQ-TASK-011 |
| „Heute“ | offen/Titel/zurückgewiesen | + Frist am Titel, Zahl überfällig | REQ-TASK-026/027 |
| Benachrichtigung | Status-Hinweise | + Erinnerung 2 h vor Fristende (nur Kind, nur TODO) | REQ-TASK-031 |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/board/BoardRules.kt` | all | `cardActions`, `sections`, `replacing/removing` – werden erweitert |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/board/UseCases.kt` | all | Use-Case-Muster inkl. Cache-Mitführung (`updateCache`) |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/notice/SyncTaskNoticesUseCase.kt` | all | Hier werden Erinnerungen eingeplant |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/reminder/AlarmReminderScheduler.kt` + `ReminderReceivers.kt` | all | Alarm-/Receiver-Muster (ADR-0013) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/reminder/UseCases.kt` | 116–148 | `ShowDueReminderUseCase`/`RestoreRemindersUseCase` |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/board/BoardViewModel.kt` | all | State/Events/`perform` |
| P1 | `.../feature/tasks/data/{Mappers,StoredBoard,RemoteTaskRepository,RemoteCatalogRepository}.kt` | all | API-/Cache-Abbildung |
| P1 | `.../feature/tasks/ui/board/{TaskCardItem,BoardScreen,BoardSections}.kt` | all | Karten-UI, FAB, Dialoge |
| P1 | `.../feature/tasks/ui/assign/*`, `ui/catalog/CatalogEdit*`, `ui/today/*` | all | Screens, die erweitert werden |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/settings/DeadlineSettingsScreen.kt` | 136–175 | TimePicker-Dialog-Muster |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/profile/ExceptionDialogs.kt` | 45–50, 136–160 | DatePickerDialog + `SelectableDates` |
| P1 | `android/feature/tasks/src/test/kotlin/de/familyplanner/android/feature/tasks/fakes/Fakes.kt` | all | Fakes, die erweitert werden |
| P2 | `api/openapi.yaml` | 917–990, 2235–2310 | Phase-4-Endpunkte und Schemas |
| P2 | `docs/03-design/tasks.md` | 72–end | App-Abschnitt, wird um Phase 5 ergänzt |
| P2 | `docs/02-architecture/adr/0013-local-reminders-and-status-sync.md` | all | Inexakte Alarme, Neu-Einplanung |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| PendingIntent-Gleichheit | Android `Intent.filterEquals` | Extras zählen **nicht**; eindeutige `data`-URI je Karte → ein Alarm je Karte, neuer Alarm mit gleicher URI ersetzt den alten |
| `AlarmManagerCompat.setAndAllowWhileIdle` | AndroidX core | Inexakt, feuert im Doze, keine Sonderberechtigung (wie meals) |
| kotlinx `explicitNulls = false` | kotlinx.serialization | Lässt `null`-Properties von Klassen **weg**; `JsonNull` in einem `JsonObject` wird dagegen geschrieben |
| Material3 `DatePicker` | Compose M3 | `selectedDateMillis` ist UTC-Mitternacht → mit `ZoneOffset.UTC` in `LocalDate` umwandeln |

---

## Patterns to Mirror

### NAMING_CONVENTION / USE_CASE
```kotlin
// SOURCE: feature/tasks/domain/board/UseCases.kt:63-72
class RemoveCardUseCase(
    private val repository: TaskRepository,
    private val cache: BoardCache,
    private val sessions: SessionStore,
) {
    suspend operator fun invoke(cardId: String): ApiResult<Unit> =
        repository.remove(cardId).also { result ->
            if (result is ApiResult.Success) updateCache(sessions, cache) { it.removing(cardId) }
        }
}
```

### ERROR_HANDLING (Validierung vor dem Server)
```kotlin
// SOURCE: feature/tasks/domain/board/UseCases.kt:90-95
when {
    memberId == null -> validationFailure("Bitte ein Kind wählen")
    catalogTaskIds.isEmpty() -> validationFailure("Bitte mindestens eine Aufgabe wählen")
    ...
    else -> repository.assign(memberId, catalogTaskIds)
}
```

### REPOSITORY_PATTERN
```kotlin
// SOURCE: feature/tasks/data/RemoteTaskRepository.kt:32,39-43
override suspend fun report(cardId: String): ApiResult<TaskCard> = apiCall(json, { tasks.reportTask(cardId) }) { it.data?.toDomain() }
/** Immer mit Body senden: Retrofit lehnt einen `null`-Body ab; `{}` heißt „kein Grund“. */
override suspend fun reject(cardId: String, reason: String?): ApiResult<TaskCard> =
    apiCall(json, { tasks.rejectTask(cardId, RejectTaskRequest(reason)) }) { it.data?.toDomain() }
```

### MAPPER (unlesbar → null statt halber Karte)
```kotlin
// SOURCE: feature/tasks/data/Mappers.kt:14-22
internal fun ApiTaskCard.toDomain(): TaskCard? {
    val assigned = parseInstant(assignedAt)
    val reported = reportedAt?.let { parseInstant(it) }
    ...
    val unreadable = assigned == null || mapped == null || (reportedAt != null && reported == null) || (doneAt != null && done == null)
    return if (unreadable) null else TaskCard(...)
}
```

### ALARM / RECEIVER
```kotlin
// SOURCE: feature/meals/data/reminder/AlarmReminderScheduler.kt:20-25 und ReminderReceivers.kt:16-38
AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, it.at.toEpochMilli(), pendingIntent(it))

private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.Default).launch { try { block() } finally { pending.finish() } }
}
class ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent {
    override fun onReceive(context: Context, intent: Intent) {
        val weekStart = parseDate(intent.getStringExtra(EXTRA_WEEK_START)) ?: return
        launchAsync { get<ShowDueReminderUseCase>()(weekStart) }
    }
}
```

### LOGGING_PATTERN
```kotlin
// SOURCE: feature/tasks/data/DataStoreBoardCache.kt:14,26-28
private const val TAG = "DataStoreBoardCache"
} catch (e: IOException) { Log.w(TAG, "Task board cache unreadable", e); null }
```
Domäne loggt nicht; nur `data/` mit `Log.w` und englischen Meldungen.

### VIEWMODEL (State + Events)
```kotlin
// SOURCE: feature/tasks/ui/board/BoardViewModel.kt:118-131
fun onEvent(event: BoardEvent) {
    when (event) {
        BoardEvent.Refresh -> { _state.update { it.copy(actionError = null) }; load() }
        is BoardEvent.Act -> act(event.card, event.action)
        ...
    }
}
```

### TEST_STRUCTURE
```kotlin
// SOURCE: feature/tasks/src/test/.../domain/SyncTaskNoticesUseCaseTest.kt:26-40
class SyncTaskNoticesUseCaseTest {
    private val repository = FakeTaskRepository()
    private val cache = InMemoryBoardCache()
    private fun sync() = SyncTaskNoticesUseCase(sessions, repository, cache, notifier, Clock.fixed(noon, ZoneOffset.UTC))
    @Test
    fun `REQ-TASK-030 first sync stores a baseline without notices`() = runTest { ... }
}
```
Repository-Tests mit `MockWebServer` (`RemoteTaskRepositoryTest`), Compose-/Android-Tests mit `@RunWith(RobolectricTestRunner::class)`.

---

## Files to Change

Basis: `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/` = `T/`, Tests `android/feature/tasks/src/test/kotlin/de/familyplanner/android/feature/tasks/` = `TT/`.

| File | Action | Justification |
|---|---|---|
| `T/domain/board/Models.kt` | UPDATE | `TaskCard` + `dueAt`, `reportedLate`, `selfAssigned` (Defaults) |
| `T/domain/board/TaskLimits.kt` | UPDATE | `REMINDER_LEAD_HOURS = 2L`, `DUE_DEFAULT_HOUR = 18` |
| `T/domain/board/DueDates.kt` | CREATE | `isOverdue`, `validateDueAt`, `dueInstant(date,time)`, `taskReminders(...)` |
| `T/domain/board/BoardRules.kt` | UPDATE | `cardActions` (+SET_DUE_DATE, PUT_BACK), `sections(cards, now)`, `adding`, `overdueCount`, Heute-Sortierung |
| `T/domain/board/Ports.kt` | UPDATE | `setDueDate`, `takeFromPool`, `assign(..., dueAt)`; `CatalogRepository.pool()`; `TaskNotifier.remind`; neuer Port `TaskReminderScheduler` |
| `T/domain/board/UseCases.kt` | UPDATE | `AssignTasksUseCase` mit Frist+Clock; `SetDueDateUseCase` |
| `T/domain/pool/PoolUseCases.kt` | CREATE | `LoadPoolUseCase`, `TakePoolTaskUseCase` |
| `T/domain/reminder/TaskReminderUseCases.kt` | CREATE | `ShowTaskReminderUseCase`, `RestoreTaskRemindersUseCase`; Modell `TaskReminder` |
| `T/domain/notice/SyncTaskNoticesUseCase.kt` | UPDATE | plant nach erfolgreichem Abgleich Erinnerungen |
| `T/domain/notice/NoticeRules.kt` | UPDATE | kein „Neue Aufgabe“ für `selfAssigned` |
| `T/domain/catalog/Models.kt`, `Validation.kt` | UPDATE | `childSelectable` in `CatalogTask`/`CatalogInput` (normalized behält es) |
| `T/data/Mappers.kt` | UPDATE | neue Felder lesen/schreiben |
| `T/data/StoredBoard.kt` | UPDATE | `dueAtMillis`, `reportedLate`, `selfAssigned` (Defaults, Version bleibt 1) |
| `T/data/TaskDueDateApi.kt` | CREATE | handgeschriebener Retrofit-Endpunkt mit `JsonObject`-Body (explizites `null`) |
| `T/data/RemoteTaskRepository.kt` | UPDATE | `setDueDate`, `takeFromPool`, `assign` mit `dueAt` |
| `T/data/RemoteCatalogRepository.kt` | UPDATE | `pool()`; `childSelectable` senden |
| `T/data/AndroidTaskNotifier.kt` | UPDATE | `remind(reminder)` |
| `T/data/reminder/AlarmTaskReminderScheduler.kt` | CREATE | ein Alarm je Karte (data-URI) |
| `T/data/reminder/TaskReminderReceivers.kt` | CREATE | Alarm- und Restore-Receiver |
| `android/feature/tasks/src/main/AndroidManifest.xml` | UPDATE | Receiver + Berechtigungen |
| `T/TasksModule.kt` | UPDATE | Koin-Einträge |
| `T/TasksNavigation.kt` | UPDATE | `PoolRoute`, `onOpenPool` |
| `T/ui/Labels.kt` | UPDATE | Labels/`isSecondary` für neue Aktionen |
| `T/ui/due/DueDateDialogs.kt` | CREATE | `DueDateField` (Button + Picker-Folge), `DueDatePickerDialog` |
| `T/ui/board/{BoardViewModel,BoardScreen,BoardSections,TaskCardItem}.kt` | UPDATE | Frist-Dialog, FAB Kind, Meta-Zeilen, overdue |
| `T/ui/assign/{AssignViewModel,AssignScreen}.kt` | UPDATE | Frist beim Zuweisen |
| `T/ui/catalog/{CatalogEditViewModel,CatalogEditScreen,CatalogScreen}.kt` | UPDATE | Freigabe-Schalter, Kennzeichen in Liste |
| `T/ui/pool/{PoolViewModel,PoolScreen}.kt` | CREATE | Pool-Screen |
| `T/ui/today/{TodayTasksViewModel,TodayTasksCard}.kt` | UPDATE | Frist/überfällig |
| `android/feature/tasks/src/main/res/values/strings.xml` | UPDATE | neue Texte |
| `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/Formatting.kt` | UPDATE | `formatWeekdayDateTime` („Sa. 01.11. 18:00“) |
| Tests (`TT/...`) | UPDATE/CREATE | siehe Testing Strategy |
| `docs/03-design/tasks.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/tasks.prd.md`, `android/README.md` | UPDATE | Doku, Abnahme-Checkliste |

## NOT Building

- Keine Backend-/OpenAPI-Änderung (Phase 4 ist vollständig; `TasksApi` ist schon generiert).
- Kein Hinweis an das Kind, wenn Eltern nur die Frist ändern (nicht in REQ-TASK-030).
- Keine Erinnerung für Eltern, keine zweite Erinnerung, keine Erinnerung, wenn das Fristende weniger als 2 h entfernt ist, wenn die
  Frist gesetzt wird (Alarm in der Vergangenheit würde bei jedem Abgleich sofort feuern).
- Keine exakten Alarme (ADR-0013).
- Kein Löschen alter Alarme beim Abmelden/Melden: der Receiver prüft gegen den lokalen Stand und verwirft veraltete Alarme.
- Keine Offline-Warteschlange für Nehmen/Frist (wie bisher: Aktionen nur im Heimnetz).
- Keine Mehrfachauswahl im Pool; nach einem erfolgreichen Nehmen geht es zurück zum Board.
- Keine Frist für selbst genommene Karten (PRD: „haben keine Frist“) – die App bietet „Frist ändern“ für `selfAssigned` nicht an.
- Kein Umbau von `meals`-Erinnerungen; Code wird gespiegelt, nicht geteilt (Feature-Module kennen sich nicht).

---

## Step-by-Step Tasks

### Task 1: Domänenmodell und Frist-Regeln
- **ACTION**: `TaskCard` erweitern, `DueDates.kt` anlegen, `TaskLimits` ergänzen.
- **IMPLEMENT**:
  - `Models.kt`: am **Ende** von `TaskCard` `val dueAt: Instant? = null, val reportedLate: Boolean = false, val selfAssigned: Boolean = false`
    (Defaults → bestehende positionelle Aufrufe und `card(...)`-Fake kompilieren weiter).
  - `TaskLimits`: `const val REMINDER_LEAD_HOURS = 2L`, `const val DUE_DEFAULT_HOUR = 18`.
  - `DueDates.kt` (Paket `domain.board`):
    ```kotlin
    /** Überfällig: noch in TODO und Fristende erreicht (REQ-TASK-027); offline mit der Geräteuhr. */
    fun TaskCard.isOverdue(now: Instant): Boolean = status == TaskStatus.TODO && dueAt != null && !dueAt.isAfter(now)
    /** Frist aus Datum + Uhrzeit in Berlin. */
    fun dueInstant(date: LocalDate, time: LocalTime): Instant = ZonedDateTime.of(date, time, BerlinZone).toInstant()
    /** `null` = gültig; die Frist muss in der Zukunft liegen (Server prüft ebenso, 400). */
    fun validateDueAt(dueAt: Instant?, now: Instant): AppError.Validation? =
        if (dueAt != null && !dueAt.isAfter(now)) AppError.Validation("Die Frist muss in der Zukunft liegen") else null
    ```
  - Grenzfall wie Backend (`DueDates.kt` im Backend): „überfällig“ ab `now >= dueAt`.
- **MIRROR**: `TaskLimits.kt`, `Validation.kt` (gibt `AppError.Validation?` zurück).
- **IMPORTS**: `de.familyplanner.android.core.ui.BerlinZone`, `de.familyplanner.android.core.data.AppError`, `java.time.*`.
- **GOTCHA**: Domäne hängt bereits an `core.ui.BerlinZone` (siehe `UseCases.kt`) – erlaubt.
- **VALIDATE**: `DueDatesTest` (neu): Grenze `now == dueAt` → überfällig, REVIEW nie überfällig, `dueInstant` bei Sommer-/Winterzeit
  (`2026-10-25` Umstellung), `validateDueAt` Vergangenheit/Gegenwart/Zukunft/null.

### Task 2: Board-Regeln erweitern
- **ACTION**: `BoardRules.kt` anpassen.
- **IMPLEMENT**:
  - `enum class CardAction { REPORT, WITHDRAW, ACCEPT, REJECT, ACCEPT_DIRECTLY, SET_DUE_DATE, REMOVE, PUT_BACK }`.
  - `cardActions`: Eltern `REVIEW` → `ACCEPT, REJECT, SET_DUE_DATE, REMOVE`; Eltern `TODO` → `ACCEPT_DIRECTLY, SET_DUE_DATE, REMOVE`;
    `SET_DUE_DATE` nur wenn `!card.selfAssigned`. Kind eigene `TODO` → `REPORT` plus `PUT_BACK` wenn `selfAssigned`
    (Reihenfolge `listOf(CardAction.PUT_BACK, CardAction.REPORT)` – „letzte Hauptaktion“ ist Erledigt).
  - `sections(cards: List<TaskCard>, now: Instant)`: todo = `sortedWith(compareByDescending<TaskCard> { it.isOverdue(now) }.thenBy { if (it.isOverdue(now)) it.dueAt else null }.thenBy { it.assignedAt })`
    → einfacher: zuerst `partition { it.isOverdue(now) }`, überfällige `sortedBy { it.dueAt }`, Rest `sortedBy { it.assignedAt }`.
  - `fun List<TaskLane>.adding(card: TaskCard): List<TaskLane>` (fügt in die Spur `card.memberId` an).
  - `fun overdueCount(lanes: List<TaskLane>, now: Instant): Int`.
  - `openCards(lane, now)`: wie bisher (nicht DONE), sortiert: überfällig zuerst, dann mit Frist (früheste zuerst), dann ohne Frist nach `assignedAt`.
    Aufrufer (`TodayTasksViewModel`) anpassen.
- **MIRROR**: bestehende Funktionen in `BoardRules.kt`.
- **GOTCHA**: `sections` wird in `BoardUiState.sectionsOf` aufgerufen → `now` in den State (Task 9). `ui/Labels.kt` braucht Einträge für
  `SET_DUE_DATE` („Frist ändern“) und `PUT_BACK` („Zurücklegen“); `isSecondary` um beide erweitern – sonst `when` nicht exhaustiv.
- **VALIDATE**: `BoardRulesTest`: Aktionen je Rolle inkl. `selfAssigned`, Sortierung überfällig oben, nach Zurückweisung wieder überfällig
  (Karte TODO mit vergangener Frist), `adding`, `overdueCount`.

### Task 3: Katalog-Domäne mit Freigabe
- **ACTION**: `CatalogTask(…, val childSelectable: Boolean = false)`, `CatalogInput(…, val childSelectable: Boolean = false)`.
- **IMPLEMENT**: `normalized()` übernimmt `childSelectable`. `PoolUseCases.kt` (Paket `domain.pool`):
  ```kotlin
  class LoadPoolUseCase(private val repository: CatalogRepository) { suspend operator fun invoke() = repository.pool() }
  /** Karte aus dem Pool (REQ-TASK-028); der lokale Stand bekommt sie sofort, damit der Abgleich keinen „Neue Aufgabe“-Hinweis zeigt. */
  class TakePoolTaskUseCase(repository: TaskRepository, cache: BoardCache, sessions: SessionStore) {
      suspend operator fun invoke(catalogTaskId: String, alreadyDone: Boolean): ApiResult<TaskCard> =
          repository.takeFromPool(catalogTaskId, alreadyDone).also { r -> if (r is ApiResult.Success) updateCache(...) { it.adding(r.data) } }
  }
  ```
  `updateCache` in `UseCases.kt` ist `private` → auf `internal` ändern.
- **MIRROR**: `RemoveCardUseCase`.
- **VALIDATE**: `UseCasesTest`: Take aktualisiert Cache; Fehler lässt Cache unverändert.

### Task 4: Ports und Use Cases für Frist und Zuweisen
- **ACTION**: `Ports.kt`, `UseCases.kt`.
- **IMPLEMENT**:
  - `TaskRepository`: `assign(memberId, catalogTaskIds, dueAt: Instant?)`, `setDueDate(cardId: String, dueAt: Instant?): ApiResult<TaskCard>`,
    `takeFromPool(catalogTaskId: String, alreadyDone: Boolean): ApiResult<TaskCard>`.
  - `CatalogRepository.pool(): ApiResult<List<CatalogTask>>`.
  - `TaskNotifier.remind(reminder: TaskReminder)`.
  - Neuer Port `interface TaskReminderScheduler { fun schedule(reminders: List<TaskReminder>) }` – idempotent je Karte.
  - `AssignTasksUseCase(repository, clock = Clock.system(BerlinZone))`, `invoke(memberId, catalogTaskIds, dueAt: Instant? = null)`;
    zusätzliche Bedingung vor dem Repository: `validateDueAt(dueAt, clock.instant())?.let { return ApiResult.Failure(it) }`.
  - `SetDueDateUseCase(repository, cache, sessions, clock)`: validiert, ruft `setDueDate`, ersetzt Karte im Cache (`replacing`).
- **MIRROR**: `MoveCardUseCase` (Cache mitführen), `AssignTasksUseCase`.
- **GOTCHA**: `FakeTaskRepository`/`FakeCatalogRepository`/`FakeTaskNotifier` müssen die neuen Methoden bekommen (Task 13), sonst
  kompiliert kein Test. `AssignTasksUseCase` bleibt in Koin `factory { AssignTasksUseCase(get()) }` (Clock-Default).
- **VALIDATE**: `UseCasesTest`: Zuweisen mit Frist in der Vergangenheit → Validation ohne Serveraufruf; SetDueDate ersetzt im Cache; `null` entfernt.

### Task 5: Erinnerungen in der Domäne
- **ACTION**: `domain/reminder/TaskReminderUseCases.kt` (Paket `domain.reminder`) anlegen; `taskReminders` in `DueDates.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Erinnerung an Karte [cardId], [at] = Fristende − 2 h (REQ-TASK-031). */
  data class TaskReminder(val cardId: String, val title: String, val dueAt: Instant, val at: Instant)

  /** Nur Kinder, nur eigene Karten in TODO mit Frist; vergangene Zeitpunkte entfallen. */
  fun taskReminders(lanes: List<TaskLane>, memberId: String, isParent: Boolean, now: Instant): List<TaskReminder> =
      if (isParent) emptyList() else lanes.filter { it.memberId == memberId }.flatMap { it.cards }
          .filter { it.status == TaskStatus.TODO && it.dueAt != null }
          .map { TaskReminder(it.id, it.title, it.dueAt!!, it.dueAt.minus(Duration.ofHours(TaskLimits.REMINDER_LEAD_HOURS))) }
          .filter { it.at.isAfter(now) }
  ```
  (`!!` vermeiden: `mapNotNull { card -> card.dueAt?.let { … } }`.)
  - `ShowTaskReminderUseCase(sessions, cache, notifier, clock)`: `invoke(cardId: String, dueAt: Instant)` – Session abwarten (`awaitSession`
    wie `SyncTaskNoticesUseCase`, Hilfsfunktion dort `private` → nach `domain/notice` als `internal` oder in die neue Datei kopieren),
    Karte im Cache des Mitglieds suchen; erinnern nur wenn eigene Spur, `TODO`, `dueAt` gleich dem Alarm, `dueAt > now`.
  - `RestoreTaskRemindersUseCase(sessions, cache, scheduler, clock)`: Cache lesen → `scheduler.schedule(taskReminders(...))`.
- **MIRROR**: meals `ShowDueReminderUseCase`, `RestoreRemindersUseCase`.
- **GOTCHA**: Veraltete Alarme (gemeldet, entfernt, Frist entfernt/geändert, abgemeldet) werden **hier** verworfen – das ist die
  gesamte Aufräumstrategie. Session `Role` steht in `Session.role`.
- **VALIDATE**: `TaskReminderUseCasesTest` (neu): erinnert bei TODO+gleicher Frist; nicht bei REVIEW, anderer Frist, entfernter Karte,
  fremder Spur, Frist vorbei, ohne Session; Restore plant nur künftige; Eltern bekommen nie Erinnerungen.

### Task 6: Abgleich plant Erinnerungen; Hinweisregel
- **ACTION**: `SyncTaskNoticesUseCase` bekommt `scheduler: TaskReminderScheduler` (vor `clock`); nach `cache.write(...)`:
  `scheduler.schedule(taskReminders(lanes, session.memberId, session.role == Role.PARENT, clock.instant()))`.
- **IMPLEMENT**: `NoticeRules.childNotice`: `before == null -> … .takeIf { now.status == TaskStatus.TODO && !now.selfAssigned }`.
- **GOTCHA**: Koin `single { SyncTaskNoticesUseCase(get(), get(), get(), get(), get()) }`. Testkonstruktor in
  `SyncTaskNoticesUseCaseTest` anpassen (Fake-Scheduler). Bei `UNREACHABLE` nichts neu planen (Alarme bleiben, ADR-0013).
  Eltern-Hinweis bei „schon erledigt“ funktioniert bereits (neue Karte in `REVIEW` → `Reported`), nur Test ergänzen.
- **VALIDATE**: `SyncTaskNoticesUseCaseTest`: Kind-Abgleich plant Erinnerung für eigene TODO mit Frist; Frist geändert → neue Zeit;
  Eltern-Abgleich plant nichts; neue REVIEW-Karte aus dem Pool → `Reported`. `NoticeRulesTest`: selbst genommene neue TODO → kein Hinweis.

### Task 7: Datenschicht – API, Mapper, Cache
- **ACTION**: Mapper, `StoredBoard`, Repositories, `TaskDueDateApi`.
- **IMPLEMENT**:
  - `Mappers.kt`: `dueAt` parsen (vorhanden+unlesbar → `null`-Karte, wie `reportedAt`), `reportedLate`, `selfAssigned` übernehmen.
    `ApiCatalogTask.toDomain() = CatalogTask(id, title, emoji, points, childSelectable)`;
    `CatalogInput.toApi() = CatalogTaskInput(title = title, points = points, emoji = emoji, childSelectable = childSelectable)`
    (immer senden → Ändern übernimmt den Schalter; Backend: fehlt = unverändert).
  - `StoredCard`: `val dueAtMillis: Long? = null, val reportedLate: Boolean = false, val selfAssigned: Boolean = false`; `CACHE_VERSION` bleibt 1
    (alte Dateien lesen sich mit Defaults; neue Felder sind additiv).
  - `TaskDueDateApi.kt`:
    ```kotlin
    /** Eigener Endpunkt, weil `explicitNulls = false` ein `dueAt: null` der generierten Klasse wegließe – der Server verlangt den Schlüssel (`{}` → 400). */
    interface TaskDueDateApi {
        @PUT("api/v1/tasks/cards/{cardId}/due-date")
        suspend fun setTaskDueDate(@Path("cardId") cardId: String, @Body body: JsonObject): Response<TaskCardResponse>
    }
    ```
    Body: `buildJsonObject { put("dueAt", dueAt?.toString()) }` (`put(String, String?)` schreibt `JsonNull`).
  - `RemoteTaskRepository(tasks, dueDates: TaskDueDateApi, json)`: `assign` mit `AssignTasksRequest(memberId, ids, dueAt?.toString())`,
    `setDueDate`, `takeFromPool` → `tasks.takePoolTask(id, TakePoolTaskRequest(alreadyDone))`.
  - `RemoteCatalogRepository.pool()` → `tasks.getTaskPool()`, `data?.map { it.toDomain() }`.
- **MIRROR**: `RemoteTaskRepository.reject`, `Mappers.kt`.
- **IMPORTS**: `de.familyplanner.api.model.{TakePoolTaskRequest, TaskCardResponse}`, `kotlinx.serialization.json.{JsonObject, buildJsonObject, put}`,
  `retrofit2.http.{PUT, Path, Body}`, `retrofit2.Response`.
- **GOTCHA**: `Instant.toString()` ergibt ISO-8601 mit `Z` – vom Backend akzeptiert. Koin:
  `single { get<Retrofit>().create(TaskDueDateApi::class.java) }`, `RemoteTaskRepository(get(), get(), get())`.
  `RemoteTaskRepositoryTest.setUp` muss `TaskDueDateApi` mitgeben.
- **VALIDATE**: `RemoteTaskRepositoryTest`: Frist entfernen sendet exakt `{"dueAt":null}` (Body von `server.takeRequest()`), setzen sendet
  ISO-Zeit, Assign mit/ohne `dueAt` (ohne → Schlüssel fehlt), Pool nehmen sendet `{"alreadyDone":true}` an `/api/v1/tasks/pool/k-1/cards`
  und liest 201, Karte mit `dueAt`/`reportedLate`/`selfAssigned`, unlesbares `dueAt` → `AppError.Server`. `RemoteCatalogRepositoryTest`: Pool,
  `childSelectable` lesen und senden. `DataStoreBoardCacheTest`: neue Felder überleben Rundreise; alte Datei ohne Felder lesbar.

### Task 8: Alarm, Receiver, Notifier, Manifest
- **ACTION**: `data/reminder/AlarmTaskReminderScheduler.kt`, `data/reminder/TaskReminderReceivers.kt`, `AndroidTaskNotifier.remind`, Manifest.
- **IMPLEMENT**:
  - Scheduler: je Reminder `PendingIntent.getBroadcast(context, 0, intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)` mit
    `Intent(context, TaskReminderReceiver::class.java).setData(Uri.parse("familyplanner://task-reminder/${cardId}")).putExtra(EXTRA_CARD_ID, cardId).putExtra(EXTRA_DUE_AT, dueAt.toEpochMilli())`
    und `AlarmManagerCompat.setAndAllowWhileIdle(..., RTC_WAKEUP, at.toEpochMilli(), pi)`. Gleiche URI ersetzt einen bestehenden Alarm.
  - `TaskReminderReceiver`: Extras lesen (fehlend → return), `launchAsync { get<ShowTaskReminderUseCase>()(cardId, Instant.ofEpochMilli(dueAt)) }`.
  - `TaskRemindersRestoreReceiver`: `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED` → `RestoreTaskRemindersUseCase`, dann `TaskSync.requestNow()`.
  - `launchAsync` als private Hilfsfunktion in der Datei spiegeln (aus meals kopieren – Module kennen sich nicht).
  - Notifier: `remind(r)` → Titel `tasks_reminder_title` („Frist bald: %1$s“), Text `tasks_reminder_text` („Bis %1$s erledigen.“, mit
    `formatWeekdayDateTime(r.dueAt)`), ID `REMINDER_ID_BASE + (cardId.hashCode() and TASK_ID_MASK)` mit `REMINDER_ID_BASE = 200_000`
    (überschneidet sich nicht mit Status-Hinweisen 100 000–165 535). Gleicher Kanal, gleicher Tap-Intent.
  - Manifest `feature/tasks/src/main/AndroidManifest.xml`: `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, beide Receiver `exported="false"`,
    Restore mit Intent-Filter (wie meals-Manifest).
- **MIRROR**: meals `AlarmReminderScheduler`, `ReminderReceivers`, meals-Manifest.
- **GOTCHA**: Extras zählen nicht für PendingIntent-Gleichheit → ohne data-URI würden alle Karten denselben Alarm teilen.
  `Uri.parse` in Unit-Tests nur unter Robolectric.
- **VALIDATE**: `AlarmTaskReminderSchedulerTest` (Robolectric, `ShadowAlarmManager`): zwei Karten → zwei Alarme; gleiche Karte erneut
  mit anderer Zeit → ein Alarm mit neuer Zeit. `AndroidTaskNotifierTest`: Erinnerungstext.

### Task 9: Board-UI
- **ACTION**: `BoardViewModel`, `BoardScreen`, `BoardSections`, `TaskCardItem`, `Labels.kt`.
- **IMPLEMENT**:
  - `BoardUiState` + `val now: Instant = Instant.EPOCH`, `val editingDue: TaskCard? = null`; `sectionsOf(lane) = sections(lane.cards, now)`;
    `fun isOverdue(card) = card.isOverdue(now)`.
  - `BoardViewModel(sessions, loadBoard, moveCard, removeCard, setDueDate, clock = Clock.system(BerlinZone))`; `show()` setzt `now = clock.instant()`.
  - Events: `BoardEvent.ConfirmDueDate(val dueAt: Instant?)`. `act`: `SET_DUE_DATE` → `editingDue = card`; `PUT_BACK` → direkt `perform`
    (ohne Bestätigung); `DismissDialog` setzt auch `editingDue = null`. `change(...)`: `REMOVE, PUT_BACK` → `removeCard`; `SET_DUE_DATE` → `setDueDate(card.id, dueAt)` und `replacing`.
    `perform` bekommt optionalen `dueAt`-Parameter (oder eigene Funktion `performDueDate`, um `change` schlank zu halten).
  - `MoveCardUseCase`: `SET_DUE_DATE`/`PUT_BACK` → `IllegalArgumentException` wie `REMOVE`.
  - `BoardDialogs`: `state.editingDue?.let { DueDatePickerDialog(initial = it.dueAt, now = state.now, allowRemove = it.dueAt != null, onConfirm = { onEvent(BoardEvent.ConfirmDueDate(it)) }, onDismiss = …) }`.
  - `BoardScreen`: FAB für Kinder `ExtendedFloatingActionButton(onClick = onOpenPool, text = tasks_pool_fab „Aufgabe nehmen“)`;
    neuer Parameter `onOpenPool: () -> Unit` in `BoardRoute`/`BoardScreen`.
  - `TaskCardItem(card, childName, actions, enabled, onAction, overdue: Boolean = false, modifier)`; `CardMeta` ergänzt Zeilen (mehrere möglich,
    in einer `Column`): Frist `tasks_card_due` („Frist: %1$s“) bzw. bei `overdue` `tasks_card_overdue` („Überfällig seit %1$s“, `colorScheme.error`);
    nur für TODO/REVIEW anzeigen; `reportedLate` → `tasks_card_reported_late` („Zu spät gemeldet“, `tertiary`) in REVIEW/DONE;
    `selfAssigned` → `tasks_card_self_assigned` („Selbst genommen“). Bestehende Zeilen (gemeldet/abgenommen/zurückgewiesen) bleiben.
  - `BoardSections.cardItems` reicht `overdue = state.isOverdue(card)` durch.
- **MIRROR**: `RejectDialog`/`ConfirmDialog`-Handhabung in `BoardDialogs`.
- **GOTCHA**: `TasksNavigation.tasksGraph` muss `onOpenPool = { navController.navigate(PoolRoute) }` übergeben. Die Zahl der
  Konstruktorparameter von `BoardViewModel` wächst auf 6 → detekt `LongParameterList` (Schwelle prüfen; ggf. `@Suppress` wie `card(...)`-Fake
  oder Use Cases bündeln wie `CatalogUseCases`). Funktionsgrößen < 50 Zeilen halten (`CardMeta` ggf. aufteilen).
- **VALIDATE**: `BoardViewModelTest`: Frist ändern ersetzt Karte; Frist entfernen; Zurücklegen entfernt Karte; overdue mit fixer Uhr nach
  oben. `BoardScreenTest`: „Überfällig seit …“, „Zu spät gemeldet“, „Selbst genommen“, FAB „Aufgabe nehmen“ nur für Kind, Menü „Frist ändern“ nur Eltern.

### Task 10: Frist-Picker
- **ACTION**: `ui/due/DueDateDialogs.kt`.
- **IMPLEMENT**:
  - `DueDatePickerDialog(initial: Instant?, now: Instant, allowRemove: Boolean, onConfirm: (Instant?) -> Unit, onDismiss)`: Schritt 1
    `DatePickerDialog` + `DatePicker` (`selectableDates`: Tage ab heute in Berlin), Schritt 2 `AlertDialog` mit `TimePicker`
    (`is24Hour = true`, Start = Uhrzeit von `initial` oder `TaskLimits.DUE_DEFAULT_HOUR:00`). Bestätigen → `dueInstant(date, time)`.
    `allowRemove` → zusätzlicher Button „Frist entfernen“ im Datumsschritt → `onConfirm(null)`. Schritt-Zustand per `rememberSaveable`.
  - `DueDateField(dueAt: Instant?, now: Instant, onChange: (Instant?) -> Unit, enabled: Boolean)`: `OutlinedButton` „Frist: keine“ /
    „Frist: Sa. 01.11. 18:00“, öffnet den Dialog (für Zuweisen).
  - Datums-Umrechnung: `Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()` (Picker liefert UTC-Mitternacht); Initialwert
    `initial.atZone(BerlinZone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()`.
  - Vergangene Uhrzeit am heutigen Tag: Dialog schickt sie ab; Use Case lehnt mit „Die Frist muss in der Zukunft liegen“ ab (Fehler am Screen).
- **MIRROR**: meals `TimePickerDialog` (DeadlineSettingsScreen 156–175), `RangePickerDialog` + `SupportedYears` (ExceptionDialogs).
- **IMPORTS**: `androidx.compose.material3.{DatePicker, DatePickerDialog, TimePicker, rememberDatePickerState, rememberTimePickerState, SelectableDates, ExperimentalMaterial3Api}`,
  `de.familyplanner.android.core.ui.R as UiR` (`action_confirm`, `action_cancel`).
- **GOTCHA**: `@OptIn(ExperimentalMaterial3Api::class)`. Picker in Robolectric-Compose-Tests schwer zu bedienen → Logik (Umrechnung) als
  reine Funktionen testen (`DueDatesTest`), Screen-Tests prüfen nur Button-Text und Öffnen.
- **VALIDATE**: `DueDatesTest` (Umrechnung), `TaskScreensTest` (Button-Text).

### Task 11: Zuweisen und Katalog
- **ACTION**: `AssignViewModel/Screen`, `CatalogEditViewModel/Screen`, `CatalogScreen`.
- **IMPLEMENT**:
  - `AssignUiState` + `dueAt: Instant? = null`, `now: Instant`; `AssignEvent.DueDateChanged(val dueAt: Instant?)`; `submit` →
    `assign(memberId, selected, dueAt)`. `AssignViewModel(args, loadBoard, loadCatalog, assign, clock = Clock.system(BerlinZone))`, `now` beim Laden setzen.
    Screen: `item { DueDateField(state.dueAt, state.now, { onEvent(AssignEvent.DueDateChanged(it)) }, enabled = !state.isBusy) }` nach `ChildChips`.
  - `CatalogEditUiState` + `childSelectable: Boolean = false`; Event `ChildSelectableChanged(val value: Boolean)`; `loadExisting` übernimmt
    `task.childSelectable`; `save` gibt es in `CatalogInput` weiter. Screen: `Row` mit Text `tasks_field_child_selectable`
    („Für Kinder frei wählbar“) + `Switch` (Muster `ProfileEditScreen.kt:84`) nach dem Punkte-Stepper.
  - `CatalogScreen`: `supportingContent = { if (task.childSelectable) Text(stringResource(R.string.tasks_catalog_child_selectable)) }` („Frei wählbar“).
- **MIRROR**: bestehende Events/Updates in beiden ViewModels.
- **GOTCHA**: `RemoteCatalogRepository.list()` filtert archivierte; `CatalogTask`-Konstruktoren in Tests mit 4 Argumenten bleiben gültig (Default).
- **VALIDATE**: `FlowViewModelsTest`: Zuweisen übergibt Frist; vergangene Frist → `submitError`; Katalog speichert Freigabe; Laden übernimmt sie.
  `TaskScreensTest`: Schalter sichtbar und sendet Event; Katalogliste zeigt „Frei wählbar“.

### Task 12: Pool-Screen und Navigation
- **ACTION**: `ui/pool/PoolViewModel.kt`, `ui/pool/PoolScreen.kt`, `TasksNavigation.kt`, `TasksModule.kt`.
- **IMPLEMENT**:
  - `PoolUiState(tasks: List<CatalogTask> = emptyList(), isLoading = true, error: AppError? = null, busyId: String? = null, actionError: AppError? = null, isDone = false)`,
    `isEmpty` wie Katalog. Events `Retry`, `Take(val catalogTaskId: String, val alreadyDone: Boolean)`. Erfolg → `isDone = true`.
    Fehler 403/409 (nicht mehr freigegeben/archiviert) → `actionError` anzeigen und neu laden.
  - Screen (Muster `AssignScreen`): Titel „Aufgabe nehmen“, je Aufgabe Zeile mit Symbol/Titel/Punkten und `OutlinedButton` „Nehmen“ +
    `Button` „Schon erledigt“; leerer Pool: `tasks_pool_empty` („Gerade ist nichts frei wählbar.“); „Zurück“.
  - `PoolRoute(onDone)` mit `LaunchedEffect(state.isDone) { if (state.isDone) onDone() }`.
  - Navigation: `@Serializable data object PoolRoute`; `composable<PoolRoute> { PoolRoute(onDone = { navController.popBackStack() }) }`.
    Name kollidiert mit Composable `PoolRoute` → wie bestehend (`AssignRoute` gibt es doppelt: Klasse in `TasksNavigation`, Composable in `ui.assign`)
    über Paket trennen.
  - Koin: `factory { LoadPoolUseCase(get()) }`, `factory { TakePoolTaskUseCase(get(), get(), get()) }`, `factory { SetDueDateUseCase(get(), get(), get()) }`,
    `viewModel { PoolViewModel(get(), get()) }`, `BoardViewModel(get(), get(), get(), get(), get())`,
    `single<TaskReminderScheduler> { AlarmTaskReminderScheduler(androidContext()) }`, `factory { ShowTaskReminderUseCase(get(), get(), get()) }`,
    `factory { RestoreTaskRemindersUseCase(get(), get(), get()) }`, `single { get<Retrofit>().create(TaskDueDateApi::class.java) }`.
- **GOTCHA**: Board lädt beim Zurückkehren neu (`LifecycleResumeEffect`) – kein Ergebnis-Rückkanal nötig. `TasksModuleTest` prüft den Koin-Graph.
- **VALIDATE**: `PoolViewModelTest` (in `FlowViewModelsTest` oder eigene Datei): lädt, nimmt (beide Varianten), Fehler; `TaskScreensTest`: Pool-Zeile,
  Buttons senden Events, leerer Pool; `TasksModuleTest` grün.

### Task 13: „Heute“, Strings, Formatierung, Fakes
- **ACTION**: `TodayTasksViewModel/Card`, `strings.xml`, `core/ui/Formatting.kt`, `TT/fakes/Fakes.kt`.
- **IMPLEMENT**:
  - `Formatting.kt`: `private val WeekdayDateTimeFormat = DateTimeFormatter.ofPattern("EE dd.MM. HH:mm", Locale.GERMANY)`,
    `fun formatWeekdayDateTime(instant: Instant): String` („Sa. 01.11. 18:00“).
  - `TodayTasksUiState` + `overdueCount: Int = 0`, `titles: List<TodayTitle>` mit `data class TodayTitle(val title: String, val dueAt: Instant?, val overdue: Boolean)`
    (bisher `List<String>` → Tests anpassen). `TodayTasksViewModel(sessions, loadBoard, clock = Clock.system(BerlinZone))`.
    Kind: `overdueCount` aus eigener Spur; Eltern: `overdueCount(lanes, now)` über alle Spuren.
  - Card: Titel mit „ · bis %1$s“ (`tasks_today_due`) bzw. rot bei überfällig; Zeile `tasks_today_overdue` (Plural „%d überfällig“) wenn > 0 (Kind und Eltern).
  - Neue Strings (deutsch, Muster der Datei): `tasks_action_set_due`, `tasks_action_put_back`, `tasks_card_due`, `tasks_card_overdue`,
    `tasks_card_reported_late`, `tasks_card_self_assigned`, `tasks_due_none` („Frist: keine“), `tasks_due_value` („Frist: %1$s“),
    `tasks_due_remove`, `tasks_due_title`, `tasks_pool_fab`, `tasks_pool_title`, `tasks_pool_take`, `tasks_pool_take_done`, `tasks_pool_empty`,
    `tasks_field_child_selectable`, `tasks_catalog_child_selectable`, `tasks_reminder_title`, `tasks_reminder_text`, `tasks_today_due`,
    Plural `tasks_today_overdue`.
  - Fakes: `card(...)` + `dueAt`, `reportedLate`, `selfAssigned`; `FakeTaskRepository` + `dueDates`, `taken`, `takeResult`, `assign` speichert `dueAt`;
    `FakeCatalogRepository.poolResult`; `FakeTaskNotifier.reminders`; neuer `FakeTaskReminderScheduler(val scheduled: MutableList<List<TaskReminder>>)`.
- **VALIDATE**: `TodayTasksTest` (überfällig/Frist, Eltern-Zahl), `./gradlew :core:ui:testDebugUnitTest` falls Formatierungstest ergänzt.

### Task 14: Doku und Abnahme-Checkliste
- **ACTION**: `docs/03-design/tasks.md` (Abschnitt „App-Seite (Phase 5)“: Bildschirme Pool/Frist-Dialog, Aktionstabelle um
  „Frist ändern“/„Zurücklegen“, Erinnerungen: Einplanung beim Abgleich, ein Alarm je Karte per data-URI, Prüfung im Receiver, Restore,
  IDs ab 200 000, explizites `null` per `TaskDueDateApi`, Cache-Felder), `docs/04-verification/traceability.md` (REQ-TASK-011, 026–028, 031:
  App-Tests eintragen, Status „umgesetzt (App), Abnahme offen“), `docs/01-requirements/tasks.prd.md` (Phase 5 Plan-Spalte, Status bei
  Abschluss), `android/README.md` (neue „Abnahme-Checkliste TASK Phase 5“ = Manual Validation unten).
- **VALIDATE**: Links prüfen; Tabelle konsistent.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-TASK-027 card is overdue from the due instant on` | TODO, `dueAt == now` | überfällig | ja |
| `REQ-TASK-027 reported card is never overdue` | REVIEW, Frist vorbei | nicht überfällig | ja |
| `REQ-TASK-027 rejected card after deadline is overdue again` | TODO + `rejectionReason`, Frist vorbei | überfällig, Todo oben | ja |
| `REQ-TASK-027 overdue cards come first in todo` | 3 Karten | überfällige (nach Frist) vor anderen | – |
| `REQ-TASK-026 due instant uses Berlin time across DST` | 2026-10-25 18:00 | `17:00Z` | ja |
| `REQ-TASK-026 assigning with a past due date fails without a call` | `dueAt < now` | `Validation`, `assigned` leer | ja |
| `REQ-TASK-026 removing the due date sends explicit null` | `setDueDate(id, null)` | Body `{"dueAt":null}` | ja |
| `REQ-TASK-026 parent sees set due date only for assigned cards` | Eltern, TODO selfAssigned | kein `SET_DUE_DATE` | ja |
| `REQ-TASK-028 child can put back own self-assigned todo` | Kind, eigene selbst genommene TODO | `PUT_BACK, REPORT` | – |
| `REQ-TASK-028 child cannot put back assigned card` | Kind, zugewiesene TODO | nur `REPORT` | ja |
| `REQ-TASK-028 taking from the pool adds the card to the cache` | Take Erfolg | Karte im Cache, keine „Neue Aufgabe“ | – |
| `REQ-TASK-028 already done card notifies parents` | neue REVIEW-Karte | `Reported` | – |
| `REQ-TASK-011 catalog edit saves child selectable` | Schalter an | `CatalogInput.childSelectable = true` | – |
| `REQ-TASK-031 child sync schedules reminder two hours before due` | TODO, Frist +5 h | Erinnerung bei +3 h | – |
| `REQ-TASK-031 no reminder when less than two hours remain` | Frist +1 h | keine | ja |
| `REQ-TASK-031 reminder is dropped after report` | Alarm, Karte REVIEW im Cache | keine Benachrichtigung | ja |
| `REQ-TASK-031 reminder is dropped when due date changed` | Alarm-Frist ≠ Cache-Frist | keine | ja |
| `REQ-TASK-031 restore schedules future reminders from cache` | Cache mit Frist | `scheduled` enthält sie | – |
| `REQ-TASK-031 same card reschedules a single alarm` | zweimal planen | ein Alarm, neue Zeit | ja |

### Edge Cases Checklist
- [ ] Leerer Pool / leerer Katalog
- [ ] Unlesbares `dueAt` vom Server → `AppError.Server`
- [ ] Alter Cache ohne neue Felder (Version 1) lesbar
- [ ] Offline: „überfällig“ aus Cache + Gerätuhr; Aktionen (Frist, Nehmen, Zurücklegen) ausgeblendet
- [ ] 403/409 beim Nehmen (Freigabe zurückgenommen, archiviert) → Fehlermeldung + Neuladen
- [ ] Zwei Abgleiche gleichzeitig (Mutex vorhanden) → keine doppelten Alarme (idempotent)
- [ ] Keine Benachrichtigungs-Berechtigung → nichts passiert
- [ ] Abgemeldet → Alarm-Receiver tut nichts

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Befunde

### Unit Tests
```bash
cd android && ./gradlew :feature:tasks:testDebugUnitTest :core:ui:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug
```
EXPECT: grün, Kover-Schwelle gehalten

### Manual Validation (Abnahme TASK Phase 5 – zwei Testhandys gegen die Testinstanz)
- [ ] Eltern: Katalogaufgabe „Mülleimer leeren“ → „Für Kinder frei wählbar“ an; Liste zeigt „Frei wählbar“.
- [ ] Eltern: Aufgabe Ben zuweisen mit Frist in ~2,5 h; Kind sieht „Frist: …“ auf Board und „Heute“.
- [ ] Kind-Handy offline schalten (WLAN aus); nach ~30 min (+ bis 1 h Verschiebung) kommt „Frist bald: …“.
- [ ] Nach Fristende: Karte rot „Überfällig seit …“, oben in Todo, „Heute“ zeigt „1 überfällig“ (auch offline).
- [ ] Kind meldet nach Fristende → Abnahme zeigt „Zu spät gemeldet“; Eltern nehmen ab → volle Punkte.
- [ ] Eltern: Frist an einer Karte ändern und entfernen (Menü „Frist ändern“); entfernt → keine Erinnerung mehr nach Abgleich.
- [ ] Kind: „Aufgabe nehmen“ → „Schon erledigt“ → Karte in Abnahme mit „Selbst genommen“; Elternhandy bekommt Hinweis; abnehmen.
- [ ] Kind: „Nehmen“ → Karte in Todo; über Menü „Zurücklegen“ → verschwunden. Zugewiesene Karte hat kein „Zurücklegen“.
- [ ] Eltern: Freigabe ausschalten → Pool beim Kind leer; Nehmen eines veralteten Eintrags zeigt Fehler.
- [ ] Neustart des Kindhandys mit künftiger Frist → Erinnerung kommt trotzdem.

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Alle Validierungskommandos grün
- [ ] Tests mit REQ-ID im Namen, Traceability gepflegt
- [ ] Keine Lint-/detekt-Befunde
- [ ] UX wie oben

## Completion Checklist
- [ ] Code folgt den Mustern (Use Case + Cache-Mitführung, `apiCall`, State/Event-ViewModels)
- [ ] Fehler wie bisher über `AppError`/`ErrorText`
- [ ] Logging nur in `data/` (`Log.w`)
- [ ] Keine Magic Numbers (`TaskLimits`)
- [ ] Doku aktualisiert (Design, Traceability, PRD, README-Checkliste)
- [ ] Kein Scope-Zuwachs

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| `dueAt: null` wird wegen `explicitNulls = false` weggelassen → 400 | hoch (ohne Gegenmaßnahme sicher) | M | `TaskDueDateApi` mit `JsonObject`; Test prüft Body exakt |
| Alarme teilen sich einen PendingIntent | M | H | data-URI je Karte; Scheduler-Test mit zwei Karten |
| Erinnerung verspätet/fehlt (Doze, Herstelleroptimierung, Abgleich nur alle 30 min) | M | M | ADR-0013 akzeptiert ±1 h; Abnahme auf Testhandys; Abgleich beim App-Start |
| detekt `LongParameterList`/`TooManyFunctions`/`LongMethod` durch Erweiterungen | M | L | Funktionen aufteilen, Use Cases bündeln; früh `./gradlew detekt` |
| Picker-Zeitzonenfehler (UTC-Mitternacht) | M | M | Umrechnung als reine Funktion mit Tests (inkl. Zeitumstellung) |
| Bestehende Tests brechen durch geänderte Signaturen (`sections`, `openCards`, `titles`) | hoch | L | Defaults wo möglich; Tests in denselben Tasks anpassen |

## Notes
- Erinnerungen werden **nur** beim Hintergrund-Abgleich (`SyncTaskNoticesUseCase`, alle 30 min + App-Start + Restore) eingeplant, nicht
  beim Öffnen des Boards – entspricht AC „sofern das Gerät seither abgeglichen hat“ und hält `LoadBoardUseCase` unverändert.
- „zu spät gemeldet“ und „selbst genommen“ kommen vom Server; „überfällig“ rechnet nur die App (Backend-Design Phase 4).
- Pool-Freigabe sieht nur, wer den Katalog bearbeitet (Eltern); `GET /pool` dürfen alle Rollen lesen, die App zeigt den Pool nur Kindern.
- Implementierung in einer neuen Session (Sonnet): `/ecc:prp-implement .claude/PRPs/plans/fristen-pool-app.plan.md`.
