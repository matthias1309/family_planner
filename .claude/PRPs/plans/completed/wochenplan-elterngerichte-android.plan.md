# Plan: Wochenplan & Elterngerichte – App inkl. Offline-Cache (Phase 3b)

## Summary
`feature:meals` bekommt den Essensplan: alle Mitglieder sehen die Woche Mo–So mit Gericht und Koch/Köchin je Tag
(offene Tage als „noch offen“), Eltern legen Gerichte an, ändern/verschieben und löschen sie in einem eigenen Screen.
Der zuletzt synchronisierte Stand (diese und nächste Woche plus jede angesehene Woche) wird lokal gespeichert und ohne
Heimnetz mit „Stand: …“ angezeigt (REQ-MEAL-002). Backend und `api/openapi.yaml` bleiben unverändert (Vertrag aus 3a).

## User Story
Als Familienmitglied möchte ich auf meinem Handy sehen, wann es was gibt – auch unterwegs –, und als Elternteil möchte
ich die Gerichte der Woche in der App planen, damit der Zettel am Kühlschrank überflüssig wird.

## Problem → Solution
Phase 3a liefert Wochenplan und Elterngerichte nur über die API (`curl`), die App kennt nur Anwesenheit & Fristen und
zeigt offline nichts → Startseite mit „Essensplan“, Wochenplan für alle, Gericht-Editor für Eltern, lokaler Cache mit
Synchronisierungszeitpunkt. Danach ist PRD-Phase 3 abgeschlossen.

## Metadata
- **Complexity**: Large (≈ 25 neue Dateien, ≈ 12 geänderte; ein neues Muster: lokaler Cache per DataStore)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 3 – Wochenplan & Elterngerichte, Teil **3b** (App inkl. Offline-Cache), REQ-MEAL-001, REQ-MEAL-002, REQ-MEAL-032
- **Estimated Files**: ≈ 25 neu, ≈ 12 geändert (inkl. Tests, ADR und Doku)

## Entscheidungen des Anwenders (2026-10-18)
| Frage | Entscheidung |
|---|---|
| Startwoche des Essensplans | **Diese Woche** (Kalenderwoche von heute, Berlin); mit ‹ › zur kommenden/vorigen Woche |
| Gericht anlegen/ändern | **Eigener Screen** (Tag, Koch/Köchin, Name, mehrzeilige Zutaten, Löschen) |
| Offline gespeicherte Wochen | **Diese + nächste Woche** werden beim Öffnen geladen und gespeichert, dazu jede weitere angesehene Woche; alte Wochen werden aufgeräumt |

---

## UX Design

### Before
```
┌ Home ────────────────────────┐
│ Hallo Anna 👋                │
│ Elternteil                   │
│ [ Anwesenheit & Fristen ]    │   Wochenplan/Gerichte: nur per curl
│ [ Familie verwalten ]        │   Offline: nur Fehlermeldung, kein Stand
└──────────────────────────────┘
```

### After
```
Home ─┬─ [ Essensplan ]            (alle, Button)       ──▶ Wochenplan
      ├─ [ Anwesenheit & Fristen ] (alle, Outlined)     ──▶ Wochenübersicht (unverändert)
      └─ [ Familie verwalten ]     (Eltern)

┌ Essensplan ───────────────────────────────┐     ┌ Gericht (Eltern) ───────────────────┐
│ ‹  Woche 12.10.–18.10.  ›                 │     │ Tag: [Mo][Di][Mi][Do][Fr][Sa][So]   │
│ ⚠ Offline – Stand: Sa 17.10., 12:00       │     │      (belegte Tage deaktiviert)     │
│                                           │     │ Koch/Köchin: [Anna ▾]               │
│ Mo 12.10.  Spaghetti Bolognese            │     │ Name: [Gemüsecurry          ]       │
│            Anna · Nudeln, Hackfleisch, …  │     │ Zutaten (eine pro Zeile):           │
│ Di 13.10.  noch offen                (+)  │ ──▶ │ ┌─────────────────────────────┐     │
│ …                                         │     │ │Kokosmilch                   │     │
│ So 18.10.  HEUTE  Pfannkuchen             │     │ │Paprika                      │     │
│            Tom                            │     │ └─────────────────────────────┘     │
│ (Eltern: Tag antippen = anlegen/ändern)   │     │ [ Speichern ]   [ Löschen ]         │
│ [ Zurück ]                                │     │ 409 → „An diesem Tag ist schon …“   │
└───────────────────────────────────────────┘     └─────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Home | „Anwesenheit & Fristen“ | + „Essensplan“ (primärer Button) für alle | Über den bestehenden Slot `homeActions` (`MealsHomeActions`) |
| Essensplan | – | Woche Mo–So, je Tag Gericht + Koch/Köchin + Zutaten kurz, „noch offen“, „Heute“ markiert, ‹ › | Start = diese Woche; Sonntag → › zur kommenden Woche (REQ-MEAL-001) |
| Offline | Fehlermeldung | Letzter Stand mit „Offline – Stand: <Wochentag Datum, Uhrzeit>“ | Ohne Cache: „Server nicht erreichbar. Bist du im Heim-WLAN?“ |
| Gericht anlegen/ändern | `curl` | Eltern tippen auf einen Tag → Editor; Speichern/Löschen → zurück zum Plan, Plan lädt neu | Offline und für Kinder keine Bearbeitung |
| Fehler | – | 400 Servermeldung, 403 „Nur für Eltern.“, 404 „Nicht gefunden.“, 409 „An diesem Tag ist schon ein Gericht geplant“ | Mapping zentral in `core:ui` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/03-design/meals.md` | Abschnitte „Wochenplan & Elterngerichte (Phase 3)“, „App-Seite (Phase 2b)“ | Fachregeln Gericht/Zutaten/Koch, App-Regeln |
| P0 | `api/openapi.yaml` | 323–400, 872–945 | Endpunkte `getWeekPlan`, `createDish`, `updateDish`, `deleteDish`; Schemas `Dish`, `SaveDishRequest`, `PlanDay`, `WeekPlan` (**nicht ändern**) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | all | Grenzen (60/80/30/100) und deutsche Servermeldungen |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/` | alle | Bestehende Muster in genau diesem Modul (attendance) |
| P0 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/ApiCall.kt` | 1–88 | `apiCall`/`apiCallNoContent`, Fehlerabbildung |
| P0 | `android/core/data/src/main/kotlin/de/familyplanner/android/core/data/session/{DataStoreSessionStore,SessionSerializer}.kt` | all | DataStore-/Serializer-Muster für den Cache |
| P1 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/` | alle | Testmuster: Fakes, MockWebServer, Robolectric, `MealsModuleTest` |
| P1 | `android/core/data/src/test/kotlin/de/familyplanner/android/core/data/session/DataStoreSessionStoreTest.kt` | 1–60 | DataStore im JVM-Test mit `TemporaryFolder` |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 59–66 | Home-Slot + `mealsGraph` |
| P1 | `android/app/build.gradle.kts` | 30–35, 72–125 | `versionCode`, Kover-Ausschlüsse (`*Route*`, `*Module*`) |
| P2 | `docs/02-architecture/adr/0009-android-app-architecture.md` | all | Module, DataStore schon im Einsatz |
| P2 | `android/README.md` | 59–end | Form der Abnahme-Checkliste |
| P2 | `android/core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` | 61–231 | Generierte Signaturen (nach `./gradlew :core:api:openApiGenerate`) |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| DataStore mit eigenem Serializer | developer.android.com/topic/libraries/architecture/datastore („Typed DataStore“) | **Eine** `DataStore`-Instanz je Datei und Prozess (sonst `IllegalStateException`) → Koin-`single`; `Serializer.readFrom` darf bei kaputten Daten den `defaultValue` liefern; `updateData` ist atomar |
| Compose `OutlinedTextField` mehrzeilig | developer.android.com/develop/ui/compose/text/user-input | `singleLine = false`, `minLines = 4`; Zeilen über `text.lines()` |

Sonst keine Recherche nötig: Retrofit, Koin, Compose, Turbine, Robolectric, MockWebServer und DataStore sind im Projekt
etabliert. **Keine neue Bibliothek** — `libs.datastore` steht schon im Version-Catalog und wird in `core:data` genutzt.

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:19-58
@Serializable
data class ProfileEditRoute(val memberId: String)

/** Einstieg von der Startseite; `app` setzt ihn in den Slot von `feature:auth`. */
@Composable
fun MealsHomeActions(onOpenWeek: () -> Unit) {
    OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_attendance)) }
}

fun NavGraphBuilder.mealsGraph(navController: NavHostController) {
    composable<ProfileEditRoute> { entry ->
        ProfileEditRoute(memberId = entry.toRoute<ProfileEditRoute>().memberId, onBack = { navController.popBackStack() })
    }
}
```
Routen-Objekte im Paket `de.familyplanner.android.feature.meals`, gleichnamige Composables in `ui.<screen>`
(`XxxRoute` holt das ViewModel, `XxxScreen` ist zustandslos und getestet). Neue Pakete: `domain/plan`, `data/plan`,
`ui/plan`, `ui/dish`. Strings mit Präfix `plan_*` / `dish_*`. Use Cases `VerbNounUseCase` mit `suspend operator fun invoke`.

### ERROR_HANDLING
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/attendance/RemoteAttendanceRepository.kt:28-36
override suspend fun profiles(): ApiResult<List<AttendanceProfile>> =
    apiCall(json, { meals.listAttendanceProfiles() }) { response -> response.data?.mapAllOrNull { it.toDomain() } }

override suspend fun deleteException(exceptionId: String): ApiResult<Unit> =
    apiCallNoContent(json) { meals.deleteAttendanceException(exceptionId) }

// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/attendance/Parsing.kt:14-24,45-50
internal fun parseDate(value: String?): LocalDate? = value?.let { try { LocalDate.parse(it) } catch (@Suppress("SwallowedException") e: DateTimeParseException) { null } }
/** Wandelt alle Elemente um; `null`, sobald eines nicht umgewandelt werden kann (Serverfehler statt halber Liste). */
internal inline fun <T, R : Any> List<T>.mapAllOrNull(transform: (T) -> R?): List<R>? { … }
```
Mapper liefern `null` bei unlesbaren Daten → `apiCall` macht daraus `AppError.Server`. Client-Validierung vor dem
Request mit `validationFailure("…")` (deutsche Texte), Anzeige über `ErrorText(state.error)`; bei `Validation`/`Conflict`
gewinnt die Servermeldung (`core/ui/.../ErrorMessages.kt:25-35`). **Kein neuer `AppError`-Typ.**

### LOGGING_PATTERN
```kotlin
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/session/DataStoreSessionStore.kt:26-34
val stored =
    try {
        dataStore.data.first().session
    } catch (e: IOException) {
        // Unlesbarer Speicher: als abgemeldet behandeln statt ewig zu laden.
        Log.w(TAG, "Session storage unreadable, treating as signed out", e)
        null
    }
```
Netzwerkfehler loggt `apiCall` zentral — im Feature nicht zusätzlich. **Neu und einzige Ausnahme:** der Cache loggt
Lese-/Schreibfehler mit `Log.w(TAG, …)` (englisch, ohne Inhalte), genau wie `DataStoreSessionStore`/`SessionSerializer`.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/attendance/RemoteAttendanceRepository.kt:22-29
class RemoteAttendanceRepository(
    private val meals: MealsApi,
    private val members: MembersApi,
    private val json: Json,
) : AttendanceRepository {
    override suspend fun memberNames(): ApiResult<Map<String, String>> =
        apiCall(json, { members.listMembers() }) { response -> response.data?.associate { it.id to it.name } }
```

### SERVICE_PATTERN (Use Case, parallele Requests)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/attendance/UseCases.kt:12-31
class LoadWeekOverviewUseCase(private val repository: AttendanceRepository) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<WeekOverview> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val names = async { repository.memberNames() }
            val weekResult = week.await()
            val namesResult = names.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (namesResult !is ApiResult.Success) return@coroutineScope namesResult as ApiResult.Failure
            // Ein evtl. gerade gelöschtes Mitglied ist kein Fehler: Name unbekannt, Anwesenheit trotzdem zeigen.
            …
        }
}
/** Bündelt die Ausnahmen-Use-Cases für das Profil-Formular (hält die ViewModel-Parameterliste kurz). */
class ExceptionUseCases(val load: LoadExceptionsUseCase, val add: AddExceptionUseCase, val delete: DeleteExceptionUseCase)
```

### VIEWMODEL_PATTERN (Woche wechseln, späte Antworten verwerfen)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/week/WeekOverviewViewModel.kt:22-96
data class WeekOverviewUiState(val weekStart: LocalDate, val overview: WeekOverview? = null, val isLoading: Boolean = false,
    val error: AppError? = null, val isParent: Boolean = false) {
    val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
    val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
}
class WeekOverviewViewModel(sessions: SessionStore, private val loadWeek: LoadWeekOverviewUseCase, clock: Clock = Clock.system(BerlinZone)) : ViewModel() {
    private fun switchWeek(deltaWeeks: Long) {
        // Alte Daten verwerfen, damit nie die Daten einer anderen Woche unter dem neuen Titel stehen.
        _state.update { it.copy(weekStart = it.weekStart.plusWeeks(deltaWeeks), overview = null) }
        load()
    }
    private fun load() {
        loadJob?.cancel()
        val requested = _state.value.weekStart
        _state.update { it.copy(isLoading = true, error = null) }
        loadJob = viewModelScope.launch {
            val result = loadWeek(requested)
            // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
            if (_state.value.weekStart != requested) return@launch
            …
        }
    }
}
```
Screen: `PullToRefreshBox` + `LazyColumn` (`WeekOverviewScreen.kt:58-97`), Woche-Kopf mit ‹ › und
`contentDescription` (`WeekOverviewScreen.kt:99-119`). Formular-ViewModel mit `isBusy`-Sperre (`ProfileEditViewModel`),
`ConfirmDialog` aus `core:ui` zum Löschen, `DropdownMenu` als Auswahl (`DeadlineSettingsScreen.kt:122-130`),
`FilterChip`-Reihe Mo–So (`ProfileEditScreen.kt:136-140`), Neuladen nach Rückkehr per `LifecycleResumeEffect`
(`ProfilesScreen.kt:38-42`).

### CACHE_PATTERN (neu, angelehnt an Session-Speicher)
```kotlin
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/session/SessionSerializer.kt:21-46
class SessionSerializer(private val aead: Aead) : Serializer<StoredSession> {
    override val defaultValue: StoredSession = StoredSession()
    override suspend fun readFrom(input: InputStream): StoredSession {
        val encrypted = input.readBytes()
        if (encrypted.isEmpty()) return defaultValue
        return try { … } catch (e: SerializationException) {
            Log.w(TAG, "Stored session is malformed, treating as signed out", e)
            defaultValue
        }
    }
    override suspend fun writeTo(t: StoredSession, output: OutputStream) { output.write(…) }
}
// SOURCE: android/core/data/src/main/kotlin/de/familyplanner/android/core/data/network/NetworkModule.kt:65-73
DataStoreFactory.create(serializer = SessionSerializer(AeadFactory.create(context)), produceFile = { context.dataStoreFile(SESSION_FILE) })
```

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteAttendanceRepositoryTest.kt:35-70
@Before fun setUp() {
    server = MockWebServer().apply { start() }
    val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
    repository = RemoteAttendanceRepository(retrofit.create(MealsApi::class.java), retrofit.create(MembersApi::class.java), json)
}
private fun ok(data: String) = """{"success":true,"data":$data}"""
private fun error(code: String, message: String) = """{"success":false,"error":{"code":"$code","message":"$message"}}"""

// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/ViewModelsTest.kt:51-60
@get:Rule val mainDispatcher = MainDispatcherRule()
private val sessions = InMemorySessionStore(parentSession)
// Samstag, 17.10.2026, 12:00 Berlin
private val clock = Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))

