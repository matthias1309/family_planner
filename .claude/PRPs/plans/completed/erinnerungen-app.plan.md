# Plan: Erinnerungen & Status-Benachrichtigungen in der App (Phase 7)

## Summary
`feature:meals` plant für das kochende Kind **lokale Erinnerungen 72/48/24 h vor seiner Kind-Frist** (auch wenn sein Gericht abgelehnt
und nicht überarbeitet ist), zeigt in Erinnerung und Essensplan den **WhatsApp-Hinweis** für unterwegs und meldet per
**Hintergrund-Abgleich im Heimnetz** Statusänderungen: dem Kind „freigegeben“/„abgelehnt (Begründung)“, den Eltern „neue Einreichung
wartet auf Freigabe“. Grundlage ist ein lokal gespeicherter Abgleich-Stand (DataStore), aus dem auch offline und nach einem Neustart
die Erinnerungen kommen. **Nur App** — `api/` und `backend/` bleiben unverändert (`GET …/status` und `GET …/plan` dürfen alle lesen).

## User Story
Als kochendes Kind möchte ich rechtzeitig — auch unterwegs — an meine Einreichung erinnert und über Freigabe/Ablehnung informiert
werden, und als Elternteil möchte ich erfahren, wenn ein Gericht auf meine Freigabe wartet, damit niemand mehr hinterherlaufen muss.

## Problem → Solution
Heute erfährt das Kind seine Frist und den Status nur, wenn es die App selbst öffnet; Eltern sehen neue Einreichungen nur im
Wochenstatus → App gleicht alle 30 min (und bei jedem Start/jeder eigenen Änderung) im Heimnetz ab, speichert den Stand lokal, plant
daraus `AlarmManager`-Erinnerungen und zeigt Android-Benachrichtigungen für Erinnerungen und Statuswechsel.

## Metadata
- **Complexity**: Large (≈ 17 neue Dateien, ≈ 16 geänderte; neue Bausteine WorkManager, AlarmManager, Notifications)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 7 – Erinnerungen, REQ-MEAL-050, -051, -052, **-053 (neu)**
- **Estimated Files**: ≈ 17 neu, ≈ 16 geändert (inkl. Tests, ADR, Doku)

## Entscheidungen des Anwenders (2026-10-24)
| Frage | Entscheidung |
|---|---|
| Erinnern auch bei abgelehntem Gericht? | **Ja** — Erinnerung bei `NOT_SUBMITTED` **und** `REJECTED`; Text „überarbeiten“ statt „einreichen“ |
| Zeitpunkte | **72 h, 48 h und 24 h vor der Frist** (drei Erinnerungen) |
| Eltern benachrichtigen? | **Ja** — „Kind hat ein Gericht eingereicht – wartet auf Freigabe“ → neue **REQ-MEAL-053** |

PRD bereits in der Planungs-Session angepasst (REQ-MEAL-050 neu formuliert, REQ-MEAL-052 um „freigegeben“ ergänzt, REQ-MEAL-053 neu,
User Flow, MoSCoW, Risiko, Phase 7 → `in-progress`); `traceability.md` hat eine Zeile REQ-MEAL-053 („offen“).

Entscheidungen dieses Plans (vertretbare Standards, ohne Rückfrage; bei Widerspruch im Review ändern):
- **„72/48/24 h“ = 3/2/1 Kalendertage vor der Frist zur Frist-Uhrzeit** (`ZonedDateTime.minusDays` in `Europe/Berlin`): Bei Frist
  Do 16:00 → Mo/Di/Mi 16:00, auch über die Zeitumstellung hinweg (dann sind es 71 bzw. 73 h — die Uhrzeit bleibt gleich).
- Nur die **Planwoche** (`currentPlanWeek`, Montag nach heute) wird abgeglichen — dort liegen alle offenen Fristen (Design `meals.md`).
- Erinnerungszeitpunkte, die beim Abgleich schon vorbei sind, **entfallen** (keine nachgeholte Benachrichtigung). Nach Fristablauf
  keine Erinnerung mehr (Einreichen bleibt erlaubt, ADR-0007).
- **Keine exakten Alarme**: `AlarmManagerCompat.setAndAllowWhileIdle` (inexakt, auch im Doze). Android verschiebt solche Alarme um
  höchstens ~1 h; für eine Erinnerung 1–3 Tage vorher reicht das, und es braucht keine Sonderberechtigung (`SCHEDULE_EXACT_ALARM` ist
  ab Android 14 standardmäßig verweigert). Abweichung von der PRD-Risikominderung „Exakte Alarme anfragen“ → **ADR-0013**.
- Hintergrund-Abgleich mit **WorkManager**: periodisch alle **30 min** (`NetworkType.CONNECTED`) + sofort beim App-Start und nach
  eigenen Änderungen (Gericht speichern/löschen, Absagen, Absage zurücknehmen). Offline/Server gestört → Stand und Alarme bleiben.
- **Erster Abgleich einer Woche meldet nichts** (Basislinie), damit nach Installation/Wochenwechsel keine Flut alter Meldungen kommt.
- Kind wird gemeldet, wenn eines **seiner** Gerichte der Planwoche `APPROVED` oder `REJECTED` wird bzw. sich der Ablehnungsgrund ändert
  (auch ein stellvertretend eingetragenes, neues `APPROVED`-Gericht → „freigegeben“). Eltern werden gemeldet, wenn ein Gericht neu
  `SUBMITTED` ist (neu oder vorher anderer Status); nur Kinder erzeugen `SUBMITTED`, Eltern-Gerichte sind immer `APPROVED`.
- Tippen auf eine Benachrichtigung öffnet die App direkt im **Essensplan** (Inbox-Muster wie `SharedRecipeInbox`).
- WhatsApp-Hinweis (REQ-MEAL-051): **immer** im Text der Erinnerung (wirkt ohne Öffnen) **und** als Karte im Essensplan, wenn das Kind
  offline ist und laut lokalem Stand noch einreichen/überarbeiten muss — mit Knopf „Per WhatsApp schicken“ (Textvorlage).
- Benachrichtigungs-Berechtigung (`POST_NOTIFICATIONS`, API 33+) wird beim Start zusammen mit der Heimnetz-Berechtigung angefragt.
  Ohne Zustimmung: keine Benachrichtigungen, App sonst unverändert (kein Fehler).
- Version **0.7.0** / `versionCode = 8`.

---

## UX Design

### Before
```
Kind:   öffnet die App selbst → Essensplan → Planwoche → Karte „Mein Kochtag“ (Frist, Zustand)
        unterwegs: App zeigt nur gespeicherten Plan, kein Hinweis, was zu tun ist
Eltern: öffnen Essensplan → Wochenstatus, um zu sehen, ob etwas wartet
```

