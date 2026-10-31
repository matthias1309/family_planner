# Plan: Echte Belohnungen in der App (GAME Phase 3b)

## Summary
Das Feature-Modul `feature:gamification` macht die Belohnungen aus Phase 3a nutzbar: Kinder sehen unter **„Ich“** ihr **Sparziel**
mit Fortschritt (auch auf **„Heute“**) und öffnen die Seite **„Belohnungen“** (Liste, Sparziel wählen, einlösen, eigene Einlösungen).
Eltern öffnen unter **„Familie“** die Seite **„Belohnungen verwalten“** (wartende Einlösungen erfüllen/ablehnen, Belohnungen
anlegen/ändern/deaktivieren). Ein **Hintergrund-Abgleich** (WorkManager, ADR-0013) meldet Eltern neue Einlösungen und Kindern
„erfüllt“/„abgelehnt“ als Systembenachrichtigung. Keine Backend- oder API-Änderung — Vertrag `openapi.yaml` **0.9.0** ist bereits in
`core:api` generiert.

## User Story
Als Kind möchte ich auf eine echte Belohnung sparen, meinen Fortschritt sehen und sie einlösen; als Elternteil möchte ich Belohnungen
ausloben und Einlösungen bestätigen oder ablehnen und davon mitbekommen, ohne die App ständig zu öffnen.

## Problem → Solution
Backend kennt Belohnungen, Sparziel und Einlösungen, die App zeigt nichts davon → Kind- und Eltern-Bildschirme, Sparziel auf „Ich“ und
„Heute“, Benachrichtigungen, sodass Phase 3 auf den Testhandys abgenommen werden kann.

## Metadata
- **Complexity**: Large (≈ 25 neue, ≈ 15 geänderte Dateien; bekannte Muster aus Phase 1b/2b und dem Aufgaben-Abgleich, keine neue Bibliothek
  außer `work-runtime` im Modul `gamification`, das `tasks`/`meals` schon nutzen)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: Phase 3 — Echte Belohnungen, Teil **3b App**
- **Requirements**: REQ-GAME-040, REQ-GAME-041 (erstes Kriterium; zweites → Phase 4), REQ-GAME-042, REQ-GAME-043
- **Version**: App 0.12.0 / `versionCode` 13

---

## Entscheidungen (Planung 2026-10-31)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Hinweise | **Systembenachrichtigung** über eigenen WorkManager-Abgleich im Modul `gamification` (periodisch 30 min + sofort bei `MainActivity.onStart`), Muster `SyncTaskNoticesUseCase` | Entscheidung Nutzer 2026-10-31; REQ-GAME-042 „Eltern erhalten einen Hinweis“, PRD „Hinweise … per Hintergrund-Abgleich (ADR-0013)“ |
| Wer bekommt was | Eltern: neue `PENDING`-Einlösung → „Ben möchte 🎢 Freizeitpark einlösen“. Kind: eigene Einlösung `PENDING → FULFILLED` („erfüllt“) bzw. `→ REJECTED` („abgelehnt: <Grund>“) | REQ-GAME-042/043 |
| Basislinie | Ohne gespeicherten Abgleich-Stand (Erststart, Update, Neuinstallation, anderes Mitglied) **keine** Meldung | ADR-0013 Nr. 5; wie Aufgaben |
| Verpasste Übergänge (Kind) | Unbekannte Einlösung, die schon entschieden ist und deren `decidedAt` nach dem letzten Abgleich liegt → trotzdem melden. Zusätzlich nach eigenem Einlösen `RewardSync.requestNow()` | Eltern können entscheiden, bevor der Kind-Abgleich die neue Einlösung je gesehen hat |
| Eltern-UI | **Eigene Seite** `ManageRewardsRoute` (ohne Leiste), erreichbar über Zeile „Belohnungen · 2 warten“ im Abschnitt „Punkte“ unter „Familie“. Oben „Wartet auf euch“ (Erfüllt / Ablehnen), darunter Belohnungsliste (antippen → ändern, Deaktivieren), Button „Belohnung anlegen“ | Entscheidung Nutzer 2026-10-31 |
| Kind-UI | Auf „Ich“ nach den Kacheln eine **Sparziel-Karte** (Emoji, Titel, Balken, „120/200“, „Einlösbar!“) bzw. „Noch kein Sparziel“; Button „Belohnungen“ → Seite `RewardsRoute` (ohne Leiste): Liste aktiver Belohnungen mit Preis und eigenem Fortschritt, Aktion „Sparziel“/„Kein Sparziel mehr“, „Einlösen“ (nur wenn Guthaben reicht, mit Bestätigungsdialog), darunter „Meine Einlösungen“ (Status, Ablehnungsgrund) | REQ-GAME-040/041/042/043 |
| „Heute“ | Karte `TodaySavingsCard` nur für Kinder **mit** Sparziel; Tippen öffnet „Belohnungen“ | REQ-GAME-041 „auf Ich und Heute“ |
| Offline | Sparziel im lokalen Punkte-Stand (`game_points.json`, **Version 3**) → „Ich“ und „Heute“ zeigen es offline. Belohnungs- und Verwaltungsseite **nur online** (Aktionen brauchen den Server) | ADR-0011; wie Geschwister-/Eltern-Badges |
| Laden „Ich“ | `LoadPointsUseCase` lädt für Kinder zusätzlich `savingsGoal(memberId)` parallel; ein Fehler → nichts speichern (bestehende Regel) | kein halber Stand |
| Port | Eigener Port **`RewardRepository`** (+ `RemoteRewardRepository` auf derselben `GamificationApi`). `LoadPointsUseCase` bekommt ihn zusätzlich | 11 Endpunkte würden `PointsRepository` überladen; klare Dateigrenzen zu Phase 4 |
| Deaktivierte Belohnungen | Werden nirgends gelistet (`includeArchived = false`); ein Sparziel auf eine deaktivierte Belohnung zeigt „nicht mehr einlösbar“ | YAGNI; Backend lässt deaktivierte nicht ändern |
| Eltern-Namen | Wartende Einlösungen zeigen den Kindnamen aus `PointsRepository.leaderboard()` (parallel geladen); fehlt er → „Kind“ | Einlösung liefert nur `memberId` |
| Kauf-Hinweis (REQ-GAME-041, 2. Kriterium) | **Nicht** in 3b — es gibt noch keinen Shop. Wandert in Phase 4 (Avatar & Shop), in PRD/Traceability vermerken | Abhängigkeit |

---

## UX Design

### Before
```
┌ Ich ──────────────────────────┐   ┌ Familie (Eltern) ─────────┐
│ [Gesammelt 42] [Guthaben 120] │   │ Punkte                    │
│ Platz 2 von 3 · Rangliste     │   │ Rangliste · Kochpunkte: 3 │
│ Badges 4/12 · Verlauf         │   └───────────────────────────┘
└───────────────────────────────┘
```