// SOURCE: android/core/data/src/test/kotlin/de/familyplanner/android/core/data/session/DataStoreSessionStoreTest.kt:27-46
@get:Rule val folder = TemporaryFolder()
DataStoreFactory.create(SessionSerializer(aead), scope = scope) { folder.root.resolve("s.pb") }
```
Screens: `@RunWith(RobolectricTestRunner::class)`, `createComposeRule()`, `FamilyPlannerTheme { … }`,
`compose.onNodeWithText(…).assertIsDisplayed()`; untere Listenelemente mit `assertExists` (hohes Fenster → OOM, siehe
2b-Report). Testnamen mit REQ-ID: `` `REQ-MEAL-001 open day shows noch offen`() ``.

---

## Files to Change

`M` = `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals`,
`T` = `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals`.

| File | Action | Justification |
|---|---|---|
| `docs/02-architecture/adr/0011-app-offline-cache.md` | CREATE | Neue Architekturentscheidung: lokaler Lese-Cache per DataStore-JSON statt Room |
| `android/feature/meals/build.gradle.kts` | UPDATE | `implementation(libs.datastore)` |
| `M/data/attendance/Parsing.kt` → `M/data/Parsing.kt` | MOVE | `parseDate`, `parseInstant`, `mapAllOrNull` für attendance **und** plan (Paket `…feature.meals.data`); Imports in `data/attendance/*.kt` ergänzen |
| `M/domain/plan/Models.kt` | CREATE | `Dish`, `PlanDay`, `WeekPlan`, `PlanMember`, `DishDraft`, `PlannedDay`, `WeekPlanSnapshot`, `WeekPlanLoad` |
| `M/domain/plan/PlanLimits.kt`, `Validation.kt` | CREATE | Grenzen 60/80/30/100 + deutsche Meldungen, `ingredientLines(text)` |
| `M/domain/plan/MealPlanRepository.kt` | CREATE | Interface Remote-Zugriff |
| `M/domain/plan/PlanCache.kt` | CREATE | Interface lokaler Cache |
| `M/domain/plan/UseCases.kt` | CREATE | `LoadWeekPlanUseCase`, `LoadDishEditorUseCase`, `SaveDishUseCase`, `DeleteDishUseCase`, `DishUseCases` |
| `M/domain/attendance/PlanWeeks.kt` | UPDATE | `currentWeek(now)` (Montag dieser Woche, Berlin) neben `currentPlanWeek` |
| `M/data/plan/Mappers.kt` | CREATE | API ↔ Domäne |
| `M/data/plan/RemoteMealPlanRepository.kt` | CREATE | `MealsApi` + `MembersApi` |
| `M/data/plan/StoredPlan.kt` | CREATE | `@Serializable` Cache-DTOs + Umwandlung |
| `M/data/plan/PlanCacheSerializer.kt` | CREATE | DataStore-`Serializer` (JSON, unlesbar → leer) |
| `M/data/plan/DataStorePlanCache.kt` | CREATE | `PlanCache`-Implementierung, Besitzer-Prüfung, Aufräumen |
| `M/ui/plan/WeekPlanViewModel.kt`, `WeekPlanScreen.kt` | CREATE | Essensplan (alle) |
| `M/ui/dish/DishEditViewModel.kt`, `DishEditScreen.kt` | CREATE | Gericht-Editor (Eltern) |
| `M/MealsNavigation.kt` | UPDATE | `WeekPlanRoute`, `DishEditRoute`, `MealsHomeActions(onOpenPlan, onOpenWeek)`, Graph |
| `M/MealsModule.kt` | UPDATE | Bindungen `// plan` inkl. DataStore-`single` |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | `home_plan`, `plan_*`, `dish_*` |
| `T/fakes/PlanFakes.kt` | CREATE | `FakeMealPlanRepository`, `InMemoryPlanCache`, Fixtures |
| `T/domain/plan/ValidationTest.kt`, `UseCasesTest.kt` | CREATE | TDD Domäne |
| `T/domain/attendance/PlanWeeksTest.kt` | UPDATE | `currentWeek` |
| `T/data/RemoteMealPlanRepositoryTest.kt` | CREATE | MockWebServer |
| `T/data/DataStorePlanCacheTest.kt` | CREATE | echter DataStore in `TemporaryFolder` |
| `T/ui/PlanViewModelsTest.kt`, `T/ui/PlanScreensTest.kt` | CREATE | Zustände, Rollen, Offline, Fehlertexte (eigene Dateien, die 2b-Dateien sind schon groß) |
| `T/MealsModuleTest.kt` | UPDATE | neue ViewModels, `PlanCache` im Test überschreiben |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | `MealsHomeActions(onOpenPlan = { navigate(WeekPlanRoute) }, onOpenWeek = …)` |
| `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | UPDATE | Home zeigt „Essensplan“ |
| `android/app/build.gradle.kts` | UPDATE | `versionCode = 3`, `versionName = "0.3.0"` |
| `android/README.md` | UPDATE | Modulbeschreibung, Abnahme-Checkliste Phase 3b |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 3b)“, Satz „Kein Cache“ in 2b-Abschnitt anpassen |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-001/002/032 App-Tests, Status |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 3 → `complete` nach Umsetzung |

## NOT Building

- Keine Änderung an `api/openapi.yaml` oder `backend/`. Fehlt etwas im Vertrag: stoppen und nachfragen.
- Kein Hintergrund-Sync (WorkManager) — synchronisiert wird beim Öffnen des Essensplans; Hintergrund-Sync kommt mit den
  Erinnerungen (Phase 6).
- Kein Offline-**Schreiben** (keine Warteschlange): offline ist der Plan nur lesbar, Bearbeiten ist ausgeblendet.
- Kein Cache für Anwesenheit/Fristen (REQ-MEAL-002 betrifft den Essensplan; die Wochenübersicht bleibt wie in 2b).
- Keine Kindergerichte, keine Einreichung/Freigabe, kein Wochenstatus (Phase 4); Koch/Köchin nur Eltern (Server-Regel 3a).
- Keine Einkaufsliste (Phase 5), keine Zusammenführung von Zutaten, keine strukturierten Zutaten.
- Kein Verschieben eines Gerichts in eine andere Woche über den Editor (nur Tage derselben Woche); Server erlaubt es,
  Bedarf ist nicht geäußert.
- Kein Room, keine Verschlüsselung des Caches (Begründung im ADR-0011).
- Kein Widget „Heute gibt es …“ (Backlog).

---

## Step-by-Step Tasks

### Task 0: Branch & Ausgangslage
- **ACTION**: Branch `feat/wochenplan-elterngerichte-android` von `main`; Ausgangslage grün.
- **IMPLEMENT**: nichts.
- **MIRROR**: –
- **IMPORTS**: –
- **GOTCHA**: Generierter Client muss die Plan-Endpunkte enthalten (`getWeekPlan`, `createDish`, `updateDish`, `deleteDish`);
  sonst `./gradlew :core:api:openApiGenerate` laufen lassen.
- **VALIDATE**: `cd android && ./gradlew :core:api:openApiGenerate :feature:meals:testDebugUnitTest` grün;
  `grep -n getWeekPlan core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` findet Zeile 144.

### Task 1: ADR-0011 Offline-Cache
- **ACTION**: `docs/02-architecture/adr/0011-app-offline-cache.md` nach `0000-template.md` schreiben (Status Akzeptiert, 2026-10-18).
- **IMPLEMENT**:
  - **Kontext**: REQ-MEAL-002, LAN-only (ADR-0006/0007), Kinder außer Haus; nur lesen nötig; wenige Wochen, kleine Daten.
  - **Entscheidung**: (1) Lese-Cache im Feature-Modul, **ein DataStore** (`meal_plan_cache.json`, Serializer mit
    kotlinx.serialization) statt Room; (2) Read-through: jede erfolgreich geladene Woche wird mit `syncedAt` gespeichert;
    bei Verbindungsfehlern (`Network`, `Certificate`, `Server`) zeigt die App den gespeicherten Stand mit Zeitpunkt;
    (3) beim Öffnen werden diese und die nächste Woche geladen; (4) behalten werden Wochen ab „vorige Woche“, höchstens 8;
    (5) Cache gehört dem angemeldeten Mitglied (`memberId`); passt er nicht zur Session, gilt er als leer und wird beim
    nächsten Schreiben ersetzt; (6) unverschlüsselt: keine Geheimnisse, App-Sandbox, Backups/Gerätetransfer sind
    ausgeschlossen (`data_extraction_rules.xml`); (7) offline kein Schreiben.
  - **Alternativen**: Room (Codegen/KSP, Schema-Migrationen — überdimensioniert für ≤ 8 Wochen), OkHttp-HTTP-Cache
    (Server sendet keine Cache-Header, Zeitpunkt nicht abbildbar), verschlüsselter DataStore (kein Schutzbedarf), Offline-Schreiben (Konflikte, YAGNI).
  - **Konsequenzen**: Format-Version im Cache (`version = 1`); unbekannte Version/kaputte Datei → leer. Spätere Module
    können das Muster übernehmen; Hintergrund-Sync (Phase 6) schreibt in denselben Cache.
  - `docs/03-design/meals.md` verlinkt das ADR (Task 13).
- **MIRROR**: Aufbau von `0009-android-app-architecture.md` (Kontext / Entscheidung / Alternativen / Konsequenzen).
- **IMPORTS**: –
- **GOTCHA**: CLAUDE.md verlangt ADR **vor** der Abweichung — Task 1 vor dem Cache-Code erledigen.
- **VALIDATE**: Datei existiert, nummeriert fortlaufend nach `0010-…`.

### Task 2: Parsing teilen + Domäne Plan (Modelle, Woche) (TDD)
- **ACTION**: `Parsing.kt` nach `M/data/Parsing.kt` verschieben (Paket `de.familyplanner.android.feature.meals.data`),
  Imports in `data/attendance/Mappers.kt`, `SettingsMappers.kt`, `RemoteAttendanceRepository.kt` ergänzen.
  Dann `PlanWeeksTest` um `currentWeek` erweitern (RED) → `PlanWeeks.kt`, danach `domain/plan/Models.kt`.
- **IMPLEMENT**:
  ```kotlin
  // PlanWeeks.kt (neben currentPlanWeek)
  /** Montag der Kalenderwoche von heute (Berlin) — der Essensplan startet hier (Anwender 2026-10-18). */
  fun currentWeek(now: Instant): LocalDate = mondayOf(now.atZone(BerlinZone).toLocalDate())

  // domain/plan/Models.kt
  data class Dish(val id: String, val date: LocalDate, val cookId: String, val name: String, val ingredients: List<String>)
  data class PlanDay(val date: LocalDate, val dish: Dish?)
  data class WeekPlan(val weekStart: LocalDate, val days: List<PlanDay>)
  data class PlanMember(val id: String, val name: String, val isParent: Boolean)
  data class DishDraft(val date: LocalDate, val cookId: String, val name: String, val ingredients: List<String>)
  /** Tag zur Anzeige: Gericht mit aufgelöstem Namen von Koch/Köchin („?“, wenn unbekannt). */
  data class PlannedDay(val date: LocalDate, val dish: Dish?, val cookName: String?)
  data class WeekPlanSnapshot(val weekStart: LocalDate, val days: List<PlannedDay>, val syncedAt: Instant)
  sealed interface WeekPlanLoad {
      data class Fresh(val week: WeekPlanSnapshot) : WeekPlanLoad
      /** Server nicht erreichbar; gezeigt wird der zuletzt gespeicherte Stand. */
      data class Cached(val week: WeekPlanSnapshot, val error: AppError) : WeekPlanLoad
      data class Failed(val error: AppError) : WeekPlanLoad
  }
  ```
- **MIRROR**: NAMING_CONVENTION; `domain/attendance/Models.kt`.
- **IMPORTS**: `java.time.{Instant, LocalDate}`, `de.familyplanner.android.core.data.AppError`, `de.familyplanner.android.core.ui.BerlinZone`.
- **GOTCHA**: `currentWeek` ≠ `currentPlanWeek` — Sonntag 18.10.2026 → `currentWeek` = 12.10., `currentPlanWeek` = 19.10.
  Sonntag 23:30 Berlin (`2026-10-18T21:30:00Z`) ist noch 12.10.; Montag 00:30 Berlin (`2026-10-18T22:30:00Z`) ist 19.10.
  Nach dem Verschieben von `Parsing.kt` müssen die bestehenden 2b-Tests unverändert grün bleiben.
- **VALIDATE**: `REQ-MEAL-001 current week is monday of today in Berlin` (Sa 17.10. → 12.10., So 23:30 → 12.10.,
  Mo 00:30 → 19.10.); `./gradlew :feature:meals:testDebugUnitTest` grün.

### Task 3: Domäne – Validierung (TDD)
- **ACTION**: `T/domain/plan/ValidationTest.kt` (RED) → `PlanLimits.kt`, `Validation.kt`.
- **IMPLEMENT**:
  ```kotlin
  object PlanLimits {
      const val NAME_MAX = 60             // DISH_NAME_MAX (MealPlanService.kt:13)
      const val INGREDIENT_MAX = 80       // INGREDIENT_MAX
      const val INGREDIENTS_MAX_COUNT = 30
      const val INGREDIENT_LINES_RAW_MAX = 100
  }
  /** Zeilen des Zutatenfelds: getrimmt, ohne Leerzeilen (wie der Server). */
  fun ingredientLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
  internal fun validateDish(name: String, ingredientsText: String): ApiResult.Failure? {
      val rawLines = ingredientsText.lines()
      val lines = ingredientLines(ingredientsText)
      return when {
          name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
          name.trim().length > PlanLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${PlanLimits.NAME_MAX} Zeichen haben.")
          lines.any { it.length > PlanLimits.INGREDIENT_MAX } -> validationFailure("Eine Zutat darf höchstens ${PlanLimits.INGREDIENT_MAX} Zeichen haben.")
          rawLines.size > PlanLimits.INGREDIENT_LINES_RAW_MAX || lines.size > PlanLimits.INGREDIENTS_MAX_COUNT ->
              validationFailure("Höchstens ${PlanLimits.INGREDIENTS_MAX_COUNT} Zutaten je Gericht.")
          else -> null
      }
  }
  ```
- **MIRROR**: ERROR_HANDLING, `domain/attendance/Validation.kt`.
- **IMPORTS**: `de.familyplanner.android.core.data.{ApiResult, validationFailure}`.
- **GOTCHA**: Zutaten sind bei Elterngerichten **optional** (Entscheidung 3a) — leeres Feld ist gültig und ergibt `[]`.
  Grenzen exakt wie Backend; Prüfreihenfolge Name → Zutaten wie `MealPlanService.validated`. `"".lines()` ist `[""]`
  (eine Rohzeile) — unkritisch.
- **VALIDATE**: `REQ-MEAL-032 blank name is rejected`, `REQ-MEAL-032 name longer than 60 characters is rejected`,
  `REQ-MEAL-032 name of 60 characters after trimming is accepted`, `REQ-MEAL-032 ingredient line longer than 80 characters is rejected`,
  `REQ-MEAL-032 more than 30 ingredients are rejected`, `REQ-MEAL-032 30 ingredients with blank lines are accepted`,
  `REQ-MEAL-032 more than 100 raw lines are rejected`, `REQ-MEAL-032 empty ingredients are allowed`,
  `REQ-MEAL-032 ingredient lines are trimmed and blank lines dropped`.

### Task 4: Daten – Mapper & Remote-Repository (TDD gegen MockWebServer)
- **ACTION**: `T/data/RemoteMealPlanRepositoryTest.kt` (RED) → `domain/plan/MealPlanRepository.kt`, `data/plan/Mappers.kt`,
  `data/plan/RemoteMealPlanRepository.kt`.
- **IMPLEMENT**:
  ```kotlin
  interface MealPlanRepository {
      suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan>
      suspend fun members(): ApiResult<List<PlanMember>>
      suspend fun createDish(draft: DishDraft): ApiResult<Dish>
      suspend fun updateDish(dishId: String, draft: DishDraft): ApiResult<Dish>
      suspend fun deleteDish(dishId: String): ApiResult<Unit>
  }
  class RemoteMealPlanRepository(private val meals: MealsApi, private val members: MembersApi, private val json: Json) : MealPlanRepository {
      override suspend fun week(weekStart: LocalDate) = apiCall(json, { meals.getWeekPlan(weekStart.toString()) }) { it.data?.toDomain() }
      override suspend fun members() = apiCall(json, { members.listMembers() }) { r -> r.data?.map { PlanMember(it.id, it.name, it.role == ApiRole.PARENT) } }
      override suspend fun createDish(draft: DishDraft) = apiCall(json, { meals.createDish(draft.toApi()) }) { it.data?.toDomain() }
      override suspend fun updateDish(dishId: String, draft: DishDraft) = apiCall(json, { meals.updateDish(dishId, draft.toApi()) }) { it.data?.toDomain() }
      override suspend fun deleteDish(dishId: String) = apiCallNoContent(json) { meals.deleteDish(dishId) }
  }
  // Mappers.kt (internal)
  private const val DAYS_PER_WEEK = 7
  internal fun ApiDish.toDomain(): Dish? = parseDate(date)?.let { Dish(id, it, cookId, name, ingredients) }
  internal fun ApiPlanDay.toDomain(): PlanDay? { val day = parseDate(date) ?: return null; val d = dish?.let { it.toDomain() ?: return null }; return PlanDay(day, d) }
  internal fun ApiWeekPlan.toDomain(): WeekPlan? {
      val start = parseDate(weekStart) ?: return null
      val mapped = days.mapAllOrNull { it.toDomain() } ?: return null
      return if (mapped.size == DAYS_PER_WEEK) WeekPlan(start, mapped) else null
  }
  internal fun DishDraft.toApi() = SaveDishRequest(date.toString(), cookId, name, ingredients)
  ```
- **MIRROR**: REPOSITORY_PATTERN, ERROR_HANDLING, TEST_STRUCTURE.
- **IMPORTS**: `de.familyplanner.api.client.{MealsApi, MembersApi}`, `de.familyplanner.api.model.{Dish as ApiDish, PlanDay as ApiPlanDay, WeekPlan as ApiWeekPlan, Role as ApiRole, SaveDishRequest}`,
  `de.familyplanner.android.core.data.{ApiResult, apiCall, apiCallNoContent}`, `de.familyplanner.android.feature.meals.data.{parseDate, mapAllOrNull}`.
- **GOTCHA**: Namenskonflikt Domäne/API (`Dish`, `PlanDay`, `WeekPlan`) über `import … as Api…` (wie `ApiWeekday` in 2b).
  `Member.role` ist `@Contextual` — klappt mit `createJson()` (2b-Test belegt das). Weniger/mehr als 7 Tage → Serverfehler,
  nicht halbe Woche. `DishDraft` wird **bereinigt** gesendet (Name getrimmt, Zutaten aus `ingredientLines`) — das macht
  der Use Case, nicht der Mapper.
- **VALIDATE**: `REQ-MEAL-001 week plan maps seven days with dish or null`, `REQ-MEAL-001 week plan requests ISO monday path`
  (`/api/v1/meals/weeks/2026-10-12/plan`), `REQ-MEAL-001 week with six days is a server error`, `REQ-MEAL-001 unreadable dish date is a server error`,
  `REQ-MEAL-032 members map parent flag`, `REQ-MEAL-032 create dish posts date cook name and ingredients` (Body per
  `json.decodeFromString<SaveDishRequest>` vergleichen), `REQ-MEAL-032 update dish uses dish path and PUT`,
  `REQ-MEAL-032 delete dish uses dish path and 204`, `REQ-MEAL-032 taken day maps 409 to conflict with server message`
  („An diesem Tag ist schon ein Gericht geplant“), `REQ-MEAL-032 child receives forbidden on create` (403 → `AppError.Forbidden`),
  `REQ-MEAL-032 validation error keeps server message` (400), `network failure maps to Network` (Server schließen).

### Task 5: Daten – Cache (TDD mit echtem DataStore)
- **ACTION**: `T/data/DataStorePlanCacheTest.kt` (RED) → `domain/plan/PlanCache.kt`, `data/plan/StoredPlan.kt`,
  `data/plan/PlanCacheSerializer.kt`, `data/plan/DataStorePlanCache.kt`; `feature/meals/build.gradle.kts` um
  `implementation(libs.datastore)` ergänzen.
- **IMPLEMENT**:
  ```kotlin
  // domain/plan/PlanCache.kt
  interface PlanCache {
      /** Gespeicherter Stand der Woche für dieses Mitglied, sonst `null`. */
      suspend fun read(memberId: String, weekStart: LocalDate): WeekPlanSnapshot?
      /** Speichert [week]; räumt Wochen vor [today]s Vorwoche ab und behält höchstens [PlanLimits.CACHED_WEEKS_MAX]. */
      suspend fun write(memberId: String, week: WeekPlanSnapshot, today: LocalDate)
  }
  // data/plan/StoredPlan.kt — Datei-Format, getrennt von der Domäne
  @Serializable data class StoredPlanCache(val version: Int = CACHE_VERSION, val memberId: String? = null, val weeks: List<StoredWeek> = emptyList())
  @Serializable data class StoredWeek(val weekStart: String, val syncedAtMillis: Long, val days: List<StoredDay>)
  @Serializable data class StoredDay(val date: String, val dish: StoredDish? = null, val cookName: String? = null)
  @Serializable data class StoredDish(val id: String, val cookId: String, val name: String, val ingredients: List<String>)
  internal const val CACHE_VERSION = 1
  internal fun WeekPlanSnapshot.toStored(): StoredWeek
  internal fun StoredWeek.toDomain(): WeekPlanSnapshot?   // parseDate; unlesbar → null
  // data/plan/PlanCacheSerializer.kt
  class PlanCacheSerializer(private val json: Json) : Serializer<StoredPlanCache> {
      override val defaultValue = StoredPlanCache()
      override suspend fun readFrom(input: InputStream): StoredPlanCache {
          val bytes = input.readBytes()
          if (bytes.isEmpty()) return defaultValue
          return try {
              json.decodeFromString<StoredPlanCache>(bytes.decodeToString()).takeIf { it.version == CACHE_VERSION } ?: defaultValue
          } catch (e: SerializationException) { Log.w(TAG, "Meal plan cache is malformed, starting empty", e); defaultValue }
            catch (e: IllegalArgumentException) { Log.w(TAG, "Meal plan cache is malformed, starting empty", e); defaultValue }
      }
      override suspend fun writeTo(t: StoredPlanCache, output: OutputStream) = output.write(json.encodeToString(t).encodeToByteArray())
  }
  // data/plan/DataStorePlanCache.kt
  class DataStorePlanCache(private val dataStore: DataStore<StoredPlanCache>) : PlanCache {
      override suspend fun read(memberId: String, weekStart: LocalDate): WeekPlanSnapshot? =
          try {
              val stored = dataStore.data.first()
              if (stored.memberId != memberId) null else stored.weeks.firstOrNull { it.weekStart == weekStart.toString() }?.toDomain()
          } catch (e: IOException) { Log.w(TAG, "Meal plan cache unreadable", e); null }
      override suspend fun write(memberId: String, week: WeekPlanSnapshot, today: LocalDate) {
          val oldest = mondayOf(today).minusWeeks(1)
          try {
              dataStore.updateData { current ->
                  val others = if (current.memberId == memberId) current.weeks.filter { it.weekStart != week.weekStart.toString() } else emptyList()
                  val kept = (others + week.toStored())
                      .filter { (parseDate(it.weekStart) ?: LocalDate.MIN) >= oldest }
                      .sortedByDescending { it.syncedAtMillis }
                      .take(PlanLimits.CACHED_WEEKS_MAX)
                  StoredPlanCache(memberId = memberId, weeks = kept)
              }
          } catch (e: IOException) { Log.w(TAG, "Meal plan cache not written", e) }
      }
  }
  // PlanLimits: const val CACHED_WEEKS_MAX = 8
  ```
- **MIRROR**: CACHE_PATTERN, LOGGING_PATTERN.
- **IMPORTS**: `androidx.datastore.core.{DataStore, Serializer}`, `kotlinx.coroutines.flow.first`, `kotlinx.serialization.{Serializable, SerializationException}`,
  `kotlinx.serialization.json.Json`, `android.util.Log`, `java.io.{InputStream, OutputStream, IOException}`.
- **GOTCHA**: Cache-Fehler sind **nie** ein Nutzerfehler: lesen → `null`, schreiben → nur loggen (Plan wurde ja geladen).
  Der Serializer bekommt das Koin-`Json` (`ignoreUnknownKeys = true`) — damit bleibt eine spätere Format-Erweiterung lesbar.
  `CancellationException` nicht fangen (nur `IOException`/`SerializationException`/`IllegalArgumentException`).
  Im Test pro Test einen eigenen `CoroutineScope(Job())` an `DataStoreFactory.create(…, scope = scope)` geben und am Ende
  `cancel()` — zwei aktive DataStores auf derselben Datei werfen `IllegalStateException` (Muster `DataStoreSessionStoreTest`).
  `Log` ist in JVM-Tests durch `unitTests.isReturnDefaultValues = true` (Convention-Plugin) unkritisch.
- **VALIDATE**: `REQ-MEAL-002 written week can be read back with sync time`, `REQ-MEAL-002 cache survives a new store instance`,
  `REQ-MEAL-002 cache of another member is not shown`, `REQ-MEAL-002 writing as another member replaces the cache`,
  `REQ-MEAL-002 weeks before last week are dropped`, `REQ-MEAL-002 at most eight weeks are kept`,
  `REQ-MEAL-002 rewriting a week replaces it`, `REQ-MEAL-002 malformed cache file reads as empty`
  (Datei vorher mit `"{kaputt"` beschreiben), `REQ-MEAL-002 unknown cache version reads as empty`.

### Task 6: Domäne – Use Cases (TDD mit Fakes)
- **ACTION**: `T/fakes/PlanFakes.kt` + `T/domain/plan/UseCasesTest.kt` (RED) → `domain/plan/UseCases.kt`.
- **IMPLEMENT**:
  ```kotlin
  private const val UNKNOWN_NAME = "?"
  /** Konnektivitätsprobleme, bei denen der gespeicherte Stand gezeigt wird (ADR-0011). */
  private fun AppError.allowsCachedPlan() = this == AppError.Network || this == AppError.Certificate || this == AppError.Server

  class LoadWeekPlanUseCase(private val repository: MealPlanRepository, private val cache: PlanCache,
                            private val sessions: SessionStore, private val clock: Clock = Clock.system(BerlinZone)) {
      suspend operator fun invoke(weekStart: LocalDate): WeekPlanLoad {
          val memberId = sessions.current?.memberId ?: return WeekPlanLoad.Failed(AppError.Unauthorized)
          return when (val result = loadRemote(weekStart)) {       // parallel week + members (SERVICE_PATTERN)
              is ApiResult.Success -> {
                  cache.write(memberId, result.data, clock.instant().atZone(BerlinZone).toLocalDate())
                  WeekPlanLoad.Fresh(result.data)
              }
              is ApiResult.Failure -> {
                  val cached = if (result.error.allowsCachedPlan()) cache.read(memberId, weekStart) else null
                  if (cached != null) WeekPlanLoad.Cached(cached, result.error) else WeekPlanLoad.Failed(result.error)
              }
          }
      }
      // loadRemote: WeekPlanSnapshot(plan.weekStart, plan.days.map { PlannedDay(it.date, it.dish, it.dish?.let { d -> names[d.cookId] ?: UNKNOWN_NAME }) }, clock.instant())
  }
  data class DishEditorData(val week: WeekPlan, val parents: List<PlanMember>)
  class LoadDishEditorUseCase(private val repository: MealPlanRepository) {
      suspend operator fun invoke(weekStart: LocalDate): ApiResult<DishEditorData>  // parallel week + members, parents = members.filter { it.isParent }
  }
  class SaveDishUseCase(private val repository: MealPlanRepository) {
      suspend operator fun invoke(dishId: String?, date: LocalDate, cookId: String, name: String, ingredientsText: String): ApiResult<Dish> {
          validateDish(name, ingredientsText)?.let { return it }
          val draft = DishDraft(date, cookId, name.trim(), ingredientLines(ingredientsText))
          return if (dishId == null) repository.createDish(draft) else repository.updateDish(dishId, draft)
      }
  }
  class DeleteDishUseCase(private val repository: MealPlanRepository) { suspend operator fun invoke(dishId: String) = repository.deleteDish(dishId) }
  /** Bündelt die Editor-Use-Cases (hält die ViewModel-Parameterliste kurz). */
  class DishUseCases(val load: LoadDishEditorUseCase, val save: SaveDishUseCase, val delete: DeleteDishUseCase)
  ```
  Fakes: `FakeMealPlanRepository` mit `var weekResult: (LocalDate) -> ApiResult<WeekPlan>`, `membersResult`,
  `createResult`/`updateResult`/`deleteResult`, Mitschnitte `requestedWeeks`, `created`, `updated`, `deleted`,
  `weekGates` (CompletableDeferred, wie `FakeAttendanceRepository`); `InMemoryPlanCache` (Map `memberId to weekStart → Snapshot`,
  `writes`-Mitschnitt). Fixtures: `thisWeek = 2026-10-12`, `parentAnna = PlanMember("p-1","Anna",true)`,
  `parentTom = PlanMember("p-2","Tom",true)`, `child = PlanMember("c-1","Ben",false)`, `weekPlanOf(start, vararg dishes)`
  (7 Tage, Gerichte an ihrem Datum), `someDish = Dish("d-1", 2026-10-12, "p-1", "Spaghetti", listOf("Nudeln","Hackfleisch"))`.
  Sessions aus `fakes/Fakes.kt` (`parentSession` = p-1 Anna, `childSession` = c-1 Ben) wiederverwenden.
- **MIRROR**: SERVICE_PATTERN, `ExceptionUseCases`.
- **IMPORTS**: `kotlinx.coroutines.{async, coroutineScope}`, `java.time.Clock`, `de.familyplanner.android.core.data.session.SessionStore`.
- **GOTCHA**: Cache **nur** bei Verbindungsproblemen; bei `Forbidden`/`Validation`/`NotFound`/`Unauthorized` den Fehler zeigen
  (sonst verdeckt der Cache echte Fehler; 401 meldet ohnehin ab). Der Zeitstempel `syncedAt` kommt aus der `Clock` des
  Use Cases (testbar), nicht vom Server. Mitglieder-Request scheitert, Plan klappt → trotzdem `Failure` (wie
  `LoadWeekOverviewUseCase`) — sonst landen „?“-Namen im Cache.
- **VALIDATE**: `REQ-MEAL-001 week plan joins cook names`, `REQ-MEAL-001 unknown cook shows question mark`,
  `REQ-MEAL-002 loaded week is written to the cache with sync time`, `REQ-MEAL-002 offline returns cached week with error`,
  `REQ-MEAL-002 certificate and server errors also fall back to the cache`, `REQ-MEAL-002 offline without cache fails`,
  `REQ-MEAL-002 forbidden does not fall back to the cache`, `REQ-MEAL-002 failed member request is not cached`,
  `REQ-MEAL-032 editor offers only parents as cooks`, `REQ-MEAL-032 invalid dish is not sent`,
  `REQ-MEAL-032 new dish is created with trimmed name and cleaned ingredients`, `REQ-MEAL-032 existing dish is updated`,
  `REQ-MEAL-032 delete passes the dish id`.

### Task 7: UI – Essensplan (alle Mitglieder) (TDD)
- **ACTION**: `T/ui/PlanViewModelsTest.kt` + `T/ui/PlanScreensTest.kt` (RED) → `ui/plan/WeekPlanViewModel.kt`, `ui/plan/WeekPlanScreen.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  data class WeekPlanUiState(
      val weekStart: LocalDate,
      val today: LocalDate,
      val week: WeekPlanSnapshot? = null,
      val isLoading: Boolean = false,
      val error: AppError? = null,
      /** Gezeigt wird ein gespeicherter Stand (REQ-MEAL-002). */
      val isOffline: Boolean = false,
      val isParent: Boolean = false,
  ) {
      val canEdit: Boolean get() = isParent && !isOffline && week != null
      val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
      val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
  }
  sealed interface WeekPlanEvent { data object Refresh; data object PreviousWeek; data object NextWeek }
  class WeekPlanViewModel(sessions: SessionStore, private val loadWeekPlan: LoadWeekPlanUseCase, clock: Clock = Clock.system(BerlinZone)) : ViewModel()
  ```
  - Start: `weekStart = currentWeek(clock.instant())`, `today = clock.instant().atZone(BerlinZone).toLocalDate()`,
    `isParent = sessions.current?.role == Role.PARENT`; `init { load(); prefetch(weekStart.plusWeeks(1)) }`.
  - `prefetch(week)`: `viewModelScope.launch { loadWeekPlan(week) }` — Ergebnis wird **nicht** angezeigt (nur Cache-Effekt),
    eigener Job, nicht `loadJob` (sonst bricht der Hauptladevorgang ab).
  - `load()`/`switchWeek()` exakt wie `WeekOverviewViewModel` (Job abbrechen, späte Antworten verwerfen, `week = null` beim Wechsel).
    Ergebnis: `Fresh` → `week, isOffline=false, error=null`; `Cached` → `week, isOffline=true, error=null` (Hinweis kommt aus `isOffline`);
    `Failed` → `error`, `week` bleibt `null`, `isOffline=false`.
  - Screen (`WeekPlanScreen(state, onEvent, onOpenDay: (LocalDate, String?) -> Unit, onBack)`):
    `PullToRefreshBox` + `LazyColumn`; Kopf ‹ „Woche 12.10.–18.10.“ › (Strings `week_title`, `week_previous`, `week_next` aus 2b wiederverwenden);
    bei `isOffline`: Hinweis „Offline – Stand: %1$s“ mit `formatDeadline(week.syncedAt)` („So 18.10., 12:00“) in `tertiary`;
    `ErrorText(state.error)`; je Tag eine Zeile/Card `PlanDayRow`:
    „Mo 12.10.“ (`weekdayShort` + `formatDate`), bei `date == today` zusätzlich „Heute“ (fett, `primary`);
    mit Gericht: Name (`titleMedium`), darunter „%1$s kocht“ (Koch-Name) und – falls Zutaten – `ingredients.joinToString(", ")`
    (`bodySmall`, `maxLines = 2`, `TextOverflow.Ellipsis`); ohne Gericht: „noch offen“ (`onSurfaceVariant`).
    `canEdit`: Zeile klickbar (`Modifier.clickable(onClickLabel = …)`) → `onOpenDay(date, dish?.id)`, offene Tage zusätzlich mit „+ Gericht“;
    Eltern offline: Hinweis „Bearbeiten ist nur im Heim-WLAN möglich.“. Unten `TextButton` „Zurück“.
  - Route: `WeekPlanRoute(onOpenDay, onBack)` mit `LifecycleResumeEffect(Unit) { viewModel.onEvent(WeekPlanEvent.Refresh); onPauseOrDispose {} }`
    (Rückkehr aus dem Editor lädt neu).
- **MIRROR**: VIEWMODEL_PATTERN, `WeekOverviewScreen.kt` (Kopf, PullToRefresh), Format-Strings statt Leerzeichen am Rand.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.attendance.{currentWeek, AttendanceLimits}`, `de.familyplanner.android.feature.meals.ui.{formatDeadline, weekdayShort}`,
  `de.familyplanner.android.core.ui.{BerlinZone, formatDate}`, `androidx.compose.foundation.clickable`, `androidx.compose.ui.text.style.TextOverflow`,
  `androidx.lifecycle.compose.LifecycleResumeEffect`.
