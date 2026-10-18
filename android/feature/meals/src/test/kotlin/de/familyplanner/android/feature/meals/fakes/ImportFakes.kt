package de.familyplanner.android.feature.meals.fakes

import de.familyplanner.android.feature.meals.data.recipe.PageScriptLoader
import de.familyplanner.android.feature.meals.domain.recipe.RecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.SourceRecipe
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult
import kotlinx.coroutines.CompletableDeferred

const val CARBONARA_URL = "https://www.rewe.de/rezepte/spaghetti-lauch-carbonara-mit-ohne-speck/"
const val QUESADILLA_URL = "https://www.rewe.de/rezepte/bunte-gemuese-quesadilla/"

// Zutatenzeilen sind Fakten (REWE-Seiten, geprüft 2026-10-18); die Schritte sind selbst formuliert.
val carbonaraSource =
    SourceRecipe(
        name = "Spaghetti mit Lauch-Carbonara mit & ohne Speck",
        servings = 4,
        ingredientLines =
            listOf(
                "500.0 g Vollkornspaghetti",
                "Salz",
                "2.0 Stange(n) Lauch",
                "2.0 Zehe(n) Knoblauch",
                "70.0 g Optional: Bio-Bacon",
                "2.0 EL Rapsöl",
                "200.0 g Sahne",
                "2.0 Eier",
                "Pfeffer",
                "Muskatnuss",
                "50.0 g Parmesan",
            ),
        steps = listOf("Nudeln kochen.", "Lauch in Ringe schneiden.", "Alles mischen."),
    )
val quesadillaSource =
    SourceRecipe(
        name = "Bunte Gemüse Quesadilla",
        servings = 4,
        ingredientLines =
            listOf(
                "4.0 Frühlingszwiebeln",
                "200.0 g Champignons",
                "1.0 Zucchini",
                "1.0 Möhre",
                "1.0 Zwiebel",
                "2.0 EL REWE Bio Rapsöl",
                "Salz",
                "Pfeffer",
                "1.0 Dose(n) REWE Beste Wahl Kidney-Bohnen",
                "Saft von 1 Limette",
                "Kreuzkümmel (gemahlen)",
                "4.0 REWE Beste Wahl Tortilla-Wraps",
                "200.0 g ja! Gouda gerieben",
            ),
        steps = listOf("Gemüse braten.", "Wraps füllen."),
    )

/** JSON-LD-Blöcke wie auf rewe.de: Webpage/mainEntity, Organization, zwei Rating-/Review-Recipes, Breadcrumb. */
val reweBlocks: List<String> =
    listOf(
        """{"@context":"https://schema.org","@type":"Webpage","mainEntity":{"@type":"Recipe","name":"Spaghetti mit Lauch-Carbonara mit & ohne Speck",
"recipeYield":"4","recipeIngredient":["500.0 g Vollkornspaghetti","Salz","2.0 Stange(n) Lauch","2.0 Zehe(n) Knoblauch","70.0 g Optional: Bio-Bacon",
"2.0 EL Rapsöl","200.0 g Sahne","2.0 Eier","Pfeffer","Muskatnuss","50.0 g Parmesan"],
"recipeInstructions":[{"@type":"HowToStep","name":"Schritt 1","text":"Nudeln kochen."},{"@type":"HowToStep","name":"Schritt 2","text":"Lauch in Ringe schneiden."},
{"@type":"HowToStep","name":"Schritt 3","text":"Alles mischen."}]}}""",
        """{"@context":"https://schema.org","@type":"Organization","name":"REWE"}""",
        """{"@type":"Recipe","@id":"https://www.rewe.de/rezepte/x/#recipe",
"aggregateRating":{"@type":"AggregateRating","ratingValue":"4.5"}}""",
        """{"@type":"Recipe","@id":"https://www.rewe.de/rezepte/x/#recipe","review":[{"@type":"Review","reviewBody":"Lecker"}]}""",
        """{"@type":"BreadcrumbList","itemListElement":[]}""",
    )

class FakeRecipeSource(
    var result: SourceResult = SourceResult.Found(carbonaraSource),
) : RecipeSource {
    val requested = mutableListOf<String>()

    /** Hält das Lesen zurück (Doppelklick-Test). */
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun read(url: String): SourceResult {
        requested += url
        gate?.await()
        return result
    }
}

class FakePageScriptLoader(
    var scripts: List<String>? = reweBlocks,
) : PageScriptLoader {
    val requested = mutableListOf<String>()

    override suspend fun ldJsonScripts(url: String): List<String>? {
        requested += url
        return scripts
    }
}
