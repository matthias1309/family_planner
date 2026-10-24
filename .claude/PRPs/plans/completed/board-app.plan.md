# Plan: Aufgaben-Board in der App (TASK Phase 3)

## Summary
Neues Android-Feature-Modul `feature:tasks` bildet das Küchenboard auf dem Handy nach: Board mit einer Spur je Kind
(Todo · Zur Abnahme · Erledigt), Aktionen je Rolle, Aufgabenkatalog und Zuweisen für Eltern, Verlauf, Offline-Lesen aus einem
lokalen Stand (ADR-0011), Aufgaben-Karte auf „Heute“, Abnahme-Badge an der Leiste und Hinweise per Hintergrund-Abgleich
(ADR-0013). Der Platzhalter „Aufgaben“ aus Phase 1 wird ersetzt. Keine Backend- oder API-Änderung — die API aus Phase 2
(`/api/v1/tasks/*`, `openapi.yaml` 0.6.0) wird unverändert genutzt.

## User Story
Als Elternteil möchte ich Aufgaben aus dem Katalog mit zwei Tippern einem Kind geben und gemeldete Aufgaben abnehmen,
als Kind meine Aufgaben sehen und als erledigt melden — auf dem Handy statt an der Magnettafel.

## Problem → Solution
Backend-Board existiert, App zeigt „Kommt bald“ → vollständiges Board in der App, inklusive Hinweisen und Badge, sodass das
Küchenboard im Testbetrieb ersetzt werden kann.

