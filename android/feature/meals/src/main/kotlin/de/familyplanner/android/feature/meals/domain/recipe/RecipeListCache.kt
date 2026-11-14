package de.familyplanner.android.feature.meals.domain.recipe

import de.familyplanner.android.core.data.session.SessionStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Zuletzt geladene Rezeptliste, nur im Arbeitsspeicher (ADR-0018): Die Liste erscheint beim Öffnen sofort und wird
 * im Hintergrund neu geladen. Sie gehört dem angemeldeten Mitglied; passt sie nicht zur Session, gilt sie als leer.
 */
class RecipeListCache(
    private val sessions: SessionStore,
) {
    private data class Entry(
        val memberId: String,
        val recipes: List<RecipeSummary>,
    )

    /** [changes] zählt eigene Änderungen (Speichern, Löschen); eine ältere Server-Antwort darf sie nicht überschreiben. */
    private data class State(
        val entry: Entry? = null,
        val changes: Long = 0,
    )

    /** Stand beim Absenden einer Anfrage: für wen geladen wird und nach welcher eigenen Änderung. */
    class Ticket internal constructor(
        internal val memberId: String?,
        internal val changes: Long,
    )

    private val state = AtomicReference(State())

    fun read(): List<RecipeSummary>? {
        val memberId = sessions.current?.memberId ?: return null
        return state
            .get()
            .entry
            ?.takeIf { it.memberId == memberId }
            ?.recipes
    }

    /** Vor der Anfrage holen und an [write] übergeben. */
    fun ticket(): Ticket = Ticket(sessions.current?.memberId, state.get().changes)

    /**
     * Speichert die Antwort für das Mitglied, für das geladen wurde. Gab es seit dem [ticket] eine eigene Änderung,
     * wird die (ältere) Antwort verworfen; die nächste Anfrage holt den neuen Stand. Ohne Mitglied wird vergessen.
     */
    fun write(
        ticket: Ticket,
        recipes: List<RecipeSummary>,
    ) {
        state.updateAndGet { current ->
            if (current.changes != ticket.changes) current else current.copy(entry = ticket.memberId?.let { Entry(it, recipes) })
        }
    }

    /** Ändert die gespeicherte Liste des angemeldeten Mitglieds; ohne passende Liste geschieht nichts. */
    fun update(change: (List<RecipeSummary>) -> List<RecipeSummary>) {
        val memberId = sessions.current?.memberId ?: return
        state.updateAndGet { current ->
            val entry = current.entry?.takeIf { it.memberId == memberId } ?: return@updateAndGet current
            State(entry.copy(recipes = change(entry.recipes)), current.changes + 1)
        }
    }
}

/** Ersetzt den Eintrag mit derselben ID an seiner Stelle oder hängt ihn an; die nächste Server-Antwort ordnet neu. */
internal fun List<RecipeSummary>.upsert(summary: RecipeSummary): List<RecipeSummary> =
    if (any { it.id == summary.id }) map { if (it.id == summary.id) summary else it } else this + summary
