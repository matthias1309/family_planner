# Plan: Rezepte in der App (Phase 5b)

## Summary
`feature:meals` bekommt die Rezeptsammlung: Alle Mitglieder sehen und durchsuchen die Rezepte, Eltern legen sie an, ändern und
löschen sie. Im Gericht-Editor lässt sich ein Rezept wählen; Name und Zutaten werden auf die Portionen (Vorschlag: Anzahl der an dem
Tag Anwesenden) umgerechnet und kopiert, das Gericht merkt sich `recipeId`/`servings`. Zutaten bleiben Textzeilen („500 g Spaghetti“),
die App zerlegt sie in Menge/Einheit/Name und sendet ab jetzt `structuredIngredients`. Im Essensplan zeigt ein Gericht mit Rezept
„Rezept ›“; die Ansicht zeigt Schritte und die Zutaten mit den Mengen des Gerichts. `api/` und `backend/` bleiben unverändert
(Vertrag 0.5.0 aus 5a). Der REWE-Import (Teilen-Menü, WebView, schema.org-Leser, Eigenmarken) folgt als **Phase 5c**.

## User Story
Als Elternteil möchte ich unsere Lieblingsrezepte in der App pflegen, und als kochendes Kind oder Elternteil möchte ich beim Planen
ein Rezept wählen und beim Kochen ansehen, damit niemand Zutaten jede Woche neu abtippt und die Mengen zur Zahl der Esser passen.

## Problem → Solution
Phase 5a liefert Rezepte und strukturierte Zutaten nur über die API; die App sendet weiter Freitextzeilen (Server macht daraus Zutaten
nur mit Name) und kennt keine Rezepte → Rezeptliste mit Suche, Rezeptansicht, Rezept-Editor (nur Eltern), Rezeptauswahl mit
Portionen-Umrechnung im Gericht-Editor, Zerlegung der Zutatenzeilen und `structuredIngredients` beim Speichern, „Rezept ›“ im Plan.

## Metadata
- **Complexity**: Large (≈ 22 neue Dateien, ≈ 20 geänderte; neues Muster: Navigationsergebnis über `SavedStateHandle`)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 5 – Rezeptsammlung, Teil **5b** (App ohne Import), REQ-MEAL-060, -062, -063 (App-Seite)
- **Estimated Files**: ≈ 22 neu, ≈ 20 geändert (inkl. Tests und Doku)

## Entscheidungen des Anwenders (2026-10-18)
| Frage | Entscheidung |
|---|---|
| Zutaten-Eingabe im Gericht- und Rezept-Editor | **Textzeilen** wie bisher („500 g Spaghetti“), die App zerlegt sie automatisch in Menge/Einheit/Name (derselbe Zerleger dient in 5c dem Import) |
| Wo sieht man das Rezept zum Gericht (REQ-MEAL-063)? | **In jeder Plan-Zeile** mit Rezept „Rezept ›“, für alle Mitglieder, nur online |
| Umfang | **Zwei Pläne:** 5b = Rezepte in der App (dieser Plan), 5c = REWE-Import (eigener Plan) |

Entscheidungen dieses Plans (ohne Rückfrage, vertretbare Standards; bei Widerspruch im Review ändern):
- Einstieg „Rezepte“ als dritter Knopf auf der Startseite (alle Mitglieder).
- Suche in der Liste **clientseitig** über die einmal geladene Liste (Familiensammlung ist klein, keine Request-Flut beim Tippen);
  Treffer = Name enthält Suchbegriff ohne Groß-/Kleinschreibung — wie der Server (REQ-MEAL-060).
- Zubereitungsschritte im Editor als **ein Textfeld, ein Schritt pro Zeile** (REWE-Schritte sind einzelne Absätze).
- Rezeptauswahl im Gericht-Editor öffnet die **Rezeptliste im Auswahlmodus** (Wiederverwendung von Liste und Suche); das Ergebnis
  kommt über den `SavedStateHandle` des vorherigen Backstack-Eintrags zurück.
- Portionen-Vorschlag = Anzahl Eltern (immer anwesend, das System kennt keine Eltern-Anwesenheit) + an dem Tag anwesende Kinder,
  begrenzt auf 1–20. **Portionen ändern rechnet die Zutaten neu aus dem Rezept** und überschreibt das Zutatenfeld (Hinweistext).
- Löschen eines Rezepts in der **Rezeptansicht** (Eltern), nicht im Editor (danach zurück zur Liste).
- Rezepte sind **nicht offline** lesbar (PRD „Later“); der Link „Rezept ›“ erscheint nicht beim gespeicherten Stand.

---

## UX Design

### Before
```
┌ Start ──────────────────┐   ┌ Gericht ───────────────────────────┐   ┌ Essensplan ─────────────────────┐
│ [ Essensplan ]          │   │ Tag, Koch/Köchin, Name,            │   │ Mo 19.10. Spaghetti · Anna kocht │
│ ( Anwesenheit & Fristen)│   │ Zutaten (eine pro Zeile) → Server  │   │   500 g Spaghetti, Salz          │
└─────────────────────────┘   │ speichert sie nur als Name         │   │ (kein Rezept)                    │
                              └────────────────────────────────────┘   └──────────────────────────────────┘
```

### After
```
┌ Start ──────────────────┐   ┌ Rezepte ───────────────────────┐   ┌ Rezept ──────────────────────────────┐
│ [ Essensplan ]          │   │ [ Suchen …               ]     │   │ Spaghetti mit Lauch-Carbonara        │
│ ( Anwesenheit & Fristen)│──▶│ Bunte Gemüse Quesadilla · 4 P. │──▶│ Für 4 Portionen                      │
│ ( Rezepte )             │   │ Spaghetti mit Lauch-… · 4 P.   │   │ Zutaten: 500 g Vollkornspaghetti, …  │
└─────────────────────────┘   │ [ + Neues Rezept ] (Eltern)    │   │ Zubereitung: 1. … 2. …               │
                              └────────────────────────────────┘   │ Quelle: rewe.de/…                    │
                                                                   │ [Bearbeiten] (Löschen)  (nur Eltern) │
┌ Rezept bearbeiten (Eltern) ───────┐                              └──────────────────────────────────────┘
│ Name             [            ]   │
│ Portionen        [ – 4 + ]        │   Gericht-Editor (Eltern/Kind, nicht im Verschieben-Modus):
│ Zutaten (eine pro Zeile)          │   ┌ Gericht einreichen ───────────────────────────────┐
│ [500 g Vollkornspaghetti ... ]    │   │ Tag: [Di][Mi][Do]                                  │
│ Zubereitung (ein Schritt/Zeile)   │   │ ( Aus Rezept wählen )  → Rezeptliste (Auswahl)     │
│ Quelle (Link, optional)           │   │ Aus Rezept „Spaghetti …“ · Portionen [ – 5 + ]     │
│ [ Speichern ]                     │   │ „Portionen ändern berechnet die Zutaten neu.“      │
└───────────────────────────────────┘   │ (Rezeptbezug entfernen)                            │
                                        │ Name: Spaghetti mit Lauch-Carbonara                │
Essensplan:                             │ Zutaten: 625 g Vollkornspaghetti / Salz / 2,5 …    │
│ Mo 19.10. Spaghetti … · Ben kocht     │ [ Einreichen ]                                     │
│   625 g Vollkornspaghetti, Salz, …    └────────────────────────────────────────────────────┘
│   Rezept ›   (online, alle)  ──▶ Rezeptansicht mit den Mengen des Gerichts („Für 5 Portionen“)
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Startseite | Essensplan, Anwesenheit | + „Rezepte“ | alle Mitglieder |
| Rezeptliste | – | Liste (Name, Portionen), Suchfeld, „Neues Rezept“ nur Eltern; im Auswahlmodus Tippen = übernehmen | REQ-MEAL-060 |
| Rezeptansicht | – | Portionen, Zutaten, Schritte, Quelle; Eltern: Bearbeiten, Löschen (mit Bestätigung) | REQ-MEAL-060/063 |
| Rezept-Editor | – | Name, Portionen 1–20, Zutaten-Zeilen (Pflicht), Schritte-Zeilen, Quelle | nur Eltern, REQ-MEAL-060 |
| Gericht-Editor | Name + Zutaten frei | + „Aus Rezept wählen“, Portionen-Stepper, „Rezeptbezug entfernen“; Zutaten werden zerlegt gesendet | REQ-MEAL-062; im Verschieben-Modus gesperrt |
| Essensplan-Zeile | Name, Koch, Zutaten | + „Rezept ›“, wenn `recipeId` gesetzt und online | REQ-MEAL-063 |
| Fehler | – | 409 „Ein Rezept mit diesem Namen gibt es schon“, 403 „Nur für Eltern.“, 404 Rezept gelöscht, offline „Server nicht erreichbar …“ | Servermeldungen über `ErrorText` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/03-design/meals.md` | Abschnitte „Rezepte & strukturierte Zutaten (Phase 5a)“, „App-Seite (Phase 3b/4b)“ | Grenzen (Name 60, Zutat 80, Einheit 20, Menge 0,001–10000, 3 Nachkommastellen, Portionen 1–20, Schritte ≤ 30 × 1000, roh ≤ 100, URL ≤ 500), Regeln Rezeptbezug |
| P0 | `docs/02-architecture/adr/0012-recipes-and-rewe-import.md` | all | Kopie statt Verknüpfung, Zerleger-Regeln, Rechte |
| P0 | `api/openapi.yaml` | 397–495, 1070–1215 | `listRecipes`/`getRecipe`/`createRecipe`/`updateRecipe`/`deleteRecipe`; `Dish` (+`structuredIngredients`,`recipeId`,`servings`), `SaveDishRequest`, `Ingredient`, `Recipe`, `RecipeSummary`, `SaveRecipeRequest` (**nicht ändern**) |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/plan/Ingredient.kt` | all | `displayText()` (Format, das die App spiegelt), Grenzen, Rundung |
| P0 | `backend/meals/src/main/kotlin/de/familyplanner/meals/recipe/{RecipeRules,RecipeService}.kt` | all | Servermeldungen (409 Name, 400 Portionen/Schritte/URL) |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/{domain,data}/plan/*.kt` | all | `Dish`, `DishDraft`, `SaveDishUseCase`, `LoadDishEditorUseCase`, Mapper |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/dish/*.kt` | all | Editor-State/Events/Modi, Sektionen |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/plan/WeekPlanScreen.kt` | 200–290 | `PlanDayRow`, `DishSummary` (Link einbauen) |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/{MealsNavigation,MealsModule}.kt` | all | Routen, Koin |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 60–72 | `MealsHomeActions`-Slot |
| P1 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/{fakes,data,ui,domain}/` | alle | Fakes, MockWebServer, Robolectric, `parentSession`/`childSession` |
| P2 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/profiles/{ProfilesScreen,ProfilesViewModel}.kt` | all | einfache Liste mit Laden/Fehler als Vorlage für die Rezeptliste |
| P2 | `android/core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/client/MealsApi.kt` | 126, 180, 211, 306, 402 | Generierte Rezept-Signaturen |
| P2 | `android/README.md` | 97–end | Form der Abnahme-Checkliste |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Navigationsergebnis | developer.android.com „Navigation – Returning a result“ | `navController.previousBackStackEntry?.savedStateHandle?.set(key, value)` vor `popBackStack()`; Empfänger liest `entry.savedStateHandle.getStateFlow(key, null)` und entfernt den Wert nach Verbrauch |
| REWE-Daten (geprüft 2026-10-18 im Browser) | `rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/`, `…/bunte-gemuese-quesadilla/` | Zutatenzeilen als echte Testfälle für den Zerleger (siehe Task 1); Import selbst ist 5c |

**Keine neue Bibliothek.**

---

## Patterns to Mirror

### NAMING_CONVENTION
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt:27-33
/** [date] als ISO-Datum (`yyyy-MM-dd`); [dishId] `null` = neues Gericht; [cookId] Vorauswahl (Stellvertretung/Kind). */
@Serializable
data class DishEditRoute(
    val date: String,
    val dishId: String? = null,
    val cookId: String? = null,
)
```
Pakete `domain/<bereich>`, `data/<bereich>`, `ui/<screen>`. Neu: `domain/recipe`, `data/recipe`, `ui/recipes` (Liste), `ui/recipe`
(Ansicht + Editor). Use Cases `VerbNounUseCase` mit `suspend operator fun invoke`, Bündel `RecipeUseCases`. Strings mit Präfix
`recipes_*` (Liste), `recipe_*` (Ansicht/Editor), `dish_recipe_*` (Gericht-Editor), `plan_recipe`, `home_recipes`.
Domänen-/API-Namenskonflikte (`Ingredient`, `Recipe`, `RecipeSummary`) über `import … as Api…`.

### ERROR_HANDLING
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/data/plan/RemoteMealPlanRepository.kt:22-40
override suspend fun week(weekStart: LocalDate): ApiResult<WeekPlan> =
    apiCall(json, { meals.getWeekPlan(weekStart.toString()) }) { it.data?.toDomain() }
override suspend fun deleteDish(dishId: String): ApiResult<Unit> = apiCallNoContent(json) { meals.deleteDish(dishId) }

// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/Validation.kt:9-26
internal fun validateDish(name: String, ingredientsText: String, ingredientsRequired: Boolean = false): ApiResult.Failure? {
    …
    return when {
        name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
        …
        else -> null
    }
}
```
Mapper liefern `null` bei unlesbaren Daten → `AppError.Server`. Client-Validierung vor dem Request mit `validationFailure("…")`,
Anzeige über `ErrorText(error)`; Servermeldung gewinnt bei `Validation`/`Conflict` (`core/ui/ErrorMessages.kt:25-35`).
**Kein neuer `AppError`-Typ.**