## Metadata
- **Complexity**: Large (≈ 45 Dateien, neues Feature-Modul, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/tasks.prd.md`
- **PRD Phase**: Phase 3 — Board App
- **Requirements**: REQ-TASK-002 (Aufgaben-Teil), 003, 010 (App), 020–025 (App), 030
- **Version**: App 0.9.0 / `versionCode` 10

---

## Entscheidungen (Planung 2026-10-24)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Modul | `android/feature/tasks`, Paket `de.familyplanner.android.feature.tasks`, Aufbau wie `feature:meals` (`domain/`, `data/`, `ui/`) | ADR-0001, ADR-0009; `meals` und `tasks` kennen sich nicht |
| Board-Darstellung | **Eine Spur pro Bildschirm** mit Reitern oben; Spalten als Abschnitte untereinander (Todo · Zur Abnahme · Erledigt) | PRD-Risiko „3 Spalten × 3 Kinder zu eng“; Handy hochkant |
| Reiter Kind | Eigene Spur zuerst, dann Geschwister (nur lesen) | REQ-TASK-021 |
| Reiter Eltern | Erster Reiter **„Zur Abnahme (n)“** über alle Kinder, danach je Kind ein Reiter | REQ-TASK-021 „Sammelansicht“ |
| Aktionen | Buttons an der Karte, kein Drag & Drop | PRD „Could“ |
| Aktionen je Karte | Kind, eigene Todo-Karte: „Erledigt“; eigene Abnahme-Karte: „Zurückziehen“. Eltern, Abnahme-Karte: „Abnehmen“ / „Zurückweisen“; Todo-Karte: Menü „Direkt abnehmen“ / „Entfernen“; Abnahme-Karte im Menü zusätzlich „Entfernen“ | REQ-TASK-022–024 |
| Zurückweisen | Dialog mit **optionaler** Begründung (≤ 200) | API: Body optional |
| Entfernen / Direkt abnehmen | Bestätigungsdialog (`ConfirmDialog` aus `core:ui`) | Nicht umkehrbar |
| Zuweisen | Bildschirm „Aufgaben zuweisen“: Kind wählen (vorbelegt mit dem aktuellen Reiter), Katalogaufgaben per Häkchen, „Zuweisen (n)“; jede Aufgabe einmal pro Vorgang | KISS; Doppelte durch zweiten Vorgang möglich (API erlaubt es) |
| Katalog | Eigener Bildschirm (Eltern, über Symbol oben im Board): Liste aktiver Aufgaben, „Neu“, Antippen = bearbeiten, Archivieren im Editor; archivierte werden nicht angezeigt | REQ-TASK-010; Archiv-Ansicht YAGNI |
| Punkte im Editor | Stepper 1–100 (Standard 1) | Wie `ServingsStepper` in meals |
| Verlauf | Abschnitt „Erledigt“ hat „Verlauf anzeigen“ → Liste der letzten 50 | REQ-TASK-025 |
| Offline | Board aus lokalem Stand (DataStore, je Mitglied), Hinweis mit Sync-Zeit; alle Aktionen offline deaktiviert | ADR-0011, wie Essensplan |
| Hinweise | Eigener Hintergrund-Abgleich in `feature:tasks` (WorkManager, 30 min + sofort beim App-Start), eigener Kanal „Aufgaben“; Vergleich mit dem lokalen Stand | ADR-0013-Muster; kein Umbau des Meals-Workers |
| Wer wird benachrichtigt | Eltern: Karte neu in „Zur Abnahme“ („Lea hat „Bad putzen“ erledigt“). Kind (nur eigene Spur): neue Karte, abgenommen (mit Punkten), zurückgewiesen (mit Grund) | REQ-TASK-030; eigene Aktionen erzeugen nach diesen Regeln nie einen Hinweis an sich selbst |
| Erster Abgleich | Ohne vorherigen Stand nur Basislinie, keine Hinweise | Wie REQ-MEAL-052 |
| Badge | Zahl der Karten in „Zur Abnahme“ aus dem lokalen Stand, nur Eltern, an „Aufgaben“ | REQ-TASK-003 |
| Tippen auf Hinweis | Öffnet „Aufgaben“ (`TaskOpenRequests`, wie `PlanOpenRequests`) | Einheitliches Muster |
| „Heute“ | Zweite Karte unter dem Gericht: Kind „Deine Aufgaben“ (Anzahl offen, bis zu 3 Titel, zurückgewiesene markiert); Eltern „n Aufgaben warten auf Abnahme“. Tippen → „Aufgaben“ | REQ-TASK-002 |
| ADR | Kein neues ADR; Design-Doku `tasks.md` Abschnitt „App-Seite (Phase 3)“, Verweis auf ADR-0011/0013/0014 | Keine neue Architekturentscheidung |

---

## UX Design

### Before
```
Aufgaben (Leiste)
┌─────────────────────────────┐
│ Aufgaben                    │
│ Hier entsteht das           │
│ Aufgaben-Board.             │
└─────────────────────────────┘
```

### After
```
Kind (Lea)                                Eltern
┌─────────────────────────────┐           ┌─────────────────────────────┐
│ Aufgaben                    │           │ Aufgaben              [▤ Katalog]
│ [ Lea ] [ Ben ] [ Mia ]     │           │ [Zur Abnahme (2)] [Lea] [Ben]│
│ Todo                        │           │ ┌─────────────────────────┐ │
│ ┌─────────────────────────┐ │           │ │ 🛁 Bad putzen · 3 P.    │ │
│ │ 🍽 Spülmaschine · 1 P.  │ │           │ │ Lea · gemeldet 14:20    │ │
│ │          [ Erledigt ]   │ │           │ │ [Zurückweisen][Abnehmen]│ │
│ └─────────────────────────┘ │           │ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │           │ ...                         │
│ │ 🧹 Zimmer · 2 P.        │ │           │                             │
│ │ Zurückgewiesen: „Unter  │ │           │                             │
│ │ dem Bett fehlt noch“    │ │           │                             │
│ │          [ Erledigt ]   │ │           │                   (+ Zuweisen)│
│ └─────────────────────────┘ │           │                             │
│ Zur Abnahme                 │           │                             │
│ │ 🛁 Bad · 3 P. [Zurückz.] │ │           │                             │
│ Erledigt (7 Tage)           │           │                             │
│ │ ✓ Müll · 1 P. · Mo 13:05 │ │           │                             │
│ [ Verlauf anzeigen ]        │           │                             │
├─────────────────────────────┤           ├─────────────────────────────┤
│ Heute  Essen  Aufgaben  Ich │           │ Heute Essen Aufgaben(2) Familie│
└─────────────────────────────┘           └─────────────────────────────┘
Offline: Hinweis „Offline – Stand vom …“, Buttons ausgegraut.
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Leiste „Aufgaben“ | Platzhalter | Board | Eltern: Badge mit Anzahl „Zur Abnahme“ |
| „Heute“ | nur Gericht | + Aufgaben-Karte | Tippen → Aufgaben |
| Kind meldet | Magnet schieben | „Erledigt“ an der Karte | Karte wandert nach „Zur Abnahme“ |
| Eltern nehmen ab | Magnet schieben | „Abnehmen“ | Karte → Erledigt; Punkte folgen mit GAME |
| Eltern weisen zu | Magnetschild aufhängen | FAB „Zuweisen“ → Kind + Häkchen | |
| Katalog pflegen | Schilder basteln | Board → Symbol „Katalog“ | |
| Hinweise | – | Benachrichtigung im Kanal „Aufgaben“ | Tippen öffnet Aufgaben |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | all | Platzhalter ersetzen, Slot „Heute“, Deep-Link-Muster |
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/TopLevelDestination.kt` | all | `TASKS`-Route, Navigation |
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavigationBar.kt` | all | Badge ergänzen |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt` | 1-60 | Remote + Cache-Fallback (`WeekPlanLoad`) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/DataStorePlanCache.kt`, `PlanCacheSerializer.kt`, `StoredPlan.kt` | all | DataStore-Stand je Mitglied, Version, Fehler → leer |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/reminder/UseCases.kt` | 1-110 | `SyncRemindersUseCase`: Mutex, `awaitSession`, Basislinie, Unauthorized → leeren |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/reminder/ReminderRules.kt` | 31-56 | `statusNotices`: reine Vergleichsregeln |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/reminder/WorkManagerReminderSync.kt`, `AndroidNotifier.kt` | all | Worker, Kanal, Berechtigung, PendingIntent |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/shopping/ShoppingListViewModel.kt` | all | State/Event-ViewModel mit `isBusy`/`actionError`, späte Antworten verwerfen |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/RemoteCookingRepository.kt` | all | `apiCall`/`apiCallNoContent` mit generiertem Client |
| P1 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt` | all | Fehler-Mapping (`AppError.Conflict(message)` usw.) |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | all | Koin: `TasksApi` per Retrofit, DataStore genau einmal |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/PlanOpenRequests.kt` | all | Muster für `TaskOpenRequests` |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/MainActivity.kt`, `FamilyPlannerApp.kt` | all | Intent-Extras, `schedulePeriodic`, `requestNow` in `onStart` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/today/TodayMealCard.kt` | all | Karte für „Heute“ |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/ReasonDialog.kt` | all | Dialog-Aufbau (Begründung) |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipe/ServingsStepper.kt` | all | Stepper für Punkte |
| P1 | `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/components/Components.kt` | all | `SubmitButton`, `ErrorText`, `ConfirmDialog` |
| P1 | `android/feature/meals/build.gradle.kts`, `android/settings.gradle.kts`, `android/app/build.gradle.kts` | all | Modul anlegen, Kover |
| P2 | `api/openapi.yaml` | 676-920, Schemas `TaskStatus` … `TaskBoard` | Vertrag (Phase 2) |
| P2 | `docs/03-design/tasks.md` | all | Backend-Regeln, Grenzen |
| P2 | `android/feature/meals/src/test/.../fakes/ReminderFakes.kt`, `PlanFakes.kt`, `MealsModuleTest.kt` | all | Fakes, Koin-Verifikation |
| P2 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | all | Koin-Testmodul, Navigationstests |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Badge in NavigationBar | developer.android.com Compose `BadgedBox` | `icon = { BadgedBox(badge = { if (n > 0) Badge { Text("$n") } }) { Icon(...) } }` |
| Tabs | Compose Material 3 `PrimaryScrollableTabRow` / `Tab` | Scrollbar bei 4 Reitern auf schmalen Displays |
| Generierter Retrofit-Client | `android/core/api` (openapi-generator `jvm-retrofit2`, Tag `tasks`) | Klasse `de.familyplanner.api.client.TasksApi`, Methoden nach `operationId` (`getTaskBoard`, `acceptTask`, `rejectTask(cardId, RejectTaskRequest?)` …) |

GOTCHA (geprüft am generierten `TasksApi.kt`, 2026-10-24): `rejectTask(cardId, @Body rejectTaskRequest: RejectTaskRequest? = null)`.
Retrofit wirft bei einem `null`-`@Body` eine `IllegalArgumentException` → **immer** `RejectTaskRequest(reason)` senden, auch mit
`reason = null`. Die App-JSON hat `explicitNulls = false` (`NetworkModule.kt:32-35`) → Body `{}`; das Backend liest dann „kein Grund“
(`TaskRoutes.kt:121-123`). Weitere Signaturen: `getTaskHistory(memberId, limit: Int? = 50)`, `listCatalogTasks(includeArchived: Boolean? = false)`,
`archiveCatalogTask`/`removeTaskCard` → `Response<Unit>`, Übergänge → `Response<TaskCardResponse>`, `assignTasks` → `TaskCardListResponse`.

---

## Patterns to Mirror

### NAMING / MODUL
```kotlin
// SOURCE: android/feature/meals/build.gradle.kts
plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}
android { namespace = "de.familyplanner.android.feature.meals" }
dependencies {
    api(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.compose)
    implementation(libs.datastore)
    implementation(libs.work.runtime)
    implementation(libs.core.ktx)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons)
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver)
}
```
App: KDoc/Kommentare **deutsch**, Bezeichner englisch; Konstanten in `object TaskLimits` (wie `PlanLimits`).

### REMOTE_REPOSITORY
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/RemoteCookingRepository.kt
class RemoteCookingRepository(private val meals: MealsApi, private val json: Json) : CookingRepository {
    override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> =
        apiCall(json, { meals.getWeekStatus(weekStart.toString()) }) { it.data?.toDomain() }

    override suspend fun approve(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.approveDish(dishId) }
```
Übergänge antworten mit `200` + Karte → `apiCall(json, { tasks.acceptTask(id) }) { it.data?.toDomain() }`. Entfernen/Archivieren `204` → `apiCallNoContent`.

