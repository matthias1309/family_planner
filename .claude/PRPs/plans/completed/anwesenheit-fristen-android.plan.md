# Plan: Anwesenheit & Fristen – Android-Eltern-UI (Phase 2b)

## Summary
Neues Feature-Modul `feature:meals` in der App, das die fertige Meals-API aus Phase 2a (8 Endpunkte, Tag `meals`)
bedient: Eltern pflegen pro Kind „kocht“ und das Anwesenheitsmuster (immer / Wechselmodell A/B), legen Ausnahmen an
und löschen sie, und ändern die Fristeinstellungen (inkl. Anzeige der vorgemerkten Änderung). Alle Mitglieder sehen
eine Wochenübersicht mit Anwesenheit je Kind und Kind-Frist. Backend und `api/openapi.yaml` bleiben unverändert.

## User Story
Als Elternteil möchte ich Wechselmodell, Ferien und Fristen in der App pflegen, damit Kochtage und Einreichfristen
ohne `curl` stimmen; als Familienmitglied möchte ich sehen, wer in welcher Woche da ist und bis wann ein kochendes
Kind einreichen muss.

## Problem → Solution
Phase 2a liefert korrekte Anwesenheit/Fristen nur über die API (Pflege per `curl`), die App zeigt nach dem Start nur
„Hallo <Name>“ → App mit Wochenübersicht für alle und Eltern-Screens für Profile, Ausnahmen und Fristen; Fehler (400,
403, 409, offline) erscheinen als deutsche Meldungen. Danach ist PRD-Phase 2 abgeschlossen.

## Metadata
- **Complexity**: Large (neues Feature-Modul, ~35 Dateien, jede klein; keine neuen Muster, keine neuen Bibliotheken)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 2 – Anwesenheit & Fristen, Teil **2b** (Android-Eltern-UI), REQ-MEAL-010–013
- **Estimated Files**: ~35 neu, ~10 geändert (inkl. Tests und Doku)

---

## UX Design

### Before
```
┌──────────────────────────┐
│ Hallo Lea 👋             │   Kinder: nur Begrüßung
│ Elternteil               │   Eltern: + „Familie verwalten“
│ Weitere Funktionen folgen│
│ [ Familie verwalten ]    │   Muster/Ausnahmen/Fristen: nur per curl
└──────────────────────────┘
```

### After
```
Home ─┬─ [ Anwesenheit & Fristen ] (alle) ──▶ Wochenübersicht
      └─ [ Familie verwalten ]    (Eltern)

┌ Wochenübersicht ─────────────────────────┐      ┌ Anwesenheit verwalten (Eltern) ─┐
│ ◀  Woche 19.10.–25.10.  ▶                │      │ Ben   kocht · Wechselmodell     │
│ Bestellfrist: So 18.10., 16:00           │      │       A: Di–Do  B: Di–So        │
│                                          │      │ Lea   kocht nicht · immer da    │
│ Ben (kocht)  Mo Di Mi Do Fr Sa So        │      │ Tim   kocht · immer da          │
│              –  ●  ●  ●  –  –  –         │      │ [ Fristen einstellen ]          │
│              Einreichen bis Do 15.10.,16:00│     └─────────────┬───────────────────┘
│ Tim (kocht)  ●  ●  ●  ●  ●  ●  ●         │                    ▼
│              Einreichen bis So 18.10.,16:00│     ┌ Ben ─────────────────────────────┐
│ Lea          ●  ●  ◆  ◆  ●  ●  ●  (◆=Ausn.)│     │ [x] kocht                        │
│ [ Anwesenheit verwalten ] (nur Eltern)   │      │ (•) Wechselmodell ( ) immer da   │
└──────────────────────────────────────────┘      │ Woche A: [Di][Mi][Do]            │
                                                  │ Woche B: [Di]…[So]               │
┌ Fristen (Eltern) ───────────────────────┐       │ Diese Woche (12.–18.10.) ist: A|B│
│ Gilt jetzt: So 16:00 · Kind 16:00       │       │ [ Speichern ]                    │
│ Vorgemerkt ab 26.10.: Sa 18:00 · 16:00  │       │ Ausnahmen                        │
│ Bestellfrist: [Sonntag ▾] [16:00]       │       │ 20.12.–06.01. anwesend „Ferien“ 🗑│
│ Kind-Frist:   [16:00]                   │       │ [ + Ausnahme ] → Zeitraum, an/ab,│
│ ⓘ gilt ab der übernächsten Planwoche    │       │   Notiz; 409 → „überschneidet …“ │
│ [ Speichern ]                           │       └──────────────────────────────────┘
└─────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Home | Begrüßung, Eltern: „Familie verwalten“ | + „Anwesenheit & Fristen“ für alle | Einstieg über Slot aus `app` (feature:auth kennt meals nicht) |
| Wochenübersicht | – | Anwesenheit Mo–So je Kind, Kind-Frist, Bestellfrist, Woche vor/zurück | Start = aktuelle Planwoche (Montag nach heute); lesend für alle |
| Profil bearbeiten | curl `PUT /profiles/{id}` | Schalter „kocht“, Muster, Tage A/B, „Diese Woche ist A/B“ | Referenzwoche wird berechnet, kein Datumspicker |
| Ausnahmen | curl | Liste, „+ Ausnahme“ (Zeitraum, anwesend/abwesend, Notiz), Löschen mit Bestätigung | 409 → Servermeldung „Zeitraum überschneidet sich …“ |
| Fristen | curl `PUT /settings` | Wochentag + zwei Uhrzeiten, Anzeige „gilt jetzt“ / „vorgemerkt ab …“ | Hinweis: wirkt ab übernächster Planwoche |
| Kinder | – | Nur Wochenübersicht; keine Verwaltungsschaltflächen | 403 trotzdem als „Nur für Eltern.“ abgebildet |
| Offline | – | Fehlertext „Server nicht erreichbar. Bist du im Heim-WLAN?“ + Pull-to-Refresh | Kein Cache in 2b (Offline-Lesen kommt mit REQ-MEAL-002, Phase 3) |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/03-design/meals.md` | all | Fachregeln: Planwoche, Wochentyp-Parität, Ausnahmen, Fristen, Eingabegrenzen |
| P0 | `api/openapi.yaml` | 178–325, 575–786 | Endpunkte und Schemas `meals` (nicht ändern!) |
| P0 | `docs/02-architecture/adr/0009-android-app-architecture.md` | all | Module, Koin, Retrofit, Navigation folgt Session |
| P0 | `docs/03-design/core.md` | Abschnitt „App-Seite (Phase 1b)“ | Fehlerabbildung `AppError`, Rollen in Screens |
| P0 | `android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/` | alle | Muster: `domain`/`data`/`ui`, Module, Navigation |
| P0 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt` | 1–88 | `apiCall`/`apiCallNoContent`, Fehlerabbildung |
| P1 | `android/feature/auth/src/test/kotlin/de/familyplanner/android/feature/auth/` | alle | Testmuster: MockWebServer, Fakes, Turbine, Robolectric |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/{AppNavHost,FamilyPlannerApp}.kt` | all | Einbindung Graph + Koin-Modul |
| P1 | `android/app/build.gradle.kts` | 72–125 | Abhängigkeiten + Kover-Aggregation |
| P1 | `android/core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` | all | Generierte Signaturen (nach `./gradlew :core:api:openApiGenerate`) |
| P2 | `backend/meals/src/main/kotlin/de/familyplanner/meals/attendance/AttendanceService.kt` | 60–130 | Servermeldungen (deutsch), Validierungsregeln |
| P2 | `backend/meals/src/main/kotlin/de/familyplanner/meals/web/RequestParsing.kt` | all | Jahr 2000–2100, `HH:mm` |
| P2 | `android/README.md` | 59–end | Form der Abnahme-Checkliste |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Material3 `DateRangePicker` | developer.android.com/develop/ui/compose/components/datepickers | `rememberDateRangePickerState()`; Werte sind **UTC-Millis um Mitternacht** → `Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()` |
| Material3 `TimePicker` | developer.android.com/develop/ui/compose/components/time-pickers | `rememberTimePickerState(hour, minute, is24Hour = true)`; in `AlertDialog` einbetten |
| Type-safe Navigation | developer.android.com/guide/navigation/design/type-safety | `@Serializable data class ProfileEditRoute(val memberId: String)`; `backStackEntry.toRoute<…>()` |

Keine weitere Recherche nötig — alle übrigen Bausteine (Retrofit, Koin, Compose, Turbine, Robolectric, MockWebServer)
sind im Projekt etabliert. Keine neuen Bibliotheken.

