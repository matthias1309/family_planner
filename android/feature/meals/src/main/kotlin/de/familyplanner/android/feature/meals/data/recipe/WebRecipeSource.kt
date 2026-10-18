package de.familyplanner.android.feature.meals.data.recipe

import de.familyplanner.android.feature.meals.domain.recipe.RecipeSource
import de.familyplanner.android.feature.meals.domain.recipe.SourceResult
import kotlin.coroutines.cancellation.CancellationException

/** Lädt eine Seite und liefert die Texte aller `application/ld+json`-Skripte; `null` = Seite nicht erreichbar. */
interface PageScriptLoader {
    suspend fun ldJsonScripts(url: String): List<String>?
}

class WebRecipeSource(
    private val loader: PageScriptLoader,
) : RecipeSource {
    override suspend fun read(url: String): SourceResult {
        val blocks = loadScripts(url) ?: return SourceResult.Unreachable
        return readJsonLdRecipe(blocks)?.let(SourceResult::Found) ?: SourceResult.NotFound
    }

    // Fehlt die System-WebView (oder wird sie aktualisiert), wirft schon der Konstruktor:
    // als „nicht erreichbar“ melden statt abzustürzen.
    @Suppress("TooGenericExceptionCaught")
    private suspend fun loadScripts(url: String): List<String>? =
        try {
            loader.ldJsonScripts(url)
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("SwallowedException") e: RuntimeException,
        ) {
            null
        }
}
