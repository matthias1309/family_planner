# Plan: Punktekonto in der App (GAME Phase 1b, inkl. REQ-MEAL-034 App)

## Summary
Neues Android-Feature-Modul `feature:gamification` bringt das Punktekonto aus Phase 1a in die App: Bereich **„Ich“** für Kinder
(Gesammelt, Guthaben, Rang, Rangliste, Verlauf, offline lesbar), Abschnitt **„Punkte“ unter „Familie“** für Eltern (Rangliste,
Kochpunktwert einstellen, Konto eines Kindes mit Verlauf und manueller Buchung). In `feature:meals` kommt die **Kochbestätigung**
dazu (Button im Gericht-Editor, Markierung im Wochenplan). Keine Backend- oder API-Änderung — Vertrag `openapi.yaml` 0.7.0 aus
Phase 1a wird unverändert genutzt.

## User Story
Als Kind möchte ich unter „Ich“ sehen, wie viele Punkte ich gesammelt habe, was ich ausgeben kann, woher die Punkte kommen und
wo ich in der Rangliste stehe; als Elternteil möchte ich Kochen bestätigen, den Küchenboard-Stand als Startbuchung übernehmen,
Punkte korrigieren und den Kochpunktwert einstellen.

## Problem → Solution
Backend bucht Punkte, App zeigt unter „Ich“ „Kommt bald“ und kann Kochen nicht bestätigen → vollständiges Punktekonto in der App,
sodass die Abnahme von Phase 1 auf den Testhandys möglich ist.

## Metadata
- **Complexity**: Large (≈ 40 Dateien, neues Feature-Modul, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/gamification.prd.md`
- **PRD Phase**: Phase 1 — Punktekonto, Teil **1b App**
- **Requirements**: REQ-GAME-010/011 (Anzeige), 012, 013, 014 (App); REQ-MEAL-034 (App)
- **Version**: App 0.10.0 / `versionCode` 11

---

## Entscheidungen (Planung 2026-10-25)

| Thema | Entscheidung | Begründung |
|---|---|---|
| Modul | `android/feature/gamification`, Paket `de.familyplanner.android.feature.gamification`, Aufbau `domain/`, `data/`, `ui/` wie `feature:tasks` | ADR-0001, ADR-0009; kennt `meals`/`tasks` nicht |
| „Ich“ (Kind) | Ein Bildschirm: Kopf mit zwei Kacheln **Gesammelt** / **Guthaben** und „Platz n von m“; Abschnitt **Rangliste** (eigene Zeile hervorgehoben); Abschnitt **Verlauf** (letzte 50 Buchungen) | REQ-GAME-012/014; Rangliste wird heute schon „angeschaut“ |
| Eltern | Abschnitt **„Punkte“** oben in „Familie“ (Slot wie `todayContent`): Rangliste, Zeile „Kochpunkte: n · Ändern“ (Dialog mit Stepper 1–20). Antippen eines Kindes → Unterseite **Punktekonto** (Stand, Verlauf, Button „Punkte buchen“) | PRD Phase 1 „Eltern-Ansicht unter Familie“; kein neuer Hauptbereich (ADR-0014) |
| Manuelle Buchung | Dialog: Auswahl **Gutschreiben / Abziehen** (zwei `FilterChip`), Betrag als Zahl 1–1000, Begründung 1–200 mit Zähler; Vorschlag „Übernahme Küchenboard“ als Platzhalter | REQ-GAME-013; Vorzeichen per Auswahl statt Minus-Eingabe (Zahlentastatur ohne „−“) |
| Rangliste | Reihenfolge und Rang wie vom Server (Gleichstand 1, 1, 3); statt Avatar ein Kreis mit Anfangsbuchstaben | Avatar folgt in Phase 4 |
| Verlauf | Zeile: Datum (`formatDate`), Titel, Art („Aufgabe“, „Kochen“, „Korrektur“, …), Betrag `+n` (primary) / `−n` (error) | REQ-GAME-012 |
| Offline | „Ich“ und Eltern-Rangliste aus lokalem Stand (DataStore, je Mitglied), Hinweis „Offline – Stand vom …“; Kind-Konto für Eltern und alle Schreibaktionen nur online | ADR-0011; wie Board |
| Aktualisieren | Pull-to-Refresh auf „Ich“ und Punktekonto; Neuladen bei `ON_RESUME` (`LifecycleEventEffect`), damit Buchungen nach Rückkehr sichtbar sind | Abnahmen/Kochbestätigung passieren auf anderen Geräten |
| Hinweise | **Keine** neuen Benachrichtigungen; Aufgaben-Hinweis „+n Punkte“ existiert bereits (TASK Phase 3), Badge-Hinweise kommen mit Phase 2 | YAGNI; REQ-GAME-010–014 fordern keine |
| Kochbestätigung | Gericht-Editor (Eltern, Kindergericht `APPROVED`, nicht bestätigt, Kochtag ≤ heute): Button „Als gekocht bestätigen“ + `ConfirmDialog` („Lea bekommt dafür Kochpunkte.“); bestätigtes Gericht: „Gekocht bestätigt am …“ | REQ-MEAL-034; der Editor ist der Ort, an dem Eltern Kindergerichte schon freigeben |
| Wochenplan | Markierung „Gekocht ✓“ bei `cookedAt != null` (alle); für Eltern „Kochen bestätigen?“ (tertiary) bei bestätigbaren Gerichten | Erfolgsmetrik „100 % bestätigt“: Eltern sehen offene Bestätigungen |
| ADR | Kein neues ADR; Design-Doku `gamification.md` Abschnitt „App-Seite (Phase 1b)“, `meals.md` Ergänzung REQ-MEAL-034 App | Keine neue Architekturentscheidung |

---

## UX Design

### Before
```
Kind: Ich                          Eltern: Familie                Eltern: Gericht (Kind, freigegeben)
┌──────────────────────────┐      ┌──────────────────────────┐   ┌──────────────────────────┐
│ Ich                      │      │ Familie                  │   │ Gericht ändern           │
│ Hier findest du bald     │      │ Mitglieder …             │   │ Name, Zutaten, Koch …    │
│ Punkte, Badges …         │      │ Geräte …                 │   │ [Speichern] [Löschen]    │
└──────────────────────────┘      └──────────────────────────┘   └──────────────────────────┘
```

### After
```
Kind (Lea): Ich                       Eltern: Familie                    Eltern: Punktekonto Lea
┌──────────────────────────┐         ┌──────────────────────────┐       ┌──────────────────────────┐
│ Ich                      │         │ Familie                  │       │ ← Punktekonto Lea        │
│ ┌──────────┐┌──────────┐ │         │ Punkte                   │       │ Gesammelt 42 · Guthaben 42│
│ │Gesammelt ││ Guthaben │ │         │ 1 (B) Ben        57      │       │ [ Punkte buchen ]        │
│ │   42     ││   42     │ │         │ 2 (L) Lea        42  ›   │       │ Verlauf                  │
│ └──────────┘└──────────┘ │         │ 3 (M) Mia        12      │       │ 25.10. Bad putzen  Aufg. +3│
│ Platz 2 von 3            │         │ Kochpunkte: 3   [Ändern] │       │ 24.10. Übernahme   Korr. +12│
│ Rangliste                │         │ Mitglieder …             │       └──────────────────────────┘
│ 1 (B) Ben            57  │         └──────────────────────────┘       Dialog „Punkte buchen“:
│ 2 (L) Lea ◀          42  │                                            (•Gutschreiben)(Abziehen)
│ 3 (M) Mia            12  │         Gericht-Editor (Kind, freigegeben,  Betrag [ 12 ]
│ Verlauf                  │         Kochtag ≤ heute):                   Begründung [Übernahme…] 17/200
│ 25.10. Bad putzen    +3  │         [ Als gekocht bestätigen ]          [Abbrechen] [Buchen]
│ 24.10. Spaghetti     +3  │         „Lea bekommt dafür Kochpunkte.“
│ 20.10. Übernahme …  +12  │         Wochenplan: „Gekocht ✓“ bzw. „Kochen bestätigen?“
├──────────────────────────┤
│ Heute Essen Aufgaben Ich │
└──────────────────────────┘
Offline: „Offline – Stand vom …“; Buchen/Ändern/Bestätigen ausgegraut bzw. Fehlermeldung.
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Leiste „Ich“ (Kind) | Platzhalter | Punktestand, Rangliste, Verlauf | offline aus lokalem Stand |
| „Familie“ (Eltern) | Mitglieder, Geräte | + Abschnitt „Punkte“ oben | Kind antippen → Punktekonto |
| Kochpunktwert | nur per API | Dialog „Kochpunkte“ (1–20) | nur Eltern |
| Startbuchung/Korrektur | nur per API | Punktekonto → „Punkte buchen“ | nur Eltern, nur online |
| Kochen bestätigen | – | Gericht-Editor → „Als gekocht bestätigen“ | Kind erhält Kochpunkte (Backend) |
| Wochenplan | – | „Gekocht ✓“ / „Kochen bestätigen?“ | |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/TasksModule.kt` | all | Koin: Retrofit-API, DataStore genau einmal, ViewModels mit Parametern |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/board/UseCases.kt` | 1-35 | `LoadBoardUseCase`: Remote + Cache-Fallback |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/data/DataStoreBoardCache.kt`, `BoardCacheSerializer.kt`, `StoredBoard.kt`, `Parsing.kt` | all | DataStore-Stand je Mitglied, Version, Fehler → leer |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/data/RemoteTaskRepository.kt`, `Mappers.kt` | all | `apiCall` mit generiertem Client, Import-Aliase |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/history/HistoryViewModel.kt`, `HistoryScreen.kt` | all | Einfacher State-ViewModel + Liste mit Args (Muster für Punktekonto) |
| P0 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/board/BoardViewModel.kt` | all | `isOffline`, `syncedAt`, `isBusy`/`actionError` |
| P0 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt`, `TopLevelDestination.kt`, `MeRoute.kt` | all | Platzhalter ersetzen, Slots, Navigation |
| P0 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt` (51-54), `ui/family/FamilyScreen.kt` (37-79) | | Slot `familyContent` ergänzen |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/dish/DishEditViewModel.kt` (Approve 290-330), `DishEditSections.kt` (`ReviewSection`), `DishEditScreen.kt` (60-80) | | Kochbestätigung analog zu Freigabe |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Models.kt` (Dish), `EditorRules.kt`, `data/plan/Mappers.kt`, `data/plan/StoredPlan.kt` (38-80) | | `cookedAt` durchreichen |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/CookingRepository.kt`, `UseCases.kt` (`ReviewDishUseCase`), `data/cooking/RemoteCookingRepository.kt` | all | `confirmCooked` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt` (328-349 `DishBadges`), `WeekPlanViewModel.kt` (30-90, `today`) | | Markierungen |
| P1 | `android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/ui/catalog/PointsStepper.kt` | all | Stepper (Kopie für Kochpunkte 1–20) |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/ReasonDialog.kt` | all | Dialog-Aufbau mit Zähler |
| P1 | `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/components/Components.kt`, `Formatting.kt`, `ErrorMessages.kt` | all | `SubmitButton`, `ErrorText`, `ConfirmDialog`, `formatDate`, `formatDateTime`, `message()` |
| P1 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt` | all | `apiCall`, `apiCallNoContent`, `validationFailure` |
| P1 | `android/app/build.gradle.kts` (30-45, 100-135), `android/settings.gradle.kts`, `FamilyPlannerApp.kt` | | Modul eintragen, Kover, Version |
| P2 | `api/openapi.yaml` | 678-694, 934-1024, Schemas 1963-2075 | Vertrag |
| P2 | `docs/03-design/gamification.md`, `docs/03-design/meals.md` (386-401) | all | Regeln, Grenzen, Fehlercodes |
| P2 | `android/feature/tasks/src/test/.../fakes/Fakes.kt`, `ui/TestSupport.kt`, `TasksModuleTest.kt`, `data/RemoteTaskRepositoryTest.kt`, `data/DataStoreBoardCacheTest.kt` | all | Test-Muster |
| P2 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` (200-240, 330-340) | | Koin-Testmodul, „Ich“-Test ersetzen |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Generierter Client | `android/core/api/build/generated/openapi/.../client/GamificationApi.kt` (geprüft 2026-10-25) | `getLeaderboard(): Response<LeaderboardResponse>`, `getPointsAccount(memberId, limit: Int? = 50): Response<PointsAccountResponse>`, `bookPoints(memberId, ManualEntryRequest(amount, reason)): Response<LedgerEntryResponse>` (201), `getGameSettings()`, `updateGameSettings(GameSettings(cookingPoints))`; `MealsApi.confirmDishCooked(dishId): Response<Unit>` (204) |
| Modelle | `de.familyplanner.api.model.LedgerEntry(id, kind: LedgerEntryKind, amount, title, bookedAt: String, note?)`, `PointsAccount(memberId, collected, balance, items)`, `LeaderboardEntry(memberId, name, rank, collected)`, `Leaderboard(items)`, `GameSettings(cookingPoints)` | Feld heißt `items` (nicht `entries`); `bookedAt`/`cookedAt` sind Strings → `parseInstant` |
| `LifecycleEventEffect` | `androidx.lifecycle:lifecycle-runtime-compose` 2.11.0 (im Projekt) | `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { onEvent(Refresh) }` |

No further external research needed — feature uses established internal patterns.

---

## Patterns to Mirror

### NAMING / MODUL
```kotlin
// SOURCE: android/feature/tasks/build.gradle.kts
plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}
android { namespace = "de.familyplanner.android.feature.tasks" }
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
Für `gamification` ohne `libs.work.runtime` (kein Worker). KDoc/Kommentare **deutsch**, Bezeichner englisch, Konstanten in `object PointsLimits`.
Ressourcen mit Präfix `game_` (Android mergt `R` der Module).

