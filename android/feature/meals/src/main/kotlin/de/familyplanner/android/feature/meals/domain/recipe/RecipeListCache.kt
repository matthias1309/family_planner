package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.cache.LastLoadedCache

/** Zuletzt geladene Rezeptliste des angemeldeten Mitglieds (ADR-0018); eine Liste, daher ohne Schlüssel. */
class RecipeListCache(
    sessions: SessionStore,
) {
    private val cache = LastLoadedCache<Unit, List<RecipeSummary>>(sessions)

    fun read(): List<RecipeSummary>? = cache.read(Unit)

    fun ticket(): LastLoadedCache.Ticket = cache.ticket()

    fun write(
        ticket: LastLoadedCache.Ticket,
        recipes: List<RecipeSummary>,
    ) = cache.write(ticket, Unit, recipes)

    fun update(change: (List<RecipeSummary>) -> List<RecipeSummary>) = cache.update(Unit, change)
}

/** Ersetzt den Eintrag mit derselben ID an seiner Stelle oder hängt ihn an; die nächste Server-Antwort ordnet neu. */
internal fun List<RecipeSummary>.upsert(summary: RecipeSummary): List<RecipeSummary> =
    if (any { it.id == summary.id }) map { if (it.id == summary.id) summary else it } else this + summary