### After
```
Android-Benachrichtigung (Kind, Mo/Di/Mi 16:00 bei Frist Do 16:00):
┌─────────────────────────────────────────────────┐
│ 🍳 Dein Kochtag: Gericht einreichen             │
│ Frist: Do 15.10., 16:00. Nicht zu Hause? Schick │
│ Gericht und Zutaten per WhatsApp an deine Eltern│
└─────────────────────────────────────────────────┘
  (abgelehnt & nicht überarbeitet: „Dein Kochtag: Gericht überarbeiten“)

Kind, nächster Abgleich im Heim-WLAN:
  „„Curry“ wurde abgelehnt“ — Begründung: kein Gemüse
  „„Curry“ ist freigegeben“ — Dein Gericht für Di 20.10. steht.

Eltern, nächster Abgleich im Heim-WLAN:
  „Ben hat ein Gericht eingereicht“ — „Curry“ für Di 20.10. wartet auf Freigabe.

Tippen → App öffnet den Essensplan.

Essensplan, Kind offline mit offener Einreichung:
┌─────────────────────────────────────────────────┐
│ Nicht zu Hause?                                 │
│ Schick dein Gericht und die Zutaten per WhatsApp│
│ an deine Eltern – sie tragen es für dich ein.   │
│ Frist: Do 15.10., 16:00                         │
│ [ Per WhatsApp schicken ]                       │
└─────────────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| App-Start | Heimnetz-Berechtigung | + Benachrichtigungs-Berechtigung (API 33+); Abgleich sofort anstoßen | `RequestMultiplePermissions` |
| Benachrichtigung „Erinnerung“ | – | Kind, 72/48/24 h vor Frist, nur `NOT_SUBMITTED`/`REJECTED` | Kanal „Erinnerungen“ (hoch) |
| Benachrichtigung „Status“ | – | Kind: freigegeben/abgelehnt; Eltern: neue Einreichung | Kanal „Freigaben“ (normal) |
| Tippen auf Benachrichtigung | – | öffnet Essensplan | Inbox `PlanOpenRequests` |
| Essensplan offline (Kind) | Offline-Hinweis | + Karte „Nicht zu Hause?“ mit WhatsApp-Knopf | nur bei offener Einreichung laut lokalem Stand |
| Gericht speichern/löschen, absagen, zurücknehmen | – | stößt Abgleich an (Alarme sofort aktuell) | in den Use Cases |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | all | Koin-Verdrahtung, DataStore-Single je Datei |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt` | 1-126 | Use-Case-Stil, `coroutineScope`/`async`, `SaveDishUseCase`, `DeleteDishUseCase` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/UseCases.kt` | 1-72 | `CancelCookingUseCase`, `WithdrawCancellationUseCase`, Bündel `CookingUseCases` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/Models.kt` | all | `CookState`, `CookStatus.submissionDeadline` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Models.kt` | 1-36 | `Dish` (status, rejectionReason, cookId), `WeekPlan` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/DataStorePlanCache.kt`, `PlanCacheSerializer.kt`, `StoredPlan.kt` | all | Muster für den lokalen Stand (Version, unlesbar → leer, `Log.w`) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanViewModel.kt` | all | State/Event, `load()`, `isOffline`, `statusError` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt` | 45-145 | Platz der neuen Karte, `OfflineNotice` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusCards.kt` | 135-200 | Kartenstil `MyCookingCard` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/SharedRecipeInbox.kt` + `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 60-80 | Inbox → Navigation |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/MainActivity.kt`, `FamilyPlannerApp.kt` | all | Berechtigungen, Koin-Start |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/attendance/PlanWeeks.kt` | all | `currentPlanWeek`, `mondayOf` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/Labels.kt` | 40-50 | `formatDeadline` („Do 22.10., 16:00“), `weekdayShort` |
| P1 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/session/SessionStore.kt` | all | `state` (Loading!) vs. `current` |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/CookingFakes.kt`, `PlanFakes.kt` | all | Fakes, Testdaten (`benDeadline`, `planWeek`, `childBen`) |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/DataStorePlanCacheTest.kt` | 1-70 | DataStore-Test mit `TemporaryFolder` |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/CookingScreensTest.kt` | 1-60 | Robolectric-Compose-Test |
| P2 | `android/app/build.gradle.kts` | 60-110 | Kover-Ausschlüsse, Version |
| P2 | `docs/03-design/meals.md` | 364-Ende | Aufbau „App-Seite (Phase 6b)“ als Vorlage |
| P2 | `docs/02-architecture/adr/0011-app-offline-cache.md` | all | Vorlage ADR-0013 |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| WorkManager | developer.android.com/develop/background-work/background-tasks/persistent | `CoroutineWorker`; `enqueueUniquePeriodicWork(…, KEEP, …)`, Minimum 15 min; `enqueueUniqueWork(…, REPLACE, …)`; Arbeit überlebt Neustart. **Version 2.12.0** (stabil, Google Maven, geprüft 2026-10-24) |
| WorkManager-Tests | developer.android.com/develop/background-work/background-tasks/testing | `work-testing` nur nötig, wenn der Worker getestet wird — hier nicht (Worker ist dünn, Kover-Ausschluss) |
| Alarme | developer.android.com/develop/background-work/services/alarms | `setAndAllowWhileIdle` feuert auch im Doze (inexakt, max. ~1×/9 min je App); Alarme gehen bei **Neustart und App-Update** verloren → `BOOT_COMPLETED` / `MY_PACKAGE_REPLACED` |
| Benachrichtigungen | developer.android.com/develop/ui/views/notifications/notification-permission | `POST_NOTIFICATIONS` ab API 33 Laufzeit-Berechtigung; Kanäle vor dem ersten `notify` anlegen; Lint `MissingPermission` verlangt `checkSelfPermission` vor `notify` |
| PendingIntent | developer.android.com/reference/android/app/PendingIntent | `FLAG_IMMUTABLE` Pflicht (targetSdk ≥ 31); gleiche `requestCode` + Intent-Filter ersetzt/cancelt |
| Robolectric | robolectric.org | `shadowOf(alarmManager).scheduledAlarms`, `shadowOf(notificationManager).allNotifications`, `shadowOf(app).grantPermissions(...)` |

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/UseCases.kt:45-56
class CancelCookingUseCase(
    private val cooking: CookingRepository,
) {
    suspend operator fun invoke(
        weekStart: LocalDate,
        memberId: String,
        reason: String,
    ): ApiResult<Unit> {
        validateReason(reason)?.let { return it }
        return cooking.cancel(weekStart, memberId, reason.trim())
    }
}
```
Pakete: `domain/reminder/` (rein, testbar), `data/reminder/` (DataStore + Android-Bausteine). Konstanten in `object ReminderLimits`
(wie `PlanLimits`, `ShoppingLimits`). KDoc und Kommentare auf Deutsch, Code/Bezeichner englisch.

### USE_CASE_PATTERN (parallel laden, erster Fehler gewinnt)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/UseCases.kt:16-28
suspend operator fun invoke(weekStart: LocalDate): ApiResult<StatusOverview> =
    coroutineScope {
        val status = async { cooking.status(weekStart) }
        val members = async { plan.members() }
        val statusResult = status.await()
        val membersResult = members.await()
        if (statusResult !is ApiResult.Success) return@coroutineScope statusResult as ApiResult.Failure
        if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
        ...
    }
```

### REPOSITORY_PATTERN / LOCAL STORE (DataStore, Fehler nie Nutzerfehler)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/DataStorePlanCache.kt:19-29
override suspend fun read(memberId: String, weekStart: LocalDate): WeekPlanSnapshot? =
    try {
        val stored = dataStore.data.first()
        if (stored.memberId != memberId) null else stored.weeks.firstOrNull { it.weekStart == weekStart.toString() }?.toDomain()
    } catch (e: IOException) {
        Log.w(TAG, "Meal plan cache unreadable", e)
        null
    }
```
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/PlanCacheSerializer.kt:18-30
override suspend fun readFrom(input: InputStream): StoredPlanCache {
    val bytes = input.readBytes()
    if (bytes.isEmpty()) return defaultValue
    return try {
        json.decodeFromString<StoredPlanCache>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
    } catch (e: SerializationException) {
        Log.w(TAG, "Meal plan cache is malformed, starting empty", e)
        defaultValue
    } catch (e: IllegalArgumentException) { ... }
}
```
Dateiformat getrennt von der Domäne (`@Serializable data class Stored…`, Daten als ISO-Text, Zeitpunkte als Epoch-Millis).