### CACHE_FALLBACK
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt:18-40
/** Verbindungsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
private fun AppError.allowsCachedPlan() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

suspend operator fun invoke(weekStart: LocalDate): WeekPlanLoad {
    val memberId = sessions.current?.memberId ?: return WeekPlanLoad.Failed(AppError.Unauthorized)
    return when (val result = loadRemote(weekStart)) {
        is ApiResult.Success -> { cache.write(memberId, result.data, …); WeekPlanLoad.Fresh(result.data) }
        is ApiResult.Failure -> {
            val cached = if (result.error.allowsCachedPlan()) cache.read(memberId, weekStart) else null
            if (cached != null) WeekPlanLoad.Cached(cached, result.error) else WeekPlanLoad.Failed(result.error)
        }
    }
}
```

### DATASTORE
```kotlin
// SOURCE: DataStorePlanCache.kt:19-29 / PlanCacheSerializer.kt:18-30
override suspend fun read(memberId: String, weekStart: LocalDate): WeekPlanSnapshot? =
    try {
        val stored = dataStore.data.first()
        if (stored.memberId != memberId) null else stored.weeks.firstOrNull { … }?.toDomain()
    } catch (e: IOException) {
        Log.w(TAG, "Meal plan cache unreadable", e)
        null
    }

override suspend fun readFrom(input: InputStream): StoredPlanCache {
    val bytes = input.readBytes()
    if (bytes.isEmpty()) return defaultValue
    return try {
        json.decodeFromString<StoredPlanCache>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
    } catch (e: SerializationException) { Log.w(TAG, "…", e); defaultValue } catch (e: IllegalArgumentException) { … }
}
```
Dateiformat getrennt von der Domäne (`@Serializable data class Stored…`), Zeitpunkte als Epoch-Millis.

### SYNC_USE_CASE
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/reminder/UseCases.kt
private suspend fun SessionStore.awaitSession(): Session? = (state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session

class SyncRemindersUseCase(...) {
    // Periodischer und sofortiger Abgleich können gleichzeitig laufen; sonst gäbe es doppelte Meldungen.
    private val mutex = Mutex()
    suspend operator fun invoke(): SyncOutcome =
        mutex.withLock {
            val session = sessions.awaitSession() ?: return@withLock signOut()
            val remote = when (val result = load(session, week)) {
                is ApiResult.Success -> result.data
                is ApiResult.Failure -> return@withLock if (result.error == AppError.Unauthorized) signOut() else SyncOutcome.UNREACHABLE
            }
            apply(session, week, remote)
            SyncOutcome.SYNCED
        }
```

### NOTICE_RULES (rein)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/reminder/ReminderRules.kt:31-47
fun statusNotices(isParent: Boolean, memberId: String, previous: Map<String, KnownDish>?, dishes: List<Dish>, names: Map<String, String>): List<StatusNotice> {
    if (previous == null) return emptyList()
    return dishes.mapNotNull { dish ->
        val before = previous[dish.id]
        when {
            isParent -> dish.takeIf { it.status == DishStatus.SUBMITTED && before?.status != DishStatus.SUBMITTED }?.toSubmitted(names)
            dish.cookId != memberId || before == dish.known() -> null
            else -> dish.toChildNotice()
        }
    }
}
```

### WORKER / NOTIFIER
```kotlin
// SOURCE: WorkManagerReminderSync.kt
private const val PERIODIC_WORK = "meal-reminder-sync-periodic"
override fun schedulePeriodic() {
    workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP,
        PeriodicWorkRequestBuilder<ReminderSyncWorker>(ReminderLimits.SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES).setConstraints(connected).build())
}
class ReminderSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {
    private val sync: SyncRemindersUseCase by inject()
    // Nicht erreichbar ist kein Fehler: Der nächste Lauf versucht es erneut.
    override suspend fun doWork(): Result { sync(); return Result.success() }
}
```
```kotlin
// SOURCE: AndroidNotifier.kt — show(): canNotify() prüfen, Kanal anlegen, BigTextStyle, AutoCancel, PendingIntent mit Extra
@Suppress("MissingPermission")
manager.notify(id, notification)
```

### VIEWMODEL (State/Event)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/shopping/ShoppingListViewModel.kt
data class ShoppingListUiState(..., val isBusy: Boolean = false, val actionError: AppError? = null) {
    val canEdit: Boolean get() = isParent && list != null
}
sealed interface ShoppingListEvent { data object Refresh : ShoppingListEvent; data class DeleteItem(val itemId: String) : ShoppingListEvent }
fun onEvent(event: ShoppingListEvent) { when (event) { … } }
// Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
```

### INBOX_NAVIGATION
```kotlin
// SOURCE: AppNavHost.kt (MainHost)
val openPlan by planRequests.pending.collectAsStateWithLifecycle()
LaunchedEffect(openPlan) {
    if (openPlan) { planRequests.consume(); navController.openMealsAndThen { openWeekPlan() } }
}
```

### TEST_STRUCTURE
Fakes in `src/test/.../fakes/` (keine Mocks), `runTest`, `kotlin.test`, REQ-ID am Namensanfang, `Clock.fixed(…, BerlinZone)`,
Robolectric für Screens/Notifier, `InMemorySessionStore` aus `:core:testing`, `MainDispatcherRule` für ViewModels,
`MockWebServer` für Remote-Repositories (siehe `RemoteMealPlanRepositoryTest`), Koin-Verifikation wie `MealsModuleTest`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/settings.gradle.kts` | UPDATE | `":feature:tasks"` |
| `android/feature/tasks/build.gradle.kts` | CREATE | wie meals |
| `android/feature/tasks/src/main/AndroidManifest.xml` | CREATE | wie `feature/meals/src/main/AndroidManifest.xml` (existiert), ohne Receiver |
| `…/tasks/TasksModule.kt` | CREATE | Koin |
| `…/tasks/TasksNavigation.kt` | CREATE | Routen, `tasksGraph`, `TasksHomeRoute` |
| `…/tasks/TaskOpenRequests.kt` | CREATE | Tippen auf Hinweis |
| `…/tasks/domain/board/Models.kt` | CREATE | `TaskStatus`, `TaskCard`, `TaskLane`, `BoardSnapshot`, `BoardLoad` |
| `…/tasks/domain/board/TaskLimits.kt` | CREATE | Grenzen (Titel 40, Emoji 16, Punkte 1–100, Grund 200, Zuweisen 20, Verlauf 50, Sync 30 min) |
| `…/tasks/domain/board/BoardRules.kt` | CREATE | Aktionen je Rolle/Status, Sortierung, Reiter, Badge-Zahl (rein) |
| `…/tasks/domain/board/Ports.kt` | CREATE | `TaskRepository`, `CatalogRepository`, `BoardCache`, `TaskNotifier`, `TaskSync` |
| `…/tasks/domain/board/UseCases.kt` | CREATE | Laden mit Cache, Übergänge, Zuweisen, Verlauf |
| `…/tasks/domain/catalog/Models.kt`, `Validation.kt`, `UseCases.kt` | CREATE | Katalog |
| `…/tasks/domain/notice/NoticeRules.kt`, `SyncTaskNoticesUseCase.kt` | CREATE | REQ-TASK-030 |
| `…/tasks/data/RemoteTaskRepository.kt`, `RemoteCatalogRepository.kt`, `Mappers.kt` | CREATE | API |
| `…/tasks/data/DataStoreBoardCache.kt`, `BoardCacheSerializer.kt`, `StoredBoard.kt` | CREATE | Lokaler Stand |
| `…/tasks/data/WorkManagerTaskSync.kt`, `AndroidTaskNotifier.kt` | CREATE | Hintergrund + Benachrichtigung |
| `…/tasks/ui/board/BoardViewModel.kt`, `BoardScreen.kt`, `TaskCardItem.kt`, `RejectDialog.kt` | CREATE | Board |
| `…/tasks/ui/assign/AssignViewModel.kt`, `AssignScreen.kt` | CREATE | Zuweisen |
| `…/tasks/ui/catalog/CatalogViewModel.kt`, `CatalogScreen.kt`, `CatalogEditViewModel.kt`, `CatalogEditScreen.kt`, `PointsStepper.kt` | CREATE | Katalog |
| `…/tasks/ui/history/HistoryViewModel.kt`, `HistoryScreen.kt` | CREATE | Verlauf |
| `…/tasks/ui/today/TodayTasksViewModel.kt`, `TodayTasksCard.kt` | CREATE | „Heute“ |
| `…/tasks/ui/ReviewBadge.kt` | CREATE | Badge-Zahl als `Flow<Int>` |
| `android/feature/tasks/src/main/res/values/strings.xml`, `res/drawable/ic_stat_tasks.xml` | CREATE | Texte, Benachrichtigungssymbol |
| `android/feature/tasks/src/test/…` | CREATE | siehe Testing Strategy |
| `android/app/build.gradle.kts` | UPDATE | Abhängigkeit, Kover-Filter, Version 0.9.0/10 |
| `android/app/src/main/kotlin/de/familyplanner/android/app/TopLevelDestination.kt` | UPDATE | `TASKS` → `TasksHomeRoute` |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavigationBar.kt` | UPDATE | Badge |
| `android/app/src/main/kotlin/de/familyplanner/android/app/PlaceholderScreens.kt` | UPDATE | `TasksRoute` entfernen |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | `tasksGraph`, „Heute“-Slot, `TaskOpenRequests`, Badge |
| `android/app/src/main/kotlin/de/familyplanner/android/app/MainActivity.kt` | UPDATE | Extra `OPEN_TASKS`, `TaskSync.requestNow()` |
| `android/app/src/main/kotlin/de/familyplanner/android/app/FamilyPlannerApp.kt` | UPDATE | `tasksModule`, `TaskSync.schedulePeriodic()` |
| `android/app/src/main/res/values/strings.xml` | UPDATE | `tasks_coming_soon` entfernen |
| `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | UPDATE | Koin-Testmodul + neue Tests |
| `android/app/src/test/kotlin/de/familyplanner/android/app/MainDestinationsTest.kt` | UPDATE | `TASKS`-Route |
| `docs/03-design/tasks.md` | UPDATE | „App-Seite (Phase 3)“ |
| `docs/04-verification/traceability.md` | UPDATE | REQ-TASK-002/003/010/020–025/030 |
| `android/README.md` | UPDATE | Abnahme-Checkliste TASK Phase 3 |
| `docs/01-requirements/tasks.prd.md` | UPDATE | Bericht-Verweis |

## NOT Building

- Keine Backend-/API-Änderung (`git diff main -- api/ backend/` leer).
- Keine Punkte, Rangliste, Badges (GAME Phase 1 ff.).
- Kein Drag & Drop, keine Archiv-Ansicht im Katalog, kein Wiederherstellen archivierter Aufgaben.
- Kein mehrfaches Zuweisen derselben Aufgabe in einem Vorgang (Mengenwähler).
- Keine Offline-Warteschlange für Aktionen (Aktionen nur im Heimnetz).
- Kein Umbau des Meals-Abgleichs zu einem gemeinsamen Worker.
- Keine Benachrichtigung an Eltern, wenn der andere Elternteil abnimmt.

---

## Step-by-Step Tasks

### Task 1: Modul anlegen
- **ACTION**: `feature:tasks` als Gradle-Modul, in `settings.gradle.kts` und `app/build.gradle.kts` eintragen.
- **IMPLEMENT**: `build.gradle.kts` = Kopie von `feature/meals/build.gradle.kts` mit Namespace `de.familyplanner.android.feature.tasks`.
  `app`: `implementation(project(":feature:tasks"))`, `kover(project(":feature:tasks"))`, Kover-`classes(...)` um
  `"*TaskSyncWorker*"`, `"*WorkManagerTaskSync*"` ergänzen (Kommentar wie bei Reminder: nur auf dem Gerät prüfbar, ADR-0013).
  Version `versionCode = 10`, `versionName = "0.9.0"`.
- **GOTCHA**: `feature/meals/src/main/AndroidManifest.xml` existiert → Pendant ohne Receiver anlegen (WorkManager braucht keinen
  Eintrag). Ressourcen-Namen mit Präfix `tasks_`/`task_`, damit sie nicht mit meals kollidieren (Android mergt `R`-Ressourcen der App).
- **VALIDATE**: `./gradlew :feature:tasks:assembleDebug`.

### Task 2: Domäne Board und Katalog (rein)
- **ACTION**: Modelle, Grenzen, Regeln, Ports.
- **IMPLEMENT**:
  - `domain/board/Models.kt`:
    ```kotlin
    enum class TaskStatus { TODO, REVIEW, DONE }
    data class TaskCard(
        val id: String, val memberId: String, val title: String, val emoji: String?, val points: Int, val status: TaskStatus,
        val rejectionReason: String?, val assignedAt: Instant, val reportedAt: Instant?, val doneAt: Instant?,
    )
    data class TaskLane(val memberId: String, val name: String, val cards: List<TaskCard>)
    /** Board vom Server oder aus dem lokalen Stand (ADR-0011). */
    data class BoardSnapshot(val lanes: List<TaskLane>, val syncedAt: Instant)
    sealed interface BoardLoad {
        data class Fresh(val board: BoardSnapshot) : BoardLoad
        data class Cached(val board: BoardSnapshot, val error: AppError) : BoardLoad
        data class Failed(val error: AppError) : BoardLoad
    }
    ```
  - `TaskLimits`: `TITLE_MAX = 40`, `EMOJI_MAX = 16`, `POINTS_MIN = 1`, `POINTS_MAX = 100`, `POINTS_DEFAULT = 1`, `REASON_MAX = 200`,
    `ASSIGN_MAX = 20`, `HISTORY_LIMIT = 50`, `TODAY_TITLES = 3`, `SYNC_INTERVAL_MINUTES = 30L`.
  - `BoardRules.kt` (reine Funktionen, getestet):
    ```kotlin
    enum class CardAction { REPORT, WITHDRAW, ACCEPT, REJECT, ACCEPT_DIRECTLY, REMOVE }
    /** Aktionen, die [viewer] an [card] sieht (REQ-TASK-022–024); offline keine. */
    fun cardActions(card: TaskCard, viewerId: String, isParent: Boolean, isOffline: Boolean): List<CardAction>
    //   offline → emptyList; Kind: eigene TODO → [REPORT], eigene REVIEW → [WITHDRAW], sonst []
    //   Eltern: REVIEW → [ACCEPT, REJECT, REMOVE], TODO → [ACCEPT_DIRECTLY, REMOVE], DONE → []
    data class LaneSections(val todo: List<TaskCard>, val review: List<TaskCard>, val done: List<TaskCard>)
    /** TODO nach Zuweisung (älteste zuerst), REVIEW nach Meldung (älteste zuerst), DONE neueste zuerst. */
    fun sections(cards: List<TaskCard>): LaneSections
    /** Alle gemeldeten Karten über alle Kinder, älteste Meldung zuerst (Sammelansicht der Eltern). */
    fun reviewQueue(lanes: List<TaskLane>): List<Pair<TaskLane, TaskCard>>
    fun reviewCount(lanes: List<TaskLane>): Int
    /** Reihenfolge der Spuren: für ein Kind die eigene zuerst, sonst wie vom Server (nach Name). */
    fun orderedLanes(lanes: List<TaskLane>, viewerId: String, isParent: Boolean): List<TaskLane>
    ```
  - `domain/catalog/Models.kt`: `CatalogTask(id, title, emoji, points)`, `CatalogInput(title, emoji, points)`.
    `Validation.kt`: `fun validateCatalogInput(input): AppError.Validation?` (Titel trim 1–40, Emoji trim ≤ 16 → leer = `null`,
    Punkte 1–100) — deutsche Texte wie Backend („Titel muss zwischen 1 und 40 Zeichen lang sein“, „Symbol ist zu lang“,
    „Punkte müssen zwischen 1 und 100 liegen“).
  - `Ports.kt`:
    ```kotlin
    interface TaskRepository {
        suspend fun board(): ApiResult<List<TaskLane>>
        suspend fun history(memberId: String, limit: Int): ApiResult<List<TaskCard>>
        suspend fun assign(memberId: String, catalogTaskIds: List<String>): ApiResult<List<TaskCard>>
        suspend fun report(cardId: String): ApiResult<TaskCard>
        suspend fun withdraw(cardId: String): ApiResult<TaskCard>
        suspend fun accept(cardId: String): ApiResult<TaskCard>
        suspend fun reject(cardId: String, reason: String?): ApiResult<TaskCard>
        suspend fun remove(cardId: String): ApiResult<Unit>
    }
    interface CatalogRepository {
        suspend fun list(): ApiResult<List<CatalogTask>>
        suspend fun create(input: CatalogInput): ApiResult<CatalogTask>
        suspend fun update(id: String, input: CatalogInput): ApiResult<CatalogTask>
        suspend fun archive(id: String): ApiResult<Unit>
    }
    interface BoardCache {
        suspend fun read(memberId: String): BoardSnapshot?
        suspend fun write(memberId: String, board: BoardSnapshot)
        suspend fun clear()
        /** Für das Badge: letzter Stand des angemeldeten Mitglieds, `null` ohne Stand. */
        fun observe(): Flow<StoredBoardView?>
    }
    interface TaskNotifier { fun announce(notice: TaskNotice) }
    /** Stößt den Abgleich im Hintergrund an (WorkManager, ADR-0013). */
    interface TaskSync { fun requestNow(); fun schedulePeriodic() }
    ```
    (`StoredBoardView` = `memberId` + `BoardSnapshot`; einfacher: `observe(): Flow<Pair<String, BoardSnapshot>?>` — eine benannte
    `data class CachedBoard(val memberId: String, val board: BoardSnapshot)` verwenden und `read`/`observe` darauf aufbauen.)
- **MIRROR**: NAMING / MODUL, NOTICE_RULES (Stil reiner Funktionen).
- **VALIDATE**: `BoardRulesTest`, `CatalogValidationTest`.

### Task 3: Daten (Remote + lokaler Stand)
- **ACTION**: Remote-Repositories, Mapper, DataStore.
- **IMPLEMENT**:
  - Koin: `single { get<Retrofit>().create(TasksApi::class.java) }` (einziger Eintrag), Repositories `single<TaskRepository> { RemoteTaskRepository(get(), get()) }` usw.
  - `Mappers.kt`: `ApiTaskCard.toDomain()` mit `Instant.parse(...)`; unbekannter Status kann nicht vorkommen (Enum generiert).
    `ApiTaskLane.toDomain()`, `ApiCatalogTask.toDomain()` (archivierte Einträge aus `list()` filtern — API liefert ohne
    `includeArchived` ohnehin nur aktive), `CatalogInput.toApi()` (`CatalogTaskInput(title, emoji, points)`).
  - `RemoteTaskRepository`: `board()` → `apiCall(json, { tasks.getTaskBoard() }) { it.data?.lanes?.map { l -> l.toDomain() } }`;
    `history` → `getTaskHistory(memberId, limit)`; Übergänge → `apiCall(... ) { it.data?.toDomain() }`;
    `reject(cardId, reason)` → `tasks.rejectTask(cardId, RejectTaskRequest(reason))` (nie `null` als Body, siehe GOTCHA oben); `remove` → `apiCallNoContent`.
  - `StoredBoard.kt`: `@Serializable data class StoredBoard(val version: Int, val memberId: String?, val syncedAtMillis: Long, val lanes: List<StoredLane>)` usw.; `CACHE_VERSION = 1`.
  - `BoardCacheSerializer` wie `PlanCacheSerializer`; `DataStoreBoardCache(dataStore)`: `read(memberId)` nur bei passender `memberId`,
    `observe()` = `dataStore.data.map { it.toCachedBoard() }.catch { emit(null) }`; Datei `task-board.json` (Konstante `BOARD_CACHE_FILE`),
    in Koin genau **eine** DataStore-Instanz (Kommentar wie in `MealsModule`).
- **MIRROR**: REMOTE_REPOSITORY, DATASTORE.
- **GOTCHA**: `TaskCard`/`TaskLane` kollidieren als Namen mit den generierten
  Modellen → generierte per Import-Alias (`import de.familyplanner.api.model.TaskCard as ApiTaskCard`) wie in meals.
- **VALIDATE**: `RemoteTaskRepositoryTest`, `RemoteCatalogRepositoryTest` (MockWebServer), `DataStoreBoardCacheTest`.

### Task 4: Use Cases
- **ACTION**: `domain/board/UseCases.kt`, `domain/catalog/UseCases.kt`.
- **IMPLEMENT**:
  - `LoadBoardUseCase(repository, cache, sessions, clock)`: wie `LoadWeekPlanUseCase` — Erfolg → `BoardSnapshot(lanes, now)` in den Stand,
    `Fresh`; Fehler mit `allowsCachedBoard()` (Network/Certificate/Server) → `Cached` falls Stand vorhanden, sonst `Failed`.
  - `MoveCardUseCase(repository, cache, sessions, clock)`: `suspend operator fun invoke(cardId, action: CardAction, reason: String? = null): ApiResult<TaskCard>`;
    mappt `REPORT/WITHDRAW/ACCEPT/ACCEPT_DIRECTLY/REJECT` auf das Repository (`ACCEPT_DIRECTLY` = `accept`), `REMOVE` separat
    (`RemoveCardUseCase`). Bei Erfolg den lokalen Stand **aktualisieren** (Karte ersetzen bzw. entfernen), damit Badge und
    Hinweis-Basislinie sofort stimmen. Grund: trim, leer → `null`, > 200 → `AppError.Validation`.
  - `AssignTasksUseCase(repository)`: 1–20 IDs (sonst Validation), Kind gesetzt; danach Board neu laden (im ViewModel).
  - `LoadHistoryUseCase(repository)`: `history(memberId, TaskLimits.HISTORY_LIMIT)`.
  - Katalog: `LoadCatalogUseCase`, `SaveCatalogTaskUseCase` (Validierung → create/update), `ArchiveCatalogTaskUseCase`;
    zusammengefasst in `data class CatalogUseCases(...)` wie `RecipeUseCases`.
  - `TaskSync.requestNow()` nach eigenen Aktionen **nicht** nötig (Stand wird direkt aktualisiert).
- **MIRROR**: CACHE_FALLBACK.
- **VALIDATE**: `UseCasesTest` (board, catalog).

### Task 5: Hinweise (REQ-TASK-030)
- **ACTION**: Regeln, Abgleich, Worker, Notifier, `TaskOpenRequests`.
- **IMPLEMENT**:
  - `domain/notice/NoticeRules.kt`:
    ```kotlin
    sealed interface TaskNotice {
        val cardId: String
        data class Reported(override val cardId: String, val title: String, val childName: String) : TaskNotice
        data class Assigned(override val cardId: String, val title: String) : TaskNotice
        data class Accepted(override val cardId: String, val title: String, val points: Int) : TaskNotice
        data class Rejected(override val cardId: String, val title: String, val reason: String?) : TaskNotice
    }
    /** Vergleicht den letzten Stand mit dem neuen; ohne vorherigen Stand keine Hinweise (Basislinie). */
    fun taskNotices(isParent: Boolean, memberId: String, previous: List<TaskLane>?, current: List<TaskLane>): List<TaskNotice>
    ```
    Eltern: jede Karte mit Status `REVIEW`, die vorher nicht `REVIEW` war (oder neu ist) → `Reported(title, lane.name)`.
    Kind (nur Spur `memberId`): Karte neu und `TODO` → `Assigned`; vorher `REVIEW`/`TODO`, jetzt `DONE` → `Accepted`;
    vorher `REVIEW`, jetzt `TODO` → `Rejected(reason)`; Karten, die verschwunden sind (entfernt) → nichts.
  - `SyncTaskNoticesUseCase(sessions, repository, cache, notifier, clock)`: Mutex, `awaitSession` (lokale Kopie der privaten
    Hilfsfunktion aus meals — `feature:tasks` kennt `meals` nicht), `Unauthorized` → `cache.clear()` + `SIGNED_OUT`, andere Fehler →
    `UNREACHABLE` ohne Änderung; Erfolg → `previous = cache.read(session.memberId)?.lanes`, Hinweise melden, Stand schreiben.
    Rückgabe `enum class TaskSyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }`.
  - `data/WorkManagerTaskSync.kt`: Kopie des Meals-Musters mit `PERIODIC_WORK = "task-sync-periodic"`, `NOW_WORK = "task-sync-now"`,
    `TaskSyncWorker` (`KoinComponent`, `inject<SyncTaskNoticesUseCase>()`).
  - `data/AndroidTaskNotifier.kt`: Kanal `CHANNEL_TASKS = "tasks"` („Aufgaben“, `IMPORTANCE_DEFAULT`), ID `TASK_ID_BASE + (cardId.hashCode() and 0xFFFF)`,
    Symbol `R.drawable.ic_stat_tasks`, Texte:
    `Reported` „%1$s hat „%2$s“ erledigt“ / „Bitte abnehmen.“; `Assigned` „Neue Aufgabe: %1$s“ / „Erledige sie, wann es passt.“;
    `Accepted` „„%1$s“ abgenommen“ / „+%1$d Punkte“ (Plural-Ressource `tasks_points`); `Rejected` „„%1$s“ zurückgewiesen“ / Grund oder
    „Bitte noch einmal ansehen.“ PendingIntent mit Extra `TaskOpenRequests.EXTRA_OPEN_TASKS`, Request-Code **1** (meals nutzt 0).
  - `TaskOpenRequests` wie `PlanOpenRequests` mit `EXTRA_OPEN_TASKS = "de.familyplanner.android.OPEN_TASKS"`.
- **MIRROR**: SYNC_USE_CASE, NOTICE_RULES, WORKER / NOTIFIER.
- **GOTCHA**: Punkte im Text „+n Punkte“ erst sinnvoll, wenn GAME bucht — trotzdem anzeigen (die Punkte stehen auf der Karte und werden
  nachgebucht, ADR-0016). Benachrichtigungs-IDs dürfen nicht mit meals kollidieren (meals: `1_000 + (hash and 0xFFFF)` =
  1_000–66_535) → `TASK_ID_BASE = 100_000`, Maske `0xFFFF` (100_000–165_535).
- **VALIDATE**: `NoticeRulesTest`, `SyncTaskNoticesUseCaseTest`, `AndroidTaskNotifierTest` (Robolectric).

### Task 6: Board-Bildschirm
- **ACTION**: `BoardViewModel`, `BoardScreen`, `TaskCardItem`, `RejectDialog`.
- **IMPLEMENT**:
  - `BoardUiState(memberId, isParent, lanes: List<TaskLane>, selectedTab: Int, syncedAt: Instant?, isOffline, isLoading, error, isBusy, actionError, rejecting: TaskCard?, rejectReason: String, confirming: Pair<TaskCard, CardAction>?)` mit
    `get()`-Ableitungen: `tabs` (Eltern: `REVIEW_TAB` + Spuren; Kind: `orderedLanes`), `selectedLane`, `sections`, `reviewQueue`,
    `actionsFor(card)` = `cardActions(...)`.
  - Events: `Refresh`, `SelectTab(i)`, `Act(card, action)` (REPORT/WITHDRAW/ACCEPT direkt; REJECT öffnet Dialog; ACCEPT_DIRECTLY/REMOVE öffnen
    Bestätigung), `RejectReasonChanged`, `ConfirmReject`, `DismissDialog`, `ConfirmAction`.
  - Laden im `init` und bei `Refresh`; Ergebnis `Cached` → `isOffline = true`, Hinweis „Offline – Stand vom {formatDateTime}“.
    Nach erfolgreicher Aktion: Karte im State ersetzen/entfernen (aus dem Ergebnis), kein Volle-Neuladen nötig.
    `AppError.Conflict` (jemand war schneller) → `actionError` + Neuladen.
  - Screen: `Scaffold`-freie `Column` (Leiste kommt aus `app`): Titel „Aufgaben“, bei Eltern rechts `IconButton` (Icons.Filled.List,
    „Katalog“); `PrimaryScrollableTabRow`; Inhalt `LazyColumn` mit Abschnitten „Todo“, „Zur Abnahme“, „Erledigt (7 Tage)“ und je
    Abschnitt leerem Text („Nichts offen“ …); unter Erledigt `TextButton` „Verlauf anzeigen“. Eltern: `ExtendedFloatingActionButton`
    „Zuweisen“ (offline deaktiviert). Reiter „Zur Abnahme“: Liste aus `reviewQueue` mit Kindername auf der Karte.
  - `TaskCardItem(card, childName: String?, actions, onAction)`: `Card`, Zeile `emoji` + `title` (titleMedium) + „n P.“, darunter je nach Status
    „gemeldet {Zeit}“, „erledigt {Tag Zeit}“, bei TODO mit `rejectionReason` „Zurückgewiesen: „…““ in `colorScheme.error`; Haupt-Aktionen als
    Buttons, `ACCEPT_DIRECTLY`/`REMOVE` im Überlaufmenü (`Icons.Filled.MoreVert`).
  - `RejectDialog`: wie `ReasonDialog`, aber Begründung optional, Bestätigen immer aktiv (außer `isBusy`).
- **MIRROR**: VIEWMODEL (State/Event), SCREEN_PATTERN (`TodayMealCard`/`StatusCards`).
- **GOTCHA**: `selectedTab` beim Neuladen auf gültigen Bereich klemmen (Kind entfernt). `Act` doppelt tippen → `isBusy` sperrt.
- **VALIDATE**: `BoardViewModelTest`, `BoardScreenTest` (Robolectric).

### Task 7: Zuweisen, Katalog, Verlauf
- **ACTION**: drei Bildschirme mit ViewModels.
- **IMPLEMENT**:
  - `AssignRoute(args: AssignArgs(memberId: String?))`: lädt Katalog + Kinder (aus Board-Stand/`LoadBoardUseCase` für die Namen),
    Kind-Auswahl als `FilterChip`-Reihe, Katalog als Liste mit `Checkbox`, `SubmitButton` „Zuweisen (n)“ (deaktiviert bei 0 oder > 20),
    leerer Katalog → Text „Noch keine Aufgaben im Katalog“ + Button „Katalog öffnen“. Erfolg → `onDone()` (Board lädt im `ON_RESUME`/
    über Ergebnis-Key neu: `savedStateHandle["tasks_changed"] = true`, Board beobachtet ihn wie `PICKED_RECIPE_KEY` in meals).
  - `CatalogRoute`: Liste (emoji, Titel, Punkte), FAB „Neu“, Antippen → `CatalogEditRoute(id)`.
  - `CatalogEditRoute(id: String?)`: Felder Titel (Zähler 0/40), Symbol (optional, Hinweis „z. B. 🧹“), `PointsStepper` (1–100),
    Speichern; bei bestehender Aufgabe zusätzlich „Archivieren“ mit `ConfirmDialog` („Die Aufgabe kann danach nicht mehr zugewiesen
    werden. Bereits verteilte Karten bleiben.“).
  - `HistoryRoute(memberId, name)`: Liste der letzten 50 erledigten Karten (Titel, Punkte, „erledigt am …“), leer → „Noch nichts erledigt“.
  - Routen in `TasksNavigation.kt`: `@Serializable data object TasksHomeRoute`, `data class AssignRoute(val memberId: String? = null)`,
    `data object CatalogRoute`, `data class CatalogEditRoute(val catalogTaskId: String? = null)`, `data class HistoryRoute(val memberId: String, val name: String)`;
    `fun NavGraphBuilder.tasksGraph(navController: NavHostController)`.
- **MIRROR**: Routen/Args wie `MealsNavigation.kt` (`RecipeEditRoute`, Koin-Parameter-Args wie `ShoppingListArgs`).
- **VALIDATE**: `AssignViewModelTest`, `CatalogViewModelsTest`, `HistoryViewModelTest`, `TaskScreensTest`.

### Task 8: „Heute“, Badge, App-Verdrahtung
- **ACTION**: `TodayTasksCard`, `ReviewBadge`, Änderungen in `app`.
- **IMPLEMENT**:
  - `TodayTasksViewModel(sessions, loadBoard)`: lädt Board (Cache-Fallback), Kind → `openCards` = eigene TODO + REVIEW, `titles = take(3)`,
    `rejectedCount`; Eltern → `reviewCount`. Karte: Kind „Deine Aufgaben“ / „%d offen“ / Titel-Liste / „%d zurückgewiesen“ (error-Farbe);
    keine offenen → „Alles erledigt 🎉“; Eltern „Aufgaben“ / „%d warten auf Abnahme“ bzw. „Nichts zur Abnahme“. `onOpenTasks`.
  - `ReviewBadge(cache, sessions)`: `val count: Flow<Int> = combine(sessions.state, cache.observe()) { s, c -> if (Eltern && c?.memberId == s.memberId) reviewCount(c.board.lanes) else 0 }`.
  - `AppNavigationBar(destinations, selected, badges: Map<TopLevelDestination, Int> = emptyMap(), onSelect)`: Icon in `BadgedBox`, `Badge { Text("$n") }` nur bei n > 0;
    Badge-`contentDescription` über `semantics { contentDescription = "%d zur Abnahme" }`.
  - `TopLevelDestination.TASKS(TasksHomeRoute, …)`; `TasksRoute` + `tasks_coming_soon` aus `PlaceholderScreens.kt`/Strings entfernen.
  - `AppNavHost.MainHost`: `mainGraph { TodayMealRoute(...); TodayTasksRoute(onOpenTasks = { navController.navigateToTopLevel(TopLevelDestination.TASKS) }) }`,
    `tasksGraph(navController)`, `val reviews by koinInject<ReviewBadge>().count.collectAsStateWithLifecycle(0)`,
    `badges = mapOf(TopLevelDestination.TASKS to reviews)`, Deep-Link `taskRequests.pending` → `navigateToTopLevel(TASKS)`.
  - `MainActivity`: `receiveOpenTasks(intent)` analog zu `receiveOpenPlan` (in `onCreate` und `onNewIntent`), `onStart`: zusätzlich `get<TaskSync>().requestNow()`.
  - `FamilyPlannerApp`: `tasksModule` in `modules(...)`, `get<TaskSync>().schedulePeriodic()`.
  - `TasksModule`: alle Bindungen inkl. `single { TaskOpenRequests() }`, `single { ReviewBadge(get(), get()) }`, `single<TaskSync> { WorkManagerTaskSync(androidContext()) }`,
    `single<TaskNotifier> { AndroidTaskNotifier(androidContext()) }`, ViewModels per `viewModel { … }` / mit Parametern.
- **MIRROR**: INBOX_NAVIGATION, KOIN (`MealsModule`).
- **GOTCHA**: `HomeScreen` rendert den Slot in einer `Column` mit `verticalScroll` → zwei Karten untereinander funktionieren ohne Änderung in `feature:auth`.
  `AppNavHostTest` braucht im Koin-Testmodul jetzt `TodayTasksViewModel`, `ReviewBadge` und `TaskOpenRequests` (Fakes für Repository/Cache).
- **VALIDATE**: `AppNavHostTest`, `TodayTasksTest`, `ReviewBadgeTest`, `TasksModuleTest`.

### Task 9: Doku, Traceability, Abnahme-Checkliste
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - `docs/03-design/tasks.md`: Abschnitt „## App-Seite (Phase 3)“ — Bildschirme, Aktionen je Rolle/Status (Tabelle = `cardActions`), lokaler Stand,
    Hinweisregeln (Tabelle Eltern/Kind × vorher → jetzt), Badge, Unit-Testfälle.
  - `docs/04-verification/traceability.md`: REQ-TASK-002 (vollständig), 003, 010, 020–025 (Backend + App), 030 → Status „umgesetzt (App)“;
    Tests nennen.
  - `android/README.md`: „## Abnahme-Checkliste TASK Phase 3 (manuell, Testhandys gegen die Testinstanz)“ = Manual Validation.
  - PRD Phase 3: Bericht-Verweis (Status bleibt `in-progress` bis zur Geräte-Abnahme).
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-TASK-022 child sees report on own todo and withdraw on own review` (`BoardRulesTest`) | Kind, eigene Karten | `[REPORT]` / `[WITHDRAW]` | |
| `REQ-TASK-022 child sees no actions on a sibling's card` | fremde Spur | `[]` | |
| `REQ-TASK-023 parent sees accept, reject and remove on review cards` | Eltern, REVIEW | `[ACCEPT, REJECT, REMOVE]` | |
| `REQ-TASK-023 parent can accept a todo card directly or remove it` | Eltern, TODO | `[ACCEPT_DIRECTLY, REMOVE]` | |
| `REQ-TASK-023 done cards and offline mode have no actions` | DONE / offline | `[]` | ja |
| `REQ-TASK-021 sections sort todo and review oldest first, done newest first` | gemischte Zeitpunkte | Reihenfolge | |
| `REQ-TASK-021 review queue spans all children oldest report first` | 2 Spuren | Reihenfolge, Kindname | |
| `REQ-TASK-021 a child sees the own lane first` | Kind Ben, Spuren Ben/Lea/Mia (alphabetisch) | Ben zuerst | |
| `REQ-TASK-010 catalog input is validated` | Titel leer/41, Emoji 17, Punkte 0/101 | Validation mit deutschem Text | ja |
| `REQ-TASK-021 board falls back to the cached board when offline` (`UseCasesTest`) | Repo `Network`, Stand vorhanden | `Cached` | ja |
| `REQ-TASK-021 cached board of another member is not used` | Stand von p-1, Session c-1 | `Failed` | ja |
| `REQ-TASK-022 reporting updates the local board` | `MoveCardUseCase(REPORT)` | Stand enthält REVIEW-Karte | |
| `REQ-TASK-023 reject sends a trimmed reason or none` | `"  "` / `" ok "` | `null` / `"ok"` | ja |
| `REQ-TASK-024 removing deletes the card from the local board` | REMOVE | Karte fehlt im Stand | |
| `REQ-TASK-020 assign needs a child and 1 to 20 tasks` | 0 / 21 / kein Kind | Validation | ja |
| `REQ-TASK-030 first sync only records a baseline` (`NoticeRulesTest`) | previous = null | `[]` | ja |
| `REQ-TASK-030 parents hear about newly reported cards once` | TODO → REVIEW, dann gleich | 1×, danach 0 | |
| `REQ-TASK-030 child hears about new, accepted and rejected own cards` | neu TODO / REVIEW→DONE / REVIEW→TODO mit Grund | `Assigned`/`Accepted(points)`/`Rejected(reason)` | |
| `REQ-TASK-030 child hears nothing about siblings or own reports` | fremde Spur / TODO→REVIEW | `[]` | ja |
| `REQ-TASK-030 removed cards are not announced` | Karte fehlt | `[]` | ja |
| `REQ-TASK-030 sync keeps the board when the server is unreachable` (`SyncTaskNoticesUseCaseTest`) | `Network` | Stand unverändert, `UNREACHABLE` | ja |
| `REQ-TASK-030 unauthorized sync clears the local board` | `Unauthorized` | `clear()`, `SIGNED_OUT` | ja |
| `REQ-TASK-003 badge counts review cards for parents only` (`ReviewBadgeTest`) | Eltern 2 REVIEW / Kind | 2 / 0 | |
| `REQ-TASK-003 badge ignores a board cached for another member` | | 0 | ja |
| `REQ-TASK-002 today shows open tasks and rejected count for a child` (`TodayTasksTest`) | 4 offen, 1 zurückgewiesen | „4 offen“, 3 Titel, „1 zurückgewiesen“ | |
| `REQ-TASK-002 today shows the review count for parents` | 2 REVIEW | „2 warten auf Abnahme“ | |
| `REQ-TASK-022 board view model reports a card and shows it in review` (`BoardViewModelTest`) | Act(REPORT) | Karte in `sections.review` | |
| `REQ-TASK-023 conflict reloads the board and shows the message` | Repo `Conflict("Die Aufgabe ist schon erledigt")` | `actionError`, Neuladen | ja |
| `REQ-TASK-023 reject dialog sends optional reason` | ConfirmReject mit leerem Text | `reject(id, null)` | |
| `REQ-TASK-021 offline board disables actions and shows sync time` | `Cached` | `isOffline`, keine Aktionen | ja |
| `REQ-TASK-020 assign view model preselects child and limits to 20` | 21 Häkchen | Button deaktiviert | ja |
| `REQ-TASK-010 catalog edit saves and archives with confirmation` | | create/update/archive aufgerufen | |
| `REQ-TASK-025 history lists done cards newest first` | | Reihenfolge, leer-Text | |