---

## Patterns to Mirror

### NAMING_CONVENTION (Module, Pakete, Routen)
```kotlin
// SOURCE: android/feature/auth/build.gradle.kts:1-21
plugins {
    id("familyplanner.android-library")
    id("familyplanner.android-compose")
}
android { namespace = "de.familyplanner.android.feature.auth" }
dependencies {
    api(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.koin.compose)
    implementation(libs.navigation.compose)
    implementation(libs.compose.material.icons)
    testImplementation(project(":core:testing"))
    testImplementation(libs.mockwebserver)
}

// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/AuthNavigation.kt:22-52
@Serializable
data object HomeRoute
fun NavGraphBuilder.mainGraph(navController: NavHostController) {
    composable<HomeRoute> { HomeRoute(onManageFamily = { navController.navigate(FamilyRoute) }) }
    composable<FamilyRoute> { FamilyRoute(onBack = { navController.popBackStack() }) }
}
```
Pakete: `de.familyplanner.android.feature.meals.{domain,data,ui.<screen>}`; Klassen `XxxViewModel`, `XxxUiState`,
`XxxEvent`, `XxxScreen` (zustandslos, testbar) + `XxxRoute` (holt ViewModel per `koinViewModel`). Use Cases
`VerbNounUseCase` mit `suspend operator fun invoke`.

### ERROR_HANDLING (Repository → `ApiResult`, Mapping → `null` = Serverfehler)
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/data/RemoteFamilyRepository.kt:21-43
override suspend fun members(): ApiResult<List<FamilyMember>> =
    apiCall(json, { members.listMembers() }) { response -> response.data?.map { it.toDomain() } }
override suspend fun revokeDevice(deviceId: String): ApiResult<Unit> = apiCallNoContent(json) { devices.revokeDevice(deviceId) }
/** Wandelt alle Elemente um; `null`, sobald eines nicht umgewandelt werden kann (Serverfehler statt halber Liste). */
private inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? { … }

// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/data/Mappers.kt:43-58
internal fun ApiDevice.toDomain(): FamilyDevice? {
    val seen = lastSeenAt?.let { parseInstant(it) ?: return null }
    return FamilyDevice(id, memberId, name, seen)
}
private fun parseInstant(value: String): Instant? =
    try { Instant.parse(value) } catch (@Suppress("SwallowedException") e: DateTimeParseException) { null }
```
Client-Validierung vor dem Request, deutsche Texte, über `validationFailure(...)`:
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/domain/Validation.kt:6-11
internal fun validateName(name: String): ApiResult.Failure? =
    when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        name.length > AuthLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${AuthLimits.NAME_MAX} Zeichen haben.")
        else -> null
    }
```
Anzeige: `ErrorText(state.error)` (`core/ui/.../components/Components.kt:33-40`) — bei `Validation`/`Conflict` hat die
deutsche Servermeldung Vorrang (`core/ui/.../ErrorMessages.kt:25-35`), 403 → „Nur für Eltern.“, `Network` → „Server
nicht erreichbar. Bist du im Heim-WLAN?“. **Keine neuen `AppError`-Typen nötig.**

### LOGGING_PATTERN
```kotlin
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt:44-58
} catch (e: IOException) {
    Log.w(TAG, "Network failure", e)
    ApiResult.Failure(AppError.Network)
```
Logging passiert zentral in `apiCall`. Im Feature-Modul **nicht** zusätzlich loggen.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/domain/Repositories.kt:22-35
interface FamilyRepository {
    suspend fun members(): ApiResult<List<FamilyMember>>
    suspend fun revokeDevice(deviceId: String): ApiResult<Unit>
}
// Implementierung: class RemoteFamilyRepository(private val members: MembersApi, private val devices: DevicesApi, private val json: Json)
```

### SERVICE_PATTERN (Use Case + parallele Ladevorgänge)
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/domain/UseCases.kt:75-88
class LoadFamilyUseCase(private val repository: FamilyRepository) {
    suspend operator fun invoke(): ApiResult<FamilyOverview> =
        coroutineScope {
            val members = async { repository.members() }
            val devices = async { repository.devices() }
            val membersResult = members.await()
            val devicesResult = devices.await()
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            if (devicesResult !is ApiResult.Success) return@coroutineScope devicesResult as ApiResult.Failure
            ApiResult.Success(FamilyOverview(membersResult.data, devicesResult.data))
        }
}
```