### REMOTE_REPOSITORY
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/RemoteCookingRepository.kt
override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> =
    apiCall(json, { meals.getWeekStatus(weekStart.toString()) }) { it.data?.toDomain() }

override suspend fun approve(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.approveDish(dishId) }
```

### CACHE_FALLBACK
```kotlin
// SOURCE: android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/domain/board/UseCases.kt:10-33
private fun AppError.allowsCachedBoard() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

suspend operator fun invoke(): BoardLoad {
    val memberId = sessions.current?.memberId ?: return BoardLoad.Failed(AppError.Unauthorized)
    return when (val result = repository.board()) {
        is ApiResult.Success -> {
            val board = BoardSnapshot(result.data, clock.instant())
            cache.write(memberId, board)
            BoardLoad.Fresh(board)
        }
        is ApiResult.Failure -> {
            val cached = if (result.error.allowsCachedBoard()) cache.read(memberId) else null
            if (cached != null) BoardLoad.Cached(cached, result.error) else BoardLoad.Failed(result.error)
        }
    }
}
```

### PARALLEL_LOAD
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/cooking/UseCases.kt:14-31
coroutineScope {
    val status = async { cooking.status(weekStart) }
    val members = async { plan.members() }
    val statusResult = status.await()
    val membersResult = members.await()
    if (statusResult !is ApiResult.Success) return@coroutineScope statusResult as ApiResult.Failure
    if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
    …
}
```

### DATASTORE
```kotlin
// SOURCE: android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/data/DataStoreBoardCache.kt:15-40
override suspend fun read(memberId: String): BoardSnapshot? =
    try {
        dataStore.data.first().toDomain()?.takeIf { it.memberId == memberId }?.board
    } catch (e: IOException) {
        Log.w(TAG, "Task board cache unreadable", e)
        null
    }
```
Serializer: leere Datei / andere `version` / `SerializationException` / `IllegalArgumentException` → `defaultValue` (siehe `BoardCacheSerializer.kt`).
Dateiformat getrennt von der Domäne (`@Serializable data class Stored…`), Zeitpunkte als Epoch-Millis.