### Integration Tests (Robolectric / MockWebServer / DataStore)

| Test | Expected |
|---|---|
| `RemoteTaskRepositoryTest`: Pfade, Methoden, Body bei `rejection` mit/ohne Grund, `204` bei `remove`, Fehler → `AppError` | wie Vertrag |
| `RemoteCatalogRepositoryTest`: list/create/update/archive | wie Vertrag |
| `DataStoreBoardCacheTest`: Rundreise, anderes Mitglied → `null`, kaputte Datei/alte Version → leer, `observe` | |
| `AndroidTaskNotifierTest`: 4 Hinweisarten, Kanal „Aufgaben“, Extra `OPEN_TASKS`, ohne Berechtigung keine Meldung, IDs ≥ 100_000 | |
| `BoardScreenTest`: Kind — eigene Spur zuerst, „Erledigt“-Button; Eltern — Reiter „Zur Abnahme (2)“, „Abnehmen“/„Zurückweisen“, FAB; offline Hinweis und keine Buttons | |
| `TaskScreensTest`: Zuweisen (Häkchen, Zähler), Katalog-Editor (Zähler, Stepper-Grenzen), Verlauf leer | |
| `TasksModuleTest`: Koin-Graph auflösbar (`verify()` wie `MealsModuleTest`) | |
| `AppNavHostTest`: `REQ-TASK-001 tasks area shows the board`, `REQ-TASK-003 parent sees badge with review count`, `REQ-TASK-030 notification tap opens tasks`, `REQ-TASK-002 today shows the tasks card` | |