### VIEWMODEL_PATTERN (UiState + Event + `isBusy`-Sperre)
```kotlin
// SOURCE: android/feature/auth/src/main/kotlin/de/familyplanner/android/feature/auth/ui/family/FamilyViewModel.kt:71-127
class FamilyViewModel(sessions: SessionStore, private val loadFamily: LoadFamilyUseCase, …) : ViewModel() {
    private val _state = MutableStateFlow(FamilyUiState(ownDeviceId = sessions.current?.deviceId))
    val state: StateFlow<FamilyUiState> = _state.asStateFlow()
    init { refresh() }
    fun onEvent(event: FamilyEvent) { when (event) { FamilyEvent.Refresh -> refresh() … } }
    private fun addMember(name: String, role: Role) {
        if (_state.value.isBusy) return
        _state.update { it.copy(isBusy = true, addMemberError = null) }
        viewModelScope.launch {
            when (val result = createMember(name, role)) {
                is ApiResult.Success -> { _state.update { it.copy(isBusy = false, showAddMember = false) }; refresh() }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, addMemberError = result.error) }
            }
        }
    }
}
```
Screen: `PullToRefreshBox` + `LazyColumn`, Dialoge in eigenem `…DialogsHost` (FamilyScreen.kt:44-107),
Löschen über `ConfirmDialog` (core:ui). Rollenabhängige Sichtbarkeit als berechnete UiState-Property
(`HomeViewModel.kt:18-23`: `val canManageFamily: Boolean get() = session?.role == Role.PARENT`).

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/auth/src/test/kotlin/de/familyplanner/android/feature/auth/data/RemoteRepositoriesTest.kt:24-72
class RemoteRepositoriesTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        …
    }
    @After fun tearDown() { server.close() }
    private fun enqueue(code: Int, body: String = "") = server.enqueue(MockResponse.Builder().code(code).body(body)
        .addHeader("Content-Type", "application/json").build())
    @Test fun `pair sends bearer-less request and maps the session`() = runTest {
        enqueue(201, """{"success":true,"data":{…}}""")
        val result = auth.pair("123456", "Pixel")
        assertIs<ApiResult.Success<*>>(result)
        val request = server.takeRequest()
        assertEquals("/api/v1/devices/pair", request.url.encodedPath)
        assertEquals("""{"code":"123456","deviceName":"Pixel"}""", request.body?.utf8())
    }
}
// ViewModels: @get:Rule val mainDispatcher = MainDispatcherRule(); Fakes mit `var xxxResult` (fakes/Fakes.kt:15-50)
// Screens:   @RunWith(RobolectricTestRunner::class), createComposeRule(), FamilyPlannerTheme { … },
//            compose.onNodeWithText("Nur für Eltern.").assertIsDisplayed()
// Namen:     `REQ-AUTH-002 pair screen shows pairing error` → hier `REQ-MEAL-0xx …`
```

---

## Files to Change

Alle Pfade relativ zu `android/`, sofern nicht anders angegeben. `M` = `feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals`, `T` = `feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals`.

| File | Action | Justification |
|---|---|---|
| `settings.gradle.kts` | UPDATE | `":feature:meals"` aufnehmen ⚠ Phase 3 |
| `feature/meals/build.gradle.kts`, `src/main/AndroidManifest.xml` | CREATE | Modul nach Muster `feature:auth` |
| `M/MealsModule.kt` | CREATE | Koin: `MealsApi`, Repository, Use Cases, ViewModels ⚠ Phase 3 |
| `M/MealsNavigation.kt` | CREATE | Routen + `mealsGraph(navController)` + `MealsHomeActions` ⚠ Phase 3 |
| `M/domain/attendance/Models.kt` | CREATE | `AttendancePattern` (sealed), `AttendanceProfile`, `AttendanceException`, `DeadlineSettings(+Overview)`, `WeekAttendance`, `ChildWeek`, `AttendanceDay`, `Presence`, `AttendanceSource`, `WeekType` |
| `M/domain/attendance/PlanWeeks.kt` | CREATE | Reine Funktionen: `ZONE`, `mondayOf`, `currentPlanWeek(now)`, `weekTypeOf(date, reference)`, `referenceWeekFor(today, currentIsA)` |
| `M/domain/attendance/AttendanceLimits.kt`, `Validation.kt` | CREATE | Grenzen (366 Tage, Notiz 60, Jahr 2000–2100) + deutsche Meldungen |
| `M/domain/attendance/Repositories.kt` | CREATE | `AttendanceRepository`-Interface |
| `M/domain/attendance/UseCases.kt` | CREATE | `LoadWeekOverviewUseCase`, `LoadProfilesUseCase`, `SaveProfileUseCase`, `LoadExceptionsUseCase`, `AddExceptionUseCase`, `DeleteExceptionUseCase`, `LoadDeadlineSettingsUseCase`, `SaveDeadlineSettingsUseCase` |
| `M/data/attendance/Mappers.kt` | CREATE | API ↔ Domäne (Datum/Instant/Weekday) |
| `M/data/attendance/RemoteAttendanceRepository.kt` | CREATE | `MealsApi` + `MembersApi` (Namen) |
| `M/ui/week/WeekOverviewViewModel.kt`, `WeekOverviewScreen.kt` | CREATE | Wochenübersicht (alle) |
| `M/ui/profiles/ProfilesViewModel.kt`, `ProfilesScreen.kt` | CREATE | Liste der Kinder (Eltern) |
| `M/ui/profile/ProfileEditViewModel.kt`, `ProfileEditScreen.kt`, `ExceptionsSection.kt`, `ExceptionDialogs.kt` | CREATE | Profil + Ausnahmen (Eltern) |
| `M/ui/settings/DeadlineSettingsViewModel.kt`, `DeadlineSettingsScreen.kt` | CREATE | Fristen (Eltern) |
| `M/ui/Labels.kt` | CREATE | deutsche Wochentagskürzel, Muster-Zusammenfassung |
| `feature/meals/src/main/res/values/strings.xml` | CREATE | alle Texte ⚠ Phase 3 |
| `T/domain/PlanWeeksTest.kt`, `ValidationTest.kt`, `UseCasesTest.kt` | CREATE | TDD Domäne |
| `T/data/RemoteAttendanceRepositoryTest.kt` | CREATE | MockWebServer: Pfade, Bodies, Mapping, 400/403/409 |
| `T/fakes/Fakes.kt` | CREATE | `FakeAttendanceRepository`, Fixtures |
| `T/ui/ViewModelsTest.kt`, `T/ui/ScreensTest.kt` | CREATE | Zustände, Rollen, Fehlertexte |
| `core/ui/src/main/kotlin/de/familyplanner/android/core/ui/Formatting.kt` | CREATE | `BerlinZone`, `formatTime`, `formatDateTime`, `formatDate` aus `feature:auth` hierher verschieben (DRY) |
| `feature/auth/src/main/kotlin/…/feature/auth/ui/Formatting.kt` | DELETE | durch core:ui ersetzt; Imports in `FamilyScreen.kt`, `FamilyDialogs.kt` anpassen |
| `feature/auth/…/AuthNavigation.kt`, `ui/home/HomeScreen.kt` | UPDATE | Slot `homeActions: @Composable () -> Unit` für Feature-Einstiege ⚠ Phase 3 |
| `feature/auth/src/test/…/ui/ScreensTest.kt` | UPDATE | `HomeScreen`-Aufrufe um Slot ergänzen |
| `app/build.gradle.kts` | UPDATE | `implementation(project(":feature:meals"))`, `kover(project(":feature:meals"))`, `versionCode = 2`, `versionName = "0.2.0"` ⚠ Phase 3 |
| `app/src/main/kotlin/…/app/FamilyPlannerApp.kt` | UPDATE | `mealsModule` in `modules(...)` ⚠ Phase 3 |
| `app/src/main/kotlin/…/app/AppNavHost.kt` | UPDATE | `MainHost`: `mealsGraph(navController)`, Slot füllen ⚠ Phase 3 |
| `app/src/test/kotlin/…/app/AppNavHostTest.kt` | UPDATE | Home zeigt „Anwesenheit & Fristen“ |
| `README.md` (android) | UPDATE | Modulliste, Abnahme-Checkliste Phase 2b |
| `docs/03-design/meals.md` (Repo-Root) | UPDATE | Abschnitt „App-Seite (Phase 2b)“ |
| `docs/04-verification/traceability.md` | UPDATE | App-Tests REQ-MEAL-010–013, Status |
| `docs/01-requirements/meals.prd.md` | UPDATE | Zeile 2 / 2b, Status `complete` nach Umsetzung |

## NOT Building

- Keine Änderung an `api/openapi.yaml` oder `backend/` (Vertrag aus 2a ist fix). Fehlt etwas, Plan stoppen und
  nachfragen statt Backend anzufassen.
- Kein Offline-Cache / Room (REQ-MEAL-002 gehört zu Phase 3); offline nur Fehlermeldung + Pull-to-Refresh.
- Kein Wochenplan, keine Gerichte, keine Einreichung/Freigabe, keine Erinnerungen (Phasen 3, 4, 6).
- Keine Client-seitige Neuberechnung von Anwesenheit/Fristen für die Anzeige — die Wochenübersicht zeigt, was der
  Server liefert. Einzige Client-Rechnung: Wochentyp/Referenzwoche für den A/B-Umschalter und die aktuelle Planwoche.
- Kein Bearbeiten einer Ausnahme (API hat kein PUT) — Löschen und neu anlegen.
- Keine Profile für Eltern (Server: „Anwesenheit gilt nur für Kinder“).
- Kein neuer `AppError`-Typ, keine Änderung an `core:data`.

---

## Step-by-Step Tasks

### Task 0: Branch & Ausgangslage
- **ACTION**: Branch `feat/anwesenheit-fristen-android` von `main`; `cd android && ./gradlew :core:api:openApiGenerate testDebugUnitTest` grün.
- **IMPLEMENT**: nichts.
- **MIRROR**: –
- **IMPORTS**: –
- **GOTCHA**: Läuft Phase 3 parallel, zuerst Task 1–2 als eigenen kleinen Commit/PR mergen (siehe „Kollisionen mit Phase 3“), damit beide Zweige auf demselben Gerüst aufsetzen.
- **VALIDATE**: `MealsApi.kt` existiert unter `core/api/build/generated/openapi/.../client/`.

### Task 1: Modul-Gerüst `feature:meals` + Home-Slot (⚠ Kollisionspunkte)
- **ACTION**: Modul anlegen und in `app` einhängen; Home bekommt einen Slot für Feature-Einstiege.
- **IMPLEMENT**:
  - `settings.gradle.kts`: `include(..., ":feature:auth", ":feature:meals")`.
  - `feature/meals/build.gradle.kts` = Kopie von `feature/auth/build.gradle.kts` mit `namespace = "de.familyplanner.android.feature.meals"`.
  - `AndroidManifest.xml` leer wie `feature/auth/src/main/AndroidManifest.xml`.
  - `MealsModule.kt`: `val mealsModule = module { single { get<Retrofit>().create(MealsApi::class.java) } … }` — `MealsApi` wird **hier** registriert, nicht in `core:data/NetworkModule.kt` (hält `core` unverändert; Phase 3 nutzt dasselbe `single`).
  - `MealsNavigation.kt`: `@Serializable data object WeekOverviewRoute`, `data object ProfilesRoute`, `data class ProfileEditRoute(val memberId: String)`, `data object DeadlineSettingsRoute`; `fun NavGraphBuilder.mealsGraph(navController: NavHostController)`; `@Composable fun MealsHomeActions(onOpenWeek: () -> Unit)` (ein `OutlinedButton` „Anwesenheit & Fristen“, `fillMaxWidth`).
  - `feature:auth`: `HomeScreen(state, onManageFamily, homeActions: @Composable () -> Unit = {})` rendert `homeActions()` oberhalb von „Familie verwalten“; `HomeRoute(onManageFamily, homeActions)`; `mainGraph(navController, homeActions: @Composable () -> Unit = {})`. `home_more` („Weitere Funktionen folgen.“) bleibt.
  - `app/AppNavHost.kt` `MainHost`: `NavHost(...) { mainGraph(navController) { MealsHomeActions(onOpenWeek = { navController.navigate(WeekOverviewRoute) }) }; mealsGraph(navController) }`.
  - `FamilyPlannerApp`: `modules(appModule, sessionModule, networkModule, authModule, mealsModule)`.
  - `app/build.gradle.kts`: `implementation(project(":feature:meals"))`, `kover(project(":feature:meals"))`, `versionCode = 2`, `versionName = "0.2.0"`.
  - Formatter nach `core:ui` verschieben (`core/ui/.../Formatting.kt`, Paket `de.familyplanner.android.core.ui`): `BerlinZone`, `formatTime(Instant)`, `formatDateTime(Instant)`, neu `formatDate(LocalDate)` (`dd.MM.`), `formatDateRange(from, to)`; `feature:auth` importiert von dort, alte Datei löschen.
- **MIRROR**: NAMING_CONVENTION; `AuthNavigation.kt:49-52`; `FamilyPlannerApp.kt:13-16`.
- **IMPORTS**: `de.familyplanner.api.client.MealsApi`, `retrofit2.Retrofit`, `org.koin.dsl.module`, `org.koin.core.module.dsl.viewModel`, `androidx.navigation.toRoute`.
- **GOTCHA**: `feature:meals` darf **nicht** von `feature:auth` abhängen und umgekehrt (ADR-0009) — nur `app` kennt beide. Kover-Ausschluss `*Route*` (app/build.gradle.kts:114) greift auch für `XxxRoute`-Composables; Screens selbst müssen getestet sein.
- **VALIDATE**: `./gradlew :app:testDebugUnitTest` (AppNavHostTest bleibt grün), neuer Test `with session the home screen offers attendance` in `AppNavHostTest` (Text „Anwesenheit & Fristen“ sichtbar; Koin-Testmodul um `WeekOverviewViewModel` nur nötig, wenn navigiert wird — Test klickt nicht).

### Task 2: Domäne – Modelle & Planwochen (TDD)
- **ACTION**: Erst `PlanWeeksTest` (RED), dann `Models.kt` + `PlanWeeks.kt`.
- **IMPLEMENT**:
  ```kotlin
  val ZONE: ZoneId = ZoneId.of("Europe/Berlin")
  enum class WeekType { A, B }
  sealed interface AttendancePattern {
      data object Always : AttendancePattern
      data class Alternating(val weekA: Set<DayOfWeek>, val weekB: Set<DayOfWeek>, val referenceWeek: LocalDate) : AttendancePattern
  }
  data class AttendanceProfile(val memberId: String, val cooks: Boolean, val pattern: AttendancePattern)
  enum class Presence { PRESENT, ABSENT }
  enum class AttendanceSource { PATTERN, EXCEPTION }
  data class AttendanceException(val id: String, val memberId: String, val from: LocalDate, val to: LocalDate, val presence: Presence, val note: String?)
  data class DeadlineSettings(val orderDay: DayOfWeek, val orderTime: LocalTime, val childTime: LocalTime, val effectiveFrom: LocalDate)
  data class DeadlineSettingsOverview(val current: DeadlineSettings, val scheduled: DeadlineSettings?)
  data class AttendanceDay(val date: LocalDate, val present: Boolean, val source: AttendanceSource)
  data class ChildWeek(val memberId: String, val name: String, val cooks: Boolean, val days: List<AttendanceDay>, val submissionDeadline: Instant?)
  data class WeekOverview(val weekStart: LocalDate, val orderDeadline: Instant, val children: List<ChildWeek>)
  data class ChildProfile(val memberId: String, val name: String, val profile: AttendanceProfile)

  fun mondayOf(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  fun currentPlanWeek(now: Instant): LocalDate = mondayOf(now.atZone(ZONE).toLocalDate()).plusWeeks(1)
  fun weekTypeOf(date: LocalDate, referenceWeek: LocalDate): WeekType =
      if (Math.floorMod(ChronoUnit.WEEKS.between(referenceWeek, mondayOf(date)), 2L) == 0L) WeekType.A else WeekType.B
  /** Referenzwoche so wählen, dass die Kalenderwoche von [today] den Typ [typeOfThisWeek] hat. */
  fun referenceWeekFor(today: LocalDate, typeOfThisWeek: WeekType): LocalDate =
      mondayOf(today).let { if (typeOfThisWeek == WeekType.A) it else it.minusWeeks(1) }
  ```
  Zeit über `java.time.Clock` injizieren (ViewModels bekommen `clock: Clock = Clock.system(ZONE)`), damit Tests deterministisch sind.
- **MIRROR**: Regeln aus `docs/03-design/meals.md` (Tabelle „Domänenmodell & Regeln“) 1:1.
- **IMPORTS**: `java.time.*`, `java.time.temporal.ChronoUnit`, `java.time.temporal.TemporalAdjusters`.
- **GOTCHA**: Parität muss **auch vor der Referenzwoche** stimmen (`floorMod`, nicht `%`). „Aktuelle Planwoche“ = Montag **nach** heute (Sa 17.10.2026 → 19.10.2026; So 18.10. 23:30 Berlin → ebenfalls 19.10.; Mo 19.10. 00:30 Berlin → 26.10.). Sonntagnacht in UTC ist schon Montag — immer über `ZONE` rechnen.
- **VALIDATE**: Tests `REQ-MEAL-010 week type alternates from reference week`, `REQ-MEAL-010 week type before reference week keeps parity`, `REQ-MEAL-010 week type across new year` (KW 53/2026 → 2027), `REQ-MEAL-010 reference week for this week as B is previous monday`, `REQ-MEAL-012 current plan week is monday after today in Berlin` (inkl. Sonntag 23:30 / Montag 00:30, Zeitumstellung 25.10.2026).

### Task 3: Domäne – Validierung (TDD)
- **ACTION**: `ValidationTest` (RED) → `AttendanceLimits.kt`, `Validation.kt`.
- **IMPLEMENT**:
  - `AttendanceLimits`: `EXCEPTION_MAX_DAYS = 366`, `NOTE_MAX = 60`, `MIN_YEAR = 2000`, `MAX_YEAR = 2100`.
  - `validatePattern(pattern)`: `Alternating` mit `weekA ∪ weekB` leer → „Bitte mindestens einen Anwesenheitstag wählen.“
  - `validateException(from, to, note)`: `to < from` → „Das Ende darf nicht vor dem Beginn liegen.“; Länge `ChronoUnit.DAYS.between(from, to) + 1 > 366` → „Eine Ausnahme darf höchstens 366 Tage dauern.“; Jahr außerhalb 2000–2100 → „Bitte ein Datum zwischen 2000 und 2100 wählen.“; Notiz nach `trim()` > 60 → „Die Notiz darf höchstens 60 Zeichen haben.“
  - Notiz: `trim()`, leer → `null`.
- **MIRROR**: ERROR_HANDLING (`validationFailure`), `feature/auth/.../domain/Validation.kt`.
- **IMPORTS**: `de.familyplanner.android.core.data.validationFailure`, `ApiResult`.
- **GOTCHA**: Grenzen exakt wie Backend (`AttendanceService.kt:104-126`, `RequestParsing.kt`): 366 Tage **inklusive** beider Randtage — Backend prüft `ChronoUnit.DAYS.between(from, to) + 1 > 366` (`AttendanceService.kt:117`), identisch übernehmen. Uhrzeiten entstehen aus dem TimePicker und sind immer gültig; Formatierung als `HH:mm` mit führender Null (`DateTimeFormatter.ofPattern("HH:mm")`).
- **VALIDATE**: `REQ-MEAL-010 alternating pattern needs at least one day`, `REQ-MEAL-011 exception end before start is rejected`, `REQ-MEAL-011 exception longer than 366 days is rejected`, `REQ-MEAL-011 note longer than 60 characters is rejected`, `REQ-MEAL-011 blank note becomes null`.

### Task 4: Daten – Mapper & Repository (TDD gegen MockWebServer)
- **ACTION**: `RemoteAttendanceRepositoryTest` (RED) → `Repositories.kt`, `Mappers.kt`, `RemoteAttendanceRepository.kt`.
- **IMPLEMENT**:
  ```kotlin
  interface AttendanceRepository {
      suspend fun memberNames(): ApiResult<Map<String, String>>
      suspend fun profiles(): ApiResult<List<AttendanceProfile>>
      suspend fun saveProfile(profile: AttendanceProfile): ApiResult<AttendanceProfile>
      suspend fun exceptions(memberId: String): ApiResult<List<AttendanceException>>
      suspend fun addException(memberId: String, from: LocalDate, to: LocalDate, presence: Presence, note: String?): ApiResult<AttendanceException>
      suspend fun deleteException(exceptionId: String): ApiResult<Unit>
      suspend fun deadlineSettings(): ApiResult<DeadlineSettingsOverview>
      suspend fun saveDeadlineSettings(orderDay: DayOfWeek, orderTime: LocalTime, childTime: LocalTime): ApiResult<DeadlineSettingsOverview>
      suspend fun week(weekStart: LocalDate): ApiResult<WeekAttendanceRaw>   // memberId-basiert, Namen ergänzt der Use Case
  }
  class RemoteAttendanceRepository(private val meals: MealsApi, private val members: MembersApi, private val json: Json) : AttendanceRepository
  ```
  Mapper (`internal`, `Mappers.kt`):
  - `Weekday` ↔ `DayOfWeek` per `DayOfWeek.valueOf(name)` / `ApiWeekday.valueOf(name)` (gleiche Namen).
  - `ApiAttendancePattern.toDomain(): AttendancePattern?` — `ALWAYS` → `Always`; `ALTERNATING` mit `referenceWeek == null` oder unlesbar → `null` (Serverfehler).
  - `AttendancePattern.toApi()`: `Always` → `ApiAttendancePattern(PatternType.ALWAYS, emptySet(), emptySet(), null)`; `Alternating` → `ALTERNATING`, Sets, `referenceWeek.toString()`.
  - Datum: `LocalDate.parse` / `toString()` (ISO `yyyy-MM-dd`); Zeitstempel: `Instant.parse` (Server sendet `Instant.toString()`, z. B. `2026-10-22T14:00:00Z`); Uhrzeit: `LocalTime.parse("16:00")`, zurück `HH:mm`. Unlesbar → `null` → `AppError.Server` (Muster `parseInstant` in `feature/auth/.../data/Mappers.kt:51-58`).
  - Listen mit `mapAllOrNull` (lokal kopieren, `private`).
  - `memberNames()` = `members.listMembers()` → `associate { it.id to it.name }`.
- **MIRROR**: REPOSITORY_PATTERN, ERROR_HANDLING, TEST_STRUCTURE.
- **IMPORTS**: `de.familyplanner.api.client.MealsApi`, `de.familyplanner.api.client.MembersApi`, `de.familyplanner.api.model.{AttendancePattern as ApiAttendancePattern, Weekday as ApiWeekday, PatternType, Presence as ApiPresence, CreateAttendanceExceptionRequest, UpdateAttendanceProfileRequest, UpdateDeadlineSettingsRequest, …}`, `core.data.apiCall`, `apiCallNoContent`.
- **GOTCHA**: Generierte Modelle haben `@Contextual` an Enum-Feldern (`AttendancePattern.type`) — funktioniert mit `createJson()` wie bei `Member.role`; im Test JSON-Roundtrip prüfen. Generierte `weekA`/`weekB` sind `Set`, Serialisierungsreihenfolge im Request-Body-Assert nicht hart erwarten → Body per `json.decodeFromString<UpdateAttendanceProfileRequest>` vergleichen. Namenskonflikt Domäne/API wie in auth über `import … as Api…` lösen.
- **VALIDATE**: Tests (Pfad, Methode, Body, Mapping):
  `REQ-MEAL-010 profiles map always and alternating patterns`, `REQ-MEAL-010 save profile sends pattern with reference week`, `REQ-MEAL-010 always pattern is sent without days and reference`, `REQ-MEAL-010 alternating without reference week is a server error`,
  `REQ-MEAL-011 add exception posts dates presence and note`, `REQ-MEAL-011 overlapping exception maps 409 to conflict with server message`, `REQ-MEAL-011 delete exception uses exception path and 204`,
  `REQ-MEAL-012 week attendance maps days and submission deadline`, `REQ-MEAL-012 missing submission deadline stays null`,
  `REQ-MEAL-013 settings map current and scheduled`, `REQ-MEAL-013 save settings sends HH mm times`,
  `REQ-MEAL-010 child receives forbidden on profile update` (403 → `AppError.Forbidden`), `validation error keeps server message` (400 → `AppError.Validation("Das Muster braucht …")`), `network failure maps to Network` (Server schließen → `AppError.Network`).

### Task 5: Domäne – Use Cases (TDD mit Fake)
- **ACTION**: `fakes/Fakes.kt` + `UseCasesTest` (RED) → `UseCases.kt`.
- **IMPLEMENT**:
  - `LoadWeekOverviewUseCase(repo)(weekStart)`: parallel `week(weekStart)` + `memberNames()` (Muster `LoadFamilyUseCase`); Kinder nach Reihenfolge der Antwort; unbekannte memberId → Name `"?"` (kein Fehler, Mitglied evtl. gerade gelöscht).
  - `LoadProfilesUseCase(repo)()`: parallel `profiles()` + `memberNames()` → `List<ChildProfile>`.
  - `SaveProfileUseCase(repo)(profile)`: `validatePattern` → `saveProfile`.
  - `LoadExceptionsUseCase`, `DeleteExceptionUseCase`: Durchreichen.
  - `AddExceptionUseCase(repo)(memberId, from, to, presence, note)`: `validateException` → `addException` mit getrimmter Notiz.
  - `LoadDeadlineSettingsUseCase`, `SaveDeadlineSettingsUseCase`: Durchreichen.
- **MIRROR**: SERVICE_PATTERN.
- **IMPORTS**: `kotlinx.coroutines.async`, `coroutineScope`.
- **GOTCHA**: Kein Client-seitiges Rechnen von Fristen — nur Server-Werte durchreichen.
- **VALIDATE**: `REQ-MEAL-012 week overview joins attendance with member names`, `REQ-MEAL-012 week overview fails when attendance fails`, `REQ-MEAL-010 invalid pattern is not sent`, `REQ-MEAL-011 invalid exception is not sent`, `REQ-MEAL-011 note is trimmed before sending`.

### Task 6: UI – Wochenübersicht (alle Mitglieder) (TDD)
- **ACTION**: ViewModel-/Screen-Tests (RED) → `ui/week/WeekOverviewViewModel.kt`, `WeekOverviewScreen.kt`, `ui/Labels.kt`.
- **IMPLEMENT**:
  - `WeekOverviewUiState(weekStart: LocalDate, overview: WeekOverview?, isLoading, error: AppError?, isParent: Boolean)`; Events `Refresh`, `PreviousWeek`, `NextWeek`.
  - Start `currentPlanWeek(clock.instant())`; Woche ±7 Tage, Begrenzung auf Jahre 2000–2100 (Buttons deaktivieren).
  - Beim Wochenwechsel alte Daten verwerfen (`overview = null`), laufenden Job abbrechen (`loadJob?.cancel()`), damit eine späte Antwort nicht die falsche Woche zeigt.
  - Screen: Titel „Woche 19.10.–25.10.“, Bestellfrist „Bestellfrist: So 18.10., 16:00“; je Kind Zeile mit Name (+ „kocht“), 7 Tageszellen Mo–So (anwesend ● / abwesend –, Ausnahme hervorgehoben + `contentDescription` „Ausnahme“), darunter bei `cooks` „Einreichen bis Do 15.10., 16:00“ bzw. „In dieser Woche nicht da – kein Kochtag“ wenn `submissionDeadline == null`. Leere Liste → „Noch keine Kinder angelegt.“ Eltern: Button „Anwesenheit verwalten“ → `ProfilesRoute`.
  - `isParent` aus `SessionStore.current?.role == Role.PARENT`.
  - `Labels.kt`: eigene Kürzel `Mo Di Mi Do Fr Sa So` und Langnamen (`Montag` …) als Konstanten/Strings, **nicht** `DayOfWeek.getDisplayName` (liefert je nach ICU „Mo.“ mit Punkt, Robolectric ≠ Gerät). Datum+Zeit: `"${weekdayShort} ${formatDate(d)}, ${formatTime(i)}"`.
- **MIRROR**: VIEWMODEL_PATTERN, `FamilyScreen.kt` (PullToRefreshBox/LazyColumn/ErrorText).
- **IMPORTS**: `core.ui.formatTime`, `formatDate`, `core.ui.components.ErrorText`, `core.data.session.{SessionStore, Role}`.
- **GOTCHA**: Fristen in **Berlin-Zeit** anzeigen (`2026-10-22T14:00:00Z` → „Do 22.10., 16:00“; nach Zeitumstellung `2026-10-29T15:00:00Z` → 16:00). Die Kind-Frist liegt in der *laufenden* Woche vor der Planwoche — Text daher mit Datum, nicht nur Wochentag.
- **VALIDATE**: ViewModel: `REQ-MEAL-012 starts at the plan week after today`, `REQ-MEAL-012 next and previous week reload`, `REQ-MEAL-012 offline shows network error and keeps week`, `late response of previous week is ignored`.
  Screens: `REQ-MEAL-012 cooking child shows submission deadline in Berlin time`, `REQ-MEAL-012 absent cooking child shows no deadline`, `REQ-MEAL-011 exception days are marked`, `REQ-MEAL-010 child sees overview without manage button`, `REQ-MEAL-010 parent sees manage button`, `offline error is shown in German` („Server nicht erreichbar. Bist du im Heim-WLAN?“).

### Task 7: UI – Profile-Liste (Eltern) (TDD)
- **ACTION**: Tests (RED) → `ui/profiles/ProfilesViewModel.kt`, `ProfilesScreen.kt`.
- **IMPLEMENT**: Liste `ChildProfile` mit Name, „kocht“/„kocht nicht“, Zusammenfassung „immer da“ bzw. „Wechselmodell · A: Di–Do · B: Di–So“ (Tagesbereiche zusammenfassen, Lücken als Komma: `Mo, Mi–Fr`). Tippen → `ProfileEditRoute(memberId)`. Button „Fristen einstellen“ → `DeadlineSettingsRoute`. Zurück-Button. Nach Rückkehr aus dem Editor neu laden (`LaunchedEffect` auf `Lifecycle.Event.ON_RESUME` im Route-Composable oder Refresh-Event aus `mealsGraph` beim Zurückkehren — einfachste Variante: `ProfilesRoute` ruft `viewModel.onEvent(Refresh)` in `LifecycleResumeEffect`).
- **MIRROR**: `FamilyScreen.kt`, VIEWMODEL_PATTERN.
- **IMPORTS**: `androidx.lifecycle.compose.LifecycleResumeEffect`.
- **GOTCHA**: Kinder kommen über die Navigation nicht hierher; ein 403 (z. B. Rolle inzwischen geändert) wird trotzdem als „Nur für Eltern.“ angezeigt. GET `/profiles` ist für alle erlaubt — 403 kommt nur beim Speichern.
- **VALIDATE**: `REQ-MEAL-010 profiles list shows cooking flag and pattern summary`, `REQ-MEAL-010 day ranges are summarised` (Unit-Test der Zusammenfassungsfunktion in `Labels.kt`), `no children shows hint`.

### Task 8: UI – Profil bearbeiten (Eltern) (TDD)
- **ACTION**: Tests (RED) → `ui/profile/ProfileEditViewModel.kt`, `ProfileEditScreen.kt`.
- **IMPLEMENT**:
  - Laden: `LoadProfilesUseCase` und das passende `ChildProfile` wählen (kein Einzel-GET in der API) + `LoadExceptionsUseCase(memberId)` parallel.
  - Formularzustand im UiState: `cooks`, `patternType` (`ALWAYS`/`ALTERNATING`), `weekA: Set<DayOfWeek>`, `weekB: Set<DayOfWeek>`, `thisWeekType: WeekType`. Beim Laden eines `Alternating`: `thisWeekType = weekTypeOf(today, referenceWeek)`; bei `Always`: Default `weekA = {TUE, WED, THU}`, `weekB = {TUE..SUN}`, `thisWeekType = A` (PRD-Wechselmodell als Vorschlag).
  - Speichern: `Alternating(weekA, weekB, referenceWeekFor(today, thisWeekType))` bzw. `Always`; Erfolg → Hinweis „Gespeichert.“ (Snackbar oder Text), Formular bleibt.
  - Screen: `Switch` „kocht“, `SegmentedButton`/`RadioButton` „immer da“ / „Wechselmodell“, bei Wechselmodell zwei Reihen `FilterChip` Mo–So („Woche A“, „Woche B“), Umschalter „Diese Woche (12.10.–18.10.) ist Woche A | B“, `SubmitButton` „Speichern“, `ErrorText`.
  - Darunter `ExceptionsSection` (Task 9).
- **MIRROR**: VIEWMODEL_PATTERN (`isBusy`), `SubmitButton` aus core:ui.
- **IMPORTS**: `androidx.compose.material3.{Switch, FilterChip, SegmentedButton, SingleChoiceSegmentedButtonRow}`.
- **GOTCHA**: Referenzwoche bezieht sich auf die **Kalenderwoche von heute**, nicht auf die Planwoche — im Text das Datum der heutigen Woche zeigen, sonst verwechseln die Eltern A/B. Beim Umschalten auf „immer da“ die A/B-Auswahl im State behalten (falls zurückgeschaltet wird), aber `Always` senden. Ein unverändertes, geladenes `Alternating` muss beim Speichern dieselbe Parität ergeben (Test!).
- **VALIDATE**: `REQ-MEAL-010 loading alternating profile derives this week type`, `REQ-MEAL-010 saving this week as B sends previous monday as reference`, `REQ-MEAL-010 unchanged profile keeps parity when saved`, `REQ-MEAL-010 switching to always sends always`, `REQ-MEAL-010 empty alternating days shows validation message`, `REQ-MEAL-010 forbidden save shows parents only`, Screen: `REQ-MEAL-010 day chips are shown only for alternating`.

### Task 9: UI – Ausnahmen (Eltern) (TDD)
- **ACTION**: Tests (RED) → `ExceptionsSection.kt`, `ExceptionDialogs.kt`, Events im `ProfileEditViewModel`.
- **IMPLEMENT**:
  - Liste nach `from` sortiert (Server sortiert schon): „20.12.–06.01. · anwesend · Ferien“ + Mülleimer-`IconButton` (`contentDescription` „Ausnahme löschen“).
  - Events: `ShowAddException`, `DismissAddException`, `AddException(from, to, presence, note)`, `RequestDeleteException(e)`, `CancelDelete`, `ConfirmDelete`. Eigener Fehler `addExceptionError` im Dialog (409 bleibt im Dialog, Eingaben bleiben erhalten).
  - Dialog: `DateRangePickerDialog`-Ablauf (Material3 `DatePickerDialog` + `DateRangePicker`), danach Formular mit `SegmentedButton` „anwesend“/„abwesend“ und `OutlinedTextField` Notiz (`maxLength` 60 durch Abschneiden im `onValueChange`). Löschen per `ConfirmDialog` („Ausnahme löschen?“, „Danach gilt wieder das Muster.“).
  - Nach Anlegen/Löschen Ausnahmen neu laden.
- **MIRROR**: `FamilyDialogsHost` (FamilyScreen.kt:79-107), `AddMemberDialog` (FamilyDialogs.kt), `ConfirmDialog`.
- **IMPORTS**: `androidx.compose.material3.{DatePickerDialog, DateRangePicker, rememberDateRangePickerState, SelectableDates}`.
- **GOTCHA**: `DateRangePicker` liefert **UTC-Mitternacht in Millis** → mit `ZoneOffset.UTC` in `LocalDate` wandeln, nie mit Systemzone (sonst Tag-Versatz). `SelectableDates` auf Jahre 2000–2100 begrenzen. Server-409 trägt die deutsche Meldung „Zeitraum überschneidet sich mit einer bestehenden Ausnahme“ → wird über `AppError.Conflict.message` angezeigt. 404 beim Löschen (bereits gelöscht) → Liste neu laden und Fehler „Nicht gefunden.“ zeigen.
- **VALIDATE**: `REQ-MEAL-011 adding exception reloads list`, `REQ-MEAL-011 overlapping exception keeps dialog open with server message`, `REQ-MEAL-011 deleting requires confirmation`, `REQ-MEAL-011 deleted exception disappears`, `REQ-MEAL-011 picker millis are read as UTC dates` (Unit-Test der Umrechnungsfunktion), Screen: `REQ-MEAL-011 exception row shows range presence and note`.

### Task 10: UI – Fristeinstellungen (Eltern) (TDD)
- **ACTION**: Tests (RED) → `ui/settings/DeadlineSettingsViewModel.kt`, `DeadlineSettingsScreen.kt`.
- **IMPLEMENT**:
  - Laden: `LoadDeadlineSettingsUseCase`; Formular mit Werten aus `scheduled ?: current` vorbelegen.
  - Anzeige: „Gilt jetzt: Sonntag, 16:00 · Kind-Frist 16:00“; wenn `scheduled != null`: „Vorgemerkt ab Planwoche 26.10.: Samstag, 18:00 · Kind-Frist 16:00“.
  - Eingabe: Wochentag-Dropdown (`ExposedDropdownMenuBox`, Langnamen aus `Labels.kt`), zwei Uhrzeit-Felder, die einen `TimePicker` (24 h) im `AlertDialog` öffnen.
  - Hinweis unter dem Formular: „Änderungen gelten ab der übernächsten Planwoche; die gerade geplante Woche behält ihre Frist.“
  - Speichern → Antwort (Overview) direkt in den State übernehmen (kein zweiter GET), Hinweis „Vorgemerkt ab …“.
- **MIRROR**: VIEWMODEL_PATTERN.
- **IMPORTS**: `androidx.compose.material3.{TimePicker, rememberTimePickerState, ExposedDropdownMenuBox, ExposedDropdownMenu, DropdownMenuItem, MenuAnchorType}`.
- **GOTCHA**: `effectiveFrom` ist ein Montag (Planwoche) — als „ab Planwoche 26.10.“ formulieren, nicht „ab 26.10.“, sonst denkt man, die Frist am 26.10. sei betroffen. Ist `scheduled == null`, aber `current.effectiveFrom` in der Zukunft, kann das nicht vorkommen (Server liefert dann `scheduled`) — keinen Sonderfall bauen.
- **VALIDATE**: `REQ-MEAL-013 shows current and scheduled settings`, `REQ-MEAL-013 form starts with scheduled values when present`, `REQ-MEAL-013 saving sends day and HH mm times and shows scheduled week`, `REQ-MEAL-013 forbidden save shows parents only`, Screen: `REQ-MEAL-013 hint explains effect from the week after next`.

### Task 11: Navigation verdrahten & Koin vervollständigen
- **ACTION**: `mealsGraph` mit allen Screens, `mealsModule` mit allen Bindungen.
- **IMPLEMENT**:
  ```kotlin
  fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
      composable<WeekOverviewRoute> { WeekOverviewRoute(onManage = { navController.navigate(ProfilesRoute) }, onBack = { navController.popBackStack() }) }
      composable<ProfilesRoute> { ProfilesRoute(onEdit = { navController.navigate(ProfileEditRoute(it)) }, onSettings = { navController.navigate(DeadlineSettingsRoute) }, onBack = { navController.popBackStack() }) }
      composable<ProfileEditRoute> { entry -> ProfileEditRoute(entry.toRoute<ProfileEditRoute>().memberId, onBack = { navController.popBackStack() }) }
      composable<DeadlineSettingsRoute> { DeadlineSettingsRoute(onBack = { navController.popBackStack() }) }
  }
  val mealsModule = module {
      single { get<Retrofit>().create(MealsApi::class.java) }
      single<AttendanceRepository> { RemoteAttendanceRepository(get(), get(), get()) }
      factory { LoadWeekOverviewUseCase(get()) } … (alle Use Cases)
      viewModel { WeekOverviewViewModel(get(), get()) }
      viewModel { ProfilesViewModel(get()) }
      viewModel { params -> ProfileEditViewModel(params.get(), get(), get(), get(), get(), get()) }
      viewModel { DeadlineSettingsViewModel(get(), get()) }
  }
  ```
  Composable-Funktionen `XxxRoute` heißen wie die Routen-Objekte (Muster `FamilyRoute`) — Namenskonflikt vermeiden: Routen-Objekte im Paket `feature.meals` (`MealsNavigation.kt`), Composables in `ui.<screen>` und beim Import mit Alias oder voll qualifiziert ansprechen, genau wie `AuthNavigation.kt:6-9`.
  `ProfileEditRoute`-Composable: `koinViewModel<ProfileEditViewModel> { parametersOf(memberId) }`.
- **MIRROR**: `AuthNavigation.kt`, `AuthModule.kt`.
- **IMPORTS**: `org.koin.core.parameter.parametersOf`, `org.koin.androidx.compose.koinViewModel`.
- **GOTCHA**: `Clock` nicht als Koin-`single` global registrieren (kollidiert ggf. mit Phase 3) — Default-Parameter `Clock.system(ZONE)` in den ViewModels, Tests übergeben `Clock.fixed`.
- **VALIDATE**: `./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest`; Koin-Graph per `AppNavHostTest`-Erweiterung oder einem `MealsModuleTest` mit `koinApplication { modules(...) }.koin.get<…>()` für alle ViewModels (mit gefaktem `Retrofit` aus `createRetrofit(ServerConfig("http://localhost/"), OkHttpClient(), createJson())`).

### Task 12: Qualität & Coverage
- **ACTION**: CI-Kette lokal laufen lassen.
- **IMPLEMENT**: Detekt-/ktlint-Funde beheben (Funktionen < 50 Zeilen, Dateien < 400 Zeilen; große Screens in Sektionen-Composables zerlegen). Kover ≥ 80 % (Aggregation in `app`).
- **MIRROR**: `familyplanner.quality` Konvention.
- **GOTCHA**: `LongParameterList` bei ViewModels mit vielen Use Cases → ggf. Use Cases zu einer kleinen `ProfileEditActions`-Klasse bündeln statt Detekt-Suppress.
- **VALIDATE**: `./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` grün.

### Task 13: Doku, Traceability, PRD
- **ACTION**: V-Modell-Dokumente nachziehen.
- **IMPLEMENT**:
  - `docs/03-design/meals.md`: Satz „Die Eltern-UI in der App folgt als Phase 2b.“ ersetzen durch Abschnitt **„App-Seite (Phase 2b)“**: Modul `feature:meals`, Screens + Rollen, Referenzwoche über „Diese Woche ist A/B“, Planwoche = Montag nach heute (Berlin), Fehlerabbildung (409 Servermeldung, 403 „Nur für Eltern.“, offline), kein Cache (folgt mit REQ-MEAL-002), Einstieg über Home-Slot.
  - `docs/04-verification/traceability.md`, Zeilen REQ-MEAL-010–013: Unit-Spalte ergänzen um „App: `PlanWeeksTest`, `ValidationTest`, `UseCasesTest`, `ViewModelsTest`, `ScreensTest` (`REQ-MEAL-01x …`)“, Integration um „App: `RemoteAttendanceRepositoryTest`“, E2E „manuell: Abnahme Phase 2b (android/README.md)“, Status `Backend umgesetzt (App folgt in 2b)` → **`umgesetzt`** (nach Geräte-Abnahme → `abgenommen (Backend + App)` mit Datum).
  - `docs/01-requirements/meals.prd.md`, Tabelle „Implementation Phases“, Zeile 2: Status `in-progress` → **`complete`**, PRP-Spalte „2b: `.claude/PRPs/plans/completed/anwesenheit-fristen-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/anwesenheit-fristen-android-report.md`; Geräte-Abnahme <Datum>)“.
  - `android/README.md`: Modulliste um `feature:meals` (Anwesenheit & Fristen) und Abschnitt „Abnahme-Checkliste Phase 2b“ (siehe Manual Validation).
  - Plan nach `.claude/PRPs/plans/completed/` verschieben (macht `/prp-implement`).
- **VALIDATE**: Links in den Docs zeigen auf existierende Dateien; `grep -n "App folgt in 2b" docs/` liefert nichts mehr.

---

## Kollisionen mit Phase 3 (parallel)

Phase 3 (Wochenplan & Elterngerichte) erweitert dasselbe Backend-Modul `meals` und baut die App-Seite sehr
wahrscheinlich ebenfalls in `feature:meals`. Gemeinsam angefasste Dateien:

| Datei | Konflikt | Regel |
|---|---|---|
| `android/settings.gradle.kts` | beide fügen `:feature:meals` hinzu | Wer zuerst merged, legt das Modul an (Task 1 als eigener kleiner PR) |
| `android/feature/meals/build.gradle.kts` | Abhängigkeiten (Phase 3: ggf. Room für REQ-MEAL-002) | Phase 3 ergänzt nur |
| `feature/meals/.../MealsModule.kt` | Koin-Bindungen, `MealsApi`-`single` | Nur **ein** `single { …MealsApi… }`; Bindungen nach Teilbereich gruppiert (Kommentar `// attendance` / `// plan`) |
| `feature/meals/.../MealsNavigation.kt` | Routen, `mealsGraph`, `MealsHomeActions` | Routen je Teilbereich in eigene Datei auslagern, falls beide wachsen (`AttendanceNavigation.kt`, `PlanNavigation.kt`), `mealsGraph` ruft beide |
| `feature/meals/src/main/res/values/strings.xml` | Strings | Präfixe `attendance_*` (2b) vs. `plan_*` (3); oder getrennte Dateien `strings_attendance.xml` |
| `app/AppNavHost.kt`, `FamilyPlannerApp.kt`, `app/build.gradle.kts` | Graph/Modul/Kover/`versionCode` | Einzeilige Änderungen; beim Rebase zusammenführen, `versionCode` nur einmal erhöhen |
| `feature/auth/.../HomeScreen.kt`, `AuthNavigation.kt` | Home-Einstieg | Slot `homeActions` kommt mit 2b; Phase 3 fügt nur Buttons in `MealsHomeActions` hinzu |
| `core/ui/.../Formatting.kt` | Datumsformatierung | 2b verschiebt sie nach core:ui; Phase 3 nutzt sie mit |
| `docs/03-design/meals.md`, `traceability.md`, `meals.prd.md`, `android/README.md` | Doku-Abschnitte | Eigene Abschnitte/Zeilen je Phase, kein Umformatieren fremder Zeilen |

