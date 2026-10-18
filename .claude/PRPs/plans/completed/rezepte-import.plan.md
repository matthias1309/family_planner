# Plan: REWE-Rezept-Import in der App (Phase 5c)

## Summary
Eltern übernehmen ein Rezept von rewe.de über das Android-**Teilen-Menü** oder durch **Einfügen eines Links** im Editor
„Neues Rezept“. Die App lädt die Seite in einer unsichtbaren WebView, liest die schema.org-JSON-LD-Blöcke, findet das `Recipe`,
zerlegt die Zutatenzeilen mit dem vorhandenen `parseIngredientLine`, entfernt Eigenmarken-Präfixe und füllt damit den bestehenden
Rezept-Editor vor. Gespeichert wird erst über „Speichern“ und die vorhandene Rezept-API. `api/` und `backend/` bleiben unverändert.

## User Story
Als Elternteil möchte ich ein Rezept von rewe.de über „Teilen“ oder per Link übernehmen, damit Lieblingsrezepte mit wenigen
Tippern in der App sind (REQ-MEAL-061).

## Problem → Solution
Rezepte müssen in 5b komplett abgetippt werden → Teilen/Link füllt Name, Portionen, Zutaten, Schritte und Quelle vor; die Eltern
prüfen und speichern.

## Metadata
- **Complexity**: Large (≈ 10 neue Dateien Main/Test, ≈ 12 geänderte; ein neues Android-Konzept: WebView + Teilen-Ziel)
- **Source PRD**: `docs/01-requirements/meals.prd.md`
- **PRD Phase**: 5 Rezeptsammlung — Teil 5c (REWE-Import)
- **Requirement**: REQ-MEAL-061 (ADR-0012 Punkt 5)
- **Branch**: `feat/rezepte-import` (bereits von `origin/main` = Merge von PR 15 angelegt; dieser Plan liegt dort uncommittet)
- **Estimated Files**: ≈ 22

---

## UX Design

### Before
```
Startseite → Rezepte → „Neues Rezept“
┌──────────────────────────────────────┐
│ Neues Rezept                         │
│ Name [                     ]         │
│ Portionen  –  4  +                   │
│ Zutaten [ alles abtippen … ]         │
│ Zubereitung [ … ]                    │
│ Quelle (Link, optional) [      ]     │
│ [Speichern]   Abbrechen              │
└──────────────────────────────────────┘
Teilen aus Chrome/REWE-App: Family Planner erscheint nicht.
```

### After
```
A) Chrome/REWE-App → Teilen → „Family Planner“
   → (angemeldet als Elternteil) Rezept-Editor öffnet sich, Import läuft sofort
B) Rezepte → „Neues Rezept“ → Link einfügen → „Übernehmen“

┌──────────────────────────────────────┐
│ Neues Rezept                         │
│ Link zum Rezept (optional)           │
│ [https://www.rewe.de/rezepte/…  ]    │
│ [Übernehmen]   (⟳ „Rezept wird       │
│                 gelesen …“)          │
│ ⚠ Auf dieser Seite wurde kein Rezept │  ← nur bei Fehler; Felder bleiben
│   gefunden. Du kannst es unten …     │
│ Name [Spaghetti mit Lauch-Carbon…]   │  ← vorausgefüllt
│ Portionen  –  4  +                   │
│ Zutaten [500 g Vollkornspaghetti     │
│          Salz                        │
│          2 Stange(n) Lauch …]        │
│ Zubereitung [Schritt 1 …]            │
│ [Speichern]   Abbrechen              │
└──────────────────────────────────────┘
Kind teilt einen Link → Editor zeigt „Nur für Eltern.“ (wie bisher), keine Seite wird geladen.
```

### Interaction Changes
| Touchpoint | Before | After | Notes |
|---|---|---|---|
| Android-Teilen-Menü | App nicht gelistet | „Family Planner“ für `text/plain` | `ACTION_SEND` an `MainActivity` |
| Rezept-Editor „Neu“ | Quelle-Feld unten | Link-Feld **oben** + „Übernehmen“ | Bearbeiten-Modus unverändert (Quelle unten, kein Import) |
| Import erfolgreich | – | Name, Portionen, Zutaten, Schritte, Quelle werden **überschrieben** | Nichts gespeichert, bis „Speichern“ |
| Import fehlgeschlagen | – | Meldung unter dem Link-Feld, Formular bleibt unverändert | manuelles Anlegen weiter möglich |
| Abgemeldet + Teilen | – | Link bleibt vorgemerkt, Import startet nach der Anmeldung | `SharedRecipeInbox` |

---

## Mandatory Reading

| Priority | File | Lines | Why |
|---|---|---|---|
| P0 | `docs/02-architecture/adr/0012-recipes-and-rewe-import.md` | Entscheidung 5 | Verbindliche Regeln: WebView in der App, JSON-LD, Zerleger, Markenliste, immer erst Editor |
| P0 | `docs/01-requirements/meals.prd.md` | 255-271 | Abnahmekriterien REQ-MEAL-061 |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/recipe/IngredientLines.kt` | all | `parseIngredientLine`, `ingredientsToText`, `displayText` — wiederverwenden, nicht ändern |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipe/RecipeEditViewModel.kt` | all | Wird um den Import erweitert |
| P0 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/ui/recipe/RecipeEditScreen.kt` | all | Wird um die Link-Zeile erweitert |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/domain/recipe/Validation.kt` | 42-57 | `isHttpUrl` (privat) — für die Link-Erkennung `internal` machen |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsNavigation.kt` | 57-61, 161-163 | `RecipeEditRoute` bekommt `importText` |
| P1 | `android/feature/meals/src/main/kotlin/de/familyplanner/android/feature/meals/MealsModule.kt` | 99-111 | Koin-Verdrahtung Rezepte |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/AppNavHost.kt` | 61-74 | `MainHost` — hier wird der geteilte Link abgeholt |
| P1 | `android/app/src/main/kotlin/de/familyplanner/android/app/MainActivity.kt` | all | Intent auslesen |
| P1 | `android/app/src/main/AndroidManifest.xml` | 16-23 | Intent-Filter ergänzen |
| P1 | `android/app/src/main/res/xml/network_security_config.xml` | all | `base-config` erlaubt HTTPS zu System-CAs → rewe.de lädt; Cleartext ist aus |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/fakes/RecipeFakes.kt` | all | Fakes/Fixtures-Stil |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/RecipeViewModelsTest.kt` | 49-80, 300-380 | VM-Test-Setup |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/ui/RecipeScreensTest.kt` | 1-70 | Robolectric-Screen-Test-Setup |
| P2 | `android/feature/meals/src/test/kotlin/de/familyplanner/android/feature/meals/MealsModuleTest.kt` | all | Koin-Graph-Test (Overrides!) |
| P2 | `android/app/src/test/kotlin/de/familyplanner/android/app/AppNavHostTest.kt` | all | Koin-Setup der App-Tests (braucht die Inbox) |
| P2 | `android/app/build.gradle.kts` | 75-104 | Kover-Ausschlüsse/80 %-Regel |

## External Documentation

| Topic | Source | Key Takeaway |
|---|---|---|
| Teilen-Ziel | developer.android.com „Receiving simple data from other apps“ | Intent-Filter `ACTION_SEND` + `CATEGORY_DEFAULT` + `mimeType="text/plain"`; Text steht in `Intent.EXTRA_TEXT` |
| `WebView.evaluateJavascript` | developer.android.com `WebView` | Muss auf dem Main-Thread laufen; Callback bekommt den Rückgabewert **JSON-kodiert** (Array von Strings → `["…","…"]`; `undefined`/kein Wert → `"null"`) |
| `WebViewClient.onReceivedError(view, request, error)` | developer.android.com | Wird auch für Unterressourcen gerufen → nur `request.isForMainFrame` werten |
| schema.org `Recipe` | schema.org/Recipe | `recipeIngredient` (Text[]), `recipeYield` (Text/Zahl/Array), `recipeInstructions` (Text, `HowToStep`, `HowToSection.itemListElement`) |

Geprüft am 2026-10-18 im Browser (echte Seiten, nicht curl):

```
KEY_INSIGHT: Beide REWE-Seiten haben 5 JSON-LD-Blöcke: [0] {"@type":"Webpage","mainEntity":{"@type":"Recipe", name, recipeIngredient,
             recipeYield:"4", recipeInstructions:[HowToStep{text,name,url}×n], …}} (script id="recipe-schema"),
             [1] Organization, [2] {"@type":"Recipe","@id":…,"aggregateRating":…}, [3] {"@type":"Recipe","@id":…,"review":…},
             [4] BreadcrumbList.