### Edge Cases Checklist
- [x] Kein Kind in der Familie (leeres Board → Text „Noch keine Kinder in der Familie“)
- [x] Leerer Katalog beim Zuweisen
- [x] Gleichzeitige Abnahme durch zwei Eltern (409 → Neuladen)
- [x] Offline mit/ohne Stand; Stand eines anderen Mitglieds
- [x] Erster Abgleich (Basislinie), Server nicht erreichbar, Gerät entfernt (401)
- [x] Benachrichtigungs-Berechtigung verweigert
- [x] Reiter-Index nach Neuladen außerhalb des Bereichs
- [x] Doppeltippen auf Aktion (`isBusy`)

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Funde

### Unit Tests
```bash
cd android && ./gradlew :feature:tasks:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease
```
EXPECT: grün, Kover ≥ 80 %; Release (R8) baut

### Test-Build
```bash
cd android && FP_TEST_INSTANCE=true ./gradlew :app:assembleRelease
```
EXPECT: `de.familyplanner.android.test`, 0.9.0-test

### Database Validation
N/A — keine Backend-/Schemaänderung. Gegenprobe: `git diff main -- api/ backend/` ist leer.

### Manual Validation (Abnahme TASK Phase 3 – zwei Testhandys gegen die Testinstanz)
- [ ] Test-Build 0.9.0-test auf Eltern- und Kind-Testhandy (Update über 0.8.0-test, gleiche Signatur); Benachrichtigungen erlaubt
- [ ] Eltern: Katalog öffnen, 4 Aufgaben anlegen (mit Emoji, unterschiedliche Punkte), eine bearbeiten, eine archivieren
- [ ] Eltern: „Zuweisen“ → Testkind, 3 Aufgaben → Board des Kindes zeigt 3 Karten in Todo
- [ ] Kind (≤ 30 min oder App öffnen): Hinweis „Neue Aufgabe …“; Tippen öffnet Aufgaben
- [ ] Kind: eigene Spur zuerst; „Erledigt“ bei einer Karte → „Zur Abnahme“; „Zurückziehen“ funktioniert
- [ ] Eltern: Hinweis „… hat „…“ erledigt“; Badge an „Aufgaben“ zeigt 1; Reiter „Zur Abnahme (1)“; „Heute“ zeigt „1 wartet auf Abnahme“
- [ ] Eltern: „Zurückweisen“ mit Grund → Kind erhält Hinweis mit Grund, Karte in Todo mit rotem Grund
- [ ] Kind meldet erneut → Eltern „Abnehmen“ → Kind erhält „abgenommen, +n Punkte“; Karte unter „Erledigt“; Badge 0
- [ ] Eltern: Todo-Karte „Direkt abnehmen“ und eine andere „Entfernen“ (je mit Bestätigung)
- [ ] „Verlauf anzeigen“ zeigt erledigte Karten
- [ ] Offline (WLAN aus): Board mit „Offline – Stand vom …“, keine Aktionen; „Heute“ zeigt Aufgaben aus dem Stand
- [ ] Erfolgssignal Phase 3: eine Woche Küchenboard auf den Testhandys vollständig nachgespielt

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-TASK-002/003/010/020–025/030 haben App-Tests mit REQ-ID im Namen
- [ ] Rollenrechte in der UI entsprechen dem Backend (keine Aktion, die der Server mit 403 beantworten würde)
- [ ] Offline lesbar, Aktionen nur online; Badge und „Heute“ aus dem lokalen Stand
- [ ] Hinweise nur für die jeweils andere Rolle; erster Abgleich ohne Hinweise
- [ ] `api/`, `backend/` unverändert; keine neue Bibliothek
- [ ] Design, Traceability, README-Checkliste aktualisiert