### ERROR_HANDLING
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt:18-19
/** Verbindungsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
private fun AppError.allowsCachedPlan() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server
```
Abgleich-Fehler (`ApiResult.Failure`) → Ergebnis `SyncOutcome.UNREACHABLE`, **nichts** ändern (Alarme/Stand behalten), kein Logging
nötig (normal unterwegs). `AppError.Unauthorized` → wie abgemeldet behandeln (Gerät entfernt). Lokale IO-Fehler → `Log.w`, weiter.

### LOGGING_PATTERN
```kotlin
// SOURCE: DataStorePlanCache.kt:14, 27
private const val TAG = "DataStorePlanCache"
Log.w(TAG, "Meal plan cache unreadable", e)
```
Nur `Log.w` bei IO-/Format-Fehlern; nie Token oder Gerichtsinhalte loggen.

### VIEWMODEL_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanViewModel.kt:52-58
val canEdit: Boolean get() = isParent && !isOffline && week != null
val showsStatus: Boolean get() = weekStart >= planWeek && !isOffline
val myCooking: CookOverview? get() = if (isParent) null else status?.cooks?.firstOrNull { it.status.memberId == memberId }
```
Abgeleitete Anzeige-Flags als `get()`-Properties im UiState; Laden in `load()`; späte Antworten verwerfen.

### SCREEN_PATTERN (Karte)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusCards.kt:139-160
@Composable
fun MyCookingCard(...) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(CardPadding)) {
            Text(stringResource(R.string.cooking_title), style = MaterialTheme.typography.titleMedium)
            Text(status.submissionDeadline?.let { stringResource(R.string.cooking_deadline, formatDeadline(it)) } ...)
```

### INBOX_NAVIGATION_PATTERN
```kotlin
// SOURCE: android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt:64-72
@Composable
private fun MainHost(inbox: SharedRecipeInbox = koinInject()) {
    val navController = rememberNavController()
    val shared by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let { text ->
            inbox.consume()
            navController.openRecipeImport(text)
        }
    }
```

### KOIN_PATTERN
```kotlin
// SOURCE: MealsModule.kt (plan)
// Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
single<PlanCache> {
    DataStorePlanCache(
        DataStoreFactory.create(
            serializer = PlanCacheSerializer(get()),
            produceFile = { androidContext().dataStoreFile(PLAN_CACHE_FILE) },
        ),
    )
}
factory { CookingUseCases(LoadWeekStatusUseCase(get(), get()), CancelCookingUseCase(get()), WithdrawCancellationUseCase(get())) }
```

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/domain/cooking/UseCasesTest.kt:16-29
class UseCasesTest {
    private val cooking = FakeCookingRepository()
    private val plan = FakeMealPlanRepository()

    @Test
    fun `REQ-MEAL-033 status joins child names`() =
        runTest {
            cooking.statusResult = { ApiResult.Success(statusOf(it, cookStatus("c-1"), cookStatus("c-1", CookState.SUBMITTED))) }
            val overview = (LoadWeekStatusUseCase(cooking, plan)(planWeek) as ApiResult.Success).data
            assertEquals(listOf("Ben", "Ben"), overview.cooks.map { it.name })
        }
```
Fakes statt Mocks (`fakes/`), `kotlin.test`-Asserts, REQ-ID am Anfang des Testnamens, feste `Clock.fixed(…, BerlinZone)`.
Robolectric: `@RunWith(RobolectricTestRunner::class)` (siehe `CookingScreensTest`); Session: `InMemorySessionStore(Session(...))`
aus `:core:testing`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/gradle/libs.versions.toml` | UPDATE | `work = "2.12.0"`, Library `work-runtime = androidx.work:work-runtime-ktx` |
| `android/feature/meals/build.gradle.kts` | UPDATE | `implementation(libs.work.runtime)`, `implementation(libs.core.ktx)` (falls nicht transitiv: `NotificationCompat`, `AlarmManagerCompat`) |
| `android/feature/meals/src/main/AndroidManifest.xml` | UPDATE | Berechtigungen `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`; zwei Receiver |
| `…/feature/meals/domain/reminder/Models.kt` | CREATE | `Reminder`, `ReminderSnapshot`, `KnownDish`, `StatusNotice`, `DeadlineReminder`, `PendingCooking`, `SyncOutcome` |
| `…/feature/meals/domain/reminder/ReminderLimits.kt` | CREATE | `DAYS_BEFORE = listOf(3L, 2L, 1L)`, `SYNC_INTERVAL_MINUTES = 30L` |
| `…/feature/meals/domain/reminder/ReminderRules.kt` | CREATE | reine Funktionen `reminderTimes`, `needsReminder`, `statusNotices` |
| `…/feature/meals/domain/reminder/Ports.kt` | CREATE | Interfaces `ReminderStore`, `ReminderScheduler`, `Notifier`, `ReminderSync` |
| `…/feature/meals/domain/reminder/UseCases.kt` | CREATE | `SyncRemindersUseCase`, `ShowDueReminderUseCase`, `RestoreRemindersUseCase`, `LoadPendingCookingUseCase` |
| `…/feature/meals/data/reminder/StoredReminders.kt` | CREATE | Dateiformat + Mapper |
| `…/feature/meals/data/reminder/ReminderStoreSerializer.kt` | CREATE | wie `PlanCacheSerializer` |
| `…/feature/meals/data/reminder/DataStoreReminderStore.kt` | CREATE | `ReminderStore`-Implementierung |
| `…/feature/meals/data/reminder/AlarmReminderScheduler.kt` | CREATE | `AlarmManager`-Implementierung |
| `…/feature/meals/data/reminder/AndroidNotifier.kt` | CREATE | Kanäle + Benachrichtigungen |
| `…/feature/meals/data/reminder/WorkManagerReminderSync.kt` | CREATE | `ReminderSync` + `ReminderSyncWorker` |
| `…/feature/meals/data/reminder/ReminderReceivers.kt` | CREATE | `ReminderAlarmReceiver`, `RemindersRestoreReceiver` |
| `…/feature/meals/PlanOpenRequests.kt` | CREATE | Inbox „Essensplan öffnen“ (Benachrichtigung → Navigation) |
| `android/feature/meals/src/main/res/drawable/ic_stat_meals.xml` | CREATE | einfarbiges Benachrichtigungs-Icon (Vektor, 24 dp, weiß) |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | Texte für Kanäle, Benachrichtigungen, Hinweis-Karte |
| `…/feature/meals/domain/plan/UseCases.kt` | UPDATE | `SaveDishUseCase`, `DeleteDishUseCase` stoßen Abgleich an |
| `…/feature/meals/domain/cooking/UseCases.kt` | UPDATE | `CancelCookingUseCase`, `WithdrawCancellationUseCase` stoßen Abgleich an |
| `…/feature/meals/ui/plan/WeekPlanViewModel.kt` | UPDATE | `pendingCooking` laden, Flag `showsWhatsAppHint` |
| `…/feature/meals/ui/plan/StatusCards.kt` | UPDATE | `WhatsAppHintCard` |
| `…/feature/meals/ui/plan/WeekPlanScreen.kt` | UPDATE | Karte einbauen; `onShareWhatsApp` in Route (Intent) |
| `…/feature/meals/MealsModule.kt` | UPDATE | neue Singles/Factories, `WeekPlanViewModel` +1 Parameter |
| `android/app/src/main/kotlin/de/familyplanner/android/app/MainActivity.kt` | UPDATE | Berechtigungen gemeinsam anfragen, `onStart` → Abgleich, Intent-Extra → `PlanOpenRequests`, `onNewIntent` |
| `android/app/src/main/kotlin/de/familyplanner/android/app/FamilyPlannerApp.kt` | UPDATE | periodischen Abgleich einplanen |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | `PlanOpenRequests` → `navigate(WeekPlanRoute)` |
| `android/app/build.gradle.kts` | UPDATE | Version 0.7.0/8, Kover-Ausschlüsse Receiver/Worker |
| Tests (siehe Testing Strategy) | CREATE/UPDATE | `ReminderRulesTest`, `reminder/UseCasesTest`, `DataStoreReminderStoreTest`, `AlarmReminderSchedulerTest`, `AndroidNotifierTest`, `fakes/ReminderFakes.kt`, bestehende Use-Case-/ViewModel-/Screen-/Modul-Tests |
| `docs/02-architecture/adr/0013-local-reminders-and-status-sync.md` | CREATE | Mechanik-Entscheidung |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 7)“ |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-050–053 |
| `android/README.md` | UPDATE | „Abnahme-Checkliste Phase 7“ |
| `docs/01-requirements/meals.prd.md` | UPDATE (am Ende) | Phase 7 Bericht-Verweis |

`…` = `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals`

## NOT Building

- Keine API-/Backend-Änderung, kein Push (Firebase/ntfy), kein Fernzugriff (PRD, ADR-0006/0007).
- Keine exakten Alarme / keine Einstellung für `SCHEDULE_EXACT_ALARM` (ADR-0013).
- Keine Einstellungen in der App (Zeitpunkte, Benachrichtigungen an/aus) — Android-Systemeinstellungen je Kanal genügen.
- Keine Eltern-Meldungen außer „neue Einreichung“ (keine Absage-Meldung, keine Frist-Erinnerung an Eltern, keine Bestellfrist-Erinnerung).
- Keine Erinnerung für Gerichte mit ungültigem Tag (`dayValid = false`) und keine nachgeholten Erinnerungen.
- Kein Abgleich anderer Wochen als der Planwoche.
- Keine Erkennung „im Heimnetz“ (SSID o. Ä.) — „Heimnetz“ = Server erreichbar.
- Kein Formular für den WhatsApp-Text in der App — nur Vorlage zum Ergänzen in WhatsApp.

---

## Step-by-Step Tasks

Branch: `feat/erinnerungen-app` von `main` (nach Merge des Plan-PRs). TDD je Task: erst Tests (RED), dann Code (GREEN).

### Task 1: Abhängigkeit, ADR-0013
- **ACTION**: WorkManager einbinden; ADR schreiben.
- **IMPLEMENT**:
  - `libs.versions.toml`: `work = "2.12.0"`; `work-runtime = { module = "androidx.work:work-runtime-ktx", version.ref = "work" }`.
  - `feature/meals/build.gradle.kts`: `implementation(libs.work.runtime)`; `implementation(libs.core.ktx)`.
  - ADR-0013 „Lokale Erinnerungen und Status-Abgleich in der App“ (Vorlage `0000-template.md`, Stil wie ADR-0011): Kontext (PRD:
    lokal statt Push, LAN-only), Entscheidung (lokaler Abgleich-Stand in DataStore; WorkManager periodisch 30 min + sofort bei Start/
    eigener Änderung; `setAndAllowWhileIdle` statt exakter Alarme; Neu-Einplanung bei `BOOT_COMPLETED`/`MY_PACKAGE_REPLACED`;
    Basislinie ohne Meldung; keine API-Änderung), Alternativen (exakte Alarme + Berechtigung; nur WorkManager mit Verzögerung — im
    Doze stundenlang; ntfy/Firebase — PRD verworfen), Konsequenzen (bis ~1 h später; Meldungen nur im Heimnetz; Akku-Optimierung der
    Hersteller kann verzögern → Abnahme auf Familienhandys).
- **GOTCHA**: WorkManager initialisiert sich per `androidx.startup` selbst — **keine** eigene `Configuration.Provider` bauen.
- **VALIDATE**: `cd android && ./gradlew :feature:meals:dependencies --configuration debugRuntimeClasspath | grep work-runtime`

### Task 2: Domäne `reminder` — Modelle, Regeln (reine Funktionen)
- **ACTION**: `domain/reminder/Models.kt`, `ReminderLimits.kt`, `ReminderRules.kt` + `ReminderRulesTest`.
- **IMPLEMENT**:
  ```kotlin
  /** Eingeplante Erinnerung: [daysBefore] Tage vor der Frist der Planwoche [weekStart]. */
  data class Reminder(val weekStart: LocalDate, val daysBefore: Long, val at: Instant)

  /** Zuletzt bekannter Stand eines Gerichts (Vergleich für Statusmeldungen). */
  data class KnownDish(val status: DishStatus, val rejectionReason: String?)

  /** Ergebnis des letzten erfolgreichen Abgleichs; Quelle für Alarme offline und nach Neustart. */
  data class ReminderSnapshot(
      val memberId: String,
      val weekStart: LocalDate,
      val deadline: Instant?,     // nur Kind mit Frist
      val cookState: CookState?,  // nur Kind, das in der Woche kocht
      val reminders: List<Reminder>,
      val dishes: Map<String, KnownDish>,
  )

  sealed interface StatusNotice {
      val dishId: String
      data class Approved(override val dishId: String, val dishName: String, val date: LocalDate) : StatusNotice
      data class Rejected(override val dishId: String, val dishName: String, val date: LocalDate, val reason: String?) : StatusNotice
      data class Submitted(override val dishId: String, val dishName: String, val date: LocalDate, val cookName: String) : StatusNotice
  }

  data class DeadlineReminder(val deadline: Instant, val rejected: Boolean)
  /** Offene Einreichung laut lokalem Stand (WhatsApp-Hinweis, REQ-MEAL-051). */
  data class PendingCooking(val deadline: Instant, val rejected: Boolean)

  enum class SyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }
  ```
  ```kotlin
  object ReminderLimits {
      /** 72/48/24 h vor der Frist, zur Frist-Uhrzeit (REQ-MEAL-050). */
      val DAYS_BEFORE: List<Long> = listOf(3L, 2L, 1L)
      const val SYNC_INTERVAL_MINUTES = 30L
  }
  ```
  `ReminderRules.kt`:
  - `fun needsReminder(state: CookState?): Boolean = state == CookState.NOT_SUBMITTED || state == CookState.REJECTED`
  - `fun reminderTimes(weekStart: LocalDate, deadline: Instant, now: Instant): List<Reminder>` =
    `DAYS_BEFORE.map { Reminder(weekStart, it, deadline.atZone(BerlinZone).minusDays(it).toInstant()) }.filter { it.at > now }`
  - `fun statusNotices(isParent: Boolean, memberId: String, previous: Map<String, KnownDish>?, dishes: List<Dish>, names: Map<String, String>): List<StatusNotice>`:
    `previous == null` → `emptyList()` (Basislinie). Kind: Gerichte mit `cookId == memberId`, Status `APPROVED`/`REJECTED`, und
    `previous[id]` fehlt oder Status bzw. Grund verschieden → `Approved`/`Rejected`. Eltern: Status `SUBMITTED` und `previous[id]?.status
    != SUBMITTED` → `Submitted(cookName = names[cookId] ?: "?")`. `fun Dish.known() = KnownDish(status, rejectionReason)`.
- **MIRROR**: NAMING_CONVENTION; reine Funktionen wie `domain/attendance/PlanWeeks.kt`.
- **IMPORTS**: `de.familyplanner.android.core.ui.BerlinZone`, `…domain.cooking.CookState`, `…domain.plan.Dish/DishStatus`.
- **GOTCHA**: `minusDays` auf `ZonedDateTime`, **nicht** `Instant.minus(Duration.ofHours(72))` (Uhrzeit über Zeitumstellung).
  `when` ohne `else` über `DishStatus`.
- **VALIDATE**: `ReminderRulesTest` grün (Fälle siehe Testing Strategy).

### Task 3: Domäne `reminder` — Ports und Use Cases
- **ACTION**: `Ports.kt`, `UseCases.kt` + `fakes/ReminderFakes.kt` + `domain/reminder/UseCasesTest.kt`.
- **IMPLEMENT**:
  ```kotlin
  interface ReminderStore { suspend fun read(): ReminderSnapshot?; suspend fun write(snapshot: ReminderSnapshot); suspend fun clear() }
  interface ReminderScheduler { fun replace(reminders: List<Reminder>); fun cancelAll() }
  interface Notifier { fun remind(reminder: DeadlineReminder); fun announce(notice: StatusNotice) }
  /** Stößt den Abgleich im Hintergrund an (WorkManager). */
  interface ReminderSync { fun requestNow(); fun schedulePeriodic() }
  ```
  `SyncRemindersUseCase(sessions, cooking: CookingRepository, plan: MealPlanRepository, store, scheduler, notifier, clock)` —
  `suspend operator fun invoke(): SyncOutcome`, intern `mutex.withLock { … }` (Koin `single`, periodischer und sofortiger Abgleich
  können gleichzeitig laufen):
  1. `val session = (sessions.state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session` — `null` → `signOut()`
     (`scheduler.cancelAll(); store.clear()`), `SIGNED_OUT`.
  2. `week = currentPlanWeek(now)`; Kind: `async { cooking.status(week) }` + `async { plan.week(week) }`; Eltern:
     `async { plan.week(week) }` + `async { plan.members() }` (Namen). Status der Kinder wird für Eltern nicht gebraucht.
  3. Fehler: `Unauthorized` → `signOut()`, `SIGNED_OUT`; sonst `UNREACHABLE` ohne Änderung.
  4. `previous = store.read()?.takeIf { it.memberId == session.memberId && it.weekStart == week }?.dishes`
  5. `statusNotices(...)` → `notifier.announce` je Meldung.
  6. Kind: `my = status.cooks.firstOrNull { it.memberId == session.memberId }`; `reminders = if (my?.submissionDeadline != null &&
     needsReminder(my.state)) reminderTimes(week, deadline, now) else emptyList()`; Eltern: leer.
  7. `store.write(ReminderSnapshot(...dishes = plan.days.mapNotNull { it.dish }.associate { it.id to it.known() }))`,
     `scheduler.replace(reminders)`, `SYNCED`.
  In private Funktionen aufteilen (je < 50 Zeilen): `loadChild`, `loadParent`, `signOut`.

  `ShowDueReminderUseCase(sessions, store, notifier, clock)` — `suspend operator fun invoke(weekStart: LocalDate)`: Snapshot lesen;
  nur wenn `memberId == sessions.state…memberId`, `snapshot.weekStart == weekStart`, `needsReminder(cookState)`, `deadline != null`
  und `deadline > now` → `notifier.remind(DeadlineReminder(deadline, cookState == REJECTED))`.

  `RestoreRemindersUseCase(store, scheduler, clock)` — `scheduler.replace(snapshot?.reminders.orEmpty().filter { it.at > now })`.

  `LoadPendingCookingUseCase(sessions, store, clock)` — `suspend operator fun invoke(): PendingCooking?`: Snapshot gehört zum Mitglied,
  `weekStart == currentPlanWeek(now)`, `needsReminder(cookState)`, `deadline != null` → `PendingCooking(deadline, rejected)` (auch nach
  Fristablauf: Einreichen bleibt möglich).
- **MIRROR**: USE_CASE_PATTERN, ERROR_HANDLING.
- **IMPORTS**: `kotlinx.coroutines.sync.Mutex/withLock`, `kotlinx.coroutines.flow.first`, `…core.data.session.SessionState/Role`,
  `…domain.attendance.currentPlanWeek`.
- **GOTCHA**: Im Worker/Receiver ist `sessions.current` nach Prozessstart evtl. noch `null` (DataStore lädt) → **immer** über
  `sessions.state.first { it !is Loading }`. Clock-Default `Clock.system(BerlinZone)` als letzter Parameter (wie `LoadWeekPlanUseCase`).
- **VALIDATE**: `reminder/UseCasesTest` grün.

### Task 4: Abgleich nach eigenen Änderungen anstoßen
- **ACTION**: `SaveDishUseCase`, `DeleteDishUseCase`, `CancelCookingUseCase`, `WithdrawCancellationUseCase` erhalten `reminders: ReminderSync`
  als **letzten** Konstruktor-Parameter und rufen bei `ApiResult.Success` `reminders.requestNow()`.
- **IMPLEMENT**: `return repository.createDish(draft).also { if (it is ApiResult.Success) reminders.requestNow() }` (analog).
- **MIRROR**: bestehende Use Cases unverändert sonst.
- **GOTCHA**: Alle Konstruktor-Aufrufe in Tests (`PlanViewModelsTest`, `CookingViewModelsTest`, `UseCasesTest` plan/cooking, `MealsModuleTest`)
  mit `FakeReminderSync()` ergänzen; Validierungsfehler lösen **keinen** Abgleich aus.
- **VALIDATE**: neue Tests `REQ-MEAL-050 saving a dish requests a reminder sync` / `… failed save does not request a sync`, Cancel/Withdraw analog.

### Task 5: Lokaler Stand (DataStore)
- **ACTION**: `StoredReminders.kt`, `ReminderStoreSerializer.kt`, `DataStoreReminderStore.kt` + `DataStoreReminderStoreTest`.
- **IMPLEMENT**: `@Serializable StoredReminders(version = REMINDER_STORE_VERSION(1), snapshot: StoredSnapshot? = null)`;
  `StoredSnapshot(memberId, weekStart: String, deadlineMillis: Long?, cookState: String?, reminders: List<StoredReminder>, dishes:
  Map<String, StoredKnownDish>)`; Enum-Namen als String, unbekannte Werte → Snapshot `null` (mit `mapAllOrNull`/`parseDate` aus
  `data/Parsing.kt`). `read()` → Domäne oder `null`; `write()`/`clear()` per `updateData`; `IOException` → `Log.w`, kein Wurf.
- **MIRROR**: REPOSITORY_PATTERN / LOCAL STORE (Plan-Cache 1:1).
- **GOTCHA**: Eigene Datei `meal_reminders.json`; **eine** DataStore-Instanz (Koin `single`).
- **VALIDATE**: `DataStoreReminderStoreTest`: Rundreise, `clear`, kaputte Datei → `null`, falsche Version → `null`.

### Task 6: Android-Bausteine (Alarme, Benachrichtigungen, WorkManager, Receiver)
- **ACTION**: `AlarmReminderScheduler.kt`, `AndroidNotifier.kt`, `WorkManagerReminderSync.kt`, `ReminderReceivers.kt`, `PlanOpenRequests.kt`,
  Manifest, Icon, Strings; Tests `AlarmReminderSchedulerTest`, `AndroidNotifierTest` (Robolectric).
- **IMPLEMENT**:
  - `AlarmReminderScheduler(context)`: `replace` = `cancelAll()` und dann je Reminder
    `AlarmManagerCompat.setAndAllowWhileIdle(alarmManager, AlarmManager.RTC_WAKEUP, at.toEpochMilli(), pendingIntent(daysBefore, weekStart))`;
    `pendingIntent`: `Intent(context, ReminderAlarmReceiver::class.java).putExtra(EXTRA_WEEK_START, weekStart.toString())`,
    `PendingIntent.getBroadcast(context, daysBefore.toInt(), intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`. `cancelAll` = für jeden
    Wert aus `DAYS_BEFORE` `getBroadcast(…, FLAG_IMMUTABLE or FLAG_NO_CREATE)?.let { alarmManager.cancel(it); it.cancel() }`.
  - `AndroidNotifier(context)`: legt beim ersten Aufruf Kanäle an (`CHANNEL_REMINDERS` „Erinnerungen“ `IMPORTANCE_HIGH`,
    `CHANNEL_STATUS` „Freigaben“ `IMPORTANCE_DEFAULT`); vor `notify` `ContextCompat.checkSelfPermission(POST_NOTIFICATIONS)` (API 33+)
    sonst `return`. `remind`: Titel `reminder_title_submit`/`reminder_title_revise`, Text `reminder_text` mit `formatDeadline(deadline)`,
    `BigTextStyle`, ID `REMINDER_NOTIFICATION_ID = 1`. `announce`: Titel/Text je `StatusNotice`-Typ (Datum per `weekdayShort` +
    `formatDate`), ID `STATUS_ID_BASE + (dishId.hashCode() and 0xFFFF)`. Alle mit `setSmallIcon(R.drawable.ic_stat_meals)`,
    `setAutoCancel(true)`, `setContentIntent(openPlanIntent())`: `packageManager.getLaunchIntentForPackage(packageName)` +
    `putExtra(PlanOpenRequests.EXTRA_OPEN_PLAN, true)` + `FLAG_ACTIVITY_NEW_TASK or FLAG_ACTIVITY_CLEAR_TOP or FLAG_ACTIVITY_SINGLE_TOP`,
    `PendingIntent.getActivity(…, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`.
  - `WorkManagerReminderSync(context)`: `schedulePeriodic` = `PeriodicWorkRequestBuilder<ReminderSyncWorker>(SYNC_INTERVAL_MINUTES,
    MINUTES).setConstraints(connected)` → `enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, …)`;
    `requestNow` = `OneTimeWorkRequestBuilder<ReminderSyncWorker>().setConstraints(connected)` → `enqueueUniqueWork(NOW_WORK,
    ExistingWorkPolicy.REPLACE, …)`. `class ReminderSyncWorker(context, params) : CoroutineWorker(context, params), KoinComponent`
    mit `private val sync: SyncRemindersUseCase by inject()`; `doWork() { sync(); return Result.success() }` (offline: nächster Lauf).
  - `ReminderReceivers.kt`: `ReminderAlarmReceiver : BroadcastReceiver(), KoinComponent` → `ShowDueReminderUseCase(weekStart)`;
    `RemindersRestoreReceiver` für `ACTION_BOOT_COMPLETED`/`ACTION_MY_PACKAGE_REPLACED` → `RestoreRemindersUseCase()` +
    `ReminderSync.requestNow()`. Gemeinsamer Helfer:
    ```kotlin
    private fun BroadcastReceiver.launchAsync(block: suspend () -> Unit) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch { try { block() } finally { pending.finish() } }
    }
    ```
  - `PlanOpenRequests` (wie `SharedRecipeInbox`): `StateFlow<Boolean> pending`, `request()`, `consume()`, `companion const val EXTRA_OPEN_PLAN`.
  - Manifest (`feature/meals`): `<uses-permission POST_NOTIFICATIONS/>`, `<uses-permission RECEIVE_BOOT_COMPLETED/>`;
    `<receiver android:name=".data.reminder.ReminderAlarmReceiver" android:exported="false"/>`;
    `<receiver android:name=".data.reminder.RemindersRestoreReceiver" android:exported="false">` mit `intent-filter` für
    `BOOT_COMPLETED` und `MY_PACKAGE_REPLACED`.
  - Strings (deutsch, Vorschlag): `reminder_channel` „Erinnerungen“, `status_channel` „Freigaben“, `reminder_title_submit`
    „Dein Kochtag: Gericht einreichen“, `reminder_title_revise` „Dein Kochtag: Gericht überarbeiten“, `reminder_text` „Frist: %1$s.
    Nicht zu Hause? Schick Gericht und Zutaten per WhatsApp an deine Eltern.“, `notice_approved_title` „„%1$s“ ist freigegeben“,
    `notice_approved_text` „Dein Gericht für %1$s steht.“, `notice_rejected_title` „„%1$s“ wurde abgelehnt“, `notice_rejected_text`
    „Begründung: %1$s“, `notice_submitted_title` „%1$s hat ein Gericht eingereicht“, `notice_submitted_text` „„%1$s“ für %2$s wartet
    auf Freigabe.“
- **MIRROR**: KOIN_PATTERN (`KoinComponent`), LOGGING_PATTERN.
- **IMPORTS**: `androidx.core.app.AlarmManagerCompat`, `NotificationCompat`, `NotificationManagerCompat`, `androidx.core.content.ContextCompat`,
  `androidx.work.*`, `org.koin.core.component.KoinComponent/inject`.
- **GOTCHA**: Lint `MissingPermission` → explizite `checkSelfPermission`-Prüfung direkt vor `notify` (sonst schlägt `lintDebug` fehl).
  `exported="false"` empfängt `BOOT_COMPLETED` (vom System gesendet) — auf dem Gerät prüfen; falls nicht, `exported="true"` (geschützte
  System-Broadcasts, unkritisch). Receiver-Arbeit < 10 s (`goAsync`). Release ist minifiziert: Worker/Receiver werden über das Manifest
  bzw. WorkManager-Regeln behalten — `assembleRelease` prüfen.
- **VALIDATE**: `AlarmReminderSchedulerTest` (3 Alarme mit Zeiten; `replace` mit weniger ersetzt; `cancelAll` leert),
  `AndroidNotifierTest` (Erinnerungstext enthält „WhatsApp“ und Frist; abgelehnt → Grund im Text; ohne Berechtigung keine
  Benachrichtigung; Content-Intent trägt `EXTRA_OPEN_PLAN`).

### Task 7: WhatsApp-Hinweis im Essensplan (REQ-MEAL-051)
- **ACTION**: ViewModel, Karte, Route.
- **IMPLEMENT**:
  - `WeekPlanViewModel(sessions, loadWeekPlan, cooking, loadPendingCooking: LoadPendingCookingUseCase, clock)`; in `load()` zusätzlich
    `viewModelScope.launch { _state.update { it.copy(pendingCooking = loadPendingCooking()) } }` (nur Kind).
  - `WeekPlanUiState`: `val pendingCooking: PendingCooking? = null`;
    `val showsWhatsAppHint: Boolean get() = !isParent && pendingCooking != null && (isOffline || error.isUnreachable() || statusError.isUnreachable())`
    mit `private fun AppError?.isUnreachable() = this == AppError.Network || this == AppError.Certificate`.
  - `StatusCards.kt`: `@Composable fun WhatsAppHintCard(pending: PendingCooking, onShare: () -> Unit)` — Titel „Nicht zu Hause?“, Text
    „Schick dein Gericht und die Zutaten per WhatsApp an deine Eltern – sie tragen es für dich ein.“, Frist-Zeile (`cooking_deadline`),
    bei `rejected` Zusatz „Dein Gericht wurde abgelehnt – schick die überarbeitete Fassung.“, `Button` „Per WhatsApp schicken“.
  - `WeekPlanScreen(…, onShareWhatsApp: () -> Unit = {})`: `if (state.showsWhatsAppHint) item { WhatsAppHintCard(...) }` direkt nach dem
    Offline-Hinweis. `WeekPlanRoute` baut den Intent: `ACTION_SEND`, `text/plain`, `EXTRA_TEXT` = `whatsapp_template` („Mein Gericht für
    meinen Kochtag:\n\nZutaten:\n“), `setPackage("com.whatsapp")`; bei `ActivityNotFoundException` dasselbe ohne Paket per
    `Intent.createChooser`.
- **MIRROR**: VIEWMODEL_PATTERN, SCREEN_PATTERN.
- **GOTCHA**: neuer Screen-Parameter mit Default am Ende (bestehende Tests bleiben gültig). `switchWeek` setzt `pendingCooking` **nicht**
  zurück (gilt wochenunabhängig).
- **VALIDATE**: `PlanViewModelsTest`: `REQ-MEAL-051 child offline with pending cooking shows whatsapp hint`, `… parent never`, `… online no hint`;
  `CookingScreensTest`: Karte sichtbar, Knopf ruft `onShareWhatsApp`.

### Task 8: App-Verdrahtung (Koin, Start, Berechtigung, Navigation)
- **ACTION**: `MealsModule.kt`, `FamilyPlannerApp.kt`, `MainActivity.kt`, `AppNavHost.kt`.
- **IMPLEMENT**:
  - `MealsModule` Abschnitt `// reminder`: `single<ReminderStore> { DataStoreReminderStore(DataStoreFactory.create(ReminderStoreSerializer(get()),
    produceFile = { androidContext().dataStoreFile(REMINDER_STORE_FILE) })) }`, `single<ReminderScheduler> { AlarmReminderScheduler(androidContext()) }`,
    `single<Notifier> { AndroidNotifier(androidContext()) }`, `single<ReminderSync> { WorkManagerReminderSync(androidContext()) }`,
    `single { SyncRemindersUseCase(get(), get(), get(), get(), get(), get()) }` (single wegen Mutex), `factory { ShowDueReminderUseCase(…) }`,
    `factory { RestoreRemindersUseCase(…) }`, `factory { LoadPendingCookingUseCase(…) }`, `single { PlanOpenRequests() }`; Use Cases aus
    Task 4 mit `get()` ergänzen; `viewModel { WeekPlanViewModel(get(), get(), get(), get()) }`.
  - `FamilyPlannerApp.onCreate`: nach `startKoin` → `get<ReminderSync>().schedulePeriodic()` (`org.koin.android.ext.android.get`).
  - `MainActivity`: `RequestMultiplePermissions` für Heimnetz (API ≥ 37) **und** `POST_NOTIFICATIONS` (API ≥ 33), nur nicht erteilte;
    `onStart()` → `get<ReminderSync>().requestNow()`; `receiveOpenPlan(intent)` in `onCreate` (wenn `savedInstanceState == null`) und
    `onNewIntent` → `get<PlanOpenRequests>().request()` bei `EXTRA_OPEN_PLAN`.
  - `AppNavHost.MainHost`: zweiter `LaunchedEffect` auf `PlanOpenRequests.pending` → `consume()` + `navController.navigate(WeekPlanRoute)`.
- **MIRROR**: KOIN_PATTERN, INBOX_NAVIGATION_PATTERN.
- **GOTCHA**: Abgemeldet bleibt der Wunsch liegen (MainHost existiert erst angemeldet) — gewollt. `MealsModuleTest` prüft den Graphen:
  `androidContext` dort per Robolectric bereitstellen bzw. wie bestehend; neue Singles müssen auflösbar sein.
- **VALIDATE**: `MealsModuleTest` grün; `AppNavHostTest` (falls Inbox getestet) analog Rezept-Inbox ergänzen.

### Task 9: Version, Kover, Doku, Traceability, Abnahme-Checkliste
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - `app/build.gradle.kts`: `versionCode = 8`, `versionName = "0.7.0"`; Kover-`classes(...)` + `"*Receiver*"`, `"*ReminderSyncWorker*"`,
    `"*WorkManagerReminderSync*"` mit Kommentar „Android-Einstiegspunkte (Alarm, Neustart, WorkManager): nur auf dem Gerät prüfbar (ADR-0013)“.
  - `docs/03-design/meals.md`: Abschnitt „## App-Seite (Phase 7)“ — Tabelle Regeln (Zeitpunkte, Zustände, Basislinie, Meldungsregeln Kind/Eltern,
    Abgleich-Auslöser, Ablauf bei Alarm/Neustart), Bausteine (`domain/reminder`, `data/reminder`), Unit-Testfälle.
  - `docs/04-verification/traceability.md`: Zeilen REQ-MEAL-050–053 mit Tests (Unit: `ReminderRulesTest`, `UseCasesTest` (reminder),
    `PlanViewModelsTest`, `CookingScreensTest`; Integration: `DataStoreReminderStoreTest`, `AlarmReminderSchedulerTest`, `AndroidNotifierTest`;
    Abnahme: README Phase 7), Status „umgesetzt (App)“.
  - `android/README.md`: „## Abnahme-Checkliste Phase 7 (manuell, echte Geräte gegen den Pi)“ = Manual Validation unten.
  - PRD Phase 7: Bericht-Verweis ergänzen (Status bleibt `in-progress` bis zur Geräte-Abnahme).
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-050 reminders come 3, 2 and 1 day before the deadline at deadline time` | Frist Do 15.10.2026 16:00, jetzt Sa 10.10. | Mo 12./Di 13./Mi 14. 16:00 Berlin | |
| `REQ-MEAL-050 reminders already past are skipped` | jetzt Di 13.10. 17:00 | nur Mi 14. 16:00 | ja |
| `REQ-MEAL-050 reminders keep the wall-clock time across the DST change` | Frist Di 27.10.2026 16:00 (Umstellung So 25.10.) | Sa 24./So 25./Mo 26. jeweils 16:00 lokal | ja |
| `REQ-MEAL-050 only open or rejected cooking needs reminders` | alle `CookState` + `null` | true nur NOT_SUBMITTED, REJECTED | |
| `REQ-MEAL-052 first sync of a week only records a baseline` | previous = null | keine Meldung | ja |
| `REQ-MEAL-052 own rejected dish is announced with reason` | SUBMITTED → REJECTED „kein Gemüse“ | `Rejected(reason)` | |
| `REQ-MEAL-052 own approved dish is announced once` | 2× gleicher Stand | 1 Meldung, danach keine | ja |
| `REQ-MEAL-052 new rejection reason is announced again` | REJECTED „a“ → REJECTED „b“ | `Rejected("b")` | ja |
| `REQ-MEAL-052 dishes of other cooks are ignored for a child` | fremdes Gericht APPROVED | keine Meldung | |
| `REQ-MEAL-053 newly submitted dish is announced to parents with cook name` | neu SUBMITTED von c-1 | `Submitted(cookName = "Ben")` | |
| `REQ-MEAL-053 resubmission after approval is announced again` | APPROVED → SUBMITTED | Meldung | |
| `REQ-MEAL-053 approved parent dishes never notify parents` | neues APPROVED | keine Meldung | |
| `REQ-MEAL-050 sync schedules reminders for an open child` (UseCases) | Kind NOT_SUBMITTED | `scheduler.replaced` = 3, Snapshot gespeichert | |
| `REQ-MEAL-050 sync clears reminders after submission` | Kind SUBMITTED | `replace(emptyList())` | |
| `REQ-MEAL-050 sync keeps reminders when the server is unreachable` | `AppError.Network` | Scheduler/Store unberührt, `UNREACHABLE` | ja |
| `REQ-MEAL-050 signed out or unauthorized cancels reminders` | keine Session / `Unauthorized` | `cancelAll`, `clear`, `SIGNED_OUT` | ja |
| `REQ-MEAL-053 parent sync announces submissions and schedules nothing` | Eltern-Session | `announce(Submitted)`, `replace(emptyList())` | |
| `REQ-MEAL-052 snapshot of another member is treated as baseline` | Store gehört p-1, Session c-1 | keine Meldung | ja |
| `REQ-MEAL-050 due reminder is shown while cooking is still open` | Snapshot NOT_SUBMITTED | `remind(rejected=false)` | |
| `REQ-MEAL-050 due reminder is suppressed after submission` | Snapshot SUBMITTED | nichts | |
| `REQ-MEAL-050 due reminder for another week or member is suppressed` | weekStart/memberId abweichend | nichts | ja |
| `REQ-MEAL-050 restore reschedules only future reminders` | 1 vergangen, 2 künftig | `replace` mit 2 | ja |
| `REQ-MEAL-051 pending cooking is loaded from the local snapshot` | Snapshot REJECTED | `PendingCooking(rejected=true)` | |
| `REQ-MEAL-050 saving a dish requests a reminder sync` / failed save not | Save/Delete/Cancel/Withdraw | `FakeReminderSync.requests` | ja |
| `DataStoreReminderStoreTest` | Rundreise, clear, kaputt, Version | wie Plan-Cache | ja |
| `AlarmReminderSchedulerTest` (Robolectric) | 3 Reminder → replace(1) → cancelAll | 3 → 1 → 0 Alarme | |
| `AndroidNotifierTest` (Robolectric) | remind/announce, mit/ohne Berechtigung | Texte, Kanal, Intent-Extra; ohne Berechtigung 0 | ja |
| `REQ-MEAL-051 …` ViewModel/Screen | siehe Task 7 | | |