- **GOTCHA**: Android-String-Ressourcen trimmen Leerzeichen am Rand (2b-Report) → „%1$s kocht“ als Format-String.
  `init`-Laden + `LifecycleResumeEffect` lädt beim ersten Öffnen zweimal; `load()` bricht den ersten Job ab — akzeptiert
  (gleich wie `ProfilesRoute`), im ViewModel-Test nur `init` prüfen. Prefetch der nächsten Woche **nicht** in
  `requestedWeeks`-Erwartungen vergessen (`[thisWeek, thisWeek+1]`). „Heute“ hängt an `today` aus der Clock, nicht an
  `LocalDate.now()` (testbar). `Cached`-Fehler nicht zusätzlich als `ErrorText` zeigen — der Offline-Hinweis reicht.
- **VALIDATE**: ViewModel: `REQ-MEAL-001 starts at this week and prefetches next week`, `REQ-MEAL-001 on sunday next week is one step away`
  (Clock So 2026-10-18 12:00 → `weekStart` 12.10., `NextWeek` → 19.10.), `REQ-MEAL-001 next and previous week reload`,
  `late response of previous week is ignored`, `REQ-MEAL-002 offline with cache shows cached week as offline`,
  `REQ-MEAL-002 offline without cache shows network error`, `REQ-MEAL-002 refresh after coming back online clears offline`,
  `REQ-MEAL-032 only online parents can edit`.
  Screens: `REQ-MEAL-001 day with dish shows name and cook`, `REQ-MEAL-001 open day shows noch offen`, `REQ-MEAL-001 today is marked`,
  `REQ-MEAL-001 week title and navigation`, `REQ-MEAL-002 offline hint shows sync time in Berlin time`,
  `REQ-MEAL-032 parent taps open day to add dish` (Callback mit Datum und `null`), `REQ-MEAL-032 parent taps planned day to edit` (Callback mit `d-1`),
  `REQ-MEAL-032 child cannot open the editor`, `REQ-MEAL-032 offline parent sees edit hint`, `network error is shown in German`.