APPLIES_TO: JsonLdRecipeReader — „das erste Recipe mit Name UND mindestens einer Zutat“ nehmen, sonst landet man bei Block [2].
GOTCHA: Das Recipe hat KEIN "url"-Feld (ADR-0012 nennt es, ist aber nicht da) → Quelle = geladener Link.
        Name der Carbonara: „Spaghetti mit Lauch-Carbonara mit & ohne Speck“ (47 Zeichen, „&“ roh, keine HTML-Entities).
        Schritte: 5 bzw. 4 HowToStep, `text` ohne Zeilenumbrüche/HTML, 58–187 Zeichen.
```

Echte Zutatenzeilen (Fakten, dürfen als Fixture verwendet werden; Schritte im Fixture **selbst formulieren**, wie in `RecipeFakes.kt`):

- Carbonara (`https://www.rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/`, yield „4“):
  `"500.0 g Vollkornspaghetti"`, `"Salz"`, `"2.0 Stange(n) Lauch"`, `"2.0 Zehe(n) Knoblauch"`, `"70.0 g Optional: Bio-Bacon"`,
  `"2.0 EL Rapsöl"`, `"200.0 g Sahne"`, `"2.0 Eier"`, `"Pfeffer"`, `"Muskatnuss"`, `"50.0 g Parmesan"` (11 Zeilen)
- Quesadilla (`https://www.rewe.de/rezepte/bunte-gemuese-quesadilla/`, Name „Bunte Gemüse Quesadilla“, yield „4“):
  `"4.0 Frühlingszwiebeln"`, `"200.0 g Champignons"`, `"1.0 Zucchini"`, `"1.0 Möhre"`, `"1.0 Zwiebel"`, `"2.0 EL REWE Bio Rapsöl"`,
  `"Salz"`, `"Pfeffer"`, `"1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen"`, `"Saft von 1 Limette"`, `"Kreuzkümmel (gemahlen)"`,
  `"4.0 REWE Beste Wahl Tortilla-Wraps"`, `"200.0 g ja! Gouda gerieben"` (13 Zeilen)

---

## Patterns to Mirror

### NAMING_CONVENTION / Paketstruktur
// SOURCE: `feature/meals/.../domain/recipe/*`, `data/recipe/*`, `ui/recipe/*`
- Domäne: reine Funktionen auf Top-Level (`parseIngredientLine`, `ingredientsToText`), Use Cases als Klassen mit `suspend operator fun invoke`,
  Repository-Interfaces in `domain`, Implementierungen `Remote…`/`DataStore…` in `data`.
- KDoc/Kommentare **deutsch**, knapp, mit ADR-/REQ-Verweis: `/** Zerlegt eine Zeile in Menge/Einheit/Name; alles, was nicht sicher passt, wird ganz zum Namen (ADR-0012). */`
- Konstanten-Liste als `private val` auf Top-Level (siehe `KNOWN_UNITS`), Grenzen in `RecipeLimits`.

### ERROR_HANDLING (Parsing ohne Exceptions nach außen)
// SOURCE: `feature/meals/.../data/Parsing.kt:13-22`
```kotlin
internal fun parseDate(value: String?): LocalDate? =
    value?.let {
        try {
            LocalDate.parse(it)
        } catch (
            @Suppress("SwallowedException") e: DateTimeParseException,
        ) {
            null
        }
    }
```
→ Für `Json.parseToJsonElement` genauso mit `kotlinx.serialization.SerializationException` (ungültige Blöcke werden übersprungen).

### UI-ZUSTAND / EVENTS
// SOURCE: `ui/recipe/RecipeEditViewModel.kt:21-90`
```kotlin
data class RecipeEditUiState(
    val recipeId: String?,
    ...
    val isBusy: Boolean = false,
    val error: AppError? = null,
    val isDone: Boolean = false,
) {
    val canSave: Boolean get() = !isLoading && loadError == null && !isBusy && name.isNotBlank() && ingredientsText.isNotBlank()
}
sealed interface RecipeEditEvent { data class NameChanged(val value: String) : RecipeEditEvent ... data object Save : RecipeEditEvent }
private fun edit(change: (RecipeEditUiState) -> RecipeEditUiState) = _state.update { change(it).copy(error = null) }
```

### KOIN
// SOURCE: `MealsModule.kt:99-111`
```kotlin
single<RecipeRepository> { RemoteRecipeRepository(get(), get()) }
factory { RecipeUseCases(LoadRecipesUseCase(get()), LoadRecipeUseCase(get()), SaveRecipeUseCase(get()), DeleteRecipeUseCase(get())) }
viewModel { params -> RecipeEditViewModel(params.get(), get(), get()) }
```

### NAVIGATION (typsichere Routen)
// SOURCE: `MealsNavigation.kt:57-61, 161-163`
```kotlin
@Serializable
data class RecipeEditRoute(val recipeId: String? = null)
composable<RecipeEditRoute> { entry ->
    RecipeEditRoute(RecipeEditArgs(entry.toRoute<RecipeEditRoute>().recipeId), onDone = { navController.popBackStack() })
}
```

### TEST_STRUCTURE
// SOURCE: `test/.../domain/recipe/IngredientLinesTest.kt:1-12` (reine Funktionen, `kotlin.test`, REQ-ID im Namen)
```kotlin
class IngredientLinesTest {
    @Test
    fun `REQ-MEAL-062 quantity unit and name are split`() {
        assertEquals(Ingredient("Vollkornspaghetti", 500.0, "g"), parseIngredientLine("500.0 g Vollkornspaghetti"))
    }
```
// SOURCE: `test/.../ui/RecipeViewModelsTest.kt:49-62` (VM-Tests ohne Robolectric, `MainDispatcherRule`, Fakes)
```kotlin
class RecipeViewModelsTest {
    @get:Rule
    val mainDispatcher = MainDispatcherRule()
    private val recipes = FakeRecipeRepository()
    private var sessions = InMemorySessionStore(parentSession)
    private fun editViewModel(recipeId: String? = null) = RecipeEditViewModel(RecipeEditArgs(recipeId), sessions, recipeUseCases(recipes))
```
// SOURCE: `test/.../ui/RecipeScreensTest.kt:52-58` (Robolectric + `createComposeRule`, `FamilyPlannerTheme`)
```kotlin
@RunWith(RobolectricTestRunner::class)
class RecipeScreensTest {
    @get:Rule
    val compose = createComposeRule()
    private fun show(content: @androidx.compose.runtime.Composable () -> Unit) =
        compose.setContent { FamilyPlannerTheme(content = content) }
```
Doppelklick-/Parallelitätstests nutzen ein `CompletableDeferred`-Gate im Fake (siehe `FakeRecipeRepository.createGate`).