Fakes in `fakes/ReminderFakes.kt`: `FakeReminderStore` (Feld `snapshot`), `FakeReminderScheduler` (`replaced: List<List<Reminder>>`,
`cancelled: Int`), `FakeNotifier` (`reminders`, `notices`), `FakeReminderSync` (`requests`, `periodic`).

### Edge Cases Checklist
- [x] Kein Kochen in der Woche / keine Frist → keine Reminder
- [x] Frist schon vorbei → keine Reminder, Hinweis-Karte offline trotzdem
- [x] Zeitumstellung
- [x] Gleichzeitiger periodischer und sofortiger Abgleich (Mutex)
- [x] Server nicht erreichbar / gestört (Stand bleibt)
- [x] Gerät entfernt (401) / abgemeldet
- [x] Benachrichtigungs-Berechtigung verweigert
- [x] Neustart / App-Update (Restore-Receiver)
- [x] Wochenwechsel (neue Planwoche → Basislinie)

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Funde

### Unit Tests
```bash
cd android && ./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug assembleRelease
```
EXPECT: grün, Kover ≥ 80 %; Release (R8) baut

### Database Validation
N/A — keine Backend-/Schemaänderung. Gegenprobe: `git diff main -- api/ backend/` ist leer.

### Gegen lokales Backend (Emulator, API 36/37)
```bash
cd backend && ./gradlew run          # Port 8095, siehe android/README.md „Gegen ein lokales Backend“
cd android && ./gradlew installDebug # mit fp.serverUrl=http://localhost:8095/ in local.properties
adb shell dumpsys alarm | grep -A3 de.familyplanner.android        # eingeplante Erinnerungen
adb shell cmd jobscheduler run -f de.familyplanner.android 1        # ggf. WorkManager-Job erzwingen (Job-ID aus dumpsys jobscheduler)
adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -p de.familyplanner.android  # Restore prüfen (Emulator, root)
```
EXPECT: Kind ohne Einreichung → 3 Alarme (bzw. die künftigen); nach Einreichen → keine; Zeit im Emulator auf einen Alarm stellen →
Benachrichtigung mit WhatsApp-Hinweis; Eltern lehnen ab → nach Abgleich Kind-Benachrichtigung mit Grund; Kind reicht ein → Eltern-Meldung.