### Task 8: UI – Gericht-Editor (Eltern) (TDD)
- **ACTION**: Tests in `PlanViewModelsTest`/`PlanScreensTest` (RED) → `ui/dish/DishEditViewModel.kt`, `ui/dish/DishEditScreen.kt`.
- **IMPLEMENT**:
  ```kotlin
  data class DishEditUiState(
      val dishId: String?,
      val weekStart: LocalDate,
      val date: LocalDate,
      val name: String = "",
      val ingredientsText: String = "",
      val cookId: String? = null,
      val parents: List<PlanMember> = emptyList(),
      /** Tage dieser Woche mit einem anderen Gericht — nicht wählbar (Server: 409). */
      val takenDates: Set<LocalDate> = emptySet(),
      val isLoading: Boolean = true,
      val loadError: AppError? = null,
      val isBusy: Boolean = false,
      val error: AppError? = null,
      val showDeleteConfirm: Boolean = false,
      /** Gespeichert oder gelöscht → Route geht zurück. */
      val isDone: Boolean = false,
  ) {
      val isNew: Boolean get() = dishId == null
      val canSave: Boolean get() = !isLoading && loadError == null && cookId != null && name.isNotBlank() && !isBusy
  }
  sealed interface DishEditEvent {
      data class NameChanged(val value: String); data class IngredientsChanged(val value: String)
      data class DateSelected(val date: LocalDate); data class CookSelected(val memberId: String)
      data object Save; data object RequestDelete; data object CancelDelete; data object ConfirmDelete; data object Retry
  }
  class DishEditViewModel(date: LocalDate, private val dishId: String?, sessions: SessionStore, private val dishes: DishUseCases) : ViewModel()
  ```
  - Laden (`init` + `Retry`): `dishes.load(mondayOf(date))`; bestehendes Gericht per `dishId` aus `week.days` suchen —
    nicht gefunden → `loadError = AppError.NotFound`; sonst Felder vorbelegen (`ingredients.joinToString("\n")`).
    Neu: `cookId = session.memberId`, falls unter `parents`, sonst `parents.firstOrNull()?.id`.
    `takenDates` = Tage mit Gericht, deren `dish.id != dishId`.
  - Eingaben: Name auf `PlanLimits.NAME_MAX` kürzen (`take`), Felder ändern → `error = null`.
  - `Save`: `isBusy`-Sperre; `dishes.save(dishId, date, cookId, name, ingredientsText)`; Erfolg → `isDone = true`; Fehler → `error`.
  - `RequestDelete`/`CancelDelete`/`ConfirmDelete`: `ConfirmDialog` („Gericht löschen?“, „Der Tag ist danach wieder offen.“, „Löschen“);
    Erfolg → `isDone`; `NotFound` (schon gelöscht) → ebenfalls `isDone` (Plan lädt neu, Ziel erreicht).
  - Screen: Titel „Gericht planen“ (neu) bzw. „Gericht ändern“; Tag-Auswahl als `FilterChip`-Reihe der 7 Tage
    (`weekdayShort`, `enabled = date !in takenDates`, `contentDescription` „Mo 12.10.“ bzw. „… (belegt)“);
    Koch/Köchin per `DropdownMenu` (Eltern-Namen); `OutlinedTextField` Name (`singleLine`); `OutlinedTextField` Zutaten
    (`minLines = 4`, Label „Zutaten (eine pro Zeile, optional)“); `ErrorText(error)`; `SubmitButton` „Speichern“
    (`enabled = canSave`, `loading = isBusy`); bei bestehendem Gericht `OutlinedButton` „Löschen“; `TextButton` „Abbrechen“.
    `loadError` → `ErrorText` + „Erneut versuchen“.
  - Route: `DishEditRoute(date: LocalDate, dishId: String?, onDone: () -> Unit)`; `koinViewModel<DishEditViewModel> { parametersOf(date, dishId) }`;
    `LaunchedEffect(state.isDone) { if (state.isDone) onDone() }`.
