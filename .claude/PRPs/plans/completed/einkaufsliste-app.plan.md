# Plan: Einkaufsliste in der App (Phase 6b)

## Summary
`feature:meals` bekommt den Screen **Einkaufsliste**: Er zeigt die vom Server berechnete Liste einer Planwoche (zusammengeführte
Zutaten der freigegebenen Gerichte mit Herkunft, freie Einträge, Häkchen). Eltern haken ab, ergänzen freie Einträge per Textzeile
(„2 kg Kartoffeln“, zerlegt mit dem vorhandenen `parseIngredientLine`) und löschen freie Einträge; Kinder lesen nur. Einstieg über
einen Knopf auf der Startseite und einen Link in der Wochenstatus-Karte des Essensplans. Nur online, kein Cache.
`api/` und `backend/` bleiben unverändert (Vertrag aus 6a).

## User Story
Als Elternteil möchte ich am Sonntagabend die Einkaufsliste der Planwoche auf dem Handy sehen, ergänzen und abhaken, damit ich bei
Picnic ohne Nachrechnen und ohne etwas zu vergessen bestellen kann.

## Problem → Solution
Phase 6a liefert die Einkaufsliste nur über die API (`GET/POST/DELETE/PUT …/weeks/{weekStart}/shopping-list…`); die App kennt sie
nicht → Screen „Einkaufsliste“ mit Wochenwahl, offenen Einträgen (Menge, Einheit, Name, Herkunft), Abschnitt „Erledigt“
(durchgestrichen), Eingabefeld für freie Einträge und Löschen freier Einträge — Schreiben nur Eltern.

## Metadata
- **Complexity**: Medium–Large (≈ 9 neue Dateien, ≈ 12 geänderte; nur bekannte Muster)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 6 – Einkaufsliste, Teil **6b** (App), REQ-MEAL-040, REQ-MEAL-041 (App-Seite)
- **Estimated Files**: ≈ 9 neu, ≈ 12 geändert (inkl. Tests und Doku)

## Entscheidungen des Anwenders (2026-10-20)
| Frage | Entscheidung |
|---|---|
| Einstieg | **Startseite** (Knopf „Einkaufsliste“) **und** Link „Einkaufsliste ›“ in der Wochenstatus-Karte des Essensplans (öffnet dieselbe Woche) |
| Rollen | **Alle lesen, Eltern bearbeiten** (wie die API: `GET` alle, Schreiben nur Eltern) |
| Offline | **Nur online**, kein Cache; offline erscheint die übliche Meldung „Server nicht erreichbar. Bist du im Heim-WLAN?“ |
| Abgehakte Einträge | **Ans Ende** in einen Abschnitt „Erledigt“, durchgestrichen |

Entscheidungen dieses Plans (vertretbare Standards, ohne Rückfrage; bei Widerspruch im Review ändern):
- Startwoche ohne Argument = **Planwoche** (`currentPlanWeek`, Montag nach heute in Berlin) — die Liste dient der Bestellung der
  kommenden Woche. Woche per ‹ › wechselbar (Grenzen 2000–2100 wie der Essensplan).
- Freier Eintrag = **eine Textzeile**, zerlegt mit `parseIngredientLine` (wie Rezept- und Gericht-Editor). Eingabe wird auf 80 Zeichen
  begrenzt (`PlanLimits.INGREDIENT_MAX`, wie der Server für Zutatennamen).
- Löschen freier Einträge **ohne Bestätigungsdialog** (schnell neu eingetippt; hält den Screen einfach).
- Pro Zeitpunkt **höchstens ein Schreibvorgang** (`isBusy`); der Server liefert nach jedem `POST`/`PUT` die ganze Liste, die direkt
  übernommen wird (kein optimistisches Abhaken).
- Herkunft je Eintrag als Zeile „Curry (Di), Carbonara (Do)“; freie Einträge zeigen „Eigener Eintrag“.

---

## UX Design

### Before
```
┌──────────────────────────────┐
│ Startseite                   │
│ [Essensplan]                 │
│ (Anwesenheit & Fristen)      │
│ (Rezepte)                    │
└──────────────────────────────┘
Einkaufsliste: nur per API; Eltern rechnen Zutaten aus dem Plan im Kopf zusammen.
```

