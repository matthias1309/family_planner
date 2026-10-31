# Plan: Badges in der App (GAME Phase 2b)

## Summary
Das Feature-Modul `feature:gamification` zeigt die Badges aus Phase 2a: Kinder sehen unter **„Ich“** ihre Badge-Übersicht
(erhaltene mit Datum, offene mit Fortschritt `7/10`, geheime als „???“ mit Hinweis) und beim Öffnen einen **Hinweis-Dialog für
neu erhaltene Badges**. Antippen eines Geschwisterkinds in der Rangliste öffnet dessen **erhaltene Badges**. Eltern sehen die
Badges eines Kindes (mit Fortschritt) auf der Punktekonto-Seite. Keine Backend- oder API-Änderung — Vertrag `openapi.yaml`
0.8.0 (`GET /api/v1/gamification/members/{memberId}/badges`) wird unverändert genutzt.

## User Story
Als Kind möchte ich meine Badges, die noch erreichbaren mit Fortschritt und die meiner Geschwister sehen und mitbekommen, wenn
ich ein neues Badge bekommen habe, damit sich Mithelfen wie in einem Videospiel anfühlt.

## Problem → Solution
Backend vergibt Badges, die App zeigt sie nirgends → Badge-Übersicht, Geschwister-Ansicht, Eltern-Ansicht und Neu-Hinweis in der
App, sodass Phase 2 auf den Testhandys abgenommen werden kann.

