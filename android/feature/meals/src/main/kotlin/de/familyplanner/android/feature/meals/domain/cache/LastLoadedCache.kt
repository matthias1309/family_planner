package de.familyplanner.android.feature.meals.domain.cache

import de.familyplanner.android.core.data.session.SessionStore
import java.util.concurrent.atomic.AtomicReference

/**
 * Zuletzt geladene Stände im Arbeitsspeicher (ADR-0018): sofort zeigen, dahinter neu laden. Die Werte gehören dem
 * angemeldeten Mitglied; passt es nicht zur Session, gelten sie als leer. Höchstens [maxKeys] Schlüssel, der älteste
 * fällt zuerst heraus.
 */
class LastLoadedCache<K : Any, V : Any>(
    private val sessions: SessionStore,
    private val maxKeys: Int = 1,
) {
    /** [changes] zählt eigene Änderungen; eine ältere Server-Antwort darf sie nicht überschreiben. */
    private data class State<K, V>(
        val memberId: String? = null,
        val values: Map<K, V> = emptyMap(),
        val changes: Long = 0,
    )

    /** Stand beim Absenden einer Anfrage: für wen geladen wird und nach welcher eigenen Änderung. */
    class Ticket internal constructor(
        internal val memberId: String?,
        internal val changes: Long,
    )

    private val state = AtomicReference(State<K, V>())

    fun read(key: K): V? {
        val memberId = sessions.current?.memberId ?: return null
        return state
            .get()
            .takeIf { it.memberId == memberId }
            ?.values
            ?.get(key)
    }

    /** Vor der Anfrage holen und an [write] übergeben. */
    fun ticket(): Ticket = Ticket(sessions.current?.memberId, state.get().changes)

    /**
     * Speichert die Antwort für das Mitglied, für das geladen wurde. Gab es seit dem [ticket] eine eigene Änderung,
     * wird die (ältere) Antwort verworfen. Ohne Mitglied wird alles vergessen.
     */
    fun write(
        ticket: Ticket,
        key: K,
        value: V,
    ) {
        state.updateAndGet { current ->
            when {
                current.changes != ticket.changes -> current
                ticket.memberId == null -> State(changes = current.changes)
                ticket.memberId != current.memberId -> State(ticket.memberId, mapOf(key to value), current.changes)
                else -> current.copy(values = current.values.withNewest(key, value))
            }
        }
    }

    /** Ändert den gespeicherten Wert des angemeldeten Mitglieds; ohne passenden Wert geschieht nichts. */
    fun update(
        key: K,
        change: (V) -> V,
    ) {
        val memberId = sessions.current?.memberId ?: return
        state.updateAndGet { current ->
            val value = current.values[key]?.takeIf { current.memberId == memberId } ?: return@updateAndGet current
            current.copy(values = current.values.withNewest(key, change(value)), changes = current.changes + 1)
        }
    }

    private fun Map<K, V>.withNewest(
        key: K,
        value: V,
    ): Map<K, V> {
        val ordered = (this - key) + (key to value)
        return if (ordered.size <= maxKeys) ordered else ordered.entries.drop(ordered.size - maxKeys).associate { it.toPair() }
    }
}