Paketgrenzen innerhalb des Moduls: 2b nutzt `domain/attendance`, `data/attendance`, `ui/{week,profiles,profile,settings}`;
Phase 3 nutzt `…/plan`. Die Wochenübersicht (`ui/week`) heißt bewusst „Anwesenheit“, damit Phase 3 einen eigenen
Wochenplan-Screen bauen kann; eine spätere Zusammenführung entscheidet Phase 4.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `weekTypeOf` | Referenz 2026-10-05, Datum 2026-10-14 | B | |
| `weekTypeOf` vor Referenz | Referenz 2026-10-05, Datum 2026-09-30 | B (floorMod) | ✔ |
| `weekTypeOf` Jahreswechsel | Referenz 2026-12-28, Datum 2027-01-06 | B | ✔ |
| `referenceWeekFor` | heute Sa 2026-10-17, B | 2026-10-05 | |
| `currentPlanWeek` | 2026-10-18T21:30Z (So 23:30 Berlin) | 2026-10-19 | ✔ |
| `currentPlanWeek` | 2026-10-18T22:30Z (Mo 00:30 Berlin) | 2026-10-26 | ✔ |
| `validateException` | to < from | Validation-Meldung | ✔ |
| `validateException` | 367 Tage | Validation-Meldung | ✔ |
| Mapper Pattern | `ALTERNATING` ohne referenceWeek | `AppError.Server` | ✔ |
| Mapper Settings | `"07:05"` | `LocalTime.of(7, 5)` und zurück `"07:05"` | |
| Picker-Umrechnung | Millis 2026-12-20T00:00Z | `LocalDate` 2026-12-20 (auch bei Systemzone Berlin) | ✔ |
| Fristanzeige | `2026-10-29T15:00:00Z` | „Do 29.10., 16:00“ (Winterzeit) | ✔ |
| 409 Ausnahme | Server `CONFLICT` + Meldung | Dialog bleibt offen, Servermeldung sichtbar | ✔ |
| 403 als Kind | `FORBIDDEN` | „Nur für Eltern.“ | ✔ |
| Offline | Verbindung abgelehnt | „Server nicht erreichbar. Bist du im Heim-WLAN?“ | ✔ |