### KOIN
```kotlin
// SOURCE: android/feature/tasks/src/main/kotlin/de/familyplanner/android/feature/tasks/TasksModule.kt:42-56
single { get<Retrofit>().create(TasksApi::class.java) }
single<TaskRepository> { RemoteTaskRepository(get(), get()) }
// Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
single<BoardCache> {
    DataStoreBoardCache(DataStoreFactory.create(serializer = BoardCacheSerializer(get()), produceFile = { androidContext().dataStoreFile(BOARD_CACHE_FILE) }))
}
viewModel { params -> HistoryViewModel(params.get(), get()) }
```

### VIEWMODEL (State/Event, Freigabe-Muster)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/dish/DishEditViewModel.kt:315-326
private fun approve() {
    val current = _state.value
    val id = dishId ?: return
    if (current.isBusy || current.mode != EditorMode.PARENT_REVIEW) return
    _state.update { it.copy(isBusy = true, error = null) }
    viewModelScope.launch {
        when (val result = dishes.review.approve(id)) {
            is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
            is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
        }
    }
}
```

### NAV_SLOT
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt:51-54
fun NavGraphBuilder.mainGraph(todayContent: @Composable () -> Unit = {}) {
    composable<HomeRoute> { HomeRoute(todayContent = todayContent) }
    composable<FamilyRoute> { FamilyRoute() }
}
```