- **MIRROR**: VIEWMODEL_PATTERN (`ProfileEditViewModel` mit `isBusy`), `ConfirmDialog`, `DropdownMenu` aus `DeadlineSettingsScreen.kt:122-130`,
  `FilterChip` aus `ProfileEditScreen.kt:136-140`.
- **IMPORTS**: `androidx.compose.material3.{FilterChip, DropdownMenu, DropdownMenuItem, OutlinedTextField, OutlinedButton}`,
  `de.familyplanner.android.core.ui.components.{ConfirmDialog, ErrorText, SubmitButton}`, `org.koin.core.parameter.parametersOf`.
- **GOTCHA**: Koin-`parametersOf(date, dishId)` mit nullable `dishId`: in Koin 4 `params.getOrNull<String>()` für den
  zweiten Parameter verwenden bzw. `viewModel { params -> DishEditViewModel(params[0], params.getOrNull(), get(), get()) }`
  — `params.get<String>()` wirft bei `null`. Eltern ohne Zugriff (Rolle geändert) → 403 „Nur für Eltern.“ im `error`.
  Ein Gericht eines inzwischen gelöschten Elternteils: `cookId` nicht unter `parents` → Dropdown zeigt „Bitte wählen“,
  `canSave = false`, bis gewählt. 409 trotz `takenDates` (anderes Gerät war schneller) → Servermeldung, Formular bleibt.
  Formular-Composables in Sektionen zerlegen (`DayChips`, `CookPicker`, `DishFields`) — detekt `LongMethod` (< 60 Zeilen).