### After
```
┌ Ich ──────────────────────────┐   ┌ Zurück ─────────────────────────┐
│ [Gesammelt 42] [Guthaben 120] │   │ Belohnungen                     │
│ ┌ Sparziel ─────────────────┐ │   │ 🎢 Freizeitpark   200 P  ★Ziel  │
│ │ 🎢 Freizeitpark            │ │   │ ▓▓▓▓▓▓░░░░ 120/200              │
│ │ ▓▓▓▓▓▓░░░░ 120/200         │ │   │ [Kein Sparziel mehr] [Einlösen]│ (disabled)
│ └───────────────────────────┘ │   │ 🍕 Pizzaabend      80 P         │
│ [ Belohnungen › ]             │   │ ▓▓▓▓▓▓▓▓▓▓ 80/80                │
│ Platz 2 … Badges … Verlauf    │   │ [Als Sparziel]      [Einlösen]  │
└───────────────────────────────┘   │ Meine Einlösungen               │
                                    │ 🍕 Pizzaabend 80 P · wartet     │
┌ Heute (Kind) ─────────────────┐   │ 🎬 Kino 50 P · abgelehnt: Ferien│
│ Sparziel 🎢 Freizeitpark      │   └─────────────────────────────────┘
│ ▓▓▓▓▓▓░░░░ 120/200            │
└───────────────────────────────┘   ┌ Familie (Eltern) ─────────┐   ┌ Zurück ──────────────────────────┐
                                    │ Punkte                    │   │ Belohnungen verwalten            │
🔔 „Ben möchte 🍕 Pizzaabend        │ Rangliste · Kochpunkte: 3 │   │ Wartet auf euch                  │
    einlösen“ (Eltern)              │ Belohnungen · 1 wartet  › │   │ Ben · 🍕 Pizzaabend · 80 P       │
🔔 „🍕 Pizzaabend erfüllt“ /        └───────────────────────────┘   │        [Ablehnen] [Erfüllt]      │
   „🎬 Kino abgelehnt: Ferien“ (Kind)                               │ Belohnungen                      │
                                                                    │ 🎢 Freizeitpark 200 P [Deaktiv.] │
                                                                    │ [ Belohnung anlegen ]            │
                                                                    └──────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| „Ich“ | – | Sparziel-Karte + Button „Belohnungen“ | Karte offline aus lokalem Stand; Button offline deaktiviert |
| „Belohnungen“ (Kind) | – | Sparziel setzen/entfernen, Einlösen (Bestätigung), Einlösungen | nur online; 409 → Fehlermeldung, neu laden |
| „Heute“ (Kind) | – | Karte Sparziel (nur wenn gesetzt) | tippen → „Belohnungen“ |
| „Familie“ → Punkte | Rangliste, Kochpunkte | + Zeile „Belohnungen · n warten“ | offline nicht antippbar |
| „Belohnungen verwalten“ | – | Erfüllt / Ablehnen (Grund), Anlegen/Ändern (Dialog), Deaktivieren (Bestätigung) | nur online |
| Benachrichtigung | – | Eltern: neue Einlösung; Kind: erfüllt/abgelehnt | tippen öffnet Verwaltungs- bzw. Belohnungsseite |

---

## Mandatory Reading

`…` = `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification`

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `api/openapi.yaml` | Pfade 1042–1258, Schemas `Reward`…`RewardListResponse` 2376–2470 (+ `RedemptionResponse`, `RedemptionListResponse`, `SavingsGoalResponse` direkt danach) | Vertrag 0.9.0 |
| P0 | `docs/03-design/gamification.md` | „Belohnungen (Phase 3a)“ 91–137 | Zustände, Rechte, 409-Fälle, Grenzen |
| P0 | `android/core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/GamificationApi.kt` | 45–329 | Methodennamen/Signaturen (nach `./gradlew :core:api:openApiGenerate` bzw. Build vorhanden) |
| P0 | `…/domain/UseCases.kt` | all | `LoadPointsUseCase.fetch` wird um Sparziel erweitert |
| P0 | `…/data/StoredPoints.kt`, `PointsCacheSerializer.kt` | all | Cache-Format → Version 3 |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/notice/*.kt`, `data/WorkManagerTaskSync.kt`, `data/AndroidTaskNotifier.kt`, `TaskOpenRequests.kt` | all | Vorlage Abgleich, Mutex, Basislinie, Notifier, Öffnen per Intent |
| P0 | `…/ui/me/MeScreen.kt`, `MeViewModel.kt`, `…/ui/family/FamilyPointsSection.kt`, `FamilyPointsViewModel.kt` | all | werden erweitert |
| P1 | `…/ui/account/ChildAccountScreen.kt`, `ChildAccountViewModel.kt`, `BookPointsDialog.kt` | all | Muster Unterseite, Args, Dialog-Entwurf (`BookingDraft`), `isBusy`/`actionError` |
| P1 | `…/ui/components/BadgeList.kt`, `StatTiles.kt` | all | Fortschrittsbalken, Kreis, Konstanten |
| P1 | `…/data/Mappers.kt`, `Parsing.kt`, `RemotePointsRepository.kt` | all | `apiCall`, `mapAllOrNull`, `parseInstant` |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt`, `MainActivity.kt` (47–67), `FamilyPlannerApp.kt`, `TopLevelDestination.kt` | all | Einhängen „Heute“, Familie, Open-Request, Sync planen |
| P1 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/today/TodayTasksCard.kt` | all | Muster „Heute“-Karte |
| P1 | Tests: `android/feature/gamification/src/test/kotlin/.../fakes/Fakes.kt`, `ui/ViewModelsTest.kt`, `ui/ScreensTest.kt`, `domain/UseCasesTest.kt`, `data/*Test.kt`, `GamificationModuleTest.kt`; `android/feature/tasks/src/test/.../notice/*` (falls vorhanden) | – | Testmuster |
| P1 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppPointsFakes.kt`, `AppNavHostTest.kt` (Z. 130, 220–236) | – | müssen mit neuen Ports/VM-Konstruktoren kompilieren |
| P2 | `docs/02-architecture/adr/0013-local-reminders-and-status-sync.md` | all | Rahmen Abgleich/Benachrichtigung |

## External Documentation
Keine externe Recherche nötig — WorkManager, NotificationCompat, Material3 (`LinearProgressIndicator`, `AlertDialog`,
`OutlinedTextField`) werden im Projekt bereits genauso verwendet.

---

## Patterns to Mirror

### GENERATED_CLIENT (Vertrag 0.9.0)
```kotlin
// SOURCE: android/core/api/build/generated/.../client/GamificationApi.kt
suspend fun listRewards(@Query("includeArchived") includeArchived: Boolean? = false): Response<RewardListResponse>
suspend fun createReward(@Body rewardInput: RewardInput): Response<RewardResponse>
suspend fun updateReward(@Path("rewardId") rewardId: String, @Body rewardInput: RewardInput): Response<RewardResponse>
suspend fun archiveReward(@Path("rewardId") rewardId: String): Response<Unit>
suspend fun redeemReward(@Path("rewardId") rewardId: String): Response<RedemptionResponse>
suspend fun listRedemptions(@Query("status") status: RedemptionStatus? = null, @Query("limit") limit: Int? = 50): Response<RedemptionListResponse>
suspend fun fulfillRedemption(@Path("redemptionId") redemptionId: String): Response<RedemptionResponse>
suspend fun rejectRedemption(@Path("redemptionId") redemptionId: String, @Body rejectRedemptionRequest: RejectRedemptionRequest): Response<RedemptionResponse>
suspend fun getSavingsGoal(@Path("memberId") memberId: String): Response<SavingsGoalResponse>
suspend fun setSavingsGoal(@Path("memberId") memberId: String, @Body savingsGoalInput: SavingsGoalInput): Response<SavingsGoalResponse>
suspend fun clearSavingsGoal(@Path("memberId") memberId: String): Response<Unit>
// Modelle: de.familyplanner.api.model.Reward(id, title, price, archived, emoji?), RewardInput(title, price, emoji?),
//   Redemption(id, memberId, rewardId, title, price, status: RedemptionStatus, requestedAt: String, emoji?, rejectionReason?, decidedAt?),
//   RedemptionStatus { PENDING, FULFILLED, REJECTED }, RejectRedemptionRequest(reason), SavingsGoalInput(rewardId),
//   SavingsGoal(memberId, balance, redeemable, reward: Reward?, progress: BadgeProgress?)
//   *Response.data ist nullable; RewardListResponse/RedemptionListResponse.data ist List<…>?
```
Feldreihenfolge der generierten Konstruktoren vor Verwendung im generierten Code prüfen (Pflichtfelder zuerst).

### REPOSITORY / apiCall
```kotlin
// SOURCE: …/data/RemotePointsRepository.kt:22-28
override suspend fun badges(memberId: String): ApiResult<BadgeOverview> =
    apiCall(json, { api.getBadges(memberId) }) { it.data?.toDomain() }
// 204-Antworten: SOURCE android/feature/tasks/.../data/RemoteCatalogRepository.kt:27
override suspend fun archive(id: String): ApiResult<Unit> = apiCallNoContent(json) { tasks.archiveCatalogTask(id) }
```
`apiCall` liefert `AppError.Server`, wenn der Mapper `null` liefert; 409 → `AppError.Conflict(message)` mit Server-Text.

### MAPPER (strikt, null = Serverfehler)
```kotlin
// SOURCE: …/data/Mappers.kt:29-38
internal fun ApiBadge.toDomain(): Badge? {
    val awarded = awardedAt?.let { parseInstant(it) ?: return null }
    if (earned && awarded == null) return null
    return Badge(...)
}
internal fun ApiBadgeOverview.toDomain(): BadgeOverview? {
    val badges = items.mapAllOrNull { it.toDomain() } ?: return null
    return BadgeOverview(memberId, earnedCount, totalCount, badges)
}
```

### CACHE FORMAT (getrennt von Domäne, Version, Epoch-Millis)
```kotlin
// SOURCE: …/data/StoredPoints.kt (CACHE_VERSION = 2, „2: Badges (Phase 2b)“)
@Serializable data class StoredPoints(val version: Int = CACHE_VERSION, val memberId: String? = null, val syncedAtMillis: Long = 0,
    val account: StoredAccount? = null, val leaderboard: List<StoredRankedChild> = emptyList(), val badges: StoredBadgeOverview? = null)
