# Plan: Einreichung & Freigabe – App (Phase 4b)

## Summary
`feature:meals` bekommt den Kochtag-Prozess: Das kochende Kind sieht im Essensplan der Planwoche eine Karte „Mein Kochtag“
(Frist, Status, Ablehnungsgrund), reicht dort sein Gericht nur für Anwesenheitstage ein, ändert es oder sagt mit Begründung ab.
Eltern sehen oben im Essensplan den Wochenstatus (offene Tage, Einreichungen mit Frist, Freigaben, Absagen, „bereit zur
Bestellung“), geben Kindergerichte im Gericht-Screen frei oder lehnen mit Begründung ab, tragen stellvertretend ein und sagen
für ein Kind ab. Ein Gericht mit `dayValid=false` (Kind an dem Tag nicht da) können Eltern **nur verschieben oder löschen**.
`api/` und `backend/` bleiben unverändert (Vertrag 0.4.0 aus 4a).

## User Story
Als kochendes Kind möchte ich mein Gericht samt Zutaten in der App einreichen (oder absagen) und sehen, ob es freigegeben ist,
und als Elternteil möchte ich auf einen Blick sehen, was vor der Bestellung fehlt, und Kindergerichte freigeben, damit die
Woche ohne WhatsApp-Abstimmung „bereit zur Bestellung“ wird.

## Problem → Solution
Phase 4a liefert Einreichung, Freigabe, Absage und Wochenstatus nur über die API; die App kennt nur Elterngerichte (alle Gerichte
sehen gleich aus, Kinder können nichts tun) → Status-Badges im Plan, Wochenstatus-Karte, „Mein Kochtag“ für Kinder, Kind-Modus
im Editor, Freigabe/Ablehnung, Stellvertretung, Absage, Verschieben-/Löschen-Modus bei ungültigem Tag. Danach ist PRD-Phase 4
abgeschlossen.

## Metadata
- **Complexity**: Large (≈ 14 neue Dateien, ≈ 20 geänderte; kein neues Architekturmuster)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 4 – Einreichung & Freigabe, Teil **4b** (App), REQ-MEAL-020, -021, -022, -023, -030, -031, -033
- **Estimated Files**: ≈ 14 neu, ≈ 20 geändert (inkl. Tests und Doku)

## Entscheidungen des Anwenders (2026-10-18)
| Frage | Entscheidung |
|---|---|
| Wo steht der Wochenstatus? | **Karte oben im Essensplan** (Planwoche und später), für alle sichtbar, Aktionen nur für Eltern; kein eigener Screen |
| Wie reicht ein Kind ein? | **Karte „Mein Kochtag“** im Essensplan: Frist, Status, Begründung, „Gericht einreichen/ändern“, „Kochen absagen“; Editor nur mit Anwesenheitstagen, Zutaten Pflicht |
| Wo geben Eltern frei? | **Im Gericht-Screen**: „Freigeben“ / „Ablehnen…“ (Dialog mit Begründung), darunter Ändern/Löschen |
| `dayValid=false` | Eltern können **nur verschieben (auf einen Anwesenheitstag des Kindes) oder löschen**; Name, Zutaten, Koch gesperrt; Freigeben/Ablehnen ausgeblendet. Verschieben durch Eltern macht das Gericht „freigegeben“ (Server-Regel 4a) — **akzeptiert**, keine API-Änderung |

---

## UX Design

### Before
```
┌ Essensplan ───────────────────────────────┐      ┌ Gericht (nur Eltern) ──────────────┐
│ ‹  Woche 19.10.–25.10.  ›                 │      │ Tag, Koch/Köchin (nur Eltern),      │
│ Mo 19.10.  Spaghetti · Anna kocht         │ ──▶  │ Name, Zutaten optional,             │
│ Di 20.10.  Curry · Ben kocht              │      │ Speichern, Löschen                  │
│   (kein Status, kein „Tag ungültig“)      │      └─────────────────────────────────────┘
│ Kinder: nur lesen; Einreichen per WhatsApp│
└───────────────────────────────────────────┘
```

### After
```
Kind (Ben, kocht), Planwoche:                         Eltern, Planwoche:
┌ Essensplan ───────────────────────────────┐        ┌ Essensplan ────────────────────────────────┐
│ ‹  Woche 19.10.–25.10.  ›                 │        │ ‹  Woche 19.10.–25.10.  ›                  │
│ ┌ Mein Kochtag ─────────────────────────┐ │        │ ┌ Wochenstatus ───────────────────────────┐ │
│ │ Einreichen bis Do 15.10., 16:00       │ │        │ │ Bestellfrist So 18.10., 16:00           │ │
│ │ Abgelehnt: „Bitte mit Gemüse“         │ │        │ │ Offen: Fr 23.10., Sa 24.10.             │ │
│ │ [ Gericht überarbeiten ]              │ │        │ │ Ben – wartet auf Freigabe · nach Frist ›│ │
│ │ ( Kochen absagen )                    │ │        │ │ Lea – noch nicht eingereicht,           │ │
│ └───────────────────────────────────────┘ │        │ │       bis Do 15.10., 16:00  [Eintragen] │ │
│ ┌ Wochenstatus (nur lesen) ─────────────┐ │        │ │       [Absagen…]                        │ │
│ └───────────────────────────────────────┘ │        │ │ Tim – abgesagt: „krank“ [Zurücknehmen]  │ │
│ Mo 19.10. Spaghetti · Anna kocht          │        │ └─────────────────────────────────────────┘ │
│ Di 20.10. Curry · Ben kocht  [abgelehnt]  │        │ Di 20.10. Curry · Ben  [wartet auf Freigabe]│
│ Mi 21.10. Pizza · Lea  ⚠ Tag ungültig     │        │ Mi 21.10. Pizza · Lea  ⚠ Tag ungültig       │
└───────────────────────────────────────────┘        └─────────────────────────────────────────────┘
Diese Woche (vor der Planwoche): Knopf „Zur Planwoche 19.10.–25.10. ›“ unter dem Kopf.
Offline: Badges aus dem Cache, keine Karten, Hinweis „Wochenstatus nur im Heim-WLAN.“

Gericht-Screen, Eltern, Kindergericht „wartet auf Freigabe“:   Gericht-Screen, Eltern, dayValid=false:
┌ Gericht prüfen ───────────────────────────┐   ┌ Gericht verschieben ─────────────────────────┐
│ Ben hat eingereicht · nach Frist          │   │ ⚠ Lea ist am Mi 21.10. nicht da. Gericht auf │
│ [ Freigeben ]  ( Ablehnen… )              │   │   einen anderen Tag verschieben oder löschen.│
│ ─────────────                             │   │ Tag: [Di][Mi✕][Do] (nur Leas Anwesenheitstage)│
│ Tag, Koch/Köchin, Name, Zutaten …         │   │ Koch/Köchin: Lea      (gesperrt)             │
│ „Speichern gibt das Gericht frei.“        │   │ Name: Pizza           (gesperrt)             │
│ [ Speichern ]  ( Löschen )                │   │ Zutaten: …            (gesperrt)             │
└───────────────────────────────────────────┘   │ [ Verschieben ] (erst nach Tageswahl) (Löschen)│
                                                └──────────────────────────────────────────────┘
Gericht-Screen, Kind:
┌ Gericht einreichen ───────────────────────┐
│ Du kochst · Einreichen bis Do 15.10.,16:00│
│ (Frist vorbei → „wird als ‚nach Frist‘    │
│  markiert“)                               │
│ Tag: [Di][Mi][Do] (nur eigene Anwesenheit)│
│ Name, Zutaten (eine pro Zeile, Pflicht)   │
│ [ Einreichen ]                            │
└───────────────────────────────────────────┘
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Plan-Zeile | Name, Koch, Zutaten | + Badge „wartet auf Freigabe“ / „abgelehnt“ / „nach Frist“; „⚠ Tag ungültig“ (`error`-Farbe) | für alle, auch offline (aus dem Cache) |
| Plan-Kopf (diese Woche) | – | „Zur Planwoche … ›“ | nur wenn `weekStart < currentPlanWeek` |
| Wochenstatus | nur API | Karte ab Planwoche, online; Eltern: je Koch „Eintragen“ (Stellvertretung), „Absagen…“, „Zurücknehmen“, Zeile antippen = Gericht öffnen | REQ-MEAL-033, -031, -023 |
| Mein Kochtag | – | Karte fürs kochende Kind: Frist, Status, Begründung, Einreichen/Ändern, Absagen/Zurücknehmen | REQ-MEAL-020/022/023/030 |
| Gericht-Screen Eltern | nur Elternköche | + kochende Kinder als Koch (Anwesenheitstage, Zutaten Pflicht); Freigeben/Ablehnen bei `SUBMITTED`; Verschieben-Modus bei `dayValid=false` | REQ-MEAL-021/030/031 |
| Gericht-Screen Kind | – | eigener Modus (Koch fest, nur eigene Anwesenheitstage, kein Löschen, Fristhinweis) | REQ-MEAL-020/021/022 |
| Fehler | 409 Tag belegt | + 409 „Für diese Woche ist schon ein Gericht eingereicht“, „Das Kochen für diese Woche ist abgesagt“, „Das Gericht wartet nicht auf Freigabe“; 400 „Das Kind ist an diesem Tag nicht da“ | Servermeldungen über `ErrorText` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/03-design/meals.md` | Abschnitte „Einreichung & Freigabe (Phase 4)“, „App-Seite (Phase 3b)“ | Fachregeln, Statusübergänge, `dayValid`, App-Regeln |
| P0 | `api/openapi.yaml` | 340–495, 969–1131 | `getWeekStatus`, `cancelCooking`, `withdrawCookingCancellation`, `approveDish`, `rejectDish`; Schemas `Dish` (+`status`,`late`,`dayValid`,`rejectionReason`), `CookStatus`, `WeekStatus` (**nicht ändern**) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/MealPlanService.kt` | 95–158 | Kind-Regeln (Zutaten Pflicht, Anwesenheit, Woche, Status beim Speichern) und Servermeldungen |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/{DishReviewService,CookingWeekService,WeekStatus,ChildCooking}.kt` | all | Freigabe nur aus `SUBMITTED` (409), Absage-Ziel, Zustand je Koch |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/{domain,data}/plan/*.kt` | all | Bestehende Plan-Domäne, Mapper, Cache |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/{plan,dish}/*.kt` | all | Screens/ViewModels, die erweitert werden |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/attendance/{Models,AttendanceRepository,PlanWeeks}.kt` | all | `RawChildWeek`, `week(weekStart)`, `currentPlanWeek` |
| P1 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/{fakes,data,ui,domain}/` | alle | Fakes (`FakeMealPlanRepository`, `FakeAttendanceRepository`, `parentSession`/`childSession`), MockWebServer, Robolectric |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt`, `MealsNavigation.kt` | all | Koin-Parameter, Routen |
| P2 | `android/core/ui/src/main/kotlin/de/familyplanner/android/core/ui/ErrorMessages.kt` | 1–35 | Servermeldung gewinnt bei `Validation`/`Conflict` |
| P2 | `android/core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` | 46, 66, 203, 254, 329 | Generierte Signaturen |
| P2 | `android/README.md` | 83–end | Form der Abnahme-Checkliste |

## External Documentation

Keine Recherche nötig — nur etablierte interne Muster (Retrofit, Koin, Compose Material3, Turbine, Robolectric, MockWebServer,
DataStore). **Keine neue Bibliothek.**

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:26-31
/** [date] als ISO-Datum (`yyyy-MM-dd`); [dishId] `null` = neues Gericht. */
@Serializable
data class DishEditRoute(
    val date: String,
    val dishId: String? = null,
)
```
Pakete `domain/<bereich>`, `data/<bereich>`, `ui/<screen>`. Neu: `domain/cooking`, `data/cooking` (Wochenstatus, Freigabe,
Absage). Use Cases `VerbNounUseCase` mit `suspend operator fun invoke`, Bündel `XxxUseCases`. Strings mit Präfix `status_*`,
`cooking_*`, `review_*`, `dish_*`, `plan_*`. Domänen-/API-Namenskonflikte über `import … as Api…`.

### ERROR_HANDLING
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/RemoteMealPlanRepository.kt:22-40
override suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan> =
    apiCall(json, { meals.getWeekPlan(weekStart.toString()) }) { it.data?.toDomain() }
override suspend fun deleteDish(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.deleteDish(dishId) }

// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Validation.kt:9-24
internal fun validateDish(name: String, ingredientsText: String): ApiResult.Failure? {
    …
    return when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        …
        else -> null
    }
}
```
Mapper liefern `null` bei unlesbaren Daten → `AppError.Server`. Client-Validierung vor dem Request mit `validationFailure("…")`,
Anzeige über `ErrorText(error)`; Servermeldung gewinnt bei `Validation`/`Conflict`. **Kein neuer `AppError`-Typ.**