### Edge Cases Checklist
- [x] Leere Eingabe (kein Anwesenheitstag, leere Notiz → `null`)
- [x] Maximale Eingabe (366 Tage, 60 Zeichen Notiz)
- [x] Ungültige Typen (unlesbare Server-Daten → `AppError.Server`)
- [x] Nebenläufigkeit (Wochenwechsel während Laden, Doppelklick „Speichern“ → `isBusy`)
- [x] Netzwerkfehler (offline, Zertifikat)
- [x] Keine Berechtigung (Kind → 403, UI blendet Aktionen aus)
- [x] Zeitzone/Zeitumstellung (25.10.2026), Sonntag-/Montag-Grenze
- [x] Keine Kinder angelegt / Kind ohne Profil (Server liefert Default `Always`, `cooks=false`)

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Funde

### Unit Tests
```bash
cd android && ./gradlew :feature:meals:testDebugUnitTest :feature:auth:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite (wie CI)
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug
```
EXPECT: grün, Kover ≥ 80 %

### Database Validation
N/A — keine Backend-/Schemaänderung. Gegenprobe: `git diff main -- api/ backend/` ist leer.

### Gegen lokales Backend (Emulator)
```bash
cd backend && ./gradlew run          # Port 8095, siehe android/README.md „Gegen ein lokales Backend“
cd android && ./gradlew installDebug # mit fp.serverUrl=http://localhost:8095/ in local.properties
```
EXPECT: Wochenübersicht lädt, Profil/Ausnahme/Fristen speichern funktioniert, 409 bei überlappender Ausnahme sichtbar