### LOGGING_PATTERN
Netzwerkfehler loggt `apiCall` zentral; im Feature wird nichts zusätzlich geloggt. In 5b **kein neues Logging**.

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
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/plan/UseCases.kt:55-89
class LoadDishEditorUseCase(private val repository: MealPlanRepository, private val attendance: AttendanceRepository) {
    suspend operator fun invoke(weekStart: LocalDate): ApiResult<DishEditorData> =
        coroutineScope {
            val week = async { repository.week(weekStart) }
            val members = async { repository.members() }
            val presence = async { attendance.week(weekStart) }
            …
            if (presenceResult !is ApiResult.Success) return@coroutineScope presenceResult as ApiResult.Failure
            …
            ApiResult.Success(DishEditorData(weekResult.data, membersResult.data.filter { it.isParent }, children))
        }
}
/** Bündelt die Editor-Use-Cases (hält die ViewModel-Parameterliste kurz). */
class DishUseCases(val load: LoadDishEditorUseCase, val save: SaveDishUseCase, val delete: DeleteDishUseCase, val review: ReviewDishUseCase)
```

### VIEWMODEL_PATTERN
```kotlin
// SOURCE: android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/dish/DishEditViewModel.kt:255-268 (isBusy-Sperre)
private fun save() {
    val current = _state.value
    val cookId = current.cookId
    if (!current.canSave || cookId == null) return
    _state.update { it.copy(isBusy = true, error = null) }
    viewModelScope.launch {
        val result = dishes.save(…)
        when (result) {
            is ApiResult.Success -> _state.update { it.copy(isBusy = false, isDone = true) }
            is ApiResult.Failure -> _state.update { it.copy(isBusy = false, error = result.error) }
        }
    }
}
// SOURCE: DishEditViewModel.kt:300-316 — 404 beim Löschen zählt als gelöscht (anderes Gerät war schneller).
```
`UiState` als `data class` mit abgeleiteten Properties (`canSave`), `sealed interface XxxEvent`, `onEvent(event)`, `isDone` →
`LaunchedEffect(state.isDone) { if (state.isDone) onDone() }`. Neuladen per `LifecycleResumeEffect` (`WeekPlanScreen.kt:48-52`).
Screens: `PullToRefreshBox` + `LazyColumn` für Listen, `Column(verticalScroll)` für Formulare, `ConfirmDialog`, `SubmitButton`,
`ErrorText` aus `core:ui`. Koin-Parameter immer als **ein** Args-Objekt (`DishEditArgs.kt`: „Koin unterscheidet Parameter nach Typ“).

### TEST_STRUCTURE
```kotlin
// SOURCE: android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/data/RemoteMealPlanRepositoryTest.kt:26-60
class RemoteMealPlanRepositoryTest {
    private val json = createJson()
    private lateinit var server: MockWebServer
    …
    private fun ok(data: String) = """{"success":true,"data":$data}"""
    private fun error(code: String, message: String) = """{"success":false,"error":{"code":"$code","message":"$message"}}"""
// SOURCE: fakes/Fakes.kt — parentSession = Session("tok", "p-1", "Anna", Role.PARENT, "d-1"); childSession = …("c-1", "Ben", Role.CHILD, "d-2")
// SOURCE: fakes/PlanFakes.kt:15-19 — thisWeek = 2026-10-12, parentAnna p-1, parentTom p-2, childBen c-1, someDish
```
ViewModel-Tests mit `MainDispatcherRule`, `Clock.fixed(Instant.parse("2026-10-17T10:00:00Z"), ZoneId.of("Europe/Berlin"))`.
Screens mit `RobolectricTestRunner`, `createComposeRule()`, `FamilyPlannerTheme { … }`; untere Elemente mit
`assertExists`/`performScrollTo`. Testnamen mit REQ-ID: `` `REQ-MEAL-062 picking a recipe scales ingredients to servings`() ``.

---

## Files to Change

`M` = `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals`,
`T` = `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals`.

| File | Action | Justification |
|---|---|---|
| `M/domain/recipe/Models.kt` | CREATE | `Ingredient`, `Recipe`, `RecipeSummary`, `RecipeDraft`, `RecipeRef` |
| `M/domain/recipe/RecipeLimits.kt` | CREATE | Grenzen wie Backend (Portionen, Schritte, Einheit, Menge, URL, Suche) |
| `M/domain/recipe/IngredientLines.kt` | CREATE | `parseIngredientLine`, `ingredientsFromText`, `Ingredient.displayText()`, `formatQuantity` — reine Funktionen |
| `M/domain/recipe/Servings.kt` | CREATE | `scaleIngredients`, `defaultServings` — reine Funktionen |
| `M/domain/recipe/Validation.kt` | CREATE | `validateRecipe`, `stepLines` |
| `M/domain/recipe/RecipeRepository.kt` | CREATE | `list`, `recipe`, `create`, `update`, `delete` |
| `M/domain/recipe/UseCases.kt` | CREATE | `LoadRecipesUseCase`, `LoadRecipeUseCase`, `SaveRecipeUseCase`, `DeleteRecipeUseCase`, `RecipeUseCases`, `filterRecipes` |
| `M/data/recipe/Mappers.kt` | CREATE | API ↔ Domäne |
| `M/data/recipe/RemoteRecipeRepository.kt` | CREATE | `MealsApi` |
| `M/domain/plan/Models.kt` | UPDATE | `Dish` + `recipeId`, `servings`; `DishDraft.ingredients: List<Ingredient>` + `recipe`; `DishInput`; `DishEditorData` + `presentByDate` |
| `M/domain/plan/UseCases.kt` | UPDATE | `LoadDishEditorUseCase` (Anwesende je Tag), `SaveDishUseCase(dishId, input, ingredientsRequired)`, `DishUseCases` + `recipe` |
| `M/data/plan/Mappers.kt` | UPDATE | `recipeId`/`servings` lesen; `DishDraft.toApi()` mit `structuredIngredients`, `ingredients = emptyList()` |
| `M/ui/recipes/RecipeListViewModel.kt`, `RecipeListScreen.kt` | CREATE | Liste, Suche, Auswahlmodus |
| `M/ui/recipe/RecipeArgs.kt` | CREATE | `RecipeViewArgs`, `RecipeEditArgs` (Koin-Parameter) |
| `M/ui/recipe/RecipeViewModel.kt`, `RecipeScreen.kt` | CREATE | Ansicht (auch mit Gericht-Kontext), Löschen |
| `M/ui/recipe/RecipeEditViewModel.kt`, `RecipeEditScreen.kt` | CREATE | Editor nur Eltern |
| `M/ui/dish/DishEditViewModel.kt` | UPDATE | `RecipePicked`, `ServingsChanged`, `ClearRecipe`; Speichern mit `DishInput` |
| `M/ui/dish/DishEditScreen.kt`, `DishEditSections.kt` | UPDATE | `RecipeSection`; Route mit Auswahl-Callback und Ergebnis |
| `M/ui/plan/WeekPlanScreen.kt` | UPDATE | „Rezept ›“ in `DishSummary`, Callback `onOpenRecipe` |
| `M/MealsNavigation.kt` | UPDATE | `RecipeListRoute`, `RecipeViewRoute`, `RecipeEditRoute`, Ergebnis-Schlüssel, `MealsHomeActions` + `onOpenRecipes` |
| `M/MealsModule.kt` | UPDATE | Abschnitt `// recipe` |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | `home_recipes`, `recipes_*`, `recipe_*`, `dish_recipe_*`, `plan_recipe` |
| `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | UPDATE | `onOpenRecipes` |
| `android/app/build.gradle.kts` | UPDATE | `versionCode = 5`, `versionName = "0.5.0"` |
| `T/fakes/RecipeFakes.kt` | CREATE | `FakeRecipeRepository`, Fixtures |
| `T/fakes/PlanFakes.kt` | UPDATE | `FakeMealPlanRepository` an `DishDraft` mit `Ingredient` anpassen |
| `T/domain/recipe/IngredientLinesTest.kt`, `ServingsTest.kt`, `ValidationTest.kt`, `UseCasesTest.kt` | CREATE | reine Funktionen, Use Cases |
| `T/data/RemoteRecipeRepositoryTest.kt` | CREATE | MockWebServer |
| `T/data/RemoteMealPlanRepositoryTest.kt` | UPDATE | `recipeId`/`servings`, gesendete `structuredIngredients` |
| `T/domain/plan/UseCasesTest.kt` | UPDATE | Anwesende je Tag, Speichern strukturiert + Rezeptbezug |
| `T/ui/RecipeViewModelsTest.kt`, `T/ui/RecipeScreensTest.kt` | CREATE | Liste, Ansicht, Editor, Gericht-Editor mit Rezept, Plan-Link (eigene Dateien; bestehende Testdateien sind groß) |
| `T/ui/PlanViewModelsTest.kt`, `PlanScreensTest.kt`, `CookingViewModelsTest.kt`, `CookingScreensTest.kt` | UPDATE | angepasste Signaturen (`DishDraft`, `onOpenRecipe`), keine neuen Fälle dort |
| `T/MealsModuleTest.kt` | UPDATE | neue Bindungen |
| `android/README.md` | UPDATE | Modulbeschreibung, Abnahme-Checkliste Phase 5b |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 5b)“ |
| `docs/04-verification/traceability.md` | UPDATE | REQ-MEAL-060, -062, -063 |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase-5-Zeile (5b umgesetzt, 5c offen) |

## NOT Building

- **REWE-Import** (REQ-MEAL-061): Teilen-Ziel im Manifest, WebView, schema.org-Leser, Eigenmarken-Präfixe, „Link einfügen“ → **Phase 5c**
  (eigener Plan). Der Zerleger aus Task 1 wird dort wiederverwendet; das Entfernen von Marken kommt dort dazu.
- Keine Änderung an `api/openapi.yaml` oder `backend/`. Fehlt etwas im Vertrag: stoppen und nachfragen.
- Keine Rezepte offline (PRD „Later“), kein Cache für Rezepte; `StoredDish` bleibt unverändert (`recipeId` wird nicht gespeichert).
- Keine Einheiten-Umrechnung, keine Einkaufsliste (Phase 6).
- Kein Live-Bezug: Rezeptänderungen ändern keine Gerichte (Server-Regel 5a); die App bietet kein „Gericht aus Rezept aktualisieren“.
- Keine Bilder, keine Kategorien/Favoriten, keine serverseitige Suche beim Tippen.
- Kein Anlegen/Ändern/Löschen von Rezepten durch Kinder (Server 403; App bietet es nicht an).
- Keine Eingabe von Zutaten als Einzelfelder (Anwender: Textzeilen).

---

## Step-by-Step Tasks

### Task 0: Branch & Ausgangslage
- **ACTION**: Branch `feat/rezepte-app` von aktuellem `main` (enthält 5a, Merge PR #14); Ausgangslage grün.
- **IMPLEMENT**: nichts.
- **MIRROR**: –
- **IMPORTS**: –
- **GOTCHA**: Der generierte Client muss 0.5.0 sein: `listRecipes(query)`, `getRecipe`, `createRecipe`, `updateRecipe`, `deleteRecipe`;
  `SaveDishRequest(date, cookId, name, ingredients, structuredIngredients = null, recipeId = null, servings = null)`;
  `Ingredient(name, quantity: Double? = null, unit: String? = null)`. Sonst `./gradlew :core:api:openApiGenerate`.
- **VALIDATE**: `cd android && ./gradlew :core:api:openApiGenerate :feature:meals:testDebugUnitTest` grün;
  `grep -n "fun listRecipes\|structuredIngredients" core/api/build/generated/openapi/src/main/kotlin/de/familyplanner/api/{client/MealsApi,model/SaveDishRequest}.kt` findet beide.

### Task 1: Domäne Rezept – Zutatenzeilen zerlegen und anzeigen (TDD)
- **ACTION**: `T/domain/recipe/IngredientLinesTest.kt` (RED) → `M/domain/recipe/Models.kt` (nur `Ingredient`), `RecipeLimits.kt`, `IngredientLines.kt`.
- **IMPLEMENT**:
  ```kotlin
  // domain/recipe/Models.kt
  /** Zutat (ADR-0012): Name Pflicht, Menge und Einheit optional. Eine Freitextzeile ist eine Zutat nur mit Name. */
  data class Ingredient(val name: String, val quantity: Double? = null, val unit: String? = null)