### Manual Validation (Abnahme Phase 7 – echte Handys gegen den Pi)
- [ ] Release-APK 0.7.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy; beim Start Benachrichtigungen erlauben
- [ ] Akku-Optimierung der App auf den Kinderhandys „nicht eingeschränkt“ (Herstellereinstellung), Ergebnis notieren
- [ ] Kinderhandy, Kind kocht in der Planwoche, nichts eingereicht: `dumpsys alarm` zeigt die künftigen Erinnerungen (72/48/24 h)
- [ ] Erinnerung kommt zur Frist-Uhrzeit (± 1 h) — mindestens eine im **Mobilnetz/außer Haus**; Text nennt Frist und WhatsApp-Hinweis
- [ ] Tippen öffnet den Essensplan; außer Haus erscheint die Karte „Nicht zu Hause?“; „Per WhatsApp schicken“ öffnet WhatsApp mit Vorlage
- [ ] Kind reicht ein → keine weiteren Erinnerungen dieser Woche
- [ ] Elternhandy: nach Einreichung (≤ 30 min im WLAN) Meldung „Ben hat ein Gericht eingereicht“; Tippen öffnet Essensplan
- [ ] Eltern lehnen mit Begründung ab → Kinderhandy meldet „wurde abgelehnt“ mit Grund; nächste Erinnerung heißt „überarbeiten“
- [ ] Eltern geben frei → Kind erhält „ist freigegeben“
- [ ] Eltern tragen stellvertretend ein → Eltern erhalten keine Meldung
- [ ] Kinderhandy neu starten (außer Haus) → Erinnerung kommt trotzdem
- [ ] Kind sagt ab → keine Erinnerungen mehr
- [ ] Erfolgssignal Phase 7: Kinder erhalten Erinnerungen auch außer Haus pünktlich (eine Pilotwoche)

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-050–053 haben Tests mit REQ-ID im Namen
- [ ] Erinnerungen 72/48/24 h vor der Frist, nur bei offenem oder abgelehntem Gericht, auch offline und nach Neustart
- [ ] Kind: Meldung bei Freigabe/Ablehnung (mit Grund); Eltern: Meldung bei neuer Einreichung; erster Abgleich meldet nichts
- [ ] WhatsApp-Hinweis in der Erinnerung und offline im Essensplan
- [ ] `api/` und `backend/` unverändert; einzige neue Bibliothek WorkManager
- [ ] ADR-0013, Design, Traceability, README-Checkliste aktualisiert; Geräte-Abnahme dokumentiert