### Manual Validation (Abnahme Phase 2b – echte Handys gegen den Pi)
- [ ] Release-APK 0.2.0 (gleiche Signatur) per `adb install -r` auf Elternhandy und einem Kinderhandy aktualisieren; Kopplung bleibt erhalten
- [ ] Eltern: für jedes Kind „kocht“ und Muster setzen (Wechselmodell A = Di–Do, B = Di–So, „Diese Woche ist A/B“ nach echter Lage)
- [ ] Wochenübersicht der aktuellen und der folgenden 3 Planwochen: Eltern bestätigen die Anwesenheitstage gegen ihren echten Kalender
- [ ] Kind-Fristen: in A-Woche Donnerstag 16:00, in B-Woche = Bestellfrist; dauerhaft anwesendes Kind = Bestellfrist
- [ ] Ausnahme (z. B. Herbst-/Weihnachtsferien) anlegen → Tage in der Übersicht markiert, Frist ändert sich; zweite überlappende Ausnahme → Meldung „Zeitraum überschneidet sich …“; Ausnahme löschen → Muster gilt wieder
- [ ] Fristen ändern → „vorgemerkt ab Planwoche …“ sichtbar, die nächste Planwoche zeigt noch die alte Bestellfrist; danach wieder auf So 16:00 zurückstellen
- [ ] Kinderhandy: Wochenübersicht sichtbar, keine „Anwesenheit verwalten“-Schaltfläche
- [ ] WLAN aus: Übersicht zeigt „Server nicht erreichbar. Bist du im Heim-WLAN?“, nach WLAN an + Pull-to-Refresh wieder Daten

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-010–013 haben App-Tests mit REQ-ID im Namen
- [ ] Kinder sehen nur die Wochenübersicht; Eltern alle Verwaltungs-Screens
- [ ] 400/403/409/offline mit deutschen Meldungen abgebildet
- [ ] `api/` und `backend/` unverändert
- [ ] Geräte-Abnahme durch die Eltern dokumentiert (README-Checkliste, Datum in traceability.md)