### TEST_STRUCTURE
Fakes in `src/test/.../fakes/` (keine Mocks), `runTest`, `kotlin.test`, REQ-ID am Namensanfang, `Clock.fixed(…, BerlinZone)`,
Robolectric für Screens, `InMemorySessionStore` aus `:core:testing`, `MainDispatcherRule` für ViewModels,
`MockWebServer` für Remote-Repositories (`RemoteTaskRepositoryTest`), Koin-Verifikation wie `TasksModuleTest`.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/settings.gradle.kts` | UPDATE | `":feature:gamification"` |
| `android/feature/gamification/build.gradle.kts`, `src/main/AndroidManifest.xml` | CREATE | wie `feature:tasks` (ohne WorkManager) |
| `…/gamification/GamificationModule.kt` | CREATE | Koin |
| `…/gamification/GamificationNavigation.kt` | CREATE | `MeHomeRoute`, `ChildAccountRoute`, `gamificationGraph` |
| `…/gamification/domain/Models.kt` | CREATE | `EntryKind`, `LedgerEntry`, `PointsAccount`, `RankedChild`, `PointsSnapshot`, `PointsLoad` |
| `…/gamification/domain/PointsLimits.kt` | CREATE | Grenzen |
| `…/gamification/domain/Validation.kt` | CREATE | Buchung, Kochpunkte |
| `…/gamification/domain/Ports.kt` | CREATE | `PointsRepository`, `PointsCache` |
| `…/gamification/domain/UseCases.kt` | CREATE | Laden mit Cache, Konto, Buchen, Einstellungen |
| `…/gamification/data/RemotePointsRepository.kt`, `Mappers.kt`, `Parsing.kt` | CREATE | API |
| `…/gamification/data/DataStorePointsCache.kt`, `PointsCacheSerializer.kt`, `StoredPoints.kt` | CREATE | Lokaler Stand |
| `…/gamification/ui/Labels.kt` | CREATE | Art-Bezeichnungen, Betrag-Format |
| `…/gamification/ui/components/LeaderboardList.kt`, `LedgerList.kt`, `StatTiles.kt` | CREATE | gemeinsame Bausteine |
| `…/gamification/ui/me/MeViewModel.kt`, `MeScreen.kt` | CREATE | „Ich“ |
| `…/gamification/ui/family/FamilyPointsViewModel.kt`, `FamilyPointsSection.kt`, `CookingPointsDialog.kt`, `PointsStepper.kt` | CREATE | Abschnitt unter „Familie“ |
| `…/gamification/ui/account/ChildAccountViewModel.kt`, `ChildAccountScreen.kt`, `BookPointsDialog.kt` | CREATE | Punktekonto + Buchen |
| `android/feature/gamification/src/main/res/values/strings.xml` | CREATE | Texte (`game_…`), Plurals |
| `android/feature/gamification/src/test/…` | CREATE | siehe Testing Strategy |
| `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt` | UPDATE | `mainGraph(todayContent, familyContent)` |
| `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/family/FamilyScreen.kt` | UPDATE | Parameter `familyContent`, als `item` nach Titel/Fehler |
| `android/feature/meals/…/domain/plan/Models.kt` | UPDATE | `Dish.cookedAt: Instant? = null` |
| `android/feature/meals/…/domain/plan/EditorRules.kt` | UPDATE | `canConfirmCooking(...)` |
| `android/feature/meals/…/data/plan/Mappers.kt`, `StoredPlan.kt` | UPDATE | `cookedAt` mappen und speichern |
| `android/feature/meals/…/domain/cooking/CookingRepository.kt`, `UseCases.kt`, `data/cooking/RemoteCookingRepository.kt` | UPDATE | `confirmCooked(dishId)` |
| `android/feature/meals/…/ui/dish/DishEditViewModel.kt`, `DishEditSections.kt`, `DishEditScreen.kt` | UPDATE | Bestätigen-Button, Dialog, Anzeige |
| `android/feature/meals/…/ui/plan/WeekPlanScreen.kt` | UPDATE | `DishBadges` mit „Gekocht ✓“/„Kochen bestätigen?“ |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | neue Texte |
| `android/feature/meals/src/test/…` (`CookingFakes.kt`, betroffene Tests) | UPDATE | Fake-Methode, neue Tests |
| `android/feature/auth/src/test/…` (FamilyScreen-Test, falls vorhanden) | UPDATE | Slot-Parameter |
| `android/app/build.gradle.kts` | UPDATE | Abhängigkeit, `kover(project(":feature:gamification"))`, Version 0.10.0/11 |
| `android/app/src/main/kotlin/de/familyplanner/android/app/TopLevelDestination.kt` | UPDATE | `ME` → `MeHomeRoute` |
| `android/app/src/main/kotlin/de/familyplanner/android/app/MeRoute.kt` | DELETE | Platzhalter (`ComingSoonScreen` wird nur hier genutzt) |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | `gamificationGraph`, Familie-Slot, Platzhalter weg |
| `android/app/src/main/kotlin/de/familyplanner/android/app/FamilyPlannerApp.kt` | UPDATE | `gamificationModule` |
| `android/app/src/main/res/values/strings.xml` | UPDATE | `me_coming_soon` entfernen |
| `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt`, `MainDestinationsTest.kt` | UPDATE | Koin-Testmodul, neue Tests, Import `MeHomeRoute` |
| `docs/03-design/gamification.md` | UPDATE | „App-Seite (Phase 1b)“ |
| `docs/03-design/meals.md` | UPDATE | REQ-MEAL-034 App-Seite |
| `docs/04-verification/traceability.md` | UPDATE | REQ-GAME-010–014, REQ-MEAL-034 App-Tests |
| `android/README.md` | UPDATE | Abnahme-Checkliste GAME Phase 1 |
| `docs/01-requirements/gamification.prd.md` | UPDATE | Bericht-Verweis 1b |

## NOT Building

- Keine Backend-/API-Änderung (`git diff main -- api/ backend/` leer).
- Keine Badges, kein Avatar, kein Shop, keine Belohnungen/Sparziel (Phasen 2–4); Avatar-Platzhalter = Anfangsbuchstabe.
- Keine neuen Benachrichtigungen, kein Hintergrund-Abgleich, kein Badge an „Ich“/„Familie“.
- Keine Punkte-Karte auf „Heute“ (kommt mit Sparziel, REQ-GAME-041).
- Keine Rangliste je Woche/Monat (Could).
- Keine Offline-Warteschlange für Buchungen/Bestätigungen.
- Keine Kochbestätigung direkt aus dem Wochenplan oder von „Heute“ (nur Editor).
- Kein Zurücknehmen einer Kochbestätigung (API hat keinen Endpunkt; Korrektur über manuelle Buchung).
- Kein „Mehr laden“ im Verlauf (fest 50).

---

## Step-by-Step Tasks

### Task 1: Modul anlegen
- **ACTION**: `feature:gamification` als Gradle-Modul anlegen und eintragen.
- **IMPLEMENT**: `build.gradle.kts` = Kopie von `feature/tasks/build.gradle.kts`, Namespace `de.familyplanner.android.feature.gamification`, **ohne** `libs.work.runtime`.
  `src/main/AndroidManifest.xml` wie `feature/tasks/src/main/AndroidManifest.xml`. `settings.gradle.kts`: `":feature:gamification"` an `include(...)` anhängen.
  `app/build.gradle.kts`: `implementation(project(":feature:gamification"))`, `kover(project(":feature:gamification"))`, `versionCode = 11`, `versionName = "0.10.0"`.
- **MIRROR**: NAMING / MODUL.
- **GOTCHA**: Kover-Filter schließen `*Route*` und `*Module*` aus → Routen-Composables `…Route` nennen, Screens separat und testbar halten.
- **VALIDATE**: `cd android && ./gradlew :feature:gamification:assembleDebug`.

### Task 2: Domäne (rein)
- **ACTION**: Modelle, Grenzen, Validierung, Ports.
- **IMPLEMENT**:
  - `domain/Models.kt`:
    ```kotlin
    enum class EntryKind { TASK, COOKING, MANUAL, SHOP, REWARD, REWARD_REFUND }
    data class LedgerEntry(val id: String, val kind: EntryKind, val amount: Int, val title: String, val note: String?, val bookedAt: Instant)
    data class PointsAccount(val memberId: String, val collected: Int, val balance: Int, val entries: List<LedgerEntry>)
    data class RankedChild(val memberId: String, val name: String, val rank: Int, val collected: Int)
    /** Stand vom Server oder aus dem lokalen Stand (ADR-0011); [account] nur für Kinder (eigenes Konto). */
    data class PointsSnapshot(val account: PointsAccount?, val leaderboard: List<RankedChild>, val syncedAt: Instant)
    sealed interface PointsLoad {
        data class Fresh(val snapshot: PointsSnapshot) : PointsLoad
        data class Cached(val snapshot: PointsSnapshot, val error: AppError) : PointsLoad
        data class Failed(val error: AppError) : PointsLoad
    }
    /** Platz des Kindes in der Rangliste; `null`, wenn es fehlt. */
    fun List<RankedChild>.rankOf(memberId: String): RankedChild? = firstOrNull { it.memberId == memberId }
    ```
    (`RankedChild` statt `LeaderboardEntry`, damit kein Namenskonflikt mit dem generierten Modell entsteht.)
  - `domain/PointsLimits.kt`: `AMOUNT_MAX = 1000`, `REASON_MAX = 200`, `COOKING_POINTS_MIN = 1`, `COOKING_POINTS_MAX = 20`, `HISTORY_LIMIT = 50`.
  - `domain/Validation.kt`:
    ```kotlin
    /** [amount] mit Vorzeichen; Server: −1000…1000, ≠ 0 (REQ-GAME-013). */
    fun validateManualEntry(amount: Int, reason: String): AppError.Validation?
    //   amount == 0 || |amount| > 1000 → "Betrag muss zwischen 1 und 1000 liegen"
    //   reason.trim() leer oder > 200 → "Begründung muss zwischen 1 und 200 Zeichen lang sein"
    fun validateCookingPoints(points: Int): AppError.Validation?   // "Kochpunkte müssen zwischen 1 und 20 liegen"
    ```
    `AppError.Validation`-Konstruktion wie in `feature/tasks/.../domain/catalog/Validation.kt`.
  - `domain/Ports.kt`:
    ```kotlin
    interface PointsRepository {
        suspend fun leaderboard(): ApiResult<List<RankedChild>>
        suspend fun account(memberId: String, limit: Int): ApiResult<PointsAccount>
        suspend fun book(memberId: String, amount: Int, reason: String): ApiResult<LedgerEntry>
        suspend fun cookingPoints(): ApiResult<Int>
        suspend fun updateCookingPoints(points: Int): ApiResult<Int>
    }
    interface PointsCache {
        suspend fun read(memberId: String): PointsSnapshot?
        suspend fun write(memberId: String, snapshot: PointsSnapshot)
        suspend fun clear()
    }
    ```
- **VALIDATE**: `ValidationTest`, `ModelsTest` (`rankOf`).

### Task 3: Daten (Remote + lokaler Stand)
- **ACTION**: Remote-Repository, Mapper, DataStore.
- **IMPLEMENT**:
  - `data/Parsing.kt`: Kopie von `feature/tasks/.../data/Parsing.kt` (`parseInstant`, `mapAllOrNull`) — Module kennen sich nicht.
  - `data/Mappers.kt` (Import-Aliase `import de.familyplanner.api.model.LedgerEntry as ApiLedgerEntry` usw.):
    `ApiLedgerEntry.toDomain(): LedgerEntry?` (`bookedAt` unlesbar → `null`; Art per `EntryKind.entries.firstOrNull { it.name == kind.name }`, unbekannt → `null`),
    `ApiPointsAccount.toDomain()` (`items.mapAllOrNull { it.toDomain() }` → bei `null` gesamtes Konto `null` = Serverfehler),
    `ApiLeaderboardEntry.toDomain() = RankedChild(memberId, name, rank, collected)`.
  - `data/RemotePointsRepository(api: GamificationApi, json: Json)`:
    `leaderboard()` → `apiCall(json, { api.getLeaderboard() }) { it.data?.items?.map { e -> e.toDomain() } }`;
    `account(memberId, limit)` → `getPointsAccount(memberId, limit)`; `book` → `bookPoints(memberId, ManualEntryRequest(amount, reason))` → `it.data?.toDomain()`;
    `cookingPoints()` → `getGameSettings()` → `it.data?.cookingPoints`; `updateCookingPoints(p)` → `updateGameSettings(GameSettings(p))`.
  - `data/StoredPoints.kt`: `@Serializable data class StoredPoints(val version: Int = CACHE_VERSION, val memberId: String? = null, val syncedAtMillis: Long = 0, val account: StoredAccount? = null, val leaderboard: List<StoredRankedChild> = emptyList())`,
    `StoredAccount(memberId, collected, balance, entries: List<StoredEntry>)`, `StoredEntry(id, kind: String, amount, title, note, bookedAtMillis)`, `CACHE_VERSION = 1`;
    `toStored()`/`toDomain()` (unbekannte Art → ganzer Stand `null`).
  - `data/PointsCacheSerializer(json)` wie `BoardCacheSerializer`; `data/DataStorePointsCache(dataStore)` wie `DataStoreBoardCache` (`read` nur bei passender `memberId`, `clear()` schreibt `StoredPoints()`), `TAG = "DataStorePointsCache"`.
- **MIRROR**: REMOTE_REPOSITORY, DATASTORE.
- **GOTCHA**: `bookPoints` antwortet **201** mit Body — `apiCall` (nicht `apiCallNoContent`). `ApiCall.kt` prüfen, dass 201 als Erfolg gilt (`response.isSuccessful`).
- **VALIDATE**: `RemotePointsRepositoryTest` (MockWebServer: Pfade, Methoden, Query `limit=50`, Body `{"amount":-2,"reason":"…"}`, 403 → `AppError.Forbidden`), `DataStorePointsCacheTest`.

### Task 4: Use Cases
- **ACTION**: `domain/UseCases.kt`.
- **IMPLEMENT**:
  - `LoadPointsUseCase(repository, cache, sessions, clock = Clock.system(BerlinZone))`: `suspend operator fun invoke(): PointsLoad`.
    Session fehlt → `Failed(Unauthorized)`. Kind: `account(session.memberId, HISTORY_LIMIT)` und `leaderboard()` parallel (PARALLEL_LOAD);
    Eltern: nur `leaderboard()`, `account = null`. Beide erfolgreich → Snapshot schreiben, `Fresh`; Fehler → wie CACHE_FALLBACK (`allowsCachedPoints()` = Network/Certificate/Server).
  - `LoadChildAccountUseCase(repository)`: `invoke(memberId) = repository.account(memberId, PointsLimits.HISTORY_LIMIT)` (Eltern, ohne Cache).
  - `BookPointsUseCase(repository)`: `invoke(memberId, amount, reason)`: `validateManualEntry` → `book(memberId, amount, reason.trim())`.
  - `CookingPointsUseCases(load: suspend () -> …)` — einfacher: `class CookingPointsUseCase(repository) { suspend fun load(); suspend fun save(points) }` (Validierung vor `updateCookingPoints`).
- **MIRROR**: CACHE_FALLBACK, PARALLEL_LOAD.
- **GOTCHA**: Bei Fehler von **einem** der beiden parallelen Aufrufe zählt der erste Fehler (Konto vor Rangliste); ein halber Stand wird nicht gespeichert.
- **VALIDATE**: `UseCasesTest`.

### Task 5: Gemeinsame UI-Bausteine
- **ACTION**: `ui/Labels.kt`, `ui/components/*`.
- **IMPLEMENT**:
  - `Labels.kt`: `@StringRes fun EntryKind.labelRes()` → „Aufgabe“, „Kochen“, „Korrektur“, „Shop“, „Belohnung“, „Rückbuchung“; `fun formatAmount(amount: Int): String` → `"+3"` / `"−2"` (U+2212).
  - `StatTiles(collected, balance)`: zwei `Card`s nebeneinander (`Row` + `weight(1f)`), Zahl `displaySmall`, Beschriftung `labelLarge`.
  - `LeaderboardList(entries: List<RankedChild>, highlightId: String?, onClick: ((RankedChild) -> Unit)?)`: **`Column`** (kein `LazyColumn`, wird in Listen eingebettet), Zeile: Rang, Kreis (40 dp, `primaryContainer`) mit Anfangsbuchstaben, Name (`fontWeight = Bold` bei Hervorhebung), Punkte; mit `onClick` klickbar + Chevron; leer → „Noch keine Kinder in der Familie“.
  - `LedgerList(entries)`: `Column`, Zeile Datum (`formatDate(bookedAt.atZone(BerlinZone).toLocalDate())`), Titel (`maxLines = 1`, Ellipsis), Art klein, Betrag rechts (positiv `primary`, negativ `error`); `note` in zweiter Zeile, falls vorhanden; leer → „Noch keine Punkte – erledige eine Aufgabe oder koche.“
- **VALIDATE**: `ComponentsTest` (Robolectric): Hervorhebung, Klick, Betrag-Format, leere Texte.

### Task 6: „Ich“ (Kind)
- **ACTION**: `MeViewModel`, `MeScreen`, Route.
- **IMPLEMENT**:
  - `MeUiState(memberId: String?, snapshot: PointsSnapshot?, isLoading = true, isOffline = false, error: AppError? = null)` mit `myRank get() = snapshot?.leaderboard?.rankOf(memberId)`.
  - Events: `Refresh`. `init` lädt; `Fresh` → `isOffline = false`; `Cached` → `isOffline = true`; `Failed` → `error`, alter `snapshot` bleibt.
    Späte Antwort eines älteren Ladevorgangs verwerfen (laufenden `Job` abbrechen, wie in `BoardViewModel`).
  - `MeRoute(viewModel = koinViewModel())` mit `LifecycleEventEffect(ON_RESUME) { onEvent(Refresh) }`; `MeScreen(state, onEvent)`: `PullToRefreshBox` + `LazyColumn`: Titel „Ich“, Offline-Hinweis „Offline – Stand vom {formatDateTime(syncedAt)}“, `ErrorText`, `StatTiles`, „Platz {rank} von {n}“, Überschrift „Rangliste“ + `LeaderboardList(highlightId = memberId, onClick = null)`, Überschrift „Verlauf“ + `LedgerList`.
- **MIRROR**: VIEWMODEL, Screen-Aufbau wie `FamilyScreen` (PullToRefreshBox + LazyColumn, `padding(horizontal = 24.dp)`).
- **GOTCHA**: Die `init`-Ladung und das erste `ON_RESUME` laden doppelt → in `Refresh` nicht neu laden, solange `isLoading` (oder `init` weglassen und nur `ON_RESUME` nutzen; Tests rufen dann `onEvent(Refresh)` explizit). Entscheidung: **nur `ON_RESUME`/`Refresh`**, kein Laden im `init`.
- **VALIDATE**: `MeViewModelTest`, `MeScreenTest`.

### Task 7: Eltern — Abschnitt „Punkte“ unter „Familie“
- **ACTION**: `FamilyPointsViewModel`, `FamilyPointsSection`, `CookingPointsDialog`, `PointsStepper`, Slot in `feature:auth`.
- **IMPLEMENT**:
  - `FamilyPointsUiState(snapshot, cookingPoints: Int?, isOffline, isLoading, error, editingCookingPoints: Int? /* Dialog offen */, isBusy, actionError)`.
    Events: `Refresh` (lädt `LoadPointsUseCase` + `CookingPointsUseCase.load()` parallel; Einstellungsfehler setzt nur `cookingPoints = null`), `EditCookingPoints`, `CookingPointsChanged(n)`, `SaveCookingPoints`, `DismissDialog`.
  - `FamilyPointsSection(state, onEvent, onOpenChild: (memberId, name) -> Unit)`: `Column`: Überschrift „Punkte“ (`titleMedium`), Offline-Hinweis, `ErrorText`, `LeaderboardList(onClick = { onOpenChild(it.memberId, it.name) })` (offline ohne Klick), Zeile „Kochpunkte: n“ + `TextButton` „Ändern“ (offline deaktiviert); `HorizontalDivider()`.
  - `FamilyPointsRoute(onOpenChild)` mit `LifecycleEventEffect(ON_RESUME)`.
  - `CookingPointsDialog(value, isBusy, error, onChange, onSave, onDismiss)`: `AlertDialog` mit `PointsStepper` (Kopie aus `feature/tasks/.../ui/catalog/PointsStepper.kt`, Grenzen als Parameter `min`/`max`) und Hinweis „Gilt für künftige Bestätigungen.“ (REQ-GAME-011).
  - `feature:auth`: `fun NavGraphBuilder.mainGraph(todayContent: @Composable () -> Unit = {}, familyContent: @Composable () -> Unit = {})`, `FamilyRoute(familyContent = familyContent)`,
    `FamilyScreen(state, onEvent, onBack, familyContent: @Composable () -> Unit = {})` → `item { familyContent() }` direkt nach `item { ErrorText(state.error) }`.
- **MIRROR**: NAV_SLOT, VIEWMODEL.
- **GOTCHA**: `FamilyScreen` hat eigenes Pull-to-Refresh (nur Familie). Der Punkte-Abschnitt lädt bei `ON_RESUME` — kein gemeinsames Refresh nötig. `feature:auth` darf `feature:gamification` **nicht** kennen; die Verbindung macht nur `app`.
- **VALIDATE**: `FamilyPointsViewModelTest`, `FamilyPointsSectionTest`; bestehende `feature:auth`-Tests bleiben grün.

### Task 8: Eltern — Punktekonto eines Kindes + Buchen
- **ACTION**: `ChildAccountViewModel`, `ChildAccountScreen`, `BookPointsDialog`, Navigation.
- **IMPLEMENT**:
  - `ChildAccountArgs(memberId: String, name: String)`; `ChildAccountUiState(args, account: PointsAccount?, isLoading, error, booking: BookingDraft?, isBusy, actionError)`;
    `data class BookingDraft(val credit: Boolean = true, val amountText: String = "", val reason: String = "")` mit `amount: Int? get() = amountText.toIntOrNull()?.let { if (credit) it else -it }` und `canSubmit` (1–1000, Begründung 1–200 nach `trim`).
  - Events: `Refresh`, `StartBooking`, `BookingCreditChanged(Boolean)`, `BookingAmountChanged(String)` (nur Ziffern, max. 4 Stellen), `BookingReasonChanged(String)` (auf 200 kürzen), `SubmitBooking`, `DismissBooking`.
    Erfolg → Dialog zu, Konto neu laden. Fehler `Validation` → im Dialog anzeigen, andere → `actionError`, Dialog zu.
  - Screen: `TopAppBar`-freie Kopfzeile wie `HistoryScreen` (Zurück + Titel „Punktekonto {name}“), `StatTiles`, `SubmitButton` „Punkte buchen“ (`enabled = account != null`), „Verlauf“ + `LedgerList`; `ErrorText`.
  - `BookPointsDialog(draft, isBusy, error, onEvent)`: zwei `FilterChip` „Gutschreiben“/„Abziehen“, `OutlinedTextField` Betrag (`KeyboardType.Number`), `OutlinedTextField` Begründung mit `supportingText` „n/200“ und `placeholder` „z. B. Übernahme Küchenboard“, Bestätigen „Buchen“ (`enabled = canSubmit && !isBusy`).
  - `GamificationNavigation.kt`:
    ```kotlin
    /** Hauptbereich „Ich“ (Kinder, ADR-0014). */
    @Serializable data object MeHomeRoute
    @Serializable data class ChildAccountRoute(val memberId: String, val name: String)
    fun NavGraphBuilder.gamificationGraph(navController: NavHostController) {
        composable<MeHomeRoute> { MeRoute() }
        composable<ChildAccountRoute> { entry ->
            val route = entry.toRoute<ChildAccountRoute>()
            ChildAccountRoute(ChildAccountArgs(route.memberId, route.name), onBack = { navController.popBackStack() })
        }
    }
    fun NavHostController.openChildAccount(memberId: String, name: String) = navigate(ChildAccountRoute(memberId, name))
    ```
    Composable-Namen: `MeRoute`, `ChildAccountRoute` (Funktion) kollidieren mit Klassen → Funktionen im Paket `ui.me`/`ui.account` belassen und in der Navigation importieren (wie `HistoryRoute` in `feature:tasks`).
- **MIRROR**: `HistoryViewModel`/`HistoryScreen`, `ReasonDialog`.
- **VALIDATE**: `ChildAccountViewModelTest`, `ChildAccountScreenTest`.

### Task 9: Koin-Modul
- **ACTION**: `GamificationModule.kt`.
- **IMPLEMENT**:
  ```kotlin
  private const val POINTS_CACHE_FILE = "game_points.json"
  val gamificationModule = module {
      single { get<Retrofit>().create(GamificationApi::class.java) }
      single<PointsRepository> { RemotePointsRepository(get(), get()) }
      // Genau eine DataStore-Instanz je Datei und Prozess, sonst IllegalStateException.
      single<PointsCache> { DataStorePointsCache(DataStoreFactory.create(serializer = PointsCacheSerializer(get()), produceFile = { androidContext().dataStoreFile(POINTS_CACHE_FILE) })) }
      factory { LoadPointsUseCase(get(), get(), get()) }
      factory { LoadChildAccountUseCase(get()) }
      factory { BookPointsUseCase(get()) }
      factory { CookingPointsUseCase(get()) }
      viewModel { MeViewModel(get(), get()) }
      viewModel { FamilyPointsViewModel(get(), get()) }
      viewModel { params -> ChildAccountViewModel(params.get(), get(), get()) }
  }
  ```
- **GOTCHA**: Beim Abmelden (`Unauthorized`) leert `feature:tasks` seinen Stand im Abgleich; hier gibt es keinen Abgleich → `read(memberId)` filtert das Mitglied, das reicht (anderes Gerät/Mitglied sieht nie fremde Daten). `clear()` trotzdem implementieren (Test), Aufruf bei `Failed(Unauthorized)` im `LoadPointsUseCase`.
- **VALIDATE**: `GamificationModuleTest` (`verify()` wie `TasksModuleTest`).

### Task 10: Kochbestätigung in `feature:meals` (REQ-MEAL-034 App)
- **ACTION**: `cookedAt` durchreichen, Bestätigen im Editor, Markierung im Plan.
- **IMPLEMENT**:
  - `Dish`: `/** Von einem Elternteil als gekocht bestätigt (REQ-MEAL-034). */ val cookedAt: Instant? = null` als **letzter** Parameter.
  - `data/plan/Mappers.kt`: `cookedAt = cookedAt?.let(::parseInstant)` (Meals-`parseInstant(value: String)` existiert in `data/Parsing.kt`), benannte Argumente.
  - `StoredDish`: `val cookedAtMillis: Long? = null` (Default → alte Cache-Dateien bleiben lesbar, keine Versionsänderung); `toStored()`/`toDomain()` ergänzen.
  - `EditorRules.kt`:
    ```kotlin
    /** Eltern bestätigen ein freigegebenes Kindergericht am oder nach dem Kochtag (REQ-MEAL-034). */
    fun canConfirmCooking(isParent: Boolean, dish: Dish?, cookIsChild: Boolean, today: LocalDate): Boolean =
        isParent && cookIsChild && dish != null && dish.status == DishStatus.APPROVED && dish.cookedAt == null && !dish.date.isAfter(today)
    ```
  - `CookingRepository.confirmCooked(dishId): ApiResult<Unit>`; `RemoteCookingRepository`: `apiCallNoContent(json) { meals.confirmDishCooked(dishId) }`;
    `ReviewDishUseCase.confirmCooked(dishId) = cooking.confirmCooked(dishId)`.
  - `DishEditUiState`: `canConfirmCooking: Boolean = false`, `showCookedConfirm: Boolean = false`; beim Laden aus `canConfirmCooking(isParent, existing, child != null, today)` setzen (`today = clock.instant().atZone(BerlinZone).toLocalDate()`).
    Events `RequestConfirmCooked`, `CancelConfirmCooked`, `ConfirmCooked` → `confirmCooked()` nach VIEWMODEL-Muster (Guard `!isBusy && canConfirmCooking`); Erfolg → `isDone = true`; Fehler → `error` (409-Text vom Server, z. B. „schon bestätigt“), Dialog zu.
  - `DishEditSections.kt`: `CookedSection(state, onEvent)`: `existing.cookedAt != null` → Text „Gekocht bestätigt am {formatDateTime}“; sonst bei `canConfirmCooking` → `Button` „Als gekocht bestätigen“ + Text „{Name} bekommt dafür Kochpunkte.“ (`bodySmall`). In `DishEditScreen` oberhalb der Felder einhängen (nur Eltern); `ConfirmDialog` bei `showCookedConfirm` („Gekocht bestätigen?“, „{Name} bekommt dafür Kochpunkte. Das lässt sich nicht zurücknehmen.“, „Bestätigen“).
  - `WeekPlanScreen.DishBadges(dish, isParent, today, isChildCook)`: zusätzlich „Gekocht ✓“ (`cookedAt != null`) und für Eltern „Kochen bestätigen?“ wenn `canConfirmCooking(...)`. `isChildCook`: Koch ist kein Elternteil — aus vorhandenem State ableiten (Kochname/`cookName` bzw. Kinderliste im `WeekPlanUiState`; vor dem Umsetzen prüfen, welches Feld es gibt, und das vorhandene nutzen).
  - Strings (`feature/meals/.../values/strings.xml`): `dish_cooked_confirm`, `dish_cooked_hint`, `dish_cooked_at`, `dish_cooked_dialog_title`, `dish_cooked_dialog_text`, `plan_badge_cooked`, `plan_badge_confirm_cooking`.
  - `FakeCookingRepository`: `confirmCooked` mit Aufrufliste und konfigurierbarem Ergebnis.
- **MIRROR**: VIEWMODEL (Freigabe-Muster), `ReviewSection`.
- **GOTCHA**: `Dish(...)` wird an mehreren Stellen positionell gebaut (`Mappers.kt:25`, `StoredPlan.kt:77`, Tests) — Parameter **am Ende** mit Default anhängen, dann bleiben positionelle Aufrufe gültig. Der Editor im `PARENT_EDIT`-Modus für vergangene Tage: Speichern kann der Server ablehnen — nicht Teil dieser Phase.
- **VALIDATE**: `EditorRulesTest` (neue Fälle), `DishEditViewModelTest` (Bestätigen), `RemoteCookingRepositoryTest` (Pfad `POST /api/v1/meals/dishes/{id}/cooking-confirmation`, 204, 409 → `Conflict(message)`), `RemoteMealPlanRepositoryTest`/`DataStorePlanCacheTest` (`cookedAt`-Rundreise, alte Datei ohne Feld).

### Task 11: App-Verdrahtung
- **ACTION**: `app` anpassen.
- **IMPLEMENT**:
  - `TopLevelDestination.ME(MeHomeRoute, …)` (Import `de.familyplanner.android.feature.gamification.MeHomeRoute`); `MeRoute.kt` löschen; `me_coming_soon` aus `strings.xml` entfernen.
  - `AppNavHost.MainHost`: `mainGraph(todayContent = {…}, familyContent = { FamilyPointsRoute(onOpenChild = { id, name -> navController.openChildAccount(id, name) }) })`, `gamificationGraph(navController)`, `composable<MeRoute>`-Block entfernen.
  - `FamilyPlannerApp`: `modules(appModule, sessionModule, networkModule, authModule, mealsModule, tasksModule, gamificationModule)`.
  - `AppNavHostTest`: Koin-Testmodul um `MeViewModel`, `FamilyPointsViewModel` (mit `FakePointsRepository`/`FakePointsCache` aus Test-Fakes — **Kopie** im `app`-Test, da Test-Fakes anderer Module nicht sichtbar sind; vorhandenes Vorgehen in `AppNavHostTest` prüfen und gleich verfahren) ergänzen;
    `REQ-TASK-001 me shows coming soon` ersetzen durch `REQ-GAME-012 me shows collected points and balance`; neu `REQ-GAME-014 parent sees the leaderboard under family`, `REQ-GAME-013 tapping a child opens its points account`.
  - `MainDestinationsTest`: Import/Erwartung `MeHomeRoute`.
- **MIRROR**: INBOX/Slot-Muster in `AppNavHost`.
- **VALIDATE**: `./gradlew :app:testDebugUnitTest`.

### Task 12: Doku, Traceability, Abnahme-Checkliste
- **ACTION**: Abschluss.
- **IMPLEMENT**:
  - `docs/03-design/gamification.md`: Abschnitt „## App-Seite (Phase 1b)“ — Bildschirme („Ich“, Familie → Punkte, Punktekonto), Sichtbarkeit (Kind nur eigenes Konto; Eltern alle), lokaler Stand (Datei `game_points.json`, je Mitglied, Version 1), Grenzen, Unit-Testfälle.
  - `docs/03-design/meals.md` unter „Kochbestätigung (REQ-MEAL-034)“: Absatz „App“ (Editor-Button, Bedingungen = `canConfirmCooking`, Markierungen, Cache-Feld `cookedAtMillis`).
  - `docs/04-verification/traceability.md`: REQ-GAME-010–014 und REQ-MEAL-034 um App-Tests ergänzen, Status „umgesetzt (App)“.
  - `android/README.md`: „## Abnahme-Checkliste GAME Phase 1 (manuell, Testhandys gegen die Testinstanz)“ = Manual Validation unten.
  - PRD Phase 1, Spalte PRP Plan: `1b: .claude/PRPs/plans/punktekonto-app.plan.md` → nach Umsetzung Bericht-Verweis; Status bleibt `in-progress` bis zur Geräte-Abnahme.
- **VALIDATE**: Full Test Suite.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-GAME-013 manual entry needs a non-zero amount up to 1000` (`ValidationTest`) | 0 / 1001 / −1001 / −2 / 1000 | Validation / Validation / Validation / ok / ok | ja |
| `REQ-GAME-013 manual entry needs a reason of 1 to 200 characters` | `"  "` / 201 Zeichen / `" ok "` | Validation / Validation / ok | ja |
| `REQ-GAME-011 cooking points must be between 1 and 20` | 0 / 21 / 3 | Validation / Validation / ok | ja |
| `REQ-GAME-014 rankOf finds the child or null` (`ModelsTest`) | | | ja |
| `REQ-GAME-012 child loads own account and leaderboard and stores them` (`UseCasesTest`) | Kind c-1 | `Fresh`, Konto c-1 angefragt, Stand geschrieben | |
| `REQ-GAME-014 parent loads only the leaderboard` | Eltern | `account == null`, kein Kontoaufruf | |
| `REQ-GAME-012 offline shows the cached snapshot` | `Network`, Stand vorhanden | `Cached` | ja |
| `REQ-GAME-012 cached snapshot of another member is not used` | Stand p-1, Session c-1 | `Failed` | ja |
| `REQ-GAME-012 a failed leaderboard does not store half a snapshot` | Konto ok, Rangliste `Server` | kein `write` | ja |
| `REQ-GAME-012 unauthorized clears the local snapshot` | `Unauthorized` | `clear()`, `Failed` | ja |
| `REQ-GAME-013 booking trims the reason and sends the signed amount` | −2, `" Fehler "` | `book(c-1, -2, "Fehler")` | |
| `REQ-GAME-013 invalid booking is not sent` | 0 | Validation, kein Aufruf | ja |
| `REQ-GAME-011 saving cooking points validates before sending` | 21 | Validation, kein Aufruf | ja |
| `REQ-GAME-012 me view model shows totals, rank and history` (`MeViewModelTest`) | Fresh | `myRank`, Einträge | |
| `REQ-GAME-012 me view model marks a cached snapshot as offline` | Cached | `isOffline`, `syncedAt` | ja |
| `REQ-GAME-012 failed refresh keeps the previous snapshot` | erst Fresh, dann `Network` ohne Stand | Snapshot bleibt, `error` | ja |
| `REQ-GAME-011 parent changes cooking points` (`FamilyPointsViewModelTest`) | Edit → 5 → Save | `updateCookingPoints(5)`, Dialog zu, Wert 5 | |
| `REQ-GAME-011 cooking points stay hidden when settings fail` | Einstellungen `Server` | `cookingPoints == null`, Rangliste da | ja |
| `REQ-GAME-013 child account books a debit and reloads` (`ChildAccountViewModelTest`) | Abziehen, „2“, „Fehler“ | `book(id, -2, …)`, Konto neu geladen | |
| `REQ-GAME-013 booking draft accepts digits only and limits the reason` | „1a2“, 250 Zeichen | „12“, 200 Zeichen | ja |
| `REQ-GAME-013 server validation keeps the dialog open` | `Validation` | `booking != null`, Fehler im Dialog | ja |
| `REQ-MEAL-034 parent can confirm an approved child dish on or after the cooking day` (`EditorRulesTest`) | heute / gestern / morgen | true / true / false | ja |
| `REQ-MEAL-034 confirmation is not offered for parents' dishes, pending or cooked dishes, or children` | | false | ja |
| `REQ-MEAL-034 confirming cooking closes the editor` (`DishEditViewModelTest`) | Request → Confirm | `confirmCooked(id)`, `isDone` | |
| `REQ-MEAL-034 conflict shows the server message` | `Conflict("Schon bestätigt")` | `error`, nicht `isDone` | ja |