### LOGGING
Die App loggt in `feature:meals` nicht (kein `Log.*`, kein Timber). **Nicht einführen.** Fehler werden als Zustand angezeigt.

---

## Files to Change

| File | Action | Justification |
|---|---|---|
| `android/feature/meals/src/main/kotlin/.../domain/recipe/StoreBrands.kt` | CREATE | `stripStoreBrand(name)` + Präfixliste (ADR-0012) |
| `android/feature/meals/src/main/kotlin/.../domain/recipe/RecipeLink.kt` | CREATE | `extractRecipeUrl(text)` — Link aus geteiltem/eingefügtem Text |
| `android/feature/meals/src/main/kotlin/.../domain/recipe/RecipeImport.kt` | CREATE | `RecipeSource`-Interface, `SourceRecipe`, `SourceResult`, `RecipeImportError`, `RecipeImportResult`, `ImportRecipeUseCase` |
| `android/feature/meals/src/main/kotlin/.../domain/recipe/Validation.kt` | UPDATE | `isHttpUrl` von `private` auf `internal` |
| `android/feature/meals/src/main/kotlin/.../data/recipe/JsonLdRecipeReader.kt` | CREATE | `decodeScriptList(raw)`, `readJsonLdRecipe(blocks)` (rein, testbar) |
| `android/feature/meals/src/main/kotlin/.../data/recipe/WebRecipeSource.kt` | CREATE | `PageScriptLoader`-Interface + `WebRecipeSource` (Loader → Reader → `SourceResult`) |
| `android/feature/meals/src/main/kotlin/.../data/recipe/WebViewPageScriptLoader.kt` | CREATE | Unsichtbare WebView, liefert die JSON-LD-Texte (einzige Android-Klasse, von Kover ausgenommen) |
| `android/feature/meals/src/main/kotlin/.../SharedRecipeInbox.kt` | CREATE | Vorgemerkter geteilter Text bis `MainHost` ihn abholt |
| `android/feature/meals/src/main/kotlin/.../ui/recipe/RecipeArgs.kt` | UPDATE | `RecipeEditArgs.importText` |
| `android/feature/meals/src/main/kotlin/.../ui/recipe/RecipeEditViewModel.kt` | UPDATE | Import-Zustand + Events |
| `android/feature/meals/src/main/kotlin/.../ui/recipe/RecipeEditScreen.kt` | UPDATE | Link-Zeile oben (nur neu), Fehlermeldung, Fortschritt |
| `android/feature/meals/src/main/kotlin/.../MealsNavigation.kt` | UPDATE | `RecipeEditRoute.importText`, `fun NavHostController.openRecipeImport(text)` |
| `android/feature/meals/src/main/kotlin/.../MealsModule.kt` | UPDATE | Bindungen Inbox, Loader, Source, Use Case, VM-Parameter |
| `android/feature/meals/src/main/res/values/strings.xml` | UPDATE | neue Texte |
| `android/app/src/main/AndroidManifest.xml` | UPDATE | `ACTION_SEND`-Intent-Filter |
| `android/app/src/main/kotlin/.../app/MainActivity.kt` | UPDATE | `EXTRA_TEXT` → Inbox |
| `android/app/src/main/kotlin/.../app/AppNavHost.kt` | UPDATE | `MainHost` holt Inbox ab und navigiert |
| `android/app/build.gradle.kts` | UPDATE | Kover-Ausschluss `*.WebViewPageScriptLoader*`; Version 0.5.1 / versionCode 6 |
| `android/feature/meals/src/test/kotlin/.../domain/recipe/StoreBrandsTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../domain/recipe/RecipeLinkTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../domain/recipe/ImportRecipeUseCaseTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../data/JsonLdRecipeReaderTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../data/WebRecipeSourceTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../fakes/ImportFakes.kt` | CREATE | `FakeRecipeSource`, `FakePageScriptLoader`, REWE-Fixtures |
| `android/feature/meals/src/test/kotlin/.../ui/RecipeImportViewModelTest.kt` | CREATE | (eigene Datei; `RecipeViewModelsTest` hat schon 608 Zeilen) |
| `android/feature/meals/src/test/kotlin/.../ui/RecipeImportScreenTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../SharedRecipeInboxTest.kt` | CREATE | |
| `android/feature/meals/src/test/kotlin/.../MealsModuleTest.kt` | UPDATE | Override `PageScriptLoader`, VM mit `importText` |
| `android/app/src/test/kotlin/.../app/AppNavHostTest.kt` | UPDATE | Inbox im Koin-Testmodul; Test „geteilter Link öffnet Editor“ |
| `docs/03-design/meals.md` | UPDATE | Abschnitt „App-Seite (Phase 5c)“ |
| `docs/04-verification/traceability.md` | UPDATE | Zeile REQ-MEAL-061 |
| `android/README.md` | UPDATE | „Abnahme-Checkliste Phase 5c“ |
| `docs/01-requirements/meals.prd.md` | UPDATE | Phase-5-Zeile: 5c-Plan/Report (nach Umsetzung) |

(`...` = `de/familyplanner/android/feature/meals` bzw. `de/familyplanner/android/app`)

## NOT Building

- Kein Server-Import, keine API-/Backend-Änderung (ADR-0012: Bot-Abwehr; `git diff main -- api/ backend/` muss leer bleiben).
- Keine Umgehung von Captcha/Bot-Abwehr, **kein** gefälschter User-Agent, keine Header-Tricks.
- Kein HTML-Scraping ohne JSON-LD, keine REWE-spezifischen Selektoren (nur schema.org).
- Keine Bilder, Nährwerte, Zeiten, Kategorien.
- Kein Import im Bearbeiten-Modus eines bestehenden Rezepts.
- Keine HTML-Entity-Dekodierung (REWE liefert keine; YAGNI).
- Keine Mengen-/Einheiten-Normalisierung über `parseIngredientLine` hinaus; `parseIngredientLine` selbst wird **nicht** geändert.
- Kein Duplikat-Check vor dem Import (Server meldet 409 beim Speichern wie bisher).
- Keine Offline-Vormerkung „später importieren“.
- Kein `launchMode`-Wechsel / kein `onNewIntent` (Standard-Modus: Teilen startet eine neue Activity-Instanz → `onCreate`).

---

## Step-by-Step Tasks

TDD je Task: erst Test (RED), dann Implementierung (GREEN). Testnamen mit `REQ-MEAL-061 …`.

### Task 0: Ausgangslage
- **ACTION**: Prüfen, dass `git branch --show-current` = `feat/rezepte-import` und `git log -1 origin/main` = Merge von PR 15 ist; dieser Plan liegt uncommittet dort.
- **VALIDATE**: `cd android && ./gradlew testDebugUnitTest` grün vor jeder Änderung.

### Task 1: Eigenmarken entfernen (`StoreBrands.kt`)
- **ACTION**: CREATE `domain/recipe/StoreBrands.kt` + `StoreBrandsTest.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Eigenmarken-Präfixe (ADR-0012), längster Treffer zuerst; erweiterbar. */
  private val STORE_BRANDS = listOf("REWE Beste Wahl", "REWE Feine Welt", "REWE Bio", "ja!", "REWE").sortedByDescending { it.length }

  /** Entfernt ein Eigenmarken-Präfix am Anfang des Namens, nur als ganzes Wort; bleibt nichts übrig, gilt der ursprüngliche Name. */
  fun stripStoreBrand(name: String): String
  ```
  Logik: `val text = name.trim()`; erster Brand `b` mit `text.startsWith(b, ignoreCase = true)` **und** (`text.length == b.length` oder `text[b.length].isWhitespace()`);
  Rest = `text.substring(b.length).trim()`; leer → `text`; sonst Rest. Kein Treffer → `text`. Nur **ein** Präfix entfernen.