## Completion Checklist
- [ ] Code folgt den Mustern aus `feature:auth` (UiState/Event, `ApiResult`, Fakes)
- [ ] Fehlerbehandlung nur über `ApiResult`/`AppError`, kein eigenes Logging im Feature
- [ ] Tests folgen Testmustern (MockWebServer, Turbine, Robolectric)
- [ ] Keine hartcodierten Grenzen außerhalb `AttendanceLimits`, keine Texte außerhalb `strings.xml`/Validation
- [ ] Moduldesign, Traceability, PRD, android/README aktualisiert
- [ ] Keine Abhängigkeit `feature:meals` ↔ `feature:auth`

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| A/B-Verwechslung durch Eltern (Referenzwoche) | M | H — falsche Fristen | Umschalter bezieht sich auf die sichtbare heutige Woche mit Datum; Abnahme über 4 Planwochen gegen echten Kalender |
| DateRangePicker-Zeitzone verschiebt Tage | M | M | UTC-Umrechnung als eigene, getestete Funktion |
| Parallel-Merge mit Phase 3 (Navigation/DI/Strings) | H | L | Gerüst (Task 1) früh separat mergen; Präfixe/Pakete trennen (Abschnitt Kollisionen) |
| Detekt `LongParameterList`/`LongMethod` bei Formular-Screens | M | L | Sektionen-Composables, Use-Case-Bündel |
| Material3-APIs (`MenuAnchorType`, `TimePicker`) experimentell | M | L | `@OptIn(ExperimentalMaterial3Api::class)` wie `FamilyScreen.kt:44` |
| Update-Installation scheitert an Signatur | L | M | Gleichen Keystore wie 1b verwenden (`fp.signing.*`), `versionCode` erhöhen |

## Notes
- Heute (2026-10-17, Samstag) ist die aktuelle Planwoche 2026-10-19; eine heute gespeicherte Friständerung gilt ab Planwoche 2026-10-26.
- Die API liefert kein Einzel-GET für ein Profil; der Editor lädt die Liste und wählt das Kind aus (5 Mitglieder → unkritisch).
- Die Wochenübersicht zeigt nur Kinder (Server liefert nur Kinder-Profile/-Anwesenheit). Namen kommen aus `GET /members`, das Kinder lesen dürfen.
- Kein ADR nötig: Modulschnitt folgt ADR-0001/0009; der Home-Slot ist eine kleine Erweiterung des bestehenden Musters und wird im Moduldesign beschrieben.
- Erfahrungen mit dem Plan nach Umsetzung im Report festhalten (`.claude/PRPs/reports/anwesenheit-fristen-android-report.md`).