```
Serializer verwirft jede andere Version.

### USE CASE (parallel, erster Fehler zählt)
```kotlin
// SOURCE: …/domain/UseCases.kt:45-66
coroutineScope {
    val account = if (isChild) async { repository.account(memberId, PointsLimits.HISTORY_LIMIT) } else null
    val leaderboard = async { repository.leaderboard() }
    val badges = if (isChild) async { repository.badges(memberId) } else null
    ...
    val failure = listOfNotNull(accountResult, leaderboardResult, badgesResult).filterIsInstance<ApiResult.Failure>().firstOrNull()
    if (failure != null) return@coroutineScope failure
    ApiResult.Success(Fetched(...))
}
```

### VALIDATION (Domäne, Grenzen als Objekt)
```kotlin
// SOURCE: …/domain/Validation.kt:7-17, PointsLimits.kt
fun validateManualEntry(amount: Int, reason: String): AppError.Validation? = when { … -> AppError.Validation("Betrag muss …"); else -> null }
```

### VIEWMODEL (StateFlow, Laden über ON_RESUME, Aktionen mit isBusy/actionError)
```kotlin
// SOURCE: …/ui/account/ChildAccountViewModel.kt:102-146
private fun refresh() { loadJob?.cancel(); _state.update { it.copy(isLoading = true, error = null) }; loadJob = viewModelScope.launch { … } }
private fun submit() {
    if (current.isBusy || !draft.canSubmit) return
    _state.update { it.copy(isBusy = true, bookingError = null) }
    viewModelScope.launch { when (val result = bookPoints(…)) {
        is ApiResult.Success -> { _state.update { it.copy(isBusy = false, booking = null) }; refresh() }
        is ApiResult.Failure -> _state.update { if (result.error is AppError.Validation) it.copy(isBusy = false, bookingError = result.error)
                                                else it.copy(isBusy = false, booking = null, error = result.error) } } }
}
```

### ROUTE + SCREEN (Zurück-Button, Pull-to-Refresh, Dialog nach der Box)
```kotlin
// SOURCE: …/ui/account/ChildAccountScreen.kt:31-80
@Composable fun ChildAccountRoute(args: ChildAccountArgs, onBack: () -> Unit) {
    val viewModel = koinViewModel<ChildAccountViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ChildAccountEvent.Refresh) }
    ChildAccountScreen(state, viewModel::onEvent, onBack)
}
// Screen: PullToRefreshBox { LazyColumn(padding horizontal 24.dp, spacedBy 8.dp) { TextButton(onBack) …; ErrorText(state.error) … } }
// state.booking?.let { BookPointsDialog(...) }   // nach der Box
```

### PROGRESS ROW
```kotlin
// SOURCE: …/ui/components/BadgeList.kt — LinearProgressIndicator(progress = { current.toFloat() / target }, Modifier.width(ProgressWidth))
// Konstanten als private val (BadgeSize = 40.dp, ProgressWidth = 72.dp, OPEN_ALPHA = 0.6f)
```

### BACKGROUND SYNC + NOTICES
```kotlin
// SOURCE: android/feature/tasks/.../domain/notice/SyncTaskNoticesUseCase.kt:25-53
class SyncTaskNoticesUseCase(sessions, repository, cache, notifier, clock) {
    private val mutex = Mutex()   // periodisch + sofort gleichzeitig → sonst doppelte Meldungen
    suspend operator fun invoke(): TaskSyncOutcome = mutex.withLock {
        val session = sessions.awaitSession() ?: return@withLock signOut()
        val lanes = when (val r = repository.board()) { is Success -> r.data
            is Failure -> return@withLock if (r.error == AppError.Unauthorized) signOut() else TaskSyncOutcome.UNREACHABLE }
        val previous = cache.read(session.memberId)?.lanes
        taskNotices(isParent, memberId, previous, lanes).forEach(notifier::announce)
        cache.write(session.memberId, BoardSnapshot(lanes, clock.instant()))
        TaskSyncOutcome.SYNCED
    }
}
private suspend fun SessionStore.awaitSession(): Session? = (state.first { it !is SessionState.Loading } as? SessionState.SignedIn)?.session
// Worker: SOURCE data/WorkManagerTaskSync.kt — CoroutineWorker + KoinComponent, inject UseCase, immer Result.success()
// Notifier: SOURCE data/AndroidTaskNotifier.kt — canNotify() (POST_NOTIFICATIONS), Kanal anlegen, ID-Basis + hashCode-Maske,
//   PendingIntent mit Launch-Intent + Extra, @Suppress("MissingPermission") mit Kommentar
```

### TODAY CARD
```kotlin
// SOURCE: android/feature/tasks/.../ui/today/TodayTasksCard.kt:22-49
@Composable fun TodayTasksRoute(onOpenTasks: () -> Unit) { val viewModel = koinViewModel<TodayTasksViewModel>(); … TodayTasksCard(state, onOpenTasks) }
Card(onClick = onOpenTasks, modifier = Modifier.fillMaxWidth()) { Column(spacedBy(8.dp), Modifier.padding(CardPadding)) { … } }
// VM lädt im init (Heute hat kein ON_RESUME-Muster)
```

### ERROR / LOGGING
Fehler als `AppError` im UI-State, Anzeige mit `ErrorText(error)` (core:ui). `ConfirmDialog(title, text, confirmLabel, onConfirm, onDismiss)`
aus `core.ui.components`. Logging nur in DataStore-Klassen (`Log.w(TAG, …)` bei `IOException`); sonst nichts loggen.

### TEST_STRUCTURE
```kotlin
// ViewModels: @get:Rule val mainDispatcher = MainDispatcherRule(); Fakes aus fakes/Fakes.kt; Namen `REQ-GAME-0xx …`
// Screens: @RunWith(RobolectricTestRunner::class), createComposeRule(), FamilyPlannerTheme { … }, onNodeWithText(…).performClick()
// Repository: MockWebServer + createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
// DataStore: runBlocking, temporäre Datei, file.writeText(…) für alte/kaputte Inhalte
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `…/domain/Rewards.kt` | CREATE | `Reward`, `RedemptionStatus`, `Redemption`, `SavingsGoal`, `RewardDraft` (Eingabe) |
| `…/domain/RewardLimits.kt` | CREATE | Grenzen wie Backend |
| `…/domain/Validation.kt` | UPDATE | `validateReward`, `validateRejection` |
| `…/domain/Ports.kt` | UPDATE | `RewardRepository`, `RedemptionNoticeStore`, `RewardNotifier`, `RewardSync` |
| `…/domain/Models.kt` | UPDATE | `PointsSnapshot.savingsGoal` |
| `…/domain/UseCases.kt` | UPDATE | `LoadPointsUseCase` lädt Sparziel (Kind) |
| `…/domain/RewardUseCases.kt` | CREATE | Laden/Aktionen Kind + Eltern, „Heute“ |
| `…/domain/notice/RewardNotice.kt`, `RewardNoticeRules.kt`, `SyncRewardNoticesUseCase.kt` | CREATE | Hinweise |
| `…/data/RewardMappers.kt` | CREATE | API ↔ Domäne |
| `…/data/RemoteRewardRepository.kt` | CREATE | Adapter |
| `…/data/StoredPoints.kt` | UPDATE | Version 3, `StoredSavingsGoal` |
| `…/data/StoredRedemptions.kt`, `RedemptionStoreSerializer.kt`, `DataStoreRedemptionNoticeStore.kt` | CREATE | Abgleich-Stand `game_redemptions.json` |
| `…/data/AndroidRewardNotifier.kt`, `WorkManagerRewardSync.kt` | CREATE | Benachrichtigung, Worker |
| `…/RewardOpenRequests.kt` | CREATE | Tippen auf Benachrichtigung |
| `…/ui/components/SavingsGoalCard.kt`, `RedemptionList.kt` | CREATE | gemeinsame Bausteine |
| `…/ui/rewards/RewardsViewModel.kt`, `RewardsScreen.kt` | CREATE | Kind-Seite |
| `…/ui/manage/ManageRewardsViewModel.kt`, `ManageRewardsScreen.kt`, `RewardEditDialog.kt`, `RejectRedemptionDialog.kt` | CREATE | Eltern-Seite |
| `…/ui/today/TodaySavingsViewModel.kt`, `TodaySavingsCard.kt` | CREATE | „Heute“ |
| `…/ui/me/MeScreen.kt`, `MeViewModel.kt` | UPDATE | Sparziel-Karte, Button |
| `…/ui/family/FamilyPointsSection.kt`, `FamilyPointsViewModel.kt` | UPDATE | Zeile „Belohnungen · n warten“ |
| `…/GamificationNavigation.kt`, `GamificationModule.kt` | UPDATE | Routen, Koin |
| `android/feature/gamification/build.gradle.kts` | UPDATE | `implementation(libs.work.runtime)` |
| `android/feature/gamification/src/main/res/values/strings.xml`, `res/drawable/ic_stat_rewards.xml` | UPDATE/CREATE | Texte, Icon |
| `android/app/src/main/kotlin/.../AppNavHost.kt`, `MainActivity.kt`, `FamilyPlannerApp.kt` | UPDATE | Heute-Karte, Familie-Callback, Open-Request, Sync |
| `android/app/build.gradle.kts` | UPDATE | 0.12.0 / 13 |
| Tests (siehe Task 14) | CREATE/UPDATE | TDD |
| `docs/03-design/gamification.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/gamification.prd.md` | UPDATE | V-Modell |

## NOT Building

- Keine Backend-/OpenAPI-Änderung (Vertrag 0.9.0 reicht).
- Kein Kauf-Hinweis beim Shop (REQ-GAME-041, 2. Kriterium) — Phase 4.
- Keine Anzeige/Verwaltung deaktivierter Belohnungen, kein Reaktivieren (Backend kann es nicht).
- Kein Verlauf entschiedener Einlösungen für Eltern (nur wartende); Kinder sehen ihre letzten 20.
- Kein Zähler-Badge an der unteren Leiste („Familie“) — die Zeile unter „Punkte“ und die Benachrichtigung reichen.
- Kein Zurückziehen einer Einlösung durch das Kind (Backend kennt es nicht).
- Keine Offline-Aktionen/Warteschlange; Belohnungs- und Verwaltungsseite nur online.
- Keine Emoji-Auswahl-Komponente — Symbol ist ein freies Textfeld (≤ 16 Zeichen, optional).

---

## Step-by-Step Tasks

TDD: Für jede Task zuerst die zugehörigen Tests aus Task 14 schreiben (RED), dann umsetzen (GREEN).

### Task 1: Domäne Belohnungen + Grenzen + Validierung
- **ACTION**: `domain/Rewards.kt`, `domain/RewardLimits.kt` anlegen; `Validation.kt` ergänzen.
- **IMPLEMENT**:
  ```kotlin
  data class Reward(val id: String, val title: String, val emoji: String?, val price: Int, val archived: Boolean)
  enum class RedemptionStatus { PENDING, FULFILLED, REJECTED }
  data class Redemption(
      val id: String, val memberId: String, val rewardId: String, val title: String, val emoji: String?, val price: Int,
      val status: RedemptionStatus, val rejectionReason: String?, val requestedAt: Instant, val decidedAt: Instant?,
  )
  /** Sparziel eines Kindes; ohne Ziel sind [reward] und [progress] `null` (REQ-GAME-041). */
  data class SavingsGoal(val memberId: String, val reward: Reward?, val balance: Int, val progress: BadgeProgress?, val redeemable: Boolean)
  /** Eingabe „Belohnung anlegen/ändern“; [rewardId] `null` = neu. */
  data class RewardDraft(val rewardId: String? = null, val title: String = "", val emoji: String = "", val priceText: String = "") {
      val price: Int? get() = priceText.toIntOrNull()
      val canSubmit: Boolean get() = price?.let { validateReward(title, emoji, it) == null } ?: false
      companion object { fun of(reward: Reward) = RewardDraft(reward.id, reward.title, reward.emoji.orEmpty(), reward.price.toString()) }
  }
  object RewardLimits {
      const val TITLE_MAX = 40; const val EMOJI_MAX = 16; const val PRICE_MIN = 1; const val PRICE_MAX = 10_000
      const val PRICE_DIGITS_MAX = 5; const val REASON_MAX = 200
      const val CHILD_HISTORY_LIMIT = 20; const val PENDING_LIMIT = 100; const val SYNC_HISTORY_LIMIT = 50
      const val SYNC_INTERVAL_MINUTES = 30L
  }
  fun validateReward(title: String, emoji: String, price: Int): AppError.Validation? = when {
      title.trim().length !in 1..RewardLimits.TITLE_MAX -> AppError.Validation("Titel muss zwischen 1 und ${RewardLimits.TITLE_MAX} Zeichen lang sein")
      emoji.trim().length > RewardLimits.EMOJI_MAX -> AppError.Validation("Symbol darf höchstens ${RewardLimits.EMOJI_MAX} Zeichen lang sein")
      price !in RewardLimits.PRICE_MIN..RewardLimits.PRICE_MAX -> AppError.Validation("Preis muss zwischen ${RewardLimits.PRICE_MIN} und ${RewardLimits.PRICE_MAX} liegen")
      else -> null
  }
  fun validateRejection(reason: String): AppError.Validation? =
      if (reason.trim().length in 1..RewardLimits.REASON_MAX) null
      else AppError.Validation("Begründung muss zwischen 1 und ${RewardLimits.REASON_MAX} Zeichen lang sein")
  ```