- **VALIDATE**: ViewModel: `REQ-MEAL-032 new dish preselects own parent as cook and the tapped day`,
  `REQ-MEAL-032 existing dish fills the form`, `REQ-MEAL-032 days with other dishes are taken`,
  `REQ-MEAL-032 saving new dish creates it and finishes`, `REQ-MEAL-032 saving existing dish updates it and finishes`,
  `REQ-MEAL-032 taken day conflict keeps form with server message`, `REQ-MEAL-032 invalid name shows validation message`,
  `REQ-MEAL-032 forbidden save shows parents only`, `REQ-MEAL-032 deleting requires confirmation`,
  `REQ-MEAL-032 deleted dish finishes`, `deleting an already deleted dish finishes`, `unknown dish id shows not found`,
  `double save sends once` (`isBusy`), `retry after failed load loads again`.
  Screens: `REQ-MEAL-032 editor shows fields and save`, `REQ-MEAL-032 taken day chip is disabled`,
  `REQ-MEAL-032 delete is offered only for existing dishes`, `REQ-MEAL-032 conflict message is shown`.

### Task 9: Navigation, Home-Einstieg & Koin
- **ACTION**: Routen und Graph ergänzen, `mealsModule` erweitern, `app` anpassen.
- **IMPLEMENT**:
  ```kotlin
  // MealsNavigation.kt
  @Serializable data object WeekPlanRoute
  /** [date] als ISO-Datum (`yyyy-MM-dd`); [dishId] `null` = neues Gericht. */
  @Serializable data class DishEditRoute(val date: String, val dishId: String? = null)

  @Composable
  fun MealsHomeActions(onOpenPlan: () -> Unit, onOpenWeek: () -> Unit) {
      Button(onClick = onOpenPlan, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_plan)) }
      OutlinedButton(onClick = onOpenWeek, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_attendance)) }
  }
  // in mealsGraph:
  composable<WeekPlanRoute> {
      WeekPlanRoute(onOpenDay = { date, dishId -> navController.navigate(DishEditRoute(date.toString(), dishId)) }, onBack = { navController.popBackStack() })
  }
  composable<DishEditRoute> { entry ->
      val route = entry.toRoute<DishEditRoute>()
      DishEditRoute(date = LocalDate.parse(route.date), dishId = route.dishId, onDone = { navController.popBackStack() })
  }
  // MealsModule.kt, Abschnitt // plan
  single<MealPlanRepository> { RemoteMealPlanRepository(get(), get(), get()) }
  single<PlanCache> {
      DataStorePlanCache(DataStoreFactory.create(serializer = PlanCacheSerializer(get()), produceFile = { androidContext().dataStoreFile(PLAN_CACHE_FILE) }))
  }
  factory { LoadWeekPlanUseCase(get(), get(), get()) }
  factory { DishUseCases(LoadDishEditorUseCase(get()), SaveDishUseCase(get()), DeleteDishUseCase(get())) }
  viewModel { WeekPlanViewModel(get(), get()) }
  viewModel { params -> DishEditViewModel(params[0], params.getOrNull(), get(), get()) }
  // private const val PLAN_CACHE_FILE = "meal_plan_cache.json"
  ```
  - `app/AppNavHost.kt` `MainHost`: `mainGraph(navController) { MealsHomeActions(onOpenPlan = { navController.navigate(WeekPlanRoute) }, onOpenWeek = { navController.navigate(WeekOverviewRoute) }) }`.
  - `app/build.gradle.kts`: `versionCode = 3`, `versionName = "0.3.0"`.
  - `AppNavHostTest`: neuer Test `with session the home screen offers the meal plan` („Essensplan“ sichtbar).
  - `MealsModuleTest`: `platform` um `single<PlanCache> { InMemoryPlanCache() }` ergänzen **nach** `mealsModule` laden
    (`modules(platform, mealsModule, testOverrides)`), damit kein Android-`Context` nötig ist; zusätzlich
    `koin.get<WeekPlanViewModel>()` und `koin.get<DishEditViewModel> { parametersOf(LocalDate.of(2026, 10, 12), null) }`.
- **MIRROR**: NAMING_CONVENTION, bestehender `mealsGraph`/`mealsModule`, Kommentar-Gruppen `// attendance` / `// plan`.
- **IMPORTS**: `androidx.datastore.core.DataStoreFactory`, `androidx.datastore.dataStoreFile`, `org.koin.android.ext.koin.androidContext`, `java.time.LocalDate`.
- **GOTCHA**: `androidx.datastore.dataStoreFile` liegt im Android-Artefakt `datastore` (wie in `NetworkModule.kt:4`).
  Koin 4 erlaubt Overrides standardmäßig; der Test-Override muss **nach** `mealsModule` stehen. `feature:meals` bleibt ohne
  Abhängigkeit zu `feature:auth` (ADR-0009). `MealsHomeActions` liegt in einer `Column` mit `spacedBy(16.dp)` (HomeScreen),
  zwei Buttons nebeneinander im Slot sind daher untereinander — gewünscht. Kover schließt `*Route*` und `*Module*` aus —
  Screens/ViewModels müssen getestet sein.
- **VALIDATE**: `./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest` grün (inkl. `MealsModuleTest`, `AppNavHostTest`).

### Task 10: Qualität & Coverage
- **ACTION**: CI-Kette lokal.
- **IMPLEMENT**: ktlint/detekt-Funde beheben (Funktionen < 60 Zeilen, Dateien < 400 Zeilen, `TooManyFunctions` → Mapper/Composables
  auf Dateien verteilen wie in 2b); Kover ≥ 80 % (Aggregation in `app`).