- **MIRROR**: `KNOWN_UNITS` (private Top-Level-Konstante), KDoc deutsch.
- **GOTCHA**: „ja!“ endet mit `!` → Wortgrenze nur über das **folgende** Zeichen prüfen, nicht mit `\b`-Regex. „Bio-Bacon“, „Optional: Bio-Bacon“, „REWEnudeln“ bleiben unverändert. „REWE“ allein → „REWE“.
- **VALIDATE** (Tests): „REWE Bio Rapsöl“→„Rapsöl“; „REWE Beste Wahl Kidney-Bohnen“→„Kidney-Bohnen“ (nicht „Beste Wahl Kidney-Bohnen“); „ja! Gouda gerieben“→„Gouda gerieben“;
  „REWE Feine Welt Pesto“→„Pesto“; „rewe bio Rapsöl“→„Rapsöl“; „REWE“→„REWE“; „REWE Bio“→„REWE Bio“; „Optional: Bio-Bacon“ unverändert; „REWEnudeln“ unverändert.

### Task 2: Link aus Text (`RecipeLink.kt`)
- **ACTION**: CREATE `domain/recipe/RecipeLink.kt` + `RecipeLinkTest.kt`; in `Validation.kt` `private fun isHttpUrl` → `internal fun isHttpUrl`.
- **IMPLEMENT**:
  ```kotlin
  private val LINK = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
  private const val TRAILING = ".,;:!?)]}\"'»“”"

  /** Erster http(s)-Link im geteilten oder eingefügten Text (REWE-App teilt „Titel https://…“), ohne Satzzeichen am Ende. */
  fun extractRecipeUrl(text: String): String? =
      LINK.find(text)?.value?.trimEnd { it in TRAILING }?.takeIf(::isHttpUrl)
  ```
- **GOTCHA**: `isHttpUrl` nutzt `java.net.URI`; Umlaute im Pfad werfen `URISyntaxException` → `null` (akzeptiert).
- **VALIDATE**: reiner Link; „Spaghetti mit Lauch-Carbonara https://www.rewe.de/rezepte/x/“; Link mit „)“ oder „.“ am Ende; zwei Links → erster; „ftp://…“, „rewe.de/rezepte“ ohne Schema, leerer Text → `null`.

### Task 3: JSON-LD-Leser (`data/recipe/JsonLdRecipeReader.kt`)
- **ACTION**: CREATE + `data/JsonLdRecipeReaderTest.kt`.
- **IMPLEMENT** (Domänentyp `SourceRecipe` aus Task 4 vorziehen oder hier zuerst anlegen):
  ```kotlin
  /** Rohdaten eines gefundenen schema.org-Recipe (noch nicht zerlegt). */
  data class SourceRecipe(val name: String, val servings: Int?, val ingredientLines: List<String>, val steps: List<String>)   // in domain/recipe/RecipeImport.kt

  /** Ergebnis von `evaluateJavascript` (JSON-kodiertes String-Array) → Texte der Skripte; alles andere → leer. */
  internal fun decodeScriptList(raw: String?): List<String>

  /** Erstes `Recipe` mit Name und mindestens einer Zutat – direkt, in `@graph`, als `mainEntity` oder in einem Array (ADR-0012). */
  internal fun readJsonLdRecipe(blocks: List<String>): SourceRecipe?
  ```
  - `decodeScriptList`: `parseOrNull(raw)` → `JsonArray` → Elemente, die `JsonPrimitive` mit `isString` sind → `content`; sonst `emptyList()`.
  - `parseOrNull(text: String?): JsonElement?` mit try/catch `SerializationException` (Muster `parseDate`).
  - Kandidaten-Suche `candidates(element, depth)`: `JsonArray` → alle Elemente; `JsonObject` → sich selbst, dann `["@graph"]`, dann `["mainEntity"]`; Tiefe max. `JSON_LD_DEPTH_MAX = 4` (unvertrauenswürdige Eingabe).
  - `isRecipe(obj)`: `@type` ist String „Recipe“ oder Array, das „Recipe“ enthält.
  - Abbildung: `name` = String, getrimmt, leer → kein Treffer; `ingredientLines` = `recipeIngredient` als Array von Strings (oder einzelner String), getrimmt, ohne leere; leer → kein Treffer;
    `servings` = `recipeYield`: Zahl → `toInt`; String → erste Zahl `Regex("""\d+""")`; Array → erstes Element, das eine Zahl ergibt; sonst `null`;
    `steps` = `recipeInstructions`: String → Zeilen; Array → je Element: String, Objekt mit `text` (sonst `name`), `HowToSection` → rekursiv `itemListElement`; jeder Schritt Whitespace zu einem Leerzeichen zusammengefasst (`Regex("""\s+""")`), getrimmt, leere weg.
  - `kotlinx.serialization.json.Json.parseToJsonElement` (verfügbar über `core:data` → `api(libs.serialization.json)`).
- **MIRROR**: ERROR_HANDLING (`Parsing.kt`), `internal` für Hilfsfunktionen in `data`.
- **GOTCHA**: REWE hat **mehrere** Blöcke mit `@type: "Recipe"` (nur `aggregateRating`/`review`) — deshalb „Name **und** Zutaten“ als Bedingung. `@type` heißt auf REWE „Webpage“ (nicht „WebPage“) — nicht auf den Typ des Containers prüfen.
  `evaluateJavascript` liefert bei fehlendem Ergebnis den String `"null"`.
- **VALIDATE** (Tests, Fixture in `ImportFakes.kt` als Kotlin-Raw-Strings nachgebaut nach der REWE-Struktur oben, Schritte selbst formuliert):
  REWE-ähnliche 5 Blöcke → Carbonara mit 11 Zeilen, `servings = 4`, Schritte in Reihenfolge; Reihenfolge der Blöcke vertauscht (Rating-Recipe zuerst) → trotzdem das volle Rezept;
  `{"@graph":[…]}`; direktes `Recipe`; `@type` als Array `["Recipe","NewsArticle"]`; `recipeYield` als `4`, `["4","4 Portionen"]`, `"4 Portionen"`, fehlt → `null`;
  `recipeInstructions` als String mit Zeilen, als String-Array, als `HowToSection` mit `itemListElement`; ungültiger JSON-Block wird übersprungen; nur Organization/Breadcrumb → `null`;
  `decodeScriptList("[\"a\",\"b\"]")` → `[a, b]`, `"null"`, `""`, `null`, `"{}"`, Kaputtes → leer.

### Task 4: Import-Domäne (`domain/recipe/RecipeImport.kt`)
- **ACTION**: CREATE + `ImportRecipeUseCaseTest.kt`; Fakes in `fakes/ImportFakes.kt`.
- **IMPLEMENT**:
  ```kotlin
  enum class RecipeImportError { NO_LINK, UNREACHABLE, NO_RECIPE }

  sealed interface SourceResult {
      data class Found(val recipe: SourceRecipe) : SourceResult
      data object NotFound : SourceResult
      data object Unreachable : SourceResult
  }

  /** Liest ein Rezept von einer Webseite (App-seitig, ADR-0012). */
  interface RecipeSource { suspend fun read(url: String): SourceResult }

  sealed interface RecipeImportResult {
      data class Success(val input: RecipeInput) : RecipeImportResult
      data class Failure(val error: RecipeImportError) : RecipeImportResult
  }

  /** Text/Link → Formularwerte für den Rezept-Editor; gespeichert wird erst dort (ADR-0012). */
  class ImportRecipeUseCase(private val source: RecipeSource) {
      suspend operator fun invoke(text: String): RecipeImportResult
  }
  ```
  Ablauf: `extractRecipeUrl(text) ?: return Failure(NO_LINK)` → `source.read(url)` → `Unreachable` → `UNREACHABLE`, `NotFound` → `NO_RECIPE`,
  `Found` → `Success(recipe.toInput(url))` mit:
  - `name = recipe.name.trim().take(PlanLimits.NAME_MAX)`
  - `servings = (recipe.servings ?: RecipeLimits.SERVINGS_DEFAULT).coerceIn(SERVINGS_MIN, SERVINGS_MAX)`
  - `ingredientsText = ingredientsToText(recipe.ingredientLines.map(::parseIngredientLine).map { it.copy(name = stripStoreBrand(it.name)) })`
  - `stepsText = recipe.steps.joinToString("\n")`
  - `sourceUrl = url` (geladener Link; REWE-Recipe hat kein `url`)