## Completion Checklist
- [ ] Code folgt den Mustern (Use Cases mit `invoke`, Ports in `domain`, DataStore wie Plan-Cache, State/Event-ViewModel)
- [ ] Fehlerbehandlung: Abgleich-Fehler ändern nichts; IO-Fehler `Log.w`; kein Absturz ohne Berechtigung
- [ ] Logging nur `Log.w` mit `TAG`, keine Inhalte/Token
- [ ] Tests nach den bestehenden Mustern (Fakes, `runTest`, Robolectric)
- [ ] Keine Magic Numbers (`ReminderLimits`, benannte IDs/Kanäle)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Hersteller-Akkuoptimierung (z. B. Xiaomi/Samsung) unterdrückt Alarme oder WorkManager | M | H | `allow while idle`; Abnahme auf echten Familienhandys; README: Akku „nicht eingeschränkt“ |
| Hintergrund-Abgleich auf Android 17 ohne Heimnetz-Berechtigung blockiert | M | M | Berechtigung beim Start; in der Abnahme Eltern-Meldung bei geschlossener App prüfen; sonst Folgeaufgabe |
| Inexakter Alarm kommt deutlich später als erwartet | L | M | Abnahme misst Abweichung; bei > 1 h ADR-0013 überdenken (exakte Alarme) |
| `BOOT_COMPLETED` mit `exported=false` kommt nicht an | L | M | Gerät prüfen; dann `exported=true` |
| Doppelte Meldungen bei parallelem Abgleich | L | L | `Mutex` in `SyncRemindersUseCase` (Koin `single`) |
| Konstruktor-Änderung von 4 Use Cases bricht viele Tests | H | L | `FakeReminderSync` zentral in `fakes/`; mechanisch ergänzen |
| R8 entfernt Worker-Konstruktor | L | H | WorkManager liefert Keep-Regeln; `assembleRelease` + Gerätetest mit Release-APK |

## Notes
- Phase 7 hängt nur an Phase 4; 6b ist abgenommen, keine parallel laufende App-Arbeit — Dateikonflikte mit anderen Phasen ausgeschlossen.
- Dieser Plan-Branch basiert auf dem offenen Doku-PR der 6b-Abnahme (`docs/einkaufsliste-abnahme-6b`); erst den mergen, dann diesen.
- Phase 8 (Pilot) braucht Phase 6 und 7 abgeschlossen.
- Die Implementierung erfolgt in einer **neuen Session** (Standard: Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/erinnerungen-app.plan.md`,
  auf einem neuen Branch `feat/erinnerungen-app` von `main`.