- **MIRROR**: `familyplanner.quality`-Konvention; 2b-Report („Mapper-Split“, „Use-Case-Bündel“).
- **IMPORTS**: –
- **GOTCHA**: `LongParameterList` beim `DishEditViewModel` vermeiden (Bündel `DishUseCases`). `ktlintFormat` vor `ktlintCheck`.
- **VALIDATE**: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` grün.

### Task 11: Doku, Traceability, PRD
- **ACTION**: V-Modell-Dokumente nachziehen.
- **IMPLEMENT**:
  - `docs/03-design/meals.md`: Einleitung „Die Eltern-UI … Phase 2b“ um „Essensplan und Offline-Cache: Abschnitt
    ‚App-Seite (Phase 3b)‘“ ergänzen; im 2b-Abschnitt Zeile „**Kein Cache:** …“ ersetzen durch „Die Wochenübersicht hat
    keinen Cache; offline lesbar ist der Essensplan (Phase 3b).“; neuer Abschnitt **„App-Seite (Phase 3b)“**: Pakete
    `domain/plan`, `data/plan`, `ui/plan`, `ui/dish`; Screens/Rollen-Tabelle (Essensplan alle, Gericht-Editor Eltern);
    Regeln: Startwoche = diese Woche (`currentWeek`), Prefetch nächste Woche, Cache nach ADR-0011 (Besitzer, Aufräumen,
    Fallback nur bei `Network`/`Certificate`/`Server`, „Offline – Stand: …“), offline keine Bearbeitung, Editor nur Tage
    derselben Woche, belegte Tage gesperrt, Zutaten optional, Grenzen wie Server, Fehlerabbildung (409/403/404).
  - `docs/04-verification/traceability.md`: REQ-MEAL-001 Unit + „App: `PlanWeeksTest`, `UseCasesTest`, `PlanViewModelsTest`,
    `PlanScreensTest`“, Integration + „App: `RemoteMealPlanRepositoryTest`“, E2E „manuell: Abnahme Phase 3b (android/README.md)“,
    Status → `umgesetzt (Backend + App)`; REQ-MEAL-002 Unit „App: `DataStorePlanCacheTest`, `UseCasesTest`, `PlanViewModelsTest`,
    `PlanScreensTest`“, E2E manuell, Status `umgesetzt (App)`; REQ-MEAL-032 analog, Status `umgesetzt (Backend + App; Einkaufsliste in Phase 5)`.
    Nach Geräte-Abnahme: „abgenommen (… ) am <Datum>“.
  - `docs/01-requirements/meals.prd.md`, Zeile 3: Status `in-progress` → **`complete`**; PRP-Spalte „3b: … (Plan erstellt)“ ersetzen durch
    „3b: `.claude/PRPs/plans/completed/wochenplan-elterngerichte-android.plan.md` (umgesetzt, Report: `.claude/PRPs/reports/wochenplan-elterngerichte-android-report.md`)“.
  - `android/README.md`: Modulbeschreibung `feature:meals` um „Essensplan (offline lesbar)“ ergänzen; Abschnitt
    „Abnahme-Checkliste Phase 3b“ (siehe Manual Validation).
  - Plan nach `.claude/PRPs/plans/completed/` verschieben (macht `/prp-implement`).
- **MIRROR**: Bestehende Abschnitte/Zeilen; eigene Zeilen je Phase, keine fremden Zeilen umformatieren.
- **IMPORTS**: –
- **GOTCHA**: `grep -rn "App folgt in 3b\|Phase 3b)" docs/` danach prüfen — alte Hinweise auf „folgt in 3b“ müssen weg.
- **VALIDATE**: Links zeigen auf existierende Dateien; ADR-0011 ist in `meals.md` verlinkt.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `currentWeek` | `2026-10-18T21:30:00Z` (So 23:30 Berlin) | 2026-10-12 | ✔ |
| `currentWeek` | `2026-10-18T22:30:00Z` (Mo 00:30 Berlin) | 2026-10-19 | ✔ |
| `validateDish` | Name 61 Zeichen | „Der Name darf höchstens 60 Zeichen haben.“ | ✔ |
| `validateDish` | 31 Zutaten | „Höchstens 30 Zutaten je Gericht.“ | ✔ |
| `validateDish` | 30 Zutaten + 70 Leerzeilen | gültig | ✔ |
| `validateDish` | 101 Rohzeilen | „Höchstens 30 Zutaten je Gericht.“ | ✔ |
| `ingredientLines` | `" Reis \n\n Erbsen"` | `["Reis","Erbsen"]` | |
| Mapper WeekPlan | 6 Tage | `AppError.Server` | ✔ |
| Mapper Members | `role: PARENT/CHILD` | `isParent` true/false | |
| Cache | anderes Mitglied | `null` | ✔ |
| Cache | 10 Wochen geschrieben | 8 neueste behalten | ✔ |
| Cache | Woche vor der Vorwoche | entfernt | ✔ |
| Cache | Datei `{kaputt` | leer, kein Absturz | ✔ |
| `LoadWeekPlanUseCase` | `Network` + Cache | `Cached(snapshot, Network)` | ✔ |
| `LoadWeekPlanUseCase` | `Forbidden` + Cache | `Failed(Forbidden)` | ✔ |
| Offline-Hinweis | `syncedAt = 2026-10-18T10:00:00Z` | „Offline – Stand: So 18.10., 12:00“ | ✔ |
| Startwoche | Clock So 18.10.2026 12:00 Berlin | 12.10.; › → 19.10. | ✔ |
| Editor neu | Elternteil Anna tippt Di 13.10. | `cookId = p-1`, `date = 13.10.` | |
| Editor | Gericht an Mo, Bearbeitung Mi-Gericht | Mo-Chip deaktiviert, Mi aktiv | ✔ |
| 409 | `CONFLICT` „An diesem Tag ist schon ein Gericht geplant“ | Meldung, Formular bleibt | ✔ |
| 403 | `FORBIDDEN` | „Nur für Eltern.“ | ✔ |

### Edge Cases Checklist
- [x] Leere Eingabe (Name leer → Meldung; Zutaten leer → erlaubt)
- [x] Maximale Eingabe (60/80/30/100)
- [x] Ungültige Typen (unlesbare Server-Daten, kaputte Cache-Datei, unbekannte Cache-Version)
- [x] Nebenläufigkeit (Wochenwechsel während Laden, Prefetch parallel, Doppelklick Speichern, 409 durch anderes Gerät)
- [x] Netzwerkfehler (offline mit/ohne Cache, Zertifikat, Serverfehler)
- [x] Keine Berechtigung (Kind → keine Bearbeitung; 403 trotzdem abgebildet)
- [x] Zeitzone (Sonntag-/Montag-Grenze, „Heute“, Sync-Zeit in Berlin)
- [x] Mitgliederwechsel auf dem Gerät (Cache eines anderen Mitglieds wird nicht gezeigt)
- [x] Gelöschtes Mitglied als Koch/Köchin („?“ im Plan, im Editor neu wählen)

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
EXPECT: Essensplan lädt, Gericht anlegen/ändern/verschieben/löschen funktioniert, 409 bei belegtem Tag sichtbar;
Backend stoppen → Plan zeigt „Offline – Stand: …“.

### Manual Validation (Abnahme Phase 3b – echte Handys gegen den Pi)
- [ ] Release-APK 0.3.0 (gleiche Signatur) per `adb install -r` auf Elternhandy und einem Kinderhandy; Kopplung bleibt erhalten
- [ ] Home zeigt „Essensplan“ und „Anwesenheit & Fristen“
- [ ] Eltern: für die kommende Woche an mehreren Tagen Gerichte mit/ohne Zutaten anlegen, eines ändern, eines auf einen anderen Tag verschieben, eines löschen
- [ ] Belegter Tag ist im Editor nicht wählbar; zweites Elternhandy legt zeitgleich am selben Tag an → Meldung „An diesem Tag ist schon ein Gericht geplant“
- [ ] Kinderhandy: Plan dieser Woche mit Gericht und Koch/Köchin je Tag, offene Tage „noch offen“, „Heute“ markiert; am Sonntag mit › zur kommenden Woche; keine Bearbeitung möglich
- [ ] Kinderhandy im WLAN öffnen, dann WLAN aus (mobile Daten an): Plan dieser **und** der nächsten Woche mit „Offline – Stand: …“ sichtbar
- [ ] Elternhandy offline: Plan lesbar, Hinweis „Bearbeiten ist nur im Heim-WLAN möglich.“
- [ ] Zurück im WLAN + Pull-to-Refresh: Hinweis verschwindet, aktueller Stand
- [ ] Nachtrag 2b: WLAN aus → Wochenübersicht zeigt „Server nicht erreichbar. Bist du im Heim-WLAN?“ (offener Punkt der 2b-Checkliste)

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-001, REQ-MEAL-002, REQ-MEAL-032 haben App-Tests mit REQ-ID im Namen
- [ ] Alle sehen den Plan; nur Eltern (online) bearbeiten
- [ ] Offline erscheint der zuletzt synchronisierte Stand mit Zeitpunkt
- [ ] 400/403/404/409/offline mit deutschen Meldungen abgebildet
- [ ] ADR-0011 geschrieben; `api/` und `backend/` unverändert
- [ ] Geräte-Abnahme dokumentiert (README-Checkliste, Datum in traceability.md)

## Completion Checklist
- [ ] Code folgt den Mustern aus `feature:meals` (UiState/Event, `ApiResult`, Fakes, Use-Case-Bündel)
- [ ] Fehlerbehandlung über `ApiResult`/`AppError`; Logging nur im Cache (`Log.w`, ohne Inhalte)
- [ ] Tests folgen Testmustern (MockWebServer, Robolectric, DataStore mit `TemporaryFolder`)
- [ ] Keine hartcodierten Grenzen außerhalb `PlanLimits`, keine Texte außerhalb `strings.xml`/Validation
- [ ] Moduldesign, Traceability, PRD, android/README aktualisiert
- [ ] Keine Abhängigkeit `feature:meals` ↔ `feature:auth`; keine neue Bibliothek

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Zwei DataStore-Instanzen auf derselben Datei (`IllegalStateException`) | M | H — Absturz | Koin-`single`; im Test eigener Scope je Store und `cancel()` |
| Cache zeigt veralteten Stand, ohne dass es auffällt | M | M | Hinweis „Offline – Stand: …“ immer bei `Cached`; Fallback nur bei Verbindungsproblemen |
| Prefetch und Hauptladen überschreiben sich gegenseitig | L | M | Getrennte Jobs; Cache schreibt je Woche, `updateData` ist atomar |
| Kinder erwarten Offline-Daten, öffnen die App aber nie im WLAN | M | M | Abnahme-Punkt; Hintergrund-Sync folgt mit Phase 6 (Erinnerungen) |
| detekt `LongMethod`/`TooManyFunctions` im Editor | M | L | Sektionen-Composables, Dateien aufteilen (wie 2b) |
| Koin-Parameter mit `null` (`dishId`) | M | L | `params.getOrNull()`; `MealsModuleTest` baut den Editor mit `null` |
| Update-Installation scheitert an Signatur | L | M | Gleicher Keystore wie 0.2.0, `versionCode = 3` |

## Notes
- Heute (So 2026-10-18) ist „diese Woche“ 2026-10-12 und die Planwoche 2026-10-19 — gut für den Sonntag-Fall in REQ-MEAL-001.
- Die API hat kein Einzel-GET für ein Gericht: der Editor lädt die Woche und sucht das Gericht (wie der Profil-Editor aus 2b).
- Namen von Koch/Köchin kommen aus `GET /api/v1/members` (alle Mitglieder dürfen lesen) und werden im Cache mitgespeichert,
  damit der Plan offline vollständig ist.
- Der Essensplan ersetzt die Wochenübersicht (Anwesenheit) nicht; eine Zusammenführung entscheidet Phase 4 (Wochenstatus).
- Erfahrungen nach Umsetzung im Report festhalten (`.claude/PRPs/reports/wochenplan-elterngerichte-android-report.md`).