- **GOTCHA**: Marken erst **nach** dem Zerlegen am `name` entfernen (sonst würde „1.0 Dose(n) REWE …“ falsch zerlegt). „Saft von 1 Limette“ bleibt ganz Name.
  Rundtrip: `ingredientsToText` → beim Speichern `ingredientsFromText` ergibt wieder dieselben Zutaten (5b-Garantie).
- **VALIDATE** (Tests mit `FakeRecipeSource`):
  - Carbonara → `ingredientsText` exakt:
    ```
    500 g Vollkornspaghetti
    Salz
    2 Stange(n) Lauch
    2 Zehe(n) Knoblauch
    70 g Optional: Bio-Bacon
    2 EL Rapsöl
    200 g Sahne
    2 Eier
    Pfeffer
    Muskatnuss
    50 g Parmesan
    ```
    `servings = 4`, `sourceUrl` = geteilter Link, Name unverändert.
  - Quesadilla → u. a. Zeilen „2 EL Rapsöl“, „1 Dose(n) Kidney-Bohnen“, „Saft von 1 Limette“, „4 Tortilla-Wraps“, „200 g Gouda gerieben“, „1 Zucchini“.
  - Text ohne Link → `NO_LINK`, Source nicht aufgerufen; `Unreachable` → `UNREACHABLE`; `NotFound` → `NO_RECIPE`.
  - `servings` `null` → 4; 50 → 20; 0 → 1; Name mit 70 Zeichen → 60.
  - `ingredientsFromText(result.input.ingredientsText)` enthält `Ingredient("Vollkornspaghetti", 500.0, "g")` und `Ingredient("Salz")`.

### Task 5: Web-Quelle (`data/recipe/WebRecipeSource.kt`)
- **ACTION**: CREATE + `data/WebRecipeSourceTest.kt` (mit `FakePageScriptLoader`).
- **IMPLEMENT**:
  ```kotlin
  /** Lädt eine Seite und liefert die Texte aller `application/ld+json`-Skripte; `null` = Seite nicht erreichbar. */
  interface PageScriptLoader { suspend fun ldJsonScripts(url: String): List<String>? }

  class WebRecipeSource(private val loader: PageScriptLoader) : RecipeSource {
      override suspend fun read(url: String): SourceResult {
          val blocks = loader.ldJsonScripts(url) ?: return SourceResult.Unreachable
          return readJsonLdRecipe(blocks)?.let(SourceResult::Found) ?: SourceResult.NotFound
      }
  }
  ```
- **VALIDATE**: `null` → `Unreachable`; `emptyList()` (z. B. Captcha-Seite) → `NotFound`; REWE-Blöcke → `Found(carbonara)`; Loader bekam genau den URL.

### Task 6: WebView-Lader (`data/recipe/WebViewPageScriptLoader.kt`)
- **ACTION**: CREATE (ohne Unit-Test; per Kover ausgeschlossen, auf dem Gerät abgenommen). So schmal wie möglich halten — alle Logik steckt in `decodeScriptList`/`readJsonLdRecipe`.
- **IMPLEMENT**:
  ```kotlin
  private const val PAGE_TIMEOUT_MS = 20_000L
  private const val LD_JSON_SCRIPTS =
      "(function(){return Array.from(document.querySelectorAll('script[type=\"application/ld+json\"]'))" +
          ".map(function(s){return s.textContent;});})()"

  /** Unsichtbare WebView (ADR-0012): ein echter Browser lädt die Seite, wir lesen nur die schema.org-Daten. */
  class WebViewPageScriptLoader(private val context: Context) : PageScriptLoader {
      override suspend fun ldJsonScripts(url: String): List<String>? =
          withContext(Dispatchers.Main) {
              val webView = createWebView()
              try {
                  withTimeoutOrNull(PAGE_TIMEOUT_MS) { awaitScripts(webView, url) }
              } finally {
                  webView.stopLoading()
                  webView.destroy()
              }
          }

      @SuppressLint("SetJavaScriptEnabled") // nötig für evaluateJavascript; keine JS-Bridge, kein Dateizugriff
      private fun createWebView() = WebView(context).apply {
          settings.javaScriptEnabled = true
          settings.allowFileAccess = false
          settings.allowContentAccess = false
          settings.blockNetworkImage = true
      }

      private suspend fun awaitScripts(webView: WebView, url: String): List<String>? =
          suspendCancellableCoroutine { cont ->
              webView.webViewClient = object : WebViewClient() {
                  override fun onPageFinished(view: WebView, finishedUrl: String?) {
                      if (!cont.isActive) return
                      view.evaluateJavascript(LD_JSON_SCRIPTS) { raw -> if (cont.isActive) cont.resume(decodeScriptList(raw)) }
                  }
                  override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                      if (request.isForMainFrame && cont.isActive) cont.resume(null)
                  }
              }
              webView.loadUrl(url)
          }
  }
  ```
  Imports: `android.annotation.SuppressLint`, `android.content.Context`, `android.webkit.*` (einzeln), `kotlinx.coroutines.Dispatchers`, `withContext`, `withTimeoutOrNull`,
  `suspendCancellableCoroutine`, `kotlin.coroutines.resume`.
- **GOTCHA**:
  - WebView nur auf dem Main-Thread erzeugen/benutzen; `destroy()` im `finally` (läuft dort auf Main).
  - **Kein** `addJavascriptInterface`, **kein** User-Agent-Ändern (ADR: Bot-Abwehr nicht umgehen).
  - `onPageFinished` kann nach `onReceivedError` noch kommen → immer `cont.isActive` prüfen.
  - Zeitüberschreitung (`null`) zählt als „nicht erreichbar“.
  - `kotlin.coroutines.resume` — bei Coroutines 1.11 ggf. Deprecation-Hinweis zu `resume(value, onCancellation)`; die Ein-Parameter-Variante aus `kotlin.coroutines` ist korrekt.
  - detekt: Datei < 15 Funktionen; anonyme Klasse ok. `MagicNumber` über `const val`.
- **VALIDATE**: kompiliert; `lintDebug` ohne neue Fehler (`SetJavaScriptEnabled` unterdrückt mit Begründung). Funktion: Geräte-Abnahme (Task 13).

### Task 7: Vorgemerkter geteilter Text (`SharedRecipeInbox.kt`)
- **ACTION**: CREATE in Paket `de.familyplanner.android.feature.meals` + `SharedRecipeInboxTest.kt`.
- **IMPLEMENT**:
  ```kotlin
  /** Geteilter Text (Teilen-Menü) bis der angemeldete Hauptbereich ihn abholt; abgemeldet bleibt er liegen. */
  class SharedRecipeInbox {
      private val _pending = MutableStateFlow<String?>(null)
      val pending: StateFlow<String?> = _pending.asStateFlow()
      fun offer(text: String?) { if (!text.isNullOrBlank()) _pending.value = text.take(SHARED_TEXT_MAX) }
      fun consume() { _pending.value = null }
  }
  private const val SHARED_TEXT_MAX = 2_000
  ```
