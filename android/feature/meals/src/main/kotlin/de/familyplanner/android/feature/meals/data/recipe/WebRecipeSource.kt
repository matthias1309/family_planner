package de.familyplanner.android.feature.meals.data.recipe

import de.familyplanner.android.feature.meals.domain.recipe.RecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult

/** Lädt eine Seite und liefert die Texte aller `application/ld+json`-Skripte; `null` = Seite nicht erreichbar. */
interface PageScriptLoader {
    suspend fun ldJsonScripts(url: String): List<String>?
}

class WebRecipeSource(
    private val loader: PageScriptLoader,
) : RecipeSource {
    override suspend fun read(url: String): SourceResult {
        val blocks = loader.ldJsonScripts(url) ?: return SourceResult.Unreachable
        return readJsonLdRecipe(blocks)?.let(SourceResult::Found) ?: SourceResult.NotFound
    }
}