- **MIRROR**: VALIDATION; `domain/Models.kt` (reine Datenklassen, KDoc deutsch).
- **GOTCHA**: `SavingsGoal.progress` nutzt `BadgeProgress(current, target)` — der Vertrag verwendet dasselbe Schema. Emoji-Länge in
  UTF-16-Einheiten wie das Backend (`String.length`); im Zweifel Backend-Prüfung entscheidet (400 → Fehler im Dialog).
- **VALIDATE**: `RewardsDomainTest` (Task 14).

### Task 2: Ports
- **ACTION**: `Ports.kt` ergänzen.
- **IMPLEMENT**:
  ```kotlin
  interface RewardRepository {
      suspend fun rewards(): ApiResult<List<Reward>>                                   // aktive, nach Preis
      suspend fun create(title: String, emoji: String?, price: Int): ApiResult<Reward>
      suspend fun update(rewardId: String, title: String, emoji: String?, price: Int): ApiResult<Reward>
      suspend fun archive(rewardId: String): ApiResult<Unit>
      suspend fun redeem(rewardId: String): ApiResult<Redemption>
      suspend fun redemptions(status: RedemptionStatus?, limit: Int): ApiResult<List<Redemption>>
      suspend fun fulfill(redemptionId: String): ApiResult<Redemption>
      suspend fun reject(redemptionId: String, reason: String): ApiResult<Redemption>
      suspend fun savingsGoal(memberId: String): ApiResult<SavingsGoal>
      suspend fun setSavingsGoal(memberId: String, rewardId: String): ApiResult<SavingsGoal>
      suspend fun clearSavingsGoal(memberId: String): ApiResult<Unit>
  }
  /** Zuletzt abgeglichener Stand der Einlösungen je Mitglied (ADR-0013); Fehler beim Speichern sind nie ein Nutzerfehler. */
  interface RedemptionNoticeStore {
      suspend fun read(memberId: String): RedemptionSnapshot?
      suspend fun write(memberId: String, snapshot: RedemptionSnapshot)
      suspend fun clear()
  }
  interface RewardNotifier { fun announce(notice: RewardNotice) }
  /** Stößt den Abgleich im Hintergrund an (WorkManager, ADR-0013). */
  interface RewardSync { fun requestNow(); fun schedulePeriodic() }
  ```
  `RedemptionSnapshot(val statuses: Map<String, RedemptionStatus>, val syncedAt: Instant)` in `domain/notice/RewardNotice.kt`.
- **VALIDATE**: `./gradlew :feature:gamification:compileDebugKotlin`.

### Task 3: Mapper + Remote-Adapter
- **ACTION**: `data/RewardMappers.kt`, `data/RemoteRewardRepository.kt`.
- **IMPLEMENT**:
  ```kotlin
  internal fun ApiReward.toDomain() = Reward(id, title, emoji, price, archived)
  internal fun ApiRedemption.toDomain(): Redemption? {
      val requested = parseInstant(requestedAt) ?: return null
      val decided = decidedAt?.let { parseInstant(it) ?: return null }
      val parsedStatus = RedemptionStatus.entries.firstOrNull { it.name == status.name } ?: return null
      if (parsedStatus != RedemptionStatus.PENDING && decided == null) return null
      return Redemption(id, memberId, rewardId, title, emoji, price, parsedStatus, rejectionReason, requested, decided)
  }
  internal fun ApiSavingsGoal.toDomain() = SavingsGoal(memberId, reward?.toDomain(), balance, progress?.let { BadgeProgress(it.current, it.target) }, redeemable)
  ```
  Imports mit Alias (`de.familyplanner.api.model.Reward as ApiReward`, `Redemption as ApiRedemption`, `SavingsGoal as ApiSavingsGoal`,
  `RedemptionStatus as ApiRedemptionStatus`). Adapter:
  ```kotlin
  class RemoteRewardRepository(private val api: GamificationApi, private val json: Json) : RewardRepository {
      override suspend fun rewards() = apiCall(json, { api.listRewards(false) }) { r -> r.data?.map { it.toDomain() } }
      override suspend fun create(title: String, emoji: String?, price: Int) = apiCall(json, { api.createReward(RewardInput(title, price, emoji)) }) { it.data?.toDomain() }
      override suspend fun archive(rewardId: String) = apiCallNoContent(json) { api.archiveReward(rewardId) }
      override suspend fun redemptions(status: RedemptionStatus?, limit: Int) =
          apiCall(json, { api.listRedemptions(status?.let { ApiRedemptionStatus.valueOf(it.name) }, limit) }) { r -> r.data?.mapAllOrNull { it.toDomain() } }
      override suspend fun clearSavingsGoal(memberId: String) = apiCallNoContent(json) { api.clearSavingsGoal(memberId) }
      // übrige analog
  }
  ```
- **MIRROR**: REPOSITORY / MAPPER.
- **GOTCHA**: Generierten Konstruktor `RewardInput(...)` auf Parameterreihenfolge prüfen (benannte Argumente verwenden). Leeres Symbol als
  `null` senden (Use Case trimmt und setzt `null`). Entschiedene Einlösung ohne `decidedAt` → Serverfehler (strikt wie Badges).
- **VALIDATE**: `RemoteRewardRepositoryTest`.

### Task 4: Sparziel im Punkte-Stand (Version 3)
- **ACTION**: `Models.kt`, `UseCases.kt`, `StoredPoints.kt`, `GamificationModule.kt`.
- **IMPLEMENT**:
  - `PointsSnapshot(account, leaderboard, syncedAt, badges = null, savingsGoal: SavingsGoal? = null)` (Default am Ende → bestehende Aufrufe kompilieren).
    KDoc: „[account], [badges], [savingsGoal] nur für Kinder (eigene)“.
  - `LoadPointsUseCase(repository, rewards: RewardRepository, cache, sessions, clock)` — `rewards` an **zweiter** Stelle; `fetch` lädt für Kinder
    zusätzlich `async { rewards.savingsGoal(memberId) }`; `Fetched.savingsGoal`; Fehlerreihenfolge Konto, Rangliste, Badges, Sparziel.
    Erfolgsfall: `PointsSnapshot(…, data.badges, data.savingsGoal)`.
  - `StoredPoints`: `CACHE_VERSION = 3` (KDoc „3: Sparziel (Phase 3b)“), Feld `savingsGoal: StoredSavingsGoal? = null`;
    ```kotlin
    @Serializable data class StoredSavingsGoal(val memberId: String, val balance: Int, val redeemable: Boolean, val reward: StoredReward? = null,
        val progressCurrent: Int? = null, val progressTarget: Int? = null)
    @Serializable data class StoredReward(val id: String, val title: String, val emoji: String? = null, val price: Int, val archived: Boolean)
    ```
    `toStored`/`toDomain` ergänzen (Progress nur, wenn beide Werte gesetzt).
  - Koin: `factory { LoadPointsUseCase(get(), get(), get(), get()) }`.
- **MIRROR**: CACHE FORMAT, USE CASE.
- **GOTCHA**: `fetch` muss < 50 Zeilen bleiben (detekt `LongMethod`) — ggf. `listOfNotNull(…).firstFailure()` als private Erweiterung.
  Version-2-Dateien werden verworfen → nach dem Update gilt für Badges wieder die Basislinie (kein Dialog), offline erst nach einem Online-Laden.
  Test `cache of version 1 counts as empty` bleibt; neuer Test für Version 2. Tests, die `"version":2` schreiben, auf 3 anpassen.
  `AppNavHostTest` (Z. 222–226) und alle `LoadPointsUseCase(...)`-Aufrufe in Tests bekommen das Reward-Fake.
- **VALIDATE**: `UseCasesTest`, `DataStorePointsCacheTest`.