### After
```
┌──────────────────────────────┐   ┌────────────────────────────────────┐
│ Startseite                   │   │ Essensplan → Karte „Wochenstatus“  │
│ [Essensplan]                 │   │ Bestellfrist: So 25.10., 17:00     │
│ (Anwesenheit & Fristen)      │   │ ✓ Bereit zur Bestellung            │
│ (Rezepte)                    │   │ …                                  │
│ (Einkaufsliste)  ← neu       │   │ [Einkaufsliste ›]  ← neu           │
└──────────────┬───────────────┘   └──────────────────┬─────────────────┘
               └──────────────┬───────────────────────┘
                              ▼
┌───────────────────────────────────────────────┐
│ Einkaufsliste                                 │
│ ‹  Woche 19.10.–25.10.  ›                     │
│ [Fehlermeldung, falls vorhanden]              │
│ ☐ 625 g Vollkornspaghetti                     │
│     Carbonara (Di)                            │
│ ☐ 2 Stange(n) Lauch                           │
│     Carbonara (Di), Curry (Do)                │
│ ☐ Salz                                        │
│     Carbonara (Di)                            │
│ ☐ 1 Packung Kaffee                    [🗑]    │
│     Eigener Eintrag                           │
│ Erledigt (1)                                  │
│ ☑ ~~500 g Reis~~                              │
│     Curry (Do)                                │
│ ┌ Eintrag hinzufügen ──────────┐ [Hinzufügen] │  ← nur Eltern
│ │ z. B. 2 kg Kartoffeln        │              │
│ └──────────────────────────────┘              │
│ (Zurück)                                      │
└───────────────────────────────────────────────┘
Kinder: keine Checkboxen (abgehakt = „✓ “ + durchgestrichen), kein Löschen, kein Eingabefeld.
Leer: „Noch nichts auf der Liste. Zutaten erscheinen, sobald Gerichte freigegeben sind.“
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Startseite | 3 Knöpfe | + „Einkaufsliste“ (OutlinedButton) | alle Mitglieder |
| Wochenstatus-Karte | Status, Köche | + TextButton „Einkaufsliste ›“ | nur wenn `status != null`; öffnet `status.weekStart` |
| Einkaufsliste öffnen | – | lädt `GET …/shopping-list` der Woche | Refresh bei Resume und Pull-to-Refresh |
| Abhaken (Eltern) | – | Checkbox → `PUT …/checks` (key, checked) | Antwort = neue Liste; Eintrag wandert nach „Erledigt“ bzw. zurück |
| Freien Eintrag hinzufügen (Eltern) | – | Textfeld + „Hinzufügen“ → `POST …/items` | Feld wird nach Erfolg geleert; Fehler bleibt sichtbar, Text bleibt |
| Freien Eintrag löschen (Eltern) | – | Mülleimer → `DELETE …/items/{id}` | danach Liste neu laden; 404 zählt als gelöscht |
| Kinder | – | nur lesen | Server würde Schreiben mit 403 ablehnen |
| Offline | – | Fehlermeldung, leere Liste | kein Cache (Entscheidung) |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `api/openapi.yaml` | 397-475, 1403-1446 | Endpunkte und Schemas `ShoppingList`, `ShoppingEntry`, `ShoppingEntrySource`, `CheckShoppingEntryRequest` |
| P0 | `docs/03-design/meals.md` | 177-214 | Server-Regeln 6a (Reihenfolge, Schlüssel, Häkchen, Rechte, 50 freie Einträge) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/RemoteCookingRepository.kt` | all | Repository-Muster mit `apiCall`/`apiCallNoContent` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/Mappers.kt` | all | Mapper mit `parseDate`/`mapAllOrNull`, `null` = Serverfehler |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipes/RecipeListViewModel.kt` | all | ViewModel-Muster (State, Event, `loadJob`) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanViewModel.kt` | 107-140, 156-235 | Wochenwechsel, `isBusy`-Schutz, verworfene späte Antworten, `Clock` |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipes/RecipeListScreen.kt` | all | Screen-Muster (Route + Screen, `PullToRefreshBox`, `LifecycleResumeEffect`) |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt` | 45-112, 181-201 | `WeekHeader` (‹ ›), `StatusCards`, Route-Signatur |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusCards.kt` | 36-56 | `WeekStatusCard` – hier kommt der Link hinein |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusActions.kt` | all | Aktionen-Bündel der Karten |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt` | all | Routen, `MealsHomeActions`, `mealsGraph` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | all | Koin-Registrierung |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 63-83 | `MealsHomeActions` verdrahten |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/recipe/IngredientLines.kt` | 74-143 | `parseIngredientLine`, `displayText`, `formatQuantity` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/profile/ExceptionsSection.kt` | 43-60 | Zeile mit Lösch-`IconButton` |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteCookingRepositoryTest.kt` | 1-90 | MockWebServer-Testmuster |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/CookingFakes.kt` | all | Fake-Repository mit Gates und Aufzeichnung |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/RecipeViewModelsTest.kt` | 1-140 | ViewModel-Tests mit `MainDispatcherRule`, `InMemorySessionStore` |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/RecipeScreensTest.kt` | 1-95 | Robolectric-Compose-Tests |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/MealsModuleTest.kt` | all | Koin-Graph-Test |

## External Documentation
No external research needed — feature uses established internal patterns (Retrofit-Client aus OpenAPI, Koin, Compose Material3,
Robolectric, MockWebServer). Material3 `Checkbox` und `TextDecoration.LineThrough` sind Standard-Compose-APIs.

---

## Patterns to Mirror

### NAMING_CONVENTION
Pakete je Teilbereich: `domain/<bereich>`, `data/<bereich>`, `ui/<screen>`. Neuer Bereich heißt **`shopping`**
(wie Backend `de.familyplanner.meals.shopping`). Domänentypen ohne Präfix (`ShoppingList`, `ShoppingEntry`), API-Typen per
Import-Alias (`as ApiShoppingList`).
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/Mappers.kt:9-10
import de.familyplanner.api.model.CookStatus as ApiCookStatus
import de.familyplanner.api.model.WeekStatus as ApiWeekStatus
```

### REPOSITORY_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/RemoteCookingRepository.kt:14-21
class RemoteCookingRepository(
    private val meals: MealsApi,
    private val json: Json,
) : CookingRepository {
    override suspend fun status(weekStart: LocalDate): ApiResult<WeekStatus> =
        apiCall(json, { meals.getWeekStatus(weekStart.toString()) }) { it.data?.toDomain() }

    override suspend fun approve(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.approveDish(dishId) }
```

### MAPPER_PATTERN (unlesbare Daten → `null` → `AppError.Server`)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/cooking/Mappers.kt:19-24
internal fun ApiWeekStatus.toDomain(): WeekStatus? {
    val dates = parseDate(weekStart)?.let { start -> parseInstant(orderDeadline)?.let { start to it } } ?: return null
    val days = openDays.mapAllOrNull { parseDate(it) } ?: return null
    val mapped = cooks.mapAllOrNull { it.toDomain() } ?: return null
    return WeekStatus(dates.first, dates.second, readyToOrder, days, mapped)
}
```

### ERROR_HANDLING
`ApiResult<T>` = `Success(data)` | `Failure(AppError)`; Client-Validierung über `validationFailure("…")`; Anzeige mit
`ErrorText(error)` (Servermeldung hat bei `Validation`/`Conflict` Vorrang).
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Validation.kt:28-34
internal fun validateReason(reason: String): ApiResult.Failure? =
    when {
        reason.isBlank() -> validationFailure("Bitte eine Begründung angeben.")
        reason.trim().length > PlanLimits.REASON_MAX ->
            validationFailure("Die Begründung darf höchstens ${PlanLimits.REASON_MAX} Zeichen haben.")
        else -> null
    }
```

### LOGGING_PATTERN
Kein Logging in ViewModels/Use Cases/Repositories der App; `apiCall` protokolliert Netzwerk-/TLS-/Parse-Fehler zentral
(`core/data/ApiCall.kt:44-58`). Nichts Neues loggen (keine Zutatennamen, keine Schlüssel).

### USE_CASE_PATTERN
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
// …:67-72 — Bündel hält die ViewModel-Parameterliste kurz
class CookingUseCases(
    val status: LoadWeekStatusUseCase,
    val cancel: CancelCookingUseCase,
    val withdraw: WithdrawCancellationUseCase,
)
```

### VIEWMODEL_PATTERN (Wochenwechsel, späte Antworten, Doppelklick-Schutz)
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanViewModel.kt:155-172
    private fun confirmCancel() {
        val current = _state.value
        val target = current.cancelTarget ?: return
        if (current.isBusy || !current.mayAct(target)) return
        _state.update { it.copy(isBusy = true, actionError = null) }
        viewModelScope.launch {
            val result = cooking.cancel(current.weekStart, target, current.cancelReason)
            // Wurde inzwischen die Woche gewechselt, gehört das Ergebnis nicht mehr zur Anzeige.
            if (_state.value.weekStart != current.weekStart) return@launch _state.update { it.copy(isBusy = false) }
            when (result) {
                is ApiResult.Success -> { … }
                is ApiResult.Failure -> _state.update { it.copy(isBusy = false, actionError = result.error) }
            }
        }
    }
// …:212-227 — Laden mit loadJob, verworfene Antwort einer anderen Woche
        loadJob?.cancel()
        val requested = _state.value.weekStart
        …
        loadJob =
            viewModelScope.launch {
                val result = loadWeekPlan(requested)
                // Eine späte Antwort der vorherigen Woche darf die aktuelle Anzeige nicht überschreiben.
                if (_state.value.weekStart != requested) return@launch
                _state.update { it.withResult(result) }
            }
```
Rolle aus der Session: `isParent = sessions.current?.role == Role.PARENT` (`RecipeListViewModel.kt:48`).
Uhr: Konstruktorparameter `clock: Clock = Clock.system(BerlinZone)` als letzter Parameter (`WeekPlanViewModel.kt:111`).

### SCREEN_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipes/RecipeListScreen.kt:35-50
@Composable
fun RecipeListRoute(
    args: RecipeListArgs,
    onOpen: (recipeId: String) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<RecipeListViewModel> { parametersOf(args) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Nach Anlegen, Ändern oder Löschen hat sich die Liste evtl. geändert.
    LifecycleResumeEffect(Unit) {
        viewModel.onEvent(RecipeListEvent.Refresh)
        onPauseOrDispose {}
    }
    RecipeListScreen(state = state, onEvent = viewModel::onEvent, onOpen = onOpen, onCreate = onCreate, onBack = onBack)
}
```
Wochenkopf (zum Wiederverwenden kopieren, nicht extrahieren — eigener State-Typ):
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt:181-201
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = { onEvent(WeekPlanEvent.PreviousWeek) }, enabled = state.canGoPrevious) {
            val previous = stringResource(R.string.week_previous)
            Text("‹", modifier = Modifier.semantics { contentDescription = previous })
        }
        Text(
            stringResource(R.string.week_title, formatDate(state.weekStart), formatDate(state.weekStart.plusDays(6))),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { onEvent(WeekPlanEvent.NextWeek) }, enabled = state.canGoNext) { … "›" … }
    }
