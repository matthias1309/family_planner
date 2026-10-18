# Implementation Report: Rezepte in der App (Phase 5b)

## Summary
`feature:meals` hat jetzt die Rezeptsammlung: Liste mit clientseitiger Suche (auch als Auswahlmodus), Rezeptansicht (auch mit den
Mengen eines Gerichts), Rezept-Editor (nur Eltern), Rezeptauswahl im Gericht-Editor mit Portionen-Umrechnung, Zerleger für
Zutatenzeilen und „Rezept ›“ in der Plan-Zeile. Die App sendet Gerichte ab 0.5.0 nur noch mit `structuredIngredients`. `api/` und
`backend/` sind unverändert (`git diff main -- api/ backend/` leer). Version 0.5.0 (versionCode 5).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch; Geräte-Abnahme steht aus |
| Files Changed | ≈ 22 neu, ≈ 20 geändert | ca. 30 neu (Main und Test), 24 geänderte Dateien (`git diff --stat`) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Branch & Ausgangslage | Done | `feat/rezepte-app` von `main`; Plan/PRD-Änderung aus `docs/plan-rezepte-app` als uncommittete Dateien übernommen |
| 1 | Zerleger/Anzeige | Done | wie geplant |
| 2 | Portionen, Validierung | Done | wie geplant |
| 3 | Repository (MockWebServer) | Done | |
| 4 | Use Cases, Fakes | Done | |
| 5 | Plan-Domäne/-Daten | Done | |
| 6 | Rezeptliste | Done | |
| 7 | Ansicht, Editor | Done | `ServingsStepper` mit Textknöpfen „–“/„+“ (siehe Abweichungen) |
| 8 | Gericht-Editor, Plan-Link | Done | Zustandsübergänge in `DishRecipeState.kt`, Koch-Auflösung in `DishCookResolution.kt` |
| 9 | Navigation, Koin, Version | Done | Rezept-Routen in eigenem `recipeGraph` |
| 10 | Qualität & Coverage | Done | CI-Kette grün |
| 11 | Doku, Traceability, PRD | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | ktlintCheck, detekt, lintDebug |
| Unit Tests | Pass | 459 Tests im Modul `feature:meals` (0 Fehler) |
| Build | Pass | `assembleDebug` |
| Coverage | Pass | `koverVerify` |
| Integration | N/A | MockWebServer-Tests Teil der Unit-Tests; kein lokales Backend gestartet |
| Edge Cases | Pass | siehe Tests (Rundung, 0/20000,  404 beim Löschen, Doppelklick) |

Befehl: `cd android && ./gradlew ktlintCheck detekt lintDebug testDebugUnitTest koverVerify assembleDebug` → BUILD SUCCESSFUL.

## Deviations from Plan
- **`ServingsStepper`** nutzt `TextButton` „–“/„+“ statt Icons: `Icons.Default.Remove` ist in `material-icons-core` nicht enthalten
  (der Plan nannte diese Rückfalloption).
- **`DishCookResolution.kt`** (neu): `parentCookId`/`childCookId`/`notCooking` aus `DishEditViewModel` ausgelagert (detekt `TooManyFunctions`).
- **`recipeGraph`** als private Erweiterung in `MealsNavigation.kt` (detekt `LongMethod` bei `mealsGraph`).
- **Rezept-Ereignisse** im `DishEditViewModel` über `onRecipe(event)` gebündelt (detekt `CyclomaticComplexMethod`).
- `RecipeLimits.SERVINGS_DEFAULT = 4` ergänzt (Plan: Konstante `DEFAULT_SERVINGS` im State).
- `RecipeViewModel` lädt das Gericht nur, wenn dessen `recipeId` zum Rezept passt und `servings` gesetzt ist; sonst Rezeptmengen.

## Issues Encountered
- Der Plan lag nur als Commit auf `docs/plan-rezepte-app`; per `git checkout docs/plan-rezepte-app -- <Dateien>` + `git reset` als
  uncommittete Änderungen auf den neuen Branch geholt (kein Commit, nichts gepusht).
- Der Screen-Test für die Lösch-Bestätigung brauchte `hasAnyAncestor(isDialog())`, weil „Löschen“ auf Screen und im Dialog vorkommt.
- Offen/ungeprüft: Warnung „Unnecessary safe call“ in `DishEditViewModel` (Zeile ≈ 267) und Deprecation von `createComposeRule` —
  beides dem bestehenden Stil entsprechend, nicht angefasst.

## Tests Written

| Test File | Area |
|---|---|
| `domain/recipe/IngredientLinesTest.kt` | Zerleger, Anzeigeformat, Rundtrip |
| `domain/recipe/ServingsTest.kt` | Umrechnung, Portionen-Vorschlag |
| `domain/recipe/ValidationTest.kt` | Rezept-Validierung, Schritte, Link |
| `domain/recipe/UseCasesTest.kt` | Suche, Speichern/Aktualisieren/Löschen |
| `data/RemoteRecipeRepositoryTest.kt` | API-Pfade, Mapping, Fehler |
| `data/RemoteMealPlanRepositoryTest.kt` (erweitert) | `recipeId`/`servings`, `structuredIngredients` |
| `domain/plan/UseCasesTest.kt` (erweitert) | Anwesende je Tag, Speichern mit Rezeptbezug |
| `ui/RecipeViewModelsTest.kt` | Liste, Ansicht, Editor, Gericht-Editor mit Rezept |
| `ui/RecipeScreensTest.kt` | Liste, Ansicht, Editor, RecipeSection, Plan-Link |
| `MealsModuleTest.kt` (erweitert) | neue Koin-Bindungen |

## Next Steps
- [ ] `/code-review`
- [ ] Commit (Plan, Report und Code gehören auf `feat/rezepte-app`) und PR via `/prp-pr`
- [ ] Geräte-Abnahme Phase 5b (README-Checkliste), danach Datum in `traceability.md`
- [ ] Phase 5c (REWE-Import) planen