### Task 5: Use Cases Belohnungen
- **ACTION**: `domain/RewardUseCases.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Seite „Belohnungen“ (Kind): aktive Belohnungen, eigenes Sparziel, eigene Einlösungen — nur online. */
  data class ChildRewards(val rewards: List<Reward>, val goal: SavingsGoal, val redemptions: List<Redemption>)
  class LoadChildRewardsUseCase(private val repository: RewardRepository) {
      suspend operator fun invoke(memberId: String): ApiResult<ChildRewards> = coroutineScope { /* 3× async, erster Fehler zählt */ }
  }
  class ChildRewardActions(private val repository: RewardRepository, private val sync: RewardSync) {
      suspend fun setGoal(memberId: String, rewardId: String) = repository.setSavingsGoal(memberId, rewardId)
      suspend fun clearGoal(memberId: String) = repository.clearSavingsGoal(memberId)
      /** Nach Erfolg sofort abgleichen, damit spätere Entscheidungen gemeldet werden (REQ-GAME-043). */
      suspend fun redeem(rewardId: String): ApiResult<Redemption> = repository.redeem(rewardId).also { if (it is ApiResult.Success) sync.requestNow() }
  }
  /** Seite „Belohnungen verwalten“ (Eltern). [names]: memberId → Name aus der Rangliste. */
  data class ManagedRewards(val rewards: List<Reward>, val pending: List<Redemption>, val names: Map<String, String>)
  class LoadManagedRewardsUseCase(private val repository: RewardRepository, private val points: PointsRepository) {
      // rewards(), redemptions(PENDING, PENDING_LIMIT), points.leaderboard() parallel; Rangliste-Fehler → names = emptyMap() (nicht fatal)
  }
  class ManageRewardActions(private val repository: RewardRepository) {
      suspend fun save(draft: RewardDraft): ApiResult<Reward> {
          val price = draft.price ?: return validationFailure("Preis fehlt")
          validateReward(draft.title, draft.emoji, price)?.let { return ApiResult.Failure(it) }
          val emoji = draft.emoji.trim().ifEmpty { null }
          return draft.rewardId?.let { repository.update(it, draft.title.trim(), emoji, price) } ?: repository.create(draft.title.trim(), emoji, price)
      }
      suspend fun archive(rewardId: String) = repository.archive(rewardId)
      suspend fun fulfill(redemptionId: String) = repository.fulfill(redemptionId)
      suspend fun reject(redemptionId: String, reason: String): ApiResult<Redemption> {
          validateRejection(reason)?.let { return ApiResult.Failure(it) }
          return repository.reject(redemptionId, reason.trim())
      }
  }
  /** Anzahl wartender Einlösungen für „Familie“. */
  class CountPendingRedemptionsUseCase(private val repository: RewardRepository) {
      suspend operator fun invoke(): ApiResult<Int> = repository.redemptions(RedemptionStatus.PENDING, RewardLimits.PENDING_LIMIT).map { it.size }
  }
  /** Sparziel für „Heute“: frisch vom Server, sonst aus dem lokalen Punkte-Stand; nur Kinder. Schreibt den Stand nicht (kein halber Snapshot). */
  class LoadTodaySavingsUseCase(private val repository: RewardRepository, private val cache: PointsCache, private val sessions: SessionStore) {
      suspend operator fun invoke(): SavingsGoal? {
          val session = sessions.current?.takeIf { it.role == Role.CHILD } ?: return null
          return when (val r = repository.savingsGoal(session.memberId)) {
              is ApiResult.Success -> r.data
              is ApiResult.Failure -> cache.read(session.memberId)?.savingsGoal
          }
      }
  }
  ```
- **MIRROR**: USE CASE; `BookPointsUseCase` (Validierung vor dem Aufruf).
- **IMPORTS**: `de.familyplanner.android.core.data.map`, `validationFailure`.
- **GOTCHA**: Eltern-Liste ohne Namen ist kein Fehler (Fallback „Kind“ im UI). `ApiResult.map` existiert in `core.data`.
- **VALIDATE**: `RewardUseCasesTest`.

### Task 6: Hinweis-Regeln + Abgleich
- **ACTION**: `domain/notice/RewardNotice.kt`, `RewardNoticeRules.kt`, `SyncRewardNoticesUseCase.kt`.
- **IMPLEMENT**:
  ```kotlin
  sealed interface RewardNotice {
      val redemptionId: String
      data class Requested(override val redemptionId: String, val childName: String?, val title: String, val emoji: String?) : RewardNotice
      data class Fulfilled(override val redemptionId: String, val title: String, val emoji: String?) : RewardNotice
      data class Rejected(override val redemptionId: String, val title: String, val emoji: String?, val reason: String?) : RewardNotice
  }
  /** Ohne [previous] keine Hinweise (Basislinie, ADR-0013 Nr. 5). */
  fun rewardNotices(isParent: Boolean, previous: RedemptionSnapshot?, current: List<Redemption>, names: Map<String, String>): List<RewardNotice> {
      if (previous == null) return emptyList()
      return if (isParent) {
          current.filter { it.status == RedemptionStatus.PENDING && it.id !in previous.statuses }
              .map { RewardNotice.Requested(it.id, names[it.memberId], it.title, it.emoji) }
      } else current.mapNotNull { childNotice(previous, it) }
  }
  private fun childNotice(previous: RedemptionSnapshot, now: Redemption): RewardNotice? {
      val before = previous.statuses[now.id]
      val decidedSinceSync = before == null && now.decidedAt?.isAfter(previous.syncedAt) == true
      if (before != RedemptionStatus.PENDING && !decidedSinceSync) return null
      return when (now.status) {
          RedemptionStatus.FULFILLED -> RewardNotice.Fulfilled(now.id, now.title, now.emoji)
          RedemptionStatus.REJECTED -> RewardNotice.Rejected(now.id, now.title, now.emoji, now.rejectionReason)
          RedemptionStatus.PENDING -> null
      }
  }
  enum class RewardSyncOutcome { SYNCED, UNREACHABLE, SIGNED_OUT }
  class SyncRewardNoticesUseCase(sessions, repository: RewardRepository, points: PointsRepository, store: RedemptionNoticeStore,
      notifier: RewardNotifier, clock: Clock = Clock.system(BerlinZone)) {
      private val mutex = Mutex()
      suspend operator fun invoke(): RewardSyncOutcome = mutex.withLock {
          // Session abwarten; Eltern: redemptions(PENDING, PENDING_LIMIT) + leaderboard() (Fehler → leere Namen);
          // Kind: redemptions(null, SYNC_HISTORY_LIMIT). Unauthorized → store.clear(), SIGNED_OUT; sonst Fehler → UNREACHABLE (Stand bleibt).
          // previous = store.read(memberId); notices.forEach(notifier::announce); store.write(memberId, RedemptionSnapshot(current.associate { it.id to it.status }, clock.instant()))
      }
  }
  ```
- **MIRROR**: BACKGROUND SYNC + NOTICES (`taskNotices`, `SyncTaskNoticesUseCase` inkl. privater `awaitSession()` — Kopie im eigenen Modul,
  Feature-Module kennen sich nicht, ADR-0001).
- **GOTCHA**: Eltern-Stand enthält nur wartende IDs; eine erledigte Einlösung verschwindet daraus — korrekt, weil nur „neu wartend“ zählt.
  Das Kind bekommt für seine **eigene** neue Einlösung (`PENDING`, unbekannt) **keine** Meldung. `syncedAt` stammt aus der App-Uhr, `decidedAt`
  vom Server — eine kleine Uhrabweichung kann im Grenzfall eine Meldung verschlucken (akzeptiert); doppelt gemeldet wird nie, weil der Status danach gespeichert ist.
- **VALIDATE**: `RewardNoticesTest`, `SyncRewardNoticesUseCaseTest`.

### Task 7: Abgleich-Stand, Notifier, Worker, Open-Request
- **ACTION**: `data/StoredRedemptions.kt`, `data/RedemptionStoreSerializer.kt`, `data/DataStoreRedemptionNoticeStore.kt`, `data/AndroidRewardNotifier.kt`,
  `data/WorkManagerRewardSync.kt`, `RewardOpenRequests.kt`, `res/drawable/ic_stat_rewards.xml`, `build.gradle.kts`.
- **IMPLEMENT**:
  - `StoredRedemptions(version = 1, memberId: String? = null, syncedAtMillis: Long = 0, statuses: Map<String, String> = emptyMap())`;
    `toDomain()` liefert `null` ohne `memberId`, unbekannte Status-Namen werden übersprungen. Serializer wie `PointsCacheSerializer`
    (andere Version → Standardwert). DataStore-Klasse wie `DataStorePointsCache` (TAG `DataStoreRedemptionNoticeStore`, `Log.w` bei `IOException`).
  - `AndroidRewardNotifier(context)`: Kanal `CHANNEL_REWARDS = "rewards"` (Name `game_channel_rewards`), `REWARD_ID_BASE = 200_000`,
    Maske `0xFFFF`, `OPEN_REWARDS_REQUEST_CODE = 2`, Icon `R.drawable.ic_stat_rewards`, Extra `RewardOpenRequests.EXTRA_OPEN_REWARDS`.
    Texte: Requested → Titel `game_notice_requested_title` („%1$s möchte %2$s einlösen“, Name oder „Ein Kind“; `%2$s` = „emoji title“),
    Text `game_notice_requested_text` („Unter Familie → Belohnungen bestätigen oder ablehnen.“); Fulfilled → `game_notice_fulfilled_title`
    („%1$s erfüllt“), Text `game_notice_fulfilled_text` („Viel Spaß!“); Rejected → `game_notice_rejected_title` („%1$s abgelehnt“), Text = Grund
    oder `game_notice_rejected_text` („Die Punkte sind zurückgebucht.“).
  - `WorkManagerRewardSync` + `RewardSyncWorker` (Unique-Namen `reward-sync-periodic`, `reward-sync-now`, Intervall `RewardLimits.SYNC_INTERVAL_MINUTES`).
  - `RewardOpenRequests` wie `TaskOpenRequests`, `EXTRA_OPEN_REWARDS = "de.familyplanner.android.OPEN_REWARDS"`.
  - `ic_stat_rewards.xml`: einfarbiges 24-dp-Vektor-Icon (Geschenk/Stern, weiß, wie `ic_stat_tasks.xml` aufgebaut).
  - `build.gradle.kts`: `implementation(libs.work.runtime)`.
- **MIRROR**: BACKGROUND SYNC + NOTICES; CACHE FORMAT.
- **GOTCHA**: genau eine DataStore-Instanz je Datei (`single`), Datei `game_redemptions.json`. Notification-IDs dürfen nicht mit Aufgaben (`100_000+`) kollidieren.
  Der `PendingIntent`-Request-Code muss sich von dem der Aufgaben (1) unterscheiden, sonst überschreibt `FLAG_UPDATE_CURRENT` das Extra.
- **VALIDATE**: `DataStoreRedemptionNoticeStoreTest`, `GamificationModuleTest`; Lint (`lintDebug`) ohne neue Befunde.