- **VALIDATE**: `offer("x")` → pending „x“; `offer(null)`/`offer("  ")` → unverändert; `consume()` → `null`; sehr langer Text wird gekürzt.

### Task 8: Editor-ViewModel (`RecipeArgs.kt`, `RecipeEditViewModel.kt`)
- **ACTION**: UPDATE + `ui/RecipeImportViewModelTest.kt`.
- **IMPLEMENT**:
  - `RecipeEditArgs(val recipeId: String? = null, val importText: String? = null)` (KDoc: „[importText] geteilter Text/Link: Import startet sofort (nur neu, nur Eltern)“).
  - Konstruktor: `RecipeEditViewModel(args, sessions, recipes: RecipeUseCases, importRecipe: ImportRecipeUseCase)`.
  - State: `val isImporting: Boolean = false`, `val importError: RecipeImportError? = null`; `canSave` zusätzlich `&& !isImporting`;
    `val canImport: Boolean get() = isNew && !isLoading && loadError == null && !isBusy && !isImporting && sourceUrl.isNotBlank()`.
  - Event: `data object Import : RecipeEditEvent`.
  - `init`: nach `load()`: `if (isParent && args.recipeId == null && args.importText != null) startImport(args.importText)`.
  - `onEvent(Import)` → `if (_state.value.canImport) startImport(_state.value.sourceUrl)`.
  - `SourceUrlChanged` setzt zusätzlich `importError = null`.
  - `startImport(text)`: `_state.update { it.copy(isImporting = true, importError = null, error = null, sourceUrl = extractRecipeUrl(text) ?: it.sourceUrl) }`, dann
    `viewModelScope.launch { when (val r = importRecipe(text)) { Success → _state.update { it.withImport(r.input) }; Failure → _state.update { it.copy(isImporting = false, importError = r.error) } } }`.
  - `withImport(input)`: `copy(isImporting = false, name = input.name, servings = input.servings, ingredientsText = input.ingredientsText, stepsText = input.stepsText, sourceUrl = input.sourceUrl)`.
- **MIRROR**: UI-ZUSTAND/EVENTS, `withRecipe(...)`.
- **GOTCHA**:
  - Kinder: `load()` setzt bereits `loadError = Forbidden` → **kein** Import, Source wird nie aufgerufen.
  - Bestehendes Rezept (`recipeId != null`): `importText` ignorieren.
  - Doppelter Tipp auf „Übernehmen“: `canImport` ist während `isImporting` false.
  - detekt `CyclomaticComplexMethod` in `onEvent`: Bei Bedarf Import-Events wie in 5b (`onRecipe(event)`) auslagern.
  - Bestehende Tests/Aufrufer von `RecipeEditViewModel(...)` (RecipeViewModelsTest `editViewModel`, MealsModule) um den 4. Parameter ergänzen.
- **VALIDATE** (Tests, `FakeRecipeSource` mit optionalem `CompletableDeferred`-Gate):
  - `REQ-MEAL-061 shared link fills the editor` — Args `importText = "Lauch-Carbonara https://www.rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/"` → Name, `servings = 4`, Zutaten-Text (11 Zeilen, s. Task 4), Schritte, `sourceUrl`; `recipes.created` leer.
  - `… save after import stores it like a manual recipe` — danach `Save` → `FakeRecipeRepository.created.single()` mit `Ingredient("Vollkornspaghetti", 500.0, "g")`, `Ingredient("Salz")`, `sourceUrl`.
  - `… corrected ingredients are saved` — nach Import `IngredientsChanged(...)` + Save → korrigierte Zutaten.
  - `… pasted link imports on demand` — `SourceUrlChanged(link)` + `Import`.
  - `… while importing saving and importing again are disabled` (Gate).
  - `… page without recipe keeps the form and reports it` — vorher `NameChanged("Curry")`, Import → `NO_RECIPE`, Name bleibt „Curry“.
  - `… unreachable page is reported`; `… shared text without link is reported` (`NO_LINK`, Source nicht gerufen).
  - `… children cannot import` — `childSession` → `loadError == Forbidden`, Source-Aufrufe 0.
  - `… existing recipe ignores import text`.
  - `… editing the link clears the import error`.

### Task 9: Editor-Screen + Texte
- **ACTION**: UPDATE `RecipeEditScreen.kt`, `strings.xml`; CREATE `ui/RecipeImportScreenTest.kt`.
- **IMPLEMENT**:
  - In `RecipeEditScreen` innerhalb `else if (!state.isLoading)`: wenn `state.isNew` zuerst `ImportSection(state, onEvent)`, dann `RecipeFields(...)`.
  - `RecipeFields`: das Quelle-Feld nur `if (!state.isNew)` (bei „neu“ steht es oben in der ImportSection).
  - `ImportSection`: `OutlinedTextField(value = state.sourceUrl, onValueChange = { SourceUrlChanged(it) }, label = recipe_import_link_label, singleLine, KeyboardType.Uri, enabled = !state.isImporting)`;
    `Row { OutlinedButton(onClick = { onEvent(Import) }, enabled = state.canImport) { Text(recipe_import) } ; if (isImporting) { CircularProgressIndicator(Modifier.size(20.dp)); Text(recipe_import_running) } }`;
    `state.importError?.let { Text(stringResource(it.messageRes()), color = MaterialTheme.colorScheme.error) }`.
  - `private fun RecipeImportError.messageRes(): Int = when (this) { NO_LINK -> R.string.recipe_import_no_link; UNREACHABLE -> …_unreachable; NO_RECIPE -> …_no_recipe }` (in der Screen-Datei).
  - Strings (deutsch, Duzen wie bestehende Texte):
    ```xml
    <string name="recipe_import_link_label">Link zum Rezept (optional, z. B. von rewe.de)</string>
    <string name="recipe_import">Übernehmen</string>
    <string name="recipe_import_running">Rezept wird gelesen …</string>
    <string name="recipe_import_no_link">Kein Link gefunden. Füge einen Link zu einem Rezept ein oder lege es unten von Hand an.</string>
    <string name="recipe_import_unreachable">Die Seite ließ sich nicht laden. Prüfe die Internetverbindung oder lege das Rezept unten von Hand an.</string>
    <string name="recipe_import_no_recipe">Auf dieser Seite wurde kein Rezept gefunden. Du kannst es unten von Hand anlegen.</string>
    ```
- **GOTCHA**: `CircularProgressIndicator` ist in material3 enthalten. `SubmitButton` hat schon ein eigenes Lade-Verhalten — nicht für den Import missbrauchen. Screen-Tests: Text kann doppelt vorkommen → `onAllNodesWithText`/`performScrollTo` wie in `RecipeScreensTest`.
- **VALIDATE** (Robolectric): neues Rezept zeigt Link-Feld + „Übernehmen“ (deaktiviert ohne Link, aktiv mit Link, Klick → `Import`-Event);
  bestehendes Rezept zeigt kein „Übernehmen“, aber „Quelle (Link, optional)“; `importError = NO_RECIPE` → Meldung sichtbar; `isImporting` → „Rezept wird gelesen …“ und „Speichern“ deaktiviert.