  // domain/recipe/RecipeLimits.kt — Grenzen wie im Backend (Ingredient.kt, RecipeRules.kt)
  object RecipeLimits {
      const val UNIT_MAX = 20
      const val QUANTITY_MIN = 0.001
      const val QUANTITY_MAX = 10_000.0
      const val QUANTITY_SCALE = 3
      const val SERVINGS_MIN = 1
      const val SERVINGS_MAX = 20
      const val STEP_MAX = 1000
      const val STEPS_MAX_COUNT = 30
      const val STEP_LINES_RAW_MAX = 100
      const val SOURCE_URL_MAX = 500
      const val QUERY_MAX = 60
  }

  // domain/recipe/IngredientLines.kt
  /** Häufige Einheiten (Vergleich ohne Groß-/Kleinschreibung); dazu jedes Wort mit Pluralendung in Klammern, z. B. „Stange(n)“. */
  private val KNOWN_UNITS = setOf(
      "g", "kg", "mg", "ml", "cl", "dl", "l", "liter", "el", "tl", "msp.", "prise", "prisen", "bund", "becher",
      "dose", "dosen", "glas", "gläser", "packung", "packungen", "pck.", "päckchen", "stück", "stk.", "scheibe", "scheiben",
      "zehe", "zehen", "stange", "stangen", "zweig", "zweige", "blatt", "blätter", "handvoll", "tasse", "tassen",
      "flasche", "flaschen", "würfel", "kopf", "köpfe", "knolle", "knollen", "beutel", "tube", "tuben", "schale", "schalen",
  )
  private val PLURAL_UNIT = Regex("""^\p{L}+\.?\(\p{L}{1,3}\)$""")
  // Zahl am Anfang, danach Leerzeichen oder direkt ein Buchstabe („500g Mehl“); Rest ist Einheit+Name oder Name.
  private val LEADING_NUMBER = Regex("""^(\d+(?:[.,]\d+)*)(?:\s+|(?=\p{L}))(.+)$""")
  // „1.000“, „12.500“: Punkt als Tausendertrenner (erste Gruppe ohne führende 0); „0.125“, „500.0“, „2.5“ bleiben Dezimalzahlen.
  private val THOUSANDS = Regex("""^[1-9]\d{0,2}(\.\d{3})+$""")

  /** Zerlegt eine Zeile in Menge/Einheit/Name; alles, was nicht sicher passt, wird ganz zum Namen (ADR-0012). */
  fun parseIngredientLine(line: String): Ingredient {
      val text = line.trim()
      val match = LEADING_NUMBER.matchEntire(text) ?: return Ingredient(text)
      val quantity = parseQuantity(match.groupValues[1])?.takeIf { it in RecipeLimits.QUANTITY_MIN..RecipeLimits.QUANTITY_MAX }
          ?: return Ingredient(text)
      val rest = match.groupValues[2].trim()
      val first = rest.substringBefore(' ')
      val afterFirst = rest.substringAfter(' ', missingDelimiterValue = "").trim()
      return if (isUnit(first) && afterFirst.isNotEmpty()) {
          Ingredient(afterFirst, quantity, first)
      } else {
          Ingredient(rest, quantity)
      }
  }
  private fun isUnit(token: String) =
      token.length <= RecipeLimits.UNIT_MAX && (token.lowercase() in KNOWN_UNITS || PLURAL_UNIT.matches(token))
  private fun parseQuantity(raw: String): Double? {
      val normalized = if (THOUSANDS.matches(raw)) raw.replace(".", "") else raw.replace(',', '.')
      return normalized.toDoubleOrNull()?.let(::roundedQuantity)   // „2.5.1“ → toDoubleOrNull = null → ganze Zeile Name
  }
  internal fun roundedQuantity(value: Double): Double =
      BigDecimal.valueOf(value).setScale(RecipeLimits.QUANTITY_SCALE, RoundingMode.HALF_UP).toDouble()

  /** Zeilen des Zutatenfelds (getrimmt, ohne Leerzeilen, vgl. `ingredientLines`) als zerlegte Zutaten. */
  fun ingredientsFromText(text: String): List<Ingredient> = ingredientLines(text).map(::parseIngredientLine)

  /** Wie Backend `displayText()`: „500 g Vollkornspaghetti“, „2,5 Stange(n) Lauch“, „Salz“. */
  fun Ingredient.displayText(): String = listOfNotNull(quantity?.let(::formatQuantity), unit, name).joinToString(" ")
  fun formatQuantity(value: Double): String =
      BigDecimal.valueOf(value).setScale(RecipeLimits.QUANTITY_SCALE, RoundingMode.HALF_UP)
          .stripTrailingZeros().toPlainString().replace('.', ',')
  /** Zutaten als Text fürs Eingabefeld, eine pro Zeile. */
  fun ingredientsToText(ingredients: List<Ingredient>): String = ingredients.joinToString("\n") { it.displayText() }
  ```
- **MIRROR**: `domain/attendance/PlanWeeks.kt` (reine, getestete Funktionen); Backend `plan/Ingredient.kt` (Format/Rundung).
- **IMPORTS**: `java.math.BigDecimal`, `java.math.RoundingMode`, `de.familyplanner.android.feature.meals.domain.plan.ingredientLines`.
- **GOTCHA**: `ingredientLines` liegt in `domain/plan/Validation.kt` (öffentlich) — wiederverwenden, nicht kopieren. Rundtrip muss stabil
  sein: `parseIngredientLine(x.displayText()) == x` für zerlegte Zutaten (Dezimalkomma!). `"500.0"` → `500.0`, Anzeige „500“.
  `"0"`/`"0,0001"` (außerhalb 0,001–10000) → ganze Zeile als Name, kein Fehler. Einheit ohne Name dahinter („2 Würfel“) → Name „Würfel“.
  Die Markenbereinigung („REWE Bio …“) gehört **nicht** hierher (Phase 5c). detekt `MagicNumber`: Konstanten aus `RecipeLimits`.
- **VALIDATE** (Testnamen; echte REWE-Zeilen, geprüft 2026-10-18):
  `REQ-MEAL-062 quantity unit and name are split` („500.0 g Vollkornspaghetti“ → 500.0, „g“, „Vollkornspaghetti“),
  `REQ-MEAL-062 plural unit in brackets is a unit` („2.0 Stange(n) Lauch“, „2.0 Zehe(n) Knoblauch“, „1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen“ → Name „REWE Beste Wahl Kidney-Bohnen“),
  `REQ-MEAL-062 quantity without unit keeps the rest as name` („2.0 Eier“ → 2.0, null, „Eier“; „4.0 Frühlingszwiebeln“; „4.0 REWE Beste Wahl Tortilla-Wraps“),
  `REQ-MEAL-062 known unit is matched ignoring case` („2.0 EL Rapsöl“, „1 el Öl“ → Einheit wie eingegeben),
  `REQ-MEAL-062 line without leading number is a name` („Salz“, „Saft von 1 Limette“, „Kreuzkümmel (gemahlen)“),
  `REQ-MEAL-062 optional prefix stays in the name` („70.0 g Optional: Bio-Bacon“ → 70, „g“, „Optional: Bio-Bacon“),
  `REQ-MEAL-062 decimal comma is accepted` („2,5 Stange(n) Lauch“ → 2.5), `REQ-MEAL-062 glued unit is accepted` („500g Mehl“),
  `REQ-MEAL-062 dot thousands separator` („1.000 g Mehl“ → 1000; „0.125 l Milch“ → 0.125),
  `REQ-MEAL-062 out of range quantity keeps the whole line` („0 g Salz“, „20000 g Mehl“), `REQ-MEAL-062 two word unit only fallback`
  („2 große Zwiebeln“ → 2, null, „große Zwiebeln“), `REQ-MEAL-062 unit without name is a name` („2 Würfel“ → 2, null, „Würfel“),
  `REQ-MEAL-062 display text matches backend format` (500.0 → „500 g Vollkornspaghetti“, 2.5 → „2,5 Stange(n) Lauch“, „Salz“, 0.333),
  `REQ-MEAL-062 display text round trips through the parser`, `REQ-MEAL-062 text lines are trimmed and blank lines dropped`.

### Task 2: Domäne Rezept – Portionen und Validierung (TDD)
- **ACTION**: `T/domain/recipe/ServingsTest.kt`, `T/domain/recipe/ValidationTest.kt` (RED) → `Servings.kt`, `Validation.kt`, Rest von `Models.kt`.
- **IMPLEMENT**:
  ```kotlin
  // domain/recipe/Models.kt (zusätzlich)
  data class RecipeSummary(val id: String, val name: String, val servings: Int)
  data class Recipe(
      val id: String, val name: String, val servings: Int, val ingredients: List<Ingredient>,
      val steps: List<String>, val sourceUrl: String?, val createdBy: String,
  )
  /** Eingabe für Anlegen/Ändern (bereits bereinigt). */
  data class RecipeDraft(val name: String, val servings: Int, val ingredients: List<Ingredient>, val steps: List<String>, val sourceUrl: String?)
  /** Am Gericht: „übernommen aus Rezept X für N Portionen“ (ADR-0012). */
  data class RecipeRef(val recipeId: String, val servings: Int)