### Task 8: Komponenten `SavingsGoalCard`, `RedemptionList`
- **ACTION**: `ui/components/SavingsGoalCard.kt`, `ui/components/RedemptionList.kt`.
- **IMPLEMENT**:
  - `SavingsGoalCard(goal: SavingsGoal?, onClick: (() -> Unit)?)`: `Card` (mit `onClick`, wenn nicht `null`). Titel `game_goal_title` („Sparziel“).
    Ohne `goal?.reward` → `game_goal_none` („Noch kein Sparziel – such dir eine Belohnung aus.“). Sonst „emoji title“, `LinearProgressIndicator(progress = { current.toFloat() / target })`
    über volle Breite, `game_goal_progress` („%1$d/%2$d“); `redeemable` → `game_goal_redeemable` („Einlösbar!“, `primary`);
    `reward.archived` → `game_goal_archived` („Nicht mehr einlösbar“, `error`).
  - `RedemptionList(redemptions: List<Redemption>, names: Map<String, String> = emptyMap(), showNames: Boolean = false)`: leer → `game_redemptions_empty`;
    je Zeile „emoji title“, `game_points_price` („%1$d P“), Status-Label (`RedemptionStatus.labelRes()` in `ui/Labels.kt`: wartet/erfüllt/abgelehnt),
    `formatDate(requestedAt…)`, bei `REJECTED` zweite Zeile `game_redemption_reason` („Grund: %1$s“).
- **MIRROR**: PROGRESS ROW, LIST COMPONENT (`Column`, keine `LazyColumn` in der Komponente).
- **GOTCHA**: `progress.target` ist ≥ 1 (Preis ≥ 1), trotzdem `coerceAtLeast(1)` gegen Division durch 0 bei kaputtem Cache. Magic Numbers als `private val`.
- **VALIDATE**: `RewardScreensTest`.

### Task 9: „Ich“ erweitern
- **ACTION**: `MeScreen.kt` (VM unverändert — Sparziel kommt aus `snapshot.savingsGoal`).
- **IMPLEMENT**: `MeRoute(onOpenBadges, onOpenRewards: () -> Unit, viewModel)`; `MeScreen(state, onEvent, onOpenBadges, onOpenRewards)`.
  Nach `StatTiles` (nur wenn `snapshot.account != null`, also Kind): `item { SavingsGoalCard(snapshot.savingsGoal, onClick = if (state.isOffline) null else onOpenRewards) }`,
  `item { OutlinedButton(onClick = onOpenRewards, enabled = !state.isOffline, Modifier.fillMaxWidth()) { Text(game_rewards_open) } }` („Belohnungen“).
- **GOTCHA**: Alle `MeScreen(...)`-Aufrufe in `ScreensTest` bekommen den neuen Parameter. „Ich“ gibt es nur für Kinder; Eltern-`snapshot.account` ist `null`.
- **VALIDATE**: `ScreensTest` / `RewardScreensTest`.

### Task 10: Seite „Belohnungen“ (Kind)
- **ACTION**: `ui/rewards/RewardsViewModel.kt`, `ui/rewards/RewardsScreen.kt`.
- **IMPLEMENT**:
  ```kotlin
  data class RewardsUiState(val memberId: String?, val data: ChildRewards? = null, val isLoading: Boolean = true, val error: AppError? = null,
      val confirmRedeem: Reward? = null, val isBusy: Boolean = false)
  sealed interface RewardsEvent { data object Refresh; data class SetGoal(val reward: Reward); data object ClearGoal
      data class AskRedeem(val reward: Reward); data object ConfirmRedeem; data object DismissRedeem }
  class RewardsViewModel(sessions: SessionStore, private val load: LoadChildRewardsUseCase, private val actions: ChildRewardActions) : ViewModel()
  ```
  Jede Aktion: `isBusy` → Aufruf → Erfolg: `refresh()`; Fehler: `error = result.error` (inkl. `Conflict` mit Server-Text), Dialog schließen, danach
  `refresh()` bei `Conflict` (Stand hat sich geändert). Ohne `memberId` → `error = AppError.Unauthorized`, kein Laden.
  Screen: Zurück, Titel `game_rewards_title` („Belohnungen“), `ErrorText`, Guthaben-Zeile `game_rewards_balance` („Dein Guthaben: %1$d“),
  je Belohnung eine `Card`: „emoji title“, Preis, Fortschritt `min(balance, price)/price`, Stern-Hinweis `game_rewards_is_goal` („Dein Sparziel“) wenn
  `goal.reward?.id == reward.id`; Buttons `game_rewards_set_goal` („Als Sparziel“) bzw. `game_rewards_clear_goal` („Kein Sparziel mehr“) und
  `game_rewards_redeem` („Einlösen“, `enabled = balance >= price && !isBusy`); fehlende Punkte `game_rewards_missing` („Noch %1$d Punkte“).
  Leer → `game_rewards_empty` („Noch keine Belohnungen – frag deine Eltern.“). Danach Überschrift `game_redemptions_title` („Meine Einlösungen“) + `RedemptionList`.
  Dialog: `ConfirmDialog(title = game_redeem_confirm_title („%1$s einlösen?“), text = game_redeem_confirm_text („%1$d Punkte werden abgebucht. Deine Eltern bestätigen die Einlösung.“), confirmLabel = game_rewards_redeem, …)`.
- **MIRROR**: ROUTE + SCREEN, VIEWMODEL.
- **GOTCHA**: Kein Koin-Parameter nötig (Mitglied aus `SessionStore`). Funktionen < 50 Zeilen: Belohnungskarte als private Composable `RewardCard`.
- **VALIDATE**: `RewardViewModelsTest`, `RewardScreensTest`.

### Task 11: Seite „Belohnungen verwalten“ (Eltern) + Familie-Zeile
- **ACTION**: `ui/manage/ManageRewardsViewModel.kt`, `ManageRewardsScreen.kt`, `RewardEditDialog.kt`, `RejectRedemptionDialog.kt`;
  `ui/family/FamilyPointsViewModel.kt`, `FamilyPointsSection.kt`.
- **IMPLEMENT**:
  - State: `data ManagedRewards?`, `isLoading`, `error`, `editing: RewardDraft?`, `rejecting: Redemption?` + `rejectReason: String`,
    `confirmArchive: Reward?`, `isBusy`, `actionError` (Validierung im Dialog).
  - Events: `Refresh`, `StartCreate`, `StartEdit(reward)`, `TitleChanged`, `EmojiChanged`, `PriceChanged` (nur Ziffern, `take(PRICE_DIGITS_MAX)`),
    `SaveReward`, `AskArchive(reward)`, `ConfirmArchive`, `Fulfill(redemption)`, `StartReject(redemption)`, `RejectReasonChanged` (`take(REASON_MAX)`),
    `ConfirmReject`, `DismissDialog`.
  - Fehlerregel wie `ChildAccountViewModel.submit`: `AppError.Validation` → `actionError` im Dialog; sonst Dialog zu, `error` setzen; `Conflict`/`NotFound` → zusätzlich `refresh()`.
  - Screen: Zurück, Titel `game_manage_title` („Belohnungen verwalten“), `ErrorText`; Überschrift `game_manage_pending` („Wartet auf euch“) — leer
    `game_manage_pending_empty` („Keine offenen Einlösungen“); je Einlösung Zeile „<Name> · emoji title · 80 P“ (`names[memberId] ?: game_child_fallback` „Kind“),
    Buttons `game_manage_reject` („Ablehnen“) und `game_manage_fulfill` („Erfüllt“). Überschrift `game_manage_rewards` („Belohnungen“), je Belohnung Zeile
    (antippen → `StartEdit`) mit Preis und `TextButton` `game_manage_archive` („Deaktivieren“); leer `game_manage_rewards_empty`. `SubmitButton` `game_manage_create` („Belohnung anlegen“).
  - `RewardEditDialog(draft, isBusy, error, onEvent)`: Titel `game_reward_new`/`game_reward_edit`, Felder Titel (Zähler `x/40`), Symbol (Platzhalter „🎢“),
    Preis (`KeyboardType.Number`, Fehler `game_reward_price_range` „1 bis 10 000 Punkte“ bei Wert außerhalb), `ErrorText(error)`, Speichern (`draft.canSubmit && !isBusy`), Abbrechen.
  - `RejectRedemptionDialog(redemption, reason, isBusy, error, onEvent)`: Titel `game_reject_title` („%1$s ablehnen?“), Hinweis `game_reject_hint`
    („Die Punkte werden zurückgebucht.“), Feld Begründung (Zähler `x/200`), Bestätigen `game_manage_reject` (aktiv bei `validateRejection == null`).
  - Archivieren: `ConfirmDialog(game_archive_title „%1$s deaktivieren?“, game_archive_text „Sie ist danach nicht mehr einlösbar. Laufende Einlösungen bleiben.“, game_manage_archive, …)`.
  - Familie: `FamilyPointsViewModel(loadPoints, cookingPointsUseCase, countPending: CountPendingRedemptionsUseCase)` lädt die Anzahl parallel →
    `pendingRedemptions: Int?` (Fehler → unverändert lassen, kein Fehlertext). `FamilyPointsRoute(onOpenChild, onOpenRewards: () -> Unit, viewModel)`;
    in der Section nach den Kochpunkten eine `Row` mit `Text(game_family_rewards)` („Belohnungen“), rechts `pluralStringResource(R.plurals.game_family_pending, n, n)`
    („1 wartet“/„%d warten“, bei 0 nichts) und `TextButton(game_family_rewards_open „Öffnen“, enabled = !state.isOffline)`.
- **MIRROR**: ROUTE + SCREEN, VIEWMODEL, `BookPointsDialog`, `CookingPointsDialog`.
- **GOTCHA**: VM-Datei könnte > 200 Zeilen werden — Event-Verarbeitung in kleine private Funktionen (`onDraft`, `save`, `archive`, `fulfill`, `reject`) teilen;
  detekt `CyclomaticComplexMethod` beim großen `when` beachten (ggf. Draft-Events in `editDraft { … }` bündeln wie `ChildAccountViewModel`).
  `AppNavHostTest` (Z. 224–227) → dritter Parameter.