## Completion Checklist
- [ ] Code folgt den Mustern (Ports in `domain`, Remote/DataStore in `data`, State/Event-ViewModels, Route/Screen-Trennung)
- [ ] Fehler: `AppError` → `message()`; Konflikte laden neu; IO-Fehler `Log.w`
- [ ] Logging nur `Log.w` mit `TAG`, keine Titel/Gründe/Token
- [ ] Tests nach den bestehenden Mustern (Fakes, `runTest`, Robolectric, MockWebServer)
- [ ] Keine Magic Numbers (`TaskLimits`, benannte IDs/Kanäle)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen (Board-Screen in `BoardScreen.kt` + `TaskCardItem.kt` + `BoardSections.kt` aufteilen)
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Zwei Hintergrund-Worker (meals + tasks) erhöhen Akku-/Netzlast | L | L | Beide nur mit Netz, 30 min, gleiche Constraints; bei Bedarf später zusammenlegen |
| Hinweis-Basislinie durch UI-Laden überschrieben → Hinweis fehlt, obwohl Nutzer es nicht gesehen hat | M | L | Gewollt: Wer das Board geöffnet hat, hat die Änderung gesehen; dokumentieren |
| Benachrichtigungs-IDs kollidieren mit meals | M | L | Basis 100_000 (siehe Task 5) |
| Generierter Client weicht von Annahmen ab (optionaler Body, Parameterreihenfolge) | M | L | Vor Task 3 `TasksApi.kt` lesen |
| Board auf kleinen Displays unübersichtlich | M | M | Eine Spur pro Reiter, Abschnitte untereinander; Abnahme auf Testhandy prüfen |
| Kover < 80 % durch viele Screens | M | M | Screens als reine Composables mit State testen (Robolectric), `*Route*` ist ausgeschlossen |

## Notes
- GAME Phase 1 ersetzt „Ich“ und ergänzt Punkte auf Karten/Board; die Badge-Map in `AppNavigationBar` ist dafür schon allgemein.
- Umsetzung laut CLAUDE.md in einer **neuen Session** mit `/ecc:prp-implement .claude/PRPs/plans/board-app.plan.md`.