### Task 10: Navigation, Koin, Teilen-Ziel
- **ACTION**: UPDATE `MealsNavigation.kt`, `MealsModule.kt`, `MainActivity.kt`, `AppNavHost.kt`, `AndroidManifest.xml` (app); Tests `MealsModuleTest`, `AppNavHostTest`.
- **IMPLEMENT**:
  - `MealsNavigation.kt`:
    ```kotlin
    /** [recipeId] `null` = neues Rezept; [importText] geteilter Text/Link für den Import (ADR-0012). */
    @Serializable
    data class RecipeEditRoute(val recipeId: String? = null, val importText: String? = null)

    /** Öffnet den Editor „Neues Rezept“ mit Import (Teilen-Menü). */
    fun NavHostController.openRecipeImport(text: String) = navigate(RecipeEditRoute(importText = text))
    ```
    `composable<RecipeEditRoute>`: `val route = entry.toRoute<RecipeEditRoute>()`; `RecipeEditArgs(route.recipeId, route.importText)`.
  - `MealsModule.kt` (Abschnitt `// recipe`):
    ```kotlin
    single { SharedRecipeInbox() }
    single<PageScriptLoader> { WebViewPageScriptLoader(androidContext()) }
    single<RecipeSource> { WebRecipeSource(get()) }
    factory { ImportRecipeUseCase(get()) }
    viewModel { params -> RecipeEditViewModel(params.get(), get(), get(), get()) }
    ```
  - `AndroidManifest.xml` (app), in `<activity .MainActivity>` zusätzlich:
    ```xml
    <!-- ADR-0012: Rezept-Link aus Browser/REWE-App teilen. -->
    <intent-filter>
        <action android:name="android.intent.action.SEND" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:mimeType="text/plain" />
    </intent-filter>
    ```
  - `MainActivity.onCreate`: vor `setContent`: `if (savedInstanceState == null) receiveShare(intent)`;
    `private fun receiveShare(intent: Intent?) { if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") get<SharedRecipeInbox>().offer(intent.getStringExtra(Intent.EXTRA_TEXT)) }`
    (`org.koin.android.ext.android.get`). Kein `onNewIntent` (siehe NOT Building).
  - `AppNavHost.kt`: `MainHost(inbox: SharedRecipeInbox = koinInject())`:
    ```kotlin
    val shared by inbox.pending.collectAsStateWithLifecycle()
    LaunchedEffect(shared) {
        shared?.let { text ->
            inbox.consume()
            navController.openRecipeImport(text)
        }
    }
    ```
    (Einfügen nach `rememberNavController()`, vor/nach `NavHost` — `LaunchedEffect` läuft nach der ersten Komposition, der Graph ist dann gesetzt.)
- **GOTCHA**:
  - `MealsModuleTest`: `PageScriptLoader` braucht `androidContext()` → in `testOverrides` `single<PageScriptLoader> { FakePageScriptLoader() }` ergänzen; zusätzlich `koin.get<RecipeEditViewModel> { parametersOf(RecipeEditArgs(importText = "https://www.rewe.de/rezepte/x/")) }` und `koin.get<SharedRecipeInbox>()` prüfen.
  - `AppNavHostTest`: Sobald ein Test angemeldet ist, komponiert `MainHost` und braucht `SharedRecipeInbox` aus Koin → im Testmodul `single { SharedRecipeInbox() }` ergänzen.
    Neuer Test (angemeldet als Elternteil): `inbox.offer("https://www.rewe.de/rezepte/x/")` vor `setContent` → Editor-Titel „Neues Rezept“ sichtbar und `inbox.pending.value == null`.
    Dafür braucht der Test die Editor-Abhängigkeiten im Koin (`RecipeEditViewModel` mit Fake-Use-Cases) — Fakes aus `feature:meals`-Tests sind im `app`-Testclasspath **nicht** verfügbar: lokale Minimal-Fakes (`object : RecipeRepository`, `object : RecipeSource`) im App-Test anlegen. Wird das zu sperrig, stattdessen nur prüfen, dass `pending` nach dem Komponieren `null` ist, und die Navigation über die Abnahme abdecken (im Report vermerken).
  - Typsichere Route: `importText` mit Leerzeichen/Sonderzeichen wird von Navigation URL-kodiert — kein eigenes Encoding.
  - Kinder teilen: Editor zeigt „Nur für Eltern.“ (vorhandener `Forbidden`-Pfad).
- **VALIDATE**: `MealsModuleTest`, `AppNavHostTest` grün; `lintDebug` ohne neue Fehler (Intent-Filter mit `exported="true"` existiert schon).

### Task 11: Version und Kover
- **ACTION**: UPDATE `android/app/build.gradle.kts`.
- **IMPLEMENT**: `versionCode = 6`, `versionName = "0.5.1"`; in `kover.reports.filters.excludes.classes(...)` ergänzen: `"*.WebViewPageScriptLoader*",` mit Kommentar-Ergänzung „WebView (nur auf dem Gerät prüfbar, ADR-0012)“.
- **VALIDATE**: `./gradlew koverVerify` ≥ 80 %.

### Task 12: Qualität
- **ACTION**: Vollständige CI-Kette lokal.
- **VALIDATE**: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` → BUILD SUCCESSFUL; `git diff origin/main -- api/ backend/` leer.

### Task 13: Doku, Traceability, Abnahme-Checkliste, PRD
- **ACTION**: UPDATE Doku.
- **IMPLEMENT**:
  - `docs/03-design/meals.md`: neuer Abschnitt „## App-Seite (Phase 5c)“ nach 5b: Einstiege (Teilen-Ziel `ACTION_SEND text/plain`, Link-Feld im Editor „Neues Rezept“), Ablauf (WebView → JSON-LD → `readJsonLdRecipe` → Zerleger → `stripStoreBrand` → Editor), Regeln
    (erstes `Recipe` mit Name und Zutaten; Portionen aus `recipeYield`, sonst 4, begrenzt 1–20; Quelle = geladener Link, weil REWE kein `url` liefert; Import überschreibt Name/Portionen/Zutaten/Schritte; Fehler NO_LINK/UNREACHABLE/NO_RECIPE lassen das Formular unverändert;
    nur Eltern; abgemeldet geteilte Links warten bis zur Anmeldung; 20 s Zeitlimit; kein UA-Ändern), Testfälle. In 5b-Abschnitt Satz „Der REWE-Import folgt in Phase 5c …“ und „Markenbereinigung gehört nicht dazu (Phase 5c).“ belassen oder auf den neuen Abschnitt verweisen.
  - `docs/04-verification/traceability.md` Zeile REQ-MEAL-061: Design `[meals](../03-design/meals.md)`, ADR-0012; Unit: `StoreBrandsTest`, `RecipeLinkTest`, `ImportRecipeUseCaseTest`, `JsonLdRecipeReaderTest`, `RecipeImportViewModelTest`, `RecipeImportScreenTest`, `SharedRecipeInboxTest`;
    Integration: `WebRecipeSourceTest`, `AppNavHostTest`; Abnahme: manuell Phase 5c ([android/README.md](../../android/README.md)); Status „umgesetzt (App), Geräte-Abnahme offen“. Zeile REQ-MEAL-060 Status-Text „(Import in 5c)“ anpassen.
  - `android/README.md`: „## Abnahme-Checkliste Phase 5c (manuell, echtes Gerät mit Internet)“:
    - [ ] Release-APK 0.5.1 per `adb install -r` auf Eltern- und Kinderhandy
    - [ ] Elternhandy, Chrome: `rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/` → Teilen → Family Planner → Editor mit Name, 4 Portionen, 11 Zutaten („500 g Vollkornspaghetti“, „2 Stange(n) Lauch“, „Salz“ ohne Menge), Schritten und Quelle
    - [ ] Zurück ohne Speichern → Rezept ist nicht in der Liste
    - [ ] Erneut teilen, eine Zutat korrigieren, Speichern → Rezept in der Liste, Ansicht zeigt die Korrektur
    - [ ] „Neues Rezept“ → Link `rewe.de/rezepte/bunte-gemuese-quesadilla/` einfügen → „Übernehmen“ → „2 EL Rapsöl“, „1 Dose(n) Kidney-Bohnen“, „200 g Gouda gerieben“, „Saft von 1 Limette“ als Name; speichern
    - [ ] Teilen aus der REWE-App (falls installiert) funktioniert ebenso
    - [ ] Link zu einer Seite ohne Rezept (z. B. `https://www.rewe.de/`) → „Auf dieser Seite wurde kein Rezept gefunden …“, Formular nutzbar
    - [ ] Flugmodus + WLAN aus → „Die Seite ließ sich nicht laden …“
    - [ ] Kinderhandy: Link teilen → „Nur für Eltern.“
    - [ ] Abgemeldetes Gerät (falls verfügbar) / sonst entfällt: Link teilen → nach Anmeldung öffnet der Editor
    - [ ] Erfolgssignal Phase 5: Kind reicht eines der importierten Rezepte mit umgerechneten Mengen ein
  - `docs/01-requirements/meals.prd.md` Phase-5-Zeile: „5c: offen“ → „5c: `.claude/PRPs/plans/rezepte-import.plan.md`“ (beim Planen bereits gesetzt); nach der Umsetzung Plan nach `completed/` verschieben, Report-Pfad ergänzen; Status `complete` erst nach Geräte-Abnahme.
  - Report: `.claude/PRPs/reports/rezepte-import-report.md` (Format wie `rezepte-app-report.md`).