- **VALIDATE**: `RewardViewModelsTest`, `RewardScreensTest`, `ViewModelsTest` (Familie).

### Task 12: „Heute“-Karte
- **ACTION**: `ui/today/TodaySavingsViewModel.kt`, `ui/today/TodaySavingsCard.kt`.
- **IMPLEMENT**: `TodaySavingsUiState(goal: SavingsGoal? = null)`; VM lädt im `init` über `LoadTodaySavingsUseCase`.
  `TodaySavingsRoute(onOpenRewards)` → `TodaySavingsCard(state, onOpenRewards)`: nichts anzeigen, solange `goal?.reward == null`; sonst
  `SavingsGoalCard(goal, onClick = onOpenRewards)`.
- **MIRROR**: TODAY CARD.
- **GOTCHA**: Eltern (`role != CHILD`) → Use Case liefert `null` → keine Karte, keine Anfrage.
- **VALIDATE**: `RewardViewModelsTest` (Kind mit/ohne Ziel, offline aus Cache, Eltern), `RewardScreensTest`.

### Task 13: Navigation, Koin, App-Verdrahtung, Strings, Version
- **ACTION**:
  - `GamificationNavigation.kt`: `@Serializable data object RewardsRoute`, `@Serializable data object ManageRewardsRoute`;
    `composable<MeHomeRoute> { MeRoute(onOpenBadges = navController::openMemberBadges, onOpenRewards = navController::openRewards) }`;
    `composable<RewardsRoute> { RewardsRoute(onBack = { navController.popBackStack() }) }`; `composable<ManageRewardsRoute> { ManageRewardsRoute(onBack = …) }`;
    `fun NavHostController.openRewards() = navigate(RewardsRoute)`, `fun NavHostController.openManageRewards() = navigate(ManageRewardsRoute)`.
  - `GamificationModule.kt`:
    ```kotlin
    single<RewardRepository> { RemoteRewardRepository(get(), get()) }
    single<RedemptionNoticeStore> { DataStoreRedemptionNoticeStore(DataStoreFactory.create(RedemptionStoreSerializer(get())) { androidContext().dataStoreFile(REDEMPTION_STORE_FILE) }) }
    single<RewardNotifier> { AndroidRewardNotifier(androidContext()) }
    single<RewardSync> { WorkManagerRewardSync(androidContext()) }
    single { SyncRewardNoticesUseCase(get(), get(), get(), get(), get()) }   // single wegen Mutex
    single { RewardOpenRequests() }
    factory { LoadChildRewardsUseCase(get()) }; factory { ChildRewardActions(get(), get()) }
    factory { LoadManagedRewardsUseCase(get(), get()) }; factory { ManageRewardActions(get()) }
    factory { CountPendingRedemptionsUseCase(get()) }; factory { LoadTodaySavingsUseCase(get(), get(), get()) }
    viewModel { RewardsViewModel(get(), get(), get()) }; viewModel { ManageRewardsViewModel(get(), get()) }
    viewModel { TodaySavingsViewModel(get()) }; viewModel { FamilyPointsViewModel(get(), get(), get()) }
    ```
  - `AppNavHost.kt` (`MainHost`): `rewardRequests: RewardOpenRequests = koinInject()`; `LaunchedEffect(openRewards)` → `consume()`, dann
    Eltern: `navigateToTopLevel(FAMILY); openManageRewards()`, Kinder: `navigateToTopLevel(ME); openRewards()`.
    `todayContent`: zusätzlich `TodaySavingsRoute(onOpenRewards = { navController.navigateToTopLevel(TopLevelDestination.ME); navController.openRewards() })` nach `TodayTasksRoute`.
    `familyContent = { FamilyPointsRoute(onOpenChild = navController::openChildAccount, onOpenRewards = navController::openManageRewards) }`.
  - `MainActivity.kt`: `onStart` → `get<RewardSync>().requestNow()`; `receiveOpenRewards(intent)` in `onCreate`/`onNewIntent` wie `receiveOpenTasks` (Kommentar „ADR-0013: Tippen auf einen Belohnungs-Hinweis …“).
  - `FamilyPlannerApp.kt`: `get<RewardSync>().schedulePeriodic()`.
  - `strings.xml`: alle in Task 7–12 genannten Schlüssel (deutsch), `game_channel_rewards` („Belohnungen“), `game_status_pending` („wartet“),
    `game_status_fulfilled` („erfüllt“), `game_status_rejected` („abgelehnt“), `<plurals name="game_family_pending">` (one „1 wartet“, other „%1$d warten“).
  - `android/app/build.gradle.kts`: `versionCode = 13`, `versionName = "0.12.0"`.
- **GOTCHA**: Namensgleichheit Route-Objekt ↔ Composable (`RewardsRoute`) wie bei `ChildAccountRoute`; meldet der Compiler Mehrdeutigkeit, Composable-Import mit Alias.
  Unterseiten zeigen keine Leiste (`topLevelOf` kennt sie nicht) — nichts in `TopLevelDestination` ändern. Strings mit `%`-Text (z. B. „80 P“) korrekt formatieren;
  „?“ in Strings escapen (`\?`, siehe 2b-Bericht).
- **VALIDATE**: `GamificationModuleTest` (alle neuen Definitionen auflösbar), `:app:testDebugUnitTest`.

### Task 14: Tests (TDD — jeweils vor den Tasks 1–13 schreiben)
- **ACTION**: Fakes erweitern, neue Testdateien (bestehende `ViewModelsTest`/`ScreensTest` sind schon 333–376 Zeilen → neue Dateien).
- **IMPLEMENT**:
  - `fakes/Fakes.kt` bzw. neue `fakes/RewardFakes.kt`: `reward(id, price = 200, title = "Freizeitpark", emoji = "🎢", archived = false)`,
    `redemption(id, status = PENDING, memberId = "c-1", decidedAt = if (status == PENDING) null else noon, reason = null)`,
    `goal(reward: Reward? = reward("r-1"), balance = 120)` (progress = `BadgeProgress(min(balance, price), price)`, `redeemable = balance >= price`);
    `FakeRewardRepository` mit `…Result`-Feldern und Aufruflisten für jede Methode; `FakeRewardSync` (Zähler), `FakeRewardNotifier` (Liste),
    `InMemoryRedemptionNoticeStore`.
  - `android/app/src/test/.../AppPointsFakes.kt`: `StaticRewardRepository` (kein Ziel, leere Listen, Aktionen `error("not used")`); `AppNavHostTest` Koin-Modul ergänzen
    (`RewardRepository`, `RewardSync` Fake, `RewardOpenRequests`, `TodaySavingsViewModel`, neue Konstruktoren).
  - `domain/RewardsDomainTest`: `REQ-GAME-040 reward draft needs title and price in range`, `REQ-GAME-040 reward draft from reward keeps values`,
    `REQ-GAME-043 rejection needs a reason`.
  - `domain/RewardUseCasesTest`: `REQ-GAME-041 child rewards load rewards, goal and redemptions`, `REQ-GAME-041 child rewards fail on first error`,
    `REQ-GAME-042 redeem requests a sync after success`, `REQ-GAME-042 failed redeem does not sync`, `REQ-GAME-040 save creates without id and updates with id`,
    `REQ-GAME-040 save trims and sends empty symbol as null`, `REQ-GAME-040 invalid draft is not sent`, `REQ-GAME-043 reject validates the reason`,
    `REQ-GAME-043 managed rewards load pending with names`, `REQ-GAME-043 managed rewards without leaderboard use no names`,
    `REQ-GAME-042 pending count`, `REQ-GAME-041 today savings uses server and falls back to cache`, `REQ-GAME-041 parent has no today savings`.
  - `domain/UseCasesTest` (bestehend): `REQ-GAME-041 child load stores the savings goal`, `REQ-GAME-041 failed savings goal stores nothing`,
    `REQ-GAME-041 parent does not load a savings goal`.
  - `domain/RewardNoticesTest`: Basislinie leer; Eltern: neue wartende → `Requested` mit Name, bekannte → nichts, Name fehlt → `null`;
    Kind: `PENDING → FULFILLED`/`REJECTED` (mit Grund), eigene neue `PENDING` → nichts, unbekannt + `decidedAt` nach `syncedAt` → Meldung, unbekannt + älter → nichts.
    (Namen `REQ-GAME-042 …` für Eltern, `REQ-GAME-043 …` für Kind.)
  - `domain/SyncRewardNoticesUseCaseTest`: meldet und speichert; Basislinie speichert ohne Meldung; nicht erreichbar → Stand unverändert, `UNREACHABLE`;
    `Unauthorized` → `clear()`, `SIGNED_OUT`; Eltern ohne Rangliste → Meldung mit `childName = null`; anderes Mitglied → Basislinie.
  - `data/RemoteRewardRepositoryTest` (MockWebServer): Belohnungen mappen; `includeArchived=false` im Pfad; Einlösung mit Status/Datum; entschiedene ohne `decidedAt` → `AppError.Server`;
    `409` beim Einlösen → `AppError.Conflict(message)`; Archivieren/Sparziel entfernen `204` → `Success(Unit)`; Sparziel ohne Ziel (`reward: null`, `progress: null`);
    `status=PENDING&limit=100` in der Anfrage.
  - `data/DataStorePointsCacheTest`: `REQ-GAME-041 savings goal survives a restart`, `REQ-GAME-041 cache of version 2 counts as empty`.
  - `data/DataStoreRedemptionNoticeStoreTest`: überlebt Neustart, anderes Mitglied → `null`, unbekannter Status wird übersprungen, kaputte Datei → `null`.
  - `ui/RewardViewModelsTest`: Rewards-VM (laden, Sparziel setzen/entfernen → neu laden, Einlösen mit Bestätigung, `Conflict` → Fehler + neu laden, `isBusy` blockt Doppelklick);
    Manage-VM (laden, anlegen mit Validierungsfehler im Dialog, ändern, deaktivieren mit Bestätigung, erfüllen, ablehnen mit Grund, Fehler ≠ Validierung schließt Dialog);
    Familie zeigt Anzahl wartender; Today-VM.
  - `ui/RewardScreensTest`: Sparziel-Karte (Fortschritt „120/200“, „Einlösbar!“, „Nicht mehr einlösbar“, kein Ziel); „Ich“ zeigt Karte + Button, offline deaktiviert;
    Belohnungsseite (Einlösen deaktiviert bei zu wenig Guthaben + „Noch 80 Punkte“, Bestätigungsdialog, „Meine Einlösungen“ mit „abgelehnt“ + „Grund: Ferien“);
    Verwaltung (Name + Erfüllt/Ablehnen, Dialoge, Fallback „Kind“); Familie-Zeile „1 wartet“; Heute-Karte versteckt ohne Ziel.
  - `GamificationModuleTest`: neue Definitionen (inkl. `SyncRewardNoticesUseCase`, `RewardOpenRequests`) auflösbar.
