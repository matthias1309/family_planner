# Implementation Report: REWE-Rezept-Import in der App (Phase 5c)

## Summary
Rezepte von rewe.de lassen sich über das Android-Teilen-Menü oder ein Link-Feld im Editor „Neues Rezept“ übernehmen (REQ-MEAL-061, ADR-0012).
Eine unsichtbare WebView lädt die Seite, `readJsonLdRecipe` liest das schema.org-`Recipe`, `parseIngredientLine` und `stripStoreBrand` bereiten die
Zutaten auf, der Editor wird vorbefüllt. `api/` und `backend/` sind unverändert (`git diff origin/main -- api/ backend/` leer). Version 0.5.1 (versionCode 6).

## Assessment vs Reality

| Metric | Predicted (Plan) | Actual |
|---|---|---|
| Complexity | Large | Large |
| Confidence | – | hoch für Logik; WebView/Teilen-Menü/REWE-Bot-Abwehr nur auf dem Gerät prüfbar |
| Files Changed | ≈ 22 | 16 neue Dateien (7 Main, 9 Test), 16 geänderte Dateien (`git status`) |

## Tasks Completed

| # | Task | Status | Notes |
|---|---|---|---|
| 0 | Ausgangslage | Done | Branch `feat/rezepte-import`, Baseline grün |
| 1 | `StoreBrands` | Done | |
| 2 | `RecipeLink`, `isHttpUrl` internal | Done | |
| 3 | `JsonLdRecipeReader` | Done | |
| 4 | `RecipeImport` (Domäne) | Done | |
| 5 | `WebRecipeSource` | Done | |
| 6 | `WebViewPageScriptLoader` | Done | kein Unit-Test, Kover-Ausschluss |
| 7 | `SharedRecipeInbox` | Done | |
| 8 | ViewModel | Done | |
| 9 | Screen, Texte | Done | |
| 10 | Navigation, Koin, Manifest, `MainHost` | Done | `AppNavHostTest` mit lokalen Minimal-Fakes |
| 11 | Version, Kover | Done | |
| 12 | Qualitätskette | Done | |
| 13 | Doku, Traceability, Checkliste, PRD | Done | |

## Validation Results

| Level | Status | Notes |
|---|---|---|
| Static Analysis | Pass | `ktlintCheck detekt lintDebug` |
| Unit Tests | Pass | `testDebugUnitTest`, neue Tests mit `REQ-MEAL-061` |
| Build | Pass | `assembleDebug`, `koverVerify` ≥ 80 % |
| Integration | N/A | App-seitig; `AppNavHostTest` deckt Teilen → Editor ab |
| Edge Cases | Pass | siehe Plan-Checkliste |

## Deviations from Plan
- Tests `JsonLdRecipeReaderTest`/`WebRecipeSourceTest` liegen im Paket `…meals.data` (Ordner `data/`), wie die übrigen Data-Tests; detekt verlangt Paket = Ordner.
- Bestehender Test „editor shows fields“ prüft für „neu“ jetzt das Import-Link-Label statt „Quelle (Link, optional)“ (Quelle-Feld nur im Bearbeiten-Modus).

## Issues Encountered
- macOS-`sed -i` ohne Suffix schlug fehl; mit `-i ''` wiederholt.
- detekt: Paket-/Ordner-Abweichung und eine zu lange Fixture-Zeile, beides behoben.

## Offen
- Geräte-Abnahme (Checkliste Phase 5c in `android/README.md`), v. a. ob REWE die WebView zulässt. Falls nicht: nicht umgehen (ADR-0012), Befund festhalten.
- Phase-5-Status im PRD bleibt `in-progress` bis zur Abnahme.

## Next Steps
- [ ] Code Review via `/ecc:code-review`
- [ ] Commit und PR nach Freigabe