### LOGGING_PATTERN
Netzwerkfehler loggt `apiCall` zentral; im Feature wird nichts zusätzlich geloggt (einzige Ausnahme bleibt der Cache,
`DataStorePlanCache.kt:26-28`, `Log.w(TAG, "…", e)`). In 4b **kein neues Logging**.

### REPOSITORY_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/RemoteMealPlanRepository.kt:17-28
class RemoteMealPlanRepository(
    private val meals: MealsApi,
    private val memberApi: MembersApi,
    private val json: Json,
) : MealPlanRepository {
    override suspend fun members(): ApiResult<List<PlanMember>> =
        apiCall(json, { memberApi.listMembers() }) { response ->
            response.data?.map { PlanMember(it.id, it.name, it.role == ApiRole.PARENT) }
        }
```

### SERVICE_PATTERN (Use Case, parallele Requests)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt:58-71
class LoadDishEditorUseCase(private val repository: MealPlanRepository) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<DishEditorData> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val members = async { repository.members() }
            val weekResult = week.await()
            val membersResult = members.await()
            if (weekResult !is ApiResult.Success) return@coroutineScope weekResult as ApiResult.Failure
            if (membersResult !is ApiResult.Success) return@coroutineScope membersResult as ApiResult.Failure
            ApiResult.Success(DishEditorData(weekResult.data, membersResult.data.filter { it.isParent }))
        }
}
/** Bündelt die Editor-Use-Cases (hält die ViewModel-Parameterliste kurz). */
class DishUseCases(val load: LoadDishEditorUseCase, val save: SaveDishUseCase, val delete: DeleteDishUseCase)
```

### VIEWMODEL_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanViewModel.kt:88-100
private fun load() {
    loadJob?.cancel()
    val requested = _state.value.weekStart
    _state.update { it.copy(isLoading = true, error = null, today = clock.instant().atZone(BerlinZone).toLocalDate()) }
    loadJob =
        viewModelScope.launch {
            val result = loadWeekPlan(requested)
            // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
            if (_state.value.weekStart != requested) return@launch
            _state.update { it.withResult(result) }
        }
}
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/dish/DishEditViewModel.kt:127-138 (isBusy-Sperre)
private fun save() {
    val current = _state.value
    val cookId = current.cookId
    if (!current.canSave || cookId == null) return
    _state.update { it.copy(isBusy = true, error = null) }
    viewModelScope.launch { … }
}
```
Screens: `PullToRefreshBox` + `LazyColumn` (`WeekPlanScreen.kt:64-86`), `ConfirmDialog` aus `core:ui`, `DropdownMenu`
(`DishEditScreen.kt:133-157`), `FilterChip`-Reihe mit `contentDescription` (`DishEditScreen.kt:108-131`), Neuladen per
`LifecycleResumeEffect` (`WeekPlanScreen.kt:48-52`). Begründungs-Dialoge nach `ui/profile/ExceptionDialogs.kt` (AlertDialog mit
`OutlinedTextField`).

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt:61-63
private val dishJson =
    """{"id":"d-1","date":"2026-10-12","cookId":"p-1","name":"Spaghetti","ingredients":["Nudeln","Hackfleisch"],""" +
        """"status":"APPROVED","late":false,"dayValid":true}"""
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/Fakes.kt:23-24
val parentSession = Session("tok", "p-1", "Anna", Role.PARENT, "d-1")
val childSession = Session("tok", "c-1", "Ben", Role.CHILD, "d-2")
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/PlanFakes.kt:15-23
val thisWeek: LocalDate = LocalDate.of(2026, 10, 12)
val childBen = PlanMember("c-1", "Ben", false)
```
ViewModel-Tests mit `MainDispatcherRule`, `Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))`
(Sa 17.10. → diese Woche 12.10., Planwoche 19.10.). Screens mit `RobolectricTestRunner`, `createComposeRule()`,
`FamilyPlannerTheme { … }`; untere Elemente mit `assertExists`/`performScrollTo`. Testnamen mit REQ-ID:
`` `REQ-MEAL-030 parent approves submitted dish`() ``.

---

## Files to Change

`M` = `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals`,
`T` = `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals`.

| File | Action | Justification |
|---|---|---|
| `M/domain/plan/Models.kt` | UPDATE | `DishStatus`; `Dish` + `status`, `rejectionReason`, `late`, `dayValid` (mit Defaults); `CookCandidate`; `DishEditorData` erweitert |
| `M/domain/plan/PlanLimits.kt` | UPDATE | `REASON_MAX = 200` |
| `M/domain/plan/Validation.kt` | UPDATE | `validateDish(…, ingredientsRequired)`, `validateReason` |
| `M/domain/plan/UseCases.kt` | UPDATE | `LoadDishEditorUseCase` (+ Anwesenheit), `SaveDishUseCase` (+ Zutatenpflicht), `DishUseCases` (+ `review`) |
| `M/domain/plan/EditorRules.kt` | CREATE | Reine Funktionen: `allowedDates`, `editorMode`, Vorauswahl — testbar ohne ViewModel |
| `M/domain/cooking/Models.kt` | CREATE | `CookState`, `CookStatus`, `WeekStatus`, `CookOverview`, `StatusOverview` |
| `M/domain/cooking/CookingRepository.kt` | CREATE | `status`, `approve`, `reject`, `cancel`, `withdraw` |
| `M/domain/cooking/UseCases.kt` | CREATE | `LoadWeekStatusUseCase`, `ReviewDishUseCase`, `CancelCookingUseCase`, `WithdrawCancellationUseCase`, `CookingUseCases` |
| `M/data/plan/Mappers.kt` | UPDATE | neue Dish-Felder |
| `M/data/plan/StoredPlan.kt` | UPDATE | `StoredDish` + Felder mit Defaults (alte Cache-Datei bleibt lesbar) |
| `M/data/cooking/Mappers.kt` | CREATE | `ApiWeekStatus`/`ApiCookStatus` → Domäne |
| `M/data/cooking/RemoteCookingRepository.kt` | CREATE | `MealsApi` |
| `M/ui/plan/WeekPlanViewModel.kt` | UPDATE | Status laden, Absage-Dialog, Planwoche, Rollen |
| `M/ui/plan/WeekPlanScreen.kt` | UPDATE | Badges, „Zur Planwoche“, Karten einbinden, `onOpenEditor(DishEditArgs)` |
| `M/ui/plan/StatusCards.kt` | CREATE | `WeekStatusCard`, `MyCookingCard` |
| `M/ui/plan/ReasonDialog.kt` | CREATE | Begründungs-Dialog (Absage + Ablehnung, wiederverwendet) |
| `M/ui/dish/DishEditViewModel.kt` | UPDATE | Modi (Eltern/Kind/Prüfen/Verschieben), Freigeben/Ablehnen |
| `M/ui/dish/DishEditScreen.kt` | UPDATE | Modi, Review-Sektion, Hinweise |
| `M/ui/dish/DishEditSections.kt` | CREATE | `ReviewSection`, `MoveBanner`, `CookLine` (detekt `LongMethod`/Dateigröße) |
| `M/ui/dish/DishEditArgs.kt` | CREATE | ein Koin-Parameter statt drei Strings |
| `M/MealsNavigation.kt` | UPDATE | `DishEditRoute(date, dishId?, cookId?)` |
| `M/MealsModule.kt` | UPDATE | Abschnitt `// cooking`, neue Use Cases, Editor-Parameter |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | `status_*`, `cooking_*`, `review_*`, `dish_*`, `plan_badge_*` |
| `T/fakes/CookingFakes.kt` | CREATE | `FakeCookingRepository`, Fixtures (`statusOf`, `cookStatus`) |
| `T/fakes/PlanFakes.kt` | UPDATE | Kindergericht-Fixtures, `FakeMealPlanRepository` unverändert kompatibel |
| `T/domain/plan/ValidationTest.kt`, `UseCasesTest.kt` | UPDATE | Zutatenpflicht, Begründung, Editor-Daten |
| `T/domain/plan/EditorRulesTest.kt` | CREATE | erlaubte Tage, Modi |
| `T/domain/cooking/UseCasesTest.kt` | CREATE | Status mit Namen, Review, Absage |
| `T/data/RemoteMealPlanRepositoryTest.kt` | UPDATE | neue Dish-Felder, unbekannter Status |
| `T/data/RemoteCookingRepositoryTest.kt` | CREATE | MockWebServer: Pfade, 204, 409 |
| `T/data/DataStorePlanCacheTest.kt` | UPDATE | Status-Felder überleben; alte Datei ohne Felder lesbar |
| `T/ui/CookingViewModelsTest.kt`, `T/ui/CookingScreensTest.kt` | CREATE | Karten, Absage, Editor-Modi (eigene Dateien, die 3b-Dateien sind schon groß) |
| `T/ui/PlanViewModelsTest.kt`, `PlanScreensTest.kt` | UPDATE | angepasste Konstruktoren/Callbacks, keine neuen Fälle dort |
| `T/MealsModuleTest.kt` | UPDATE | neue Bindungen, Editor mit `DishEditArgs` |
| `android/app/build.gradle.kts` | UPDATE | `versionCode = 4`, `versionName = "0.4.0"` |
| `android/README.md` | UPDATE | Modulbeschreibung, Abnahme-Checkliste Phase 4b |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 4b)“; „die App folgt in 4b“ ersetzen |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-020…023, 030, 031, 033 App-Tests, Status |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 4 → `complete` nach Umsetzung |