- **MIRROR**: TEST_STRUCTURE.
- **GOTCHA**: `formatDate` liefert `dd.MM.` ohne Jahr; `noon` = 2026-10-25T10:00Z → „25.10.“. Für die Notifier-Klasse genügt Lint + manuelle Prüfung (wie Aufgaben).
- **VALIDATE**: `cd android && ./gradlew :feature:gamification:testDebugUnitTest :app:testDebugUnitTest`.

### Task 15: Doku
- **ACTION**:
  - `docs/03-design/gamification.md`: Kopf von „Belohnungen (Phase 3a)“ („App-Bildschirme folgen in Phase 3b“) → Verweis; neuer Abschnitt **„App-Seite Belohnungen (Phase 3b)“**:
    Bildschirm-Tabelle (Ich, Belohnungen, Heute, Familie, Belohnungen verwalten), Hinweise (wer/was, Basislinie, verpasste Übergänge, `game_redemptions.json`),
    Cache Version 3, Offline-Regeln, Tests.
  - `docs/04-verification/traceability.md` Zeilen REQ-GAME-040–043: App-Tests ergänzen, Status „umgesetzt (Backend + App; Abnahme Testhandy offen)“;
    bei REQ-GAME-041 „2. Kriterium (Kauf-Hinweis) folgt mit Phase 4“.
  - PRD: Phase-3-Zeile → 3b-Plan/Bericht verlinken (Status bleibt `in-progress` bis Abnahme); Phase-4-Scope um „Kauf-Hinweis bei Sparziel (REQ-GAME-041)“ ergänzen.
  - ADR-0013 gilt unverändert (kein neues ADR) — im Design-Abschnitt auf ADR-0013 verweisen.
- **VALIDATE**: `grep -n "belohnungen-app" docs/01-requirements/gamification.prd.md`.

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `rewardNotices` | previous `null` | leer | ja (Basislinie) |
| `rewardNotices` Eltern | neue `PENDING` von c-2, Name „Ben“ | `Requested("Ben", …)` | – |
| `rewardNotices` Kind | vorher `PENDING`, jetzt `REJECTED` „Ferien“ | `Rejected(reason = "Ferien")` | – |
| `rewardNotices` Kind | unbekannt, `FULFILLED`, `decidedAt` > `syncedAt` | `Fulfilled` | ja |
| `rewardNotices` Kind | eigene neue `PENDING` | leer | ja |
| Mapper | `REJECTED` ohne `decidedAt` | `AppError.Server` | ja |
| `ManageRewardActions.save` | Titel „ “ | `Validation`, kein Aufruf | ja |
| `ChildRewardActions.redeem` | `409` | `Conflict`, kein Sync | ja |
| `LoadPointsUseCase` (Kind) | Sparziel-Fehler | nichts gespeichert, Rückfall auf alten Stand | ja |
| `LoadTodaySavingsUseCase` | Netzwerkfehler, Cache mit Ziel | Ziel aus Cache | ja |
| Cache | Datei Version 2 | `null` | ja |

### Edge Cases Checklist
- [x] Keine Belohnungen → Leerhinweise (Kind/Eltern)
- [x] Guthaben < Preis → Einlösen deaktiviert, fehlende Punkte sichtbar
- [x] Sparziel auf deaktivierte Belohnung → „Nicht mehr einlösbar“, kein Einlösen
- [x] Gleichzeitiges Einlösen/Deaktivieren (409) → Server-Text, neu laden
- [x] Doppelklick auf Aktion → `isBusy` blockt
- [x] Offline → „Ich“/„Heute“ aus Cache, Seiten/Buttons deaktiviert
- [x] Keine Benachrichtigungs-Berechtigung → keine Meldung, App funktioniert
- [x] Abmeldung (401) im Worker → Abgleich-Stand gelöscht
- [x] Erststart nach Update → keine Meldungsflut, kein Badge-Dialog

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Befunde

### Unit Tests
```bash
cd android && ./gradlew :feature:gamification:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite + Build
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerifyDebug assembleDebug assembleRelease
```
EXPECT: grün, Kover ≥ 80 %

### Testinstanz-Build
```bash
cd android && FP_TEST_INSTANCE=true ./gradlew :app:assembleRelease
```

### Manual Validation (Testhandys gegen Testinstanz, Backend ≥ 3a deployt)
- [ ] Eltern: Familie → „Belohnungen“ → „Pizzaabend“ 80 P und „Freizeitpark“ 200 P anlegen; Titel ändern; ungültiger Preis (0, 10001) wird verhindert.
- [ ] Kind: „Ich“ zeigt „Noch kein Sparziel“; „Belohnungen“ → „Freizeitpark“ als Sparziel → „Ich“ und „Heute“ zeigen `x/200`.
- [ ] Kind mit Guthaben ≥ 80: „Pizzaabend“ einlösen (Dialog) → Guthaben sinkt, „Meine Einlösungen: wartet“; Gesammelt/Rangliste unverändert.
- [ ] Eltern-Handy erhält innerhalb ≤ 30 min (bzw. sofort beim Öffnen der App) „Lea möchte 🍕 Pizzaabend einlösen“; Tippen öffnet „Belohnungen verwalten“; Familie zeigt „1 wartet“.
- [ ] Eltern: „Erfüllt“ → Kind erhält „🍕 Pizzaabend erfüllt“. Zweite Einlösung „Ablehnen“ mit Grund → Kind erhält „… abgelehnt“ mit Grund, Guthaben zurück, Verlauf zeigt „Rückbuchung“.
- [ ] Eltern deaktivieren „Freizeitpark“ → Kind-Sparziel zeigt „Nicht mehr einlösbar“, Belohnung verschwindet aus der Liste.
- [ ] Flugmodus: „Ich“/„Heute“ zeigen Sparziel aus lokalem Stand; „Belohnungen“-Button deaktiviert.
- [ ] Erstes Öffnen nach Update: keine Benachrichtigung für alte Einlösungen, kein Badge-Dialog.

---

## Acceptance Criteria
- [ ] Alle Tasks umgesetzt
- [ ] Alle Validierungsbefehle grün
- [ ] Tests mit REQ-IDs geschrieben und grün
- [ ] UX wie oben
- [ ] Doku (Design, Traceability, PRD) aktualisiert

## Completion Checklist
- [ ] Muster aus 1b/2b übernommen (Mapper strikt, Cache getrennt, Laden über ON_RESUME, `isBusy`/`actionError`)
- [ ] Abgleich nach Aufgaben-Vorlage (Mutex, Basislinie, `awaitSession`, 401 → löschen)
- [ ] Fehler über `AppError`/`ErrorText`, keine neuen Logs außer DataStore
- [ ] Keine Magic Numbers in Composables, Grenzen in `RewardLimits`
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen (VMs ggf. aufteilen)
- [ ] Keine Scope-Erweiterung (kein Backend, kein Shop-Hinweis, kein Leisten-Badge)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Kind verpasst „erfüllt/abgelehnt“, weil Eltern vor dem ersten Kind-Abgleich entscheiden | M | M | Sync nach eigenem Einlösen + Regel „unbekannt, aber nach letztem Abgleich entschieden“; Test |
| Meldungsflut nach Update/Neuinstallation | L | M | Basislinie ohne gespeicherten Stand; Test |
| Hersteller-Akkuoptimierung verzögert WorkManager | M | L | Sofort-Abgleich bei `onStart`; ADR-0013-Konsequenz bekannt, Abnahme auf Testhandys |
| `ManageRewardsViewModel` wird groß/komplex (detekt) | M | L | Events in private Funktionen, Draft-Events bündeln |
| Cache-Version 3 verwirft Badges-Stand → kurz kein Offline-Rückfall | L | L | Akzeptiert und dokumentiert (wie 2b) |
| Phase 4 ändert parallel `MeScreen`/`strings.xml`/`Ports.kt` | M | L | Eigene Dateien je Bereich; Konflikte klein halten (PRD Parallelism Notes) |
| PendingIntent-Request-Code kollidiert mit Aufgaben | L | M | eigener Code 2; Lint/Manuell |

## Notes
- Vertrag 0.9.0 ist in `core:api` generiert (`listRewards`, `createReward`, `updateReward`, `archiveReward`, `redeemReward`, `listRedemptions`,
  `fulfillRedemption`, `rejectRedemption`, `getSavingsGoal`, `setSavingsGoal`, `clearSavingsGoal`).
- Backend-Regeln, die die App nicht doppelt prüfen muss: Rollen (403), Guthaben/aktiv beim Einlösen (409), nur `PENDING` entscheidbar (409),
  Sparziel nur auf aktive Belohnung (409). Die App zeigt den Server-Text aus `AppError.Conflict`.
- Ab-/Rückbuchungen (`REWARD`, `REWARD_REFUND`) erscheinen bereits im Verlauf (Labels existieren seit 1b).
- Umsetzung laut `CLAUDE.md` in einer **neuen Session** (Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/belohnungen-app.plan.md`.