### Integration Tests (Robolectric / MockWebServer / DataStore)

| Test | Expected |
|---|---|
| `RemotePointsRepositoryTest`: alle 5 Aufrufe (Pfade, Methoden, `limit=50`, Body mit negativem Betrag, `201`), unbekannte Art / unlesbares `bookedAt` → `AppError.Server`, 403 → `Forbidden` | wie Vertrag |
| `DataStorePointsCacheTest`: Rundreise (Kind mit Konto, Eltern ohne), anderes Mitglied → `null`, kaputte Datei/alte Version → leer, `clear()` | |
| `RemoteCookingRepositoryTest` (meals): `confirmCooked` 204 / 409 | |
| `RemoteMealPlanRepositoryTest` / `DataStorePlanCacheTest` (meals): `cookedAt` gemappt und gespeichert; Datei ohne `cookedAtMillis` lesbar | |
| `ComponentsTest`: Rangliste hervorgehoben/klickbar, Betrag „+3“/„−2“, leere Texte | |
| `MeScreenTest`: Kacheln „Gesammelt 42“/„Guthaben 42“, „Platz 2 von 3“, Verlauf-Zeile, Offline-Hinweis | |
| `FamilyPointsSectionTest`: Rangliste, Klick → `onOpenChild`, „Kochpunkte: 3“, Dialog-Stepper-Grenzen, offline kein Klick | |
| `ChildAccountScreenTest`: „Punkte buchen“ öffnet Dialog, „Buchen“ erst bei gültiger Eingabe aktiv, Zähler „n/200“ | |
| `DishEditScreenTest` (meals, bestehende Datei erweitern): Button „Als gekocht bestätigen“ nur bei `canConfirmCooking`; „Gekocht bestätigt am …“ | |
| `WeekPlanScreenTest` (meals, falls vorhanden): „Gekocht ✓“ / „Kochen bestätigen?“ | |
| `GamificationModuleTest`: Koin-Graph auflösbar | |
| `AppNavHostTest`: „Ich“ zeigt Punkte; Familie zeigt Rangliste; Klick öffnet Punktekonto ohne Leiste | |