```
Löschen-Knopf:
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/profile/ExceptionsSection.kt:55-57
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.exceptions_delete))
        }
```

### NAVIGATION_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:43-47, 157-163
@Serializable
data class RecipeListRoute(
    val pick: Boolean = false,
)
…
    composable<RecipeViewRoute> { entry ->
        val route = entry.toRoute<RecipeViewRoute>()
        RecipeViewRoute(
            args = RecipeViewArgs(route.recipeId, route.dishDate?.let(LocalDate::parse), route.dishId),
            …
```
Datum in Routen als ISO-String; Args-Klasse je Screen mit genau einem Koin-Parameter (`RecipeArgs.kt:5`: „Koin unterscheidet
Parameter nach Typ“).

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteCookingRepositoryTest.kt:23-39, 70-75
class RemoteCookingRepositoryTest {
    private val json = createJson()
    private val monday = LocalDate.of(2026, 10, 19)
    private lateinit var server: MockWebServer
    private lateinit var repository: RemoteCookingRepository

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
        val retrofit = createRetrofit(ServerConfig(server.url("/").toString()), OkHttpClient(), json)
        repository = RemoteCookingRepository(retrofit.create(MealsApi::class.java), json)
    }
    …
    @Test
    fun `REQ-MEAL-033 status maps open days cooks and ready flag`() =
        runTest {
            enqueue(200, statusJson())
            val status = (repository.status(monday) as ApiResult.Success).data
```
ViewModel-Tests: `@get:Rule val mainDispatcher = MainDispatcherRule()`, `InMemorySessionStore(parentSession|childSession)`,
`Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))` → Planwoche = `planWeek` (2026-10-19) aus
`fakes/Fakes.kt`. Screen-Tests: `@RunWith(RobolectricTestRunner::class)`, `createComposeRule()`, `FamilyPlannerTheme`.
Testnamen beginnen mit der REQ-ID.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/shopping/Models.kt` | CREATE | `ShoppingList`, `ShoppingEntry`, `ShoppingSource`, `ShoppingSections`, `splitByChecked` |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/shopping/ShoppingRepository.kt` | CREATE | Repository-Interface |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/shopping/UseCases.kt` | CREATE | Laden, Hinzufügen (mit Validierung), Abhaken, Löschen + Bündel |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/shopping/Mappers.kt` | CREATE | API → Domäne |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/shopping/RemoteShoppingRepository.kt` | CREATE | Retrofit-Aufrufe |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/shopping/ShoppingListViewModel.kt` | CREATE | State, Events, Args |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/shopping/ShoppingListScreen.kt` | CREATE | Route + Screen + Zeilen |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | UPDATE | Koin: Repository, Use Cases, ViewModel |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt` | UPDATE | `ShoppingListRoute`, Home-Knopf, Graph, Link aus dem Essensplan |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusActions.kt` | UPDATE | `onOpenShopping` |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/StatusCards.kt` | UPDATE | Link „Einkaufsliste ›“ in `WeekStatusCard` |
| `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt` | UPDATE | Callback `onOpenShopping` durchreichen |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | neue Texte |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | Home-Knopf verdrahten |
| `android/app/build.gradle.kts` | UPDATE | `versionCode = 7`, `versionName = "0.6.0"` |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/ShoppingFakes.kt` | CREATE | `FakeShoppingRepository`, Fixtures |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/domain/shopping/UseCasesTest.kt` | CREATE | Domäne/Use Cases |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteShoppingRepositoryTest.kt` | CREATE | MockWebServer |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/ShoppingViewModelTest.kt` | CREATE | ViewModel |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/ShoppingScreensTest.kt` | CREATE | Compose (Liste, Kinder, Link in der Statuskarte) |
| `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/MealsModuleTest.kt` | UPDATE | ViewModel aus dem Graph |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 6b)“, Kopfzeile |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-040/041: App-Tests, Status |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase 6: Plan-Verweis (bereits in diesem Planungsschritt), nach Abnahme `complete` |
| `android/README.md` | UPDATE | Abnahme-Checkliste Phase 6b |

## NOT Building

- Keine Änderung an `api/` oder `backend/` (Vertrag aus 6a reicht; Gegenprobe `git diff main -- api/ backend/` leer).
- Kein Offline-Cache der Einkaufsliste, kein Offline-Schreiben (Entscheidung „nur online“).
- Keine Einheiten-Umrechnung, keine Client-Zusammenführung — die Liste kommt fertig vom Server.
- Keine Bearbeitung freier Einträge (nur hinzufügen/löschen); keine Bearbeitung berechneter Einträge.
- Kein „Alle abhaken“/„Erledigte ausblenden“, keine Sortierung durch Nutzer, kein Teilen/Export, keine Picnic-Übergabe.
- Keine Benachrichtigung bei Änderungen (Phase 7).
- Kein Bestätigungsdialog beim Löschen freier Einträge.

---

## Step-by-Step Tasks

TDD: je Task zuerst die Tests (RED), dann Implementierung (GREEN). Alle Testnamen beginnen mit `REQ-MEAL-040` bzw. `REQ-MEAL-041`.

### Task 1: Domäne `shopping` (Modelle, Repository, Use Cases)
- **ACTION**: `domain/shopping/Models.kt`, `ShoppingRepository.kt`, `UseCases.kt` anlegen; Tests `domain/shopping/UseCasesTest.kt`
  und `fakes/ShoppingFakes.kt` zuerst.
- **IMPLEMENT**:
  ```kotlin
  package de.familyplanner.android.feature.meals.domain.shopping

  /** Gericht, aus dem ein Eintrag stammt. */
  data class ShoppingSource(val dishId: String, val dishName: String, val date: LocalDate)

  /** Eintrag der Liste; [itemId] nur bei freien Einträgen, [key] zum Abhaken (vom Server, nie selbst bauen). */
  data class ShoppingEntry(
      val key: String,
      val name: String,
      val quantity: Double?,
      val unit: String?,
      val checked: Boolean,
      val itemId: String?,
      val sources: List<ShoppingSource>,
  ) {
      val isFreeItem: Boolean get() = itemId != null
      /** „625 g Vollkornspaghetti“, „Salz“ — gleiche Darstellung wie Zutaten. */
      fun displayText(): String = Ingredient(name, quantity, unit).displayText()
  }

  data class ShoppingList(val weekStart: LocalDate, val entries: List<ShoppingEntry>)

  /** Offene Einträge zuerst, erledigte am Ende — Reihenfolge innerhalb wie vom Server. */
  data class ShoppingSections(val open: List<ShoppingEntry>, val done: List<ShoppingEntry>)

  fun splitByChecked(entries: List<ShoppingEntry>): ShoppingSections =
      entries.partition { !it.checked }.let { (open, done) -> ShoppingSections(open, done) }
  ```
  Repository:
  ```kotlin
  interface ShoppingRepository {
      suspend fun list(weekStart: LocalDate): ApiResult<ShoppingList>
      suspend fun addItem(weekStart: LocalDate, item: Ingredient): ApiResult<ShoppingList>
      suspend fun deleteItem(weekStart: LocalDate, itemId: String): ApiResult<Unit>
      suspend fun setChecked(weekStart: LocalDate, key: String, checked: Boolean): ApiResult<ShoppingList>
  }
  ```
  Use Cases (Datei `UseCases.kt`):
  - `LoadShoppingListUseCase(repository)` → `invoke(weekStart)`.
  - `AddShoppingItemUseCase(repository)` → `invoke(weekStart, text: String, freeItemCount: Int)`:
    `validateShoppingItem(text, freeItemCount)?.let { return it }`, dann `repository.addItem(weekStart, parseIngredientLine(text.trim()))`.
  - `validateShoppingItem` (internal, gleiche Datei) in Reihenfolge:
    1. `text.isBlank()` → `"Bitte einen Eintrag eingeben."`
    2. `text.trim().length > PlanLimits.INGREDIENT_MAX` → `"Ein Eintrag darf höchstens ${PlanLimits.INGREDIENT_MAX} Zeichen haben."`
    3. `freeItemCount >= ShoppingLimits.FREE_ITEMS_MAX` → `"Höchstens ${ShoppingLimits.FREE_ITEMS_MAX} freie Einträge."`
  - `object ShoppingLimits { const val FREE_ITEMS_MAX = 50 }` mit KDoc „Grenze wie im Backend (`SHOPPING_ITEMS_MAX_COUNT`)“ —
    in `Models.kt` oder eigene `ShoppingLimits.kt` (wie `RecipeLimits.kt`; bevorzugt eigene Datei).
  - `SetShoppingCheckUseCase(repository)` → `invoke(weekStart, key, checked)`.
  - `DeleteShoppingItemUseCase(repository)` → `invoke(weekStart, itemId)`; **`AppError.NotFound` wird zu `Success(Unit)`**
    (schon gelöscht, Muster „404 gilt als gelöscht“ aus Rezepten/Gerichten).
  - `class ShoppingUseCases(val load, val add, val check, val delete)`.
- **MIRROR**: USE_CASE_PATTERN, ERROR_HANDLING
- **IMPORTS**: `de.familyplanner.android.core.data.{ApiResult, AppError, validationFailure}`,
  `de.familyplanner.android.feature.meals.domain.recipe.{Ingredient, parseIngredientLine, displayText}`,
  `de.familyplanner.android.feature.meals.domain.plan.PlanLimits`, `java.time.LocalDate`
- **GOTCHA**: `parseIngredientLine` liefert bei nicht zerlegbaren Zeilen `Ingredient(text)` — der Name kann dann bis 80 Zeichen lang
  sein; eine zerlegte Einheit ist durch `isUnit` schon ≤ 20 Zeichen. Den Schlüssel `key` **nie** im Client berechnen (Maskierung siehe
  meals.md 6a). `displayText` ist eine Extension in `domain/recipe/IngredientLines.kt` — importieren, nicht kopieren.
- **VALIDATE**: `UseCasesTest`: `splitByChecked` (Reihenfolge bleibt, leere Liste), `displayText` („625 g Vollkornspaghetti“,
  „Salz“, „12500 g Mehl“ für Summe > 10000), Hinzufügen zerlegt „2 kg Kartoffeln“ → `Ingredient("Kartoffeln", 2.0, "kg")`, leerer/
  zu langer Text und 50 vorhandene freie Einträge → Validierungsfehler **ohne** Repository-Aufruf, Löschen 404 → Success,
  Löschen 403 → Failure(Forbidden).

### Task 2: Data `shopping` (Mapper, Remote-Repository)
- **ACTION**: `data/shopping/Mappers.kt`, `data/shopping/RemoteShoppingRepository.kt`; Test `data/RemoteShoppingRepositoryTest.kt` zuerst.
- **IMPLEMENT**:
  ```kotlin
  internal fun ApiShoppingEntrySource.toDomain(): ShoppingSource? = parseDate(date)?.let { ShoppingSource(dishId, dishName, it) }

  internal fun ApiShoppingEntry.toDomain(): ShoppingEntry? =
      sources.mapAllOrNull { it.toDomain() }?.let { ShoppingEntry(key, name, quantity, unit, checked, itemId, it) }

  /** Der Generator nennt `entries` in `propertyEntries` um (Kotlin-Reservierung); JSON bleibt `entries`. */
  internal fun ApiShoppingList.toDomain(): ShoppingList? {
      val start = parseDate(weekStart) ?: return null
      return propertyEntries.mapAllOrNull { it.toDomain() }?.let { ShoppingList(start, it) }
  }
  ```
  Repository:
  ```kotlin
  class RemoteShoppingRepository(private val meals: MealsApi, private val json: Json) : ShoppingRepository {
      override suspend fun list(weekStart: LocalDate) =
          apiCall(json, { meals.getShoppingList(weekStart.toString()) }) { it.data?.toDomain() }
      override suspend fun addItem(weekStart: LocalDate, item: Ingredient) =
          apiCall(json, { meals.addShoppingItem(weekStart.toString(), item.toApi()) }) { it.data?.toDomain() }
      override suspend fun deleteItem(weekStart: LocalDate, itemId: String) =
          apiCallNoContent(json) { meals.deleteShoppingItem(weekStart.toString(), itemId) }
      override suspend fun setChecked(weekStart: LocalDate, key: String, checked: Boolean) =
          apiCall(json, { meals.setShoppingEntryChecked(weekStart.toString(), CheckShoppingEntryRequest(key, checked)) }) { it.data?.toDomain() }
  }
  ```
  (Rückgabetypen explizit als `ApiResult<…>` ausschreiben wie im Vorbild.)
- **MIRROR**: REPOSITORY_PATTERN, MAPPER_PATTERN, NAMING_CONVENTION
- **IMPORTS**: `de.familyplanner.android.feature.meals.data.recipe.toApi` (Ingredient → ApiIngredient, `internal` im selben Modul),
  `de.familyplanner.android.feature.meals.data.{parseDate, mapAllOrNull}`, `de.familyplanner.api.model.CheckShoppingEntryRequest`,
  `de.familyplanner.api.model.ShoppingList as ApiShoppingList`, `…ShoppingEntry as ApiShoppingEntry`,
  `…ShoppingEntrySource as ApiShoppingEntrySource`, `de.familyplanner.api.client.MealsApi`
- **GOTCHA**: `propertyEntries` statt `entries` (Bericht 6a). `addShoppingItem` antwortet mit **201 und Body** → `apiCall`,
  nicht `apiCallNoContent`. `deleteShoppingItem` antwortet mit 204.
- **VALIDATE**: MockWebServer-Tests: Liste mappt Menge/Einheit/Häkchen/`itemId`/Quellen (Datum); Eintrag ohne `quantity`/`unit` → `null`;
  kaputtes Quelldatum → `AppError.Server`; `POST` sendet `{"name":"Kartoffeln","quantity":2.0,"unit":"kg"}` an
  `/api/v1/meals/weeks/2026-10-19/shopping-list/items` (Pfad und Body per `server.takeRequest()` prüfen) und mappt die 201-Antwort;
  `PUT …/checks` sendet `{"key":"…","checked":true}`; `DELETE` 204 → Success; 403 mit `FORBIDDEN` → `AppError.Forbidden`;
  400 mit `VALIDATION_FAILED` „Höchstens 50 freie Einträge“ → `AppError.Validation` mit dieser Meldung; 404 → `AppError.NotFound`.

### Task 3: ViewModel
- **ACTION**: `ui/shopping/ShoppingListViewModel.kt` (inkl. `ShoppingListArgs`, `ShoppingListUiState`, `ShoppingListEvent`);
  Test `ui/ShoppingViewModelTest.kt` zuerst.
- **IMPLEMENT**:
  ```kotlin
  /** [weekStart] `null` = Planwoche (Montag nach heute, Berlin). Ein Koin-Parameter. */
  data class ShoppingListArgs(val weekStart: LocalDate? = null)

  data class ShoppingListUiState(
      val weekStart: LocalDate,
      val list: ShoppingList? = null,
      val isLoading: Boolean = false,
      val error: AppError? = null,
      val isParent: Boolean = false,
      val newItem: String = "",
      val isBusy: Boolean = false,
      val actionError: AppError? = null,
  ) {
      val sections: ShoppingSections get() = splitByChecked(list?.entries.orEmpty())
      val canEdit: Boolean get() = isParent && list != null
      val freeItemCount: Int get() = list?.entries?.count { it.isFreeItem } ?: 0
      val isEmpty: Boolean get() = list != null && list.entries.isEmpty()
      val canGoPrevious: Boolean get() = weekStart.minusWeeks(1).year >= AttendanceLimits.MIN_YEAR
      val canGoNext: Boolean get() = weekStart.plusWeeks(1).year <= AttendanceLimits.MAX_YEAR
  }

  sealed interface ShoppingListEvent {
      data object Refresh : ShoppingListEvent
      data object PreviousWeek : ShoppingListEvent
      data object NextWeek : ShoppingListEvent
      data class NewItemChanged(val value: String) : ShoppingListEvent
      data object AddItem : ShoppingListEvent
      data class SetChecked(val key: String, val checked: Boolean) : ShoppingListEvent
      data class DeleteItem(val itemId: String) : ShoppingListEvent
  }

  class ShoppingListViewModel(
      args: ShoppingListArgs,
      sessions: SessionStore,
      private val shopping: ShoppingUseCases,
      clock: Clock = Clock.system(BerlinZone),
  ) : ViewModel()
  ```
  Verhalten:
  - Start: `weekStart = args.weekStart ?: currentPlanWeek(clock.instant())`; `init { load() }`.
  - `load()`: wie `WeekPlanViewModel.load` — `loadJob?.cancel()`, `isLoading = true, error = null`, Antwort einer anderen Woche
    verwerfen; Success → `list = data`; Failure → `list = null, error = …`.
  - `PreviousWeek`/`NextWeek` (nur wenn `canGo…`): `weekStart` ändern, `list = null, error = null, actionError = null, newItem = ""`,
    dann `load()`.
  - `NewItemChanged`: `newItem = value.take(PlanLimits.INGREDIENT_MAX)`.
  - Schreib-Events nur wenn `canEdit && !isBusy` (sonst ignorieren). Gemeinsame Hilfsfunktion
    `write(block: suspend (LocalDate) -> ApiResult<ShoppingList?>, onSuccess: …)` ist erlaubt, aber nur, wenn sie die Funktionen
    < 50 Zeilen hält; sonst drei kleine private Funktionen nach dem `confirmCancel`-Muster:
    - `AddItem`: `shopping.add(week, newItem, freeItemCount)`; Success → `list = data, newItem = ""`; Failure → `actionError`
      (Text bleibt im Feld).
    - `SetChecked`: `shopping.check(week, key, checked)`; Success → `list = data`; Failure → `actionError`; bei `AppError.NotFound`
      zusätzlich `load()` (Eintrag ist inzwischen weg, z. B. Gericht geändert).
    - `DeleteItem`: `shopping.delete(week, itemId)`; Success → `load()`; Failure → `actionError`.
    - Immer: `isBusy = true, actionError = null` vor dem Request; nach der Antwort `isBusy = false`; hat sich `weekStart`
      inzwischen geändert, nur `isBusy = false` setzen und das Ergebnis verwerfen.
  - `Refresh` lädt neu und setzt `actionError = null` (frischer Stand).
- **MIRROR**: VIEWMODEL_PATTERN
- **IMPORTS**: `androidx.lifecycle.{ViewModel, viewModelScope}`, `de.familyplanner.android.core.data.{ApiResult, AppError}`,
  `de.familyplanner.android.core.data.session.{Role, SessionStore}`, `de.familyplanner.android.core.ui.BerlinZone`,
  `…domain.attendance.{AttendanceLimits, currentPlanWeek}`, `…domain.plan.PlanLimits`, `…domain.shopping.*`,
  `kotlinx.coroutines.{Job, launch}`, `kotlinx.coroutines.flow.{MutableStateFlow, StateFlow, asStateFlow, update}`, `java.time.{Clock, LocalDate}`
- **GOTCHA**: `when` über das versiegelte `ShoppingListEvent` ohne `else`. detekt: Funktionen < 50 Zeilen, `ThrowsCount`/
  `ComplexCondition` beachten; `CyclomaticComplexMethod` → `onEvent` delegiert an kleine Funktionen. Kinder bekommen keine Schreib-
  Requests (Guard `canEdit`), auch wenn ein Event ankommt.
- **VALIDATE**: `ShoppingViewModelTest` (Clock Samstag 17.10.2026 → Planwoche 19.10.): Startwoche = Planwoche bzw. `args.weekStart`;
  Laden füllt `sections`; Wochenwechsel lädt die neue Woche und verwirft die späte Antwort der alten (Gate im Fake); Hinzufügen leert
  das Feld und übernimmt die Liste; Validierungsfehler ohne Request (`added` leer) und Text bleibt; Abhaken sendet key/checked und
  übernimmt die Liste; 404 beim Abhaken lädt neu; Löschen lädt neu; zweiter Schreib-Event während `isBusy` erzeugt keinen zweiten
  Request (Gate); Kind: `canEdit = false`, `AddItem`/`SetChecked`/`DeleteItem` erzeugen keinen Request; Eingabe auf 80 Zeichen begrenzt;
  offline (`AppError.Network`) → `error`, `list = null`.

### Task 4: Screen
- **ACTION**: `ui/shopping/ShoppingListScreen.kt` mit `ShoppingListRoute(args, onBack)` und `ShoppingListScreen(state, onEvent, onBack)`;
  Strings in `strings.xml`; Test `ui/ShoppingScreensTest.kt` zuerst.
- **IMPLEMENT**:
  - Route nach SCREEN_PATTERN (`koinViewModel<ShoppingListViewModel> { parametersOf(args) }`, `LifecycleResumeEffect` → `Refresh`).
  - Screen: `PullToRefreshBox` + `LazyColumn(Modifier.fillMaxSize().padding(horizontal = 24.dp), spacedBy(12.dp))`:
    1. Titel `shopping_title` (`headlineMedium`)
    2. Wochenkopf (Kopie von `WeekHeader` mit `ShoppingListEvent.PreviousWeek/NextWeek`, Strings `week_title`, `week_previous`, `week_next`)
    3. `ErrorText(state.error)`, `ErrorText(state.actionError)`
    4. Leerhinweis `shopping_empty`, wenn `state.isEmpty`
    5. `items(state.sections.open, key = { it.key })` → `ShoppingRow`
    6. wenn `done` nicht leer: Überschrift `shopping_done` mit Anzahl (`titleMedium`), dann `items(done, key = { it.key })`
    7. wenn `state.canEdit`: `AddItemRow` — `OutlinedTextField` (Label `shopping_add_label`, Placeholder `shopping_add_hint`,
       `singleLine`, `ImeAction.Done` → `AddItem`) + `Button` „Hinzufügen“ (`enabled = !isBusy && newItem.isNotBlank()`)
    8. `TextButton` „Zurück“ (`R.string.back`)
  - `ShoppingRow(entry, canEdit, isBusy, onEvent)`: `Row(verticalAlignment = CenterVertically)`:
    - Eltern: `Checkbox(checked = entry.checked, onCheckedChange = { onEvent(SetChecked(entry.key, it)) }, enabled = !isBusy)`;
      Kinder: kein Checkbox; erledigte Einträge mit Präfix „✓ “.
    - `Column(weight(1f))`: `Text(entry.displayText(), bodyLarge, textDecoration = if (checked) LineThrough else null)`,
      darunter `Text(sourcesText(entry), bodySmall)`.
    - Eltern + `entry.itemId != null`: `IconButton` mit `Icons.Filled.Delete`, `contentDescription = shopping_delete`.
  - `sourcesText`: freie Einträge → `stringResource(R.string.shopping_own_item)`; sonst
    `entry.sources.joinToString(", ") { "${it.dishName} (${weekdayShort(it.date.dayOfWeek)})" }`. Diese Formatierung als
    **reine Funktion** `formatSources(sources: List<ShoppingSource>): String` in `ui/shopping/ShoppingListScreen.kt` (internal) oder
    `ui/Labels.kt` ablegen, damit sie ohne Compose testbar ist.
  - Checkbox-Zeile per `Modifier.toggleable` o. Ä. ist **nicht** nötig; die Checkbox selbst reicht (Barrierefreiheit: Checkbox hat
    Rolle; zusätzlich `Modifier.semantics { contentDescription = entry.displayText() }` an der Checkbox, damit Tests und TalkBack
    sie finden).
  - Strings (Deutsch, in `feature/meals/src/main/res/values/strings.xml`):
    ```xml
    <string name="home_shopping">Einkaufsliste</string>
    <string name="shopping_title">Einkaufsliste</string>
    <string name="shopping_empty">Noch nichts auf der Liste. Zutaten erscheinen, sobald Gerichte freigegeben sind.</string>
    <string name="shopping_done">Erledigt (%1$d)</string>
    <string name="shopping_own_item">Eigener Eintrag</string>
    <string name="shopping_add_label">Eintrag hinzufügen</string>
    <string name="shopping_add_hint">z. B. 2 kg Kartoffeln</string>
    <string name="shopping_add">Hinzufügen</string>
    <string name="shopping_delete">Eintrag löschen</string>
    <string name="status_shopping">Einkaufsliste ›</string>
    ```
- **MIRROR**: SCREEN_PATTERN
- **IMPORTS**: wie `RecipeListScreen.kt` plus `androidx.compose.material3.{Checkbox, Icon, IconButton}`,
  `androidx.compose.material.icons.Icons`, `androidx.compose.material.icons.filled.Delete`,
  `androidx.compose.ui.text.style.TextDecoration`, `androidx.compose.foundation.text.KeyboardActions/KeyboardOptions`,
  `androidx.compose.ui.text.input.ImeAction`, `androidx.compose.ui.semantics.{contentDescription, semantics}`,
  `de.familyplanner.android.core.ui.formatDate`, `de.familyplanner.android.feature.meals.ui.weekdayShort`
- **GOTCHA**: `LazyColumn`-Keys müssen eindeutig sein — `key` vom Server ist eindeutig (offen und erledigt sind disjunkt). Datei unter
  ~250 Zeilen halten; bei Bedarf Zeilen-Composables in `ShoppingRows.kt` auslagern. `formatQuantity` nutzt Komma („2,5“).
- **VALIDATE**: `ShoppingScreensTest`: Eltern sehen Einträge mit Menge und Herkunft („625 g Vollkornspaghetti“, „Carbonara (Di)“);
  erledigte stehen unter „Erledigt (1)“ (Reihenfolge: offener Eintrag vor der Überschrift); Checkbox-Klick sendet
  `SetChecked(key, true)`; freier Eintrag zeigt „Eigener Eintrag“ und Lösch-Knopf, Klick sendet `DeleteItem(id)`; berechneter Eintrag
  hat keinen Lösch-Knopf; Eingabe + „Hinzufügen“ sendet `NewItemChanged` und `AddItem`; Kind: kein Eingabefeld, keine Checkbox, kein
  Lösch-Knopf, erledigter Eintrag mit „✓ “; leere Liste zeigt den Leerhinweis; `actionError` (Validation „Höchstens 50 freie Einträge“)
  wird angezeigt.

### Task 5: Navigation, Einstiege, Koin
- **ACTION**: Route, Home-Knopf, Link in der Wochenstatus-Karte, Koin; `MealsModuleTest` und Screen-Test für den Link zuerst.
- **IMPLEMENT**:
  - `MealsNavigation.kt`:
    ```kotlin
    /** [weekStart] ISO-Datum (Montag); `null` = Planwoche. */
    @Serializable
    data class ShoppingListRoute(val weekStart: String? = null)
    ```
    `MealsHomeActions(onOpenPlan, onOpenWeek, onOpenRecipes, onOpenShopping)` mit viertem `OutlinedButton` (`R.string.home_shopping`).
    In `mealsGraph`:
    ```kotlin
    composable<ShoppingListRoute> { entry ->
        val route = entry.toRoute<ShoppingListRoute>()
        ShoppingListRoute(ShoppingListArgs(route.weekStart?.let(LocalDate::parse)), onBack = { navController.popBackStack() })
    }
    ```
    `WeekPlanRoute(…, onOpenShopping = { week -> navController.navigate(ShoppingListRoute(week.toString())) })`.
  - `StatusActions`: neues Feld `val onOpenShopping: (weekStart: LocalDate) -> Unit`.
  - `WeekPlanScreen`/`WeekPlanRoute`: neuer Parameter `onOpenShopping: (LocalDate) -> Unit = {}` **als letzter Parameter** (Default
    hält die bestehenden Positionsaufrufe in `PlanScreensTest`, `CookingScreensTest`, `RecipeScreensTest` kompilierbar); in
    `StatusCards(…)` an `StatusActions(…, onOpenShopping = onOpenShopping)` weiterreichen.
  - `WeekStatusCard`: wenn `status != null`, am Ende der Karte
    `TextButton(onClick = { actions.onOpenShopping(status.weekStart) }) { Text(stringResource(R.string.status_shopping)) }` — für alle
    Rollen.
  - `MealsModule.kt` (neuer Block `// shopping`):
    ```kotlin
    single<ShoppingRepository> { RemoteShoppingRepository(get(), get()) }
    factory {
        ShoppingUseCases(
            LoadShoppingListUseCase(get()),
            AddShoppingItemUseCase(get()),
            SetShoppingCheckUseCase(get()),
            DeleteShoppingItemUseCase(get()),
        )
    }
    viewModel { params -> ShoppingListViewModel(params.get(), get(), get()) }
    ```
  - `AppNavHost.kt`: `onOpenShopping = { navController.navigate(ShoppingListRoute()) }` + Import
    `de.familyplanner.android.feature.meals.ShoppingListRoute`.
- **MIRROR**: NAVIGATION_PATTERN
- **IMPORTS**: siehe oben; in `MealsNavigation.kt` die Composable-Funktion `ui.shopping.ShoppingListRoute` und die gleichnamige
  Routen-Klasse kollidieren — wie bei `RecipeListRoute` gelöst: die Composable wird über ihr Paket importiert
  (`import de.familyplanner.android.feature.meals.ui.shopping.ShoppingListRoute`), die Klasse liegt im Paket
  `de.familyplanner.android.feature.meals` selbst. Muster exakt wie `RecipeListRoute` (Klasse in `MealsNavigation.kt`, Composable
  importiert) übernehmen; Kotlin löst `ShoppingListRoute(args, …)` (Composable) und `ShoppingListRoute(weekStart)` (Klasse) über die
  Signatur auf, `composable<ShoppingListRoute>` meint die Klasse.
- **GOTCHA**: Koin unterscheidet Parameter nach Typ → genau ein `ShoppingListArgs`-Parameter. `StatusActions` hat keinen Default für
  `onOpenShopping` (einziger Erzeuger ist `WeekPlanScreen`).
- **VALIDATE**: `MealsModuleTest`: `koin.get<ShoppingListViewModel> { parametersOf(ShoppingListArgs()) }` und mit Woche;
  `ShoppingScreensTest` (oder `CookingScreensTest`): Wochenstatus-Karte zeigt „Einkaufsliste ›“, Klick ruft `onOpenShopping(planWeek)`;
  ohne Status kein Link. `./gradlew :app:compileDebugKotlin`.

### Task 6: Version, Doku, Traceability, Abnahme-Checkliste
- **ACTION**: Doku nachziehen (keine Codeänderung).
- **IMPLEMENT**:
  - `android/app/build.gradle.kts`: `versionCode = 7`, `versionName = "0.6.0"`.
  - `docs/03-design/meals.md`: Kopfzeile Zeile 6 „(Server-Anteil: Einkaufsliste, Phase 6a; App: Phase 6b)“ bleibt; Abschnitt
    „Einkaufsliste (Phase 6a)“ Satz „Oberfläche und Offline-Anzeige folgen in Phase 6b“ → „Oberfläche folgt in Phase 6b (nur online,
    Abschnitt „App-Seite (Phase 6b)“)“. Neuer Abschnitt **„App-Seite (Phase 6b)“** am Ende, Stil wie 5b: Pakete `domain/shopping`,
    `data/shopping`, `ui/shopping`; Tabelle Screen/Rolle/Inhalt; Regeln: Startwoche = Planwoche, Einstiege, Rollen, Abschnitt
    „Erledigt“, freier Eintrag = eine Zeile über `parseIngredientLine`, Client-Validierung (leer, 80 Zeichen, 50 freie Einträge),
    ein Schreibvorgang gleichzeitig, Server-Antwort ersetzt Liste, 404 beim Löschen = gelöscht, 404 beim Abhaken → neu laden, kein
    Cache/offline nur Meldung, `key` nie im Client bauen, `propertyEntries`. Unit-Testfälle auflisten.
  - `docs/04-verification/traceability.md` Zeilen REQ-MEAL-040/041: Unit-Spalte ergänzen um
    „App: `UseCasesTest` (shopping), `ShoppingViewModelTest`, `ShoppingScreensTest`“, Integration um „`RemoteShoppingRepositoryTest`“,
    Abnahme „manuell: Abnahme Phase 6b ([android/README.md](../../android/README.md))“, Status „umgesetzt (Backend + App)“.
  - `android/README.md`: neuer Abschnitt „## Abnahme-Checkliste Phase 6b (manuell, echte Geräte gegen den Pi)“ mit den Punkten aus
    „Manual Validation“ unten (unangehakt).
  - `docs/01-requirements/meals.prd.md` Phase-6-Zeile: bleibt `in-progress`, PRP-Spalte „6b: `.claude/PRPs/plans/einkaufsliste-app.plan.md`“
    (wird schon in diesem Planungsschritt gesetzt); nach Implementierung „6b umgesetzt (Bericht: …)“, nach Geräte-Abnahme `complete`.
- **MIRROR**: bestehende Abschnitte „App-Seite (Phase 5b)“ und Checkliste Phase 5b.
- **GOTCHA**: Keine neuen ADRs nötig (keine Abweichung: kein Cache = ADR-0011 bleibt unberührt).
- **VALIDATE**: Links in Markdown prüfen; `git diff main -- api/ backend/` leer.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| `REQ-MEAL-041 split keeps server order and moves checked entries to done` | [A offen, B erledigt, C offen] | open = [A, C], done = [B] | |
| `REQ-MEAL-040 entry display text matches ingredient format` | 625.0 g Vollkornspaghetti / Salz / 12500 g Mehl | „625 g Vollkornspaghetti“, „Salz“, „12500 g Mehl“ | Summe > 10000 |
| `REQ-MEAL-041 add item parses quantity and unit` | „2 kg Kartoffeln“ | `addItem(week, Ingredient("Kartoffeln", 2.0, "kg"))` | |
| `REQ-MEAL-041 add item keeps unparsable text as name` | „Saft von 1 Limette“ | `Ingredient("Saft von 1 Limette")` | ja |
| `REQ-MEAL-041 blank item is rejected without request` | „  “ | Validation „Bitte einen Eintrag eingeben.“ | ja |
| `REQ-MEAL-041 too long item is rejected` | 81 Zeichen | Validation „… höchstens 80 Zeichen …“ | ja |
| `REQ-MEAL-041 fifty free items are rejected without request` | freeItemCount = 50 | Validation „Höchstens 50 freie Einträge.“ | ja |
| `REQ-MEAL-041 deleting an already deleted item succeeds` | 404 | `Success(Unit)` | ja |
| `REQ-MEAL-040 repository maps entries sources and checks` | JSON mit 3 Einträgen | Domäne inkl. `itemId`, Datum | |
| `REQ-MEAL-040 unreadable source date is a server error` | `"date":"x"` | `AppError.Server` | ja |
| `REQ-MEAL-041 add item posts ingredient and returns list` | 201 | Pfad/Body korrekt, Liste | |
| `REQ-MEAL-041 check puts key and flag` | 200 | Body `{"key":…,"checked":true}` | |
| `REQ-MEAL-041 child write is forbidden` | 403 FORBIDDEN | `AppError.Forbidden` | ja |
| `REQ-MEAL-040 opens plan week by default` | Clock Sa 17.10. | `weekStart = 2026-10-19` | |
| `REQ-MEAL-040 late answer of previous week is discarded` | Gate | Liste der neuen Woche | Rennen |
| `REQ-MEAL-041 second write while busy sends no request` | Gate | genau 1 Request | Doppelklick |
| `REQ-MEAL-041 child cannot write` | childSession | keine Requests, `canEdit = false` | Rechte |
| `REQ-MEAL-041 unknown key reloads list` | 404 beim Abhaken | `actionError` + neuer `list`-Aufruf | ja |
| `REQ-MEAL-040 offline shows network error` | `AppError.Network` | `error`, `list = null` | Netzwerk |
| Screen: Eltern/Kind/Leer/Erledigt/Link in Statuskarte | UI-States | siehe Task 4/5 | |

### Edge Cases Checklist
- [x] Leere Liste (keine freigegebenen Gerichte, keine freien Einträge) → Leerhinweis
- [x] Maximale Größe: 50 freie Einträge (Client-Vorprüfung + Server-400), Eingabe 80 Zeichen
- [x] Ungültige Serverdaten (Datum) → `AppError.Server`
- [x] Gleichzeitiger Zugriff: zweiter Schreibvorgang blockiert; anderes Elternteil hakt parallel ab → Server-Antwort/Refresh zeigt Stand
- [x] Netzwerkfehler → Meldung „Server nicht erreichbar …“
- [x] Keine Berechtigung (Kind) → keine Schreib-UI; Server 403 → Meldung
- [x] Eintrag verschwunden (Gericht geändert) beim Abhaken → 404 → neu laden
- [x] Menge ändert sich nach dem Abhaken (Server macht Eintrag wieder offen) → erscheint wieder oben (Server-Regel, nur anzeigen)

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
EXPECT: Gericht freigeben → Einkaufsliste zeigt seine Zutaten; abhaken, freien Eintrag hinzufügen/löschen; `GET
/api/v1/meals/weeks/<Montag>/shopping-list` zeigt denselben Stand.

### Manual Validation (Abnahme Phase 6b – echte Handys gegen den Pi)
- [ ] Pi läuft mit Image inkl. Migration V6 (6a); Release-APK 0.6.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [ ] Planwoche mit mindestens zwei freigegebenen Gerichten mit gemeinsamer Zutat (z. B. „2 Stange(n) Lauch“ in beiden) und einem
      Kindergericht „wartet auf Freigabe“
- [ ] Elternhandy: Startseite → „Einkaufsliste“ öffnet die Planwoche; Lauch steht einmal mit addierter Menge und beiden Gerichten als
      Herkunft; „Salz“ ohne Menge einmal; Zutaten des wartenden Gerichts fehlen
- [ ] „200 g Sahne“ und „1 Becher Sahne“ bleiben getrennte Einträge
- [ ] Essensplan → Wochenstatus → „Einkaufsliste ›“ öffnet dieselbe Woche
- [ ] Abhaken → Eintrag wandert durchgestrichen nach „Erledigt“; erneut tippen → wieder offen
- [ ] „2 kg Kartoffeln“ hinzufügen → erscheint als „2 kg Kartoffeln“ mit „Eigener Eintrag“; löschen → verschwindet
- [ ] Leeres Feld → „Hinzufügen“ deaktiviert
- [ ] Zweites Elternhandy sieht nach Pull-to-Refresh dieselben Häkchen
- [ ] Kind gibt danach ein weiteres Gericht frei bzw. Eltern geben das wartende Gericht frei → neue Zutaten erscheinen; ein abgehakter
      Eintrag mit geänderter Menge ist wieder offen
- [ ] Kinderhandy: Liste sichtbar, keine Checkboxen, kein Eingabefeld, kein Löschen; erledigte Einträge mit „✓“
- [ ] WLAN aus: „Server nicht erreichbar. Bist du im Heim-WLAN?“
- [ ] Erfolgssignal Phase 6: Sonntagsbestellung bei Picnic nur anhand der Liste

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-040 und -041 haben App-Tests mit REQ-ID im Namen
- [ ] Eltern sehen, haken ab, ergänzen und löschen; Kinder lesen nur
- [ ] Einstieg über Startseite und Wochenstatus-Karte
- [ ] 400/403/404/offline mit deutschen Meldungen abgebildet
- [ ] `api/` und `backend/` unverändert; keine neue Bibliothek
- [ ] Geräte-Abnahme dokumentiert (README-Checkliste, Datum in traceability.md)

## Completion Checklist
- [ ] Code folgt den Mustern (`apiCall`, `mapAllOrNull`, Use-Case-Bündel, State/Event-ViewModel, Route/Screen)
- [ ] Fehlerbehandlung wie im Modul (`ErrorText`, `validationFailure`)
- [ ] Kein neues Logging
- [ ] Tests nach den bestehenden Mustern (Fakes statt Mocks, MockWebServer, Robolectric)
- [ ] Keine Magic Numbers (`ShoppingLimits.FREE_ITEMS_MAX`, `PlanLimits.INGREDIENT_MAX`)
- [ ] Doku aktualisiert (meals.md, traceability, README, PRD)
- [ ] Kein Scope-Zuwachs (siehe NOT Building)

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Namenskollision `ShoppingListRoute` (Klasse vs. Composable) verwirrt | M | L | Muster `RecipeListRoute` exakt übernehmen; notfalls Composable `ShoppingListScreenRoute` nennen |
| Viele Abhak-Requests nacheinander fühlen sich träge an (`isBusy` sperrt) | M | L | Checkbox nur während eines Requests gesperrt; LAN-Latenz klein; optimistisches Update bewusst nicht gebaut |
| `WeekPlanScreen`-Signatur ändert sich → Tests brechen | L | L | neuer Parameter mit Default `{}` am Ende |
| Kover sinkt durch Compose-Code | L | M | Screen-Tests für Eltern/Kind/Leer/Erledigt; `formatSources` als reine Funktion getestet |
| Generierter Name `propertyEntries` ändert sich bei Generator-Update | L | M | Mapper-Test gegen echtes JSON fängt es ab |

## Notes
- Phase 7 (Erinnerungen) ändert ebenfalls `feature:meals` (PRD-Hinweis „nicht gleichzeitig dieselben Dateien bearbeiten“): 6b berührt
  `MealsModule.kt`, `MealsNavigation.kt`, `strings.xml`, `StatusCards.kt`, `WeekPlanScreen.kt` — Phase 7 erst danach planen bzw. mergen.
- Die Implementierung erfolgt in einer **neuen Session** (Standard: Sonnet) mit `/ecc:prp-implement .claude/PRPs/plans/einkaufsliste-app.plan.md`,
  auf einem neuen Branch `feat/einkaufsliste-app` von `main`.