  // domain/recipe/Servings.kt
  /** Rechnet Mengen von [base] auf [target] Portionen um (3 Nachkommastellen); Zutaten ohne Menge bleiben ohne Menge (REQ-MEAL-062). */
  fun scaleIngredients(ingredients: List<Ingredient>, base: Int, target: Int): List<Ingredient> =
      ingredients.map { ingredient ->
          ingredient.quantity?.let { ingredient.copy(quantity = roundedQuantity(it * target / base)) } ?: ingredient
      }
  /** Vorschlag: an dem Tag anwesende Mitglieder, begrenzt auf 1–20 (REQ-MEAL-062). */
  fun defaultServings(presentMembers: Int): Int = presentMembers.coerceIn(RecipeLimits.SERVINGS_MIN, RecipeLimits.SERVINGS_MAX)

  // domain/recipe/Validation.kt
  /** Schritte: eine Zeile je Schritt, getrimmt, ohne Leerzeilen (wie der Server). */
  fun stepLines(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
  internal fun validateRecipe(name: String, servings: Int, ingredientsText: String, stepsText: String, sourceUrl: String): ApiResult.Failure? {
      val ingredients = ingredientLines(ingredientsText)
      val steps = stepLines(stepsText)
      val url = sourceUrl.trim()
      return when {
          name.isBlank() -> validationFailure("Bitte einen Namen eingeben.")
          name.trim().length > PlanLimits.NAME_MAX -> validationFailure("Der Name darf höchstens ${PlanLimits.NAME_MAX} Zeichen haben.")
          servings !in RecipeLimits.SERVINGS_MIN..RecipeLimits.SERVINGS_MAX ->
              validationFailure("Portionen müssen zwischen ${RecipeLimits.SERVINGS_MIN} und ${RecipeLimits.SERVINGS_MAX} liegen.")
          ingredients.isEmpty() -> validationFailure("Bitte mindestens eine Zutat angeben.")
          // Zutaten-Länge/Anzahl wie beim Gericht (gleiche Meldungen):
          else -> validateDish(name, ingredientsText) ?: validateSteps(stepsText, steps) ?: validateSourceUrl(url)
      }
  }
  // validateSteps: Rohzeilen > STEP_LINES_RAW_MAX oder steps.size > STEPS_MAX_COUNT → "Höchstens 30 Schritte."; ein Schritt > STEP_MAX → "Ein Schritt darf höchstens 1000 Zeichen haben."
  // validateSourceUrl: leer → null; > SOURCE_URL_MAX oder nicht http(s)://<host> (java.net.URI, scheme http/https, host nicht leer) → "Bitte einen gültigen Link (http/https) angeben."
  ```
- **MIRROR**: ERROR_HANDLING (`validationFailure`), Backend `RecipeRules.kt`.
- **IMPORTS**: `de.familyplanner.android.core.data.{ApiResult, validationFailure}`, `de.familyplanner.android.feature.meals.domain.plan.{PlanLimits, ingredientLines, validateDish}`, `java.net.URI`, `java.net.URISyntaxException`.
- **GOTCHA**: `validateDish` ist `internal` im selben Modul — nutzbar; er prüft auch den Namen (bereits geprüft, harmlos). Reihenfolge der
  Meldungen wie Backend `RecipeService.validated` (Name → Portionen → Zutaten → Schritte → URL). `URI(…)` wirft bei Leerzeichen →
  `URISyntaxException` fangen (`@Suppress("SwallowedException")` wie `Parsing.kt`). `scaleIngredients` mit `base` 0 kommt nicht vor
  (Rezept-Portionen ≥ 1 vom Server); trotzdem `require(base > 0)` vermeiden — Mapper liefert `null` bei Portionen < 1 (Task 3).
- **VALIDATE**: `REQ-MEAL-062 four servings scaled to five` (500 → 625; 2 Stange(n) → 2,5), `REQ-MEAL-062 ingredient without quantity stays without`,
  `REQ-MEAL-062 scaled quantity is rounded to three decimals` (1 → 1/3 → 0.333), `REQ-MEAL-062 default servings is present members clamped`
  (0 → 1, 5 → 5, 25 → 20); `REQ-MEAL-060 recipe without name is rejected`, `REQ-MEAL-060 recipe without ingredients is rejected`,
  `REQ-MEAL-060 servings below one are rejected`, `REQ-MEAL-060 servings above twenty are rejected`, `REQ-MEAL-060 more than thirty steps are rejected`,
  `REQ-MEAL-060 step longer than 1000 characters is rejected`, `REQ-MEAL-060 invalid source url is rejected` („ftp://x“, „rewe.de/rezepte“, „https://“),
  `REQ-MEAL-060 empty source url is allowed`, `REQ-MEAL-060 valid recipe passes`.

### Task 3: Daten Rezept – Repository gegen MockWebServer (TDD)
- **ACTION**: `T/data/RemoteRecipeRepositoryTest.kt` (RED) → `M/domain/recipe/RecipeRepository.kt`, `M/data/recipe/Mappers.kt`, `M/data/recipe/RemoteRecipeRepository.kt`.
- **IMPLEMENT**:
  ```kotlin
  // domain/recipe/RecipeRepository.kt
  interface RecipeRepository {
      suspend fun list(): ApiResult<List<RecipeSummary>>
      suspend fun recipe(recipeId: String): ApiResult<Recipe>
      suspend fun create(draft: RecipeDraft): ApiResult<Recipe>
      suspend fun update(recipeId: String, draft: RecipeDraft): ApiResult<Recipe>
      suspend fun delete(recipeId: String): ApiResult<Unit>
  }
  // data/recipe/RemoteRecipeRepository.kt
  class RemoteRecipeRepository(private val meals: MealsApi, private val json: Json) : RecipeRepository {
      override suspend fun list() = apiCall(json, { meals.listRecipes() }) { r -> r.data?.mapAllOrNull { it.toDomain() } }
      override suspend fun recipe(recipeId: String) = apiCall(json, { meals.getRecipe(recipeId) }) { it.data?.toDomain() }
      override suspend fun create(draft: RecipeDraft) = apiCall(json, { meals.createRecipe(draft.toApi()) }) { it.data?.toDomain() }
      override suspend fun update(recipeId: String, draft: RecipeDraft) =
          apiCall(json, { meals.updateRecipe(recipeId, draft.toApi()) }) { it.data?.toDomain() }
      override suspend fun delete(recipeId: String) = apiCallNoContent(json) { meals.deleteRecipe(recipeId) }
  }
  // data/recipe/Mappers.kt
  internal fun ApiIngredient.toDomain() = Ingredient(name, quantity, unit)
  internal fun Ingredient.toApi() = ApiIngredient(name, quantity, unit)
  internal fun ApiRecipeSummary.toDomain(): RecipeSummary? = RecipeSummary(id, name, servings).takeIf { servings >= RecipeLimits.SERVINGS_MIN }
  internal fun ApiRecipe.toDomain(): Recipe? =
      Recipe(id, name, servings, ingredients.map { it.toDomain() }, steps, sourceUrl, createdBy).takeIf { servings >= RecipeLimits.SERVINGS_MIN }
  internal fun RecipeDraft.toApi() = SaveRecipeRequest(name, servings, ingredients.map { it.toApi() }, steps, sourceUrl)
  ```
- **MIRROR**: REPOSITORY_PATTERN, ERROR_HANDLING, TEST_STRUCTURE (`RemoteMealPlanRepositoryTest`: `enqueue`, `ok`, `error`, `server.takeRequest()`,
  Request-Body mit `json.decodeFromString<SaveRecipeRequest>(request.body…)` prüfen wie der Dish-Test).
- **IMPORTS**: `de.familyplanner.api.client.MealsApi`, `de.familyplanner.api.model.SaveRecipeRequest`,
  `de.familyplanner.api.model.{Ingredient as ApiIngredient, Recipe as ApiRecipe, RecipeSummary as ApiRecipeSummary}`,
  `de.familyplanner.android.core.data.{ApiResult, apiCall, apiCallNoContent}`, `de.familyplanner.android.feature.meals.data.mapAllOrNull`.
- **GOTCHA**: `listRecipes()` ohne `query` (clientseitige Suche) → Pfad genau `/api/v1/meals/recipes` ohne Query-String. `delete` ist 204.
  `explicitNulls = false` in `createJson()`: `sourceUrl = null` fehlt im Body (Server behandelt fehlend = null). Prüfe im Test, welcher
  Pfad für `getRecipe` aufgerufen wird. Kein Mapping-Fehler bei leeren Schritten (0 Schritte sind erlaubt).
- **VALIDATE**: `REQ-MEAL-060 list maps summaries without query`, `REQ-MEAL-060 recipe maps ingredients steps and source`,
  `REQ-MEAL-060 create posts structured ingredients and steps`, `REQ-MEAL-060 duplicate name maps conflict with server message`
  („Ein Rezept mit diesem Namen gibt es schon“), `REQ-MEAL-060 child create receives forbidden`, `REQ-MEAL-060 update puts to recipe path`,
  `REQ-MEAL-060 delete accepts 204`, `REQ-MEAL-060 unknown recipe maps not found`, `REQ-MEAL-060 recipe with zero servings is a server error`.

### Task 4: Domäne Rezept – Use Cases (TDD mit Fakes)
- **ACTION**: `T/fakes/RecipeFakes.kt`, `T/domain/recipe/UseCasesTest.kt` (RED) → `M/domain/recipe/UseCases.kt`.
- **IMPLEMENT**:
  ```kotlin
  class LoadRecipesUseCase(private val repository: RecipeRepository) {
      suspend operator fun invoke(): ApiResult<List<RecipeSummary>> = repository.list()
  }
  /** Name enthält [query] ohne Groß-/Kleinschreibung; leer → alle (wie Server-Suche, REQ-MEAL-060). Reihenfolge bleibt (Server sortiert nach Name). */
  fun filterRecipes(recipes: List<RecipeSummary>, query: String): List<RecipeSummary> {
      val needle = query.trim()
      return if (needle.isEmpty()) recipes else recipes.filter { it.name.contains(needle, ignoreCase = true) }
  }
  class LoadRecipeUseCase(private val repository: RecipeRepository) {
      suspend operator fun invoke(recipeId: String): ApiResult<Recipe> = repository.recipe(recipeId)
  }
  class SaveRecipeUseCase(private val repository: RecipeRepository) {
      suspend operator fun invoke(recipeId: String?, input: RecipeInput): ApiResult<Recipe> {
          validateRecipe(input.name, input.servings, input.ingredientsText, input.stepsText, input.sourceUrl)?.let { return it }
          val draft = RecipeDraft(input.name.trim(), input.servings, ingredientsFromText(input.ingredientsText),
              stepLines(input.stepsText), input.sourceUrl.trim().ifEmpty { null })
          return if (recipeId == null) repository.create(draft) else repository.update(recipeId, draft)
      }
  }
  /** Formularwerte des Rezept-Editors (roh, ungeprüft). */
  data class RecipeInput(val name: String, val servings: Int, val ingredientsText: String, val stepsText: String, val sourceUrl: String)
  class DeleteRecipeUseCase(private val repository: RecipeRepository) {
      suspend operator fun invoke(recipeId: String): ApiResult<Unit> = repository.delete(recipeId)
  }
  /** Bündelt die Rezept-Use-Cases (hält die ViewModel-Parameterlisten kurz). */
  class RecipeUseCases(val list: LoadRecipesUseCase, val load: LoadRecipeUseCase, val save: SaveRecipeUseCase, val delete: DeleteRecipeUseCase)
  ```
  `RecipeInput` gehört nach `Models.kt`. Fakes (`RecipeFakes.kt`): `FakeRecipeRepository` mit `listResult`, `recipeResult: (String) -> ApiResult<Recipe>`,
  `createResult`, `updateResult`, `deleteResult`, Mitschnitte `created`, `updated: List<Pair<String, RecipeDraft>>`, `deleted`, `requested`,
  `createGate` (Doppelklick). Fixtures: `carbonara = Recipe("r-1", "Spaghetti mit Lauch-Carbonara", 4, listOf(Ingredient("Vollkornspaghetti", 500.0, "g"),
  Ingredient("Salz"), Ingredient("Lauch", 2.0, "Stange(n)")), listOf("Nudeln kochen.", "Lauch schneiden."), "https://www.rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/", "p-1")`,
  `quesadilla = RecipeSummary("r-2", "Bunte Gemüse Quesadilla", 4)`, `carbonaraSummary`.
- **MIRROR**: SERVICE_PATTERN, `SaveDishUseCase` (validieren → bereinigen → create/update).
- **IMPORTS**: `de.familyplanner.android.core.data.ApiResult`.
- **GOTCHA**: Schritte in Fixtures **selbst formulieren** (keine REWE-Texte kopieren, Urheberrecht); Zutatenzeilen sind Fakten und dürfen
  als Testdaten dienen. Validierung vor jedem Request — kein Request bei ungültiger Eingabe.
- **VALIDATE**: `REQ-MEAL-060 search filters by name ignoring case`, `REQ-MEAL-060 blank search shows all`, `REQ-MEAL-060 save parses ingredient lines`
  (Text „500 g Vollkornspaghetti\nSalz“ → zwei Zutaten), `REQ-MEAL-060 save trims steps and drops blank lines`, `REQ-MEAL-060 empty source becomes null`,
  `REQ-MEAL-060 invalid recipe sends no request`, `REQ-MEAL-060 existing recipe is updated`.

### Task 5: Plan-Domäne & -Daten – Rezeptbezug und strukturierte Zutaten am Gericht (TDD)
- **ACTION**: `T/data/RemoteMealPlanRepositoryTest.kt`, `T/domain/plan/UseCasesTest.kt` erweitern (RED) → `M/domain/plan/Models.kt`, `UseCases.kt`, `M/data/plan/Mappers.kt`; Fakes anpassen.
- **IMPLEMENT**:
  ```kotlin
  // domain/plan/Models.kt
  data class Dish(
      …bestehende Felder…,
      /** Rezept, aus dem das Gericht übernommen wurde (ADR-0012); `null` nach Löschen des Rezepts. */
      val recipeId: String? = null,
      val servings: Int? = null,
  )
  data class DishDraft(val date: LocalDate, val cookId: String, val name: String, val ingredients: List<Ingredient>, val recipe: RecipeRef? = null)
  /** Formularwerte des Gericht-Editors (roh). */
  data class DishInput(val date: LocalDate, val cookId: String, val name: String, val ingredientsText: String, val recipe: RecipeRef? = null)
  data class DishEditorData(
      val week: WeekPlan, val parents: List<PlanMember>, val children: List<CookCandidate>,
      /** Anwesende Mitglieder je Tag der Woche: alle Eltern + anwesende Kinder (Portionen-Vorschlag, REQ-MEAL-062). */
      val presentByDate: Map<LocalDate, Int> = emptyMap(),
  )
  // domain/plan/UseCases.kt
  // LoadDishEditorUseCase: zusätzlich
  val parents = membersResult.data.filter { it.isParent }
  val presentByDate = weekResult.data.days.associate { day ->
      day.date to parents.size + presenceResult.data.children.count { child -> child.days.any { it.date == day.date && it.present } }
  }
  ApiResult.Success(DishEditorData(weekResult.data, parents, children, presentByDate))

  class SaveDishUseCase(private val repository: MealPlanRepository) {
      suspend operator fun invoke(dishId: String?, input: DishInput, ingredientsRequired: Boolean = false): ApiResult<Dish> {
          validateDish(input.name, input.ingredientsText, ingredientsRequired)?.let { return it }
          val draft = DishDraft(input.date, input.cookId, input.name.trim(), ingredientsFromText(input.ingredientsText), input.recipe)
          return if (dishId == null) repository.createDish(draft) else repository.updateDish(dishId, draft)
      }
  }
  class DishUseCases(val load: …, val save: …, val delete: …, val review: ReviewDishUseCase, val recipe: LoadRecipeUseCase)

  // data/plan/Mappers.kt
  internal fun ApiDish.toDomain(): Dish? { … Dish(id, day, cookId, name, ingredients, mappedStatus, rejectionReason, late, dayValid, recipeId, servings) }
  /** Ab 5b immer strukturiert; `ingredients` muss dann leer sein (Server: sonst 400). */
  internal fun DishDraft.toApi() =
      SaveDishRequest(
          date = date.toString(), cookId = cookId, name = name, ingredients = emptyList(),
          structuredIngredients = ingredients.map { it.toApi() }, recipeId = recipe?.recipeId, servings = recipe?.servings,
      )
  ```
- **MIRROR**: SERVICE_PATTERN; bestehender Mapper.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.recipe.{Ingredient, RecipeRef, ingredientsFromText, LoadRecipeUseCase}`,
  `de.familyplanner.android.feature.meals.data.recipe.toApi`.
- **GOTCHA**: `Dish.ingredients` bleibt `List<String>` (Anzeigetexte vom Server, auch im Cache) — die App braucht die Struktur nur beim
  Speichern; der Editor füllt das Textfeld aus diesen Zeilen und zerlegt neu (Rundtrip aus Task 1). `StoredDish` **nicht** ändern
  (`recipeId` offline nicht nötig, Rezepte sind nicht offline). `DishDraft.ingredients` wechselt den Typ → `FakeMealPlanRepository`
  (`Dish(..., draft.ingredients.map { it.displayText() })`) und Assertions in `PlanViewModelsTest`/`CookingViewModelsTest`/`UseCasesTest`
  (`listOf("Reis")` → `listOf(Ingredient("Reis"))`) anpassen; `SaveDishUseCase`-Aufrufe auf `DishInput` umstellen.
  Ein Elterngericht ohne Zutaten sendet `structuredIngredients = []` (gültig). `DishUseCases` bekommt einen 5. Parameter → Koin (Task 9).
- **VALIDATE**: `REQ-MEAL-062 dish maps recipe reference`, `REQ-MEAL-062 dish without recipe maps nulls`,
  `REQ-MEAL-062 create sends structured ingredients and empty lines` (Body: `"ingredients":[]`, `structuredIngredients[0] = {name:"Vollkornspaghetti",quantity:625.0,unit:"g"}`, `recipeId`, `servings`),
  `REQ-MEAL-062 dish without recipe sends no recipe fields`; Use Cases: `REQ-MEAL-062 editor counts parents and present children per day`
  (Ben Di–Do anwesend, 2 Eltern → Mo 2, Di 3), `REQ-MEAL-062 save sends parsed ingredients and recipe ref`, `REQ-MEAL-020 child dish still requires ingredients`.

### Task 6: UI – Rezeptliste mit Suche und Auswahlmodus (TDD)
- **ACTION**: `T/ui/RecipeViewModelsTest.kt`, `T/ui/RecipeScreensTest.kt` (RED) → `M/ui/recipes/RecipeListViewModel.kt`, `RecipeListScreen.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  data class RecipeListUiState(
      val recipes: List<RecipeSummary> = emptyList(),
      val query: String = "",
      val isLoading: Boolean = false,
      val error: AppError? = null,
      val isParent: Boolean = false,
      /** Auswahl für den Gericht-Editor: Tippen übernimmt, kein Anlegen. */
      val pick: Boolean = false,
  ) {
      val visible: List<RecipeSummary> get() = filterRecipes(recipes, query)
      val canCreate: Boolean get() = isParent && !pick
  }
  sealed interface RecipeListEvent { data object Refresh; data class QueryChanged(val value: String) }
  class RecipeListViewModel(args: RecipeListArgs, sessions: SessionStore, recipes: RecipeUseCases) : ViewModel()
  data class RecipeListArgs(val pick: Boolean = false)   // in ui/recipe/RecipeArgs.kt
  ```
  - `init`: `isParent` aus `sessions.current?.role == Role.PARENT`, `load()`. `Refresh` lädt neu (Job abbrechen wie `WeekPlanViewModel.load`).
  - `QueryChanged`: `value.take(RecipeLimits.QUERY_MAX)`; kein Request.
  - Screen `RecipeListScreen(state, onEvent, onOpen: (String) -> Unit, onCreate: () -> Unit, onBack)`: Titel `recipes_title` „Rezepte“ bzw.
    `recipes_pick_title` „Rezept wählen“; `OutlinedTextField` Suche (`recipes_search` „Suchen“, einzeilig); `PullToRefreshBox` + `LazyColumn`,
    Zeile „Name“ + „%1$d Portionen“ (`recipes_servings`), ganze Zeile `clickable` → `onOpen(id)`; leer: `recipes_empty` „Noch keine Rezepte.“
    bzw. bei Suche `recipes_no_match` „Kein Rezept passt zu „%1$s“.“; `ErrorText(state.error)`; `canCreate` → `Button` „Neues Rezept“
    (`recipes_new`); `TextButton` Zurück (`recipes_back`). Neuladen per `LifecycleResumeEffect` (nach Anlegen/Ändern/Löschen).
- **MIRROR**: `ui/profiles/ProfilesScreen.kt`/`ProfilesViewModel.kt` (Liste, Laden, Fehler), VIEWMODEL_PATTERN.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.recipe.{RecipeSummary, RecipeUseCases, RecipeLimits, filterRecipes}`,
  `de.familyplanner.android.core.data.session.{Role, SessionStore}`, `androidx.compose.material3.pulltorefresh.PullToRefreshBox`.
- **GOTCHA**: Im Auswahlmodus darf ein **Kind** wählen (REQ-MEAL-062), sieht aber kein „Neues Rezept“. Android-Strings mit Anführungszeichen
  („„%1$s““) in `strings.xml` escapen bzw. typografische Zeichen verwenden. Doppeltes Laden bei `init` + `LifecycleResumeEffect` wie im Plan akzeptiert.
- **VALIDATE**: ViewModel: `REQ-MEAL-060 list loads recipes`, `REQ-MEAL-060 search filters loaded list without request`,
  `REQ-MEAL-060 child cannot create`, `REQ-MEAL-060 parent can create`, `REQ-MEAL-062 pick mode hides create`, `REQ-MEAL-060 load error is shown`.
  Screens: `REQ-MEAL-060 list shows name and servings`, `REQ-MEAL-060 search shows only matching recipes`, `REQ-MEAL-060 empty search result names the query`,
  `REQ-MEAL-060 new recipe button only for parents`, `REQ-MEAL-062 pick mode shows Rezept wählen and returns tapped id`.

### Task 7: UI – Rezeptansicht und Rezept-Editor (TDD)
- **ACTION**: Tests in `RecipeViewModelsTest`/`RecipeScreensTest` (RED) → `M/ui/recipe/RecipeArgs.kt`, `RecipeViewModel.kt`, `RecipeScreen.kt`,
  `RecipeEditViewModel.kt`, `RecipeEditScreen.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  // ui/recipe/RecipeArgs.kt — je ein Koin-Parameter (Koin unterscheidet Parameter nach Typ)
  data class RecipeListArgs(val pick: Boolean = false)
  /** [dishDate]/[dishId]: Ansicht zu einem Gericht — Zutaten mit dessen Mengen (REQ-MEAL-063). */
  data class RecipeViewArgs(val recipeId: String, val dishDate: LocalDate? = null, val dishId: String? = null)
  /** [recipeId] `null` = neues Rezept. */
  data class RecipeEditArgs(val recipeId: String? = null)

  // RecipeViewModel
  data class RecipeUiState(
      val recipe: Recipe? = null,
      /** Zutaten des Gerichts (Anzeigetexte) und seine Portionen, wenn aus dem Plan geöffnet. */
      val dishIngredients: List<String>? = null,
      val dishServings: Int? = null,
      val isLoading: Boolean = true, val error: AppError? = null, val isParent: Boolean = false,
      val showDeleteConfirm: Boolean = false, val isBusy: Boolean = false, val actionError: AppError? = null,
      /** Gelöscht → zurück zur Liste. */
      val isDeleted: Boolean = false,
  ) {
      val ingredientLines: List<String> get() = dishIngredients ?: recipe?.ingredients?.map { it.displayText() }.orEmpty()
      val servings: Int? get() = dishServings ?: recipe?.servings
      val canManage: Boolean get() = isParent && dishIngredients == null
  }
  class RecipeViewModel(args: RecipeViewArgs, sessions: SessionStore, recipes: RecipeUseCases, plan: MealPlanRepository) : ViewModel()
  // Events: Refresh, RequestDelete, CancelDelete, ConfirmDelete

  // RecipeEditViewModel
  data class RecipeEditUiState(
      val recipeId: String?, val name: String = "", val servings: Int = DEFAULT_SERVINGS /* 4 */,
      val ingredientsText: String = "", val stepsText: String = "", val sourceUrl: String = "",
      val isLoading: Boolean = false, val loadError: AppError? = null, val isBusy: Boolean = false, val error: AppError? = null,
      val isDone: Boolean = false,
  ) {
      val isNew: Boolean get() = recipeId == null
      val canSave: Boolean get() = !isLoading && loadError == null && !isBusy && name.isNotBlank() && ingredientsText.isNotBlank()
  }
  // Events: NameChanged, ServingsChanged(Int), IngredientsChanged, StepsChanged, SourceUrlChanged, Save, Retry
  class RecipeEditViewModel(args: RecipeEditArgs, sessions: SessionStore, recipes: RecipeUseCases) : ViewModel()
  ```
  - **Ansicht laden**: `recipes.load(recipeId)`; mit `dishDate`+`dishId` **parallel** `plan.week(mondayOf(dishDate))` und das Gericht suchen
    → `dishIngredients = dish.ingredients`, `dishServings = dish.servings`. Woche nicht ladbar oder Gericht nicht (mehr) da → still die
    Rezeptmengen zeigen (kein Fehler; das Rezept ist das Wichtige). Rezept 404 → `error` (z. B. inzwischen gelöscht).
  - **Löschen** (nur `canManage`): `ConfirmDialog` „Rezept löschen?“ / „Gerichte, die schon daraus übernommen wurden, bleiben unverändert.“
    → `recipes.delete`; Erfolg oder 404 → `isDeleted` (Route: zurück); sonst `actionError`. `isBusy`-Sperre.
  - **Ansicht-Screen**: Titel = Rezeptname; „Für %1$d Portionen“ (`recipe_servings`); Abschnitt „Zutaten“ (`recipe_ingredients`) als Liste;
    „Zubereitung“ (`recipe_steps`) nummeriert „1. …“ — bei 0 Schritten „Keine Zubereitungsschritte hinterlegt.“ (`recipe_no_steps`);
    Quelle (`recipe_source`, Text der URL, kein Link-Öffnen nötig); `canManage` → `Button` „Bearbeiten“ (→ `onEdit(recipeId)`),
    `OutlinedButton` „Löschen“; `TextButton` „Zurück“. Neuladen per `LifecycleResumeEffect` (nach Bearbeiten).
  - **Editor laden**: Kind → `loadError = AppError.Forbidden` (kein Request). Neu → leeres Formular (`servings = 4`). Bestehend →
    `recipes.load(id)` → Felder: `ingredientsToText(recipe.ingredients)`, `steps.joinToString("\n")`, `sourceUrl.orEmpty()`.
  - **Editor-Felder**: Name (Zähler `x/60`, `take(NAME_MAX)`), Portionen als Stepper „–“ `n` „+“ (1–20, Buttons deaktiviert an den Grenzen,
    `contentDescription` „Weniger Portionen“/„Mehr Portionen“), Zutaten (`recipe_ingredients_label` „Zutaten (eine pro Zeile, z. B. 500 g Spaghetti)“,
    `minLines = 4`), Schritte (`recipe_steps_label` „Zubereitung (ein Schritt pro Zeile, optional)“, `minLines = 4`),
    Quelle (`recipe_source_label` „Quelle (Link, optional)“, einzeilig, `KeyboardType.Uri`), `SubmitButton` „Speichern“, `ErrorText(error)`,
    `TextButton` „Abbrechen“. Speichern → `recipes.save(recipeId, RecipeInput(...))` → `isDone`.
  - Portionen-Stepper als wiederverwendbares Composable `ServingsStepper(value, onChange, enabled)` in `ui/recipe/ServingsStepper.kt`
    (auch im Gericht-Editor, Task 8).
- **MIRROR**: VIEWMODEL_PATTERN, `DishEditScreen.kt` (Formular, `SubmitButton`, Zähler), `ConfirmDialog`.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.recipe.*`, `de.familyplanner.android.feature.meals.domain.plan.{MealPlanRepository, PlanLimits}`,
  `de.familyplanner.android.feature.meals.domain.attendance.mondayOf`, `androidx.compose.ui.text.input.KeyboardType`, `androidx.compose.foundation.text.KeyboardOptions`.
- **GOTCHA**: `RecipeViewModel` braucht `MealPlanRepository` direkt (nur `week`), kein neuer Use Case — oder einen kleinen
  `LoadDishOfDayUseCase`, falls detekt/Architektur-Review es fordert; einfachste Variante wählen. Ein **Kind** kann die Ansicht öffnen
  (Liste und Plan), sieht aber keine Verwaltung. Aus dem Plan geöffnet zeigt die Ansicht **keine** Verwaltungsknöpfe (`canManage`), damit
  „Löschen“ nicht neben „Zutaten des Gerichts“ steht. Steps können lang sein → `Column(verticalScroll)`, keine `LazyColumn` in `verticalScroll`.
  `ServingsStepper`: `IconButton` mit `Icons.Default.Remove`/`Add` aus `material-icons-core` (bereits Abhängigkeit) — `Remove` ist in core
  enthalten; falls nicht, Text-Buttons „–“/„+“.
- **VALIDATE**: ViewModel: `REQ-MEAL-060 view shows recipe for all members`, `REQ-MEAL-063 view from dish shows dish ingredients and servings`
  (Gericht „625 g Vollkornspaghetti“, 5 Portionen), `REQ-MEAL-063 missing dish falls back to recipe quantities`, `REQ-MEAL-060 parent deletes recipe`,
  `REQ-MEAL-060 deleting an already deleted recipe counts as done`, `REQ-MEAL-060 child cannot manage`, `REQ-MEAL-063 view from dish cannot manage`,
  `REQ-MEAL-060 editor loads existing recipe as text`, `REQ-MEAL-060 child editor is forbidden without request`,
  `REQ-MEAL-060 servings stay within 1 and 20`, `REQ-MEAL-060 save creates recipe and finishes`, `REQ-MEAL-060 duplicate name shows server message`,
  `REQ-MEAL-060 invalid url shows validation without request`, `double save sends once`.
  Screens: `REQ-MEAL-060 view shows servings ingredients and numbered steps`, `REQ-MEAL-060 view without steps shows hint`,
  `REQ-MEAL-060 parent sees edit and delete`, `REQ-MEAL-060 child sees no edit`, `REQ-MEAL-060 delete asks for confirmation`,
  `REQ-MEAL-063 view from dish shows Für 5 Portionen`, `REQ-MEAL-060 editor shows fields and counter`, `REQ-MEAL-060 stepper disables minus at one`.

### Task 8: UI – Gericht-Editor mit Rezeptauswahl und Plan-Link (TDD)
- **ACTION**: Tests in `RecipeViewModelsTest`/`RecipeScreensTest` (RED) → `M/ui/dish/DishEditViewModel.kt`, `DishEditScreen.kt`, `DishEditSections.kt`, `M/ui/plan/WeekPlanScreen.kt`, Strings.
- **IMPLEMENT**:
  ```kotlin
  // DishEditUiState: zusätzlich
  val recipeId: String? = null,
  val servings: Int? = null,
  /** Geladenes Rezept (Auswahl oder Rezeptbezug des Gerichts); nur damit lassen sich Portionen neu rechnen. */
  val recipe: Recipe? = null,
  val isRecipeLoading: Boolean = false,
  val presentByDate: Map<LocalDate, Int> = emptyMap(),
  // abgeleitet:
  val canUseRecipe: Boolean get() = !isLoading && loadError == null && !fieldsLocked && !isBusy
  val recipeRef: RecipeRef? get() = recipeId?.let { id -> servings?.let { RecipeRef(id, it) } }
  // Events zusätzlich: RecipePicked(recipeId), ServingsChanged(value), ClearRecipe
  ```
  - `withLoaded`: `presentByDate = data.presentByDate`, `recipeId = existing?.recipeId`, `servings = existing?.servings`; hat das Gericht einen
    Rezeptbezug, im Hintergrund `dishes.recipe(recipeId)` laden → `recipe` (Fehler still ignorieren: Stepper bleibt dann aus, Bezug bleibt).
  - `RecipePicked(id)` (nur `canUseRecipe`): `isRecipeLoading = true` → `dishes.recipe(id)`; Erfolg → `recipe`, `recipeId = id`,
    `servings = defaultServings(presentByDate[date] ?: 0)`, `name = recipe.name.take(PlanLimits.NAME_MAX)`,
    `ingredientsText = ingredientsToText(scaleIngredients(recipe.ingredients, recipe.servings, servings))`; Fehler → `error`.
  - `ServingsChanged(n)` (nur mit geladenem `recipe` und `!fieldsLocked`): `n.coerceIn(1, 20)`, Zutaten neu aus dem Rezept rechnen
    (überschreibt manuelle Zutaten-Änderungen, Name bleibt).
  - `ClearRecipe`: `recipeId = null, servings = null, recipe = null`; Name/Zutaten bleiben.
  - `save()`: `dishes.save(dishId, DishInput(date, cookId, name, ingredientsText, recipeRef), ingredientsRequired)`.
  - **Abschnitt** `RecipeSection(state, onEvent, onPickRecipe)` in `DishEditSections.kt`, oberhalb der Felder Name/Zutaten, nicht im
    `PARENT_MOVE`-Modus: ohne Bezug `OutlinedButton` „Aus Rezept wählen“ (`dish_recipe_pick`); mit Bezug Text „Aus Rezept „%1$s““
    (`dish_recipe_from`, Name aus `recipe`, sonst „Aus einem Rezept“ `dish_recipe_from_unknown`), `ServingsStepper` (nur mit `recipe`),
    Hinweis „Portionen ändern berechnet die Zutaten neu.“ (`dish_recipe_servings_hint`), `TextButton` „Anderes Rezept“ (= erneut wählen)
    und „Rezeptbezug entfernen“ (`dish_recipe_clear`). Ladeanzeige bei `isRecipeLoading`.
  - **Route**: `DishEditRoute(args, pickedRecipeId: String?, onRecipeConsumed: () -> Unit, onPickRecipe: () -> Unit, onDone)`;
    `LaunchedEffect(pickedRecipeId) { pickedRecipeId?.let { viewModel.onEvent(DishEditEvent.RecipePicked(it)); onRecipeConsumed() } }`.
  - **Plan**: `WeekPlanScreen`/`WeekPlanRoute` bekommen `onOpenRecipe: (RecipeViewArgs) -> Unit`; `DishSummary` zeigt bei
    `dish.recipeId != null && !state.isOffline` einen `TextButton` „Rezept ›“ (`plan_recipe`, `contentDescription` „Rezept zu %1$s ansehen“)
    → `onOpenRecipe(RecipeViewArgs(dish.recipeId, dish.date, dish.id))`. Für **alle** Mitglieder (Anwender).
- **MIRROR**: VIEWMODEL_PATTERN, `DishEditSections.kt` (`ReviewSection` als Vorlage für eine Sektion), `edit { … }`-Helfer.
- **IMPORTS**: `de.familyplanner.android.feature.meals.domain.recipe.{Recipe, RecipeRef, scaleIngredients, defaultServings, ingredientsToText}`,
  `de.familyplanner.android.feature.meals.domain.plan.DishInput`, `de.familyplanner.android.feature.meals.ui.recipe.{ServingsStepper, RecipeViewArgs}`.
- **GOTCHA**: `DishEditViewModel` ist schon 317 Zeilen → Rezept-Handler in private Funktionen bündeln (`onRecipe(event)` wie `onReview`)
  und, falls die Datei 400 Zeilen überschreitet, die reinen Zustandsübergänge als Erweiterungsfunktionen in `ui/dish/DishRecipeState.kt`
  (`DishEditUiState.withRecipe(recipe, servings)`) auslagern. detekt `CyclomaticComplexMethod` im `onEvent`-`when` beachten.
  `pickedRecipeId` wird nach Verbrauch entfernt (`onRecipeConsumed`), sonst übernimmt eine Rotation das Rezept erneut und überschreibt
  Eingaben. Ein Kind im `CHILD`-Modus darf wählen; Zutaten bleiben Pflicht (Rezepte haben ≥ 1 Zutat). Die Plan-Zeile ist selbst
  klickbar (Editor) — der innere `TextButton` konsumiert seinen Klick, kein Konflikt. Gespeicherter Stand (`isOffline`) zeigt keinen Link.
- **VALIDATE**: ViewModel: `REQ-MEAL-062 picking a recipe scales ingredients to present members` (2 Eltern + Ben am Di → 3 Portionen; 500 g → 375 g),
  `REQ-MEAL-062 picking a recipe sets name`, `REQ-MEAL-062 changing servings rescales from recipe` (5 → 625 g Vollkornspaghetti; Salz ohne Menge),
  `REQ-MEAL-062 servings are clamped to 1 and 20`, `REQ-MEAL-062 save sends recipe reference and parsed ingredients`,
  `REQ-MEAL-062 clearing the recipe keeps text and drops reference`, `REQ-MEAL-062 without recipe dish is saved as before`,
  `REQ-MEAL-062 existing dish keeps its recipe reference`, `REQ-MEAL-062 failed recipe load shows error`, `REQ-MEAL-062 child can pick a recipe`,
  `REQ-MEAL-062 move mode ignores recipe events`, `REQ-MEAL-062 edited ingredients after pick keep the recipe reference`.
  Screens: `REQ-MEAL-062 editor offers Aus Rezept wählen`, `REQ-MEAL-062 editor shows recipe name and servings stepper`,
  `REQ-MEAL-062 move mode shows no recipe section`, `REQ-MEAL-063 plan row with recipe shows Rezept link` (Callback mit `RecipeViewArgs`),
  `REQ-MEAL-063 offline plan shows no recipe link`, `REQ-MEAL-063 dish without recipe shows no link`.

### Task 9: Navigation, Koin, Startseite, Version
- **ACTION**: Routen und Graph erweitern, `mealsModule` ergänzen, `MealsHomeActions` + `AppNavHost`, Version erhöhen.
- **IMPLEMENT**:
  ```kotlin
  // MealsNavigation.kt
  /** [pick]: Rezept für den Gericht-Editor wählen (Ergebnis über [PICKED_RECIPE_KEY]). */
  @Serializable data class RecipeListRoute(val pick: Boolean = false)
  /** [dishDate] ISO-Datum; mit [dishId]: Ansicht zu einem Gericht (REQ-MEAL-063). */
  @Serializable data class RecipeViewRoute(val recipeId: String, val dishDate: String? = null, val dishId: String? = null)
  /** [recipeId] `null` = neues Rezept. */
  @Serializable data class RecipeEditRoute(val recipeId: String? = null)
  internal const val PICKED_RECIPE_KEY = "pickedRecipeId"

  @Composable
  fun MealsHomeActions(onOpenPlan: () -> Unit, onOpenWeek: () -> Unit, onOpenRecipes: () -> Unit) {
      … + OutlinedButton(onClick = onOpenRecipes, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.home_recipes)) }
  }

  // mealsGraph: zusätzlich
  composable<WeekPlanRoute> { WeekPlanRoute(onOpenEditor = …, onOpenRecipe = { a -> navController.navigate(RecipeViewRoute(a.recipeId, a.dishDate?.toString(), a.dishId)) }, onBack = …) }
  composable<DishEditRoute> { entry ->
      val route = entry.toRoute<DishEditRoute>()
      val picked by entry.savedStateHandle.getStateFlow<String?>(PICKED_RECIPE_KEY, null).collectAsStateWithLifecycle()
      DishEditRoute(
          DishEditArgs(LocalDate.parse(route.date), route.dishId, route.cookId),
          pickedRecipeId = picked,
          onRecipeConsumed = { entry.savedStateHandle[PICKED_RECIPE_KEY] = null },
          onPickRecipe = { navController.navigate(RecipeListRoute(pick = true)) },
          onDone = { navController.popBackStack() },
      )
  }
  composable<RecipeListRoute> { entry ->
      val pick = entry.toRoute<RecipeListRoute>().pick
      RecipeListRoute(
          args = RecipeListArgs(pick),
          onOpen = { id ->
              if (pick) {
                  navController.previousBackStackEntry?.savedStateHandle?.set(PICKED_RECIPE_KEY, id)
                  navController.popBackStack()
              } else {
                  navController.navigate(RecipeViewRoute(id))
              }
          },
          onCreate = { navController.navigate(RecipeEditRoute()) },
          onBack = { navController.popBackStack() },
      )
  }
  composable<RecipeViewRoute> { entry ->
      val r = entry.toRoute<RecipeViewRoute>()
      RecipeViewRoute(RecipeViewArgs(r.recipeId, r.dishDate?.let(LocalDate::parse), r.dishId),
          onEdit = { navController.navigate(RecipeEditRoute(it)) }, onDone = { navController.popBackStack() })
  }
  composable<RecipeEditRoute> { entry ->
      RecipeEditRoute(RecipeEditArgs(entry.toRoute<RecipeEditRoute>().recipeId), onDone = { navController.popBackStack() })
  }

  // MealsModule.kt
  // recipe
  single<RecipeRepository> { RemoteRecipeRepository(get(), get()) }
  factory { RecipeUseCases(LoadRecipesUseCase(get()), LoadRecipeUseCase(get()), SaveRecipeUseCase(get()), DeleteRecipeUseCase(get())) }
  viewModel { params -> RecipeListViewModel(params.get(), get(), get()) }
  viewModel { params -> RecipeViewModel(params.get(), get(), get(), get()) }
  viewModel { params -> RecipeEditViewModel(params.get(), get(), get()) }
  // plan: DishUseCases(..., ReviewDishUseCase(get()), LoadRecipeUseCase(get()))

  // AppNavHost.kt (MainHost)
  MealsHomeActions(onOpenPlan = …, onOpenWeek = …, onOpenRecipes = { navController.navigate(RecipeListRoute()) })
  ```
  - `android/app/build.gradle.kts`: `versionCode = 5`, `versionName = "0.5.0"`.
  - `MealsModuleTest`: `RecipeListViewModel` (mit `parametersOf(RecipeListArgs())`), `RecipeViewModel` (`RecipeViewArgs("r-1")`),
    `RecipeEditViewModel` (`RecipeEditArgs()`), `DishEditViewModel` wie bisher.
- **MIRROR**: bestehender `mealsGraph`/`mealsModule` (Kommentar-Gruppen `// attendance` / `// plan` / `// cooking` / `// recipe`).
- **IMPORTS**: `androidx.lifecycle.compose.collectAsStateWithLifecycle` (bereits Abhängigkeit `lifecycle-runtime-compose`),
  `androidx.compose.runtime.getValue`, `…ui.recipe.*`, `…ui.recipes.RecipeListRoute` (Composable), `…domain.recipe.*`, `…data.recipe.RemoteRecipeRepository`.
- **GOTCHA**: Gleichnamige Navigationsklassen und Composables (`RecipeListRoute` etc.) wie bei `DishEditRoute` — Overload über die
  Parameter, sonst Import-Alias. `getStateFlow` braucht einen Startwert und erlaubt `String?`. Der Rückweg geht über
  `previousBackStackEntry` = Gericht-Editor; aus der Rezeptliste von der Startseite (kein Auswahlmodus) wird nichts gesetzt.
  Kover schließt `*Route*` und `*Module*` aus — die Logik muss in ViewModels/Use Cases getestet sein. `AppNavHostTest` bleibt grün
  (Startseite zeigt jetzt drei Knöpfe; kein Test prüft deren Anzahl — prüfen).
- **VALIDATE**: `cd android && ./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest` grün (inkl. `MealsModuleTest`, `AppNavHostTest`).

### Task 10: Qualität & Coverage
- **ACTION**: CI-Kette lokal.
- **IMPLEMENT**: ktlint/detekt-Funde beheben (Funktionen < 60 Zeilen, Dateien < 400 Zeilen, `TooManyFunctions`, `CyclomaticComplexMethod`,
  `LongParameterList`, `MagicNumber`); Kover ≥ 80 %.
- **MIRROR**: `familyplanner.quality`-Konvention; 4b-Report (Sektionen, Bündel, Callback-Objekte wie `StatusActions`).
- **IMPORTS**: –
- **GOTCHA**: `ktlintFormat` vor `ktlintCheck`. `RecipeScreen`/`RecipeEditScreen` mit vielen Callbacks → kleines Callback-Objekt, falls
  detekt `LongParameterList` meldet. `lintDebug` meldet hartcodierte Texte — alle Texte in `strings.xml`.
- **VALIDATE**: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` grün;
  `git diff main -- api/ backend/` leer.

### Task 11: Doku, Traceability, PRD
- **ACTION**: V-Modell-Dokumente nachziehen.
- **IMPLEMENT**:
  - `docs/03-design/meals.md`: in „Rezepte & strukturierte Zutaten (Phase 5a)“ den Satz „Umrechnen der Mengen, REWE-Import und Oberflächen
    folgen in 5b.“ → „Oberflächen und Umrechnung: ‚App-Seite (Phase 5b)‘; REWE-Import: Phase 5c.“; neuer Abschnitt **„App-Seite (Phase 5b)“**:
    Pakete `domain/recipe`, `data/recipe`, `ui/recipes`, `ui/recipe`; Tabelle Screens/Rollen (Rezeptliste mit Suche/Auswahlmodus, Rezeptansicht
    inkl. Gericht-Kontext, Rezept-Editor nur Eltern, Gericht-Editor mit Rezeptauswahl, Plan-Link); Regeln: Zerleger (Zahl/Einheit/Name,
    bekannte Einheiten + „(n)“-Form, Dezimalkomma, Tausenderpunkt, sonst Name), Anzeigeformat wie Backend, App sendet ab 0.5.0 nur
    `structuredIngredients`; Portionen-Vorschlag (Eltern + anwesende Kinder, 1–20), Portionen ändern rechnet aus dem Rezept neu;
    Rezeptbezug bleibt nach manuellen Änderungen, „Rezeptbezug entfernen“; Suche clientseitig; Rezepte nicht offline; Löschen in der Ansicht.
  - `docs/04-verification/traceability.md`: REQ-MEAL-060 → Unit + „App: `IngredientLinesTest`, `ValidationTest` (recipe), `UseCasesTest` (recipe),
    `RecipeViewModelsTest`, `RecipeScreensTest`“, Integration + „App: `RemoteRecipeRepositoryTest`“, E2E „manuell: Abnahme Phase 5b
    ([android/README.md](../../android/README.md))“, Status `umgesetzt (Backend + App; Import in 5c)`; REQ-MEAL-062 → + „App: `IngredientLinesTest`,
    `ServingsTest`, `UseCasesTest` (plan), `RecipeViewModelsTest`, `RecipeScreensTest`“, „App: `RemoteMealPlanRepositoryTest`“, Status
    `umgesetzt (Backend + App)`; REQ-MEAL-063 → „App: `RecipeViewModelsTest`, `RecipeScreensTest`“, Status `umgesetzt (App)`.
    REQ-MEAL-061 bleibt `offen` (5c). Nach Geräte-Abnahme: „abgenommen (<Datum>, Geräte-Abnahme 5b)“.
  - `docs/01-requirements/meals.prd.md`: Phase-5-Zeile — Beschreibung „aufgeteilt in 5a Backend/API, 5b App (Rezepte, Auswahl, Ansicht) und
    5c REWE-Import“; PRP-Spalte „5b: `.claude/PRPs/plans/rezepte-app.plan.md` (Plan erstellt)“ → „5b: `.claude/PRPs/plans/completed/rezepte-app.plan.md`
    (umgesetzt, Report: `.claude/PRPs/reports/rezepte-app-report.md`); 5c: offen“. Status bleibt `in-progress` (5c fehlt).
  - `android/README.md`: Modulbeschreibung `feature:meals` um „Rezepte (Liste, Ansicht, Editor), Gericht aus Rezept“; Abschnitt
    „Abnahme-Checkliste Phase 5b“ (siehe Manual Validation).
  - Plan nach `.claude/PRPs/plans/completed/` verschieben (macht `/prp-implement`).
- **MIRROR**: bestehende Abschnitte/Zeilen; keine fremden Zeilen umformatieren.
- **IMPORTS**: –
- **GOTCHA**: Danach `grep -rn "folgt in 5b\|in 5b\b" docs/ android/` — Treffer, die 5b als „später“ beschreiben, anpassen.
- **VALIDATE**: Links zeigen auf existierende Dateien.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| Zerleger | „500.0 g Vollkornspaghetti“ | 500.0 · g · Vollkornspaghetti | |
| Zerleger | „2.0 Eier“ | 2.0 · – · Eier | |
| Zerleger | „1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen“ | 1.0 · Dose(n) · REWE Beste Wahl Kidney-Bohnen (Marke erst 5c) | ✔ |
| Zerleger | „Saft von 1 Limette“ | nur Name | ✔ |
| Zerleger | „1.000 g Mehl“ / „0.125 l Milch“ | 1000 / 0.125 | ✔ |
| Zerleger | „0 g Salz“ | nur Name | ✔ |
| Anzeige | 2.5 · Stange(n) · Lauch | „2,5 Stange(n) Lauch“ | |
| Umrechnung | 500 g, 4 → 5 | 625 g | |
| Umrechnung | 1 Stück, 3 → 1 | 0,333 | ✔ |
| Vorschlag | 0 / 25 Anwesende | 1 / 20 | ✔ |
| Rezept-Validierung | Portionen 0 / 21 | Meldung | ✔ |
| Rezept-Validierung | „rewe.de/rezepte“ ohne Schema | „Bitte einen gültigen Link …“ | ✔ |
| Gericht speichern | Text „625 g Spaghetti\nSalz“ + Rezept | `structuredIngredients` + `recipeId` + `servings`, `ingredients = []` | |
| Gericht-Editor | Portionen ändern nach manueller Zutatenänderung | Zutaten neu aus Rezept | ✔ |
| Gericht-Editor | Rotation nach Auswahl | Rezept wird nicht erneut übernommen | ✔ |
| Rezeptansicht | aus dem Plan, Gericht gelöscht | Rezeptmengen, kein Fehler | ✔ |
| Löschen | 404 | gilt als gelöscht | ✔ |

### Edge Cases Checklist
- [x] Leere Eingabe (Rezept ohne Name/Zutaten, leere Suche, leere Quelle)
- [x] Maximale Eingabe (Name 60, 30 Zutaten, 30 Schritte × 1000, URL 500, Portionen 20)
- [x] Ungültige Typen (unlesbare Menge → Name; Rezept mit 0 Portionen vom Server → Serverfehler)
- [x] Nebenläufigkeit (Doppelklick Speichern/Löschen; Rezept auf anderem Gerät gelöscht → 404)
- [x] Netzwerkfehler (Rezeptscreens zeigen „Server nicht erreichbar …“ mit Neu laden; offline kein Plan-Link)
- [x] Keine Berechtigung (Kind: kein Anlegen/Ändern/Löschen; Editor-Zugriff → „Nur für Eltern.“)
- [x] Rundung (3 Nachkommastellen, Anzeige ohne überflüssige Nullen, Dezimalkomma)
- [x] Navigationsergebnis (Rotation, Zurück ohne Auswahl)

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
EXPECT: Rezept anlegen, im Gericht-Editor wählen, Portionen ändern, speichern; `GET /api/v1/meals/weeks/<Montag>/plan` zeigt
`structuredIngredients` mit Mengen, `recipeId`, `servings`.

### Manual Validation (Abnahme Phase 5b – echte Handys gegen den Pi)
- [ ] Pi läuft mit Image inkl. Migration V5 (5a); Release-APK 0.5.0 (gleiche Signatur) per `adb install -r` auf Eltern- und Kinderhandy
- [ ] Elternhandy: Startseite → „Rezepte“ → „Neues Rezept“: Lauch-Carbonara mit 4 Portionen und Zutaten als Zeilen
      („500 g Vollkornspaghetti“, „Salz“, „2 Stange(n) Lauch“, …) und Schritten anlegen; ohne Zutaten/mit 0 Portionen gibt es eine Meldung
- [ ] Gleicher Name noch einmal → „Ein Rezept mit diesem Namen gibt es schon“
- [ ] Rezeptliste: Suche „lauch“ findet das Rezept, „xyz“ zeigt „Kein Rezept passt …“
- [ ] Kinderhandy: Rezepte sichtbar, kein „Neues Rezept“, in der Ansicht kein Bearbeiten/Löschen
- [ ] Kind reicht über „Mein Kochtag“ ein: „Aus Rezept wählen“ → Carbonara; Portionen vorbelegt mit den Anwesenden des Tages; auf 5 stellen →
      „625 g Vollkornspaghetti“, „2,5 Stange(n) Lauch“, „Salz“ ohne Menge; einreichen
- [ ] Essensplan (beide Handys): Gericht mit „Rezept ›“; Ansicht zeigt „Für 5 Portionen“, die Gerichtsmengen und die Schritte
- [ ] Eltern ändern danach das Rezept (z. B. 600 g) → das eingereichte Gericht bleibt bei 625 g
- [ ] Eltern löschen das Rezept → Gericht bleibt mit seinen Zutaten, „Rezept ›“ verschwindet nach Neuladen
- [ ] Elterngericht ohne Rezept mit „500g Mehl“ speichern → Plan zeigt „500 g Mehl“
- [ ] WLAN aus: Plan ohne „Rezept ›“; Rezeptliste zeigt „Server nicht erreichbar …“

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt, alle Validation Commands grün
- [ ] REQ-MEAL-060, -062, -063 haben App-Tests mit REQ-ID im Namen
- [ ] Ein Kind reicht ein Rezept mit umgerechneten Mengen ein (Teil des PRD-Erfolgssignals Phase 5; der Import-Teil folgt in 5c)
- [ ] App sendet `structuredIngredients` (mit `ingredients = []`) und den Rezeptbezug; der Plan zeigt die Mengen
- [ ] 400/403/404/409/offline mit deutschen Meldungen abgebildet
- [ ] `api/` und `backend/` unverändert; keine neue Bibliothek
- [ ] Geräte-Abnahme dokumentiert (README-Checkliste, Datum in traceability.md)

## Completion Checklist
- [ ] Code folgt den Mustern aus `feature:meals` (UiState/Event, `ApiResult`, Fakes, Use-Case-Bündel, Args-Objekte für Koin)
- [ ] Fehlerbehandlung über `ApiResult`/`AppError`; kein neues Logging
- [ ] Tests folgen Testmustern (MockWebServer, Robolectric, Fakes)
- [ ] Keine hartcodierten Grenzen außerhalb `PlanLimits`/`RecipeLimits`, keine Texte außerhalb `strings.xml`/Validation
- [ ] Moduldesign, Traceability, PRD, android/README aktualisiert
- [ ] Keine Abhängigkeit `feature:meals` ↔ `feature:auth`

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Zerleger deutet eine Zeile falsch (z. B. „2 große Zwiebeln“, „1.500 kg“) | M | L | Nur bekannte Einheiten + „(n)“-Form; sonst Name; die Zeile bleibt im Plan lesbar; Tests mit echten REWE-Zeilen; Einkaufsliste (Phase 6) führt nur gleiche Name+Einheit zusammen |
| Bisher reine Namenszeilen werden beim nächsten Speichern zu Menge/Einheit/Name | M | L | Gewollt (bessere Einkaufsliste); Anzeige bleibt gleich (gleiches Format wie Backend) |
| Portionen ändern überschreibt manuelle Zutaten-Änderungen | M | L | Hinweistext im Editor; Reihenfolge „erst Portionen, dann anpassen“ |
| Navigationsergebnis wird doppelt verbraucht (Rotation) | M | M | Wert nach Verbrauch entfernen (`onRecipeConsumed`); Test |
| `DishEditViewModel` wird zu groß (detekt) | M | L | Rezept-Handler gebündelt, Zustandsübergänge in `DishRecipeState.kt` |
| Ältere App (0.4.0) auf einem Kinderhandy sendet weiter Zeilen | L | L | Server 5a hält Struktur bei unveränderten Zeilen; alle Handys bei der Abnahme aktualisieren |
| Eltern-Anwesenheit ist unbekannt → Vorschlag zu hoch | L | L | Vorschlag ist änderbar (PRD) |

## Notes
- Heute (So 2026-10-18): diese Woche 12.10., Planwoche 19.10.
- Echte REWE-Zutatenzeilen (geprüft im Browser 2026-10-18), als Testdaten nutzbar (Fakten, keine Schritte kopieren):
  Carbonara (4 Portionen, 11 Zutaten): „500.0 g Vollkornspaghetti“, „Salz“, „2.0 Stange(n) Lauch“, „2.0 Zehe(n) Knoblauch“,
  „70.0 g Optional: Bio-Bacon“, „2.0 EL Rapsöl“, „200.0 g Sahne“, „2.0 Eier“, „Pfeffer“, „Muskatnuss“, „50.0 g Parmesan“.
  Quesadilla (4 Portionen, 13 Zutaten): „4.0 Frühlingszwiebeln“, „200.0 g Champignons“, „1.0 Zucchini“, „1.0 Möhre“, „1.0 Zwiebel“,
  „2.0 EL REWE Bio Rapsöl“, „Salz“, „Pfeffer“, „1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen“, „Saft von 1 Limette“,
  „Kreuzkümmel (gemahlen)“, „4.0 REWE Beste Wahl Tortilla-Wraps“, „200.0 g ja! Gouda gerieben“.
- Für **5c** festgehalten (aus derselben Prüfung): Die Seite hat mehrere `application/ld+json`-Blöcke; das Rezept steckt als `mainEntity` eines
  `Webpage`-Objekts im Block `id="recipe-schema"`, **weitere Blöcke haben ebenfalls `@type: Recipe`** (nur `aggregateRating` bzw. `review`, ohne
  Zutaten) → der Leser muss das `Recipe` **mit** `recipeIngredient` wählen. `recipeYield` ist der String „4“, `recipeInstructions` sind
  `HowToStep` mit `text`; das Rezeptobjekt hat keine `url` (die hat das `Webpage`-Objekt). Keine HTML-Entities im JSON gesehen.
- Erfahrungen nach Umsetzung im Report festhalten (`.claude/PRPs/reports/rezepte-app-report.md`).