### Edge Cases Checklist
- [x] Kind ohne Buchungen (0/0, Verlauf-Leertext), Familie ohne Kinder (Rangliste-Leertext)
- [x] Negative Korrektur → Guthaben/Gesammelt negativ (Anzeige „−2“ ohne Absturz)
- [x] Offline mit/ohne Stand; Stand eines anderen Mitglieds
- [x] Halber Ladeerfolg (Konto ok, Rangliste nicht)
- [x] 401 → Stand leeren
- [x] Doppeltippen auf „Buchen“/„Bestätigen“ (`isBusy`)
- [x] Kochbestätigung zu früh / schon bestätigt / anderes Elternteil war schneller (409)
- [x] Alte Plan-Cache-Datei ohne `cookedAtMillis`

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Funde

### Unit Tests
```bash
cd android && ./gradlew :feature:gamification:testDebugUnitTest :feature:meals:testDebugUnitTest :feature:auth:testDebugUnitTest :app:testDebugUnitTest
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
EXPECT: `de.familyplanner.android.test`, 0.10.0-test

### Database Validation
N/A — keine Backend-/Schemaänderung. Gegenprobe: `git diff main -- api/ backend/` ist leer.

### Manual Validation (Abnahme GAME Phase 1 – zwei Testhandys gegen die Testinstanz)
- [ ] Testinstanz läuft mit Backend aus Phase 1a (Migrationen V9/V10); Test-Build 0.10.0-test auf Eltern- und Kind-Testhandy (Update über 0.9.0-test)
- [ ] Kind: „Ich“ zeigt Gesammelt/Guthaben (ggf. nachgebuchte alte Abnahmen), Platz, Rangliste, Verlauf
- [ ] Eltern: „Familie“ → Abschnitt „Punkte“ mit Rangliste; Testkind antippen → Punktekonto
- [ ] Eltern: „Punkte buchen“ → Gutschreiben 12 „Übernahme Küchenboard“ → Stand +12; Kind sieht nach Öffnen von „Ich“ +12 im Verlauf (Art „Korrektur“)
- [ ] Eltern: Abziehen 2 mit Begründung → Gesammelt und Guthaben −2
- [ ] Eltern nimmt eine Karte mit 2 Punkten ab → Kind: „Ich“ zeigt +2 („Aufgabe“) — kein Doppel nach erneutem Öffnen
- [ ] Eltern: „Kochpunkte: 3 · Ändern“ → 4 speichern
- [ ] Eltern: freigegebenes Kindergericht von heute/gestern im Editor → „Als gekocht bestätigen“ → Wochenplan „Gekocht ✓“; Kind: +4 („Kochen“); erneutes Bestätigen nicht mehr angeboten
- [ ] Kindergericht von morgen: kein Bestätigen-Button; Wochenplan zeigt „Kochen bestätigen?“ nur für vergangene/heutige
- [ ] Rangliste: Reihenfolge nach Gesammelt; Gleichstand gleicher Rang
- [ ] Offline (WLAN aus): „Ich“ mit „Offline – Stand vom …“; Eltern-Rangliste aus Stand, Antippen/„Ändern“ deaktiviert
- [ ] Erfolgssignal Phase 1: Abnahme einer Karte und Bestätigung eines Kochens ergeben genau die erwarteten Punkte; Startbuchung übernimmt den Küchenboard-Stand

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-GAME-010–014 und REQ-MEAL-034 haben App-Tests mit REQ-ID im Namen
- [ ] Rollenrechte in der UI entsprechen dem Backend (Kind: nur eigenes Konto, kein Buchen/Ändern/Bestätigen)
- [ ] „Ich“ offline lesbar; Schreibaktionen nur online
- [ ] `api/`, `backend/` unverändert; keine neue Bibliothek
- [ ] Design (`gamification.md`, `meals.md`), Traceability, README-Checkliste aktualisiert

## Completion Checklist
- [ ] Code folgt den Mustern (Ports in `domain`, Remote/DataStore in `data`, State/Event-ViewModels, Route/Screen-Trennung)
- [ ] Fehler: `AppError` → `message()`; IO-Fehler `Log.w` mit `TAG`, keine Begründungen/Namen im Log
- [ ] Keine Magic Numbers (`PointsLimits`, benannte Dateinamen)
- [ ] Funktionen < 50 Zeilen, Dateien < 400 Zeilen
- [ ] `feature:gamification` kennt weder `meals` noch `tasks` noch `auth`; nur `app` verbindet
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Slot-Änderung an `FamilyScreen`/`mainGraph` bricht `feature:auth`-Tests | M | L | Default-Parameter `{}`; bestehende Aufrufe bleiben gültig |
| Doppeltes Laden durch `init` + `ON_RESUME` | M | L | Nur über `ON_RESUME`/`Refresh` laden (Task 6) |
| `Dish`-Konstruktor positionell an vielen Stellen | M | L | `cookedAt` als letzter Parameter mit Default |
| Rangliste wirkt für das jüngste Kind unfair | M | M | PRD-Risiko; im Testbetrieb beobachten (Could: Zeitraum-Rangliste) |
| Eltern vergessen das Bestätigen → Kochpunkte fehlen | M | M | Markierung „Kochen bestätigen?“ im Wochenplan; bei Bedarf später Hinweis auf „Heute“ |
| Kover < 80 % durch neue Screens | M | M | Screens als reine Composables mit State testen; `*Route*` ist ausgeschlossen |
| Generierte Modellnamen kollidieren (`LedgerEntry`, `PointsAccount`) | H | L | Import-Aliase `Api…` wie in `feature:tasks` |

## Notes
- Phase 2–4 erweitern „Ich“ (Badges, Avatar, Sparziel); `MeScreen` bleibt dafür eine `LazyColumn` mit Abschnitten.
- Umsetzung laut CLAUDE.md in einer **neuen Session** (Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/punktekonto-app.plan.md`.
- Vor dem Start einen neuen Branch von `main` anlegen (z. B. `feat/punktekonto-app`); der aktuelle Branch `feat/punktekonto-backend` ist gemergt.
