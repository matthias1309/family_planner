package de.familyplanner.android.feature.meals.domain.shopping

import de.familyplanner.android.core.data.session.SessionStore
import de.familyplanner.android.feature.meals.domain.cache.LastLoadedCache
import java.time.LocalDate

/** Höchstens so viele Wochen bleiben gemerkt (wie beim Plan-Cache, ADR-0011). */
private const val KEPT_WEEKS = 8

/** Zuletzt geladene Einkaufsliste je Woche des angemeldeten Mitglieds (ADR-0018). */
class ShoppingListCache(
    sessions: SessionStore,
) {
    private val cache = LastLoadedCache<LocalDate, ShoppingList>(sessions, KEPT_WEEKS)

    fun read(weekStart: LocalDate): ShoppingList? = cache.read(weekStart)

    fun ticket(): LastLoadedCache.Ticket = cache.ticket()

    fun write(
        ticket: LastLoadedCache.Ticket,
        list: ShoppingList,
    ) = cache.write(ticket, list.weekStart, list)

    /** Eigene Änderung (Abhaken, Hinzufügen, Löschen); eine ältere Server-Antwort überschreibt sie nicht. */
    fun update(
        weekStart: LocalDate,
        change: (ShoppingList) -> ShoppingList,
    ) = cache.update(weekStart, change)
}