## Metadata
- **Complexity**: Medium (≈ 8 neue, ≈ 14 geänderte Dateien; bekannte Muster, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: Phase 2 — Badges, Teil **2b App**
- **Requirements**: REQ-GAME-020 (Hinweis beim nächsten Öffnen), REQ-GAME-021 (Übersicht, Fortschritt, geheim, Geschwister)
- **Version**: App 0.11.0 / `versionCode` 12

---

## Entscheidungen (Planung 2026-10-31)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Ort „Ich“ (Kind) | Neuer Abschnitt **„Badges n/m“** zwischen „Platz n von m“/Rangliste und Verlauf: erst Rangliste, dann Badges, dann Verlauf | REQ-GAME-021; Rangliste bleibt oben wie heute |
| Darstellung | Liste in Katalogreihenfolge (wie Server). Zeile: Emoji (40 dp Kreis) · Titel · Bedingung · rechts „erhalten TT.MM.“ **oder** `LinearProgressIndicator` + „7/10“. Erhaltene Zeilen normal, offene mit `alpha 0.6` | Kein Grid: lange deutsche Titel/Bedingungen; Listenmuster wie `LedgerList` |
| Geheime Badges | Maskiert (`title == null`): Emoji „❓“, Titel „???“, zweite Zeile = `hint` (falls vorhanden), kein Fortschritt | REQ-GAME-021; Server liefert bereits maskiert |
| Neu-Hinweis | **In-App-Dialog** „Neues Badge!“ auf „Ich“ mit Emoji + Titel je neuem Badge, Button „Super!“. „Neu“ = in der frischen Übersicht erhalten, im zuletzt gespeicherten lokalen Stand nicht erhalten. **Kein** gespeicherter Stand mit Badges → Basislinie, kein Dialog | REQ-GAME-020 „beim nächsten Öffnen“; Basislinie wie ADR-0013 Nr. 5 (keine Flut nach Update/Neuinstallation); kein zusätzlicher Speicher nötig |
| Systembenachrichtigung | **Keine** (kein WorkManager-Abgleich für Badges) | Akzeptanzkriterium verlangt Hinweis beim Öffnen; YAGNI; ADR-0013-Abgleich ist an Essensplan gebunden |
| Geschwister | Auf „Ich“ ist die Rangliste antippbar (nur online) → Unterseite **„Badges von <Name>“** (`MemberBadgesRoute`, ohne Leiste) mit „n von m Badges“ und Liste. Server liefert für Geschwister nur erhaltene; Antippen der eigenen Zeile öffnet dieselbe Seite mit eigener Sicht | REQ-GAME-021 „Geschwisterkind ansehen“; eine Seite für alle Fälle |
| Eltern | Punktekonto-Seite (`ChildAccountScreen`) bekommt Abschnitt **„Badges n/m“** vor dem Verlauf (mit Fortschritt, Server-Eltern-Sicht) | Eltern sind Spielleitung; kein neuer Bildschirm |
| Offline | Eigene Badges im lokalen Stand (`game_points.json`, **Version 2**), gleiche Rückfallregeln wie Punkte. Geschwister- und Eltern-Ansicht nur online | ADR-0011; wie Punktekonto für Eltern |
| Laden | Kind: Konto, Rangliste und **eigene Badges** parallel in `LoadPointsUseCase`; Fehler einer Anfrage → ganzer Ladevorgang fehlgeschlagen (kein halber Stand) | Bestehende Regel „halber Ladeerfolg wird nicht gespeichert“ |
| Port | `PointsRepository.badges(memberId)` (kein eigenes Repository) | Eine API (`GamificationApi`), ein Remote-Adapter; KISS |

---

## UX Design

### Before
```
┌ Ich ──────────────────────────┐
│ [Gesammelt 42] [Guthaben 40]  │
│ Platz 2 von 3                 │
│ Rangliste (nicht antippbar)   │
│ Verlauf …                     │
└───────────────────────────────┘
```

### After
```
┌ Ich ──────────────────────────┐      ┌ Neues Badge! ─────────┐
│ [Gesammelt 42] [Guthaben 40]  │      │ 🍳 Erstes Gericht      │
│ Platz 2 von 3                 │      │          [ Super! ]    │
│ Rangliste            (tap ›)  │      └───────────────────────┘
│ Badges 4/12                   │
│ 🧹 Erste Aufgabe   erhalten 25.10.
│ ✅ Fleißig         ▓▓▓▓░░ 7/10│
│ ❓ ???  Bleib richtig lange dran
│ Verlauf …                     │
└───────────────────────────────┘
Tap „Ben ›“ → ┌ Zurück ─────────────────┐
              │ Badges von Ben          │
              │ 3 von 12 Badges         │
              │ 🧹 Erste Aufgabe 20.10. │
              └─────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| „Ich“ → Rangliste | nicht antippbar | antippbar (online) → „Badges von …“ | offline `onClick = null` |
| „Ich“ öffnen | – | Dialog bei neuen Badges | einmal je neuem Badge; Basislinie ohne Dialog |
| „Ich“ | – | Abschnitt „Badges n/m“ | offline aus lokalem Stand |
| Familie → Kind → Punktekonto | Stand, Buchen, Verlauf | + Abschnitt „Badges n/m“ mit Fortschritt | nur online |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification/domain/UseCases.kt` | all | `LoadPointsUseCase` wird um Badges + Neu-Erkennung erweitert |
| P0 | `.../gamification/data/StoredPoints.kt` | all | Cache-Format, Version → 2 |
| P0 | `.../gamification/ui/me/MeScreen.kt`, `MeViewModel.kt` | all | Abschnitt + Dialog + Navigation |
| P0 | `.../gamification/ui/account/ChildAccountScreen.kt`, `ChildAccountViewModel.kt` | all | Eltern-Abschnitt; Lade-/Event-Muster |
| P0 | `api/openapi.yaml` | Schemas `Badge`, `BadgeProgress`, `BadgeOverview` (~2038–2087), Pfad `/members/{memberId}/badges` (~992) | Vertrag |
| P1 | `.../gamification/data/Mappers.kt`, `Parsing.kt`, `RemotePointsRepository.kt` | all | Mapping-Muster `mapAllOrNull`, `parseInstant`, `apiCall` |
| P1 | `.../gamification/ui/components/LedgerList.kt`, `LeaderboardList.kt` | all | Listen-Komponente als `Column` |
| P1 | `.../gamification/GamificationNavigation.kt`, `GamificationModule.kt` | all | Route + Koin |
| P1 | `android/feature/gamification/src/test/.../fakes/Fakes.kt`, `ui/ViewModelsTest.kt`, `ui/ScreensTest.kt`, `domain/UseCasesTest.kt`, `data/DataStorePointsCacheTest.kt`, `data/RemotePointsRepositoryTest.kt` | all | Testmuster |
| P1 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppPointsFakes.kt`, `AppNavHostTest.kt` (Z. 129, 220–230) | – | müssen mit dem erweiterten Port/VM-Konstruktor kompilieren |
| P2 | `backend/gamification/src/main/kotlin/de/familyplanner/gamification/badges/Badge.kt` | all | Katalog (12 Badges, 3 geheim) — nur zum Verständnis/Testdaten |
| P2 | `docs/03-design/gamification.md` | „Badges (Phase 2a)“, „App-Seite (Phase 1b)“ | Doku-Ort für 2b |

## External Documentation
Keine externe Recherche nötig — nur etablierte interne Muster und Material3 (`LinearProgressIndicator(progress = { … })`,
`AlertDialog`), die bereits im Projekt verwendet werden bzw. Standard sind.

---

## Patterns to Mirror

### GENERATED_CLIENT (Vertrag 0.8.0)
```kotlin
// SOURCE: android/core/api/build/generated/openapi/.../client/GamificationApi.kt:54
suspend fun getBadges(@Path("memberId") memberId: kotlin.String): Response<BadgeOverviewResponse>
// de.familyplanner.api.model.Badge(id: String, secret: Boolean, earned: Boolean, emoji: String?, title: String?,
//   description: String?, hint: String?, awardedAt: String?, progress: BadgeProgress?)
// de.familyplanner.api.model.BadgeProgress(current: Int, target: Int)
// de.familyplanner.api.model.BadgeOverview(memberId: String, earnedCount: Int, totalCount: Int, items: List<Badge>)
```

### REPOSITORY / apiCall
```kotlin
// SOURCE: .../data/RemotePointsRepository.kt
override suspend fun account(memberId: String, limit: Int): ApiResult<PointsAccount> =
    apiCall(json, { api.getPointsAccount(memberId, limit) }) { it.data?.toDomain() }
```
`apiCall` liefert `AppError.Server`, wenn der Mapper `null` zurückgibt.

### MAPPER (strikt, null = Serverfehler)
```kotlin
// SOURCE: .../data/Mappers.kt
internal fun ApiPointsAccount.toDomain(): PointsAccount? {
    val entries = items.mapAllOrNull { it.toDomain() } ?: return null
    return PointsAccount(memberId, collected, balance, entries)
}
```

### CACHE FORMAT (getrennt von Domäne, Epoch-Millis, Version)
```kotlin
// SOURCE: .../data/StoredPoints.kt
internal const val CACHE_VERSION = 1
@Serializable
data class StoredPoints(val version: Int = CACHE_VERSION, val memberId: String? = null, val syncedAtMillis: Long = 0,
    val account: StoredAccount? = null, val leaderboard: List<StoredRankedChild> = emptyList())
```
Serializer verwirft jede andere Version (`takeIf { it.version == CACHE_VERSION }`).

### USE CASE (parallel, erster Fehler zählt)
```kotlin
// SOURCE: .../domain/UseCases.kt (fetch)
coroutineScope {
    val account = if (withAccount) async { repository.account(memberId, PointsLimits.HISTORY_LIMIT) } else null
    val leaderboard = async { repository.leaderboard() }
    ...
    when { accountResult is ApiResult.Failure -> accountResult; leaderboardResult is ApiResult.Failure -> leaderboardResult; else -> … }
}
```

### VIEWMODEL (StateFlow, Laden nur über Refresh/ON_RESUME, Job abbrechen)
```kotlin
// SOURCE: .../ui/me/MeViewModel.kt
private fun refresh() {
    loadJob?.cancel()
    _state.update { it.copy(isLoading = true, error = null) }
    loadJob = viewModelScope.launch { val result = loadPoints(); _state.update { when (result) { … } } }
}
```

### ROUTE + SCREEN (Args als ein Koin-Parameter, Zurück-Button, Pull-to-Refresh)
```kotlin
// SOURCE: .../ui/account/ChildAccountScreen.kt
@Composable
fun ChildAccountRoute(args: ChildAccountArgs, onBack: () -> Unit) {
    val viewModel = koinViewModel<ChildAccountViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onEvent(ChildAccountEvent.Refresh) }
    ChildAccountScreen(state, viewModel::onEvent, onBack)
}
```

### LIST COMPONENT (Column, eingebettet in LazyColumn-Item, Leerhinweis)
```kotlin
// SOURCE: .../ui/components/LedgerList.kt
@Composable
fun LedgerList(entries: List<LedgerEntry>) {
    if (entries.isEmpty()) { Text(stringResource(R.string.game_history_empty)); return }
    Column { entries.forEach { entry -> Row(…) { … } } }
}
```

### ERROR / LOGGING
Fehler als `AppError` im UI-State, Anzeige über `ErrorText(state.error)` (core:ui). Logging nur im Cache (`Log.w(TAG, …)`)
— neue Codepfade loggen nichts zusätzlich.

### TEST_STRUCTURE
```kotlin
// SOURCE: .../test/.../ui/ViewModelsTest.kt
@get:Rule val mainDispatcher = MainDispatcherRule()
private val repository = FakePointsRepository()
@Test fun `REQ-GAME-012 me view model shows totals, rank and history`() { val vm = me(); vm.onEvent(MeEvent.Refresh); … }
// ScreensTest: @RunWith(RobolectricTestRunner::class), createComposeRule(), FamilyPlannerTheme { … }, onNodeWithText
// Repository: MockWebServer + createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
// Cache: runBlocking, newCache(), file.writeText(…) für alte/kaputte Dateien
```

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `.../domain/Badges.kt` | CREATE | `Badge`, `BadgeProgress`, `BadgeOverview`, `newlyEarned()` |
| `.../domain/Models.kt` | UPDATE | `PointsSnapshot.badges`, `PointsLoad.Fresh.newBadges` |
| `.../domain/Ports.kt` | UPDATE | `PointsRepository.badges(memberId)` |
| `.../domain/UseCases.kt` | UPDATE | `LoadPointsUseCase` lädt Badges + erkennt neue; `LoadBadgesUseCase` |
| `.../data/Mappers.kt` | UPDATE | `ApiBadge/ApiBadgeOverview.toDomain()` |
| `.../data/RemotePointsRepository.kt` | UPDATE | `badges()` |
| `.../data/StoredPoints.kt` | UPDATE | Version 2, `StoredBadgeOverview`, `StoredBadge` |
| `.../ui/components/BadgeList.kt` | CREATE | Liste + Kopfzeile |
| `.../ui/components/NewBadgesDialog.kt` | CREATE | Hinweis-Dialog |
| `.../ui/badges/MemberBadgesScreen.kt` | CREATE | Route + Screen „Badges von …“ |
| `.../ui/badges/MemberBadgesViewModel.kt` | CREATE | Args, UiState, Event, VM |
| `.../ui/me/MeScreen.kt`, `MeViewModel.kt` | UPDATE | Abschnitt, Dialog, Klick in Rangliste |
| `.../ui/account/ChildAccountScreen.kt`, `ChildAccountViewModel.kt` | UPDATE | Abschnitt Badges |
| `.../GamificationNavigation.kt` | UPDATE | `MemberBadgesRoute`, `MeRoute(onOpenBadges)` |
| `.../GamificationModule.kt` | UPDATE | `LoadBadgesUseCase`, `MemberBadgesViewModel`, VM-Konstruktor |
| `.../res/values/strings.xml` | UPDATE | neue Texte |
| Tests: `fakes/Fakes.kt`, `domain/UseCasesTest.kt`, `domain/BadgesTest.kt` (CREATE), `data/RemotePointsRepositoryTest.kt`, `data/DataStorePointsCacheTest.kt`, `ui/ViewModelsTest.kt`, `ui/ScreensTest.kt`, `GamificationModuleTest.kt` | UPDATE/CREATE | TDD |
| `android/app/src/test/.../AppPointsFakes.kt`, `AppNavHostTest.kt` | UPDATE | Port + VM-Konstruktoren |
| `android/app/build.gradle.kts` | UPDATE | 0.11.0 / 12 |
| `docs/03-design/gamification.md`, `docs/04-verification/traceability.md`, `docs/01-requirements/gamification.prd.md` | UPDATE | V-Modell |

(`...` = `android/feature/gamification/src/main/kotlin/de/familyplanner/android/feature/gamification`, Tests analog unter `src/test/kotlin/...`)

## NOT Building

- Keine Backend-/OpenAPI-Änderung (Vertrag 0.8.0 reicht).
- Keine Systembenachrichtigung/WorkManager für neue Badges.
- Keine Badges auf „Heute“, in der Board-Lane oder in der Rangliste (Avatar-Phase 4 entscheidet über Rangliste).
- Kein Detail-Bildschirm pro Badge, keine Animation/Konfetti.
- Keine Offline-Ansicht für Geschwister- und Eltern-Badges.
- Keine Eltern-Funktion zum Vergeben/Entziehen von Badges.

---

## Step-by-Step Tasks

### Task 1: Domänenmodell Badges
- **ACTION**: `domain/Badges.kt` anlegen.
- **IMPLEMENT**:
  ```kotlin
  data class BadgeProgress(val current: Int, val target: Int)
  data class Badge(
      val id: String, val emoji: String?, val title: String?, val description: String?, val hint: String?,
      val secret: Boolean, val earned: Boolean, val awardedAt: Instant?, val progress: BadgeProgress?,
  ) { /** Geheim und noch nicht erhalten: Server liefert weder Titel noch Bedingung. */ val masked: Boolean get() = title == null }
  data class BadgeOverview(val memberId: String, val earnedCount: Int, val totalCount: Int, val items: List<Badge>)
  /** Badges, die in [this] erhalten sind, in [previous] aber nicht; ohne [previous] (Basislinie) leer. */
  fun BadgeOverview.newlyEarned(previous: BadgeOverview?): List<Badge> {
      if (previous == null) return emptyList()
      val before = previous.items.filter { it.earned }.map { it.id }.toSet()
      return items.filter { it.earned && it.id !in before }
  }
  ```
- **MIRROR**: `domain/Models.kt` (reine Datenklassen, KDoc deutsch, kurz).
- **GOTCHA**: `newlyEarned` nur auf gleicher `memberId` sinnvoll — Cache liefert schon nur das eigene Mitglied.
- **VALIDATE**: `BadgesTest` (Task 11): Basislinie leer, neues erkannt, bereits erhaltenes nicht erneut.

### Task 2: Snapshot + Ladeergebnis erweitern
- **ACTION**: `Models.kt` ändern.
- **IMPLEMENT**: `PointsSnapshot(account, leaderboard, syncedAt, badges: BadgeOverview? = null)` (Parameter **am Ende mit Default**, damit
  bestehende Aufrufe kompilieren). `PointsLoad.Fresh(snapshot, newBadges: List<Badge> = emptyList())`. KDoc: „[badges] nur für Kinder (eigene)“.
- **GOTCHA**: `AppPointsFakes`/`Fakes.snapshot()` nutzen positionale Argumente → Default am Ende hält sie kompatibel.
- **VALIDATE**: `./gradlew :feature:gamification:compileDebugKotlin`.

### Task 3: Port + Remote-Adapter + Mapper
- **ACTION**: `Ports.kt`: `suspend fun badges(memberId: String): ApiResult<BadgeOverview>`. `Mappers.kt`:
  ```kotlin
  internal fun ApiBadge.toDomain(): Badge? {
      val awarded = awardedAt?.let { parseInstant(it) ?: return null }
      if (earned && awarded == null) return null
      return Badge(id, emoji, title, description, hint, secret, earned, awarded, progress?.let { BadgeProgress(it.current, it.target) })
  }
  internal fun ApiBadgeOverview.toDomain(): BadgeOverview? {
      val badges = items.mapAllOrNull { it.toDomain() } ?: return null
      return BadgeOverview(memberId, earnedCount, totalCount, badges)
  }
  ```
  Imports `de.familyplanner.api.model.Badge as ApiBadge`, `…BadgeOverview as ApiBadgeOverview`.
  `RemotePointsRepository`: `override suspend fun badges(memberId: String) = apiCall(json, { api.getBadges(memberId) }) { it.data?.toDomain() }`.
- **MIRROR**: REPOSITORY / MAPPER.
- **GOTCHA**: Unparsebares `awardedAt` oder erhaltenes Badge ohne Datum → ganze Übersicht `null` → `AppError.Server` (Muster „Serverfehler statt halber Liste“).
- **VALIDATE**: `RemotePointsRepositoryTest` (Task 11).

### Task 4: Lokaler Stand Version 2
- **ACTION**: `StoredPoints.kt`.
- **IMPLEMENT**: `CACHE_VERSION = 2`; `StoredPoints.badges: StoredBadgeOverview? = null`;
  ```kotlin
  @Serializable data class StoredBadgeOverview(val memberId: String, val earnedCount: Int, val totalCount: Int, val items: List<StoredBadge>)
  @Serializable data class StoredBadge(val id: String, val emoji: String? = null, val title: String? = null, val description: String? = null,
      val hint: String? = null, val secret: Boolean, val earned: Boolean, val awardedAtMillis: Long? = null,
      val progressCurrent: Int? = null, val progressTarget: Int? = null)
  ```
  `toStored`/`toDomain` ergänzen (Progress nur, wenn beide Werte gesetzt). KDoc an `CACHE_VERSION`: „2: Badges (Phase 2b)“.
- **MIRROR**: CACHE FORMAT.
- **GOTCHA**: Datei mit Version 1 wird verworfen (Serializer) → erster Start nach Update hat keinen alten Stand → Basislinie, **kein**
  Dialog-Schwall; offline direkt nach Update gibt es keinen Rückfall, bis einmal online geladen wurde (akzeptiert, dokumentieren).
  Test `unknown entry kind counts as not stored` schreibt `"version":1` → auf `2` ändern.
- **VALIDATE**: `DataStorePointsCacheTest` (Task 11).

### Task 5: Use Cases
- **ACTION**: `UseCases.kt`.
- **IMPLEMENT**:
  - `fetch` lädt für Kinder zusätzlich `async { repository.badges(memberId) }`; Rückgabe als kleine private Datenklasse
    `private data class Fetched(val account: PointsAccount?, val leaderboard: List<RankedChild>, val badges: BadgeOverview?)`
    statt `Pair` (drei Werte). Fehlerreihenfolge: Konto, Rangliste, Badges.
  - Im Erfolgsfall: `val previous = cache.read(memberId)?.badges` **vor** `cache.write`; `newBadges = badges?.newlyEarned(previous).orEmpty()`;
    `PointsLoad.Fresh(snapshot, newBadges)`.
  - Neu: `class LoadBadgesUseCase(private val repository: PointsRepository) { suspend operator fun invoke(memberId: String) = repository.badges(memberId) }`
    (für Geschwister und Eltern, ohne lokalen Stand; KDoc wie `LoadChildAccountUseCase`).
- **MIRROR**: USE CASE.
- **GOTCHA**: `cache.read` vor dem Schreiben aufrufen; `cache.read` darf `null` liefern (IOException wird intern geloggt). `fetch` bleibt < 50 Zeilen — ggf. `firstFailure(vararg results)` als private Hilfsfunktion.
- **VALIDATE**: `UseCasesTest` (Task 11).

### Task 6: Komponenten `BadgeList` + `NewBadgesDialog`
- **ACTION**: `ui/components/BadgeList.kt`, `ui/components/NewBadgesDialog.kt`.
- **IMPLEMENT**:
  - `@Composable fun BadgeHeader(overview: BadgeOverview)` → `Text(stringResource(R.string.game_badges_count, earnedCount, totalCount), titleMedium)`.
  - `@Composable fun BadgeList(badges: List<Badge>)`: leer → `game_badges_empty`; sonst `Column`, je Badge `Row`:
    Kreis (`Surface(CircleShape, secondaryContainer, size 40.dp)`) mit `badge.emoji ?: "❓"`; `Column(weight 1f)` mit Titel
    (`badge.title ?: stringResource(R.string.game_badge_secret)`) und zweiter Zeile (`description ?: hint`, `bodySmall`, optional);
    rechts: erhalten → `stringResource(R.string.game_badge_awarded, formatDate(awardedAt.atZone(BerlinZone).toLocalDate()))`;
    sonst `progress` → `Column { LinearProgressIndicator(progress = { current.toFloat() / target }, Modifier.width(72.dp)); Text("$current/$target", labelSmall) }`.
    Offene Zeilen `Modifier.alpha(0.6f)`.
  - `NewBadgesDialog(badges: List<Badge>, onDismiss: () -> Unit)`: `AlertDialog`, Titel `game_new_badge_title` (Plural über
    `pluralStringResource(R.plurals.game_new_badges_title, size)`), Text: je Badge „emoji title“, `confirmButton = TextButton(onDismiss) { Text(game_new_badge_ok) }`.
- **MIRROR**: LIST COMPONENT; `LeaderboardList.Initial` (Kreis).
- **IMPORTS**: `de.familyplanner.android.core.ui.formatDate`, `BerlinZone`, `androidx.compose.ui.draw.alpha`, `androidx.compose.ui.res.pluralStringResource`.
- **GOTCHA**: Magic Numbers als `private val` (`BadgeSize = 40.dp`, `ProgressWidth = 72.dp`, `OPEN_ALPHA = 0.6f`). Kein `LazyColumn` in der Komponente (wird eingebettet).
- **VALIDATE**: `ScreensTest` (Task 11).

### Task 7: „Ich“ erweitern
- **ACTION**: `MeViewModel.kt`, `MeScreen.kt`.
- **IMPLEMENT**:
  - `MeUiState.newBadges: List<Badge> = emptyList()`; `MeEvent.DismissNewBadges`.
  - Bei `PointsLoad.Fresh` → `newBadges = it.newBadges + result.newBadges` (nicht verlieren, falls Dialog noch offen und erneut geladen wird; per `distinctBy { it.id }`).
  - `DismissNewBadges` → `newBadges = emptyList()`.
  - `MeRoute(onOpenBadges: (memberId: String, name: String) -> Unit, viewModel = koinViewModel())`; `MeScreen(state, onEvent, onOpenBadges)`.
  - Rangliste: `onClick = if (state.isOffline) null else { child -> onOpenBadges(child.memberId, child.name) }`.
  - Nach Rangliste: `snapshot.badges?.let { item { BadgeHeader(it) }; item { BadgeList(it.items) } }`, dann Verlauf.
  - Außerhalb des `PullToRefreshBox`: `if (state.newBadges.isNotEmpty()) NewBadgesDialog(state.newBadges) { onEvent(MeEvent.DismissNewBadges) }`.
- **MIRROR**: `ChildAccountScreen` (Dialog nach der Box), FamilyPointsSection (offline-Klick).
- **GOTCHA**: `ScreensTest`-Aufrufe von `MeScreen` brauchen den neuen Parameter.
- **VALIDATE**: `ViewModelsTest`, `ScreensTest`.

### Task 8: Seite „Badges von …“
- **ACTION**: `ui/badges/MemberBadgesViewModel.kt`, `ui/badges/MemberBadgesScreen.kt`.
- **IMPLEMENT**:
  ```kotlin
  data class MemberBadgesArgs(val memberId: String, val name: String)
  data class MemberBadgesUiState(val args: MemberBadgesArgs, val overview: BadgeOverview? = null, val isLoading: Boolean = true, val error: AppError? = null)
  sealed interface MemberBadgesEvent { data object Refresh : MemberBadgesEvent }
  class MemberBadgesViewModel(private val args: MemberBadgesArgs, private val loadBadges: LoadBadgesUseCase) : ViewModel() { … refresh() wie ChildAccountViewModel … }
  ```
  Screen: `PullToRefreshBox` + `LazyColumn` (24 dp): Zurück-Button, Titel `game_member_badges_title` („Badges von %1$s“, headlineMedium),
  `ErrorText`, `overview?.let { BadgeHeader(it); BadgeList(it.items) }`. Route analog `ChildAccountRoute` (`parametersOf(args)`).
- **MIRROR**: ROUTE + SCREEN, VIEWMODEL.
- **VALIDATE**: `ViewModelsTest`, `ScreensTest`.

### Task 9: Eltern-Abschnitt im Punktekonto
- **ACTION**: `ChildAccountViewModel.kt`, `ChildAccountScreen.kt`.
- **IMPLEMENT**: Konstruktor `ChildAccountViewModel(args, loadAccount, loadBadges: LoadBadgesUseCase, bookPoints)`; `ChildAccountUiState.badges: BadgeOverview? = null`.
  `refresh()` lädt Konto und Badges parallel (`coroutineScope { async … }` im `launch`); Fehler: erster Fehler (Konto vor Badges) in `error`, erfolgreiche Teile werden trotzdem gesetzt.
  Screen: nach dem Buchen-Button und vor „Verlauf“: `state.badges?.let { item { BadgeHeader(it) }; item { BadgeList(it.items) } }`.
- **GOTCHA**: Koin-Definition und `AppNavHostTest` (Z. 229) haben vier Parameter. Reihenfolge der Parameter exakt so, sonst verwechselt Koin `get()` nicht (Typen unterscheiden sich), aber Tests mit positionalen Argumenten.
- **VALIDATE**: `ViewModelsTest` (Eltern sehen Badges mit Fortschritt; Badge-Fehler zeigt Konto trotzdem).

### Task 10: Navigation, Koin, Strings, Version
- **ACTION**:
  - `GamificationNavigation.kt`: `@Serializable data class MemberBadgesRoute(val memberId: String, val name: String)`;
    `composable<MeHomeRoute> { MeRoute(onOpenBadges = navController::openMemberBadges) }`;
    `composable<MemberBadgesRoute> { … MemberBadgesRoute(MemberBadgesArgs(route.memberId, route.name), onBack = { navController.popBackStack() }) }`;
    `fun NavHostController.openMemberBadges(memberId: String, name: String) = navigate(MemberBadgesRoute(memberId, name))`.
    Namenskonflikt Route-Klasse vs. Composable `MemberBadgesRoute` — wie bei `ChildAccountRoute` gelöst (Composable importiert aus `ui.badges`, Klasse im Paket der Navigation); falls der Compiler mehrdeutig meldet, Composable-Import mit Alias.
  - `GamificationModule.kt`: `factory { LoadBadgesUseCase(get()) }`, `viewModel { params -> MemberBadgesViewModel(params.get(), get()) }`,
    `ChildAccountViewModel(params.get(), get(), get(), get())`.
  - `strings.xml`:
    ```xml
    <string name="game_badges_count">Badges %1$d/%2$d</string>
    <string name="game_badges_empty">Noch keine Badges</string>
    <string name="game_badge_secret">???</string>
    <string name="game_badge_awarded">erhalten %1$s</string>
    <plurals name="game_new_badges_title"><item quantity="one">Neues Badge!</item><item quantity="other">Neue Badges!</item></plurals>
    <string name="game_new_badge_ok">Super!</string>
    <string name="game_member_badges_title">Badges von %1$s</string>
    ```
  - `android/app/build.gradle.kts`: `versionCode = 12`, `versionName = "0.11.0"`.
- **GOTCHA**: `MeHomeRoute` ist eine Hauptleisten-Route; die neue Unterseite zeigt (wie `ChildAccountRoute`) keine Leiste, weil `topLevelOf` sie nicht kennt — nichts in `app/` ändern.
- **VALIDATE**: `GamificationModuleTest` (Koin `verify`/Auflösung) und `:app:testDebugUnitTest`.

### Task 11: Tests (TDD — jeweils vor Task 1–10 schreiben)
- **ACTION**: Fakes erweitern, Tests schreiben (Namen mit REQ-ID).
- **IMPLEMENT**:
  - `fakes/Fakes.kt`: `fun badge(id, earned = false, title: String? = "Fleißig", progress: BadgeProgress? = BadgeProgress(7, 10), awardedAt = if (earned) noon else null, secret = false, hint = null, emoji = if (title == null) null else "✅")`,
    `fun overview(vararg items: Badge, memberId = "c-1") = BadgeOverview(memberId, items.count { it.earned }, 12, items.toList())`;
    `FakePointsRepository.badgesResult: ApiResult<BadgeOverview> = ApiResult.Success(overview(badge("first-task", earned = true), badge("tasks-10")))`, `badgesCalls`.
  - `AppPointsFakes.kt` (`StaticPointsRepository`): `badges()` liefert eine kleine Übersicht.
  - `BadgesTest`: `REQ-GAME-020 newly earned is empty without a previous overview`, `REQ-GAME-020 newly earned lists only badges earned since the previous overview`.
  - `UseCasesTest`: `REQ-GAME-021 child loads own badges and stores them`, `REQ-GAME-020 fresh load reports badges earned since the stored snapshot`,
    `REQ-GAME-020 first load without stored badges reports nothing`, `REQ-GAME-021 parent does not load badges`, `REQ-GAME-021 failed badges do not store half a snapshot`,
    `REQ-GAME-021 badges of another member load without cache`.
  - `RemotePointsRepositoryTest`: `REQ-GAME-021 badges map earned, open and masked badges`, `REQ-GAME-021 earned badge without date is a server error`.
  - `DataStorePointsCacheTest`: `REQ-GAME-021 badges survive a restart` (inkl. maskiertem Badge und Fortschritt), `REQ-GAME-021 cache of version 1 counts as empty`.
  - `ViewModelsTest`: `REQ-GAME-020 me view model shows new badges until dismissed`, `REQ-GAME-021 member badges view model loads the member`,
    `REQ-GAME-021 child account view model shows badges with progress`, `REQ-GAME-021 child account keeps the account when badges fail`.
  - `ScreensTest`: `REQ-GAME-021 badge list shows earned date, progress and masked secret badge` (Texte „erhalten 25.10.“, „7/10“, „???“, Hinweis),
    `REQ-GAME-020 me screen shows the new badge dialog`, `REQ-GAME-021 me screen opens badges of a sibling` (Klick „Ben“ → Callback `c-2`),
    `REQ-GAME-021 offline leaderboard is not clickable`, `REQ-GAME-021 member badges screen shows name and count`.
- **MIRROR**: TEST_STRUCTURE.
- **GOTCHA**: `formatDate` (core/ui `Formatting.kt`) liefert `dd.MM.` **ohne Jahr**. `noon` = 2026-10-25T10:00Z → „25.10.“ in Berlin.
- **VALIDATE**: `cd android && ./gradlew :feature:gamification:testDebugUnitTest :app:testDebugUnitTest`.

### Task 12: Doku
- **ACTION**:
  - `docs/03-design/gamification.md`: Kopf „App-Seite folgt in Phase 1b bzw. 2b“ → umgesetzt; neuer Abschnitt **„App-Seite Badges (Phase 2b)“**: Bildschirm-Tabelle (Ich, Badges von …, Punktekonto), Neu-Hinweis mit Basislinie, Cache Version 2, Offline-Regeln, Tests.
  - `docs/04-verification/traceability.md` Zeilen REQ-GAME-020/021: Unit-Tests um App-Tests ergänzen, Status „umgesetzt (Backend + App; Abnahme Testhandy offen)“.
  - PRD Phase-2-Zeile: 2b-Plan/Bericht verlinken (beim Umsetzen: Status bleibt `in-progress` bis Abnahme auf Testhandys).
- **VALIDATE**: Links prüfen (`grep -n "badges-app" docs/01-requirements/gamification.prd.md`).

---

## Testing Strategy

### Unit Tests
| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `newlyEarned` | previous `null` | leer | ja (Basislinie) |
| `newlyEarned` | previous: first-task erhalten; jetzt first-task + tasks-10 | `[tasks-10]` | – |
| Mapper | erhaltenes Badge ohne `awardedAt` | `AppError.Server` | ja |
| Mapper | maskiert (`title/emoji/description/progress = null`, `hint`) | `masked == true`, Hinweis erhalten | ja |
| LoadPoints (Kind) | Badges-Fehler | `Failed`/`Cached`, nichts geschrieben | ja |
| LoadPoints (Eltern) | – | `badgesCalls` leer, `snapshot.badges == null` | – |
| Cache | Datei Version 1 | `null` | ja |
| MeViewModel | Fresh mit neuen Badges, dann Dismiss | Dialogliste gefüllt → leer | – |
| ChildAccountVM | Badges `Server`-Fehler, Konto ok | Konto gesetzt, `error = Server` | ja |

### Edge Cases Checklist
- [x] Leere Badge-Liste (Geschwister ohne Badges) → „Noch keine Badges“
- [x] Geheimes Badge ohne Hinweis → nur „???“
- [x] Fortschritt `current == target` bei offenem Badge (Server kappt) → Balken voll, kein Absturz
- [x] Offline → Rangliste nicht antippbar, Badges aus Cache
- [x] Cache alter Version → Basislinie, kein Dialog
- [x] Erneutes Laden während Dialog offen → keine Duplikate

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

### Manual Validation (Testhandys gegen Testinstanz)
- [ ] Kind öffnet „Ich“: Abschnitt „Badges n/12“ mit erhaltenen (Datum), offenen (Fortschritt) und drei „???“ mit Hinweis.
- [ ] Eltern nehmen eine erste Aufgabe eines Kindes ohne `first-task` ab → Kind öffnet „Ich“ → Dialog „Neues Badge! 🧹 Erste Aufgabe“; erneutes Öffnen zeigt ihn nicht mehr.
- [ ] Erstes Öffnen nach dem Update zeigt **keinen** Dialog für bereits vorhandene Badges.
- [ ] Kind tippt Geschwister in der Rangliste → nur dessen erhaltene Badges, kein Fortschritt.
- [ ] Flugmodus: „Ich“ zeigt Badges aus lokalem Stand, Rangliste nicht antippbar.
- [ ] Eltern: Familie → Kind → Punktekonto zeigt Badges mit Fortschritt.

---

## Acceptance Criteria
- [ ] Alle Tasks umgesetzt
- [ ] Alle Validierungsbefehle grün
- [ ] Tests mit REQ-IDs geschrieben und grün
- [ ] UX wie oben
- [ ] Doku (Design, Traceability, PRD) aktualisiert

## Completion Checklist
- [ ] Muster aus Phase 1b übernommen (Mapper strikt, Cache getrennt, Laden über ON_RESUME)
- [ ] Fehler über `AppError`/`ErrorText`
- [ ] Keine Magic Numbers in Composables
- [ ] Funktionen < 50 Zeilen (`fetch`, `BadgeList`)
- [ ] Keine Scope-Erweiterung (keine Benachrichtigung, kein Backend)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Dialog-Schwall nach Update | M | M | Basislinie: Cache v1 verworfen → kein Vergleich → kein Dialog; Test |
| Hinweis geht verloren, wenn Kind „Ich“ lädt und App sofort schließt | L | L | Akzeptiert; Badge bleibt in der Übersicht sichtbar mit Datum |
| Emoji-Darstellung (ZWJ „👨‍🍳“) auf älteren Geräten | L | L | Testhandys prüfen; Server liefert Emoji, keine App-Änderung nötig |
| Name-Konflikt Route-Klasse/Composable | M | L | Wie `ChildAccountRoute`; ggf. Import-Alias |
| Phasen 3/4 ändern parallel `MeScreen`/`strings.xml` | M | L | Badges als eigene Komponenten; Merge-Konflikte klein halten (PRD Parallelism Notes) |

## Notes
- Vertrag 0.8.0 ist bereits in `core:api` generiert (`GamificationApi.getBadges`, Modelle `Badge`, `BadgeProgress`, `BadgeOverview`).
- Backend-Sicht: Eigene/Eltern → alle Badges mit Fortschritt, geheime maskiert; Geschwister → nur erhaltene, `totalCount` = ganzer Katalog (12). Eltern-Mitglied → 400 (die App fragt nie Badges für Eltern ab).
- Umsetzung laut `CLAUDE.md` in einer **neuen Session** (Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/badges-app.plan.md`.