## NOT Building

- Keine Änderung an `api/openapi.yaml` oder `backend/`. Fehlt etwas im Vertrag: stoppen und nachfragen.
- Keine Benachrichtigungen bei Freigabe/Ablehnung (REQ-MEAL-052) und keine Erinnerungen (REQ-MEAL-050/051) — Phase 6.
- Keine Einkaufsliste (Phase 5); „Zutaten stehen auf der Einkaufsliste“ aus REQ-MEAL-030/-023 kommt dort.
- Kein Offline-Cache für den Wochenstatus und kein Offline-Schreiben; offline sind nur Plan und Badges lesbar.
- Keine Sperre für Kinder nach Fristablauf (Entscheidung ADR-0007) — nur Hinweis und „nach Frist“.
- Kein Löschen durch Kinder (Server 403) — Kinder sagen ab.
- Kein Verschieben in eine andere Woche; Editor bleibt bei Tagen derselben Woche.
- Kein eigener Wochenstatus-Screen, keine Zusammenführung mit der Wochenübersicht „Anwesenheit & Fristen“.
- Keine serverseitige Sperre der Freigabe bei `dayValid=false` (Server erlaubt sie; die App bietet sie nicht an, siehe Risks).

---

## Step-by-Step Tasks

### Task 0: Branch & Ausgangslage
- **ACTION**: Branch `feat/einreichung-freigabe-android` von aktuellem `main` (enthält 4a, Merge PR #11); Ausgangslage grün.
- **IMPLEMENT**: nichts.
- **MIRROR**: –
- **IMPORTS**: –
- **GOTCHA**: Der generierte Client muss 0.4.0 sein: `approveDish`, `rejectDish`, `getWeekStatus`, `cancelCooking`,
  `withdrawCookingCancellation`; sonst `./gradlew :core:api:openApiGenerate`. `Dish.status` ist im generierten Modell
  `@Contextual` — funktioniert mit `createJson()` (Fallback-Serializer, wie `Member.role`).
- **VALIDATE**: `cd android && ./gradlew :core:api:openApiGenerate :feature:meals:testDebugUnitTest` grün;
  `grep -n "getWeekStatus\|rejectDish" core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` findet beide.

### Task 1: Domäne Plan – Dish-Status, Validierung (TDD)
- **ACTION**: `T/domain/plan/ValidationTest.kt` erweitern (RED) → `Models.kt`, `PlanLimits.kt`, `Validation.kt`.
- **IMPLEMENT**:
  ```kotlin
  // Models.kt
  enum class DishStatus { SUBMITTED, APPROVED, REJECTED }
  data class Dish(
      val id: String, val date: LocalDate, val cookId: String, val name: String, val ingredients: List<String>,
      val status: DishStatus = DishStatus.APPROVED,
      val rejectionReason: String? = null,
      /** Vom Kind nach seiner Frist gespeichert (REQ-MEAL-020). */
      val late: Boolean = false,
      /** `false`: Das kochende Kind ist an dem Tag nicht (mehr) da (REQ-MEAL-021). */
      val dayValid: Boolean = true,
  )
  // PlanLimits: const val REASON_MAX = 200   // REASON_MAX im Backend (Begründung Absage/Ablehnung)
  // Validation.kt
  internal fun validateDish(name: String, ingredientsText: String, ingredientsRequired: Boolean = false): ApiResult.Failure? {
      … bestehende Fälle …
      // nach Name, vor Länge/Anzahl der Zutaten — Reihenfolge wie MealPlanService.childDraft (Name → Zutaten → Pflicht)
      ingredientsRequired && lines.isEmpty() -> validationFailure("Bitte mindestens eine Zutat angeben.")
  }
  internal fun validateReason(reason: String): ApiResult.Failure? =
      when {
          reason.isBlank() -> validationFailure("Bitte eine Begründung angeben.")
          reason.trim().length > PlanLimits.REASON_MAX -> validationFailure("Die Begründung darf höchstens ${PlanLimits.REASON_MAX} Zeichen haben.")
          else -> null
      }
  ```
- **MIRROR**: ERROR_HANDLING.
- **IMPORTS**: unverändert.
- **GOTCHA**: Defaults im `Dish` halten alle 3b-Tests und Fakes kompilierbar (`someDish` bleibt Elterngericht `APPROVED`).
  Server prüft Zutatenpflicht erst nach Zeilenbereinigung — die App ebenso (`lines`, nicht `rawLines`).
- **VALIDATE**: `REQ-MEAL-020 child dish without ingredients is rejected`, `REQ-MEAL-020 child dish with blank lines only is rejected`,
  `REQ-MEAL-032 parent dish without ingredients stays allowed`, `REQ-MEAL-023 blank reason is rejected`,
  `REQ-MEAL-023 reason longer than 200 characters is rejected`, `REQ-MEAL-030 reason of 200 characters after trimming is accepted`.

### Task 2: Daten Plan – Mapper & Cache (TDD)
- **ACTION**: `T/data/RemoteMealPlanRepositoryTest.kt`, `T/data/DataStorePlanCacheTest.kt` erweitern (RED) → `data/plan/Mappers.kt`, `StoredPlan.kt`.
- **IMPLEMENT**:
  ```kotlin
  // data/plan/Mappers.kt
  internal fun ApiDishStatus.toDomain(): DishStatus = DishStatus.valueOf(name)
  internal fun ApiDish.toDomain(): Dish? =
      parseDate(date)?.let { Dish(id, it, cookId, name, ingredients, status.toDomain(), rejectionReason, late, dayValid) }
  // data/plan/StoredPlan.kt
  @Serializable
  data class StoredDish(
      val id: String, val cookId: String, val name: String, val ingredients: List<String>,
      // Defaults: Cache-Dateien aus 3b (ohne diese Felder) bleiben lesbar; dort gab es nur Elterngerichte.
      val status: String = "APPROVED",
      val rejectionReason: String? = null,
      val late: Boolean = false,
      val dayValid: Boolean = true,
  )
  // toStored: status = it.status.name, …; toDomain: DishStatus.entries.firstOrNull { s -> s.name == it.status } ?: return null
  ```
- **MIRROR**: ERROR_HANDLING (unlesbar → `null`), CACHE-Format aus 3b.
- **IMPORTS**: `de.familyplanner.api.model.DishStatus as ApiDishStatus`, `de.familyplanner.android.feature.meals.domain.plan.DishStatus`.
- **GOTCHA**: `CACHE_VERSION` bleibt **1** — die Erweiterung ist abwärtskompatibel (Defaults + `ignoreUnknownKeys`). Ein
  unbekannter Status-Text im Cache macht den Tag und damit die Woche unlesbar (`mapAllOrNull` → `null`, Woche gilt als nicht
  gespeichert), kein Absturz. `StoredDay.toDomain` ist `private` und gibt `PlannedDay?` zurück — Status-Parsing dort einbauen.
  Fixture `dishJson` im Test gibt es schon mit allen Pflichtfeldern (4a-Task 10).
- **VALIDATE**: `REQ-MEAL-020 dish maps status late and day validity`, `REQ-MEAL-030 rejected dish maps reason`,
  `REQ-MEAL-021 invalid day maps dayValid false`; Cache: `REQ-MEAL-002 dish status survives the cache`,
  `REQ-MEAL-002 cache file from phase 3b without status is still readable` (JSON ohne die neuen Felder in die Datei schreiben),
  `REQ-MEAL-002 unknown dish status in cache reads the week as missing`.

### Task 3: Domäne & Daten Cooking – Wochenstatus, Freigabe, Absage (TDD gegen MockWebServer)
- **ACTION**: `T/data/RemoteCookingRepositoryTest.kt` (RED) → `domain/cooking/Models.kt`, `domain/cooking/CookingRepository.kt`,
  `data/cooking/Mappers.kt`, `data/cooking/RemoteCookingRepository.kt`.
- **IMPLEMENT**:
  ```kotlin
  // domain/cooking/Models.kt
  enum class CookState { NOT_SUBMITTED, SUBMITTED, APPROVED, REJECTED, CANCELLED }
  data class CookStatus(
      val memberId: String, val submissionDeadline: Instant?, val state: CookState, val dishId: String?,
      val late: Boolean, val dayValid: Boolean, val cancellationReason: String?,
  )
  data class WeekStatus(
      val weekStart: LocalDate, val orderDeadline: Instant, val readyToOrder: Boolean,
      val openDays: List<LocalDate>, val cooks: List<CookStatus>,
  )
  /** Koch mit aufgelöstem Namen („?“, wenn unbekannt). */
  data class CookOverview(val status: CookStatus, val name: String)
  data class StatusOverview(
      val weekStart: LocalDate, val orderDeadline: Instant, val readyToOrder: Boolean,
      val openDays: List<LocalDate>, val cooks: List<CookOverview>,
  )
  // domain/cooking/CookingRepository.kt
  interface CookingRepository {
      suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus>
      suspend fun approve(dishId: String): ApiResult<Unit>
      suspend fun reject(dishId: String, reason: String): ApiResult<Unit>
      suspend fun cancel(weekStart: LocalDate, memberId: String, reason: String): ApiResult<Unit>
      suspend fun withdraw(weekStart: LocalDate, memberId: String): ApiResult<Unit>
  }
  // data/cooking/RemoteCookingRepository.kt
  class RemoteCookingRepository(private val meals: MealsApi, private val json: Json) : CookingRepository {
      override suspend fun status(weekStart: LocalDate) = apiCall(json, { meals.getWeekStatus(weekStart.toString()) }) { it.data?.toDomain() }
      override suspend fun approve(dishId: String) = apiCallNoContent(json) { meals.approveDish(dishId) }
      override suspend fun reject(dishId: String, reason: String) = apiCallNoContent(json) { meals.rejectDish(dishId, RejectDishRequest(reason)) }
      override suspend fun cancel(weekStart: LocalDate, memberId: String, reason: String) =
          apiCall(json, { meals.cancelCooking(weekStart.toString(), memberId, CancelCookingRequest(reason)) }) { r -> r.data?.let { } }
      override suspend fun withdraw(weekStart: LocalDate, memberId: String) =
          apiCallNoContent(json) { meals.withdrawCookingCancellation(weekStart.toString(), memberId) }
  }
  // data/cooking/Mappers.kt
  internal fun ApiCookStatus.toDomain(): CookStatus? {
      val deadline = submissionDeadline?.let { parseInstant(it) ?: return null }
      return CookStatus(memberId, deadline, CookState.valueOf(state.name), dishId, late, dayValid, cancellationReason)
  }
  internal fun ApiWeekStatus.toDomain(): WeekStatus? {
      val start = parseDate(weekStart) ?: return null
      val deadline = parseInstant(orderDeadline) ?: return null
      val days = openDays.mapAllOrNull { parseDate(it) } ?: return null
      val mapped = cooks.mapAllOrNull { it.toDomain() } ?: return null
      return WeekStatus(start, deadline, readyToOrder, days, mapped)
  }
  ```
- **MIRROR**: REPOSITORY_PATTERN, ERROR_HANDLING, TEST_STRUCTURE (`RemoteMealPlanRepositoryTest`: `enqueue`, `ok`, `error`, `server.takeRequest()`).
- **IMPORTS**: `de.familyplanner.api.client.MealsApi`, `de.familyplanner.api.model.{RejectDishRequest, CancelCookingRequest, CookStatus as ApiCookStatus, WeekStatus as ApiWeekStatus}`,
  `de.familyplanner.android.core.data.{ApiResult, apiCall, apiCallNoContent}`, `de.familyplanner.android.feature.meals.data.{parseDate, parseInstant, mapAllOrNull}`.
- **GOTCHA**: `cancel` antwortet **200 mit Body** (`CookingCancellationResponse`), nicht 204 → `apiCall` mit Mapper `{ r -> r.data?.let { } }`
  (`Unit` bei vorhandenem Body, sonst Serverfehler); `approve`/`reject`/`withdraw` sind 204 → `apiCallNoContent`. Begründung
  **getrimmt** senden (macht der Use Case). Namenskonflikte (`CookStatus`, `WeekStatus`, `CookState`) per `as Api…`.
  `submissionDeadline` ist bei nicht mehr kochenden Kindern mit Gericht `null` — gültig, kein Fehler.
- **VALIDATE**: `REQ-MEAL-033 status maps open days cooks and ready flag`, `REQ-MEAL-033 status requests ISO monday path`
  (`/api/v1/meals/weeks/2026-10-19/status`), `REQ-MEAL-033 unreadable deadline is a server error`,
  `REQ-MEAL-033 cook without deadline maps to null deadline`, `REQ-MEAL-030 approve posts to approval path and accepts 204`,
  `REQ-MEAL-030 reject posts reason`, `REQ-MEAL-030 approving a reviewed dish maps 409 with server message`
  („Das Gericht wartet nicht auf Freigabe“), `REQ-MEAL-023 cancel puts reason to cancellation path`,
  `REQ-MEAL-023 withdraw deletes cancellation and accepts 204`, `REQ-MEAL-023 withdraw without cancellation maps 404`,
  `REQ-MEAL-023 child cancelling for a sibling receives forbidden`.

### Task 4: Domäne – Use Cases Cooking & Editor-Daten (TDD mit Fakes)
- **ACTION**: `T/fakes/CookingFakes.kt`, `T/domain/cooking/UseCasesTest.kt`, `T/domain/plan/UseCasesTest.kt` (RED) →
  `domain/cooking/UseCases.kt`, `domain/plan/Models.kt` (`CookCandidate`, `DishEditorData`), `domain/plan/UseCases.kt`.
- **IMPLEMENT**:
  ```kotlin
  // domain/cooking/UseCases.kt
  class LoadWeekStatusUseCase(private val cooking: CookingRepository, private val plan: MealPlanRepository) {
      // parallel status + members (SERVICE_PATTERN); unbekannter Name → "?"
      suspend operator fun invoke(weekStart: LocalDate): ApiResult<StatusOverview>
  }
  class ReviewDishUseCase(private val cooking: CookingRepository) {
      suspend fun approve(dishId: String): ApiResult<Unit> = cooking.approve(dishId)
      suspend fun reject(dishId: String, reason: String): ApiResult<Unit> {
          validateReason(reason)?.let { return it }
          return cooking.reject(dishId, reason.trim())
      }
  }
  class CancelCookingUseCase(private val cooking: CookingRepository) {
      suspend operator fun invoke(weekStart: LocalDate, memberId: String, reason: String): ApiResult<Unit>  // validateReason, trim
  }
  class WithdrawCancellationUseCase(private val cooking: CookingRepository) {
      suspend operator fun invoke(weekStart: LocalDate, memberId: String) = cooking.withdraw(weekStart, memberId)
  }
  /** Bündelt Status und Absage für den Essensplan (hält die ViewModel-Parameterliste kurz). */
  class CookingUseCases(val status: LoadWeekStatusUseCase, val cancel: CancelCookingUseCase, val withdraw: WithdrawCancellationUseCase)

  // domain/plan/Models.kt
  /** Kind, das in der Woche kochen darf, mit seinen Anwesenheitstagen (REQ-MEAL-021). */
  data class CookCandidate(val memberId: String, val name: String, val presentDays: Set<LocalDate>, val submissionDeadline: Instant?)
  data class DishEditorData(val week: WeekPlan, val parents: List<PlanMember>, val children: List<CookCandidate>)

  // domain/plan/UseCases.kt
  class LoadDishEditorUseCase(private val repository: MealPlanRepository, private val attendance: AttendanceRepository) {
      // parallel: week, members, attendance.week(weekStart)
      // children = attendance.children.filter { it.cooks && it.submissionDeadline != null }  (kocht in der Woche)
      //            + Kind, dem ein Gericht der Woche gehört (auch wenn es nicht mehr kocht, z. B. dayValid=false)
      //   presentDays = days.filter { it.present }.map { it.date }.toSet(), name aus members ("?" wenn unbekannt)
  }
  class SaveDishUseCase(private val repository: MealPlanRepository) {
      suspend operator fun invoke(dishId: String?, date: LocalDate, cookId: String, name: String, ingredientsText: String,
                                  ingredientsRequired: Boolean): ApiResult<Dish>
  }
  class DishUseCases(val load: LoadDishEditorUseCase, val save: SaveDishUseCase, val delete: DeleteDishUseCase, val review: ReviewDishUseCase)
  ```
  Fakes (`CookingFakes.kt`): `FakeCookingRepository` mit `statusResult: (LocalDate) -> ApiResult<WeekStatus>`, `approveResult`,
  `rejectResult`, `cancelResult`, `withdrawResult`, Mitschnitte `approved`, `rejected: List<Pair<String,String>>`,
  `cancelled: List<Triple<LocalDate,String,String>>`, `withdrawn`, `cancelGate`. Fixtures: `planWeek = 2026-10-19`,
  `benDeadline = Instant.parse("2026-10-15T14:00:00Z")` (Do 15.10., 16:00 Berlin), `cookStatus(memberId, state, …)`,
  `statusOf(weekStart, vararg cooks, openDays, ready)`, `childDish = Dish("d-2", 2026-10-20, "c-1", "Curry", listOf("Reis"), DishStatus.SUBMITTED)`.
  `childBen` bleibt `c-1`; ein zweites Kind `childLea = PlanMember("c-2", "Lea", false)`.
- **MIRROR**: SERVICE_PATTERN, `ExceptionUseCases`/`DishUseCases`-Bündel.
- **IMPORTS**: `kotlinx.coroutines.{async, coroutineScope}`, `de.familyplanner.android.feature.meals.domain.attendance.AttendanceRepository`,
  `de.familyplanner.android.feature.meals.domain.plan.{validateReason, MealPlanRepository}`.
- **GOTCHA**: `LoadDishEditorUseCase` bekommt einen zweiten Parameter → Koin-Bindung und 3b-Tests anpassen (Fake
  `FakeAttendanceRepository.weekResult` liefern). Attendance-Fehler = Editor-Ladefehler (ohne Anwesenheit keine
  gültigen Tage für Kinder) — auch für reine Elterngerichte, bewusst einfach. Mitglieder-Request scheitert → `Failure`
  (wie 3b). Validierung vor jedem Request; **kein** Request bei ungültiger Begründung.
- **VALIDATE**: `REQ-MEAL-033 status joins child names`, `REQ-MEAL-033 unknown child shows question mark`,
  `REQ-MEAL-033 failed member request fails the status`, `REQ-MEAL-030 reject validates reason before sending`,
  `REQ-MEAL-030 reject sends trimmed reason`, `REQ-MEAL-023 cancel validates and trims reason`,
  `REQ-MEAL-023 withdraw passes week and member`, `REQ-MEAL-021 editor lists cooking children with present days`,
  `REQ-MEAL-021 child that does not cook is no candidate`, `REQ-MEAL-021 owner of an invalid dish stays a candidate`,
  `REQ-MEAL-031 editor offers parents and cooking children`, `REQ-MEAL-020 child dish requires ingredients when saving`,
  `REQ-MEAL-020 failed attendance request fails the editor`.

### Task 5: Domäne – Editor-Regeln als reine Funktionen (TDD)
- **ACTION**: `T/domain/plan/EditorRulesTest.kt` (RED) → `domain/plan/EditorRules.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Was der Gericht-Screen erlaubt. */
  enum class EditorMode {
      /** Elternteil plant/ändert (Elterngericht oder Kindergericht, gespeichert = freigegeben). */
      PARENT_EDIT,
      /** Elternteil, Kindergericht wartet auf Freigabe: Freigeben/Ablehnen + Ändern/Löschen (REQ-MEAL-030). */
      PARENT_REVIEW,
      /** Elternteil, Kindergericht mit ungültigem Tag: nur verschieben oder löschen (REQ-MEAL-021, Anwender 2026-10-18). */
      PARENT_MOVE,
      /** Kind reicht eigenes Gericht ein / ändert es (REQ-MEAL-020/022). */
      CHILD,
  }
  fun editorMode(isParent: Boolean, existing: Dish?, cookIsChild: Boolean): EditorMode = when {
      !isParent -> EditorMode.CHILD
      existing != null && cookIsChild && !existing.dayValid -> EditorMode.PARENT_MOVE
      existing != null && cookIsChild && existing.status == DishStatus.SUBMITTED -> EditorMode.PARENT_REVIEW
      else -> EditorMode.PARENT_EDIT
  }
  /** Wählbare Tage: Kind → nur Anwesenheitstage; immer ohne Tage mit einem anderen Gericht. */
  fun allowedDates(weekStart: LocalDate, takenDates: Set<LocalDate>, childCook: CookCandidate?): Set<LocalDate> {
      val week = (0L..6L).map { weekStart.plusDays(it) }
      val base = childCook?.let { c -> week.filter { it in c.presentDays } } ?: week
      return base.filterNot { it in takenDates }.toSet()
  }
  /** Vorauswahl beim Öffnen: angetippter Tag, wenn erlaubt, sonst der erste erlaubte, sonst `null`. */
  fun preselectDate(tapped: LocalDate, allowed: Set<LocalDate>): LocalDate? = tapped.takeIf { it in allowed } ?: allowed.minOrNull()
  ```
- **MIRROR**: `domain/attendance/PlanWeeks.kt` (reine, getestete Funktionen).
- **IMPORTS**: `java.time.LocalDate`.
- **GOTCHA**: Im `PARENT_MOVE`-Modus ist der **aktuelle** (ungültige) Tag nicht in `allowedDates` (Kind abwesend) — das ist
  gewollt: Speichern erst nach Wahl eines gültigen Tages. `dayValid=false` hat Vorrang vor `SUBMITTED` (Freigabe einer
  ungültigen Woche wäre sinnlos, `readyToOrder` bliebe `false`). Ein abgelehntes Kindergericht ist `PARENT_EDIT`
  (Eltern dürfen es übernehmen und speichern = freigeben, oder löschen).
- **VALIDATE**: `REQ-MEAL-021 child cook allows only present free days` (Di–Do anwesend, Mi belegt → {Di, Do}),
  `REQ-MEAL-032 parent cook allows all free days`, `REQ-MEAL-021 tapped absent day preselects first allowed day`,
  `REQ-MEAL-021 no allowed day preselects nothing`, `REQ-MEAL-021 invalid child dish opens move mode for parents`,
  `REQ-MEAL-030 submitted child dish opens review mode`, `REQ-MEAL-021 invalid day wins over submitted`,
  `REQ-MEAL-030 rejected child dish opens edit mode`, `REQ-MEAL-020 child always gets child mode`,
  `REQ-MEAL-032 parent dish opens edit mode`.

### Task 6: UI – Gericht-Screen mit Modi (TDD)
- **ACTION**: `T/ui/CookingViewModelsTest.kt` + `T/ui/CookingScreensTest.kt` (RED) → `ui/dish/DishEditArgs.kt`,
  `DishEditViewModel.kt`, `DishEditScreen.kt`, `DishEditSections.kt`, `ui/plan/ReasonDialog.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  // ui/dish/DishEditArgs.kt
  /** Ein Koin-Parameter statt mehrerer Strings (Koin unterscheidet Parameter nach Typ). [cookId]: Vorauswahl (Stellvertretung). */
  data class DishEditArgs(val date: LocalDate, val dishId: String? = null, val cookId: String? = null)

  // DishEditUiState: zusätzlich
  val mode: EditorMode = EditorMode.PARENT_EDIT,
  val children: List<CookCandidate> = emptyList(),
  val existing: Dish? = null,
  val allowedDates: Set<LocalDate> = emptySet(),     // ersetzt die Prüfung über takenDates
  val showRejectDialog: Boolean = false,
  val rejectReason: String = "",
  val now: Instant,                                  // für den Fristhinweis im Kind-Modus
  // abgeleitet:
  val childCook: CookCandidate? get() = children.firstOrNull { it.memberId == cookId }
  val ingredientsRequired: Boolean get() = childCook != null
  val fieldsLocked: Boolean get() = mode == EditorMode.PARENT_MOVE
  val isLate: Boolean get() = mode == EditorMode.CHILD && childCook?.submissionDeadline?.let { now.isAfter(it) } == true
  val canSave: Boolean get() = !isLoading && loadError == null && cookId != null && name.isNotBlank() && date in allowedDates &&
      !isBusy && (mode != EditorMode.PARENT_MOVE || date != existing?.date)
  val canDelete: Boolean get() = !isNew && mode != EditorMode.CHILD
  // Events zusätzlich: Approve, RequestReject, RejectReasonChanged(value), CancelReject, ConfirmReject
  class DishEditViewModel(args: DishEditArgs, sessions: SessionStore, dishes: DishUseCases, clock: Clock = Clock.system(BerlinZone))
  ```
  - Laden: wie 3b, danach `cookId` je Modus: Kind → `session.memberId` (fest; ist das Kind kein `CookCandidate` →
    `loadError = validationFailure("Du kochst in dieser Woche nicht.")`-Fehler); Eltern neu → `args.cookId` falls Elternteil
    oder Kandidat, sonst eigene ID/erster Elternteil (wie 3b); bestehend → `existing.cookId`, wenn Elternteil oder Kandidat.
    `mode = editorMode(isParent, existing, cookIsChild = existing?.cookId in children ids || (neu && args.cookId in children ids))`.
    `allowedDates = allowedDates(weekStart, taken, childCook)`; `date = existing?.date ?: preselectDate(args.date, allowed)`.
    Kind ohne erlaubten Tag → `date` bleibt der angetippte Tag, `canSave = false`, Hinweis `dish_no_allowed_day`.
  - `CookSelected`: `allowedDates` neu berechnen; liegt `date` nicht mehr darin → `preselectDate(date, allowed)`.
  - In `PARENT_MOVE` und `CHILD` ignorieren `CookSelected`; in `PARENT_MOVE` auch `NameChanged`/`IngredientsChanged`.
  - `Save`: `dishes.save(dishId, date, cookId, name, ingredientsText, ingredientsRequired)`.
  - `Approve`: `isBusy`-Sperre → `dishes.review.approve(id)` → `isDone`. `ConfirmReject`: `dishes.review.reject(id, rejectReason)`;
    Validierungsfehler bleibt im Dialog (`error`), Erfolg → `isDone`. 409 („wartet nicht auf Freigabe“, anderes Elternhandy war
    schneller) → Servermeldung, Dialog schließen.
  - Screen (Sektionen in `DishEditSections.kt`):
    - Titel je Modus: `dish_title_new` / `dish_title_edit` / `review_title` „Gericht prüfen“ / `move_title` „Gericht verschieben“ /
      Kind: `child_title_new` „Gericht einreichen“ bzw. `child_title_edit` „Gericht ändern“.
    - `PARENT_REVIEW`: `ReviewSection`: „%1$s hat eingereicht“ (+ „ · nach Frist“ bei `late`), `Button` „Freigeben“,
      `OutlinedButton` „Ablehnen…“; darunter `HorizontalDivider` und das normale Formular mit Hinweis
      „Speichern gibt das Gericht frei.“
    - `PARENT_MOVE`: `MoveBanner` in `error`-Farbe: „%1$s ist am %2$s nicht da. Gericht auf einen anderen Tag verschieben oder
      löschen.“; Felder mit `enabled = false`, Koch als Text statt Dropdown; Speichern-Text „Verschieben“.
    - `CHILD`: statt Dropdown `CookLine` „Du kochst · Einreichen bis %1$s“ (`formatDeadline`); bei `isLate` Hinweis
      „Deine Frist ist vorbei – das Gericht wird als „nach Frist“ markiert.“; bei `existing.status == REJECTED`
      „Abgelehnt: %1$s“; bei `APPROVED` „Nach einer Änderung muss es erneut freigegeben werden.“; bei `!existing.dayValid`
      „Du bist an diesem Tag nicht da – bitte wähle einen anderen Tag.“; Speichern-Text „Einreichen“ (neu) / „Erneut einreichen“; kein Löschen.
    - Koch-Dropdown (Eltern): erst Eltern, dann Kinder mit Zusatz „%1$s (Kind)“.
    - Tag-Chips: `enabled = day in allowedDates || day == date` (aktueller Tag bleibt sichtbar ausgewählt);
      `contentDescription` „… (belegt)“ / „… (nicht da)“.
    - Zutaten-Label: `dish_ingredients` (optional) bzw. `dish_ingredients_required` „Zutaten (eine pro Zeile)“.
    - `ReasonDialog(title, text, reason, onReasonChange, confirmLabel, error, isBusy, onConfirm, onDismiss)` in
      `ui/plan/ReasonDialog.kt`: `AlertDialog` + `OutlinedTextField` (Zähler `x/200`, Eingabe auf `REASON_MAX` kürzen) + `ErrorText`.
  - Route: `DishEditRoute(args: DishEditArgs, onDone)`; `koinViewModel<DishEditViewModel> { parametersOf(args) }`.
- **MIRROR**: VIEWMODEL_PATTERN, `ExceptionDialogs.kt` (Dialog mit Feld), `DishEditScreen.kt`-Sektionen.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.plan.{EditorMode, editorMode, allowedDates, preselectDate, CookCandidate, DishStatus}`,
  `de.familyplanner.android.feature.meals.ui.formatDeadline`, `androidx.compose.material3.{AlertDialog, HorizontalDivider, Button}`, `java.time.{Clock, Instant}`.
- **GOTCHA**: Koin unterscheidet `parametersOf`-Werte nach **Typ** — mit zwei nullable Strings (`dishId`, `cookId`) würde
  `params.getOrNull<String>()` den falschen liefern → **ein** `DishEditArgs`-Parameter (`viewModel { p -> DishEditViewModel(p.get(), get(), get()) }`).
  `takenDates` bleibt im State (für die Chip-Beschreibung „belegt“), entschieden wird über `allowedDates`.
  `ReasonDialog` liegt in `ui/plan`, wird aber auch vom Editor benutzt — Paketgrenze innerhalb des Moduls ist unkritisch.
  detekt: `DishEditViewModel` hat jetzt viele Events → Handler in private Funktionen (`onReview`, `onEdit`) aufteilen,
  `CyclomaticComplexMethod` im `when` beachten; Datei < 400 Zeilen, sonst `withLoaded`-Logik in `EditorRules.kt` ziehen.
  Android-Strings trimmen Randleerzeichen → Format-Strings („%1$s (Kind)“, „ · nach Frist“ als eigener String `review_late` ohne Randleerzeichen und per `Text` anhängen).
- **VALIDATE**: ViewModel: `REQ-MEAL-020 child editor fixes cook to self`, `REQ-MEAL-021 child editor allows only own present days`,
  `REQ-MEAL-020 child saving without ingredients shows validation`, `REQ-MEAL-020 late child sees after deadline hint`
  (Clock nach `benDeadline`), `REQ-MEAL-022 child editing approved dish sees re-approval hint`,
  `REQ-MEAL-030 rejected dish shows reason to the child`, `REQ-MEAL-021 child moves own invalid dish`,
  `REQ-MEAL-031 parent preselects child from args and only its days`, `REQ-MEAL-031 switching cook to child drops absent day`,
  `REQ-MEAL-031 parent saving child dish requires ingredients`, `REQ-MEAL-030 parent approves submitted dish and finishes`,
  `REQ-MEAL-030 parent rejects with reason and finishes`, `REQ-MEAL-030 reject without reason keeps dialog with message`,
  `REQ-MEAL-030 approving an already reviewed dish shows conflict`, `REQ-MEAL-021 move mode locks fields and cook`,
  `REQ-MEAL-021 move mode cannot save the same invalid day`, `REQ-MEAL-021 move mode saves a present day`,
  `REQ-MEAL-021 move mode allows delete`, `REQ-MEAL-021 move mode hides review`, `child cannot delete`, `double approve sends once`.
  Screens: `REQ-MEAL-030 review section shows approve and reject`, `REQ-MEAL-030 reject dialog shows counter and error`,
  `REQ-MEAL-021 move banner names child and day`, `REQ-MEAL-021 move mode shows Verschieben and disabled fields`,
  `REQ-MEAL-021 absent day chip is disabled with nicht da`, `REQ-MEAL-020 child editor shows deadline and Einreichen`,
  `REQ-MEAL-020 child editor has no delete`, `REQ-MEAL-031 cook picker marks children`.

### Task 7: UI – Essensplan mit Badges, Wochenstatus, „Mein Kochtag“ (TDD)
- **ACTION**: Tests in `CookingViewModelsTest`/`CookingScreensTest` (RED) → `WeekPlanViewModel.kt`, `WeekPlanScreen.kt`,
  `StatusCards.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  // WeekPlanUiState: zusätzlich
  val planWeek: LocalDate,                 // currentPlanWeek(clock.instant())
  val memberId: String? = null,
  val status: StatusOverview? = null,
  val statusError: AppError? = null,
  /** Kind, für das der Absage-Dialog offen ist. */
  val cancelTarget: String? = null,
  val cancelReason: String = "",
  val isBusy: Boolean = false,
  val actionError: AppError? = null,
  // abgeleitet:
  val showsStatus: Boolean get() = weekStart >= planWeek && !isOffline
  val myCooking: CookOverview? get() = if (isParent) null else status?.cooks?.firstOrNull { it.status.memberId == memberId }
  val showsPlanWeekLink: Boolean get() = weekStart == planWeek.minusWeeks(1)
  // Events zusätzlich: GoToPlanWeek, RequestCancel(memberId), CancelReasonChanged(value), DismissCancel, ConfirmCancel, Withdraw(memberId)
  class WeekPlanViewModel(sessions: SessionStore, loadWeekPlan: LoadWeekPlanUseCase, cooking: CookingUseCases, clock: Clock = …)
  ```
  - `load()`: Plan wie bisher; zusätzlich, wenn `weekStart >= planWeek`, **parallel** `cooking.status(requested)` in einem eigenen
    `statusJob` (ebenfalls abbrechen + späte Antworten der alten Woche verwerfen). Status nur zeigen, wenn der Plan nicht
    `Cached` ist; Fehler → `statusError` (die Karte zeigt `ErrorText`), Netzwerkfehler bei `Cached`-Plan → still (`status = null`).
    Beim Wochenwechsel `status = null, statusError = null`.
  - `ConfirmCancel`: `isBusy`; `cooking.cancel(weekStart, cancelTarget, cancelReason)`; Erfolg → Dialog zu, `load()`;
    Validierungsfehler bleibt im Dialog (`actionError`). `Withdraw(memberId)`: `cooking.withdraw` → `load()`; Fehler → `actionError`.
  - Screen:
    - Unter dem Kopf bei `showsPlanWeekLink`: `TextButton` „Zur Planwoche %1$s–%2$s ›“ → `GoToPlanWeek` (setzt `weekStart = planWeek`).
    - Bei `isOffline && weekStart >= planWeek`: Text „Wochenstatus nur im Heim-WLAN.“
    - `MyCookingCard(cook, onOpen, onCancel, onWithdraw)` (Kind, `showsStatus`, `myCooking != null`): „Einreichen bis %1$s“
      (oder „Keine Frist“ bei `null`); Zustandszeile je `CookState`: `NOT_SUBMITTED` „Noch nicht eingereicht“,
      `SUBMITTED` „Wartet auf Freigabe“, `APPROVED` „Freigegeben“, `REJECTED` „Abgelehnt: %1$s“ (Grund aus dem Gericht im Plan,
      `state.week.days` → `dish.rejectionReason`), `CANCELLED` „Abgesagt: %1$s“; „nach Frist“ bei `late`; bei `!dayValid`
      „Du bist an deinem Kochtag nicht da – bitte verschiebe dein Gericht.“ (`error`).
      Buttons: `NOT_SUBMITTED` „Gericht einreichen“ → Editor `DishEditArgs(planWeek-Tag = erster Tag der Woche, null, memberId)`;
      mit `dishId` „Gericht ändern“ / bei `REJECTED` „Gericht überarbeiten“ → Editor mit `dishId`;
      `OutlinedButton` „Kochen absagen“ (nicht bei `CANCELLED`) → `RequestCancel(memberId)`; bei `CANCELLED` „Absage zurücknehmen“.
    - `WeekStatusCard(status, isParent, onOpenCook, onCancel, onWithdraw)` (alle, `showsStatus`): „Bestellfrist %1$s“;
      `readyToOrder` → „✓ Bereit zur Bestellung“ (`primary`, fett); sonst „Offen: Mo 19.10., …“ (nur wenn `openDays` nicht leer);
      je Koch eine Zeile „%1$s – <Zustand>“ + Zusatz „bis %1$s“ bei `NOT_SUBMITTED`, „nach Frist“, „Tag ungültig“.
      Eltern: Zeile mit `dishId` antippbar → `onOpenCook(DishEditArgs(dishDate, dishId))`; `NOT_SUBMITTED` → `TextButton` „Eintragen“
      (`DishEditArgs(weekStart, null, memberId)`, REQ-MEAL-031) und „Absagen…“; `CANCELLED` → „Zurücknehmen“.
      Kinder: nur lesen (keine Buttons, keine Klicks).
    - `ReasonDialog` für die Absage: Titel „Kochen absagen?“, Text „%1$s kocht diese Woche nicht. Ein eingereichtes Gericht wird entfernt.“
      bzw. für das Kind selbst „Du kochst diese Woche nicht. Dein Gericht wird entfernt.“, Bestätigen „Absagen“.
    - `PlanDayRow`/`DishSummary`: Badges unter dem Koch-Namen: `SUBMITTED` „wartet auf Freigabe“, `REJECTED` „abgelehnt“,
      `late` „nach Frist“ (`tertiary`, `labelMedium`); `!dayValid` „⚠ Tag ungültig“ (`error`).
    - Klickbarkeit: Eltern online wie 3b; Kind online nur die Zeile seines eigenen Gerichts (`dish.cookId == memberId`) bei
      `weekStart >= planWeek`. Callback `onOpenEditor: (DishEditArgs) -> Unit` ersetzt `onOpenDay(date, dishId)`.
  - `ErrorText(state.actionError)` unter den Karten.
- **MIRROR**: VIEWMODEL_PATTERN (Job abbrechen, späte Antworten verwerfen), `WeekPlanScreen.kt` (Kopf, `OfflineNotice`), Card aus Material3.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.attendance.currentPlanWeek`, `de.familyplanner.android.feature.meals.domain.cooking.*`,
  `de.familyplanner.android.feature.meals.ui.dish.DishEditArgs`, `androidx.compose.material3.{Card, OutlinedButton, Button}`.
- **GOTCHA**: Der Status wird beim ersten Öffnen ebenfalls doppelt geladen (`init` + `LifecycleResumeEffect`), der zweite
  bricht den ersten ab — wie beim Plan, akzeptiert. Prefetch der nächsten Woche lädt **keinen** Status. `today`/`planWeek`
  aus der `Clock`, nie `LocalDate.now()`. Sonntag 18.10.: diese Woche 12.10., Planwoche 19.10. → Link sichtbar; Samstag
  17.10. ebenso. Die Karten liegen als `item {}` in der `LazyColumn` vor den Tagen — im Robolectric-Fenster sind die Tage
  dann weiter unten (`assertExists`/`performScrollTo`). `WeekPlanViewModel` wächst → Status-/Absage-Logik in private Funktionen,
  Datei < 300 Zeilen; `LongParameterList` durch `CookingUseCases`-Bündel vermeiden.
- **VALIDATE**: ViewModel: `REQ-MEAL-033 plan week loads status in parallel`, `REQ-MEAL-033 this week loads no status`,
  `REQ-MEAL-033 prefetch loads no status`, `REQ-MEAL-033 late status of previous week is ignored`,
  `REQ-MEAL-033 offline plan hides status`, `REQ-MEAL-033 status error is shown in the card`,
  `REQ-MEAL-020 child sees own cooking`, `REQ-MEAL-033 parent has no own cooking card`, `go to plan week switches week`,
  `REQ-MEAL-023 child cancels own cooking with reason and reloads`, `REQ-MEAL-023 cancel without reason keeps dialog`,
  `REQ-MEAL-023 parent cancels for child`, `REQ-MEAL-023 withdraw reloads`, `double confirm cancels once`.
  Screens: `REQ-MEAL-020 submitted dish shows wartet auf Freigabe badge`, `REQ-MEAL-030 rejected dish shows abgelehnt badge`,
  `REQ-MEAL-020 late dish shows nach Frist`, `REQ-MEAL-021 invalid day shows Tag ungültig`,
  `REQ-MEAL-033 status card shows deadline open days and cooks`, `REQ-MEAL-033 ready week shows Bereit zur Bestellung`,
  `REQ-MEAL-033 not submitted cook shows deadline`, `REQ-MEAL-031 parent can enter dish for child` (Callback mit `cookId`),
  `REQ-MEAL-023 cancelled cook shows reason and withdraw for parents`, `REQ-MEAL-033 child sees status without actions`,
  `REQ-MEAL-020 my cooking card shows deadline and submit button`, `REQ-MEAL-030 my cooking card shows rejection reason`,
  `REQ-MEAL-021 my cooking card warns about invalid day`, `REQ-MEAL-023 my cooking card offers cancel and withdraw`,
  `REQ-MEAL-022 child taps own dish row to edit`, `REQ-MEAL-022 child cannot open other dishes`,
  `plan week link is shown on this week`, `offline plan week shows status hint`.

### Task 8: Navigation, Koin, Version
- **ACTION**: Route und Graph anpassen, `mealsModule` erweitern, Version erhöhen.
- **IMPLEMENT**:
  ```kotlin
  // MealsNavigation.kt
  /** [date] als ISO-Datum (`yyyy-MM-dd`); [dishId] `null` = neues Gericht; [cookId] Vorauswahl (Stellvertretung/Kind). */
  @Serializable
  data class DishEditRoute(val date: String, val dishId: String? = null, val cookId: String? = null)
  // Graph
  composable<WeekPlanRoute> {
      WeekPlanRoute(
          onOpenEditor = { args -> navController.navigate(DishEditRoute(args.date.toString(), args.dishId, args.cookId)) },
          onBack = { navController.popBackStack() },
      )
  }
  composable<DishEditRoute> { entry ->
      val route = entry.toRoute<DishEditRoute>()
      DishEditRoute(DishEditArgs(LocalDate.parse(route.date), route.dishId, route.cookId), onDone = { navController.popBackStack() })
  }
  // MealsModule.kt
  // plan
  factory { DishUseCases(LoadDishEditorUseCase(get(), get()), SaveDishUseCase(get()), DeleteDishUseCase(get()), ReviewDishUseCase(get())) }
  viewModel { WeekPlanViewModel(get(), get(), get()) }
  viewModel { params -> DishEditViewModel(params.get(), get(), get()) }
  // cooking
  single<CookingRepository> { RemoteCookingRepository(get(), get()) }
  factory { CookingUseCases(LoadWeekStatusUseCase(get(), get()), CancelCookingUseCase(get()), WithdrawCancellationUseCase(get())) }
  ```
  - `android/app/build.gradle.kts`: `versionCode = 4`, `versionName = "0.4.0"`.
  - `MealsModuleTest`: `koin.get<DishEditViewModel> { parametersOf(DishEditArgs(LocalDate.of(2026, 10, 19))) }`, `koin.get<WeekPlanViewModel>()`.
- **MIRROR**: bestehender `mealsGraph`/`mealsModule`, Kommentar-Gruppen `// attendance` / `// plan` / `// cooking`.
- **IMPORTS**: `de.familyplanner.android.feature.meals.ui.dish.DishEditArgs`, `…domain.cooking.*`, `…data.cooking.RemoteCookingRepository`.
- **GOTCHA**: `DishEditRoute` (Navigation-Objekt) und `DishEditRoute` (Composable) heißen gleich wie in 3b — Import-Aliase
  wie bisher beibehalten. `app` (`AppNavHost`) bleibt unverändert, Home-Buttons ändern sich nicht. Kover schließt `*Route*`
  und `*Module*` aus — Screens/ViewModels müssen getestet sein.
- **VALIDATE**: `cd android && ./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest` grün (inkl. `MealsModuleTest`).

### Task 9: Qualität & Coverage
- **ACTION**: CI-Kette lokal.
- **IMPLEMENT**: ktlint/detekt-Funde beheben (Funktionen < 60 Zeilen, Dateien < 400 Zeilen, `TooManyFunctions`,
  `CyclomaticComplexMethod`, `LongParameterList`); Kover ≥ 80 %.
- **MIRROR**: `familyplanner.quality`-Konvention; 3b-Report (Sektionen, Bündel).
- **IMPORTS**: –
- **GOTCHA**: `ktlintFormat` vor `ktlintCheck`. Die Composables mit vielen Callbacks (`WeekStatusCard`) bekommen ein
  kleines Callback-Objekt (`StatusActions(onOpen, onEnter, onCancel, onWithdraw)`), falls detekt `LongParameterList` meldet.
- **VALIDATE**: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` grün;
  `git diff main -- api/ backend/` leer.

### Task 10: Doku, Traceability, PRD
- **ACTION**: V-Modell-Dokumente nachziehen.
- **IMPLEMENT**:
  - `docs/03-design/meals.md`: Einleitung und Phase-4-Abschnitt („Dieser Stand ist Backend/API (4a); die App folgt in 4b.“)
    auf „App-Seite: Abschnitt ‚App-Seite (Phase 4b)‘“ ändern; neuer Abschnitt **„App-Seite (Phase 4b)“**: Pakete
    `domain/cooking`, `data/cooking`, Erweiterungen in `domain/plan`, `ui/plan`, `ui/dish`; Tabelle Screens/Rollen
    (Essensplan mit Wochenstatus-Karte + „Mein Kochtag“, Gericht-Screen mit Modi `PARENT_EDIT`, `PARENT_REVIEW`,
    `PARENT_MOVE`, `CHILD`); Regeln: Karten nur ab Planwoche und online; Badges aus dem Cache; Kind nur Anwesenheitstage,
    Zutaten Pflicht, kein Löschen, Fristhinweis ohne Sperre; `dayValid=false` → Eltern nur Verschieben/Löschen (Verschieben
    gibt frei, Anwender 2026-10-18); Freigabe/Ablehnung im Gericht-Screen, Begründung 1–200; Stellvertretung über
    „Eintragen“ (Koch vorausgewählt); Absage/Rücknahme in den Karten; Cache-Format um Status-Felder erweitert, Version 1 bleibt.
  - `docs/04-verification/traceability.md`: Zeilen REQ-MEAL-020, -021, -022, -023, -030, -031, -033: Unit + „App: `ValidationTest`,
    `UseCasesTest` (plan/cooking), `EditorRulesTest`, `CookingViewModelsTest`, `CookingScreensTest`“ (je passend),
    Integration + „App: `RemoteCookingRepositoryTest`, `RemoteMealPlanRepositoryTest`“, E2E „manuell: Abnahme Phase 4b
    ([android/README.md](../../android/README.md))“, Status `umgesetzt (Backend + App)`; bei -023/-030 „(Einkaufsliste in Phase 5)“.
    Nach Geräte-Abnahme: „abgenommen (<Datum>, Geräte-Abnahme 4b)“.
  - `docs/01-requirements/meals.prd.md`, Zeile Phase 4: Status `in-progress` → **`complete`**; PRP-Spalte „4b: … (Plan erstellt)“
    ersetzen durch „4b: `.claude/PRPs/plans/completed/einreichung-freigabe-android.plan.md` (umgesetzt, Report:
    `.claude/PRPs/reports/einreichung-freigabe-android-report.md`)“.
  - `android/README.md`: Modulbeschreibung `feature:meals` um „Einreichen, Freigeben, Absagen, Wochenstatus“; Abschnitt
    „Abnahme-Checkliste Phase 4b“ (siehe Manual Validation).
  - Plan nach `.claude/PRPs/plans/completed/` verschieben (macht `/prp-implement`).
- **MIRROR**: bestehende Abschnitte/Zeilen; keine fremden Zeilen umformatieren.
- **IMPORTS**: –
- **GOTCHA**: Danach `grep -rn "folgt in 4b" docs/ android/` — alle Treffer ersetzen.
- **VALIDATE**: Links zeigen auf existierende Dateien.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `validateDish` Kind | Name „Curry“, Zutaten `"\n \n"` | „Bitte mindestens eine Zutat angeben.“ | ✔ |
| `validateDish` Eltern | Zutaten leer | gültig | |
| `validateReason` | `"   "` | „Bitte eine Begründung angeben.“ | ✔ |
| `validateReason` | 201 Zeichen | „Die Begründung darf höchstens 200 Zeichen haben.“ | ✔ |
| Mapper Dish | `status: REJECTED`, `rejectionReason` | `DishStatus.REJECTED` + Grund | |
| Cache | Datei aus 3b ohne `status` | lesbar, `APPROVED`, `dayValid = true` | ✔ |
| Mapper WeekStatus | `submissionDeadline: null` | `CookStatus.submissionDeadline = null` | ✔ |
| Mapper WeekStatus | `orderDeadline: "kaputt"` | `AppError.Server` | ✔ |
| `cancel` | 200 mit Body | `Success(Unit)` | |
| `approve` | 409 | `Conflict("Das Gericht wartet nicht auf Freigabe")` | ✔ |
| `allowedDates` | Kind Di–Do da, Mi belegt | {Di, Do} | ✔ |
| `editorMode` | Eltern, Kindergericht `SUBMITTED`, `dayValid=false` | `PARENT_MOVE` | ✔ |
| Verschieben | gleicher (ungültiger) Tag | `canSave = false` | ✔ |
| Kind-Frist | Clock nach Do 15.10., 16:00 Berlin | Hinweis „nach Frist“, Speichern möglich | ✔ |
| Status | diese Woche (12.10.) | kein Status-Request | ✔ |
| Status | Planwoche, Plan offline aus Cache | keine Karten, Hinweis | ✔ |
| Absage | Begründung leer | Dialog bleibt, Meldung, kein Request | ✔ |
| Doppelklick | Freigeben ×2 | ein Request | ✔ |

### Edge Cases Checklist
- [x] Leere Eingabe (Zutaten bei Kindergericht, Begründung)
- [x] Maximale Eingabe (Begründung 200, Grenzen aus 3b)
- [x] Ungültige Typen (unbekannter Status im Cache, unlesbare Frist)
- [x] Nebenläufigkeit (zwei Elternhandys geben frei → 409; Wochenwechsel während Status-Laden; Doppelklick)
- [x] Netzwerkfehler (offline: Badges aus Cache, keine Karten; Statusfehler bei frischem Plan)
- [x] Keine Berechtigung (Kind: kein Löschen, keine fremden Gerichte, keine Freigabe; 403 trotzdem abgebildet)
- [x] Zeitzone (Fristhinweis in Berlin, Sonntag/Montag-Grenze für Planwoche)
- [x] Anwesenheit ändert sich nach Einreichung (`dayValid=false` → Verschieben-Modus)
- [x] Kind ohne freien Anwesenheitstag (Hinweis, kein Speichern)
- [x] Kind kocht nicht (keine Karte; Editor-Fehler „Du kochst in dieser Woche nicht.“)

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
EXPECT: Mit einem gekoppelten Kind- und Eltern-Emulator lässt sich eine Woche bis „Bereit zur Bestellung“ durchspielen.

### Manual Validation (Abnahme Phase 4b – echte Handys gegen den Pi)
- [ ] Pi läuft mit Image inkl. Migration V4 (4a); Release-APK 0.4.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [ ] Kinderhandy (kochendes Kind) an Sa/So: Essensplan zeigt „Zur Planwoche … ›“; dort „Mein Kochtag“ mit Frist
- [ ] Kind reicht ein: nur Anwesenheitstage wählbar, ohne Zutaten Meldung; danach „wartet auf Freigabe“ in Karte und Plan
- [ ] Elternhandy: Wochenstatus zeigt offene Tage, Kind „wartet auf Freigabe“, anderes Kind „noch nicht eingereicht, bis …“
- [ ] Eltern lehnen mit Begründung ab → Kinderhandy (Pull-to-Refresh) zeigt „Abgelehnt: …“; Kind überarbeitet → wieder „wartet“
- [ ] Eltern geben frei → „Freigegeben“; Kind ändert → wieder „wartet auf Freigabe“
- [ ] Eltern tragen für das zweite Kind über „Eintragen“ ein → direkt freigegeben, nur dessen Anwesenheitstage wählbar
- [ ] Eltern legen für das Kind eine Abwesenheits-Ausnahme auf dessen Kochtag an → Plan „⚠ Tag ungültig“; Gericht-Screen
      „Gericht verschieben“: Felder gesperrt, kein Freigeben; auf gültigen Tag verschieben → freigegeben; Variante: löschen
- [ ] Kind sagt mit Begründung ab → Gericht verschwindet, Status „abgesagt: …“; Eltern/Kind nehmen die Absage zurück
- [ ] Alle Tage belegt und freigegeben → „✓ Bereit zur Bestellung“
- [ ] Zweites Elternhandy gibt gleichzeitig frei → Meldung „Das Gericht wartet nicht auf Freigabe“
- [ ] WLAN aus: Plan mit Badges lesbar, „Wochenstatus nur im Heim-WLAN.“, keine Karten

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-020, -021, -022, -023, -030, -031, -033 haben App-Tests mit REQ-ID im Namen
- [ ] Eine Woche lässt sich in der App bis „bereit zur Bestellung“ durchspielen (PRD-Erfolgssignal Phase 4)
- [ ] `dayValid=false`: Eltern können nur verschieben oder löschen; Kind sieht den Hinweis und verschiebt selbst
- [ ] 400/403/404/409/offline mit deutschen Meldungen abgebildet
- [ ] `api/` und `backend/` unverändert; keine neue Bibliothek
- [ ] Geräte-Abnahme dokumentiert (README-Checkliste, Datum in traceability.md)

## Completion Checklist
- [ ] Code folgt den Mustern aus `feature:meals` (UiState/Event, `ApiResult`, Fakes, Use-Case-Bündel)
- [ ] Fehlerbehandlung über `ApiResult`/`AppError`; kein neues Logging
- [ ] Tests folgen Testmustern (MockWebServer, Robolectric, DataStore mit `TemporaryFolder`)
- [ ] Keine hartcodierten Grenzen außerhalb `PlanLimits`, keine Texte außerhalb `strings.xml`/Validation
- [ ] Moduldesign, Traceability, PRD, android/README aktualisiert
- [ ] Keine Abhängigkeit `feature:meals` ↔ `feature:auth`

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Server erlaubt die Freigabe eines Gerichts mit `dayValid=false` (nur App verhindert sie) | L | L | `readyToOrder` bleibt trotzdem `false` (Server); falls gewünscht, Backend-Härtung als eigener kleiner Fix nach ADR-0004 |
| Verschieben durch Eltern gibt ungeprüft frei | L | L | Vom Anwender akzeptiert; Name/Zutaten sind im Verschieben-Modus gesperrt, der Inhalt war schon eingereicht |
| `DishEditViewModel`/`WeekPlanViewModel` werden zu groß (detekt) | M | L | Reine Regeln in `EditorRules.kt`, Sektionen-Composables, Use-Case-Bündel |
| Koin-Parameter verwechselt (zwei nullable Strings) | M | M | Ein `DishEditArgs`-Parameter; `MealsModuleTest` baut den Editor |
| 403 zeigt für Kinder „Nur für Eltern.“ (generische Meldung) | L | L | Kinder bekommen keine verbotenen Aktionen angeboten; Text in `core:ui` bei Bedarf später verallgemeinern |
| Kinder öffnen die App nur auf „dieser Woche“ und übersehen die Planwoche | M | M | Link „Zur Planwoche … ›“; Erinnerungen folgen in Phase 6 |
| Alte Cache-Datei inkompatibel | L | M | Neue Felder mit Defaults, Test mit 3b-Datei |

## Notes
- Heute (So 2026-10-18): diese Woche 12.10., Planwoche 19.10.; die Kind-Frist der Planwoche liegt in der laufenden Woche (12.–18.10.).
- Die API hat kein Einzel-GET für ein Gericht: der Editor lädt Woche, Mitglieder und Anwesenheit und sucht das Gericht (wie 3b).
- Der Ablehnungsgrund kommt aus dem Gericht (`Dish.rejectionReason`), der Absagegrund aus dem Status (`CookStatus.cancellationReason`).
- Benachrichtigung bei Statusänderung (REQ-MEAL-052) baut auf den Status-Daten dieser Phase auf (Phase 6).
- Erfahrungen nach Umsetzung im Report festhalten (`.claude/PRPs/reports/einreichung-freigabe-android-report.md`).