- **VALIDATE**: Links in den Markdown-Dateien zeigen auf existierende Dateien.

---

## Testing Strategy

### Unit Tests

| Test | Input | Expected Output | Edge Case? |
|---|---|---|---|
| StoreBrandsTest | „REWE Beste Wahl Kidney-Bohnen“ | „Kidney-Bohnen“ | längster Treffer |
| StoreBrandsTest | „REWE“ / „REWE Bio“ | unverändert | nichts bleibt übrig |
| StoreBrandsTest | „Optional: Bio-Bacon“, „REWEnudeln“ | unverändert | kein ganzes Wort |
| RecipeLinkTest | „Titel https://…/x/.“ | „https://…/x/“ | Satzzeichen |
| RecipeLinkTest | „rewe.de/rezepte“ | `null` | ohne Schema |
| JsonLdRecipeReaderTest | REWE-ähnliche 5 Blöcke | Carbonara, 11 Zeilen, 4 | mehrere Recipe-Typen |
| JsonLdRecipeReaderTest | `@graph`, Array-`@type`, HowToSection | Rezept | Varianten |
| JsonLdRecipeReaderTest | kaputter Block + gültiger | gültiger | ungültiges JSON |
| JsonLdRecipeReaderTest | nur Organization | `null` | kein Rezept |
| ImportRecipeUseCaseTest | Carbonara-Rohdaten | exakter Zutaten-Text | `500.0` → `500` |
| ImportRecipeUseCaseTest | Quesadilla-Rohdaten | Marken entfernt | „Saft von 1 Limette“ |
| ImportRecipeUseCaseTest | yield null/50/0 | 4/20/1 | Grenzen |
| WebRecipeSourceTest | Loader `null`/leer/REWE | Unreachable/NotFound/Found | Captcha-Seite |
| RecipeImportViewModelTest | geteilter Link | Formular gefüllt, nichts gespeichert | – |
| RecipeImportViewModelTest | Kind | Forbidden, Source 0× | Rolle |
| RecipeImportViewModelTest | Gate offen | `canSave`/`canImport` false | Doppelklick |
| RecipeImportScreenTest | neu vs. bestehend | Link-Zeile nur bei neu | – |
| SharedRecipeInboxTest | offer/consume | pending | blank/lang |
| AppNavHostTest | Inbox vorbelegt, Eltern angemeldet | Editor offen, Inbox leer | – |

### Edge Cases Checklist
- [x] Leerer/geteilter Text ohne Link → NO_LINK
- [x] Sehr langer geteilter Text → gekürzt (Inbox)
- [x] Ungültiges JSON-LD → übersprungen
- [x] Mehrere `Recipe`-Blöcke → das mit Zutaten
- [x] Netzwerkfehler/Zeitüberschreitung → UNREACHABLE
- [x] Kind (Permission) → Forbidden, kein Laden
- [x] Doppelklick „Übernehmen“ → gesperrt
- [ ] > 30 Zutaten / Schritt > 1000 Zeichen → Import füllt trotzdem, Validierung meldet beim Speichern (bestehendes Verhalten, kein eigener Test nötig)

---

## Validation Commands

### Static Analysis
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug
```
EXPECT: keine Fehler

### Unit Tests
```bash
cd android && ./gradlew :feature:meals:testDebugUnitTest :app:testDebugUnitTest
```
EXPECT: alle grün

### Full Test Suite + Coverage + Build
```bash
cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug
```
EXPECT: BUILD SUCCESSFUL

### Contract unverändert
```bash
git diff origin/main --stat -- api/ backend/
```
EXPECT: leer

### Manual Validation
Siehe „Abnahme-Checkliste Phase 5c“ (Task 13) — nur auf echtem Gerät prüfbar (WebView, Teilen-Menü, REWE-Bot-Abwehr).

---

## Acceptance Criteria
- [ ] Alle Tasks erledigt
- [ ] Validierungsbefehle grün
- [ ] Tests mit `REQ-MEAL-061` im Namen geschrieben und grün
- [ ] Keine Änderung an `api/`, `backend/`
- [ ] UX wie oben (Link-Zeile nur bei „Neues Rezept“, Meldungen)
- [ ] Doku/Traceability/Checkliste aktualisiert

## Completion Checklist
- [ ] Muster aus 5b übernommen (State/Event, Koin, Routen, Tests)
- [ ] Fehler als Zustand, kein Logging eingeführt
- [ ] Keine hartkodierten Grenzen (Konstanten/`RecipeLimits`/`PlanLimits`)
- [ ] Kein UA-Spoofing, keine JS-Bridge, Dateizugriff aus
- [ ] Self-contained — keine Rückfragen nötig

## Risks
| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| REWE blockt die WebView (Bot-Abwehr erkennt „; wv“ im User-Agent) | M | H | Abnahme auf dem Gerät zuerst mit dem Carbonara-Link; dann „kein Rezept gefunden“ + manuelles Anlegen (AC erfüllt). Nicht umgehen (ADR-0012); Befund im Report/ADR festhalten |
| JSON-LD erst nach `onPageFinished` per JS eingefügt | L | M | Auf REWE steht `recipe-schema` im ausgelieferten HTML. Falls nicht: im Lader nach leerem Ergebnis einmal kurz (z. B. 1,5 s) erneut auswerten — nur bei Befund |
| Teilen startet neue Activity in fremdem Task; zwei Instanzen | M | L | Funktional egal (Koin-Singletons geteilt); kein `launchMode`-Wechsel |
| `onPageFinished` mehrfach / nach Fehler | M | L | `cont.isActive`-Prüfung |
| Kover < 80 % durch WebView-Klasse | M | M | Ausschluss in `app/build.gradle.kts` |
| `AppNavHostTest` wird durch Editor-Abhängigkeiten sperrig | M | L | Rückfall in Task 10 beschrieben |
| Importierter Name > 60 Zeichen oder > 30 Zutaten | L | L | Name gekürzt; Zutaten meldet die bestehende Validierung |

## Notes
- Datenschutz/Rechte (ADR-0012): nur privater Familiengebrauch; Quelle wird gespeichert; keine Bilder.
- Kommunikation in der App ausschließlich mit `planner.home.arpa`; die WebView ist der **einzige** Internetzugriff und nur durch einen Elternteil ausgelöst.
  `network_security_config.xml` muss dafür nicht geändert werden (`base-config` = System-CAs, kein Cleartext).
- Umsetzung laut CLAUDE.md in einer **neuen Session** (Sonnet): `/ecc:prp-implement .claude/PRPs/plans/rezepte-import.plan.md`.
